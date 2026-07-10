# InkBrief 实现方案 — 详细审查报告

> 审查日期：2026-07-10  
> 审查范围：backend/ (config.py, database.py) + Horizon 集成层 + 架构计划  
> 审查深度：代码级（已实现部分）+ 设计级（未实现部分）  
> 状态摘要：**2 个 P0 问题，5 个 P1 问题，4 个 P2 优化**

---

## P0 — 必须修复

### P0-1: Webhook 批次完成检测缺少信号机制

**严重性**: P0 — 批次会永久卡在 'receiving' 状态，每日卡组永不生成

**问题描述**:  
Horizon 的 `summary_and_items` delivery 模式会发送 **多次独立 POST 请求**（1 个 overview + N 个 item），如 `webhook.py:749-751` 所示：

```python
for message in messages:
    await self.notify(message)
```

当前 `increment_webhook_batch()` 在 `received_count >= expected_count` 时标记完成。这本身正确——overview 消息携带了 `important_items` 计数。但 **没有超时机制**。如果 Horizon 在发送过程中崩溃、网络中断、第 3/5 个 item 丢失，batch 永远无法 reach expected_count，每日卡组也就永远无法生成。

`config.py` 中定义的 `WEBHOOK_BATCH_TIMEOUT = 300` **在 database.py 中完全没有被使用**。

**修复方案**:

1. 在 `webhook_batches` 表中添加 `deadline` 列（使用 batch 创建时间 + timeout）：
```python
# database.py
CREATE TABLE IF NOT EXISTS webhook_batches (
    date TEXT PRIMARY KEY,
    expected_count INTEGER NOT NULL,
    received_count INTEGER DEFAULT 0,
    status TEXT DEFAULT 'receiving',
    started_at TEXT NOT NULL,
    deadline TEXT NOT NULL  -- 新增：ISO格式超时时间
);
```

2. 在 `create_webhook_batch()` 中计算 deadline：
```python
from datetime import timedelta
from config import WEBHOOK_BATCH_TIMEOUT

def create_webhook_batch(date: str, expected_count: int) -> None:
    now = datetime.utcnow()
    deadline = (now + timedelta(seconds=WEBHOOK_BATCH_TIMEOUT)).isoformat()
    conn.execute(
        """INSERT OR REPLACE INTO webhook_batches
           (date, expected_count, received_count, status, started_at, deadline)
           VALUES (?, ?, 0, 'receiving', ?, ?)""",
        (date, expected_count, now.isoformat(), deadline),
    )
```

3. 在获取 pending batch 时检查超时：
```python
def get_active_webhook_batch() -> dict | None:
    conn = get_connection()
    now = datetime.utcnow().isoformat()
    row = conn.execute(
        """SELECT * FROM webhook_batches
           WHERE status = 'receiving' AND deadline > ?
           ORDER BY started_at DESC LIMIT 1""",
        (now,),
    ).fetchone()
    # 如果有超时的 batch，标记为 timeout
    conn.execute(
        """UPDATE webhook_batches SET status = 'timeout'
           WHERE status = 'receiving' AND deadline <= ?""",
        (now,),
    )
    conn.commit()
    conn.close()
    return dict(row) if row else None
```

4. 在 `deck.py` 的 assemble 逻辑中处理 timeout：
```python
if not active_batch:
    # 检查是否有超时的 batch
    timed_out = get_timed_out_batch(today)
    if timed_out and timed_out["received_count"] > 0:
        # 用已收到的部分数据生成卡组
        deck = assemble_partial_deck(today, timed_out["received_count"])
```

---

### P0-2: SQLite 并发写入缺少 busy_timeout 保护

**严重性**: P0 — 多 worker 场景下会产生 "database is locked" 错误

**问题描述**:  
数据库使用了 WAL 模式（`PRAGMA journal_mode=WAL`），这允许多个读者并发。但 SQLite **同一时间只能有一个写入者**。当 uvicorn 启动多个 worker 时，多个进程同时调用 `get_connection()` → `conn.execute(...)` → `conn.commit()` → `conn.close()` 时，第二个写入者会立即失败，因为 `busy_timeout` 默认为 0。

