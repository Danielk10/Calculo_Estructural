package com.diamon.civil.structural.engine;

import org.junit.Test;
import static org.junit.Assert.*;

import com.diamon.civil.structural.export.PDFReportGenerator;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit Test for FrameAnalysisEngine:
 * Validates direct stiffness calculations, exact Timoshenko formulations,
 * static equilibrium, continuity of member internal actions, and prevention
 * of spurious numerical stress concentrations.
 */
public class FrameAnalysisEngineTest {

    @Test
    public void testThreeStoryBuilding_LateralSeismicLoad_90kN() {
        StructuralModel model = new StructuralModel();

        // 12 Nodes: 2 bays (3m each) x 3 stories (3m each)
        // Level 0: y = 0
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 3.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(3, 6.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        // Level 1: y = 3
        model.nodes.add(new StructuralModel.Node(4, 0.0, 3.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(5, 3.0, 3.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(6, 6.0, 3.0, 0.0, StructuralModel.SupportType.FREE));
        // Level 2: y = 6
        model.nodes.add(new StructuralModel.Node(7, 0.0, 6.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(8, 3.0, 6.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(9, 6.0, 6.0, 0.0, StructuralModel.SupportType.FREE));
        // Level 3: y = 9
        model.nodes.add(new StructuralModel.Node(10, 0.0, 9.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(11, 3.0, 9.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(12, 6.0, 9.0, 0.0, StructuralModel.SupportType.FREE));

        // 15 Elements: 9 Columns (HEB200) + 6 Beams (IPE300)
        // Story 1 Columns
        model.elements.add(new StructuralModel.Element(1, 1, 4, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(2, 2, 5, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(3, 3, 6, "HEB200", "Structural Steel A36"));
        // Story 2 Columns
        model.elements.add(new StructuralModel.Element(4, 4, 7, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(5, 5, 8, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(6, 6, 9, "HEB200", "Structural Steel A36"));
        // Story 3 Columns
        model.elements.add(new StructuralModel.Element(7, 7, 10, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(8, 8, 11, "HEB200", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(9, 9, 12, "HEB200", "Structural Steel A36"));
        // Story 1 Beams
        model.elements.add(new StructuralModel.Element(10, 4, 5, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(11, 5, 6, "IPE300", "Structural Steel A36"));
        // Story 2 Beams
        model.elements.add(new StructuralModel.Element(12, 7, 8, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(13, 8, 9, "IPE300", "Structural Steel A36"));
        // Story 3 Beams
        model.elements.add(new StructuralModel.Element(14, 10, 11, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(15, 11, 12, "IPE300", "Structural Steel A36"));

        // Lateral seismic pattern: +15 kN at Level 1, +30 kN at Level 2, +45 kN at Level 3 (Total = +90 kN)
        model.loads.add(new StructuralModel.Load(4, 15000.0, 0.0, 0.0));
        model.loads.add(new StructuralModel.Load(7, 30000.0, 0.0, 0.0));
        model.loads.add(new StructuralModel.Load(10, 45000.0, 0.0, 0.0));

        FrameAnalysisEngine.AnalysisOutput out = FrameAnalysisEngine.analyze(model);
        assertNotNull(out);
        assertNotNull(out.parseResult);
        assertNull(out.parseResult.error);

        // 1. Static Equilibrium Verification
        assertEquals("Total applied lateral load must be +90 kN", 90000.0, out.sumAppliedFx, 1e-3);
        assertEquals("Total horizontal reaction must be -90 kN", -90000.0, out.sumReactRx, 1e-1);
        assertEquals("Equilibrium residual Fx must be zero", 0.0, out.residualFx, 1e-1);
        assertEquals("Equilibrium residual Fy must be zero", 0.0, out.residualFy, 1e-1);

        // 2. Displacements & Sway Verification
        Map<Integer, StructuralBeamDatParser.NodeDisplacement> dispMap = new HashMap<>();
        for (StructuralBeamDatParser.NodeDisplacement d : out.parseResult.displacements) {
            dispMap.put(d.nodeId, d);
        }

        // Base nodes must have strictly zero displacement
        assertEquals(0.0, dispMap.get(1).ux, 1e-8);
        assertEquals(0.0, dispMap.get(2).ux, 1e-8);
        assertEquals(0.0, dispMap.get(3).ux, 1e-8);

        // Story 1 lateral drift (Nodes 4, 5, 6): ~9.1 mm
        double ux4_mm = dispMap.get(4).ux * 1000.0;
        double ux5_mm = dispMap.get(5).ux * 1000.0;
        double ux6_mm = dispMap.get(6).ux * 1000.0;
        assertTrue("Story 1 sway must be ~9 mm", ux4_mm > 8.0 && ux4_mm < 10.0);
        assertEquals("Story 1 floor nodes must have nearly identical sway", ux4_mm, ux5_mm, 0.1);
        assertEquals("Story 1 floor nodes must have nearly identical sway", ux4_mm, ux6_mm, 0.1);

        // Story 2 lateral drift (Nodes 7, 8, 9): ~19.5 mm
        double ux7_mm = dispMap.get(7).ux * 1000.0;
        double ux8_mm = dispMap.get(8).ux * 1000.0;
        double ux9_mm = dispMap.get(9).ux * 1000.0;
        assertTrue("Story 2 sway must be ~19.5 mm", ux7_mm > 18.0 && ux7_mm < 21.0);
        assertEquals("Story 2 floor nodes must have nearly identical sway", ux7_mm, ux8_mm, 0.15);
        assertEquals("Story 2 floor nodes must have nearly identical sway", ux7_mm, ux9_mm, 0.15);

        // Story 3 lateral drift (Nodes 10, 11, 12): ~26.1 mm
        double ux10_mm = dispMap.get(10).ux * 1000.0;
        double ux11_mm = dispMap.get(11).ux * 1000.0;
        double ux12_mm = dispMap.get(12).ux * 1000.0;
        assertTrue("Story 3 sway must be ~26 mm", ux10_mm > 24.0 && ux10_mm < 28.0);
        assertEquals("Story 3 floor nodes must have nearly identical sway", ux10_mm, ux11_mm, 0.2);
        assertEquals("Story 3 floor nodes must have nearly identical sway", ux10_mm, ux12_mm, 0.2);

        // 3. Member Internal Forces & Continuity Verification
        Map<Integer, StructuralBeamDatParser.SectionForces> forceMap = new HashMap<>();
        for (StructuralBeamDatParser.SectionForces sf : out.parseResult.forces) {
            forceMap.put(sf.elementId, sf);
        }

        // Peak Frame Axial Force must be around ~78.86 kN (Windward column tension)
        // MUST NEVER BE +11,556.40 kN!
        double maxAbsN_kN = out.parseResult.maxAbsN / 1000.0;
        assertTrue("Peak Frame Axial Force must be between 70 kN and 90 kN (was " + maxAbsN_kN + " kN)",
                maxAbsN_kN >= 70.0 && maxAbsN_kN <= 90.0);

        // Story 1 Columns
        StructuralBeamDatParser.SectionForces col1 = forceMap.get(1);
        StructuralBeamDatParser.SectionForces col2 = forceMap.get(2);
        StructuralBeamDatParser.SectionForces col3 = forceMap.get(3);

        assertEquals("Windward column 1 in tension", 78.86, col1.N / 1000.0, 2.0);
        assertEquals("Central column 2 near neutral axis", 0.0, col2.N / 1000.0, 2.0);
        assertEquals("Leeward column 3 in compression", -78.61, col3.N / 1000.0, 2.0);

        // Shear sum in Story 1 columns must balance total 90 kN lateral load
        double totalStory1Shear = (col1.V2 + col2.V2 + col3.V2) / 1000.0;
        assertEquals("Story 1 columns must carry exactly 90 kN total base shear", 90.0, totalStory1Shear, 0.5);

        // Story 3 Columns (Element 7: Node 7 to Node 10)
        StructuralBeamDatParser.SectionForces col7 = forceMap.get(7);
        assertNotNull(col7);
        assertEquals("Element 7 axial force must be ~12.8 kN (NOT 11,556 kN!)", 12.83, col7.N / 1000.0, 2.0);
    }

    @Test
    public void testCantileverBeam_DirectStiffness() {
        StructuralModel model = new StructuralModel();
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 4.0, 0.0, 0.0, StructuralModel.SupportType.FREE));

        model.elements.add(new StructuralModel.Element(1, 1, 2, "RECT_200x300", "Structural Steel A36"));
        model.loads.add(new StructuralModel.Load(2, 0.0, -10000.0, 0.0));

        FrameAnalysisEngine.AnalysisOutput out = FrameAnalysisEngine.analyze(model);
        assertNotNull(out);

        // Tip deflection: δ = (P*L^3)/(3*E*I) * (1 + Phi)
        // L=4m, P=10kN, E=200GPa, b=0.2m, h=0.3m, I = 0.2*0.3^3/12 = 4.5e-4 m4
        // E*I = 200e9 * 4.5e-4 = 9.0e7 N*m2
        // δ_EB = 10000 * 64 / (3 * 9.0e7) = 2.370 mm
        Map<Integer, StructuralBeamDatParser.NodeDisplacement> dispMap = new HashMap<>();
        for (StructuralBeamDatParser.NodeDisplacement d : out.parseResult.displacements) dispMap.put(d.nodeId, d);

        double tipUy_mm = Math.abs(dispMap.get(2).uy) * 1000.0;
        assertTrue("Cantilever tip deflection must be ~2.4 mm (was " + tipUy_mm + ")", tipUy_mm > 2.3 && tipUy_mm < 2.5);

        // Member forces: |V2| = 10 kN, |M1| at base = 40 kN*m, |M2| at tip = 0
        StructuralBeamDatParser.SectionForces sf = out.parseResult.forces.get(0);
        assertEquals("Shear force magnitude must be 10 kN", 10000.0, Math.abs(sf.V2), 1.0);
        assertEquals("Support moment magnitude must be 40 kN*m", 40000.0, Math.abs(sf.M1), 1.0);
        assertEquals("Tip moment must be 0", 0.0, Math.abs(sf.M2), 1e-3);
    }

    @Test
    public void testOverhangingBeam_ExactDeflection() {
        StructuralModel model = new StructuralModel();
        // Node 1: Left support (Pinned)
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        // Node 2: Intermediate support (Roller)
        model.nodes.add(new StructuralModel.Node(2, 4.0, 0.0, 0.0, StructuralModel.SupportType.ROLLER));
        // Node 3: Overhanging cantilever tip (Free)
        model.nodes.add(new StructuralModel.Node(3, 6.0, 0.0, 0.0, StructuralModel.SupportType.FREE));

        // Elements (IPE300)
        model.elements.add(new StructuralModel.Element(1, 1, 2, "IPE300", "Structural Steel A36"));
        model.elements.add(new StructuralModel.Element(2, 2, 3, "IPE300", "Structural Steel A36"));

        // 15 kN tip load at node 3
        model.loads.add(new StructuralModel.Load(3, 0.0, -15000.0, 0.0));

        FrameAnalysisEngine.AnalysisOutput out = FrameAnalysisEngine.analyze(model);
        assertNotNull(out);

        Map<Integer, StructuralBeamDatParser.NodeDisplacement> dispMap = new HashMap<>();
        for (StructuralBeamDatParser.NodeDisplacement d : out.parseResult.displacements) dispMap.put(d.nodeId, d);

        // Theoretical Euler-Bernoulli deflection = 7.180 mm + Timoshenko shear deformation = 7.311 mm
        double tipUy_mm = dispMap.get(3).uy * 1000.0;
        assertEquals("Overhang tip deflection must be ~ -7.2 to -7.3 mm (NOT -5.15 mm)", -7.25, tipUy_mm, 0.15);

        // Member forces verification
        Map<Integer, StructuralBeamDatParser.SectionForces> forceMap = new HashMap<>();
        for (StructuralBeamDatParser.SectionForces f : out.parseResult.forces) forceMap.put(f.elementId, f);

        StructuralBeamDatParser.SectionForces e1 = forceMap.get(1);
        StructuralBeamDatParser.SectionForces e2 = forceMap.get(2);

        assertEquals("Span 1 shear V2 = -7.50 kN", -7500.0, e1.V2, 1.0);
        assertEquals("Overhang shear V2 = +15.00 kN", 15000.0, e2.V2, 1.0);
        assertEquals("Span 1 M at node 1 = 0.0 kN*m", 0.0, e1.M1 / 1000.0, 0.01);
        assertEquals("Span 1 M at node 2 = +30.0 kN*m", 30.0, e1.M2 / 1000.0, 0.01);
        assertEquals("Overhang M at node 2 = +30.0 kN*m", 30.0, e2.M1 / 1000.0, 0.01);
        assertEquals("Overhang M at tip = 0.0 kN*m", 0.0, e2.M2 / 1000.0, 0.01);
    }

    @Test
    public void testConcreteSlabPlateS4R_CenterPointLoad_40kN() {
        StructuralModel model = new StructuralModel();
        double w = 4.0, l = 4.0, t = 0.15;

        // 3x3 grid (9 nodes)
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(2, w / 2.0, 0.0, 0.0, StructuralModel.SupportType.ROLLER));
        model.nodes.add(new StructuralModel.Node(3, w, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(4, 0.0, l / 2.0, 0.0, StructuralModel.SupportType.ROLLER));
        model.nodes.add(new StructuralModel.Node(5, w / 2.0, l / 2.0, 0.0, StructuralModel.SupportType.FREE));
        model.nodes.add(new StructuralModel.Node(6, w, l / 2.0, 0.0, StructuralModel.SupportType.ROLLER));
        model.nodes.add(new StructuralModel.Node(7, 0.0, l, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(8, w / 2.0, l, 0.0, StructuralModel.SupportType.ROLLER));
        model.nodes.add(new StructuralModel.Node(9, w, l, 0.0, StructuralModel.SupportType.PINNED));

        // Perimeter boundary beams
        model.elements.add(new StructuralModel.Element(1, 1, 2, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(2, 2, 3, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(3, 3, 6, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(4, 6, 9, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(5, 9, 8, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(6, 8, 7, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(7, 7, 4, "Rect 200x300", "Concrete 25 MPa"));
        model.elements.add(new StructuralModel.Element(8, 4, 1, "Rect 200x300", "Concrete 25 MPa"));

        // 4 Quad Shell Panels (S4R)
        model.panels.add(new StructuralModel.Panel(1, java.util.Arrays.asList(1, 2, 5, 4), t, "Concrete 25 MPa", "S4R"));
        model.panels.add(new StructuralModel.Panel(2, java.util.Arrays.asList(2, 3, 6, 5), t, "Concrete 25 MPa", "S4R"));
        model.panels.add(new StructuralModel.Panel(3, java.util.Arrays.asList(4, 5, 8, 7), t, "Concrete 25 MPa", "S4R"));
        model.panels.add(new StructuralModel.Panel(4, java.util.Arrays.asList(5, 6, 9, 8), t, "Concrete 25 MPa", "S4R"));

        // 40 kN concentrated load at center node 5
        model.loads.add(new StructuralModel.Load(5, 0.0, 0.0, -40000.0));

        FrameAnalysisEngine.AnalysisOutput out = FrameAnalysisEngine.analyze(model);
        assertNotNull(out);
        assertNotNull(out.parseResult);

        // 1. Check Center Displacement Uz is physically realistic (~ -1.0 to -1.2 mm)
        Map<Integer, StructuralBeamDatParser.NodeDisplacement> dispMap = new HashMap<>();
        for (StructuralBeamDatParser.NodeDisplacement d : out.parseResult.displacements) {
            dispMap.put(d.nodeId, d);
        }

        double uz5_mm = dispMap.get(5).uz * 1000.0;
        assertTrue("Center node 5 vertical deflection must be ~ -1.08 mm (actual: " + uz5_mm + " mm)",
                uz5_mm < -0.5 && uz5_mm > -2.0);

        // 2. Check Static Equilibrium
        assertEquals(-40000.0, out.sumAppliedFz, 1e-3);
        assertEquals(40000.0, Math.abs(out.sumReactRz), 1e-1);
        assertEquals(0.0, Math.abs(out.residualFz), 1e-1);

        // 3. Check Panel Bending Moments
        assertFalse(out.parseResult.panelForces.isEmpty());
        StructuralBeamDatParser.PanelForces p1 = out.parseResult.panelForces.get(0);
        assertEquals("Mx moment must be 2.50 kN*m/m", 2.50, p1.Mx, 0.05);
        assertEquals("My moment must be 2.13 kN*m/m", 2.13, p1.My, 0.05);
        assertEquals("Mxy moment must be 0.38 kN*m/m", 0.38, p1.Mxy, 0.05);
        assertEquals("Vmax shear must be 5.00 kN/m", 5.00, p1.Vmax, 0.05);
    }

    @Test
    public void testShearWallCPS4_LateralLoad_50kN() {
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

        // 50 kN lateral shear force at node 4
        model.loads.add(new StructuralModel.Load(4, 50000.0, 0.0, 0.0));

        FrameAnalysisEngine.AnalysisOutput out = FrameAnalysisEngine.analyze(model);
        assertNotNull(out);
        assertNotNull(out.parseResult);

        Map<Integer, StructuralBeamDatParser.NodeDisplacement> dispMap = new HashMap<>();
        for (StructuralBeamDatParser.NodeDisplacement d : out.parseResult.displacements) {
            dispMap.put(d.nodeId, d);
        }

        double ux4_mm = dispMap.get(4).ux * 1000.0;
        assertTrue("Top lateral displacement must be realistic (~0.02 to 0.08 mm, actual: " + ux4_mm + " mm)",
                ux4_mm > 0.01 && ux4_mm < 0.15);

        assertEquals(50000.0, out.sumAppliedFx, 1e-3);
        assertEquals(50000.0, Math.abs(out.sumReactRx), 1e-1);

        assertFalse(out.parseResult.panelForces.isEmpty());
        StructuralBeamDatParser.PanelForces p1 = out.parseResult.panelForces.get(0);
        assertEquals("CPS4 element must have zero out-of-plane moment Mx", 0.0, p1.Mx, 1e-4);
        assertEquals("CPS4 element must have zero out-of-plane moment My", 0.0, p1.My, 1e-4);
        assertTrue("In-plane shear stress tauXY must be positive", p1.tauXY > 0.0);
        assertTrue("Wall panel must carry > 90% of total 50 kN lateral shear (actual: " + p1.Vshear_total + " kN)",
                p1.Vshear_total > 45.0);

        // Verify columns carry only residual shear (< 5% of 50 kN) and small base moments (< 2 kN*m)
        for (StructuralBeamDatParser.SectionForces sf : out.parseResult.forces) {
            if (sf.elementId == 1 || sf.elementId == 2) {
                double colShear_kN = Math.abs(sf.V2 / 1000.0);
                double colBaseMoment_kNm = Math.abs(sf.M1 / 1000.0);
                assertTrue("Column " + sf.elementId + " shear must be < 2 kN (actual: " + colShear_kN + " kN)",
                        colShear_kN < 2.0);
                assertTrue("Column " + sf.elementId + " base moment must be < 2 kN*m (actual: " + colBaseMoment_kNm + " kN*m)",
                        colBaseMoment_kNm < 2.0);
            }
        }
    }

    @Test
    public void testBeamWithEndRelease_PinnedHinge_M33Zero() {
        StructuralModel model = new StructuralModel();
        // Fixed-Fixed beam of 6m span with M33 release at Node 2 (becomes propped cantilever / fixed-pinned)
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 6.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));

        StructuralModel.Element elem = new StructuralModel.Element(1, 1, 2, "IPE300", "Structural Steel A36");
        // Release M33 at end J (Node 2) with stiffness = 0 (pure hinge)
        elem.releaseEnd.m33Released = true;
        elem.releaseEnd.m33Stiffness = 0.0;
        model.elements.add(elem);

        // Uniform distributed load of 10 kN/m along the span
        elem.distLoads.add(new StructuralModel.ElementDistLoad(1, 0.0, 1.0, -10000.0, -10000.0));

        FrameAnalysisEngine.AnalysisOutput out = FrameAnalysisEngine.analyze(model);
        assertNotNull(out);
        assertNotNull(out.parseResult);

        // For a fixed-pinned beam under uniform load w:
        // Theoretical Moment at fixed end 1: M1 = -wL^2/8 = -(10000 * 36)/8 = -45.0 kN*m
        // Theoretical Moment at pinned end 2: M2 = 0.0 kN*m
        // Reactions: R1 = 5wL/8 = 37.5 kN, R2 = 3wL/8 = 22.5 kN
        StructuralBeamDatParser.SectionForces sf = out.parseResult.forces.get(0);
        assertNotNull(sf);
        assertEquals("Moment at released end J (Node 2) must be exactly 0.0 kN*m", 0.0, Math.abs(sf.M2 / 1000.0), 0.5);
        assertEquals("Moment magnitude at fixed end I (Node 1) must be ~ 45.0 kN*m", 45.0, Math.abs(sf.M1 / 1000.0), 2.0);

        // Check Support Reactions
        double[] r1 = out.reactions.get(1);
        double[] r2 = out.reactions.get(2);
        assertNotNull(r1);
        assertNotNull(r2);
        assertEquals("Vertical reaction R1 must be ~37.5 kN", 37500.0, r1[1], 1000.0);
        assertEquals("Vertical reaction R2 must be ~22.5 kN", 22500.0, r2[1], 1000.0);
        assertEquals("Total vertical equilibrium satisfied", -60000.0, out.sumAppliedFy, 1.0);
        assertEquals("Residual Fy must be 0", 0.0, out.residualFy, 1.0);
    }

    @Test
    public void testBeamWithSemiRigidRelease_RotationalStiffness() {
        StructuralModel model = new StructuralModel();
        // Fixed-Fixed beam of 6m span with Semi-Rigid rotational connection at Node 2
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 6.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));

        StructuralModel.Element elem = new StructuralModel.Element(1, 1, 2, "IPE300", "Structural Steel A36");
        // Semi-rigid connection with stiffness K_theta = 5,000 kN*m/rad = 5.0e6 N*m/rad
        elem.releaseEnd.m33Released = true;
        elem.releaseEnd.m33Stiffness = 5.0e6;
        model.elements.add(elem);

        // Uniform load of 10 kN/m
        elem.distLoads.add(new StructuralModel.ElementDistLoad(1, 0.0, 1.0, -10000.0, -10000.0));

        FrameAnalysisEngine.AnalysisOutput out = FrameAnalysisEngine.analyze(model);
        assertNotNull(out);

        StructuralBeamDatParser.SectionForces sf = out.parseResult.forces.get(0);
        assertNotNull(sf);
        // Moment at Node 2 must be strictly between 0 (pinned) and 30 kN*m (fully fixed, wL^2/12)
        double m2_kNm = Math.abs(sf.M2 / 1000.0);
        assertTrue("Semi-rigid moment must be strictly between 0 and 30 kN*m (actual: " + m2_kNm + " kN*m)",
                m2_kNm > 0.5 && m2_kNm < 29.5);
    }

    @Test
    public void testElementPointLoad_SpanArbitraryPosition() {
        StructuralModel model = new StructuralModel();
        // Cantilever beam 4m span
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 4.0, 0.0, 0.0, StructuralModel.SupportType.FREE));

        StructuralModel.Element elem = new StructuralModel.Element(1, 1, 2, "HEB200", "Structural Steel A36");
        // Concentrated point load on span at position x = 0.5 (2.0 m from start), Fy = -20 kN, Mz = 5 kN*m
        elem.pointLoads.add(new StructuralModel.ElementPointLoad(1, 0.5, -20000.0, 0.0, 5000.0));
        model.elements.add(elem);

        FrameAnalysisEngine.AnalysisOutput out = FrameAnalysisEngine.analyze(model);
        assertNotNull(out);

        // Total applied vertical force must be -20 kN
        assertEquals(-20000.0, out.sumAppliedFy, 1.0);
        // Vertical reaction at Node 1 must be +20 kN
        double[] r1 = out.reactions.get(1);
        assertNotNull(r1);
        assertEquals("Reaction Ry at Node 1 must be +20 kN", 20000.0, r1[1], 100.0);
        assertEquals("Residual Fy must be 0", 0.0, out.residualFy, 1.0);
    }

    @Test
    public void testElementDistributedLoad_VariableTrapezoidal_PartialSpan() {
        StructuralModel model = new StructuralModel();
        // Simply supported beam 8m span
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        model.nodes.add(new StructuralModel.Node(2, 8.0, 0.0, 0.0, StructuralModel.SupportType.ROLLER));

        StructuralModel.Element elem = new StructuralModel.Element(1, 1, 2, "IPE400", "Structural Steel A36");
        // Trapezoidal load from 25% (2m) to 75% (6m), varying from w1 = -10 kN/m to w2 = -20 kN/m
        elem.distLoads.add(new StructuralModel.ElementDistLoad(1, 0.25, 0.75, -10000.0, -20000.0));
        model.elements.add(elem);

        FrameAnalysisEngine.AnalysisOutput out = FrameAnalysisEngine.analyze(model);
        assertNotNull(out);

        // Total load = avg(-15 kN/m) * 4m = -60 kN
        assertEquals("Total applied load must be ~ -60 kN", -60000.0, out.sumAppliedFy, 500.0);

        // Equilibrium check
        double[] r1 = out.reactions.get(1);
        double[] r2 = out.reactions.get(2);
        assertNotNull(r1);
        assertNotNull(r2);
        assertEquals("Sum of vertical reactions must balance total load", 60000.0, (r1[1] + r2[1]), 500.0);
        assertEquals("Residual Fy must be 0", 0.0, out.residualFy, 1.0);
    }

    @Test
    public void testCustomMaterialIntegration() {
        MaterialDatabase db = new MaterialDatabase();
        // Add custom high-strength steel material
        db.addCustomMaterial("Custom Steel Grade 70", 210000.0, 0.28, 7900.0, 485.0, 0.0);

        MaterialDatabase.Material mat = db.getMaterialByName("Custom Steel Grade 70");
        assertNotNull(mat);
        assertEquals(210000.0, mat.E, 1e-3);
        assertEquals(0.28, mat.nu, 1e-3);
        assertEquals(7900.0, mat.rho, 1e-3);
        assertEquals(485.0, mat.yieldStrength, 1e-3);

        // Model with custom material list
        StructuralModel model = new StructuralModel();
        model.customMaterials.add(new StructuralModel.CustomMaterial("Custom Composite Mat", 150000.0, 0.25, 5000.0, 300.0, 0.0));
        db.loadCustomMaterials(model.customMaterials);

        MaterialDatabase.Material loadedMat = db.getMaterialByName("Custom Composite Mat");
        assertNotNull(loadedMat);
        assertEquals(150000.0, loadedMat.E, 1e-3);
        assertEquals(0.25, loadedMat.nu, 1e-3);
    }
}

