package com.diamon.civil.terminal.engine;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.*;

public class TerminalCommandExecutorTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private TerminalCommandExecutor executor;
    private File rootDir;

    @Before
    public void setUp() throws Exception {
        rootDir = tempFolder.newFolder("terminal_workspace");
        executor = new TerminalCommandExecutor(rootDir);
    }

    @Test
    public void testBasicShellCommands() {
        // 1. pwd
        assertEquals("/", executor.execute("pwd"));

        // 2. mkdir
        String mkdirRes = executor.execute("mkdir project1");
        assertTrue("mkdir should succeed", mkdirRes.contains("created"));
        File projDir = new File(rootDir, "project1");
        assertTrue(projDir.exists() && projDir.isDirectory());

        // 3. cd
        String cdRes = executor.execute("cd project1");
        assertTrue("cd should switch directory", cdRes.contains("/project1"));
        assertEquals("/project1", executor.execute("pwd"));

        // 4. touch
        String touchRes = executor.execute("touch test.inp");
        assertTrue("touch should create file", touchRes.contains("Created") || touchRes.contains("Updated"));
        File inpFile = new File(projDir, "test.inp");
        assertTrue(inpFile.exists());

        // 5. write and cat
        try {
            java.nio.file.Files.write(inpFile.toPath(), "*NODE\n1, 0, 0, 0\n".getBytes());
        } catch (Exception e) {
            fail(e.getMessage());
        }
        String catRes = executor.execute("cat test.inp");
        assertTrue("cat should output file content", catRes.contains("*NODE"));
        assertTrue(catRes.contains("1, 0, 0, 0"));

        // 6. ls
        String lsRes = executor.execute("ls");
        assertTrue("ls should list test.inp", lsRes.contains("test.inp"));

        // 7. cd back
        executor.execute("cd ..");
        assertEquals("/", executor.execute("pwd"));

        // 8. rm
        String rmRes = executor.execute("rm -rf project1");
        assertTrue("rm should delete folder", rmRes.contains("Deleted"));
        assertFalse(projDir.exists());

        // 9. help
        String helpRes = executor.execute("help");
        assertNotNull(helpRes);
        assertTrue(helpRes.contains("FEA Advanced Terminal System"));
    }
}
