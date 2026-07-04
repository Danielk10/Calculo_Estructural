# Reporte de Limpieza de Assets y Corrección de Rutas

Este reporte documenta los archivos y carpetas dentro de `app/src/main/assets/data/data/com.diamon.civil/files/usr` que no son necesarios para la ejecución de los binarios en la arquitectura `arm64-v8a` del proyecto Android y que, por lo tanto, pueden ser eliminados para reducir significativamente el peso del APK y mejorar el rendimiento de la aplicación. También detalla las correcciones de rutas requeridas en los scripts restantes.

---

## 1. Archivos y Carpetas Propuestos para Eliminación

Los siguientes recursos corresponden a archivos de desarrollo, cabeceras de compilación, configuraciones de compiladores y wrappers de lenguajes no soportados (Python, Julia) que no son utilizados en tiempo de ejecución por la aplicación.

### A. Cabeceras y Archivos de Código Fuente C/C++ (`include` y otros)
*   **Ruta:** `usr/include/` (Directorio completo)
    *   **Descripción:** Contiene 124 archivos de cabecera (`.h`, `.hxx`, `.h_cwrap`, `.f90`, `.hf`) de OpenCASCADE, HDF5, Gmsh, OpenBLAS y Tcl/Tk.
    *   **Razón:** Solo se necesitan durante el tiempo de compilación. Las aplicaciones Android ya compiladas no requieren cabeceras.
    *   **Ahorro aproximado:** ~4.1 MB
*   **Ruta:** `usr/lib/tcl8.6/tclAppInit.c` y `usr/lib/tk8.6/tkAppInit.c`
    *   **Descripción:** Archivos fuente de inicialización en C.
    *   **Razón:** No tienen ninguna función a nivel de ejecución ya que los binarios ya están compilados.
    *   **Ahorro aproximado:** ~10 KB

### B. Librerías Estáticas (`.a`)
*   **Ruta:** `usr/lib/` (Archivos `.a`)
    *   `libarpack.a` (~590 KB)
    *   `libhdf5.a` (~13 MB)
    *   `libhdf5_hl.a` (~248 KB)
    *   `libopenblas.a` (~42.7 MB)
    *   `libopenblasp-r0.3.33.dev.a` (~42.7 MB)
    *   `libspooles.a` (~2.16 MB)
    *   `libspoolesMT.a` (~52 KB)
    *   `libtclstub8.6.a` (~7 KB)
    *   `libtkstub8.6.a` (~5 KB)
    *   **Razón:** Las librerías estáticas se enlazan al compilar los binarios. En la ejecución del APK solo se emplean las librerías dinámicas (`.so`) cargadas por Android desde `jniLibs`.
    *   **Ahorro aproximado:** **~101.4 MB**

### C. Archivos de Configuración de Desarrollo y Compilación
*   **Ruta:** `usr/lib/cmake/` y `usr/share/cmake/` y `usr/cmake/` (Directorios completos)
    *   **Descripción:** Archivos de configuración de CMake para HDF5, OpenCASCADE y Medfile.
    *   **Razón:** CMake es una herramienta de compilación; Android no ejecuta CMake en tiempo de ejecución.
    *   **Ahorro aproximado:** ~350 KB
*   **Ruta:** `usr/lib/pkgconfig/` (Directorio completo)
    *   **Descripción:** Archivos `.pc` para la detección de librerías mediante `pkg-config` (arpack, hdf5, openblas, tcl, tk).
    *   **Razón:** Pkg-config no se ejecuta en Android a nivel de runtime.
    *   **Ahorro aproximado:** ~3 KB
*   **Ruta:** `usr/lib/libhdf5.settings`
    *   **Descripción:** Archivo de texto que describe la configuración con la que se compiló HDF5 (contiene rutas absolutas de compilación como `/data/data/com.termux`).
    *   **Razón:** Es meramente informativo para desarrolladores, no lo lee la librería a nivel de ejecución.
    *   **Ahorro aproximado:** ~4.4 KB
*   **Ruta:** `usr/lib/tclConfig.sh`, `usr/lib/tclooConfig.sh`, `usr/lib/tkConfig.sh`
    *   **Descripción:** Scripts de configuración de Tcl/Tk usados por compiladores externos para enlazar contra Tcl/Tk (contienen rutas de Termux heredadas).
    *   **Razón:** No son leídos por el intérprete a nivel de runtime.
    *   **Ahorro aproximado:** ~14 KB
*   **Ruta:** `usr/share/gmsh/` (Directorio completo)
    *   **Descripción:** Archivos `.cmake` relacionados con la exportación de Gmsh.
    *   **Razón:** Tampoco son necesarios a nivel de ejecución.
    *   **Ahorro aproximado:** ~8 KB

### D. Wrappers de Lenguajes no Soportados
*   **Ruta:** `usr/lib/gmsh-5.0.0.dev1.dist-info/` (Directorio) y `usr/lib/gmsh.py`, `usr/lib/gmsh.jl`
    *   **Descripción:** Módulos de Python y Julia para interactuar con la API de Gmsh.
    *   **Razón:** La aplicación no incluye un intérprete de Python o Julia; el uso de Gmsh se realiza mediante el ejecutable compilado y llamadas JNI nativas en C++.
    *   **Ahorro aproximado:** ~800 KB
*   **Ruta:** `usr/bin/onelab.py`
    *   **Descripción:** Script de interfaz de Python para la comunicación de Gmsh y CalculiX.
    *   **Razón:** No es ejecutable en Android debido a la falta de intérprete de Python.
    *   **Ahorro aproximado:** ~20 KB

