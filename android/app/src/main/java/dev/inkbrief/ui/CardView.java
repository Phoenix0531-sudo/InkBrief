package dev.inkbrief.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import dev.inkbrief.data.Card;

/**
 * Card UI built from stock TextViews.
 * Custom Canvas drawing is blank on some Kindle/KOSP e-ink devices.
 */
public class CardView extends FrameLayout {

    private final TextView titleView;
    private final TextView metaView;
    private final TextView reasonView;
    private final TextView summaryView;
    private final TextView positionView;

    private Card card;
    private int currentPosition;
    private int totalCards;

    public CardView(Context context) {
        super(context);
        setBackgroundColor(Color.WHITE);
        // Receive touch events so Activity can still process flings via dispatch.
        setClickable(false);
        setFocusable(false);

        int pad = dp(18);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(Color.WHITE);
        column.setPadding(pad, pad, pad, pad);

        titleView = new TextView(context);
        titleView.setTextColor(Color.BLACK);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView.setPadding(0, 0, 0, dp(12));
        column.addView(titleView, lp());

        metaView = new TextView(context);
        metaView.setTextColor(Color.parseColor("#555555"));
        metaView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        metaView.setGravity(Gravity.CENTER_HORIZONTAL);
        metaView.setPadding(0, 0, 0, dp(16));
        column.addView(metaView, lp());

        reasonView = new TextView(context);
        reasonView.setTextColor(Color.parseColor("#333333"));
        reasonView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        reasonView.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));
        reasonView.setPadding(0, 0, 0, dp(16));
        column.addView(reasonView, lp());

        summaryView = new TextView(context);
        summaryView.setTextColor(Color.BLACK);
        summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        summaryView.setPadding(0, 0, 0, dp(24));
        column.addView(summaryView, lp());

        // Spacer pushes position to bottom when content is short.
        TextView spacer = new TextView(context);
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        column.addView(spacer, spacerLp);

        positionView = new TextView(context);
        positionView.setTextColor(Color.parseColor("#888888"));
        positionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        positionView.setGravity(Gravity.END);
        column.addView(positionView, lp());

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.WHITE);
        scroll.addView(column, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.MATCH_PARENT));

        addView(scroll, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setCard(Card card) {
        this.card = card;
        bind();
    }

    public void setPosition(int current, int total) {
        this.currentPosition = current;
        this.totalCards = total;
        if (totalCards > 0) {
            positionView.setText((currentPosition + 1) + " / " + totalCards);
        } else {
            positionView.setText("");
        }
    }

    private void bind() {
        if (card == null) {
            titleView.setText("");
            metaView.setText("");
            reasonView.setText("");
            summaryView.setText("");
            return;
        }

        titleView.setText(safe(card.getTitle()));

        StringBuilder meta = new StringBuilder();
        if (card.getSource() != null && card.getSource().length() > 0) {
            meta.append(card.getSource());
        }
        String tag = card.getTag();
        if (tag != null && tag.length() > 0) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(tag.length() > 8 ? tag.substring(0, 8) + ".." : tag);
        }
        if (meta.length() > 0) meta.append(" · ");
        meta.append(String.format("%.1f", card.getAiScore()));
        metaView.setText(meta.toString());

        String reason = card.getReason();
        if (reason != null && reason.length() > 0) {
            reasonView.setVisibility(VISIBLE);
            reasonView.setText(reason);
        } else {
            reasonView.setVisibility(GONE);
        }

        String summary = card.getSummary();
        summaryView.setText(summary != null ? summary : "");

        if (totalCards > 0) {
            positionView.setText((currentPosition + 1) + " / " + totalCards);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private static LinearLayout.LayoutParams lp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
