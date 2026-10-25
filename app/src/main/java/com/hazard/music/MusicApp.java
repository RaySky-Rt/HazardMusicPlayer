package com.hazard.music;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import com.hazard.music.api.CookieJar;
import com.hazard.music.api.NetEaseClient;
import com.hazard.music.download.DownloadManager;
import com.hazard.music.player.PlayerController;
import com.hazard.music.store.AppDb;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicApp extends Application {

    public NetEaseClient client;
    public CookieJar cookies;
    public ExecutorService io;
    public Handler main;
    private PlayerController playerController;
    private AppDb appDb;
    private DownloadManager downloadManager;

    @Override
    public void onCreate() {
        super.onCreate();
        cookies = new CookieJar(this);
        io = Executors.newFixedThreadPool(3);
        main = new Handler(Looper.getMainLooper());
        client = new NetEaseClient(this, cookies, io, main);
        installCrashHandler();
    }

    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                java.io.StringWriter writer = new java.io.StringWriter();
                error.printStackTrace(new java.io.PrintWriter(writer));
                String text = "time=" + new java.util.Date() + "\nthread=" + thread.getName()
                        + "\n\n" + writer;
                java.io.File file = new java.io.File(getFilesDir(), "crash_last.txt");
                java.io.FileOutputStream out = new java.io.FileOutputStream(file, false);
                out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.close();
            } catch (Throwable ignored) {
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    public static MusicApp get(android.content.Context context) {
        return (MusicApp) context.getApplicationContext();
    }

    public synchronized PlayerController playerController() {
        if (playerController == null) playerController = new PlayerController(this);
        return playerController;
    }

    public synchronized PlayerController peekPlayerController() {
        return playerController;
    }

    public synchronized AppDb db() {
        if (appDb == null) appDb = new AppDb(this);
        return appDb;
    }

    public synchronized DownloadManager downloads() {
        if (downloadManager == null) downloadManager = new DownloadManager(this, db(), client, main);
        return downloadManager;
    }

    @Override
    public void onTerminate() {
        if (playerController != null) playerController.release();
        if (io != null) io.shutdownNow();
        super.onTerminate();
    }
}
