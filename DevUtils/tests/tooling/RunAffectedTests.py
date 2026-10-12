#!/usr/bin/env python3
"""Run only the Java and Rust tests for the packages and modules a change touches.

Changed files come from `git diff --name-only BASE` plus untracked files
(default BASE: HEAD, so the uncommitted working tree). They map to:

- Java: a changed `src/main/java/<pkg>/X.java` selects `<pkg>.*`; a changed
  test class selects itself. Only packages with tests are kept.
- Rust: a changed `src/main/rust/<path>.rs` selects its module path as a
  `cargo test` filter (`mod.rs`/`tests.rs` select their directory's module).
  A Rust module whose path mirrors a Java test package (for example
  `world/level/chunk` and `net.minecraft.world.level.chunk`) also selects
  that package, since its Java tests cross the FFI.
- `lib.rs`, `Cargo.toml`, `Cargo.lock` or `build.rs` select the whole Rust
  suite; Gradle build files select the whole ordinary Java suite.

Java runs `./gradlew test --tests ...`: filtered runs include parity-tagged
classes, skip slow-tagged cases (unless --include-slow) and skip the
automatic full serial Rust suite. This is an iteration check, not a full
verification: callers outside the selected packages are not run. Use
`./gradlew check` before publishing a cross-cutting change.

    python3 DevUtils/tests/tooling/RunAffectedTests.py --dry-run
    python3 DevUtils/tests/tooling/RunAffectedTests.py --base origin/master
    python3 DevUtils/tests/tooling/RunAffectedTests.py --rust-only
    python3 DevUtils/tests/tooling/RunAffectedTests.py --files src/main/rust/world/level/chunk/reclaim.rs
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import shlex
import subprocess
import sys
import time

REPO = Path(__file__).resolve().parents[3]
JAVA_MAIN, JAVA_TEST, RUST = "src/main/java/", "src/test/java/", "src/main/rust/"
RUST_WHOLE = {"lib.rs", "Cargo.toml", "Cargo.lock", "build.rs"}
GRADLE_FILES = {"build.gradle", "settings.gradle", "gradle.properties"}
JAVA_TEST_ROOTS = ("net/minecraft", "net/vulkanic", "net/sodium", "net/fabricmc", "com/seibel/distanthorizons")


def changed_files(base: str) -> list[str]:
    def git(*args: str) -> list[str]:
        return subprocess.run(["git", *args], cwd=REPO, check=True, capture_output=True, text=True).stdout.split()
    return sorted(set(git("diff", "--name-only", base)) | set(git("ls-files", "--others", "--exclude-standard")))


def has_tests(package: str) -> bool:
    directory = REPO / JAVA_TEST / package.replace(".", "/")
    return directory.is_dir() and any(directory.glob("*.java"))


def rust_module(path: str) -> str | None:
    """Module filter for a crate-relative .rs path; None selects the whole suite."""
    parts = path[:-3].split("/")
    if parts[-1] in ("mod", "tests"):
        parts = parts[:-1]
    return "::".join(parts) if parts and parts != ["lib"] else None


def select(files: list[str]) -> tuple[set[str], set[str], bool, bool]:
    java: set[str] = set()
    rust: set[str] = set()
    whole_rust = whole_java = False
    for path in files:
        name = path.rsplit("/", 1)[-1]
        if path in GRADLE_FILES:
            whole_java = True
        elif path.startswith(JAVA_MAIN) and path.endswith(".java"):
            package = path[len(JAVA_MAIN):].rsplit("/", 1)[0].replace("/", ".")
            if has_tests(package):
                java.add(package + ".*")
        elif path.startswith(JAVA_TEST) and path.endswith(".java"):
            java.add(path[len(JAVA_TEST):-5].replace("/", "."))
        elif path.startswith(RUST):
            relative = path[len(RUST):]
            if relative in RUST_WHOLE:
                whole_rust = True
            elif relative.endswith(".rs"):
                module = rust_module(relative)
                if module is None:
                    whole_rust = True
                    continue
                rust.add(module)
                # Java tests of the mirrored package exercise this module across the FFI.
                directory = relative.rsplit("/", 1)[0] if "/" in relative else ""
                for root in JAVA_TEST_ROOTS:
                    package = f"{root}/{directory}".replace("/", ".")
                    if directory and has_tests(package):
                        java.add(package + ".*")
    # A package pattern already covers its own classes.
    packages = {pattern[:-2] for pattern in java if pattern.endswith(".*")}
    java = {p for p in java if p.endswith(".*") or p.rsplit(".", 1)[0] not in packages}
    # A parent module filter already matches its children.
    rust = {m for m in rust if not any(m != o and m.startswith(o + "::") for o in rust)}
    return java, rust, whole_rust, whole_java


def run(label: str, command: list[str], cwd: Path, env: dict[str, str]) -> tuple[int, float]:
    print(f"\n== {label}: {shlex.join(command)}", flush=True)
    started = time.monotonic()
    code = subprocess.run(command, cwd=cwd, env=env).returncode
    return code, time.monotonic() - started


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base", default="HEAD", help="git ref to diff against (default HEAD: uncommitted changes)")
    parser.add_argument("--files", nargs="+", metavar="PATH", help="select from these repository paths instead of git")
    parser.add_argument("--dry-run", action="store_true", help="print the selection and commands only")
    side = parser.add_mutually_exclusive_group()
    side.add_argument("--java-only", action="store_true")
    side.add_argument("--rust-only", action="store_true")
    parser.add_argument("--include-slow", action="store_true", help="also run @Tag(\"slow\") cases in the selected Java packages")
    parser.add_argument("--rust-threads", type=int, default=4, help="cargo test threads (default 4)")
    parser.add_argument("--rust-profile", default="release", help="native library profile for Gradle (default release)")
    args = parser.parse_args()

    files = sorted(set(args.files)) if args.files else changed_files(args.base)
    java, rust, whole_rust, whole_java = select(files)
    print(f"{len(files)} changed files" + ("" if args.files else f" against {args.base}"))
    print("Java:", "whole ordinary suite" if whole_java else (", ".join(sorted(java)) or "none"))
    print("Rust:", "whole suite" if whole_rust else (", ".join(sorted(rust)) or "none"))

    gradle = "gradlew.bat" if os.name == "nt" else "./gradlew"
    commands = []
    env = dict(os.environ, ALSOFT_DRIVERS="null")
    if not args.java_only and (whole_rust or rust):
        filters = [] if whole_rust else sorted(rust)
        commands.append(("Rust", ["cargo", "test", "--locked", "--profile", "suite", "--lib", "--", *filters,
                                  f"--test-threads={args.rust_threads}"], REPO / RUST))
    if not args.rust_only and (whole_java or java):
        command = [gradle, f"-PmattmcRustProfile={args.rust_profile}", "test"]
        if whole_java:
            command += ["-x", "testRustNative"]
        else:
            for pattern in sorted(java):
                command += ["--tests", pattern]
        if args.include_slow:
            command.append("-PmattmcSlowTests=true")
        commands.append(("Java", command, REPO))
    if not commands:
        print("Nothing to test.")
        return 0
    if args.dry_run:
        for label, command, cwd in commands:
            print(f"{label} (in {cwd.relative_to(REPO) or '.'}): {shlex.join(command)}")
        return 0
    results = [(label, *run(label, command, cwd, env)) for label, command, cwd in commands]
    print()
    for label, code, seconds in results:
        print(f"{label}: {'passed' if code == 0 else f'FAILED (exit {code})'} in {seconds / 60:.1f} min")
    return next((code for _, code, _ in results if code), 0)


if __name__ == "__main__":
    sys.exit(main())
