#!/bin/bash
set -e

cd "$HOME" || exit 1

export APP_PREFIX=/data/data/com.diamon.civil/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"
export TMX_PREFIX=/data/data/com.termux/files/usr

export CC=clang
export CXX=clang++
export FC=gfortran

export COMMON_CFLAGS="-fPIC -fPIE -Oz -ffile-prefix-map=$DESTDIR= -I$FAKE_USR/include -I$TMX_PREFIX/include"
export COMMON_CXXFLAGS="-fPIC -fPIE -Oz -ffile-prefix-map=$DESTDIR= -I$FAKE_USR/include -I$TMX_PREFIX/include"
export COMMON_FFLAGS="-fPIC -fPIE -Oz -fallow-argument-mismatch -Wno-error"
export LDFLAGS="-pie -Wl,-z,max-page-size=16384 -L$FAKE_USR/lib -L$TMX_PREFIX/lib"

export PKG_CONFIG_PATH="$FAKE_USR/lib/pkgconfig:$TMX_PREFIX/lib/pkgconfig:${PKG_CONFIG_PATH:-}"
export LD_LIBRARY_PATH="$FAKE_USR/lib:$TMX_PREFIX/lib:${LD_LIBRARY_PATH:-}"

echo "Verificando OpenCASCADE..."
find "$FAKE_USR/lib" -maxdepth 1 \( -name 'libTKBRep.so' -o -name 'libTKTopAlgo.so' -o -name 'libTKernel.so' \) | sort
test -f "$FAKE_USR/lib/libTKernel.so"

echo "Clonando repositorio de Gmsh..."
rm -rf "$HOME/gmsh"
git clone https://gitlab.onelab.info/gmsh/gmsh.git --depth 1
cd "$HOME/gmsh" || exit 1

echo "Configurando con CMake para Termux/Android..."
mkdir -p build
cd build || exit 1
rm -rf ./*

cmake .. \
  -DCMAKE_INSTALL_PREFIX="$APP_PREFIX" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER="$CC" \
  -DCMAKE_CXX_COMPILER="$CXX" \
  -DCMAKE_Fortran_COMPILER="$FC" \
  -DCMAKE_C_FLAGS="$COMMON_CFLAGS" \
  -DCMAKE_CXX_FLAGS="$COMMON_CXXFLAGS" \
  -DCMAKE_Fortran_FLAGS="$COMMON_FFLAGS" \
  -DCMAKE_SHARED_LINKER_FLAGS="$LDFLAGS" \
  -DCMAKE_EXE_LINKER_FLAGS="$LDFLAGS" \
  -DCMAKE_PREFIX_PATH="$FAKE_USR;$TMX_PREFIX" \
  -DENABLE_FLTK=OFF \
  -DENABLE_OPENGL=OFF \
  -DENABLE_MPI=OFF \
  -DENABLE_BUILD_DYNAMIC=ON \
  -DENABLE_BUILD_SHARED=ON \
  -DENABLE_NETGEN=ON \
  -DENABLE_TETGEN=ON \
  -DENABLE_OCC=ON

echo "Compilando Gmsh..."
JOBS="$(nproc)"
if [ "$JOBS" -gt 1 ]; then
  JOBS=$((JOBS - 1))
fi
cmake --build . --parallel "$JOBS"

echo "Instalando en fake_root..."
DESTDIR="$DESTDIR" cmake --install .

echo "=== Compilación de Gmsh exitosa ==="

if [ -f "$FAKE_USR/lib/libgmsh.so" ]; then
  ls -lh "$FAKE_USR/lib/libgmsh.so"
else
  echo "Error: no se encontró libgmsh.so"
  exit 1
fi

if [ -f "$FAKE_USR/bin/gmsh" ]; then
  ls -lh "$FAKE_USR/bin/gmsh"
else
  echo "Aviso: no se generó el ejecutable gmsh; en build headless puede interesar solo libgmsh.so"
fi

echo
echo "=== Alineación 16KB ==="
readelf -l "$FAKE_USR/lib/libgmsh.so" | grep LOAD || true

echo
echo "=== Dependencias directas de libgmsh.so ==="
readelf -d "$FAKE_USR/lib/libgmsh.so" | grep NEEDED || true

echo
echo "=== Verificando OpenCASCADE en Gmsh ==="
readelf -d "$FAKE_USR/lib/libgmsh.so" | grep TK || echo "Aviso: no se detectaron dependencias TK directas; puede haber enlace indirecto o build de OCC no embebido directamente."

echo
echo "=== Símbolos OCC dentro de libgmsh.so ==="
nm -D "$FAKE_USR/lib/libgmsh.so" 2>/dev/null | grep -E "TopoDS_|BRep_|Geom_|STEPControl_" | head || true
