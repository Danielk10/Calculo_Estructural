package com.diamon.civil.structural.engine;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * StructuralBeamFrdParser — Parses CalculiX .frd output files for beam section forces.
 *
 * Extracts data from blocks starting with "-4  STRESS".
 * Maps stress tensor to beam section forces as per CalculiX documentation:
 * SXX -> Axial Normal Force (N)
 * SYY -> Shear Force 1 (V2)
 * SZZ -> Shear Force 2 (V3)
 * SXY -> Torque (T)
 * SYZ -> Bending Moment 1 (M2)
 * SZX -> Bending Moment 2 (M3)
 */
public class StructuralBeamFrdParser {

    private static final String TAG = "StructuralBeamFrdParser";

    /** Section force data extracted from the FRD stress tensor for one element. */
    public static class SectionForces {
        public int nodeId;
        public double axialNormal;    // SXX — Axial Force (N)
        public double shear1;         // SYY — Shear Force V2
        public double shear2;         // SZZ — Shear Force V3
        public double torque;         // SXY — Torque
        public double bendingMoment1; // SYZ — Bending Moment M2
        public double bendingMoment2; // SZX — Bending Moment M3

        @Override
        public String toString() {
            return String.format(
                    "Node %d: N=%.3f V1=%.3f V2=%.3f T=%.3f M1=%.3f M2=%.3f",
                    nodeId, axialNormal, shear1, shear2, torque, bendingMoment1, bendingMoment2);
        }
    }

    /** Summary of the parsed forces and their maximum absolute values. */
    public static class ParseResult {
        public final List<SectionForces> forces;
        public double maxAbsAxial = 0;
        public double maxAbsShear1 = 0;
        public double maxAbsShear2 = 0;
        public double maxAbsTorque = 0;
        public double maxAbsBending1 = 0;
        public double maxAbsBending2 = 0;
        public String error = null;

        public ParseResult(List<SectionForces> forces) {
            this.forces = forces;
        }
    }

    /**
     * Parses the STRESS blocks from an .frd file.
     *
     * @param frdFile CalculiX output .frd file
     * @return ParseResult containing the SectionForces list and maximum values
     */
    public ParseResult parse(File frdFile) {
        List<SectionForces> results = new ArrayList<>();
        ParseResult r = new ParseResult(results);

        if (!frdFile.exists()) {
            r.error = "File not found: " + frdFile.getName();
            return r;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(frdFile))) {
            String line;
            boolean inStressBlock = false;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                // Check for STRESS block header
                if (trimmed.startsWith("-4") && trimmed.contains("STRESS")) {
                    inStressBlock = true;
                    continue;
                }

                // If another block starts or current block ends, reset flag
                if (trimmed.startsWith("-4") && !trimmed.contains("STRESS")) {
                    inStressBlock = false;
                }
                
                if (trimmed.equals("-3")) {
                    inStressBlock = false;
                }

                // Inside the stress block, parse data lines starting with "-1"
                if (inStressBlock && trimmed.startsWith("-1")) {
                    if (line.length() >= 85) {
                        try {
                            SectionForces sf = new SectionForces();
                            sf.nodeId = Integer.parseInt(line.substring(3, 13).trim());
                            double c1 = parseScientific(line.substring(13, 25)); // SXX
                            double c2 = parseScientific(line.substring(25, 37)); // SYY
                            double c3 = parseScientific(line.substring(37, 49)); // SZZ
                            double c4 = parseScientific(line.substring(49, 61)); // SXY
                            double c5 = parseScientific(line.substring(61, 73)); // SYZ
                            double c6 = parseScientific(line.substring(73, 85)); // SZX

                            // In CalculiX 1D beam elements, depending on element orientation:
                            // If SXX (c1) is dominant normal stress (X-axis beam):
                            // c1 -> Axial N, c2 -> Shear V2, c3 -> Shear V3, c4 -> Torque T, c5 -> Moment M1/M2, c6 -> Moment M2/M3
                            // If SZZ (c3) is dominant normal stress (2D beam section):
                            // c3 -> Axial N, c2 -> Shear V2, c1 -> Shear V3, c4 -> Torque T, c5 -> Moment M1/M2, c6 -> Moment M2/M3
                            if (Math.abs(c1) >= Math.abs(c3)) {
                                sf.axialNormal = c1;
                                sf.shear1 = c2;
                                sf.shear2 = c3;
                            } else {
                                sf.axialNormal = c3;
                                sf.shear1 = c2;
                                sf.shear2 = c1;
                            }
                            sf.torque = c4;
                            sf.bendingMoment1 = c5;
                            sf.bendingMoment2 = c6;

                            r.maxAbsAxial = Math.max(r.maxAbsAxial, Math.abs(sf.axialNormal));
                            r.maxAbsShear1 = Math.max(r.maxAbsShear1, Math.abs(sf.shear1));
                            r.maxAbsShear2 = Math.max(r.maxAbsShear2, Math.abs(sf.shear2));
                            r.maxAbsTorque = Math.max(r.maxAbsTorque, Math.abs(sf.torque));
                            r.maxAbsBending1 = Math.max(r.maxAbsBending1, Math.abs(sf.bendingMoment1));
                            r.maxAbsBending2 = Math.max(r.maxAbsBending2, Math.abs(sf.bendingMoment2));

                            results.add(sf);
                        } catch (NumberFormatException e) {
                            logW("Parsing error in STRESS block: " + line);
                        }
                    }
                }
            }
        } catch (IOException e) {
            r.error = "Error reading file: " + e.getMessage();
        }

        logD("Parsed " + results.size() + " stress records from " + frdFile.getName());
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
        if (result.error != null) return "StructuralBeamFrdParser Error: " + result.error;
        if (result.forces.isEmpty()) return "No stress forces found in .frd file.";

        return "Section Force Envelope from FRD (" + result.forces.size() + " records)\n"
                + "─────────────────────────────────────────\n"
                + String.format("  Max |N|  (Axial):    %12.3f N\n",    result.maxAbsAxial)
                + String.format("  Max |V1| (Shear-1):  %12.3f N\n",    result.maxAbsShear1)
                + String.format("  Max |V2| (Shear-2):  %12.3f N\n",    result.maxAbsShear2)
                + String.format("  Max |M1| (Bending-1):%12.3f N·m\n",  result.maxAbsBending1)
                + String.format("  Max |M2| (Bending-2):%12.3f N·m\n",  result.maxAbsBending2)
                + String.format("  Max |T|  (Torque):   %12.3f N·m\n",  result.maxAbsTorque);
    }

    /** Handles CalculiX scientific notation e.g. -1.23456E+03 */
    private double parseScientific(String s) {
        return Double.parseDouble(s.replace("E", "e").replace("D", "e"));
    }
}
