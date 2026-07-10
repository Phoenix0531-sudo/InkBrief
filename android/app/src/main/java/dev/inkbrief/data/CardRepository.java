package dev.inkbrief.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class CardRepository {

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
                db.delete(InkBriefDatabase.TABLE_CARDS,
                        InkBriefDatabase.COL_DATE + " = ?", new String[]{today});
            }
            for (Card card : cards) {
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
