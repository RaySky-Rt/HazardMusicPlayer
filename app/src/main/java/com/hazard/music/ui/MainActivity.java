package com.hazard.music.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.hazard.music.MusicApp;
import com.hazard.music.api.NetEaseClient;
import com.hazard.music.download.DownloadManager;
import com.hazard.music.model.DownloadItem;
import com.hazard.music.model.Account;
import com.hazard.music.model.Album;
import com.hazard.music.model.Playlist;
import com.hazard.music.model.Song;
import com.hazard.music.player.PlayerController;
import com.hazard.music.store.AppDb;
import com.hazard.music.util.ImageLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;

public class MainActivity extends Activity {

    private static final int REQ_LOGIN = 1;
    private static final int REQ_STORAGE = 100;
    private static final int REQ_SETTINGS = 2;

    private MusicApp app;
    private NetEaseClient client;

    private FrameLayout content;
    private Button tabRecommend;
    private Button tabSearch;
    private Button tabMine;
    private Button tabDownloads;
    private TextView status;
    private LinearLayout topBar;
    private LinearLayout bottomTabs;
    private TextView appTitle;
    private Button topAction;
    private boolean subMode;

    private PlayerController playerController;
    private LinearLayout miniBar;
    private TextView miniTitle;
    private ImageView miniCover;
    private TextView miniSub;
    private Button miniToggle;
    private Button miniNext;

    private final PlayerController.Listener playbackListener = this::updateMiniPlayer;

    private DownloadManager downloadManager;
    private DownloadAdapter downloadAdapter;
    private TextView downloadInfo;
    private final DownloadManager.Listener downloadsListener = this::onDownloadsChanged;
    private final DownloadAdapter.Listener downloadRowListener = new DownloadAdapter.Listener() {
        
        public void onPlay(DownloadItem item) {
            playDownloaded(item);
        }

        
        public void onAction(DownloadItem item) {
            handleDownloadAction(item);
        }
    };

    private int tab = 0;

    private Account account;
    private final Set<Long> likedIds = new HashSet<>();
    private final List<Song> recommendSongs = new ArrayList<>();
    private final List<Song> searchSongs = new ArrayList<>();
    private final List<Playlist> playlists = new ArrayList<>();
    private final List<Album> albums = new ArrayList<>();

    private boolean recommendLoading;
    private boolean recommendLoaded;
    private String recommendError = "";

    private boolean searchLoading;
    private String searchError = "";
    private String searchKeyword = "";

    private boolean accountLoading;
    private boolean accountChecked;
    private boolean likesLoading;
    private boolean likesLoaded;
    private boolean playlistsLoading;
    private boolean playlistsLoaded;
    private boolean albumsLoading;
    private boolean albumsLoaded;
    private String albumsError = "";
    private String mineError = "";

    private Runnable currentBack;
    private String subTitle;
    private List<Song> subSongs;
    private Runnable subBack;
    private Playlist subPlaylist;
    private Album subAlbum;
    private int subSeq;
    private boolean subLoading;
    private String subError = "";

    private final SongAdapter.Listener songListener = new SongAdapter.Listener() {
        @Override
        public void onSongClick(Song song) {
            playFrom(song);
        }

        @Override
        public void onLike(Song song, boolean like) {
            toggleLike(song, like);
        }

        @Override
        public void onDownload(Song song) {
            enqueueDownload(song);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = MusicApp.get(this);
        client = app.client;
        playerController = app.peekPlayerController();
        downloadManager = app.downloads();
        buildRoot();
        showTab(0);
        requestStoragePermission();
        loadCache();
        loadAccount();
        loadRecommend();
    }

    @Override
    protected void onResume() {
        super.onResume();
        playerController = app.peekPlayerController();
        if (playerController != null) playerController.setListener(playbackListener);
        downloadManager.setListener(downloadsListener);
        updateMiniPlayer();
        onDownloadsChanged();
    }

    @Override
    protected void onPause() {
        if (playerController != null) playerController.setListener(null);
        if (downloadManager != null) downloadManager.setListener(null);
        super.onPause();
    }

    private void buildRoot() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);

