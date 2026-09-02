# Release Notes Archive

## Version Alfa 0.5.0 (v0.5.0)

- **Published At:** 2026-09-02T09:15:21Z
- **Tag:** `v0.5.0`
- **Pre-release:** True

### Release Details / Notes:

# Notas de Versión - Structural Analysis FEA 3D

## 📦 Structural Analysis FEA 3D v0.5.0 (Pre-Release)

### 🚀 Mejoras en Visor 3D, Costura de Superficies IGES y Validación Físico-Matemática Completa

#### 1. Estabilidad y Persistencia en Visor 3D
- **Cámara Independiente al Cambiar de Pestaña:**
  - Se desacopló la recarga del composable 3D en `SceneViewBridge`. Al alternar entre las pestañas de Parámetros, Visor 3D y Logs, la orientación y posición de la cámara del usuario se mantienen intactas sin recentrarse automáticamente.
  - La función de centrado queda asignada exclusivamente al botón *Centrar Vista* del visor 3D.
- **Retención y Reemplazo Seguro de Resultados 3D:**
  - Se eliminó el borrado preventivo de `job_solid.glb` durante la limpieza previa al cálculo.
  - El modelo 3D visualizado permanece en pantalla durante la ejecución y se reemplaza de forma atómica una vez que CalculiX y la conversión FRD→GLB finalizan con éxito, eliminando parpadeos, pantallas vacías o posibles cierres involuntarios de la app.

#### 2. Soporte y Costura Robusta de Archivos CAD IGES / IGS
- **Corrección en DRAWEXE (OpenCASCADE):**
  - Se actualizó el comando de serialización de sólidos cosidos en `GmshRunner` de `save` a `writebrep`.
  - Garantiza que modelos en formato `.igs` y `.iges` cosan de manera determinista sus caras NURBS en sólidos cerrados `.brep`, permitiendo que Gmsh genere la malla volumétrica tetraédrica sin errores de contorno abierto.

#### 3. Validación Físico-Matemática con CalculiX Real y Gmsh
- **29/29 Pruebas Superadas al 100%:**
  - Validación completa del pipeline CAD → Malla C3D4 → Deck CalculiX → Solver ccx → Resultados FRD en todos los formatos CAD soportados (`.step`, `.stp`, `.brep`, `.igs`, `.iges`, `.geo`) y geometrías de prueba (`cantilever_plate`, `perforated_block`, `structural_ibeam`, `flanged_connector`).
- **Verificación Analítica (Euler-Bernoulli):**
  - Validación en viga en voladizo ($100 \times 10 \times 10\text{ mm}$, $P = -100\text{ N}$, $E = 200\,000\text{ MPa}$): flecha analítica $\delta = 0.2000\text{ mm}$ vs FEA $\delta = 0.1762\text{ mm}$ (diferencia esperada de $11.88\% \le 15\%$ por rigidez de elementos C3D4).
- **Consistencia Cross-Formato:**
  - Desviaciones volumétricas inferiores al $0.7\%$ entre los diferentes formatos CAD para una misma geometría.

---

## Version Alfa 0.4.0 (v0.4.0)

- **Published At:** 2026-09-02T08:08:02Z
- **Tag:** `v0.4.0`
- **Pre-release:** True

### Release Details / Notes:

# Notas de Versión - Structural Analysis FEA 3D

## 📦 Structural Analysis FEA 3D v0.4.0 (Pre-Release)

### 🚀 Determinismo Absoluto, Aislamiento de Ejecución y Suite Completa de Elementos Finitos 3D

#### 1. Aislamiento Total del Espacio de Trabajo y Eliminación de Archivos Residuales
- **Limpieza Previa Obligatoria:**
  - Al presionar *Calcular* o importar archivos INP/CAD en el módulo de sólidos 3D, se eliminan automáticamente todos los archivos intermedios y de resultados de corridas anteriores (`job_solid_raw.inp`, `job_solid.inp`, `job_solid_clean.inp`, `nsets.inp`, `job_solid.frd`, `job_solid.dat`, `job_solid.sta`, `job_solid.cvg`, `job_solid.12d`, `job_solid.glb`).
  - Previene que ejecuciones canceladas, con errores de geometría o cambios de parámetros reutilicen por accidente archivos `.frd` o mallas cacheadas de corridas previas.
