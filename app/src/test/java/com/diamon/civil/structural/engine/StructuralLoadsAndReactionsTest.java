package com.diamon.civil.structural.engine;

import com.diamon.civil.structural.export.PDFReportGenerator;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.util.Map;

public class StructuralLoadsAndReactionsTest {

    @Test
    public void testBeam4mWithPointLoadAt1mAndTrapezoidalDistributedLoad() {
        System.out.println("=== TEST BEAM 4M WITH POINT LOAD AT 1M & TRAPEZOIDAL DISTRIBUTED LOAD ===");

        // 1. Create 4m beam model: Node 1 at (0,0), Node 2 at (4,0)
        StructuralModel model = new StructuralModel();
        StructuralModel.Node n1 = new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED);
        StructuralModel.Node n2 = new StructuralModel.Node(2, 4.0, 0.0, 0.0, StructuralModel.SupportType.ROLLER);
        model.nodes.add(n1);
        model.nodes.add(n2);

        // 2. Custom material: High-Strength Custom Steel
        String customMatName = "CustomTitaniumSteel";
        double customE = 210000.0; // MPa
        double customNu = 0.28;
        double customRho = 7850.0;
        double customFy = 690.0;
        StructuralModel.CustomMaterial customMat = new StructuralModel.CustomMaterial(
                customMatName, customE, customNu, customRho, customFy, 0.0);
        model.customMaterials.add(customMat);

        // Member
        StructuralModel.Element elem = new StructuralModel.Element(1, 1, 2, "HEB200", customMatName);

        // 3. Point load at 1m along the 4m beam (position = 1m / 4m = 0.25)
        // Force: Fy = -10 kN (-10,000 N)
        elem.pointLoads.add(new StructuralModel.ElementPointLoad(1, 0.25, -10000.0, 0.0, 0.0));

        // Additional point load at 3m (position = 3m / 4m = 0.75), Fy = -20 kN (-20,000 N)
        elem.pointLoads.add(new StructuralModel.ElementPointLoad(1, 0.75, -20000.0, 0.0, 0.0));

        // Concentrated moment at 2m (position = 0.50), Mz = +15 kN*m (+15,000 N*m)
        elem.pointLoads.add(new StructuralModel.ElementPointLoad(1, 0.50, 0.0, 0.0, 15000.0));

        // 4. Variable partial-span distributed load from 1m to 3m (0.25 to 0.75)
        // w1 = -5 kN/m (-5,000 N/m) at 1m, w2 = -15 kN/m (-15,000 N/m) at 3m (trapezoidal)
        elem.distLoads.add(new StructuralModel.ElementDistLoad(1, 0.25, 0.75, -5000.0, -15000.0));

        model.elements.add(elem);

        // 5. Analyze with FrameAnalysisEngine
        FrameAnalysisEngine.AnalysisOutput output = FrameAnalysisEngine.analyze(model);

        assertNotNull("Analysis output should not be null", output);
        assertNotNull("ParseResult should not be null", output.parseResult);
        assertNull("ParseResult error should be null", output.parseResult.error);

        // Total applied vertical load:
        // P1 = -10 kN, P2 = -20 kN
        // Trapezoidal: avg_w = (-5 + -15)/2 = -10 kN/m over 2m span = -20 kN
        // Total expected Fy = -10 - 20 - 20 = -50 kN (-50,000 N)
        System.out.printf("Total Applied Fy: %.3f kN (Expected: -50.000 kN)%n", output.sumAppliedFy / 1000.0);
        assertEquals("Total applied vertical load must be -50 kN", -50000.0, output.sumAppliedFy, 1.0);

        // Check Support Reactions
        assertNotNull("Reactions map must not be null", output.reactions);
        assertTrue("Node 1 must have reactions", output.reactions.containsKey(1));
        assertTrue("Node 2 must have reactions", output.reactions.containsKey(2));

        double[] r1 = output.reactions.get(1); // [Rx, Ry, Rz, Mx, My, Mz]
        double[] r2 = output.reactions.get(2);

        System.out.printf("Node 1 Reaction: Ry=%.3f kN, Mz=%.3f kN*m%n", r1[1] / 1000.0, r1[5] / 1000.0);
        System.out.printf("Node 2 Reaction: Ry=%.3f kN%n", r2[1] / 1000.0);
        System.out.printf("Sum Reactions Ry: %.3f kN%n", output.sumReactRy / 1000.0);
        System.out.printf("Residual Fy: %.6f kN%n", output.residualFy / 1000.0);

        // Global vertical equilibrium: Applied + Reaction = 0
        assertEquals("Residual Fy must be practically zero (Equilibrium satisfied)",
                0.0, output.residualFy, 1e-2);

        // Check PDF Generator Material Properties Resolution
        PDFReportGenerator.MaterialInfo resolvedMat = PDFReportGenerator.getMaterialProps(model, customMatName);
        assertNotNull("Custom material must be resolved by PDF generator", resolvedMat);
        assertEquals("Custom material name must match", customMatName, resolvedMat.name);
        assertEquals("Custom E (GPa) must match 210.0", 210.0, resolvedMat.E_GPa, 0.01);
        assertEquals("Custom Poisson nu must match 0.28", 0.28, resolvedMat.nu, 0.001);
        assertEquals("Custom density rho must match 7850.0", 7850.0, resolvedMat.rho_kg_m3, 0.01);
        assertEquals("Custom yield strength must match 690.0", 690.0, resolvedMat.strength_MPa, 0.01);

        System.out.println("✅ All assertions PASSED: 4m beam with load at 1m, custom material, reactions, and equilibrium!");
    }
}
