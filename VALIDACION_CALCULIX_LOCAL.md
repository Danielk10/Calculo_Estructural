# Validación Local con CalculiX

Este documento registra la configuración y ejecución de pruebas locales utilizando **CalculiX** en el entorno de desarrollo. 

## Propósito
El objetivo de utilizar CalculiX a nivel de sistema (local) es **validar los algoritmos de análisis de elementos finitos (FEA) y modelos estructurales** antes de proceder con su implementación definitiva en la **App Nativa de Android**. Esto permite verificar la precisión matemática, afinar los procesos iterativos y medir el rendimiento del motor fuera del entorno móvil para aislar problemas.

## Proceso Realizado

1. **Gestión de Scripts y Ejemplos**
   - Se incorporaron al control de versiones los scripts de preparación (`preparar_calculix.sh`) y prueba (`run_calculix_tests.sh`), así como el archivo de entrada de ejemplo (`test_calculix.inp`). Estos proveen una forma estandarizada de verificar el solver localmente.

2. **Instalación de Dependencias**
   - Mediante el script `preparar_calculix.sh`, se automatizó la instalación de bibliotecas fundamentales del sistema (Linux) requeridas por CalculiX para operaciones de álgebra lineal y paralelismo: `liblapack3`, `libarpack2`, `libgfortran5` y `libgomp1`.

3. **Ejecución de Pruebas de Rendimiento**
   - Utilizando el script `run_calculix_tests.sh`, se evaluó el modelo `test_calculix.inp` para medir el tiempo de respuesta en dos modalidades:
     * **Prueba Secuencial (1 Núcleo):** El solver completó la rutina estática en **0.28 segundos**.
     * **Prueba Paralela (4 Núcleos):** Configurando `OMP_NUM_THREADS=4`, el tiempo de procesamiento se redujo a tan solo **0.026 segundos**.
   - **Conclusión:** El motor CalculiX demostró alta capacidad de escalamiento en multiprocesamiento. Las validaciones matemáticas son correctas, allanando el camino para integrar estas rutinas mediante JNI/NDK en Android sin sorpresas algorítmicas.

4. **Verificación del Proyecto Android**
   - A la par de estas pruebas locales, se liberó la caché del proyecto y se compiló exitosamente (`assembleDebug`) la App en Android. Esto confirma que el ecosistema completo está sincronizado y libre de errores.

*Reporte documentado en el sistema durante el flujo de trabajo de integración continua local.*
