package com.diamon.civil.core.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import com.diamon.civil.solids.ui.OnHitListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.text.HtmlCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.diamon.civil.R;
import com.diamon.civil.databinding.ActivityMainBinding;
import com.diamon.civil.solids.ui.fragments.SolidFragment;
import com.diamon.civil.structural.ui.fragments.StructuralFragment;
import com.diamon.civil.terminal.ui.fragments.TerminalFragment;
import com.diamon.civil.core.util.AssetHelper;
import com.google.android.material.navigation.NavigationView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, OnHitListener {

    private ActivityMainBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AssetHelper assetHelper;
    private com.diamon.civil.core.export.ProjectExporter projectExporter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Keep CPU & screen active during FEA simulations and 3D rendering (API 24 to API 37)
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout, (v, windowInsets) -> {
            Insets statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            Insets navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.ime());

            binding.appBarLayout.setPadding(0, statusBars.top, 0, 0);
            binding.navHostFragment.setPadding(navBars.left, 0, navBars.right, navBars.bottom);

            return windowInsets;
        });

        setSupportActionBar(binding.toolbar);
        
        assetHelper = new AssetHelper(this);
        projectExporter = new com.diamon.civil.core.export.ProjectExporter(this);

        setupNavigation();
        checkAndLoadAssets();
    }

    private void setupNavigation() {
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, binding.drawerLayout, binding.toolbar,
                R.string.app_name, R.string.app_name); 
        binding.drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        binding.navView.setNavigationItemSelectedListener(this);
        
        // Initial fragment
        if (getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment) == null) {
            switchFragment(new StructuralFragment(), getString(R.string.menu_structural_analysis));
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
            switchFragment(new StructuralFragment(), getString(R.string.menu_structural_analysis));
        } else if (id == R.id.nav_3d_solid) {
            switchFragment(new SolidFragment(), getString(R.string.menu_solid_analysis));
        } else if (id == R.id.nav_terminal) {
            switchFragment(new TerminalFragment(), getString(R.string.menu_advanced_terminal));
        } else if (id == R.id.nav_disclaimer) {
            showDisclaimerDialog();
        } else if (id == R.id.nav_privacy_policy) {
            startActivity(new Intent(this, PrivacyPolicyActivity.class));
        } else if (id == R.id.nav_docs) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.dhondt.de/ccx_2.23.pdf")));
            } catch (Exception e) {
                Toast.makeText(this, R.string.toast_browser_open_error, Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.nav_about) {
            showAboutDialog();
        }

        binding.drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void showDisclaimerDialog() {
        CharSequence message = HtmlCompat.fromHtml(getString(R.string.disclaimer_dialog_message), HtmlCompat.FROM_HTML_MODE_LEGACY);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.disclaimer_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .create();
        dialog.show();
    }

    private void showAboutDialog() {
        CharSequence message = HtmlCompat.fromHtml(getString(R.string.about_dialog_message), HtmlCompat.FROM_HTML_MODE_LEGACY);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.about_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.close, null)
                .create();
        dialog.show();
        android.widget.TextView msgView = dialog.findViewById(android.R.id.message);
        if (msgView != null) {
            msgView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        }
    }

    private void showLicensesDialog() {
        CharSequence message = HtmlCompat.fromHtml(getString(R.string.licenses_dialog_message), HtmlCompat.FROM_HTML_MODE_LEGACY);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.licenses_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.close, null)
                .create();
        dialog.show();
        android.widget.TextView msgView = dialog.findViewById(android.R.id.message);
        if (msgView != null) {
            msgView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        }
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
            Fragment current = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
            if (current instanceof SolidFragment) {
                File workDir = new File(getFilesDir(), "3d_solid_analysis");
                projectExporter.exportAll(workDir, "3d_solid_analysis");
            } else if (current instanceof StructuralFragment) {
                File workDir = new File(getFilesDir(), "structural_analysis");
                projectExporter.exportAll(workDir, "structural_analysis");
            } else if (current instanceof TerminalFragment) {
                File workDir = ((TerminalFragment) current).getCurrentWorkDir();
                if (workDir == null) workDir = getFilesDir();
                projectExporter.exportAll(workDir, "terminal");
            } else {
                Toast.makeText(this, R.string.toast_no_active_module, Toast.LENGTH_SHORT).show();
            }
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
                Toast.makeText(this, R.string.toast_no_active_module, Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (id == R.id.action_import) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(intent, 1);
            return true;
        } else if (id == R.id.action_licenses) {
            showLicensesDialog();
            return true;
        } else if (id == R.id.action_disclaimer) {
            showDisclaimerDialog();
            return true;
        } else if (id == R.id.action_privacy_policy) {
            startActivity(new Intent(this, PrivacyPolicyActivity.class));
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
                        String fileName = getFileNameFromUri(uri);
                        if (fileName == null || fileName.isEmpty()) fileName = "imported_model";
                        String nameLower = fileName.toLowerCase();

                        com.diamon.civil.core.io.FileHelper fh = new com.diamon.civil.core.io.FileHelper(getContentResolver());
                        final String finalName = fileName;

                        Fragment current = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                        if (current instanceof SolidFragment) {
                            boolean isCad = SolidFragment.isSupportedCadFormat(fileName);
                            boolean isInp = nameLower.endsWith(".inp");
                            if (!isCad && !isInp) {
                                runOnUiThread(() -> Toast.makeText(this, getString(R.string.toast_unsupported_cad_format, finalName), Toast.LENGTH_LONG).show());
                                return;
                            }
                            File solidDir = new File(getFilesDir(), "3d_solid_analysis");
                            if (!solidDir.exists()) solidDir.mkdirs();
                            File targetFile = new File(solidDir, fileName);
                            if (fh.importFile(uri, targetFile)) {
                                runOnUiThread(() -> {
                                    Fragment activeFrag = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                                    if (activeFrag instanceof SolidFragment && ((SolidFragment) activeFrag).isAdded()) {
                                        if (isCad) {
                                            ((SolidFragment) activeFrag).loadGeometryFile(targetFile);
                                        } else {
                                            com.diamon.civil.core.io.FileHelper.copyFile(targetFile, new File(solidDir, "job_solid_raw.inp"));
                                            ((SolidFragment) activeFrag).onInpImported(targetFile);
                                            Toast.makeText(this, getString(R.string.toast_inp_imported_solid, finalName), Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                            }
                        } else if (current instanceof TerminalFragment) {
                            if (!nameLower.endsWith(".inp")) {
                                runOnUiThread(() -> Toast.makeText(this, R.string.toast_only_inp_supported, Toast.LENGTH_LONG).show());
                                return;
                            }
                            File terminalDir = ((TerminalFragment) current).getCurrentWorkDir();
                            if (terminalDir == null) terminalDir = getFilesDir();
                            if (!terminalDir.exists()) terminalDir.mkdirs();
                            File targetFile = new File(terminalDir, fileName);
                            if (fh.importFile(uri, targetFile)) {
                                runOnUiThread(() -> {
                                    Fragment activeFrag = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                                    if (activeFrag instanceof TerminalFragment && ((TerminalFragment) activeFrag).isAdded()) {
                                        ((TerminalFragment) activeFrag).onInpImported(targetFile);
                                    }
                                    Toast.makeText(this, getString(R.string.toast_inp_imported_terminal, finalName), Toast.LENGTH_SHORT).show();
                                });
                            }
                        } else if (current instanceof StructuralFragment) {
                            File structDir = new File(getFilesDir(), "structural_analysis");
                            if (!structDir.exists()) structDir.mkdirs();
                            File targetFile = new File(structDir, fileName);
                            if (fh.importFile(uri, targetFile)) {
                                com.diamon.civil.structural.io.AbaqusInpImporter importer = new com.diamon.civil.structural.io.AbaqusInpImporter();
                                com.diamon.civil.structural.engine.StructuralModel model = importer.importInp(targetFile);
                                runOnUiThread(() -> {
                                    Fragment activeFrag = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                                    if (activeFrag instanceof StructuralFragment && ((StructuralFragment) activeFrag).isAdded()) {
                                        ((StructuralFragment) activeFrag).loadModel(model);
                                    }
                                    Toast.makeText(this, getString(R.string.toast_inp_imported_structural, model.nodes.size(), model.elements.size()), Toast.LENGTH_SHORT).show();
                                });
                            }
                        } else {
                            File targetFile = new File(getFilesDir(), fileName);
                            if (fh.importFile(uri, targetFile)) {
                                runOnUiThread(() -> Toast.makeText(this, getString(R.string.toast_inp_imported_terminal, finalName), Toast.LENGTH_SHORT).show());
                            }
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, getString(R.string.toast_import_failed, e.getMessage()), Toast.LENGTH_LONG).show());
                    }
                });
            }
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignore) {}
        }
        if (result == null && uri.getPath() != null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result;
    }

    private void resetAssets() {
        getSharedPreferences("AssetHelperPrefs", MODE_PRIVATE).edit().clear().apply();
        recreate();
    }

    private void checkAndLoadAssets() {
        binding.layoutLoading.setVisibility(View.VISIBLE);
        binding.tvLoadingText.setText(R.string.deploying_engine);
        executor.execute(() -> {
            com.diamon.civil.core.util.NativeLoader.setFilesDir(getFilesDir());
            boolean assetsOk = assetHelper.ensureRuntimeReady();
            
            // Pre-load native libraries to avoid freeze in fragments
            runOnUiThread(() -> binding.tvLoadingText.setText(R.string.initializing_native_modules));
            try {
                com.diamon.civil.structural.engine.NativeFeaCore.loadLibraries();
            } catch (Throwable e) {
                android.util.Log.e("MainActivity", "Failed to load libraries: " + e.getMessage());
                com.diamon.civil.core.util.logging.ModuleLogger.getGlobal().error("CRITICAL: Failed to load JNI libraries in MainActivity", e);
            }
            
            runOnUiThread(() -> {
                binding.layoutLoading.setVisibility(View.GONE);
                if (!assetsOk) {
                    Toast.makeText(this, R.string.engine_failure_assets, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @Override
    public void onHit(Object info) {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (current instanceof SolidFragment) {
            ((SolidFragment) current).onHit(info);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (projectExporter != null) projectExporter.shutdown();
    }
}
