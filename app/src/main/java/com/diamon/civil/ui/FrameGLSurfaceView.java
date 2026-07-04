package com.diamon.civil.ui;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;

import com.diamon.civil.engine.StructuralModel;

public class FrameGLSurfaceView extends GLSurfaceView {

    private final FrameRenderer renderer;
    private GestureDetector gestureDetector;
    private int nextNodeId = 1;
    private int nextElemId = 1;
    private StructuralModel.Node lastSelectedNode = null;

    public FrameGLSurfaceView(Context context) {
        this(context, null);
    }

    public FrameGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(3);
        renderer = new FrameRenderer(context);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        setupGestures(context);
    }

    public FrameRenderer getRenderer() { return renderer; }

    private void setupGestures(Context context) {
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // Simplified world coordinates: map screen pixels to world meters
                // In a real app, use unproject or ray-plane intersection
                float x = (e.getX() - getWidth() / 2f) / 50f;
                float y = -(e.getY() - getHeight() / 2f) / 50f;
                
                StructuralModel.Node newNode = new StructuralModel.Node(nextNodeId++, x, y, 0);
                renderer.addNode(newNode);
                
                if (lastSelectedNode != null) {
                    renderer.addElement(new StructuralModel.Element(nextElemId++, lastSelectedNode.id, newNode.id, "W8x31", "Steel"));
                }
                lastSelectedNode = newNode;
                
                requestRender();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                renderer.clear();
                nextNodeId = 1;
                nextElemId = 1;
                lastSelectedNode = null;
                requestRender();
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }
}
