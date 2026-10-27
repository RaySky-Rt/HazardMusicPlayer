package com.hazard.music.ui;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.hazard.music.MusicApp;
import com.hazard.music.R;
import com.hazard.music.api.NetEaseClient;
import com.hazard.music.model.Account;
import com.hazard.music.model.Playlist;
import com.hazard.music.model.Song;
import com.hazard.music.player.PlayerController;
import com.hazard.music.util.ImageLoader;
import com.hazard.music.util.Settings;

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

        ImageView more = Ui.icon(this, R.drawable.ic_more, 20, Ui.TEXT);
        more.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        more.setContentDescription("更多功能");
        more.setOnClickListener(v -> showMoreSheet());
        top.addView(more, new FrameLayout.LayoutParams(
                Ui.dp(this, 40), Ui.dp(this, 40), Gravity.END | Gravity.CENTER_VERTICAL));
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
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qualityParams.gravity = Gravity.CENTER_HORIZONTAL;
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


    private LinearLayout newSheetContent() {
        LinearLayout sheet = Ui.column(this);
        sheet.setBackground(Ui.rounded(Ui.SURFACE, 18, this));
        sheet.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16),
                Ui.dp(this, 10) + navigationBarHeight());
        View handle = new View(this);
        handle.setBackgroundColor(Ui.SURFACE_2);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 36), Ui.dp(this, 4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.topMargin = Ui.dp(this, 4);
        sheet.addView(handle, handleParams);
        return sheet;
    }

    private int navigationBarHeight() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    private TextView sheetTitle(LinearLayout sheet, String text) {
        TextView title = Ui.text(this, text, 16, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        sheet.addView(title, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return title;
    }

    private Dialog newSheetDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    private void showSheet(Dialog dialog, LinearLayout sheet, float heightFraction) {
        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.45f);
            window.setGravity(Gravity.BOTTOM);
            window.setWindowAnimations(R.style.BottomSheetAnimation);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = heightFraction > 0
                    ? (int) (getResources().getDisplayMetrics().heightPixels * heightFraction)
                    : ViewGroup.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.BOTTOM;
            window.setAttributes(params);
        }
        dialog.show();
    }

    private LinearLayout menuRow(String title, String subtitle, Runnable action) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        LinearLayout texts = Ui.column(this);
        TextView heading = Ui.text(this, title, 15, Ui.TEXT);
        TextView sub = Ui.text(this, subtitle, 11, Ui.MUTED);
        sub.setPadding(0, Ui.dp(this, 2), 0, 0);
        texts.addView(heading);
        texts.addView(sub);
        row.addView(texts, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView chevron = Ui.icon(this, R.drawable.ic_chevron, 18, Ui.MUTED);
        row.addView(chevron, Ui.lp(Ui.dp(this, 24), Ui.dp(this, 24)));
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private void showMoreSheet() {
        final Dialog dialog = newSheetDialog();
        LinearLayout sheet = newSheetContent();
        sheetTitle(sheet, "更多功能");

        final TextView timerSubtitle = Ui.text(this, timerSummary(), 11, Ui.MUTED);
        LinearLayout timerRow = menuRow("定时停止播放", "", () -> {
            dialog.dismiss();
            showTimerSheet();
        });
        // Replace the empty subtitle inside the row with the live one.
        LinearLayout timerTexts = (LinearLayout) timerRow.getChildAt(0);
        timerTexts.removeViewAt(1);
        timerTexts.addView(timerSubtitle);
        sheet.addView(timerRow);
        sheet.addView(dividerLine());

        sheet.addView(menuRow("收藏到歌单", "选择创建的歌单", () -> {
            dialog.dismiss();
            showPlaylistSheet();
        }));
        sheet.addView(dividerLine());

        String currentQuality = controller.quality();
        if (currentQuality.isEmpty()) currentQuality = Settings.label(Settings.quality(this));
        sheet.addView(menuRow("播放音质", currentQuality, () -> {
            dialog.dismiss();
            showQualityDialog();
        }));

        final Runnable[] ticker = new Runnable[1];
        ticker[0] = () -> {
            if (!dialog.isShowing()) return;
            timerSubtitle.setText(timerSummary());
            handler.postDelayed(ticker[0], 1000);
        };
        dialog.setOnDismissListener(d -> handler.removeCallbacks(ticker[0]));
        showSheet(dialog, sheet, 0);

        handler.post(ticker[0]);
    }

    private View dividerLine() {
        View view = new View(this);
        view.setBackgroundColor(0xFF2A2A2A);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 1))));
        return view;
    }

    private String timerSummary() {
        if (controller.timerWaitingForTrackEnd()) return "等待本曲结束后暂停";
        if (controller.timerRunning()) return "剩余 " + countdownText(controller.timerRemainingMs());
        long last = Settings.timerLastMillis(this);
        return last > 0 ? "上次 " + durationLabel(last) : "定时已关闭";
    }

    private String durationLabel(long millis) {
        long minutes = Math.max(0, millis) / 60000L;
        if (minutes < 60) return minutes + " 分钟";
        long hours = minutes / 60;
        long rest = minutes % 60;
        return rest == 0 ? hours + " 小时" : hours + " 小时 " + rest + " 分钟";
    }

    private String countdownText(long millis) {
        long seconds = (Math.max(0, millis) + 999L) / 1000L;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs);
        return String.format(Locale.US, "%02d:%02d", minutes, secs);
    }

    private void showTimerSheet() {
        final Dialog dialog = newSheetDialog();
        LinearLayout sheet = newSheetContent();
        sheetTitle(sheet, "定时停止播放");

        final TextView status = Ui.text(this, timerSummary(), 12, Ui.MUTED);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 0, 0, Ui.dp(this, 8));
        sheet.addView(status, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        final int[] presets = {10, 20, 30, 45, 60, 90};
        for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
            LinearLayout row = Ui.row(this);
            for (int col = 0; col < 3; col++) {
                final int minutes = presets[rowIndex * 3 + col];
                TextView button = Ui.text(this, minutes + " 分钟", 13, Ui.TEXT);
                button.setGravity(Gravity.CENTER);
                button.setBackground(Ui.rounded(Ui.SURFACE_2, 12, this));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, Ui.dp(this, 42), 1f);
                if (col > 0) params.leftMargin = Ui.dp(this, 8);
                button.setLayoutParams(params);
                button.setOnClickListener(v -> {
                    controller.startTimer(minutes * 60_000L);
                    dialog.dismiss();
                    Ui.toast(this, "定时已开启：" + minutes + " 分钟");
                });
                row.addView(button);
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (rowIndex > 0) rowParams.topMargin = Ui.dp(this, 8);
            sheet.addView(row, rowParams);
        }

        Switch enable = new Switch(this);
        enable.setText("启用定时停止");
        enable.setTextColor(Ui.TEXT);
        enable.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        enable.setChecked(controller.timerRunning());
        enable.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                long last = Settings.timerLastMillis(this);
                if (last <= 0) {
                    button.setChecked(false);
                    Ui.toast(this, "请先选择定时时间");
                    return;
                }
                controller.startTimer(last);
            } else {
                controller.cancelTimer();
            }
            status.setText(timerSummary());
        });
        sheet.addView(enable);

        Switch waitEnd = new Switch(this);
        waitEnd.setText("等待当前歌曲结束后暂停");
        waitEnd.setTextColor(Ui.TEXT);
        waitEnd.setChecked(controller.timerWaitForEnd());
        waitEnd.setOnCheckedChangeListener((button, checked) ->
                controller.setTimerWaitForEnd(checked));
        sheet.addView(waitEnd);

        TextView custom = Ui.text(this, "自定义时间", 14, Ui.ACCENT);
        custom.setGravity(Gravity.CENTER);
        custom.setBackground(Ui.rounded(Ui.SURFACE_2, 12, this));
        custom.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        custom.setOnClickListener(v -> {
            dialog.dismiss();
            showCustomTimeSheet();
        });
        LinearLayout.LayoutParams customParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44));
        customParams.topMargin = Ui.dp(this, 8);
        sheet.addView(custom, customParams);

        final Runnable[] ticker = new Runnable[1];
        ticker[0] = () -> {
            if (!dialog.isShowing()) return;
            status.setText(timerSummary());
            handler.postDelayed(ticker[0], 1000);
        };
        dialog.setOnDismissListener(d -> handler.removeCallbacks(ticker[0]));
        showSheet(dialog, sheet, 0.78f);
        handler.post(ticker[0]);
    }

    private void showCustomTimeSheet() {
        final Dialog dialog = newSheetDialog();
        LinearLayout sheet = newSheetContent();
        sheetTitle(sheet, "自定义停止时间");

        long last = Settings.timerLastMillis(this);
        int lastHour = (int) (last / 3_600_000L);
        int lastMinute = (int) ((last / 60_000L) % 60L);

        LinearLayout columns = Ui.row(this);
        columns.setGravity(Gravity.CENTER);
        NumberPicker hourPicker = new NumberPicker(this);
        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(23);
        hourPicker.setValue(Math.min(23, Math.max(0, lastHour)));
        hourPicker.setWrapSelectorWheel(true);
        hourPicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        hourPicker.setFormatter(value -> String.format(Locale.US, "%02d", value));
        columns.addView(pickerColumn("小时", hourPicker), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        NumberPicker minutePicker = new NumberPicker(this);
        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(59);
        minutePicker.setValue(Math.min(59, Math.max(0, lastMinute)));
        minutePicker.setWrapSelectorWheel(true);
        minutePicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        minutePicker.setFormatter(value -> String.format(Locale.US, "%02d", value));
        columns.addView(pickerColumn("分钟", minutePicker), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sheet.addView(columns);

        TextView confirm = Ui.text(this, "开始倒计时", 14, Color.WHITE);
        confirm.setGravity(Gravity.CENTER);
        confirm.setBackground(Ui.rounded(Ui.ACCENT, 12, this));
        confirm.setOnClickListener(v -> {
            int totalMinutes = hourPicker.getValue() * 60 + minutePicker.getValue();
            if (totalMinutes <= 0) {
                Ui.toast(this, "时间必须大于 0 分钟");
                return;
            }
            controller.startTimer(totalMinutes * 60_000L);
            dialog.dismiss();
            Ui.toast(this, "定时已开启：" + durationLabel(totalMinutes * 60_000L));
        });
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44));
        confirmParams.topMargin = Ui.dp(this, 10);
        sheet.addView(confirm, confirmParams);

        showSheet(dialog, sheet, 0.68f);
    }

    private LinearLayout pickerColumn(String label, NumberPicker picker) {
        LinearLayout column = Ui.column(this);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = Ui.text(this, label, 12, Ui.MUTED);
        column.addView(title);
        column.addView(picker, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 150)));
        return column;
    }

    private void showPlaylistSheet() {
        final Song song = controller.current();
        if (song == null) {
            Ui.toast(this, "当前没有播放歌曲");
            return;
        }
        final Dialog dialog = newSheetDialog();
        final LinearLayout sheet = newSheetContent();
        sheetTitle(sheet, "收藏到歌单");

        final TextView status = Ui.text(this, "加载中…", 12, Ui.MUTED);
        status.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 8));
        sheet.addView(status);
        final LinearLayout list = Ui.column(this);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        sheet.addView(scroll, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        showSheet(dialog, sheet, 0.62f);

        app.client.account(new NetEaseClient.Callback<Account>() {
            @Override
            public void onSuccess(Account account) {
                if (!dialog.isShowing()) return;
                if (account == null) {
                    status.setText("请先登录");
                    return;
                }
                app.client.playlists(account.userId, new NetEaseClient.Callback<List<Playlist>>() {
                    @Override
                    public void onSuccess(List<Playlist> data) {
                        if (!dialog.isShowing()) return;
                        list.removeAllViews();
                        int count = 0;
                        if (data != null) {
                            for (Playlist playlist : data) {
                                if (!playlist.isCreatedBy(account.userId) || playlist.specialType != 0) continue;
                                list.addView(playlistRow(playlist, song, status, dialog));
                                count++;
                            }
                        }
                        status.setText(count == 0 ? "没有创建的歌单" : "选择要收藏到的歌单");
                    }

                    @Override
                    public void onError(String message, int code) {
                        if (dialog.isShowing()) status.setText("歌单加载失败：" + message);
                    }
                });
            }

            @Override
            public void onError(String message, int code) {
                if (dialog.isShowing()) status.setText("账号验证失败：" + message);
            }
        });
    }

    private View playlistRow(Playlist playlist, Song song, TextView status, Dialog dialog) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(Ui.SURFACE_2);
        ImageLoader.get(this).loadCover(playlist.coverUrl, cover, 96);
        row.addView(cover, Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40)));
        LinearLayout texts = Ui.column(this);
        TextView name = Ui.text(this, playlist.name, 14, Ui.TEXT);
        TextView count = Ui.text(this, playlist.trackCount + " 首", 11, Ui.MUTED);
        texts.addView(name);
        texts.addView(count);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = Ui.dp(this, 10);
        row.addView(texts, textParams);
        row.setOnClickListener(v -> {
            status.setText("收藏中…");
            app.client.addToPlaylist(playlist.id, song.id, new NetEaseClient.Callback<Boolean>() {
                @Override
                public void onSuccess(Boolean data) {
                    if (dialog.isShowing()) dialog.dismiss();
                    Ui.toast(PlayerActivity.this, "已收藏到 " + playlist.name);
                }

                @Override
                public void onError(String message, int code) {
                    if (dialog.isShowing()) status.setText("收藏失败：" + message);
                }
            });
        });
        return row;
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

    private void showQualityDialog() {
        final String[] values = {"hires", "lossless", "exhigh", "standard"};
        final String[] labels = {"Hi-Res", "无损", "320 kbps", "标准"};
        String current = Settings.quality(this);
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("播放音质")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    Settings.setQuality(this, values[which]);
                    dialog.dismiss();
                    controller.reloadQuality();
                    Ui.toast(this, "音质已切换为 " + Settings.label(values[which]));
                })
                .setNegativeButton("取消", null)
                .show();
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
