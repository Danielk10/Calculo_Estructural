package com.diamon.civil.test;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.diamon.civil.ui.MainActivity;
import com.diamon.civil.R;
import com.diamon.civil.engine.NativeFeaCore;

import com.google.android.material.navigation.NavigationView;

public class AutoTester {
    private static final String TAG = "AutoTester";

    public static void run(final MainActivity activity) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "Starting automatic tests...");
            Toast.makeText(activity, "Running Automatic Tests...", Toast.LENGTH_SHORT).show();
            
            try {
                // Check library status
                checkLibraryStatus();

                // Test 1: Structural Analysis Module
                runStructuralTest(activity);
                
                // Delay between tests
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        // Test 2: 3D Solid / Basic Analysis Module
                        run3DSolidTest(activity);
                    } catch (Throwable t) {
                        Log.e(TAG, "FATAL ERROR in 3D Solid Test: " + t.getMessage(), t);
                    }
                }, 10000); // Increased delay to 10s
                
            } catch (Throwable t) {
                Log.e(TAG, "FATAL ERROR in Structural Test: " + t.getMessage(), t);
            }
            
        }, 3000); // 3 seconds delay
    }

    private static void checkLibraryStatus() {
        NativeFeaCore.loadLibraries();
        Log.d(TAG, "JNI libraries ready: " + NativeFeaCore.isLibrariesLoaded());
    }

    private static void runStructuralTest(MainActivity activity) {
        Log.d(TAG, "Testing Structural Module...");
        activity.runOnUiThread(() -> {
            // Switch to structural module via NavigationView
            NavigationView navView = activity.findViewById(R.id.nav_view);
            if (navView != null) {
                activity.onNavigationItemSelected(navView.getMenu().findItem(R.id.nav_structural));
            }
            
            // Populate data for a simple beam
            String nodes = "1, 0.0, 0.0, 0.0\n2, 5.0, 0.0, 0.0";
            String elements = "1, 1, 2";
            
            android.widget.EditText etNodes = activity.findViewById(com.diamon.civil.R.id.etNodes);
            android.widget.EditText etElements = activity.findViewById(com.diamon.civil.R.id.etElements);
            android.widget.Button btnSolve = activity.findViewById(com.diamon.civil.R.id.btnSolveStructural);
            
            if (etNodes != null) etNodes.setText(nodes);
            if (etElements != null) etElements.setText(elements);
            
            Log.d(TAG, "Clicking Solve Structural...");
            if (btnSolve != null) btnSolve.performClick();
        });
    }

    private static void run3DSolidTest(MainActivity activity) {
        Log.d(TAG, "Testing 3D Solid / Basic Module...");
        activity.runOnUiThread(() -> {
            // Switch to 3D Solid module via NavigationView
            NavigationView navView = activity.findViewById(R.id.nav_view);
            if (navView != null) {
                activity.onNavigationItemSelected(navView.getMenu().findItem(R.id.nav_3d_solid));
            }
            
            // Set parameters
            android.widget.EditText etModulus = activity.findViewById(com.diamon.civil.R.id.etSolidModulus);
            android.widget.Button btnRun = activity.findViewById(com.diamon.civil.R.id.btnRunSolidAnalysis);

            if (etModulus != null) etModulus.setText("210000");
            
            Log.d(TAG, "Clicking Run Analysis...");
            if (btnRun != null) btnRun.performClick();
        });
    }
}
