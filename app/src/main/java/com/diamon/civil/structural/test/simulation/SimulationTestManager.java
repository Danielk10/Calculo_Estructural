package com.diamon.civil.structural.test.simulation;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.diamon.civil.solids.engine.SampleSimulationCase;
import com.diamon.civil.solids.engine.SolidDisplacementFrdParser;

public class SimulationTestManager {
    public static String runTest(File workDir, File nativeLibDir) {
        StringBuilder report = new StringBuilder();
        try {
            report.append("=== Starting Standalone Simulation ===\n");
            
            // 1. Create the same reference case used by the Solid interface.
            File geoFile = SampleSimulationCase.createCantileverGeo(workDir);
            report.append("OK: cantilever.geo generated\n");

            // 2. Execute Gmsh
            File gmsh = new File(nativeLibDir, "libgmsh.so");
            if (!gmsh.exists()) gmsh = new File(new File(workDir, "usr/bin"), "gmsh");
            if (!gmsh.exists() && workDir.getParentFile() != null) gmsh = new File(new File(workDir.getParentFile(), "usr/bin"), "gmsh");
            if (!gmsh.exists()) gmsh = new File("/usr/bin/gmsh");
            
            report.append("Running Gmsh...\n");
            report.append(executeBinary(gmsh.getAbsolutePath(), workDir, nativeLibDir, 
                "cantilever.geo", "-3", "-format", "inp", "-o", workDir.getAbsolutePath() + "/cantilever_raw.inp"));

            // 2.1 Assemble final .inp file
            report.append("Assembling Final Input...\n");
            com.diamon.civil.solids.engine.SolidInpAssembler.assemble(workDir, "cantilever", "Steel", 210000.0, 0.3, -100.0, "Fixed", "Loaded");

            // 3. Execute CalculiX
            File ccx = new File(nativeLibDir, "libccx.so");
            if (!ccx.exists()) ccx = new File(new File(workDir, "usr/bin"), "ccx");
            if (!ccx.exists() && workDir.getParentFile() != null) ccx = new File(new File(workDir.getParentFile(), "usr/bin"), "ccx");
            if (!ccx.exists()) {
                String home = System.getProperty("user.home", "");
                ccx = new File(home + "/.local/bin/ccx");
            }
            if (!ccx.exists()) ccx = new File("/usr/bin/ccx");
            
            report.append("\nRunning CalculiX Solver...\n");
            report.append(executeBinary(ccx.getAbsolutePath(), workDir, nativeLibDir, 
                "-i", workDir.getAbsolutePath() + "/cantilever"));

            // 4. Parse results (.frd)
            File frdFile = new File(workDir, "cantilever.frd");
            if (frdFile.exists()) {
                report.append("\n=== FINAL RESULTS SUMMARY (Java Parser) ===\n");
                report.append(SolidDisplacementFrdParser.parseAndSummarize(frdFile));
            } else {
                report.append("\nWARNING: No .frd result file was generated.\n");
            }

            return report.toString();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private static String executeBinary(String binaryPath, File workDir, File nativeLibDir, String... args) {
        List<String> command = new ArrayList<>();
        command.add(binaryPath);
        for (String arg : args) command.add(arg);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir);
            pb.redirectErrorStream(true);

            Map<String, String> env = pb.environment();
            // Mirror the terminal environment
            File parentDir = workDir.getParentFile();
            String usrLib = (parentDir != null ? parentDir.getAbsolutePath() + "/usr/lib:" : "") + workDir.getAbsolutePath() + "/usr/lib:";
            String usrBin = (parentDir != null ? parentDir.getAbsolutePath() + "/usr/bin:" : "") + workDir.getAbsolutePath() + "/usr/bin:";
            env.put("LD_LIBRARY_PATH", usrLib + (nativeLibDir != null ? nativeLibDir.getAbsolutePath() : ""));
            env.put("PATH", usrBin + System.getenv("PATH"));
            String numCores = String.valueOf(Runtime.getRuntime().availableProcessors());
            env.put("OMP_NUM_THREADS", numCores);
            env.put("CCX_NPROC_EQUATION_SOLVER", numCores);
            
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            process.waitFor();
            return output.toString();
        } catch (Exception e) {
            return "Execution Failed: " + e.getMessage();
        }
    }
}
