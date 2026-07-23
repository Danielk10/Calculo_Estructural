# Plan de Implementación Consolidado: Structural FEA Suite

Este plan ha sido generado tras auditar el código fuente actual del proyecto Android (`app/src/main/...`) y contrastarlo con las especificaciones del diseño original (conversaciones sobre SAP2000 y Abaqus).

## 🟢 1. ESTADO ACTUAL: LO QUE REALMENTE ESTÁ IMPLEMENTADO

### 1.1 Núcleo (Backend / NDK)
- **Motor CalculiX & Gmsh**: Los binarios están integrados y se ejecutan correctamente a través de `CalculixExecutor` y `GmshRunner`.
- **Generación de Archivos**: `AnalysisModel.cpp` exporta estructuras a `.inp`.
- **Traducción 3D**: `frd_converter.cpp` lee archivos `.frd` y los convierte a `.glb` para visualización 3D.
- **Parseo de Resultados**: `DatParser.java` está programado para leer Momentos y Cortantes de archivos `.dat`.

### 1.2 Modo Civil (Estilo SAP2000)
- **Renderizado 3D/2D Visual**: `FrameGLSurfaceView` y `FrameRenderer` están implementados para dibujar líneas y diagramas de colores.
- **UI Base**: Fragmento `StructuralFragment` con pestañas.

### 1.3 Modo Mecánico (Estilo Abaqus)
- **Visualizador 3D**: Implementado usando `SceneViewBridge.kt` (Filament por debajo).
- **Creación de Geometría**: Uso de `OcctPrimitivesJNI` para crear cajas, cilindros y esferas básicas.
- **Fragmento UI**: `SolidFragment` con controles básicos.

---

## 🔴 2. BRECHAS DETECTADAS: LO QUE FALTA POR IMPLEMENTAR

Tras revisar el código, estas son las carencias críticas que impiden que la app funcione como se describió en la visión original:

### Faltantes en el Modo Civil (SAP2000)
1. **Editor de Lienzo Interactivo (Grid)**: Actualmente, `StructuralFragment` usa campos de texto (`etNodes`, `etElements`) para introducir coordenadas a mano. **Falta** el lienzo táctil donde el usuario dibuja las líneas con el dedo.
2. **Elementos B32 (Cuadráticos)**: `StructuralFragment.java` (línea 246) fuerza el uso de `B31` (lineales) por problemas técnicos. **Falta** programar la interpolación automática del nodo central para poder usar `B32`.
3. **Falta `*SECTION PRINT`**: En `AnalysisModel.cpp` (línea 75) solo se imprime `*EL PRINT, ELSET=Eall\nS, E\n`. Sin `*SECTION PRINT`, CalculiX no calcula los momentos y fuerzas cortantes para las vigas, por lo que `DatParser` fallará o no encontrará datos.
4. **Catálogo de Perfiles Comerciales**: Faltan los menús para seleccionar perfiles reales (W, IPE) en lugar de enviar un arreglo duro de `[200, 200]`.

### Faltantes en el Modo Mecánico (Abaqus)
1. **Importador de CAD (STL/STEP)**: En `SolidFragment.java` solo hay botones para primitivas (Box, Cylinder, Sphere). **Falta** el botón e `Intent` de Android para abrir archivos CAD reales del teléfono.
2. **Ray-Casting (Selección Táctil)**: En `SceneViewBridge.kt`, el evento `onGestureListener` está vacío (solo devuelve `null`). **Falta** la lógica matemática para proyectar el toque de la pantalla sobre el modelo 3D y seleccionar caras/nodos para aplicar fuerzas.
3. **Condiciones de Contorno Dinámicas**: `InpAssembler.java` inyecta cargas rígidas de `-100.0` y lee los NSETs generados estáticamente. **Falta** que la UI alimente estos valores basándose en lo que el usuario seleccionó.

---

## 🚀 3. NUEVO PLAN DE ACCIÓN PASO A PASO

### PASO 1: Arreglar el Motor de Vigas (Backend Civil)
- **Modificar `AnalysisModel.cpp`**: Agregar la instrucción `*SECTION PRINT, ELSET=Eall, SECTION FORCE, SECTION MOMENT` a la exportación del archivo `.inp`.
- **Implementar interpolación B32**: Modificar `StructuralFragment.modelToJson` para que cuando el usuario dé 2 nodos, el código interpole un tercer nodo en el medio y declare el elemento como `B32`.

### PASO 2: Importación de Archivos Reales (Abaqus)
- **Modificar `SolidFragment.java`**: Agregar un botón "Import CAD".
- **Lógica de SO**: Usar `Intent(Intent.ACTION_GET_CONTENT)` filtrando por tipos MIME genéricos para seleccionar archivos `.stl` o `.step`.
- Conectar este archivo importado a la tubería existente de `GmshRunner`.

### PASO 3: UI Interactiva Civil (El Lienzo SAP2000)
- **Reemplazar TextBoxes**: Eliminar los EditText de Nodos y Elementos.
- **Crear `GridEditorView.java`**: Una vista 2D personalizada (basada en Canvas) donde el usuario toque puntos en una cuadrícula (snap-to-grid) para dibujar líneas.
- **Generación en Tiempo Real**: Traducir los trazos del `GridEditorView` a objetos `StructuralModel.Node` y `StructuralModel.Element` dinámicamente.

### PASO 4: Ray-Casting y Condiciones Dinámicas (Abaqus)
- **Mejorar `SceneViewBridge.kt`**: Implementar detección de colisión. SceneView permite hacer `HitTest` contra nodos renderizados. Identificar las coordenadas del vértice impactado.
- **Menú Flotante**: Al tocar un nodo, abrir un `BottomSheet` de Android preguntando: "¿Aplicar Fuerza o Empotrar?".
- **Conexión a InpAssembler**: Pasar estos datos a `InpAssembler.java` en vez de usar valores quemados en el código.

### PASO 5: Base de Datos de Perfiles
- Crear/ampliar `MaterialDatabase.java` o un archivo SQLite que contenga un catálogo de vigas (Base, Altura, Espesor del alma, Espesor del patín).
- Alimentar los `params` de la tarjeta `*BEAM SECTION` dinámicamente.
