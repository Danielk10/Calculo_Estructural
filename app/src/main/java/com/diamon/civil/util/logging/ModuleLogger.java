package com.diamon.civil.util.logging;

import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class ModuleLogger {
    private final StringBuilder logBuilder = new StringBuilder();
    private final List<LogListener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface LogListener {
        void onLogUpdated(String fullLog);
    }

    public void addListener(LogListener listener) {
        listeners.add(listener);
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

    public void debug(String message) {
        log("[DEBUG] " + message);
    }

    public void clear() {
        logBuilder.setLength(0);
        notifyListeners();
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
            // Auto-scroll logic can be added in the fragment
        });
    }
}
