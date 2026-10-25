package com.hazard.music.model;

public final class DownloadItem {

    public static final int QUEUED = 0;
    public static final int DOWNLOADING = 1;
    public static final int PAUSED = 2;
    public static final int DONE = 3;
    public static final int ERROR = 4;

    public final Song song;
    public String path;
    public long bytes;
    public long total;
    public int state;
    public String quality;
    public String error;
    public long updated;

    public DownloadItem(Song song) {
        this.song = song;
        this.path = "";
        this.quality = "";
        this.error = "";
        this.updated = System.currentTimeMillis();
    }

    public boolean isDone() {
        return state == DONE;
    }

    public int progress() {
        if (total <= 0) return state == DONE ? 100 : 0;
        return (int) Math.min(100, bytes * 100 / total);
    }

    public String statusText() {
        switch (state) {
            case QUEUED:
                return "排队中";
            case DOWNLOADING:
                return "下载中 " + progress() + "%";
            case PAUSED:
                return "已暂停 " + progress() + "%";
            case DONE:
                return "已完成" + (quality.isEmpty() ? "" : " · " + quality);
            case ERROR:
                return "失败" + (error.isEmpty() ? "" : " · " + error);
            default:
                return "";
        }
    }

    public Song playableSong() {
        Song copy = new Song(song.id, song.name, song.artist, song.album, song.picUrl,
                song.durationMs, song.fee);
        copy.liked = song.liked;
        copy.localPath = path;
        return copy;
    }
}
