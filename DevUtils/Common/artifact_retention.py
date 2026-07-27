#!/usr/bin/env python3
"""Safe retention and disk-budget helpers for generated graphics artifacts."""

from __future__ import annotations

import json
import os
import shutil
import time
import gzip
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


MARKER_NAME = ".mattmc-generated-artifact-root.json"
DEFAULT_RESERVE_MB = 8192
PROFILE_LIMITS = {
    "smoke": {"global_mb": 2048, "run_mb": 384, "keep_success": 1, "keep_failed": 1, "heavy_keep": 0, "estimate_mb": 256},
    "standard": {"global_mb": 8192, "run_mb": 1536, "keep_success": 3, "keep_failed": 1, "heavy_keep": 0, "estimate_mb": 768},
    "extended": {"global_mb": 12288, "run_mb": 2048, "keep_success": 3, "keep_failed": 1, "heavy_keep": 1, "estimate_mb": 1024},
    "diagnostic": {"global_mb": 16384, "run_mb": 4096, "keep_success": 2, "keep_failed": 2, "heavy_keep": 1, "estimate_mb": 2048},
    "preserve": {"global_mb": 0, "run_mb": 0, "keep_success": 999999, "keep_failed": 999999, "heavy_keep": 999999, "estimate_mb": 0},
}
HEAVY_SUFFIXES = {".rdc", ".tracy"}
COPIED_GAME_DIR_PREFIXES = ("game_dir_", "region_validation_game_")
CAPTURE_RUN_ID_PATTERN = re.compile(r"_(20[0-9]{6}_[0-9]{6})(?:[_.]|$)")
COMPRESSIBLE_SUFFIXES = {".log", ".txt", ".csv", ".json"}
NEVER_COMPRESS_NAMES = {
    "graphics_audit_artifact.json",
    "graphics_audit_manifest.json",
    "graphics_audit_matrix.json",
    "graphics_audit_baselines.json",
}
DEFAULT_COMPRESS_THRESHOLD_BYTES = 16 * 1024 * 1024


@dataclass(frozen=True)
class RetentionPolicy:
    profile: str
    root: Path
    global_limit_bytes: int
    run_limit_bytes: int
    reserve_bytes: int
    keep_success: int
    keep_failed: int
    heavy_keep: int
    preserve: bool = False


@dataclass(frozen=True)
class ArtifactRun:
    path: Path
    tool: str
    mode: str
    profile: str
    success: bool
    preserved: bool
    heavy: bool
    mtime: float
    size_bytes: int


def mb(value: int | float) -> int:
    return int(value * 1024 * 1024)


def directory_size(path: Path) -> int:
    if not path.exists():
        return 0
    if path.is_file():
        return path.stat().st_size
    total = 0
    for item in path.rglob("*"):
        try:
            if item.is_file() or item.is_symlink():
                total += item.stat().st_size
        except FileNotFoundError:
            continue
    return total


def artifact_size_excluding_copied_game_dirs(path: Path) -> int:
    if not path.exists():
        return 0
    if path.is_file():
        return path.stat().st_size
    total = 0
    for item in path.rglob("*"):
        try:
            if any(parent.name.startswith(COPIED_GAME_DIR_PREFIXES) for parent in (item, *item.parents)):
                continue
            if item.is_file() or item.is_symlink():
                total += item.stat().st_size
        except FileNotFoundError:
            continue
    return total


def canonical(path: Path) -> Path:
    return path.expanduser().resolve()


def default_artifact_base(repo_root: Path) -> Path:
    configured = os.environ.get("MATTMC_GRAPHICS_ARTIFACT_ROOT")
    if configured:
        path = Path(configured)
        return canonical(path if path.is_absolute() else repo_root / path)
    return canonical(repo_root / "logs" / "graphics-audit")


