# Notas de Versión - Structural Analysis FEA Advanced (Versión Alfa 0.1.0)

## 📦 Structural Analysis FEA Advanced v0.1.0 (Pre-Release)

### 🚀 Novedades, Mejoras de UI, Motores FEA y Correcciones de la Versión

#### 1. Mejora Integral de Flechas Vectoriales de Fuerza (3D OpenGL ES y 2D)
- **Visor 3D OpenGL ES:**
  - Generación de conos volumétricos de 8 aletas con base circular 3D completa y base ortonormalizada ($u, v \perp d$), garantizando visualización tridimensional nítida y simétrica de vectores de carga aplicados en cualquier dirección espacial ($F_x, F_y, F_z$).
- **Lienzo 2D y Diagramas (`GridEditorView` & `DiagramView`):**
  - Puntas de flecha sólidas y triangulares con unión limpia al fuste del vector.
  - Soporte completo para fuerzas fuera del plano ($F_z$) mediante simbología estándar de ingeniería (círculo con punto central para $+Z$, círculo con cruz para $-Z$) y badges dinámicos con la magnitud de carga en kN.

#### 2. Solucionador FEA Directo para Losas (S4R) y Muros de Cortante (CPS4)
- **Integración Multielemento en `FrameAnalysisEngine`:**
  - Implementación de formulación cuadrilateral de flexión de placas Mindlin-Reissner / Discrete Kirchhoff ($12 \times 12$) acoplada a vigas perimetrales, calculando deflexiones nodales exactas ($U_z \approx -1.08\text{ mm}$ en nodo libre para losa de $4\times 4\text{m}$ con 40 kN) y envolventes Wood-Armer ($M_x, M_y, M_{xy}, V_{max}$).
  - Implementación de formulación Q4 de tensión plana ($8 \times 8$) para muros de cortante `CPS4` acoplados a marcos de confinamiento.
  - Corrección de restricción de rotación en apoyos articulados (*Pinned*) y móviles (*Roller*), liberando giros libres.
  - Cumplimiento estricto del equilibrio estático global ($\sum F + \sum R = 0.0$).

#### 3. Política de Privacidad en Línea y Permisos de Red
- **Actividad Responsiva `PrivacyPolicyActivity`:**
  - Integración de WebView con soporte de JavaScript, barra de progreso de carga y pantalla de reintento ante desconexión.
  - Archivo oficial HTML5 generado: `politica_privacidad_structural_analysis_fea_advanced.html` cargado desde `https://todoandroid.42web.io/`.
  - Acceso directo a la Política de Privacidad desde el Menú de Opciones Superior y el Menú Lateral (*Navigation Drawer*).
  - Permisos `INTERNET` y `ACCESS_NETWORK_STATE` declarados en `AndroidManifest.xml`.
  - Ficha de metadatos de Google Play Store creada en `GOOGLE_PLAY_STORE_LISTING.md`.

#### 4. Nuevas Herramientas de Navegación e Inspección en el Editor 2D
- **✋ Pan View:** Desplazamiento libre de la vista y cámara 2D con un solo dedo sin alterar la geometría de la estructura.
- **✥ Move Nodes:** Selección y arrastre de nodos geométricos con snapping magnético a $0.5\text{ m}$.
- **🔍 Select / Info:** Inspección técnica sin edición. Al tocar cualquier componente (nodo, viga, columna, panel o apoyo), se resalta y se despliegan todas sus propiedades en el banner superior y en el HUD del canvas.
- **Banner Flotante de Propiedades (`tvComponentInfo`):** Muestra coordenadas, tipo de apoyo, cargas aplicadas ($F_x, F_y, F_z$), longitud, perfil transversal asignado, material y esfuerzos máximos.

#### 5. Base de Datos Normalizada de Materiales y Secciones (JSON)
- Inclusión de los catálogos `materials.json` y `sections.json` con propiedades mecánicas ($E, \nu, \rho, f_y$) y seccionales ($A, I_z, I_y, S_z, Z_z$).
- Sincronización automática con los selectores de la interfaz de usuario y las memorias técnicas en PDF.

#### 6. Sincronización Dinámica del Veredicto Técnico en Memorias PDF
- **Evaluación Integral en Tiempo Real:**
  - Las secciones 9.1 y 9.2 del reporte PDF generado por `PDFReportGenerator.java` ahora leen y evalúan dinámicamente los estados reales de deflexión máxima ($L/360$ o $25\text{ mm}$), derivas laterales de entrepiso (NSR-10 $\le 1.0\%$, ASCE 7-22 $\le 1.5\%$) y sobreesfuerzos en miembros estructurales ($D/C > 1.0$).
  - Si un modelo supera las tolerancias de servicio o derivas admisibles, el veredicto formal emite `STRUCTURAL VERDICT: CONDITIONAL / SERVICEABILITY REVIEW REQUIRED (VERIFY)` con advertencias técnicas detalladas y sellos en color ámbar/naranja, evitando falsas aprobaciones incondicionales.
  - Subsección 6.2 en PDF con envolventes de placas/paneles ($M_x, M_y, M_{xy}, V_{max}$).

#### 7. Aislamiento y Sanitización de la Terminal
- **Directorio de Trabajo Dedicado (`terminal/`):**
  - Se aisló por completo el espacio de ejecución y almacenamiento de la consola interactiva a la subcarpeta `terminal/` dentro del almacenamiento interno de la app.
  - La función de exportación de la terminal ahora extrae de forma limpia y exclusiva los archivos generados por los comandos de la consola sin incluir carpetas de otros proyectos.
  - Implementación de `sanitizeBinaryOutput()` en `CalculixExecutor.java` para reemplazar de forma automática rutas internas largas de Android por nombres ejecutables nativos (`gmsh`, `ccx`, `DRAWEXE`).

#### 8. Rediseño Compacto y Separación de Gestos en Sólidos 3D
- **Interfaz Compacta en Pantalla Única:**
  - Eliminación de `NestedScrollView` en la pestaña de parámetros, manteniendo el 100% de controles CAD (primitivas, redondeos, extrusión, uniones/cortes booleanos).
  - Barra inferior independiente en el visor SceneView protegida contra superposición con los insets de navegación del sistema.

#### 9. Licenciamiento GPLv3 y Firma de Producción
- **Cumplimiento Copyleft Oficial:**
  - Adición formal del archivo `LICENSE` (GNU GPL v3.0) en la raíz del repositorio.
  - Automatización de firma de producción (`keystore.properties`) para compilación de APKs y App Bundles de lanzamiento.

---

### 📦 Artefactos de Compilación Disponibles:
- **APK Release (Producción firmado):** `app-release.apk`
- **App Bundle Release (Google Play):** `app-release.aab`
- **APK Debug:** `app-debug.apk` (/tmp/calculoestructural_build/outputs/apk/debug/app-debug.apk)
