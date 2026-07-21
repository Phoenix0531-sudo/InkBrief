package dev.inkbrief.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.inkbrief.data.Card;

/**
 * Card UI from stock TextViews (no Canvas, no ScrollView).
 * ScrollView eats horizontal swipes on Kindle; keep layout non-scrollable.
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
        setClickable(false);
        setFocusable(false);

        int pad = dp(18);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(Color.WHITE);
        column.setPadding(pad, pad, pad, pad);
        // Do not intercept: let Activity handle swipes.
        column.setClickable(false);
        column.setFocusable(false);

        titleView = new TextView(context);
        titleView.setTextColor(Color.BLACK);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView.setPadding(0, 0, 0, dp(12));
        titleView.setClickable(false);
        column.addView(titleView, lp());

        metaView = new TextView(context);
        metaView.setTextColor(Color.parseColor("#555555"));
        metaView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        metaView.setGravity(Gravity.CENTER_HORIZONTAL);
        metaView.setPadding(0, 0, 0, dp(16));
        metaView.setClickable(false);
        column.addView(metaView, lp());

        reasonView = new TextView(context);
        reasonView.setTextColor(Color.parseColor("#333333"));
        reasonView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        reasonView.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));
        reasonView.setPadding(0, 0, 0, dp(16));
        reasonView.setClickable(false);
        column.addView(reasonView, lp());

        summaryView = new TextView(context);
        summaryView.setTextColor(Color.BLACK);
        summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        summaryView.setPadding(0, 0, 0, dp(24));
        summaryView.setMaxLines(12);
        summaryView.setClickable(false);
        column.addView(summaryView, lp());

        TextView spacer = new TextView(context);
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        column.addView(spacer, spacerLp);

        positionView = new TextView(context);
        positionView.setTextColor(Color.parseColor("#888888"));
        positionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        positionView.setGravity(Gravity.END);
        positionView.setClickable(false);
        column.addView(positionView, lp());

        addView(column, new LayoutParams(
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