def ensure_marker(root: Path) -> None:
    root.mkdir(parents=True, exist_ok=True)
    marker = root / MARKER_NAME
    if not marker.exists():
        marker.write_text(
            json.dumps(
                {
                    "schema": "mattmc-generated-artifact-root-v1",
                    "created_at_epoch": int(time.time()),
                    "purpose": "Generated MattMC graphics audit artifacts. Safe for retention-managed cleanup.",
                },
                indent=2,
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
        )


def assert_marked_root(root: Path) -> Path:
    root = canonical(root)
    marker = root / MARKER_NAME
    if not marker.is_file():
        raise RuntimeError(f"refusing artifact cleanup without ownership marker: {marker}")
    return root


def nearest_marked_root(path: Path) -> Path | None:
    current = canonical(path)
    if current.is_file():
        current = current.parent
    while True:
        if (current / MARKER_NAME).is_file():
            return current
        if current.parent == current:
            return None
        current = current.parent


def assert_inside_marked_root(root: Path, candidate: Path) -> Path:
    root = assert_marked_root(root)
    candidate = canonical(candidate)
    if candidate != root and root not in candidate.parents:
        raise RuntimeError(f"refusing to delete outside artifact root: {candidate}")
    return candidate


def policy_for(
    profile: str,
    root: Path,
    *,
    diagnostic: bool = False,
    preserve: bool = False,
    global_limit_mb: int | None = None,
    run_limit_mb: int | None = None,
    reserve_mb: int | None = None,
    keep_success: int | None = None,
    keep_failed: int | None = None,
) -> RetentionPolicy:
    effective_profile = "preserve" if preserve else ("diagnostic" if diagnostic else profile)
    limits = PROFILE_LIMITS.get(effective_profile, PROFILE_LIMITS["smoke"])
    return RetentionPolicy(
        profile=effective_profile,
        root=canonical(root),
        global_limit_bytes=0 if preserve else mb(global_limit_mb if global_limit_mb is not None else limits["global_mb"]),
        run_limit_bytes=0 if preserve else mb(run_limit_mb if run_limit_mb is not None else limits["run_mb"]),
        reserve_bytes=mb(reserve_mb if reserve_mb is not None else DEFAULT_RESERVE_MB),
        keep_success=keep_success if keep_success is not None else int(limits["keep_success"]),
        keep_failed=keep_failed if keep_failed is not None else int(limits["keep_failed"]),
        heavy_keep=int(limits["heavy_keep"]),
        preserve=preserve,
    )


def estimated_run_bytes(profile: str, *, diagnostic: bool = False, renderdoc: bool = False, tracy: bool = False) -> int:
    effective = "diagnostic" if diagnostic or renderdoc or tracy else profile
    estimate = int(PROFILE_LIMITS.get(effective, PROFILE_LIMITS["smoke"])["estimate_mb"])
    if renderdoc:
        estimate += 1024
    if tracy:
        estimate += 512
    return mb(estimate)


def free_bytes(path: Path) -> int:
    target = path if path.exists() else path.parent
    while not target.exists() and target.parent != target:
        target = target.parent
    return shutil.disk_usage(target).free


def preflight_disk_budget(policy: RetentionPolicy, estimated_bytes: int | None = None) -> dict[str, int | str]:
    ensure_marker(policy.root)
    estimated = estimated_bytes if estimated_bytes is not None else estimated_run_bytes(policy.profile)
    root_usage = directory_size(policy.root)
    available = free_bytes(policy.root)
    required = estimated + policy.reserve_bytes
    result: dict[str, int | str] = {
        "artifact_root": str(policy.root),
        "free_bytes": available,
        "estimated_run_bytes": estimated,
        "reserve_bytes": policy.reserve_bytes,
        "artifact_root_usage_bytes": root_usage,
        "global_limit_bytes": policy.global_limit_bytes,
        "run_limit_bytes": policy.run_limit_bytes,
    }
    if available < required:
        raise RuntimeError(
            f"insufficient free disk for graphics audit run: free={available} required={required} "
            f"root={policy.root}"
        )
    if policy.global_limit_bytes and root_usage + estimated > policy.global_limit_bytes:
        raise RuntimeError(
            f"graphics artifact root quota would be exceeded before launch: "
            f"usage={root_usage} estimated={estimated} limit={policy.global_limit_bytes} root={policy.root}"
        )
    return result


def run_size_check(policy: RetentionPolicy, run_root: Path) -> None:
    if policy.preserve or policy.run_limit_bytes <= 0:
        return
    run_root = assert_inside_marked_root(policy.root, run_root)
    size = artifact_size_excluding_copied_game_dirs(run_root)
    if size > policy.run_limit_bytes:
        manifest = run_root / "quota_failure_manifest.json"
        manifest.write_text(
            json.dumps(
                {
                    "schema": "mattmc-graphics-quota-failure-v1",
                    "run_root": str(run_root),
                    "size_bytes": size,
                    "limit_bytes": policy.run_limit_bytes,
                    "created_at_epoch": int(time.time()),
                },
                indent=2,
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
        )
        raise RuntimeError(f"graphics artifact run exceeded quota: {size} > {policy.run_limit_bytes} ({run_root})")


def _read_json(path: Path) -> dict[str, object]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {}


def _tool_mode_profile_for(path: Path, root: Path) -> tuple[str, str, str]:
    rel = path.relative_to(root)
    parts = rel.parts
    if len(parts) >= 4 and parts[0] in {"capture", "gameplay", "subsystem", "matrix"}:
        tool = parts[0]
        mode = parts[1] if len(parts) > 1 else "unknown"
    elif len(parts) >= 3:
        tool = parts[2]
        mode = parts[1]
    else:
        tool = "legacy"
        mode = "default"
    profile = "unknown"
    for manifest_name in ("graphics_audit_manifest.json", "graphics_audit_artifact.json"):
        for candidate in path.rglob(manifest_name):
            data = _read_json(candidate)
            runtime = data.get("runtime_profile")
            if isinstance(runtime, dict) and isinstance(runtime.get("name"), str):
                profile = runtime["name"]
                break
        if profile != "unknown":
            break
    return tool, mode, profile


def _is_successful(path: Path) -> bool:
    for candidate in path.rglob("graphics_audit_manifest.json"):
        data = _read_json(candidate)
        if data.get("success") is True:
            return True
    for candidate in path.rglob("graphics_audit_artifact.json"):
        data = _read_json(candidate)
        capture = data.get("capture")
        validation = data.get("validation")
        if isinstance(capture, dict) and isinstance(validation, dict):
            if capture.get("success") is True and validation.get("complete") is True:
                return True
    return False


def _is_preserved(path: Path) -> bool:
    return (path / ".preserve").exists() or (path / "PRESERVE").exists()


def _is_heavy(path: Path) -> bool:
    return any(item.is_file() and item.suffix.lower() in HEAVY_SUFFIXES for item in path.rglob("*"))


def discover_runs(root: Path) -> list[ArtifactRun]:
    root = assert_marked_root(root)
    runs: list[ArtifactRun] = []
    for child in root.iterdir():
        if not child.is_dir():
            continue
        if child.name.startswith("."):
            continue
        if child.name in {"capture", "gameplay", "subsystem", "matrix"}:
            for dated in child.iterdir():
                if dated.is_dir():
                    tool, mode, profile = _tool_mode_profile_for(dated, root)
                    runs.append(
                        ArtifactRun(dated, tool, mode, profile, _is_successful(dated), _is_preserved(dated), _is_heavy(dated), dated.stat().st_mtime, directory_size(dated))
                    )
        else:
            tool, mode, profile = _tool_mode_profile_for(child, root)
            runs.append(
                ArtifactRun(child, tool, mode, profile, _is_successful(child), _is_preserved(child), _is_heavy(child), child.stat().st_mtime, directory_size(child))
            )
    return runs


def remove_copied_game_dirs(root: Path, scope: Path | None = None) -> list[Path]:
    root = assert_marked_root(root)
    scope = assert_inside_marked_root(root, scope or root)
    removed: list[Path] = []
    for path in sorted(scope.rglob("*"), reverse=True):
        if not path.is_dir():
            continue
        if not path.name.startswith(COPIED_GAME_DIR_PREFIXES):
            continue
        path = assert_inside_marked_root(root, path)
        shutil.rmtree(path)
        removed.append(path)
    return removed


def capture_run_ids(scope: Path) -> set[str]:
    if not scope.exists():
        return set()
    ids: set[str] = set()
    for path in scope.rglob("*"):
        match = CAPTURE_RUN_ID_PATTERN.search(path.name)
        if match:
            ids.add(match.group(1))
    return ids


def remove_generated_temp_dirs_for_capture(root: Path, capture_scope: Path) -> list[Path]:
    root = assert_marked_root(root)
    capture_scope = assert_inside_marked_root(root, capture_scope)
    temp_root = root / ".tmp"
    removed: list[Path] = []
    for run_id in sorted(capture_run_ids(capture_scope)):
        candidate = temp_root / run_id
        if not candidate.exists():
            continue
        candidate = assert_inside_marked_root(root, candidate)
        if candidate.parent != temp_root:
            raise RuntimeError(f"refusing unexpected generated temp path: {candidate}")
        shutil.rmtree(candidate)
        removed.append(candidate)
    return removed


def remove_path(root: Path, path: Path) -> None:
    path = assert_inside_marked_root(root, path)
    if path == assert_marked_root(root):
        raise RuntimeError("refusing to remove artifact root itself")
    if path.is_dir():
        shutil.rmtree(path)
    elif path.exists():
        path.unlink()


def _should_compress(path: Path, threshold_bytes: int) -> bool:
    if not path.is_file() or path.suffix.lower() not in COMPRESSIBLE_SUFFIXES:
        return False
    if path.name in NEVER_COMPRESS_NAMES or path.name.endswith(".gz"):
        return False
    if path.name.startswith("deterministic_camera_capture_") and path.suffix.lower() == ".json":
        return False
    return path.stat().st_size >= threshold_bytes


def compress_large_text_artifacts(root: Path, scope: Path | None = None, *, threshold_bytes: int = DEFAULT_COMPRESS_THRESHOLD_BYTES) -> list[Path]:
    root = assert_marked_root(root)
    scope = assert_inside_marked_root(root, scope or root)
    compressed: list[Path] = []
    for path in sorted(scope.rglob("*")):
        if not _should_compress(path, threshold_bytes):
            continue
        path = assert_inside_marked_root(root, path)
        compressed_path = path.with_name(path.name + ".gz")
        if compressed_path.exists():
            continue
        with path.open("rb") as source, gzip.open(compressed_path, "wb", compresslevel=6) as target:
            shutil.copyfileobj(source, target)
        path.unlink()
        compressed.append(compressed_path)
    return compressed


def cleanup(policy: RetentionPolicy, *, after_run: Path | None = None) -> dict[str, object]:
    ensure_marker(policy.root)
    if policy.preserve:
        return {"removed": [], "removed_game_dirs": [], "root_usage_bytes": directory_size(policy.root)}
    removed: list[str] = []
    removed_game_dirs = [str(path) for path in remove_copied_game_dirs(policy.root, after_run or policy.root)]
    compressed = [str(path) for path in compress_large_text_artifacts(policy.root, after_run or policy.root)]
    runs = discover_runs(policy.root)
    protected: set[Path] = set()
    groups: dict[tuple[str, str, bool], list[ArtifactRun]] = {}
    for run in runs:
        if run.preserved:
            protected.add(run.path)
            continue
        if run.heavy:
            continue
        groups.setdefault((run.tool, run.mode, run.success), []).append(run)
    for (_, _, success), group in groups.items():
        keep = policy.keep_success if success else policy.keep_failed
        for run in sorted(group, key=lambda item: item.mtime, reverse=True)[:keep]:
            protected.add(run.path)
    heavy_runs = [run for run in runs if run.heavy and not run.preserved]
    for run in sorted(heavy_runs, key=lambda item: item.mtime, reverse=True)[: policy.heavy_keep]:
        protected.add(run.path)
    for run in sorted(runs, key=lambda item: item.mtime):
        if run.path in protected:
            continue
        remove_path(policy.root, run.path)
        removed.append(str(run.path))
    if policy.global_limit_bytes:
        while directory_size(policy.root) > policy.global_limit_bytes:
            candidates = [run for run in discover_runs(policy.root) if not run.preserved and run.path not in protected]
            if not candidates:
                break
            victim = sorted(candidates, key=lambda item: (item.success, item.heavy, item.mtime))[0]
            remove_path(policy.root, victim.path)
            removed.append(str(victim.path))
    return {
        "removed": removed,
        "removed_game_dirs": removed_game_dirs,
        "compressed": compressed,
        "root_usage_bytes": directory_size(policy.root),
        "free_bytes": free_bytes(policy.root),
    }


def write_quota_failure(root: Path, run_root: Path, message: str) -> Path:
    ensure_marker(root)
    run_root = assert_inside_marked_root(root, run_root)
    run_root.mkdir(parents=True, exist_ok=True)
    path = run_root / "quota_failure_manifest.json"
    path.write_text(
        json.dumps(
            {
                "schema": "mattmc-graphics-quota-failure-v1",
                "created_at_epoch": int(time.time()),
                "run_root": str(run_root),
                "message": message,
            },
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
    )
    return path
