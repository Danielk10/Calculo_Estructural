# 📦 Notas de Versión v0.3.0
## Structural Analysis FEA 3D (`com.diamon.civil`) — Versión Oficial v0.3.0

---

### 🌟 Resumen Ejecutivo

La versión **v0.3.0** representa una evolución mayor en la infraestructura computacional de la aplicación, expandiendo el stack nativo a **330 binarios y librerías dinámicas** en arquitectura **ARM64-v8a**. Incorpora aceleración multihilo con **oneAPI TBB**, compresión geométrica 3D de alto rendimiento con **Google Draco**, la suite integral de persistencia científica **HDF5**, soporte moderno de intercambio **glTF/GLB** para OpenCASCADE 8.0.0, actualización de **OpenBLAS a r0.3.34** y sincronización del solver **CalculiX (`libccx.so`)**, todo con una purga total de rutas fijas (`0 RPATH/RUNPATH`) y paridad 100% en los 332 enlaces simbólicos de tiempo de ejecución.

---

### ⚡ 1. Ecosistema Científico Nativo y Nuevas Librerías (ARM64-v8a)

* **oneAPI TBB / oneTBB 2021.13.0:**
  * Integración de `libtbb.so` y el asignador de memoria concurrente `libtbbmalloc.so` para optimizar operaciones booleanas y mallado intensivo en procesadores multinúcleo ARM64.
* **Google Draco 1.5.7:**
  * Incorporación de `libdraco.so` y los ejecutables nativos `draco_encoder` y `draco_decoder` para comprimir mallas de resultados y nubes de puntos hasta en un 95%, reduciendo drásticamente la huella de memoria RAM.
* **Suite HDF5 2.3.0 & MEDfile 6.0.1:**
  * Soporte completo para persistencia jerárquica de matrices y mallas con compresión SZIP (`libsz.so`) y codificación de entropía adaptativa (`libaec.so`).
  * Integración de herramientas CLI (`h5dump`, `h5ls`, `h5diff`, `h5repack`, `h5copy`, `h5stat`, `h5clear`) operables directamente desde la terminal.
* **OpenCASCADE glTF (`TKDEGLTF` & `TKXSDRAWGLTF`):**
  * Soporte nativo para exportar e importar modelos CAD en formato glTF/GLB estándar.
* **OpenBLAS r0.3.34.dev:**
  * Núcleo de álgebra lineal multihilo de alto rendimiento actualizado para ARM64.

---

### 🛡️ 2. Saneamiento Crítico de Rutas y Cumplimiento Android NDK

* **Purga Absoluta de RPATH / RUNPATH (`0 RPATH`):**
  * Eliminación sistemática de todas las rutas absolutas heredadas del entorno de compilación (como rutas de Termux) mediante saneamiento ELF según `REPORTE_CRITICO_RUTAS.md`.
* **Cierre Total del Grafo de Dependencias:**
  * Auditoría de 1,284 dependencias dinámicas requeridas (`DT_NEEDED`) a través de los 330 binarios empaquetados: **0 dependencias faltantes** registradas en `REPORTE_ANALISIS_DEPENDENCIAS.md`.
* **Sincronización de Enlaces Simbólicos (332 enlaces):**
  * `AssetHelper.java` despliega en tiempo de ejecución los 332 enlaces en `files/usr/bin` y `files/usr/lib` apuntando a las librerías empaquetadas en `jniLibs/arm64-v8a`.

---

### 📚 3. Sincronización de Cabeceras C++ y Depuración de Assets

* **1,343 Archivos de Cabecera Sincronizados:**
  * Actualización de 18 cabeceras e incorporación de 1,325 nuevas cabeceras en `app/src/main/cpp/include/` para habilitar el desarrollo JNI directo contra las nuevas librerías.
* **Depuración de Recursos según `REPORTE_LIMPIEZA_ASSETS.md`:**
  * Sincronización selectiva de assets limpios de OpenCASCADE, Tcl/Tk y temas de terminal.

---

# 📦 Notas de Versión v0.2.0
## Structural Analysis FEA 3D (`com.diamon.civil`) — Versión Oficial v0.2.0

---

### 🌟 Resumen Ejecutivo

La versión **v0.2.0** consolida el análisis estructural avanzado en dispositivos móviles mediante la certificación físico-matemática del **Módulo de Sólidos 3D**, la integración de una **Terminal Técnica de Ingeniería** con entorno de scripting paramétrico, optimizaciones de interfaz y la generación de reportes periciales en PDF con trazabilidad completa de parámetros de simulación.

---

### 🔬 1. Módulo de Sólidos 3D y Certificación Mecánica Real

* **Formulación Cuadrática de 10 Nodos Predeterminada:**
  * Se establece el tetraedro cuadrático de 10 nodos como elemento predeterminado, eliminando el bloqueo por cortante (*shear locking*) inherente a formulaciones lineales y alcanzando una correlación superior al **99.5%** con las soluciones analíticas clásicas de vigas elásticas.
