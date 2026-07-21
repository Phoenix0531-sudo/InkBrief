"""Tag engine: assigns content items to tags based on keyword rules."""

from __future__ import annotations

import json
import os
import re
from typing import Optional

# Load default rules
_DEFAULT_RULES_PATH = os.path.join(
    os.path.dirname(os.path.dirname(__file__)), "references", "tag-rules.json"
)


def _load_rules(path: Optional[str] = None) -> dict:
    p = path or _DEFAULT_RULES_PATH
    if os.path.exists(p):
        with open(p, encoding="utf-8") as f:
            return json.load(f)
    # Fallback built-in rules (Agent-tech focused)
    return {
        "tags_order": ["机会雷达", "与我相关的技术链", "AI 技术与资讯"],
        "rules": {
            "AI 技术与资讯": {
                "keywords": [
                    "ai", "llm", "gpt", "claude", "openai", "anthropic",
                    "deepseek", "gemini", "copilot", "rag", "prompt",
                ],
                "source_types": ["hackernews", "rss"],
            },
            "与我相关的技术链": {
                "keywords": [
                    "mcp", "tool use", "function calling", "agent framework",
                    "coding agent", "claude code", "codex", "hermes",
                    "eink", "kindle", "fastapi", "webhook",
                ],
                "source_types": ["github", "ossinsight"],
            },
            "机会雷达": {
                "keywords": [
                    "招聘", "人才引进", "事业编", "公务员", "编制",
                    "recruit", "hiring", "job", "career", "博士后",
                ],
                "source_types": [],
            },
        },
        "follow_sources": [
            {"name": "Simon Willison", "tag": "AI 技术与资讯"},
            {"name": "机器之心", "tag": "AI 技术与资讯"},
            {"name": "HackerNews", "tag": "AI 技术与资讯"},
            {"name": "V2EX", "tag": "与我相关的技术链"},
            {"name": "阮一峰周刊", "tag": "与我相关的技术链"},
            {"name": "Solidot", "tag": "与我相关的技术链"},
            {"name": "github", "tag": "与我相关的技术链"},
            {"name": "ossinsight", "tag": "与我相关的技术链"},
        ],
    }


_rules = None


def get_rules() -> dict:
    global _rules
    if _rules is None:
        _rules = _load_rules()
    return _rules


def reload_rules(path: Optional[str] = None) -> None:
    global _rules
    _rules = _load_rules(path)


def _keyword_matches(keyword: str, text_lower: str) -> bool:
    """Match keywords with word boundaries for plain ASCII tokens."""
    kw = keyword.lower()
    if " " in kw or "-" in kw or not kw.isascii():
        return kw in text_lower
    return bool(re.search(r"\b" + re.escape(kw) + r"\b", text_lower))


def tag_item(
    title: str,
    source_type: str = "rss",
    source_name: str = "",
    summary: str = "",
) -> tuple[str, list[str]]:
    """
    Assign a tag to a content item.

    Priority: 机会雷达 > keyword match > follow_sources
              > source_type fallback > default AI

    Keyword match beats follow_sources so MCP/Claude Code items from
    Simon/HN still land in 技术链.

    Returns:
        (tag_name, matched_keywords_list)
    """
    rules = get_rules()
    haystack = f"{title or ''}\n{summary or ''}".lower()

    # 1. 机会雷达 first
    for kw in rules["rules"]["机会雷达"]["keywords"]:
        if _keyword_matches(kw, haystack):
            return "机会雷达", [kw]

    # 2. keyword match by tags_order (skip 机会雷达)
    for tag_name in rules["tags_order"]:
        if tag_name == "机会雷达":
            continue
        matched_keywords: list[str] = []
        for kw in rules["rules"][tag_name]["keywords"]:
            if _keyword_matches(kw, haystack):
                matched_keywords.append(kw)
        if matched_keywords:
            return tag_name, matched_keywords

    # 3. follow_sources by exact source name
    for fs in rules["follow_sources"]:
        if source_name and source_name == fs["name"]:
            return fs["tag"], []

    # 4. source_type fallback
    for tag_name in rules["tags_order"]:
        if source_type in rules["rules"][tag_name].get("source_types", []):
            return tag_name, []

    # 5. default
    return "AI 技术与资讯", []