- **Limpieza en Gmsh:**
  - `GmshRunner` elimina el archivo de salida `.inp` antes de invocar el binario nativo de Gmsh, garantizando un resultado limpio desde cero.

#### 2. Detección Espacial de Apoyos y Cargas con Tolerancia Numérica Estricta
- **Eliminación de Alias Ambiguos (`SURFACE1`/`SURFACE2`):**
  - Se eliminó el mapeo ciego de superficies genéricas OpenCASCADE en `SolidInpAssembler`, evitando que en piezas CAD importadas se confunda la cara inferior ($Y=0$) con el empotramiento en voladizo ($X=0$).
- **Tolerancia Planar Submilimétrica:**
  - Se ajustó la captura de nodos en caras de contorno a una tolerancia planar estricta de $0.001\text{ mm}$ ($0.1\%$). Garantiza que únicamente se seleccionen los nodos superficiales del plano de apoyo y ningún nodo interno de volumen quede atrapado erróneamente en el conjunto `NFix`.

#### 3. Soporte Completo y Riguroso de las 8 Familias de Elementos Finitos Sólidos 3D
- **C3D4 (Tetraedro Lineal, 4 nodos):** Formulación de 1er orden con recomendación de refinamiento de malla para mitigar rigidez por *shear locking*.
- **C3D8 (Hexaedro Lineal, integración completa 2×2×2):** Integración exacta a cortante para tracción/compresión y sólidos con múltiples capas en espesor.
- **C3D8R (Hexaedro Lineal, integración reducida 1 punto):** Formulación económica con control automático de *hourglassing*.
- **C3D6 (Cuña/Prisma Lineal, 6 nodos):** Formulación prismática estructurada por capas para transiciones geométricas.
- **C3D10 (Tetraedro Cuadrático, 10 nodos, 4 puntos de Gauss):** Elemento de propósito general recomendado para geometrías libres no estructuradas; exactitud analítica del 99.9%.
- **C3D20 (Hexaedro Cuadrático, integración completa 3×3×3):** Malla cuadrática serendipity de 20 nodos para gradientes tensionales complejos.
- **C3D20R (Hexaedro Cuadrático, integración reducida 2×2×2):** Elemento óptimo y balanceado recomendado por CalculiX con puntos superconvergentes.
- **C3D15 (Cuña/Prisma Cuadrático, 15 nodos, 9 puntos de Gauss):** Formulación cuadrática para mallas extruidas estructuradas.

#### 4. Verificación y Determinismo Numérico al 100%
- **Batería de 200 Simulaciones:**
  - Evaluadas 5 repeticiones idénticas en los 5 niveles de densidad de malla con $0.000000\%$ de variabilidad numérica (100% determinista).
- **Suite de Tests Unitarios:**
  - 26/26 tests pasando con éxito en `SolidInpAssemblerTest`, incluyendo validaciones automatizadas para cada una de las 8 familias de elementos finitos y modelos STEP reales.

---

## Version Alfa 0.3.0 (v0.3.0)

- **Published At:** 2026-09-02T04:20:00Z
- **Tag:** `v0.3.0`
- **Pre-release:** True

### Release Details / Notes:

## 📦 Structural Analysis FEA 3D v0.3.0 (Pre-Release)

### 🚀 Novedades, Rebranding Oficial, Control de Hilos y Optimización de Solvers

#### 1. Renombrado Oficial e Integral de la Plataforma a "Structural Analysis FEA 3D"
- **Recursos y UI de la Aplicación:**
  - Actualización completa de `app_name`, títulos y mensajes de diálogos informativos, licencias y avisos flotantes (*toasts*).
  - Actualización del tema de la aplicación a `Theme.StructuralAnalysisFEA3D.NoActionBar` en `themes.xml` y `AndroidManifest.xml`.
- **Capa Nativa C++ / CMake y Carga JNI:**
  - Renombrado del proyecto CMake a `structural_analysis_fea_3d`, generando `libstructural_analysis_fea_3d.so`.
  - Sincronización transparente de las dependencias nativas en `NativeLoader` y `NativeFeaCore`.