每个数据库函数都打开了独立连接（`get_connection()` → 操作 → `conn.close()`），这加剧了问题。

**修复方案**:

在 `get_connection()` 中添加 busy_timeout：
```python
def get_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(DATABASE_PATH, timeout=5)  # timeout=5 seconds
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    conn.execute("PRAGMA busy_timeout=5000")  # 5 seconds
    return conn
```

或者更优方案：在 `app.py` 中使用单个 SQLite 连接并通过 FastAPI 的 `Depends()` 注入，配合 `asyncio.Lock` 或 `sqlite3.connect(..., check_same_thread=False)` + 线程锁。

---

## P1 — 建议修复

### P1-1: 冷启动时 /v1/cards/today 必须合理响应

**严重性**: P1 — 用户体验关键路径

**问题描述**:  
冷启动第一天（没有 webhook 来过、没有任何 content_items），`get_daily_deck(today)` 返回空列表。`get_today_progress()` 返回 `{"total": 0, "liked": 0, "skipped": 0}`。

Android 端收到空列表后，可能显示空白页面或崩溃（空指针检查不到位时）。

**修复方案**:

在 `api.py` 中添加冷启动检测，返回有意义的响应：

```python
@app.get("/v1/cards/today")
async def get_today_cards():
    today = datetime.utcnow().strftime("%Y-%m-%d")
    deck = get_daily_deck(today)
    progress = get_today_progress(today)
    
    if not deck:
        pending_count = len(get_pending_items(today))
        if pending_count == 0:
            return {
                "status": "warming_up",
                "message": "暂无今日简报，Horizon 尚未推送今日内容",
                "cards": [],
                "progress": {"total": 0, "liked": 0, "skipped": 0},
                "estimated_arrival": None,
            }
        else:
            return {
                "status": "assembling",
                "message": f"收到 {pending_count} 条内容，正在组装卡组",
                "cards": [],
                "progress": progress,
            }
    
    return {"status": "ready", "cards": deck, "progress": progress}
```

Android 端需要处理 `status` 字段：
- `warming_up`: 显示"等待今日简报到达..."，给出友好提示，5 分钟后自动重试
- `assembling`: 显示"简报正在生成中..."
- `ready`: 正常显示卡片

---

### P1-2: Token 硬编码默认值且缺少 .env 文件

**严重性**: P1 — 生产安全风险

**问题描述**:  
```python
TOKEN = os.getenv("INKBRIEF_TOKEN", "dev-token")
```

如果用户没有创建 `.env` 文件（目前确实没有 `.env` 文件存在于项目中），所有部署都会使用 `"dev-token"`。项目目录中没有 `.env.example`、没有安装文档提示用户修改 token。

另外，X-InkBrief-Token 的校验位置问题：使用 middleware 更简洁（所有路由统一校验），但 health check 端点（如 `/health`）应允许免校验。所以 middleware 需要路径白名单。

**修复方案**:

1. 创建 `.env.example` 和首次运行脚本：

`.env.example`:
```
INKBRIEF_TOKEN=your-secure-token-here
INKBRIEF_PORT=8720
INKBRIEF_HOST=0.0.0.0
INKBRIEF_DB_PATH=inkbrief.db
WEBHOOK_BATCH_TIMEOUT=300
```

2. 在 `config.py` 中添加 token 强度检查：
```python
import secrets
import os

TOKEN = os.getenv("INKBRIEF_TOKEN")
if not TOKEN:
    TOKEN = secrets.token_urlsafe(32)
    print("WARNING: INKBRIEF_TOKEN not set. Generated random token for this session.")
    print(f"WARNING: Token: {TOKEN}")
    print("WARNING: Set INKBRIEF_TOKEN in .env for persistent access.")
```

3. 使用 middleware 校验 token，带健康检查白名单：
```python
@app.middleware("http")
async def verify_token(request: Request, call_next):
    if request.url.path in ("/health", "/docs", "/openapi.json"):
        return await call_next(request)
    
    token = request.headers.get("X-InkBrief-Token")
    if token != config.TOKEN:
        return JSONResponse(
            status_code=401,
            content={"detail": "Invalid or missing X-InkBrief-Token"},
        )
    return await call_next(request)
```