* **Matriz Completa de Elementos Finitos Continuum 3D:**
  * Soporte validado para las familias canónicas de elementos volumétricos: tetraedros (4 y 10 nodos), hexaedros/ladrillos (8 y 20 nodos, integración completa y reducida) y cuñas/prismas (6 y 15 nodos).
* **Convergencia Asintótica y Malla Multinivel (7 Niveles):**
  * Verificación y calibración de 7 niveles de refinamiento métrico continuo:
    * **Niveles 1 al 5:** Mallas balanceadas optimizadas para cualquier dispositivo móvil.
    * **Nivel 6 (Hiper Fina - Máxima Móvil Libre):** Refinamiento denso totalmente disponible para cualquier equipo sin restricciones de hardware.
    * **Nivel 7 (Hiper Extremo - Máxima Absoluta):** Refinamiento ultra-denso (~700,000 grados de libertad y memoria de pila extendida), configurado de forma inteligente para dispositivos con $\ge 12\text{ GB}$ de RAM física.
    * **Diferenciación Dinámica y Transparente en la Interfaz (UI):**
      * **Indicador en Vivo de Perfil de Hardware:** Muestra en tiempo real la RAM física y los núcleos de CPU activos directamente en el panel de discretización.
      * Protección inteligente del sistema para evitar cierres inesperados por falta de memoria al seleccionar niveles ultra-densos en terminales con recursos moderados.
* **Depuración y Determinismo:**
  * Filtrado riguroso de modelos auxiliares en el selector de geometrías para evitar duplicados en pantalla.
  * Purga sistemática de archivos temporales de cálculo antes y después de cada análisis para asegurar determinismo numérico en ejecuciones continuas.

---

### 📄 2. Reportes PDF de Ingeniería y Criterios Mecánicos

* **Trazabilidad de Parámetros de Entrada:**
  * Incorporación de la tabla **"Simulation & Boundary Condition Parameters"** que documenta:
    * Formulación canónica y orden cinemático del elemento finito empleado.
    * Constantes elásticas constitutivas del material (Módulo de Young $E$ y coeficiente de Poisson $\nu$).
    * Vector de acciones mecánicas con identificación del grado de libertad activo (Axial $X$, Vertical $Y$, Lateral $Z$).
    * Regiones de contorno y restricciones cinemáticas impuestas.
* **Criterios de Mecánica de Medios Continuos:**
  * **Puntos de Gauss:** Explicación técnica sobre la recuperación de esfuerzos de Cauchy evaluados en puntos de integración interna respecto a la fibra exterior.
  * **Singularidad de Empotramiento 3D:** Justificación analítica del efecto Poisson en elementos de segundo orden bajo apoyos rígidos tridimensionales y recomendación de evaluación pericial a distancia de Saint-Venant.
* **Arquitectura Modular de Exportación:**
  * Generadores especializados e independientes por tipología estructural: pórticos 2D/3D, componentes sólidos 3D y reportes de consola.

---

### 💻 3. Terminal Técnica y Scripting Paramétrico

* **Modelado Geométrico Tridimensional por Consola:**
  * Generación headless de sólidos primitivos (cajas, cilindros, esferas) y operaciones booleanas (unión, corte, intersección) mediante comandos de texto.
* **Control Métrico de Discretización:**
  * Parámetros de tamaño de elemento, optimización de malla y exportación directa de geometrías.
* **Solucionador Numérico Multinúcleo:**
  * Aprovechamiento paralelo de todos los núcleos del procesador del dispositivo para resolver sistemas de ecuaciones lineales y tensoriales en menor tiempo.
* **Herramientas de Consola:**
  * Comandos de creación, edición y visualización de archivos de datos en almacenamiento local, con manual de usuario interactivo.

---

### 🎨 4. Interfaz de Usuario y Estabilidad

* **Modo Oscuro Mejorado:** Corrección de contraste y legibilidad en las pestañas principales de navegación bajo fondos oscuros.
* **Prevención de Suspensión:** Integración de mecanismo de bloqueo de suspensión (*Wake Lock*) que mantiene activo el procesador en segundo plano durante corridas de cálculo volumétrico pesado.
* **Normalización de Mensajería:** Filtro y control de repetición de notificaciones (*toast debouncing*) y unificación de términos formales de ingeniería.

---

### 🧪 5. Certificación de Calidad

* **Batería de Pruebas Automatizadas:** 100% de aprobación en pruebas analíticas y de integración.
* **Artefactos Compilados:**
  * App Bundle Release (Google Play): `/tmp/calculoestructural_build/outputs/bundle/release/app-release.aab`
  * APK Release (Producción firmado): `/tmp/calculoestructural_build/outputs/apk/release/app-release.apk`
  * APK Debug: `/tmp/calculoestructural_build/outputs/apk/debug/app-debug.apk`
