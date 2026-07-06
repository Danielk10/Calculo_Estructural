package com.diamon.civil.engine;

import java.io.*;
import java.util.*;

/**
 * Utility to assemble final .inp files for CalculiX.
 * Ported from verified simulation tests to the main engine.
 */
public class InpAssembler {
    public static void assemble(File workDir, String inputName, String materialName, double E, double nu, double loadValue, String elsetType) throws IOException {
        File rawInp = new File(workDir, inputName + "_raw.inp");
        File cleanInp = new File(workDir, inputName + "_clean.inp");
        File nsetsInp = new File(workDir, "nsets.inp");
        File finalInp = new File(workDir, inputName + ".inp");

        if (!rawInp.exists()) {
            throw new FileNotFoundException("Raw INP not found: " + rawInp.getAbsolutePath());
        }

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(rawInp))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        }

        // 1. Extract NSETs from physical surfaces (Gmsh format)
        Set<Integer> fixedNodes = extractNodesFromPhysical(lines, "Fixed");
        Set<Integer> loadedNodes = extractNodesFromPhysical(lines, "Loaded");

        try (PrintWriter pw = new PrintWriter(new FileWriter(nsetsInp))) {
            pw.println("*NSET, NSET=NFix");
            writeNodes(pw, fixedNodes);
            pw.println("*NSET, NSET=NLoad");
            writeNodes(pw, loadedNodes);
        }

        // 2. Clean up main mesh (remove Gmsh specific boundary definitions)
        try (PrintWriter pw = new PrintWriter(new FileWriter(cleanInp))) {
            for (String line : lines) {
                String u = line.trim().toUpperCase();
                if (u.startsWith("*BOUNDARY") || u.startsWith("*STEP") || u.startsWith("*CLOAD")) {
                    break; 
                }
                pw.println(line);
            }
        }

        // 3. Assemble final INP with professional engineering logic
        try (PrintWriter pw = new PrintWriter(new FileWriter(finalInp))) {
            pw.println("*INCLUDE, INPUT=" + cleanInp.getName());
            pw.println("*INCLUDE, INPUT=" + nsetsInp.getName());
            pw.println("*MATERIAL, NAME=" + materialName.replace(" ", "_"));
            pw.println("*ELASTIC");
            pw.println(E + ", " + nu);
            
            // Apply section to all elements using the specified type or a general set
            pw.println("*SOLID SECTION, ELSET=Eall, MATERIAL=" + materialName.replace(" ", "_"));
            
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*BOUNDARY");
            pw.println("NFix, 1, 3, 0.0");
            pw.println("*CLOAD");
            pw.println("NLoad, 2, " + loadValue);
            pw.println("*NODE FILE");
            pw.println("U");
            pw.println("*EL FILE");
            pw.println("S, E");
            pw.println("*END STEP");
        }
    }

    private static Set<Integer> extractNodesFromPhysical(List<String> lines, String setName) {
        Set<Integer> nodes = new TreeSet<>();
        boolean capture = false;
        for (String line : lines) {
            String u = line.trim().toUpperCase();
            if (u.startsWith("*ELEMENT") && u.contains("ELSET=" + setName.toUpperCase())) {
                capture = true;
                continue;
            }
            if (capture) {
                if (u.startsWith("*")) {
                    capture = false;
                    continue;
                }
                String[] parts = line.trim().split(",");
                for (int i = 1; i < parts.length; i++) {
                    String p = parts[i].trim();
                    if (!p.isEmpty()) {
                        try {
                            nodes.add(Integer.parseInt(p));
                        } catch (NumberFormatException ignore) {}
                    }
                }
            }
        }
        return nodes;
    }

    private static void writeNodes(PrintWriter pw, Set<Integer> nodes) {
        int count = 0;
        for (Integer node : nodes) {
            pw.print(node + (count % 10 == 9 || count == nodes.size() - 1 ? "" : ","));
            if (++count % 10 == 0) pw.println();
        }
        pw.println();
    }
}
