#!/usr/bin/env bash

set -euo pipefail

if [[ "${MATTMC_GRAPHICS_TOOL_INTERNAL:-}" != "1" ]]; then
    echo "capture_runner.sh is an internal capture engine for graphics testing." >&2
    echo "Use DevUtils/Audit/Capture.py, DevUtils/PerfAudit/Gameplay.py, DevUtils/PerfAudit/Subsystem.py, or DevUtils/PerfAudit/Matrix.py from the current repo." >&2
    exit 2
fi

usage() {
    cat <<'EOF'
Usage: ./DevUtils/Common/capture_runner.sh [--backend vulkan|opengl] [--shaders off|on] [--artifact-dir DIR] [--max-secs N] [--dump-secs N] [--validation off|standard] [--shader-input-parity off|standard|full] [--client-args "..."]

Runs ./gradlew runClient in a bounded session, captures diagnostics, and
self-terminates so no manual kill is required.

Additional diagnostics captured:
    - full latest.log copy
    - shader/runtime config snapshots before and after the run
    - shader-focused filtered event logs for OpenGL/Vulkan comparison
    - crash reports, hs_err files, and recent debug artifacts
    - run/patched_shaders snapshot for shader-source diffing
    - system and git state snapshots
    - optional Vulkan validation-layer messages

Environment overrides:
    MAX_SECS                    (default: 120)
    DUMP_SECS                   (default: 45)
    SCREENSHOT_INTERVAL_SECS    (default: 5)
    SCREENSHOT_MAX_COUNT        (default: 6)
    SCREENSHOT_START_DELAY_SECS (default: 0)
    VALIDATION_MODE             off|standard (default: off)
    SHADER_INPUT_PARITY         off|standard|full (default: off)
    SHADER_INPUT_PARITY_MAX_LOGS (default: 120000)
    LIGHTMAP_INFO_PARITY_MAX_LOGS (default: 512)
    CLIENT_ARGS
EOF
}

BACKEND="vulkan"
SHADERS="off"
ARTIFACT_DIR_OVERRIDE=""
MAX_SECS="${MAX_SECS:-120}"
DUMP_SECS="${DUMP_SECS:-45}"
SCREENSHOT_INTERVAL_SECS="${SCREENSHOT_INTERVAL_SECS:-5}"
SCREENSHOT_MAX_COUNT="${SCREENSHOT_MAX_COUNT:-6}"
SCREENSHOT_START_DELAY_SECS="${SCREENSHOT_START_DELAY_SECS:-0}"
VALIDATION_MODE="${VALIDATION_MODE:-off}"
SHADER_INPUT_PARITY="${SHADER_INPUT_PARITY:-off}"
SHADER_INPUT_PARITY_MAX_LOGS="${SHADER_INPUT_PARITY_MAX_LOGS:-120000}"
LIGHTMAP_INFO_PARITY_MAX_LOGS="${LIGHTMAP_INFO_PARITY_MAX_LOGS:-512}"
CLIENT_ARGS="${CLIENT_ARGS:-}"
WORLD="${MATTMC_CAPTURE_WORLD:-Origin}"
WORLD_SOURCE="${MATTMC_CAPTURE_WORLD_SOURCE:-}"
RUN_SOURCE="${MATTMC_CAPTURE_RUN_SOURCE:-}"
GUI_RESOURCE_PACK_SCENARIO="${MATTMC_GUI_RESOURCE_PACK_SCENARIO:-}"
WORLD_STATIC_TERRAIN_RESOURCE_PACK_SCENARIO="${MATTMC_WORLD_STATIC_TERRAIN_RESOURCE_PACK_SCENARIO:-}"
WORLD_STATIC_TERRAIN_SCENARIO="${MATTMC_WORLD_STATIC_TERRAIN_SCENARIO:-}"
WORLD_STATIC_TERRAIN_SECOND_WORLD="${MATTMC_WORLD_STATIC_TERRAIN_SECOND_WORLD:-}"
WORLD_STATIC_TERRAIN_FAULT="${MATTMC_WORLD_STATIC_TERRAIN_FAULT:-}"
WORLD_STATIC_TERRAIN_WATER_ANIMATION_CAPTURE="${MATTMC_WORLD_STATIC_TERRAIN_WATER_ANIMATION_CAPTURE:-false}"
DETERMINISTIC_CAPTURE="${MATTMC_GRAPHICS_CORRECTNESS_CAPTURE:-false}"
DETERMINISTIC_METADATA="${MATTMC_DETERMINISTIC_METADATA:-}"
DETERMINISTIC_SCREENSHOT_DIR="${MATTMC_DETERMINISTIC_SCREENSHOT_DIR:-}"
TITLE_SCREEN_CAPTURE="${MATTMC_TITLE_SCREEN_CAPTURE:-false}"
# Diagnostic capture only. When true, retain the normal overlay-to-title
# timeline instead of waiting for the title receipt before the first image.
TITLE_SCREEN_TRANSITION_CAPTURE="${MATTMC_TITLE_SCREEN_TRANSITION_CAPTURE:-false}"
FRAME_BENCHMARK_STATUS="${MATTMC_GRAPHICS_FRAME_BENCHMARK_STATUS:-}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --backend)
            BACKEND="${2:-}"
            shift 2
            ;;
        --shaders)
            SHADERS="${2:-}"
            shift 2
            ;;
        --artifact-dir)
            ARTIFACT_DIR_OVERRIDE="${2:-}"
            shift 2
            ;;
        --max-secs)
            MAX_SECS="${2:-}"
            shift 2
            ;;
        --dump-secs)
            DUMP_SECS="${2:-}"
            shift 2
            ;;
        --validation)
            VALIDATION_MODE="${2:-}"
            shift 2
            ;;
        --shader-input-parity)
            SHADER_INPUT_PARITY="${2:-}"
            shift 2
            ;;
        --client-args)
            CLIENT_ARGS="${2:-}"
            shift 2
            ;;
        --world)
            WORLD="${2:-}"
            shift 2
            ;;
        --gui-resource-pack-scenario)
            GUI_RESOURCE_PACK_SCENARIO="${2:-}"
            shift 2
            ;;
        --world-static-terrain-resource-pack-scenario)
            WORLD_STATIC_TERRAIN_RESOURCE_PACK_SCENARIO="${2:-}"
            shift 2
            ;;
        --world-static-terrain-water-animation-capture)
            WORLD_STATIC_TERRAIN_WATER_ANIMATION_CAPTURE="true"
            shift
            ;;
        --world-static-terrain-scenario)
            WORLD_STATIC_TERRAIN_SCENARIO="${2:-}"
            shift 2
            ;;
        --world-static-terrain-second-world)
            WORLD_STATIC_TERRAIN_SECOND_WORLD="${2:-}"
            shift 2
            ;;
        --world-static-terrain-fault)
            WORLD_STATIC_TERRAIN_FAULT="${2:-}"
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

if [[ "$BACKEND" != "vulkan" && "$BACKEND" != "opengl" ]]; then
    echo "Invalid backend: $BACKEND (expected vulkan or opengl)" >&2
    exit 1
fi

if [[ "$SHADERS" != "off" && "$SHADERS" != "on" ]]; then
    echo "Invalid shaders mode: $SHADERS (expected off or on)" >&2
    exit 1
fi

if ! [[ "$MAX_SECS" =~ ^[0-9]+$ ]] || ! [[ "$DUMP_SECS" =~ ^[0-9]+$ ]]; then
    echo "--max-secs and --dump-secs must be integers" >&2
    exit 1
fi

if ! [[ "$SCREENSHOT_INTERVAL_SECS" =~ ^[0-9]+$ ]] || ! [[ "$SCREENSHOT_MAX_COUNT" =~ ^[0-9]+$ ]] || ! [[ "$SCREENSHOT_START_DELAY_SECS" =~ ^[0-9]+$ ]]; then
    echo "SCREENSHOT_INTERVAL_SECS, SCREENSHOT_MAX_COUNT, and SCREENSHOT_START_DELAY_SECS must be integers" >&2
    exit 1
fi

if [[ "$VALIDATION_MODE" != "off" && "$VALIDATION_MODE" != "standard" ]]; then
    echo "--validation must be 'off' or 'standard'" >&2
    exit 1
fi

if [[ "$SHADER_INPUT_PARITY" != "off" && "$SHADER_INPUT_PARITY" != "standard" && "$SHADER_INPUT_PARITY" != "full" ]]; then
    echo "--shader-input-parity must be 'off', 'standard', or 'full'" >&2
    exit 1
fi

if ! [[ "$SHADER_INPUT_PARITY_MAX_LOGS" =~ ^-?[0-9]+$ ]]; then
    echo "SHADER_INPUT_PARITY_MAX_LOGS must be an integer" >&2
    exit 1
fi

if ! [[ "$LIGHTMAP_INFO_PARITY_MAX_LOGS" =~ ^-?[0-9]+$ ]]; then
    echo "LIGHTMAP_INFO_PARITY_MAX_LOGS must be an integer" >&2
    exit 1
fi

# Find project root (where gradlew exists).
SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
while [[ ! -f "$PROJECT_ROOT/gradlew" && "$PROJECT_ROOT" != "/" ]]; do
    PROJECT_ROOT="$(dirname "$PROJECT_ROOT")"
done

if [[ ! -f "$PROJECT_ROOT/gradlew" ]]; then
    echo "ERROR: Could not find gradlew from $SCRIPT_DIR" >&2
    exit 1
fi

cd "$PROJECT_ROOT"

SOURCE_RUN_DIR="${RUN_SOURCE:-$PROJECT_ROOT/run}"
RUN_DIR="$SOURCE_RUN_DIR"
ARTIFACT_DIR="$PROJECT_ROOT/logs/auto-capture"
if [[ -n "$ARTIFACT_DIR_OVERRIDE" ]]; then
    ARTIFACT_DIR="$ARTIFACT_DIR_OVERRIDE"
