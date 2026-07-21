"""Daily deck assembly logic.

Given today's content items and tag weights, assemble a deck of
max_cards items using Thompson Sampling for tag selection.

Supports stable re-assembly: previously liked/skipped cards keep their
slots; only empty/pending slots are refilled from the pending pool.
"""

from __future__ import annotations

from collections import defaultdict

from bandit import ThompsonSamplingBandit
from config import EXPLORATION_RATIO, MAX_CARDS_PER_DAY, MIN_CARDS_PER_DAY


def assemble_daily_deck(
    today_items: list[dict],
    tag_weights: dict[str, tuple[int, int]],
    max_cards: int = MAX_CARDS_PER_DAY,
    min_cards: int = MIN_CARDS_PER_DAY,
    exploration_ratio: float = EXPLORATION_RATIO,
    previous_deck: list[dict] | None = None,
) -> list[dict]:
    """
    Assemble a daily card deck using Thompson Sampling.

    Strategy:
    1. Prefer never-seen (pending) items only for *new* slots.
    2. If previous_deck is provided (same day re-finalize), keep
       liked/skipped cards in their previous order, then fill remaining
       slots from the pending pool (no re-surface of already-swiped ids).
    3. Rank tags by Thompson Sampling score for new slot allocation.
    4. Within each tag, pick highest AI score items first.
    """
    if not today_items:
        return list(previous_deck or [])[:max_cards]

    items_by_id = {item["id"]: item for item in today_items if item.get("id")}

    # --- Stable path: preserve already-feedback cards from previous deck ---
    kept: list[dict] = []
    kept_ids: set[str] = set()
    if previous_deck:
        for prev in previous_deck:
            pid = prev.get("id")
            if not pid:
                continue
            live = items_by_id.get(pid) or prev
            status = (live.get("status") or prev.get("status") or "pending").strip()
            if status in {"liked", "skipped"}:
                merged = dict(live)
                # Prefer deck_tag / tag from previous for position continuity
                if prev.get("deck_tag"):
                    merged["deck_tag"] = prev["deck_tag"]
                if prev.get("tag") and not merged.get("tag"):
                    merged["tag"] = prev["tag"]
                kept.append(merged)
                kept_ids.add(pid)

    candidates = [
        item
        for item in today_items
        if (item.get("status") or "pending") == "pending"
        and item.get("id") not in kept_ids
    ]

    remaining_slots = max(0, max_cards - len(kept))
    if remaining_slots == 0:
        return kept[:max_cards]

    if not candidates:
        return kept[:max_cards]

    if len(kept) + len(candidates) <= min_cards:
        extra = sorted(
            candidates,
            key=lambda x: x.get("ai_score") or 0,
            reverse=True,
        )[:remaining_slots]
        return (kept + extra)[:max_cards]

    new_cards = _assemble_from_pool(
        candidates,
        tag_weights,
        max_cards=remaining_slots,
        exploration_ratio=exploration_ratio,
    )
    return (kept + new_cards)[:max_cards]


def _assemble_from_pool(
    pool: list[dict],
    tag_weights: dict[str, tuple[int, int]],
    max_cards: int,
    exploration_ratio: float,
) -> list[dict]:
    items_by_tag: dict[str, list[dict]] = defaultdict(list)
    for item in pool:
        tag = item.get("tag", "AI 技术与资讯")
        items_by_tag[tag].append(item)

    for tag in items_by_tag:
        items_by_tag[tag].sort(key=lambda x: x.get("ai_score") or 0, reverse=True)

    bandit = ThompsonSamplingBandit(tag_weights)
    ranked_tags = bandit.rank_tags()
    if not ranked_tags:
        return sorted(pool, key=lambda x: x.get("ai_score") or 0, reverse=True)[:max_cards]

    exploration_slots = max(1, int(max_cards * exploration_ratio))
    exploitation_slots = max_cards - exploration_slots

    total_score = max(sum(s for _, s in ranked_tags), 0.01)
    tag_slots: dict[str, int] = {}

    for tag, score in ranked_tags:
        if score <= 0:
            tag_slots[tag] = 0
            continue
        raw = int(exploitation_slots * score / total_score)
        tag_slots[tag] = max(raw, 0)

    tags_with_items = [t for t, _ in ranked_tags if t in items_by_tag and items_by_tag[t]]
    allocated = sum(tag_slots.values())
    remaining = exploitation_slots - allocated
    for tag in tags_with_items:
        if tag_slots.get(tag, 0) == 0 and remaining > 0:
            tag_slots[tag] = 1
            remaining -= 1

    if exploration_slots > 0 and tags_with_items:
        lowest_tags = [
            t for t, _ in reversed(ranked_tags) if t in items_by_tag
        ][:exploration_slots]
        for tag in lowest_tags:
            tag_slots[tag] = tag_slots.get(tag, 0) + 1

    deck: list[dict] = []
    for tag, _ in ranked_tags:
        slot_count = tag_slots.get(tag, 0)
        available = items_by_tag.get(tag, [])
        deck.extend(available[:slot_count])

    if len(deck) < max_cards:
        used_ids = {item["id"] for item in deck}
        remaining_items = sorted(
            [item for item in pool if item["id"] not in used_ids],
            key=lambda x: x.get("ai_score") or 0,
            reverse=True,
        )
        deck.extend(remaining_items[: max_cards - len(deck)])

    return deck[:max_cards]
