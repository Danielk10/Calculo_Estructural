package com.diamon.civil.engine;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A2: DatParser — Parses CalculiX .dat output files for beam section forces.
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
public class DatParser {

    private static final String TAG = "DatParser";

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

    /** Summary of the most extreme values found in the .dat */
    public static class ParseResult {
        public final List<SectionForces> forces;
        public double maxAbsN  = 0;
        public double maxAbsV2 = 0;
        public double maxAbsV3 = 0;
        public double maxAbsM1 = 0;
        public double maxAbsM2 = 0;
        public double maxAbsM3 = 0;
        public String error = null;

        public ParseResult(List<SectionForces> forces) {
            this.forces = forces;
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

        if (!datFile.exists()) {
            ParseResult r = new ParseResult(results);
            r.error = "File not found: " + datFile.getName();
            return r;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(datFile))) {
            String line;
            boolean inSectionBlock = false;
            boolean skipHeader = false;
            int headerLines = 0;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                // Detect start of a section forces block
                if (trimmed.toLowerCase().contains("section forces") &&
                    trimmed.toLowerCase().contains("moment")) {
                    inSectionBlock = true;
                    skipHeader = true;
                    headerLines = 0;
                    continue;
                }

                // Skip the two header/label lines after the block title
                if (inSectionBlock && skipHeader) {
                    headerLines++;
                    if (headerLines >= 2) skipHeader = false;
                    continue;
                }

                // Blank line or new keyword ends the section block
                if (inSectionBlock) {
                    if (trimmed.isEmpty() || trimmed.startsWith("*") ||
                        (trimmed.startsWith("set") && trimmed.contains("time"))) {
                        inSectionBlock = false;
                        continue;
                    }

                    // Parse data line: elemId  intPt  N  V2  V3  M1  M2  M3
                    String[] tok = trimmed.split("\\s+");
                    if (tok.length >= 8) {
                        try {
                            SectionForces sf = new SectionForces();
                            sf.elementId        = Integer.parseInt(tok[0]);
                            sf.integrationPoint = Integer.parseInt(tok[1]);
                            sf.N  = parseScientific(tok[2]);
                            sf.V2 = parseScientific(tok[3]);
                            sf.V3 = parseScientific(tok[4]);
                            sf.M1 = parseScientific(tok[5]);
                            sf.M2 = parseScientific(tok[6]);
                            sf.M3 = parseScientific(tok[7]);
                            results.add(sf);
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Skipping malformed line: " + trimmed);
                        }
                    }
                }
            }

        } catch (IOException e) {
            ParseResult r = new ParseResult(results);
            r.error = "Read error: " + e.getMessage();
            return r;
        }

        // Compute envelope maxima
        ParseResult result = new ParseResult(results);
        for (SectionForces sf : results) {
            result.maxAbsN  = Math.max(result.maxAbsN,  Math.abs(sf.N));
            result.maxAbsV2 = Math.max(result.maxAbsV2, Math.abs(sf.V2));
            result.maxAbsV3 = Math.max(result.maxAbsV3, Math.abs(sf.V3));
            result.maxAbsM1 = Math.max(result.maxAbsM1, Math.abs(sf.M1));
            result.maxAbsM2 = Math.max(result.maxAbsM2, Math.abs(sf.M2));
            result.maxAbsM3 = Math.max(result.maxAbsM3, Math.abs(sf.M3));
        }

        Log.d(TAG, "Parsed " + results.size() + " section force records from " + datFile.getName());
        return result;
    }

    /**
     * Produces a human-readable summary of the maximum forces.
     */
    public String formatSummary(ParseResult result) {
        if (result.error != null) return "DatParser Error: " + result.error;
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
