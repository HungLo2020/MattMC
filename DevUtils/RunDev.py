#!/usr/bin/env python3
"""Run the MattMC client in the development environment."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path


def script_dir() -> Path:
    return Path(__file__).resolve().parent


def common_platform_dir() -> Path:
    return script_dir() / "Common" / "platform"


def load_platform_detection():
    detection_dir = common_platform_dir() / "detection"
    sys.path.insert(0, str(detection_dir))
    from platform_detection import detect_platform_info, normalize_platform

    return detect_platform_info(), normalize_platform


def repo_root() -> Path:
    current = script_dir()
    while current != current.parent:
        if (current / "gradlew").is_file() or (current / "gradlew.bat").is_file():
            return current
        current = current.parent

    raise SystemExit("ERROR: Could not find gradlew. Are you in the MattMC project?")


def gradle_command(root: Path, platform_name: str) -> list[str]:
    if platform_name == "windows":
        wrapper = root / "gradlew.bat"
        if wrapper.is_file():
            return [str(wrapper)]

    wrapper = root / "gradlew"
    if wrapper.is_file():
        return [str(wrapper)]

    fallback = root / "gradlew.bat"
    if fallback.is_file():
        return [str(fallback)]

    raise SystemExit("ERROR: Could not find gradlew. Are you in the MattMC project?")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the MattMC client in the development environment.")
    parser.add_argument(
        "--platform",
        help="platform to pass to shared helpers: linux, windows, or macos",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    platform_info, normalize_platform = load_platform_detection()
    platform_name = normalize_platform(args.platform) if args.platform else platform_info.platform

    root = repo_root()
    gradle = gradle_command(root, platform_name)
    return subprocess.run([*gradle, "runClient"], cwd=root).returncode


if __name__ == "__main__":
    raise SystemExit(main())
