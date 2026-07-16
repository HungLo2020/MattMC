#!/usr/bin/env python3
"""Detect the current platform and CPU architecture for DevUtils helpers."""

from __future__ import annotations

import argparse
import json
import platform
from dataclasses import asdict, dataclass


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

ARCH_ALIASES = {
    "aarch64": "arm64",
    "amd64": "x86_64",
    "arm64": "arm64",
    "armv6l": "arm",
    "armv7l": "arm",
    "i386": "x86",
    "i686": "x86",
    "x64": "x86_64",
    "x86": "x86",
    "x86_64": "x86_64",
}


@dataclass(frozen=True)
class PlatformInfo:
    platform: str
    arch: str
    raw_platform: str
    raw_arch: str


def normalize_platform(value: str) -> str:
    raw_platform = value.strip().lower()
    if raw_platform.startswith(("cygwin", "mingw", "msys")):
        return "windows"
    normalized = PLATFORM_ALIASES.get(raw_platform)
    if normalized is None:
        raise ValueError(f"Unsupported platform: {value}")
    return normalized


def normalize_arch(value: str) -> str:
    raw_arch = value.strip().lower()
    return ARCH_ALIASES.get(raw_arch, raw_arch or "unknown")


def detect_platform_info() -> PlatformInfo:
    raw_platform = platform.system()
    raw_arch = platform.machine()
    return PlatformInfo(
        platform=normalize_platform(raw_platform),
        arch=normalize_arch(raw_arch),
        raw_platform=raw_platform,
        raw_arch=raw_arch,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Detect current OS platform and CPU architecture.")
    parser.add_argument(
        "--field",
        choices=("platform", "arch", "raw_platform", "raw_arch"),
        help="print only one detected field",
    )
    parser.add_argument(
        "--format",
        choices=("json", "plain"),
        default="json",
        help="output format when --field is not used; default: json",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    info = detect_platform_info()

    if args.field:
        print(getattr(info, args.field))
    elif args.format == "plain":
        print(f"{info.platform} {info.arch}")
    else:
        print(json.dumps(asdict(info), sort_keys=True))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
