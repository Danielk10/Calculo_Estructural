package com.diamon.civil.solids.engine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for CalculiX .frd files in the context of 3D Solids displacements.
 * Relocated to keep the solids module independent.
 */
public class SolidDisplacementFrdParser {
    public static String parseAndSummarize(File frdFile) {
        Map<Integer, List<Double>> results = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(frdFile))) {
            String line;
            boolean capture = false;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains("-4  DISP")) {
                    capture = true;
                    continue;
                }
                if (capture && line.startsWith(" -3")) {
                    break;
                }
                if (capture && line.startsWith(" -1")) {
                    if (line.length() >= 13) {
                        int nodeId = Integer.parseInt(line.substring(3, 13).trim());
                        List<Double> values = new ArrayList<>();
                        for (int i = 13; i < line.length(); i += 12) {
                            int end = Math.min(i + 12, line.length());
                            String chunk = line.substring(i, end).trim();
                            if (!chunk.isEmpty()) {
                                try {
                                    values.add(Double.parseDouble(chunk));
                                } catch (NumberFormatException e) {
                                    break;
                                }
                            }
                        }
                        if (!values.isEmpty()) {
                            results.put(nodeId, values);
                        }
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            return "Error parsing FRD: " + e.getMessage();
        }

        if (results.isEmpty()) return "WARNING: no displacements were extracted.";

        int maxNode = -1;
        double maxDispSq = -1.0;
        for (Map.Entry<Integer, List<Double>> entry : results.entrySet()) {
            double dispSq = 0;
            for (double v : entry.getValue()) dispSq += v * v;
            if (dispSq > maxDispSq) {
                maxDispSq = dispSq;
                maxNode = entry.getKey();
            }
        }
        
        return "Nodes with displacement: " + results.size() + 
               "\nNode with maximum displacement: " + maxNode + " -> " + results.get(maxNode);
    }
}
