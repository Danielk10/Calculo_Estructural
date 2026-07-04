package com.diamon.civil.engine;

/**
 * C2: OcctBooleanJNI — JNI interface for OpenCASCADE boolean operations.
 */
public class OcctBooleanJNI {
    static {
        System.loadLibrary("c++_shared");
        System.loadLibrary("openblas");
        System.loadLibrary("gmsh");
        System.loadLibrary("calculoestructural");
    }

    public static native boolean fuse(String pathA, String pathB, String outPath);
    public static native boolean cut(String pathA, String pathB, String outPath);
    public static native boolean intersect(String pathA, String pathB, String outPath);
}
