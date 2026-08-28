package com.diamon.civil.terminal.engine;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import static org.junit.Assert.*;

public class TerminalPipelineRealExecutionTest {

    private static final String CCX_PATH = "/home/danielpdiamon/.local/bin/ccx";
    private static final String GMSH_PATH = "/usr/bin/gmsh";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testGmsh2DMeshingRealExecution() throws Exception {
        System.out.printf("%n=== GMSH 2D MESHING REAL EXECUTION ===%n");
        File workDir = tempFolder.newFolder("gmsh_real_test");
        File geoFile = new File(workDir, "plate.geo");
        File inpFile = new File(workDir, "plate.inp");

        // Simple rectangular plate in Gmsh geo format
        try (PrintWriter pw = new PrintWriter(new FileWriter(geoFile))) {
            pw.println("Point(1) = {0, 0, 0, 0.5};");
            pw.println("Point(2) = {2, 0, 0, 0.5};");
            pw.println("Point(3) = {2, 1, 0, 0.5};");
            pw.println("Point(4) = {0, 1, 0, 0.5};");
            pw.println("Line(1) = {1, 2};");
            pw.println("Line(2) = {2, 3};");
            pw.println("Line(3) = {3, 4};");
            pw.println("Line(4) = {4, 1};");
            pw.println("Curve Loop(1) = {1, 2, 3, 4};");
            pw.println("Plane Surface(1) = {1};");
            pw.println("Physical Surface(\"Eall\") = {1};");
            pw.println("Physical Curve(\"FixedEdge\") = {4};");
        }

        // Run local Gmsh binary: gmsh plate.geo -2 -format inp -o plate.inp
        ProcessBuilder pb = new ProcessBuilder(GMSH_PATH, "plate.geo", "-2", "-format", "inp", "-o", "plate.inp");
        pb.directory(workDir);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        int exitCode = p.waitFor();
        System.out.println("Gmsh Output:\n" + output);
        assertEquals("Gmsh mesher must exit with code 0", 0, exitCode);
        assertTrue("Generated INP mesh must exist", inpFile.exists());
        assertTrue("Generated INP mesh must have content", inpFile.length() > 50);
    }

    @Test
    public void testCalculiXCcxRealExecution() throws Exception {
        System.out.printf("%n=== CALCULIX CCX REAL EXECUTION ===%n");
        File workDir = tempFolder.newFolder("ccx_real_test");
        File inpFile = new File(workDir, "beam_solve.inp");

        try (PrintWriter pw = new PrintWriter(new FileWriter(inpFile))) {
            pw.println("*NODE, NSET=NALL");
            pw.println("1, 0.0, 0.0, 0.0");
            pw.println("2, 4.0, 0.0, 0.0");
            pw.println("*ELEMENT, TYPE=B31, ELSET=Eall");
            pw.println("1, 1, 2");
            pw.println("*BEAM SECTION, ELSET=Eall, MATERIAL=Steel, SECTION=RECT");
            pw.println("0.200, 0.300");
            pw.println("0.0, 0.0, -1.0");
            pw.println("*MATERIAL, NAME=Steel");
            pw.println("*ELASTIC");
            pw.println("210000000000.0, 0.3");
            pw.println("*BOUNDARY");
            pw.println("1, 1, 6, 0.0");
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*CLOAD");
            pw.println("2, 2, -10000.0");
            pw.println("*NODE PRINT, NSET=NALL");
            pw.println("U");
            pw.println("*NODE FILE");
            pw.println("U");
            pw.println("*EL FILE, SECTION FORCES, OUTPUT=2D");
            pw.println("S");
            pw.println("*END STEP");
        }

        // Run local CalculiX binary: ccx beam_solve
        ProcessBuilder pb = new ProcessBuilder(CCX_PATH, "beam_solve");
        pb.directory(workDir);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        int exitCode = p.waitFor();
        System.out.println("CalculiX CCX Output:\n" + output);
        assertEquals("CalculiX CCX must exit with code 0", 0, exitCode);

        File datFile = new File(workDir, "beam_solve.dat");
        File frdFile = new File(workDir, "beam_solve.frd");
        assertTrue("CalculiX must generate .dat file", datFile.exists() && datFile.length() > 0);
        assertTrue("CalculiX must generate .frd file", frdFile.exists() && frdFile.length() > 0);
    }
}
