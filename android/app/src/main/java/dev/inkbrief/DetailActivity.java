package dev.inkbrief;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import dev.inkbrief.data.Card;
import dev.inkbrief.data.CardRepository;
import dev.inkbrief.sync.SyncClient;

public class DetailActivity extends Activity {

    private Card card;
    private CardRepository cardRepository;
    private SyncClient syncClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cardRepository = new CardRepository(this);
        syncClient = new SyncClient(this);

        String cardJson = getIntent().getStringExtra("card_json");
        if (cardJson != null) {
            try {
                card = Card.fromJson(new JSONObject(cardJson));
            } catch (Exception e) {
                finish();
                return;
            }
        } else {
            finish();
            return;
        }

        buildUI();
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        // Scroll area for card content
        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        // Title
        TextView titleView = new TextView(this);
        titleView.setText(card.getTitle());
        titleView.setTextSize(22);
        titleView.setTextColor(Color.BLACK);
        titleView.setPadding(0, 0, 0, pad);
        content.addView(titleView);

        // Source · Score
        TextView sourceView = new TextView(this);
        String sourceText = card.getSource() != null ? card.getSource() : "";
        sourceText += " \u00B7 " + String.format("%.1f", card.getAiScore());
        sourceView.setText(sourceText);
        sourceView.setTextSize(14);
        sourceView.setTextColor(Color.parseColor("#555555"));
        sourceView.setPadding(0, 0, 0, pad);
        content.addView(sourceView);

        // Divider line
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#CCCCCC"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        divider.setPadding(0, 0, 0, 0);
        content.addView(divider);

        // Spacer
        TextView spacer1 = new TextView(this);
        spacer1.setHeight(pad);
        content.addView(spacer1);

        // Summary
        if (card.getSummary() != null && card.getSummary().length() > 0) {
            TextView summaryLabel = new TextView(this);
            summaryLabel.setText("\u6458\u8981");
            summaryLabel.setTextSize(16);
            summaryLabel.setTextColor(Color.BLACK);
            summaryLabel.setPadding(0, 0, 0, pad / 2);
            content.addView(summaryLabel);

            TextView summaryText = new TextView(this);
            summaryText.setText(card.getSummary());
            summaryText.setTextSize(15);
            summaryText.setTextColor(Color.parseColor("#333333"));
            summaryText.setPadding(0, 0, 0, pad);
            content.addView(summaryText);
        }

        // Reason
        if (card.getReason() != null && card.getReason().length() > 0) {
            TextView reasonLabel = new TextView(this);
            reasonLabel.setText("\u9009\u62E9\u7406\u7531");
            reasonLabel.setTextSize(16);
            reasonLabel.setTextColor(Color.BLACK);
            reasonLabel.setPadding(0, 0, 0, pad / 2);
            content.addView(reasonLabel);

            TextView reasonText = new TextView(this);
            reasonText.setText(card.getReason());
            reasonText.setTextSize(15);
            reasonText.setTextColor(Color.parseColor("#333333"));
            reasonText.setPadding(0, 0, 0, pad);
            content.addView(reasonText);
        }

        // Tag
        if (card.getTag() != null && card.getTag().length() > 0) {
            TextView tagLabel = new TextView(this);
            tagLabel.setText("\u6807\u7B7E");
            tagLabel.setTextSize(16);
            tagLabel.setTextColor(Color.BLACK);
            tagLabel.setPadding(0, 0, 0, pad / 2);
            content.addView(tagLabel);

            TextView tagText = new TextView(this);
            tagText.setText(card.getTag());
            tagText.setTextSize(15);
            tagText.setTextColor(Color.parseColor("#555555"));
            tagText.setPadding(0, 0, 0, pad);
            content.addView(tagText);
        }

        // URL
        if (card.getUrl() != null && card.getUrl().length() > 0) {
            TextView urlLabel = new TextView(this);
            urlLabel.setText("\u94FE\u63A5");
            urlLabel.setTextSize(16);
            urlLabel.setTextColor(Color.BLACK);
            urlLabel.setPadding(0, 0, 0, pad / 2);
            content.addView(urlLabel);

            TextView urlText = new TextView(this);
            urlText.setText(card.getUrl());
            urlText.setTextSize(13);
            urlText.setTextColor(Color.parseColor("#555555"));
            urlText.setPadding(0, 0, 0, pad);
            content.addView(urlText);
        }

        scrollView.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        // Button row (vertical for e-ink, large touch targets)
        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);
        int btnPad = (int) (48 * getResources().getDisplayMetrics().density);
        buttonRow.setPadding(0, pad, 0, pad);

        Button likeBtn = new Button(this);
        likeBtn.setText("\u559C\u6B22");
        likeBtn.setMinWidth(btnPad);
        likeBtn.setMinHeight(btnPad);
        likeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doAction("like");
            }
        });
        buttonRow.addView(likeBtn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button skipBtn = new Button(this);
        skipBtn.setText("\u8DF3\u8FC7");
        skipBtn.setMinWidth(btnPad);
        skipBtn.setMinHeight(btnPad);
        skipBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doAction("skip");
            }
        });
        buttonRow.addView(skipBtn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button backBtn = new Button(this);
        backBtn.setText("\u8FD4\u56DE");
        backBtn.setMinWidth(btnPad);
        backBtn.setMinHeight(btnPad);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        buttonRow.addView(backBtn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        root.addView(buttonRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private void doAction(final String action) {
        final String newStatus = "like".equals(action) ? "liked" : "skipped";
        card.setStatus(newStatus);
        cardRepository.updateStatus(card.getId(), newStatus);

        Toast.makeText(this,
                "like".equals(action) ? "\u5DF2\u559C\u6B22" : "\u5DF2\u8DF3\u8FC7",
                Toast.LENGTH_SHORT).show();

        // Enqueue first so offline is never lost; remove on success.
        cardRepository.enqueueAction(card.getId(), action);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean ok;
                    if ("like".equals(action)) {
                        ok = syncClient.likeCard(card.getId());
                    } else {
                        ok = syncClient.skipCard(card.getId());
                    }
                    if (ok) {
                        cardRepository.removePendingAction(card.getId(), action);
                        String other = "like".equals(action) ? "skip" : "like";
                        cardRepository.removePendingAction(card.getId(), other);
                    } else {
                        cardRepository.bumpPendingTries(card.getId(), action);
                    }
                } catch (Exception e) {
                    cardRepository.bumpPendingTries(card.getId(), action);
                }
            }
        }).start();
    }
}