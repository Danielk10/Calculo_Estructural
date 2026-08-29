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
