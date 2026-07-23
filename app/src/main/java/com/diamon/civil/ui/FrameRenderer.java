package com.diamon.civil.ui;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.diamon.civil.engine.StructuralModel;

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

    private float rotationX = 30f;
    private float rotationY = -45f;
    private float translationX = 0f;
    private float translationY = 0f;
    private float zoom = 15f;

    private int programId;
    private int mvpMatrixHandle;
    private int positionHandle;
    private int colorHandle;
    private int pointSizeHandle;

    private int[] nodeVBO = new int[2];
    private int[] elemVBO = new int[2];
    private int[] gridVBO = new int[2];
    private int[] deformedVBO = new int[2];
    private int[] diagramVBO = new int[2];

    private float[] deformedPositions;
    private float[] deformedColors;
    private int deformedVertexCount = 0;

    private float[] diagramPositions;
    private float[] diagramColors;
    private int diagramVertexCount = 0;

    private boolean showDeformed = false;
    private boolean showDiagrams = false;

    private int nodeCount = 0;
    private int elemVertexCount = 0;
    private int gridVertexCount = 0;

    private int screenWidth = 1;
    private int screenHeight = 1;
    
    private boolean vbosInitialized = false;

    public FrameRenderer(Context context) {
        this.context = context;
    }

    public void addNode(StructuralModel.Node node) {
        nodes.add(node);
        updateModelBuffers();
    }

    public void addElement(StructuralModel.Element element) {
        elements.add(element);
        updateModelBuffers();
    }

    public void clear() {
        nodes.clear();
        elements.clear();
        updateModelBuffers();
    }

    private void updateModelBuffers() {
        if (!vbosInitialized) return;
        
        // Build Nodes Data
        if (nodes.isEmpty()) {
            nodeCount = 0;
        } else {
            float[] nodePositions = new float[nodes.size() * 3];
            float[] nodeColors = new float[nodes.size() * 4];
            for (int i = 0; i < nodes.size(); i++) {
                nodePositions[i * 3] = (float) nodes.get(i).x;
                nodePositions[i * 3 + 1] = (float) nodes.get(i).y;
                nodePositions[i * 3 + 2] = (float) nodes.get(i).z;
                
                nodeColors[i * 4] = 1.0f;     // R
                nodeColors[i * 4 + 1] = 0.3f; // G
                nodeColors[i * 4 + 2] = 0.3f; // B
                nodeColors[i * 4 + 3] = 1.0f; // A
            }
            nodeCount = nodes.size();
            uploadVBO(nodeVBO, nodePositions, nodeColors);
        }

        // Build Elements Data
        if (elements.isEmpty()) {
            elemVertexCount = 0;
        } else {
            float[] elemPositions = new float[elements.size() * 6];
            float[] elemColors = new float[elements.size() * 8];
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
                    
                    // Node 1 Color (Blue)
                    elemColors[colIdx++] = 0.2f; elemColors[colIdx++] = 0.4f; elemColors[colIdx++] = 0.8f; elemColors[colIdx++] = 1.0f;
                    // Node 2 Color (Blue)
                    elemColors[colIdx++] = 0.2f; elemColors[colIdx++] = 0.4f; elemColors[colIdx++] = 0.8f; elemColors[colIdx++] = 1.0f;
                }
            }
            elemVertexCount = posIdx / 3;
            if (elemVertexCount > 0) {
                uploadVBO(elemVBO, elemPositions, elemColors);
            }
        }
    }

    private StructuralModel.Node findNode(int id) {
        for (StructuralModel.Node n : nodes) if (n.id == id) return n;
        return null;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES30.glClearColor(0.08f, 0.08f, 0.12f, 1.0f); // Dark blue-gray background
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glLineWidth(3.0f);

        programId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (programId == 0) return;

        mvpMatrixHandle = GLES30.glGetUniformLocation(programId, "uMVPMatrix");
        positionHandle = GLES30.glGetAttribLocation(programId, "aPosition");
        colorHandle = GLES30.glGetAttribLocation(programId, "aColor");
        pointSizeHandle = GLES30.glGetUniformLocation(programId, "uPointSize");

        GLES30.glGenBuffers(2, nodeVBO, 0);
        GLES30.glGenBuffers(2, elemVBO, 0);
        GLES30.glGenBuffers(2, gridVBO, 0);
        GLES30.glGenBuffers(2, deformedVBO, 0);
        GLES30.glGenBuffers(2, diagramVBO, 0);
        
        vbosInitialized = true;

        createGrid();
        updateModelBuffers(); // Ensure previously added nodes are buffered

        if (deformedVertexCount > 0) uploadVBO(deformedVBO, deformedPositions, deformedColors);
        if (diagramVertexCount > 0) uploadVBO(diagramVBO, diagramPositions, diagramColors);
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
        Matrix.translateM(viewMatrix, 0, translationX, translationY, -zoom);
        Matrix.rotateM(viewMatrix, 0, rotationX, 1f, 0f, 0f);
        Matrix.rotateM(viewMatrix, 0, rotationY, 0f, 1f, 0f);

        Matrix.setIdentityM(modelMatrix, 0);

        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0);

        GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

        if (gridVertexCount > 0) {
            GLES30.glUniform1f(pointSizeHandle, 1.0f);
            drawVBO(gridVBO, gridVertexCount, GLES30.GL_LINES);
        }

        if (elemVertexCount > 0) {
            drawVBO(elemVBO, elemVertexCount, GLES30.GL_LINES);
        }

        if (showDeformed && deformedVertexCount > 0) {
            GLES30.glLineWidth(3.0f);
            drawVBO(deformedVBO, deformedVertexCount, GLES30.GL_LINES);
            GLES30.glLineWidth(2.0f);
        }

        if (showDiagrams && diagramVertexCount > 0) {
            GLES30.glLineWidth(2.5f);
            drawVBO(diagramVBO, diagramVertexCount, GLES30.GL_LINES);
            GLES30.glLineWidth(2.0f);
        }

        if (nodeCount > 0) {
            GLES30.glUniform1f(pointSizeHandle, 15.0f);
            drawVBO(nodeVBO, nodeCount, GLES30.GL_POINTS);
        }
    }

    public void setShowDeformed(boolean show) {
        this.showDeformed = show;
    }

    public void setShowDiagrams(boolean show) {
        this.showDiagrams = show;
    }

    public void setDeformedShape(float[] positions, float[] colors) {
        if (positions == null || positions.length == 0) return;
        deformedVertexCount = positions.length / 3;
        deformedPositions = positions;
        deformedColors = colors;
        if (vbosInitialized) {
            uploadVBO(deformedVBO, positions, colors);
        }
    }

    public void setDiagrams(float[] positions, float[] colors) {
        if (positions == null || positions.length == 0) return;
        diagramVertexCount = positions.length / 3;
        diagramPositions = positions;
        diagramColors = colors;
        if (vbosInitialized) {
            uploadVBO(diagramVBO, positions, colors);
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
        if (this.zoom < 1f) this.zoom = 1f;
        if (this.zoom > 100f) this.zoom = 100f;
        updateProjectionMatrix();
    }

    private void updateProjectionMatrix() {
        if (screenWidth == 0 || screenHeight == 0) return;
        float ratio = (float) screenWidth / screenHeight;
        float orthoScale = zoom / 3f;
        Matrix.orthoM(projectionMatrix, 0, -ratio * orthoScale, ratio * orthoScale, -orthoScale, orthoScale, 0.1f, 200f);
    }

    private void createGrid() {
        int gridSize = 10;
        int lineCount = (gridSize * 2 + 1) * 2;
        float[] positions = new float[lineCount * 2 * 3];
        float[] colors = new float[lineCount * 2 * 4];

        int idx = 0;
        int cidx = 0;
        for (int i = -gridSize; i <= gridSize; i++) {
            positions[idx++] = i; positions[idx++] = 0; positions[idx++] = -gridSize;
            positions[idx++] = i; positions[idx++] = 0; positions[idx++] = gridSize;
            
            positions[idx++] = -gridSize; positions[idx++] = 0; positions[idx++] = i;
            positions[idx++] = gridSize; positions[idx++] = 0; positions[idx++] = i;

            float alpha = (i == 0) ? 0.5f : 0.15f;
            for (int j = 0; j < 4; j++) {
                colors[cidx++] = 0.4f; colors[cidx++] = 0.4f; colors[cidx++] = 0.5f; colors[cidx++] = alpha;
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
        if (vbo[0] == 0 || vbo[1] == 0) return;
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
