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
    private final ModuleLogger logger = new ModuleLogger("Structural");
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
        
        final android.content.Context appContext = requireContext().getApplicationContext();
        executor.execute(() -> {
            try {
                NativeFeaCore.loadLibraries();
                calculixExecutor = new CalculixExecutor(appContext);
                android.app.Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        if (isAdded() && binding != null) {
                            logger.info("Structural engine initialized");
                        }
                    });
                }
            } catch (Throwable e) {
                logger.error("Initialization failed", e);
            }
        });
        datParser = new DatParser();
        logger.attachToTextView(binding.tvStructuralLog);

        setupTabs();
        setupButtons();
        loadDefaultTestCase();
    }

    private void loadDefaultTestCase() {
        if (binding == null) return;
        // Solvable B31 cantilever: fixed at node 1 and load at node 2.
        binding.spinnerStructureType.setSelection(0);
        binding.etNodes.setText("1, 0, 0, 0\n2, 10, 0, 0");
        binding.etElements.setText("1, 1, 2");
    }

    private void setupTabs() {
        binding.tabLayoutStructural.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (binding == null) return;
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
        binding.fabClearStructuralLog.setOnClickListener(v -> logger.clear());
        
        binding.scrollStructuralLog.setOnClickListener(v -> copyToClipboard(logger.getFullLog()));
        binding.tvStructuralLog.setOnClickListener(v -> copyToClipboard(logger.getFullLog()));

        binding.btnShowBMD.setOnClickListener(v -> binding.diagramView.setDiagramType(1));
        binding.btnShowSFD.setOnClickListener(v -> binding.diagramView.setDiagramType(2));
        binding.btnShowAFD.setOnClickListener(v -> binding.diagramView.setDiagramType(3));
    }


    private void copyToClipboard(String text) {
        if (getContext() == null) return;
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("FEA Log", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Log copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    public void exportResults() {
        if (getContext() == null) return;
        File workDir = getContext().getFilesDir();
        File reportFile = new File(workDir, "Structural_Report.pdf");
        
        // Comprehensive PDF report including results and log
        StringBuilder reportContent = new StringBuilder();
        reportContent.append("STRUCTURE TYPE: ").append(binding.spinnerStructureType.getSelectedItem() != null ? binding.spinnerStructureType.getSelectedItem().toString() : "N/A").append("\n\n");
        reportContent.append("--- FULL SOLVER LOG ---\n").append(logger.getFullLog());

        com.diamon.civil.engine.ReportGenerator.generateReport(reportFile, "Structural Analysis Report SAP2000 style", reportContent.toString(), null);

        File[] files = workDir.listFiles((dir, name) -> name.startsWith("structural_job") || name.equals("Structural_Report.pdf"));
        if (files != null && files.length > 0) {
            com.diamon.civil.util.export.ExportManager manager = new com.diamon.civil.util.export.ExportManager(getContext());
            for (File f : files) {
                manager.exportToDownloads(f);
            }
            Toast.makeText(getContext(), "Exported to Downloads/Structural_Analysis_FEA_Advanced", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getContext(), "No results to export", Toast.LENGTH_SHORT).show();
        }
    }

    private void runAnalysis() {
        if (getContext() == null || binding == null) return;
        
        if (binding.spinnerStructureType.getSelectedItem() == null) {
            Toast.makeText(getContext(), "Please select a structure type", Toast.LENGTH_SHORT).show();
            return;
        }

        final String nodesStr = binding.etNodes.getText().toString().trim();
        final String elementsStr = binding.etElements.getText().toString().trim();
        final String structureType = binding.spinnerStructureType.getSelectedItem().toString();

        if (nodesStr.isEmpty() || elementsStr.isEmpty()) {
            Toast.makeText(getContext(), "Please define nodes and elements", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.pbStructural.setVisibility(View.VISIBLE);
        binding.btnSolveStructural.setEnabled(false);
        logger.info("Starting Structural Analysis...");

        final android.content.Context appContext = getContext().getApplicationContext();
        final File filesDir = getContext().getFilesDir();

        executor.execute(() -> {
            NativeFeaCore core = new NativeFeaCore();
            long modelPtr = 0;
            try {
                modelPtr = core.createModel();
                StructuralModel model = parseInputs(nodesStr, elementsStr);
                validateModel(model);
                String jsonModel = modelToJson(model, structureType);
                core.modelFromJson(modelPtr, jsonModel);
                
                logger.info("Assembling CalculiX Input INP...");
                String inpContent = core.modelToInp(modelPtr);
                File inpFile = new File(filesDir, "structural_job.inp");
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(inpFile)) {
                    fos.write(inpContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                
                logger.info("Executing CalculiX Solver ccx...");
                if (calculixExecutor == null) {
                    calculixExecutor = new CalculixExecutor(appContext);
                }
                String result = calculixExecutor.executeCalculix("structural_job");
                logger.log(result);

                File datFile = new File(filesDir, "structural_job.dat");
                if (datFile.exists()) {
                    DatParser.ParseResult parseResult = datParser.parse(datFile);
                    
                    android.app.Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            if (binding != null) {
                                binding.diagramView.setModelAndResults(model, parseResult);
                                binding.diagramView.setDiagramType(1);
                            }
                        });
                    }
                }

                android.app.Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        if (binding != null) {
                            binding.pbStructural.setVisibility(View.GONE);
                            binding.btnSolveStructural.setEnabled(true);
                            Toast.makeText(appContext, "Analysis Complete", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

            } catch (Throwable e) {
                logger.error("Analysis Error: " + e.getMessage());
                android.app.Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        if (binding != null) {
                            binding.pbStructural.setVisibility(View.GONE);
                            binding.btnSolveStructural.setEnabled(true);
                        }
                    });
                }
            } finally {
                if (modelPtr != 0) {
                    try {
                        core.deleteModel(modelPtr);
                    } catch (Throwable error) {
                        logger.error("No se pudo liberar el modelo nativo: " + error.getMessage());
                    }
                }
            }
        });
    }

    private String modelToJson(StructuralModel model, String structureType) {
        // The editor accepts two nodes per element, therefore it intentionally
        // exposes B31 only.  B32 was incompatible and produced invalid jobs.
        String elementType = "B31";

        StringBuilder sb = new StringBuilder();
        sb.append("{ \"nodes\": [");
        for (int i = 0; i < model.nodes.size(); i++) {
            StructuralModel.Node n = model.nodes.get(i);
            sb.append(String.format(java.util.Locale.US, "{\"id\":%d,\"x\":%f,\"y\":%f,\"z\":%f}", n.id, n.x, n.y, n.z));
            if (i < model.nodes.size() - 1) sb.append(",");
        }
        sb.append("], \"elements\": [");
        for (int i = 0; i < model.elements.size(); i++) {
            StructuralModel.Element e = model.elements.get(i);
            // Explicitly set elset to Eall for results parsing
            sb.append(String.format(java.util.Locale.US, "{\"id\":%d,\"type\":\"%s\",\"elset\":\"Eall\",\"nodes\":[%d,%d]}", e.id, elementType, e.node1Id, e.node2Id));
            if (i < model.elements.size() - 1) sb.append(",");
        }
        sb.append("], \"materials\": [{\"name\":\"Steel\",\"youngModulus\":210000,\"poissonRatio\":0.3,\"density\":7850}],");
        sb.append("\"sections\": [{\"elset\":\"Eall\",\"type\":\"BEAM\",\"material\":\"Steel\",\"params\":[200,200]}],");
        
        sb.append("\"constraints\": [");
        boolean firstConstraint = true;
        for (StructuralModel.Node node : model.nodes) {
            if (node.y == 0.0) { // Fijar la base del pórtico
                if (!firstConstraint) sb.append(",");
                sb.append("{\"nodeId\":").append(node.id).append(",\"dofs\":[1,2,3,4,5,6],\"value\":0}");
                firstConstraint = false;
            }
        }
        sb.append("],");
        
        // Carga lateral en el nodo superior izquierdo
        int loadedNodeId = model.nodes.get(model.nodes.size() - 1).id;
        double maxY = 0;
        double minX = Double.MAX_VALUE;
        for (StructuralModel.Node node : model.nodes) {
            if (node.y > maxY) maxY = node.y;
        }
        for (StructuralModel.Node node : model.nodes) {
            if (node.y == maxY && node.x < minX) {
                minX = node.x;
                loadedNodeId = node.id;
            }
        }
        sb.append("\"loads\": [{\"nodeId\":").append(loadedNodeId)
                .append(",\"fx\":10000,\"fy\":0,\"fz\":0}]");
        sb.append("}");
        return sb.toString();
    }

    private void validateModel(StructuralModel model) {
        if (model.nodes.size() < 2) {
            throw new IllegalArgumentException("Defina al menos dos nodos válidos");
        }
        if (model.elements.isEmpty()) {
            throw new IllegalArgumentException("Defina al menos un elemento válido");
        }
        java.util.HashSet<Integer> nodeIds = new java.util.HashSet<>();
        for (StructuralModel.Node node : model.nodes) {
            nodeIds.add(node.id);
        }
        for (StructuralModel.Element element : model.elements) {
            if (!nodeIds.contains(element.node1Id) || !nodeIds.contains(element.node2Id)) {
                throw new IllegalArgumentException("El elemento " + element.id + " referencia nodos inexistentes");
            }
        }
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
        executor.shutdownNow();
        super.onDestroyView();
        binding = null;
    }
}
