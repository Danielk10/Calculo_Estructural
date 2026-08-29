package com.diamon.civil.terminal.ui.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.diamon.civil.R;
import com.diamon.civil.core.export.ExportManager;
import com.diamon.civil.core.util.logging.ModuleLogger;
import com.diamon.civil.databinding.FragmentTerminalBinding;
import com.diamon.civil.solids.engine.SolidDisplacementFrdParser;
import com.diamon.civil.solids.engine.SolidInpAssembler;
import com.diamon.civil.solids.export.SolidPDFReportGenerator;
import com.diamon.civil.structural.engine.CalculixExecutor;
import com.diamon.civil.structural.engine.StructuralBeamDatParser;
import com.diamon.civil.terminal.engine.TerminalCommandExecutor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TerminalFragment extends Fragment {

    private FragmentTerminalBinding binding;
    private TerminalCommandExecutor terminalExecutor;
    private CalculixExecutor calculixExecutor;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;
    private volatile boolean isExecutingCommand = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentTerminalBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Context appContext = requireContext().getApplicationContext();
        File filesRoot = appContext.getFilesDir();
        File terminalDir = new File(filesRoot, "terminal");
        if (!terminalDir.exists()) terminalDir.mkdirs();
        terminalExecutor = new TerminalCommandExecutor(filesRoot, terminalDir);

        executor.execute(() -> {
            try {
                calculixExecutor = new CalculixExecutor(appContext, terminalDir);
            } catch (Exception e) {
                // Ignore or log error
            }
        });

        binding.btnSend.setOnClickListener(v -> {
            sendCommand();
            forceInputFocus();
        });
        binding.etCommand.setOnEditorActionListener((v, actionId, event) -> {
            sendCommand();
            forceInputFocus();
            return true;
        });

        binding.etCommand.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    navigateHistory(-1);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    navigateHistory(1);
                    return true;
                }
            }
            return false;
        });

        binding.btnHistoryPrev.setOnClickListener(v -> navigateHistory(-1));
        binding.btnHistoryNext.setOnClickListener(v -> navigateHistory(1));
        binding.btnAbort.setOnClickListener(v -> {
            if (calculixExecutor != null) {
                calculixExecutor.abort();
                if (isAdded()) {
                    ModuleLogger.getGlobal().log(getString(R.string.terminal_process_aborted));
                } else {
                    ModuleLogger.getGlobal().log("\n[PROCESS ABORTED BY USER]\n");
                }
            }
            keepInputFocus();
        });
        binding.btnCopyLog.setOnClickListener(v -> copyLogToClipboard());

        keepInputFocus();

        ModuleLogger.getGlobal().addListener(fullLog -> {
            if (binding != null && getActivity() != null) {
                final boolean wasAtBottom = isScrollAtBottom();
                final int scrollY = binding.scrollLog.getScrollY();
                binding.tvLog.setText(fullLog);
                if (wasAtBottom || isExecutingCommand) {
                    scrollDown();
                } else {
                    binding.scrollLog.post(() -> {
                        if (binding != null) {
                            binding.scrollLog.setScrollY(scrollY);
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        keepInputFocus();
    }

    private void forceInputFocus() {
        if (binding == null || binding.etCommand == null) return;
        binding.etCommand.post(() -> {
            if (binding != null && isAdded() && binding.etCommand != null) {
                binding.etCommand.requestFocus();
                Context context = getContext();
                if (context != null) {
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                            context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(binding.etCommand, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            }
        });
    }

    private void keepInputFocus() {
        if (binding == null || binding.etCommand == null) return;
        if (binding.tvLog != null && (binding.tvLog.hasSelection() || binding.tvLog.isFocused())) {
            return;
        }
        binding.etCommand.post(() -> {
            if (binding != null && isAdded() && binding.etCommand != null) {
                if (binding.tvLog != null && (binding.tvLog.hasSelection() || binding.tvLog.isFocused())) {
                    return;
                }
                binding.etCommand.requestFocus();
                Context context = getContext();
                if (context != null) {
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                            context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(binding.etCommand, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            }
        });
    }

    private boolean isScrollAtBottom() {
        if (binding == null || binding.scrollLog == null || binding.tvLog == null) return true;
        int scrollY = binding.scrollLog.getScrollY();
        int scrollHeight = binding.scrollLog.getHeight();
        int contentHeight = binding.tvLog.getHeight();
        if (contentHeight == 0) return true;
        return (scrollY + scrollHeight) >= (contentHeight - 100);
    }

    private void navigateHistory(int direction) {
        if (commandHistory.isEmpty() || binding == null) return;

        if (historyIndex == -1) {
            historyIndex = commandHistory.size();
        }

        historyIndex += direction;

        if (historyIndex < 0) {
            historyIndex = 0;
        } else if (historyIndex >= commandHistory.size()) {
            historyIndex = commandHistory.size();
            binding.etCommand.setText("");
            keepInputFocus();
            return;
        }

        String command = commandHistory.get(historyIndex);
        binding.etCommand.setText(command);
        binding.etCommand.setSelection(command.length());
        keepInputFocus();
    }

    private void copyLogToClipboard() {
        if (getContext() == null || binding == null) return;
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("FEA Terminal Log", binding.tvLog.getText().toString());
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), R.string.terminal_copied_toast, Toast.LENGTH_SHORT).show();
        }
    }

    public void onInpImported(File inpFile) {
        if (inpFile == null) return;
        ModuleLogger.getGlobal().log("[INP IMPORTED] " + inpFile.getName() + " saved to terminal workspace.");
    }

    public void exportResults() {
        if (getContext() == null || binding == null) return;
        final Context ctx = getContext();
        final File workDir = new File(ctx.getFilesDir(), "terminal");
        if (!workDir.exists()) workDir.mkdirs();
        final File reportFile = new File(workDir, "Terminal_Analysis_Report.pdf");
        final String logText = binding.tvLog != null ? binding.tvLog.getText().toString() : "";

        if (logText.isEmpty()) {
            Toast.makeText(ctx, getString(R.string.toast_no_report_to_export), Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            boolean exported = false;
            try {
                SolidPDFReportGenerator generator = new SolidPDFReportGenerator();
                boolean success = generator.generateReport(ctx, reportFile, "Terminal Analysis Log", logText);

                if (success && reportFile.exists()) {
                    ExportManager manager = new ExportManager(ctx);
                    exported = manager.exportToDownloads(reportFile, "terminal");
                }
            } catch (Throwable ignored) {}

            final boolean success = exported;
            android.app.Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (success) {
                        Toast.makeText(ctx, getString(R.string.toast_pdf_exported), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(ctx, getString(R.string.toast_pdf_export_failed), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void sendCommand() {
        if (getContext() == null || binding == null) return;
        final File filesDir = new File(getContext().getApplicationContext().getFilesDir(), "terminal");
        if (!filesDir.exists()) filesDir.mkdirs();
        String input = binding.etCommand.getText().toString().trim();
        if (input.isEmpty()) return;

        if (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(input)) {
            commandHistory.add(input);
        }
        historyIndex = commandHistory.size();

        binding.etCommand.setText("");
        keepInputFocus();

        if (input.equalsIgnoreCase("clear")) {
            ModuleLogger.getGlobal().clear();
            keepInputFocus();
            return;
        }

        ModuleLogger.getGlobal().log("$ " + input);
        scrollDown();

        isExecutingCommand = true;
        if (binding.btnAbort != null) {
            binding.btnAbort.setVisibility(View.VISIBLE);
        }

        executor.execute(() -> {
            File currentDir = terminalExecutor.getCurrentDir();
            if (calculixExecutor != null) {
                calculixExecutor.setWorkDir(currentDir);
            }
            String result = null;

            if (input.equalsIgnoreCase("test-gmsh") || input.equalsIgnoreCase("test_gmsh")) {
                result = "Executing Gmsh Boolean Operation Test (Hollow Cylinder)...\n";
                File geoFile = new File(currentDir, "boolean_test.geo");
                String script = "SetFactory(\"OpenCASCADE\");\n" +
                        "Cylinder(1) = {0, 0, 0, 0, 0, 5, 2};\n" +
                        "Sphere(2) = {0, 0, 2.5, 1.5};\n" +
                        "BooleanDifference(3) = { Volume{1}; Delete; } { Volume{2}; Delete; };\n" +
                        "Mesh.MeshSizeMax = 0.5;\n";
                try (FileOutputStream fos = new FileOutputStream(geoFile)) {
                    fos.write(script.getBytes(StandardCharsets.UTF_8));
                    result += "Created 'boolean_test.geo'.\nRunning Gmsh mesher to generate 'hollow_cylinder.inp'...\n";
                    String gmshOut = calculixExecutor.executeBinary("gmsh", "boolean_test.geo", "-3", "-format", "inp", "-o", "hollow_cylinder.inp");
                    result += gmshOut;
                } catch (Exception e) {
                    result += "Error running test: " + e.getMessage();
                }
            } else if (input.equalsIgnoreCase("test_draw") || input.equalsIgnoreCase("test-draw")
                    || input.equalsIgnoreCase("test-occt") || input.equalsIgnoreCase("test_occt")) {
                String drawScript = "pload ALL\n" +
                        "box b 10 10 10\n" +
                        "writebrep b test_box.brep\n" +
                        "puts \"BOX CREATED SUCCESSFULLY\"\n" +
                        "exit\n";
                result = "Executing Headless DRAWEXE Test (OCCT Box Primitive)...\n";
                result += calculixExecutor.executeBinaryWithInput("DRAWEXE", drawScript, "-b");
            } else if (input.equalsIgnoreCase("test-calculix") || input.equalsIgnoreCase("test_calculix")) {
                result = "Executing CalculiX Validation Test (test_calculix.inp)...\n";
                copyAssetToFilesDir("test_calculix.inp", currentDir);
                result += calculixExecutor.executeCalculix("test_calculix");
            } else if (input.equalsIgnoreCase("test-calculix-parallel") || input.equalsIgnoreCase("test_calculix_parallel")) {
                int cores = Runtime.getRuntime().availableProcessors();
                result = "Executing CalculiX Parallel Test (" + cores + " Cores / Multi-core)...\n";
                copyAssetToFilesDir("test_calculix.inp", currentDir);
                result += calculixExecutor.executeCalculix("test_calculix");
            } else if (input.equalsIgnoreCase("test-frame") || input.equalsIgnoreCase("test_frame")
                    || input.equalsIgnoreCase("test-portico") || input.equalsIgnoreCase("test_portico")) {
                result = "Executing 2D Frame Structural Analysis Test (test_portico.inp)...\n";
                copyAssetToFilesDir("test_portico.inp", currentDir);
                result += calculixExecutor.executeCalculix("test_portico");
            } else if (input.equalsIgnoreCase("test-frd-parser") || input.equalsIgnoreCase("test_frd_parser")) {
                result = "Executing C++ JNI FRD Parser Test (test_calculix.frd)...\n";
                File frdFile = new File(currentDir, "test_calculix.frd");
                File glbFile = new File(currentDir, "test_calculix.glb");
                if (frdFile.exists()) {
                    boolean ok = calculixExecutor.convertFrdToGlb(frdFile.getAbsolutePath(), glbFile.getAbsolutePath(), 1.0f);
                    result += "Conversion to GLB: " + (ok ? "SUCCESS" : "FAILED") + "\n";
                    result += "Output: " + glbFile.getAbsolutePath();
                } else {
                    result += "Error: test_calculix.frd not found. Run 'test-calculix' first.";
                }
            } else if (input.equalsIgnoreCase("test-dat-parser") || input.equalsIgnoreCase("test_dat_parser")) {
                result = "Executing Java DAT Parser Test (test_calculix.dat)...\n";
                File datFile = new File(currentDir, "test_calculix.dat");
                if (datFile.exists()) {
                    StructuralBeamDatParser parser = new StructuralBeamDatParser();
                    StructuralBeamDatParser.ParseResult parseRes = parser.parse(datFile);
                    if (parseRes.displacements.isEmpty() && parseRes.forces.isEmpty()) {
                        result += "WARNING: no displacements or forces were extracted.\n";
                    } else {
                        if (!parseRes.forces.isEmpty()) result += parser.formatSummary(parseRes) + "\n";
                        if (!parseRes.displacements.isEmpty()) result += "Nodes with displacement (DAT): " + parseRes.displacements.size() + "\nMax Disp: " + parseRes.maxDisp + "\n";
                    }
                } else {
                    result += "Error: test_calculix.dat not found. Run 'test-calculix' first.";
                }
            } else if (input.equalsIgnoreCase("test-coordinate-fallback") || input.equalsIgnoreCase("test_coordinate_fallback")) {
                result = "Executing Coordinate-Based Boundary Fallback Test...\n";
                result += runStepTest("linkrods.step", "fallback_test", currentDir);
            } else if (input.equalsIgnoreCase("test-step-solve") || input.equalsIgnoreCase("test_step_solve")) {
                result = "Executing STEP Meshing & Solving Pipeline (linkrods.step)...\n";
                result += runStepTest("linkrods.step", "linkrods", currentDir);
            } else if (input.equalsIgnoreCase("test-bracket-solve") || input.equalsIgnoreCase("test_bracket_solve")) {
                result = "Executing Bracket Meshing & Solving Pipeline (bracket_simple.step)...\n";
                result += runStepTest("bracket_simple.step", "bracket", currentDir);
            } else if (input.equalsIgnoreCase("test-cad-solve") || input.equalsIgnoreCase("test_cad_solve")) {
                result = "Executing Headless CAD Meshing & Solving Pipeline (OCCT + Gmsh + CalculiX)...\n";
                String drawScript = "pload ALL\n" +
                        "box b 2 2 10\n" +
                        "writebrep b bar.brep\n" +
                        "exit\n";
                result += "Step 1: Generating CAD geometry (bar.brep) with DRAWEXE...\n";
                result += calculixExecutor.executeBinaryWithInput("DRAWEXE", drawScript, "-b") + "\n";

                File geoFile = new File(currentDir, "bar.geo");
                String geoScript = "SetFactory(\"OpenCASCADE\");\n" +
                        "Merge \"bar.brep\";\n" +
                        "Mesh.MeshSizeMax = 1.0;\n";
                try (FileOutputStream fos = new FileOutputStream(geoFile)) {
                    fos.write(geoScript.getBytes(StandardCharsets.UTF_8));
                    result += "Step 2: Created 'bar.geo'. Running Gmsh to generate 'bar_raw.inp'...\n";
                    String gmshOut = calculixExecutor.executeBinary("gmsh", "bar.geo", "-3", "-format", "inp", "-o", "bar_raw.inp");
                    result += gmshOut + "\n";

                    result += "Step 3: Assembling final 'bar.inp' using InpAssembler (Coordinate Fallback)...\n";
                    SolidInpAssembler.assemble(currentDir, "bar", "Steel", 210000.0, 0.3, -500.0, "nonexistent_fixed", "nonexistent_load");

                    result += "Step 4: Executing CalculiX Solver (ccx -i bar)...\n";
                    String ccxOut = calculixExecutor.executeBinary("ccx", "-i", "bar");
                    result += ccxOut + "\n";

                    File frdFile = new File(currentDir, "bar.frd");
                    if (frdFile.exists()) {
                        result += "\nStep 5: Summarizing Engineering Results:\n";
                        result += SolidDisplacementFrdParser.parseAndSummarize(frdFile);
                    } else {
                        result += "\nError: No .frd results generated.\n";
                    }
                } catch (Exception e) {
                    result += "Error running test: " + e.getMessage();
                }
            } else if (input.equalsIgnoreCase("run-sim-test") || input.equalsIgnoreCase("run_sim_test")) {
                result = "Executing Automated Cantilever Simulation Test...\n";
                try {
                    File geoFile = com.diamon.civil.solids.engine.SampleSimulationCase.createCantileverGeo(currentDir);
                    result += "Created 'cantilever.geo'. Running Gmsh...\n";
                    String gmshOut = calculixExecutor.executeBinary("gmsh", "cantilever.geo", "-3", "-format", "inp", "-o", "cantilever_raw.inp");
                    result += gmshOut + "\n";

                    result += "Assembling 'cantilever.inp'...\n";
                    SolidInpAssembler.assemble(currentDir, "cantilever", "Steel", 210000.0, 0.3, -100.0, "Fixed", "Loaded");

                    result += "Running CalculiX Solver (ccx -i cantilever)...\n";
                    String ccxOut = calculixExecutor.executeBinary("ccx", "-i", "cantilever");
                    result += ccxOut + "\n";

                    File frdFile = new File(currentDir, "cantilever.frd");
                    if (frdFile.exists()) {
                        result += "\n=== FINAL RESULTS SUMMARY (Cantilever Beam) ===\n";
                        result += SolidDisplacementFrdParser.parseAndSummarize(frdFile);
                    } else {
                        result += "\nWarning: cantilever.frd not found.\n";
                    }
                } catch (Exception e) {
                    result += "Error running simulation test: " + e.getMessage();
                }
            } else {
                result = terminalExecutor.execute(input);
            }

            if (result == null) {
                // Delegate to binary execution if command not built-in
                String[] parts = input.split("\\s+");
                String binary = parts[0];
                String[] args = new String[parts.length - 1];
                System.arraycopy(parts, 1, args, 0, args.length);

                if (binary.equalsIgnoreCase("gmsh")) {
                    result = calculixExecutor.executeBinary("gmsh", args);
                } else if (binary.equalsIgnoreCase("ccx")) {
                    if (args.length == 1 && !args[0].startsWith("-")) {
                        result = calculixExecutor.executeCalculix(args[0]);
                    } else {
                        result = calculixExecutor.executeBinary("ccx", args);
                    }
                } else if (binary.equalsIgnoreCase("drawexe")) {
                    result = calculixExecutor.executeBinary("DRAWEXE", args);
                } else {
                    result = calculixExecutor.executeBinary(binary, args);
                }
            }

            final String finalResult = result;
            isExecutingCommand = false;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        if (finalResult != null && !finalResult.isEmpty()) {
                            ModuleLogger.getGlobal().log(finalResult);
                        }
                        if (binding.btnAbort != null) {
                            binding.btnAbort.setVisibility(View.GONE);
                        }
                        scrollDown();
                        keepInputFocus();
                    }
                });
            }
        });
    }

    private void copyAssetToFilesDir(String filename, File destDir) {
        if (getContext() == null) return;
        if (destDir == null) destDir = new File(requireContext().getFilesDir(), "terminal");
        if (!destDir.exists()) destDir.mkdirs();
        File outFile = new File(destDir, filename);
        if (outFile.exists()) return;
        try (InputStream is = requireContext().getAssets().open(filename);
             FileOutputStream os = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String runStepTest(String stepFileName, String baseName, File destDir) {
        StringBuilder sb = new StringBuilder();
        if (getContext() == null) return "Context is null";
        if (destDir == null) destDir = new File(requireContext().getFilesDir(), "terminal");
        if (!destDir.exists()) destDir.mkdirs();
        File stepFile = new File(destDir, stepFileName);

        sb.append("Step 1: Preparing STEP file... ");
        String assetPath = "data/data/com.diamon.civil/files/usr/share/opencascade/data/step/" + stepFileName;
        try (InputStream is = requireContext().getAssets().open(assetPath);
             FileOutputStream os = new FileOutputStream(stepFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            sb.append("OK\n");
        } catch (IOException e) {
            return sb.append("FAILED (").append(assetPath).append("): ").append(e.getMessage()).append("\n").toString();
        }

        File geoFile = new File(destDir, baseName + ".geo");
        String geoScript = "SetFactory(\"OpenCASCADE\");\n" +
                "Merge \"" + stepFileName + "\";\n" +
                "Mesh.MeshSizeMax = 2.0;\n";
        try (FileOutputStream fos = new FileOutputStream(geoFile)) {
            fos.write(geoScript.getBytes(StandardCharsets.UTF_8));
            sb.append("Step 2: Created '").append(baseName).append(".geo'. Running Gmsh...\n");
            String gmshOut = calculixExecutor.executeBinary("gmsh", baseName + ".geo", "-3", "-format", "inp", "-o", baseName + "_raw.inp");
            sb.append(gmshOut).append("\n");

            sb.append("Step 3: Assembling final '").append(baseName).append(".inp'...\n");
            SolidInpAssembler.assemble(destDir, baseName, "Steel", 210000.0, 0.3, -100.0, "Fixed", "Loaded");

            sb.append("Step 4: Executing CalculiX Solver...\n");
            String ccxOut = calculixExecutor.executeBinary("ccx", "-i", baseName);
            sb.append(ccxOut).append("\n");

            File frdFile = new File(destDir, baseName + ".frd");
            if (frdFile.exists()) {
                sb.append("Step 5: Parsing results (.frd)...\n");
                sb.append(SolidDisplacementFrdParser.parseAndSummarize(frdFile));
            } else {
                sb.append("Step 5: FAILED - Result file .frd not found.\n");
            }
        } catch (Exception e) {
            sb.append("Error running test: ").append(e.getMessage());
        }
        return sb.toString();
    }

    private void scrollDown() {
        if (binding != null && binding.scrollLog != null) {
            binding.scrollLog.post(() -> {
                if (binding != null && binding.scrollLog != null && binding.tvLog != null) {
                    binding.scrollLog.scrollTo(0, binding.tvLog.getBottom());
                }
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdown();
        binding = null;
    }
}
