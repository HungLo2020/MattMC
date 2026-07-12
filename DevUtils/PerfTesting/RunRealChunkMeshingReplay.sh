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
ALTERNATE_ORDER="${MATTMC_REAL_REPLAY_ALTERNATE_ORDER:-0}"
RUN_ID="$(date +%Y%m%d-%H%M%S)"
ARTIFACT_DIR="$PROJECT_ROOT/build/perf/real-chunk-meshing-replay/$RUN_ID"

if [[ ! -f "$FROZEN_ROOT/gradlew" ]]; then
    echo "ERROR: Frozen Java repo not found at $FROZEN_ROOT" >&2
    exit 1
fi

mkdir -p "$ARTIFACT_DIR/current" "$ARTIFACT_DIR/frozen"

NATIVES_DIR="$PROJECT_ROOT/build/rust/native"
NATIVE_BUILD_LOG="$ARTIFACT_DIR/build-current-native.log"
echo "[setup] building current Rust native support library"
(
    cd "$PROJECT_ROOT"
    ./gradlew buildRustNative --no-daemon
) > "$NATIVE_BUILD_LOG" 2>&1

if ! compgen -G "$NATIVES_DIR/mattmc_rust-*.*" > /dev/null; then
    echo "ERROR: Current native library was not produced in $NATIVES_DIR" >&2
    echo "See log: $NATIVE_BUILD_LOG" >&2
    exit 1
fi

run_one() {
    local repo="$1"
    local label="$2"
    local index="$3"
    local output="$ARTIFACT_DIR/$label/run-$index.json"
    local log="$ARTIFACT_DIR/$label/run-$index.log"
    local excludes=(-x test)
    if [[ -d "$repo/src/main/rust" ]]; then
        excludes+=(-x testRustNative)
    fi

    echo "[$label run $index/$RUNS] launching real client replay"
    (
        cd "$repo"
        export JAVA_TOOL_OPTIONS="-Dmattmc.rust.natives.dir=$NATIVES_DIR ${JAVA_TOOL_OPTIONS:-}"
        ./gradlew runRealChunkMeshingReplay \
            -PmattmcReplayOutput="$output" \
            -PmattmcReplayWarmup="$WARMUP" \
            -PmattmcReplayMeasure="$MEASURE" \
            --no-daemon \
            "${excludes[@]}"
    ) > "$log" 2>&1

    if [[ ! -f "$output" ]]; then
        echo "ERROR: $label run $index did not produce $output" >&2
        echo "See log: $log" >&2
        exit 1
    fi
}

for ((i = 1; i <= RUNS; i++)); do
    if [[ "$ALTERNATE_ORDER" == "1" || "$ALTERNATE_ORDER" == "true" || "$ALTERNATE_ORDER" == "yes" ]]; then
        if (( i % 2 == 1 )); then
            run_one "$FROZEN_ROOT" frozen "$i"
            run_one "$PROJECT_ROOT" current "$i"
        else
            run_one "$PROJECT_ROOT" current "$i"
            run_one "$FROZEN_ROOT" frozen "$i"
        fi
    else
        run_one "$FROZEN_ROOT" frozen "$i"
        run_one "$PROJECT_ROOT" current "$i"
    fi
done

python3 - "$ARTIFACT_DIR" "$RUNS" "$WARMUP" "$MEASURE" <<'PY'
import json
import statistics
import sys
from collections import Counter
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
mismatch_dir = artifact / "mismatches"
mismatch_dir.mkdir(exist_ok=True)

def med(values):
    return statistics.median(values) if values else 0

def raw_equivalent(a, b):
    if a["skipped_by_production_empty_section"] != b["skipped_by_production_empty_section"]:
        return False
    fields = [
        "pass_count", "total_vertices", "total_vertex_bytes", "checksum",
        "block_entities", "animated_sprites", "animated_sprite_names"
    ]
    return all(a["summary"].get(field) == b["summary"].get(field) for field in fields)

def canonical_quad_counter(fixture):
    counter = Counter()
    for render_pass in fixture["summary"].get("passes", []):
        pass_name = render_pass.get("name", "")
        for quad in render_pass.get("canonical_quads", []):
            counter[(pass_name, json.dumps(quad, sort_keys=True, separators=(",", ":")))] += 1
    return counter

