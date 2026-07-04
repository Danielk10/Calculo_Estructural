package com.diamon.civil.engine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class InpGenerator {

    public void generateFullInpFile(File destFile, String lengthStr, String sectionStr, String modulusStr, String densityStr,
                                   String loadStr, String distLoadStr, int mode, int supportType) throws Exception {
        
        double L = parseDouble(lengthStr, "Length");
        double E = parseDouble(modulusStr, "Modulus") * 1e6; // MPa to Pa
        double rho = parseDouble(densityStr, "Density");
        double F = parseDouble(loadStr, "Point Load") * 1000.0; // kN to N
        double q = parseDouble(distLoadStr, "Distributed Load") * 1000.0; // kN/m to N/m
        
        double w = 0.3, h = 0.5;
        try {
            String sanitizedSection = sectionStr.toLowerCase().replace(" ", "");
            String[] parts = sanitizedSection.split("x");
            if (parts.length == 2) {
                w = Double.parseDouble(parts[0]) / 1000.0; // mm to m
                h = Double.parseDouble(parts[1]) / 1000.0; // mm to m
            } else {
                throw new Exception();
            }
        } catch (Exception e) {
            throw new Exception("Invalid section format. Use 'Width x Height' (e.g., 300x500)");
        }

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(destFile), StandardCharsets.UTF_8)) {
            writer.write("*NODE, NSET=NALL\n");
            writer.write("1, 0, 0, 0\n");
            
            int numElements = 20; 
            for (int i = 1; i <= numElements; i++) {
                writer.write((i + 1) + ", " + (L * i / numElements) + ", 0, 0\n");
            }
            
            writer.write("*ELEMENT, TYPE=B31, ELSET=EBEAM\n");
            for (int i = 1; i <= numElements; i++) {
                writer.write(i + ", " + i + ", " + (i + 1) + "\n");
            }
            
            writer.write("*MATERIAL, NAME=STRUCT_MAT\n");
            writer.write("*ELASTIC\n");
            writer.write(E + ", 0.3\n");
            writer.write("*DENSITY\n");
            writer.write(rho + "\n");
            
            writer.write("*BEAM SECTION, ELSET=EBEAM, MATERIAL=STRUCT_MAT, SECTION=RECT\n");
            writer.write(w + ", " + h + "\n");
            writer.write("0, 1, 0\n");
            
            writer.write("*BOUNDARY\n");
            if (supportType == 0) { // Fixed
                writer.write("1, 1, 6, 0\n");
            } else if (supportType == 1) { // Pinned
                writer.write("1, 1, 3, 0\n"); 
            } else { // Fixed-Fixed
                writer.write("1, 1, 6, 0\n");
                writer.write((numElements + 1) + ", 1, 6, 0\n");
            }
            
            writer.write("*STEP\n");
            if (mode == 0) { // Static
                writer.write("*STATIC\n");
                if (F != 0) {
                    writer.write("*CLOAD\n");
                    writer.write((numElements + 1) + ", 2, " + (-F) + "\n");
                }
                if (q != 0) {
                    writer.write("*DLOAD\n");
                    writer.write("EBEAM,P2," + (-q) + "\n");
                }
                writer.write("*NODE PRINT, NSET=NALL\n");
                writer.write("U\n");
                writer.write("*EL PRINT, ELSET=EBEAM\n");
                writer.write("S\n");
                // A2: Emit section forces to .dat for DatParser (N, V, M diagrams)
                writer.write("*SECTION PRINT, NAME=SP1, ELSET=EBEAM\n");
                writer.write("SOF\n");
            } else { // Frequency
                writer.write("*FREQUENCY\n");
                writer.write("10\n");
                writer.write("*NODE PRINT, NSET=NALL\n");
                writer.write("U\n");
            }
            writer.write("*END STEP\n");
            writer.flush();
        }
    }

    private double parseDouble(String value, String fieldName) throws Exception {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new Exception("Invalid numeric value for " + fieldName + ": " + value);
        }
    }
}
