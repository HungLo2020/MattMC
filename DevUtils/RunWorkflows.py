#!/usr/bin/env python3
"""Trigger GitHub Actions workflows for MattMC."""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path


DEFAULT_WORKFLOW_NAME = "Release Latest"


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


def executable_name(base_name: str, platform_name: str) -> str:
    return f"{base_name}.exe" if platform_name == "windows" else base_name


def require_command(name: str, message: str) -> str:
    path = shutil.which(name)
    if path is None:
        raise SystemExit(message)
    return path


def run_capture(command: list[str], *, cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        cwd=cwd,
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )


def current_git_branch(root: Path, git: str) -> str:
    result = run_capture([git, "rev-parse", "--abbrev-ref", "HEAD"], cwd=root)
    if result.returncode != 0:
        message = result.stderr.strip() or result.stdout.strip()
        raise SystemExit(message or "ERROR: Could not determine current git branch.")
    return result.stdout.strip()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Trigger a GitHub Actions workflow with GitHub CLI.")
    parser.add_argument(
        "workflow_name",
        nargs="?",
        default=DEFAULT_WORKFLOW_NAME,
        help=f"workflow name to run; default: {DEFAULT_WORKFLOW_NAME}",
    )
    parser.add_argument(
        "--ref",
        default=os.getenv("WORKFLOW_REF"),
        help="git ref to dispatch; default: WORKFLOW_REF or current branch",
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

    gh = require_command(executable_name("gh", platform_name), "ERROR: GitHub CLI (gh) is not installed.")
    git = require_command(executable_name("git", platform_name), "ERROR: Git is not installed.")
    workflow_ref = args.ref or current_git_branch(root, git)

    auth_result = subprocess.run([gh, "auth", "status"], cwd=root, check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if auth_result.returncode != 0:
        raise SystemExit("ERROR: gh is not authenticated. Run: gh auth login")

    print("=========================================")
    print("  Run GitHub Workflow")
    print("=========================================")
    print(f"Workflow: {args.workflow_name}")
    print(f"Ref:      {workflow_ref}")
    print("")

    subprocess.run([gh, "workflow", "run", args.workflow_name, "--ref", workflow_ref], cwd=root, check=True)

    print("Workflow dispatch submitted.")
    print("")
    print("To watch progress:")
    print(f'  gh run list --workflow "{args.workflow_name}" --limit 5')
    print("  gh run watch")
    print("")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
