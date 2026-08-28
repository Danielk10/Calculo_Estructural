package com.diamon.civil.core.util;

import android.util.Log;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utility for loading native libraries with normalized names.
 * Maps internal names (e.g. TKMath) to physical Android names (e.g. libTKMath.so).
 */
public class NativeLoader {
    private static final String TAG = "NativeLoader";
    private static final Map<String, String> LIBRARY_MAP = new HashMap<>();
    private static final Set<String> LOADED_LIBRARIES = new HashSet<>();
    private static final Map<String, String> LOAD_FAILURES = new HashMap<>();

    static {
        // Mapping based on DOCUMENTACION_RENOMBRADO_BINARIOS.md and REPORTE_ANALISIS_DEPENDENCIAS.md
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
        
        // Special cases of normalized versions for Android compatibility
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

    /**
     * Loads one library and reports the real result.  The previous implementation
     * only logged an error and pretended that the native layer was ready; the next
     * JNI invocation then terminated the application with an uncaught LinkageError.
     */
    public static synchronized boolean loadLibrary(String libName) {
        String physicalName = LIBRARY_MAP.getOrDefault(libName, libName);
        if (LOADED_LIBRARIES.contains(physicalName)) {
            return true;
        }

        try {
            Log.d(TAG, "Loading native library: " + libName + " (physical: " + physicalName + ")");
            System.loadLibrary(physicalName);
            LOADED_LIBRARIES.add(physicalName);
            LOAD_FAILURES.remove(libName);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Initial load failed for " + libName + " (physical: " + physicalName + "): " + t.getMessage() + ", attempting fallback by path...");
            if (loadByPath(libName, physicalName)) {
                LOADED_LIBRARIES.add(physicalName);
                LOAD_FAILURES.remove(libName);
                return true;
            }
            String detail = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            LOAD_FAILURES.put(libName, detail);
            Log.e(TAG, "CRITICAL FAILURE: Could not load " + libName + ": " + detail);
            return false;
        }
    }

    /** Loads a mandatory JNI dependency or fails before any native method is invoked. */
    public static void loadRequiredLibrary(String libName) {
        if (!loadLibrary(libName)) {
            String detail = LOAD_FAILURES.get(libName);
            throw new UnsatisfiedLinkError("Could not load " + libName +
                    (detail == null ? "" : ": " + detail));
        }
    }

    private static boolean loadByPath(String libName, String physicalName) {
        File usrLib = new File(filesDirPath, "usr/lib");
        
        // Try the exact name that might have dots (e.g. libTKMath.so.8.0.0)
        // or the _dot.so convention for libraries whose original SONAME ends with a dot.
        String[] possibleNames = {
            "lib" + physicalName + ".so",
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
                    Log.d(TAG, "Loading by absolute path: " + libFile.getAbsolutePath());
                    System.load(libFile.getAbsolutePath());
                    return true;
                } catch (Throwable t) {
                    Log.w(TAG, "Load failed by path (" + libFile.getAbsolutePath() + "): " + t.getMessage());
                }
            }
        }
        return false;
    }
}
