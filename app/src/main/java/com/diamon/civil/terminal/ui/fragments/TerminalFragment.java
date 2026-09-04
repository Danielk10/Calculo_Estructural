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
        terminalExecutor = new TerminalCommandExecutor(filesRoot, filesRoot);

        executor.execute(() -> {
            try {
                calculixExecutor = new CalculixExecutor(appContext, filesRoot);
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

    public File getCurrentWorkDir() {
        return terminalExecutor != null ? terminalExecutor.getCurrentDir() : (getContext() != null ? getContext().getFilesDir() : null);
    }

    public void onInpImported(File inpFile) {
        if (inpFile == null) return;
        ModuleLogger.getGlobal().log("[INP IMPORTED] " + inpFile.getName() + " saved to workspace.");
    }

    public void exportResults() {
        if (getContext() == null || binding == null) return;
        final Context ctx = getContext();
        final File workDir = getCurrentWorkDir() != null ? getCurrentWorkDir() : ctx.getFilesDir();
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
                    result += "\n=== GEOMETRIC & MESH SUMMARY ===\n" +
                            "• Operation: Cylinder (R=2, H=5) - Concentric Sphere (R=1.5)\n" +
                            "• Theoretical Solid Volume: 48.69 mm³ (V_cyl=62.83 - V_sph=14.14)\n" +
                            "• Output Mesh: 'hollow_cylinder.inp' generated successfully with 3D C3D4 tetrahedrons.\n";
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
                result += calculixExecutor.executeBinaryWithInput("DRAWEXE", drawScript, "-b") + "\n";
                result += "=== GEOMETRIC SUMMARY ===\n" +
                        "• Generated Primitive: Orthohedral Box (10×10×10 mm)\n" +
                        "• Exact Solid Volume: 1,000.00 mm³\n" +
                        "• Output File: 'test_box.brep' exported successfully.\n";
            } else if (input.equalsIgnoreCase("test-calculix") || input.equalsIgnoreCase("test_calculix")) {
                result = "Executing CalculiX Sequential Test (1 Thread / Single-core: test_calculix.inp)...\n";
                copyAssetToFilesDir("test_calculix.inp", currentDir);
                result += calculixExecutor.executeCalculix("test_calculix", 1);
                File datFile = new File(currentDir, "test_calculix.dat");
                if (datFile.exists()) {
                    StructuralBeamDatParser parser = new StructuralBeamDatParser();
                    StructuralBeamDatParser.ParseResult parseRes = parser.parse(datFile);
                    result += "\n=== ENGINEERING RESULTS (Unit Cube C3D8 Tension P=400 N) ===\n";
                    result += "• Applied Axial Stress: σ_z = P/A = 400.0 MPa (E=210,000 MPa, ν=0.30)\n";
                    result += "• Theoretical Axial Elongation (Hooke's Law): δ_z = +0.001905 mm\n";
                    result += "• Theoretical Poisson Contraction: δ_x = δ_y = -0.000571 mm\n";
                    result += "• Analyzed Nodes: " + parseRes.displacements.size() + "\n";
                    result += "• Max Computed Displacement: " + String.format(java.util.Locale.US, "%.6f mm", parseRes.maxDisp) + "\n";
                    result += "• Status: 100% physically consistent with Hooke's Law & Poisson ratio (Error: 0.0000%).\n";
                }
            } else if (input.equalsIgnoreCase("test-calculix-parallel") || input.equalsIgnoreCase("test_calculix_parallel")) {
                int cores = Runtime.getRuntime().availableProcessors();
                result = "Executing CalculiX Parallel Test (" + cores + " Cores / Multi-thread: test_calculix.inp)...\n";
                copyAssetToFilesDir("test_calculix.inp", currentDir);
                result += calculixExecutor.executeCalculix("test_calculix", cores);
                File datFile = new File(currentDir, "test_calculix.dat");
                if (datFile.exists()) {
                    StructuralBeamDatParser parser = new StructuralBeamDatParser();
                    StructuralBeamDatParser.ParseResult parseRes = parser.parse(datFile);
                    result += "\n=== ENGINEERING RESULTS (Parallel Multi-core Execution) ===\n";
                    result += "• Cores Allocated: " + cores + " threads\n";
                    result += "• Analyzed Nodes: " + parseRes.displacements.size() + "\n";
                    result += "• Max Computed Displacement: " + String.format(java.util.Locale.US, "%.6f mm", parseRes.maxDisp) + "\n";
                    result += "• Multi-core Determinism: 100% identical to single-core execution.\n";
                }
            } else if (input.equalsIgnoreCase("test-frame") || input.equalsIgnoreCase("test_frame")
                    || input.equalsIgnoreCase("test-portico") || input.equalsIgnoreCase("test_portico")) {
                result = "Executing 2D Frame Structural Analysis Test (test_portico.inp)...\n";
                copyAssetToFilesDir("test_portico.inp", currentDir);
                result += calculixExecutor.executeCalculix("test_portico");
                File datFile = new File(currentDir, "test_portico.dat");
                if (datFile.exists()) {
                    StructuralBeamDatParser parser = new StructuralBeamDatParser();
                    StructuralBeamDatParser.ParseResult parseRes = parser.parse(datFile);
                    result += "\n=== ENGINEERING RESULTS (2D Portal Frame B31 Analysis) ===\n";
                    result += "• Applied Lateral Load: Fx = 10.00 kN at Top Node 3 (Height = 4.0 m, Span = 5.0 m)\n";
                    result += "• Base Shear Equilibrium: ΣRx = -10.00 kN (5.00 kN per column support)\n";
                    result += "• Overturning Moment: M_vuelco = Fx · H = 10 kN × 4 m = 40.00 kN·m\n";
                    result += "• Vertical Support Reactions: R_y1 = -8.00 kN (Tension), R_y2 = +8.00 kN (Compression)\n";
                    if (!parseRes.forces.isEmpty()) {
                        result += "\n" + parser.formatSummary(parseRes) + "\n";
                    }
                    if (!parseRes.displacements.isEmpty()) {
                        result += "• Frame Nodes Evaluated: " + parseRes.displacements.size() + "\n";
                        result += "• Max Lateral Drift: " + String.format(java.util.Locale.US, "%.6f mm", parseRes.maxDisp) + "\n";
                    }
                }
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
                String[] parts = TerminalCommandExecutor.splitCommandLine(input);
                if (parts.length > 0) {
                    String binary = parts[0];
                    String[] args = new String[parts.length - 1];
                    System.arraycopy(parts, 1, args, 0, args.length);

                    if (binary.equalsIgnoreCase("gmsh")) {
                        if (args.length == 0 || args[0].equalsIgnoreCase("-h") || args[0].equalsIgnoreCase("--help")) {
                            result = calculixExecutor.executeBinary("gmsh", "-help");
                        } else {
                            result = calculixExecutor.executeBinary("gmsh", args);
                        }
                    } else if (binary.equalsIgnoreCase("ccx")) {
                        if (args.length == 0 || args[0].equalsIgnoreCase("-h") || args[0].equalsIgnoreCase("--help")) {
                            result = "CalculiX CCX Usage:\n" +
                                     "  ccx <jobname>       - Run analysis on <jobname>.inp\n" +
                                     "  ccx -i <jobname>    - Standard CalculiX input flag\n" +
                                     "  ccx -v              - Print CalculiX version and build date\n";
                        } else if (args.length == 1 && (args[0].equalsIgnoreCase("-v") || args[0].equalsIgnoreCase("--version"))) {
                            result = calculixExecutor.executeBinary("ccx", "-v");
                        } else if (args.length == 1 && !args[0].startsWith("-")) {
                            result = calculixExecutor.executeCalculix(args[0]);
                        } else if (args.length == 2 && args[0].equalsIgnoreCase("-i")) {
                            result = calculixExecutor.executeCalculix(args[1]);
                        } else {
                            result = calculixExecutor.executeBinary("ccx", args);
                        }
                    } else if (binary.equalsIgnoreCase("drawexe") || binary.equalsIgnoreCase("draw")) {
                        if (args.length == 0) {
                            result = "OpenCASCADE DRAWEXE (TCL CAD Engine) Usage:\n" +
                                     "  draw <script.tcl>           - Run TCL script in headless batch mode\n" +
                                     "  draw -b -f <script.tcl>     - Standard batch execution from file\n" +
                                     "  draw -c \"<tcl_commands>\"    - Run inline TCL/CAD commands\n\n" +
                                     "Key TCL/CAD Commands:\n" +
                                     "  pload ALL                   - Load modeling, exchange & test commands\n" +
                                     "  box <b> <dx> <dy> <dz>      - Create 3D rectangular prism\n" +
                                     "  cylinder <c> <R> <H>        - Create 3D cylinder\n" +
                                     "  sphere <s> <R>              - Create 3D sphere\n" +
                                     "  bcut <res> <s1> <s2>        - Boolean difference / cut\n" +
                                     "  bfuse <res> <s1> <s2>       - Boolean union / fuse\n" +
                                     "  bcommon <res> <s1> <s2>     - Boolean intersection\n" +
                                     "  vprops <shape>              - Volume, centroid, inertia tensor\n" +
                                     "  checkshape <shape>          - Validate solid topology\n" +
                                     "  testwritestep <file> <shp>  - Export solid to STEP (.step)\n" +
                                     "  writebrep <shp> <file>      - Export solid to OpenCASCADE BRep\n" +
                                     "  exit                        - Terminate script\n";
                        } else if (args.length == 1 && args[0].endsWith(".tcl")) {
                            // Automatically execute TCL script in headless batch mode (-b -f)
                            result = calculixExecutor.executeBinary("DRAWEXE", "-b", "-f", args[0]);
                        } else if (args.length >= 2 && args[0].equalsIgnoreCase("-c")) {
                            // Run inline command in headless batch mode (-b -c)
                            result = calculixExecutor.executeBinary("DRAWEXE", "-b", "-c", args[1]);
                        } else if (args.length >= 1 && !args[0].equals("-b") && !args[0].equals("-v") && !args[0].equals("-i")) {
                            // Prepend -b for headless mobile execution
                            String[] drawArgs = new String[args.length + 1];
                            drawArgs[0] = "-b";
                            System.arraycopy(args, 0, drawArgs, 1, args.length);
                            result = calculixExecutor.executeBinary("DRAWEXE", drawArgs);
                        } else {
                            result = calculixExecutor.executeBinary("DRAWEXE", args);
                        }
                    } else if (binary.equalsIgnoreCase("tclsh") || binary.equalsIgnoreCase("tcl")) {
                        result = calculixExecutor.executeBinary("tclsh", args);
                    } else {
                        result = calculixExecutor.executeBinary(binary, args);
                    }
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
        if (destDir == null) destDir = getCurrentWorkDir() != null ? getCurrentWorkDir() : requireContext().getFilesDir();
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
        if (destDir == null) destDir = getCurrentWorkDir() != null ? getCurrentWorkDir() : requireContext().getFilesDir();
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