fi
mkdir -p "$ARTIFACT_DIR"

RUN_ID="$(date +%Y%m%d_%H%M%S)"
START_EPOCH="$(date +%s)"
ISOLATED_GAME_DIR="$ARTIFACT_DIR/game_dir_${RUN_ID}"
OPTIONS_FILE="$RUN_DIR/options.txt"
RUN_LOG="$ARTIFACT_DIR/runClient_${RUN_ID}.log"
META_LOG="$ARTIFACT_DIR/meta_${RUN_ID}.txt"
THREAD_DUMP="$ARTIFACT_DIR/thread_dump_${RUN_ID}.txt"
PROCESS_SNAPSHOT="$ARTIFACT_DIR/process_snapshot_${RUN_ID}.txt"
SYSTEM_SNAPSHOT="$ARTIFACT_DIR/system_snapshot_${RUN_ID}.txt"
GIT_SNAPSHOT="$ARTIFACT_DIR/git_snapshot_${RUN_ID}.txt"
SHADERPACK_INFO="$ARTIFACT_DIR/shaderpack_${RUN_ID}.txt"
SHADER_SUMMARY="$ARTIFACT_DIR/shader_summary_${RUN_ID}.txt"
SHADER_EVENT_LOG="$ARTIFACT_DIR/shader_events_${RUN_ID}.log"
VALIDATION_EVENT_LOG="$ARTIFACT_DIR/validation_events_${RUN_ID}.log"
LATEST_LOG_COPY="$ARTIFACT_DIR/latest_${RUN_ID}.log"
LATEST_TAIL="$ARTIFACT_DIR/latest_tail_${RUN_ID}.log"
HSERR_LIST="$ARTIFACT_DIR/hs_err_${RUN_ID}.txt"
CRASH_REPORT_LIST="$ARTIFACT_DIR/crash_reports_${RUN_ID}.txt"
CRASH_REPORT_DIR="$ARTIFACT_DIR/crash_reports_${RUN_ID}"
DEBUG_FILE_LIST="$ARTIFACT_DIR/debug_files_${RUN_ID}.txt"
DEBUG_SNAPSHOT_DIR="$ARTIFACT_DIR/debug_${RUN_ID}"
CONFIG_BEFORE_DIR="$ARTIFACT_DIR/config_before_${RUN_ID}"
CONFIG_AFTER_DIR="$ARTIFACT_DIR/config_after_${RUN_ID}"
PATCHED_SHADERS_SNAPSHOT="$ARTIFACT_DIR/patched_shaders_${RUN_ID}"
PATCHED_SHADERS_MANIFEST="$ARTIFACT_DIR/patched_shaders_manifest_${RUN_ID}.txt"
WINDOW_TREE="$ARTIFACT_DIR/window_tree_${RUN_ID}.txt"
WINDOW_TREE_DUMP="$ARTIFACT_DIR/window_tree_dump_${RUN_ID}.txt"

copy_optional_path() {
    local source="$1"
    local target="$2"

    if [[ ! -e "$source" ]]; then
        return
    fi
    mkdir -p "$(dirname "$target")"
    cp -a "$source" "$target"
}

prepare_isolated_game_dir() {
    local source_world="${WORLD_SOURCE:-$SOURCE_RUN_DIR/saves/$WORLD}"

    if [[ ! -d "$source_world" ]]; then
        echo "ERROR: Cannot copy missing benchmark world: $source_world" >&2
        exit 1
    fi
    if [[ -e "$ISOLATED_GAME_DIR" ]]; then
        echo "ERROR: Refusing to reuse isolated game dir: $ISOLATED_GAME_DIR" >&2
        exit 1
    fi

    mkdir -p "$ISOLATED_GAME_DIR/saves"
    copy_optional_path "$SOURCE_RUN_DIR/options.txt" "$ISOLATED_GAME_DIR/options.txt"
    copy_optional_path "$SOURCE_RUN_DIR/config" "$ISOLATED_GAME_DIR/config"
    copy_optional_path "$SOURCE_RUN_DIR/resourcepacks" "$ISOLATED_GAME_DIR/resourcepacks"
    copy_optional_path "$SOURCE_RUN_DIR/shaderpacks" "$ISOLATED_GAME_DIR/shaderpacks"
    copy_optional_path "$SOURCE_RUN_DIR/Distant_Horizons_server_data" "$ISOLATED_GAME_DIR/Distant_Horizons_server_data"
    copy_optional_path "$SOURCE_RUN_DIR/voxelmap" "$ISOLATED_GAME_DIR/voxelmap"
    cp -a "$source_world" "$ISOLATED_GAME_DIR/saves/$WORLD"

    RUN_DIR="$ISOLATED_GAME_DIR"
    OPTIONS_FILE="$RUN_DIR/options.txt"
}

VALIDATION_LAYER_MANIFEST=""
VALIDATION_LAYER_DIR=""
VALIDATION_LAYER_AVAILABLE="false"
VALIDATION_ENABLED="false"
CLIENT_ARG_SHADER_PACK=""
CLIENT_ARG_ENABLE_SHADERS=""
IRIS_PROPERTY_SHADER_PACK=""
IRIS_PROPERTY_ENABLE_SHADERS=""
IRIS_PROPERTY_COLOR_SPACE=""
EFFECTIVE_SHADER_PACK=""
EFFECTIVE_ENABLE_SHADERS=""
PROJECT_SPIRV_COMPILER="$PROJECT_ROOT/libraries/deps/glslangValidator"
SHADER_EVENT_PATTERN='Using shaderpack:|Loaded Shaderpack:|shaderPack=|enableShaders=|Profile:|Reloading pipeline on dimension change|Creating pipeline for dimension|Skipping compute shader|Missing program .*sodium:pipeline|Sodium Vulkan chunk pipelines|raw GLSL imports|ShaderInputParity|LightmapInfoParity|Type is (VERTEX|FRAGMENT|GEOMETRY|COMPUTE)|bindVertexBuffer requires an active render pass|No active Vulkan render pass to end|shader compose into swapchain|Unexpected error|DistantHorizons|\[DH-|DH Ready|DH Iris events|Validation Error|Validation Warning|VUID-|UNASSIGNED-|VK_LAYER_KHRONOS_validation|GL_INVALID|OpenGL debug'
VALIDATION_EVENT_PATTERN='VK_LAYER_KHRONOS_validation|Validation Error|Validation Warning|VUID-|UNASSIGNED-'
KEY_SUMMARY_PATTERN='Using shaderpack:|Loaded Shaderpack:|Profile:|Reloading pipeline on dimension change|Creating pipeline for dimension|Skipping compute shader|Missing program .*sodium:pipeline|Sodium Vulkan chunk pipelines|raw GLSL imports|ShaderInputParity|LightmapInfoParity|bindVertexBuffer requires an active render pass|No active Vulkan render pass to end|Unexpected error|DistantHorizons|\[DH-|DH Ready|DH Iris events|Validation Error|Validation Warning|VUID-|UNASSIGNED-'

prepare_isolated_game_dir

save_state_fingerprint() {
    python3 - "$RUN_DIR" "$WORLD" <<'PY'
import hashlib
import pathlib
import sys

run_dir = pathlib.Path(sys.argv[1])
world = sys.argv[2]
save_dir = run_dir / "saves" / world
if not save_dir.is_dir():
    print("world_save_state_hash=missing")
    print("world_save_state_file_count=0")
    print("world_save_state_total_bytes=0")
    print("world_save_state_truncated=false")
    raise SystemExit(0)

allowed = {".mca"}
digest = hashlib.sha256()
file_count = 0
total_bytes = 0
truncated = False
for path in sorted(p for p in save_dir.rglob("*") if p.is_file()):
    rel = path.relative_to(save_dir).as_posix()
    if rel == "session.lock" or path.suffix not in allowed:
        continue
    file_count += 1
    if file_count > 5000:
        truncated = True
        break
    try:
        data = path.read_bytes()
    except OSError:
        data = b""
    total_bytes += len(data)
    digest.update(rel.encode("utf-8"))
    digest.update(b"\0")
    digest.update(str(len(data)).encode("ascii"))
    digest.update(b"\0")
    digest.update(hashlib.sha256(data).hexdigest().encode("ascii"))
    digest.update(b"\n")

print(f"world_save_state_hash={digest.hexdigest()}")
print(f"world_save_state_file_count={file_count}")
print(f"world_save_state_total_bytes={total_bytes}")
print(f"world_save_state_truncated={str(truncated).lower()}")
PY
}

