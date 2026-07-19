package com.diamon.civil.test.simulation;

import java.io.File;
import java.io.IOException;

public class InpAssembler {
    public static void assemble(File workDir, String inputName) throws IOException {
        com.diamon.civil.engine.InpAssembler.assemble(
                workDir, inputName, "Steel", 210000.0, 0.3, -100.0);
    }
}
