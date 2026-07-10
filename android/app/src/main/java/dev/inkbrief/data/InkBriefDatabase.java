package dev.inkbrief.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class InkBriefDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "inkbrief.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_CARDS = "cards";
    public static final String COL_ID = "id";
    public static final String COL_POSITION = "position";
    public static final String COL_TAG = "tag";
    public static final String COL_TITLE = "title";
    public static final String COL_SOURCE = "source";
    public static final String COL_AI_SCORE = "ai_score";
    public static final String COL_SUMMARY = "summary";
    public static final String COL_URL = "url";
    public static final String COL_REASON = "reason";
    public static final String COL_STATUS = "status";
    public static final String COL_DATE = "date";
    public static final String COL_CACHED_AT = "cached_at";

    private static final String CREATE_TABLE_CARDS =
        "CREATE TABLE " + TABLE_CARDS + " (" +
        COL_ID + " TEXT PRIMARY KEY, " +
        COL_POSITION + " INTEGER, " +
        COL_TAG + " TEXT, " +
        COL_TITLE + " TEXT, " +
        COL_SOURCE + " TEXT, " +
        COL_AI_SCORE + " REAL, " +
        COL_SUMMARY + " TEXT, " +
        COL_URL + " TEXT, " +
        COL_REASON + " TEXT, " +
        COL_STATUS + " TEXT DEFAULT 'new', " +
        COL_DATE + " TEXT, " +
        COL_CACHED_AT + " INTEGER" +
        ")";

    public InkBriefDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_CARDS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CARDS);
        onCreate(db);
    }
}
