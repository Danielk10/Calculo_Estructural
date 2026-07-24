package com.diamon.civil.util.logging;

import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class ModuleLogger {
    private static final ModuleLogger GLOBAL_LOGGER = new ModuleLogger("Global");

    static {
        GLOBAL_LOGGER.log("--- FEA Advanced Terminal System ---\n" +
            "Type 'help' to see list of available commands.\n\n" +
            "Special Test Commands:\n" +
            "  test-gmsh    - Run a 3D CAD Boolean operations subtraction & mesh test (Gmsh + OCCT)\n" +
            "  test-draw    - Run OpenCASCADE DRAWEXE headless primitive box test (OCCT)\n" +
            "  run-sim-test - Run automated end-to-end FEA calculation test (Cantilever Beam)\n");
    }

    private final String moduleName;
    private final StringBuilder logBuilder = new StringBuilder();
    private final List<LogListener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface LogListener {
        void onLogUpdated(String fullLog);
    }

    public static ModuleLogger getGlobal() {
        return GLOBAL_LOGGER;
    }

    public ModuleLogger(String moduleName) {
        this.moduleName = moduleName;
        if (this != GLOBAL_LOGGER) {
            // Seed initial global log with current status if applicable
        }
    }

    public ModuleLogger() {
        this("General");
    }

    public void addListener(LogListener listener) {
        listeners.add(listener);
        // Call immediately with existing content to synchronize UI
        listener.onLogUpdated(getFullLog());
    }

    public void log(String message) {
        logBuilder.append(message).append("\n");
        notifyListeners();
    }

    public void info(String message) {
        log("[INFO] " + message);
    }

    public void error(String message) {
        log("[ERROR] " + message);
    }

    public void error(String message, Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append("[ERROR] ").append(message);
        if (throwable != null) {
            sb.append("\nException: ").append(throwable.toString()).append("\n");
            for (StackTraceElement ste : throwable.getStackTrace()) {
                sb.append("    at ").append(ste.toString()).append("\n");
            }
            Throwable cause = throwable.getCause();
            if (cause != null) {
                sb.append("Caused by: ").append(cause.toString()).append("\n");
                for (StackTraceElement ste : cause.getStackTrace()) {
                    sb.append("    at ").append(ste.toString()).append("\n");
                }
            }
        }
        log(sb.toString());
    }

    public void debug(String message) {
        log("[DEBUG] " + message);
    }

    public void clear() {
        logBuilder.setLength(0);
        notifyListeners();
        if (this == GLOBAL_LOGGER) {
            logBuilder.append("--- FEA Advanced Terminal System ---\n" +
                "Type 'help' to see list of available commands.\n\n" +
                "Special Test Commands:\n" +
                "  test-gmsh    - Run a 3D CAD Boolean operations subtraction & mesh test (Gmsh + OCCT)\n" +
                "  test-draw    - Run OpenCASCADE DRAWEXE headless primitive box test (OCCT)\n" +
                "  run-sim-test - Run automated end-to-end FEA calculation test (Cantilever Beam)\n\n");
            notifyListeners();
        }
    }

    public String getFullLog() {
        return logBuilder.toString();
    }

    private void notifyListeners() {
        final String currentLog = logBuilder.toString();
        mainHandler.post(() -> {
            for (LogListener listener : listeners) {
                listener.onLogUpdated(currentLog);
            }
        });
    }

    public void attachToTextView(final TextView textView) {
        addListener(fullLog -> {
            textView.setText(fullLog);
        });
    }
}
