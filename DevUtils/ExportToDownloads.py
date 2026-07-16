#!/usr/bin/env python3
"""Build and export the MattMC client distribution to the configured downloads directory."""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path


PROJECT_NAME = "MattMC"


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


def resolve_directory(name: str, platform_name: str | None) -> Path:
    helper = common_platform_dir() / "directory" / "directory_helper.py"
    command = [sys.executable, str(helper), name]
    if platform_name:
        command.extend(["--platform", platform_name])

    result = subprocess.run(
        command,
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if result.returncode != 0:
        message = result.stderr.strip() or result.stdout.strip()
        raise SystemExit(message or f"Failed to resolve directory: {name}")
    return Path(result.stdout.strip()).resolve()


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


def run_checked(command: list[str], *, cwd: Path, capture: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        cwd=cwd,
        check=True,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
    )


def read_project_version(root: Path, gradle: list[str]) -> str:
    result = run_checked([*gradle, "properties", "-q"], cwd=root, capture=True)
    for line in result.stdout.splitlines():
        if line.startswith("version:"):
            return line.split(":", 1)[1].strip()
    return ""


def find_distribution_zip(root: Path, version: str) -> Path:
    distributions_dir = root / "build" / "distributions"
    candidates: list[Path] = []

    if version:
        candidates.extend(sorted(distributions_dir.glob(f"MattMC-Client-{version}*.zip")))

    if not candidates:
        candidates.extend(sorted(distributions_dir.glob("MattMC-Client-*.zip")))

    existing = [path for path in candidates if path.is_file()]
    if not existing:
        raise SystemExit("ERROR: Could not find the built zip file!")

    return max(existing, key=lambda path: path.stat().st_mtime)


def safe_extract(zip_path: Path, destination: Path) -> None:
    destination = destination.resolve()
    with zipfile.ZipFile(zip_path) as archive:
        for member in archive.infolist():
            target = (destination / member.filename).resolve()
            if destination != target and destination not in target.parents:
                raise SystemExit(f"ERROR: Refusing to extract unsafe archive path: {member.filename}")
        archive.extractall(destination)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build and export MattMC client distribution.")
    parser.add_argument(
        "--platform",
        help="platform to pass to shared helpers: linux, windows, or macos",
    )
    parser.add_argument(
        "--downloads-dir",
        type=Path,
        default=None,
        help="override downloads directory; otherwise uses the shared directory helper",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    platform_info, normalize_platform = load_platform_detection()
    platform_name = normalize_platform(args.platform) if args.platform else platform_info.platform

    root = repo_root()
    gradle = gradle_command(root, platform_name)
    downloads_override = args.downloads_dir
    if downloads_override is None and os.getenv("DOWNLOADS_DIR"):
        downloads_override = Path(os.environ["DOWNLOADS_DIR"])
    downloads_dir = (
        downloads_override.expanduser().resolve()
        if downloads_override is not None
        else resolve_directory("downloads", platform_name)
    )

    print("=========================================")
    print("  MattMC Client Export Script")
    print("=========================================")
    print("")

    print("[1/5] Building client distribution...")
    run_checked([*gradle, "clean", "clientDistZip", "-PmattmcRustProfile=release", "--no-daemon"], cwd=root)

    version = read_project_version(root, gradle)
    zip_file = find_distribution_zip(root, version)
    print(f"[2/5] Found distribution: {zip_file}")

    print("[3/5] Ensuring Downloads directory exists...")
    downloads_dir.mkdir(parents=True, exist_ok=True)

    print(f"[4/5] Copying to {downloads_dir}...")
    copied_zip = downloads_dir / zip_file.name
    shutil.copy2(zip_file, copied_zip)

    print(f"[5/5] Extracting in {downloads_dir}...")
    extract_root = downloads_dir / PROJECT_NAME
    if extract_root.exists():
        shutil.rmtree(extract_root)
    safe_extract(copied_zip, downloads_dir)

    print("")
    print("=========================================")
    print("  Export Complete!")
    print("=========================================")
    print("")
    print("Your game has been exported to:")
    print(f"  {downloads_dir / PROJECT_NAME}")
    print("")
    print("To run the client:")
    print(f"  cd {downloads_dir / PROJECT_NAME}")
    print("  run-mattmc.bat" if platform_name == "windows" else "  ./run-mattmc.sh")
    print("")
    print("To run the server:")
    print(f"  cd {downloads_dir / PROJECT_NAME / 'server'}")
    print("  run-server.bat" if platform_name == "windows" else "  ./run-server.sh")
    print("")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
