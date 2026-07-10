"""REST API endpoints for InkBrief."""

from datetime import datetime
from fastapi import APIRouter, HTTPException, Header, Depends
from models import TodayCardsResponse, CardModel, FeedbackResponse, WeeklyReviewResponse, HealthResponse
from database import (
    get_daily_deck,
    get_today_progress,
    get_item_by_id,
    update_item_status,
    record_feedback,
    update_tag_weight,
    get_all_tag_weights,
    get_active_webhook_batch,
)
from bandit import ThompsonSamplingBandit
from webhook_handler import handle_webhook_payload
from weekly import generate_weekly_review
from config import TOKEN

router = APIRouter()


def verify_token(x_inkbrief_token: str = Header(None, alias="X-InkBrief-Token")):
    if x_inkbrief_token != TOKEN:
        raise HTTPException(status_code=401, detail="Invalid token")


def _today_str() -> str:
    return datetime.now().strftime("%Y-%m-%d")


def _get_tag_weights_with_scores() -> dict[str, float]:
    """Get current tag weights as probability scores."""
    tw = get_all_tag_weights()
    bandit = ThompsonSamplingBandit(tw)
    return bandit.get_normalized_weights()


@router.get("/v1/health", response_model=HealthResponse)
def health_check():
    today = _today_str()
    deck = get_daily_deck(today)
    return HealthResponse(cards_today=len(deck))


@router.get("/v1/cards/today", dependencies=[Depends(verify_token)])
def get_today_cards():
    today = _today_str()
    deck = get_daily_deck(today)

    progress = get_today_progress(today)
    tag_weights_with_scores = _get_tag_weights_with_scores()

    cards = []
    for item in deck:
        reason = _generate_reason(item)
        cards.append(CardModel(
            id=item["id"],
            position=item.get("position", 0),
            tag=item.get("deck_tag", item.get("tag", "")),
            title=item.get("title", ""),
            source=f"{item.get('source_name', '')} · {item.get('source_type', '')}",
            ai_score=item.get("ai_score"),
            summary=item.get("summary", ""),
            url=item.get("url", ""),
            reason=reason,
        ))

    return TodayCardsResponse(
        date=today,
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

    update_item_status(item_id, "liked")
    record_feedback(item_id, item["tag"], "like")
    update_tag_weight(item["tag"], likes_delta=1)

    # Recompute weight for response
    tw = get_all_tag_weights()
    bandit = ThompsonSamplingBandit(tw)
    weight = round(bandit.sample(item["tag"]), 2)

    return FeedbackResponse(success=True, tag=item["tag"], new_weight=weight)


@router.post("/v1/cards/{item_id}/skip", dependencies=[Depends(verify_token)])
def skip_card(item_id: str):
    item = get_item_by_id(item_id)
    if not item:
        raise HTTPException(status_code=404, detail="Card not found")

    update_item_status(item_id, "skipped")
    record_feedback(item_id, item["tag"], "skip")
    update_tag_weight(item["tag"], skips_delta=1)

    tw = get_all_tag_weights()
    bandit = ThompsonSamplingBandit(tw)
    weight = round(bandit.sample(item["tag"]), 2)

    return FeedbackResponse(success=True, tag=item["tag"], new_weight=weight)


@router.get("/v1/cards/today/progress", dependencies=[Depends(verify_token)])
def today_progress():
    today = _today_str()
    return get_today_progress(today)


@router.get("/v1/weekly/review", dependencies=[Depends(verify_token)])
def weekly_review():
    review = generate_weekly_review()
    return review


@router.get("/v1/config/tags", dependencies=[Depends(verify_token)])
def config_tags():
    return {
        "tags": _get_tag_weights_with_scores(),
        "cold_start": True,
    }


@router.post("/webhook/horizon")
def horizon_webhook(data: dict):
    """Receive Horizon webhook payloads."""
    # Accept both with and without token for Horizon
    handle_webhook_payload(data)
    return {"status": "accepted"}


def _generate_reason(item: dict) -> str:
    """Generate a personalized reason for why this card appears."""
    tag = item.get("deck_tag", item.get("tag", ""))
    keywords = item.get("matched_keywords", "[]")
    import json
    try:
        kws = json.loads(keywords) if isinstance(keywords, str) else keywords
    except (json.JSONDecodeError, TypeError):
        kws = []

    if tag == "机会雷达":
        return f"匹配到关键词：{'、'.join(kws) if kws else '招聘/机会类内容'}"
    elif tag == "与我相关的技术链":
        if kws:
            return f"与你正在使用的技术栈相关（{'、'.join(kws[:3])}）"
        return "与你关注的技术领域相关"
    else:
        if kws:
            return f"AI 领域动态（关键词：{'、'.join(kws[:3])}）"
        return "AI 技术与资讯领域的热门内容"
