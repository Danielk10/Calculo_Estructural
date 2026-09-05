# 📦 Notas de Versión
## Structural Analysis FEA 3D (`com.diamon.civil`) — Versión Alfa 0.3.0 (Build 3)

---

### 🌟 Resumen Ejecutivo de la Versión Alfa 0.3.0

La versión **Alfa 0.3.0 (Build 3)** representa un hito fundamental en el desarrollo de **Structural Analysis FEA 3D**, transformando la consola técnica en un entorno de ingeniería computacional y scripting de primer nivel para dispositivos móviles. Se introduce soporte completo para **scripts paramétricos en Tcl 8.6**, modelado de sólidos 3D headless con **OpenCASCADE (`draw` / `DRAWEXE`)**, control exhaustivo del mallador **Gmsh** mediante banderas avanzadas, ejecución directa del solver **CalculiX CCX 2.23**, herramientas de creación y edición de archivos en el dispositivo (`echo > / >>`), una **Guía Maestra Paso a Paso** de 9 capítulos, y certificación científica automatizada de punta a punta con $0.0000\%$ de error analítico. Adicionalmente, se perfecciona la interfaz de usuario con la reubicación fija del menú de licencias, la unificación del prompt del sistema en inglés y la optimización de los textos de entrada para pantallas compactas.

---

### 💎 Actualización: Validación Integral de Sólidos 3D y Arquitectura Modular de Reportes PDF

Esta actualización incorpora una validación exhaustiva de punta a punta del **Módulo de Sólidos 3D FEA** (`com.diamon.civil.solids`), la estricta compatibilidad con la física real del medio continuo elástico, la corrección del ensamblado de mallas para CalculiX CCX, la compatibilidad con formatos CAD / INP y la independencia total de los generadores de reportes PDF por módulo:

1. **Validación Completa de la UI y Opciones de Sólidos 3D:**
   * **Barra CAD de Modelado 3D:** Creación y visualización de primitivas B-Rep (Caja, Cilindro, Esfera) y operaciones booleanas OpenCASCADE (Unión `bfuse`, Corte `bcut`, Intersección `bcommon`).
   * **Discretización y Mallado 3D (Gmsh):** Control del tamaño métrico de elementos (`clmax`), selector de orden polinomial (1er orden lineal vs 2do orden cuadrático con nodos intermedios) y algoritmo de recombinación hexaédrica.
   * **Catálogo Completo de Elementos Finitos 3D Continuum:** Soporte íntegro para las 8 familias estándar de CalculiX/Abaqus:
     * Tetraedros: `C3D4` (lineal de 4 nodos) y `C3D10` (cuadrático de 10 nodos).
     * Hexaedros / Ladrillos: `C3D8` (lineal de 8 nodos), `C3D8R` (integración reducida), `C3D20` (cuadrático de 20 nodos) y `C3D20R` (cuadrático reducido).
     * Prismas / Cuñas: `C3D6` (lineal de 6 nodos) y `C3D15` (cuadrático de 15 nodos).
   * **Materiales y Condiciones de Borde:** Asignación dinámica de Módulo de Young ($E$), Coeficiente de Poisson ($\nu$) y Densidad ($\rho$); fijación de caras y aplicación de cargas concentradas y distribuidas en $F_X, F_Y, F_Z$.
   * **Visor 3D SceneView Interactivo:** Renderizado GLB con escala de deformada, contornos de tensión de Von Mises y consola de logs del solver en tiempo real.

