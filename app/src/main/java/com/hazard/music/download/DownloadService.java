package com.hazard.music.download;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.hazard.music.MusicApp;

public class DownloadService extends Service {

    public static final String CHANNEL_ID = "download";
    public static final int NOTIFICATION_ID = 1002;

    public static final String ACTION_PAUSE_ALL = "com.hazard.music.action.DOWNLOAD_PAUSE_ALL";
    public static final String ACTION_RESUME_ALL = "com.hazard.music.action.DOWNLOAD_RESUME_ALL";
    public static final String ACTION_STOP = "com.hazard.music.action.DOWNLOAD_STOP";

    public static void start(Context context) {
        Intent intent = new Intent(context, DownloadService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, DownloadService.class));
    }

    public static PendingIntent action(Context context, String action, int code) {
        Intent intent = new Intent(context, DownloadService.class).setAction(action);
        return PendingIntent.getService(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW));
        }
        DownloadManager manager = MusicApp.get(this).downloads();
        startForeground(NOTIFICATION_ID, manager.buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        DownloadManager manager = MusicApp.get(this).downloads();
        if (ACTION_PAUSE_ALL.equals(action)) {
            manager.pauseAll();
        } else if (ACTION_RESUME_ALL.equals(action)) {
            manager.resumeAll();
        } else if (ACTION_STOP.equals(action)) {
            manager.pauseAll();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
