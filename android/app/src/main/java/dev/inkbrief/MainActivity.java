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
import java.util.ArrayList;
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
    private TextView doneTitle;
    private TextView statsText;

    private GestureDetector gestureDetector;
    private CardRepository cardRepository;
    private SyncClient syncClient;
    private List<Card> cards;
    private int currentIndex = 0;
    private int state = STATE_CARD;
    private final Handler handler = new Handler();

    // Progress from server / local (full deck, not just pending list)
    private int progressTotal = 0;
    private int progressLiked = 0;
    private int progressSkipped = 0;
    // Server deck date (may differ from device calendar "today")
    private String deckDate = null;

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

                        if (Math.abs(diffY) > Math.abs(diffX)) return false;
                        if (Math.abs(diffX) < 200) return false;

                        if (diffX < 0) {
                            handleAction("like");
                        } else {
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
    protected void onResume() {
        super.onResume();
        // Flush offline queue when app comes back to foreground.
        new Thread(new Runnable() {
            @Override
            public void run() {
                flushPendingActions();
            }
        }).start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    private void buildUI() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        cardView = new CardView(this);
        root.addView(cardView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        confirmationText = new TextView(this);
        confirmationText.setBackgroundColor(Color.WHITE);
        confirmationText.setGravity(Gravity.CENTER);
        confirmationText.setTextSize(28);
        confirmationText.setTextColor(Color.BLACK);
        confirmationText.setVisibility(View.GONE);
        root.addView(confirmationText, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

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

        doneTitle = new TextView(this);
        doneTitle.setText("\u2605 \u4ECA\u65E5\u5DF2\u5B8C\u6210");
        doneTitle.setTextSize(22);
        doneTitle.setGravity(Gravity.CENTER);
        doneTitle.setPadding(0, 0, 0, pad * 2);
        layout.addView(doneTitle);

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
        // Prefer latest cached deck date; calendar "today" is only a hint.
        // Backend may still serve yesterday's deck (Horizon not run yet today).
        String calendarToday = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String cachedDate = cardRepository.getLatestDeckDate();
        deckDate = (cachedDate != null && cachedDate.length() > 0) ? cachedDate : calendarToday;

        List<Card> all = cardRepository.getAllCards(deckDate);
        cards = filterPending(all);
        recomputeLocalProgress(all);

        if (cards != null && !cards.isEmpty()) {
            showCard(0);
        } else if (all != null && !all.isEmpty()) {
            // All cards already liked/skipped locally for this deck.
            showDone();
        } else {
            // No local cache — show loading state, not "done".
            showLoading();
        }

        doSync();
    }

    private static List<Card> filterPending(List<Card> all) {
        List<Card> out = new ArrayList<Card>();
        if (all == null) return out;
        for (Card c : all) {
            if (c != null && c.isPending()) {
                out.add(c);
            }
        }
        return out;
    }

    private void recomputeLocalProgress(List<Card> all) {
        int liked = 0;
        int skipped = 0;
        int total = all != null ? all.size() : 0;
        if (all != null) {
            for (Card c : all) {
                String s = c.getStatus();
                if ("liked".equals(s)) {
                    liked++;
                } else if ("skipped".equals(s)) {
                    skipped++;
                }
            }
        }
        progressTotal = total;
        progressLiked = liked;
        progressSkipped = skipped;
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
        // Show remaining pending count; full deck total if known.
        int remaining = cards.size();
        int shownTotal = progressTotal > 0 ? progressTotal : remaining;
        int shownPos = (progressLiked + progressSkipped) + index + 1;
        if (shownPos > shownTotal) {
            shownPos = index + 1;
            shownTotal = remaining;
        }
        cardView.setPosition(shownPos - 1, shownTotal);
        cardView.setVisibility(View.VISIBLE);
        confirmationText.setVisibility(View.GONE);
        doneLayout.setVisibility(View.GONE);
    }

    private void showLoading() {
        state = STATE_DONE;
        cardView.setVisibility(View.GONE);
        confirmationText.setVisibility(View.GONE);
        doneLayout.setVisibility(View.VISIBLE);
        if (doneTitle != null) {
            doneTitle.setText("\u6B63\u5728\u540C\u6B65\u2026");
        }
        statsText.setText("\u82E5\u957F\u65F6\u95F4\u65E0\u53CD\u5E94\uFF0C\u8BF7\u68C0\u67E5 API \u5730\u5740\u4E0E Token");
    }

    private void showEmpty() {
        state = STATE_DONE;
        cardView.setVisibility(View.GONE);
        confirmationText.setVisibility(View.GONE);
        doneLayout.setVisibility(View.VISIBLE);
        if (doneTitle != null) {
            doneTitle.setText("\u6682\u65E0\u5F85\u5212\u5361\u7247");
        }
        String dateHint = (deckDate != null && deckDate.length() > 0) ? deckDate : "";
        statsText.setText("\u670D\u52A1\u7AEF\u6CA1\u6709\u5F85\u5212\u5185\u5BB9"
                + (dateHint.length() > 0 ? "\n\u5361\u7EC4\u65E5\u671F: " + dateHint : "")
                + "\n\u70B9\u300C\u5237\u65B0\u300D\u91CD\u8BD5");
    }

    private void showDone() {
        state = STATE_DONE;
        cardView.setVisibility(View.GONE);
        confirmationText.setVisibility(View.GONE);
        doneLayout.setVisibility(View.VISIBLE);
        if (doneTitle != null) {
            doneTitle.setText("\u2605 \u4ECA\u65E5\u5DF2\u5B8C\u6210");
        }

        int total = progressTotal;
        int liked = progressLiked;
        int skipped = progressSkipped;
        if (total <= 0 && cards != null) {
            total = cards.size() + liked + skipped;
        }
        String dateHint = (deckDate != null && deckDate.length() > 0)
                ? ("\n\u5361\u7EC4: " + deckDate) : "";
        statsText.setText("\u603B\u8BA1: " + total
                + "   \u559C\u6B22: " + liked
                + "   \u8DF3\u8FC7: " + skipped
                + dateHint);
    }

    private void handleAction(final String action) {
        if (cards == null || currentIndex >= cards.size()) return;
        final Card card = cards.get(currentIndex);

        state = STATE_CONFIRMATION;

        if ("like".equals(action)) {
            confirmationText.setText("\u2713  \u559C\u6B22");
        } else {
            confirmationText.setText("\u2715  \u8DF3\u8FC7");
        }
        confirmationText.setVisibility(View.VISIBLE);
        cardView.setVisibility(View.GONE);

        final String newStatus = "like".equals(action) ? "liked" : "skipped";
        card.setStatus(newStatus);
        cardRepository.updateStatus(card.getId(), newStatus);

        if ("like".equals(action)) {
            progressLiked++;
        } else {
            progressSkipped++;
        }

        // Always enqueue first so offline is never lost; remove on success.
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
                        // Drop opposite action if any (mutex on client too).
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

    private void flushPendingActions() {
        List<CardRepository.PendingAction> pending = cardRepository.getPendingActions();
        for (CardRepository.PendingAction pa : pending) {
            if (pa.tries >= 10) {
                cardRepository.removePendingAction(pa.cardId, pa.action);
                continue;
            }
            try {
                boolean ok;
                if ("like".equals(pa.action)) {
                    ok = syncClient.likeCard(pa.cardId);
                } else {
                    ok = syncClient.skipCard(pa.cardId);
                }
                if (ok) {
                    cardRepository.removePendingAction(pa.cardId, pa.action);
                    String other = "like".equals(pa.action) ? "skip" : "like";
                    cardRepository.removePendingAction(pa.cardId, other);
                } else {
                    cardRepository.bumpPendingTries(pa.cardId, pa.action);
                }
            } catch (Exception e) {
                cardRepository.bumpPendingTries(pa.cardId, pa.action);
                break; // likely offline; stop flushing
            }
        }
    }

    private void doSync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Push offline likes/skips before pulling deck.
                flushPendingActions();

                try {
                    final SyncClient.TodayResult result = syncClient.fetchToday();
                    if (result != null) {
                        // Persist server deck date (not device calendar).
                        if (result.date != null && result.date.length() > 0) {
                            deckDate = result.date;
                            for (Card c : result.cards) {
                                if (c.getDate() == null || c.getDate().length() == 0) {
                                    c.setDate(result.date);
                                }
                            }
                        }
                        cardRepository.insertOrUpdate(result.cards);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // Never clobber an in-progress confirmation frame.
                                if (state == STATE_CONFIRMATION) {
                                    progressTotal = result.total;
                                    progressLiked = result.likedToday;
                                    progressSkipped = result.skippedToday;
                                    return;
                                }

                                String currentId = null;
                                if (state == STATE_CARD
                                        && cards != null
                                        && currentIndex < cards.size()) {
                                    currentId = cards.get(currentIndex).getId();
                                }

                                progressTotal = result.total;
                                progressLiked = result.likedToday;
                                progressSkipped = result.skippedToday;
                                if (result.date != null && result.date.length() > 0) {
                                    deckDate = result.date;
                                }

                                // API already returns pending-only; still filter.
                                cards = filterPending(result.cards);
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
                                    if (result.total > 0
                                            && (result.likedToday + result.skippedToday) >= result.total) {
                                        showDone();
                                    } else if (result.total > 0
                                            && (result.likedToday + result.skippedToday) < result.total) {
                                        // Server says deck exists with remaining cards but
                                        // pending list empty — rare race; show empty+retry.
                                        showEmpty();
                                    } else {
                                        showEmpty();
                                    }
                                } else {
                                    // Always leave loading/done when pending cards exist.
                                    if (currentId == null) {
                                        showCard(0);
                                    } else {
                                        showCard(newIndex);
                                    }
                                }
                                String msg = "\u5DF2\u540C\u6B65 " + cards.size() + " \u6761\u5F85\u5212"
                                        + " / \u603B " + result.total
                                        + (deckDate != null ? " (" + deckDate + ")" : "");
                                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (final Exception e) {
                    final String toast;
                    if (e instanceof SyncClient.SyncException) {
                        SyncClient.SyncException se = (SyncClient.SyncException) e;
                        if (se.code == 401) {
                            toast = "\u8BA4\u8BC1\u5931\u8D25\uFF0C\u8BF7\u68C0\u67E5 Token";
                        } else if (se.code == 0) {
                            toast = "\u7F51\u7EDC\u4E0D\u53EF\u7528\uFF0C\u4F7F\u7528\u672C\u5730\u6570\u636E";
                        } else {
                            toast = "\u540C\u6B65\u5931\u8D25 HTTP " + se.code;
                        }
                    } else {
                        toast = "\u540C\u6B65\u5931\u8D25\uFF0C\u4F7F\u7528\u672C\u5730\u6570\u636E";
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, toast, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
}
