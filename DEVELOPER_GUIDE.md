# Developer Guide: FEA 3D Visualization Pipeline

This guide explains how the 3D visualization system works and how to use it within the **Structural Analysis FEA** app.

## 1. Architectural Overview

The app uses a hybrid pipeline to transform numerical FEA results into interactive 3D models:
1. **FEA Engine (CalculiX/ccx):** Solves the structural problem and outputs a `.frd` (Results File).
2. **Native Converter (C++/NDK):** A JNI-based component (`frd_converter.cpp`) using `tinygltf` to parse the `.frd` file and generate a `.glb` (glTF Binary) file.
3. **Visualization (SceneView):** An Android 3D engine that renders the `.glb` file with support for vertex colors (stress heatmaps).

## 2. The Conversion Process

The conversion logic is triggered in `MainActivity.java` after a successful simulation:

```java
// Logic inside runAnalysis()
File frdFile = new File(getFilesDir(), "structural_simulation.frd");
File glbFile = new File(getFilesDir(), "structural_simulation.glb");
boolean converted = calculixExecutor.convertFrdToGlb(frdFile.getPath(), glbFile.getPath());
```

### Key Technical Details:
- **minSdkVersion:** Requires level 24 (Android 7.0) due to SceneView library constraints.
- **Vertex Colors:** The converter maps Von Mises stress values to a RGB gradient (Blue = Low, Red = High).
- **Element Support:** Currently supports `TET4` (3D Tetrahedrons) and `TRIA3` (2D Triangles).
- **Math Library:** Uses `dev.romainguy.kotlin.math.Float3` for 3D coordinates.

## 3. How to Use the 3D Viewer

### In the UI:
1. Navigate to the **VIEWER** tab.
2. The viewer will automatically attempt to load `structural_simulation.glb` if it was generated.
3. Use standard touch gestures (pinch to zoom, one finger to rotate).

### Manual Loading for Testing:
To load a specific model manually from code:
```java
cargarModeloExterno(new File(getFilesDir(), "my_model.glb"));
```

Implementation details for loading:
```java
ModelNode modelNode = new ModelNode(
        binding.sceneView.getEngine(),
        file.getPath(),
        true,
        1.0f,
        new Float3(0.0f, 0.0f, 0.0f),
        null,
        null
);
binding.sceneView.addChild(modelNode);
```

## 4. Maintenance (C++)

The converter source is located at `app/src/main/cpp/frd_converter.cpp`. 
- **Dependencies:** Uses headers in `app/src/main/cpp/include/` (tinygltf, json.hpp).
- **Compiling:** Managed by CMake through `app/src/main/cpp/CMakeLists.txt`.

## 5. Troubleshooting
- **No Model Shown:** Check Logcat for `FRD_CONVERTER` tags. Ensure the `.frd` file contains `CLSTRESS` blocks.
- **SceneView Issues:** Ensure the device supports OpenGL ES 3.0+.
