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
                "// Dimensiones en mm: Longitud = 100 mm, Base = 10 mm, Altura = 10 mm\n" +
                "// Con P = 100 N produce sigma_max ≈ 60 MPa y flecha ≈ 0.2 mm (comportamiento elástico seguro)\n" +
                "Box(1) = {0, 0, 0, 100, 10, 10};\n\n" +
                "// Superficie de empotramiento en X = 0\n" +
                "s() = Surface In BoundingBox{-0.1, -0.1, -0.1, 0.1, 10.1, 10.1};\n" +
                "Physical Surface(\"Fixed\") = s();\n\n" +
                "// Superficie de aplicación de carga en X = 100\n" +
                "s2() = Surface In BoundingBox{99.9, -0.1, -0.1, 100.1, 10.1, 10.1};\n" +
                "Physical Surface(\"Loaded\") = s2();\n\n" +
                "// Volumen físico para CalculiX\n" +
                "Physical Volume(\"Steel\") = {1};\n";

        try (FileOutputStream output = new FileOutputStream(geoFile, false)) {
            output.write(script.getBytes(StandardCharsets.UTF_8));
        }
        return geoFile;
    }
}
