package com.hazard.music.download;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.net.Uri;
import android.database.Cursor;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.util.Log;

import com.hazard.music.MusicApp;
import com.hazard.music.api.NetEaseClient;
import com.hazard.music.model.DownloadItem;
import com.hazard.music.model.Song;
import com.hazard.music.model.SongUrl;
import com.hazard.music.store.AppDb;
import com.hazard.music.util.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DownloadManager {

    private static final String TAG = "Download";
    private static final long MIN_FREE = 50L * 1024 * 1024;

    public interface Listener {
        void onDownloadsChanged();
    }

    private static final class PausedException extends Exception {
    }

    private static final class CanceledException extends Exception {
    }

    private final Context context;
    private final AppDb db;
    private final NetEaseClient client;
    private final Handler main;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<DownloadItem> items = new ArrayList<>();
    private final Object lock = new Object();

    private Listener listener;
    private boolean running;
    private volatile boolean pauseAll;
    private volatile long currentId = -1;
    private volatile boolean currentPaused;
    private volatile boolean currentCanceled;
    private volatile File cachedRoot;
    private volatile String safTreeKey;
    private volatile boolean safReady;
    private long lastNotify;
    private long lastDbWrite;

    public DownloadManager(Context context, AppDb db, NetEaseClient client, Handler main) {
        this.context = context.getApplicationContext();
        this.db = db;
        this.client = client;
        this.main = main;

        synchronized (lock) {
            items.addAll(db.listDownloads());
            for (DownloadItem item : items) {
                if (item.state == DownloadItem.DOWNLOADING) {
                    item.state = DownloadItem.PAUSED;
                    item.error = "应用退出时中断";
                }
            }
            db.markInterrupted();
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(
                    DownloadService.CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW));
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        if (listener != null) main.post(listener::onDownloadsChanged);
    }

    public List<DownloadItem> snapshot() {
        synchronized (lock) {
            return new ArrayList<>(items);
        }
    }

    public List<Song> offlineSongs() {
        List<DownloadItem> copy = snapshot();
        List<Song> out = new ArrayList<>();
        for (DownloadItem item : copy) {
            if (item.isDone() && (item.path.startsWith("content://") || new File(item.path).isFile())) {
                out.add(item.playableSong());
            }
        }
        return out;
    }

    public boolean isDownloaded(long id) {
        synchronized (lock) {
            DownloadItem item = find(id);
            return item != null && item.isDone();
        }
    }

    public void enqueue(List<Song> songs) {
        if (songs == null || songs.isEmpty()) return;
        boolean added = false;
        synchronized (lock) {
            for (Song song : songs) {
                DownloadItem existing = find(song.id);
                if (existing != null) {
                    if (existing.state == DownloadItem.ERROR) {
                        existing.state = DownloadItem.QUEUED;
                        existing.error = "";
                        db.updateState(existing);
                        added = true;
                    }
                    continue;
                }
                DownloadItem item = new DownloadItem(song);
                item.state = DownloadItem.QUEUED;
                items.add(0, item);
                db.upsert(item);
                added = true;
            }
        }
        if (!added) return;
        Log.i(TAG, "enqueue, pending=" + snapshot().size());
        startService();
        notifyChanged();
        startWorker();
    }

    private void startService() {
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(new Intent(context, DownloadService.class));
        } else {
            context.startService(new Intent(context, DownloadService.class));
        }
    }

    private void startWorker() {
        synchronized (lock) {
            if (running) return;
            running = true;
        }
        io.execute(this::runLoop);
    }

    private void runLoop() {
        while (true) {
            DownloadItem item;
            synchronized (lock) {
                if (pauseAll) {
                    running = false;
                    if (noActiveTasksLocked()) {
                        DownloadService.stop(context);
                    }
                    return;
                }
                item = nextQueuedLocked();
                if (item == null) {
                    running = false;
                    if (noActiveTasksLocked()) {
                        DownloadService.stop(context);
                    }
                    notifyChanged();
                    return;
                }
                item.state = DownloadItem.DOWNLOADING;
                item.error = "";
                item.updated = System.currentTimeMillis();
                currentId = item.song.id;
                currentPaused = false;
                currentCanceled = false;
                db.updateState(item);
                Log.i(TAG, "start id=" + item.song.id + " name=" + item.song.name);
            }
            notifyChanged();
            updateNotification(true);
            try {
                download(item);
            } catch (PausedException e) {
                synchronized (lock) {
                    item.state = DownloadItem.PAUSED;
                    item.error = "";
                    db.updateState(item);
                }
                Log.i(TAG, "paused id=" + item.song.id + " bytes=" + item.bytes + " part=" + item.path);
                notifyChanged();
            } catch (CanceledException e) {
                synchronized (lock) {
                    items.remove(item);
                    db.deleteDownload(item.song.id);
                }
                notifyChanged();
            } catch (Exception e) {
                Log.w(TAG, "download failed id=" + item.song.id, e);
                synchronized (lock) {
                    item.state = DownloadItem.ERROR;
                    item.error = shortMessage(e);
                    db.updateState(item);
                }
                notifyChanged();
            } finally {
                synchronized (lock) {
                    currentId = -1;
                    currentPaused = false;
                }
                updateNotification(true);
            }
        }
    }

    private void download(DownloadItem item) throws Exception {
        SongUrl songUrl = fetchUrl(item.song.id);
        if (songUrl.trial) throw new IllegalStateException("该曲目仅支持试听");

        String type = "flac".equalsIgnoreCase(songUrl.type) ? "flac" : "mp3";
        if (isSaf()) {
            downloadSaf(item, songUrl, type);
            return;
        }
        String path = item.path;
        if (path == null || path.isEmpty() || !path.endsWith("." + type)) {
            path = buildPath(item.song, type);
            item.path = path;
        }
        File finalFile = new File(path);
        File part = new File(path + ".part");
        File parent = finalFile.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IllegalStateException("无法创建下载目录");
        }

        long existing = part.isFile() ? part.length() : 0;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(songUrl.url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (existing > 0) conn.setRequestProperty("Range", "bytes=" + existing + "-");
            int code = conn.getResponseCode();
            if (code == 416) {
                if (part.renameTo(finalFile)) {
                    finish(item, finalFile, songUrl);
                    return;
                }
                existing = 0;
                if (!part.delete()) throw new IllegalStateException("无法清理临时文件");
                throw new IllegalStateException("续传失败，请重试");
            }
            if (code != 200 && code != 206) {
                throw new IllegalStateException("HTTP " + code);
            }
            if (code == 200) existing = 0;
            long length = conn.getContentLengthLong();
            long total = length > 0 ? existing + length : -1;
            if (total > 0) {
                long available = new StatFs(parent.getAbsolutePath()).getAvailableBytes();
                if (available < total - existing + MIN_FREE) {
                    throw new IllegalStateException("存储空间不足");
                }
            }
            item.total = total;
            item.bytes = existing;
            Log.i(TAG, "http id=" + item.song.id + " code=" + code + " existing=" + existing + " total=" + total);

            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(part, code == 206 && existing > 0)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                long written = 0;
                while ((read = in.read(buffer)) != -1) {
                    if (currentCanceled) throw new CanceledException();
                    if (pauseAll || currentPaused) throw new PausedException();
                    out.write(buffer, 0, read);
                    written += read;
                    item.bytes = existing + written;
                    notifyProgress();
                }
                out.flush();
            }
            if (total > 0) item.bytes = total;
            if (finalFile.exists() && !finalFile.delete()) {
                throw new IllegalStateException("无法覆盖旧文件");
            }
            if (!part.renameTo(finalFile)) {
                throw new IllegalStateException("重命名失败");
            }
            finish(item, finalFile, songUrl);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private boolean isSaf() {
        String tree = Settings.downloadTree(context);
        if (tree.isEmpty()) return false;
        if (!tree.equals(safTreeKey)) {
            safTreeKey = tree;
            safReady = checkSaf(tree);
        }
        return safReady;
    }

    private boolean checkSaf(String tree) {
        try {
            Uri uri = Uri.parse(tree);
            if (!DocumentsContract.isTreeUri(uri)) return false;
            for (UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
                if (permission.getUri().equals(uri) && permission.isWritePermission()) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private File tempRoot() {
        File base = context.getExternalFilesDir(null);
        File tmp = new File(base == null ? context.getFilesDir() : base, "tmp");
        ensureDir(tmp);
        return tmp;
    }

    private void downloadSaf(DownloadItem item, SongUrl songUrl, String type) throws Exception {
        File tmpDir = tempRoot();
        File part = new File(tmpDir, item.song.id + ".part");
        long existing = part.isFile() ? part.length() : 0;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(songUrl.url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (existing > 0) conn.setRequestProperty("Range", "bytes=" + existing + "-");
            int code = conn.getResponseCode();
            if (code == 416) {
                if (existing > 0) {
                    finishSaf(item, songUrl, part, type, existing);
                    return;
                }
                throw new IllegalStateException("续传失败，请重试");
            }
            if (code != 200 && code != 206) throw new IllegalStateException("HTTP " + code);
            if (code == 200) existing = 0;
            long length = conn.getContentLengthLong();
            long total = length > 0 ? existing + length : -1;
            if (total > 0) {
                long available = new StatFs(tmpDir.getAbsolutePath()).getAvailableBytes();
                if (available < total - existing + MIN_FREE) {
                    throw new IllegalStateException("临时空间不足");
                }
            }
            item.total = total;
            item.bytes = existing;
            synchronized (lock) {
                db.updateState(item);
            }
            Log.i(TAG, "saf http id=" + item.song.id + " code=" + code
                    + " existing=" + existing + " total=" + total);
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(part, code == 206 && existing > 0)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                long written = 0;
                while ((read = in.read(buffer)) != -1) {
                    if (currentCanceled) throw new CanceledException();
                    if (pauseAll || currentPaused) throw new PausedException();
                    out.write(buffer, 0, read);
                    written += read;
                    item.bytes = existing + written;
                    notifyProgress();
                }
                out.flush();
            }
            if (total > 0) item.bytes = total;
            finishSaf(item, songUrl, part, type, item.bytes);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void finishSaf(DownloadItem item, SongUrl songUrl, File part, String type, long size) throws Exception {
        String tree = Settings.downloadTree(context);
        if (tree.isEmpty()) throw new IllegalStateException("未选择 SAF 目录");
        Uri treeUri = Uri.parse(tree);
        ContentResolver resolver = context.getContentResolver();
        String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
        String name = buildSafName(item.song, type);
        deleteSafByName(resolver, treeUri, treeDocId, name);
        Uri root = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId);
        String mime = "flac".equals(type) ? "audio/flac" : "audio/mpeg";
        Uri document = DocumentsContract.createDocument(resolver, root, mime, name);
        if (document == null) throw new IllegalStateException("无法创建 SAF 文件");
        try {
            InputStream in = new FileInputStream(part);
            OutputStream out = resolver.openOutputStream(document);
            if (out == null) throw new IllegalStateException("无法写入 SAF 文件");
            try {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                out.flush();
            } finally {
                try { in.close(); } catch (Exception ignored) { }
                try { out.close(); } catch (Exception ignored) { }
            }
        } catch (Exception e) {
            try { DocumentsContract.deleteDocument(resolver, document); } catch (Exception ignored) { }
            throw e;
        }
        if (!part.delete()) part.deleteOnExit();
        synchronized (lock) {
            item.path = document.toString();
            item.bytes = size;
            item.total = size;
            item.state = DownloadItem.DONE;
            item.quality = songUrl.qualityText();
            item.error = "";
            db.updateState(item);
        }
        Log.i(TAG, "saf done id=" + item.song.id + " uri=" + document + " size=" + size);
        scanSaf(document);
        notifyChanged();
        updateNotification(true);
    }

    private void deleteSafByName(ContentResolver resolver, Uri treeUri, String treeDocId, String name) {
        try {
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId);
            try (Cursor cursor = resolver.query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                if (cursor == null) return;
                while (cursor.moveToNext()) {
                    if (!name.equals(cursor.getString(1))) continue;
                    Uri doc = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0));
                    try {
                        DocumentsContract.deleteDocument(resolver, doc);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String buildSafName(Song song, String type) {
        String ext = "flac".equals(type) ? "flac" : "mp3";
        String base = sanitize(song.artist + " - " + song.name);
        if (base.isEmpty()) base = String.valueOf(song.id);
        if (base.length() > 90) base = base.substring(0, 90);
        return base + "." + ext;
    }

    private void scanSaf(Uri document) {
        try {
            if (!"com.android.externalstorage.documents".equals(document.getAuthority())) return;
            String docId = DocumentsContract.getDocumentId(document);
            int colon = docId.indexOf(':');
            if (colon <= 0) return;
            String volume = docId.substring(0, colon);
            String relative = docId.substring(colon + 1);
            File base = "primary".equals(volume)
                    ? Environment.getExternalStorageDirectory()
                    : new File("/storage/" + volume);
            File file = new File(base, relative);
            MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, null, null);
        } catch (Exception ignored) {
        }
    }

    private void finish(DownloadItem item, File file, SongUrl songUrl) {
        synchronized (lock) {
            item.path = file.getAbsolutePath();
            item.bytes = item.total > 0 ? item.total : file.length();
            item.total = item.bytes;
            item.state = DownloadItem.DONE;
            item.quality = songUrl.qualityText();
            item.error = "";
            db.updateState(item);
        }
        Log.i(TAG, "done id=" + item.song.id + " path=" + file.getAbsolutePath()
                + " size=" + item.bytes + " quality=" + item.quality);
        MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, null, null);
        notifyChanged();
        updateNotification(true);
    }

    private SongUrl fetchUrl(long id) throws Exception {
        Exception last = null;
        for (String level : Settings.levels(context)) {
            try {
                return client.songUrlBlocking(id, level);
            } catch (Exception e) {
                last = e;
            }
        }
        throw last == null ? new IllegalStateException("无法获取下载地址") : last;
    }

    private String buildPath(Song song, String ext) {
        File root = downloadRoot();
        String base = sanitize(song.artist + " - " + song.name);
        if (base.isEmpty()) base = String.valueOf(song.id);
        if (base.length() > 90) base = base.substring(0, 90);
        File file = new File(root, base + "." + ext);
        if (file.exists()) {
            file = new File(root, base + " (" + song.id + ")." + ext);
        }
        return file.getAbsolutePath();
    }

    public void resetRoot() {
        cachedRoot = null;
        safTreeKey = null;
        safReady = false;
    }

    private File downloadRoot() {
        File cached = cachedRoot;
        if (cached != null) return cached;
        synchronized (this) {
            if (cachedRoot != null) return cachedRoot;
            cachedRoot = pickRoot();
            return cachedRoot;
        }
    }

    private File pickRoot() {
        File publicMusic = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC), "Hazard");
        if (ensureDir(publicMusic)) return logRoot(publicMusic);
        File base = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (base == null) base = context.getExternalFilesDir(null);
        File appMusic = new File(base == null ? context.getFilesDir() : base, "Music");
        if (ensureDir(appMusic)) return logRoot(appMusic);
        File internal = new File(context.getFilesDir(), "Music");
        ensureDir(internal);
        return logRoot(internal);
    }

    private File logRoot(File dir) {
        Log.i(TAG, "root=" + dir.getAbsolutePath());
        return dir;
    }

    private boolean ensureDir(File dir) {
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            File probe = new File(dir, ".write_test_" + System.nanoTime());
            boolean ok = probe.createNewFile();
            if (ok) probe.delete();
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    private String sanitize(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public String storageText() {
        if (isSaf()) {
            String tree = Settings.downloadTree(context);
            String label = tree;
            try {
                label = DocumentsContract.getTreeDocumentId(Uri.parse(tree));
            } catch (Exception ignored) {
            }
            try {
                File tmp = tempRoot();
                long available = new StatFs(tmp.getAbsolutePath()).getAvailableBytes();
                return "SAF · " + label + " · 临时剩余 " + formatSize(available);
            } catch (Exception e) {
                return "SAF · " + label;
            }
        }
        File root = downloadRoot();
        try {
            StatFs stat = new StatFs(root.getAbsolutePath());
            long available = stat.getAvailableBytes();
            return root.getAbsolutePath() + " · 剩余 " + formatSize(available);
        } catch (Exception e) {
            return root.getAbsolutePath();
        }
    }

    public static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.1f GB", bytes / 1024.0 / 1024 / 1024);
        if (bytes >= 1024L * 1024) return String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024);
        return (bytes / 1024) + " KB";
    }

    public void pause(long id) {
        synchronized (lock) {
            DownloadItem item = find(id);
            if (item == null) return;
            if (item.state == DownloadItem.QUEUED || item.state == DownloadItem.DOWNLOADING) {
                item.state = DownloadItem.PAUSED;
                db.updateState(item);
                if (currentId == id) currentPaused = true;
            }
        }
        notifyChanged();
        updateNotification(true);
    }

    public void resume(long id) {
        synchronized (lock) {
            DownloadItem item = find(id);
            if (item == null) return;
            if (item.state == DownloadItem.PAUSED || item.state == DownloadItem.ERROR) {
                item.state = DownloadItem.QUEUED;
                item.error = "";
                db.updateState(item);
            }
        }
        startService();
        notifyChanged();
        startWorker();
    }

    public void pauseAll() {
        pauseAll = true;
        synchronized (lock) {
            currentPaused = true;
            for (DownloadItem item : items) {
                if (item.state == DownloadItem.QUEUED) {
                    item.state = DownloadItem.PAUSED;
                    db.updateState(item);
                }
            }
        }
        notifyChanged();
        updateNotification(true);
    }

    public void resumeAll() {
        pauseAll = false;
        synchronized (lock) {
            for (DownloadItem item : items) {
                if (item.state == DownloadItem.PAUSED || item.state == DownloadItem.ERROR) {
                    item.state = DownloadItem.QUEUED;
                    item.error = "";
                    db.updateState(item);
                }
            }
        }
        startService();
        notifyChanged();
        startWorker();
    }

    public void delete(long id) {
        synchronized (lock) {
            DownloadItem item = find(id);
            if (item == null) return;
            if (currentId == id) currentCanceled = true;
            if (item.path != null && !item.path.isEmpty()) {
                if (item.path.startsWith("content://")) {
                    try {
                        DocumentsContract.deleteDocument(context.getContentResolver(), Uri.parse(item.path));
                    } catch (Exception ignored) {
                    }
                } else {
                    new File(item.path).delete();
                    new File(item.path + ".part").delete();
                }
            }
            new File(tempRoot(), item.song.id + ".part").delete();
            items.remove(item);
            db.deleteDownload(id);
        }
        notifyChanged();
        updateNotification(true);
        startWorker();
    }

    public void stopServiceIfIdle() {
        synchronized (lock) {
            if (!running && noActiveTasksLocked()) {
                DownloadService.stop(context);
                NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                nm.cancel(DownloadService.NOTIFICATION_ID);
            }
        }
    }

    private DownloadItem find(long id) {
        for (DownloadItem item : items) {
            if (item.song.id == id) return item;
        }
        return null;
    }

    private DownloadItem nextQueuedLocked() {
        for (DownloadItem item : items) {
            if (item.state == DownloadItem.QUEUED) return item;
        }
        return null;
    }

    private boolean noActiveTasksLocked() {
        for (DownloadItem item : items) {
            if (item.state == DownloadItem.QUEUED || item.state == DownloadItem.DOWNLOADING) return false;
        }
        return true;
    }

    private void notifyProgress() {
        long now = System.currentTimeMillis();
        if (now - lastNotify >= 400) {
            lastNotify = now;
            notifyChanged();
            updateNotification(false);
        }
        if (now - lastDbWrite >= 5000) {
            lastDbWrite = now;
            synchronized (lock) {
                DownloadItem item = find(currentId);
                if (item != null) db.updateState(item);
            }
        }
    }

    private void notifyChanged() {
        if (listener != null) main.post(listener::onDownloadsChanged);
    }

    private void updateNotification(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastNotify < 900) return;
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(DownloadService.NOTIFICATION_ID, buildNotification());
        } catch (Exception ignored) {
        }
    }

    public Notification buildNotification() {
        int done = 0;
        int active = 0;
        DownloadItem current = null;
        synchronized (lock) {
            for (DownloadItem item : items) {
                if (item.state == DownloadItem.DONE) done++;
                if (item.state == DownloadItem.DOWNLOADING || item.state == DownloadItem.QUEUED) active++;
                if (item.song.id == currentId) current = item;
            }
        }
        String title = current == null
                ? "下载 · 完成 " + done
                : current.song.name + " · " + current.progress() + "%";
        String text = current == null
                ? "剩余 " + active + " 项"
                : current.song.artist;

        Intent content = new Intent(context, com.hazard.music.ui.MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 10, content, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(context, DownloadService.CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }
        builder.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(active > 0)
                .setCategory(Notification.CATEGORY_PROGRESS);
        builder.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_media_pause, "暂停全部",
                DownloadService.action(context, DownloadService.ACTION_PAUSE_ALL, 11)).build());
        builder.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_media_play, "继续全部",
                DownloadService.action(context, DownloadService.ACTION_RESUME_ALL, 12)).build());
        builder.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel, "停止",
                DownloadService.action(context, DownloadService.ACTION_STOP, 13)).build());
        return builder.build();
    }

    private static String shortMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isEmpty()) message = e.getClass().getSimpleName();
        return message.length() > 80 ? message.substring(0, 80) : message;
    }
}
