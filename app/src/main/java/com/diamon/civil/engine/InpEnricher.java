package com.diamon.civil.engine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Enriches Gmsh-generated .inp files with material properties and boundary conditions.
 */
public class InpEnricher {

    public void enrich(File gmshInp, File targetInp, String modulus, String poisson, String density, List<FaceCondition> conditions) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(gmshInp))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(targetInp))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }

            // Add Material
            writer.write("*MATERIAL, NAME=MATERIAL1");
            writer.newLine();
            writer.write("*ELASTIC");
            writer.newLine();
            writer.write(modulus + ", " + poisson);
            writer.newLine();
            writer.write("*DENSITY");
            writer.newLine();
            writer.write(density);
            writer.newLine();

            // Assign Material to all elements
            writer.write("*SOLID SECTION, ELSET=Volume1, MATERIAL=MATERIAL1");
            writer.newLine();

            // Step
            writer.write("*STEP");
            writer.newLine();
            writer.write("*STATIC");
            writer.newLine();

            // Boundary Conditions & Loads
            if (conditions == null || conditions.isEmpty()) {
                // Default fallback if no UI selection
                writer.write("*BOUNDARY");
                writer.newLine();
                writer.write("Surface1, 1, 3, 0.0");
                writer.newLine();
                writer.write("*CLOAD");
                writer.newLine();
                writer.write("Surface2, 3, -1000.0");
                writer.newLine();
            } else {
                boolean boundaryStarted = false;
                boolean loadStarted = false;

                for (FaceCondition cond : conditions) {
                    if (cond.type == FaceCondition.Type.FIXED) {
                        if (!boundaryStarted) {
                            writer.write("*BOUNDARY");
                            writer.newLine();
                            boundaryStarted = true;
                        }
                        writer.write("Surface" + cond.surfaceId + ", 1, 3, 0.0");
                        writer.newLine();
                    } else if (cond.type == FaceCondition.Type.PRESSURE) {
                        if (!loadStarted) {
                            writer.write("*CLOAD");
                            writer.newLine();
                            loadStarted = true;
                        }
                        writer.write("Surface" + cond.surfaceId + ", 3, " + cond.value);
                        writer.newLine();
                    }
                }
            }

            // Outputs
            writer.write("*NODE FILE");
            writer.newLine();
            writer.write("U");
            writer.newLine();
            writer.write("*EL FILE");
            writer.newLine();
            writer.write("S, E");
            writer.newLine();
            writer.write("*END STEP");
            writer.newLine();
        }
    }
}
