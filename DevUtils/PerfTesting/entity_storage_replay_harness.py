#!/usr/bin/env python3
"""Deterministic cross-repository entity-storage replay benchmark."""

from __future__ import annotations

import argparse
import hashlib
import json
import platform
import shutil
import statistics
import subprocess
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


DEFAULT_WORLD = "Origin"
DEFAULT_WARMUP = 8
DEFAULT_MEASURE = 24
DEFAULT_REAL_CHUNKS = 32


@dataclass(frozen=True)
class RepoTarget:
    name: str
    root: Path
    implementation: str


@dataclass(frozen=True)
class RunSpec:
    index: int
    target: RepoTarget


def repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def timestamp() -> str:
    return datetime.now().strftime("%Y%m%d-%H%M%S")


def resolve_named_directory(name: str) -> Path:
    helper = repo_root() / "DevUtils" / "Common" / "platform" / "directory" / "directory_helper.py"
    completed = subprocess.run([sys.executable, str(helper), name], cwd=repo_root(), text=True, capture_output=True, check=False)
    if completed.returncode != 0:
        raise SystemExit(completed.stderr.strip() or completed.stdout.strip())
    path = Path(completed.stdout.strip()).resolve()
    if not path.exists():
        raise SystemExit(f"Resolved directory '{name}' does not exist: {path}")
    return path


def gradle_wrapper(repo: Path) -> Path:
    wrapper = repo / ("gradlew.bat" if platform.system().lower().startswith("win") else "gradlew")
    if wrapper.exists():
        return wrapper
    fallback = repo / "gradlew.bat"
    if fallback.exists():
        return fallback
    raise SystemExit(f"Gradle wrapper not found in {repo}")


