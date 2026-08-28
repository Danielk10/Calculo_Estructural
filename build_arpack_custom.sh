#!/bin/bash
set -e

cd "$HOME"

export APP_PREFIX=/data/data/com.diamon.civil/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"
export TMX_PREFIX=/data/data/com.termux/files/usr

mkdir -p "$FAKE_USR/include" "$FAKE_USR/lib"

export CC=clang
export FC=gfortran

export COMMON_CFLAGS="-fPIC -fPIE -Oz -ffile-prefix-map=$DESTDIR= -I$FAKE_USR/include -I$TMX_PREFIX/include"
# Bandera clave para compilar Fortran legado en Termux
export COMMON_FFLAGS="-fPIC -fPIE -Oz -fallow-argument-mismatch -Wno-error"
export LDFLAGS="-pie -Wl,-z,max-page-size=16384 -L$FAKE_USR/lib -L$TMX_PREFIX/lib"

echo "Clonando repositorio moderno de ARPACK-NG..."
rm -rf "$HOME/arpack-ng"
git clone https://github.com/opencollab/arpack-ng.git --depth 1
cd "$HOME/arpack-ng"

echo "Configurando con CMake..."
mkdir -p build
cd build
rm -rf ./*

# Utilizamos CMake enlazando tu OpenBLAS nativo
cmake .. \
  -DCMAKE_INSTALL_PREFIX="$APP_PREFIX" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER="$CC" \
  -DCMAKE_Fortran_COMPILER="$FC" \
  -DCMAKE_C_FLAGS="$COMMON_CFLAGS" \
  -DCMAKE_Fortran_FLAGS="$COMMON_FFLAGS" \
  -DCMAKE_EXE_LINKER_FLAGS="$LDFLAGS" \
  -DBLAS_LIBRARIES="$FAKE_USR/lib/libopenblas.so" \
  -DLAPACK_LIBRARIES="$FAKE_USR/lib/libopenblas.so" \
  -DBUILD_SHARED_LIBS=OFF \
  -DEXAMPLES=OFF \
  -DTESTS=OFF

echo "Compilando ARPACK..."
cmake --build . --parallel "$(nproc)"

echo "Instalando en fake_root..."
# Usamos el instalador nativo de CMake hacia el DESTDIR
DESTDIR="$DESTDIR" cmake --install .

echo "=== Compilación de ARPACK Exitosa ==="
ls -lh "$FAKE_USR/lib/libarpack.a"
