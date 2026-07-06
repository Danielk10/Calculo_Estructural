package com.diamon.civil.util.export;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class ExportManager {
    private final Context context;

    public ExportManager(Context context) {
        this.context = context;
    }

    public boolean exportToDownloads(File sourceFile, String subFolder) {
        if (!sourceFile.exists()) return false;

        String displayName = sourceFile.getName();
        String relativePath = Environment.DIRECTORY_DOWNLOADS + "/FEA_Suite/" + subFolder;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);

                Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return false;

                try (OutputStream os = context.getContentResolver().openOutputStream(uri);
                     InputStream is = new FileInputStream(sourceFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                    }
                }
                return true;
            } else {
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File targetDir = new File(downloadDir, "FEA_Suite/" + subFolder);
                if (!targetDir.exists() && !targetDir.mkdirs()) return false;
                
                File targetFile = new File(targetDir, displayName);
                try (InputStream is = new FileInputStream(sourceFile);
                     OutputStream os = new java.io.FileOutputStream(targetFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                    }
                }
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