{
    echo "run_id=$RUN_ID"
    echo "start_epoch=$START_EPOCH"
    echo "backend=$BACKEND"
    echo "shaders=$SHADERS"
    echo "world=$WORLD"
    echo "isolated_game_source=$SOURCE_RUN_DIR"
    echo "isolated_game_dir=$ISOLATED_GAME_DIR"
    echo "isolated_world_source=${WORLD_SOURCE:-$SOURCE_RUN_DIR/saves/$WORLD}"
    echo "isolated_world_copy=$ISOLATED_GAME_DIR/saves/$WORLD"
    save_state_fingerprint
    echo "parity_fixture_schema=${MATTMC_PARITY_FIXTURE_SCHEMA:-}"
    echo "parity_fixture_id=${MATTMC_PARITY_FIXTURE_ID:-}"
    echo "parity_fixture_manifest=${MATTMC_PARITY_FIXTURE_MANIFEST:-}"
    echo "parity_fixture_source_save_hash=${MATTMC_PARITY_FIXTURE_SOURCE_SAVE_HASH:-}"
    echo "parity_camera_x=${MATTMC_PARITY_CAMERA_X:-}"
    echo "parity_camera_y=${MATTMC_PARITY_CAMERA_Y:-}"
    echo "parity_camera_z=${MATTMC_PARITY_CAMERA_Z:-}"
    echo "parity_camera_yaw=${MATTMC_PARITY_CAMERA_YAW:-}"
    echo "parity_camera_pitch=${MATTMC_PARITY_CAMERA_PITCH:-}"
    echo "parity_camera_pose_sequence=${MATTMC_PARITY_CAMERA_POSE_SEQUENCE:-}"
    echo "max_secs=$MAX_SECS"
    echo "dump_secs=$DUMP_SECS"
    echo "validation_mode=$VALIDATION_MODE"
    echo "graphics_run_type=${MATTMC_GRAPHICS_RUN_TYPE:-clean-performance}"
    echo "graphics_audit_enabled=${MATTMC_GRAPHICS_AUDIT:-false}"
    echo "validation_profile=${MATTMC_GRAPHICS_VALIDATION_PROFILE:-$VALIDATION_MODE}"
    echo "validation_fail_severity=${MATTMC_GRAPHICS_VALIDATION_FAIL_SEVERITY:-warning}"
    echo "renderdoc_capture=${MATTMC_RENDERDOC_CAPTURE:-false}"
    echo "renderdoc_frame=${MATTMC_RENDERDOC_FRAME:-0}"
    echo "renderdoc_capture_path=${MATTMC_RENDERDOC_CAPTURE_PATH:-}"
    echo "tracy_capture=${MATTMC_TRACY_CAPTURE:-false}"
    echo "tracy_duration_seconds=${MATTMC_TRACY_DURATION_SECONDS:-0}"
    echo "tracy_max_size_mb=${MATTMC_TRACY_MAX_SIZE_MB:-0}"
    echo "deterministic_camera_capture=$DETERMINISTIC_CAPTURE"
    echo "deterministic_metadata=$DETERMINISTIC_METADATA"
    echo "deterministic_screenshot_dir=$DETERMINISTIC_SCREENSHOT_DIR"
    echo "frame_benchmark_status=$FRAME_BENCHMARK_STATUS"
    echo "title_screen_capture=$TITLE_SCREEN_CAPTURE"
    echo "title_screen_transition_capture=$TITLE_SCREEN_TRANSITION_CAPTURE"
    echo "gui_resource_pack_scenario=$GUI_RESOURCE_PACK_SCENARIO"
    echo "world_static_terrain_resource_pack_scenario=$WORLD_STATIC_TERRAIN_RESOURCE_PACK_SCENARIO"
    echo "world_static_terrain_scenario=$WORLD_STATIC_TERRAIN_SCENARIO"
    echo "world_static_terrain_second_world=$WORLD_STATIC_TERRAIN_SECOND_WORLD"
    echo "world_static_terrain_fault=$WORLD_STATIC_TERRAIN_FAULT"
    echo "world_static_terrain_water_animation_capture=$WORLD_STATIC_TERRAIN_WATER_ANIMATION_CAPTURE"
    echo "shader_input_parity=$SHADER_INPUT_PARITY"
    echo "shader_input_parity_max_logs=$SHADER_INPUT_PARITY_MAX_LOGS"
    echo "lightmap_info_parity_max_logs=$LIGHTMAP_INFO_PARITY_MAX_LOGS"
    echo "client_args=$CLIENT_ARGS"
} > "$META_LOG"

screenshot_count=0
screenshot_enabled="false"

find_client_window_id() {
    local client_pid="$1"
    local client_list raw_ids id props pid

    if [[ -z "$client_pid" ]] || ! command -v xprop >/dev/null 2>&1; then
        return 1
    fi

    client_list="$(xprop -root _NET_CLIENT_LIST_STACKING 2>/dev/null || true)"
    raw_ids="$(printf '%s\n' "$client_list" | grep -o '0x[0-9a-fA-F]\+' || true)"
    if [[ -z "$raw_ids" ]]; then
        return 1
    fi

    while IFS= read -r id; do
        [[ -z "$id" ]] && continue
        props="$(xprop -id "$id" _NET_WM_PID WM_NAME 2>/dev/null || true)"
        pid="$(printf '%s\n' "$props" | awk -F' = ' '/_NET_WM_PID\(CARDINAL\)/ {print $2; exit}' | tr -d '[:space:]')"
        if [[ "$pid" == "$client_pid" ]]; then
            printf '%s\n' "$id"
            return 0
        fi
    done < <(printf '%s\n' "$raw_ids" | tac)

    return 1
}

capture_x11_window() {
    local target_window="$1"
    local screenshot_file="$2"
    local raw_capture="${screenshot_file}.xwd"

    # ImageMagick's `import -window` can succeed yet capture unrelated desktop
    # pixels under this X11 setup. xwd reads the requested drawable by id and
    # fails when it cannot; conversion happens only after that direct read.
    command -v xwd >/dev/null 2>&1 || return 1
    command -v magick >/dev/null 2>&1 || return 1
    xwd -silent -id "$target_window" -out "$raw_capture" >/dev/null 2>&1 || {
        rm -f "$raw_capture"
        return 1
    }
    [[ -s "$raw_capture" ]] || {
        rm -f "$raw_capture"
        return 1
    }
    magick "$raw_capture" "$screenshot_file" >/dev/null 2>&1
    local status=$?
    rm -f "$raw_capture"
    [[ $status -eq 0 && -f "$screenshot_file" ]]
}

x11_window_is_viewable() {
    local target_window="$1"
    command -v xwininfo >/dev/null 2>&1 || return 1
    xwininfo -id "$target_window" 2>/dev/null | grep -q 'Map State: IsViewable'
}

capture_root_screenshot() {
    local label="$1"
    local elapsed_secs="$2"
    local client_pid="${3:-}"
    local target_window="root"

    if [[ "$screenshot_enabled" != "true" || "$SCREENSHOT_MAX_COUNT" -eq 0 ]]; then
        return
    fi
    if [[ "$screenshot_count" -ge "$SCREENSHOT_MAX_COUNT" ]]; then
        return
    fi

    screenshot_count=$((screenshot_count + 1))
    local screenshot_file="$ARTIFACT_DIR/screenshot_${RUN_ID}_${screenshot_count}_${label}_${elapsed_secs}s.png"
    if ! target_window="$(find_client_window_id "$client_pid" 2>/dev/null)"; then
        # The X root is the desktop, not a Minecraft frame.  Capturing it can
        # silently record an editor or terminal and falsely attribute those
        # pixels to Frozen's renderer.  Leave this scheduled sample available
        # for a later client-window retry instead.
        screenshot_count=$((screenshot_count - 1))
        echo "screenshot_rejected_root_target=$((screenshot_count + 1))" >> "$META_LOG"
        return
    fi
    if ! x11_window_is_viewable "$target_window"; then
        screenshot_count=$((screenshot_count - 1))
        echo "screenshot_rejected_unviewable_target=$((screenshot_count + 1))" >> "$META_LOG"
        return
    fi

    if capture_x11_window "$target_window" "$screenshot_file"; then
        echo "screenshot_${screenshot_count}=$screenshot_file" >> "$META_LOG"
        echo "screenshot_${screenshot_count}_target=$target_window" >> "$META_LOG"
    else
        rm -f "$screenshot_file"
        echo "screenshot_${screenshot_count}=failed:$label:$elapsed_secs" >> "$META_LOG"
    fi
}

deterministic_capture_status() {
    if [[ "$DETERMINISTIC_CAPTURE" != "true" || -z "$DETERMINISTIC_METADATA" || ! -f "$DETERMINISTIC_METADATA" ]]; then
        return 1
    fi
    python3 - "$DETERMINISTIC_METADATA" <<'PY'
import json
import sys
from pathlib import Path

try:
    print(json.loads(Path(sys.argv[1]).read_text(encoding="utf-8")).get("status", "missing"))
except Exception:
    print("invalid")
PY
}

capture_requested_screenshot() {
    local request="$1"
    local client_pid="$2"
    local ack="${request%.json}.ack.json"
    local screenshot target_window window_props observed_window_pid

    if [[ -f "$ack" ]]; then
        return
    fi
    if [[ "$screenshot_enabled" != "true" ]]; then
        echo "deterministic_capture_failed=$request:no_screenshot_tool" >> "$META_LOG"
        return
    fi

    screenshot="$(python3 - "$request" <<'PY'
import json
import sys
from pathlib import Path

request = Path(sys.argv[1])
data = json.loads(request.read_text(encoding="utf-8"))
print(data.get("screenshot", str(request.with_suffix(".png"))))
PY
)" || {
        echo "deterministic_capture_request_invalid=$request" >> "$META_LOG"
        return
    }
    mkdir -p "$(dirname "$screenshot")"
    if ! target_window="$(find_client_window_id "$client_pid" 2>/dev/null)"; then
        # Do not acknowledge a level/title capture from the desktop root.
        # The request remains pending and the normal polling loop retries once
        # the Frozen client owns a real X window.
        echo "deterministic_capture_rejected_root_target=$request" >> "$META_LOG"
        return
    fi
    if ! x11_window_is_viewable "$target_window"; then
        echo "deterministic_capture_rejected_unviewable_window=$request" >> "$META_LOG"
        return
    fi
    # Diagnostic provenance only: re-read the X11 identity immediately before
    # capture so a title/transition screenshot cannot be attributed to a
    # reused foreign window id. This never changes Frozen rendering.
    window_props="$(xprop -id "$target_window" _NET_WM_PID WM_NAME _NET_WM_NAME WM_CLASS 2>/dev/null || true)"
    observed_window_pid="$(printf '%s\n' "$window_props" | awk -F' = ' '/_NET_WM_PID\(CARDINAL\)/ {print $2; exit}' | tr -d '[:space:]')"
    if [[ -z "$observed_window_pid" || "$observed_window_pid" != "$client_pid" ]]; then
        echo "deterministic_capture_rejected_unverified_window=$request" >> "$META_LOG"
        return
    fi

    if capture_x11_window "$target_window" "$screenshot"; then
        python3 - "$request" "$ack" "$screenshot" "$target_window" "$client_pid" "$window_props" <<'PY'
