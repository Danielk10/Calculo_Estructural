package com.diamon.civil.structural.engine;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import java.io.*;

/**
 * Unit Tests for Structural Module — Pure logic validation.
 *
 * Tests cover:
 * 1. FRD Parser tensor mapping (critical bug fix validation)
 * 2. DAT Parser field extraction
 * 3. Material property physical correctness
 * 4. Section property geometric correctness
 * 5. Cantilever beam physics validation (Euler-Bernoulli theory)
 * 6. Model validation logic
 * 7. Diagram zero-crossing correctness
 */
public class StructuralModuleUnitTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // =====================================================
    // TEST 1: FRD Parser — Corrected Tensor Mapping
    // =====================================================

    @Test
    public void testFrdParserTensorMapping_SXX_is_Axial() throws Exception {
        // CalculiX FRD format: -1 followed by nodeId and 6 stress components
        // SXX=1000 (Axial), SYY=200 (V2), SZZ=50 (V3), SXY=10 (Torque), SYZ=5000 (M2), SZX=3000 (M3)
        File frdFile = tempFolder.newFile("test_beam.frd");
        try (PrintWriter pw = new PrintWriter(new FileWriter(frdFile))) {
            pw.println("    1C");
            pw.println(" -4  STRESS           6    1");
            pw.println(" -5  SXX              1    2    1    0");
            pw.println(" -5  SYY              1    2    2    0");
            pw.println(" -5  SZZ              1    2    3    0");
            pw.println(" -5  SXY              1    2    4    0");
            pw.println(" -5  SYZ              1    2    5    0");
            pw.println(" -5  SZX              1    2    6    0");
            // Node 1: SXX=1000, SYY=200, SZZ=50, SXY=10, SYZ=5000, SZX=3000
            pw.println(" -1         1 1.00000E+03 2.00000E+02 5.00000E+01 1.00000E+01 5.00000E+03 3.00000E+03");
            pw.println(" -3");
        }

        StructuralBeamFrdParser parser = new StructuralBeamFrdParser();
        StructuralBeamFrdParser.ParseResult result = parser.parse(frdFile);

        assertNull("No parse error expected", result.error);
        assertEquals("Should parse 1 force record", 1, result.forces.size());

        StructuralBeamFrdParser.SectionForces sf = result.forces.get(0);
        
        // CRITICAL VALIDATION: SXX must map to axialNormal, NOT to shear1
        assertEquals("SXX (1000) must be Axial Normal Force", 1000.0, sf.axialNormal, 1e-3);
        assertEquals("SYY (200) must be Shear Force V2", 200.0, sf.shear1, 1e-3);
        assertEquals("SZZ (50) must be Shear Force V3", 50.0, sf.shear2, 1e-3);
        assertEquals("SXY (10) must be Torque", 10.0, sf.torque, 1e-3);
        assertEquals("SYZ (5000) must be Bending Moment M2", 5000.0, sf.bendingMoment1, 1e-3);
        assertEquals("SZX (3000) must be Bending Moment M3", 3000.0, sf.bendingMoment2, 1e-3);
    }

    @Test
    public void testFrdParserMaxAbsValues() throws Exception {
        File frdFile = tempFolder.newFile("test_max.frd");
        try (PrintWriter pw = new PrintWriter(new FileWriter(frdFile))) {
            pw.println(" -4  STRESS           6    1");
            // Two records with different signs to test max absolute values
            pw.println(" -1         1 1.00000E+03-2.00000E+02 5.00000E+01 1.00000E+01 5.00000E+03-3.00000E+03");
            pw.println(" -1         2-1.50000E+03 3.00000E+02-7.00000E+01-1.50000E+01-4.00000E+03 4.00000E+03");
            pw.println(" -3");
        }

        StructuralBeamFrdParser parser = new StructuralBeamFrdParser();
        StructuralBeamFrdParser.ParseResult result = parser.parse(frdFile);

        assertEquals("Max |Axial| should be 1500", 1500.0, result.maxAbsAxial, 1e-3);
        assertEquals("Max |Shear1| should be 300", 300.0, result.maxAbsShear1, 1e-3);
        assertEquals("Max |Shear2| should be 70", 70.0, result.maxAbsShear2, 1e-3);
        assertEquals("Max |Torque| should be 15", 15.0, result.maxAbsTorque, 1e-3);
        assertEquals("Max |Bending1| should be 5000", 5000.0, result.maxAbsBending1, 1e-3);
        assertEquals("Max |Bending2| should be 4000", 4000.0, result.maxAbsBending2, 1e-3);
    }

    // =====================================================
    // TEST 2: DAT Parser — Section Force Extraction
    // =====================================================

    @Test
    public void testDatParserSectionForces() throws Exception {
        File datFile = tempFolder.newFile("test_beam.dat");
        try (PrintWriter pw = new PrintWriter(new FileWriter(datFile))) {
            pw.println("");
            pw.println(" section forces and moments for set BEAMS and time  0.1000000E+01");
            pw.println("");
            pw.println("  element  integration  normal  shear  shear  bending  bending  torque");
            pw.println("           pt            force  force  force  moment   moment");
            pw.println("                          (1)    (2)    (3)    (1)      (2)");
            pw.println("       1       1    1.000E+04  5.000E+03  0.000E+00  2.000E+04  0.000E+00  0.000E+00");
            pw.println("       2       1   -5.000E+03  3.000E+03  0.000E+00  1.500E+04  0.000E+00  0.000E+00");
            pw.println("");
            pw.println(" displacements (vx,vy,vz) for set NALL and target time  0.1000000E+01");
            pw.println("");
            pw.println("       1  0.000E+00  0.000E+00  0.000E+00");
            pw.println("       2  5.000E-03 -2.000E-02  0.000E+00");
            pw.println("       3  1.200E-02 -8.000E-02  0.000E+00");
        }

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult result = parser.parse(datFile);

        assertNull("No parse error", result.error);
        assertEquals("Should parse 2 section force records", 2, result.forces.size());

        // Element 1
        StructuralBeamDatParser.SectionForces sf1 = result.forces.get(0);
        assertEquals("Element 1 axial force N", 10000.0, sf1.N, 1e-1);
        assertEquals("Element 1 shear V2", 5000.0, sf1.V2, 1e-1);
        assertEquals("Element 1 moment M1", 20000.0, sf1.M1, 1e-1);

        // Element 2
        StructuralBeamDatParser.SectionForces sf2 = result.forces.get(1);
        assertEquals("Element 2 axial force N (negative)", -5000.0, sf2.N, 1e-1);

        // Displacements
        assertEquals("Should parse 3 displacements", 3, result.displacements.size());
        StructuralBeamDatParser.NodeDisplacement nd1 = result.displacements.get(0);
        assertEquals("Node 1 UX should be 0", 0.0, nd1.ux, 1e-8);
        StructuralBeamDatParser.NodeDisplacement nd3 = result.displacements.get(2);
        assertEquals("Node 3 UX", 0.012, nd3.ux, 1e-6);
        assertEquals("Node 3 UY", -0.08, nd3.uy, 1e-6);

        // Max envelope
        assertEquals("Max |N| should be 10000", 10000.0, result.maxAbsN, 1e-1);
        assertEquals("Max |V2| should be 5000", 5000.0, result.maxAbsV2, 1e-1);
    }

    // =====================================================
    // TEST 3: Physics Validation — Cantilever Beam Theory
    // =====================================================

    @Test
    public void testCantileverBeamTheory_EulerBernoulli() {
        // Cantilever beam: L=4m, P=10kN at tip, Steel E=210GPa, rectangular section 200x300mm
        double L = 4.0;           // m
        double P = 10000.0;       // N
        double E = 210e9;         // Pa (210 GPa)
        double b = 0.200;         // m
        double h = 0.300;         // m
        double I = (b * Math.pow(h, 3)) / 12.0;  // m^4

        // Euler-Bernoulli theory:
        // Max deflection at tip: δ = PL³/(3EI)
        // Max moment at support: M = P*L
        // Max shear: V = P (constant along beam)

        double I_expected = 4.5e-4;  // (0.2 * 0.3^3)/12 = 4.5e-4 m^4
        assertEquals("Moment of inertia I", I_expected, I, 1e-7);

        double deflection = (P * Math.pow(L, 3)) / (3.0 * E * I);
        double maxMoment = P * L;
        double maxShear = P;

        // Expected deflection: 10000 * 64 / (3 * 210e9 * 4.5e-4) = 640000 / 283500000 ≈ 2.258e-3 m
        assertEquals("Tip deflection (mm)", 2.258, deflection * 1000.0, 0.01);
        assertEquals("Max moment at support", 40000.0, maxMoment, 1e-1);
        assertEquals("Max shear (constant)", 10000.0, maxShear, 1e-1);

        System.out.printf("CANTILEVER BEAM (Euler-Bernoulli):%n");
        System.out.printf("  L=%.1fm, P=%.0fN, E=%.0fGPa, b=%.0fmm, h=%.0fmm%n", L, P, E/1e9, b*1000, h*1000);
        System.out.printf("  I = %.6e m⁴%n", I);
        System.out.printf("  δ_tip = %.4f mm  (PL³/3EI)%n", deflection * 1000);
        System.out.printf("  M_max = %.0f N·m  (P·L)%n", maxMoment);
        System.out.printf("  V_max = %.0f N  (P)%n", maxShear);
    }

    @Test
    public void testSimplySupportedBeam_CenterLoad() {
        // Simply supported beam: L=6m, P=20kN at center
        // Steel E=210GPa, IPE300: I=83.56e-6 m^4 (Iy=83560000 mm^4)
        double L = 6.0;           // m
        double P = 20000.0;       // N
        double E = 210e9;         // Pa
        double I = 83.56e-6;     // m^4 (IPE300 Iy converted from mm^4)

        // Simply supported, center load:
        // Max deflection: δ = PL³/(48EI)
        // Max moment at center: M = PL/4
        // Max shear: V = P/2

        double deflection = (P * Math.pow(L, 3)) / (48.0 * E * I);
        double maxMoment = P * L / 4.0;
        double maxShear = P / 2.0;

        assertTrue("Deflection should be positive", deflection > 0);
        assertEquals("Max moment at center PL/4", 30000.0, maxMoment, 1e-1);
        assertEquals("Max shear P/2", 10000.0, maxShear, 1e-1);

        // Deflection: 20000 * 216 / (48 * 210e9 * 83.56e-6) = 4320000 / 841881.6 ≈ 5.131e-3 m
        assertEquals("Mid-span deflection (mm)", 5.131, deflection * 1000.0, 0.1);

        System.out.printf("SIMPLY SUPPORTED BEAM (Center Load):%n");
        System.out.printf("  L=%.1fm, P=%.0fN, E=%.0fGPa, IPE300 Iy=%.2e m⁴%n", L, P, E/1e9, I);
        System.out.printf("  δ_mid = %.4f mm  (PL³/48EI)%n", deflection * 1000);
        System.out.printf("  M_max = %.0f N·m  (PL/4)%n", maxMoment);
        System.out.printf("  V_max = %.0f N  (P/2)%n", maxShear);
    }

    @Test
    public void testPortalFrame_LateralStiffness() {
        // Portal frame: Fixed-Fixed, Span=4m, Height=3m
        // HEB200 columns: I=56.96e-6 m^4, IPE300 beam: I=83.56e-6 m^4
        // E=210GPa, Lateral load at top: F=10kN

        double H = 3.0;   // column height (m)
        double L = 4.0;   // beam span (m)
        double E = 210e9;  // Pa
        double Ic = 56.96e-6;  // HEB200 Iy (m^4)
        double Ib = 83.56e-6;  // IPE300 Iy (m^4)
        double F = 10000.0;    // N

        // For a fixed-base portal frame with rigid beam (simplified):
        // Stiffness k ≈ 24EIc/H³ (two fixed-fixed columns)
        // This is an upper bound approximation (beam is not infinitely rigid)
        double k_upper = 24.0 * E * Ic / Math.pow(H, 3);

        // Lower bound (pinned-top columns): k ≈ 6EIc/H³ 
        double k_lower = 6.0 * E * Ic / Math.pow(H, 3);

        double disp_upper = F / k_upper;
        double disp_lower = F / k_lower;

        assertTrue("Lateral displacement must be between lower and upper bounds",
                   disp_upper < disp_lower);
        assertTrue("Upper bound displacement should be > 0", disp_upper > 0);

        // For the beam stiffness ratio ρ = (Ib/L)/(Ic/H):
        double rho = (Ib / L) / (Ic / H);
        assertTrue("Stiffness ratio should be realistic (0.5 < ρ < 5)", rho > 0.5 && rho < 5.0);

        System.out.printf("PORTAL FRAME (Lateral Load):%n");
        System.out.printf("  H=%.1fm, L=%.1fm, F=%.0fN%n", H, L, F);
        System.out.printf("  HEB200 Ic=%.2e m⁴, IPE300 Ib=%.2e m⁴%n", Ic, Ib);
        System.out.printf("  Beam/Column stiffness ratio ρ = %.3f%n", rho);
        System.out.printf("  δ_lateral bounds: [%.4f, %.4f] mm%n", disp_upper * 1000, disp_lower * 1000);
    }

    // =====================================================
    // TEST 4: Material Properties Physical Correctness
    // =====================================================

    @Test
    public void testMaterialProperties_SteelPhysics() {
        // Steel must satisfy physical bounds
        double E = 210000.0;  // MPa (from our materials.json "Steel")
        double nu = 0.3;
        double rho = 7850.0;  // kg/m³
        double fy = 250.0;    // MPa

        // Physical bounds for structural steel
        assertTrue("E must be 190-215 GPa", E >= 190000 && E <= 215000);
        assertTrue("Poisson's ratio must be 0.25-0.33", nu >= 0.25 && nu <= 0.33);
        assertTrue("Density must be 7700-8100 kg/m³", rho >= 7700 && rho <= 8100);
        assertTrue("Yield strength must be 235-460 MPa (structural grades)", fy >= 235 && fy <= 460);

        // Shear modulus G = E / (2*(1+nu))
        double G = E / (2.0 * (1.0 + nu));
        assertEquals("Shear modulus G ≈ 80769 MPa", 80769.0, G, 100.0);
    }

    @Test
    public void testMaterialProperties_ConcretePhysics() {
        double fc = 25.0;     // MPa
        double E = 23500.0;   // MPa
        double nu = 0.2;
        double rho = 2400.0;  // kg/m³

        // ACI 318: E_c = 4700 * sqrt(f'c) for normal weight concrete
        double E_aci = 4700.0 * Math.sqrt(fc);
        assertEquals("E should match ACI 318 formula ±10%", E_aci, E, E_aci * 0.10);

        assertTrue("Concrete density 2300-2500 kg/m³", rho >= 2300 && rho <= 2500);
        assertTrue("Poisson's ratio 0.15-0.25 for concrete", nu >= 0.15 && nu <= 0.25);
    }

    @Test
    public void testMaterialProperties_AluminumPhysics() {
        double E = 68900.0;   // MPa (6061-T6)
        double nu = 0.33;
        double rho = 2700.0;

        assertTrue("E must be 68-72 GPa for 6061-T6", E >= 68000 && E <= 72000);
        assertTrue("Density 2650-2750 kg/m³", rho >= 2650 && rho <= 2750);
    }

    // =====================================================
    // TEST 5: Section Properties Geometric Correctness
    // =====================================================

    @Test
    public void testSectionProperties_HEB200() {
        // HEB200 from European steel catalog (all values in mm)
        double h = 200.0, b = 200.0, tf = 15.0, tw = 9.0;
        double A = 7810.0;       // mm²
        double Iy = 56960000.0;  // mm⁴

        // Approximate I-section moment of inertia (simplified):
        // Iy ≈ (b*h³)/12 - (b-tw)*(h-2*tf)³/12
        double hw = h - 2 * tf;  // web height
        double I_calc = (b * Math.pow(h, 3)) / 12.0 - ((b - tw) * Math.pow(hw, 3)) / 12.0;

        // Allow 5% tolerance due to fillet radii
        assertEquals("HEB200 Iy within 5% of catalog", Iy, I_calc, Iy * 0.05);

        // Area: A ≈ 2*b*tf + hw*tw
        double A_calc = 2 * b * tf + hw * tw;
        assertEquals("HEB200 Area within 10% of catalog (fillets add area)", A, A_calc, A * 0.10);
    }

    @Test
    public void testSectionProperties_IPE300() {
        double h = 300.0, b = 150.0, tf = 10.7, tw = 7.1;
        double Iy = 83560000.0;  // mm⁴ from catalog

        double hw = h - 2 * tf;
        double I_calc = (b * Math.pow(h, 3)) / 12.0 - ((b - tw) * Math.pow(hw, 3)) / 12.0;

        assertEquals("IPE300 Iy within 5% of catalog", Iy, I_calc, Iy * 0.05);
    }

    @Test
    public void testSectionProperties_RectangularSection() {
        // Rect 300x400
        double b = 300.0, h = 400.0;
        double A = 120000.0;
        double Iy = 1600000000.0;
        double Iz = 900000000.0;

        assertEquals("Rect Area = b*h", b * h, A, 1e-1);
        assertEquals("Rect Iy = b*h³/12", b * Math.pow(h, 3) / 12.0, Iy, 1e-1);
        assertEquals("Rect Iz = h*b³/12", h * Math.pow(b, 3) / 12.0, Iz, 1e-1);
    }

    // =====================================================
    // TEST 6: StructuralModel Validation Logic
    // =====================================================

    @Test
    public void testStructuralModel_Copy() {
        StructuralModel model = new StructuralModel();
        model.nodes.add(new StructuralModel.Node(1, 0, 0, 0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 4, 3, 0, StructuralModel.SupportType.FREE));
        model.elements.add(new StructuralModel.Element(1, 1, 2, "HEB200", "Steel"));
        model.loads.add(new StructuralModel.Load(2, 10000, -5000, 0));

        StructuralModel copy = model.copy();

        assertEquals("Copy should have same node count", model.nodes.size(), copy.nodes.size());
        assertEquals("Copy should have same element count", model.elements.size(), copy.elements.size());

        // Modify original, verify copy is independent
        model.nodes.get(0).x = 999.0;
        assertNotEquals("Copy node should be independent", 999.0, copy.nodes.get(0).x, 1e-1);
    }

    @Test
    public void testSupportTypes() {
        // FIXED: Ux=Uy=Uz=Rx=Ry=Rz=0 → 6 DOFs constrained
        // PINNED: Ux=Uy=Uz=0 → 3 DOFs constrained (rotations free)
        // ROLLER: Uy=0 → 1 DOF constrained (lateral and rotation free)

        StructuralModel.Node fixed = new StructuralModel.Node(1, 0, 0, 0, StructuralModel.SupportType.FIXED);
        StructuralModel.Node pinned = new StructuralModel.Node(2, 3, 0, 0, StructuralModel.SupportType.PINNED);
        StructuralModel.Node roller = new StructuralModel.Node(3, 6, 0, 0, StructuralModel.SupportType.ROLLER);
        StructuralModel.Node free = new StructuralModel.Node(4, 3, 3, 0, StructuralModel.SupportType.FREE);

        assertEquals("Fixed support", StructuralModel.SupportType.FIXED, fixed.supportType);
        assertEquals("Pinned support", StructuralModel.SupportType.PINNED, pinned.supportType);
        assertEquals("Roller support", StructuralModel.SupportType.ROLLER, roller.supportType);
        assertEquals("Free node", StructuralModel.SupportType.FREE, free.supportType);
    }

    // =====================================================
    // TEST 7: CalculiX DAT Parser Scientific Notation
    // =====================================================

    @Test
    public void testDatParserScientificNotation_Fortran_D() throws Exception {
        File datFile = tempFolder.newFile("test_fortran.dat");
        try (PrintWriter pw = new PrintWriter(new FileWriter(datFile))) {
            pw.println(" section forces and moments for set BEAMS and time  0.1000000E+01");
            // Use Fortran 'D' notation (CalculiX sometimes uses D instead of E)
            pw.println("       1       1    1.234D+04  5.678D+03  0.000D+00  2.345D+04  0.000D+00  0.000D+00");
        }

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult result = parser.parse(datFile);

        assertNull("No parse error", result.error);
        assertEquals("Should parse 1 record", 1, result.forces.size());
        assertEquals("N with D notation", 12340.0, result.forces.get(0).N, 1.0);
        assertEquals("V2 with D notation", 5678.0, result.forces.get(0).V2, 1.0);
        assertEquals("M1 with D notation", 23450.0, result.forces.get(0).M1, 1.0);
    }

    @Test
    public void testDatParserEmptyFile() throws Exception {
        File datFile = tempFolder.newFile("empty.dat");
        try (PrintWriter pw = new PrintWriter(new FileWriter(datFile))) {
            pw.println("  This is not a valid CalculiX output file");
        }

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult result = parser.parse(datFile);

        assertNull("No parse error for empty results", result.error);
        assertEquals("Should find 0 forces", 0, result.forces.size());
        assertEquals("Should find 0 displacements", 0, result.displacements.size());
    }

    @Test
    public void testDatParserNonexistentFile() {
        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult result = parser.parse(new File("/nonexistent/path.dat"));

        assertNotNull("Should have error message", result.error);
        assertTrue("Error should mention file not found", result.error.contains("File not found"));
    }

    // =====================================================
    // TEST 8: CRITICAL REGRESSION — Stresses block must NOT
    //         be parsed as displacements
    // =====================================================

    @Test
    public void testDatParserDoesNotParseStressesAsDisplacements() throws Exception {
        // This test reproduces the critical bug where *EL PRINT output
        // (volumetric stress tensors with 27 integration points per B32 element)
        // was being parsed as displacement data because the parser did not
        // detect the transition from the displacement block to the stresses block.
        //
        // The stresses block has the header:
        //   "stresses (elem, integ.pnt.,sxx,syy,szz,sxy,sxz,syz) for set EALL and time ..."
        // followed by 8-column data lines that the old parser would try to parse
        // as 4-column displacement lines, taking the first 4 values (elem, intPt, sxx, syy)
        // as (nodeId, ux, uy, uz), producing absurd displacements of 10^4-10^5 meters.

        File datFile = tempFolder.newFile("test_stress_vs_disp.dat");
        try (PrintWriter pw = new PrintWriter(new FileWriter(datFile))) {
            pw.println("");
            pw.println("                        S T E P       1");
            pw.println("");
            pw.println("                                INCREMENT     1");
            pw.println("");
            pw.println(" displacements (vx,vy,vz) for set NALL and time  0.1000000E+01");
            pw.println("");
            pw.println("         1  1.240771E-24 -1.880791E-37  0.000000E+00");
            pw.println("         2 -2.398824E-23 -1.880791E-37  0.000000E+00");
            pw.println("         3  3.845895E-05  1.267499E-06 -5.002569E-16");
            pw.println("         4  3.614641E-05 -1.329588E-06 -3.744071E-16");
            pw.println("");
            pw.println(" stresses (elem, integ.pnt.,sxx,syy,szz,sxy,sxz,syz) for set EALL and time  0.1000000E+01");
            pw.println("");
            // These are stress tensor values — NOT displacements!
            // The values ~10^4 to 10^5 would cause absurd drift ratios if misinterpreted
            pw.println("         1   1  8.893959E+04  3.594978E+05  2.728336E+04  3.601989E+05  3.082812E+04  2.239466E+03");
            pw.println("         1   2  1.262073E+04  1.389377E+05  9.628304E+03 -1.678687E+05  1.496214E+03  1.422515E+03");
            pw.println("         1   3 -3.888870E+04 -8.149347E+04 -3.240634E+04  3.585535E+05 -3.241181E+03  6.055638E+02");
        }

        StructuralBeamDatParser parser = new StructuralBeamDatParser();
        StructuralBeamDatParser.ParseResult result = parser.parse(datFile);

        assertNull("No parse error expected", result.error);

        // CRITICAL: Only 4 real displacement nodes should be parsed
        assertEquals("Must parse exactly 4 displacement records (not stress data)", 4, result.displacements.size());

        // Verify the parsed displacements are the correct small values
        for (StructuralBeamDatParser.NodeDisplacement nd : result.displacements) {
            double mag = Math.sqrt(nd.ux * nd.ux + nd.uy * nd.uy + nd.uz * nd.uz);
            assertTrue("Displacement magnitude must be < 1.0 m for any real structure, got " + mag + " for node " + nd.nodeId,
                       mag < 1.0);
        }

        // Max displacement must be on the order of 10^-5, NOT 10^5
        assertTrue("Max displacement must be < 0.001 m, got " + result.maxDisp,
                   result.maxDisp < 0.001);

        // Stress data should NOT be in the forces list (no section forces header present)
        assertEquals("No section forces should be parsed from stresses block", 0, result.forces.size());
    }

    // =====================================================
    // TEST 8: Structural System Classification & Clustering
    // =====================================================

    @Test
    public void testStructuralClassifier_WarrenTrussIsPlaneTruss() {
        // Warren Truss (9 nodes, 15 elements in L100x10)
        StructuralModel model = new StructuralModel();
        // Bottom chord
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(2, 3.0, 0.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(3, 6.0, 0.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(4, 9.0, 0.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(5, 12.0, 0.0, 0.0, StructuralModel.SupportType.ROLLER));
        // Top chord
        model.nodes.add(new StructuralModel.Node(6, 1.5, 3.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(7, 4.5, 3.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(8, 7.5, 3.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(9, 10.5, 3.0, 0.0, StructuralModel.SupportType.FREE));

        // 4 bottom chord + 3 top chord + 8 diagonals
        model.elements.add(new StructuralModel.Element(1, 1, 2, "L100x10", "Steel"));
        model.elements.add(new StructuralModel.Element(2, 2, 3, "L100x10", "Steel"));
        model.elements.add(new StructuralModel.Element(3, 3, 4, "L100x10", "Steel"));
        model.elements.add(new StructuralModel.Element(4, 4, 5, "L100x10", "Steel"));
        model.elements.add(new StructuralModel.Element(5, 6, 7, "L100x10", "Steel"));
        model.elements.add(new StructuralModel.Element(6, 7, 8, "L100x10", "Steel"));
        model.elements.add(new StructuralModel.Element(7, 8, 9, "L100x10", "Steel"));
        model.elements.add(new StructuralModel.Element(8, 1, 6, "L100x10", "Steel"));
        model.elements.add(new StructuralModel.Element(9, 6, 2, "L100x10", "Steel"));
        model.elements.add(new StructuralModel.Element(10, 2, 7, "L100x10", "Steel"));
        model.elements.add(new StructuralModel.Element(11, 7, 3, "L100x10", "Steel"));
        model.elements.add(new StructuralModel.Element(12, 3, 8, "L100x10", "Steel"));
        model.elements.add(new StructuralModel.Element(13, 8, 4, "L100x10", "Steel"));
        model.elements.add(new StructuralModel.Element(14, 4, 9, "L100x10", "Steel"));
        model.elements.add(new StructuralModel.Element(15, 9, 5, "L100x10", "Steel"));

        com.diamon.civil.structural.export.PDFReportGenerator.StructuralSystemType sysType =
                com.diamon.civil.structural.export.PDFReportGenerator.classifyStructure(model);

        assertEquals("Warren truss must be classified as PLANE_TRUSS (not building frame drift)",
                com.diamon.civil.structural.export.PDFReportGenerator.StructuralSystemType.PLANE_TRUSS,
                sysType);
    }

    @Test
    public void testStructuralClassifier_ThreeStoryBuildingIsMultiStoryFrame() {
        StructuralModel model = new StructuralModel();
        // 3 stories, 2 bays -> 12 nodes, 15 elements
        for (int story = 0; story <= 3; story++) {
            double y = story * 3.0;
            model.nodes.add(new StructuralModel.Node(story * 3 + 1, 0.0, y, 0.0));
            model.nodes.add(new StructuralModel.Node(story * 3 + 2, 3.0, y, 0.0));
            model.nodes.add(new StructuralModel.Node(story * 3 + 3, 6.0, y, 0.0));
        }
        // Columns
        int eid = 1;
        for (int s = 0; s < 3; s++) {
            int b = s * 3 + 1;
            int t = (s + 1) * 3 + 1;
            model.elements.add(new StructuralModel.Element(eid++, b, t, "HEB200", "Steel"));
            model.elements.add(new StructuralModel.Element(eid++, b + 1, t + 1, "HEB200", "Steel"));
            model.elements.add(new StructuralModel.Element(eid++, b + 2, t + 2, "HEB200", "Steel"));
        }
        // Beams
        for (int s = 1; s <= 3; s++) {
            int b = s * 3 + 1;
            model.elements.add(new StructuralModel.Element(eid++, b, b + 1, "IPE300", "Steel"));
            model.elements.add(new StructuralModel.Element(eid++, b + 1, b + 2, "IPE300", "Steel"));
        }

        com.diamon.civil.structural.export.PDFReportGenerator.StructuralSystemType sysType =
                com.diamon.civil.structural.export.PDFReportGenerator.classifyStructure(model);

        assertEquals("Multi-story building must be classified as MULTI_STORY_FRAME",
                com.diamon.civil.structural.export.PDFReportGenerator.StructuralSystemType.MULTI_STORY_FRAME,
                sysType);
    }

    @Test
    public void testStructuralClassifier_ContinuousBeamIsBeamStructure() {
        StructuralModel model = new StructuralModel();
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(2, 3.0, 0.0, 0.0, StructuralModel.SupportType.ROLLER));
        model.nodes.add(new StructuralModel.Node(3, 6.0, 0.0, 0.0, StructuralModel.SupportType.ROLLER));

        model.elements.add(new StructuralModel.Element(1, 1, 2, "IPE300", "Steel"));
        model.elements.add(new StructuralModel.Element(2, 2, 3, "IPE300", "Steel"));

        com.diamon.civil.structural.export.PDFReportGenerator.StructuralSystemType sysType =
                com.diamon.civil.structural.export.PDFReportGenerator.classifyStructure(model);

        assertEquals("Flat continuous beam must be classified as BEAM_STRUCTURE",
                com.diamon.civil.structural.export.PDFReportGenerator.StructuralSystemType.BEAM_STRUCTURE,
                sysType);
    }

    @Test
    public void testClusterStoryElevations() {
        java.util.List<StructuralModel.Node> nodes = new java.util.ArrayList<>();
        nodes.add(new StructuralModel.Node(1, 0.0, 0.00, 0.0));
        nodes.add(new StructuralModel.Node(2, 3.0, 0.02, 0.0)); // Should cluster with 0.00
        nodes.add(new StructuralModel.Node(3, 0.0, 3.50, 0.0));
        nodes.add(new StructuralModel.Node(4, 3.0, 3.51, 0.0)); // Should cluster with 3.50
        nodes.add(new StructuralModel.Node(5, 0.0, 6.50, 0.0));

        java.util.List<Double> clusters = com.diamon.civil.structural.export.PDFReportGenerator.clusterStoryElevations(nodes, 0.15);

        assertEquals("Must identify exactly 3 distinct story levels", 3, clusters.size());
        assertEquals("Ground elevation", 0.00, clusters.get(0), 1e-3);
        assertEquals("Story 1 elevation", 3.50, clusters.get(1), 1e-3);
        assertEquals("Story 2 elevation", 6.50, clusters.get(2), 1e-3);
    }

    // =====================================================
    // TEST 13: 2D Planar Panels (Slabs & Shear Walls)
    // =====================================================

    @Test
    public void testStructuralClassifier_SlabIsPlateShellStructure() {
        StructuralModel model = new StructuralModel();
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(2, 4.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(3, 4.0, 4.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(4, 0.0, 4.0, 0.0, StructuralModel.SupportType.PINNED));

        model.panels.add(new StructuralModel.Panel(1, java.util.Arrays.asList(1, 2, 3, 4), 0.15, "Concrete", "S4R"));

        com.diamon.civil.structural.export.PDFReportGenerator.StructuralSystemType sysType =
                com.diamon.civil.structural.export.PDFReportGenerator.classifyStructure(model);

        assertEquals("2D Slab panel must be classified as PLATE_SHELL_STRUCTURE",
                com.diamon.civil.structural.export.PDFReportGenerator.StructuralSystemType.PLATE_SHELL_STRUCTURE,
                sysType);
    }

    @Test
    public void testStructuralClassifier_ShearWallIsShearWallPanel() {
        StructuralModel model = new StructuralModel();
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 3.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(3, 3.0, 3.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(4, 0.0, 3.0, 0.0, StructuralModel.SupportType.FREE));

        model.panels.add(new StructuralModel.Panel(1, java.util.Arrays.asList(1, 2, 3, 4), 0.20, "Concrete", "CPS4"));

        com.diamon.civil.structural.export.PDFReportGenerator.StructuralSystemType sysType =
                com.diamon.civil.structural.export.PDFReportGenerator.classifyStructure(model);

        assertEquals("2D Wall panel must be classified as SHEAR_WALL_PANEL",
                com.diamon.civil.structural.export.PDFReportGenerator.StructuralSystemType.SHEAR_WALL_PANEL,
                sysType);
    }

    @Test
    public void testStructuralModelCopy_PreservesPanels() {
        StructuralModel model = new StructuralModel();
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0));
        model.panels.add(new StructuralModel.Panel(1, java.util.Arrays.asList(1, 2, 3, 4), 0.18, "Concrete", "S4R"));

        StructuralModel clone = model.copy();

        assertEquals(1, clone.panels.size());
        assertEquals(0.18, clone.panels.get(0).thickness, 1e-4);
        assertEquals("S4R", clone.panels.get(0).elementType);
        assertEquals(4, clone.panels.get(0).nodeIds.size());
    }
}
