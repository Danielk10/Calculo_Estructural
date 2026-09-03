package com.diamon.civil.terminal.engine;

import com.diamon.civil.solids.engine.SampleSimulationCase;
import com.diamon.civil.solids.engine.SolidDisplacementFrdParser;
import com.diamon.civil.solids.engine.SolidInpAssembler;
import com.diamon.civil.structural.engine.CalculixExecutor;
import com.diamon.civil.structural.engine.StructuralBeamDatParser;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * End-to-end local validation test for all commands and workflows
 * documented in GUIA_TERMINAL_APP_PASO_A_PASO.md.
 *
 * Verifies that every command, shell utility, test-* pipeline,
 * and direct binary call works with 100% fidelity without false positives.
 */
public class TerminalGuideEndToEndTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File sandboxRoot;
    private File structDir;
    private File solidsDir;
    private TerminalCommandExecutor terminalExecutor;
    private CalculixExecutor calculixExecutor;

    @Before
    public void setUp() throws Exception {
        sandboxRoot = tempFolder.newFolder("terminal_sandbox");
        structDir = new File(sandboxRoot, "structural_analysis");
        solidsDir = new File(sandboxRoot, "3d_solid_analysis");
        assertTrue(structDir.mkdirs());
        assertTrue(solidsDir.mkdirs());

        terminalExecutor = new TerminalCommandExecutor(sandboxRoot, sandboxRoot);
        calculixExecutor = new CalculixExecutor(sandboxRoot, null, sandboxRoot);
    }

    @Test
    public void testLevel1_ShellCommands() throws Exception {
        // 1. pwd
        assertEquals("/", terminalExecutor.execute("pwd"));

        // 2. mkdir
        String mkdirRes = terminalExecutor.execute("mkdir proyecto_puente");
        assertTrue(mkdirRes.contains("created"));
        File projDir = new File(sandboxRoot, "proyecto_puente");
        assertTrue(projDir.exists() && projDir.isDirectory());

        // 3. cd
        String cdRes = terminalExecutor.execute("cd proyecto_puente");
        assertTrue(cdRes.contains("/proyecto_puente"));
        assertEquals("/proyecto_puente", terminalExecutor.execute("pwd"));

        // 4. touch
        String touchRes = terminalExecutor.execute("touch notas.txt");
        assertTrue(touchRes.contains("Created") || touchRes.contains("Updated"));
        File notas = new File(projDir, "notas.txt");
        assertTrue(notas.exists());

        // 5. write and cat
        Files.write(notas.toPath(), "Notas de calculo estructural\n".getBytes(StandardCharsets.UTF_8));
        String catRes = terminalExecutor.execute("cat notas.txt");
        assertTrue(catRes.contains("Notas de calculo estructural"));

        // 6. cp
        String cpRes = terminalExecutor.execute("cp notas.txt notas_backup.txt");
        assertTrue(cpRes.contains("Copied"));
        File backup = new File(projDir, "notas_backup.txt");
        assertTrue(backup.exists());

        // 7. ls
        String lsRes = terminalExecutor.execute("ls");
        assertTrue(lsRes.contains("notas.txt"));
        assertTrue(lsRes.contains("notas_backup.txt"));

        // 8. rm
        String rmRes = terminalExecutor.execute("rm notas_backup.txt");
        assertTrue(rmRes.contains("Deleted"));
        assertFalse(backup.exists());

        // 9. cd .. and rm -rf
        terminalExecutor.execute("cd ..");
        assertEquals("/", terminalExecutor.execute("pwd"));
        String rmRfRes = terminalExecutor.execute("rm -rf proyecto_puente");
        assertTrue(rmRfRes.contains("Deleted"));
        assertFalse(projDir.exists());

        // 10. help
        String helpRes = terminalExecutor.execute("help");
        assertNotNull(helpRes);
        assertTrue(helpRes.contains("Special Test & Pipeline Commands"));
        assertTrue(helpRes.contains("Standard Shell Commands"));
    }

    @Test
    public void testLevel2_CalculixHookeAndPoisson() throws Exception {
        // Prepare test_calculix.inp in sandbox
        File inpFile = new File(sandboxRoot, "test_calculix.inp");
        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            pw.println("1, 0., 0., 0.");
            pw.println("2, 1., 0., 0.");
            pw.println("3, 1., 1., 0.");
            pw.println("4, 0., 1., 0.");
            pw.println("5, 0., 0., 1.");
            pw.println("6, 1., 0., 1.");
            pw.println("7, 1., 1., 1.");
            pw.println("8, 0., 1., 1.");
            pw.println("*ELEMENT, TYPE=C3D8, ELSET=EALL");
            pw.println("1, 1, 2, 3, 4, 5, 6, 7, 8");
            pw.println("*MATERIAL, NAME=STEEL");
            pw.println("*ELASTIC");
            pw.println("210000., .3");
            pw.println("*SOLID SECTION, ELSET=EALL, MATERIAL=STEEL");
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*BOUNDARY");
            pw.println("1, 1, 3, 0.");
            pw.println("2, 2, 3, 0.");
            pw.println("3, 3, 3, 0.");
            pw.println("4, 1, 1, 0.");
            pw.println("4, 3, 3, 0.");
            pw.println("5, 1, 2, 0.");
            pw.println("8, 1, 1, 0.");
            pw.println("*CLOAD");
            pw.println("5, 3, 100.");
            pw.println("6, 3, 100.");
            pw.println("7, 3, 100.");
            pw.println("8, 3, 100.");
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*END STEP");
        }

        // Execute ccx
        calculixExecutor.setWorkDir(sandboxRoot);
        String out = calculixExecutor.executeCalculix("test_calculix", 1);
        assertTrue("CalculiX must exit with code 0", out.contains("Exit Code: 0"));

        File datFile = new File(sandboxRoot, "test_calculix.dat");
        assertTrue("Output .dat must exist", datFile.exists());

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult parseRes = parser.parse(datFile);

        // Theoretical delta_z = +0.00190476 mm
        // Transverse Poisson delta_x = delta_y = -0.00057143 mm
        // Vector magnitude ||delta|| = sqrt(delta_x^2 + delta_y^2 + delta_z^2) = 0.0020691 mm
        assertEquals("Analyzed nodes must be 8", 8, parseRes.displacements.size());
        assertEquals("Resultant vector displacement magnitude must be ~0.002069 mm", 0.002069, parseRes.maxDisp, 1e-4);

        // Check axial component uz and Poisson contraction on loaded node
        StructuralBeamDatParser.NodeDisplacement node6 = parseRes.displacements.stream()
                .filter(n -> n.nodeId == 6).findFirst().orElse(null);
        assertNotNull("Node 6 displacement must be present", node6);
        assertEquals("Axial elongation uz must be +0.001905 mm", 0.001905, node6.uz, 1e-4);
        assertEquals("Poisson contraction ux must be -0.000571 mm", -0.000571, node6.ux, 1e-4);
    }

    @Test
    public void testLevel2_PortalFrameAnalysis() throws Exception {
        File inpFile = new File(sandboxRoot, "test_portico.inp");
        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            pw.println("1, 0.0, 0.0, 0.0");
            pw.println("2, 5.0, 0.0, 0.0");
            pw.println("3, 0.0, 4.0, 0.0");
            pw.println("4, 5.0, 4.0, 0.0");
            pw.println("*ELEMENT, TYPE=B31, ELSET=EALL");
            pw.println("1, 1, 3");
            pw.println("2, 2, 4");
            pw.println("3, 3, 4");
            pw.println("*MATERIAL, NAME=STEEL");
            pw.println("*ELASTIC");
            pw.println("210000, 0.3");
            pw.println("*DENSITY");
            pw.println("7850");
            pw.println("*BEAM SECTION, ELSET=EALL, MATERIAL=STEEL, SECTION=RECT");
            pw.println("200, 200");
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*BOUNDARY");
            pw.println("1, 1, 6, 0.0");
            pw.println("2, 1, 6, 0.0");
            pw.println("*CLOAD");
            pw.println("3, 1, 10000.0");
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*EL FILE, SECTION FORCES, OUTPUT=2D");
            pw.println("S");
            pw.println("*END STEP");
        }

        calculixExecutor.setWorkDir(sandboxRoot);
        String out = calculixExecutor.executeCalculix("test_portico");
        assertTrue("ccx portal frame must exit 0", out.contains("Exit Code: 0"));

        File datFile = new File(sandboxRoot, "test_portico.dat");
        assertTrue(datFile.exists());

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult parseRes = parser.parse(datFile);
        assertFalse("Displacements must be parsed", parseRes.displacements.isEmpty());
        assertTrue("Max displacement must be greater than 0", parseRes.maxDisp > 0);
    }

    @Test
    public void testLevel2_GmshBooleanDifference() throws Exception {
        File geoFile = new File(sandboxRoot, "boolean_test.geo");
        String script = "SetFactory(\"OpenCASCADE\");\n" +
                "Cylinder(1) = {0, 0, 0, 0, 0, 5, 2};\n" +
                "Sphere(2) = {0, 0, 2.5, 1.5};\n" +
                "BooleanDifference(3) = { Volume{1}; Delete; } { Volume{2}; Delete; };\n" +
                "Mesh.MeshSizeMax = 1.0;\n";
        Files.write(geoFile.toPath(), script.getBytes(StandardCharsets.UTF_8));

        calculixExecutor.setWorkDir(sandboxRoot);
        String gmshOut = calculixExecutor.executeBinary("gmsh", "boolean_test.geo", "-3", "-format", "inp", "-o", "hollow_cylinder.inp");
        assertTrue("Gmsh must exit with 0", gmshOut.contains("Exit Code: 0"));

        File inpFile = new File(sandboxRoot, "hollow_cylinder.inp");
        assertTrue("hollow_cylinder.inp must exist", inpFile.exists());
        assertTrue("Mesh must have content", inpFile.length() > 500);
    }

    @Test
    public void testLevel2_OpenCascadeDrawHeadless() throws Exception {
        String drawScript = "pload ALL\n" +
                "box b 10 10 10\n" +
                "writebrep b test_box.brep\n" +
                "puts \"BOX CREATED SUCCESSFULLY\"\n" +
                "exit\n";

        calculixExecutor.setWorkDir(sandboxRoot);
        String drawOut = calculixExecutor.executeBinaryWithInput("DRAWEXE", drawScript, "-b");
        assertTrue("DRAWEXE must execute cleanly", drawOut.contains("Exit Code: 0") || drawOut.contains("BOX CREATED SUCCESSFULLY"));

        File brepFile = new File(sandboxRoot, "test_box.brep");
        assertTrue("test_box.brep must exist", brepFile.exists());
        assertTrue("BRep file must have geometry content", brepFile.length() > 50);
    }

    @Test
    public void testLevel4_PracticalCase1_StructuralAnalysisModuleInteroperability() throws Exception {
        // Simulate a completed calculation in /structural_analysis
        File jobInp = new File(structDir, "job_structural.inp");
        Files.write(jobInp.toPath(), ("*NODE, NSET=NALL\n" +
                "1, 0.0, 0.0, 0.0\n" +
                "2, 4.0, 0.0, 0.0\n" +
                "*ELEMENT, TYPE=B31, ELSET=EALL\n" +
                "1, 1, 2\n" +
                "*MATERIAL, NAME=STEEL\n" +
                "*ELASTIC\n" +
                "210000000000, 0.3\n" +
                "*BEAM SECTION, ELSET=EALL, MATERIAL=STEEL, SECTION=RECT\n" +
                "0.2, 0.3\n" +
                "*STEP\n" +
                "*STATIC\n" +
                "*BOUNDARY\n" +
                "1, 1, 6, 0.0\n" +
                "*CLOAD\n" +
                "2, 2, -10000.0\n" +
                "*NODE PRINT, NSET=NALL\n" +
                "U\n" +
                "*END STEP\n").getBytes(StandardCharsets.UTF_8));

        // 1. cd into /structural_analysis
        String cdRes = terminalExecutor.execute("cd /structural_analysis");
        assertTrue(cdRes.contains("/structural_analysis"));

        // 2. ls
        String lsRes = terminalExecutor.execute("ls");
        assertTrue(lsRes.contains("job_structural.inp"));

        // 3. cat
        String catRes = terminalExecutor.execute("cat job_structural.inp");
        assertTrue(catRes.contains("*NODE"));

        // 4. ccx job_structural
        calculixExecutor.setWorkDir(terminalExecutor.getCurrentDir());
        String ccxRes = calculixExecutor.executeCalculix("job_structural");
        assertTrue(ccxRes.contains("Exit Code: 0"));

        // 5. cat job_structural.dat
        File datFile = new File(structDir, "job_structural.dat");
        assertTrue(datFile.exists());
        String catDat = terminalExecutor.execute("cat job_structural.dat");
        assertTrue(catDat.contains("displacements"));
    }

    @Test
    public void testLevel2_CadSolvePipeline() throws Exception {
        // Step 1: Draw box with DRAWEXE
        String drawScript = "pload ALL\n" +
                "box b 2 2 10\n" +
                "writebrep b bar.brep\n" +
                "exit\n";
        calculixExecutor.setWorkDir(sandboxRoot);
        String drawOut = calculixExecutor.executeBinaryWithInput("DRAWEXE", drawScript, "-b");
        assertTrue(drawOut.contains("Exit Code: 0"));
        File brepFile = new File(sandboxRoot, "bar.brep");
        assertTrue(brepFile.exists());

        // Step 2: Mesh with Gmsh
        File geoFile = new File(sandboxRoot, "bar.geo");
        String geoScript = "SetFactory(\"OpenCASCADE\");\n" +
                "Merge \"bar.brep\";\n" +
                "Mesh.MeshSizeMax = 1.0;\n";
        Files.write(geoFile.toPath(), geoScript.getBytes(StandardCharsets.UTF_8));
        String gmshOut = calculixExecutor.executeBinary("gmsh", "bar.geo", "-3", "-format", "inp", "-o", "bar_raw.inp");
        assertTrue(gmshOut.contains("Exit Code: 0"));
        File rawInp = new File(sandboxRoot, "bar_raw.inp");
        assertTrue(rawInp.exists());

        // Step 3: Assemble with SolidInpAssembler
        SolidInpAssembler.assemble(sandboxRoot, "bar", "Steel", 210000.0, 0.3, -500.0, "nonexistent_fixed", "nonexistent_load");
        File barInp = new File(sandboxRoot, "bar.inp");
        assertTrue(barInp.exists());

        // Step 4: Solve with ccx
        String ccxOut = calculixExecutor.executeBinary("ccx", "-i", "bar");
        assertTrue(ccxOut.contains("Exit Code: 0"));

        // Step 5: Parse FRD results
        File frdFile = new File(sandboxRoot, "bar.frd");
        assertTrue(frdFile.exists());
        String frdSummary = SolidDisplacementFrdParser.parseAndSummarize(frdFile);
        assertNotNull(frdSummary);
        assertTrue(frdSummary.contains("Nodes with displacement") && frdSummary.contains("maximum displacement"));
    }

    @Test
    public void testLevel2_RunSimTest() throws Exception {
        File geoFile = SampleSimulationCase.createCantileverGeo(sandboxRoot);
        assertTrue(geoFile.exists());

        calculixExecutor.setWorkDir(sandboxRoot);
        String gmshOut = calculixExecutor.executeBinary("gmsh", "cantilever.geo", "-3", "-format", "inp", "-o", "cantilever_raw.inp");
        assertTrue(gmshOut.contains("Exit Code: 0"));

        SolidInpAssembler.assemble(sandboxRoot, "cantilever", "Steel", 210000.0, 0.3, -100.0, "Fixed", "Loaded");
        File inpFile = new File(sandboxRoot, "cantilever.inp");
        assertTrue(inpFile.exists());

        String ccxOut = calculixExecutor.executeBinary("ccx", "-i", "cantilever");
        assertTrue(ccxOut.contains("Exit Code: 0"));

        File frdFile = new File(sandboxRoot, "cantilever.frd");
        assertTrue(frdFile.exists());
        String summary = SolidDisplacementFrdParser.parseAndSummarize(frdFile);
        assertNotNull(summary);
        assertTrue(summary.contains("Nodes with displacement") && summary.contains("maximum displacement"));
    }

    @Test
    public void testLevel4_PracticalCase2_ExternalInpSolve() throws Exception {
        // Simulate importing cercha_especial.inp into the active terminal folder
        File inpFile = new File(sandboxRoot, "cercha_especial.inp");
        Files.write(inpFile.toPath(), ("*NODE, NSET=NALL\n" +
                "1, 0.0, 0.0, 0.0\n" +
                "2, 4.0, 0.0, 0.0\n" +
                "3, 2.0, 3.0, 0.0\n" +
                "*ELEMENT, TYPE=B31, ELSET=EALL\n" +
                "1, 1, 2\n" +
                "2, 1, 3\n" +
                "3, 2, 3\n" +
                "*BEAM SECTION, ELSET=EALL, MATERIAL=STEEL, SECTION=RECT\n" +
                "0.1, 0.1\n" +
                "*MATERIAL, NAME=STEEL\n" +
                "*ELASTIC\n" +
                "210000000000, 0.3\n" +
                "*STEP\n" +
                "*STATIC\n" +
                "*BOUNDARY\n" +
                "1, 1, 3, 0.0\n" +
                "2, 2, 2, 0.0\n" +
                "*CLOAD\n" +
                "3, 2, -50000.0\n" +
                "*NODE PRINT, NSET=NALL\n" +
                "U, RF\n" +
                "*END STEP\n").getBytes(StandardCharsets.UTF_8));

        // 1. Verify file exists
        String lsRes = terminalExecutor.execute("ls");
        assertTrue(lsRes.contains("cercha_especial.inp"));

        // 2. Solve with ccx cercha_especial
        calculixExecutor.setWorkDir(sandboxRoot);
        String ccxOut = calculixExecutor.executeCalculix("cercha_especial");
        assertTrue(ccxOut.contains("Exit Code: 0"));

        // 3. Check and cat results
        File datFile = new File(sandboxRoot, "cercha_especial.dat");
        assertTrue(datFile.exists());
        String catDat = terminalExecutor.execute("cat cercha_especial.dat");
        assertTrue(catDat.contains("displacements") || catDat.contains("forces"));
    }

    @Test
    public void testLevel4_PracticalCase3_ProjectCreationAndCleanup() throws Exception {
        // 1. mkdir estudio_vigas
        String mkdirRes = terminalExecutor.execute("mkdir estudio_vigas");
        assertTrue(mkdirRes.contains("created"));

        // 2. cd estudio_vigas
        terminalExecutor.execute("cd estudio_vigas");
        assertEquals("/estudio_vigas", terminalExecutor.execute("pwd"));

        // 3. Create sample viga_prueba.inp
        File projDir = new File(sandboxRoot, "estudio_vigas");
        File inpFile = new File(projDir, "viga_prueba.inp");
        Files.write(inpFile.toPath(), ("*NODE, NSET=NALL\n" +
                "1, 0.0, 0.0, 0.0\n" +
                "2, 6.0, 0.0, 0.0\n" +
                "*ELEMENT, TYPE=B31, ELSET=EALL\n" +
                "1, 1, 2\n" +
                "*BEAM SECTION, ELSET=EALL, MATERIAL=STEEL, SECTION=RECT\n" +
                "0.2, 0.3\n" +
                "*MATERIAL, NAME=STEEL\n" +
                "*ELASTIC\n" +
                "210000000000, 0.3\n" +
                "*STEP\n" +
                "*STATIC\n" +
                "*BOUNDARY\n" +
                "1, 1, 3, 0.0\n" +
                "2, 2, 2, 0.0\n" +
                "*CLOAD\n" +
                "2, 2, -20000.0\n" +
                "*NODE PRINT, NSET=NALL\n" +
                "U\n" +
                "*END STEP\n").getBytes(StandardCharsets.UTF_8));

        // 4. Solve
        calculixExecutor.setWorkDir(terminalExecutor.getCurrentDir());
        String ccxOut = calculixExecutor.executeCalculix("viga_prueba");
        assertTrue(ccxOut.contains("Exit Code: 0"));

        // 5. Check .sta file exists and can be read
        File staFile = new File(projDir, "viga_prueba.sta");
        assertTrue(staFile.exists());
        String catSta = terminalExecutor.execute("cat viga_prueba.sta");
        assertNotNull(catSta);

        // 6. Delete .sta
        String rmRes = terminalExecutor.execute("rm viga_prueba.sta");
        assertTrue(rmRes.contains("Deleted"));
        assertFalse(staFile.exists());

        // 7. cd back to root
        terminalExecutor.execute("cd /");
        assertEquals("/", terminalExecutor.execute("pwd"));
    }
}
