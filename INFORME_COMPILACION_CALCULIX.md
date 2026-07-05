# Informe de compilación de CalculiX 2.23 en Termux/Android

Este informe resume el proceso automatizado de compilación de CalculiX 2.23 para un entorno Android/Termux, con instalación final en `fake_root`. El proceso asegura compatibilidad con sistemas modernos (AArch64, alineación de 16 KB) mediante el uso de flags específicos (`-pie`, `-Wl,-z,max-page-size=16384`, `-ffile-prefix-map`).

## Dependencias

Todas las dependencias se compilan e instalan en el prefijo `$FAKE_USR` para asegurar la integridad del árbol de instalación.

### 1. SPOOLES 2.2
- **Proceso:** Descarga, aplicación de parches de CalculiX, configuración de `Make.inc` (usando `-Oz`, `-fPIC`, `-fPIE`), compilación de librería base (`make lib`) y multihilo (`MT/src`).
- **Instalación:** Cabeceras en `$FAKE_USR/include/spooles` y librerías (`libspooles.a`, `libspoolesMT.a`) en `$FAKE_USR/lib`.

### 2. OpenBLAS
- **Proceso:** Compilación con soporte LAPACK (`NOFORTRAN=0`), optimización ARMv8, multihilo (`USE_THREAD=1`) y alineación de 16 KB en el enlazador.
- **Instalación:** Instalada como biblioteca compartida (`libopenblas.so`) en `$FAKE_USR/lib`.

### 3. ARPACK-NG
- **Proceso:** Configuración vía CMake enlazando contra `libopenblas.so`, deshabilitando ejemplos y pruebas.
- **Instalación:** Instalada como librería estática (`libarpack.a`) en `$FAKE_USR/lib`.

## Parches y Correcciones en CalculiX (Aplicados en build_calculix_custom.sh)

Para lograr una compilación exitosa en el entorno Termux, se aplican los siguientes ajustes automáticos:

1.  **OpenMP (`omp_lib`):** Reemplazo de `use omp_lib` por `include "omp_lib.h"` y generación de un header con declaraciones necesarias.
2.  **`matvec_struct.f`:** Eliminación de `IMPLICIT NONE` para evitar conflictos con declaraciones de atributos.
3.  **`readnewmesh.c`:** Corrección de `return NULL;` a `return;` en función `void`.

## Proceso de Compilación de CalculiX

El proceso automatizado genera un `Makefile_android` personalizado:

- **Flags clave:** `-fopenmp=libomp`, `-pthread`, `-ffile-prefix-map=$DESTDIR=`, `-pie`, `-Wl,-z,max-page-size=16384`.
- **Enlazado:** Se utiliza `gfortran` (`FC`) como enlazador final para resolver dependencias de Fortran, incluyendo explícitamente `-lomp`, `-lopenblas`, y las librerías estáticas de SPOOLES y ARPACK.

El binario resultante (`ccx`) se instala en `$FAKE_USR/bin/ccx`.
