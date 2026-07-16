#!/usr/bin/env python3
"""Build, serve, or set up the MattMC wiki/docs environment."""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path


def script_dir() -> Path:
    return Path(__file__).resolve().parent


def repo_root() -> Path:
    return script_dir().parent


def common_platform_dir() -> Path:
    return script_dir() / "Common" / "platform"


def load_platform_detection():
    detection_dir = common_platform_dir() / "detection"
    sys.path.insert(0, str(detection_dir))
    from platform_detection import detect_platform_info, normalize_platform

    return detect_platform_info(), normalize_platform


def venv_python(root: Path, platform_name: str) -> Path:
    venv = root / ".venv-wiki"
    if platform_name == "windows":
        return venv / "Scripts" / "python.exe"
    return venv / "bin" / "python"


def system_python(platform_name: str) -> str:
    if os.getenv("PYTHON"):
        return os.environ["PYTHON"]
    return "python" if platform_name == "windows" else "python3"


def run_checked(command: list[str], *, cwd: Path) -> None:
    subprocess.run(command, cwd=cwd, check=True)


def install_wiki_environment(root: Path, platform_name: str) -> None:
    venv = root / ".venv-wiki"
    python = system_python(platform_name)
    venv_py = venv_python(root, platform_name)

    print(f"Preparing wiki environment at {venv}")
    run_checked([python, "-m", "venv", str(venv)], cwd=root)
    run_checked([str(venv_py), "-m", "pip", "install", "--upgrade", "pip"], cwd=root)
    run_checked([str(venv_py), "-m", "pip", "install", "-r", str(root / "requirements-docs.txt")], cwd=root)


def ensure_wiki_environment(root: Path, platform_name: str) -> None:
    venv_py = venv_python(root, platform_name)
    if not venv_py.is_file():
        install_wiki_environment(root, platform_name)
        return

    result = subprocess.run(
        [str(venv_py), "-m", "mkdocs", "--version"],
        cwd=root,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    if result.returncode != 0:
        install_wiki_environment(root, platform_name)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build, serve, or set up the MattMC wiki/docs environment.")
    parser.add_argument(
        "command",
        nargs="?",
        default="serve",
        choices=("serve", "build", "setup"),
        help="command to run; default: serve",
    )
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
    venv_py = venv_python(root, platform_name)

    if args.command == "setup":
        install_wiki_environment(root, platform_name)
        print("Wiki environment is ready.")
    elif args.command == "serve":
        ensure_wiki_environment(root, platform_name)
        run_checked([str(venv_py), "-m", "mkdocs", "serve"], cwd=root)
    elif args.command == "build":
        ensure_wiki_environment(root, platform_name)
        run_checked([str(venv_py), "-m", "mkdocs", "build", "--strict"], cwd=root)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
