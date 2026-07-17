#!/usr/bin/env python3
"""Cross-platform harness for MattMC Java-to-Rust migration test workloads."""

from __future__ import annotations

import argparse
import json
import os
import platform
import shutil
import signal
import statistics
import subprocess
import sys
import time
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, Sequence


WORKLOADS = ("metadata", "gradle-test", "chunk-meshing-hotpath")
TARGETS = ("current", "frozen", "both")
BENCHMARK_FORCE_MODES = ("clean-test", "none", "rerun-tasks")
DEFAULT_TIMEOUT_SECONDS = 900
BENCHMARK_OUTPUT = "chunk-meshing-hotpath.json"


@dataclass(frozen=True)
class RepoTarget:
    name: str
    root: Path
    role: str


@dataclass
class CommandResult:
    target: str
    workload: str
    success: bool
    timed_out: bool
    exit_code: int | None
    command: list[str]
    artifact_dir: str
    metadata_path: str
    stdout_path: str | None
    stderr_path: str | None
    started_at: str
    ended_at: str
    duration_seconds: float
    error: str = ""


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def timestamp() -> str:
    return datetime.now().strftime("%Y%m%d-%H%M%S")


def script_dir() -> Path:
    return Path(__file__).resolve().parent


def repo_root(start: Path | None = None) -> Path:
    current = (start or script_dir()).resolve()
    if current.is_file():
        current = current.parent
    while current != current.parent:
        if (current / "gradlew").is_file() or (current / "gradlew.bat").is_file():
            return current
        current = current.parent
    raise SystemExit(f"Could not find a MattMC repository root from {start or script_dir()}")


def platform_name() -> str:
    raw = platform.system().strip().lower()
    if raw.startswith(("cygwin", "mingw", "msys")):
        return "windows"
    if raw == "darwin":
        return "macos"
    if raw == "windows":
        return "windows"
    if raw == "linux":
        return "linux"
    return raw or "unknown"


def directory_helper(root: Path) -> Path | None:
    helper = root / "DevUtils" / "Common" / "platform" / "directory" / "directory_helper.py"
    return helper if helper.is_file() else None


