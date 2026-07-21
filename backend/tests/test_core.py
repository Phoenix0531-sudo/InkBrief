"""Minimal tests for tag engine + webhook source/summary cleaning.

Run from backend/:
  uv run python -m pytest tests/ -q
or:
  uv run python tests/test_core.py
"""

from __future__ import annotations

import os
import sys
import tempfile

# Ensure backend package path
BACKEND = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if BACKEND not in sys.path:
    sys.path.insert(0, BACKEND)

from tag_engine import reload_rules, tag_item  # noqa: E402
from webhook_handler import clean_summary, derive_source  # noqa: E402


def test_clean_summary_strips_horizon_wrapper():
    raw = (
        "Item 3/20\n\n"
        "## [Claude Code 支持 MCP](https://example.com) ⭐️ 8.2/10\n\n"
        "Anthropic 宣布 Claude Code 正式支持 MCP 协议，可连接外部工具。\n\n"
        "rss · Simon Willison · Jul 20, 12:00"
    )
    cleaned = clean_summary(raw)
    assert "Item 3/20" not in cleaned
    assert "Claude Code 支持 MCP" not in cleaned or "Anthropic" in cleaned
    assert cleaned.startswith("Anthropic")
    assert "rss ·" not in cleaned
    assert "⭐" not in cleaned


def test_clean_summary_zh_prefix():
    raw = "第 1/8 条\n\n## [标题](https://x.com)\n\n正文摘要\n\nhackernews · HackerNews"
    cleaned = clean_summary(raw)
    assert cleaned == "正文摘要"


def test_clean_summary_strips_tags_footer():
    raw = (
        "Body only.\n\n"
        "rss · V2EX · Jul 20\n\n"
        "**Tags**: `#ai-ethics`, `#mcp`"
    )
    cleaned = clean_summary(raw)
    assert cleaned == "Body only."
    assert "Tags" not in cleaned


def test_derive_source_from_url():
    st, sn = derive_source(
        url="https://news.ycombinator.com/item?id=1",
        title="Some HN story",
        summary="",
    )
    assert st == "hackernews"
    assert sn == "HackerNews"


def test_derive_source_from_summary_line():
    st, sn = derive_source(
        url="https://example.com/x",
        title="x",
        summary="Body\n\nrss · V2EX · Jul 20, 12:00",
    )
    assert st == "rss"
    assert sn == "V2EX"


def test_tag_follow_source():
    reload_rules()
    tag, kws = tag_item("Random title", "rss", "HackerNews")
    assert tag == "AI 技术与资讯"
    assert kws == []


def test_tag_agent_chain_keywords():
    reload_rules()
    tag, kws = tag_item("New MCP server for Claude Code", "rss", "")
    assert tag == "与我相关的技术链"
    assert any("mcp" in k.lower() or "claude" in k.lower() for k in kws)


def test_tag_keywords_beat_follow_source():
    """MCP keyword must win even if source is Simon Willison (AI follow)."""
    reload_rules()
    tag, kws = tag_item(
        "Claude Code adds MCP support",
        "rss",
        "Simon Willison",
        summary="Claude Code can now connect external tools via MCP.",
    )
    assert tag == "与我相关的技术链"
    assert kws


def test_tag_ai_keywords():
    reload_rules()
    tag, kws = tag_item("OpenAI releases GPT-5.6", "rss", "")
    assert tag == "AI 技术与资讯"
    assert kws


def test_tag_no_broad_python_false_positive():
    """python/git/docker removed from tech-chain keywords."""
    reload_rules()
    tag, kws = tag_item("After 7 years Scarf moved away from Haskell", "rss", "")
    # Should NOT be tech-chain purely due to broad words
    assert tag != "与我相关的技术链" or not kws


def test_tag_github_source_type_fallback():
    reload_rules()
    tag, kws = tag_item("Some repo update", "github", "github")
    # follow_sources maps github name -> tech chain
    assert tag == "与我相关的技术链"


def test_upsert_preserves_liked_status():
    """Re-delivered webhook must not wipe liked/skipped status."""
    import database as db

    fd, path = tempfile.mkstemp(suffix=".db")
    os.close(fd)
    old = db.DATABASE_PATH
    try:
        db.DATABASE_PATH = path
        db.initialize_database()
        db.upsert_content_item(
            {
                "id": "item_test1",
                "title": "T1",
                "url": "https://example.com/1",
                "source_type": "rss",
                "source_name": "V2EX",
                "ai_score": 8.0,
                "summary": "first",
                "tag": "AI 技术与资讯",
                "status": "pending",
                "matched_keywords": "[]",
                "created_at": "2026-07-20T00:00:00",
            }
        )
        db.update_item_status("item_test1", "liked")
        db.upsert_content_item(
            {
                "id": "item_test1",
                "title": "T1 updated",
                "url": "https://example.com/1",
                "source_type": "rss",
                "source_name": "V2EX",
                "ai_score": 9.0,
                "summary": "second",
                "tag": "与我相关的技术链",
                "status": "pending",  # redelivery would force pending without guard
                "matched_keywords": "[]",
                "created_at": "2026-07-20T00:00:00",
            }
        )
        item = db.get_item_by_id("item_test1")
        assert item is not None
        assert item["status"] == "liked"
        assert item["title"] == "T1 updated"
        assert item["summary"] == "second"
        assert item["tag"] == "与我相关的技术链"
    finally:
        db.DATABASE_PATH = old
        try:
            os.remove(path)
        except OSError:
            pass


