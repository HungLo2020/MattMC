#!/usr/bin/env bash

set -euo pipefail

usage() {
    cat <<'EOF'
Usage: ./DevUtils/RunBackendPerfCompare.sh [options]

Runs back-to-back MattMC client sessions on OpenGL and Vulkan and collects
performance diagnostics for each backend.

After each run, the harness regenerates the root performance-history graph at:
    - mattmc_performance_history.html

Artifacts collected per backend:
  - Gradle run log
  - latest.log tail
  - start/end thread dumps
  - Java Flight Recorder (.jfr)
  - NVIDIA GPU telemetry (if nvidia-smi is available)
    - average FPS and 1% low FPS telemetry (via client metrics recorder when attach tooling is available)
  - optional perf data (if perf is available)
  - screenshots (if import and DISPLAY are available)
  - custom /profile HTML/TXT reports (only if xdotool automation is available)

Options:
    --world NAME             Quick-play into a singleplayer world.
                                                     Default: Origin when no quick-play target is supplied.
    --client-args STRING     Extra client args passed to runClient.
                                                     If no quick-play target is supplied here or via --world,
                                                     the harness defaults to --quickPlaySingleplayer=Origin.
    --runs-per-backend N     Number of runs per backend. Default: 3
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
    ./DevUtils/RunBackendPerfCompare.sh
  ./DevUtils/RunBackendPerfCompare.sh --world Origin --sample-secs 45
  PERF_MODE=record ./DevUtils/RunBackendPerfCompare.sh --client-args '--quickPlaySingleplayer=Origin'
    ./DevUtils/RunBackendPerfCompare.sh --client-args '--quickPlayMultiplayer=127.0.0.1:25565'
EOF
}

has_quick_play_target() {
        local args="$1"
        [[ "$args" == *"--quickPlaySingleplayer="* ]] \
                || [[ "$args" == *"--quickPlayMultiplayer="* ]] \
                || [[ "$args" == *"--quickPlayRealms="* ]]
}

ensure_quick_play_target() {
    if has_quick_play_target "$CLIENT_ARGS"; then
        return 0
    fi

    local default_world="${WORLD_NAME:-Origin}"
    if [[ -n "$CLIENT_ARGS" ]]; then
        CLIENT_ARGS="$CLIENT_ARGS --quickPlaySingleplayer=$default_world"
    else
        CLIENT_ARGS="--quickPlaySingleplayer=$default_world"
    fi
}

WORLD_NAME=""
CLIENT_ARGS="${CLIENT_ARGS:-}"
RUNS_PER_BACKEND="${RUNS_PER_BACKEND:-3}"
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
METRICS_WINDOW_SECS=10

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
        --runs-per-backend)
            RUNS_PER_BACKEND="${2:-}"
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

for value_name in RUNS_PER_BACKEND WARMUP_SECS SAMPLE_SECS SHUTDOWN_GRACE_SECS LAUNCH_TIMEOUT_SECS SCREENSHOT_INTERVAL_SECS SCREENSHOT_MAX_COUNT SCREENSHOT_START_DELAY_SECS; do
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
PERF_HISTORY_GRAPH="$PROJECT_ROOT/mattmc_performance_history.html"
PERF_HISTORY_GRAPH_GENERATOR="$PROJECT_ROOT/DevUtils/generate_perf_history_graph.py"

if [[ -n "$WORLD_NAME" ]]; then
    if [[ -n "$CLIENT_ARGS" ]]; then
        CLIENT_ARGS="$CLIENT_ARGS --quickPlaySingleplayer=$WORLD_NAME"
    else
        CLIENT_ARGS="--quickPlaySingleplayer=$WORLD_NAME"
    fi
fi

ensure_quick_play_target

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
METRICS_ATTACH_BUILD_DIR=""
METRICS_AGENT_JAR=""
METRICS_JAVA_BIN=""
METRICS_ATTACH_READY="false"

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
    if [[ -n "$METRICS_ATTACH_BUILD_DIR" && -d "$METRICS_ATTACH_BUILD_DIR" ]]; then
        rm -rf "$METRICS_ATTACH_BUILD_DIR"
    fi
}

