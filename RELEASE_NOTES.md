# Notas de Versión - Versión Alfa 0.2.0

## 📦 Calculo Estructural v0.2.0 (Pre-Release)

### 🚀 Novedades, Mejoras de UI y Correcciones de la Versión

#### 1. Corrección de Spinners y Tipografía en el Módulo de Cálculo Estructural
- **Eliminación de Texto Cortado y Apretado:**
  - Creación de `spinner_compact_bg.xml` e `ic_arrow_down_spinner.xml` con bordes suaves de 1dp, radio de 6dp y flecha desplegable alineada a la derecha sin reducir el espacio útil vertical de texto.
  - Actualización de `item_spinner_compact.xml` e `item_spinner_dropdown_compact.xml` con centrado vertical absoluto (`gravity="center_vertical"`), `match_parent` de altura y padding lateral seguro (`paddingEnd="22dp"`).
  - Aplicación directa en todos los selectores de tipo de estructura, formulación de elementos, presets, materiales y secciones transversales, así como en los diálogos de asignación de cargas y propiedades de barras.

#### 2. Rediseño Compacto del Módulo de Sólidos 3D (Sin Scroll)
- **Interfaz Compacta en una Sola Pantalla:**
  - Eliminación de `NestedScrollView` en la pestaña de parámetros, adaptando una arquitectura visual compacta basada en el módulo estructural.
  - Preservación del 100% de los controles: selector de modelo CAD activo, eliminación, benchmark, creación de primitivas (Box, Cylinder, Sphere), operaciones BRep (Fillet, Chamfer, Extrude) y booleanas (Union, Cut, Intersect).
  - Reubicación de la barra de progreso (`pbSolid`) inmediatamente arriba del botón de cálculo para ofrecer una indicación de estado inmediata y clara.

#### 3. Visor 3D SceneView con Controles Inferiores Protegidos
- **Prevención de Conflictos de Gestos y Botones Virtuales:**
  - Reubicación de los botones de reinicio de cámara y recarga de malla en una barra inferior independiente (`#161E2E`).
  - Separación física del lienzo táctil de Filament / SceneView y protección con los insets de navegación del sistema.

#### 4. Verificación y Sincronización de Versiones de Motores Científicos
- **Gmsh 5.0.0 (`Gmsh 5.0.0-git-e5f5181`):**
  - Actualización formal de la versión en metadatos de memorias de cálculo PDF (`SolidPDFReportGenerator.java`), cadenas de licencia (`strings.xml`) y documentación general (`README.md`).
- **OpenCASCADE Technology (OCCT 8.0.0.p1) & Enlaces Oficiales:**
  - Verificación de la versión en cabeceras C++ (`Standard_Version.hxx`).
  - Enlaces oficiales e interactivos (`LinkMovementMethod`) a los repositorios y sitios web de CalculiX, Gmsh, OpenCASCADE, SPOOLES, ARPACK, OpenBLAS, SceneView, iText 7 y GCC Fortran Runtime en el diálogo "About / Licenses".

#### 5. Compilación y Validación de Producción
- **Compilación Exitosa:** Compilación limpia del APK en `/tmp/calculoestructural_build/outputs/apk/release/app-release.apk` y `/tmp/calculoestructural_build/outputs/apk/debug/app-debug.apk`.
