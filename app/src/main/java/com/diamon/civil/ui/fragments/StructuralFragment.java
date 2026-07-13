package com.diamon.civil.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.diamon.civil.databinding.FragmentStructuralBinding;
import com.diamon.civil.engine.CalculixExecutor;
import com.diamon.civil.engine.DatParser;
import com.diamon.civil.engine.NativeFeaCore;
import com.diamon.civil.engine.StructuralModel;
import com.diamon.civil.util.logging.ModuleLogger;
import com.google.android.material.tabs.TabLayout;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StructuralFragment extends Fragment {

    private FragmentStructuralBinding binding;
    private final ModuleLogger logger = new ModuleLogger();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private CalculixExecutor calculixExecutor;
    private DatParser datParser;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentStructuralBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        executor.execute(() -> {
            calculixExecutor = new CalculixExecutor(requireContext());
        });
        datParser = new DatParser();
        logger.attachToTextView(binding.tvStructuralLog);

        setupTabs();
        setupButtons();
        loadDefaultTestCase();
    }

    private void loadDefaultTestCase() {
        // Sample Cantilever Beam
        binding.etNodes.setText("1, 0, 0, 0\n2, 10, 0, 0\n3, 5, 0, 0");
        binding.etElements.setText("1, 1, 3\n2, 3, 2");
        Toast.makeText(getContext(), "Loaded Cantilever Example", Toast.LENGTH_SHORT).show();
    }

    private void setupTabs() {
        binding.tabLayoutStructural.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                binding.layoutStructuralData.setVisibility(tab.getPosition() == 0 ? View.VISIBLE : View.GONE);
                binding.layoutStructuralGL.setVisibility(tab.getPosition() == 1 ? View.VISIBLE : View.GONE);
                binding.layoutStructuralLog.setVisibility(tab.getPosition() == 2 ? View.VISIBLE : View.GONE);
                
                if (tab.getPosition() == 1) {
                    binding.frameGLView.requestRender();
                }
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupButtons() {
        binding.btnSolveStructural.setOnClickListener(v -> runAnalysis());
        binding.btnExportStructural.setOnClickListener(v -> exportResults());
        binding.btnClearStructuralLog.setOnClickListener(v -> logger.clear());
        binding.btnCopyStructuralLog.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Log copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        binding.btnShowBMD.setOnClickListener(v -> binding.diagramView.setDiagramType(1));
        binding.btnShowSFD.setOnClickListener(v -> binding.diagramView.setDiagramType(2));
        binding.btnShowAFD.setOnClickListener(v -> binding.diagramView.setDiagramType(3));

        // Sample Model Button
        binding.btnSampleModel.setOnClickListener(v -> {
            loadDefaultTestCase();
        });
    }

    private void exportResults() {
        File workDir = requireContext().getFilesDir();
        File reportFile = new File(workDir, "Structural_Report.pdf");
        
        // Comprehensive PDF report including results and log
        StringBuilder reportContent = new StringBuilder();
        reportContent.append("STRUCTURE TYPE: ").append(binding.spinnerStructureType.getSelectedItem().toString()).append("\n\n");
        reportContent.append(binding.tvStructuralResultSummary.getText().toString()).append("\n\n");
        reportContent.append("--- FULL SOLVER LOG ---\n").append(logger.getFullLog());

        com.diamon.civil.engine.ReportGenerator.generateReport(reportFile, "Structural Analysis Report (SAP2000-style)", reportContent.toString(), null);

        File[] files = workDir.listFiles((dir, name) -> name.startsWith("structural_job") || name.equals("Structural_Report.pdf"));
        if (files != null && files.length > 0) {
            com.diamon.civil.util.export.ExportManager manager = new com.diamon.civil.util.export.ExportManager(requireContext());
            for (File f : files) {
                manager.exportToDownloads(f, "Structural_Analysis");
            }
            Toast.makeText(getContext(), "Exported to Downloads/FEA_Suite", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getContext(), "No results to export", Toast.LENGTH_SHORT).show();
        }
    }

    private void runAnalysis() {
        if (binding.spinnerStructureType.getSelectedItem() == null) {
            Toast.makeText(getContext(), "Please select a structure type", Toast.LENGTH_SHORT).show();
            return;
        }

        String nodesStr = binding.etNodes.getText().toString().trim();
        String elementsStr = binding.etElements.getText().toString().trim();

        if (nodesStr.isEmpty() || elementsStr.isEmpty()) {
            Toast.makeText(getContext(), "Please define nodes and elements", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.pbStructural.setVisibility(View.VISIBLE);
        binding.btnSolveStructural.setEnabled(false);
        logger.info("Starting Structural Analysis...");

        executor.execute(() -> {
            NativeFeaCore core = new NativeFeaCore();
            long modelPtr = core.createModel();
            try {
                StructuralModel model = parseInputs(nodesStr, elementsStr);
                String jsonModel = modelToJson(model);
                core.modelFromJson(modelPtr, jsonModel);
                
                logger.info("Assembling CalculiX Input (.inp)...");
                String inpContent = core.modelToInp(modelPtr);
                File inpFile = new File(requireContext().getFilesDir(), "structural_job.inp");
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(inpFile)) {
                    fos.write(inpContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                
                logger.info("Executing CalculiX Solver (ccx)...");
                if (calculixExecutor == null) {
                    calculixExecutor = new CalculixExecutor(requireContext());
                }
                String result = calculixExecutor.executeCalculix("structural_job");
                logger.log(result);

                File datFile = new File(requireContext().getFilesDir(), "structural_job.dat");
                if (datFile.exists()) {
                    String nativeSummary = core.parseDatResults(datFile.getAbsolutePath());
                    DatParser.ParseResult parseResult = datParser.parse(datFile);
                    if (isAdded() && binding != null) {
                        getActivity().runOnUiThread(() -> {
                            binding.tvStructuralResultSummary.setText("NATIVE SUMMARY:\n" + nativeSummary + "\n\n" + datParser.formatSummary(parseResult));
                            binding.diagramView.setModelAndResults(model, parseResult);
                            binding.diagramView.setDiagramType(1);
                        });
                    }
                }

                if (isAdded() && binding != null) {
                    getActivity().runOnUiThread(() -> {
                        binding.pbStructural.setVisibility(View.GONE);
                        binding.btnSolveStructural.setEnabled(true);
                        Toast.makeText(getContext(), "Analysis Complete", Toast.LENGTH_SHORT).show();
                    });
                }

            } catch (Exception e) {
                logger.error(e.getMessage());
                if (isAdded() && binding != null) {
                    getActivity().runOnUiThread(() -> {
                        binding.pbStructural.setVisibility(View.GONE);
                        binding.btnSolveStructural.setEnabled(true);
                    });
                }
            } finally {
                if (modelPtr != 0) core.deleteModel(modelPtr);
            }
        });
    }

    private String modelToJson(StructuralModel model) {
        String selection = binding.spinnerStructureType.getSelectedItem().toString();
        String elementType = "B32"; // Default
        if (selection.contains("B32")) elementType = "B32";
        else if (selection.contains("T2D2")) elementType = "T2D2";
        else if (selection.contains("B31")) elementType = "B31";

        StringBuilder sb = new StringBuilder();
        sb.append("{ \"nodes\": [");
        for (int i = 0; i < model.nodes.size(); i++) {
            StructuralModel.Node n = model.nodes.get(i);
            sb.append(String.format("{\"id\":%d,\"x\":%f,\"y\":%f,\"z\":%f}", n.id, n.x, n.y, n.z));
            if (i < model.nodes.size() - 1) sb.append(",");
        }
        sb.append("], \"elements\": [");
        for (int i = 0; i < model.elements.size(); i++) {
            StructuralModel.Element e = model.elements.get(i);
            // Use the selected element type
            sb.append(String.format("{\"id\":%d,\"type\":\"%s\",\"nodes\":[%d,%d]}", e.id, elementType, e.node1Id, e.node2Id));
            if (i < model.elements.size() - 1) sb.append(",");
        }
        sb.append("], \"materials\": [{\"name\":\"Steel\",\"youngModulus\":210000,\"poissonRatio\":0.3,\"density\":7850}],");
        sb.append("\"sections\": [{\"elset\":\"ALL\",\"type\":\"BEAM\",\"material\":\"Steel\",\"params\":[200,200]}]");
        sb.append("}");
        return sb.toString();
    }

    private StructuralModel parseInputs(String nodes, String elements) {
        StructuralModel model = new StructuralModel();
        String[] nodeLines = nodes.split("\n");
        for (String line : nodeLines) {
            String[] p = line.split(",");
            if (p.length >= 3) {
                try {
                    model.nodes.add(new StructuralModel.Node(
                        Integer.parseInt(p[0].trim()),
                        Double.parseDouble(p[1].trim()),
                        Double.parseDouble(p[2].trim()),
                        p.length > 3 ? Double.parseDouble(p[3].trim()) : 0.0
                    ));
                } catch (Exception ignore) {}
            }
        }
        String[] elemLines = elements.split("\n");
        for (String line : elemLines) {
            String[] p = line.split(",");
            if (p.length >= 3) {
                try {
                    model.elements.add(new StructuralModel.Element(
                        Integer.parseInt(p[0].trim()),
                        Integer.parseInt(p[1].trim()),
                        Integer.parseInt(p[2].trim()),
                        "HEB200", "Steel"
                    ));
                } catch (Exception ignore) {}
            }
        }
        return model;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
