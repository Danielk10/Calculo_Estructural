package com.diamon.civil.ui;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.diamon.civil.engine.StructuralModel;

public class FrameGLSurfaceView extends GLSurfaceView {

    private final FrameRenderer renderer;
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleDetector;

    private int nextNodeId = 1;
    private int nextElemId = 1;
    private StructuralModel.Node lastSelectedNode = null;

    private float previousX;
    private float previousY;
    private int activePointerId = -1;
    private boolean isScaling = false;

    private float prevMidX, prevMidY;

    public FrameGLSurfaceView(Context context) {
        this(context, null);
    }

    public FrameGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(3);
        renderer = new FrameRenderer(context);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY); // Animación fluida

        setupGestures(context);
    }

    public FrameRenderer getRenderer() { return renderer; }

    private void setupGestures(Context context) {
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                renderer.setZoom(1f / scaleFactor);
                isScaling = true;
                return true;
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                float x = (e.getX() - getWidth() / 2f) / 50f;
                float y = -(e.getY() - getHeight() / 2f) / 50f;
                
                StructuralModel.Node newNode = new StructuralModel.Node(nextNodeId++, x, y, 0);
                renderer.addNode(newNode);
                
                if (lastSelectedNode != null) {
                    renderer.addElement(new StructuralModel.Element(nextElemId++, lastSelectedNode.id, newNode.id, "W8x31", "Steel"));
                }
                lastSelectedNode = newNode;
                
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                renderer.clear();
                nextNodeId = 1;
                nextElemId = 1;
                lastSelectedNode = null;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);

        int action = event.getActionMasked();
        int pointerCount = event.getPointerCount();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                activePointerId = event.getPointerId(0);
                previousX = event.getX();
                previousY = event.getY();
                isScaling = false;
                break;
            }

            case MotionEvent.ACTION_POINTER_DOWN: {
                if (pointerCount == 2) {
                    prevMidX = (event.getX(0) + event.getX(1)) / 2f;
                    prevMidY = (event.getY(0) + event.getY(1)) / 2f;
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (isScaling) break;

                if (pointerCount == 1) {
                    int pointerIndex = event.findPointerIndex(activePointerId);
                    if (pointerIndex < 0) break;

                    float x = event.getX(pointerIndex);
                    float y = event.getY(pointerIndex);
                    float dx = (x - previousX) * 0.3f;
                    float dy = (y - previousY) * 0.3f;

                    renderer.addRotation(dx, dy);

                    previousX = x;
                    previousY = y;
                } else if (pointerCount == 2) {
                    float midX = (event.getX(0) + event.getX(1)) / 2f;
                    float midY = (event.getY(0) + event.getY(1)) / 2f;

                    float dx = (midX - prevMidX) * 0.02f;
                    float dy = -(midY - prevMidY) * 0.02f;

                    renderer.setTranslation(dx, dy);

                    prevMidX = midX;
                    prevMidY = midY;
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                activePointerId = -1;
                isScaling = false;
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                int pointerIndex = event.getActionIndex();
                int pointerId = event.getPointerId(pointerIndex);
                if (pointerId == activePointerId) {
                    int newIndex = pointerIndex == 0 ? 1 : 0;
                    previousX = event.getX(newIndex);
                    previousY = event.getY(newIndex);
                    activePointerId = event.getPointerId(newIndex);
                }
                break;
            }
        }

        return true;
    }
}
