# Informe de compilación de CalculiX 2.23 en Termux/Android

Este informe resume el proceso de compilación de CalculiX 2.23 para un entorno Android/Termux con instalación final en `fake_root`, incluyendo dependencias, fallas encontradas, parches aplicados y verificación final del binario. El flujo de instalación manual del ejecutable es coherente con las instrucciones oficiales de CalculiX, que indican compilar `ccx_2.23` y luego mover el ejecutable al prefijo de instalación deseado.

## Objetivo
El objetivo fue compilar `ccx` para una aplicación Android nativa usando un árbol de instalación propio en:
`$HOME/fake_root/data/data/com.diamon.civil/files/usr`

Se buscó cumplir además con estos requisitos técnicos:
- Ejecutable PIE para Android moderno.
- Alineación de segmentos a 16 KB para AArch64/Android 15+.
- Integración con SPOOLES, ARPACK y OpenBLAS compilados previamente.
- Soporte multihilo mediante `libspoolesMT.a` y `-DUSE_MT`.

## Dependencias compiladas previamente

### SPOOLES 2.2
Se compiló primero la librería base `spooles.a` y después la librería multihilo `spoolesMT.a`, instalando cabeceras bajo `include/spooles` y ambas librerías en `fake_root/lib`. Fue necesario usar ambas librerías para resolver símbolos como `DVfill`, `Graph_new` e `InpMtx_fullAdjacency`.

### OpenBLAS
OpenBLAS se compiló como biblioteca compartida (`libopenblas.so`) usando `-shared` y manteniendo la alineación de 16 KB en el enlazado. La verificación posterior mostró correctamente una dependencia dinámica sobre `libopenblas.so` en el binario final de CalculiX.

### ARPACK-NG
ARPACK se compiló desde arpack-ng usando CMake y enlazando explícitamente contra el `libopenblas.so` generado previamente. La librería final se instaló como archivo estático `libarpack.a` dentro de `fake_root/lib`.

## Problemas encontrados en CalculiX

### 1. Falta de omp_lib.mod
El módulo `omp_lib.mod` no estaba disponible.
*   **Corrección aplicada:**
    Se reemplazó `use omp_lib` por `include "omp_lib.h"` y se generó un archivo `omp_lib.h` con las declaraciones mínimas.

### 2. Error en readnewmesh.c
Error: `readnewmesh.c:468:3: error: void function 'readnewmesh' should not return a value`.
*   **Corrección aplicada:**
    `sed -i '468s/return NULL;/return;/' readnewmesh.c`

### 3. Error en matvec_struct.f
Error: `Error: IMPLICIT NONE statement at (1) cannot follow attribute declaration statement at (2)`.
*   **Corrección aplicada:**
    Se eliminó `IMPLICIT NONE` del archivo mediante: `sed -i '/IMPLICIT[[:space:]][[:space:]]*NONE/Id' matvec_struct.f`

### 4. Enlace incompleto con SPOOLES multihilo
Faltaban símbolos base al enlazar solo con `libspoolesMT.a`.
*   **Corrección aplicada:**
    Se ajustó `LIBS` para incluir ambas librerías: `libspoolesMT.a` y `libspooles.a` (en ese orden).

## Script final consolidado de CalculiX

```bash
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

sed -i 's/use omp_lib/include "omp_lib.h"/gI' *.f
cat > omp_lib.h << 'EOF2'
      integer omp_get_num_threads
      integer omp_get_thread_num
      integer omp_get_max_threads
      external omp_set_num_threads
EOF2

sed -i '/IMPLICIT[[:space:]][[:space:]]*NONE/Id' matvec_struct.f
sed -i '468s/return NULL;/return;/' readnewmesh.c

echo "Generando Makefile_android..."

COMMON_CFLAGS='-Wall -O2 -fPIC -fPIE -I'"$FAKE_USR"'/include -I'"$FAKE_USR"'/include/spooles -fopenmp -DARCH="Linux" -DSPOOLES -DARPACK -DMATRIXSTORAGE -DNETWORKOUT -DUSE_MT'
COMMON_FFLAGS='-Wall -O2 -fPIC -fPIE -cpp -fallow-argument-mismatch -Wno-error -fopenmp'
COMMON_LDFLAGS='-pie -Wl,-z,max-page-size=16384 -L'"$FAKE_USR"'/lib -fopenmp'
LIBS="$FAKE_USR/lib/libspoolesMT.a $FAKE_USR/lib/libspooles.a $FAKE_USR/lib/libarpack.a -lopenblas -lgfortran -lpthread -lm -lc"

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
printf 'ccx_2.23: $(OCCXMAIN) ccx_2.23.a
\t$(CC) $(LDFLAGS) -o $@ $(OCCXMAIN) ccx_2.23.a $(LIBS)

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
```

## Resultado final
El proceso dejó un binario `ccx` funcional dentro de `fake_root`, enlazado con las dependencias necesarias y verificado como ejecutable AArch64 con alineación de 16 KB.
