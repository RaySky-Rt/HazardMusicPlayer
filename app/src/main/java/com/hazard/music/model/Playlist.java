package com.hazard.music.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Playlist {

    public final long id;
    public final String name;
    public final String coverUrl;
    public final int trackCount;
    public final int specialType;
    public final boolean subscribed;
    public final String creatorName;
    public final long creatorId;

    public Playlist(long id, String name, String coverUrl, int trackCount, int specialType) {
        this(id, name, coverUrl, trackCount, specialType, false, "", 0);
    }

    public Playlist(long id, String name, String coverUrl, int trackCount, int specialType,
                    boolean subscribed, String creatorName, long creatorId) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.coverUrl = coverUrl == null ? "" : coverUrl;
        this.trackCount = trackCount;
        this.specialType = specialType;
        this.subscribed = subscribed;
        this.creatorName = creatorName == null ? "" : creatorName;
        this.creatorId = creatorId;
    }

    public boolean isCreatedBy(long uid) {
        return !subscribed && (creatorId <= 0 || creatorId == uid);
    }

    public static Playlist from(JSONObject o) {
        if (o == null) return null;
        long id = o.optLong("id", 0);
        if (id <= 0) return null;
        Object sub = o.opt("subscribed");
        boolean subscribed = (sub instanceof Boolean && (Boolean) sub)
                || (sub instanceof Number && ((Number) sub).intValue() == 1)
                || (sub instanceof String && Boolean.parseBoolean((String) sub));
        JSONObject creator = o.optJSONObject("creator");
        String creatorName = creator == null ? "" : creator.optString("nickname", "");
        long creatorId = creator == null ? 0 : creator.optLong("userId", 0);
        return new Playlist(id,
                o.optString("name", ""),
                o.optString("coverImgUrl", o.optString("picUrl", "")),
                o.optInt("trackCount", 0),
                o.optInt("specialType", 0),
                subscribed, creatorName, creatorId);
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("cover", coverUrl);
            o.put("count", trackCount);
            o.put("special", specialType);
            o.put("subscribed", subscribed);
            o.put("creatorName", creatorName);
            o.put("creatorId", creatorId);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static List<Playlist> listFromJson(String raw) {
        List<Playlist> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                Playlist p = new Playlist(o.optLong("id"), o.optString("name"), o.optString("cover"),
                        o.optInt("count"), o.optInt("special"),
                        o.optBoolean("subscribed"), o.optString("creatorName"), o.optLong("creatorId"));
                if (p.id > 0) out.add(p);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static String listToJson(List<Playlist> playlists) {
        JSONArray array = new JSONArray();
        if (playlists != null) for (Playlist p : playlists) array.put(p.toJson());
        return array.toString();
    }

    public static List<Playlist> list(JSONArray array) {
        List<Playlist> out = new ArrayList<>();
        if (array == null) return out;
        for (int i = 0; i < array.length(); i++) {
            Playlist playlist = from(array.optJSONObject(i));
            if (playlist != null) out.add(playlist);
        }
        return out;
    }
}
