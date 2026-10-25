package com.hazard.music.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Minimal vertical pull-to-refresh container (no AndroidX). */
public final class PullRefreshLayout extends FrameLayout {

    public interface Listener {
        void onRefresh();
    }

    private final LinearLayout indicator;
    private final ProgressBar progress;
    private final TextView label;
    private final int trigger;
    private final int max;
    private final int slop;
    private final int indicatorHeight;

    private View content;
    private Listener listener;
    private float startY;
    private float startX;
    private boolean dragging;
    private boolean refreshing;

    public PullRefreshLayout(Context context) {
        super(context);
        indicatorHeight = Ui.dp(context, 52);
        trigger = Ui.dp(context, 54);
        max = Ui.dp(context, 110);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();

        indicator = new LinearLayout(context);
        indicator.setOrientation(LinearLayout.HORIZONTAL);
        indicator.setGravity(Gravity.CENTER);
        progress = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
        progress.setIndeterminate(true);
        progress.getIndeterminateDrawable().setColorFilter(Ui.ACCENT, PorterDuff.Mode.SRC_IN);
        label = Ui.text(context, "下拉刷新", 12, Ui.MUTED);
        label.setPadding(Ui.dp(context, 8), 0, 0, 0);
        indicator.addView(progress);
        indicator.addView(label);
        addView(indicator, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, indicatorHeight, Gravity.TOP));
    }

    public void setContent(View view) {
        content = view;
        addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (content == null || refreshing) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startY = ev.getY();
                startX = ev.getX();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dy = ev.getY() - startY;
                float dx = ev.getX() - startX;
                if (!content.canScrollVertically(-1)
                        && dy > slop && Math.abs(dy) > Math.abs(dx)) {
                    dragging = true;
                    return true;
                }
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (content == null || refreshing) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return false;
                float dy = ev.getY() - startY;
                float distance = Math.max(0, Math.min(max, dy * 0.5f));
                content.setTranslationY(distance);
                label.setText(distance >= trigger ? "松开刷新" : "下拉刷新");
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) return false;
                dragging = false;
                if (content.getTranslationY() >= trigger) startRefresh();
                else reset();
                return true;
        }
        return false;
    }

    private void startRefresh() {
        refreshing = true;
        label.setText("正在刷新…");
        progress.setVisibility(VISIBLE);
        content.animate().translationY(indicatorHeight).setDuration(150).start();
        if (listener != null) listener.onRefresh();
    }

    private void reset() {
        label.setText("下拉刷新");
        progress.setVisibility(GONE);
        content.animate().translationY(0).setDuration(180).start();
    }

    public void finishRefresh() {
        if (!refreshing) return;
        refreshing = false;
        content.animate().translationY(0).setDuration(180)
                .withEndAction(() -> progress.setVisibility(GONE))
                .start();
    }
}
