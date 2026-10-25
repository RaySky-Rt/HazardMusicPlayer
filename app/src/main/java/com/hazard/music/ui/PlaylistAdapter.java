package com.hazard.music.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hazard.music.model.Playlist;

import java.util.List;

public final class PlaylistAdapter extends BaseAdapter {

    private final Context context;
    private final List<Playlist> playlists;
    private final Listener listener;

    public interface Listener {
        void onPlaylistClick(Playlist playlist);
    }

    public PlaylistAdapter(Context context, List<Playlist> playlists, Listener listener) {
        this.context = context;
        this.playlists = playlists;
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return playlists.size();
    }

    @Override
    public Playlist getItem(int position) {
        return playlists.get(position);
    }

    @Override
    public long getItemId(int position) {
        return playlists.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        if (convertView == null) {
            LinearLayout row = Ui.row(context);
            row.setBackgroundColor(Ui.SURFACE);
            row.setPadding(Ui.dp(context, 12), Ui.dp(context, 10), Ui.dp(context, 12), Ui.dp(context, 10));
            LinearLayout texts = Ui.column(context);
            TextView title = Ui.text(context, "", 16, Ui.TEXT);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            TextView sub = Ui.text(context, "", 12, Ui.MUTED);
            texts.addView(title);
            texts.addView(sub);
            row.addView(texts, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView chevron = Ui.text(context, "›", 22, Ui.MUTED);
            chevron.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
            row.addView(chevron, Ui.lp(Ui.dp(context, 20), ViewGroup.LayoutParams.MATCH_PARENT));
            convertView = row;
            holder = new Holder(title, sub);
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }
        Playlist playlist = getItem(position);
        holder.title.setText(playlist.name);
        holder.sub.setText(playlist.trackCount + " 首");
        convertView.setOnClickListener(v -> listener.onPlaylistClick(playlist));
        return convertView;
    }

    private static final class Holder {
        final TextView title;
        final TextView sub;

        Holder(TextView title, TextView sub) {
            this.title = title;
            this.sub = sub;
        }
    }
}
