#!/bin/bash
set -e

cd "$HOME"

export APP_PREFIX=/data/data/com.diamon.civil/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"

# CORRECCIÓN: solo limpiamos OpenBLAS, nunca todo fake_root (ahí vive SPOOLES ya compilado)
rm -rf "$HOME/OpenBLAS"
mkdir -p "$FAKE_USR/include" "$FAKE_USR/lib"
git clone https://github.com/OpenMathLib/OpenBLAS.git --depth 1

cd "$HOME/OpenBLAS"

export CC=clang
export HOSTCC=clang
export FC=gfortran
export AR=llvm-ar
export RANLIB=llvm-ranlib
export LD=ld.lld

export CFLAGS="-fPIC -Oz -ffile-prefix-map=$DESTDIR="
export FFLAGS="-fPIC -Oz"
export LDFLAGS="-Wl,-z,max-page-size=16384"

echo "Compilando OpenBLAS con LAPACK (sin ejecutar tests)..."
make \
  TARGET=ARMV8 \
  BINARY=64 \
  ONLYCBLAS=0 \
  NOFORTRAN=0 \
  DYNAMIC_ARCH=1 \
  USE_THREAD=1 \
  NUM_THREADS=8 \
  CROSS=1 \
  CC="$CC" \
  HOSTCC="$HOSTCC" \
  FC="$FC" \
  CFLAGS="$CFLAGS" \
  FFLAGS="$FFLAGS" \
  LDFLAGS="$LDFLAGS" \
  AR="$AR" \
  RANLIB="$RANLIB" \
  LD="$LD" \
  -j"$(nproc)"

echo "Instalando OpenBLAS en fake_root..."
# CORRECCIÓN: Usamos APP_PREFIX. OpenBLAS le concatenará el DESTDIR automáticamente.
make \
  PREFIX="$APP_PREFIX" \
  TARGET=ARMV8 \
  BINARY=64 \
  ONLYCBLAS=0 \
  NOFORTRAN=0 \
  DYNAMIC_ARCH=1 \
  USE_THREAD=1 \
  NUM_THREADS=8 \
  CROSS=1 \
  CC="$CC" \
  HOSTCC="$HOSTCC" \
  FC="$FC" \
  CFLAGS="$CFLAGS" \
  FFLAGS="$FFLAGS" \
  LDFLAGS="$LDFLAGS" \
  AR="$AR" \
  RANLIB="$RANLIB" \
  LD="$LD" \
  install

echo "=== Dependencias ==="
readelf -d "$FAKE_USR/lib/libopenblas.so" | grep NEEDED || true

echo
echo "=== Alineación 16KB ==="
readelf -l "$FAKE_USR/lib/libopenblas.so" | grep LOAD || true

echo
echo "=== Headers ==="
ls -lh "$FAKE_USR/include/cblas.h"
ls -lh "$FAKE_USR/include/lapacke.h"
