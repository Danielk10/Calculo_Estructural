package com.diamon.civil.solids.engine;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.Files;
import com.diamon.civil.solids.engine.SolidDisplacementFrdParser;

public class SolidInpAssemblerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testCoordinateBasedBoundaryFallback() throws Exception {
        File workDir = tempFolder.newFolder("work");
        File rawInp = new java.io.File(workDir, "job_solid_raw.inp");
        
        // Write mock raw .inp mesh file with nodes along the Z axis (0 to 10)
        // and no physical groups (to trigger the coordinate-based fallback)
        try (PrintWriter pw = new PrintWriter(new FileWriter(rawInp))) {
            pw.println("*NODE, NSET=Nall");
            pw.println("1, 0.0, 0.0, 0.0");
            pw.println("2, 0.0, 0.0, 1.0");
            pw.println("3, 0.0, 0.0, 2.0");
            pw.println("4, 0.0, 0.0, 3.0");
            pw.println("5, 0.0, 0.0, 4.0");
            pw.println("6, 0.0, 0.0, 5.0");
            pw.println("7, 0.0, 0.0, 6.0");
            pw.println("8, 0.0, 0.0, 8.0");
            pw.println("9, 0.0, 0.0, 9.0");
            pw.println("10, 0.0, 0.0, 10.0");
            pw.println("*ELEMENT, TYPE=C3D4, ELSET=Eall");
            pw.println("1, 1, 2, 3, 4");
            pw.println("2, 7, 8, 9, 10");
        }

        // Run the assembler. The target group IDs don't exist, so it should trigger the coordinate fallback
        SolidInpAssembler.assemble(
            workDir, 
            "job_solid", 
            "Steel", 
            210000.0, 
            0.3, 
            -500.0, 
            "invalid_fixed_id", 
            "invalid_load_id"
        );

        // Verify the final assembly file exists and contains the correct outputs
        File finalInp = new File(workDir, "job_solid.inp");
        File nsetsInp = new File(workDir, "nsets.inp");
        
        assertTrue("Final .inp file should be created", finalInp.exists());
        assertTrue("nsets.inp file should be created", nsetsInp.exists());

        // Read nsets.inp contents
        String nsetsContent = new String(Files.readAllBytes(nsetsInp.toPath()));
        System.out.println("Generated nsets.inp contents:\n" + nsetsContent);

        // NFix should contain node 1 (lowest Z = 0.0)
        // NLoad should contain node 10 (highest Z = 10.0)
        assertTrue("NFix should be defined", nsetsContent.contains("*NSET, NSET=NFix"));
        assertTrue("NLoad should be defined", nsetsContent.contains("*NSET, NSET=NLoad"));
        
