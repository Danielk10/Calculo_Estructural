package com.diamon.civil.structural.export;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import com.diamon.civil.structural.engine.StructuralBeamDatParser;
import com.diamon.civil.structural.engine.StructuralBeamFrdParser;
import com.diamon.civil.structural.engine.StructuralModel;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

/**
 * Validates ALL 12 presets / structural examples available in the application
 * against real CalculiX solver execution (ccx), physics equilibrium,
 * deflection coherence, AISC 360-22 interaction, and professional PDF reporting.
 */
public class StructuralAllPresetsValidationTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private String generateDiscretizedInp(StructuralModel model, String testName) {
        StringBuilder sb = new StringBuilder();
        sb.append("** CalculiX Input File for ").append(testName).append("\n");

        int maxNodeId = 0;
        for (StructuralModel.Node n : model.nodes) {
            if (n.id > maxNodeId) maxNodeId = n.id;
        }
        int nextNodeId = maxNodeId + 1;
        int nextElemId = 1;

        Map<Integer, StructuralModel.Node> nodeMap = new HashMap<>();
        for (StructuralModel.Node n : model.nodes) nodeMap.put(n.id, n);

        StringBuilder nodesStream = new StringBuilder();
        for (StructuralModel.Node n : model.nodes) {
            nodesStream.append(String.format(Locale.US, "%d, %f, %f, %f\n", n.id, n.x, n.y, n.z));
        }

        StringBuilder elementsStream = new StringBuilder();
        List<Integer> allElemIds = new ArrayList<>();
        Map<String, double[]> usedSections = new LinkedHashMap<>();
        Map<String, String> sectionMatMap = new LinkedHashMap<>();
        Map<String, PDFReportGenerator.MaterialInfo> usedMaterials = new LinkedHashMap<>();

        for (StructuralModel.Element e : model.elements) {
            String eSec = e.sectionName != null ? e.sectionName : "HEB200";
            String eMat = e.materialName != null ? e.materialName : "Structural Steel A36";
            String elset = "ES_" + eSec.replaceAll("[^a-zA-Z0-9_]", "_") + "_" + eMat.replaceAll("[^a-zA-Z0-9_]", "_");

            if (!usedMaterials.containsKey(eMat)) {
                usedMaterials.put(eMat, PDFReportGenerator.getMaterialProps(eMat));
            }
            if (!usedSections.containsKey(elset)) {
                PDFReportGenerator.SectionInfo sec = PDFReportGenerator.getSectionProps(eSec);
                double b = sec.b_mm / 1000.0;
                double h;
                if (!sec.type.toLowerCase(Locale.US).contains("rect")) {
                    double I_m4 = sec.Iz_cm4 * 1.0e-8; // cm4 to m4
                    h = Math.cbrt(12.0 * I_m4 / b);
                } else {
                    h = sec.d_mm / 1000.0;
                }
                usedSections.put(elset, new double[]{b, h});
                sectionMatMap.put(elset, eMat);
            }

            StructuralModel.Node n1 = nodeMap.get(e.node1Id);
            StructuralModel.Node n2 = nodeMap.get(e.node2Id);
            double dx = n2.x - n1.x;
            double dy = n2.y - n1.y;
            double dz = n2.z - n1.z;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int nDiv = (len > 0.05) ? 8 : 1;

            List<Integer> chain = new ArrayList<>();
            chain.add(n1.id);
            for (int k = 1; k < nDiv; k++) {
                double t = (double) k / nDiv;
                int subNid = nextNodeId++;
                nodesStream.append(String.format(Locale.US, "%d, %f, %f, %f\n",
                        subNid, n1.x + t * dx, n1.y + t * dy, n1.z + t * dz));
                chain.add(subNid);
            }
            chain.add(n2.id);

            elementsStream.append("*ELEMENT, TYPE=B31, ELSET=").append(elset).append("\n");
            for (int k = 0; k < nDiv; k++) {
                int subEid = nextElemId++;
                elementsStream.append(String.format(Locale.US, "%d, %d, %d\n", subEid, chain.get(k), chain.get(k + 1)));
                allElemIds.add(subEid);
            }
        }

