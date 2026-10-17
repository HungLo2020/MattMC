#!/usr/bin/env python3
"""Provision the Frozen Java baseline checkout used by the comparison tools.

Frozen is a separate full clone of this repository on the ``JavaPerfTesting``
branch. The graphics harness (Capture.py, Gameplay.py, Matrix.py) and the
PerfAudit tools find it through ``java_perf_repo`` in
DevUtils/Common/platform/directory/directories.json (or ``--frozen-repo``).

Frozen keeps evolving for new diagnostics, so this tracks the branch's latest
commit rather than a pin:

* no checkout yet: clone the branch from this repository's ``origin``;
* an existing clone: fetch, then fast-forward to the latest commit only when
  it is on the branch, has no local changes and has not diverged. Anything
  else is reported and left untouched;
* then validate it as the tools do and, unless ``--no-build``, let the
  branch's own Gradle tasks fetch its bundled JDK and build it.

``--check`` only reports; ``--dry-run`` prints what would run. Game inputs
under run/ (gitignored) are copied only with ``--copy-run-inputs`` and never
overwrite existing files.
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

BRANCH = "JavaPerfTesting"
DIRECTORY_NAME = "java_perf_repo"
SHADER_PACK = "ComplementaryHungLoIfied.zip"
# Gradle tasks that fetch Frozen's bundled JDK and build what runClient and the
# Frozen*Probe classpaths need; tests are not run.
PREPARE_TASKS = ["copyJdkToRun", "jar", "shaderPackZip", "testClasses"]


def script_dir() -> Path:
    return Path(__file__).resolve().parent


def repo_root() -> Path:
    return script_dir().parent


def common_platform_dir() -> Path:
    return script_dir() / "Common" / "platform"


def load_platform_detection():
    sys.path.insert(0, str(common_platform_dir() / "detection"))
    from platform_detection import detect_platform_info

    return detect_platform_info()


def resolve_directory(name: str, platform_name: str) -> Path:
    helper = common_platform_dir() / "directory" / "directory_helper.py"
    result = subprocess.run(
        [sys.executable, str(helper), name, "--platform", platform_name],
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        cwd=repo_root(),
    )
    if result.returncode != 0:
        message = result.stderr.strip() or result.stdout.strip()
        raise SystemExit(message or f"Failed to resolve directory: {name}")
    return Path(result.stdout.strip()).resolve()


def git(args: list[str], cwd: Path, *, check: bool = True) -> str:
    result = subprocess.run(["git", *args], cwd=cwd, check=False, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if check and result.returncode != 0:
        raise SystemExit(f"git {' '.join(args)} failed in {cwd}:\n{result.stderr.strip() or result.stdout.strip()}")
    return result.stdout.strip()


def normalize_remote(url: str) -> str:
    url = url.strip().rstrip("/")
    return url[:-4] if url.endswith(".git") else url


class Step:
    """Runs or, with --dry-run, prints the commands that change something."""

    def __init__(self, dry_run: bool) -> None:
        self.dry_run = dry_run

    def run(self, command: list[str], cwd: Path) -> None:
        print(f"$ {' '.join(command)}    (in {cwd})", flush=True)
        if self.dry_run:
            return
        result = subprocess.run(command, cwd=cwd, check=False)
        if result.returncode != 0:
            raise SystemExit(f"Command failed with exit code {result.returncode}: {' '.join(command)}")


def clone(dest: Path, remote: str, branch: str, step: Step) -> None:
    command = ["git", "clone", "--branch", branch, "--single-branch"]
    # Borrow objects this checkout already has, then copy them in: a full,
    # independent clone (the tools reject worktrees, whose .git is a file).
    current_git = repo_root() / ".git"
    if current_git.is_dir():
        command += ["--reference-if-able", str(current_git), "--dissociate"]
    command += [remote, str(dest)]
    if not step.dry_run:
        dest.parent.mkdir(parents=True, exist_ok=True)
    step.run(command, dest.parent)


def local_changes(dest: Path) -> list[str]:
    # Tracked edits and untracked files that are not ignored (run/ and build/ are ignored).
    status = git(["status", "--porcelain"], dest)
    return [line for line in status.splitlines() if line.strip()]


def update(dest: Path, remote: str, branch: str, step: Step, check_only: bool) -> list[str]:
    """Brings an existing clone to the branch's latest commit. Returns problems
    that leave it as it was."""
    problems: list[str] = []
    if not (dest / ".git").is_dir():
        kind = "a file (a git worktree)" if (dest / ".git").exists() else "missing"
        return [f"{dest} is not a full clone: .git is {kind}. The comparison tools require a .git directory."]
    origin = git(["remote", "get-url", "origin"], dest, check=False)
    if normalize_remote(origin) != normalize_remote(remote):
        return [f"origin is {origin or '(none)'}, expected {remote}"]
    current_branch = git(["branch", "--show-current"], dest, check=False)
    if current_branch != branch:
        return [f"checked out {current_branch or '(detached HEAD)'}, expected branch {branch}"]
    # Fetching only moves origin/<branch>; the checkout itself is unchanged, so
    # --check fetches too (to compare with the latest commit). --dry-run does not.
    if step.dry_run and not check_only:
        step.run(["git", "fetch", "origin", branch], dest)
    else:
        git(["fetch", "--quiet", "origin", branch], dest)
    upstream = f"origin/{branch}"
    behind = int(git(["rev-list", "--count", f"HEAD..{upstream}"], dest) or 0)
    ahead = int(git(["rev-list", "--count", f"{upstream}..HEAD"], dest) or 0)
    changes = local_changes(dest)
    if ahead:
        problems.append(f"{ahead} local commit(s) not on {upstream}; refusing to discard or merge them")
    if changes:
        problems.append("local changes (left untouched):\n    " + "\n    ".join(changes[:20]))
    if problems or behind == 0:
        if behind:
            problems.append(f"{behind} commit(s) behind {upstream}")
        return problems
    if check_only:
        return [f"{behind} commit(s) behind {upstream}; run without --check to update"]
    before = git(["rev-parse", "--short", "HEAD"], dest)
    step.run(["git", "merge", "--ff-only", "--quiet", upstream], dest)
    if not step.dry_run:
        print(f"  updated {before}..{git(['rev-parse', '--short', 'HEAD'], dest)} ({behind} commit(s))")
    return []


def validate(dest: Path, current: Path) -> list[str]:
    """The checks the comparison tools make, plus what their Frozen runs use."""
    problems = []
    if dest == current:
        problems.append("Frozen must be a separate checkout from the current repository")
    if not dest.is_dir():
        return problems + [f"{dest} does not exist"]
    if not ((dest / "gradlew").is_file() or (dest / "gradlew.bat").is_file()):
        problems.append("missing the Gradle wrapper")
    if not (dest / ".git").exists():
        problems.append("missing a .git directory")
    if not ((dest / "DevUtils/Common/capture_runner.py").is_file() or (dest / "DevUtils/Common/capture_runner.sh").is_file()):
        problems.append("missing DevUtils/Common/capture_runner.(py|sh), which graphics captures launch")
    build = dest / "build.gradle"
    text = build.read_text(encoding="utf-8", errors="replace") if build.is_file() else ""
    for needle, use in (("'runClient'", "captures and meshing replays"), ("mattmcRunGameDir", "per-run game directories")):
        if needle not in text:
            problems.append(f"build.gradle lacks {needle} ({use})")
    return problems


def gradle(dest: Path, platform_name: str) -> list[str]:
    if platform_name == "windows":
        return [str(dest / "gradlew.bat")]
    return [str(dest / "gradlew")]


def copy_run_inputs(dest: Path, source: Path, worlds: list[str], step: Step) -> None:
    """Copies gitignored game inputs into Frozen's run/; existing files win."""
    pairs = [(source / "options.txt", dest / "run/options.txt"),
             (source / "shaderpacks" / SHADER_PACK, dest / "run/shaderpacks" / SHADER_PACK)]
    pairs += [(source / "saves" / world, dest / "run/saves" / world) for world in worlds]
    for src, dst in pairs:
        if dst.exists():
            print(f"  keep existing {dst}")
            continue
        if not src.exists():
            print(f"  WARNING: {src} does not exist; not copied")
            continue
        print(f"  copy {src} -> {dst}")
        if step.dry_run:
            continue
        dst.parent.mkdir(parents=True, exist_ok=True)
        if src.is_dir():
            shutil.copytree(src, dst)
        else:
            shutil.copy2(src, dst)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--dest", help=f"checkout path (default: {DIRECTORY_NAME} in directories.json for this platform)")
    parser.add_argument("--remote", help="clone URL (default: this repository's origin)")
    parser.add_argument("--branch", default=BRANCH, help=f"branch to track (default: {BRANCH})")
    parser.add_argument("--check", action="store_true", help="only report whether the checkout is valid and up to date")
    parser.add_argument("--dry-run", action="store_true", help="print the commands that would change anything")
    parser.add_argument("--no-build", action="store_true", help="skip fetching the bundled JDK and building with Gradle")
    parser.add_argument("--copy-run-inputs", action="store_true",
                        help=f"copy run/options.txt and run/shaderpacks/{SHADER_PACK} (and --world saves) into Frozen's run/ when absent")
    parser.add_argument("--run-source", help="run/ directory to copy inputs from (default: this repository's run/)")
    parser.add_argument("--world", action="append", default=[], help="a run/saves world to copy with --copy-run-inputs (repeatable)")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    platform_name = load_platform_detection().platform
    current = repo_root()
    configured = None
    try:
        configured = resolve_directory(DIRECTORY_NAME, platform_name)
    except SystemExit as error:
        if not args.dest:
            raise SystemExit(f"{error}\nPass --dest for this platform.")
    dest = Path(args.dest).expanduser().resolve() if args.dest else configured
    remote = args.remote or git(["remote", "get-url", "origin"], current)
    step = Step(args.dry_run)
    print(f"Frozen baseline: {dest}\n  branch {args.branch} from {remote}", flush=True)
    if dest == current:
        raise SystemExit("Frozen must be a separate checkout from the current repository")

    problems: list[str] = []
    fresh = not dest.exists() or (dest.is_dir() and not any(dest.iterdir()))
    if fresh:
        if args.check:
            problems.append(f"{dest} does not exist; run without --check to clone it")
        else:
            clone(dest, remote, args.branch, step)
    else:
        problems += update(dest, remote, args.branch, step, args.check)

    if not args.dry_run and dest.exists():
        problems += validate(dest, current)

    if not args.check and not problems and not args.no_build:
        print("Preparing Frozen's bundled JDK and build (its own Gradle tasks):", flush=True)
        step.run([*gradle(dest, platform_name), "--no-daemon", "-x", "test", *PREPARE_TASKS], dest)

    if args.copy_run_inputs and not args.check and not problems:
        source = Path(args.run_source).expanduser().resolve() if args.run_source else current / "run"
        print(f"Run inputs from {source}:", flush=True)
        copy_run_inputs(dest, source, args.world, step)

    if dest.exists() and (dest / ".git").is_dir() and not args.dry_run:
        print(f"  commit {git(['log', '-1', '--format=%H %cd %s', '--date=short'], dest, check=False)}")
    if configured is not None and dest != configured:
        print(f"NOTE: {dest} is not the configured {DIRECTORY_NAME} ({configured}); pass --frozen-repo {dest} to the tools.")
    if problems:
        print("Frozen baseline is not ready:", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1
    print("Frozen baseline is ready." if not args.dry_run else "Dry run complete; nothing changed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
