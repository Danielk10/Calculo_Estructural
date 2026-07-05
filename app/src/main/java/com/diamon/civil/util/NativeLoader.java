package com.diamon.civil.util;

import android.util.Log;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilidad para la carga de librerías nativas con nombres normalizados.
 * Mapea los nombres internos (ej: TKMath) a los nombres físicos de Android (ej: libTKMath.so).
 */
public class NativeLoader {
    private static final String TAG = "NativeLoader";
    private static final Map<String, String> LIBRARY_MAP = new HashMap<>();

    static {
        // Mapeo basado en DOCUMENTACION_RENOMBRADO_BINARIOS.md
        LIBRARY_MAP.put("TKernel", "TKernel");
        LIBRARY_MAP.put("TKMath", "TKMath");
        LIBRARY_MAP.put("TKG2d", "TKG2d");
        LIBRARY_MAP.put("TKG3d", "TKG3d");
        LIBRARY_MAP.put("TKGeomBase", "TKGeomBase");
        LIBRARY_MAP.put("TKBRep", "TKBRep");
        LIBRARY_MAP.put("TKTopAlgo", "TKTopAlgo");
        LIBRARY_MAP.put("TKBO", "TKBO");
        LIBRARY_MAP.put("TKBool", "TKBool");
        LIBRARY_MAP.put("TKPrim", "TKPrim");
        LIBRARY_MAP.put("openblas", "openblas");
        LIBRARY_MAP.put("gmsh", "gmsh");
        LIBRARY_MAP.put("calculoestructural", "calculoestructural");
        LIBRARY_MAP.put("c++_shared", "c++_shared");
        
        // Agregar casos especiales de versiones o puntos si son cargados via JNI
        LIBRARY_MAP.put("EGL", "EGL_v1");
        LIBRARY_MAP.put("GLESv2", "GLESv2_v2");
    }

    public static void loadLibrary(String libName) {
        String physicalName = LIBRARY_MAP.getOrDefault(libName, libName);
        try {
            Log.d(TAG, "Cargando librería nativa: " + libName + " (físico: " + physicalName + ")");
            System.loadLibrary(physicalName);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Error cargando librería " + libName + " (" + physicalName + "): " + e.getMessage());
            // Fallback al nombre original por si acaso
            if (!physicalName.equals(libName)) {
                System.loadLibrary(libName);
            }
        }
    }
}