def hash_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def hash_tree(root: Path) -> str:
    digest = hashlib.sha256()
    if not root.exists():
        return ""
    for path in sorted(p for p in root.rglob("*") if p.is_file()):
        digest.update(path.relative_to(root).as_posix().encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def copy_tree(src: Path, dst: Path) -> None:
    if dst.exists():
        shutil.rmtree(dst)
    shutil.copytree(src, dst)


def ensure_baseline(source_world: Path, baseline_world: Path) -> str:
    if not source_world.exists():
        raise SystemExit(f"Source world does not exist: {source_world}")
    if not baseline_world.exists():
        baseline_world.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(source_world, baseline_world)
    return hash_tree(baseline_world)


def commit_id(repo: Path) -> str:
    completed = subprocess.run(["git", "rev-parse", "HEAD"], cwd=repo, text=True, capture_output=True, check=False)
    return completed.stdout.strip() if completed.returncode == 0 else "unknown"


def git_status(repo: Path) -> str:
    completed = subprocess.run(["git", "status", "--short"], cwd=repo, text=True, capture_output=True, check=False)
    return completed.stdout.strip() if completed.returncode == 0 else "unknown"


def native_library_path(repo: Path) -> Path:
    system = platform.system().lower()
    machine = platform.machine().lower()
    if system.startswith("win"):
        os_part, extension = "win", "dll"
    elif system == "darwin":
        os_part, extension = "mac", "dylib"
    else:
        os_part, extension = "linux", "so"
    arch_part = "x64" if machine in {"amd64", "x86_64"} else "aarch64"
    return repo / "build" / "rust" / "native" / f"mattmc_rust-{os_part}-{arch_part}.{extension}"


def native_identity(repo: Path) -> dict[str, Any] | None:
    path = native_library_path(repo)
    if not path.exists():
        return None
    stat = path.stat()
    return {
        "path": str(path),
        "sha256": hash_file(path),
        "size": stat.st_size,
        "modifiedUtc": datetime.fromtimestamp(stat.st_mtime, timezone.utc).isoformat(),
        "profile": "release",
    }


def force_release_rust_build(repo: Path, artifact_dir: Path, timeout: int) -> dict[str, Any]:
    log_path = artifact_dir / "release-rust-build.log"
    command = [
        str(gradle_wrapper(repo)),
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
        raise RuntimeError(f"Release Rust build failed; see {log_path}")
    identity = native_identity(repo)
    if identity is None:
        raise RuntimeError("Release Rust build did not produce a native library")
    return {"command": command, "durationSeconds": duration, "log": str(log_path), "after": identity}


def inject_frozen_benchmark(current_repo: Path, frozen_repo: Path, artifact_dir: Path) -> Path:
    source = current_repo / "src" / "test" / "java" / "net" / "minecraft" / "world" / "level" / "chunk" / "storage" / "EntityStorageReplayBenchmark.java"
    if not source.exists():
        raise SystemExit(f"Benchmark source is missing: {source}")
    injected = frozen_repo / "build" / "entityReplayBenchmark" / "src" / "test" / "java" / "net" / "minecraft" / "world" / "level" / "chunk" / "storage"
    injected.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, injected / source.name)
    init_script = artifact_dir / "frozen-entity-replay-init.gradle"
    init_script.write_text(
        """
allprojects {
    afterEvaluate { project ->
        project.sourceSets.test.java.srcDir(project.file('build/entityReplayBenchmark/src/test/java'))
        if (!project.tasks.findByName('runEntityStorageReplayBenchmark')) {
            project.tasks.register('runEntityStorageReplayBenchmark', JavaExec) {
                group = 'verification'
                description = 'Runs the deterministic headless entity-storage replay benchmark.'
                dependsOn 'testClasses'
                classpath = project.sourceSets.test.runtimeClasspath
                mainClass = 'net.minecraft.world.level.chunk.storage.EntityStorageReplayBenchmark'
                workingDir = project.file('run')
                doFirst {
                    def trace = project.providers.gradleProperty('mattmcEntityReplayTrace')
                    def world = project.providers.gradleProperty('mattmcEntityReplayWorld')
                    def output = project.providers.gradleProperty('mattmcEntityReplayOutput')
                    def scratch = project.providers.gradleProperty('mattmcEntityReplayScratch')
                    if (!trace.isPresent() || !world.isPresent() || !output.isPresent() || !scratch.isPresent()) {
                        throw new GradleException('runEntityStorageReplayBenchmark requires trace, world, output, and scratch properties')
                    }
                    systemProperty 'mattmc.entityReplay.trace', project.file(trace.get()).absolutePath
                    systemProperty 'mattmc.entityReplay.world', project.file(world.get()).absolutePath
                    systemProperty 'mattmc.entityReplay.output', project.file(output.get()).absolutePath
                    systemProperty 'mattmc.entityReplay.scratch', project.file(scratch.get()).absolutePath
                    systemProperty 'mattmc.entityReplay.warmup', project.providers.gradleProperty('mattmcEntityReplayWarmup').orElse('8').get()
                    systemProperty 'mattmc.entityReplay.measure', project.providers.gradleProperty('mattmcEntityReplayMeasure').orElse('24').get()
                    systemProperty 'mattmc.entityReplay.implementation', project.providers.gradleProperty('mattmcEntityReplayImplementation').orElse('frozen-java').get()
                    systemProperty 'mattmc.entityReplay.generateTrace', 'false'
                    systemProperty 'java.awt.headless', 'true'
                }
                def osName = System.getProperty('os.name').toLowerCase()
                def javaExecutableName = osName.contains('win') ? 'java.exe' : 'java'
                def platformKey = osName.contains('win') ? 'windows' : (osName.contains('mac') ? 'macos' : 'linux')
                def bundledJava = project.file("run/jdk/${platformKey}/bin/${javaExecutableName}")
                if (bundledJava.exists()) {
                    executable = bundledJava.absolutePath
                }
            }
        }
    }
}
""".strip()
        + "\n",
        encoding="utf-8",
    )
    return init_script


def run_trace_generation(repo: Path, world: Path, trace: Path, max_real_chunks: int, timeout: int) -> dict[str, Any]:
    command = [
        str(gradle_wrapper(repo)),
        "runEntityStorageReplayBenchmark",
        "-x",
        "test",
        "-PmattmcRustProfile=release",
        f"-PmattmcEntityReplayTrace={trace}",
        f"-PmattmcEntityReplayWorld={world}",
        f"-PmattmcEntityReplayGenerateTrace=true",
        f"-PmattmcEntityReplayMaxRealChunks={max_real_chunks}",
        "--no-daemon",
    ]
    started = time.perf_counter()
    completed = subprocess.run(command, cwd=repo, text=True, capture_output=True, timeout=timeout, check=False)
    duration = time.perf_counter() - started
    log = trace.with_suffix(".generate.log")
    log.write_text(completed.stdout + "\n--- STDERR ---\n" + completed.stderr, encoding="utf-8", errors="replace")
    if completed.returncode != 0:
        raise RuntimeError(f"Entity trace generation failed; see {log}")
    return {"command": command, "durationSeconds": duration, "log": str(log)}


def run_gradle_replay(
    spec: RunSpec,
    trace: Path,
    world_name: str,
    output: Path,
    scratch: Path,
    warmup: int,
    measure: int,
    timeout: int,
    frozen_init: Path | None,
) -> dict[str, Any]:
    command = [str(gradle_wrapper(spec.target.root))]
    if spec.target.name == "frozen" and frozen_init is not None:
        command.extend(["-I", str(frozen_init)])
    command.extend(
        [
            "runEntityStorageReplayBenchmark",
            "-x",
            "test",
            "-PmattmcRustProfile=release",
            f"-PmattmcEntityReplayTrace={trace}",
            f"-PmattmcEntityReplayWorld={spec.target.root / 'run' / 'saves' / world_name}",
            f"-PmattmcEntityReplayOutput={output}",
            f"-PmattmcEntityReplayScratch={scratch}",
            f"-PmattmcEntityReplayWarmup={warmup}",
            f"-PmattmcEntityReplayMeasure={measure}",
            f"-PmattmcEntityReplayImplementation={spec.target.implementation}",
            "--no-daemon",
        ]
    )
    if spec.target.name == "current":
        command[2:2] = ["-x", "testRustNative"]
    started = time.perf_counter()
    completed = subprocess.run(command, cwd=spec.target.root, text=True, capture_output=True, timeout=timeout, check=False)
    duration = time.perf_counter() - started
    log = output.with_suffix(".log")
    log.write_text(completed.stdout + "\n--- STDERR ---\n" + completed.stderr, encoding="utf-8", errors="replace")
    if completed.returncode != 0:
        raise RuntimeError(f"{spec.target.name} entity replay failed with exit code {completed.returncode}; see {log}")
    result = json.loads(output.read_text(encoding="utf-8"))
    result["processSeconds"] = duration
    result["command"] = command
    result["log"] = str(log)
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
    return [RunSpec(i + 1, target) for i, target in enumerate(order)]


def summarize_runs(runs: list[dict[str, Any]]) -> dict[str, Any]:
    by_impl: dict[str, dict[str, list[float]]] = {}
    allocations: dict[str, dict[str, list[float]]] = {}
    for run in runs:
        implementation = run["implementation"]
        by_impl.setdefault(implementation, {})
        allocations.setdefault(implementation, {})
        for sample in run["samples"]:
            if sample["phase"] != "measure":
                continue
            for name, metric in sample["metrics"].items():
                operations = metric.get("operations", 0)
                if operations <= 0:
                    continue
                by_impl[implementation].setdefault(name, []).append(metric.get("nanos", 0) / operations)
                if metric.get("allocationSamples", 0) > 0:
                    allocations[implementation].setdefault(name, []).append(metric.get("allocatedBytes", 0) / metric["allocationSamples"])
    summary: dict[str, Any] = {}
    for implementation, metrics in by_impl.items():
        summary[implementation] = {}
        for name, values in metrics.items():
            summary[implementation][name] = summarize_values(values)
            alloc_values = allocations.get(implementation, {}).get(name)
            if alloc_values:
                summary[implementation][name]["medianAllocatedBytes"] = statistics.median(alloc_values)
    if "current-rust" in summary and "frozen-java" in summary:
        ratios: dict[str, float] = {}
        for name, current_metric in summary["current-rust"].items():
            frozen_metric = summary["frozen-java"].get(name)
            if frozen_metric and frozen_metric["medianNsPerOp"] > 0:
                ratios[name] = current_metric["medianNsPerOp"] / frozen_metric["medianNsPerOp"]
        summary["ratiosCurrentOverFrozen"] = ratios
    return summary


def summarize_values(values: list[float]) -> dict[str, Any]:
    return {
        "medianNsPerOp": statistics.median(values),
        "stdevNsPerOp": statistics.stdev(values) if len(values) > 1 else 0.0,
        "samples": values,
    }


def compare_fingerprints(runs: list[dict[str, Any]]) -> dict[str, Any]:
    latest_by_impl: dict[str, dict[str, dict[str, Any]]] = {}
    for run in runs:
        implementation = run["implementation"]
        measured = [sample for sample in run["samples"] if sample["phase"] == "measure"]
        if not measured:
            continue
        for fixture in measured[-1].get("fixtures", []):
            latest_by_impl.setdefault(implementation, {})[fixture["id"]] = fixture

    current = latest_by_impl.get("current-rust", {})
    frozen = latest_by_impl.get("frozen-java", {})
    fixture_ids = sorted(set(current) | set(frozen))
    rust_mismatches = []
    stable_state_mismatches = []
    serialization_drifts = []
    missing = []
    for fixture_id in fixture_ids:
        current_fixture = current.get(fixture_id)
        frozen_fixture = frozen.get(fixture_id)
        if current_fixture is None or frozen_fixture is None:
            missing.append({"fixture": fixture_id, "currentPresent": current_fixture is not None, "frozenPresent": frozen_fixture is not None})
            continue

        current_java_saved = current_fixture.get("currentJavaSavedFingerprint")
        current_java_state = current_fixture.get("currentJavaStateFingerprint")
        current_rust_saved = current_fixture.get("savedFingerprint")
        current_rust_state = current_fixture.get("stateFingerprint")
        frozen_saved = frozen_fixture.get("savedFingerprint")
        frozen_state = frozen_fixture.get("stateFingerprint")

        if current_java_saved and current_java_saved != current_rust_saved:
            rust_mismatches.append(
                {
                    "fixture": fixture_id,
                    "classification": "Rust bug",
                    "reason": "current Rust re-saved NBT differs from current Java for the same payload",
                    "currentJava": current_java_saved,
                    "currentRust": current_rust_saved,
                    "fieldDiffs": bounded_field_diffs(
                        current_fixture.get("currentJavaSavedEntityDiagnostics", []),
                        current_fixture.get("savedEntityDiagnostics", []),
                        frozen_fixture.get("savedEntityDiagnostics", []),
                    ),
                }
            )
            continue

        if current_java_state and current_java_state != current_rust_state:
            rust_mismatches.append(
                {
                    "fixture": fixture_id,
                    "classification": "Rust bug",
                    "reason": "current Rust stable entity state differs from current Java for the same payload",
                    "currentJava": current_java_state,
                    "currentRust": current_rust_state,
                    "fieldDiffs": bounded_field_diffs(
                        current_fixture.get("currentJavaSavedEntityDiagnostics", []),
                        current_fixture.get("savedEntityDiagnostics", []),
                        frozen_fixture.get("savedEntityDiagnostics", []),
                    ),
                }
            )
            continue

        if current_java_state and frozen_state and current_java_state != frozen_state:
            stable_state_mismatches.append(
                {
                    "fixture": fixture_id,
                    "classification": "current/frozen Java behavioral drift",
                    "reason": "frozen Java stable entity state differs from current Java for the same payload",
                    "currentJava": current_java_state,
                    "currentRust": current_rust_state,
                    "frozenJava": frozen_state,
                    "fieldDiffs": bounded_field_diffs(
                        current_fixture.get("currentJavaSavedEntityDiagnostics", []),
                        current_fixture.get("savedEntityDiagnostics", []),
                        frozen_fixture.get("savedEntityDiagnostics", []),
                    ),
                }
            )
            continue

        if current_java_saved and frozen_saved and current_java_saved != frozen_saved:
            serialization_drifts.append(
                {
                    "fixture": fixture_id,
                    "classification": "current/frozen Java serialization drift",
                    "reason": "current Java and Rust match; frozen Java re-saves a different NBT shape for equivalent stable entity state",
                    "currentJava": current_java_saved,
                    "currentRust": current_rust_saved,
                    "frozenJava": frozen_saved,
                    "fieldDiffs": bounded_field_diffs(
                        current_fixture.get("currentJavaSavedEntityDiagnostics", []),
                        current_fixture.get("savedEntityDiagnostics", []),
                        frozen_fixture.get("savedEntityDiagnostics", []),
                    ),
                }
            )

    return {
        "allMatch": not rust_mismatches and not stable_state_mismatches and not missing,
        "rustCurrentParity": not rust_mismatches,
        "stableEntityParity": not stable_state_mismatches,
        "mismatches": rust_mismatches + stable_state_mismatches,
        "serializationDrift": serialization_drifts,
        "missingFixtures": missing,
        "fixturesCompared": len(fixture_ids),
    }


def bounded_field_diffs(current_java: list[dict[str, Any]], current_rust: list[dict[str, Any]], frozen_java: list[dict[str, Any]], limit: int = 48) -> list[dict[str, Any]]:
    current_java_fields = diagnostic_field_map(current_java)
    current_rust_fields = diagnostic_field_map(current_rust)
    frozen_java_fields = diagnostic_field_map(frozen_java)
    keys = sorted(set(current_java_fields) | set(current_rust_fields) | set(frozen_java_fields))
    diffs: list[dict[str, Any]] = []
    for key in keys:
        values = {
            "currentJava": current_java_fields.get(key),
            "currentRust": current_rust_fields.get(key),
            "frozenJava": frozen_java_fields.get(key),
        }
        present_values = {value for value in values.values() if value is not None}
        if len(present_values) <= 1 and all(value is not None for value in values.values()):
            continue
        diffs.append({"path": key, **values})
        if len(diffs) >= limit:
            break
    return diffs


def diagnostic_field_map(entities: list[dict[str, Any]]) -> dict[str, str]:
    fields: dict[str, str] = {}
    for entity in entities:
        prefix = f"entity[{entity.get('rootIndex', '?')}]"
        for key in ["type", "uuid", "x", "y", "z", "yRot", "xRot", "directPassengers", "totalPassengers"]:
            if key in entity:
                fields[f"{prefix}.{key}"] = str(entity[key])
        for field in entity.get("fields", []):
            fields[f"{prefix}{field.get('path', '')}"] = str(field.get("value"))
    return fields


def write_markdown(report: dict[str, Any], path: Path) -> None:
    ratios = report["summary"].get("ratiosCurrentOverFrozen", {})
    lines = [
        "# Deterministic Entity Storage Replay",
        "",
        f"- Generated: `{report['createdUtc']}`",
        f"- Preset: `{report['preset']}`",
        f"- Baseline hash: `{report['baselineHash']}`",
        f"- Trace: `{report['tracePath']}`",
        f"- Semantic parity: `{report['semanticParity']['allMatch']}`",
        f"- Rust/current-Java parity: `{report['semanticParity'].get('rustCurrentParity')}`",
        f"- Stable current/frozen entity parity: `{report['semanticParity'].get('stableEntityParity')}`",
        f"- Current/frozen serialization drift fixtures: `{len(report['semanticParity'].get('serializationDrift', []))}`",
        "",
        "## Key Ratios",
        "",
    ]
    for name in [
        "complete.entity_load_path",
        "complete.iteration_with_validation",
        "java.decompress_nbt_parse",
        "java.entity_load",
        "rust.complete_native_read_decode",
        "rust.tape_indexing",
        "rust.entity_load",
        "complete.entity_save_write_path",
        "rust.entity_save_tape",
        "rust.entity_chunk_encode_write",
        "java.entity_save_compound",
        "java.entity_chunk_encode_write",
    ]:
        if name in ratios:
            ratio = ratios[name]
            direction = "faster" if ratio < 1.0 else "slower"
            lines.append(f"- `{name}`: current/frozen `{ratio:.3f}` ({direction})")
    if report["semanticParity"].get("mismatches"):
        lines.extend(["", "## Blocking Mismatches", ""])
        for mismatch in report["semanticParity"]["mismatches"][:12]:
            lines.append(f"- `{mismatch['fixture']}`: {mismatch['classification']} - {mismatch['reason']}")
    if report["semanticParity"].get("serializationDrift"):
        lines.extend(["", "## Serialization Drift", ""])
        for drift in report["semanticParity"]["serializationDrift"][:12]:
            lines.append(f"- `{drift['fixture']}`: {drift['reason']}")
    lines.extend(["", "## Artifacts", "", f"- JSON: `{report['jsonPath']}`", f"- Trace directory: `{report['traceDir']}`"])
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preset", choices=["quick", "development", "authoritative"], default="development")
    parser.add_argument("--world", default=DEFAULT_WORLD)
    parser.add_argument("--warmup", type=int, default=DEFAULT_WARMUP)
    parser.add_argument("--measure", type=int, default=DEFAULT_MEASURE)
    parser.add_argument("--max-real-chunks", type=int, default=DEFAULT_REAL_CHUNKS)
    parser.add_argument("--timeout", type=int, default=1800)
    parser.add_argument("--output-dir", type=Path, default=repo_root() / "build" / "entity-storage-replay")
    parser.add_argument("--trace", type=Path, help="reuse an existing entity replay trace")
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
    run_result_dir = artifact_dir / "run-results"
    scratch_dir = artifact_dir / "scratch"
    artifact_dir.mkdir(parents=True, exist_ok=True)
    trace_dir.mkdir(parents=True, exist_ok=True)
    run_result_dir.mkdir(parents=True, exist_ok=True)
    scratch_dir.mkdir(parents=True, exist_ok=True)

    baseline_hash = ensure_baseline(source_world, baseline_world)
    trace_path = args.trace.resolve() if args.trace else trace_dir / "entity_replay_trace.json"
    release_build = force_release_rust_build(current_repo, artifact_dir, args.timeout)
    frozen_init = inject_frozen_benchmark(current_repo, frozen_repo, artifact_dir)
    trace_generation = None
    if args.trace is None:
        trace_generation = run_trace_generation(current_repo, baseline_world, trace_path, args.max_real_chunks, args.timeout)

    run_outputs: list[dict[str, Any]] = []
    for spec in preset_runs(args.preset, current, frozen):
        target_world = spec.target.root / "run" / "saves" / args.world
        restored_hash = ensure_baseline(baseline_world, target_world) if not target_world.exists() else None
        copy_tree(baseline_world, target_world)
        restored_hash = hash_tree(target_world)
        if restored_hash != baseline_hash:
            raise SystemExit(f"Baseline restore hash mismatch for {spec.target.name}: {restored_hash} != {baseline_hash}")
        output_path = run_result_dir / f"{spec.index:02d}-{spec.target.name}.json"
        scratch = scratch_dir / f"{spec.index:02d}-{spec.target.name}"
        result = run_gradle_replay(spec, trace_path, args.world, output_path, scratch, args.warmup, args.measure, args.timeout, frozen_init)
        run_outputs.append(
            {
                "runIndex": spec.index,
                "target": spec.target.name,
                "implementation": spec.target.implementation,
                "repo": str(spec.target.root),
                "commit": commit_id(spec.target.root),
                "statusShort": git_status(spec.target.root),
                "rustNative": native_identity(spec.target.root) if spec.target.name == "current" else None,
                "restoredHash": restored_hash,
                "outputPath": str(output_path),
                "log": result["log"],
                "processSeconds": result["processSeconds"],
                "result": result,
            }
        )

    report_path = artifact_dir / "entity_storage_replay_report.json"
    markdown_path = artifact_dir / "entity_storage_replay_report.md"
    raw_results = [run["result"] for run in run_outputs]
    report = {
        "createdUtc": utc_now(),
        "preset": args.preset,
        "baselineWorld": str(baseline_world),
        "baselineHash": baseline_hash,
        "tracePath": str(trace_path),
        "traceDir": str(trace_dir),
        "traceGeneration": trace_generation,
        "releaseRustBuild": release_build,
        "jsonPath": str(report_path),
        "currentRepo": str(current_repo),
        "frozenRepo": str(frozen_repo),
        "runs": run_outputs,
        "summary": summarize_runs(raw_results),
        "semanticParity": compare_fingerprints(raw_results),
        "notes": [
            "Minecraft is not launched; each process replays exact current-version entity chunk payload bytes through scratch region files.",
            "The frozen benchmark source is injected under the frozen repo build directory with a Gradle init script; production frozen sources are not changed.",
            "Validation fingerprints come from re-saved entity trees built with runtime registries and a controlled mocked Level.",
        ],
    }
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    write_markdown(report, markdown_path)
    print(f"Report: {report_path}")
    print(f"Markdown: {markdown_path}")
    print(f"Semantic parity: {report['semanticParity']['allMatch']}")
    return 0 if report["semanticParity"]["allMatch"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
