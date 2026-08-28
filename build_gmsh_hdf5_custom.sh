#!/bin/bash
set -e

cd "$HOME" || exit 1

export APP_PREFIX=/data/data/com.diamon.civil/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"
export TMX_PREFIX=/data/data/com.termux/files/usr

# Asegurar dependencias del sistema para OpenMP / BLAS
pkg install -y libomp openblas 2>/dev/null || true

export CC=clang
export CXX=clang++
export FC=gfortran

export COMMON_CFLAGS="-fPIC -fPIE -Oz -ffile-prefix-map=$DESTDIR= -I$FAKE_USR/include -I$TMX_PREFIX/include"
export COMMON_CXXFLAGS="-fPIC -fPIE -Oz -ffile-prefix-map=$DESTDIR= -I$FAKE_USR/include -I$TMX_PREFIX/include"
export COMMON_FFLAGS="-fPIC -fPIE -Oz -fallow-argument-mismatch -Wno-error"

# Banderas de enlace separadas (alineación 16KB)
export BASE_LDFLAGS="-Wl,-z,max-page-size=16384 -L$FAKE_USR/lib -L$TMX_PREFIX/lib"
export EXE_LDFLAGS="-pie $BASE_LDFLAGS"
export SHARED_LDFLAGS="$BASE_LDFLAGS"

export PKG_CONFIG_PATH="$FAKE_USR/lib/pkgconfig:$TMX_PREFIX/lib/pkgconfig:${PKG_CONFIG_PATH:-}"
export LD_LIBRARY_PATH="$FAKE_USR/lib:$TMX_PREFIX/lib:${LD_LIBRARY_PATH:-}"

echo "Verificando OpenCASCADE..."
find "$FAKE_USR/lib" -maxdepth 1 \( -name 'libTKBRep.so' -o -name 'libTKTopAlgo.so' -o -name 'libTKernel.so' \) | sort
test -f "$FAKE_USR/lib/libTKernel.so"

echo "Verificando HDF5 propio en fake_root..."
test -f "$FAKE_USR/lib/libhdf5.so"
test -f "$FAKE_USR/lib/libhdf5_hl.so"
test -f "$FAKE_USR/include/hdf5.h"

echo "Verificando MEDfile propio en fake_root..."
test -f "$FAKE_USR/lib/libmedC.so"
test -f "$FAKE_USR/include/med.h"

echo "Verificando OpenBLAS propio en fake_root..."
test -f "$FAKE_USR/lib/libopenblas.so"

echo "Verificando VTK..."
VTK_CMAKE_DIR="$(find "$FAKE_USR/lib/cmake" -maxdepth 1 -iname 'vtk-*' 2>/dev/null | head -n1)"
if [ -n "$VTK_CMAKE_DIR" ] && [ -f "$VTK_CMAKE_DIR/VTKConfig.cmake" ]; then
  echo "VTK detectado en: $VTK_CMAKE_DIR"
  USE_VTK_FLAG="-DENABLE_VTK=ON -DVTK_DIR=$VTK_CMAKE_DIR"
else
  echo "Aviso: VTK no encontrado en fake_root, se desactivará el módulo VTK en Gmsh."
  USE_VTK_FLAG="-DENABLE_VTK=OFF"
fi

echo "Clonando repositorio de Gmsh..."
rm -rf "$HOME/gmsh"
git clone https://gitlab.onelab.info/gmsh/gmsh.git --depth 1
cd "$HOME/gmsh" || exit 1

mkdir -p build && cd build || exit 1
rm -rf ./*

echo "Configurando Gmsh con características avanzadas..."
cmake .. \
  -DCMAKE_INSTALL_PREFIX="$APP_PREFIX" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER="$CC" \
  -DCMAKE_CXX_COMPILER="$CXX" \
  -DCMAKE_Fortran_COMPILER="$FC" \
  -DCMAKE_C_FLAGS="$COMMON_CFLAGS" \
  -DCMAKE_CXX_FLAGS="$COMMON_CXXFLAGS" \
  -DCMAKE_Fortran_FLAGS="$COMMON_FFLAGS" \
  -DCMAKE_SHARED_LINKER_FLAGS="$SHARED_LDFLAGS" \
  -DCMAKE_EXE_LINKER_FLAGS="$EXE_LDFLAGS" \
  -DCMAKE_PREFIX_PATH="$FAKE_USR;$TMX_PREFIX" \
  -DCASROOT="$FAKE_USR" \
  -DHDF5_ROOT="$FAKE_USR" \
  -DHDF5_NO_FIND_PACKAGE_CONFIG_FILE=ON \
  -DHDF5_INCLUDE_DIR="$FAKE_USR/include" \
  -DHDF5_LIBRARY="$FAKE_USR/lib/libhdf5.so" \
  -DHDF5_HL_LIBRARY="$FAKE_USR/lib/libhdf5_hl.so" \
  -DHDF5_C_LIBRARY="$FAKE_USR/lib/libhdf5.so" \
  -DMEDFILE_ROOT_DIR="$FAKE_USR" \
  -DMEDFILE_INCLUDE_DIR="$FAKE_USR/include" \
  -DMEDFILE_LIBRARY="$FAKE_USR/lib/libmedC.so" \
  $USE_VTK_FLAG \
  -DENABLE_BLAS_LAPACK=ON \
  -DBLAS_LIBRARIES="$FAKE_USR/lib/libopenblas.so" \
  -DLAPACK_LIBRARIES="$FAKE_USR/lib/libopenblas.so" \
  -DENABLE_OCC=ON \
  -DENABLE_MED=ON \
  -DENABLE_NETGEN=ON \
  -DENABLE_TETGEN=ON \
  -DENABLE_CGNS=ON \
  -DENABLE_OPENMP=ON \
  -DENABLE_VOROPP=ON \
  -DENABLE_ALGRAPH=ON \
  -DENABLE_PRIVATE_API=ON \
  -DENABLE_BUILD_DYNAMIC=ON \
  -DENABLE_BUILD_SHARED=ON \
  -DENABLE_FLTK=OFF \
  -DENABLE_OPENGL=OFF \
  -DENABLE_MPI=OFF

echo "Compilando Gmsh..."
JOBS="$(nproc)"
[ "$JOBS" -gt 1 ] && JOBS=$((JOBS - 1))
cmake --build . --parallel "$JOBS"

echo "Instalando Gmsh en fake_root..."
DESTDIR="$DESTDIR" cmake --install .

echo "=== Compilación de Gmsh exitosa ==="

if [ -f "$FAKE_USR/lib/libgmsh.so" ]; then
  ls -lh "$FAKE_USR/lib/libgmsh.so"
else
  echo "Error: no se encontró libgmsh.so"
  exit 1
fi

echo
echo "=== Alineación 16KB ==="
readelf -l "$FAKE_USR/lib/libgmsh.so" | grep LOAD || true

echo
echo "=== Dependencias directas de libgmsh.so ==="
readelf -d "$FAKE_USR/lib/libgmsh.so" | grep NEEDED || true

echo
echo "=== Verificación de módulos habilitados en libgmsh.so ==="
readelf -d "$FAKE_USR/lib/libgmsh.so" | grep -E -i "hdf5|med|TK|vtk|omp|cgns|openblas" || true
