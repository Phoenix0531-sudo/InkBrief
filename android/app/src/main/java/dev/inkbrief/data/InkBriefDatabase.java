package dev.inkbrief.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class InkBriefDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "inkbrief.db";
    private static final int DATABASE_VERSION = 2;

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

    // Offline feedback queue (like/skip that failed to reach server)
    public static final String TABLE_PENDING = "pending_actions";
    public static final String COL_ACTION = "action";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_TRIES = "tries";

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

    private static final String CREATE_TABLE_PENDING =
        "CREATE TABLE " + TABLE_PENDING + " (" +
        COL_ID + " TEXT NOT NULL, " +
        COL_ACTION + " TEXT NOT NULL, " +
        COL_CREATED_AT + " INTEGER NOT NULL, " +
        COL_TRIES + " INTEGER DEFAULT 0, " +
        "PRIMARY KEY (" + COL_ID + ", " + COL_ACTION + ")" +
        ")";

    public InkBriefDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_CARDS);
        db.execSQL(CREATE_TABLE_PENDING);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL(CREATE_TABLE_PENDING);
        }
    }
}
