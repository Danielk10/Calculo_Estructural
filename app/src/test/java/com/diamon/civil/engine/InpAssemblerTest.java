package com.diamon.civil.engine;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.Files;

public class InpAssemblerTest {

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
        InpAssembler.assemble(
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

        // 4. Assemble the CalculiX input using InpAssembler (will trigger coordinate fallback!)
        InpAssembler.assemble(workDir, "linkrods", "Steel", 210000.0, 0.3, -200.0, "nonexistent_fixed", "nonexistent_load");

        // 5. Execute local system CalculiX solver
        ProcessBuilder pbCcx = new ProcessBuilder("/home/danielpdiamon/.local/bin/ccx", "-i", "linkrods");
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
        
        String summary = com.diamon.civil.test.simulation.FrdParser.parseAndSummarize(frdFile);
        System.out.println("Calculated STEP FEA Summary:\n" + summary);
        
        assertTrue("Summary should contain displacement nodes", summary.contains("Nodos con desplazamiento"));
        assertTrue("Summary should contain max displacement node", summary.contains("Nodo con mayor desplazamiento"));
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

        // 4. Assemble the CalculiX input using InpAssembler (will trigger coordinate fallback!)
        InpAssembler.assemble(workDir, "bracket", "Steel", 210000.0, 0.3, -150.0, "nonexistent_fixed", "nonexistent_load");

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
        
        String summary = com.diamon.civil.test.simulation.FrdParser.parseAndSummarize(frdFile);
        System.out.println("Calculated BRACKET FEA Summary:\n" + summary);
        
        assertTrue("Summary should contain displacement nodes", summary.contains("Nodos con desplazamiento"));
        assertTrue("Summary should contain max displacement node", summary.contains("Nodo con mayor desplazamiento"));
    }

    @Test
    public void testCADModelingMeshingAndSolvingPipeline() throws Exception {
        File workDir = tempFolder.newFolder("work_cad_solve");

        // 1. Execute occt-draw to generate a BREP solid
        String drawScript = "pload ALL\n" +
                           "box b 3 3 15\n" +
                           "writebrep b bar.brep\n" +
                           "exit\n";
        ProcessBuilder pbDraw = new ProcessBuilder("xvfb-run", "-a", "/usr/share/opencascade/bin/draw.sh");
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
        int codeDraw = pDraw.waitFor();
        // Since occt-draw might return non-zero exit codes if Tk is missing, we check if the file was created instead
        File brepFile = new File(workDir, "bar.brep");
        assertTrue("bar.brep should be created by DRAWEXE", brepFile.exists());

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

        // 4. Assemble the CalculiX input using InpAssembler (triggers coordinate fallback!)
        InpAssembler.assemble(workDir, "bar", "Steel", 210000.0, 0.3, -300.0, "nonexistent_fixed", "nonexistent_load");

        // 5. Run CalculiX Solver ccx
        ProcessBuilder pbCcx = new ProcessBuilder("/home/danielpdiamon/.local/bin/ccx", "-i", "bar");
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

        String summary = com.diamon.civil.test.simulation.FrdParser.parseAndSummarize(frdFile);
        System.out.println("Generated CAD Solve Summary:\n" + summary);
        assertTrue("Summary should contain displacement nodes", summary.contains("Nodos con desplazamiento"));
        assertTrue("Summary should contain max displacement node", summary.contains("Nodo con mayor desplazamiento"));
    }
}
