#!/bin/bash
set -e

export LD_LIBRARY_PATH="$HOME/android_sim_libs"
TEST_DIR="$HOME/android_sim_test"
cd "$TEST_DIR" || exit 1

echo "=== Verificando nproc real en este momento ==="
nproc
cat /proc/cpuinfo | grep -c processor

echo
echo "=== Corriendo CalculiX SIN filtrar salida (ver todo) ==="
export OMP_NUM_THREADS=4
"$HOME/android_sim_libs/libccx.so" -i cantilever_test
echo "Código de salida: $?"

echo
echo "=== Verificando archivos generados tras la corrida ==="
ls -lh cantilever_test.dat cantilever_test.frd cantilever_test.sta 2>/dev/null

echo
echo "=== Contenido real del .dat (si existe) ==="
cat cantilever_test.dat 2>/dev/null | head -20 || echo "ARCHIVO VACÍO O NO EXISTE"

echo
echo "=== Verificando si el .frd sí tiene resultados (formato alternativo) ==="
wc -l cantilever_test.frd 2>/dev/null || echo "No hay .frd"
grep -c "DISP" cantilever_test.frd 2>/dev/null || echo "0"
