#!/bin/bash
set -e

cd "$HOME" || exit 1

export APP_PREFIX=/data/data/com.diamon.civil/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"
export TMX_PREFIX=/data/data/com.termux/files/usr
export NDK_VERSION="r27d"
export NDK_ZIP="android-ndk-${NDK_VERSION}-linux.zip"
export NDK_ROOT="$HOME/android-ndk-${NDK_VERSION}"
export NATIVE_GLUE_DIR="$NDK_ROOT/sources/android/native_app_glue"

# ==============================================================================
# FUNCIONES DE VERIFICACION (alineacion ELF 16KB + integridad)
# ==============================================================================
require_file() {
  [ -e "$1" ] || { echo "ERROR: no existe $1" >&2; exit 1; }
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "ERROR: falta comando $1" >&2; exit 1; }
}

check_elf_alignment() {
  local lib="$1"
  local align
  align=$(readelf -lW "$lib" 2>/dev/null | grep LOAD | head -n1 | awk '{print $NF}')
  if [ "$align" = "0x4000" ] || [ "$align" = "0x10000" ]; then
    echo "  [OK] $(basename "$lib") alineado a 16KB ($align)"
    return 0
  else
    echo "  [FALTA] $(basename "$lib") NO alineado a 16KB (actual: ${align:-desconocido})"
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

export CC=clang
export CXX=clang++

mkdir -p "$FAKE_USR/lib" "$FAKE_USR/include"

# ==============================================================================
# Dependencias GLES/EGL nativas de Termux (sin X11)
# ==============================================================================
pkg install -y mesa

# ==============================================================================
# NDK completo de Google: solo se usa como fuente de headers/glue reales,
# NO como compilador (se sigue usando clang de Termux)
# ==============================================================================
if [ ! -d "$NDK_ROOT" ]; then
  echo "=== Descargando NDK oficial (${NDK_VERSION}) ==="
  curl -fsSL -o "$HOME/${NDK_ZIP}" "https://dl.google.com/android/repository/${NDK_ZIP}"
  echo "=== Extrayendo NDK ==="
  cd "$HOME" && unzip -q "${NDK_ZIP}"
  rm -f "$HOME/${NDK_ZIP}"
else
  echo "=== NDK ya presente en $NDK_ROOT, omitiendo descarga ==="
fi

require_file "$NATIVE_GLUE_DIR/android_native_app_glue.h"
require_file "$NATIVE_GLUE_DIR/android_native_app_glue.c"

echo "=== Compilando android_native_app_glue.c con clang de Termux ==="
# CORRECCIÓN: Se elimina -fPIE para permitir su uso en librerías compartidas
clang -c -fPIC -O2 -I"$NATIVE_GLUE_DIR" \
  "$NATIVE_GLUE_DIR/android_native_app_glue.c" \
  -o "$HOME/native_app_glue.o"

echo "=== Generando implementacion real de android_main (entrypoint no usado por JNI) ==="
cat > "$HOME/android_main_impl.c" <<'EOF'
#include <android_native_app_glue.h>
void android_main(struct android_app* app) {
    /* Esta app usa JNI/Kotlin como entrypoint real.
       Este simbolo solo satisface el enlace de vtkRenderingUI,
       que exige android_main aunque nunca se invoque en runtime. */
    (void)app;
}
EOF
# CORRECCIÓN: Se elimina -fPIE aquí también
clang -c -fPIC -O2 -I"$NATIVE_GLUE_DIR" \
  "$HOME/android_main_impl.c" -o "$HOME/android_main_impl.o"

echo "=== Empaquetando en libreria estatica ==="
# CORRECCIÓN: Empaquetar en .a para evitar inyectar código no deseado en HDF5
llvm-ar rcs "$HOME/libandroid_glue.a" "$HOME/native_app_glue.o" "$HOME/android_main_impl.o"

export CPPFLAGS="-I$NATIVE_GLUE_DIR -I$HOME/vtk_compat -I$FAKE_USR/include -I$TMX_PREFIX/include"

# CORRECCIÓN: Se elimina -fPIE de las flags globales
export CFLAGS="-fPIC -Oz -Wno-implicit-function-declaration -ffile-prefix-map=$DESTDIR="
export CXXFLAGS="-fPIC -Oz -Wno-implicit-function-declaration -ffile-prefix-map=$DESTDIR="

# CORRECCIÓN: Se elimina -L/system/lib64 y se enlaza la librería estática limpiamente
export LDFLAGS="-pie -Wl,-z,max-page-size=16384 -lm -L$FAKE_USR/lib -L$TMX_PREFIX/lib -L$HOME -landroid_glue -llog -landroid"
export SHARED_LDFLAGS="-Wl,-z,max-page-size=16384 -lm -L$FAKE_USR/lib -L$TMX_PREFIX/lib -L$HOME -landroid_glue -llog -landroid"

export PKG_CONFIG_PATH="$FAKE_USR/lib/pkgconfig:$TMX_PREFIX/lib/pkgconfig:${PKG_CONFIG_PATH:-}"
export LD_LIBRARY_PATH="$FAKE_USR/lib:$TMX_PREFIX/lib:${LD_LIBRARY_PATH:-}"

echo "=== Descargando VTK (rama release) ==="
rm -rf "$HOME/vtk"
git clone --depth 1 --branch release https://gitlab.kitware.com/vtk/vtk.git "$HOME/vtk"
cd "$HOME/vtk" || exit 1

# ==============================================================================
# PARCHES DE COMPATIBILIDAD ANDROID/BIONIC (wrapper, sin borrar codigo fuente)
# Unicos parches necesarios; SIN tocar RenderingUI ni ANDROID:BOOL
# ==============================================================================

# --- Parche netcdf: Bionic ya declara mremap en <sys/mman.h> ---
if grep -q "extern void \*mremap" ThirdParty/netcdf/vtknetcdf/libsrc/mmapio.c; then
  sed -i '/extern void \*mremap/i #ifndef __BIONIC__' ThirdParty/netcdf/vtknetcdf/libsrc/mmapio.c
  sed -i '/extern void \*mremap/a #endif' ThirdParty/netcdf/vtknetcdf/libsrc/mmapio.c
fi

# --- Parche ioss: Bionic no tiene <sys/termios.h>, usar shim de include ---
mkdir -p "$HOME/vtk_compat/sys"
cat > "$HOME/vtk_compat/sys/termios.h" <<'EOF'
#include <termios.h>
EOF
# vtk_compat ya esta primero en CPPFLAGS, el shim intercepta el include
# sin modificar Ioss_Getline.C

mkdir -p build && cd build || exit 1
rm -rf ./*

echo "=== Configurando VTK COMPLETO (sin desactivar ANDROID ni RenderingUI) ==="
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

# ==============================================================================
# Guardar entorno para poder retomar sin recompilar/reconfigurar desde cero
# ==============================================================================
mkdir -p "$HOME/vtk_env"
cat > "$HOME/vtk_env/vtk_build_env.sh" <<EOF
export APP_PREFIX="$APP_PREFIX"
export DESTDIR="$DESTDIR"
export FAKE_USR="$FAKE_USR"
export TMX_PREFIX="$TMX_PREFIX"
export NDK_ROOT="$NDK_ROOT"
export NATIVE_GLUE_DIR="$NATIVE_GLUE_DIR"
export CC="$CC"
export CXX="$CXX"
export CPPFLAGS="$CPPFLAGS"
export CFLAGS="$CFLAGS"
export CXXFLAGS="$CXXFLAGS"
export LDFLAGS="$LDFLAGS"
export SHARED_LDFLAGS="$SHARED_LDFLAGS"
export PKG_CONFIG_PATH="$PKG_CONFIG_PATH"
export LD_LIBRARY_PATH="$LD_LIBRARY_PATH"
export VTK_BUILD_DIR="$HOME/vtk/build"
EOF
echo "=== Entorno guardado en \$HOME/vtk_env/vtk_build_env.sh ==="

cmake --build . --parallel "$(nproc)"
DESTDIR="$DESTDIR" cmake --install .

echo ""
echo "=== Verificando instalacion de VTK ==="
find "$FAKE_USR/lib" -maxdepth 1 -name 'libvtk*' | sort

echo ""
echo "=== Verificando enlace con OpenGL ES (no OpenGL desktop) ==="
VTKGL=$(find "$FAKE_USR/lib" -maxdepth 1 -name 'libvtkRenderingOpenGL2*.so*' | head -n1)
if [ -n "$VTKGL" ]; then
  readelf -d "$VTKGL" | grep -i "libGLESv2\|libGLESv3" && echo "  [OK] Enlazado con GLES" || echo "  [AVISO] No se detecto libGLESv2/v3 en NEEDED"
  readelf -d "$VTKGL" | grep -i "libGL\.so" && echo "  [AVISO] Se detecto libGL.so (OpenGL desktop) - revisar VTK_OPENGL_USE_GLES" || echo "  [OK] No enlaza contra libGL.so de escritorio"
  readelf -d "$VTKGL" | grep -i "libEGL" && echo "  [OK] Enlazado con EGL (nativo Android)" || echo "  [AVISO] No se detecto libEGL en NEEDED"
fi

echo ""
echo "=== Alineacion a 16KB de segmentos ELF (VTK) ==="
verify_libs_alignment "$FAKE_USR/lib" "libvtk*.so*" || { echo "ERROR: alineacion incorrecta en VTK"; exit 1; }