import json
import sys
import time
from pathlib import Path

request = Path(sys.argv[1])
ack = Path(sys.argv[2])
data = json.loads(request.read_text(encoding="utf-8"))
data.update({
    "status": "captured",
    "screenshot": sys.argv[3],
    "targetWindow": sys.argv[4],
    "capturedAtEpoch": int(time.time()),
    "windowProvenance": {
        "schema": "mattmc-window-capture-provenance-v1",
        "platform": "linux",
        "targetWindow": sys.argv[4],
        "expectedClientPid": int(sys.argv[5]),
        "observedWindowPid": int(sys.argv[5]),
        "windowProperties": sys.argv[6],
        "status": "verified",
    },
})
ack.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
        echo "deterministic_capture_ack=$ack" >> "$META_LOG"
        echo "deterministic_capture_screenshot=$screenshot" >> "$META_LOG"
        echo "deterministic_capture_target=$target_window" >> "$META_LOG"
    else
        rm -f "$screenshot"
        echo "deterministic_capture_failed=$request" >> "$META_LOG"
    fi
}

capture_deterministic_requests() {
    local client_pid="$1"
    local request

    if [[ "$DETERMINISTIC_CAPTURE" != "true" || -z "$DETERMINISTIC_SCREENSHOT_DIR" || ! -d "$DETERMINISTIC_SCREENSHOT_DIR" ]]; then
        return
    fi
    shopt -s nullglob
    for request in "$DETERMINISTIC_SCREENSHOT_DIR"/capture_request_*.json; do
        [[ "$request" == *.ack.json ]] && continue
        capture_requested_screenshot "$request" "$client_pid"
    done
    shopt -u nullglob
}

capture_frozen_title_presented_frame() {
    local client_pid="$1"
    local request ack
    [[ "$TITLE_SCREEN_CAPTURE" == "true" ]] || return 1
    [[ -n "${MATTMC_FROZEN_TITLE_FRAME_CAPTURE_DIR:-}" ]] || return 1
    request="$MATTMC_FROZEN_TITLE_FRAME_CAPTURE_DIR/title_frame_capture.json"
    ack="$MATTMC_FROZEN_TITLE_FRAME_CAPTURE_DIR/title_frame_capture.ack.json"
    [[ -f "$ack" ]] && return 0
    [[ -f "$request" ]] || return 1
    capture_requested_screenshot "$request" "$client_pid"
    [[ -f "$ack" ]]
}

wait_for_deterministic_shutdown() {
    local grace_secs="${1:-20}"
    local waited

    echo "deterministic_shutdown_grace_secs=$grace_secs" >> "$META_LOG"
    for ((waited = 0; waited < grace_secs; waited++)); do
        if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
            echo "deterministic_shutdown_grace_elapsed=$waited" >> "$META_LOG"
            return 0
        fi
        sleep 1
    done
    echo "deterministic_shutdown_grace_elapsed=$grace_secs" >> "$META_LOG"
    echo "deterministic_shutdown_grace_expired=true" >> "$META_LOG"
    return 1
}

frame_benchmark_status() {
    if [[ -z "$FRAME_BENCHMARK_STATUS" || ! -f "$FRAME_BENCHMARK_STATUS" ]]; then
        return 0
    fi
    python3 - "$FRAME_BENCHMARK_STATUS" <<'PY'
import json
import sys

try:
    with open(sys.argv[1], encoding="utf-8") as handle:
        value = json.load(handle).get("status", "")
except (OSError, ValueError, TypeError):
    value = ""
print(value if isinstance(value, str) else "")
PY
}

find_validation_layer_manifest() {
    local candidate

    if [[ -n "${VULKAN_SDK:-}" ]]; then
        candidate="$VULKAN_SDK/share/vulkan/explicit_layer.d/VkLayer_khronos_validation.json"
        if [[ -f "$candidate" ]]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    fi

    for candidate in \
        /usr/share/vulkan/explicit_layer.d/VkLayer_khronos_validation.json \
        /usr/local/share/vulkan/explicit_layer.d/VkLayer_khronos_validation.json \
        /etc/vulkan/explicit_layer.d/VkLayer_khronos_validation.json; do
        if [[ -f "$candidate" ]]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done

    return 1
}

copy_config_snapshot() {
    local target_dir="$1"
    local rel_path src_path

    mkdir -p "$target_dir"
    for rel_path in \
        options.txt \
        config/iris.properties \
        config/iris-excluded.json \
        config/DistantHorizons.toml \
        config/sodium-options.json \
        config/sodium-mixins.properties \
        config/voxelmap.properties; do
        src_path="$RUN_DIR/$rel_path"
        if [[ -f "$src_path" ]]; then
            cp "$src_path" "$target_dir/$(basename "$rel_path")"
        fi
    done
}

extract_property_value() {
    local file_path="$1"
    local key="$2"

    if [[ ! -f "$file_path" ]]; then
        return 1
    fi

    awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "$file_path"
}

extract_client_arg_assignment() {
    local key="$1"

    if [[ -z "$CLIENT_ARGS" ]]; then
        return 1
    fi

    printf '%s\n' "$CLIENT_ARGS" \
        | grep -oE "(^|[[:space:]])${key}=[^[:space:]]+" \
        | head -n 1 \
        | sed -E "s/(^|[[:space:]])${key}=//"
}

upsert_property() {
    local file_path="$1"
    local key="$2"
    local value="$3"

    if [[ ! -f "$file_path" ]]; then
        return 1
    fi

    if grep -q "^${key}=" "$file_path"; then
        sed -i "s|^${key}=.*|${key}=${value}|" "$file_path"
    else
        printf '%s=%s\n' "$key" "$value" >> "$file_path"
    fi
}

upsert_option() {
    local file_path="$1"
    local key="$2"
    local value="$3"

    if [[ ! -f "$file_path" ]]; then
        return 1
    fi

    if grep -q "^${key}:" "$file_path"; then
        sed -i "s|^${key}:.*|${key}:${value}|" "$file_path"
    else
        printf '%s:%s\n' "$key" "$value" >> "$file_path"
    fi
}

apply_iris_overrides() {
    local iris_file="$RUN_DIR/config/iris.properties"
    local normalized_enable=""

    if [[ ! -f "$iris_file" ]]; then
        echo "iris_override_note=iris.properties_missing" >> "$META_LOG"
        return
    fi

    if [[ -n "$CLIENT_ARG_ENABLE_SHADERS" ]]; then
        normalized_enable="$(printf '%s' "$CLIENT_ARG_ENABLE_SHADERS" | tr '[:upper:]' '[:lower:]')"
        if [[ "$normalized_enable" == "true" || "$normalized_enable" == "false" ]]; then
            upsert_property "$iris_file" "enableShaders" "$normalized_enable"
            echo "iris_override_enable_shaders=$normalized_enable" >> "$META_LOG"
        else
            echo "iris_override_enable_shaders_ignored=$CLIENT_ARG_ENABLE_SHADERS" >> "$META_LOG"
        fi
    fi

    if [[ -n "$CLIENT_ARG_SHADER_PACK" ]]; then
        upsert_property "$iris_file" "shaderPack" "$CLIENT_ARG_SHADER_PACK"
        echo "iris_override_shader_pack=$CLIENT_ARG_SHADER_PACK" >> "$META_LOG"
    fi
}

resolve_shader_pack_path() {
    local shader_pack_name="$1"
    local candidate

    if [[ -z "$shader_pack_name" ]]; then
        return 1
    fi

    if [[ -f "$shader_pack_name" || -d "$shader_pack_name" ]]; then
        printf '%s\n' "$shader_pack_name"
        return 0
    fi

    for candidate in \
        "$RUN_DIR/shaderpacks/$shader_pack_name" \
        "$PROJECT_ROOT/shaderpacks/$shader_pack_name"; do
        if [[ -f "$candidate" || -d "$candidate" ]]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done

    return 1
}

collect_system_snapshot() {
    {
        echo "backend=$BACKEND"
        echo "validation_mode=$VALIDATION_MODE"
        echo "validation_enabled=$VALIDATION_ENABLED"
        echo "validation_layer_available=$VALIDATION_LAYER_AVAILABLE"
        echo "validation_layer_manifest=${VALIDATION_LAYER_MANIFEST:-unavailable}"
        echo "shader_input_parity=$SHADER_INPUT_PARITY"
        echo "shader_input_parity_max_logs=$SHADER_INPUT_PARITY_MAX_LOGS"
        echo "display=${DISPLAY:-unset}"
        echo "wayland_display=${WAYLAND_DISPLAY:-unset}"
        echo "xdg_session_type=${XDG_SESSION_TYPE:-unset}"
        echo "project_spirv_compiler=${PROJECT_SPIRV_COMPILER:-unavailable}"
        echo
        echo "===== uname -a ====="
        uname -a
        echo
        echo "===== java -version ====="
        java -version 2>&1 || true
        if [[ -x "$PROJECT_SPIRV_COMPILER" ]]; then
            echo
            echo "===== project glslangValidator --version ====="
            "$PROJECT_SPIRV_COMPILER" --version 2>&1 || true
        elif command -v glslangValidator >/dev/null 2>&1; then
            echo
            echo "===== glslangValidator --version ====="
            glslangValidator --version 2>&1 || true
        fi
        if command -v vulkaninfo >/dev/null 2>&1; then
            echo
            echo "===== vulkaninfo --summary ====="
            vulkaninfo --summary 2>&1 || true
        fi
        if command -v glxinfo >/dev/null 2>&1; then
            echo
            echo "===== glxinfo -B ====="
            glxinfo -B 2>&1 || true
        fi
        if command -v nvidia-smi >/dev/null 2>&1; then
            echo
            echo "===== nvidia-smi ====="
            nvidia-smi --query-gpu=name,driver_version,vbios_version --format=csv,noheader 2>&1 || true
        fi
    } > "$SYSTEM_SNAPSHOT"
}