- **Exportación y Memorias de Cálculo PDF:**
  - Directorio público de descargas configurado en `Downloads/Structural_Analysis_FEA_3D/`.
  - Actualización formal de membretes, metadatos y pies de página en `PDFReportGenerator` y `SolidPDFReportGenerator`.
- **Política de Privacidad y Documentación:**
  - Archivo oficial renombrado a `politica_privacidad_structural_analysis_fea_3d.html` y cargado dinámicamente desde el servidor.
  - Sincronización total de manuales técnicos, ficha de Google Play Store (`GOOGLE_PLAY_STORE_LISTING.md`) y `README.md`.

#### 2. Control de Concurrencia y Hilos en el Solucionador CalculiX (Secuencial vs Paralelo)
- **Soporte Dinámico de Hilos en `CalculixExecutor`:**
  - Se implementó la parametrización de hilos mediante inyección de variables de entorno OpenMP (`OMP_NUM_THREADS` y `CCX_NPROC_EQUATION_SOLVER`).
- **Prueba Base Secuencial (`test-calculix`):**
  - Configurada para ejecutarse estrictamente a **1 solo hilo** (`OMP_NUM_THREADS=1`), garantizando la validación determinista y secuencial en cualquier dispositivo independiente de su número de núcleos.
- **Prueba Paralela Multiprocesador (`test-calculix-parallel`):**
  - Utiliza el 100% de los núcleos disponibles de la CPU del dispositivo móvil con aceleración multihilo OpenMP y SPOOLES.
- **Módulos de Simulación 3D y Estructural:**
  - Todos los cálculos del módulo estructural y de sólidos continúan ejecutándose automáticamente con paralelismo total para máxima velocidad.

---

### 📦 Artefacto de Lanzamiento:
- **APK Release (Producción firmado):** `app-release.apk` (185 MB)

---

## Version Alfa 0.2.0 (v0.2.0)

- **Published At:** 2026-09-02T03:41:31Z
- **Tag:** `v0.2.0`
- **Pre-release:** True

### Release Details / Notes:

### 🚀 Novedades, Motores FEA, Visualización 2D/3D y Correcciones Físicas (Versión Alfa v0.2.0)

#### 1. Liberaciones Mecánicas en Extremos de Barras (Releases M11, M22, M33) y Semirrigidez Kθ
- **Condensación Estática Exacta:**
  - Implementación de liberaciones de grado de libertad por nodo (extremo inicial I y extremo final J) para flexión en el plano principal ({33}$), flexión secundaria ({22}$) y torsión ({11}$).
  - Soporte para **articulación pura** (\theta = 0$) y **conexiones semirrígidas** (\theta > 0\text{ kN}\cdot\text{m/rad}$), modificando la matriz de rigidez local mediante condensación estática de resortes rotacionales.
- **Diálogo de Propiedades y Glifos Gráficos:**
  - Checkboxes en el diálogo de asignación de barras para activar liberaciones de momento con campo condicional de rigidez rotacional.
  - Símbolos normalizados de articulación circular en el editor 2D y rombos dorados 3D en el visor OpenGL.

#### 2. Cargas Complejas en el Vano del Elemento (Puntuales, Momentos y Distribuidas Trapezoidales)
- **Cargas Puntuales Excéntricas y Momentos Concentrados en el Vano (/L \in [0, 1]$):**
  - Fuerzas transversales ($), fuerzas axiales ($) y momentos flectores concentrados ($) aplicados en cualquier posición relativa del miembro.
- **Cargas Distribuidas Variables y Parciales:**
  - Cargas trapezoidales con intensidad inicial $ (en {start}$) e intensidad final $ (en {end}$).
  - Integración numérica exacta de fuerzas de empotramiento perfecto (Fixed-End Forces) mediante regla compuesta de Simpson, transformadas automáticamente al vector global de cargas.

#### 3. Catálogo y Creación de Materiales Personalizados
- **Diálogo Interactivo (`dialog_custom_material.xml`):**
  - Permite al usuario definir materiales propios especificando: Módulo de Young $ (MPa), Coeficiente de Poisson $\nu$, Densidad $\rho$ ($\text{kg/m}^3$), Límite de fluencia $ (MPa) y Resistencia a compresión '_c$ (MPa).
  - Integración transparente en `MaterialDatabase` y resolución en el motor `FrameAnalysisEngine`.

