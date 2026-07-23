# Plan de Implementación Consolidado: Structural FEA Suite (Versión Extendida)

Este plan ha sido generado tras auditar el código fuente actual del proyecto Android (`app/src/main/...`) y contrastarlo exhaustivamente con las especificaciones del diseño original (incluyendo las capacidades avanzadas de OCCT, Gmsh y CalculiX). Ha sido validado por un análisis profundo de subagentes en la base de código.

## 🟢 1. ESTADO ACTUAL: LO QUE REALMENTE ESTÁ IMPLEMENTADO

### 1.1 Núcleo (Backend / NDK)
- **Motor CalculiX & Gmsh**: Los binarios están integrados y se ejecutan correctamente a través de `CalculixExecutor` y `GmshRunner`.
- **Generación de Archivos**: `AnalysisModel.cpp` exporta estructuras al formato `.inp`.
- **Traducción 3D**: `frd_converter.cpp` lee archivos `.frd` y los convierte a `.glb` (glTF) usando TinyGLTF para su visualización.
- **Parseo de Resultados**: `DatParser.java` está programado para leer Momentos y Cortantes desde bloques "section forces" en archivos `.dat`.

### 1.2 Modo Civil (Estilo SAP2000)
- **Renderizado 3D/2D Visual**: `FrameGLSurfaceView` y `FrameRenderer` dibujan líneas y diagramas de colores.
- **UI Base**: Fragmento `StructuralFragment` con navegación por pestañas.

### 1.3 Modo Mecánico (Estilo Abaqus)
- **Visualizador 3D**: Implementado usando `SceneViewBridge.kt` (motor Filament).
- **Creación de Geometría Básica (OCCT)**: Uso de `OcctPrimitivesJNI` para crear cajas, cilindros y esferas. Operaciones booleanas básicas expuestas (`OcctBooleanJNI`).
- **Fragmento UI**: `SolidFragment` con controles básicos.

---

## 🔴 2. BRECHAS DETECTADAS Y CAPACIDADES OCULTAS POR EXPLOTAR

Tras la auditoría cruzada y revisando las capacidades reales de OCCT y Gmsh, estas son las carencias críticas verificadas en el código que debemos implementar para desatar el verdadero potencial del motor:

### Faltantes en el Nivel CAD (OCCT - Interfaz Gráfica)
Actualmente, el usuario solo puede crear formas primitivas. **Falta exponer las verdaderas capacidades de OCCT** en la UI:
1. **Modificadores Geométricos**: Faltan los controles UI e invocaciones JNI para realizar **Fillets (redondeos)** y **Chamfers (chaflanes)** sobre aristas y bordes curvos (vitales para evitar concentración de esfuerzos irreales).
2. **Generación Avanzada 3D**: Falta la capacidad de dibujar perfiles 2D y hacer **Extrusión**, **Revolución** y **Barrido (Sweep)**.
3. **Importación de CAD Externo**: En `SolidFragment.java` **falta** la capacidad UI (`Intent`) de abrir un archivo `.step`, `.iges` o `.stl` de la memoria del teléfono.

### Faltantes en el Nivel FEM (Configuración de Malla Gmsh)
Actualmente, `GmshRunner` solo controla la densidad (`-clmax`). Se está desperdiciando la capacidad multielemento de CalculiX. **Falta un "Configurador de Malla" en la UI** que permita elegir:
1. **Orden del Elemento (ElementOrder)**: Poder elegir entre malla Lineal (Orden 1) o Cuadrática (Orden 2).
2. **Tipo de Elemento**:
   - Forzar algoritmos **Tetraédricos** (C3D4 rápidos, o C3D10 precisos para geometrías con fillets).
   - Forzar algoritmos **Hexaédricos** (C3D8 o C3D20) usando comandos como `Transfinite` o `Recombine3DAll` en geometrías regulares.
   - Habilitar soporte para elementos **Shell / Cáscara** (S6, S8) a partir de mallas 2D.
3. **Modo Civil (SAP2000)**: `StructuralFragment.java` fuerza el uso de vigas **B31** (lineales). **Falta** programar la interpolación automática del nodo central para habilitar vigas cuadráticas **B32**.

### Faltantes en Post-Proceso y Exportación INP
1. **Falta `*SECTION PRINT`**: En `AnalysisModel.cpp` solo se imprime `*EL PRINT`. Al omitirse `*SECTION PRINT`, CalculiX no calcula los esfuerzos cortantes ni momentos flectores para vigas.
2. **Ray-Casting Interactivo**: En `SceneViewBridge.kt`, la función `onSingleTapConfirmed` es un cascarón que devuelve `null`. **Falta** la matemática para identificar qué cara o nodo exacto tocó el usuario para asignarle un SPC o Carga, ya que actualmente `InpAssembler.java` inyecta cargas rígidas de `-100.0` y lee strings predefinidos ("Fixed", "Loaded").

---

## 🚀 3. NUEVO PLAN DE ACCIÓN EXTENDIDO PASO A PASO

### FASE 1: Reparación del Núcleo Estructural (Civil)
1. **Modificar `AnalysisModel.cpp`**: Añadir la instrucción `*SECTION PRINT, ELSET=Eall, SECTION FORCE, SECTION MOMENT` a la exportación `.inp`.
2. **Implementar vigas B32**: Modificar `StructuralFragment.modelToJson` para calcular automáticamente un tercer nodo (punto medio de la línea) y declarar el elemento como `B32`.
3. **Trazador Gráfico Civil (El Lienzo)**: Reemplazar los `EditText` por un `GridEditorView.java` interactivo donde el usuario arrastre el dedo creando nodos (snap-to-grid).

### FASE 2: Expansión del Modelador CAD (OCCT)
1. **Importador de Archivos (STEP/IGES/STL)**: Programar un `Intent.ACTION_GET_CONTENT` en `SolidFragment.java` para cargar archivos reales y pasarlos al flujo.
2. **Operaciones Complejas (JNI)**: Exponer desde el backend C++ los comandos de OCCT para `BRepFilletAPI_MakeChamfer` y extrusiones, vinculándolos a nuevos botones en la UI (ej. "Aplicar Redondeo", "Extruir").

### FASE 3: Configurador Avanzado de Malla (Gmsh)
1. **UI de Malla**: Añadir a la interfaz de Sólidos un selector de **Orden de Elemento** (Lineal vs Cuadrático) y **Algoritmo de Malla** (Libre/Delaunay para Tet4/Tet10, o Transfinite para Hex8/Hex20).
2. **Scripting Gmsh**: Modificar `GmshRunner.java` para que genere o escriba comandos `.geo` que ordenen a Gmsh ejecutar `Mesh.ElementOrder = 2` o `Recombine3DAll` según lo pida el usuario, en lugar de pasar solo un flag de densidad.

### FASE 4: Condiciones de Contorno Dinámicas (Ray-Casting)
1. **Hit-Test 3D en `SceneView`**: Desarrollar la lógica en `SceneViewBridge.kt` para evaluar colisiones de rayos contra la malla generada, identificando el ID del nodo o cara exacta.
2. **Menú Contextual (UI/UX)**: Mostrar un `BottomSheet` de Android al tocar una superficie, ofreciendo "Fijar (Boundary)" o "Aplicar Presión/Carga (DLOAD/CLOAD)".
3. **Generación Dinámica INP**: Refactorizar `InpAssembler.java` para que consuma estos datos dinámicos elegidos por el usuario, eliminando por completo el harcodeo de `-100.0`.
