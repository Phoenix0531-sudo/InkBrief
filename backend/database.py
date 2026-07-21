"""SQLite database setup and access layer for InkBrief."""

import sqlite3
import os
from datetime import datetime, timedelta
from config import DATABASE_PATH, WEBHOOK_BATCH_TIMEOUT

DB_DIR = os.path.dirname(DATABASE_PATH) or "."
os.makedirs(DB_DIR, exist_ok=True)

_schema_sql = """
CREATE TABLE IF NOT EXISTS content_items (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    url TEXT NOT NULL,
    source_type TEXT NOT NULL,
    source_name TEXT DEFAULT '',
    ai_score REAL,
    summary TEXT DEFAULT '',
    tag TEXT NOT NULL DEFAULT 'AI 技术与资讯',
    status TEXT DEFAULT 'pending',
    matched_keywords TEXT DEFAULT '[]',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS tag_weights (
    tag TEXT PRIMARY KEY,
    likes INTEGER NOT NULL DEFAULT 0,
    skips INTEGER NOT NULL DEFAULT 0,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS feedback (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    content_item_id TEXT NOT NULL,
    tag TEXT NOT NULL,
    action TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (content_item_id) REFERENCES content_items(id)
);

CREATE TABLE IF NOT EXISTS daily_decks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    date TEXT NOT NULL,
    content_item_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    tag TEXT NOT NULL,
    FOREIGN KEY (content_item_id) REFERENCES content_items(id)
);

CREATE TABLE IF NOT EXISTS webhook_batches (
    date TEXT PRIMARY KEY,
    expected_count INTEGER NOT NULL,
    received_count INTEGER DEFAULT 0,
    status TEXT DEFAULT 'receiving',
    started_at TEXT NOT NULL,
    deadline TEXT NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_daily_decks_date_pos ON daily_decks(date, position);
CREATE INDEX IF NOT EXISTS idx_daily_decks_item ON daily_decks(content_item_id);
CREATE INDEX IF NOT EXISTS idx_content_tag ON content_items(tag);
CREATE INDEX IF NOT EXISTS idx_feedback_created ON feedback(created_at);

CREATE INDEX IF NOT EXISTS idx_content_status ON content_items(status);
CREATE INDEX IF NOT EXISTS idx_content_created ON content_items(created_at);
CREATE INDEX IF NOT EXISTS idx_daily_decks_date ON daily_decks(date);
CREATE INDEX IF NOT EXISTS idx_feedback_item ON feedback(content_item_id);
"""


def get_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(DATABASE_PATH, timeout=5)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    conn.execute("PRAGMA busy_timeout=5000")
    return conn


def initialize_database():
    conn = get_connection()
    conn.executescript(_schema_sql)
    # Initialize cold-start tag weights if not exist
    from config import COLD_START_WEIGHTS
    now = datetime.utcnow().isoformat()
    for tag, weights in COLD_START_WEIGHTS.items():
        conn.execute(
            """INSERT OR IGNORE INTO tag_weights (tag, likes, skips, updated_at)
               VALUES (?, ?, ?, ?)""",
            (tag, weights["likes"], weights["skips"], now),
        )
    conn.commit()
    conn.close()


# --- Content Items ---

def upsert_content_item(item: dict) -> None:
    """Insert or update a content item.

    On re-delivery of the same Horizon item, content fields refresh but
    an existing liked/skipped status is preserved so feedback is not wiped.
    """
    conn = get_connection()
    now = datetime.utcnow().isoformat()
    created_at = item.get("created_at", now)
    conn.execute(
        """INSERT INTO content_items
           (id, title, url, source_type, source_name, ai_score, summary,
            tag, status, matched_keywords, created_at, updated_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
           ON CONFLICT(id) DO UPDATE SET
             title = excluded.title,
             url = excluded.url,
             source_type = excluded.source_type,
             source_name = excluded.source_name,
             ai_score = excluded.ai_score,
             summary = excluded.summary,
             tag = excluded.tag,
             matched_keywords = excluded.matched_keywords,
             updated_at = excluded.updated_at,
             status = CASE
               WHEN content_items.status IN ('liked', 'skipped')
               THEN content_items.status
               ELSE excluded.status
             END
        """,
        (
            item["id"],
            item["title"],
            item["url"],
            item.get("source_type", "rss"),
            item.get("source_name", ""),
            item.get("ai_score"),
            item.get("summary", ""),
            item.get("tag", "AI 技术与资讯"),
            item.get("status", "pending"),
            item.get("matched_keywords", "[]"),
            created_at,
            now,
        ),
    )
    conn.commit()
    conn.close()


def get_pending_items(date: str) -> list[dict]:
    """Get all pending items created on a given date."""
    conn = get_connection()
    rows = conn.execute(
        """SELECT * FROM content_items
           WHERE date(created_at) = ? AND status = 'pending'
           ORDER BY ai_score DESC""",
        (date,),
    ).fetchall()
    conn.close()
    return [dict(r) for r in rows]


