# Resumen de Aplicación de Parche a SPOOLES 2.2

## Descripción
Se ha procedido a la descarga, instalación y aplicación del parche de corrección de SPOOLES 2.2 proporcionado por CalculiX para garantizar la compatibilidad y optimización en entornos de 64 bits (solución del sistema I2Ohash).

## Fuentes
- **SPOOLES 2.2:** [http://www.netlib.org/linalg/spooles/spooles.2.2.tgz](http://www.netlib.org/linalg/spooles/spooles.2.2.tgz)
- **Parche de CalculiX:** [https://www.dhondt.de/ccx_2.23.SPOOLEScorrection.tar.bz2](https://www.dhondt.de/ccx_2.23.SPOOLEScorrection.tar.bz2)

## Pasos Realizados

1. **Descarga y Extracción:**
   - SPOOLES 2.2 fue descargado y extraído en: `$HOME/SPOOLES.2.2`.
   - El parche de CalculiX (`ccx_2.23.SPOOLEScorrection.tar.bz2`) fue descargado y extraído dentro de la carpeta raíz de SPOOLES (`$HOME/SPOOLES.2.2/CalculiX`).

2. **Aplicación del Parche:**
   - Se ha reemplazado el archivo `util.c` de `I2Ohash` para solucionar problemas de manejo de memoria en sistemas de 64 bits.
   - **Ruta del archivo original:** `$HOME/SPOOLES.2.2/I2Ohash/src/util.c`
   - **Ruta del archivo del parche:** `$HOME/SPOOLES.2.2/CalculiX/ccx_2.23/SPOOLES.2.2/I2Ohash/src/util.c`
   - **Comando ejecutado:** `cp $HOME/SPOOLES.2.2/CalculiX/ccx_2.23/SPOOLES.2.2/I2Ohash/src/util.c $HOME/SPOOLES.2.2/I2Ohash/src/util.c`

3. Verificación y Corrección de Makefiles:
   - Se ha verificado la estructura de directorios siguiendo las recomendaciones del README de CalculiX.
   - **Corrección requerida:** El archivo `~/SPOOLES.2.2/Tree/src/makeGlobalLib` contiene un error donde se referencia `drawTree.c`, el cual no existe en esa ruta (existe como driver en `Tree/drivers/drawTree.c`). Según el README, esta referencia debe ser reemplazada por `draw.c`.
   - Se ha confirmado que la estructura permite la compilación al ajustar estas referencias.
