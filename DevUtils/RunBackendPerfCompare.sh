#!/usr/bin/env bash

set -euo pipefail

usage() {
    cat <<'EOF'
Usage: ./DevUtils/RunBackendPerfCompare.sh [options]

Runs back-to-back MattMC client sessions on OpenGL and Vulkan and collects
performance diagnostics for each backend.

Artifacts collected per backend:
  - Gradle run log
  - latest.log tail
  - start/end thread dumps
  - Java Flight Recorder (.jfr)
  - NVIDIA GPU telemetry (if nvidia-smi is available)
  - optional perf data (if perf is available)
  - screenshots (if import and DISPLAY are available)
  - custom /profile HTML/TXT reports (only if xdotool automation is available)

Options:
  --world NAME             Quick-play into a singleplayer world.
  --client-args STRING     Extra client args passed to runClient.
  --warmup-secs N          Seconds to wait before starting measurement. Default: 45
  --sample-secs N          Seconds to record profiling data. Default: 30
  --shutdown-grace-secs N  Seconds to wait after stopping profiling before kill. Default: 8
  --launch-timeout-secs N  Seconds to wait for the client JVM/window. Default: 240
  --backends LIST          Comma-separated backends. Default: opengl,vulkan
  --artifact-dir PATH      Output directory. Default: logs/auto-profile/<timestamp>
  --no-profile-command     Skip in-game /profile automation even if xdotool exists.
  -h, --help               Show this help.

Environment overrides:
  PERF_MODE=off|stat|record        Default: stat
  JFR_SETTINGS=<settings-name>     Default: profile
  SCREENSHOT_INTERVAL_SECS=N       Default: 15
  SCREENSHOT_MAX_COUNT=N           Default: 4
  SCREENSHOT_START_DELAY_SECS=N    Default: 0

Examples:
  ./DevUtils/RunBackendPerfCompare.sh --world Origin --sample-secs 45
  PERF_MODE=record ./DevUtils/RunBackendPerfCompare.sh --client-args '--quickPlaySingleplayer=Origin'
EOF
}

WORLD_NAME=""
CLIENT_ARGS="${CLIENT_ARGS:-}"
WARMUP_SECS="${WARMUP_SECS:-45}"
SAMPLE_SECS="${SAMPLE_SECS:-30}"
SHUTDOWN_GRACE_SECS="${SHUTDOWN_GRACE_SECS:-8}"
LAUNCH_TIMEOUT_SECS="${LAUNCH_TIMEOUT_SECS:-240}"
BACKENDS_CSV="${BACKENDS:-opengl,vulkan}"
ARTIFACT_DIR=""
PROFILE_COMMAND_MODE="auto"

PERF_MODE="${PERF_MODE:-stat}"
JFR_SETTINGS="${JFR_SETTINGS:-profile}"
SCREENSHOT_INTERVAL_SECS="${SCREENSHOT_INTERVAL_SECS:-15}"
SCREENSHOT_MAX_COUNT="${SCREENSHOT_MAX_COUNT:-4}"
SCREENSHOT_START_DELAY_SECS="${SCREENSHOT_START_DELAY_SECS:-0}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --world)
            WORLD_NAME="${2:-}"
            shift 2
            ;;
        --client-args)
            CLIENT_ARGS="${2:-}"
            shift 2
            ;;
        --warmup-secs)
            WARMUP_SECS="${2:-}"
            shift 2
            ;;
        --sample-secs)
            SAMPLE_SECS="${2:-}"
            shift 2
            ;;
        --shutdown-grace-secs)
            SHUTDOWN_GRACE_SECS="${2:-}"
            shift 2
            ;;
        --launch-timeout-secs)
            LAUNCH_TIMEOUT_SECS="${2:-}"
            shift 2
            ;;
        --backends)
            BACKENDS_CSV="${2:-}"
            shift 2
            ;;
        --artifact-dir)
            ARTIFACT_DIR="${2:-}"
            shift 2
            ;;
        --no-profile-command)
            PROFILE_COMMAND_MODE="off"
            shift
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

for value_name in WARMUP_SECS SAMPLE_SECS SHUTDOWN_GRACE_SECS LAUNCH_TIMEOUT_SECS SCREENSHOT_INTERVAL_SECS SCREENSHOT_MAX_COUNT SCREENSHOT_START_DELAY_SECS; do
    value="${!value_name}"
    if ! [[ "$value" =~ ^[0-9]+$ ]]; then
        echo "$value_name must be an integer, got: $value" >&2
        exit 1
    fi
