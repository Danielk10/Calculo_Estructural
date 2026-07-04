package com.diamon.civil.engine;

/**
 * C1: OcctPrimitivesJNI — JNI interface for OpenCASCADE primitive creation.
 */
public class OcctPrimitivesJNI {
    static {
        System.loadLibrary("c++_shared");
        System.loadLibrary("openblas");
        System.loadLibrary("gmsh");
        System.loadLibrary("calculoestructural");
    }

    public static native boolean createBox(double l, double w, double h, String outPath);
    public static native boolean createCylinder(double r, double h, String outPath);
    public static native boolean createSphere(double r, String outPath);
}
