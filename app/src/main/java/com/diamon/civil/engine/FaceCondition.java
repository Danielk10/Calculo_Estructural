package com.diamon.civil.engine;

/**
 * C3: FaceCondition — Stores boundary conditions or loads applied to a 3D model surface.
 */
public class FaceCondition {
    public enum Type {
        FIXED,
        PRESSURE,
        MESH_REFINEMENT
    }

    public final int surfaceId;
    public final Type type;
    public final double value;

    public FaceCondition(int surfaceId, Type type, double value) {
        this.surfaceId = surfaceId;
        this.type = type;
        this.value = value;
    }
}