done

if [[ "$PERF_MODE" != "off" && "$PERF_MODE" != "stat" && "$PERF_MODE" != "record" ]]; then
    echo "PERF_MODE must be one of: off, stat, record" >&2
    exit 1
fi

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

RUN_DIR="$PROJECT_ROOT/run"
OPTIONS_FILE="$RUN_DIR/options.txt"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
if [[ -z "$ARTIFACT_DIR" ]]; then
    ARTIFACT_DIR="$PROJECT_ROOT/logs/auto-profile/$TIMESTAMP"
fi
mkdir -p "$ARTIFACT_DIR"

SUMMARY_FILE="$ARTIFACT_DIR/summary.txt"
MANIFEST_FILE="$ARTIFACT_DIR/manifest.txt"

if [[ -n "$WORLD_NAME" ]]; then
    if [[ -n "$CLIENT_ARGS" ]]; then
        CLIENT_ARGS="$CLIENT_ARGS --quickPlaySingleplayer=$WORLD_NAME"
    else
        CLIENT_ARGS="--quickPlaySingleplayer=$WORLD_NAME"
    fi
fi

SPIRV_COMPILER="$PROJECT_ROOT/libraries/deps/glslangValidator"
if [[ -x "$SPIRV_COMPILER" ]]; then
    export VULKANIC_SPIRV_COMPILER="$SPIRV_COMPILER"
fi

ORIGINAL_BACKEND=""
if [[ -f "$OPTIONS_FILE" ]]; then
    ORIGINAL_BACKEND="$(awk -F= '/^graphics_backend=/{print $2; exit}' "$OPTIONS_FILE" || true)"
fi

ACTIVE_GRADLE_PID=""
ACTIVE_GPU_MONITOR_PID=""
ACTIVE_PERF_PID=""
LAST_GRADLE_EXIT_CODE=""

restore_backend() {
    if [[ -n "$ORIGINAL_BACKEND" && -f "$OPTIONS_FILE" ]]; then
        if grep -q '^graphics_backend=' "$OPTIONS_FILE"; then
            sed -i "s/^graphics_backend=.*/graphics_backend=$ORIGINAL_BACKEND/" "$OPTIONS_FILE"
        else
            echo "graphics_backend=$ORIGINAL_BACKEND" >> "$OPTIONS_FILE"
        fi
    fi
}

cleanup_active_children() {
    if [[ -n "$ACTIVE_GPU_MONITOR_PID" ]] && kill -0 "$ACTIVE_GPU_MONITOR_PID" 2>/dev/null; then
        kill "$ACTIVE_GPU_MONITOR_PID" 2>/dev/null || true
        wait "$ACTIVE_GPU_MONITOR_PID" 2>/dev/null || true
    fi
    ACTIVE_GPU_MONITOR_PID=""

    if [[ -n "$ACTIVE_PERF_PID" ]] && kill -0 "$ACTIVE_PERF_PID" 2>/dev/null; then
        wait "$ACTIVE_PERF_PID" 2>/dev/null || true
    fi
    ACTIVE_PERF_PID=""
}

shutdown_active_run() {
    cleanup_active_children
    if [[ -n "$ACTIVE_GRADLE_PID" ]]; then
        if kill -0 "$ACTIVE_GRADLE_PID" 2>/dev/null; then
            kill -TERM -- "-$ACTIVE_GRADLE_PID" 2>/dev/null || true
            sleep 5
            if kill -0 "$ACTIVE_GRADLE_PID" 2>/dev/null; then
                kill -KILL -- "-$ACTIVE_GRADLE_PID" 2>/dev/null || true
            fi
        fi

        if wait "$ACTIVE_GRADLE_PID"; then
            LAST_GRADLE_EXIT_CODE=0
        else
            LAST_GRADLE_EXIT_CODE=$?
        fi
    fi
    ACTIVE_GRADLE_PID=""
}

final_cleanup() {
    shutdown_active_run
    restore_backend
}

trap final_cleanup EXIT INT TERM

set_backend() {
    local backend="$1"

    if [[ ! -f "$OPTIONS_FILE" ]]; then
        echo "ERROR: Missing options file: $OPTIONS_FILE" >&2
        exit 1
    fi

    if grep -q '^graphics_backend=' "$OPTIONS_FILE"; then
        sed -i "s/^graphics_backend=.*/graphics_backend=$backend/" "$OPTIONS_FILE"
    else
        echo "graphics_backend=$backend" >> "$OPTIONS_FILE"
    fi
}

