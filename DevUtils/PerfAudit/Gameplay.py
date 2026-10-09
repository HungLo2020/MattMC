#!/usr/bin/env python3
"""User-facing gameplay graphics benchmark."""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "Common"))

import graphics_harness as harness


if __name__ == "__main__":
    if "--ordinary" in sys.argv[1:]:
        sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tests" / "rendering"))
        import RunOrdinaryPerformance
        raise SystemExit(RunOrdinaryPerformance.main([arg for arg in sys.argv[1:] if arg != "--ordinary"]))
    raise SystemExit(harness.main(["gameplay", *sys.argv[1:]]))
