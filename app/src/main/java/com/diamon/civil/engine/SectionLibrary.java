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
        InputStream is = context.getAssets().open("sections.json");
        int size = is.available();
        byte[] buffer = new byte[size];
        is.read(buffer);
        is.close();
        String json = new String(buffer, StandardCharsets.UTF_8);
        JSONArray array = new JSONArray(json);
        sections.clear();
        for (int i = 0; i < array.length(); i++) {
            sections.add(new Section(array.getJSONObject(i)));
        }
    }

    public List<Section> getSections() {
        return sections;
    }

    public Section getSectionByName(String name) {
        for (Section s : sections) {
            if (s.name.equals(name)) return s;
        }
        return null;
    }
}
