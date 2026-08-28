#!/bin/bash
set -e

cd "$HOME" || exit 1

# Definición de rutas personalizadas
export APP_PREFIX=/data/data/com.diamon.civil/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"
export TMX_PREFIX=/data/data/com.termux/files/usr

mkdir -p "$FAKE_USR/lib" "$FAKE_USR/include"

# Compiladores
export CC=clang
export CXX=clang++

# Banderas de compilación
export COMMON_CPPFLAGS="-I$FAKE_USR/include -I$TMX_PREFIX/include"
export COMMON_CFLAGS="-fPIC -fPIE -Oz -ffile-prefix-map=$DESTDIR="
export COMMON_CXXFLAGS="-fPIC -fPIE -Oz -ffile-prefix-map=$DESTDIR="

# Banderas de enlace (Alineación a 16KB para Android)
export BASE_LDFLAGS="-Wl,-z,max-page-size=16384 -L$FAKE_USR/lib -L$TMX_PREFIX/lib"
export EXE_LDFLAGS="-pie $BASE_LDFLAGS"
export SHARED_LDFLAGS="$BASE_LDFLAGS"

export PKG_CONFIG_PATH="$FAKE_USR/lib/pkgconfig:$TMX_PREFIX/lib/pkgconfig:${PKG_CONFIG_PATH:-}"

# ==========================================
# Compilación e Instalación de libaec
# ==========================================
echo "=== Clonando libaec ==="
rm -rf "$HOME/libaec"
git clone https://github.com/Deutsches-Klimarechenzentrum/libaec.git --depth 1
cd "$HOME/libaec" || exit 1

mkdir -p build && cd build || exit 1
rm -rf ./*

echo "=== Configurando CMake para libaec ==="
cmake .. \
  -G "Unix Makefiles" \
  -DCMAKE_INSTALL_PREFIX="$APP_PREFIX" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER="$CC" \
  -DCMAKE_CXX_COMPILER="$CXX" \
  -DCMAKE_C_FLAGS="$COMMON_CFLAGS $COMMON_CPPFLAGS" \
  -DCMAKE_CXX_FLAGS="$COMMON_CXXFLAGS $COMMON_CPPFLAGS" \
  -DCMAKE_EXE_LINKER_FLAGS="$EXE_LDFLAGS" \
  -DCMAKE_SHARED_LINKER_FLAGS="$SHARED_LDFLAGS" \
  -DBUILD_SHARED_LIBS=ON \
  -DBUILD_TESTING=OFF

echo "=== Compilando libaec ==="
cmake --build . --parallel "$(nproc)"

echo "=== Instalando libaec en fake_root ==="
DESTDIR="$DESTDIR" cmake --install .

# ==========================================
# Verificación de la instalación
# ==========================================
echo
echo "=== Verificando librerías generadas ==="
ls -lh "$FAKE_USR/lib/libaec.so"
ls -lh "$FAKE_USR/lib/libsz.so"

echo
echo "=== Verificando Headers instalados ==="
find "$FAKE_USR/include" \( -name 'libaec.h' -o -name 'szlib.h' \) | sort

echo
echo "=== Alineación a 16KB de segmentos ELF ==="
readelf -l "$FAKE_USR/lib/libsz.so" | grep LOAD
