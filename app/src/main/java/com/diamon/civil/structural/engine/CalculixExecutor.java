package com.diamon.civil.structural.engine;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CalculixExecutor {
    private static final String TAG = "CalculixExecutor";
    private volatile File workDir;
    private final File nativeLibDir;
    private final File filesDir;

    public CalculixExecutor(Context context) {
        this(context, context.getFilesDir());
    }

    public CalculixExecutor(Context context, File workDir) {
        this.workDir = workDir;
        this.nativeLibDir = context != null && context.getApplicationInfo() != null ? new File(context.getApplicationInfo().nativeLibraryDir) : null;
        this.filesDir = context != null ? context.getFilesDir() : null;
        
        // The solver is a child process. Do not load the JNI/OCCT stack here: the
        // terminal must remain usable even when the optional 3D viewer is absent.
    }

    /** Standalone / local testing constructor without requiring Android Context. */
    public CalculixExecutor(File filesDir, File nativeLibDir, File workDir) {
        this.filesDir = filesDir;
        this.nativeLibDir = nativeLibDir;
        this.workDir = workDir;
    }

    public void setWorkDir(File workDir) {
        if (workDir != null) {
            this.workDir = workDir;
        }
    }

    public File getWorkDir() {
        return this.workDir;
    }

    public interface OutputListener {
        void onOutput(String chunk);
    }

    private volatile Process currentProcess;

    public synchronized void abort() {
        Process p = currentProcess;
        if (p != null) {
            try {
                p.destroyForcibly();
            } catch (Exception ignored) {}
            currentProcess = null;
        }
    }

    public boolean isRunning() {
        Process p = currentProcess;
        return p != null && p.isAlive();
    }

    public native boolean convertFrdToGlb(String inputPath, String outputPath, float deformationScale, boolean isSphere);

    public boolean convertFrdToGlb(String inputPath, String outputPath, float deformationScale) {
        return convertFrdToGlb(inputPath, outputPath, deformationScale, false);
    }

    public String executeCalculix(String jobName) {
        return executeCalculix(jobName, null, 0);
    }

    public String executeCalculix(String jobName, int numThreads) {
        return executeCalculix(jobName, null, numThreads);
    }

    public String executeCalculix(String jobName, OutputListener listener) {
        return executeCalculix(jobName, listener, 0);
    }

    public String executeCalculix(String jobName, OutputListener listener, int numThreads) {
        if (jobName == null || jobName.trim().isEmpty()) {
            return "Execution Error: CalculiX job name is empty";
        }
        String baseName = jobName.endsWith(".inp")
                ? jobName.substring(0, jobName.length() - ".inp".length())
                : jobName;
        File inputBase = new File(workDir, baseName);
        if (workDir != null) {
            String[] staleExtensions = {
                ".dat", ".frd", ".sta", ".cvg", ".12d", ".fcv", ".cel",
                ".eig", ".rout", ".nam"
            };
            for (String ext : staleExtensions) {
                File f = new File(workDir, baseName + ext);
                if (f.exists()) deleteFileThoroughly(f);
            }
            String[] staleSolverFiles = {
                "spooles.out", "spooles.log", "intpoints.out",
                "slavintmortar.out", "temporaryrestartfile"
            };
            for (String sName : staleSolverFiles) {
                File f = new File(workDir, sName);
                if (f.exists()) deleteFileThoroughly(f);
            }
        }
        return executeBinaryWithStreaming("ccx", listener, null, numThreads, "-i", inputBase.getAbsolutePath());
    }

    public String runGmsh(String inputPath, String outputPath, double meshSize) {
        return executeBinary("gmsh", inputPath, "-3", "-clmax", String.valueOf(meshSize), "-o", outputPath, "-format", "inp");
    }

    public String executeBinary(String binaryName, String... args) {
        return executeBinaryWithStreaming(binaryName, null, null, 0, args);
    }

    public String executeBinaryWithInput(String binaryName, String input, String... args) {
        return executeBinaryWithStreaming(binaryName, null, input, 0, args);
    }

    public String executeBinaryWithStreaming(String binaryName, OutputListener listener, String input, String... args) {
        return executeBinaryWithStreaming(binaryName, listener, input, 0, args);
    }

    public String executeBinaryWithStreaming(String binaryName, OutputListener listener, String input, int numThreads, String... args) {
        File binary = null;
        File packagedBinary = nativeLibDir != null ? new File(nativeLibDir, "lib" + binaryName + ".so") : null;
        if (packagedBinary != null && packagedBinary.exists()) {
            binary = packagedBinary;
        } else if (filesDir != null) {
            File binInFiles = new File(new File(filesDir, "usr/bin"), binaryName);
            if (binInFiles.exists()) {
                binary = binInFiles;
            }
        }

        if (binary == null || !binary.exists()) {
            File systemBinary = new File("/usr/bin", binaryName);
            if (systemBinary.exists()) {
                binary = systemBinary;
            } else {
                String home = System.getProperty("user.home", "");
                File localBinary = new File(home + "/.local/bin", binaryName);
                if (localBinary.exists()) {
                    binary = localBinary;
                }
            }
        }

        if (binary == null || !binary.exists()) {
             return "Invalid command: " + binaryName;
        }

        List<String> command = new ArrayList<>();
        command.add(binary.getAbsolutePath());
        for (String arg : args) {
            if (arg != null && !arg.isEmpty()) command.add(arg);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir);
            pb.redirectErrorStream(true);

            Map<String, String> env = pb.environment();

            int availableCores = Runtime.getRuntime().availableProcessors();
            int threadsToUse = (numThreads > 0) ? numThreads : availableCores;
            String threadsStr = String.valueOf(threadsToUse);

            env.put("OMP_NUM_THREADS", threadsStr);
            env.put("OMP_STACKSIZE", "128M");
            env.put("CCX_NPROC_EQUATION_SOLVER", threadsStr);

            File usrLib = filesDir != null ? new File(filesDir, "usr/lib") : null;
            File usrBin = filesDir != null ? new File(filesDir, "usr/bin") : null;

            // Critical TCL/TK environment for DRAWEXE headless execution
            if (usrLib != null && new File(usrLib, "tcl8.6").exists()) {
                env.put("TCL_LIBRARY", new File(usrLib, "tcl8.6").getAbsolutePath());
                env.put("TK_LIBRARY", new File(usrLib, "tk8.6").getAbsolutePath());
                env.put("TCLLIBPATH", String.format("%s %s %s",
                        usrLib.getAbsolutePath(),
                        new File(usrLib, "tcl8.6").getAbsolutePath(),
                        new File(usrLib, "tk8.6").getAbsolutePath()));
            }

            if (filesDir != null) {
                File occtResources = new File(filesDir, "usr/share/opencascade/resources");
                if (occtResources.exists()) {
                    env.put("CASROOT", new File(filesDir, "usr/share/opencascade").getAbsolutePath());
                    env.put("CSF_OCCTResourcePath", occtResources.getAbsolutePath());
                    env.put("CSF_DrawPluginDefaults", new File(occtResources, "DrawResources").getAbsolutePath());
                }
            }

            // Force headless mode by ensuring DISPLAY is absent
            env.remove("DISPLAY");

            // Disable fdsan via LD_PRELOAD to prevent aborts in DRAWEXE on Android 11+
            if (nativeLibDir != null) {
                File preloadFile = new File(nativeLibDir, "libfdsan_bypass.so");
                if (preloadFile.exists()) {
                    env.put("LD_PRELOAD", preloadFile.getAbsolutePath());
                }
            }

            String currentLdPath = System.getenv("LD_LIBRARY_PATH");
            if (currentLdPath == null) currentLdPath = "";

            String extraLd = (usrLib != null && usrLib.exists()) ? usrLib.getAbsolutePath() + ":" : "";
            String nativeLd = (nativeLibDir != null && nativeLibDir.exists()) ? nativeLibDir.getAbsolutePath() + ":" : "";
            env.put("LD_LIBRARY_PATH", extraLd + nativeLd + currentLdPath);

            String currentPath = System.getenv("PATH");
            if (currentPath == null) currentPath = "";
            String extraPath = (usrBin != null && usrBin.exists()) ? usrBin.getAbsolutePath() + ":" : "";
            String nativePath = (nativeLibDir != null && nativeLibDir.exists()) ? nativeLibDir.getAbsolutePath() + ":" : "";
            env.put("PATH", extraPath + nativePath + currentPath);

            Process process = pb.start();
            synchronized (this) {
                currentProcess = process;
            }

            // Send input to stdin if provided
            if (input != null && !input.isEmpty()) {
                try (java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                    writer.write(input);
                    if (!input.endsWith("\n")) writer.write("\n");
                    writer.flush();
                }
            }

            StringBuilder output = new StringBuilder();
            char[] buffer = new char[1024];
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                int read;
                while ((read = reader.read(buffer, 0, buffer.length)) != -1) {
                    String chunk = new String(buffer, 0, read);
                    String cleanChunk = sanitizeBinaryOutput(chunk, binaryName, binary);
                    output.append(cleanChunk);
                    if (listener != null) {
                        listener.onOutput(cleanChunk);
                    }
                }
            }

            int exitCode = process.waitFor();
            synchronized (this) {
                if (currentProcess == process) {
                    currentProcess = null;
                }
            }
            return output.toString().trim() + "\nExit Code: " + exitCode;

        } catch (Exception e) {
            Log.e(TAG, "Execution Failed: " + e.getMessage());
            return "Execution Error: " + e.getMessage();
        } finally {
            synchronized (this) {
                currentProcess = null;
            }
        }
    }

    public static String sanitizeBinaryOutput(String text) {
        return sanitizeBinaryOutput(text, null, null);
    }

    public static String sanitizeBinaryOutput(String text, String binaryName, File binary) {
        if (text == null || text.isEmpty()) return text;
        String res = text;
        if (binary != null) {
            res = res.replace(binary.getAbsolutePath(), binaryName != null ? binaryName : binary.getName());
        }
        res = res.replaceAll("/data/app/[^\\s'\"`]+/lib/(?:arm64|armeabi-v7a|x86_64|x86)/lib([a-zA-Z0-9_-]+)\\.so", "$1");
        res = res.replaceAll("/data/(?:data|user/[0-9]+)/[^\\s'\"`]+/files/usr/bin/([a-zA-Z0-9_-]+)", "$1");
        return res;
    }

    public static boolean wasSuccessful(String output) {
        return output != null && output.contains("Exit Code: 0");
    }

    public static void deleteFileThoroughly(File f) {
        if (f == null || !f.exists()) return;
        boolean deleted = f.delete();
        if (!deleted && f.exists()) {
            try {
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f, false)) {
                    fos.flush();
                }
                f.delete();
            } catch (Exception ignored) {}
        }
    }
}