2. **Compatibilidad con la Física Real y Correcciones en el Ensamblador (`SolidInpAssembler`):**
   * **Reordenamiento Estricto de Bloques CalculiX (`*NODE` antes de `*ELEMENT`):** Se implementó un búfer en `SolidInpAssembler` que asegura que las definiciones de nodos siempre antecedan a los elementos en `job_clean.inp`, erradicando el error `value in set ... > nk` (código de salida 201) al procesar mallas invertidas o generadas externamente.
   * **Eliminación de Solapamiento entre Apoyos y Cargas:** Se eliminan automáticamente los nodos con apoyos fijos de los conjuntos de carga (`loadedNodes.removeAll(fixedNodes)`), previniendo que los desplazamientos prescritos ($u=0$) anulen la carga y asegurando la transmisión total de esfuerzos a través del continuo elástico.
   * **Certificación Analítica contra Mecánica de Materiales (Timoshenko / Navier):**
     * Modelo de viga en voladizo ($L = 100\text{ mm}$, $b=h=10\text{ mm}$, $E = 200{,}000\text{ MPa}$, $\nu = 0.3$, $P = 100\text{ N}$):
       * Flecha máxima teórica: $\delta = \frac{P L^3}{3EI} + \frac{P L}{\kappa G A} = 0.2016\text{ mm}$.
       * Flecha calculada por FEA (C3D10): $0.2000\text{ mm}$ (**99.2% de coincidencia**).
       * Tensión normal teórica de flexión (Navier): $\sigma_{max} = \frac{M c}{I} = 60.00\text{ MPa}$.
       * Tensión de Von Mises FEA: $58.33\text{ MPa}$ (**97.2% de coincidencia**).

3. **Compatibilidad Universal de Formatos CAD y Decks de Malla:**
   * Soporte unificado de importación para archivos CAD paramétricos y B-Rep (`.step`, `.stp`, `.brep`, `.iges`, `.igs`), scripts nativos de Gmsh (`.geo`) y decks de elementos finitos Abaqus/CalculiX (`.inp`).
   * Enrutamiento inteligente desde el menú superior de la aplicación (`MainActivity`): si el usuario importa un archivo `.inp`, se transfiere al espacio de trabajo y se ensambla directamente respetando las propiedades mecánicas y cargas configuradas en la UI.
   * Protección del espacio de trabajo en `cleanSimulationWorkspace()` para preservar geometrías B-Rep y decks de malla de usuario.

4. **Arquitectura Modular e Independiente de Reportes PDF:**
   * **Generador Dedicado de la Terminal (`TerminalPDFReportGenerator`):** Se creó un generador exclusivo para el módulo de terminal en `com.diamon.civil.terminal.export`, eliminando la dependencia previa de `SolidPDFReportGenerator`.
   * **Título Oficial en Inglés para la Terminal:** `TERMINAL EXECUTION & ANALYSIS REPORT` (subtítulo: `Interactive CLI & Engineering Engines Execution Record`), con encabezados y pies de página propios (`Structural Analysis FEA 3D | Terminal Execution Report`).
   * **Especificaciones Técnicas de Motores Nativos:** Documentación de CalculiX FEA (ccx 2.23 multihilo OpenMP), Gmsh (5.0.0), OpenCASCADE Technology (OCCT 8.0.0.p1 DRAWEXE) y arquitectura Android NDK ARM64-v8a.
   * **Tarjeta Ejecutiva de Estadísticas:** Conteo automático de comandos interactivos ejecutados (`$ `), total de líneas y volumen de caracteres.
   * **Aislamiento Total de Reportes por Módulo:**
     1. Módulo Estructural: `PDFReportGenerator` (`STRUCTURAL CALCULATION REPORT`).
     2. Módulo de Sólidos 3D: `SolidPDFReportGenerator` (`3D SOLID ANALYSIS REPORT`).
     3. Módulo de Terminal: `TerminalPDFReportGenerator` (`TERMINAL EXECUTION & ANALYSIS REPORT`).

