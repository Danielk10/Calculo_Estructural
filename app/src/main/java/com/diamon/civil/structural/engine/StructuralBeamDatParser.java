package com.diamon.civil.structural.engine;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * StructuralBeamDatParser — Parses CalculiX .dat output files for beam section forces and displacements.
 *
 * CalculiX writes section forces when the .inp includes:
 *   *SECTION PRINT, ELSET=BEAMS
 *
 * The .dat contains blocks like:
 *   section forces and moments for set BEAMS and time  0.1000000E+01
 *   element  integration  normal  shear  shear  bending  bending  torque
 *            pt            force  force  force  moment   moment
 *                          (1)    (2)    (3)    (1)      (2)
 *       1       1    -1.23E+03  4.56E+02  ...
 *
 * This parser extracts: elementId, N (axial), V2, V3 (shear), M1, M2 (bending), M3 (torque).
 */
public class StructuralBeamDatParser {

    private static final String TAG = "StructuralBeamDatParser";

    /** Section force data for one element integration point. */
    public static class SectionForces {
        public int elementId;
        public int integrationPoint;
        public double N;   // Axial force
        public double V2;  // Shear force (local 2)
        public double V3;  // Shear force (local 3)
        public double M1;  // Bending moment (local 1)
        public double M2;  // Bending moment (local 2)
        public double M3;  // Torque

        @Override
        public String toString() {
            return String.format(
                    "Elem %d (pt %d): N=%.3f  V2=%.3f  V3=%.3f  M1=%.3f  M2=%.3f  M3=%.3f",
                    elementId, integrationPoint, N, V2, V3, M1, M2, M3);
        }
    }

    public static class NodeDisplacement {
        public int nodeId;
        public double ux, uy, uz;
    }

    public static class PanelForces {
        public int panelId;
        public String panelType;
        public double Mx;   // kN·m/m
        public double My;   // kN·m/m
        public double Mxy;  // kN·m/m
        public double Vmax; // kN/m
        public double tauXY; // MPa
        public double sigmaX; // MPa
        public double sigmaY; // MPa
        public double sigmaVM; // MPa
        public double Vshear_total; // kN
    }

    /** Summary of the most extreme values found in the .dat */
    public static class ParseResult {
        public List<SectionForces> forces;
        public final List<NodeDisplacement> displacements = new ArrayList<>();
        public final List<PanelForces> panelForces = new ArrayList<>();
        public double maxAbsN  = 0;
        public double maxAbsV2 = 0;
        public double maxAbsV3 = 0;
        public double maxAbsM1 = 0;
        public double maxAbsM2 = 0;
        public double maxAbsM3 = 0;
        public double maxDisp = 0;
        public String error = null;

        public ParseResult(List<SectionForces> forces) {
            this.forces = forces;
        }

        public void recalculateMaxForces() {
            maxAbsN = 0;
            maxAbsV2 = 0;
            maxAbsV3 = 0;
            maxAbsM1 = 0;
            maxAbsM2 = 0;
            maxAbsM3 = 0;
            if (forces != null) {
                for (SectionForces sf : forces) {
                    maxAbsN = Math.max(maxAbsN, Math.abs(sf.N));
                    maxAbsV2 = Math.max(maxAbsV2, Math.abs(sf.V2));
                    maxAbsV3 = Math.max(maxAbsV3, Math.abs(sf.V3));
                    maxAbsM1 = Math.max(maxAbsM1, Math.abs(sf.M1));
                    maxAbsM2 = Math.max(maxAbsM2, Math.abs(sf.M2));
                    maxAbsM3 = Math.max(maxAbsM3, Math.abs(sf.M3));
                }
            }
        }
    }

