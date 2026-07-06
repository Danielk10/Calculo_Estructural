package com.diamon.civil.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.diamon.civil.databinding.FragmentTerminalBinding;
import com.diamon.civil.engine.CalculixExecutor;
import com.diamon.civil.engine.TerminalCommandExecutor;
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
        
        terminalExecutor = new TerminalCommandExecutor(requireContext().getFilesDir());
        calculixExecutor = new CalculixExecutor(requireContext());

        binding.btnSend.setOnClickListener(v -> sendCommand());
        binding.etCommand.setOnEditorActionListener((v, actionId, event) -> {
            sendCommand();
            return true;
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
