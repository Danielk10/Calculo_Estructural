package com.diamon.civil.structural.ui;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.diamon.civil.structural.engine.StructuralModel;

public class FrameGLSurfaceView extends GLSurfaceView {

    private final FrameRenderer renderer;
    private ScaleGestureDetector scaleDetector;

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
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        setupGestures(context);
    }

    public FrameRenderer getRenderer() { return renderer; }

    public void setModel(StructuralModel model) {
        setModel(model, true);
    }

    public void setModel(StructuralModel model, boolean autoCenter) {
        queueEvent(() -> {
            renderer.setModel(model, autoCenter);
            requestRender();
        });
    }

    public void resetCamera() {
        queueEvent(() -> {
            renderer.resetCamera();
            requestRender();
        });
    }

    public void setShowDeformed(boolean show) {
        queueEvent(() -> {
            renderer.setShowDeformed(show);
            requestRender();
        });
    }

    public void setShowDiagrams(boolean show) {
        queueEvent(() -> {
            renderer.setShowDiagrams(show);
            requestRender();
        });
    }

    public void setShowLoads(boolean show) {
        queueEvent(() -> {
            renderer.setShowLoads(show);
            requestRender();
        });
    }

    public void setDeformedShape(float[] positions, float[] colors) {
        queueEvent(() -> {
            renderer.setDeformedShape(positions, colors);
            requestRender();
        });
    }

    public void setLoads(float[] positions, float[] colors) {
        queueEvent(() -> {
            renderer.setLoads(positions, colors);
            requestRender();
        });
    }

    public void setDiagrams(float[] fillPos, float[] fillCol, float[] linePos, float[] lineCol) {
        queueEvent(() -> {
            renderer.setDiagrams(fillPos, fillCol, linePos, lineCol);
            requestRender();
        });
    }

    public void setSupports(float[] positions, float[] colors) {
        queueEvent(() -> {
            renderer.setSupports(positions, colors);
            requestRender();
        });
    }

    public void setShowUndeformed(boolean show) {
        queueEvent(() -> {
            renderer.setShowUndeformed(show);
            requestRender();
        });
    }

    public void setShowSupports(boolean show) {
        queueEvent(() -> {
            renderer.setShowSupports(show);
            requestRender();
        });
    }

    private void setupGestures(Context context) {
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                final float scaleFactor = detector.getScaleFactor();
                queueEvent(() -> renderer.setZoom(1f / scaleFactor));
                isScaling = true;
                return true;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
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
                    // Orbit
                    int pointerIndex = event.findPointerIndex(activePointerId);
                    if (pointerIndex < 0) break;

                    float x = event.getX(pointerIndex);
                    float y = event.getY(pointerIndex);
                    float dx = (x - previousX) * 0.3f;
                    float dy = (y - previousY) * 0.3f;

                    final float fdx = dx;
                    final float fdy = dy;
                    queueEvent(() -> renderer.addRotation(fdx, fdy));

                    previousX = x;
                    previousY = y;
                } else if (pointerCount == 2) {
                    // Pan
                    float midX = (event.getX(0) + event.getX(1)) / 2f;
                    float midY = (event.getY(0) + event.getY(1)) / 2f;

                    float dx = (midX - prevMidX) * 0.02f;
                    float dy = -(midY - prevMidY) * 0.02f;

                    final float fdx = dx;
                    final float fdy = dy;
                    queueEvent(() -> renderer.setTranslation(fdx, fdy));

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

        requestRender();
        return true;
    }
}
