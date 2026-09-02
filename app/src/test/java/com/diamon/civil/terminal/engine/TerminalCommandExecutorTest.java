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
        assertTrue(helpRes.contains("Special Test & Pipeline Commands"));
    }

    @Test
    public void testGlobalMultiModuleNavigation() throws Exception {
        File globalRoot = tempFolder.newFolder("global_app_files");
        File terminalHome = new File(globalRoot, "terminal");
        File structDir = new File(globalRoot, "structural_analysis");
        File solidsDir = new File(globalRoot, "3d_solid_analysis");
        File usrDir = new File(globalRoot, "usr"); // Internal system folder
        File binDir = new File(globalRoot, "bin"); // Internal system folder
        File profileFile = new File(globalRoot, "profileInstalled");
        File profileData = new File(globalRoot, "profileinstaller_profileWrittenFor_lastUpdateTime.dat");
        File userDatFile = new File(globalRoot, "output.dat");
        assertTrue(terminalHome.mkdirs());
        assertTrue(structDir.mkdirs());
        assertTrue(solidsDir.mkdirs());
        assertTrue(usrDir.mkdirs());
        assertTrue(binDir.mkdirs());
        assertTrue(profileFile.createNewFile());
        assertTrue(profileData.createNewFile());
        assertTrue(userDatFile.createNewFile());

        // Create a model file in structural_analysis
        File structModel = new File(structDir, "model.json");
        java.nio.file.Files.write(structModel.toPath(), "{\"model\":\"frame\"}".getBytes());

        TerminalCommandExecutor globalExecutor = new TerminalCommandExecutor(globalRoot, globalRoot);

        // 1. Initial directory should be / (global root)
        assertEquals("/", globalExecutor.execute("pwd"));

        // 2. ls at root should show module folders, user files, and hide internal system folders & profileinstaller files
        String lsRoot = globalExecutor.execute("ls");
        assertTrue(lsRoot.contains("structural_analysis"));
        assertTrue(lsRoot.contains("3d_solid_analysis"));
        assertTrue(lsRoot.contains("terminal"));
        assertTrue("Legitimate user .dat files should be listed", lsRoot.contains("output.dat"));
        assertFalse("profileInstalled should be hidden", lsRoot.contains("profileInstalled"));
        assertFalse("profileinstaller .dat should be hidden", lsRoot.contains("profileinstaller_profileWrittenFor_lastUpdateTime.dat"));
        assertFalse("Internal usr folder should be hidden", lsRoot.contains("usr"));
        assertFalse("Internal bin folder should be hidden", lsRoot.contains("bin"));

        // 3. cd into structural_analysis
        String cdStruct = globalExecutor.execute("cd /structural_analysis");
        assertTrue(cdStruct.contains("/structural_analysis"));
        assertEquals("/structural_analysis", globalExecutor.execute("pwd"));

        // 4. ls in structural_analysis should show model.json
        String lsStruct = globalExecutor.execute("ls");
        assertTrue(lsStruct.contains("model.json"));

        // 5. cat model.json
        String catStruct = globalExecutor.execute("cat model.json");
        assertTrue(catStruct.contains("frame"));

        // 6. cd ~ should return to global home (/)
        String cdHome = globalExecutor.execute("cd ~");
        assertTrue(cdHome.contains("/"));
        assertEquals("/", globalExecutor.execute("pwd"));

        // 7. Sandbox check: cannot cd ../../ outside global root
        globalExecutor.execute("cd ../../..");
        assertEquals("/", globalExecutor.execute("pwd"));
    }
}
