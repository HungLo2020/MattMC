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


def frozen_repo(root: Path, platform_name: str, explicit: str | None) -> Path:
    if explicit:
        target = Path(explicit).expanduser().resolve()
    else:
        helper = root / "DevUtils" / "Common" / "platform" / "directory" / "directory_helper.py"
        result = subprocess.run(
            [sys.executable, str(helper), "java_perf_repo", "--platform", platform_name],
            cwd=root,
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode != 0:
            raise SystemExit(result.stderr.strip() or "ERROR: Could not resolve java_perf_repo.")
        target = Path(result.stdout.strip()).resolve()

    if target == root:
        raise SystemExit("ERROR: Frozen must be a separate checkout from the current repository.")
    if not target.is_dir() or not (target / ".git").is_dir():
        raise SystemExit(
            f"ERROR: Frozen checkout is missing or is not a full clone: {target}\n"
            "Run python3 DevUtils/ProvisionFrozenBaseline.py to prepare it, "
            "or pass --frozen-repo PATH."
        )
    return target


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
    parser.add_argument(
        "--frozen",
        action="store_true",
        help="launch the configured Frozen Java checkout, skipping its launch-blocking tests",
    )
    parser.add_argument(
        "--frozen-repo",
        help="launch Frozen from this path instead of the configured java_perf_repo",
    )
    parser.add_argument(
        "--record",
        action="store_true",
        help="record a hand-played Current session (per-frame timings, JFR, GPU/CPU samples) "
             "under artifacts/recordings/ and summarize it on exit",
    )
    parser.add_argument(
        "--record-label",
        help="short label appended to the recording directory name",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    platform_info, normalize_platform = load_platform_detection()
    platform_name = normalize_platform(args.platform) if args.platform else platform_info.platform

    if platform_name == "linux":
        check_linux_nvidia_driver()

    root = repo_root()
    use_frozen = args.frozen or args.frozen_repo is not None
    if use_frozen:
        root = frozen_repo(root, platform_name, args.frozen_repo)
    gradle = gradle_command(root, platform_name)
    environment = os.environ.copy()
    if use_frozen:
        command = [*gradle, "runClient", "-x", "test"]
        print(f"Launching Frozen Java from {root} (tests skipped)", flush=True)
    else:
        environment.setdefault("MATTMC_RUST_VULKAN_GPU_TIMESTAMPS", "true")
        command = [*gradle, "-PmattmcRustProfile=release", "runClient"]
    if args.record:
        if use_frozen:
            raise SystemExit("ERROR: --record records Current only; Frozen has no per-frame recorder.")
        return run_recorded(root, command, environment, args.record_label)
    return subprocess.run(
        command,
        cwd=root,
        env=environment,
    ).returncode


def run_recorded(root: Path, command: list[str], environment: dict[str, str], label: str | None) -> int:
    sys.path.insert(0, str(script_dir() / "PerfAudit"))
    import Recording

    directory = Recording.new_recording_dir(root, label)
    print(f"Recording this session to {directory}", flush=True)
    samplers = Recording.Samplers(directory)
    stalls = Recording.StallSampler(directory)
    samplers.start()
    stalls.start()
    try:
        code = Recording.run_with_console(
            [*command, f"-PmattmcRecordDir={directory}"], root, environment, directory / "console.log")
    finally:
        samplers.stop()
        stalls.stop()
    print(Recording.summarize(directory), end="", flush=True)
    print(f"Recording saved to {directory}", flush=True)
    return code


if __name__ == "__main__":
    raise SystemExit(main())
