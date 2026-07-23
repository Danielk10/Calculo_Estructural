# Plan de Desarrollo Futuro: Structural FEA Suite

Este plan refleja las brechas finales y las tareas pendientes identificadas tras la auditoría profunda del código y los avances hasta la versión `v0.4.0`.

## 🟢 1. LOGROS ALCANZADOS (Ya en código)
- **Motor Civil (SAP2000):** Cálculo de pórticos con elementos B32 (cuadráticos). Inyección de `*SECTION PRINT` funcional. Parser `DatParser` extrayendo Cortantes y Momentos. PDF Report profesional y completo.
- **Motor Sólidos (Abaqus):** Generación de Cajas, Cilindros, Esferas. Operaciones OCCT de Fillet y Chamfer (Linkeadas y cargadas correctamente en JNI tras parche). Mallado Hexaédrico / Cuadrático vía Gmsh.
- **Visualización 3D:** Parseo de archivo `.frd` a glTF y visualización interactiva en SceneView de Filament.
- **Ray-Casting UI:** BottomSheet funcional para asignar cargas en superficies del sólido 3D.
- **Generación PDF de Sólidos:** Utiliza `android.graphics.pdf.PdfDocument` de forma nativa para armar un reporte profesional que sustituyó a la librería de terceros iText.

## 🔴 2. TAREAS PENDIENTES / BRECHAS (Lo que falta)

### 2.1 Conexión Total del Ray-Casting (Módulo Sólidos)
* **El Problema:** Actualmente el menú interactivo capta la carga (`currentDynamicLoadValue`) e inyecta esa magnitud, PERO el ensamblador `InpAssembler.java` aún depende de nombres de grupos físicos estáticos (Physical Group `"Fixed"`, `"Loaded"`, `"SURFACE1"`, `"SURFACE2"`) creados por defecto en la geometría `.geo`.
* **La Solución:** Refactorizar `InpAssembler.java` para que reciba directamente la lista de IDs nodales o IDs de caras seleccionadas por el usuario al hacer clic en SceneView, y defina los bloques `*NSET` y `*BOUNDARY` / `*CLOAD` dinámicamente según la malla en memoria.

### 2.2 Rejilla Interactiva (GridEditorView - Módulo Civil)
* **El Problema:** La interfaz civil sigue dependiendo de que el usuario introduzca los Nodos y Elementos manualmente en cajas de texto (`EditText`), en lugar de dibujarlos en un lienzo estilo SAP2000.
* **La Solución:** Implementar un `GridEditorView` personalizado (Canvas 2D) que permita Snap-to-Grid para que el ingeniero construya el marco haciendo clics en la pantalla.

### 2.3 Post-procesador Gráfico PDF
* **El Problema:** Ambos generadores de PDF (`PDFReportGenerator` y `SolidPDFReportGenerator`) imprimen datos tabulados y logs, pero no incluyen "capturas de pantalla" de la deformación o de la malla renderizada.
* **La Solución:** Modificar los renderizadores OpenGL y Filament (SceneView) para capturar el Framebuffer (Bitmaps) de la pantalla e inyectarlos directamente en el lienzo del `PdfDocument` generado.

### 2.4 Extensión de Lógica OCCT
* **El Problema:** Solo se implementaron primitivas y operaciones booleanas básicas (Fillet, Chamfer). Faltan extrusiones (Sweep/Extrude) a partir de bocetos (Sketches) 2D.
* **La Solución:** Implementar la lectura de paths o SVG y conectarlos a `BRepOffsetAPI_MakePipe` o `BRepPrimAPI_MakePrism` de OpenCASCADE en la capa JNI.

## 🚀 3. CONCLUSIÓN
El motor base, las dependencias dinámicas NDK y los flujos están completamente estabilizados. Las tareas restantes se centran netamente en la manipulación y orquestación dinámica en la capa de UI / Java, y en perfeccionar la retroalimentación gráfica del usuario.