generate_perf_history_graph() {
        local output_file="$1"

        if [[ -x "$PERF_HISTORY_GRAPH_GENERATOR" || -f "$PERF_HISTORY_GRAPH_GENERATOR" ]]; then
                if command -v python3 >/dev/null 2>&1; then
                        python3 "$PERF_HISTORY_GRAPH_GENERATOR" \
                                --logs-dir "$PROJECT_ROOT/logs/auto-profile" \
                                --output "$output_file"
                        return 0
                fi
        fi

        cat > "$output_file" <<'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>MattMC Performance History</title>
    <style>
        body { font-family: sans-serif; margin: 2rem; line-height: 1.5; }
        h1 { margin-top: 0; }
        .note { padding: 1rem; background: #f4f4f4; border-left: 4px solid #666; }
    </style>
</head>
<body>
    <h1>MattMC Performance History</h1>
    <div class="note">
        The performance-history graph could not be regenerated because python3 was not available.
    </div>
</body>
</html>
EOF
}

append_backend_run_summary() {
    local backend="$1"
    local run_index="$2"
    local backend_dir="$3"
    local fps_summary_file="$4"

    echo "$backend run=$run_index artifacts: $backend_dir" >> "$SUMMARY_FILE"
    if [[ -f "$fps_summary_file" ]]; then
        local fps_status
        fps_status="$(awk -F= '/^status=/{print $2; exit}' "$fps_summary_file" || true)"
        if [[ "$fps_status" == "ok" ]]; then
            local avg_fps fps_1pct_low frame_samples sampled_seconds avg_frame_ms
            avg_fps="$(awk -F= '/^avg_fps=/{print $2; exit}' "$fps_summary_file")"
            fps_1pct_low="$(awk -F= '/^fps_1pct_low=/{print $2; exit}' "$fps_summary_file")"
            frame_samples="$(awk -F= '/^frame_samples=/{print $2; exit}' "$fps_summary_file")"
            sampled_seconds="$(awk -F= '/^sampled_seconds=/{print $2; exit}' "$fps_summary_file")"
            avg_frame_ms="$(awk -F= '/^avg_frame_ms=/{print $2; exit}' "$fps_summary_file")"
            echo "$backend run=$run_index fps_avg=$avg_fps fps_1pct_low=$fps_1pct_low frame_samples=$frame_samples sampled_seconds=$sampled_seconds avg_frame_ms=$avg_frame_ms" >> "$SUMMARY_FILE"
        else
            local fps_reason
            fps_reason="$(awk -F= '/^reason=/{print $2; exit}' "$fps_summary_file" || true)"
            echo "$backend run=$run_index fps_metrics=$fps_status reason=${fps_reason:-unknown}" >> "$SUMMARY_FILE"
        fi
    fi
    echo "$backend.run_$run_index=$backend_dir" >> "$MANIFEST_FILE"
}

append_backend_aggregate_summary() {
    local backend="$1"
    local backend_root_dir="$2"
    local aggregate_file="$backend_root_dir/fps_aggregate.txt"

    python3 - "$backend_root_dir" "$RUNS_PER_BACKEND" > "$aggregate_file" <<'PY'
import pathlib
import sys

backend_root = pathlib.Path(sys.argv[1])
expected_runs = int(sys.argv[2])
fps_files = sorted(backend_root.glob("run_*/fps_summary.txt"))
ok_runs = []
for fps_file in fps_files:
    data = {}
    for line in fps_file.read_text(encoding="utf-8", errors="replace").splitlines():
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key.strip()] = value.strip()
    if data.get("status") != "ok":
        continue
    try:
        ok_runs.append(
            {
                "avg_fps": float(data["avg_fps"]),
                "fps_1pct_low": float(data["fps_1pct_low"]),
                "frame_samples": float(data.get("frame_samples", "0") or "0"),
                "sampled_seconds": float(data.get("sampled_seconds", "0") or "0"),
                "avg_frame_ms": float(data.get("avg_frame_ms", "0") or "0"),
            }
        )
    except (KeyError, ValueError):
        continue

print("status=ok" if ok_runs else "status=missing")
print(f"expected_runs={expected_runs}")
print(f"successful_runs={len(ok_runs)}")

if ok_runs:
    count = float(len(ok_runs))
    print(f"avg_fps={sum(run['avg_fps'] for run in ok_runs) / count:.4f}")
    print(f"fps_1pct_low={sum(run['fps_1pct_low'] for run in ok_runs) / count:.4f}")
    print(f"frame_samples={sum(run['frame_samples'] for run in ok_runs) / count:.0f}")
    print(f"sampled_seconds={sum(run['sampled_seconds'] for run in ok_runs) / count:.4f}")
    print(f"avg_frame_ms={sum(run['avg_frame_ms'] for run in ok_runs) / count:.4f}")
PY

    local aggregate_status
    aggregate_status="$(awk -F= '/^status=/{print $2; exit}' "$aggregate_file" || true)"
    if [[ "$aggregate_status" == "ok" ]]; then
        local avg_fps fps_1pct_low successful_runs frame_samples sampled_seconds avg_frame_ms
        successful_runs="$(awk -F= '/^successful_runs=/{print $2; exit}' "$aggregate_file")"
        avg_fps="$(awk -F= '/^avg_fps=/{print $2; exit}' "$aggregate_file")"
        fps_1pct_low="$(awk -F= '/^fps_1pct_low=/{print $2; exit}' "$aggregate_file")"
        frame_samples="$(awk -F= '/^frame_samples=/{print $2; exit}' "$aggregate_file")"
        sampled_seconds="$(awk -F= '/^sampled_seconds=/{print $2; exit}' "$aggregate_file")"
        avg_frame_ms="$(awk -F= '/^avg_frame_ms=/{print $2; exit}' "$aggregate_file")"
        echo "$backend aggregate runs=$successful_runs/$RUNS_PER_BACKEND fps_avg=$avg_fps fps_1pct_low=$fps_1pct_low frame_samples=$frame_samples sampled_seconds=$sampled_seconds avg_frame_ms=$avg_frame_ms" >> "$SUMMARY_FILE"
    else
        local successful_runs
        successful_runs="$(awk -F= '/^successful_runs=/{print $2; exit}' "$aggregate_file" || true)"
        echo "$backend aggregate status=$aggregate_status runs=${successful_runs:-0}/$RUNS_PER_BACKEND" >> "$SUMMARY_FILE"
    fi

    echo "$backend.aggregate=$aggregate_file" >> "$MANIFEST_FILE"
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

init_metrics_attach_tooling() {
    if [[ "$METRICS_ATTACH_READY" == "true" ]]; then
        return 0
    fi

    local java_bin="${JAVA_BIN:-$(command -v java || true)}"
    local javac_bin="${JAVAC_BIN:-$(command -v javac || true)}"
    local jar_bin="${JAR_BIN:-$(command -v jar || true)}"

    if [[ -z "$java_bin" || -z "$javac_bin" || -z "$jar_bin" ]]; then
        return 1
    fi

    METRICS_ATTACH_BUILD_DIR="$(mktemp -d "$ARTIFACT_DIR/.metrics_attach.XXXXXX")"
    local src_dir="$METRICS_ATTACH_BUILD_DIR/src"
    local classes_dir="$METRICS_ATTACH_BUILD_DIR/classes"
    mkdir -p "$src_dir" "$classes_dir"

    cat > "$src_dir/MattMCMetricsAgent.java" <<'EOF'
import java.lang.instrument.Instrumentation;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class MattMCMetricsAgent {
    public static void agentmain(String agentArgs, Instrumentation instrumentation) throws Exception {
        String rawArgs = agentArgs == null ? "" : agentArgs.trim();
        String[] parts = rawArgs.split("\\|", 2);
        String action = parts.length > 0 ? parts[0] : "";
        String logPath = parts.length > 1 ? parts[1] : "";

        try (PrintWriter log = logPath.isEmpty() ? null : new PrintWriter(logPath)) {
            try {
                if (log != null) {
                    log.println("action=" + action);
                }

                Class<?> minecraftClass = null;
                for (Class<?> candidate : instrumentation.getAllLoadedClasses()) {
                    if ("net.minecraft.client.Minecraft".equals(candidate.getName())) {
                        minecraftClass = candidate;
                        break;
                    }
                }
                if (minecraftClass == null) {
                    throw new IllegalStateException("Could not find loaded net.minecraft.client.Minecraft class in target JVM");
                }
                final Class<?> minecraftRuntimeClass = minecraftClass;
                Method getInstance = minecraftRuntimeClass.getMethod("getInstance");
                Object minecraft = null;
                for (int attempt = 0; attempt < 30; attempt++) {
                    minecraft = getInstance.invoke(null);
                    if (minecraft != null) {
                        break;
                    }
                    Thread.sleep(500L);
                }
                if (minecraft == null) {
                    throw new IllegalStateException("Minecraft singleton was still null after waiting for initialization");
                }
                final Object minecraftInstance = minecraft;
                Method executeBlocking = minecraftRuntimeClass.getMethod("executeBlocking", Runnable.class);
                CountDownLatch latch = new CountDownLatch(1);
                final Throwable[] failure = new Throwable[1];

                executeBlocking.invoke(minecraftInstance, (Runnable) () -> {
                    try {
                        if ("start".equals(action)) {
                            Method start = minecraftRuntimeClass.getMethod("debugClientMetricsStart", Consumer.class);
                            Object started = start.invoke(minecraftInstance, (Consumer<Object>) ignored -> { });
                            if (started instanceof Boolean && !((Boolean) started)) {
                                throw new IllegalStateException("debugClientMetricsStart returned false");
                            }
                        } else if ("stop".equals(action)) {
                            Method stop = minecraftRuntimeClass.getDeclaredMethod("debugClientMetricsStop");
                            stop.setAccessible(true);
                            stop.invoke(minecraftInstance);
                        } else {
                            throw new IllegalArgumentException("Unsupported metrics action: " + action);
                        }
                    } catch (Throwable t) {
                        failure[0] = t;
                    } finally {
                        latch.countDown();
                    }
                });

                if (!latch.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for metrics action on render thread: " + action);
                }
                if (failure[0] != null) {
                    throw failure[0];
                }
                if (log != null) {
                    log.println("status=ok");
                }
            } catch (Throwable t) {
                if (log != null) {
                    t.printStackTrace(log);
                    log.flush();
                }
                if (t instanceof Exception exception) {
                    throw exception;
                }
                throw new RuntimeException(t);
            }
        }
    }
}
EOF

    cat > "$src_dir/MattMCMetricsLoader.java" <<'EOF'
import com.sun.tools.attach.VirtualMachine;

public final class MattMCMetricsLoader {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: MattMCMetricsLoader <pid> <agentJar> <action>");
        }

        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try {
            vm.loadAgent(args[1], args[2]);
        } finally {
            vm.detach();
        }
    }
}
EOF

    cat > "$METRICS_ATTACH_BUILD_DIR/manifest.mf" <<'EOF'
