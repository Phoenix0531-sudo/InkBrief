"""Root bridge: pure backend tag/webhook tests without FastAPI stack."""
from __future__ import annotations

import importlib.util
import sys
import types
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1] / "backend"
CORE = BACKEND / "tests" / "test_core.py"

PURE = {
    "test_clean_summary_strips_horizon_wrapper",
    "test_clean_summary_zh_prefix",
    "test_clean_summary_strips_tags_footer",
    "test_derive_source_from_url",
    "test_derive_source_from_summary_line",
    "test_tag_follow_source",
    "test_tag_agent_chain_keywords",
    "test_tag_keywords_beat_follow_source",
}


def _load():
    if str(BACKEND) not in sys.path:
        sys.path.insert(0, str(BACKEND))
    try:
        import dotenv  # noqa: F401
    except ImportError:
        stub = types.ModuleType("dotenv")
        stub.load_dotenv = lambda *a, **k: None
        sys.modules["dotenv"] = stub
    spec = importlib.util.spec_from_file_location("inkbrief_backend_test_core", CORE)
    mod = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(mod)
    return mod


_mod = _load()
for name in PURE:
    if hasattr(_mod, name):
        globals()[name] = getattr(_mod, name)


def test_backend_sources_exist():
    assert (BACKEND / "tag_engine.py").exists()
    assert CORE.exists()
