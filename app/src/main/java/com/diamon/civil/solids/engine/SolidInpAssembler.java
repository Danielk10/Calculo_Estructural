package com.diamon.civil.solids.engine;

import java.io.*;
import java.util.*;

/**
 * Utility to assemble final .inp files for CalculiX 3D Solids.
 * Ported from InpAssembler to keep the solids module completely independent.
 */
public class SolidInpAssembler {
    public static void assemble(File workDir, String inputName, String materialName, double E, double nu, double loadValue, String fixedId, String loadId) throws IOException {
        assemble(workDir, inputName, materialName, E, nu, loadValue, 2, fixedId, loadId);
    }

    public static void assemble(File workDir, String inputName, String materialName, double E, double nu, double totalLoadValue, int loadDof, String fixedRegion, String loadRegion) throws IOException {
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

        // 1. Parse all node coordinates
        Map<Integer, double[]> nodeCoords = new HashMap<>();
        boolean inNodeBlock = false;
        for (String line : lines) {
            String u = line.trim().toUpperCase(Locale.US);
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

        // 2. Resolve fixed and loaded node sets according to spatial region or physical face
        Set<Integer> fixedNodes = resolveRegionNodes(lines, nodeCoords, fixedRegion, true);
        Set<Integer> loadedNodes = resolveRegionNodes(lines, nodeCoords, loadRegion, false);

        if (fixedNodes.isEmpty() || loadedNodes.isEmpty()) {
            throw new IOException("Boundary condition assignment failed: fixed or loaded nodes could not be determined.");
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(nsetsInp))) {
            pw.println("*NSET, NSET=NFix");
            writeNodes(pw, fixedNodes);
            pw.println("*NSET, NSET=NLoad");
            writeNodes(pw, loadedNodes);
        }

        // 2. Clean up main mesh and retain ONLY valid 3D continuum solid elements (C3D4, C3D10, C3D8, C3D8R, C3D20, C3D20R, C3D6, C3D15)
        int elementCount = 0;
        try (PrintWriter pw = new PrintWriter(new FileWriter(cleanInp))) {
            boolean inElementBlock = false;
            boolean skipCurrentBlock = false;
            String current3DType = null;
            for (String line : lines) {
                String u = line.trim().toUpperCase(Locale.US);
                
                if (u.startsWith("*NODE")) {
                    pw.println("*NODE, NSET=NALL");
                    inElementBlock = false;
                    skipCurrentBlock = false;
                    current3DType = null;
                    continue;
                }
                
                if (u.startsWith("*ELEMENT")) {
                    if (u.contains("TYPE=TETRA4") || u.contains("TYPE=TET4") || u.contains("TYPE=C3D4")) {
                        pw.println("*ELEMENT, TYPE=C3D4, ELSET=Eall");
                        inElementBlock = true;
                        skipCurrentBlock = false;
                        current3DType = "C3D4";
                        continue;
                    } else if (u.contains("TYPE=TETRA10") || u.contains("TYPE=C3D10") || u.contains("TYPE=TET10")) {
                        pw.println("*ELEMENT, TYPE=C3D10, ELSET=Eall");
                        inElementBlock = true;
                        skipCurrentBlock = false;
                        current3DType = "C3D10";
                        continue;
                    } else if (u.contains("TYPE=HEXA8R") || u.contains("TYPE=C3D8R") || u.contains("TYPE=HEX8R")) {
                        pw.println("*ELEMENT, TYPE=C3D8R, ELSET=Eall");
                        inElementBlock = true;
                        skipCurrentBlock = false;
                        current3DType = "C3D8R";
                        continue;
                    } else if (u.contains("TYPE=HEXA8") || u.contains("TYPE=C3D8") || u.contains("TYPE=HEX8")) {
                        pw.println("*ELEMENT, TYPE=C3D8, ELSET=Eall");
                        inElementBlock = true;
                        skipCurrentBlock = false;
                        current3DType = "C3D8";
                        continue;
                    } else if (u.contains("TYPE=HEXA20R") || u.contains("TYPE=C3D20R") || u.contains("TYPE=HEX20R")) {
                        pw.println("*ELEMENT, TYPE=C3D20R, ELSET=Eall");
                        inElementBlock = true;
                        skipCurrentBlock = false;
                        current3DType = "C3D20R";
                        continue;
                    } else if (u.contains("TYPE=HEXA20") || u.contains("TYPE=C3D20") || u.contains("TYPE=HEX20") || u.contains("TYPE=C3D27") || u.contains("TYPE=HEX27") || u.contains("TYPE=HEXA27")) {
                        pw.println("*ELEMENT, TYPE=C3D20, ELSET=Eall");
                        inElementBlock = true;
                        skipCurrentBlock = false;
                        current3DType = "C3D20";
                        continue;
                    } else if (u.contains("TYPE=PRISM6") || u.contains("TYPE=C3D6") || u.contains("TYPE=WED6") || u.contains("TYPE=PRI6")) {
                        pw.println("*ELEMENT, TYPE=C3D6, ELSET=Eall");
                        inElementBlock = true;
                        skipCurrentBlock = false;
                        current3DType = "C3D6";
                        continue;
                    } else if (u.contains("TYPE=PRISM15") || u.contains("TYPE=C3D15") || u.contains("TYPE=WED15") || u.contains("TYPE=PRI15") || u.contains("TYPE=PRI18") || u.contains("TYPE=WED18") || u.contains("TYPE=PRISM18")) {
                        pw.println("*ELEMENT, TYPE=C3D15, ELSET=Eall");
                        inElementBlock = true;
                        skipCurrentBlock = false;
                        current3DType = "C3D15";
                        continue;
                    } else {
                        // Any 1D/2D or non-solid element (M3D9, M3D8, M3D4, CPS3, CPS4, CPS8, S4, S8, B31, etc.) must be skipped
                        skipCurrentBlock = true;
                        inElementBlock = false;
                        current3DType = null;
                        continue;
                    }
                }
                
                if (u.startsWith("*") && !u.startsWith("*ELEMENT")) {
                    if (u.startsWith("*BOUNDARY") || u.startsWith("*STEP") || u.startsWith("*CLOAD") || u.startsWith("*END")) {
                        break; 
                    }
                    skipCurrentBlock = true;
                    inElementBlock = false;
                    current3DType = null;
                    continue;
                }

                if (skipCurrentBlock) continue;
                
                if (inElementBlock && line.contains(",")) {
                    String[] parts = line.trim().split(",");
                    if ("C3D20".equals(current3DType) || "C3D20R".equals(current3DType)) {
                        // Truncate C3D27 (27 nodes) to standard CalculiX C3D20 (20 nodes)
                        if (parts.length > 21) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < 21; i++) {
                                sb.append(parts[i].trim()).append(i < 20 ? ", " : "");
                            }
                            line = sb.toString();
                        }
                    } else if ("C3D15".equals(current3DType)) {
                        // Truncate C3D18 (18 nodes) to standard CalculiX C3D15 (15 nodes)
                        if (parts.length > 16) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < 16; i++) {
                                sb.append(parts[i].trim()).append(i < 15 ? ", " : "");
                            }
                            line = sb.toString();
                        }
                    }
                    pw.println(line);
                    elementCount++;
                } else if (!inElementBlock) {
                    pw.println(line);
                }
            }
        }

        if (elementCount == 0) {
            throw new IOException("Mesh generation produced 0 3D solid continuum elements (C3D4/C3D10/C3D8/C3D6). The CAD model could not be discretized as a 3D solid volume.");
        }

        // 3. Assemble final INP with professional engineering logic
        try (PrintWriter pw = new PrintWriter(new FileWriter(finalInp))) {
            pw.println("*INCLUDE, INPUT=" + cleanInp.getName());
            pw.println("*INCLUDE, INPUT=" + nsetsInp.getName());
            String safeMaterialName = (materialName != null && !materialName.trim().isEmpty())
                    ? materialName.trim().replaceAll("[^a-zA-Z0-9_]", "_")
                    : "Steel";
            pw.println("*MATERIAL, NAME=" + safeMaterialName);
            pw.println("*ELASTIC");
            pw.println(E + ", " + nu);
            
            // Apply section to all elements using the specified type or a general set
            pw.println("*SOLID SECTION, ELSET=Eall, MATERIAL=" + safeMaterialName);
            
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*BOUNDARY");
            pw.println("NFix, 1, 3, 0.0");
            
            // Distribute total load evenly among loaded nodes
            double nodalLoad = loadedNodes.size() > 0 ? (totalLoadValue / loadedNodes.size()) : totalLoadValue;
            int dof = (loadDof >= 1 && loadDof <= 3) ? loadDof : 2;
            pw.println("*CLOAD");
            pw.println("NLoad, " + dof + ", " + String.format(Locale.US, "%.6e", nodalLoad));
            
            pw.println("*NODE FILE");
            pw.println("U");
            pw.println("*EL FILE");
            pw.println("S, E");
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*EL PRINT, ELSET=Eall");
            pw.println("S");
            pw.println("*END STEP");
        }
    }

    private static Set<Integer> resolveRegionNodes(List<String> lines, Map<Integer, double[]> nodeCoords, String regionName, boolean isFixed) {
        Set<Integer> nodes = new TreeSet<>();
        if (regionName == null || regionName.trim().isEmpty() || regionName.equalsIgnoreCase("AUTO") || regionName.contains("Auto")) {
            // First check standard physical groups in Gmsh
            if (isFixed) {
                nodes = extractNodesFromPhysical(lines, "Fixed");
                if (nodes.isEmpty()) nodes = extractNodesFromPhysical(lines, "SURFACE1");
                if (nodes.isEmpty()) nodes = extractNodesFromPhysical(lines, "NFix");
            } else {
                nodes = extractNodesFromPhysical(lines, "Loaded");
                if (nodes.isEmpty()) nodes = extractNodesFromPhysical(lines, "SURFACE2");
                if (nodes.isEmpty()) nodes = extractNodesFromPhysical(lines, "NLoad");
            }
            if (!nodes.isEmpty()) return nodes;
            
            // Auto fallback: determine longest axis
            return extractSpatialFaceNodes(nodeCoords, isFixed ? "AUTO_MIN" : "AUTO_MAX");
        }

        String reg = regionName.toUpperCase(Locale.US);
        if (reg.contains("X-") || reg.contains("X_MIN") || reg.contains("LEFT")) {
            return extractSpatialFaceNodes(nodeCoords, "X_MIN");
        }
        if (reg.contains("X+") || reg.contains("X_MAX") || reg.contains("RIGHT")) {
            return extractSpatialFaceNodes(nodeCoords, "X_MAX");
        }
        if (reg.contains("Y-") || reg.contains("Y_MIN") || reg.contains("BOTTOM") || reg.contains("BASE")) {
            return extractSpatialFaceNodes(nodeCoords, "Y_MIN");
        }
        if (reg.contains("Y+") || reg.contains("Y_MAX") || reg.contains("TOP") || reg.contains("ROOF")) {
            return extractSpatialFaceNodes(nodeCoords, "Y_MAX");
        }
        if (reg.contains("Z-") || reg.contains("Z_MIN") || reg.contains("BACK")) {
            return extractSpatialFaceNodes(nodeCoords, "Z_MIN");
        }
        if (reg.contains("Z+") || reg.contains("Z_MAX") || reg.contains("FRONT")) {
            return extractSpatialFaceNodes(nodeCoords, "Z_MAX");
        }

        // Try as physical surface name
        nodes = extractNodesFromPhysical(lines, regionName);
        if (!nodes.isEmpty()) return nodes;

        // Try as single node ID
        if (isInteger(regionName)) {
            int id = Integer.parseInt(regionName);
            if (nodeCoords != null && nodeCoords.containsKey(id)) {
                nodes.add(id);
                return nodes;
            }
        }

        return extractSpatialFaceNodes(nodeCoords, isFixed ? "AUTO_MIN" : "AUTO_MAX");
    }

    private static Set<Integer> extractSpatialFaceNodes(Map<Integer, double[]> nodeCoords, String faceType) {
        Set<Integer> result = new TreeSet<>();
        if (nodeCoords == null || nodeCoords.isEmpty()) return result;

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (double[] c : nodeCoords.values()) {
            minX = Math.min(minX, c[0]); maxX = Math.max(maxX, c[0]);
            minY = Math.min(minY, c[1]); maxY = Math.max(maxY, c[1]);
            minZ = Math.min(minZ, c[2]); maxZ = Math.max(maxZ, c[2]);
        }

        double dx = Math.max(maxX - minX, 1e-6);
        double dy = Math.max(maxY - minY, 1e-6);
        double dz = Math.max(maxZ - minZ, 1e-6);

        double tolX = Math.max(dx * 0.05, 1e-4);
        double tolY = Math.max(dy * 0.05, 1e-4);
        double tolZ = Math.max(dz * 0.05, 1e-4);

        if (faceType.equals("X_MIN")) {
            for (Map.Entry<Integer, double[]> e : nodeCoords.entrySet()) {
                if (e.getValue()[0] <= minX + tolX) result.add(e.getKey());
            }
        } else if (faceType.equals("X_MAX")) {
            for (Map.Entry<Integer, double[]> e : nodeCoords.entrySet()) {
                if (e.getValue()[0] >= maxX - tolX) result.add(e.getKey());
            }
        } else if (faceType.equals("Y_MIN")) {
            for (Map.Entry<Integer, double[]> e : nodeCoords.entrySet()) {
                if (e.getValue()[1] <= minY + tolY) result.add(e.getKey());
            }
        } else if (faceType.equals("Y_MAX")) {
            for (Map.Entry<Integer, double[]> e : nodeCoords.entrySet()) {
                if (e.getValue()[1] >= maxY - tolY) result.add(e.getKey());
            }
        } else if (faceType.equals("Z_MIN")) {
            for (Map.Entry<Integer, double[]> e : nodeCoords.entrySet()) {
                if (e.getValue()[2] <= minZ + tolZ) result.add(e.getKey());
            }
        } else if (faceType.equals("Z_MAX")) {
            for (Map.Entry<Integer, double[]> e : nodeCoords.entrySet()) {
                if (e.getValue()[2] >= maxZ - tolZ) result.add(e.getKey());
            }
        } else {
            // AUTO selection along longest dimension
            int axis = 0;
            double minVal = minX, maxVal = maxX, tol = tolX;
            if (dy > dx && dy >= dz) {
                axis = 1; minVal = minY; maxVal = maxY; tol = tolY;
            } else if (dz > dx && dz > dy) {
                axis = 2; minVal = minZ; maxVal = maxZ; tol = tolZ;
            }

            boolean wantMin = faceType.equals("AUTO_MIN");
            for (Map.Entry<Integer, double[]> e : nodeCoords.entrySet()) {
                double val = e.getValue()[axis];
                if (wantMin && val <= minVal + tol) {
                    result.add(e.getKey());
                } else if (!wantMin && val >= maxVal - tol) {
                    result.add(e.getKey());
                }
            }
        }

        return result;
    }

    private static Set<Integer> extractNodesFromPhysical(List<String> lines, String setName) {
        Set<Integer> nodes = new TreeSet<>();
        if (setName == null) return nodes;
        boolean capture = false;
        for (String line : lines) {
            String u = line.trim().toUpperCase(Locale.US);
            if (u.startsWith("*ELEMENT") && u.contains("ELSET=" + setName.toUpperCase(Locale.US))) {
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