Manifest-Version: 1.0
Agent-Class: MattMCMetricsAgent
Can-Redefine-Classes: false
Can-Retransform-Classes: false
EOF

    "$javac_bin" -d "$classes_dir" "$src_dir/MattMCMetricsAgent.java" "$src_dir/MattMCMetricsLoader.java"
    "$jar_bin" cfm "$METRICS_ATTACH_BUILD_DIR/mattmc-metrics-agent.jar" "$METRICS_ATTACH_BUILD_DIR/manifest.mf" -C "$classes_dir" .

    METRICS_JAVA_BIN="$java_bin"
    METRICS_AGENT_JAR="$METRICS_ATTACH_BUILD_DIR/mattmc-metrics-agent.jar"
    METRICS_ATTACH_READY="true"
    return 0
}

invoke_metrics_attach_action() {
    local client_pid="$1"
    local action="$2"
    local log_file="${3:-}"

    if ! init_metrics_attach_tooling; then
        return 1
    fi

    "$METRICS_JAVA_BIN" --add-modules jdk.attach -cp "$METRICS_AGENT_JAR" MattMCMetricsLoader "$client_pid" "$METRICS_AGENT_JAR" "${action}|${log_file}"
}

count_metrics_archives_since() {
    local since_epoch="$1"

    if [[ ! -d "$RUN_DIR/debug/profiling" ]]; then
        printf '0\n'
        return 0
    fi

    find "$RUN_DIR/debug/profiling" -maxdepth 1 -type f -name '*.zip' -printf '%T@ %p\n' 2>/dev/null \
        | awk -v since="$since_epoch" '$1 >= since {count++} END {print count+0}'
}

