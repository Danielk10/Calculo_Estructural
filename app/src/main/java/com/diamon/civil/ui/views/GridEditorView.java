package com.diamon.civil.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.diamon.civil.engine.StructuralModel;

import java.util.ArrayList;
import java.util.List;

public class GridEditorView extends View {

    private Paint gridPaint;
    private Paint nodePaint;
    private Paint elementPaint;
    private Paint activeElementPaint;

    private float gridSize = 100f; // px per grid unit
    private float nodeRadius = 15f;

    private List<StructuralModel.Node> nodes = new ArrayList<>();
    private List<StructuralModel.Element> elements = new ArrayList<>();
    private int nextNodeId = 1;
    private int nextElementId = 1;

    private StructuralModel.Node activeNode = null;
    private float currentDragX, currentDragY;
    private boolean isDragging = false;

    public GridEditorView(Context context) {
        super(context);
        init();
    }

    public GridEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        gridPaint = new Paint();
        gridPaint.setColor(Color.LTGRAY);
        gridPaint.setStrokeWidth(2f);

        nodePaint = new Paint();
        nodePaint.setColor(Color.BLUE);
        nodePaint.setStyle(Paint.Style.FILL);
        nodePaint.setAntiAlias(true);

        elementPaint = new Paint();
        elementPaint.setColor(Color.DKGRAY);
        elementPaint.setStrokeWidth(8f);
        elementPaint.setAntiAlias(true);

        activeElementPaint = new Paint();
        activeElementPaint.setColor(Color.RED);
        activeElementPaint.setStrokeWidth(6f);
        activeElementPaint.setAntiAlias(true);
    }

    public List<StructuralModel.Node> getNodes() {
        return nodes;
    }

    public List<StructuralModel.Element> getElements() {
        return elements;
    }

    public void clear() {
        nodes.clear();
        elements.clear();
        nextNodeId = 1;
        nextElementId = 1;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Draw grid
        for (float x = 0; x < width; x += gridSize) {
            canvas.drawLine(x, 0, x, height, gridPaint);
        }
        for (float y = height; y > 0; y -= gridSize) {
            canvas.drawLine(0, y, width, y, gridPaint);
        }

        // Draw elements
        for (StructuralModel.Element e : elements) {
            StructuralModel.Node n1 = findNode(e.node1Id);
            StructuralModel.Node n2 = findNode(e.node2Id);
            if (n1 != null && n2 != null) {
                float x1 = (float) n1.x * gridSize;
                float y1 = height - (float) n1.y * gridSize;
                float x2 = (float) n2.x * gridSize;
                float y2 = height - (float) n2.y * gridSize;
                canvas.drawLine(x1, y1, x2, y2, elementPaint);
            }
        }

        // Draw active drag line
        if (isDragging && activeNode != null) {
            float x1 = (float) activeNode.x * gridSize;
            float y1 = height - (float) activeNode.y * gridSize;
            canvas.drawLine(x1, y1, currentDragX, currentDragY, activeElementPaint);
        }

        // Draw nodes
        for (StructuralModel.Node n : nodes) {
            float x = (float) n.x * gridSize;
            float y = height - (float) n.y * gridSize;
            canvas.drawCircle(x, y, nodeRadius, nodePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        
        // Convert to grid coordinates
        float gridX = Math.round(x / gridSize);
        float gridY = Math.round((getHeight() - y) / gridSize);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                activeNode = getNodeAt(gridX, gridY);
                if (activeNode == null) {
                    // Create new node
                    activeNode = new StructuralModel.Node(nextNodeId++, gridX, gridY, 0.0);
                    nodes.add(activeNode);
                }
                isDragging = true;
                currentDragX = x;
                currentDragY = y;
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    currentDragX = x;
                    currentDragY = y;
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (isDragging) {
                    isDragging = false;
                    StructuralModel.Node targetNode = getNodeAt(gridX, gridY);
                    if (targetNode == null && (gridX != activeNode.x || gridY != activeNode.y)) {
                        // Create target node
                        targetNode = new StructuralModel.Node(nextNodeId++, gridX, gridY, 0.0);
                        nodes.add(targetNode);
                    }
                    
                    if (targetNode != null && targetNode.id != activeNode.id) {
                        // Create element
                        elements.add(new StructuralModel.Element(nextElementId++, activeNode.id, targetNode.id, "B31", "Steel"));
                    }
                    activeNode = null;
                    invalidate();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private StructuralModel.Node getNodeAt(float gx, float gy) {
        for (StructuralModel.Node n : nodes) {
            if (Math.abs(n.x - gx) < 0.5f && Math.abs(n.y - gy) < 0.5f) {
                return n;
            }
        }
        return null;
    }

    private StructuralModel.Node findNode(int id) {
        for (StructuralModel.Node n : nodes) {
            if (n.id == id) return n;
        }
        return null;
    }
}
