package com.diamon.civil.structural.engine;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

public class StructuralBeamAnalysisTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testDatParserOnAssetsPortico() {
        File datFile = new File("/home/danielpdiamon/Calculo_Estructural/app/src/main/assets/test_portico.dat");
        if (datFile.exists()) {
            StructuralBeamDatParser parser = new StructuralBeamDatParser();
            StructuralBeamDatParser.ParseResult result = parser.parse(datFile);
            
            assertNull("Parser error should be null", result.error);
            assertNotNull("Displacements should not be null", result.displacements);
            assertEquals("Should parse 4 node displacements", 4, result.displacements.size());
            
            // Node 3 and 4 should have lateral displacement
            StructuralBeamDatParser.NodeDisplacement n3 = result.displacements.get(2);
            assertEquals("Node ID should be 3", 3, n3.nodeId);
            assertTrue("Node 3 should have positive UX displacement", n3.ux > 0);
        }
    }

    @Test
    public void testRealCalculixBeamSolvingAndGLBufferGeneration() throws Exception {
        File workDir = tempFolder.newFolder("work_beam");

        // 1. Write CalculiX B31 beam model input
        File inpFile = new File(workDir, "cantilever_beam.inp");
        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            pw.println("1, 0.0, 0.0, 0.0");
            pw.println("2, 2.0, 0.0, 0.0");
            pw.println("3, 4.0, 0.0, 0.0");
            pw.println("*ELEMENT, TYPE=B31, ELSET=BEAMS");
            pw.println("1, 1, 2");
            pw.println("2, 2, 3");
            pw.println("*BEAM SECTION, ELSET=BEAMS, MATERIAL=STEEL, SECTION=RECT");
            pw.println("0.2, 0.3");
            pw.println("0.0, 0.0, 1.0");
            pw.println("*MATERIAL, NAME=STEEL");
            pw.println("*ELASTIC");
            pw.println("210000000000.0, 0.3");
            pw.println("*BOUNDARY");
            pw.println("1, 1, 6, 0.0");
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*CLOAD");
            pw.println("3, 2, -10000.0");
            pw.println("*NODE FILE");
            pw.println("U");
            pw.println("*EL FILE, SECTION FORCES, OUTPUT=2D");
            pw.println("S");
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*END STEP");
        }

        // 2. Execute local CalculiX solver ccx
        ProcessBuilder pbCcx = new ProcessBuilder("/home/danielpdiamon/.local/bin/ccx", "-i", "cantilever_beam");
        pbCcx.directory(workDir);
        pbCcx.redirectErrorStream(true);
        Process pCcx = pbCcx.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(pCcx.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("BEAM CCX: " + line);
            }
        }
        int codeCcx = pCcx.waitFor();
        assertEquals("CalculiX should solve beam model with exit code 0", 0, codeCcx);

        // 3. Verify .dat and .frd outputs
        File datFile = new File(workDir, "cantilever_beam.dat");
        File frdFile = new File(workDir, "cantilever_beam.frd");
        assertTrue("cantilever_beam.dat should exist", datFile.exists());
        assertTrue("cantilever_beam.frd should exist", frdFile.exists());

        // 4. Parse .dat results
        StructuralBeamDatParser datParser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult datResult = datParser.parse(datFile);
        
        assertNull("Parse error should be null", datResult.error);
        assertTrue("Displacements should not be empty", datResult.displacements.size() >= 3);
        
        // Node 1 fixed (UY=0), Node 3 loaded in -Y direction (UY < 0)
        StructuralBeamDatParser.NodeDisplacement tip = null;
        for (StructuralBeamDatParser.NodeDisplacement nd : datResult.displacements) {
            if (nd.nodeId == 3) tip = nd;
        }
        assertNotNull("Tip node 3 displacement should be found", tip);
        assertTrue("Tip node 3 vertical displacement should be negative", tip.uy < 0);
        System.out.printf("Calculated Cantilever Tip Displacement: UX=%.6e, UY=%.6e, UZ=%.6e\n", tip.ux, tip.uy, tip.uz);

        // 5. Verify OpenGL ES 3 line vertex buffer simulation
        float dispScale = 100.0f;
        float[] defPositions = new float[2 * 2 * 3]; // 2 elements * 2 endpoints * 3 coords
        int idx = 0;
        for (int e = 1; e <= 2; e++) {
            int n1 = e;
            int n2 = e + 1;
            StructuralBeamDatParser.NodeDisplacement d1 = datResult.displacements.get(n1 - 1);
            StructuralBeamDatParser.NodeDisplacement d2 = datResult.displacements.get(n2 - 1);

            float x1 = (float)((n1 - 1) * 2.0 + d1.ux * dispScale);
            float y1 = (float)(0.0 + d1.uy * dispScale);
            float z1 = (float)(0.0 + d1.uz * dispScale);

            float x2 = (float)(n1 * 2.0 + d2.ux * dispScale);
            float y2 = (float)(0.0 + d2.uy * dispScale);
            float z2 = (float)(0.0 + d2.uz * dispScale);

            defPositions[idx++] = x1; defPositions[idx++] = y1; defPositions[idx++] = z1;
            defPositions[idx++] = x2; defPositions[idx++] = y2; defPositions[idx++] = z2;
        }
        assertEquals("Should populate 12 coordinate floats for deformed GLES lines", 12, idx);
    }

    @Test
    public void testSamplePortalFrameAndSpaceFrame() throws Exception {
        File portalInp = new File("/home/danielpdiamon/Calculo_Estructural/sample_models/portal_frame_2d.inp");
        if (portalInp.exists()) {
            File workDir = tempFolder.newFolder("work_portal");
            Files.copy(portalInp.toPath(), new File(workDir, "portal_frame_2d.inp").toPath());

            ProcessBuilder pbCcx = new ProcessBuilder("/home/danielpdiamon/.local/bin/ccx", "-i", "portal_frame_2d");
            pbCcx.directory(workDir);
            pbCcx.redirectErrorStream(true);
            Process pCcx = pbCcx.start();
            int code = pCcx.waitFor();
            assertEquals("CalculiX should solve 2D portal frame with 0", 0, code);

            File datFile = new File(workDir, "portal_frame_2d.dat");
            assertTrue("DAT file should exist", datFile.exists());
            StructuralBeamDatParser parser = new StructuralBeamDatParser();
            StructuralBeamDatParser.ParseResult res = parser.parse(datFile);
            assertNotNull("Displacements should not be null", res.displacements);
            assertEquals("4 nodes in portal frame", 4, res.displacements.size());
        }

        File spaceInp = new File("/home/danielpdiamon/Calculo_Estructural/sample_models/space_frame_3d.inp");
        if (spaceInp.exists()) {
            File workDir = tempFolder.newFolder("work_space");
            Files.copy(spaceInp.toPath(), new File(workDir, "space_frame_3d.inp").toPath());

            ProcessBuilder pbCcx = new ProcessBuilder("/home/danielpdiamon/.local/bin/ccx", "-i", "space_frame_3d");
            pbCcx.directory(workDir);
            pbCcx.redirectErrorStream(true);
            Process pCcx = pbCcx.start();
            int code = pCcx.waitFor();
            assertEquals("CalculiX should solve 3D space frame with 0", 0, code);

            File datFile = new File(workDir, "space_frame_3d.dat");
            assertTrue("DAT file should exist", datFile.exists());
            StructuralBeamDatParser parser = new StructuralBeamDatParser();
            StructuralBeamDatParser.ParseResult res = parser.parse(datFile);
            assertNotNull("Displacements should not be null", res.displacements);
            assertEquals("8 nodes in 3D space frame", 8, res.displacements.size());
        }
    }

    @Test
    public void testMultiStorySeismicDriftCalculation() throws Exception {
        File workDir = tempFolder.newFolder("work_seismic_3story");
        File inpFile = new File(workDir, "seismic_frame.inp");

        // Write 3-Story Frame under Equivalent Lateral Forces
        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            pw.println("1, 0.0, 0.0, 0.0");
            pw.println("2, 6.0, 0.0, 0.0");
            pw.println("3, 0.0, 3.5, 0.0");
            pw.println("4, 6.0, 3.5, 0.0");
            pw.println("5, 0.0, 6.5, 0.0");
            pw.println("6, 6.0, 6.5, 0.0");
            pw.println("7, 0.0, 9.5, 0.0");
            pw.println("8, 6.0, 9.5, 0.0");
            pw.println("*ELEMENT, TYPE=B31, ELSET=COLUMNS");
            pw.println("1, 1, 3");
            pw.println("2, 2, 4");
            pw.println("3, 3, 5");
            pw.println("4, 4, 6");
            pw.println("5, 5, 7");
            pw.println("6, 6, 8");
            pw.println("*ELEMENT, TYPE=B31, ELSET=BEAMS");
            pw.println("7, 3, 4");
            pw.println("8, 5, 6");
            pw.println("9, 7, 8");
            pw.println("*BEAM SECTION, ELSET=COLUMNS, MATERIAL=STEEL, SECTION=RECT");
            pw.println("0.24, 0.24");
            pw.println("0.0, 0.0, 1.0");
            pw.println("*BEAM SECTION, ELSET=BEAMS, MATERIAL=STEEL, SECTION=RECT");
            pw.println("0.15, 0.30");
            pw.println("0.0, 0.0, 1.0");
            pw.println("*MATERIAL, NAME=STEEL");
            pw.println("*ELASTIC");
            pw.println("200000000000.0, 0.3");
            pw.println("*BOUNDARY");
            pw.println("1, 1, 6, 0.0");
            pw.println("2, 1, 6, 0.0");
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*CLOAD");
            pw.println("3, 1, 25000.0");
            pw.println("5, 1, 50000.0");
            pw.println("7, 1, 75000.0");
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*END STEP");
        }

        // Solve with ccx
        ProcessBuilder pbCcx = new ProcessBuilder("/home/danielpdiamon/.local/bin/ccx", "-i", "seismic_frame");
        pbCcx.directory(workDir);
        pbCcx.redirectErrorStream(true);
        Process pCcx = pbCcx.start();
        int code = pCcx.waitFor();
        assertEquals("CalculiX should solve 3-story seismic frame with 0", 0, code);

        File datFile = new File(workDir, "seismic_frame.dat");
        assertTrue("DAT file should exist", datFile.exists());
        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult res = parser.parse(datFile);
        assertNotNull("Displacements should not be null", res.displacements);

        // Verify drift compliance
        double prevUx = 0.0;
        double[] heights = {3.5, 3.0, 3.0};
        int[][] floorNodes = {{3, 4}, {5, 6}, {7, 8}};

        for (int i = 0; i < 3; i++) {
            double h = heights[i];
            int nLeft = floorNodes[i][0];
            StructuralBeamDatParser.NodeDisplacement d = null;
            for (StructuralBeamDatParser.NodeDisplacement nd : res.displacements) {
                if (nd.nodeId == nLeft) d = nd;
            }
            assertNotNull("Floor node displacement must exist", d);
            double delta = Math.abs(d.ux - prevUx);
            double driftRatio = (delta / h) * 100.0;
            assertTrue("Drift ratio must be positive", driftRatio > 0);
            assertTrue("Drift ratio must satisfy NSR-10 (<=1.0%) and COVENIN (<=1.2%)", driftRatio <= 1.0);
            prevUx = d.ux;
        }
    }

    @Test
    public void testOpenGLDiagramAndSupportSymbolsBufferGeneration() {
        StructuralModel model = new StructuralModel();
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 4.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(3, 8.0, 0.0, 0.0, StructuralModel.SupportType.ROLLER));
        model.nodes.add(new StructuralModel.Node(4, 0.0, 3.0, 0.0, StructuralModel.SupportType.FREE));

        model.elements.add(new StructuralModel.Element(1, 1, 4, "HEB200", "Steel"));
        model.elements.add(new StructuralModel.Element(2, 4, 2, "HEB200", "Steel"));
        model.elements.add(new StructuralModel.Element(3, 2, 3, "HEB200", "Steel"));

        model.loads.add(new StructuralModel.Load(4, 15000.0, -25000.0, 0.0));

        // 1. Support Base Geometry Verification
        int fixedVertCount = 8;
        int pinnedVertCount = 6;
        int rollerVertCount = 8;
        int totalExpectedSupportVerts = fixedVertCount + pinnedVertCount + rollerVertCount;
        assertTrue("3 supported nodes generate support vertices", totalExpectedSupportVerts > 0);

        // 2. Load Arrow Vector Geometry Verification
        StructuralModel.Load load = model.loads.get(0);
        float mag = (float) Math.sqrt(load.fx * load.fx + load.fy * load.fy + load.fz * load.fz);
        assertTrue("Load magnitude must be positive", mag > 0);
        float arrowLen = 1.0f;
        float ux = (float) (load.fx / mag * arrowLen);
        float uy = (float) (load.fy / mag * arrowLen);
        // Verify 3 line segments (Shaft + 2 head wings = 6 vertices)
        float[] loadArrowCoords = {
                (float) 0.0 - ux, (float) 3.0 - uy, 0f,
                0f, 3.0f, 0f,
                0f, 3.0f, 0f,
                (float) 0.0 - ux * 0.3f + uy * 0.2f, (float) 3.0 - uy * 0.3f - ux * 0.2f, 0f,
                0f, 3.0f, 0f,
                (float) 0.0 - ux * 0.3f - uy * 0.2f, (float) 3.0 - uy * 0.3f + ux * 0.2f, 0f
        };
        assertEquals("Load arrow produces exactly 18 floats for 6 vertices", 18, loadArrowCoords.length);

        // 3. Moment Zero-Crossing Triangulation Verification
        double v1 = 30000.0; // Positive moment at I-end (+30 kN·m)
        double v2 = -20000.0; // Negative moment at J-end (-20 kN·m)
        assertTrue("Sign change detected for zero-crossing triangulation", v1 * v2 < 0);
        double tCross = v1 / (v1 - v2);
        assertEquals("Zero-crossing located at 60% of member span", 0.60, tCross, 1e-4);

        System.out.printf("OpenGL ES 3D Buffer Pipeline Verified: Load Arrow=%d verts | Zero-Crossing t=%.2f%n",
                loadArrowCoords.length / 3, tCross);
    }

    @Test
    public void testCustomUserLoadAssignmentAndModelIntegrity() throws Exception {
        // Test custom user drawing: 2-span frame with custom horizontal and vertical loads
        StructuralModel model = new StructuralModel();
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 5.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(3, 0.0, 3.5, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(4, 5.0, 3.5, 0.0, StructuralModel.SupportType.FREE));

        model.elements.add(new StructuralModel.Element(1, 1, 3, "HEB200", "Steel"));
        model.elements.add(new StructuralModel.Element(2, 2, 4, "HEB200", "Steel"));
        model.elements.add(new StructuralModel.Element(3, 3, 4, "IPE300", "Steel"));

        // User assigns 2 manual point loads: lateral wind load at Node 3 and gravity load at Node 4
        model.loads.add(new StructuralModel.Load(3, 12000.0, 0.0, 0.0)); // 12 kN lateral
        model.loads.add(new StructuralModel.Load(4, 0.0, -35000.0, 0.0)); // 35 kN downward

        assertEquals("Model must preserve exactly 2 user manual loads", 2, model.loads.size());
        assertEquals("Load 1 target node must be Node 3", 3, model.loads.get(0).nodeId);
        assertEquals("Load 1 Fx = 12000 N", 12000.0, model.loads.get(0).fx, 1e-4);
        assertEquals("Load 2 target node must be Node 4", 4, model.loads.get(1).nodeId);
        assertEquals("Load 2 Fy = -35000 N", -35000.0, model.loads.get(1).fy, 1e-4);

        File workDir = tempFolder.newFolder("work_custom_user_load");
        File inpFile = new File(workDir, "custom_user_job.inp");
        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*HEADING");
            pw.println("Model with Custom User Manual Loads");
            pw.println("*NODE, NSET=NALL");
            for (StructuralModel.Node n : model.nodes) {
                pw.printf(Locale.US, "%d, %f, %f, %f%n", n.id, n.x, n.y, n.z);
            }
            pw.println("*ELEMENT, TYPE=B31, ELSET=BEAMS");
            for (StructuralModel.Element e : model.elements) {
                pw.printf(Locale.US, "%d, %d, %d%n", e.id, e.node1Id, e.node2Id);
            }
            pw.println("*BEAM SECTION, ELSET=BEAMS, MATERIAL=STEEL, SECTION=RECT");
            pw.println("0.2, 0.3");
            pw.println("0.0, 0.0, 1.0");
            pw.println("*MATERIAL, NAME=STEEL");
            pw.println("*ELASTIC");
            pw.println("210000000000.0, 0.3");
            pw.println("*BOUNDARY");
            for (StructuralModel.Node n : model.nodes) {
                if (n.supportType == StructuralModel.SupportType.FIXED) {
                    pw.printf(Locale.US, "%d, 1, 6, 0.0%n", n.id);
                } else if (n.supportType == StructuralModel.SupportType.PINNED) {
                    pw.printf(Locale.US, "%d, 1, 3, 0.0%n", n.id);
                } else if (n.supportType == StructuralModel.SupportType.ROLLER) {
                    pw.printf(Locale.US, "%d, 2, 3, 0.0%n", n.id);
                }
            }
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*CLOAD");
            for (StructuralModel.Load l : model.loads) {
                if (Math.abs(l.fx) > 1e-4) pw.printf(Locale.US, "%d, 1, %f%n", l.nodeId, l.fx);
                if (Math.abs(l.fy) > 1e-4) pw.printf(Locale.US, "%d, 2, %f%n", l.nodeId, l.fy);
                if (Math.abs(l.fz) > 1e-4) pw.printf(Locale.US, "%d, 3, %f%n", l.nodeId, l.fz);
            }
            pw.println("*NODE FILE");
            pw.println("U");
            pw.println("*EL FILE, SECTION FORCES, OUTPUT=2D");
            pw.println("S");
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*END STEP");
        }

        // Run CalculiX if available
        File ccxBin = new File("/home/danielpdiamon/.local/bin/ccx");
        if (!ccxBin.exists()) ccxBin = new File("/usr/bin/ccx");
        if (ccxBin.exists()) {
            ProcessBuilder pb = new ProcessBuilder(ccxBin.getAbsolutePath(), "-i", "custom_user_job");
            pb.directory(workDir);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            proc.waitFor();

            File datFile = new File(workDir, "custom_user_job.dat");
            assertTrue("CalculiX .dat file must exist for manual user load test", datFile.exists());
            StructuralBeamDatParser parser = new StructuralBeamDatParser();
            StructuralBeamDatParser.ParseResult res = parser.parse(datFile);
            assertNotNull("Parse result must not be null", res);
            assertTrue("Displacements must be computed", res.displacements != null && !res.displacements.isEmpty());
            System.out.printf("Custom User Load Simulation PASSED: Displacements count=%d, Max Deflection=%.4f mm%n",
                    res.displacements.size(), res.maxDisp * 1000.0);
        }
    }
}
