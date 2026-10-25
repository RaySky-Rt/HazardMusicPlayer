package com.hazard.music.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.hazard.music.MusicApp;
import com.hazard.music.model.Song;
import com.hazard.music.player.PlayerController;
import com.hazard.music.util.ImageLoader;

import java.util.Locale;

public class PlayerActivity extends Activity {

    private PlayerController controller;
    private ImageView cover;
    private TextView titleView;
    private TextView artistView;
    private TextView qualityView;
    private TextView currentTime;
    private TextView totalTime;
    private SeekBar seekBar;
    private Button toggleButton;
    private boolean userSeeking;
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
        controller = MusicApp.get(this).playerController();
        build();
    }

    private void build() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 14));

        LinearLayout top = Ui.row(this);
        Button back = Ui.button(this, "‹", false);
        back.setTextSize(22);
        back.setMinWidth(0);
        back.setPadding(0, 0, 0, 0);
        back.setOnClickListener(v -> finish());
        top.addView(back, Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40)));
        TextView screenTitle = Ui.text(this, "正在播放", 16, Ui.TEXT);
        screenTitle.setTypeface(Typeface.DEFAULT_BOLD);
        screenTitle.setPadding(Ui.dp(this, 8), 0, 0, 0);
        top.addView(screenTitle, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        cover = new ImageView(this);
        cover.setBackgroundColor(Ui.SURFACE_2);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int coverSize = Ui.dp(this, 200);
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(coverSize, coverSize);
        coverParams.gravity = Gravity.CENTER_HORIZONTAL;
        coverParams.topMargin = Ui.dp(this, 8);
        root.addView(cover, coverParams);

        titleView = Ui.text(this, "", 20, Ui.TEXT);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = Ui.dp(this, 14);
        root.addView(titleView, titleParams);

        artistView = Ui.text(this, "", 13, Ui.MUTED);
        artistView.setGravity(Gravity.CENTER);
        artistView.setSingleLine(true);
        artistView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(artistView);

        qualityView = Ui.text(this, "", 12, Ui.ACCENT);
        qualityView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams qualityParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qualityParams.topMargin = Ui.dp(this, 4);
        root.addView(qualityView, qualityParams);

        seekBar = new SeekBar(this);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        seekParams.topMargin = Ui.dp(this, 10);
        root.addView(seekBar, seekParams);

        LinearLayout times = Ui.row(this);
        currentTime = Ui.text(this, "0:00", 11, Ui.MUTED);
        totalTime = Ui.text(this, "0:00", 11, Ui.MUTED);
        times.addView(currentTime, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView total = totalTime;
        total.setGravity(Gravity.END);
        times.addView(total);
        root.addView(times, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout controls = Ui.row(this);
        controls.setGravity(Gravity.CENTER);
        Button previous = Ui.button(this, "⏮", false);
        previous.setTextSize(20);
        previous.setOnClickListener(v -> {
            controller.previous();
            update();
        });
        toggleButton = Ui.button(this, "▶", true);
        toggleButton.setTextSize(20);
        toggleButton.setOnClickListener(v -> {
            controller.toggle();
            update();
        });
        Button next = Ui.button(this, "⏭", false);
        next.setTextSize(20);
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
        controlsParams.topMargin = Ui.dp(this, 10);
        root.addView(controls, controlsParams);

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
        Song song = controller.current();
        if (song == null) {
            titleView.setText("没有正在播放");
            artistView.setText("");
            qualityView.setText("");
            toggleButton.setText("▶");
            cover.setImageDrawable(null);
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
        toggleButton.setText(controller.isPlaying() ? "⏸" : "▶");
        ImageLoader.get(this).load(song.picUrl, cover, 400);
        updateProgress();
        handler.removeCallbacks(ticker);
        if (controller.isPlaying()) handler.post(ticker);
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
        toggleButton.setText(controller.isPlaying() ? "⏸" : "▶");
    }

    private static String formatTime(long ms) {
        long total = Math.max(0, ms) / 1000;
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60);
    }
}
