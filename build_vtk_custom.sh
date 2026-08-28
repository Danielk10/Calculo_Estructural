#!/bin/bash
set -e

cd "$HOME" || exit 1

# 0. Habilitar repo X11 e instalar dependencias X11 (mismo patron que Tk)
pkg install -y x11-repo
pkg install -y libx11 libxft libxext libxrender libxrandr libxfixes libxcursor libxinerama xorgproto mesa

export APP_PREFIX=/data/data/com.diamon.civil/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"
export TMX_PREFIX=/data/data/com.termux/files/usr

# ==============================================================================
# FUNCIONES DE VERIFICACIÓN (alineación ELF 16KB + integridad)
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

export CC=clang
export CXX=clang++

mkdir -p "$FAKE_USR/lib" "$FAKE_USR/include"

echo "Verificando headers X11 instalados por Termux..."
find "$TMX_PREFIX/include/X11" -maxdepth 1 \( -name 'X.h' -o -name 'Xlib.h' \) | sort

export CPPFLAGS="-I$FAKE_USR/include -I$TMX_PREFIX/include -I$TMX_PREFIX/include/X11"
export CFLAGS="-fPIC -fPIE -Oz -Wno-implicit-function-declaration -ffile-prefix-map=$DESTDIR="
export CXXFLAGS="-fPIC -fPIE -Oz -Wno-implicit-function-declaration -ffile-prefix-map=$DESTDIR="
export LDFLAGS="-pie -Wl,-z,max-page-size=16384 -lm -L$FAKE_USR/lib -L$TMX_PREFIX/lib"
export SHARED_LDFLAGS="-Wl,-z,max-page-size=16384 -lm -L$FAKE_USR/lib -L$TMX_PREFIX/lib"
export PKG_CONFIG_PATH="$FAKE_USR/lib/pkgconfig:$TMX_PREFIX/lib/pkgconfig:${PKG_CONFIG_PATH:-}"
export LD_LIBRARY_PATH="$FAKE_USR/lib:$TMX_PREFIX/lib:${LD_LIBRARY_PATH:-}"

echo "=== Descargando VTK (rama release) ==="
rm -rf "$HOME/vtk"
git clone --depth 1 --branch release https://gitlab.kitware.com/vtk/vtk.git "$HOME/vtk"
cd "$HOME/vtk" || exit 1

# --- PARCHE DE COMPATIBILIDAD NETCDF PARA
#          TERMUX/ANDROID ---
sed -i '/extern void \*mremap/d' ThirdParty/netcdf/vtknetcdf/libsrc/mmapio.c

# --- PARCHE DE COMPATIBILIDAD IOS_GETLINE PARA
#          TERMUX/ANDROID ---
sed -i 's/<sys\/termios.h>/<termios.h>/g' ThirdParty/ioss/vtkioss/Ioss_Getline.C

mkdir -p build && cd build || exit 1
rm -rf ./*

echo "=== Configurando VTK completo: X11 + OpenGL ES + FiltersModeling + Charts ==="
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
  -DVTK_MODULE_ENABLE_VTK_netcdf=YES \
  -DVTK_MODULE_ENABLE_VTK_IONetCDF=YES \
  -DVTK_MODULE_ENABLE_VTK_tiff=YES \
  -DVTK_USE_X=ON \
  -DX11_X11_INCLUDE_PATH="$TMX_PREFIX/include" \
  -DX11_X11_LIB="$TMX_PREFIX/lib/libX11.so" \
  -DX11_Xext_LIB="$TMX_PREFIX/lib/libXext.so" \
  -DVTK_OPENGL_USE_GLES=ON \
  -DVTK_OPENGL_HAS_EGL=OFF \
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
  -DVTK_MODULE_ENABLE_VTK_IOGeometry=YES \
  -DVTK_MODULE_ENABLE_VTK_IOLegacy=YES \
  -DVTK_MODULE_ENABLE_VTK_IOXML=YES \
  -DVTK_MODULE_ENABLE_VTK_IOXMLParser=YES \
  -DVTK_MODULE_ENABLE_VTK_IOPLY=YES \
  -DVTK_MODULE_ENABLE_VTK_RenderingCore=YES \
  -DVTK_MODULE_ENABLE_VTK_RenderingOpenGL2=YES \
  -DVTK_MODULE_ENABLE_VTK_RenderingContext2D=YES \
  -DVTK_MODULE_ENABLE_VTK_ChartsCore=YES \
  -DVTK_MODULE_ENABLE_VTK_InteractionStyle=YES

cmake --build . --parallel "$(nproc)"
DESTDIR="$DESTDIR" cmake --install .

echo ""
echo "=== Verificando instalación de VTK ==="
find "$FAKE_USR/lib" -maxdepth 1 -name 'libvtk*' | sort

echo ""
echo "=== Verificando enlace con OpenGL ES (no OpenGL desktop) ==="
VTKGL=$(find "$FAKE_USR/lib" -maxdepth 1 -name 'libvtkRenderingOpenGL2*.so*' | head -n1)
if [ -n "$VTKGL" ]; then
  readelf -d "$VTKGL" | grep -i "libGLESv2\|libGLESv3" && echo "  [OK] Enlazado con GLES" || echo "  [AVISO] No se detecto libGLESv2/v3 en NEEDED"
  readelf -d "$VTKGL" | grep -i "libGL\.so" && echo "  [AVISO] Se detecto libGL.so (OpenGL desktop) - revisar VTK_OPENGL_USE_GLES" || echo "  [OK] No enlaza contra libGL.so de escritorio"
fi

echo ""
echo "=== Alineación a 16KB de segmentos ELF (VTK) ==="
verify_libs_alignment "$FAKE_USR/lib" "libvtk*.so*" || { echo "ERROR: alineacion incorrecta en VTK"; exit 1; }

echo ""
echo "=== Verificando soporte GLX_EXT_create_context_es2_profile en Mesa/Termux ==="
if command -v glxinfo >/dev/null 2>&1; then
  glxinfo -B 2>/dev/null | grep -Ei "es2_profile|es_profile" && \
    echo "  [OK] Mesa soporta contexto GLES sobre GLX" || \
    echo "  [AVISO] Mesa NO expone es2_profile; X11+GLES puede fallar en runtime"
else
  echo "  [AVISO] glxinfo no disponible (instala mesa-utils para verificar)"
fi
