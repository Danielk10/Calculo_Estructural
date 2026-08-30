#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$HOME"

export APP_PREFIX=/data/data/com.diamon.civil/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"
export TMX_PREFIX=/data/data/com.termux/files/usr
export NDK_VERSION="r27d"
export NDK_ZIP="android-ndk-${NDK_VERSION}-linux.zip"
export NDK_ROOT="$HOME/android-ndk-${NDK_VERSION}"
export NATIVE_GLUE_DIR="$NDK_ROOT/sources/android/native_app_glue"
export VTK_SRC_DIR="$HOME/vtk"
export VTK_BUILD_DIR="$VTK_SRC_DIR/build"
export VTK_COMPAT_DIR="$HOME/vtk_compat"
export VTK_ENV_DIR="$HOME/vtk_env"

require_file() {
  [ -e "$1" ] || { echo "ERROR: no existe $1" >&2; exit 1; }
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "ERROR: falta comando $1" >&2; exit 1; }
}

check_elf_alignment() {
  local lib="$1"
  local align
  align=$(readelf -lW "$lib" 2>/dev/null | awk '/LOAD/ {print $NF; exit}')
  if [ "$align" = "0x4000" ] || [ "$align" = "0x10000" ]; then
    echo " [OK] $(basename "$lib") alineado a 16KB ($align)"
    return 0
  else
    echo " [FALTA] $(basename "$lib") NO alineado a 16KB (actual: ${align:-desconocido})"
    return 1
  fi
}

verify_libs_alignment() {
  local dir="$1"
  local pattern="$2"
  local fail=0
  while IFS= read -r -d '' lib; do
    check_elf_alignment "$lib" || fail=1
  done < <(find "$dir" -maxdepth 1 -name "$pattern" -print0)
  return $fail
}

require_cmd readelf
require_cmd find
require_cmd awk
require_cmd patch
require_cmd curl
require_cmd unzip
require_cmd git
require_cmd cmake
require_cmd clang
require_cmd clang++
require_cmd llvm-ar

export CC=clang
export CXX=clang++

mkdir -p "$FAKE_USR/lib" "$FAKE_USR/include" "$VTK_COMPAT_DIR" "$VTK_ENV_DIR"

pkg install -y mesa

if [ ! -d "$NDK_ROOT" ]; then
  echo "=== Descargando NDK oficial (${NDK_VERSION}) ==="
  curl -fsSL -o "$HOME/${NDK_ZIP}" "https://dl.google.com/android/repository/${NDK_ZIP}"
  echo "=== Extrayendo NDK ==="
  (cd "$HOME" && unzip -q "${NDK_ZIP}")
  rm -f "$HOME/${NDK_ZIP}"
else
  echo "=== NDK ya presente en $NDK_ROOT, omitiendo descarga ==="
fi

require_file "$NATIVE_GLUE_DIR/android_native_app_glue.h"
require_file "$NATIVE_GLUE_DIR/android_native_app_glue.c"

echo "=== Compilando android_native_app_glue.c con clang de Termux ==="
clang -c -fPIC -O2 -I"$NATIVE_GLUE_DIR" \
  "$NATIVE_GLUE_DIR/android_native_app_glue.c" \
  -o "$HOME/native_app_glue.o"

echo "=== Generando implementacion dummy de android_main ==="
cat > "$HOME/android_main_impl.c" <<'EOC'
#include <android_native_app_glue.h>
void android_main(struct android_app* app) {
  (void)app;
}
EOC
clang -c -fPIC -O2 -I"$NATIVE_GLUE_DIR" \
  "$HOME/android_main_impl.c" -o "$HOME/android_main_impl.o"

echo "=== Empaquetando libandroid_glue.a ==="
llvm-ar rcs "$HOME/libandroid_glue.a" \
  "$HOME/native_app_glue.o" \
  "$HOME/android_main_impl.o"

