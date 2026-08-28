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

    public void loadFromAssets(Context context) throws IOException, JSONException {
        try (InputStream is = context.getAssets().open("sections.json");
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            String json = baos.toString(StandardCharsets.UTF_8.name());
            JSONArray array = new JSONArray(json);
            sections.clear();
            for (int i = 0; i < array.length(); i++) {
                sections.add(new Section(array.getJSONObject(i)));
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
