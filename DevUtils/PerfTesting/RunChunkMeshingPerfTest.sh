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
current_only_names = [name for name in sorted(current_results) if name not in old_results]
old_only_names = [name for name in sorted(old_results) if name not in current_results]

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

def median_stage(forks, stage):
    values = [
        fork.get("native_profile", {}).get("stage_nanos_per_invocation", {}).get(stage, 0.0)
        for fork in forks
    ]
    return statistics.median(values) if values else 0.0

def median_count(forks, count):
    values = [
        fork.get("native_profile", {}).get("counts_per_invocation", {}).get(count, 0.0)
        for fork in forks
    ]
    return statistics.median(values) if values else 0.0

def current_summary(name, forks):
    return {
        "name": name,
        "rust_median_mean_ms": statistics.median(r["mean_ms"] for r in forks),
        "rust_median_throughput_mqps": statistics.median(r["mega_quads_per_second"] for r in forks),
        "rust_forks": forks,
    }

comparisons = []
for name in names:
    old_forks = old_results[name]
    current_forks = current_results[name]
    old_mean = statistics.median(r["mean_ms"] for r in old_forks)
    current_mean = statistics.median(r["mean_ms"] for r in current_forks)
    old_throughput = statistics.median(r["mega_quads_per_second"] for r in old_forks)
    current_throughput = statistics.median(r["mega_quads_per_second"] for r in current_forks)
    old_quads = median_nested(old_forks, "accounting", "output_quads_per_invocation")
    current_quads = median_nested(current_forks, "accounting", "output_quads_per_invocation")
    old_vertex_bytes = median_nested(old_forks, "accounting", "output_vertex_bytes_per_invocation")
    current_vertex_bytes = median_nested(current_forks, "accounting", "output_vertex_bytes_per_invocation")
    old_index_bytes = median_nested(old_forks, "accounting", "output_index_bytes_per_invocation")
    current_index_bytes = median_nested(current_forks, "accounting", "output_index_bytes_per_invocation")
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
        "output_equivalent": old_quads == current_quads
            and old_vertex_bytes == current_vertex_bytes
            and old_index_bytes == current_index_bytes,
        "java_output_quads": old_quads,
        "rust_output_quads": current_quads,
        "java_output_vertex_bytes": old_vertex_bytes,
        "rust_output_vertex_bytes": current_vertex_bytes,
        "java_output_index_bytes": old_index_bytes,
        "rust_output_index_bytes": current_index_bytes,
        "java_forks": old_forks,
        "rust_forks": current_forks,
    })

current_only = [current_summary(name, current_results[name]) for name in current_only_names]
old_only = [
    {
        "name": name,
        "java_median_mean_ms": statistics.median(r["mean_ms"] for r in old_results[name]),
        "java_median_throughput_mqps": statistics.median(r["mega_quads_per_second"] for r in old_results[name]),
        "java_forks": old_results[name],
    }
    for name in old_only_names
]

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
    "current_only_results": current_only,
    "old_only_results": old_only,
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
def comparison_table(title, predicate):
    rows = [item for item in comparisons if predicate(item["name"])]
    if not rows:
        return
    lines.append(title)
    lines.append("-" * len(title))
    lines.append(f"{'benchmark':42} {'java ms':>10} {'rust ms':>10} {'ratio':>10} {'java samp':>10} {'rust samp':>10}")
    for item in rows:
        jf = item["java_forks"]
        rf = item["rust_forks"]
        lines.append(f"{label(item['name'])[:42]:42} {item['java_median_mean_ms']:10.3f} {item['rust_median_mean_ms']:10.3f} {item['rust_vs_java_time_ratio']:10.2f} {int(statistics.median(r['samples'] for r in jf)):10d} {int(statistics.median(r['samples'] for r in rf)):10d}")
    lines.append("")

comparison_table("Full end-to-end section replay", lambda name: name.startswith("full_section_replay_"))
comparison_table("Isolated packing and algorithm microbenchmarks",
        lambda name: not name.startswith("full_section_replay_")
        and not name.startswith("snapshot_create_")
        and not name.startswith("replay_section_")
        and not name.startswith("diagnostic_"))
comparison_table("Legacy native replay rows without snapshot creation",
        lambda name: name.startswith("replay_section_"))
lines.append("")
lines.append("Output equivalence, median per invocation")
lines.append("-----------------------------------------")
lines.append(f"{'benchmark':42} {'status':>8} {'java quads':>11} {'rust quads':>11} {'java out':>13} {'rust out':>13} {'java idx':>13} {'rust idx':>13}")
for item in comparisons:
    status = "ok" if item["output_equivalent"] else "DIFF"
    lines.append(f"{label(item['name'])[:42]:42} {status:>8} {item['java_output_quads']:11.1f} {item['rust_output_quads']:11.1f} {fmt_bytes(item['java_output_vertex_bytes']):>13} {fmt_bytes(item['rust_output_vertex_bytes']):>13} {fmt_bytes(item['java_output_index_bytes']):>13} {fmt_bytes(item['rust_output_index_bytes']):>13}")