find_client_pid_for_group() {
    local gradle_pid="$1"
    local pid

    pid="$(ps -eo pid=,pgid=,cmd= | awk -v pg="$gradle_pid" '$2 == pg && $0 ~ /java/ && $0 ~ /(devlaunchinjector|minecraft|fabric|MattMC-1\.21)/ {print $1; exit}' || true)"
    if [[ -n "$pid" ]]; then
        printf '%s\n' "$pid"
        return 0
    fi

    pid="$(jcmd -l 2>/dev/null | awk '/devlaunchinjector|minecraft|fabric|MattMC-1\.21/ {print $1; exit}' || true)"
    if [[ -n "$pid" ]]; then
        printf '%s\n' "$pid"
        return 0
    fi

    return 1
}

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

capture_screenshot() {
    local backend_dir="$1"
    local label="$2"
    local client_pid="$3"
    local window_id="root"
    local screenshot_file

    if ! command -v import >/dev/null 2>&1 || [[ -z "${DISPLAY:-}" ]]; then
        return 0
    fi

    if [[ -n "$client_pid" ]] && window_id="$(find_client_window_id "$client_pid" 2>/dev/null)"; then
        :
    else
        window_id="root"
    fi

    screenshot_file="$backend_dir/${label}.png"
    if ! import -window "$window_id" "$screenshot_file" >/dev/null 2>&1; then
        rm -f "$screenshot_file"
    fi
}

maybe_send_profile_command() {
    local window_id="$1"
    local command_text="$2"

    if [[ "$PROFILE_COMMAND_MODE" == "off" ]]; then
        return 1
    fi
    if ! command -v xdotool >/dev/null 2>&1; then
        return 1
    fi
    if [[ -z "$window_id" ]]; then
        return 1
    fi

    xdotool windowactivate --sync "$window_id" >/dev/null 2>&1 || return 1
    sleep 1
    xdotool key --window "$window_id" slash >/dev/null 2>&1 || return 1
    sleep 0.2
    xdotool type --window "$window_id" --delay 12 "${command_text#/}" >/dev/null 2>&1 || return 1
    xdotool key --window "$window_id" Return >/dev/null 2>&1 || return 1
    return 0
}

start_gpu_monitor() {
    local output_file="$1"

    if ! command -v nvidia-smi >/dev/null 2>&1; then
        return 1
    fi

    (
        echo "# nvidia-smi dmon -s pucmt -d 1"
        nvidia-smi dmon -s pucmt -d 1
    ) > "$output_file" 2>&1 &
    ACTIVE_GPU_MONITOR_PID=$!
    return 0
}

start_perf_capture() {
    local client_pid="$1"
    local backend_dir="$2"
    local duration_secs="$3"
    local perf_log="$backend_dir/perf_${PERF_MODE}.log"

    if [[ "$PERF_MODE" == "off" ]] || ! command -v perf >/dev/null 2>&1; then
        return 1
    fi

    if [[ "$PERF_MODE" == "stat" ]]; then
        perf stat -d -d -d -p "$client_pid" -- sleep "$duration_secs" > "$perf_log" 2>&1 &
    else
        perf record -F 99 -g -o "$backend_dir/perf.data" -p "$client_pid" -- sleep "$duration_secs" > "$perf_log" 2>&1 &
    fi
    ACTIVE_PERF_PID=$!
    return 0
}

collect_jcmd_snapshot() {
    local client_pid="$1"
    local output_prefix="$2"

    if [[ -z "$client_pid" ]] || ! command -v jcmd >/dev/null 2>&1; then
        return 0
    fi

    jcmd "$client_pid" Thread.print > "${output_prefix}_threads.txt" 2>&1 || true
    jcmd "$client_pid" GC.heap_info > "${output_prefix}_heap.txt" 2>&1 || true
    jcmd "$client_pid" VM.flags > "${output_prefix}_flags.txt" 2>&1 || true
    jcmd "$client_pid" VM.native_memory summary > "${output_prefix}_native_memory.txt" 2>&1 || true
}

collect_new_profile_reports() {
    local since_epoch="$1"
    local output_dir="$2"
    mkdir -p "$output_dir"

    while IFS= read -r file_path; do
        [[ -z "$file_path" ]] && continue
        cp "$file_path" "$output_dir/" 2>/dev/null || true
    done < <(find "$PROJECT_ROOT" "$RUN_DIR" -type f \( -path '*/debug/profiling/*' -o -path '*/debug/profiling/html/*' \) -printf '%T@ %p\n' 2>/dev/null | awk -v start="$since_epoch" '$1 >= start {sub(/^[^ ]+ /, ""); print}')
}

