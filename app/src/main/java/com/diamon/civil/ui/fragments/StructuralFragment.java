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
    private StructuralModel currentModel;
    private DatParser.ParseResult currentResult;

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
        if (binding.gridEditorView != null) {
            binding.gridEditorView.clear();
        }
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
        
        binding.btnClearGrid.setOnClickListener(v -> {
            if (binding.gridEditorView != null) {
                binding.gridEditorView.clear();
            }
        });
        
        binding.scrollStructuralLog.setOnClickListener(v -> copyToClipboard(logger.getFullLog()));
        binding.btnViewWireframe.setOnClickListener(v -> {
            binding.frameGLView.setShowDeformed(false);
            binding.frameGLView.setShowDiagrams(false);
        });
        
        binding.btnViewDeformed.setOnClickListener(v -> {
            binding.frameGLView.setShowDeformed(true);
            binding.frameGLView.setShowDiagrams(false);
        });
        
        binding.btnViewDiagrams.setOnClickListener(v -> {
            binding.frameGLView.setShowDeformed(true);
            binding.frameGLView.setShowDiagrams(true);
        });
        
        binding.tvStructuralLog.setOnClickListener(v -> copyToClipboard(logger.getFullLog()));
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
        File reportFile = new File(workDir, "Structural_Report_SAP2000.pdf");
        
        if (currentModel == null || currentResult == null) {
            Toast.makeText(getContext(), "Please run analysis first", Toast.LENGTH_SHORT).show();
            return;
        }

        com.diamon.civil.util.export.PDFReportGenerator generator = new com.diamon.civil.util.export.PDFReportGenerator();
        boolean success = generator.generateReport(getContext(), currentModel, currentResult, "Structural Frame Analysis", "Calculo Estructural User", reportFile);

        if (success) {
            com.diamon.civil.util.export.ExportManager manager = new com.diamon.civil.util.export.ExportManager(getContext());
            manager.exportToDownloads(reportFile);
            Toast.makeText(getContext(), "Exported PDF to Downloads", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getContext(), "Failed to export PDF", Toast.LENGTH_SHORT).show();
        }
    }

    private void runAnalysis() {
        if (getContext() == null || binding == null) return;
        
        if (binding.spinnerStructureType.getSelectedItem() == null) {
            Toast.makeText(getContext(), "Please select a structure type", Toast.LENGTH_SHORT).show();
            return;
        }

        final String structureType = binding.spinnerStructureType.getSelectedItem().toString();

        StructuralModel uiModel = new StructuralModel();
        if (binding.gridEditorView != null) {
            uiModel.nodes.addAll(binding.gridEditorView.getNodes());
            uiModel.elements.addAll(binding.gridEditorView.getElements());
        }

        if (uiModel.nodes.isEmpty() || uiModel.elements.isEmpty()) {
            Toast.makeText(getContext(), "Please define nodes and elements in the grid", Toast.LENGTH_SHORT).show();
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
                StructuralModel model = uiModel;
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
                File frdFile = new File(filesDir, "structural_job.frd");
                if (datFile.exists()) {
                    DatParser.ParseResult parseResult = datParser.parse(datFile);
                    if (frdFile.exists()) {
                        com.diamon.civil.engine.FrdParser.ParseResult frdResult = new com.diamon.civil.engine.FrdParser().parse(frdFile);
                        if (frdResult.forces != null) {
                            parseResult.forces = new java.util.ArrayList<>();
                            for (com.diamon.civil.engine.FrdParser.SectionForces f : frdResult.forces) {
                                DatParser.SectionForces sf = new DatParser.SectionForces();
                                sf.elementId = f.elementId;
                                sf.M1 = f.bendingMoment1;
                                sf.M2 = f.bendingMoment2;
                                sf.M3 = f.torque;
                                sf.V2 = f.shear1;
                                sf.V3 = f.shear2;
                                sf.N = f.axialNormal;
                                parseResult.forces.add(sf);
                            }
                        }
                    }
                    currentModel = model;
                    currentResult = parseResult;
                    calculateVBOs(model, parseResult);
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
        // Automatically interpolate a third node to use B32 quadratic beam elements
        String elementType = "B32";

        StringBuilder sb = new StringBuilder();
        sb.append("{ \"nodes\": [");
        
        int maxNodeId = 0;
        for (int i = 0; i < model.nodes.size(); i++) {
            StructuralModel.Node n = model.nodes.get(i);
            if (n.id > maxNodeId) maxNodeId = n.id;
            sb.append(String.format(java.util.Locale.US, "{\"id\":%d,\"x\":%f,\"y\":%f,\"z\":%f}", n.id, n.x, n.y, n.z));
            sb.append(",");
        }
        
        // List to hold the 3 node IDs for each element
        java.util.List<int[]> elementNodes = new java.util.ArrayList<>();
        for (int i = 0; i < model.elements.size(); i++) {
            StructuralModel.Element e = model.elements.get(i);
            StructuralModel.Node n1 = null, n2 = null;
            for (StructuralModel.Node n : model.nodes) {
                if (n.id == e.node1Id) n1 = n;
                if (n.id == e.node2Id) n2 = n;
            }
            if (n1 != null && n2 != null) {
                maxNodeId++;
                int midNodeId = maxNodeId;
                double mx = (n1.x + n2.x) / 2.0;
                double my = (n1.y + n2.y) / 2.0;
                double mz = (n1.z + n2.z) / 2.0;
                sb.append(String.format(java.util.Locale.US, "{\"id\":%d,\"x\":%f,\"y\":%f,\"z\":%f}", midNodeId, mx, my, mz));
                if (i < model.elements.size() - 1) sb.append(",");
                
                elementNodes.add(new int[]{e.node1Id, e.node2Id, midNodeId});
            } else {
                elementNodes.add(new int[]{e.node1Id, e.node2Id}); // Fallback
            }
        }
        
        sb.append("], \"elements\": [");
        for (int i = 0; i < model.elements.size(); i++) {
            StructuralModel.Element e = model.elements.get(i);
            int[] nodes = elementNodes.get(i);
            if (nodes.length == 3) {
                sb.append(String.format(java.util.Locale.US, "{\"id\":%d,\"type\":\"%s\",\"elset\":\"Eall\",\"nodes\":[%d,%d,%d]}", e.id, elementType, nodes[0], nodes[1], nodes[2]));
            } else {
                sb.append(String.format(java.util.Locale.US, "{\"id\":%d,\"type\":\"B31\",\"elset\":\"Eall\",\"nodes\":[%d,%d]}", e.id, nodes[0], nodes[1]));
            }
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

    private void calculateVBOs(StructuralModel model, DatParser.ParseResult res) {
        float dispScale = 1000f; 
        
        java.util.List<Float> defLines = new java.util.ArrayList<>();
        java.util.List<Float> defColors = new java.util.ArrayList<>();
        
        java.util.List<Float> diagLines = new java.util.ArrayList<>();
        java.util.List<Float> diagColors = new java.util.ArrayList<>();
        
        java.util.Map<Integer, DatParser.NodeDisplacement> dispMap = new java.util.HashMap<>();
        if (res.displacements != null) {
            for (DatParser.NodeDisplacement nd : res.displacements) {
                dispMap.put(nd.nodeId, nd);
            }
        }
        
        java.util.Map<Integer, DatParser.SectionForces> forceMap = new java.util.HashMap<>();
        if (res.forces != null) {
            for (DatParser.SectionForces f : res.forces) {
                forceMap.put(f.elementId, f);
            }
        }

        for (StructuralModel.Element elem : model.elements) {
            StructuralModel.Node n1 = null, n2 = null;
            for (StructuralModel.Node n : model.nodes) {
                if (n.id == elem.node1Id) n1 = n;
                if (n.id == elem.node2Id) n2 = n;
            }
            if (n1 == null || n2 == null) continue;
            
            DatParser.NodeDisplacement d1 = dispMap.get(n1.id);
            DatParser.NodeDisplacement d2 = dispMap.get(n2.id);
            
            float dx1 = d1 != null ? (float)d1.ux * dispScale : 0;
            float dy1 = d1 != null ? (float)d1.uy * dispScale : 0;
            float dz1 = d1 != null ? (float)d1.uz * dispScale : 0;
            
            float dx2 = d2 != null ? (float)d2.ux * dispScale : 0;
            float dy2 = d2 != null ? (float)d2.uy * dispScale : 0;
            float dz2 = d2 != null ? (float)d2.uz * dispScale : 0;
            
            defLines.add((float)n1.x + dx1); defLines.add((float)n1.y + dy1); defLines.add((float)n1.z + dz1);
            defLines.add((float)n2.x + dx2); defLines.add((float)n2.y + dy2); defLines.add((float)n2.z + dz2);
            defColors.add(1f); defColors.add(0f); defColors.add(0f); defColors.add(1f);
            defColors.add(1f); defColors.add(0f); defColors.add(0f); defColors.add(1f);
            
            DatParser.SectionForces sf = forceMap.get(elem.id);
            if (sf != null) {
                float M = (float)sf.M1;
                float offsetScale = 0.005f;
                float offset = M * offsetScale;
                
                float L = (float)Math.hypot(n2.x - n1.x, n2.y - n1.y);
                if (L > 1e-4) {
                    float nx = (float)(-(n2.y - n1.y)/L) * offset;
                    float ny = (float)((n2.x - n1.x)/L) * offset;
                    
                    diagLines.add((float)n1.x); diagLines.add((float)n1.y); diagLines.add((float)n1.z);
                    diagLines.add((float)n1.x + nx); diagLines.add((float)n1.y + ny); diagLines.add((float)n1.z);
                    
                    diagLines.add((float)n1.x + nx); diagLines.add((float)n1.y + ny); diagLines.add((float)n1.z);
                    diagLines.add((float)n2.x + nx); diagLines.add((float)n2.y + ny); diagLines.add((float)n2.z);
                    
                    diagLines.add((float)n2.x + nx); diagLines.add((float)n2.y + ny); diagLines.add((float)n2.z);
                    diagLines.add((float)n2.x); diagLines.add((float)n2.y); diagLines.add((float)n2.z);
                    
                    for (int k=0; k<6; k++) {
                        diagColors.add(1f); diagColors.add(0f); diagColors.add(1f); diagColors.add(1f);
                    }
                }
            }
        }
        
        float[] defPosArray = new float[defLines.size()];
        for (int i=0; i<defLines.size(); i++) defPosArray[i] = defLines.get(i);
        float[] defColArray = new float[defColors.size()];
        for (int i=0; i<defColors.size(); i++) defColArray[i] = defColors.get(i);
        
        float[] diagPosArray = new float[diagLines.size()];
        for (int i=0; i<diagLines.size(); i++) diagPosArray[i] = diagLines.get(i);
        float[] diagColArray = new float[diagColors.size()];
        for (int i=0; i<diagColors.size(); i++) diagColArray[i] = diagColors.get(i);
        
        android.app.Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (binding != null) {
                    binding.frameGLView.setDeformedShape(defPosArray, defColArray);
                    binding.frameGLView.setDiagrams(diagPosArray, diagColArray);
                    binding.frameGLView.setShowDeformed(true);
                    binding.frameGLView.setShowDiagrams(true);
                    
                    binding.diagramView.setVisibility(android.view.View.GONE);
                }
            });
        }
    }

    @Override
    public void onDestroyView() {
        executor.shutdownNow();
        super.onDestroyView();
        binding = null;
    }
}