if current_only:
    lines.append("")
    lines.append("Current-only diagnostic rows")
    lines.append("----------------------------")
    lines.append(f"{'benchmark':58} {'rust ms':>10} {'samples':>10} {'quads':>10} {'native calls':>12}")
    for item in current_only:
        rf = item["rust_forks"]
        quads = median_nested(rf, "accounting", "output_quads_per_invocation")
        calls = median_nested(rf, "accounting", "native_calls_per_invocation")
        lines.append(f"{label(item['name'])[:58]:58} {item['rust_median_mean_ms']:10.3f} {int(statistics.median(r['samples'] for r in rf)):10d} {quads:10.1f} {calls:12.1f}")
if old_only:
    lines.append("")
    lines.append("Frozen-only historical/prebuilt rows")
    lines.append("------------------------------------")
    lines.append(f"{'benchmark':58} {'java ms':>10} {'samples':>10} {'quads':>10}")
    for item in old_only:
        jf = item["java_forks"]
        quads = median_nested(jf, "accounting", "output_quads_per_invocation")
        lines.append(f"{label(item['name'])[:58]:58} {item['java_median_mean_ms']:10.3f} {int(statistics.median(r['samples'] for r in jf)):10d} {quads:10.1f}")
lines.append("")
lines.append("Current-path stage timing, median ms/invocation")
lines.append("-----------------------------------------------")
for item in comparisons + current_only:
    if not item["name"].startswith("replay_section_") and not item["name"].startswith("diagnostic_"):
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
lines.append("Rust native internal profile, median ms/invocation")
lines.append("--------------------------------------------------")
for item in comparisons + current_only:
    if not item["name"].startswith("replay_section_") and not item["name"].startswith("diagnostic_") and item["name"] != "native_section_scan_and_build":
        continue
    forks = item["rust_forks"]
    stages = [
        name
        for name in current_runs[0]["results"][0].get("native_profile", {}).get("stage_nanos_per_invocation", {})
    ]
    # The first row may be an old historical microbenchmark with no profile values;
    # collect names from this benchmark if necessary.
    if not stages:
        stages = sorted({
            stage
            for fork in forks
            for stage in fork.get("native_profile", {}).get("stage_nanos_per_invocation", {})
        })
    values = [(stage, median_stage(forks, stage) / 1_000_000.0) for stage in stages]
    values = [(stage, value) for stage, value in values if value > 0.000001]
    if not values:
        continue
    lines.append(label(item["name"]))
    for stage, value in values:
        lines.append(f"  {stage:38} {value:9.4f}")
lines.append("")
lines.append("Rust normalized native costs, median")
lines.append("------------------------------------")
lines.append(f"{'benchmark':42} {'scan ns/block':>14} {'model ns/quad':>14} {'fluid ns/block':>15} {'fluid ns/face':>14} {'trans ns/quad':>14} {'pack ns/quad':>13} {'assembly ns/quad':>16}")
for item in comparisons + current_only:
    if not item["name"].startswith("replay_section_") and not item["name"].startswith("diagnostic_") and item["name"] != "native_section_scan_and_build":
        continue
    forks = item["rust_forks"]
    scanned = median_count(forks, "scanned_blocks")
    model_quads = median_count(forks, "native_model_quads")
    fluid_blocks = median_count(forks, "fluid_blocks")
    fluid_faces = median_count(forks, "fluid_faces")
    translucent_quads = median_count(forks, "translucent_quads")
    emitted_quads = median_count(forks, "emitted_quads")
    def per(stage, count):
        return median_stage(forks, stage) / count if count else 0.0
    scan = per("section_scanning", scanned)
    model = per("native_model_lookup_and_emission", model_quads)
    fluid_block = per("fluid_visibility_and_height", fluid_blocks)
    fluid_face = (median_stage(forks, "fluid_geometry_and_uv") / fluid_faces) if fluid_faces else 0.0
    trans = per("translucent_analyzer_ingestion", translucent_quads)
    pack = per("vertex_packing", emitted_quads)
    assembly = per("final_mesh_assembly", emitted_quads)
    lines.append(f"{label(item['name'])[:42]:42} {scan:14.1f} {model:14.1f} {fluid_block:15.1f} {fluid_face:14.1f} {trans:14.1f} {pack:13.1f} {assembly:16.1f}")
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