collect_git_snapshot() {
    {
        echo "===== git branch ====="
        git -C "$PROJECT_ROOT" branch --show-current 2>&1 || true
        echo
        echo "===== git rev-parse HEAD ====="
        git -C "$PROJECT_ROOT" rev-parse HEAD 2>&1 || true
        echo
        echo "===== git status --short ====="
        git -C "$PROJECT_ROOT" status --short 2>&1 || true
    } > "$GIT_SNAPSHOT"
}

write_shaderpack_info() {
    local iris_file="$CONFIG_BEFORE_DIR/iris.properties"
    local shader_pack_path=""

    {
        echo "client_args=$CLIENT_ARGS"
        echo "client_arg_enable_shaders=${CLIENT_ARG_ENABLE_SHADERS:-unset}"
        echo "client_arg_shader_pack=${CLIENT_ARG_SHADER_PACK:-unset}"
        echo "iris_property_enable_shaders=${IRIS_PROPERTY_ENABLE_SHADERS:-unset}"
        echo "iris_property_shader_pack=${IRIS_PROPERTY_SHADER_PACK:-unset}"
        echo "iris_property_color_space=${IRIS_PROPERTY_COLOR_SPACE:-unset}"
        echo "effective_enable_shaders=${EFFECTIVE_ENABLE_SHADERS:-unset}"
        echo "effective_shader_pack=${EFFECTIVE_SHADER_PACK:-unset}"
        echo "config_snapshot=$iris_file"

        if shader_pack_path="$(resolve_shader_pack_path "$EFFECTIVE_SHADER_PACK" 2>/dev/null)"; then
            echo "shader_pack_path=$shader_pack_path"
            echo
            echo "===== shader pack stat ====="
            stat "$shader_pack_path" 2>&1 || true
            echo
            echo "===== shader pack sha256 ====="
            sha256sum "$shader_pack_path" 2>&1 || true
            if [[ -f "$shader_pack_path" && "$shader_pack_path" == *.zip ]] && command -v unzip >/dev/null 2>&1; then
                echo
                echo "===== shader pack zip listing (first 200 lines) ====="
                unzip -l "$shader_pack_path" 2>&1 | sed -n '1,200p'
            elif [[ -d "$shader_pack_path" ]]; then
                echo
                echo "===== shader pack file listing ====="
                find "$shader_pack_path" -maxdepth 4 -type f | sort
            fi
        else
            echo "shader_pack_path=unresolved"
        fi
    } > "$SHADERPACK_INFO"
}

copy_recent_files_since_start() {
    local source_dir="$1"
    local dest_dir="$2"
    local list_file="$3"
    local file_path rel_path

    if [[ ! -d "$source_dir" ]]; then
        : > "$list_file"
        return
    fi

    find "$source_dir" -type f -printf '%T@ %p\n' \
        | awk -v start="$START_EPOCH" '$1 >= start {print $2}' \
        | sort > "$list_file"

    if [[ ! -s "$list_file" ]]; then
        return
    fi

    mkdir -p "$dest_dir"
    while IFS= read -r file_path; do
        [[ -z "$file_path" ]] && continue
        rel_path="${file_path#$source_dir/}"
        mkdir -p "$dest_dir/$(dirname "$rel_path")"
        cp "$file_path" "$dest_dir/$rel_path"
    done < "$list_file"
}

append_filtered_section() {
    local label="$1"
    local file_path="$2"
    local pattern="$3"
    local output_file="$4"

    if [[ ! -f "$file_path" ]]; then
        return
    fi

    {
        echo "===== $label: $file_path ====="
        if ! grep -nEi "$pattern" "$file_path"; then
            echo "(no matches)"
        fi
        echo
    } >> "$output_file"
}

count_list_entries() {
    local file_path="$1"

    if [[ ! -f "$file_path" ]]; then
        echo 0
        return
    fi

    awk 'NF {count++} END {print count + 0}' "$file_path"
}

if command -v import >/dev/null 2>&1 && [[ -n "${DISPLAY:-}" ]]; then
    screenshot_enabled="true"
    {
        echo "screenshot_enabled=true"
        echo "screenshot_interval_secs=$SCREENSHOT_INTERVAL_SECS"
        echo "screenshot_max_count=$SCREENSHOT_MAX_COUNT"
        echo "screenshot_start_delay_secs=$SCREENSHOT_START_DELAY_SECS"
        echo "display=${DISPLAY}"
    } >> "$META_LOG"
    xwininfo -root -tree > "$WINDOW_TREE" 2>&1 || true
else
    {
        echo "screenshot_enabled=false"
        echo "display=${DISPLAY:-unset}"
    } >> "$META_LOG"
fi

if [[ -f "$OPTIONS_FILE" ]]; then
    GUI_SCALE="${MATTMC_CAPTURE_GUI_SCALE:-3}"
    if ! [[ "$GUI_SCALE" =~ ^[0-9]+$ ]] || [[ "$GUI_SCALE" -le 0 ]]; then
        echo "MATTMC_CAPTURE_GUI_SCALE must be a positive integer, got '$GUI_SCALE'" >&2
        exit 1
    fi
    if grep -q '^graphics_backend=' "$OPTIONS_FILE"; then
        sed -i "s/^graphics_backend=.*/graphics_backend=$BACKEND/" "$OPTIONS_FILE"
    else
        echo "graphics_backend=$BACKEND" >> "$OPTIONS_FILE"
    fi
    upsert_option "$OPTIONS_FILE" renderDistance "${MATTMC_CAPTURE_RENDER_DISTANCE:-10}"
    upsert_option "$OPTIONS_FILE" simulationDistance "${MATTMC_CAPTURE_SIMULATION_DISTANCE:-12}"
    upsert_option "$OPTIONS_FILE" guiScale "$GUI_SCALE"
    if [[ -n "${MATTMC_CAPTURE_MIPMAP_LEVELS+x}" ]]; then
        if ! [[ "$MATTMC_CAPTURE_MIPMAP_LEVELS" =~ ^[0-4]$ ]]; then
            echo "MATTMC_CAPTURE_MIPMAP_LEVELS must be 0 through 4" >&2
            exit 1
        fi
        upsert_option "$OPTIONS_FILE" mipmapLevels "$MATTMC_CAPTURE_MIPMAP_LEVELS"
        echo "forced_option_mipmapLevels=$MATTMC_CAPTURE_MIPMAP_LEVELS" >> "$META_LOG"
    fi
    upsert_option "$OPTIONS_FILE" fullscreen false
    upsert_option "$OPTIONS_FILE" hideGui false
    upsert_option "$OPTIONS_FILE" maxFps "${MATTMC_CAPTURE_MAX_FPS:-120}"
    upsert_option "$OPTIONS_FILE" enableVsync false
    upsert_option "$OPTIONS_FILE" tutorialStep none
    if [[ "$TITLE_SCREEN_CAPTURE" == "true" ]]; then
        MENU_BACKGROUND_BLUR="${MATTMC_CAPTURE_MENU_BACKGROUND_BLUR:-5}"
        if ! [[ "$MENU_BACKGROUND_BLUR" =~ ^[0-9]+$ ]] || [[ "$MENU_BACKGROUND_BLUR" -gt 10 ]]; then
            echo "MATTMC_CAPTURE_MENU_BACKGROUND_BLUR must be an integer from 0 to 10" >&2
            exit 2
        fi
        upsert_option "$OPTIONS_FILE" menuBackgroundBlurriness "$MENU_BACKGROUND_BLUR"
        echo "forced_option_menuBackgroundBlurriness=$MENU_BACKGROUND_BLUR" >> "$META_LOG"
    fi
    # `PanoramaTheme` is a serialized vanilla enum. "default" is not an
    # element; AQUATIC is the game's default. Never make a Frozen baseline
    # capture depend on Options' error-recovery behavior.
    PANORAMA_THEME="${MATTMC_CAPTURE_PANORAMA_THEME:-aquatic}"
    case "$PANORAMA_THEME" in
        aquatic|caves|copper_age|nether|release|spring_to_life|tricky_trials) ;;
        *) echo "MATTMC_CAPTURE_PANORAMA_THEME must be a vanilla theme, got '$PANORAMA_THEME'" >&2; exit 1;;
    esac
    upsert_option "$OPTIONS_FILE" panoramaTheme "\"$PANORAMA_THEME\""
    if [[ "${MATTMC_CAPTURE_TITLE_STATIC_FIXTURE:-false}" == "true" ]]; then
        # Isolated parity-fixture settings only.  They make title imagery a
        # stable Frozen OpenGL baseline; they do not alter renderer behavior.
        upsert_option "$OPTIONS_FILE" panoramaScrollSpeed 0.0
        upsert_option "$OPTIONS_FILE" hideSplashTexts true
    fi
fi

if [[ -f "$RUN_DIR/config/voxelmap.properties" ]]; then
    upsert_option "$RUN_DIR/config/voxelmap.properties" "Welcome Message" false
    # The vanilla migration fixture excludes mod-owned minimap output. Its
    # asynchronous cache otherwise makes paired isolated captures diverge
    # before any vanilla renderer is involved.
    upsert_option "$RUN_DIR/config/voxelmap.properties" "Hide Minimap" true
fi

if [[ -n "$GUI_RESOURCE_PACK_SCENARIO" && "$GUI_RESOURCE_PACK_SCENARIO" != "vanilla" && -n "${MATTMC_GUI_PACK_GENERATOR_ROOT:-}" ]]; then
    # Capture-only parity setup. Reuse the shared Python pack specification so
    # Frozen and Current consume byte-equivalent synthetic resource packs.
    python3 - "$MATTMC_GUI_PACK_GENERATOR_ROOT" "$RUN_DIR" "$GUI_RESOURCE_PACK_SCENARIO" <<'PY'