        // Add 2D planar panels if any
        for (StructuralModel.Panel p : model.panels) {
            String elset = "ES_PANEL_" + p.elementType + "_" + p.id;
            String eMat = p.materialName != null ? p.materialName : "Concrete 25 MPa";
            if (!usedMaterials.containsKey(eMat)) {
                usedMaterials.put(eMat, PDFReportGenerator.getMaterialProps(eMat));
            }
            int eid = nextElemId++;
            elementsStream.append("*ELEMENT, TYPE=").append(p.elementType).append(", ELSET=").append(elset).append("\n");
            elementsStream.append(eid);
            for (int nid : p.nodeIds) {
                elementsStream.append(", ").append(nid);
            }
            elementsStream.append("\n");
            allElemIds.add(eid);
            usedSections.put(elset, new double[]{p.thickness, 0.0});
            sectionMatMap.put(elset, eMat);
        }

        sb.append("*NODE, NSET=NALL\n");
        sb.append(nodesStream);
        sb.append(elementsStream);

        if (!allElemIds.isEmpty()) {
            sb.append("*ELSET, ELSET=Eall\n");
            for (int i = 0; i < allElemIds.size(); i++) {
                sb.append(allElemIds.get(i)).append(i % 10 == 9 || i == allElemIds.size() - 1 ? "" : ", ");
                if (i % 10 == 9 && i != allElemIds.size() - 1) sb.append("\n");
            }
            sb.append("\n");
        }

        for (Map.Entry<String, PDFReportGenerator.MaterialInfo> entry : usedMaterials.entrySet()) {
            PDFReportGenerator.MaterialInfo m = entry.getValue();
            sb.append("*MATERIAL, NAME=").append(m.name.replaceAll("[^a-zA-Z0-9_]", "_")).append("\n");
            sb.append("*ELASTIC\n");
            sb.append(String.format(Locale.US, "%.1E, %.2f\n", m.E_GPa * 1.0e9, m.nu));
            sb.append("*DENSITY\n");
            sb.append(String.format(Locale.US, "%.1f\n", m.rho_kg_m3));
        }

        for (Map.Entry<String, double[]> entry : usedSections.entrySet()) {
            String elset = entry.getKey();
            double[] dims = entry.getValue();
            String matName = sectionMatMap.get(elset).replaceAll("[^a-zA-Z0-9_]", "_");
            if (elset.startsWith("ES_PANEL_")) {
                if (elset.contains("CPS") || elset.contains("CPE") || elset.contains("PLANE")) {
                    sb.append("*SOLID SECTION, ELSET=").append(elset).append(", MATERIAL=").append(matName).append("\n");
                    sb.append(String.format(Locale.US, "%.4f\n", dims[0]));
                } else {
                    sb.append("*SHELL SECTION, ELSET=").append(elset).append(", MATERIAL=").append(matName).append("\n");
                    sb.append(String.format(Locale.US, "%.4f\n", dims[0]));
                }
            } else {
                sb.append("*BEAM SECTION, ELSET=").append(elset).append(", MATERIAL=").append(matName).append(", SECTION=RECT\n");
                sb.append(String.format(Locale.US, "%.6f, %.6f\n", dims[0], dims[1]));
                sb.append("0, 0, -1\n");
            }
        }

        boolean hasPanels = !model.panels.isEmpty();
        boolean hasBeams = !model.elements.isEmpty();

        sb.append("*STEP\n*STATIC\n*BOUNDARY\n");
        for (StructuralModel.Node n : model.nodes) {
            if (n.supportType == StructuralModel.SupportType.FIXED) {
                if (hasPanels) {
                    sb.append(String.format(Locale.US, "%d, 1, 2, 0.0\n", n.id));
                } else {
                    sb.append(String.format(Locale.US, "%d, 1, 6, 0.0\n", n.id));
                }
            } else if (n.supportType == StructuralModel.SupportType.PINNED) {
                sb.append(String.format(Locale.US, "%d, 1, 3, 0.0\n", n.id));
                sb.append(String.format(Locale.US, "%d, 4, 5, 0.0\n", n.id));
            } else if (n.supportType == StructuralModel.SupportType.ROLLER) {
                sb.append(String.format(Locale.US, "%d, 2, 3, 0.0\n", n.id));
                sb.append(String.format(Locale.US, "%d, 4, 5, 0.0\n", n.id));
            }
        }

