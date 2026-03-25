#!/usr/bin/env bash

set -euo pipefail

usage() {
    cat <<'EOF'
Usage: ./DevUtils/RunDevCapture.sh [--backend vulkan|opengl] [--max-secs N] [--dump-secs N] [--client-args "..."]

Runs ./gradlew runClient in a bounded session, captures diagnostics, and
self-terminates so no manual kill is required.

Environment overrides:
  MAX_SECS   (default: 120)
  DUMP_SECS  (default: 45)
    CLIENT_ARGS
EOF
}

BACKEND="vulkan"
MAX_SECS="${MAX_SECS:-120}"
DUMP_SECS="${DUMP_SECS:-45}"
SCREENSHOT_INTERVAL_SECS="${SCREENSHOT_INTERVAL_SECS:-5}"
SCREENSHOT_MAX_COUNT="${SCREENSHOT_MAX_COUNT:-6}"
SCREENSHOT_START_DELAY_SECS="${SCREENSHOT_START_DELAY_SECS:-0}"
CLIENT_ARGS="${CLIENT_ARGS:-}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --backend)
            BACKEND="${2:-}"
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
        --client-args)
            CLIENT_ARGS="${2:-}"
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

if ! [[ "$MAX_SECS" =~ ^[0-9]+$ ]] || ! [[ "$DUMP_SECS" =~ ^[0-9]+$ ]]; then
    echo "--max-secs and --dump-secs must be integers" >&2
    exit 1
fi

if ! [[ "$SCREENSHOT_INTERVAL_SECS" =~ ^[0-9]+$ ]] || ! [[ "$SCREENSHOT_MAX_COUNT" =~ ^[0-9]+$ ]] || ! [[ "$SCREENSHOT_START_DELAY_SECS" =~ ^[0-9]+$ ]]; then
    echo "SCREENSHOT_INTERVAL_SECS, SCREENSHOT_MAX_COUNT, and SCREENSHOT_START_DELAY_SECS must be integers" >&2
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

RUN_DIR="$PROJECT_ROOT/run"
OPTIONS_FILE="$RUN_DIR/options.txt"
ARTIFACT_DIR="$PROJECT_ROOT/logs/auto-capture"
mkdir -p "$ARTIFACT_DIR"

RUN_ID="$(date +%Y%m%d_%H%M%S)"
START_EPOCH="$(date +%s)"
RUN_LOG="$ARTIFACT_DIR/runClient_${RUN_ID}.log"
META_LOG="$ARTIFACT_DIR/meta_${RUN_ID}.txt"
THREAD_DUMP="$ARTIFACT_DIR/thread_dump_${RUN_ID}.txt"
PROCESS_SNAPSHOT="$ARTIFACT_DIR/process_snapshot_${RUN_ID}.txt"
LATEST_TAIL="$ARTIFACT_DIR/latest_tail_${RUN_ID}.log"
HSERR_LIST="$ARTIFACT_DIR/hs_err_${RUN_ID}.txt"
WINDOW_TREE="$ARTIFACT_DIR/window_tree_${RUN_ID}.txt"
WINDOW_TREE_DUMP="$ARTIFACT_DIR/window_tree_dump_${RUN_ID}.txt"

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
    if target_window="$(find_client_window_id "$client_pid" 2>/dev/null)"; then
        :
    else
        target_window="root"
    fi

    if import -window "$target_window" "$screenshot_file" >/dev/null 2>&1; then
        echo "screenshot_${screenshot_count}=$screenshot_file" >> "$META_LOG"
        echo "screenshot_${screenshot_count}_target=$target_window" >> "$META_LOG"
    else
        rm -f "$screenshot_file"
        echo "screenshot_${screenshot_count}=failed:$label:$elapsed_secs" >> "$META_LOG"
    fi
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
    if rg -q '^graphics_backend=' "$OPTIONS_FILE"; then
        sed -i "s/^graphics_backend=.*/graphics_backend=$BACKEND/" "$OPTIONS_FILE"
    else
        echo "graphics_backend=$BACKEND" >> "$OPTIONS_FILE"
    fi
fi

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

{
    echo "run_id=$RUN_ID"
    echo "start_epoch=$START_EPOCH"
    echo "backend=$BACKEND"
    echo "max_secs=$MAX_SECS"
    echo "dump_secs=$DUMP_SECS"
    echo "client_args=$CLIENT_ARGS"
} > "$META_LOG"

echo "Starting bounded runClient capture (run_id=$RUN_ID, backend=$BACKEND)"
GRADLE_CMD=(./gradlew -x test runClient)
if [[ -n "$CLIENT_ARGS" ]]; then
    GRADLE_CMD+=("--args=$CLIENT_ARGS")
fi
setsid "${GRADLE_CMD[@]}" > "$RUN_LOG" 2>&1 &
GRADLE_PID=$!
echo "gradle_pid=$GRADLE_PID" >> "$META_LOG"

elapsed=0
while kill -0 "$GRADLE_PID" 2>/dev/null; do
    sleep 1
    elapsed=$((elapsed + 1))

    CLIENT_PID="$(find_client_pid)"

    if [[ "$screenshot_enabled" == "true" && "$SCREENSHOT_INTERVAL_SECS" -gt 0 && "$elapsed" -ge "$SCREENSHOT_START_DELAY_SECS" && $(((elapsed - SCREENSHOT_START_DELAY_SECS) % SCREENSHOT_INTERVAL_SECS)) -eq 0 ]]; then
        capture_root_screenshot "tick" "$elapsed" "$CLIENT_PID"
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

echo "exit_code=$exit_code" >> "$META_LOG"
echo "end_epoch=$(date +%s)" >> "$META_LOG"

if [[ -f "$RUN_DIR/logs/latest.log" ]]; then
    tail -n 240 "$RUN_DIR/logs/latest.log" > "$LATEST_TAIL" || true
fi

find "$RUN_DIR" -maxdepth 1 -type f -name 'hs_err_pid*.log' -printf '%T@ %p\n' \
    | awk -v start="$START_EPOCH" '$1 >= start {print $2}' \
    | sort > "$HSERR_LIST"

while IFS= read -r err_file; do
    if [[ -f "$err_file" ]]; then
        cp "$err_file" "$ARTIFACT_DIR/$(basename "$err_file" .log)_${RUN_ID}.log"
    fi
done < "$HSERR_LIST"

echo "Capture complete."
echo "- Meta:        $META_LOG"
echo "- Run log:     $RUN_LOG"
echo "- Thread dump: $THREAD_DUMP"
echo "- Proc snap:   $PROCESS_SNAPSHOT"
echo "- Latest tail: $LATEST_TAIL"
echo "- hs_err list: $HSERR_LIST"
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
