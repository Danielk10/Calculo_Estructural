# Reporte Técnico: Implementación del Pipeline de Simulación (Java Native)

Este documento detalla los cambios realizados para integrar el pipeline de simulación estructural dentro de la aplicación, garantizando paridad funcional con el script original `run_simulation_test.sh` y mejorando la estabilidad del entorno.

## 1. Contexto y Objetivos
El objetivo fue portar la lógica de pruebas de ingeniería de un entorno de Bash/Python (Termux) a una implementación nativa en Java/Kotlin dentro de la aplicación, asegurando una ejecución autónoma y robusta en el entorno sandbox de Android, sin depender de scripts externos.

## 2. Cambios Implementados

### A. Integración del Pipeline de Simulación (`SimulationTestManager`)
Se implementó un nuevo paquete `com.diamon.civil.test.simulation` que encapsula todo el proceso de simulación:
- **`SimulationTestManager.java`:** Orquestador principal que replica los pasos del script `.sh`. Ejecuta el pre-procesamiento, mallado (Gmsh), ensamblado físico (CalculiX) y post-procesamiento.
- **`InpAssembler.java`:** Sustituye la lógica de pre-procesamiento de Python. Extrae dinámicamente los sets de nodos (`NSET`) a partir de las superficies físicas (`ELSET`) definidas en el archivo `.geo`, limpia elementos incompatibles (tipo `CPS3`) y ensambla el archivo `.inp` final incluyendo los bloques de física necesarios (`*MATERIAL`, `*STEP`, `*BOUNDARY`, etc.).
- **`FrdParser.java`:** Sustituye el parser de Python. Lee el archivo de resultados binario/texto `.frd` y extrae los desplazamientos nodales para mostrar un resumen legible en la terminal de la app.

### B. Estabilización del Entorno de Ejecución
Se ajustaron los parámetros de invocación de los binarios nativos para replicar fielmente el entorno exitoso de Termux:
- **Variables de Entorno:** Se configuraron explícitamente `PATH`, `LD_LIBRARY_PATH` y `OMP_NUM_THREADS` (fijado a 4) en `ProcessBuilder` para asegurar que `gmsh` y `ccx` carguen las librerías correctas y se ejecuten en paralelo de forma estable.
- **Resolución de Dependencias:** Se movieron y organizaron las cabeceras (`.h`, `.hxx`) necesarias para la compilación nativa en `app/src/main/cpp/include/`, permitiendo una compilación autocontenida y sin dependencias externas.

### C. Depuración y Estabilidad de UI
- **Corrección de Condición de Carrera:** Se movió la ejecución del `AutoTester` para que ocurra solo **después** de la preparación exitosa de los activos nativos (`ensureRuntimeReady`), evitando cierres inesperados al inicio.
- **Terminal Mejorada:** Se redirigió la salida (`stdout` y `stderr`) de los binarios hacia la terminal en pantalla de la app, permitiendo diagnósticos inmediatos.
- **Manejo de Errores:** Implementación de bloques `try-catch` para evitar crashes nativos no controlados.

## 3. Guía de Uso
1. Abre la pestaña **Terminal** en la aplicación.
2. Ejecuta el comando:
   ```bash
   run-sim-test
   ```
3. El sistema generará `cantilever.geo`, realizará el mallado con `gmsh`, ejecutará la simulación con `ccx` y mostrará los resultados directamente en la consola de la app.

## 4. Estado del Sistema
*   **Integridad:** Total. Todos los archivos de simulación (`.inp`, `.dat`, `.frd`, etc.) se generan en la carpeta de archivos privados de la aplicación.
*   **Binarios:** No se han realizado modificaciones en los binarios originales, asegurando su integridad funcional.
*   **Documentación:** `GUIA_PRUEBAS_INGENIERIA.md` ha sido actualizada con este flujo de trabajo.