        topBar = Ui.row(this);
        topBar.setBackgroundColor(Ui.BG);
        topBar.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 6));

        LinearLayout titleBox = Ui.column(this);
        appTitle = Ui.text(this, "每日推荐", 19, Ui.TEXT);
        appTitle.setTypeface(Typeface.DEFAULT_BOLD);
        titleBox.addView(appTitle);
        status = Ui.text(this, "", 11, Ui.MUTED);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setVisibility(View.GONE);
        titleBox.addView(status);
        topBar.addView(titleBox, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        topAction = Ui.button(this, "刷新", false);
        topAction.setTextSize(11);
        topAction.setMinHeight(0);
        topAction.setMinWidth(0);
        topAction.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
        topAction.setOnClickListener(v -> {
            if (tab == 0) refreshRecommend();
            else if (tab == 2) refreshMine();
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 34));
        actionParams.setMargins(0, 0, Ui.dp(this, 6), 0);
        topBar.addView(topAction, actionParams);

        Button settings = Ui.button(this, "设置", false);
        settings.setTextSize(11);
        settings.setMinHeight(0);
        settings.setMinWidth(0);
        settings.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
        settings.setOnClickListener(v -> startActivityForResult(
                new Intent(this, SettingsActivity.class), REQ_SETTINGS));
        topBar.addView(settings, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 34)));
        root.addView(topBar);
        root.addView(divider());

        content = new FrameLayout(this);
        root.addView(content, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        miniBar = Ui.row(this);
        miniBar.setBackgroundColor(Ui.SURFACE_2);
        miniBar.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        miniBar.setVisibility(View.GONE);
        miniBar.setOnClickListener(v -> startActivity(new Intent(this, PlayerActivity.class)));

        miniCover = new ImageView(this);
        miniCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniCover.setBackgroundColor(Ui.SURFACE);
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 44), Ui.dp(this, 44));
        coverParams.setMargins(0, 0, Ui.dp(this, 10), 0);
        miniBar.addView(miniCover, coverParams);

        LinearLayout miniTexts = Ui.column(this);
        miniTitle = Ui.text(this, "", 15, Ui.TEXT);
        miniTitle.setSingleLine(true);
        miniTitle.setEllipsize(TextUtils.TruncateAt.END);
        miniSub = Ui.text(this, "", 11, Ui.MUTED);
        miniSub.setSingleLine(true);
        miniSub.setEllipsize(TextUtils.TruncateAt.END);
        miniTexts.addView(miniTitle);
        miniTexts.addView(miniSub);
        miniBar.addView(miniTexts, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        miniToggle = Ui.button(this, "▶", false);
        miniToggle.setTextSize(18);
        miniNext = Ui.button(this, "⏭", false);
        miniNext.setTextSize(18);
        LinearLayout.LayoutParams miniButton = new LinearLayout.LayoutParams(
                Ui.dp(this, 48), Ui.dp(this, 40));
        miniButton.setMargins(Ui.dp(this, 4), 0, 0, 0);
        miniBar.addView(miniToggle, miniButton);
        miniBar.addView(miniNext, miniButton);

        miniToggle.setOnClickListener(v -> {
            if (playerController != null) {
                playerController.toggle();
                updateMiniPlayer();
            }
        });
        miniNext.setOnClickListener(v -> {
            if (playerController != null) {
                playerController.next();
                updateMiniPlayer();
            }
        });
        root.addView(miniBar, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(divider());
        bottomTabs = Ui.row(this);
        bottomTabs.setBackgroundColor(Ui.SURFACE);
        bottomTabs.setPadding(Ui.dp(this, 4), Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 2));
        tabRecommend = tabButton("♪ 推荐");
        tabSearch = tabButton("⌕ 搜索");
        tabMine = tabButton("☺ 我的");
        tabDownloads = tabButton("↓ 下载");
        tabRecommend.setOnClickListener(v -> showTab(0));
        tabSearch.setOnClickListener(v -> showTab(1));
        tabMine.setOnClickListener(v -> showTab(2));
        tabDownloads.setOnClickListener(v -> showTab(3));
        bottomTabs.addView(tabRecommend, tabParams());
        bottomTabs.addView(tabSearch, tabParams());
        bottomTabs.addView(tabMine, tabParams());
        bottomTabs.addView(tabDownloads, tabParams());
        root.addView(bottomTabs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));
        root.post(() -> {
            Rect visible = new Rect();
            getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
            int navId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            int navHeight = navId > 0 ? getResources().getDimensionPixelSize(navId) : 0;
            int expected = getResources().getDisplayMetrics().heightPixels - visible.top - navHeight;
            int inset = Math.max(0, root.getHeight() - expected);
            if (inset > 0) root.setPadding(0, 0, 0, Math.min(inset, navHeight));
        });

        setContentView(root);
        updateTabs();
        updateChrome();
    }

    private Button tabButton(String label) {
        Button button = Ui.button(this, label, false);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setTextSize(11);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private View divider() {
        View view = new View(this);
        view.setBackgroundColor(0xFF2A2A2A);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 1))));
        return view;
    }

    private LinearLayout.LayoutParams tabParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private void updateTabs() {
        tabRecommend.setTextColor(tab == 0 ? Ui.ACCENT : Ui.MUTED);
        tabSearch.setTextColor(tab == 1 ? Ui.ACCENT : Ui.MUTED);
        tabMine.setTextColor(tab == 2 ? Ui.ACCENT : Ui.MUTED);
        tabDownloads.setTextColor(tab == 3 ? Ui.ACCENT : Ui.MUTED);
        tabRecommend.setTypeface(tab == 0 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        tabSearch.setTypeface(tab == 1 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        tabMine.setTypeface(tab == 2 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        tabDownloads.setTypeface(tab == 3 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    private void updateChrome() {
        topBar.setVisibility(subMode ? View.GONE : View.VISIBLE);
        bottomTabs.setVisibility(subMode ? View.GONE : View.VISIBLE);
        if (subMode) return;
        String[] titles = {"每日推荐", "搜索", "我的", "下载"};
        appTitle.setText(titles[Math.max(0, Math.min(3, tab))]);
        topAction.setVisibility(tab == 0 || tab == 2 ? View.VISIBLE : View.GONE);
        if (tab == 2 || tab == 3) status.setVisibility(View.GONE);
    }

    private void showTab(int index) {
        tab = index;
        subMode = false;
        currentBack = null;
        subTitle = null;
        subSongs = null;
        subBack = null;
        subPlaylist = null;
        subAlbum = null;
        subLoading = false;
        subError = "";
        content.removeAllViews();
        setStatus("");
        updateTabs();
        updateChrome();
        if (index == 0) {
            content.addView(buildRecommend());
        } else if (index == 1) {
            content.addView(buildSearch());
        } else if (index == 2) {
            content.addView(buildMine());
        } else {
            content.addView(buildDownloads());
        }
    }

    // ---------------- recommend ----------------

    private View buildRecommend() {
        LinearLayout screen = Ui.column(this);

        if (recommendLoading && recommendSongs.isEmpty()) {
            screen.addView(centered("加载中…"), Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else if (recommendSongs.isEmpty()) {
            String text = recommendError.isEmpty()
                    ? (recommendLoaded ? "暂无推荐" : "点击刷新加载每日推荐")
                    : recommendError;
            screen.addView(messageView(text, "刷新", () -> {
                recommendLoaded = false;
                recommendError = "";
                loadRecommend();
            }), Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            ListView list = songList(recommendSongs);
            screen.addView(list, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        return screen;
    }

    private void loadRecommend() {
        if (recommendLoading) return;
        recommendLoading = true;
        recommendError = "";
        if (tab == 0 && currentBack == null) showTab(0);
        client.dailyRecommend(new NetEaseClient.Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> data) {
                recommendLoading = false;
                recommendLoaded = true;
                recommendSongs.clear();
                if (data != null) recommendSongs.addAll(data);
                saveCache("daily", Song.listToJson(recommendSongs));
                if (tab == 0 && currentBack == null) showTab(0);
                setStatus("每日推荐 " + recommendSongs.size() + " 首");
            }

            @Override
            public void onError(String message, int code) {
                recommendLoading = false;
                recommendLoaded = true;
                recommendError = message + " (" + code + ")";
                if (tab == 0 && currentBack == null) showTab(0);
                setStatus("每日推荐失败：" + message);
            }
        });
    }

    private void refreshRecommend() {
        recommendLoaded = false;
        recommendError = "";
        recommendSongs.clear();
        loadRecommend();
    }

    private void refreshMine() {
        loadAccount();
        loadLikes();
        loadPlaylists();
        loadAlbums();
    }

    // ---------------- search ----------------

    private View buildSearch() {
        LinearLayout screen = Ui.column(this);

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 6));
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("搜索歌曲");
        input.setText(searchKeyword);
        input.setSelection(input.getText().length());
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.MUTED);
        input.setTextSize(15);
        input.setBackgroundColor(Ui.SURFACE_2);
        input.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);

        Button go = Ui.button(this, "搜索", true);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        inputParams.setMargins(0, 0, Ui.dp(this, 8), 0);
        bar.addView(input, inputParams);
        bar.addView(go, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        screen.addView(bar);

        Runnable search = () -> doSearch(input.getText().toString());
        go.setOnClickListener(v -> search.run());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search.run();
                return true;
            }
            return false;
        });

        if (searchLoading) {
            screen.addView(centered("搜索中…"), Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else if (!searchError.isEmpty()) {
            screen.addView(messageView(searchError, "重试", search), Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else if (searchSongs.isEmpty()) {
            screen.addView(centered(searchKeyword.isEmpty() ? "输入关键词搜索歌曲" : "没有结果"),
                    Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            screen.addView(songList(searchSongs), Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        return screen;
    }

    private void doSearch(String keyword) {
        String value = keyword == null ? "" : keyword.trim();
        if (value.isEmpty()) {
            Ui.toast(this, "请输入关键词");
            return;
        }
        searchKeyword = value;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(content.getWindowToken(), 0);

        searchLoading = true;
        searchError = "";
        searchSongs.clear();
        if (tab == 1 && currentBack == null) showTab(1);
        client.search(value, 0, new NetEaseClient.Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> data) {
                searchLoading = false;
                searchSongs.clear();
                if (data != null) searchSongs.addAll(data);
                if (tab == 1 && currentBack == null) showTab(1);
                setStatus("搜索到 " + searchSongs.size() + " 首");
            }

            @Override
            public void onError(String message, int code) {
                searchLoading = false;
                searchError = message + " (" + code + ")";
                if (tab == 1 && currentBack == null) showTab(1);
                setStatus("搜索失败：" + message);
            }
        });
    }

    // ---------------- mine ----------------

    private View buildMine() {
        LinearLayout screen = Ui.column(this);

        LinearLayout card = Ui.row(this);
        card.setBackgroundColor(Ui.SURFACE);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));

        String name = account == null ? "未登录" : account.nickname;
        if (name.isEmpty()) name = "已登录";
        String initial = name.isEmpty() ? "?" : new String(Character.toChars(name.codePointAt(0)));

        TextView avatar = Ui.text(this, initial, 19, Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(Ui.ACCENT);
        avatar.setBackground(avatarBg);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 46), Ui.dp(this, 46));
        avatarParams.setMargins(0, 0, Ui.dp(this, 12), 0);
        card.addView(avatar, avatarParams);

        LinearLayout info = Ui.column(this);
        TextView title = Ui.text(this, name, 17, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        info.addView(title);

        String detail;
        if (account == null) {
            detail = accountLoading ? "正在验证登录…" : "登录后同步收藏与歌单";
        } else {
            detail = "UID " + account.userId + (account.vipType > 0 ? " · 会员" : "");
        }
        TextView sub = Ui.text(this, detail, 11, Ui.MUTED);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        sub.setPadding(0, Ui.dp(this, 3), 0, 0);
        info.addView(sub);
        card.addView(info, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (account == null) {
            Button login = Ui.button(this, "登录", true);
            login.setTextSize(12);
            login.setMinHeight(0);
            login.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
            login.setOnClickListener(v -> startActivityForResult(
                    new Intent(this, LoginActivity.class), REQ_LOGIN));
            card.addView(login, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 36)));
        }
        screen.addView(card);

        if (!mineError.isEmpty()) {
            TextView error = Ui.text(this, mineError, 12, Ui.ACCENT);
            error.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
            screen.addView(error);
        }

        if (account == null) {
            screen.addView(centered("登录后查看收藏与歌单"),
                    Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return screen;
        }

        screen.addView(navRow("♥", "我喜欢的音乐", "全部红心歌曲", this::openLiked));
        screen.addView(divider());
        screen.addView(navRow("☆", "收藏的歌单与专辑", "收藏的歌单 / 专辑", this::showCollections));
        screen.addView(divider());

        TextView section = Ui.text(this, "创建的歌单", 14, Ui.TEXT);
        section.setTypeface(Typeface.DEFAULT_BOLD);
        section.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 6));
        screen.addView(section);

        List<Playlist> created = createdPlaylists();
        if (playlistsLoading && playlists.isEmpty()) {
            screen.addView(centered("加载中…"), Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else if (created.isEmpty()) {
            screen.addView(centered(playlistsLoaded ? "没有创建的歌单" : "暂无歌单"),
                    Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            ListView list = new ListView(this);
            list.setDivider(new ColorDrawable(0xFF2A2A2A));
            list.setDividerHeight(1);
            list.setSelector(new ColorDrawable(Color.TRANSPARENT));
            list.setCacheColorHint(Color.TRANSPARENT);
            list.setAdapter(new PlaylistAdapter(this, created, this::openPlaylist));
            screen.addView(list, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        return screen;
    }

    private View navRow(String icon, String title, String subtitle, Runnable click) {
        LinearLayout row = Ui.row(this);
        row.setBackgroundColor(Ui.SURFACE);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 11), Ui.dp(this, 14), Ui.dp(this, 11));
        TextView iconView = Ui.text(this, icon, 19, Ui.ACCENT);
        iconView.setGravity(Gravity.CENTER);
        row.addView(iconView, Ui.lp(Ui.dp(this, 32), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout texts = Ui.column(this);
        TextView titleView = Ui.text(this, title, 15, Ui.TEXT);
        TextView subView = Ui.text(this, subtitle, 11, Ui.MUTED);
        texts.addView(titleView);
        texts.addView(subView);
        row.addView(texts, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = Ui.text(this, "›", 22, Ui.MUTED);
        chevron.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(chevron, Ui.lp(Ui.dp(this, 20), ViewGroup.LayoutParams.MATCH_PARENT));
        row.setOnClickListener(v -> click.run());
        return row;
    }

    // ---------------- downloads ----------------

    private View buildDownloads() {
        LinearLayout screen = Ui.column(this);

        LinearLayout header = Ui.row(this);
        header.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 2));
        header.addView(new View(this), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button pauseAll = Ui.button(this, "全部暂停", false);
        pauseAll.setTextSize(12);
        pauseAll.setOnClickListener(v -> {
            downloadManager.pauseAll();
            onDownloadsChanged();
        });
        Button resumeAll = Ui.button(this, "全部继续", false);
        resumeAll.setTextSize(12);
        resumeAll.setOnClickListener(v -> downloadManager.resumeAll());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.setMargins(Ui.dp(this, 4), 0, 0, 0);
        header.addView(pauseAll, actionParams);
        header.addView(resumeAll, actionParams);
        screen.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));

        downloadInfo = Ui.text(this, downloadManager.storageText(), 11, Ui.MUTED);
        downloadInfo.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 4));
        screen.addView(downloadInfo);

        List<DownloadItem> items = downloadManager.snapshot();
        downloadAdapter = null;
        if (items.isEmpty()) {
            screen.addView(centered("在歌曲列表点击 ↓ 添加下载"),
                    Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            ListView list = new ListView(this);
            list.setDivider(new ColorDrawable(0xFF2A2A2A));
            list.setDividerHeight(1);
            list.setSelector(new ColorDrawable(Color.TRANSPARENT));
            list.setCacheColorHint(Color.TRANSPARENT);
            downloadAdapter = new DownloadAdapter(this, items, downloadRowListener);
            list.setAdapter(downloadAdapter);
            screen.addView(list, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        return screen;
    }

    private void onDownloadsChanged() {
        if (downloadManager == null) downloadManager = app.downloads();
        if (tab != 3) return;
        List<DownloadItem> items = downloadManager.snapshot();
        if (downloadAdapter == null) {
            if (!items.isEmpty()) showTab(3);
            return;
        }
        downloadAdapter.setItems(items);
        downloadAdapter.notifyDataSetChanged();
        if (downloadInfo != null) downloadInfo.setText(downloadManager.storageText());
    }

    private void enqueueDownload(Song song) {
        if (!hasStoragePermission()) {
            requestStoragePermission();
            Ui.toast(this, "请先授予存储权限");
            return;
        }
        downloadManager.enqueue(Collections.singletonList(song));
        Ui.toast(this, "已加入下载：" + song.name);
        setStatus("已加入下载：" + song.name);
    }

    private void playDownloaded(DownloadItem item) {
        List<Song> queue = downloadManager.offlineSongs();
        int index = indexById(queue, item.song.id);
        if (index < 0) {
            Ui.toast(this, "文件不存在，可能已被删除");
            return;
        }
        if (playerController == null) playerController = app.playerController();
        playerController.playQueue(queue, index);
        updateMiniPlayer();
        setStatus("本地播放：" + item.song.name);
    }

    private void handleDownloadAction(DownloadItem item) {
        if (item.isDone()) {
            new AlertDialog.Builder(this)
                    .setTitle("删除下载")
                    .setMessage(item.song.name)
                    .setPositiveButton("删除", (dialog, which) -> {
                        downloadManager.delete(item.song.id);
                        Ui.toast(this, "已删除");
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } else if (item.state == DownloadItem.DOWNLOADING || item.state == DownloadItem.QUEUED) {
            downloadManager.pause(item.song.id);
        } else {
            downloadManager.resume(item.song.id);
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23 && !hasStoragePermission()) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE) {
            if (hasStoragePermission()) {
                if (downloadManager != null) downloadManager.resetRoot();
                Ui.toast(this, "存储权限已授予");
            } else {
                Ui.toast(this, "未授予存储权限，无法下载");
            }
        }
    }

    private void loadCache() {
        app.io.execute(() -> {
            AppDb db = app.db();
            final Account cachedAccount = Account.fromJson(db.getCache("account"));
            final List<Playlist> cachedPlaylists = Playlist.listFromJson(db.getCache("playlists"));
            final List<Album> cachedAlbums = Album.listFromJson(db.getCache("albums"));
            final List<Song> cachedDaily = Song.listFromJson(db.getCache("daily"));
            final Set<Long> cachedLikes = new HashSet<>();
            try {
                JSONArray array = new JSONArray(db.getCache("liked"));
                for (int i = 0; i < array.length(); i++) {
                    long id = array.optLong(i, 0);
                    if (id > 0) cachedLikes.add(id);
                }
            } catch (Exception ignored) {
            }
            app.main.post(() -> {
                if (account == null && cachedAccount != null) account = cachedAccount;
                if (playlists.isEmpty() && !cachedPlaylists.isEmpty()) {
                    playlists.addAll(cachedPlaylists);
                    playlistsLoaded = true;
                }
                if (albums.isEmpty() && !cachedAlbums.isEmpty()) {
                    albums.addAll(cachedAlbums);
                    albumsLoaded = true;
                }
                if (recommendSongs.isEmpty() && !cachedDaily.isEmpty()) {
                    recommendSongs.addAll(cachedDaily);
                    recommendLoaded = true;
                }
                if (likedIds.isEmpty() && !cachedLikes.isEmpty()) {
                    likedIds.addAll(cachedLikes);
                    likesLoaded = true;
                }
                if (currentBack == null && (tab == 0 || tab == 2)) showTab(tab);
            });
        });
    }

    private void saveCache(String key, String value) {
        app.io.execute(() -> {
            try {
                app.db().putCache(key, value);
            } catch (Exception ignored) {
            }
        });
    }

    private void clearCache() {
        saveCache("account", "");
        saveCache("playlists", "[]");
        saveCache("albums", "[]");
        saveCache("liked", "[]");
    }

    private void loadAccount() {
        if (accountLoading) return;
        accountLoading = true;
        mineError = "";
        refreshMineOrCollections();
        client.account(new NetEaseClient.Callback<Account>() {
            @Override
            public void onSuccess(Account data) {
                accountLoading = false;
                accountChecked = true;
                account = data;
                if (account != null) {
                    saveCache("account", account.toJson().toString());
                    likesLoaded = false;
                    playlistsLoaded = false;
                    albumsLoaded = false;
                    albumsError = "";
                    loadLikes();
                    loadPlaylists();
                    loadAlbums();
                } else {
                    likedIds.clear();
                    playlists.clear();
                    albums.clear();
                    likesLoaded = false;
                    playlistsLoaded = false;
                    albumsLoaded = false;
                    albumsError = "";
                    clearCache();
                }
                refreshMineOrCollections();
                setStatus(account == null ? "未登录" : "已登录：" + account.nickname);
            }

            @Override
            public void onError(String message, int code) {
                accountLoading = false;
                accountChecked = true;
                mineError = "获取账号失败：" + message + " (" + code + ")";
                refreshMineOrCollections();
                setStatus("账号验证失败");
            }
        });
    }

    private void loadLikes() {
        if (account == null || likesLoading) return;
        likesLoading = true;
        client.likedIds(account.userId, new NetEaseClient.Callback<Set<Long>>() {
            @Override
            public void onSuccess(Set<Long> data) {
                likesLoading = false;
                likesLoaded = true;
                likedIds.clear();
                if (data != null) likedIds.addAll(data);
                JSONArray arr = new JSONArray();
                for (Long id : likedIds) arr.put(id);
                saveCache("liked", arr.toString());
                refreshSongViews();
            }

            @Override
            public void onError(String message, int code) {
                likesLoading = false;
                likesLoaded = true;
                setStatus("喜欢列表失败：" + message);
            }
        });
    }

    private void loadPlaylists() {
        if (account == null || playlistsLoading) return;
        playlistsLoading = true;
        client.playlists(account.userId, new NetEaseClient.Callback<List<Playlist>>() {
            @Override
            public void onSuccess(List<Playlist> data) {
                playlistsLoading = false;
                playlistsLoaded = true;
                playlists.clear();
                if (data != null) playlists.addAll(data);
                saveCache("playlists", Playlist.listToJson(playlists));
                refreshMineOrCollections();
            }

            @Override
            public void onError(String message, int code) {
                playlistsLoading = false;
                playlistsLoaded = true;
                mineError = "歌单加载失败：" + message + " (" + code + ")";
                refreshMineOrCollections();
            }
        });
    }

    private void loadAlbums() {
        if (account == null || albumsLoading) return;
        albumsLoading = true;
        albumsError = "";
        client.collectedAlbums(new NetEaseClient.Callback<List<Album>>() {
            @Override
            public void onSuccess(List<Album> data) {
                albumsLoading = false;
                albumsLoaded = true;
                albums.clear();
                if (data != null) albums.addAll(data);
                saveCache("albums", Album.listToJson(albums));
                refreshMineOrCollections();
            }

            @Override
            public void onError(String message, int code) {
                albumsLoading = false;
                albumsLoaded = true;
                albumsError = "收藏专辑加载失败：" + message + " (" + code + ")";
                refreshMineOrCollections();
            }
        });
    }

    private List<Playlist> createdPlaylists() {
        List<Playlist> out = new ArrayList<>();
        long uid = account == null ? 0 : account.userId;
        for (Playlist p : playlists) {
            if (p.isCreatedBy(uid)) out.add(p);
        }
        return out;
    }

    private List<Playlist> collectedPlaylists() {
        List<Playlist> out = new ArrayList<>();
        for (Playlist p : playlists) if (p.subscribed) out.add(p);
        return out;
    }

    private void refreshMineOrCollections() {
        if (tab != 2) return;
        if (subTitle != null) return;
        if (currentBack != null) showCollections(); else showTab(2);
    }

    private void showCollections() {
        subMode = true;
        updateChrome();
        subTitle = null;
        subSongs = null;
        subBack = null;
        subPlaylist = null;
        subAlbum = null;
        subLoading = false;
        subError = "";
        Runnable back = () -> showTab(2);
        currentBack = back;
        content.removeAllViews();
        content.addView(buildCollections(back));
    }

    private View buildCollections(Runnable back) {
        LinearLayout screen = Ui.column(this);
        screen.addView(subHeader("收藏", back));

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = Ui.column(this);
        root.setPadding(0, 0, 0, Ui.dp(this, 12));

        LinearLayout actions = Ui.row(this);
        actions.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 2));
        Button refresh = Ui.button(this, "刷新收藏", false);
        refresh.setOnClickListener(v -> {
            loadPlaylists();
            loadAlbums();
        });
        actions.addView(refresh);
        root.addView(actions);

        List<Playlist> collected = collectedPlaylists();
        addSectionLabel(root, "收藏的歌单");
        if (playlistsLoading && collected.isEmpty()) {
            root.addView(centered("加载中…"));
        } else if (collected.isEmpty()) {
            root.addView(centered(playlistsLoaded ? "暂无收藏歌单" : "点击刷新加载收藏"));
        } else {
            for (Playlist p : collected) {
                root.addView(playlistRow(p, () -> openPlaylist(p, this::showCollections)));
                root.addView(divider());
            }
        }

        addSectionLabel(root, "收藏的专辑");
        if (albumsLoading && albums.isEmpty()) {
            root.addView(centered("加载中…"));
        } else if (albums.isEmpty()) {
            String msg = albumsError.isEmpty()
                    ? (albumsLoaded ? "暂无收藏专辑" : "点击刷新加载收藏")
                    : albumsError;
            root.addView(centered(msg));
        } else {
            if (!albumsError.isEmpty()) root.addView(centered(albumsError));
            for (Album a : albums) {
                root.addView(albumRow(a, () -> openAlbum(a, this::showCollections)));
                root.addView(divider());
            }
        }

        scroll.addView(root);
        screen.addView(scroll, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return screen;
    }

    private void addSectionLabel(LinearLayout root, String text) {
        TextView section = Ui.text(this, text, 15, Ui.TEXT);
        section.setTypeface(Typeface.DEFAULT_BOLD);
        section.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 4));
        root.addView(section);
    }

    private View playlistRow(Playlist playlist, Runnable click) {
        LinearLayout row = Ui.row(this);
        row.setBackgroundColor(Ui.SURFACE);
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        TextView icon = Ui.text(this, "♪", 17, Ui.ACCENT);
        icon.setGravity(Gravity.CENTER);
        row.addView(icon, Ui.lp(Ui.dp(this, 30), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout texts = Ui.column(this);
        TextView title = Ui.text(this, playlist.name, 15, Ui.TEXT);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        String sub = playlist.trackCount + " 首";
        if (!playlist.creatorName.isEmpty()) sub += " · " + playlist.creatorName;
        TextView subView = Ui.text(this, sub, 12, Ui.MUTED);
        texts.addView(title);
        texts.addView(subView);
        row.addView(texts, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView chevron = Ui.text(this, "›", 22, Ui.MUTED);
        chevron.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(chevron, Ui.lp(Ui.dp(this, 18), ViewGroup.LayoutParams.MATCH_PARENT));
        row.setOnClickListener(v -> click.run());
        return row;
    }

    private View albumRow(Album album, Runnable click) {
        LinearLayout row = Ui.row(this);
        row.setBackgroundColor(Ui.SURFACE);
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        TextView icon = Ui.text(this, "◎", 17, Ui.ACCENT);
        icon.setGravity(Gravity.CENTER);
        row.addView(icon, Ui.lp(Ui.dp(this, 30), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout texts = Ui.column(this);
        TextView title = Ui.text(this, album.name, 15, Ui.TEXT);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        String sub = album.artist;
        if (album.size > 0) sub += (sub.isEmpty() ? "" : " · ") + album.size + " 首";
        TextView subView = Ui.text(this, sub, 12, Ui.MUTED);
        texts.addView(title);
        texts.addView(subView);
        row.addView(texts, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView chevron = Ui.text(this, "›", 22, Ui.MUTED);
        chevron.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(chevron, Ui.lp(Ui.dp(this, 18), ViewGroup.LayoutParams.MATCH_PARENT));
        row.setOnClickListener(v -> click.run());
        return row;
    }

    private void openAlbum(Album album, Runnable back) {
        subMode = true;
        updateChrome();
        subAlbum = album;
        subPlaylist = null;
        subTitle = album.name;
        subSongs = new ArrayList<>();
        subError = "";
        subLoading = true;
        final int seq = ++subSeq;
        subBack = back;
        currentBack = back;
        content.removeAllViews();
        content.addView(buildSubMessage(album.name, "加载专辑…", null, back));

        client.albumSongs(album.id, new NetEaseClient.Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> data) {
                if (seq != subSeq) return;
                subLoading = false;
                subError = "";
                subSongs = data == null ? new ArrayList<>() : data;
                if (currentBack == back) {
                    content.removeAllViews();
                    content.addView(buildSongsScreen(subTitle, subSongs, back));
                }
                setStatus(subTitle + " · " + subSongs.size() + " 首");
            }

            @Override
            public void onError(String message, int code) {
                if (seq != subSeq) return;
                subLoading = false;
                subError = message + " (" + code + ")";
                if (currentBack == back) {
                    content.removeAllViews();
                    content.addView(buildSubMessage(subTitle, subError, () -> openAlbum(subAlbum, back), back));
                }
            }
        });
    }

    private void openLiked() {
        Playlist liked = null;
        for (Playlist playlist : playlists) {
            if (playlist.specialType == 5) {
                liked = playlist;
                break;
            }
        }
        if (liked == null) {
            Ui.toast(this, "未找到“我喜欢的音乐”歌单");
            return;
        }
        openPlaylist(liked);
    }

    // ---------------- playlist / songs ----------------

    private void openPlaylist(Playlist playlist) {
        openPlaylist(playlist, () -> showTab(tab));
    }

    private void openPlaylist(Playlist playlist, Runnable back) {
        subMode = true;
        updateChrome();
        subPlaylist = playlist;
        subAlbum = null;
        subTitle = playlist.name;
        subSongs = new ArrayList<>();
        subError = "";
        subLoading = true;
        final int seq = ++subSeq;
        subBack = back;
        currentBack = back;
        content.removeAllViews();
        content.addView(buildSubMessage(playlist.name, "加载歌单…", null, back));

        client.playlistSongs(playlist.id, new NetEaseClient.Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> data) {
                if (seq != subSeq) return;
                subLoading = false;
                subError = "";
                subSongs = data == null ? new ArrayList<>() : data;
                if (currentBack == back) {
                    content.removeAllViews();
                    content.addView(buildSongsScreen(subTitle, subSongs, back));
                }
                setStatus(subTitle + " · " + subSongs.size() + " 首");
            }

            @Override
            public void onError(String message, int code) {
                if (seq != subSeq) return;
                subLoading = false;
                subError = message + " (" + code + ")";
                if (currentBack == back) {
                    content.removeAllViews();
                    content.addView(buildSubMessage(subTitle, subError, () -> openPlaylist(subPlaylist, back), back));
                }
            }
        });
    }

    private View buildSubMessage(String title, String message, Runnable retry, Runnable back) {
        LinearLayout screen = Ui.column(this);
        screen.addView(subHeader(title, back));

        LinearLayout center = Ui.column(this);
        center.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        center.addView(centered(message), Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (retry != null) {
            Button retryButton = Ui.button(this, "重试", false);
            retryButton.setOnClickListener(v -> retry.run());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = Ui.dp(this, 12);
            center.addView(retryButton, params);
        }
        screen.addView(center, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return screen;
    }

    private View buildSongsScreen(String title, List<Song> songs, Runnable back) {
        LinearLayout screen = Ui.column(this);
        screen.addView(subHeader(title, back));
        if (songs.isEmpty()) {
            screen.addView(centered("没有歌曲"), Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            screen.addView(songList(songs), Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        return screen;
    }

    private View subHeader(String title, Runnable back) {
        LinearLayout header = Ui.row(this);
        header.setBackgroundColor(Ui.SURFACE);
        header.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        Button backButton = Ui.button(this, "‹", false);
        backButton.setTextSize(22);
        backButton.setMinWidth(0);
        backButton.setPadding(0, 0, 0, 0);
        backButton.setOnClickListener(v -> {
            currentBack = null;
            back.run();
        });
        header.addView(backButton, Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40)));
        TextView titleView = Ui.text(this, title, 16, Ui.TEXT);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setPadding(Ui.dp(this, 10), 0, 0, 0);
        header.addView(titleView, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return header;
    }

    private ListView songList(List<Song> songs) {
        ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(0xFF2A2A2A));
        list.setDividerHeight(1);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setAdapter(new SongAdapter(this, songs, likedIds, songListener, downloadManager));
        return list;
    }

    private void toggleLike(Song song, boolean like) {
        if (account == null) {
            Ui.toast(this, "请先登录");
            return;
        }
        final boolean previous = likedIds.contains(song.id);
        if (like) likedIds.add(song.id); else likedIds.remove(song.id);
        refreshSongViews();
        client.like(song.id, account.userId, like, new NetEaseClient.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean data) {
                setStatus(like ? "已喜欢：" + song.name : "已取消喜欢：" + song.name);
            }

            @Override
            public void onError(String message, int code) {
                if (previous) likedIds.add(song.id); else likedIds.remove(song.id);
                refreshSongViews();
                Ui.toast(MainActivity.this, "操作失败：" + message);
            }
        });
    }

    private void refreshSongViews() {
        if (subTitle != null && subSongs != null && subBack != null) {
            content.removeAllViews();
            content.addView(buildSongsScreen(subTitle, subSongs, subBack));
            currentBack = subBack;
        } else if (tab == 0) {
            showTab(0);
        } else if (tab == 1) {
            showTab(1);
        } else if (tab == 2) {
            showTab(2);
        } else {
            showTab(3);
        }
    }

    // ---------------- helpers ----------------

    private TextView centered(String text) {
        TextView view = Ui.text(this, text, 14, Ui.MUTED);
        view.setGravity(Gravity.CENTER);
        view.setPadding(Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24));
        return view;
    }

    private View messageView(String message, String action, Runnable retry) {
        LinearLayout box = Ui.column(this);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(centered(message));
        Button button = Ui.button(this, action, false);
        button.setOnClickListener(v -> retry.run());
        box.addView(button, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private void playFrom(Song song) {
        if (song == null) return;
        List<Song> queue = null;
        if (subTitle != null && subSongs != null && indexById(subSongs, song.id) >= 0) {
            queue = subSongs;
        } else if (tab == 0) {
            queue = recommendSongs;
        } else if (tab == 1) {
            queue = searchSongs;
        }
        if (queue == null || queue.isEmpty()) return;
        int position = indexById(queue, song.id);
        if (position < 0) return;
        if (playerController == null) playerController = app.playerController();
        playerController.playQueue(queue, position);
        updateMiniPlayer();
        setStatus("播放：" + song.name);
    }

    private static int indexById(List<Song> songs, long id) {
        if (songs == null) return -1;
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).id == id) return i;
        }
        return -1;
    }

    private void updateMiniPlayer() {
        if (miniBar == null) return;
        if (playerController == null) {
            if (miniBar != null) miniBar.setVisibility(View.GONE);
            return;
        }
        Song song = playerController.current();
        if (song == null) {
            miniBar.setVisibility(View.GONE);
            return;
        }
        miniBar.setVisibility(View.VISIBLE);
        miniCover.setVisibility(View.VISIBLE);
        ImageLoader.get(this).load(song.picUrl, miniCover, 100);
        miniTitle.setText(song.name);
        StringBuilder sub = new StringBuilder(song.artist);
        if (playerController.isLoading()) {
            sub.append(" · 加载中");
        } else if (!playerController.error().isEmpty()) {
            sub.append(" · 播放失败");
        } else if (!playerController.quality().isEmpty()) {
            sub.append(" · ").append(playerController.quality());
        }
        if (playerController.isTrial()) sub.append(" · 试听");
        miniSub.setText(sub.toString());
        miniToggle.setText(playerController.isPlaying() ? "⏸" : "▶");
    }

    private void setStatus(String text) {
        if (status == null) return;
        status.setText(text == null ? "" : text);
        boolean show = (tab == 0 || tab == 1) && !subMode && text != null && !text.isEmpty();
        status.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (currentBack != null) {
            Runnable back = currentBack;
            currentBack = null;
            back.run();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SETTINGS) {
            account = null;
            likedIds.clear();
            playlists.clear();
            albums.clear();
            likesLoaded = false;
            playlistsLoaded = false;
            albumsLoaded = false;
            albumsError = "";
            loadAccount();
            loadRecommend();
            showTab(tab);
            return;
        }
        if (requestCode == REQ_LOGIN && resultCode == RESULT_OK) {
            setStatus("登录成功");
            showTab(2);
            loadAccount();
        }
    }
}
