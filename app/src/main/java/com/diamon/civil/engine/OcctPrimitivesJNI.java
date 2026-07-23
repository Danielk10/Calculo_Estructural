package com.diamon.civil.engine;

/**
 * C1: OcctPrimitivesJNI — JNI interface for OpenCASCADE primitive creation.
 */
import com.diamon.civil.util.NativeLoader;

public class OcctPrimitivesJNI {
    static {
        NativeFeaCore.loadLibraries();
    }

    public static native boolean createBox(double l, double w, double h, String outPath);
    public static native boolean createCylinder(double r, double h, String outPath);
    public static native boolean createSphere(double r, String outPath);

    public static native boolean applyFillet(String inputPath, String outputPath, double radius);
    public static native boolean applyChamfer(String inputPath, String outputPath, double distance);
    public static native boolean applyExtrude(String inputPath, String outputPath, double dx, double dy, double dz);
}
