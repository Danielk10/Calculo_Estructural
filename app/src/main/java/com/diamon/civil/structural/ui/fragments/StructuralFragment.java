package com.diamon.civil.structural.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.diamon.civil.R;
import com.diamon.civil.databinding.FragmentStructuralBinding;
import com.diamon.civil.structural.engine.CalculixExecutor;
import com.diamon.civil.structural.engine.StructuralBeamDatParser;
import com.diamon.civil.structural.engine.NativeFeaCore;
import com.diamon.civil.structural.engine.StructuralModel;
import com.diamon.civil.core.util.logging.ModuleLogger;
import com.google.android.material.tabs.TabLayout;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.diamon.civil.structural.engine.MaterialDatabase;
import com.diamon.civil.structural.engine.SectionLibrary;
import com.diamon.civil.structural.ui.views.GridEditorView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;


public class StructuralFragment extends Fragment {

    private FragmentStructuralBinding binding;
    private final ModuleLogger logger = new ModuleLogger("Structural");
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private CalculixExecutor calculixExecutor;
    private StructuralBeamDatParser datParser;
    
    private StructuralModel currentModel;
    private MaterialDatabase materialDatabase;
    private SectionLibrary sectionLibrary;

    private StructuralBeamDatParser.ParseResult currentResult;
    private float currentDispScale = 1000f;

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
        final File workDir = new File(appContext.getFilesDir(), "structural_analysis");
        if (!workDir.exists()) workDir.mkdirs();
        executor.execute(() -> {
            try {
                NativeFeaCore.loadLibraries();
                calculixExecutor = new CalculixExecutor(appContext, workDir);
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
        datParser = new StructuralBeamDatParser();
        logger.attachToTextView(binding.tvStructuralLog);

        setupTabs();
        setupButtons();
        
        try {
            ArrayAdapter<CharSequence> structTypeAdapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.structure_types_structural, R.layout.item_spinner_compact);
            structTypeAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown_compact);
            binding.spinnerStructureType.setAdapter(structTypeAdapter);

            ArrayAdapter<CharSequence> elemTypeAdapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.element_types_structural, R.layout.item_spinner_compact);
            elemTypeAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown_compact);
            binding.spinnerElementTypeStructural.setAdapter(elemTypeAdapter);

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
            ArrayAdapter<String> matAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_compact, matNames);
            matAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown_compact);
            binding.spinnerMaterialStructural.setAdapter(matAdapter);
            if (!matNames.isEmpty() && binding.gridEditorView != null) {
                binding.gridEditorView.setDefaultMaterial(matNames.get(0));
            }
            
            sectionLibrary = new SectionLibrary();
            if (getContext() != null) {
                try {
                    sectionLibrary.loadFromAssets(requireContext());
                } catch (Exception e) {
                    logger.warn("Using default sections: " + e.getMessage());
                }
            }
            List<String> secNames = new ArrayList<>();
            for (SectionLibrary.Section s : sectionLibrary.getSections()) {
                secNames.add(s.name);
            }
            ArrayAdapter<String> secAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_compact, secNames);
            secAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown_compact);
            binding.spinnerSectionStructural.setAdapter(secAdapter);
            if (!secNames.isEmpty() && binding.gridEditorView != null) {
                binding.gridEditorView.setDefaultSection(secNames.get(0));
            }

            binding.spinnerMaterialStructural.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (binding != null && binding.gridEditorView != null && parent.getItemAtPosition(position) != null) {
                        binding.gridEditorView.setDefaultMaterial(parent.getItemAtPosition(position).toString());
                    }
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });

            binding.spinnerSectionStructural.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (binding != null && binding.gridEditorView != null && parent.getItemAtPosition(position) != null) {
                        binding.gridEditorView.setDefaultSection(parent.getItemAtPosition(position).toString());
                    }
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        } catch (Exception e) {
            logger.error("Failed to load databases: " + e.getMessage());
        }

        loadDefaultTestCase();

    }

    public void loadModel(StructuralModel model) {
        if (model == null) return;
        currentModel = model;
        if (binding != null) {
            if (binding.gridEditorView != null) {
                binding.gridEditorView.setModel(model.nodes, model.elements);
            }
            if (binding.frameGLView != null) {
                binding.frameGLView.setModel(model);
            }
            logger.info("Loaded structural model: " + model.nodes.size() + " nodes, " + model.elements.size() + " elements");
        }
    }

    private void loadDefaultTestCase() {
        if (binding == null) return;
        binding.spinnerStructureType.setSelection(0);
        binding.spinnerElementTypeStructural.setSelection(0);
        if (binding.tvGridStats != null && binding.gridEditorView != null && isAdded()) {
            binding.tvGridStats.setText(getString(R.string.grid_stats_format, binding.gridEditorView.getNodes().size(), binding.gridEditorView.getElements().size()));
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
                    if (currentModel != null) {
                        binding.frameGLView.setModel(currentModel);
                    } else if (binding.gridEditorView != null) {
                        StructuralModel tempModel = new StructuralModel();
                        tempModel.nodes.addAll(binding.gridEditorView.getNodes());
                        tempModel.elements.addAll(binding.gridEditorView.getElements());
                        binding.frameGLView.setModel(tempModel);
                    }
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
        binding.fabClearStructuralLog.setOnClickListener(v -> logger.clear());
        
        setupGridEditorControls();
        
        binding.scrollStructuralLog.setOnClickListener(v -> copyToClipboard(logger.getFullLog()));
        
        binding.btnViewWireframe.setOnClickListener(v -> {
            binding.frameGLView.setShowUndeformed(true);
            binding.frameGLView.setShowDeformed(false);
            binding.frameGLView.setShowDiagrams(false);
            binding.frameGLView.setShowLoads(true);
        });
        
        binding.btnViewDeformed.setOnClickListener(v -> {
            binding.frameGLView.setShowUndeformed(true);
            binding.frameGLView.setShowDeformed(true);
            binding.frameGLView.setShowDiagrams(false);
            binding.frameGLView.setShowLoads(true);
        });
        
        binding.btnViewMoment.setOnClickListener(v -> {
            currentDiagramMode = DiagramMode.MOMENT_M33;
            if (currentModel != null && currentResult != null) {
                calculateVBOs(currentModel, currentResult);
            }
            binding.frameGLView.setShowUndeformed(true);
            binding.frameGLView.setShowDeformed(false);
            binding.frameGLView.setShowDiagrams(true);
            binding.frameGLView.setShowLoads(true);
        });
        
        binding.btnViewShear.setOnClickListener(v -> {
            currentDiagramMode = DiagramMode.SHEAR_V22;
            if (currentModel != null && currentResult != null) {
                calculateVBOs(currentModel, currentResult);
            }
            binding.frameGLView.setShowUndeformed(true);
            binding.frameGLView.setShowDeformed(false);
            binding.frameGLView.setShowDiagrams(true);
            binding.frameGLView.setShowLoads(true);
        });

        binding.btnViewAxial.setOnClickListener(v -> {
            currentDiagramMode = DiagramMode.AXIAL_N;
            if (currentModel != null && currentResult != null) {
                calculateVBOs(currentModel, currentResult);
            }
            binding.frameGLView.setShowUndeformed(true);
            binding.frameGLView.setShowDeformed(false);
            binding.frameGLView.setShowDiagrams(true);
            binding.frameGLView.setShowLoads(true);
        });

        binding.btnResetCamera.setOnClickListener(v -> {
            binding.frameGLView.resetCamera();
        });
        
        binding.tvStructuralLog.setOnClickListener(v -> copyToClipboard(logger.getFullLog()));

        binding.seekStructDeformScale.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                currentDispScale = (progress / 100f) * 1000f;
                if (isAdded()) {
                    binding.tvStructDeformScale.setText(String.format(java.util.Locale.US, getString(R.string.deformation_scale_format), currentDispScale / 1000f));
                }
                if (currentModel != null && currentResult != null) {
                    calculateVBOs(currentModel, currentResult);
                }
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
    }

    private void setupGridEditorControls() {
        if (binding == null || binding.gridEditorView == null) return;

        ArrayAdapter<CharSequence> presetAdapter = ArrayAdapter.createFromResource(
            requireContext(), R.array.structural_presets, R.layout.item_spinner_compact);
        presetAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown_compact);
        binding.spinnerPresets.setAdapter(presetAdapter);

        binding.spinnerPresets.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        binding.gridEditorView.loadPresetPortalFrame(4.0, 3.0);
                        break;
                    case 1:
                        binding.gridEditorView.loadPresetTwoBayFrame(4.0, 3.0);
                        break;
                    case 2:
                        binding.gridEditorView.loadPresetContinuousBeam(3.0);
                        break;
                    case 3:
                        binding.gridEditorView.loadPresetPitchedTruss(6.0, 3.0, 4.5);
                        break;
                    case 4:
                        binding.gridEditorView.loadPresetOverhangingBeam(4.0, 2.0);
                        break;
                    case 5:
                        binding.gridEditorView.loadPresetThreeStoryBuilding(3.0, 3.0);
                        break;
                    case 6:
                        binding.gridEditorView.loadPresetWarrenTrussBridge(12.0, 3.0);
                        break;
                    case 7:
                        binding.gridEditorView.loadPresetConcreteContinuousBeam(4.0, 3.0, 2.0);
                        break;
                    case 8:
                        binding.gridEditorView.loadPresetPrattTruss(10.0, 2.5);
                        break;
                    case 9:
                        binding.gridEditorView.loadPresetCantileverBracket(4.0, 3.0);
                        break;
                    case 10:
                        binding.gridEditorView.loadPresetConcreteSlabPlate(4.0, 4.0, 0.15);
                        break;
                    case 11:
                        binding.gridEditorView.loadPresetShearWall(3.0, 3.0, 0.20);
                        break;
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        binding.btnModeDraw.setOnClickListener(v -> {
            binding.gridEditorView.setMode(GridEditorView.Mode.DRAW);
            updateModeButtons(GridEditorView.Mode.DRAW);
        });
        binding.btnModePan.setOnClickListener(v -> {
            binding.gridEditorView.setMode(GridEditorView.Mode.PAN);
            updateModeButtons(GridEditorView.Mode.PAN);
        });
        binding.btnModeMoveNodes.setOnClickListener(v -> {
            binding.gridEditorView.setMode(GridEditorView.Mode.MOVE_NODES);
            updateModeButtons(GridEditorView.Mode.MOVE_NODES);
        });
        binding.btnModeInspect.setOnClickListener(v -> {
            binding.gridEditorView.setMode(GridEditorView.Mode.INSPECT);
            updateModeButtons(GridEditorView.Mode.INSPECT);
        });
        binding.btnModeSupport.setOnClickListener(v -> {
            binding.gridEditorView.setMode(GridEditorView.Mode.SUPPORT);
            updateModeButtons(GridEditorView.Mode.SUPPORT);
        });
        binding.btnModeLoad.setOnClickListener(v -> {
            binding.gridEditorView.setMode(GridEditorView.Mode.LOAD);
            updateModeButtons(GridEditorView.Mode.LOAD);
        });
        binding.btnModeDelete.setOnClickListener(v -> {
            binding.gridEditorView.setMode(GridEditorView.Mode.DELETE);
            updateModeButtons(GridEditorView.Mode.DELETE);
        });

        binding.btnUndoGrid.setOnClickListener(v -> binding.gridEditorView.undo());
        binding.btnClearGrid.setOnClickListener(v -> binding.gridEditorView.clear());

        binding.gridEditorView.setOnNodeSelectedListener(this::showNodePropertiesDialog);
        binding.gridEditorView.setOnElementSelectedListener(this::showElementPropertiesDialog);

        binding.gridEditorView.setOnComponentInspectedListener(infoText -> {
            if (binding != null && binding.tvComponentInfo != null && isAdded()) {
                binding.tvComponentInfo.setText(infoText);
                binding.tvComponentInfo.setVisibility(View.VISIBLE);
            }
        });

        binding.gridEditorView.setOnModelChangeListener((nodeCount, elementCount) -> {
            if (binding != null && binding.tvGridStats != null && isAdded()) {
                binding.tvGridStats.setText(getString(R.string.grid_stats_format, nodeCount, elementCount));
            }
        });

        updateModeButtons(GridEditorView.Mode.DRAW);
    }

    private void updateModeButtons(GridEditorView.Mode mode) {
        if (binding == null || !isAdded()) return;
        android.content.res.ColorStateList activeTint = android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.secondaryColor, null));
        android.content.res.ColorStateList inactiveTint = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT);
        android.content.res.ColorStateList activeText = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE);
        android.content.res.ColorStateList inactiveText = android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.primaryColor, null));
        android.content.res.ColorStateList activeStroke = android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.secondaryColor, null));
        android.content.res.ColorStateList inactiveStroke = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#38BDF8"));

        setButtonModeStyle(binding.btnModeDraw, mode == GridEditorView.Mode.DRAW, activeTint, inactiveTint, activeText, inactiveText, activeStroke, inactiveStroke);
        setButtonModeStyle(binding.btnModePan, mode == GridEditorView.Mode.PAN, activeTint, inactiveTint, activeText, inactiveText, activeStroke, inactiveStroke);
        setButtonModeStyle(binding.btnModeMoveNodes, mode == GridEditorView.Mode.MOVE_NODES || mode == GridEditorView.Mode.SELECT_MOVE, activeTint, inactiveTint, activeText, inactiveText, activeStroke, inactiveStroke);
        setButtonModeStyle(binding.btnModeInspect, mode == GridEditorView.Mode.INSPECT, activeTint, inactiveTint, activeText, inactiveText, activeStroke, inactiveStroke);
        setButtonModeStyle(binding.btnModeSupport, mode == GridEditorView.Mode.SUPPORT, activeTint, inactiveTint, activeText, inactiveText, activeStroke, inactiveStroke);
        setButtonModeStyle(binding.btnModeLoad, mode == GridEditorView.Mode.LOAD, activeTint, inactiveTint, activeText, inactiveText, activeStroke, inactiveStroke);
        setButtonModeStyle(binding.btnModeDelete, mode == GridEditorView.Mode.DELETE, activeTint, inactiveTint, activeText, inactiveText, activeStroke, inactiveStroke);

        if (binding.tvComponentInfo != null) {
            if (mode == GridEditorView.Mode.INSPECT) {
                binding.tvComponentInfo.setVisibility(View.VISIBLE);
                binding.tvComponentInfo.setText(binding.gridEditorView.getDetailedComponentInfo());
            } else {
                binding.tvComponentInfo.setVisibility(View.GONE);
            }
        }
    }

    private void setButtonModeStyle(com.google.android.material.button.MaterialButton btn, boolean isActive,
                                     android.content.res.ColorStateList activeTint, android.content.res.ColorStateList inactiveTint,
                                     android.content.res.ColorStateList activeText, android.content.res.ColorStateList inactiveText,
                                     android.content.res.ColorStateList activeStroke, android.content.res.ColorStateList inactiveStroke) {
        if (btn == null) return;
        btn.setBackgroundTintList(isActive ? activeTint : inactiveTint);
        btn.setTextColor(isActive ? activeText : inactiveText);
        btn.setStrokeColor(isActive ? activeStroke : inactiveStroke);
    }

    private void showNodePropertiesDialog(StructuralModel.Node node, StructuralModel.Load load) {
        if (getContext() == null || !isAdded()) return;

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_assign_node_load, null);
        TextView tvBadge = dialogView.findViewById(R.id.tvDialogNodeBadge);
        TextView tvCoords = dialogView.findViewById(R.id.tvDialogNodeCoords);
        Spinner spSupport = dialogView.findViewById(R.id.spDialogSupport);
        TextInputEditText etFx = dialogView.findViewById(R.id.etDialogFx);
        TextInputEditText etFy = dialogView.findViewById(R.id.etDialogFy);

        tvBadge.setText(String.format(java.util.Locale.US, "Node #%d", node.id));
        tvCoords.setText(String.format(java.util.Locale.US, "Coordinates: X = %.2f m, Y = %.2f m", node.x, node.y));

        String[] supportOptions = new String[]{
                "FREE (Unconstrained)",
                "FIXED (Encaster SPC 1-6)",
                "PINNED (Hinged SPC 1-3)",
                "ROLLER (Sliding Base SPC 2)"
        };
        ArrayAdapter<String> supportAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_compact, supportOptions);
        supportAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown_compact);
        spSupport.setAdapter(supportAdapter);
        if (node.supportType == StructuralModel.SupportType.FIXED) {
            spSupport.setSelection(1);
        } else if (node.supportType == StructuralModel.SupportType.PINNED) {
            spSupport.setSelection(2);
        } else if (node.supportType == StructuralModel.SupportType.ROLLER) {
            spSupport.setSelection(3);
        } else {
            spSupport.setSelection(0);
        }

        double curFx = (load != null) ? (load.fx / 1000.0) : 0.0;
        double curFy = (load != null) ? (load.fy / 1000.0) : 0.0;
        if (Math.abs(curFx) > 1e-4) etFx.setText(String.format(java.util.Locale.US, "%.2f", curFx));
        if (Math.abs(curFy) > 1e-4) etFy.setText(String.format(java.util.Locale.US, "%.2f", curFy));

        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton(R.string.apply, (dialog, which) -> {
                    int supIdx = spSupport.getSelectedItemPosition();
                    if (supIdx == 1) {
                        node.supportType = StructuralModel.SupportType.FIXED;
                    } else if (supIdx == 2) {
                        node.supportType = StructuralModel.SupportType.PINNED;
                    } else if (supIdx == 3) {
                        node.supportType = StructuralModel.SupportType.ROLLER;
                    } else {
                        node.supportType = StructuralModel.SupportType.FREE;
                    }

                    double fx = 0.0, fy = 0.0;
                    try {
                        if (etFx.getText() != null && etFx.getText().length() > 0) {
                            fx = Double.parseDouble(etFx.getText().toString());
                        }
                    } catch (Exception ignored) {}
                    try {
                        if (etFy.getText() != null && etFy.getText().length() > 0) {
                            fy = Double.parseDouble(etFy.getText().toString());
                        }
                    } catch (Exception ignored) {}

                    binding.gridEditorView.assignPointLoad(node.id, fx * 1000.0, fy * 1000.0, 0.0);
                    Toast.makeText(requireContext(), String.format(java.util.Locale.US, "Node #%d: Restraints & Loads updated (Fx=%.1f kN, Fy=%.1f kN)", node.id, fx, fy), Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Clear Load", (dialog, which) -> {
                    binding.gridEditorView.assignPointLoad(node.id, 0.0, 0.0, 0.0);
                    Toast.makeText(requireContext(), "Applied load cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showElementPropertiesDialog(StructuralModel.Element element) {
        if (getContext() == null || !isAdded()) return;

        double length = binding.gridEditorView.getElementLength(element);

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_assign_member_properties, null);
        TextView tvBadge = dialogView.findViewById(R.id.tvDialogMemberBadge);
        TextView tvSpan = dialogView.findViewById(R.id.tvDialogMemberSpan);
        Spinner spSec = dialogView.findViewById(R.id.spDialogSection);
        Spinner spMat = dialogView.findViewById(R.id.spDialogMaterial);
        TextInputEditText etDistLoad = dialogView.findViewById(R.id.etDialogDistLoad);
        TextInputEditText etPtLoad = dialogView.findViewById(R.id.etDialogPointLoad);

        tvBadge.setText(String.format(java.util.Locale.US, "Member #%d", element.id));
        tvSpan.setText(String.format(java.util.Locale.US, "Node %d ➔ Node %d | Span Length L = %.2f m", element.node1Id, element.node2Id, length));

        List<String> secNames = new ArrayList<>();
        if (sectionLibrary != null && !sectionLibrary.getSections().isEmpty()) {
            for (SectionLibrary.Section s : sectionLibrary.getSections()) secNames.add(s.name);
        } else {
            secNames.addAll(Arrays.asList("HEB200", "HEB300", "IPE200", "IPE300", "IPE400", "W8x31", "W12x50", "L100x10", "Rect 300x400", "Rect 200x300"));
        }
        ArrayAdapter<String> secAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_compact, secNames);
        secAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown_compact);
        spSec.setAdapter(secAdapter);
        if (element.sectionName != null) {
            int idx = secNames.indexOf(element.sectionName);
            if (idx >= 0) spSec.setSelection(idx);
        }

        List<String> matNames = new ArrayList<>();
        if (materialDatabase != null && !materialDatabase.getMaterials().isEmpty()) {
            for (MaterialDatabase.Material m : materialDatabase.getMaterials()) matNames.add(m.name);
        } else {
            matNames.addAll(Arrays.asList("Structural Steel A36", "Structural Steel A572 Gr50", "Normal Weight Concrete 25MPa", "Normal Weight Concrete 30MPa", "Aluminum 6061-T6"));
        }
        ArrayAdapter<String> matAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_compact, matNames);
        matAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown_compact);
        spMat.setAdapter(matAdapter);
        if (element.materialName != null) {
            int idx = matNames.indexOf(element.materialName);
            if (idx >= 0) spMat.setSelection(idx);
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton(R.string.apply, (dialog, which) -> {
                    if (spSec.getSelectedItem() != null) {
                        element.sectionName = spSec.getSelectedItem().toString();
                    }
                    if (spMat.getSelectedItem() != null) {
                        element.materialName = spMat.getSelectedItem().toString();
                    }

                    double w = 0.0, p = 0.0;
                    try {
                        if (etDistLoad.getText() != null && etDistLoad.getText().length() > 0) {
                            w = Double.parseDouble(etDistLoad.getText().toString());
                        }
                    } catch (Exception ignored) {}
                    try {
                        if (etPtLoad.getText() != null && etPtLoad.getText().length() > 0) {
                            p = Double.parseDouble(etPtLoad.getText().toString());
                        }
                    } catch (Exception ignored) {}

                    if (Math.abs(w) > 1e-4 || Math.abs(p) > 1e-4) {
                        binding.gridEditorView.assignDistributedLoadToElement(element, w, p);
                        Toast.makeText(requireContext(), String.format(java.util.Locale.US, "Member #%d: Properties & Tributary Loads assigned (w=%.1f kN/m, P=%.1f kN)", element.id, w, p), Toast.LENGTH_SHORT).show();
                    } else {
                        binding.gridEditorView.invalidate();
                        Toast.makeText(requireContext(), "Member properties updated", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
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
        final File workDir = new File(ctx.getFilesDir(), "structural_analysis");
        if (!workDir.exists()) workDir.mkdirs();
        final File reportFile = new File(workDir, "Structural_Report.pdf");
        
        if (currentModel == null || currentResult == null) {
            Toast.makeText(ctx, R.string.toast_run_analysis_first, Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            boolean exported = false;
            try {
                com.diamon.civil.structural.export.PDFReportGenerator generator = new com.diamon.civil.structural.export.PDFReportGenerator();
                boolean success = generator.generateReport(ctx, currentModel, currentResult, "Structural Frame Analysis", "Structural Engineer", reportFile);

                if (success && reportFile.exists()) {
                    com.diamon.civil.core.export.ExportManager manager = new com.diamon.civil.core.export.ExportManager(ctx);
                    exported = manager.exportToDownloads(reportFile, "structural_analysis");
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

    private void runAnalysis() {
        if (getContext() == null || binding == null) return;
        
        if (binding.spinnerStructureType.getSelectedItem() == null) {
            Toast.makeText(getContext(), R.string.toast_select_structure_type, Toast.LENGTH_SHORT).show();
            return;
        }

        
        final String structureType = binding.spinnerStructureType.getSelectedItem().toString();
        
        String matName = binding.spinnerMaterialStructural.getSelectedItem() != null ? binding.spinnerMaterialStructural.getSelectedItem().toString() : "Steel";
        String secName = binding.spinnerSectionStructural.getSelectedItem() != null ? binding.spinnerSectionStructural.getSelectedItem().toString() : "HEB200";


        StructuralModel uiModel = binding.gridEditorView != null ?
                binding.gridEditorView.getStructuralModel() : new StructuralModel();

        if (uiModel.nodes.isEmpty() || (uiModel.elements.isEmpty() && uiModel.panels.isEmpty())) {
            Toast.makeText(getContext(), R.string.toast_define_nodes_first, Toast.LENGTH_SHORT).show();
            return;
        }

        binding.pbStructural.setVisibility(View.VISIBLE);
        binding.btnSolveStructural.setEnabled(false);
        binding.btnSolveStructural.setText(R.string.btn_solving_fea);
        logger.info("Starting Structural Analysis...");

        final android.content.Context appContext = getContext().getApplicationContext();
        final File workDir = new File(getContext().getFilesDir(), "structural_analysis");
        if (!workDir.exists()) workDir.mkdirs();

        double loadVal = 10000.0;
        try {
            if (binding.etLoadValue.getText() != null) {
                loadVal = Double.parseDouble(binding.etLoadValue.getText().toString());
            }
        } catch (NumberFormatException e) {
            // keep default
        }
        final double loadValue = loadVal;

        executor.execute(() -> {
            NativeFeaCore core = new NativeFeaCore();
            long modelPtr = 0;
            try {
                modelPtr = core.createModel();
                StructuralModel model = uiModel;
                validateModel(model);
                String jsonModel = modelToJson(model, structureType, matName, secName, loadValue);
                core.modelFromJson(modelPtr, jsonModel);
                
                logger.info("Assembling CalculiX Input INP...");
                String inpContent = core.modelToInp(modelPtr);
                File inpFile = new File(workDir, "structural_job.inp");
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(inpFile)) {
                    fos.write(inpContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                
                logger.info("Executing CalculiX Solver ccx...");
                if (calculixExecutor == null) {
                    calculixExecutor = new CalculixExecutor(appContext, workDir);
                }
                String result = calculixExecutor.executeCalculix("structural_job");
                logger.log(result);

                File datFile = new File(workDir, "structural_job.dat");
                StructuralBeamDatParser.ParseResult parseResult = null;
                if (datFile.exists()) {
                    StructuralBeamDatParser.ParseResult datResult = datParser.parse(datFile);
                    if (datResult != null && datResult.displacements != null && !datResult.displacements.isEmpty()) {
                        parseResult = datResult;
                    }
                }

                // Analyze with FrameAnalysisEngine for direct stiffness verification / fallback
                com.diamon.civil.structural.engine.FrameAnalysisEngine.AnalysisOutput engineOutput =
                        com.diamon.civil.structural.engine.FrameAnalysisEngine.analyze(model);

                if (parseResult == null || parseResult.displacements == null || parseResult.displacements.isEmpty()) {
                    parseResult = engineOutput.parseResult;
                } else {
                    // If CalculiX datResult has displacements but needs supplemental frame member forces
                    if ((parseResult.forces == null || parseResult.forces.isEmpty()) &&
                            engineOutput.parseResult != null && engineOutput.parseResult.forces != null && !engineOutput.parseResult.forces.isEmpty()) {
                        parseResult.forces = engineOutput.parseResult.forces;
                        parseResult.recalculateMaxForces();
                    }
                }

                currentModel = model;
                currentResult = parseResult;
                binding.frameGLView.setModel(model, true);
                calculateVBOs(model, parseResult);

                android.app.Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        if (binding != null) {
                            binding.pbStructural.setVisibility(View.GONE);
                            binding.btnSolveStructural.setEnabled(true);
                            binding.btnSolveStructural.setText(R.string.btn_run_fea_solver);
                            Toast.makeText(appContext, R.string.toast_simulation_complete, Toast.LENGTH_SHORT).show();
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
                            binding.btnSolveStructural.setText(R.string.btn_run_fea_solver);
                        }
                    });
                }
            } finally {
                if (modelPtr != 0) {
                    try {
                        core.deleteModel(modelPtr);
                    } catch (Throwable error) {
                        logger.error("Could not release native model: " + error.getMessage());
                    }
                }
            }
        });
    }

    private String cleanElsetName(String name) {
        if (name == null || name.trim().isEmpty()) return "DEF";
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String modelToJson(StructuralModel model, String structureType, String matName, String secName, double loadValue) {
        StringBuilder sb = new StringBuilder();
        sb.append("{ \"nodes\": [");
        
        for (int i = 0; i < model.nodes.size(); i++) {
            StructuralModel.Node n = model.nodes.get(i);
            sb.append(String.format(java.util.Locale.US, "{\"id\":%d,\"x\":%f,\"y\":%f,\"z\":%f}", n.id, n.x, n.y, n.z));
            if (i < model.nodes.size() - 1) sb.append(",");
        }
        
        Map<String, String> elementElsetMap = new HashMap<>();
        Map<String, MaterialDatabase.Material> usedMaterials = new LinkedHashMap<>();
        Map<String, double[]> usedSections = new LinkedHashMap<>();
        Map<String, String> sectionMatMap = new HashMap<>();

        for (int i = 0; i < model.elements.size(); i++) {
            StructuralModel.Element e = model.elements.get(i);
            String eSec = (e.sectionName != null && !e.sectionName.isEmpty()) ? e.sectionName : secName;
            String eMat = (e.materialName != null && !e.materialName.isEmpty()) ? e.materialName : matName;

            String secKey = cleanElsetName(eSec) + "_" + cleanElsetName(eMat);
            String elsetName = "ES_" + secKey;
            elementElsetMap.put(String.valueOf(e.id), elsetName);

            if (!usedMaterials.containsKey(eMat)) {
                MaterialDatabase.Material m = materialDatabase != null ? materialDatabase.getMaterialByName(eMat) : null;
                usedMaterials.put(eMat, m);
            }

            if (!usedSections.containsKey(elsetName)) {
                SectionLibrary.Section s = sectionLibrary != null ? sectionLibrary.getSectionByName(eSec) : null;
                double b = (s != null && s.b > 0 ? s.b : 200.0) / 1000.0;
                double h;
                if (s != null && s.Iy > 0 && (s.type == null || !s.type.toLowerCase(java.util.Locale.US).contains("rect"))) {
                    // Equivalent height to match true strong-axis inertia Iy (in mm4): I = (b * h^3) / 12 => h = (12 * I / b)^(1/3)
                    double I_m4 = s.Iy / 1.0e12; // mm4 to m4
                    h = Math.cbrt(12.0 * I_m4 / b);
                } else {
                    h = (s != null && s.h > 0 ? s.h : 200.0) / 1000.0;
                }
                usedSections.put(elsetName, new double[]{b, h});
                sectionMatMap.put(elsetName, eMat);
            }
        }

        for (int i = 0; i < model.panels.size(); i++) {
            StructuralModel.Panel p = model.panels.get(i);
            String pMat = (p.materialName != null && !p.materialName.isEmpty()) ? p.materialName : matName;
            if (!usedMaterials.containsKey(pMat)) {
                MaterialDatabase.Material m = materialDatabase != null ? materialDatabase.getMaterialByName(pMat) : null;
                usedMaterials.put(pMat, m);
            }
        }

        if (usedMaterials.isEmpty()) {
            MaterialDatabase.Material m = materialDatabase != null ? materialDatabase.getMaterialByName(matName) : null;
            usedMaterials.put(matName, m);
        }

        sb.append("], \"elements\": [");
        boolean firstElement = true;
        for (int i = 0; i < model.elements.size(); i++) {
            StructuralModel.Element e = model.elements.get(i);
            if (!firstElement) sb.append(",");
            String elset = elementElsetMap.get(String.valueOf(e.id));
            if (elset == null) elset = "Eall";
            sb.append(String.format(java.util.Locale.US, "{\"id\":%d,\"type\":\"B31\",\"elset\":\"%s\",\"nodes\":[%d,%d]}", e.id, elset, e.node1Id, e.node2Id));
            firstElement = false;
        }

        // Add 2D Planar Panels (Shells/Plates/Slabs/Shear Walls)
        for (int i = 0; i < model.panels.size(); i++) {
            StructuralModel.Panel p = model.panels.get(i);
            if (!firstElement) sb.append(",");
            sb.append(String.format(java.util.Locale.US, "{\"id\":%d,\"type\":\"%s\",\"elset\":\"Eslab%d\",\"nodes\":[", p.id, p.elementType, p.id));
            for (int k = 0; k < p.nodeIds.size(); k++) {
                sb.append(p.nodeIds.get(k));
                if (k < p.nodeIds.size() - 1) sb.append(",");
            }
            sb.append("]}");
            firstElement = false;
        }

        sb.append("], \"materials\": [");
        boolean firstMat = true;
        for (Map.Entry<String, MaterialDatabase.Material> entry : usedMaterials.entrySet()) {
            String mName = entry.getKey();
            MaterialDatabase.Material m = entry.getValue();
            double E = (m != null ? m.E : 210000.0) * 1e6; // Convert MPa to Pa
            double nu = (m != null ? m.nu : 0.3);
            double rho = (m != null ? m.rho : 7850);
            if (!firstMat) sb.append(",");
            sb.append(String.format(java.util.Locale.US, "{\"name\":\"%s\",\"youngModulus\":%f,\"poissonRatio\":%f,\"density\":%f}", mName, E, nu, rho));
            firstMat = false;
        }
        sb.append("],");

        sb.append("\"sections\": [");
        boolean firstSection = true;
        for (Map.Entry<String, double[]> entry : usedSections.entrySet()) {
            String elset = entry.getKey();
            double[] dims = entry.getValue();
            String elsetMat = sectionMatMap.get(elset);
            if (elsetMat == null) elsetMat = matName;
            if (!firstSection) sb.append(",");
            sb.append(String.format(java.util.Locale.US, "{\"elset\":\"%s\",\"type\":\"BEAM\",\"material\":\"%s\",\"params\":[%f,%f]}", elset, elsetMat, dims[0], dims[1]));
            firstSection = false;
        }
        for (StructuralModel.Panel p : model.panels) {
            if (!firstSection) sb.append(",");
            String pMat = (p.materialName != null && !p.materialName.isEmpty()) ? p.materialName : matName;
            String secType = ("CPS4".equalsIgnoreCase(p.elementType) || "CPE4".equalsIgnoreCase(p.elementType)) ? "PLANE_STRESS" : "SHELL";
            sb.append(String.format(java.util.Locale.US, "{\"elset\":\"Eslab%d\",\"type\":\"%s\",\"material\":\"%s\",\"params\":[%f]}", p.id, secType, pMat, p.thickness));
            firstSection = false;
        }
        sb.append("],");
        
        sb.append("\"constraints\": [");
        boolean firstConstraint = true;
        boolean hasExplicitSupports = false;
        for (StructuralModel.Node node : model.nodes) {
            if (node.supportType != null && node.supportType != StructuralModel.SupportType.FREE) {
                hasExplicitSupports = true;
                break;
            }
        }

        if (hasExplicitSupports) {
            for (StructuralModel.Node node : model.nodes) {
                if (node.supportType == StructuralModel.SupportType.FIXED) {
                    if (!firstConstraint) sb.append(",");
                    sb.append("{\"nodeId\":").append(node.id).append(",\"dofs\":[1,2,3,4,5,6],\"value\":0}");
                    firstConstraint = false;
                } else if (node.supportType == StructuralModel.SupportType.PINNED) {
                    if (!firstConstraint) sb.append(",");
                    sb.append("{\"nodeId\":").append(node.id).append(",\"dofs\":[1,2,3,4,5],\"value\":0}");
                    firstConstraint = false;
                } else if (node.supportType == StructuralModel.SupportType.ROLLER) {
                    if (!firstConstraint) sb.append(",");
                    sb.append("{\"nodeId\":").append(node.id).append(",\"dofs\":[2,3,4,5],\"value\":0}");
                    firstConstraint = false;
                }
            }
        } else {
            // Fallback: Fix base nodes (lowest Y)
            double minY = Double.MAX_VALUE;
            for (StructuralModel.Node node : model.nodes) {
                if (node.y < minY) minY = node.y;
            }
            for (StructuralModel.Node node : model.nodes) {
                if (Math.abs(node.y - minY) < 0.001) {
                    if (!firstConstraint) sb.append(",");
                    sb.append("{\"nodeId\":").append(node.id).append(",\"dofs\":[1,2,3,4,5,6],\"value\":0}");
                    firstConstraint = false;
                }
            }
        }
        sb.append("],");
        
        // If user hasn't defined loads, apply intelligent default based on structural system:
        if (model.loads == null || model.loads.isEmpty()) {
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            for (StructuralModel.Node node : model.nodes) {
                if (node.y < minY) minY = node.y;
                if (node.y > maxY) maxY = node.y;
                if (node.x < minX) minX = node.x;
                if (node.x > maxX) maxX = node.x;
            }

            com.diamon.civil.structural.export.PDFReportGenerator.StructuralSystemType sysType =
                    com.diamon.civil.structural.export.PDFReportGenerator.classifyStructure(model);

            int loadedNodeId = model.nodes.get(0).id;
            double loadFx = 0.0;
            double loadFy = 0.0;

            if (sysType == com.diamon.civil.structural.export.PDFReportGenerator.StructuralSystemType.MULTI_STORY_FRAME ||
                sysType == com.diamon.civil.structural.export.PDFReportGenerator.StructuralSystemType.PORTAL_FRAME) {
                // Multi-level Frame: Lateral load at top-left
                double minTopX = Double.MAX_VALUE;
                for (StructuralModel.Node node : model.nodes) {
                    if (Math.abs(node.y - maxY) < 0.15 && node.x < minTopX) {
                        minTopX = node.x;
                        loadedNodeId = node.id;
                    }
                }
                loadFx = loadValue;
                loadFy = 0.0;
            } else if (sysType == com.diamon.civil.structural.export.PDFReportGenerator.StructuralSystemType.PLANE_TRUSS) {
                // Plane Truss / Bridge: Downward vertical load at center node
                double midX = (minX + maxX) / 2.0;
                double bestDist = Double.MAX_VALUE;
                for (StructuralModel.Node node : model.nodes) {
                    if (node.supportType == null || node.supportType == StructuralModel.SupportType.FREE) {
                        double dist = Math.abs(node.x - midX);
                        if (dist < bestDist) {
                            bestDist = dist;
                            loadedNodeId = node.id;
                        }
                    }
                }
                loadFx = 0.0;
                loadFy = -loadValue;
            } else {
                // Flat Horizontal Beam: Downward vertical load on an unsupported span location
                StructuralModel.Node freeNode = null;
                for (StructuralModel.Node node : model.nodes) {
                    if (node.supportType == null || node.supportType == StructuralModel.SupportType.FREE) {
                        if (freeNode == null || Math.abs(node.x - (minX + maxX) / 2.0) < Math.abs(freeNode.x - (minX + maxX) / 2.0)) {
                            freeNode = node;
                        }
                    }
                }

                if (freeNode != null) {
                    loadedNodeId = freeNode.id;
                } else {
                    loadedNodeId = model.nodes.size() > 0 ? (model.nodes.size() + 1) : 1;
                }
                loadFx = 0.0;
                loadFy = -loadValue;
            }
            
            model.loads = new java.util.ArrayList<>();
            model.loads.add(new StructuralModel.Load(loadedNodeId, loadFx, loadFy, 0));
        }

        sb.append("\"loads\": [");
        for (int i=0; i<model.loads.size(); i++) {
            StructuralModel.Load l = model.loads.get(i);
            sb.append(String.format(java.util.Locale.US, "{\"nodeId\":%d,\"fx\":%f,\"fy\":%f,\"fz\":%f}", l.nodeId, l.fx, l.fy, l.fz));
            if (i < model.loads.size() - 1) sb.append(",");
        }
        sb.append("]");
        
        sb.append("}");
        return sb.toString();
    }

    private void validateModel(StructuralModel model) {
        if (model.nodes.size() < 2) {
            throw new IllegalArgumentException("Define at least two valid nodes");
        }
        if (model.elements.isEmpty() && model.panels.isEmpty()) {
            throw new IllegalArgumentException("Define at least one valid element or panel");
        }
        java.util.HashSet<Integer> nodeIds = new java.util.HashSet<>();
        for (StructuralModel.Node node : model.nodes) {
            nodeIds.add(node.id);
        }
        for (StructuralModel.Element element : model.elements) {
            if (!nodeIds.contains(element.node1Id) || !nodeIds.contains(element.node2Id)) {
                throw new IllegalArgumentException("Element " + element.id + " references non-existent nodes");
            }
        }
        for (StructuralModel.Panel panel : model.panels) {
            if (panel.nodeIds != null) {
                for (int nid : panel.nodeIds) {
                    if (!nodeIds.contains(nid)) {
                        throw new IllegalArgumentException("Panel " + panel.id + " references non-existent node " + nid);
                    }
                }
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

    public enum DiagramMode {
        MOMENT_M33,
        SHEAR_V22,
        AXIAL_N
    }
    private DiagramMode currentDiagramMode = DiagramMode.MOMENT_M33;

    private void calculateVBOs(StructuralModel model, StructuralBeamDatParser.ParseResult res) {
        if (model == null || model.nodes.isEmpty() || (model.elements.isEmpty() && model.panels.isEmpty())) return;

        // 1. Calculate structural bounding box for adaptive scaling
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (StructuralModel.Node n : model.nodes) {
            if (n.x < minX) minX = n.x; if (n.x > maxX) maxX = n.x;
            if (n.y < minY) minY = n.y; if (n.y > maxY) maxY = n.y;
        }
        float span = (float) Math.max(Math.hypot(maxX - minX, maxY - minY), 2.0);

        // 2. Maps for displacements and forces
        java.util.Map<Integer, StructuralBeamDatParser.NodeDisplacement> dispMap = new java.util.HashMap<>();
        double maxU = 1e-7;
        if (res.displacements != null) {
            for (StructuralBeamDatParser.NodeDisplacement nd : res.displacements) {
                dispMap.put(nd.nodeId, nd);
                double mag = Math.sqrt(nd.ux * nd.ux + nd.uy * nd.uy + nd.uz * nd.uz);
                if (mag > maxU) maxU = mag;
            }
        }

        java.util.Map<Integer, StructuralBeamDatParser.SectionForces> forceMap = new java.util.HashMap<>();
        if (res.forces != null) {
            for (StructuralBeamDatParser.SectionForces f : res.forces) {
                forceMap.put(f.elementId, f);
            }
        }

        // Adaptive displacement scaling
        float targetMaxDisp = span * 0.10f; // 10% of frame span
        float dispScale = (float) ((targetMaxDisp / maxU) * (currentDispScale / 1000f));

        // 3. Interpolated Deformed Elastic Curve
        java.util.List<Float> defLines = new java.util.ArrayList<>();
        java.util.List<Float> defColors = new java.util.ArrayList<>();

        for (StructuralModel.Element elem : model.elements) {
            StructuralModel.Node n1 = findNodeInModel(model, elem.node1Id);
            StructuralModel.Node n2 = findNodeInModel(model, elem.node2Id);
            if (n1 == null || n2 == null) continue;

            StructuralBeamDatParser.NodeDisplacement d1 = dispMap.get(n1.id);
            StructuralBeamDatParser.NodeDisplacement d2 = dispMap.get(n2.id);

            double u1x = d1 != null ? d1.ux * dispScale : 0;
            double u1y = d1 != null ? d1.uy * dispScale : 0;
            double u2x = d2 != null ? d2.ux * dispScale : 0;
            double u2y = d2 != null ? d2.uy * dispScale : 0;

            int steps = 12;
            for (int i = 0; i < steps; i++) {
                double tA = (double) i / steps;
                double tB = (double) (i + 1) / steps;

                double pAx = n1.x * (1 - tA) + n2.x * tA + u1x * (1 - tA) + u2x * tA;
                double pAy = n1.y * (1 - tA) + n2.y * tA + u1y * (1 - tA) + u2y * tA;

                double pBx = n1.x * (1 - tB) + n2.x * tB + u1x * (1 - tB) + u2x * tB;
                double pBy = n1.y * (1 - tB) + n2.y * tB + u1y * (1 - tB) + u2y * tB;

                defLines.add((float) pAx); defLines.add((float) pAy); defLines.add(0f);
                defLines.add((float) pBx); defLines.add((float) pBy); defLines.add(0f);

                double magA = Math.hypot(u1x * (1 - tA) + u2x * tA, u1y * (1 - tA) + u2y * tA) / (dispScale * maxU);
                double magB = Math.hypot(u1x * (1 - tB) + u2x * tB, u1y * (1 - tB) + u2y * tB) / (dispScale * maxU);

                addColorToBuffer(defColors, (float) magA);
                addColorToBuffer(defColors, (float) magB);
            }
        }

        for (StructuralModel.Panel panel : model.panels) {
            if (panel.nodeIds == null || panel.nodeIds.size() < 3) continue;
            int count = panel.nodeIds.size();
            for (int k = 0; k < count; k++) {
                int nid1 = panel.nodeIds.get(k);
                int nid2 = panel.nodeIds.get((k + 1) % count);
                StructuralModel.Node n1 = findNodeInModel(model, nid1);
                StructuralModel.Node n2 = findNodeInModel(model, nid2);
                if (n1 == null || n2 == null) continue;

                StructuralBeamDatParser.NodeDisplacement d1 = dispMap.get(n1.id);
                StructuralBeamDatParser.NodeDisplacement d2 = dispMap.get(n2.id);

                double u1x = d1 != null ? d1.ux * dispScale : 0;
                double u1y = d1 != null ? d1.uy * dispScale : 0;
                double u2x = d2 != null ? d2.ux * dispScale : 0;
                double u2y = d2 != null ? d2.uy * dispScale : 0;

                double pAx = n1.x + u1x;
                double pAy = n1.y + u1y;
                double pBx = n2.x + u2x;
                double pBy = n2.y + u2y;

                defLines.add((float) pAx); defLines.add((float) pAy); defLines.add(0f);
                defLines.add((float) pBx); defLines.add((float) pBy); defLines.add(0f);

                double magA = Math.hypot(u1x, u1y) / (dispScale * maxU);
                double magB = Math.hypot(u2x, u2y) / (dispScale * maxU);

                addColorToBuffer(defColors, (float) magA);
                addColorToBuffer(defColors, (float) magB);
            }
        }

        // 4. Force Diagrams (Moment M, Shear V, Axial N)
        java.util.List<Float> diagFillPos = new java.util.ArrayList<>();
        java.util.List<Float> diagFillCol = new java.util.ArrayList<>();
        java.util.List<Float> diagLinePos = new java.util.ArrayList<>();
        java.util.List<Float> diagLineCol = new java.util.ArrayList<>();

        // Find max force for active mode
        double maxForceVal = 1e-4;
        for (StructuralModel.Element elem : model.elements) {
            StructuralBeamDatParser.SectionForces sf = forceMap.get(elem.id);
            if (sf != null) {
                double val = (currentDiagramMode == DiagramMode.MOMENT_M33) ? Math.max(Math.abs(sf.M1), Math.abs(sf.M2))
                           : (currentDiagramMode == DiagramMode.SHEAR_V22) ? Math.abs(sf.V2)
                           : Math.abs(sf.N);
                if (val > maxForceVal) maxForceVal = val;
            }
        }

        float targetDiagHeight = span * 0.16f; // 16% of span
        float diagScale = (float) (targetDiagHeight / maxForceVal);

        for (StructuralModel.Element elem : model.elements) {
            StructuralModel.Node n1 = findNodeInModel(model, elem.node1Id);
            StructuralModel.Node n2 = findNodeInModel(model, elem.node2Id);
            if (n1 == null || n2 == null) continue;

            double dx = n2.x - n1.x;
            double dy = n2.y - n1.y;
            double L = Math.hypot(dx, dy);
            if (L < 1e-5) continue;

            double nx = -dy / L;
            double ny = dx / L;

            StructuralBeamDatParser.SectionForces sf = forceMap.get(elem.id);
            double v1 = 0, v2 = 0;
            if (sf != null) {
                if (currentDiagramMode == DiagramMode.MOMENT_M33) {
                    v1 = sf.M1;
                    v2 = sf.M2;
                } else if (currentDiagramMode == DiagramMode.SHEAR_V22) {
                    v1 = sf.V2;
                    v2 = sf.V2;
                } else {
                    v1 = sf.N;
                    v2 = sf.N;
                }
            } else {
                v1 = 0;
                v2 = 0;
            }

            int subDiv = 10;
            for (int s = 0; s < subDiv; s++) {
                double t0 = (double) s / subDiv;
                double t1 = (double) (s + 1) / subDiv;

                double val0 = v1 * (1.0 - t0) + v2 * t0;
                double val1 = v1 * (1.0 - t1) + v2 * t1;

                double a0x = n1.x * (1 - t0) + n2.x * t0;
                double a0y = n1.y * (1 - t0) + n2.y * t0;
                double a1x = n1.x * (1 - t1) + n2.x * t1;
                double a1y = n1.y * (1 - t1) + n2.y * t1;

                // Check for zero crossing within this sub-segment
                if (val0 * val1 < 0 && Math.abs(val0 - val1) > 1e-10) {
                    // Calculate zero crossing fraction within sub-segment
                    double tCross = val0 / (val0 - val1);
                    double aCx = a0x * (1 - tCross) + a1x * tCross;
                    double aCy = a0y * (1 - tCross) + a1y * tCross;

                    double h0 = val0 * diagScale;
                    double top0x = a0x + nx * h0;
                    double top0y = a0y + ny * h0;

                    // First triangle: a0 -> top0 -> crossPoint (val0's sign)
                    addVertex(diagFillPos, a0x, a0y, 0);
                    addVertex(diagFillPos, top0x, top0y, 0);
                    addVertex(diagFillPos, aCx, aCy, 0);

                    boolean isPos0 = val0 >= 0;
                    float r0 = isPos0 ? 0.0f : 1.0f;
                    float g0 = isPos0 ? 0.85f : 0.35f;
                    float b0 = isPos0 ? 1.0f : 0.15f;
                    for (int k = 0; k < 3; k++) {
                        diagFillCol.add(r0); diagFillCol.add(g0); diagFillCol.add(b0); diagFillCol.add(0.50f);
                    }

                    double h1 = val1 * diagScale;
                    double top1x = a1x + nx * h1;
                    double top1y = a1y + ny * h1;

                    // Second triangle: crossPoint -> top1 -> a1 (val1's sign)
                    addVertex(diagFillPos, aCx, aCy, 0);
                    addVertex(diagFillPos, top1x, top1y, 0);
                    addVertex(diagFillPos, a1x, a1y, 0);

                    boolean isPos1 = val1 >= 0;
                    float r1 = isPos1 ? 0.0f : 1.0f;
                    float g1 = isPos1 ? 0.85f : 0.35f;
                    float b1 = isPos1 ? 1.0f : 0.15f;
                    for (int k = 0; k < 3; k++) {
                        diagFillCol.add(r1); diagFillCol.add(g1); diagFillCol.add(b1); diagFillCol.add(0.50f);
                    }

                    // Boundary lines for both segments
                    addVertex(diagLinePos, top0x, top0y, 0);
                    addVertex(diagLinePos, aCx, aCy, 0);
                    for (int k = 0; k < 2; k++) {
                        diagLineCol.add(r0); diagLineCol.add(g0); diagLineCol.add(b0); diagLineCol.add(1.0f);
                    }
                    addVertex(diagLinePos, aCx, aCy, 0);
                    addVertex(diagLinePos, top1x, top1y, 0);
                    for (int k = 0; k < 2; k++) {
                        diagLineCol.add(r1); diagLineCol.add(g1); diagLineCol.add(b1); diagLineCol.add(1.0f);
                    }
                } else {
                    // Normal case: no zero crossing
                    double h0 = val0 * diagScale;
                    double h1 = val1 * diagScale;

                    double top0x = a0x + nx * h0;
                    double top0y = a0y + ny * h0;
                    double top1x = a1x + nx * h1;
                    double top1y = a1y + ny * h1;

                    addVertex(diagFillPos, a0x, a0y, 0);
                    addVertex(diagFillPos, top0x, top0y, 0);
                    addVertex(diagFillPos, top1x, top1y, 0);

                    addVertex(diagFillPos, a0x, a0y, 0);
                    addVertex(diagFillPos, top1x, top1y, 0);
                    addVertex(diagFillPos, a1x, a1y, 0);

                    boolean isPos = val0 >= 0;
                    float r = isPos ? 0.0f : 1.0f;
                    float g = isPos ? 0.85f : 0.35f;
                    float b = isPos ? 1.0f : 0.15f;
                    float alpha = 0.50f;

                    for (int k = 0; k < 6; k++) {
                        diagFillCol.add(r); diagFillCol.add(g); diagFillCol.add(b); diagFillCol.add(alpha);
                    }

                    // Outer boundary line
                    addVertex(diagLinePos, top0x, top0y, 0);
                    addVertex(diagLinePos, top1x, top1y, 0);
                    for (int k = 0; k < 2; k++) {
                        diagLineCol.add(r); diagLineCol.add(g); diagLineCol.add(b); diagLineCol.add(1.0f);
                    }
                }

                // Vertical station hatch lines (every other sub-segment)
                if (s % 2 == 0) {
                    double hatch0 = (v1 * (1.0 - t0) + v2 * t0) * diagScale;
                    double hatchTopX = a0x + nx * hatch0;
                    double hatchTopY = a0y + ny * hatch0;
                    addVertex(diagLinePos, a0x, a0y, 0);
                    addVertex(diagLinePos, hatchTopX, hatchTopY, 0);
                    boolean hatchPos = (v1 * (1.0 - t0) + v2 * t0) >= 0;
                    float hr = hatchPos ? 0.0f : 1.0f;
                    float hg = hatchPos ? 0.85f : 0.35f;
                    float hb = hatchPos ? 1.0f : 0.15f;
                    for (int k = 0; k < 2; k++) {
                        diagLineCol.add(hr); diagLineCol.add(hg); diagLineCol.add(hb); diagLineCol.add(0.70f);
                    }
                }
            }
        }

        // 5. 3D Support Base Glyphs (Realistic structural engineering symbols)
        java.util.List<Float> supLines = new java.util.ArrayList<>();
        java.util.List<Float> supColors = new java.util.ArrayList<>();
        float supSize = span * 0.045f;

        for (StructuralModel.Node n : model.nodes) {
            if (n.supportType == null || n.supportType == StructuralModel.SupportType.FREE) continue;

            int vertsBefore = supLines.size() / 3;
            float x = (float) n.x;
            float y = (float) n.y;
            float z = (float) n.z;

            if (n.supportType == StructuralModel.SupportType.FIXED) {
                // Ground plate
                supLines.add(x - supSize * 1.3f); supLines.add(y); supLines.add(z);
                supLines.add(x + supSize * 1.3f); supLines.add(y); supLines.add(z);

                // 45-degree Ground Anchor Hatch lines
                for (float t = -1.0f; t <= 1.0f; t += 0.4f) {
                    supLines.add(x + t * supSize); supLines.add(y); supLines.add(z);
                    supLines.add(x + (t - 0.35f) * supSize); supLines.add(y - supSize * 0.6f); supLines.add(z);
                }
            } else if (n.supportType == StructuralModel.SupportType.PINNED) {
                // Triangle pyramid apex at joint
                supLines.add(x); supLines.add(y); supLines.add(z);
                supLines.add(x - supSize); supLines.add(y - supSize * 1.2f); supLines.add(z);

                supLines.add(x); supLines.add(y); supLines.add(z);
                supLines.add(x + supSize); supLines.add(y - supSize * 1.2f); supLines.add(z);

                supLines.add(x - supSize); supLines.add(y - supSize * 1.2f); supLines.add(z);
                supLines.add(x + supSize); supLines.add(y - supSize * 1.2f); supLines.add(z);

                // Base plate & ground hatch
                supLines.add(x - supSize * 1.3f); supLines.add(y - supSize * 1.2f); supLines.add(z);
                supLines.add(x + supSize * 1.3f); supLines.add(y - supSize * 1.2f); supLines.add(z);

                for (float t = -1.0f; t <= 1.0f; t += 0.5f) {
                    supLines.add(x + t * supSize); supLines.add(y - supSize * 1.2f); supLines.add(z);
                    supLines.add(x + (t - 0.35f) * supSize); supLines.add(y - supSize * 1.7f); supLines.add(z);
                }
            } else if (n.supportType == StructuralModel.SupportType.ROLLER) {
                // Triangle pyramid apex at joint
                supLines.add(x); supLines.add(y); supLines.add(z);
                supLines.add(x - supSize); supLines.add(y - supSize * 1.0f); supLines.add(z);

                supLines.add(x); supLines.add(y); supLines.add(z);
                supLines.add(x + supSize); supLines.add(y - supSize * 1.0f); supLines.add(z);

                supLines.add(x - supSize); supLines.add(y - supSize * 1.0f); supLines.add(z);
                supLines.add(x + supSize); supLines.add(y - supSize * 1.0f); supLines.add(z);

                // Roller Wheels (crosses)
                float rY = y - supSize * 1.2f;
                float rRadius = supSize * 0.18f;
                // Wheel 1
                supLines.add(x - supSize * 0.5f); supLines.add(rY - rRadius); supLines.add(z);
                supLines.add(x - supSize * 0.5f); supLines.add(rY + rRadius); supLines.add(z);
                supLines.add(x - supSize * 0.5f - rRadius); supLines.add(rY); supLines.add(z);
                supLines.add(x - supSize * 0.5f + rRadius); supLines.add(rY); supLines.add(z);
                // Wheel 2
                supLines.add(x + supSize * 0.5f); supLines.add(rY - rRadius); supLines.add(z);
                supLines.add(x + supSize * 0.5f); supLines.add(rY + rRadius); supLines.add(z);
                supLines.add(x + supSize * 0.5f - rRadius); supLines.add(rY); supLines.add(z);
                supLines.add(x + supSize * 0.5f + rRadius); supLines.add(rY); supLines.add(z);

                // Ground Guide Rail under rollers
                float railY = rY - rRadius - supSize * 0.05f;
                supLines.add(x - supSize * 1.4f); supLines.add(railY); supLines.add(z);
                supLines.add(x + supSize * 1.4f); supLines.add(railY); supLines.add(z);

                // Ground hatch under rail
                for (float t = -1.1f; t <= 1.1f; t += 0.5f) {
                    supLines.add(x + t * supSize); supLines.add(railY); supLines.add(z);
                    supLines.add(x + (t - 0.35f) * supSize); supLines.add(railY - supSize * 0.5f); supLines.add(z);
                }
            }

            int vertsAdded = (supLines.size() / 3) - vertsBefore;
            for (int k = 0; k < vertsAdded; k++) {
                supColors.add(1.0f); supColors.add(0.65f); supColors.add(0.15f); supColors.add(1.0f); // Professional Golden-Amber support color
            }
        }

        // 6. 3D Load Arrows
        java.util.List<Float> loadLines = new java.util.ArrayList<>();
        java.util.List<Float> loadColors = new java.util.ArrayList<>();
        for (StructuralModel.Load load : model.loads) {
            StructuralModel.Node n = findNodeInModel(model, load.nodeId);
            if (n == null) continue;

            float fx = (float) load.fx;
            float fy = (float) load.fy;
            float fz = (float) load.fz;
            float mag = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
            if (mag < 1e-6) continue;

            int loadVertsBefore = loadLines.size() / 3;

            float arrowLen = span * 0.16f;
            float ux = fx / mag * arrowLen;
            float uy = fy / mag * arrowLen;
            float uz = fz / mag * arrowLen;

            loadLines.add((float) n.x - ux); loadLines.add((float) n.y - uy); loadLines.add((float) n.z - uz);
            loadLines.add((float) n.x); loadLines.add((float) n.y); loadLines.add((float) n.z);

            loadLines.add((float) n.x); loadLines.add((float) n.y); loadLines.add((float) n.z);
            loadLines.add((float) n.x - ux * 0.3f + uy * 0.2f); loadLines.add((float) n.y - uy * 0.3f - ux * 0.2f); loadLines.add((float) n.z);

            loadLines.add((float) n.x); loadLines.add((float) n.y); loadLines.add((float) n.z);
            loadLines.add((float) n.x - ux * 0.3f - uy * 0.2f); loadLines.add((float) n.y - uy * 0.3f + ux * 0.2f); loadLines.add((float) n.z);

            int loadVertsAdded = (loadLines.size() / 3) - loadVertsBefore;
            for (int k = 0; k < loadVertsAdded; k++) {
                loadColors.add(1.0f); loadColors.add(0.18f); loadColors.add(0.18f); loadColors.add(1.0f); // Vivid Crimson Red load arrow
            }
        }

        // Convert lists to arrays
        float[] defPosArr = toArray(defLines);
        float[] defColArr = toArray(defColors);
        float[] fillPosArr = toArray(diagFillPos);
        float[] fillColArr = toArray(diagFillCol);
        float[] linePosArr = toArray(diagLinePos);
        float[] lineColArr = toArray(diagLineCol);
        float[] supPosArr = toArray(supLines);
        float[] supColArr = toArray(supColors);
        float[] loadPosArr = toArray(loadLines);
        float[] loadColArr = toArray(loadColors);

        android.app.Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (binding != null) {
                    binding.frameGLView.setModel(model, false);
                    binding.frameGLView.setDeformedShape(defPosArr, defColArr);
                    binding.frameGLView.setDiagrams(fillPosArr, fillColArr, linePosArr, lineColArr);
                    binding.frameGLView.setSupports(supPosArr, supColArr);
                    binding.frameGLView.setLoads(loadPosArr, loadColArr);
                    binding.frameGLView.setShowUndeformed(true);
                    binding.frameGLView.setShowSupports(true);
                    binding.frameGLView.setShowLoads(true);
                }
            });
        }
    }

    private void addVertex(java.util.List<Float> list, double x, double y, double z) {
        list.add((float) x);
        list.add((float) y);
        list.add((float) z);
    }

    private void addColorToBuffer(java.util.List<Float> col, float t) {
        t = Math.max(0f, Math.min(1f, t));
        if (t < 0.25f) {
            float f = t / 0.25f;
            col.add(0.1f); col.add(0.3f + 0.6f * f); col.add(1.0f); col.add(1.0f);
        } else if (t < 0.5f) {
            float f = (t - 0.25f) / 0.25f;
            col.add(0.0f); col.add(0.9f); col.add(1.0f - 0.7f * f); col.add(1.0f);
        } else if (t < 0.75f) {
            float f = (t - 0.5f) / 0.25f;
            col.add(1.0f * f); col.add(0.9f); col.add(0.1f); col.add(1.0f);
        } else {
            float f = (t - 0.75f) / 0.25f;
            col.add(1.0f); col.add(0.9f - 0.7f * f); col.add(0.1f); col.add(1.0f);
        }
    }

    private StructuralModel.Node findNodeInModel(StructuralModel model, int id) {
        for (StructuralModel.Node n : model.nodes) if (n.id == id) return n;
        return null;
    }

    private float[] toArray(java.util.List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    @Override
    public void onDestroyView() {
        executor.shutdownNow();
        super.onDestroyView();
        binding = null;
    }
}