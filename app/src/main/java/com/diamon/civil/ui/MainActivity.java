package com.diamon.civil.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.text.InputType;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;

import com.diamon.civil.R;
import com.diamon.civil.databinding.ActivityMainBinding;
import com.diamon.civil.engine.CalculixExecutor;
import com.diamon.civil.engine.DatParser;
import com.diamon.civil.engine.FaceCondition;
import com.diamon.civil.engine.GmshRunner;
import com.diamon.civil.engine.InpEnricher;
import com.diamon.civil.engine.InpGenerator;
import com.diamon.civil.engine.MshToInpConverter;
import com.diamon.civil.engine.NativeFeaCore;
import com.diamon.civil.engine.OcctBooleanJNI;
import com.diamon.civil.engine.OcctPrimitivesJNI;
import com.diamon.civil.engine.ReportGenerator;
import com.diamon.civil.engine.StructuralModel;
import com.diamon.civil.engine.TerminalCommandExecutor;
import com.diamon.civil.io.AbaqusInpImporter;
import com.diamon.civil.io.FileHelper;
import com.diamon.civil.test.AutoTester;
import com.diamon.civil.util.AssetHelper;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;

import io.github.sceneview.collision.HitResult;
import io.github.sceneview.node.ModelNode;
import io.github.sceneview.node.LightNode;
import dev.romainguy.kotlin.math.Float3;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, OnHitListener {

    private ActivityMainBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private CalculixExecutor calculixExecutor;
    private TerminalCommandExecutor terminalExecutor;
    private AssetHelper assetHelper;
    private FileHelper fileHelper;
    private InpGenerator inpGenerator;
    private GmshRunner gmshRunner;
    private MshToInpConverter mshConverter;
    private DatParser datParser;
    private DatParser.ParseResult lastParseResult;
    private final List<FaceCondition> faceConditions = new ArrayList<>();
    
    // UI State Controller
    private class UIStateController {
        void showLoading(String message) {
            binding.pbStructural.setVisibility(View.VISIBLE);
            binding.pb3D.setVisibility(View.VISIBLE);
            binding.tvStructuralResult.setText(message);
            binding.tvBasicResult.setText(message);
            binding.btnSolveStructural.setEnabled(false);
            binding.btnRunAnalysis.setEnabled(false);
        }

        void hideLoading() {
            binding.pbStructural.setVisibility(View.GONE);
            binding.pb3D.setVisibility(View.GONE);
            binding.btnSolveStructural.setEnabled(true);
            binding.btnRunAnalysis.setEnabled(true);
        }
    }
    private final UIStateController uiState = new UIStateController();

    private final ActivityResultLauncher<Intent> importLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    handleImport(result.getData().getData());
                }
            }
    );

    private final ActivityResultLauncher<Intent> exportPdfLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    handleExportPdf(result.getData().getData());
                }
            }
    );

    private final ActivityResultLauncher<Intent> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    handleExport(result.getData().getData());
                }
            }
    );

    private final ActivityResultLauncher<Intent> exportZipLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    handleExportAll(result.getData().getData());
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Dependency Initialization
        calculixExecutor = new CalculixExecutor(this);
        terminalExecutor = new TerminalCommandExecutor(getFilesDir());
        assetHelper = new AssetHelper(this);
        fileHelper = new FileHelper(getContentResolver());
        inpGenerator = new InpGenerator();
        gmshRunner = new GmshRunner(getFilesDir(), new File(getApplicationInfo().nativeLibraryDir));
        mshConverter = new MshToInpConverter();
        datParser = new DatParser();

        binding.btnCreateBox.setOnClickListener(v -> showCreateBoxDialog());
        binding.btnCreateCylinder.setOnClickListener(v -> showCreateCylinderDialog());
        binding.btnCreateSphere.setOnClickListener(v -> showCreateSphereDialog());
        binding.btnFuse.setOnClickListener(v -> runBooleanOperation(0));
        binding.btnCut.setOnClickListener(v -> runBooleanOperation(1));
        binding.btnIntersect.setOnClickListener(v -> runBooleanOperation(2));
        
        binding.seekbarMeshDensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                binding.tvBasicResult.setText("Mesh Density: " + (progress + 1));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Initialize Compose-based SceneView
        SceneViewBridgeKt.setSceneViewContent(binding.sceneViewComposeContainer, "models/test_beam.glb", this);

        setupNavigation();
        setupUI();
        checkAndLoadAssets();
    }

    private void setupNavigation() {
        binding.navView.setNavigationItemSelectedListener(this);
        // Default module
        switchModule(R.id.nav_3d_solid);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        gmshRunner.shutdown();
    }

    private void setupUI() {
        // Sub-Tabs for 3D Solid Module
        binding.tabLayout3D.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    binding.layout3DParams.setVisibility(View.VISIBLE);
                    binding.sceneViewComposeContainer.setVisibility(View.GONE);
                } else {
                    binding.layout3DParams.setVisibility(View.GONE);
                    binding.sceneViewComposeContainer.setVisibility(View.VISIBLE);
                }
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Material Presets
        binding.spinnerMaterial.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0: setMaterialParams("210000", "7850", false); break; // Steel
                    case 1: setMaterialParams("30000", "2400", false); break;  // Concrete
                    case 2: setMaterialParams("11000", "600", false); break;   // Wood
                    case 3: setMaterialParams("69000", "2700", false); break;  // Aluminum
                    case 4: binding.layoutModulus.setEnabled(true); break;     // Custom
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Event Listeners
        binding.tvLog.setOnClickListener(v -> copyToClipboard(binding.tvLog.getText().toString(), "Terminal Log"));
        binding.btnCopyResult.setOnClickListener(v -> copyToClipboard(binding.tvBasicResult.getText().toString(), "FEA Result"));
        binding.btnClearResult.setOnClickListener(v -> binding.tvBasicResult.setText("Ready for computation."));
        binding.btnRunAnalysis.setOnClickListener(v -> runAnalysis());
        binding.btnSolveStructural.setOnClickListener(v -> runStructuralAnalysisNative());
        binding.btnShowBMD.setOnClickListener(v -> binding.diagramView.setDiagramType(1));
        binding.btnShowSFD.setOnClickListener(v -> binding.diagramView.setDiagramType(2));
        binding.btnShowAFD.setOnClickListener(v -> binding.diagramView.setDiagramType(3));
        binding.btnHideDiagram.setOnClickListener(v -> binding.diagramView.setDiagramType(0));
        binding.btnImportInp.setOnClickListener(v -> openInpFilePicker());
        
        binding.btnSend.setOnClickListener(v -> sendTerminalCommand());
        binding.btnImportCad.setOnClickListener(v -> openCadFilePicker());
        binding.etCommand.setOnEditorActionListener((v, actionId, event) -> {
            sendTerminalCommand();
            return true;
        });
    }

    private void handleSceneTouch(float x, float y) {
        // Simplified: Highlight the model if touched
        Toast.makeText(this, "Face selected at: " + x + ", " + y, Toast.LENGTH_SHORT).show();
        // In a real flow, we would use Ray-Casting to find the exact face
    }

    @Override
    public void onHit(HitResult hitResult) {
        if (hitResult != null && hitResult.getNode() instanceof ModelNode) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Surface Selected", Toast.LENGTH_SHORT).show();
                showFaceConditionDialog(1); // Simplified: surface ID 1
            });
        }
    }

    private void showFaceConditionDialog(int surfaceId) {
        String[] options = {"Fixed Support", "Pressure (1000 N)", "Mesh Refinement"};
        new AlertDialog.Builder(this)
                .setTitle("Surface " + surfaceId + " Options")
                .setItems(options, (dialog, which) -> {
                    FaceCondition.Type type = FaceCondition.Type.FIXED;
                    double value = 0.0;
                    if (which == 1) {
                        type = FaceCondition.Type.PRESSURE;
                        value = -1000.0;
                    } else if (which == 2) {
                        type = FaceCondition.Type.MESH_REFINEMENT;
                        value = 1.0;
                    }
                    faceConditions.add(new FaceCondition(surfaceId, type, value));
                    Toast.makeText(this, "Condition applied to surface " + surfaceId, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private final ActivityResultLauncher<Intent> inpFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) handleInpFileSelected(uri);
                }
            });

    private void openInpFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        inpFileLauncher.launch(Intent.createChooser(intent, "Select CalculiX/Abaqus .inp file"));
    }

    private void handleInpFileSelected(Uri uri) {
        File destFile = new File(getFilesDir(), "imported_model.inp");
        if (fileHelper.importFile(uri, destFile)) {
            runInpImport(destFile);
        }
    }

    private void runInpImport(File inpFile) {
        executor.execute(() -> {
            try {
                StructuralModel model = new AbaqusInpImporter().importInp(inpFile);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Imported " + model.nodes.size() + " nodes", Toast.LENGTH_SHORT).show();
                    // Load into Structural Editor
                    binding.frameGLView.getRenderer().clear();
                    for (StructuralModel.Node n : model.nodes) binding.frameGLView.getRenderer().addNode(n);
                    for (StructuralModel.Element e : model.elements) binding.frameGLView.getRenderer().addElement(e);
                    binding.frameGLView.requestRender();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, "Import Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ── C1: CAD Primitives ──────────────────────────────────────────────────

    private void showCreateBoxDialog() {
        showPrimitiveDialog("Create Box", new String[]{"Length", "Width", "Height"}, (values) -> {
            File outFile = new File(getFilesDir(), "box.brep");
            boolean success = OcctPrimitivesJNI.createBox(values[0], values[1], values[2], outFile.getAbsolutePath());
            handlePrimitiveResult(success, outFile);
        });
    }

    private void showCreateCylinderDialog() {
        showPrimitiveDialog("Create Cylinder", new String[]{"Radius", "Height"}, (values) -> {
            File outFile = new File(getFilesDir(), "cylinder.brep");
            boolean success = OcctPrimitivesJNI.createCylinder(values[0], values[1], outFile.getAbsolutePath());
            handlePrimitiveResult(success, outFile);
        });
    }

    private void showCreateSphereDialog() {
        showPrimitiveDialog("Create Sphere", new String[]{"Radius"}, (values) -> {
            File outFile = new File(getFilesDir(), "sphere.brep");
            boolean success = OcctPrimitivesJNI.createSphere(values[0], outFile.getAbsolutePath());
            handlePrimitiveResult(success, outFile);
        });
    }

    private interface PrimitiveCallback {
        void onExecute(double[] values);
    }

    private void showPrimitiveDialog(String title, String[] labels, PrimitiveCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        EditText[] edits = new EditText[labels.length];
        for (int i = 0; i < labels.length; i++) {
            edits[i] = new EditText(this);
            edits[i].setHint(labels[i]);
            edits[i].setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            layout.addView(edits[i]);
        }

        builder.setView(layout);
        builder.setPositiveButton("Create", (dialog, which) -> {
            double[] values = new double[labels.length];
            for (int i = 0; i < labels.length; i++) {
                try {
                    values[i] = Double.parseDouble(edits[i].getText().toString());
                } catch (Exception e) {
                    values[i] = 1.0;
                }
            }
            callback.onExecute(values);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void handlePrimitiveResult(boolean success, File file) {
        if (success) {
            Toast.makeText(this, "Solid created: " + file.getName(), Toast.LENGTH_SHORT).show();
            // In a real flow, we would trigger Gmsh meshing here
            runCadPipeline(file);
        } else {
            Toast.makeText(this, "Failed to create solid", Toast.LENGTH_SHORT).show();
        }
    }

    private void runBooleanOperation(int type) {
        File fileA = new File(getFilesDir(), "box.brep");
        File fileB = new File(getFilesDir(), "cylinder.brep");
        File outFile = new File(getFilesDir(), "boolean_result.brep");

        if (!fileA.exists() || !fileB.exists()) {
            Toast.makeText(this, "Need Box and Cylinder to perform operation", Toast.LENGTH_LONG).show();
            return;
        }

        executor.execute(() -> {
            boolean success = false;
            if (type == 0) success = OcctBooleanJNI.fuse(fileA.getAbsolutePath(), fileB.getAbsolutePath(), outFile.getAbsolutePath());
            else if (type == 1) success = OcctBooleanJNI.cut(fileA.getAbsolutePath(), fileB.getAbsolutePath(), outFile.getAbsolutePath());
            else if (type == 2) success = OcctBooleanJNI.intersect(fileA.getAbsolutePath(), fileB.getAbsolutePath(), outFile.getAbsolutePath());

            boolean finalSuccess = success;
            runOnUiThread(() -> handlePrimitiveResult(finalSuccess, outFile));
        });
    }

    // ── A1: CAD Pipeline ─────────────────────────────────────────────────────

    private final ActivityResultLauncher<Intent> cadFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) handleCadFileSelected(uri);
                }
            });

    private void openCadFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"model/stl", "application/octet-stream", "*/*"});
        cadFileLauncher.launch(Intent.createChooser(intent, "Select CAD file (STL/STEP/IGES)"));
    }

    private void handleCadFileSelected(Uri uri) {
        String fileName = fileHelper.getFileName(uri);
        if (fileName == null) fileName = "cad_import_" + System.currentTimeMillis() + ".step";

        File destFile = new File(getFilesDir(), fileName);
        boolean copied = fileHelper.importFile(uri, destFile);
        if (!copied) {
            Toast.makeText(this, "Failed to copy CAD file", Toast.LENGTH_SHORT).show();
            return;
        }
        runCadPipeline(destFile);
    }

    /**
     * A1: Full CAD pipeline — Gmsh → MshToInp → InpEnricher → CalculiX → FRD→GLB
     */
    private void runCadPipeline(File cadFile) {
        binding.tvBasicResult.setText("Meshing with Gmsh...");
        binding.btnImportCad.setEnabled(false);

        int density = binding.seekbarMeshDensity.getProgress() + 1;

        gmshRunner.meshAsync(cadFile, density, new GmshRunner.GmshCallback() {
            @Override
            public void onSuccess(File mshFile) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    binding.tvBasicResult.setText("Mesh OK. Converting to INP...");
                });
                executor.execute(() -> {
                    try {
                        // Convert .msh → .inp skeleton
                        File inpSkeleton = new File(getFilesDir(), "cad_mesh.inp");
                        MshToInpConverter.ConversionResult conv =
                                mshConverter.convert(mshFile, inpSkeleton);

                        if (!conv.success) {
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;
                                binding.tvBasicResult.setText("Conversion Error: " + conv.message);
                                binding.btnImportCad.setEnabled(true);
                            });
                            return;
                        }

                        // Enrich: inject material + BCs
                        File enrichedInp = new File(getFilesDir(), "cad_analysis.inp");
                        String modulus = binding.etModulus.getText().toString();
                        String density2 = binding.etDensity.getText().toString();
                        new InpEnricher().enrich(inpSkeleton, enrichedInp,
                                modulus, "0.3", density2, faceConditions);

                        // Copy to final job name (CalculiX needs file without .inp)
                        File finalInp = new File(getFilesDir(), "cad_simulation.inp");
                        if (enrichedInp.exists()) enrichedInp.renameTo(finalInp);

                        // Run CalculiX
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            binding.tvBasicResult.setText(
                                conv.message + "\nRunning CalculiX...");
                        });
                        String ccxResult = calculixExecutor.executeCalculix("cad_simulation");

                        // Convert FRD → GLB
                        File frdFile = new File(getFilesDir(), "cad_simulation.frd");
                        File glbFile = new File(getFilesDir(), "cad_simulation.glb");
                        boolean converted = frdFile.exists() &&
                                calculixExecutor.convertFrdToGlb(
                                        frdFile.getAbsolutePath(), glbFile.getAbsolutePath());

                        final boolean finalConverted = converted;
                        final String finalCcx = ccxResult;
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            binding.tvBasicResult.setText(
                                    "CAD PIPELINE COMPLETE\n" + conv.message + "\n\n" + finalCcx);
                            binding.btnImportCad.setEnabled(true);
                            if (finalConverted) {
                                Toast.makeText(MainActivity.this,
                                        "3D Model Generated", Toast.LENGTH_SHORT).show();
                                cargarModeloExterno(glbFile);
                                binding.tabLayout3D.getTabAt(1).select();
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            binding.tvBasicResult.setText("Pipeline Error: " + e.getMessage());
                            binding.btnImportCad.setEnabled(true);
                        });
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    binding.tvBasicResult.setText("Gmsh Error: " + message);
                    binding.btnImportCad.setEnabled(true);
                });
            }
        });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_docs) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.calculix.de/html/ccx.html")));
        } else if (id == R.id.nav_about) {
            showAboutDialog();
        } else {
            switchModule(id);
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void switchModule(int navId) {
        binding.layoutStructural.setVisibility(navId == R.id.nav_structural ? View.VISIBLE : View.GONE);
        binding.containerStructural.setVisibility(navId == R.id.nav_structural ? View.VISIBLE : View.GONE);
        binding.frameGLView.setVisibility(navId == R.id.nav_structural ? View.VISIBLE : View.GONE);
        binding.diagramView.setVisibility(navId == R.id.nav_structural ? View.VISIBLE : View.GONE);
        binding.layout3DSolid.setVisibility(navId == R.id.nav_3d_solid ? View.VISIBLE : View.GONE);
        binding.layoutConsole.setVisibility(navId == R.id.nav_terminal ? View.VISIBLE : View.GONE);
        
        String title = "FEA Suite";
        if (navId == R.id.nav_structural) title = "Structural Analysis";
        else if (navId == R.id.nav_3d_solid) title = "3D Solid Analysis";
        else if (navId == R.id.nav_terminal) title = "Advanced Terminal";
        
        if (binding.toolbar != null) {
            binding.toolbar.setTitle(title);
        }
    }

    private void setMaterialParams(String modulus, String density, boolean editable) {
        binding.etModulus.setText(modulus);
        binding.etDensity.setText(density);
        binding.layoutModulus.setEnabled(editable);
    }

    private void refreshDiagram(DatParser.ParseResult forces, StructuralModel model) {
        runOnUiThread(() -> {
            binding.diagramView.setModelAndResults(model, forces);
            binding.diagramView.setDiagramType(1); // Default to BMD
        });
    }

    private void runStructuralAnalysisNative() {
        String nodesText = binding.etNodes.getText().toString().trim();
        String elementsText = binding.etElements.getText().toString().trim();

        if (nodesText.isEmpty() || elementsText.isEmpty()) {
            binding.tvStructuralResult.setText("Error: Nodes and Elements definitions cannot be empty.");
            return;
        }

        binding.tvStructuralResult.setText("Executing Native CalculiX Solver...");
        binding.pbStructural.setVisibility(View.VISIBLE);
        binding.btnSolveStructural.setEnabled(false);

        executor.execute(() -> {
            long modelPtr = 0;
            NativeFeaCore core = new NativeFeaCore();
            try {
                StructuralModel model = new StructuralModel();
                // Parse Nodes
                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("{\n");
                jsonBuilder.append("  \"nodes\": [\n");
                String[] nodeLines = nodesText.split("\n");
                List<Integer> parsedNodeIds = new ArrayList<>();
                for (String nodeLine : nodeLines) {
                    String line = nodeLine.trim();
                    if (line.isEmpty()) continue;
                    String[] tokens = line.split(",");
                    if (tokens.length < 3) continue;
                    int id = Integer.parseInt(tokens[0].trim());
                    double x = Double.parseDouble(tokens[1].trim());
                    double y = Double.parseDouble(tokens[2].trim());
                    double z = (tokens.length >= 4) ? Double.parseDouble(tokens[3].trim()) : 0.0;
                    parsedNodeIds.add(id);
                    model.nodes.add(new StructuralModel.Node(id, x, y, z));

                    if (parsedNodeIds.size() > 1) {
                        jsonBuilder.append(",\n");
                    }
                    jsonBuilder.append(String.format("    {\"id\": %d, \"x\": %f, \"y\": %f, \"z\": %f}", id, x, y, z));
                }
                jsonBuilder.append("\n  ],\n");

                if (parsedNodeIds.isEmpty()) {
                    runOnUiThread(() -> binding.tvStructuralResult.setText("Error: No valid nodes parsed. Format: id, x, y"));
                    return;
                }

                // Parse Elements
                jsonBuilder.append("  \"elements\": [\n");
                String[] elementLines = elementsText.split("\n");
                int elementCount = 0;
                boolean hasBeams = false;
                boolean hasSolids = false;
                
                for (String line : elementLines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    String[] tokens = line.split(",");
                    if (tokens.length < 3) continue;
                    
                    int id = Integer.parseInt(tokens[0].trim());
                    String type = "B31";
                    String elset = "EBEAM";
                    List<Integer> nodeIds = new ArrayList<>();
                    
                    if (tokens.length == 3) {
                        type = "B31";
                        elset = "EBEAM";
                        nodeIds.add(Integer.parseInt(tokens[1].trim()));
                        nodeIds.add(Integer.parseInt(tokens[2].trim()));
                        hasBeams = true;
                    } else if (tokens.length == 5) {
                        type = "C3D4";
                        elset = "ESOLID";
                        nodeIds.add(Integer.parseInt(tokens[1].trim()));
                        nodeIds.add(Integer.parseInt(tokens[2].trim()));
                        nodeIds.add(Integer.parseInt(tokens[3].trim()));
                        nodeIds.add(Integer.parseInt(tokens[4].trim()));
                        hasSolids = true;
                    }
                    
                    model.elements.add(new StructuralModel.Element(id, nodeIds.get(0), nodeIds.get(1), elset, "Steel"));

                    if (elementCount > 0) {
                        jsonBuilder.append(",\n");
                    }
                    
                    jsonBuilder.append(String.format("    {\"id\": %d, \"type\": \"%s\", \"elset\": \"%s\", \"nodes\": [", id, type, elset));
                    for (int i = 0; i < nodeIds.size(); i++) {
                        jsonBuilder.append(nodeIds.get(i)).append(i == nodeIds.size() - 1 ? "" : ", ");
                    }
                    jsonBuilder.append("]}");
                    elementCount++;
                }
                jsonBuilder.append("\n  ],\n");

                if (elementCount == 0) {
                    runOnUiThread(() -> binding.tvStructuralResult.setText("Error: No valid elements parsed. Format: id, n1, n2 or id, n1, n2, n3, n4"));
                    return;
                }

                // Default Material
                jsonBuilder.append("  \"materials\": [\n");
                jsonBuilder.append("    {\"name\": \"Steel\", \"youngModulus\": 210000.0, \"poissonRatio\": 0.3, \"density\": 7850.0}\n");
                jsonBuilder.append("  ],\n");

                // Sections (Mixed Modeling)
                jsonBuilder.append("  \"sections\": [\n");
                boolean firstSec = true;
                if (hasBeams) {
                    jsonBuilder.append("    {\"elset\": \"EBEAM\", \"type\": \"BEAM\", \"material\": \"Steel\", \"params\": [0.3, 0.5]}");
                    firstSec = false;
                }
                if (hasSolids) {
                    if (!firstSec) jsonBuilder.append(",\n");
                    jsonBuilder.append("    {\"elset\": \"ESOLID\", \"type\": \"SOLID\", \"material\": \"Steel\", \"params\": []}");
                }
                jsonBuilder.append("\n  ],\n");

                // Boundary Conditions - Fix first node
                int firstNodeId = parsedNodeIds.get(0);
                jsonBuilder.append("  \"constraints\": [\n");
                jsonBuilder.append(String.format("    {\"nodeId\": %d, \"dofs\": [1, 2, 3, 4, 5, 6], \"value\": 0.0}\n", firstNodeId));
                jsonBuilder.append("  ],\n");

                // Loads - Apply load to the last node
                int lastNodeId = parsedNodeIds.get(parsedNodeIds.size() - 1);
                jsonBuilder.append("  \"loads\": [\n");
                jsonBuilder.append(String.format("    {\"nodeId\": %d, \"fx\": 0.0, \"fy\": -100.0, \"fz\": 0.0}\n", lastNodeId));
                jsonBuilder.append("  ]\n");

                jsonBuilder.append("}");

                String jsonStr = jsonBuilder.toString();
                
                modelPtr = core.createModel();
                core.modelFromJson(modelPtr, jsonStr);
                
                String workDirPath = getFilesDir().getAbsolutePath();
                String libDirPath = getApplicationInfo().nativeLibraryDir;
                String solverResult = core.runCalculix(workDirPath, libDirPath, "structural_simulation", modelPtr);
                
                final String finalResult = solverResult;
                runOnUiThread(() -> {
                    binding.tvStructuralResult.setText("STRUCTURAL SIMULATION COMPLETED\n================================\n" + finalResult);
                    binding.pbStructural.setVisibility(View.GONE);
                    binding.btnSolveStructural.setEnabled(true);
                    File datFile = new File(getFilesDir(), "structural_simulation.dat");
                    if (datFile.exists()) {
                        lastParseResult = datParser.parse(datFile);
                        refreshDiagram(lastParseResult, model);
                    }
                });

            } catch (Exception e) {
                final String errorMsg = e.getMessage();
                runOnUiThread(() -> {
                    binding.tvStructuralResult.setText("CRITICAL ERROR: " + errorMsg);
                    binding.pbStructural.setVisibility(View.GONE);
                    binding.btnSolveStructural.setEnabled(true);
                });
            } finally {
                if (modelPtr != 0) {
                    core.deleteModel(modelPtr);
                }
            }
        });
    }

    private void runAnalysis() {
        String length = binding.etLength.getText().toString();
        String section = binding.etSection.getText().toString();
        String modulus = binding.etModulus.getText().toString();
        String density = binding.etDensity.getText().toString();
        String pointLoad = binding.etLoad.getText().toString();
        String distLoad = binding.etDistLoad.getText().toString();
        
        int mode = binding.spinnerAnalysisMode.getSelectedItemPosition();
        int support = binding.spinnerSupport.getSelectedItemPosition();

        binding.tvBasicResult.setText("Starting CalculiX Core Engine...");
        binding.pb3D.setVisibility(View.VISIBLE);
        binding.btnRunAnalysis.setEnabled(false);
        
        executor.execute(() -> {
            try {
                File jobFile = new File(getFilesDir(), "structural_simulation.inp");
                inpGenerator.generateFullInpFile(jobFile, length, section, modulus, density, pointLoad, distLoad, mode, support);
                
                String result = calculixExecutor.executeCalculix("structural_simulation");
                
                File frdFile = new File(getFilesDir(), "structural_simulation.frd");
                File glbFile = new File(getFilesDir(), "structural_simulation.glb");
                boolean converted = false;
                if (frdFile.exists()) {
                    converted = calculixExecutor.convertFrdToGlb(frdFile.getAbsolutePath(), glbFile.getAbsolutePath());
                }

                final boolean finalConverted = converted;
                runOnUiThread(() -> {
                    binding.tvBasicResult.setText("ANALYSIS COMPLETE\n=================\n" + result);
                    binding.pb3D.setVisibility(View.GONE);
                    binding.btnRunAnalysis.setEnabled(true);
                    if (finalConverted) {
                        Toast.makeText(this, "3D Model Generated", Toast.LENGTH_SHORT).show();
                        cargarModeloExterno(glbFile);
                        binding.tabLayout3D.getTabAt(1).select();
                    }
                    // A2: Parse section forces from .dat
                    File datFile = new File(getFilesDir(), "structural_simulation.dat");
                    if (datFile.exists()) {
                        lastParseResult = datParser.parse(datFile);
                        String forceSummary = datParser.formatSummary(lastParseResult);
                        binding.tvBasicResult.append("\n\n" + forceSummary);
                        
                        // Update 2D diagram
                        try {
                            double L = Double.parseDouble(length);
                            StructuralModel model = buildBeamModel(L, 20, section, "Steel");
                            refreshDiagram(lastParseResult, model);
                        } catch (Exception e) {}
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.tvBasicResult.setText("CRITICAL ERROR: " + e.getMessage());
                    binding.pb3D.setVisibility(View.GONE);
                    binding.btnRunAnalysis.setEnabled(true);
                });
            }
        });
    }

    private StructuralModel buildBeamModel(double L, int numElements, String section, String material) {
        StructuralModel model = new StructuralModel();
        for (int i = 0; i <= numElements; i++) {
            model.nodes.add(new StructuralModel.Node(i + 1, (L * i / numElements), 0, 0));
        }
        for (int i = 1; i <= numElements; i++) {
            model.elements.add(new StructuralModel.Element(i, i, i + 1, section, material));
        }
        return model;
    }

    private void cargarModeloExterno(File file) {
        SceneViewBridgeKt.setSceneViewContent(binding.sceneViewComposeContainer, file.getAbsolutePath(), this);
    }

    private void sendTerminalCommand() {
        String input = binding.etCommand.getText().toString().trim();
        if (input.isEmpty()) return;
        
        binding.etCommand.setText("");
        if (input.equalsIgnoreCase("clear")) {
            binding.tvLog.setText("--- Shared FEA Terminal Core ---\n");
            return;
        }

        binding.tvLog.append("\n$ " + input + "\n");
        scrollLogDown();
        
        executor.execute(() -> {
            String result = terminalExecutor.execute(input);
            if (result == null) {
                String[] parts = input.split("\\s+");
                if (parts.length > 0) {
                    String binary = parts[0];
                    if (binary.equalsIgnoreCase("gmsh")) {
                        String[] args = new String[parts.length - 1];
                        System.arraycopy(parts, 1, args, 0, args.length);
                        result = calculixExecutor.executeBinary("gmsh", args);
                    } else if (binary.endsWith(".inp")) {
                        result = calculixExecutor.executeCalculix(binary);
                    } else {
                        String[] args = new String[parts.length - 1];
                        System.arraycopy(parts, 1, args, 0, args.length);
                        result = calculixExecutor.executeBinary(binary, args);
                    }
                }
            }
            final String finalResult = result;
            runOnUiThread(() -> {
                binding.tvLog.append(finalResult + "\n");
                scrollLogDown();
            });
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        
        int id = item.getItemId();
        if (id == R.id.action_import) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("*/*");
            importLauncher.launch(intent);
            return true;
        } else if (id == R.id.action_export) {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TITLE, "FEA_Report.txt");
            exportLauncher.launch(intent);
            return true;
        } else if (id == R.id.action_export_pdf) {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/pdf")
                    .putExtra(Intent.EXTRA_TITLE, "FEA_Analysis_Report.pdf");
            exportPdfLauncher.launch(intent);
            return true;
        } else if (id == R.id.action_export_all) {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip")
                    .putExtra(Intent.EXTRA_TITLE, "FEA_Project_Files.zip");
            exportZipLauncher.launch(intent);
            return true;
        } else if (id == R.id.action_reset) {
            showResetDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("About FEA Suite")
                .setMessage("Structural & 3D Solid Analysis\nPowered by CalculiX & GMSH\n\nDeveloped by Daniel Diamon")
                .setPositiveButton("Close", null)
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }

    private void showResetDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Reset System")
                .setMessage("Reinstall native binaries?")
                .setPositiveButton("Reset", (dialog, which) -> {
                    getSharedPreferences("AssetHelperPrefs", MODE_PRIVATE).edit().clear().apply();
                    recreate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleImport(Uri uri) {
        if (uri == null) return;
        executor.execute(() -> {
            String fileName = fileHelper.getFileName(uri);
            if (fileName == null) fileName = "imported_" + System.currentTimeMillis();
            File destFile = new File(terminalExecutor.getCurrentDir(), fileName);
            boolean success = fileHelper.importFile(uri, destFile);
            runOnUiThread(() -> {
                if (success) {
                    binding.tvLog.append("File imported: " + destFile.getName() + "\n");
                    scrollLogDown();
                } else {
                    Toast.makeText(this, "Import Failed", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void handleExportAll(Uri uri) {
        if (uri == null) return;
        executor.execute(() -> {
            File[] files = getFilesDir().listFiles();
            boolean success = false;
            if (files != null) success = fileHelper.zipFiles(files, uri);
            final boolean finalSuccess = success;
            runOnUiThread(() -> Toast.makeText(this, finalSuccess ? "All files exported to ZIP" : "Export Failed", Toast.LENGTH_SHORT).show());
        });
    }

    private void handleExport(Uri uri) {
        if (uri == null) return;
        executor.execute(() -> {
            String content = binding.tvLog.getText().toString() + "\n\n--- RESULTS ---\n" + binding.tvBasicResult.getText().toString();
            boolean success = fileHelper.exportText(uri, content);
            runOnUiThread(() -> Toast.makeText(this, success ? "Report Exported" : "Export Failed", Toast.LENGTH_SHORT).show());
        });
    }

    private void handleExportPdf(Uri uri) {
        if (uri == null) return;
        executor.execute(() -> {
            File tempFile = new File(getCacheDir(), "temp_report.pdf");
            String summary = binding.tvBasicResult.getText().toString();
            List<DatParser.SectionForces> forces = (lastParseResult != null) ? lastParseResult.forces : null;

            boolean success = ReportGenerator.generateReport(tempFile, "FEA Structural Analysis Report", summary, forces);

            if (success) {
                boolean copied = fileHelper.exportFile(tempFile, uri);
                runOnUiThread(() -> {
                    Toast.makeText(this, copied ? "PDF Report Exported" : "Export Failed", Toast.LENGTH_SHORT).show();
                    tempFile.delete();
                });
            } else {
                runOnUiThread(() -> Toast.makeText(this, "Failed to generate PDF", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void scrollLogDown() {
        binding.scrollLog.post(() -> binding.scrollLog.fullScroll(View.FOCUS_DOWN));
    }

    private void checkAndLoadAssets() {
        if (assetHelper.areAssetsExtracted()) {
            binding.layoutLoading.setVisibility(View.GONE);
            executor.execute(() -> {
                assetHelper.ensureRuntimeReady();
                runOnUiThread(() -> AutoTester.run(this));
            });
        } else {
            binding.layoutLoading.setVisibility(View.VISIBLE);
            binding.tvLoadingText.setText("Deploying FEM Core Engine...");
            executor.execute(() -> {
                boolean success = assetHelper.ensureRuntimeReady();
                runOnUiThread(() -> {
                    binding.layoutLoading.setVisibility(View.GONE);
                    if (!success) {
                        Toast.makeText(this, "Engine Failure", Toast.LENGTH_LONG).show();
                    } else {
                        AutoTester.run(this);
                    }
                });
            });
        }
    }

    private void copyToClipboard(String text, String label) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
