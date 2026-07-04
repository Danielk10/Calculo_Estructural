package com.diamon.civil.engine;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

public class InpEnricherTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testEnrich() throws IOException {
        // Create mock Gmsh .inp file
        File gmshInp = tempFolder.newFile("gmsh_mock.inp");
        try (FileWriter writer = new FileWriter(gmshInp)) {
            writer.write("*NODE\n");
            writer.write("1, 0.0, 0.0, 0.0\n");
            writer.write("*ELEMENT, TYPE=C3D4\n");
            writer.write("1, 1, 2, 3, 4\n");
        }

        File targetInp = tempFolder.newFile("enriched.inp");
        InpEnricher enricher = new InpEnricher();
        enricher.enrich(gmshInp, targetInp, "200000", "0.3", "7800", "-500.0");

        // Verify target file exists and contains enriched blocks
        assertTrue(targetInp.exists());
        List<String> lines = Files.readAllLines(targetInp.toPath());

        boolean hasNode = false;
        boolean hasMaterial = false;
        boolean hasElastic = false;
        boolean hasSolidSection = false;
        boolean hasCload = false;

        for (String line : lines) {
            if (line.contains("*NODE")) hasNode = true;
            if (line.contains("*MATERIAL, NAME=MATERIAL1")) hasMaterial = true;
            if (line.contains("*ELASTIC")) hasElastic = true;
            if (line.contains("*SOLID SECTION, ELSET=Volume1, MATERIAL=MATERIAL1")) hasSolidSection = true;
            if (line.contains("Surface2, 3, -500.0")) hasCload = true;
        }

        assertTrue(hasNode);
        assertTrue(hasMaterial);
        assertTrue(hasElastic);
        assertTrue(hasSolidSection);
        assertTrue(hasCload);
    }
}
