#!/usr/bin/env bash

set -euo pipefail

usage() {
    cat <<'EOF'
Usage: ./DevUtils/RunDevCapture.sh [--backend vulkan|opengl] [--max-secs N] [--dump-secs N] [--validation off|standard] [--shader-input-parity off|standard|full] [--client-args "..."]

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
    CLIENT_ARGS
EOF
}

BACKEND="vulkan"
MAX_SECS="${MAX_SECS:-120}"
DUMP_SECS="${DUMP_SECS:-45}"
SCREENSHOT_INTERVAL_SECS="${SCREENSHOT_INTERVAL_SECS:-5}"
SCREENSHOT_MAX_COUNT="${SCREENSHOT_MAX_COUNT:-6}"
SCREENSHOT_START_DELAY_SECS="${SCREENSHOT_START_DELAY_SECS:-0}"
VALIDATION_MODE="${VALIDATION_MODE:-off}"
SHADER_INPUT_PARITY="${SHADER_INPUT_PARITY:-off}"
SHADER_INPUT_PARITY_MAX_LOGS="${SHADER_INPUT_PARITY_MAX_LOGS:-120000}"
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
SHADER_EVENT_PATTERN='Using shaderpack:|Loaded Shaderpack:|shaderPack=|enableShaders=|Profile:|Reloading pipeline on dimension change|Creating pipeline for dimension|Skipping compute shader|Missing program .*sodium:pipeline|Sodium Vulkan chunk pipelines|raw GLSL imports|ShaderInputParity|Type is (VERTEX|FRAGMENT|GEOMETRY|COMPUTE)|bindVertexBuffer requires an active render pass|No active Vulkan render pass to end|shader compose into swapchain|Unexpected error|DistantHorizons|\[DH-|DH Ready|DH Iris events|Validation Error|Validation Warning|VUID-|UNASSIGNED-|VK_LAYER_KHRONOS_validation|GL_INVALID|OpenGL debug'
VALIDATION_EVENT_PATTERN='VK_LAYER_KHRONOS_validation|Validation Error|Validation Warning|VUID-|UNASSIGNED-'
KEY_SUMMARY_PATTERN='Using shaderpack:|Loaded Shaderpack:|Profile:|Reloading pipeline on dimension change|Creating pipeline for dimension|Skipping compute shader|Missing program .*sodium:pipeline|Sodium Vulkan chunk pipelines|raw GLSL imports|ShaderInputParity|bindVertexBuffer requires an active render pass|No active Vulkan render pass to end|Unexpected error|DistantHorizons|\[DH-|DH Ready|DH Iris events|Validation Error|Validation Warning|VUID-|UNASSIGNED-'

{
    echo "run_id=$RUN_ID"
    echo "start_epoch=$START_EPOCH"
    echo "backend=$BACKEND"
    echo "max_secs=$MAX_SECS"
    echo "dump_secs=$DUMP_SECS"
    echo "validation_mode=$VALIDATION_MODE"
    echo "shader_input_parity=$SHADER_INPUT_PARITY"
    echo "shader_input_parity_max_logs=$SHADER_INPUT_PARITY_MAX_LOGS"
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
    if grep -q '^graphics_backend=' "$OPTIONS_FILE"; then
        sed -i "s/^graphics_backend=.*/graphics_backend=$BACKEND/" "$OPTIONS_FILE"
    else
        echo "graphics_backend=$BACKEND" >> "$OPTIONS_FILE"
    fi
fi

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
GRADLE_CMD=(./gradlew -x test runClient)
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
    {
        echo "vk_instance_layers=$VK_INSTANCE_LAYERS"
        echo "vk_add_layer_path=${VK_ADD_LAYER_PATH:-unset}"
        echo "vk_loader_debug=$VK_LOADER_DEBUG"
    } >> "$META_LOG"
fi

if [[ "$SHADER_INPUT_PARITY" != "off" ]]; then
    SHADER_INPUT_PARITY_JAVA_OPTIONS=(
        "-Dmattmc.vulkan.traceShaderInputParity=true"
        "-Dmattmc.vulkan.traceShaderInputParity.maxLogs=$SHADER_INPUT_PARITY_MAX_LOGS"
    )
    if [[ "$SHADER_INPUT_PARITY" == "full" ]]; then
        SHADER_INPUT_PARITY_JAVA_OPTIONS+=("-Dmattmc.vulkan.traceStandaloneUniformBlockMembers=true")
    fi

    if [[ -n "${JAVA_TOOL_OPTIONS:-}" ]]; then
        export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS ${SHADER_INPUT_PARITY_JAVA_OPTIONS[*]}"
    else
        export JAVA_TOOL_OPTIONS="${SHADER_INPUT_PARITY_JAVA_OPTIONS[*]}"
    fi

    {
        echo "shader_input_parity_java_options=${SHADER_INPUT_PARITY_JAVA_OPTIONS[*]}"
        echo "java_tool_options=$JAVA_TOOL_OPTIONS"
    } >> "$META_LOG"
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