import json
import sys
from pathlib import Path

root = Path(sys.argv[1])
run_dir = Path(sys.argv[2])
scenario = sys.argv[3]
sys.path.insert(0, str(root))
sys.path.insert(0, str(root / "DevUtils" / "Common"))
from DevUtils.Common.capture_runner import gui_resource_pack_specs, write_gui_resource_pack

pack_root = run_dir / "resourcepacks"
pack_root.mkdir(parents=True, exist_ok=True)
selected = []
for spec in gui_resource_pack_specs(scenario):
    pack_dir = pack_root / str(spec["name"])
    write_gui_resource_pack(pack_dir, spec)
    selected.append(f"file/{spec['name']}")
options = run_dir / "options.txt"
lines = options.read_text(encoding="utf-8").splitlines() if options.is_file() else []
values = {"resourcePacks": json.dumps(selected, separators=(",", ":")), "incompatibleResourcePacks": "[]"}
for key, value in values.items():
    for index, line in enumerate(lines):
        if line.startswith(key + ":"):
            lines[index] = key + ":" + value
            break
    else:
        lines.append(key + ":" + value)
options.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
    echo "gui_resource_pack_generated=$GUI_RESOURCE_PACK_SCENARIO" >> "$META_LOG"
    selected_packs="$(sed -n 's/^resourcePacks://p' "$RUN_DIR/options.txt" | head -n 1)"
    echo "gui_resource_pack_selected=${selected_packs:-[]}" >> "$META_LOG"
fi

# Ordinary static-terrain parity fixtures intentionally exclude unrelated DH
# generation on both Current and Frozen rows.  Keep this as capture setup only;
# production rendering behavior is unchanged.
if [[ "${MATTMC_CAPTURE_DISABLE_DH_FOR_ORDINARY_SOURCE:-false}" == "true" && -f "$RUN_DIR/config/DistantHorizons.toml" ]]; then
    upsert_toml_value() {
        local file_path="$1" key="$2" value="$3"
        sed -i -E "s|^([[:space:]]*)${key}[[:space:]]*=.*|\\1${key} = ${value}|" "$file_path"
    }
    # Isolated vanilla producer fixtures do not exercise DH rendering. Disable
    # its legacy draw pass as well as generation so Current and Frozen expose
    # the same workload fingerprint; dedicated DH rows do not set this flag.
    # enableRendering is the debug-wireframe switch, not the LOD renderer.
    upsert_toml_value "$RUN_DIR/config/DistantHorizons.toml" rendererMode '"DISABLED"'
    upsert_toml_value "$RUN_DIR/config/DistantHorizons.toml" enableRendering false
    # DH can retain this option even after rendering is disabled. It changes
    # vanilla settings such as clouds, so an ordinary Frozen OpenGL baseline
    # must turn it off in the copied harness run just as Current does.
    upsert_toml_value "$RUN_DIR/config/DistantHorizons.toml" overrideVanillaGraphicsSettings false
    # The excluded DH compositor would otherwise suppress Frozen's vanilla
    # fog. Keep ordinary parity rows on the same vanilla fog contract as the
    # Rust route; this affects only this materialized harness game directory.
    upsert_toml_value "$RUN_DIR/config/DistantHorizons.toml" enableVanillaFog true
    upsert_toml_value "$RUN_DIR/config/DistantHorizons.toml" enableDhFog false
    upsert_toml_value "$RUN_DIR/config/DistantHorizons.toml" enableDistantGeneration false
    upsert_toml_value "$RUN_DIR/config/DistantHorizons.toml" synchronizeOnLoad false
    upsert_toml_value "$RUN_DIR/config/DistantHorizons.toml" enableRealTimeUpdates false
    upsert_toml_value "$RUN_DIR/config/DistantHorizons.toml" maxSyncOnLoadRequestDistance 0
    upsert_toml_value "$RUN_DIR/config/DistantHorizons.toml" maxGenerationRequestDistance 0
    upsert_toml_value "$RUN_DIR/config/DistantHorizons.toml" numberOfThreads 1
    upsert_toml_value "$RUN_DIR/config/DistantHorizons.toml" threadRunTimeRatio '"0.0"'
    upsert_toml_value "$RUN_DIR/config/DistantHorizons.toml" lodChunkRenderDistanceRadius 2
    echo "forced_dh_enableRendering=false reason=ordinary-selected-source" >> "$META_LOG"
    echo "forced_dh_rendererMode=DISABLED reason=ordinary-selected-source" >> "$META_LOG"
    echo "forced_dh_overrideVanillaGraphicsSettings=false reason=ordinary-selected-source" >> "$META_LOG"
    echo "forced_dh_enableDistantGeneration=false reason=ordinary-selected-source" >> "$META_LOG"
fi

{
    echo "forced_window_width=${MATTMC_CAPTURE_MENU_WIDTH:-1280}"
    echo "forced_window_height=${MATTMC_CAPTURE_MENU_HEIGHT:-720}"
    echo "forced_option_renderDistance=${MATTMC_CAPTURE_RENDER_DISTANCE:-10}"
    echo "forced_option_simulationDistance=${MATTMC_CAPTURE_SIMULATION_DISTANCE:-12}"
    echo "forced_option_guiScale=${GUI_SCALE:-3}"
    echo "forced_option_fullscreen=false"
    echo "forced_option_hideGui=false"
    echo "forced_option_maxFps=${MATTMC_CAPTURE_MAX_FPS:-120}"
    echo "forced_option_enableVsync=false"
    echo "forced_option_tutorialStep=none"
    echo "forced_option_panoramaTheme=${PANORAMA_THEME:-default}"
    if [[ "${MATTMC_CAPTURE_TITLE_STATIC_FIXTURE:-false}" == "true" ]]; then
        echo "forced_option_panoramaScrollSpeed=0.0"
        echo "forced_option_hideSplashTexts=true"
        echo "title_static_fixture=true"
    fi
    echo "forced_voxelmap_welcome=false"
    echo "forced_voxelmap_minimap_hidden=true"
} >> "$META_LOG"

if VALIDATION_LAYER_MANIFEST="$(find_validation_layer_manifest 2>/dev/null)"; then
    VALIDATION_LAYER_DIR="$(dirname "$VALIDATION_LAYER_MANIFEST")"
    VALIDATION_LAYER_AVAILABLE="true"
fi

if [[ "$BACKEND" == "vulkan" && "$VALIDATION_MODE" == "standard" && "$VALIDATION_LAYER_AVAILABLE" == "true" ]]; then
    VALIDATION_ENABLED="true"
fi

copy_config_snapshot "$CONFIG_BEFORE_DIR"
CLIENT_ARG_SHADER_PACK="$(extract_client_arg_assignment shaderPack || true)"
CLIENT_ARG_ENABLE_SHADERS="$(extract_client_arg_assignment enableShaders || true)"
IRIS_PROPERTY_SHADER_PACK="$(extract_property_value "$CONFIG_BEFORE_DIR/iris.properties" shaderPack || true)"
IRIS_PROPERTY_ENABLE_SHADERS="$(extract_property_value "$CONFIG_BEFORE_DIR/iris.properties" enableShaders || true)"
IRIS_PROPERTY_COLOR_SPACE="$(extract_property_value "$CONFIG_BEFORE_DIR/iris.properties" colorSpace || true)"
EFFECTIVE_SHADER_PACK="${CLIENT_ARG_SHADER_PACK:-$IRIS_PROPERTY_SHADER_PACK}"
EFFECTIVE_ENABLE_SHADERS="${CLIENT_ARG_ENABLE_SHADERS:-$IRIS_PROPERTY_ENABLE_SHADERS}"

apply_iris_overrides

collect_system_snapshot
collect_git_snapshot
write_shaderpack_info

{
    echo "validation_enabled=$VALIDATION_ENABLED"
    echo "validation_layer_available=$VALIDATION_LAYER_AVAILABLE"
    echo "validation_layer_manifest=${VALIDATION_LAYER_MANIFEST:-unavailable}"
    echo "config_before=$CONFIG_BEFORE_DIR"
    echo "system_snapshot=$SYSTEM_SNAPSHOT"
    echo "git_snapshot=$GIT_SNAPSHOT"
    echo "shaderpack_info=$SHADERPACK_INFO"
    echo "effective_enable_shaders=${EFFECTIVE_ENABLE_SHADERS:-unset}"
    echo "effective_shader_pack=${EFFECTIVE_SHADER_PACK:-unset}"
} >> "$META_LOG"

find_client_pid() {
    local pid

    # Prefer Java processes in the same process group as this run, with Minecraft/Fabric markers.
    pid="$(ps -eo pid=,pgid=,cmd= \
        | awk -v pg="$GRADLE_PID" '$2 == pg && $0 ~ /java/ && $0 ~ /(devlaunchinjector|minecraft|fabric|MattMC-1\\.21)/ {print $1; exit}' 2>/dev/null || true)"
    if [[ -n "${pid:-}" ]]; then
        echo "$pid"
        return
    fi

    # Fallback to jcmd process inventory.
    pid="$(jcmd -l 2>/dev/null | awk '/devlaunchinjector|minecraft|fabric|MattMC-1\.21/ {print $1; exit}' || true)"
    if [[ -n "${pid:-}" ]]; then
        echo "$pid"
        return
    fi

    # Last resort: any Java process in this run's process group.
    pid="$(ps -eo pid=,pgid=,cmd= | awk -v pg="$GRADLE_PID" '$2 == pg && $0 ~ /java/ {print $1; exit}' || true)"
    if [[ -n "${pid:-}" ]]; then
        echo "$pid"
    fi
}

dump_taken="false"
timed_out="false"
exit_code=""

