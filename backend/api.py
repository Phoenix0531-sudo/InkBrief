"""REST API endpoints for InkBrief."""

from __future__ import annotations

import json
import re
from datetime import datetime
from urllib.parse import urlparse

from fastapi import APIRouter, Depends, Header, HTTPException

from bandit import ThompsonSamplingBandit
from config import TOKEN
from database import (
    apply_card_action,
    get_all_tag_weights,
    get_deck_for_display,
    get_item_by_id,
    get_today_progress,
)
from models import (
    CardModel,
    FeedbackResponse,
    HealthResponse,
    TodayCardsResponse,
)
from webhook_handler import handle_webhook_payload
from weekly import generate_weekly_review

router = APIRouter()

_GITHUB_PATH_RE = re.compile(
    r"^/(?P<owner>[A-Za-z0-9_.-]+)/(?P<repo>[A-Za-z0-9_.-]+)"
)


def verify_token(x_inkbrief_token: str = Header(None, alias="X-InkBrief-Token")):
    if x_inkbrief_token != TOKEN:
        raise HTTPException(status_code=401, detail="Invalid token")


def _today_str() -> str:
    return datetime.now().strftime("%Y-%m-%d")


def _get_tag_weights_with_scores() -> dict[str, float]:
    """Deterministic display weights (Beta mean, normalized)."""
    tw = get_all_tag_weights()
    bandit = ThompsonSamplingBandit(tw)
    return {k: round(v, 4) for k, v in bandit.get_display_weights().items()}


def _tag_display_weight(tag: str) -> float:
    tw = get_all_tag_weights()
    bandit = ThompsonSamplingBandit(tw)
    return round(bandit.mean(tag), 4)


def _github_repo_from_url(url: str) -> str | None:
    if not url:
        return None
    try:
        parsed = urlparse(url)
    except Exception:
        return None
    host = (parsed.netloc or "").lower()
    if host not in {"github.com", "www.github.com"}:
        return None
    m = _GITHUB_PATH_RE.match(parsed.path or "")
    if not m:
        return None
    owner = m.group("owner")
    repo = m.group("repo").removesuffix(".git")
    if owner.lower() in {"orgs", "users", "settings", "topics", "search"}:
        return None
    return f"{owner}/{repo}"


def _format_source(item: dict) -> str:
    sn = (item.get("source_name") or "").strip()
    st = (item.get("source_type") or "").strip().lower()
    url = item.get("url") or ""

    if st == "hackernews":
        return "HackerNews"

    if st == "github":
        repo = _github_repo_from_url(url)
        if repo:
            return f"GitHub · {repo}"
        if sn and "/" in sn and sn.lower() not in {"github", "unknown", "anon"}:
            return f"GitHub · {sn}"
        if sn and sn.lower() not in {"github", "unknown", "anon"}:
            return f"GitHub · {sn}"
        return "GitHub"

    if st == "ossinsight":
        repo = _github_repo_from_url(url)
        if repo:
            return f"OSS Insight · {repo}"
        if sn and sn.lower() not in {"ossinsight", "unknown", "anon"}:
            return f"OSS Insight · {sn}"
        return "OSS Insight"

    if sn and st and sn.lower() != st.lower():
        return f"{sn} · {st}"
    return sn or st or "unknown"


def _parse_keywords(item: dict) -> list[str]:
    raw = item.get("matched_keywords", "[]")
    try:
        kws = json.loads(raw) if isinstance(raw, str) else raw
    except (json.JSONDecodeError, TypeError):
        return []
    if not isinstance(kws, list):
        return []
    out: list[str] = []
    for k in kws:
        s = str(k).strip()
        if s and s not in out:
            out.append(s)
    return out