#### 4. Renderizado Vectorial Dinámico en el Editor 2D y Visor 3D OpenGL
- **Editor 2D (`GridEditorView`):**
  - Dibujo de cargas puntuales transversales y axiales en la posición geométrica exacta del vano.
  - Arcos circulares con punta de flecha direccional para momentos concentrados $ (horario/antihorario) con insignias de valor.
  - Polígonos sombreados (`#33FF1744`) con peines de flechas para cargas distribuidas uniformes y trapezoidales.
- **Visor 3D OpenGL (`StructuralFragment`):**
  - Generación de líneas y vectores tridimensionales en los VBOs de OpenGL para cargas en barras y lazos de momentos concentrados.

#### 5. Acoplamiento Físico Riguroso Muro-Pórtico (Shear Wall CPS4)
- **Formulación Q4 Isoparamétrica Exacta:**
  - Matriz de rigidez de membrana \times 8$ integrada numéricamente con cuadratura \times 2$ de Gauss-Legendre.
  - Resolución del acoplamiento monolítico marco-muro: el muro de corte absorbe el **8.74\%* del cortante lateral (9.37\text{ kN}$ de 0\text{ kN}$), y las columnas confinantes absorben el **.26\%* (bash.63\text{ kN}$), con momentos basales residuales mínimos ($< 0.71\text{ kN}\cdot\text{m}$).
- **Corrección de Variables en Reportes PDF (`PDFReportGenerator`):**
  - La Sección 6.2 del reporte PDF ahora diferencia paneles de tensión plana ($\text{CPS4}/\text{CPE4}$) mostrando tensiones en el plano ($\sigma_x, \sigma_y, \tau_{xy}, V_{wall}$) en lugar de momentos de losa (, M_y$).

#### 6. Suite de Certificación FEM Multi-Solver (CalculiX & OpenSees)
- Validación automatizada cruzada en local con CalculiX `ccx 2.23` y `OpenSeesPy` certificando 12/12 presets estructurales y 100/100 tests unitarios con 100% de éxito.

---

## Structural Analysis FEA Advanced v0.1.0 (v0.1.0)

- **Published At:** 2026-08-28T20:59:22Z
- **Tag:** `v0.1.0`
- **Pre-release:** True

### Release Details / Notes:

# Notas de Versión - Structural Analysis FEA Advanced (Versión Alfa 0.1.0)

## 📦 Structural Analysis FEA Advanced v0.1.0 (Pre-Release)

### 🚀 Novedades, Mejoras de UI, Motores FEA y Correcciones de la Versión

#### 1. Navegación Global Multi-Módulo en la Terminal con Exportación Aislada
- **Sandbox Global de Archivos en la Terminal:**
  - `TerminalCommandExecutor` ahora permite navegación completa por todas las carpetas y archivos de modelos de la aplicación (`cd ..`, `cd /`, `cd /structural_analysis`, `cd /3d_solid_analysis`, `cd ~`, `cd /terminal`, `ls [path]`, `cat <archivo>`, `cp <origen> <destino>`, `mkdir`, `rm [-rf]`), facilitando inspeccionar, leer y transferir modelos entre módulos directamente desde la consola.
  - El directorio de inicio predeterminado se sitúa en `/terminal`, y el atajo `cd` o `cd ~` regresa instantáneamente al directorio base de la terminal.
  - Los directorios internos del sistema (`usr`, `bin`, `lib`, `include`, `share`, `cache`, etc.) se ocultan automáticamente de los listados en la raíz (`ls /`) para ofrecer una interfaz limpia y libre de ruido.
- **Exportación Aislada y Segura:**
  - La función de exportación total ("Export All") desde la terminal y desde el menú superior exporta **estrictamente** los archivos y carpetas contenidos en `files/terminal/`, sin mezclar proyectos de otros módulos (`structural_analysis`, `3d_solid_analysis`) ni incluir carpetas del sistema.

#### 2. Depuración y Estandarización de Comandos de Terminal
- **Estandarización de Limpieza de Pantalla:**
  - Se eliminó el comando `cls`, manteniendo exclusivamente el estándar POSIX `clear` tanto en ejecución como en el menú de ayuda (`help`).
