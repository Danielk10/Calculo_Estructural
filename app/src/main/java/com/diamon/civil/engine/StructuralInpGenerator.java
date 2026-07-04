package com.diamon.civil.engine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * B3: StructuralInpGenerator — Translates the structural model to CalculiX .inp format using B32 elements.
 */
public class StructuralInpGenerator {

    public void generate(File destFile, StructuralModel model, SectionLibrary sections, MaterialDatabase materials) throws Exception {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(destFile), StandardCharsets.UTF_8)) {
            
            // 1. Nodes
            writer.write("*NODE, NSET=NALL\n");
            Map<Integer, StructuralModel.Node> nodeMap = new HashMap<>();
            for (StructuralModel.Node n : model.nodes) {
                nodeMap.put(n.id, n);
                writer.write(n.id + ", " + n.x + ", " + n.y + ", " + n.z + "\n");
            }

            // Generate mid-nodes for B32
            int midNodeIdStart = 100000; // Arbitrary start for mid-nodes
            int elementCount = 0;
            
            // 2. Elements (B32)
            writer.write("*ELEMENT, TYPE=B32, ELSET=BEAMS\n");
            for (StructuralModel.Element e : model.elements) {
                elementCount++;
                StructuralModel.Node n1 = nodeMap.get(e.node1Id);
                StructuralModel.Node n2 = nodeMap.get(e.node2Id);
                
                int midNodeId = midNodeIdStart + elementCount;
                double mx = (n1.x + n2.x) / 2.0;
                double my = (n1.y + n2.y) / 2.0;
                double mz = (n1.z + n2.z) / 2.0;
                
                // We'll write mid-nodes later or now? Better write them in a separate block?
                // Actually, *NODE can be called multiple times? No, usually one block.
                // Let's re-collect all nodes first.
            }
            
            // Re-generation with mid-nodes
            writer.close();
        }
        
        // Let's restart with a better approach.
        doGenerate(destFile, model, sections, materials);
    }

    private void doGenerate(File destFile, StructuralModel model, SectionLibrary sections, MaterialDatabase materials) throws Exception {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(destFile), StandardCharsets.UTF_8)) {
            Map<Integer, StructuralModel.Node> nodeMap = new HashMap<>();
            for (StructuralModel.Node n : model.nodes) nodeMap.put(n.id, n);

            writer.write("*NODE, NSET=NALL\n");
            for (StructuralModel.Node n : model.nodes) {
                writer.write(n.id + ", " + n.x + ", " + n.y + ", " + n.z + "\n");
            }

            int midNodeId = 100000;
            for (StructuralModel.Element e : model.elements) {
                midNodeId++;
                StructuralModel.Node n1 = nodeMap.get(e.node1Id);
                StructuralModel.Node n2 = nodeMap.get(e.node2Id);
                double mx = (n1.x + n2.x) / 2.0;
                double my = (n1.y + n2.y) / 2.0;
                double mz = (n1.z + n2.z) / 2.0;
                writer.write(midNodeId + ", " + mx + ", " + my + ", " + mz + "\n");
            }

            writer.write("*ELEMENT, TYPE=B32, ELSET=BEAMS\n");
            midNodeId = 100000;
            for (StructuralModel.Element e : model.elements) {
                midNodeId++;
                writer.write(e.id + ", " + e.node1Id + ", " + e.node2Id + ", " + midNodeId + "\n");
            }

            // Sections and Materials
            // For now, assume one global material and section for all elements
            // In a real implementation, we'd group elements by section/material
            
            if (!model.elements.isEmpty()) {
                StructuralModel.Element first = model.elements.get(0);
                SectionLibrary.Section s = sections.getSectionByName(first.sectionName);
                MaterialDatabase.Material m = materials.getMaterialByName(first.materialName);

                if (m != null) {
                    writer.write("*MATERIAL, NAME=" + m.name.replace(" ", "_") + "\n");
                    writer.write("*ELASTIC\n");
                    writer.write((m.E * 1e6) + ", " + m.nu + "\n");
                }

                if (s != null) {
                    writer.write("*BEAM SECTION, ELSET=BEAMS, MATERIAL=" + (m != null ? m.name.replace(" ", "_") : "MAT1") + ", SECTION=GENERAL\n");
                    // Area, Izz, Iyz, Iyy, J, Gamma, Gamma
                    writer.write((s.A * 1e-6) + ", " + (s.Iz * 1e-12) + ", 0, " + (s.Iy * 1e-12) + ", " + (s.J * 1e-12) + "\n");
                    writer.write("0, 1, 0\n"); // Orientation vector
                }
            }

            writer.write("*STEP\n");
            writer.write("*STATIC\n");
            // Add BCs and Loads (Mock for now)
            writer.write("*BOUNDARY\n");
            if (!model.nodes.isEmpty()) {
                writer.write(model.nodes.get(0).id + ", 1, 6, 0\n");
            }
            writer.write("*NODE PRINT, NSET=NALL\n");
            writer.write("U\n");
            writer.write("*SECTION PRINT, NAME=SP1, ELSET=BEAMS\n");
            writer.write("SOF\n");
            writer.write("*END STEP\n");
        }
    }
}
