#!/usr/bin/env bash

set -euo pipefail

usage() {
    cat <<'EOF'
Usage: ./RunVulkanPerfAudit.sh [options] [-- extra compare-harness args]

Runs a bounded backend comparison with the Vulkan perf audit enabled, then
summarizes the dominant Vulkan slowdown bucket into machine-readable and
human-readable reports.

Options:
  --world NAME             Quick-play singleplayer world. Default: Origin
  --warmup-secs N          Warmup before sampling. Default: 20
  --sample-secs N          Sampling duration. Default: 20
  --runs-per-backend N     Number of runs per backend. Default: 1
  --backends LIST          Backends for compare harness. Default: opengl,vulkan
  --label NAME             Label prefix for artifact directory.
  --artifact-dir PATH      Explicit artifact directory.
  --no-profile-command     Pass through to compare harness.
  -h, --help               Show this help.

Environment:
  JFR_SETTINGS             Passed through to compare harness. Default: profile
  PERF_MODE                Passed through to compare harness. Default: off

Examples:
  ./RunVulkanPerfAudit.sh
  ./RunVulkanPerfAudit.sh --world Origin --warmup-secs 30 --sample-secs 30
  ./RunVulkanPerfAudit.sh --label world-entry -- --launch-timeout-secs 300
EOF
}

canonicalize_path() {
    local path="$1"
    if command -v realpath >/dev/null 2>&1; then
        realpath -m "$path"
    else
        python3 - <<'PY' "$path"
import os
import sys
print(os.path.abspath(sys.argv[1]))
PY
    fi
}

extract_value() {
    local file="$1"
    local key="$2"
    awk -F= -v wanted="$key" '$1 == wanted {print $2; exit}' "$file"
}

extract_summary_fps() {
    local file="$1"
    local backend="$2"
    awk -v wanted="$backend" '
        $1 == wanted && $2 == "aggregate" {
            for (i = 1; i <= NF; i++) {
                if ($i ~ /^fps_avg=/) {
                    split($i, parts, "=")
                    print parts[2]
                    exit
                }
            }
        }
    ' "$file"
}

format_number() {
    local value="${1:-0}"
    printf '%.4f' "$value"
}

WORLD_NAME="Origin"
WARMUP_SECS="20"
SAMPLE_SECS="20"
RUNS_PER_BACKEND="1"
BACKENDS="opengl,vulkan"
LABEL=""
ARTIFACT_DIR=""
NO_PROFILE_COMMAND="false"
EXTRA_COMPARE_ARGS=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --world)
            WORLD_NAME="${2:-}"
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
        --runs-per-backend)
            RUNS_PER_BACKEND="${2:-}"
            shift 2
            ;;
        --backends)
            BACKENDS="${2:-}"
            shift 2
            ;;
        --label)
            LABEL="${2:-}"
            shift 2
            ;;
        --artifact-dir)
            ARTIFACT_DIR="${2:-}"
            shift 2
            ;;
        --no-profile-command)
            NO_PROFILE_COMMAND="true"
            shift
            ;;
        --)
            shift
            EXTRA_COMPARE_ARGS=("$@")
            break
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
done

for value_name in WARMUP_SECS SAMPLE_SECS RUNS_PER_BACKEND; do
    value="${!value_name}"
    if ! [[ "$value" =~ ^[0-9]+$ ]]; then
        echo "$value_name must be an integer, got: $value" >&2
        exit 1
    fi
done

SCRIPT_PATH="$(canonicalize_path "$0")"
PROJECT_ROOT="$(dirname "$SCRIPT_PATH")"
COMPARE_SCRIPT="$PROJECT_ROOT/DevUtils/RunBackendPerfCompare.sh"

if [[ ! -x "$COMPARE_SCRIPT" ]]; then
    echo "Missing compare harness: $COMPARE_SCRIPT" >&2
    exit 1
fi

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
if [[ -z "$ARTIFACT_DIR" ]]; then
    artifact_name="${TIMESTAMP}"
    if [[ -n "$LABEL" ]]; then
        artifact_name="${LABEL}-${TIMESTAMP}"
    fi
    ARTIFACT_DIR="$PROJECT_ROOT/logs/vulkan-perf-audit/$artifact_name"
fi

