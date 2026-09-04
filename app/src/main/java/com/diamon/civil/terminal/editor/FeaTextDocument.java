package com.diamon.civil.terminal.editor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FeaTextDocument {

    private String filename = "untitled.inp";
    private File currentFile;
    private final StringBuilder buffer = new StringBuilder();
    private boolean modified = false;

    public FeaTextDocument() {
    }

    public FeaTextDocument(String filename) {
        this.filename = (filename != null && !filename.trim().isEmpty()) ? filename.trim() : "untitled.inp";
    }

    public void loadFromFile(File file) throws IOException {
        if (file == null) throw new IllegalArgumentException("File cannot be null");
        this.currentFile = file;
        this.filename = file.getName();
        this.buffer.setLength(0);

        if (file.exists()) {
            byte[] bytes = new byte[(int) file.length()];
            int read = 0;
            try (FileInputStream fis = new FileInputStream(file)) {
                while (read < bytes.length) {
                    int n = fis.read(bytes, read, bytes.length - read);
                    if (n <= 0) break;
                    read += n;
                }
            }
            this.buffer.append(new String(bytes, 0, read, StandardCharsets.UTF_8));
        }
        this.modified = false;
    }

    public void saveToFile(File file) throws IOException {
        if (file == null) {
            file = this.currentFile;
        }
        if (file == null) {
            throw new IllegalArgumentException("Target file is unspecified");
        }
        this.currentFile = file;
        this.filename = file.getName();

        byte[] bytes = buffer.toString().getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream fos = new FileOutputStream(file, false)) {
            fos.write(bytes);
            fos.flush();
        }
        this.modified = false;
    }

    public void setContent(String text) {
        buffer.setLength(0);
        if (text != null) {
            buffer.append(text);
        }
        modified = true;
    }

    public String getContent() {
        return buffer.toString();
    }

    public int length() {
        return buffer.length();
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        if (filename != null && !filename.trim().isEmpty()) {
            this.filename = filename.trim();
        }
    }

    public File getCurrentFile() {
        return currentFile;
    }

    public void setCurrentFile(File file) {
        this.currentFile = file;
        if (file != null) {
            this.filename = file.getName();
        }
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public FeaTextTokenizer.SyntaxMode getSyntaxMode() {
        return FeaTextTokenizer.detectMode(filename);
    }

    /**
     * Inserts text at the specified character offset.
     * Clamps position to valid range [0, length()].
     */
    public int insert(int position, String text) {
        if (text == null || text.isEmpty()) return position;
        int pos = Math.max(0, Math.min(position, buffer.length()));
        buffer.insert(pos, text);
        modified = true;
        return pos + text.length();
    }

    /**
     * Backspace: Deletes 1 character before the specified position.
     * Returns the new cursor position.
     */
    public int backspace(int position) {
        int pos = Math.max(0, Math.min(position, buffer.length()));
        if (pos > 0) {
            buffer.deleteCharAt(pos - 1);
            modified = true;
            return pos - 1;
        }
        return 0;
    }

    /**
     * Delete: Deletes 1 character at the specified position.
     * Returns the same cursor position.
     */
    public int delete(int position) {
        int pos = Math.max(0, Math.min(position, buffer.length()));
        if (pos < buffer.length()) {
            buffer.deleteCharAt(pos);
            modified = true;
        }
        return pos;
    }

    /**
     * Returns total number of lines in the document (at least 1).
     */
    public int getLineCount() {
        if (buffer.length() == 0) return 1;
        int count = 1;
        for (int i = 0; i < buffer.length(); i++) {
            if (buffer.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    /**
     * Computes 1-indexed {line, col} for a given character offset.
     */
    public int[] getLineAndCol(int position) {
        int pos = Math.max(0, Math.min(position, buffer.length()));
        int line = 1;
        int col = 1;
        for (int i = 0; i < pos; i++) {
            if (buffer.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        return new int[]{line, col};
    }

    /**
     * Generates a newline-separated string of line numbers ("1\n2\n3...").
     */
    public String getLineNumbersText() {
        int total = getLineCount();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= total; i++) {
            if (i > 1) sb.append('\n');
            sb.append(i);
        }
        return sb.toString();
    }
}
