package com.diamon.civil.core.export;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class ExportManager {
    private static final String TAG = "ExportManager";
    private final Context context;

    public ExportManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean exportToDownloads(File sourceFile) {
        return exportToDownloads(sourceFile, "");
    }

    public boolean exportToDownloads(File sourceFile, String subFolder) {
        if (sourceFile == null || !sourceFile.exists()) return false;

        if (sourceFile.isDirectory()) {
            File[] children = sourceFile.listFiles();
            boolean success = true;
            if (children != null) {
                for (File child : children) {
                    if (ProjectExporter.shouldIgnore(child)) continue;
                    String nextSubFolder = (subFolder == null || subFolder.isEmpty())
                            ? sourceFile.getName()
                            : subFolder + "/" + sourceFile.getName();
                    if (!exportToDownloads(child, nextSubFolder)) {
                        success = false;
                    }
                }
            }
            return success;
        }

        String displayName = sourceFile.getName();
        String targetSubDir = "Structural_Analysis_FEA_Advanced" + 
                (subFolder != null && !subFolder.trim().isEmpty() ? "/" + subFolder.trim() : "");
        String relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + targetSubDir;

        String mimeType = "application/octet-stream";
        String nameLower = displayName.toLowerCase();
        if (nameLower.endsWith(".pdf")) {
            mimeType = "application/pdf";
        } else if (nameLower.endsWith(".txt")) {
            mimeType = "text/plain";
        } else if (nameLower.endsWith(".json")) {
            mimeType = "application/json";
        } else if (nameLower.endsWith(".stl")) {
            mimeType = "application/sla";
        } else if (nameLower.endsWith(".glb")) {
            mimeType = "model/gltf-binary";
        } else if (nameLower.endsWith(".gltf")) {
            mimeType = "model/gltf+json";
        } else {
            // For .inp, .dat, .frd, .sta, .iges, .igs, .step, .stp, .brep, .geo, etc.
            // Android MediaStore will append default suffixes (e.g. .txt for text/plain, .stl for application/sla)
            // if non-matching mime types are used. application/octet-stream preserves the exact real extension.
            mimeType = "application/octet-stream";
        }

        // Try MediaStore API on Android 10+ (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            Uri targetUri = null;
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

                // Fallback: If insert with RELATIVE_PATH fails on some ROMs, try base Downloads directory
                if (targetUri == null) {
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                }

                if (targetUri != null) {
                    try (OutputStream os = resolver.openOutputStream(targetUri, "w");
                         InputStream is = new FileInputStream(sourceFile)) {
                        if (os == null) throw new Exception("openOutputStream returned null");
                        byte[] buffer = new byte[16384];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            os.write(buffer, 0, read);
                        }
                        os.flush();
                    }

                    // Release pending state
                    values.clear();
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    resolver.update(targetUri, values, null, null);
                    Log.i(TAG, "Exported via MediaStore: " + displayName + " -> " + targetUri);
                    return true;
                }
            } catch (Throwable e) {
                Log.e(TAG, "MediaStore export failed for " + displayName + ": " + e.getMessage(), e);
                // Clean up incomplete pending file if possible
                if (targetUri != null) {
                    try { resolver.delete(targetUri, null, null); } catch (Throwable ignored) {}
                }
            }
        }

        // Direct File System export (Android 9 and below, or fallback on Android 10+)
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File targetDir = new File(downloadDir, targetSubDir);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            String baseName = displayName;
            String extension = "";
            int dotIdx = displayName.lastIndexOf('.');
            if (dotIdx > 0) {
                baseName = displayName.substring(0, dotIdx);
                extension = displayName.substring(dotIdx);
            }
            File targetFile = new File(targetDir, displayName);
            int counter = 1;
            while (targetFile.exists()) {
                targetFile = new File(targetDir, baseName + " (" + counter + ")" + extension);
                counter++;
            }

            try (InputStream is = new FileInputStream(sourceFile);
                 OutputStream os = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            Log.i(TAG, "Exported via direct File I/O: " + targetFile.getAbsolutePath());
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "Direct File export failed for " + displayName + ": " + e.getMessage(), e);
            return false;
        }
    }
}
