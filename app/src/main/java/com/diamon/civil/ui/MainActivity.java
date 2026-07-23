package com.diamon.civil.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.diamon.civil.R;
import com.diamon.civil.databinding.ActivityMainBinding;
import com.diamon.civil.ui.fragments.SolidFragment;
import com.diamon.civil.ui.fragments.StructuralFragment;
import com.diamon.civil.ui.fragments.TerminalFragment;
import com.diamon.civil.util.AssetHelper;
import com.diamon.civil.util.export.ExportManager;
import com.google.android.material.navigation.NavigationView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, OnHitListener {

    private ActivityMainBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AssetHelper assetHelper;
    private com.diamon.civil.util.export.ProjectExporter projectExporter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        
        assetHelper = new AssetHelper(this);
        projectExporter = new com.diamon.civil.util.export.ProjectExporter(this);

        setupNavigation();
        checkAndLoadAssets();
    }

    private void setupNavigation() {
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, binding.drawerLayout, binding.toolbar,
                R.string.app_name, R.string.app_name); 
        binding.drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        binding.navView.setNavigationItemSelectedListener(this);
        
        // Initial fragment
        if (getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment) == null) {
            switchFragment(new StructuralFragment(), "Structural Analysis");
        }
    }

    private void switchFragment(Fragment fragment, String title) {
        if (isFinishing() || isDestroyed()) return;
        
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .commitAllowingStateLoss();
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_structural) {
            switchFragment(new StructuralFragment(), "Structural Analysis");
        } else if (id == R.id.nav_3d_solid) {
            switchFragment(new SolidFragment(), "3D Solid Analysis");
        } else if (id == R.id.nav_terminal) {
            switchFragment(new TerminalFragment(), "Advanced Terminal");
        } else if (id == R.id.nav_docs) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.dhondt.de/ccx_2.23.pdf")));
            } catch (Exception e) {
                Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.nav_about) {
            showAboutDialog();
        }

        binding.drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("About Structural Analysis FEA Advanced")
                .setMessage("Structural & 3D Solid Analysis\nPowered by CalculiX & GMSH\n\nDeveloped by Daniel Diamon")
                .setPositiveButton("Close", null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_export_all) {
            projectExporter.exportAll(getFilesDir());
            return true;
        } else if (id == R.id.action_export) {
            Fragment current = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
            if (current instanceof SolidFragment) {
                ((SolidFragment) current).exportResults();
            } else if (current instanceof StructuralFragment) {
                ((StructuralFragment) current).exportResults();
            } else if (current instanceof TerminalFragment) {
                ((TerminalFragment) current).exportResults();
            } else {
                Toast.makeText(this, "No module active to export", Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (id == R.id.action_import) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(intent, 1);
            return true;
        } else if (id == R.id.action_docs_calculix) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.dhondt.de/ccx_2.23.pdf")));
            return true;
        } else if (id == R.id.action_docs_gmsh) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://gmsh.info/doc/texinfo/gmsh.html")));
            return true;
        } else if (id == R.id.action_docs_occt) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://dev.opencascade.org/doc/overview/html/index.html")));
            return true;
        } else if (id == R.id.action_licenses) {
            new AlertDialog.Builder(this)
                .setTitle("Licenses")
                .setMessage("CalculiX - GPLv2\nGmsh - GPLv2 or later\nOpenCASCADE - LGPLv2.1")
                .setPositiveButton("Close", null)
                .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                executor.execute(() -> {
                    try {
                        File tempFile = new File(getFilesDir(), "imported.inp");
                        com.diamon.civil.io.FileHelper fh = new com.diamon.civil.io.FileHelper(getContentResolver());
                        if (fh.importFile(uri, tempFile)) {
                            com.diamon.civil.io.AbaqusInpImporter importer = new com.diamon.civil.io.AbaqusInpImporter();
                            com.diamon.civil.engine.StructuralModel model = importer.importInp(tempFile);
                            runOnUiThread(() -> {
                                Toast.makeText(this, "Imported " + model.nodes.size() + " nodes", Toast.LENGTH_SHORT).show();
                                switchFragment(new StructuralFragment(), "Structural Analysis");
                            });
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "Import Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            }
        }
    }

    private void resetAssets() {
        getSharedPreferences("AssetHelperPrefs", MODE_PRIVATE).edit().clear().apply();
        recreate();
    }

    private void checkAndLoadAssets() {
        binding.layoutLoading.setVisibility(View.VISIBLE);
        binding.tvLoadingText.setText("Deploying FEM Core Engine...");
        executor.execute(() -> {
            com.diamon.civil.util.NativeLoader.setFilesDir(getFilesDir());
            boolean assetsOk = assetHelper.ensureRuntimeReady();
            
            // Pre-load native libraries to avoid freeze in fragments
            runOnUiThread(() -> binding.tvLoadingText.setText("Initializing Native Modules..."));
            try {
                com.diamon.civil.engine.NativeFeaCore.loadLibraries();
            } catch (Throwable e) {
                android.util.Log.e("MainActivity", "Failed to load libraries: " + e.getMessage());
                com.diamon.civil.util.logging.ModuleLogger.getGlobal().error("CRITICAL: Failed to load JNI libraries in MainActivity", e);
            }
            
            runOnUiThread(() -> {
                binding.layoutLoading.setVisibility(View.GONE);
                if (!assetsOk) {
                    Toast.makeText(this, "Engine Failure: Assets could not be deployed", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @Override
    public void onHit(Object info) {
        // Forward hit events to the current fragment if needed
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (projectExporter != null) projectExporter.shutdown();
    }

    @Override
    public void onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
