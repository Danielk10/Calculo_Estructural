package com.diamon.civil.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.system.Os;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * Clase mejorada para la gestion de assets y binarios nativos.
 * Sigue las politicas de Android para nombres de binarios (lib*.so)
 * y restaura los nombres originales mediante enlaces simbolicos (symlinks).
 */
public class AssetHelper {
    private static final String TAG = "AssetHelper";
    private static final String PREFS_NAME = "AssetHelperPrefs";
    private static final String KEY_EXTRACTED = "assets_extracted_v5"; // Incrementada version por cambios en nomenclatura
    private static final int BUFFER_SIZE = 8192;

    private final Context context;

    public AssetHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Asegura que el entorno de ejecucion este listo:
     * 1. Extrae la carpeta 'usr' de assets a la memoria interna.
     * 2. Crea los enlaces simbolicos necesarios para los binarios nativos.
     */
    public synchronized boolean ensureRuntimeReady() {
        File usrDir = new File(context.getFilesDir(), "usr");
        boolean alreadyExtracted = areAssetsExtracted();

        if (!alreadyExtracted) {
            Log.i(TAG, "Extrayendo assets por primera vez o actualizacion...");
            if (!extractAssets("data/data/com.diamon.civil/files/usr", usrDir)) {
                return false;
            }
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_EXTRACTED, true).apply();
        }

