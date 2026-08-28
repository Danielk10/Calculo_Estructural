package com.diamon.civil.structural.io;

import com.diamon.civil.structural.engine.StructuralModel;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * D1: AbaqusInpImporter — Parses basic .inp files for model reconstruction.
 */
public class AbaqusInpImporter {

    public StructuralModel importInp(File file) throws Exception {
        StructuralModel model = new StructuralModel();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            String currentSection = "";
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim().toUpperCase();
                if (trimmed.startsWith("*NODE")) {
                    currentSection = "NODE";
                    continue;
                } else if (trimmed.startsWith("*ELEMENT")) {
                    currentSection = "ELEMENT";
                    continue;
                } else if (trimmed.startsWith("*")) {
                    currentSection = "";
                    continue;
                }

                if (trimmed.isEmpty() || trimmed.startsWith("**")) continue;

                String[] tok = line.split(",");
                try {
                    if (currentSection.equals("NODE") && tok.length >= 3) {
                        int id = Integer.parseInt(tok[0].trim());
                        double x = Double.parseDouble(tok[1].trim());
                        double y = Double.parseDouble(tok[2].trim());
                        double z = (tok.length > 3) ? Double.parseDouble(tok[3].trim()) : 0;
                        model.nodes.add(new StructuralModel.Node(id, x, y, z));
                    } else if (currentSection.equals("ELEMENT") && tok.length >= 3) {
                        int id = Integer.parseInt(tok[0].trim());
                        int n1 = Integer.parseInt(tok[1].trim());
                        int n2 = Integer.parseInt(tok[2].trim());
                        model.elements.add(new StructuralModel.Element(id, n1, n2, "W8x31", "Steel"));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return model;
    }
}
