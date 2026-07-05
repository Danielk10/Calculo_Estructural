# Resumen de Compilación e Instalación de SPOOLES 2.2

## Descripción
Se ha automatizado la descarga, parcheado, configuración, compilación e instalación de SPOOLES 2.2 para entornos Android/Termux, aplicando las correcciones proporcionadas por CalculiX.

## Fuentes
- **SPOOLES 2.2:** [http://www.netlib.org/linalg/spooles/spooles.2.2.tgz](http://www.netlib.org/linalg/spooles/spooles.2.2.tgz)
- **Parche de CalculiX:** [https://www.dhondt.de/ccx_2.23.SPOOLEScorrection.tar.bz2](https://www.dhondt.de/ccx_2.23.SPOOLEScorrection.tar.bz2)

## Proceso de Construcción (build_spooles_custom.sh)

1. **Descarga y Preparación:**
   - Descarga y extracción de SPOOLES 2.2 y el parche de CalculiX.
   - Aplicación de parches automáticos sobre los archivos del código fuente:
     - `Tree/src/makeGlobalLib`: Reemplazo de `drawTree.c` por `draw.c`.
     - `makefile`: Corrección de ruta en `misc/src`.

2. **Configuración (`Make.inc`):**
   - Adaptación de variables para el compilador `clang` y `gfortran` de Termux:
     - `CC`, `CFLAGS` (incluye `-Oz`, `-fPIC`, etc.), `LDFLAGS` y `OPTLEVEL`.

3. **Compilación:**
   - **Librería Base:** `make lib` en el directorio raíz.
   - **Librería Multi-Thread (MT):** `make` dentro de `MT/src`.

4. **Instalación:**
   - Los archivos de cabecera (`*.h`) se copian a `$FAKE_USR/include/spooles`.
   - Las librerías estáticas (`spooles.a`, `spoolesMT.a`) se instalan en `$FAKE_USR/lib`.