5. **Aceleración Multi-Núcleo Integral (CalculiX CCX y Gmsh Runner):**
   * **CalculiX:** Configuración dinámica que asigna automáticamente todos los núcleos de procesador disponibles en el dispositivo móvil (`Runtime.getRuntime().availableProcessors()`), habilitando paralelismo completo en la descomposición de matrices de rigidez y cálculo de tensiones mediante el solucionador sparse multihilo SPOOLES (`CCX_NPROC_EQUATION_SOLVER` y `OMP_NUM_THREADS`).
   * **Gmsh:** Inyección automática del número de hilos de procesamiento (`-nt <cores>`) y entorno OpenMP (`OMP_NUM_THREADS`, `OMP_STACKSIZE=64M`) en el generador de mallas volumétricas 3D, con fallback inteligente a monohilo si la geometría importada presenta singularidades o auto-intersecciones complejas.
   * **Estabilidad de Pila:** Asignación de pila extendida `OMP_STACKSIZE=64M` para erradicar fallos de desbordamiento en mallas de alta densidad.

6. **Prevención Total de Corrupción de Datos y Purga Sistemática de Temporales:**
   * Eliminación exhaustiva antes y después de cada análisis numérico de archivos residuales del solucionador (`spooles.out`, `spooles.log`, `intpoints.out`, `slavintmortar.out`, `temporaryrestartfile`, `.cvg`, `.sta`, `.12d`, `.fcv`, `.cel`, `.eig`, `.rout`, `.nam`, `.dat`, `.frd`), garantizando 100% de determinismo e independencia entre corridas consecutivas y erradicando inconsistencias numéricas cruzadas.
   * Preservación estricta de geometrías CAD de entrada (`.step`, `.stp`, `.geo`, `.iges`, `.igs`, `.brep`), decks `.inp` y reportes PDF.

7. **Consistencia Visual en la Terminal y Editor de Código FEA:**
   * Sustitución del emoji gráfico de disquete por la convención clásica de comandos de terminal: `[^S] Guardar | [^X] Salir | [Tab] Tab | [Del] Borrar`.
   * Unificación de controles y botones de acción (`btnAbort` y `btnCloseEditor`) con el color verde característico de la terminal (`@color/terminal_green`), manteniendo un tema visual técnico homogéneo.

8. **Extracción y Ordenamiento Robusto de Resultados en el Reporte PDF (`SolidPDFReportGenerator`):**
   * **Flexibilización de Cabeceras:** El analizador léxico de CalculiX ahora detecta de forma resiliente cualquier variante de salida para tensiones en puntos de Gauss (`stresses (elem, integ.pnt`), asegurando compatibilidad con todas las versiones y compilaciones de CalculiX CCX.
   * **Ordenamiento por Magnitud Euclidiana:** Clasificación automática descendente de desplazamientos nodales ($|U| = \sqrt{U_x^2 + U_y^2 + U_z^2}$) y tensiones de Von Mises ($\sigma_{\text{vm}}$) para destacar de inmediato los puntos críticos y elementos con mayor concentración de esfuerzos.
   * **Eliminación Total de Logs Crudos:** Los reportes de sólidos 3D ahora presentan exclusivamente tablas de ingeniería limpias y formateadas profesionalmente para formato A4, con membretes y metadatos periciales.

9. **Certificación Científica Integral Automatizada (`validate_solids_complete_matrix.py`):**
   * Suite completa de pruebas locales que valida de punta a punta **28/28 casos con 100.0% de éxito**:
     * **8 Familias de Elementos Finitos:** `C3D4`, `C3D8`, `C3D8R`, `C3D6`, `C3D10`, `C3D20`, `C3D20R` y `C3D15`.
     * **5 Niveles de Densidad de Malla:** De Muy Gruesa a Muy Fina (hasta 7,428 elementos `C3D10`), comprobando convergencia monotónica hacia la solución exacta de Timoshenko ($0.2016\text{ mm}$, error $< 0.8\%$).
     * **3 Primitivas CAD BRep:** Caja, Cilindro y Esfera.
     * **4 Modelos CAD Industriales Reales:** Ménsula estructural con taladros (`bracket_simple.step`), perno roscado (`screw.step`), brazo de biela (`CrankArm.brep`) y tapa de bomba hidráulica (`Pump_TopCover.brep`).
     * **Operaciones Booleanas Constructivas (CSG):** Cilindro hueco por corte en `DRAWEXE` / OpenCASCADE.
     * **Configuraciones de Interfaz:** Ensayos en Acero A36, Concreto 25 MPa y Aluminio 6061 con cargas axiales, transversales y cortantes en $\pm X, \pm Y, \pm Z$, y selectores multilenguaje (Español/Inglés).