def resolve_named_directory(root: Path, name: str, requested_platform: str | None = None) -> Path:
    helper = directory_helper(root)
    if helper is None:
        raise SystemExit(f"Directory helper is missing in repository: {root}")
    command = [sys.executable, str(helper), name]
    if requested_platform:
        command.extend(["--platform", requested_platform])
    result = subprocess.run(
        command,
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != 0:
        message = result.stderr.strip() or result.stdout.strip()
        raise SystemExit(message or f"Could not resolve directory: {name}")
    return Path(result.stdout.strip()).resolve()


def find_frozen_repo(current_root: Path, explicit: str | None = None) -> Path:
    if explicit:
        return Path(explicit).expanduser().resolve()
    env_path = os.environ.get("MATTMC_JAVA_PERF_REPO") or os.environ.get("MATTMC_FROZEN_JAVA_REPO")
    if env_path:
        return Path(env_path).expanduser().resolve()
    return resolve_named_directory(current_root, "java_perf_repo")


def validate_repo(path: Path, label: str) -> None:
    if not path.is_dir():
        raise SystemExit(f"{label} repository does not exist: {path}")
    if not ((path / "gradlew").is_file() or (path / "gradlew.bat").is_file()):
        raise SystemExit(f"{label} repository is missing a Gradle wrapper: {path}")
    if not (path / ".git").is_dir():
        raise SystemExit(f"{label} repository is missing .git: {path}")


def select_targets(args: argparse.Namespace) -> list[RepoTarget]:
    current = repo_root()
    frozen = find_frozen_repo(current, args.frozen_repo)
    validate_repo(current, "Current")
    validate_repo(frozen, "Frozen Java")
    if args.target == "current":
        return [RepoTarget("current", current, "current")]
    if args.target == "frozen":
        return [RepoTarget("frozen", frozen, "frozen")]
    return [
        RepoTarget("current", current, "current"),
        RepoTarget("frozen", frozen, "frozen"),
    ]


def gradle_wrapper(root: Path) -> list[str]:
    if platform_name() == "windows" and (root / "gradlew.bat").is_file():
        return [str(root / "gradlew.bat")]
    if (root / "gradlew").is_file():
        return [str(root / "gradlew")]
    if (root / "gradlew.bat").is_file():
        return [str(root / "gradlew.bat")]
    raise SystemExit(f"No Gradle wrapper found in {root}")


def git_output(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    return result.stdout.strip() if result.returncode == 0 else ""


def dirty_status(root: Path) -> dict[str, object]:
    status = git_output(root, "status", "--short")
    return {
        "dirty": bool(status),
        "status_short": status.splitlines(),
    }


def java_version() -> str:
    java = shutil.which("java")
    if not java:
        return "java not found"
    result = subprocess.run(
        [java, "-version"],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    return (result.stderr or result.stdout).strip()


def repository_metadata(target: RepoTarget) -> dict[str, object]:
    status = dirty_status(target.root)
    return {
        "target": target.name,
        "role": target.role,
        "path": str(target.root),
        "branch": git_output(target.root, "branch", "--show-current"),
        "commit": git_output(target.root, "rev-parse", "HEAD"),
        "dirty": status["dirty"],
        "status_short": status["status_short"],
    }


def base_metadata(target: RepoTarget, workload: str, command: Sequence[str], args: argparse.Namespace) -> dict[str, object]:
    return {
        "schema": "mattmc-rust-migration-harness-v1",
        "workload": workload,
        "repository": repository_metadata(target),
        "host": {
            "platform": platform_name(),
            "raw_platform": platform.system(),
            "arch": platform.machine(),
            "python": sys.version.split()[0],
            "java_version": java_version(),
        },
        "rust_profile": args.rust_profile if target.role == "current" else None,
        "command": list(command),
        "timeout_seconds": args.timeout_seconds,
        "jvm_args": args.jvm_arg,
        "benchmark_force_mode": args.benchmark_force_mode if workload == "chunk-meshing-hotpath" else None,
        "fork_index": args.fork_index if workload == "chunk-meshing-hotpath" else None,
    }


def merged_env(extra: dict[str, str], jvm_args: Iterable[str]) -> dict[str, str]:
    env = os.environ.copy()
    existing_java_tool_options = env.get("JAVA_TOOL_OPTIONS", "")
    workload_java_tool_options = extra.get("JAVA_TOOL_OPTIONS", "")
    merged_jvm = " ".join(
        [part for part in [existing_java_tool_options, workload_java_tool_options, *jvm_args] if part]
    )
    for key, value in extra.items():
        if key != "JAVA_TOOL_OPTIONS":
            env[key] = value
    if merged_jvm:
        env["JAVA_TOOL_OPTIONS"] = merged_jvm
    return env


def build_command(target: RepoTarget, workload: str, args: argparse.Namespace, output_json: Path) -> tuple[list[str], dict[str, str]]:
    if workload == "metadata":
        return [], {}
    if workload == "gradle-test":
        command = [*gradle_wrapper(target.root), "test", "--no-daemon", *args.gradle_arg]
        if target.role == "current":
            command.insert(1, f"-PmattmcRustProfile={args.rust_profile}")
        return command, {}
    if workload == "chunk-meshing-hotpath":
        jvm = [
            "-Dmattmc.runPerfBenchmarks=true",
            f"-Dmattmc.perf.quads={args.quads}",
            f"-Dmattmc.perf.warmup={args.warmup}",
            f"-Dmattmc.perf.iterations={args.iterations}",
            f"-Dmattmc.perf.warmupMillis={args.warmup_ms}",
            f"-Dmattmc.perf.measureMillis={args.measure_ms}",
            f"-Dmattmc.perf.forkIndex={args.fork_index}",
            f"-Dmattmc.perf.output={output_json}",
        ]
        benchmark_tasks = ["test"]
        if args.benchmark_force_mode == "clean-test":
            benchmark_tasks.insert(0, "cleanTest")
        command = [
            *gradle_wrapper(target.root),
            *benchmark_tasks,
            "--tests",
            "net.sodium.client.perf.ChunkMeshingHotPathBenchmarkTest",
            "--no-build-cache",
            "--no-daemon",
            *args.gradle_arg,
        ]
        if args.benchmark_force_mode == "rerun-tasks":
            command.insert(-len(args.gradle_arg) if args.gradle_arg else len(command), "--rerun-tasks")
        if target.role == "current":
            command.insert(1, f"-PmattmcRustProfile={args.rust_profile}")
        return command, {
            "MATTMC_RUN_PERF_BENCHMARKS": "true",
            "JAVA_TOOL_OPTIONS": " ".join(jvm),
        }
    raise SystemExit(f"Unsupported workload: {workload}")


def terminate_process_tree(process: subprocess.Popen[str]) -> None:
    if process.poll() is not None:
        return
    if platform_name() == "windows":
        subprocess.run(
            ["taskkill", "/PID", str(process.pid), "/T", "/F"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )
    else:
        try:
            os.killpg(process.pid, signal.SIGTERM)
            time.sleep(2)
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass


def popen_kwargs() -> dict[str, object]:
    if platform_name() == "windows":
        return {"creationflags": subprocess.CREATE_NEW_PROCESS_GROUP}
    return {"start_new_session": True}


def run_target(target: RepoTarget, workload: str, artifact_root: Path, args: argparse.Namespace) -> CommandResult:
    target_dir = artifact_root / target.name
    target_dir.mkdir(parents=True, exist_ok=True)
    output_json = target_dir / f"{workload}.json"
    command, extra_env = build_command(target, workload, args, output_json)
    metadata = base_metadata(target, workload, command, args)
    metadata["started_at"] = utc_now().isoformat()
    stdout_path = target_dir / "stdout.log" if command else None
    stderr_path = target_dir / "stderr.log" if command else None
    metadata_path = target_dir / "metadata.json"
    started = time.monotonic()

    success = True
    timed_out = False
    exit_code: int | None = 0
    error = ""

    if command and not args.dry_run:
        env = merged_env(extra_env, args.jvm_arg)
        with stdout_path.open("w", encoding="utf-8") as stdout, stderr_path.open("w", encoding="utf-8") as stderr:
            process = subprocess.Popen(
                command,
                cwd=target.root,
                text=True,
                stdout=stdout,
                stderr=stderr,
                env=env,
                **popen_kwargs(),
            )
            try:
                exit_code = process.wait(timeout=args.timeout_seconds)
            except subprocess.TimeoutExpired:
                timed_out = True
                terminate_process_tree(process)
                exit_code = process.wait(timeout=30)
                error = f"Timed out after {args.timeout_seconds}s"
            success = exit_code == 0 and not timed_out
            if success and workload == "chunk-meshing-hotpath" and not output_json.is_file():
                success = False
                error = f"Benchmark completed but did not write expected output: {output_json}"
    elif command and args.dry_run:
        success = True
        exit_code = 0
        metadata["dry_run"] = True

    ended = utc_now()
    duration = time.monotonic() - started
    metadata.update(
        {
            "ended_at": ended.isoformat(),
            "duration_seconds": duration,
            "success": success,
            "timed_out": timed_out,
            "exit_code": exit_code,
            "error": error,
            "stdout_path": str(stdout_path) if stdout_path else None,
            "stderr_path": str(stderr_path) if stderr_path else None,
            "workload_output": str(output_json) if output_json.exists() else None,
        }
    )
    metadata_path.write_text(json.dumps(metadata, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return CommandResult(
        target=target.name,
        workload=workload,
        success=success,
        timed_out=timed_out,
        exit_code=exit_code,
        command=list(command),
        artifact_dir=str(target_dir),
        metadata_path=str(metadata_path),
        stdout_path=str(stdout_path) if stdout_path else None,
        stderr_path=str(stderr_path) if stderr_path else None,
        started_at=metadata["started_at"],
        ended_at=metadata["ended_at"],
        duration_seconds=duration,
        error=error,
    )


def clone_args(args: argparse.Namespace, **updates: object) -> argparse.Namespace:
    values = vars(args).copy()
    values.update(updates)
    return argparse.Namespace(**values)


def ordered_targets(targets: list[RepoTarget], fork_number: int, alternate_order: bool) -> list[RepoTarget]:
    if alternate_order and len(targets) == 2 and fork_number % 2 == 0:
        return list(reversed(targets))
    return list(targets)


def benchmark_artifact_path(result: CommandResult) -> Path:
    return Path(result.artifact_dir) / BENCHMARK_OUTPUT


def load_benchmark_output(result: CommandResult) -> dict[str, object] | None:
    path = benchmark_artifact_path(result)
    if not path.is_file():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def numeric(value: object, default: float = 0.0) -> float:
    if isinstance(value, (int, float)):
        return float(value)
    return default


def median_or_none(values: list[float]) -> float | None:
    return statistics.median(values) if values else None


def summarize_values(values: list[float]) -> dict[str, object]:
    if not values:
        return {
            "count": 0,
            "min": None,
            "median": None,
            "max": None,
            "mean": None,
            "stddev": None,
            "coefficient_of_variation": None,
        }
    mean = statistics.mean(values)
    stddev = statistics.stdev(values) if len(values) > 1 else 0.0
    return {
        "count": len(values),
        "min": min(values),
        "median": statistics.median(values),
        "max": max(values),
        "mean": mean,
        "stddev": stddev,
        "coefficient_of_variation": (stddev / mean) if mean else None,
    }


def benchmark_category(name: str) -> str:
    if name.startswith("transfer_copy_"):
        return "transfer-copy"
    if name in {"compact_chunk_mesh_buffer_build", "translucent_quad_index_emission"}:
        return "support-kernel"
    if "replay" in name or name in {
        "dense_cube_terrain",
        "normal_surface_terrain",
        "foliage_tinted_models",
        "weighted_and_multipart_models",
        "waterlogged_geometry",
        "fluid_heavy",
        "translucent_heavy",
        "complex_modded_static_serializable",
    }:
        return "section-meshing"
    return "other"


def row_by_name(doc: dict[str, object]) -> dict[str, dict[str, object]]:
    rows = doc.get("results", [])
    if not isinstance(rows, list):
        return {}
    return {str(row.get("name")): row for row in rows if isinstance(row, dict) and row.get("name")}


def accounting(row: dict[str, object]) -> dict[str, object]:
    value = row.get("accounting")
    return value if isinstance(value, dict) else {}


def rows_equivalent(current: dict[str, object], frozen: dict[str, object]) -> bool:
    current_accounting = accounting(current)
    frozen_accounting = accounting(frozen)
    keys = (
        "output_quads_per_invocation",
        "output_vertex_bytes_per_invocation",
        "output_index_bytes_per_invocation",
    )
    return all(
        numeric(current_accounting.get(key), -1.0) == numeric(frozen_accounting.get(key), -2.0)
        for key in keys
    )


def fallback_contaminated(row: dict[str, object]) -> bool:
    row_accounting = accounting(row)
    return (
        numeric(row_accounting.get("fallback_like_blocks_total")) > 0
        or numeric(row_accounting.get("fallback_like_quads_total")) > 0
    )


def build_benchmark_aggregate(results: list[CommandResult]) -> dict[str, object] | None:
    docs_by_fork: dict[int, dict[str, dict[str, object]]] = {}
    output_paths: list[str] = []
    for result in results:
        if result.workload != "chunk-meshing-hotpath" or not result.success:
            continue
        doc = load_benchmark_output(result)
        if doc is None:
            continue
        fork = int(numeric(doc.get("fork_index"), 0.0))
        docs_by_fork.setdefault(fork, {})[result.target] = doc
        output_paths.append(str(benchmark_artifact_path(result)))
    if not docs_by_fork:
        return None

    all_names: set[str] = set()
    for target_docs in docs_by_fork.values():
        for doc in target_docs.values():
            all_names.update(row_by_name(doc).keys())

    row_summaries: list[dict[str, object]] = []
    for name in sorted(all_names):
        current_values: list[float] = []
        frozen_values: list[float] = []
        ratios: list[float] = []
        equivalence_failures: list[int] = []
        fallback_forks: list[int] = []
        current_native_profiles: list[dict[str, object]] = []

        for fork, target_docs in sorted(docs_by_fork.items()):
            current_doc = target_docs.get("current")
            frozen_doc = target_docs.get("frozen")
            current_row = row_by_name(current_doc).get(name) if current_doc else None
            frozen_row = row_by_name(frozen_doc).get(name) if frozen_doc else None
            if current_row is not None:
                current_values.append(numeric(current_row.get("median_ms")))
                profile = current_row.get("native_profile")
                if isinstance(profile, dict) and profile:
                    current_native_profiles.append(profile)
                if fallback_contaminated(current_row):
                    fallback_forks.append(fork)
            if frozen_row is not None:
                frozen_values.append(numeric(frozen_row.get("median_ms")))
                if fallback_contaminated(frozen_row):
                    fallback_forks.append(fork)
            if current_row is not None and frozen_row is not None:
                frozen_median = numeric(frozen_row.get("median_ms"))
                current_median = numeric(current_row.get("median_ms"))
                if frozen_median:
                    ratios.append(current_median / frozen_median)
                if not rows_equivalent(current_row, frozen_row):
                    equivalence_failures.append(fork)

        ratio_summary = summarize_values(ratios)
        classification = "incomplete"
        ratio_median = ratio_summary["median"]
        if current_values and frozen_values and not equivalence_failures and not fallback_forks:
            if isinstance(ratio_median, float):
                if ratio_median <= 0.95:
                    classification = "current-faster"
                elif ratio_median >= 1.05:
                    classification = "current-slower"
                else:
                    classification = "similar"
        elif fallback_forks:
            classification = "fallback-contaminated"
        elif equivalence_failures:
            classification = "work-not-equivalent"

        row_summaries.append(
            {
                "name": name,
                "category": benchmark_category(name),
                "classification": classification,
                "current_ms": summarize_values(current_values),
                "frozen_ms": summarize_values(frozen_values),
                "current_over_frozen_ratio": ratio_summary,
                "fallback_contaminated_forks": sorted(set(fallback_forks)),
                "work_equivalence_failed_forks": equivalence_failures,
                "current_native_profiles_observed": bool(current_native_profiles),
            }
        )

    headline_rows = [
        row
        for row in row_summaries
        if row["category"] == "section-meshing"
        and row["classification"] in {"current-faster", "current-slower", "similar"}
    ]
    return {
        "schema": "mattmc-chunk-meshing-comparison-aggregate-v1",
        "created_at": utc_now().isoformat(),
        "benchmark_outputs": output_paths,
        "forks_observed": sorted(docs_by_fork.keys()),
        "row_count": len(row_summaries),
        "headline_section_rows": len(headline_rows),
        "rows": row_summaries,
    }


def write_aggregate_files(artifact_root: Path, aggregate: dict[str, object] | None) -> tuple[Path | None, Path | None]:
    if aggregate is None:
        return None, None
    json_path = artifact_root / "aggregate_summary.json"
    json_path.write_text(json.dumps(aggregate, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    lines = [
        "MattMC chunk meshing cross-repo aggregate",
        f"forks_observed: {aggregate.get('forks_observed')}",
        "",
        "benchmark | category | classification | frozen median ms | current median ms | current/frozen",
        "--- | --- | --- | ---: | ---: | ---:",
    ]
    for row in aggregate.get("rows", []):
        if not isinstance(row, dict):
            continue
        frozen = row.get("frozen_ms", {})
        current = row.get("current_ms", {})
        ratio = row.get("current_over_frozen_ratio", {})
        lines.append(
            f"{row.get('name')} | {row.get('category')} | {row.get('classification')} | "
            f"{(frozen or {}).get('median')} | {(current or {}).get('median')} | {(ratio or {}).get('median')}"
        )
    text_path = artifact_root / "aggregate_report.md"
    text_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return json_path, text_path


def write_combined_manifest(
    artifact_root: Path,
    results: list[CommandResult],
    args: argparse.Namespace,
    aggregate_path: Path | None = None,
    aggregate_report_path: Path | None = None,
) -> Path:
    manifest = {
        "schema": "mattmc-rust-migration-comparison-v1",
        "created_at": utc_now().isoformat(),
        "workload": args.workload,
        "artifact_dir": str(artifact_root),
        "success": all(result.success for result in results),
        "forks": args.forks if args.workload == "chunk-meshing-hotpath" else None,
        "alternate_order": args.alternate_order if args.workload == "chunk-meshing-hotpath" else None,
        "aggregate_summary": str(aggregate_path) if aggregate_path else None,
        "aggregate_report": str(aggregate_report_path) if aggregate_report_path else None,
        "results": [asdict(result) for result in results],
    }
    path = artifact_root / "combined_manifest.json"
    path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return path


def non_negative_int(value: str) -> int:
    parsed = int(value)
    if parsed < 0:
        raise argparse.ArgumentTypeError("value must be non-negative")
    return parsed


def positive_int(value: str) -> int:
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("value must be positive")
    return parsed


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", choices=TARGETS, default="both")
    parser.add_argument("--workload", choices=WORKLOADS, default="metadata")
    parser.add_argument("--frozen-repo", help="override frozen Java repository path")
    parser.add_argument("--artifact-dir", type=Path)
    parser.add_argument("--timeout-seconds", type=non_negative_int, default=DEFAULT_TIMEOUT_SECONDS)
    parser.add_argument("--rust-profile", default="release")
    parser.add_argument("--jvm-arg", action="append", default=[])
    parser.add_argument("--gradle-arg", action="append", default=[])
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--quads", type=non_negative_int, default=32768)
    parser.add_argument("--warmup", type=non_negative_int, default=8)
    parser.add_argument("--iterations", type=non_negative_int, default=25)
    parser.add_argument("--warmup-ms", type=non_negative_int, default=1500)
    parser.add_argument("--measure-ms", type=non_negative_int, default=2500)
    parser.add_argument("--fork-index", type=non_negative_int, default=0)
    parser.add_argument(
        "--forks",
        type=positive_int,
        default=1,
        help="Number of chunk-meshing-hotpath forks to run. Each fork runs each selected target once.",
    )
    parser.add_argument(
        "--alternate-order",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Alternate current/frozen execution order between forks when --target both is selected.",
    )
    parser.add_argument(
        "--benchmark-force-mode",
        choices=BENCHMARK_FORCE_MODES,
        default="clean-test",
        help=(
            "How chunk-meshing-hotpath forces execution. clean-test deletes only test outputs before running the "
            "selected benchmark; none allows Gradle up-to-date skipping; rerun-tasks preserves the old heavy behavior."
        ),
    )
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    targets = select_targets(args)
    artifact_root = (
        args.artifact_dir.resolve()
        if args.artifact_dir
        else repo_root() / "build" / "perf" / "rust-migration-harness" / timestamp()
    )
    artifact_root.mkdir(parents=True, exist_ok=True)

    results: list[CommandResult] = []
    if args.workload == "chunk-meshing-hotpath" and args.forks > 1:
        for fork_number in range(1, args.forks + 1):
            fork_root = artifact_root / f"fork-{fork_number:02d}"
            fork_args = clone_args(args, fork_index=fork_number)
            for target in ordered_targets(targets, fork_number, args.alternate_order):
                results.append(run_target(target, args.workload, fork_root, fork_args))
    else:
        results = [run_target(target, args.workload, artifact_root, args) for target in targets]

    aggregate = build_benchmark_aggregate(results) if args.workload == "chunk-meshing-hotpath" else None
    aggregate_path, aggregate_report_path = write_aggregate_files(artifact_root, aggregate)
    manifest = write_combined_manifest(artifact_root, results, args, aggregate_path, aggregate_report_path)
    print(f"Wrote combined manifest: {manifest}")
    if aggregate_path:
        print(f"Wrote aggregate summary: {aggregate_path}")
    if aggregate_report_path:
        print(f"Wrote aggregate report: {aggregate_report_path}")
    for result in results:
        status = "ok" if result.success else "FAILED"
        print(f"{result.target}: {status} ({result.metadata_path})")
    return 0 if all(result.success for result in results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
