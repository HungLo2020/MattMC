"""Small shared fixtures for focused graphics-audit unit tests."""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "Common"))

import graphics_harness as harness


def fake_repo(root: Path, name: str) -> harness.RepoTarget:
    repo = root / name
    repo.mkdir(parents=True, exist_ok=True)
    (repo / ".git").mkdir(exist_ok=True)
    script_dir = repo / "DevUtils" / "Common"
    script_dir.mkdir(parents=True, exist_ok=True)
    (script_dir / "capture_runner.py").write_text(
        "raise SystemExit(0)\n", encoding="utf-8"
    )
    (repo / "gradlew").write_text("#!/usr/bin/env sh\nexit 0\n", encoding="utf-8")
    role = "current-under-test" if name == "current" else "frozen-baseline"
    return harness.RepoTarget(name, repo, role)
