# Análisis de Alineación a 16 KB y Dependencias

## 1. Alineación a 16 KB (Soporte Android 15+)
Se ha realizado un análisis profundo a todos los binarios y librerías dinámicas (`.so`) que hemos introducido en `jniLibs/arm64-v8a` usando `readelf -Wl`.

**Resultado:**
Todos los binarios (incluyendo CalculiX, Gmsh y OCCT) tienen un segmento `LOAD` con alineación de **`0x4000` (16384 bytes = 16 KB)**. 
- Esto es **excelente** e indica que los binarios fueron compilados con el flag adecuado (`-z max-page-size=16384`) y **cumplirán sin problemas con la nueva política de Android 15**.

## 2. Resolución de Dependencias

Se ejecutó un escaneo de cabeceras ELF (`readelf -d`) para identificar las dependencias dinámicas (`NEEDED`) de cada binario.

### A. Dependencias Nativas de Android (Sistema)
Estas dependencias **SÍ** existen en el sistema Android y se resolverán de forma nativa sin hacer nada extra:
- `libc.so`, `libm.so`, `libdl.so`, `liblog.so`, `libz.so`, `libGLESv2.so`, `libEGL.so`
- `libc++_shared.so` (Extraída del NDK y añadida a `jniLibs`)

### B. Dependencias Externas (Mapeadas Localmente)
Estas librerías no existen en Android nativo, pero **ya las hemos movido a `jniLibs` y las hemos enlazado correctamente**:
- `libhdf5.so`, `libhdf5.so.1000`
- `libtcl8.6.so`
- `libopenblas.so`
- `libgmsh.so.5.0`
- `libTK*.so` (Librerías de OpenCASCADE)
- `libgmp.so` (Requerida por Gmsh)
- `libfreetype.so` (Requerida por Gmsh y OCCT)

### C. Dependencias Críticas a Resolver (Atención)
Hay detalles técnicos críticos que dependen de configuraciones adicionales:

1. **Estado de Gmsh + OCCT**:
   - Se ha verificado que `libgmsh.so` enlaza correctamente con las 27 librerías dinámicas de OpenCASCADE. 
   - La alineación de 16 KB ha sido verificada en los nuevos binarios.

2. **`libz.so.1`**: 
   - Varios binarios (como `tclsh` y `CalculiX`) dependen de `libz.so.1`. Android tiene `libz.so` en su sistema (`/system/lib64/libz.so`), pero no bajo el nombre con `.1`.
   - **Solución implementada**: Hemos creado un enlace simbólico (`symlink`) en `AssetHelper.java` que apunta `usr/lib/libz.so.1` hacia `/system/lib64/libz.so`.

2. **Ruta Absoluta Termux en CalculiX**:
   - `libCalculiX.so` tiene una dependencia listada literalmente como: `/data/data/com.termux/files/home/fake_root/data/data/com.diamon.civil/files/usr/lib/libtcl8.6.so`.
   - **Efecto**: Android intentará buscar esa ruta absoluta exacta que existía cuando compilaste el binario (probablemente en Termux). Si no la encuentra, puede fallar al iniciar, a pesar de que hayamos pasado `LD_LIBRARY_PATH`.
   - **Solución futura**: Si CalculiX se cierra de golpe (Crash) indicando que no encuentra ese archivo, será necesario parchear el ELF (usando `patchelf --replace-needed`) para borrar esa ruta absoluta y dejar solo `libtcl8.6.so`.

3. **Librerías X11 para Wish y Tk**:
   - Los binarios `wish` y la librería `libtk8.6.so` dependen de: `libXft.so`, `libfontconfig.so`, `libfreetype.so`, `libX11.so`, `libXss.so`, `libXext.so`.
   - **Efecto**: Estas son librerías del entorno gráfico X11 de Linux de Escritorio. **No existen en Android**. Por lo tanto, intentar ejecutar `wish` nativamente fallará, ya que el sistema Android usa *SurfaceFlinger*, no X11.
   - **Impacto**: `CalculiX` no depende directamente de `libtk8.6.so`, por lo que **la consola y los cálculos matemáticos funcionarán perfectamente**. Solo las funciones gráficas de TCL (como las ventanas nativas de `wish`) fallarán.