---

### 🚀 Novedades y Capacidades Principales en la Versión v0.3.0

#### 1. 💻 Terminal de Comandos Avanzada y Motor de Scripting (TCL + CAD + Gmsh + CCX)
* **Soporte Nativo de Scripts TCL y CAD Headless (`draw` / `DRAWEXE`):**
  * **Intérprete Tcl 8.6 integrado:** Inclusión de OpenCASCADE Test Harness como intérprete nativo de Tcl para modelado geométrico y evaluación topológica de sólidos 3D.
  * **Ejecución Batch Headless Automática:** Los comandos `draw <script.tcl>` o `drawexe <script.tcl>` inyectan de forma transparente las banderas `-b -f`, permitiendo ejecutar scripts paramétricos sin requerir servidor X11 (`DISPLAY`), garantizando estabilidad total en Android y previniendo fallos gráficos.
  * **Invocación en una Sola Línea:** Soporte para ejecución inline mediante el modificador `-c` (`draw -c "pload ALL; box b 10 20 30; puts [vprops b]; exit"`).
  * **Manual Interactivo Integrado:** Al teclear simplemente `draw` en la consola, se despliega una guía de referencia rápida con la sintaxis de primitivas, operaciones booleanas y comandos de exportación.
  * **Intérprete Tcl Estándar:** Soporte para scripts Tcl puros mediante el comando `tclsh <script.tcl>`.
  * **Catálogo de Comandos CAD Disponibles y Validados en Local:**
    * **Primitivas 3D:** `box <nombre> <dx> <dy> <dz>`, `cylinder <nombre> <radio> <altura>`, `sphere <nombre> <radio>`, `cone`, `torus`.
    * **Operaciones Booleanas Constructivas (CSG):** `bcut <resultado> <objeto> <herramienta>` (diferencia booleana), `bfuse <resultado> <obj1> <obj2>` (unión booleana), `bcommon <resultado> <obj1> <obj2>` (intersección).
    * **Propiedades Físicas y Evaluación Topológica:** `vprops <solido>` (cálculo exacto de volumen, centro de gravedad $X_G, Y_G, Z_G$ y matriz del tensor de inercia), `sprops <solido>` (área superficial total) y `checkshape <solido>` (auditoría topológica de caras cerradas, orientación de normales y estanqueidad del volumen).
    * **Exportación Universal de Geometría:** `testwritestep <archivo.step> <solido>` (exportación en formato universal STEP ISO 10303 compatible con Gmsh, FreeCAD, AutoCAD y SolidWorks) y `writebrep <solido> <archivo.brep>`.
* **Control Completo de Gmsh desde la Terminal (`gmsh`):**
  * Soporte exhaustivo para todas las banderas oficiales de línea de comandos de Gmsh:
    * **Dimensión de Discretización:** `-1` (barras 1D), `-2` (placas y cáscaras 2D), `-3` (mallas volumétricas 3D de tetraedros).
    * **Formatos de Salida:** `-format inp` (Abaqus/CalculiX deck con nodos y conectividades), `-format msh` (formato nativo Gmsh), `-format stl` (estereolitografía para impresión 3D).
    * **Control Métrico de Elementos:** `-clmax <valor>` (longitud máxima de arista), `-clmin <valor>` (longitud mínima), `-clscale <factor>` (factor multiplicador global).
    * **Orden Polinomial de Elementos Finitos:** `-order 1` (tetraedros lineales `C3D4`) y `-order 2` (tetraedros cuadráticos de alta precisión `C3D10` con nodos intermedios de arista).
    * **Optimización Geométrica de Elementos:** Banderas `-optimize` y `-optimize_netgen` para eliminar tetraedros degenerados y garantizar Jacobianos positivos en toda la malla.
    * **Inyección de Directivas Dinámicas:** Parámetro `-string "..."` para definir geometrías y variables directamente en la invocación de Gmsh.
    * **Consulta de Ayuda y Versión:** `gmsh -help` y `gmsh -version`.
