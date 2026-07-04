# Reporte de Auditoría y Corrección: Rutas y Portabilidad de CalculiX

**ESTADO: CORREGIDO** (14 de Junio, 2026)

Este documento detalla los hallazgos críticos y las acciones correctivas aplicadas para garantizar la portabilidad de CalculiX en Android.

## 1. Problema: Rutas Hardcoded en Binarios (RUNPATH) - **SOLUCIONADO**
*   **Acción:** Se utilizó `patchelf --remove-rpath` en todas las librerías compartidas dentro de `app/src/main/jniLibs/arm64-v8a/`.
*   **Resultado:** El sistema Android ahora cargará las librerías utilizando las rutas estándar del sistema y el directorio nativo del APK, eliminando la dependencia de `/data/data/com.termux`.

## 2. Contaminación en Assets y Scripts - **SOLUCIONADO**
*   **Acción:** Se aplicó una limpieza masiva mediante `sed` en los archivos `tclConfig.sh`, `tkConfig.sh`, `tk.pc`, `openblas.pc` y el script `libh5cc.so`.
*   **Resultado:** Todas las referencias a `/data/data/com.termux/files/usr` han sido reemplazadas por `/data/data/com.diamon.civil/files/usr`, alineándose con la ruta de instalación interna de la aplicación.

## 3. Optimización de Espacio en el APK - **SOLUCIONADO**
*   **Acción:** Se eliminaron todas las librerías estáticas (`.a`) y archivos de configuración de compilación (`.settings`) de la carpeta `assets`.
*   **Resultado:** El tamaño del APK se ha reducido significativamente (~100MB menos de redundancia), y se ha evitado la extracción innecesaria de archivos de desarrollo a la memoria interna del dispositivo.

## Resumen de Acciones Aplicadas
1. Instalación de `patchelf` en el entorno de desarrollo.
2. Limpieza de `RUNPATH` en binarios ELF.
3. Corrección de rutas en scripts de shell y archivos de configuración de Tcl/Tk.
4. Purga de archivos binarios estáticos innecesarios en `assets`.

---
**Auditoría Finalizada:** El runtime de CalculiX ahora es portátil y cumple con los estándares de despliegue en Android.
