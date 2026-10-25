package com.hazard.music.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hazard.music.download.DownloadManager;
import com.hazard.music.model.DownloadItem;

import java.util.List;

public final class DownloadAdapter extends BaseAdapter {

    public interface Listener {
        void onPlay(DownloadItem item);

        void onAction(DownloadItem item);
    }

    private final Context context;
    private final List<DownloadItem> items;
    private final Listener listener;

    public DownloadAdapter(Context context, List<DownloadItem> items, Listener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    public void setItems(List<DownloadItem> newItems) {
        items.clear();
        items.addAll(newItems);
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public DownloadItem getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).song.id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        if (convertView == null) {
            LinearLayout row = Ui.row(context);
            row.setBackgroundColor(Ui.SURFACE);
            row.setPadding(Ui.dp(context, 12), Ui.dp(context, 8), Ui.dp(context, 6), Ui.dp(context, 8));

            LinearLayout texts = Ui.column(context);
            TextView title = Ui.text(context, "", 15, Ui.TEXT);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            TextView sub = Ui.text(context, "", 11, Ui.MUTED);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title);
            texts.addView(sub);

            Button action = Ui.button(context, "", false);
            action.setTextSize(12);
            action.setMinHeight(Ui.dp(context, 40));
            action.setMinWidth(Ui.dp(context, 64));

            row.addView(texts, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(action, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            convertView = row;
            holder = new Holder(title, sub, action);
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }

        DownloadItem item = getItem(position);
        holder.title.setText(item.song.name);
        StringBuilder sub = new StringBuilder(item.song.artist);
        sub.append(" · ").append(item.statusText());
        if (item.total > 0) {
            sub.append(" · ").append(DownloadManager.formatSize(item.bytes))
                    .append('/').append(DownloadManager.formatSize(item.total));
        }
        holder.sub.setText(sub.toString());

        if (item.isDone()) {
            holder.action.setText("删除");
        } else if (item.state == DownloadItem.DOWNLOADING || item.state == DownloadItem.QUEUED) {
            holder.action.setText("暂停");
        } else {
            holder.action.setText("继续");
        }
        holder.action.setOnClickListener(v -> listener.onAction(item));
        convertView.setOnClickListener(v -> {
            if (item.isDone()) listener.onPlay(item);
        });
        return convertView;
    }

    private static final class Holder {
        final TextView title;
        final TextView sub;
        final Button action;

        Holder(TextView title, TextView sub, Button action) {
            this.title = title;
            this.sub = sub;
            this.action = action;
        }
    }
}
