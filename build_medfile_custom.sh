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

# 1. BANDERAS DE ENLACE SEPARADAS (Alineación 16KB)
export BASE_LDFLAGS="-Wl,-z,max-page-size=16384 -L$FAKE_USR/lib -L$TMX_PREFIX/lib"
export EXE_LDFLAGS="-pie $BASE_LDFLAGS"
export SHARED_LDFLAGS="$BASE_LDFLAGS"

export PKG_CONFIG_PATH="$FAKE_USR/lib/pkgconfig:$TMX_PREFIX/lib/pkgconfig:${PKG_CONFIG_PATH:-}"
export LD_LIBRARY_PATH="$FAKE_USR/lib:$TMX_PREFIX/lib:${LD_LIBRARY_PATH:-}"

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
  -DCMAKE_Fortran_FLAGS="$COMMON_FFLAGS" \
  -DCMAKE_SHARED_LINKER_FLAGS="$SHARED_LDFLAGS" \
  -DCMAKE_EXE_LINKER_FLAGS="$EXE_LDFLAGS" \
  -DCMAKE_PREFIX_PATH="$FAKE_USR;$TMX_PREFIX" \
  -DHDF5_ROOT_DIR="$FAKE_USR" \
  -DHDF5_INCLUDE_DIR="$FAKE_USR/include" \
  -DHDF5_LIBRARY="$FAKE_USR/lib/libhdf5.so" \
  -DHDF5_Fortran_LIBRARY="$FAKE_USR/lib/libhdf5_fortran.so" \
  -DHDF5_Fortran_INCLUDE_DIR="$FAKE_USR/include" \
  -DHDF5_NO_FIND_PACKAGE_CONFIG_FILE=ON \
  -DMEDFILE_BUILD_TESTS=OFF \
  -DMEDFILE_BUILD_PYTHON=OFF \
  -DMEDFILE_INSTALL_DOC=OFF \
  -DMEDFILE_USE_MPI=OFF \
  -DMEDFILE_BUILD_FORTRAN=ON \
  -DBUILD_SHARED_LIBS=ON

echo "Compilando MEDfile..."
cmake --build . --parallel "$(nproc)"

echo "Instalando MEDfile en fake_root..."
DESTDIR="$DESTDIR" cmake --install .

echo ""
echo "=== Verificando instalación de MEDfile ==="
test -f "$FAKE_USR/lib/libmedC.so" && echo "  [OK] libmedC.so" || echo "  [FALTA] libmedC.so"
test -f "$FAKE_USR/include/med.h" && echo "  [OK] med.h" || echo "  [FALTA] med.h"
# En MEDfile v6.0.1 (fork chennes/med) el binding Fortran ya no se llama libmedfC.so,
# ahora se genera como libmedfwrap.so (medfwrap = MED Fortran WRAPper)
echo "Nota: desde MEDfile v6.0.1 el binding Fortran se llama libmedfwrap.so (antes libmedfC.so)"
test -f "$FAKE_USR/lib/libmedfwrap.so" && echo "  [OK] libmedfwrap.so" || echo "  [FALTA] libmedfwrap.so"

echo ""
echo "=== Dependencias directas de libmedC.so ==="
readelf -d "$FAKE_USR/lib/libmedC.so" | grep NEEDED || true

echo ""
echo "=== Alineación a 16KB de segmentos ELF ==="
readelf -l "$FAKE_USR/lib/libmedC.so" | grep LOAD || true
