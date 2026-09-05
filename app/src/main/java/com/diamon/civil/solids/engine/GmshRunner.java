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

    /** Density slider mapping: 1 (coarse) to 7 (hyper-extreme). Calibrated for stable mobile FEA and monotonic refinement. */
    private static final double[] CLMAX_VALUES = {50.0, 25.0, 15.0, 8.0, 5.0, 5.0, 5.0};

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
     * @param meshDensity 1–7: 1 = coarse (~50 mm), 7 = hyper-extreme (~1.5 mm, absolute precision)
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
                boolean success = (result != null && result.contains("Exit Code: 0") && outputInp.exists() && outputInp.length() > 0);
                if (success) {
                    callback.onSuccess(outputInp);
                } else {
                    if (outputInp.exists()) outputInp.delete();
                    callback.onError("Gmsh did not produce a valid mesh.\n" + result);
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

        double clmax = CLMAX_VALUES[Math.max(0, Math.min(6, meshDensity - 1))];
        double[] sizeFactors = {2.0, 1.5, 1.0, 0.75, 0.55, 0.25, 0.18};
        double meshFactor = sizeFactors[Math.max(0, Math.min(6, meshDensity - 1))];

        boolean is2ndOrder = elementType != null && (elementType.contains("C3D10") || elementType.contains("C3D20") || elementType.contains("C3D15") || elementType.contains("2nd-Order") || elementType.contains("2do-Orden") || elementType.contains("Quadratic") || elementType.contains("Cuadrático"));
        boolean isHex = elementType != null && (elementType.contains("C3D8") || elementType.contains("C3D20") || elementType.contains("Hexahedron") || elementType.contains("Hexaedro"));
        boolean isWedge = elementType != null && (elementType.contains("C3D6") || elementType.contains("C3D15") || elementType.contains("Wedge") || elementType.contains("Cuña") || elementType.contains("Prism") || elementType.contains("Prisma"));

        File targetInput = inputFile;
        String nameLower = inputFile.getName().toLowerCase(java.util.Locale.US);

        // If wedge formulation is requested and active model is cantilever benchmark, ensure we use the extruded wedge definition
        if (isWedge && (nameLower.startsWith("cantilever") || nameLower.equals("cantilever.geo") || nameLower.equals("cantilever_benchmark.geo"))) {
            try {
                targetInput = SampleSimulationCase.createCantileverWedgeGeo(workDir);
                nameLower = targetInput.getName().toLowerCase(java.util.Locale.US);
            } catch (Exception e) {
                logW(TAG, "Could not create cantilever wedge geo: " + e.getMessage());
            }
        } else if (!isWedge && nameLower.equals("cantilever_wedge.geo")) {
            targetInput = new File(workDir, "cantilever.geo");
            nameLower = targetInput.getName().toLowerCase(java.util.Locale.US);
        }

        // If input is an IGES file, sew unstitched trimmed NURBS surfaces into a closed 3D solid BRep using OCCT/DRAWEXE
        if (nameLower.endsWith(".iges") || nameLower.endsWith(".igs")) {
            File drawexeBin = findDrawexeBinary();
            if (drawexeBin != null && drawexeBin.exists()) {
                File sewnBrep = new File(workDir, stripExtension(inputFile.getName()) + "_sewn.brep");
                if (sewnBrep.exists()) {
                    sewnBrep.delete();
                }
                File tclFile = new File(workDir, "sew_iges.tcl");
                String tclScript = "pload MODELING\n" +
                        "pload XSDRAW\n" +
                        "igesread \"" + inputFile.getAbsolutePath() + "\" ig *\n" +
                        "sewing s 0.1 ig\n" +
                        "mkvolume v s\n" +
                        "writebrep v \"" + sewnBrep.getAbsolutePath() + "\"\n" +
                        "exit\n";
                try {
                    try (java.io.PrintWriter pwTcl = new java.io.PrintWriter(new java.io.FileWriter(tclFile))) {
                        pwTcl.write(tclScript);
                    }
                    ProcessBuilder pbDraw = new ProcessBuilder(drawexeBin.getAbsolutePath(), "-b", "-f", tclFile.getAbsolutePath());
                    pbDraw.directory(workDir);
                    setupEnvironment(pbDraw.environment());
                    Process pDraw = pbDraw.start();
                    pDraw.waitFor();
                    if (sewnBrep.exists() && sewnBrep.length() > 0) {
                        targetInput = sewnBrep;
                        nameLower = targetInput.getName().toLowerCase(java.util.Locale.US);
                        logD(TAG, "Successfully sewed IGES to solid BRep: " + sewnBrep.getName());
                    }
                } catch (Exception e) {
                    logW(TAG, "DRAWEXE IGES sewing error: " + e.getMessage());
                }
            }
        }

        // If input is a CAD file (STEP, IGES, BREP), generate a robust OpenCASCADE driver .geo file
        if (nameLower.endsWith(".step") || nameLower.endsWith(".stp") ||
            nameLower.endsWith(".iges") || nameLower.endsWith(".igs") ||
            nameLower.endsWith(".brep")) {

            File geoDriver = new File(workDir, "gmsh_cad_driver.geo");
            if (geoDriver.exists()) {
                geoDriver.delete();
            }
            File geoUnrolled = new File(workDir, "gmsh_cad_driver.geo_unrolled");
            if (geoUnrolled.exists()) {
                geoUnrolled.delete();
            }
            boolean isSphere = nameLower.contains("sphere") || targetInput.getName().toLowerCase(java.util.Locale.US).contains("sphere");
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(geoDriver))) {
                pw.println("// OpenCASCADE CAD Driver for Gmsh");
                pw.println("SetFactory(\"OpenCASCADE\");");
                if (!isSphere) {
                    pw.println("Geometry.OCCFixDegenerated = 1;");
                    pw.println("Geometry.OCCFixSmallEdges = 1;");
                    pw.println("Geometry.OCCFixSmallFaces = 1;");
                    pw.println("Geometry.OCCSewFaces = 1;");
                    pw.println("Geometry.Tolerance = 0.1;");
                }
                pw.println("Merge \"" + targetInput.getAbsolutePath() + "\";");
                pw.println("");
                pw.println("v() = Volume{:};");
                pw.println("If (#v() == 0)");
                pw.println("  Surface Loop(1) = Surface{:};");
                pw.println("  Volume(1) = {1};");
                pw.println("EndIf");
                pw.println("");
                pw.println("Physical Volume(\"SOLID_VOLUME\", 1) = Volume{:};");
            } catch (Exception e) {
                logE(TAG, "Failed to create geo driver: " + e.getMessage());
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
            if (targetInput.getName().contains("wedge")) {
                meshOpts.append(" Mesh.Algorithm3D=1;");
            } else {
                meshOpts.append(" Mesh.Recombine3DAll=1; Mesh.Algorithm=6; Mesh.SubdivisionAlgorithm=2; Mesh.Recombine3DLevel=2; Mesh.Algorithm3D=1;");
            }
        } else {
            meshOpts.append(" Mesh.Algorithm3D=1; Mesh.Recombine3DAll=0;");
        }
        meshOpts.append(" Mesh.SaveGroupsOfNodes=1; Mesh.SaveGroupsOfElements=1;");
        
        command.add(meshOpts.toString());
        int availableCores = Runtime.getRuntime().availableProcessors();
        command.add("-nt");
        command.add(String.valueOf(availableCores));
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
            if (exitCode != 0 && !nameLower.contains("sphere") && targetInput.getName().equals("gmsh_cad_driver.geo")) {
                logW(TAG, "Gmsh meshing failed with shape healing enabled; retrying with clean OCC topology (healing disabled)...");
                try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(targetInput))) {
                    pw.println("// OpenCASCADE CAD Driver for Gmsh (Fallback without healing)");
                    pw.println("SetFactory(\"OpenCASCADE\");");
                    pw.println("Merge \"" + inputFile.getAbsolutePath() + "\";");
                    pw.println("");
                    pw.println("v() = Volume{:};");
                    pw.println("If (#v() == 0)");
                    pw.println("  Surface Loop(1) = Surface{:};");
                    pw.println("  Volume(1) = {1};");
                    pw.println("EndIf");
                    pw.println("");
                    pw.println("Physical Volume(\"SOLID_VOLUME\", 1) = Volume{:};");
                } catch (Exception ignored) {}

                Process processFallback = pb.start();
                StringBuilder outputFallback = new StringBuilder();
                try (BufferedReader readerFallback = new BufferedReader(
                        new InputStreamReader(processFallback.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = readerFallback.readLine()) != null) {
                        outputFallback.append(line).append("\n");
                    }
                }
                int exitCodeFallback = processFallback.waitFor();
                if (exitCodeFallback == 0 && outputMsh.exists() && outputMsh.length() > 0) {
                    String resFb = "> gmsh " + inputFile.getName()
                            + " -3 -clmax " + clmax + " (Clean OCC Fallback)\n"
                            + outputFallback.toString().trim()
                            + "\nExit Code: 0";
                    logD(TAG, resFb);
                    return resFb;
                }
                output.append("\n--- Clean OCC Fallback Log ---\n").append(outputFallback);
            }
            String result = "> gmsh " + inputFile.getName()
                    + " -3 -clmax " + clmax + "\n"
                    + output.toString().trim()
                    + "\nExit Code: " + exitCode;
            logD(TAG, result);
            return result;

        } catch (Throwable e) {
            logE(TAG, "Gmsh execution failed: " + e.getMessage());
            return "Execution Error: " + e.getMessage();
        }
    }

    private static void logD(String tag, String msg) {
        try {
            Log.d(tag, msg);
        } catch (Throwable ignored) {
            System.out.println("[" + tag + "] " + msg);
        }
    }

    private static void logW(String tag, String msg) {
        try {
            Log.w(tag, msg);
        } catch (Throwable ignored) {
            System.err.println("[" + tag + "] WARN: " + msg);
        }
    }

    private static void logE(String tag, String msg) {
        try {
            Log.e(tag, msg);
        } catch (Throwable ignored) {
            System.err.println("[" + tag + "] ERROR: " + msg);
        }
    }

    private void setupEnvironment(Map<String, String> env) {
        int availableCores = Runtime.getRuntime().availableProcessors();
        String threadsStr = String.valueOf(availableCores);
        env.put("OMP_NUM_THREADS", threadsStr);
        env.put("OMP_STACKSIZE", "128M");

        File usrLib = filesDir != null ? new File(filesDir, "usr/lib") : null;
        File usrBin = filesDir != null ? new File(filesDir, "usr/bin") : null;

        String currentLdPath = env.get("LD_LIBRARY_PATH");
        if (currentLdPath == null) currentLdPath = "";

        StringBuilder extraLd = new StringBuilder();
        if (usrLib != null && usrLib.exists()) extraLd.append(usrLib.getAbsolutePath()).append(":");
        if (nativeLibDir != null && nativeLibDir.exists()) extraLd.append(nativeLibDir.getAbsolutePath()).append(":");
        if (filesDir != null) extraLd.append(filesDir.getAbsolutePath()).append("/usr/lib/calculix:");
        env.put("LD_LIBRARY_PATH", extraLd.toString() + currentLdPath);

        String currentPath = env.get("PATH");
        if (currentPath == null) currentPath = "";
        StringBuilder extraPath = new StringBuilder();
        if (usrBin != null && usrBin.exists()) extraPath.append(usrBin.getAbsolutePath()).append(":");
        if (nativeLibDir != null && nativeLibDir.exists()) extraPath.append(nativeLibDir.getAbsolutePath()).append(":");
        env.put("PATH", extraPath.toString() + currentPath);
    }

    /** Find DRAWEXE binary: prioritized for CAD sewing and repair */
    private File findDrawexeBinary() {
        if (filesDir != null) {
            File usrBin = new File(filesDir, "usr/bin/DRAWEXE");
            if (usrBin.exists() && usrBin.canExecute()) return usrBin;
        }
        if (nativeLibDir != null) {
            File libDrawexe = new File(nativeLibDir, "libDRAWEXE.so");
            if (libDrawexe.exists()) return libDrawexe;

            File libDrawexeVer = new File(nativeLibDir, "libDRAWEXE_8.0.0.so");
            if (libDrawexeVer.exists()) return libDrawexeVer;
        }
        File sysDrawexe = new File("/usr/bin/DRAWEXE");
        if (sysDrawexe.exists() && sysDrawexe.canExecute()) return sysDrawexe;

        return null;
    }

    /** Find gmsh binary: prioritize the verified binary used in simulation tests */
    private File findGmshBinary() {
        // 1. Try the symlink at usr/bin created by AssetHelper (verified standalone)
        if (filesDir != null) {
            File usrBin = new File(filesDir, "usr/bin/gmsh");
            if (usrBin.exists() && usrBin.canExecute()) return usrBin;
        }

        // 2. Try the packaged binary in jniLibs
        if (nativeLibDir != null) {
            File verifiedGmsh = new File(nativeLibDir, "libgmsh_v5_0_0.so");
            if (verifiedGmsh.exists()) return verifiedGmsh;

            // 3. Fallback to the standard physical name
            File libGmsh = new File(nativeLibDir, "libgmsh.so");
            if (libGmsh.exists()) return libGmsh;
        }

        // 4. Fallback to host system binary for local unit testing / CLI
        File sysGmsh = new File("/usr/bin/gmsh");
        if (sysGmsh.exists() && sysGmsh.canExecute()) return sysGmsh;

        String home = System.getProperty("user.home", "");
        File localGmsh = new File(home + "/.local/bin/gmsh");
        if (localGmsh.exists() && localGmsh.canExecute()) return localGmsh;

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
