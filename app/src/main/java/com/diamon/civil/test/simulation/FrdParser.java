package com.diamon.civil.test.simulation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FrdParser {
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
                        for (int i = 13; i + 12 <= line.length(); i += 12) {
                            String chunk = line.substring(i, i + 12).trim();
                            if (!chunk.isEmpty()) {
                                try {
                                    values.add(Double.parseDouble(chunk));
                                } catch (NumberFormatException e) {
                                    break;
                                }
                            }
                        }
                        results.put(nodeId, values);
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            return "Error parsing FRD: " + e.getMessage();
        }

        if (results.isEmpty()) return "ADVERTENCIA: no se extrajeron desplazamientos.";

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
        
        return "Nodos con desplazamiento: " + results.size() + 
               "\nNodo con mayor desplazamiento: " + maxNode + " -> " + results.get(maxNode);
    }
}
