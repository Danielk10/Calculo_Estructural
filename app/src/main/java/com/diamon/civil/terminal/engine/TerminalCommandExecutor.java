package com.diamon.civil.terminal.engine;

import com.diamon.civil.structural.test.simulation.SimulationTestManager;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TerminalCommandExecutor {

    /** Global sandbox boundary – user cannot cd above this (e.g. context.getFilesDir()). */
    private final File globalRootDir;
    /** Home directory – initial dir and target for "cd ~" / "cd" (e.g. filesDir/terminal). */
    private final File homeDir;
    private File currentDir;

    /**
     * Constructor for global navigation.
     * @param globalRootDir  sandbox boundary (context.getFilesDir())
     * @param homeDir        initial working directory (filesDir/terminal)
     */
    public TerminalCommandExecutor(File globalRootDir, File homeDir) {
        this.globalRootDir = globalRootDir;
        this.homeDir = homeDir;
        this.currentDir = homeDir;
    }

    /**
     * Legacy single-arg constructor – root IS home (backward compatibility for tests).
     */
    public TerminalCommandExecutor(File rootDir) {
        this(rootDir, rootDir);
    }

    public File getCurrentDir() {
        return currentDir;
    }

    public File getHomeDir() {
        return homeDir;
    }

    public String execute(String commandLine) {
        if (commandLine == null || commandLine.trim().isEmpty()) return "";

        String[] parts = splitCommandLine(commandLine.trim());
        if (parts.length == 0) return "";
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
            case "cp":
                return copyFile(parts);
            case "echo":
                return executeEcho(commandLine.trim());
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
                return SimulationTestManager.runTest(homeDir, new File(System.getProperty("java.library.path")));
            case "gmsh":
            case "ccx":
            case "draw":
            case "drawexe":
            case "tcl":
            case "tclsh":
                return null; // Delegate to binary executor in TerminalFragment
            case "help":
            case "?":
                return com.diamon.civil.core.util.logging.ModuleLogger.HELP_TEXT;
            default:
                return null; // Delegate
        }
    }

    public static String[] splitCommandLine(String commandLine) {
        if (commandLine == null || commandLine.trim().isEmpty()) {
            return new String[0];
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;
        boolean escapeNext = false;

        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);

            if (escapeNext) {
                currentToken.append(c);
                escapeNext = false;
                continue;
            }

            if (c == '\\' && !inSingleQuotes) {
                escapeNext = true;
                continue;
            }

            if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                continue;
            }

            if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                continue;
            }

            if (Character.isWhitespace(c) && !inDoubleQuotes && !inSingleQuotes) {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            } else {
                currentToken.append(c);
            }
        }

        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }

        return tokens.toArray(new String[0]);
    }

    private String executeEcho(String commandLine) {
        String trimmed = commandLine.trim();
        String afterEcho = trimmed.length() > 4 ? trimmed.substring(4).trim() : "";
        if (afterEcho.isEmpty()) return "";

        boolean append = false;
        String content;
        String targetName = null;

        if (afterEcho.contains(" >> ")) {
            int idx = afterEcho.lastIndexOf(" >> ");
            content = afterEcho.substring(0, idx).trim();
            targetName = afterEcho.substring(idx + 4).trim();
            append = true;
        } else if (afterEcho.contains(" > ")) {
            int idx = afterEcho.lastIndexOf(" > ");
            content = afterEcho.substring(0, idx).trim();
            targetName = afterEcho.substring(idx + 3).trim();
            append = false;
        } else {
            content = afterEcho;
        }

        if ((content.startsWith("\"") && content.endsWith("\"")) ||
            (content.startsWith("'") && content.endsWith("'"))) {
            if (content.length() >= 2) {
                content = content.substring(1, content.length() - 1);
            }
        }
        content = content.replace("\\n", "\n");

        if (targetName != null && !targetName.isEmpty()) {
            File target;
            if (targetName.startsWith("/")) {
                target = new File(globalRootDir, targetName.substring(1));
            } else {
                target = new File(currentDir, targetName);
            }

            try {
                String rootPath = globalRootDir.getCanonicalPath();
                String targetPath = target.getCanonicalPath();
                if (!targetPath.startsWith(rootPath)) {
                    return "Error: Path escapes workspace sandbox";
                }
            } catch (IOException e) {
                return "Error resolving target: " + e.getMessage();
            }

            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try {
                if (append) {
                    java.nio.file.Files.write(target.toPath(), (content + "\n").getBytes(StandardCharsets.UTF_8),
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                    return "Appended to " + target.getName();
                } else {
                    java.nio.file.Files.write(target.toPath(), (content + "\n").getBytes(StandardCharsets.UTF_8),
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                    return "Written to " + target.getName();
                }
            } catch (IOException e) {
                return "Error writing file: " + e.getMessage();
            }
        }

        return content;
    }

    private String listFiles(String[] parts) {
        File dir = currentDir;
        if (parts.length > 1) {
            File target = resolveTarget(parts[1]);
            if (target != null) dir = target;
            else return "Error: Path not found: " + parts[1];
        }

        if (!dir.exists()) return "Error: Path not found: " + dir.getName();
        if (!dir.isDirectory()) return "Error: Not a directory: " + dir.getName();

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) return "(empty)";

        // Filter out system directories when listing the global root
        boolean isGlobalRoot;
        try {
            isGlobalRoot = dir.getCanonicalPath().equals(globalRootDir.getCanonicalPath());
        } catch (IOException e) {
            isGlobalRoot = false;
        }

        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        StringBuilder sb = new StringBuilder();
        for (File f : files) {
            // Hide system directories from listings at the global root
            if (isGlobalRoot && shouldHideFromListing(f)) continue;
            sb.append(f.isDirectory() ? "[DIR] " : "      ").append(f.getName()).append("\n");
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? "(empty)" : result;
    }

    /** Returns true for system/internal directories and files that should not be visible to the user at root. */
    private boolean shouldHideFromListing(File f) {
        String name = f.getName();
        if (name.startsWith(".")) return true;
        if (name.equalsIgnoreCase("profileInstalled") ||
            name.equalsIgnoreCase("profileinstaller_profileWrittenFor_lastUpdateTime.dat") ||
            name.toLowerCase().startsWith("profileinstaller")) {
            return true;
        }
        if (!f.isDirectory()) return false;
        return name.equals("usr") || name.equals("fake_root") || name.equals("lib") ||
               name.equals("include") || name.equals("share") || name.equals("bin") ||
               name.equals("cache") || name.equals("code_cache") || name.equals("app_webview") ||
               name.equals("databases") || name.equals("shared_prefs") || name.equals("system");
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
            // cd with no args -> go home
            currentDir = homeDir;
            return "Current: " + getRelativePath(currentDir);
        }

        String path = parts[1];
        File newDir;
        
        if (path.equals("~")) {
            newDir = homeDir;
        } else if (path.equals("/")) {
            newDir = globalRootDir;
        } else if (path.equals("..")) {
            newDir = currentDir.getParentFile();
        } else if (path.startsWith("/")) {
            // Absolute path from globalRootDir
            newDir = new File(globalRootDir, path.substring(1));
        } else {
            newDir = new File(currentDir, path);
        }

        if (newDir == null || !newDir.exists() || !newDir.isDirectory()) {
            return "Error: Invalid directory: " + path;
        }

        // Sandbox check: Prevent escaping globalRootDir
        try {
            String rootPath = globalRootDir.getCanonicalPath();
            String newPath = newDir.getCanonicalPath();
            if (!newPath.startsWith(rootPath)) {
                currentDir = globalRootDir;
                return "Current: /";
            }
        } catch (IOException e) {
            return "Traversal Error: " + e.getMessage();
        }

        currentDir = newDir;
        return "Current: " + getRelativePath(currentDir);
    }

    /**
     * Resolves a path argument relative to currentDir or as absolute from globalRoot.
     */
    private File resolveTarget(String path) {
        File target;
        if (path.startsWith("/")) {
            target = new File(globalRootDir, path.substring(1));
        } else {
            target = new File(currentDir, path);
        }
        // Sandbox check
        try {
            String rootPath = globalRootDir.getCanonicalPath();
            String targetPath = target.getCanonicalPath();
            if (!targetPath.startsWith(rootPath)) return null;
        } catch (IOException e) {
            return null;
        }
        return target.exists() ? target : null;
    }

    private String getRelativePath(File dir) {
        try {
            String rootPath = globalRootDir.getCanonicalPath();
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

    /**
     * Simple file copy: cp <source> <destination>
     * Allows copying files between module folders (e.g. from structural_analysis to terminal).
     */
    private String copyFile(String[] parts) {
        if (parts.length < 3) return "Usage: cp <source> <destination>";
        File src = new File(currentDir, parts[1]);
        if (!src.exists()) {
            // Try as absolute from root
            File absSrc = resolveTarget(parts[1]);
            if (absSrc != null) src = absSrc;
            else return "Error: Source not found: " + parts[1];
        }
        if (src.isDirectory()) return "Error: Cannot copy directories (use individual files)";
        
        File dst = new File(currentDir, parts[2]);
        if (dst.isDirectory()) {
            dst = new File(dst, src.getName());
        }
        try {
            java.nio.file.Files.copy(src.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return "Copied: " + src.getName() + " -> " + dst.getName();
        } catch (IOException e) {
            return "Error copying file: " + e.getMessage();
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
