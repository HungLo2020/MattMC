#!/usr/bin/env python3
"""Deterministic cross-repository NBT/region storage replay benchmark."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import platform
import shutil
import statistics
import struct
import subprocess
import sys
import time
import zlib
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable


DEFAULT_WORLD = "Origin"
DEFAULT_WARMUP = 4
DEFAULT_MEASURE = 12
SECTOR_BYTES = 4096
HEADER_BYTES = 8192
CHUNK_HEADER_BYTES = 5
EXTERNAL_STREAM_FLAG = 0x80
EXTERNAL_CHUNK_THRESHOLD = 256
NBT_RAW = 0
NBT_GZIP = 1
NBT_ZLIB = 2
REGION_RAW = 3
REGION_LZ4 = 4


@dataclass(frozen=True)
class RepoTarget:
    name: str
    root: Path
    implementation: str


@dataclass(frozen=True)
class RunSpec:
    index: int
    target: RepoTarget


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def timestamp() -> str:
    return datetime.now().strftime("%Y%m%d-%H%M%S")


def repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def resolve_named_directory(name: str) -> Path:
    helper = repo_root() / "DevUtils" / "Common" / "platform" / "directory" / "directory_helper.py"
    completed = subprocess.run(
        [sys.executable, str(helper), name],
        cwd=repo_root(),
        text=True,
        capture_output=True,
        check=False,
    )
    if completed.returncode != 0:
        raise SystemExit(completed.stderr.strip() or completed.stdout.strip())
    path = Path(completed.stdout.strip())
    if not path.exists():
        raise SystemExit(f"Resolved directory '{name}' does not exist: {path}")
    return path


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def hash_tree(root: Path) -> str:
    digest = hashlib.sha256()
    if not root.exists():
        return ""
    for path in sorted(p for p in root.rglob("*") if p.is_file()):
        rel = path.relative_to(root).as_posix().encode("utf-8")
        digest.update(rel)
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def semantic_world_fingerprint(world: Path) -> dict[str, Any]:
    files: list[dict[str, Any]] = []
    for path in sorted(p for p in world.rglob("*") if p.is_file()):
        rel = path.relative_to(world).as_posix()
        if path.suffix == ".mca":
            files.append({"path": rel, "kind": "region", "fingerprint": semantic_region_fingerprint(path)})
        elif path.suffix == ".mcc":
            files.append({"path": rel, "kind": "external", "sha256": sha256_bytes(path.read_bytes())})
        elif path.suffix == ".dat" or path.suffix == ".nbt":
            raw = decode_standalone_nbt(path.read_bytes())
            files.append({"path": rel, "kind": "nbt", "rawSha256": sha256_bytes(raw)})
        elif path.name.endswith(".tmp"):
            files.append({"path": rel, "kind": "unexpected-temp", "sha256": sha256_bytes(path.read_bytes())})
        else:
            files.append({"path": rel, "kind": "raw", "sha256": sha256_bytes(path.read_bytes())})
    digest = hashlib.sha256(json.dumps(files, sort_keys=True, separators=(",", ":")).encode("utf-8")).hexdigest()
    return {"hash": digest, "files": files}


def semantic_region_fingerprint(path: Path) -> str:
    data = path.read_bytes()
    digest = hashlib.sha256()
    digest.update(b"region-v1")
    if len(data) < HEADER_BYTES:
        digest.update(b"truncated")
        digest.update(data)
        return digest.hexdigest()
    offsets = [struct.unpack(">I", data[i * 4 : i * 4 + 4])[0] for i in range(1024)]
    for index, offset_entry in enumerate(offsets):
        if offset_entry == 0:
            continue
        sector = (offset_entry >> 8) & 0xFFFFFF
        count = offset_entry & 0xFF
        encoded = read_region_payload_bytes(path, data, index, sector, count)
        digest.update(struct.pack(">HII", index, sector, count))
        digest.update(sha256_bytes(encoded).encode("ascii"))
    return digest.hexdigest()


def decode_standalone_nbt(data: bytes) -> bytes:
    if len(data) >= 2 and data[0] == 0x1F and data[1] == 0x8B:
        return gzip.decompress(data)
    if looks_like_zlib(data):
        return zlib.decompress(data)
    return data


def looks_like_zlib(data: bytes) -> bool:
    if len(data) < 2:
        return False
    cmf, flg = data[0], data[1]
    return (cmf & 0x0F) == 8 and (cmf >> 4) <= 7 and (((cmf << 8) | flg) % 31 == 0)


def read_region_payload_bytes(region_path: Path, region_data: bytes, index: int, sector: int, count: int) -> bytes:
    start = sector * SECTOR_BYTES
    if count == 0 or start < HEADER_BYTES or start + CHUNK_HEADER_BYTES > len(region_data):
        return b""
    declared_len = struct.unpack(">I", region_data[start : start + 4])[0]
    compression_byte = region_data[start + 4]
    if declared_len <= 0:
        return b""
    compression_id = compression_byte & ~EXTERNAL_STREAM_FLAG
    if compression_byte & EXTERNAL_STREAM_FLAG:
        region_x, region_z = parse_region_coords(region_path.name)
        chunk_x = region_x * 32 + index % 32
        chunk_z = region_z * 32 + index // 32
        external = region_path.parent / f"c.{chunk_x}.{chunk_z}.mcc"
        if not external.exists():
            return b""
        payload = external.read_bytes()
        return struct.pack(">IB", len(payload) + 1, compression_id) + payload
    payload_len = declared_len - 1
    available = max(0, min(count * SECTOR_BYTES - CHUNK_HEADER_BYTES, len(region_data) - start - CHUNK_HEADER_BYTES))
    payload = region_data[start + CHUNK_HEADER_BYTES : start + CHUNK_HEADER_BYTES + min(payload_len, available)]
    return struct.pack(">IB", declared_len, compression_byte) + payload


def parse_region_coords(name: str) -> tuple[int, int]:
    parts = name.split(".")
    if len(parts) < 4 or parts[0] != "r" or parts[-1] != "mca":
        raise ValueError(f"Not a region filename: {name}")
    return int(parts[1]), int(parts[2])


def copy_tree(src: Path, dst: Path) -> None:
    if dst.exists():
        shutil.rmtree(dst)
    shutil.copytree(src, dst)


def ensure_baseline(source_world: Path, baseline_world: Path) -> str:
    if not source_world.exists():
        raise SystemExit(f"Source world does not exist: {source_world}")
    if baseline_world.exists():
        return hash_tree(baseline_world)
    else:
        baseline_world.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(source_world, baseline_world)
    return hash_tree(baseline_world)


def restore_baseline(baseline_world: Path, target_world: Path) -> str:
    copy_tree(baseline_world, target_world)
    return hash_tree(target_world)


def native_library_path(repo: Path) -> Path:
    system = platform.system().lower()
    machine = platform.machine().lower()
    if system.startswith("win"):
        os_part, extension = "win", "dll"
    elif system == "darwin":
        os_part, extension = "mac", "dylib"
    elif system == "linux":
        os_part, extension = "linux", "so"
    else:
        raise SystemExit(f"Unsupported platform for MattMC Rust native library: {platform.system()}")
    if machine in {"amd64", "x86_64"}:
        arch_part = "x64"
    elif machine in {"arm64", "aarch64"}:
        arch_part = "aarch64"
    else:
        raise SystemExit(f"Unsupported architecture for MattMC Rust native library: {platform.machine()}")
    return repo / "build" / "rust" / "native" / f"mattmc_rust-{os_part}-{arch_part}.{extension}"


def native_identity(repo: Path, profile: str = "release") -> dict[str, Any]:
    path = native_library_path(repo)
    if not path.exists():
        raise SystemExit(f"Rust native library is missing: {path}")
    stat = path.stat()
    return {
        "profile": profile,
        "path": str(path),
        "sha256": sha256_bytes(path.read_bytes()),
        "size": stat.st_size,
        "lastWriteTimeUtc": datetime.fromtimestamp(stat.st_mtime, timezone.utc).isoformat(),
    }


def git_dirty_state(repo: Path) -> dict[str, Any]:
    status = subprocess.run(["git", "status", "--short"], cwd=repo, text=True, capture_output=True, check=False)
    return {
        "commit": commit_id(repo),
        "dirty": bool(status.stdout.strip()),
        "statusShort": status.stdout.splitlines(),
    }


def cargo_profile_settings(repo: Path) -> dict[str, Any]:
    cargo = repo / "src" / "main" / "rust" / "Cargo.toml"
    settings = {
        "opt-level": None,
        "lto": None,
        "codegen-units": None,
        "debug-assertions": None,
        "overflow-checks": None,
    }
    in_release = False
    for raw_line in cargo.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if line.startswith("[") and line.endswith("]"):
            in_release = line == "[profile.release]"
            continue
        if not in_release or "=" not in line:
            continue
        key, value = [part.strip() for part in line.split("=", 1)]
        if key in settings:
            settings[key] = value.strip('"')
    return settings


def force_release_rust_build(repo: Path, artifact_dir: Path, timeout: int) -> dict[str, Any]:
    before = native_identity(repo, "release") if native_library_path(repo).exists() else None
    log_path = artifact_dir / "release-rust-build.log"
    command = [
        str(repo / "gradlew.bat"),
        "buildRustNative",
        "--rerun-tasks",
        "-PmattmcRustProfile=release",
        "--no-daemon",
    ]
    started = time.perf_counter()
    completed = subprocess.run(command, cwd=repo, text=True, capture_output=True, timeout=timeout, check=False)
    duration = time.perf_counter() - started
    log_path.write_text(completed.stdout + "\n--- STDERR ---\n" + completed.stderr, encoding="utf-8", errors="replace")
    if completed.returncode != 0:
        raise RuntimeError(f"Release Rust build failed with exit code {completed.returncode}; see {log_path}")
    after = native_identity(repo, "release")
    combined_log = (completed.stdout + "\n" + completed.stderr).lower()
    if after["profile"] != "release" or "release" not in combined_log or "profile" not in combined_log:
        # The Gradle task copies Cargo's profile output into build/rust/native;
        # seeing the release profile path in the build log protects benchmark reports
        # from accidentally loading a debug/dev DLL.
        raise RuntimeError(f"Release Rust build did not clearly use Cargo release output; see {log_path}")
    return {
        "command": command,
        "durationSeconds": duration,
        "log": str(log_path),
        "before": before,
        "after": after,
        "rebuilt": before is None or before["sha256"] != after["sha256"] or before["lastWriteTimeUtc"] != after["lastWriteTimeUtc"],
        "cargoProfileSettings": cargo_profile_settings(repo),
        "git": git_dirty_state(repo),
    }


def assert_release_native_unchanged(expected: dict[str, Any], repo: Path, context: str) -> None:
    actual = native_identity(repo, "release")
    keys = ("path", "sha256", "size", "lastWriteTimeUtc")
    mismatches = [key for key in keys if actual[key] != expected[key]]
    if mismatches:
        raise RuntimeError(f"Rust native library changed during {context}: {mismatches}; expected {expected}; actual {actual}")


def blob_path(blobs: Path, data: bytes, suffix: str = ".bin") -> str:
    digest = sha256_bytes(data)
    path = blobs / f"{digest}{suffix}"
    if not path.exists():
        path.write_bytes(data)
    return path.name


def build_trace(source_world: Path, trace_dir: Path, max_region_chunks: int, max_nbt_docs: int) -> Path:
    trace_dir.mkdir(parents=True, exist_ok=True)
    blobs = trace_dir / "blobs"
    blobs.mkdir(exist_ok=True)
    nbt_docs: list[dict[str, Any]] = []
    region_ops: list[dict[str, Any]] = []
    seen_doc_hashes: set[tuple[str, int]] = set()

    for path in standalone_nbt_files(source_world):
        if len(nbt_docs) >= max_nbt_docs:
            break
        encoded = path.read_bytes()
        compression = detect_nbt_compression(encoded)
        if compression not in {NBT_RAW, NBT_GZIP, NBT_ZLIB}:
            continue
        raw = decode_standalone_nbt(encoded)
        append_nbt_doc_variants(nbt_docs, seen_doc_hashes, blobs, path.relative_to(source_world).as_posix(), raw, max_nbt_docs)

    selected_chunks = collect_region_chunks(source_world, max_region_chunks)
    for chunk in selected_chunks:
        encoded = chunk["encoded"]
        raw = try_decode_region_nbt(encoded)
        if raw is not None and len(nbt_docs) < max_nbt_docs:
            nbt_compression = region_to_nbt_compression(chunk["compression"])
            if nbt_compression is not None:
                append_nbt_doc_variants(
                    nbt_docs,
                    seen_doc_hashes,
                    blobs,
                    f"{chunk['region']}:{chunk['chunkX']},{chunk['chunkZ']}",
                    raw,
                    max_nbt_docs,
                )

    chunks_by_region: dict[str, list[dict[str, Any]]] = {}
    for chunk in selected_chunks:
        chunks_by_region.setdefault(chunk["region"], []).append(chunk)
    skipped_write_regions: list[str] = []
    for region, chunks in sorted(chunks_by_region.items()):
        for chunk in chunks:
            region_ops.append(read_operation(chunk, blobs))
        if not strict_writable_region(baseline_path(source_world, region)):
            skipped_write_regions.append(region)
            region_ops.append(basic_region_operation("flush", chunks[0]))
            region_ops.append(basic_region_operation("reopen", chunks[0]))
            continue
        first = chunks[0]
        region_ops.append(write_operation(first, blobs))
        region_ops.append(read_operation(first, blobs))
        region_ops.append(basic_region_operation("delete", first))
        missing = read_operation(first, blobs)
        missing["expectedPresent"] = False
        missing["payloadBytes"] = 0
        missing["payloadSha256"] = ""
        missing.pop("payloadBlob", None)
        region_ops.append(missing)
        region_ops.append(write_operation(first, blobs))
        region_ops.append(basic_region_operation("flush", first))
        region_ops.append(basic_region_operation("reopen", first))
        region_ops.append(read_operation(first, blobs))

    if not nbt_docs:
        raise SystemExit(f"No usable NBT documents found under {source_world}")
    if not region_ops:
        raise SystemExit(f"No region payload operations could be captured from {source_world}")

    trace = {
        "version": 1,
        "createdUtc": utc_now(),
        "sourceWorld": str(source_world),
        "sourceWorldHash": hash_tree(source_world),
        "nbtDocuments": nbt_docs,
        "regionOperations": region_ops,
        "skippedWriteRegions": skipped_write_regions,
        "expectedInitialSemanticWorld": semantic_world_fingerprint(source_world),
    }
    trace_path = trace_dir / "storage_trace.json"
    trace_path.write_text(json.dumps(trace, indent=2), encoding="utf-8")
    return trace_path


def baseline_path(world: Path, region: str) -> Path:
    return world / Path(region)


def append_nbt_doc_variants(
    docs: list[dict[str, Any]],
    seen: set[tuple[str, int]],
    blobs: Path,
    base_id: str,
    raw: bytes,
    max_docs: int,
) -> None:
    raw_hash = sha256_bytes(raw)
    variants = (
        (NBT_RAW, raw),
        (NBT_GZIP, gzip.compress(raw, mtime=0)),
        (NBT_ZLIB, zlib.compress(raw)),
    )
    for compression, encoded in variants:
        if len(docs) >= max_docs:
            return
        key = (raw_hash, compression)
        if key in seen:
            continue
        seen.add(key)
        docs.append(
            {
                "id": f"{base_id}#{compression_name(compression)}",
                "compression": compression,
                "encodedBlob": "blobs/" + blob_path(blobs, encoded),
                "rawBlob": "blobs/" + blob_path(blobs, raw),
                "encodedBytes": len(encoded),
                "rawBytes": len(raw),
                "rawSha256": raw_hash,
                "objectFingerprint": raw_hash,
            }
        )


def compression_name(compression: int) -> str:
    return {NBT_RAW: "raw", NBT_GZIP: "gzip", NBT_ZLIB: "zlib"}.get(compression, str(compression))


def strict_writable_region(path: Path) -> bool:
    try:
        data = path.read_bytes()
    except OSError:
        return False
    if len(data) < HEADER_BYTES:
        return False
    sectors = max(2, len(data) // SECTOR_BYTES)
    used = [False] * sectors
    used[0] = True
    used[1] = True
    for index in range(1024):
        offset = struct.unpack(">I", data[index * 4 : index * 4 + 4])[0]
        if offset == 0:
            continue
        sector = (offset >> 8) & 0xFFFFFF
        count = offset & 0xFF
        if sector < 2 or count == 0:
            return False
        start = sector * SECTOR_BYTES
        end = (sector + count) * SECTOR_BYTES
        if start > len(data) or end > len(data) or sector + count > sectors:
            return False
        for current in range(sector, sector + count):
            if used[current]:
                return False
            used[current] = True
    return True


def standalone_nbt_files(world: Path) -> Iterable[Path]:
    candidates = [world / "level.dat"]
    candidates.extend(sorted((world / "playerdata").glob("*.dat")))
    candidates.extend(sorted((world / "data").glob("*.dat")))
    candidates.extend(sorted(world.rglob("*.nbt")))
    seen: set[Path] = set()
    for path in candidates:
        if path.exists() and path.is_file() and path not in seen:
            seen.add(path)
            yield path


def detect_nbt_compression(data: bytes) -> int:
    if len(data) >= 2 and data[0] == 0x1F and data[1] == 0x8B:
        return NBT_GZIP
    if looks_like_zlib(data):
        return NBT_ZLIB
    return NBT_RAW


def collect_region_chunks(world: Path, max_chunks: int) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    region_files = [p for p in sorted(world.rglob("*.mca")) if p.parent.name in {"region", "entities", "poi"}]
    per_type_counts: dict[str, int] = {}
    per_region_limit = max(1, max_chunks // max(1, len(region_files)))
    for region_path in region_files:
        storage_type = region_path.parent.name
        if per_type_counts.get(storage_type, 0) >= max_chunks:
            continue
        region_x, region_z = parse_region_coords(region_path.name)
        data = region_path.read_bytes()
        if len(data) < HEADER_BYTES:
            continue
        taken = 0
        for index in range(1024):
            offset = struct.unpack(">I", data[index * 4 : index * 4 + 4])[0]
            if offset == 0:
                continue
            sector = (offset >> 8) & 0xFFFFFF
            count = offset & 0xFF
            encoded = read_region_payload_bytes(region_path, data, index, sector, count)
            if len(encoded) < CHUNK_HEADER_BYTES:
                continue
            compression_byte = encoded[4]
            compression = compression_byte & ~EXTERNAL_STREAM_FLAG
            chunk = {
                "storageType": storage_type,
                "region": region_path.relative_to(world).as_posix(),
                "chunkX": region_x * 32 + index % 32,
                "chunkZ": region_z * 32 + index // 32,
                "compression": compression,
                "external": bool(compression_byte & EXTERNAL_STREAM_FLAG) or size_to_sectors(len(encoded)) >= EXTERNAL_CHUNK_THRESHOLD,
                "encoded": encoded,
                "encodedSha256": sha256_bytes(encoded),
            }
            result.append(chunk)
            per_type_counts[storage_type] = per_type_counts.get(storage_type, 0) + 1
            taken += 1
            if taken >= per_region_limit or len(result) >= max_chunks:
                break
        if len(result) >= max_chunks:
            break
    return result


def size_to_sectors(size: int) -> int:
    return (size + SECTOR_BYTES - 1) // SECTOR_BYTES


def region_to_nbt_compression(compression: int) -> int | None:
    if compression == NBT_GZIP:
        return NBT_GZIP
    if compression == NBT_ZLIB:
        return NBT_ZLIB
    if compression == REGION_RAW:
        return NBT_RAW
    return None


def try_decode_region_nbt(encoded: bytes) -> bytes | None:
    if len(encoded) < CHUNK_HEADER_BYTES:
        return None
    declared_len = struct.unpack(">I", encoded[:4])[0]
    if declared_len <= 0:
        return None
    compression = encoded[4] & ~EXTERNAL_STREAM_FLAG
    payload = encoded[CHUNK_HEADER_BYTES:]
    try:
        if compression == NBT_GZIP:
            return gzip.decompress(payload)
        if compression == NBT_ZLIB:
            return zlib.decompress(payload)
        if compression == REGION_RAW:
            return payload
    except Exception:
        return None
    return None


def read_operation(chunk: dict[str, Any], blobs: Path) -> dict[str, Any]:
    return {
        "op": "read",
        "storageType": chunk["storageType"],
        "region": chunk["region"],
        "chunkX": chunk["chunkX"],
        "chunkZ": chunk["chunkZ"],
        "expectedPresent": True,
        "compression": chunk["compression"],
        "external": chunk["external"],
        "payloadBytes": len(chunk["encoded"]),
        "payloadSha256": chunk["encodedSha256"],
        "payloadBlob": "blobs/" + blob_path(blobs, chunk["encoded"]),
    }


def write_operation(chunk: dict[str, Any], blobs: Path) -> dict[str, Any]:
    operation = read_operation(chunk, blobs)
    operation["op"] = "write"
    return operation


def basic_region_operation(op: str, chunk: dict[str, Any]) -> dict[str, Any]:
    return {
        "op": op,
        "storageType": chunk["storageType"],
        "region": chunk["region"],
        "chunkX": chunk["chunkX"],
        "chunkZ": chunk["chunkZ"],
    }


def run_gradle_replay(
    run: RunSpec,
    trace_path: Path,
    world_path: Path,
    output_path: Path,
    warmup: int,
    measure: int,
    timeout: int,
) -> dict[str, Any]:
    gradle = run.target.root / "gradlew.bat"
    command = [
        str(gradle),
        "runStorageReplayBenchmark",
        "-x",
        "test",
        "-PmattmcRustProfile=release",
        f"-PmattmcStorageReplayTrace={trace_path}",
        f"-PmattmcStorageReplayWorld={world_path}",
        f"-PmattmcStorageReplayOutput={output_path}",
        f"-PmattmcStorageReplayWarmup={warmup}",
        f"-PmattmcStorageReplayMeasure={measure}",
        f"-PmattmcStorageReplayImplementation={run.target.implementation}",
        "--no-daemon",
    ]
    if run.target.name == "current":
        command[4:4] = ["-x", "testRustNative"]
    started = time.perf_counter()
    completed = subprocess.run(command, cwd=run.target.root, text=True, capture_output=True, timeout=timeout, check=False)
    duration = time.perf_counter() - started
    log_path = output_path.with_suffix(".log")
    log_path.write_text(completed.stdout + "\n--- STDERR ---\n" + completed.stderr, encoding="utf-8", errors="replace")
    if completed.returncode != 0:
        raise RuntimeError(f"{run.target.name} replay failed with exit code {completed.returncode}; see {log_path}")
    result = json.loads(output_path.read_text(encoding="utf-8"))
    result["processSeconds"] = duration
    result["command"] = command
    result["log"] = str(log_path)
    return result


def preset_runs(preset: str, current: RepoTarget, frozen: RepoTarget) -> list[RunSpec]:
    if preset == "quick":
        order = [current, frozen]
    elif preset == "development":
        order = [current, frozen, frozen, current]
    elif preset == "authoritative":
        order = [current, frozen, current, frozen, current]
    else:
        raise ValueError(preset)
    return [RunSpec(index=i + 1, target=target) for i, target in enumerate(order)]


def commit_id(repo: Path) -> str:
    completed = subprocess.run(["git", "rev-parse", "HEAD"], cwd=repo, text=True, capture_output=True, check=False)
    return completed.stdout.strip() if completed.returncode == 0 else "unknown"


def summarize_runs(runs: list[dict[str, Any]]) -> dict[str, Any]:
    by_impl: dict[str, dict[str, list[float]]] = {}
    for run in runs:
        implementation = run["implementation"]
        by_impl.setdefault(implementation, {})
        for sample in run["samples"]:
            if sample["phase"] != "measure":
                continue
            for name, metric in sample["metrics"].items():
                if metric["operations"] == 0:
                    continue
                by_impl[implementation].setdefault(name, []).append(metric["nanos"] / metric["operations"])
    summary: dict[str, Any] = {}
    for implementation, metrics in by_impl.items():
        summary[implementation] = {}
        for name, values in metrics.items():
            summary[implementation][name] = {
                "medianNsPerOp": statistics.median(values),
                "stdevNsPerOp": statistics.stdev(values) if len(values) > 1 else 0.0,
                "samples": values,
            }
    if "current-rust" in summary and "frozen-java" in summary:
        ratios: dict[str, Any] = {}
        for name, current_metric in summary["current-rust"].items():
            frozen_metric = summary["frozen-java"].get(name)
            if frozen_metric and frozen_metric["medianNsPerOp"] > 0:
                ratios[name] = current_metric["medianNsPerOp"] / frozen_metric["medianNsPerOp"]
        summary["ratiosCurrentOverFrozen"] = ratios
    return summary


def compare_semantics(run_worlds: list[dict[str, Any]]) -> dict[str, Any]:
    fingerprints = [entry["semantic"]["hash"] for entry in run_worlds]
    return {
        "allMatch": len(set(fingerprints)) == 1,
        "hashes": fingerprints,
        "worlds": [{"run": entry["run"], "target": entry["target"], "hash": entry["semantic"]["hash"]} for entry in run_worlds],
    }


def write_markdown(report: dict[str, Any], path: Path) -> None:
    lines = [
        "# Deterministic Storage Replay",
        "",
        f"- Generated: `{report['createdUtc']}`",
        f"- Preset: `{report['preset']}`",
        f"- Baseline hash: `{report['baselineHash']}`",
        f"- Trace: `{report['tracePath']}`",
        f"- Trace reused: `{report['traceReused']}`",
        f"- Semantic parity: `{report['semanticComparison']['allMatch']}`",
        "",
        "## Rust Native",
        "",
        f"- Profile: `{report['releaseRustBuild']['after']['profile']}`",
        f"- DLL: `{report['releaseRustBuild']['after']['path']}`",
        f"- SHA-256: `{report['releaseRustBuild']['after']['sha256']}`",
        f"- Size: `{report['releaseRustBuild']['after']['size']}` bytes",
        f"- Timestamp UTC: `{report['releaseRustBuild']['after']['lastWriteTimeUtc']}`",
        f"- Rebuilt by preflight: `{report['releaseRustBuild']['rebuilt']}`",
        f"- opt-level: `{report['releaseRustBuild']['cargoProfileSettings']['opt-level']}`",
        f"- LTO: `{report['releaseRustBuild']['cargoProfileSettings']['lto']}`",
        f"- codegen units: `{report['releaseRustBuild']['cargoProfileSettings']['codegen-units']}`",
        f"- debug assertions: `{report['releaseRustBuild']['cargoProfileSettings']['debug-assertions']}`",
        f"- overflow checks: `{report['releaseRustBuild']['cargoProfileSettings']['overflow-checks']}`",
        "",
        "## Run Order",
        "",
    ]
    for run in report["runs"]:
        lines.append(f"- {run['runIndex']}: `{run['target']}` `{run['implementation']}` restored `{run['restoredHash']}`")
    lines.extend(["", "## Median Ratios", ""])
    ratios = report["summary"].get("ratiosCurrentOverFrozen", {})
    for name in sorted(ratios):
        ratio = ratios[name]
        direction = "faster" if ratio < 1.0 else "slower"
        lines.append(f"- `{name}`: current/frozen `{ratio:.3f}` ({direction})")
    lines.extend(["", "## Artifacts", ""])
    lines.append(f"- JSON report: `{report['jsonPath']}`")
    lines.append(f"- Trace directory: `{report['traceDir']}`")
    lines.append(f"- Baseline world: `{report['baselineWorld']}`")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preset", choices=["quick", "development", "authoritative"], default="development")
    parser.add_argument("--world", default=DEFAULT_WORLD)
    parser.add_argument("--warmup", type=int, default=DEFAULT_WARMUP)
    parser.add_argument("--measure", type=int, default=DEFAULT_MEASURE)
    parser.add_argument("--timeout", type=int, default=1800)
    parser.add_argument("--max-region-chunks", type=int, default=96)
    parser.add_argument("--max-nbt-docs", type=int, default=160)
    parser.add_argument("--output-dir", type=Path, default=repo_root() / "logs" / "perf-audit" / "storage-replay")
    parser.add_argument("--trace", type=Path, help="reuse an existing deterministic storage trace instead of capturing a new one")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    current_repo = repo_root()
    frozen_repo = resolve_named_directory("java_perf_repo")
    current = RepoTarget("current", current_repo, "current-rust")
    frozen = RepoTarget("frozen", frozen_repo, "frozen-java")
    source_world = current_repo / "run" / "saves" / args.world
    baseline_world = args.output_dir / "baseline" / args.world
    artifact_dir = args.output_dir / "runs" / f"{timestamp()}-{args.preset}"
    trace_dir = artifact_dir / "trace"
    run_world_dir = artifact_dir / "run-worlds"
    run_result_dir = artifact_dir / "run-results"
    run_world_dir.mkdir(parents=True, exist_ok=True)
    run_result_dir.mkdir(parents=True, exist_ok=True)

    baseline_hash = ensure_baseline(source_world, baseline_world)
    if args.trace:
        trace_path = args.trace.resolve()
        if not trace_path.exists():
            raise SystemExit(f"Requested trace does not exist: {trace_path}")
        trace_dir.mkdir(parents=True, exist_ok=True)
    else:
        trace_path = build_trace(baseline_world, trace_dir, args.max_region_chunks, args.max_nbt_docs)
    release_build = force_release_rust_build(current_repo, artifact_dir, args.timeout)
    expected_native = release_build["after"]
    run_outputs: list[dict[str, Any]] = []
    run_worlds: list[dict[str, Any]] = []
    for spec in preset_runs(args.preset, current, frozen):
        target_world = spec.target.root / "run" / "saves" / args.world
        restored_hash = restore_baseline(baseline_world, target_world)
        if restored_hash != baseline_hash:
            raise SystemExit(f"Baseline restore hash mismatch for {spec.target.name}: {restored_hash} != {baseline_hash}")
        output_path = run_result_dir / f"{spec.index:02d}-{spec.target.name}.json"
        if spec.target.name == "current":
            assert_release_native_unchanged(expected_native, current_repo, f"before run {spec.index}")
        result = run_gradle_replay(spec, trace_path, target_world, output_path, args.warmup, args.measure, args.timeout)
        if spec.target.name == "current":
            assert_release_native_unchanged(expected_native, current_repo, f"after run {spec.index}")
        preserved_world = run_world_dir / f"{spec.index:02d}-{spec.target.name}-{args.world}"
        copy_tree(target_world, preserved_world)
        semantic = semantic_world_fingerprint(preserved_world)
        run_outputs.append(
            {
                "runIndex": spec.index,
                "target": spec.target.name,
                "implementation": spec.target.implementation,
                "repo": str(spec.target.root),
                "commit": commit_id(spec.target.root),
                "rustNative": native_identity(spec.target.root, "release") if spec.target.name == "current" else None,
                "restoredHash": restored_hash,
                "outputPath": str(output_path),
                "log": result["log"],
                "processSeconds": result["processSeconds"],
                "result": result,
            }
        )
        run_worlds.append({"run": spec.index, "target": spec.target.name, "world": str(preserved_world), "semantic": semantic})

    report_path = artifact_dir / "storage_replay_report.json"
    markdown_path = artifact_dir / "storage_replay_report.md"
    report = {
        "createdUtc": utc_now(),
        "preset": args.preset,
        "baselineWorld": str(baseline_world),
        "baselineHash": baseline_hash,
        "tracePath": str(trace_path),
        "traceDir": str(trace_dir),
        "traceReused": bool(args.trace),
        "releaseRustBuild": release_build,
        "jsonPath": str(report_path),
        "currentRepo": str(current_repo),
        "frozenRepo": str(frozen_repo),
        "runs": run_outputs,
        "preservedWorlds": run_worlds,
        "semanticComparison": compare_semantics(run_worlds),
        "summary": summarize_runs([run["result"] for run in run_outputs]),
        "notes": [
            "Minecraft is not launched; the benchmark replays explicit region/NBT operations headlessly.",
            "Region timestamps are excluded from semantic world fingerprints.",
            "Standalone Rust NBT FFI supports raw/gzip/zlib; LZ4 is exercised through region payload replay when present.",
        ],
    }
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    write_markdown(report, markdown_path)
    print(f"Report: {report_path}")
    print(f"Markdown: {markdown_path}")
    print(f"Semantic parity: {report['semanticComparison']['allMatch']}")
    if not report["semanticComparison"]["allMatch"]:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
