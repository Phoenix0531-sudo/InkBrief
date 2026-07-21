"""Weekly review generation for InkBrief."""

from datetime import datetime, timedelta

from bandit import ThompsonSamplingBandit
from database import get_all_tag_weights, get_weekly_feedback, get_weekly_top_items


def get_week_range() -> tuple[str, str]:
    """Get the current week's start (Monday) and end (Sunday) dates."""
    today = datetime.now()
    monday = today - timedelta(days=today.weekday())
    sunday = monday + timedelta(days=6)
    return monday.strftime("%Y-%m-%d"), sunday.strftime("%Y-%m-%d")


def _compute_trend(tag: str, week_feedback: list[dict]) -> str:
    """Simple trend indicator based on like/skip ratio.

    Requires at least 5 total interactions for a meaningful trend;
    returns 'stable' for smaller samples.
    """
    likes = 0
    skips = 0
    for fb in week_feedback:
        if fb["tag"] == tag:
            if fb["action"] == "like":
                likes = fb["count"]
            elif fb["action"] == "skip":
                skips = fb["count"]
    total = likes + skips
    if total < 5:
        return "stable"
    ratio = likes / total
    if ratio >= 0.7:
        return "up"
    if ratio <= 0.3:
        return "down"
    return "stable"


def generate_weekly_review(
    week_start: str | None = None,
    week_end: str | None = None,
) -> dict:
    """Generate a weekly review report."""
    if not week_start or not week_end:
        week_start, week_end = get_week_range()

    week_feedback = get_weekly_feedback(week_start, week_end)

    total_liked = sum(fb["count"] for fb in week_feedback if fb["action"] == "like")
    total_skipped = sum(fb["count"] for fb in week_feedback if fb["action"] == "skip")

    tag_weights = get_all_tag_weights()
    bandit = ThompsonSamplingBandit(tag_weights)
    normalized_weights = bandit.get_display_weights()

    tags = []
    suggestions = []

    for tag in tag_weights.keys():
        likes = sum(
            fb["count"]
            for fb in week_feedback
            if fb["tag"] == tag and fb["action"] == "like"
        )
        skips = sum(
            fb["count"]
            for fb in week_feedback
            if fb["tag"] == tag and fb["action"] == "skip"
        )
        weight = normalized_weights.get(tag, 0.5)
        trend = _compute_trend(tag, week_feedback)
        top_items = get_weekly_top_items(tag, week_start, week_end)

        tags.append(
            {
                "name": tag,
                "likes": likes,
                "skips": skips,
                "weight": round(weight, 2),
                "trend": trend,
                "top_items": top_items,
            }
        )

        if likes == 0 and skips == 0:
            suggestions.append(f"{tag} 本周没有被划过，是否考虑调整此标签？")
        elif trend == "up" and weight > 0.7:
            suggestions.append(f"{tag} 权重持续上升，你的兴趣在该领域集中。")
        elif trend == "down" and weight < 0.3:
            suggestions.append(f"{tag} 权重下降较快，是否需要调整此标签的关键词？")
        elif skips > likes * 3 and total_liked + total_skipped > 10:
            suggestions.append(
                f"{tag} 的跳过率较高（{skips}/{likes + skips}），"
                "考虑降低此标签的权重或调整规则。"
            )

    if not suggestions:
        suggestions.append("本周反馈数据较少，继续使用冷启动权重。")

    return {
        "week_start": week_start,
        "week_end": week_end,
        "total_liked": total_liked,
        "total_skipped": total_skipped,
        "tags": tags,
        "suggestions": suggestions,
    }
