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
import com.diamon.civil.structural.engine.StructuralModel;

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

        public Material(String name, double E, double nu, double rho, double yieldStrength, double fc) {
            this.name = name;
            this.E = E;
            this.nu = nu;
            this.rho = rho;
            this.yieldStrength = yieldStrength;
            this.fc = fc;
        }

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

    public MaterialDatabase() {
        populateDefaults();
    }

    private void populateDefaults() {
        materials.clear();
        materials.add(new Material("Structural Steel A36", 200000.0, 0.30, 7850.0, 250.0, 0.0));
        materials.add(new Material("Structural Steel A572 Gr50", 200000.0, 0.30, 7850.0, 345.0, 0.0));
        materials.add(new Material("Structural Steel S275", 200000.0, 0.30, 7850.0, 275.0, 0.0));
        materials.add(new Material("Structural Steel S355", 210000.0, 0.30, 7850.0, 355.0, 0.0));
        materials.add(new Material("Normal Weight Concrete 25MPa", 23500.0, 0.20, 2400.0, 0.0, 25.0));
        materials.add(new Material("Normal Weight Concrete 30MPa", 25700.0, 0.20, 2400.0, 0.0, 30.0));
        materials.add(new Material("Aluminum 6061-T6", 68900.0, 0.33, 2700.0, 276.0, 0.0));
        materials.add(new Material("Structural Timber / Wood", 12400.0, 0.29, 530.0, 50.0, 0.0));
    }

    public void loadFromAssets(Context context) throws IOException, JSONException {
        if (context == null) return;
        try (InputStream is = context.getAssets().open("materials.json");
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            String json = baos.toString(StandardCharsets.UTF_8.name());
            JSONArray array = new JSONArray(json);
            List<Material> loaded = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                loaded.add(new Material(array.getJSONObject(i)));
            }
            if (!loaded.isEmpty()) {
                materials.clear();
                materials.addAll(loaded);
            }
        }
    }

    /**
     * Adds a custom user-defined material to the database.
     * If a material with the same name already exists, it will be replaced.
     */
    public void addCustomMaterial(String name, double E, double nu, double rho, double yieldStrength, double fc) {
        // Remove existing material with same name if any
        materials.removeIf(m -> m.name.equalsIgnoreCase(name));
        materials.add(new Material(name, E, nu, rho, yieldStrength, fc));
    }

    /**
     * Removes a material by name.
     * @return true if the material was found and removed
     */
    public boolean removeMaterial(String name) {
        return materials.removeIf(m -> m.name.equalsIgnoreCase(name));
    }

    /**
     * Loads custom materials from a StructuralModel's custom material list.
     * These are added to the existing materials without clearing defaults.
     */
    public void loadCustomMaterials(List<StructuralModel.CustomMaterial> customMaterials) {
        if (customMaterials == null) return;
        for (StructuralModel.CustomMaterial cm : customMaterials) {
            if (cm.name != null && !cm.name.trim().isEmpty()) {
                addCustomMaterial(cm.name, cm.E, cm.nu, cm.rho, cm.yieldStrength, cm.fc);
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
