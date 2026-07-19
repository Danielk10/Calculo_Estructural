package com.diamon.civil.test.simulation;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.diamon.civil.engine.SampleSimulationCase;

public class SimulationTestManager {
    public static String runTest(File workDir, File nativeLibDir) {
        StringBuilder report = new StringBuilder();
        try {
            report.append("=== Iniciando Simulacion Standalone ===\n");
            
            // 1. Crear el mismo caso de referencia que utiliza la interfaz Solid.
            File geoFile = SampleSimulationCase.createCantileverGeo(workDir);
            report.append("OK: cantilever.geo generado\n");

            // 2. Ejecutar Gmsh
            File gmsh = new File(nativeLibDir, "libgmsh_v5_0_0.so"); // Usar el binario correcto
            if (!gmsh.exists()) gmsh = new File(new File(workDir, "usr/bin"), "gmsh");
            
            report.append("Ejecutando Gmsh...\n");
            report.append(executeBinary(gmsh.getAbsolutePath(), workDir, nativeLibDir, 
                "cantilever.geo", "-3", "-format", "inp", "-o", workDir.getAbsolutePath() + "/cantilever_raw.inp"));

            // 2.1 Ensamblar archivo final .inp
            report.append("Ensamblando Input Final...\n");
            InpAssembler.assemble(workDir, "cantilever");

            // 3. Ejecutar CalculiX
            File ccx = new File(nativeLibDir, "libccx.so");
            if (!ccx.exists()) ccx = new File(new File(workDir, "usr/bin"), "ccx");
            
            report.append("\nEjecutando CalculiX...\n");
            report.append(executeBinary(ccx.getAbsolutePath(), workDir, nativeLibDir, 
                "-i", workDir.getAbsolutePath() + "/cantilever"));

            // 4. Parsear resultados (.frd)
            File frdFile = new File(workDir, "cantilever.frd");
            if (frdFile.exists()) {
                report.append("\n=== RESUMEN FINAL DE RESULTADOS (Parser Java) ===\n");
                report.append(FrdParser.parseAndSummarize(frdFile));
            } else {
                report.append("\nADVERTENCIA: No se generó archivo .frd.\n");
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
            env.put("LD_LIBRARY_PATH", workDir.getAbsolutePath() + "/usr/lib:" + nativeLibDir.getAbsolutePath());
            env.put("PATH", workDir.getAbsolutePath() + "/usr/bin:" + System.getenv("PATH"));
            env.put("OMP_NUM_THREADS", "4");
            
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
