package com.hazard.music.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hazard.music.MusicApp;
import com.hazard.music.R;
import com.hazard.music.api.NetEaseClient;
import com.hazard.music.model.Account;

import org.json.JSONObject;

public class LoginActivity extends Activity {

    private NetEaseClient client;
    private ImageView qrView;
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean polling;
    private String key;

    private final Runnable pollTask = this::poll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        client = MusicApp.get(this).client;

        LinearLayout root = Ui.column(this);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 24), Ui.dp(this, 28), Ui.dp(this, 24), Ui.dp(this, 24));

        LinearLayout top = Ui.row(this);
        ImageView back = Ui.icon(this, R.drawable.ic_back, 20, Ui.TEXT);
        back.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        back.setOnClickListener(v -> finish());
        top.addView(back, Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40)));
        TextView title = Ui.text(this, "扫码登录", 18, Ui.TEXT);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(Ui.dp(this, 8), 0, 0, 0);
        top.addView(title, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.space(this, 12));

        qrView = new ImageView(this);
        qrView.setBackgroundColor(Color.WHITE);
        qrView.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        qrView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int size = Ui.dp(this, 240);
        root.addView(qrView, Ui.lp(size, size));
        root.addView(Ui.space(this, 16));

        status = Ui.text(this, "获取二维码…", 14, Ui.MUTED);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10));
        root.addView(status, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = Ui.row(this);
        buttons.setGravity(Gravity.CENTER);
        android.widget.Button retry = Ui.button(this, "刷新二维码", false);
        retry.setOnClickListener(v -> begin());
        buttons.addView(retry);

        android.widget.Button cookie = Ui.button(this, "Cookie 登录", false);
        cookie.setOnClickListener(v -> showCookieDialog());
        LinearLayout.LayoutParams cookieParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cookieParams.leftMargin = Ui.dp(this, 8);
        buttons.addView(cookie, cookieParams);
        root.addView(buttons, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.space(this, 16));

        TextView tip = Ui.text(this,
                "请使用网易云音乐 App 扫描二维码。\n二维码有效期约 2 分钟。",
                12, Ui.MUTED);
        tip.setGravity(Gravity.CENTER);
        root.addView(tip, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        begin();
    }

    private void begin() {
        polling = false;
        handler.removeCallbacks(pollTask);
        qrView.setImageDrawable(null);
        setStatus("获取二维码…");
        client.qrKey(new NetEaseClient.Callback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject json) {
                String unikey = json.optString("unikey", "");
                if (unikey.isEmpty()) {
                    setStatus("二维码获取失败，请重试");
                    return;
                }
                key = unikey;
                String url = "https://music.163.com/login?codekey=" + key;
                try {
                    Bitmap bitmap = QrCodeUtil.encode(url, Ui.dp(LoginActivity.this, 240));
                    qrView.setImageBitmap(bitmap);
                    setStatus("请用网易云 App 扫码");
                    polling = true;
                    handler.postDelayed(pollTask, 1200);
                } catch (Exception e) {
                    setStatus("生成二维码失败，请重试");
                }
            }

            @Override
            public void onError(String message, int code) {
                setStatus("二维码获取失败，请重试");
            }
        });
    }

    private void poll() {
        if (!polling || key == null) return;
        client.qrCheck(key, new NetEaseClient.Callback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject json) {
                int code = json.optInt("code", -1);
                if (code == 803) {
                    polling = false;
                    setStatus("登录成功");
                    setResult(RESULT_OK);
                    handler.postDelayed(LoginActivity.this::finish, 600);
                } else if (code == 802) {
                    setStatus("已扫码，请在手机上确认");
                    schedule();
                } else if (code == 801) {
                    setStatus("等待扫码…");
                    schedule();
                } else if (code == 800) {
                    polling = false;
                    setStatus("二维码已过期，正在刷新…");
                    handler.postDelayed(LoginActivity.this::begin, 800);
                } else if (code == 8821 || code == -462 || code == 8810) {
                    polling = false;
                    setStatus("需要安全验证，请改用 Cookie 登录");
                } else {
                    setStatus("等待登录确认…");
                    schedule();
                }
            }

            @Override
            public void onError(String message, int code) {
                setStatus("网络异常，请重试");
                schedule();
            }
        });
    }

    private void schedule() {
        if (polling) handler.postDelayed(pollTask, 2000);
    }

    private void setStatus(String text) {
        status.setText(text);
    }

    private void showCookieDialog() {
        EditText input = new EditText(this);
        input.setHint("MUSIC_U=...; __csrf=...");
        input.setTextSize(12);
        input.setSingleLine(false);
        int padding = Ui.dp(this, 16);
        input.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(this)
                .setTitle("Cookie 登录")
                .setMessage("在浏览器登录 music.163.com，从开发者工具 Cookies 复制 MUSIC_U（可连同 __csrf 一起粘贴）。")
                .setView(input)
                .setPositiveButton("登录", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) {
                        Ui.toast(this, "请粘贴 Cookie");
                        return;
                    }
                    client.cookies().setAll(text);
                    setStatus("验证登录态…");
                    client.account(new NetEaseClient.Callback<Account>() {
                        @Override
                        public void onSuccess(Account account) {
                            if (account != null) {
                                setStatus("登录成功");
                                setResult(RESULT_OK);
                                finish();
                            } else {
                                client.logout();
                                setStatus("Cookie 无效或已过期");
                            }
                        }

                        @Override
                        public void onError(String message, int code) {
                            client.logout();
                            setStatus("Cookie 无效或已过期");
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        polling = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