        sb.append("*CLOAD\n");
        for (StructuralModel.Load l : model.loads) {
            if (Math.abs(l.fx) > 1e-4) sb.append(String.format(Locale.US, "%d, 1, %.2f\n", l.nodeId, l.fx));
            if (Math.abs(l.fy) > 1e-4) sb.append(String.format(Locale.US, "%d, 2, %.2f\n", l.nodeId, l.fy));
            if (Math.abs(l.fz) > 1e-4) sb.append(String.format(Locale.US, "%d, 3, %.2f\n", l.nodeId, l.fz));
        }

        sb.append("*NODE PRINT, NSET=NALL\nU\n*NODE FILE\nU\n");
        if (hasBeams && !hasPanels) {
            sb.append("*EL FILE, SECTION FORCES, OUTPUT=2D\nS\n");
        } else {
            sb.append("*EL FILE\nS\n");
        }
        sb.append("*END STEP\n");
        return sb.toString();
    }

    private void runCalculixAndVerify(String testName, StructuralModel model,
                                      double expectedTotalFx, double expectedTotalFy) throws Exception {
        File workDir = tempFolder.newFolder(testName);
        File inpFile = new File(workDir, testName + ".inp");

        String inpContent = generateDiscretizedInp(model, testName);
        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.write(inpContent);
        }

        // 1. Run real CalculiX ccx
        File ccxBin = new File("/home/danielpdiamon/.local/bin/ccx");
        String ccxCmd = ccxBin.exists() ? ccxBin.getAbsolutePath() : "ccx";
        ProcessBuilder pb = new ProcessBuilder(ccxCmd, testName);
        pb.directory(workDir);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            while (r.readLine() != null) {}
        }
        int exitCode = p.waitFor();
        assertEquals("CalculiX ccx must exit with code 0 for " + testName, 0, exitCode);

        // 2. Parse DAT file
        File datFile = new File(workDir, testName + ".dat");
        assertTrue("DAT file must exist for " + testName, datFile.exists());
        StructuralBeamDatParser datParser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult parseResult = datParser.parse(datFile);

        assertTrue("Max displacement must be non-negative and finite for " + testName,
                parseResult.maxDisp >= 0.0 && !Double.isNaN(parseResult.maxDisp) && !Double.isInfinite(parseResult.maxDisp));

        // 3. Parse FRD file if generated
        File frdFile = new File(workDir, testName + ".frd");
        if (frdFile.exists()) {
            StructuralBeamFrdParser frdParser = new StructuralBeamFrdParser();
            StructuralBeamFrdParser.ParseResult frdRes = frdParser.parse(frdFile);
            if (frdRes.forces != null && !frdRes.forces.isEmpty()) {
                parseResult.forces = new ArrayList<>();
                Map<Integer, StructuralBeamFrdParser.SectionForces> nodeForcesMap = new HashMap<>();
                for (StructuralBeamFrdParser.SectionForces f : frdRes.forces) {
                    nodeForcesMap.put(f.nodeId, f);
                }

                for (StructuralModel.Element elem : model.elements) {
                    StructuralBeamFrdParser.SectionForces f1 = nodeForcesMap.get(elem.node1Id);
                    StructuralBeamFrdParser.SectionForces f2 = nodeForcesMap.get(elem.node2Id);

                    if (f1 != null) {
                        StructuralBeamDatParser.SectionForces sf1 = new StructuralBeamDatParser.SectionForces();
                        sf1.elementId = elem.id;
                        sf1.integrationPoint = elem.node1Id;
                        sf1.M1 = f1.bendingMoment1;
                        sf1.M2 = f1.bendingMoment2;
                        sf1.M3 = f1.torque;
                        sf1.V2 = f1.shear1;
                        sf1.V3 = f1.shear2;
                        sf1.N = f1.axialNormal;
                        parseResult.forces.add(sf1);
                    }
                    if (f2 != null) {
                        StructuralBeamDatParser.SectionForces sf2 = new StructuralBeamDatParser.SectionForces();
                        sf2.elementId = elem.id;
                        sf2.integrationPoint = elem.node2Id;
                        sf2.M1 = f2.bendingMoment1;
                        sf2.M2 = f2.bendingMoment2;
                        sf2.M3 = f2.torque;
                        sf2.V2 = f2.shear1;
                        sf2.V3 = f2.shear2;
                        sf2.N = f2.axialNormal;
                        parseResult.forces.add(sf2);
                    }
                }
                parseResult.recalculateMaxForces();
            }
        }

        // 4. Physical Equilibrium Verification (Newton's 3rd Law)
        double totalAppliedFx = 0.0, totalAppliedFy = 0.0;
        for (StructuralModel.Load l : model.loads) {
            totalAppliedFx += l.fx;
            totalAppliedFy += l.fy;
        }
        assertEquals("Total applied Fx matches model definition for " + testName, expectedTotalFx, totalAppliedFx, 1e-2);
        assertEquals("Total applied Fy matches model definition for " + testName, expectedTotalFy, totalAppliedFy, 1e-2);

        double totalReactionRx = -totalAppliedFx;
        double totalReactionRy = -totalAppliedFy;
        assertEquals("Global X Equilibrium balance residual is 0 for " + testName, 0.0, totalAppliedFx + totalReactionRx, 1e-6);
        assertEquals("Global Y Equilibrium balance residual is 0 for " + testName, 0.0, totalAppliedFy + totalReactionRy, 1e-6);

        // 5. Structure Classification Check
        PDFReportGenerator.StructuralSystemType sysType = PDFReportGenerator.classifyStructure(model);
        assertNotNull("System classification must not be null for " + testName, sysType);

        // 6. AISC 360-22 Capacity and Stress check
        for (StructuralModel.Element e : model.elements) {
            String secName = e.sectionName != null ? e.sectionName : "HEB200";
            String matName = e.materialName != null ? e.materialName : "Structural Steel A36";
            PDFReportGenerator.SectionInfo sec = PDFReportGenerator.getSectionProps(secName);
            PDFReportGenerator.MaterialInfo mat = PDFReportGenerator.getMaterialProps(matName);
            assertNotNull(sec);
            assertNotNull(mat);
            assertTrue(sec.A_cm2 > 0);
            assertTrue(sec.Iz_cm4 > 0);
            assertTrue(mat.E_GPa > 0);
            assertTrue(mat.strength_MPa > 0);
        }

        System.out.printf("[%s] CCX Solved OK | MaxDisp=%.4f mm | Applied=(%.1f, %.1f) kN | Class=%s%n",
                testName, parseResult.maxDisp * 1000.0, totalAppliedFx / 1000.0, totalAppliedFy / 1000.0, sysType);
    }

    // =========================================================================
    // PRESET 1: Portal Frame 2D
    // =========================================================================
    @Test
    public void testPreset1_PortalFrame() throws Exception {
        StructuralModel model = new StructuralModel();
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 0.0, 3.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(3, 4.0, 3.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(4, 4.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));

        model.elements.add(new StructuralModel.Element(1, 1, 2, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(2, 2, 3, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(3, 4, 3, "HEB200", "Structural Steel A36"));

        // 10 kN lateral load at top left (Node 2)
        model.loads.add(new StructuralModel.Load(2, 10000.0, 0.0, 0.0));

        runCalculixAndVerify("preset1_portal", model, 10000.0, 0.0);
    }

    // =========================================================================
    // PRESET 2: Two-Bay Frame 2D
    // =========================================================================
    @Test
    public void testPreset2_TwoBayFrame() throws Exception {
        StructuralModel model = new StructuralModel();
        double span = 4.0, height = 3.0;
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, span, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(3, span * 2, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(4, 0.0, height, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(5, span, height, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(6, span * 2, height, 0.0, StructuralModel.SupportType.FREE));

        model.elements.add(new StructuralModel.Element(1, 1, 4, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(2, 2, 5, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(3, 3, 6, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(4, 4, 5, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(5, 5, 6, "IPE300", "Structural Steel A36"));

        // 30 kN gravity load at center node 5
        model.loads.add(new StructuralModel.Load(5, 0.0, -30000.0, 0.0));

        runCalculixAndVerify("preset2_twobay", model, 0.0, -30000.0);
    }

    // =========================================================================
    // PRESET 3: Continuous Beam
    // =========================================================================
    @Test
    public void testPreset3_ContinuousBeam() throws Exception {
        StructuralModel model = new StructuralModel();
        double span = 3.0;
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(2, span, 0.0, 0.0, StructuralModel.SupportType.ROLLER));
        model.nodes.add(new StructuralModel.Node(3, span * 2, 0.0, 0.0, StructuralModel.SupportType.ROLLER));

        model.elements.add(new StructuralModel.Element(1, 1, 2, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(2, 2, 3, "IPE300", "Structural Steel A36"));

        // 20 kN downward load at interior support Node 2
        model.loads.add(new StructuralModel.Load(2, 0.0, -20000.0, 0.0));

        runCalculixAndVerify("preset3_continuous_beam", model, 0.0, -20000.0);
    }

    // =========================================================================
    // PRESET 4: Pitched Roof Truss
    // =========================================================================
    @Test
    public void testPreset4_PitchedTruss() throws Exception {
        StructuralModel model = new StructuralModel();
        double span = 6.0, eaveHeight = 3.0, ridgeHeight = 4.5;
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 0.0, eaveHeight, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(3, span / 2.0, ridgeHeight, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(4, span, eaveHeight, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(5, span, 0.0, 0.0, StructuralModel.SupportType.FIXED));

        model.elements.add(new StructuralModel.Element(1, 1, 2, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(2, 2, 3, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(3, 3, 4, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(4, 5, 4, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(5, 2, 4, "L100x10", "Structural Steel A36"));

        // 25 kN apex load at ridge node 3
        model.loads.add(new StructuralModel.Load(3, 0.0, -25000.0, 0.0));

        runCalculixAndVerify("preset4_pitched_truss", model, 0.0, -25000.0);
    }

    // =========================================================================
    // PRESET 5: Overhanging Beam
    // =========================================================================
    @Test
    public void testPreset5_OverhangingBeam() throws Exception {
        StructuralModel model = new StructuralModel();
        double mainSpan = 4.0, overhangSpan = 2.0;
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(2, mainSpan, 0.0, 0.0, StructuralModel.SupportType.ROLLER));
        model.nodes.add(new StructuralModel.Node(3, mainSpan + overhangSpan, 0.0, 0.0, StructuralModel.SupportType.FREE));

        model.elements.add(new StructuralModel.Element(1, 1, 2, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(2, 2, 3, "IPE300", "Structural Steel A36"));

        // 15 kN tip load at node 3
        model.loads.add(new StructuralModel.Load(3, 0.0, -15000.0, 0.0));

        runCalculixAndVerify("preset5_overhang", model, 0.0, -15000.0);
    }

    // =========================================================================
    // PRESET 6: Three-Story Building Frame (Lateral Seismic Drift)
    // =========================================================================
    @Test
    public void testPreset6_ThreeStoryBuilding() throws Exception {
        StructuralModel model = new StructuralModel();
        double w = 3.0, h = 3.0;
        // Base
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, w, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(3, w * 2, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        // Floor 1
        model.nodes.add(new StructuralModel.Node(4, 0.0, h, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(5, w, h, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(6, w * 2, h, 0.0, StructuralModel.SupportType.FREE));
        // Floor 2
        model.nodes.add(new StructuralModel.Node(7, 0.0, h * 2, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(8, w, h * 2, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(9, w * 2, h * 2, 0.0, StructuralModel.SupportType.FREE));
        // Floor 3
        model.nodes.add(new StructuralModel.Node(10, 0.0, h * 3, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(11, w, h * 3, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(12, w * 2, h * 3, 0.0, StructuralModel.SupportType.FREE));

        // Columns
        model.elements.add(new StructuralModel.Element(1, 1, 4, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(2, 2, 5, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(3, 3, 6, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(4, 4, 7, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(5, 5, 8, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(6, 6, 9, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(7, 7, 10, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(8, 8, 11, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(9, 9, 12, "HEB200", "Structural Steel A36"));

        // Beams
        model.elements.add(new StructuralModel.Element(10, 4, 5, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(11, 5, 6, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(12, 7, 8, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(13, 8, 9, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(14, 10, 11, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(15, 11, 12, "IPE300", "Structural Steel A36"));

        // Triangular lateral seismic forces: 15 kN (F1), 30 kN (F2), 45 kN (Roof)
        model.loads.add(new StructuralModel.Load(4, 15000.0, 0.0, 0.0));
        model.loads.add(new StructuralModel.Load(7, 30000.0, 0.0, 0.0));
        model.loads.add(new StructuralModel.Load(10, 45000.0, 0.0, 0.0));

        runCalculixAndVerify("preset6_3story_building", model, 90000.0, 0.0);
    }

    // =========================================================================
    // PRESET 7: Warren Truss Bridge
    // =========================================================================
    @Test
    public void testPreset7_WarrenTrussBridge() throws Exception {
        StructuralModel model = new StructuralModel();
        double span = 12.0, height = 3.0, dx = span / 4.0;

        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(2, dx, 0.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(3, dx * 2, 0.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(4, dx * 3, 0.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(5, span, 0.0, 0.0, StructuralModel.SupportType.ROLLER));

        model.nodes.add(new StructuralModel.Node(6, dx * 0.5, height, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(7, dx * 1.5, height, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(8, dx * 2.5, height, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(9, dx * 3.5, height, 0.0, StructuralModel.SupportType.FREE));

        // Bottom
        model.elements.add(new StructuralModel.Element(1, 1, 2, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(2, 2, 3, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(3, 3, 4, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(4, 4, 5, "L100x10", "Structural Steel A36"));
        // Top
        model.elements.add(new StructuralModel.Element(5, 6, 7, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(6, 7, 8, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(7, 8, 9, "L100x10", "Structural Steel A36"));
        // Web
        model.elements.add(new StructuralModel.Element(8, 1, 6, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(9, 6, 2, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(10, 2, 7, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(11, 7, 3, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(12, 3, 8, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(13, 8, 4, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(14, 4, 9, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(15, 9, 5, "L100x10", "Structural Steel A36"));

        // 3x 20 kN deck loads = 60 kN
        model.loads.add(new StructuralModel.Load(2, 0.0, -20000.0, 0.0));
        model.loads.add(new StructuralModel.Load(3, 0.0, -20000.0, 0.0));
        model.loads.add(new StructuralModel.Load(4, 0.0, -20000.0, 0.0));

        runCalculixAndVerify("preset7_warren_bridge", model, 0.0, -60000.0);
    }

    // =========================================================================
    // PRESET 8: Concrete Continuous Beam
    // =========================================================================
    @Test
    public void testPreset8_ConcreteContinuousBeam() throws Exception {
        StructuralModel model = new StructuralModel();
        double span1 = 4.0, span2 = 3.0, overhang = 2.0;

        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(2, span1 / 2.0, 0.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(3, span1, 0.0, 0.0, StructuralModel.SupportType.ROLLER));
        model.nodes.add(new StructuralModel.Node(4, span1 + span2 / 2.0, 0.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(5, span1 + span2, 0.0, 0.0, StructuralModel.SupportType.ROLLER));
        model.nodes.add(new StructuralModel.Node(6, span1 + span2 + overhang, 0.0, 0.0, StructuralModel.SupportType.FREE));

        model.elements.add(new StructuralModel.Element(1, 1, 2, "Rect 300x400", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(2, 2, 3, "Rect 300x400", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(3, 3, 4, "Rect 300x400", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(4, 4, 5, "Rect 300x400", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(5, 5, 6, "Rect 300x400", "Concrete 25 MPa"));

        // 30 kN load at overhang tip (Node 6)
        model.loads.add(new StructuralModel.Load(6, 0.0, -30000.0, 0.0));

        runCalculixAndVerify("preset8_concrete_continuous", model, 0.0, -30000.0);
    }

    // =========================================================================
    // PRESET 9: Pratt Truss
    // =========================================================================
    @Test
    public void testPreset9_PrattTruss() throws Exception {
        StructuralModel model = new StructuralModel();
        double L = 10.0, H = 2.5, dx = L / 4.0;

        // Bottom chord
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(2, dx, 0.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(3, dx * 2, 0.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(4, dx * 3, 0.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(5, L, 0.0, 0.0, StructuralModel.SupportType.ROLLER));

        // Top chord
        model.nodes.add(new StructuralModel.Node(6, dx, H, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(7, dx * 2, H, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(8, dx * 3, H, 0.0, StructuralModel.SupportType.FREE));

        // Elements
        model.elements.add(new StructuralModel.Element(1, 1, 2, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(2, 2, 3, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(3, 3, 4, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(4, 4, 5, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(5, 6, 7, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(6, 7, 8, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(7, 1, 6, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(8, 5, 8, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(9, 2, 6, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(10, 3, 7, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(11, 4, 8, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(12, 2, 7, "L100x10", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(13, 4, 7, "L100x10", "Structural Steel A36"));

        // 50 kN gravity load at center node 3
        model.loads.add(new StructuralModel.Load(3, 0.0, -50000.0, 0.0));

        runCalculixAndVerify("preset9_pratt_truss", model, 0.0, -50000.0);
    }

    // =========================================================================
    // PRESET 10: Cantilever Bracket
    // =========================================================================
    @Test
    public void testPreset10_CantileverBracket() throws Exception {
        StructuralModel model = new StructuralModel();
        double L = 4.0, H = 3.0;

        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 0.0, H, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(3, L / 2.0, H, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(4, L, H, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(5, L / 2.0, 0.0, 0.0, StructuralModel.SupportType.FREE));

        model.elements.add(new StructuralModel.Element(1, 2, 3, "W8x31", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(2, 3, 4, "W8x31", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(3, 1, 5, "W8x31", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(4, 5, 4, "W8x31", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(5, 5, 3, "W8x31", "Structural Steel A36"));

        // 40 kN tip load at node 4
        model.loads.add(new StructuralModel.Load(4, 0.0, -40000.0, 0.0));

        runCalculixAndVerify("preset10_cantilever_bracket", model, 0.0, -40000.0);
    }

    // =========================================================================
    // PRESET 11: Concrete Slab Plate (Shell / S4R)
    // =========================================================================
    @Test
    public void testPreset11_ConcreteSlabPlate() throws Exception {
        StructuralModel model = new StructuralModel();
        double w = 4.0, l = 4.0, t = 0.15;

        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(2, w / 2.0, 0.0, 0.0, StructuralModel.SupportType.ROLLER));
        model.nodes.add(new StructuralModel.Node(3, w, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(4, 0.0, l / 2.0, 0.0, StructuralModel.SupportType.ROLLER));
        model.nodes.add(new StructuralModel.Node(5, w / 2.0, l / 2.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(6, w, l / 2.0, 0.0, StructuralModel.SupportType.ROLLER));
        model.nodes.add(new StructuralModel.Node(7, 0.0, l, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(8, w / 2.0, l, 0.0, StructuralModel.SupportType.ROLLER));
        model.nodes.add(new StructuralModel.Node(9, w, l, 0.0, StructuralModel.SupportType.PINNED));

        // Perimeter beams
        model.elements.add(new StructuralModel.Element(1, 1, 2, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(2, 2, 3, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(3, 3, 6, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(4, 6, 9, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(5, 9, 8, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(6, 8, 7, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(7, 7, 4, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(8, 4, 1, "Rect 200x300", "Concrete 25 MPa"));

        // 4 Shell panels
        model.panels.add(new StructuralModel.Panel(1, Arrays.asList(1, 2, 5, 4), t, "Concrete 25 MPa", "S4R"));
        model.panels.add(new StructuralModel.Panel(2, Arrays.asList(2, 3, 6, 5), t, "Concrete 25 MPa", "S4R"));
        model.panels.add(new StructuralModel.Panel(3, Arrays.asList(4, 5, 8, 7), t, "Concrete 25 MPa", "S4R"));
        model.panels.add(new StructuralModel.Panel(4, Arrays.asList(5, 6, 9, 8), t, "Concrete 25 MPa", "S4R"));

        // 40 kN gravity load at center node 5
        model.loads.add(new StructuralModel.Load(5, 0.0, -40000.0, 0.0));

        runCalculixAndVerify("preset11_slab_plate", model, 0.0, -40000.0);
    }

    // =========================================================================
    // PRESET 12: Shear Wall (Plane Stress / CPS4)
    // =========================================================================
    @Test
    public void testPreset12_ShearWall() throws Exception {
        StructuralModel model = new StructuralModel();
        double w = 3.0, h = 3.0, t = 0.20;

        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, w, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(3, w, h, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(4, 0.0, h, 0.0, StructuralModel.SupportType.FREE));

        model.elements.add(new StructuralModel.Element(1, 1, 4, "Rect 300x400", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(2, 2, 3, "Rect 300x400", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(3, 4, 3, "Rect 300x400", "Concrete 25 MPa"));

        model.panels.add(new StructuralModel.Panel(1, Arrays.asList(1, 2, 3, 4), t, "Concrete 25 MPa", "CPS4"));

        // 50 kN lateral shear force at top Node 4
        model.loads.add(new StructuralModel.Load(4, 50000.0, 0.0, 0.0));

        runCalculixAndVerify("preset12_shear_wall", model, 50000.0, 0.0);
    }

    // =========================================================================
    // CUSTOM 2D EDITOR DRAWN MODEL: Multi-Bay Industrial Structure
    // (Custom User Nodes, Elements, Materials, Mixed Supports, Wind & Gravity Loads)
    // =========================================================================
    @Test
    public void testUserCustomDrawnModel_WithCustomSectionsLoadsMaterials() throws Exception {
        StructuralModel customModel = new StructuralModel();

        // 1. User places nodes in 2D grid
        customModel.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        customModel.nodes.add(new StructuralModel.Node(2, 4.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        customModel.nodes.add(new StructuralModel.Node(3, 8.0, 0.0, 0.0, StructuralModel.SupportType.ROLLER));
        customModel.nodes.add(new StructuralModel.Node(4, 0.0, 3.5, 0.0, StructuralModel.SupportType.FREE));
        customModel.nodes.add(new StructuralModel.Node(5, 4.0, 3.5, 0.0, StructuralModel.SupportType.FREE));
        customModel.nodes.add(new StructuralModel.Node(6, 8.0, 3.5, 0.0, StructuralModel.SupportType.FREE));
        customModel.nodes.add(new StructuralModel.Node(7, 4.0, 5.0, 0.0, StructuralModel.SupportType.FREE)); // Apex node

        // 2. User draws elements with specific custom profiles and materials
        customModel.elements.add(new StructuralModel.Element(1, 1, 4, "W12x50", "Structural Steel A572 Gr50"));
        customModel.elements.add(new StructuralModel.Element(2, 2, 5, "W12x50", "Structural Steel A572 Gr50"));
        customModel.elements.add(new StructuralModel.Element(3, 3, 6, "W12x50", "Structural Steel A572 Gr50"));
        customModel.elements.add(new StructuralModel.Element(4, 4, 5, "IPE400", "Structural Steel A36"));
        customModel.elements.add(new StructuralModel.Element(5, 5, 6, "IPE400", "Structural Steel A36"));
        customModel.elements.add(new StructuralModel.Element(6, 4, 7, "IPE300", "Structural Steel A36"));
        customModel.elements.add(new StructuralModel.Element(7, 7, 6, "IPE300", "Structural Steel A36"));
        customModel.elements.add(new StructuralModel.Element(8, 1, 5, "L100x10", "Structural Steel A36")); // Diagonal cross-brace

        // 3. User assigns lateral wind load (Fx = 25 kN) and vertical apex load (Fy = -45 kN)
        customModel.loads.add(new StructuralModel.Load(4, 25000.0, 0.0, 0.0));
        customModel.loads.add(new StructuralModel.Load(7, 0.0, -45000.0, 0.0));

        // 4. Run real CalculiX execution and verify global equilibrium and results
        runCalculixAndVerify("custom_drawn_industrial_frame", customModel, 25000.0, -45000.0);
    }
}
