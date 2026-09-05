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
        assemble(workDir, inputName, materialName, E, nu, totalLoadValue, loadDof, fixedRegion, loadRegion, null);
    }

    public static void assemble(File workDir, String inputName, String materialName, double E, double nu, double totalLoadValue, int loadDof, String fixedRegion, String loadRegion, String requestedElementType) throws IOException {
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
        Map<Integer, double[]> nodeCoords = new TreeMap<>();
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

        // Ensure physical realism: loaded nodes cannot be fixed supports.
        // If the user selected the identical region for both, or there is partial overlap:
        if (fixedNodes.containsAll(loadedNodes)) {
            boolean fixedIsMin = true;
            if (fixedRegion != null) {
                String fr = fixedRegion.toUpperCase(Locale.US);
                if (fr.contains("MAX") || fr.contains("RIGHT") || fr.contains("TOP") || fr.contains("FRONT") || fr.contains("+")) {
                    fixedIsMin = false;
                }
            }
            loadedNodes = extractSpatialFaceNodes(nodeCoords, fixedIsMin ? "AUTO_MAX" : "AUTO_MIN");
        }
        loadedNodes.removeAll(fixedNodes);

        if (loadedNodes.isEmpty() && !nodeCoords.isEmpty()) {
            if (fixedNodes.size() >= nodeCoords.size() && fixedNodes.size() > 1) {
                // Fixed nodes swallowed the entire mesh. Release the furthest node for load.
                int lastNode = -1;
                for (int n : fixedNodes) lastNode = n;
                if (lastNode != -1) {
                    fixedNodes.remove(lastNode);
                    loadedNodes.add(lastNode);
                }
            } else {
                // If overlap removed all loaded nodes, find nodes with maximum distance from fixed nodes
                double maxDist = -1;
                int bestNode = -1;
                for (Map.Entry<Integer, double[]> entry : nodeCoords.entrySet()) {
                    if (fixedNodes.contains(entry.getKey())) continue;
                    double[] c = entry.getValue();
                    double d = 0;
                    for (int fn : fixedNodes) {
                        double[] fc = nodeCoords.get(fn);
                        if (fc != null) {
                            double dx_n = c[0] - fc[0], dy_n = c[1] - fc[1], dz_n = c[2] - fc[2];
                            d += dx_n * dx_n + dy_n * dy_n + dz_n * dz_n;
                        }
                    }
                    if (d > maxDist) {
                        maxDist = d;
                        bestNode = entry.getKey();
                    }
                }
                if (bestNode != -1) {
                    loadedNodes.add(bestNode);
                }
            }
        }

        if (fixedNodes.isEmpty() || loadedNodes.isEmpty()) {
            throw new IOException("Boundary condition assignment failed: fixed or loaded nodes could not be determined.");
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(nsetsInp))) {
            pw.println("*NSET, NSET=NFix");
            writeNodes(pw, fixedNodes);
            pw.println("*NSET, NSET=NLoad");
            writeNodes(pw, loadedNodes);
        }

        // 3. Clean up main mesh and retain ONLY valid 3D continuum solid elements
        // (C3D4, C3D8, C3D8R, C3D6, C3D10, C3D20, C3D20R, C3D15)
        String targetElemType = null;
        if (requestedElementType != null && !requestedElementType.trim().isEmpty()) {
            String req = requestedElementType.toUpperCase(Locale.US);
            if (req.contains("C3D8R")) targetElemType = "C3D8R";
            else if (req.contains("C3D8")) targetElemType = "C3D8";
            else if (req.contains("C3D20R")) targetElemType = "C3D20R";
            else if (req.contains("C3D20")) targetElemType = "C3D20";
            else if (req.contains("C3D10")) targetElemType = "C3D10";
            else if (req.contains("C3D4")) targetElemType = "C3D4";
            else if (req.contains("C3D15")) targetElemType = "C3D15";
            else if (req.contains("C3D6")) targetElemType = "C3D6";
        }

        List<String> nodeLines = new ArrayList<>();
        List<String> elementLines = new ArrayList<>();
        int elementCount = 0;

        inNodeBlock = false;
        boolean inElementBlock = false;
        boolean skipCurrentBlock = false;
        String current3DType = null;

        for (String line : lines) {
            String u = line.trim().toUpperCase(Locale.US);

            if (u.startsWith("*NODE")) {
                inNodeBlock = true;
                inElementBlock = false;
                skipCurrentBlock = false;
                current3DType = null;
                continue;
            }

            if (u.startsWith("*ELEMENT")) {
                inNodeBlock = false;
                if (u.contains("TYPE=TETRA4") || u.contains("TYPE=TET4") || u.contains("TYPE=C3D4")) {
                    String outType = (targetElemType != null && targetElemType.startsWith("C3D4")) ? targetElemType : "C3D4";
                    elementLines.add("*ELEMENT, TYPE=" + outType + ", ELSET=Eall");
                    inElementBlock = true;
                    skipCurrentBlock = false;
                    current3DType = "C3D4";
                    continue;
                } else if (u.contains("TYPE=TETRA10") || u.contains("TYPE=C3D10") || u.contains("TYPE=TET10")) {
                    String outType = (targetElemType != null && targetElemType.startsWith("C3D10")) ? targetElemType : "C3D10";
                    elementLines.add("*ELEMENT, TYPE=" + outType + ", ELSET=Eall");
                    inElementBlock = true;
                    skipCurrentBlock = false;
                    current3DType = "C3D10";
                    continue;
                } else if (u.contains("TYPE=HEXA8R") || u.contains("TYPE=C3D8R") || u.contains("TYPE=HEX8R")) {
                    String outType = (targetElemType != null && targetElemType.equals("C3D8")) ? "C3D8" : "C3D8R";
                    elementLines.add("*ELEMENT, TYPE=" + outType + ", ELSET=Eall");
                    inElementBlock = true;
                    skipCurrentBlock = false;
                    current3DType = "C3D8R";
                    continue;
                } else if (u.contains("TYPE=HEXA8") || u.contains("TYPE=C3D8") || u.contains("TYPE=HEX8")) {
                    String outType = (targetElemType != null && targetElemType.equals("C3D8R")) ? "C3D8R" : "C3D8";
                    if (targetElemType != null && targetElemType.startsWith("C3D6")) {
                        outType = "C3D6";
                    }
                    elementLines.add("*ELEMENT, TYPE=" + outType + ", ELSET=Eall");
                    inElementBlock = true;
                    skipCurrentBlock = false;
                    current3DType = "C3D6".equals(outType) ? "C3D8_TO_C3D6" : "C3D8";
                    continue;
                } else if (u.contains("TYPE=HEXA20R") || u.contains("TYPE=C3D20R") || u.contains("TYPE=HEX20R")) {
                    String outType = (targetElemType != null && targetElemType.equals("C3D20")) ? "C3D20" : "C3D20R";
                    elementLines.add("*ELEMENT, TYPE=" + outType + ", ELSET=Eall");
                    inElementBlock = true;
                    skipCurrentBlock = false;
                    current3DType = "C3D20R";
                    continue;
                } else if (u.contains("TYPE=HEXA20") || u.contains("TYPE=C3D20") || u.contains("TYPE=HEX20") || u.contains("TYPE=C3D27") || u.contains("TYPE=HEX27") || u.contains("TYPE=HEXA27")) {
                    String outType = (targetElemType != null && targetElemType.equals("C3D20R")) ? "C3D20R" : "C3D20";
                    elementLines.add("*ELEMENT, TYPE=" + outType + ", ELSET=Eall");
                    inElementBlock = true;
                    skipCurrentBlock = false;
                    current3DType = "C3D20";
                    continue;
                } else if (u.contains("TYPE=PRISM6") || u.contains("TYPE=C3D6") || u.contains("TYPE=WED6") || u.contains("TYPE=PRI6")) {
                    String outType = (targetElemType != null && targetElemType.startsWith("C3D6")) ? targetElemType : "C3D6";
                    elementLines.add("*ELEMENT, TYPE=" + outType + ", ELSET=Eall");
                    inElementBlock = true;
                    skipCurrentBlock = false;
                    current3DType = "C3D6";
                    continue;
                } else if (u.contains("TYPE=PRISM15") || u.contains("TYPE=C3D15") || u.contains("TYPE=WED15") || u.contains("TYPE=PRI15") || u.contains("TYPE=PRI18") || u.contains("TYPE=WED18") || u.contains("TYPE=PRISM18")) {
                    String outType = (targetElemType != null && targetElemType.startsWith("C3D15")) ? targetElemType : "C3D15";
                    elementLines.add("*ELEMENT, TYPE=" + outType + ", ELSET=Eall");
                    inElementBlock = true;
                    skipCurrentBlock = false;
                    current3DType = "C3D15";
                    continue;
                } else {
                    // Any 1D/2D or non-solid element (M3D9, M3D8, M3D4, CPS3, CPS4, CPS6, CPS8, S4, S8, B31, etc.) must be skipped
                    skipCurrentBlock = true;
                    inElementBlock = false;
                    current3DType = null;
                    continue;
                }
            }

            if (u.startsWith("*") && !u.startsWith("*ELEMENT") && !u.startsWith("*NODE")) {
                inNodeBlock = false;
                if (u.startsWith("*BOUNDARY") || u.startsWith("*STEP") || u.startsWith("*CLOAD") || u.startsWith("*END")) {
                    break;
                }
                skipCurrentBlock = true;
                inElementBlock = false;
                current3DType = null;
                continue;
            }

            if (skipCurrentBlock) continue;

            if (inNodeBlock && line.contains(",")) {
                nodeLines.add(line);
            } else if (inElementBlock && line.contains(",")) {
                String formattedLine = line;
                String[] parts = line.trim().split(",");
                if ("C3D8_TO_C3D6".equals(current3DType)) {
                    if (parts.length >= 9) {
                        String n1 = parts[1].trim(), n2 = parts[2].trim(), n3 = parts[3].trim(), n4 = parts[4].trim();
                        String n5 = parts[5].trim(), n6 = parts[6].trim(), n7 = parts[7].trim(), n8 = parts[8].trim();
                        elementCount++;
                        elementLines.add(elementCount + ", " + n1 + ", " + n2 + ", " + n3 + ", " + n5 + ", " + n6 + ", " + n7);
                        elementCount++;
                        elementLines.add(elementCount + ", " + n1 + ", " + n3 + ", " + n4 + ", " + n5 + ", " + n7 + ", " + n8);
                        continue;
                    }
                } else if ("C3D20".equals(current3DType) || "C3D20R".equals(current3DType)) {
                    // Truncate C3D27 (27 nodes) to standard serendipity CalculiX C3D20 (20 nodes)
                    if (parts.length > 21) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < 21; i++) {
                            sb.append(parts[i].trim()).append(i < 20 ? ", " : "");
                        }
                        formattedLine = sb.toString();
                    }
                } else if ("C3D15".equals(current3DType)) {
                    // Truncate C3D18 (18 nodes) to standard serendipity CalculiX C3D15 (15 nodes)
                    if (parts.length > 16) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < 16; i++) {
                            sb.append(parts[i].trim()).append(i < 15 ? ", " : "");
                        }
                        formattedLine = sb.toString();
                    }
                }
                elementLines.add(formattedLine);
                elementCount++;
            }
        }

        if (elementCount == 0) {
            throw new IOException("Mesh generation produced 0 3D solid continuum elements (C3D4/C3D10/C3D8/C3D8R/C3D20/C3D20R/C3D6/C3D15). The CAD model could not be discretized as a 3D solid volume.");
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(cleanInp))) {
            pw.println("*NODE, NSET=NALL");
            for (String nl : nodeLines) {
                pw.println(nl);
            }
            for (String el : elementLines) {
                pw.println(el);
            }
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
            // Check standard physical groups in Gmsh (both direct and indirect ELSETs)
            if (isFixed) {
                String[] fixedAliases = {"Fixed", "N_FIXED_SURF", "NFix", "FIXED_SURF", "FIXED_NODES", "SUPPORT", "EMPOTRAMIENTO"};
                for (String alias : fixedAliases) {
                    nodes = extractNodesFromPhysical(lines, alias);
                    if (!nodes.isEmpty()) return nodes;
                }
            } else {
                String[] loadAliases = {"Loaded", "E_LOAD_FACETS", "NLoad", "LOAD_SURF", "LOAD_NODES", "FORCE", "CARGA"};
                for (String alias : loadAliases) {
                    nodes = extractNodesFromPhysical(lines, alias);
                    if (!nodes.isEmpty()) return nodes;
                }
            }
            if (!nodes.isEmpty()) return nodes;
            
            // Auto fallback: determine longest axis
            return extractSpatialFaceNodes(nodeCoords, isFixed ? "AUTO_MIN" : "AUTO_MAX");
        }

        String reg = regionName.toUpperCase(Locale.US);
        if (reg.contains("X-") || reg.contains("X_MIN") || reg.contains("LEFT") || reg.contains("IZQUIERD")) {
            return extractSpatialFaceNodes(nodeCoords, "X_MIN");
        }
        if (reg.contains("X+") || reg.contains("X_MAX") || reg.contains("RIGHT") || reg.contains("DERECH")) {
            return extractSpatialFaceNodes(nodeCoords, "X_MAX");
        }
        if (reg.contains("Y-") || reg.contains("Y_MIN") || reg.contains("BOTTOM") || reg.contains("BASE") || reg.contains("INFERIOR")) {
            return extractSpatialFaceNodes(nodeCoords, "Y_MIN");
        }
        if (reg.contains("Y+") || reg.contains("Y_MAX") || reg.contains("TOP") || reg.contains("ROOF") || reg.contains("SUPERIOR") || reg.contains("TECHO")) {
            return extractSpatialFaceNodes(nodeCoords, "Y_MAX");
        }
        if (reg.contains("Z-") || reg.contains("Z_MIN") || reg.contains("BACK") || reg.contains("POSTERIOR") || reg.contains("FONDO")) {
            return extractSpatialFaceNodes(nodeCoords, "Z_MIN");
        }
        if (reg.contains("Z+") || reg.contains("Z_MAX") || reg.contains("FRONT") || reg.contains("FRONTAL")) {
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

        // Adaptive tolerance: start tight (0.1%) and progressively widen
        // to capture at least 3 non-collinear nodes for kinematic stability.
        // For planar faces, the distributed non-collinearity check breaks at factor 0.001.
        // For curved faces (sphere, cylinder), it expands to capture the support ring.
        double[] toleranceFactors = {0.001, 0.005, 0.01, 0.02, 0.05, 0.10};

        for (double factor : toleranceFactors) {
            double tolX = Math.max(dx * factor, 1e-3);
            double tolY = Math.max(dy * factor, 1e-3);
            double tolZ = Math.max(dz * factor, 1e-3);

            result.clear();

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

            // Check kinematic sufficiency: need >= 3 non-collinear nodes
            // to prevent rigid body rotation around any axis
            if (areNodesKinematicallySufficient(result, nodeCoords)) {
                break; // Current tolerance captures enough well-distributed nodes
            }
        }

        // Robust safety fallback for curved geometry extremes (e.g. sphere apex, cone tip) or 1D lines
        if (result.size() < 3 && !nodeCoords.isEmpty()) {
            int selAxis = 0;
            if (faceType.contains("Y")) selAxis = 1;
            else if (faceType.contains("Z")) selAxis = 2;
            else if (faceType.contains("AUTO")) {
                if (dy > dx && dy >= dz) selAxis = 1;
                else if (dz > dx && dz > dy) selAxis = 2;
            }
            final int chosenAxis = selAxis;
            final boolean wantMin = faceType.contains("MIN") || faceType.contains("LEFT") || faceType.contains("BOTTOM") || faceType.contains("BASE") || faceType.contains("BACK");
            List<Map.Entry<Integer, double[]>> sorted = new ArrayList<>(nodeCoords.entrySet());
            sorted.sort((e1, e2) -> {
                double v1 = e1.getValue()[chosenAxis];
                double v2 = e2.getValue()[chosenAxis];
                int cmp = wantMin ? Double.compare(v1, v2) : Double.compare(v2, v1);
                if (cmp != 0) return cmp;
                return Integer.compare(e1.getKey(), e2.getKey());
            });
            int count = Math.min(4, Math.max(1, sorted.size() / 2));
            for (int i = 0; i < count; i++) {
                result.add(sorted.get(i).getKey());
            }
        }

        return result;
    }

    /**
     * Checks whether the selected node set is kinematically sufficient to
     * prevent all rigid body modes when used as a fixed boundary condition
     * on 3D continuum elements (which only have translational DOFs).
     *
     * Requirements:
     * - At least 3 nodes (to provide 9 SPCs, more than the 6 needed)
     * - The 3 nodes must NOT be collinear (otherwise rotation around the
     *   line they define remains unconstrained)
     *
     * Sampling is distributed across the entire set to ensure edge-numbered
     * nodes in fine meshes do not falsely appear 100% collinear.
     */
    private static boolean areNodesKinematicallySufficient(Set<Integer> nodeIds, Map<Integer, double[]> nodeCoords) {
        if (nodeIds == null || nodeIds.size() < 3 || nodeCoords == null) return false;

        // Collect all coordinates of the candidate nodes
        List<double[]> allPts = new ArrayList<>(nodeIds.size());
        for (int id : nodeIds) {
            double[] c = nodeCoords.get(id);
            if (c != null) allPts.add(c);
        }
        if (allPts.size() < 3) return false;

        // Distribute sample across the set rather than taking only the first 20 sequential IDs,
        // which on fine meshes may all lie on a single edge curve.
        List<double[]> pts = new ArrayList<>();
        int sampleSize = Math.min(40, allPts.size());
        int stride = Math.max(1, allPts.size() / sampleSize);
        for (int i = 0; i < allPts.size() && pts.size() < sampleSize; i += stride) {
            pts.add(allPts.get(i));
        }
        double[] lastPt = allPts.get(allPts.size() - 1);
        if (!pts.contains(lastPt)) {
            pts.add(lastPt);
        }

        // Find pair (a, b) with maximum separation distance
        double[] a = pts.get(0);
        double[] b = null;
        double maxDistSq = 0.0;
        for (double[] p : pts) {
            double dx = p[0] - a[0], dy = p[1] - a[1], dz = p[2] - a[2];
            double dSq = dx * dx + dy * dy + dz * dz;
            if (dSq > maxDistSq) {
                maxDistSq = dSq;
                b = p;
            }
        }
        if (b == null || maxDistSq < 1e-12) return false;

        // Vector AB
        double abx = b[0] - a[0], aby = b[1] - a[1], abz = b[2] - a[2];
        double abLen = Math.sqrt(maxDistSq);

        // Find any third point c not collinear with AB (sin(angle) > 0.01)
        for (double[] c : pts) {
            double acx = c[0] - a[0], acy = c[1] - a[1], acz = c[2] - a[2];
            double acLen = Math.sqrt(acx * acx + acy * acy + acz * acz);
            if (acLen < 1e-6) continue;

            // Cross product AB × AC
            double cx = aby * acz - abz * acy;
            double cy = abz * acx - abx * acz;
            double cz = abx * acy - aby * acx;
            double crossNorm = Math.sqrt(cx * cx + cy * cy + cz * cz);

            double sinAngle = crossNorm / (abLen * acLen);
            if (sinAngle > 0.01) {
                return true; // Kinematically sufficient non-collinear support face
            }
        }

        return false;
    }

    private static Set<Integer> extractNodesFromPhysical(List<String> lines, String setName) {
        Set<Integer> nodes = new TreeSet<>();
        if (setName == null || setName.trim().isEmpty()) return nodes;
        String target = setName.trim().toUpperCase(Locale.US);

        Map<Integer, List<Integer>> elementToNodes = new HashMap<>();
        Map<String, Set<Integer>> elsets = new HashMap<>();

        boolean inElementBlock = false;
        String currentElset = null;
        boolean inElsetBlock = false;
        String currentNamedElset = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String u = trimmed.toUpperCase(Locale.US);

            if (u.startsWith("*ELEMENT")) {
                inElementBlock = true;
                inElsetBlock = false;
                currentNamedElset = null;
                currentElset = null;
                int idx = u.indexOf("ELSET=");
                if (idx != -1) {
                    currentElset = u.substring(idx + 6).trim().split("[,\\s]")[0];
                }
                continue;
            }

            if (u.startsWith("*ELSET")) {
                inElsetBlock = true;
                inElementBlock = false;
                currentElset = null;
                currentNamedElset = null;
                int idx = u.indexOf("ELSET=");
                if (idx != -1) {
                    currentNamedElset = u.substring(idx + 6).trim().split("[,\\s]")[0];
                }
                continue;
            }

            if (u.startsWith("*")) {
                inElementBlock = false;
                inElsetBlock = false;
                currentElset = null;
                currentNamedElset = null;
                continue;
            }

            if (inElementBlock) {
                String[] parts = trimmed.split(",");
                if (parts.length >= 2) {
                    try {
                        int elemId = Integer.parseInt(parts[0].trim());
                        List<Integer> elemNodes = new ArrayList<>();
                        for (int i = 1; i < parts.length; i++) {
                            String p = parts[i].trim();
                            if (!p.isEmpty()) {
                                elemNodes.add(Integer.parseInt(p));
                            }
                        }
                        elementToNodes.put(elemId, elemNodes);

                        if (currentElset != null) {
                            elsets.computeIfAbsent(currentElset.toUpperCase(Locale.US), k -> new HashSet<>()).add(elemId);
                            if (currentElset.equalsIgnoreCase(target)) {
                                nodes.addAll(elemNodes);
                            }
                        }
                    } catch (NumberFormatException ignore) {}
                }
            } else if (inElsetBlock && currentNamedElset != null) {
                String[] parts = trimmed.split(",");
                for (String part : parts) {
                    String p = part.trim();
                    if (!p.isEmpty()) {
                        try {
                            int elemId = Integer.parseInt(p);
                            elsets.computeIfAbsent(currentNamedElset.toUpperCase(Locale.US), k -> new HashSet<>()).add(elemId);
                        } catch (NumberFormatException ignore) {}
                    }
                }
            }
        }

        // If nodes were not directly in an *ELEMENT block with matching ELSET, check *ELSET blocks
        if (nodes.isEmpty()) {
            for (Map.Entry<String, Set<Integer>> entry : elsets.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(target)) {
                    for (int elemId : entry.getValue()) {
                        List<Integer> nList = elementToNodes.get(elemId);
                        if (nList != null) {
                            nodes.addAll(nList);
                        }
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
