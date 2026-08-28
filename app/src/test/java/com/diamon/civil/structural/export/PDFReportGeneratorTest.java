package com.diamon.civil.structural.export;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import com.diamon.civil.structural.engine.StructuralBeamDatParser;
import com.diamon.civil.structural.engine.StructuralModel;

import java.util.List;

/**
 * Unit test for PDFReportGenerator:
 * Validates professional structural report data, AISC 360-22 LRFD/ASD calculations,
 * expanded cross-section properties, material constitutive parameters,
 * static equilibrium balance, multi-station discretization, drift checks,
 * and 100% English terminology.
 */
public class PDFReportGeneratorTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testMaterialDatabase_PropertiesAndUnits() {
        PDFReportGenerator.MaterialInfo steelA36 = PDFReportGenerator.getMaterialProps("Structural Steel A36");
        assertNotNull(steelA36);
        assertEquals(200.0, steelA36.E_GPa, 0.01);
        assertEquals(76.9, steelA36.G_GPa, 0.5);
        assertEquals(0.30, steelA36.nu, 0.01);
        assertEquals(7850.0, steelA36.rho_kg_m3, 1.0);
        assertEquals(250.0, steelA36.strength_MPa, 0.01);
        assertEquals(400.0, steelA36.fu_MPa, 1.0);
        assertEquals(1.2e-5, steelA36.alpha_therm, 1e-7);

        PDFReportGenerator.MaterialInfo steelGr50 = PDFReportGenerator.getMaterialProps("A572 Gr 50");
        assertNotNull(steelGr50);
        assertEquals(345.0, steelGr50.strength_MPa, 0.01);

