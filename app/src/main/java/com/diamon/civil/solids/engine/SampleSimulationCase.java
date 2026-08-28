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
     * Writes a 10 × 1 × 1 cantilever with explicit physical surfaces. Those sets
     * are consumed by {@link SolidInpAssembler}, so the generated CalculiX job always
     * has a real fixed end and a real loaded end.
     */
    public static File createCantileverGeo(File workDir) throws IOException {
        if (workDir == null || (!workDir.exists() && !workDir.mkdirs())) {
            throw new IOException("Could not prepare working directory");
        }

        File geoFile = new File(workDir, CANTILEVER_NAME + ".geo");
        String script = "SetFactory(\"OpenCASCADE\");\n" +
                "Box(1) = {0, 0, 0, 10, 1, 1};\n" +
                "s() = Surface In BoundingBox{-0.1,-0.1,-0.1, 0.1,1.1,1.1};\n" +
                "Physical Surface(\"Fixed\") = s();\n" +
                "s2() = Surface In BoundingBox{9.9,-0.1,-0.1, 10.1,1.1,1.1};\n" +
                "Physical Surface(\"Loaded\") = s2();\n" +
                "Physical Volume(\"Steel\") = {1};\n";

        try (FileOutputStream output = new FileOutputStream(geoFile, false)) {
            output.write(script.getBytes(StandardCharsets.UTF_8));
        }
        return geoFile;
    }
}
