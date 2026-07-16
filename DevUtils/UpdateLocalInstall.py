#!/usr/bin/env python3
"""Build MattMC and update the local exported install."""

from __future__ import annotations

import argparse
import os
import shutil
import stat
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


def resolve_directory(name: str, platform_name: str) -> Path:
    helper = common_platform_dir() / "directory" / "directory_helper.py"
    result = subprocess.run(
        [sys.executable, str(helper), name, "--platform", platform_name],
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


def native_file_name(platform_name: str, arch: str) -> str:
    platform_parts = {
        "linux": ("linux", "so"),
        "macos": ("mac", "dylib"),
        "windows": ("win", "dll"),
    }
    arch_parts = {
        "x86_64": "x64",
        "x86": "x86",
        "arm64": "aarch64",
        "arm": "arm",
    }

    if platform_name not in platform_parts:
        raise SystemExit(f"ERROR: Unsupported OS for Rust native library: {platform_name}")
    if arch not in arch_parts:
        raise SystemExit(f"ERROR: Unsupported architecture for Rust native library: {arch}")

    os_part, extension = platform_parts[platform_name]
    arch_part = arch_parts[arch]
    return f"mattmc_rust-{os_part}-{arch_part}.{extension}"


def latest_built_jar(root: Path) -> Path:
    libs_dir = root / "build" / "libs"
    jars = [
        path
        for path in libs_dir.glob("MattMC*.jar")
        if path.is_file()
        and not path.name.endswith("-sources.jar")
        and not path.name.endswith("-javadoc.jar")
    ]
    if not jars:
        raise SystemExit(f"ERROR: Could not find built jar in {libs_dir}.")
    return max(jars, key=lambda path: path.stat().st_mtime)


def project_version(root: Path, gradle: list[str]) -> str:
    result = run_checked([*gradle, "properties", "-q", "--no-daemon"], cwd=root, capture=True)
    for line in result.stdout.splitlines():
        if line.startswith("version:"):
            return line.split(":", 1)[1].strip()
    raise SystemExit("ERROR: Could not determine project version.")


def copy_template(source: Path, destination: Path, version: str, *, executable: bool = False) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    content = source.read_text(encoding="utf-8").replace("@VERSION@", version)
    destination.write_text(content, encoding="utf-8", newline="")
    if executable:
        destination.chmod(destination.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)


def refresh_packaging_scripts(packaging_dir: Path, install_root: Path, server_dir: Path, version: str) -> None:
    for script_file in sorted(path for path in packaging_dir.iterdir() if path.is_file()):
        file_name = script_file.name
        if file_name == "run-server.sh":
            copy_template(script_file, server_dir / file_name, version, executable=True)
        elif file_name == "run-server.bat":
            copy_template(script_file, server_dir / file_name, version)
        elif file_name == "SERVER-README.md":
            shutil.copy2(script_file, server_dir / "README.md")
        elif file_name.endswith(".sh"):
            copy_template(script_file, install_root / file_name, version, executable=True)
        elif file_name.endswith((".bat", ".ps1")):
            copy_template(script_file, install_root / file_name, version)
        else:
            shutil.copy2(script_file, install_root / file_name)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build MattMC and update the local exported install.")
    parser.add_argument(
        "--install-root",
        type=Path,
        default=None,
        help="local install root; otherwise uses INSTALL_ROOT or shared local_install directory",
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

    install_root = args.install_root
    if install_root is None and os.getenv("INSTALL_ROOT"):
        install_root = Path(os.environ["INSTALL_ROOT"])
    if install_root is None:
        install_root = resolve_directory("local_install", platform_name)
    else:
        install_root = install_root.expanduser().resolve()

    root = repo_root()
    gradle = gradle_command(root, platform_name)
    lib_dir = install_root / "lib"
    natives_dir = install_root / "natives"
    server_dir = install_root / "server"
    packaging_dir = root / "packaging"
    built_natives_dir = root / "build" / "rust" / "native"

    print("=========================================")
    print("  MattMC Local Install Update")
    print("=========================================")
    print("")

    print("[1/6] Building fresh jar and optimized Rust native...")
    run_checked([*gradle, "clean", "buildRustNative", "jar", "-PmattmcRustProfile=release", "--rerun-tasks", "--no-daemon"], cwd=root)

    print("[2/6] Locating built jar...")
    jar_file = latest_built_jar(root)
    print(f"    Found: {jar_file}")

    print("[3/6] Ensuring local install directories exist...")
    lib_dir.mkdir(parents=True, exist_ok=True)
    natives_dir.mkdir(parents=True, exist_ok=True)
    server_dir.mkdir(parents=True, exist_ok=True)

    print(f"[4/6] Copying jar to {lib_dir}...")
    shutil.copy2(jar_file, lib_dir / jar_file.name)

    print(f"[5/6] Refreshing Rust native in {natives_dir}...")
    if not built_natives_dir.is_dir():
        raise SystemExit(f"ERROR: Rust native output directory does not exist: {built_natives_dir}")

    rust_native_file_name = native_file_name(platform_name, platform_info.arch)
    built_native_file = built_natives_dir / rust_native_file_name
    if not built_native_file.is_file():
        raise SystemExit(f"ERROR: Expected Rust native library was not produced: {built_native_file}")

    destination_native_file = natives_dir / rust_native_file_name
    shutil.copy2(built_native_file, destination_native_file)
    print(f"    Updated: {destination_native_file}")

    version = project_version(root, gradle)

    print("[6/6] Refreshing packaging scripts...")
    refresh_packaging_scripts(packaging_dir, install_root, server_dir, version)

    print("")
    print("=========================================")
    print("  Export Complete!")
    print("=========================================")
    print("")
    print("Updated local install:")
    print(f"  {install_root}")
    print("")
    print("Jar:")
    print(f"  {lib_dir / jar_file.name}")
    print("")
    print("Rust natives:")
    for native in sorted(
        path for path in natives_dir.iterdir()
        if path.is_file() and path.suffix in {".so", ".dll", ".dylib"}
    ):
        print(f"  {native}")
    print("")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
