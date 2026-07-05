# Documentación Técnica Final: Proyecto Android CalculoEstructural (100% COMPLETADO)

Este documento unifica toda la auditoría técnica del proyecto Android principal (`/app/src/main/`). Incluye la descripción granular de componentes y su trazabilidad directa con el `plan_implementacion_fea.md`. Todo el sistema técnico ha sido validado y está listo para producción.

---

## 1. Código Fuente Java (`/app/src/main/java/com/diamon/civil/`)

### 1.1. Motor Estructural (`.../engine`)
*   **`StructuralModel.java`**: Define las estructuras de datos (Nodos, Elementos) que representan el modelo estructural en memoria antes de la generación del input de CalculiX.
*   **`StructuralInpGenerator.java`**: Lógica de conversión de `StructuralModel` al formato de texto `.inp` de CalculiX (pórticos).
*   **`CalculixExecutor.java`**: Gestiona la ejecución de binarios (`ccx`, `gmsh`) mediante `ProcessBuilder`, manejando entornos nativos y threads.
*   **`NativeFeaCore.java`**: Wrapper JNI que expone funciones de FEA escritas en C++ a la capa Java.
*   **`ReportGenerator.java`**: Generador de reportes técnicos en formato PDF utilizando iText7.
*   **`FaceCondition.java`**: Modelo de datos para almacenar cargas y apoyos aplicados a superficies 3D.
*   **`MaterialDatabase.java`**: Carga y gestiona los materiales disponibles desde `materials.json`.
*   **`SectionLibrary.java`**: Carga y gestiona las secciones transversales desde `sections.json`.
*   **`GmshRunner.java`**: Lógica específica para ejecutar el enmallador Gmsh sobre geometrías.
*   **`MshToInpConverter.java`**: Lógica para convertir la malla generada por Gmsh (`.msh`) a `.inp`.
*   **`DatParser.java`**: Analiza el archivo `.dat` resultante de la simulación para extraer diagramas de esfuerzos.
*   **`InpGenerator.java`**: Generador genérico de archivos input, incluyendo directivas `*SECTION PRINT`.
*   **`InpEnricher.java`**: Modifica archivos `.inp` para inyectar propiedades de material y sección después del mallado.
*   **`OcctBooleanJNI.java`**: Bridge Java para operaciones booleanas geométricas (OCCT).
*   **`OcctPrimitivesJNI.java`**: Bridge Java para creación de primitivas geométricas (OCCT).
*   **`TerminalCommandExecutor.java`**: Ejecutor genérico de comandos del sistema operativo (usado para tareas auxiliares).

### 1.2. Gestión de Archivos (`.../io`)
*   **`AbaqusInpImporter.java`**: Importa archivos `.inp` externos para cargarlos en el modelo estructural de la app.
*   **`FileHelper.java`**: Utilidades para operaciones básicas de E/S de archivos.

### 1.3. UI y Renderizado (`.../ui`)
*   **`MainActivity.java`**: Actividad principal. Punto de entrada, coordina la navegación y la comunicación entre los motores FEA y la UI.
*   **`FrameGLSurfaceView.java`**: Vista contenedor para el renderizado 3D de pórticos mediante OpenGL ES.
*   **`FrameRenderer.java`**: Clase que implementa la lógica de dibujo OpenGL (cuadrícula, nodos, vigas).
*   **`DiagramView.java`**: Vista para renderizar diagramas de esfuerzos (BMD/SFD).
*   **`SceneViewBridge.kt`**: Puente de integración para `SceneView` v4.18.0 utilizando Jetpack Compose.

### 1.4. Utilitarios (`.../util`)
*   **`FaceSelector.java`**: Lógica para implementar picking (selección táctil) de caras en el visor 3D.
*   **`AssetHelper.java`**: Utilidades para leer recursos desde la carpeta `assets/`.

### 1.5. Pruebas (`.../test`)
*   **Paquete de Pruebas Internas**: Contiene clases dedicadas para la validación interna de los motores de cálculo, parseo de inputs y consistencia de datos antes de la integración con UI.

---

## 2. Código Fuente Nativo C++ (`/app/src/main/cpp/`)

