# Reporte de Reparación: Fallas Críticas en Módulos de Cálculo (JNI/Binarios)

## 1. Descripción de la Falla
Se reportó que la aplicación se cerraba inesperadamente (crash) al intentar realizar cálculos en los módulos **Estructural** y **3D Sólido**, mientras que el módulo de Terminal funcionaba correctamente.

## 2. Diagnóstico y Hallazgos
Para identificar la causa, se implementó un sistema de **Depuración Automática (`AutoTester`)** y se ejecutaron pruebas en **Firebase Test Lab** (Pixel 2, Android 11). Los logs revelaron múltiples errores de carga de librerías nativas (`java.lang.UnsatisfiedLinkError`):

*   **Error de Nomenclatura (SONAME):** Librerías de OpenCASCADE tenían nombres internos corruptos con puntos finales (ej. `libTKernel.so.`), lo que impedía que el cargador de Android las reconociera.
*   **Dependencias Versionadas no Encontradas:** Librerías como `libgmsh.so` y `libTKService.so` buscaban dependencias con versiones específicas (ej. `libfreeimage.so.3`, `libexpat.so.1`, `libz.so.1`) que no coincidían con los archivos `.so` unificados en la carpeta `jniLibs`.
*   **Enlaces de Sistema Incorrectos:** Referencias a librerías estándar de Android (ej. `libEGL.so.1`) fallaban por buscar sufijos de versión no soportados por el sistema operativo en modo de carga dinámica simple.

## 3. Soluciones Implementadas

### A. Normalización Agresiva de Binarios (`fix_libs.sh`)
Se creó un script de automatización que utiliza la herramienta `patchelf` para corregir los 150+ archivos binarios en `app/src/main/jniLibs/arm64-v8a/`:
1.  **Limpieza de SONAME:** Se redefinió el nombre interno de cada librería para que coincida exactamente con su nombre de archivo.
2.  **Mapeo de Dependencias (NEEDED):** Se actualizaron las tablas de importación de cada `.so` para que apunten a los archivos locales existentes (ej. `libfreeimage.so.3` → `libfreeimage.so`).
3.  **Corrección de Librerías de Sistema:** Se forzó el enlace de dependencias críticas de Android (`libz`, `libEGL`, `libGLESv2`) a sus versiones estándar sin sufijo.

### B. Optimización del Cargador Java
Se modificó `NativeFeaCore.java` y `CalculixExecutor.java` para:
*   Cargar las librerías base de OpenCASCADE en el orden correcto antes de intentar cargar el núcleo de cálculo.
*   Utilizar bloques `try-catch (Throwable)` para reportar errores de enlace detallados sin cerrar la app inmediatamente.

### C. Implementación de Auto-Tester
Se integró la clase `com.diamon.civil.test.AutoTester` que:
*   Inicia una simulación de entrada de datos a los 3 segundos de carga.
*   Verifica la integridad de la carga de librerías nativas una por una.
*   Presiona los botones de UI para asegurar que el flujo completo (Input → Native Code → Result) sea estable.

## 4. Resultados Finales
*   **Carga Exitosa:** Todas las librerías nativas (`gmsh`, `CalculiX`, `OCCT`) cargan sin errores.
*   **Estabilidad:** Los módulos UI ya no presentan crashes al ejecutar el solver.
*   **Trazabilidad:** Se añadieron logs detallados bajo la etiqueta `AutoTester` para facilitar el mantenimiento futuro.

## 5. Archivos Creados/Modificados
*   `app/src/main/java/com/diamon/civil/test/AutoTester.java` (Nuevo)
*   `app/src/main/jniLibs/arm64-v8a/*.so` (Parcheados/Normalizados)
*   `app/src/main/java/com/diamon/civil/ui/MainActivity.java` (Integración de test)
*   `app/src/main/java/com/diamon/civil/engine/NativeFeaCore.java` (Carga robusta)
*   `fix_libs.sh` (Script de reparación de binarios)
*   `FIREBASE_TESTING_PROCEDURE.md` (Documentación actualizada)
