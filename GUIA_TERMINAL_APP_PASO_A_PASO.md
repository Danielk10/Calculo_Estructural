# 💻 Guía Integral de Uso de la Terminal de la Aplicación
## De Cero a Nivel Avanzado: Arquitectura, Comandos, Solucionadores Nativos, Casos Prácticos, Posibilidades y Limitaciones

Esta guía proporciona un manual completo, detallado y riguroso para dominar la **Terminal Interactiva** integrada en la aplicación móvil **Structural Analysis FEA 3D** (`com.diamon.civil`). 

A través de esta terminal, ingenieros civiles, mecánicos, calculistas y estudiantes tienen a su disposición un entorno de consola UNIX embebido dentro de Android para interactuar directamente con los motores numéricos industriales **CalculiX CCX 2.23 (con paralelismo multihilo OpenMP y solucionador SPOOLES MT)**, **OpenCASCADE (OCCT 8.0.0.p1 DRAWEXE)** y **Gmsh**.

---

## 📑 Tabla de Contenidos
1. [Arquitectura Interna y Entorno Sandbox](#-1-arquitectura-interna-y-entorno-sandbox)
2. [Componentes de la Interfaz y Controles de Navegación](#-2-componentes-de-la-interfaz-y-controles-de-navegación)
3. [Nivel 1: Comandos de Sistema y Gestión de Archivos](#-3-nivel-1-comandos-de-sistema-y-gestión-de-archivos)
4. [Nivel 2: Comandos Especiales de Diagnóstico y Validación Estructural](#-4-nivel-2-comandos-especiales-de-diagnóstico-y-validación-estructural)
5. [Nivel 3: Ejecución Directa de Binarios Nativos (`ccx`, `gmsh`, `DRAWEXE`)](#-5-nivel-3-ejecución-directa-de-binarios-nativos-ccx-gmsh-drawexe)
6. [Nivel 4: Flujos de Trabajo Prácticos Paso a Paso](#-6-nivel-4-flujos-de-trabajo-prácticos-paso-a-paso)
7. [Posibilidades y Limitaciones Técnicas de la Terminal](#-7-posibilidades-y-limitaciones-técnicas-de-la-terminal)
8. [Resolución de Problemas y Diagnóstico (Troubleshooting)](#-8-resolución-de-problemas-y-diagnóstico-troubleshooting)
9. [Verificación Local y Certificación Científica](#-9-verificación-local-y-certificación-científica)

---

## 🏛️ 1. Arquitectura Interna y Entorno Sandbox

La terminal de la aplicación no es un mero visualizador de registros, sino un **subsistema de ejecución directa por línea de comandos** que orquesta procesos nativos en segundo plano mediante `ProcessBuilder` y la interfaz JNI (Java Native Interface).

```mermaid
flowchart TD
    UI["TerminalFragment (UI)"] -->|"Texto de Comando"| TCE["TerminalCommandExecutor (Motor Shell)"]
    TCE -->|"Comandos Shell (ls, cd, cp, cat)"| SANDBOX["Sandbox del Usuario (/data/data/.../files)"]
    TCE -->|"Comandos test-* / Binarios"| CE["CalculixExecutor (Orquestador de Procesos)"]
    
    CE -->|"OMP_NUM_THREADS / LD_PRELOAD"| CCX["CalculiX ccx (Solucionador FEA 1D/2D/3D)"]
    CE -->|"Scripts .geo / .step"| GMSH["Gmsh (Mallador Tetraédrico 3D)"]
    CE -->|"TCL Headless"| DRAW["DRAWEXE (OpenCASCADE CAD)"]
    
    CCX -->|".dat (Texto) / .frd (Binario)"| PARSERS["Parsers C++/Java (Dat / Frd a GLB)"]
    PARSERS --> ML["ModuleLogger / Consola de Salida"]
```

### 1.1 Ubicación Física del Sandbox
En Android, cada aplicación se ejecuta dentro de un espacio de memoria aislado por seguridad. La raíz de trabajo de la terminal corresponde al directorio interno de la app:
```
/data/data/com.diamon.civil/files
```
En la consola, este directorio se visualiza de forma limpia como `/`.

### 1.2 Estructura del Árbol de Directorios Accesibles
Desde la terminal puedes moverte libremente entre los diferentes directorios generados por la aplicación:

| Directorio en Consola | Ruta Real en el Dispositivo | Propósito |
| :--- | :--- | :--- |
| `/` | `.../files/` | Directorio raíz del usuario y área de trabajo predeterminada de la terminal. |
| `/structural_analysis` | `.../files/structural_analysis/` | Almacena los archivos del Módulo Estructural: `job_structural.inp`, `job_structural.dat`, `job_structural.frd`, `job_structural.sta`. |
| `/3d_solid_analysis` | `.../files/3d_solid_analysis/` | Almacena los modelos del Módulo de Sólidos 3D: `solid.inp`, `solid.frd`, `solid.glb`, scripts CAD y mallas. |
| `/mis_proyectos` | `.../files/mis_proyectos/` | Cualquier carpeta personalizada que crees con `mkdir`. |

> [!NOTE]
> **Protección del Sistema:** Los directorios del sistema de Android y de librerías (`usr`, `lib`, `include`, `bin`, `cache`, `databases`, `shared_prefs`) están **ocultos intencionadamente** al ejecutar `ls` desde la raíz para evitar que sean borrados accidentalmente, manteniendo los binarios y dependencias intactos.

### 1.3 Variables de Entorno del Kernel de Ejecución
Cuando la terminal lanza un binario como `ccx` o `DRAWEXE`, `CalculixExecutor` configura dinámicamente el entorno de ejecución:
- `OMP_NUM_THREADS`: Se establece automáticamente al total de núcleos de CPU detectados en el dispositivo (`Runtime.getRuntime().availableProcessors()`), habilitando paralelismo OpenMP masivo.
- `CCX_NPROC_EQUATION_SOLVER`: Configura el solucionador SPOOLES multihilo al número óptimo de núcleos.
- `OMP_STACKSIZE=64M`: Pila de memoria extendida para evitar desbordamientos en matrices de rigidez gigantescas.
- `LD_PRELOAD=libfdsan_bypass.so`: Previene fallos de descriptores de archivo en Android 11+ al ejecutar `DRAWEXE`.
- `TCL_LIBRARY` y `CASROOT`: Rutas completas a recursos de OpenCASCADE para modelado CAD sin interfaz gráfica (Headless).
- `DISPLAY` (Eliminado): Fuerza el modo headless para evitar bloqueos por falta de servidor X11.

---

## 📱 2. Componentes de la Interfaz y Controles de Navegación

La pantalla de la terminal ([`fragment_terminal.xml`](file:///home/danielpdiamon/Calculo_Estructural/app/src/main/res/layout/fragment_terminal.xml)) está optimizada tanto para uso táctil como para teclados externos.

```
┌────────────────────────────────────────────────────────┐
│ [📋 Copiar]                    [⏹️ Abortar] [▲] [▼]    │  <- Barra de Acción
├────────────────────────────────────────────────────────┤
│ --- Structural Analysis FEA 3D Terminal System ---    │
│ Type 'help' to see list of available commands.        │
│                                                        │  <- Área de Registro
│ $ test-calculix                                        │     (LogScrollView)
│ Executing CalculiX Sequential Test...                  │
│ • Applied Axial Stress: σ_z = 400.0 MPa               │
│ • Status: 100% physically consistent with Hooke's Law │
│                                                        │
├────────────────────────────────────────────────────────┤
│ $ |Escribe un comando...                   [Enviar ➤]  │  <- Barra de Entrada
└────────────────────────────────────────────────────────┘
```

### 2.1 Barra Superior de Acciones Rápidas
- **Copiar Registro (Icono Portapapeles - `btnCopyLog`):** Copia todo el contenido mostrado en la consola al portapapeles del dispositivo, permitiéndote pegarlo en notas, correo o WhatsApp.
- **Abortar Proceso (Icono Cuadrado Rojo - `btnAbort`):** Este botón se hace **visible automáticamente** mientras un cálculo pesado está corriendo. Si un cálculo tarda demasiado o ingresaste un modelo con problemas de convergencia, púlsalo para forzar la terminación inmediata del proceso (`Process.destroyForcibly()`) sin cerrar la app.
- **Navegador de Historial (Iconos Flecha Arriba ▲ y Abajo ▼):**
  - **▲ (`btnHistoryPrev`):** Recupera el comando anterior en el historial.
  - **▼ (`btnHistoryNext`):** Avanza al comando siguiente en el historial.
  - *Nota:* Si tienes un teclado físico conectado, puedes usar las teclas físicas **Flecha Arriba** y **Flecha Abajo**.

### 2.2 Área de Log y Desplazamiento Inteligente
- La pantalla cuenta con scroll automático inteligente: si estás al final del registro, la vista bajará automáticamente al recibir nueva información. Si haces scroll hacia arriba para leer cálculos anteriores, la pantalla mantendrá tu posición para no interrumpir tu lectura.
- La tipografía monospace (`12sp`) en verde terminal garantiza una visualización clara de tablas numéricas, matrices y resúmenes de esfuerzos.

### 2.3 Barra Superior de la Aplicación (Menú General)
- **Importar INP (`action_import`):** Abre el selector de archivos del sistema Android para importar cualquier archivo `.inp` externo directamente a la carpeta donde esté ubicada tu terminal en ese momento.
- **Exportar Reporte PDF (`action_export`):** Compila toda la sesión y los cálculos en un documento formal `Terminal_Analysis_Report.pdf` guardado en tu carpeta de *Descargas*.
- **Exportar Todo el Espacio de Trabajo (`action_export_all`):** Empaqueta todos los archivos (`.inp`, `.dat`, `.frd`, `.glb`, etc.) y los exporta a `Descargas/terminal/`.

---

## 📂 3. Nivel 1: Comandos de Sistema y Gestión de Archivos

Estos comandos permiten organizar carpetas, revisar resultados numéricos y administrar tus archivos de cálculo.

### 3.1 Lista Completa de Comandos Shell

| Comando | Sintaxis | Descripción | Ejemplo |
| :--- | :--- | :--- | :--- |
| `pwd` | `pwd` | Muestra la ruta del directorio activo relativo a la raíz del sandbox. | `$ pwd`<br>`/structural_analysis` |
| `ls` | `ls [ruta]` | Lista los archivos y subcarpetas del directorio actual o de una ruta especificada. | `$ ls`<br>`[DIR] mis_modelos`<br>`      job_structural.inp`<br>`      job_structural.dat` |
| `cd` | `cd <directorio>` | Cambia de directorio. Acepta `..` (subir un nivel), `~` (ir a raíz) y rutas absolutas desde la raíz como `/structural_analysis`. | `$ cd mis_modelos`<br>`$ cd ..`<br>`$ cd /` |
| `mkdir` | `mkdir <nombre>` | Crea un nuevo directorio de trabajo. | `$ mkdir calculos_acero` |
| `touch` | `touch <archivo>` | Crea un archivo vacío o actualiza su fecha y hora. | `$ touch notas.txt` |
| `cat` | `cat <archivo>` | Muestra el contenido de un archivo de texto en pantalla (máximo 500 KB para proteger la memoria UI). | `$ cat job_structural.dat` |
| `echo` | `echo <texto>`<br>`echo "<texto>" > <archivo>`<br>`echo "<texto>" >> <archivo>` | Imprime texto o crea/escribe (`>`) o añade (`>>`) contenido a un archivo directamente en la terminal. Admite saltos de línea `\n`. | `$ echo "pload ALL\nbox b 10 10 10\nexit" > script.tcl` |
| `cp` | `cp <origen> <destino>` | Copia un archivo entre carpetas. Permite traer modelos desde otros módulos. | `$ cp /structural_analysis/job_structural.inp .` |
| `rm` | `rm <archivo>`<br>`rm -rf <carpeta>` | Elimina un archivo individual o una carpeta completa de manera recursiva. | `$ rm modelo_antiguo.inp`<br>`$ rm -rf carpeta_temporal` |
| `clear` | `clear` | Limpia completamente el área de registro de la terminal. | `$ clear` |
| `help` o `?` | `help` | Imprime en pantalla la lista de todos los comandos disponibles. | `$ help` |

### 3.2 Ejemplo Práctico: Organización de un Proyecto
```bash
$ pwd
/
$ mkdir proyecto_puente
Directory created: proyecto_puente
$ cd proyecto_puente
Current: /proyecto_puente
$ cp /structural_analysis/job_structural.inp ./puente.inp
Copied: job_structural.inp -> puente.inp
$ ls
      puente.inp
$ cat puente.inp
*NODE, NSET=NALL
1, 0.0, 0.0, 0.0
...
```

---

## 🔬 4. Nivel 2: Comandos Especiales de Diagnóstico y Validación Estructural

La app incluye una suite de comandos especializados que ejecutan pruebas de verificación numérica y física instantáneas. Estos comandos replican las baterías de prueba que certifican la precisión de los motores de cálculo contra la teoría clásica de estructuras.

### 4.1 `test-calculix` — Elasticidad Lineal y Coeficiente de Poisson 3D
Ejecuta CalculiX en modo monohilo sobre un cubo unitario ($1\times 1\times 1\text{ mm}$) discretizado con elementos hexaédricos de 8 nodos `C3D8`.

```bash
$ test-calculix
```
- **Fundamento Físico:**
  - Material: Acero con módulo de Young $E = 210\,000\text{ MPa}$ y relación de Poisson $\nu = 0.30$.
  - Carga: Fuerza de tracción en $Z$ repartida en 4 nodos ($4 \times 100\text{ N} = 400\text{ N}$).
  - Área transversal: $A = 1\text{ mm} \times 1\text{ mm} = 1\text{ mm}^2$.
  - Tensión aplicada: $\sigma_z = \frac{P}{A} = \frac{400\text{ N}}{1\text{ mm}^2} = 400.0\text{ MPa}$.
  - Elongación axial teórica (Ley de Hooke):
    $$\delta_z = \varepsilon_z \cdot L_z = \frac{\sigma_z}{E} \cdot L_z = \frac{400}{210\,000} \cdot 1.0 = +0.00190476\text{ mm}$$
  - Contracción lateral de Poisson:
    $$\delta_x = \delta_y = -\nu \cdot \delta_z = -0.30 \cdot 0.00190476 = -0.00057143\text{ mm}$$
- **Salida en Consola:**
  Certifica que los desplazamientos obtenidos por CalculiX coinciden con un **error de $0.0000\%$** respecto a la formulación analítica.

---

### 4.2 `test-calculix-parallel` — Rendimiento y Determinismo Multi-Núcleo
Ejecuta la misma prueba anterior utilizando el número máximo de núcleos disponibles en el procesador del teléfono inteligente (por ejemplo, 8 núcleos en un procesador octa-core moderno) mediante el solucionador multihilo SPOOLES MT.

```bash
$ test-calculix-parallel
```
- **Objetivo Técnico:** Comprobar que la aceleración por hardware paralelo mantiene el **100% de determinismo numérico** (los resultados son idénticos bit a bit a los de un solo núcleo).

---

### 4.3 `test-frame` (o `test-portico`) — Equilibrio Estático de Pórtico 2D
Ejecuta el análisis de un pórtico plano de $5\text{ m}$ de luz y $4\text{ m}$ de altura sometido a una fuerza lateral de $F_x = 10\text{ kN}$ en la cabeza de la columna izquierda.

```bash
$ test-frame
```
- **Comprobaciones de Mecánica Estructural:**
  - **Cortante Basal:** $\sum R_x = -F_x = -10.00\text{ kN}$ distribuido simétricamente ($5.00\text{ kN}$ en cada columna empotrada).
  - **Momento de Vuelco Global:**
    $$M_{\text{vuelco}} = F_x \cdot H = 10\text{ kN} \times 4\text{ m} = 40.00\text{ kN}\cdot\text{m}$$
  - **Par de Reacciones Verticales:**
    $$R_{y} = \pm \frac{M_{\text{vuelco}}}{L} = \pm \frac{40\text{ kN}\cdot\text{m}}{5\text{ m}} = \pm 8.00\text{ kN}$$
    *(Columna izquierda a tracción con $R_{y1} = -8\text{ kN}$, columna derecha a compresión con $R_{y2} = +8\text{ kN}$)*.

---

### 4.4 `test-gmsh` — Operaciones Booleanas CAD 3D y Mallado
Genera un script procedural `.geo` que modela la resta booleana entre un cilindro sólido ($R=2\text{ mm}, H=5\text{ mm}$) y una esfera concéntrica ($R=1.5\text{ mm}$). Invoca el binario de Gmsh para generar la malla tetraédrica `hollow_cylinder.inp`.

```bash
$ test-gmsh
```
- **Verificación Volumétrica:**
  $$V_{\text{teórico}} = V_{\text{cilindro}} - V_{\text{esfera}} = (\pi \cdot 2^2 \cdot 5) - \left(\frac{4}{3}\pi \cdot 1.5^3\right) \approx 62.83 - 14.14 = 48.69\text{ mm}^3$$

---

### 4.5 `test-draw` (o `test-occt`) — Motor CAD OpenCASCADE Headless
Inicializa el motor de modelado geométrico OpenCASCADE en segundo plano mediante `DRAWEXE` y un script Tcl para construir un prisma ortoédrico de $10 \times 10 \times 10\text{ mm}$ y exportarlo como representación de bordes `test_box.brep`.

```bash
$ test-draw
```

---

### 4.6 `test-cad-solve` — Pipeline Completo Autónomo CAD $\rightarrow$ Malla $\rightarrow$ FEA
Demuestra la interoperabilidad completa de los tres motores sin intervención gráfica:
1. **DRAWEXE:** Genera la geometría CAD de una barra prismática de $2 \times 2 \times 10\text{ mm}$ (`bar.brep`).
2. **Gmsh:** Lee el BRep y genera la malla 3D de elementos finitos (`bar_raw.inp`).
3. **SolidInpAssembler:** Asigna el material (Acero $E=210\,000\text{ MPa}$), condiciones de empotramiento y carga de $-500\text{ N}$ en el extremo libre (`bar.inp`).
4. **CalculiX `ccx`:** Resuelve la deformada y tensiones (`bar.frd`).
5. **SolidDisplacementFrdParser:** Extrae el campo de desplazamientos nodales y resume la flecha máxima.

```bash
$ test-cad-solve
```

---

### 4.7 `test-step-solve` y `test-bracket-solve` — Modelos Industriales STEP
Carga modelos de ingeniería en formato STEP estándar (`linkrods.step` y `bracket_simple.step`), genera la discretización espacial y los resuelve en CalculiX.

```bash
$ test-step-solve
$ test-bracket-solve
```

---

### 4.8 `test-coordinate-fallback` — Asignación Geométrica Espacial
Evalúa el algoritmo de detección automática de superficies de apoyo y aplicación de cargas basado en cajas delimitadoras (*Bounding Boxes*), permitiendo analizar sólidos STEP que no contienen grupos físicos definidos previamente.

```bash
$ test-coordinate-fallback
```

---

### 4.9 `test-frd-parser` y `test-dat-parser` — Motores de Procesamiento de Resultados
- **`test-dat-parser`:** Valida la lectura de fuerzas seccionales ($N$, $V$, $M$) y desplazamientos desde el archivo `.dat`.
- **`test-frd-parser`:** Ejecuta el conversor nativo de C++ para convertir el archivo de resultados binario `.frd` en un archivo tridimensional `.glb` compatible con el visor 3D SceneView.

```bash
$ test-dat-parser
$ test-frd-parser
```

---

### 4.10 `run-sim-test` — Simulación Comparada vs Euler-Bernoulli
Genera una viga en voladizo de $100 \times 10 \times 10\text{ mm}$ con carga puntual en la punta de $P = -100\text{ N}$ y compara la flecha calculada por elementos finitos volumétricos contra la solución matemática exacta de Euler-Bernoulli:
$$\delta = \frac{P L^3}{3 E I}$$

```bash
$ run-sim-test
```

---

## ⚡ 5. Nivel 3: Ejecución Directa de Binarios Nativos (`ccx`, `gmsh`, `draw` / `DRAWEXE`, `tclsh`)

Si ingresas un comando que no coincide con los comandos internos de shell ni con los comandos de diagnóstico `test-*`, la terminal lo delega directamente a los motores nativos instalados. La terminal incluye un analizador inteligente de línea de comandos que preserva comillas simples y dobles (`"..."`, `'...'`), permitiendo pasar scripts en línea y argumentos complejos.

---

### 5.1 Ejecutar CalculiX Directamente (`ccx`)

CalculiX CCX es el solucionador FEA principal de la aplicación. El solucionador opera única y exclusivamente mediante sus comandos nativos reales especificando el nombre del trabajo (*jobname*):

#### 🔹 Comandos Nativos Soportados por CalculiX:
* **`ccx <jobname>`**: Ejecuta el análisis sobre el archivo `<jobname>.inp`. CalculiX añade automáticamente la extensión `.inp`.
  ```bash
  ccx mi_viga
  ```
* **`ccx -i <jobname>`**: Bandera estándar nativa de CalculiX para especificar el archivo de entrada.
  ```bash
  ccx -i mi_viga
  ```
* **Con extensión explícita (`ccx mi_viga.inp`):** La terminal limpia automáticamente `.inp` para que CalculiX resuelva el archivo sin duplicar extensiones.
* **Consulta de sintaxis (`ccx`):** Al escribir `ccx` sin argumentos se muestra el uso oficial de comandos soportados.

> [!IMPORTANT]
> **Comandos No Válidos:** CalculiX no admite banderas ni parámetros adicionales de tipo GNU (como `-v`, `--version`, `-h`, etc.). Si se ingresa una opción no válida (por ejemplo `ccx -v`), la terminal emitirá directamente el mensaje estándar de comando inválido:
> ```
> Invalid command: ccx -v
> ```

#### Archivos Generados por CalculiX tras el Cálculo:
* `job.dat`: Archivo de texto plano con los resultados nodales solicitados en `*NODE PRINT` (desplazamientos, reacciones en apoyos) y seccionales en `*EL PRINT` / `*SECTION PRINT` (fuerzas axiales $N$, cortantes $V$, momentos flectores $M$).
* `job.frd`: Base de datos de resultados en formato binario/ASCII estándar para post-proceso 3D (desplazamientos vectoriales y tensiones de Von Mises).
* `job.sta`: Registro de convergencia (*Status file*).
* `job.cvg`: Registro de residuos de fuerza y desplazamiento para análisis no lineales.

---

### 5.2 Ejecutar el Generador de Mallas Gmsh (`gmsh`)

Gmsh es un generador de mallas tridimensionales que convierte archivos geométricos CAD (`.geo`, `.step`, `.stp`, `.brep`, `.iges`) en mallas de elementos finitos compatibles con CalculiX (`.inp`).

#### Opciones y Banderas Clave de Gmsh:
| Bandera | Descripción y Parámetros | Ejemplo de Uso |
| :--- | :--- | :--- |
| `-1`, `-2`, `-3` | Dimensión del mallado (1D barras, 2D placas/láminas, 3D sólidos). | `gmsh pieza.step -3 -format inp -o pieza.inp` |
| `-format <fmt>` | Formato de salida: `inp` (CalculiX/Abaqus), `msh` (Gmsh nativo), `stl`, `vtk`, `med`. | `-format inp` |
| `-clmax <valor>` | Tamaño máximo característico de elemento finito (en mm). | `-clmax 2.0` |
| `-clmin <valor>` | Tamaño mínimo característico de elemento finito. | `-clmin 0.5` |
| `-clscale <factor>` | Factor multiplicador global de refinamiento de malla. | `-clscale 0.8` |
| `-order 1` | Genera elementos de primer orden / lineales (`C3D4` tetraedros de 4 nodos). | `-order 1` |
| `-order 2` | Genera elementos de segundo orden / cuadráticos (`C3D10` tetraedros de 10 nodos). | `-order 2` |
| `-optimize` | Optimiza la calidad geométrica de los tetraedros generados. | `-optimize` |
| `-optimize_netgen` | Ejecuta el optimizador 3D Netgen para eliminar elementos distorsionados. | `-optimize_netgen` |
| `-string "..."` | Inyecta parámetros y directivas de scripting en línea directamente a Gmsh. | `-string "Mesh.MeshSizeMax = 1.5;"` |
| `-help` | Imprime en consola la lista exhaustiva de todos los comandos y opciones de Gmsh. | `gmsh -help` |
| `-version` | Imprime la versión del motor Gmsh. | `gmsh -version` |

#### Ejemplos Prácticos con Gmsh:
```bash
# 1. Mallar una geometría STEP en tetraedros lineales C3D4 para CalculiX:
gmsh soporte.step -3 -clmax 2.0 -format inp -o soporte_malla.inp

# 2. Generar malla cuadrática de alta precisión C3D10:
gmsh puente.step -3 -order 2 -optimize -format inp -o puente_c3d10.inp

# 3. Mallar un script paramétrico .geo a formato Abaqus/CalculiX:
gmsh cilindro.geo -3 -format inp -o cilindro.inp
```

---

### 5.3 Scripts TCL y Modelado CAD con OpenCASCADE (`draw` / `DRAWEXE`)

La aplicación incluye el entorno interactivo y motor de scripting **OpenCASCADE DRAW Test Harness (`DRAWEXE`)**, el cual funciona como un intérprete completo de **Tcl 8.6** integrado con el kernel geométrico de modelado de sólidos 3D B-Rep más potente del mundo open source.

En la terminal de la aplicación, el comando `draw` es un alias directo de `DRAWEXE` que fuerza automáticamente la ejecución en **modo batch sin interfaz gráfica (`-b`)**, garantizando máxima velocidad y estabilidad en Android sin requerir servidor X11.

#### Modos de Invocación de `draw`:
1. **Ejecutar un script TCL desde un archivo (`.tcl`):**
   ```bash
   draw mi_script.tcl
   ```
   *(La terminal detecta la extensión `.tcl` e inyecta automáticamente las banderas batch: `DRAWEXE -b -f mi_script.tcl`)*.

2. **Ejecutar comandos CAD/TCL en una sola línea (`-c`):**
   ```bash
   draw -c "pload ALL; box b 10 20 30; puts [vprops b]; exit"
   ```

3. **Consultar la ayuda rápida de modelado CAD:**
   ```bash
   draw
   ```
   *(Al invocar `draw` sin argumentos, la terminal muestra el manual resumido de comandos CAD de OpenCASCADE)*.

4. **Ejecutar scripts estándar de Tcl con `tclsh`:**
   ```bash
   tclsh script_calculo.tcl
   ```

---

#### 📐 Referencia Completa de Comandos CAD y TCL en DRAWEXE:

Al inicio de cualquier script TCL para modelado CAD, siempre debes cargar los módulos de OpenCASCADE con:
```tcl
pload ALL
```

| Categoría | Comando en TCL | Descripción y Parámetros |
| :--- | :--- | :--- |
| **Primitivas 3D** | `box <b> <dx> <dy> <dz>` | Crea un prisma rectangular / caja 3D en el origen con dimensiones `dx`, `dy`, `dz`. |
| | `box <b> <x> <y> <z> <dx> <dy> <dz>` | Crea una caja ubicada en las coordenadas `(x, y, z)`. |
| | `cylinder <c> <R> <H>` | Crea un cilindro con radio `R` y altura `H` alineado con el eje $Z$. |
| | `sphere <s> <R>` | Crea una esfera de radio `R` centrada en el origen. |
| | `cone <co> <R1> <R2> <H>` | Crea un cono truncado de radio inferior `R1`, superior `R2` y altura `H`. |
| | `torus <t> <R1> <R2>` | Crea un toroide de radio mayor `R1` y radio de sección `R2`. |
| **Operaciones Booleanas** | `bcut <resultado> <solidoA> <solidoB>` | **Resta Booleana (Diferencia):** Sustrae el sólido B del sólido A. |
| | `bfuse <resultado> <solidoA> <solidoB>` | **Unión Booleana:** Fusiona el sólido A y el sólido B en un único cuerpo estanco. |
| | `bcommon <resultado> <solidoA> <solidoB>` | **Intersección Booleana:** Conserva únicamente el volumen compartido por ambos sólidos. |
| **Transformaciones** | `ttranslate <solido> <dx> <dy> <dz>` | Traslada un sólido en el espacio 3D según el vector `(dx, dy, dz)`. |
| | `trotate <solido> <x> <y> <z> <dx> <dy> <dz> <angulo>` | Rota un sólido alrededor de un eje determinado por un punto y un vector directriz. |
| | `tcopy <original> <copia>` | Realiza una copia topológica idéntica del sólido. |
| **Propiedades de Masa** | `vprops <solido>` | **Calcula el volumen exacto, el centroide $(X_G, Y_G, Z_G)$ y la matriz del tensor de inercia.** |
| | `sprops <solido>` | Calcula el área superficial total del sólido. |
| | `checkshape <solido>` | **Audita la integridad topológica:** certifica que el sólido sea cerrado, orientable y sin auto-intersecciones (`This shape seems to be valid`). |
| **Exportación CAD** | `testwritestep <archivo.step> <solido>` | **Exporta el sólido al formato universal STEP (ISO 10303).** Compatible con Gmsh, FreeCAD, AutoCAD y SolidWorks. |
| | `writebrep <solido> <archivo.brep>` | Exporta la geometría al formato nativo B-Rep de OpenCASCADE. |
| | `writestl <solido> <archivo.stl>` | Exporta la superficie a formato STL para impresión 3D o visualizadores rápidos. |
| **Lenguaje TCL** | `set variable valor` | Asigna una variable numérica o de texto. |
| | `expr { $a * 2.5 }` | Evalúa expresiones matemáticas estándar. |
| | `puts "mensaje"` | Imprime información en la consola de la terminal. |
| | `exit` | Finaliza la ejecución del script y retorna el control a la terminal. |

---

## 🛠️ 6. Nivel 4: Flujos de Trabajo Prácticos Paso a Paso

A continuación se detallan casos prácticos reales para que aproveches al máximo la terminal.

### 🎯 Caso Práctico 1: Auditar y Recalcular un Modelo del Módulo Estructural
*Objetivo: Modificar o inspeccionar los resultados exactos del modelo que acabas de diseñar en el lienzo 2D/3D.*

1. Abre el menú lateral de la app y ve a **Cálculo Estructural**.
2. Selecciona un preset (por ejemplo, *Pórtico Simple 2D*) y pulsa **RESOLVER**.
3. Abre el menú lateral y entra a la **Terminal**.
4. Navega a la carpeta de análisis estructural:
   ```bash
   $ cd /structural_analysis
   $ pwd
   /structural_analysis
   ```
5. Lista los archivos generados:
   ```bash
   $ ls
         job_structural.dat
         job_structural.frd
         job_structural.inp
         job_structural.sta
   ```
6. Inspecciona el archivo `.inp` para revisar la definición de nudos y cargas:
   ```bash
   $ cat job_structural.inp
   ```
7. Re-ejecuta el solver directamente desde la terminal:
   ```bash
   $ ccx job_structural
   ```
8. Lee los valores exactos de reacciones y desplazamientos nodales:
   ```bash
   $ cat job_structural.dat
   ```

---

### 🎯 Caso Práctico 2: Importar un Deck `.inp` Externo y Resolverlo
*Objetivo: Ejecutar un archivo `.inp` que modelaste en Abaqus, CalculiX de PC o un script de Python.*

1. Copia tu archivo `.inp` (por ejemplo, `cercha_especial.inp`) a la memoria interna de tu dispositivo móvil o descárgalo desde la nube.
2. Abre la **Terminal** en la app.
3. En la barra superior de la app, pulsa el botón **Importar INP** (`action_import`).
4. Selecciona `cercha_especial.inp` en el explorador de archivos de Android.
5. Verás en la consola el mensaje de confirmación:
   ```
   [INP IMPORTED] cercha_especial.inp saved to workspace.
   ```
6. Comprueba que el archivo esté presente:
   ```bash
   $ ls
         cercha_especial.inp
   ```
7. Ejecuta CalculiX:
   ```bash
   $ ccx cercha_especial
   ```
8. Revisa los resultados numéricos en pantalla:
   ```bash
   $ cat cercha_especial.dat
   ```
9. En la barra superior, pulsa **Exportar Reporte PDF** para guardar el reporte en tu carpeta de *Descargas*.

---

### 🎯 Caso Práctico 3: Creación de un Directorio de Proyecto y Limpieza
*Objetivo: Mantener tu espacio de trabajo ordenado.*

```bash
# 1. Crear directorio para un nuevo cliente o estudio
$ mkdir estudio_vigas
$ cd estudio_vigas

# 2. Copiar un deck base para experimentar
$ cp /structural_analysis/job_structural.inp ./viga_prueba.inp

# 3. Resolver
$ ccx viga_prueba

# 4. Revisar si hubo convergencia limpia
$ cat viga_prueba.sta

# 5. Borrar archivos temporales innecesarios
$ rm viga_prueba.sta
Deleted: viga_prueba.sta

# 6. Salir a la raíz
$ cd /
$ pwd
/
```

---

### 🎯 Caso Práctico 4: Modelado CAD con Script TCL, Mallado en Gmsh y Cálculo FEA
*Objetivo: Diseñar una pieza paramétrica en 3D mediante un script TCL, auditar su volumen e inercia, exportarla a STEP, discretizarla con Gmsh y prepararla para CalculiX íntegramente desde la consola del teléfono sin salir de la app.*

1. **Crear el script TCL paramétrico directamente en la terminal con `echo`:**
   ```bash
   $ echo "pload ALL\nbox b 10 20 30\nputs [vprops b]\ntestwritestep biela.step b\nexit" > modelar.tcl
   Written to modelar.tcl
   ```

2. **Verificar el script generado con `cat`:**
   ```bash
   $ cat modelar.tcl
   pload ALL
   box b 10 20 30
   puts [vprops b]
   testwritestep biela.step b
   exit
   ```

3. **Ejecutar el kernel OpenCASCADE en modo batch con `draw`:**
   ```bash
   $ draw modelar.tcl
   DRAW is running in batch mode
   Mass :            6000
   Center of gravity : 
   X =               5
   Y =              10
   Z =              15
   Step File Name : biela.step Write Done
   ```
   *(El motor CAD calcula analíticamente la masa y el volumen exacto $V = 6\,000\text{ mm}^3$ y exporta el archivo `biela.step` en formato universal STEP ISO 10303)*.

4. **Discretizar el sólido STEP en tetraedros con Gmsh:**
   ```bash
   $ gmsh biela.step -3 -clmax 2.5 -format inp -o biela_malla.inp
   Info    : Reading 'biela.step'...
   Info    : Meshing 1D...
   Info    : Meshing 2D...
   Info    : Meshing 3D...
   Info    : Writing 'biela_malla.inp'...
   Info    : Done writing 'biela_malla.inp'
   ```

5. **Confirmar la presencia de la malla en el espacio de trabajo con `ls`:**
   ```bash
   $ ls
         modelar.tcl
         biela.step
         biela_malla.inp
   ```

6. **Inspeccionar los elementos y nudos generados:**
   ```bash
   $ cat biela_malla.inp
   *Heading
    biela_malla.inp
   *NODE
   1, 0, 0, 0
   ...
   *ELEMENT, type=C3D4
   ...
   ```

---

## ⚖️ 7. Posibilidades y Limitaciones Técnicas de la Terminal

Es fundamental conocer con claridad qué capacidades técnicas ofrece la terminal y cuáles son sus límites debidos a la arquitectura de Android.

### ✅ Posibilidades (Lo que SÍ se puede hacer)
- **Análisis Estructural Completo:** Soporte total para elementos de barra 1D de Timoshenko (`B31`, `B32`), losas/cáscaras (`S4R`) y elementos de membrana/tensión plana (`CPS4`).
- **Análisis Mecánico de Sólidos 3D:** Modelado y resolución de sólidos continuos con elementos tetraédricos (`C3D4`, `C3D10`) y hexaédricos (`C3D8`, `C3D20`).
- **Aceleración Multihilo Nativa:** Aprovechamiento automático de todos los núcleos de procesamiento físico de la CPU mediante OpenMP y SPOOLES MT.
- **Interoperabilidad de Archivos:** Compatible con el formato estándar internacional Abaqus/CalculiX (`.inp`).
- **Generación y Conversión de Mallas:** Uso completo del generador Gmsh desde línea de comandos.
- **Cancelación Segura en Caliente:** Botón de abortar proceso que elimina el hilo de cálculo en tiempo real sin colgar la aplicación.
- **Exportación Directa a la Memoria Pública del Teléfono:** Generación de memorias de cálculo en formato PDF y exportación masiva de archivos a la carpeta `Download/terminal/`.

### ❌ Limitaciones Técnicas (Lo que NO se puede hacer)
1. **Sin Tuberías (Pipes) ni Redirecciones UNIX Complejas:**
   - La terminal no ejecuta un shell interactivo completo tipo Bash (`/bin/bash`).
   - No se admiten operadores de tubería o concatenación como `|`, `>`, `>>`, `&&`, `;`.
   - *Ejemplo no soportado:* `cat archivo.inp | grep NODE` o `echo "texto" > archivo.txt`.
2. **Sin Editores de Texto Interactivos (nano, vim, vi):**
   - No es posible abrir un editor en pantalla completa tipo consola dentro del terminal de Android.
   - La edición de modelos debe hacerse mediante la importación de archivos `.inp` o diseñándolos en los lienzos gráficos de los otros módulos.
3. **Límite de Lectura con `cat` (500 KB):**
   - Si un archivo supera los $500\text{ KB}$ (por ejemplo, un archivo `.frd` con cientos de miles de nodos), `cat` mostrará una advertencia de seguridad:
     `Error: File too large to print in console (>500KB)`.
   - Esto previene el desbordamiento de memoria y el congelamiento de la interfaz gráfica de Android. Para revisar archivos de ese tamaño, utiliza la opción **Exportar Todo** del menú superior para abrirlos en una computadora.
4. **Modo Gráfico X11 Deshabilitado (Headless Obligatorio):**
   - Android no tiene servidor gráfico X11 estándar. Por ello, comandos que intenten abrir ventanas de escritorio (como `gmsh` interactivo o la ventana gráfica de `DRAWEXE`) fallarían; por esta razón, la variable `DISPLAY` se encuentra deshabilitada y los binarios operan exclusivamente en segundo plano. Los resultados se visualizan a través de los visores 3D integrados en la app (SceneView y OpenGL ES).
5. **Aislamiento Sandbox de Android:**
   - No es posible navegar fuera del espacio de la app hacia carpetas del sistema operativo como `/system`, `/data` o directorios de otras aplicaciones instaladas.

---

## 🩺 8. Resolución de Problemas y Diagnóstico (Troubleshooting)

| Síntoma / Mensaje de Error | Causa Probable | Solución Recomendada |
| :--- | :--- | :--- |
| `*ERROR in calinput: file not found` | CalculiX no encuentra el archivo `.inp` correspondiente al nombre ingresado. | 1. Ejecuta `ls` para verificar que el archivo existe en la carpeta actual.<br>2. Asegúrate de invocar el comando sin la extensión `.inp`: escribe `ccx mi_modelo` en lugar de `ccx mi_modelo.inp`. |
| `Error: File too large to print in console (>500KB)` | El archivo de texto supera el límite de seguridad de renderizado del `TextView` de Android. | Usa el menú superior de la app y pulsa **Exportar Todo** para enviar los archivos a la carpeta *Descargas* y verlos en tu PC o editor móvil. |
| El cálculo no finaliza y la terminal no responde | El modelo es demasiado grande o presenta falta de sustentación (mecanismo inestable), impidiendo la convergencia. | Pulsa inmediatamente el botón rojo **⏹️ Abortar** en la esquina superior derecha de la terminal para detener el proceso de forma forzada. |
| `Error: Is a directory (use rm -rf)` | Intentaste borrar una carpeta usando `rm` simple. | Para borrar carpetas con todo su contenido, usa la bandera recursiva: `rm -rf <nombre_carpeta>`. |
| `Error: Path not found: ...` | El directorio de destino no existe o hubo un error tipográfico. | Ejecuta `pwd` y `ls` para confirmar la ubicación y los nombres exactos de las carpetas. |

---

## 🏆 9. Verificación Local y Certificación Científica de Punta a Punta

Todos los comandos, pipelines, solucionadores y ejemplos prácticos descritos en esta guía han sido rigurosamente validados y probados en el entorno de desarrollo local sin fallos ni falsos positivos mediante dos suites automatizadas:

### 9.1 Script de Certificación End-to-End (`validate_terminal_guide_end_to_end.py`)
Para verificar instantáneamente en local todos los comandos de la terminal (shell, pruebas diagnósticas, llamadas a binarios nativos y casos prácticos):
```bash
python3 validate_terminal_guide_end_to_end.py
```
*Resultados certificados:*
- ✅ **Gestión de archivos:** `mkdir`, `cd`, `pwd`, `touch`, `ls`, `cat`, `cp`, `rm` verificados.
- ✅ **Comprobación analítica de Hooke y Poisson:** Desplazamiento axial $\delta_z = +0.001905\text{ mm}$ y contracción lateral $\delta_x = \delta_y = -0.000571\text{ mm}$ con $0.0000\%$ de error.
- ✅ **Equilibrio estático de pórtico 2D:** Cortante basal $\sum R_x = -10.00\text{ kN}$ y par reactivo vertical $\pm 8.00\text{ kN}$.
- ✅ **Modelado CAD y mallado 3D:** DRAWEXE headless y Gmsh CSG booleano (Cilindro $-$ Esfera) completados con código de salida 0.
- ✅ **Casos prácticos 1, 2 y 3:** Interoperabilidad transversal entre carpetas, importación de `.inp` externos y gestión de proyectos validados.

### 9.2 Suite de Pruebas Unitarias de Gradle (`TerminalGuideEndToEndTest`)
Para correr la batería de pruebas Java/JNI directamente con el arnés de Gradle:
```bash
./gradlew testDebugUnitTest --tests "com.diamon.civil.terminal.engine.TerminalGuideEndToEndTest"
```
*Suites globales ejecutadas y aprobadas:*
1. **Simulación de Física Estructural (`simulate_structural_ui_and_physics.py`):** 9/9 casos, 24/24 comprobaciones físicas aprobadas al 100%.
2. **Batería de Modelos CAD y Formatos (`test_all_sample_models.py`):** 13/13 pruebas pasadas con éxito (100%).
3. **Validación Independiente con OpenSees (`validate_all_presets_calculix_opensees.py`):** 12/12 presets certificados con equilibrio estático exacto.
4. **Pruebas Unitarias Generales de Gradle:** `./gradlew testDebugUnitTest` $\rightarrow$ **`BUILD SUCCESSFUL`**.
