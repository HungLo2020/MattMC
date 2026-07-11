#!/usr/bin/env bash

# Runs the chunk-meshing hot-path benchmark for the frozen Java baseline and
# the current Rust-backed MattMC checkout, strictly one after the other, then
# prints a comparison table from the generated JSON results.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

while [ ! -f "$PROJECT_ROOT/gradlew" ] && [ "$PROJECT_ROOT" != "/" ]; do
    PROJECT_ROOT="$(dirname "$PROJECT_ROOT")"
done

if [ ! -f "$PROJECT_ROOT/gradlew" ]; then
    echo "ERROR: Could not find gradlew. Are you in the MattMC project?" >&2
    exit 1
fi

usage() {
    cat <<'EOF'
Usage: ./DevUtils/PerfTesting/RunChunkMeshingPerfTest.sh [options]

Runs frozen Java and current Rust chunk-meshing benchmarks sequentially, then
prints a comparison table. The two benchmark runs are never launched at the
same time.

Options:
  --old-repo PATH       Frozen Java repo path.
                        Default: /home/matt/Documents/Repos/MattMC_JavaPerfTesting/MattMC
  --artifact-dir PATH   Directory for result JSON files.
                        Default: build/perf/chunk-meshing-compare/<timestamp>
  --quads N            Number of synthetic quads. Default: 32768
  --warmup N           Warmup iterations. Default: 8
  --iterations N       Measured iterations. Default: 25
  --warmup-ms N        Minimum time-based warmup per benchmark. Default: 1500
  --measure-ms N       Minimum time-based measurement per benchmark. Default: 2500
  --forks N            Independent JVM/process forks per implementation. Default: 3
  --rust-profile NAME  Cargo profile for current native build. Default: release
  -h, --help           Show this help.

Environment overrides:
  MATTMC_JAVA_PERF_REPO   Alternative default for --old-repo.
  MATTMC_PERF_QUADS       Alternative default for --quads.
  MATTMC_PERF_WARMUP      Alternative default for --warmup.
  MATTMC_PERF_ITERATIONS  Alternative default for --iterations.
  MATTMC_PERF_WARMUP_MS   Alternative default for --warmup-ms.
  MATTMC_PERF_MEASURE_MS  Alternative default for --measure-ms.
  MATTMC_PERF_FORKS       Alternative default for --forks.
EOF
}

timestamp="$(date +%Y%m%d-%H%M%S)"
OLD_REPO="${MATTMC_JAVA_PERF_REPO:-/home/matt/Documents/Repos/MattMC_JavaPerfTesting/MattMC}"
ARTIFACT_DIR="$PROJECT_ROOT/build/perf/chunk-meshing-compare/$timestamp"
QUADS="${MATTMC_PERF_QUADS:-32768}"
WARMUP="${MATTMC_PERF_WARMUP:-8}"
ITERATIONS="${MATTMC_PERF_ITERATIONS:-25}"
WARMUP_MS="${MATTMC_PERF_WARMUP_MS:-1500}"
MEASURE_MS="${MATTMC_PERF_MEASURE_MS:-2500}"
FORKS="${MATTMC_PERF_FORKS:-3}"
RUST_PROFILE="${MATTMC_RUST_PROFILE:-release}"

while [ "$#" -gt 0 ]; do
    case "$1" in
        --old-repo)
            OLD_REPO="${2:-}"
            shift 2
            ;;
        --artifact-dir)
            ARTIFACT_DIR="${2:-}"
            shift 2
            ;;
        --quads)
            QUADS="${2:-}"
            shift 2
            ;;
        --warmup)
            WARMUP="${2:-}"
            shift 2
            ;;
        --iterations)
            ITERATIONS="${2:-}"
            shift 2
            ;;
        --warmup-ms)
            WARMUP_MS="${2:-}"
            shift 2
            ;;
        --measure-ms)
            MEASURE_MS="${2:-}"
            shift 2
            ;;
        --forks)
            FORKS="${2:-}"
            shift 2
            ;;
        --rust-profile)
            RUST_PROFILE="${2:-}"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage
            exit 1
            ;;
    esac
done