def test_deck_display_fallback_skips_future_test_dates():
    import database as db

    fd, path = tempfile.mkstemp(suffix=".db")
    os.close(fd)
    old = db.DATABASE_PATH
    try:
        db.DATABASE_PATH = path
        db.initialize_database()
        # real deck
        db.upsert_content_item(
            {
                "id": "item_a",
                "title": "A",
                "url": "https://example.com/a",
                "source_type": "rss",
                "source_name": "V2EX",
                "ai_score": 8.0,
                "summary": "a",
                "tag": "AI 技术与资讯",
                "status": "pending",
                "matched_keywords": "[]",
                "created_at": "2026-07-20T00:00:00",
            }
        )
        db.save_daily_deck("2026-07-20", [{"id": "item_a", "tag": "AI 技术与资讯"}])
        # synthetic test date must be ignored by fallback
        db.upsert_content_item(
            {
                "id": "item_b",
                "title": "B",
                "url": "https://example.com/b",
                "source_type": "rss",
                "source_name": "V2EX",
                "ai_score": 7.0,
                "summary": "b",
                "tag": "AI 技术与资讯",
                "status": "pending",
                "matched_keywords": "[]",
                "created_at": "2099-01-01T00:00:00",
            }
        )
        db.save_daily_deck("2099-01-01", [{"id": "item_b", "tag": "AI 技术与资讯"}])

        date, deck = db.get_deck_for_display("2026-07-21")
        assert date == "2026-07-20"
        assert len(deck) == 1
        assert deck[0]["id"] == "item_a"
    finally:
        db.DATABASE_PATH = old
        try:
            os.remove(path)
        except OSError:
            pass


def test_deck_excludes_liked_and_skipped():
    from deck import assemble_daily_deck

    items = [
        {
            "id": "p1",
            "title": "pending high",
            "tag": "AI 技术与资讯",
            "status": "pending",
            "ai_score": 9.0,
        },
        {
            "id": "l1",
            "title": "liked",
            "tag": "AI 技术与资讯",
            "status": "liked",
            "ai_score": 10.0,
        },
        {
            "id": "s1",
            "title": "skipped",
            "tag": "与我相关的技术链",
            "status": "skipped",
            "ai_score": 10.0,
        },
        {
            "id": "p2",
            "title": "pending tech",
            "tag": "与我相关的技术链",
            "status": "pending",
            "ai_score": 8.0,
        },
    ]
    weights = {
        "AI 技术与资讯": (3, 1),
        "与我相关的技术链": (3, 1),
        "机会雷达": (1, 1),
    }
    deck = assemble_daily_deck(items, weights, max_cards=10, min_cards=1)
    ids = {d["id"] for d in deck}
    assert "p1" in ids
    assert "p2" in ids
    assert "l1" not in ids
    assert "s1" not in ids


def test_display_weights_are_deterministic():
    from bandit import ThompsonSamplingBandit

    tw = {
        "AI 技术与资讯": (3, 1),
        "与我相关的技术链": (3, 1),
        "机会雷达": (1, 1),
    }
    b = ThompsonSamplingBandit(tw)
    a = b.get_display_weights()
    b2 = ThompsonSamplingBandit(tw)
    c = b2.get_display_weights()
    assert a == c
    # mean for (3,1) = 4/6 ≈ 0.6667 before normalize
    assert abs(b.mean("AI 技术与资讯") - 4 / 6) < 1e-9
    # normalized sum ≈ 1
    assert abs(sum(a.values()) - 1.0) < 1e-6


