package com.hazard.music.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hazard.music.R;
import com.hazard.music.download.DownloadManager;
import com.hazard.music.model.Song;
import com.hazard.music.util.ImageLoader;

import java.util.List;
import java.util.Set;

public final class SongAdapter extends BaseAdapter {

    public interface Listener {
        void onSongClick(Song song);

        void onLike(Song song, boolean like);

        void onDownload(Song song);
    }

    private final Context context;
    private final List<Song> songs;
    private final Set<Long> likedIds;
    private final Listener listener;
    private final DownloadManager downloads;

    public SongAdapter(Context context, List<Song> songs, Set<Long> likedIds,
                       Listener listener, DownloadManager downloads) {
        this.context = context;
        this.songs = songs;
        this.likedIds = likedIds;
        this.listener = listener;
        this.downloads = downloads;
    }

    @Override
    public int getCount() {
        return songs.size();
    }

    @Override
    public Song getItem(int position) {
        return songs.get(position);
    }

    @Override
    public long getItemId(int position) {
        return songs.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        if (convertView == null) {
            LinearLayout row = Ui.row(context);
            row.setBackgroundColor(Ui.SURFACE);
            row.setPadding(Ui.dp(context, 12), Ui.dp(context, 9), Ui.dp(context, 6), Ui.dp(context, 9));

            ImageView cover = new ImageView(context);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackgroundColor(Ui.SURFACE_2);
            LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(
                    Ui.dp(context, 44), Ui.dp(context, 44));
            coverParams.rightMargin = Ui.dp(context, 10);
            row.addView(cover, coverParams);

            LinearLayout texts = Ui.column(context);
            TextView title = Ui.text(context, "", 16, Ui.TEXT);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            TextView sub = Ui.text(context, "", 12, Ui.MUTED);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title);
            texts.addView(sub);

            ImageView heart = new ImageView(context);
            heart.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

            ImageView download = new ImageView(context);
            download.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

            row.addView(texts, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(heart, Ui.lp(Ui.dp(context, 48), ViewGroup.LayoutParams.MATCH_PARENT));
            row.addView(download, Ui.lp(Ui.dp(context, 34), ViewGroup.LayoutParams.MATCH_PARENT));
            convertView = row;
            holder = new Holder(cover, title, sub, heart, download);
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }

        Song song = getItem(position);
        ImageLoader.get(context).loadCover(song.picUrl, holder.cover, 96);
        holder.title.setText(song.name);
        StringBuilder detail = new StringBuilder();
        if (!song.artist.isEmpty()) detail.append(song.artist);
        if (!song.album.isEmpty()) {
            if (detail.length() > 0) detail.append(" · ");
            detail.append(song.album);
        }
        String duration = song.durationText();
        if (!duration.isEmpty()) {
            if (detail.length() > 0) detail.append(" · ");
            detail.append(duration);
        }
        holder.sub.setText(detail);
        boolean liked = likedIds.contains(song.id);
        holder.heart.setImageResource(liked ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        holder.heart.setColorFilter(liked ? Ui.ACCENT : Ui.MUTED);

        holder.heart.setOnClickListener(v -> listener.onLike(song, !likedIds.contains(song.id)));
        boolean downloaded = downloads != null && downloads.isDownloaded(song.id);
        holder.download.setImageResource(R.drawable.ic_download);
        holder.download.setColorFilter(downloaded ? Ui.SUCCESS : Ui.MUTED);
        holder.download.setOnClickListener(v -> listener.onDownload(song));
        convertView.setOnClickListener(v -> listener.onSongClick(song));
        return convertView;
    }

    private static final class Holder {
        final ImageView cover;
        final TextView title;
        final TextView sub;
        final ImageView heart;
        final ImageView download;

        Holder(ImageView cover, TextView title, TextView sub, ImageView heart, ImageView download) {
            this.cover = cover;
            this.title = title;
            this.sub = sub;
            this.heart = heart;
            this.download = download;
        }
    }
}
