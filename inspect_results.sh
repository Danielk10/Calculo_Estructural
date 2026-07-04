#!/bin/bash
cd "$HOME/android_sim_test"

echo "=== Bloques de resultados en el .frd ==="
grep -E "DISP|STRESS|^  100C" cantilever_test.frd | head -10

echo
echo "=== Primeros valores de desplazamiento reales ==="
grep -A 5 " -4  DISP" cantilever_test.frd | head -20

echo
echo "=== Confirmando que hay valores no-cero (análisis real, no vacío) ==="
awk '{print $2, $3, $4}' cantilever_test.frd | grep -E "^-?[0-9]" | sort -u | tail -10
