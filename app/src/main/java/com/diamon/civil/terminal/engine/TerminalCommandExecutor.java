package com.diamon.civil.terminal.engine;

import com.diamon.civil.structural.test.simulation.SimulationTestManager;
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
            case "cat":
                return readFile(parts);
            case "touch":
                return touchFile(parts);
            case "test-gmsh":
            case "test_gmsh":
            case "test-draw":
            case "test_draw":
            case "test-occt":
            case "test_occt":
            case "test-calculix":
            case "test_calculix":
            case "test-calculix-parallel":
            case "test_calculix_parallel":
            case "test-frame":
            case "test_frame":
            case "test-portico":
            case "test_portico":
            case "test-frd-parser":
            case "test_frd_parser":
            case "test-dat-parser":
            case "test_dat_parser":
            case "test-coordinate-fallback":
            case "test_coordinate_fallback":
            case "test-step-solve":
            case "test_step_solve":
            case "test-bracket-solve":
            case "test_bracket_solve":
            case "test-cad-solve":
            case "test_cad_solve":
                return null; // Intercepted and executed fully in TerminalFragment
            case "run-sim-test":
            case "run_sim_test":
                return SimulationTestManager.runTest(rootDir, new File(System.getProperty("java.library.path")));
            case "gmsh":
            case "ccx":
            case "drawexe":
                return null; // Delegate to binary executor in TerminalFragment
            case "help":
            case "?":
                return com.diamon.civil.core.util.logging.ModuleLogger.HELP_TEXT;
            default:
                return null; // Delegate
        }
    }

    private String runGmshBooleanTest() {
        File geoFile = new File(currentDir, "boolean_test.geo");
        String script = "// Enable OpenCASCADE CAD kernel\n" +
                "SetFactory(\"OpenCASCADE\");\n\n" +
                "// Create primitive cylinder (Radius 2, Height 5)\n" +
                "Cylinder(1) = {0, 0, 0, 0, 0, 5, 2};\n\n" +
                "// Create primitive sphere at cylinder center (Radius 1.5)\n" +
                "Sphere(2) = {0, 0, 2.5, 1.5};\n\n" +
                "// Boolean Difference: Cut Sphere from Cylinder\n" +
                "BooleanDifference(3) = { Volume{1}; Delete; } { Volume{2}; Delete; };\n\n" +
                "// Set global tetrahedron mesh size\n" +
                "Mesh.MeshSizeMax = 0.5;\n";

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(geoFile)) {
            fos.write(script.getBytes());
            return "Script 'boolean_test.geo' created.\nRun 'gmsh boolean_test.geo -3 -format inp -o hollow_cylinder.inp' to generate the mesh.";
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

    private String readFile(String[] parts) {
        if (parts.length < 2) return "Usage: cat <filename>";
        File f = new File(currentDir, parts[1]);
        if (!f.exists()) return "Error: File not found: " + parts[1];
        if (f.isDirectory()) return "Error: Is a directory: " + parts[1];
        if (f.length() > 500000) return "Error: File too large to print in console (>500KB)";
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    private String touchFile(String[] parts) {
        if (parts.length < 2) return "Usage: touch <filename>";
        File f = new File(currentDir, parts[1]);
        try {
            if (f.exists()) {
                f.setLastModified(System.currentTimeMillis());
                return "Updated timestamp: " + parts[1];
            } else {
                if (f.createNewFile()) return "Created file: " + parts[1];
                return "Error creating file: " + parts[1];
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
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
