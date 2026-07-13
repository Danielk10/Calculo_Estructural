package com.diamon.civil.engine;

import com.diamon.civil.util.NativeLoader;

public class NativeFeaCore {
    private static boolean librariesLoaded = false;

    public static synchronized void loadLibraries() {
        if (librariesLoaded) return;
        
        try {
            // 1. Capa Base y Dependencias de Terceros
            NativeLoader.loadLibrary("c++_shared");
            NativeLoader.loadLibrary("openblas");
            NativeLoader.loadLibrary("gmp");
            NativeLoader.loadLibrary("z");
            NativeLoader.loadLibrary("freetype");
            
            // 2. Dependencias de Formatos (HDF5 / MED) - Necesarios para GMSH
            NativeLoader.loadLibrary("hdf5");
            NativeLoader.loadLibrary("hdf5_hl");
            NativeLoader.loadLibrary("medC");
            
            // 3. Núcleo OpenCASCADE (Orden Estricto de Dependencia)
            // Foundation
            NativeLoader.loadLibrary("TKernel");
            NativeLoader.loadLibrary("TKMath");
            
            // Modeling Data
            NativeLoader.loadLibrary("TKG2d");
            NativeLoader.loadLibrary("TKG3d");
            NativeLoader.loadLibrary("TKGeomBase");
            NativeLoader.loadLibrary("TKBRep");
            
            // Modeling Algorithms
            NativeLoader.loadLibrary("TKGeomAlgo");
            NativeLoader.loadLibrary("TKTopAlgo");
            NativeLoader.loadLibrary("TKPrim");
            NativeLoader.loadLibrary("TKBO");
            NativeLoader.loadLibrary("TKBool");
            NativeLoader.loadLibrary("TKMesh");
            NativeLoader.loadLibrary("TKShHealing");
            NativeLoader.loadLibrary("TKFillet");
            NativeLoader.loadLibrary("TKOffset");
            NativeLoader.loadLibrary("TKFeat");
            
            // Visualization & Data Exchange (Necesarios para GMSH/CAD)
            NativeLoader.loadLibrary("TKService");
            NativeLoader.loadLibrary("TKHLR");
            NativeLoader.loadLibrary("TKCDF");
            NativeLoader.loadLibrary("TKV3d");
            NativeLoader.loadLibrary("TKCAF");
            NativeLoader.loadLibrary("TKVCAF");
            NativeLoader.loadLibrary("TKLCAF");
            NativeLoader.loadLibrary("TKXCAF");
            NativeLoader.loadLibrary("TKXSBase");
            NativeLoader.loadLibrary("TKDESTEP");
            NativeLoader.loadLibrary("TKDEIGES");
            
            // 4. Motores de Simulación y JNI Final
            // gmsh se ejecuta como binario independiente, no se carga como librería.
            NativeLoader.loadLibrary("calculoestructural");
            
            librariesLoaded = true;
            android.util.Log.d("NativeFeaCore", "Exhaustive native library loading completed successfully");
        } catch (Throwable t) {
            android.util.Log.e("NativeFeaCore", "CRITICAL ERROR: Native library loading failed: " + t.getMessage());
            // Log full stack trace for easier debugging
            t.printStackTrace();
            throw new RuntimeException("Could not initialize native FEA engine: " + t.getMessage(), t);
        }
    }

    static {
        loadLibraries();
    }

    // Lifecycle
    public native long createModel();
    public native void deleteModel(long ptr);
    public native void clearModel(long ptr);

    // Serialization
    public native String modelToInp(long ptr);
    public native String modelToJson(long ptr);
    public native void modelFromJson(long ptr, String json);

    // Project Storage
    public native boolean saveProject(String path, long ptr);
    public native boolean loadProject(String path, long ptr);

    // Parsing (Native Implementation)
    public native String parseDatResults(String datPath);
    public native String parseFrdSummary(String frdPath);
}
