package com.diamon.civil.structural.engine;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * B2: SectionLibrary — Manages the library of structural cross-sections.
 */
public class SectionLibrary {
    public static class Section {
        public String name;
        public String type;
        public double h, b, tf, tw, t, d;
        public double A, Iy, Iz, J;

        public Section(String name, String type, double h, double b, double tf, double tw, double t, double d, double A, double Iy, double Iz, double J) {
            this.name = name;
            this.type = type;
            this.h = h;
            this.b = b;
            this.tf = tf;
            this.tw = tw;
            this.t = t;
            this.d = d;
            this.A = A;
            this.Iy = Iy;
            this.Iz = Iz;
            this.J = J;
        }

        public Section(JSONObject obj) throws JSONException {
            name = obj.getString("name");
            type = obj.getString("type");
            h = obj.optDouble("h", 0);
            b = obj.optDouble("b", 0);
            tf = obj.optDouble("tf", 0);
            tw = obj.optDouble("tw", 0);
            t = obj.optDouble("t", 0);
            d = obj.optDouble("d", 0);
            A = obj.optDouble("A", 0);
            Iy = obj.optDouble("Iy", 0);
            Iz = obj.optDouble("Iz", 0);
            J = obj.optDouble("J", 0);
        }
    }

    private final List<Section> sections = new ArrayList<>();

    public SectionLibrary() {
        populateDefaults();
    }

    private void populateDefaults() {
        sections.clear();
        sections.add(new Section("HEB200", "Wide Flange / I-Beam", 200.0, 200.0, 15.0, 9.0, 0.0, 0.0, 7810.0, 20030000.0, 56960000.0, 593000.0));
        sections.add(new Section("HEB300", "Wide Flange / I-Beam", 300.0, 300.0, 19.0, 11.0, 0.0, 0.0, 14910.0, 85630000.0, 251700000.0, 1850000.0));
        sections.add(new Section("IPE200", "Standard I-Beam", 200.0, 100.0, 8.5, 5.6, 0.0, 0.0, 2850.0, 1420000.0, 19430000.0, 69800.0));
        sections.add(new Section("IPE300", "Standard I-Beam", 300.0, 150.0, 10.7, 7.1, 0.0, 0.0, 5380.0, 6040000.0, 83560000.0, 201000.0));
        sections.add(new Section("IPE400", "Standard I-Beam", 400.0, 180.0, 13.5, 8.6, 0.0, 0.0, 8450.0, 16340000.0, 231300000.0, 514000.0));
        sections.add(new Section("W8x31", "Wide Flange Column", 203.2, 203.2, 11.0, 7.2, 0.0, 0.0, 5890.0, 15400000.0, 45800000.0, 221000.0));
        sections.add(new Section("W12x50", "Wide Flange Girder", 310.0, 205.0, 16.3, 9.4, 0.0, 0.0, 9480.0, 23400000.0, 164000000.0, 579000.0));
        sections.add(new Section("L100x10", "Equal Angle L-Shape", 100.0, 100.0, 0.0, 0.0, 10.0, 0.0, 1920.0, 1770000.0, 1770000.0, 63600.0));
        sections.add(new Section("HSS 100x100x6", "Hollow Structural Tube", 100.0, 100.0, 0.0, 0.0, 6.0, 0.0, 2210.0, 3290000.0, 3290000.0, 5310000.0));
        sections.add(new Section("Circular Pipe D200x10", "Circular Pipe / Tube", 0.0, 0.0, 0.0, 0.0, 10.0, 200.0, 5970.0, 26400000.0, 26400000.0, 52800000.0));
        sections.add(new Section("Rect 300x400", "Rectangular Concrete 300x400", 400.0, 300.0, 0.0, 0.0, 0.0, 0.0, 120000.0, 900000000.0, 1600000000.0, 1430000000.0));
        sections.add(new Section("Rect 200x300", "Rectangular Section 200x300", 300.0, 200.0, 0.0, 0.0, 0.0, 0.0, 60000.0, 200000000.0, 450000000.0, 360000000.0));
        sections.add(new Section("Rect 150x300", "Rectangular Section 150x300", 300.0, 150.0, 0.0, 0.0, 0.0, 0.0, 45000.0, 112500000.0, 337500000.0, 220000000.0));
    }

    public void loadFromAssets(Context context) throws IOException, JSONException {
        if (context == null) return;
        try (InputStream is = context.getAssets().open("sections.json");
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            String json = baos.toString(StandardCharsets.UTF_8.name());
            JSONArray array = new JSONArray(json);
            List<Section> loaded = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                loaded.add(new Section(array.getJSONObject(i)));
            }
            if (!loaded.isEmpty()) {
                sections.clear();
                sections.addAll(loaded);
            }
        }
    }

    public List<Section> getSections() {
        return sections;
    }

    public Section getSectionByName(String name) {
        if (name == null || name.trim().isEmpty()) return !sections.isEmpty() ? sections.get(0) : null;
        for (Section s : sections) {
            if (s.name.equalsIgnoreCase(name)) return s;
        }
        String cleanQuery = name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(java.util.Locale.US);
        for (Section s : sections) {
            String cleanS = s.name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(java.util.Locale.US);
            if (cleanS.equals(cleanQuery) || cleanS.contains(cleanQuery) || cleanQuery.contains(cleanS)) return s;
        }
        return null;
    }
}
