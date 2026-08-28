#!/bin/bash
set -euo pipefail
cd "$HOME"

export APP_PREFIX=/data/data/com.diamon.civil/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"
export TMX_PREFIX=/data/data/com.termux/files/usr

mkdir -p "$FAKE_USR/include" "$FAKE_USR/lib"

export CC=clang
export CXX=clang++
export FC=gfortran

export COMMON_CFLAGS="-fPIC -fPIE -Oz -ffile-prefix-map=$DESTDIR= -Wno-int-conversion -Wno-incompatible-pointer-types -Wno-implicit-function-declaration -I$FAKE_USR/include -I$TMX_PREFIX/include"
export COMMON_LDFLAGS="-pie -Wl,-z,max-page-size=16384 -L$FAKE_USR/lib -L$TMX_PREFIX/lib"
export PKG_CONFIG_PATH="$FAKE_USR/lib/pkgconfig:$TMX_PREFIX/lib/pkgconfig:${PKG_CONFIG_PATH:-}"
export LD_LIBRARY_PATH="$FAKE_USR/lib:$TMX_PREFIX/lib:${LD_LIBRARY_PATH:-}"

echo "Descargando SPOOLES 2.2..."
rm -rf "$HOME/SPOOLES.2.2"
mkdir -p "$HOME/SPOOLES.2.2"
cd "$HOME/SPOOLES.2.2"
wget -qO- http://www.netlib.org/linalg/spooles/spooles.2.2.tgz | tar -xzf -

echo "Aplicando Parche de CalculiX (SPOOLEScorrection)..."
wget -qO- http://www.dhondt.de/ccx_2.23.SPOOLEScorrection.tar.bz2 | tar -xjf - -C .

echo "Aplicando correcciones manuales según CalculiX README..."
sed -i 's/drawTree.c/draw.c/g' Tree/src/makeGlobalLib
sed -i 's|# cd misc/src|cd misc/src|g' makefile

echo "Configurando Make.inc para Termux/Android..."
sed -i "s|^ *CC =.*|  CC = $CC|g" Make.inc
# Se escapa el signo $ para que sed inserte literalmente $(OPTLEVEL) y sea make quien lo expanda
sed -i "s|^ *CFLAGS =.*|  CFLAGS = \$(OPTLEVEL) $COMMON_CFLAGS|g" Make.inc
sed -i "s|^ *OPTLEVEL =.*|  OPTLEVEL = -Oz|g" Make.inc
# Inyectar LDFLAGS por si algún sub-Makefile los usa (p.ej. MT)
if grep -q "^ *LDFLAGS =" Make.inc; then
    sed -i "s|^ *LDFLAGS =.*|  LDFLAGS = $COMMON_LDFLAGS|g" Make.inc
else
    echo "  LDFLAGS = $COMMON_LDFLAGS" >> Make.inc
fi

echo "Compilando SPOOLES (Librería Base)..."
make lib -j"$(nproc)"

echo "Compilando SPOOLES Multi-Thread (MT)..."
cd MT/src
make -j"$(nproc)"

echo "Instalando en fake_root..."
mkdir -p "$FAKE_USR/include/spooles"
cd "$HOME/SPOOLES.2.2"

find . -name "*.h" | tar -cf - -T - | (cd "$FAKE_USR/include/spooles" && tar -xf -)

cp spooles.a "$FAKE_USR/lib/libspooles.a"
cp MT/src/spoolesMT.a "$FAKE_USR/lib/libspoolesMT.a"

echo "=== Compilación de SPOOLES Exitosa ==="
ls -lh "$FAKE_USR/lib/libspooles.a"
ls -lh "$FAKE_USR/lib/libspoolesMT.a"
