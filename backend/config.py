"""InkBrief Backend Configuration."""

import os
import secrets
from dotenv import load_dotenv

load_dotenv()

# Server
PORT = int(os.getenv("INKBRIEF_PORT", "8720"))
HOST = os.getenv("INKBRIEF_HOST", "0.0.0.0")

# Authentication
TOKEN = os.getenv("INKBRIEF_TOKEN")
if not TOKEN:
    TOKEN = secrets.token_urlsafe(32)
    print("=" * 60)
    print("WARNING: INKBRIEF_TOKEN not set in .env!")
    print(f"Generated random token for this session: {TOKEN}")
    print("Set INKBRIEF_TOKEN in backend/.env for persistent access.")
    print("=" * 60)
# WARNING: Change dev-token in production!

# Database
DATABASE_PATH = os.getenv("INKBRIEF_DB_PATH", "inkbrief.db")

# Webhook
WEBHOOK_BATCH_TIMEOUT = int(os.getenv("WEBHOOK_BATCH_TIMEOUT", "300"))  # 5 minutes

# Deck
MAX_CARDS_PER_DAY = 10
MIN_CARDS_PER_DAY = 3
EXPLORATION_RATIO = 0.2

# Cold start weights
COLD_START_WEIGHTS = {
    "AI 技术与资讯": {"likes": 3, "skips": 1},
    "与我相关的技术链": {"likes": 3, "skips": 1},
    "机会雷达": {"likes": 1, "skips": 1},
}

# Tags
TAGS = ["AI 技术与资讯", "与我相关的技术链", "机会雷达"]
