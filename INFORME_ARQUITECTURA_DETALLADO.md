# Informe Técnico: Análisis de Arquitectura de Binarios y Resolución de Conflictos JNI

## 1. Introducción
Este documento detalla el análisis técnico realizado sobre la estructura de binarios nativos del proyecto "CalculoEstructural", evaluando la estrategia original de enlaces simbólicos frente a la necesidad de compatibilidad con el cargador de librerías de Android (JNI) en versiones modernas (v10 a v15).

## 2. El Modelo Arquitectónico Original
La arquitectura propuesta se basa en la preservación de la integridad de los binarios (OpenCASCADE, Gmsh, CalculiX) mediante un sistema de despliegue en el sistema de archivos del dispositivo (`/data/data/com.diamon.civil/files/usr/`).

### Componentes Clave:
1.  **Nomenclatura Compatible (`jniLibs`):** Android requiere que las librerías en la carpeta `jniLibs` sigan el patrón `lib[nombre].so` y no contengan puntos adicionales (ej. `libTKernel.so.8.0` es rechazado). La solución fue renombrar estos archivos a `libTKernel._so_.so` para forzar su inclusión en el APK.
2.  **Reconstrucción del Entorno (`AssetHelper`):** En tiempo de ejecución, la aplicación extrae estos archivos y crea enlaces simbólicos (`symlinks`) en `usr/lib` con sus nombres y versionados originales (ej. `libTKernel.so.1`).
3.  **Resolución de Dependencias:** Los binarios de terminal (`ccx`, `gmsh`) resuelven sus dependencias a través de estos symlinks utilizando variables de entorno como `LD_LIBRARY_PATH`.

## 3. Conflicto Detectado: La Barrera del Linker de Android
Durante la ejecución de las pruebas en la **UI**, se detectó un cierre inmediato de la aplicación. El análisis reveló que el culpable es el **Linker dinámico de Android** (`/system/bin/linker64`).

### Hallazgos Técnicos:
*   **Aislamiento de Namespaces:** A partir de Android 10, el sistema utiliza "Linker Namespaces" aislados. Cuando Java llama a `System.loadLibrary("gmsh")`, el linker solo busca en la ruta de librerías nativas del APK (`/data/app/.../lib/arm64/`).
*   **Ignorancia de Symlinks:** El linker del sistema **ignora por completo** los enlaces simbólicos creados en el directorio de datos de la app (`usr/lib`) por razones de seguridad (política W^X - Write XOR Execute).
*   **Falla de Enlace:** Como `libgmsh.so` tiene una tabla de dependencias (`NEEDED`) que pide archivos con puntos (ej. `libTKMath.so.`), y esos archivos no existen en la carpeta nativa del APK con ese nombre exacto, el sistema lanza un `UnsatisfiedLinkError` y cierra el proceso.

## 4. Análisis de la Solución de Emergencia (Parcheo)
Para validar que los binarios eran funcionales, se aplicó un parche temporal usando `patchelf`:
1.  Se eliminaron los puntos de los `SONAME` y `NEEDED`.
2.  Esto permitió que el Linker de Android encontrara todo dentro del APK.
3.  **Resultado:** Los cálculos funcionaron, confirmando que la lógica interna es correcta, pero se sacrificó la arquitectura original de "binarios limpios".

## 5. Propuesta de Reversión al Modelo Original
Es posible y deseable volver al modelo anterior, manteniendo los binarios intactos y resolviendo el problema de la UI mediante una **carga manual jerárquica**.

### Plan de Acción para la Reversión:
1.  **Restauración de Binarios:** Revertir los cambios de `patchelf` y restaurar los nombres `._so_.so` en `jniLibs`.
2.  **Carga Manual en Java:** En lugar de confiar en que Android resuelva las dependencias, modificaremos `NativeFeaCore.java` para cargar cada dependencia en el orden exacto usando `System.load()` con la **ruta completa al archivo extraído**.
3.  **Orden de Precedencia:**
    *   Cargar librerías base (OCCT: `TKernel`, `TKMath`, etc.).
    *   Cargar librerías intermedias (`openblas`, `gmsh`).
    *   Cargar el núcleo del proyecto (`calculoestructural`).

## 6. Conclusión
La arquitectura de enlaces simbólicos es la más robusta para mantener un entorno tipo Linux funcional en Android. La falla en la UI no se debió a una mala arquitectura, sino a las restricciones crecientes del sistema operativo sobre cómo los procesos JNI acceden al sistema de archivos.

Al cargar las librerías manualmente por su ruta absoluta desde el código Java, podemos satisfacer tanto al Linker de Android (UI) como a los scripts de terminal, manteniendo los binarios originales sin modificaciones internas.

---
**Documento generado para el equipo de desarrollo de CalculoEstructural.**
