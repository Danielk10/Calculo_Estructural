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
export LDFLAGS="-pie -Wl,-z,max-page-size=16384 -L$FAKE_USR/lib -L$TMX_PREFIX/lib"

echo "Clonando MEDfile v6.0.1..."
rm -rf "$HOME/med-6.0.1"
git clone --depth 1 --branch v6.0.1 https://github.com/chennes/med.git "$HOME/med-6.0.1"

MACRO_FILE="$HOME/med-6.0.1/config/cmake_files/medMacros.cmake"

echo "Parchando chequeo rígido de versión de HDF5 (exige major=1, minor=14)..."
sed -i 's/IF (NOT HDF_VERSION_MAJOR_REF EQUAL 1 OR NOT HDF_VERSION_MINOR_REF EQUAL 14 OR NOT HDF_VERSION_RELEASE_REF GREATER_EQUAL 0)/IF (FALSE)/' \
  "$MACRO_FILE"

echo "Confirmando que el parche se aplicó..."
grep -n "IF (FALSE)" "$MACRO_FILE" || { echo "ERROR: el patrón no coincidió, revisa el texto exacto"; exit 1; }

echo "Configurando MEDfile..."
mkdir -p "$HOME/medfile-build"
cd "$HOME/medfile-build" || exit 1
rm -rf ./*

cmake "$HOME/med-6.0.1" \
  -DCMAKE_INSTALL_PREFIX="$APP_PREFIX" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER="$CC" \
  -DCMAKE_CXX_COMPILER="$CXX" \
  -DCMAKE_Fortran_COMPILER="$FC" \
  -DCMAKE_C_FLAGS="$COMMON_CFLAGS" \
  -DCMAKE_CXX_FLAGS="$COMMON_CXXFLAGS" \
  -DCMAKE_SHARED_LINKER_FLAGS="$LDFLAGS" \
  -DCMAKE_EXE_LINKER_FLAGS="$LDFLAGS" \
  -DHDF5_ROOT_DIR="$FAKE_USR" \
  -DHDF5_NO_FIND_PACKAGE_CONFIG_FILE=ON \
  -DMEDFILE_BUILD_TESTS=OFF \
  -DMEDFILE_BUILD_PYTHON=OFF \
  -DMEDFILE_INSTALL_DOC=OFF \
  -DMEDFILE_USE_MPI=OFF \
  -DBUILD_SHARED_LIBS=ON

echo "Compilando MEDfile..."
cmake --build . --parallel "$(nproc)"

echo "Instalando MEDfile en fake_root..."
DESTDIR="$DESTDIR" cmake --install .

echo "=== Verificando instalación de MEDfile ==="
test -f "$FAKE_USR/lib/libmedC.so" && echo "libmedC.so OK" || echo "FALTA libmedC.so"
test -f "$FAKE_USR/include/med.h" && echo "med.h OK" || echo "FALTA med.h"
