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

public class AssetHelper {
    private static final String TAG = "AssetHelper";
    private static final String PREFS_NAME = "AssetHelperPrefs";
    private static final String KEY_EXTRACTED = "assets_extracted_v4";
    private static final int BUFFER_SIZE = 8192;

    private final Context context;

    public AssetHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized boolean ensureRuntimeReady() {
        File usrDir = new File(context.getFilesDir(), "usr");
        boolean alreadyExtracted = areAssetsExtracted();

        if (!alreadyExtracted) {
            if (!extractAssets("data/data/com.diamon.civil/files/usr", usrDir)) {
                return false;
            }
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_EXTRACTED, true).apply();
        }

        boolean ok = ensureNativeToolLinks();

        // Copy test.inp to root for convenience
        File testInp = new File(context.getFilesDir(), "usr/test.inp");
        File destTestInp = new File(context.getFilesDir(), "test.inp");
        if (testInp.exists() && !destTestInp.exists()) {
            copyFile(testInp, destTestInp);
        }

        return ok;
    }

    public boolean areAssetsExtracted() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean flagged = prefs.getBoolean(KEY_EXTRACTED, false);
        File libDir = new File(context.getFilesDir(), "usr/lib");
        return flagged && libDir.exists();
    }

    private boolean extractAssets(String assetPath, File destDir) {
        AssetManager assetManager = context.getAssets();
        try {
            String[] files = assetManager.list(assetPath);
            if (files == null || files.length == 0) {
                return copyAssetFile(assetManager, assetPath, destDir);
            } else {
                if (!destDir.exists() && !destDir.mkdirs()) {
                    return false;
                }
                for (String fileName : files) {
                    if (fileName == null || fileName.isEmpty()) continue;
                    String childAssetPath = assetPath + "/" + fileName;
                    File childDestDir = new File(destDir, fileName);
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

    private boolean ensureNativeToolLinks() {
        File filesDir = context.getFilesDir();
        File nativeLibDir = new File(context.getApplicationInfo().nativeLibraryDir);
        File usrBin = new File(filesDir, "usr/bin");
        File usrLib = new File(filesDir, "usr/lib");

        if (!usrBin.exists()) usrBin.mkdirs();
        if (!usrLib.exists()) usrLib.mkdirs();

        boolean ok = true;
        
        // System libz.so.1 workaround (CalculiX and others need it)
        File systemLibz = new File("/system/lib64/libz.so");
        if (!systemLibz.exists()) systemLibz = new File("/system/lib/libz.so");
        if (systemLibz.exists()) {
            ok &= linkTool(new File(usrLib, "libz.so.1"), systemLibz);
        }

        File[] nativeLibs = nativeLibDir.listFiles();
        if (nativeLibs != null) {
            for (File libFile : nativeLibs) {
                String name = libFile.getName();
                
                // Binaries
                if (name.equals("libccx.so")) {
                    ok &= linkTool(new File(usrBin, "ccx"), libFile);
                    continue;
                }
                if (name.equals("libgmsh_bin.so")) {
                    ok &= linkTool(new File(usrBin, "gmsh"), libFile);
                    continue;
                }

                // General Library Linking
                ok &= linkTool(new File(usrLib, name), libFile);

                // Compatibility mappings: create versioned symlinks for known libraries
                if (name.equals("libTKBO._so_.so")) {
                    ok &= linkTool(new File(usrLib, "libTKBO.so."), libFile);
                    ok &= linkTool(new File(usrLib, "libTKBO.so"), libFile);
                } else if (name.startsWith("libTK") && name.endsWith("._so_.so")) {
                    // e.g. libTKXXX._so_.so -> libTKXXX.so.
                    String realName = name.replace("._so_.so", ".so.");
                    ok &= linkTool(new File(usrLib, realName), libFile);
                    ok &= linkTool(new File(usrLib, realName.substring(0, realName.length() - 1)), libFile);
                } else if (name.startsWith("libTK") && name.endsWith(".so")) {
                    // OCCT SONAMEs often have a trailing dot
                    ok &= linkTool(new File(usrLib, name + "."), libFile);
                } else if (name.equals("libopenblas_so_0.so")) {
                    ok &= linkTool(new File(usrLib, "libopenblas.so.0"), libFile);
                } else if (name.equals("libopenblasp_r0_3_33_dev.so")) {
                    ok &= linkTool(new File(usrLib, "libopenblasp-r0.3.33.dev.so"), libFile);
                } else if (name.equals("libgmsh.so")) {
                    ok &= linkTool(new File(usrLib, "libgmsh.so.5.0"), libFile);
                } else if (name.equals("libgmsh.so.5.0")) {
                    ok &= linkTool(new File(usrLib, "libgmsh.so.5.0"), libFile);
                } else if (name.equals("libbz2_so_1_0.so")) {
                    ok &= linkTool(new File(usrLib, "libbz2.so.1.0"), libFile);
                } else if (name.equals("libz_so_1.so")) {
                    ok &= linkTool(new File(usrLib, "libz.so.1"), libFile);
                } else if (name.equals("libfreeimage.so")) {
                    ok &= linkTool(new File(usrLib, "libfreeimage.so.3"), libFile);
                } else if (name.equals("libexpat.so")) {
                    ok &= linkTool(new File(usrLib, "libexpat.so.1"), libFile);
                } else if (name.equals("libtcl8_6.so")) {
                    ok &= linkTool(new File(usrLib, "libtcl8.6.so"), libFile);
                } else if (name.equals("libtk8_6.so")) {
                    ok &= linkTool(new File(usrLib, "libtk8.6.so"), libFile);
                } else if (name.equals("libgmsh.so.5.0.0")) {
                    ok &= linkTool(new File(usrLib, "libgmsh.so.5.0.0"), libFile);
                } else if (name.equals("libtclsh.so")) {
                    ok &= linkTool(new File(usrBin, "tclsh"), libFile);
                } else if (name.equals("libwish.so")) {
                    ok &= linkTool(new File(usrBin, "wish"), libFile);
                } else if (name.equals("libDRAWEXE.so")) {
                    ok &= linkTool(new File(usrBin, "DRAWEXE"), libFile);
                }
            }
        }

        return ok;
    }

    private boolean linkTool(File linkPath, File target) {
        if (!target.exists()) {
            Log.e(TAG, "Libreria nativa faltante: " + target.getAbsolutePath());
            return false;
        }

        try {
            if (linkPath.exists() && linkPath.getCanonicalPath().equals(target.getCanonicalPath())) {
                return true;
            }
            linkPath.delete();
            Os.symlink(target.getAbsolutePath(), linkPath.getAbsolutePath());
            Log.d(TAG, "Symlink creado: " + linkPath.getAbsolutePath() + " -> " + target.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Symlink fallo para " + linkPath.getName() + " -> " + target.getName() + ": " + e.getMessage());
            // Fallback a copia si symlink falla
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
            File binParent = dest.getParentFile();
            if (binParent != null && "bin".equals(binParent.getName())) {
                dest.setExecutable(true, true);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error copiando archivo de fallback: " + e.getMessage());
            return false;
        }
    }
}
