package com.diamon.civil.engine;

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
        InputStream is = context.getAssets().open("materials.json");
        int size = is.available();
        byte[] buffer = new byte[size];
        is.read(buffer);
        is.close();
        String json = new String(buffer, StandardCharsets.UTF_8);
        JSONArray array = new JSONArray(json);
        materials.clear();
        for (int i = 0; i < array.length(); i++) {
            materials.add(new Material(array.getJSONObject(i)));
        }
    }

    public List<Material> getMaterials() {
        return materials;
    }

    public Material getMaterialByName(String name) {
        for (Material m : materials) {
            if (m.name.equals(name)) return m;
        }
        return null;
    }
}
