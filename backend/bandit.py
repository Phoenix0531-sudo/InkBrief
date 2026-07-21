"""Thompson Sampling bandit for tag weight management."""

from __future__ import annotations

import numpy as np

from config import COLD_START_WEIGHTS


class ThompsonSamplingBandit:
    """Multi-armed bandit using Thompson Sampling with Beta distribution.

    Each tag is an arm. The Beta distribution models the probability
    that the user will like content from this tag.
    """

    def __init__(self, tag_weights: dict[str, tuple[int, int]] | None = None):
        """
        Args:
            tag_weights: {tag_name: (likes, skips)}
                         If None, uses cold start defaults.
        """
        if tag_weights is None:
            tag_weights = {
                tag: (w["likes"], w["skips"])
                for tag, w in COLD_START_WEIGHTS.items()
            }
        self.tag_weights = tag_weights

    def sample(self, tag: str) -> float:
        """Draw a sample from the Beta posterior for a given tag.

        Uses Laplace smoothing (+1) to avoid 0-probability.
        Used for deck assembly exploration only.
        """
        likes, skips = self.tag_weights.get(tag, (1, 1))
        return float(np.random.beta(likes + 1, skips + 1))

    def mean(self, tag: str) -> float:
        """Deterministic Beta mean: (likes+1)/(likes+skips+2).

        Used for API display / weekly report so weights don't jitter.
        """
        likes, skips = self.tag_weights.get(tag, (1, 1))
        return (likes + 1) / (likes + skips + 2)

    def rank_tags(self) -> list[tuple[str, float]]:
        """Rank all tags by their Thompson Sampling score.

        Returns:
            List of (tag_name, score) sorted descending by score.
        """
        tags = list(self.tag_weights.keys())
        scores = [(tag, self.sample(tag)) for tag in tags]
        return sorted(scores, key=lambda x: x[1], reverse=True)

    def get_display_weights(self) -> dict[str, float]:
        """Normalized deterministic weights (sum ≈ 1) for UI / weekly."""
        if not self.tag_weights:
            return {}
        means = {tag: self.mean(tag) for tag in self.tag_weights}
        total = sum(means.values())
        if total <= 0:
            n = len(means)
            return {tag: 1.0 / n for tag in means}
        return {tag: m / total for tag, m in means.items()}

    def get_normalized_weights(self) -> dict[str, float]:
        """Alias for display weights (stable). Prefer get_display_weights()."""
        return self.get_display_weights()
