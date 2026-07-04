package com.diamon.civil.engine;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class TerminalCommandExecutor {

    private final File rootDir;
    private File currentDir;

    public TerminalCommandExecutor(File rootDir) {
        this.rootDir = rootDir;
        this.currentDir = rootDir;
    }

    public File getCurrentDir() {
        return currentDir;
    }

    public String execute(String commandLine) {
        if (commandLine == null || commandLine.trim().isEmpty()) return "";

        String[] parts = commandLine.trim().split("\\s+");
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "ls":
                return listFiles(parts);
            case "mkdir":
                return makeDirectory(parts);
            case "rm":
                return removeFile(parts);
            case "cd":
                return changeDirectory(parts);
            case "pwd":
                return getRelativePath(currentDir);
            case "test-gmsh":
                return runGmshBooleanTest();
            case "gmsh":
                return null; // Delegate to binary executor in MainActivity
            case "help":
                return "Available Commands: ls, mkdir, rm, cd, pwd, test-gmsh, gmsh, clear, help\nOr enter an .inp job name to run CalculiX.";
            default:
                return null; // Delegate
        }
    }

    private String runGmshBooleanTest() {
        File geoFile = new File(currentDir, "prueba_booleana.geo");
        String script = "// Activar el motor OpenCASCADE\n" +
                "SetFactory(\"OpenCASCADE\");\n\n" +
                "// Crear un cilindro (Radio 2, Altura 5)\n" +
                "Cylinder(1) = {0, 0, 0, 0, 0, 5, 2};\n\n" +
                "// Crear una esfera en el medio del cilindro (Radio 1.5)\n" +
                "Sphere(2) = {0, 0, 2.5, 1.5};\n\n" +
                "// Operacion Booleana: Restar la Esfera del Cilindro\n" +
                "BooleanDifference(3) = { Volume{1}; Delete; } { Volume{2}; Delete; };\n\n" +
                "// Ajustar el tamano general de los tetraedros\n" +
                "Mesh.MeshSizeMax = 0.5;\n";

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(geoFile)) {
            fos.write(script.getBytes());
            return "Script 'prueba_booleana.geo' created.\nRun 'gmsh prueba_booleana.geo -3 -format inp -o cilindro_hueco.inp' to generate the mesh.";
        } catch (IOException e) {
            return "Error creating script: " + e.getMessage();
        }
    }

    private String listFiles(String[] parts) {
        File dir = currentDir;
        if (parts.length > 1) {
            dir = new File(currentDir, parts[1]);
        }

        if (!dir.exists()) return "Error: Path not found: " + dir.getName();
        if (!dir.isDirectory()) return "Error: Not a directory: " + dir.getName();

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) return "(empty)";

        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        StringBuilder sb = new StringBuilder();
        for (File f : files) {
            sb.append(f.isDirectory() ? "[DIR] " : "      ").append(f.getName()).append("\n");
        }
        return sb.toString().trim();
    }

    private String makeDirectory(String[] parts) {
        if (parts.length < 2) return "Usage: mkdir <name>";
        File newDir = new File(currentDir, parts[1]);
        if (newDir.exists()) return "Error: Already exists: " + parts[1];
        if (newDir.mkdirs()) return "Directory created: " + parts[1];
        return "Error creating directory: " + parts[1];
    }

    private String removeFile(String[] parts) {
        if (parts.length < 2) return "Usage: rm <file> or rm -rf <folder>";
        
        boolean recursive = false;
        String targetName;
        
        if (parts[1].equals("-rf") && parts.length > 2) {
            recursive = true;
            targetName = parts[2];
        } else {
            targetName = parts[1];
        }

        File target = new File(currentDir, targetName);
        if (!target.exists()) return "Error: Not found: " + targetName;

        if (recursive) {
            if (deleteRecursive(target)) return "Deleted recursively: " + targetName;
            return "Error deleting: " + targetName;
        } else {
            if (target.isDirectory()) return "Error: Is a directory (use rm -rf)";
            if (target.delete()) return "Deleted: " + targetName;
            return "Error deleting file: " + targetName;
        }
    }

    private String changeDirectory(String[] parts) {
        if (parts.length < 2) {
            currentDir = rootDir;
            return "Current: /";
        }

        String path = parts[1];
        File newDir;
        
        if (path.equals("/")) {
            newDir = rootDir;
        } else if (path.equals("..")) {
            newDir = currentDir.getParentFile();
        } else {
            newDir = new File(currentDir, path);
        }

        if (newDir == null || !newDir.exists() || !newDir.isDirectory()) {
            return "Error: Invalid directory: " + path;
        }

        // Sandbox check: Prevent escaping rootDir
        try {
            String rootPath = rootDir.getCanonicalPath();
            String newPath = newDir.getCanonicalPath();
            if (!newPath.startsWith(rootPath)) {
                currentDir = rootDir;
                return "Current: /";
            }
        } catch (IOException e) {
            return "Traversal Error: " + e.getMessage();
        }

        currentDir = newDir;
        return "Current: " + getRelativePath(currentDir);
    }

    private String getRelativePath(File dir) {
        try {
            String rootPath = rootDir.getCanonicalPath();
            String dirPath = dir.getCanonicalPath();
            if (rootPath.equals(dirPath)) return "/";
            if (dirPath.startsWith(rootPath)) {
                return dirPath.substring(rootPath.length());
            }
            return dir.getName();
        } catch (IOException e) {
            return "/";
        }
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }
}
