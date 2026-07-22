"""Root bridge: load backend/tests/test_core.py into this suite."""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1] / "backend"
CORE = BACKEND / "tests" / "test_core.py"


def _load():
    if str(BACKEND) not in sys.path:
        sys.path.insert(0, str(BACKEND))
    try:
        import dotenv  # noqa: F401
    except ImportError:
        # soft stub if python-dotenv not installed in minimal env
        import types

        stub = types.ModuleType("dotenv")
        stub.load_dotenv = lambda *a, **k: None
        sys.modules["dotenv"] = stub
    spec = importlib.util.spec_from_file_location("inkbrief_backend_test_core", CORE)
    mod = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(mod)
    return mod


_mod = _load()
for name in dir(_mod):
    if name.startswith("test_"):
        globals()[name] = getattr(_mod, name)


def test_backend_sources_exist():
    assert (BACKEND / "tag_engine.py").exists()
    assert CORE.exists()
