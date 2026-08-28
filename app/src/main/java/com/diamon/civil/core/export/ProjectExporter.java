package com.diamon.civil.core.export;

import android.content.Context;
import android.widget.Toast;
import com.diamon.civil.R;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProjectExporter {
    private final Context context;
    private final ExportManager exportManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ProjectExporter(Context context) {
        this.context = context;
        this.exportManager = new ExportManager(context);
    }

    public void exportAll(File workDir, final String subFolder) {
        executor.execute(() -> {
            File[] files = workDir.listFiles();
            int count = 0;
            if (files != null) {
                for (File f : files) {
                    if (shouldIgnore(f)) continue;
                    if (!f.getName().startsWith(".") && exportManager.exportToDownloads(f, subFolder)) {
                        count++;
                    }
                }
            }
            final int finalCount = count;
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).runOnUiThread(() -> 
                    Toast.makeText(context, context.getString(R.string.toast_export_all_success, finalCount, subFolder), Toast.LENGTH_LONG).show());
            }
        });
    }

    private boolean shouldIgnore(File f) {
        String name = f.getName();
        return name.equalsIgnoreCase("usr") || 
               name.equalsIgnoreCase("fake_root") || 
               name.equalsIgnoreCase("lib") ||
               name.equalsIgnoreCase("include") ||
               name.equalsIgnoreCase("share") ||
               name.equalsIgnoreCase("bin") ||
               name.equalsIgnoreCase("cache") ||
               name.equalsIgnoreCase("code_cache") ||
               name.equalsIgnoreCase("app_webview") ||
               name.equalsIgnoreCase("databases") ||
               name.equalsIgnoreCase("shared_prefs") ||
               name.startsWith(".");
    }

    public void shutdown() {
        executor.shutdown();
    }
}