def equivalent(a, b):
    if a["skipped_by_production_empty_section"] != b["skipped_by_production_empty_section"]:
        return False
    fields = [
        "pass_count", "total_vertices", "total_vertex_bytes",
        "block_entities", "animated_sprites", "animated_sprite_names"
    ]
    if not all(a["summary"].get(field) == b["summary"].get(field) for field in fields):
        return False
    return canonical_quad_counter(a) == canonical_quad_counter(b)

def first_mismatch(name, run_index, frozen_path, frozen_entry, current_path, current_entry):
    frozen_counter = canonical_quad_counter(frozen_entry)
    current_counter = canonical_quad_counter(current_entry)
    missing = frozen_counter - current_counter
    extra = current_counter - frozen_counter
    mismatch = {
        "fixture": name,
        "run": run_index,
        "frozen_path": str(frozen_path),
        "current_path": str(current_path),
        "raw_equivalent": raw_equivalent(frozen_entry, current_entry),
        "summary_fields": {},
        "first_missing_from_current": None,
        "first_extra_in_current": None,
        "missing_count": sum(missing.values()),
        "extra_count": sum(extra.values()),
    }
    for field in [
        "pass_count", "total_vertices", "total_vertex_bytes", "checksum",
        "block_entities", "animated_sprites", "animated_sprite_names", "fallback_blocks", "fallback_quads",
    ]:
        mismatch["summary_fields"][field] = {
            "frozen": frozen_entry["summary"].get(field),
            "current": current_entry["summary"].get(field),
        }
    if missing:
        key, count = min(missing.items(), key=lambda item: item[0])
        pass_name, quad_json = key
        mismatch["first_missing_from_current"] = {
            "pass": pass_name,
            "count": count,
            "quad": json.loads(quad_json),
        }
    if extra:
        key, count = min(extra.items(), key=lambda item: item[0])
        pass_name, quad_json = key
        mismatch["first_extra_in_current"] = {
            "pass": pass_name,
            "count": count,
            "quad": json.loads(quad_json),
        }
    return mismatch

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
    paired = list(zip(f_entries, c_entries))
    eq_by_run = []
    raw_eq_by_run = []
    mismatch_paths = []
    for run_index, ((f_path, f_entry), (c_path, c_entry)) in enumerate(paired, start=1):
        run_eq = equivalent(f_entry, c_entry)
        eq_by_run.append(run_eq)
        raw_eq_by_run.append(raw_equivalent(f_entry, c_entry))
        if not run_eq:
            mismatch = first_mismatch(name, run_index, f_path, f_entry, c_path, c_entry)
            mismatch_path = mismatch_dir / f"{name}-run-{run_index}.json"
            mismatch_path.write_text(json.dumps(mismatch, indent=2) + "\n")
            mismatch_paths.append(str(mismatch_path))
    eq = len(f_entries) == len(c_entries) and all(eq_by_run)
    raw_eq = len(f_entries) == len(c_entries) and all(raw_eq_by_run)
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
        bad_runs = [str(i + 1) for i, ok in enumerate(eq_by_run) if not ok]
        if bad_runs:
            notes.append("mismatch run(s)=" + ",".join(bad_runs))
        if mismatch_paths:
            notes.append("mismatch dump(s)=" + ",".join(mismatch_paths))
    elif not raw_eq:
        notes.append("canonical match; raw order/checksum differs")
    if c_ref["summary"].get("fallback_blocks", 0) or c_ref["summary"].get("fallback_quads", 0):
        notes.append(f"current fallback b={c_ref['summary'].get('fallback_blocks', 0)} q={c_ref['summary'].get('fallback_quads', 0)}")
    ratio_text = f"{ratio:15.2f}x" if ratio is not None else f"{'-':>15}"
    f_text = f"{f_med:11.3f}" if f_times else f"{'-':>11}"
    c_text = f"{c_med:11.3f}" if c_times else f"{'-':>11}"
    lines.append(f"{name:32} {('yes' if eq else 'NO'):>3} {f_text} {c_text} {ratio_text}  {'; '.join(notes)}")
    summary["rows"].append({
        "fixture": name,
        "equivalent": eq,
        "raw_equivalent": raw_eq,
        "frozen_median_ms": f_med,
        "current_median_ms": c_med,
        "current_over_frozen": ratio,
        "frozen_summary": f_ref["summary"],
        "current_summary": c_ref["summary"],
        "mismatch_paths": mismatch_paths,
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
