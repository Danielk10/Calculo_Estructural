package com.diamon.civil.engine;

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
    private final File workDir;
    private final File nativeLibDir;

    public CalculixExecutor(Context context) {
        this.workDir = context.getFilesDir();
        this.nativeLibDir = new File(context.getApplicationInfo().nativeLibraryDir);
        
        // The solver is a child process. Do not load the JNI/OCCT stack here: the
        // terminal must remain usable even when the optional 3D viewer is absent.
    }

    public native boolean convertFrdToGlb(String inputPath, String outputPath);

    public String executeCalculix(String jobName) {
        if (jobName == null || jobName.trim().isEmpty()) {
            return "Execution Error: CalculiX job name is empty";
        }
        String baseName = jobName.endsWith(".inp")
                ? jobName.substring(0, jobName.length() - ".inp".length())
                : jobName;
        File inputBase = new File(workDir, baseName);
        // This is the same explicit invocation used by the validated terminal test.
        return executeBinary("ccx", "-i", inputBase.getAbsolutePath());
    }

    public String runGmsh(String inputPath, String outputPath, double meshSize) {
        return executeBinary("gmsh", inputPath, "-3", "-clmax", String.valueOf(meshSize), "-o", outputPath, "-format", "inp");
    }

    public String executeBinary(String binaryName, String... args) {
        return executeBinaryWithInput(binaryName, null, args);
    }

    public String executeBinaryWithInput(String binaryName, String input, String... args) {
        // AssetHelper creates symlinks in usr/bin that point to lib*.so
        File binary;
        // ccx is already validated as an executable in nativeLibraryDir. Prefer it
        // over a user-space symlink, whose target can be stale after an app update.
        File packagedBinary = new File(nativeLibDir, "lib" + binaryName + ".so");
        if (packagedBinary.exists()) {
            binary = packagedBinary;
        } else {
            binary = new File(new File(workDir, "usr/bin"), binaryName);
        }
        
        if (!binary.exists()) {
             return "Error: Binary (" + binaryName + ") not found at " + binary.getAbsolutePath();
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
            
            String numCores = String.valueOf(Runtime.getRuntime().availableProcessors());
            env.put("OMP_NUM_THREADS", numCores);
            env.put("CCX_NPROC_EQUATION_SOLVER", numCores);
            
            File usrLib = new File(workDir, "usr/lib");
            File usrBin = new File(workDir, "usr/bin");
            
            // Critical TCL/TK environment for DRAWEXE headless execution
            env.put("TCL_LIBRARY", new File(usrLib, "tcl8.6").getAbsolutePath());
            env.put("TK_LIBRARY", new File(usrLib, "tk8.6").getAbsolutePath());
            env.put("TCLLIBPATH", String.format("%s %s %s", 
                    usrLib.getAbsolutePath(),
                    new File(usrLib, "tcl8.6").getAbsolutePath(),
                    new File(usrLib, "tk8.6").getAbsolutePath()));
            
            // Force headless mode by ensuring DISPLAY is absent
            env.remove("DISPLAY");

            String currentLdPath = System.getenv("LD_LIBRARY_PATH");
            if (currentLdPath == null) currentLdPath = "";
            
            env.put("LD_LIBRARY_PATH", usrLib.getAbsolutePath() + ":" + 
                    nativeLibDir.getAbsolutePath() + ":" + 
                    currentLdPath);
                    
            String currentPath = System.getenv("PATH");
            if (currentPath == null) currentPath = "";
            env.put("PATH", usrBin.getAbsolutePath() + ":" + 
                    nativeLibDir.getAbsolutePath() + ":" + 
                    currentPath);

            Process process = pb.start();
            
            // Send input to stdin if provided
            if (input != null && !input.isEmpty()) {
                try (java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                    writer.write(input);
                    if (!input.endsWith("\n")) writer.write("\n");
                    writer.flush();
                }
            }

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            return output.toString().trim() + "\nExit Code: " + exitCode;

        } catch (Exception e) {
            Log.e(TAG, "Execution Failed: " + e.getMessage());
            return "Execution Error: " + e.getMessage();
        }
    }

    public static boolean wasSuccessful(String output) {
        return output != null && output.contains("Exit Code: 0");
    }
}
