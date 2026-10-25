package com.hazard.music.store;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONObject;

import com.hazard.music.model.DownloadItem;
import com.hazard.music.model.Song;

import java.util.ArrayList;
import java.util.List;

public final class AppDb extends SQLiteOpenHelper {

    private static final String NAME = "hazard.db";
    private static final int VERSION = 2;

    public AppDb(Context context) {
        super(context.getApplicationContext(), NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE downloads ("
                + "id INTEGER PRIMARY KEY,"
                + "name TEXT, artist TEXT, album TEXT, pic TEXT, duration INTEGER, fee INTEGER,"
                + "path TEXT, bytes INTEGER, total INTEGER, state INTEGER,"
                + "quality TEXT, error TEXT, updated INTEGER)");
        db.execSQL("CREATE TABLE kv (key TEXT PRIMARY KEY, value TEXT, updated INTEGER)");
        createHistory(db);
    }

    private static void createHistory(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS history ("
                + "song_id INTEGER PRIMARY KEY,"
                + "song_json TEXT NOT NULL,"
                + "played_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS history_played ON history(played_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createHistory(db);
    }

    public synchronized void upsert(DownloadItem item) {
        ContentValues values = new ContentValues();
        values.put("id", item.song.id);
        values.put("name", item.song.name);
        values.put("artist", item.song.artist);
        values.put("album", item.song.album);
        values.put("pic", item.song.picUrl);
        values.put("duration", item.song.durationMs);
        values.put("fee", item.song.fee);
        values.put("path", item.path);
        values.put("bytes", item.bytes);
        values.put("total", item.total);
        values.put("state", item.state);
        values.put("quality", item.quality);
        values.put("error", item.error);
        values.put("updated", item.updated);
        getWritableDatabase().insertWithOnConflict("downloads", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void updateState(DownloadItem item) {
        item.updated = System.currentTimeMillis();
        upsert(item);
    }

    public synchronized List<DownloadItem> listDownloads() {
        List<DownloadItem> out = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("downloads", null, null, null, null, null, "updated DESC")) {
            while (cursor.moveToNext()) {
                Song song = new Song(
                        cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("artist")),
                        cursor.getString(cursor.getColumnIndexOrThrow("album")),
                        cursor.getString(cursor.getColumnIndexOrThrow("pic")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("duration")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("fee")));
                DownloadItem item = new DownloadItem(song);
                item.path = cursor.getString(cursor.getColumnIndexOrThrow("path"));
                item.bytes = cursor.getLong(cursor.getColumnIndexOrThrow("bytes"));
                item.total = cursor.getLong(cursor.getColumnIndexOrThrow("total"));
                item.state = cursor.getInt(cursor.getColumnIndexOrThrow("state"));
                item.quality = cursor.getString(cursor.getColumnIndexOrThrow("quality"));
                item.error = cursor.getString(cursor.getColumnIndexOrThrow("error"));
                item.updated = cursor.getLong(cursor.getColumnIndexOrThrow("updated"));
                out.add(item);
            }
        }
        return out;
    }

    public synchronized void markInterrupted() {
        ContentValues values = new ContentValues();
        values.put("state", DownloadItem.PAUSED);
        values.put("error", "应用退出时中断");
        getWritableDatabase().update("downloads", values, "state=?", new String[]{String.valueOf(DownloadItem.DOWNLOADING)});
    }

    public synchronized void deleteDownload(long id) {
        getWritableDatabase().delete("downloads", "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void putCache(String key, String value) {
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value);
        values.put("updated", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("kv", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void recordHistory(Song song, long playedAt) {
        if (song == null || song.id <= 0) return;
        ContentValues values = new ContentValues();
        values.put("song_id", song.id);
        values.put("song_json", song.toJson().toString());
        values.put("played_at", playedAt);
        SQLiteDatabase db = getWritableDatabase();
        db.insertWithOnConflict("history", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.execSQL("DELETE FROM history WHERE song_id NOT IN ("
                + "SELECT song_id FROM history ORDER BY played_at DESC LIMIT 200)");
    }

    public synchronized List<Song> listHistory(int limit) {
        List<Song> out = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("history", new String[]{"song_json"},
                null, null, null, null, "played_at DESC", String.valueOf(limit))) {
            while (cursor.moveToNext()) {
                try {
                    Song song = Song.from(new JSONObject(cursor.getString(0)));
                    if (song != null) out.add(song);
                } catch (Exception ignored) {
                }
            }
        }
        return out;
    }

    public synchronized int historyCount() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM history", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public synchronized void clearHistory() {
        getWritableDatabase().delete("history", null, null);
    }

    public synchronized String getCache(String key) {
        try (Cursor cursor = getReadableDatabase().query("kv", new String[]{"value"},
                "key=?", new String[]{key}, null, null, null)) {
            if (cursor.moveToFirst()) return cursor.getString(0);
        }
        return null;
    }
}
