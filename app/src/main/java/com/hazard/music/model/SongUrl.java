package com.hazard.music.model;

public final class SongUrl {

    public final String url;
    public final String type;
    public final String level;
    public final int br;
    public final boolean trial;

    public SongUrl(String url, String type, String level, int br, boolean trial) {
        this.url = url;
        this.type = type == null ? "" : type;
        this.level = level == null ? "" : level;
        this.br = br;
        this.trial = trial;
    }

    public String qualityText() {
        if ("flac".equalsIgnoreCase(type)) {
            return "无损" + (level.isEmpty() ? "" : " · " + level);
        }
        if (br >= 300000) return "320 kbps";
        if (br >= 180000) return "192 kbps";
        if (br > 0) return (br / 1000) + " kbps";
        return level.isEmpty() ? "标准" : level;
    }
}
