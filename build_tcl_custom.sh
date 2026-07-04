#!/bin/bash
set -e

cd "$HOME"

export APP_PREFIX=/data/data/com.diamon.civil/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"
export TMX_PREFIX=/data/data/com.termux/files/usr

mkdir -p "$FAKE_USR/lib" "$FAKE_USR/bin" "$FAKE_USR/include"

# Limpiar clon previo si quieres reconstrucción total
rm -rf "$HOME/tcl"

echo "Clonando Tcl oficial..."
git clone https://github.com/tcltk/tcl.git -b core-8-6-branch --depth 1
cd "$HOME/tcl/unix"

echo "Limpiando build previo..."
make clean 2>/dev/null || true

export CC=clang
export CPPFLAGS="-I$FAKE_USR/include -I$TMX_PREFIX/include"
export CFLAGS="-fPIC -fPIE -Oz -ffile-prefix-map=$DESTDIR="
export LDFLAGS="-pie -Wl,-z,max-page-size=16384 -L$FAKE_USR/lib -L$TMX_PREFIX/lib"
export PKG_CONFIG_PATH="$FAKE_USR/lib/pkgconfig:$TMX_PREFIX/lib/pkgconfig:${PKG_CONFIG_PATH:-}"

echo "Configurando Tcl..."
./configure \
  --prefix="$APP_PREFIX" \
  --enable-shared \
  --disable-symbols \
  CC="$CC" \
  CPPFLAGS="$CPPFLAGS" \
  CFLAGS="$CFLAGS" \
  LDFLAGS="$LDFLAGS"

echo "Compilando Tcl..."
make -j"$(nproc)"

echo "Instalando binarios y librerías sin docs..."
make install-binaries DESTDIR="$DESTDIR"
make install-libraries DESTDIR="$DESTDIR"

echo "Instalando headers privados..."
make install-private-headers DESTDIR="$DESTDIR"

echo "Ajustando tclConfig.sh..."
if [ -f "$FAKE_USR/lib/tclConfig.sh" ]; then
  sed -i "s|$HOME/tcl/unix|$APP_PREFIX/lib|g" "$FAKE_USR/lib/tclConfig.sh"
  sed -i "s|$HOME/tcl|$APP_PREFIX/include|g" "$FAKE_USR/lib/tclConfig.sh"
  sed -i "s|$DESTDIR||g" "$FAKE_USR/lib/tclConfig.sh"
fi

echo "Creando enlace tclsh si hace falta..."
if [ -f "$FAKE_USR/bin/tclsh8.6" ] && [ ! -e "$FAKE_USR/bin/tclsh" ]; then
  ln -sf tclsh8.6 "$FAKE_USR/bin/tclsh"
fi

echo "Verificando instalación..."
ls -lh "$FAKE_USR/lib/libtcl8.6.so"
ls -lh "$FAKE_USR/lib/tclConfig.sh"
find "$FAKE_USR/include" -name 'tcl.h' | head -n 5
ls -lh "$FAKE_USR/bin"/tclsh*
