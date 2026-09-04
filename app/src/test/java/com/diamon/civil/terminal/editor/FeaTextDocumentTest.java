package com.diamon.civil.terminal.editor;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import java.io.File;

public class FeaTextDocumentTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testDocumentEditingAndMetrics() {
        FeaTextDocument doc = new FeaTextDocument("test.inp");
        assertEquals("test.inp", doc.getFilename());
        assertEquals(FeaTextTokenizer.SyntaxMode.INP, doc.getSyntaxMode());
        assertFalse(doc.isModified());

        doc.setContent("*NODE\n1, 0, 0, 0\n2, 10, 0, 0\n");
        assertTrue(doc.isModified());
        assertEquals(4, doc.getLineCount()); // 3 lines ending with newline -> 4th line is empty line

        int[] lc1 = doc.getLineAndCol(0);
        assertEquals(1, lc1[0]);
        assertEquals(1, lc1[1]);

        int[] lc2 = doc.getLineAndCol(6); // right after "*NODE\n" -> line 2, col 1
        assertEquals(2, lc2[0]);
        assertEquals(1, lc2[1]);

        // Test insert
        int newPos = doc.insert(0, "** Header Comment\n");
        assertTrue(doc.getContent().startsWith("** Header Comment\n*NODE"));
        assertEquals(18, newPos);

        // Test backspace
        int afterBs = doc.backspace(1);
        assertEquals(0, afterBs);
        assertTrue(doc.getContent().startsWith("* Header Comment\n*NODE"));

        // Test delete
        int afterDel = doc.delete(0);
        assertEquals(0, afterDel);
        assertTrue(doc.getContent().startsWith(" Header Comment\n*NODE"));
    }

    @Test
    public void testFileSaveAndLoad() throws Exception {
        File folder = tempFolder.newFolder("featext_tests");
        File file = new File(folder, "cantilever.geo");

        FeaTextDocument doc = new FeaTextDocument();
        doc.setCurrentFile(file);
        doc.setContent("// Cantilever Beam Model\nSetFactory(\"OpenCASCADE\");\nBox(1) = {0, 0, 0, 10, 2, 2};\n");

        doc.saveToFile(file);
        assertTrue(file.exists());
        assertTrue(file.length() > 0);
        assertFalse(doc.isModified());

        FeaTextDocument loadedDoc = new FeaTextDocument();
        loadedDoc.loadFromFile(file);
        assertEquals("cantilever.geo", loadedDoc.getFilename());
        assertEquals(FeaTextTokenizer.SyntaxMode.CAD_SCRIPT, loadedDoc.getSyntaxMode());
        assertEquals(doc.getContent(), loadedDoc.getContent());
        assertFalse(loadedDoc.isModified());
    }

    @Test
    public void testLineNumbersText() {
        FeaTextDocument doc = new FeaTextDocument("simple.tcl");
        doc.setContent("line 1\nline 2\nline 3");
        assertEquals(3, doc.getLineCount());
        assertEquals("1\n2\n3", doc.getLineNumbersText());
    }
}
