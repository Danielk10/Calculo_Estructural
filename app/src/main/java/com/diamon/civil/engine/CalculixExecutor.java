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

import com.diamon.civil.util.NativeLoader;

public class CalculixExecutor {
    private static final String TAG = "CalculixExecutor";
    private final File workDir;
    private final File nativeLibDir;

    public CalculixExecutor(Context context) {
        this.workDir = context.getFilesDir();
        this.nativeLibDir = new File(context.getApplicationInfo().nativeLibraryDir);
        
        // Ensure all dependencies are loaded correctly
        NativeFeaCore.loadLibraries();
    }

    public native boolean convertFrdToGlb(String inputPath, String outputPath);

    public String executeCalculix(String jobName) {
        return executeBinary("ccx", jobName);
    }

    public String runGmsh(String inputPath, String outputPath, double meshSize) {
        return executeBinary("gmsh", inputPath, "-3", "-clmax", String.valueOf(meshSize), "-o", outputPath, "-format", "inp");
    }

    public String executeBinary(String binaryName, String... args) {
        // AssetHelper creates symlinks in usr/bin that point to lib*.so
        File binary = new File(new File(workDir, "usr/bin"), binaryName);
        
        if (!binary.exists()) {
            // Fallback to normalized physical name in nativeLibDir
            String normalizedName = binaryName;
            // Removed incorrect mapping for gmsh to v5_0_0 shared library. 
            // The 6KB libgmsh.so is the actual PIE binary.
            binary = new File(nativeLibDir, "lib" + normalizedName + ".so");
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
            
            // Mirror verified test environment and native runner logic
            String numCores = String.valueOf(Runtime.getRuntime().availableProcessors());
            env.put("OMP_NUM_THREADS", numCores);
            env.put("CCX_NPROC_EQUATION_SOLVER", numCores);
            
            File usrLib = new File(workDir, "usr/lib");
            File usrBin = new File(workDir, "usr/bin");
            
            // Critical: Add GFortran and OpenBLAS dependencies if present in workDir/usr/lib
            String currentLdPath = System.getenv("LD_LIBRARY_PATH");
            if (currentLdPath == null) currentLdPath = "";
            
            env.put("LD_LIBRARY_PATH", usrLib.getAbsolutePath() + ":" + 
                    nativeLibDir.getAbsolutePath() + ":" + 
                    workDir.getAbsolutePath() + "/usr/lib/calculix:" +
                    currentLdPath);
                    
            String currentPath = System.getenv("PATH");
            if (currentPath == null) currentPath = "";
            env.put("PATH", usrBin.getAbsolutePath() + ":" + 
                    nativeLibDir.getAbsolutePath() + ":" + 
                    currentPath);

            Process process = pb.start();
            
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
}
