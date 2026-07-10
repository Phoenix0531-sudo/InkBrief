"""Daily deck assembly logic.

Given today's content items and tag weights, assemble a deck of
max_cards items using Thompson Sampling for tag selection.
"""

import random
from collections import defaultdict
from bandit import ThompsonSamplingBandit
from config import MAX_CARDS_PER_DAY, MIN_CARDS_PER_DAY, EXPLORATION_RATIO


def assemble_daily_deck(
    today_items: list[dict],
    tag_weights: dict[str, tuple[int, int]],
    max_cards: int = MAX_CARDS_PER_DAY,
    min_cards: int = MIN_CARDS_PER_DAY,
    exploration_ratio: float = EXPLORATION_RATIO,
) -> list[dict]:
    """
    Assemble a daily card deck using Thompson Sampling.

    Strategy:
    1. Rank tags by Thompson Sampling score
    2. Allocate card slots proportionally to tag weights
    3. Reserve exploration_ratio slots for lower-weighted tags
    4. Fill remaining slots from highest-weight tags
    5. Within each tag, pick highest AI score items first

    Returns:
        List of content items, ordered by tag weight (highest first),
        then by AI score within each tag.
    """
    if not today_items:
        return []

    if len(today_items) <= min_cards:
        # Not enough items — return all sorted by AI score
        return sorted(today_items, key=lambda x: x.get("ai_score") or 0, reverse=True)

    # Group items by tag
    items_by_tag: dict[str, list[dict]] = defaultdict(list)
    for item in today_items:
        tag = item.get("tag", "AI 技术与资讯")
        items_by_tag[tag].append(item)

    # Sort items within each tag by AI score descending
    for tag in items_by_tag:
        items_by_tag[tag].sort(key=lambda x: x.get("ai_score") or 0, reverse=True)

    # Rank tags using Thompson Sampling
    bandit = ThompsonSamplingBandit(tag_weights)
    ranked_tags = bandit.rank_tags()

    # Allocate card slots
    deck = []
    num_tags = len(ranked_tags)
    if num_tags == 0:
        return []

    # Calculate exploration slots
    exploration_slots = max(1, int(max_cards * exploration_ratio))
    exploitation_slots = max_cards - exploration_slots

    # Use ranked tag scores to allocate exploitation slots
    total_score = max(sum(s for _, s in ranked_tags), 0.01)
    tag_slots: dict[str, int] = {}

    for i, (tag, score) in enumerate(ranked_tags):
        if score <= 0:
            tag_slots[tag] = 0
            continue
        # Proportional allocation, ensure each tag gets at least 1 slot
        # if there are enough max_cards
        raw = int(exploitation_slots * score / total_score)
        tag_slots[tag] = max(raw, 0)

    # Ensure minimum 1 slot for tags that have items, if we have room
    tags_with_items = [t for t, _ in ranked_tags if t in items_by_tag and items_by_tag[t]]
    allocated = sum(tag_slots.values())
    remaining = exploitation_slots - allocated
    for tag in tags_with_items:
        if tag_slots.get(tag, 0) == 0 and remaining > 0:
            tag_slots[tag] = 1
            remaining -= 1

    # Add exploration slots to tags with fewer items in deck
    if exploration_slots > 0 and tags_with_items:
        # Give exploration slots to the lowest-weighted tags that have items
        lowest_tags = [t for t, _ in reversed(ranked_tags) if t in items_by_tag][:exploration_slots]
        for tag in lowest_tags:
            if tag not in tag_slots:
                tag_slots[tag] = 0
            tag_slots[tag] += 1

    # Build deck from tag slots
    for tag, _ in ranked_tags:
        slot_count = tag_slots.get(tag, 0)
        available = items_by_tag.get(tag, [])
        selected = available[:slot_count]
        for item in selected:
            deck.append(item)

    # If we still have room, fill with remaining high-score items
    if len(deck) < max_cards:
        used_ids = {item["id"] for item in deck}
        remaining_items = sorted(
            [item for item in today_items if item["id"] not in used_ids],
            key=lambda x: x.get("ai_score") or 0,
            reverse=True,
        )
        deck.extend(remaining_items[: max_cards - len(deck)])

    # Truncate to max_cards
    return deck[:max_cards]