### E. Wrappers de Compilación e Información General
*   **Ruta:** `usr/bin/h5cc`
    *   **Descripción:** Script wrapper del compilador C para HDF5 (contiene rutas absolutas `/data/data/com.termux`).
    *   **Razón:** No compilamos código dentro del dispositivo Android.
    *   **Ahorro aproximado:** ~9 KB
*   **Ruta:** `usr/share/doc/` y `usr/share/man/` y archivos `.md` sueltos en `usr/share/` (`CHANGELOG.md`, `LICENSE`, `USING_HDF5_CMake.md`)
    *   **Descripción:** Documentación del software, licencias e información de ayuda (man pages).
    *   **Razón:** Ninguno de los solvers utiliza esta documentación para realizar cálculos.
    *   **Ahorro aproximado:** ~250 KB

---

## 2. Archivos y Carpetas Necesarios (Deben Conservarse)

Los siguientes archivos son esenciales y se mantendrán intactos:
1.  `usr/lib/tcl8/` y `usr/lib/tcl8.6/` (con excepción de `.c` y `.sh` descritos arriba): Contienen los scripts `.tcl` de inicialización fundamentales para que el entorno de Tcl funcione.
2.  `usr/lib/tk8.6/` (con excepción de `.c` y `.sh` descritos arriba): Contiene recursos `.tcl` necesarios si se inicializan componentes Tk.
3.  `usr/share/opencascade/`: Contiene recursos, sombreadores (shaders) y mensajes requeridos por OpenCASCADE a nivel de runtime.
4.  Binarios de utilidades en `usr/bin/` (`xmdump2`, `xmdump3`, `xmdump4`): Aunque son binarios de utilidades, no los tocaremos para cumplir la directriz de no modificar binarios.

---

## 3. Modificación de Rutas en Scripts Restantes (Si se conservan)

Si decidimos conservar los scripts en `usr/bin/` (`drawenv`, `env.sh`, `custom.sh`, `custom_clang_64.sh` y `draw.sh`) para permitir pruebas por terminal/adb:

### A. Corrección de `usr/bin/drawenv`
Se propone reemplazar las referencias a `/data/data/com.termux/files/home/fake_root/data/data/com.diamon.civil/files/usr` por `/data/data/com.diamon.civil/files/usr` y limpiar rutas externas de Termux.
*   **Antes:**
    ```bash
    export DESTDIR="/data/data/com.termux/files/home/fake_root"
    export FAKE_USR="/data/data/com.termux/files/home/fake_root/data/data/com.diamon.civil/files/usr"
    export TMX_PREFIX="/data/data/com.termux/files/usr"
    export LD_LIBRARY_PATH="/data/data/com.termux/files/home/fake_root/data/data/com.diamon.civil/files/usr/lib:/data/data/com.termux/files/usr/lib:/data/data/com.termux/files/home/fake_root/data/data/com.diamon.civil/files/usr/lib:/data/data/com.termux/files/usr/lib:/data/data/com.termux/files/usr/lib:/data/data/com.termux/files/usr/lib:"
    export TCL_LIBRARY="/data/data/com.termux/files/home/fake_root/data/data/com.diamon.civil/files/usr/lib/tcl8.6"
    export TK_LIBRARY="/data/data/com.termux/files/home/fake_root/data/data/com.diamon.civil/files/usr/lib/tk8.6"
    export TCLLIBPATH="/data/data/com.termux/files/home/fake_root/data/data/com.diamon.civil/files/usr/lib /data/data/com.termux/files/home/fake_root/data/data/com.diamon.civil/files/usr/lib/tcl8.6 /data/data/com.termux/files/home/fake_root/data/data/com.diamon.civil/files/usr/lib/tk8.6"
    exec "/data/data/com.termux/files/home/fake_root/data/data/com.diamon.civil/files/usr/bin/DRAWEXE" "$@"
    ```
*   **Después:**
    ```bash
    export DESTDIR="/data/data/com.diamon.civil/files"
    export FAKE_USR="/data/data/com.diamon.civil/files/usr"
    export TMX_PREFIX="/data/data/com.diamon.civil/files/usr"
    export LD_LIBRARY_PATH="/data/data/com.diamon.civil/files/usr/lib"
    export TCL_LIBRARY="/data/data/com.diamon.civil/files/usr/lib/tcl8.6"
    export TK_LIBRARY="/data/data/com.diamon.civil/files/usr/lib/tk8.6"
    export TCLLIBPATH="/data/data/com.diamon.civil/files/usr/lib /data/data/com.diamon.civil/files/usr/lib/tcl8.6 /data/data/com.diamon.civil/files/usr/lib/tk8.6"
    exec "/data/data/com.diamon.civil/files/usr/bin/DRAWEXE" "$@"
    ```

### B. Corrección de `usr/bin/env.sh`
*   **Antes:**
    ```bash
    export THIRDPARTY_DIR="/data/data/com.termux/files/home/fake_root/data/data/com.diamon.civil/files/usr"
    ```
*   **Después:**
    ```bash
    export THIRDPARTY_DIR="/data/data/com.diamon.civil/files/usr"
    ```

---

## Resumen del Impacto de la Limpieza
*   **Peso Inicial Ahorrado:** **~106.8 MB** (reduciendo drásticamente el peso del APK y el espacio que ocupa al extraerse en la memoria interna del dispositivo).
*   **Portabilidad:** Eliminación completa de rutas `/data/data/com.termux` obsoletas e incorrectas.
*   **Seguridad:** Reducción de la superficie de ataque al no incluir herramientas de compilación (`h5cc`) ni headers innecesarios.
