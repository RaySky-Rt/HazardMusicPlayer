package com.hazard.music.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small HTTP cover loader with a memory LRU. */
public final class ImageLoader {

    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static volatile ImageLoader instance;

    private final LruCache<String, Bitmap> cache;
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    private ImageLoader() {
        cache = new LruCache<String, Bitmap>(MAX_BYTES) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
    }

    public static ImageLoader get(Context context) {
        if (instance == null) {
            synchronized (ImageLoader.class) {
                if (instance == null) instance = new ImageLoader();
            }
        }
        return instance;
    }

    public void clear() {
        cache.evictAll();
    }

    public void load(String url, ImageView view, int targetSize) {
        if (url == null || url.isEmpty()) {
            view.setTag("");
            view.setImageDrawable(null);
            return;
        }
        String key = url + "@" + targetSize;
        Bitmap cached = cache.get(key);
        if (cached != null) {
            view.setTag(key);
            view.setImageBitmap(cached);
            return;
        }
        view.setTag(key);
        view.setImageDrawable(null);
        io.execute(() -> {
            Bitmap bitmap = download(url, targetSize);
            if (bitmap != null) cache.put(key, bitmap);
            main.post(() -> {
                if (key.equals(view.getTag())) {
                    if (bitmap != null) view.setImageBitmap(bitmap);
                    else view.setImageDrawable(null);
                }
            });
        });
    }

    private Bitmap download(String url, int targetSize) {
        HttpURLConnection conn = null;
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
                int sample = 1;
                int size = Math.max(bounds.outWidth, bounds.outHeight);
                while (size / sample > targetSize * 2) sample *= 2;

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                return BitmapFactory.decodeByteArray(data, 0, data.length, options);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
