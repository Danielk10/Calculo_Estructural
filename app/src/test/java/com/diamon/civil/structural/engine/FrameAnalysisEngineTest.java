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
}