wait_for_client_pid() {
    local gradle_pid="$1"
    local waited=0
    local client_pid=""

    while [[ "$waited" -lt "$LAUNCH_TIMEOUT_SECS" ]]; do
        if ! kill -0 "$gradle_pid" 2>/dev/null; then
            break
        fi
        client_pid="$(find_client_pid_for_group "$gradle_pid" || true)"
        if [[ -n "$client_pid" ]]; then
            printf '%s\n' "$client_pid"
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
    done

    return 1
}

run_backend_capture() {
    local backend="$1"
    local backend_dir="$ARTIFACT_DIR/$backend"
    local run_started_epoch="$(date +%s)"
    local run_log="$backend_dir/runClient.log"
    local meta_log="$backend_dir/meta.txt"
    local latest_tail="$backend_dir/latest.log.tail"
    local gpu_log="$backend_dir/nvidia_dmon.log"
    local jfr_file="$backend_dir/$backend.jfr"
    local jfr_name="MattMC_${backend}_${TIMESTAMP}"
    local profile_report_dir="$backend_dir/profile_reports"
    local gradle_cmd
    local client_pid=""
    local client_window_id=""
    local start_measure_epoch=""
    local profile_started="false"
    local jfr_started="false"
    local exit_code=""

    mkdir -p "$backend_dir"

    set_backend "$backend"

    {
        echo "backend=$backend"
        echo "started_epoch=$run_started_epoch"
        echo "warmup_secs=$WARMUP_SECS"
        echo "sample_secs=$SAMPLE_SECS"
        echo "shutdown_grace_secs=$SHUTDOWN_GRACE_SECS"
        echo "launch_timeout_secs=$LAUNCH_TIMEOUT_SECS"
        echo "client_args=$CLIENT_ARGS"
        echo "perf_mode=$PERF_MODE"
        echo "jfr_settings=$JFR_SETTINGS"
        echo "display=${DISPLAY:-unset}"
        echo "xdotool=$(command -v xdotool || echo missing)"
        echo "nvidia_smi=$(command -v nvidia-smi || echo missing)"
        echo "perf=$(command -v perf || echo missing)"
    } > "$meta_log"

    echo "[$backend] launching runClient"
    gradle_cmd=(./gradlew -x test runClient)
    if [[ -n "$CLIENT_ARGS" ]]; then
        gradle_cmd+=("--args=$CLIENT_ARGS")
    fi

    setsid "${gradle_cmd[@]}" > "$run_log" 2>&1 &
    ACTIVE_GRADLE_PID=$!
    echo "gradle_pid=$ACTIVE_GRADLE_PID" >> "$meta_log"

    client_pid="$(wait_for_client_pid "$ACTIVE_GRADLE_PID" || true)"
    if [[ -z "$client_pid" ]]; then
        echo "[$backend] failed to find client pid within timeout" | tee -a "$meta_log"
        shutdown_active_run
        return 1
    fi

    echo "client_pid=$client_pid" >> "$meta_log"
    client_window_id="$(find_client_window_id "$client_pid" || true)"
    if [[ -n "$client_window_id" ]]; then
        echo "window_id=$client_window_id" >> "$meta_log"
    fi

    collect_jcmd_snapshot "$client_pid" "$backend_dir/start"
    capture_screenshot "$backend_dir" "start" "$client_pid"

    echo "[$backend] warmup ${WARMUP_SECS}s"
    sleep "$WARMUP_SECS"

    start_measure_epoch="$(date +%s)"
    echo "measurement_started_epoch=$start_measure_epoch" >> "$meta_log"
    capture_screenshot "$backend_dir" "warmup_complete" "$client_pid"

    if start_gpu_monitor "$gpu_log"; then
        echo "gpu_monitor=started" >> "$meta_log"
    else
        echo "gpu_monitor=skipped" >> "$meta_log"
    fi

    if start_perf_capture "$client_pid" "$backend_dir" "$SAMPLE_SECS"; then
        echo "perf_capture=started" >> "$meta_log"
    else
        echo "perf_capture=skipped" >> "$meta_log"
    fi

    if command -v jcmd >/dev/null 2>&1; then
        if jcmd "$client_pid" JFR.start name="$jfr_name" settings="$JFR_SETTINGS" > "$backend_dir/jfr_start.txt" 2>&1; then
            jfr_started="true"
            echo "jfr_capture=started" >> "$meta_log"
        else
            echo "jfr_capture=failed" >> "$meta_log"
        fi
    else
        echo "jfr_capture=skipped" >> "$meta_log"
    fi

    if maybe_send_profile_command "$client_window_id" "/profile start"; then
        profile_started="true"
        echo "profile_command=started" >> "$meta_log"
        sleep 2
    else
        echo "profile_command=skipped" >> "$meta_log"
    fi

    local screenshot_index=0
    local elapsed=0
    while [[ "$elapsed" -lt "$SAMPLE_SECS" ]]; do
        sleep 1
        elapsed=$((elapsed + 1))
        if [[ "$SCREENSHOT_MAX_COUNT" -gt 0 ]] && [[ "$elapsed" -ge "$SCREENSHOT_START_DELAY_SECS" ]] && [[ "$SCREENSHOT_INTERVAL_SECS" -gt 0 ]] && [[ $(((elapsed - SCREENSHOT_START_DELAY_SECS) % SCREENSHOT_INTERVAL_SECS)) -eq 0 ]]; then
            if [[ "$screenshot_index" -lt "$SCREENSHOT_MAX_COUNT" ]]; then
                screenshot_index=$((screenshot_index + 1))
                capture_screenshot "$backend_dir" "sample_${screenshot_index}_${elapsed}s" "$client_pid"
            fi
        fi
        if ! kill -0 "$ACTIVE_GRADLE_PID" 2>/dev/null; then
            break
        fi
    done

    if [[ "$profile_started" == "true" ]]; then
        maybe_send_profile_command "$client_window_id" "/profile stop" || true
        sleep 3
    fi

    if [[ "$jfr_started" == "true" ]]; then
        jcmd "$client_pid" JFR.stop name="$jfr_name" filename="$jfr_file" > "$backend_dir/jfr_stop.txt" 2>&1 || true
    fi

    cleanup_active_children
    collect_jcmd_snapshot "$client_pid" "$backend_dir/end"
    capture_screenshot "$backend_dir" "end" "$client_pid"
    collect_new_profile_reports "$start_measure_epoch" "$profile_report_dir" || true

    sleep "$SHUTDOWN_GRACE_SECS"
    shutdown_active_run

    if [[ -f "$RUN_DIR/logs/latest.log" ]]; then
        tail -n 400 "$RUN_DIR/logs/latest.log" > "$latest_tail" || true
    fi

    exit_code="${LAST_GRADLE_EXIT_CODE:-unknown}"
    LAST_GRADLE_EXIT_CODE=""

    {
        echo "ended_epoch=$(date +%s)"
        echo "exit_code=${exit_code:-unknown}"
    } >> "$meta_log"

    echo "$backend artifacts: $backend_dir" >> "$SUMMARY_FILE"
    echo "$backend=$backend_dir" >> "$MANIFEST_FILE"
    ACTIVE_GRADLE_PID=""
    return 0
}