export CPPFLAGS="-I$NATIVE_GLUE_DIR -I$VTK_COMPAT_DIR -I$FAKE_USR/include -I$TMX_PREFIX/include"
export CFLAGS="-fPIC -Oz -Wno-implicit-function-declaration -ffile-prefix-map=$DESTDIR="
export CXXFLAGS="-fPIC -Oz -Wno-implicit-function-declaration -ffile-prefix-map=$DESTDIR="
export LDFLAGS="-pie -Wl,-z,max-page-size=16384 -lm -L$FAKE_USR/lib -L$TMX_PREFIX/lib -L$HOME -landroid_glue"
export SHARED_LDFLAGS="-Wl,-z,max-page-size=16384 -lm -L$FAKE_USR/lib -L$TMX_PREFIX/lib -L$HOME -landroid_glue -llog -landroid"
export PKG_CONFIG_PATH="$FAKE_USR/lib/pkgconfig:$TMX_PREFIX/lib/pkgconfig:${PKG_CONFIG_PATH:-}"
export LD_LIBRARY_PATH="$FAKE_USR/lib:$TMX_PREFIX/lib:${LD_LIBRARY_PATH:-}"

echo "=== Descargando VTK (rama release) ==="
rm -rf "$VTK_SRC_DIR"
git clone --depth 1 --branch release https://gitlab.kitware.com/vtk/vtk.git "$VTK_SRC_DIR"
cd "$VTK_SRC_DIR"

if grep -q 'extern void \*mremap' ThirdParty/netcdf/vtknetcdf/libsrc/mmapio.c; then
  sed -i '/extern void \*mremap/i #ifndef __BIONIC__' ThirdParty/netcdf/vtknetcdf/libsrc/mmapio.c
  sed -i '/extern void \*mremap/a #endif' ThirdParty/netcdf/vtknetcdf/libsrc/mmapio.c
fi

mkdir -p "$VTK_COMPAT_DIR/sys"
cat > "$VTK_COMPAT_DIR/sys/termios.h" <<'EOC'
#include <termios.h>
EOC

