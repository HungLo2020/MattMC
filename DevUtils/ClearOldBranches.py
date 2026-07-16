#!/usr/bin/env python3
"""Delete local branches that no longer exist on the remote by short name."""

from __future__ import annotations

import argparse
import subprocess
import sys


PROTECTED_BRANCHES = {"main", "master", "develop"}


def run_git(args: list[str], *, check: bool = True, capture: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        check=check,
        text=True,
        stdout=subprocess.PIPE if capture else subprocess.DEVNULL,
        stderr=subprocess.PIPE if capture else None,
    )


def git_lines(args: list[str]) -> list[str]:
    result = run_git(args, capture=True)
    return [line for line in result.stdout.splitlines() if line]


def current_branch() -> str:
    result = run_git(["symbolic-ref", "--quiet", "--short", "HEAD"], check=False, capture=True)
    if result.returncode != 0:
        return ""
    return result.stdout.strip()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Delete local branches that are not present on the selected remote.",
    )
    parser.add_argument(
        "-r",
        "--remote",
        default="origin",
        help="remote to compare against, default: origin",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    remote = args.remote

    repo_check = run_git(["rev-parse", "--git-dir"], check=False, capture=True)
    if repo_check.returncode != 0:
        print("Not a git repo.", file=sys.stderr)
        return 2

    run_git(["fetch", remote, "--prune", "--prune-tags"])

    remote_branches = set()
    for branch in git_lines(["for-each-ref", "--format=%(refname:strip=3)", f"refs/remotes/{remote}"]):
        if branch.startswith("HEAD -> "):
            continue
        remote_branches.add(branch)

    active_branch = current_branch()
    protected = set(PROTECTED_BRANCHES)
    if active_branch:
        protected.add(active_branch)

    local_branches = git_lines(["for-each-ref", "--format=%(refname:short)", "refs/heads"])
    to_delete = [
        branch
        for branch in local_branches
        if branch not in protected and branch not in remote_branches
    ]

    if not to_delete:
        print(f"No local-only branches to delete relative to '{remote}'.")
        return 0

    print(f"Deleting local branches not present on '{remote}':")
    for branch in to_delete:
        print(f"  {branch}")

    for branch in to_delete:
        if active_branch and branch == active_branch:
            print(f"Skipping current branch: {branch}")
            continue

        result = run_git(["branch", "-D", branch], check=False)
        if result.returncode == 0:
            print(f"Deleted {branch}")
        else:
            print(f"Failed to delete {branch}")

    print("Done.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
