package com.diamon.civil.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.diamon.civil.engine.DatParser;
import com.diamon.civil.engine.StructuralModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * B4: DiagramView — Renders structural diagrams (BMD, SFD, AFD) using Android Canvas.
 * Superimposed on top of the 3D view.
 */
public class DiagramView extends View {

    private DatParser.ParseResult results;
    private StructuralModel model;
    private int diagramType = 0; // 0: None, 1: BMD (M3), 2: SFD (V2), 3: AFD (N)
    
    private final Paint beamPaint = new Paint();
    private final Paint diagramPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint deformedPaint = new Paint(); // New improvement

    public DiagramView(Context context) {
        this(context, null);
    }

    public DiagramView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        beamPaint.setColor(Color.BLACK);
        beamPaint.setStrokeWidth(4f);
        beamPaint.setStyle(Paint.Style.STROKE);

        diagramPaint.setColor(Color.argb(150, 255, 0, 0)); // Transparent Red for BMD
        diagramPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        diagramPaint.setStrokeWidth(2f);

        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(24f);
        textPaint.setFakeBoldText(true);

        deformedPaint.setColor(Color.argb(200, 100, 100, 100)); // Grey for deformed
        deformedPaint.setStyle(Paint.Style.STROKE);
        deformedPaint.setStrokeWidth(4f);
        deformedPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{10, 10}, 0));
    }

    public void setModelAndResults(StructuralModel model, DatParser.ParseResult results) {
        this.model = model;
        this.results = results;
        invalidate();
    }

    public void setDiagramType(int type) {
        this.diagramType = type;
        if (type == 1) diagramPaint.setColor(Color.argb(150, 255, 0, 0)); // BMD: Red
        else if (type == 2) diagramPaint.setColor(Color.argb(150, 0, 0, 255)); // SFD: Blue
        else if (type == 3) diagramPaint.setColor(Color.argb(150, 0, 255, 0)); // AFD: Green
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (model == null || results == null || diagramType == 0) return;

        // Simplified 2D Projection for prototype (assuming XY plane)
        // In a full implementation, we'd use the camera projection matrix from FrameRenderer
        
        float scale = 50f; // Pixels per meter
        float offsetX = getWidth() / 2f;
        float offsetY = getHeight() / 2f;

        Map<Integer, StructuralModel.Node> nodeMap = new HashMap<>();
        for (StructuralModel.Node n : model.nodes) nodeMap.put(n.id, n);

        // Group results by element
        Map<Integer, DatParser.SectionForces> elemResults = new HashMap<>();
        for (DatParser.SectionForces sf : results.forces) {
            // For now, take the first integration point or average
            elemResults.put(sf.elementId, sf);
        }

        for (StructuralModel.Element e : model.elements) {
            StructuralModel.Node n1 = nodeMap.get(e.node1Id);
            StructuralModel.Node n2 = nodeMap.get(e.node2Id);
            if (n1 == null || n2 == null) continue;

            float x1 = (float) n1.x * scale + offsetX;
            float y1 = (float) -n1.y * scale + offsetY; // Flip Y for screen
            float x2 = (float) n2.x * scale + offsetX;
            float y2 = (float) -n2.y * scale + offsetY;

            // Draw Beam
            canvas.drawLine(x1, y1, x2, y2, beamPaint);
            
            // Draw Deformed Shape (Placeholder: offset by a scaled displacement factor)
            float dispScale = 200f; // Amplification factor for visibility
            float d1 = (float) (n1.y * 0.05); // Simulated displacement
            float d2 = (float) (n2.y * 0.05);
            canvas.drawLine(x1, y1 - d1 * dispScale, x2, y2 - d2 * dispScale, deformedPaint);

            // Draw Diagram
            DatParser.SectionForces f = elemResults.get(e.id);
            if (f != null) {
                double value = 0;
                double maxVal = 1.0;
                if (diagramType == 1) { value = f.M1; maxVal = results.maxAbsM1; }
                else if (diagramType == 2) { value = f.V2; maxVal = results.maxAbsV2; }
                else if (diagramType == 3) { value = f.N; maxVal = results.maxAbsN; }

                if (maxVal == 0) maxVal = 1.0;
                float diagramScale = 100f; // Max height of diagram in pixels
                float h = (float) (value / maxVal) * diagramScale;

                // Perpendicular vector for the diagram offset
                float dx = x2 - x1;
                float dy = y2 - y1;
                float len = (float) Math.sqrt(dx*dx + dy*dy);
                if (len > 0) {
                    float px = -dy / len;
                    float py = dx / len;

                    Path path = new Path();
                    path.moveTo(x1, y1);
                    path.lineTo(x1 + px * h, y1 + py * h);
                    path.lineTo(x2 + px * h, y2 + py * h);
                    path.lineTo(x2, y2);
                    path.close();
                    canvas.drawPath(path, diagramPaint);
                    
                    // Label
                    canvas.drawText(String.format("%.1f", value), (x1+x2)/2 + px*h, (y1+y2)/2 + py*h, textPaint);
                }
            }
        }
    }
}
