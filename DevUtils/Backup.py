#!/usr/bin/env python3
"""Create a lean MattMC source backup using shared DevUtils directory lookup."""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
from datetime import datetime
from pathlib import Path


EXCLUDED_DIR_NAMES = {
    ".git",
    ".gradle",
    ".idea",
    ".vscode",
    "build",
    "logs",
    "out",
    "run",
    "site",
}
EXCLUDED_FILE_NAMES = {".DS_Store"}


def script_dir() -> Path:
    return Path(__file__).resolve().parent


def repo_root() -> Path:
    result = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        cwd=script_dir(),
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if result.returncode != 0:
        raise SystemExit("ERROR: Not in a git repository")
    return Path(result.stdout.strip()).resolve()


def resolve_directory(name: str, platform_name: str | None) -> Path:
    helper = script_dir() / "Common" / "platform" / "directory" / "directory_helper.py"
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


def should_skip(path: Path, root: Path, archive_name: str) -> bool:
    rel_path = path.relative_to(root)
    if any(part in EXCLUDED_DIR_NAMES for part in rel_path.parts):
        return True
    if path.name in EXCLUDED_FILE_NAMES:
        return True
    return path.name == archive_name


def iter_backup_files(root: Path, archive_name: str):
    for current_root, dir_names, file_names in os.walk(root):
        current_path = Path(current_root)
        dir_names[:] = [
            dir_name
            for dir_name in dir_names
            if dir_name not in EXCLUDED_DIR_NAMES
        ]

        for file_name in file_names:
            file_path = current_path / file_name
            if file_path.is_symlink() or not file_path.is_file():
                continue
            if not should_skip(file_path, root, archive_name):
                yield file_path


def create_archive(root: Path, archive_path: Path, archive_name: str) -> None:
    with zipfile.ZipFile(archive_path, mode="w", compression=zipfile.ZIP_DEFLATED) as archive:
        for file_path in iter_backup_files(root, archive_name):
            archive.write(file_path, file_path.relative_to(root).as_posix())


def parse_bool(value: str | None, *, default: bool) -> bool:
    if value is None:
        return default
    normalized = value.strip().lower()
    if normalized in {"1", "true", "yes", "y", "on"}:
        return True
    if normalized in {"0", "false", "no", "n", "off"}:
        return False
    raise argparse.ArgumentTypeError(f"Invalid boolean value: {value}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Create and copy a MattMC source backup zip.")
    parser.add_argument(
        "--platform",
        help="platform to pass to the directory helper: linux, windows, or macos",
    )
    parser.add_argument(
        "--copy-to-downloads",
        action="store_true",
        default=False,
        help="also copy the archive to the configured downloads directory",
    )
    args = parser.parse_args()

    try:
        args.copy_to_downloads = args.copy_to_downloads or parse_bool(os.getenv("COPY_TO_DOWNLOADS"), default=False)
    except argparse.ArgumentTypeError as exc:
        parser.error(str(exc))

    return args


def main() -> int:
    args = parse_args()

    root = repo_root()
    repo_name = root.name
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    archive_name = f"{repo_name}-{timestamp}.zip"

    backup_destination = resolve_directory("backup_destination", args.platform)
    backup_destination.mkdir(parents=True, exist_ok=True)

    download_destination = None
    if args.copy_to_downloads:
        download_destination = resolve_directory("downloads", args.platform)
        download_destination.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="mattmc-backup-") as temp_dir:
        archive_path = Path(temp_dir) / archive_name

        print(f"Creating archive: {archive_path}")
        create_archive(root, archive_path, archive_name)
        print("Archive created.")

        backup_path = backup_destination / archive_name
        shutil.copy2(archive_path, backup_path)

        copied_paths = [backup_path]
        if download_destination is not None:
            download_path = download_destination / archive_name
            shutil.copy2(archive_path, download_path)
            copied_paths.append(download_path)

    print("Copied to:")
    for copied_path in copied_paths:
        print(f"  {copied_path}")
    print("Done.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
