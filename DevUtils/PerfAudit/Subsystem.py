#!/usr/bin/env python3
"""User-facing isolated rendering subsystem benchmark."""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "Common"))

import graphics_harness as harness


if __name__ == "__main__":
    raise SystemExit(harness.main(["subsystem", *sys.argv[1:]]))

