package com.diamon.civil.structural.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.diamon.civil.structural.engine.StructuralModel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public class GridEditorView extends View {

    public enum Mode {
        DRAW,           // Draw beams / columns with magnetic snapping
        PAN,            // Move/Pan camera with single-finger drag without modifying entities
        MOVE_NODES,     // Select and drag nodes geometrically
        INSPECT,        // Select node/element/support/panel to inspect all technical properties
        SELECT_MOVE,    // Legacy alias for MOVE_NODES
        SUPPORT,        // Toggle support conditions (Fixed / Pinned / Roller / Free)
        LOAD,           // Assign / edit point loads (Fx, Fy)
        DELETE          // Tap node/element to delete
    }

    private Mode currentMode = Mode.DRAW;

    // Viewport transform (Pan & Zoom)
    private float scale = 70f; // pixels per structural meter
    private float minScale = 30f;
    private float maxScale = 200f;
    private float offsetX = 80f; // origin X offset in pixels
    private float offsetY = 80f; // origin Y offset in pixels from bottom

    // Touch & Gesture handling
    private ScaleGestureDetector scaleDetector;
    private boolean isMultiTouch = false;
    private float lastTouchX, lastTouchY;
    private float prevMidX, prevMidY;

    // Drawing state
    private List<StructuralModel.Node> nodes = new ArrayList<>();
    private List<StructuralModel.Element> elements = new ArrayList<>();
    private List<StructuralModel.Panel> panels = new ArrayList<>();
    private List<StructuralModel.Load> loads = new ArrayList<>();
    private int nextNodeId = 1;
    private int nextElementId = 1;
    private int nextPanelId = 1;

    private String defaultSection = "HEB200";
    private String defaultMaterial = "Structural Steel A36";

    // Active interaction
    private StructuralModel.Node activeNode = null;
    private StructuralModel.Node hoveredNode = null;
    private StructuralModel.Element selectedElement = null;
    private StructuralModel.Node selectedNode = null;
    private StructuralModel.Panel selectedPanel = null;
    private boolean activeNodeIsPending = false;

    private float currentDragX, currentDragY;
    private boolean isDragging = false;
    private boolean hasMovedNodeInDrag = false;
    private float snappedGridX, snappedGridY;
    private boolean hasSnappedTarget = false;

    // Undo history
    private final Deque<StructuralModel> undoStack = new ArrayDeque<>();
    private static final int MAX_UNDO = 20;

    // Paints
    private Paint bgPaint;
    private Paint majorGridPaint;
    private Paint minorGridPaint;
    private Paint axisPaint;
    private Paint axisTextPaint;
    private Paint nodePaint;
    private Paint nodeFixedPaint;
    private Paint nodeSelectedPaint;
    private Paint elementPaint;
    private Paint activeElementPaint;
    private Paint selectedElementPaint;
    private Paint panelPaint;
    private Paint panelBorderPaint;
    private Paint supportPaint;
    private Paint supportHatchPaint;
    private Paint loadArrowPaint;
    private Paint loadTextPaint;
    private Paint textPaint;
    private Paint hudBgPaint;
    private Paint hudTextPaint;
    private Paint snapIndicatorPaint;
    private Paint badgeBgPaint;
    private Paint badgeBorderPaint;
    private Paint loadBadgeBgPaint;
    private Paint loadBadgeBorderPaint;
    private Paint guideLinePaint;

    public interface OnModelChangeListener {
        void onModelChanged(int nodeCount, int elementCount);
    }

    public interface OnNodeSelectedListener {
        void onNodeSelected(StructuralModel.Node node, StructuralModel.Load load);
    }

    public interface OnElementSelectedListener {
        void onElementSelected(StructuralModel.Element element);
    }

    public interface OnComponentInspectedListener {
        void onComponentInspected(String infoText);
    }

    private OnModelChangeListener modelChangeListener;
    private OnNodeSelectedListener nodeSelectedListener;
    private OnElementSelectedListener elementSelectedListener;
    private OnComponentInspectedListener componentInspectedListener;

    public GridEditorView(Context context) {
        super(context);
        init(context);
    }

    public GridEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GridEditorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        bgPaint = new Paint();
        bgPaint.setColor(Color.parseColor("#0F141F"));

        minorGridPaint = new Paint();
        minorGridPaint.setColor(Color.parseColor("#1C2538"));
        minorGridPaint.setStrokeWidth(1.5f);

        majorGridPaint = new Paint();
        majorGridPaint.setColor(Color.parseColor("#2A3854"));
        majorGridPaint.setStrokeWidth(2.5f);

        axisPaint = new Paint();
        axisPaint.setColor(Color.parseColor("#4B6B94"));
        axisPaint.setStrokeWidth(3.5f);

        axisTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisTextPaint.setColor(Color.parseColor("#94A3B8"));
        axisTextPaint.setTextSize(18f);

        nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        nodePaint.setColor(Color.parseColor("#00E5FF"));
        nodePaint.setStyle(Paint.Style.FILL);

        nodeFixedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        nodeFixedPaint.setColor(Color.parseColor("#FF9100"));
        nodeFixedPaint.setStyle(Paint.Style.FILL);

        nodeSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        nodeSelectedPaint.setColor(Color.parseColor("#FFD600"));
        nodeSelectedPaint.setStyle(Paint.Style.STROKE);
        nodeSelectedPaint.setStrokeWidth(6f);

        elementPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        elementPaint.setColor(Color.parseColor("#80D8FF"));
        elementPaint.setStrokeWidth(9f);
        elementPaint.setStrokeCap(Paint.Cap.ROUND);

        activeElementPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        activeElementPaint.setColor(Color.parseColor("#00E5FF"));
        activeElementPaint.setStrokeWidth(6f);
        activeElementPaint.setPathEffect(new DashPathEffect(new float[]{14, 8}, 0));

        selectedElementPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedElementPaint.setColor(Color.parseColor("#FFD600"));
        selectedElementPaint.setStrokeWidth(12f);
        selectedElementPaint.setStrokeCap(Paint.Cap.ROUND);

        supportPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        supportPaint.setColor(Color.parseColor("#FFA726"));
        supportPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        supportPaint.setStrokeWidth(3f);

        supportHatchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        supportHatchPaint.setColor(Color.parseColor("#FFA726"));
        supportHatchPaint.setStrokeWidth(2f);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(18f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        hudBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hudBgPaint.setColor(Color.parseColor("#E60F172A"));

        hudTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hudTextPaint.setColor(Color.parseColor("#38BDF8"));
        hudTextPaint.setTextSize(19f);
        hudTextPaint.setFakeBoldText(true);

        loadArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        loadArrowPaint.setColor(Color.parseColor("#FF1744")); // Vibrant Crimson Red
        loadArrowPaint.setStrokeWidth(4.5f);
        loadArrowPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        loadTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        loadTextPaint.setColor(Color.parseColor("#FF8A80"));
        loadTextPaint.setTextSize(19f);
        loadTextPaint.setTextAlign(Paint.Align.CENTER);
        loadTextPaint.setFakeBoldText(true);

        badgeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgeBgPaint.setColor(Color.parseColor("#E60F141F"));
        badgeBgPaint.setStyle(Paint.Style.FILL);

        badgeBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgeBorderPaint.setColor(Color.parseColor("#38BDF8"));
        badgeBorderPaint.setStyle(Paint.Style.STROKE);
        badgeBorderPaint.setStrokeWidth(1.2f);

        loadBadgeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        loadBadgeBgPaint.setColor(Color.parseColor("#E6180E14"));
        loadBadgeBgPaint.setStyle(Paint.Style.FILL);

        loadBadgeBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        loadBadgeBorderPaint.setColor(Color.parseColor("#FF1744"));
        loadBadgeBorderPaint.setStyle(Paint.Style.STROKE);
        loadBadgeBorderPaint.setStrokeWidth(1.5f);

        guideLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        guideLinePaint.setColor(Color.parseColor("#4438BDF8"));
        guideLinePaint.setStrokeWidth(1.5f);
        guideLinePaint.setPathEffect(new DashPathEffect(new float[]{8, 8}, 0));

        panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        panelPaint.setColor(Color.parseColor("#4000BCD4"));
        panelPaint.setStyle(Paint.Style.FILL);

        panelBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        panelBorderPaint.setColor(Color.parseColor("#00ACC1"));
        panelBorderPaint.setStyle(Paint.Style.STROKE);
        panelBorderPaint.setStrokeWidth(3f);
        panelBorderPaint.setPathEffect(new DashPathEffect(new float[]{12, 6}, 0));

        snapIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        snapIndicatorPaint.setColor(Color.parseColor("#00E676"));
        snapIndicatorPaint.setStyle(Paint.Style.STROKE);
        snapIndicatorPaint.setStrokeWidth(4f);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                float focusX = detector.getFocusX();
                float focusY = detector.getFocusY();
                
                float newScale = scale * scaleFactor;
                if (newScale < minScale) newScale = minScale;
                if (newScale > maxScale) newScale = maxScale;
                
                // Zoom toward focus point
                offsetX = focusX - (focusX - offsetX) * (newScale / scale);
                offsetY = (getHeight() - focusY) - ((getHeight() - focusY) - offsetY) * (newScale / scale);
                
                scale = newScale;
                invalidate();
                return true;
            }
        });

        // Initialize with default standard Portal Frame
        loadPresetPortalFrame(4.0, 3.0);
    }

    public void setDefaultSection(String section) {
        if (section != null && !section.isEmpty()) this.defaultSection = section;
    }

    public void setDefaultMaterial(String material) {
        if (material != null && !material.isEmpty()) this.defaultMaterial = material;
    }

    public String getDefaultSection() {
        return defaultSection;
    }

    public String getDefaultMaterial() {
        return defaultMaterial;
    }

    public void setOnModelChangeListener(OnModelChangeListener listener) {
        this.modelChangeListener = listener;
    }

    public void setOnNodeSelectedListener(OnNodeSelectedListener listener) {
        this.nodeSelectedListener = listener;
    }

    public void setOnElementSelectedListener(OnElementSelectedListener listener) {
        this.elementSelectedListener = listener;
    }

    public void setOnComponentInspectedListener(OnComponentInspectedListener listener) {
        this.componentInspectedListener = listener;
    }

    public String getDetailedComponentInfo() {
        if (selectedNode != null) {
            StructuralModel.Load ld = getLoadForNode(selectedNode.id);
            String loadStr = (ld != null && (Math.abs(ld.fx) > 1e-3 || Math.abs(ld.fy) > 1e-3 || Math.abs(ld.fz) > 1e-3))
                    ? String.format(Locale.US, " | Load: [Fx=%.1fkN, Fy=%.1fkN]", ld.fx / 1000.0, ld.fy / 1000.0)
                    : " | Load: None";
            return String.format(Locale.US, "NODE %d: Coords: (X=%.2fm, Y=%.2fm) | Support: %s%s",
                    selectedNode.id, selectedNode.x, selectedNode.y, selectedNode.supportType, loadStr);
        } else if (selectedElement != null) {
            StructuralModel.Node n1 = findNode(selectedElement.node1Id);
            StructuralModel.Node n2 = findNode(selectedElement.node2Id);
            double len = (n1 != null && n2 != null) ? Math.hypot(n2.x - n1.x, n2.y - n1.y) : 0.0;
            return String.format(Locale.US, "MEMBER %d: Span (N%d -> N%d) | Length: %.2fm | Profile: %s | Material: %s",
                    selectedElement.id, selectedElement.node1Id, selectedElement.node2Id, len,
                    selectedElement.sectionName != null ? selectedElement.sectionName : defaultSection,
                    selectedElement.materialName != null ? selectedElement.materialName : defaultMaterial);
        } else if (selectedPanel != null) {
            return String.format(Locale.US, "PANEL %d: Type: %s | Nodes: %s | Thickness: %.2fm | Material: %s",
                    selectedPanel.id, selectedPanel.elementType, selectedPanel.nodeIds.toString(), selectedPanel.thickness,
                    selectedPanel.materialName != null ? selectedPanel.materialName : defaultMaterial);
        }
        return "Select any node, member, or panel to inspect technical properties";
    }

    public void notifyComponentInspected() {
        String info = getDetailedComponentInfo();
        if (componentInspectedListener != null) {
            componentInspectedListener.onComponentInspected(info);
        }
    }

    private void notifyModelChange() {
        if (modelChangeListener != null) {
            modelChangeListener.onModelChanged(nodes.size(), elements.size());
        }
    }

    public void setMode(Mode mode) {
        this.currentMode = mode;
        this.activeNode = null;
        this.isDragging = false;
        notifyComponentInspected();
        invalidate();
    }

    public Mode getMode() {
        return currentMode;
    }

    public List<StructuralModel.Node> getNodes() {
        return nodes;
    }

    public List<StructuralModel.Element> getElements() {
        return elements;
    }

    public List<StructuralModel.Panel> getPanels() {
        return panels;
    }

    public List<StructuralModel.Load> getLoads() {
        return loads;
    }

    public void setLoads(List<StructuralModel.Load> newLoads) {
        saveSnapshot();
        loads.clear();
        if (newLoads != null) {
            for (StructuralModel.Load l : newLoads) {
                loads.add(new StructuralModel.Load(l.nodeId, l.fx, l.fy, l.fz));
            }
        }
        invalidate();
    }

    public StructuralModel getStructuralModel() {
        StructuralModel m = new StructuralModel();
        for (StructuralModel.Node n : nodes) m.nodes.add(n.copy());
        for (StructuralModel.Element e : elements) m.elements.add(e.copy());
        for (StructuralModel.Panel p : panels) m.panels.add(p.copy());
        for (StructuralModel.Load l : loads) m.loads.add(new StructuralModel.Load(l.nodeId, l.fx, l.fy, l.fz));
        return m;
    }

    public void setModel(StructuralModel model) {
        if (model == null) return;
        saveSnapshot();
        nodes.clear();
        elements.clear();
        panels.clear();
        loads.clear();
        if (model.nodes != null) {
            for (StructuralModel.Node n : model.nodes) nodes.add(n.copy());
        }
        if (model.elements != null) {
            for (StructuralModel.Element e : model.elements) elements.add(e.copy());
        }
        if (model.panels != null) {
            for (StructuralModel.Panel p : model.panels) panels.add(p.copy());
        }
        if (model.loads != null) {
            for (StructuralModel.Load l : model.loads) loads.add(new StructuralModel.Load(l.nodeId, l.fx, l.fy, l.fz));
        }
        recalcNextIds();
        notifyModelChange();
        invalidate();
    }

    public StructuralModel.Load getLoadForNode(int nodeId) {
        for (StructuralModel.Load l : loads) {
            if (l.nodeId == nodeId) return l;
        }
        return null;
    }

    public void assignPointLoad(int nodeId, double fx, double fy, double fz) {
        saveSnapshot();
        StructuralModel.Load existing = null;
        for (StructuralModel.Load l : loads) {
            if (l.nodeId == nodeId) {
                existing = l;
                break;
            }
        }
        if (Math.abs(fx) < 1e-4 && Math.abs(fy) < 1e-4 && Math.abs(fz) < 1e-4) {
            if (existing != null) loads.remove(existing);
        } else {
            if (existing != null) {
                existing.fx = fx;
                existing.fy = fy;
                existing.fz = fz;
            } else {
                loads.add(new StructuralModel.Load(nodeId, fx, fy, fz));
            }
        }
        invalidate();
    }

    public StructuralModel.Node getNodeById(int nodeId) {
        for (StructuralModel.Node n : nodes) {
            if (n.id == nodeId) return n;
        }
        return null;
    }

    public double getElementLength(StructuralModel.Element element) {
        if (element == null) return 0.0;
        StructuralModel.Node n1 = getNodeById(element.node1Id);
        StructuralModel.Node n2 = getNodeById(element.node2Id);
        if (n1 == null || n2 == null) return 0.0;
        double dx = n2.x - n1.x;
        double dy = n2.y - n1.y;
        double dz = n2.z - n1.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public void assignDistributedLoadToElement(StructuralModel.Element element, double w_kN_per_m, double pointLoad_kN) {
        if (element == null) return;
        StructuralModel.Node n1 = getNodeById(element.node1Id);
        StructuralModel.Node n2 = getNodeById(element.node2Id);
        if (n1 == null || n2 == null) return;

        saveSnapshot();
        double length = getElementLength(element);
        double totalDistForceN = (w_kN_per_m * length * 1000.0);
        double totalPointForceN = (pointLoad_kN * 1000.0);
        double halfForceN = (totalDistForceN + totalPointForceN) / 2.0;

        // Apply tributary vertical force to n1 and n2
        StructuralModel.Load l1 = getLoadForNode(n1.id);
        double curFy1 = (l1 != null) ? l1.fy : 0.0;
        double curFx1 = (l1 != null) ? l1.fx : 0.0;
        assignPointLoad(n1.id, curFx1, curFy1 + halfForceN, 0.0);

        StructuralModel.Load l2 = getLoadForNode(n2.id);
        double curFy2 = (l2 != null) ? l2.fy : 0.0;
        double curFx2 = (l2 != null) ? l2.fx : 0.0;
        assignPointLoad(n2.id, curFx2, curFy2 + halfForceN, 0.0);

        invalidate();
    }

    public void removeLoadForNode(int nodeId) {
        saveSnapshot();
        loads.removeIf(l -> l.nodeId == nodeId);
        invalidate();
    }

    public void setModel(List<StructuralModel.Node> newNodes, List<StructuralModel.Element> newElements) {
        setModel(newNodes, newElements, null);
    }

    public void setModel(List<StructuralModel.Node> newNodes, List<StructuralModel.Element> newElements, List<StructuralModel.Panel> newPanels) {
        saveSnapshot();
        nodes.clear();
        elements.clear();
        panels.clear();
        loads.clear();
        if (newNodes != null) {
            for (StructuralModel.Node n : newNodes) nodes.add(n.copy());
        }
        if (newElements != null) {
            for (StructuralModel.Element e : newElements) elements.add(e.copy());
        }
        if (newPanels != null) {
            for (StructuralModel.Panel p : newPanels) panels.add(p.copy());
        }
        recalcNextIds();
        notifyModelChange();
        invalidate();
    }

    public void clear() {
        saveSnapshot();
        nodes.clear();
        elements.clear();
        panels.clear();
        loads.clear();
        selectedNode = null;
        selectedElement = null;
        activeNode = null;
        nextNodeId = 1;
        nextElementId = 1;
        nextPanelId = 1;
        notifyModelChange();
        invalidate();
    }

    private void saveSnapshot() {
        StructuralModel snapshot = new StructuralModel();
        for (StructuralModel.Node n : nodes) snapshot.nodes.add(n.copy());
        for (StructuralModel.Element e : elements) snapshot.elements.add(e.copy());
        for (StructuralModel.Panel p : panels) snapshot.panels.add(p.copy());
        for (StructuralModel.Load l : loads) snapshot.loads.add(new StructuralModel.Load(l.nodeId, l.fx, l.fy, l.fz));
        undoStack.push(snapshot);
        if (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast();
        }
    }

    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        StructuralModel previous = undoStack.pop();
        nodes.clear();
        elements.clear();
        panels.clear();
        loads.clear();
        for (StructuralModel.Node n : previous.nodes) nodes.add(n.copy());
        for (StructuralModel.Element e : previous.elements) elements.add(e.copy());
        for (StructuralModel.Panel p : previous.panels) panels.add(p.copy());
        for (StructuralModel.Load l : previous.loads) loads.add(new StructuralModel.Load(l.nodeId, l.fx, l.fy, l.fz));
        recalcNextIds();
        selectedNode = null;
        selectedElement = null;
        activeNode = null;
        notifyModelChange();
        invalidate();
        return true;
    }

    private void recalcNextIds() {
        int maxN = 0;
        for (StructuralModel.Node n : nodes) if (n.id > maxN) maxN = n.id;
        nextNodeId = maxN + 1;

        int maxE = 0;
        for (StructuralModel.Element e : elements) if (e.id > maxE) maxE = e.id;
        nextElementId = maxE + 1;

        int maxP = 0;
        for (StructuralModel.Panel p : panels) if (p.id > maxP) maxP = p.id;
        nextPanelId = maxP + 1;
    }

    // ==========================================
    // STRUCTURAL PRESETS (One-touch generation)
    // ==========================================

    public void loadPresetPortalFrame(double span, double height) {
        saveSnapshot();
        nodes.clear();
        elements.clear();
        panels.clear();
        loads.clear();

        // Node 1: Base left (Fixed)
        nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        // Node 2: Top left (Free)
        nodes.add(new StructuralModel.Node(2, 0.0, height, 0.0, StructuralModel.SupportType.FREE));
        // Node 3: Top right (Free)
        nodes.add(new StructuralModel.Node(3, span, height, 0.0, StructuralModel.SupportType.FREE));
        // Node 4: Base right (Fixed)
        nodes.add(new StructuralModel.Node(4, span, 0.0, 0.0, StructuralModel.SupportType.FIXED));

        // Elements
        elements.add(new StructuralModel.Element(1, 1, 2, "HEB200", "Steel"));
        elements.add(new StructuralModel.Element(2, 2, 3, "IPE300", "Steel"));
        elements.add(new StructuralModel.Element(3, 4, 3, "HEB200", "Steel"));

        // 10 kN lateral load at top left (Node 2)
        loads.add(new StructuralModel.Load(2, 10000.0, 0.0, 0.0));

        recalcNextIds();
        notifyModelChange();
        invalidate();
    }

    public void loadPresetTwoBayFrame(double span, double height) {
        saveSnapshot();
        nodes.clear();
        elements.clear();
        panels.clear();
        loads.clear();

        // Base nodes
        nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        nodes.add(new StructuralModel.Node(2, span, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        nodes.add(new StructuralModel.Node(3, span * 2, 0.0, 0.0, StructuralModel.SupportType.FIXED));

        // Top nodes
        nodes.add(new StructuralModel.Node(4, 0.0, height, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(5, span, height, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(6, span * 2, height, 0.0, StructuralModel.SupportType.FREE));

        // Columns
        elements.add(new StructuralModel.Element(1, 1, 4, "HEB200", "Steel"));
        elements.add(new StructuralModel.Element(2, 2, 5, "HEB200", "Steel"));
        elements.add(new StructuralModel.Element(3, 3, 6, "HEB200", "Steel"));

        // Beams
        elements.add(new StructuralModel.Element(4, 4, 5, "IPE300", "Steel"));
        elements.add(new StructuralModel.Element(5, 5, 6, "IPE300", "Steel"));

        // 30 kN gravity load at center node 5
        loads.add(new StructuralModel.Load(5, 0.0, -30000.0, 0.0));

        recalcNextIds();
        notifyModelChange();
        invalidate();
    }

    public void loadPresetContinuousBeam(double span) {
        saveSnapshot();
        nodes.clear();
        elements.clear();
        panels.clear();
        loads.clear();

        nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        nodes.add(new StructuralModel.Node(2, span, 0.0, 0.0, StructuralModel.SupportType.ROLLER));
        nodes.add(new StructuralModel.Node(3, span * 2, 0.0, 0.0, StructuralModel.SupportType.ROLLER));

        elements.add(new StructuralModel.Element(1, 1, 2, "IPE300", "Steel"));
        elements.add(new StructuralModel.Element(2, 2, 3, "IPE300", "Steel"));

        // 20 kN downward load at interior support/point
        loads.add(new StructuralModel.Load(2, 0.0, -20000.0, 0.0));

        recalcNextIds();
        notifyModelChange();
        invalidate();
    }

    public void loadPresetPitchedTruss(double span, double eaveHeight, double ridgeHeight) {
        saveSnapshot();
        nodes.clear();
        elements.clear();
        panels.clear();
        loads.clear();

        nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        nodes.add(new StructuralModel.Node(2, 0.0, eaveHeight, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(3, span / 2.0, ridgeHeight, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(4, span, eaveHeight, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(5, span, 0.0, 0.0, StructuralModel.SupportType.FIXED));

        elements.add(new StructuralModel.Element(1, 1, 2, "HEB200", "Steel"));
        elements.add(new StructuralModel.Element(2, 2, 3, "IPE300", "Steel"));
        elements.add(new StructuralModel.Element(3, 3, 4, "IPE300", "Steel"));
        elements.add(new StructuralModel.Element(4, 5, 4, "HEB200", "Steel"));
        elements.add(new StructuralModel.Element(5, 2, 4, "L100x10", "Steel")); // Tie beam

        // 25 kN apex load at ridge node 3
        loads.add(new StructuralModel.Load(3, 0.0, -25000.0, 0.0));

        recalcNextIds();
        notifyModelChange();
        invalidate();
    }

    public void loadPresetOverhangingBeam(double mainSpan, double overhangSpan) {
        saveSnapshot();
        nodes.clear();
        elements.clear();
        panels.clear();
        loads.clear();

        // Node 1: Left support (Pinned)
        nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        // Node 2: Intermediate support (Roller)
        nodes.add(new StructuralModel.Node(2, mainSpan, 0.0, 0.0, StructuralModel.SupportType.ROLLER));
        // Node 3: Overhanging cantilever tip (Free)
        nodes.add(new StructuralModel.Node(3, mainSpan + overhangSpan, 0.0, 0.0, StructuralModel.SupportType.FREE));

        // Main span element
        elements.add(new StructuralModel.Element(1, 1, 2, "IPE300", "Steel"));
        // Cantilever overhang element
        elements.add(new StructuralModel.Element(2, 2, 3, "IPE300", "Steel"));

        // 15 kN tip load at node 3
        loads.add(new StructuralModel.Load(3, 0.0, -15000.0, 0.0));

        recalcNextIds();
        notifyModelChange();
        invalidate();
    }

    public void loadPresetThreeStoryBuilding(double bayWidth, double storyHeight) {
        saveSnapshot();
        nodes.clear();
        elements.clear();
        panels.clear();
        loads.clear();

        // Base nodes (Fixed)
        nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        nodes.add(new StructuralModel.Node(2, bayWidth, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        nodes.add(new StructuralModel.Node(3, bayWidth * 2, 0.0, 0.0, StructuralModel.SupportType.FIXED));

        // Floor 1 nodes
        nodes.add(new StructuralModel.Node(4, 0.0, storyHeight, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(5, bayWidth, storyHeight, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(6, bayWidth * 2, storyHeight, 0.0, StructuralModel.SupportType.FREE));

        // Floor 2 nodes
        nodes.add(new StructuralModel.Node(7, 0.0, storyHeight * 2, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(8, bayWidth, storyHeight * 2, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(9, bayWidth * 2, storyHeight * 2, 0.0, StructuralModel.SupportType.FREE));

        // Floor 3 nodes
        nodes.add(new StructuralModel.Node(10, 0.0, storyHeight * 3, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(11, bayWidth, storyHeight * 3, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(12, bayWidth * 2, storyHeight * 3, 0.0, StructuralModel.SupportType.FREE));

        // Columns
        elements.add(new StructuralModel.Element(1, 1, 4, "HEB200", "Steel"));
        elements.add(new StructuralModel.Element(2, 2, 5, "HEB200", "Steel"));
        elements.add(new StructuralModel.Element(3, 3, 6, "HEB200", "Steel"));
        elements.add(new StructuralModel.Element(4, 4, 7, "HEB200", "Steel"));
        elements.add(new StructuralModel.Element(5, 5, 8, "HEB200", "Steel"));
        elements.add(new StructuralModel.Element(6, 6, 9, "HEB200", "Steel"));
        elements.add(new StructuralModel.Element(7, 7, 10, "HEB200", "Steel"));
        elements.add(new StructuralModel.Element(8, 8, 11, "HEB200", "Steel"));
        elements.add(new StructuralModel.Element(9, 9, 12, "HEB200", "Steel"));

        // Beams
        elements.add(new StructuralModel.Element(10, 4, 5, "IPE300", "Steel"));
        elements.add(new StructuralModel.Element(11, 5, 6, "IPE300", "Steel"));
        elements.add(new StructuralModel.Element(12, 7, 8, "IPE300", "Steel"));
        elements.add(new StructuralModel.Element(13, 8, 9, "IPE300", "Steel"));
        elements.add(new StructuralModel.Element(14, 10, 11, "IPE300", "Steel"));
        elements.add(new StructuralModel.Element(15, 11, 12, "IPE300", "Steel"));

        // Seismic lateral load pattern
        loads.add(new StructuralModel.Load(4, 15000.0, 0.0, 0.0));
        loads.add(new StructuralModel.Load(7, 30000.0, 0.0, 0.0));
        loads.add(new StructuralModel.Load(10, 45000.0, 0.0, 0.0));

        recalcNextIds();
        notifyModelChange();
        invalidate();
    }

    public void loadPresetWarrenTrussBridge(double span, double height) {
        saveSnapshot();
        nodes.clear();
        elements.clear();
        panels.clear();
        loads.clear();

        double dx = span / 4.0;
        // Bottom chord nodes
        nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        nodes.add(new StructuralModel.Node(2, dx, 0.0, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(3, dx * 2, 0.0, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(4, dx * 3, 0.0, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(5, span, 0.0, 0.0, StructuralModel.SupportType.ROLLER));

        // Top chord nodes
        nodes.add(new StructuralModel.Node(6, dx * 0.5, height, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(7, dx * 1.5, height, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(8, dx * 2.5, height, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(9, dx * 3.5, height, 0.0, StructuralModel.SupportType.FREE));

        // Bottom chord elements
        elements.add(new StructuralModel.Element(1, 1, 2, "L100x10", "Steel"));
        elements.add(new StructuralModel.Element(2, 2, 3, "L100x10", "Steel"));
        elements.add(new StructuralModel.Element(3, 3, 4, "L100x10", "Steel"));
        elements.add(new StructuralModel.Element(4, 4, 5, "L100x10", "Steel"));

        // Top chord elements
        elements.add(new StructuralModel.Element(5, 6, 7, "L100x10", "Steel"));
        elements.add(new StructuralModel.Element(6, 7, 8, "L100x10", "Steel"));
        elements.add(new StructuralModel.Element(7, 8, 9, "L100x10", "Steel"));

        // Diagonals & Verticals
        elements.add(new StructuralModel.Element(8, 1, 6, "L100x10", "Steel"));
        elements.add(new StructuralModel.Element(9, 6, 2, "L100x10", "Steel"));
        elements.add(new StructuralModel.Element(10, 2, 7, "L100x10", "Steel"));
        elements.add(new StructuralModel.Element(11, 7, 3, "L100x10", "Steel"));
        elements.add(new StructuralModel.Element(12, 3, 8, "L100x10", "Steel"));
        elements.add(new StructuralModel.Element(13, 8, 4, "L100x10", "Steel"));
        elements.add(new StructuralModel.Element(14, 4, 9, "L100x10", "Steel"));
        elements.add(new StructuralModel.Element(15, 9, 5, "L100x10", "Steel"));

        // Deck vehicular loads
        loads.add(new StructuralModel.Load(2, 0.0, -20000.0, 0.0));
        loads.add(new StructuralModel.Load(3, 0.0, -20000.0, 0.0));
        loads.add(new StructuralModel.Load(4, 0.0, -20000.0, 0.0));

        recalcNextIds();
        notifyModelChange();
        invalidate();
    }

    public void loadPresetConcreteContinuousBeam(double span1, double span2, double overhang) {
        saveSnapshot();
        nodes.clear();
        elements.clear();
        panels.clear();
        loads.clear();

        // Node 1: Left support (Pinned)
        nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        // Node 2: Intermediate span 1 point
        nodes.add(new StructuralModel.Node(2, span1 / 2.0, 0.0, 0.0, StructuralModel.SupportType.FREE));
        // Node 3: Interior support 1 (Roller)
        nodes.add(new StructuralModel.Node(3, span1, 0.0, 0.0, StructuralModel.SupportType.ROLLER));
        // Node 4: Intermediate span 2 point
        nodes.add(new StructuralModel.Node(4, span1 + span2 / 2.0, 0.0, 0.0, StructuralModel.SupportType.FREE));
        // Node 5: Interior support 2 (Roller)
        nodes.add(new StructuralModel.Node(5, span1 + span2, 0.0, 0.0, StructuralModel.SupportType.ROLLER));
        // Node 6: Cantilever tip (Free)
        nodes.add(new StructuralModel.Node(6, span1 + span2 + overhang, 0.0, 0.0, StructuralModel.SupportType.FREE));

        // Elements
        elements.add(new StructuralModel.Element(1, 1, 2, "Rect 300x400", "Normal Weight Concrete 25MPa"));
        elements.add(new StructuralModel.Element(2, 2, 3, "Rect 300x400", "Normal Weight Concrete 25MPa"));
        elements.add(new StructuralModel.Element(3, 3, 4, "Rect 300x400", "Normal Weight Concrete 25MPa"));
        elements.add(new StructuralModel.Element(4, 4, 5, "Rect 300x400", "Normal Weight Concrete 25MPa"));
        elements.add(new StructuralModel.Element(5, 5, 6, "Rect 300x400", "Normal Weight Concrete 25MPa"));

        // 30 kN load at overhang tip (Node 6)
        loads.add(new StructuralModel.Load(6, 0.0, -30000.0, 0.0));

        recalcNextIds();
        notifyModelChange();
        invalidate();
    }

    public void loadPresetConcreteSlabPlate(double width, double length, double thickness) {
        saveSnapshot();
        nodes.clear();
        elements.clear();
        panels.clear();
        loads.clear();

        // 4 corner nodes + 4 edge nodes + 1 center node (3x3 mesh)
        double w = width > 0 ? width : 4.0;
        double l = length > 0 ? length : 4.0;
        double t = thickness > 0 ? thickness : 0.15;

        nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        nodes.add(new StructuralModel.Node(2, w / 2.0, 0.0, 0.0, StructuralModel.SupportType.ROLLER));
        nodes.add(new StructuralModel.Node(3, w, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        nodes.add(new StructuralModel.Node(4, 0.0, l / 2.0, 0.0, StructuralModel.SupportType.ROLLER));
        nodes.add(new StructuralModel.Node(5, w / 2.0, l / 2.0, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(6, w, l / 2.0, 0.0, StructuralModel.SupportType.ROLLER));
        nodes.add(new StructuralModel.Node(7, 0.0, l, 0.0, StructuralModel.SupportType.PINNED));
        nodes.add(new StructuralModel.Node(8, w / 2.0, l, 0.0, StructuralModel.SupportType.ROLLER));
        nodes.add(new StructuralModel.Node(9, w, l, 0.0, StructuralModel.SupportType.PINNED));

        // 4 Quad Shell Elements (S4R)
        panels.add(new StructuralModel.Panel(1, java.util.Arrays.asList(1, 2, 5, 4), t, "Normal Weight Concrete 25MPa", "S4R"));
        panels.add(new StructuralModel.Panel(2, java.util.Arrays.asList(2, 3, 6, 5), t, "Normal Weight Concrete 25MPa", "S4R"));
        panels.add(new StructuralModel.Panel(3, java.util.Arrays.asList(4, 5, 8, 7), t, "Normal Weight Concrete 25MPa", "S4R"));
        panels.add(new StructuralModel.Panel(4, java.util.Arrays.asList(5, 6, 9, 8), t, "Normal Weight Concrete 25MPa", "S4R"));

        // Perimeter boundary beams
        elements.add(new StructuralModel.Element(1, 1, 2, "Rect 200x300", "Normal Weight Concrete 25MPa"));
        elements.add(new StructuralModel.Element(2, 2, 3, "Rect 200x300", "Normal Weight Concrete 25MPa"));
        elements.add(new StructuralModel.Element(3, 3, 6, "Rect 200x300", "Normal Weight Concrete 25MPa"));
        elements.add(new StructuralModel.Element(4, 6, 9, "Rect 200x300", "Normal Weight Concrete 25MPa"));
        elements.add(new StructuralModel.Element(5, 9, 8, "Rect 200x300", "Normal Weight Concrete 25MPa"));
        elements.add(new StructuralModel.Element(6, 8, 7, "Rect 200x300", "Normal Weight Concrete 25MPa"));
        elements.add(new StructuralModel.Element(7, 7, 4, "Rect 200x300", "Normal Weight Concrete 25MPa"));
        elements.add(new StructuralModel.Element(8, 4, 1, "Rect 200x300", "Normal Weight Concrete 25MPa"));

        // 40 kN center load at Node 5
        loads.add(new StructuralModel.Load(5, 0.0, 0.0, -40000.0));

        recalcNextIds();
        notifyModelChange();
        invalidate();
    }

    public void loadPresetShearWall(double width, double height, double thickness) {
        saveSnapshot();
        nodes.clear();
        elements.clear();
        panels.clear();
        loads.clear();

        double w = width > 0 ? width : 3.0;
        double h = height > 0 ? height : 3.0;
        double t = thickness > 0 ? thickness : 0.20;

        // Base nodes fixed
        nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        nodes.add(new StructuralModel.Node(2, w, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        // Top nodes free
        nodes.add(new StructuralModel.Node(3, w, h, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(4, 0.0, h, 0.0, StructuralModel.SupportType.FREE));

        // 1 Quad Plane Stress Panel (CPS4)
        panels.add(new StructuralModel.Panel(1, java.util.Arrays.asList(1, 2, 3, 4), t, "Normal Weight Concrete 25MPa", "CPS4"));

        // Boundary elements / Column stubs
        elements.add(new StructuralModel.Element(1, 1, 4, "Rect 300x400", "Normal Weight Concrete 25MPa"));
        elements.add(new StructuralModel.Element(2, 2, 3, "Rect 300x400", "Normal Weight Concrete 25MPa"));
        elements.add(new StructuralModel.Element(3, 4, 3, "Rect 300x400", "Normal Weight Concrete 25MPa"));

        // 50 kN lateral shear force at top Node 4
        loads.add(new StructuralModel.Load(4, 50000.0, 0.0, 0.0));

        recalcNextIds();
        notifyModelChange();
        invalidate();
    }

    public void loadPresetPrattTruss(double span, double height) {
        saveSnapshot();
        nodes.clear();
        elements.clear();
        panels.clear();
        loads.clear();

        double L = span > 0 ? span : 10.0;
        double H = height > 0 ? height : 2.5;
        double panelWidth = L / 4.0;

        // Bottom chord nodes (y = 0)
        nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.PINNED));
        nodes.add(new StructuralModel.Node(2, panelWidth, 0.0, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(3, panelWidth * 2, 0.0, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(4, panelWidth * 3, 0.0, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(5, L, 0.0, 0.0, StructuralModel.SupportType.ROLLER));

        // Top chord nodes (y = H)
        nodes.add(new StructuralModel.Node(6, panelWidth, H, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(7, panelWidth * 2, H, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(8, panelWidth * 3, H, 0.0, StructuralModel.SupportType.FREE));

        // Bottom chord members
        elements.add(new StructuralModel.Element(1, 1, 2, "L100x10", "Structural Steel A36"));
        elements.add(new StructuralModel.Element(2, 2, 3, "L100x10", "Structural Steel A36"));
        elements.add(new StructuralModel.Element(3, 3, 4, "L100x10", "Structural Steel A36"));
        elements.add(new StructuralModel.Element(4, 4, 5, "L100x10", "Structural Steel A36"));

        // Top chord members
        elements.add(new StructuralModel.Element(5, 6, 7, "L100x10", "Structural Steel A36"));
        elements.add(new StructuralModel.Element(6, 7, 8, "L100x10", "Structural Steel A36"));

        // End posts
        elements.add(new StructuralModel.Element(7, 1, 6, "L100x10", "Structural Steel A36"));
        elements.add(new StructuralModel.Element(8, 5, 8, "L100x10", "Structural Steel A36"));

        // Verticals
        elements.add(new StructuralModel.Element(9, 2, 6, "L100x10", "Structural Steel A36"));
        elements.add(new StructuralModel.Element(10, 3, 7, "L100x10", "Structural Steel A36"));
        elements.add(new StructuralModel.Element(11, 4, 8, "L100x10", "Structural Steel A36"));

        // Pratt Diagonals (tension struts slanting toward the center)
        elements.add(new StructuralModel.Element(12, 2, 7, "L100x10", "Structural Steel A36"));
        elements.add(new StructuralModel.Element(13, 4, 7, "L100x10", "Structural Steel A36"));

        // 50 kN gravity load at center node 3
        loads.add(new StructuralModel.Load(3, 0.0, -50000.0, 0.0));

        recalcNextIds();
        notifyModelChange();
        invalidate();
    }

    public void loadPresetCantileverBracket(double length, double height) {
        saveSnapshot();
        nodes.clear();
        elements.clear();
        panels.clear();
        loads.clear();

        double L = length > 0 ? length : 4.0;
        double H = height > 0 ? height : 3.0;

        // Wall support nodes (fixed/pinned along wall at x = 0)
        nodes.add(new StructuralModel.Node(1, 0.0, 0.0, 0.0, StructuralModel.SupportType.FIXED));
        nodes.add(new StructuralModel.Node(2, 0.0, H, 0.0, StructuralModel.SupportType.FIXED));

        // Cantilever projection nodes
        nodes.add(new StructuralModel.Node(3, L / 2.0, H, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(4, L, H, 0.0, StructuralModel.SupportType.FREE));
        nodes.add(new StructuralModel.Node(5, L / 2.0, 0.0, 0.0, StructuralModel.SupportType.FREE));

        // Top tension boom
        elements.add(new StructuralModel.Element(1, 2, 3, "W8x31", "Structural Steel A36"));
        elements.add(new StructuralModel.Element(2, 3, 4, "W8x31", "Structural Steel A36"));

        // Bottom compression strut
        elements.add(new StructuralModel.Element(3, 1, 5, "W8x31", "Structural Steel A36"));
        elements.add(new StructuralModel.Element(4, 5, 4, "W8x31", "Structural Steel A36"));

        // Vertical strut
        elements.add(new StructuralModel.Element(5, 5, 3, "W8x31", "Structural Steel A36"));

        // Diagonal brace
        elements.add(new StructuralModel.Element(6, 1, 3, "W8x31", "Structural Steel A36"));

        // 20 kN downward tip load at Node 4
        loads.add(new StructuralModel.Load(4, 0.0, -20000.0, 0.0));

        recalcNextIds();
        notifyModelChange();
        invalidate();
    }

    // ==========================================
    // RENDERING PIPELINE
    // ==========================================

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // 1. Background
        canvas.drawRect(0, 0, width, height, bgPaint);

        // 2. Metric Grid
        drawMetricGrid(canvas, width, height);

        // 3. 2D Planar Panels (Slabs / Shells / Walls)
        drawPanels(canvas, height);

        // 4. Elements (Beams / Columns) with dimension badges
        drawElements(canvas, height);

        // 5. Active candidate drag line & guidelines
        if (isDragging && activeNode != null) {
            float x1 = worldToScreenX(activeNode.x);
            float y1 = worldToScreenY(activeNode.y, height);
            float x2 = hasSnappedTarget ? worldToScreenX(snappedGridX) : currentDragX;
            float y2 = hasSnappedTarget ? worldToScreenY(snappedGridY, height) : currentDragY;

            // Orthogonal guide line across screen
            if (Math.abs(x1 - x2) < 2f) {
                canvas.drawLine(x1, 0, x1, height, guideLinePaint);
            } else if (Math.abs(y1 - y2) < 2f) {
                canvas.drawLine(0, y1, width, y1, guideLinePaint);
            }

            canvas.drawLine(x1, y1, x2, y2, activeElementPaint);

            // Dynamic Length & Delta HUD badge on active drag
            double dx = screenToWorldX(x2) - activeNode.x;
            double dy = screenToWorldY(y2, height) - activeNode.y;
            double len = Math.sqrt(dx * dx + dy * dy);
            float mx = (x1 + x2) / 2f;
            float my = (y1 + y2) / 2f - 20f;

            String dragHudText = String.format(Locale.US, "L=%.2fm (dX=%.1f, dY=%.1f)", len, dx, dy);
            float hudW = hudTextPaint.measureText(dragHudText);
            RectF dragBadgeRect = new RectF(mx - hudW / 2f - 8f, my - 22f, mx + hudW / 2f + 8f, my + 6f);
            canvas.drawRoundRect(dragBadgeRect, 8f, 8f, hudBgPaint);
            canvas.drawRoundRect(dragBadgeRect, 8f, 8f, badgeBorderPaint);
            canvas.drawText(dragHudText, mx - hudW / 2f, my - 2f, hudTextPaint);
        }

        // 6. Applied Point Loads
        drawLoads(canvas, height);

        // 7. Nodes and Supports (Standard Industry Glyphs)
        drawNodesAndSupports(canvas, height);

        // 8. Snapped Cursor Indicator
        if (hasSnappedTarget) {
            float sx = worldToScreenX(snappedGridX);
            float sy = worldToScreenY(snappedGridY, height);
            canvas.drawCircle(sx, sy, 20f, snapIndicatorPaint);
        }

        // 9. Top HUD Info Bar
        drawTopHud(canvas, width);
    }

    private void drawLoads(Canvas canvas, int height) {
        if (loads.isEmpty()) return;
        for (StructuralModel.Load l : loads) {
            StructuralModel.Node n = findNode(l.nodeId);
            if (n == null) continue;
            float sx = worldToScreenX(n.x);
            float sy = worldToScreenY(n.y, height);

            float fx = (float) l.fx;
            float fy = (float) l.fy;
            float mag = (float) Math.hypot(fx, fy);
            if (mag < 1e-4) continue;

            float arrowLen = 65f;
            float ux = (fx / mag) * arrowLen;
            float uy = -(fy / mag) * arrowLen; // screen Y inverted

            float startX = sx - ux;
            float startY = sy - uy;

            // Shaft
            canvas.drawLine(startX, startY, sx, sy, loadArrowPaint);

            // Arrow head
            float headLen = 18f;
            float wingAngle = 0.40f;
            double angle = Math.atan2(-uy, -ux);

            float w1x = sx + (float) (headLen * Math.cos(angle + wingAngle));
            float w1y = sy + (float) (headLen * Math.sin(angle + wingAngle));
            float w2x = sx + (float) (headLen * Math.cos(angle - wingAngle));
            float w2y = sy + (float) (headLen * Math.sin(angle - wingAngle));

            Path headPath = new Path();
            headPath.moveTo(sx, sy);
            headPath.lineTo(w1x, w1y);
            headPath.lineTo(w2x, w2y);
            headPath.close();
            canvas.drawPath(headPath, loadArrowPaint);

            // Professional Load Badge at arrow tail
            String label;
            if (Math.abs(fx) > 0 && Math.abs(fy) > 0) {
                label = String.format(Locale.US, "Fx:%.1f Fy:%.1f kN", fx / 1000.0, fy / 1000.0);
            } else if (Math.abs(fx) > 0) {
                label = String.format(Locale.US, "Fx:%.1f kN", fx / 1000.0);
            } else {
                label = String.format(Locale.US, "Fy:%.1f kN", fy / 1000.0);
            }

            float textW = loadTextPaint.measureText(label);
            float badgeCx = startX - (float) ((ux / arrowLen) * 18.0);
            float badgeCy = startY - (float) ((uy / arrowLen) * 18.0);

            RectF badgeRect = new RectF(badgeCx - textW / 2f - 8f, badgeCy - 16f, badgeCx + textW / 2f + 8f, badgeCy + 10f);
            canvas.drawRoundRect(badgeRect, 6f, 6f, loadBadgeBgPaint);
            canvas.drawRoundRect(badgeRect, 6f, 6f, loadBadgeBorderPaint);
            canvas.drawText(label, badgeCx, badgeCy + 2f, loadTextPaint);
        }
    }

    private void drawMetricGrid(Canvas canvas, int width, int height) {
        float originScreenY = height - offsetY;
        float originScreenX = offsetX;

        double minWorldX = screenToWorldX(0);
        double maxWorldX = screenToWorldX(width);
        double minWorldY = screenToWorldY(height, height);
        double maxWorldY = screenToWorldY(0, height);

        // Determine adaptive major grid step based on zoom scale
        double majorStep = 1.0;
        if (scale >= 120f) {
            majorStep = 0.5;
        } else if (scale >= 45f) {
            majorStep = 1.0;
        } else if (scale >= 20f) {
            majorStep = 2.0;
        } else {
            majorStep = 5.0;
        }

        double minorStep = (majorStep >= 5.0) ? 1.0 : (majorStep / 2.0);

        // 1. Minor grid lines
        int minMinorX = (int) Math.floor(minWorldX / minorStep) - 1;
        int maxMinorX = (int) Math.ceil(maxWorldX / minorStep) + 1;
        for (int i = minMinorX; i <= maxMinorX; i++) {
            double wx = i * minorStep;
            float sx = worldToScreenX(wx);
            if (sx >= 0 && sx <= width) {
                canvas.drawLine(sx, 0, sx, height, minorGridPaint);
            }
        }

        int minMinorY = (int) Math.floor(minWorldY / minorStep) - 1;
        int maxMinorY = (int) Math.ceil(maxWorldY / minorStep) + 1;
        for (int j = minMinorY; j <= maxMinorY; j++) {
            double wy = j * minorStep;
            float sy = worldToScreenY(wy, height);
            if (sy >= 0 && sy <= height) {
                canvas.drawLine(0, sy, width, sy, minorGridPaint);
            }
        }

        // 2. Major grid lines
        int minMajorX = (int) Math.floor(minWorldX / majorStep) - 1;
        int maxMajorX = (int) Math.ceil(maxWorldX / majorStep) + 1;
        for (int i = minMajorX; i <= maxMajorX; i++) {
            double wx = i * majorStep;
            float sx = worldToScreenX(wx);
            if (sx >= 0 && sx <= width) {
                canvas.drawLine(sx, 0, sx, height, majorGridPaint);
            }
        }

        int minMajorY = (int) Math.floor(minWorldY / majorStep) - 1;
        int maxMajorY = (int) Math.ceil(maxWorldY / majorStep) + 1;
        for (int j = minMajorY; j <= maxMajorY; j++) {
            double wy = j * majorStep;
            float sy = worldToScreenY(wy, height);
            if (sy >= 0 && sy <= height) {
                canvas.drawLine(0, sy, width, sy, majorGridPaint);
            }
        }

        // 3. Primary Coordinate Axes (X = 0 and Y = 0)
        canvas.drawLine(0, originScreenY, width, originScreenY, axisPaint);
        canvas.drawLine(originScreenX, 0, originScreenX, height, axisPaint);

        // Origin crosshair indicator (0, 0)
        if (originScreenX >= -20 && originScreenX <= width + 20 && originScreenY >= -20 && originScreenY <= height + 20) {
            canvas.drawCircle(originScreenX, originScreenY, 6f, axisPaint);
        }

        // 4. Metric Numerical Labels on X Axis
        float labelY = originScreenY + 22f;
        if (labelY > height - 12f) labelY = height - 12f;
        if (labelY < 28f) labelY = 28f;

        for (int i = minMajorX; i <= maxMajorX; i++) {
            double wx = i * majorStep;
            float sx = worldToScreenX(wx);
            if (sx >= 25 && sx <= width - 25) {
                String label = (Math.abs(wx) < 1e-4) ? "0" : ((wx == (long) wx) ? String.format(Locale.US, "%dm", (long) wx) : String.format(Locale.US, "%.1fm", wx));
                canvas.drawText(label, sx, labelY, axisTextPaint);
            }
        }

        // 5. Metric Numerical Labels on Y Axis
        float labelX = originScreenX - 10f;
        if (labelX < 32f) labelX = 32f;
        if (labelX > width - 36f) labelX = width - 36f;

        Paint yLabelPaint = new Paint(axisTextPaint);
        yLabelPaint.setTextAlign(Paint.Align.RIGHT);

        for (int j = minMajorY; j <= maxMajorY; j++) {
            double wy = j * majorStep;
            if (Math.abs(wy) < 1e-4) continue; // Skip 0 to avoid overlapping with X axis
            float sy = worldToScreenY(wy, height);
            if (sy >= 18 && sy <= height - 18) {
                String label = ((wy == (long) wy) ? String.format(Locale.US, "%dm", (long) wy) : String.format(Locale.US, "%.1fm", wy));
                canvas.drawText(label, labelX, sy + 6f, yLabelPaint);
            }
        }

        // 6. Coordinate Direction Badges
        canvas.drawText("+X (m)", width - 42f, originScreenY - 8f, axisTextPaint);
        canvas.drawText("+Y (m)", originScreenX + 12f, 26f, axisTextPaint);
    }

    private void drawPanels(Canvas canvas, int height) {
        if (panels.isEmpty()) return;
        Path path = new Path();
        for (StructuralModel.Panel p : panels) {
            if (p.nodeIds.size() < 3) continue;
            path.reset();
            boolean first = true;
            float sumX = 0f, sumY = 0f;
            int count = 0;
            for (int nid : p.nodeIds) {
                StructuralModel.Node n = findNode(nid);
                if (n == null) continue;
                float sx = worldToScreenX(n.x);
                float sy = worldToScreenY(n.y, height);
                sumX += sx;
                sumY += sy;
                count++;
                if (first) {
                    path.moveTo(sx, sy);
                    first = false;
                } else {
                    path.lineTo(sx, sy);
                }
            }
            path.close();
            canvas.drawPath(path, panelPaint);
            canvas.drawPath(path, panelBorderPaint);

            if (count > 0) {
                float cx = sumX / count;
                float cy = sumY / count;
                String label = String.format(Locale.US, "Panel %d [%s t=%.2fm]", p.id, p.elementType, p.thickness);
                canvas.drawText(label, cx, cy, axisTextPaint);
            }
        }
    }

    private void drawElements(Canvas canvas, int height) {
        for (StructuralModel.Element e : elements) {
            StructuralModel.Node n1 = findNode(e.node1Id);
            StructuralModel.Node n2 = findNode(e.node2Id);
            if (n1 != null && n2 != null) {
                float x1 = worldToScreenX(n1.x);
                float y1 = worldToScreenY(n1.y, height);
                float x2 = worldToScreenX(n2.x);
                float y2 = worldToScreenY(n2.y, height);

                Paint p = (selectedElement != null && selectedElement.id == e.id) ? selectedElementPaint : elementPaint;
                canvas.drawLine(x1, y1, x2, y2, p);

                // Dimension length badge offset perpendicularly from the bar
                double dx = n2.x - n1.x;
                double dy = n2.y - n1.y;
                double len = Math.sqrt(dx * dx + dy * dy);
                if (len > 1e-4) {
                    double screenDist = Math.hypot(x2 - x1, y2 - y1);
                    double nx = -(y2 - y1) / screenDist;
                    double ny = (x2 - x1) / screenDist;

                    float mx = (x1 + x2) / 2f + (float) (nx * 18.0);
                    float my = (y1 + y2) / 2f + (float) (ny * 18.0);

                    String text = String.format(Locale.US, "%.1fm", len);
                    float textWidth = axisTextPaint.measureText(text);
                    RectF badgeRect = new RectF(mx - textWidth / 2f - 6f, my - 16f, mx + textWidth / 2f + 6f, my + 8f);
                    canvas.drawRoundRect(badgeRect, 6f, 6f, badgeBgPaint);
                    canvas.drawRoundRect(badgeRect, 6f, 6f, badgeBorderPaint);

                    canvas.drawText(text, mx, my + 2f, textPaint);
                }
            }
        }
    }

    private void drawNodesAndSupports(Canvas canvas, int height) {
        for (StructuralModel.Node n : nodes) {
            float sx = worldToScreenX(n.x);
            float sy = worldToScreenY(n.y, height);

            // 1. Draw Support glyph cleanly underneath node circle
            drawSupportGlyph(canvas, sx, sy, n.supportType);

            // 2. Draw Node Circle
            Paint p = (n.supportType == StructuralModel.SupportType.FIXED) ? nodeFixedPaint : nodePaint;
            canvas.drawCircle(sx, sy, 15f, p);

            // 3. Selected Halo
            if (selectedNode != null && selectedNode.id == n.id) {
                canvas.drawCircle(sx, sy, 23f, nodeSelectedPaint);

                // Show live position HUD badge when moving joint
                if (isDragging && currentMode == Mode.SELECT_MOVE) {
                    String nodePosText = String.format(Locale.US, "N%d (%.2f, %.2f)", n.id, n.x, n.y);
                    float w = hudTextPaint.measureText(nodePosText);
                    RectF nodeBadge = new RectF(sx - w / 2f - 6f, sy - 40f, sx + w / 2f + 6f, sy - 14f);
                    canvas.drawRoundRect(nodeBadge, 6f, 6f, hudBgPaint);
                    canvas.drawRoundRect(nodeBadge, 6f, 6f, badgeBorderPaint);
                    canvas.drawText(nodePosText, sx - w / 2f, sy - 22f, hudTextPaint);
                }
            }

            // 4. Node ID Number inside/at center
            canvas.drawText(String.valueOf(n.id), sx, sy + 7f, textPaint);
        }
    }

    private void drawSupportGlyph(Canvas canvas, float sx, float sy, StructuralModel.SupportType type) {
        if (type == null || type == StructuralModel.SupportType.FREE) return;

        if (type == StructuralModel.SupportType.FIXED) {
            // Fixed Base (Empotramiento): Rigid base plate with ground hatching
            float plateHalfWidth = 26f;
            float plateTop = sy + 14f;
            float plateBottom = sy + 20f;

            // Ground base plate
            canvas.drawRect(sx - plateHalfWidth, plateTop, sx + plateHalfWidth, plateBottom, supportPaint);

            // Ground 45-degree diagonal hatch lines
            for (float h = sx - plateHalfWidth + 3f; h <= sx + plateHalfWidth; h += 7f) {
                canvas.drawLine(h, plateBottom, h - 8f, plateBottom + 12f, supportHatchPaint);
            }
        } else if (type == StructuralModel.SupportType.PINNED) {
            // Pinned Support (Hinged SPC 1-3): Hinge apex attached at bottom of node
            float triHalfWidth = 18f;
            float triHeight = 22f;
            float topY = sy + 10f;
            float baseY = topY + triHeight;

            // Equilateral Triangle
            Path path = new Path();
            path.moveTo(sx, topY);
            path.lineTo(sx - triHalfWidth, baseY);
            path.lineTo(sx + triHalfWidth, baseY);
            path.close();
            canvas.drawPath(path, supportPaint);

            // Base plate under triangle
            float baseHalfWidth = 24f;
            canvas.drawLine(sx - baseHalfWidth, baseY, sx + baseHalfWidth, baseY, supportPaint);

            // Ground hatch lines
            for (float h = sx - baseHalfWidth + 3f; h <= sx + baseHalfWidth; h += 7f) {
                canvas.drawLine(h, baseY, h - 7f, baseY + 10f, supportHatchPaint);
            }
        } else if (type == StructuralModel.SupportType.ROLLER) {
            // Roller Support (Sliding SPC 2): Hinge apex + Triangle + 3 Roller Wheels + Base Rail + Hatching
            float triHalfWidth = 16f;
            float triHeight = 18f;
            float topY = sy + 10f;
            float baseY = topY + triHeight;

            // Triangle
            Path path = new Path();
            path.moveTo(sx, topY);
            path.lineTo(sx - triHalfWidth, baseY);
            path.lineTo(sx + triHalfWidth, baseY);
            path.close();
            canvas.drawPath(path, supportPaint);

            // 3 Roller wheels (circles)
            float rollerY = baseY + 4.5f;
            float rollerRadius = 3.5f;
            canvas.drawCircle(sx - 11f, rollerY, rollerRadius, supportPaint);
            canvas.drawCircle(sx, rollerY, rollerRadius, supportPaint);
            canvas.drawCircle(sx + 11f, rollerY, rollerRadius, supportPaint);

            // Ground guide rail under rollers
            float railHalfWidth = 24f;
            float railY = rollerY + rollerRadius + 1.5f;
            canvas.drawLine(sx - railHalfWidth, railY, sx + railHalfWidth, railY, supportPaint);

            // Ground hatch lines under rail
            for (float h = sx - railHalfWidth + 3f; h <= sx + railHalfWidth; h += 7f) {
                canvas.drawLine(h, railY, h - 7f, railY + 10f, supportHatchPaint);
            }
        }
    }

    private void drawTopHud(Canvas canvas, int width) {
        String modeName;
        switch (currentMode) {
            case DRAW:
                modeName = "DRAW";
                break;
            case PAN:
                modeName = "PAN VIEW";
                break;
            case MOVE_NODES:
            case SELECT_MOVE:
                modeName = "MOVE NODES";
                break;
            case INSPECT:
                modeName = "INSPECT";
                break;
            case SUPPORT:
                modeName = "SUPPORT";
                break;
            case LOAD:
                modeName = "LOADS";
                break;
            case DELETE:
                modeName = "DELETE";
                break;
            default:
                modeName = "NAV";
                break;
        }

        String infoText;
        if (currentMode == Mode.INSPECT && (selectedNode != null || selectedElement != null || selectedPanel != null)) {
            infoText = "[" + modeName + "] " + getDetailedComponentInfo();
        } else {
            double curX = hasSnappedTarget ? snappedGridX : (isDragging ? screenToWorldX(currentDragX) : 0.0);
            double curY = hasSnappedTarget ? snappedGridY : (isDragging ? screenToWorldY(currentDragY, getHeight()) : 0.0);
            infoText = String.format(Locale.US, "[%s] Snap: 0.5m | X: %.2fm  Y: %.2fm", modeName, curX, curY);
        }

        float textW = hudTextPaint.measureText(infoText);
        RectF hudRect = new RectF(12f, 12f, 12f + textW + 20f, 44f);
        canvas.drawRoundRect(hudRect, 8f, 8f, hudBgPaint);
        canvas.drawRoundRect(hudRect, 8f, 8f, badgeBorderPaint);
        canvas.drawText(infoText, 22f, 33f, hudTextPaint);
    }

    // ==========================================
    // TOUCH INTERACTION & MAGNETIC SNAPPING
    // ==========================================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }

        scaleDetector.onTouchEvent(event);

        if (event.getPointerCount() > 1) {
            if (!isMultiTouch) {
                isMultiTouch = true;
                isDragging = false;
                activeNode = null;
                if (event.getPointerCount() >= 2) {
                    prevMidX = (event.getX(0) + event.getX(1)) / 2f;
                    prevMidY = (event.getY(0) + event.getY(1)) / 2f;
                }
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && event.getPointerCount() >= 2) {
                float midX = (event.getX(0) + event.getX(1)) / 2f;
                float midY = (event.getY(0) + event.getY(1)) / 2f;
                offsetX += (midX - prevMidX);
                offsetY -= (midY - prevMidY);
                prevMidX = midX;
                prevMidY = midY;
                invalidate();
            }
            return true;
        }

        if (isMultiTouch && event.getAction() == MotionEvent.ACTION_UP) {
            isMultiTouch = false;
            return true;
        }
        if (isMultiTouch) return true;

        float x = event.getX();
        float y = event.getY();
        int height = getHeight();

        // Calculate magnetic snapping coordinates
        double rawWorldX = screenToWorldX(x);
        double rawWorldY = screenToWorldY(y, height);

        snappedGridX = (float) Math.round(rawWorldX * 2.0) / 2.0f; // 0.5m grid snap
        snappedGridY = (float) Math.round(rawWorldY * 2.0) / 2.0f;
        if (snappedGridY < 0) snappedGridY = 0f;

        hoveredNode = getNodeNearScreen(x, y, 40f);
        if (hoveredNode != null) {
            snappedGridX = (float) hoveredNode.x;
            snappedGridY = (float) hoveredNode.y;
            hasSnappedTarget = true;
        } else {
            float distToSnap = (float) Math.hypot(worldToScreenX(snappedGridX) - x, worldToScreenY(snappedGridY, height) - y);
            hasSnappedTarget = distToSnap < 60f;
        }

        // Orthogonal guide snapping when drawing from activeNode
        if (isDragging && currentMode == Mode.DRAW && activeNode != null && hoveredNode == null) {
            double adx = snappedGridX - activeNode.x;
            double ady = snappedGridY - activeNode.y;
            double angleDeg = Math.toDegrees(Math.atan2(Math.abs(ady), Math.abs(adx)));
            if (angleDeg < 8.0) {
                // Snap to horizontal
                snappedGridY = (float) activeNode.y;
            } else if (angleDeg > 82.0) {
                // Snap to vertical
                snappedGridX = (float) activeNode.x;
            } else if (Math.abs(angleDeg - 45.0) < 6.0) {
                // Snap to 45 degree diagonal
                double signX = Math.signum(adx);
                double signY = Math.signum(ady);
                double avgDist = (Math.abs(adx) + Math.abs(ady)) / 2.0;
                avgDist = Math.round(avgDist * 2.0) / 2.0;
                snappedGridX = (float) (activeNode.x + signX * avgDist);
                snappedGridY = (float) (activeNode.y + signY * avgDist);
                if (snappedGridY < 0) snappedGridY = 0f;
            }
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = x;
                lastTouchY = y;

                if (currentMode == Mode.PAN) {
                    isDragging = true;
                    return true;
                } else if (currentMode == Mode.DRAW) {
                    if (hoveredNode != null) {
                        activeNode = hoveredNode;
                        activeNodeIsPending = false;
                    } else {
                        // Create start node at snapped location, but don't commit yet
                        activeNode = new StructuralModel.Node(nextNodeId++, snappedGridX, snappedGridY, 0.0,
                                snappedGridY == 0 ? StructuralModel.SupportType.FIXED : StructuralModel.SupportType.FREE);
                        activeNodeIsPending = true;
                    }
                    isDragging = true;
                    currentDragX = x;
                    currentDragY = y;
                    invalidate();
                    return true;
                } else if (currentMode == Mode.LOAD) {
                    selectedNode = getNodeNearScreen(x, y, 45f);
                    selectedElement = (selectedNode == null) ? getElementNearScreen(x, y, 30f) : null;
                    selectedPanel = (selectedNode == null && selectedElement == null) ? getPanelNearScreen(x, y) : null;
                    if (selectedNode != null) {
                        if (nodeSelectedListener != null) {
                            nodeSelectedListener.onNodeSelected(selectedNode, getLoadForNode(selectedNode.id));
                        }
                        notifyComponentInspected();
                        invalidate();
                        return true;
                    } else if (selectedElement != null) {
                        if (elementSelectedListener != null) {
                            elementSelectedListener.onElementSelected(selectedElement);
                        }
                        notifyComponentInspected();
                        invalidate();
                        return true;
                    }
                } else if (currentMode == Mode.MOVE_NODES || currentMode == Mode.SELECT_MOVE) {
                    selectedNode = getNodeNearScreen(x, y, 45f);
                    selectedElement = (selectedNode == null) ? getElementNearScreen(x, y, 30f) : null;
                    selectedPanel = (selectedNode == null && selectedElement == null) ? getPanelNearScreen(x, y) : null;
                    if (selectedNode != null) {
                        isDragging = true;
                        hasMovedNodeInDrag = false;
                    }
                    notifyComponentInspected();
                    invalidate();
                    return true;
                } else if (currentMode == Mode.INSPECT) {
                    selectedNode = getNodeNearScreen(x, y, 45f);
                    selectedElement = (selectedNode == null) ? getElementNearScreen(x, y, 30f) : null;
                    selectedPanel = (selectedNode == null && selectedElement == null) ? getPanelNearScreen(x, y) : null;
                    notifyComponentInspected();
                    invalidate();
                    return true;
                } else if (currentMode == Mode.SUPPORT) {
                    StructuralModel.Node n = getNodeNearScreen(x, y, 45f);
                    if (n != null) {
                        saveSnapshot();
                        cycleSupportType(n);
                        notifyModelChange();
                        invalidate();
                    }
                    return true;
                } else if (currentMode == Mode.DELETE) {
                    StructuralModel.Node n = getNodeNearScreen(x, y, 45f);
                    if (n != null) {
                        saveSnapshot();
                        deleteNode(n);
                        notifyModelChange();
                        invalidate();
                        return true;
                    }
                    StructuralModel.Element el = getElementNearScreen(x, y, 30f);
                    if (el != null) {
                        saveSnapshot();
                        elements.remove(el);
                        notifyModelChange();
                        invalidate();
                        return true;
                    }
                    StructuralModel.Panel p = getPanelNearScreen(x, y);
                    if (p != null) {
                        saveSnapshot();
                        panels.remove(p);
                        notifyModelChange();
                        invalidate();
                        return true;
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (currentMode == Mode.PAN && isDragging) {
                    offsetX += (x - lastTouchX);
                    offsetY -= (y - lastTouchY);
                    lastTouchX = x;
                    lastTouchY = y;
                    invalidate();
                    return true;
                } else if (isDragging && currentMode == Mode.DRAW) {
                    currentDragX = x;
                    currentDragY = y;
                    invalidate();
                } else if (isDragging && (currentMode == Mode.MOVE_NODES || currentMode == Mode.SELECT_MOVE) && selectedNode != null) {
                    if (Math.abs(snappedGridX - selectedNode.x) > 1e-3 || Math.abs(snappedGridY - selectedNode.y) > 1e-3) {
                        if (!hasMovedNodeInDrag) {
                            saveSnapshot();
                            hasMovedNodeInDrag = true;
                        }
                        selectedNode.x = snappedGridX;
                        selectedNode.y = snappedGridY;
                        notifyComponentInspected();
                        invalidate();
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (currentMode == Mode.PAN) {
                    isDragging = false;
                    return true;
                } else if (isDragging && currentMode == Mode.DRAW && activeNode != null) {
                    isDragging = false;
                    
                    boolean dragged = (Math.hypot(snappedGridX - activeNode.x, snappedGridY - activeNode.y) > 0.15);
                    
                    if (activeNodeIsPending && !dragged) {
                        // User tapped on empty grid point: commit node placement!
                        saveSnapshot();
                        nodes.add(activeNode);
                        activeNode = null;
                        activeNodeIsPending = false;
                        notifyModelChange();
                        invalidate();
                        return true;
                    }

                    if (dragged) {
                        saveSnapshot();

                        if (activeNodeIsPending) {
                            // Check if a node already exists at activeNode position
                            StructuralModel.Node existingStart = null;
                            for (StructuralModel.Node n : nodes) {
                                if (Math.hypot(n.x - activeNode.x, n.y - activeNode.y) < 0.15) {
                                    existingStart = n;
                                    break;
                                }
                            }
                            if (existingStart != null) {
                                activeNode = existingStart;
                            } else {
                                nodes.add(activeNode);
                            }
                            activeNodeIsPending = false;
                        }

                        StructuralModel.Node targetNode = hoveredNode;
                        if (targetNode == null) {
                            for (StructuralModel.Node n : nodes) {
                                if (Math.hypot(n.x - snappedGridX, n.y - snappedGridY) < 0.15) {
                                    targetNode = n;
                                    break;
                                }
                            }
                        }

                        if (targetNode == null) {
                            targetNode = new StructuralModel.Node(nextNodeId++, snappedGridX, snappedGridY, 0.0,
                                    snappedGridY == 0 ? StructuralModel.SupportType.FIXED : StructuralModel.SupportType.FREE);
                            nodes.add(targetNode);
                        }

                        if (targetNode.id != activeNode.id && !hasElementBetween(activeNode.id, targetNode.id)) {
                            elements.add(new StructuralModel.Element(nextElementId++, activeNode.id, targetNode.id, defaultSection, defaultMaterial));
                        }
                        
                        notifyModelChange();
                    }

                    activeNode = null;
                    activeNodeIsPending = false;
                    invalidate();
                    return true;
                } else if (isDragging && (currentMode == Mode.MOVE_NODES || currentMode == Mode.SELECT_MOVE)) {
                    isDragging = false;
                    if (hasMovedNodeInDrag) {
                        notifyModelChange();
                    }
                    hasMovedNodeInDrag = false;
                    invalidate();
                    return true;
                }
                break;
        }

        return super.onTouchEvent(event);
    }

    private void cycleSupportType(StructuralModel.Node node) {
        if (node.supportType == StructuralModel.SupportType.FREE) {
            node.supportType = StructuralModel.SupportType.FIXED;
        } else if (node.supportType == StructuralModel.SupportType.FIXED) {
            node.supportType = StructuralModel.SupportType.PINNED;
        } else if (node.supportType == StructuralModel.SupportType.PINNED) {
            node.supportType = StructuralModel.SupportType.ROLLER;
        } else {
            node.supportType = StructuralModel.SupportType.FREE;
        }
    }

    private boolean hasElementBetween(int n1, int n2) {
        for (StructuralModel.Element e : elements) {
            if ((e.node1Id == n1 && e.node2Id == n2) || (e.node1Id == n2 && e.node2Id == n1)) {
                return true;
            }
        }
        return false;
    }

    private void deleteNode(StructuralModel.Node node) {
        nodes.remove(node);
        loads.removeIf(l -> l.nodeId == node.id);
        List<StructuralModel.Element> toRemove = new ArrayList<>();
        for (StructuralModel.Element e : elements) {
            if (e.node1Id == node.id || e.node2Id == node.id) {
                toRemove.add(e);
            }
        }
        elements.removeAll(toRemove);
        List<StructuralModel.Panel> pRemove = new ArrayList<>();
        for (StructuralModel.Panel p : panels) {
            if (p.nodeIds != null && p.nodeIds.contains(node.id)) {
                pRemove.add(p);
            }
        }
        panels.removeAll(pRemove);
    }

    private StructuralModel.Panel getPanelNearScreen(float sx, float sy) {
        int height = getHeight();
        double wx = screenToWorldX(sx);
        double wy = screenToWorldY(sy, height);
        for (StructuralModel.Panel p : panels) {
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (int nid : p.nodeIds) {
                StructuralModel.Node n = findNode(nid);
                if (n != null) {
                    minX = Math.min(minX, n.x);
                    maxX = Math.max(maxX, n.x);
                    minY = Math.min(minY, n.y);
                    maxY = Math.max(maxY, n.y);
                }
            }
            if (wx >= minX - 0.2 && wx <= maxX + 0.2 && wy >= minY - 0.2 && wy <= maxY + 0.2) {
                return p;
            }
        }
        return null;
    }

    private StructuralModel.Node getNodeNearScreen(float sx, float sy, float maxDistPx) {
        int height = getHeight();
        for (StructuralModel.Node n : nodes) {
            float nx = worldToScreenX(n.x);
            float ny = worldToScreenY(n.y, height);
            if (Math.hypot(nx - sx, ny - sy) <= maxDistPx) {
                return n;
            }
        }
        return null;
    }

    private StructuralModel.Element getElementNearScreen(float sx, float sy, float maxDistPx) {
        int height = getHeight();
        for (StructuralModel.Element e : elements) {
            StructuralModel.Node n1 = findNode(e.node1Id);
            StructuralModel.Node n2 = findNode(e.node2Id);
            if (n1 != null && n2 != null) {
                float x1 = worldToScreenX(n1.x);
                float y1 = worldToScreenY(n1.y, height);
                float x2 = worldToScreenX(n2.x);
                float y2 = worldToScreenY(n2.y, height);

                float dist = distanceToSegment(sx, sy, x1, y1, x2, y2);
                if (dist <= maxDistPx) return e;
            }
        }
        return null;
    }

    private float distanceToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
        if (l2 == 0) return (float) Math.hypot(px - x1, py - y1);
        float t = Math.max(0, Math.min(1, ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2));
        float projX = x1 + t * (x2 - x1);
        float projY = y1 + t * (y2 - y1);
        return (float) Math.hypot(px - projX, py - projY);
    }

    private StructuralModel.Node findNode(int id) {
        for (StructuralModel.Node n : nodes) {
            if (n.id == id) return n;
        }
        return null;
    }

    // Coordinate transforms
    private float worldToScreenX(double wx) {
        return offsetX + (float) wx * scale;
    }

    private float worldToScreenY(double wy, int height) {
        return height - offsetY - (float) wy * scale;
    }

    private double screenToWorldX(float sx) {
        return (sx - offsetX) / scale;
    }

    private double screenToWorldY(float sy, int height) {
        return (height - offsetY - sy) / scale;
    }
}
