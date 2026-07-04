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
 * Produces a .msh output file that MshToInpConverter can then translate to CalculiX format.
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
        executor.execute(() -> {
            try {
                File outputMsh = new File(workDir, stripExtension(inputFile.getName()) + ".msh");
                String result = runGmsh(inputFile, outputMsh, meshDensity);
                if (outputMsh.exists() && outputMsh.length() > 0) {
                    callback.onSuccess(outputMsh);
                } else {
                    callback.onError("Gmsh did not produce output.\n" + result);
                }
            } catch (Exception e) {
                callback.onError("GmshRunner exception: " + e.getMessage());
            }
        });
    }

    /**
     * Synchronous Gmsh execution. Call from a background thread.
     */
    public String runGmsh(File inputFile, File outputMsh, int meshDensity) {
        File gmshBin = findGmshBinary();
        if (gmshBin == null) {
            return "Error: Gmsh binary not found in " + nativeLibDir.getAbsolutePath();
        }

        double clmax = CLMAX_VALUES[Math.max(0, Math.min(4, meshDensity - 1))];

        List<String> command = new ArrayList<>();
        command.add(gmshBin.getAbsolutePath());
        command.add(inputFile.getAbsolutePath());
        command.add("-3");                         // 3D mesh
        command.add("-clmax");
        command.add(String.valueOf(clmax));
        command.add("-o");
        command.add(outputMsh.getAbsolutePath());
        command.add("-format");
        command.add("msh2");                       // Use MSH2 for wider compatibility
        command.add("-v");
        command.add("0");                          // Quiet mode

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir);
            pb.redirectErrorStream(true);

            Map<String, String> env = pb.environment();
            File usrLib = new File(workDir, "usr/lib");
            env.put("LD_LIBRARY_PATH",
                    usrLib.getAbsolutePath() + ":" + nativeLibDir.getAbsolutePath()
                            + (env.get("LD_LIBRARY_PATH") != null ? ":" + env.get("LD_LIBRARY_PATH") : ""));

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

        } catch (Exception e) {
            Log.e(TAG, "Gmsh execution failed: " + e.getMessage());
            return "Execution Error: " + e.getMessage();
        }
    }

    /** Find gmsh binary: first check usr/bin/gmsh, then jniLibs as libgmsh_bin.so */
    private File findGmshBinary() {
        File usrBin = new File(workDir, "usr/bin/gmsh");
        if (usrBin.exists()) return usrBin;

        File libBin = new File(nativeLibDir, "libgmsh_bin.so");
        if (libBin.exists()) return libBin;

        File libGmsh = new File(nativeLibDir, "libgmsh.so");
        if (libGmsh.exists()) return libGmsh;

        return null;
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }

    public void shutdown() {
        executor.shutdown();
    }
}
