# InkBrief

**面向 Kindle PW3（KOSP）的墨水屏 Agent 同步简报**

[English](README.md) | [中文](README.zh-CN.md)

![CI](https://github.com/Phoenix0531-sudo/InkBrief/actions/workflows/ci.yml/badge.svg)
![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)

InkBrief 是 Kindle Paperwhite 3（KOSP Android 4.4.2）上的**私人简报终端**。**不是**通用 RSS 阅读器。

流水线：

1. **Horizon**（git 子模块，已钉死）抓取 / 打分 / 摘要  
2. **InkBrief 后端**（FastAPI + SQLite）收 webhook、打标签、bandit 排序实验、下发卡牌  
3. **Android 客户端**在墨水屏上展示今日牌组（极简 UI）  

## 为什么做这个

旧墨水屏 Android 跑不动现代 Agent UI。重活在 PC/手机 Agent；Kindle 只做低内存、安静的简报面。

## Horizon 钉扎

子模块 commit 固定（见 `.gitmodules`）。不要把 `horizon/` 里的本地脏改当产品补丁。

```bash
git submodule update --init --recursive
cd horizon && git reset --hard 0414f12b5e6e10faa4eece7eb37a1e70f9c80f4e && cd ..
```

Horizon 密钥只放 `horizon/.env`（已 ignore）。

## 安装 / 运行（开发）

```bash
git clone --recurse-submodules https://github.com/Phoenix0531-sudo/InkBrief.git
cd InkBrief
pip install -r requirements.txt
python pipeline.py --help
python pipeline.py
```

Windows：`start.bat` / `stop.bat`。

## 测试

- 根目录 `tests/` — 纯 tag/webhook  
- `backend/tests/` — 完整 API（需依赖）  

```bash
pytest tests/
```

## 目录结构

```
pipeline.py
backend/
android/
horizon/
tests/
```

## 明确不做

- 非多租户 SaaS 阅读器  
- 非未钉扎的 Horizon `main` 试验场  

## 许可证

MIT。可在署名前提下商用。见 [LICENSE](LICENSE)。
