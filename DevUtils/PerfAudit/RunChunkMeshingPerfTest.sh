#!/usr/bin/env bash

# Runs the local chunk-meshing hot-path benchmark for this frozen Java baseline
# checkout. This script intentionally runs only this repository's test; the
# modern MattMC checkout owns cross-repo comparison orchestration.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

while [ ! -f "$PROJECT_ROOT/gradlew" ] && [ "$PROJECT_ROOT" != "/" ]; do
    PROJECT_ROOT="$(dirname "$PROJECT_ROOT")"
done

if [ ! -f "$PROJECT_ROOT/gradlew" ]; then
    echo "ERROR: Could not find gradlew. Are you in the MattMC Java perf project?" >&2
    exit 1
fi

usage() {
    cat <<'EOF'
Usage: ./DevUtils/PerfAudit/RunChunkMeshingPerfTest.sh [options]

Runs the frozen Java chunk-meshing benchmark and writes JSON results.

Options:
  --quads N        Number of synthetic quads. Default: 32768
  --warmup N       Warmup iterations. Default: 8
  --iterations N   Measured iterations. Default: 25
  --warmup-ms N    Minimum time-based warmup per benchmark. Default: 1500
  --measure-ms N   Minimum time-based measurement per benchmark. Default: 2500
  --fork-index N   Fork index recorded in the result JSON. Default: 0
  --output PATH    Result JSON path. Default: build/perf/chunk-meshing-hotpaths.json
  -h, --help       Show this help.
EOF
}

QUADS="${MATTMC_PERF_QUADS:-32768}"
WARMUP="${MATTMC_PERF_WARMUP:-8}"
ITERATIONS="${MATTMC_PERF_ITERATIONS:-25}"
WARMUP_MS="${MATTMC_PERF_WARMUP_MS:-1500}"
MEASURE_MS="${MATTMC_PERF_MEASURE_MS:-2500}"
FORK_INDEX="${MATTMC_PERF_FORK_INDEX:-0}"
OUTPUT_FILE=""

while [ "$#" -gt 0 ]; do
    case "$1" in
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
        --fork-index)
            FORK_INDEX="${2:-}"
            shift 2
            ;;
        --output)
            OUTPUT_FILE="${2:-}"
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

for value_name in QUADS WARMUP ITERATIONS WARMUP_MS MEASURE_MS FORK_INDEX; do
    value="${!value_name}"
    if ! [[ "$value" =~ ^[0-9]+$ ]]; then
        echo "ERROR: $value_name must be a non-negative integer, got: $value" >&2
        exit 1
    fi
done

if [ -z "$OUTPUT_FILE" ]; then
    OUTPUT_FILE="$PROJECT_ROOT/build/perf/chunk-meshing-hotpaths.json"
elif [[ "$OUTPUT_FILE" != /* ]]; then
    OUTPUT_FILE="$PWD/$OUTPUT_FILE"
fi

mkdir -p "$(dirname "$OUTPUT_FILE")"
rm -f "$OUTPUT_FILE"

echo "Running frozen Java chunk meshing benchmark..."
echo "  repo:       $PROJECT_ROOT"
echo "  quads:      $QUADS"
echo "  warmup:     $WARMUP"
echo "  iterations: $ITERATIONS"
echo "  warmup-ms:  $WARMUP_MS"
echo "  measure-ms: $MEASURE_MS"
echo "  fork-index: $FORK_INDEX"
echo "  output:     $OUTPUT_FILE"

cd "$PROJECT_ROOT"

PERF_JAVA_TOOL_OPTIONS="-Dmattmc.runPerfBenchmarks=true -Dmattmc.perf.quads=$QUADS -Dmattmc.perf.warmup=$WARMUP -Dmattmc.perf.iterations=$ITERATIONS -Dmattmc.perf.warmupMillis=$WARMUP_MS -Dmattmc.perf.measureMillis=$MEASURE_MS -Dmattmc.perf.forkIndex=$FORK_INDEX -Dmattmc.perf.output=$OUTPUT_FILE"
if [ -n "${JAVA_TOOL_OPTIONS:-}" ]; then
    PERF_JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS $PERF_JAVA_TOOL_OPTIONS"
fi

env \
    MATTMC_RUN_PERF_BENCHMARKS=true \
    JAVA_TOOL_OPTIONS="$PERF_JAVA_TOOL_OPTIONS" \
    ./gradlew test --tests net.sodium.client.perf.ChunkMeshingHotPathBenchmarkTest --rerun-tasks --no-daemon

if [ ! -f "$OUTPUT_FILE" ]; then
    echo "ERROR: Benchmark finished but did not write expected result JSON: $OUTPUT_FILE" >&2
    exit 1
fi

echo "Frozen Java benchmark results written to: $OUTPUT_FILE"
