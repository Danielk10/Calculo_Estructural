package com.diamon.civil.test.simulation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SimulationTestManager {
    public static String runTest(File workDir, File nativeLibDir) {
        StringBuilder report = new StringBuilder();
        try {
            report.append("=== Iniciando Simulacion Standalone ===\n");
            
            // 1. Crear cantilever.geo
            File geoFile = new File(workDir, "cantilever.geo");
            String script = "SetFactory(\"OpenCASCADE\");\n" +
                    "Box(1) = {0, 0, 0, 10, 1, 1};\n" +
                    "Mesh.CharacteristicLengthMax = 0.5;\n" +
                    "s() = Surface In BoundingBox{-0.1,-0.1,-0.1, 0.1,1.1,1.1};\n" +
                    "Physical Surface(\"Fixed\") = s();\n" +
                    "s2() = Surface In BoundingBox{9.9,-0.1,-0.1, 10.1,1.1,1.1};\n" +
                    "Physical Surface(\"Loaded\") = s2();\n" +
                    "Physical Volume(\"Steel\") = {1};\n";
            try (FileOutputStream fos = new FileOutputStream(geoFile)) {
                fos.write(script.getBytes());
            }
            report.append("OK: cantilever.geo generado\n");

            // 2. Ejecutar Gmsh
            File gmsh = new File(nativeLibDir, "libgmsh_v5_0_0.so"); // Usar el binario correcto
            if (!gmsh.exists()) gmsh = new File(new File(workDir, "usr/bin"), "gmsh");
            
            report.append("Ejecutando Gmsh...\n");
            report.append(executeBinary(gmsh.getAbsolutePath(), workDir, nativeLibDir, 
                "cantilever.geo", "-3", "-format", "inp", "-o", workDir.getAbsolutePath() + "/cantilever_hueco.inp"));

            // 3. Ejecutar CalculiX
            File ccx = new File(nativeLibDir, "libccx.so");
            if (!ccx.exists()) ccx = new File(new File(workDir, "usr/bin"), "ccx");
            
            report.append("\nEjecutando CalculiX...\n");
            report.append(executeBinary(ccx.getAbsolutePath(), workDir, nativeLibDir, 
                "-i", workDir.getAbsolutePath() + "/cantilever_hueco"));

            // 4. Parsear resultados (.frd)
            File frdFile = new File(workDir, "cantilever_hueco.frd");
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
