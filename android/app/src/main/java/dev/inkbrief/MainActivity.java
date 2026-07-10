package dev.inkbrief;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import dev.inkbrief.data.Card;
import dev.inkbrief.data.CardRepository;
import dev.inkbrief.sync.SyncClient;
import dev.inkbrief.ui.CardView;

public class MainActivity extends Activity {

    private static final long CONFIRMATION_DELAY_MS = 1200;
    private static final int STATE_CARD = 0;
    private static final int STATE_CONFIRMATION = 1;
    private static final int STATE_DONE = 2;

    private CardView cardView;
    private TextView confirmationText;
    private LinearLayout doneLayout;
    private TextView statsText;

    private GestureDetector gestureDetector;
    private CardRepository cardRepository;
    private SyncClient syncClient;
    private List<Card> cards;
    private int currentIndex = 0;
    private int state = STATE_CARD;
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cardRepository = new CardRepository(this);
        syncClient = new SyncClient(this);

        buildUI();

        gestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                            float velocityX, float velocityY) {
                        if (e1 == null || e2 == null) return false;
                        if (state != STATE_CARD) return false;

                        float diffX = e2.getX() - e1.getX();
                        float diffY = e2.getY() - e1.getY();

                        // Ignore vertical swipes
                        if (Math.abs(diffY) > Math.abs(diffX)) return false;
                        if (Math.abs(diffX) < 200) return false;

                        if (diffX < 0) {
                            // Swipe left = like
                            handleAction("like");
                        } else {
                            // Swipe right = skip
                            handleAction("skip");
                        }
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        if (state == STATE_CARD && cards != null
                                && currentIndex < cards.size()) {
                            openDetail(cards.get(currentIndex));
                            return true;
                        }
                        return false;
                    }
                });
        gestureDetector.setIsLongpressEnabled(false);

        loadCards();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    private void buildUI() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        // Card view (fills screen)
        cardView = new CardView(this);
        root.addView(cardView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Confirmation overlay (white bg, centered text)
        confirmationText = new TextView(this);
        confirmationText.setBackgroundColor(Color.WHITE);
        confirmationText.setGravity(Gravity.CENTER);
        confirmationText.setTextSize(28);
        confirmationText.setTextColor(Color.BLACK);
        confirmationText.setVisibility(View.GONE);
        root.addView(confirmationText, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Done screen
        doneLayout = buildDoneView();
        doneLayout.setVisibility(View.GONE);
        root.addView(doneLayout, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    private LinearLayout buildDoneView() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.WHITE);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("\u2605 \u4ECA\u65E5\u5DF2\u5B8C\u6210");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, pad * 2);
        layout.addView(title);

        statsText = new TextView(this);
        statsText.setTextSize(16);
        statsText.setGravity(Gravity.CENTER);
        statsText.setPadding(0, 0, 0, pad * 2);
        layout.addView(statsText);

        Button refreshBtn = new Button(this);
        refreshBtn.setText("\u5237\u65B0");
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doSync();
            }
        });
        layout.addView(refreshBtn);

        // Add some spacing
        TextView spacer = new TextView(this);
        spacer.setHeight(pad);
        layout.addView(spacer);

        Button settingsBtn = new Button(this);
        settingsBtn.setText("\u8BBE\u7F6E");
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        layout.addView(settingsBtn);

        return layout;
    }

    private void loadCards() {
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        cards = cardRepository.getAllCards(today);

        if (cards != null && !cards.isEmpty()) {
            showCard(0);
        } else {
            showEmpty();
        }

        doSync();
    }

    private void showCard(int index) {
        if (cards == null || index >= cards.size()) {
            showDone();
            return;
        }
        currentIndex = index;
        state = STATE_CARD;
        Card card = cards.get(index);

        cardView.setCard(card);
        cardView.setPosition(index, cards.size());
        cardView.setVisibility(View.VISIBLE);
        confirmationText.setVisibility(View.GONE);
        doneLayout.setVisibility(View.GONE);
    }

    private void showEmpty() {
        state = STATE_DONE;
        cardView.setVisibility(View.GONE);
        confirmationText.setVisibility(View.GONE);
        doneLayout.setVisibility(View.VISIBLE);
        statsText.setText("\u4ECA\u5929\u6CA1\u6709\u5185\u5BB9\u3002");
    }

    private void showDone() {
        state = STATE_DONE;
        cardView.setVisibility(View.GONE);
        confirmationText.setVisibility(View.GONE);
        doneLayout.setVisibility(View.VISIBLE);

        int liked = 0;
        int skipped = 0;
        int total = cards != null ? cards.size() : 0;
        if (cards != null) {
            for (Card c : cards) {
                String s = c.getStatus();
                if ("liked".equals(s)) {
                    liked++;
                } else if ("skipped".equals(s)) {
                    skipped++;
                }
            }
        }
        statsText.setText("\u603B\u8BA1: " + total
                + "   \u559C\u6B22: " + liked
                + "   \u8DF3\u8FC7: " + skipped);
    }

    private void handleAction(final String action) {
        if (cards == null || currentIndex >= cards.size()) return;
        final Card card = cards.get(currentIndex);

        state = STATE_CONFIRMATION;

        // Show confirmation
        if ("like".equals(action)) {
            confirmationText.setText("\u2713  \u559C\u6B22");
        } else {
            confirmationText.setText("\u2715  \u8DF3\u8FC7");
        }
        confirmationText.setVisibility(View.VISIBLE);
        cardView.setVisibility(View.GONE);

        // Update local status immediately
        final String newStatus = "like".equals(action) ? "liked" : "skipped";
        card.setStatus(newStatus);
        cardRepository.updateStatus(card.getId(), newStatus);

        // Post to server in background
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if ("like".equals(action)) {
                        syncClient.likeCard(card.getId());
                    } else {
                        syncClient.skipCard(card.getId());
                    }
                } catch (Exception e) {
                    // Offline - status already saved locally
                }
            }
        }).start();

        // After delay, show next card
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                showCard(currentIndex + 1);
            }
        }, CONFIRMATION_DELAY_MS);
    }

    private void openDetail(Card card) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("card_json", card.toJson().toString());
        startActivity(intent);
    }

    private void doSync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Card> freshCards = syncClient.fetchTodayCards();
                    if (freshCards != null) {
                        cardRepository.insertOrUpdate(freshCards);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String currentId = null;
                                if (cards != null && currentIndex < cards.size()) {
                                    currentId = cards.get(currentIndex).getId();
                                }
                                cards = freshCards;
                                int newIndex = 0;
                                if (currentId != null) {
                                    for (int i = 0; i < cards.size(); i++) {
                                        if (cards.get(i).getId().equals(currentId)) {
                                            newIndex = i;
                                            break;
                                        }
                                    }
                                }
                                if (cards.isEmpty()) {
                                    showEmpty();
                                } else if (state == STATE_DONE) {
                                    showDone();
                                } else {
                                    showCard(newIndex);
                                }
                                String msg = "\u5DF2\u540C\u6B65 " + cards.size() + " \u6761";
                                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this,
                                    "\u540C\u6B65\u5931\u8D25\uFF0C\u4F7F\u7528\u672C\u5730\u6570\u636E",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
}
