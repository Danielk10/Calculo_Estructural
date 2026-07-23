package com.diamon.civil.engine;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A1: GmshRunner — Executes the Gmsh binary to mesh STL/STEP/IGES files.
 * Produces a .msh or .inp output file for CalculiX.
 */
public class GmshRunner {

    private static final String TAG = "GmshRunner";

    /** Density slider mapping: 1 (coarse) to 5 (fine). */
    private static final double[] CLMAX_VALUES = {50.0, 30.0, 20.0, 10.0, 5.0};

    private final File workDir;
    private final File nativeLibDir;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface GmshCallback {
        void onSuccess(File mshFile);
        void onError(String message);
    }

    public GmshRunner(File workDir, File nativeLibDir) {
        this.workDir = workDir;
        this.nativeLibDir = nativeLibDir;
    }

    /**
     * Meshes an input CAD file asynchronously.
     *
     * @param inputFile  Source CAD file (STL / STEP / IGES / GEO)
     * @param meshDensity 1–5: 1 = coarse (~50 mm), 5 = fine (~5 mm)
     * @param callback   Called on the calling thread pool when done
     */
    public void meshAsync(File inputFile, int meshDensity, GmshCallback callback) {
        meshAsync(inputFile, meshDensity, stripExtension(inputFile.getName()), false, false, callback);
    }

    public void meshAsync(File inputFile, int meshDensity, String jobName, GmshCallback callback) {
        meshAsync(inputFile, meshDensity, jobName, false, false, callback);
    }

    /** Creates a raw CalculiX mesh using a deterministic job name. */
    public void meshAsync(File inputFile, int meshDensity, String jobName, boolean useQuadratic, boolean useHexahedral, GmshCallback callback) {
        executor.execute(() -> {
            try {
                // Generar directamente el .inp crudo para el ensamblador
                File outputInp = new File(workDir, jobName + "_raw.inp");
                String result = runGmsh(inputFile, outputInp, meshDensity, useQuadratic, useHexahedral);
                if (outputInp.exists() && outputInp.length() > 0) {
                    callback.onSuccess(outputInp);
                } else {
                    callback.onError("Gmsh did not produce output.\n" + result);
                }
            } catch (Throwable e) {
                callback.onError("GmshRunner exception: " + e.getMessage());
            }
        });
    }

    /**
     * Synchronous Gmsh execution. Call from a background thread.
     */
    public String runGmsh(File inputFile, File outputMsh, int meshDensity) {
        return runGmsh(inputFile, outputMsh, meshDensity, false, false);
    }

    public String runGmsh(File inputFile, File outputMsh, int meshDensity, boolean useQuadratic, boolean useHexahedral) {
        File gmshBin = findGmshBinary();
        if (gmshBin == null) {
            return "Error: Gmsh binary not found in " + nativeLibDir.getAbsolutePath();
        }

        double clmax = CLMAX_VALUES[Math.max(0, Math.min(4, meshDensity - 1))];

        List<String> command = new ArrayList<>();
        command.add(gmshBin.getAbsolutePath());
        command.add(inputFile.getAbsolutePath());
        command.add("-string");
        
        String meshOpts = "Mesh.ElementOrder=" + (useQuadratic ? 2 : 1) + ";";
        if (useHexahedral) meshOpts += " Mesh.Recombine3DAll=1; Mesh.Algorithm3D=1; Mesh.Algorithm=6; Mesh.SubdivisionAlgorithm=2;";
        else meshOpts += " Mesh.Algorithm3D=1;";
        
        command.add(meshOpts);
        command.add("-3");                         // 3D mesh
        command.add("-clmax");
        command.add(String.valueOf(clmax));
        command.add("-o");
        command.add(outputMsh.getAbsolutePath());
        command.add("-format");
        command.add("inp");                        // Use INP for CalculiX compatibility
        command.add("-v");
        command.add("0");                          // Quiet mode

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir);
            pb.redirectErrorStream(true);

            Map<String, String> env = pb.environment();
            File usrLib = new File(workDir, "usr/lib");
            File usrBin = new File(workDir, "usr/bin");
            
            String currentLdPath = env.get("LD_LIBRARY_PATH");
            if (currentLdPath == null) currentLdPath = "";
            
            env.put("LD_LIBRARY_PATH", 
                    usrLib.getAbsolutePath() + ":" + 
                    nativeLibDir.getAbsolutePath() + ":" + 
                    workDir.getAbsolutePath() + "/usr/lib/calculix:" +
                    currentLdPath);
                    
            String currentPath = env.get("PATH");
            if (currentPath == null) currentPath = "";
            env.put("PATH", usrBin.getAbsolutePath() + ":" + nativeLibDir.getAbsolutePath() + ":" + currentPath);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            String result = "> gmsh " + inputFile.getName()
                    + " -3 -clmax " + clmax + "\n"
                    + output.toString().trim()
                    + "\nExit Code: " + exitCode;
            Log.d(TAG, result);
            return result;

        } catch (Throwable e) {
            Log.e(TAG, "Gmsh execution failed: " + e.getMessage());
            return "Execution Error: " + e.getMessage();
        }
    }

    /** Find gmsh binary: prioritize the verified binary used in simulation tests */
    private File findGmshBinary() {
        // 1. Probar el enlace simbólico en usr/bin creado por AssetHelper (Standalone que funciona)
        File usrBin = new File(workDir, "usr/bin/gmsh");
        if (usrBin.exists() && usrBin.canExecute()) return usrBin;

        // 2. Probar el binario empaquetado en jniLibs
        File verifiedGmsh = new File(nativeLibDir, "libgmsh_v5_0_0.so");
        if (verifiedGmsh.exists()) return verifiedGmsh;

        // 3. Fallback al nombre físico estándar
        File libGmsh = new File(nativeLibDir, "libgmsh.so");
        if (libGmsh.exists()) return libGmsh;

        return null;
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