---

### P1-3: daily_decks 和 feedback 表缺少关键索引

**严重性**: P1 — SQLite 全表扫描导致读取性能退化

**问题描述**:  
当前索引：
```sql
CREATE INDEX IF NOT EXISTS idx_content_status ON content_items(status);
CREATE INDEX IF NOT EXISTS idx_content_created ON content_items(created_at);
CREATE INDEX IF NOT EXISTS idx_daily_decks_date ON daily_decks(date);
CREATE INDEX IF NOT EXISTS idx_feedback_item ON feedback(content_item_id);
```

缺少的索引：
- **daily_decks(date, position)**: `get_daily_deck()` 的 `WHERE date=? ORDER BY dd.position` 可以利用复合索引避免排序
- **daily_decks(content_item_id)**: `get_today_progress()` JOIN content_items 时需要
- **content_items(tag)**: 按标签过滤卡片时需要
- **feedback(created_at)**: `get_weekly_feedback()` 的日期范围查询需要

**修复方案**:
```sql
CREATE INDEX IF NOT EXISTS idx_daily_decks_date_pos ON daily_decks(date, position);
CREATE INDEX IF NOT EXISTS idx_daily_decks_item ON daily_decks(content_item_id);
CREATE INDEX IF NOT EXISTS idx_content_tag ON content_items(tag);
CREATE INDEX IF NOT EXISTS idx_feedback_created ON feedback(created_at);
```

---

### P1-4: Horizon summary_and_items 格式验证 — batch 检测逻辑需要确认

**严重性**: P1 — 集成正确性依赖

**问题描述**:  
审查了 `horizon/src/services/webhook.py` 的全部代码后，`summary_and_items` 的实际行为已经明确：

1. 发送 **N+1** 次独立的 HTTP POST 请求（N = important_items 数量）
2. 第 1 个请求：`message_kind="overview"`，携带 `important_items=N` 字段（项目总数）
3. 第 2...N+1 个请求：`message_kind="item"`，携带单条 item 数据
4. 每次请求互不关联——没有 batch_id、没有序列号、没有终止信号

关键问题：**overview 和 items 的到达顺序不一定严格**。如果 overview 最后一个到达（网络延迟/重试），批次创建会滞后。如果某个 item 先于 overview 到达，当前代码无法将其归类到批次。

**修复方案**:

在 `webhook_handler.py` 中实现 **延迟匹配机制**：

1. 所有请求先进入一个暂存区，按日期分组
2. 收到 overview 后：创建 batch，将暂存区中匹配该日期的 items 归入 batch
3. 收到 item 后：
   - 如果该日期已有 active batch → 计入 batch
   - 如果没有 → 放入暂存区，启动 30 秒定时器等待 overview
4. 每次 increment 后检查 `received_count >= expected_count` → 触发 deck assembly

```python
# 暂存区：日期 → [item_data, ...]
_item_buffer: dict[str, list[dict]] = {}
_BUFFER_TIMEOUT = 30  # seconds

async def handle_webhook(payload: dict):
    date = payload.get("date")
    kind = payload.get("message_kind")
    
    if kind == "overview":
        expected = payload["important_items"]
        create_webhook_batch(date, expected)
        # 检查暂存区是否有遗漏的 items
        if date in _item_buffer:
            for item in _item_buffer.pop(date):
                await handle_item(item)
    
    elif kind == "item":
        batch = get_active_webhook_batch_for_date(date)
        if batch:
            increment_webhook_batch(date)
        else:
            # 暂存，等待 overview
            _item_buffer.setdefault(date, []).append(payload)
            # 30秒后超时清理
            asyncio.create_task(flush_stale_buffer(date))
```

---

### P1-5: Android HttpURLConnection TLS 1.2 兼容性（API 19）

**严重性**: P1 — Android 4.4 用户无法连接

**问题描述**:  
Android 4.4 (API 19) 默认**不启用** TLS 1.2。`HttpURLConnection` 默认使用 TLS 1.0。现代服务器（包括大多数 HTTPS API）已经禁用 TLS 1.0/1.1。

如果没有显式启用 TLS 1.2，InkBrief Android 端在 API 19 设备上无法与后端 API 建立 HTTPS 连接。