*   **`native-lib.cpp`**: Punto de entrada JNI; registra las funciones que llaman a la lógica nativa.
*   **`AnalysisModel.cpp` / `AnalysisModel.hpp`**: Define la clase nativa `AnalysisModel` para gestión de nodos, elementos, cargas y BCs. Provee serialización JSON.
*   **`ProjectStore.cpp` / `ProjectStore.hpp`**: Lógica de persistencia nativa; guarda/carga el estado del proyecto en archivos JSON.
*   **`CalculixRunner.cpp` / `CalculixRunner.hpp`**: Lógica nativa de bajo nivel para ejecutar binarios de cálculo (manejando buffers de memoria y procesos).
*   **`frd_converter.cpp`**: Conversor de resultados FRD (formato binario de CalculiX) a GLB para visualización 3D.
*   **`OcctBooleanJNI.cpp`**: Mapeo JNI para operaciones booleanas geométricas basadas en OpenCASCADE.
*   **`OcctPrimitivesJNI.cpp`**: Mapeo JNI para creación de primitivas OCCT (cubos, esferas, etc.).
*   **Librerías/Headers Auxiliares**: Incluye una amplia gama de librerías en `/app/src/main/cpp/include/` (HDF5, MED, TCL, TK, entre otras) para soportar la funcionalidad extendida de los motores de simulación.

---

## 3. Dependencias Nativas (`/app/src/main/jniLibs/`)
*   El proyecto contiene un conjunto extensivo de bibliotecas nativas compiladas (`.so`) para `arm64-v8a` que soportan la integración de CalculiX, OpenCASCADE, Gmsh y sus dependencias (HDF5, MED, etc.).

---

## 4. Recursos de UI (XML - `/app/src/main/res/`)

*   **Layouts/Menús**: `activity_main.xml` (main), `nav_header.xml` (drawer), `drawer_menu.xml`, `main_menu.xml`.
*   **Estilos/Recursos**: `edit_text_border.xml`, `themes.xml` (claro/oscuro), `strings.xml`, `colors.xml`.
*   **Sistema**: `data_extraction_rules.xml`, `backup_rules.xml`, `AndroidManifest.xml`.

---

## 5. Trazabilidad: Código Fuente vs. Plan de Implementación

| Componente | Propósito | Ítem Plan |
| :--- | :--- | :--- |
| `StructuralModel.java` | Estructuras datos memoria. | **B3** |
| `StructuralInpGenerator.java`| Generación .inp. | **B3** |
| `CalculixExecutor.java` | Ejecución binarios nativos. | **A2** |
| `NativeFeaCore.java` | Interface JNI lógica nativa. | **A1** |
| `ReportGenerator.java` | Generación de reportes PDF. | **D2** |
| `FaceCondition.java` | Datos de cargas/apoyos en caras 3D. | **C3** |
| `MaterialDatabase.java` | Gestión materiales (JSON). | **C4** |
| `SectionLibrary.java` | Gestión secciones (JSON). | **B2** |
| `GmshRunner.java` | Enmallador Gmsh. | **A1** |
| `MshToInpConverter.java` | Conversión malla -> input. | **A1** |
| `DatParser.java` | Análisis resultados. | **B4** |
| `InpGenerator.java` | Generador genérico. | **A2** |
| `InpEnricher.java` | Inyección propiedades. | **A1** |
| `MainActivity.java` | Coordinador UI/Motores. | **A1, C1, C3, C5, D3** |
| `FrameGLSurfaceView.java` | Vista render 3D. | **B1** |
| `FrameRenderer.java` | Lógica dibujo. | **B1** |
| `DiagramView.java` | Visualizador diagramas. | **B4** |
| `SceneViewBridge.kt` | Integración SceneView + Compose. | **A1** |
| `FaceSelector.java` | Selección táctil 3D. | **C3** |
| `AnalysisModel.*` | Estructura nativa FEA. | **A1** |
| `ProjectStore.*` | Persistencia estado. | **A1** |
| `CalculixRunner.*` | Gestión nativa binarios. | **A1** |
| `frd_converter.cpp` | Conversión FRD -> GLB. | **A1** |
| `OcctBooleanJNI.cpp` | Booleanas OCCT. | **C2** |
| `OcctPrimitivesJNI.cpp` | Primitivas OCCT. | **C1** |
| `test/*` | Validaciones internas. | **V1** |
