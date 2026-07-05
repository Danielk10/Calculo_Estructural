package com.diamon.civil.engine;

import com.diamon.civil.util.NativeLoader;

public class NativeFeaCore {
    static {
        NativeLoader.loadLibrary("c++_shared");
        try {
            NativeLoader.loadLibrary("TKernel");
            NativeLoader.loadLibrary("TKMath");
            NativeLoader.loadLibrary("TKG2d");
            NativeLoader.loadLibrary("TKG3d");
            NativeLoader.loadLibrary("TKGeomBase");
            NativeLoader.loadLibrary("TKBRep");
            NativeLoader.loadLibrary("TKTopAlgo");
            NativeLoader.loadLibrary("TKBO");
            NativeLoader.loadLibrary("TKBool");
            NativeLoader.loadLibrary("TKPrim");
        } catch (Exception e) {
            // Ignorar errores si algunas no son críticas para el inicio
        }
        NativeLoader.loadLibrary("openblas");
        NativeLoader.loadLibrary("gmsh");
        NativeLoader.loadLibrary("calculoestructural");
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

    // Execution
    public native String runCalculix(String workDir, String libDir, String jobName, long ptr);
}
