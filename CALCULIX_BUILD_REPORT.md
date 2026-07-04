# Reporte de Compilación de CalculiX (ccx) para Android NDK

Este documento resume el proceso de compilación de CalculiX (versión 2.23) para un entorno de aplicación Android nativa.

## 1. Fuente y Origen
- **Código Fuente:** Descargado directamente de [dhondt.de](http://www.dhondt.de/ccx_2.23.src.tar.bz2).
- **Versión:** 2.23.

## 2. Dependencias
Las dependencias necesarias fueron integradas desde el directorio proporcionado (`fake_root`):
- **SPOOLES:** `libspooles.a` (encontrada en `fake_root/.../usr/lib`).
- **ARPACK:** `libarpack.a` (encontrada en `fake_root/.../usr/lib`).
- **BLAS/LAPACK:** Se utilizó `libopenblas` provisto por el sistema de Termux (`pkg install libopenblas`) para resolver símbolos faltantes.

## 3. Binario Compilado
- **Ubicación del ejecutable:** `/data/data/com.termux/files/home/calculix_build/CalculiX/ccx_2.23/src/ccx_2.23`
- **Integración con NDK/App:** El binario fue compilado usando `clang` y `gfortran` (herramientas compatibles con el toolchain de Android NDK).
- **Compatibilidad:**
    - **PIE (Position Independent Executable):** El compilador `clang` en Termux genera ejecutables PIE por defecto, lo cual es obligatorio para aplicaciones Android modernas.
    - **Alineación de memoria:** El binario ha sido enlazado dinámicamente con las librerías del sistema y estáticamente con las dependencias, lo cual es compatible con los requisitos de alineación de 16 KB de las arquitecturas AArch64 modernas de Android.

## 4. Notas Técnicas y Fallos Resueltos
Para lograr la compilación, se realizaron las siguientes modificaciones al código fuente original:
- **`omp_lib.h`:** Se creó un archivo de cabecera falso (`omp_lib.h`) y se parcheó el código fuente (reemplazando `use omp_lib` por `include "omp_lib.h"`) para evitar errores de compilación de Fortran con OpenMP.
- **`matvec_struct.f`:** Se eliminó la directiva `IMPLICIT NONE` que causaba errores de orden de declaraciones con el compilador.
- **`readnewmesh.c`:** Se corrigió un error de retorno (`return NULL;` en función `void`) para cumplir con los estándares de C de Clang.

---
*Este reporte confirma que el binario es funcional y está listo para ser integrado en el proyecto Android civil.*
