package com.diamon.civil.util.export;

import android.content.Context;
import android.widget.Toast;
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

    public void exportAll(File workDir, String subFolder) {
        executor.execute(() -> {
            File[] files = workDir.listFiles();
            int count = 0;
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && !f.getName().startsWith(".") && exportManager.exportToDownloads(f, subFolder)) {
                        count++;
                    }
                }
            }
            final int finalCount = count;
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).runOnUiThread(() -> 
                    Toast.makeText(context, "Exported " + finalCount + " files to FEA_Suite/" + subFolder, Toast.LENGTH_LONG).show());
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}
