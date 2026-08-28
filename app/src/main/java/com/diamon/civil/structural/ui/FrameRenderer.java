package com.diamon.civil.structural.ui;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import com.diamon.civil.structural.engine.StructuralModel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class FrameRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "FrameRenderer";

    private final Context context;
    private final List<StructuralModel.Node> nodes = new ArrayList<>();
    private final List<StructuralModel.Element> elements = new ArrayList<>();
    private final List<StructuralModel.Panel> panels = new ArrayList<>();

    // Shader sources (GLSL ES 3.0)
    private static final String VERTEX_SHADER =
            "#version 300 es\n" +
            "uniform mat4 uMVPMatrix;\n" +
            "uniform float uPointSize;\n" +
            "in vec3 aPosition;\n" +
            "in vec4 aColor;\n" +
            "out vec4 vColor;\n" +
            "void main() {\n" +
            "    gl_Position = uMVPMatrix * vec4(aPosition, 1.0);\n" +
            "    gl_PointSize = uPointSize;\n" +
            "    vColor = aColor;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "in vec4 vColor;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    fragColor = vColor;\n" +
            "}\n";

    private final float[] mvpMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] tempMatrix = new float[16];

    private float rotationX = 15f;
    private float rotationY = -15f;
    private float translationX = 0f;
    private float translationY = 0f;
    private float zoom = 12f;

    private int programId;
    private int mvpMatrixHandle;
    private int positionHandle;
    private int colorHandle;
    private int pointSizeHandle;

    private final int[] nodeVBO = new int[2];
    private final int[] elemVBO = new int[2];
    private final int[] undeformedVBO = new int[2];
    private final int[] gridVBO = new int[2];
    private final int[] deformedVBO = new int[2];
    private final int[] loadVBO = new int[2];
    private final int[] diagramFillVBO = new int[2];
    private final int[] diagramLineVBO = new int[2];
    private final int[] supportVBO = new int[2];

    private float[] deformedPositions;
    private float[] deformedColors;
    private int deformedVertexCount = 0;

    private float[] loadPositions;
    private float[] loadColors;
    private int loadVertexCount = 0;

    private float[] diagramFillPositions;
    private float[] diagramFillColors;
    private int diagramFillVertexCount = 0;

    private float[] diagramLinePositions;
    private float[] diagramLineColors;
    private int diagramLineVertexCount = 0;

    private float[] supportPositions;
    private float[] supportColors;
    private int supportVertexCount = 0;

    private boolean showUndeformed = true;
    private boolean showDeformed = false;
    private boolean showDiagrams = false;
    private boolean showLoads = true;
    private boolean showSupports = true;
    private boolean usePerspective = true;

    private int nodeCount = 0;
    private int elemVertexCount = 0;
    private int gridVertexCount = 0;

    private int screenWidth = 1;
    private int screenHeight = 1;

    private boolean vbosInitialized = false;

    public FrameRenderer(Context context) {
        this.context = context;
    }

    public void setModel(StructuralModel model) {
        setModel(model, true);
    }

    public void setModel(StructuralModel model, boolean autoCenter) {
        nodes.clear();
        elements.clear();
        panels.clear();
        if (model != null) {
            if (model.nodes != null) nodes.addAll(model.nodes);
            if (model.elements != null) elements.addAll(model.elements);
            if (model.panels != null) panels.addAll(model.panels);
        }
        if (autoCenter) {
            autoCenterStructure();
        }
        updateModelBuffers();
    }

    public void resetCamera() {
        rotationX = 15f;
        rotationY = -15f;
        autoCenterStructure();
    }

    public void clear() {
        nodes.clear();
        elements.clear();
        panels.clear();
        deformedVertexCount = 0;
        loadVertexCount = 0;
        diagramFillVertexCount = 0;
        diagramLineVertexCount = 0;
        supportVertexCount = 0;
        updateModelBuffers();
    }

    private void autoCenterStructure() {
        if (nodes.isEmpty()) return;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (StructuralModel.Node n : nodes) {
            if (n.x < minX) minX = n.x; if (n.x > maxX) maxX = n.x;
            if (n.y < minY) minY = n.y; if (n.y > maxY) maxY = n.y;
            if (n.z < minZ) minZ = n.z; if (n.z > maxZ) maxZ = n.z;
        }

        float centerX = (float) ((minX + maxX) / 2.0);
        float centerY = (float) ((minY + maxY) / 2.0);
        float sizeX = (float) (maxX - minX);
        float sizeY = (float) (maxY - minY);
        float maxDim = Math.max(Math.max(sizeX, sizeY), 2.0f);

        translationX = -centerX;
        translationY = -centerY;
        zoom = maxDim * 2.2f;
        if (zoom < 6f) zoom = 6f;
    }

    private void updateModelBuffers() {
        if (!vbosInitialized) return;

        // 1. Nodes Buffer
        if (nodes.isEmpty()) {
            nodeCount = 0;
        } else {
            float[] nodePositions = new float[nodes.size() * 3];
            float[] nodeColors = new float[nodes.size() * 4];
            for (int i = 0; i < nodes.size(); i++) {
                nodePositions[i * 3] = (float) nodes.get(i).x;
                nodePositions[i * 3 + 1] = (float) nodes.get(i).y;
                nodePositions[i * 3 + 2] = (float) nodes.get(i).z;

                nodeColors[i * 4] = 0.0f;     // R
                nodeColors[i * 4 + 1] = 0.9f; // G
                nodeColors[i * 4 + 2] = 1.0f; // B (Cyan)
                nodeColors[i * 4 + 3] = 1.0f; // A
            }
            nodeCount = nodes.size();
            uploadVBO(nodeVBO, nodePositions, nodeColors);
        }

        // 2. Elements & Panels (Undeformed / Baseline Wireframe)
        int totalSegments = elements.size();
        for (StructuralModel.Panel p : panels) {
            if (p.nodeIds != null) totalSegments += p.nodeIds.size();
        }

        if (totalSegments == 0) {
            elemVertexCount = 0;
        } else {
            float[] elemPositions = new float[totalSegments * 6];
            float[] elemColors = new float[totalSegments * 8];
            int posIdx = 0;
            int colIdx = 0;

            for (StructuralModel.Element e : elements) {
                StructuralModel.Node n1 = findNode(e.node1Id);
                StructuralModel.Node n2 = findNode(e.node2Id);
                if (n1 != null && n2 != null) {
                    elemPositions[posIdx++] = (float) n1.x;
                    elemPositions[posIdx++] = (float) n1.y;
                    elemPositions[posIdx++] = (float) n1.z;

                    elemPositions[posIdx++] = (float) n2.x;
                    elemPositions[posIdx++] = (float) n2.y;
                    elemPositions[posIdx++] = (float) n2.z;

                    // Classic solid steel blue
                    for (int k = 0; k < 2; k++) {
                        elemColors[colIdx++] = 0.35f; // R
                        elemColors[colIdx++] = 0.65f; // G
                        elemColors[colIdx++] = 0.95f; // B
                        elemColors[colIdx++] = 0.85f; // A
                    }
                }
            }

            for (StructuralModel.Panel p : panels) {
                if (p.nodeIds == null || p.nodeIds.size() < 3) continue;
                int count = p.nodeIds.size();
                for (int k = 0; k < count; k++) {
                    int nid1 = p.nodeIds.get(k);
                    int nid2 = p.nodeIds.get((k + 1) % count);
                    StructuralModel.Node n1 = findNode(nid1);
                    StructuralModel.Node n2 = findNode(nid2);
                    if (n1 != null && n2 != null) {
                        elemPositions[posIdx++] = (float) n1.x;
                        elemPositions[posIdx++] = (float) n1.y;
                        elemPositions[posIdx++] = (float) n1.z;

                        elemPositions[posIdx++] = (float) n2.x;
                        elemPositions[posIdx++] = (float) n2.y;
                        elemPositions[posIdx++] = (float) n2.z;

                        // Vibrant cyan panel border
                        for (int j = 0; j < 2; j++) {
                            elemColors[colIdx++] = 0.0f;  // R
                            elemColors[colIdx++] = 0.85f; // G
                            elemColors[colIdx++] = 0.95f; // B
                            elemColors[colIdx++] = 0.80f; // A
                        }
                    }
                }
            }

            elemVertexCount = posIdx / 3;
            if (elemVertexCount > 0) {
                uploadVBO(elemVBO, elemPositions, elemColors);
                uploadVBO(undeformedVBO, elemPositions, elemColors);
            }
        }
    }

    private StructuralModel.Node findNode(int id) {
        for (StructuralModel.Node n : nodes) if (n.id == id) return n;
        return null;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES30.glClearColor(0.06f, 0.08f, 0.12f, 1.0f); // Dark engineering slate
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glLineWidth(3.0f);

        programId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (programId == 0) return;

        mvpMatrixHandle = GLES30.glGetUniformLocation(programId, "uMVPMatrix");
        positionHandle = GLES30.glGetAttribLocation(programId, "aPosition");
        colorHandle = GLES30.glGetAttribLocation(programId, "aColor");
        pointSizeHandle = GLES30.glGetUniformLocation(programId, "uPointSize");

        GLES30.glGenBuffers(2, nodeVBO, 0);
        GLES30.glGenBuffers(2, elemVBO, 0);
        GLES30.glGenBuffers(2, undeformedVBO, 0);
        GLES30.glGenBuffers(2, gridVBO, 0);
        GLES30.glGenBuffers(2, deformedVBO, 0);
        GLES30.glGenBuffers(2, loadVBO, 0);
        GLES30.glGenBuffers(2, diagramFillVBO, 0);
        GLES30.glGenBuffers(2, diagramLineVBO, 0);
        GLES30.glGenBuffers(2, supportVBO, 0);

        vbosInitialized = true;

        createGrid();
        updateModelBuffers();

        if (deformedVertexCount > 0) uploadVBO(deformedVBO, deformedPositions, deformedColors);
        if (loadVertexCount > 0) uploadVBO(loadVBO, loadPositions, loadColors);
        if (diagramFillVertexCount > 0) uploadVBO(diagramFillVBO, diagramFillPositions, diagramFillColors);
        if (diagramLineVertexCount > 0) uploadVBO(diagramLineVBO, diagramLinePositions, diagramLineColors);
        if (supportVertexCount > 0) uploadVBO(supportVBO, supportPositions, supportColors);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES30.glViewport(0, 0, width, height);
        screenWidth = width;
        screenHeight = height;
        updateProjectionMatrix();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        if (programId == 0) return;
        GLES30.glUseProgram(programId);

        Matrix.setIdentityM(viewMatrix, 0);
        Matrix.translateM(viewMatrix, 0, 0f, 0f, -zoom);
        Matrix.rotateM(viewMatrix, 0, rotationX, 1f, 0f, 0f);
        Matrix.rotateM(viewMatrix, 0, rotationY, 0f, 1f, 0f);
        Matrix.translateM(viewMatrix, 0, translationX, translationY, 0f);

        Matrix.setIdentityM(modelMatrix, 0);

        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0);

        GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

        // 1. Grid (Crisp structural coordinate reference — offset to avoid Z-fighting)
        if (gridVertexCount > 0) {
            GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL);
            GLES30.glPolygonOffset(1.0f, 1.0f);
            GLES30.glLineWidth(1.8f);
            drawVBO(gridVBO, gridVertexCount, GLES30.GL_LINES);
            GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL);
        }

        // 2. Ghost Undeformed Wireframe (when deformed or diagram is shown)
        if (showUndeformed && (showDeformed || showDiagrams) && elemVertexCount > 0) {
            GLES30.glLineWidth(1.5f);
            drawVBO(undeformedVBO, elemVertexCount, GLES30.GL_LINES);
        }

        // 3. Baseline Structural Elements (when not deformed)
        if (!showDeformed && !showDiagrams && elemVertexCount > 0) {
            GLES30.glLineWidth(4.0f);
            drawVBO(elemVBO, elemVertexCount, GLES30.GL_LINES);
        }

        // 4. Shaded Filled Diagram Polygons (Alpha fill)
        if (showDiagrams && diagramFillVertexCount > 0) {
            GLES30.glDepthMask(false); // Enable transparency without depth sorting artifacts
            drawVBO(diagramFillVBO, diagramFillVertexCount, GLES30.GL_TRIANGLES);
            GLES30.glDepthMask(true);
        }

        // 5. Diagram Boundary Lines & Station Hatch Ticks
        if (showDiagrams && diagramLineVertexCount > 0) {
            GLES30.glLineWidth(2.5f);
            drawVBO(diagramLineVBO, diagramLineVertexCount, GLES30.GL_LINES);
        }

        // 6. Deformed Structure (Continuous Curved Beams with displacement colors)
        if (showDeformed && deformedVertexCount > 0) {
            GLES30.glLineWidth(4.5f);
            drawVBO(deformedVBO, deformedVertexCount, GLES30.GL_LINES);
        }

        // 7. Supports (3D Structural Base Symbols)
        if (showSupports && supportVertexCount > 0) {
            GLES30.glLineWidth(2.5f);
            drawVBO(supportVBO, supportVertexCount, GLES30.GL_LINES);
        }

        // 8. Load Arrows (Golden 3D Vector Shafts & Heads)
        if (showLoads && loadVertexCount > 0) {
            GLES30.glLineWidth(3.5f);
            drawVBO(loadVBO, loadVertexCount, GLES30.GL_LINES);
        }

        // 9. Structural Nodes (Cyan Badges)
        if (nodeCount > 0) {
            GLES30.glUniform1f(pointSizeHandle, 16.0f);
            drawVBO(nodeVBO, nodeCount, GLES30.GL_POINTS);
        }
    }

    public void setShowUndeformed(boolean show) {
        this.showUndeformed = show;
    }

    public void setShowDeformed(boolean show) {
        this.showDeformed = show;
    }

    public void setShowDiagrams(boolean show) {
        this.showDiagrams = show;
    }

    public void setShowLoads(boolean show) {
        this.showLoads = show;
    }

    public void setShowSupports(boolean show) {
        this.showSupports = show;
    }

    public void setDeformedShape(float[] positions, float[] colors) {
        if (positions == null || positions.length == 0) {
            deformedVertexCount = 0;
            return;
        }
        deformedVertexCount = positions.length / 3;
        deformedPositions = positions;
        deformedColors = colors;
        if (vbosInitialized) {
            uploadVBO(deformedVBO, positions, colors);
        }
    }

    public void setDiagrams(float[] fillPos, float[] fillCol, float[] linePos, float[] lineCol) {
        if (fillPos != null && fillPos.length > 0) {
            diagramFillVertexCount = fillPos.length / 3;
            diagramFillPositions = fillPos;
            diagramFillColors = fillCol;
            if (vbosInitialized) uploadVBO(diagramFillVBO, fillPos, fillCol);
        } else {
            diagramFillVertexCount = 0;
        }

        if (linePos != null && linePos.length > 0) {
            diagramLineVertexCount = linePos.length / 3;
            diagramLinePositions = linePos;
            diagramLineColors = lineCol;
            if (vbosInitialized) uploadVBO(diagramLineVBO, linePos, lineCol);
        } else {
            diagramLineVertexCount = 0;
        }
    }

    public void setLoads(float[] positions, float[] colors) {
        if (positions == null || positions.length == 0) {
            loadVertexCount = 0;
            return;
        }
        loadVertexCount = positions.length / 3;
        loadPositions = positions;
        loadColors = colors;
        if (vbosInitialized) {
            uploadVBO(loadVBO, positions, colors);
        }
    }

    public void setSupports(float[] positions, float[] colors) {
        if (positions == null || positions.length == 0) {
            supportVertexCount = 0;
            return;
        }
        supportVertexCount = positions.length / 3;
        supportPositions = positions;
        supportColors = colors;
        if (vbosInitialized) {
            uploadVBO(supportVBO, positions, colors);
        }
    }

    public void addRotation(float dx, float dy) {
        this.rotationY += dx;
        this.rotationX += dy;
        if (this.rotationX > 89f) this.rotationX = 89f;
        if (this.rotationX < -89f) this.rotationX = -89f;
    }

    public void setTranslation(float dx, float dy) {
        this.translationX += dx;
        this.translationY += dy;
    }

    public void setZoom(float scale) {
        this.zoom *= scale;
        if (this.zoom < 2f) this.zoom = 2f;
        if (this.zoom > 100f) this.zoom = 100f;
        updateProjectionMatrix();
    }

    private void updateProjectionMatrix() {
        if (screenWidth == 0 || screenHeight == 0) return;
        float ratio = (float) screenWidth / screenHeight;
        if (usePerspective) {
            Matrix.perspectiveM(projectionMatrix, 0, 45f, ratio, 0.1f, 300f);
        } else {
            float orthoScale = zoom / 3f;
            Matrix.orthoM(projectionMatrix, 0, -ratio * orthoScale, ratio * orthoScale, -orthoScale, orthoScale, 0.1f, 300f);
        }
    }

    private void createGrid() {
        int gridSize = 20;
        int lineCount = (gridSize * 2 + 1) * 2;
        float[] positions = new float[lineCount * 2 * 3];
        float[] colors = new float[lineCount * 2 * 4];

        int idx = 0;
        int cidx = 0;
        for (int i = -gridSize; i <= gridSize; i++) {
            // Lines parallel to Z axis (varying X = i)
            positions[idx++] = i; positions[idx++] = 0; positions[idx++] = -gridSize;
            positions[idx++] = i; positions[idx++] = 0; positions[idx++] = gridSize;

            // Lines parallel to X axis (varying Z = i)
            positions[idx++] = -gridSize; positions[idx++] = 0; positions[idx++] = i;
            positions[idx++] = gridSize; positions[idx++] = 0; positions[idx++] = i;

            float r, g, b, alpha;
            if (i == 0) {
                // Main Coordinate Axes (Cyan / Electric Blue)
                r = 0.20f; g = 0.80f; b = 1.0f; alpha = 0.85f;
            } else if (i % 5 == 0) {
                // Major 5-meter Module Lines (Crisp Slate Blue)
                r = 0.45f; g = 0.65f; b = 0.88f; alpha = 0.55f;
            } else {
                // Standard 1-meter Grid Lines (Clear & High Contrast)
                r = 0.35f; g = 0.52f; b = 0.72f; alpha = 0.32f;
            }

            for (int j = 0; j < 4; j++) {
                colors[cidx++] = r; colors[cidx++] = g; colors[cidx++] = b; colors[cidx++] = alpha;
            }
        }

        gridVertexCount = idx / 3;
        uploadVBO(gridVBO, positions, colors);
    }

    private void uploadVBO(int[] vbo, float[] positions, float[] colors) {
        FloatBuffer posBuffer = createFloatBuffer(positions);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0]);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, positions.length * 4, posBuffer, GLES30.GL_STATIC_DRAW);

        FloatBuffer colorBuffer = createFloatBuffer(colors);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[1]);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, colors.length * 4, colorBuffer, GLES30.GL_STATIC_DRAW);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    private void drawVBO(int[] vbo, int vertexCount, int drawMode) {
        if (vbo[0] == 0 || vbo[1] == 0 || vertexCount <= 0) return;
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0]);
        GLES30.glEnableVertexAttribArray(positionHandle);
        GLES30.glVertexAttribPointer(positionHandle, 3, GLES30.GL_FLOAT, false, 0, 0);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[1]);
        GLES30.glEnableVertexAttribArray(colorHandle);
        GLES30.glVertexAttribPointer(colorHandle, 4, GLES30.GL_FLOAT, false, 0, 0);

        GLES30.glDrawArrays(drawMode, 0, vertexCount);

        GLES30.glDisableVertexAttribArray(positionHandle);
        GLES30.glDisableVertexAttribArray(colorHandle);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    private FloatBuffer createFloatBuffer(float[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(data);
        fb.position(0);
        return fb;
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource);
        if (vertexShader == 0 || fragmentShader == 0) return 0;

        int program = GLES30.glCreateProgram();
        GLES30.glAttachShader(program, vertexShader);
        GLES30.glAttachShader(program, fragmentShader);
        GLES30.glLinkProgram(program);
        return program;
    }

    private int loadShader(int type, String source) {
        int shader = GLES30.glCreateShader(type);
        GLES30.glShaderSource(shader, source);
        GLES30.glCompileShader(shader);
        return shader;
    }
}

