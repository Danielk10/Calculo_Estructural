package com.diamon.civil.test.simulation;

import java.io.*;
import java.util.*;

public class InpAssembler {
    public static void assemble(File workDir, String inputName) throws IOException {
        File rawInp = new File(workDir, inputName + "_raw.inp");
        File cleanInp = new File(workDir, inputName + "_clean.inp");
        File nsetsInp = new File(workDir, "nsets.inp");
        File finalInp = new File(workDir, inputName + ".inp");

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(rawInp))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        }

        // 1. Extraer NSETs
        Set<Integer> fixedNodes = extractNodes(lines, "SURFACE1");
        Set<Integer> loadedNodes = extractNodes(lines, "SURFACE2");
        android.util.Log.d("InpAssembler", "Fixed nodes: " + fixedNodes.size() + ", Loaded nodes: " + loadedNodes.size());

        try (PrintWriter pw = new PrintWriter(new FileWriter(nsetsInp))) {
            pw.println("*NSET, NSET=NFix");
            writeNodes(pw, fixedNodes);
            pw.println("*NSET, NSET=NLoad");
            writeNodes(pw, loadedNodes);
        }

        // 2. Limpiar elementos CPS3
        try (PrintWriter pw = new PrintWriter(new FileWriter(cleanInp))) {
            boolean skip = false;
            for (String line : lines) {
                String u = line.trim().toUpperCase();
                if (u.startsWith("*ELEMENT") && u.contains("TYPE=CPS3")) {
                    skip = true;
                    continue;
                }
                if (skip && u.startsWith("*") && !u.contains("TYPE=CPS3")) skip = false;
                if (skip) continue;
                pw.println(line);
            }
        }

        // 3. Ensamblar final
        try (PrintWriter pw = new PrintWriter(new FileWriter(finalInp))) {
            pw.println("*INCLUDE, INPUT=" + cleanInp.getAbsolutePath());
            pw.println("*INCLUDE, INPUT=" + nsetsInp.getAbsolutePath());
            pw.println("*MATERIAL, NAME=STEEL");
            pw.println("*ELASTIC");
            pw.println("210000, 0.3");
            pw.println("*SOLID SECTION, ELSET=Volume1, MATERIAL=STEEL");
            pw.println("*STEP");
            pw.println("*STATIC");
            pw.println("*BOUNDARY");
            pw.println("NFix, 1, 3");
            pw.println("*CLOAD");
            pw.println("NLoad, 2, -100");
            pw.println("*NODE FILE");
            pw.println("U");
            pw.println("*EL FILE");
            pw.println("S");
            pw.println("*END STEP");
        }
    }

    private static Set<Integer> extractNodes(List<String> lines, String setName) {
        Set<Integer> nodes = new TreeSet<>();
        boolean capture = false;
        for (String line : lines) {
            String u = line.trim().toUpperCase();
            if (u.startsWith("*ELEMENT")) {
                capture = u.contains("ELSET=" + setName);
                continue;
            }
            if (capture) {
                if (u.startsWith("*")) {
                    capture = false;
                    continue;
                }
                String[] parts = line.trim().split(",");
                for (int i = 1; i < parts.length; i++) {
                    if (!parts[i].trim().isEmpty()) nodes.add(Integer.parseInt(parts[i].trim()));
                }
            }
        }
        return nodes;
    }

    private static void writeNodes(PrintWriter pw, Set<Integer> nodes) {
        int count = 0;
        for (Integer node : nodes) {
            pw.print(node + ",");
            if (++count % 10 == 0) pw.println();
        }
        pw.println();
    }
}
