# 📦 Notas de Versión v0.3.0
## Structural Analysis FEA 3D (`com.diamon.civil`) — Versión Oficial v0.3.0

---

### 🌟 Resumen Ejecutivo

La versión **v0.3.0** consolida el análisis estructural avanzado en dispositivos móviles mediante la certificación físico-matemática del **Módulo de Sólidos 3D**, la integración de una **Terminal Técnica de Ingeniería** con scripting en Tcl 8.6, y la independencia total de reportes PDF periciales con trazabilidad completa de parámetros de simulación.

---

### 🔬 1. Módulo de Sólidos 3D y Certificación Física Real

* **Formulación Cuadrática `C3D10` Predeterminada:**
  * Se establece el tetraedro cuadrático de 10 nodos (`C3D10`) como elemento predeterminado, eliminando el bloqueo por cortante (*shear locking*) inherente a elementos lineales y alcanzando una correlación superior al **99.5%** con las soluciones clásicas de Euler-Bernoulli y Timoshenko ($\delta = 0.2000\text{ mm}$).
* **Matriz Completa de Elementos Finitos Continuum 3D:**
  * Soporte validado para los 8 tipos de elementos de CalculiX/Abaqus: tetraedros (`C3D4`, `C3D10`), hexaedros/ladrillos (`C3D8`, `C3D8R`, `C3D20`, `C3D20R`) y cuñas/prismas (`C3D6`, `C3D15`).
* **Convergencia Asintótica y Malla Multinivel (7 Niveles):**
  * Verificación y calibración de 7 niveles de refinamiento métrico con Gmsh:
    * **Niveles 1 al 5:** Mallas continuas y balanceadas (~50mm a ~5mm) optimizadas para cualquier dispositivo móvil.
    * **Nivel 6 (Hiper Fina - ~2.5mm | Máxima Móvil - Totalmente Libre / Alta Exigencia):** Refinamiento denso (`MeshSizeFactor = 0.25`, ~64,000 elementos C3D10) totalmente libre y disponible para cualquier dispositivo móvil sin restricciones de hardware. En modelos de gran volumen exige alta memoria RAM física (si el teléfono agota su memoria y se cierra, se comporta como en las versiones anteriores sin limitar la libertad del usuario).
    * **Nivel 7 (Hiper Extremo - ~1.5mm | Máxima Absoluta en Configuración Máxima):** Refinamiento ultra-denso sin restricciones (`MeshSizeFactor = 0.18`, ~174,000 elementos C3D10, ~700,000 DOFs y `OMP_STACKSIZE=128M`), reservado exclusivamente para los terminales más potentes del mercado (flagships con ≥12 GB de RAM física, como Red Magic, ROG Phone, etc.).
    * **Diferenciación Dinámica y Transparente en la Interfaz (UI):**
      * El usuario en un teléfono estándar visualiza explícitamente: `Nivel 6 / 7 (Hiper Fina - ~2.5mm (Máxima para este dispositivo: Menos Recursos))`, sabiendo con total transparencia que está en el tope de su hardware.
      * Al tocar o intentar deslizar al Nivel 7, el slider se tranca en el Nivel 6 con un mensaje Toast claro: *"Estás en la densidad Máxima para tu dispositivo (Nivel 6: optimizada con menos recursos). El Nivel 7 es la Máxima Absoluta en configuración máxima, exclusiva para flagships con ≥12 GB de RAM."*
      * Solo los dispositivos con hardware certificado de ≥12 GB desbloquean el acceso al Nivel 7 (*Máxima Absoluta*).
* **Corrección en el Selector de Geometrías (Spinner):**
  * Filtrado estricto de archivos de malla auxiliares (`cantilever_wedge.geo`), evitando elementos duplicados en la interfaz y unificando el modelo base bajo `Benchmark: Viga en Voladizo`.
  * Purga sistemática de temporales antes y después de cada análisis para garantizar determinismo estricto.

---

### 📄 2. Reportes PDF de Ingeniería y Criterios Mecánicos

* **Trazabilidad de Parámetros de Entrada (`SolidPDFReportGenerator`):**
  * Incorporación de la tabla **"Simulation & Boundary Condition Parameters"** que extrae y documenta:
    * Formulación canónica del elemento finito empleado.
    * Parámetros constitutivos del material (Módulo de Young $E$ y coeficiente de Poisson $\nu$).
    * Vector de carga mecánica con identificación unívoca del grado de libertad activo (DOF 1: Axial $X$, DOF 2: Vertical $Y$, DOF 3: Lateral $Z$).
    * Regiones de contorno y restricciones cinemáticas impuestas.
* **Criterios de Mecánica de Medios Continuos Documentados:**
  * **Puntos de Gauss:** Explicación técnica de tensiones de Cauchy evaluadas en puntos de integración numérica internos respecto a la fibra extrema superficial.
  * **Singularidad de Empotramiento 3D:** Justificación analítica del efecto Poisson en mallas de segundo orden (`C3D20`) bajo apoyos rígidos ($U=0$) y recomendación pericial de evaluación a distancia $x \ge h/2$ por el Principio de Saint-Venant.
* **Arquitectura Modular de Exportación:**
  * Generadores dedicados e independientes por módulo: `PDFReportGenerator` (Pórticos 2D/3D), `SolidPDFReportGenerator` (Sólidos 3D) y `TerminalPDFReportGenerator` (Terminal CLI).

---

### 💻 3. Terminal Técnica y Entorno de Scripting

* **Ejecución Paramétrica con OpenCASCADE (`draw` / `DRAWEXE`):**
  * Soporte nativo de scripts en Tcl 8.6 para generación headless de primitivas CSG (cajas, cilindros, esferas) y operaciones booleanas (unión, corte, intersección).
* **Control Completo del Mallador Gmsh:**
  * Parámetros métricos (`clmax`, `clmin`), optimización de mallas y exportación directa en formato `.inp`.
* **Solucionador CalculiX CCX Multihilo (OpenMP):**
  * Aprovechamiento automático de todos los núcleos CPU del dispositivo y tamaño de pila extendido (`OMP_STACKSIZE=64M`).
* **Herramientas de Consola:** Comandos de edición y visualización en disco (`echo`, `cat`), manual interactivo y Guía Maestra de 9 capítulos.

---

### 🧪 4. Certificación y Estado del Proyecto

* **Pruebas de Integración:** 100% de aprobación en la suite de pruebas unitarias JUnit (134/134 pruebas exitosas en Gradle).
* **Batería Local de Simulación:** Aprobación del 100% en `validate_solids_complete_matrix.py` (29/29) y `test_all_sample_models.py` (13/13).
* **Artefactos Compilados:**
  * APK Release (Producción firmado): `/tmp/calculoestructural_build/outputs/apk/release/app-release.apk`
  * APK Debug: `/tmp/calculoestructural_build/outputs/apk/debug/app-debug.apk`