- **Corrección Integral de `run-sim-test`:**
  - Corrección de la resolución de rutas de binarios para `gmsh` y `ccx` en `SimulationTestManager` y `TerminalFragment`. Ahora genera `cantilever.geo`, ejecuta Gmsh para crear `cantilever_raw.inp`, ensambla el modelo de elementos finitos y resuelve con CalculiX ccx de principio a fin, mostrando el resumen de desplazamientos y tensiones elásticas en la consola sin errores.

#### 3. Optimización de Flechas Vectoriales de Carga en 3D (OpenGL ES) y 2D
- **Visor 3D OpenGL ES (`StructuralFragment`):**
  - Reemplazo de la cabeza cónica multifilamento de 8 aletas por una **punta de flecha estándar y limpia de 2 alas planas ortonormalizadas** ($u, v \perp d$). La visualización de cargas en 3D es ahora estilizada, profesional y sin saturación geométrica.
- **Lienzo 2D y Diagramas (`GridEditorView` & `DiagramView`):**
  - Puntas de flecha sólidas y triangulares con unión limpia al fuste del vector.
  - Soporte completo para fuerzas fuera del plano ($F_z$) mediante simbología estándar de ingeniería (círculo con punto central para $+Z$, círculo con cruz para $-Z$) y badges dinámicos con la magnitud de carga en kN.

#### 4. Solución al Bloqueo Numérico de Cáscaras (S4R -> S8R) y Flexión de Losas
- **Diagnóstico y Eliminación de *Parasitic Shear Locking*:**
  - Los elementos de cáscara lineales `S4R` en CalculiX bajo mallas gruesas (como el preset de 4 cuadrantes con bordes apoyados) sufrían de bloqueo numérico por cortante parasitario, sobreestimando la rigidez por un factor de ~700x y subestimando la flecha vertical a $-0.0015\text{ mm}$.
- **Generación Automática de Cuadriláteros Cuadráticos `S8R` en `AnalysisModel.cpp`:**
  - El motor C++ nativo ahora actualiza y genera automáticamente elementos de cáscara cuadráticos de 8 nodos `S8R` con nodos intermedios de arista interpolados.
  - CalculiX `ccx` calcula ahora la flecha física real exacta:
    - **Losa cuadrada pura ($4\times 4\text{m}$, $t=15\text{cm}$, $E=25\text{GPa}$, $\nu=0.2$, $P=40\text{kN}$):** $U_z = -1.039\text{ mm}$ (Concordancia del **97.6%** con la solución elástica analítica de Navier/Timoshenko de $w_{\max} = 1.014\text{ mm}$).
    - **Losa con vigas de borde perimetrales ($200\times 300\text{mm}$):** $U_z = -0.807\text{ mm}$ (Físicamente exacto considerando la rigidez aportada por las 8 vigas).
  - Reporte de fuerzas internas en vigas de borde ($V_2 = 5.00\text{ kN}$, $M_1 = 5.00\text{ kN}\cdot\text{m}$) y momentos distribuidos de placa ($M_x = 2.50\text{ kN}\cdot\text{m/m}$, $M_y = 2.13\text{ kN}\cdot\text{m/m}$, $M_{xy} = 0.38\text{ kN}\cdot\text{m/m}$) plenamente consistentes con el equilibrio estático global.

#### 5. Solucionador FEA Directo para Losas y Muros de Cortante (CPS4)
- **Integración Multielemento en `FrameAnalysisEngine`:**
  - Formulación cuadrilateral de flexión de placas Mindlin-Reissner / Discrete Kirchhoff ($12 \times 12$) acoplada a vigas perimetrales, calculando deflexiones nodales y envolventes Wood-Armer.
  - Formulación Q4 de tensión plana ($8 \times 8$) para muros de cortante `CPS4` acoplados a marcos de confinamiento.
  - Corrección de restricción de rotación en apoyos articulados (*Pinned*) y móviles (*Roller*), liberando giros libres.
  - Cumplimiento estricto del equilibrio estático global ($\sum F + \sum R = 0.0$).