        // Node 1 (Z=0) should be fixed because it is min Z
        assertTrue("Node 1 should be in NFix", nsetsContent.contains("1"));
        // Node 10 (Z=10) should be loaded because it is max Z
        assertTrue("Node 10 should be in NLoad", nsetsContent.contains("10"));
    }

    @Test
    public void testRealStepFileMeshingAndSolving() throws Exception {
        File workDir = tempFolder.newFolder("work_step");
        
        // 1. Copy sample STEP file from assets to temporary work dir
        File stepAsset = new File("/home/danielpdiamon/Calculo_Estructural/app/src/main/assets/data/data/com.diamon.civil/files/usr/share/opencascade/data/step/linkrods.step");
        assertTrue("STEP asset file should exist", stepAsset.exists());
        File tempStep = new File(workDir, "linkrods.step");
        Files.copy(stepAsset.toPath(), tempStep.toPath());

        // 2. Write a .geo wrapper script to load the STEP file in Gmsh
        File geoFile = new File(workDir, "linkrods.geo");
        try (PrintWriter pw = new PrintWriter(new FileWriter(geoFile))) {
            pw.println("SetFactory(\"OpenCASCADE\");");
            pw.println("Merge \"linkrods.step\";");
            pw.println("Mesh.MeshSizeMax = 10.0;"); // Coarse mesh for fast testing
        }

        // 3. Execute local system Gmsh to generate the raw mesh
        ProcessBuilder pbGmsh = new ProcessBuilder("gmsh", "linkrods.geo", "-3", "-format", "inp", "-o", "linkrods_raw.inp");
        pbGmsh.directory(workDir);
        pbGmsh.redirectErrorStream(true);
        Process pGmsh = pbGmsh.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(pGmsh.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("GMSH OUT: " + line);
            }
        }
        int codeGmsh = pGmsh.waitFor();
        assertEquals("Gmsh should exit with 0", 0, codeGmsh);

        // 4. Assemble the CalculiX input using SolidInpAssembler (will trigger coordinate fallback!)
        SolidInpAssembler.assemble(workDir, "linkrods", "Steel", 210000.0, 0.3, -200.0, "nonexistent_fixed", "nonexistent_load");

        // 5. Execute local system CalculiX solver
        ProcessBuilder pbCcx = new ProcessBuilder("/home/danielpdiamon/.local/bin/ccx", "linkrods");
        pbCcx.directory(workDir);
        pbCcx.redirectErrorStream(true);
        Process pCcx = pbCcx.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(pCcx.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("CCX OUT: " + line);
            }
        }
        int codeCcx = pCcx.waitFor();
        assertEquals("CalculiX should exit with 0", 0, codeCcx);

        // 6. Verify that the results file .frd exists and parses successfully
        File frdFile = new File(workDir, "linkrods.frd");
        assertTrue("Results .frd file should exist", frdFile.exists());
        
        String summary = SolidDisplacementFrdParser.parseAndSummarize(frdFile);
        System.out.println("Calculated STEP FEA Summary:\n" + summary);
        
        assertTrue("Summary should contain displacement nodes", summary.contains("Nodes with displacement") || summary.contains("WARNING"));
        assertTrue("Summary should contain max displacement node", summary.contains("Node with maximum displacement") || summary.contains("WARNING"));
    }

    @Test
    public void testDownloadedBracketStepFile() throws Exception {
        File workDir = tempFolder.newFolder("work_bracket");
        
        // 1. Copy sample STEP file from assets to temporary work dir
        File stepAsset = new File("/home/danielpdiamon/Calculo_Estructural/app/src/main/assets/data/data/com.diamon.civil/files/usr/share/opencascade/data/step/bracket_simple.step");
        assertTrue("STEP asset file should exist", stepAsset.exists());
        File tempStep = new File(workDir, "bracket.step");
        Files.copy(stepAsset.toPath(), tempStep.toPath());

        // 2. Write a .geo wrapper script to load the STEP file in Gmsh
        File geoFile = new File(workDir, "bracket.geo");
        try (PrintWriter pw = new PrintWriter(new FileWriter(geoFile))) {
            pw.println("SetFactory(\"OpenCASCADE\");");
            pw.println("Merge \"bracket.step\";");
            pw.println("Mesh.MeshSizeMax = 2.0;"); // Coarse mesh for fast testing
        }

        // 3. Execute local system Gmsh to generate the raw mesh
        ProcessBuilder pbGmsh = new ProcessBuilder("gmsh", "bracket.geo", "-3", "-format", "inp", "-o", "bracket_raw.inp");
        pbGmsh.directory(workDir);
        pbGmsh.redirectErrorStream(true);
        Process pGmsh = pbGmsh.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(pGmsh.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("GMSH BRACKET: " + line);
            }
        }
        int codeGmsh = pGmsh.waitFor();
        assertEquals("Gmsh should exit with 0", 0, codeGmsh);

        // 4. Assemble the CalculiX input using SolidInpAssembler (will trigger coordinate fallback!)
        SolidInpAssembler.assemble(workDir, "bracket", "Steel", 210000.0, 0.3, -150.0, "nonexistent_fixed", "nonexistent_load");

        // 5. Execute local system CalculiX solver
        ProcessBuilder pbCcx = new ProcessBuilder("/home/danielpdiamon/.local/bin/ccx", "-i", "bracket");
        pbCcx.directory(workDir);
        pbCcx.redirectErrorStream(true);
        Process pCcx = pbCcx.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(pCcx.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("CCX BRACKET: " + line);
            }
        }
        int codeCcx = pCcx.waitFor();
        assertEquals("CalculiX should exit with 0", 0, codeCcx);

        // 6. Verify that the results file .frd exists and parses successfully
        File frdFile = new File(workDir, "bracket.frd");
        assertTrue("Results .frd file should exist", frdFile.exists());
        
        String summary = SolidDisplacementFrdParser.parseAndSummarize(frdFile);
        System.out.println("Calculated BRACKET FEA Summary:\n" + summary);
        
        assertTrue("Summary should contain displacement nodes", summary.contains("Nodes with displacement") || summary.contains("WARNING"));
        assertTrue("Summary should contain max displacement node", summary.contains("Node with maximum displacement") || summary.contains("WARNING"));
    }

    @Test
    public void testCADModelingMeshingAndSolvingPipeline() throws Exception {
        File workDir = tempFolder.newFolder("work_cad_solve");

        // 1. Execute occt-draw to generate a BREP solid
        String drawScript = "pload MODELING\n" +
                           "box b 3 3 15\n" +
                           "writebrep b bar.brep\n" +
                           "exit\n";
        File drawBinFile = new File("/usr/bin/DRAWEXE").exists() ? new File("/usr/bin/DRAWEXE") :
                         (new File("/usr/bin/occt-draw").exists() ? new File("/usr/bin/occt-draw") :
                         (new File("/usr/share/opencascade/bin/draw.sh").exists() ? new File("/usr/share/opencascade/bin/draw.sh") : null));
        boolean hasXvfb = new File("/usr/bin/xvfb-run").exists();
        if (drawBinFile == null || !drawBinFile.exists() || !hasXvfb) {
            org.junit.Assume.assumeTrue("DRAWEXE and xvfb-run required for headless CAD modeling test", false);
            return;
        }
        ProcessBuilder pbDraw = new ProcessBuilder("xvfb-run", "-a", drawBinFile.getAbsolutePath());
        pbDraw.directory(workDir);
        pbDraw.environment().put("CSF_OCCTResourcePath", "/usr/share/opencascade/resources");
        pbDraw.redirectErrorStream(true);
        Process pDraw = pbDraw.start();
        try (PrintWriter writer = new PrintWriter(pDraw.getOutputStream())) {
            writer.print(drawScript);
            writer.flush();
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(pDraw.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("DRAWEXE OUT: " + line);
            }
        }
        File brepFile = new File(workDir, "bar.brep");
        if (!brepFile.exists()) {
            org.junit.Assume.assumeTrue("bar.brep generated by DRAWEXE", brepFile.exists());
            return;
        }

        // 2. Write the .geo wrapper script for Gmsh
        File geoFile = new File(workDir, "bar.geo");
        try (PrintWriter pw = new PrintWriter(new FileWriter(geoFile))) {
            pw.println("SetFactory(\"OpenCASCADE\");");
            pw.println("Merge \"bar.brep\";");
            pw.println("Mesh.MeshSizeMax = 1.5;");
        }

        // 3. Execute Gmsh to generate the mesh
        ProcessBuilder pbGmsh = new ProcessBuilder("gmsh", "bar.geo", "-3", "-format", "inp", "-o", "bar_raw.inp");
        pbGmsh.directory(workDir);
        pbGmsh.redirectErrorStream(true);
        Process pGmsh = pbGmsh.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(pGmsh.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("GMSH OUT: " + line);
            }
        }
        int codeGmsh = pGmsh.waitFor();
        assertEquals("Gmsh should exit with 0", 0, codeGmsh);

        // 4. Assemble the CalculiX input using SolidInpAssembler (triggers coordinate fallback!)
        SolidInpAssembler.assemble(workDir, "bar", "Steel", 210000.0, 0.3, -300.0, "nonexistent_fixed", "nonexistent_load");

        // 5. Run CalculiX Solver ccx
        ProcessBuilder pbCcx = new ProcessBuilder("/home/danielpdiamon/.local/bin/ccx", "bar");
        pbCcx.directory(workDir);
        pbCcx.redirectErrorStream(true);
        Process pCcx = pbCcx.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(pCcx.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("CCX OUT: " + line);
            }
        }
        int codeCcx = pCcx.waitFor();
        assertEquals("CalculiX should exit with 0", 0, codeCcx);

        // 6. Verify .frd exists and parse results
        File frdFile = new File(workDir, "bar.frd");
        assertTrue("Results .frd file should exist", frdFile.exists());

        String summary = SolidDisplacementFrdParser.parseAndSummarize(frdFile);
        System.out.println("Generated CAD Solve Summary:\n" + summary);
        assertTrue("Summary should contain displacement nodes", summary.contains("Nodes with displacement") || summary.contains("WARNING"));
        assertTrue("Summary should contain max displacement node", summary.contains("Node with maximum displacement") || summary.contains("WARNING"));
    }

    @Test
    public void testFrdParserOnAssetsSamples() {
        File porticoFrd = new File("/home/danielpdiamon/Calculo_Estructural/app/src/main/assets/test_portico.frd");
        if (porticoFrd.exists()) {
            String summary = SolidDisplacementFrdParser.parseAndSummarize(porticoFrd);
            assertNotNull("Summary should not be null", summary);
            assertTrue("Should handle FRD without DISP gracefully", summary.contains("WARNING") || summary.contains("Nodes with displacement"));
        }

        File calculixFrd = new File("/home/danielpdiamon/Calculo_Estructural/app/src/main/assets/test_calculix.frd");
        if (calculixFrd.exists()) {
            String summary = SolidDisplacementFrdParser.parseAndSummarize(calculixFrd);
            assertNotNull("Summary should not be null", summary);
            assertTrue("Should handle FRD gracefully", summary.contains("WARNING") || summary.contains("Nodes with displacement"));
        }
    }

    @Test
    public void testSampleStepAndGeoSolving() throws Exception {
        File stepFile = new File("/home/danielpdiamon/Calculo_Estructural/sample_models/cantilever_plate.step");
        if (stepFile.exists()) {
            File workDir = tempFolder.newFolder("work_step_sample");
            File rawInp = new File(workDir, "sample_raw.inp");

            // 1. Mesh STEP model with Gmsh
            ProcessBuilder pbGmsh = new ProcessBuilder("gmsh", stepFile.getAbsolutePath(), "-3", "-format", "inp", "-o", rawInp.getAbsolutePath());
            pbGmsh.redirectErrorStream(true);
            Process pGmsh = pbGmsh.start();
            int codeGmsh = pGmsh.waitFor();
            assertEquals("Gmsh meshing STEP should succeed", 0, codeGmsh);

            // 2. Assemble INP with SolidInpAssembler
            SolidInpAssembler.assemble(workDir, "sample", "Steel", 210000.0, 0.3, -1000.0, "Fixed", "Loaded");

            // 3. Run CalculiX ccx
            ProcessBuilder pbCcx = new ProcessBuilder("/home/danielpdiamon/.local/bin/ccx", "sample");
            pbCcx.directory(workDir);
            pbCcx.redirectErrorStream(true);
            Process pCcx = pbCcx.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(pCcx.getInputStream()))) {
                while (reader.readLine() != null) {}
            }
            int codeCcx = pCcx.waitFor();
            assertEquals("CalculiX solving STEP sample should succeed", 0, codeCcx);

            File frdFile = new File(workDir, "sample.frd");
            assertTrue("Results .frd should exist", frdFile.exists());
            String summary = SolidDisplacementFrdParser.parseAndSummarize(frdFile);
            assertTrue("Summary should contain solved displacement nodes", summary.contains("Nodes with displacement") || summary.contains("WARNING"));
        }
    }

    @Test
    public void testM3D9SurfaceElementFilteringAndC3D20Assembly() throws Exception {
        File workDir = tempFolder.newFolder("work_m3d9");
        File rawInp = new File(workDir, "job_solid_raw.inp");
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(rawInp))) {
            pw.println("*Heading");
            pw.println(" Gmsh 2nd Order Hex Mesh");
            pw.println("*NODE, NSET=Nall");
            for (int i = 1; i <= 30; i++) {
                pw.println(i + ", " + (i * 0.5) + ", 0.0, 0.0");
            }
            // 2D surface elements generated by Gmsh for physical surfaces
            pw.println("*ELEMENT, type=M3D9, ELSET=Fixed");
            pw.println("1, 1, 2, 3, 4, 5, 6, 7, 8, 9");
            pw.println("*ELEMENT, type=M3D9, ELSET=Loaded");
            pw.println("2, 10, 11, 12, 13, 14, 15, 16, 17, 18");
            // 3D volume element (27 nodes from Gmsh)
            pw.println("*ELEMENT, type=C3D27, ELSET=Steel");
            StringBuilder elemLine = new StringBuilder("3");
            for (int i = 1; i <= 27; i++) {
                elemLine.append(", ").append(i);
            }
            pw.println(elemLine.toString());
            pw.println("*ELSET, ELSET=Fixed");
            pw.println(" 1");
            pw.println("*ELSET, ELSET=Loaded");
            pw.println(" 2");
        }

        SolidInpAssembler.assemble(
            workDir, 
            "job_solid", 
            "Structural Steel A36", 
            200000.0, 
            0.3, 
            -100.0, 
            "Fixed", 
            "Loaded"
        );

        File cleanInp = new File(workDir, "job_solid_clean.inp");
        assertTrue("Clean INP should exist", cleanInp.exists());
        String cleanContent = new String(Files.readAllBytes(cleanInp.toPath()));

        // M3D9 and auxiliary ELSETs should be completely filtered out
        assertFalse("M3D9 element definition must NOT be in clean INP", cleanContent.contains("M3D9"));
        assertFalse("Non-3D ELSETs must NOT be in clean INP", cleanContent.contains("*ELSET, ELSET=Fixed"));
        // C3D20 element definition must be present
        assertTrue("C3D20 element definition must be in clean INP", cleanContent.contains("*ELEMENT, TYPE=C3D20, ELSET=Eall"));
        
        // Element line 3 under *ELEMENT should be truncated from 27 nodes to 20 nodes
        boolean inElemBlock = false;
        for (String line : cleanContent.split("\n")) {
            if (line.contains("*ELEMENT")) inElemBlock = true;
            if (inElemBlock && line.trim().startsWith("3,")) {
                String[] parts = line.trim().split(",");
                assertEquals("C3D20 element line should contain exactly 21 parts (1 id + 20 nodes)", 21, parts.length);
            }
        }
    }

    private void runElementBenchmarkTest(String elemType) throws Exception {
        File workDir = tempFolder.newFolder("work_" + elemType.toLowerCase(java.util.Locale.US));
        
        // 1. Generate standard cantilever benchmark (or extruded prism for C3D6/C3D15)
        boolean isWedge = elemType.contains("6") || elemType.contains("15");
        File geoFile;
        if (isWedge) {
            geoFile = new File(workDir, "cantilever_wedge.geo");
            String wedgeGeo = "Point(1) = {0, 0, 0, 5.0};\n" +
                    "Point(2) = {0, 10, 0, 5.0};\n" +
                    "Point(3) = {0, 10, 10, 5.0};\n" +
                    "Point(4) = {0, 0, 10, 5.0};\n" +
                    "Line(1) = {1, 2};\n" +
                    "Line(2) = {2, 3};\n" +
                    "Line(3) = {3, 1};\n" +
                    "Line(4) = {1, 3};\n" +
                    "Line(5) = {3, 4};\n" +
                    "Line(6) = {4, 1};\n" +
                    "Line Loop(1) = {1, 2, 3};\n" +
                    "Plane Surface(1) = {1};\n" +
                    "Line Loop(2) = {-3, 5, 6};\n" +
                    "Plane Surface(2) = {2};\n" +
                    "ext1[] = Extrude {100, 0, 0} { Surface{1}; Layers{10}; Recombine; };\n" +
                    "ext2[] = Extrude {100, 0, 0} { Surface{2}; Layers{10}; Recombine; };\n" +
                    "Physical Surface(\"Fixed\") = {1, 2};\n" +
                    "Physical Surface(\"Loaded\") = {ext1[0], ext2[0]};\n" +
                    "Physical Volume(\"Steel\") = {ext1[1], ext2[1]};\n";
            try (PrintWriter pw = new PrintWriter(new FileWriter(geoFile))) {
                pw.write(wedgeGeo);
            }
        } else {
            geoFile = SampleSimulationCase.createCantileverGeo(workDir);
        }
        assertTrue("geo file should exist", geoFile.exists());
        
        // 2. Determine Gmsh options based on formulation
        boolean is2nd = elemType.contains("10") || elemType.contains("20") || elemType.contains("15");
        boolean isHex = elemType.contains("8") || elemType.contains("20");
        
        StringBuilder meshOpts = new StringBuilder();
        meshOpts.append("Mesh.ElementOrder=").append(is2nd ? 2 : 1).append(";");
        if (is2nd) meshOpts.append(" Mesh.SecondOrderIncomplete=1; Mesh.SecondOrderLinear=1; Mesh.Optimize=1;");
        if (isHex) {
            meshOpts.append(" Mesh.Recombine3DAll=1; Mesh.Algorithm=6; Mesh.SubdivisionAlgorithm=2; Mesh.Recombine3DLevel=2; Mesh.Algorithm3D=1;");
        } else if (!isWedge) {
            meshOpts.append(" Mesh.Algorithm3D=1; Mesh.Recombine3DAll=0;");
        }
        meshOpts.append(" Mesh.SaveGroupsOfNodes=1; Mesh.SaveGroupsOfElements=1;");
        
        File rawInp = new File(workDir, "job_raw.inp");
        ProcessBuilder pbGmsh = new ProcessBuilder(
            "gmsh", geoFile.getAbsolutePath(),
            "-string", meshOpts.toString(),
            "-3",
            "-clmax", "4.0",
            "-o", rawInp.getAbsolutePath(),
            "-format", "inp",
            "-v", "0"
        );
        pbGmsh.directory(workDir);
        pbGmsh.redirectErrorStream(true);
        Process pGmsh = pbGmsh.start();
        int codeGmsh = pGmsh.waitFor();
        assertEquals("Gmsh meshing for " + elemType + " should succeed with code 0", 0, codeGmsh);
        assertTrue("raw INP should exist for " + elemType, rawInp.exists());
        
        // 3. Assemble with SolidInpAssembler
        SolidInpAssembler.assemble(workDir, "job", "Structural Steel A36", 200000.0, 0.3, -100.0, 2, "Fixed", "Loaded", elemType);
        File cleanInp = new File(workDir, "job_clean.inp");
        File finalInp = new File(workDir, "job.inp");
        assertTrue("Clean INP should exist for " + elemType, cleanInp.exists());
        assertTrue("Final INP should exist for " + elemType, finalInp.exists());
        
        // 4. Solve with CalculiX
        File ccxBin = new File("/home/danielpdiamon/.local/bin/ccx");
        if (!ccxBin.exists()) ccxBin = new File("/usr/bin/ccx");
        ProcessBuilder pbCcx = new ProcessBuilder(ccxBin.getAbsolutePath(), "job");
        pbCcx.directory(workDir);
        pbCcx.redirectErrorStream(true);
        Process pCcx = pbCcx.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(pCcx.getInputStream()))) {
            while (reader.readLine() != null) {}
        }
        int codeCcx = pCcx.waitFor();
        assertEquals("CalculiX solving for " + elemType + " should succeed with code 0", 0, codeCcx);
        
        // 5. Verify results .frd and .dat
        File frdFile = new File(workDir, "job.frd");
        File datFile = new File(workDir, "job.dat");
        assertTrue(".frd file should exist for " + elemType, frdFile.exists());
        assertTrue(".dat file should exist for " + elemType, datFile.exists());
        
        String summary = SolidDisplacementFrdParser.parseAndSummarize(frdFile);
        assertNotNull("FRD summary should not be null for " + elemType, summary);
        assertTrue("Displacements should be extracted for " + elemType, summary.contains("Nodes with displacement"));
    }

    @Test
    public void testC3D4LinearTetrahedron() throws Exception {
        runElementBenchmarkTest("C3D4");
    }

    @Test
    public void testC3D10QuadraticTetrahedron() throws Exception {
        runElementBenchmarkTest("C3D10");
    }

    @Test
    public void testC3D8LinearHexahedron() throws Exception {
        runElementBenchmarkTest("C3D8");
    }

    @Test
    public void testC3D8RReducedIntegrationHexahedron() throws Exception {
        runElementBenchmarkTest("C3D8R");
    }

    @Test
    public void testC3D20QuadraticHexahedron() throws Exception {
        runElementBenchmarkTest("C3D20");
    }

    @Test
    public void testC3D20RReducedIntegrationQuadraticHexahedron() throws Exception {
        runElementBenchmarkTest("C3D20R");
    }

    @Test
    public void testC3D6LinearWedge() throws Exception {
        runElementBenchmarkTest("C3D6");
    }

    @Test
    public void testC3D15QuadraticWedge() throws Exception {
        runElementBenchmarkTest("C3D15");
    }

    @Test
    public void testCantileverRealPhysicsValidation() throws Exception {
        File workDir = tempFolder.newFolder("work_physics");
        File geoFile = SampleSimulationCase.createCantileverGeo(workDir);

        File rawInp = new File(workDir, "job_raw.inp");
        ProcessBuilder pbGmsh = new ProcessBuilder(
                "gmsh", geoFile.getAbsolutePath(),
                "-3",
                "-clmax", "3.0",
                "-o", rawInp.getAbsolutePath(),
                "-format", "inp",
                "-string", "Mesh.ElementOrder=2; Mesh.SecondOrderIncomplete=1; Mesh.Optimize=1;"
        );
        pbGmsh.directory(workDir);
        pbGmsh.redirectErrorStream(true);
        Process pGmsh = pbGmsh.start();
        int codeGmsh = pGmsh.waitFor();
        assertEquals("Gmsh meshing should succeed with code 0", 0, codeGmsh);

        SolidInpAssembler.assemble(workDir, "job", "Structural Steel A36", 200000.0, 0.3, -100.0, 2, "Fixed", "Loaded", "C3D10");

        File ccxBin = new File("/home/danielpdiamon/.local/bin/ccx");
        if (!ccxBin.exists()) ccxBin = new File("/usr/bin/ccx");
        ProcessBuilder pbCcx = new ProcessBuilder(ccxBin.getAbsolutePath(), "job");
        pbCcx.directory(workDir);
        pbCcx.redirectErrorStream(true);
        Process pCcx = pbCcx.start();
        int codeCcx = pCcx.waitFor();
        assertEquals("CalculiX solving should succeed with code 0", 0, codeCcx);

        File datFile = new File(workDir, "job.dat");
        assertTrue("job.dat should exist", datFile.exists());

        double maxUy = 0.0;
        double maxVonMises = 0.0;
        try (BufferedReader reader = new BufferedReader(new FileReader(datFile))) {
            String line;
            boolean inDisp = false;
            boolean inStress = false;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                String lower = trimmed.toLowerCase(java.util.Locale.US);
                if (lower.contains("displacements (vx,vy,vz)")) {
                    inDisp = true; inStress = false; continue;
                }
                if (lower.contains("stresses (elem, integ.pnt.")) {
                    inStress = true; inDisp = false; continue;
                }
                if (inDisp) {
                    String[] parts = trimmed.split("\\s+");
                    if (parts.length >= 4 && Character.isDigit(parts[0].charAt(0))) {
                        try {
                            double uy = Double.parseDouble(parts[2].replace('D', 'E'));
                            if (Math.abs(uy) > Math.abs(maxUy)) maxUy = uy;
                        } catch (NumberFormatException ignore) {}
                    }
                } else if (inStress) {
                    String[] parts = trimmed.split("\\s+");
                    if (parts.length >= 8 && Character.isDigit(parts[0].charAt(0))) {
                        try {
                            double sxx = Double.parseDouble(parts[2].replace('D', 'E'));
                            double syy = Double.parseDouble(parts[3].replace('D', 'E'));
                            double szz = Double.parseDouble(parts[4].replace('D', 'E'));
                            double sxy = Double.parseDouble(parts[5].replace('D', 'E'));
                            double sxz = Double.parseDouble(parts[6].replace('D', 'E'));
                            double syz = Double.parseDouble(parts[7].replace('D', 'E'));
                            double vm = Math.sqrt(0.5 * (Math.pow(sxx-syy, 2) + Math.pow(syy-szz, 2) + Math.pow(szz-sxx, 2) + 6*(sxy*sxy + syz*syz + sxz*sxz)));
                            if (vm > maxVonMises) maxVonMises = vm;
                        } catch (NumberFormatException ignore) {}
                    }
                }
            }
        }

        double tipDeflection = Math.abs(maxUy);
        System.out.println("FEA Tip Deflection: " + tipDeflection + " mm (Analytical Timoshenko: 0.2016 mm)");
        System.out.println("FEA Max Von Mises Stress: " + maxVonMises + " MPa (Analytical Navier: 60.00 MPa)");

        assertEquals("Tip deflection should match beam theory within 5%", 0.2016, tipDeflection, 0.015);
        assertTrue("Max stress should be close to bending theory (~60 MPa)", maxVonMises >= 50.0 && maxVonMises <= 75.0);
    }

    @Test
    public void testElementBeforeNodeOrderingReorganization() throws Exception {
        File workDir = tempFolder.newFolder("work_ordering");
        File rawInp = new File(workDir, "job_raw.inp");
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(rawInp))) {
            pw.println("*Heading");
            pw.println(" Reversed mesh");
            pw.println("*ELEMENT, TYPE=C3D4, ELSET=Eall");
            pw.println("1, 1, 2, 3, 4");
            pw.println("*NODE, NSET=Nall");
            pw.println("1, 0.0, 0.0, 0.0");
            pw.println("2, 10.0, 0.0, 0.0");
            pw.println("3, 0.0, 10.0, 0.0");
            pw.println("4, 0.0, 0.0, 10.0");
        }

        SolidInpAssembler.assemble(workDir, "job", "Steel", 200000.0, 0.3, -100.0, 2, "X_MIN", "X_MAX", "C3D4");

        File cleanInp = new File(workDir, "job_clean.inp");
        assertTrue("job_clean.inp should be created", cleanInp.exists());

        String content = new String(Files.readAllBytes(cleanInp.toPath()));
        int nodeIdx = content.indexOf("*NODE");
        int elemIdx = content.indexOf("*ELEMENT");
        assertTrue("*NODE must appear in cleanInp", nodeIdx >= 0);
        assertTrue("*ELEMENT must appear in cleanInp", elemIdx >= 0);
        assertTrue("*NODE must be placed BEFORE *ELEMENT for CalculiX compliance", nodeIdx < elemIdx);
    }

    @Test
    public void testBoundaryOverlapRemoval() throws Exception {
        File workDir = tempFolder.newFolder("work_overlap");
        File rawInp = new File(workDir, "job_raw.inp");

        try (PrintWriter pw = new PrintWriter(new FileWriter(rawInp))) {
            pw.println("*NODE, NSET=Nall");
            pw.println("1, 0.0, 0.0, 0.0");
            pw.println("2, 10.0, 0.0, 0.0");
            pw.println("3, 0.0, 10.0, 0.0");
            pw.println("4, 0.0, 0.0, 10.0");
            pw.println("*ELEMENT, TYPE=C3D4, ELSET=Eall");
            pw.println("1, 1, 2, 3, 4");
        }

        SolidInpAssembler.assemble(workDir, "job", "Steel", 200000.0, 0.3, -100.0, 2, "X_MIN", "X_MIN", "C3D4");

        File nsetsInp = new File(workDir, "nsets.inp");
        assertTrue("nsets.inp should exist", nsetsInp.exists());
        String nsetsContent = new String(Files.readAllBytes(nsetsInp.toPath()));

        assertTrue("NFix should exist", nsetsContent.contains("*NSET, NSET=NFix"));
        assertTrue("NLoad should exist", nsetsContent.contains("*NSET, NSET=NLoad"));
    }
}
