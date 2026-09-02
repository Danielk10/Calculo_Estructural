# Notas de Versión - Structural Analysis FEA 3D

## 📦 Structural Analysis FEA 3D v0.1.0 (Pre-Release)

### 🚀 Primera Versión Oficial - Plataforma Integral de Elementos Finitos 3D y Marcos Estructurales para Android

---

### 1. ⚙️ Núcleo Científico y Motores Nativos de Simulación FEA
- **Solucionador CalculiX (CCX 2.23):** Integración nativa del motor de cálculo de elementos finitos con soporte multihilo de álgebra lineal **SPOOLES 2.2** y extracción modal / frecuencias naturales mediante **ARPACK-NG**.
- **Generador de Mallas Gmsh (v5.0.0):** Generación automática y refinamiento de mallas tetraédricas de 1.er y 2.º orden (C3D4, C3D10) y soporte para transiciones hexaédricas.
- **Modelador CAD OpenCASCADE (OCCT 8.0.0.p1):** Creación directa de geometrías primitivas (Cubo, Cilindro, Esfera), operaciones avanzadas (Redondeos, Chaflanes, Extrusión) y operaciones booleanas completas (Unión, Corte e Intersección).
- **Ingesta de Formatos CAD Estándar:** Compatibilidad total para importar y analizar modelos en formatos STEP (`.step`, `.stp`), IGES (`.iges`, `.igs`), BREP (`.brep`) y GEO (`.geo`).

---

### 2. 🏗️ Módulo de Análisis Estructural y Pórticos 2D/3D
- **Editor Topológico en Cuadrícula 2D:** Dibujo interactivo con snapping magnético (0.5 m), asignación de apoyos en nodos (Empotrado, Articulado, Rodillo), cargas puntuales con momentos ($F_x, F_y, M_z$), liberaciones de momentos en extremos y cargas distribuidas trapezoidales/parciales en vano ($w_1, w_2$).
- **Diagramas de Esfuerzos Internos:** Cálculo en tiempo real y visualización gráfica interactiva de Momento Flector ($M_{33}$), Fuerza Cortante ($V_{22}$), Fuerza Axial ($N$) y Deformada ($U$).
- **Plantillas y Benchmarks Predefinidos:** Pórticos industriales, vigas continuas, armaduras Pratt y Warren, losas de hormigón (S4R) y muros de cortante confinados (CPS4).

---

### 3. 🎨 Visor 3D Interactivo y Memorias Técnicas en PDF
- **Visor SceneView / Filament:** Renderizado de esfuerzos de Von Mises con mapa térmico continuo de colores, deformación escalable y control fluido de cámara 3D.
- **Memorias de Cálculo en PDF Automatizadas:** Generación de informes técnicos de ingeniería con evaluación normativa (ACI 318-19, AISC 360-16, ASCE 7-22), tablas de rigidez, reacciones en apoyos y veredicto estructural automático.
- **Diálogos Interactivos de Acerca de y Licencias:** Enlaces directos y clickeables a la documentación de todos los componentes de código abierto integrados y al repositorio del proyecto.

---

### 📦 Artefactos Disponibles para esta Versión:
- **APK Release (Producción firmado):** `/tmp/calculoestructural_build/outputs/apk/release/app-release.apk`
- **APK Debug:** `/tmp/calculoestructural_build/outputs/apk/debug/app-debug.apk`
