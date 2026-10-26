package com.hazard.music.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;

import com.hazard.music.MusicApp;
import com.hazard.music.api.NetEaseClient;
import com.hazard.music.model.Song;
import com.hazard.music.model.SongUrl;
import com.hazard.music.ui.PlayerActivity;
import com.hazard.music.util.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class PlayerController implements Player.Listener {

    public enum PlayMode {
        REPEAT_ALL("repeat_all", "列表循环"),
        REPEAT_ONE("repeat_one", "单曲循环"),
        SHUFFLE("shuffle", "随机播放");

        final String key;
        final String label;

        PlayMode(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    public interface Listener {
        void onPlaybackChanged();
    }

    private static final String TAG = "Player";

    private final Context context;
    private final MusicApp app;
    private final NetEaseClient client;
    private final Handler main;
    private final SimpleExoPlayer player;
    private final MediaSession session;
    private final List<Song> queue = new ArrayList<>();
    private final List<Integer> shuffleHistory = new ArrayList<>();
    private final Random random = new Random();
    private PlayMode mode;
    private String queueTitle = "";

    private int index = -1;
    private Song current;
    private String quality = "";
    private boolean trial;
    private boolean loading;
    private String error = "";
    private int qualityAttempt;
    private int loadToken;
    private long lastHistorySongId;
    private Listener listener;

    public PlayerController(Context context) {
        this.context = context.getApplicationContext();
        this.app = MusicApp.get(this.context);
        this.client = app.client;
        this.main = app.main;
        this.mode = parseMode(Settings.playMode(this.context));

        DefaultRenderersFactory factory = new DefaultRenderersFactory(this.context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true);
        player = new SimpleExoPlayer.Builder(this.context, factory).build();
        player.setAudioAttributes(new com.google.android.exoplayer2.audio.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(), true);
        player.setHandleAudioBecomingNoisy(true);
        applyRepeatMode();
        player.setWakeMode(C.WAKE_MODE_NETWORK);
        player.addListener(this);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(
                    PlaybackService.CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW));
        }

        session = new MediaSession(this.context, "HazardSession");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setSessionActivity(PendingIntent.getActivity(this.context, 0,
                new Intent(this.context, PlayerActivity.class), PendingIntent.FLAG_UPDATE_CURRENT));
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() {
                Log.i(TAG, "session onPlay");
                play();
            }
            @Override public void onPause() {
                Log.i(TAG, "session onPause");
                pause();
            }
            @Override public void onStop() {
                Log.i(TAG, "session onStop");
                stop();
            }
            @Override public void onSkipToNext() {
                Log.i(TAG, "session next");
                next();
            }
            @Override public void onSkipToPrevious() {
                Log.i(TAG, "session previous");
                previous();
            }
            @Override public void onSeekTo(long pos) {
                Log.i(TAG, "session seek " + pos);
                seekTo(pos);
            }
        });
        session.setActive(true);
        updateSession();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        if (listener != null) main.post(listener::onPlaybackChanged);
    }

    public void playQueue(List<Song> songs, int start, String title) {
        if (songs == null || songs.isEmpty()) return;
        queue.clear();
        queue.addAll(songs);
        shuffleHistory.clear();
        queueTitle = title == null || title.trim().isEmpty() ? "当前播放" : title.trim();
        index = Math.max(0, Math.min(start, queue.size() - 1));
        lastHistorySongId = 0;
        applyRepeatMode();
        PlaybackService.start(context);
        loadCurrent(0);
    }

    public String queueTitle() {
        return queueTitle;
    }

    public List<Song> queue() {
        return new ArrayList<>(queue);
    }

    public int queueIndex() {
        return index;
    }

    public void playAt(int position) {
        if (position < 0 || position >= queue.size()) return;
        index = position;
        loadCurrent(0);
    }

    private void loadCurrent(int attempt) {
        if (queue.isEmpty()) return;
        final String[] levels = Settings.levels(context);
        final int level = Math.max(0, Math.min(attempt, levels.length - 1));
        final Song song = queue.get(index);
        current = song;
        Uri localUri = localUri(song);
        if (localUri != null) {
            loadToken++;
            loading = false;
            quality = "本地";
            Log.i(TAG, "play local path=" + song.localPath);
            trial = false;
            error = "";
            player.stop();
            player.setMediaItem(MediaItem.fromUri(localUri));
            player.prepare();
            player.play();
            notifyChanged();
            updateSession();
            updateNotification();
            return;
        }
        qualityAttempt = level;
        loading = true;
        error = "";
        trial = false;
        quality = "";
        final int token = ++loadToken;
        notifyChanged();
        updateSession();
        updateNotification();

        client.songUrl(song.id, levels[level], new NetEaseClient.Callback<SongUrl>() {
            @Override
            public void onSuccess(SongUrl data) {
                if (token != loadToken) return;
                loading = false;
                quality = data.qualityText();
                trial = data.trial;
                error = "";
                Log.i(TAG, "play url type=" + data.type + " level=" + data.level
                        + " trial=" + data.trial + " quality=" + quality);
                player.stop();
                player.setMediaItem(MediaItem.fromUri(data.url));
                player.prepare();
                player.play();
                notifyChanged();
                updateSession();
                updateNotification();
            }

            @Override
            public void onError(String message, int code) {
                if (token != loadToken) return;
                if (level + 1 < levels.length) {
                    Log.w(TAG, "quality " + levels[level] + " failed: " + message + ", fallback");
                    loadCurrent(level + 1);
                } else {
                    loading = false;
                    error = message;
                    notifyChanged();
                    updateSession();
                    updateNotification();
                }
            }
        });
    }

    public void play() {
        if (current == null) return;
        if (player.getPlaybackState() == Player.STATE_IDLE
                || player.getPlaybackState() == Player.STATE_ENDED) {
            loadCurrent(qualityAttempt);
            return;
        }
        player.play();
    }

    public void pause() {
        player.pause();
    }

    public void toggle() {
        if (player.isPlaying()) pause(); else play();
    }

    public void next() {
        if (queue.isEmpty()) return;
        if (mode == PlayMode.SHUFFLE) {
            playRandom();
            return;
        }
        if (index + 1 < queue.size()) {
            index++;
            loadCurrent(0);
        } else {
            index = 0;
            loadCurrent(0);
        }
    }

    public void previous() {
        if (queue.isEmpty()) return;
        if (player.getCurrentPosition() > 3000) {
            seekTo(0);
            return;
        }
        if (mode == PlayMode.SHUFFLE) {
            if (!shuffleHistory.isEmpty()) {
                int previous = shuffleHistory.remove(shuffleHistory.size() - 1);
                if (previous >= 0 && previous < queue.size()) {
                    index = previous;
                    loadCurrent(0);
                    return;
                }
            }
            seekTo(0);
            return;
        }
        if (index > 0) {
            index--;
        } else {
            index = queue.size() - 1;
        }
        loadCurrent(0);
    }

    private void playRandom() {
        if (queue.isEmpty()) return;
        if (queue.size() == 1) {
            loadCurrent(0);
            return;
        }
        if (index >= 0) {
            shuffleHistory.add(index);
            if (shuffleHistory.size() > 100) shuffleHistory.remove(0);
        }
        int next = index;
        while (next == index) next = random.nextInt(queue.size());
        index = next;
        loadCurrent(0);
    }

    public PlayMode playMode() {
        return mode;
    }

    public String playModeLabel() {
        return mode.label;
    }

    public void cyclePlayMode() {
        PlayMode[] modes = PlayMode.values();
        setPlayMode(modes[(mode.ordinal() + 1) % modes.length]);
    }

    public void setPlayMode(PlayMode next) {
        if (next == null) next = PlayMode.REPEAT_ALL;
        if (mode == next) return;
        mode = next;
        Settings.setPlayMode(context, mode.key);
        if (mode == PlayMode.SHUFFLE) shuffleHistory.clear();
        applyRepeatMode();
        notifyChanged();
    }

    private void applyRepeatMode() {
        player.setRepeatMode(mode == PlayMode.REPEAT_ONE
                ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }

    private static PlayMode parseMode(String value) {
        for (PlayMode mode : PlayMode.values()) {
            if (mode.key.equals(value)) return mode;
        }
        return PlayMode.REPEAT_ALL;
    }

    public void seekTo(long positionMs) {
        player.seekTo(Math.max(0, positionMs));
        updateSession();
        notifyChanged();
    }

    public void stop() {
        loadToken++;
        player.stop();
        player.clearMediaItems();
        queue.clear();
        shuffleHistory.clear();
        queueTitle = "";
        index = -1;
        current = null;
        quality = "";
        trial = false;
        loading = false;
        error = "";
        notifyChanged();
        updateSession();
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(PlaybackService.NOTIFICATION_ID);
        PlaybackService.stop(context);
    }

    public void release() {
        try {
            player.release();
            session.release();
        } catch (Exception ignored) {
        }
    }

    public Song current() {
        return current;
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public boolean isLoading() {
        return loading;
    }

    public boolean isTrial() {
        return trial;
    }

    public String quality() {
        return quality;
    }

    public String error() {
        return error;
    }

    public long position() {
        return player.getCurrentPosition();
    }

    public long duration() {
        long duration = player.getDuration();
        if (duration > 0) return duration;
        return current == null ? 0 : current.durationMs;
    }

    private Uri localUri(Song song) {
        if (song.localPath == null || song.localPath.isEmpty()) return null;
        if (song.localPath.startsWith("content://")) {
            Uri uri = Uri.parse(song.localPath);
            try (android.content.res.AssetFileDescriptor descriptor =
                         context.getContentResolver().openAssetFileDescriptor(uri, "r")) {
                return descriptor == null ? null : uri;
            } catch (Exception e) {
                return null;
            }
        }
        File file = new File(song.localPath);
        return file.isFile() ? Uri.fromFile(file) : null;
    }

    private void notifyChanged() {
        if (listener != null) main.post(listener::onPlaybackChanged);
    }

    private void updateSession() {
        if (session == null) return;
        int state;
        switch (player.getPlaybackState()) {
            case Player.STATE_BUFFERING:
                state = PlaybackState.STATE_BUFFERING;
                break;
            case Player.STATE_READY:
                state = player.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
                break;
            case Player.STATE_ENDED:
                state = PlaybackState.STATE_STOPPED;
                break;
            default:
                state = PlaybackState.STATE_NONE;
                break;
        }
        PlaybackState.Builder builder = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP
                        | PlaybackState.ACTION_SEEK_TO
                        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(state, player.getCurrentPosition(), player.isPlaying() ? 1.0f : 0.0f);
        session.setPlaybackState(builder.build());

        if (current != null) {
            MediaMetadata.Builder meta = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, current.name)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, current.artist)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, duration());
            session.setMetadata(meta.build());
        }
    }

    private void updateNotification() {
        if (current == null) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(PlaybackService.NOTIFICATION_ID, buildNotification());
    }

    public Notification buildNotification() {
        Intent content = new Intent(context, PlayerActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, content, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(context, PlaybackService.CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }

        String title = current == null ? "Hazard Music" : current.name;
        String text;
        if (current == null) {
            text = "准备就绪";
        } else if (loading) {
            text = "加载中…";
        } else if (!error.isEmpty()) {
            text = "播放失败：" + error;
        } else {
            text = current.artist + (quality.isEmpty() ? "" : " · " + quality) + (trial ? " · 试听" : "");
        }

        builder.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(isPlaying())
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC);

        if (current != null) {
            builder.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_media_previous, "上一首",
                    serviceIntent(PlaybackService.ACTION_PREV, 1)).build());
            builder.addAction(new Notification.Action.Builder(
                    isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                    isPlaying() ? "暂停" : "播放",
                    serviceIntent(PlaybackService.ACTION_TOGGLE, 2)).build());
            builder.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_media_next, "下一首",
                    serviceIntent(PlaybackService.ACTION_NEXT, 3)).build());
            builder.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel, "停止",
                    serviceIntent(PlaybackService.ACTION_STOP, 4)).build());
        }
        return builder.build();
    }

    private PendingIntent serviceIntent(String action, int code) {
        Intent intent = new Intent(context, PlaybackService.class).setAction(action);
        return PendingIntent.getService(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // ---- ExoPlayer.Listener ----

    @Override
    public void onPlaybackStateChanged(int state) {
        Log.i(TAG, "state=" + state + " pos=" + player.getCurrentPosition());
        if (state == Player.STATE_ENDED && !queue.isEmpty()) {
            if (mode == PlayMode.REPEAT_ONE) {
                player.seekTo(0);
                player.play();
                return;
            }
            if (mode == PlayMode.SHUFFLE) {
                next();
                return;
            }
            if (index + 1 < queue.size()) {
                next();
                return;
            }
            index = 0;
            loadCurrent(0);
            return;
        }
        notifyChanged();
        updateSession();
        updateNotification();
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        Log.i(TAG, "isPlaying=" + isPlaying + " pos=" + player.getCurrentPosition());
        if (isPlaying && current != null && current.id != lastHistorySongId) {
            lastHistorySongId = current.id;
            app.db().recordHistory(current, System.currentTimeMillis());
        }
        notifyChanged();
        updateSession();
        updateNotification();
    }

    @Override
    public void onPlayerError(PlaybackException exception) {
        Log.e(TAG, "playerError " + exception.errorCode + " " + exception.getMessage(), exception);
        if (exception instanceof ExoPlaybackException) {
            ExoPlaybackException e = (ExoPlaybackException) exception;
            Log.e(TAG, "type=" + e.type + " renderer=" + e.rendererName);
        }
        if (qualityAttempt + 1 < Settings.levels(context).length) {
            loadCurrent(qualityAttempt + 1);
        } else {
            loading = false;
            error = exception.getMessage() == null ? "播放失败" : exception.getMessage();
            notifyChanged();
            updateSession();
            updateNotification();
        }
    }
}