def _generate_reason(item: dict) -> str:
    """Personalized one-liner: tag + matched keywords + source."""
    tag = item.get("deck_tag", item.get("tag", "")) or ""
    kws = _parse_keywords(item)
    source = _format_source(item)
    kw_text = "、".join(kws[:3]) if kws else ""

    if tag == "机会雷达":
        if kw_text:
            return f"机会雷达命中：{kw_text}"
        return "机会雷达：招聘 / 编制 / 岗位类信息"

    if tag == "与我相关的技术链":
        if kw_text:
            return f"命中你的技术链关键词（{kw_text}）· {source}"
        return f"与你关注的技术栈相关 · {source}"

    # AI 技术与资讯
    if kw_text:
        return f"AI 动态命中（{kw_text}）· {source}"
    return f"AI 技术与资讯 · {source}"


@router.get("/v1/health", response_model=HealthResponse)
def health_check():
    today = _today_str()
    _, deck = get_deck_for_display(today)
    return HealthResponse(cards_today=len(deck))


@router.get("/v1/cards/today", dependencies=[Depends(verify_token)])
def get_today_cards(include_done: bool = False):
    """Return today's deck.

    By default only pending cards are listed for swiping.
    Progress totals still count the full deck.
    Pass include_done=true to return all cards (debug / rebuild).
    """
    today = _today_str()
    deck_date, deck = get_deck_for_display(today)

    progress = get_today_progress(deck_date)
    tag_weights_with_scores = _get_tag_weights_with_scores()

    cards = []
    pending_position = 0
    for item in deck:
        status = (item.get("status") or "pending").strip() or "pending"
        if not include_done and status != "pending":
            continue
        pending_position += 1
        cards.append(
            CardModel(
                id=item["id"],
                position=pending_position if not include_done else item.get("position", pending_position),
                tag=item.get("deck_tag", item.get("tag", "")),
                title=item.get("title", ""),
                source=_format_source(item),
                ai_score=item.get("ai_score"),
                summary=item.get("summary", ""),
                url=item.get("url", ""),
                reason=_generate_reason(item),
                status=status,
            )
        )

    return TodayCardsResponse(
        date=deck_date,
        cards=cards,
        total=progress["total"],
        liked_today=progress["liked"],
        skipped_today=progress["skipped"],
        tag_weights=tag_weights_with_scores,
    )


@router.post("/v1/cards/{item_id}/like", dependencies=[Depends(verify_token)])
def like_card(item_id: str):
    item = get_item_by_id(item_id)
    if not item:
        raise HTTPException(status_code=404, detail="Card not found")

    try:
        apply_card_action(item_id, item["tag"], "like")
    except KeyError:
        raise HTTPException(status_code=404, detail="Card not found")

    return FeedbackResponse(
        success=True,
        tag=item["tag"],
        new_weight=_tag_display_weight(item["tag"]),
    )


@router.post("/v1/cards/{item_id}/skip", dependencies=[Depends(verify_token)])
def skip_card(item_id: str):
    item = get_item_by_id(item_id)
    if not item:
        raise HTTPException(status_code=404, detail="Card not found")

    try:
        apply_card_action(item_id, item["tag"], "skip")
    except KeyError:
        raise HTTPException(status_code=404, detail="Card not found")

    return FeedbackResponse(
        success=True,
        tag=item["tag"],
        new_weight=_tag_display_weight(item["tag"]),
    )


@router.get("/v1/cards/today/progress", dependencies=[Depends(verify_token)])
def today_progress():
    today = _today_str()
    deck_date, _ = get_deck_for_display(today)
    return get_today_progress(deck_date)


@router.get("/v1/weekly/review", dependencies=[Depends(verify_token)])
def weekly_review():
    return generate_weekly_review()


@router.get("/v1/config/tags", dependencies=[Depends(verify_token)])
def config_tags():
    return {
        "tags": _get_tag_weights_with_scores(),
        "cold_start": True,
    }


@router.post("/webhook/horizon")
def horizon_webhook(data: dict, token: str = Depends(verify_token)):
    """Receive Horizon webhook payloads."""
    handle_webhook_payload(data)
    return {"status": "accepted"}