#### 6. Política de Privacidad en Línea y Permisos de Red
- **Actividad Responsiva `PrivacyPolicyActivity`:**
  - Integración de WebView con soporte de JavaScript, barra de progreso de carga y pantalla de reintento ante desconexión.
  - Archivo oficial HTML5 generado: `politica_privacidad_structural_analysis_fea_advanced.html` cargado desde `https://todoandroid.42web.io/politica_privacidad_structural_analysis_fea_advanced.html`.
  - Acceso directo a la Política de Privacidad desde el Menú de Opciones Superior y el Menú Lateral (*Navigation Drawer*).
  - Permisos `INTERNET` y `ACCESS_NETWORK_STATE` declarados en `AndroidManifest.xml`.
  - Ficha de metadatos de Google Play Store creada en `GOOGLE_PLAY_STORE_LISTING.md`.

#### 7. Herramientas de Navegación e Inspección en el Editor 2D
- **✋ Pan View:** Desplazamiento libre de la vista y cámara 2D con un solo dedo sin alterar la geometría de la estructura.
- **✥ Move Nodes:** Selección y arrastre de nodos geométricos con snapping magnético a $0.5\text{ m}$.
- **🔍 Select / Info:** Inspección técnica sin edición. Al tocar cualquier componente (nodo, viga, columna, panel o apoyo), se resalta y se despliegan todas sus propiedades en el banner superior y en el HUD del canvas.
- **Banner Flotante de Propiedades (`tvComponentInfo`):** Muestra coordenadas, tipo de apoyo, cargas aplicadas ($F_x, F_y, F_z$), longitud, perfil transversal asignado, material y esfuerzos máximos.

#### 8. Base de Datos Normalizada de Materiales y Secciones (JSON)
- Inclusión de los catálogos `materials.json` y `sections.json` con propiedades mecánicas ($E, \nu, \rho, f_y$) y seccionales ($A, I_z, I_y, S_z, Z_z$).
- Sincronización automática con los selectores de la interfaz de usuario y las memorias técnicas en PDF.

#### 9. Sincronización Dinámica del Veredicto Técnico en Memorias PDF
- **Evaluación Integral en Tiempo Real:**
  - Las secciones 9.1 y 9.2 del reporte PDF generado por `PDFReportGenerator.java` ahora leen y evalúan dinámicamente los estados reales de deflexión máxima ($L/360$ o $25\text{ mm}$), derivas laterales de entrepiso (NSR-10 $\le 1.0\%$, ASCE 7-22 $\le 1.5\%$) y sobreesfuerzos en miembros estructurales ($D/C > 1.0$).
  - Si un modelo supera las tolerancias de servicio o derivas admisibles, el veredicto formal emite `STRUCTURAL VERDICT: CONDITIONAL / SERVICEABILITY REVIEW REQUIRED (VERIFY)` con advertencias técnicas detalladas y sellos en color ámbar/naranja, evitando falsas aprobaciones incondicionales.
  - Subsección 6.2 en PDF con envolventes de placas/paneles ($M_x, M_y, M_{xy}, V_{max}$).

#### 10. Rediseño Compacto y Separación de Gestos en Sólidos 3D
- **Interfaz Compacta en Pantalla Única:**
  - Eliminación de `NestedScrollView` en la pestaña de parámetros, manteniendo el 100% de controles CAD (primitivas, redondeos, extrusión, uniones/cortes booleanos).
  - Barra inferior independiente en el visor SceneView protegida contra superposición con los insets de navegación del sistema.

#### 11. Licenciamiento GPLv3 y Firma de Producción
- **Cumplimiento Copyleft Oficial:**
  - Adición formal del archivo `LICENSE` (GNU GPL v3.0) en la raíz del repositorio.
  - Automatización de firma de producción (`keystore.properties`) para compilación de APKs y App Bundles de lanzamiento.

---

### 📦 Artefactos de Compilación Disponibles:
- **APK Release (Producción firmado):** `/tmp/calculoestructural_build/outputs/apk/release/app-release.apk`
- **App Bundle Release (Google Play):** `/tmp/calculoestructural_build/outputs/bundle/release/app-release.aab`
- **APK Debug:** `/tmp/calculoestructural_build/outputs/apk/debug/app-debug.apk`

---

