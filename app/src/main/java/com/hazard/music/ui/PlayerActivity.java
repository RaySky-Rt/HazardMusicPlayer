package com.hazard.music.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.hazard.music.MusicApp;
import com.hazard.music.R;
import com.hazard.music.api.NetEaseClient;
import com.hazard.music.model.Account;
import com.hazard.music.model.Song;
import com.hazard.music.player.PlayerController;
import com.hazard.music.util.ImageLoader;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PlayerActivity extends Activity {

    private MusicApp app;
    private PlayerController controller;
    private ImageView cover;
    private TextView playlistView;
    private TextView titleView;
    private TextView artistView;
    private TextView qualityView;
    private TextView currentTime;
    private TextView totalTime;
    private SeekBar seekBar;
    private ImageView toggleButton;
    private ImageView modeButton;
    private ImageView heartButton;
    private boolean userSeeking;
    private boolean likeLoading;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (controller != null && controller.isPlaying()) {
                handler.postDelayed(this, 500);
            }
        }
    };

    private final PlayerController.Listener listener = this::update;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = MusicApp.get(this);
        controller = app.playerController();
        loadLikedCache();
        build();
    }

    private void build() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 6));

        FrameLayout top = new FrameLayout(this);
        ImageView back = Ui.icon(this, R.drawable.ic_back, 20, Ui.TEXT);
        back.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(
                Ui.dp(this, 40), Ui.dp(this, 40), Gravity.START | Gravity.CENTER_VERTICAL);
        top.addView(back, backParams);
        playlistView = Ui.text(this, "", 16, Ui.TEXT);
        playlistView.setTypeface(Typeface.DEFAULT_BOLD);
        playlistView.setGravity(Gravity.CENTER);
        playlistView.setSingleLine(true);
        playlistView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        playlistView.setPadding(Ui.dp(this, 48), 0, Ui.dp(this, 48), 0);
        top.addView(playlistView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        root.addView(top, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 40)));

        cover = new ImageView(this);
        cover.setBackgroundColor(Ui.SURFACE_2);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int coverSize = Ui.dp(this, 180);
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(coverSize, coverSize);
        coverParams.gravity = Gravity.CENTER_HORIZONTAL;
        coverParams.topMargin = Ui.dp(this, 6);
        root.addView(cover, coverParams);

        LinearLayout songRow = Ui.row(this);
        songRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout songText = Ui.column(this);
        titleView = Ui.text(this, "", 20, Ui.TEXT);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        artistView = Ui.text(this, "", 13, Ui.MUTED);
        artistView.setSingleLine(true);
        artistView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        songText.addView(titleView);
        songText.addView(artistView);
        songRow.addView(songText, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        heartButton = Ui.icon(this, R.drawable.ic_favorite_border, 24, Ui.MUTED);
        heartButton.setBackground(Ui.rounded(Ui.SURFACE_2, 24, this));
        heartButton.setPadding(Ui.dp(this, 11), Ui.dp(this, 11), Ui.dp(this, 11), Ui.dp(this, 11));
        heartButton.setOnClickListener(v -> toggleLike());
        songRow.addView(heartButton, Ui.lp(Ui.dp(this, 48), Ui.dp(this, 48)));
        LinearLayout.LayoutParams songRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        songRowParams.topMargin = Ui.dp(this, 12);
        root.addView(songRow, songRowParams);

        qualityView = Ui.text(this, "", 12, Ui.ACCENT);
        qualityView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams qualityParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qualityParams.topMargin = Ui.dp(this, 6);
        root.addView(qualityView, qualityParams);

        seekBar = new SeekBar(this);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        seekParams.topMargin = Ui.dp(this, 6);
        root.addView(seekBar, seekParams);

        LinearLayout times = Ui.row(this);
        currentTime = Ui.text(this, "0:00", 11, Ui.MUTED);
        totalTime = Ui.text(this, "0:00", 11, Ui.MUTED);
        times.addView(currentTime, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        totalTime.setGravity(Gravity.END);
        times.addView(totalTime);
        root.addView(times, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout controls = Ui.row(this);
        controls.setGravity(Gravity.CENTER);
        ImageView previous = Ui.icon(this, R.drawable.ic_prev, 22, Ui.TEXT);
        previous.setBackground(Ui.rounded(Ui.SURFACE_2, 24, this));
        previous.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        previous.setOnClickListener(v -> {
            controller.previous();
            update();
        });
        toggleButton = Ui.icon(this, R.drawable.ic_play, 24, Color.WHITE);
        toggleButton.setBackground(Ui.rounded(Ui.ACCENT, 24, this));
        toggleButton.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        toggleButton.setOnClickListener(v -> {
            controller.toggle();
            update();
        });
        ImageView next = Ui.icon(this, R.drawable.ic_next, 22, Ui.TEXT);
        next.setBackground(Ui.rounded(Ui.SURFACE_2, 24, this));
        next.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        next.setOnClickListener(v -> {
            controller.next();
            update();
        });
        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 72), Ui.dp(this, 48));
        controlParams.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        controls.addView(previous, controlParams);
        controls.addView(toggleButton, controlParams);
        controls.addView(next, controlParams);
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        controlsParams.topMargin = Ui.dp(this, 8);
        root.addView(controls, controlsParams);

        LinearLayout bottom = Ui.row(this);
        bottom.setGravity(Gravity.CENTER);
        modeButton = Ui.icon(this, R.drawable.ic_repeat, 20, Ui.ACCENT);
        modeButton.setBackground(Ui.rounded(Ui.SURFACE_2, 22, this));
        modeButton.setPadding(Ui.dp(this, 11), Ui.dp(this, 10), Ui.dp(this, 11), Ui.dp(this, 10));
        modeButton.setOnClickListener(v -> {
            controller.cyclePlayMode();
            updateModeButton();
            Ui.toast(this, controller.playModeLabel());
        });
        bottom.addView(modeButton, Ui.lp(Ui.dp(this, 56), Ui.dp(this, 44)));

        ImageView queueButton = Ui.icon(this, R.drawable.ic_library, 22, Ui.TEXT);
        queueButton.setBackground(Ui.rounded(Ui.SURFACE_2, 22, this));
        queueButton.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        queueButton.setContentDescription("查看当前播放列表");
        queueButton.setOnClickListener(v -> showQueue());
        LinearLayout.LayoutParams queueParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 56), Ui.dp(this, 44));
        queueParams.leftMargin = Ui.dp(this, 10);
        bottom.addView(queueButton, queueParams);
        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomParams.topMargin = Ui.dp(this, 4);
        root.addView(bottom, bottomParams);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) currentTime.setText(formatTime(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userSeeking = false;
                controller.seekTo(seekBar.getProgress());
            }
        });

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        controller.setListener(listener);
        update();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        controller.setListener(null);
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private void update() {
        playlistView.setText(controller.queueTitle().isEmpty() ? "当前播放" : controller.queueTitle());
        updateModeButton();
        Song song = controller.current();
        if (song == null) {
            titleView.setText("没有正在播放");
            artistView.setText("");
            qualityView.setText("");
            toggleButton.setImageResource(R.drawable.ic_play);
            cover.setImageDrawable(null);
            updateHeart();
            return;
        }
        titleView.setText(song.name);
        artistView.setText(song.artist + (song.album.isEmpty() ? "" : " · " + song.album));
        if (controller.isLoading()) {
            qualityView.setText("加载中…");
            qualityView.setTextColor(Ui.MUTED);
        } else if (!controller.error().isEmpty()) {
            qualityView.setText("播放失败：" + controller.error());
            qualityView.setTextColor(Ui.ACCENT);
        } else {
            qualityView.setText(controller.quality() + (controller.isTrial() ? " · 试听" : ""));
            qualityView.setTextColor(Ui.ACCENT);
        }
        toggleButton.setImageResource(controller.isPlaying()
                ? R.drawable.ic_pause : R.drawable.ic_play);
        ImageLoader.get(this).loadCover(song.picUrl, cover, 400);
        updateHeart();
        updateProgress();
        handler.removeCallbacks(ticker);
        if (controller.isPlaying()) handler.post(ticker);
    }

    private void updateModeButton() {
        if (modeButton == null) return;
        int icon;
        int color;
        switch (controller.playMode()) {
            case REPEAT_ONE:
                icon = R.drawable.ic_repeat_one;
                color = Ui.ACCENT;
                break;
            case SHUFFLE:
                icon = R.drawable.ic_shuffle;
                color = Ui.ACCENT;
                break;
            case REPEAT_ALL:
            default:
                icon = R.drawable.ic_repeat;
                color = Ui.ACCENT;
                break;
        }
        modeButton.setImageResource(icon);
        modeButton.setColorFilter(color);
        modeButton.setContentDescription("播放模式：" + controller.playModeLabel());
    }

    private void updateHeart() {
        if (heartButton == null) return;
        Song song = controller.current();
        boolean liked = song != null && app.likedIds.contains(song.id);
        heartButton.setImageResource(liked ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        heartButton.setColorFilter(liked ? Ui.ACCENT : Ui.MUTED);
        heartButton.setEnabled(song != null);
        heartButton.setContentDescription(liked ? "取消喜欢" : "喜欢");
    }

    private void toggleLike() {
        if (likeLoading) return;
        final Song song = controller.current();
        if (song == null) return;
        final boolean like = !app.likedIds.contains(song.id);
        if (like) app.likedIds.add(song.id); else app.likedIds.remove(song.id);
        likeLoading = true;
        updateHeart();
        app.client.account(new NetEaseClient.Callback<Account>() {
            @Override
            public void onSuccess(Account account) {
                if (account == null) {
                    revertLike(song, like, "请先登录");
                    return;
                }
                app.client.like(song.id, account.userId, like, new NetEaseClient.Callback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean data) {
                        likeLoading = false;
                        saveLikedCache();
                        updateHeart();
                        Ui.toast(PlayerActivity.this, like ? "已喜欢：" + song.name : "已取消喜欢：" + song.name);
                    }

                    @Override
                    public void onError(String message, int code) {
                        revertLike(song, like, "操作失败：" + message);
                    }
                });
            }

            @Override
            public void onError(String message, int code) {
                revertLike(song, like, "账号验证失败：" + message);
            }
        });
    }

    private void revertLike(Song song, boolean like, String message) {
        if (like) app.likedIds.remove(song.id); else app.likedIds.add(song.id);
        likeLoading = false;
        updateHeart();
        Ui.toast(this, message);
    }

    private void loadLikedCache() {
        if (!app.likedIds.isEmpty()) return;
        try {
            String raw = app.db().getCache("liked");
            if (raw == null || raw.isEmpty()) return;
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                long id = array.optLong(i, 0);
                if (id > 0) app.likedIds.add(id);
            }
        } catch (Exception ignored) {
        }
    }

    private void saveLikedCache() {
        JSONArray array = new JSONArray();
        for (Long id : app.likedIds) array.put(id);
        final String value = array.toString();
        app.io.execute(() -> {
            try {
                app.db().putCache("liked", value);
            } catch (Exception ignored) {
            }
        });
    }

    private void showQueue() {
        final List<Song> queue = controller.queue();
        if (queue.isEmpty()) {
            Ui.toast(this, "播放列表为空");
            return;
        }
        List<String> rows = new ArrayList<>(queue.size());
        for (Song song : queue) rows.add(song.name + " · " + song.artist);

        final Dialog dialog = new Dialog(this);
        if (dialog.getWindow() != null) dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = Ui.column(this);
        sheet.setBackground(Ui.rounded(Ui.SURFACE, 18, this));
        sheet.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 8));

        View handle = new View(this);
        handle.setBackgroundColor(Ui.SURFACE_2);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 36), Ui.dp(this, 4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.topMargin = Ui.dp(this, 4);
        sheet.addView(handle, handleParams);

        TextView title = Ui.text(this, controller.queueTitle().isEmpty()
                ? "当前播放列表" : controller.queueTitle(), 16, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        sheet.addView(title, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(0xFF2A2A2A));
        list.setDividerHeight(1);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setCacheColorHint(Color.TRANSPARENT);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, rows) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(position == controller.queueIndex() ? Ui.ACCENT : Ui.TEXT);
                view.setBackgroundColor(Ui.SURFACE);
                view.setPadding(Ui.dp(PlayerActivity.this, 16), Ui.dp(PlayerActivity.this, 12),
                        Ui.dp(PlayerActivity.this, 16), Ui.dp(PlayerActivity.this, 12));
                return view;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            controller.playAt(position);
            update();
            dialog.dismiss();
        });
        sheet.addView(list, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dialog.setContentView(sheet);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.45f);
            window.setGravity(Gravity.BOTTOM);
            window.setWindowAnimations(R.style.BottomSheetAnimation);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.62f);
            params.gravity = Gravity.BOTTOM;
            window.setAttributes(params);
        }
        dialog.show();
    }

    private void updateProgress() {
        if (controller.current() == null) return;
        long duration = controller.duration();
        seekBar.setMax((int) Math.max(1, duration));
        if (!userSeeking) {
            seekBar.setProgress((int) Math.max(0, controller.position()));
            currentTime.setText(formatTime(controller.position()));
        }
        totalTime.setText(formatTime(duration));
    }

    private static String formatTime(long ms) {
        long total = Math.max(0, ms) / 1000;
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60);
    }
}