copy_metrics_archives_since() {
    local since_epoch="$1"
    local output_dir="$2"

    mkdir -p "$output_dir"
    if [[ ! -d "$RUN_DIR/debug/profiling" ]]; then
        return 0
    fi

    find "$RUN_DIR/debug/profiling" -maxdepth 1 -type f -name '*.zip' -printf '%T@ %p\n' 2>/dev/null \
        | awk -v since="$since_epoch" '$1 >= since {sub(/^[^ ]+ /, ""); print}' \
        | while IFS= read -r archive_path; do
            [[ -z "$archive_path" ]] && continue
            cp "$archive_path" "$output_dir/" 2>/dev/null || true
        done
}

wait_for_metrics_archives() {
    local since_epoch="$1"
    local expected_count="$2"
    local timeout_secs="$3"
    local waited=0
    local found=0

    if [[ "$expected_count" -le 0 ]]; then
        return 0
    fi

    while [[ "$waited" -lt "$timeout_secs" ]]; do
        found="$(count_metrics_archives_since "$since_epoch")"
        if [[ "$found" -ge "$expected_count" ]]; then
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
    done

    return 1
}

generate_fps_summary() {
    local metrics_dir="$1"
    local output_file="$2"

    python3 - "$metrics_dir" > "$output_file" <<'PY'
import csv
import math
import sys
import zipfile
from pathlib import Path

metrics_dir = Path(sys.argv[1])
archives = sorted(metrics_dir.glob('*.zip'))
frame_times = []
sources = []

for archive in archives:
    try:
        with zipfile.ZipFile(archive) as zf:
            for name in zf.namelist():
                if not name.endswith('client/metrics/ticking.csv'):
                    continue
                with zf.open(name) as handle:
                    rows = csv.reader(line.decode('utf-8', 'replace') for line in handle)
                    header = next(rows, None)
                    if not header or 'ticktime' not in header:
                        continue
                    idx = header.index('ticktime')
                    for row in rows:
                        if idx >= len(row):
                            continue
                        try:
                            value = float(row[idx])
                        except ValueError:
                            continue
                        if value > 0:
                            frame_times.append(value)
                    sources.append(archive.name)
    except zipfile.BadZipFile:
        continue

if not frame_times:
    print('status=unavailable')
    print('reason=no_ticktime_samples')
    print(f'archive_count={len(archives)}')
    sys.exit(0)

frame_times.sort(reverse=True)
sample_count = len(frame_times)
total_nanos = sum(frame_times)
worst_count = max(1, math.ceil(sample_count * 0.01))
worst_frame_mean = sum(frame_times[:worst_count]) / worst_count
avg_fps = sample_count * 1_000_000_000.0 / total_nanos
low_1pct_fps = 1_000_000_000.0 / worst_frame_mean

print('status=ok')
print(f'archive_count={len(archives)}')
print(f'frame_samples={sample_count}')
print(f'sampled_seconds={total_nanos / 1_000_000_000.0:.6f}')
print(f'avg_fps={avg_fps:.4f}')
print(f'fps_1pct_low={low_1pct_fps:.4f}')
print(f'avg_frame_ms={total_nanos / sample_count / 1_000_000.0:.4f}')
print(f'frame_ms_99pct={(frame_times[worst_count - 1] / 1_000_000.0):.4f}')
print('archives=' + ','.join(sources))
PY
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
    local run_index="$2"
    local backend_root_dir="$ARTIFACT_DIR/$backend"
    local backend_dir="$backend_root_dir/run_$run_index"
    local run_started_epoch="$(date +%s)"
    local run_log="$backend_dir/runClient.log"
    local meta_log="$backend_dir/meta.txt"
    local latest_tail="$backend_dir/latest.log.tail"
    local gpu_log="$backend_dir/nvidia_dmon.log"
    local jfr_file="$backend_dir/${backend}_run_${run_index}.jfr"
    local jfr_name="MattMC_${backend}_run_${run_index}_${TIMESTAMP}"
    local profile_report_dir="$backend_dir/profile_reports"
    local metrics_report_dir="$backend_dir/metrics_reports"
    local fps_summary_file="$backend_dir/fps_summary.txt"
    local gradle_cmd
    local client_pid=""
    local client_window_id=""
    local start_measure_epoch=""
    local profile_started="false"
    local jfr_started="false"
    local metrics_started="false"
    local metrics_expected_reports=0
    local metrics_window_started_elapsed=0
    local exit_code=""

    mkdir -p "$backend_dir"

    set_backend "$backend"

    {
        echo "backend=$backend"
        echo "run_index=$run_index"
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
        echo "python3=$(command -v python3 || echo missing)"
    } > "$meta_log"

    echo "[$backend run $run_index/$RUNS_PER_BACKEND] launching runClient"
    gradle_cmd=(./gradlew -x test runClient)
    if [[ -n "$CLIENT_ARGS" ]]; then
        gradle_cmd+=("--args=$CLIENT_ARGS")
    fi

    setsid "${gradle_cmd[@]}" > "$run_log" 2>&1 &
    ACTIVE_GRADLE_PID=$!
    echo "gradle_pid=$ACTIVE_GRADLE_PID" >> "$meta_log"

    client_pid="$(wait_for_client_pid "$ACTIVE_GRADLE_PID" || true)"
    if [[ -z "$client_pid" ]]; then
        echo "[$backend run $run_index/$RUNS_PER_BACKEND] failed to find client pid within timeout" | tee -a "$meta_log"
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

    echo "[$backend run $run_index/$RUNS_PER_BACKEND] warmup ${WARMUP_SECS}s"
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

    if command -v python3 >/dev/null 2>&1 && invoke_metrics_attach_action "$client_pid" start "$backend_dir/metrics_agent_start.log" > "$backend_dir/metrics_start.txt" 2>&1; then
        metrics_started="true"
        metrics_window_started_elapsed=0
        echo "fps_metrics=started" >> "$meta_log"
    else
        echo "fps_metrics=skipped" >> "$meta_log"
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

        if [[ "$metrics_started" == "true" ]] && [[ $((elapsed - metrics_window_started_elapsed)) -ge "$METRICS_WINDOW_SECS" ]]; then
            invoke_metrics_attach_action "$client_pid" stop "$backend_dir/metrics_agent_stop_${metrics_expected_reports}.log" > "$backend_dir/metrics_stop_${metrics_expected_reports}.txt" 2>&1 || true
            metrics_expected_reports=$((metrics_expected_reports + 1))
            if [[ "$elapsed" -lt "$SAMPLE_SECS" ]]; then
                sleep 1
                if invoke_metrics_attach_action "$client_pid" start "$backend_dir/metrics_agent_restart_${metrics_expected_reports}.log" > "$backend_dir/metrics_restart_${metrics_expected_reports}.txt" 2>&1; then
                    metrics_window_started_elapsed=$elapsed
                else
                    metrics_started="false"
                    echo "fps_metrics_restart=failed_at_${elapsed}s" >> "$meta_log"
                fi
            else
                metrics_started="false"
            fi
        fi
    done

    if [[ "$metrics_started" == "true" ]]; then
        invoke_metrics_attach_action "$client_pid" stop "$backend_dir/metrics_agent_stop_final.log" > "$backend_dir/metrics_stop_final.txt" 2>&1 || true
        metrics_expected_reports=$((metrics_expected_reports + 1))
        metrics_started="false"
        sleep 2
    fi

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
    wait_for_metrics_archives "$start_measure_epoch" "$metrics_expected_reports" 20 || true
    copy_metrics_archives_since "$start_measure_epoch" "$metrics_report_dir"
    if [[ -d "$metrics_report_dir" ]] && command -v python3 >/dev/null 2>&1; then
        generate_fps_summary "$metrics_report_dir" "$fps_summary_file"
        cat "$fps_summary_file" >> "$meta_log"
    fi

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

    append_backend_run_summary "$backend" "$run_index" "$backend_dir" "$fps_summary_file"
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
    echo "runs_per_backend=$RUNS_PER_BACKEND"
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
            for ((run_index = 1; run_index <= RUNS_PER_BACKEND; run_index++)); do
                run_backend_capture "$backend" "$run_index"
            done
            append_backend_aggregate_summary "$backend" "$ARTIFACT_DIR/$backend"
            ;;
        *)
            echo "Skipping unsupported backend: $backend" | tee -a "$SUMMARY_FILE"
            ;;
    esac
done

restore_backend
trap - EXIT INT TERM

generate_perf_history_graph "$PERF_HISTORY_GRAPH"

echo "Complete. Artifacts saved to: $ARTIFACT_DIR"
echo "Summary: $SUMMARY_FILE"
echo "Graph: $PERF_HISTORY_GRAPH"