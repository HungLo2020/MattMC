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
WARMUP_SECONDS="${MATTMC_REAL_REPLAY_WARMUP_SECONDS:-0}"
MEASURE_SECONDS="${MATTMC_REAL_REPLAY_MEASURE_SECONDS:-0}"
REBUILDS_PER_SAMPLE="${MATTMC_REAL_REPLAY_REBUILDS_PER_SAMPLE:-1}"
VALIDATE_EACH_SAMPLE="${MATTMC_REAL_REPLAY_VALIDATE_EACH_SAMPLE:-1}"
ALTERNATE_ORDER="${MATTMC_REAL_REPLAY_ALTERNATE_ORDER:-0}"
RUST_PROFILE="${MATTMC_REAL_REPLAY_RUST_PROFILE:-release}"
FIXTURE="${MATTMC_REAL_REPLAY_FIXTURE:-}"
RUN_ID="$(date +%Y%m%d-%H%M%S)"
ARTIFACT_DIR="$PROJECT_ROOT/build/perf/real-chunk-meshing-replay/$RUN_ID"

if [[ ! -f "$FROZEN_ROOT/gradlew" ]]; then
    echo "ERROR: Frozen Java repo not found at $FROZEN_ROOT" >&2
    exit 1
fi

case "${VALIDATE_EACH_SAMPLE,,}" in
    1|true|yes|on)
        VALIDATE_EACH_SAMPLE_JAVA="true"
        ;;
    0|false|no|off)
        VALIDATE_EACH_SAMPLE_JAVA="false"
        ;;
    *)
        echo "ERROR: Invalid MATTMC_REAL_REPLAY_VALIDATE_EACH_SAMPLE value: $VALIDATE_EACH_SAMPLE" >&2
        exit 1
        ;;
esac

mkdir -p "$ARTIFACT_DIR/current" "$ARTIFACT_DIR/frozen"

