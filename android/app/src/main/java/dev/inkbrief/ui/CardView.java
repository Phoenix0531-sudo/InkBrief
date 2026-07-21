package dev.inkbrief.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;

import dev.inkbrief.data.Card;

public class CardView extends View {

    private Card card;
    private int currentPosition;
    private int totalCards;

    private final TextPaint titlePaint;
    private final Paint sourcePaint;
    private final TextPaint reasonPaint;
    private final TextPaint summaryPaint;
    private final Paint positionPaint;
    private final int paddingPx;

    public CardView(Context context) {
        super(context);
        setBackgroundColor(Color.WHITE);

        paddingPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 18, getResources().getDisplayMetrics());

        float titleSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 18, getResources().getDisplayMetrics());
        titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.BLACK);
        titlePaint.setTextSize(titleSize);
        titlePaint.setTypeface(Typeface.DEFAULT_BOLD);
        titlePaint.setTextAlign(Paint.Align.CENTER);

        float sourceSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 14, getResources().getDisplayMetrics());
        sourcePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sourcePaint.setColor(Color.parseColor("#555555"));
        sourcePaint.setTextSize(sourceSize);
        sourcePaint.setTextAlign(Paint.Align.CENTER);

        float reasonSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 15, getResources().getDisplayMetrics());
        reasonPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        reasonPaint.setColor(Color.parseColor("#333333"));
        reasonPaint.setTextSize(reasonSize);
        reasonPaint.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));

        float summarySize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 14, getResources().getDisplayMetrics());
        summaryPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        summaryPaint.setColor(Color.BLACK);
        summaryPaint.setTextSize(summarySize);

        float posSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 13, getResources().getDisplayMetrics());
        positionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        positionPaint.setColor(Color.parseColor("#888888"));
        positionPaint.setTextSize(posSize);
    }

    public void setCard(Card card) {
        this.card = card;
        invalidate();
    }

    public void setPosition(int current, int total) {
        this.currentPosition = current;
        this.totalCards = total;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (card == null) {
            return;
        }

        int viewWidth = getWidth();
        int viewHeight = getHeight();
        int contentWidth = viewWidth - 2 * paddingPx;

        int y = paddingPx + 40;

        // Title - centered, bold
        String title = card.getTitle();
        if (title != null && title.length() > 0) {
            StaticLayout titleLayout = new StaticLayout(
                    title, titlePaint, contentWidth,
                    Layout.Alignment.ALIGN_CENTER, 1.0f, 0, false);
            canvas.save();
            canvas.translate(paddingPx, y);
            titleLayout.draw(canvas);
            canvas.restore();
            y += titleLayout.getHeight() + 12;
        }

        // Source · Score · Tag - centered
        String sourceText = "";
        if (card.getSource() != null && card.getSource().length() > 0) {
            sourceText = card.getSource();
        }
        String tag = card.getTag();
        if (tag != null && tag.length() > 0) {
            String shortTag = tag.length() > 6 ? tag.substring(0, 6) + ".." : tag;
            sourceText += " · " + shortTag;
        }
        sourceText += " · " + String.format("%.1f", card.getAiScore());
        float sourceY = y + Math.abs(sourcePaint.ascent());
        canvas.drawText(sourceText, viewWidth / 2.0f, sourceY, sourcePaint);
        y += sourcePaint.getFontSpacing() + 12;

        // Reason paragraph - italic, left-aligned
        String reason = card.getReason();
        if (reason != null && reason.length() > 0) {
            StaticLayout reasonLayout = new StaticLayout(
                    reason, reasonPaint, contentWidth,
                    Layout.Alignment.ALIGN_NORMAL, 1.3f, 0, false);
            canvas.save();
            canvas.translate(paddingPx, y);
            reasonLayout.draw(canvas);
            canvas.restore();
            y += reasonLayout.getHeight() + 16;
        }

        // Summary - normal weight, left-aligned, show as much as fits
        String summary = card.getSummary();
        if (summary != null && summary.length() > 0) {
            int maxLines = Math.max(3, (viewHeight - y - 60) / 40);
            StaticLayout summaryLayout = new StaticLayout(
                    summary, summaryPaint, contentWidth,
                    Layout.Alignment.ALIGN_NORMAL, 1.2f, 0, false);
            int visibleLines = Math.min(summaryLayout.getLineCount(), maxLines);
            int summaryHeight = 0;
            for (int i = 0; i < visibleLines; i++) {
                summaryHeight = summaryLayout.getLineBottom(i);
            }
            canvas.save();
            canvas.clipRect(paddingPx, y, viewWidth - paddingPx, y + summaryHeight);
            canvas.translate(paddingPx, y);
            summaryLayout.draw(canvas);
            canvas.restore();
            y += summaryHeight + 12;
        }

        // Position indicator - bottom-right
        if (totalCards > 0) {
            String posText = (currentPosition + 1) + " / " + totalCards;
            float textWidth = positionPaint.measureText(posText);
            float posX = viewWidth - paddingPx - textWidth;
            float posY = viewHeight - paddingPx;
            canvas.drawText(posText, posX, posY, positionPaint);
        }
    }
}