IFS=',' read -r -a BACKENDS <<< "$BACKENDS_CSV"
if [[ "${#BACKENDS[@]}" -eq 0 ]]; then
    echo "No backends specified" >&2
    exit 1
fi

{
    echo "MattMC backend performance compare"
    echo "timestamp=$TIMESTAMP"
    echo "artifact_dir=$ARTIFACT_DIR"
    echo "project_root=$PROJECT_ROOT"
    echo "client_args=$CLIENT_ARGS"
    echo "warmup_secs=$WARMUP_SECS"
    echo "sample_secs=$SAMPLE_SECS"
    echo "shutdown_grace_secs=$SHUTDOWN_GRACE_SECS"
    echo "perf_mode=$PERF_MODE"
    echo "jfr_settings=$JFR_SETTINGS"
    echo
} > "$SUMMARY_FILE"
: > "$MANIFEST_FILE"

for backend in "${BACKENDS[@]}"; do
    backend="$(printf '%s' "$backend" | xargs)"
    case "$backend" in
        opengl|vulkan)
            run_backend_capture "$backend"
            ;;
        *)
            echo "Skipping unsupported backend: $backend" | tee -a "$SUMMARY_FILE"
            ;;
    esac
done

restore_backend
trap - EXIT INT TERM

echo "Complete. Artifacts saved to: $ARTIFACT_DIR"
echo "Summary: $SUMMARY_FILE"