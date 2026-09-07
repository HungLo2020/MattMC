#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
while [[ ! -f "$PROJECT_ROOT/gradlew" && "$PROJECT_ROOT" != "/" ]]; do
    PROJECT_ROOT="$(dirname "$PROJECT_ROOT")"
done

if [[ ! -f "$PROJECT_ROOT/gradlew" ]]; then
    echo "ERROR: Could not find gradlew from $SCRIPT_DIR" >&2
    exit 1
fi

FROZEN_ROOT="${MATTMC_FROZEN_JAVA_REPO:-/home/matt/Documents/Repos/MattMC_JavaPerfTesting/MattMC}"
RUNS="${MATTMC_REAL_REPLAY_RUNS:-3}"
WARMUP="${MATTMC_REAL_REPLAY_WARMUP:-30}"
MEASURE="${MATTMC_REAL_REPLAY_MEASURE:-80}"
RUN_ID="$(date +%Y%m%d-%H%M%S)"
ARTIFACT_DIR="$PROJECT_ROOT/build/perf/real-chunk-meshing-replay/$RUN_ID"

if [[ ! -f "$FROZEN_ROOT/gradlew" ]]; then
    echo "ERROR: Frozen Java repo not found at $FROZEN_ROOT" >&2
    exit 1
fi

mkdir -p "$ARTIFACT_DIR/current" "$ARTIFACT_DIR/frozen"

run_one() {
    local repo="$1"
    local label="$2"
    local index="$3"
    local output="$ARTIFACT_DIR/$label/run-$index.json"
    local log="$ARTIFACT_DIR/$label/run-$index.log"

    echo "[$label run $index/$RUNS] launching real client replay"
    (
        cd "$repo"
        ./gradlew runRealChunkMeshingReplay \
            -PmattmcReplayOutput="$output" \
            -PmattmcReplayWarmup="$WARMUP" \
            -PmattmcReplayMeasure="$MEASURE" \
            --no-daemon
    ) > "$log" 2>&1

    if [[ ! -f "$output" ]]; then
        echo "ERROR: $label run $index did not produce $output" >&2
        echo "See log: $log" >&2
        exit 1
    fi
}

for ((i = 1; i <= RUNS; i++)); do
    if (( i % 2 == 1 )); then
        run_one "$FROZEN_ROOT" frozen "$i"
        run_one "$PROJECT_ROOT" current "$i"
    else
        run_one "$PROJECT_ROOT" current "$i"
        run_one "$FROZEN_ROOT" frozen "$i"
    fi
done

python3 - "$ARTIFACT_DIR" "$RUNS" "$WARMUP" "$MEASURE" <<'PY'
import json
import statistics
import sys
from pathlib import Path

artifact = Path(sys.argv[1])
runs = int(sys.argv[2])
warmup = int(sys.argv[3])
measure = int(sys.argv[4])

def load(label):
    docs = []
    for path in sorted((artifact / label).glob("run-*.json")):
        with path.open() as f:
            docs.append((path, json.load(f)))
    return docs

def fixtures(docs):
    out = {}
    for path, doc in docs:
        if doc.get("status") != "ok":
            raise SystemExit(f"{path} failed: {doc}")
        for fixture in doc["fixtures"]:
            out.setdefault(fixture["name"], []).append((path, fixture))
    return out

frozen = fixtures(load("frozen"))
current = fixtures(load("current"))
names = sorted(set(frozen) | set(current))

def med(values):
    return statistics.median(values) if values else 0

def equivalent(a, b):
    if a["skipped_by_production_empty_section"] != b["skipped_by_production_empty_section"]:
        return False
    fields = [
        "pass_count", "total_vertices", "total_vertex_bytes", "checksum",
        "block_entities", "animated_sprites"
    ]
    return all(a["summary"].get(field) == b["summary"].get(field) for field in fields)

summary = {
    "artifact_dir": str(artifact),
    "runs": runs,
    "warmup_iterations": warmup,
    "measurement_iterations": measure,
    "rows": []
}

lines = []
lines.append("Real Production Chunk Meshing Replay")
lines.append("=" * 38)
lines.append(f"Artifacts: {artifact}")
lines.append(f"Runs: {runs} process(es), warmup={warmup}, measure={measure}")
lines.append("")
lines.append(f"{'Fixture':32} {'Eq':>3} {'Frozen ms':>11} {'Current ms':>11} {'Current/Frozen':>15}  Notes")
lines.append("-" * 94)

for name in names:
    f_entries = frozen.get(name, [])
    c_entries = current.get(name, [])
    if not f_entries or not c_entries:
        lines.append(f"{name:32} {'NO':>3} {'-':>11} {'-':>11} {'-':>15}  missing row")
        continue

    f_ref = f_entries[0][1]
    c_ref = c_entries[0][1]
    eq = equivalent(f_ref, c_ref)
    f_times = [entry["median_ns"] / 1_000_000.0 for _, entry in f_entries if entry["iterations"] > 0]
    c_times = [entry["median_ns"] / 1_000_000.0 for _, entry in c_entries if entry["iterations"] > 0]
    f_med = med(f_times)
    c_med = med(c_times)
    ratio = (c_med / f_med) if eq and f_med else None
    notes = []
    if f_ref["skipped_by_production_empty_section"]:
        notes.append("production empty-section skip")
    if not eq:
        notes.append("output mismatch; ratio withheld")
    if c_ref["summary"].get("fallback_blocks", 0) or c_ref["summary"].get("fallback_quads", 0):
        notes.append(f"current fallback b={c_ref['summary'].get('fallback_blocks', 0)} q={c_ref['summary'].get('fallback_quads', 0)}")
    ratio_text = f"{ratio:15.2f}x" if ratio is not None else f"{'-':>15}"
    f_text = f"{f_med:11.3f}" if f_times else f"{'-':>11}"
    c_text = f"{c_med:11.3f}" if c_times else f"{'-':>11}"
    lines.append(f"{name:32} {('yes' if eq else 'NO'):>3} {f_text} {c_text} {ratio_text}  {'; '.join(notes)}")
    summary["rows"].append({
        "fixture": name,
        "equivalent": eq,
        "frozen_median_ms": f_med,
        "current_median_ms": c_med,
        "current_over_frozen": ratio,
        "frozen_summary": f_ref["summary"],
        "current_summary": c_ref["summary"],
        "notes": notes,
    })

report = "\n".join(lines) + "\n"
(artifact / "real-chunk-meshing-replay-report.txt").write_text(report)
(artifact / "real-chunk-meshing-replay-summary.json").write_text(json.dumps(summary, indent=2) + "\n")
print(report)
PY

echo "Wrote:"
echo "  $ARTIFACT_DIR/real-chunk-meshing-replay-report.txt"
echo "  $ARTIFACT_DIR/real-chunk-meshing-replay-summary.json"
