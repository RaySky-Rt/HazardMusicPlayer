package com.hazard.music.player;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.hazard.music.MusicApp;

public class PlaybackService extends Service {

    public static final int NOTIFICATION_ID = 1001;
    public static final String CHANNEL_ID = "playback";

    public static final String ACTION_TOGGLE = "com.hazard.music.action.TOGGLE";
    public static final String ACTION_NEXT = "com.hazard.music.action.NEXT";
    public static final String ACTION_PREV = "com.hazard.music.action.PREV";
    public static final String ACTION_STOP = "com.hazard.music.action.STOP";

    public static void start(Context context) {
        Intent intent = new Intent(context, PlaybackService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, PlaybackService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW));
        }
        PlayerController controller = MusicApp.get(this).playerController();
        startForeground(NOTIFICATION_ID, controller.buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        PlayerController controller = MusicApp.get(this).playerController();
        if (ACTION_TOGGLE.equals(action)) {
            controller.toggle();
        } else if (ACTION_NEXT.equals(action)) {
            controller.next();
        } else if (ACTION_PREV.equals(action)) {
            controller.previous();
        } else if (ACTION_STOP.equals(action)) {
            controller.stop();
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
