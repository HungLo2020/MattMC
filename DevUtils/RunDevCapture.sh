#!/usr/bin/env bash

set -euo pipefail

usage() {
    cat <<'EOF'
Usage: ./DevUtils/RunDevCapture.sh [--backend vulkan|opengl] [--max-secs N] [--dump-secs N]

Runs ./gradlew runClient in a bounded session, captures diagnostics, and
self-terminates so no manual kill is required.

Environment overrides:
  MAX_SECS   (default: 120)
  DUMP_SECS  (default: 45)
EOF
}

BACKEND="vulkan"
MAX_SECS="${MAX_SECS:-120}"
DUMP_SECS="${DUMP_SECS:-45}"

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
} > "$META_LOG"

echo "Starting bounded runClient capture (run_id=$RUN_ID, backend=$BACKEND)"
setsid ./gradlew -x test runClient > "$RUN_LOG" 2>&1 &
GRADLE_PID=$!
echo "gradle_pid=$GRADLE_PID" >> "$META_LOG"

elapsed=0
while kill -0 "$GRADLE_PID" 2>/dev/null; do
    sleep 1
    elapsed=$((elapsed + 1))

    if [[ "$dump_taken" == "false" && "$elapsed" -ge "$DUMP_SECS" ]]; then
        CLIENT_PID="$(find_client_pid)"
        {
            echo "===== process snapshot at dump point (elapsed=${elapsed}s, gradle_pid=${GRADLE_PID}) ====="
            ps -eo pid,ppid,pgid,cmd | awk -v pg="$GRADLE_PID" '$3 == pg {print}'
            echo
            echo "===== jcmd -l ====="
            jcmd -l || true
        } > "$PROCESS_SNAPSHOT" 2>&1

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
            dump_taken="true"
        else
            echo "No Minecraft Java PID found at dump point." > "$THREAD_DUMP"
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

if [[ "$timed_out" == "true" ]]; then
    echo "Result: timed out after ${MAX_SECS}s and was terminated automatically."
elif [[ "$exit_code" -ne 0 ]]; then
    echo "Result: exited with non-zero code $exit_code."
else
    echo "Result: exited cleanly."
fi
