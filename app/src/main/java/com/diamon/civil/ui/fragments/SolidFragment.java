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
import com.diamon.civil.engine.SampleSimulationCase;
import com.diamon.civil.ui.MainActivity;
import com.diamon.civil.ui.SceneViewBridgeKt;
import com.diamon.civil.util.logging.ModuleLogger;
import com.google.android.material.tabs.TabLayout;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SolidFragment extends Fragment {

    private FragmentSolidBinding binding;
    private final ModuleLogger logger = new ModuleLogger("Solid");
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private GmshRunner gmshRunner;
    private CalculixExecutor calculixExecutor;
    private volatile boolean engineReady;
    private volatile File activeSimulationGeometry;
    private volatile String modelPath = "models/test_beam.glb";
    private File workDir;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSolidBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        final android.content.Context appContext = requireContext().getApplicationContext();
        final File nativeLibDir = new File(requireContext().getApplicationInfo().nativeLibraryDir);
        final File filesDir = requireContext().getFilesDir();
        workDir = filesDir;

        logger.attachToTextView(binding.tvSolidLog);
        setupTabs();
        setupButtons();
        binding.btnRunSolidAnalysis.setEnabled(false);

        executor.execute(() -> {
            try {
                // Load only the JNI dependencies; Gmsh and ccx remain child processes.
                NativeFeaCore.loadLibraries();
                gmshRunner = new GmshRunner(filesDir, nativeLibDir);
                calculixExecutor = new CalculixExecutor(appContext);
                activeSimulationGeometry = SampleSimulationCase.createCantileverGeo(filesDir);
                
                android.app.Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        if (isAdded() && binding != null) {
                            engineReady = true;
                            binding.btnRunSolidAnalysis.setEnabled(true);
                            logger.info("Native engines initialized successfully");
                            loadDefaultTestCase();
                        }
                    });
                }
            } catch (Throwable e) {
                logger.error("Initialization Error: " + e.getMessage());
                android.app.Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        if (binding != null) {
                            binding.btnRunSolidAnalysis.setEnabled(false);
                        }
                    });
                }
            }
        });
    }

    private void loadDefaultTestCase() {
        if (binding == null) return;
        binding.seekbarMeshDensity.setProgress(2);
        binding.spinnerElementType.setSelection(0);
        binding.etSolidModulus.setText("210000");
        logger.info("Caso de prueba listo: voladizo 3D de acero, apoyo fijo y carga vertical.");
    }

    private void setupTabs() {
        binding.tabLayoutSolid.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (binding == null) return;
                binding.layoutSolidParams.setVisibility(tab.getPosition() == 0 ? View.VISIBLE : View.GONE);
                binding.solidSceneViewContainer.setVisibility(tab.getPosition() == 1 ? View.VISIBLE : View.GONE);
                binding.layoutSolidLog.setVisibility(tab.getPosition() == 2 ? View.VISIBLE : View.GONE);
                if (tab.getPosition() == 1) {
                    showModelInViewer();
                }
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
        binding.btnCopySolidLog.setOnClickListener(v -> {
            copyToClipboard(logger.getFullLog());
        });

        // Professional addition: Sample Model Button
        binding.btnSampleModel.setOnClickListener(v -> {
            if (!engineReady) {
                Toast.makeText(getContext(), "El motor aún se está inicializando", Toast.LENGTH_SHORT).show();
                return;
            }
            executor.execute(() -> {
                try {
                    activeSimulationGeometry = SampleSimulationCase.createCantileverGeo(workDir);
                    android.app.Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            if (binding != null) {
                                loadDefaultTestCase();
                                Toast.makeText(getContext(), "Caso de voladizo cargado y listo para calcular", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (Exception error) {
                    logger.error("No se pudo crear el caso de prueba: " + error.getMessage());
                }
            });
        });
    }

    private void copyToClipboard(String text) {
        if (getContext() == null) return;
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("FEA Log", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportResults() {
        if (getContext() == null) return;
        File workDir = getContext().getFilesDir();
        File reportFile = new File(workDir, "Solid_Analysis_Report.pdf");
        
        String logText = logger.getFullLog();
        if (!logText.isEmpty()) {
            com.diamon.civil.engine.ReportGenerator.generateReport(reportFile, "3D Solid Analysis Report (Abacus-style)", logText, null);
        }

        File[] files = workDir.listFiles((dir, name) -> name.startsWith("job_solid") || name.endsWith(".brep") || name.endsWith(".msh") || name.equals("Solid_Analysis_Report.pdf"));
        if (files != null && files.length > 0) {
            com.diamon.civil.util.export.ExportManager manager = new com.diamon.civil.util.export.ExportManager(getContext());
            for (File f : files) {
                manager.exportToDownloads(f, "Solid_Analysis");
            }
            Toast.makeText(getContext(), "Exported to Downloads/FEA_Suite/Solid_Analysis", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getContext(), "No files to export", Toast.LENGTH_SHORT).show();
        }
    }

    private void createPrimitive(String type) {
        if (getContext() == null) return;
        final String path = new File(workDir, type + ".brep").getAbsolutePath();
        executor.execute(() -> {
            boolean success = false;
            try {
                if (type.equals("box")) success = OcctPrimitivesJNI.createBox(10, 10, 10, path);
                else if (type.equals("cylinder")) success = OcctPrimitivesJNI.createCylinder(5, 10, path);
                else if (type.equals("sphere")) success = OcctPrimitivesJNI.createSphere(5, path);
            } catch (Throwable error) {
                logger.error("Native CAD Error: " + error.getMessage());
            }

            if (success) {
                activeSimulationGeometry = new File(path);
                logger.info("Created primitive: " + type);
                android.app.Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        if (isAdded()) Toast.makeText(getContext(), type.toUpperCase() + " created", Toast.LENGTH_SHORT).show();
                    });
                }
            } else {
                logger.error("Failed to create " + type);
            }
        });
    }

    private void runFullPipeline() {
        if (binding == null || getContext() == null) return;

        if (!engineReady || gmshRunner == null || calculixExecutor == null) {
            Toast.makeText(getContext(), "El motor de análisis aún no está listo", Toast.LENGTH_SHORT).show();
            return;
        }

        if (binding.spinnerElementType.getSelectedItem() == null) {
            Toast.makeText(getContext(), "Please select an element type", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.pbSolid.setVisibility(View.VISIBLE);
        binding.btnRunSolidAnalysis.setEnabled(false);
        
        // Capture ALL UI values on main thread to avoid crashes
        final int density = binding.seekbarMeshDensity.getProgress() + 1;
        final String modulusStr = binding.etSolidModulus.getText().toString().trim();
        
        double youngModulusTemp = 210000.0;
        try {
            if (!modulusStr.isEmpty()) youngModulusTemp = Double.parseDouble(modulusStr);
        } catch (NumberFormatException e) {
            logger.error("Invalid modulus, using default 210GPa");
        }
        final double E = youngModulusTemp;

        logger.info("Starting Pipeline for: Linear Tetrahedron (C3D4)");

        final File workDir = getContext().getFilesDir();
        final File cadFile = activeSimulationGeometry;
        final android.content.Context appContext = getContext().getApplicationContext();

        if (cadFile == null || !cadFile.exists()) {
            logger.error("Simulation Error: no hay geometría de prueba disponible.");
            binding.pbSolid.setVisibility(View.GONE);
            binding.btnRunSolidAnalysis.setEnabled(true);
            return;
        }

        if (!cadFile.getName().endsWith(".geo")) {
            logger.error("La geometría CAD no define superficies Fixed/Loaded. Cargue el caso de prueba antes de resolver.");
            binding.pbSolid.setVisibility(View.GONE);
            binding.btnRunSolidAnalysis.setEnabled(true);
            return;
        }

        logger.info("Step 1: Generating Mesh with Gmsh (Density: " + density + ")...");
        
        gmshRunner.meshAsync(cadFile, density, "job_solid", new GmshRunner.GmshCallback() {
            @Override
            public void onSuccess(File rawInp) {
                logger.info("Mesh OK: " + rawInp.getName());
                executor.execute(() -> {
                    try {
                        logger.info("Step 2: Assembling CalculiX Input (.inp)...");
                        
                        if (!rawInp.exists()) {
                            throw new java.io.FileNotFoundException("No se generó la malla de entrada");
                        }
                        
                        if (calculixExecutor == null) {
                            calculixExecutor = new CalculixExecutor(appContext);
                        }

                        com.diamon.civil.engine.InpAssembler.assemble(workDir, "job_solid", "Steel", E, 0.3, -100.0);
                        
                        logger.info("Step 3: Running CalculiX Solver (ccx)...");
                        String ccxResult = calculixExecutor.executeCalculix("job_solid");
                        logger.log(ccxResult);
                        if (!CalculixExecutor.wasSuccessful(ccxResult)) {
                            throw new IllegalStateException("CalculiX terminó con error; consulte el Solver Log");
                        }

                        File frdFile = new File(workDir, "job_solid.frd");
                        if (frdFile.exists()) {
                            logger.info("Step 4: Parsing Engineering Results (Native)...");
                            NativeFeaCore core = new NativeFeaCore();
                            String summary = core.parseFrdSummary(frdFile.getAbsolutePath());
                            logger.info("NATIVE SIMULATION SUMMARY:\n" + summary);
                            
                            File glbFile = new File(workDir, "job_solid.glb");
                            if (calculixExecutor.convertFrdToGlb(frdFile.getAbsolutePath(), glbFile.getAbsolutePath())) {
                                modelPath = glbFile.getAbsolutePath();
                                android.app.Activity activity = getActivity();
                                if (activity != null) {
                                    activity.runOnUiThread(() -> {
                                        if (binding != null) showModelInViewer();
                                    });
                                }
                            }
                        }

                        android.app.Activity activity = getActivity();
                        if (activity != null) {
                            activity.runOnUiThread(() -> {
                                if (binding != null) {
                                    binding.pbSolid.setVisibility(View.GONE);
                                    binding.btnRunSolidAnalysis.setEnabled(true);
                                    Toast.makeText(appContext, "Simulation Complete", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                    } catch (Throwable e) {
                        logger.error("Pipeline Failure: " + e.getMessage());
                        android.app.Activity activity = getActivity();
                        if (activity != null) {
                            activity.runOnUiThread(() -> {
                                if (binding != null) {
                                    binding.pbSolid.setVisibility(View.GONE);
                                    binding.btnRunSolidAnalysis.setEnabled(true);
                                }
                            });
                        }
                    }
                });
            }

            @Override
            public void onError(String message) {
                logger.error("Meshing Failed: " + message);
                android.app.Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        if (binding != null) {
                            binding.pbSolid.setVisibility(View.GONE);
                            binding.btnRunSolidAnalysis.setEnabled(true);
                        }
                    });
                }
            }
        });
    }

    /** Initializes SceneView only when the user opens the viewer or a result exists. */
    private void showModelInViewer() {
        if (!isAdded() || binding == null || !(getActivity() instanceof MainActivity)) return;
        try {
            SceneViewBridgeKt.setSceneViewContent(
                    binding.solidSceneViewContainer,
                    modelPath,
                    (MainActivity) getActivity());
        } catch (Throwable error) {
            logger.error("SceneView Error: " + error.getMessage());
        }
    }

    @Override
    public void onDestroyView() {
        engineReady = false;
        if (binding != null) {
            binding.solidSceneViewContainer.disposeComposition();
        }
        super.onDestroyView();
        if (gmshRunner != null) gmshRunner.shutdown();
        executor.shutdownNow();
        binding = null;
    }
}
