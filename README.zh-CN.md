# InkBrief

**轻量墨水屏 Agent 同步简报 — 后端 + Android 界面。**

[English](README.md) | [中文](README.zh-CN.md)

[![CI](https://github.com/Phoenix0531-sudo/InkBrief/actions/workflows/ci.yml/badge.svg)](https://github.com/Phoenix0531-sudo/InkBrief/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Python](https://img.shields.io/badge/python-3.10%2B-blue.svg)](https://www.python.org/)

轻量墨水屏 Agent 同步简报 — 后端 + Android 界面。

面向受限屏幕与 Agent 生成摘要。


## 功能

- 📟 墨水屏友好的简报呈现
- 🔗 后端 + Android 客户端树
- 🧰 tools/ + horizon 参考
- ✅ 核心检查 CI

## 快速开始

### 安装

```bash
git clone https://github.com/Phoenix0531-sudo/InkBrief.git
cd InkBrief
# follow backend/ and android/ docs for environment setup
```

### 使用

按 `backend/` 启动服务，再连接 Android 客户端。详见 `docs/`。

## 项目结构

```
android/  backend/  tools/  horizon/
tests/  docs/
```

## 说明

硬件显示约束驱动 UX — 偏好短而高信噪比的简报。

## 许可证

MIT。在注明出处的前提下可商业使用（以 LICENSE 为准）。详见 [LICENSE](LICENSE)。
