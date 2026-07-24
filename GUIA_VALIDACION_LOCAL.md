# Guía de Validación y Pruebas Locales (CalculiX, Gmsh, OCCT, Parser y Java/C++)

Esta guía describe cómo ejecutar todas las pruebas locales necesarias en este entorno Linux para validar cualquier cambio en el motor de cálculo, enmallador o parser nativo antes de compilar y subir la APK final.

---

## 1. Pruebas del Parser Nativo C++ (`frd_converter.cpp`)

El parser nativo C++ es el encargado de convertir los archivos de resultados de CalculiX (`.frd`) al formato binario comprimido `.glb` para su correcta renderización plana y brillante (sin luces) en el visor 3D de SceneView.

### Compilación Standalone del Parser local
Para probar cambios rápidos en el parser de C++ sin compilar toda la app Android:
1. Compila el binario ejecutable local con `g++`:
   ```bash
   g++ -O3 -std=c++17 -DSTANDALONE_TEST \
       -I/home/danielpdiamon/Calculo_Estructural/app/src/main/cpp/ \
       /home/danielpdiamon/Calculo_Estructural/app/src/main/cpp/frd_converter.cpp \
       -o /tmp/test_parser
   ```
2. Ejecuta el parser sobre un archivo de resultados `.frd` real:
   ```bash
   /tmp/test_parser /home/danielpdiamon/Calculo_Estructural/test_calculix.frd
   ```

### Validación
* Debe imprimir `SUCCESSFULLY PARSED FRD!`.
* Debe listar un conteo de nodos y elementos mayor a cero.
* No debe haber fallos de segmentación (`Segmentation fault`) ni errores de desbordamiento de memoria al leer líneas que inician con `-2` (conectividad multilínea).

---

## 2. Pruebas Locales del Solver CalculiX (`ccx`)

CalculiX está preinstalado en la ruta local del sistema. Resuelve la ecuación matricial de elasticidad lineal $K \cdot U = F$.

### Comando de Ejecución
Corre el cálculo localmente utilizando el archivo de entrada `.inp`:
```bash
ccx -i test_calculix
```

### Validación
* Al finalizar el proceso, la consola debe imprimir: `Job finished`.
* Debe generar tres archivos resultantes en el directorio de trabajo con la misma marca de tiempo:
  * `test_calculix.dat` (Resultados de texto estructurado).
  * `test_calculix.sta` (Estado de convergencia del solver).
  * `test_calculix.frd` (Base de datos binaria de desplazamientos y esfuerzos).

---

## 3. Pruebas Locales del Enmallador Gmsh (`gmsh`)

Gmsh se encarga de discretizar los modelos CAD (.geo, .step, .brep, .iges) en mallas volumétricas de elementos tetraédricos.

### Comando de Enmallado 3D
Genera una malla 3D en formato CalculiX (.inp) a partir de un archivo de geometría:
```bash
gmsh cantilever.geo -3 -format inp -o cantilever_raw.inp
```

### Parámetros Adicionales útiles
* `-clmax <float>`: Define el tamaño máximo de los elementos de malla (menor valor = malla más fina).
* `-v 0`: Modo silencioso (suprime la salida extensa).

---

## 4. Pruebas de Modelado CAD con Open CASCADE (`DRAWEXE` / `occt-draw`)

`DRAWEXE` es la consola interactiva de comandos CAD de Open CASCADE. Permite crear sólidos paramétricos y realizar operaciones booleanas.

### Ejecución Headless en Servidor (Virtual Framebuffer)
Dado que `occt-draw` intenta cargar la interfaz gráfica Tk, en entornos headless (como este terminal Linux) debes ejecutarlo bajo el servidor virtual `xvfb-run` e inyectar las instrucciones por la entrada estándar (`stdin`):

```bash
echo "pload ALL; box b 3 3 15; writebrep b /tmp/bar.brep; exit" | xvfb-run -a DRAWEXE
```

### Comandos Tcl/Tk Útiles en DRAWEXE
* `pload ALL`: Carga todas las librerías estándar de modelado y visualización.
* `box <nombre> <dx> <dy> <dz>`: Crea una caja de dimensiones especificadas.
* `cylinder <nombre> <x> <y> <z> <dx> <dy> <dz> <r>`: Crea un cilindro.
* `bop <sólidoA> <sólidoB>`: Prepara la operación booleana.
* `bopfuse <resultado>`: Unión booleana.
* `bopcut <resultado>`: Corte booleano.
* `bopintersect <resultado>`: Intersección booleana.
* `writebrep <sólido> <ruta_archivo.brep>`: Exporta el sólido a formato BREP.
* `stepwrite a <sólido> <ruta_archivo.step>`: Exporta el sólido a formato STEP.

---

## 5. Pruebas Unitarias y de Integración con Gradle (`./gradlew`)

Automatizan todo el flujo anterior de manera integrada. Se localizan en `app/src/test/java/com/diamon/civil/engine/InpAssemblerTest.java`.

### Ejecutar todas las pruebas unitarias
Corre la suite completa de pruebas:
```bash
./gradlew testDebugUnitTest
```

### Pruebas Críticas Incluidas
1. **`testCoordinateBasedBoundaryFallback`**: Valida que si la malla no cuenta con superficies físicas predefinidas, el ensamblador parsea las coordenadas 3D, identifica el eje de mayor longitud y asigna empotramientos en la base y cargas en la punta automáticamente.
2. **`testRealStepFileMeshingAndSolving`**: Copia un archivo STEP de los assets (`linkrods.step`), escribe el script `.geo` para Gmsh, genera la malla localmente, la ensambla aplicando el fallback de condiciones de contorno, la calcula con CalculiX y parsea los desplazamientos.
3. **`testDownloadedBracketStepFile`**: Copia el modelo mecánico real de un bracket descargado de internet a los assets (`bracket_simple.step`), realiza el enmallado con Gmsh, ensambla y resuelve la deformación de la pieza mecánica con CalculiX de forma robusta.
4. **`testCADModelingMeshingAndSolvingPipeline`**: Valida toda la cadena elástica de modelado en Open CASCADE, enmallado en Gmsh, ensamblaje CalculiX y parseo local desde cero, ejecutando DRAWEXE bajo un servidor gráfico virtual headless (`xvfb-run`).

Cualquier cambio en el código Java del ensamblador o del parser que rompa estas simulaciones causará que la tarea de Gradle falle, sirviendo como el primer filtro de control de calidad.

---

## 6. Proceso de Compilación e Instalación
Una vez que todas las pruebas locales pasan con éxito:
1. Compila el APK final de depuración:
   ```bash
   ./gradlew assembleDebug
   ```
2. La APK finalizada se guardará en:
   `/tmp/calculoestructural_build/outputs/apk/debug/app-debug.apk`
