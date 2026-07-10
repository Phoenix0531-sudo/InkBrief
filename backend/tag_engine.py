"""Tag engine: assigns content items to tags based on keyword rules."""

import json
import os
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
    # Fallback built-in rules
    return {
        "tags_order": ["机会雷达", "与我相关的技术链", "AI 技术与资讯"],
        "rules": {
            "AI 技术与资讯": {
                "keywords": [
                    "ai", "artificial intelligence", "llm", "gpt", "claude",
                    "chatgpt", "openai", "anthropic", "deepseek", "gemini",
                    "agent", "copilot", "machine learning", "model", "neural",
                    "transformer", "rag", "fine-tun", "prompt", "token",
                    "langchain", "llama", "mistral", "qwen",
                ],
                "source_types": ["hackernews", "rss", "ossinsight"],
            },
            "与我相关的技术链": {
                "keywords": [
                    "mcp", "ink", "queue", "brief", "python", "fastapi",
                    "android", "eink", "kindle", "koreader", "kosp",
                    "sqlite", "rest api", "open source", "gradle",
                    "git", "docker", "linux", "terminal", "cli",
                    "uv", "pip", "pypi", "openapi", "swagger",
                    "rss", "atom", "webhook", "json", "yaml",
                ],
                "source_types": ["github", "rss", "hackernews"],
            },
            "机会雷达": {
                "keywords": [
                    "招聘", "人才引进", "事业编", "公务员", "编制",
                    "国企", "央企", "事业单位", "公开遴选",
                    "recruit", "hiring", "job", "career",
                    "高校招聘", "科研助理", "博士后",
                ],
                "source_types": ["rss"],
            },
        },
        "follow_sources": [
            {"name": "Simon Willison", "tag": "AI 技术与资讯"},
            {"name": "V2EX", "tag": "AI 技术与资讯"},
            {"name": "HackerNews", "tag": "AI 技术与资讯"},
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


def tag_item(
    title: str,
    source_type: str = "rss",
    source_name: str = "",
) -> tuple[str, list[str]]:
    """
    Assign a tag to a content item based on keyword rules and source mapping.

    Priority: 机会雷达 > follow_sources > keyword match > source_type fallback > default

    Returns:
        (tag_name, matched_keywords_list)
    """
    rules = get_rules()
    title_lower = title.lower()
    matched_keywords = []

    # 1. Check "机会雷达" keywords first (highest priority)
    for kw in rules["rules"]["机会雷达"]["keywords"]:
        if kw.lower() in title_lower:
            return "机会雷达", [kw]

    # 2. Check follow_sources by source name
    for fs in rules["follow_sources"]:
        if source_name == fs["name"]:
            return fs["tag"], []

    # 3. Keyword match by tags_order (skip 机会雷达 already done)
    for tag_name in rules["tags_order"]:
        if tag_name == "机会雷达":
            continue
        for kw in rules["rules"][tag_name]["keywords"]:
            if kw.lower() in title_lower:
                matched_keywords.append(kw)
        if matched_keywords:
            return tag_name, matched_keywords

    # 4. Fallback by source_type
    for tag_name in rules["tags_order"]:
        if source_type in rules["rules"][tag_name].get("source_types", []):
            return tag_name, []

    # 5. Default
    return "AI 技术与资讯", []
