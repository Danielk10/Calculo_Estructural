package com.diamon.civil.io;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileHelper {

    private final ContentResolver contentResolver;

    public FileHelper(ContentResolver contentResolver) {
        this.contentResolver = contentResolver;
    }

    public String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = contentResolver.query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) result = cursor.getString(index);
                }
            } catch (Exception ignore) {}
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) result = result.substring(cut + 1);
            }
        }
        return result;
    }

    public boolean importFile(Uri sourceUri, File destFile) {
        try (InputStream is = contentResolver.openInputStream(sourceUri);
             OutputStream os = new FileOutputStream(destFile)) {
            if (is == null) return false;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean exportText(Uri destUri, String content) {
        try (OutputStream os = contentResolver.openOutputStream(destUri)) {
            if (os == null) return false;
            os.write(content.getBytes());
            os.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean exportFile(File sourceFile, Uri destUri) {
        try (InputStream is = new FileInputStream(sourceFile);
             OutputStream os = contentResolver.openOutputStream(destUri)) {
            if (os == null) return false;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean zipFiles(File[] files, Uri destUri) {
        try (OutputStream os = contentResolver.openOutputStream(destUri);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os))) {
            if (os == null) return false;
            
            for (File file : files) {
                if (file.isDirectory() || !file.exists()) continue; 
                addToZip(file, zos);
            }
            zos.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void addToZip(File file, ZipOutputStream zos) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            ZipEntry entry = new ZipEntry(file.getName());
            zos.putNextEntry(entry);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }
            zos.closeEntry();
        }
    }

    public static boolean copyFile(File source, File dest) {
        try (InputStream is = new FileInputStream(source);
             OutputStream os = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