mkdir -p "$VTK_BUILD_DIR"
cd "$VTK_BUILD_DIR"
rm -rf ./*

echo "=== Configurando VTK para OCCT/TKIVtk + GLES/EGL + Android glue ==="
cmake .. \
  -DCMAKE_INSTALL_PREFIX="$APP_PREFIX" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER="$CC" \
  -DCMAKE_CXX_COMPILER="$CXX" \
  -DHAVE_MREMAP:BOOL=ON \
  -DCMAKE_C_FLAGS="$CFLAGS $CPPFLAGS -Wno-implicit-function-declaration" \
  -DCMAKE_CXX_FLAGS="$CXXFLAGS $CPPFLAGS -Wno-implicit-function-declaration" \
  -DCMAKE_EXE_LINKER_FLAGS="$LDFLAGS" \
  -DCMAKE_SHARED_LINKER_FLAGS="$SHARED_LDFLAGS" \
  -DCMAKE_PREFIX_PATH="$FAKE_USR;$TMX_PREFIX" \
  -DBUILD_SHARED_LIBS=ON \
  -DVTK_BUILD_TESTING=OFF \
  -DVTK_BUILD_DOCUMENTATION=OFF \
  -DBUILD_EXAMPLES=OFF \
  -DVTK_USE_X=OFF \
  -DVTK_OPENGL_USE_GLES=ON \
  -DVTK_OPENGL_HAS_EGL=ON \
  -DVTK_OPENGL_HAS_OSMESA=OFF \
  -DVTK_GROUP_ENABLE_Rendering=YES \
  -DVTK_GROUP_ENABLE_Qt=NO \
  -DVTK_GROUP_ENABLE_Views=NO \
  -DVTK_GROUP_ENABLE_Web=NO \
  -DVTK_GROUP_ENABLE_MPI=NO \
  -DVTK_GROUP_ENABLE_Imaging=NO \
  -DVTK_MODULE_ENABLE_VTK_CommonCore=YES \
  -DVTK_MODULE_ENABLE_VTK_CommonDataModel=YES \
  -DVTK_MODULE_ENABLE_VTK_CommonExecutionModel=YES \
  -DVTK_MODULE_ENABLE_VTK_FiltersCore=YES \
  -DVTK_MODULE_ENABLE_VTK_FiltersGeneral=YES \
  -DVTK_MODULE_ENABLE_VTK_FiltersModeling=YES \
  -DVTK_MODULE_ENABLE_VTK_FiltersSources=YES \
  -DVTK_MODULE_ENABLE_VTK_IOLegacy=YES \
  -DVTK_MODULE_ENABLE_VTK_IOXML=YES \
  -DVTK_MODULE_ENABLE_VTK_IOXMLParser=YES \
  -DVTK_MODULE_ENABLE_VTK_IOGeometry=YES \
  -DVTK_MODULE_ENABLE_VTK_IOPLY=YES \
  -DVTK_MODULE_ENABLE_VTK_RenderingCore=YES \
  -DVTK_MODULE_ENABLE_VTK_RenderingOpenGL2=YES \
  -DVTK_MODULE_ENABLE_VTK_RenderingUI=YES \
  -DVTK_MODULE_ENABLE_VTK_RenderingContext2D=YES \
  -DVTK_MODULE_ENABLE_VTK_ChartsCore=YES \
  -DVTK_MODULE_ENABLE_VTK_InteractionStyle=YES

cat > "$VTK_ENV_DIR/vtk_build_env.sh" <<EOC
export APP_PREFIX="$APP_PREFIX"
export DESTDIR="$DESTDIR"
export FAKE_USR="$FAKE_USR"
export TMX_PREFIX="$TMX_PREFIX"
export NDK_ROOT="$NDK_ROOT"
export NATIVE_GLUE_DIR="$NATIVE_GLUE_DIR"
export CPPFLAGS='$CPPFLAGS'
export CFLAGS='$CFLAGS'
export CXXFLAGS='$CXXFLAGS'
export LDFLAGS='$LDFLAGS'
export SHARED_LDFLAGS='$SHARED_LDFLAGS'
export PKG_CONFIG_PATH='$PKG_CONFIG_PATH'
export LD_LIBRARY_PATH='$LD_LIBRARY_PATH'
EOC

echo "=== Compilando VTK ==="
cmake --build . --parallel "$(nproc)"

echo "=== Instalando VTK ==="
DESTDIR="$DESTDIR" cmake --install .

echo ""
echo "=== Verificando instalacion de VTK ==="
find "$FAKE_USR/lib" -maxdepth 1 -name 'libvtk*' | sort

echo ""
echo "=== Verificando enlace con OpenGL ES / EGL ==="
VTKGL=$(find "$FAKE_USR/lib" -maxdepth 1 -name 'libvtkRenderingOpenGL2*.so*' | head -n1)
if [ -n "$VTKGL" ]; then
  readelf -d "$VTKGL" | grep -i 'libGLESv2\|libGLESv3' && echo ' [OK] Enlazado con GLES' || echo ' [AVISO] No se detecto libGLESv2/v3 en NEEDED'
  readelf -d "$VTKGL" | grep -i 'libGL\.so' && echo ' [AVISO] Se detecto libGL.so (OpenGL desktop)' || echo ' [OK] No enlaza contra libGL.so de escritorio'
  readelf -d "$VTKGL" | grep -i 'libEGL' && echo ' [OK] Enlazado con EGL (nativo Android)' || echo ' [AVISO] No se detecto libEGL en NEEDED'
fi

echo ""
echo "=== Alineacion a 16KB de segmentos ELF (VTK) ==="
verify_libs_alignment "$FAKE_USR/lib" 'libvtk*.so*' || { echo 'ERROR: alineacion incorrecta en VTK'; exit 1; }

echo "=== Listo ==="
echo "Entorno guardado en: $VTK_ENV_DIR/vtk_build_env.sh"
