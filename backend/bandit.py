"""Thompson Sampling bandit for tag weight management."""

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
        """
        likes, skips = self.tag_weights.get(tag, (1, 1))
        # Beta(alpha, beta) with alpha = likes + 1, beta = skips + 1
        return float(np.random.beta(likes + 1, skips + 1))

    def rank_tags(self) -> list[tuple[str, float]]:
        """Rank all tags by their Thompson Sampling score.

        Returns:
            List of (tag_name, score) sorted descending by score.
        """
        tags = list(self.tag_weights.keys())
        scores = [(tag, self.sample(tag)) for tag in tags]
        return sorted(scores, key=lambda x: x[1], reverse=True)

    def get_normalized_weights(self) -> dict[str, float]:
        """Get normalized probability weights (sum to 1) for all tags."""
        scores = [self.sample(tag) for tag in self.tag_weights]
        total = sum(scores)
        if total <= 0:
            return {tag: 1.0 / len(scores) for tag in self.tag_weights}
        return {tag: s / total for tag, s in zip(self.tag_weights.keys(), scores)}
