package com.hazard.music.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Song {

    public final long id;
    public final String name;
    public final String artist;
    public final String album;
    public final String picUrl;
    public final long durationMs;
    public final int fee;

    public boolean liked;
    public String localPath;

    public Song(long id, String name, String artist, String album, String picUrl, long durationMs, int fee) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.artist = artist == null ? "" : artist;
        this.album = album == null ? "" : album;
        this.picUrl = picUrl == null ? "" : picUrl;
        this.durationMs = durationMs;
        this.fee = fee;
    }

    public static Song from(JSONObject o) {
        if (o == null) return null;
        long id = o.optLong("id", 0);
        if (id <= 0) return null;
        String name = o.optString("name", "");

        JSONArray artists = o.optJSONArray("ar");
        if (artists == null) artists = o.optJSONArray("artists");
        StringBuilder artist = new StringBuilder();
        if (artists != null) {
            for (int i = 0; i < artists.length(); i++) {
                JSONObject a = artists.optJSONObject(i);
                if (a == null) continue;
                String n = a.optString("name", "");
                if (n.isEmpty()) continue;
                if (artist.length() > 0) artist.append('/');
                artist.append(n);
            }
        }

        JSONObject al = o.optJSONObject("al");
        if (al == null) al = o.optJSONObject("album");
        String album = al == null ? "" : al.optString("name", "");
        String pic = al == null ? "" : al.optString("picUrl", "");

        long duration = o.optLong("dt", 0);
        if (duration == 0) duration = o.optLong("duration", 0);
        int fee = o.optInt("fee", 0);

        return new Song(id, name, artist.toString(), album, pic, duration, fee);
    }

    public static List<Song> list(JSONArray array) {
        List<Song> out = new ArrayList<>();
        if (array == null) return out;
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            Song song = from(array.optJSONObject(i));
            if (song == null || !seen.add(song.id)) continue;
            out.add(song);
        }
        return out;
    }

    public org.json.JSONObject toJson() {
        org.json.JSONObject o = new org.json.JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("artist", artist);
            o.put("album", album);
            o.put("pic", picUrl);
            o.put("duration", durationMs);
            o.put("fee", fee);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static List<Song> listFromJson(String raw) {
        List<Song> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                Song song = new Song(o.optLong("id"), o.optString("name"), o.optString("artist"),
                        o.optString("album"), o.optString("pic"), o.optLong("duration"), o.optInt("fee"));
                if (song.id > 0) out.add(song);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static String listToJson(List<Song> songs) {
        JSONArray array = new JSONArray();
        if (songs != null) for (Song s : songs) array.put(s.toJson());
        return array.toString();
    }

    public String durationText() {
        if (durationMs <= 0) return "";
        long seconds = durationMs / 1000;
        return String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }
}
