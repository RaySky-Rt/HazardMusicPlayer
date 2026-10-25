package com.hazard.music.api;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Persisted cookie jar for music.163.com. */
public final class CookieJar {

    private static final Pattern COOKIE = Pattern.compile("([A-Za-z0-9_\\-]+)=([^;,]*)");
    private static final Set<String> ATTRS = new HashSet<>(Arrays.asList(
            "path", "domain", "expires", "max-age", "secure", "httponly", "samesite",
            "version", "comment", "priority"));

    private final SharedPreferences prefs;
    private final Map<String, String> cookies = new LinkedHashMap<>();

    public CookieJar(Context context) {
        prefs = context.getSharedPreferences("cookies", Context.MODE_PRIVATE);
        load();
    }

    public synchronized void update(Map<String, List<String>> headers) {
        if (headers == null) return;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String name = entry.getKey();
            if (name == null) continue;
            String lower = name.toLowerCase(Locale.US);
            if (!lower.equals("set-cookie") && !lower.equals("set-cookie2")) continue;
            List<String> values = entry.getValue();
            if (values == null) continue;
            for (String value : values) {
                parse(value);
            }
        }
        save();
    }

    private void parse(String value) {
        if (value == null) return;
        Matcher matcher = COOKIE.matcher(value);
        while (matcher.find()) {
            String name = matcher.group(1);
            String val = matcher.group(2);
            if (ATTRS.contains(name.toLowerCase(Locale.US))) continue;
            if (val == null) continue;
            cookies.put(name, val);
        }
    }

    public synchronized String get(String name) {
        String value = cookies.get(name);
        return value == null ? "" : value;
    }

    public synchronized boolean isLoggedIn() {
        return !get("MUSIC_U").isEmpty();
    }

    public synchronized String header() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    public synchronized void clear() {
        cookies.clear();
        prefs.edit().remove("jar").apply();
    }

    /** Import a full Cookie header (e.g. "MUSIC_U=...; __csrf=..."). */
    public synchronized void setAll(String header) {
        cookies.clear();
        parse(header);
        save();
    }

    private void save() {
        JSONObject json = new JSONObject();
        try {
            for (Map.Entry<String, String> entry : cookies.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }
        } catch (Exception ignored) {
        }
        prefs.edit().putString("jar", json.toString()).apply();
    }

    private void load() {
        String raw = prefs.getString("jar", null);
        if (raw == null) return;
        try {
            JSONObject json = new JSONObject(raw);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = json.optString(key, "");
                if (!value.isEmpty()) cookies.put(key, value);
            }
        } catch (Exception ignored) {
        }
    }
}