def get_items_for_date(date: str) -> list[dict]:
    """Get all content items for a date (any status), for deck rebuilds."""
    conn = get_connection()
    rows = conn.execute(
        """SELECT * FROM content_items
           WHERE date(created_at) = ?
           ORDER BY ai_score DESC""",
        (date,),
    ).fetchall()
    conn.close()
    return [dict(r) for r in rows]


def get_item_by_id(item_id: str) -> dict | None:
    conn = get_connection()
    row = conn.execute(
        "SELECT * FROM content_items WHERE id = ?", (item_id,)
    ).fetchone()
    conn.close()
    return dict(row) if row else None


def update_item_status(item_id: str, status: str) -> None:
    conn = get_connection()
    conn.execute(
        "UPDATE content_items SET status = ?, updated_at = ? WHERE id = ?",
        (status, datetime.utcnow().isoformat(), item_id),
    )
    conn.commit()
    conn.close()


# --- Tag Weights ---

def get_all_tag_weights() -> dict[str, tuple[int, int]]:
    conn = get_connection()
    rows = conn.execute("SELECT tag, likes, skips FROM tag_weights").fetchall()
    conn.close()
    return {r["tag"]: (r["likes"], r["skips"]) for r in rows}


def update_tag_weight(tag: str, likes_delta: int = 0, skips_delta: int = 0) -> None:
    """Adjust tag counts; never allow negative likes/skips."""
    conn = get_connection()
    conn.execute(
        """UPDATE tag_weights
           SET likes = MAX(0, likes + ?),
               skips = MAX(0, skips + ?),
               updated_at = ?
           WHERE tag = ?""",
        (likes_delta, skips_delta, datetime.utcnow().isoformat(), tag),
    )
    conn.commit()
    conn.close()


# --- Feedback ---

def feedback_exists(item_id: str, action: str) -> bool:
    conn = get_connection()
    row = conn.execute(
        "SELECT 1 FROM feedback WHERE content_item_id = ? AND action = ? LIMIT 1",
        (item_id, action),
    ).fetchone()
    conn.close()
    return row is not None


def get_latest_feedback_action(item_id: str) -> str | None:
    """Return latest like/skip action for an item, or None."""
    conn = get_connection()
    row = conn.execute(
        """SELECT action FROM feedback
           WHERE content_item_id = ?
           ORDER BY id DESC
           LIMIT 1""",
        (item_id,),
    ).fetchone()
    conn.close()
    return row["action"] if row else None


def record_feedback(item_id: str, tag: str, action: str) -> None:
    """Replace any prior feedback for this item with the new action (single truth)."""
    conn = get_connection()
    conn.execute("DELETE FROM feedback WHERE content_item_id = ?", (item_id,))
    conn.execute(
        "INSERT INTO feedback (content_item_id, tag, action, created_at) VALUES (?, ?, ?, ?)",
        (item_id, tag, action, datetime.utcnow().isoformat()),
    )
    conn.commit()
    conn.close()


def apply_card_action(item_id: str, tag: str, action: str) -> dict:
    """
    Apply like/skip with mutual exclusion and weight rollback.

    - Same action twice → no-op (idempotent)
    - Switch like↔skip → roll back previous weight, apply new
    - First action → record + weight delta

    Returns:
        {changed: bool, previous: str|None, action: str}
    """
    if action not in ("like", "skip"):
        raise ValueError(f"invalid action: {action}")

    item = get_item_by_id(item_id)
    if not item:
        raise KeyError(item_id)

    previous = None
    status = (item.get("status") or "pending").lower()
    if status in ("liked", "skipped"):
        previous = "like" if status == "liked" else "skip"
    else:
        previous = get_latest_feedback_action(item_id)

    if previous == action:
        return {"changed": False, "previous": previous, "action": action}

    # Rollback opposite action's weight contribution
    if previous == "like":
        update_tag_weight(tag, likes_delta=-1)
    elif previous == "skip":
        update_tag_weight(tag, skips_delta=-1)

    new_status = "liked" if action == "like" else "skipped"
    update_item_status(item_id, new_status)
    record_feedback(item_id, tag, action)

    if action == "like":
        update_tag_weight(tag, likes_delta=1)
    else:
        update_tag_weight(tag, skips_delta=1)

    return {"changed": True, "previous": previous, "action": action}


def get_weekly_feedback(week_start: str, week_end: str) -> list[dict]:
    conn = get_connection()
    rows = conn.execute(
        """SELECT tag, action, COUNT(*) as count
           FROM feedback
           WHERE date(created_at) BETWEEN ? AND ?
           GROUP BY tag, action""",
        (week_start, week_end),
    ).fetchall()
    conn.close()
    return [dict(r) for r in rows]


