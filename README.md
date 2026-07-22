# InkBrief

**E-ink agent-synced daily briefing for Kindle PW3 (KOSP)**

[English](README.md) | [中文](README.zh-CN.md)

![CI](https://github.com/Phoenix0531-sudo/InkBrief/actions/workflows/ci.yml/badge.svg)
![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)

InkBrief is a **private briefing terminal** for Kindle Paperwhite 3 (KOSP Android 4.4.2). It is **not** a generic RSS reader.

Pipeline:

1. **Horizon** (git submodule, pinned) scrapes / scores / summarizes sources.
2. **InkBrief backend** (FastAPI + SQLite) receives webhooks, tags items, runs bandit-style ranking experiments, serves cards.
3. **Android client** on the e-ink device shows today’s deck with extreme UI simplicity.

## Why this exists

Old e-ink Androids cannot run modern agent UIs. The phone/PC agent does the heavy work; the Kindle only renders a calm, low-RAM briefing surface.

## Horizon pin

Submodule commit is pinned (see `.gitmodules`). Do **not** treat dirty edits inside `horizon/` as product patches.

```bash
git submodule update --init --recursive
# if horizon is dirty:
cd horizon && git reset --hard 0414f12b5e6e10faa4eece7eb37a1e70f9c80f4e && cd ..
```

Local Horizon secrets stay in `horizon/.env` (ignored).

## Install / run (dev)

```bash
git clone --recurse-submodules https://github.com/Phoenix0531-sudo/InkBrief.git
cd InkBrief
pip install -r requirements.txt
# backend env: copy backend/.env.example → backend/.env
python pipeline.py --help
python pipeline.py              # backend + horizon + verify (typical day path)
python pipeline.py --backend    # backend only
python pipeline.py --verify
```

Windows helpers: `start.bat` / `stop.bat`.

## Tests

- Root `tests/` — pure tag/webhook bridge tests (no full FastAPI boot required)
- `backend/tests/` — deeper API suite when dependencies are installed

```bash
pytest tests/
```

## Project layout

```
pipeline.py
backend/            # FastAPI app
android/            # e-ink client
horizon/            # pinned submodule
tests/
```

## What this is not

- Not a multi-tenant SaaS reader
- Not a fork playground for unpinned Horizon `main`

## License

MIT. Free for commercial use with attribution. See [LICENSE](LICENSE).
