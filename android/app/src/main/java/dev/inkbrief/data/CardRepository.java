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
            cursor = db.query(InkBriefDatabase.TABLE_CARDS, null,
                    InkBriefDatabase.COL_DATE + " = ?",
                    new String[]{date}, null, null,
                    InkBriefDatabase.COL_POSITION + " ASC");
            while (cursor.moveToNext()) {
                cards.add(cursorToCard(cursor));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
        return cards;
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
            String today = cards.get(0).getDate();
            if (today != null && today.length() > 0) {
                // Preserve local liked/skipped status for same-day cards
                // when re-syncing from server. Server status wins if not "new".
                Cursor existing = null;
                java.util.HashMap<String, String> statusMap = new java.util.HashMap<String, String>();
                try {
                    existing = db.query(InkBriefDatabase.TABLE_CARDS,
                            new String[]{InkBriefDatabase.COL_ID, InkBriefDatabase.COL_STATUS},
                            InkBriefDatabase.COL_DATE + " = ?",
                            new String[]{today}, null, null, null);
                    while (existing.moveToNext()) {
                        String id = existing.getString(0);
                        String st = existing.getString(1);
                        if ("liked".equals(st) || "skipped".equals(st)) {
                            statusMap.put(id, st);
                        }
                    }
                } finally {
                    if (existing != null) {
                        existing.close();
                    }
                }

                db.delete(InkBriefDatabase.TABLE_CARDS,
                        InkBriefDatabase.COL_DATE + " = ?", new String[]{today});
                for (Card card : cards) {
                    String serverStatus = card.getStatus();
                    if (serverStatus == null || serverStatus.length() == 0
                            || "new".equals(serverStatus)) {
                        card.setStatus("pending");
                    }
                    // Local feedback wins if user already swiped offline.
                    String keep = statusMap.get(card.getId());
                    if (keep != null) {
                        card.setStatus(keep);
                    }
                    db.insertWithOnConflict(InkBriefDatabase.TABLE_CARDS, null,
                            cardToValues(card), SQLiteDatabase.CONFLICT_REPLACE);
                }
            } else {
                for (Card card : cards) {
                    if (card.getStatus() == null || card.getStatus().length() == 0
                            || "new".equals(card.getStatus())) {
                        card.setStatus("pending");
                    }
                    db.insertWithOnConflict(InkBriefDatabase.TABLE_CARDS, null,
                            cardToValues(card), SQLiteDatabase.CONFLICT_REPLACE);
                }
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
