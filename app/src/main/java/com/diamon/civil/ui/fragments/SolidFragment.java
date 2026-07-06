package com.diamon.civil.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.diamon.civil.databinding.FragmentSolidBinding;
import com.diamon.civil.engine.CalculixExecutor;
import com.diamon.civil.engine.GmshRunner;
import com.diamon.civil.engine.OcctPrimitivesJNI;
import com.diamon.civil.engine.NativeFeaCore;
import com.diamon.civil.ui.MainActivity;
import com.diamon.civil.ui.SceneViewBridgeKt;
import com.diamon.civil.util.logging.ModuleLogger;
import com.google.android.material.tabs.TabLayout;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SolidFragment extends Fragment {

    private FragmentSolidBinding binding;
    private final ModuleLogger logger = new ModuleLogger();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private GmshRunner gmshRunner;
    private CalculixExecutor calculixExecutor;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSolidBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        gmshRunner = new GmshRunner(requireContext().getFilesDir(), new File(requireContext().getApplicationInfo().nativeLibraryDir));
        calculixExecutor = new CalculixExecutor(requireContext());
        logger.attachToTextView(binding.tvSolidLog);

        setupTabs();
        setupButtons();
        
        SceneViewBridgeKt.setSceneViewContent(binding.solidSceneViewContainer, "models/test_beam.glb", (MainActivity) getActivity());
    }

    private void setupTabs() {
        binding.tabLayoutSolid.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                binding.layoutSolidParams.setVisibility(tab.getPosition() == 0 ? View.VISIBLE : View.GONE);
                binding.solidSceneViewContainer.setVisibility(tab.getPosition() == 1 ? View.VISIBLE : View.GONE);
                binding.layoutSolidLog.setVisibility(tab.getPosition() == 2 ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupButtons() {
        binding.btnCreateBox.setOnClickListener(v -> createPrimitive("box"));
        binding.btnCreateCylinder.setOnClickListener(v -> createPrimitive("cylinder"));
        binding.btnCreateSphere.setOnClickListener(v -> createPrimitive("sphere"));
        
        binding.btnRunSolidAnalysis.setOnClickListener(v -> runFullPipeline());
        binding.btnExportSolid.setOnClickListener(v -> exportResults());
        
        binding.btnClearSolidLog.setOnClickListener(v -> logger.clear());
        binding.btnCopySolidLog.setOnClickListener(v -> Toast.makeText(getContext(), "Log copied", Toast.LENGTH_SHORT).show());
    }

    private void exportResults() {
        File workDir = requireContext().getFilesDir();
        File reportFile = new File(workDir, "Solid_Analysis_Report.pdf");
        
        // Comprehensive PDF report including results and log
        StringBuilder reportContent = new StringBuilder();
        reportContent.append("ELEMENT TYPE: ").append(binding.spinnerElementType.getSelectedItem().toString()).append("\n\n");
        reportContent.append("--- FULL SOLVER LOG ---\n").append(logger.getFullLog());

        com.diamon.civil.engine.ReportGenerator.generateReport(reportFile, "3D Solid Analysis Report (Abacus-style)", reportContent.toString(), null);

        File[] files = workDir.listFiles((dir, name) -> name.startsWith("job_solid") || name.endsWith(".brep") || name.endsWith(".msh") || name.equals("Solid_Analysis_Report.pdf"));
        if (files != null && files.length > 0) {
            com.diamon.civil.util.export.ExportManager manager = new com.diamon.civil.util.export.ExportManager(requireContext());
            for (File f : files) {
                manager.exportToDownloads(f, "Solid_Analysis");
            }
            Toast.makeText(getContext(), "Exported to Downloads/FEA_Suite/Solid_Analysis", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getContext(), "No files to export", Toast.LENGTH_SHORT).show();
        }
    }

    private void createPrimitive(String type) {
        executor.execute(() -> {
            File outFile = new File(requireContext().getFilesDir(), type + ".brep");
            boolean success = false;
            if (type.equals("box")) success = OcctPrimitivesJNI.createBox(10, 10, 10, outFile.getAbsolutePath());
            else if (type.equals("cylinder")) success = OcctPrimitivesJNI.createCylinder(5, 10, outFile.getAbsolutePath());
            else if (type.equals("sphere")) success = OcctPrimitivesJNI.createSphere(5, outFile.getAbsolutePath());

            if (success) {
                logger.info("Created primitive: " + type);
                getActivity().runOnUiThread(() -> Toast.makeText(getContext(), type.toUpperCase() + " created", Toast.LENGTH_SHORT).show());
            } else {
                logger.error("Failed to create " + type);
            }
        });
    }

    private void runFullPipeline() {
        binding.pbSolid.setVisibility(View.VISIBLE);
        binding.btnRunSolidAnalysis.setEnabled(false);
        String elementType = binding.spinnerElementType.getSelectedItem().toString();
        logger.info("Starting Pipeline for: " + elementType);

        File workDir = requireContext().getFilesDir();
        File cadFile = new File(workDir, "box.brep");
        if (!cadFile.exists()) {
            logger.error("Simulation Error: Source geometry (box.brep) not found.");
            binding.pbSolid.setVisibility(View.GONE);
            binding.btnRunSolidAnalysis.setEnabled(true);
            return;
        }

        int density = binding.seekbarMeshDensity.getProgress() + 1;
        logger.info("Step 1: Generating Mesh with Gmsh (Density: " + density + ")...");
        
        gmshRunner.meshAsync(cadFile, density, new GmshRunner.GmshCallback() {
            @Override
            public void onSuccess(File mshFile) {
                logger.info("Mesh OK: " + mshFile.getName());
                executor.execute(() -> {
                    try {
                        logger.info("Step 2: Assembling CalculiX Input (.inp)...");
                        String rawInpPath = workDir.getAbsolutePath() + "/job_solid_raw.inp";
                        String gmshResult = calculixExecutor.executeBinary("gmsh", 
                            cadFile.getAbsolutePath(), "-3", "-format", "inp", "-o", rawInpPath);
                        logger.debug(gmshResult);

                        com.diamon.civil.engine.InpAssembler.assemble(workDir, "job_solid", "Steel", 210000, 0.3, -100.0);
                        
                        logger.info("Step 3: Running CalculiX Solver (ccx)...");
                        String ccxResult = calculixExecutor.executeCalculix("job_solid");
                        logger.log(ccxResult);

                        File frdFile = new File(workDir, "job_solid.frd");
                        if (frdFile.exists()) {
                            logger.info("Step 4: Parsing Engineering Results (Native)...");
                            NativeFeaCore core = new NativeFeaCore();
                            String summary = core.parseFrdSummary(frdFile.getAbsolutePath());
                            logger.info("NATIVE SIMULATION SUMMARY:\n" + summary);
                            
                            File glbFile = new File(workDir, "job_solid.glb");
                            if (calculixExecutor.convertFrdToGlb(frdFile.getAbsolutePath(), glbFile.getAbsolutePath())) {
                                getActivity().runOnUiThread(() -> {
                                    SceneViewBridgeKt.setSceneViewContent(binding.solidSceneViewContainer, glbFile.getAbsolutePath(), (MainActivity) getActivity());
                                });
                            }
                        }

                        getActivity().runOnUiThread(() -> {
                            binding.pbSolid.setVisibility(View.GONE);
                            binding.btnRunSolidAnalysis.setEnabled(true);
                            Toast.makeText(getContext(), "Simulation Complete", Toast.LENGTH_SHORT).show();
                        });

                    } catch (Exception e) {
                        logger.error("Pipeline Failure: " + e.getMessage());
                        getActivity().runOnUiThread(() -> {
                            binding.pbSolid.setVisibility(View.GONE);
                            binding.btnRunSolidAnalysis.setEnabled(true);
                        });
                    }
                });
            }

            @Override
            public void onError(String message) {
                logger.error("Meshing Failed: " + message);
                getActivity().runOnUiThread(() -> {
                    binding.pbSolid.setVisibility(View.GONE);
                    binding.btnRunSolidAnalysis.setEnabled(true);
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        gmshRunner.shutdown();
        binding = null;
    }
}
