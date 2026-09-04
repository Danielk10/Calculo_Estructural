package com.diamon.civil.terminal.export;

import org.junit.Test;

import static org.junit.Assert.*;

public class TerminalPDFReportGeneratorTest {

    @Test
    public void testParseSessionStatsEmpty() {
        TerminalPDFReportGenerator.SessionStats stats = TerminalPDFReportGenerator.parseSessionStats("");
        assertEquals(0, stats.commandCount);
        assertEquals(0, stats.totalLines);
        assertEquals(0, stats.totalChars);

        TerminalPDFReportGenerator.SessionStats nullStats = TerminalPDFReportGenerator.parseSessionStats(null);
        assertEquals(0, nullStats.commandCount);
        assertEquals(0, nullStats.totalLines);
        assertEquals(0, nullStats.totalChars);
    }

    @Test
    public void testParseSessionStatsWithCommandsAndOutput() {
        String sampleLog = "$ test-calculix\n" +
                "Executing CalculiX Sequential Test (1 Thread / Single-core: test_calculix.inp)...\n" +
                "=== ENGINEERING RESULTS (Unit Cube C3D8 Tension P=400 N) ===\n" +
                "• Applied Axial Stress: σ_z = 400.0 MPa\n" +
                "$ test_gmsh\n" +
                "Executing Gmsh Boolean Operation Test...\n" +
                "$ ls -la\n" +
                "-rw-r--r-- job.inp\n";

        TerminalPDFReportGenerator.SessionStats stats = TerminalPDFReportGenerator.parseSessionStats(sampleLog);
        assertEquals("Should identify 3 user commands starting with $", 3, stats.commandCount);
        assertEquals("Should match total lines", 8, stats.totalLines);
        assertEquals("Should match character count", sampleLog.length(), stats.totalChars);
    }
}
