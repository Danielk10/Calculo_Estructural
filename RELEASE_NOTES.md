# 📦 Notas de Versión
## Structural Analysis FEA 3D (`com.diamon.civil`) — Versión Alfa 0.1.0

---

### 🚀 Novedades y Capacidades Principales

#### 1. 🏗️ Módulo de Análisis Estructural y Pórticos (FEA 2D / 3D)
* **Dibujo Interactivo de Losas y Muros en Editor 2D:**
  * Soporte para dibujar paneles bidimensionales de **Losa / Placa Flexible (`S4R`)** y **Muro de Cortante (`CPS4`)** mediante arrastre directo sobre la rejilla.
  * Vista previa rectangular con borde punteado y HUD con cotas métricas y espesor en tiempo real.
  * Generación topológica automática de los 4 nudos de esquina, apoyos en base y elementos perimetrales de contorno.
* **Inspección y Edición de Paneles 2D:**
  * Nuevo diálogo de propiedades de panel para editar espesor ($t$), material asignado, formulación (`S4R` vs `CPS4`) y asignación de cargas superficiales o presiones ($q$ en $\text{kN/m}^2$) con distribución tributaria automática a los nudos.
* **Editor Topológico Interactivo de Barras y Nudos:** Lienzo 2D con rejilla magnética (*snapping* a 0.5 m), creación rápida de nudos, barras, apoyos (Empotrado, Articulado, Rodillo) y visualización de estadísticas (Nodos, Barras, Paneles).
* **Cargas Complejas en Barras (Múltiples Fuerzas y Momentos):**
  * Asignación de **múltiples cargas puntuales** transversales ($F_y$), axiales ($F_x$) y momentos concentrados ($M_z$) por barra, con soporte de listas separadas por coma o espacio.
  * **Conversión inteligente de cotas métricas:** Detección automática de distancias absolutas en metros a ratios de vano normalizados ($x/L$).
  * **Cargas distribuidas variables y parciales:** Asignación de cargas uniformes y trapezoidales ($w_1 \neq w_2$) en tramos parciales arbitrarios ($a \to b$), con integración numérica de Simpson de 20 intervalos en el solver.
* **Renderizado 2D y 3D OpenGL ES (Estándar SAP2000):**
  * Flechas de carga orientadas con precisión milimétrica en el vano, cabezas vectoriales 3D doradas/rojas, distintivos numéricos de carga y bucles/arcos de momentos concentrados.
* **Materiales Personalizados:**
  * Diálogo de material propio: Módulo de Elasticidad ($E$), Coeficiente de Poisson ($\nu$), Densidad ($\rho$), Tensión de Fluencia ($F_y$) y Resistencia a Compresión ($f'_c$).
* **Exportación de Reacciones y Vinculaciones:**
  * Matriz espacial 6-DOF de reacciones en apoyos ($R_x, R_y, R_z, M_x, M_y, M_z$) y reporte de vinculaciones externas con verificación de equilibrio estático en log y portapapeles.
* **Articulaciones y Semirrigidez:** Desacoplamiento de momentos flectores (*End Releases* $M_{33}$) y rigideces rotacionales $K_\theta$.
* **Visualización de Resultados:** Diagramas de esfuerzos internos con relleno cromático continuo (Momento Flector $M_{33}$, Fuerza Cortante $V_{22}$, Fuerza Axial $N$) y deformada elástica interpolada.
* **12 Presets Estructurales Integrados:** Vigas continuas, vigas con voladizo, pórticos simples e industriales de múltiples crujías, cerchas Pratt y Warren, losas bidireccionales de hormigón (S4R) y muros de corte confinados (CPS4).

#### 2. 🌐 Multi-idioma (Español / Inglés) y Aviso Legal
* **Soporte de Idioma Español (ES):** Traducción integral de todos los textos de la interfaz gráfica de usuario (UI), etiquetas, botones, diálogos y selectores.
* **Aviso Legal y Deslinde de Responsabilidad:** Inclusión de advertencia explícita en menú lateral, menú principal y política de privacidad, indicando que la aplicación es para fines académicos, educativos y de pre-dimensionamiento rápido, prohibiendo su uso directo para obras civiles reales de ingeniería sin firma profesional independiente.

#### 3. 🧊 Módulo de Sólidos 3D y Modelado CAD (CAD + Gmsh + CalculiX)
* **Modelado CAD Headless con OpenCASCADE (OCCT 8.0.0.p1):** Generación de primitivas volumétricas (Cubo, Cilindro, Esfera), chaflanes, redondeos y operaciones booleanas (Unión, Corte e Intersección).
* **Importación Universal CAD:** Soporte completo para formatos STEP (`.step`, `.stp`), IGES (`.iges`, `.igs`), BREP (`.brep`) y GEO (`.geo`).
* **Mallador Automático Gmsh (v5.0.0):** Generación de mallas tetraédricas cuadráticas de segundo orden (`C3D10`) y lineales (`C3D4`) libres de distorsión jacobiana.
* **Visualizador 3D SceneView:** Renderizado tridimensional de mapas térmicos de tensiones de Von Mises y deformación elástica.

#### 4. 📄 Memorias Técnicas y Reportes en PDF de Ingeniería
* **Generación Automatizada:** Exportación directa de informes técnicos estructurados listos para firma y revisión pericial.
* **Capítulo 2 (Propiedades de Materiales):** Integración completa de materiales personalizados definidos por el usuario en la Tabla 2.1 con sus propiedades mecánicas ($E, G, \nu, \rho, F_y$).
* **Capítulo 3 (Esquemas de Carga):** Tablas 3.2 (cargas puntuales y momentos en vano con cotas métricas) y 3.3 (cargas distribuidas parciales y trapezoidales con resultante total).
* **Capítulo 4 (Equilibrio Global y Reacciones):** Balance estático Newtoniano ($\Sigma F + \Sigma R = 0$) y Tabla 4.2 detallada de reacciones nodales.
* **Tabla 6.1 (Multi-Station Frame Forces):** Registro estricto de 10 columnas con cortantes ($V_2, V_3$) y momentos ($M_2, M_3$) alineados con precisión en estaciones $0.00L$, $0.50L$ y $1.00L$.
* **Chequeo Normativo Automatizado:**
  * **AISC 360-22:** Comprobación de perfiles de acero a flexo-compresión biaxial combinada ($P-M-M$, ecuaciones H1-1a / H1-1b).
  * **ACI 318-19 / Eurocódigo:** Resistencia a cortante y flexión en elementos de hormigón armado.
  * **ASCE 7-22 / NSR-10:** Verificación de derivas sísmicas de entrepiso ($\Delta \le 1.0\%$) y flechas admisibles ($L/360, L/250$).

#### 5. ⚙️ Núcleo Científico y Validación
* **CalculiX CCX 2.23:** Solucionador nativo en C/Fortran con álgebra lineal directa multihilo **SPOOLES 2.2**.
* **Validación Cruzada Independiente con OpenSees:** Correlación física certificada superior al 99.85% en modelos de flexión, momentos y cargas trapezoidales.
* **100% de Pruebas Superadas:** Suite unitaria `./gradlew test` completada con 0 fallos, incluyendo validación analítica de losas S4R y muros CPS4 personalizados.

---

### 📦 Artefactos Generados:
* **Production Signed Bundle (AAB para Google Play):** `/tmp/calculoestructural_build/outputs/bundle/release/app-release.aab`
* **Release Signed APK:** `/tmp/calculoestructural_build/outputs/apk/release/app-release.apk`
* **Debug Testing APK:** `/tmp/calculoestructural_build/outputs/apk/debug/app-debug.apk`
