#!/usr/bin/env python3
"""Resolve shared DevUtils directory names for the current platform."""

from __future__ import annotations

import argparse
import json
import os
import platform
import sys
from pathlib import Path
from typing import Any


CONFIG_PATH = Path(__file__).with_name("directories.json")
PLATFORM_ALIASES = {
    "darwin": "macos",
    "mac": "macos",
    "macos": "macos",
    "osx": "macos",
    "linux": "linux",
    "win": "windows",
    "win32": "windows",
    "windows": "windows",
}


def current_platform() -> str:
    system = platform.system().lower()
    if system == "darwin":
        return "macos"
    if system == "windows":
        return "windows"
    if system == "linux":
        return "linux"
    return system


def normalize_platform(value: str) -> str:
    normalized = PLATFORM_ALIASES.get(value.strip().lower())
    if normalized is None:
        raise ValueError(f"Unsupported platform: {value}")
    return normalized


def load_directories(config_path: Path) -> dict[str, Any]:
    try:
        with config_path.open("r", encoding="utf-8") as handle:
            data = json.load(handle)
    except FileNotFoundError as exc:
        raise SystemExit(f"Directory config not found: {config_path}") from exc
    except json.JSONDecodeError as exc:
        raise SystemExit(f"Directory config is not valid JSON: {config_path}: {exc}") from exc

    if not isinstance(data, dict):
        raise SystemExit(f"Directory config must contain a JSON object: {config_path}")
    return data


def resolve_directory(name: str, platform_name: str, directories: dict[str, Any]) -> str:
    entry = directories.get(name)
    if entry is None:
        available = ", ".join(sorted(directories)) or "(none)"
        raise SystemExit(f"Directory '{name}' is not listed. Available directories: {available}")
    if not isinstance(entry, dict):
        raise SystemExit(f"Directory '{name}' must map platforms to paths.")

    if platform_name not in entry or not entry[platform_name]:
        raise SystemExit(f"Directory '{name}' is not listed for this platform: {platform_name}")

    raw_path = entry[platform_name]
    if not isinstance(raw_path, str):
        raise SystemExit(f"Directory '{name}' for platform '{platform_name}' must be a string path.")

    return os.path.abspath(os.path.expandvars(os.path.expanduser(raw_path)))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Resolve a logical DevUtils directory name to a platform-specific path.",
    )
    parser.add_argument("name", nargs="?", help="logical directory name to resolve")
    parser.add_argument(
        "--platform",
        default=current_platform(),
        help="platform to resolve for: linux, windows, or macos; default: current platform",
    )
    parser.add_argument(
        "--config",
        type=Path,
        default=CONFIG_PATH,
        help="directory JSON file; default: DevUtils/Common/directories.json",
    )
    parser.add_argument(
        "--list",
        action="store_true",
        help="list configured logical directory names",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    directories = load_directories(args.config)

    if args.list:
        for name in sorted(directories):
            print(name)
        return 0

    if not args.name:
        print("A directory name is required unless --list is used.", file=sys.stderr)
        return 2

    try:
        platform_name = normalize_platform(args.platform)
        print(resolve_directory(args.name, platform_name, directories))
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 2
    except SystemExit as exc:
        print(str(exc), file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
