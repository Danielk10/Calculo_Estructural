package com.diamon.civil.solids.engine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Shared, solvable reference case used by the Solids UI and the standalone terminal test. */
public final class SampleSimulationCase {
    public static final String CANTILEVER_NAME = "cantilever";

    private SampleSimulationCase() {
    }

    /**
     * Writes a 100 × 10 × 10 mm cantilever with explicit physical surfaces.
     * With P = 100 N, sigma_max ≈ 60 MPa and deflection ≈ 0.20 mm.
     */
    public static File createCantileverGeo(File workDir) throws IOException {
        if (workDir == null || (!workDir.exists() && !workDir.mkdirs())) {
            throw new IOException("Could not prepare working directory");
        }

        File geoFile = new File(workDir, CANTILEVER_NAME + ".geo");
        String script = "SetFactory(\"OpenCASCADE\");\n\n" +
                "// Dimensions in mm: Length = 100 mm, Base = 10 mm, Height = 10 mm\n" +
                "// P = 100 N yields sigma_max ≈ 60 MPa and deflection ≈ 0.2 mm (linear elastic response)\n" +
                "Box(1) = {0, 0, 0, 100, 10, 10};\n\n" +
                "// Fixed boundary constraint surface at X = 0\n" +
                "s() = Surface In BoundingBox{-0.1, -0.1, -0.1, 0.1, 10.1, 10.1};\n" +
                "Physical Surface(\"Fixed\") = s();\n\n" +
                "// Load application boundary surface at X = 100\n" +
                "s2() = Surface In BoundingBox{99.9, -0.1, -0.1, 100.1, 10.1, 10.1};\n" +
                "Physical Surface(\"Loaded\") = s2();\n\n" +
                "// Physical volume domain for CalculiX FEA\n" +
                "Physical Volume(\"Steel\") = {1};\n";

        try (FileOutputStream output = new FileOutputStream(geoFile, false)) {
            output.write(script.getBytes(StandardCharsets.UTF_8));
        }
        return geoFile;
    }

    /**
     * Writes a 100 × 10 × 10 mm cantilever discretized into prismatic wedges (C3D6 / C3D15).
     * With P = 100 N, sigma_max ≈ 60 MPa and deflection ≈ 0.20 mm.
     */
    public static File createCantileverWedgeGeo(File workDir) throws IOException {
        if (workDir == null || (!workDir.exists() && !workDir.mkdirs())) {
            throw new IOException("Could not prepare working directory");
        }

        File geoFile = new File(workDir, CANTILEVER_NAME + "_wedge.geo");
        String script = "Point(1) = {0, 0, 0, 5.0};\n" +
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

        try (FileOutputStream output = new FileOutputStream(geoFile, false)) {
            output.write(script.getBytes(StandardCharsets.UTF_8));
        }
        return geoFile;
    }
}

