package com.diamon.civil.solids.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.diamon.civil.R;
import com.diamon.civil.databinding.FragmentSolidBinding;
import com.diamon.civil.structural.engine.CalculixExecutor;
import com.diamon.civil.solids.engine.GmshRunner;
import com.diamon.civil.solids.engine.OcctPrimitivesJNI;
import com.diamon.civil.solids.engine.OcctBooleanJNI;
import com.diamon.civil.structural.engine.NativeFeaCore;
import com.diamon.civil.solids.engine.SampleSimulationCase;
import com.diamon.civil.core.ui.MainActivity;
import com.diamon.civil.solids.ui.SceneViewBridgeKt;
import com.diamon.civil.core.util.logging.ModuleLogger;
import com.google.android.material.tabs.TabLayout;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import com.diamon.civil.structural.engine.MaterialDatabase;
import java.util.ArrayList;
import java.util.List;

public class SolidFragment extends Fragment {

    private FragmentSolidBinding binding;
    private final ModuleLogger logger = new ModuleLogger("Solid");
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private GmshRunner gmshRunner;
    private CalculixExecutor calculixExecutor;
    private MaterialDatabase materialDatabase;
    private volatile boolean engineReady;
    private volatile File activeSimulationGeometry;
    private volatile String modelPath = "models/test_beam.glb";
    private File workDir;
    private androidx.activity.result.ActivityResultLauncher<android.content.Intent> importCadLauncher;
    private volatile double currentDynamicLoadValue = -100.0;
    private volatile String selectedFixedId = "Fixed";
    private volatile String selectedLoadId = "Loaded";
    private final List<File> availableGeometries = new ArrayList<>();
    private boolean isProgrammaticGeometrySelection = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        importCadLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    android.net.Uri uri = result.getData().getData();
                    if (uri != null) {
                        executor.execute(() -> {
                            try {
                                String rawFileName = getFileNameFromUri(uri);
                                if (rawFileName == null || rawFileName.trim().isEmpty()) {
                                    rawFileName = "imported_cad.step";
                                }
                                final String fileName = rawFileName;

                                if (!isSupportedCadFormat(fileName)) {
                                    String warnMsg = "Incompatible CAD format: '" + fileName + "'. Supported formats: STEP (*.step, *.stp), IGES (*.iges, *.igs), BREP (*.brep), GEO (*.geo).";
                                    logger.warn(warnMsg);
                                    android.app.Activity activity = getActivity();
                                    if (activity != null) {
                                        activity.runOnUiThread(() -> {
                                            if (getContext() != null) {
                                                Toast.makeText(getContext(), getString(R.string.toast_unsupported_cad_format, fileName), Toast.LENGTH_LONG).show();
                                            }
                                        });
                                    }
                                    return;
                                }

                                java.io.InputStream in = requireContext().getContentResolver().openInputStream(uri);
                                File targetDir = new File(requireContext().getFilesDir(), "3d_solid_analysis");
                                if (!targetDir.exists()) targetDir.mkdirs();
                                File out = new File(targetDir, fileName);
                                java.io.FileOutputStream fout = new java.io.FileOutputStream(out);
                                byte[] buf = new byte[1024];
                                int len;
                                while((len = in.read(buf)) > 0) { fout.write(buf, 0, len); }
                                in.close(); fout.close();
                                activeSimulationGeometry = out;
                                logger.info("Imported CAD model: " + fileName + " (saved to 3d_solid_analysis)");
                                android.app.Activity activity = getActivity();
                                if (activity != null) {
                                    activity.runOnUiThread(() -> {
                                        if (getContext() != null) {
                                            Toast.makeText(getContext(), getString(R.string.toast_cad_imported, fileName), Toast.LENGTH_SHORT).show();
                                            refreshGeometrySpinner(activeSimulationGeometry);
                                        }
                                    });
                                }
                            } catch (Exception ex) {
                                logger.error("Import failed: " + ex.getMessage());
                            }
                        });
                    }
                }
            }
        );
    }

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
        workDir = new File(filesDir, "3d_solid_analysis");
        if (!workDir.exists()) {
            workDir.mkdirs();
        }

        logger.attachToTextView(binding.tvSolidLog);
        setupTabs();
        setupButtons();
        
        binding.btnRunSolidAnalysis.setEnabled(false);

        // Setup Spinners with compact layouts
        try {
            ArrayAdapter<CharSequence> elemAdapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.element_types_solid, R.layout.item_spinner_compact);
            elemAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown_compact);
            binding.spinnerElementType.setAdapter(elemAdapter);

            ArrayAdapter<CharSequence> fixedAdapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.solid_fixed_regions, R.layout.item_spinner_compact);
            fixedAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown_compact);
            binding.spinnerFixedRegion.setAdapter(fixedAdapter);

            ArrayAdapter<CharSequence> loadRegionAdapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.solid_load_regions, R.layout.item_spinner_compact);
            loadRegionAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown_compact);
            binding.spinnerLoadRegion.setAdapter(loadRegionAdapter);

            ArrayAdapter<CharSequence> loadDirAdapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.solid_load_directions, R.layout.item_spinner_compact);
            loadDirAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown_compact);
            binding.spinnerLoadDirection.setAdapter(loadDirAdapter);
        } catch (Exception ex) {
            logger.error("Failed to setup static spinners: " + ex.getMessage());
        }

        // Load Materials (once)
        setupMaterialSpinner();
        setupMeshDensitySlider();
        setupGeometrySpinner();
        loadDefaultTestCase();

        executor.execute(() -> {
            try {
                // Load only the JNI dependencies; Gmsh and ccx remain child processes.
                NativeFeaCore.loadLibraries();
                gmshRunner = new GmshRunner(workDir, nativeLibDir);
                calculixExecutor = new CalculixExecutor(appContext, workDir);
                if (activeSimulationGeometry == null || !activeSimulationGeometry.exists()) {
                    activeSimulationGeometry = SampleSimulationCase.createCantileverGeo(workDir);
                }
                
                android.app.Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        if (isAdded() && binding != null) {
                            engineReady = true;
                            binding.btnRunSolidAnalysis.setEnabled(true);
                            logger.info("Native engines initialized successfully");
                            refreshGeometrySpinner(activeSimulationGeometry);
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
                            logger.error("Engine initialization failed. FEA solver will not be available.");
                            refreshGeometrySpinner(activeSimulationGeometry);
                        }
                    });
                }
            }
        });
    }

    /** Sets up the material spinner from the JSON database. Called once in onViewCreated. */
    private void setupMaterialSpinner() {
        try {
            materialDatabase = new MaterialDatabase();
            if (getContext() != null) {
                try {
                    materialDatabase.loadFromAssets(requireContext());
                } catch (Exception e) {
                    logger.warn("Using default materials: " + e.getMessage());
                }
            }
            List<String> matNames = new ArrayList<>();
            for (MaterialDatabase.Material m : materialDatabase.getMaterials()) {
                matNames.add(m.name);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_compact, matNames);
            adapter.setDropDownViewResource(R.layout.item_spinner_dropdown_compact);
            binding.spinnerMaterialSolid.setAdapter(adapter);
            if (!matNames.isEmpty()) {
                binding.spinnerMaterialSolid.setSelection(0);
                binding.etSolidModulus.setText(formatModulus(materialDatabase.getMaterials().get(0).E));
            }
            
            binding.spinnerMaterialSolid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (materialDatabase != null && position >= 0 && position < materialDatabase.getMaterials().size()) {
                        MaterialDatabase.Material selected = materialDatabase.getMaterials().get(position);
                        binding.etSolidModulus.setText(formatModulus(selected.E));
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        } catch (Exception ex) {
            logger.error("Failed to load materials: " + ex.getMessage());
        }
    }

    private String formatModulus(double value) {
        if (value == (long) value) {
            return String.format(java.util.Locale.US, "%d", (long) value);
        } else {
            return String.format(java.util.Locale.US, "%s", value);
        }
    }

    private void setupMeshDensitySlider() {
        if (binding == null) return;
        updateMeshDensityLabel(binding.seekbarMeshDensity.getProgress());
        binding.seekbarMeshDensity.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                updateMeshDensityLabel(progress);
            }
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
    }

    private void updateMeshDensityLabel(int progress) {
        if (binding == null || !isAdded()) return;
        int level = progress + 1;
        String desc;
        switch (level) {
            case 1:
                desc = getString(R.string.density_coarse);
                break;
            case 2:
                desc = getString(R.string.density_medium_coarse);
                break;
            case 3:
                desc = getString(R.string.density_medium);
                break;
            case 4:
                desc = getString(R.string.density_fine);
                break;
            case 5:
                desc = getString(R.string.density_ultra_fine);
                break;
            default:
                desc = "";
                break;
        }
        binding.tvMeshDensityLabel.setText(getString(R.string.mesh_density_format, level, desc));
    }

    private String getFileNameFromUri(android.net.Uri uri) {
        String result = null;
        if (getContext() != null && "content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignore) {}
        }
        if (result == null && uri.getPath() != null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result;
    }

    private void loadDefaultTestCase() {
        if (binding == null) return;
        binding.seekbarMeshDensity.setProgress(2); // Level 3 / 5 (Medium - ~20mm)
        binding.spinnerElementType.setSelection(0); // 1st-Order C3D4 default
        if (materialDatabase != null && !materialDatabase.getMaterials().isEmpty()) {
            binding.spinnerMaterialSolid.setSelection(0);
            MaterialDatabase.Material defaultMat = materialDatabase.getMaterials().get(0);
            binding.etSolidModulus.setText(formatModulus(defaultMat.E));
        } else {
            binding.etSolidModulus.setText("200000");
        }
        binding.spinnerFixedRegion.setSelection(0); // Auto / Fixed
        binding.spinnerLoadRegion.setSelection(0); // Auto / Loaded
        binding.spinnerLoadDirection.setSelection(0); // Vertical Y (DOF 2)
        binding.etLoadMagnitude.setText("-100.0");
        updateMeshDensityLabel(binding.seekbarMeshDensity.getProgress());
        if (activeSimulationGeometry == null || !activeSimulationGeometry.exists()) {
            logger.info("Test case ready: 3D Steel Cantilever Beam, fixed support and vertical load.");
            activeSimulationGeometry = new File(workDir, "cantilever.geo");
        } else {
            logger.info("Active model ready: " + activeSimulationGeometry.getName());
        }
        refreshGeometrySpinner(activeSimulationGeometry);
    }

    public static boolean isFullyAssembledInp(File inpFile) {
        if (inpFile == null || !inpFile.exists()) return false;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(inpFile))) {
            String line;
            boolean hasStep = false;
            boolean hasMaterialOrSection = false;
            while ((line = reader.readLine()) != null) {
                String u = line.trim().toUpperCase(java.util.Locale.US);
                if (u.startsWith("*STEP")) hasStep = true;
                if (u.startsWith("*SOLID SECTION") || u.startsWith("*SHELL SECTION") || u.startsWith("*MATERIAL")) {
                    hasMaterialOrSection = true;
                }
                if (hasStep && hasMaterialOrSection) return true;
            }
        } catch (Exception ignore) {}
        return false;
    }

    private void setupGeometrySpinner() {
        if (binding == null) return;
        binding.spinnerActiveGeometry.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isProgrammaticGeometrySelection) return;
                if (position >= 0 && position < availableGeometries.size()) {
                    File chosen = availableGeometries.get(position);
                    activeSimulationGeometry = chosen;
                    logger.info("Active CAD Model switched to: " + chosen.getName());
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    public static boolean isSupportedCadFormat(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return false;
        String name = fileName.toLowerCase(java.util.Locale.US).trim();
        if (name.equals("gmsh_cad_driver.geo") || name.startsWith("job_") || name.startsWith(".")) return false;
        return name.endsWith(".step") || name.endsWith(".stp")
                || name.endsWith(".brep")
                || name.endsWith(".iges") || name.endsWith(".igs")
                || name.endsWith(".geo");
    }

    public static boolean isSupportedFormat(String fileName) {
        return isSupportedCadFormat(fileName);
    }

    private void refreshGeometrySpinner(File selectFile) {
        if (binding == null || getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (!isAdded() || binding == null || getContext() == null) return;
            availableGeometries.clear();
            List<String> displayNames = new ArrayList<>();

            File benchFile = new File(workDir, "cantilever.geo");
            if (!benchFile.exists()) {
                benchFile = new File(workDir, "cantilever_benchmark.geo");
            }
            if (benchFile.exists()) {
                availableGeometries.add(benchFile);
                displayNames.add(getString(R.string.geo_item_benchmark));
            }

            if (workDir != null && workDir.exists()) {
                File[] files = workDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String name = f.getName().toLowerCase(java.util.Locale.US);
                        if (name.equals("cantilever.geo") || name.equals("cantilever_benchmark.geo") || name.equals("gmsh_cad_driver.geo")) continue;
                        if (isSupportedCadFormat(name)) {
                            availableGeometries.add(f);
                            if (name.equals("box.brep")) displayNames.add(getString(R.string.geo_item_box));
                            else if (name.equals("cylinder.brep")) displayNames.add(getString(R.string.geo_item_cylinder));
                            else if (name.equals("sphere.brep")) displayNames.add(getString(R.string.geo_item_sphere));
                            else if (name.equals("operated_fillet.brep")) displayNames.add(getString(R.string.geo_item_fillet));
                            else if (name.equals("operated_chamfer.brep")) displayNames.add(getString(R.string.geo_item_chamfer));
                            else if (name.equals("operated_extrude.brep")) displayNames.add(getString(R.string.geo_item_extrude));
                            else if (name.equals("union_result.brep")) displayNames.add(getString(R.string.geo_item_union));
                            else if (name.equals("cut_result.brep")) displayNames.add(getString(R.string.geo_item_cut));
                            else if (name.equals("intersect_result.brep")) displayNames.add(getString(R.string.geo_item_intersect));
                            else displayNames.add("📥 " + f.getName());
                        }
                    }
                }
            }

            if (selectFile != null && isSupportedCadFormat(selectFile.getName()) && !availableGeometries.contains(selectFile)) {
                availableGeometries.add(selectFile);
                displayNames.add("📥 " + selectFile.getName());
            }

            isProgrammaticGeometrySelection = true;
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_compact, displayNames);
            adapter.setDropDownViewResource(R.layout.item_spinner_dropdown_compact);
            binding.spinnerActiveGeometry.setAdapter(adapter);

            int selectedIndex = 0;
            if (selectFile != null && isSupportedCadFormat(selectFile.getName())) {
                for (int i = 0; i < availableGeometries.size(); i++) {
                    if (availableGeometries.get(i).getAbsolutePath().equals(selectFile.getAbsolutePath())) {
                        selectedIndex = i;
                        break;
                    }
                }
            }
            binding.spinnerActiveGeometry.setSelection(selectedIndex);
            isProgrammaticGeometrySelection = false;
        });
    }

    private void setupTabs() {
        binding.tabLayoutSolid.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (binding == null) return;
                binding.layoutSolidParams.setVisibility(tab.getPosition() == 0 ? View.VISIBLE : View.GONE);
                binding.layoutViewer3D.setVisibility(tab.getPosition() == 1 ? View.VISIBLE : View.GONE);
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
        binding.btnDeleteGeometry.setOnClickListener(v -> showDeleteGeometryDialog());
        binding.btnCreateBox.setOnClickListener(v -> createPrimitive("box"));
        binding.btnCreateCylinder.setOnClickListener(v -> createPrimitive("cylinder"));
        binding.btnCreateSphere.setOnClickListener(v -> createPrimitive("sphere"));
        
        binding.btnImportCAD.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            importCadLauncher.launch(intent);
        });

        binding.btnFillet.setOnClickListener(v -> applyOperation("fillet"));
        binding.btnChamfer.setOnClickListener(v -> applyOperation("chamfer"));
        binding.btnExtrude.setOnClickListener(v -> applyOperation("extrude"));
        binding.btnUnion.setOnClickListener(v -> showBooleanDialog("union"));
        binding.btnCut.setOnClickListener(v -> showBooleanDialog("cut"));
        binding.btnIntersect.setOnClickListener(v -> showBooleanDialog("intersect"));
        
        binding.btnRunSolidAnalysis.setOnClickListener(v -> runFullPipeline());
        
        binding.btnResetSolidCamera.setOnClickListener(v -> {
            SceneViewBridgeKt.resetSceneViewCamera();
            Toast.makeText(getContext(), R.string.toast_view_centered, Toast.LENGTH_SHORT).show();
        });
        
        binding.btnReloadSolidModel.setOnClickListener(v -> {
            showModelInViewer();
            Toast.makeText(getContext(), R.string.toast_model_reloaded, Toast.LENGTH_SHORT).show();
        });

        binding.fabClearSolidLog.setOnClickListener(v -> logger.clear());
        
        // Make the entire scrollview or log text copyable on tap
        binding.scrollSolidLog.setOnClickListener(v -> copyToClipboard(logger.getFullLog()));
        binding.tvSolidLog.setOnClickListener(v -> copyToClipboard(logger.getFullLog()));
    }

    private void showDeleteGeometryDialog() {
        if (getContext() == null || binding == null) return;
        int pos = binding.spinnerActiveGeometry.getSelectedItemPosition();
        if (pos < 0 || pos >= availableGeometries.size()) {
            Toast.makeText(getContext(), R.string.toast_no_model_to_delete, Toast.LENGTH_SHORT).show();
            return;
        }

        final File toDelete = availableGeometries.get(pos);
        final String displayName = toDelete.getName();

        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle(R.string.dialog_delete_model_title)
                .setMessage(getString(R.string.dialog_delete_model_msg, displayName))
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    try {
                        if (toDelete.exists()) {
                            toDelete.delete();
                        }
                        logger.info("Deleted model: " + displayName);
                        Toast.makeText(getContext(), getString(R.string.toast_model_deleted, displayName), Toast.LENGTH_SHORT).show();

                        File nextActive = null;
                        if (availableGeometries.size() > 1) {
                            int nextIdx = (pos > 0) ? (pos - 1) : 1;
                            nextActive = availableGeometries.get(nextIdx);
                        }
                        activeSimulationGeometry = nextActive;
                        refreshGeometrySpinner(activeSimulationGeometry);
                    } catch (Exception ex) {
                        logger.error("Failed to delete model: " + ex.getMessage());
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void copyToClipboard(String text) {
        if (getContext() == null) return;
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("FEA Log", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), R.string.toast_copied_to_clipboard, Toast.LENGTH_SHORT).show();
        }
    }

    public void exportResults() {
        if (getContext() == null) return;
        final android.content.Context ctx = getContext();
        final File workDir = new File(ctx.getFilesDir(), "3d_solid_analysis");
        if (!workDir.exists()) workDir.mkdirs();
        final File reportFile = new File(workDir, "Solid_Analysis_Report.pdf");

        executor.execute(() -> {
            boolean exported = false;
            try {
                com.diamon.civil.solids.export.SolidPDFReportGenerator generator = new com.diamon.civil.solids.export.SolidPDFReportGenerator();
                boolean generated = generator.generateReport(ctx, reportFile, "3D Solid Analysis", workDir);
                
                if (generated && reportFile.exists()) {
                    com.diamon.civil.core.export.ExportManager manager = new com.diamon.civil.core.export.ExportManager(ctx);
                    exported = manager.exportToDownloads(reportFile, "3d_solid_analysis");
                }
            } catch (Throwable e) {
                logger.error("Export Error: " + e.getMessage());
            }

            final boolean success = exported;
            android.app.Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (success) {
                        Toast.makeText(ctx, R.string.toast_pdf_exported, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(ctx, R.string.toast_pdf_export_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private String resolveCanonicalElementType(String displayString, int position) {
        if (displayString != null) {
            String u = displayString.toUpperCase(java.util.Locale.US);
            if (u.contains("C3D8R")) return "C3D8R";
            if (u.contains("C3D8")) return "C3D8";
            if (u.contains("C3D20R")) return "C3D20R";
            if (u.contains("C3D20")) return "C3D20";
            if (u.contains("C3D10")) return "C3D10";
            if (u.contains("C3D4")) return "C3D4";
            if (u.contains("C3D15")) return "C3D15";
            if (u.contains("C3D6")) return "C3D6";
        }
        switch (position) {
            case 0: return "C3D4";
            case 1: return "C3D8";
            case 2: return "C3D8R";
            case 3: return "C3D6";
            case 4: return "C3D10";
            case 5: return "C3D20";
            case 6: return "C3D20R";
            case 7: return "C3D15";
            default: return "C3D10";
        }
    }

    public void onInpImported(File inpFile) {
        if (inpFile == null || !inpFile.exists()) return;
        final android.content.Context ctx = getContext();
        if (ctx == null) return;
        final android.content.Context appContext = ctx.getApplicationContext();

        logger.info("Imported INP Deck: " + inpFile.getName());

        final int matPos = (binding != null) ? binding.spinnerMaterialSolid.getSelectedItemPosition() : 0;
        final String fixedRegion = (binding != null && binding.spinnerFixedRegion.getSelectedItem() != null)
                ? binding.spinnerFixedRegion.getSelectedItem().toString() : "AUTO";
        final String loadRegion = (binding != null && binding.spinnerLoadRegion.getSelectedItem() != null)
                ? binding.spinnerLoadRegion.getSelectedItem().toString() : "AUTO";
        final String elemType = (binding != null && binding.spinnerElementType.getSelectedItem() != null)
                ? resolveCanonicalElementType(binding.spinnerElementType.getSelectedItem().toString(), binding.spinnerElementType.getSelectedItemPosition()) : "C3D10";
        final int loadDirPos = (binding != null) ? binding.spinnerLoadDirection.getSelectedItemPosition() : 0;
        final int loadDof = (loadDirPos == 1) ? 1 : (loadDirPos == 2) ? 3 : 2;

        final String loadMagStr = (binding != null) ? binding.etLoadMagnitude.getText().toString().trim() : "";
        double parsedLoad = -100.0;
        try {
            if (!loadMagStr.isEmpty()) parsedLoad = Double.parseDouble(loadMagStr);
        } catch (NumberFormatException ignored) {}
        final double finalLoadMagnitude = parsedLoad;

        final String modulusStr = (binding != null) ? binding.etSolidModulus.getText().toString().trim() : "";
        String materialName = "Structural Steel A36";
        double nu = 0.3;
        double youngModulusTemp = 200000.0;
        if (materialDatabase != null && matPos >= 0 && matPos < materialDatabase.getMaterials().size()) {
            MaterialDatabase.Material mat = materialDatabase.getMaterials().get(matPos);
            materialName = mat.name;
            nu = mat.nu;
            youngModulusTemp = mat.E;
        }
        try {
            if (!modulusStr.isEmpty()) youngModulusTemp = Double.parseDouble(modulusStr);
        } catch (NumberFormatException ignored) {}
        final double finalE = youngModulusTemp;
        final String finalMaterialName = materialName;
        final double finalNu = nu;

        android.app.Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (binding != null) {
                    binding.pbSolid.setVisibility(View.VISIBLE);
                    binding.btnRunSolidAnalysis.setEnabled(false);
                }
            });
        }

        executor.execute(() -> {
            try {
                if (calculixExecutor == null) {
                    calculixExecutor = new CalculixExecutor(appContext, workDir);
                }

                cleanSimulationWorkspace(workDir, modelPath);

                if (isFullyAssembledInp(inpFile)) {
                    logger.info("Detected Pre-assembled CalculiX Deck (" + inpFile.getName() + "). Running CalculiX Solver directly...");
                    File targetJobInp = new File(workDir, "job_solid.inp");
                    com.diamon.civil.core.io.FileHelper.copyFile(inpFile, targetJobInp);
                } else {
                    logger.info("Detected Raw Solid Mesh INP (" + inpFile.getName() + "). Assembling with active analysis parameters...");
                    File rawInp = new File(workDir, "job_solid_raw.inp");
                    com.diamon.civil.core.io.FileHelper.copyFile(inpFile, rawInp);

                    com.diamon.civil.solids.engine.SolidInpAssembler.assemble(workDir, "job_solid", finalMaterialName, finalE, finalNu, finalLoadMagnitude, loadDof, fixedRegion, loadRegion, elemType);
                }

                logger.info("Step: Running CalculiX Solver ccx...");
                String ccxResult = calculixExecutor.executeCalculix("job_solid");
                logger.log(ccxResult);
                if (!CalculixExecutor.wasSuccessful(ccxResult)) {
                    throw new IllegalStateException("CalculiX solver reported errors; check the Solver Log tab");
                }

                File frdFile = new File(workDir, "job_solid.frd");
                if (frdFile.exists() && frdFile.length() > 0) {
                    logger.info("Step: Converting FRD results to 3D solid model...");
                    String newGlbName = "job_solid_" + System.currentTimeMillis() + ".glb";
                    File newGlbFile = new File(workDir, newGlbName);
                    float deformScale = 1.0f;
                    boolean isSphere = inpFile.getName().toLowerCase(java.util.Locale.US).contains("sphere");
                    if (calculixExecutor.convertFrdToGlb(frdFile.getAbsolutePath(), newGlbFile.getAbsolutePath(), deformScale, isSphere)) {
                        if (newGlbFile.exists() && newGlbFile.length() > 0) {
                            logger.info("Step: 3D Visualization Model ready! Open the 3D Viewer tab to inspect results.");
                            String oldGlbPath = modelPath;
                            modelPath = newGlbFile.getAbsolutePath();

                            if (activity != null) {
                                activity.runOnUiThread(() -> {
                                    if (binding != null && isAdded()) {
                                        showModelInViewer(true);
                                        binding.pbSolid.setVisibility(View.GONE);
                                        binding.btnRunSolidAnalysis.setEnabled(true);
                                        Toast.makeText(appContext, R.string.toast_simulation_complete, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                            if (oldGlbPath != null && !oldGlbPath.equals(newGlbFile.getAbsolutePath())) {
                                File oldGlb = new File(oldGlbPath);
                                if (oldGlb.exists() && oldGlb.getName().startsWith("job_solid_")) {
                                    oldGlb.delete();
                                }
                            }
                        } else {
                            logger.error("FRD to GLB conversion output file is empty");
                        }
                    } else {
                        logger.error("FRD to GLB conversion failed for imported INP");
                    }
                } else {
                    logger.error("No FRD result file generated by CalculiX for imported INP");
                }
                cleanIntermediateSimulationFiles(workDir);

            } catch (Throwable e) {
                cleanIntermediateSimulationFiles(workDir);
                logger.error("INP Execution Error: " + e.getMessage());
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        if (binding != null && isAdded()) {
                            binding.pbSolid.setVisibility(View.GONE);
                            binding.btnRunSolidAnalysis.setEnabled(true);
                            Toast.makeText(appContext, getString(R.string.toast_inp_error, e.getMessage()), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    public void loadGeometryFile(File file) {
        if (file == null || !file.exists()) {
            logger.error("Geometry file not found or invalid.");
            return;
        }
        if (!isSupportedCadFormat(file.getName())) {
            String warnMsg = "Incompatible CAD format: '" + file.getName() + "'. Supported formats: STEP (*.step, *.stp), IGES (*.iges, *.igs), BREP (*.brep), GEO (*.geo).";
            logger.warn(warnMsg);
            if (getContext() != null) {
                Toast.makeText(getContext(), getString(R.string.toast_unsupported_cad_format, file.getName()), Toast.LENGTH_LONG).show();
            }
            return;
        }
        activeSimulationGeometry = file;
        logger.info("Loaded geometry model: " + file.getName());
        android.app.Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(), getString(R.string.toast_cad_imported, file.getName()), Toast.LENGTH_SHORT).show();
                    refreshGeometrySpinner(activeSimulationGeometry);
                }
            });
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
                        if (isAdded()) {
                            Toast.makeText(getContext(), getString(R.string.toast_primitive_created, type.toUpperCase(java.util.Locale.US)), Toast.LENGTH_SHORT).show();
                            refreshGeometrySpinner(activeSimulationGeometry);
                        }
                    });
                }
            } else {
                logger.error("Failed to create " + type);
                android.app.Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        if (isAdded()) {
                            Toast.makeText(getContext(), getString(R.string.toast_primitive_failed, type.toUpperCase(java.util.Locale.US)), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private void applyOperation(String op) {
        if (activeSimulationGeometry == null) {
            Toast.makeText(getContext(), R.string.toast_no_active_geometry, Toast.LENGTH_SHORT).show();
            return;
        }

        final String geoName = activeSimulationGeometry.getName();
        final String nameLower = geoName.toLowerCase(java.util.Locale.US);

        if (nameLower.contains("sphere") && (op.equals("fillet") || op.equals("chamfer"))) {
            String msg = "Not applicable: Sphere is a continuous smooth surface with no sharp dihedral edges to " + op + ".";
            logger.warn(msg);
            if (getContext() != null) {
                Toast.makeText(getContext(), getString(R.string.toast_sphere_no_edges, op), Toast.LENGTH_LONG).show();
            }
            return;
        }

        executor.execute(() -> {
            boolean success = false;
            String inPath = activeSimulationGeometry.getAbsolutePath();
            String outPath = new File(workDir, "operated_" + op + ".brep").getAbsolutePath();
            try {
                if (op.equals("fillet")) success = OcctPrimitivesJNI.applyFillet(inPath, outPath, 1.0);
                else if (op.equals("chamfer")) success = OcctPrimitivesJNI.applyChamfer(inPath, outPath, 1.0);
                else if (op.equals("extrude")) success = OcctPrimitivesJNI.applyExtrude(inPath, outPath, 0, 0, 10.0);
            } catch (Throwable e) {
                logger.error("Operation failed: " + e.getMessage());
            }

            if (success) {
                activeSimulationGeometry = new File(outPath);
                logger.info("Applied operation: " + op + " -> " + activeSimulationGeometry.getName());
                android.app.Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        if (isAdded()) {
                            Toast.makeText(getContext(), getString(R.string.toast_operation_applied, op.toUpperCase(java.util.Locale.US)), Toast.LENGTH_SHORT).show();
                            refreshGeometrySpinner(activeSimulationGeometry);
                        }
                    });
                }
            } else {
                String warnLog = "Operation " + op.toUpperCase(java.util.Locale.US) + " not applicable to '" + geoName + "': Geometry has no suitable sharp dihedral edges or radius exceeds feature thickness.";
                logger.warn(warnLog);
                android.app.Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        if (isAdded()) {
                            Toast.makeText(getContext(), getString(R.string.toast_operation_failed_detail, op.toUpperCase(java.util.Locale.US)), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    private void showBooleanDialog(final String op) {
        if (getContext() == null) return;
        final File[] files = workDir.listFiles((dir, name) -> name.endsWith(".brep") || name.endsWith(".step") || name.endsWith(".stp"));
        if (files == null || files.length < 2) {
            String msg = "Not applicable: Please create or import at least 2 CAD files (.brep / .step) first for Boolean operations.";
            logger.warn(msg);
            Toast.makeText(getContext(), R.string.toast_boolean_min_files, Toast.LENGTH_LONG).show();
            return;
        }

        final String[] fileNames = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            fileNames[i] = files[i].getName();
        }

        android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        android.widget.TextView tvA = new android.widget.TextView(getContext());
        tvA.setText(R.string.dialog_boolean_solid_a);
        tvA.setPadding(0, 10, 0, 10);
        layout.addView(tvA);

        final android.widget.Spinner spinnerA = new android.widget.Spinner(getContext());
        spinnerA.setBackgroundResource(R.drawable.spinner_compact_bg);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(getContext(), R.layout.item_spinner_compact, fileNames);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown_compact);
        spinnerA.setAdapter(adapter);
        layout.addView(spinnerA);

        android.widget.TextView tvB = new android.widget.TextView(getContext());
        tvB.setText(R.string.dialog_boolean_solid_b);
        tvB.setPadding(0, 20, 0, 10);
        layout.addView(tvB);

        final android.widget.Spinner spinnerB = new android.widget.Spinner(getContext());
        spinnerB.setBackgroundResource(R.drawable.spinner_compact_bg);
        spinnerB.setAdapter(adapter);
        if (fileNames.length > 1) {
            spinnerB.setSelection(1);
        }
        layout.addView(spinnerB);

        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.dialog_boolean_title, op.toUpperCase(java.util.Locale.US)))
                .setView(layout)
                .setPositiveButton(R.string.apply, (dialog, which) -> {
                    int idxA = spinnerA.getSelectedItemPosition();
                    int idxB = spinnerB.getSelectedItemPosition();
                    if (idxA == idxB) {
                        Toast.makeText(getContext(), R.string.toast_boolean_distinct, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    final File fileA = files[idxA];
                    final File fileB = files[idxB];
                    final String outPath = new File(workDir, op + "_result.brep").getAbsolutePath();
                    
                    logger.info("Running Boolean " + op.toUpperCase(java.util.Locale.US) + " between " + fileA.getName() + " and " + fileB.getName() + "...");
                    
                    executor.execute(() -> {
                        boolean success = false;
                        try {
                            if (op.equals("union")) {
                                success = OcctBooleanJNI.fuse(fileA.getAbsolutePath(), fileB.getAbsolutePath(), outPath);
                            } else if (op.equals("cut")) {
                                success = OcctBooleanJNI.cut(fileA.getAbsolutePath(), fileB.getAbsolutePath(), outPath);
                            } else if (op.equals("intersect")) {
                                success = OcctBooleanJNI.intersect(fileA.getAbsolutePath(), fileB.getAbsolutePath(), outPath);
                            }
                        } catch (Throwable e) {
                            logger.error("Boolean operation error: " + e.getMessage());
                        }

                        final boolean finalSuccess = success;
                        android.app.Activity activity = getActivity();
                        if (activity != null) {
                            activity.runOnUiThread(() -> {
                                if (isAdded()) {
                                    if (finalSuccess) {
                                        activeSimulationGeometry = new File(outPath);
                                        logger.info("Boolean " + op.toUpperCase(java.util.Locale.US) + " Success! Saved as " + op + "_result.brep");
                                        Toast.makeText(getContext(), getString(R.string.toast_operation_applied, "Boolean " + op.toUpperCase(java.util.Locale.US)), Toast.LENGTH_SHORT).show();
                                        refreshGeometrySpinner(activeSimulationGeometry);
                                    } else {
                                        String boolWarn = "Boolean " + op.toUpperCase(java.util.Locale.US) + " not applicable: Shapes do not intersect in 3D space or have non-manifold boundaries.";
                                        logger.warn(boolWarn);
                                        Toast.makeText(getContext(), getString(R.string.toast_boolean_non_manifold, op.toUpperCase(java.util.Locale.US)), Toast.LENGTH_LONG).show();
                                    }
                                }
                            });
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void runFullPipeline() {
        if (binding == null || getContext() == null) return;

        if (!engineReady || gmshRunner == null || calculixExecutor == null) {
            Toast.makeText(getContext(), R.string.toast_engine_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }

        if (binding.spinnerElementType.getSelectedItem() == null) {
            Toast.makeText(getContext(), R.string.toast_select_element_type, Toast.LENGTH_SHORT).show();
            return;
        }

        binding.pbSolid.setVisibility(View.VISIBLE);
        binding.btnRunSolidAnalysis.setEnabled(false);

        // Capture ALL UI values on main thread to avoid crashes
        final int density = binding.seekbarMeshDensity.getProgress() + 1;
        final String rawElemStr = binding.spinnerElementType.getSelectedItem().toString();
        final String elemType = resolveCanonicalElementType(rawElemStr, binding.spinnerElementType.getSelectedItemPosition());
        final String modulusStr = binding.etSolidModulus.getText().toString().trim();
        final String fixedRegion = binding.spinnerFixedRegion.getSelectedItem() != null ? binding.spinnerFixedRegion.getSelectedItem().toString() : "AUTO";
        final String loadRegion = binding.spinnerLoadRegion.getSelectedItem() != null ? binding.spinnerLoadRegion.getSelectedItem().toString() : "AUTO";
        final int loadDirPos = binding.spinnerLoadDirection.getSelectedItemPosition();
        final int loadDof = (loadDirPos == 1) ? 1 : (loadDirPos == 2) ? 3 : 2; // 0 -> 2 (Y), 1 -> 1 (X), 2 -> 3 (Z)

        final String loadMagStr = binding.etLoadMagnitude.getText().toString().trim();
        double parsedLoad = -100.0;
        try {
            if (!loadMagStr.isEmpty()) parsedLoad = Double.parseDouble(loadMagStr);
        } catch (NumberFormatException e) {
            logger.error("Invalid load magnitude format, defaulting to -100.0 N");
        }
        final double finalLoadMagnitude = parsedLoad;
        
        String materialName = "Structural Steel A36";
        double nu = 0.3;
        int matPos = binding.spinnerMaterialSolid.getSelectedItemPosition();
        if (materialDatabase != null && matPos >= 0 && matPos < materialDatabase.getMaterials().size()) {
            MaterialDatabase.Material mat = materialDatabase.getMaterials().get(matPos);
            materialName = mat.name;
            nu = mat.nu;
        }

        double youngModulusTemp = (materialDatabase != null && matPos >= 0 && matPos < materialDatabase.getMaterials().size())
                ? materialDatabase.getMaterials().get(matPos).E : 200000.0;
        try {
            if (!modulusStr.isEmpty()) {
                double val = Double.parseDouble(modulusStr);
                if (val > 0.0) {
                    youngModulusTemp = val;
                } else {
                    logger.warn("Young's modulus must be > 0. Using default " + youngModulusTemp + " MPa");
                }
            }
        } catch (NumberFormatException e) {
            logger.error("Invalid modulus format, using default " + youngModulusTemp + " MPa");
        }
        final double E = youngModulusTemp;
        final String finalMaterialName = materialName;
        final double finalNu = nu;
        
        logger.info("Starting Pipeline for Finite Element: " + elemType + " (" + rawElemStr + ") | Material: " + finalMaterialName + " (E=" + E + " MPa, nu=" + finalNu + ") | Fixed: " + fixedRegion + " | Load: " + loadRegion + " (" + finalLoadMagnitude + " N, DOF " + loadDof + ")");

        // Synchronize active simulation geometry with the active spinner selection to avoid desync
        int selectedGeoPos = binding.spinnerActiveGeometry.getSelectedItemPosition();
        if (selectedGeoPos >= 0 && selectedGeoPos < availableGeometries.size()) {
            activeSimulationGeometry = availableGeometries.get(selectedGeoPos);
        }

        final File workDir = new File(getContext().getFilesDir(), "3d_solid_analysis");
        if (!workDir.exists()) workDir.mkdirs();

        // Ensure clean workspace so previous simulation results never leak into subsequent runs
        cleanSimulationWorkspace(workDir, modelPath);

        final File cadFile = activeSimulationGeometry;
        final android.content.Context appContext = getContext().getApplicationContext();

        if (cadFile == null || !cadFile.exists()) {
            logger.error("Simulation Error: no test geometry available.");
            binding.pbSolid.setVisibility(View.GONE);
            binding.btnRunSolidAnalysis.setEnabled(true);
            return;
        }

        String nameLower = cadFile.getName().toLowerCase();
        if (nameLower.endsWith(".inp")) {
            logger.info("Directly executing active INP mesh/deck: " + cadFile.getName());
            onInpImported(cadFile);
            return;
        }

        if (!nameLower.endsWith(".geo") && !nameLower.endsWith(".step") && !nameLower.endsWith(".stp") && !nameLower.endsWith(".brep") && !nameLower.endsWith(".iges") && !nameLower.endsWith(".igs")) {
            logger.error("Unsupported geometry format. Please load a compatible model (.geo, .step, .brep, .iges, .inp) before running the solver.");
            binding.pbSolid.setVisibility(View.GONE);
            binding.btnRunSolidAnalysis.setEnabled(true);
            return;
        }

        logger.info("Step 1: Generating Mesh with Gmsh (" + elemType + ", Density: " + density + ")...");
        
        gmshRunner.meshAsync(cadFile, density, "job_solid", elemType, new GmshRunner.GmshCallback() {
            @Override
            public void onSuccess(File rawInp) {
                logger.info("Mesh OK: " + rawInp.getName());
                executor.execute(() -> {
                    try {
                        logger.info("Step 2: Assembling CalculiX Input INP...");
                        
                        if (!rawInp.exists()) {
                            throw new java.io.FileNotFoundException("Input mesh was not generated");
                        }
                        
                        if (calculixExecutor == null) {
                            calculixExecutor = new CalculixExecutor(appContext, workDir);
                        }

                        com.diamon.civil.solids.engine.SolidInpAssembler.assemble(workDir, "job_solid", finalMaterialName, E, finalNu, finalLoadMagnitude, loadDof, fixedRegion, loadRegion, elemType);
                        
                        logger.info("Step 3: Running CalculiX Solver ccx...");
                        String ccxResult = calculixExecutor.executeCalculix("job_solid");
                        logger.log(ccxResult);
                        if (!CalculixExecutor.wasSuccessful(ccxResult)) {
                            throw new IllegalStateException("CalculiX terminated with errors; check the Solver Log");
                        }

                        File frdFile = new File(workDir, "job_solid.frd");
                        if (frdFile.exists() && frdFile.length() > 0) {
                            logger.info("Step 4: Converting FRD results to 3D model...");

                            String newGlbName = "job_solid_" + System.currentTimeMillis() + ".glb";
                            File newGlbFile = new File(workDir, newGlbName);
                            float deformScale = 1.0f;
                            boolean isSphere = cadFile != null && cadFile.getName().toLowerCase().contains("sphere");
                            if (calculixExecutor.convertFrdToGlb(frdFile.getAbsolutePath(), newGlbFile.getAbsolutePath(), deformScale, isSphere)) {
                                if (newGlbFile.exists() && newGlbFile.length() > 0) {
                                    logger.info("Step 5: Loading 3D visualization...");
                                    String oldGlbPath = modelPath;
                                    modelPath = newGlbFile.getAbsolutePath();
                                    android.app.Activity activity = getActivity();
                                    if (activity != null) {
                                        activity.runOnUiThread(() -> {
                                            if (binding != null) {
                                                showModelInViewer(true);
                                            }
                                        });
                                    }
                                    if (oldGlbPath != null && !oldGlbPath.equals(newGlbFile.getAbsolutePath())) {
                                        File oldGlb = new File(oldGlbPath);
                                        if (oldGlb.exists() && oldGlb.getName().startsWith("job_solid_")) {
                                            oldGlb.delete();
                                        }
                                    }
                                } else {
                                    logger.error("FRD to GLB conversion output file is empty");
                                }
                            } else {
                                logger.error("FRD to GLB conversion failed");
                            }
                        } else {
                            logger.error("No FRD result file generated by CalculiX");
                        }
                        cleanIntermediateSimulationFiles(workDir);

                        android.app.Activity activity = getActivity();
                        if (activity != null) {
                            activity.runOnUiThread(() -> {
                                if (binding != null) {
                                    binding.pbSolid.setVisibility(View.GONE);
                                    binding.btnRunSolidAnalysis.setEnabled(true);
                                    Toast.makeText(appContext, R.string.toast_simulation_complete, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                    } catch (Throwable e) {
                        cleanIntermediateSimulationFiles(workDir);
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
                cleanIntermediateSimulationFiles(workDir);
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

    /**
     * Cleans all temporary simulation files in workDir (intermediate meshes, solver output, decks, logs),
     * while strictly preserving source CAD models, user-imported decks, PDF reports, and the currently
     * displayed GLB model (preserving the active GLB per user constraint).
     */
    private void cleanSimulationWorkspace(File targetDir, String currentGlbToKeep) {
        if (targetDir == null || !targetDir.exists()) return;
        File[] files = targetDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) continue;
            String name = f.getName();
            String lower = name.toLowerCase(java.util.Locale.US);

            // 1. Preserve source CAD models, primitives, operations, booleans, and user-imported INP meshes
            boolean isSourceCad = (lower.endsWith(".step") || lower.endsWith(".stp") ||
                                   (lower.endsWith(".geo") && !lower.endsWith(".geo_unrolled") && !name.equals("gmsh_cad_driver.geo")) ||
                                   lower.endsWith(".iges") || lower.endsWith(".igs") ||
                                   (lower.endsWith(".brep") && !lower.endsWith("_sewn.brep")) ||
                                   (lower.endsWith(".inp") && !lower.startsWith("job_solid") && !lower.startsWith("nsets")));
            if (isSourceCad) {
                continue;
            }

            // 2. Preserve user exported PDF reports
            if (lower.endsWith(".pdf")) {
                continue;
            }

            // 3. Keep the active GLB currently displayed in the 3D viewer (STRICT constraint: do not touch active glb)
            if (currentGlbToKeep != null && f.getAbsolutePath().equals(currentGlbToKeep)) {
                continue;
            }

            // 4. Delete all intermediate calculation artifacts, stale results, solver outputs, spooles.out, etc.
            deleteFileThoroughly(f);
        }
    }

    /**
     * Deletes transient intermediate files created during the simulation (deck assemblies, raw meshes,
     * solver logs), while leaving final result files (job_solid.dat, job_solid.frd, and active GLB)
     * intact for inspection and reporting.
     */
    private void cleanIntermediateSimulationFiles(File targetDir) {
        if (targetDir == null || !targetDir.exists()) return;
        String[] intermediateNames = {
            "job_solid_raw.inp", "job_solid_clean.inp", "nsets.inp",
            "spooles.out", "spooles.log", "intpoints.out", "slavintmortar.out",
            "temporaryrestartfile", "sew_iges.tcl", "gmsh_cad_driver.geo",
            "gmsh_cad_driver.geo_unrolled", "job_solid.cvg", "job_solid.sta",
            "job_solid.12d", "job_solid.fcv", "job_solid.cel", "job_solid.eig",
            "job_solid.rout", "job_solid.nam"
        };
        for (String inName : intermediateNames) {
            deleteFileThoroughly(new File(targetDir, inName));
        }

        // Also clean any leftover .geo_unrolled, .opt, or .gmsh-options
        File[] files = targetDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) continue;
                String n = f.getName().toLowerCase(java.util.Locale.US);
                if (n.endsWith(".geo_unrolled") || n.endsWith(".opt") || n.equals(".gmsh-options")) {
                    deleteFileThoroughly(f);
                }
            }
        }
    }

    private static void deleteFileThoroughly(File f) {
        if (f == null || !f.exists()) return;
        boolean deleted = f.delete();
        if (!deleted && f.exists()) {
            try {
                // If standard delete fails, truncate to 0 bytes so stale content cannot leak
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f, false)) {
                    fos.flush();
                }
                f.delete();
            } catch (Exception ignored) {}
        }
    }

    /** Initializes SceneView only when the user opens the viewer or a result exists. */
    private void showModelInViewer() {
        showModelInViewer(false);
    }

    /**
     * Loads the current modelPath into the 3D viewer.
     * @param forceReload if true, forces a full model reload even if the path hasn't changed
     *                    (needed after recalculation where the file content changes but path stays the same).
     */
    private void showModelInViewer(boolean forceReload) {
        if (!isAdded() || binding == null || !(getActivity() instanceof MainActivity)) return;
        try {
            SceneViewBridgeKt.setSceneViewContent(
                    binding.solidSceneViewContainer,
                    modelPath,
                    (MainActivity) getActivity(),
                    forceReload);
        } catch (Throwable error) {
            logger.error("SceneView Error: " + error.getMessage());
        }
    }

    public void onHit(Object info) {
        // Uninterrupted 3D navigation: Boundary conditions and loads are configured in the Parameters tab.
        if (info != null) {
            logger.info("3D Inspection: " + info);
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
