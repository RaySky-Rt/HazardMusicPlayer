package com.hazard.music.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Album {

    public final long id;
    public final String name;
    public final String artist;
    public final String coverUrl;
    public final int size;

    public Album(long id, String name, String artist, String coverUrl, int size) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.artist = artist == null ? "" : artist;
        this.coverUrl = coverUrl == null ? "" : coverUrl;
        this.size = size;
    }

    public static Album from(JSONObject o) {
        if (o == null) return null;
        long id = o.optLong("id", 0);
        if (id <= 0) return null;
        String artist = "";
        JSONObject a = o.optJSONObject("artist");
        if (a != null) artist = a.optString("name", "");
        if (artist.isEmpty()) {
            JSONArray artists = o.optJSONArray("artists");
            if (artists != null && artists.length() > 0) {
                JSONObject first = artists.optJSONObject(0);
                if (first != null) artist = first.optString("name", "");
            }
        }
        return new Album(id, o.optString("name", ""), artist, o.optString("picUrl", ""),
                o.optInt("size", 0));
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("artist", artist);
            o.put("cover", coverUrl);
            o.put("size", size);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static List<Album> listFromJson(String raw) {
        List<Album> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                Album album = new Album(o.optLong("id"), o.optString("name"), o.optString("artist"),
                        o.optString("cover"), o.optInt("size"));
                if (album.id > 0) out.add(album);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static String listToJson(List<Album> albums) {
        JSONArray array = new JSONArray();
        if (albums != null) for (Album a : albums) array.put(a.toJson());
        return array.toString();
    }

    public static List<Album> list(JSONArray array) {
        List<Album> out = new ArrayList<>();
        if (array == null) return out;
        for (int i = 0; i < array.length(); i++) {
            Album album = from(array.optJSONObject(i));
            if (album != null) out.add(album);
        }
        return out;
    }
}
