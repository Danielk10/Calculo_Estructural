package com.diamon.civil.engine;

import java.io.*;
import java.util.*;

/**
 * Utility to assemble final .inp files for CalculiX.
 * Ported from verified simulation tests to the main engine.
 */
public class InpAssembler {
    public static void assemble(File workDir, String inputName, String materialName, double E, double nu, double loadValue, String fixedId, String loadId) throws IOException {
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

        // 1. Extract NSETs from physical surfaces (Gmsh format) or dynamically selected Ray-Casted IDs
        Set<Integer> fixedNodes = extractNodesFromPhysical(lines, fixedId);
        if (fixedNodes.isEmpty() && isInteger(fixedId)) fixedNodes.add(Integer.parseInt(fixedId));
        if (fixedNodes.isEmpty()) fixedNodes = extractNodesFromPhysical(lines, "Fixed"); // Fallback
        if (fixedNodes.isEmpty()) fixedNodes = extractNodesFromPhysical(lines, "SURFACE1"); // Fallback
        
        Set<Integer> loadedNodes = extractNodesFromPhysical(lines, loadId);
        if (loadedNodes.isEmpty() && isInteger(loadId)) loadedNodes.add(Integer.parseInt(loadId));
        if (loadedNodes.isEmpty()) loadedNodes = extractNodesFromPhysical(lines, "Loaded"); // Fallback
        if (loadedNodes.isEmpty()) loadedNodes = extractNodesFromPhysical(lines, "SURFACE2"); // Fallback

        if (fixedNodes.isEmpty() || loadedNodes.isEmpty()) {
            Map<Integer, double[]> nodeCoords = new HashMap<>();
            boolean inNodeBlock = false;
            for (String line : lines) {
                String u = line.trim().toUpperCase();
                if (u.startsWith("*NODE")) {
                    inNodeBlock = true;
                    continue;
                }
                if (inNodeBlock) {
                    if (u.startsWith("*")) {
                        inNodeBlock = false;
                        continue;
                    }
                    String[] parts = line.trim().split(",");
                    if (parts.length >= 4) {
                        try {
                            int id = Integer.parseInt(parts[0].trim());
                            double x = Double.parseDouble(parts[1].trim());
                            double y = Double.parseDouble(parts[2].trim());
                            double z = Double.parseDouble(parts[3].trim());
                            nodeCoords.put(id, new double[]{x, y, z});
                        } catch (NumberFormatException ignore) {}
                    }
                }
            }
            if (!nodeCoords.isEmpty()) {
                double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
                double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
                double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
                for (double[] coords : nodeCoords.values()) {
                    minX = Math.min(minX, coords[0]); maxX = Math.max(maxX, coords[0]);
                    minY = Math.min(minY, coords[1]); maxY = Math.max(maxY, coords[1]);
                    minZ = Math.min(minZ, coords[2]); maxZ = Math.max(maxZ, coords[2]);
                }
                double dx = maxX - minX;
                double dy = maxY - minY;
                double dz = maxZ - minZ;
                
                int axis = 2; // Default Z
                double minVal = minZ, maxVal = maxZ;
                if (dx > dy && dx > dz) { axis = 0; minVal = minX; maxVal = maxX; }
                else if (dy > dx && dy > dz) { axis = 1; minVal = minY; maxVal = maxY; }
                
                double range = maxVal - minVal;
                double tol = range * 0.05; // 5% tolerance
                
                for (Map.Entry<Integer, double[]> entry : nodeCoords.entrySet()) {
                    double val = entry.getValue()[axis];
                    if (fixedNodes.isEmpty() && val <= minVal + tol) {
                        fixedNodes.add(entry.getKey());
                    }
                    if (loadedNodes.isEmpty() && val >= maxVal - tol) {
                        loadedNodes.add(entry.getKey());
                    }
                }
            }
        }

        if (fixedNodes.isEmpty() || loadedNodes.isEmpty()) {
            throw new IOException("La malla no contiene las superficies físicas Fixed/Loaded y la selección por coordenadas espaciales falló.");
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(nsetsInp))) {
            pw.println("*NSET, NSET=NFix");
            writeNodes(pw, fixedNodes);
            pw.println("*NSET, NSET=NLoad");
            writeNodes(pw, loadedNodes);
        }

        // 2. Clean up main mesh and remove incompatible elements (like CPS3)
        try (PrintWriter pw = new PrintWriter(new FileWriter(cleanInp))) {
            boolean inElementBlock = false;
            boolean skipCurrentBlock = false;
            for (String line : lines) {
                String u = line.trim().toUpperCase();
                
                // Ensure we use a valid element type for 3D (C3D4 for Gmsh tetras)
                if (u.startsWith("*ELEMENT")) {
                    if (u.contains("TYPE=CPS3") || u.contains("TYPE=T3D2")) {
                        skipCurrentBlock = true;
                        continue;
                    }
                    if (u.contains("TYPE=TET4") || u.contains("TYPE=C3D4")) {
                        pw.println("*ELEMENT, TYPE=C3D4, ELSET=Eall");
                        inElementBlock = true;
                        skipCurrentBlock = false;
                        continue;
                    }
                }
                
                if (u.startsWith("*") && !u.startsWith("*ELEMENT") && skipCurrentBlock) {
                    skipCurrentBlock = false;
                }

                if (skipCurrentBlock) continue;
                
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

    private static boolean isInteger(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
