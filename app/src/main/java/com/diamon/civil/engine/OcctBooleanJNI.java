package com.diamon.civil.engine;

/**
 * C2: OcctBooleanJNI — JNI interface for OpenCASCADE boolean operations.
 */
import com.diamon.civil.util.NativeLoader;

public class OcctBooleanJNI {
    static {
        NativeLoader.loadLibrary("c++_shared");
        NativeLoader.loadLibrary("openblas");
        NativeLoader.loadLibrary("gmsh");
        NativeLoader.loadLibrary("calculoestructural");
    }

    public static native boolean fuse(String pathA, String pathB, String outPath);
    public static native boolean cut(String pathA, String pathB, String outPath);
    public static native boolean intersect(String pathA, String pathB, String outPath);
}
