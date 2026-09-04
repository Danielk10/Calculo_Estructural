package com.diamon.civil.core.util.logging;

import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class ModuleLogger {
    private static final ModuleLogger GLOBAL_LOGGER = new ModuleLogger("Global");

    public static final String WELCOME_BANNER =
        "--- Structural Analysis FEA 3D Terminal System ---\n" +
        "Type 'help' to see list of available commands.\n";

    public static final String HELP_TEXT =
        "Special Test & Pipeline Commands:\n" +
        "  test-gmsh              - 3D CAD Boolean difference & mesh test (Cylinder - Sphere, V=48.69 mm³)\n" +
        "  test-draw              - OpenCASCADE DRAWEXE headless box primitive test (10×10×10 mm BREP)\n" +
        "  test-calculix          - Linear elasticity validation: Hooke & Poisson on unit cube (1 Thread)\n" +
        "  test-calculix-parallel - Multi-threaded CalculiX execution on unit cube (Multi-core)\n" +
        "  test-frame             - 2D Portal Frame structural analysis (B31 beam forces & lateral drift)\n" +
        "  test-frd-parser        - C++ JNI FRD binary result parser & GLB converter test\n" +
        "  test-dat-parser        - Java DAT parser test for beam internal forces and displacements\n" +
        "  test-coordinate-fallback - Automatic spatial boundary condition detection on 3D geometry\n" +
        "  test-step-solve        - End-to-end CAD meshing & solving pipeline with linkrods.step\n" +
        "  test-bracket-solve     - End-to-end CAD meshing & solving pipeline with bracket_simple.step\n" +
        "  test-cad-solve         - Headless CAD generation, meshing & FEA solving (DRAWEXE+Gmsh+CCX)\n" +
        "  run-sim-test           - Automated Cantilever Beam simulation vs Euler-Bernoulli theory\n\n" +
        "Standard Shell Commands:\n" +
        "  ls [path]              - List files and directories in workspace\n" +
        "  cd <path>              - Change working directory\n" +
        "  pwd                    - Show current directory path\n" +
        "  cat <file>             - Print file content to terminal\n" +
        "  echo <text> [> file]   - Print text or write/append (>>) to file\n" +
        "  touch <file>           - Create empty file or update timestamp\n" +
        "  cp <src> <dst>         - Copy file between folders\n" +
        "  mkdir <name>           - Create new directory\n" +
        "  rm [-rf] <target>      - Delete file or directory\n" +
        "  clear                  - Clear terminal screen\n" +
        "  help                   - Show this help message\n\n" +
        "Solvers, CAD & Scripting Binaries:\n" +
        "  ccx <jobname>          - Run CalculiX CCX solver directly on <jobname>.inp (-i, -v)\n" +
        "  gmsh <args>            - Run Gmsh 3D mesh generator directly (-3, -order, -clmax, -format)\n" +
        "  draw [script.tcl|-c]   - Run OpenCASCADE DRAWEXE TCL CAD engine in headless mode\n" +
        "  tclsh <script.tcl>     - Execute Tcl 8.6 script interpreter\n";

    static {
        GLOBAL_LOGGER.log(WELCOME_BANNER);
    }

    private final String moduleName;
    private final List<StringBuilder> consoleLines = new ArrayList<>();
    private int currentLineIndex = -1;
    private boolean cursorAtStartOfLine = false;
    private boolean isLogUpdatePending = false;
    private final List<LogListener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Runnable logNotifier = new Runnable() {
        @Override
        public void run() {
            String fullLog;
            synchronized (consoleLines) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < consoleLines.size(); i++) {
                    if (i > 0) {
                        sb.append("\n");
                    }
                    sb.append(consoleLines.get(i).toString());
                }
                fullLog = sb.toString();
                isLogUpdatePending = false;
            }
            for (LogListener listener : listeners) {
                listener.onLogUpdated(fullLog);
            }
        }
    };

    public interface LogListener {
        void onLogUpdated(String fullLog);
    }

    public static ModuleLogger getGlobal() {
        return GLOBAL_LOGGER;
    }

    public ModuleLogger(String moduleName) {
        this.moduleName = moduleName;
    }

    public ModuleLogger() {
        this("General");
    }

    public void addListener(LogListener listener) {
        synchronized (consoleLines) {
            listeners.add(listener);
        }
        listener.onLogUpdated(getFullLog());
    }

    public void removeListener(LogListener listener) {
        synchronized (consoleLines) {
            listeners.remove(listener);
        }
    }

    public void log(String message) {
        if (message == null) return;
        appendRaw(message.endsWith("\n") ? message : message + "\n");
    }

    public void logRaw(String chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        appendRaw(chunk);
    }

    private void appendRaw(String text) {
        synchronized (consoleLines) {
            if (consoleLines.isEmpty()) {
                consoleLines.add(new StringBuilder());
                currentLineIndex = 0;
            }

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\n') {
                    consoleLines.add(new StringBuilder());
                    currentLineIndex = consoleLines.size() - 1;
                    cursorAtStartOfLine = false;
                } else if (c == '\r') {
                    cursorAtStartOfLine = true;
                } else if (c == '\b') {
                    StringBuilder currentLine = consoleLines.get(currentLineIndex);
                    if (currentLine.length() > 0) {
                        currentLine.setLength(currentLine.length() - 1);
                    }
                } else {
                    StringBuilder currentLine = consoleLines.get(currentLineIndex);
                    if (cursorAtStartOfLine) {
                        currentLine.setLength(0); // Overwrite line from start for in-place terminal updates
                        cursorAtStartOfLine = false;
                    }
                    currentLine.append(c);
                }
            }

            // Limit console buffer to 1000 lines to prevent OOM
            while (consoleLines.size() > 1000) {
                consoleLines.remove(0);
                currentLineIndex--;
            }
            if (currentLineIndex < 0) {
                currentLineIndex = 0;
            }
        }

        if (!isLogUpdatePending) {
            isLogUpdatePending = true;
            mainHandler.postDelayed(logNotifier, 80); // 80ms fast debounced UI refresh
        }
    }

    public void info(String message) {
        log("[INFO] " + message);
    }

    public void warn(String message) {
        log("[WARN] " + message);
    }

    public void warn(String message, Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append("[WARN] ").append(message);
        if (throwable != null) {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            throwable.printStackTrace(pw);
            sb.append("\n").append(sw.toString());
        }
        log(sb.toString());
    }

    public void error(String message) {
        log("[ERROR] " + message);
    }

    public void error(String message, Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append("[ERROR] ").append(message);
        if (throwable != null) {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            throwable.printStackTrace(pw);
            sb.append("\n").append(sw.toString());
        }
        log(sb.toString());
    }

    public void debug(String message) {
        log("[DEBUG] " + message);
    }

    public void clear() {
        synchronized (consoleLines) {
            consoleLines.clear();
            currentLineIndex = -1;
            cursorAtStartOfLine = false;
        }
        if (this == GLOBAL_LOGGER) {
            log(WELCOME_BANNER);
        } else {
            notifyImmediate();
        }
    }

    private void notifyImmediate() {
        mainHandler.post(logNotifier);
    }

    public String getFullLog() {
        synchronized (consoleLines) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < consoleLines.size(); i++) {
                if (i > 0) {
                    sb.append("\n");
                }
                sb.append(consoleLines.get(i).toString());
            }
            return sb.toString();
        }
    }

    public void attachToTextView(final TextView textView) {
        addListener(fullLog -> {
            textView.setText(fullLog);
        });
    }
}
