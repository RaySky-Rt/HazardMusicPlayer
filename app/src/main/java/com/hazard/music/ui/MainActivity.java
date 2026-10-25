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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.hazard.music.MusicApp;
import com.hazard.music.R;
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
    private ImageView tabHomeIcon;
    private ImageView tabMineIcon;
    private TextView tabHomeLabel;
    private TextView tabMineLabel;
    private TextView status;
    private LinearLayout topBar;
    private LinearLayout bottomTabs;
    private TextView appTitle;
    private boolean subMode;
    private boolean searchOpen;
    private boolean localMusicOpen;
    private boolean dailyOpen;
    private boolean historyExpanded;
    private PullRefreshLayout homeRefresh;
    private int homeRefreshPending;

    private PlayerController playerController;
    private LinearLayout miniBar;
    private TextView miniTitle;
    private ImageView miniCover;
    private TextView miniSub;
    private ImageView miniToggle;
    private ImageView miniNext;

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
    private final List<Playlist> recommendedPlaylists = new ArrayList<>();
    private final List<Song> historySongs = new ArrayList<>();

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
    private boolean recommendedLoading;
    private boolean recommendedLoaded;
    private String recommendedError = "";

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
        loadRecommended();
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
        appTitle = Ui.text(this, "首页", 19, Ui.TEXT);
        appTitle.setTypeface(Typeface.DEFAULT_BOLD);
        titleBox.addView(appTitle);
        status = Ui.text(this, "", 11, Ui.MUTED);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setVisibility(View.GONE);
        titleBox.addView(status);
        topBar.addView(titleBox, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

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

        miniToggle = Ui.icon(this, R.drawable.ic_play, 20, Ui.TEXT);
        miniToggle.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        miniNext = Ui.icon(this, R.drawable.ic_next, 20, Ui.TEXT);
        miniNext.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
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

        bottomTabs = Ui.row(this);
        bottomTabs.setBackgroundColor(Ui.SURFACE);
        bottomTabs.setPadding(Ui.dp(this, 4), Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 2));
        bottomTabs.addView(navItem(R.drawable.ic_home, "首页", 0), tabParams());
        bottomTabs.addView(navItem(R.drawable.ic_person, "我的", 1), tabParams());
        root.addView(bottomTabs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));
        root.post(() -> {
            Rect visible = new Rect();
            getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
            int navId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            int navHeight = navId > 0 ? getResources().getDimensionPixelSize(navId) : 0;
            int expected = getResources().getDisplayMetrics().heightPixels - visible.top - navHeight;
            int inset = Math.max(0, Math.min(root.getHeight() - expected, navHeight));
            if (inset > 0) root.setPadding(0, 0, 0, inset);
        });
        setContentView(root);
        updateTabs();
        updateChrome();
    }

    private View navItem(int iconRes, String label, int index) {
        LinearLayout item = Ui.column(this);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setBackgroundColor(Color.TRANSPARENT);
        ImageView icon = Ui.icon(this, iconRes, 22, Ui.MUTED);
        TextView text = Ui.text(this, label, 10, Ui.MUTED);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, Ui.dp(this, 2), 0, 0);
        item.addView(icon);
        item.addView(text);
        if (index == 0) { tabHomeIcon = icon; tabHomeLabel = text; }
        else { tabMineIcon = icon; tabMineLabel = text; }
        item.setOnClickListener(v -> showTab(index));
        return item;
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
        tabHomeIcon.setColorFilter(tab == 0 ? Ui.ACCENT : Ui.MUTED);
        tabMineIcon.setColorFilter(tab == 1 ? Ui.ACCENT : Ui.MUTED);
        tabHomeLabel.setTextColor(tab == 0 ? Ui.ACCENT : Ui.MUTED);
        tabMineLabel.setTextColor(tab == 1 ? Ui.ACCENT : Ui.MUTED);
        tabHomeLabel.setTypeface(tab == 0 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        tabMineLabel.setTypeface(tab == 1 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    private void updateChrome() {
        topBar.setVisibility(subMode ? View.GONE : View.VISIBLE);
        bottomTabs.setVisibility(subMode ? View.GONE : View.VISIBLE);
        if (subMode) return;
        String[] titles = {"首页", "我的"};
        appTitle.setText(titles[Math.max(0, Math.min(1, tab))]);
        if (tab == 1) status.setVisibility(View.GONE);
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
        searchOpen = false;
        localMusicOpen = false;
        dailyOpen = false;
        historyExpanded = false;
        homeRefresh = null;
        content.removeAllViews();
        setStatus("");
        updateTabs();
        updateChrome();
        if (index == 0) {
            content.addView(buildHome());
        } else {
            content.addView(buildMine());
        }
    }

    // ---------------- recommend ----------------

    private View buildHome() {
        PullRefreshLayout pull = new PullRefreshLayout(this);
        pull.setListener(() -> refreshHome(true));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        LinearLayout content = Ui.column(this);
        content.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 12));

        LinearLayout search = Ui.row(this);
        search.setBackground(Ui.rounded(Ui.SURFACE_2, 22, this));
        search.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        search.addView(Ui.icon(this, R.drawable.ic_search, 19, Ui.MUTED));
        TextView searchHint = Ui.text(this, "搜索歌曲、歌单", 14, Ui.MUTED);
        searchHint.setPadding(Ui.dp(this, 8), 0, 0, 0);
        search.addView(searchHint, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 8));
        content.addView(search, searchParams);
        search.setOnClickListener(v -> openSearch());

        content.addView(dailyCard());
        content.addView(sectionHeaderRow("推荐歌单", null));
        if (recommendedLoading && recommendedPlaylists.isEmpty()) {
            content.addView(centered("加载中…"));
        } else if (recommendedPlaylists.isEmpty()) {
            content.addView(centered(recommendedError.isEmpty() ? "暂无推荐歌单" : recommendedError));
        } else {
            content.addView(playlistStrip(recommendedPlaylists));
        }

        scroll.addView(content);
        pull.setContent(scroll);
        homeRefresh = pull;
        return pull;
    }

    private View dailyCard() {
        LinearLayout card = Ui.row(this);
        card.setBackground(Ui.rounded(Ui.SURFACE, 12, this));
        card.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(Ui.SURFACE_2);
        int size = Ui.dp(this, 60);
        if (!recommendSongs.isEmpty()) {
            ImageLoader.get(this).load(recommendSongs.get(0).picUrl, cover, 180);
        } else {
            cover.setImageResource(R.drawable.ic_library);
            cover.setColorFilter(Ui.MUTED);
        }
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(size, size);
        coverParams.setMargins(0, 0, Ui.dp(this, 10), 0);
        card.addView(cover, coverParams);

        String first = recommendSongs.isEmpty() ? null : recommendSongs.get(0).name;
        String text;
        if (first != null) {
            text = "每日推荐 | 从 " + first + " 听起";
        } else if (recommendLoading) {
            text = "每日推荐 | 加载中…";
        } else if (!recommendError.isEmpty()) {
            text = "每日推荐 | 加载失败";
        } else {
            text = "每日推荐 | 暂无推荐";
        }
        LinearLayout texts = Ui.column(this);
        TextView title = Ui.text(this, text, 14, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        TextView sub = Ui.text(this, recommendSongs.isEmpty() ? "点击加载每日推荐" : "根据你的口味每日更新", 10, Ui.MUTED);
        sub.setPadding(0, Ui.dp(this, 3), 0, 0);
        texts.addView(sub);
        card.addView(texts, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(Ui.icon(this, R.drawable.ic_chevron, 20, Ui.MUTED));
        card.setOnClickListener(v -> openDaily());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 4));
        card.setLayoutParams(params);
        return card;
    }

    private void openDaily() {
        subMode = true;
        dailyOpen = true;
        searchOpen = false;
        localMusicOpen = false;
        subTitle = "每日推荐";
        subSongs = recommendSongs;
        subBack = () -> showTab(0);
        currentBack = subBack;
        updateChrome();
        renderDaily();
        if (recommendSongs.isEmpty() && !recommendLoading) loadRecommend();
    }

    private void renderDaily() {
        if (!dailyOpen) return;
        content.removeAllViews();
        if (recommendSongs.isEmpty()) {
            String message = recommendLoading ? "加载中…"
                    : (recommendError.isEmpty() ? "暂无每日推荐" : recommendError);
            Runnable retry = recommendLoading ? null : () -> loadRecommend();
            content.addView(buildSubMessage("每日推荐", message, retry, subBack));
        } else {
            content.addView(buildSongsScreen("每日推荐", recommendSongs, subBack));
        }
    }

    private LinearLayout sectionHeaderRow(String title, Runnable action) {
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 4));
        TextView text = Ui.text(this, title, 15, Ui.TEXT);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(text, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (action != null) {
            TextView more = Ui.text(this, "查看全部  ›", 11, Ui.ACCENT);
            more.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), 0, Ui.dp(this, 4));
            more.setOnClickListener(v -> action.run());
            row.addView(more);
        }
        return row;
    }

    private View playlistStrip(List<Playlist> items) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);
        row.setBaselineAligned(false);
        row.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 4));
        int count = 0;
        for (Playlist playlist : items) {
            if (count++ >= 10) break;
            row.addView(playlistCard(playlist));
        }
        scroll.addView(row);
        return scroll;
    }

    private View playlistCard(Playlist playlist) {
        LinearLayout card = Ui.column(this);
        card.setPadding(0, 0, Ui.dp(this, 10), 0);
        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(Ui.SURFACE_2);
        ImageLoader.get(this).load(playlist.coverUrl, cover, 240);
        int size = Ui.dp(this, 112);
        card.addView(cover, new LinearLayout.LayoutParams(size, size));

        TextView title = Ui.text(this, playlist.name, 12, Ui.TEXT);
        title.setMinLines(2);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(title, new LinearLayout.LayoutParams(size, ViewGroup.LayoutParams.WRAP_CONTENT));

        String sub = playlist.trackCount > 0 ? playlist.trackCount + " 首" : "";
        TextView subView = Ui.text(this, sub, 10, Ui.MUTED);
        card.addView(subView);
        card.setOnClickListener(v -> openPlaylist(playlist));
        return card;
    }

    private void refreshHome() {
        refreshHome(false);
    }

    private void refreshHome(boolean fromPull) {
        homeRefreshPending = fromPull ? 2 : 0;
        recommendLoaded = false;
        recommendError = "";
        recommendSongs.clear();
        recommendedLoaded = false;
        recommendedError = "";
        recommendedPlaylists.clear();
        loadRecommend();
        loadRecommended();
    }

    private void homeLoaded() {
        if (homeRefreshPending > 0) {
            homeRefreshPending--;
            if (homeRefreshPending > 0) return;
            if (homeRefresh != null) homeRefresh.finishRefresh();
        }
        if (dailyOpen) {
            subSongs = recommendSongs;
            renderDaily();
        } else if (tab == 0 && currentBack == null) {
            showTab(0);
        }
    }

    private void loadRecommended() {
        if (recommendedLoading) {
            homeLoaded();
            return;
        }
        recommendedLoading = true;
        recommendedError = "";
        client.recommendedPlaylists(new NetEaseClient.Callback<List<Playlist>>() {
            @Override
            public void onSuccess(List<Playlist> data) {
                recommendedLoading = false;
                recommendedLoaded = true;
                recommendedPlaylists.clear();
                if (data != null) recommendedPlaylists.addAll(data);
                saveCache("recommended", Playlist.listToJson(recommendedPlaylists));
                homeLoaded();
            }

            @Override
            public void onError(String message, int code) {
                recommendedLoading = false;
                recommendedLoaded = true;
                recommendedError = "歌单推荐失败：" + message;
                homeLoaded();
            }
        });
    }

    private void loadRecommend() {
        if (recommendLoading) {
            homeLoaded();
            return;
        }
        recommendLoading = true;
        recommendError = "";
        client.dailyRecommend(new NetEaseClient.Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> data) {
                recommendLoading = false;
                recommendLoaded = true;
                recommendSongs.clear();
                if (data != null) recommendSongs.addAll(data);
                saveCache("daily", Song.listToJson(recommendSongs));
                homeLoaded();
                setStatus("每日推荐 " + recommendSongs.size() + " 首");
            }

            @Override
            public void onError(String message, int code) {
                recommendLoading = false;
                recommendLoaded = true;
                recommendError = message + " (" + code + ")";
                homeLoaded();
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

    private void openSearch() {
        subMode = true;
        searchOpen = true;
        localMusicOpen = false;
        subTitle = "搜索";
        subSongs = searchSongs;
        subBack = () -> showTab(0);
        currentBack = subBack;
        updateChrome();
        content.removeAllViews();
        content.addView(buildSearchScreen(subBack));
    }

    private View buildSearchScreen(Runnable back) {
        LinearLayout screen = Ui.column(this);
        screen.addView(subHeader("搜索", back));
        screen.addView(buildSearch(), Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return screen;
    }

    private void renderSearch() {
        if (!searchOpen) return;
        content.removeAllViews();
        content.addView(buildSearchScreen(subBack));
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
        renderSearch();
        client.search(value, 0, new NetEaseClient.Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> data) {
                searchLoading = false;
                searchSongs.clear();
                if (data != null) searchSongs.addAll(data);
                renderSearch();
                setStatus("搜索到 " + searchSongs.size() + " 首");
            }

            @Override
            public void onError(String message, int code) {
                searchLoading = false;
                searchError = message + " (" + code + ")";
                renderSearch();
                setStatus("搜索失败：" + message);
            }
        });
    }

    // ---------------- mine ----------------

    private View buildMine() {
        historySongs.clear();
        historySongs.addAll(app.db().listHistory(100));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout screen = Ui.column(this);
        screen.setPadding(0, 0, 0, Ui.dp(this, 16));

        LinearLayout card = Ui.row(this);
        card.setBackgroundColor(Ui.SURFACE);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16));

        String name = account == null ? "未登录" : account.nickname;
        if (name.isEmpty()) name = "已登录";

        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(Ui.ACCENT);
        avatar.setBackground(avatarBg);
        avatar.setClipToOutline(true);
        avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        if (account != null && !account.avatarUrl.isEmpty()) {
            ImageLoader.get(this).load(account.avatarUrl, avatar, 160);
        } else {
            avatar.setImageResource(R.drawable.ic_person);
            avatar.setColorFilter(Color.WHITE);
        }
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 56), Ui.dp(this, 56));
        avatarParams.setMargins(0, 0, Ui.dp(this, 12), 0);
        card.addView(avatar, avatarParams);

        LinearLayout info = Ui.column(this);
        LinearLayout nameRow = Ui.row(this);
        TextView title = Ui.text(this, name, 17, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        nameRow.addView(title);
        if (account != null && account.vipType > 0) {
            TextView vip = Ui.text(this, "VIP", 9, Color.WHITE);
            vip.setGravity(Gravity.CENTER);
            vip.setBackground(Ui.rounded(Ui.ACCENT, 8, this));
            vip.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2));
            LinearLayout.LayoutParams vipParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            vipParams.setMargins(Ui.dp(this, 6), 0, 0, 0);
            nameRow.addView(vip, vipParams);
        }
        info.addView(nameRow);

        String detail = account == null
                ? (accountLoading ? "正在验证登录…" : "登录后同步收藏与歌单")
                : "UID " + account.userId;
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
        } else {
            ImageView refresh = Ui.icon(this, R.drawable.ic_refresh, 21, Ui.MUTED);
            LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                    Ui.dp(this, 34), Ui.dp(this, 34));
            refresh.setOnClickListener(v -> refreshMine());
            card.addView(refresh, refreshParams);

            ImageView settings = Ui.icon(this, R.drawable.ic_settings, 22, Ui.MUTED);
            LinearLayout.LayoutParams gearParams = new LinearLayout.LayoutParams(
                    Ui.dp(this, 34), Ui.dp(this, 34));
            settings.setOnClickListener(v -> openSettings());
            card.addView(settings, gearParams);
        }
        screen.addView(card);

        if (!mineError.isEmpty()) {
            TextView error = Ui.text(this, mineError, 12, Ui.ACCENT);
            error.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
            screen.addView(error);
        }

        View historyBody = historySongs.isEmpty() ? null : historyStrip();
        TextView historyMore = Ui.text(this, "查看全部  ›", 11, Ui.ACCENT);
        historyMore.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        historyMore.setOnClickListener(v -> openHistory());
        ImageView historyChevron = Ui.icon(this, R.drawable.ic_chevron, 18, Ui.MUTED);
        LinearLayout historyHeader = Ui.row(this);
        historyHeader.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 4));
        TextView historyTitle = Ui.text(this, "播放历史", 15, Ui.TEXT);
        historyTitle.setTypeface(Typeface.DEFAULT_BOLD);
        historyHeader.addView(historyTitle, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView historyCount = Ui.text(this, historySongs.size() + " 首", 11, Ui.MUTED);
        historyCount.setPadding(0, 0, Ui.dp(this, 6), 0);
        historyHeader.addView(historyCount);
        historyHeader.addView(historyChevron);
        historyChevron.setRotation(historyExpanded ? 90 : 0);
        historyHeader.setOnClickListener(v -> {
            if (historySongs.isEmpty()) {
                openHistory();
                return;
            }
            historyExpanded = !historyExpanded;
            if (historyBody != null) historyBody.setVisibility(historyExpanded ? View.VISIBLE : View.GONE);
            historyMore.setVisibility(historyExpanded ? View.VISIBLE : View.GONE);
            historyChevron.animate().rotation(historyExpanded ? 90 : 0).setDuration(150).start();
        });
        screen.addView(historyHeader);
        if (historySongs.isEmpty()) {
            screen.addView(centered("还没有播放记录"));
        } else {
            historyBody.setVisibility(historyExpanded ? View.VISIBLE : View.GONE);
            screen.addView(historyBody);
            historyMore.setVisibility(historyExpanded ? View.VISIBLE : View.GONE);
            screen.addView(historyMore);
        }

        if (account != null) {
            screen.addView(sectionHeaderRow("创建的歌单", this::openCreatedPlaylists));
            List<Playlist> created = createdPlaylists();
            if (created.isEmpty()) {
                screen.addView(centered(playlistsLoaded ? "没有创建的歌单" : "加载中…"));
            } else {
                screen.addView(playlistStrip(created));
            }

            screen.addView(sectionHeaderRow("收藏的歌单", this::showCollections));
            List<Playlist> collected = collectedPlaylists();
            if (collected.isEmpty()) {
                screen.addView(centered(playlistsLoaded ? "暂无收藏歌单" : "加载中…"));
            } else {
                screen.addView(playlistStrip(collected));
            }

            screen.addView(sectionHeaderRow("收藏的专辑", this::showCollections));
            if (albums.isEmpty()) {
                screen.addView(centered(albumsLoaded ? "暂无收藏专辑" : "加载中…"));
            } else {
                screen.addView(albumStrip(albums));
            }
        }

        screen.addView(divider());
        int downloads = downloadManager.snapshot().size();
        screen.addView(navRow(R.drawable.ic_download, "本地音乐",
                downloads > 0 ? downloads + " 首已下载" : "下载与离线播放", this::openLocalMusic));
        screen.addView(divider());
        screen.addView(navRow(R.drawable.ic_settings, "设置", "音质、目录、账号与缓存", this::openSettings));

        scroll.addView(screen);
        return scroll;
    }

    private View historyStrip() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);
        row.setBaselineAligned(false);
        row.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 4));
        int count = 0;
        for (Song song : historySongs) {
            if (count++ >= 8) break;
            row.addView(mediaCard(song.picUrl, song.name, song.artist, () -> playHistory(song)));
        }
        scroll.addView(row);
        return scroll;
    }

    private View albumStrip(List<Album> items) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);
        row.setBaselineAligned(false);
        row.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 4));
        int count = 0;
        for (Album album : items) {
            if (count++ >= 10) break;
            row.addView(mediaCard(album.coverUrl, album.name, album.artist, () -> openAlbum(album, () -> showTab(1))));
        }
        scroll.addView(row);
        return scroll;
    }

    private View mediaCard(String coverUrl, String title, String subtitle, Runnable click) {
        LinearLayout card = Ui.column(this);
        card.setPadding(0, 0, Ui.dp(this, 10), 0);
        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(Ui.SURFACE_2);
        ImageLoader.get(this).load(coverUrl, cover, 240);
        int size = Ui.dp(this, 92);
        card.addView(cover, new LinearLayout.LayoutParams(size, size));
        TextView name = Ui.text(this, title, 11, Ui.TEXT);
        name.setMinLines(2);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0, Ui.dp(this, 5), 0, 0);
        card.addView(name, new LinearLayout.LayoutParams(size, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView sub = Ui.text(this, subtitle, 9, Ui.MUTED);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(sub);
        card.setOnClickListener(v -> click.run());
        return card;
    }

    private View navRow(int iconRes, String title, String subtitle, Runnable click) {
        LinearLayout row = Ui.row(this);
        row.setBackgroundColor(Ui.SURFACE);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        ImageView icon = Ui.icon(this, iconRes, 20, Ui.ACCENT);
        icon.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 24)));
        row.addView(icon);

        LinearLayout texts = Ui.column(this);
        TextView titleView = Ui.text(this, title, 15, Ui.TEXT);
        TextView subView = Ui.text(this, subtitle, 11, Ui.MUTED);
        texts.addView(titleView);
        texts.addView(subView);
        row.addView(texts, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView chevron = Ui.icon(this, R.drawable.ic_chevron, 18, Ui.MUTED);
        row.addView(chevron);
        row.setOnClickListener(v -> click.run());
        return row;
    }

    private void playHistory(Song song) {
        if (historySongs.isEmpty()) return;
        int index = indexById(historySongs, song.id);
        if (index < 0) return;
        if (playerController == null) playerController = app.playerController();
        playerController.playQueue(historySongs, index);
        updateMiniPlayer();
        setStatus("播放：" + song.name);
    }

    private void openCreatedPlaylists() {
        subMode = true;
        dailyOpen = false;
        searchOpen = false;
        localMusicOpen = false;
        subTitle = "创建的歌单";
        subSongs = null;
        subBack = () -> showTab(1);
        currentBack = subBack;
        updateChrome();
        content.removeAllViews();
        content.addView(buildPlaylistListScreen("创建的歌单", createdPlaylists(), subBack));
    }

    private View buildPlaylistListScreen(String title, List<Playlist> items, Runnable back) {
        LinearLayout screen = Ui.column(this);
        screen.addView(subHeader(title, back));
        if (items.isEmpty()) {
            screen.addView(centered("没有创建的歌单"),
                    Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return screen;
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = Ui.column(this);
        for (Playlist playlist : items) {
            rows.addView(playlistRow(playlist, () -> openPlaylist(playlist, this::openCreatedPlaylists)));
            rows.addView(divider());
        }
        scroll.addView(rows);
        screen.addView(scroll, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return screen;
    }

    private void openHistory() {
        historySongs.clear();
        historySongs.addAll(app.db().listHistory(100));
        subMode = true;
        searchOpen = false;
        localMusicOpen = false;
        subTitle = "播放历史";
        subSongs = historySongs;
        subBack = () -> showTab(1);
        currentBack = subBack;
        updateChrome();
        content.removeAllViews();
        if (historySongs.isEmpty()) {
            content.addView(buildSubMessage("播放历史", "还没有播放记录", null, subBack));
        } else {
            content.addView(buildSongsScreen("播放历史", historySongs, subBack));
        }
    }

    private void openSettings() {
        startActivityForResult(new Intent(this, SettingsActivity.class), REQ_SETTINGS);
    }

    // ---------------- downloads ----------------

    private void openLocalMusic() {
        subMode = true;
        localMusicOpen = true;
        searchOpen = false;
        subTitle = "本地音乐";
        subSongs = null;
        subBack = () -> showTab(1);
        currentBack = subBack;
        updateChrome();
        content.removeAllViews();
        content.addView(buildLocalMusic(subBack));
    }

    private View buildLocalMusic(Runnable back) {
        LinearLayout screen = Ui.column(this);
        screen.addView(subHeader("本地音乐", back));
        screen.addView(buildDownloads(), Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return screen;
    }

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
        if (!localMusicOpen) return;
        List<DownloadItem> items = downloadManager.snapshot();
        if (downloadAdapter == null) {
            if (!items.isEmpty() && localMusicOpen) openLocalMusic();
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
            final List<Playlist> cachedRecommended = Playlist.listFromJson(db.getCache("recommended"));
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
                if (recommendedPlaylists.isEmpty() && !cachedRecommended.isEmpty()) {
                    recommendedPlaylists.addAll(cachedRecommended);
                    recommendedLoaded = true;
                }
                if (recommendSongs.isEmpty() && !cachedDaily.isEmpty()) {
                    recommendSongs.addAll(cachedDaily);
                    recommendLoaded = true;
                }
                if (likedIds.isEmpty() && !cachedLikes.isEmpty()) {
                    likedIds.addAll(cachedLikes);
                    likesLoaded = true;
                }
                if (currentBack == null && (tab == 0 || tab == 1)) showTab(tab);
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
        saveCache("recommended", "[]");
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
        if (tab != 1) return;
        if (subTitle != null) return;
        if (currentBack != null) showCollections(); else showTab(1);
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
        Runnable back = () -> showTab(1);
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
        ImageView icon = Ui.icon(this, R.drawable.ic_library, 19, Ui.ACCENT);
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
        ImageView chevron = Ui.icon(this, R.drawable.ic_chevron, 18, Ui.MUTED);
        row.addView(chevron, Ui.lp(Ui.dp(this, 18), ViewGroup.LayoutParams.MATCH_PARENT));
        row.setOnClickListener(v -> click.run());
        return row;
    }

    private View albumRow(Album album, Runnable click) {
        LinearLayout row = Ui.row(this);
        row.setBackgroundColor(Ui.SURFACE);
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        ImageView icon = Ui.icon(this, R.drawable.ic_album, 19, Ui.ACCENT);
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
        ImageView chevron = Ui.icon(this, R.drawable.ic_chevron, 18, Ui.MUTED);
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
        ImageView backButton = Ui.icon(this, R.drawable.ic_back, 20, Ui.TEXT);
        backButton.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
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
        if (searchOpen) {
            renderSearch();
        } else if (subTitle != null && subSongs != null && subBack != null) {
            content.removeAllViews();
            content.addView(buildSongsScreen(subTitle, subSongs, subBack));
            currentBack = subBack;
        } else {
            showTab(tab == 0 ? 0 : 1);
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
        miniToggle.setImageResource(playerController.isPlaying()
                ? R.drawable.ic_pause : R.drawable.ic_play);
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
            boolean loggedOut = data != null && data.getBooleanExtra("logout", false);
            if (loggedOut) {
                account = null;
                likedIds.clear();
                playlists.clear();
                albums.clear();
                likesLoaded = false;
                playlistsLoaded = false;
                albumsLoaded = false;
                albumsError = "";
            }
            showTab(tab);
            return;
        }
        if (requestCode == REQ_LOGIN && resultCode == RESULT_OK) {
            setStatus("登录成功");
            showTab(1);
            loadAccount();
        }
    }
}
