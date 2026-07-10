"""Horizon webhook handler for InkBrief.

Receives Horizon's summary_and_items delivery mode.
Handles batch completion, timeout fallback, and tag assignment.
"""

import json
import threading
from datetime import datetime
from database import (
    upsert_content_item,
    create_webhook_batch,
    increment_webhook_batch,
    get_active_webhook_batch,
    save_daily_deck,
    get_pending_items,
)
from deck import assemble_daily_deck
from tag_engine import tag_item
from database import get_all_tag_weights
from config import WEBHOOK_BATCH_TIMEOUT

# Track active batch timers
_batch_timers: dict[str, threading.Timer] = {}


def _cancel_timer(date: str):
    timer = _batch_timers.pop(date, None)
    if timer:
        timer.cancel()


def _finalize_batch(date: str):
    """Finalize deck generation for a given date's batch."""
    _cancel_timer(date)
    tag_weights = get_all_tag_weights()
    items = get_pending_items(date)
    if items:
        deck = assemble_daily_deck(items, tag_weights)
        save_daily_deck(date, deck)
    print(f"[webhook] Batch finalized for {date}: {len(items)} items, {len(deck) if items else 0} cards")


def handle_overview(data: dict) -> None:
    """Handle Horizon overview message — starts a new batch."""
    date = data.get("date", datetime.now().strftime("%Y-%m-%d"))
    expected_count = data.get("important_items", 0)

    # Cancel any existing timer for this date
    _cancel_timer(date)

    # Create batch record
    create_webhook_batch(date, expected_count)
    print(f"[webhook] Batch started for {date}: expecting {expected_count} items")

    # Set timeout timer to force-complete the batch
    timer = threading.Timer(WEBHOOK_BATCH_TIMEOUT, _finalize_batch, args=[date])
    timer.daemon = True
    _batch_timers[date] = timer
    timer.start()


def handle_item(data: dict) -> None:
    """Handle a single content item from Horizon webhook."""
    date = data.get("date", datetime.now().strftime("%Y-%m-%d"))

    title = data.get("item_title", "")
    url = data.get("item_url", "")
    source_type = data.get("source_type", "rss")
    source_name = data.get("source_name", "")
    ai_score = data.get("item_score")
    summary = data.get("summary", "")

    # Generate a stable item ID
    item_id = data.get("item_id", "")
    if not item_id:
        import hashlib
        item_id = f"item_{hashlib.md5(url.encode()).hexdigest()[:12]}"

    # Assign tag using tag engine
    tag, matched_keywords = tag_item(title, source_type, source_name)

    # Save to database
    now = datetime.utcnow().isoformat()
    item = {
        "id": item_id,
        "title": title,
        "url": url,
        "source_type": source_type,
        "source_name": source_name,
        "ai_score": ai_score,
        "summary": summary,
        "tag": tag,
        "status": "pending",
        "matched_keywords": json.dumps(matched_keywords, ensure_ascii=False),
        "created_at": f"{date}T00:00:00",
    }
    upsert_content_item(item)

    # Check if batch is complete
    result = increment_webhook_batch(date)
    if result == 1:
        _finalize_batch(date)


def handle_webhook_payload(data: dict) -> None:
    """Route webhook payload to appropriate handler."""
    kind = data.get("message_kind", "")

    if kind == "overview":
        handle_overview(data)
    elif kind == "item":
        handle_item(data)
    else:
        print(f"[webhook] Unknown message_kind: {kind}")
