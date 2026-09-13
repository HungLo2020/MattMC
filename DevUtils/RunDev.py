#!/usr/bin/env python3
"""Run the MattMC client in the development environment."""

from __future__ import annotations

import argparse
import os
import re
import shutil
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


def nvidia_version(path: Path) -> str | None:
    """Read an NVIDIA version without depending on the broken user-space driver."""
    try:
        contents = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return None
    match = re.search(r"\b(\d{3}\.\d+(?:\.\d+)?)\b", contents)
    return match.group(1) if match else None


def check_linux_nvidia_driver() -> None:
    """Fail early when a pending NVIDIA update makes GLX and Vulkan unusable."""
    nvidia_smi = shutil.which("nvidia-smi")
    if nvidia_smi is None or not Path("/proc/driver/nvidia/version").is_file():
        return

    try:
        result = subprocess.run(
            [nvidia_smi],
            capture_output=True,
            text=True,
            timeout=10,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return

    diagnostic = f"{result.stdout}\n{result.stderr}".strip()
    if "driver/library version mismatch" not in diagnostic.lower():
        return

    loaded = nvidia_version(Path("/proc/driver/nvidia/version")) or "unknown"
    installed = "unknown"
    modinfo = shutil.which("modinfo")
    if modinfo is not None:
        try:
            module = subprocess.run(
                [modinfo, "-F", "version", "nvidia"],
                capture_output=True,
                text=True,
                timeout=10,
                check=False,
            )
            if module.returncode == 0 and module.stdout.strip():
                installed = module.stdout.strip()
        except (OSError, subprocess.TimeoutExpired):
            pass

    raise SystemExit(
        "ERROR: The NVIDIA kernel module and user-space driver do not match "
        f"(loaded {loaded}, installed {installed}).\n"
        "OpenGL and Vulkan presentation are unavailable in this state, which causes "
        "the transparent window and X11 BadDrawable failure. Reboot to load the "
        "installed NVIDIA module, then run DevUtils/RunDev.py again."
    )


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

    if platform_name == "linux":
        check_linux_nvidia_driver()

    root = repo_root()
    gradle = gradle_command(root, platform_name)
    environment = os.environ.copy()
    environment.setdefault("MATTMC_RUST_VULKAN_GPU_TIMESTAMPS", "true")
    return subprocess.run(
        [*gradle, "-PmattmcRustProfile=release", "runClient"],
        cwd=root,
        env=environment,
    ).returncode


if __name__ == "__main__":
    raise SystemExit(main())
