package com.diamon.civil.terminal.editor;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class FeaTextTokenizerTest {

    @Test
    public void testInpSyntaxTokenization() {
        String inpCode =
                "** Cantilever Beam Model INP\n" +
                "*NODE, NSET=Nall\n" +
                "1, 0.0, 0.0, 0.0\n" +
                "2, 10.0, 0.0, 0.0\n" +
                "*ELEMENT, TYPE=C3D4, ELSET=Eall\n" +
                "1, 1, 2, 3, 4\n" +
                "*MATERIAL, NAME=Steel\n" +
                "*ELASTIC\n" +
                "210000.0, 0.3\n" +
                "*STEP\n" +
                "*STATIC\n" +
                "*CLOAD\n" +
                "NLoad, 2, -500.0\n" +
                "*END STEP\n";

        List<FeaTextToken> tokens = FeaTextTokenizer.tokenize(inpCode, FeaTextTokenizer.SyntaxMode.INP);
        assertNotNull(tokens);
        assertFalse(tokens.isEmpty());

        boolean hasComment = false;
        boolean hasKeyword = false;
        boolean hasParam = false;
        boolean hasNumber = false;

        for (FeaTextToken t : tokens) {
            String snippet = inpCode.substring(t.getStart(), t.getEnd());
            if (t.getType() == FeaTextTokenType.COMMENT && snippet.contains("** Cantilever")) {
                hasComment = true;
            }
            if (t.getType() == FeaTextTokenType.KEYWORD && snippet.contains("*NODE")) {
                hasKeyword = true;
            }
            if (t.getType() == FeaTextTokenType.PARAMETER && snippet.contains("NSET=")) {
                hasParam = true;
            }
            if (t.getType() == FeaTextTokenType.NUMBER && snippet.equals("210000.0")) {
                hasNumber = true;
            }
        }

        assertTrue("Should detect ** comments", hasComment);
        assertTrue("Should detect *NODE keywords", hasKeyword);
        assertTrue("Should detect NSET= parameter", hasParam);
        assertTrue("Should detect numbers", hasNumber);
    }

    @Test
    public void testTclSyntaxTokenization() {
        String tclCode =
                "# DRAWEXE Script for Solid Modeling\n" +
                "pload ALL\n" +
                "set radius 2.5\n" +
                "box b 10 10 10\n" +
                "cylinder c $radius 15\n" +
                "bcut res b c\n" +
                "writebrep res \"output.brep\"\n" +
                "exit\n";

        List<FeaTextToken> tokens = FeaTextTokenizer.tokenize(tclCode, FeaTextTokenizer.SyntaxMode.TCL);
        assertNotNull(tokens);

        boolean hasComment = false;
        boolean hasPload = false;
        boolean hasSet = false;
        boolean hasBox = false;
        boolean hasVariable = false;
        boolean hasString = false;

        for (FeaTextToken t : tokens) {
            String snippet = tclCode.substring(t.getStart(), t.getEnd());
            if (t.getType() == FeaTextTokenType.COMMENT && snippet.startsWith("#")) {
                hasComment = true;
            }
            if (t.getType() == FeaTextTokenType.CAD_COMMAND && snippet.equals("pload")) {
                hasPload = true;
            }
            if (t.getType() == FeaTextTokenType.KEYWORD && snippet.equals("set")) {
                hasSet = true;
            }
            if (t.getType() == FeaTextTokenType.CAD_COMMAND && snippet.equals("box")) {
                hasBox = true;
            }
            if (t.getType() == FeaTextTokenType.VARIABLE && snippet.equals("$radius")) {
                hasVariable = true;
            }
            if (t.getType() == FeaTextTokenType.STRING && snippet.contains("output.brep")) {
                hasString = true;
            }
        }

        assertTrue("Should detect # comment", hasComment);
        assertTrue("Should detect pload", hasPload);
        assertTrue("Should detect set keyword", hasSet);
        assertTrue("Should detect box command", hasBox);
        assertTrue("Should detect $radius variable", hasVariable);
        assertTrue("Should detect string", hasString);
    }

    @Test
    public void testCadGeoSyntaxTokenization() {
        String geoCode =
                "// Gmsh Geometry Script\n" +
                "SetFactory(\"OpenCASCADE\");\n" +
                "Point(1) = {0, 0, 0, 0.1};\n" +
                "Line(1) = {1, 2};\n" +
                "Mesh.MeshSizeMax = 1.0;\n";

        List<FeaTextToken> tokens = FeaTextTokenizer.tokenize(geoCode, FeaTextTokenizer.SyntaxMode.CAD_SCRIPT);
        assertNotNull(tokens);

        boolean hasComment = false;
        boolean hasFactory = false;
        boolean hasPoint = false;
        boolean hasLine = false;

        for (FeaTextToken t : tokens) {
            String snippet = geoCode.substring(t.getStart(), t.getEnd());
            if (t.getType() == FeaTextTokenType.COMMENT && snippet.contains("//")) {
                hasComment = true;
            }
            if (t.getType() == FeaTextTokenType.KEYWORD && snippet.equals("SetFactory")) {
                hasFactory = true;
            }
            if (t.getType() == FeaTextTokenType.KEYWORD && snippet.equals("Point")) {
                hasPoint = true;
            }
            if (t.getType() == FeaTextTokenType.KEYWORD && snippet.equals("Line")) {
                hasLine = true;
            }
        }

        assertTrue("Should detect // comment", hasComment);
        assertTrue("Should detect SetFactory", hasFactory);
        assertTrue("Should detect Point", hasPoint);
        assertTrue("Should detect Line", hasLine);
    }

    @Test
    public void testDetectMode() {
        assertEquals(FeaTextTokenizer.SyntaxMode.INP, FeaTextTokenizer.detectMode("beam.inp"));
        assertEquals(FeaTextTokenizer.SyntaxMode.TCL, FeaTextTokenizer.detectMode("model.tcl"));
        assertEquals(FeaTextTokenizer.SyntaxMode.CAD_SCRIPT, FeaTextTokenizer.detectMode("bracket.geo"));
        assertEquals(FeaTextTokenizer.SyntaxMode.CAD_SCRIPT, FeaTextTokenizer.detectMode("linkrods.step"));
        assertEquals(FeaTextTokenizer.SyntaxMode.GENERIC, FeaTextTokenizer.detectMode("notes.txt"));
    }
}