* **Solucionador CalculiX CCX Directo (`ccx`):**
  * Admite invocación por nombre base del modelo (`ccx viga`), con la bandera oficial Abaqus/CalculiX (`ccx -i viga`), o especificando la extensión completa (`ccx viga.inp`).
  * Consulta oficial de versión (`ccx -v`, reportando *CalculiX Version 2.23 by Guido Dhondt* con manejo seguro del código de salida nativo 201) y ayuda de opciones (`ccx -h`).
* **Creación y Edición de Archivos en el Móvil con `echo`:**
  * Soporte integrado de redirección:
    * `echo "<contenido>" > <archivo>`: Creación o sobreescritura limpia de archivos.
    * `echo "<contenido>" >> <archivo>`: Concatenación y adición secuencial de texto al final del archivo.
  * Interpretación completa de secuencias de escape de salto de línea (`\n`), permitiendo al usuario programar scripts paramétricos `.tcl`, archivos geométricos `.geo` o decks `.inp` directamente desde el teclado del dispositivo móvil sin recurrir a un ordenador externo.
* **Tokenizador Léxico Avanzado con Preservación de Comillas (`splitCommandLine`):**
  * Analizador sintáctico robusto que preserva comillas dobles y simples (`"..."` y `'...'`), permitiendo pasar cadenas con espacios, directivas multilínea de Tcl y rutas complejas de archivos sin fragmentación indeseada de argumentos.
* **Suite de Comandos Especiales de Diagnóstico Físico (`test-*`):**
  * `test-calculix` y `test-calculix-parallel`: Validación de elasticidad lineal tridimensional (Hooke y contracción de Poisson en cubo `C3D8`) con $0.0000\%$ de error analítico y aceleración multihilo SPOOLES.
  * `test-frame` / `test-portico`: Pórtico plano 2D con elementos `B31` y comprobación estricta de equilibrio estático global en apoyos ($\sum R_x = -10.00\text{ kN}$, par de reacciones verticales $\pm 8.00\text{ kN}$).
  * `test-gmsh`: Operación booleana CSG (Cilindro $-$ Esfera) y discretización tetraédrica 3D.
  * `test-draw`: Generación y exportación de prisma BRep paramétrico con OpenCASCADE en modo batch headless.
  * `test-cad-solve`: Pipeline integral automatizado: Script TCL $\to$ Sólido STEP $\to$ Malla Gmsh INP $\to$ InpAssembler $\to$ CalculiX CCX $\to$ Parser FRD.
  * `test-step-solve`, `test-bracket-solve`, `test-coordinate-fallback` y `run-sim-test`.

---

