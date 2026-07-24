#!/usr/bin/env python3
"""Cross-repository graphics audit harness for staged Vulkanic migration."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import re
import shutil
import signal
import statistics
import subprocess
import sys
import time
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, Mapping, Sequence


SCHEMA = "mattmc-cross-graphics-audit-v2"
ARTIFACT_NAME = "graphics_audit_artifact.json"
MANIFEST_NAME = "graphics_audit_manifest.json"
BASELINE_INDEX_NAME = "graphics_audit_baselines.json"
DEFAULT_TIMEOUT_SECONDS = 900
CURRENT_REPO_ROOT = Path(__file__).resolve().parents[2]
DEVUTILS_CACHE_ROOT = CURRENT_REPO_ROOT / "DevUtils" / ".cache"
WORKLOAD_PROFILES = ("correctness", "moving-camera", "settled-static", "gameplay")
TOOL_KINDS = ("gameplay", "subsystem", "capture", "matrix")
RUNTIME_PROFILE_NAMES = ("smoke", "standard", "extended")


@dataclass(frozen=True)
class RepoTarget:
    name: str
    root: Path
    role: str


@dataclass(frozen=True)
class ModeSpec:
    name: str
    target: str
    backend: str
    shaders: str
    expected_attribution: str
    supports_validation: bool


@dataclass
class MatrixResult:
    mode: str
    success: bool
    reused_baseline: bool
    timed_out: bool
    timed_out_phase: str | None
    exit_code: int | None
    artifact_path: str
    capture_dir: str
    command: list[str]
    error: str = ""


@dataclass(frozen=True)
class RuntimeProfile:
    name: str
    hard_timeout_seconds: int
    startup_timeout_seconds: int
    readiness_timeout_seconds: int
    warmup_timeout_seconds: int
    measurement_timeout_seconds: int
    shutdown_timeout_seconds: int
    cleanup_timeout_seconds: int
    max_secs: int
    dump_secs: int
    warmup_frames: int
    measure_frames: int
    settle_frames: int
    max_settle_frames: int
    subsystem_iterations: int


RUNTIME_PROFILES = {
    "smoke": RuntimeProfile("smoke", 60, 15, 15, 10, 20, 5, 5, 50, 15, 20, 60, 60, 600, 20),
    "standard": RuntimeProfile("standard", 180, 30, 40, 25, 75, 10, 10, 160, 45, 120, 300, 240, 1800, 120),
    "extended": RuntimeProfile("extended", 300, 55, 65, 40, 125, 15, 15, 270, 60, 240, 600, 360, 3000, 200),
}


MATRIX_MODES = (
    ModeSpec("current-opengl-shaders-off", "current", "opengl", "off", "java-opengl", False),
    ModeSpec("current-opengl-shaders-on", "current", "opengl", "on", "java-opengl", False),
    ModeSpec("current-java-vulkan-shaders-off", "current", "vulkan", "off", "java-vulkan", True),
    ModeSpec("current-java-vulkan-shaders-on", "current", "vulkan", "on", "java-vulkan", True),
    ModeSpec("frozen-opengl-shaders-off", "frozen", "opengl", "off", "java-opengl", False),
    ModeSpec("frozen-opengl-shaders-on", "frozen", "opengl", "on", "java-opengl", False),
)

ERROR_PATTERNS = {
    "crash": re.compile(r"fatal error|A fatal error has been detected|Minecraft Crash Report", re.IGNORECASE),
    "gl_error": re.compile(r"GL_INVALID|OpenGL debug", re.IGNORECASE),
    "vuid": re.compile(r"VUID-|Validation Error|Validation Warning|UNASSIGNED-", re.IGNORECASE),
    "device_loss": re.compile(r"VK_ERROR_DEVICE_LOST|device.?lost", re.IGNORECASE),
    "rss_guard": re.compile(r"client_rss_limit_exceeded|memory_guard_triggered=true", re.IGNORECASE),
    "orphan_process": re.compile(r"orphan|cleanup_.*kill=true", re.IGNORECASE),
}
REPO_PROCESS_MARKERS = ("KnotClient", "GradleWrapperMain", "capture_runner", "gradlew", "runClient")
VALIDATION_PROFILES = ("off", "routine", "deep")
INSTRUMENTED_RUN_TYPES = ("vulkan-validation", "renderdoc-capture", "tracy-capture")


def executable_version(command: str, args: Sequence[str] = ("--version",)) -> dict[str, object]:
    path = shutil.which(command)
    if not path:
        return {"available": False, "path": None, "version": None}
    try:
        result = subprocess.run(
            [path, *args],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=8,
            check=False,
        )
        version = result.stdout.strip().splitlines()[0] if result.stdout.strip() else f"exit={result.returncode}"
    except Exception as exc:
        version = f"version-unavailable: {exc}"
    return {"available": True, "path": path, "version": version}


def local_tracy_capture_path() -> str | None:
    candidate = DEVUTILS_CACHE_ROOT / "tools" / "tracy" / "bin" / "tracy-capture"
    if candidate.is_file() and os.access(candidate, os.X_OK):
        return str(candidate)
    return shutil.which("tracy-capture")


def local_renderdoccmd_path() -> str | None:
    candidate = DEVUTILS_CACHE_ROOT / "tools" / "renderdoc" / "bin" / "renderdoccmd"
    if candidate.is_file() and os.access(candidate, os.X_OK):
        return str(candidate)
    return shutil.which("renderdoccmd")


def local_qrenderdoc_path() -> str | None:
    candidate = DEVUTILS_CACHE_ROOT / "tools" / "renderdoc" / "bin" / "qrenderdoc"
    if candidate.is_file() and os.access(candidate, os.X_OK):
        return str(candidate)
    return shutil.which("qrenderdoc")


def local_renderdoc_library_path() -> str | None:
    root = DEVUTILS_CACHE_ROOT / "tools" / "renderdoc"
    candidates = sorted(root.glob("extracted/**/librenderdoc.so"))
    for candidate in candidates:
        if candidate.is_file():
            return str(candidate)
    return None


def find_validation_layer_manifest_path() -> Path | None:
    candidates: list[Path] = []
    vulkan_sdk = os.environ.get("VULKAN_SDK")
    if vulkan_sdk:
        candidates.append(Path(vulkan_sdk) / "share" / "vulkan" / "explicit_layer.d" / "VkLayer_khronos_validation.json")
    if platform_name() == "linux":
        candidates.extend(
            [
                Path("/usr/share/vulkan/explicit_layer.d/VkLayer_khronos_validation.json"),
                Path("/usr/local/share/vulkan/explicit_layer.d/VkLayer_khronos_validation.json"),
                Path("/etc/vulkan/explicit_layer.d/VkLayer_khronos_validation.json"),
            ]
        )
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    return None


def tool_fingerprint() -> dict[str, object]:
    validation_manifest = find_validation_layer_manifest_path()
    renderdoc_path = local_renderdoccmd_path()
    qrenderdoc_path = local_qrenderdoc_path()
    tracy_path = local_tracy_capture_path()
    tracy = {"available": bool(tracy_path), "path": tracy_path, "version": None}
    if tracy_path:
        try:
            result = subprocess.run([tracy_path, "--help"], text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=8, check=False)
            tracy["version"] = result.stdout.strip().splitlines()[0] if result.stdout.strip() else f"exit={result.returncode}"
        except Exception as exc:
            tracy["version"] = f"version-unavailable: {exc}"
    return {
        "vulkaninfo": executable_version("vulkaninfo"),
        "glslangValidator": executable_version("glslangValidator"),
        "khronos_validation": {
            "available": validation_manifest is not None,
            "manifest": str(validation_manifest) if validation_manifest else None,
        },
        "renderdoccmd": executable_version(renderdoc_path) if renderdoc_path else executable_version("renderdoccmd"),
        "qrenderdoc": executable_version(qrenderdoc_path) if qrenderdoc_path else executable_version("qrenderdoc"),
        "tracy_capture": tracy,
        "tracy_profiler": executable_version("tracy-profiler"),
    }


def run_type_for_args(args: argparse.Namespace) -> str:
    enabled = []
    if getattr(args, "validation", "off") != "off":
        enabled.append("vulkan-validation")
    if getattr(args, "renderdoc_capture", False):
        enabled.append("renderdoc-capture")
    if getattr(args, "tracy_capture", False):
        enabled.append("tracy-capture")
    if len(enabled) > 1:
        raise SystemExit("select only one instrumented run type at a time: validation, RenderDoc, or Tracy")
    return enabled[0] if enabled else "clean-performance"


def validation_profile_details(profile: str) -> dict[str, object]:
    normalized = "routine" if profile == "standard" else profile
    if normalized == "off":
        return {"profile": "off", "features": [], "performance_comparable": True}
    if normalized == "routine":
        return {
            "profile": "routine",
            "features": ["core", "synchronization", "best-practices"],
            "performance_comparable": False,
            "fail_severity": "warning",
        }
    if normalized == "deep":
        return {
            "profile": "deep",
            "features": ["gpu-assisted", "synchronization", "best-practices", "optional-debug-printf"],
            "performance_comparable": False,
            "fail_severity": "info",
        }
    return {"profile": normalized, "features": [], "performance_comparable": False}


VALIDATION_LINE_RE = re.compile(
    r"(?P<severity>ERROR|WARNING|INFO|VERBOSE)?[:\\s-]*"
    r"(?P<type>VALIDATION|PERFORMANCE|GENERAL)?[^\\n]*?"
    r"(?P<vuid>VUID-[A-Za-z0-9_./:-]+)?[^\\n]*?"
    r"(?:(?:MessageID|message id|msgNum|Message Id Name)\\s*[=: ]\\s*(?P<message_id>[A-Za-z0-9_./:-]+))?",
    re.IGNORECASE,
)


def parse_validation_findings(text: str) -> list[dict[str, object]]:
    findings: dict[tuple[object, ...], dict[str, object]] = {}
    for line in text.splitlines():
        if not re.search(r"VUID-|Validation (?:Error|Warning)|UNASSIGNED-|VK_LAYER_KHRONOS_validation", line, re.IGNORECASE):
            continue
        match = VALIDATION_LINE_RE.search(line)
        vuid_match = re.search(r"VUID-[A-Za-z0-9_./:-]+", line)
        severity_match = re.search(r"\b(ERROR|WARNING|INFO|VERBOSE)\b", line, re.IGNORECASE)
        message_id_match = re.search(r"(?:MessageID|message id|msgNum|Message Id Name)\s*[=: ]\s*([A-Za-z0-9_./:-]+)", line, re.IGNORECASE)
        object_handles = sorted(set(re.findall(r"0x[0-9a-fA-F]+", line)))
        semantic_match = re.search(r"(?:GAL|callsite|operation)[=: ]+([A-Za-z0-9_./:-]+)", line)
        key = (
            vuid_match.group(0) if vuid_match else "UNASSIGNED",
            message_id_match.group(1) if message_id_match else None,
            re.sub(r"0x[0-9a-fA-F]+", "0x*", line.strip())[:300],
        )
        finding = findings.setdefault(
            key,
            {
                "vuid": key[0],
                "severity": severity_match.group(1).lower() if severity_match else "unknown",
                "message_type": match.group("type").lower() if match and match.group("type") else "unknown",
                "message_id": key[1],
                "message": line.strip(),
                "first_occurrence": len(findings) + 1,
                "count": 0,
                "objects": object_handles,
                "frame": first_number(line, r"frame[=: ]+(\d+)"),
                "pass": first_number(line, r"pass[=: ]+(\d+)"),
                "draw": first_number(line, r"draw[=: ]+(\d+)"),
                "queue": first_number(line, r"queue[=: ]+(\d+)"),
                "submission": first_number(line, r"submission[=: ]+(\d+)"),
                "semantic_operation": semantic_match.group(1) if semantic_match else None,
            },
        )
        finding["count"] = int(finding["count"]) + 1
        if object_handles:
            finding["objects"] = sorted(set([*finding.get("objects", []), *object_handles]))
    return list(findings.values())


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def timestamp() -> str:
    return datetime.now().strftime("%Y%m%d-%H%M%S")


def script_dir() -> Path:
    return Path(__file__).resolve().parent


def repo_root(start: Path | None = None) -> Path:
    current = (start or script_dir()).resolve()
    if current.is_file():
        current = current.parent
    while current != current.parent:
        if (current / "gradlew").is_file() or (current / "gradlew.bat").is_file():
            return current
        current = current.parent
    raise SystemExit(f"Could not find MattMC repository root from {start or script_dir()}")


def platform_name() -> str:
    raw = platform.system().strip().lower()
    if raw.startswith(("cygwin", "mingw", "msys")):
        return "windows"
    if raw == "darwin":
        return "macos"
    return raw or "unknown"


def command_text(command: list[str], *, cwd: Path) -> str:
    if not shutil.which(command[0]) and not Path(command[0]).exists():
        return ""
    result = subprocess.run(command, cwd=cwd, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    return result.stdout.strip() if result.returncode == 0 else ""


def git_output(root: Path, *args: str) -> str:
    return command_text(["git", *args], cwd=root)


def repository_metadata(target: RepoTarget) -> dict[str, object]:
    status = git_output(target.root, "status", "--short")
    return {
        "target": target.name,
        "role": target.role,
        "path": str(target.root),
        "branch": git_output(target.root, "branch", "--show-current"),
        "commit": git_output(target.root, "rev-parse", "HEAD"),
        "dirty": bool(status),
        "status_short": status.splitlines() if status else [],
    }


def directory_helper(root: Path) -> Path | None:
    helper = root / "DevUtils" / "Common" / "platform" / "directory" / "directory_helper.py"
    return helper if helper.is_file() else None


def resolve_named_directory(root: Path, name: str) -> Path:
    helper = directory_helper(root)
    if helper is None:
        raise SystemExit(f"Directory helper is missing in repository: {root}")
    result = subprocess.run(
        [sys.executable, str(helper), name],
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != 0:
        raise SystemExit(result.stderr.strip() or result.stdout.strip() or f"Could not resolve {name}")
    return Path(result.stdout.strip()).resolve()


def find_frozen_repo(current_root: Path, explicit: str | None = None) -> Path:
    if explicit:
        return Path(explicit).expanduser().resolve()
    env_path = os.environ.get("MATTMC_JAVA_PERF_REPO") or os.environ.get("MATTMC_FROZEN_JAVA_REPO")
    if env_path:
        return Path(env_path).expanduser().resolve()
    return resolve_named_directory(current_root, "java_perf_repo")


def select_targets(args: argparse.Namespace) -> dict[str, RepoTarget]:
    current = repo_root()
    frozen = find_frozen_repo(current, args.frozen_repo)
    return {
        "current": RepoTarget("current", current, "current"),
        "frozen": RepoTarget("frozen", frozen, "frozen-java-reference"),
    }


def gradle_script(root: Path) -> Path:
    if platform_name() == "windows" and (root / "gradlew.bat").is_file():
        return root / "gradlew.bat"
    if (root / "gradlew").is_file():
        return root / "gradlew"
    if (root / "gradlew.bat").is_file():
        return root / "gradlew.bat"
    raise SystemExit(f"No Gradle wrapper found in {root}")


def run_dev_capture_entrypoint(root: Path) -> tuple[str, Path]:
    python_script = root / "DevUtils" / "Common" / "capture_runner.py"
    if python_script.is_file():
        return "python", python_script
    shell_script = root / "DevUtils" / "Common" / "capture_runner.sh"
    if shell_script.is_file():
        return "shell", shell_script
    raise SystemExit(f"Internal capture runner is missing in {root}")


def run_dev_capture_script(root: Path) -> Path:
    return run_dev_capture_entrypoint(root)[1]


def remove_client_arg_option(client_args: str, option: str) -> str:
    if not client_args:
        return ""
    pattern = rf"(^|\s){re.escape(option)}(=[^\s]+|\s+[^\s]+)?"
    return re.sub(pattern, " ", client_args).strip()


def remove_client_arg_assignment(client_args: str, key: str) -> str:
    if not client_args:
        return ""
    pattern = rf"(^|\s){re.escape(key)}=[^\s]+"
    return re.sub(pattern, " ", client_args).strip()


def append_client_arg(client_args: str, arg: str) -> str:
    return " ".join(part for part in [client_args.strip(), arg.strip()] if part)


def stable_json_hash(value: object) -> str:
    payload = json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def read_json(path: Path) -> dict[str, object] | None:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None
    return value if isinstance(value, dict) else None


def read_key_values(path: Path | None) -> dict[str, str]:
    if path is None or not path.is_file():
        return {}
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if "=" not in raw_line:
            continue
        key, value = raw_line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def latest_matching(directory: Path, pattern: str) -> Path | None:
    matches = sorted(directory.glob(pattern), key=lambda path: path.stat().st_mtime if path.exists() else 0)
    return matches[-1] if matches else None


def parse_number(value: object) -> float | None:
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, str):
        try:
            return float(value.strip())
        except ValueError:
            return None
    return None


def percentile(values: list[float], pct: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round((pct / 100.0) * (len(ordered) - 1))))
    return ordered[index]


def summarize_distribution(values: list[float]) -> dict[str, object]:
    if not values:
        return {"count": 0, "median": None, "p95": None, "p99": None, "worst": None, "total": None}
    return {
        "count": len(values),
        "median": statistics.median(values),
        "p95": percentile(values, 95),
        "p99": percentile(values, 99),
        "worst": max(values),
        "total": sum(values),
    }


def frame_nanos_to_millis(frame_nanos: Iterable[int | float]) -> list[float]:
    return [float(value) / 1_000_000.0 for value in frame_nanos if isinstance(value, (int, float)) and value > 0]


def fps_from_frame_nanos(frame_nanos: Iterable[int | float]) -> list[float]:
    return [1_000_000_000.0 / float(value) for value in frame_nanos if isinstance(value, (int, float)) and value > 0]


def phase_stats_to_distribution(stats: dict[str, object]) -> dict[str, object]:
    count = parse_number(stats.get("count")) or 0.0
    total = parse_number(stats.get("total"))
    worst = parse_number(stats.get("worst"))
    if count <= 0.0 or total is None:
        return {"count": int(count), "median": None, "p95": None, "p99": None, "worst": None, "total": None}
    average_ms = (total / count) / 1_000_000.0
    return {
        "count": int(count),
        "median": average_ms,
        "p95": None,
        "p99": None,
        "worst": (worst / 1_000_000.0) if worst is not None else None,
        "total": total / 1_000_000.0,
    }


def phase_family_name(label: str) -> str:
    name = label.lower()
    if name.startswith("game."):
        return "game-client"
    if name.startswith("sodium.terrain.setup"):
        return "sodium-terrain-setup"
    if name.startswith("sodium.terrain.draw"):
        return "sodium-terrain-draw"
    if name.startswith("iris.") and "shadow" in name:
        return "iris-shadow"
    if name.startswith("iris.") and "deferred" in name:
        return "iris-deferred"
    if name.startswith("iris.") and ("composite" in name or "final" in name):
        return "iris-composite"
    if name.startswith("iris."):
        return "iris-other"
    if name.startswith("distant-horizons."):
        return "distant-horizons"
    if name.startswith("gal.") or name.startswith("vulkanic.frontend"):
        return "gal-frontend"
    if name.startswith("backend.") or name.startswith("vulkanic.backend"):
        return "backend-lowering"
    if "upload" in name or "transfer" in name:
        return "uploads-transfers"
    if name.startswith("command.recording"):
        return "command-recording"
    if name.startswith("api.present") or name.startswith("sync.") or "submit" in name or "wait" in name:
        return "submission-present-waits"
    if "ffi" in name or "rust" in name:
        return "rust-ffi"
    return "other"


def phase_family_timings(phase_timings: dict[str, dict[str, object]]) -> dict[str, dict[str, object]]:
    families: dict[str, dict[str, float]] = {}
    for label, stats in phase_timings.items():
        family = phase_family_name(label)
        count = parse_number(stats.get("count")) or 0.0
        total = parse_number(stats.get("total")) or 0.0
        worst = parse_number(stats.get("worst")) or 0.0
        record = families.setdefault(family, {"count": 0.0, "total": 0.0, "worst": 0.0})
        record["count"] += count
        record["total"] += total
        record["worst"] = max(record["worst"], worst)
    return {
        family: {
            "count": int(values["count"]),
            "median": (values["total"] / values["count"]) if values["count"] > 0 else None,
            "p95": None,
            "p99": None,
            "worst": values["worst"] if values["worst"] > 0 else None,
            "total": values["total"],
        }
        for family, values in sorted(families.items())
    }


def load_capture_files(capture_dir: Path) -> dict[str, Path | None]:
    return {
        "meta": latest_matching(capture_dir, "meta_*.txt"),
        "shader_summary": latest_matching(capture_dir, "shader_summary_*.txt"),
        "shaderpack": latest_matching(capture_dir, "shaderpack_*.txt"),
        "validation_events": latest_matching(capture_dir, "validation_events_*.log"),
        "shader_events": latest_matching(capture_dir, "shader_events_*.log"),
        "latest_log": latest_matching(capture_dir, "latest_*.log"),
        "latest_tail": latest_matching(capture_dir, "latest_tail_*.log"),
        "run_log": latest_matching(capture_dir, "runClient_*.log"),
        "deterministic": latest_matching(capture_dir, "deterministic_camera_capture_*.json"),
        "frame_benchmark": latest_matching(capture_dir, "graphics_frame_benchmark_*.json"),
        "subsystem_benchmark": latest_matching(capture_dir, "graphics_subsystem_benchmark_*.json"),
        "system_snapshot": latest_matching(capture_dir, "system_snapshot_*.txt"),
        "process_snapshot": latest_matching(capture_dir, "process_snapshot_*.txt"),
        "renderdoc_summary": latest_matching(capture_dir, "renderdoc_summary_*.json"),
        "tracy_summary": latest_matching(capture_dir, "tracy_summary_*.json"),
        "crash_reports": latest_matching(capture_dir, "crash_reports_*.txt"),
        "hs_err": latest_matching(capture_dir, "hs_err_*.txt"),
    }


def file_text(path: Path | None) -> str:
    if path is None or not path.is_file():
        return ""
    return path.read_text(encoding="utf-8", errors="replace")


def count_pattern(paths: Iterable[Path | None], pattern: re.Pattern[str]) -> int:
    return sum(len(pattern.findall(file_text(path))) for path in paths)


def listed_failure_file_count(path: Path | None) -> int:
    if path is None or not path.is_file():
        return 0
    count = 0
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        if stripped.startswith("=====") or stripped.lower().startswith("(no "):
            continue
        count += 1
    return count


def detect_attribution(mode: ModeSpec, meta: dict[str, str], logs: str) -> str:
    explicit = meta.get("implementation_attribution") or meta.get("render_implementation")
    if explicit:
        return explicit
    if re.search(r"Rust Vulkan|rust-vulkan|mattmc_rust.*vulkan", logs, re.IGNORECASE):
        return "rust-vulkan"
    if mode.backend == "vulkan":
        if re.search(r"fallback|compatibility OpenGL|delegated to OpenGL", logs, re.IGNORECASE):
            return "fallback"
        return "java-vulkan"
    return "java-opengl"


def deterministic_camera_signature(doc: dict[str, object] | None) -> dict[str, object]:
    if not doc:
        return {
            "status": "unavailable",
            "dimension": None,
            "position": None,
            "poses": [],
            "window": None,
            "frame_count": None,
        }
    captures = doc.get("captures") if isinstance(doc.get("captures"), list) else []
    poses: list[dict[str, object]] = []
    for capture in captures:
        if not isinstance(capture, dict):
            continue
        poses.append(
            {
                "pose": capture.get("poseName"),
                "yaw": capture.get("requestedYaw"),
                "pitch": capture.get("requestedPitch"),
                "frame": capture.get("renderedFrameIndex"),
            }
        )
    window = doc.get("window") if isinstance(doc.get("window"), dict) else None
    position = doc.get("initialPosition") or doc.get("position")
    return {
        "status": doc.get("status"),
        "dimension": doc.get("dimension") or doc.get("initialDimension"),
        "position": position,
        "poses": poses,
        "window": window,
        "frame_count": len(poses) or doc.get("captureCount"),
    }


def dh_state_from_text(text: str, meta: dict[str, str]) -> dict[str, object]:
    dh_enabled = bool(re.search(r"DistantHorizons|\[DH-|DH Ready|renderLods|renderDeferredLods", text, re.IGNORECASE))
    return {
        "present_or_logged": dh_enabled,
        "render_distance": meta.get("dh_render_distance") or meta.get("distant_horizons_render_distance"),
        "state": meta.get("dh_state") or ("logged" if dh_enabled else "unknown"),
    }


def config_snapshot_hash(capture_dir: Path, prefix: str) -> dict[str, object]:
    snapshots = sorted(capture_dir.glob(f"{prefix}_*"), key=lambda path: path.stat().st_mtime if path.exists() else 0)
    if not snapshots:
        return {"status": "missing", "hash": None, "files": {}}
    directory = snapshots[-1]
    files: dict[str, str] = {}
    digest = hashlib.sha256()
    included = {"DistantHorizons.toml", "sodium-options.json", "sodium-mixins.properties", "voxelmap.properties", "iris-excluded.json"}
    for path in sorted(candidate for candidate in directory.rglob("*") if candidate.is_file() and candidate.name in included):
        rel = path.relative_to(directory).as_posix()
        try:
            payload = path.read_bytes()
        except OSError:
            payload = b""
        file_hash = hashlib.sha256(payload).hexdigest()
        files[rel] = file_hash
        digest.update(rel.encode("utf-8"))
        digest.update(b"\0")
        digest.update(file_hash.encode("ascii"))
        digest.update(b"\n")
    return {"status": "ok", "hash": digest.hexdigest(), "files": files}


def shaderpack_sha256(shaderpack_text: str, meta: dict[str, str]) -> str | None:
    explicit = meta.get("shader_pack_sha256") or meta.get("shaderpack_sha256")
    if explicit:
        return explicit
    for line in shaderpack_text.splitlines():
        match = re.match(r"^([0-9a-fA-F]{64})\s+", line.strip())
        if match:
            return match.group(1).lower()
    return None


def frame_runtime_state(frame_doc: dict[str, object] | None) -> dict[str, object]:
    runtime = frame_doc.get("runtimeState") if isinstance(frame_doc, dict) and isinstance(frame_doc.get("runtimeState"), dict) else {}
    camera = frame_doc.get("cameraPath") if isinstance(frame_doc, dict) and isinstance(frame_doc.get("cameraPath"), dict) else {}
    window = frame_doc.get("window") if isinstance(frame_doc, dict) and isinstance(frame_doc.get("window"), dict) else None
    return {
        "window": window,
        "camera_path": camera,
        "loaded_chunks": runtime.get("loadedChunks"),
        "entity_count": runtime.get("entityCount"),
        "player_count": runtime.get("playerCount"),
        "window_focused": runtime.get("windowFocused"),
        "fullscreen": runtime.get("fullscreen"),
        "screen": runtime.get("screen"),
        "overlay": runtime.get("overlay"),
        "hide_gui": runtime.get("hideGui"),
        "debug_overlay_visible": runtime.get("debugOverlayVisible"),
        "render_distance": runtime.get("renderDistance"),
        "effective_render_distance": runtime.get("effectiveRenderDistance"),
        "simulation_distance": runtime.get("simulationDistance"),
        "entity_distance_scaling": runtime.get("entityDistanceScaling"),
        "gui_scale": runtime.get("guiScale"),
        "max_fps": runtime.get("maxFps"),
        "enable_vsync": runtime.get("enableVsync"),
        "graphics_mode": runtime.get("graphicsMode"),
    }


def workload_family_name(raw_name: str) -> str:
    name = raw_name.lower()
    if "sodium" in name or "terrain" in name or "chunk" in name:
        return "terrain"
    if "iris" in name or "shadow" in name or "deferred" in name or "composite" in name:
        return "iris"
    if "distant" in name or name.startswith("dh") or "lod" in name:
        return "distant-horizons"
    if "gui" in name or "text" in name:
        return "gui-text"
    if "entity" in name:
        return "entities"
    if "transfer" in name or "upload" in name or "copy" in name or "blit" in name:
        return "transfers"
    if "pipeline" in name or "descriptor" in name or "resource" in name:
        return "pipelines-resources"
    if "pass" in name:
        return "render-passes"
    return "other"


def bucketed_rate(value: float) -> str:
    if value <= 0.0:
        return "zero"
    if value < 0.25:
        return "trace"
    if value < 1.0:
        return "low"
    if value < 4.0:
        return "medium"
    if value < 16.0:
        return "high"
    return "very-high"


def stable_workload_family_summary(frame_doc: dict[str, object] | None, combined_logs: str) -> dict[str, object]:
    counts = frame_doc.get("submittedWorkCounts") if isinstance(frame_doc, dict) and isinstance(frame_doc.get("submittedWorkCounts"), dict) else {}
    measured = parse_number(frame_doc.get("measuredFrameCount")) if isinstance(frame_doc, dict) else None
    measured_frames = measured if measured and measured > 0 else 1.0
    families: dict[str, dict[str, object]] = {}
    for raw_name, raw_count in counts.items():
        count = parse_number(raw_count) or 0.0
        family = workload_family_name(str(raw_name))
        record = families.setdefault(family, {"present": False, "count": 0, "calls_per_frame": 0.0, "bucket": "zero"})
        record["present"] = bool(record["present"] or count > 0)
        record["count"] = int(record["count"]) + int(count)
    if not families:
        for family, count in semantic_draw_families(combined_logs).items():
            stable = workload_family_name(family)
            record = families.setdefault(stable, {"present": False, "count": 0, "calls_per_frame": 0.0, "bucket": "zero"})
            record["present"] = bool(record["present"] or count > 0)
            record["count"] = int(record["count"]) + int(count)
    for record in families.values():
        calls_per_frame = float(record["count"]) / measured_frames
        record["calls_per_frame"] = round(calls_per_frame, 3)
        record["bucket"] = bucketed_rate(calls_per_frame)
    for family in ("terrain", "iris", "distant-horizons", "gui-text", "entities", "transfers", "pipelines-resources", "render-passes"):
        families.setdefault(family, {"present": False, "count": 0, "calls_per_frame": 0.0, "bucket": "zero"})
    return families


def workload_signature(
    target: RepoTarget,
    capture_dir: Path,
    mode: ModeSpec,
    workload_profile: str,
    meta: dict[str, str],
    deterministic_doc: dict[str, object] | None,
    frame_doc: dict[str, object] | None,
    combined_logs: str,
    shaderpack_text: str,
) -> dict[str, object]:
    runtime = frame_runtime_state(frame_doc)
    camera = runtime.get("camera_path") or deterministic_camera_signature(deterministic_doc)
    return {
        "workload_profile": workload_profile,
        "world": meta.get("world", "Origin"),
        "world_save_state": {
            "hash": meta.get("world_save_state_hash"),
            "file_count": parse_number(meta.get("world_save_state_file_count")),
            "total_bytes": parse_number(meta.get("world_save_state_total_bytes")),
            "truncated": meta.get("world_save_state_truncated", "false").lower() == "true",
        },
        "camera": camera,
        "runtime": runtime,
        "resolution": runtime.get("window") or camera.get("window") or {
            "width": meta.get("window_width") or meta.get("resolution_width"),
            "height": meta.get("window_height") or meta.get("resolution_height"),
        },
        "settings": {
            "backend": mode.backend,
            "shaders": mode.shaders,
            "effective_enable_shaders": meta.get("effective_enable_shaders"),
            "validation_mode": meta.get("validation_mode"),
            "shader_input_parity": meta.get("shader_input_parity"),
            "forced_window_width": meta.get("forced_window_width"),
            "forced_window_height": meta.get("forced_window_height"),
            "render_distance": meta.get("forced_option_renderDistance") or runtime.get("render_distance"),
            "simulation_distance": meta.get("forced_option_simulationDistance") or runtime.get("simulation_distance"),
            "gui_scale": meta.get("forced_option_guiScale") or runtime.get("gui_scale"),
            "fullscreen": meta.get("forced_option_fullscreen") or runtime.get("fullscreen"),
            "hide_gui": meta.get("forced_option_hideGui") or runtime.get("hide_gui"),
            "max_fps": meta.get("forced_option_maxFps") or runtime.get("max_fps"),
            "enable_vsync": meta.get("forced_option_enableVsync") or runtime.get("enable_vsync"),
        },
        "shaderpack": {
            "name": meta.get("effective_shader_pack") or "unset",
            "sha256": shaderpack_sha256(shaderpack_text, meta),
        },
        "dh": dh_state_from_text(combined_logs, meta),
        "config_before": config_snapshot_hash(capture_dir, "config_before"),
        "workload_families": stable_workload_family_summary(frame_doc, combined_logs),
    }


def instrumentation_signature(meta: dict[str, str], command: Sequence[str]) -> dict[str, object]:
    run_type = meta.get("graphics_run_type", "clean-performance")
    validation_profile = meta.get("validation_profile") or meta.get("validation_mode", "off")
    validation_details = validation_profile_details(validation_profile)
    return {
        "run_type": run_type,
        "performance_comparable": run_type == "clean-performance",
        "diagnostics_enabled": run_type != "clean-performance" or meta.get("shader_input_parity", "off") != "off",
        "validation": {
            **validation_details,
            "mode": meta.get("validation_mode", "off"),
            "fail_severity": meta.get("validation_fail_severity", validation_details.get("fail_severity", "warning")),
            "vk_instance_layers": meta.get("vk_instance_layers"),
            "vk_add_layer_path": meta.get("vk_add_layer_path"),
            "vk_layer_settings": meta.get("vk_layer_settings"),
        },
        "renderdoc": {
            "enabled": meta.get("renderdoc_capture", "false").lower() == "true",
            "frame": parse_number(meta.get("renderdoc_frame")),
            "capture_path": meta.get("renderdoc_capture_path") or None,
        },
        "tracy": {
            "enabled": meta.get("tracy_capture", "false").lower() == "true",
            "duration_seconds": parse_number(meta.get("tracy_duration_seconds")),
            "max_size_mb": parse_number(meta.get("tracy_max_size_mb")),
        },
        "shader_input_parity": meta.get("shader_input_parity", "off"),
        "tool_versions": tool_fingerprint(),
        "launch_command_uses_renderdoc": bool(command and Path(command[0]).name == "renderdoccmd"),
    }


def semantic_draw_families(text: str) -> dict[str, int]:
    families = {
        "sodium-terrain": r"Sodium|chunk pipeline|terrain",
        "iris": r"Iris|gbuffer|composite|shadow",
        "distant-horizons": r"DistantHorizons|\[DH-|renderLods|LodRenderer",
        "voxelmap": r"VoxelMap|voxelmap",
        "vanilla": r"LevelRenderer|RenderType|entity|weather|sky",
    }
    return {name: len(re.findall(pattern, text, re.IGNORECASE)) for name, pattern in families.items()}


def comparability_key(artifact: dict[str, object]) -> dict[str, object]:
    fingerprint = artifact.get("benchmark_fingerprint")
    if not isinstance(fingerprint, dict):
        return {}
    signature = fingerprint.get("workload_signature")
    if not isinstance(signature, dict):
        return {}
    normalized = json.loads(json.dumps(signature))
    instrumentation = fingerprint.get("instrumentation")
    if isinstance(instrumentation, dict):
        normalized["instrumentation"] = {
            "run_type": instrumentation.get("run_type"),
            "validation": instrumentation.get("validation", {}),
            "renderdoc": instrumentation.get("renderdoc", {}),
            "tracy": instrumentation.get("tracy", {}),
            "shader_input_parity": instrumentation.get("shader_input_parity"),
        }
    settings = normalized.get("settings")
    if isinstance(settings, dict):
        settings.pop("backend", None)
        settings.pop("validation_mode", None)
    families = normalized.get("workload_families")
    if isinstance(families, dict):
        for family in families.values():
            if isinstance(family, dict):
                family.pop("count", None)
    return normalized


def compare_workloads(left: dict[str, object], right: dict[str, object]) -> dict[str, object]:
    left_key = comparability_key(left)
    right_key = comparability_key(right)
    differences = workload_differences(left_key, right_key)
    match = bool(left_key) and not differences
    return {
        "comparable": match,
        "left_hash": stable_json_hash(left_key) if left_key else None,
        "right_hash": stable_json_hash(right_key) if right_key else None,
        "reason": "ok" if match else "workload-fingerprint-mismatch",
        "differences": differences,
        "left": left_key,
        "right": right_key,
    }


def workload_differences(left: object, right: object, prefix: str = "") -> list[dict[str, object]]:
    if workload_values_equivalent(prefix, left, right):
        return []
    if left == right:
        return []
    if isinstance(left, dict) and isinstance(right, dict):
        differences: list[dict[str, object]] = []
        for key in sorted(set(left) | set(right)):
            child_prefix = f"{prefix}.{key}" if prefix else str(key)
            differences.extend(workload_differences(left.get(key), right.get(key), child_prefix))
        return differences
    return [{"path": prefix or "$", "left": left, "right": right}]


def workload_values_equivalent(path: str, left: object, right: object) -> bool:
    if not path:
        return False
    left_number = parse_number(left)
    right_number = parse_number(right)
    if left_number is None or right_number is None:
        return False
    tolerance = workload_numeric_tolerance(path)
    if tolerance is None:
        return False
    absolute, relative = tolerance
    delta = abs(left_number - right_number)
    scale = max(abs(left_number), abs(right_number), 1.0)
    return delta <= absolute or (delta / scale) <= relative


def workload_numeric_tolerance(path: str) -> tuple[float, float] | None:
    if path.endswith((".loaded_chunks", ".effective_render_distance")):
        return (4.0, 0.10)
    if path.endswith((".entity_count", ".player_count")):
        return (1.0, 0.05)
    if path.endswith(".calls_per_frame"):
        return (0.25, 0.20)
    if path.endswith((".file_count", ".total_bytes")):
        return (0.0, 0.0)
    return None


def reject_mismatched_workloads(left: dict[str, object], right: dict[str, object]) -> None:
    comparison = compare_workloads(left, right)
    if not comparison["comparable"]:
        differences = comparison.get("differences")
        raise ValueError(
            f"Refusing graphics comparison: {comparison['reason']} "
            f"left={comparison['left_hash']} right={comparison['right_hash']} "
            f"differences={differences}"
        )


def baseline_reusable(baseline: dict[str, object], requested_fingerprint: dict[str, object]) -> bool:
    if baseline.get("schema") != SCHEMA:
        return False
    fingerprint = baseline.get("benchmark_fingerprint")
    return isinstance(fingerprint, dict) and stable_json_hash(fingerprint) == stable_json_hash(requested_fingerprint)


def runtime_profile_dict(args: argparse.Namespace) -> dict[str, int | str]:
    return asdict(RUNTIME_PROFILES[args.profile])


def phase_timeout_seconds(args: argparse.Namespace, phase: str) -> int:
    value = getattr(args, f"{phase}_timeout_seconds", None)
    if isinstance(value, int):
        return value
    profile = RUNTIME_PROFILES[args.profile]
    return int(getattr(profile, f"{phase}_timeout_seconds"))


def per_mode_timeout_seconds(args: argparse.Namespace) -> int:
    return min(args.timeout_seconds, RUNTIME_PROFILES[args.profile].hard_timeout_seconds)


def child_process_timeout_seconds(args: argparse.Namespace) -> int:
    cleanup_budget = max(1, phase_timeout_seconds(args, "cleanup"))
    return max(1, per_mode_timeout_seconds(args) - cleanup_budget - 1)


def timeout_phase_for_artifact(
    tool_kind: str,
    timed_out: bool,
    exit_code: int | None,
    frame_doc: dict[str, object] | None,
    subsystem_doc: dict[str, object] | None,
    deterministic_doc: dict[str, object] | None,
) -> str | None:
    if not timed_out and exit_code == 0:
        return None
    if tool_kind == "gameplay":
        if not isinstance(frame_doc, dict):
            return "startup"
        status = str(frame_doc.get("status") or "")
        measured = parse_number(frame_doc.get("measuredFrameCount")) or 0.0
        requested = parse_number(frame_doc.get("measureFramesRequested")) or 0.0
        if frame_doc.get("worldEntered") is False:
            return "readiness"
        if status == "failed" and frame_doc.get("lastReadinessBlocker"):
            return "readiness"
        if measured <= 0:
            return "warmup"
        if status != "complete" or (requested > 0 and measured < requested):
            return "measurement"
        return "shutdown"
    if tool_kind == "subsystem":
        if not isinstance(subsystem_doc, dict):
            return "startup"
        if subsystem_doc.get("status") != "complete":
            return "measurement"
        return "shutdown"
    if tool_kind == "capture":
        if not isinstance(deterministic_doc, dict):
            return "startup"
        if deterministic_doc.get("status") != "complete":
            return "measurement"
        return "shutdown"
    return "shutdown"


def parse_vulkan_perf_reports(capture_dir: Path) -> dict[str, object]:
    reports = sorted(capture_dir.rglob("vulkan-perf-audit-*.txt"))
    totals: dict[str, float] = {}
    for report in reports:
        for key, value in read_key_values(report).items():
            number = parse_number(value)
            if number is not None:
                totals[key] = totals.get(key, 0.0) + number
    return {"report_count": len(reports), "totals": totals}


def summarize_renderdoc_capture(summary: dict[str, object] | None) -> dict[str, object]:
    if not isinstance(summary, dict):
        return {"enabled": False, "status": "not_requested"}
    return {
        "enabled": True,
        "status": summary.get("status", "unknown"),
        "capture_path": summary.get("capture_path"),
        "replay_status": summary.get("replay_status"),
        "event_count": summary.get("event_count"),
        "draw_count": summary.get("draw_count"),
        "dispatch_count": summary.get("dispatch_count"),
        "pass_count": summary.get("pass_count"),
        "attachments": summary.get("attachments", []),
        "resource_hashes": summary.get("resource_hashes", []),
        "failure": summary.get("failure"),
    }


def summarize_tracy_capture(summary: dict[str, object] | None) -> dict[str, object]:
    if not isinstance(summary, dict):
        return {"enabled": False, "status": "not_requested"}
    return {
        "enabled": True,
        "status": summary.get("status", "unknown"),
        "capture_path": summary.get("capture_path"),
        "duration_seconds": summary.get("duration_seconds"),
        "size_bytes": summary.get("size_bytes"),
        "zones": summary.get("zones", {}),
        "ffi": summary.get("ffi", {}),
        "allocations": summary.get("allocations", {}),
        "cache_misses": summary.get("cache_misses", {}),
        "unattributed_time": summary.get("unattributed_time"),
        "failure": summary.get("failure"),
    }


def normalize_capture_artifact(
    target: RepoTarget,
    mode: ModeSpec,
    capture_dir: Path,
    workload_profile: str,
    success: bool,
    command: Sequence[str],
    exit_code: int | None,
    timed_out: bool,
    timed_out_phase: str | None = None,
    reused_baseline: bool = False,
    tool_kind: str = "capture",
    runtime_profile: dict[str, object] | None = None,
) -> dict[str, object]:
    files = load_capture_files(capture_dir)
    meta = read_key_values(files["meta"])
    shader_summary = read_key_values(files["shader_summary"])
    deterministic_doc = read_json(files["deterministic"]) if files["deterministic"] else None
    frame_doc = read_json(files["frame_benchmark"]) if files["frame_benchmark"] else None
    subsystem_doc = read_json(files["subsystem_benchmark"]) if files["subsystem_benchmark"] else None
    renderdoc_doc = read_json(files["renderdoc_summary"]) if files["renderdoc_summary"] else None
    tracy_doc = read_json(files["tracy_summary"]) if files["tracy_summary"] else None
    effective_meta = {**shader_summary, **meta}
    launch_uses_renderdoc = bool(command and Path(command[0]).name == "renderdoccmd")
    if isinstance(renderdoc_doc, dict) or launch_uses_renderdoc:
        effective_meta.setdefault("graphics_run_type", "renderdoc-capture")
        effective_meta["renderdoc_capture"] = "true"
        if isinstance(renderdoc_doc, dict) and renderdoc_doc.get("capture_path"):
            effective_meta.setdefault("renderdoc_capture_path", str(renderdoc_doc.get("capture_path")))
    if isinstance(tracy_doc, dict):
        effective_meta.setdefault("graphics_run_type", "tracy-capture")
        effective_meta["tracy_capture"] = "true"
        if tracy_doc.get("duration_seconds") is not None:
            effective_meta.setdefault("tracy_duration_seconds", str(tracy_doc.get("duration_seconds")))
    failed_phase = timed_out_phase or timeout_phase_for_artifact(
        tool_kind, timed_out, exit_code, frame_doc, subsystem_doc, deterministic_doc
    )
    log_paths = [
        files["run_log"],
        files["latest_log"],
        files["latest_tail"],
        files["shader_events"],
        files["validation_events"],
        files["crash_reports"],
        files["hs_err"],
    ]
    combined_logs = "\n".join(file_text(path) for path in log_paths)
    attribution = detect_attribution(mode, meta, combined_logs)
    shaderpack_text = file_text(files["shaderpack"])
    signature = workload_signature(
        target,
        capture_dir,
        mode,
        workload_profile,
        effective_meta,
        deterministic_doc,
        frame_doc,
        combined_logs,
        shaderpack_text,
    )
    benchmark_fingerprint = {
        "schema": "mattmc-graphics-workload-fingerprint-v1",
        "mode": mode.name,
        "workload_signature": signature,
        "instrumentation": instrumentation_signature(effective_meta, command),
        "capture_script": str(run_dev_capture_script(target.root)),
    }
    frame_nanos = []
    if isinstance(frame_doc, dict) and isinstance(frame_doc.get("frameNanosSamples"), list):
        frame_nanos = [value for value in frame_doc["frameNanosSamples"] if isinstance(value, (int, float))]
    frame_validity = frame_doc.get("validity") if isinstance(frame_doc, dict) and isinstance(frame_doc.get("validity"), dict) else {}
    frame_times = frame_nanos_to_millis(frame_nanos)
    fps_values = fps_from_frame_nanos(frame_nanos)
    exclusive_phases = frame_doc.get("exclusivePhaseNanos") if isinstance(frame_doc, dict) else None
    nested_phases = frame_doc.get("nestedPhaseNanos") if isinstance(frame_doc, dict) else None
    cpu_phase_timings = {
        key: phase_stats_to_distribution(value)
        for key, value in (exclusive_phases.items() if isinstance(exclusive_phases, dict) else [])
        if isinstance(value, dict)
    }
    nested_cpu_phase_timings = {
        key: phase_stats_to_distribution(value)
        for key, value in (nested_phases.items() if isinstance(nested_phases, dict) else [])
        if isinstance(value, dict)
    }
    java_doc = frame_doc.get("java") if isinstance(frame_doc, dict) and isinstance(frame_doc.get("java"), dict) else {}
    vulkan_perf = parse_vulkan_perf_reports(capture_dir)
    validation_findings = parse_validation_findings(combined_logs)
    renderdoc_summary = summarize_renderdoc_capture(renderdoc_doc)
    tracy_summary = summarize_tracy_capture(tracy_doc)
    error_counts = {name: count_pattern(log_paths, pattern) for name, pattern in ERROR_PATTERNS.items()}
    error_counts["crash"] += listed_failure_file_count(files["crash_reports"])
    error_counts["crash"] += listed_failure_file_count(files["hs_err"])
    memory_guard_triggered = meta.get("memory_guard_triggered", "false").lower() == "true" or error_counts["rss_guard"] > 0
    frame_sample_window_complete = tool_kind != "gameplay" or (
        isinstance(frame_doc, dict)
        and frame_doc.get("status") == "complete"
        and frame_doc.get("measuredFrameCount") == len(frame_nanos)
        and len(frame_nanos) > 0
    )
    displayed_fps_required = bool(frame_validity.get("displayedFpsCheckRequired"))
    if "displayedFpsCheckRequired" not in frame_validity:
        displayed_fps_required = len(frame_nanos) >= 240
    frame_validity_complete = tool_kind != "gameplay" or (
        frame_sample_window_complete
        and frame_validity.get("wallClockCheckPassed") is not False
        and (not displayed_fps_required or frame_validity.get("displayedFpsCheckPassed") is not False)
    )
    world_entered = tool_kind != "gameplay" or (
        isinstance(frame_doc, dict)
        and frame_doc.get("status") == "complete"
        and frame_doc.get("worldEntered") is not False
        and len(frame_nanos) > 0
    )
    subsystem_complete = tool_kind != "subsystem" or (
        isinstance(subsystem_doc, dict)
        and subsystem_doc.get("status") == "complete"
        and isinstance(subsystem_doc.get("workloads"), list)
        and len(subsystem_doc.get("workloads")) >= 7
        and all(isinstance(workload, dict) and workload.get("status") == "ok" for workload in subsystem_doc.get("workloads"))
    )
    deterministic_complete = tool_kind != "capture" or (
        isinstance(deterministic_doc, dict)
        and deterministic_doc.get("status") == "complete"
    )
    validation_messages: list[str] = []
    if not files["meta"]:
        validation_messages.append("capture metadata is missing")
    if not (success or reused_baseline):
        validation_messages.append("capture process failed or timed out")
    if failed_phase:
        validation_messages.append(f"phase failure: {failed_phase}")
    if tool_kind == "gameplay":
        if isinstance(frame_doc, dict) and frame_doc.get("status") == "failed":
            reason = frame_doc.get("failureReason") or "unknown sampler failure"
            blocker = frame_doc.get("lastReadinessBlocker")
            detail = f"; {blocker}" if blocker else ""
            validation_messages.append(f"game-loop sampler failed: {reason}{detail}")
        if not world_entered:
            validation_messages.append("gameplay workload did not enter a world; title-screen/startup-only runs are rejected")
        if not frame_sample_window_complete:
            validation_messages.append("missing or incomplete measured game-loop frame samples")
        if not frame_validity_complete:
            validation_messages.append("frame sampler wall-clock/displayed-FPS consistency checks failed")
    if tool_kind == "subsystem" and not subsystem_complete:
        validation_messages.append("isolated subsystem benchmark did not complete all required workloads")
    if tool_kind == "capture" and not deterministic_complete:
        validation_messages.append("deterministic correctness capture did not complete")
    instrumentation = benchmark_fingerprint["instrumentation"]
    run_type = instrumentation.get("run_type") if isinstance(instrumentation, dict) else "clean-performance"
    if run_type in INSTRUMENTED_RUN_TYPES:
        validation_messages.append(f"{run_type} run is diagnostic-only; performance numbers are not publishable")
    if isinstance(instrumentation, dict) and instrumentation.get("renderdoc", {}).get("enabled"):
        if renderdoc_summary.get("status") != "complete":
            validation_messages.append("RenderDoc capture/replay did not complete")
    if isinstance(instrumentation, dict) and instrumentation.get("tracy", {}).get("enabled"):
        if tracy_summary.get("status") != "complete":
            validation_messages.append("Tracy capture did not complete")
    hard_errors_absent = (
        error_counts["crash"] == 0
        and error_counts["gl_error"] == 0
        and error_counts["vuid"] == 0
        and error_counts["device_loss"] == 0
        and not memory_guard_triggered
        and error_counts["orphan_process"] == 0
    )
    renderdoc_complete = not (isinstance(instrumentation, dict) and instrumentation.get("renderdoc", {}).get("enabled")) or renderdoc_summary.get("status") == "complete"
    tracy_complete = not (isinstance(instrumentation, dict) and instrumentation.get("tracy", {}).get("enabled")) or tracy_summary.get("status") == "complete"
    if not hard_errors_absent:
        validation_messages.append("strict error scan found crash, GL/Vulkan validation, device-loss, RSS, or orphan-process evidence")
    complete = (
        bool(files["meta"])
        and (success or reused_baseline)
        and frame_validity_complete
        and world_entered
        and subsystem_complete
        and deterministic_complete
        and renderdoc_complete
        and tracy_complete
        and hard_errors_absent
    )
    artifact = {
        "schema": SCHEMA,
        "created_at": utc_now(),
        "tool": tool_kind,
        "mode": asdict(mode),
        "repository": repository_metadata(target),
        "capture": {
            "directory": str(capture_dir),
            "command": list(command),
            "success": success,
            "exit_code": exit_code,
            "timed_out": timed_out,
            "timed_out_phase": failed_phase if timed_out else None,
            "failed_phase": failed_phase,
            "reused_baseline": reused_baseline,
            "files": {key: str(path) if path else None for key, path in files.items()},
        },
        "runtime_profile": runtime_profile or {},
        "benchmark_fingerprint": benchmark_fingerprint,
        "implementation_attribution": attribution,
        "expected_attribution": mode.expected_attribution,
        "metrics": {
            "source": {
                "frame_samples": str(files["frame_benchmark"]) if files["frame_benchmark"] else None,
                "fps_derivation": "1e9/frameNanosSamples; never parsed from log FPS text",
                "subsystem_samples": str(files["subsystem_benchmark"]) if files["subsystem_benchmark"] else None,
            },
            "frame_time_ms": summarize_distribution(frame_times),
            "fps": summarize_distribution(fps_values),
            "cpu_phase_timings": cpu_phase_timings,
            "nested_cpu_phase_timings": nested_cpu_phase_timings,
            "phase_family_timings": phase_family_timings(cpu_phase_timings),
            "nested_phase_family_timings": phase_family_timings(nested_cpu_phase_timings),
            "java_allocation_and_gc": {
                "gc_events": java_doc.get("gcCountDelta") if isinstance(java_doc, dict) else None,
                "gc_time_millis": java_doc.get("gcTimeMillisDelta") if isinstance(java_doc, dict) else None,
                "used_memory_bytes_start": java_doc.get("usedMemoryBytesAtStart") if isinstance(java_doc, dict) else None,
                "used_memory_bytes_end": java_doc.get("usedMemoryBytesAtEnd") if isinstance(java_doc, dict) else None,
                "allocation_bytes": first_number(combined_logs, r"allocation(?:_bytes| bytes)?[=: ]+(\d+)"),
            },
            "frame_sampler_validity": frame_validity,
            "rust_allocation": {
                "allocation_bytes": first_number(combined_logs, r"rust(?:_native)?_allocation(?:_bytes| bytes)?[=: ]+(\d+)"),
            },
            "rss_and_native_memory": {
                "peak_rss_kb": parse_number(meta.get("memory_guard_peak_rss_kb")),
                "rss_guard_triggered": memory_guard_triggered,
                "native_or_vulkan_memory_bytes": first_number(combined_logs, r"(?:native|vulkan).*memory.*?(\d+)"),
            },
            "ffi": {
                "call_count": first_number(combined_logs, r"ffi(?:_call)?_count[=: ]+(\d+)"),
                "bytes": first_number(combined_logs, r"ffi(?:_bytes| bytes)[=: ]+(\d+)"),
            },
            "work_counts": {
                "draw": len(re.findall(r"\bdraw(?:Indexed|Arrays)?\b", combined_logs)),
                "dispatch": len(re.findall(r"\bdispatchCompute\b|\bdispatch\b", combined_logs)),
                "pass": len(re.findall(r"render pass|RenderPass|render_pass", combined_logs, re.IGNORECASE)),
                "transfer": len(re.findall(r"copyBuffer|copyImage|blit|transfer", combined_logs, re.IGNORECASE)),
            },
            "creation_counts": {
                "pipeline": len(re.findall(r"create.*pipeline|Creating .*pipeline", combined_logs, re.IGNORECASE)),
                "descriptor": len(re.findall(r"descriptor", combined_logs, re.IGNORECASE)),
                "resource": len(re.findall(r"create.*(?:buffer|texture|image|sampler)", combined_logs, re.IGNORECASE)),
            },
            "vulkan": vulkan_perf,
            "validation_findings": {
                "profile": meta.get("validation_profile") or meta.get("validation_mode", "off"),
                "finding_count": len(validation_findings),
                "findings": validation_findings,
            },
            "renderdoc": renderdoc_summary,
            "tracy": tracy_summary,
            "opengl_state": {
                "requested_state_changes": first_number(combined_logs, r"opengl.*requested.*state.*?(\d+)"),
                "emitted_state_changes": first_number(combined_logs, r"opengl.*emitted.*state.*?(\d+)"),
            },
            "subsystem": subsystem_doc or {"status": "not_available"},
            "errors": error_counts,
        },
        "validation": {
            "strict_gl_error_scan_passed": error_counts["gl_error"] == 0,
            "vulkan_validation_passed": error_counts["vuid"] == 0,
            "crash_free": error_counts["crash"] == 0,
            "device_loss_free": error_counts["device_loss"] == 0,
            "rss_guard_triggered": memory_guard_triggered,
            "orphan_process_detected": error_counts["orphan_process"] > 0,
            "complete": complete,
            "workload_entered": world_entered,
            "frame_samples_complete": frame_sample_window_complete,
            "frame_sampler_validity_passed": frame_validity_complete,
            "subsystem_complete": subsystem_complete,
            "deterministic_capture_complete": deterministic_complete,
            "performance_publishable": run_type == "clean-performance" and complete,
            "renderdoc_complete": renderdoc_complete,
            "tracy_complete": tracy_complete,
            "messages": validation_messages,
        },
        "diagnosis": incomplete_run_diagnosis(
            tool_kind,
            failed_phase,
            frame_doc,
            subsystem_doc,
            deterministic_doc,
            error_counts,
            memory_guard_triggered,
            attribution,
        ),
    }
    artifact["benchmark_fingerprint"]["hash"] = stable_json_hash(artifact["benchmark_fingerprint"])
    return artifact


def first_number(text: str, pattern: str) -> float | None:
    match = re.search(pattern, text, re.IGNORECASE)
    if not match:
        return None
    return parse_number(match.group(1))


def incomplete_run_diagnosis(
    tool_kind: str,
    failed_phase: str | None,
    frame_doc: dict[str, object] | None,
    subsystem_doc: dict[str, object] | None,
    deterministic_doc: dict[str, object] | None,
    error_counts: dict[str, int],
    memory_guard_triggered: bool,
    attribution: str,
) -> dict[str, object]:
    evidence: list[str] = []
    if isinstance(frame_doc, dict):
        evidence.append(f"frame_status={frame_doc.get('status')}")
        evidence.append(f"world_entered={frame_doc.get('worldEntered')}")
        evidence.append(f"measured_frames={frame_doc.get('measuredFrameCount')}/{frame_doc.get('measureFramesRequested')}")
        if frame_doc.get("failureReason"):
            evidence.append(f"failure_reason={frame_doc.get('failureReason')}")
        if frame_doc.get("lastReadinessBlocker"):
            evidence.append(f"readiness={frame_doc.get('lastReadinessBlocker')}")
    if isinstance(subsystem_doc, dict):
        evidence.append(f"subsystem_status={subsystem_doc.get('status')}")
        failed_workloads = [
            str(workload.get("name"))
            for workload in subsystem_doc.get("workloads", [])
            if isinstance(workload, dict) and workload.get("status") != "ok"
        ]
        if failed_workloads:
            evidence.append(f"failed_subsystem_workloads={','.join(failed_workloads)}")
    if isinstance(deterministic_doc, dict):
        evidence.append(f"deterministic_status={deterministic_doc.get('status')}")
    for name, count in sorted(error_counts.items()):
        if count:
            evidence.append(f"{name}_count={count}")
    if memory_guard_triggered:
        evidence.append("rss_guard_triggered=true")
    if attribution == "fallback":
        evidence.append("implementation_attribution=fallback")
    root_cause = "complete"
    if failed_phase:
        root_cause = f"{failed_phase}-phase"
    if error_counts.get("device_loss", 0):
        root_cause = "device-loss"
    elif error_counts.get("vuid", 0):
        root_cause = "vulkan-validation"
    elif error_counts.get("crash", 0):
        root_cause = "crash"
    elif memory_guard_triggered:
        root_cause = "rss-guard"
    elif attribution == "fallback":
        root_cause = "fallback-route"
    elif not failed_phase and tool_kind == "gameplay" and isinstance(frame_doc, dict) and frame_doc.get("worldEntered") is False:
        root_cause = "world-readiness"
    elif tool_kind == "subsystem" and isinstance(subsystem_doc, dict) and subsystem_doc.get("status") != "complete":
        root_cause = "subsystem-measurement"
    return {
        "root_cause": root_cause,
        "blocking_phase": failed_phase,
        "evidence": evidence,
    }


def write_artifact(path: Path, artifact: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(artifact, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_preflight_meta(capture_dir: Path, mode: ModeSpec, args: argparse.Namespace, env: Mapping[str, str]) -> Path:
    lines = [
        f"run_id=preflight-{timestamp()}",
        f"backend={mode.backend}",
        f"shaders={mode.shaders}",
        f"world={getattr(args, 'world', DEFAULT_WORLD)}",
        f"validation_mode={getattr(args, 'validation', 'off')}",
        f"graphics_run_type={env.get('MATTMC_GRAPHICS_RUN_TYPE', run_type_for_args(args))}",
        f"validation_profile={env.get('MATTMC_GRAPHICS_VALIDATION_PROFILE', getattr(args, 'validation', 'off'))}",
        f"validation_fail_severity={env.get('MATTMC_GRAPHICS_VALIDATION_FAIL_SEVERITY', 'warning')}",
        f"renderdoc_capture={env.get('MATTMC_RENDERDOC_CAPTURE', 'false')}",
        f"renderdoc_frame={env.get('MATTMC_RENDERDOC_FRAME', '0')}",
        f"renderdoc_capture_path={env.get('MATTMC_RENDERDOC_CAPTURE_PATH', '')}",
        f"tracy_capture={env.get('MATTMC_TRACY_CAPTURE', 'false')}",
        f"tracy_duration_seconds={env.get('MATTMC_TRACY_DURATION_SECONDS', '0')}",
        f"tracy_max_size_mb={env.get('MATTMC_TRACY_MAX_SIZE_MB', '0')}",
        f"client_args_initial={getattr(args, 'client_args', '')}",
        "preflight_failure=true",
    ]
    path = capture_dir / f"meta_preflight_{timestamp()}.txt"
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return path


def write_renderdoc_summary(capture_dir: Path, status: str, capture_path: Path | None = None, failure: str | None = None) -> Path:
    summary = {
        "schema": "mattmc-renderdoc-summary-v1",
        "status": status,
        "capture_path": str(capture_path) if capture_path else None,
        "replay_status": "not_run" if status != "complete" else "complete",
        "event_count": None,
        "draw_count": None,
        "dispatch_count": None,
        "pass_count": None,
        "attachments": [],
        "resource_hashes": [],
        "failure": failure,
    }
    path = capture_dir / f"renderdoc_summary_{timestamp()}.json"
    path.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return path


def write_tracy_summary(capture_dir: Path, status: str, capture_path: Path | None = None, failure: str | None = None, started_at: float | None = None) -> Path:
    size_bytes = capture_path.stat().st_size if capture_path and capture_path.exists() else 0
    summary = {
        "schema": "mattmc-tracy-summary-v1",
        "status": status,
        "capture_path": str(capture_path) if capture_path else None,
        "duration_seconds": (time.monotonic() - started_at) if started_at else None,
        "size_bytes": size_bytes,
        "zones": {},
        "ffi": {},
        "allocations": {},
        "cache_misses": {},
        "unattributed_time": None,
        "failure": failure,
    }
    path = capture_dir / f"tracy_summary_{timestamp()}.json"
    path.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return path


def renderdoc_capture_path_from_env(env: dict[str, str], capture_dir: Path) -> Path:
    configured = env.get("MATTMC_RENDERDOC_CAPTURE_PATH")
    return Path(configured) if configured else capture_dir / f"renderdoc_{timestamp()}.rdc"


def resolve_renderdoc_capture_path(capture_dir: Path, requested: Path) -> Path:
    if requested.exists():
        return requested
    captures = sorted(capture_dir.glob("*.rdc"), key=lambda path: path.stat().st_mtime if path.exists() else 0)
    return captures[-1] if captures else requested


def replay_renderdoc_summary(capture_dir: Path, capture_path: Path) -> Path:
    capture_path = resolve_renderdoc_capture_path(capture_dir, capture_path)
    if not capture_path.exists():
        return write_renderdoc_summary(capture_dir, "failed", capture_path, "RenderDoc did not produce an .rdc file")
    renderdoc = local_renderdoccmd_path()
    if not renderdoc:
        return write_renderdoc_summary(capture_dir, "failed", capture_path, "renderdoccmd is not available for replay")
    # Keep replay bounded and conservative. Not every RenderDoc build exposes the same JSON export flags,
    # so the permanent artifact records capture presence and fails loudly when replay cannot be invoked.
    try:
        result = subprocess.run(
            [renderdoc, "replay", "-l", "1", str(capture_path)],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=45,
            check=False,
        )
    except Exception as exc:
        return write_renderdoc_summary(capture_dir, "failed", capture_path, f"RenderDoc replay failed to start: {exc}")
    if result.returncode != 0:
        summary_path = write_renderdoc_summary(capture_dir, "failed", capture_path, f"RenderDoc replay exited {result.returncode}")
        summary_path.with_suffix(".log").write_text(result.stdout, encoding="utf-8", errors="replace")
        return summary_path
    event_count = len(re.findall(r"\bEID\b|Event", result.stdout))
    draw_count = len(re.findall(r"\bDraw(?:Indexed|Instanced)?\b", result.stdout))
    dispatch_count = len(re.findall(r"\bDispatch\b", result.stdout))
    pass_count = len(re.findall(r"RenderPass|render pass|BeginPass", result.stdout, re.IGNORECASE))
    summary = {
        "schema": "mattmc-renderdoc-summary-v1",
        "status": "complete",
        "capture_path": str(capture_path),
        "replay_status": "complete",
        "event_count": event_count,
        "draw_count": draw_count,
        "dispatch_count": dispatch_count,
        "pass_count": pass_count,
        "attachments": [],
        "resource_hashes": [],
        "failure": None,
    }
    path = capture_dir / f"renderdoc_summary_{timestamp()}.json"
    path.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    path.with_suffix(".log").write_text(result.stdout, encoding="utf-8", errors="replace")
    return path


def build_capture_command(
    target: RepoTarget,
    mode: ModeSpec,
    capture_dir: Path,
    workload_profile: str,
    args: argparse.Namespace,
    tool_kind: str,
) -> tuple[list[str], dict[str, str]]:
    kind, entrypoint = run_dev_capture_entrypoint(target.root)
    requested_validation = "routine" if args.validation == "standard" else args.validation
    validation = "standard" if mode.supports_validation and requested_validation != "off" else "off"
    base_client_args = args.client_args
    for option in ("--quickPlaySingleplayer", "--width", "--height"):
        base_client_args = remove_client_arg_option(base_client_args, option)
    base_client_args = remove_client_arg_assignment(base_client_args, "enableShaders")
    client_arg_parts = [base_client_args]
    if run_dev_capture_entrypoint(target.root)[0] == "shell":
        client_arg_parts.append(f"--quickPlaySingleplayer={args.world}")
        client_arg_parts.append("--width 1280")
        client_arg_parts.append("--height 720")
    client_arg_parts.append(f"enableShaders={'true' if mode.shaders == 'on' else 'false'}")
    client_args = " ".join(part for part in client_arg_parts if part)
    if kind == "python":
        command = [
            sys.executable,
            str(entrypoint),
            "--backend",
            mode.backend,
            "--shaders",
            mode.shaders,
            "--artifact-dir",
            str(capture_dir),
            "--world",
            args.world,
            "--max-secs",
            str(args.max_secs),
            "--dump-secs",
            str(args.dump_secs),
            "--skip-tests",
            "--validation",
            validation,
        ]
        if client_args:
            command.extend(["--client-args", client_args])
        if tool_kind == "capture" and workload_profile in {"correctness", "moving-camera"}:
            command.append("--deterministic-camera-capture")
        if tool_kind == "capture" and workload_profile == "settled-static":
            command.append("--deterministic-static-camera-capture")
    else:
        shell = "bash" if platform_name() != "windows" else "bash"
        command = [
            shell,
            str(entrypoint),
            "--backend",
            mode.backend,
            "--max-secs",
            str(args.max_secs),
            "--dump-secs",
            str(args.dump_secs),
            "--validation",
            validation,
        ]
        if client_args:
            command.extend(["--client-args", client_args])
    env = os.environ.copy()
    run_type = run_type_for_args(args)
    env["MATTMC_GRAPHICS_TOOL_INTERNAL"] = "1"
    env["MATTMC_GRAPHICS_RUN_TYPE"] = run_type
    env["MATTMC_GRAPHICS_VALIDATION_PROFILE"] = requested_validation
    env["MATTMC_GRAPHICS_VALIDATION_FAIL_SEVERITY"] = getattr(args, "validation_fail_severity", "warning")
    env["MATTMC_RENDERDOC_CAPTURE"] = "true" if getattr(args, "renderdoc_capture", False) else "false"
    env["MATTMC_RENDERDOC_FRAME"] = str(getattr(args, "renderdoc_frame", 8))
    env["MATTMC_TRACY_CAPTURE"] = "true" if getattr(args, "tracy_capture", False) else "false"
    env["MATTMC_TRACY_DURATION_SECONDS"] = str(getattr(args, "tracy_duration_seconds", 20))
    env["MATTMC_TRACY_MAX_SIZE_MB"] = str(getattr(args, "tracy_max_size_mb", 256))
    env["MATTMC_CAPTURE_WORLD"] = args.world
    env["MATTMC_CAPTURE_WORLD_SOURCE"] = str(CURRENT_REPO_ROOT / "run" / "saves" / args.world)
    env["CLIENT_RSS_LIMIT_MB"] = str(args.client_rss_limit_mb)
    java_options = [env.get("JAVA_TOOL_OPTIONS", "")]
    if tool_kind == "gameplay":
        frame_status = capture_dir / f"graphics_frame_benchmark_{timestamp()}.json"
        java_options.extend(
            [
                "-Dmattmc.dev.graphicsFrameBenchmark=true",
                f"-Dmattmc.dev.graphicsFrameBenchmark.status={frame_status}",
                f"-Dmattmc.dev.graphicsFrameBenchmark.settleFrames={args.settle_frames}",
                f"-Dmattmc.dev.graphicsFrameBenchmark.maxSettleFrames={args.max_settle_frames}",
                f"-Dmattmc.dev.graphicsFrameBenchmark.warmupFrames={args.warmup_frames}",
                f"-Dmattmc.dev.graphicsFrameBenchmark.measureFrames={args.measure_frames}",
                f"-Dmattmc.dev.graphicsFrameBenchmark.workloadProfile={workload_profile}",
                f"-Dmattmc.dev.graphicsFrameBenchmark.world={args.world}",
                f"-Dmattmc.dev.graphicsFrameBenchmark.backend={mode.backend}",
                f"-Dmattmc.dev.graphicsFrameBenchmark.shaders={mode.shaders}",
                f"-Dmattmc.dev.graphicsFrameBenchmark.implementation={mode.expected_attribution}",
            ]
        )
        env["MATTMC_GRAPHICS_GAMEPLAY_BENCHMARK"] = "true"
    if tool_kind == "subsystem":
        subsystem_status = capture_dir / f"graphics_subsystem_benchmark_{timestamp()}.json"
        java_options.extend(
            [
                "-Dmattmc.dev.graphicsSubsystemBenchmark=true",
                f"-Dmattmc.dev.graphicsSubsystemBenchmark.status={subsystem_status}",
                f"-Dmattmc.dev.graphicsSubsystemBenchmark.iterations={args.subsystem_iterations}",
                f"-Dmattmc.dev.graphicsSubsystemBenchmark.backend={mode.backend}",
                f"-Dmattmc.dev.graphicsSubsystemBenchmark.shaders={mode.shaders}",
            ]
        )
        env["MATTMC_GRAPHICS_SUBSYSTEM_BENCHMARK"] = "true"
    if tool_kind == "capture":
        deterministic_metadata = capture_dir / f"deterministic_camera_capture_{timestamp()}.json"
        deterministic_screenshot_dir = capture_dir / deterministic_metadata.stem
        java_options.extend(
            [
                "-Dmattmc.dev.deterministicCameraCapture=true",
                f"-Dmattmc.dev.deterministicCameraCapture.metadata={deterministic_metadata}",
                f"-Dmattmc.dev.deterministicCameraCapture.screenshotDir={deterministic_screenshot_dir}",
                f"-Dmattmc.dev.deterministicCameraCapture.world={args.world}",
                "-Dmattmc.dev.deterministicCameraCapture.stopAfterComplete=true",
            ]
        )
        env["MATTMC_GRAPHICS_CORRECTNESS_CAPTURE"] = "true"
    if args.diagnostic:
        perf_dir = capture_dir / "vulkan-perf-audit"
        java_options.extend(
            [
                "-Dmattmc.vulkan.perfAudit=true",
                f"-Dmattmc.vulkan.perfAuditReportDir={perf_dir}",
                "-Dmattmc.graphicsAudit=true",
            ]
        )
        env["MATTMC_GRAPHICS_AUDIT"] = "true"
    if requested_validation == "routine":
        env["VK_LAYER_SETTINGS"] = "validate_sync=true,validate_best_practices=true"
    elif requested_validation == "deep":
        env["VK_LAYER_SETTINGS"] = "validate_sync=true,validate_best_practices=true,gpuav_enable=true,printf_enable=true"
    if getattr(args, "tracy_capture", False):
        env["MATTMC_GRAPHICS_AUDIT"] = "true"
        java_options.append("-Dmattmc.dev.tracyCapture=true")
    if getattr(args, "renderdoc_capture", False):
        renderdoc = local_renderdoccmd_path()
        renderdoc_capture = capture_dir / f"renderdoc_{timestamp()}.rdc"
        renderdoc_template = capture_dir / f"renderdoc_{timestamp()}"
        java_options.extend(
            [
                "-Dmattmc.dev.renderdocCapture=true",
                f"-Dmattmc.dev.renderdocCapture.pathTemplate={renderdoc_template}",
            ]
        )
        renderdoc_library = local_renderdoc_library_path()
        if renderdoc_library:
            java_options.append(f"-Dmattmc.dev.renderdocCapture.library={renderdoc_library}")
            existing_preload = env.get("LD_PRELOAD", "").strip()
            env["LD_PRELOAD"] = f"{renderdoc_library} {existing_preload}".strip()
        if renderdoc:
            command = [
                renderdoc,
                "capture",
                "-d",
                str(target.root),
                "-w",
                "--opt-hook-children",
                "--opt-api-validation",
                "-c",
                str(renderdoc_template),
                *command,
            ]
            env["MATTMC_RENDERDOC_CAPTURE_PATH"] = str(renderdoc_capture)
        else:
            env["MATTMC_RENDERDOC_CAPTURE_PATH"] = str(renderdoc_capture)
    env["JAVA_TOOL_OPTIONS"] = " ".join(part for part in java_options if part).strip()
    return command, env


def terminate_process_tree(process: subprocess.Popen[str]) -> None:
    if process.poll() is not None:
        return
    if platform_name() == "windows":
        subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
    else:
        try:
            os.killpg(process.pid, signal.SIGTERM)
            time.sleep(1)
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass


def popen_kwargs() -> dict[str, object]:
    if platform_name() == "windows":
        return {"creationflags": subprocess.CREATE_NEW_PROCESS_GROUP}
    return {"start_new_session": True}


def process_exists(pid: int) -> bool:
    if platform_name() == "windows":
        result = subprocess.run(["tasklist", "/FI", f"PID eq {pid}"], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, check=False)
        return str(pid) in result.stdout
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        return True


def repo_processes(root: Path) -> list[tuple[int, int, str]]:
    if platform_name() == "windows":
        return []
    result = subprocess.run(
        ["ps", "-eo", "pid=,pgid=,cmd="],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    if result.returncode != 0:
        return []
    root_text = str(root.resolve())
    matches: list[tuple[int, int, str]] = []
    current_pid = os.getpid()
    for line in result.stdout.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        parts = stripped.split(None, 2)
        if len(parts) < 3:
            continue
        try:
            pid = int(parts[0])
            pgid = int(parts[1])
        except ValueError:
            continue
        command = parts[2]
        if pid == current_pid:
            continue
        if root_text not in command:
            continue
        if any(marker in command for marker in REPO_PROCESS_MARKERS):
            matches.append((pid, pgid, command))
    return matches


def cleanup_repo_processes(root: Path) -> list[int]:
    matches = repo_processes(root)
    if not matches:
        return []
    pids = {pid for pid, _pgid, _command in matches}
    pgids = {pgid for _pid, pgid, _command in matches if pgid > 1}
    for pgid in sorted(pgids):
        try:
            os.killpg(pgid, signal.SIGTERM)
        except ProcessLookupError:
            pass
        except PermissionError:
            pass
    for pid in sorted(pids):
        try:
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
        except PermissionError:
            pass
    time.sleep(1)
    survivors = [pid for pid in pids if process_exists(pid)]
    for pgid in sorted(pgids):
        try:
            os.killpg(pgid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        except PermissionError:
            pass
    for pid in survivors:
        try:
            os.kill(pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        except PermissionError:
            pass
    return sorted(pids)


def run_mode(
    target: RepoTarget,
    mode: ModeSpec,
    artifact_root: Path,
    args: argparse.Namespace,
    tool_kind: str = "capture",
    repetition: int = 1,
) -> MatrixResult:
    kind, _ = run_dev_capture_entrypoint(target.root)
    effective_workload_profile = "gameplay" if tool_kind == "gameplay" else args.workload_profile
    run_dir = f"run-{repetition:02d}"
    capture_dir = artifact_root / mode.name / tool_kind / run_dir / "capture" if kind == "python" else target.root / "logs" / "auto-capture"
    output_path = artifact_root / mode.name / tool_kind / run_dir / ARTIFACT_NAME
    command, env = build_capture_command(target, mode, capture_dir, effective_workload_profile, args, tool_kind)
    if args.dry_run:
        artifact = normalize_capture_artifact(
            target,
            mode,
            capture_dir,
            effective_workload_profile,
            True,
            command,
            0,
            False,
            tool_kind=tool_kind,
            runtime_profile=runtime_profile_dict(args),
        )
        write_artifact(output_path, artifact)
        return MatrixResult(mode.name, True, False, False, None, 0, str(output_path), str(capture_dir), command)

    if kind == "shell" and capture_dir.exists():
        shutil.rmtree(capture_dir)
    capture_dir.mkdir(parents=True, exist_ok=True)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    if getattr(args, "renderdoc_capture", False) and not local_renderdoccmd_path():
        write_preflight_meta(capture_dir, mode, args, env)
        write_renderdoc_summary(capture_dir, "failed", renderdoc_capture_path_from_env(env, capture_dir), "renderdoccmd is not installed")
        artifact = normalize_capture_artifact(
            target,
            mode,
            capture_dir,
            effective_workload_profile,
            False,
            command,
            127,
            False,
            tool_kind=tool_kind,
            runtime_profile=runtime_profile_dict(args),
        )
        write_artifact(output_path, artifact)
        return MatrixResult(mode.name, False, False, False, "renderdoccmd is not installed", 127, str(output_path), str(capture_dir), command)
    tracy_capture_path: Path | None = None
    tracy_started_at: float | None = None
    tracy_process: subprocess.Popen[str] | None = None
    if getattr(args, "tracy_capture", False):
        tracy_tool = local_tracy_capture_path()
        tracy_capture_path = capture_dir / f"tracy_{timestamp()}.tracy"
        if not tracy_tool:
            write_preflight_meta(capture_dir, mode, args, env)
            write_tracy_summary(capture_dir, "failed", tracy_capture_path, "tracy-capture is not installed")
            artifact = normalize_capture_artifact(
                target,
                mode,
                capture_dir,
                effective_workload_profile,
                False,
                command,
                127,
                False,
                tool_kind=tool_kind,
                runtime_profile=runtime_profile_dict(args),
            )
            write_artifact(output_path, artifact)
            return MatrixResult(mode.name, False, False, False, "tracy-capture is not installed", 127, str(output_path), str(capture_dir), command)
        try:
            tracy_process = subprocess.Popen(
                [tracy_tool, "-o", str(tracy_capture_path), "-s", str(getattr(args, "tracy_duration_seconds", 20))],
                cwd=target.root,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                **popen_kwargs(),
            )
            tracy_started_at = time.monotonic()
        except Exception as exc:
            write_tracy_summary(capture_dir, "failed", tracy_capture_path, f"failed to launch tracy-capture: {exc}")
            tracy_process = None
    stdout_path = artifact_root / mode.name / tool_kind / run_dir / "stdout.log"
    stderr_path = artifact_root / mode.name / tool_kind / run_dir / "stderr.log"
    timed_out = False
    timed_out_phase: str | None = None
    cleanup_killed_processes: list[int] = []
    error = ""
    started = time.monotonic()
    timeout_seconds = child_process_timeout_seconds(args)
    hard_timeout_seconds = per_mode_timeout_seconds(args)
    with stdout_path.open("w", encoding="utf-8") as stdout, stderr_path.open("w", encoding="utf-8") as stderr:
        process = subprocess.Popen(
            command,
            cwd=target.root,
            text=True,
            stdout=stdout,
            stderr=stderr,
            env=env,
            **popen_kwargs(),
        )
        try:
            exit_code = process.wait(timeout=timeout_seconds)
        except subprocess.TimeoutExpired:
            timed_out = True
            terminate_process_tree(process)
            cleanup_killed_processes = cleanup_repo_processes(target.root)
            cleanup_timeout = max(1, phase_timeout_seconds(args, "cleanup"))
            exit_code = process.wait(timeout=cleanup_timeout)
            error = f"Timed out after {timeout_seconds}s; profile hard cap is {hard_timeout_seconds}s"
        except KeyboardInterrupt:
            terminate_process_tree(process)
            cleanup_repo_processes(target.root)
            raise
    cleanup_killed_processes = sorted(set(cleanup_killed_processes + cleanup_repo_processes(target.root)))
    if getattr(args, "renderdoc_capture", False):
        replay_renderdoc_summary(capture_dir, renderdoc_capture_path_from_env(env, capture_dir))
    if getattr(args, "tracy_capture", False):
        tracy_failure = None
        if tracy_process is not None:
            try:
                tracy_process.wait(timeout=max(1, min(getattr(args, "tracy_duration_seconds", 20) + 10, 60)))
            except subprocess.TimeoutExpired:
                terminate_process_tree(tracy_process)
                tracy_failure = "tracy-capture timed out"
            if tracy_process.returncode not in (0, None) and tracy_failure is None:
                tracy_failure = f"tracy-capture exited {tracy_process.returncode}"
        if tracy_capture_path and tracy_capture_path.exists() and tracy_capture_path.stat().st_size <= getattr(args, "tracy_max_size_mb", 256) * 1024 * 1024 and tracy_failure is None:
            write_tracy_summary(capture_dir, "complete", tracy_capture_path, None, tracy_started_at)
        else:
            if tracy_failure is None:
                tracy_failure = "Tracy capture missing, empty, or exceeded size limit"
            write_tracy_summary(capture_dir, "failed", tracy_capture_path, tracy_failure, tracy_started_at)
    success = exit_code == 0 and not timed_out
    artifact = normalize_capture_artifact(
        target,
        mode,
        capture_dir,
        effective_workload_profile,
        success,
        command,
        exit_code,
        timed_out,
        timed_out_phase=timed_out_phase,
        tool_kind=tool_kind,
        runtime_profile=runtime_profile_dict(args),
    )
    validation = artifact.get("validation") if isinstance(artifact.get("validation"), dict) else {}
    if not validation.get("complete"):
        success = False
        artifact["capture"]["success"] = False
        error = error or "Artifact validation incomplete"
    artifact["capture"]["stdout_path"] = str(stdout_path)
    artifact["capture"]["stderr_path"] = str(stderr_path)
    artifact["capture"]["duration_seconds"] = time.monotonic() - started
    artifact["capture"]["cleanup_killed_processes"] = cleanup_killed_processes
    write_artifact(output_path, artifact)
    capture_meta = artifact.get("capture") if isinstance(artifact.get("capture"), dict) else {}
    failed_phase = capture_meta.get("failed_phase") if isinstance(capture_meta.get("failed_phase"), str) else timed_out_phase
    if timed_out and failed_phase and "during" not in error:
        error = f"{error} during {failed_phase}".strip()
    return MatrixResult(
        mode.name,
        success,
        False,
        timed_out,
        failed_phase,
        exit_code,
        str(output_path),
        str(capture_dir),
        command,
        error,
    )


def load_cross_repo_artifact(path: Path) -> dict[str, object]:
    artifact = read_json(path)
    if not artifact:
        raise ValueError(f"Malformed graphics audit artifact: {path}")
    required = ("schema", "repository", "benchmark_fingerprint", "implementation_attribution", "metrics", "validation")
    missing = [key for key in required if key not in artifact]
    if artifact.get("schema") != SCHEMA or missing:
        raise ValueError(f"Incomplete graphics audit artifact: {path}; missing={missing}")
    return artifact


def load_baseline_index(path: Path | None) -> dict[str, object]:
    if path is None or not path.is_file():
        return {}
    data = read_json(path)
    return data if data else {}


def aggregate_matrix(artifact_paths: Iterable[Path]) -> dict[str, object]:
    artifacts = [load_cross_repo_artifact(path) for path in artifact_paths]
    rows: list[dict[str, object]] = []
    references: dict[tuple[str, str], dict[str, object]] = {}
    rejections: list[dict[str, object]] = []
    for artifact in artifacts:
        tool = str(artifact.get("tool", "unknown"))
        mode = artifact.get("mode") if isinstance(artifact.get("mode"), dict) else {}
        shaders = str(mode.get("shaders", "unknown"))
        reference_key = (tool, shaders)
        reference = references.get(reference_key)
        if reference is None:
            references[reference_key] = artifact
        else:
            comparison = compare_workloads(reference, artifact)
            if not comparison["comparable"]:
                rejections.append({"mode": artifact.get("mode", {}).get("name") if isinstance(artifact.get("mode"), dict) else "unknown", **comparison})
        frame = artifact["metrics"]["frame_time_ms"] if isinstance(artifact.get("metrics"), dict) else {}
        fps = artifact["metrics"]["fps"] if isinstance(artifact.get("metrics"), dict) else {}
        validation = artifact.get("validation") if isinstance(artifact.get("validation"), dict) else {}
        rows.append(
            {
                "mode": artifact.get("mode", {}).get("name") if isinstance(artifact.get("mode"), dict) else "unknown",
                "tool": artifact.get("tool"),
                "repo": artifact.get("repository", {}).get("target") if isinstance(artifact.get("repository"), dict) else "unknown",
                "attribution": artifact.get("implementation_attribution"),
                "median_ms": frame.get("median") if isinstance(frame, dict) else None,
                "p95_ms": frame.get("p95") if isinstance(frame, dict) else None,
                "p99_ms": frame.get("p99") if isinstance(frame, dict) else None,
                "worst_ms": frame.get("worst") if isinstance(frame, dict) else None,
                "fps_median": fps.get("median") if isinstance(fps, dict) else None,
                "strict_gl": validation.get("strict_gl_error_scan_passed"),
                "vulkan_validation": validation.get("vulkan_validation_passed"),
                "crash_free": validation.get("crash_free"),
            }
        )
    return {
        "schema": "mattmc-cross-graphics-audit-matrix-v1",
        "created_at": utc_now(),
        "row_count": len(rows),
        "comparison_rejections": rejections,
        "rows": rows,
    }


def repeatability_report(artifact_paths: Iterable[Path], tolerance: float) -> dict[str, object]:
    groups: dict[tuple[str, str], list[float]] = {}
    for path in artifact_paths:
        artifact = read_json(path)
        if not artifact or artifact.get("tool") != "gameplay":
            continue
        mode = artifact.get("mode") if isinstance(artifact.get("mode"), dict) else {}
        metrics = artifact.get("metrics") if isinstance(artifact.get("metrics"), dict) else {}
        frame = metrics.get("frame_time_ms") if isinstance(metrics.get("frame_time_ms"), dict) else {}
        median = parse_number(frame.get("median")) if isinstance(frame, dict) else None
        if median is not None:
            groups.setdefault((str(mode.get("name", "unknown")), str(artifact.get("tool"))), []).append(median)
    rows: list[dict[str, object]] = []
    passed = True
    for (mode, tool), medians in sorted(groups.items()):
        if len(medians) < 2:
            rows.append({"mode": mode, "tool": tool, "run_count": len(medians), "passed": True, "median_ms": medians})
            continue
        center = statistics.median(medians)
        relative_span = 0.0 if center <= 0 else (max(medians) - min(medians)) / center
        row_passed = relative_span <= tolerance
        passed = passed and row_passed
        rows.append(
            {
                "mode": mode,
                "tool": tool,
                "run_count": len(medians),
                "median_ms": medians,
                "relative_span": relative_span,
                "tolerance": tolerance,
                "passed": row_passed,
            }
        )
    return {"passed": passed, "rows": rows}


def relative_span(values: list[float]) -> float | None:
    if len(values) < 2:
        return None
    center = statistics.median(values)
    if center <= 0:
        return 0.0
    return (max(values) - min(values)) / center


def coefficient_of_variation(values: list[float]) -> float | None:
    if len(values) < 2:
        return None
    mean = statistics.mean(values)
    if mean <= 0:
        return 0.0
    return statistics.pstdev(values) / mean


def variance_report(artifact_paths: Iterable[Path], tolerance: float) -> dict[str, object]:
    groups: dict[tuple[str, str], list[dict[str, object]]] = {}
    for path in artifact_paths:
        artifact = read_json(path)
        if not artifact or artifact.get("tool") != "gameplay":
            continue
        mode = artifact.get("mode") if isinstance(artifact.get("mode"), dict) else {}
        metrics = artifact.get("metrics") if isinstance(artifact.get("metrics"), dict) else {}
        groups.setdefault((str(mode.get("name", "unknown")), str(artifact.get("implementation_attribution"))), []).append(metrics)
    rows: list[dict[str, object]] = []
    passed = True
    for (mode, attribution), metrics_group in sorted(groups.items()):
        frame_medians = [
            value
            for metrics in metrics_group
            for value in [parse_number(metrics.get("frame_time_ms", {}).get("median")) if isinstance(metrics.get("frame_time_ms"), dict) else None]
            if value is not None
        ]
        phase_rows: dict[str, dict[str, object]] = {}
        phase_names = sorted(
            {
                phase
                for metrics in metrics_group
                if isinstance(metrics.get("cpu_phase_timings"), dict)
                for phase in metrics["cpu_phase_timings"]
            }
        )
        for phase in phase_names:
            values = [
                value
                for metrics in metrics_group
                for value in [
                    parse_number(metrics.get("cpu_phase_timings", {}).get(phase, {}).get("median"))
                    if isinstance(metrics.get("cpu_phase_timings"), dict)
                    and isinstance(metrics.get("cpu_phase_timings", {}).get(phase), dict)
                    else None
                ]
                if value is not None
            ]
            if values:
                phase_rows[phase] = {
                    "median_ms": statistics.median(values),
                    "relative_span": relative_span(values),
                    "coefficient_of_variation": coefficient_of_variation(values),
                    "samples": values,
                }
        span = relative_span(frame_medians)
        row_passed = span is None or span <= tolerance
        passed = passed and row_passed
        rows.append(
            {
                "mode": mode,
                "attribution": attribution,
                "run_count": len(frame_medians),
                "frame_median_ms": frame_medians,
                "relative_span": span,
                "coefficient_of_variation": coefficient_of_variation(frame_medians),
                "tolerance": tolerance,
                "passed": row_passed,
                "phases": phase_rows,
            }
        )
    return {"passed": passed, "rows": rows}


def instrumentation_overhead_report(control_paths: Iterable[Path], diagnostic_paths: Iterable[Path], tolerance: float) -> dict[str, object]:
    def medians(paths: Iterable[Path]) -> dict[str, float]:
        grouped: dict[str, list[float]] = {}
        for path in paths:
            artifact = read_json(path)
            if not artifact or artifact.get("tool") != "gameplay":
                continue
            mode = artifact.get("mode") if isinstance(artifact.get("mode"), dict) else {}
            metrics = artifact.get("metrics") if isinstance(artifact.get("metrics"), dict) else {}
            frame = metrics.get("frame_time_ms") if isinstance(metrics.get("frame_time_ms"), dict) else {}
            value = parse_number(frame.get("median")) if isinstance(frame, dict) else None
            if value is not None:
                grouped.setdefault(str(mode.get("name", "unknown")), []).append(value)
        return {mode: statistics.median(values) for mode, values in grouped.items() if values}

    control = medians(control_paths)
    diagnostic = medians(diagnostic_paths)
    rows: list[dict[str, object]] = []
    passed = True
    for mode in sorted(set(control) | set(diagnostic)):
        base = control.get(mode)
        diag = diagnostic.get(mode)
        overhead = None if base is None or diag is None or base <= 0 else (diag - base) / base
        row_passed = overhead is not None and overhead <= tolerance
        passed = passed and row_passed
        rows.append(
            {
                "mode": mode,
                "control_median_ms": base,
                "diagnostic_median_ms": diag,
                "relative_overhead": overhead,
                "tolerance": tolerance,
                "passed": row_passed,
            }
        )
    return {"passed": passed, "rows": rows}


def selected_modes(args: argparse.Namespace) -> list[ModeSpec]:
    by_name = {mode.name: mode for mode in MATRIX_MODES}
    if args.mode:
        return [by_name[name] for name in args.mode]
    return list(MATRIX_MODES)


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    incoming = list(argv) if argv is not None else sys.argv[1:]
    if not incoming or incoming[0] not in TOOL_KINDS:
        incoming.insert(0, "matrix")
    provided_options = {
        token.split("=", 1)[0]
        for token in incoming
        if token.startswith("--")
    }
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="tool", required=True)

    def add_common(subparser: argparse.ArgumentParser) -> None:
        subparser.add_argument("--profile", choices=RUNTIME_PROFILE_NAMES, default="smoke")
        subparser.add_argument("--frozen-repo")
        subparser.add_argument("--artifact-dir", type=Path)
        subparser.add_argument("--mode", action="append", choices=[mode.name for mode in MATRIX_MODES])
        subparser.add_argument("--workload-profile", choices=WORKLOAD_PROFILES, default="correctness")
        subparser.add_argument("--world", default=os.environ.get("MATTMC_CAPTURE_WORLD", "Origin"))
        subparser.add_argument("--client-args", default=os.environ.get("CLIENT_ARGS", ""))
        subparser.add_argument("--timeout-seconds", type=int)
        subparser.add_argument("--startup-timeout-seconds", type=int)
        subparser.add_argument("--readiness-timeout-seconds", type=int)
        subparser.add_argument("--warmup-timeout-seconds", type=int)
        subparser.add_argument("--measurement-timeout-seconds", type=int)
        subparser.add_argument("--shutdown-timeout-seconds", type=int)
        subparser.add_argument("--cleanup-timeout-seconds", type=int)
        subparser.add_argument("--max-secs", type=int)
        subparser.add_argument("--dump-secs", type=int)
        subparser.add_argument("--client-rss-limit-mb", type=int, default=int(os.environ.get("CLIENT_RSS_LIMIT_MB", "12288")))
        subparser.add_argument("--validation", choices=("off", "standard", "routine", "deep"), default="off")
        subparser.add_argument("--validation-fail-severity", choices=("info", "warning", "error"), default="warning")
        subparser.add_argument("--renderdoc-capture", action="store_true", help="Run as a RenderDoc correctness capture; never performance-comparable.")
        subparser.add_argument("--renderdoc-frame", type=int, default=8, help="Deterministic frame number requested for RenderDoc capture.")
        subparser.add_argument("--tracy-capture", action="store_true", help="Run as a Tracy timeline capture; never performance-comparable.")
        subparser.add_argument("--tracy-duration-seconds", type=int, default=20)
        subparser.add_argument("--tracy-max-size-mb", type=int, default=256)
        subparser.add_argument("--warmup-frames", type=int)
        subparser.add_argument("--measure-frames", type=int)
        subparser.add_argument("--settle-frames", type=int)
        subparser.add_argument("--max-settle-frames", type=int)
        subparser.add_argument("--subsystem-iterations", type=int)
        subparser.add_argument("--repetitions", type=int, default=1)
        subparser.add_argument("--repeatability-tolerance", type=float, default=0.25)
        subparser.add_argument("--diagnostic", action="store_true", help="Enable gated graphics audit diagnostics.")
        subparser.add_argument("--dry-run", action="store_true")

    for tool in TOOL_KINDS:
        add_common(subparsers.add_parser(tool))
    args = parser.parse_args(incoming)
    profile = RUNTIME_PROFILES[args.profile]
    profile_defaults = {
        "--timeout-seconds": ("timeout_seconds", profile.hard_timeout_seconds),
        "--startup-timeout-seconds": ("startup_timeout_seconds", profile.startup_timeout_seconds),
        "--readiness-timeout-seconds": ("readiness_timeout_seconds", profile.readiness_timeout_seconds),
        "--warmup-timeout-seconds": ("warmup_timeout_seconds", profile.warmup_timeout_seconds),
        "--measurement-timeout-seconds": ("measurement_timeout_seconds", profile.measurement_timeout_seconds),
        "--shutdown-timeout-seconds": ("shutdown_timeout_seconds", profile.shutdown_timeout_seconds),
        "--cleanup-timeout-seconds": ("cleanup_timeout_seconds", profile.cleanup_timeout_seconds),
        "--max-secs": ("max_secs", profile.max_secs),
        "--dump-secs": ("dump_secs", profile.dump_secs),
        "--warmup-frames": ("warmup_frames", profile.warmup_frames),
        "--measure-frames": ("measure_frames", profile.measure_frames),
        "--settle-frames": ("settle_frames", profile.settle_frames),
        "--max-settle-frames": ("max_settle_frames", profile.max_settle_frames),
        "--subsystem-iterations": ("subsystem_iterations", profile.subsystem_iterations),
    }
    for option, (field, default_value) in profile_defaults.items():
        if option not in provided_options or getattr(args, field) is None:
            setattr(args, field, default_value)
    if args.timeout_seconds > profile.hard_timeout_seconds:
        raise SystemExit(
            f"{args.profile} profile hard limit is {profile.hard_timeout_seconds}s per mode; "
            f"requested --timeout-seconds={args.timeout_seconds}"
        )
    if args.max_secs > args.timeout_seconds:
        raise SystemExit("--max-secs may not exceed --timeout-seconds")
    phase_total = (
        args.startup_timeout_seconds
        + args.readiness_timeout_seconds
        + args.warmup_timeout_seconds
        + args.measurement_timeout_seconds
        + args.shutdown_timeout_seconds
    )
    if phase_total > args.timeout_seconds + args.cleanup_timeout_seconds:
        raise SystemExit("phase timeout budget exceeds the selected per-mode limit")
    if args.timeout_seconds < 0 or args.max_secs < 0 or args.dump_secs < 0:
        raise SystemExit("timeout/max/dump seconds must be non-negative")
    for field in (
        "startup_timeout_seconds",
        "readiness_timeout_seconds",
        "warmup_timeout_seconds",
        "measurement_timeout_seconds",
        "shutdown_timeout_seconds",
        "cleanup_timeout_seconds",
    ):
        if getattr(args, field) <= 0:
            raise SystemExit(f"{field.replace('_', '-')} must be positive")
    if args.client_rss_limit_mb <= 0:
        raise SystemExit("client RSS limit must be positive")
    run_type_for_args(args)
    if args.renderdoc_capture and args.tool not in {"capture", "matrix"}:
        raise SystemExit("RenderDoc capture is a correctness/audit mode; use DevUtils/Audit/Capture.py or Matrix.py")
    if args.tracy_duration_seconds <= 0 or args.tracy_max_size_mb <= 0:
        raise SystemExit("Tracy duration and max size must be positive")
    if args.warmup_frames < 0 or args.measure_frames <= 0:
        raise SystemExit("warmup frames must be non-negative and measure frames must be positive")
    if args.settle_frames < 0 or args.max_settle_frames <= 0 or args.subsystem_iterations <= 0 or args.repetitions <= 0:
        raise SystemExit("settle/max-settle/subsystem iterations/repetitions must be positive where applicable")
    return args


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    targets = select_targets(args)
    artifact_root = (
        args.artifact_dir.resolve()
        if args.artifact_dir
        else repo_root() / "logs" / "graphics-audit" / args.tool / timestamp()
    )
    artifact_root.mkdir(parents=True, exist_ok=True)
    results: list[MatrixResult] = []
    tools_to_run = ("gameplay", "capture", "subsystem") if args.tool == "matrix" else (args.tool,)
    for mode in selected_modes(args):
        for tool_kind in tools_to_run:
            for repetition in range(1, args.repetitions + 1):
                result = run_mode(targets[mode.target], mode, artifact_root, args, tool_kind, repetition)
                if args.tool == "matrix":
                    result.mode = f"{mode.name}/{tool_kind}/run-{repetition:02d}"
                elif args.repetitions > 1:
                    result.mode = f"{mode.name}/run-{repetition:02d}"
                results.append(result)

    artifact_paths = [Path(result.artifact_path) for result in results if Path(result.artifact_path).is_file()]
    aggregate = aggregate_matrix(artifact_paths) if artifact_paths else None
    repeatability = repeatability_report(artifact_paths, args.repeatability_tolerance)
    aggregate_path = artifact_root / "graphics_audit_matrix.json"
    if aggregate:
        aggregate_path.write_text(json.dumps(aggregate, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    manifest = {
        "schema": "mattmc-cross-graphics-audit-manifest-v1",
        "created_at": utc_now(),
        "artifact_dir": str(artifact_root),
        "runtime_profile": runtime_profile_dict(args),
        "workload_profile": args.workload_profile,
        "tool": args.tool,
        "invoked_tools": list(tools_to_run),
        "diagnostic": args.diagnostic,
        "repeatability": repeatability,
        "success": all(result.success for result in results) and repeatability["passed"],
        "aggregate": str(aggregate_path) if aggregate else None,
        "results": [asdict(result) for result in results],
    }
    manifest_path = artifact_root / MANIFEST_NAME
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Wrote graphics audit manifest: {manifest_path}")
    if aggregate:
        print(f"Wrote graphics audit matrix: {aggregate_path}")
    for result in results:
        status = "ok" if result.success else "FAILED"
        phase = f", phase={result.timed_out_phase}" if result.timed_out_phase else ""
        print(f"{result.mode}: {status}{phase} ({result.artifact_path})")
    return 0 if manifest["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
