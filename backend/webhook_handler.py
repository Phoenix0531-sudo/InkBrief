"""Horizon webhook handler for InkBrief.

Receives Horizon's summary_and_items delivery mode.
Handles batch completion, timeout fallback, tag assignment,
source inference, and summary cleaning.
"""

from __future__ import annotations

import hashlib
import json
import re
import threading
from datetime import datetime
from urllib.parse import urlparse

from config import WEBHOOK_BATCH_TIMEOUT
from database import (
    create_webhook_batch,
    get_all_tag_weights,
    get_daily_deck,
    get_items_for_date,
    get_pending_items,
    increment_webhook_batch,
    save_daily_deck,
    upsert_content_item,
)
from deck import assemble_daily_deck
from tag_engine import tag_item

# Track active batch timers
_batch_timers: dict[str, threading.Timer] = {}

# Horizon item summary often looks like:
#   Item 3/20
#
#   ## [Title](url) ⭐️ 8/10
#
#   actual summary text...
#
#   rss · V2EX · Jul 20, 12:00
_ITEM_PREFIX_RE = re.compile(
    r"^(?:Item|第)\s*\d+\s*/\s*\d+\s*(?:条)?\s*",
    re.IGNORECASE,
)
_MD_TITLE_RE = re.compile(
    r"^##\s*\[[^\]]*\]\([^)]*\)[^\n]*\n*",
    re.MULTILINE,
)
_HTML_ANCHOR_RE = re.compile(r'<a\s+id="item-\d+"></a>\s*', re.IGNORECASE)
_SOURCE_LINE_RE = re.compile(
    r"^(rss|hackernews|github|ossinsight|reddit|twitter|telegram)"
    r"(?:\s*[·•|]\s*.+)?\s*$",
    re.IGNORECASE | re.MULTILINE,
)
_MD_LINK_RE = re.compile(r"\[([^\]]+)\]\([^)]+\)")
_STAR_RE = re.compile(r"[⭐★☆]\uFE0F?\s*\d+(?:\.\d+)?\s*/\s*10")


def _cancel_timer(date: str) -> None:
    timer = _batch_timers.pop(date, None)
    if timer:
        timer.cancel()


def _finalize_batch(date: str) -> None:
    """Finalize deck generation for a given date's batch."""
    _cancel_timer(date)
    tag_weights = get_all_tag_weights()
    # Prefer pending-only assembly. get_items_for_date keeps full pool for
    # logging; assemble_daily_deck filters liked/skipped out of *new* slots.
    # previous_deck keeps already-swiped cards stable across re-finalizes.
    items = get_items_for_date(date)
    if not items:
        items = get_pending_items(date)
    previous_deck = get_daily_deck(date)
    deck = []
    if items or previous_deck:
        deck = assemble_daily_deck(
            items,
            tag_weights,
            previous_deck=previous_deck or None,
        )
        save_daily_deck(date, deck)
    pending_n = sum(1 for i in items if (i.get("status") or "pending") == "pending")
    kept_n = sum(
        1
        for i in (previous_deck or [])
        if (i.get("status") or "pending") in {"liked", "skipped"}
    )
    print(
        f"[webhook] Batch finalized for {date}: "
        f"{len(items)} items ({pending_n} pending, kept {kept_n} feedback) "
        f"→ {len(deck)} cards"
    )


def clean_summary(raw: str) -> str:
    """Strip Horizon item wrapper; keep the readable AI summary body."""
    if not raw:
        return ""

    text = raw.replace("\r\n", "\n").strip()
    text = _HTML_ANCHOR_RE.sub("", text)
    text = _ITEM_PREFIX_RE.sub("", text).lstrip("\n")
    text = _MD_TITLE_RE.sub("", text).lstrip("\n")

    cleaned_lines: list[str] = []
    for line in text.split("\n"):
        s = line.strip()
        # Drop source footer lines anywhere (not only trailing)
        if s and _SOURCE_LINE_RE.match(s):
            continue
        # Drop Horizon tag trails like **Tags**: `#ai` ...
        if re.match(r"^\*{0,2}tags?\*{0,2}\s*:", s, re.I):
            continue
        if re.match(r"^#(?:ai|tech|agent|llm|mcp|python)\S*$", s, re.I):
            continue
        cleaned_lines.append(line)

    # Trim trailing empties
    while cleaned_lines and not cleaned_lines[-1].strip():
        cleaned_lines.pop()

    text = "\n".join(cleaned_lines).strip()
    text = _STAR_RE.sub("", text)
    text = re.sub(r"\n{3,}", "\n\n", text).strip()
    return text


def _host(url: str) -> str:
    try:
        return (urlparse(url).hostname or "").lower()
    except Exception:
        return ""