echo "Starting bounded runClient capture (run_id=$RUN_ID, backend=$BACKEND)"
# A capture must not attach to an unrelated long-lived IDE or prior-run daemon:
# its JVM properties and resource state are part of the captured fixture. A
# single-use daemon is isolated and exits with this bounded runner.
GRADLE_CMD=(./gradlew --no-daemon -x test runClient "-PmattmcRunGameDir=$RUN_DIR")
if [[ -n "$CLIENT_ARGS" ]]; then
    GRADLE_CMD+=("--args=$CLIENT_ARGS")
fi

if [[ "$VALIDATION_MODE" != "off" && "$BACKEND" != "vulkan" ]]; then
    echo "validation_note=ignored_for_non_vulkan_backend" >> "$META_LOG"
elif [[ "$VALIDATION_MODE" != "off" && "$VALIDATION_ENABLED" != "true" ]]; then
    echo "validation_note=requested_but_khronos_layer_unavailable" >> "$META_LOG"
fi

if [[ "$VALIDATION_ENABLED" == "true" ]]; then
    if [[ -n "${VK_INSTANCE_LAYERS:-}" ]]; then
        if [[ ":${VK_INSTANCE_LAYERS}:" == *":VK_LAYER_KHRONOS_validation:"* ]]; then
            export VK_INSTANCE_LAYERS
        else
            export VK_INSTANCE_LAYERS="VK_LAYER_KHRONOS_validation:${VK_INSTANCE_LAYERS}"
        fi
    else
        export VK_INSTANCE_LAYERS="VK_LAYER_KHRONOS_validation"
    fi
    if [[ -n "$VALIDATION_LAYER_DIR" ]]; then
        if [[ -n "${VK_ADD_LAYER_PATH:-}" ]]; then
            if [[ ":${VK_ADD_LAYER_PATH}:" != *":${VALIDATION_LAYER_DIR}:"* ]]; then
                export VK_ADD_LAYER_PATH="${VALIDATION_LAYER_DIR}:${VK_ADD_LAYER_PATH}"
            fi
        else
            export VK_ADD_LAYER_PATH="$VALIDATION_LAYER_DIR"
        fi
    fi
    export VK_LOADER_DEBUG="${VK_LOADER_DEBUG:-error,warn,layer}"
    if [[ "${MATTMC_GRAPHICS_VALIDATION_PROFILE:-$VALIDATION_MODE}" == "routine" || "${MATTMC_GRAPHICS_VALIDATION_PROFILE:-$VALIDATION_MODE}" == "standard" ]]; then
        export VK_LAYER_SETTINGS="${VK_LAYER_SETTINGS:-validate_sync=true,validate_best_practices=true}"
    elif [[ "${MATTMC_GRAPHICS_VALIDATION_PROFILE:-$VALIDATION_MODE}" == "deep" ]]; then
        export VK_LAYER_SETTINGS="${VK_LAYER_SETTINGS:-validate_sync=true,validate_best_practices=true,gpuav_enable=true,printf_enable=true}"
    fi
    {
        echo "vk_instance_layers=$VK_INSTANCE_LAYERS"
        echo "vk_add_layer_path=${VK_ADD_LAYER_PATH:-unset}"
        echo "vk_loader_debug=$VK_LOADER_DEBUG"
        echo "vk_layer_settings=${VK_LAYER_SETTINGS:-}"
    } >> "$META_LOG"
fi

if [[ "$SHADER_INPUT_PARITY" != "off" ]]; then
    SHADER_INPUT_PARITY_JAVA_OPTIONS=(
        "-Dmattmc.vulkan.traceShaderInputParity=true"
        "-Dmattmc.vulkan.traceShaderInputParity.maxLogs=$SHADER_INPUT_PARITY_MAX_LOGS"
        "-Dmattmc.vulkan.deterministicLightmapParity=true"
        "-Dmattmc.vulkan.traceLightmapInfoParity=true"
        "-Dmattmc.vulkan.traceLightmapInfoParity.maxLogs=$LIGHTMAP_INFO_PARITY_MAX_LOGS"
    )
    if [[ "$SHADER_INPUT_PARITY" == "full" ]]; then
        SHADER_INPUT_PARITY_JAVA_OPTIONS+=(
            "-Dmattmc.vulkan.traceStandaloneUniformBlockMembers=true"
            "-Dmattmc.vulkan.traceShaderInputParity.fullUniforms=true"
        )
    fi

if [[ -n "${JAVA_TOOL_OPTIONS:-}" ]]; then
    export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS ${SHADER_INPUT_PARITY_JAVA_OPTIONS[*]}"
    else
        export JAVA_TOOL_OPTIONS="${SHADER_INPUT_PARITY_JAVA_OPTIONS[*]}"
    fi

    {
        echo "shader_input_parity_java_options=${SHADER_INPUT_PARITY_JAVA_OPTIONS[*]}"
        echo "java_tool_options=$JAVA_TOOL_OPTIONS"
        echo "deterministic_lightmap_parity=true"
    } >> "$META_LOG"
fi

setsid "${GRADLE_CMD[@]}" > "$RUN_LOG" 2>&1 &
GRADLE_PID=$!
echo "gradle_pid=$GRADLE_PID" >> "$META_LOG"

elapsed=0
title_screen_capture_completed="false"
title_screen_receipt_observed="false"
title_screen_receipt_wait_reported="false"
title_screen_receipt_ready() {
    if [[ "$TITLE_SCREEN_CAPTURE" != "true" || "${MATTMC_TITLE_SCREEN_REQUIRE_RECEIPT:-false}" != "true" ]]; then
        return 0
    fi
    if [[ "$TITLE_SCREEN_TRANSITION_CAPTURE" == "true" ]]; then
        if [[ "$title_screen_receipt_observed" != "true" ]] && grep -Fq "[MattMC graphics audit] title-screen semantic receipt" "$RUN_LOG" 2>/dev/null; then
            title_screen_receipt_observed="true"
            echo "title_screen_receipt_observed=true" >> "$META_LOG"
            echo "title_screen_receipt_elapsed=$elapsed" >> "$META_LOG"
        fi
        return 0
    fi
    if [[ "$title_screen_receipt_observed" == "true" ]]; then
        return 0
    fi
    if grep -Fq "[MattMC graphics audit] title-screen semantic receipt" "$RUN_LOG" 2>/dev/null; then
        title_screen_receipt_observed="true"
        echo "title_screen_receipt_observed=true" >> "$META_LOG"
        echo "title_screen_receipt_elapsed=$elapsed" >> "$META_LOG"
        return 0
    fi
    if [[ "$title_screen_receipt_wait_reported" != "true" ]]; then
        title_screen_receipt_wait_reported="true"
        echo "title_screen_capture_waiting_for_receipt=true" >> "$META_LOG"
    fi
    return 1
}
while kill -0 "$GRADLE_PID" 2>/dev/null; do
    sleep 1
    elapsed=$((elapsed + 1))

    CLIENT_PID="$(find_client_pid)"

    # A presented title image completes a static capture, but must not truncate
    # an explicitly requested transition timeline. The later bounded-count and
    # title-receipt gate owns completion for that diagnostic, matching Current.
    if capture_frozen_title_presented_frame "$CLIENT_PID" && [[ "$TITLE_SCREEN_TRANSITION_CAPTURE" != "true" ]]; then
        title_screen_capture_completed="true"
        echo "frozen_title_presented_frame_ack=$MATTMC_FROZEN_TITLE_FRAME_CAPTURE_DIR/title_frame_capture.ack.json" >> "$META_LOG"
        echo "title_screen_capture_complete_elapsed=$elapsed" >> "$META_LOG"
        kill -TERM -- "-$GRADLE_PID" 2>/dev/null || true
        break
    fi

    if [[ "$screenshot_enabled" == "true" && "$SCREENSHOT_INTERVAL_SECS" -gt 0 && "$elapsed" -ge "$SCREENSHOT_START_DELAY_SECS" && $(((elapsed - SCREENSHOT_START_DELAY_SECS) % SCREENSHOT_INTERVAL_SECS)) -eq 0 ]]; then
        if ! title_screen_receipt_ready; then
            continue
        fi
        capture_root_screenshot "tick" "$elapsed" "$CLIENT_PID"
        # A transition may fill its finite timeline while the ordinary loading
        # overlay is still active. Those samples document startup; they do not
        # prove TitleScreen rendered. Keep observing until the existing
        # log-only TitleScreen receipt arrives (or the normal timeout), then
        # terminate without changing Frozen rendering behavior.
        # When the cross-repository harness armed Frozen's presented-frame
        # handshake, a semantic TitleScreen receipt is intentionally weaker
        # than the post-present screenshot acknowledgment.  Do not let the
        # bounded desktop timeline mask a still-running title fade.
        if [[ "$TITLE_SCREEN_CAPTURE" == "true" && "$screenshot_count" -ge "$SCREENSHOT_MAX_COUNT" && ( "$TITLE_SCREEN_TRANSITION_CAPTURE" != "true" || "$title_screen_receipt_observed" == "true" ) && ( -z "${MATTMC_FROZEN_TITLE_FRAME_CAPTURE_DIR:-}" || -f "$MATTMC_FROZEN_TITLE_FRAME_CAPTURE_DIR/title_frame_capture.ack.json" ) ]]; then
            title_screen_capture_completed="true"
            echo "title_screen_capture_complete_elapsed=$elapsed" >> "$META_LOG"
            kill -TERM -- "-$GRADLE_PID" 2>/dev/null || true
            break
        fi
    fi

    capture_deterministic_requests "$CLIENT_PID"
    if [[ "$(deterministic_capture_status || true)" == "complete" ]]; then
        if [[ -n "$FRAME_BENCHMARK_STATUS" ]]; then
            benchmark_status="$(frame_benchmark_status)"
            if [[ "$benchmark_status" != "complete" && "$benchmark_status" != "failed" ]]; then
                echo "deterministic_capture_waiting_for_frame_benchmark=true" >> "$META_LOG"
                continue
            fi
        fi
        echo "deterministic_capture_complete_elapsed=$elapsed" >> "$META_LOG"
        if ! wait_for_deterministic_shutdown; then
            kill -TERM -- "-$GRADLE_PID" 2>/dev/null || true
            sleep 3
            kill -KILL -- "-$GRADLE_PID" 2>/dev/null || true
        fi
        break
    fi

    if [[ "$dump_taken" == "false" && "$elapsed" -ge "$DUMP_SECS" ]]; then
        {
            echo "===== process snapshot at dump point (elapsed=${elapsed}s, gradle_pid=${GRADLE_PID}) ====="
            ps -eo pid,ppid,pgid,cmd | awk -v pg="$GRADLE_PID" '$3 == pg {print}'
            echo
            echo "===== jcmd -l ====="
            jcmd -l || true
        } > "$PROCESS_SNAPSHOT" 2>&1

        if [[ "$screenshot_enabled" == "true" ]]; then
            xwininfo -root -tree > "$WINDOW_TREE_DUMP" 2>&1 || true
            echo "window_tree_dump=$WINDOW_TREE_DUMP" >> "$META_LOG"
        fi

        {
            echo "dump_epoch=$(date +%s)"
            echo "dump_elapsed_secs=$elapsed"
            echo "client_pid=${CLIENT_PID:-none}"
        } >> "$META_LOG"

        if [[ -n "$CLIENT_PID" ]]; then
            {
                echo "===== jcmd Thread.print (pid=$CLIENT_PID) ====="
                jcmd "$CLIENT_PID" Thread.print
            } > "$THREAD_DUMP" 2>&1 || true
            capture_root_screenshot "dump" "$elapsed"
            dump_taken="true"
        else
            echo "No Minecraft Java PID found at dump point." > "$THREAD_DUMP"
            capture_root_screenshot "dump" "$elapsed"
            dump_taken="true"
        fi
    fi

    if [[ "$elapsed" -ge "$MAX_SECS" ]]; then
        timed_out="true"
        break
    fi
