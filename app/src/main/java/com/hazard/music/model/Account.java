package com.hazard.music.model;

import org.json.JSONObject;

public final class Account {

    public final long userId;
    public final String nickname;
    public final String avatarUrl;
    public final int vipType;

    public Account(long userId, String nickname, String avatarUrl, int vipType) {
        this.userId = userId;
        this.nickname = nickname == null ? "" : nickname;
        this.avatarUrl = avatarUrl == null ? "" : avatarUrl;
        this.vipType = vipType;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("userId", userId);
            o.put("nickname", nickname);
            o.put("avatar", avatarUrl);
            o.put("vipType", vipType);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static Account fromJson(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(raw);
            long id = o.optLong("userId", 0);
            if (id == 0) return null;
            return new Account(id, o.optString("nickname"), o.optString("avatar"), o.optInt("vipType"));
        } catch (Exception e) {
            return null;
        }
    }

    public static Account from(JSONObject account, JSONObject profile) {
        JSONObject source = profile != null ? profile : account;
        if (source == null) return null;
        long id = source.optLong("userId", 0);
        if (id == 0 && account != null) id = account.optLong("id", 0);
        if (id == 0) return null;
        String nickname = source.optString("nickname", "");
        if (nickname.isEmpty() && account != null) nickname = account.optString("nickname", "");
        String avatar = source.optString("avatarUrl", "");
        int vip = source.optInt("vipType", account == null ? 0 : account.optInt("vipType", 0));
        return new Account(id, nickname, avatar, vip);
    }
}
