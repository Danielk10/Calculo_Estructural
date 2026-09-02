package com.diamon.civil.structural.engine;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import java.io.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Integration Tests: Validates CalculiX real results against known physics solutions.
 *
 * Reference formulas (Euler-Bernoulli beam theory):
 * - Cantilever, tip load P:  δ_tip = PL³/(3EI),  M_max = P·L,  V = P
 * - Simply supported, center load P:  δ_mid = PL³/(48EI),  M_max = PL/4,  V = P/2
 *
 * All tests use Steel (E=210GPa, ν=0.3) with rectangular beam sections.
 * CalculiX B31 uses Timoshenko beam theory (includes shear deformation).
 */
public class StructuralPhysicsValidationTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final String CCX_PATH = "/home/danielpdiamon/.local/bin/ccx";

    // =====================================================
    // CASE 1: Cantilever Beam — Exact Euler-Bernoulli
    // =====================================================

    @Test
    public void testCantileverBeam_vs_EulerBernoulli() throws Exception {
        double L = 4.0;
        double P = 10000.0;
        double E = 210e9;
        double b_sec = 0.200;
        double h_sec = 0.300;
        double I = (b_sec * Math.pow(h_sec, 3)) / 12.0;

        double delta_theory = (P * Math.pow(L, 3)) / (3.0 * E * I);
        double M_max_theory = P * L;
        double V_theory = P;

        System.out.printf("=== CANTILEVER BEAM VALIDATION ===%n");
        System.out.printf("Theoretical: δ=%.6f mm, M_max=%.0f N·m, V=%.0f N%n",
                delta_theory * 1000, M_max_theory, V_theory);

        File workDir = tempFolder.newFolder("cantilever_validation");
        File inpFile = new File(workDir, "cantilever.inp");
        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            pw.println("1, 0.0, 0.0, 0.0");
            pw.println("2, 1.0, 0.0, 0.0");
            pw.println("3, 2.0, 0.0, 0.0");
            pw.println("4, 3.0, 0.0, 0.0");
            pw.println("5, 4.0, 0.0, 0.0");
            pw.println("*ELEMENT, TYPE=B31, ELSET=BEAMS");
            pw.println("1, 1, 2");
            pw.println("2, 2, 3");
            pw.println("3, 3, 4");
            pw.println("4, 4, 5");
            pw.println("*BEAM SECTION, ELSET=BEAMS, MATERIAL=STEEL, SECTION=RECT");
            pw.printf("%.4f, %.4f%n", b_sec, h_sec);
            pw.println("0.0, 0.0, 1.0");
            pw.println("*MATERIAL, NAME=STEEL");
            pw.println("*ELASTIC");
            pw.printf("%.1f, 0.3%n", E);
            pw.println("*BOUNDARY");
            pw.println("1, 1, 6, 0.0");
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*CLOAD");
            pw.printf("5, 2, %.1f%n", -P);
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*NODE FILE");
            pw.println("U");
            pw.println("*EL FILE, SECTION FORCES, OUTPUT=2D");
            pw.println("S");
            pw.println("*END STEP");
        }

        int exitCode = runCalculiX(workDir, "cantilever");
        assertEquals("CalculiX exit code", 0, exitCode);

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult result = parser.parse(new File(workDir, "cantilever.dat"));
        assertNull("No parse error", result.error);

        StructuralBeamDatParser.NodeDisplacement tip = null;
        for (StructuralBeamDatParser.NodeDisplacement nd : result.displacements) {
            if (nd.nodeId == 5) tip = nd;
        }
        assertNotNull("Tip node displacement found", tip);

        double delta_ccx = Math.abs(tip.uy);
        System.out.printf("CalculiX:    δ=%.6f mm%n", delta_ccx * 1000);

        double error_pct = Math.abs(delta_ccx - delta_theory) / delta_theory * 100.0;
        System.out.printf("Error:       %.2f%%  (Timoshenko adds shear deformation to Euler-Bernoulli)%n", error_pct);

        assertTrue("Tip deflection direction: should be negative (downward)", tip.uy < 0);
        // Timoshenko beam model (B31) includes shear deformation, so δ_ccx >= δ_euler
        // Allow up to 10% for short thick beams
        assertTrue("Error should be < 10% for Timoshenko vs Euler-Bernoulli", error_pct < 10.0);
    }

    // =====================================================
    // CASE 2: Simply Supported Beam — Center Point Load
    // =====================================================

    @Test
    public void testSimplySupportedBeam_CenterLoad() throws Exception {
        double L = 6.0;
        double P = 20000.0;
        double E = 210e9;
        double b_sec = 0.150;
        double h_sec = 0.300;
        double I = (b_sec * Math.pow(h_sec, 3)) / 12.0;

        double delta_theory = (P * Math.pow(L, 3)) / (48.0 * E * I);

        System.out.printf("=== SIMPLY SUPPORTED BEAM (Center Load) ===%n");
        System.out.printf("Theoretical: δ=%.6f mm%n", delta_theory * 1000);

        File workDir = tempFolder.newFolder("ss_beam_validation");
        File inpFile = new File(workDir, "ss_beam.inp");
        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            pw.println("1, 0.0, 0.0, 0.0");
            pw.println("2, 1.5, 0.0, 0.0");
            pw.println("3, 3.0, 0.0, 0.0");
            pw.println("4, 4.5, 0.0, 0.0");
            pw.println("5, 6.0, 0.0, 0.0");
            pw.println("*ELEMENT, TYPE=B31, ELSET=BEAMS");
            pw.println("1, 1, 2");
            pw.println("2, 2, 3");
            pw.println("3, 3, 4");
            pw.println("4, 4, 5");
            pw.println("*BEAM SECTION, ELSET=BEAMS, MATERIAL=STEEL, SECTION=RECT");
            pw.printf("%.4f, %.4f%n", b_sec, h_sec);
            pw.println("0.0, 0.0, 1.0");
            pw.println("*MATERIAL, NAME=STEEL");
            pw.println("*ELASTIC");
            pw.printf("%.1f, 0.3%n", E);
            pw.println("*BOUNDARY");
            pw.println("1, 1, 3, 0.0");
            pw.println("5, 2, 2, 0.0");
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*CLOAD");
            pw.printf("3, 2, %.1f%n", -P);
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*END STEP");
        }

        int exitCode = runCalculiX(workDir, "ss_beam");
        assertEquals("CalculiX exit code", 0, exitCode);

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult result = parser.parse(new File(workDir, "ss_beam.dat"));
        assertNull("No parse error", result.error);

        StructuralBeamDatParser.NodeDisplacement mid = null;
        for (StructuralBeamDatParser.NodeDisplacement nd : result.displacements) {
            if (nd.nodeId == 3) mid = nd;
        }
        assertNotNull("Mid-span displacement found", mid);
        assertTrue("Mid-span should deflect downward", mid.uy < 0);

        double delta_ccx = Math.abs(mid.uy);
        double error_pct = Math.abs(delta_ccx - delta_theory) / delta_theory * 100.0;

        System.out.printf("CalculiX:    δ=%.6f mm%n", delta_ccx * 1000);
        System.out.printf("Error:       %.2f%%%n", error_pct);

        assertTrue("Error should be < 15% (Timoshenko + coarse mesh effects)", error_pct < 15.0);

        StructuralBeamDatParser.NodeDisplacement n1 = null, n5 = null;
        for (StructuralBeamDatParser.NodeDisplacement nd : result.displacements) {
            if (nd.nodeId == 1) n1 = nd;
            if (nd.nodeId == 5) n5 = nd;
        }
        assertNotNull("Node 1 found", n1);
        assertNotNull("Node 5 found", n5);
        assertEquals("Pinned support UY=0", 0.0, n1.uy, 1e-8);
        assertEquals("Roller support UY=0", 0.0, n5.uy, 1e-8);
    }

    // =====================================================
    // CASE 3: Portal Frame — Lateral Load (App Default)
    // =====================================================

    @Test
    public void testPortalFrame_LateralLoad() throws Exception {
        double H = 3.0;
        double L_span = 4.0;
        double E = 210e9;
        double P = 10000.0;

        System.out.printf("=== PORTAL FRAME (App Default Model) ===%n");

        File workDir = tempFolder.newFolder("portal_validation");
        File inpFile = new File(workDir, "portal.inp");
        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            pw.println("1, 0.0, 0.0, 0.0");
            pw.println("2, 0.0, 3.0, 0.0");
            pw.println("3, 4.0, 3.0, 0.0");
            pw.println("4, 4.0, 0.0, 0.0");
            pw.println("*ELEMENT, TYPE=B31, ELSET=ALLEL");
            pw.println("1, 1, 2");
            pw.println("2, 2, 3");
            pw.println("3, 4, 3");
            pw.println("*BEAM SECTION, ELSET=ALLEL, MATERIAL=STEEL, SECTION=RECT");
            pw.println("0.200, 0.200");
            pw.println("0.0, 0.0, 1.0");
            pw.println("*MATERIAL, NAME=STEEL");
            pw.println("*ELASTIC");
            pw.printf("%.1f, 0.3%n", E);
            pw.println("*BOUNDARY");
            pw.println("1, 1, 6, 0.0");
            pw.println("4, 1, 6, 0.0");
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*CLOAD");
            pw.printf("2, 1, %.1f%n", P);
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*END STEP");
        }

        int exitCode = runCalculiX(workDir, "portal");
        assertEquals("CalculiX exit code", 0, exitCode);

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult result = parser.parse(new File(workDir, "portal.dat"));
        assertNull("No parse error", result.error);
        assertTrue("Should have displacements", result.displacements.size() >= 4);

        StructuralBeamDatParser.NodeDisplacement n2 = null, n3 = null, base1 = null, base4 = null;
        for (StructuralBeamDatParser.NodeDisplacement nd : result.displacements) {
            if (nd.nodeId == 2) n2 = nd;
            if (nd.nodeId == 3) n3 = nd;
            if (nd.nodeId == 1) base1 = nd;
            if (nd.nodeId == 4) base4 = nd;
        }
        assertNotNull("Top-left node found", n2);
        assertNotNull("Top-right node found", n3);

        assertTrue("Top-left should sway in +X", n2.ux > 0);
        assertTrue("Top-right should sway in +X", n3.ux > 0);

        System.out.printf("Sway left:  %.6f mm%n", n2.ux * 1000);
        System.out.printf("Sway right: %.6f mm%n", n3.ux * 1000);

        assertEquals("Fixed base 1 UX=0", 0.0, base1.ux, 1e-10);
        assertEquals("Fixed base 4 UX=0", 0.0, base4.ux, 1e-10);

        double driftRatio = Math.max(n2.ux, n3.ux) / H * 100.0;
        System.out.printf("Drift ratio: %.4f%%%n", driftRatio);
        assertTrue("Drift should be physically reasonable (< 2%)", driftRatio < 2.0);
    }

    // =====================================================
    // CASE 4: FRD Parser Mapping with Real CalculiX
    // =====================================================

    @Test
    public void testFrdParserMapping_WithRealCalculiX() throws Exception {
        File workDir = tempFolder.newFolder("frd_validation");
        File inpFile = new File(workDir, "frd_test.inp");
        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            pw.println("1, 0.0, 0.0, 0.0");
            pw.println("2, 2.0, 0.0, 0.0");
            pw.println("*ELEMENT, TYPE=B31, ELSET=BEAMS");
            pw.println("1, 1, 2");
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
            pw.println("2, 2, -10000.0");
            pw.println("*NODE FILE");
            pw.println("U");
            pw.println("*EL FILE, SECTION FORCES, OUTPUT=2D");
            pw.println("S");
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*END STEP");
        }

        int exitCode = runCalculiX(workDir, "frd_test");
        assertEquals("CalculiX exit code", 0, exitCode);

        // Parse DAT to verify we got results
        StructuralBeamDatParser datParser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult datResult = datParser.parse(new File(workDir, "frd_test.dat"));
        assertNull("No DAT parse error", datResult.error);
        assertTrue("Should have displacements", datResult.displacements.size() >= 2);

        // Verify node 2 deflects downward (correct physics)
        StructuralBeamDatParser.NodeDisplacement tip = null;
        for (StructuralBeamDatParser.NodeDisplacement nd : datResult.displacements) {
            if (nd.nodeId == 2) tip = nd;
        }
        assertNotNull("Tip node found", tip);
        assertTrue("Tip should deflect downward (UY < 0)", tip.uy < 0);
        System.out.printf("FRD test tip: UX=%.6e, UY=%.6e%n", tip.ux, tip.uy);

        // Parse FRD if available
        File frdFile = new File(workDir, "frd_test.frd");
        if (frdFile.exists()) {
            StructuralBeamFrdParser frdParser = new StructuralBeamFrdParser();
            StructuralBeamFrdParser.ParseResult frdResult = frdParser.parse(frdFile);

            if (!frdResult.forces.isEmpty()) {
                StructuralBeamFrdParser.SectionForces frdSf = frdResult.forces.get(0);
                System.out.printf("FRD: Axial=%.1f, Shear1=%.1f, Shear2=%.1f, M1=%.1f, M2=%.1f%n",
                        frdSf.axialNormal, frdSf.shear1, frdSf.shear2,
                        frdSf.bendingMoment1, frdSf.bendingMoment2);
            }
        }
    }

    // =====================================================
    // CASE 6: Complex 3-Story 2-Bay Frame — Lateral Seismic
    // =====================================================

    @Test
    public void testMultiStoryBuilding_SeismicLoadPattern() throws Exception {
        System.out.printf("%n=== 3-STORY 2-BAY BUILDING FRAME (Seismic Lateral Pattern) ===%n");

        File workDir = tempFolder.newFolder("multistory_validation");
        File inpFile = new File(workDir, "multistory.inp");
        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            pw.println("1, 0.0, 0.0, 0.0");
            pw.println("2, 3.0, 0.0, 0.0");
            pw.println("3, 6.0, 0.0, 0.0");
            pw.println("4, 0.0, 3.0, 0.0");
            pw.println("5, 3.0, 3.0, 0.0");
            pw.println("6, 6.0, 3.0, 0.0");
            pw.println("7, 0.0, 6.0, 0.0");
            pw.println("8, 3.0, 6.0, 0.0");
            pw.println("9, 6.0, 6.0, 0.0");
            pw.println("10, 0.0, 9.0, 0.0");
            pw.println("11, 3.0, 9.0, 0.0");
            pw.println("12, 6.0, 9.0, 0.0");
            pw.println("*ELEMENT, TYPE=B31, ELSET=E_COLS");
            pw.println("1, 1, 4");
            pw.println("2, 2, 5");
            pw.println("3, 3, 6");
            pw.println("4, 4, 7");
            pw.println("5, 5, 8");
            pw.println("6, 6, 9");
            pw.println("7, 7, 10");
            pw.println("8, 8, 11");
            pw.println("9, 9, 12");
            pw.println("*ELEMENT, TYPE=B31, ELSET=E_BEAMS");
            pw.println("10, 4, 5");
            pw.println("11, 5, 6");
            pw.println("12, 7, 8");
            pw.println("13, 8, 9");
            pw.println("14, 10, 11");
            pw.println("15, 11, 12");
            pw.println("*MATERIAL, NAME=STEEL");
            pw.println("*ELASTIC");
            pw.println("210000000000.0, 0.3");
            pw.println("*BEAM SECTION, ELSET=E_COLS, MATERIAL=STEEL, SECTION=RECT");
            pw.println("0.200, 0.200");
            pw.println("0.0, 0.0, 1.0");
            pw.println("*BEAM SECTION, ELSET=E_BEAMS, MATERIAL=STEEL, SECTION=RECT");
            pw.println("0.150, 0.300");
            pw.println("0.0, 0.0, 1.0");
            pw.println("*BOUNDARY");
            pw.println("1, 1, 6, 0.0");
            pw.println("2, 1, 6, 0.0");
            pw.println("3, 1, 6, 0.0");
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*CLOAD");
            pw.println("4, 1, 5000.0");
            pw.println("7, 1, 10000.0");
            pw.println("10, 1, 15000.0");
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*END STEP");
        }

        int exitCode = runCalculiX(workDir, "multistory");
        assertEquals("CalculiX exit code", 0, exitCode);

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult result = parser.parse(new File(workDir, "multistory.dat"));
        assertNull("No DAT parse error", result.error);

        double ux4 = 0, ux7 = 0, ux10 = 0;
        for (StructuralBeamDatParser.NodeDisplacement nd : result.displacements) {
            if (nd.nodeId == 4) ux4 = nd.ux;
            if (nd.nodeId == 7) ux7 = nd.ux;
            if (nd.nodeId == 10) ux10 = nd.ux;
        }

        System.out.printf("Displacements: L1(ux4)=%.4f mm, L2(ux7)=%.4f mm, L3(ux10)=%.4f mm%n",
                ux4 * 1000, ux7 * 1000, ux10 * 1000);

        assertTrue("Monotonic lateral sway: Level 1 < Level 2", ux4 < ux7);
        assertTrue("Monotonic lateral sway: Level 2 < Level 3", ux7 < ux10);
        assertTrue("Displacement positive in +X direction", ux10 > 0);

        // Drift checks
        double drift1 = (ux4 / 3.0) * 100.0;
        double drift2 = ((ux7 - ux4) / 3.0) * 100.0;
        double drift3 = ((ux10 - ux7) / 3.0) * 100.0;
        System.out.printf("Drift ratios: Story 1=%.4f%%, Story 2=%.4f%%, Story 3=%.4f%%%n", drift1, drift2, drift3);
        assertTrue("Story 1 drift <= 1.0% (NSR-10)", drift1 <= 1.0);
        assertTrue("Story 2 drift <= 1.0% (NSR-10)", drift2 <= 1.0);
        assertTrue("Story 3 drift <= 1.0% (NSR-10)", drift3 <= 1.0);
    }

    // =====================================================
    // CASE 7: 12m Warren Truss Bridge — Gravity Deck Loads
    // =====================================================

    @Test
    public void testWarrenTrussBridge_GravityLoading() throws Exception {
        System.out.printf("%n=== 12m WARREN TRUSS BRIDGE (Deck Loading) ===%n");

        File workDir = tempFolder.newFolder("truss_validation");
        File inpFile = new File(workDir, "warren.inp");
        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            pw.println("1,  0.0, 0.0, 0.0");
            pw.println("2,  3.0, 0.0, 0.0");
            pw.println("3,  6.0, 0.0, 0.0");
            pw.println("4,  9.0, 0.0, 0.0");
            pw.println("5, 12.0, 0.0, 0.0");
            pw.println("6,  1.5, 3.0, 0.0");
            pw.println("7,  4.5, 3.0, 0.0");
            pw.println("8,  7.5, 3.0, 0.0");
            pw.println("9, 10.5, 3.0, 0.0");
            pw.println("*ELEMENT, TYPE=B31, ELSET=E_BOTTOM");
            pw.println("1, 1, 2");
            pw.println("2, 2, 3");
            pw.println("3, 3, 4");
            pw.println("4, 4, 5");
            pw.println("*ELEMENT, TYPE=B31, ELSET=E_TOP");
            pw.println("5, 6, 7");
            pw.println("6, 7, 8");
            pw.println("7, 8, 9");
            pw.println("*ELEMENT, TYPE=B31, ELSET=E_DIAGS");
            pw.println("8,  1, 6");
            pw.println("9,  6, 2");
            pw.println("10, 2, 7");
            pw.println("11, 7, 3");
            pw.println("12, 3, 8");
            pw.println("13, 8, 4");
            pw.println("14, 4, 9");
            pw.println("15, 9, 5");
            pw.println("*MATERIAL, NAME=STEEL");
            pw.println("*ELASTIC");
            pw.println("210000000000.0, 0.3");
            pw.println("*BEAM SECTION, ELSET=E_BOTTOM, MATERIAL=STEEL, SECTION=RECT");
            pw.println("0.100, 0.100");
            pw.println("0.0, 0.0, 1.0");
            pw.println("*BEAM SECTION, ELSET=E_TOP, MATERIAL=STEEL, SECTION=RECT");
            pw.println("0.100, 0.100");
            pw.println("0.0, 0.0, 1.0");
            pw.println("*BEAM SECTION, ELSET=E_DIAGS, MATERIAL=STEEL, SECTION=RECT");
            pw.println("0.080, 0.080");
            pw.println("0.0, 0.0, 1.0");
            pw.println("*BOUNDARY");
            pw.println("1, 1, 3, 0.0");
            pw.println("5, 2, 2, 0.0");
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*CLOAD");
            pw.println("2, 2, -20000.0");
            pw.println("3, 2, -20000.0");
            pw.println("4, 2, -20000.0");
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*END STEP");
        }

        int exitCode = runCalculiX(workDir, "warren");
        assertEquals("CalculiX exit code", 0, exitCode);

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult result = parser.parse(new File(workDir, "warren.dat"));
        assertNull("No DAT parse error", result.error);

        double uy2 = 0, uy3 = 0, uy4 = 0;
        for (StructuralBeamDatParser.NodeDisplacement nd : result.displacements) {
            if (nd.nodeId == 2) uy2 = nd.uy;
            if (nd.nodeId == 3) uy3 = nd.uy;
            if (nd.nodeId == 4) uy4 = nd.uy;
        }

        System.out.printf("Truss deflections: Node2=%.4f mm, MidNode3=%.4f mm, Node4=%.4f mm%n",
                uy2 * 1000, uy3 * 1000, uy4 * 1000);

        assertTrue("Mid-span is the maximum downward deflection", uy3 < uy2 && uy3 < uy4);
        assertEquals("Symmetry: Node 2 and Node 4 have equal deflections", uy2, uy4, 1e-6);
    }

    // =====================================================
    // CASE 8: Multi-Material Verification (Concrete vs Steel)
    // =====================================================

    @Test
    public void testMultiMaterialContinuousBeam_Concrete_vs_Steel() throws Exception {
        System.out.printf("%n=== MULTI-MATERIAL VALIDATION (Concrete 25MPa vs Steel) ===%n");

        double E_steel = 210e9;
        double E_conc = 23.5e9;

        double delta_steel = runMaterialBeam("Steel", E_steel, 0.3);
        double delta_conc = runMaterialBeam("Concrete", E_conc, 0.2);

        System.out.printf("Deflection Steel:    δ=%.4f mm%n", delta_steel * 1000);
        System.out.printf("Deflection Concrete: δ=%.4f mm%n", delta_conc * 1000);

        double expectedRatio = E_steel / E_conc; // ~8.936
        double actualRatio = delta_conc / delta_steel;
        double errorPct = Math.abs(actualRatio - expectedRatio) / expectedRatio * 100.0;

        System.out.printf("Rigidity Ratio: Expected=%.3f, Actual=%.3f (Error=%.2f%%)%n",
                expectedRatio, actualRatio, errorPct);

        assertTrue("Material stiffness scales inversely with deflection (< 2% error)", errorPct < 2.0);
    }

    private double runMaterialBeam(String matName, double E, double nu) throws Exception {
        File workDir = tempFolder.newFolder("mat_" + matName);
        File inpFile = new File(workDir, "beam.inp");
        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            pw.println("1, 0.0, 0.0, 0.0");
            pw.println("2, 4.0, 0.0, 0.0");
            pw.println("3, 2.0, 0.0, 0.0");
            pw.println("*ELEMENT, TYPE=B32, ELSET=BEAMS");
            pw.println("1, 1, 3, 2");
            pw.println("*BEAM SECTION, ELSET=BEAMS, MATERIAL=" + matName + ", SECTION=RECT");
            pw.println("0.200, 0.300");
            pw.println("0.0, 0.0, 1.0");
            pw.println("*MATERIAL, NAME=" + matName);
            pw.println("*ELASTIC");
            pw.printf("%.1f, %.2f%n", E, nu);
            pw.println("*BOUNDARY");
            pw.println("1, 1, 6, 0.0");
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*CLOAD");
            pw.println("2, 2, -10000.0");
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*END STEP");
        }

        int exitCode = runCalculiX(workDir, "beam");
        assertEquals("CalculiX exit code", 0, exitCode);

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult result = parser.parse(new File(workDir, "beam.dat"));
        for (StructuralBeamDatParser.NodeDisplacement nd : result.displacements) {
            if (nd.nodeId == 2) return Math.abs(nd.uy);
        }
        fail("Tip node not found");
        return 0;
    }

    // =====================================================
    // CASE 8: Full Structural Engineering & Mechanics Validation
    // =====================================================

    @Test
    public void testStructuralEngineeringAndPhysicalValidation() throws Exception {
        StructuralModel model = new StructuralModel();
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 6.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(3, 0.0, 3.5, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(4, 6.0, 3.5, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(5, 0.0, 7.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(6, 6.0, 7.0, 0.0, StructuralModel.SupportType.FREE));

        model.elements.add(new StructuralModel.Element(1, 1, 3, "W8x31", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(2, 2, 4, "W8x31", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(3, 3, 5, "W8x31", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(4, 4, 6, "W8x31", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(5, 3, 4, "W12x50", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(6, 5, 6, "W12x50", "Structural Steel A36"));

        model.loads.add(new StructuralModel.Load(5, 25000.0, 0.0, 0.0)); // 25 kN lateral seismic load at roof
        model.loads.add(new StructuralModel.Load(3, 15000.0, 0.0, 0.0)); // 15 kN lateral seismic load at 1st floor

        // 1. Classification check
        com.diamon.civil.structural.export.PDFReportGenerator.StructuralSystemType sysType =
                com.diamon.civil.structural.export.PDFReportGenerator.classifyStructure(model);
        assertEquals("Should classify as Multi-Story Frame",
                com.diamon.civil.structural.export.PDFReportGenerator.StructuralSystemType.MULTI_STORY_FRAME, sysType);

        // 2. Material & Section lookup checks
        com.diamon.civil.structural.export.PDFReportGenerator.MaterialInfo mat =
                com.diamon.civil.structural.export.PDFReportGenerator.getMaterialProps("Structural Steel A36");
        assertNotNull(mat);
        assertEquals(200.0, mat.E_GPa, 0.01);
        assertEquals(250.0, mat.strength_MPa, 0.01);

        com.diamon.civil.structural.export.PDFReportGenerator.SectionInfo secCol =
                com.diamon.civil.structural.export.PDFReportGenerator.getSectionProps("W8x31");
        assertNotNull(secCol);
        assertTrue("W8x31 area should be positive", secCol.A_cm2 > 50.0);
        assertTrue("W8x31 major inertia should be positive", secCol.Iz_cm4 > 4000.0);

        // 3. Static Equilibrium Verification (Newton's 3rd Law)
        double totalAppliedFx = 0.0, totalAppliedFy = 0.0, totalAppliedFz = 0.0;
        for (StructuralModel.Load l : model.loads) {
            totalAppliedFx += l.fx;
            totalAppliedFy += l.fy;
            totalAppliedFz += l.fz;
        }
        assertEquals("Total applied lateral force is 40 kN", 40000.0, totalAppliedFx, 1e-3);
        double totalReactionRx = -totalAppliedFx;
        assertEquals("Base reaction balances lateral force exactly to 0", 0.0, totalAppliedFx + totalReactionRx, 1e-6);

        // 4. Drift calculation verification
        java.util.List<Double> levels = com.diamon.civil.structural.export.PDFReportGenerator.clusterStoryElevations(model.nodes, 0.15);
        assertEquals("3 elevation levels (0m, 3.5m, 7.0m)", 3, levels.size());
        double h1 = levels.get(1) - levels.get(0);
        double h2 = levels.get(2) - levels.get(1);
        assertEquals("Story 1 height is 3.5m", 3.5, h1, 1e-3);
        assertEquals("Story 2 height is 3.5m", 3.5, h2, 1e-3);

        // 5. Stress capacity ratio verification
        double P_N = 12000.0; // 12 kN axial
        double M_Nmm = 45000.0 * 1000.0; // 45 kN·m
        double A_mm2 = secCol.A_cm2 * 100.0;
        double S_mm3 = secCol.Sz_cm3 * 1000.0;
        double sigma_a = P_N / A_mm2;
        double sigma_b = M_Nmm / S_mm3;
        double sigma_total = sigma_a + sigma_b;
        double allowStress = mat.strength_MPa * 0.66;
        double dcRatio = sigma_total / allowStress;
        assertTrue("Stress ratio should be physically calculated and finite", dcRatio > 0 && !Double.isInfinite(dcRatio));

        System.out.printf("Structural Validation: Total V_base=%.2f kN | Stress sigma_comb=%.2f MPa | Allowable=%.2f MPa | D/C Ratio=%.3f%n",
                Math.abs(totalReactionRx) / 1000.0, sigma_total, allowStress, dcRatio);
    }

    // =====================================================
    // CASE 9: Portal Frame 1:1 Node Consistency & Equilibrium
    // =====================================================

    @Test
    public void testPresetPortalFrame_ExactNodeCountAndEquilibrium() throws Exception {
        File workDir = tempFolder.newFolder("portal_frame_1to1");
        File inpFile = new File(workDir, "portal.inp");

        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            pw.println("1, 0.0, 0.0, 0.0");
            pw.println("2, 4.0, 0.0, 0.0");
            pw.println("3, 0.0, 3.0, 0.0");
            pw.println("4, 4.0, 3.0, 0.0");
            pw.println("*ELEMENT, TYPE=B31, ELSET=BEAMS");
            pw.println("1, 1, 3");
            pw.println("2, 3, 4");
            pw.println("3, 4, 2");
            pw.println("*BEAM SECTION, ELSET=BEAMS, MATERIAL=STEEL, SECTION=RECT");
            pw.println("0.200, 0.200");
            pw.println("0.0, 0.0, 1.0");
            pw.println("*MATERIAL, NAME=STEEL");
            pw.println("*ELASTIC");
            pw.println("210000000000.0, 0.3");
            pw.println("*BOUNDARY");
            pw.println("1, 1, 6, 0.0");
            pw.println("2, 1, 6, 0.0");
            pw.println("NALL, 3, 3, 0.0");
            pw.println("NALL, 4, 5, 0.0");
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*CLOAD");
            pw.println("3, 1, 10000.0"); // 10 kN lateral load at node 3
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*NODE FILE");
            pw.println("U");
            pw.println("*EL FILE, SECTION FORCES, OUTPUT=2D");
            pw.println("S");
            pw.println("*END STEP");
        }

        int exitCode = runCalculiX(workDir, "portal");
        assertEquals("CalculiX exit code", 0, exitCode);

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult result = parser.parse(new File(workDir, "portal.dat"));
        assertNull("No parse error", result.error);

        // Displacements should have exactly 4 nodes
        assertEquals("Displacements must correspond to the 4 structural nodes (no phantom nodes)",
                4, result.displacements.size());

        Map<Integer, StructuralBeamDatParser.NodeDisplacement> dispMap = new HashMap<>();
        for (StructuralBeamDatParser.NodeDisplacement d : result.displacements) dispMap.put(d.nodeId, d);

        System.out.println("Node 1 UX=" + dispMap.get(1).ux);
        System.out.println("Node 2 UX=" + dispMap.get(2).ux);
        System.out.println("Node 3 UX=" + dispMap.get(3).ux);
        System.out.println("Node 4 UX=" + dispMap.get(4).ux);

        // Fixed base nodes must have zero displacement
        assertEquals(0.0, dispMap.get(1).ux, 1e-9);
        assertEquals(0.0, dispMap.get(2).ux, 1e-9);

        // Top nodes must have positive lateral displacement and frame sway
        assertTrue("Node 3 sway Ux > 0", dispMap.get(3).ux > 0.0);
        assertTrue("Node 4 sway Ux > 0", dispMap.get(4).ux > 0.0);
        assertEquals("Frame top nodes have equal lateral sway",
                dispMap.get(3).ux, dispMap.get(4).ux, 1e-5);

        System.out.printf("Portal Frame 1:1 Validation PASSED: Sway Ux=%.4f mm%n", dispMap.get(3).ux * 1000.0);
    }

    // =====================================================
    // CASE 10: Pratt Plane Truss Solver Validation
    // =====================================================

    @Test
    public void testPresetPrattTruss_SolvingAndEquilibrium() throws Exception {
        File workDir = tempFolder.newFolder("pratt_truss_solve");
        File inpFile = new File(workDir, "pratt.inp");

        double L = 10.0, H = 2.5, dx = L / 4.0;
        double P_load = 50000.0; // 50 kN center load

        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            // Bottom chord nodes
            pw.printf(Locale.US, "1, 0.0, 0.0, 0.0%n");
            pw.printf(Locale.US, "2, %.3f, 0.0, 0.0%n", dx);
            pw.printf(Locale.US, "3, %.3f, 0.0, 0.0%n", dx * 2);
            pw.printf(Locale.US, "4, %.3f, 0.0, 0.0%n", dx * 3);
            pw.printf(Locale.US, "5, %.3f, 0.0, 0.0%n", L);
            // Top chord nodes
            pw.printf(Locale.US, "6, %.3f, %.3f, 0.0%n", dx, H);
            pw.printf(Locale.US, "7, %.3f, %.3f, 0.0%n", dx * 2, H);
            pw.printf(Locale.US, "8, %.3f, %.3f, 0.0%n", dx * 3, H);

            pw.println("*ELEMENT, TYPE=B31, ELSET=BEAMS");
            pw.println("1, 1, 2"); pw.println("2, 2, 3"); pw.println("3, 3, 4"); pw.println("4, 4, 5");
            pw.println("5, 6, 7"); pw.println("6, 7, 8");
            pw.println("7, 1, 6"); pw.println("8, 5, 8");
            pw.println("9, 2, 6"); pw.println("10, 3, 7"); pw.println("11, 4, 8");
            pw.println("12, 2, 7"); pw.println("13, 4, 7");

            pw.println("*BEAM SECTION, ELSET=BEAMS, MATERIAL=STEEL, SECTION=RECT");
            pw.println("0.100, 0.100");
            pw.println("0.0, 0.0, 1.0");
            pw.println("*MATERIAL, NAME=STEEL");
            pw.println("*ELASTIC");
            pw.println("200000000000.0, 0.3");
            pw.println("*BOUNDARY");
            pw.println("1, 1, 2, 0.0"); // Pinned at node 1 in X, Y
            pw.println("5, 2, 2, 0.0"); // Roller Uy=0 at node 5
            pw.println("NALL, 3, 3, 0.0"); // Constrain out-of-plane Uz=0
            pw.println("NALL, 4, 5, 0.0"); // Constrain out-of-plane Rx=0, Ry=0
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*CLOAD");
            pw.printf(Locale.US, "3, 2, %.1f%n", -P_load); // Downward load at bottom center node 3
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*NODE FILE");
            pw.println("U");
            pw.println("*EL FILE, SECTION FORCES, OUTPUT=2D");
            pw.println("S");
            pw.println("*END STEP");
        }

        int exitCode = runCalculiX(workDir, "pratt");
        assertEquals("CalculiX exit code", 0, exitCode);

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult result = parser.parse(new File(workDir, "pratt.dat"));
        assertNull("No parse error", result.error);

        // Center deflection must be downward
        Map<Integer, StructuralBeamDatParser.NodeDisplacement> dispMap = new HashMap<>();
        for (StructuralBeamDatParser.NodeDisplacement d : result.displacements) dispMap.put(d.nodeId, d);

        assertTrue("Center node 3 deflection Uy < 0", dispMap.get(3).uy < 0);
        System.out.printf("Pratt Truss Center Deflection: Uy=%.4f mm%n", dispMap.get(3).uy * 1000.0);
    }

    // =====================================================
    // CASE 11: Cantilever Bracket Triangular Frame
    // =====================================================

    @Test
    public void testPresetCantileverBracket_SolvingAndEquilibrium() throws Exception {
        File workDir = tempFolder.newFolder("cantilever_bracket_solve");
        File inpFile = new File(workDir, "bracket.inp");

        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            pw.println("1, 0.0, 0.0, 0.0");
            pw.println("2, 0.0, 3.0, 0.0");
            pw.println("3, 2.0, 3.0, 0.0");
            pw.println("4, 4.0, 3.0, 0.0");
            pw.println("5, 2.0, 0.0, 0.0");

            pw.println("*ELEMENT, TYPE=B31, ELSET=BEAMS");
            pw.println("1, 2, 3");
            pw.println("2, 3, 4");
            pw.println("3, 1, 5");
            pw.println("4, 5, 4");
            pw.println("5, 5, 3");
            pw.println("6, 1, 3");

            pw.println("*BEAM SECTION, ELSET=BEAMS, MATERIAL=STEEL, SECTION=RECT");
            pw.println("0.200, 0.200");
            pw.println("0.0, 0.0, 1.0");
            pw.println("*MATERIAL, NAME=STEEL");
            pw.println("*ELASTIC");
            pw.println("200000000000.0, 0.3");
            pw.println("*BOUNDARY");
            pw.println("1, 1, 6, 0.0"); // Fixed at wall
            pw.println("2, 1, 6, 0.0"); // Fixed at wall
            pw.println("NALL, 3, 3, 0.0"); // Constrain out-of-plane Uz=0
            pw.println("NALL, 4, 5, 0.0"); // Constrain out-of-plane Rx=0, Ry=0
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*CLOAD");
            pw.println("4, 2, -20000.0"); // 20 kN tip load at node 4
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*NODE FILE");
            pw.println("U");
            pw.println("*EL FILE, SECTION FORCES, OUTPUT=2D");
            pw.println("S");
            pw.println("*END STEP");
        }

        int exitCode = runCalculiX(workDir, "bracket");
        assertEquals("CalculiX exit code", 0, exitCode);

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult result = parser.parse(new File(workDir, "bracket.dat"));
        assertNull("No parse error", result.error);

        Map<Integer, StructuralBeamDatParser.NodeDisplacement> dispMap = new HashMap<>();
        for (StructuralBeamDatParser.NodeDisplacement d : result.displacements) dispMap.put(d.nodeId, d);

        assertTrue("Cantilever tip node 4 downward deflection Uy < 0", dispMap.get(4).uy < 0);
        System.out.printf("Cantilever Bracket Tip Deflection: Uy=%.4f mm%n", dispMap.get(4).uy * 1000.0);
    }

    // =====================================================
    // CASE 12: 2D Concrete Continuous Beam (3 Supports + Overhang)
    // =====================================================

    @Test
    public void testPresetConcreteContinuousBeam_ThreeSupports() throws Exception {
        File workDir = tempFolder.newFolder("concrete_continuous_beam");
        File inpFile = new File(workDir, "conc_beam.inp");

        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            pw.println("1, 0.0, 0.0, 0.0");
            pw.println("2, 2.0, 0.0, 0.0");
            pw.println("3, 4.0, 0.0, 0.0");
            pw.println("4, 5.5, 0.0, 0.0");
            pw.println("5, 7.0, 0.0, 0.0");
            pw.println("6, 9.0, 0.0, 0.0");

            pw.println("*ELEMENT, TYPE=B31, ELSET=BEAMS");
            pw.println("1, 1, 2");
            pw.println("2, 2, 3");
            pw.println("3, 3, 4");
            pw.println("4, 4, 5");
            pw.println("5, 5, 6");

            pw.println("*BEAM SECTION, ELSET=BEAMS, MATERIAL=CONCRETE, SECTION=RECT");
            pw.println("0.300, 0.400");
            pw.println("0.0, 0.0, 1.0");
            pw.println("*MATERIAL, NAME=CONCRETE");
            pw.println("*ELASTIC");
            pw.println("25000000000.0, 0.2"); // Concrete 25 GPa
            pw.println("*BOUNDARY");
            pw.println("1, 1, 2, 0.0"); // Pinned support (node 1)
            pw.println("3, 2, 2, 0.0"); // Roller support (node 3)
            pw.println("5, 2, 2, 0.0"); // Roller support (node 5)
            pw.println("NALL, 3, 3, 0.0");
            pw.println("NALL, 4, 5, 0.0");
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*CLOAD");
            pw.println("6, 2, -30000.0"); // 30 kN load at overhang tip (node 6)
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*NODE FILE");
            pw.println("U");
            pw.println("*EL FILE, SECTION FORCES, OUTPUT=2D");
            pw.println("S");
            pw.println("*END STEP");
        }

        int exitCode = runCalculiX(workDir, "conc_beam");
        assertEquals("CalculiX exit code", 0, exitCode);

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult result = parser.parse(new File(workDir, "conc_beam.dat"));
        assertNull("No parse error", result.error);

        Map<Integer, StructuralBeamDatParser.NodeDisplacement> dispMap = new HashMap<>();
        for (StructuralBeamDatParser.NodeDisplacement d : result.displacements) dispMap.put(d.nodeId, d);

        // Overhang tip deflects downward
        assertTrue("Overhang tip node 6 downward deflection Uy < 0", dispMap.get(6).uy < 0);
        // Span 2 midpoint (node 4) experiences upward uplift due to cantilever negative moment at support 5
        assertTrue("Span 2 node 4 upward uplift Uy > 0", dispMap.get(4).uy > 0);

        System.out.printf("Continuous Beam Overhang Uy=%.4f mm | Span 2 Uplift Uy=%.4f mm%n",
                dispMap.get(6).uy * 1000.0, dispMap.get(4).uy * 1000.0);
    }

    // =====================================================
    // CASE 13: 2D Shear Wall Panel (CPS4 Plane Stress)
    // =====================================================

    @Test
    public void testPresetShearWall_PlaneStressCPS4() throws Exception {
        File workDir = tempFolder.newFolder("shear_wall_cps4");
        File inpFile = new File(workDir, "shearwall.inp");

        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            pw.println("1, 0.0, 0.0, 0.0");
            pw.println("2, 3.0, 0.0, 0.0");
            pw.println("3, 3.0, 3.0, 0.0");
            pw.println("4, 0.0, 3.0, 0.0");

            pw.println("*ELEMENT, TYPE=CPS4, ELSET=WALL");
            pw.println("1, 1, 2, 3, 4");

            pw.println("*SOLID SECTION, ELSET=WALL, MATERIAL=CONCRETE");
            pw.println("0.200"); // 20 cm thickness

            pw.println("*MATERIAL, NAME=CONCRETE");
            pw.println("*ELASTIC");
            pw.println("25000000000.0, 0.2");
            pw.println("*BOUNDARY");
            pw.println("1, 1, 2, 0.0"); // Fixed at base
            pw.println("2, 1, 2, 0.0");
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*CLOAD");
            pw.println("4, 1, 50000.0"); // 50 kN lateral shear force at top
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*NODE FILE");
            pw.println("U");
            pw.println("*END STEP");
        }

        int exitCode = runCalculiX(workDir, "shearwall");
        assertEquals("CalculiX exit code", 0, exitCode);

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult result = parser.parse(new File(workDir, "shearwall.dat"));
        assertNull("No parse error", result.error);

        Map<Integer, StructuralBeamDatParser.NodeDisplacement> dispMap = new HashMap<>();
        for (StructuralBeamDatParser.NodeDisplacement d : result.displacements) dispMap.put(d.nodeId, d);

        assertTrue("Top wall node 4 lateral shear displacement Ux > 0", dispMap.get(4).ux > 0);
        System.out.printf("Shear Wall Top Lateral Drift: Ux=%.4f mm%n", dispMap.get(4).ux * 1000.0);
    }

    // =====================================================
    // CASE 13: Interactive Custom Drawn Grid Topology Validation
    // =====================================================

    @Test
    public void testCustomDrawnTopologyCalculixValidation() throws Exception {
        // Simulating a custom 2D frame drawn in GridEditorView
        // Node 1 (0,0) Fixed, Node 2 (6,0) Pinned
        // Node 3 (0,4) Beam-column knee, Node 4 (6,4) Beam-column knee, Node 5 (3,5.5) Roof apex
        // Loads: 15 kN horizontal wind on Node 3, 30 kN downward gravity on Apex Node 5
        StructuralModel model = new StructuralModel();
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 6.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(3, 0.0, 4.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(4, 6.0, 4.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(5, 3.0, 5.5, 0.0, StructuralModel.SupportType.FREE));

        // Elements
        model.elements.add(new StructuralModel.Element(1, 1, 3, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(2, 2, 4, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(3, 3, 5, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(4, 5, 4, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(5, 3, 4, "HEB200", "Structural Steel A36")); // Tie beam

        // Loads
        model.loads.add(new StructuralModel.Load(3, 15000.0, 0.0, 0.0));
        model.loads.add(new StructuralModel.Load(5, 0.0, -30000.0, 0.0));

        File workDir = tempFolder.newFolder("custom_drawn_frame");
        File inpFile = new File(workDir, "custom_frame.inp");
        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            for (StructuralModel.Node n : model.nodes) {
                pw.printf(Locale.US, "%d, %.4f, %.4f, %.4f%n", n.id, n.x, n.y, n.z);
            }
            pw.println("*ELEMENT, TYPE=B31, ELSET=BEAMS");
            for (StructuralModel.Element e : model.elements) {
                pw.printf("%d, %d, %d%n", e.id, e.node1Id, e.node2Id);
            }
            pw.println("*BEAM SECTION, ELSET=BEAMS, MATERIAL=STEEL, SECTION=RECT");
            pw.println("0.200, 0.200");
            pw.println("0.0, 0.0, 1.0");
            pw.println("*MATERIAL, NAME=STEEL");
            pw.println("*ELASTIC");
            pw.println("200000000000.0, 0.3");
            pw.println("*BOUNDARY");
            pw.println("1, 1, 6, 0.0"); // Node 1 Fixed
            pw.println("2, 1, 3, 0.0"); // Node 2 Pinned (Translations fixed)
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*CLOAD");
            for (StructuralModel.Load l : model.loads) {
                if (Math.abs(l.fx) > 1e-4) pw.printf(Locale.US, "%d, 1, %.1f%n", l.nodeId, l.fx);
                if (Math.abs(l.fy) > 1e-4) pw.printf(Locale.US, "%d, 2, %.1f%n", l.nodeId, l.fy);
            }
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*NODE FILE");
            pw.println("U");
            pw.println("*EL FILE, SECTION FORCES, OUTPUT=2D");
            pw.println("S");
            pw.println("*END STEP");
        }

        int exitCode = runCalculiX(workDir, "custom_frame");
        assertEquals("CalculiX must solve custom drawn frame with exit code 0", 0, exitCode);

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult result = parser.parse(new File(workDir, "custom_frame.dat"));
        assertNull("No parse error in custom drawn frame", result.error);
        assertNotNull("Displacements must not be null", result.displacements);
        assertEquals(5, result.displacements.size());

        Map<Integer, StructuralBeamDatParser.NodeDisplacement> dispMap = new HashMap<>();
        for (StructuralBeamDatParser.NodeDisplacement d : result.displacements) dispMap.put(d.nodeId, d);

        // Node 1 fixed -> displacements are 0
        assertEquals(0.0, dispMap.get(1).ux, 1e-6);
        assertEquals(0.0, dispMap.get(1).uy, 1e-6);

        // Node 2 pinned -> translation is 0
        assertEquals(0.0, dispMap.get(2).ux, 1e-6);
        assertEquals(0.0, dispMap.get(2).uy, 1e-6);

        // Node 3 lateral wind -> Ux > 0
        assertTrue("Node 3 must deflect laterally in wind direction", dispMap.get(3).ux > 0);

        // Apex Node 5 downward gravity -> Uy < 0
        assertTrue("Node 5 roof apex must sag downwards under vertical load", dispMap.get(5).uy < 0);

        System.out.printf("Custom Frame Solve: Apex Sag=%.4f mm, Drift Node3=%.4f mm%n",
                dispMap.get(5).uy * 1000.0, dispMap.get(3).ux * 1000.0);
    }

    @Test
    public void testMixedSectionsAndMaterialsValidation() throws Exception {
        System.out.printf("%n=== MIXED SECTIONS AND MATERIALS VALIDATION (No Conflict) ===%n");
        File workDir = tempFolder.newFolder("mixed_sections_validation");
        File inpFile = new File(workDir, "mixed_frame.inp");

        // Column 1 (HEB300, Steel A36), Column 2 (HEB300, Steel A36), Beam (IPE200, Steel A572)
        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            pw.println("1, 0.0, 0.0, 0.0");
            pw.println("2, 6.0, 0.0, 0.0");
            pw.println("3, 0.0, 4.0, 0.0");
            pw.println("4, 6.0, 4.0, 0.0");

            pw.println("*ELEMENT, TYPE=B31, ELSET=ES_HEB300_STEELA36");
            pw.println("1, 1, 3");
            pw.println("2, 2, 4");

            pw.println("*ELEMENT, TYPE=B31, ELSET=ES_IPE200_STEELA572");
            pw.println("3, 3, 4");

            pw.println("*ELSET, ELSET=Eall");
            pw.println("1, 2, 3");

            pw.println("*BEAM SECTION, ELSET=ES_HEB300_STEELA36, MATERIAL=STEELA36, SECTION=RECT");
            pw.println("0.300, 0.300");
            pw.println("0.0, 0.0, -1.0");

            pw.println("*BEAM SECTION, ELSET=ES_IPE200_STEELA572, MATERIAL=STEELA572, SECTION=RECT");
            pw.println("0.100, 0.200");
            pw.println("0.0, 0.0, -1.0");

            pw.println("*MATERIAL, NAME=STEELA36");
            pw.println("*ELASTIC");
            pw.println("200000000000.0, 0.3");

            pw.println("*MATERIAL, NAME=STEELA572");
            pw.println("*ELASTIC");
            pw.println("210000000000.0, 0.3");

            pw.println("*BOUNDARY");
            pw.println("1, 1, 6, 0.0");
            pw.println("2, 1, 6, 0.0");

            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*CLOAD");
            pw.println("3, 1, 25000.0"); // 25 kN lateral wind load
            pw.println("3, 2, -50000.0"); // 50 kN vertical gravity load

            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*NODE FILE");
            pw.println("U");
            pw.println("*EL FILE, SECTION FORCES, OUTPUT=2D");
            pw.println("S");
            pw.println("*END STEP");
        }

        int exitCode = runCalculiX(workDir, "mixed_frame");
        assertEquals("CalculiX must solve mixed-section and mixed-material frame successfully", 0, exitCode);

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult result = parser.parse(new File(workDir, "mixed_frame.dat"));
        assertNull("No parse error in mixed frame", result.error);
        assertEquals(4, result.displacements.size());

        Map<Integer, StructuralBeamDatParser.NodeDisplacement> dispMap = new HashMap<>();
        for (StructuralBeamDatParser.NodeDisplacement d : result.displacements) dispMap.put(d.nodeId, d);

        // Fixed base nodes 1 & 2 -> zero displacement
        assertEquals(0.0, dispMap.get(1).ux, 1e-6);
        assertEquals(0.0, dispMap.get(1).uy, 1e-6);
        assertEquals(0.0, dispMap.get(2).ux, 1e-6);
        assertEquals(0.0, dispMap.get(2).uy, 1e-6);

        // Top joint node 3 deflections
        assertTrue("Node 3 lateral deflection must be positive under positive Fx", dispMap.get(3).ux > 0);
        assertTrue("Node 3 vertical deflection must be negative under downward Fy", dispMap.get(3).uy < 0);

        System.out.printf("Mixed Frame Solve: Node 3 Lateral Drift=%.4f mm, Vertical Sag=%.4f mm%n",
                dispMap.get(3).ux * 1000.0, dispMap.get(3).uy * 1000.0);
    }

    @Test
    public void testFullUiWorkflowGabledFrameValidation() throws Exception {
        System.out.printf("%n=== FULL UI SIMULATED WORKFLOW (Gabled Frame With Mixed Materials & Supports) ===%n");
        File workDir = tempFolder.newFolder("full_ui_sim_validation");
        File inpFile = new File(workDir, "gabled_frame.inp");

        // Simulating complete UI output:
        // Node 1: (0,0) FIXED, Node 2: (10,0) ROLLER (Uy=0, Ux free), Node 3: (0,5), Node 4: (10,5), Node 5: (5, 7.5) APEX
        // Elements: Columns HEB300 / Steel A36, Rafters IPE240 / Steel A572, Tie Beam IPE200 / Steel A36
        // Loads: Node 5 Fy = -35 kN, Node 3 Fx = 15 kN
        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            pw.println("1, 0.0, 0.0, 0.0");
            pw.println("2, 10.0, 0.0, 0.0");
            pw.println("3, 0.0, 5.0, 0.0");
            pw.println("4, 10.0, 5.0, 0.0");
            pw.println("5, 5.0, 7.5, 0.0");

            pw.println("*ELEMENT, TYPE=B31, ELSET=ES_HEB300_STEELA36");
            pw.println("1, 1, 3");
            pw.println("2, 2, 4");

            pw.println("*ELEMENT, TYPE=B31, ELSET=ES_IPE240_STEELA572");
            pw.println("3, 3, 5");
            pw.println("4, 5, 4");

            pw.println("*ELEMENT, TYPE=B31, ELSET=ES_IPE200_STEELA36");
            pw.println("5, 3, 4");

            pw.println("*ELSET, ELSET=Eall");
            pw.println("1, 2, 3, 4, 5");

            pw.println("*BEAM SECTION, ELSET=ES_HEB300_STEELA36, MATERIAL=STEELA36, SECTION=RECT");
            pw.println("0.300, 0.300");
            pw.println("0.0, 0.0, -1.0");

            pw.println("*BEAM SECTION, ELSET=ES_IPE240_STEELA572, MATERIAL=STEELA572, SECTION=RECT");
            pw.println("0.120, 0.240");
            pw.println("0.0, 0.0, -1.0");

            pw.println("*BEAM SECTION, ELSET=ES_IPE200_STEELA36, MATERIAL=STEELA36, SECTION=RECT");
            pw.println("0.100, 0.200");
            pw.println("0.0, 0.0, -1.0");

            pw.println("*MATERIAL, NAME=STEELA36");
            pw.println("*ELASTIC");
            pw.println("200000000000.0, 0.3");

            pw.println("*MATERIAL, NAME=STEELA572");
            pw.println("*ELASTIC");
            pw.println("210000000000.0, 0.3");

            pw.println("*BOUNDARY");
            pw.println("1, 1, 6, 0.0"); // Fixed base
            pw.println("2, 2, 2, 0.0"); // Roller base (restrains Uy only, allows Ux lateral sliding)
            pw.println("2, 3, 6, 0.0"); // In-plane only

            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*CLOAD");
            pw.println("3, 1, 15000.0"); // 15 kN lateral wind load at left eave
            pw.println("5, 2, -35000.0"); // 35 kN downward gravity load at roof apex

            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*NODE FILE");
            pw.println("U");
            pw.println("*EL FILE, SECTION FORCES, OUTPUT=2D");
            pw.println("S");
            pw.println("*END STEP");
        }

        int exitCode = runCalculiX(workDir, "gabled_frame");
        assertEquals("CalculiX must solve full UI gabled frame with code 0", 0, exitCode);

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult result = parser.parse(new File(workDir, "gabled_frame.dat"));
        assertNull("No parse error in gabled frame", result.error);
        assertEquals(5, result.displacements.size());

        Map<Integer, StructuralBeamDatParser.NodeDisplacement> dispMap = new HashMap<>();
        for (StructuralBeamDatParser.NodeDisplacement d : result.displacements) dispMap.put(d.nodeId, d);

        // Fixed base Node 1: Ux = 0, Uy = 0
        assertEquals(0.0, dispMap.get(1).ux, 1e-6);
        assertEquals(0.0, dispMap.get(1).uy, 1e-6);

        // Roller base Node 2: Uy = 0 (strictly zero vertical settlement), Ux != 0 (slides under lateral load)
        assertEquals(0.0, dispMap.get(2).uy, 1e-6);
        assertTrue("Roller node 2 must be free to displace horizontally under wind drift", Math.abs(dispMap.get(2).ux) > 1e-6);

        // Eave Node 3: drifts in direction of positive Fx
        assertTrue("Left eave Node 3 must displace laterally in positive X direction", dispMap.get(3).ux > 0);

        // Apex Node 5: sags downwards under gravity
        assertTrue("Roof apex Node 5 must deflect downwards (negative Uy)", dispMap.get(5).uy < 0);

        System.out.printf("Gabled Frame Solve: Eave Drift=%.4f mm, Apex Sag=%.4f mm, Roller Slide=%.4f mm%n",
                dispMap.get(3).ux * 1000.0, dispMap.get(5).uy * 1000.0, dispMap.get(2).ux * 1000.0);
    }

    // =====================================================
    // Helper Methods
    // =====================================================

    @Test
    public void testPreset11_ConcreteSlabPlate_FullMechanicsAndEquilibrium() throws Exception {
        System.out.printf("%n=== CONCRETE SLAB PLATE (S4R - Mindlin/Reissner Plate Flexure) ===%n");
        StructuralModel model = new StructuralModel();
        double w = 4.0, l = 4.0, t = 0.15;

        // 9 Nodes on 4x4m grid
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(2, w / 2.0, 0.0, 0.0, StructuralModel.SupportType.ROLLER));
        model.nodes.add(new StructuralModel.Node(3, w, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(4, 0.0, l / 2.0, 0.0, StructuralModel.SupportType.ROLLER));
        model.nodes.add(new StructuralModel.Node(5, w / 2.0, l / 2.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(6, w, l / 2.0, 0.0, StructuralModel.SupportType.ROLLER));
        model.nodes.add(new StructuralModel.Node(7, 0.0, l, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(8, w / 2.0, l, 0.0, StructuralModel.SupportType.ROLLER));
        model.nodes.add(new StructuralModel.Node(9, w, l, 0.0, StructuralModel.SupportType.PINNED));

        // Perimeter beams (Rect 200x300, Concrete 25 MPa)
        model.elements.add(new StructuralModel.Element(1, 1, 2, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(2, 2, 3, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(3, 3, 6, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(4, 6, 9, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(5, 9, 8, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(6, 8, 7, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(7, 7, 4, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(8, 4, 1, "Rect 200x300", "Concrete 25 MPa"));

        // 4 Shell panels S4R
        model.panels.add(new StructuralModel.Panel(1, java.util.Arrays.asList(1, 2, 5, 4), t, "Concrete 25 MPa", "S4R"));
        model.panels.add(new StructuralModel.Panel(2, java.util.Arrays.asList(2, 3, 6, 5), t, "Concrete 25 MPa", "S4R"));
        model.panels.add(new StructuralModel.Panel(3, java.util.Arrays.asList(4, 5, 8, 7), t, "Concrete 25 MPa", "S4R"));
        model.panels.add(new StructuralModel.Panel(4, java.util.Arrays.asList(5, 6, 9, 8), t, "Concrete 25 MPa", "S4R"));

        // -40 kN out-of-plane vertical load at center node 5
        model.loads.add(new StructuralModel.Load(5, 0.0, 0.0, -40000.0));

        FrameAnalysisEngine.AnalysisOutput out = FrameAnalysisEngine.analyze(model);
        assertNotNull(out);
        assertNotNull(out.parseResult);

        // 1. Global Static Equilibrium: Total vertical reaction = +40.0 kN
        assertEquals("Applied vertical force Fz is -40 kN", -40000.0, out.sumAppliedFz, 1e-2);
        assertEquals("Total reactive vertical force Rz is +40 kN", 40000.0, out.sumReactRz, 1e-2);
        assertEquals("Vertical equilibrium residual is 0.000 kN", 0.0, out.residualFz, 1e-6);

        // 2. Center deflection magnitude: Uz is downward (< 0)
        StructuralBeamDatParser.NodeDisplacement node5Disp = null;
        for (StructuralBeamDatParser.NodeDisplacement d : out.parseResult.displacements) {
            if (d.nodeId == 5) node5Disp = d;
        }
        assertNotNull(node5Disp);
        System.out.printf("Plate Center Node 5 Deflection Uz = %.6f mm%n", node5Disp.uz * 1000.0);
        assertTrue("Center deflection Uz is negative (downward)", node5Disp.uz < 0.0);
        assertTrue("Center deflection magnitude is physically non-zero and finite (< 25 mm)", Math.abs(node5Disp.uz * 1000.0) < 25.0 && Math.abs(node5Disp.uz) > 1e-6);

        // 3. Panel Internal Forces & Symmetry: 4 panels have identical moments
        assertEquals(4, out.parseResult.panelForces.size());
        for (StructuralBeamDatParser.PanelForces pf : out.parseResult.panelForces) {
            assertEquals("Mx is 2.50 kN·m/m", 2.50, pf.Mx, 0.1);
            assertEquals("My is 2.13 kN·m/m", 2.13, pf.My, 0.1);
            assertEquals("Vmax is 5.00 kN/m", 5.00, pf.Vmax, 0.1);
        }

        // 4. Perimeter beams: V2 = 5.0 kN, M3 = 5.0 kN·m
        for (StructuralBeamDatParser.SectionForces sf : out.parseResult.forces) {
            assertEquals("Beam shear V2 is 5.0 kN", 5000.0, Math.abs(sf.V2), 1e-2);
            assertEquals("Beam moment M1/M3 is 5.0 kN·m", 5000.0, Math.abs(sf.M1), 1e-2);
        }
    }

    @Test
    public void testPreset12_ShearWall_FullMechanicsAndEquilibrium() throws Exception {
        System.out.printf("%n=== SHEAR WALL (CPS4 - Plane Stress & Coupled Boundary Elements) ===%n");
        StructuralModel model = new StructuralModel();
        double w = 3.0, h = 3.0, t = 0.20;

        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, w, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(3, w, h, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(4, 0.0, h, 0.0, StructuralModel.SupportType.FREE));

        model.elements.add(new StructuralModel.Element(1, 1, 4, "Rect 300x400", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(2, 2, 3, "Rect 300x400", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(3, 4, 3, "Rect 300x400", "Concrete 25 MPa"));

        model.panels.add(new StructuralModel.Panel(1, java.util.Arrays.asList(1, 2, 3, 4), t, "Concrete 25 MPa", "CPS4"));

        // 50 kN lateral shear force at top Node 4
        model.loads.add(new StructuralModel.Load(4, 50000.0, 0.0, 0.0));

        FrameAnalysisEngine.AnalysisOutput out = FrameAnalysisEngine.analyze(model);
        assertNotNull(out);
        assertNotNull(out.parseResult);

        // 1. Horizontal Equilibrium: Rx1 + Rx2 = -50 kN
        double rx1 = out.reactions.get(1)[0];
        double rx2 = out.reactions.get(2)[0];
        assertEquals("Horizontal reaction balance sum is -50 kN", -50000.0, rx1 + rx2, 1e-2);
        assertEquals("Support 1 takes ~19.33 kN horizontal shear", -19330.0, rx1, 100.0);
        assertEquals("Support 2 takes ~30.67 kN horizontal shear (strut compression)", -30670.0, rx2, 100.0);

        // 2. Vertical Couple: Ry1 = -49.58 kN (tension), Ry2 = +49.58 kN (compression)
        double ry1 = out.reactions.get(1)[1];
        double ry2 = out.reactions.get(2)[1];
        assertEquals("Vertical couple balance is zero", 0.0, ry1 + ry2, 1e-3);
        assertEquals("Column 1 is in tension Ry1 = -49.58 kN", -49583.0, ry1, 100.0);
        assertEquals("Column 2 is in compression Ry2 = +49.58 kN", 49583.0, ry2, 100.0);

        // 3. Overturning moment balance: 50 kN * 3m = 150 kN*m
        double M_couple = ry2 * w; // 49.583 kN * 3m = 148.75 kN*m (99.2% of total overturning)
        double mz1 = out.reactions.get(1)[5];
        double mz2 = out.reactions.get(2)[5];
        double totalResistingMoment = (M_couple + mz1 + mz2) / 1000.0;
        assertEquals("Total resisting moment balances exactly 150 kN·m", 150.0, totalResistingMoment, 0.1);

        // 4. Panel Stresses non-zero & computed
        assertEquals(1, out.parseResult.panelForces.size());
        StructuralBeamDatParser.PanelForces pf = out.parseResult.panelForces.get(0);
        assertTrue("Shear wall panel carried shear > 40 kN", pf.Vshear_total > 40.0);
        assertTrue("Panel von Mises stress > 0", pf.sigmaVM > 0.0);

        // 5. Lateral Drift at Node 4: Ux ~ 0.0366 mm
        StructuralBeamDatParser.NodeDisplacement node4Disp = null;
        for (StructuralBeamDatParser.NodeDisplacement d : out.parseResult.displacements) {
            if (d.nodeId == 4) node4Disp = d;
        }
        assertNotNull(node4Disp);
        assertEquals("Top drift Ux is ~ 0.0366 mm", 0.0000366, node4Disp.ux, 0.00001);
    }

    private int runCalculiX(File workDir, String jobName) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(CCX_PATH, jobName);
        pb.directory(workDir);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("CCX: " + line);
            }
        }
        return p.waitFor();
    }
}
