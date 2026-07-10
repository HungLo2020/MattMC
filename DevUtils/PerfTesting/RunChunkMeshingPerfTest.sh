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
  --rust-profile NAME  Cargo profile for current native build. Default: release
  -h, --help           Show this help.

Environment overrides:
  MATTMC_JAVA_PERF_REPO   Alternative default for --old-repo.
  MATTMC_PERF_QUADS       Alternative default for --quads.
  MATTMC_PERF_WARMUP      Alternative default for --warmup.
  MATTMC_PERF_ITERATIONS  Alternative default for --iterations.
EOF
}

timestamp="$(date +%Y%m%d-%H%M%S)"
OLD_REPO="${MATTMC_JAVA_PERF_REPO:-/home/matt/Documents/Repos/MattMC_JavaPerfTesting/MattMC}"
ARTIFACT_DIR="$PROJECT_ROOT/build/perf/chunk-meshing-compare/$timestamp"
QUADS="${MATTMC_PERF_QUADS:-32768}"
WARMUP="${MATTMC_PERF_WARMUP:-8}"
ITERATIONS="${MATTMC_PERF_ITERATIONS:-25}"
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

for value_name in QUADS WARMUP ITERATIONS; do
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

OLD_OUTPUT="$ARTIFACT_DIR/old-java/chunk-meshing-hotpaths.json"
CURRENT_OUTPUT="$ARTIFACT_DIR/current-rust/chunk-meshing-hotpaths.json"
rm -f "$OLD_OUTPUT" "$CURRENT_OUTPUT"

echo "Chunk meshing perf comparison"
echo "  frozen Java repo: $OLD_REPO"
echo "  current repo:     $PROJECT_ROOT"
echo "  artifact dir:     $ARTIFACT_DIR"
echo "  quads:            $QUADS"
echo "  warmup:           $WARMUP"
echo "  iterations:       $ITERATIONS"
echo "  rust profile:     $RUST_PROFILE"
echo ""
echo "Step 1/2: running frozen Java baseline..."
"$OLD_SCRIPT" \
    --quads "$QUADS" \
    --warmup "$WARMUP" \
    --iterations "$ITERATIONS" \
    --output "$OLD_OUTPUT"

if [ ! -f "$OLD_OUTPUT" ]; then
    echo "ERROR: Frozen Java benchmark did not write expected result JSON: $OLD_OUTPUT" >&2
    exit 1
fi

echo ""
echo "Step 2/2: running current Rust-backed benchmark..."
cd "$PROJECT_ROOT"

PERF_JAVA_TOOL_OPTIONS="-Dmattmc.runPerfBenchmarks=true -Dmattmc.perf.quads=$QUADS -Dmattmc.perf.warmup=$WARMUP -Dmattmc.perf.iterations=$ITERATIONS -Dmattmc.perf.output=$CURRENT_OUTPUT"
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

python3 - "$OLD_OUTPUT" "$CURRENT_OUTPUT" <<'PY'
import json
import sys
from pathlib import Path

old_path = Path(sys.argv[1])
current_path = Path(sys.argv[2])

with old_path.open("r", encoding="utf-8") as handle:
    old = json.load(handle)
with current_path.open("r", encoding="utf-8") as handle:
    current = json.load(handle)

old_results = {result["name"]: result for result in old["results"]}
current_results = {result["name"]: result for result in current["results"]}
names = [name for name in old_results if name in current_results]

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

comparisons = []
for name in names:
    old_result = old_results[name]
    current_result = current_results[name]
    old_mean = old_result["mean_ms"]
    current_mean = current_result["mean_ms"]
    old_throughput = old_result["mega_quads_per_second"]
    current_throughput = current_result["mega_quads_per_second"]
    time_ratio = current_mean / old_mean if old_mean else float("nan")
    throughput_ratio = current_throughput / old_throughput if old_throughput else float("nan")
    comparisons.append((name, old_result, current_result, time_ratio, throughput_ratio))

print("")
print("Chunk Meshing Hot-Path Results")
print("==============================")
print(f"Old implementation:     {old.get('implementation', 'unknown')}")
print(f"Current implementation: {current.get('implementation', 'unknown')}")
print(f"Quad count:             {current.get('quad_count', 'unknown')}")
print(f"Warmup iterations:      {current.get('warmup_iterations', 'unknown')}")
print(f"Measured iterations:    {current.get('measure_iterations', 'unknown')}")

for name, old_result, current_result, time_ratio, throughput_ratio in comparisons:
    print("")
    print(label(name))
    print("-" * len(label(name)))
    print(f"Mean time:   Java {fmt_ms(old_result['mean_ms'])} | Rust {fmt_ms(current_result['mean_ms'])} | {verdict_for(time_ratio)}")
    print(f"Median time: Java {fmt_ms(old_result['median_ms'])} | Rust {fmt_ms(current_result['median_ms'])}")
    print(f"Best time:   Java {fmt_ms(old_result['min_ms'])} | Rust {fmt_ms(current_result['min_ms'])}")
    print(f"Throughput:  Java {fmt_rate(old_result['mega_quads_per_second'])} | Rust {fmt_rate(current_result['mega_quads_per_second'])} | Rust/Java {fmt_ratio(throughput_ratio)}")

print("")
print(f"Frozen Java JSON: {old_path}")
print(f"Current Rust JSON: {current_path}")
PY