**修复方案**:

方法一：在 `onCreate` 或应用初始化时全局启用 TLS 1.2：
```java
// 应用启动时调用一次
public static void enableTls12() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
        try {
            SSLContext context = SSLContext.getInstance("TLSv1.2");
            context.init(null, null, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
        } catch (Exception e) {
            Log.e("TLS", "Failed to enable TLS 1.2", e);
        }
    }
}
```

方法二：使用 `SSLSocket` 工厂，在建立连接时指定协议：
```java
// 需要设置支持的协议列表
connection.setSSLSocketFactory(new TLSSocketFactory());
```

推荐方法一，简单可靠。

---

## P2 — 可选优化

### P2-1: content_items 缺少 content 字段限制 Thompson Sampling 深度

**严重性**: P2 — 功能增强

**问题描述**:  
当前 `content_items` 表有 `summary` 但没有 `content`（原文）。Horizon 的 `ContentItem` 有完整的 `content` 字段。对于 eInk 终端，不存储全文是正确的（节省空间），但 Thompson Sampling 的 tag 权重重评估时，仅靠 summary 难以准确判断内容质量。

在 Observe 哲学中，"信源分层"的实践需要对内容有更深的理解。

**优化方案**:

考虑添加 `content_tier` 字段（枚举：summary / truncated / full），或仅存储 Horizon 的 `ai_summary` 和 `ai_score` 以备后用。在 `tag_engine.py` 中，除了 tag 分类外，也写入 `ai_score` 和 `ai_tags`：

```sql
ALTER TABLE content_items ADD COLUMN content_tier TEXT DEFAULT 'summary';
ALTER TABLE content_items ADD COLUMN ai_summary TEXT DEFAULT '';
ALTER TABLE content_items ADD COLUMN ai_tags TEXT DEFAULT '[]';
```

---

### P2-2: tag_weights 缺少 recency decay 机制

**严重性**: P2 — 长期运行后推荐质量下降

**问题描述**:  
当前 `tag_weights` 是累加计数器（likes/skips 不断增加）。3 个月后，早期数据的影响仍然和昨天一样大。用户的兴趣会漂移，但 Thompson Sampling 无法适应。

**优化方案**:

在 `weekly.py`（周报生成器）中添加权重衰减逻辑。每周衰减一次：

```python
def decay_tag_weights(decay_factor: float = 0.9):
    """每周衰减 tag 权重，使近期反馈权重更高"""
    conn = get_connection()
    # 将 likes/skips 乘以衰减因子，但保留最少 1 的底数
    conn.execute(
        """UPDATE tag_weights
           SET likes = MAX(1, ROUND(likes * ?)),
               skips = MAX(1, ROUND(skips * ?)),
               updated_at = ?""",
        (decay_factor, decay_factor, datetime.utcnow().isoformat()),
    )
    conn.commit()
    conn.close()
```

同时添加 `total_impressions` 列，用于计算 Thompson Sampling 的置信度：
```sql
ALTER TABLE tag_weights ADD COLUMN total_impressions INTEGER DEFAULT 0;
```

---

### P2-3: start.bat 的 taskkill 方式需要精确化

**严重性**: P2 — 但可能导致用户其他 Python 进程被杀

**问题描述**:  
计划中的 `start.bat` 包含 `taskkill /F /IM python.exe`。这会杀死**所有** Python 进程，包括用户正在运行的其他项目、Horizon 后端等。

**优化方案**:

使用窗口标题或 PID 文件精确控制：

```batch
@echo off
cd /d "%~dp0"

:: 先通过窗口标题杀死旧实例
taskkill /F /FI "WINDOWTITLE eq InkBrief*" 2>nul

:: 启动新实例
title InkBrief Backend
uv run uvicorn app:app --host 0.0.0.0 --port 8720 --workers 1

:: 或者使用 PID 文件
if exist inkbrief.pid (
    set /p OLD_PID=<inkbrief.pid
    taskkill /F /PID %OLD_PID% 2>nul
)
echo %! > inkbrief.pid
```

---

### P2-4: Android 三帧切换策略推荐

**严重性**: P2 — 实现细节

