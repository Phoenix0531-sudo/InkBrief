#!/usr/bin/env python
"""InkBrief daily pipeline.

Usage:
  uv run python pipeline.py              # start backend + run Horizon + verify cards
  uv run python pipeline.py --backend    # start backend only (background)
  uv run python pipeline.py --horizon    # run Horizon only (backend must be up)
  uv run python pipeline.py --verify     # health + today's cards check
  uv run python pipeline.py --stop       # stop backend if pid file exists

Logs: pipeline.log
"""

from __future__ import annotations

import argparse
import json
import os
import signal
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime

ROOT = os.path.dirname(os.path.abspath(__file__))
BACKEND = os.path.join(ROOT, "backend")
HORIZON = os.path.join(ROOT, "horizon")
LOG_PATH = os.path.join(ROOT, "pipeline.log")
PID_PATH = os.path.join(ROOT, "backend.pid")
TOKEN = os.environ.get("INKBRIEF_TOKEN", "dev-token")
BASE = os.environ.get("INKBRIEF_URL", "http://127.0.0.1:8720")


def log(msg: str) -> None:
    line = f"[{datetime.now().strftime('%H:%M:%S')}] {msg}"
    print(line, flush=True)
    try:
        with open(LOG_PATH, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    except OSError:
        pass


def http_json(method: str, path: str, body: dict | None = None, timeout: int = 15) -> tuple[int, dict | list | str]:
    url = BASE + path
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("X-InkBrief-Token", TOKEN)
    if body is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
            try:
                return resp.status, json.loads(raw)
            except json.JSONDecodeError:
                return resp.status, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, raw
    except Exception as e:
        return 0, str(e)


def is_backend_up() -> bool:
    code, data = http_json("GET", "/v1/health", timeout=3)
    if code != 200 or not isinstance(data, dict):
        return False
    # HealthResponse: status=ok + cards_today
    if data.get("status") == "ok":
        return True
    return "cards_today" in data


def start_backend() -> None:
    if is_backend_up():
        log("Backend already running")
        return

    # Prefer uv run in backend dir
    env = os.environ.copy()
    env["INKBRIEF_TOKEN"] = TOKEN
    creationflags = 0
    if sys.platform == "win32":
        creationflags = subprocess.CREATE_NEW_PROCESS_GROUP  # type: ignore[attr-defined]

    log_file = open(os.path.join(ROOT, "backend.log"), "a", encoding="utf-8")
    proc = subprocess.Popen(
        ["uv", "run", "python", "app.py"],
        cwd=BACKEND,
        stdout=log_file,
        stderr=subprocess.STDOUT,
        env=env,
        creationflags=creationflags,
    )
    with open(PID_PATH, "w", encoding="utf-8") as f:
        f.write(str(proc.pid))
    log(f"Backend started pid={proc.pid}")

    for i in range(30):
        time.sleep(1)
        if is_backend_up():
            log("Backend health OK")
            return
    raise RuntimeError("Backend failed to become healthy within 30s")


def _kill_pid(pid: int) -> None:
    """Kill a single process by PID only — never taskkill /IM python.exe."""
    if sys.platform == "win32":
        subprocess.run(
            ["taskkill", "/PID", str(pid), "/F", "/T"],
            capture_output=True,
            text=True,
        )
    else:
        try:
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            pass


def stop_backend() -> None:
    """Stop only the InkBrief backend tracked by backend.pid.

    Never kills global python.exe. If pid file is missing but port responds,
    log a warning and leave the foreign process alone.
    """
    if not os.path.exists(PID_PATH):
        log("No backend.pid — nothing to stop")
        if is_backend_up():
            log(
                "Backend still up (started outside pipeline). "
                "Stop that process manually; will NOT kill global python."
            )
        return
    try:
        with open(PID_PATH, encoding="utf-8") as f:
            pid = int(f.read().strip())
    except (OSError, ValueError) as e:
        log(f"Invalid backend.pid: {e}")
        return
    try:
        _kill_pid(pid)
        log(f"Stopped backend pid={pid}")
    except Exception as e:
        log(f"Stop backend failed: {e}")
    try:
        os.remove(PID_PATH)
    except OSError:
        pass


def run_horizon(timeout: int = 900) -> int:
    log("Running Horizon (this may take several minutes)...")
    env = os.environ.copy()
    # Prefer project venv if present
    cmd = ["uv", "run", "horizon"]
    try:
        r = subprocess.run(
            cmd,
            cwd=HORIZON,
            capture_output=True,
            text=True,
            timeout=timeout,
            env=env,
        )
    except subprocess.TimeoutExpired:
        log("Horizon TIMEOUT")
        return -1
    # Tail last lines
    out = (r.stdout or "") + "\n" + (r.stderr or "")
    tail = "\n".join(out.splitlines()[-40:])
    log(f"Horizon exit={r.returncode}")
    if tail.strip():
        for line in tail.splitlines():
            log(f"  | {line}")
    return r.returncode


def verify_cards(min_cards: int = 1) -> bool:
    code, data = http_json("GET", "/v1/health")
    log(f"Health: {code} {data}")
    if code != 200:
        return False

    code, data = http_json("GET", "/v1/cards/today")
    if code != 200 or not isinstance(data, dict):
        log(f"Cards FAIL: {code} {data}")
        return False

    cards = data.get("cards") or []
    log(f"Today cards: {len(cards)} (date={data.get('date')})")
    empty_source = 0
    empty_summary = 0
    for i, c in enumerate(cards[:10], 1):
        src = (c.get("source") or "").strip()
        sm = (c.get("summary") or "").strip()
        if not src or src in {"·", "· rss", "rss"}:
            empty_source += 1
        if not sm or sm.startswith("Item ") or sm.startswith("第 "):
            empty_summary += 1
        log(
            f"  {i}. [{c.get('tag')}] {c.get('title','')[:60]} | "
            f"src={src[:40]} score={c.get('ai_score')} sum={sm[:40]!r}"
        )

    ok = len(cards) >= min_cards
    if empty_source:
        log(f"WARN: {empty_source} cards have weak/empty source")
    if empty_summary:
        log(f"WARN: {empty_summary} cards have dirty/empty summary")
    return ok


def reseed_from_db_retags() -> None:
    """Optional local reprocess: retag existing pending items with new rules.
    Not used in daily path — real reseed comes from Horizon rerun.
    """
    pass


def main() -> int:
    parser = argparse.ArgumentParser(description="InkBrief daily pipeline")
    parser.add_argument("--backend", action="store_true", help="start backend only")
    parser.add_argument("--horizon", action="store_true", help="run Horizon only")
    parser.add_argument("--verify", action="store_true", help="verify health/cards only")
    parser.add_argument("--stop", action="store_true", help="stop backend")
    parser.add_argument("--timeout", type=int, default=900, help="Horizon timeout seconds")
    args = parser.parse_args()

    # Default: full daily
    do_all = not any([args.backend, args.horizon, args.verify, args.stop])

    try:
        if args.stop:
            stop_backend()
            return 0

        if args.backend or do_all:
            start_backend()
            if args.backend and not do_all:
                return 0

        if args.horizon or do_all:
            if not is_backend_up():
                log("Backend not up — starting first")
                start_backend()
            rc = run_horizon(timeout=args.timeout)
            if rc != 0:
                log("Horizon non-zero exit — still verifying cards")

        if args.verify or do_all:
            # Wait a bit for batch finalize if just finished
            time.sleep(2)
            ok = verify_cards(min_cards=1)
            if not ok:
                log("VERIFY FAILED")
                return 1
            log("VERIFY OK")
        return 0
    except Exception as e:
        log(f"FATAL: {e}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
