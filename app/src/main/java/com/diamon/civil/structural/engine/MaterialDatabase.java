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
 * C4: MaterialDatabase — Manages the library of materials.
 */
public class MaterialDatabase {
    public static class Material {
        public String name;
        public double E;      // Young's Modulus (MPa)
        public double nu;     // Poisson's Ratio
        public double rho;    // Density (kg/m3)
        public double yieldStrength; // MPa
        public double fc;     // Compressive strength for concrete (MPa)

        public Material(JSONObject obj) throws JSONException {
            name = obj.getString("name");
            E = obj.getDouble("E");
            nu = obj.getDouble("nu");
            rho = obj.getDouble("rho");
            yieldStrength = obj.optDouble("yield_strength", 0);
            fc = obj.optDouble("f_c", 0);
        }
    }

    private final List<Material> materials = new ArrayList<>();

    public void loadFromAssets(Context context) throws IOException, JSONException {
        try (InputStream is = context.getAssets().open("materials.json");
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            String json = baos.toString(StandardCharsets.UTF_8.name());
            JSONArray array = new JSONArray(json);
            materials.clear();
            for (int i = 0; i < array.length(); i++) {
                materials.add(new Material(array.getJSONObject(i)));
            }
        }
    }

    public List<Material> getMaterials() {
        return materials;
    }

    public Material getMaterialByName(String name) {
        if (name == null || name.trim().isEmpty()) return !materials.isEmpty() ? materials.get(0) : null;
        for (Material m : materials) {
            if (m.name.equalsIgnoreCase(name)) return m;
        }
        String cleanQuery = name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(java.util.Locale.US);
        for (Material m : materials) {
            String cleanM = m.name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(java.util.Locale.US);
            if (cleanM.equals(cleanQuery) || cleanM.contains(cleanQuery) || cleanQuery.contains(cleanM)) return m;
        }
        if (cleanQuery.contains("concrete")) {
            for (Material m : materials) {
                if (m.name.toLowerCase(java.util.Locale.US).contains("concrete")) return m;
            }
        }
        if (cleanQuery.contains("steel")) {
            for (Material m : materials) {
                if (m.name.toLowerCase(java.util.Locale.US).contains("steel")) return m;
            }
        }
        return !materials.isEmpty() ? materials.get(0) : null;
    }
}
