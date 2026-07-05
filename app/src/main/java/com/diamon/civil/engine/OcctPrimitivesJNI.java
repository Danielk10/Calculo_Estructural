package com.diamon.civil.engine;

/**
 * C1: OcctPrimitivesJNI — JNI interface for OpenCASCADE primitive creation.
 */
import com.diamon.civil.util.NativeLoader;

public class OcctPrimitivesJNI {
    static {
        NativeLoader.loadLibrary("c++_shared");
        NativeLoader.loadLibrary("openblas");
        NativeLoader.loadLibrary("gmsh");
        NativeLoader.loadLibrary("calculoestructural");
    }

    public static native boolean createBox(double l, double w, double h, String outPath);
    public static native boolean createCylinder(double r, double h, String outPath);
    public static native boolean createSphere(double r, String outPath);
}