#### 2. 🎨 Experiencia de Usuario e Interfaz Gráfica (UI/UX)
* **Reordenamiento Estricto del Menú Principal de Opciones:**
  * En [`main_menu.xml`](file:///home/danielpdiamon/Calculo_Estructural/app/src/main/res/menu/main_menu.xml), el ítem **"Acerca de / Licencias"** (`action_licenses`) ha sido reubicado de forma definitiva después de **"Políticas de privacidad"** (`action_privacy_policy`), asegurando que siempre sea el último elemento de la lista desplegable.
* **Optimización de la Caja de Entrada de la Terminal:**
  * Se ajustó el texto de ayuda (*hint*) del campo de entrada para evitar desbordamientos visuales o saltos de línea antiestéticos en pantallas de teléfonos móviles compactos:
    * **Español:** `"Ingrese comando FEA"` (19 caracteres, encaje visual exacto).
    * **Inglés:** `"Enter FEA command"`.
* **Etiqueta del Prompt Uniforme en Inglés:**
  * En [`values-es/strings.xml`](file:///home/danielpdiamon/Calculo_Estructural/app/src/main/res/values-es/strings.xml) y [`values/strings.xml`](file:///home/danielpdiamon/Calculo_Estructural/app/src/main/res/values/strings.xml), la etiqueta del prompt se mantiene uniforme como **`fea@system:~$`** en todos los idiomas sin ser traducida a español, preservando el estándar visual clásico de terminales UNIX.

---

#### 3. 📚 Documentación Técnica Integral
* **Nueva Guía Maestra Paso a Paso ([`GUIA_TERMINAL_APP_PASO_A_PASO.md`](file:///home/danielpdiamon/Calculo_Estructural/GUIA_TERMINAL_APP_PASO_A_PASO.md)):**
  * Manual exhaustivo de 9 capítulos con tablas de sintaxis, manual de binarios y ejemplos prácticos:
    * *Capítulo 1:* Introducción, Fundamentos y Filosofía de la Terminal Móvil.
    * *Capítulo 2:* Navegación por el Sistema de Archivos, Sandbox y Protección de Datos.
    * *Capítulo 3:* Creación y Edición de Archivos en el Teléfono (`echo`, redirección `>` y `>>`).
    * *Capítulo 4:* Modelado CAD 3D y Scripts Paramétricos en Tcl con `draw` (`DRAWEXE`).
    * *Capítulo 5:* Discretización y Generación de Mallas de Elementos Finitos con `gmsh`.
    * *Capítulo 6:* Solucionador Numérico de Elementos Finitos CalculiX (`ccx`).
    * *Capítulo 7:* Batería de Comandos Especiales de Diagnóstico y Certificación (`test-*`).
    * *Capítulo 8:* Casos Prácticos Completos:
      * *Caso 1:* Auditar y recalcular modelos estructurales navegando transversalmente a `/structural_analysis`.
      * *Caso 2:* Importar decks `.inp` externos y resolverlos en la terminal.
      * *Caso 3:* Creación de carpetas de proyecto, ejecución y limpieza selectiva.
      * *Caso 4:* Flujo integral desde la terminal: Script TCL con `echo >` $\to$ Modelo STEP con `draw` $\to$ Mallado con `gmsh` $\to$ Resolución con `ccx`.
    * *Capítulo 9:* Matriz de Capacidades Reales, Límites Técnicos en Android y Buenas Prácticas.

---

#### 4. 🧪 Validación y Certificación Científica de Punta a Punta
* **Script de Certificación Automatizada (`validate_terminal_guide_end_to_end.py`):**
  * Batería automatizada en Python que ejecuta de forma real y certifica cada comando, fórmula analítica y caso práctico de la guía en el entorno local, logrando un **100% de éxito** en sus 4 niveles de verificación sin errores ni falsos positivos.
* **Suite de Pruebas Unitarias de Integración en Gradle (`TerminalGuideEndToEndTest.java`):**
  * 11 pruebas de integración JUnit ejecutadas con `./gradlew testDebugUnitTest`, validando comandos shell, scripts TCL, mallado Gmsh, resolución CalculiX CCX, interoperabilidad modular y protección de sandbox.
* **Concordancia Físico-Matemática Analítica:**
  * **Tracción Uniaxial en Cubo Sólido (Cubo $1\times 1\times 1\text{ mm}$, $E = 210\,000\text{ MPa}$, $\nu = 0.30$, $P = 400\text{ N}$):**
    * Desplazamiento axial analítico: $u_z = \frac{P L}{E A} = \frac{400}{210000 \cdot 1.0} = 0.001905\text{ mm}$.
    * Contracción lateral analítica de Poisson: $u_x = u_y = -\nu \frac{P}{E A} = -0.30 \cdot 0.001905 = -0.000571\text{ mm}$.
    * Magnitud resultante analítica: $\|\vec{\delta}\| = \sqrt{u_x^2 + u_y^2 + u_z^2} = 0.002069\text{ mm}$.
    * **Resultado CalculiX CCX FEA:** $u_z = 0.001905\text{ mm}$, $\|\vec{\delta}\| = 0.002069\text{ mm}$ (Error analítico: **$0.0000\%$**).
  * **Pórtico Plano 2D (Vigas $B31$, Carga Lateral $P_x = 10\text{ kN}$ a $h = 4\text{ m}$, Luz $L = 5\text{ m}$):**
    * Sumatoria de reacciones horizontales: $\sum R_x = -10.00\text{ kN}$ (Equilibrio estático exacto).
    * Par reactivo vertical: $R_{y1} = -8.00\text{ kN}$, $R_{y2} = +8.00\text{ kN}$ ($\sum M = 10 \cdot 4 - 8 \cdot 5 = 0$).

---

#### 5. 🏛️ Capacidades Nucleares Consolidadas de la Suite
* **Módulo de Análisis Estructural y Pórticos (FEA 2D / 3D):**
  * Dibujo táctil interactivo sobre rejilla milimétrica con snapping a 0.5 m.
  * Modelado de **Losas / Placas Flexibles (`S4R` / `S8R`)** y **Muros de Cortante (`CPS4`)** con formulación isoparamétrica Q4.
  * Cargas complejas en barras: múltiples cargas puntuales ($F_y, F_x$), momentos concentrados ($M_z$) y cargas distribuidas uniformes y trapezoidales en tramos parciales con integración de Simpson.
  * Articulaciones y semirrigidez en extremos de barras (*End Releases* y rigideces rotacionales $K_\theta$).
  * Diagramas de esfuerzos internos continuos con relleno cromático ($M_{33}, V_{22}, N$) y deformada elástica 3D interactiva en OpenGL ES.
  * 12 Presets estructurales precargados (vigas continuas, pórticos simples e industriales, cerchas Pratt y Warren, losas y muros).
* **Módulo de Sólidos 3D y Modelado CAD:**
  * Motor CAD OpenCASCADE para primitivas 3D, operaciones booleanas CSG (unión, corte, intersección), chaflanes y redondeos.
  * Importación universal para formatos STEP (`.step`, `.stp`), IGES (`.iges`, `.igs`), BREP (`.brep`) y GEO (`.geo`).
  * Mallador Gmsh con elementos tetraédricos de alta fidelidad lineal (`C3D4`) y cuadrática (`C3D10`).
  * Visor tridimensional SceneView con mapas de calor térmicos de tensiones de Von Mises.
* **Memorias Técnicas y Reportes en PDF de Ingeniería:**
  * Generación automatizada de reportes formales listos para revisión y firma pericial.
  * Chequeo normativo automatizado bajo normativas internacionales:
    * **AISC 360-22:** Interacción combinada flexo-compresora biaxial $P-M-M$ (ecuaciones H1-1a / H1-1b).
    * **ACI 318-19 / Eurocódigo 2:** Resistencia a corte y flexión en concreto armado.
    * **ASCE 7-22 / NSR-10:** Verificación de derivas sísmicas de entrepiso ($\Delta \le 1.0\%$) y flechas admisibles ($L/360, L/250$).

---

#### 6. ⚙️ Especificaciones de Compilación y Configuración SDK / NDK
* **Incremento de Versión:**
  * `versionCode`: **3**
  * `versionName`: **"0.3.0"**
* **Entorno de Compilación Automatizado:**
  * Android SDK configurado en `/tmp/android-sdk` (`platforms;android-37.0`, `build-tools;37.0.0`, `ndk;30.0.14904198`, `cmake;4.1.2`).
  * Ejecución de compilación limpia de APKs mediante Gradle 9.6.0 (`BUILD SUCCESSFUL`).

---

### 📦 Artefactos Compilados Generados:
* **Release Signed APK (Producción firmado):** `/tmp/calculoestructural_build/outputs/apk/release/app-release.apk` (185 MB)
* **Debug Testing APK (Pruebas):** `/tmp/calculoestructural_build/outputs/apk/debug/app-debug.apk` (205 MB)
