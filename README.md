# InkBrief 📡✍️

**Lightweight e-ink agent-synced briefing companion**

[English](README.md) | [中文](README.zh-CN.md)

![CI](https://github.com/Phoenix0531-sudo/InkBrief/actions/workflows/ci.yml/badge.svg)
![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)

面向 Kindle PW3（KOSP Android 4.4.2）的**私人信息简报终端**。

它不是 RSS 阅读器，而是"你的私人信息系统的墨水屏终端"。

## Overview (English)

InkBrief is a **private briefing terminal** for Kindle Paperwhite 3 (KOSP Android 4.4.2). It is not an RSS reader.

Pipeline:

1. **Horizon** scrapes / scores / summarizes sources (RSS, HN, GitHub, …).
2. **InkBrief backend** (FastAPI + SQLite) receives Horizon webhooks, tags items with keyword rules, runs **Thompson Sampling** over tag weights, and builds a daily deck (~10 cards).
3. **Android app** shows swipe cards on e-ink: left = like, right = skip; feedback updates the bandit.

Design goals: offline-friendly local cache, low memory on API 19, zero fancy animations, honest empty states.

### Backend tests

```bash
cd backend
pip install -r requirements.txt   # or: uv sync
pytest -q tests
```

Root `tests/` only re-exports pure tag/webhook unit cases so default CI stays light; full suite lives under `backend/tests` and is installed + run by GitHub Actions when present.

## 核心理念

```
Horizon（引擎层） → AI 评分 + 去重 + 摘要
       ↓ webhook
InkBrief（个性化层） → 关键词打标签 + Thompson Sampling 权重学习
       ↓ REST API
Kindle App（消费层） → 滑动卡片做"喜欢/跳过"反馈
```

- **Horizon**（[Thysrael/Horizon](https://github.com/Thysrael/Horizon)）负责抓取 RSS / HackerNews / GitHub / OSSInsight，AI 评分 0-10，去重和摘要
- **InkBrief Backend** 接收 webhook，用关键词规则打标签（AI 技术与资讯 / 与我相关的技术链 / 机会雷达），Thompson Sampling 管理权重，每日生成 10 张卡组
- **InkBrief Android App** 在 Kindle 上以滑动卡片形式展示内容，左滑喜欢、右滑跳过，权重系统自动学习你的偏好

**设计哲学参考：** [Observe](https://x.com/Guluhehe/status/2075201763154759817)（李简AI 的私人信息观察系统）——"信息流是私人的，请勇敢地手搓吧。"

## 架构

```
Windows 电脑（每天开机一次）
│
├── horizon/                    # Horizon AI 新闻雷达
│    ├─ data/config.json        # Horizon 配置
│    └─ .env                    # API Keys
│
├── backend/                    # InkBrief Backend (FastAPI + SQLite)
│    ├─ app.py                  # 入口
│    ├─ api.py                  # REST API 端点
│    ├─ database.py             # SQLite 层
│    ├─ models.py               # 数据模型
│    ├─ tag_engine.py           # 关键词规则打标签
│    ├─ bandit.py               # Thompson Sampling
│    ├─ deck.py                 # 每日卡组生成
│    ├─ webhook_handler.py      # Horizon webhook 接收
│    └─ weekly.py               # 每周回顾
│
├── android/                    # InkBrief Android App (Java, minSdk 19)
│
├── tools/
│    ├── jdk-11.0.31+11/        # 便携版 JDK 11 (Temurin)
│    └── jdk-17.0.14+7/         # 便携版 JDK 17 (Android 构建用)
│
├── references/
│    └── tag-rules.json         # 关键词规则
│
├── .agent-workflow             # Agent Orchestrator 标记
├── start.bat                   # 一键启动
└── README.md
```

## 快速开始

### 1. 启动后端

```bash
cd backend
uv sync
uv run python app.py
```

后端默认监听 `http://0.0.0.0:8720`。

健康检查：

```bash
curl http://127.0.0.1:8720/v1/health
```

### 2. 配置 Horizon

编辑 `horizon/data/config.json`，填写 AI provider 配置。

编辑 `horizon/.env`：

```env
OPENCODE_GO_API_KEY=your_api_key_here
INKBRIEF_WEBHOOK_URL=http://127.0.0.1:8720/webhook/horizon
```

运行 Horizon：

```bash
cd horizon
uv run horizon
```

### 3. 构建 Android APK

```bash
set JAVA_HOME=D:\3_Code_Projects\InkBrief\tools\jdk-17.0.14+7
set ANDROID_HOME=D:\path\to\android-sdk
cd android
D:\3_Code_Projects\InkBrief\.tools\gradle-8.9\bin\gradle assembleDebug --no-daemon
```

APK 位置：`android/app/build/outputs/apk/debug/app-debug.apk`

### 4. 一键启动

```bash
start.bat
```

## API 文档

所有 API 都需要认证头 `X-InkBrief-Token: dev-token`。

| 端点 | 方法 | 说明 |
|---|---|---|
| `/v1/health` | GET | 健康检查 |
| `/v1/cards/today` | GET | 今日卡组（最多 10 张） |
| `/v1/cards/{id}/like` | POST | 喜欢此卡 |
| `/v1/cards/{id}/skip` | POST | 跳过此卡 |
| `/v1/cards/today/progress` | GET | 今日进度 |
| `/v1/weekly/review` | GET | 本周回顾 |
| `/v1/config/tags` | GET | 标签权重状态 |
| `/webhook/horizon` | POST | 接收 Horizon webhook |

### 请求示例

```bash
# 获取今日卡组
curl -s http://127.0.0.1:8720/v1/cards/today -H "X-InkBrief-Token: dev-token"

# 喜欢卡片
curl -s -X POST http://127.0.0.1:8720/v1/cards/{id}/like -H "X-InkBrief-Token: dev-token"

# 模拟 Horizon 推送
curl -s -X POST http://127.0.0.1:8720/webhook/horizon \
  -H "Content-Type: application/json" \
  -d '{"date":"2026-07-10","message_kind":"overview","important_items":3}'
```

### 响应示例

`GET /v1/cards/today`:
```json
{
  "date": "2026-07-10",
  "cards": [
    {
      "position": 1,
      "tag": "AI 技术与资讯",
      "title": "Claude Code 正式支持 MCP 协议",
      "source": "Simon Willison · rss",
      "ai_score": 8.5,
      "summary": "Anthropic 宣布 Claude Code 正式支持 MCP 协议。",
      "reason": "AI 领域动态（关键词：claude）"
    }
  ],
  "total": 4,
  "liked_today": 0,
  "skipped_today": 0,
  "tag_weights": {
    "AI 技术与资讯": 0.41,
    "与我相关的技术链": 0.32,
    "机会雷达": 0.28
  }
}
```

## 标签系统

三条内容轨道，通过 Thompson Sampling 自动学习你的偏好：

| 标签 | 描述 | 冷启动权重 |
|---|---|---|
| **AI 技术与资讯** | LLM、Agent、AI 框架等 | likes=3, skips=1 |
| **与我相关的技术链** | Python、Android、E-Ink、MCP、你用的技术栈 | likes=3, skips=1 |
| **机会雷达** | 招聘、人才引进、事业编、国企等 | likes=1, skips=1 |

关键词规则见 `references/tag-rules.json`，可随时由 Agent 修改。

## 环境要求

- Python 3.11+（通过 uv 管理）
- JDK 17（便携版已自带：`tools/jdk-17.0.14+7/`）
- Android SDK（build-tools 35.0.0, platform android-35）
- Windows（启动脚本基于 batch）

## 致谢

- **Horizon** — AI 驱动的新闻雷达引擎，提供了内容抓取、评分和摘要的基础能力
- **Observe**（[@Guluhehe](https://x.com/Guluhehe/status/2075201763154759817)）— 其"信息流是私人的"设计哲学为 InkBrief 的个性化筛选提供了核心思路
- **Open Code Go** — AI 推理 API 服务

## License

[MIT](LICENSE) — free for commercial use with attribution.

