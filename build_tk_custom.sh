#!/bin/bash
set -e

cd "$HOME"

# 0. Habilitar repo X11 e instalar dependencias X11
pkg install -y x11-repo
pkg install -y libx11 libxft libxext xorgproto

export APP_PREFIX=/data/data/com.diamon.civil/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"
export TMX_PREFIX=/data/data/com.termux/files/usr

mkdir -p "$FAKE_USR/include" "$FAKE_USR/lib"

# 1. Verificar que ya exista el header crítico
find "$TMX_PREFIX/include/X11" -maxdepth 1 \( -name 'X.h' -o -name 'Xlib.h' \)

# 2. Reconstrucción limpia de Tk
rm -rf "$HOME/tk"
git clone https://github.com/tcltk/tk.git -b core-8-6-branch --depth 1
cd "$HOME/tk/unix"
make clean 2>/dev/null || true

export CC=clang
export CPPFLAGS="-I$FAKE_USR/include -I$TMX_PREFIX/include -I$TMX_PREFIX/include/X11"
export CFLAGS="-fPIC -fPIE -Oz -ffile-prefix-map=$DESTDIR="
export LDFLAGS="-pie -Wl,-z,max-page-size=16384 -L$FAKE_USR/lib -L$TMX_PREFIX/lib"
export PKG_CONFIG_PATH="$FAKE_USR/lib/pkgconfig:$TMX_PREFIX/lib/pkgconfig:${PKG_CONFIG_PATH:-}"
export LD_LIBRARY_PATH="$FAKE_USR/lib:$TMX_PREFIX/lib:${LD_LIBRARY_PATH:-}"

echo "Configurando Tk con X11..."
./configure \
  --prefix="$APP_PREFIX" \
  --enable-shared \
  --enable-threads \
  --disable-symbols \
  --with-tcl="$FAKE_USR/lib" \
  CC="$CC" \
  CPPFLAGS="$CPPFLAGS" \
  CFLAGS="$CFLAGS" \
  LDFLAGS="$LDFLAGS"

echo "Compilando Tk (binarios y librerías; se omite la regla de docs, inalcanzable por sandbox de Android)..."
make binaries libraries -j"$(nproc)"

echo "Instalando Tk..."
make install-binaries DESTDIR="$DESTDIR"
make install-libraries DESTDIR="$DESTDIR"
make install-private-headers DESTDIR="$DESTDIR"

echo "Ajustando tkConfig.sh..."
if [ -f "$FAKE_USR/lib/tkConfig.sh" ]; then
  sed -i "s|$HOME/tk/unix|$APP_PREFIX/lib|g" "$FAKE_USR/lib/tkConfig.sh"
  sed -i "s|$HOME/tk|$APP_PREFIX/include|g" "$FAKE_USR/lib/tkConfig.sh"
  sed -i "s|$DESTDIR||g" "$FAKE_USR/lib/tkConfig.sh"
fi

echo "Creando enlace wish..."
if [ -f "$FAKE_USR/bin/wish8.6" ] && [ ! -e "$FAKE_USR/bin/wish" ]; then
  ln -sf wish8.6 "$FAKE_USR/bin/wish"
fi

echo "Verificando Tk..."
ls -lh "$FAKE_USR/lib/libtk8.6.so"
ls -lh "$FAKE_USR/lib/tkConfig.sh"
find "$FAKE_USR/include" \( -name 'tk.h' -o -name 'tkDecls.h' \) | sort
ls -lh "$FAKE_USR/bin"/wish*