**问题描述**:  
三帧策略（"当天简报" → "阅览中卡片" → "已完成卡片"）。

选择 `ViewFlipper` 还是清空 `ViewGroup`？

**分析**:
- eInk 屏幕**不支持流畅动画**，所以 ViewFlipper 的动画优势不存在
- ViewFlipper 在内存中保持所有子 View，对 256MB RAM 的 Kindle PW3 是负担
- 清空 ViewGroup (removeAllViews + addView) 更节省内存，逻辑更清晰
- 但清空策略的缺点是滚轮位置会重置

**推荐方案**: **ViewFlipper + 懒加载**。使用 ViewFlipper 的三帧布局，但每帧使用 `ViewStub` 延迟 inflate。第一帧（当天简报）立即 inflate，其余帧在切换到时才 inflate。

```xml
<ViewFlipper android:id="@+id/flipper">
    <ViewStub android:id="@+id/frame_today" android:layout="@layout/frame_today" />
    <ViewStub android:id="@+id/frame_current" android:layout="@layout/frame_current" />
    <ViewStub android:id="@+id/frame_done" android:layout="@layout/frame_done" />
</ViewFlipper>
```

如果 ViewStub 过于复杂，直接用 removeAllViews/addView 就是 API 19 下最正确的选择。

---

### P2-5: 同步失败时缓存队列的幂等性

**严重性**: P2 — 数据一致性问题

**问题描述**:  
Android 离线操作队列在同步失败时会重试。如果没有幂等性机制，重试可能导致：
- 同一 feedback（like/skip）被重复插入 → feedback 表膨胀
- 多次 like 同一卡片 → tag_weights 被重复累加

**优化方案**:

1. 添加 `client_idempotency_key` 列到 feedback 表（可选）：
```sql
ALTER TABLE feedback ADD COLUMN client_idempotency_key TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS idx_feedback_idempotency ON feedback(client_idempotency_key);
```

2. Android 端生成唯一的幂等键：
```java
String idempotencyKey = itemId + ":" + action + ":" + System.currentTimeMillis();
```

3. API 端使用幂等键去重：
```python
@app.post("/v1/feedback")
async def submit_feedback(feedback: FeedbackRequest):
    if feedback.idempotency_key:
        existing = get_feedback_by_idempotency_key(feedback.idempotency_key)
        if existing:
            return {"status": "duplicate", "existing_id": existing["id"]}
    # ... 正常处理流程
```

4. 简化方案：在 Android 端使用 SQLite 事务标记已同步的条目，重试前先检查是否已提交。

---

## 结构完整性总评

### 依赖链审查

`database.py → models.py → tag_engine.py → bandit.py → deck.py → webhook_handler.py → weekly.py → api.py → app.py`

| 模块 | 状态 | 依赖关系 | 问题 |
|------|------|----------|------|
| config.py | ✅ 已实现 | 无依赖 | token 默认值安全风险（P1-2） |
| database.py | ✅ 已实现 | config.py | 缺少 busy_timeout（P0-2），缺少索引（P1-3） |
| models.py | ❌ 未实现 | database.py | 需定义 Pydantic 请求/响应模型 |
| tag_engine.py | ❌ 未实现 | database.py, models.py | 需集成 tag-rules.json |
| bandit.py | ❌ 未实现 | database.py, tag_engine.py | Thompson Sampling 公式已验证正确 |
| deck.py | ❌ 未实现 | bandit.py, database.py | 需处理 exploration_ratio + 冷启动 |
| webhook_handler.py | ❌ 未实现 | database.py, models.py | 批次完成检测缺少信号（P0-1），无序到达（P1-4） |
| weekly.py | ❌ 未实现 | database.py | 可添加权重衰减（P2-2） |
| api.py | ❌ 未实现 | 所有以上模块 | 冷启动端点（P1-1），token middleware（P1-2） |
| app.py | ❌ 未实现 | api.py | uvicorn workers=1 可避免 P0-2 |

**依赖链总体评价**: ✅ 方向正确。模块按从底层数据到高层 API 的层次组织，没有循环依赖。建议在 `app.py` 中使用 `workers=1` 以规避 SQLite 并发问题。

### 数据模型审查总表

