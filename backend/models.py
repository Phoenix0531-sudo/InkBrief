"""Pydantic models for InkBrief API."""

from pydantic import BaseModel, Field
from typing import Optional


class ContentItemModel(BaseModel):
    id: str
    title: str
    url: str
    source_type: str = "rss"
    source_name: str = ""
    ai_score: Optional[float] = None
    summary: str = ""
    tag: str = "AI 技术与资讯"
    status: str = "pending"
    matched_keywords: str = "[]"
    created_at: str = ""
    updated_at: str = ""


class CardModel(BaseModel):
    id: str
    position: int
    tag: str
    title: str
    source: str
    ai_score: Optional[float] = None
    summary: str
    url: str
    reason: str = ""


class TodayCardsResponse(BaseModel):
    date: str
    cards: list[CardModel]
    total: int
    liked_today: int
    skipped_today: int
    tag_weights: dict[str, float]


class FeedbackResponse(BaseModel):
    success: bool
    tag: str
    new_weight: float


class WeeklyReviewResponse(BaseModel):
    week_start: str
    week_end: str
    total_liked: int
    total_skipped: int
    tags: list[dict]
    suggestions: list[str]


class HealthResponse(BaseModel):
    status: str = "ok"
    version: str = "0.1.0"
    cards_today: int = 0


class WebhookItem(BaseModel):
    date: str
    language: str = "zh"
    important_items: int = 0
    all_items: int = 0
    message_kind: str = ""  # "overview" or "item"
    item_index: int = 0
    item_count: int = 0
    item_title: str = ""
    item_url: str = ""
    item_score: Optional[float] = None
    summary: str = ""
    source_type: str = ""
    source_name: str = ""
    item_id: str = ""
