#!/bin/bash
# run_calculix_tests.sh

cd ~

echo "================================================="
echo "  Ejecutando Prueba SECUENCIAL (1 Núcleo)"
echo "================================================="
export OMP_NUM_THREADS=1
time ccx -i test_calculix

echo ""
echo "================================================="
echo "  Ejecutando Prueba PARALELA (4 Núcleos)"
echo "================================================="
export OMP_NUM_THREADS=4
time ccx -i test_calculix
