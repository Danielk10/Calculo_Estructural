# Notas de Versión - Structural Analysis FEA 3D

## 📦 Structural Analysis FEA 3D v0.2.0 (Pre-Release)

### 🚀 Novedades, Motores FEA, Visualización 2D/3D y Correcciones Físicas

#### 1. Liberaciones Mecánicas en Extremos de Barras (Releases M11, M22, M33) y Semirrigidez Kθ
- **Condensación Estática Exacta:**
  - Implementación de liberaciones de grado de libertad por nodo (extremo inicial I y extremo final J) para flexión en el plano principal ($M_{33}$), flexión secundaria ($M_{22}$) y torsión ($M_{11}$).
  - Soporte para **articulación pura** ($K_\theta = 0$) y **conexiones semirrígidas** ($K_\theta > 0\text{ kN}\cdot\text{m/rad}$), modificando la matriz de rigidez local mediante condensación estática de resortes rotacionales.
- **Diálogo de Propiedades y Glifos Gráficos:**
  - Checkboxes en el diálogo de asignación de barras para activar liberaciones de momento con campo condicional de rigidez rotacional.
  - Símbolos normalizados de articulación circular en el editor 2D y rombos dorados 3D en el visor OpenGL.

#### 2. Cargas Complejas en el Vano del Elemento (Puntuales, Momentos y Distribuidas Trapezoidales)
- **Cargas Puntuales Excéntricas y Momentos Concentrados en el Vano ($x/L \in [0, 1]$):**
  - Fuerzas transversales ($F_y$), fuerzas axiales ($F_x$) y momentos flectores concentrados ($M_z$) aplicados en cualquier posición relativa del miembro.
- **Cargas Distribuidas Variables y Parciales:**
  - Cargas trapezoidales con intensidad inicial $w_1$ (en $x_{start}$) e intensidad final $w_2$ (en $x_{end}$).
  - Integración numérica exacta de fuerzas de empotramiento perfecto (Fixed-End Forces) mediante regla compuesta de Simpson, transformadas automáticamente al vector global de cargas.

#### 3. Catálogo y Creación de Materiales Personalizados
- **Diálogo Interactivo `dialog_custom_material.xml`:**
  - Permite al usuario definir materiales propios especificando: Módulo de Young $E$ (MPa), Coeficiente de Poisson $\nu$, Densidad $\rho$ ($\text{kg/m}^3$), Límite de fluencia $F_y$ (MPa) y Resistencia a compresión $f'_c$ (MPa).
  - Integración transparente en `MaterialDatabase` y resolución en el motor `FrameAnalysisEngine`.

#### 4. Renderizado Vectorial Dinámico en el Editor 2D y Visor 3D OpenGL
- **Editor 2D (`GridEditorView`):**
  - Dibujo de cargas puntuales transversales y axiales en la posición geométrica exacta del vano.
  - Arcos circulares con punta de flecha direccional para momentos concentrados $M_z$ (horario/antihorario) con insignias de valor.
  - Polígonos sombreados (`#33FF1744`) con peines de flechas para cargas distribuidas uniformes y trapezoidales.
- **Visor 3D OpenGL (`StructuralFragment`):**
  - Generación de líneas y vectores tridimensionales en los VBOs de OpenGL para cargas en barras y lazos de momentos concentrados.

#### 5. Acoplamiento Físico Riguroso Muro-Pórtico (Shear Wall CPS4)
- **Formulación Q4 Isoparamétrica Exacta:**
  - Matriz de rigidez de membrana $8\times 8$ integrada numéricamente con cuadratura $2\times 2$ de Gauss-Legendre.
  - Resolución del acoplamiento monolítico marco-muro: el muro de corte absorbe el **$98.74\%$** del cortante lateral ($49.37\text{ kN}$ de $50\text{ kN}$), y las columnas confinantes absorben el **$1.26\%$** ($0.63\text{ kN}$), con momentos basales residuales mínimos ($< 0.71\text{ kN}\cdot\text{m}$).
- **Corrección de Variables en Reportes PDF (`PDFReportGenerator`):**
  - La Sección 6.2 del reporte PDF ahora diferencia paneles de tensión plana ($\text{CPS4}/\text{CPE4}$) mostrando tensiones en el plano ($\sigma_x, \sigma_y, \tau_{xy}, V_{wall}$) en lugar de momentos de losa ($M_x, M_y$).

