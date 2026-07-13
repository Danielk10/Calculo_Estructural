package com.diamon.civil.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.widget.Toast;
import com.diamon.civil.databinding.FragmentTerminalBinding;
import com.diamon.civil.engine.CalculixExecutor;
import com.diamon.civil.engine.TerminalCommandExecutor;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TerminalFragment extends Fragment {

    private FragmentTerminalBinding binding;
    private TerminalCommandExecutor terminalExecutor;
    private CalculixExecutor calculixExecutor;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentTerminalBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        final android.content.Context appContext = requireContext().getApplicationContext();
        terminalExecutor = new TerminalCommandExecutor(appContext.getFilesDir());
        
        executor.execute(() -> {
            try {
                calculixExecutor = new CalculixExecutor(appContext);
            } catch (Exception e) {
                // Log error if needed
            }
        });

        binding.btnSend.setOnClickListener(v -> sendCommand());
        binding.etCommand.setOnEditorActionListener((v, actionId, event) -> {
            sendCommand();
            return true;
        });

        binding.btnCopyLog.setOnClickListener(v -> copyLogToClipboard());
        binding.btnExportReport.setOnClickListener(v -> exportTerminalReport());
        binding.tvLog.setOnClickListener(v -> copyLogToClipboard());
    }

    private void copyLogToClipboard() {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("FEA Terminal Log", binding.tvLog.getText().toString());
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Log copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportTerminalReport() {
        executor.execute(() -> {
            try {
                File workDir = requireContext().getFilesDir();
                File reportFile = new File(workDir, "terminal_report.txt");
                try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(reportFile))) {
                    pw.println("FEA CORE TERMINAL SESSION REPORT");
                    pw.println("Generated: " + new java.util.Date().toString());
                    pw.println("----------------------------------");
                    pw.println(binding.tvLog.getText().toString());
                }
                
                com.diamon.civil.util.export.ExportManager manager = new com.diamon.civil.util.export.ExportManager(requireContext());
                if (manager.exportToDownloads(reportFile, "Terminal")) {
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Report exported to Downloads/FEA_Suite/Terminal", Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Export Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void sendCommand() {
        String input = binding.etCommand.getText().toString().trim();
        if (input.isEmpty()) return;

        binding.etCommand.setText("");
        if (input.equalsIgnoreCase("clear")) {
            binding.tvLog.setText("--- FEA Terminal Core ---\n");
            return;
        }

        binding.tvLog.append("\n$ " + input + "\n");
        scrollDown();

        executor.execute(() -> {
            String result = terminalExecutor.execute(input);
            if (result == null) {
                // Delegate to binary execution if command not built-in
                String[] parts = input.split("\\s+");
                String binary = parts[0];
                String[] args = new String[parts.length - 1];
                System.arraycopy(parts, 1, args, 0, args.length);
                
                if (binary.equalsIgnoreCase("gmsh")) {
                    result = calculixExecutor.executeBinary("gmsh", args);
                } else if (binary.equalsIgnoreCase("ccx")) {
                    result = calculixExecutor.executeBinary("ccx", args);
                } else {
                    result = calculixExecutor.executeBinary(binary, args);
                }
            }
            final String finalResult = result;
            getActivity().runOnUiThread(() -> {
                binding.tvLog.append(finalResult + "\n");
                scrollDown();
            });
        });
    }

    private void scrollDown() {
        binding.scrollLog.post(() -> binding.scrollLog.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdown();
        binding = null;
    }
}
