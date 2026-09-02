package com.diamon.civil.solids.engine;

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
    private final File filesDir;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface GmshCallback {
        void onSuccess(File mshFile);
        void onError(String message);
    }

    public GmshRunner(File workDir, File nativeLibDir) {
        this.workDir = workDir;
        this.nativeLibDir = nativeLibDir;
        this.filesDir = workDir.getParentFile();
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

    public void meshAsync(File inputFile, int meshDensity, String jobName, String elementType, GmshCallback callback) {
        executor.execute(() -> {
            try {
                File outputInp = new File(workDir, jobName + "_raw.inp");
                String result = runGmsh(inputFile, outputInp, meshDensity, elementType);
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

    /** Creates a raw CalculiX mesh using a deterministic job name. */
    public void meshAsync(File inputFile, int meshDensity, String jobName, boolean useQuadratic, boolean useHexahedral, GmshCallback callback) {
        String type = (useQuadratic ? "C3D10" : "C3D4");
        if (useHexahedral) type = (useQuadratic ? "C3D20" : "C3D8");
        meshAsync(inputFile, meshDensity, jobName, type, callback);
    }

    /**
     * Synchronous Gmsh execution. Call from a background thread.
     */
    public String runGmsh(File inputFile, File outputMsh, int meshDensity) {
        return runGmsh(inputFile, outputMsh, meshDensity, "C3D4");
    }

    public String runGmsh(File inputFile, File outputMsh, int meshDensity, boolean useQuadratic, boolean useHexahedral) {
        String type = (useQuadratic ? "C3D10" : "C3D4");
        if (useHexahedral) type = (useQuadratic ? "C3D20" : "C3D8");
        return runGmsh(inputFile, outputMsh, meshDensity, type);
    }

    public String runGmsh(File inputFile, File outputMsh, int meshDensity, String elementType) {
        File gmshBin = findGmshBinary();
        if (gmshBin == null) {
            return "Error: Gmsh binary not found in " + nativeLibDir.getAbsolutePath();
        }

        double clmax = CLMAX_VALUES[Math.max(0, Math.min(4, meshDensity - 1))];
        double[] sizeFactors = {2.0, 1.5, 1.0, 0.5, 0.25};
        double meshFactor = sizeFactors[Math.max(0, Math.min(4, meshDensity - 1))];

        boolean is2ndOrder = elementType != null && (elementType.contains("C3D10") || elementType.contains("C3D20") || elementType.contains("C3D15") || elementType.contains("2nd-Order") || elementType.contains("Quadratic"));
        boolean isHex = elementType != null && (elementType.contains("C3D8") || elementType.contains("C3D20") || elementType.contains("Hexahedron"));
        boolean isWedge = elementType != null && (elementType.contains("C3D6") || elementType.contains("C3D15") || elementType.contains("Wedge") || elementType.contains("Prism"));

        File targetInput = inputFile;
        String nameLower = inputFile.getName().toLowerCase(java.util.Locale.US);

        // If input is an IGES file, sew unstitched trimmed NURBS surfaces into a closed 3D solid BRep using OCCT/DRAWEXE
        if (nameLower.endsWith(".iges") || nameLower.endsWith(".igs")) {
            File drawexeBin = findDrawexeBinary();
            if (drawexeBin != null && drawexeBin.exists()) {
                File sewnBrep = new File(workDir, stripExtension(inputFile.getName()) + "_sewn.brep");
                String tclScript = "pload ALL\n" +
                        "igesread \"" + inputFile.getAbsolutePath() + "\" ig *\n" +
                        "sewing s 0.05 ig\n" +
                        "mkvolume v s\n" +
                        "save v \"" + sewnBrep.getAbsolutePath() + "\"\n" +
                        "exit\n";
                try {
                    ProcessBuilder pbDraw = new ProcessBuilder(drawexeBin.getAbsolutePath());
                    pbDraw.directory(workDir);
                    setupEnvironment(pbDraw.environment());
                    Process pDraw = pbDraw.start();
                    try (java.io.OutputStream os = pDraw.getOutputStream()) {
                        os.write(tclScript.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    }
                    pDraw.waitFor();
                    if (sewnBrep.exists() && sewnBrep.length() > 0) {
                        targetInput = sewnBrep;
                        nameLower = targetInput.getName().toLowerCase(java.util.Locale.US);
                        Log.d(TAG, "Successfully sewed IGES to solid BRep: " + sewnBrep.getName());
                    }
                } catch (Exception e) {
                    Log.w(TAG, "DRAWEXE IGES sewing error: " + e.getMessage());
                }
            }
        }

        // If input is a CAD file (STEP, IGES, BREP), generate a clean OpenCASCADE driver .geo file
        if (nameLower.endsWith(".step") || nameLower.endsWith(".stp") ||
            nameLower.endsWith(".iges") || nameLower.endsWith(".igs") ||
            nameLower.endsWith(".brep")) {

            File geoDriver = new File(workDir, "gmsh_cad_driver.geo");
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(geoDriver))) {
                pw.println("// OpenCASCADE CAD Driver for Gmsh");
                pw.println("SetFactory(\"OpenCASCADE\");");
                pw.println("Merge \"" + targetInput.getAbsolutePath() + "\";");
            } catch (Exception e) {
                Log.e(TAG, "Failed to create geo driver: " + e.getMessage());
            }
            if (geoDriver.exists()) {
                targetInput = geoDriver;
            }
        }

        List<String> command = new ArrayList<>();
        command.add(gmshBin.getAbsolutePath());
        command.add(targetInput.getAbsolutePath());
        command.add("-string");
        
        StringBuilder meshOpts = new StringBuilder();
        meshOpts.append("Mesh.MeshSizeFactor=").append(meshFactor).append(";");
        meshOpts.append(" Mesh.ElementOrder=").append(is2ndOrder ? 2 : 1).append(";");
        if (is2ndOrder) {
            meshOpts.append(" Mesh.SecondOrderIncomplete=1; Mesh.SecondOrderLinear=1; Mesh.Optimize=1;");
        }
        if (isHex) {
            meshOpts.append(" Mesh.Recombine3DAll=1; Mesh.Algorithm=6; Mesh.SubdivisionAlgorithm=2; Mesh.Recombine3DLevel=2; Mesh.Algorithm3D=1;");
        } else if (isWedge) {
            meshOpts.append(" Mesh.SubdivisionAlgorithm=1; Mesh.Algorithm3D=1;");
        } else {
            meshOpts.append(" Mesh.Algorithm3D=1; Mesh.Recombine3DAll=0;");
        }
        meshOpts.append(" Mesh.SaveGroupsOfNodes=1; Mesh.SaveGroupsOfElements=1;");
        
        command.add(meshOpts.toString());
        command.add("-3");                         // 3D mesh
        command.add("-clmax");
        command.add(String.valueOf(clmax));
        command.add("-o");
        command.add(outputMsh.getAbsolutePath());
        command.add("-format");
        command.add("inp");                        // Use INP for CalculiX compatibility
        if (outputMsh.exists()) {
            outputMsh.delete();
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir);
            pb.redirectErrorStream(true);

            setupEnvironment(pb.environment());

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

    private void setupEnvironment(Map<String, String> env) {
        File usrLib = new File(filesDir, "usr/lib");
        File usrBin = new File(filesDir, "usr/bin");

        String currentLdPath = env.get("LD_LIBRARY_PATH");
        if (currentLdPath == null) currentLdPath = "";

        env.put("LD_LIBRARY_PATH",
                usrLib.getAbsolutePath() + ":" +
                nativeLibDir.getAbsolutePath() + ":" +
                filesDir.getAbsolutePath() + "/usr/lib/calculix:" +
                currentLdPath);

        String currentPath = env.get("PATH");
        if (currentPath == null) currentPath = "";
        env.put("PATH", usrBin.getAbsolutePath() + ":" + nativeLibDir.getAbsolutePath() + ":" + currentPath);
    }

    /** Find DRAWEXE binary: prioritized for CAD sewing and repair */
    private File findDrawexeBinary() {
        File usrBin = new File(filesDir, "usr/bin/DRAWEXE");
        if (usrBin.exists() && usrBin.canExecute()) return usrBin;

        File libDrawexe = new File(nativeLibDir, "libDRAWEXE.so");
        if (libDrawexe.exists()) return libDrawexe;

        File libDrawexeVer = new File(nativeLibDir, "libDRAWEXE_8.0.0.so");
        if (libDrawexeVer.exists()) return libDrawexeVer;

        return null;
    }

    /** Find gmsh binary: prioritize the verified binary used in simulation tests */
    private File findGmshBinary() {
        // 1. Try the symlink at usr/bin created by AssetHelper (verified standalone)
        File usrBin = new File(filesDir, "usr/bin/gmsh");
        if (usrBin.exists() && usrBin.canExecute()) return usrBin;

        // 2. Try the packaged binary in jniLibs
        File verifiedGmsh = new File(nativeLibDir, "libgmsh_v5_0_0.so");
        if (verifiedGmsh.exists()) return verifiedGmsh;

        // 3. Fallback to the standard physical name
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
