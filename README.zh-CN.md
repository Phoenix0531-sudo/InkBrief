# InkBrief

**轻量级墨水屏 Agent 同步简报应用**

[English](README.md) | [中文](README.zh-CN.md)

![CI](https://github.com/Phoenix0531-sudo/InkBrief/actions/workflows/ci.yml/badge.svg)
![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)

轻量级墨水屏 Agent 同步简报应用。

> 作者：[Phoenix0531-sudo](https://github.com/Phoenix0531-sudo) · 欢迎学习、二次开发与**商业使用**，请保留本仓库署名与许可证声明。

## 技术栈

Python · Android · Go/脚本

## 功能特性

- 面向墨水屏的低刷新简报流
- Agent / 管道同步内容
- Android 端 + 后端服务

## 快速开始

```bash
git clone https://github.com/Phoenix0531-sudo/InkBrief.git
cd InkBrief
```

```bash
cd backend  # 或查看 pipeline.py
pip install -r requirements.txt  # 若存在
python pipeline.py
```

更完整的英文说明见 [README.md](README.md)。

## 仓库结构（摘要）

```
InkBrief/
├─ .github/
├─ android/
├─ backend/
├─ docs/
├─ horizon/
├─ references/
├─ tools/
├─ LICENSE
├─ pipeline.py
├─ README.md
├─ README.zh-CN.md
```

## 测试

```bash
pip install pytest
pytest -q
```

仓库内 `tests/` 至少包含 smoke 测试；有完整测试套件时以 CI 为准。

## CI

GitHub Actions（`push` / `pull_request`）会：

- 安装依赖（requirements / pyproject）
- 运行 `pytest`（**硬失败**）
- 尽力做语法/结构检查

## 许可证

[MIT](LICENSE) — 可自由使用、修改、分发与**商用**，需保留版权与许可声明（提及本仓库 / 作者即可）。

## 关于

维护者：[Phoenix0531-sudo](https://github.com/Phoenix0531-sudo)