def derive_source(
    url: str,
    title: str,
    summary: str,
    source_type: str = "",
    source_name: str = "",
) -> tuple[str, str]:
    """Infer source_type / source_name from URL, title, and Horizon summary."""
    url_l = (url or "").lower()
    title_l = (title or "").lower()
    summary_l = (summary or "").lower()
    host = _host(url)

    st = (source_type or "").strip().lower()
    sn = (source_name or "").strip()

    # Prefer source line embedded in Horizon summary markdown
    for line in reversed((summary or "").splitlines()):
        line = line.strip()
        m = _SOURCE_LINE_RE.match(line)
        if not m:
            continue
        st = m.group(1).lower()
        parts = re.split(r"\s*[·•|]\s*", line)
        if len(parts) >= 2:
            candidate = _MD_LINK_RE.sub(r"\1", parts[1]).strip()
            # Ignore pure timestamps / unknown placeholders
            if candidate and candidate.lower() not in {"unknown", "anon"}:
                if not re.match(
                    r"^(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\b",
                    candidate,
                    re.I,
                ):
                    if not re.match(r"^\d+月", candidate):
                        sn = candidate
        break

    # URL / host heuristics (authoritative for known hosts)
    if "news.ycombinator.com" in url_l or "ycombinator.com" in host:
        st, sn = "hackernews", "HackerNews"
    elif "github.com" in host or host.endswith("github.io") or "#github" in title_l:
        st = "github"
        if not sn or sn.lower() in {"unknown", "anon"}:
            sn = "github"
    elif "ossinsight" in url_l or "ossinsight" in title_l:
        st = "ossinsight"
        if not sn or sn.lower() in {"unknown", "anon"}:
            sn = "ossinsight"
    elif "v2ex.com" in host:
        st, sn = "rss", "V2EX"
    elif "solidot.org" in host:
        st, sn = "rss", "Solidot"
    elif "simonwillison" in host:
        st, sn = "rss", "Simon Willison"
    elif "jiqizhixin.com" in host or "机器之心" in (summary or ""):
        st, sn = "rss", "机器之心"
    elif "ruanyifeng" in host or "feedburner.com" in host:
        st, sn = "rss", "阮一峰周刊"
    elif "reddit.com" in host:
        st, sn = "reddit", sn or "Reddit"

    # Title/summary hashtags from Horizon
    if not sn:
        if "#hackernews" in title_l or "hacker news" in summary_l:
            st, sn = "hackernews", "HackerNews"
        elif "#github" in title_l:
            st, sn = "github", "github"
        elif "#ossinsight" in title_l:
            st, sn = "ossinsight", "ossinsight"

    # Normalize known source display names
    name_map = {
        "hackernews": "HackerNews",
        "hacker news": "HackerNews",
        "github": "github",
        "ossinsight": "ossinsight",
        "v2ex": "V2EX",
        "solidot": "Solidot",
        "simon willison": "Simon Willison",
        "ruanyifeng": "阮一峰周刊",
        "jiqizhixin": "机器之心",
    }
    if sn:
        sn = name_map.get(sn.lower(), sn)

    # Canonical names for source types that shouldn't use random authors
    if st == "hackernews":
        sn = "HackerNews"
    elif st == "github" and (not sn or sn.lower() in {"unknown", "anon"}):
        sn = "github"
    elif st == "ossinsight" and (not sn or sn.lower() in {"unknown", "anon"}):
        sn = "ossinsight"

    if not st:
        st = "rss"
    if not sn:
        sn = host[4:] if host.startswith("www.") else host

    return st, sn


def handle_overview(data: dict) -> None:
    """Handle Horizon overview message — starts a new batch."""
    date = data.get("date", datetime.now().strftime("%Y-%m-%d"))
    expected_count = data.get("important_items", 0)
    try:
        expected_count = int(expected_count or 0)
    except (TypeError, ValueError):
        expected_count = 0

    _cancel_timer(date)
    create_webhook_batch(date, expected_count)
    print(f"[webhook] Batch started for {date}: expecting {expected_count} items")

    timer = threading.Timer(WEBHOOK_BATCH_TIMEOUT, _finalize_batch, args=[date])
    timer.daemon = True
    _batch_timers[date] = timer
    timer.start()


def handle_item(data: dict) -> None:
    """Handle a single content item from Horizon webhook."""
    date = data.get("date", datetime.now().strftime("%Y-%m-%d"))

    title = data.get("item_title", "") or ""
    url = data.get("item_url", "") or ""
    raw_summary = data.get("summary", "") or ""
    ai_score = data.get("item_score")
    try:
        ai_score = float(ai_score) if ai_score not in (None, "") else None
    except (TypeError, ValueError):
        ai_score = None

    source_type, source_name = derive_source(
        url=url,
        title=title,
        summary=raw_summary,
        source_type=data.get("source_type", "") or "",
        source_name=data.get("source_name", "") or "",
    )
    summary = clean_summary(raw_summary)

    item_id = data.get("item_id", "") or ""
    if not item_id:
        seed = url or f"{title}|{date}"
        item_id = f"item_{hashlib.md5(seed.encode('utf-8')).hexdigest()[:12]}"

    tag, matched_keywords = tag_item(title, source_type, source_name, summary=summary)

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