ARTIFACT_DIR="$(canonicalize_path "$ARTIFACT_DIR")"
COMPARE_ARTIFACT_DIR="$ARTIFACT_DIR/compare"
REPORT_DIR="$ARTIFACT_DIR/reports"
SUMMARY_FILE="$ARTIFACT_DIR/diagnosis_summary.txt"
MACHINE_FILE="$ARTIFACT_DIR/diagnosis_machine.txt"

mkdir -p "$COMPARE_ARTIFACT_DIR" "$REPORT_DIR"

original_java_tool_options="${JAVA_TOOL_OPTIONS-}"
audit_java_tool_options="-Dmattmc.vulkan.perfAudit=true -Dmattmc.vulkan.perfAuditReportDir=$REPORT_DIR"
if [[ -n "$original_java_tool_options" ]]; then
    export JAVA_TOOL_OPTIONS="$audit_java_tool_options $original_java_tool_options"
else
    export JAVA_TOOL_OPTIONS="$audit_java_tool_options"
fi

compare_cmd=(
    "$COMPARE_SCRIPT"
    --world "$WORLD_NAME"
    --runs-per-backend "$RUNS_PER_BACKEND"
    --warmup-secs "$WARMUP_SECS"
    --sample-secs "$SAMPLE_SECS"
    --backends "$BACKENDS"
    --artifact-dir "$COMPARE_ARTIFACT_DIR"
)

if [[ "$NO_PROFILE_COMMAND" == "true" ]]; then
    compare_cmd+=(--no-profile-command)
fi

if [[ ${#EXTRA_COMPARE_ARGS[@]} -gt 0 ]]; then
    compare_cmd+=("${EXTRA_COMPARE_ARGS[@]}")
fi

echo "Running bounded backend compare with Vulkan perf audit enabled"
echo "Artifacts: $ARTIFACT_DIR"
"${compare_cmd[@]}"

compare_summary="$COMPARE_ARTIFACT_DIR/summary.txt"
if [[ ! -f "$compare_summary" ]]; then
    echo "Compare summary not found: $compare_summary" >&2
    exit 1
fi

report_file="$(find "$REPORT_DIR" -maxdepth 1 -type f -name 'vulkan-perf-audit-*.txt' | sort | tail -n 1)"

gl_avg_fps="$(extract_summary_fps "$compare_summary" opengl || true)"
vk_avg_fps="$(extract_summary_fps "$compare_summary" vulkan || true)"
ratio="0"
if [[ -n "$gl_avg_fps" && -n "$vk_avg_fps" ]]; then
    ratio="$(awk -v gl="$gl_avg_fps" -v vk="$vk_avg_fps" 'BEGIN { if (gl > 0) printf "%.4f", vk / gl; else print "0.0000" }')"
fi

diagnostic_issue="missing_report"
diagnostic_hint="No Vulkan perf audit report was produced."
primary_submit_total_ms="0"
primary_submit_share_pct="0"
primary_submit_wait_before_ms="0"
primary_submit_wait_after_ms="0"
descriptor_bind_total_ms="0"
binding_build_total_ms="0"
frame_start_wait_total_ms="0"
primary_submits_per_frame="0"
render_passes_per_frame="0"
descriptor_binds_per_frame="0"
binding_builds_per_frame="0"

if [[ -n "$report_file" && -f "$report_file" ]]; then
    primary_submit_total_ms="$(extract_value "$report_file" primary_submit_total_ms || true)"
    primary_submit_share_pct="$(extract_value "$report_file" primary_submit_share_of_tracked_cpu_time_pct || true)"
    primary_submit_wait_before_ms="$(extract_value "$report_file" primary_submit_wait_before_ms || true)"
    primary_submit_wait_after_ms="$(extract_value "$report_file" primary_submit_wait_after_ms || true)"
    descriptor_bind_total_ms="$(extract_value "$report_file" descriptor_bind_total_ms || true)"
    binding_build_total_ms="$(extract_value "$report_file" binding_build_total_ms || true)"
    frame_start_wait_total_ms="$(extract_value "$report_file" begin_frame_fence_wait_ms || true)"
    primary_submits_per_frame="$(extract_value "$report_file" primary_submits_per_presented_frame || true)"
    render_passes_per_frame="$(extract_value "$report_file" render_passes_per_presented_frame || true)"
    descriptor_binds_per_frame="$(extract_value "$report_file" descriptor_binds_per_presented_frame || true)"
    binding_builds_per_frame="$(extract_value "$report_file" binding_builds_per_presented_frame || true)"

    dominant_bucket="$(awk \
        -v submit="${primary_submit_total_ms:-0}" \
        -v desc="${descriptor_bind_total_ms:-0}" \
        -v bind="${binding_build_total_ms:-0}" \
        -v frame="${frame_start_wait_total_ms:-0}" \
        'BEGIN {
            bucket = "primary_submit"
            max = submit + 0
            if ((desc + 0) > max) { max = desc + 0; bucket = "descriptor_bind" }
            if ((bind + 0) > max) { max = bind + 0; bucket = "binding_build" }
            if ((frame + 0) > max) { bucket = "frame_start_wait" }
            print bucket
        }')"

    case "$dominant_bucket" in
        primary_submit)
            diagnostic_issue="immediate_primary_submit_churn"
            diagnostic_hint="Immediate primary command buffer submits dominate tracked Vulkan CPU time. The compatibility layer closes many small render passes, and every non-frame command buffer falls into submitPrimaryCommandBuffer(), which blocks on vkWaitForFences before and after vkQueueSubmit."
            ;;
        descriptor_bind)
            diagnostic_issue="descriptor_bind_churn"
            diagnostic_hint="Descriptor update and bind churn dominates tracked Vulkan CPU time more than queue submission."
            ;;
        binding_build)
            diagnostic_issue="binding_build_churn"
            diagnostic_hint="Pipeline resource binding construction dominates tracked Vulkan CPU time more than queue submission."
            ;;
        frame_start_wait)
            diagnostic_issue="frame_start_wait"
            diagnostic_hint="Frame-start fence waits dominate tracked Vulkan CPU time more than submission or descriptor work."
            ;;
    esac
