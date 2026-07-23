package com.diamon.civil.engine;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * FrdParser — Parses CalculiX .frd output files for beam section forces.
 *
 * Extracts data from blocks starting with "-4  STRESS".
 * Maps stress tensor to beam forces as follows:
 * SXX -> Shear 1
 * SYY -> Shear 2
 * SZZ -> Axial Normal
 * SXY -> Torque
 * SYZ -> Bending Moment 1
 * SZX -> Bending Moment 2
 */
public class FrdParser {

    private static final String TAG = "FrdParser";

    /** Section force data extracted from the FRD stress tensor for one element. */
    public static class SectionForces {
        public int elementId;
        public double shear1;         // SXX
        public double shear2;         // SYY
        public double axialNormal;    // SZZ
        public double torque;         // SXY
        public double bendingMoment1; // SYZ
        public double bendingMoment2; // SZX

        @Override
        public String toString() {
            return String.format(
                    "Elem %d: N=%.3f V1=%.3f V2=%.3f T=%.3f M1=%.3f M2=%.3f",
                    elementId, axialNormal, shear1, shear2, torque, bendingMoment1, bendingMoment2);
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
                    String[] parts = trimmed.split("\\s+");
                    // parts[0] is "-1", parts[1] is id, parts[2..7] are 6 values
                    if (parts.length >= 8) {
                        try {
                            SectionForces sf = new SectionForces();
                            sf.elementId = Integer.parseInt(parts[1]);
                            sf.shear1 = parseScientific(parts[2]);         // SXX
                            sf.shear2 = parseScientific(parts[3]);         // SYY
                            sf.axialNormal = parseScientific(parts[4]);    // SZZ
                            sf.torque = parseScientific(parts[5]);         // SXY
                            sf.bendingMoment1 = parseScientific(parts[6]); // SYZ
                            sf.bendingMoment2 = parseScientific(parts[7]); // SZX

                            r.maxAbsAxial = Math.max(r.maxAbsAxial, Math.abs(sf.axialNormal));
                            r.maxAbsShear1 = Math.max(r.maxAbsShear1, Math.abs(sf.shear1));
                            r.maxAbsShear2 = Math.max(r.maxAbsShear2, Math.abs(sf.shear2));
                            r.maxAbsTorque = Math.max(r.maxAbsTorque, Math.abs(sf.torque));
                            r.maxAbsBending1 = Math.max(r.maxAbsBending1, Math.abs(sf.bendingMoment1));
                            r.maxAbsBending2 = Math.max(r.maxAbsBending2, Math.abs(sf.bendingMoment2));

                            results.add(sf);
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Parsing error in STRESS block: " + line);
                        }
                    }
                }
            }
        } catch (IOException e) {
            r.error = "Error reading file: " + e.getMessage();
        }

        Log.d(TAG, "Parsed " + results.size() + " stress records from " + frdFile.getName());
        return r;
    }

    /**
     * Produces a human-readable summary of the maximum forces.
     */
    public String formatSummary(ParseResult result) {
        if (result.error != null) return "FrdParser Error: " + result.error;
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