| 表 | 字段完整性 | 索引覆盖 | 约束完整性 | 整体评价 |
|----|-----------|---------|-----------|---------|
| content_items | ⚠️ 缺少 content/ai_summary/ai_tags | ⚠️ 缺 tag 索引 | ✅ PK 合理 | 良好，可增强 |
| tag_weights | ⚠️ 缺 total_impressions/last_seen_at | ✅ PK 足够 | ✅ 合理 | 基本够用 |
| feedback | ✅ 完整 | ⚠️ 缺 created_at 索引 | ⚠️ 缺唯一约束 | 需要幂等性 |
| daily_decks | ⚠️ 缺 exploration 标记 | ⚠️ 缺复合索引 | ⚠️ 缺唯一约束 | 建议增强 |
| webhook_batches | ⚠️ 缺 deadline 列 | ✅ PK 足够 | ✅ 合理 | 需要超时机制（P0-1） |

---

## Observe 哲学融合建议

### "写作弹药"维度融入 Thompson Sampling

Observe 哲学强调从信息中沉淀"写作弹药"——那些能激发写作灵感、提供论证素材的内容。

**技术方案**：引入第二个 Thompson Sampling 维度，形成**双目标推荐**：

```
总效用 = α × 阅读兴趣 + (1-α) × 写作价值
```

其中 `α` 是配置的权衡参数（默认 0.7）。

- 阅读兴趣 = Beta(likes + 1, skips + 1) 采样值（已有）
- 写作价值 = Beta(writing_likes + 1, writing_skips + 1) 采样值（新建）

在 `tag_weights` 中添加：
```sql
ALTER TABLE tag_weights ADD COLUMN writing_likes INTEGER DEFAULT 0;
ALTER TABLE tag_weights ADD COLUMN writing_skips INTEGER DEFAULT 0;
```

卡片详情页增加"标记为写作素材"按钮（☆ 图标），触发 `action='writing_like'`。

### 信源分层增强 tag_engine

Observe 强调不同信源有不同的信号/噪音比。HackerNews 的 AI 文章比个人 RSS 博客更有信息量。

**技术方案**：将 source_type 作为 Thompson Sampling 的**超先验**：

```python
# 信源先验配置
SOURCE_PRIORS = {
    "hackernews": {"likes": 4, "skips": 1},     # 高信号源
    "github": {"likes": 2, "skips": 2},          # 中等
    "rss": {"likes": 2, "skips": 3},             # 中等偏低
    "ossinsight": {"likes": 3, "skips": 2},      # 中等偏上
    "twitter": {"likes": 1, "skips": 4},         # 高噪音源
}
```

在 `bandit.py` 中采样时：

```python
def sample_tag_score(tag_weights: dict, source_type: str) -> float:
    """带信源先验的 Thompson Sampling"""
    prior = SOURCE_PRIORS.get(source_type, {"likes": 1, "skips": 1})
    alpha = tag_weights["likes"] + prior["likes"] + 1
    beta = tag_weights["skips"] + prior["skips"] + 1
    return numpy.random.beta(alpha, beta)
```

这使得高信噪比信源的内容天然获得更高的初始采样概率，但也保留了用户反馈的修正能力。

---

## 总结优先级排序

| 编号 | 级别 | 领域 | 关键影响 |
|------|------|------|---------|
| P0-1 | **P0** | Webhook | 批次永久卡住 → 今日无简报 |
| P0-2 | **P0** | 数据库 | 多 worker 下写入崩溃 |
| P1-1 | P1 | API | 冷启动用户看到空/错误页面 |
| P1-2 | P1 | 安全 | 生产部署默认 token |
| P1-3 | P1 | 性能 | 数据量大后查询退化 |
| P1-4 | P1 | Webhook | 消息乱序到达丢失数据 |
| P1-5 | P1 | Android | API 19 TLS 连接失败 |
| P2-1~5 | P2 | 优化 | 长期维护和体验改进 |

**建议开发顺序**: P0-2 → P1-5 → P0-1 → P1-4 → P1-1 → P1-2 → P1-3 → P2 项

（P0-2 影响所有数据库操作，P1-5 影响 Android 设备连接后端，这两个需要最优先解决）