#### 6. Suite de Certificación FEM Multi-Solver (CalculiX & OpenSees)
- Validación automatizada cruzada en local con CalculiX `ccx 2.23` y `OpenSeesPy` certificando 12/12 presets estructurales y 100/100 tests unitarios con 100% de éxito.

---

## 📦 Structural Analysis FEA 3D v0.1.0 (Pre-Release)

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
  - Los comandos `clear` y `cls` ahora limpian completamente la pantalla en lugar de únicamente el búfer de texto, restableciendo el banner inicial de bienvenida.
- **Comando `history` y Atajos de Teclado:**
  - Incorporación del comando `history` en el intérprete y soporte en UI de botones dedicados (flechas arriba/abajo) para navegar por el historial de comandos ejecutados en tiempo real.
- **Comandos de Prueba de Solvers Especiales:**
  - Incorporación y prueba automatizada de comandos directos: `test-gmsh`, `test-draw`, `test-calculix`, `test-calculix-parallel`, `test-frame`, `test-frd-parser`, `test-dat-parser`, `test-coordinate-fallback`, `test-step-solve`, `test-bracket-solve`, `test-cad-solve` y `run-sim-test`.

#### 3. Soporte Integral de Formatos CAD y Malla Volumétrica
- **Importación de Mallas INP:**
  - Detección inteligente del tipo de archivo INP (`*ELEMENT, TYPE=C3D*` vs `*ELEMENT, TYPE=B3* / T3*`):
    - Mallas de sólidos se cargan directamente en el Módulo de Sólidos 3D.
    - Mallas de pórticos/barras se cargan directamente en el Módulo Estructural con reconstrucción completa de nodos y elementos.
    - En el módulo Terminal, los archivos INP se importan en el espacio de trabajo local para cálculo directo.
- **Importación y Modelado CAD (STEP / IGES / BREP / GEO):**
  - Soporte para importación de geometrías externas e integración de primitivas paramétricas 3D (Cubo, Cilindro, Esfera) con operaciones de Redondeo (Fillet), Chaflán (Chamfer), Extrusión y Operaciones Booleanas (Unión, Corte, Intersección) impulsadas por OpenCASCADE Technology (OCCT 8.0.0.p1).

#### 4. Motor de Renderizado 3D de Alta Fidelidad
- **Migración a SceneView v4.18.0 / Google Filament:**
  - Renderizado PBR con iluminación física, soporte para sombreadores custom unlit con Vertex Colors, visualización de mapas de color de tensiones de Von Mises y deformadas elásticas en tiempo real.
  - Generador y exportador nativo de archivos **GLB (glTF Binary)** para visualización tridimensional optimizada en dispositivos móviles.

#### 5. Módulo Estructural y Memorias de Cálculo PDF
- **Generación de Reportes Técnicos Formales:**
  - Exportación automática a la carpeta pública de descargas de reportes en PDF con formato profesional (portada ejecutiva, parámetros de cálculo, diagramas de esfuerzos de vigas Timoshenko, desplazamientos nodales y verificación de estados límite).

#### 6. Conformidad Legal, Políticas y Documentación
- **Cumplimiento de Licencias Copyleft (GPL v3.0 / LGPL v2.1):**
  - Declaración formal en `README.md` del cumplimiento de la licencia GPL v3.0 para CalculiX 2.23 y Gmsh 5.0.0, permitiendo monetización comercial con total apertura y disponibilidad del código fuente.
- **Pantalla de Política de Privacidad (`PrivacyPolicyActivity`):**
  - Actividad nativa responsiva con WebView para la visualización de la Política de Privacidad de la aplicación conforme a las directrices de Google Play Console.
  - Archivo oficial HTML5 generado: `politica_privacidad_structural_analysis_fea_3d.html` cargado desde `https://todoandroid.42web.io/politica_privacidad_structural_analysis_fea_3d.html`.
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
  - Archivo oficial HTML5 generado: `politica_privacidad_structural_analysis_fea_3d.html` cargado desde `https://todoandroid.42web.io/politica_privacidad_structural_analysis_fea_3d.html`.
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
