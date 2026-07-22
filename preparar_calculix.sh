#!/bin/bash
# preparar_calculix.sh

echo "==============================================="
echo "Preparando dependencias efímeras de CalculiX..."
echo "==============================================="

sudo apt-get update
sudo apt-get install -y liblapack3 libarpack2 libgfortran5 libgomp1

echo "==============================================="
echo "¡Dependencias instaladas!"
echo "Puedes ejecutar pruebas de rendimiento con el script:"
echo "  bash ~/run_calculix_tests.sh"
echo ""
echo "O ejecutar tu propio modelo:"
echo "  export OMP_NUM_THREADS=4  # Para 4 núcleos"
echo "  export OMP_NUM_THREADS=1  # Para modo secuencial"
echo "  ccx <nombre_archivo_sin_extension>"
echo "==============================================="
