package com.diamon.civil.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.widget.Toast;
import com.diamon.civil.databinding.FragmentTerminalBinding;
import com.diamon.civil.engine.CalculixExecutor;
import com.diamon.civil.engine.TerminalCommandExecutor;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TerminalFragment extends Fragment {

    private FragmentTerminalBinding binding;
    private TerminalCommandExecutor terminalExecutor;
    private CalculixExecutor calculixExecutor;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentTerminalBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        final android.content.Context appContext = requireContext().getApplicationContext();
        terminalExecutor = new TerminalCommandExecutor(appContext.getFilesDir());
        
        executor.execute(() -> {
            try {
                calculixExecutor = new CalculixExecutor(appContext);
            } catch (Exception e) {
                // Log error if needed
            }
        });

        binding.btnSend.setOnClickListener(v -> sendCommand());
        binding.etCommand.setOnEditorActionListener((v, actionId, event) -> {
            sendCommand();
            return true;
        });

        binding.scrollLog.setOnClickListener(v -> copyLogToClipboard());
        binding.tvLog.setOnClickListener(v -> copyLogToClipboard());
        
        com.diamon.civil.util.logging.ModuleLogger.getGlobal().attachToTextView(binding.tvLog);
    }

    private void copyLogToClipboard() {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("FEA Terminal Log", binding.tvLog.getText().toString());
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Log copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    public void exportResults() {
        if (getContext() == null || binding == null) return;
        File workDir = getContext().getFilesDir();
        File logFile = new File(workDir, "Terminal_Log.txt");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(logFile)) {
            fos.write(binding.tvLog.getText().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            com.diamon.civil.util.export.ExportManager manager = new com.diamon.civil.util.export.ExportManager(getContext());
            manager.exportToDownloads(logFile, "terminal");
            Toast.makeText(getContext(), "Exported to Downloads/Structural_Analysis_FEA_Advanced/terminal", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void sendCommand() {
        if (getContext() == null || binding == null) return;
        final File filesDir = getContext().getApplicationContext().getFilesDir();
        String input = binding.etCommand.getText().toString().trim();
        if (input.isEmpty()) return;

        binding.etCommand.setText("");
        if (input.equalsIgnoreCase("clear")) {
            com.diamon.civil.util.logging.ModuleLogger.getGlobal().clear();
            return;
        }

        com.diamon.civil.util.logging.ModuleLogger.getGlobal().log("$ " + input);
        scrollDown();

        executor.execute(() -> {
            String result = null;
            
            // Special test command for Gmsh Boolean & OCCT Meshing
            if (input.equalsIgnoreCase("test-gmsh") || input.equalsIgnoreCase("test_gmsh")) {
                result = "Executing Gmsh Boolean Operation Test (Hollow Cylinder)...\n";
                File geoFile = new File(filesDir, "prueba_booleana.geo");
                String script = "SetFactory(\"OpenCASCADE\");\n" +
                        "Cylinder(1) = {0, 0, 0, 0, 0, 5, 2};\n" +
                        "Sphere(2) = {0, 0, 2.5, 1.5};\n" +
                        "BooleanDifference(3) = { Volume{1}; Delete; } { Volume{2}; Delete; };\n" +
                        "Mesh.MeshSizeMax = 0.5;\n";
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(geoFile)) {
                    fos.write(script.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    result += "Created 'prueba_booleana.geo'.\nRunning Gmsh mesher to generate 'cilindro_hueco.inp'...\n";
                    String gmshOut = calculixExecutor.executeBinary("gmsh", "prueba_booleana.geo", "-3", "-format", "inp", "-o", "cilindro_hueco.inp");
                    result += gmshOut;
                } catch (Exception e) {
                    result += "Error running test: " + e.getMessage();
                }
            } else if (input.equalsIgnoreCase("test_draw") || input.equalsIgnoreCase("test-draw") || input.equalsIgnoreCase("test-occt")) {
                String drawScript = "box b 10 10 10\n" +
                                   "writebrep b test_box.brep\n" +
                                   "puts \"BOX CREATED SUCCESSFULLY\"\n" +
                                   "exit\n";
                result = "Executing Headless DRAWEXE Test (OCCT Box Primitive)...\n";
                result += calculixExecutor.executeBinaryWithInput("DRAWEXE", drawScript);
            } else if (input.equalsIgnoreCase("test-cad-solve")) {
                result = "Executing Headless CAD Meshing & Solving Pipeline (OCCT + Gmsh + CalculiX)...\n";
                String drawScript = "box b 2 2 10\n" +
                                   "writebrep b bar.brep\n" +
                                   "exit\n";
                result += "Step 1: Generating CAD geometry (bar.brep) with DRAWEXE...\n";
                result += calculixExecutor.executeBinaryWithInput("DRAWEXE", drawScript) + "\n";
                
                File geoFile = new File(filesDir, "bar.geo");
                String geoScript = "SetFactory(\"OpenCASCADE\");\n" +
                                   "Merge \"bar.brep\";\n" +
                                   "Mesh.MeshSizeMax = 1.0;\n";
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(geoFile)) {
                    fos.write(geoScript.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    result += "Step 2: Created 'bar.geo'. Running Gmsh to generate 'bar_raw.inp'...\n";
                    String gmshOut = calculixExecutor.executeBinary("gmsh", "bar.geo", "-3", "-format", "inp", "-o", "bar_raw.inp");
                    result += gmshOut + "\n";
                    
                    result += "Step 3: Assembling final 'bar.inp' using InpAssembler (Coordinate Fallback)...\n";
                    com.diamon.civil.engine.InpAssembler.assemble(filesDir, "bar", "Steel", 210000.0, 0.3, -500.0, "nonexistent_fixed", "nonexistent_load");
                    
                    result += "Step 4: Executing CalculiX Solver (ccx -i bar)...\n";
                    String ccxOut = calculixExecutor.executeBinary("ccx", "-i", "bar");
                    result += ccxOut + "\n";
                    
                    File frdFile = new File(filesDir, "bar.frd");
                    if (frdFile.exists()) {
                        result += "\nStep 5: Summarizing Engineering Results:\n";
                        result += com.diamon.civil.test.simulation.FrdParser.parseAndSummarize(frdFile);
                    } else {
                        result += "\nError: No .frd results generated.\n";
                    }
                } catch (Exception e) {
                    result += "Error running test: " + e.getMessage();
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
                    result = calculixExecutor.executeBinary("ccx", args);
                } else {
                    result = calculixExecutor.executeBinary(binary, args);
                }
            }
            final String finalResult = result;
            getActivity().runOnUiThread(() -> {
                if (binding != null) {
                    com.diamon.civil.util.logging.ModuleLogger.getGlobal().log(finalResult);
                    scrollDown();
                }
            });
        });
    }

    private void scrollDown() {
        binding.scrollLog.post(() -> binding.scrollLog.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdown();
        binding = null;
    }
}
