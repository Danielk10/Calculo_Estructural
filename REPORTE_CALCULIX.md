# Reporte de Instalación Persistente de CalculiX ccx 2.23 (MT Spooles)

Este reporte detalla el procedimiento ejecutado para instalar **CalculiX ccx 2.23** de forma aislada y persistente en el entorno de Cloud Shell, incorporando paralelización mediante el solucionador **SPOOLES MT**.

## Resumen de la Instalación

1. **Eliminación de versiones de sistema**: Se removieron los paquetes pre-compilados por defecto (`calculix-ccx`, `calculix-cgx`) para evitar conflictos de versiones y librerías obsoletas.
2. **Preparación de Dependencias**:
   - Instalación de componentes base (`gcc`, `gfortran`, `make`, `liblapack-dev`, `libarpack2-dev`, `libspooles-dev`).
3. **SPOOLES MT (Multi-Threading)**:
   - Se descargó el código fuente de Spooles 2.2.
   - Se aplicó un parche al archivo `Make.inc` para forzar el uso del compilador `gcc` y banderas de optimización modernas (`-O2`).
   - Se compiló la librería secuencial (`spooles.a`) y posteriormente el núcleo paralelo en `MT/src` (`spoolesMT.a`).
4. **CalculiX ccx 2.23**:
   - Se descargó el código fuente más reciente (v2.23) del sitio oficial de Dhondt.
   - Se preparó el `Makefile` con la macro `-DUSE_MT=1` y la vinculación dinámica a los módulos estáticos de Spooles compilados en el paso previo.
   - **Parche de Preprocesador C (El supuesto error Intel)**: CalculiX normalmente compila en Linux sin problemas, pero su código fuente (archivos `.f`) contiene directivas de preprocesador de C (como `#if defined(__INTEL_COMPILER)`). El compilador `gfortran` de GNU no procesa macros de C en archivos `.f` de manera predeterminada, lo que generó un error de lectura de módulos. Para solucionarlo sin tocar el código fuente, se añadió la bandera `-cpp` al `FFLAGS` del Makefile, obligando a GNU Fortran a evaluar las macros correctamente.
   - Se añadieron las banderas de OpenMP (`-fopenmp`) requeridas para que las operaciones matriciales se distribuyan correctamente entre los hilos del procesador.
   - Se compiló satisfactoriamente.
5. **Persistencia (Cloud Shell)**:
   - El ejecutable estático resultante `ccx_2.23` se movió al directorio permanente `~/.local/bin/ccx`.
   - Se eliminaron todos los gigabytes de archivos de compilación temporales (`~/calculix_build`).

## Restauración Dinámica en Entorno Efímero

Debido a que Cloud Shell reinicia el sistema operativo (`/usr`, `/etc`) en cada sesión (lo que borra las librerías dinámicas como ARPACK o LAPACK a las que está vinculado `ccx`), es necesario cargar estas dependencias al iniciar una nueva sesión para que el binario de CalculiX (que está a salvo en `~/.local/bin`) funcione correctamente.

**Script de preparación**: Se creó el archivo `~/preparar_calculix.sh`.
Su función es instalar en 15 segundos las librerías matemáticas requeridas por el sistema operativo.

### Ejemplos Prácticos y Pruebas
Para probar que CalculiX resuelve la paralelización de manera correcta, se ha añadido un modelo base (cubo hexaédrico) y un script bash para medir tiempos y ejecución en `~`.

Puedes correr el analizador de rendimiento así:
```bash
bash ~/run_calculix_tests.sh
```

**Ejecutar Análisis Secuencial (1 Núcleo)**:
```bash
export OMP_NUM_THREADS=1
ccx -i ~/test_calculix
```

**Ejecutar Análisis en Paralelo (Múltiples Núcleos)**:
```bash
export OMP_NUM_THREADS=4
ccx -i ~/test_calculix
```

*Nota: CalculiX `ccx` no cuenta con interfaz gráfica de usuario. Utiliza archivos `.inp` basados en la sintaxis de Abaqus. Para visualizar los resultados (`.frd`), debes usar herramientas de post-procesamiento como ParaView (exportando a VTK) o CalculiX GraphiX (cgx) en un entorno gráfico.*