    /**
     * Parses all section force blocks from a .dat file.
     *
     * @param datFile CalculiX output .dat file
     * @return ParseResult with all SectionForces and envelope maxima
     */
    public ParseResult parse(File datFile) {
        List<SectionForces> results = new ArrayList<>();
        ParseResult r = new ParseResult(results);

        if (!datFile.exists()) {
            r.error = "File not found: " + datFile.getName();
            return r;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(datFile))) {
            String line;
            boolean inSectionBlock = false;
            boolean inDispBlock = false;
            boolean dataStarted = false; // Track if we've seen actual data in current block

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                // Detect block headers — any new header terminates the previous block
                String lower = trimmed.toLowerCase();
                if (lower.contains("section forces") && lower.contains("moment")) {
                    inSectionBlock = true;
                    inDispBlock = false;
                    dataStarted = false;
                    continue;
                }

                if (lower.contains("displacements (vx,vy,vz)")) {
                    inDispBlock = true;
                    inSectionBlock = false;
                    dataStarted = false;
                    continue;
                }

                // Detect any other block header (stresses, temperatures, etc.)
                // These headers contain "for set" and "and time" patterns
                if (lower.contains("for set") && lower.contains("and time")) {
                    inDispBlock = false;
                    inSectionBlock = false;
                    dataStarted = false;
                    continue;
                }

                // Empty lines terminate the current data block only after data was found
                if (trimmed.isEmpty()) {
                    if (dataStarted) {
                        inDispBlock = false;
                        inSectionBlock = false;
                        dataStarted = false;
                    }
                    continue;
                }

                if (inSectionBlock) {
                    String[] parts = trimmed.split("\\s+");
                    if (parts.length >= 8 && parts[0].matches("-?\\d+")) {
                        try {
                            SectionForces sf = new SectionForces();
                            sf.elementId = Integer.parseInt(parts[0]);

                            sf.integrationPoint = Integer.parseInt(parts[1]);
                            sf.N = parseScientific(parts[2]);
                            sf.V2 = parseScientific(parts[3]);
                            sf.V3 = parseScientific(parts[4]);
                            sf.M1 = parseScientific(parts[5]);
                            sf.M2 = parseScientific(parts[6]);
                            sf.M3 = parseScientific(parts[7]);

                            r.maxAbsN = Math.max(r.maxAbsN, Math.abs(sf.N));
                            r.maxAbsV2 = Math.max(r.maxAbsV2, Math.abs(sf.V2));
                            r.maxAbsV3 = Math.max(r.maxAbsV3, Math.abs(sf.V3));
                            r.maxAbsM1 = Math.max(r.maxAbsM1, Math.abs(sf.M1));
                            r.maxAbsM2 = Math.max(r.maxAbsM2, Math.abs(sf.M2));
                            r.maxAbsM3 = Math.max(r.maxAbsM3, Math.abs(sf.M3));

                            results.add(sf);
                            dataStarted = true;
                        } catch (NumberFormatException e) {
                            logW("Parsing error section block: " + line);
                        }
                    }
                } else if (inDispBlock) {
                    String[] parts = trimmed.split("\\s+");
                    // Displacement lines have exactly 4 fields: nodeId, ux, uy, uz
                    // Stress lines have 8 fields — reject those to prevent misinterpretation
                    if (parts.length >= 4 && parts.length <= 5) {
                        try {
                            NodeDisplacement nd = new NodeDisplacement();
                            nd.nodeId = Integer.parseInt(parts[0]);
                            nd.ux = parseScientific(parts[1]);
                            nd.uy = parseScientific(parts[2]);
                            nd.uz = parseScientific(parts[3]);
                            
                            double mag = Math.sqrt(nd.ux*nd.ux + nd.uy*nd.uy + nd.uz*nd.uz);
                            r.maxDisp = Math.max(r.maxDisp, mag);
                            r.displacements.add(nd);
                            dataStarted = true;
                        } catch (NumberFormatException e) {
                            logW("Parsing error disp block: " + line);
                        }
                    }
                }
            }
        } catch (IOException e) {
            r.error = "Error reading file: " + e.getMessage();
        }

        logD("Parsed " + results.size() + " section force records from " + datFile.getName());
        return r;
    }

    private static void logD(String msg) {
        try {
            android.util.Log.d(TAG, msg);
        } catch (Throwable ignore) {
            System.out.println(TAG + ": " + msg);
        }
    }

    private static void logW(String msg) {
        try {
            android.util.Log.w(TAG, msg);
        } catch (Throwable ignore) {
            System.err.println(TAG + " WARN: " + msg);
        }
    }

    /**
     * Produces a human-readable summary of the maximum forces.
     */
    public String formatSummary(ParseResult result) {
        if (result.error != null) return "StructuralBeamDatParser Error: " + result.error;
        if (result.forces.isEmpty()) return "No section forces found in .dat file.";

        return "Section Force Envelope (" + result.forces.size() + " records)\n"
                + "─────────────────────────────────────────\n"
                + String.format("  Max |N|  (Axial):    %12.3f N\n",    result.maxAbsN)
                + String.format("  Max |V2| (Shear-2):  %12.3f N\n",    result.maxAbsV2)
                + String.format("  Max |V3| (Shear-3):  %12.3f N\n",    result.maxAbsV3)
                + String.format("  Max |M1| (Bending-1):%12.3f N·m\n",  result.maxAbsM1)
                + String.format("  Max |M2| (Bending-2):%12.3f N·m\n",  result.maxAbsM2)
                + String.format("  Max |M3| (Torque):   %12.3f N·m\n",  result.maxAbsM3);
    }

    /** Handles CalculiX scientific notation e.g. -1.23456E+03 */
    private double parseScientific(String s) {
        return Double.parseDouble(s.replace("E", "e").replace("D", "e"));
    }
}
