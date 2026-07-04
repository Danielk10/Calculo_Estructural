package com.diamon.civil.engine;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A1: MshToInpConverter — Reads a Gmsh MSH2 file and emits a CalculiX .inp skeleton.
 *
 * Sections parsed:
 *   $Nodes ... $EndNodes      → *NODE, NSET=NALL
 *   $Elements ... $EndElements → *ELEMENT, TYPE=C3D4, ELSET=Volume1  (tetrahedra)
 *
 * After conversion, InpEnricher injects material, BCs and step cards.
 */
public class MshToInpConverter {

    private static final String TAG = "MshToInpConverter";

    /** Gmsh element type id for 4-node tetrahedron (C3D4). */
    private static final int MSH_TET4 = 4;
    /** Gmsh element type id for 3-node triangle (TRIA3/S3). */
    private static final int MSH_TRIA3 = 2;

    public static class ConversionResult {
        public final boolean success;
        public final int nodeCount;
        public final int elementCount;
        public final String message;

        public ConversionResult(boolean success, int nodeCount, int elementCount, String message) {
            this.success = success;
            this.nodeCount = nodeCount;
            this.elementCount = elementCount;
            this.message = message;
        }
    }

    /**
     * Converts a Gmsh .msh file to a CalculiX .inp skeleton.
     *
     * @param mshFile  Input Gmsh MSH2 file
     * @param inpFile  Output CalculiX .inp (nodes + elements only — no material/BCs)
     * @return ConversionResult with node/element counts
     */
    public ConversionResult convert(File mshFile, File inpFile) {
        List<String> nodeLines = new ArrayList<>();
        List<String> tet4Lines = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(mshFile))) {
            String line;
            boolean inNodes = false;
            boolean inElements = false;
            int totalNodes = 0;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // ── Nodes ──────────────────────────────────────────────
                if (line.equals("$Nodes")) {
                    inNodes = true;
                    totalNodes = 0;
                    if (reader.ready()) {
                        String countLine = reader.readLine();
                        if (countLine != null) {
                            try { totalNodes = Integer.parseInt(countLine.trim()); } catch (NumberFormatException ignored) {}
                        }
                    }
                    continue;
                }
                if (line.equals("$EndNodes")) { inNodes = false; continue; }

                if (inNodes && !line.isEmpty()) {
                    // Format: nodeId  x  y  z
                    String[] tok = line.split("\\s+");
                    if (tok.length >= 4) {
                        // CalculiX NODE line: id, x, y, z
                        nodeLines.add(tok[0] + ", " + tok[1] + ", " + tok[2] + ", " + tok[3]);
                    }
                }

                // ── Elements ────────────────────────────────────────────
                if (line.equals("$Elements")) { inElements = true; continue; }
                if (line.equals("$EndElements")) { inElements = false; continue; }

                if (inElements && !line.isEmpty()) {
                    // MSH2 format: elemId  elemType  numTags  [tags...]  nodeIds...
                    String[] tok = line.split("\\s+");
                    if (tok.length < 2) continue;
                    try {
                        int elemType = Integer.parseInt(tok[1]);
                        int numTags = tok.length > 2 ? Integer.parseInt(tok[2]) : 0;
                        int firstNodeIdx = 3 + numTags;

                        if (elemType == MSH_TET4 && tok.length >= firstNodeIdx + 4) {
                            // C3D4: 4 nodes
                            String elemId = tok[0];
                            String n1 = tok[firstNodeIdx];
                            String n2 = tok[firstNodeIdx + 1];
                            String n3 = tok[firstNodeIdx + 2];
                            String n4 = tok[firstNodeIdx + 3];
                            tet4Lines.add(elemId + ", " + n1 + ", " + n2 + ", " + n3 + ", " + n4);
                        }
                        // Surface triangles (TRIA3) are skipped — CalculiX handles surfaces via ELSET
                    } catch (NumberFormatException ignored) {}
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "Error reading .msh: " + e.getMessage());
            return new ConversionResult(false, 0, 0, "Read error: " + e.getMessage());
        }

        if (nodeLines.isEmpty()) {
            return new ConversionResult(false, 0, 0,
                    "No nodes found in .msh file. Check Gmsh output.");
        }
        if (tet4Lines.isEmpty()) {
            return new ConversionResult(false, nodeLines.size(), 0,
                    "No C3D4 (TET4) elements found. Gmsh may not have meshed the volume.");
        }

        // Write the .inp skeleton
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(inpFile))) {
            writer.write("** CalculiX Input — generated by MshToInpConverter\n");
            writer.write("** Nodes: " + nodeLines.size() + "  Elements: " + tet4Lines.size() + "\n");

            writer.write("*NODE, NSET=NALL\n");
            for (String node : nodeLines) {
                writer.write(node);
                writer.newLine();
            }

            writer.write("*ELEMENT, TYPE=C3D4, ELSET=Volume1\n");
            for (String elem : tet4Lines) {
                writer.write(elem);
                writer.newLine();
            }

        } catch (IOException e) {
            Log.e(TAG, "Error writing .inp: " + e.getMessage());
            return new ConversionResult(false, nodeLines.size(), tet4Lines.size(),
                    "Write error: " + e.getMessage());
        }

        String msg = "Converted " + nodeLines.size() + " nodes, "
                + tet4Lines.size() + " C3D4 elements.";
        Log.d(TAG, msg);
        return new ConversionResult(true, nodeLines.size(), tet4Lines.size(), msg);
    }
}
