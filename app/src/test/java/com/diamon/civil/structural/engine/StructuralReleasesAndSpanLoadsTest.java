package com.diamon.civil.structural.engine;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Comprehensive Unit Tests for Structural Releases, Rotational Stiffness Springs,
 * Element Span Point Loads, Variable & Partial Distributed Loads, and Custom Materials.
 */
public class StructuralReleasesAndSpanLoadsTest {

    @Test
    public void testBothEndsPinned_TrussBehavior() {
        StructuralModel model = new StructuralModel();
        // Fixed supports at Node 1 and Node 2, but both ends of the element have M33 released (pinned-pinned)
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 6.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));

        StructuralModel.Element elem = new StructuralModel.Element(1, 1, 2, "IPE300", "Structural Steel A36");
        elem.releaseStart.m33Released = true;
        elem.releaseStart.m33Stiffness = 0.0;
        elem.releaseEnd.m33Released = true;
        elem.releaseEnd.m33Stiffness = 0.0;
        model.elements.add(elem);

        // Uniform load w = -15 kN/m
        elem.distLoads.add(new StructuralModel.ElementDistLoad(1, 0.0, 1.0, -15000.0, -15000.0));

        FrameAnalysisEngine.AnalysisOutput out = FrameAnalysisEngine.analyze(model);
        assertNotNull(out);
        assertNotNull(out.parseResult);

        StructuralBeamDatParser.SectionForces sf = out.parseResult.forces.get(0);
        assertNotNull(sf);
        // Both end moments must be exactly zero (pinned-pinned)
        assertEquals("Start moment M1 must be 0 for double pin", 0.0, sf.M1 / 1000.0, 0.1);
        assertEquals("End moment M2 must be 0 for double pin", 0.0, sf.M2 / 1000.0, 0.1);

        // Vertical reactions must each be wL/2 = 15000 * 6 / 2 = 45 kN
        double[] r1 = out.reactions.get(1);
        double[] r2 = out.reactions.get(2);
        assertNotNull(r1);
        assertNotNull(r2);
        assertEquals("Reaction R1 must be 45 kN", 45000.0, r1[1], 100.0);
        assertEquals("Reaction R2 must be 45 kN", 45000.0, r2[1], 100.0);
        assertEquals("Total equilibrium satisfied", 0.0, out.residualFy, 1.0);
    }

    @Test
    public void testMultiplePointLoadsAndMomentsOnSpan() {
        StructuralModel model = new StructuralModel();
        // Fixed-Fixed beam of 10m span
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 10.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));

        StructuralModel.Element elem = new StructuralModel.Element(1, 1, 2, "HEB300", "Structural Steel A36");
        // Point load 1: at 25% (x=2.5m), Fy = -30 kN
        elem.pointLoads.add(new StructuralModel.ElementPointLoad(1, 0.25, -30000.0, 0.0, 0.0));
        // Point load 2: at 50% (x=5.0m), Fy = -40 kN, Mz = +10 kN*m
        elem.pointLoads.add(new StructuralModel.ElementPointLoad(1, 0.50, -40000.0, 0.0, 10000.0));
        // Point load 3: at 75% (x=7.5m), Fy = -30 kN, Fx = +20 kN
        elem.pointLoads.add(new StructuralModel.ElementPointLoad(1, 0.75, -30000.0, 20000.0, 0.0));
        model.elements.add(elem);

        FrameAnalysisEngine.AnalysisOutput out = FrameAnalysisEngine.analyze(model);
        assertNotNull(out);

        // Total vertical applied force = -100 kN
        assertEquals("Total Fy load must be -100 kN", -100000.0, out.sumAppliedFy, 1.0);
        assertEquals("Total Fx load must be +20 kN", 20000.0, out.sumAppliedFx, 1.0);

        // Vertical reactions must sum to 100 kN
        double[] r1 = out.reactions.get(1);
        double[] r2 = out.reactions.get(2);
        assertNotNull(r1);
        assertNotNull(r2);
        assertEquals("Sum of vertical reactions = 100 kN", 100000.0, r1[1] + r2[1], 100.0);
        assertEquals("Sum of horizontal reactions = -20 kN", -20000.0, r1[0] + r2[0], 100.0);
        assertEquals("Residual Fy must be 0", 0.0, out.residualFy, 1.0);
        assertEquals("Residual Fx must be 0", 0.0, out.residualFx, 1.0);
    }

    @Test
    public void testTriangularDistributedLoad_FullSpan() {
        StructuralModel model = new StructuralModel();
        // Fixed-Fixed beam of 6m span with triangular load (w1 = 0 at start, w2 = -24 kN/m at end)
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 6.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));

        StructuralModel.Element elem = new StructuralModel.Element(1, 1, 2, "IPE300", "Structural Steel A36");
        elem.distLoads.add(new StructuralModel.ElementDistLoad(1, 0.0, 1.0, 0.0, -24000.0));
        model.elements.add(elem);

        FrameAnalysisEngine.AnalysisOutput out = FrameAnalysisEngine.analyze(model);
        assertNotNull(out);

        // Total load W = 0.5 * 24 kN/m * 6m = 72 kN
        assertEquals("Total vertical load must be -72 kN", -72000.0, out.sumAppliedFy, 100.0);

        // Theoretical Fixed-End Moments for right-triangular load on fixed-fixed beam:
        // M1 (at zero-load end) = w0 * L^2 / 30 = (24000 * 36)/30 = 28.8 kN*m
        // M2 (at peak-load end) = w0 * L^2 / 20 = (24000 * 36)/20 = 43.2 kN*m
        StructuralBeamDatParser.SectionForces sf = out.parseResult.forces.get(0);
        assertNotNull(sf);
        assertEquals("M1 must be ~ 28.8 kN*m", 28.8, Math.abs(sf.M1 / 1000.0), 1.0);
        assertEquals("M2 must be ~ 43.2 kN*m", 43.2, Math.abs(sf.M2 / 1000.0), 1.0);

        // Reactions: R1 = 3/20 * W_tot = 3/20 * 72 = 21.6 kN, R2 = 7/20 * 72 = 50.4 kN
        double[] r1 = out.reactions.get(1);
        double[] r2 = out.reactions.get(2);
        assertNotNull(r1);
        assertNotNull(r2);
        assertEquals("R1 must be ~ 21.6 kN", 21600.0, r1[1], 500.0);
        assertEquals("R2 must be ~ 50.4 kN", 50400.0, r2[1], 500.0);
    }

    @Test
    public void testCombinedReleases_Loads_CustomMaterial() {
        StructuralModel model = new StructuralModel();
        // Custom Material: High-performance Aluminum 7075-T6
        StructuralModel.CustomMaterial customAlum = new StructuralModel.CustomMaterial(
                "Aluminum 7075-T6", 72000.0, 0.33, 2810.0, 503.0, 0.0);
        model.customMaterials.add(customAlum);

        MaterialDatabase db = new MaterialDatabase();
        db.loadCustomMaterials(model.customMaterials);

        // Cantilever 3m span with custom material
        model.nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        model.nodes.add(new StructuralModel.Node(2, 3.0, 0.0, 0.0, StructuralModel.SupportType.FREE));

        StructuralModel.Element elem = new StructuralModel.Element(1, 1, 2, "Rect 100x200", "Aluminum 7075-T6");
        // Semi-rigid torsion and weak axis releases
        elem.releaseStart.m11Released = true;
        elem.releaseStart.m22Released = true;
        // Distributed load on outer half of span (x = 1.5m to 3.0m)
        elem.distLoads.add(new StructuralModel.ElementDistLoad(1, 0.5, 1.0, -8000.0, -8000.0));
        // Point load at tip (x = 1.0)
        elem.pointLoads.add(new StructuralModel.ElementPointLoad(1, 1.0, -5000.0, 0.0, 0.0));
        model.elements.add(elem);

        FrameAnalysisEngine.AnalysisOutput out = FrameAnalysisEngine.analyze(model);
        assertNotNull(out);

        // Total load = (8 kN/m * 1.5m) + 5 kN = 12 + 5 = 17 kN
        assertEquals("Total applied load must be -17 kN", -17000.0, out.sumAppliedFy, 10.0);

        // Support moment magnitude: |M1| = (12 kN * 2.25m) + (5 kN * 3.0m) = 42 kN*m
        StructuralBeamDatParser.SectionForces sf = out.parseResult.forces.get(0);
        assertNotNull(sf);
        assertEquals("Support moment magnitude |M1| must be 42.0 kN*m", 42.0, Math.abs(sf.M1 / 1000.0), 0.5);
        assertEquals("Tip moment M2 must be 0", 0.0, Math.abs(sf.M2 / 1000.0), 0.1);
    }
}