def get_weekly_top_items(tag: str, week_start: str, week_end: str, limit: int = 3) -> list[dict]:
    conn = get_connection()
    rows = conn.execute(
        """SELECT c.title, c.url, c.ai_score
           FROM content_items c
           JOIN feedback f ON c.id = f.content_item_id
           WHERE f.tag = ? AND f.action = 'like'
             AND date(f.created_at) BETWEEN ? AND ?
           ORDER BY c.ai_score DESC
           LIMIT ?""",
        (tag, week_start, week_end, limit),
    ).fetchall()
    conn.close()
    return [dict(r) for r in rows]


# --- Daily Decks ---

def save_daily_deck(date: str, deck: list[dict]) -> None:
    conn = get_connection()
    conn.execute("DELETE FROM daily_decks WHERE date = ?", (date,))
    for i, item in enumerate(deck):
        conn.execute(
            "INSERT INTO daily_decks (date, content_item_id, position, tag) VALUES (?, ?, ?, ?)",
            (date, item["id"], i + 1, item["tag"]),
        )
    conn.commit()
    conn.close()


def get_daily_deck(date: str) -> list[dict]:
    conn = get_connection()
    rows = conn.execute(
        """SELECT c.*, dd.position, dd.tag as deck_tag
           FROM daily_decks dd
           JOIN content_items c ON c.id = dd.content_item_id
           WHERE dd.date = ?
           ORDER BY dd.position""",
        (date,),
    ).fetchall()
    conn.close()
    return [dict(r) for r in rows]


def get_latest_deck_date(before_or_on: str | None = None) -> str | None:
    """Most recent deck date that has cards. Optionally cap by date (YYYY-MM-DD)."""
    conn = get_connection()
    if before_or_on:
        row = conn.execute(
            """SELECT date FROM daily_decks
               WHERE date <= ? AND date < '2090-01-01'
               GROUP BY date
               HAVING COUNT(*) > 0
               ORDER BY date DESC
               LIMIT 1""",
            (before_or_on,),
        ).fetchone()
    else:
        row = conn.execute(
            """SELECT date FROM daily_decks
               WHERE date < '2090-01-01'
               GROUP BY date
               HAVING COUNT(*) > 0
               ORDER BY date DESC
               LIMIT 1"""
        ).fetchone()
    conn.close()
    return row["date"] if row else None


def get_deck_for_display(date: str) -> tuple[str, list[dict]]:
    """Return (actual_date, cards). Falls back to latest available deck if empty."""
    deck = get_daily_deck(date)
    if deck:
        return date, deck
    latest = get_latest_deck_date(before_or_on=date)
    if latest:
        return latest, get_daily_deck(latest)
    return date, []


def get_today_progress(date: str) -> dict:
    conn = get_connection()
    total = conn.execute(
        "SELECT COUNT(*) FROM daily_decks WHERE date = ?", (date,)
    ).fetchone()[0]
    liked = conn.execute(
        """SELECT COUNT(*) FROM daily_decks dd
           JOIN content_items c ON c.id = dd.content_item_id
           WHERE dd.date = ? AND c.status = 'liked'""",
        (date,),
    ).fetchone()[0]
    skipped = conn.execute(
        """SELECT COUNT(*) FROM daily_decks dd
           JOIN content_items c ON c.id = dd.content_item_id
           WHERE dd.date = ? AND c.status = 'skipped'""",
        (date,),
    ).fetchone()[0]
    conn.close()
    return {"total": total, "liked": liked, "skipped": skipped}


# --- Webhook Batches ---

def create_webhook_batch(date: str, expected_count: int) -> None:
    conn = get_connection()
    now = datetime.utcnow()
    deadline = (now + timedelta(seconds=WEBHOOK_BATCH_TIMEOUT)).isoformat()
    conn.execute(
        """INSERT OR REPLACE INTO webhook_batches
           (date, expected_count, received_count, status, started_at, deadline)
           VALUES (?, ?, 0, 'receiving', ?, ?)""",
        (date, expected_count, now.isoformat(), deadline),
    )
    conn.commit()
    conn.close()


def increment_webhook_batch(date: str) -> int:
    conn = get_connection()
    conn.execute(
        "UPDATE webhook_batches SET received_count = received_count + 1 WHERE date = ?",
        (date,),
    )
    conn.commit()
    row = conn.execute(
        "SELECT expected_count, received_count FROM webhook_batches WHERE date = ?",
        (date,),
    ).fetchone()
    conn.close()
    if row and row["received_count"] >= row["expected_count"]:
        return 1  # complete
    return 0  # still receiving


def get_active_webhook_batch() -> dict | None:
    conn = get_connection()
    row = conn.execute(
        "SELECT * FROM webhook_batches WHERE status = 'receiving' ORDER BY started_at DESC LIMIT 1"
    ).fetchone()
    conn.close()
    return dict(row) if row else None