done

if [[ "$timed_out" == "true" ]]; then
    echo "timeout=true" >> "$META_LOG"
    capture_root_screenshot "timeout" "$elapsed"
    # Final best-effort dump before terminating, in case dump window was too early.
    CLIENT_PID="$(find_client_pid)"
    if [[ -n "$CLIENT_PID" ]]; then
        {
            echo
            echo "===== final jcmd Thread.print before termination (pid=$CLIENT_PID) ====="
            jcmd "$CLIENT_PID" Thread.print
        } >> "$THREAD_DUMP" 2>&1 || true
    fi

    # Kill the whole process group started with setsid.
    kill -TERM -- "-$GRADLE_PID" 2>/dev/null || true
    sleep 8
    if kill -0 "$GRADLE_PID" 2>/dev/null; then
        kill -KILL -- "-$GRADLE_PID" 2>/dev/null || true
    fi
fi

if wait "$GRADLE_PID"; then
    exit_code=0
else
    exit_code=$?
fi
if [[ "$title_screen_capture_completed" == "true" ]]; then
    exit_code=0
fi

echo "exit_code=$exit_code" >> "$META_LOG"
echo "title_screen_capture_completed=$title_screen_capture_completed" >> "$META_LOG"
echo "end_epoch=$(date +%s)" >> "$META_LOG"

copy_config_snapshot "$CONFIG_AFTER_DIR"
echo "config_after=$CONFIG_AFTER_DIR" >> "$META_LOG"

if [[ -f "$RUN_DIR/logs/latest.log" ]]; then
    cp "$RUN_DIR/logs/latest.log" "$LATEST_LOG_COPY"
    tail -n 240 "$LATEST_LOG_COPY" > "$LATEST_TAIL" || true
fi

find "$RUN_DIR" -maxdepth 1 -type f -name 'hs_err_pid*.log' -printf '%T@ %p\n' \
    | awk -v start="$START_EPOCH" '$1 >= start {print $2}' \
    | sort > "$HSERR_LIST"

while IFS= read -r err_file; do
    if [[ -f "$err_file" ]]; then
        cp "$err_file" "$ARTIFACT_DIR/$(basename "$err_file" .log)_${RUN_ID}.log"
    fi
done < "$HSERR_LIST"

copy_recent_files_since_start "$RUN_DIR/crash-reports" "$CRASH_REPORT_DIR" "$CRASH_REPORT_LIST"
copy_recent_files_since_start "$RUN_DIR/debug" "$DEBUG_SNAPSHOT_DIR" "$DEBUG_FILE_LIST"

if [[ -d "$RUN_DIR/patched_shaders" ]]; then
    cp -a "$RUN_DIR/patched_shaders" "$PATCHED_SHADERS_SNAPSHOT"
    {
        echo "source=$RUN_DIR/patched_shaders"
        while IFS= read -r file_path; do
            rel_path="${file_path#$RUN_DIR/patched_shaders/}"
            printf '%s %s\n' "$(sha256sum "$file_path" | awk '{print $1}')" "$rel_path"
        done < <(find "$RUN_DIR/patched_shaders" -type f | sort)
    } > "$PATCHED_SHADERS_MANIFEST"
fi

: > "$SHADER_EVENT_LOG"
append_filtered_section "runClient log" "$RUN_LOG" "$SHADER_EVENT_PATTERN" "$SHADER_EVENT_LOG"
append_filtered_section "latest.log" "$LATEST_LOG_COPY" "$SHADER_EVENT_PATTERN" "$SHADER_EVENT_LOG"
while IFS= read -r crash_file; do
    [[ -z "$crash_file" ]] && continue
    append_filtered_section "crash report" "$crash_file" "$SHADER_EVENT_PATTERN" "$SHADER_EVENT_LOG"
done < "$CRASH_REPORT_LIST"
while IFS= read -r err_file; do
    [[ -z "$err_file" ]] && continue
    append_filtered_section "hs_err" "$err_file" "$SHADER_EVENT_PATTERN" "$SHADER_EVENT_LOG"
done < "$HSERR_LIST"

: > "$VALIDATION_EVENT_LOG"
append_filtered_section "runClient log" "$RUN_LOG" "$VALIDATION_EVENT_PATTERN" "$VALIDATION_EVENT_LOG"
append_filtered_section "latest.log" "$LATEST_LOG_COPY" "$VALIDATION_EVENT_PATTERN" "$VALIDATION_EVENT_LOG"
while IFS= read -r crash_file; do
    [[ -z "$crash_file" ]] && continue
    append_filtered_section "crash report" "$crash_file" "$VALIDATION_EVENT_PATTERN" "$VALIDATION_EVENT_LOG"
done < "$CRASH_REPORT_LIST"

{
    echo "backend=$BACKEND"
    echo "validation_mode=$VALIDATION_MODE"
    echo "validation_enabled=$VALIDATION_ENABLED"
    echo "validation_layer_available=$VALIDATION_LAYER_AVAILABLE"
    echo "validation_layer_manifest=${VALIDATION_LAYER_MANIFEST:-unavailable}"
    echo "shader_input_parity=$SHADER_INPUT_PARITY"
    echo "shader_input_parity_max_logs=$SHADER_INPUT_PARITY_MAX_LOGS"
    echo "lightmap_info_parity_max_logs=$LIGHTMAP_INFO_PARITY_MAX_LOGS"
    echo "effective_enable_shaders=${EFFECTIVE_ENABLE_SHADERS:-unset}"
    echo "effective_shader_pack=${EFFECTIVE_SHADER_PACK:-unset}"
    echo "run_log=$RUN_LOG"
    echo "latest_log=${LATEST_LOG_COPY:-missing}"
    echo "shader_events=$SHADER_EVENT_LOG"
    echo "validation_events=$VALIDATION_EVENT_LOG"
    echo "patched_shaders_snapshot=${PATCHED_SHADERS_SNAPSHOT:-absent}"
    echo "patched_shaders_manifest=${PATCHED_SHADERS_MANIFEST:-absent}"
    echo "crash_report_count=$(count_list_entries "$CRASH_REPORT_LIST")"
    echo "hs_err_count=$(count_list_entries "$HSERR_LIST")"
    echo "debug_artifact_count=$(count_list_entries "$DEBUG_FILE_LIST")"
    echo
    echo "===== key events from runClient log ====="
    grep -nEi "$KEY_SUMMARY_PATTERN" "$RUN_LOG" | head -n 200 || true
} > "$SHADER_SUMMARY"

echo "Capture complete."
echo "- Meta:        $META_LOG"
echo "- System:      $SYSTEM_SNAPSHOT"
echo "- Git:         $GIT_SNAPSHOT"
echo "- Shader info: $SHADERPACK_INFO"
echo "- Shader sum:  $SHADER_SUMMARY"
echo "- Shader log:  $SHADER_EVENT_LOG"
echo "- Validation:  $VALIDATION_EVENT_LOG"
echo "- Run log:     $RUN_LOG"
if [[ -f "$LATEST_LOG_COPY" ]]; then
    echo "- Latest log:  $LATEST_LOG_COPY"
fi
echo "- Thread dump: $THREAD_DUMP"
echo "- Proc snap:   $PROCESS_SNAPSHOT"
echo "- Latest tail: $LATEST_TAIL"
echo "- hs_err list: $HSERR_LIST"
echo "- Crash list:  $CRASH_REPORT_LIST"
echo "- Debug list:  $DEBUG_FILE_LIST"
if [[ -d "$PATCHED_SHADERS_SNAPSHOT" ]]; then
    echo "- Patched shaders: $PATCHED_SHADERS_SNAPSHOT"
fi
if [[ -f "$WINDOW_TREE" ]]; then
    echo "- Window tree: $WINDOW_TREE"
fi

if [[ "$timed_out" == "true" ]]; then
    echo "Result: timed out after ${MAX_SECS}s and was terminated automatically."
elif [[ "$exit_code" -ne 0 ]]; then
    echo "Result: exited with non-zero code $exit_code."
else
    echo "Result: exited cleanly."
fi
