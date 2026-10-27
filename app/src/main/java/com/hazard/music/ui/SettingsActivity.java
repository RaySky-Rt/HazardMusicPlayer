package com.hazard.music.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.hazard.music.MusicApp;
import com.hazard.music.R;
import com.hazard.music.download.DownloadManager;
import com.hazard.music.store.AppDb;
import com.hazard.music.util.ImageLoader;
import com.hazard.music.util.Settings;


public class SettingsActivity extends Activity {

    private static final int REQ_TREE = 200;

    private MusicApp app;
    private TextView downloadPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = MusicApp.get(this);
        build();
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.column(this);
        int navId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        int navHeight = navId > 0 ? getResources().getDimensionPixelSize(navId) : 0;
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16),
                Ui.dp(this, 24) + navHeight);

        LinearLayout top = Ui.row(this);
        ImageView back = Ui.icon(this, R.drawable.ic_back, 20, Ui.TEXT);
        back.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        back.setOnClickListener(v -> finish());
        top.addView(back, Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40)));
        TextView title = Ui.text(this, "设置", 18, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(Ui.dp(this, 8), 0, 0, 0);
        top.addView(title);
        root.addView(top);

        section(root, "播放音质");
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        String[] values = {"hires", "lossless", "exhigh", "standard"};
        String[] labels = {"Hi-Res（FLAC 24bit，自动回退无损）", "无损优先（FLAC，自动回退 320k）", "320 kbps", "标准"};
        String current = Settings.quality(this);
        for (int i = 0; i < values.length; i++) {
            RadioButton radio = new RadioButton(this);
            radio.setText(labels[i]);
            radio.setTextColor(Ui.TEXT);
            radio.setTextSize(14);
            radio.setId(i + 1);
            if (values[i].equals(current)) radio.setChecked(true);
            group.addView(radio);
        }
        group.setOnCheckedChangeListener((g, checkedId) -> {
            int index = checkedId - 1;
            if (index >= 0 && index < values.length) {
                Settings.setQuality(this, values[index]);
                Ui.toast(this, "音质已切换为 " + Settings.label(values[index]));
            }
        });
        root.addView(group);

        section(root, "下载");
        downloadPath = Ui.text(this, app.downloads().storageText(), 12, Ui.MUTED);
        downloadPath.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 6));
        root.addView(downloadPath);
        Button redetect = Ui.button(this, "重新探测下载目录", false);
        redetect.setOnClickListener(v -> {
            DownloadManager manager = app.downloads();
            manager.resetRoot();
            downloadPath.setText(manager.storageText());
            Ui.toast(this, "已重新探测");
        });
        root.addView(redetect);
        Button chooseTree = Ui.button(this, "选择 SAF 公共目录", true);
        chooseTree.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, REQ_TREE);
        });
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chooseParams.topMargin = Ui.dp(this, 8);
        root.addView(chooseTree, chooseParams);
        Button restoreTree = Ui.button(this, "恢复默认（SD 专属）", false);
        restoreTree.setOnClickListener(v -> {
            Settings.setDownloadTree(this, "");
            app.downloads().resetRoot();
            downloadPath.setText(app.downloads().storageText());
            Ui.toast(this, "已恢复默认目录");
        });
        LinearLayout.LayoutParams restoreParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        restoreParams.topMargin = Ui.dp(this, 8);
        root.addView(restoreTree, restoreParams);
        TextView safNote = Ui.text(this,
                "注：部分 FiiO 固件的文件选择器没有“选择”按钮，无法确认 SAF 目录；此时请使用默认 SD 专属目录。",
                11, Ui.MUTED);
        safNote.setPadding(0, Ui.dp(this, 6), 0, 0);
        root.addView(safNote);

        section(root, "缓存");
        Button clearLists = Ui.button(this, "清除推荐/歌单缓存", false);
        clearLists.setOnClickListener(v -> {
            AppDb db = app.db();
            db.putCache("daily", "[]");
            db.putCache("playlists", "[]");
            db.putCache("albums", "[]");
            db.putCache("recommended", "[]");
            db.putCache("liked", "[]");
            Ui.toast(this, "列表缓存已清除");
        });
        root.addView(clearLists);
        Button clearHistory = Ui.button(this, "清空播放历史", false);
        clearHistory.setOnClickListener(v -> {
            app.db().clearHistory();
            Ui.toast(this, "播放历史已清空");
        });
        LinearLayout.LayoutParams historyGap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        historyGap.topMargin = Ui.dp(this, 8);
        root.addView(clearHistory, historyGap);

        Button clearImages = Ui.button(this, "清除封面缓存", false);
        clearImages.setOnClickListener(v -> {
            ImageLoader.get(this).clear();
            Ui.toast(this, "封面缓存已清除");
        });
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.topMargin = Ui.dp(this, 8);
        root.addView(clearImages, gap);

        section(root, "账号");
        Button logout = Ui.button(this, "退出登录", false);
        logout.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("退出登录")
                .setMessage("将清除 Cookie 与本地列表缓存，下载文件保留。")
                .setPositiveButton("退出", (dialog, which) -> {
                    app.client.logout();
                    app.db().putCache("account", "");
                    app.db().putCache("playlists", "[]");
                    app.db().putCache("albums", "[]");
                    app.db().putCache("liked", "[]");
                    Intent out = new Intent();
                    out.putExtra("logout", true);
                    setResult(RESULT_OK, out);
                    finish();
                })
                .setNegativeButton("取消", null)
                .show());
        root.addView(logout);

        section(root, "关于");
        String version = "0.1";
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = info.versionName;
        } catch (Exception ignored) {
        }
        TextView about = Ui.text(this,
                "Hazard Music " + version + "\n"
                        + "下载目录：" + app.downloads().storageText(),
                12, Ui.MUTED);
        about.setPadding(0, Ui.dp(this, 4), 0, 0);
        root.addView(about);

        scroll.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_TREE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) {
            Ui.toast(this, "无法保存目录权限，请重试");
            return;
        }
        Settings.setDownloadTree(this, uri.toString());
        app.downloads().resetRoot();
        downloadPath.setText(app.downloads().storageText());
        setResult(RESULT_OK);
        Ui.toast(this, "已选择下载目录");
    }

    private void section(LinearLayout root, String title) {
        TextView view = Ui.text(this, title, 14, Ui.ACCENT);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 18);
        params.bottomMargin = Ui.dp(this, 6);
        root.addView(view, params);
    }

}
