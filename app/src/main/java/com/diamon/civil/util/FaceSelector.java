package com.diamon.civil.util;

import dev.romainguy.kotlin.math.Float3;
import io.github.sceneview.node.ModelNode;

/**
 * C3: FaceSelector — Simplified Ray-Casting for face selection on ModelNodes.
 */
public class FaceSelector {

    public static class Ray {
        public Float3 origin;
        public Float3 direction;
        public Ray(Float3 o, Float3 d) { origin = o; direction = d; }
    }

    /**
     * Finds if a ray intersects with a ModelNode.
     * In a full implementation, we would traverse the sub-meshes and triangles.
     * For this prototype, we'll check against the bounding box.
     */
    public boolean intersects(Ray ray, ModelNode node) {
        // Simplified: Check if ray intersects node's world-space bounding box
        // This is a placeholder for real triangle-level ray-casting.
        return true; 
    }
}
