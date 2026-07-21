package dev.inkbrief.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class CardRepository {

    public static class PendingAction {
        public final String cardId;
        public final String action;
        public final long createdAt;
        public final int tries;

        public PendingAction(String cardId, String action, long createdAt, int tries) {
            this.cardId = cardId;
            this.action = action;
            this.createdAt = createdAt;
            this.tries = tries;
        }
    }

    private final InkBriefDatabase dbHelper;

    public CardRepository(Context context) {
        dbHelper = new InkBriefDatabase(context);
    }

    public List<Card> getAllCards(String date) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Card> cards = new ArrayList<Card>();
        Cursor cursor = null;
        try {
            if (date != null && date.length() > 0) {
                cursor = db.query(InkBriefDatabase.TABLE_CARDS, null,
                        InkBriefDatabase.COL_DATE + " = ?",
                        new String[]{date}, null, null,
                        InkBriefDatabase.COL_POSITION + " ASC");
                while (cursor.moveToNext()) {
                    cards.add(cursorToCard(cursor));
                }
            }
            // Calendar "today" may lag behind server deck date (or vice versa).
            // Fall back to the newest cached deck so offline open still works.
            if (cards.isEmpty()) {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
                String latest = null;
                Cursor d = null;
                try {
                    d = db.rawQuery(
                            "SELECT " + InkBriefDatabase.COL_DATE
                                    + " FROM " + InkBriefDatabase.TABLE_CARDS
                                    + " WHERE " + InkBriefDatabase.COL_DATE
                                    + " IS NOT NULL AND " + InkBriefDatabase.COL_DATE
                                    + " != '' AND " + InkBriefDatabase.COL_DATE
                                    + " < '2090-01-01'"
                                    + " ORDER BY " + InkBriefDatabase.COL_DATE
                                    + " DESC LIMIT 1",
                            null);
                    if (d.moveToFirst()) {
                        latest = d.getString(0);
                    }
                } finally {
                    if (d != null) {
                        d.close();
                    }
                }
                if (latest != null && (date == null || !latest.equals(date))) {
                    cursor = db.query(InkBriefDatabase.TABLE_CARDS, null,
                            InkBriefDatabase.COL_DATE + " = ?",
                            new String[]{latest}, null, null,
                            InkBriefDatabase.COL_POSITION + " ASC");
                    while (cursor.moveToNext()) {
                        cards.add(cursorToCard(cursor));
                    }
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
        return cards;
    }

    /** Latest non-synthetic deck date in cache, or null. */
    public String getLatestDeckDate() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor d = null;
        try {
            d = db.rawQuery(
                    "SELECT " + InkBriefDatabase.COL_DATE
                            + " FROM " + InkBriefDatabase.TABLE_CARDS
                            + " WHERE " + InkBriefDatabase.COL_DATE
                            + " IS NOT NULL AND " + InkBriefDatabase.COL_DATE
                            + " != '' AND " + InkBriefDatabase.COL_DATE
                            + " < '2090-01-01'"
                            + " ORDER BY " + InkBriefDatabase.COL_DATE
                            + " DESC LIMIT 1",
                    null);
            if (d.moveToFirst()) {
                return d.getString(0);
            }
        } finally {
            if (d != null) {
                d.close();
            }
            db.close();
        }
        return null;
    }

    public Card getCard(String id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Card card = null;
        Cursor cursor = null;
        try {
            cursor = db.query(InkBriefDatabase.TABLE_CARDS, null,
                    InkBriefDatabase.COL_ID + " = ?",
                    new String[]{id}, null, null, null);
            if (cursor.moveToFirst()) {
                card = cursorToCard(cursor);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
        return card;
    }

    public void insertOrUpdate(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return;
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            String deckDate = cards.get(0).getDate();
            if (deckDate == null || deckDate.length() == 0) {
                // Last resort: keep previous latest date if server omitted it.
                Cursor d = null;
                try {
                    d = db.rawQuery(
                            "SELECT " + InkBriefDatabase.COL_DATE
                                    + " FROM " + InkBriefDatabase.TABLE_CARDS
                                    + " WHERE " + InkBriefDatabase.COL_DATE
                                    + " IS NOT NULL AND " + InkBriefDatabase.COL_DATE
                                    + " != '' AND " + InkBriefDatabase.COL_DATE
                                    + " < '2090-01-01'"
                                    + " ORDER BY " + InkBriefDatabase.COL_DATE
                                    + " DESC LIMIT 1",
                            null);
                    if (d.moveToFirst()) {
                        deckDate = d.getString(0);
                    }
                } finally {
                    if (d != null) {
                        d.close();
                    }
                }
                if (deckDate == null) {
                    deckDate = "";
                }
                for (Card c : cards) {
                    c.setDate(deckDate);
                }
            }

            // Preserve local liked/skipped for same deck; server pending list
            // is incomplete (API returns pending-only). Never wipe feedback rows.
            java.util.HashMap<String, String> localFeedback = new java.util.HashMap<String, String>();
            Cursor existing = null;
            try {
                existing = db.query(InkBriefDatabase.TABLE_CARDS,
                        new String[]{InkBriefDatabase.COL_ID, InkBriefDatabase.COL_STATUS},
                        InkBriefDatabase.COL_DATE + " = ?",
                        new String[]{deckDate}, null, null, null);
                while (existing.moveToNext()) {
                    String id = existing.getString(0);
                    String st = existing.getString(1);
                    if ("liked".equals(st) || "skipped".equals(st)) {
                        localFeedback.put(id, st);
                    }
                }
            } finally {
                if (existing != null) {
                    existing.close();
                }
            }

            // Remove only pending/new rows for this deck; keep feedback history.
            db.delete(InkBriefDatabase.TABLE_CARDS,
                    InkBriefDatabase.COL_DATE + " = ? AND ("
                            + InkBriefDatabase.COL_STATUS + " IS NULL OR "
                            + InkBriefDatabase.COL_STATUS + " = '' OR "
                            + InkBriefDatabase.COL_STATUS + " = 'pending' OR "
                            + InkBriefDatabase.COL_STATUS + " = 'new')",
                    new String[]{deckDate});

            for (Card card : cards) {
                String serverStatus = card.getStatus();
                if (serverStatus == null || serverStatus.length() == 0
                        || "new".equals(serverStatus)) {
                    card.setStatus("pending");
                }
                if (card.getDate() == null || card.getDate().length() == 0) {
                    card.setDate(deckDate);
                }
                // Local offline swipe wins over server pending.
                String keep = localFeedback.get(card.getId());
                if (keep != null) {
                    card.setStatus(keep);
                }
                // Skip re-inserting if already liked/skipped locally (row kept).
                if (keep != null) {
                    continue;
                }
                db.insertWithOnConflict(InkBriefDatabase.TABLE_CARDS, null,
                        cardToValues(card), SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public void updateStatus(String cardId, String status) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(InkBriefDatabase.COL_STATUS, status);
        db.update(InkBriefDatabase.TABLE_CARDS, values,
                InkBriefDatabase.COL_ID + " = ?", new String[]{cardId});
        db.close();
    }

    public void enqueueAction(String cardId, String action) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(InkBriefDatabase.COL_ID, cardId);
        values.put(InkBriefDatabase.COL_ACTION, action);
        values.put(InkBriefDatabase.COL_CREATED_AT, System.currentTimeMillis());
        values.put(InkBriefDatabase.COL_TRIES, 0);
        db.insertWithOnConflict(InkBriefDatabase.TABLE_PENDING, null,
                values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    public List<PendingAction> getPendingActions() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<PendingAction> list = new ArrayList<PendingAction>();
        Cursor cursor = null;
        try {
            cursor = db.query(InkBriefDatabase.TABLE_PENDING, null,
                    null, null, null, null,
                    InkBriefDatabase.COL_CREATED_AT + " ASC");
            while (cursor.moveToNext()) {
                list.add(new PendingAction(
                        cursor.getString(cursor.getColumnIndex(InkBriefDatabase.COL_ID)),
                        cursor.getString(cursor.getColumnIndex(InkBriefDatabase.COL_ACTION)),
                        cursor.getLong(cursor.getColumnIndex(InkBriefDatabase.COL_CREATED_AT)),
                        cursor.getInt(cursor.getColumnIndex(InkBriefDatabase.COL_TRIES))
                ));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
        return list;
    }

    public void removePendingAction(String cardId, String action) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(InkBriefDatabase.TABLE_PENDING,
                InkBriefDatabase.COL_ID + " = ? AND " + InkBriefDatabase.COL_ACTION + " = ?",
                new String[]{cardId, action});
        db.close();
    }

    public void bumpPendingTries(String cardId, String action) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.execSQL(
                "UPDATE " + InkBriefDatabase.TABLE_PENDING +
                        " SET " + InkBriefDatabase.COL_TRIES + " = " +
                        InkBriefDatabase.COL_TRIES + " + 1 WHERE " +
                        InkBriefDatabase.COL_ID + " = ? AND " +
                        InkBriefDatabase.COL_ACTION + " = ?",
                new Object[]{cardId, action});
        db.close();
    }

    private Card cursorToCard(Cursor cursor) {
        Card card = new Card();
        card.setId(cursor.getString(cursor.getColumnIndex(InkBriefDatabase.COL_ID)));
        card.setPosition(cursor.getInt(cursor.getColumnIndex(InkBriefDatabase.COL_POSITION)));
        card.setTag(cursor.getString(cursor.getColumnIndex(InkBriefDatabase.COL_TAG)));
        card.setTitle(cursor.getString(cursor.getColumnIndex(InkBriefDatabase.COL_TITLE)));
        card.setSource(cursor.getString(cursor.getColumnIndex(InkBriefDatabase.COL_SOURCE)));
        card.setAiScore(cursor.getDouble(cursor.getColumnIndex(InkBriefDatabase.COL_AI_SCORE)));
        card.setSummary(cursor.getString(cursor.getColumnIndex(InkBriefDatabase.COL_SUMMARY)));
        card.setUrl(cursor.getString(cursor.getColumnIndex(InkBriefDatabase.COL_URL)));
        card.setReason(cursor.getString(cursor.getColumnIndex(InkBriefDatabase.COL_REASON)));
        card.setStatus(cursor.getString(cursor.getColumnIndex(InkBriefDatabase.COL_STATUS)));
        card.setDate(cursor.getString(cursor.getColumnIndex(InkBriefDatabase.COL_DATE)));
        card.setCachedAt(cursor.getLong(cursor.getColumnIndex(InkBriefDatabase.COL_CACHED_AT)));
        return card;
    }

    private ContentValues cardToValues(Card card) {
        ContentValues values = new ContentValues();
        values.put(InkBriefDatabase.COL_ID, card.getId());
        values.put(InkBriefDatabase.COL_POSITION, card.getPosition());
        values.put(InkBriefDatabase.COL_TAG, card.getTag());
        values.put(InkBriefDatabase.COL_TITLE, card.getTitle());
        values.put(InkBriefDatabase.COL_SOURCE, card.getSource());
        values.put(InkBriefDatabase.COL_AI_SCORE, card.getAiScore());
        values.put(InkBriefDatabase.COL_SUMMARY, card.getSummary());
        values.put(InkBriefDatabase.COL_URL, card.getUrl());
        values.put(InkBriefDatabase.COL_REASON, card.getReason());
        values.put(InkBriefDatabase.COL_STATUS, card.getStatus());
        values.put(InkBriefDatabase.COL_DATE, card.getDate());
        values.put(InkBriefDatabase.COL_CACHED_AT, card.getCachedAt());
        return values;
    }
}
