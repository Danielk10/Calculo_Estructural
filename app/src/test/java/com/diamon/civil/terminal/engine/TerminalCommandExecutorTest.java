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

    @Test
    public void testGlobalMultiModuleNavigation() throws Exception {
        File globalRoot = tempFolder.newFolder("global_app_files");
        File terminalHome = new File(globalRoot, "terminal");
        File structDir = new File(globalRoot, "structural_analysis");
        File solidsDir = new File(globalRoot, "3d_solid_analysis");
        File usrDir = new File(globalRoot, "usr"); // Internal system folder
        assertTrue(terminalHome.mkdirs());
        assertTrue(structDir.mkdirs());
        assertTrue(solidsDir.mkdirs());
        assertTrue(usrDir.mkdirs());

        // Create a model file in structural_analysis
        File structModel = new File(structDir, "model.json");
        java.nio.file.Files.write(structModel.toPath(), "{\"model\":\"frame\"}".getBytes());

        TerminalCommandExecutor globalExecutor = new TerminalCommandExecutor(globalRoot, terminalHome);

        // 1. Initial directory should be /terminal
        assertEquals("/terminal", globalExecutor.execute("pwd"));

        // 2. cd / should go to global root
        String cdRoot = globalExecutor.execute("cd /");
        assertTrue(cdRoot.contains("/"));
        assertEquals("/", globalExecutor.execute("pwd"));

        // 3. ls at root should show module folders and hide internal system folders (usr)
        String lsRoot = globalExecutor.execute("ls");
        assertTrue(lsRoot.contains("structural_analysis"));
        assertTrue(lsRoot.contains("3d_solid_analysis"));
        assertTrue(lsRoot.contains("terminal"));
        assertFalse("Internal usr folder should be hidden", lsRoot.contains("usr"));

        // 4. cd into structural_analysis
        String cdStruct = globalExecutor.execute("cd /structural_analysis");
        assertTrue(cdStruct.contains("/structural_analysis"));
        assertEquals("/structural_analysis", globalExecutor.execute("pwd"));

        // 5. ls in structural_analysis should show model.json
        String lsStruct = globalExecutor.execute("ls");
        assertTrue(lsStruct.contains("model.json"));

        // 6. cat model.json
        String catStruct = globalExecutor.execute("cat model.json");
        assertTrue(catStruct.contains("frame"));

        // 7. cd ~ should return to terminal home
        String cdHome = globalExecutor.execute("cd ~");
        assertTrue(cdHome.contains("/terminal"));
        assertEquals("/terminal", globalExecutor.execute("pwd"));

        // 8. Sandbox check: cannot cd ../../ outside global root
        globalExecutor.execute("cd ../../..");
        assertEquals("/", globalExecutor.execute("pwd"));
    }
}
