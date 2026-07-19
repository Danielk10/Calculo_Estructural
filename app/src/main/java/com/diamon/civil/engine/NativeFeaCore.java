package com.diamon.civil.engine;

import com.diamon.civil.util.NativeLoader;

public class NativeFeaCore {
    private static volatile boolean librariesLoaded = false;

    /*
     * These are exactly the JNI library's CMake dependencies.  Gmsh and CalculiX
     * are executable processes, not in-process libraries.  Likewise SceneView
     * must use Android's EGL/GLES implementation, never the desktop copies shipped
     * for the standalone CAD executables.
     */
    private static final String[] JNI_DEPENDENCIES = {
            "c++_shared",
            "TKernel", "TKMath", "TKG2d", "TKG3d", "TKGeomBase",
            "TKBRep", "TKGeomAlgo", "TKTopAlgo", "TKPrim", "TKBO", "TKBool",
            "calculoestructural"
    };

    public static synchronized void loadLibraries() {
        if (librariesLoaded) return;
        
        try {
            for (String dependency : JNI_DEPENDENCIES) {
                NativeLoader.loadRequiredLibrary(dependency);
            }
            
            librariesLoaded = true;
            android.util.Log.d("NativeFeaCore", "Exhaustive native library loading completed successfully");
        } catch (Throwable t) {
            librariesLoaded = false;
            android.util.Log.e("NativeFeaCore", "CRITICAL ERROR: Native library loading failed: " + t.getMessage());
            // Log full stack trace for easier debugging
            t.printStackTrace();
            throw new RuntimeException("Could not initialize native FEA engine: " + t.getMessage(), t);
        }
    }

    public static boolean isLibrariesLoaded() {
        return librariesLoaded;
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
