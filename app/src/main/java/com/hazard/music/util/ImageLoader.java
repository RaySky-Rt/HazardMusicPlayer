package com.hazard.music.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small HTTP cover loader with memory + disk cache and CDN thumbnail requests. */
public final class ImageLoader {

    private static final String TAG = "ImageLoader";
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final long MAX_DISK_BYTES = 20L * 1024 * 1024;
    private static volatile ImageLoader instance;

    private final LruCache<String, Bitmap> cache;
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final File diskDir;
    private final Map<String, List<ImageView>> pending = new HashMap<>();

    private ImageLoader(Context context) {
        cache = new LruCache<String, Bitmap>(MAX_BYTES) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
        diskDir = new File(context.getCacheDir(), "covers");
        if (!diskDir.isDirectory()) diskDir.mkdirs();
    }

    public static ImageLoader get(Context context) {
        if (instance == null) {
            synchronized (ImageLoader.class) {
                if (instance == null) instance = new ImageLoader(context.getApplicationContext());
            }
        }
        return instance;
    }

    public void clear() {
        cache.evictAll();
        File[] files = diskDir.listFiles();
        if (files != null) {
            for (File file : files) file.delete();
        }
    }

    public void load(String url, ImageView view, int targetSize) {
        if (url == null || url.isEmpty()) {
            view.setTag("");
            view.setImageDrawable(null);
            return;
        }
        String requestUrl = thumbnailUrl(url, targetSize);
        String key = requestUrl + "@" + targetSize;
        Bitmap cached = cache.get(key);
        if (cached != null) {
            view.setTag(key);
            view.setImageBitmap(cached);
            return;
        }
        view.setTag(key);
        view.setImageDrawable(null);
        synchronized (pending) {
            List<ImageView> waiting = pending.get(key);
            if (waiting != null) {
                waiting.add(view);
                return;
            }
            waiting = new ArrayList<>();
            waiting.add(view);
            pending.put(key, waiting);
        }
        io.execute(() -> {
            Bitmap bitmap = loadDisk(key, targetSize);
            if (bitmap == null) bitmap = download(requestUrl, targetSize, key);
            if (bitmap != null) cache.put(key, bitmap);
            final Bitmap result = bitmap;
            main.post(() -> deliver(key, result));
        });
    }

    private void deliver(String key, Bitmap bitmap) {
        List<ImageView> views;
        synchronized (pending) {
            views = pending.remove(key);
        }
        if (views == null) return;
        for (ImageView view : views) {
            if (key.equals(view.getTag())) {
                if (bitmap != null) view.setImageBitmap(bitmap);
                else view.setImageDrawable(null);
            }
        }
    }

    private Bitmap loadDisk(String key, int targetSize) {
        File file = diskFile(key);
        if (!file.isFile()) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            BitmapFactory.Options options = decodeOptions(bounds.outWidth, bounds.outHeight, targetSize);
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Bitmap download(String url, int targetSize, String key) {
        HttpURLConnection conn = null;
        boolean success = false;
        long started = System.currentTimeMillis();
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                byte[] data = out.toByteArray();

                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length,
                        decodeOptions(bounds.outWidth, bounds.outHeight, targetSize));
                if (bitmap != null) writeDisk(key, data);
                success = true;
                return bitmap;
            }
        } catch (Exception e) {
            Log.w(TAG, "download failed " + url + " after "
                    + (System.currentTimeMillis() - started) + "ms: " + e);
            return null;
        } finally {
            // Keep successful connections eligible for HttpURLConnection keep-alive.
            if (conn != null && !success) conn.disconnect();
        }
    }

    private BitmapFactory.Options decodeOptions(int width, int height, int targetSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        int sample = 1;
        int size = Math.max(width, height);
        while (size / sample > targetSize * 2) sample *= 2;
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return options;
    }

    private void writeDisk(String key, byte[] data) {
        File file = diskFile(key);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
        } catch (Exception ignored) {
        }
        trimDisk();
    }

    private void trimDisk() {
        File[] files = diskDir.listFiles();
        if (files == null) return;
        long total = 0;
        List<File> list = new ArrayList<>();
        for (File file : files) {
            total += file.length();
            list.add(file);
        }
        if (total <= MAX_DISK_BYTES) return;
        Collections.sort(list, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(a.lastModified(), b.lastModified());
            }
        });
        for (File file : list) {
            if (total <= MAX_DISK_BYTES) break;
            total -= file.length();
            file.delete();
        }
    }

    private File diskFile(String key) {
        return new File(diskDir, diskName(key));
    }

    private String diskName(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(key.getBytes("UTF-8"));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte value : hash) out.append(String.format("%02x", value));
            return out.toString();
        } catch (Exception e) {
            return Integer.toHexString(key.hashCode());
        }
    }

    /** NetEase image CDN supports on-the-fly resizing via ?param=WxH. */
    private String thumbnailUrl(String url, int targetSize) {
        if (!url.contains("music.126.net") || url.contains("param=")) return url;
        int size = Math.max(80, Math.min(480, targetSize));
        return url + (url.contains("?") ? "&" : "?") + "param=" + size + "y" + size;
    }
}
