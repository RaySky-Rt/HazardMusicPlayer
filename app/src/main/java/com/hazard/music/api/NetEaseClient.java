package com.hazard.music.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import com.hazard.music.model.Account;
import com.hazard.music.model.Album;
import com.hazard.music.model.Playlist;
import com.hazard.music.model.Song;
import com.hazard.music.model.SongUrl;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/** Minimal NetEase Cloud Music client (weapi + eapi). */
public final class NetEaseClient {

    public static final String TAG = "Netease";
    private static final String UA_WEB =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final String UA_EAPI = "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)";
    private static final String UA_API = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/3.1.29.205117";

    public interface Parser<T> {
        T parse(JSONObject json) throws Exception;
    }

    public interface Callback<T> {
        void onSuccess(T data);
        void onError(String message, int code);
    }

    public static final class ApiException extends Exception {
        public final int code;

        public ApiException(String message, int code) {
            super(message);
            this.code = code;
        }
    }

    private final CookieJar cookies;
    private final ExecutorService io;
    private final Handler main;
    private final String deviceId;

    public NetEaseClient(Context context, CookieJar cookies, ExecutorService io, Handler main) {
        this.cookies = cookies;
        this.io = io;
        this.main = main;
        SharedPreferences prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE);
        String id = prefs.getString("id", null);
        if (id == null) {
            id = "hazard" + randomToken(10);
            prefs.edit().putString("id", id).apply();
        }
        this.deviceId = id;
    }

    public CookieJar cookies() {
        return cookies;
    }

    public void logout() {
        cookies.clear();
    }

    // ---- auth ----

    public void qrKey(Callback<JSONObject> callback) {
        requestApi("/api/login/qrcode/unikey", obj("type", 3), json -> json, callback);
    }

    public void qrCheck(String key, Callback<JSONObject> callback) {
        requestApi("/api/login/qrcode/client/login", obj("key", key, "type", 3), json -> json, callback);
    }

    public void account(Callback<Account> callback) {
        request("/api/nuser/account/get", obj(), false, json -> {
            JSONObject account = json.optJSONObject("account");
            JSONObject profile = json.optJSONObject("profile");
            if (account == null && profile == null) return null;
            return Account.from(account, profile);
        }, callback);
    }

    // ---- discover ----

    public void dailyRecommend(Callback<List<Song>> callback) {
        request("/api/v3/discovery/recommend/songs", obj(), false, json -> {
            ensureOk(json);
            JSONObject data = json.optJSONObject("data");
            JSONArray songs = data == null ? null : data.optJSONArray("dailySongs");
            return Song.list(songs);
        }, callback);
    }

    public void search(String keyword, int offset, Callback<List<Song>> callback) {
        request("/api/cloudsearch/pc",
                obj("s", keyword, "type", 1, "limit", 30, "offset", offset, "total", true),
                false, json -> {
                    ensureOk(json);
                    JSONObject result = json.optJSONObject("result");
                    JSONArray songs = result == null ? null : result.optJSONArray("songs");
                    return Song.list(songs);
                }, callback);
    }

    // ---- library ----

    public void playlists(long uid, Callback<List<Playlist>> callback) {
        request("/api/user/playlist",
                obj("uid", uid, "limit", 1000, "offset", 0, "includeVideo", true),
                false, json -> {
                    ensureOk(json);
                    return Playlist.list(json.optJSONArray("playlist"));
                }, callback);
    }

    public void recommendedPlaylists(Callback<List<Playlist>> callback) {
        request("/api/personalized/playlist",
                obj("limit", 12, "total", true, "n", 1000),
                false, json -> {
                    ensureOk(json);
                    JSONArray list = json.optJSONArray("result");
                    if (list == null) list = json.optJSONArray("playlists");
                    Log.i(TAG, "personalized playlists=" + (list == null ? -1 : list.length()));
                    return Playlist.list(list);
                }, callback);
    }

    public void collectedAlbums(Callback<List<Album>> callback) {
        request("/api/album/sublist",
                obj("limit", 1000, "offset", 0, "total", true),
                false, json -> {
                    ensureOk(json);
                    JSONArray albums = json.optJSONArray("albums");
                    if (albums == null) albums = json.optJSONArray("data");
                    if (albums == null) {
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) albums = data.optJSONArray("albums");
                    }
                    Log.i(TAG, "albumSublist count=" + (albums == null ? -1 : albums.length()));
                    return Album.list(albums);
                }, callback);
    }

    public void albumSongs(long albumId, Callback<List<Song>> callback) {
        request("/api/v1/album/" + albumId, obj(), false, json -> {
            ensureOk(json);
            return Song.list(json.optJSONArray("songs"));
        }, callback);
    }

    public void likedIds(long uid, Callback<Set<Long>> callback) {
        request("/api/song/like/get", obj("uid", uid), true, json -> {
            int code = json.optInt("code", 200);
            if (code == 301 || code == 302) return new LinkedHashSet<Long>();
            ensureOk(json);
            JSONArray ids = json.optJSONArray("ids");
            if (ids == null) {
                JSONObject data = json.optJSONObject("data");
                if (data != null) ids = data.optJSONArray("ids");
            }
            Set<Long> out = new LinkedHashSet<>();
            if (ids != null) {
                for (int i = 0; i < ids.length(); i++) {
                    long id = ids.optLong(i, 0);
                    if (id > 0) out.add(id);
                }
            }
            return out;
        }, callback);
    }

    public void like(long trackId, long userId, boolean like, Callback<Boolean> callback) {
        request("/api/song/like",
                obj("trackId", trackId, "userid", userId, "like", like),
                true, json -> {
                    ensureOk(json);
                    return Boolean.TRUE;
                }, callback);
    }

    public void playlistSongs(long playlistId, Callback<List<Song>> callback) {
        io.execute(() -> {
            try {
                JSONObject detail = execute("/api/v6/playlist/detail",
                        obj("id", playlistId, "n", 1000, "s", 0), true);
                ensureOk(detail);
                JSONObject playlist = detail.optJSONObject("playlist");
                if (playlist == null) throw new ApiException("歌单不存在", -2);

                List<Song> songs = Song.list(playlist.optJSONArray("tracks"));
                if (songs.isEmpty()) {
                    JSONArray trackIds = playlist.optJSONArray("trackIds");
                    if (trackIds != null) {
                        List<Long> ids = new ArrayList<>();
                        for (int i = 0; i < trackIds.length() && ids.size() < 1000; i++) {
                            JSONObject item = trackIds.optJSONObject(i);
                            long id = item == null ? 0 : item.optLong("id", 0);
                            if (id > 0) ids.add(id);
                        }
                        for (int start = 0; start < ids.size(); start += 500) {
                            int end = Math.min(start + 500, ids.size());
                            JSONArray c = new JSONArray();
                            for (int i = start; i < end; i++) {
                                c.put(new JSONObject().put("id", ids.get(i)));
                            }
                            JSONObject detail2 = execute("/api/v3/song/detail",
                                    obj("c", c.toString()), false);
                            ensureOk(detail2);
                            songs.addAll(Song.list(detail2.optJSONArray("songs")));
                        }
                    }
                }
                final List<Song> result = songs;
                main.post(() -> callback.onSuccess(result));
            } catch (ApiException e) {
                Log.w(TAG, "playlistSongs error code=" + e.code + " " + e.getMessage());
                main.post(() -> callback.onError(e.getMessage(), e.code));
            } catch (Exception e) {
                main.post(() -> callback.onError(describe(e), -1));
            }
        });
    }

    // ---- playback URL (P2) ----

    public void songUrl(long trackId, String level, Callback<SongUrl> callback) {
        request("/api/song/enhance/player/url/v1",
                obj("ids", "[" + trackId + "]", "level", level, "encodeType", "flac"),
                true, json -> parseSongUrl(json, trackId, level), callback);
    }

    public SongUrl songUrlBlocking(long trackId, String level) throws Exception {
        JSONObject json = execute("/api/song/enhance/player/url/v1",
                obj("ids", "[" + trackId + "]", "level", level, "encodeType", "flac"), true);
        return parseSongUrl(json, trackId, level);
    }

    private SongUrl parseSongUrl(JSONObject json, long trackId, String level) throws ApiException {
        ensureOk(json);
        JSONArray data = json.optJSONArray("data");
        JSONObject item = data == null ? null : data.optJSONObject(0);
        if (item == null) throw new ApiException("无播放地址", -2);
        String url = item.optString("url", "");
        boolean trial = !item.isNull("freeTrialInfo");
        Log.i(TAG, "songUrl id=" + trackId + " level=" + level + " type=" + item.optString("type")
                + " br=" + item.optInt("br") + " trial=" + trial + " hasUrl=" + !url.isEmpty());
        if (url.isEmpty()) throw new ApiException("无可用音质（可能需要会员）", -3);
        return new SongUrl(url, item.optString("type"), item.optString("level"),
                item.optInt("br"), trial);
    }

    // ---- core ----

    public <T> void request(String path, JSONObject data, boolean eapi,
                            Parser<T> parser, Callback<T> callback) {
        io.execute(() -> {
            try {
                JSONObject json = execute(path, data, eapi);
                T result = parser.parse(json);
                main.post(() -> callback.onSuccess(result));
            } catch (ApiException e) {
                Log.w(TAG, path + " error code=" + e.code + " " + e.getMessage());
                main.post(() -> callback.onError(e.getMessage(), e.code));
            } catch (Exception e) {
                Log.w(TAG, path + " failed: " + e);
                main.post(() -> callback.onError(describe(e), -1));
            }
        });
    }

    public <T> void requestApi(String path, JSONObject data,
                               Parser<T> parser, Callback<T> callback) {
        io.execute(() -> {
            try {
                JSONObject json = executeApi(path, data);
                T result = parser.parse(json);
                main.post(() -> callback.onSuccess(result));
            } catch (ApiException e) {
                Log.w(TAG, path + " error code=" + e.code + " " + e.getMessage());
                main.post(() -> callback.onError(e.getMessage(), e.code));
            } catch (Exception e) {
                Log.w(TAG, path + " failed: " + e);
                main.post(() -> callback.onError(describe(e), -1));
            }
        });
    }

    /** Plaintext desktop API (new QR login endpoints). */
    private JSONObject executeApi(String path, JSONObject data) throws Exception {
        String url = "https://interface.music.163.com" + path;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
            conn.setRequestProperty("User-Agent", UA_API);
            conn.setRequestProperty("Referer", "https://music.163.com");
            conn.setRequestProperty("Accept", "*/*");

            String base = "os=pc; appver=3.1.17.204416; deviceId=" + deviceId + "; channel=netease";
            String jar = cookies.header();
            String csrf = cookies.get("__csrf");
            if (!csrf.isEmpty()) base += "; __csrf=" + csrf;
            conn.setRequestProperty("Cookie", jar.isEmpty() ? base : base + "; " + jar);

            StringBuilder body = new StringBuilder();
            java.util.Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                String name = keys.next();
                if (body.length() > 0) body.append('&');
                body.append(URLEncoder.encode(name, StandardCharsets.UTF_8.name()))
                        .append('=')
                        .append(URLEncoder.encode(data.optString(name, ""), StandardCharsets.UTF_8.name()));
            }
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

            int status = conn.getResponseCode();
            Log.i(TAG, "api " + path + " http=" + status);
            cookies.update(conn.getHeaderFields());
            InputStream stream = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
            String text = read(stream);
            if (status < 200 || status >= 300) {
                throw new ApiException("HTTP " + status + ": " + trim(text), status);
            }
            if (text.isEmpty()) throw new ApiException("空响应", status);
            JSONObject parsed = new JSONObject(text);
            Log.i(TAG, path + " code=" + parsed.optInt("code", 200));
            return parsed;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private JSONObject execute(String path, JSONObject data, boolean eapi) throws Exception {
        String url;
        Map<String, String> form;
        if (eapi) {
            JSONObject header = new JSONObject();
            header.put("os", "pc");
            header.put("appver", "3.1.17.204416");
            header.put("osver", "Microsoft-Windows-10-Professional-build-19045-64bit");
            header.put("deviceId", deviceId);
            header.put("requestId", System.currentTimeMillis() + "_" + (1000 + new Random().nextInt(8999)));
            header.put("__csrf", cookies.get("__csrf"));
            header.put("channel", "netease");
            String musicU = cookies.get("MUSIC_U");
            if (!musicU.isEmpty()) header.put("MUSIC_U", musicU);
            data.put("header", header);
            form = new LinkedHashMap<>();
            form.put("params", Crypto.eapi(path, data.toString()));
            url = "https://interfacepc.music.163.com/eapi/" + strip(path);
        } else {
            data.put("csrf_token", cookies.get("__csrf"));
            form = Crypto.weapi(data.toString());
            url = "https://music.163.com/weapi/" + strip(path);
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
            conn.setRequestProperty("User-Agent", eapi ? UA_EAPI : UA_WEB);
            conn.setRequestProperty("Referer", "https://music.163.com");
            conn.setRequestProperty("Accept", "*/*");
            String cookie = cookies.header();
            if (!cookie.isEmpty()) conn.setRequestProperty("Cookie", cookie);

            StringBuilder body = new StringBuilder();
            for (Map.Entry<String, String> entry : form.entrySet()) {
                if (body.length() > 0) body.append('&');
                body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()))
                        .append('=')
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
            }
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(bytes);
                out.flush();
            }

            int status = conn.getResponseCode();
            Log.i(TAG, (eapi ? "eapi " : "weapi ") + path + " http=" + status);
            cookies.update(conn.getHeaderFields());
            InputStream stream = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
            String text = read(stream);
            if (status < 200 || status >= 300) {
                throw new ApiException("HTTP " + status + ": " + trim(text), status);
            }
            if (text.isEmpty()) throw new ApiException("空响应", status);
            try {
                JSONObject parsed = new JSONObject(text);
                Log.i(TAG, path + " code=" + parsed.optInt("code", 200));
                return parsed;
            } catch (Exception e) {
                throw new ApiException("无法解析响应: " + trim(text), status);
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void ensureOk(JSONObject json) throws ApiException {
        int code = json.optInt("code", 200);
        if (code != 200) {
            String message = json.optString("message", json.optString("msg", "接口错误"));
            throw new ApiException(message + " (" + code + ")", code);
        }
    }

    private static JSONObject obj(Object... pairs) {
        JSONObject json = new JSONObject();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            try {
                json.put(String.valueOf(pairs[i]), pairs[i + 1]);
            } catch (Exception ignored) {
            }
        }
        return json;
    }

    private static String strip(String path) {
        if (path.startsWith("/api/")) return path.substring(5);
        if (path.startsWith("/")) return path.substring(1);
        return path;
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) {
            out.write(buffer, 0, count);
        }
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String trim(String text) {
        if (text == null) return "";
        String oneLine = text.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() > 300 ? oneLine.substring(0, 300) : oneLine;
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isEmpty()) message = e.getClass().getSimpleName();
        return message;
    }

    private static String randomToken(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }
}