def test_like_skip_mutex_and_rollback():
    import database as db

    fd, path = tempfile.mkstemp(suffix=".db")
    os.close(fd)
    old = db.DATABASE_PATH
    try:
        db.DATABASE_PATH = path
        db.initialize_database()
        db.upsert_content_item(
            {
                "id": "item_x",
                "title": "X",
                "url": "https://example.com/x",
                "source_type": "rss",
                "source_name": "V2EX",
                "ai_score": 8.0,
                "summary": "x",
                "tag": "AI 技术与资讯",
                "status": "pending",
                "matched_keywords": "[]",
                "created_at": "2026-07-20T00:00:00",
            }
        )
        before = db.get_all_tag_weights()["AI 技术与资讯"]
        r1 = db.apply_card_action("item_x", "AI 技术与资讯", "like")
        assert r1["changed"] is True
        mid = db.get_all_tag_weights()["AI 技术与资讯"]
        assert mid[0] == before[0] + 1  # likes +1

        # same action idempotent
        r2 = db.apply_card_action("item_x", "AI 技术与资讯", "like")
        assert r2["changed"] is False
        assert db.get_all_tag_weights()["AI 技术与资讯"] == mid

        # switch to skip: likes -1, skips +1
        r3 = db.apply_card_action("item_x", "AI 技术与资讯", "skip")
        assert r3["changed"] is True
        assert r3["previous"] == "like"
        after = db.get_all_tag_weights()["AI 技术与资讯"]
        assert after[0] == before[0]  # likes rolled back
        assert after[1] == before[1] + 1  # skips +1

        item = db.get_item_by_id("item_x")
        assert item["status"] == "skipped"
        # single feedback row
        conn = db.get_connection()
        n = conn.execute(
            "SELECT COUNT(*) FROM feedback WHERE content_item_id=?",
            ("item_x",),
        ).fetchone()[0]
        conn.close()
        assert n == 1
    finally:
        db.DATABASE_PATH = old
        try:
            os.remove(path)
        except OSError:
            pass


def test_deck_keeps_feedback_on_reassemble():
    from deck import assemble_daily_deck

    prev = [
        {
            "id": "liked1",
            "title": "kept liked",
            "tag": "AI 技术与资讯",
            "status": "liked",
            "ai_score": 9.0,
        },
        {
            "id": "old_pending",
            "title": "was pending",
            "tag": "AI 技术与资讯",
            "status": "pending",
            "ai_score": 8.0,
        },
    ]
    today = [
        {
            "id": "liked1",
            "title": "kept liked",
            "tag": "AI 技术与资讯",
            "status": "liked",
            "ai_score": 9.0,
        },
        {
            "id": "new1",
            "title": "new pending",
            "tag": "与我相关的技术链",
            "status": "pending",
            "ai_score": 8.5,
        },
        {
            "id": "new2",
            "title": "new pending 2",
            "tag": "AI 技术与资讯",
            "status": "pending",
            "ai_score": 7.0,
        },
    ]
    weights = {
        "AI 技术与资讯": (3, 1),
        "与我相关的技术链": (3, 1),
        "机会雷达": (1, 1),
    }
    deck = assemble_daily_deck(
        today, weights, max_cards=10, min_cards=1, previous_deck=prev
    )
    ids = [d["id"] for d in deck]
    assert ids[0] == "liked1"  # kept first
    assert "new1" in ids or "new2" in ids
    assert "old_pending" not in ids  # not in today pool / not feedback


def test_github_source_from_url():
    from api import _format_source

    item = {
        "source_type": "github",
        "source_name": "wertyk",
        "url": "https://github.com/moonshine-app/moonshine",
    }
    assert _format_source(item) == "GitHub · moonshine-app/moonshine"

    hn = {"source_type": "hackernews", "source_name": "alice", "url": "https://x.com"}
    assert _format_source(hn) == "HackerNews"


def test_reason_uses_keywords():
    from api import _generate_reason

    item = {
        "tag": "与我相关的技术链",
        "matched_keywords": '["mcp", "agent"]',
        "source_type": "rss",
        "source_name": "V2EX",
        "url": "https://www.v2ex.com/t/1",
    }
    reason = _generate_reason(item)
    assert "mcp" in reason
    assert "agent" in reason


def main():
    tests = [
        test_clean_summary_strips_horizon_wrapper,
        test_clean_summary_zh_prefix,
        test_clean_summary_strips_tags_footer,
        test_derive_source_from_url,
        test_derive_source_from_summary_line,
        test_tag_follow_source,
        test_tag_agent_chain_keywords,
        test_tag_keywords_beat_follow_source,
        test_tag_ai_keywords,
        test_tag_no_broad_python_false_positive,
        test_tag_github_source_type_fallback,
        test_upsert_preserves_liked_status,
        test_deck_display_fallback_skips_future_test_dates,
        test_deck_excludes_liked_and_skipped,
        test_display_weights_are_deterministic,
        test_like_skip_mutex_and_rollback,
        test_deck_keeps_feedback_on_reassemble,
        test_github_source_from_url,
        test_reason_uses_keywords,
    ]
    failed = 0
    for t in tests:
        try:
            t()
            print(f"[ok] {t.__name__}")
        except AssertionError as e:
            failed += 1
            print(f"[FAIL] {t.__name__}: {e}")
        except Exception as e:
            failed += 1
            print(f"[ERROR] {t.__name__}: {e}")
    print()
    if failed:
        print(f"{failed}/{len(tests)} FAILED")
        sys.exit(1)
    print(f"{len(tests)}/{len(tests)} PASS")
    sys.exit(0)


if __name__ == "__main__":
    main()
