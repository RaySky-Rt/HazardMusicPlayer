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
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.hazard.music.MusicApp;
import com.hazard.music.download.DownloadManager;
import com.hazard.music.store.AppDb;
import com.hazard.music.util.ImageLoader;
import com.hazard.music.util.Settings;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

public class SettingsActivity extends Activity {

    private static final int REQ_TREE = 200;

    private MusicApp app;
    private TextView downloadPath;
    private TextView crashView;

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
        Button back = Ui.button(this, "‹", false);
        back.setTextSize(22);
        back.setMinWidth(0);
        back.setPadding(0, 0, 0, 0);
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
        String[] values = {"lossless", "exhigh", "standard"};
        String[] labels = {"无损优先（FLAC，自动回退 320k）", "320 kbps", "标准"};
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
            db.putCache("liked", "[]");
            Ui.toast(this, "列表缓存已清除");
        });
        root.addView(clearLists);
        Button clearImages = Ui.button(this, "清除封面缓存", false);
        clearImages.setOnClickListener(v -> {
            ImageLoader.get(this).clear();
            Ui.toast(this, "封面缓存已清除");
        });
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.topMargin = Ui.dp(this, 8);
        root.addView(clearImages, gap);

        section(root, "崩溃记录");
        crashView = Ui.text(this, readCrash(), 11, Ui.MUTED);
        crashView.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 6));
        root.addView(crashView);
        Button clearCrash = Ui.button(this, "清除崩溃记录", false);
        clearCrash.setOnClickListener(v -> {
            new File(getFilesDir(), "crash_last.txt").delete();
            crashView.setText("无崩溃记录");
        });
        root.addView(clearCrash);

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
                    setResult(RESULT_OK);
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
                        + "ExoPlayer 2.18.7 + libFLAC\n"
                        + "下载目录：" + app.downloads().storageText() + "\n"
                        + "仅供个人使用，接口来自非官方网易云 API。",
                12, Ui.MUTED);
        about.setPadding(0, Ui.dp(this, 4), 0, 0);
        root.addView(about);

        scroll.addView(root);
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
            Ui.toast(this, "无法持久化目录权限：" + e.getMessage());
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

    private String readCrash() {
        File file = new File(getFilesDir(), "crash_last.txt");
        if (!file.isFile()) return "无崩溃记录";
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            int total = 0;
            while ((read = in.read(buffer)) != -1 && total < 20000) {
                out.write(buffer, 0, read);
                total += read;
            }
            String text = out.toString("UTF-8");
            String[] lines = text.split("\n");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(lines.length, 10); i++) {
                sb.append(lines[i]).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return "读取失败：" + e;
        }
    }
}