        return ensureNativeToolLinks();
    }

    public boolean areAssetsExtracted() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean flagged = prefs.getBoolean(KEY_EXTRACTED, false);
        File libDir = new File(context.getFilesDir(), "usr/lib");
        return flagged && libDir.exists();
    }

    /**
     * Extrae recursivamente archivos desde assets.
     */
    private boolean extractAssets(String assetPath, File destDir) {
        AssetManager assetManager = context.getAssets();
        try {
            String[] files = assetManager.list(assetPath);
            if (files == null || files.length == 0) {
                // Es un archivo
                return copyAssetFile(assetManager, assetPath, destDir);
            } else {
                // Es un directorio
                if (!destDir.exists() && !destDir.mkdirs()) {
                    return false;
                }
                for (String fileName : files) {
                    if (fileName == null || fileName.isEmpty()) continue;
                    String childAssetPath = assetPath + "/" + fileName;
                    File childDestDir = new File(destDir, fileName);
                    
                    // Recursividad para subdirectorios
                    String[] subFiles = assetManager.list(childAssetPath);
                    if (subFiles != null && subFiles.length > 0) {
                        if (!extractAssets(childAssetPath, childDestDir)) {
                            return false;
                        }
                    } else {
                        if (!copyAssetFile(assetManager, childAssetPath, destDir)) {
                            return false;
                        }
                    }
                }
                return true;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error extrayendo assets: " + e.getMessage());
            return false;
        }
    }

    private boolean copyAssetFile(AssetManager assetManager, String assetPath, File destDir) {
        String fileName = assetPath.substring(assetPath.lastIndexOf('/') + 1);
        File destFile = new File(destDir, fileName);
        
        if (destFile.exists()) return true;

        if (!destDir.exists() && !destDir.mkdirs()) return false;

        try (InputStream in = assetManager.open(assetPath);
             OutputStream out = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error copiando " + assetPath + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Crea enlaces simbolicos desde la carpeta jniLibs (donde Android guarda los .so)
     * hacia la estructura de carpetas 'usr/bin' y 'usr/lib' esperada por las herramientas.
     */
    private boolean ensureNativeToolLinks() {
        File filesDir = context.getFilesDir();
        File usrBin = new File(filesDir, "usr/bin");
        File usrLib = new File(filesDir, "usr/lib");

        if (!usrBin.exists()) usrBin.mkdirs();
        if (!usrLib.exists()) usrLib.mkdirs();

        boolean ok = true;
        
        // --- System libz.so.1 workaround ---
        File systemLibz = new File("/system/lib64/libz.so");
        if (!systemLibz.exists()) systemLibz = new File("/system/lib/libz.so");
        if (systemLibz.exists()) {
            ok &= linkToSystem(new File(usrLib, "libz.so.1"), systemLibz);
        }

        // --- ENLACES GENERADOS SEGUN MAPEO DE BINARIOS ---
        ok &= linkTool(new File(usrBin, "DRAWEXE"), "libDRAWEXE.so");
        ok &= linkTool(new File(usrBin, "DRAWEXE-8.0.0"), "libDRAWEXE_8.0.0.so");
        ok &= linkTool(new File(usrBin, "ccx"), "libccx.so");
        ok &= linkTool(new File(usrBin, "gmsh"), "libgmsh.so");
        ok &= linkTool(new File(usrLib, "libEGL.so.1"), "libEGL_v1.so");
        ok &= linkTool(new File(usrLib, "libGLESv2.so.2"), "libGLESv2_v2.so");
        ok &= linkTool(new File(usrLib, "libGLdispatch.so.0"), "libGLdispatch_v0.so");
        ok &= linkTool(new File(usrLib, "libTKBO.so"), "libTKBO.so");
        ok &= linkTool(new File(usrLib, "libTKBO.so."), "libTKBO.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKBO.so.8.0.0"), "libTKBO_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKBRep.so"), "libTKBRep.so");
        ok &= linkTool(new File(usrLib, "libTKBRep.so."), "libTKBRep.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKBRep.so.8.0.0"), "libTKBRep_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKBin.so"), "libTKBin.so");
        ok &= linkTool(new File(usrLib, "libTKBin.so."), "libTKBin.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKBin.so.8.0.0"), "libTKBin_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKBinL.so"), "libTKBinL.so");
        ok &= linkTool(new File(usrLib, "libTKBinL.so."), "libTKBinL.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKBinL.so.8.0.0"), "libTKBinL_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKBinTObj.so"), "libTKBinTObj.so");
        ok &= linkTool(new File(usrLib, "libTKBinTObj.so."), "libTKBinTObj.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKBinTObj.so.8.0.0"), "libTKBinTObj_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKBinXCAF.so"), "libTKBinXCAF.so");
        ok &= linkTool(new File(usrLib, "libTKBinXCAF.so."), "libTKBinXCAF.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKBinXCAF.so.8.0.0"), "libTKBinXCAF_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKBool.so"), "libTKBool.so");
        ok &= linkTool(new File(usrLib, "libTKBool.so."), "libTKBool.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKBool.so.8.0.0"), "libTKBool_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKCAF.so"), "libTKCAF.so");
        ok &= linkTool(new File(usrLib, "libTKCAF.so."), "libTKCAF.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKCAF.so.8.0.0"), "libTKCAF_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKCDF.so"), "libTKCDF.so");
        ok &= linkTool(new File(usrLib, "libTKCDF.so."), "libTKCDF.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKCDF.so.8.0.0"), "libTKCDF_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKDCAF.so"), "libTKDCAF.so");
        ok &= linkTool(new File(usrLib, "libTKDCAF.so."), "libTKDCAF.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKDCAF.so.8.0.0"), "libTKDCAF_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKDE.so"), "libTKDE.so");
        ok &= linkTool(new File(usrLib, "libTKDE.so."), "libTKDE.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKDE.so.8.0.0"), "libTKDE_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKDECascade.so"), "libTKDECascade.so");
        ok &= linkTool(new File(usrLib, "libTKDECascade.so."), "libTKDECascade.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKDECascade.so.8.0.0"), "libTKDECascade_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKDEIGES.so"), "libTKDEIGES.so");
        ok &= linkTool(new File(usrLib, "libTKDEIGES.so."), "libTKDEIGES.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKDEIGES.so.8.0.0"), "libTKDEIGES_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKDEOBJ.so"), "libTKDEOBJ.so");
        ok &= linkTool(new File(usrLib, "libTKDEOBJ.so."), "libTKDEOBJ.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKDEOBJ.so.8.0.0"), "libTKDEOBJ_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKDEPLY.so"), "libTKDEPLY.so");
        ok &= linkTool(new File(usrLib, "libTKDEPLY.so."), "libTKDEPLY.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKDEPLY.so.8.0.0"), "libTKDEPLY_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKDESTEP.so"), "libTKDESTEP.so");
        ok &= linkTool(new File(usrLib, "libTKDESTEP.so."), "libTKDESTEP.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKDESTEP.so.8.0.0"), "libTKDESTEP_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKDESTL.so"), "libTKDESTL.so");
        ok &= linkTool(new File(usrLib, "libTKDESTL.so."), "libTKDESTL.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKDESTL.so.8.0.0"), "libTKDESTL_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKDEVRML.so"), "libTKDEVRML.so");
        ok &= linkTool(new File(usrLib, "libTKDEVRML.so."), "libTKDEVRML.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKDEVRML.so.8.0.0"), "libTKDEVRML_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKDraw.so"), "libTKDraw.so");
        ok &= linkTool(new File(usrLib, "libTKDraw.so."), "libTKDraw.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKDraw.so.8.0.0"), "libTKDraw_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKExpress.so"), "libTKExpress.so");
        ok &= linkTool(new File(usrLib, "libTKExpress.so."), "libTKExpress.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKExpress.so.8.0.0"), "libTKExpress_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKFeat.so"), "libTKFeat.so");
        ok &= linkTool(new File(usrLib, "libTKFeat.so."), "libTKFeat.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKFeat.so.8.0.0"), "libTKFeat_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKFillet.so"), "libTKFillet.so");
        ok &= linkTool(new File(usrLib, "libTKFillet.so."), "libTKFillet.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKFillet.so.8.0.0"), "libTKFillet_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKG2d.so"), "libTKG2d.so");
        ok &= linkTool(new File(usrLib, "libTKG2d.so."), "libTKG2d.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKG2d.so.8.0.0"), "libTKG2d_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKG3d.so"), "libTKG3d.so");
        ok &= linkTool(new File(usrLib, "libTKG3d.so."), "libTKG3d.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKG3d.so.8.0.0"), "libTKG3d_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKGeomAlgo.so"), "libTKGeomAlgo.so");
        ok &= linkTool(new File(usrLib, "libTKGeomAlgo.so."), "libTKGeomAlgo.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKGeomAlgo.so.8.0.0"), "libTKGeomAlgo_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKGeomBase.so"), "libTKGeomBase.so");
        ok &= linkTool(new File(usrLib, "libTKGeomBase.so."), "libTKGeomBase.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKGeomBase.so.8.0.0"), "libTKGeomBase_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKHLR.so"), "libTKHLR.so");
        ok &= linkTool(new File(usrLib, "libTKHLR.so."), "libTKHLR.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKHLR.so.8.0.0"), "libTKHLR_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKHelix.so"), "libTKHelix.so");
        ok &= linkTool(new File(usrLib, "libTKHelix.so."), "libTKHelix.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKHelix.so.8.0.0"), "libTKHelix_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKLCAF.so"), "libTKLCAF.so");
        ok &= linkTool(new File(usrLib, "libTKLCAF.so."), "libTKLCAF.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKLCAF.so.8.0.0"), "libTKLCAF_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKMath.so"), "libTKMath.so");
        ok &= linkTool(new File(usrLib, "libTKMath.so."), "libTKMath.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKMath.so.8.0.0"), "libTKMath_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKMesh.so"), "libTKMesh.so");
        ok &= linkTool(new File(usrLib, "libTKMesh.so."), "libTKMesh.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKMesh.so.8.0.0"), "libTKMesh_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKMeshVS.so"), "libTKMeshVS.so");
        ok &= linkTool(new File(usrLib, "libTKMeshVS.so."), "libTKMeshVS.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKMeshVS.so.8.0.0"), "libTKMeshVS_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKOffset.so"), "libTKOffset.so");
        ok &= linkTool(new File(usrLib, "libTKOffset.so."), "libTKOffset.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKOffset.so.8.0.0"), "libTKOffset_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKOpenGles.so"), "libTKOpenGles.so");
        ok &= linkTool(new File(usrLib, "libTKOpenGles.so."), "libTKOpenGles.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKOpenGles.so.8.0.0"), "libTKOpenGles_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKOpenGlesTest.so"), "libTKOpenGlesTest.so");
        ok &= linkTool(new File(usrLib, "libTKOpenGlesTest.so."), "libTKOpenGlesTest.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKOpenGlesTest.so.8.0.0"), "libTKOpenGlesTest_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKPrim.so"), "libTKPrim.so");
        ok &= linkTool(new File(usrLib, "libTKPrim.so."), "libTKPrim.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKPrim.so.8.0.0"), "libTKPrim_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKQADraw.so"), "libTKQADraw.so");
        ok &= linkTool(new File(usrLib, "libTKQADraw.so."), "libTKQADraw.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKQADraw.so.8.0.0"), "libTKQADraw_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKRWMesh.so"), "libTKRWMesh.so");
        ok &= linkTool(new File(usrLib, "libTKRWMesh.so."), "libTKRWMesh.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKRWMesh.so.8.0.0"), "libTKRWMesh_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKService.so"), "libTKService.so");
        ok &= linkTool(new File(usrLib, "libTKService.so."), "libTKService.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKService.so.8.0.0"), "libTKService_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKShHealing.so"), "libTKShHealing.so");
        ok &= linkTool(new File(usrLib, "libTKShHealing.so."), "libTKShHealing.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKShHealing.so.8.0.0"), "libTKShHealing_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKStd.so"), "libTKStd.so");
        ok &= linkTool(new File(usrLib, "libTKStd.so."), "libTKStd.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKStd.so.8.0.0"), "libTKStd_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKStdL.so"), "libTKStdL.so");
        ok &= linkTool(new File(usrLib, "libTKStdL.so."), "libTKStdL.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKStdL.so.8.0.0"), "libTKStdL_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKTObj.so"), "libTKTObj.so");
        ok &= linkTool(new File(usrLib, "libTKTObj.so."), "libTKTObj.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKTObj.so.8.0.0"), "libTKTObj_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKTObjDRAW.so"), "libTKTObjDRAW.so");
        ok &= linkTool(new File(usrLib, "libTKTObjDRAW.so."), "libTKTObjDRAW.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKTObjDRAW.so.8.0.0"), "libTKTObjDRAW_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKTopAlgo.so"), "libTKTopAlgo.so");
        ok &= linkTool(new File(usrLib, "libTKTopAlgo.so."), "libTKTopAlgo.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKTopAlgo.so.8.0.0"), "libTKTopAlgo_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKTopTest.so"), "libTKTopTest.so");
        ok &= linkTool(new File(usrLib, "libTKTopTest.so."), "libTKTopTest.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKTopTest.so.8.0.0"), "libTKTopTest_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKV3d.so"), "libTKV3d.so");
        ok &= linkTool(new File(usrLib, "libTKV3d.so."), "libTKV3d.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKV3d.so.8.0.0"), "libTKV3d_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKVCAF.so"), "libTKVCAF.so");
        ok &= linkTool(new File(usrLib, "libTKVCAF.so."), "libTKVCAF.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKVCAF.so.8.0.0"), "libTKVCAF_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKViewerTest.so"), "libTKViewerTest.so");
        ok &= linkTool(new File(usrLib, "libTKViewerTest.so."), "libTKViewerTest.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKViewerTest.so.8.0.0"), "libTKViewerTest_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKXCAF.so"), "libTKXCAF.so");
        ok &= linkTool(new File(usrLib, "libTKXCAF.so."), "libTKXCAF.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKXCAF.so.8.0.0"), "libTKXCAF_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKXDEDRAW.so"), "libTKXDEDRAW.so");
        ok &= linkTool(new File(usrLib, "libTKXDEDRAW.so."), "libTKXDEDRAW.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKXDEDRAW.so.8.0.0"), "libTKXDEDRAW_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKXMesh.so"), "libTKXMesh.so");
        ok &= linkTool(new File(usrLib, "libTKXMesh.so."), "libTKXMesh.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKXMesh.so.8.0.0"), "libTKXMesh_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKXSBase.so"), "libTKXSBase.so");
        ok &= linkTool(new File(usrLib, "libTKXSBase.so."), "libTKXSBase.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKXSBase.so.8.0.0"), "libTKXSBase_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAW.so"), "libTKXSDRAW.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAW.so."), "libTKXSDRAW.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAW.so.8.0.0"), "libTKXSDRAW_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWDE.so"), "libTKXSDRAWDE.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWDE.so."), "libTKXSDRAWDE.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWDE.so.8.0.0"), "libTKXSDRAWDE_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWIGES.so"), "libTKXSDRAWIGES.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWIGES.so."), "libTKXSDRAWIGES.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWIGES.so.8.0.0"), "libTKXSDRAWIGES_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWOBJ.so"), "libTKXSDRAWOBJ.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWOBJ.so."), "libTKXSDRAWOBJ.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWOBJ.so.8.0.0"), "libTKXSDRAWOBJ_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWPLY.so"), "libTKXSDRAWPLY.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWPLY.so."), "libTKXSDRAWPLY.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWPLY.so.8.0.0"), "libTKXSDRAWPLY_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWSTEP.so"), "libTKXSDRAWSTEP.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWSTEP.so."), "libTKXSDRAWSTEP.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWSTEP.so.8.0.0"), "libTKXSDRAWSTEP_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWSTL.so"), "libTKXSDRAWSTL.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWSTL.so."), "libTKXSDRAWSTL.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWSTL.so.8.0.0"), "libTKXSDRAWSTL_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWVRML.so"), "libTKXSDRAWVRML.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWVRML.so."), "libTKXSDRAWVRML.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKXSDRAWVRML.so.8.0.0"), "libTKXSDRAWVRML_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKXml.so"), "libTKXml.so");
        ok &= linkTool(new File(usrLib, "libTKXml.so."), "libTKXml.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKXml.so.8.0.0"), "libTKXml_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKXmlL.so"), "libTKXmlL.so");
        ok &= linkTool(new File(usrLib, "libTKXmlL.so."), "libTKXmlL.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKXmlL.so.8.0.0"), "libTKXmlL_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKXmlTObj.so"), "libTKXmlTObj.so");
        ok &= linkTool(new File(usrLib, "libTKXmlTObj.so."), "libTKXmlTObj.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKXmlTObj.so.8.0.0"), "libTKXmlTObj_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKXmlXCAF.so"), "libTKXmlXCAF.so");
        ok &= linkTool(new File(usrLib, "libTKXmlXCAF.so."), "libTKXmlXCAF.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKXmlXCAF.so.8.0.0"), "libTKXmlXCAF_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libTKernel.so"), "libTKernel.so");
        ok &= linkTool(new File(usrLib, "libTKernel.so."), "libTKernel.so_dot.so");
        ok &= linkTool(new File(usrLib, "libTKernel.so.8.0.0"), "libTKernel_v8_0_0.so");
        ok &= linkTool(new File(usrLib, "libX11.so"), "libX11.so");
        ok &= linkTool(new File(usrLib, "libXau.so"), "libXau.so");
        ok &= linkTool(new File(usrLib, "libXdmcp.so"), "libXdmcp.so");
        ok &= linkTool(new File(usrLib, "libXext.so"), "libXext.so");
        ok &= linkTool(new File(usrLib, "libXft.so"), "libXft.so");
        ok &= linkTool(new File(usrLib, "libXrender.so"), "libXrender.so");
        ok &= linkTool(new File(usrLib, "libXss.so"), "libXss.so");
        ok &= linkTool(new File(usrLib, "libandroid-support.so"), "libandroid-support.so");
        ok &= linkTool(new File(usrLib, "libbrotlicommon.so"), "libbrotlicommon.so");
        ok &= linkTool(new File(usrLib, "libbrotlidec.so"), "libbrotlidec.so");
        ok &= linkTool(new File(usrLib, "libbz2.so.1.0"), "libbz2_v1_0.so");
        ok &= linkTool(new File(usrLib, "libc++_shared.so"), "libc++_shared.so");
        ok &= linkTool(new File(usrLib, "libexpat.so.1"), "libexpat_v1.so");
        ok &= linkTool(new File(usrLib, "libfontconfig.so"), "libfontconfig.so");
        ok &= linkTool(new File(usrLib, "libfreeimage.so.3"), "libfreeimage_v3.so");
        ok &= linkTool(new File(usrLib, "libfreetype.so"), "libfreetype.so");
        ok &= linkTool(new File(usrLib, "libfreetype.so.6"), "libfreetype_v6.so");
        ok &= linkTool(new File(usrLib, "libgmp.so"), "libgmp.so");
        ok &= linkTool(new File(usrLib, "libgmsh.so"), "libgmsh.so");
        ok &= linkTool(new File(usrLib, "libgmsh.so.5.0"), "libgmsh_v5_0.so");
        ok &= linkTool(new File(usrLib, "libgmsh.so.5.0.0"), "libgmsh_v5_0_0.so");
        ok &= linkTool(new File(usrLib, "libhdf5.so"), "libhdf5.so");
        ok &= linkTool(new File(usrLib, "libhdf5.so.1000"), "libhdf5_v1000.so");
        ok &= linkTool(new File(usrLib, "libhdf5.so.1000.0.0"), "libhdf5_v1000_0_0.so");
        ok &= linkTool(new File(usrLib, "libhdf5_hl.so"), "libhdf5_hl.so");
        ok &= linkTool(new File(usrLib, "libhdf5_hl.so.1000"), "libhdf5_hl_v1000.so");
        ok &= linkTool(new File(usrLib, "libhdf5_hl.so.1000.0.0"), "libhdf5_hl_v1000_0_0.so");
        ok &= linkTool(new File(usrLib, "libmed.so"), "libmed.so");
        ok &= linkTool(new File(usrLib, "libmed.so.14"), "libmed_v14.so");
        ok &= linkTool(new File(usrLib, "libmed.so.14.0.1"), "libmed_v14_0_1.so");
        ok &= linkTool(new File(usrLib, "libmedC.so"), "libmedC.so");
        ok &= linkTool(new File(usrLib, "libmedC.so.14"), "libmedC_v14.so");
        ok &= linkTool(new File(usrLib, "libmedC.so.14.0.1"), "libmedC_v14_0_1.so");
        ok &= linkTool(new File(usrLib, "libmedfwrap.so"), "libmedfwrap.so");
        ok &= linkTool(new File(usrLib, "libmedfwrap.so.14"), "libmedfwrap_v14.so");
        ok &= linkTool(new File(usrLib, "libmedfwrap.so.14.0.1"), "libmedfwrap_v14_0_1.so");
        ok &= linkTool(new File(usrLib, "libmedimport.so"), "libmedimport.so");
        ok &= linkTool(new File(usrLib, "libmedimport.so.0"), "libmedimport_v0.so");
        ok &= linkTool(new File(usrLib, "libmedimport.so.0.4.3"), "libmedimport_v0_4_3.so");
        ok &= linkTool(new File(usrLib, "libopenblas.so"), "libopenblas.so");
        ok &= linkTool(new File(usrLib, "libopenblas.so.0"), "libopenblas_v0.so");
        ok &= linkTool(new File(usrLib, "libopenblasp-r0.3.33.dev.so"), "libopenblasp-r0.3.33.dev.so");
        ok &= linkTool(new File(usrLib, "libpng16.so"), "libpng16.so");
        ok &= linkTool(new File(usrLib, "libtcl8.6.so"), "libtcl8.6.so");
        ok &= linkTool(new File(usrLib, "libtk8.6.so"), "libtk8.6.so");
        ok &= linkTool(new File(usrLib, "libxcb.so"), "libxcb.so");
        ok &= linkTool(new File(usrLib, "libz.so.1"), "libz_v1.so");
        ok &= linkTool(new File(usrLib, "libz.so.1.3.2"), "libz_v1_3_2.so");
        ok &= linkTool(new File(usrBin, "mdump2"), "libmdump2.so");
        ok &= linkTool(new File(usrBin, "mdump3"), "libmdump3.so");
        ok &= linkTool(new File(usrBin, "mdump4"), "libmdump4.so");
        ok &= linkTool(new File(usrBin, "medconforme"), "libmedconforme.so");
        ok &= linkTool(new File(usrBin, "medimport"), "libmedimport.so");
        ok &= linkTool(new File(usrBin, "tclsh"), "libtclsh.so");
        ok &= linkTool(new File(usrBin, "tclsh8.6"), "libtclsh8.6.so");
        ok &= linkTool(new File(usrBin, "wish"), "libwish.so");
        ok &= linkTool(new File(usrBin, "wish8.6"), "libwish8.6.so");

        return ok;
    }

    /**
     * Crea un enlace simbolico seguro.
     * @param linkPath Ruta donde se creara el enlace (ej: usr/bin/ccx)
     * @param nativeLibName Nombre del archivo real en jniLibs (ej: libccx.so)
     */
    private boolean linkTool(File linkPath, String nativeLibName) {
        File nativeLibDir = new File(context.getApplicationInfo().nativeLibraryDir);
        File target = new File(nativeLibDir, nativeLibName);

        if (!target.exists()) {
            Log.e(TAG, "Libreria nativa faltante en jniLibs: " + nativeLibName);
            return false;
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (linkPath.exists() || Files.isSymbolicLink(linkPath.toPath())) {
                    linkPath.delete();
                }
            } else {
                linkPath.delete();
            }
            
            Os.symlink(target.getAbsolutePath(), linkPath.getAbsolutePath());
            Log.d(TAG, "Symlink creado: " + linkPath.getAbsolutePath() + " -> " + target.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Fallo symlink para " + linkPath.getName() + ", intentando copia: " + e.getMessage());
            return copyFile(target, linkPath);
        }
    }

    private boolean linkToSystem(File linkPath, File target) {
        try {
            if (linkPath.exists()) linkPath.delete();
            Os.symlink(target.getAbsolutePath(), linkPath.getAbsolutePath());
            return true;
        } catch (Exception e) {
            return copyFile(target, linkPath);
        }
    }

    private boolean copyFile(File source, File dest) {
        try (InputStream in = new java.io.FileInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            // Asegurar permisos de ejecucion si es binario
            if (dest.getParentFile() != null && dest.getParentFile().getName().equals("bin")) {
                dest.setExecutable(true, true);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error en fallback de copia: " + e.getMessage());
            return false;
        }
    }
}
