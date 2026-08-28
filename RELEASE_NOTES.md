# Notas de Versión - Versión Alfa 0.1.0

## 📦 Calculo Estructural v0.1.0 (Pre-Release)

### 🚀 Novedades, Mejoras de UI y Correcciones de la Versión

#### 1. Sincronización Dinámica del Veredicto Técnico en Memorias PDF
- **Evaluación Integral en Tiempo Real:**
  - Las secciones 9.1 y 9.2 del reporte PDF generado por `PDFReportGenerator.java` ahora leen y evalúan dinámicamente los estados reales de deflexión máxima ($L/360$ o $25\text{ mm}$), derivas laterales de entrepiso (NSR-10 $\le 1.0\%$, ASCE 7-22 $\le 1.5\%$) y sobreesfuerzos en miembros estructurales ($D/C > 1.0$).
  - Si un modelo supera las tolerancias de servicio o derivas admisibles, el veredicto formal emite `STRUCTURAL VERDICT: CONDITIONAL / SERVICEABILITY REVIEW REQUIRED (VERIFY)` con advertencias técnicas detalladas y sellos en color ámbar/naranja, evitando falsas aprobaciones incondicionales.

#### 2. Aislamiento y Exportación Limpia de la Terminal
- **Directorio de Trabajo Dedicado (`terminal/`):**
  - Se aisló por completo el espacio de ejecución y almacenamiento de la consola interactiva a la subcarpeta `terminal/` dentro del almacenamiento interno de la app.
  - La función de exportación de la terminal ahora extrae de forma limpia y exclusiva los archivos generados por los comandos de la consola sin incluir carpetas de otros proyectos (`structural_analysis`, `3d_solid_analysis`).

#### 3. Sanitización de Logs Nativos y Comandos de la Terminal
- **Salida de Consola Limpia:**
  - Implementación de `sanitizeBinaryOutput()` en `CalculixExecutor.java` para reemplazar de forma automática rutas internas largas de Android (como `/data/app/.../libgmsh.so` o directorios de assets) por nombres ejecutables nativos (`gmsh`, `ccx`, `DRAWEXE`).
  - Limpieza del menú de ayuda `help` en `ModuleLogger.java`, eliminando etiquetas dobles redundantes en comandos (`test-draw`).

#### 4. Corrección de Spinners y Tipografía en el Módulo Estructural
- **Eliminación de Texto Cortado:**
  - Creación de `spinner_compact_bg.xml` e `ic_arrow_down_spinner.xml` con bordes suaves de 1dp, radio de 6dp y flecha desplegable alineada a la derecha sin reducir el espacio útil vertical de texto.
  - Actualización de `item_spinner_compact.xml` e `item_spinner_dropdown_compact.xml` con centrado vertical absoluto (`gravity="center_vertical"`), `match_parent` de altura y padding lateral seguro (`paddingEnd="22dp"`).

#### 5. Rediseño Compacto del Módulo de Sólidos 3D (Sin Scroll)
- **Interfaz Compacta en Pantalla Única:**
  - Eliminación de `NestedScrollView` en la pestaña de parámetros, adaptando una arquitectura visual compacta basada en el módulo estructural.
  - Preservación del 100% de los controles: selector de modelo CAD activo, eliminación, benchmark, creación de primitivas (Box, Cylinder, Sphere), operaciones BRep (Fillet, Chamfer, Extrude) y booleanas (Union, Cut, Intersect).

#### 6. Visor 3D SceneView con Controles Inferiores Protegidos
- **Separación de Gestos:**
  - Reubicación de los botones de reinicio de cámara y recarga de malla en una barra inferior independiente (`#161E2E`), protegida contra superposición con los insets de navegación del sistema.

#### 7. Licenciamiento GPLv3 y Firma para Producción
- **Cumplimiento Copyleft Oficial:**
  - Adición formal del archivo `LICENSE` (GNU GPL v3.0) en la raíz del repositorio.
  - Automatización de firma de producción (`keystore.properties`) para compilación de APKs y App Bundles de lanzamiento.

---

### 📦 Artefactos de Compilación Disponibles:
- **APK Release (Producción firmado):** `app-release.apk` (185 MB)
- **App Bundle Release (Google Play):** `app-release.aab` (184 MB)
- **APK Debug:** `app-debug.apk` (205 MB)
