#!/bin/bash
set -e

cd "$HOME"

export APP_PREFIX=/data/data/com.diamon.civil/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"

export CC=clang
export FC=gfortran

echo "Descargando código fuente de CalculiX 2.23..."
rm -rf "$HOME/CalculiX"
mkdir -p "$HOME/CalculiX"
cd "$HOME/CalculiX"
wget -qO- http://www.dhondt.de/ccx_2.23.src.tar.bz2 | tar -xjf -

SRC_DIR="$HOME/CalculiX/CalculiX/ccx_2.23/src"
cd "$SRC_DIR"

echo "Aplicando parches necesarios..."

# 1) OpenMP: reemplazo de use omp_lib porque omp_lib.mod no está disponible
sed -i 's/use omp_lib/include "omp_lib.h"/gI' *.f
cat > omp_lib.h << 'EOF'
      integer omp_get_num_threads
      integer omp_get_thread_num
      integer omp_get_max_threads
      external omp_set_num_threads
EOF

# 2) matvec_struct.f: quitar IMPLICIT NONE
sed -i '/IMPLICIT[[:space:]][[:space:]]*NONE/Id' matvec_struct.f

# 3) readnewmesh.c: parche puntual, readnewmesh es void
sed -i '468s/return NULL;/return;/' readnewmesh.c

echo "Generando Makefile_android..."

# Ajuste 1: Se agrega -pthread a CFLAGS
# Ajuste 2 (NUEVO): -ffile-prefix-map elimina el prefijo de fake_root de cualquier
# ruta que quede embebida internamente (p. ej. via __FILE__ o futuros símbolos de debug)
COMMON_CFLAGS='-Wall -O2 -fPIC -fPIE -ffile-prefix-map='"$DESTDIR"'= -I'"$FAKE_USR"'/include -I'"$FAKE_USR"'/include/spooles -fopenmp=libomp -pthread -DARCH="Linux" -DSPOOLES -DARPACK -DMATRIXSTORAGE -DNETWORKOUT -DUSE_MT'
COMMON_FFLAGS='-Wall -O2 -fPIC -fPIE -cpp -fallow-argument-mismatch -Wno-error -fopenmp'
COMMON_LDFLAGS='-pie -Wl,-z,max-page-size=16384 -L'"$FAKE_USR"'/lib -fopenmp -pthread'

# Ajuste 3: Se agrega -lomp explícitamente para que gfortran lo encuentre
LIBS="$FAKE_USR/lib/libarpack.a $FAKE_USR/lib/libspoolesMT.a $FAKE_USR/lib/libspooles.a -lopenblas -lomp -lm -lc"

rm -f Makefile_android
printf '.c.o :
\t$(CC) $(CFLAGS) -c $<
' >> Makefile_android
printf '.f.o :
\t$(FC) $(FFLAGS) -c $<

' >> Makefile_android
printf 'include Makefile.inc

' >> Makefile_android
printf 'SCCXMAIN = ccx_2.23.c
' >> Makefile_android
printf 'OCCXF = $(SCCXF:.f=.o)
' >> Makefile_android
printf 'OCCXC = $(SCCXC:.c=.o)
' >> Makefile_android
printf 'OCCXMAIN = $(SCCXMAIN:.c=.o)

' >> Makefile_android
# CORRECCIÓN CLAVE: Usar FC (gfortran) para el enlace final en lugar de CC (clang)
printf 'ccx_2.23: $(OCCXMAIN) ccx_2.23.a
\t$(FC) $(LDFLAGS) -o $@ $(OCCXMAIN) ccx_2.23.a $(LIBS)

' >> Makefile_android
printf 'ccx_2.23.a: $(OCCXF) $(OCCXC)
\tar vr $@ $?
' >> Makefile_android

echo "Iniciando compilación de CalculiX..."
make -f Makefile_android \
  CC="$CC" \
  FC="$FC" \
  CFLAGS="$COMMON_CFLAGS" \
  FFLAGS="$COMMON_FFLAGS" \
  LDFLAGS="$COMMON_LDFLAGS" \
  LIBS="$LIBS" \
  -j"$(nproc)"

echo "Instalando ejecutable ccx en fake_root..."
: "${FAKE_USR:?FAKE_USR no está definido}"
mkdir -p "$FAKE_USR/bin"
cp "$SRC_DIR/ccx_2.23" "$FAKE_USR/bin/ccx"
chmod 755 "$FAKE_USR/bin/ccx"

echo "=== Compilación de CalculiX Exitosa ==="
ls -lh "$FAKE_USR/bin/ccx"
echo
echo "=== ELF Header ==="
readelf -h "$FAKE_USR/bin/ccx" | head
echo
echo "=== Dependencias dinámicas ==="
readelf -d "$FAKE_USR/bin/ccx" | grep NEEDED || true
echo
echo "=== Alineación 16KB ==="
readelf -l "$FAKE_USR/bin/ccx" | grep LOAD
