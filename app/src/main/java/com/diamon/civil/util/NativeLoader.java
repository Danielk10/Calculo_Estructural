package com.diamon.civil.util;

import android.util.Log;
import java.io.File;
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
        // Mapeo basado en DOCUMENTACION_RENOMBRADO_BINARIOS.md y REPORTE_ANALISIS_DEPENDENCIAS.md
        LIBRARY_MAP.put("c++_shared", "c++_shared");
        LIBRARY_MAP.put("openblas", "openblas");
        LIBRARY_MAP.put("gmp", "gmp");
        LIBRARY_MAP.put("z", "z_v1_3_2");
        LIBRARY_MAP.put("TKernel", "TKernel");
        LIBRARY_MAP.put("TKMath", "TKMath");
        LIBRARY_MAP.put("TKG2d", "TKG2d");
        LIBRARY_MAP.put("TKG3d", "TKG3d");
        LIBRARY_MAP.put("TKGeomBase", "TKGeomBase");
        LIBRARY_MAP.put("TKBRep", "TKBRep");
        LIBRARY_MAP.put("TKGeomAlgo", "TKGeomAlgo");
        LIBRARY_MAP.put("TKTopAlgo", "TKTopAlgo");
        LIBRARY_MAP.put("TKPrim", "TKPrim");
        LIBRARY_MAP.put("TKShHealing", "TKShHealing");
        LIBRARY_MAP.put("TKBO", "TKBO");
        LIBRARY_MAP.put("TKBool", "TKBool");
        LIBRARY_MAP.put("TKMesh", "TKMesh");
        LIBRARY_MAP.put("TKFillet", "TKFillet");
        LIBRARY_MAP.put("TKOffset", "TKOffset");
        LIBRARY_MAP.put("TKFeat", "TKFeat");
        LIBRARY_MAP.put("TKHLR", "TKHLR");
        LIBRARY_MAP.put("gmsh", "gmsh");
        LIBRARY_MAP.put("calculoestructural", "calculoestructural");
        LIBRARY_MAP.put("med", "med_v14");
        LIBRARY_MAP.put("medC", "medC_v14");
        LIBRARY_MAP.put("hdf5", "hdf5_v1000");
        LIBRARY_MAP.put("hdf5_hl", "hdf5_hl_v1000");
        LIBRARY_MAP.put("bz2", "bz2_v1_0");
        
        // Casos especiales de versiones normalizadas para cumplir con Android
        LIBRARY_MAP.put("EGL", "EGL_v1");
        LIBRARY_MAP.put("GLESv2", "GLESv2_v2");
        LIBRARY_MAP.put("GLdispatch", "GLdispatch_v0");
        LIBRARY_MAP.put("freetype", "freetype_v6");
    }

    private static String filesDirPath = "/data/data/com.diamon.civil/files";

    public static void setFilesDir(File filesDir) {
        if (filesDir != null) {
            filesDirPath = filesDir.getAbsolutePath();
        }
    }

    public static void loadLibrary(String libName) {
        String physicalName = LIBRARY_MAP.getOrDefault(libName, libName);
        try {
            Log.d(TAG, "Cargando librería nativa: " + libName + " (físico: " + physicalName + ")");
            System.loadLibrary(physicalName);
        } catch (Throwable t) {
            Log.e(TAG, "Fallo inicial cargando " + libName + " (physical: " + physicalName + "): " + t.getMessage() + ", intentando fallback por ruta...");
            if (!loadByPath(libName)) {
                 Log.e(TAG, "FALLO CRÍTICO: No se pudo cargar " + libName);
            }
        }
    }

    private static boolean loadByPath(String libName) {
        File usrLib = new File(filesDirPath, "usr/lib");
        
        // Probar con el nombre exacto que podria tener puntos (ej: libTKMath.so.8.0.0)
        // o la convención _dot.so para librerías cuyo SONAME original termina en punto.
        String[] possibleNames = {
            "lib" + libName + ".so",
            "lib" + libName + ".so_dot.so",
            "lib" + libName + ".so.8.0.0",
            "lib" + libName + ".so.1",
            "lib" + libName + ".so.5.0",
            libName.startsWith("lib") ? libName : "lib" + libName
        };

        for (String name : possibleNames) {
            File libFile = new File(usrLib, name);
            if (libFile.exists()) {
                try {
                    Log.d(TAG, "Cargando por ruta absoluta: " + libFile.getAbsolutePath());
                    System.load(libFile.getAbsolutePath());
                    return true;
                } catch (Throwable t) {
                    Log.w(TAG, "Fallo carga por ruta (" + libFile.getAbsolutePath() + "): " + t.getMessage());
                }
            }
        }
        return false;
    }
}