NATIVES_DIR="$PROJECT_ROOT/build/rust/native"
NATIVE_BUILD_LOG="$ARTIFACT_DIR/build-current-native.log"
echo "[setup] building current Rust native support library"
(
    cd "$PROJECT_ROOT"
    ./gradlew buildRustNative -PmattmcRustProfile="$RUST_PROFILE" --no-daemon
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
    local run_dir="$ARTIFACT_DIR/$label/run-$index-work"
    local excludes=(-x test)
    if [[ -d "$repo/src/main/rust" ]]; then
        excludes+=(-x testRustNative)
    fi

    echo "[$label run $index/$RUNS] launching real client replay"
    rm -rf "$run_dir"
    mkdir -p "$run_dir"
    (
        cd "$repo"
        export MATTMC_PROFILE_STATIC_MODEL_SUBSTAGES="${MATTMC_PROFILE_STATIC_MODEL_SUBSTAGES:-0}"
        export MATTMC_PROFILE_SCAN_SUBSTAGES="${MATTMC_PROFILE_SCAN_SUBSTAGES:-0}"
        export MATTMC_PROFILE_STAGING_SUBSTAGES="${MATTMC_PROFILE_STAGING_SUBSTAGES:-0}"
        export MATTMC_PROFILE_FLUID_SUBSTAGES="${MATTMC_PROFILE_FLUID_SUBSTAGES:-0}"
        export JAVA_TOOL_OPTIONS="-Dmattmc.rust.natives.dir=$NATIVES_DIR ${JAVA_TOOL_OPTIONS:-}"
        ./gradlew runRealChunkMeshingReplay \
            -PmattmcRustProfile="$RUST_PROFILE" \
            -PmattmcReplayOutput="$output" \
            -PmattmcReplayRunDir="$run_dir" \
            -PmattmcReplayWarmup="$WARMUP" \
            -PmattmcReplayWarmupSeconds="$WARMUP_SECONDS" \
            -PmattmcReplayMeasure="$MEASURE" \
            -PmattmcReplayMeasureSeconds="$MEASURE_SECONDS" \
            -PmattmcReplayRebuildsPerSample="$REBUILDS_PER_SAMPLE" \
            -PmattmcReplayValidateEachSample="$VALIDATE_EACH_SAMPLE_JAVA" \
            -PmattmcReplayRustProfile="$RUST_PROFILE" \
            -PmattmcReplayFixture="$FIXTURE" \
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

python3 - "$ARTIFACT_DIR" "$RUNS" "$WARMUP" "$MEASURE" "$WARMUP_SECONDS" "$MEASURE_SECONDS" "$REBUILDS_PER_SAMPLE" "$VALIDATE_EACH_SAMPLE_JAVA" "$ALTERNATE_ORDER" "$RUST_PROFILE" "$NATIVES_DIR" <<'PY'
import json
import statistics
import sys
from collections import Counter
from pathlib import Path

artifact = Path(sys.argv[1])
runs = int(sys.argv[2])
warmup = int(sys.argv[3])
measure = int(sys.argv[4])
warmup_seconds = float(sys.argv[5])
measure_seconds = float(sys.argv[6])
rebuilds_per_sample = int(sys.argv[7])
validate_each_sample = sys.argv[8]
alternate_order = sys.argv[9]
rust_profile = sys.argv[10]
natives_dir = sys.argv[11]

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
fingerprint_dir = artifact / "fingerprints"
fingerprint_dir.mkdir(exist_ok=True)

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
            counter[(pass_name, json.dumps(semantic_quad(quad), sort_keys=True, separators=(",", ":")))] += 1
    return counter

def semantic_quad(quad):
    semantic = json.loads(json.dumps(quad))
    for vertex in semantic.get("vertices", []):
        metadata = vertex.get("metadata")
        if isinstance(metadata, dict):
            metadata.pop("raw_word", None)
    return semantic

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

def stable_iteration_hashes(entry):
    hashes = entry.get("iteration_canonical_hashes", [])
    return len(set(hashes)) <= 1

def fingerprints_match(a, b):
    return a.get("fingerprint") == b.get("fingerprint")

def fingerprint_mismatch(name, run_index, frozen_path, frozen_entry, current_path, current_entry):
    return {
        "fixture": name,
        "run": run_index,
        "frozen_path": str(frozen_path),
        "current_path": str(current_path),
        "frozen_fingerprint": frozen_entry.get("fingerprint"),
        "current_fingerprint": current_entry.get("fingerprint"),
        "frozen_iteration_canonical_hashes": frozen_entry.get("iteration_canonical_hashes", []),
        "current_iteration_canonical_hashes": current_entry.get("iteration_canonical_hashes", []),
    }

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
    "warmup_seconds": warmup_seconds,
    "measurement_seconds": measure_seconds,
    "rebuilds_per_sample": rebuilds_per_sample,
    "validate_each_sample": validate_each_sample,
    "alternate_order": alternate_order,
    "rust_profile": rust_profile,
    "natives_dir": natives_dir,
    "determinism_ok": True,
    "rows": []
}

lines = []
lines.append("Real Production Chunk Meshing Replay")
lines.append("=" * 38)
lines.append(f"Artifacts: {artifact}")
lines.append(f"Runs: {runs} process(es), warmup={warmup} iteration(s) plus {warmup_seconds:.1f}s minimum, measure={measure} sample(s) plus {measure_seconds:.1f}s minimum")
lines.append(f"Per sample: {rebuilds_per_sample} ChunkBuilderMeshingTask.execute call(s); reported times are normalized per rebuild")
lines.append(f"Rust native profile: {rust_profile}; natives: {natives_dir}")
lines.append(f"Validation each sample: {validate_each_sample}; alternate launch order: {alternate_order}")
lines.append("")
lines.append(f"{'Fixture':32} {'Eq':>3} {'Frozen ms':>11} {'Current ms':>11} {'Current/Frozen':>15}  Notes")
lines.append("-" * 94)

profile_sections = []

def current_profile(fixture):
    return fixture.get("summary", {}).get("native_profile", {}) or {}

def format_profile_table(name, fixture):
    profile = current_profile(fixture)
    stages = profile.get("stages_nanos", {})
    counts = profile.get("counts", {})
    if not stages and not counts:
        return []
    interesting_stages = [
        "section_scanning",
        "native_model_lookup_and_emission",
        "static_state_selector_lookup",
        "static_weighted_multipart_resolution",
        "static_cached_model_lookup",
        "static_culling",
        "static_quad_iteration",
        "static_lighting_ao",
        "static_tint",
        "static_position_offset_transform",
        "static_sprite_material_pass",
        "static_native_quad_creation",
        "static_staging",
        "quad_staging",
        "translucent_analyzer_ingestion",
        "translucent_metadata_key_generation",
        "sorting",
        "vertex_packing",
        "index_emission",
        "final_mesh_assembly",
        "staging_quad_append",
        "staging_pending_write",
        "staging_flush",
        "staging_vertex_encoding",
    ]
    interesting_counts = [
        "scanned_blocks",
        "native_model_blocks",
        "native_model_quads",
        "fluid_blocks",
        "fluid_faces",
        "translucent_quads",
        "sorted_quads",
        "emitted_quads",
        "generic_native_quads",
        "generic_native_bytes_retained",
        "selector_resolutions",
        "selector_cache_hits",
        "selector_cache_misses",
        "multipart_children_tested",
        "multipart_children_selected",
        "weighted_entries_visited",
        "model_cache_hits",
        "model_cache_misses",
        "temporary_vector_clears",
        "translucent_retained_bytes",
        "translucent_analyzer_entries",
        "translucent_validity_bytes",
    ]
    out = []
    out.append("")
    out.append(f"Current Native Profile: {name}")
    out.append("-" * (24 + len(name)))
    out.append("Stages:")
    for stage in interesting_stages:
        value = stages.get(stage)
        if value:
            out.append(f"  {stage:42} {value / 1_000_000.0:10.3f} ms")
    out.append("Counts:")
    for count in interesting_counts:
        value = counts.get(count)
        if value:
            out.append(f"  {count:42} {value:10}")
    return out

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
    fingerprint_paths = []
    unstable_runs = []
    for run_index, ((f_path, f_entry), (c_path, c_entry)) in enumerate(paired, start=1):
        stable = stable_iteration_hashes(f_entry) and stable_iteration_hashes(c_entry)
        fingerprints_equal = fingerprints_match(f_entry, c_entry)
        run_eq = stable and fingerprints_equal and equivalent(f_entry, c_entry)
        eq_by_run.append(run_eq)
        raw_eq_by_run.append(stable and fingerprints_equal and raw_equivalent(f_entry, c_entry))
        if not stable:
            unstable_runs.append(str(run_index))
        if not fingerprints_equal:
            fingerprint = fingerprint_mismatch(name, run_index, f_path, f_entry, c_path, c_entry)
            fingerprint_path = fingerprint_dir / f"{name}-run-{run_index}.json"
            fingerprint_path.write_text(json.dumps(fingerprint, indent=2) + "\n")
            fingerprint_paths.append(str(fingerprint_path))
        if not run_eq and stable and fingerprints_equal:
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
        if unstable_runs:
            notes.append("unstable same-process run(s)=" + ",".join(unstable_runs))
            summary["determinism_ok"] = False
        if fingerprint_paths:
            notes.append("fingerprint dump(s)=" + ",".join(fingerprint_paths))
            summary["determinism_ok"] = False
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
    if name in ("weighted_multipart", "translucent_heavy"):
        profile_sections.extend(format_profile_table(name, c_ref))
    summary["rows"].append({
        "fixture": name,
        "equivalent": eq,
        "raw_equivalent": raw_eq,
        "frozen_median_ms": f_med,
        "current_median_ms": c_med,
        "current_over_frozen": ratio,
        "frozen_summary": f_ref["summary"],
        "current_summary": c_ref["summary"],
        "current_native_profile": current_profile(c_ref),
        "mismatch_paths": mismatch_paths,
        "fingerprint_paths": fingerprint_paths,
        "stable_same_process_hashes": not unstable_runs,
        "paired_fingerprints_match": not fingerprint_paths,
        "notes": notes,
        "frozen_raw_rebuild_times_ns": [entry.get("raw_rebuild_times_ns", []) for _, entry in f_entries],
        "current_raw_rebuild_times_ns": [entry.get("raw_rebuild_times_ns", []) for _, entry in c_entries],
        "frozen_raw_sample_batch_times_ns": [entry.get("raw_sample_batch_times_ns", []) for _, entry in f_entries],
        "current_raw_sample_batch_times_ns": [entry.get("raw_sample_batch_times_ns", []) for _, entry in c_entries],
    })

lines.extend(profile_sections)
report = "\n".join(lines) + "\n"
(artifact / "real-chunk-meshing-replay-report.txt").write_text(report)
(artifact / "real-chunk-meshing-replay-summary.json").write_text(json.dumps(summary, indent=2) + "\n")
print(report)
if not summary["determinism_ok"]:
    raise SystemExit("Determinism failure: paired fingerprints or same-process canonical hashes were unstable")
PY

echo "Wrote:"
echo "  $ARTIFACT_DIR/real-chunk-meshing-replay-report.txt"
echo "  $ARTIFACT_DIR/real-chunk-meshing-replay-summary.json"