for value_name in QUADS WARMUP ITERATIONS WARMUP_MS MEASURE_MS FORKS; do
    value="${!value_name}"
    if ! [[ "$value" =~ ^[0-9]+$ ]]; then
        echo "ERROR: $value_name must be a non-negative integer, got: $value" >&2
        exit 1
    fi
done

if [[ "$OLD_REPO" != /* ]]; then
    OLD_REPO="$PWD/$OLD_REPO"
fi
if [[ "$ARTIFACT_DIR" != /* ]]; then
    ARTIFACT_DIR="$PWD/$ARTIFACT_DIR"
fi

OLD_SCRIPT="$OLD_REPO/DevUtils/PerfTesting/RunChunkMeshingPerfTest.sh"
if [ ! -x "$OLD_SCRIPT" ]; then
    echo "ERROR: Frozen Java benchmark script is missing or not executable: $OLD_SCRIPT" >&2
    exit 1
fi

mkdir -p "$ARTIFACT_DIR/old-java" "$ARTIFACT_DIR/current-rust"

SUMMARY_OUTPUT="$ARTIFACT_DIR/chunk-meshing-comparison-summary.json"
REPORT_OUTPUT="$ARTIFACT_DIR/chunk-meshing-comparison-report.txt"
rm -f "$SUMMARY_OUTPUT" "$REPORT_OUTPUT"

echo "Chunk meshing perf comparison"
echo "  frozen Java repo: $OLD_REPO"
echo "  current repo:     $PROJECT_ROOT"
echo "  artifact dir:     $ARTIFACT_DIR"
echo "  quads:            $QUADS"
echo "  warmup:           $WARMUP"
echo "  iterations:       $ITERATIONS"
echo "  warmup-ms:        $WARMUP_MS"
echo "  measure-ms:       $MEASURE_MS"
echo "  forks:            $FORKS"
echo "  rust profile:     $RUST_PROFILE"
echo ""
for ((fork=1; fork<=FORKS; fork++)); do
    OLD_OUTPUT="$ARTIFACT_DIR/old-java/chunk-meshing-hotpaths-fork-$fork.json"
    CURRENT_OUTPUT="$ARTIFACT_DIR/current-rust/chunk-meshing-hotpaths-fork-$fork.json"
    rm -f "$OLD_OUTPUT" "$CURRENT_OUTPUT"

    if (( fork % 2 == 1 )); then
        ORDER=(old current)
    else
        ORDER=(current old)
    fi

    echo "Fork $fork/$FORKS order: ${ORDER[*]}"
    for target in "${ORDER[@]}"; do
        if [ "$target" = old ]; then
            echo "  running frozen Java..."
            "$OLD_SCRIPT" \
                --quads "$QUADS" \
                --warmup "$WARMUP" \
                --iterations "$ITERATIONS" \
                --warmup-ms "$WARMUP_MS" \
                --measure-ms "$MEASURE_MS" \
                --fork-index "$fork" \
                --output "$OLD_OUTPUT"
            if [ ! -f "$OLD_OUTPUT" ]; then
                echo "ERROR: Frozen Java benchmark did not write expected result JSON: $OLD_OUTPUT" >&2
                exit 1
            fi
        else
            echo "  running current Rust-backed..."
            cd "$PROJECT_ROOT"
            PERF_JAVA_TOOL_OPTIONS="-Dmattmc.runPerfBenchmarks=true -Dmattmc.perf.quads=$QUADS -Dmattmc.perf.warmup=$WARMUP -Dmattmc.perf.iterations=$ITERATIONS -Dmattmc.perf.warmupMillis=$WARMUP_MS -Dmattmc.perf.measureMillis=$MEASURE_MS -Dmattmc.perf.forkIndex=$fork -Dmattmc.perf.output=$CURRENT_OUTPUT"
            if [ -n "${JAVA_TOOL_OPTIONS:-}" ]; then
                PERF_JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS $PERF_JAVA_TOOL_OPTIONS"
            fi
            env \
                MATTMC_RUN_PERF_BENCHMARKS=true \
                JAVA_TOOL_OPTIONS="$PERF_JAVA_TOOL_OPTIONS" \
                ./gradlew test --tests net.sodium.client.perf.ChunkMeshingHotPathBenchmarkTest -PmattmcRustProfile="$RUST_PROFILE" --rerun-tasks --no-daemon
            if [ ! -f "$CURRENT_OUTPUT" ]; then
                echo "ERROR: Current Rust benchmark did not write expected result JSON: $CURRENT_OUTPUT" >&2
                exit 1
            fi
        fi
    done
done

python3 - "$ARTIFACT_DIR" "$SUMMARY_OUTPUT" "$REPORT_OUTPUT" <<'PY'
import json
import sys
import statistics
from pathlib import Path

artifact_dir = Path(sys.argv[1])
summary_path = Path(sys.argv[2])
report_path = Path(sys.argv[3])

def load_all(kind):
    paths = sorted((artifact_dir / kind).glob("chunk-meshing-hotpaths-fork-*.json"))
    runs = []
    for path in paths:
        data = json.loads(path.read_text(encoding="utf-8"))
        data["_path"] = str(path)
        runs.append(data)
    return runs

old_runs = load_all("old-java")
current_runs = load_all("current-rust")
if not old_runs or not current_runs:
    raise SystemExit("missing benchmark fork outputs")

def by_name(runs):
    out = {}
    for run in runs:
        for result in run["results"]:
            out.setdefault(result["name"], []).append(result)
    return out

old_results = by_name(old_runs)
current_results = by_name(current_runs)
names = [name for name in sorted(old_results) if name in current_results]

def verdict_for(time_ratio):
    if time_ratio < 1.0:
        return f"Rust {1.0 / time_ratio:.2f}x faster"
    if time_ratio > 1.0:
        return f"Rust {time_ratio:.2f}x slower"
    return "Tie"

def label(name):
    return name.replace("_", " ")

def fmt_ms(value):
    return f"{value:8.3f} ms"

def fmt_rate(value):
    return f"{value:8.1f} Mq/s"

def fmt_ratio(value):
    return f"{value:8.2f}x"

def fmt_bytes(value):
    if value is None:
        return "     n/a"
    sign = "-" if value < 0 else ""
    value = abs(value)
    units = [
        ("GiB", 1024 ** 3),
        ("MiB", 1024 ** 2),
        ("KiB", 1024),
    ]
    for suffix, factor in units:
        if value >= factor:
            return f"{sign}{value / factor:7.2f} {suffix}"
    return f"{sign}{value:7.0f} B"

def memory_value(result, key):
    return result.get("memory", {}).get(key)

def median_nested(forks, group, key, default=0.0):
    values = [fork.get(group, {}).get(key, default) for fork in forks]
    return statistics.median(values) if values else default

comparisons = []
for name in names:
    old_forks = old_results[name]
    current_forks = current_results[name]
    old_mean = statistics.median(r["mean_ms"] for r in old_forks)
    current_mean = statistics.median(r["mean_ms"] for r in current_forks)
    old_throughput = statistics.median(r["mega_quads_per_second"] for r in old_forks)
    current_throughput = statistics.median(r["mega_quads_per_second"] for r in current_forks)
    time_ratio = current_mean / old_mean if old_mean else float("nan")
    throughput_ratio = current_throughput / old_throughput if old_throughput else float("nan")
    comparisons.append({
        "name": name,
        "java_median_mean_ms": old_mean,
        "rust_median_mean_ms": current_mean,
        "rust_vs_java_time_ratio": time_ratio,
        "java_median_throughput_mqps": old_throughput,
        "rust_median_throughput_mqps": current_throughput,
        "rust_vs_java_throughput_ratio": throughput_ratio,
        "java_forks": old_forks,
        "rust_forks": current_forks,
    })

summary = {
    "artifact_dir": str(artifact_dir),
    "old_implementation": old_runs[0].get("implementation", "unknown"),
    "current_implementation": current_runs[0].get("implementation", "unknown"),
    "forks": min(len(old_runs), len(current_runs)),
    "quad_count": current_runs[0].get("quad_count"),
    "warmup_iterations_minimum": current_runs[0].get("warmup_iterations"),
    "measurement_iterations_minimum": current_runs[0].get("measure_iterations"),
    "warmup_millis": current_runs[0].get("warmup_millis"),
    "measure_millis": current_runs[0].get("measure_millis"),
    "limitations": current_runs[0].get("benchmark_limitations", []),
    "comparisons": comparisons,
    "old_json_paths": [run["_path"] for run in old_runs],
    "current_json_paths": [run["_path"] for run in current_runs],
}
summary_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")

lines = []
lines.append("Chunk Meshing Replay/Hot-Path Results")
lines.append("=====================================")
lines.append(f"Old implementation:     {summary['old_implementation']}")
lines.append(f"Current implementation: {summary['current_implementation']}")
lines.append(f"Forks:                  {summary['forks']}")
lines.append(f"Warmup:                 {summary['warmup_millis']} ms + at least {summary['warmup_iterations_minimum']} invocations")
lines.append(f"Measurement:            {summary['measure_millis']} ms + at least {summary['measurement_iterations_minimum']} samples")
lines.append("")
lines.append("Median of per-fork means")
lines.append("------------------------")
lines.append(f"{'benchmark':42} {'java ms':>10} {'rust ms':>10} {'ratio':>10} {'java samp':>10} {'rust samp':>10}")
for item in comparisons:
    jf = item["java_forks"]
    rf = item["rust_forks"]
    lines.append(f"{label(item['name'])[:42]:42} {item['java_median_mean_ms']:10.3f} {item['rust_median_mean_ms']:10.3f} {item['rust_vs_java_time_ratio']:10.2f} {int(statistics.median(r['samples'] for r in jf)):10d} {int(statistics.median(r['samples'] for r in rf)):10d}")
lines.append("")
lines.append("Current-path stage timing, median ms/invocation")
lines.append("-----------------------------------------------")
for item in comparisons:
    if not item["name"].startswith("replay_section_"):
        continue
    stage_names = sorted({name for fork in item["rust_forks"] for name in fork.get("stage_timing_ms_per_invocation", {})})
    if not stage_names:
        continue
    lines.append(label(item["name"]))
    for stage in stage_names:
        values = [fork["stage_timing_ms_per_invocation"][stage] for fork in item["rust_forks"] if stage in fork.get("stage_timing_ms_per_invocation", {})]
        if values:
            lines.append(f"  {stage:32} {statistics.median(values):9.4f}")
lines.append("")
lines.append("Boundary/accounting, median per invocation")
lines.append("------------------------------------------")
lines.append(f"{'benchmark':42} {'java calls':>10} {'rust calls':>10} {'rust abi':>13} {'rust out':>13} {'rust fbq':>9} {'rust gc':>8}")
for item in comparisons:
    java_calls = median_nested(item["java_forks"], "accounting", "native_calls_per_invocation")
    rust_calls = median_nested(item["rust_forks"], "accounting", "native_calls_per_invocation")
    rust_abi = median_nested(item["rust_forks"], "accounting", "abi_payload_bytes_per_invocation")
    rust_out = median_nested(item["rust_forks"], "accounting", "output_vertex_bytes_per_invocation")
    rust_fallback_quads = median_nested(item["rust_forks"], "accounting", "fallback_like_quads_per_invocation")
    rust_gc = median_nested(item["rust_forks"], "gc", "collection_count_delta")
    lines.append(f"{label(item['name'])[:42]:42} {java_calls:10.1f} {rust_calls:10.1f} {fmt_bytes(rust_abi):>13} {fmt_bytes(rust_out):>13} {rust_fallback_quads:9.1f} {rust_gc:8.0f}")
lines.append("")
lines.append("Artifacts")
lines.append("---------")
lines.append(f"Summary JSON: {summary_path}")
lines.append(f"Report:       {report_path}")
lines.append("Frozen Java fork JSONs:")
for path in summary["old_json_paths"]:
    lines.append(f"  {path}")
lines.append("Current Rust fork JSONs:")
for path in summary["current_json_paths"]:
    lines.append(f"  {path}")
lines.append("")
lines.append("Limitations")
lines.append("-----------")
for limitation in summary["limitations"]:
    lines.append(f"- {limitation}")

report = "\n".join(lines) + "\n"
report_path.write_text(report, encoding="utf-8")
print("")
print(report)
PY