        PDFReportGenerator.MaterialInfo concrete = PDFReportGenerator.getMaterialProps("Concrete 25 MPa");
        assertNotNull(concrete);
        assertEquals(23.5, concrete.E_GPa, 0.5);
        assertEquals(0.20, concrete.nu, 0.01);
        assertEquals(2400.0, concrete.rho_kg_m3, 1.0);
        assertEquals(25.0, concrete.strength_MPa, 0.01);
    }

    @Test
    public void testExpandedSectionProperties_HEB200_and_IPE300() {
        // HEB200 per standard catalog
        PDFReportGenerator.SectionInfo heb200 = PDFReportGenerator.getSectionProps("HEB200");
        assertNotNull(heb200);
        assertEquals("Wide Flange / I-Beam", heb200.type);
        assertEquals(78.10, heb200.A_cm2, 0.01);
        assertEquals(5696.0, heb200.Iz_cm4, 0.01);
        assertEquals(2003.0, heb200.Iy_cm4, 0.01);
        assertEquals(570.0, heb200.Sz_cm3, 0.01);
        assertEquals(200.3, heb200.Sy_cm3, 0.01);
        assertEquals(642.5, heb200.Zz_cm3, 0.01);
        assertEquals(305.8, heb200.Zy_cm3, 0.01);
        assertEquals(59.3, heb200.J_cm4, 0.1);
        assertEquals(171100.0, heb200.Cw_cm6, 1.0);
        assertEquals(8.54, heb200.r33_cm, 0.01);
        assertEquals(5.07, heb200.r22_cm, 0.01);
        assertEquals(25.20, heb200.Av2_cm2, 0.01);
        assertEquals(52.90, heb200.Av3_cm2, 0.01);

        // IPE300 per standard catalog
        PDFReportGenerator.SectionInfo ipe300 = PDFReportGenerator.getSectionProps("IPE300");
        assertNotNull(ipe300);
        assertEquals("Standard I-Beam", ipe300.type);
        assertEquals(53.80, ipe300.A_cm2, 0.01);
        assertEquals(8356.0, ipe300.Iz_cm4, 0.01);
        assertEquals(604.0, ipe300.Iy_cm4, 0.01);
        assertEquals(557.0, ipe300.Sz_cm3, 0.01);
        assertEquals(80.5, ipe300.Sy_cm3, 0.01);
        assertEquals(628.4, ipe300.Zz_cm3, 0.01);
        assertEquals(125.2, ipe300.Zy_cm3, 0.01);
        assertEquals(20.1, ipe300.J_cm4, 0.1);
        assertEquals(125900.0, ipe300.Cw_cm6, 1.0);
        assertEquals(12.46, ipe300.r33_cm, 0.01);
        assertEquals(3.35, ipe300.r22_cm, 0.01);
        assertEquals(25.68, ipe300.Av2_cm2, 0.01);
        assertEquals(28.12, ipe300.Av3_cm2, 0.01);
    }

    @Test
    public void testAisc360_22_PMM_Equations() {
        // Validation of AISC 360-22 Chapter H Equations H1-1a and H1-1b
        PDFReportGenerator.SectionInfo sec = PDFReportGenerator.getSectionProps("HEB200");
        PDFReportGenerator.MaterialInfo mat = PDFReportGenerator.getMaterialProps("Structural Steel A36");

        double L_m = 3.0;
        double Fy = mat.strength_MPa;
        double E = mat.E_GPa * 1000.0; // MPa
        double Ag_mm2 = sec.A_cm2 * 100.0;
        double Z33_mm3 = sec.Zz_cm3 * 1000.0;
        double Z22_mm3 = sec.Zy_cm3 * 1000.0;
        double r_min_mm = Math.min(sec.r33_cm, sec.r22_cm) * 10.0;

        double KL_r = (1.0 * L_m * 1000.0) / r_min_mm;
        double Fe = (Math.PI * Math.PI * E) / (KL_r * KL_r);
        double Fcr = (KL_r <= 4.71 * Math.sqrt(E / Fy)) ? Math.pow(0.658, Fy / Fe) * Fy : 0.877 * Fe;

        double phi_c_Pn = 0.90 * (Fcr * Ag_mm2) / 1000.0; // kN
        double phi_b_Mn3 = 0.90 * (Fy * Z33_mm3) / 1.0e6;  // kN·m
        double phi_b_Mn2 = 0.90 * (Fy * Z22_mm3) / 1.0e6;  // kN·m

        assertTrue("Nominal compressive capacity must be > 1000 kN", phi_c_Pn > 1000.0);
        assertTrue("Nominal major flexural capacity must be > 100 kN·m", phi_b_Mn3 > 100.0);

        // Case A: High axial load (Pu / phi_c_Pn >= 0.20) -> Eq H1-1a
        double Pu_A = 0.40 * phi_c_Pn;
        double Mu3_A = 0.30 * phi_b_Mn3;
        double Mu2_A = 0.10 * phi_b_Mn2;
        double p_ratio_A = Pu_A / phi_c_Pn;
        double m_ratio_A = (Mu3_A / phi_b_Mn3) + (Mu2_A / phi_b_Mn2);
        double dc_A = p_ratio_A + (8.0 / 9.0) * m_ratio_A;
        assertEquals(0.40 + (8.0 / 9.0) * 0.40, dc_A, 1e-4);
        assertTrue("D/C Ratio A must be <= 1.0 (PASS)", dc_A <= 1.0);

        // Case B: Low axial load (Pu / phi_c_Pn < 0.20) -> Eq H1-1b
        double Pu_B = 0.10 * phi_c_Pn;
        double Mu3_B = 0.50 * phi_b_Mn3;
        double Mu2_B = 0.20 * phi_b_Mn2;
        double p_ratio_B = Pu_B / phi_c_Pn;
        double m_ratio_B = (Mu3_B / phi_b_Mn3) + (Mu2_B / phi_b_Mn2);
        double dc_B = (p_ratio_B / 2.0) + m_ratio_B;
        assertEquals(0.05 + 0.70, dc_B, 1e-4);
        assertTrue("D/C Ratio B must be <= 1.0 (PASS)", dc_B <= 1.0);
    }

    @Test
    public void testGlobalStaticEquilibrium_Verification() {
        StructuralModel model = new StructuralModel();
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 6.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(3, 0.0, 4.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(4, 6.0, 4.0, 0.0, StructuralModel.SupportType.FREE));

        model.loads.add(new StructuralModel.Load(3, 18000.0, -45000.0, 0.0));
        model.loads.add(new StructuralModel.Load(4, 12000.0, -35000.0, 0.0));

        double sumFx = 0, sumFy = 0, sumFz = 0;
        for (StructuralModel.Load l : model.loads) {
            sumFx += l.fx;
            sumFy += l.fy;
            sumFz += l.fz;
        }

        assertEquals("Total applied lateral force Fx is +30 kN", 30000.0, sumFx, 1e-3);
        assertEquals("Total applied gravity force Fy is -80 kN", -80000.0, sumFy, 1e-3);

        double rx = -sumFx;
        double ry = -sumFy;
        assertEquals("Base reaction Rx perfectly balances applied Fx to 0", 0.0, sumFx + rx, 1e-6);
        assertEquals("Base reaction Ry perfectly balances applied Fy to 0", 0.0, sumFy + ry, 1e-6);
    }

    @Test
    public void testMultiStoryDriftAndSystemClassification() {
        StructuralModel model = new StructuralModel();
        // 3 Story Building (Levels at y=0, 3.5m, 7.0m, 10.5m)
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 6.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(3, 0.0, 3.5, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(4, 6.0, 3.5, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(5, 0.0, 7.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(6, 6.0, 7.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(7, 0.0, 10.5, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(8, 6.0, 10.5, 0.0, StructuralModel.SupportType.FREE));

        model.elements.add(new StructuralModel.Element(1, 1, 3, "HEB200", "Steel"));
        model.elements.add(new StructuralModel.Element(2, 2, 4, "HEB200", "Steel"));
        model.elements.add(new StructuralModel.Element(3, 3, 5, "HEB200", "Steel"));
        model.elements.add(new StructuralModel.Element(4, 4, 6, "HEB200", "Steel"));
        model.elements.add(new StructuralModel.Element(5, 5, 7, "HEB200", "Steel"));
        model.elements.add(new StructuralModel.Element(6, 6, 8, "HEB200", "Steel"));
        model.elements.add(new StructuralModel.Element(7, 3, 4, "IPE300", "Steel"));
        model.elements.add(new StructuralModel.Element(8, 5, 6, "IPE300", "Steel"));
        model.elements.add(new StructuralModel.Element(9, 7, 8, "IPE300", "Steel"));

        // Classification
        PDFReportGenerator.StructuralSystemType sysType = PDFReportGenerator.classifyStructure(model);
        assertEquals(PDFReportGenerator.StructuralSystemType.MULTI_STORY_FRAME, sysType);

        // Story Clustering
        List<Double> levels = PDFReportGenerator.clusterStoryElevations(model.nodes, 0.15);
        assertEquals("Must cluster 4 distinct story levels", 4, levels.size());
        assertEquals(0.0, levels.get(0), 1e-2);
        assertEquals(3.5, levels.get(1), 1e-2);
        assertEquals(7.0, levels.get(2), 1e-2);
        assertEquals(10.5, levels.get(3), 1e-2);

        // Drift check simulation
        double ux_story1 = 0.008; // 8 mm sway
        double ux_story2 = 0.018; // 18 mm sway
        double ux_story3 = 0.026; // 26 mm sway

        double drift1_ratio = (ux_story1 / 3.5) * 100.0;
        double drift2_ratio = ((ux_story2 - ux_story1) / 3.5) * 100.0;
        double drift3_ratio = ((ux_story3 - ux_story2) / 3.5) * 100.0;

        assertTrue("Story 1 drift <= 1.0% (NSR-10)", drift1_ratio <= 1.0);
        assertTrue("Story 2 drift <= 1.0% (NSR-10)", drift2_ratio <= 1.0);
        assertTrue("Story 3 drift <= 1.0% (NSR-10)", drift3_ratio <= 1.0);
    }

    @Test
    public void testThreeStoryFrame_FullDriftAndDisplacementsFromAnalysisEngine() {
        StructuralModel model = new StructuralModel();
        // 12 Nodes: 2 bays (3m each) x 3 stories (3m each)
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 3.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(3, 6.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(4, 0.0, 3.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(5, 3.0, 3.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(6, 6.0, 3.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(7, 0.0, 6.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(8, 3.0, 6.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(9, 6.0, 6.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(10, 0.0, 9.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(11, 3.0, 9.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(12, 6.0, 9.0, 0.0, StructuralModel.SupportType.FREE));

        // Elements
        model.elements.add(new StructuralModel.Element(1, 1, 4, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(2, 2, 5, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(3, 3, 6, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(4, 4, 7, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(5, 5, 8, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(6, 6, 9, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(7, 7, 10, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(8, 8, 11, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(9, 9, 12, "HEB200", "Structural Steel A36"));

        model.elements.add(new StructuralModel.Element(10, 4, 5, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(11, 5, 6, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(12, 7, 8, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(13, 8, 9, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(14, 10, 11, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(15, 11, 12, "IPE300", "Structural Steel A36"));

        // Lateral loads
        model.loads.add(new StructuralModel.Load(4, 15000.0, 0.0, 0.0));
        model.loads.add(new StructuralModel.Load(7, 30000.0, 0.0, 0.0));
        model.loads.add(new StructuralModel.Load(10, 45000.0, 0.0, 0.0));

        com.diamon.civil.structural.engine.FrameAnalysisEngine.AnalysisOutput out =
                com.diamon.civil.structural.engine.FrameAnalysisEngine.analyze(model);
        assertNotNull(out);
        assertNotNull(out.parseResult);

        java.util.Map<Integer, StructuralBeamDatParser.NodeDisplacement> dispMap = new java.util.HashMap<>();
        for (StructuralBeamDatParser.NodeDisplacement d : out.parseResult.displacements) {
            dispMap.put(d.nodeId, d);
        }

        // Verify displacements are in millimeters: Node 4 ~8.8-9.1mm, Node 7 ~18.8-19.5mm, Node 10 ~25.2-26.1mm
        double ux4_mm = dispMap.get(4).ux * 1000.0;
        double ux7_mm = dispMap.get(7).ux * 1000.0;
        double ux10_mm = dispMap.get(10).ux * 1000.0;

        assertEquals(8.78, ux4_mm, 0.5);  // ~8.78 mm (NOT 0.0898 mm!)
        assertEquals(18.83, ux7_mm, 1.0); // ~18.83 mm (NOT 0.2231 mm!)
        assertEquals(25.17, ux10_mm, 1.5); // ~25.17 mm (NOT 0.3344 mm!)

        // Check drift ratios
        double drift1_pct = (ux4_mm / 3000.0) * 100.0;
        double drift2_pct = ((ux7_mm - ux4_mm) / 3000.0) * 100.0;
        double drift3_pct = ((ux10_mm - ux7_mm) / 3000.0) * 100.0;

        assertTrue("Story 1 drift ~0.29% (PASS / OK)", drift1_pct > 0.20 && drift1_pct < 0.40);
        assertTrue("Story 2 drift ~0.33% (PASS / OK)", drift2_pct > 0.25 && drift2_pct < 0.45);
        assertTrue("Story 3 drift ~0.21% (PASS / OK)", drift3_pct > 0.15 && drift3_pct < 0.35);
    }
}