fi

cat > "$MACHINE_FILE" <<EOF
gl_avg_fps=$(format_number "${gl_avg_fps:-0}")
vk_avg_fps=$(format_number "${vk_avg_fps:-0}")
vk_to_gl_ratio=$(format_number "$ratio")
diagnostic_issue=$diagnostic_issue
primary_submit_total_ms=$(format_number "$primary_submit_total_ms")
primary_submit_share_pct=$(format_number "$primary_submit_share_pct")
descriptor_bind_total_ms=$(format_number "$descriptor_bind_total_ms")
binding_build_total_ms=$(format_number "$binding_build_total_ms")
frame_start_wait_total_ms=$(format_number "$frame_start_wait_total_ms")
primary_submits_per_frame=$(format_number "$primary_submits_per_frame")
render_passes_per_frame=$(format_number "$render_passes_per_frame")
descriptor_binds_per_frame=$(format_number "$descriptor_binds_per_frame")
binding_builds_per_frame=$(format_number "$binding_builds_per_frame")
audit_report=${report_file:-missing}
EOF

cat > "$SUMMARY_FILE" <<EOF
Artifacts: $COMPARE_ARTIFACT_DIR
Vulkan audit report: ${report_file:-missing}

Backend FPS:
  OpenGL avg FPS: $(format_number "${gl_avg_fps:-0}")
  Vulkan avg FPS: $(format_number "${vk_avg_fps:-0}")
  Vulkan/OpenGL ratio: $(format_number "$ratio")

Diagnosis:
  $diagnostic_hint

Tracked Vulkan buckets:
  primary_submit_total_ms=$(format_number "$primary_submit_total_ms")
  primary_submit_share_pct=$(format_number "$primary_submit_share_pct")
  primary_submit_wait_before_ms=$(format_number "$primary_submit_wait_before_ms")
  primary_submit_wait_after_ms=$(format_number "$primary_submit_wait_after_ms")
  descriptor_bind_total_ms=$(format_number "$descriptor_bind_total_ms")
  binding_build_total_ms=$(format_number "$binding_build_total_ms")
  frame_start_wait_total_ms=$(format_number "$frame_start_wait_total_ms")

Per presented frame:
  primary_submits_per_frame=$(format_number "$primary_submits_per_frame")
  render_passes_per_frame=$(format_number "$render_passes_per_frame")
  descriptor_binds_per_frame=$(format_number "$descriptor_binds_per_frame")
  binding_builds_per_frame=$(format_number "$binding_builds_per_frame")

Relevant code path:
  - src/main/java/net/blaze3d/opengl/GlCommandEncoder.java::finishRenderPass()
  - src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java::submitCommandBuffer(CommandContext)
  - src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java::NativeSpine.submitPrimaryCommandBuffer(long)
EOF

echo "Diagnosis summary: $SUMMARY_FILE"
echo "Diagnosis machine output: $MACHINE_FILE"