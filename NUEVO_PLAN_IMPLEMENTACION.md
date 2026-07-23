# Plan de Implementación Consolidado: Structural FEA Suite

Este plan ha sido generado tras auditar el código fuente actual del proyecto Android (`app/src/main/...`) y contrastarlo con las especificaciones del diseño original (conversaciones sobre SAP2000 y Abaqus). Ha sido validado por un análisis profundo de subagentes en la base de código.

## 🟢 1. ESTADO ACTUAL: LO QUE REALMENTE ESTÁ IMPLEMENTADO

### 1.1 Núcleo (Backend / NDK)
- **Motor CalculiX & Gmsh**: Los binarios están integrados y se ejecutan correctamente a través de `CalculixExecutor` y `GmshRunner`.
- **Generación de Archivos**: `AnalysisModel.cpp` exporta estructuras al formato `.inp`.
- **Traducción 3D**: `frd_converter.cpp` lee archivos `.frd` y los convierte a `.glb` (glTF) usando TinyGLTF para su visualización.
- **Parseo de Resultados**: `DatParser.java` está programado y listo para leer Momentos y Cortantes desde bloques "section forces" en archivos `.dat`.

### 1.2 Modo Civil (Estilo SAP2000)
- **Renderizado 3D/2D Visual**: `FrameGLSurfaceView` y `FrameRenderer` están implementados para dibujar líneas y diagramas de colores.
- **UI Base**: Fragmento `StructuralFragment` con navegación por pestañas y generación de reportes PDF.

### 1.3 Modo Mecánico (Estilo Abaqus)
- **Visualizador 3D**: Implementado usando `SceneViewBridge.kt` (usando el motor Filament de Google por debajo).
- **Creación de Geometría**: Uso de `OcctPrimitivesJNI` para crear cajas, cilindros y esferas (CAD básico).
- **Fragmento UI**: `SolidFragment` con controles básicos y carga de un caso de prueba en voladizo (cantilever).

---

## 🔴 2. BRECHAS DETECTADAS: LO QUE FALTA POR IMPLEMENTAR

Tras la auditoría cruzada, estas son las carencias críticas verificadas en el código:

### Faltantes en el Modo Civil (SAP2000)
1. **Editor de Lienzo Interactivo (Grid)**: `StructuralFragment.java` no tiene interfaz gráfica de dibujo; usa campos de texto (`etNodes`, `etElements`) y el método `parseInputs()` para meter coordenadas a mano. **Falta** el lienzo táctil.
2. **Elementos B32 (Cuadráticos)**: El código fuerza el uso de vigas `B31` (lineales) en la línea 245 de `modelToJson()`, con el comentario de que `B32` era incompatible. **Falta** programar la interpolación automática del nodo central al dibujar para poder habilitar `B32`.
3. **Falta `*SECTION PRINT`**: En `AnalysisModel.cpp` (líneas 74-75) solo se imprime `*EL PRINT, ELSET=Eall\nS, E\n`. Al omitirse `*SECTION PRINT`, CalculiX no calcula los esfuerzos cortantes ni momentos flectores, dejando a `DatParser` ciego.
4. **Catálogo de Perfiles Comerciales**: Se inyecta una sección hardcodeada de `[200, 200]`. **Faltan** menús para seleccionar perfiles reales de ingeniería.

### Faltantes en el Modo Mecánico (Abaqus)
1. **Importador de CAD (STL/STEP)**: En `SolidFragment.java` solo se pueden generar geometrías desde cero mediante rutinas NDK o un caso de prueba precargado. **Falta** la capacidad UI (`Intent`) de abrir un archivo `.stl` o `.step` externo de la memoria del teléfono.
2. **Ray-Casting (Selección Táctil)**: En `SceneViewBridge.kt`, la función `onSingleTapConfirmed` es un "stub" (cascarón) que recibe el parámetro `node` pero lo ignora, retornando `null`. **Falta** la lógica matemática para identificar qué cara o nodo exacto tocó el usuario en el espacio 3D.
3. **Condiciones de Contorno Dinámicas**: Actualmente, `InpAssembler.java` (líneas 29-33) busca cadenas de texto duras como "Fixed" o "Loaded" generadas estáticamente en el `.msh`, e inyecta siempre una fuerza de `-100.0`. **Falta** conectar esto a la UI para que el usuario pueda parametrizar sus propias cargas y apoyos basándose en el ray-casting.

---

## 🚀 3. NUEVO PLAN DE ACCIÓN PASO A PASO

### PASO 1: Reparar el Motor de Vigas y Salidas (Backend Civil)
- **Modificar `AnalysisModel.cpp`**: Añadir la instrucción `*SECTION PRINT, ELSET=Eall, SECTION FORCE, SECTION MOMENT` a la exportación `.inp`.
- **Implementar interpolación B32**: Modificar `StructuralFragment.modelToJson` para que calcule un tercer nodo (punto medio de la línea) y cambie la declaración de elemento a `B32`.

### PASO 2: Trazador Gráfico Civil (El Lienzo SAP2000)
- **Desarrollar `GridEditorView.java`**: Reemplazar los `EditText` por un canvas 2D donde el usuario arrastre el dedo creando nodos y elementos que se ajusten a una cuadrícula (snap-to-grid).
- **Traducción en Tiempo Real**: Convertir los eventos de dibujo a la estructura `StructuralModel`.

### PASO 3: Base de Datos de Secciones
- **Completar `MaterialDatabase.java`**: Crear un pequeño catálogo o SQLite local con geometrías reales (Perfiles W, Tubos, IPE).
- **Ligar con INP**: Usar estos datos al escribir la tarjeta `*BEAM SECTION`.

### PASO 4: Importación de Archivos Externos (Abaqus)
- **Botón Importar en `SolidFragment.java`**: Llamar a `Intent.ACTION_GET_CONTENT` filtrando archivos `.stl` y `.step`.
- **Ruta de Archivo Segura**: Copiar el archivo seleccionado al directorio `workDir` (`getFilesDir()`) antes de enviarlo a `GmshRunner`.

### PASO 5: Ray-Casting Interactivo y Cargas (Abaqus)
- **Hit-Test en `SceneView`**: Modificar `SceneViewBridge.kt` para evaluar colisiones. Identificar el identificador de nodo más cercano al toque.
- **Flujo de Usuario (UI/UX)**: Levantar un `BottomSheet` de Android al tocar una superficie, ofreciendo "Empotrar (Fixed)" o "Aplicar Carga (Load)".
- **Inyección Dinámica**: Refactorizar `InpAssembler.java` para aceptar los nodos seleccionados y la magnitud ingresada por el usuario desde la interfaz, dejando atrás el hardcoding de `-100.0` y etiquetas prefijadas.
