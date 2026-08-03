#!/usr/bin/env python3
"""Cross-repository graphics audit harness for staged Vulkanic migration."""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import math
import os
import platform
import re
import shlex
import shutil
import signal
import statistics
import subprocess
import sys
import threading
import time
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence

import artifact_retention


SCHEMA = "mattmc-cross-graphics-audit-v2"
ARTIFACT_NAME = "graphics_audit_artifact.json"
MANIFEST_NAME = "graphics_audit_manifest.json"
BASELINE_INDEX_NAME = "graphics_audit_baselines.json"
DEFAULT_TIMEOUT_SECONDS = 900
NORMALIZED_LOG_TAIL_BYTES = 2_000_000
CURRENT_REPO_ROOT = Path(__file__).resolve().parents[2]
DEVUTILS_CACHE_ROOT = CURRENT_REPO_ROOT / "DevUtils" / ".cache"
WORKLOAD_PROFILES = ("correctness", "moving-camera", "settled-static", "gameplay")
TOOL_KINDS = ("gameplay", "subsystem", "capture", "matrix")
RUNTIME_PROFILE_NAMES = ("smoke", "standard", "performance", "extended")
WORLD_MATERIAL_TEXTURE_STONE = 0x21DF896F
WORLD_MATERIAL_TEXTURE_DIRT = 0x0B0BBD25
WORLD_MATERIAL_TEXTURE_OAK_LEAVES = 0x72321EC7
WORLD_MATERIAL_TEXTURE_DEEPSLATE = 0x715D8D65
WORLD_MATERIAL_TEXTURE_WHITE_WOOL = 0x2253A2EF
WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_BARRIER = 0x447D596A
WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_IDS = [
    0x665DA7AA,
    0x50E88E0F,
    0x079E2B74,
    0x4A7C2B71,
    0x35E90AE6,
    0x2F21FECB,
    0x2A27ABF0,
    0x0EA4C92D,
    0x4473CCE2,
    0x0AB551C7,
    0x7A250241,
    0x1F439384,
    0x4BAB8F5F,
    0x431688FA,
    0x0B2BDBBD,
    0x019476C0,
]
WORLD_MATERIAL_OPAQUE_TEXTURED = 0x6A2FD335
WORLD_MATERIAL_CUTOUT_TEXTURED = 0x129B1B90
WORLD_MATERIAL_BLOCK_MARKER_CUTOUT = 0x224A8659
WORLD_PROFILE_NAMES = ("migration-gate", "stress-diagnostic")
PISTON_SHELL_SCAN_BUDGET_NANOS = 5_000_000
FALLING_BLOCK_MIN_CAPTURE_FRAMES = 4
EXPECTED_SHADER_PACK = "ComplementaryHungLoIfied.zip"


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
class TracyListener:
    port: int
    pid: int | None = None
    process: str | None = None
    source: str = "proc"


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


@dataclass(frozen=True)
class WorldProfile:
    name: str
    world: str
    role: str
    migration_gate_blocking: bool
    description: str
    deterministic_ready_families: str
    deterministic_ready_frames: int
    deterministic_ready_max_wait_frames: int
    diagnostic_jvm_args: tuple[str, ...] = ()


RUNTIME_PROFILES = {
    "smoke": RuntimeProfile("smoke", 60, 15, 15, 10, 20, 5, 5, 50, 15, 20, 60, 60, 600, 20),
    "standard": RuntimeProfile("standard", 180, 30, 40, 25, 75, 10, 10, 160, 45, 120, 300, 240, 1800, 120),
    "performance": RuntimeProfile("performance", 300, 30, 40, 80, 120, 15, 10, 270, 60, 900, 900, 120, 1800, 120),
    "extended": RuntimeProfile("extended", 300, 55, 65, 40, 125, 15, 15, 270, 60, 240, 600, 360, 3000, 200),
}
PROFILE_RANK = {name: index for index, name in enumerate(RUNTIME_PROFILE_NAMES)}

WORLD_PROFILES = {
    "migration-gate": WorldProfile(
        "migration-gate",
        "Origin",
        "routine-migration-gate",
        True,
        "Small stable pre-generated world for every producer migration.",
        "sodium-terrain",
        0,
        180,
    ),
    "stress-diagnostic": WorldProfile(
        "stress-diagnostic",
        "Origin Prime City 3",
        "affected-world-stress",
        False,
        "Affected-world stress profile preserving DH/world-generation pressure.",
        "sodium-terrain,distant-horizons",
        8,
        900,
        ("-Dmattmc.dev.directTriggerDiagnostics=true",),
    ),
}


MATRIX_MODES = (
    ModeSpec("current-opengl-shaders-off", "current", "opengl", "off", "java-opengl", False),
    ModeSpec("current-opengl-shaders-on", "current", "opengl", "on", "java-opengl", False),
    ModeSpec("current-java-vulkan-shaders-off", "current", "vulkan", "off", "java-vulkan", True),
    ModeSpec("current-java-vulkan-shaders-on", "current", "vulkan", "on", "java-vulkan", True),
    ModeSpec("current-rust-opengl-shaders-off", "current", "rust-opengl", "off", "rust-opengl", False),
    ModeSpec("current-rust-opengl-shaders-on", "current", "rust-opengl", "on", "rust-opengl", False),
    ModeSpec("current-rust-vulkan-shaders-off", "current", "rust-vulkan", "off", "rust-vulkan", True),
    ModeSpec("current-rust-vulkan-shaders-on", "current", "rust-vulkan", "on", "rust-vulkan", True),
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


def local_tracy_csvexport_path() -> str | None:
    candidate = DEVUTILS_CACHE_ROOT / "tools" / "tracy" / "bin" / "tracy-csvexport"
    if candidate.is_file() and os.access(candidate, os.X_OK):
        return str(candidate)
    return shutil.which("tracy-csvexport")


def local_renderdoccmd_path() -> str | None:
    candidate = DEVUTILS_CACHE_ROOT / "tools" / "renderdoc" / "bin" / "renderdoccmd"
    if candidate.is_file() and os.access(candidate, os.X_OK):
        return str(candidate)
    return shutil.which("renderdoccmd")


def local_renderdoc_library_path() -> str | None:
    root = DEVUTILS_CACHE_ROOT / "tools" / "renderdoc"
    candidates = sorted(root.glob("extracted/**/librenderdoc.so"))
    for candidate in candidates:
        if candidate.is_file():
            return str(candidate)
    return None


def renderdoc_python_module_available() -> bool:
    try:
        result = subprocess.run(
            [sys.executable, "-c", "import renderdoc"],
            text=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=5,
            **popen_kwargs(),
        )
        return result.returncode == 0
    except Exception:
        return False


def local_renderdoc_layer_manifest_path() -> Path | None:
    root = DEVUTILS_CACHE_ROOT / "tools" / "renderdoc"
    candidates = sorted(root.glob("extracted/**/renderdoc_capture.json"))
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    return None


def listening_tcp_ports(start_port: int, end_port: int) -> list[int]:
    discovered = discover_tracy_listeners(start_port, end_port)
    if discovered:
        return sorted({listener.port for listener in discovered})
    ports = {f"{port:04X}": port for port in range(start_port, end_port + 1)}
    found: set[int] = set()
    for table in (Path("/proc/net/tcp"), Path("/proc/net/tcp6")):
        try:
            lines = table.read_text(encoding="utf-8").splitlines()[1:]
        except OSError:
            continue
        for line in lines:
            columns = line.split()
            if len(columns) < 4:
                continue
            local_address = columns[1]
            state = columns[3]
            if state != "0A":
                continue
            port_hex = local_address.rsplit(":", 1)[-1].upper()
            if port_hex in ports:
                found.add(ports[port_hex])
    return sorted(found)


def parse_ss_tracy_listeners(output: str, start_port: int, end_port: int) -> list[TracyListener]:
    listeners: list[TracyListener] = []
    seen: set[int] = set()
    for line in output.splitlines():
        tokens = line.split()
        local = next((token for token in tokens if re.search(r":\d+$", token) or re.search(r"\]:\d+$", token)), "")
        match = re.search(r":(\d+)$", local)
        if not match:
            continue
        port = int(match.group(1))
        if port < start_port or port > end_port or port in seen:
            continue
        process_match = re.search(r'\("([^"]+)"', line)
        pid_match = re.search(r"pid=(\d+)", line)
        listeners.append(
            TracyListener(
                port=port,
                pid=int(pid_match.group(1)) if pid_match else None,
                process=process_match.group(1) if process_match else None,
                source="ss",
            )
        )
        seen.add(port)
    return sorted(listeners, key=lambda listener: listener.port)


def discover_tracy_listeners(start_port: int = 8086, end_port: int = 8110) -> list[TracyListener]:
    listeners: dict[int, TracyListener] = {}
    ss = shutil.which("ss")
    if ss:
        try:
            result = subprocess.run(
                [ss, "-H", "-ltnp"],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                timeout=2,
                check=False,
            )
            if result.returncode == 0:
                listeners.update({listener.port: listener for listener in parse_ss_tracy_listeners(result.stdout, start_port, end_port)})
        except Exception:
            pass
    ports = {f"{port:04X}": port for port in range(start_port, end_port + 1)}
    for table in (Path("/proc/net/tcp"), Path("/proc/net/tcp6")):
        try:
            lines = table.read_text(encoding="utf-8").splitlines()[1:]
        except OSError:
            continue
        for line in lines:
            columns = line.split()
            if len(columns) < 4 or columns[3] != "0A":
                continue
            port_hex = columns[1].rsplit(":", 1)[-1].upper()
            port = ports.get(port_hex)
            if port is not None:
                listeners.setdefault(port, TracyListener(port=port))
    return [listeners[port] for port in sorted(listeners)]


RUST_OPENGL_CONFORMANCE_TEST = (
    "render::vulkanic::backends::opengl::conformance::"
    "isolated_opengl_conformance_renders_indexed_textured_draw"
)
RUST_SHADER_GBUFFER_SCENE_TEST = (
    "render::vulkanic::world_primitive_frontend::tests::"
    "rust_owned_shader_pack_mesh_scene_matches_opengl_and_vulkan_when_available"
)


def rust_test_binary_candidates(root: Path) -> list[Path]:
    deps = root / "src" / "main" / "rust" / "target" / "debug" / "deps"
    if not deps.is_dir():
        return []
    candidates = [
        path
        for path in deps.glob("mattmc_rust-*")
        if path.is_file() and os.access(path, os.X_OK) and path.suffix not in {".d", ".rlib", ".rmeta"}
    ]
    return sorted(candidates, key=lambda path: path.stat().st_mtime, reverse=True)


def resolve_rust_test_binary(root: Path, *, build_if_missing: bool = True) -> Path:
    candidates = rust_test_binary_candidates(root)
    if candidates:
        return candidates[0]
    if build_if_missing:
        result = subprocess.run(
            [
                "cargo",
                "test",
                "--manifest-path",
                str(root / "src" / "main" / "rust" / "Cargo.toml"),
                RUST_OPENGL_CONFORMANCE_TEST,
                "--no-run",
            ],
            cwd=root,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=120,
            check=False,
        )
        if result.returncode == 0:
            candidates = rust_test_binary_candidates(root)
            if candidates:
                return candidates[0]
        raise RuntimeError(f"failed to build Rust test binary for RenderDoc:\n{result.stdout}")
    raise FileNotFoundError("compiled mattmc_rust test binary was not found")


def build_rust_shader_gbuffer_test_binary(root: Path) -> Path:
    result = subprocess.run(
        [
            "cargo",
            "test",
            "--manifest-path",
            str(root / "src" / "main" / "rust" / "Cargo.toml"),
            RUST_SHADER_GBUFFER_SCENE_TEST,
            "--no-run",
        ],
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=180,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(f"failed to build Rust shader G-buffer test binary:\n{result.stdout}")
    return resolve_rust_test_binary(root, build_if_missing=False)


def build_rust_opengl_renderdoc_command(root: Path, capture_template: Path) -> tuple[list[str], dict[str, str]]:
    renderdoc = local_renderdoccmd_path()
    if not renderdoc:
        raise FileNotFoundError("renderdoccmd is not installed")
    binary = resolve_rust_test_binary(root)
    env = os.environ.copy()
    env["MATTMC_RENDERDOC_CAPTURE"] = "1"
    env["MATTMC_OPENGL_STRICT"] = "1"
    env["MATTMC_GRAPHICS_RUN_TYPE"] = "renderdoc-capture"
    return (
        [
            renderdoc,
            "capture",
            "-w",
            "-d",
            str(root),
            "-c",
            str(capture_template),
            str(binary),
            RUST_OPENGL_CONFORMANCE_TEST,
            "--nocapture",
        ],
        env,
    )


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


def shaderc_compiler_fingerprint() -> dict[str, object]:
    cargo_toml = CURRENT_REPO_ROOT / "src" / "main" / "rust" / "Cargo.toml"
    cargo_lock = CURRENT_REPO_ROOT / "src" / "main" / "rust" / "Cargo.lock"
    source = CURRENT_REPO_ROOT / "src" / "main" / "rust" / "render" / "vulkanic" / "backends" / "vulkan" / "shaderc_spirv_compiler.rs"
    version = None
    features: list[str] = []
    if cargo_toml.is_file():
        text = cargo_toml.read_text(encoding="utf-8", errors="replace")
        match = re.search(r"shaderc\s*=\s*\{[^}]*version\s*=\s*\"([^\"]+)\"", text)
        if match:
            version = match.group(1)
        features_match = re.search(r"shaderc\s*=\s*\{[^}]*features\s*=\s*\[([^\]]*)\]", text)
        if features_match:
            features = [part.strip().strip("\"'") for part in features_match.group(1).split(",") if part.strip()]
    if version is None and cargo_lock.is_file():
        text = cargo_lock.read_text(encoding="utf-8", errors="replace")
        match = re.search(r'name = "shaderc"\s+version = "([^"]+)"', text)
        if match:
            version = match.group(1)
    return {
        "name": "mattmc_rust:shaderc",
        "crate": "shaderc",
        "version": version,
        "features": features,
        "source": str(source) if source.is_file() else None,
        "cargo_toml": str(cargo_toml) if cargo_toml.is_file() else None,
        "cargo_lock": str(cargo_lock) if cargo_lock.is_file() else None,
    }


def tool_fingerprint() -> dict[str, object]:
    validation_manifest = find_validation_layer_manifest_path()
    renderdoc_path = local_renderdoccmd_path()
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
        "shader_compiler": shaderc_compiler_fingerprint(),
        "khronos_validation": {
            "available": validation_manifest is not None,
            "manifest": str(validation_manifest) if validation_manifest else None,
        },
        "renderdoc_vulkan_layer": {
            "local_manifest": str(local_renderdoc_layer_manifest_path()) if local_renderdoc_layer_manifest_path() else None,
        },
        "renderdoccmd": executable_version(renderdoc_path) if renderdoc_path else executable_version("renderdoccmd"),
        "tracy_capture": tracy,
        "tracy_csvexport": executable_version(local_tracy_csvexport_path()) if local_tracy_csvexport_path() else executable_version("tracy-csvexport"),
        "tracy_profiler": executable_version("tracy-profiler"),
    }


def run_type_for_args(args: argparse.Namespace) -> str:
    return run_type_for_effective_options(
        getattr(args, "validation", "off"),
        renderdoc=getattr(args, "renderdoc_capture", False),
        tracy=getattr(args, "tracy_capture", False),
    )


def run_type_for_effective_options(validation: str, *, renderdoc: bool, tracy: bool) -> str:
    enabled = []
    if validation != "off":
        enabled.append("vulkan-validation")
    if renderdoc:
        enabled.append("renderdoc-capture")
    if tracy:
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
VALIDATION_OBJECT_RE = re.compile(
    r"(?:Object\s*\d*:\s*|object\s+|handle\s*=\s*)"
    r"(?P<handle>0x[0-9a-fA-F]+)"
    r"(?:[^,\n]*,\s*(?:type\s*=\s*)?(?P<type>VK_OBJECT_TYPE_[A-Z0-9_]+|[A-Za-z][A-Za-z0-9_]*))?"
    r"(?:[^,\n]*,\s*(?:name|debug name)\s*=\s*(?P<name>[^,\]\n]+))?",
    re.IGNORECASE,
)
VALIDATION_HANDLE_TYPE_RE = re.compile(
    r"handle\s*=\s*(?P<handle>0x[0-9a-fA-F]+)"
    r"\s*,\s*type\s*=\s*(?P<type>VK_OBJECT_TYPE_[A-Z0-9_]+|[A-Za-z][A-Za-z0-9_]*)"
    r"(?:\s*,\s*(?:name|debug name)\s*=\s*(?P<name>[^,\]\n]+))?",
    re.IGNORECASE,
)
VALIDATION_INDEXED_OBJECT_RE = re.compile(
    r"^\s*\[\d+\]\s+(?P<type>Vk[A-Za-z0-9_]+)\s+(?P<handle>0x[0-9a-fA-F]+)"
    r"(?:\s+(?:name|debug name)\s*=\s*(?P<name>[^,\]\n]+))?",
    re.MULTILINE,
)


def validation_message_blocks(text: str) -> list[str]:
    blocks: list[list[str]] = []
    current: list[str] = []
    trigger = re.compile(
        r"^\s*(?:Validation (?:Error|Warning)|(?:ERROR|WARNING|INFO|VERBOSE)\s+(?:VALIDATION|PERFORMANCE|GENERAL)|"
        r"\[Vulkan Loader\]|UNASSIGNED-)",
        re.IGNORECASE,
    )
    for line in text.splitlines():
        if trigger.search(line):
            if current:
                blocks.append(current)
            if re.search(r"^\s*\[Vulkan Loader\]", line, re.IGNORECASE):
                blocks.append([line])
                current = []
            else:
                current = [line]
        elif current and not line.strip():
            blocks.append(current)
            current = []
        elif current:
            current.append(line)
    if current:
        blocks.append(current)
    return ["\n".join(block).strip() for block in blocks if block]


def parse_validation_objects(message: str) -> list[dict[str, object]]:
    objects: dict[str, dict[str, object]] = {}
    for match in VALIDATION_HANDLE_TYPE_RE.finditer(message):
        handle = match.group("handle")
        entry = objects.setdefault(
            handle.lower(),
            {
                "handle": handle,
                "type": None,
                "debug_name": None,
            },
        )
        entry["type"] = match.group("type")
        debug_name = match.group("name")
        if debug_name:
            entry["debug_name"] = debug_name.strip().strip("'\"")
    for match in VALIDATION_OBJECT_RE.finditer(message):
        handle = match.group("handle")
        if not handle:
            continue
        entry = objects.setdefault(
            handle.lower(),
            {
                "handle": handle,
                "type": None,
                "debug_name": None,
            },
        )
        object_type = match.group("type")
        debug_name = match.group("name")
        if object_type:
            entry["type"] = object_type
        if debug_name:
            entry["debug_name"] = debug_name.strip().strip("'\"")
    for match in VALIDATION_INDEXED_OBJECT_RE.finditer(message):
        handle = match.group("handle")
        entry = objects.setdefault(
            handle.lower(),
            {
                "handle": handle,
                "type": None,
                "debug_name": None,
            },
        )
        entry["type"] = match.group("type")
        debug_name = match.group("name")
        if debug_name:
            entry["debug_name"] = debug_name.strip().strip("'\"")
    return list(objects.values())


def validation_finding_category(message: str, vuid: str, message_type: str) -> str:
    lowered = message.lower()
    if "device lost" in lowered or "vk_error_device_lost" in lowered:
        return "device_loss"
    if "[vulkan loader]" in lowered or "env var 'vk_instance_layers'" in lowered or "insert instance layer" in lowered:
        return "loader_environment_notice"
    if "vk_layer_khronos_validation" in lowered and vuid == "UNASSIGNED":
        return "validation_configuration_notice"
    if "gpu-assisted" in lowered or "gpuav" in lowered:
        return "gpu_assisted"
    if "sync" in lowered or "synchronization" in lowered:
        return "synchronization"
    if vuid != "UNASSIGNED" or message_type in {"validation", "performance"}:
        return "concrete_api_vuid"
    return "validation_configuration_notice"


def parse_validation_findings(text: str) -> list[dict[str, object]]:
    findings: dict[tuple[object, ...], dict[str, object]] = {}
    occurrence = 0
    for message in validation_message_blocks(text):
        match = VALIDATION_LINE_RE.search(message)
        vuid_match = re.search(r"VUID-[A-Za-z0-9_./:-]+", message)
        severity_match = re.search(r"\b(ERROR|WARNING|INFO|VERBOSE)\b", message, re.IGNORECASE)
        type_match = re.search(r"\b(VALIDATION|PERFORMANCE|GENERAL)\b", message, re.IGNORECASE)
        message_id_match = re.search(
            r"(?:MessageID|message id|msgNum|Message Id Name|MessageIDName)\s*[=: ]\s*([A-Za-z0-9_./:-]+|0x[0-9a-fA-F]+)",
            message,
            re.IGNORECASE,
        )
        objects = parse_validation_objects(message)
        semantic_match = re.search(r"(?:GAL|callsite|operation|semantic_operation)[=: ]+([A-Za-z0-9_./:-]+)", message)
        vuid = vuid_match.group(0) if vuid_match else "UNASSIGNED"
        message_type = type_match.group(1).lower() if type_match else (match.group("type").lower() if match and match.group("type") else "unknown")
        normalized_message = re.sub(r"0x[0-9a-fA-F]+", "0x*", message.strip())
        occurrence += 1
        key = (
            vuid,
            message_id_match.group(1) if message_id_match else None,
            hashlib.sha256(normalized_message.encode("utf-8")).hexdigest(),
        )
        finding = findings.setdefault(
            key,
            {
                "vuid": key[0],
                "severity": severity_match.group(1).lower() if severity_match else "unknown",
                "message_type": message_type,
                "message_id": key[1],
                "message": message.strip(),
                "category": validation_finding_category(message, vuid, message_type),
                "first_occurrence": occurrence,
                "last_occurrence": occurrence,
                "count": 0,
                "objects": objects,
                "object_handles": sorted({str(obj["handle"]) for obj in objects}),
                "frame": first_number(message, r"frame[=: ]+(\d+)"),
                "pass": first_number(message, r"pass[=: ]+(\d+)"),
                "draw": first_number(message, r"draw[=: ]+(\d+)"),
                "queue": first_number(message, r"queue[=: ]+(\d+)"),
                "submission": first_number(message, r"submission[=: ]+(\d+)"),
                "semantic_operation": semantic_match.group(1) if semantic_match else None,
            },
        )
        finding["count"] = int(finding["count"]) + 1
        finding["last_occurrence"] = occurrence
        if objects:
            merged = {str(obj["handle"]).lower(): obj for obj in finding.get("objects", []) if isinstance(obj, dict) and obj.get("handle")}
            for obj in objects:
                merged[str(obj["handle"]).lower()] = obj
            finding["objects"] = list(merged.values())
            finding["object_handles"] = sorted({str(obj.get("handle")) for obj in finding["objects"] if isinstance(obj, dict) and obj.get("handle")})
    return list(findings.values())


def validation_category_counts(findings: Sequence[dict[str, object]]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for finding in findings:
        category = str(finding.get("category") or "unknown")
        counts[category] = counts.get(category, 0) + int(finding.get("count") or 0)
    return counts


def concrete_validation_finding_count(findings: Sequence[dict[str, object]]) -> int:
    concrete_categories = {"concrete_api_vuid", "synchronization", "gpu_assisted", "device_loss"}
    return sum(int(finding.get("count") or 0) for finding in findings if finding.get("category") in concrete_categories)


def validation_proof(
    meta: Mapping[str, str],
    combined_logs: str,
    files: Mapping[str, Path | None],
    tool_kind: str,
    mode: ModeSpec,
    work_counts: Mapping[str, object],
    creation_counts: Mapping[str, object],
    deterministic_complete: bool,
    subsystem_complete: bool,
    frame_sample_window_complete: bool,
) -> dict[str, object]:
    profile = meta.get("validation_profile") or meta.get("validation_mode", "off")
    requested_details = validation_profile_details(str(profile))
    vk_layer_settings = meta.get("vk_layer_settings", "")
    requested_features = requested_details.get("features", [])
    applied_settings = [part.strip() for part in vk_layer_settings.split(",") if part.strip()]
    layer_loaded = (
        meta.get("validation_enabled", "").lower() == "true"
        and "VK_LAYER_KHRONOS_validation" in meta.get("vk_instance_layers", "")
        and (
            "VK_LAYER_KHRONOS_validation" in combined_logs
            or "libVkLayer_khronos_validation" in combined_logs
            or "Insert instance layer" in combined_logs
        )
    )
    no_filtering = not any(
        meta.get(name)
        for name in (
            "vk_validation_features_disabled",
            "vk_validation_message_filter",
            "vk_validation_mute",
        )
    )
    is_vulkan_validation_backend = mode.backend in {"vulkan", "rust-vulkan"}
    rust_vulkan_shell_workload = mode.backend == "rust-vulkan" and bool(
        re.search(
            r"gal\.frame\.(?:acquire|present) backend=vulkan|Rust VulkanicGAL GUI frame|rust_gal_backend_submissions[=: ]+[1-9]",
            combined_logs,
            re.IGNORECASE,
        )
    )
    meaningful_workload = not is_vulkan_validation_backend or (
        rust_vulkan_shell_workload
        or
        (tool_kind == "capture" and deterministic_complete and (int(work_counts.get("draw") or 0) > 0 or int(creation_counts.get("descriptor") or 0) > 0))
        or (
            tool_kind == "subsystem"
            and (
                subsystem_complete
                or int(work_counts.get("draw") or 0) > 0
                or int(work_counts.get("pass") or 0) > 0
                or int(creation_counts.get("pipeline") or 0) > 0
                or int(creation_counts.get("descriptor") or 0) > 0
            )
        )
        or (tool_kind == "gameplay" and frame_sample_window_complete and int(work_counts.get("draw") or 0) > 0)
    )
    run_id = meta.get("run_id", "")
    associated_files: dict[str, bool] = {}
    if run_id:
        for name, path in files.items():
            if path is None:
                continue
            if name in {"meta", "renderdoc_summary", "tracy_summary", "deterministic", "frame_benchmark", "subsystem_benchmark"}:
                continue
            associated_files[name] = run_id in path.name
    return {
        "profile": profile,
        "layer_loaded": layer_loaded,
        "validation_layer_manifest": meta.get("validation_layer_manifest"),
        "vk_instance_layers": meta.get("vk_instance_layers", ""),
        "vk_add_layer_path": meta.get("vk_add_layer_path", ""),
        "vk_loader_debug": meta.get("vk_loader_debug", ""),
        "requested_features": requested_features,
        "applied_layer_settings": applied_settings,
        "core_requested": profile not in {"off", ""},
        "synchronization_requested": any("validate_sync=true" == item for item in applied_settings),
        "best_practices_requested": any("validate_best_practices=true" == item for item in applied_settings),
        "gpu_assisted_requested": any(item.startswith("gpuav_enable=true") for item in applied_settings),
        "debug_printf_requested": any(item.startswith("printf_enable=true") for item in applied_settings),
        "no_message_filtering": no_filtering,
        "meaningful_vulkan_workload": meaningful_workload,
        "run_id": run_id,
        "process": {
            "gradle_pid": parse_number(meta.get("gradle_pid")),
            "client_pid": parse_number(meta.get("client_pid")),
            "exit_code": parse_number(meta.get("exit_code")),
        },
        "artifact_log_association": {
            "all_named_logs_match_run": all(associated_files.values()) if associated_files else False,
            "files": associated_files,
        },
        "workload_counts": dict(work_counts),
        "creation_counts": dict(creation_counts),
    }


def subsystem_workload_counts(subsystem_doc: dict[str, object] | None) -> tuple[dict[str, int], dict[str, int]]:
    work_counts = {"draw": 0, "dispatch": 0, "pass": 0, "transfer": 0, "vulkan_submission": 0}
    creation_counts = {"pipeline": 0, "descriptor": 0, "resource": 0}
    if not isinstance(subsystem_doc, dict):
        return work_counts, creation_counts
    workloads = subsystem_doc.get("workloads")
    if not isinstance(workloads, list):
        return work_counts, creation_counts
    max_submission = 0
    for workload in workloads:
        if not isinstance(workload, dict):
            continue
        counts = workload.get("counts")
        if isinstance(counts, dict):
            for key in ("draw", "dispatch", "pass", "transfer"):
                value = counts.get(key)
                if isinstance(value, (int, float)):
                    work_counts[key] += int(value)
            for key in ("pipeline", "descriptor", "resource"):
                value = counts.get(key)
                if isinstance(value, (int, float)):
                    creation_counts[key] += int(value)
            value = counts.get("submission")
            if isinstance(value, (int, float)):
                work_counts["vulkan_submission"] += int(value)
        last_submission = workload.get("lastSubmission")
        if isinstance(last_submission, (int, float)):
            max_submission = max(max_submission, int(last_submission))
    if work_counts["vulkan_submission"] == 0:
        work_counts["vulkan_submission"] = max_submission
    return work_counts, creation_counts


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
        frozen = Path(explicit).expanduser().resolve()
        source = "--frozen-repo"
    else:
        frozen = resolve_named_directory(current_root, "java_perf_repo")
        source = "DevUtils/Common/platform/directory/directories.json:java_perf_repo"
    validate_repository_root(frozen, "Frozen Java", source)
    return frozen


def validate_repository_root(root: Path, label: str, source: str) -> None:
    if not root.is_dir():
        raise SystemExit(f"{label} repository path from {source} does not exist: {root}")
    if not ((root / "gradlew").is_file() or (root / "gradlew.bat").is_file()):
        raise SystemExit(f"{label} repository path from {source} is missing a Gradle wrapper: {root}")
    if not (root / ".git").is_dir():
        raise SystemExit(f"{label} repository path from {source} is missing .git: {root}")


def repository_resolution(current: Path, frozen: Path, explicit: str | None = None) -> dict[str, object]:
    return {
        "current": str(current.resolve()),
        "frozen": str(frozen.resolve()),
        "frozen_source": "--frozen-repo" if explicit else "DevUtils/Common/platform/directory/directories.json:java_perf_repo",
    }


def select_targets(args: argparse.Namespace) -> dict[str, RepoTarget]:
    current = repo_root()
    validate_repository_root(current, "Current", "repository root")
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
    try:
        tokens = shlex.split(client_args)
        filtered: list[str] = []
        skip_next = False
        for token in tokens:
            if skip_next:
                skip_next = False
                continue
            if token == option:
                skip_next = True
                continue
            if token.startswith(f"{option}="):
                continue
            filtered.append(token)
        return shlex.join(filtered)
    except ValueError:
        pass
    pattern = rf"(^|\s){re.escape(option)}(=[^\s]+|\s+[^\s]+)?"
    return re.sub(pattern, " ", client_args).strip()


def remove_client_arg_assignment(client_args: str, key: str) -> str:
    if not client_args:
        return ""
    try:
        return shlex.join(token for token in shlex.split(client_args) if not token.startswith(f"{key}="))
    except ValueError:
        pass
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


def static_terrain_geometry_evidence(doc: dict[str, object] | None) -> dict[str, object]:
    if not isinstance(doc, dict):
        return {"status": "fail", "failure": "geometry_truth_missing", "checked_events": 0}
    events = doc.get("recentEvents")
    if not isinstance(events, list) or not events:
        return {"status": "fail", "failure": "geometry_truth_missing", "checked_events": 0}
    checked = 0
    visible_checked = 0
    failures: list[str] = []
    landmark_events: list[dict[str, object]] = []
    expected_stride = int(parse_number(doc.get("activeNativeVertexStride")) or 0)
    previous_visible_key: tuple[object, object] | None = None
    visible_by_frame: dict[tuple[object, object, object, object], object] = {}
    visible_generation_by_frame: dict[tuple[object, object, object], set[int]] = {}
    executed_by_submission: set[tuple[object, object, object, object]] = set()
    mesh_content: dict[int, tuple[object, object, int]] = {}

    def number(event: dict[str, object], name: str) -> float | None:
        return parse_number(event.get(name))

    def nested_number(event: dict[str, object], parent: str, name: str) -> float | None:
        child = event.get(parent)
        if not isinstance(child, dict):
            return None
        return parse_number(child.get(name))

    for raw_event in events:
        if not isinstance(raw_event, dict):
            continue
        reason = str(raw_event.get("reason") or "")
        if reason not in {
            "mesh-registered",
            "visible-submit",
            "stale-or-unregistered-submit",
            "executed-submit",
            "cross_world_stale_submission",
            "cross_world_stale_submission_unexpected_success",
        }:
            continue
        gameplay_frame_id = int(parse_number(raw_event.get("gameplayFrameId")) or 0)
        extraction_frame_id = int(parse_number(raw_event.get("terrainExtractionFrameId")) or 0)
        enqueue_frame_id = int(parse_number(raw_event.get("rustEnqueueFrameId")) or 0)
        execution_frame_id = int(parse_number(raw_event.get("executionFrameId")) or 0)
        execution_submission_id = int(parse_number(raw_event.get("executionSubmissionId")) or 0)
        mesh_key = int(parse_number(raw_event.get("meshKey")) or 0)
        content_hash = int(parse_number(raw_event.get("contentHash")) or 0)
        vertex_count = int(parse_number(raw_event.get("vertexCount")) or 0)
        buffer_vertex_capacity = int(parse_number(raw_event.get("bufferVertexCapacity")) or 0)
        vertex_stride = int(parse_number(raw_event.get("vertexStride")) or 0)
        index_count = int(parse_number(raw_event.get("indexCount")) or 0)
        max_index = int(parse_number(raw_event.get("maxIndex")) or -1)
        index_type = int(parse_number(raw_event.get("indexType")) or 0)
        section_count = int(parse_number(raw_event.get("sectionCount")) or 0)
        if mesh_key == 0 or vertex_count <= 0 or vertex_stride < 20 or index_count <= 0 or section_count <= 0:
            failures.append("geometry_truth_missing")
            continue
        checked += 1
        if reason == "visible-submit":
            visible_checked += 1
            visible_key = (raw_event.get("sectionPos"), raw_event.get("layer"))
            generation_key = (
                gameplay_frame_id if gameplay_frame_id > 0 else enqueue_frame_id,
                raw_event.get("sectionPos"),
                raw_event.get("layer"),
            )
            frame_visible_key = (
                generation_key[0],
                raw_event.get("sectionPos"),
                raw_event.get("layer"),
                int(parse_number(raw_event.get("visibleGeneration")) or 0),
            )
            if frame_visible_key in visible_by_frame:
                failures.append("duplicate_section_visible")
            visible_by_frame[frame_visible_key] = raw_event.get("meshKey")
            generation_set = visible_generation_by_frame.setdefault(generation_key, set())
            generation_set.add(int(parse_number(raw_event.get("visibleGeneration")) or 0))
            if len(generation_set) > 1:
                failures.append("stale_generation_overlap")
            if visible_key == previous_visible_key and generation_key[0] <= 0:
                failures.append("duplicate_section_visible")
            previous_visible_key = visible_key
        if reason == "executed-submit":
            executed_key = (
                execution_submission_id,
                raw_event.get("sectionPos"),
                raw_event.get("layer"),
                int(parse_number(raw_event.get("visibleGeneration")) or 0),
            )
            if execution_submission_id <= 0 or execution_frame_id <= 0:
                failures.append("execution_identity_missing")
            elif executed_key in executed_by_submission:
                failures.append("duplicate_section_execution")
            executed_by_submission.add(executed_key)
        if reason == "cross_world_stale_submission":
            failures.append("cross_world_stale_submission")
        if reason == "cross_world_stale_submission_unexpected_success":
            failures.append("cross_world_stale_submission_unexpected_success")
        if reason == "stale-or-unregistered-submit" or int(parse_number(raw_event.get("visibleGeneration")) or 0) not in {
            0,
            int(parse_number(raw_event.get("meshGeneration")) or 0),
        }:
            failures.append("stale_generation_visible")
        mesh_identity = (raw_event.get("sectionPos"), raw_event.get("layer"))
        previous_mesh = mesh_content.get(mesh_key)
        if previous_mesh is not None and previous_mesh[:2] != mesh_identity and previous_mesh[2] != content_hash:
            failures.append("mesh_key_collision")
        else:
            mesh_content[mesh_key] = (mesh_identity[0], mesh_identity[1], content_hash)
        if expected_stride > 0 and vertex_stride != expected_stride:
            failures.append("vertex_stride_invalid")
        if buffer_vertex_capacity > 0 and vertex_count > buffer_vertex_capacity:
            failures.append("vertex_count_exceeds_capacity")
        if index_type not in {1, 2}:
            failures.append("index_type_invalid")
        if max_index < 0 or max_index >= vertex_count:
            failures.append("index_range_invalid")
        if raw_event.get("vertexPositionsFinite") is not True:
            failures.append("non_finite_vertex_position")
        if raw_event.get("sectionOriginValid") is not True:
            failures.append("section_origin_mismatch")
        if raw_event.get("indexOffsetAlignmentValid") is not True:
            failures.append("index_alignment_invalid")
        lighting_flag_failures = {
            "normalContractValid": "terrain_lighting_normal_invalid",
            "aoContractValid": "terrain_lighting_ao_invalid",
            "blockSkyLightContractValid": "terrain_lighting_block_sky_invalid",
            "topFaceShadeContractValid": "terrain_lighting_top_shade_invalid",
        }
        for flag_name, failure_name in lighting_flag_failures.items():
            if raw_event.get(flag_name) is not True:
                failures.append(failure_name)
        if raw_event.get("separateAoActive") is True:
            separate_ao_vertices = int(parse_number(raw_event.get("separateAoVertexCount")) or 0)
            if separate_ao_vertices <= 0:
                failures.append("terrain_lighting_ao_missing")
            ao_range = raw_event.get("aoRange")
            if isinstance(ao_range, dict):
                min_ao = parse_number(ao_range.get("min"))
                max_ao = parse_number(ao_range.get("max"))
                if min_ao is None or max_ao is None or not (0.0 <= min_ao <= max_ao <= 1.0):
                    failures.append("terrain_lighting_ao_range_invalid")
            else:
                failures.append("terrain_lighting_ao_range_invalid")
        flag_failures = {
            "localBoundsValid": "geometry_out_of_bounds",
            "uvBoundsValid": "uv_bounds_invalid",
            "indexRangeValid": "index_range_invalid",
            "segmentLayoutValid": "segment_layout_invalid",
            "cameraBoundsFinite": "camera_bounds_invalid",
        }
        for flag_name, failure_name in flag_failures.items():
            if raw_event.get(flag_name) is not True:
                failures.append(failure_name)
        local_min_x = nested_number(raw_event, "localBounds", "minX")
        local_min_y = nested_number(raw_event, "localBounds", "minY")
        local_min_z = nested_number(raw_event, "localBounds", "minZ")
        local_max_x = nested_number(raw_event, "localBounds", "maxX")
        local_max_y = nested_number(raw_event, "localBounds", "maxY")
        local_max_z = nested_number(raw_event, "localBounds", "maxZ")
        bounds = [local_min_x, local_min_y, local_min_z, local_max_x, local_max_y, local_max_z]
        if any(value is None or not math.isfinite(value) for value in bounds):
            failures.append("geometry_out_of_bounds")
        elif (
            local_min_x < -8.01
            or local_min_y < -8.01
            or local_min_z < -8.01
            or local_max_x > 24.01
            or local_max_y > 24.01
            or local_max_z > 24.01
            or local_min_x > local_max_x
            or local_min_y > local_max_y
            or local_min_z > local_max_z
        ):
            failures.append("geometry_out_of_bounds")
        if len(landmark_events) < 8:
            landmark_events.append(
                {
                    "reason": reason,
                    "gameplayFrameId": gameplay_frame_id,
                    "terrainExtractionFrameId": extraction_frame_id,
                    "rustEnqueueFrameId": enqueue_frame_id,
                    "executionFrameId": execution_frame_id,
                    "executionSubmissionId": execution_submission_id,
                    "sectionPos": raw_event.get("sectionPos"),
                    "layer": raw_event.get("layer"),
                    "meshKey": mesh_key,
                    "contentHash": content_hash,
                    "vertexCount": vertex_count,
                    "bufferVertexCapacity": buffer_vertex_capacity,
                    "vertexStride": vertex_stride,
                    "indexCount": index_count,
                    "maxIndex": max_index,
                    "indexType": index_type,
                    "sectionCount": section_count,
                    "sectionOrigin": raw_event.get("sectionOrigin"),
                    "transformTranslation": raw_event.get("transformTranslation"),
                    "localBounds": raw_event.get("localBounds"),
                    "uvBounds": raw_event.get("uvBounds"),
                }
            )
    if checked <= 0:
        return {
            "status": "fail",
            "failure": "geometry_truth_missing",
            "checked_events": checked,
            "visible_submit_events": visible_checked,
            "landmark_events": landmark_events,
        }
    if visible_checked <= 0:
        failures.append("projected_landmark_missing")
    unique_failures = sorted(set(failures))
    return {
        "status": "pass" if not unique_failures else "fail",
        "failure": unique_failures[0] if unique_failures else None,
        "failures": unique_failures,
        "checked_events": checked,
        "visible_submit_events": visible_checked,
        "landmark_events": landmark_events,
    }


def static_terrain_lifecycle_evidence(
    diagnostics_doc: dict[str, object] | None,
    lifecycle_doc: dict[str, object] | None,
    scenario: str,
) -> dict[str, object]:
    scenario = (scenario or "").strip().lower()
    lifecycle_scenarios = {
        "interior-edit",
        "boundary-x-edit",
        "boundary-y-edit",
        "boundary-z-edit",
        "section-reentry",
        "resource-reload",
        "opaque-texture-replacement",
        "cutout-texture-replacement",
        "pack-priority-reversal",
        "missing-atlas-payload",
        "malformed-png-payload",
        "partial-texture-update",
        "model-resource-generation-change",
        "resize-cycle",
        "swapchain-recreate",
        "world-unload-reload",
        "world-different-reload",
        "view-distance-decrease",
        "view-distance-increase",
        "camera-relocation",
        "return-visited-terrain",
        "memory-cache-soak",
        "steady-state-performance",
    }
    atlas_scenarios = {
        "resource-reload",
        "opaque-texture-replacement",
        "cutout-texture-replacement",
        "pack-priority-reversal",
        "missing-atlas-payload",
        "malformed-png-payload",
        "partial-texture-update",
    }
    edit_scenarios = {
        "interior-edit",
        "boundary-x-edit",
        "boundary-y-edit",
        "boundary-z-edit",
        "section-reentry",
        "model-resource-generation-change",
    }
    if scenario not in lifecycle_scenarios:
        return {"status": "skip", "failure": None}
    failures: list[str] = []
    events: list[object] = []
    if isinstance(diagnostics_doc, dict):
        lifecycle_events = diagnostics_doc.get("lifecycleEvents")
        recent_events = diagnostics_doc.get("recentEvents")
        if isinstance(lifecycle_events, list):
            events.extend(lifecycle_events)
        if isinstance(recent_events, list):
            events.extend(recent_events)
    event_reasons: list[str] = []
    if events:
        for event in events:
            if isinstance(event, dict):
                event_reasons.append(str(event.get("reason") or ""))
    else:
        failures.append("lifecycle_events_missing")
    stage = str(lifecycle_doc.get("stage") or "") if isinstance(lifecycle_doc, dict) else ""
    setup = bool(lifecycle_doc.get("setup")) if isinstance(lifecycle_doc, dict) else False
    after_recorded = bool(lifecycle_doc.get("afterRecorded")) if isinstance(lifecycle_doc, dict) else False
    before_generation = int(parse_number(lifecycle_doc.get("beforeGeneration")) or 0) if isinstance(lifecycle_doc, dict) else 0
    after_generation = int(parse_number(lifecycle_doc.get("afterGeneration")) or 0) if isinstance(lifecycle_doc, dict) else 0
    edit_block = str(lifecycle_doc.get("editBlock") or "") if isinstance(lifecycle_doc, dict) else ""
    if not setup:
        failures.append("lifecycle_setup_missing")
    if not after_recorded or stage != "replacement-visible":
        failures.append("lifecycle_replacement_missing")
    if before_generation == 0 or after_generation == 0:
        failures.append("lifecycle_generation_missing")
    elif scenario in (edit_scenarios | atlas_scenarios) and before_generation == after_generation:
        failures.append("lifecycle_generation_unchanged")
    world_transition_scenarios = {"world-unload-reload", "world-different-reload"}
    if not edit_block and scenario not in world_transition_scenarios:
        failures.append("lifecycle_edit_block_missing")
    required_reason_prefixes = []
    if scenario not in world_transition_scenarios:
        required_reason_prefixes.extend(
            [
                "lifecycle-edit-before",
                "lifecycle-edit-applied",
                "lifecycle-edit-after",
            ]
        )
    if scenario == "section-reentry":
        required_reason_prefixes.extend(["lifecycle-section-removed", "lifecycle-section-reentry-requested"])
    if scenario in {"resize-cycle", "swapchain-recreate"}:
        required_reason_prefixes.extend(["lifecycle-resize-1", "lifecycle-resize-2"])
        resize_count = int(parse_number(lifecycle_doc.get("resizeCount")) or 0) if isinstance(lifecycle_doc, dict) else 0
        if resize_count < 2:
            failures.append("lifecycle_resize_cycle_incomplete")
    if scenario in {"resource-reload", "pack-priority-reversal"}:
        required_reason_prefixes.append("lifecycle-resource-reload-started")
    if scenario == "view-distance-decrease":
        required_reason_prefixes.append("lifecycle-view-distance-decreased")
    if scenario in {"view-distance-increase", "memory-cache-soak", "steady-state-performance"}:
        required_reason_prefixes.append("lifecycle-view-distance-increased")
    if scenario == "camera-relocation":
        required_reason_prefixes.append("lifecycle-camera-relocated")
    if scenario == "return-visited-terrain":
        required_reason_prefixes.extend(["lifecycle-camera-relocated", "lifecycle-return-visited-terrain-away", "lifecycle-return-visited-terrain-back"])
        action_step = int(parse_number(lifecycle_doc.get("actionStep")) or 0) if isinstance(lifecycle_doc, dict) else 0
        if action_step < 2:
            failures.append("lifecycle_return_visit_incomplete")
    if scenario == "memory-cache-soak":
        required_reason_prefixes.append("lifecycle-memory-cache-soak-started")
    if scenario == "steady-state-performance":
        required_reason_prefixes.append("lifecycle-steady-state-performance-started")
    if scenario in {"world-unload-reload", "world-different-reload"}:
        required_reason_prefixes.extend(
            [
                "lifecycle-world-unload",
                "lifecycle-world-menu-baseline",
                "lifecycle-world-reload-requested",
                "lifecycle-world-reload-valid",
            ]
        )
        before_cached = int(parse_number(lifecycle_doc.get("beforeCachedLayers")) or 0) if isinstance(lifecycle_doc, dict) else 0
        after_cached = int(parse_number(lifecycle_doc.get("afterCachedLayers")) or 0) if isinstance(lifecycle_doc, dict) else 0
        menu_cached = int(parse_number(lifecycle_doc.get("menuCachedLayers")) or 0) if isinstance(lifecycle_doc, dict) else 0
        menu_active_layers = int(parse_number(lifecycle_doc.get("menuActiveTerrainLayers")) or 0) if isinstance(lifecycle_doc, dict) else 0
        menu_active_section_assets = int(parse_number(lifecycle_doc.get("menuActiveSectionAssets")) or 0) if isinstance(lifecycle_doc, dict) else 0
        menu_current_frame_submissions = (
            int(parse_number(lifecycle_doc.get("menuCurrentFrameVisibleSubmissions")) or 0)
            if isinstance(lifecycle_doc, dict)
            else 0
        )
        unload_submissions = int(parse_number(lifecycle_doc.get("unloadVisibleSubmissions")) or 0) if isinstance(lifecycle_doc, dict) else 0
        menu_submissions = int(parse_number(lifecycle_doc.get("menuVisibleSubmissions")) or 0) if isinstance(lifecycle_doc, dict) else 0
        reload_generation_a = int(parse_number(lifecycle_doc.get("reloadGenerationA")) or 0) if isinstance(lifecycle_doc, dict) else 0
        reload_generation_b = int(parse_number(lifecycle_doc.get("reloadGenerationB")) or 0) if isinstance(lifecycle_doc, dict) else 0
        if before_cached <= 0 or after_cached <= 0:
            failures.append("lifecycle_world_cache_boundary_missing")
        if menu_cached != 0:
            failures.append("lifecycle_world_menu_cache_not_bounded")
        if menu_active_layers != 0:
            failures.append("lifecycle_world_menu_active_layers_not_zero")
        if menu_active_section_assets != 0:
            failures.append("lifecycle_world_menu_active_section_assets_not_zero")
        if menu_current_frame_submissions != 0:
            failures.append("lifecycle_world_menu_current_frame_submission_leak")
        if unload_submissions <= 0 or menu_submissions != unload_submissions:
            failures.append("lifecycle_world_cumulative_submission_boundary_invalid")
        if reload_generation_a <= 0:
            failures.append("lifecycle_world_a_reload_generation_missing")
        if scenario == "world-different-reload" and reload_generation_b <= 0:
            failures.append("lifecycle_world_b_reload_generation_missing")
    if scenario in {"memory-cache-soak", "steady-state-performance"} and isinstance(lifecycle_doc, dict):
        before_memory = int(parse_number(lifecycle_doc.get("beforeUsedMemoryBytes")) or 0)
        after_memory = int(parse_number(lifecycle_doc.get("afterUsedMemoryBytes")) or 0)
        if before_memory <= 0 or after_memory <= 0:
            failures.append("lifecycle_memory_measurement_missing")
    if scenario in atlas_scenarios:
        diagnostics = diagnostics_doc if isinstance(diagnostics_doc, dict) else {}
        texture_updates = int(parse_number(diagnostics.get("texturePayloadUpdates")) or 0)
        payload_bytes = int(parse_number(diagnostics.get("texturePayloadUpdateBytes")) or 0)
        texture_only = int(parse_number(diagnostics.get("atlasTextureOnlyUpdates")) or 0)
        world_mesh_metrics = diagnostics.get("worldMeshAssetMetrics")
        world_mesh_failures = 0
        if isinstance(world_mesh_metrics, dict):
            world_mesh_failures = int(parse_number(world_mesh_metrics.get("failures")) or 0)
        if texture_updates <= 0:
            failures.append("atlas_texture_update_missing")
        if scenario not in {"missing-atlas-payload", "malformed-png-payload"} and payload_bytes <= 0:
            failures.append("atlas_payload_bytes_missing")
        if scenario in {"opaque-texture-replacement", "cutout-texture-replacement", "missing-atlas-payload", "malformed-png-payload", "partial-texture-update"} and texture_only <= 0:
            failures.append("atlas_texture_only_update_missing")
        if scenario == "missing-atlas-payload" and int(parse_number(diagnostics.get("atlasMissingPayloadUpdates")) or 0) <= 0:
            failures.append("atlas_missing_payload_not_recorded")
        if scenario == "malformed-png-payload":
            if int(parse_number(diagnostics.get("atlasMalformedPayloadUpdates")) or 0) <= 0:
                failures.append("atlas_malformed_payload_not_recorded")
            if world_mesh_failures <= 0:
                failures.append("atlas_malformed_rollback_missing")
        if scenario == "partial-texture-update" and int(parse_number(diagnostics.get("atlasPartialPayloadUpdates")) or 0) <= 0:
            failures.append("atlas_partial_payload_not_recorded")
    lifecycle_fault_prefixes = {
        "lifecycle-fault-old-generation-after-edit": "lifecycle_old_generation_after_edit",
        "lifecycle-fault-old-new-overlap": "lifecycle_old_new_overlap",
        "lifecycle-fault-removed-section-resubmitted": "lifecycle_removed_section_resubmitted",
        "lifecycle-fault-wrong-neighbor-invalidated": "lifecycle_wrong_neighbor_invalidated",
        "cross_world_stale_submission": "cross_world_stale_submission",
        "cross_world_stale_submission_unexpected_success": "cross_world_stale_submission_unexpected_success",
    }
    for prefix, classification in lifecycle_fault_prefixes.items():
        if any(reason.startswith(prefix) for reason in event_reasons):
            failures.append(classification)
    for prefix in required_reason_prefixes:
        if not any(reason.startswith(prefix) for reason in event_reasons):
            failures.append(prefix.replace("-", "_") + "_missing")
    return {
        "status": "pass" if not failures else "fail",
        "failure": sorted(set(failures))[0] if failures else None,
        "failures": sorted(set(failures)),
        "stage": stage,
        "edit_block": edit_block,
        "before_generation": before_generation,
        "after_generation": after_generation,
        "reason_counts": {reason: event_reasons.count(reason) for reason in sorted(set(event_reasons)) if reason.startswith("lifecycle-")},
    }


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
    median = parse_number(stats.get("median"))
    p95 = parse_number(stats.get("p95"))
    p99 = parse_number(stats.get("p99"))
    return {
        "count": int(count),
        "median": (median / 1_000_000.0) if median is not None else average_ms,
        "p95": (p95 / 1_000_000.0) if p95 is not None else None,
        "p99": (p99 / 1_000_000.0) if p99 is not None else None,
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


def file_text(path: Path | None, max_tail_bytes: int = NORMALIZED_LOG_TAIL_BYTES) -> str:
    if path is None or not path.is_file():
        return ""
    size = path.stat().st_size
    if max_tail_bytes > 0 and size > max_tail_bytes:
        with path.open("rb") as handle:
            handle.seek(-max_tail_bytes, 2)
            data = handle.read()
        return (
            f"[graphics-audit log truncated path={path.name} original_bytes={size} "
            f"tail_bytes={max_tail_bytes}]\n"
            + data.decode("utf-8", errors="replace")
        )
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


def rust_gal_metrics_positive(logs: str) -> bool:
    for line in logs.splitlines():
        if "Rust OpenGL VulkanicGAL GUI" not in line and "Rust VulkanicGAL bridge" not in line:
            continue
        for key in (
            "rust_gal_frames_executed",
            "rust_gal_batches_executed",
            "rust_gal_ffi_frame_acquire_calls",
            "rust_gal_ffi_submit_calls",
            "ffi_call_count",
        ):
            match = re.search(rf"\b{key}=([0-9]+)\b", line)
            if match and int(match.group(1)) > 0:
                return True
    return False


def has_rust_opengl_evidence(mode: ModeSpec, logs: str) -> bool:
    return mode.backend == "rust-opengl" or bool(
        re.search(r"\brust-opengl\b", logs, re.IGNORECASE)
        or rust_gal_metrics_positive(logs)
    )


def has_rust_vulkan_evidence(mode: ModeSpec, logs: str) -> bool:
    return mode.backend == "rust-vulkan" or bool(
        re.search(r"Rust Vulkan(?!icGAL)|rust-vulkan|mattmc_rust.*vulkan", logs, re.IGNORECASE)
    )


def parse_java_property(text: str, key: str) -> str | None:
    pattern = re.compile(rf"-D{re.escape(key)}=([^\s'\"\\]+)")
    match = pattern.search(text)
    return match.group(1) if match else None


def detect_attribution(mode: ModeSpec, meta: dict[str, str], logs: str) -> str:
    explicit = meta.get("implementation_attribution") or meta.get("render_implementation")
    rust_opengl_evidence = has_rust_opengl_evidence(mode, logs)
    rust_vulkan_evidence = has_rust_vulkan_evidence(mode, logs)
    if mode.target == "current" and mode.backend == "opengl" and rust_opengl_evidence:
        return "mixed-java-opengl-rust-opengl"
    if explicit == "rust-vulkan" and rust_opengl_evidence and not rust_vulkan_evidence:
        return "rust-opengl"
    if explicit == "rust-opengl" and rust_vulkan_evidence and not rust_opengl_evidence:
        return "rust-vulkan"
    if explicit:
        return explicit
    if rust_opengl_evidence:
        return "rust-opengl"
    if rust_vulkan_evidence:
        return "rust-vulkan"
    if mode.backend == "vulkan":
        java_vulkan_evidence = re.search(
            r"Sodium Vulkan|Vulkan beginFramebufferRenderPass|vk[A-Z][A-Za-z0-9_]+|VK_LAYER_KHRONOS_validation|Vulkanic",
            logs,
            re.IGNORECASE,
        )
        if java_vulkan_evidence:
            return "java-vulkan"
        if re.search(r"fallback|compatibility OpenGL|delegated to OpenGL", logs, re.IGNORECASE):
            return "fallback"
        return "java-vulkan"
    return "java-opengl"


def implementation_attribution_families(mode: ModeSpec, attribution: str, logs: str) -> dict[str, str]:
    families = {"frame.base": mode.expected_attribution}
    if attribution == "mixed-java-opengl-rust-opengl" or (
        mode.target == "current" and mode.backend == "opengl" and has_rust_opengl_evidence(mode, logs)
    ):
        families["gui.migrated"] = "rust-opengl"
    elif mode.backend.startswith("rust-"):
        families["frame.base"] = attribution
    return families


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


def expected_capture_window_size(doc: dict[str, object] | None, capture: dict[str, object] | None) -> tuple[int, int]:
    for source in (
        capture.get("window") if isinstance(capture, dict) and isinstance(capture.get("window"), dict) else None,
        doc.get("window") if isinstance(doc, dict) and isinstance(doc.get("window"), dict) else None,
    ):
        if not isinstance(source, dict):
            continue
        width = int(parse_number(source.get("width")) or 0)
        height = int(parse_number(source.get("height")) or 0)
        if width > 0 and height > 0:
            return width, height
    return 1280, 720


def captured_game_window_evidence(
    screenshot_size: tuple[int, int],
    doc: dict[str, object] | None,
    capture: dict[str, object] | None,
) -> dict[str, object]:
    expected_width, expected_height = expected_capture_window_size(doc, capture)
    width, height = screenshot_size
    matches = width == expected_width and height == expected_height
    target_window = str((capture or {}).get("targetWindow") or "")
    target_status = "not_recorded"
    if target_window:
        target_status = "passed" if target_window != "root" else "failed"
    return {
        "status": "passed" if matches and target_status != "failed" else "failed",
        "captured_width": width,
        "captured_height": height,
        "expected_width": expected_width,
        "expected_height": expected_height,
        "target_window": target_window,
        "target_window_status": target_status,
    }


def deterministic_block_outline_pixel_evidence(doc: dict[str, object] | None, style: object) -> dict[str, object]:
    evidence: dict[str, object] = {
        "checked": False,
        "status": "not_requested",
        "screenshot": None,
        "crop": None,
        "matching_pixels": 0,
        "threshold": 64,
    }
    if not isinstance(doc, dict) or not (
        doc.get("blockOutlineRealTargetForced") or doc.get("blockOutlineRealTargetAimed")
    ):
        return evidence
    if str(style or "").strip() != "high-contrast":
        evidence["status"] = "not_high_contrast"
        return evidence
    captures = doc.get("captures")
    if not isinstance(captures, list) or not captures:
        evidence["status"] = "missing_capture"
        return evidence
    first = captures[0]
    if not isinstance(first, dict) or not first.get("screenshot"):
        evidence["status"] = "missing_screenshot"
        return evidence
    screenshot = Path(str(first["screenshot"]))
    evidence["screenshot"] = str(screenshot)
    if not screenshot.is_file():
        evidence["status"] = "missing_screenshot_file"
        return evidence
    try:
        from PIL import Image
    except Exception as exc:  # pragma: no cover - depends on local test environment packaging.
        evidence["status"] = f"pillow_unavailable:{exc}"
        return evidence
    try:
        with Image.open(screenshot) as image:
            rgb = image.convert("RGB")
            width, height = rgb.size
            left = max(0, width // 2 - 160)
            top = max(0, height // 2 - 140)
            right = min(width, width // 2 + 160)
            bottom = min(height, height // 2 + 140)
            crop = rgb.crop((left, top, right, bottom))
            matching = 0
            for red, green, blue in crop.getdata():
                if red <= 120 and green >= 120 and blue >= 100 and green >= red + 45 and blue >= red + 35:
                    matching += 1
            evidence.update(
                {
                    "checked": True,
                    "status": "present" if matching >= int(evidence["threshold"]) else "absent",
                    "crop": {"left": left, "top": top, "right": right, "bottom": bottom},
                    "matching_pixels": matching,
                }
            )
    except Exception as exc:
        evidence["status"] = f"read_failed:{exc}"
    return evidence


def deterministic_world_border_pixel_evidence(doc: dict[str, object] | None, scenario: object) -> dict[str, object]:
    scenario_name = str(scenario or "").strip().lower()
    evidence: dict[str, object] = {
        "checked": False,
        "status": "not_requested",
        "scenario": scenario_name or None,
        "screenshot": None,
        "crop": None,
        "crop_path": None,
        "matching_pixels": 0,
        "pack_a_signature_pixels": 0,
        "pack_b_signature_pixels": 0,
        "vanilla_like_pixels": 0,
        "texture_signature": "unknown",
        "threshold": 128,
    }
    if not scenario_name:
        return evidence
    expected_visible = scenario_name not in {"hidden", "far", "no-target"}
    captures = doc.get("captures") if isinstance(doc, dict) else None
    if not isinstance(captures, list) or not captures:
        evidence["status"] = "missing_capture"
        return evidence
    first = captures[0]
    if not isinstance(first, dict) or not first.get("screenshot"):
        evidence["status"] = "missing_screenshot"
        return evidence
    capture_frame = parse_number(first.get("renderedFrameIndex"))
    screenshot = Path(str(first["screenshot"]))
    evidence["screenshot"] = str(screenshot)
    if not screenshot.is_file():
        evidence["status"] = "missing_screenshot_file"
        return evidence
    try:
        from PIL import Image
    except Exception as exc:  # pragma: no cover - depends on local test environment packaging.
        evidence["status"] = f"pillow_unavailable:{exc}"
        return evidence
    try:
        with Image.open(screenshot) as image:
            rgb = image.convert("RGB")
            matching = 0
            pack_a_signature = 0
            pack_b_signature = 0
            vanilla_like = 0
            min_x = rgb.width
            min_y = rgb.height
            max_x = -1
            max_y = -1
            for y in range(rgb.height):
                for x in range(rgb.width):
                    red, green, blue = rgb.getpixel((x, y))
                    green_border = green >= 110 and green >= red + 30 and green >= blue + 15
                    forcefield_border = blue >= 120 and green >= 80 and blue >= red + 60 and green >= red + 20
                    pack_a_border = red >= 90 and red >= green + 25 and red >= blue + 10
                    pack_b_border = green >= 120 and green >= red + 35 and green >= blue + 25
                    world_region = y < int(rgb.height * 0.72)
                    if world_region and pack_a_border:
                        pack_a_signature += 1
                    if world_region and pack_b_border:
                        pack_b_signature += 1
                    if forcefield_border:
                        vanilla_like += 1
                    if green_border or forcefield_border or (expected_visible and (pack_a_border or pack_b_border)):
                        matching += 1
                        min_x = min(min_x, x)
                        min_y = min(min_y, y)
                        max_x = max(max_x, x)
                        max_y = max(max_y, y)
            crop_info = None
            crop_path = None
            if matching:
                pad = 12
                left = max(0, min_x - pad)
                top = max(0, min_y - pad)
                right = min(rgb.width, max_x + pad + 1)
                bottom = min(rgb.height, max_y + pad + 1)
                crop_info = {"left": left, "top": top, "right": right, "bottom": bottom}
                crop_path_candidate = screenshot.with_name(f"world_border_pixel_crop_{scenario_name}.png")
                rgb.crop((left, top, right, bottom)).save(crop_path_candidate)
                crop_path = str(crop_path_candidate)
            present = matching >= int(evidence["threshold"])
            if expected_visible:
                status = "present" if present else "absent"
            else:
                status = "unexpected_present" if present else "absent_expected"
            texture_signature = "unknown"
            threshold = int(evidence["threshold"])
            pack_threshold = max(threshold, 512)
            if pack_a_signature >= pack_threshold or pack_b_signature >= pack_threshold:
                texture_signature = "pack-a" if pack_a_signature >= pack_b_signature else "pack-b"
            elif vanilla_like >= threshold:
                texture_signature = "vanilla-like"
            evidence.update(
                {
                    "checked": True,
                    "status": status,
                    "crop": crop_info,
                    "crop_path": crop_path,
                    "matching_pixels": matching,
                    "pack_a_signature_pixels": pack_a_signature,
                    "pack_b_signature_pixels": pack_b_signature,
                    "vanilla_like_pixels": vanilla_like,
                    "texture_signature": texture_signature,
                }
            )
    except Exception as exc:
        evidence["status"] = f"read_failed:{exc}"
    return evidence


def deterministic_world_crack_pixel_evidence(doc: dict[str, object] | None, scenario: object) -> dict[str, object]:
    scenario_name = str(scenario or "").strip().lower()
    evidence: dict[str, object] = {
        "checked": False,
        "status": "not_requested",
        "scenario": scenario_name or None,
        "screenshot": None,
        "crop": None,
        "crop_path": None,
        "matching_pixels": 0,
        "vanilla_like_pixels": 0,
        "bright_target_pixels": 0,
        "pack_a_signature_pixels": 0,
        "pack_b_signature_pixels": 0,
        "texture_signature": "unknown",
        "threshold": 256,
    }
    if not scenario_name:
        return evidence
    expected_visible = scenario_name not in {"hidden", "no-target"}
    captures = doc.get("captures") if isinstance(doc, dict) else None
    if not isinstance(captures, list) or not captures:
        evidence["status"] = "missing_capture"
        return evidence
    first = captures[0]
    if not isinstance(first, dict) or not first.get("screenshot"):
        evidence["status"] = "missing_screenshot"
        return evidence
    capture_frame = parse_number(first.get("renderedFrameIndex"))
    screenshot = Path(str(first["screenshot"]))
    evidence["screenshot"] = str(screenshot)
    if not screenshot.is_file():
        evidence["status"] = "missing_screenshot_file"
        return evidence
    try:
        from PIL import Image
    except Exception as exc:  # pragma: no cover - depends on local test environment packaging.
        evidence["status"] = f"pillow_unavailable:{exc}"
        return evidence
    try:
        with Image.open(screenshot) as image:
            rgb = image.convert("RGB")
            width, height = rgb.size
            left = max(0, int(width * 0.42))
            top = max(0, int(height * 0.28))
            right = min(width, int(width * 0.62))
            bottom = min(height, int(height * 0.70))
            crop = rgb.crop((left, top, right, bottom))
            vanilla_like = 0
            bright_target = 0
            pack_a_signature = 0
            pack_b_signature = 0
            pack_colored_signature = 0
            for red, green, blue in crop.getdata():
                if red >= 180 and green >= 180 and blue >= 180:
                    bright_target += 1
                dark_crack = red <= 120 and green <= 130 and blue <= 150
                if dark_crack:
                    vanilla_like += 1
                if red >= 145 and green <= 95 and blue <= 145:
                    pack_a_signature += 1
                if green >= 145 and red <= 130 and blue <= 155:
                    pack_b_signature += 1
                red_pack = red >= 145 and green <= 120 and blue <= 155
                green_pack = green >= 145 and red <= 130 and blue <= 155
                magenta_pack = red >= 130 and blue >= 120 and green <= 120
                if red_pack or green_pack or magenta_pack:
                    pack_colored_signature += 1
            matching = pack_colored_signature
            vanilla_present = vanilla_like >= int(evidence["threshold"])
            pack_present = matching >= 512
            if expected_visible:
                status = "present" if vanilla_present or pack_present else "absent"
            else:
                status = "not_checked_hidden"
            texture_signature = "unknown"
            if pack_present:
                texture_signature = "pack-colored"
            elif vanilla_present:
                texture_signature = "vanilla-like"
            crop_path = screenshot.with_name(f"world_crack_pixel_crop_{scenario_name}.png")
            crop.save(crop_path)
            evidence.update(
                {
                    "checked": True,
                    "status": status,
                    "crop": {"left": left, "top": top, "right": right, "bottom": bottom},
                    "crop_path": str(crop_path),
                    "matching_pixels": max(matching, vanilla_like if vanilla_present else 0),
                    "vanilla_like_pixels": vanilla_like,
                    "bright_target_pixels": bright_target,
                    "pack_a_signature_pixels": pack_a_signature,
                    "pack_b_signature_pixels": pack_b_signature,
                    "texture_signature": texture_signature,
                }
            )
    except Exception as exc:
        evidence["status"] = f"read_failed:{exc}"
    return evidence


def deterministic_world_material_marker_pixel_evidence(
    doc: dict[str, object] | None,
    scenario: object,
) -> dict[str, object]:
    scenario_name = str(scenario or "").strip().lower()
    evidence: dict[str, object] = {
        "checked": False,
        "status": "not_requested",
        "scenario": scenario_name or None,
        "screenshot": None,
        "markers_reported": 0,
        "expected_texture_ids": [],
        "validated_texture_ids": [],
        "crops": [],
        "matching_pixels": 0,
        "texture_signature": "unknown",
        "orientation_status": "not_checked",
        "position_status": "not_checked",
        "threshold": 96,
    }
    if not scenario_name:
        return evidence
    expected_texture_ids = expected_block_marker_texture_ids(scenario_name)
    evidence["expected_texture_ids"] = expected_texture_ids
    captures = doc.get("captures") if isinstance(doc, dict) else None
    markers = doc.get("rustGalWorldMaterialMarkers") if isinstance(doc, dict) else None
    if not isinstance(markers, list):
        markers = []
    evidence["markers_reported"] = len(markers)
    if scenario_name == "hidden":
        evidence.update(
            {
                "checked": True,
                "status": "absent_expected" if not markers else "unexpected_marker_metadata",
                "position_status": "absent_expected",
                "orientation_status": "absent_expected",
            }
        )
        return evidence
    if not expected_texture_ids:
        evidence["status"] = "unknown_scenario"
        return evidence
    if not isinstance(captures, list) or not captures:
        evidence["status"] = "missing_capture"
        return evidence
    first = captures[0]
    if not isinstance(first, dict) or not first.get("screenshot"):
        evidence["status"] = "missing_screenshot"
        return evidence
    capture_frame = parse_number(first.get("renderedFrameIndex"))
    screenshot = Path(str(first["screenshot"]))
    evidence["screenshot"] = str(screenshot)
    if not screenshot.is_file():
        evidence["status"] = "missing_screenshot_file"
        return evidence
    try:
        from PIL import Image
    except Exception as exc:  # pragma: no cover - depends on local test environment packaging.
        evidence["status"] = f"pillow_unavailable:{exc}"
        return evidence
    try:
        with Image.open(screenshot) as image:
            rgb = image.convert("RGB")
            validated: list[int] = []
            crops: list[dict[str, object]] = []
            total_matching = 0
            signatures = {"vanilla-barrier": 0, "vanilla-light": 0, "pack-a": 0, "pack-b": 0}
            orientation_ok = True
            for texture_id in expected_texture_ids:
                frame_filtered_markers = markers
                if capture_frame is not None:
                    exact_or_near = [
                        marker for marker in markers
                        if isinstance(marker, dict)
                        and marker.get("frameIndex") is not None
                        and abs(int(marker.get("frameIndex") or -999999) - int(capture_frame)) <= 1
                    ]
                    if exact_or_near:
                        frame_filtered_markers = exact_or_near
                candidates = [
                    marker for marker in frame_filtered_markers
                    if isinstance(marker, dict)
                    and int(marker.get("textureId") or -1) == texture_id
                    and marker.get("projected") is True
                    and isinstance(marker.get("screenBounds"), dict)
                ]
                best: dict[str, object] | None = None
                for marker in candidates:
                    bounds = marker["screenBounds"]
                    assert isinstance(bounds, dict)
                    for mirrored in (False, True):
                        crop_box = marker_crop_box(bounds, rgb.width, rgb.height, mirrored_y=mirrored)
                        if crop_box is None:
                            continue
                        crop = rgb.crop(crop_box)
                        stats = marker_crop_stats(crop, texture_id)
                        if best is None or int(stats["matching_pixels"]) > int(best["matching_pixels"]):
                            crop_path = screenshot.with_name(
                                f"world_material_marker_crop_{scenario_name}_{texture_id}_{'mirrored' if mirrored else 'projected'}.png"
                            )
                            crop.save(crop_path)
                            best = {
                                **stats,
                                "texture_id": texture_id,
                                "crop": {
                                    "left": crop_box[0],
                                    "top": crop_box[1],
                                    "right": crop_box[2],
                                    "bottom": crop_box[3],
                                    "mirrored_y": mirrored,
                                },
                                "crop_path": str(crop_path),
                                "route": marker.get("route"),
                                "center": marker.get("center"),
                                "quad_size": marker.get("quadSize"),
                            }
                if best is None:
                    crops.append({"texture_id": texture_id, "status": "missing_projected_marker"})
                    orientation_ok = False
                    continue
                crops.append(best)
                matching = int(best["matching_pixels"])
                total_matching += matching
                signatures["vanilla-barrier"] += int(best.get("vanilla_barrier_pixels", 0))
                signatures["vanilla-light"] += int(best.get("vanilla_light_pixels", 0))
                signatures["pack-a"] += int(best.get("pack_a_pixels", 0))
                signatures["pack-b"] += int(best.get("pack_b_pixels", 0))
                if matching >= int(evidence["threshold"]):
                    validated.append(texture_id)
                else:
                    orientation_ok = False
                if best.get("orientation_status") == "failed":
                    orientation_ok = False
            evidence.update(
                {
                    "checked": True,
                    "status": "present" if set(validated) == set(expected_texture_ids) else "absent",
                    "validated_texture_ids": validated,
                    "crops": crops,
                    "matching_pixels": total_matching,
                    "texture_signature": max(signatures.items(), key=lambda item: item[1])[0],
                    "orientation_status": "passed" if orientation_ok and validated else "failed",
                    "position_status": "projected_crop_hit" if validated else "missing_projected_crop_hit",
                }
            )
    except Exception as exc:
        evidence["status"] = f"read_failed:{exc}"
    return evidence


def expected_block_marker_texture_ids(scenario_name: str) -> list[int]:
    if scenario_name == "barrier":
        return [WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_BARRIER]
    if scenario_name == "lights-all":
        return WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_IDS
    if scenario_name.startswith("light-"):
        try:
            level = int(scenario_name.removeprefix("light-"))
        except ValueError:
            return []
        if 0 <= level <= 15:
            return [WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_IDS[level]]
    return []


def expected_terrain_particle_texture_ids(scenario_name: str) -> list[int]:
    return {
        "stone": [WORLD_MATERIAL_TEXTURE_STONE],
        "dirt": [WORLD_MATERIAL_TEXTURE_DIRT],
        "oak-leaves": [WORLD_MATERIAL_TEXTURE_OAK_LEAVES],
        "deepslate": [WORLD_MATERIAL_TEXTURE_DEEPSLATE],
        "white-wool": [WORLD_MATERIAL_TEXTURE_WHITE_WOOL],
        "mixed-many": [
            WORLD_MATERIAL_TEXTURE_STONE,
            WORLD_MATERIAL_TEXTURE_DIRT,
            WORLD_MATERIAL_TEXTURE_OAK_LEAVES,
            WORLD_MATERIAL_TEXTURE_DEEPSLATE,
            WORLD_MATERIAL_TEXTURE_WHITE_WOOL,
        ],
    }.get(scenario_name, [])


def expected_terrain_particle_material_mode(texture_id: int) -> int:
    return 2 if texture_id == WORLD_MATERIAL_TEXTURE_OAK_LEAVES else 1


def deterministic_world_material_terrain_particle_pixel_evidence(
    doc: dict[str, object] | None,
    scenario: object,
) -> dict[str, object]:
    scenario_name = str(scenario or "").strip().lower()
    evidence: dict[str, object] = {
        "checked": False,
        "status": "not_requested",
        "scenario": scenario_name or None,
        "screenshot": None,
        "particles_reported": 0,
        "expected_texture_ids": [],
        "validated_texture_ids": [],
        "crops": [],
        "matching_pixels": 0,
        "position_status": "not_checked",
        "threshold": 24,
    }
    if not scenario_name:
        return evidence
    particles = doc.get("rustGalWorldTerrainParticles") if isinstance(doc, dict) else None
    if not isinstance(particles, list):
        particles = []
    evidence["particles_reported"] = len(particles)
    if scenario_name == "hidden":
        evidence.update(
            {
                "checked": True,
                "status": "absent_expected" if not particles else "unexpected_particle_metadata",
                "position_status": "absent_expected",
            }
        )
        return evidence
    expected_texture_ids = expected_terrain_particle_texture_ids(scenario_name)
    evidence["expected_texture_ids"] = expected_texture_ids
    if not expected_texture_ids:
        evidence["status"] = "unknown_scenario"
        return evidence
    captures = doc.get("captures") if isinstance(doc, dict) else None
    if not isinstance(captures, list) or not captures:
        evidence["status"] = "missing_capture"
        return evidence
    first = captures[0]
    if not isinstance(first, dict) or not first.get("screenshot"):
        evidence["status"] = "missing_screenshot"
        return evidence
    screenshot = Path(str(first["screenshot"]))
    evidence["screenshot"] = str(screenshot)
    if not screenshot.is_file():
        evidence["status"] = "missing_screenshot_file"
        return evidence
    capture_frame = parse_number(first.get("renderedFrameIndex"))
    try:
        from PIL import Image
    except Exception as exc:  # pragma: no cover
        evidence["status"] = f"pillow_unavailable:{exc}"
        return evidence
    try:
        with Image.open(screenshot) as image:
            rgb = image.convert("RGB")
            validated: list[int] = []
            crops: list[dict[str, object]] = []
            total_matching = 0
            for texture_id in expected_texture_ids:
                frame_filtered_particles = particles
                if capture_frame is not None:
                    exact_or_near = [
                        particle for particle in particles
                        if isinstance(particle, dict)
                        and particle.get("frameIndex") is not None
                        and abs(int(particle.get("frameIndex") or -999999) - int(capture_frame)) <= 1
                    ]
                    if exact_or_near:
                        frame_filtered_particles = exact_or_near
                candidates = [
                    particle for particle in frame_filtered_particles
                    if isinstance(particle, dict)
                    and int(particle.get("textureId") or -1) == texture_id
                    and particle.get("projected") is True
                    and isinstance(particle.get("screenBounds"), dict)
                ]
                best: dict[str, object] | None = None
                for particle in candidates:
                    material_mode = int(parse_number(particle.get("materialMode")) or -1)
                    expected_material_mode = expected_terrain_particle_material_mode(texture_id)
                    bounds = particle["screenBounds"]
                    assert isinstance(bounds, dict)
                    for mirrored in (False, True):
                        crop_box = marker_crop_box(bounds, rgb.width, rgb.height, mirrored_y=mirrored)
                        if crop_box is None:
                            continue
                        crop = rgb.crop(crop_box)
                        stats = terrain_particle_crop_stats(crop, texture_id)
                        if best is None or int(stats["matching_pixels"]) > int(best["matching_pixels"]):
                            crop_path = screenshot.with_name(
                                f"world_material_terrain_particle_crop_{scenario_name}_{texture_id}_{'mirrored' if mirrored else 'projected'}.png"
                            )
                            crop.save(crop_path)
                            best = {
                                **stats,
                                "texture_id": texture_id,
                                "crop": {
                                    "left": crop_box[0],
                                    "top": crop_box[1],
                                    "right": crop_box[2],
                                    "bottom": crop_box[3],
                                    "mirrored_y": mirrored,
                                },
                                "crop_path": str(crop_path),
                                "route": particle.get("route"),
                                "center": particle.get("center"),
                                "quad_size": particle.get("quadSize"),
                                "uv": particle.get("uv"),
                                "sprite_id": particle.get("spriteId"),
                                "material_mode": material_mode,
                                "expected_material_mode": expected_material_mode,
                                "material_mode_matches": material_mode == expected_material_mode,
                            }
                if best is None:
                    crops.append({"texture_id": texture_id, "status": "missing_projected_particle"})
                    continue
                crops.append(best)
                matching = int(best["matching_pixels"])
                total_matching += matching
                if (
                    matching >= int(evidence["threshold"])
                    and best.get("material_mode_matches") is True
                ):
                    validated.append(texture_id)
            evidence.update(
                {
                    "checked": True,
                    "status": "present" if set(validated) == set(expected_texture_ids) else "absent",
                    "validated_texture_ids": validated,
                    "crops": crops,
                    "matching_pixels": total_matching,
                    "position_status": "projected_crop_hit" if validated else "missing_projected_crop_hit",
                }
            )
    except Exception as exc:
        evidence["status"] = f"read_failed:{exc}"
    return evidence


def expected_block_display_block_id(scenario_name: str) -> str | None:
    return {
        "stone": "minecraft:stone",
        "oak-leaves": "minecraft:oak_leaves",
        "cutout": "minecraft:oak_leaves",
        "tinted": "minecraft:oak_leaves",
        "asymmetric": "minecraft:furnace",
        "furnace": "minecraft:furnace",
        "non-full-cube": "minecraft:oak_stairs",
        "stairs": "minecraft:oak_stairs",
    }.get(scenario_name)


def expected_block_display_material_mode(scenario_name: str) -> int | None:
    if scenario_name in {"oak-leaves", "cutout", "tinted"}:
        return 2
    if expected_block_display_block_id(scenario_name):
        return 1
    return None


def expected_falling_block_id(scenario_name: str) -> str | None:
    return {
        "sand": "minecraft:sand",
        "gravel": "minecraft:gravel",
        "concrete-powder": "minecraft:white_concrete_powder",
        "concrete_powder": "minecraft:white_concrete_powder",
    }.get(scenario_name)


def deterministic_world_mesh_block_display_pixel_evidence(
    doc: dict[str, object] | None,
    scenario: object,
) -> dict[str, object]:
    scenario_name = str(scenario or "").strip().lower()
    evidence: dict[str, object] = {
        "checked": False,
        "status": "not_requested",
        "scenario": scenario_name or None,
        "screenshot": None,
        "displays_reported": 0,
        "expected_block_id": None,
        "validated_mesh_keys": [],
        "crops": [],
        "matching_pixels": 0,
        "texture_status": "not_checked",
        "material_status": "not_checked",
        "orientation_status": "not_checked",
        "position_status": "not_checked",
        "game_window_status": "not_checked",
        "game_window": None,
        "threshold": 96,
    }
    if not scenario_name:
        return evidence
    displays = doc.get("rustGalWorldBlockDisplays") if isinstance(doc, dict) else None
    if not isinstance(displays, list):
        displays = []
    evidence["displays_reported"] = len(displays)
    if scenario_name == "hidden":
        evidence.update(
            {
                "checked": True,
                "status": "absent_expected" if not displays else "unexpected_mesh_metadata",
                "texture_status": "absent_expected",
                "material_status": "absent_expected",
                "orientation_status": "absent_expected",
                "position_status": "absent_expected",
            }
        )
        return evidence
    expected_block = expected_block_display_block_id(scenario_name)
    expected_material_mode = expected_block_display_material_mode(scenario_name)
    evidence["expected_block_id"] = expected_block
    if not expected_block:
        evidence["status"] = "unknown_scenario"
        return evidence
    captures = doc.get("captures") if isinstance(doc, dict) else None
    if not isinstance(captures, list) or not captures:
        evidence["status"] = "missing_capture"
        return evidence
    first = captures[0]
    if not isinstance(first, dict) or not first.get("screenshot"):
        evidence["status"] = "missing_screenshot"
        return evidence
    screenshot = Path(str(first["screenshot"]))
    evidence["screenshot"] = str(screenshot)
    if not screenshot.is_file():
        evidence["status"] = "missing_screenshot_file"
        return evidence
    capture_frame = parse_number(first.get("renderedFrameIndex"))
    try:
        from PIL import Image
    except Exception as exc:  # pragma: no cover
        evidence["status"] = f"pillow_unavailable:{exc}"
        return evidence
    try:
        with Image.open(screenshot) as image:
            rgb = image.convert("RGB")
            frame_filtered_displays = displays
            if capture_frame is not None:
                exact_or_near = [
                    display for display in displays
                    if isinstance(display, dict)
                    and display.get("frameIndex") is not None
                    and abs(int(display.get("frameIndex") or -999999) - int(capture_frame)) <= 1
                ]
                if exact_or_near:
                    frame_filtered_displays = exact_or_near
            candidates = [
                display for display in frame_filtered_displays
                if isinstance(display, dict)
                and display.get("blockId") == expected_block
                and display.get("projected") is True
                and isinstance(display.get("screenBounds"), dict)
            ]
            crops: list[dict[str, object]] = []
            validated_mesh_keys: list[str] = []
            total_matching = 0
            material_ok = True
            orientation_ok = True
            rejected_candidate_count = 0
            for display in candidates[:4]:
                bounds = display["screenBounds"]
                assert isinstance(bounds, dict)
                best: dict[str, object] | None = None
                for mirrored in (False, True):
                    crop_box = marker_crop_box(bounds, rgb.width, rgb.height, mirrored_y=mirrored)
                    if crop_box is None:
                        continue
                    crop = rgb.crop(crop_box)
                    stats = block_display_crop_stats(crop, scenario_name)
                    if best is None or int(stats["matching_pixels"]) > int(best["matching_pixels"]):
                        mesh_key = str(display.get("meshKey") or "unknown")
                        crop_path = screenshot.with_name(
                            f"world_mesh_block_display_crop_{scenario_name}_{mesh_key}_{'mirrored' if mirrored else 'projected'}.png"
                        )
                        crop.save(crop_path)
                        material_mode = int(parse_number(display.get("materialMode")) or -1)
                        best = {
                            **stats,
                            "mesh_key": mesh_key,
                            "mesh_generation": display.get("meshGeneration"),
                            "block_id": display.get("blockId"),
                            "texture_ids": display.get("textureIds"),
                            "material_mode": material_mode,
                            "expected_material_mode": expected_material_mode,
                            "material_mode_matches": material_mode == expected_material_mode,
                            "index_type": display.get("indexType"),
                            "vertex_count": display.get("vertexCount"),
                            "section_count": display.get("sectionCount"),
                            "crop": {
                                "left": crop_box[0],
                                "top": crop_box[1],
                                "right": crop_box[2],
                                "bottom": crop_box[3],
                                "mirrored_y": mirrored,
                            },
                            "crop_path": str(crop_path),
                        }
                if best is None:
                    crops.append({"status": "missing_projected_display_crop", "display": display})
                    orientation_ok = False
                    continue
                crops.append(best)
                matching = int(best["matching_pixels"])
                total_matching += matching
                if (
                    matching >= int(evidence["threshold"])
                    and best.get("material_mode_matches") is True
                ):
                    validated_mesh_keys.append(str(best["mesh_key"]))
                if matching >= int(evidence["threshold"]) and best.get("material_mode_matches") is not True:
                    material_ok = False
                if matching >= int(evidence["threshold"]) and best.get("orientation_status") == "failed":
                    orientation_ok = False
            texture_ok = bool(validated_mesh_keys) and all(bool(crop.get("texture_ids")) for crop in crops if isinstance(crop, dict))
            evidence.update(
                {
                    "checked": True,
                    "status": "present" if validated_mesh_keys and material_ok and orientation_ok else "absent",
                    "validated_mesh_keys": validated_mesh_keys,
                    "crops": crops,
                    "matching_pixels": total_matching,
                    "texture_status": "passed" if texture_ok else "failed",
                    "material_status": "passed" if material_ok and validated_mesh_keys else "failed",
                    "orientation_status": "passed" if orientation_ok and validated_mesh_keys else "failed",
                    "position_status": "projected_crop_hit" if validated_mesh_keys else "missing_projected_crop_hit",
                }
            )
    except Exception as exc:
        evidence["status"] = f"read_failed:{exc}"
    return evidence


def deterministic_world_mesh_falling_block_pixel_evidence(
    doc: dict[str, object] | None,
    scenario: object,
) -> dict[str, object]:
    scenario_name = str(scenario or "").strip().lower()
    evidence: dict[str, object] = {
        "checked": False,
        "status": "not_requested",
        "scenario": scenario_name or None,
        "screenshot": None,
        "falling_blocks_reported": 0,
        "expected_block_id": None,
        "validated_mesh_keys": [],
        "crops": [],
        "matching_pixels": 0,
        "texture_status": "not_checked",
        "material_status": "not_checked",
        "orientation_status": "not_checked",
        "position_status": "not_checked",
        "game_window_status": "not_checked",
        "game_window": None,
        "frame_sequence_status": "not_checked",
        "producer_traversal_status": "not_checked",
        "setup_status": "not_checked",
        "capture_method_status": "not_checked",
        "frames_validated": 0,
        "captures_reported": 0,
        "setup": None,
        "extraction_probe": None,
        "threshold": 96,
    }
    if not scenario_name:
        return evidence
    falling_blocks = doc.get("rustGalWorldFallingBlocks") if isinstance(doc, dict) else None
    if not isinstance(falling_blocks, list):
        falling_blocks = []
    evidence["falling_blocks_reported"] = len(falling_blocks)
    if scenario_name == "hidden":
        evidence.update(
            {
                "checked": True,
                "status": "absent_expected" if not falling_blocks else "unexpected_mesh_metadata",
                "texture_status": "absent_expected",
                "material_status": "absent_expected",
                "orientation_status": "absent_expected",
                "position_status": "absent_expected",
            }
        )
        return evidence
    expected_block = expected_falling_block_id(scenario_name)
    evidence["expected_block_id"] = expected_block
    if not expected_block:
        evidence["status"] = "unknown_scenario"
        return evidence
    captures = doc.get("captures") if isinstance(doc, dict) else None
    if not isinstance(captures, list) or not captures:
        evidence["status"] = "missing_capture"
        return evidence
    evidence["captures_reported"] = len(captures)
    setup = doc.get("rustGalWorldFallingBlockSetup") if isinstance(doc, dict) else None
    extraction_probe = doc.get("rustGalWorldFallingBlockExtractionProbe") if isinstance(doc, dict) else None
    evidence["setup"] = setup if isinstance(setup, dict) else None
    evidence["extraction_probe"] = extraction_probe if isinstance(extraction_probe, dict) else None
    if not isinstance(setup, dict) or setup.get("status") != "spawned":
        evidence.update({"checked": True, "status": "missing_real_setup", "setup_status": "failed"})
        return evidence
    if setup.get("blockId") != expected_block or setup.get("spawnMethod") != "FallingBlockEntity.fall":
        evidence.update({"checked": True, "status": "invalid_real_setup", "setup_status": "failed"})
        return evidence
    evidence["setup_status"] = "passed"
    if not isinstance(extraction_probe, dict):
        evidence.update({"checked": True, "status": "missing_producer_traversal", "producer_traversal_status": "failed"})
        return evidence
    required_probe_counts = ("seen", "shouldRender", "extracted")
    missing_probe_counts = [
        name for name in required_probe_counts
        if int(parse_number(extraction_probe.get(name)) or 0) <= 0
    ]
    if missing_probe_counts:
        evidence.update(
            {
                "checked": True,
                "status": "missing_producer_traversal",
                "producer_traversal_status": "failed",
                "missing_probe_counts": missing_probe_counts,
            }
        )
        return evidence
    evidence["producer_traversal_status"] = "passed"
    falling_captures = [
        capture for capture in captures
        if isinstance(capture, dict) and str(capture.get("poseName") or "").startswith("falling-")
    ]
    if len(falling_captures) < FALLING_BLOCK_MIN_CAPTURE_FRAMES:
        evidence.update(
            {
                "checked": True,
                "status": "incomplete_frame_sequence",
                "frame_sequence_status": "failed",
                "required_capture_frames": FALLING_BLOCK_MIN_CAPTURE_FRAMES,
                "falling_capture_frames": len(falling_captures),
            }
        )
        return evidence
    try:
        from PIL import Image
    except Exception as exc:  # pragma: no cover
        evidence["status"] = f"pillow_unavailable:{exc}"
        return evidence
    crops: list[dict[str, object]] = []
    validated_mesh_keys: list[str] = []
    total_matching = 0
    material_ok = True
    rejected_candidate_count = 0
    game_windows: list[dict[str, object]] = []
    capture_methods: list[str] = []
    try:
        for capture_index, first in enumerate(falling_captures):
            if not isinstance(first, dict) or not first.get("screenshot"):
                evidence["status"] = "missing_screenshot"
                return evidence
            screenshot = Path(str(first["screenshot"]))
            evidence["screenshot"] = str(screenshot)
            if not screenshot.is_file():
                evidence["status"] = "missing_screenshot_file"
                return evidence
            capture_method = str(first.get("captureMethod") or "")
            capture_methods.append(capture_method)
            expected_capture_method = (
                "external-window-request"
                if str(first.get("backend") or "").lower() == "vulkan"
                else "internal-main-render-target"
            )
            if capture_method != expected_capture_method:
                evidence.update(
                    {
                        "checked": True,
                        "status": "wrong_capture_method",
                        "capture_method_status": "failed",
                        "expected_capture_method": expected_capture_method,
                        "capture_methods": capture_methods,
                    }
                )
                return evidence
            capture_frame = parse_number(first.get("renderedFrameIndex"))
            if capture_frame is None:
                evidence.update({"checked": True, "status": "missing_capture_frame", "frame_sequence_status": "failed"})
                return evidence
            with Image.open(screenshot) as image:
                rgb = image.convert("RGB")
            game_window = captured_game_window_evidence(rgb.size, doc, first)
            game_windows.append(game_window)
            evidence["game_window"] = game_window
            if game_window["status"] != "passed":
                evidence.update(
                    {
                        "checked": True,
                        "status": "not_game_window",
                        "game_window_status": "failed",
                        "texture_status": "failed",
                        "material_status": "failed",
                        "orientation_status": "failed",
                        "position_status": "not_game_window",
                        "game_windows": game_windows,
                    }
                )
                return evidence
            exact_or_near = [
                block for block in falling_blocks
                if isinstance(block, dict)
                and block.get("frameIndex") is not None
                and abs(int(block.get("frameIndex") or -999999) - int(capture_frame)) <= 1
            ]
            candidates = [
                block for block in exact_or_near
                if isinstance(block, dict)
                and block.get("blockId") == expected_block
                and block.get("projected") is True
                and isinstance(block.get("screenBounds"), dict)
            ]
            frame_validated = False
            if not candidates:
                crops.append(
                    {
                        "status": "missing_frame_producer_work",
                        "capture_index": capture_index,
                        "pose_name": first.get("poseName"),
                        "rendered_frame_index": capture_frame,
                    }
                )
                rejected_candidate_count += 1
                continue
            for block in candidates[:8]:
                bounds = block["screenBounds"]
                assert isinstance(bounds, dict)
                best: dict[str, object] | None = None
                for mirrored in (False, True):
                    crop_box = marker_crop_box(bounds, rgb.width, rgb.height, mirrored_y=mirrored)
                    if crop_box is None:
                        continue
                    crop = rgb.crop(crop_box)
                    stats = falling_block_crop_stats(crop, scenario_name)
                    if best is None or int(stats["matching_pixels"]) > int(best["matching_pixels"]):
                        mesh_key = str(block.get("meshKey") or "unknown")
                        crop_path = screenshot.with_name(
                            f"world_mesh_falling_block_crop_{scenario_name}_{capture_index}_{mesh_key}_{'mirrored' if mirrored else 'projected'}.png"
                        )
                        crop.save(crop_path)
                        material_mode = int(parse_number(block.get("materialMode")) or -1)
                        best = {
                            **stats,
                            "capture_index": capture_index,
                            "pose_name": first.get("poseName"),
                            "rendered_frame_index": capture_frame,
                            "mesh_key": mesh_key,
                            "mesh_generation": block.get("meshGeneration"),
                            "block_id": block.get("blockId"),
                            "texture_ids": block.get("textureIds"),
                            "material_mode": material_mode,
                            "expected_material_mode": 1,
                            "material_mode_matches": material_mode == 1,
                            "index_type": block.get("indexType"),
                            "vertex_count": block.get("vertexCount"),
                            "section_count": block.get("sectionCount"),
                            "crop": {
                                "left": crop_box[0],
                                "top": crop_box[1],
                                "right": crop_box[2],
                                "bottom": crop_box[3],
                                "mirrored_y": mirrored,
                            },
                            "crop_path": str(crop_path),
                            "full_frame_path": str(screenshot),
                        }
                if best is None:
                    crops.append({"status": "missing_projected_falling_block_crop", "falling_block": block})
                    rejected_candidate_count += 1
                    continue
                crops.append(best)
                matching = int(best["matching_pixels"])
                total_matching += matching
                if (
                    matching >= int(evidence["threshold"])
                    and best.get("material_mode_matches") is True
                    and best.get("filled_coverage_ok") is True
                ):
                    validated_mesh_keys.append(str(best["mesh_key"]))
                    frame_validated = True
                if best.get("material_mode_matches") is not True:
                    material_ok = False
                if best.get("orientation_status") == "failed":
                    rejected_candidate_count += 1
            if not frame_validated:
                rejected_candidate_count += 1
        frames_validated = len(
            {
                int(crop.get("capture_index") or -1)
                for crop in crops
                if isinstance(crop, dict)
                and int(crop.get("matching_pixels") or 0) >= int(evidence["threshold"])
                and crop.get("filled_coverage_ok") is True
                and crop.get("material_mode_matches") is True
            }
        )
        unique_validated = sorted(set(validated_mesh_keys))
        texture_ok = bool(unique_validated) and all(
            bool(crop.get("texture_ids")) for crop in crops if isinstance(crop, dict) and crop.get("mesh_key")
        )
        evidence.update(
            {
                "checked": True,
                "status": "present" if frames_validated >= FALLING_BLOCK_MIN_CAPTURE_FRAMES and material_ok else "absent",
                "validated_mesh_keys": unique_validated,
                "crops": crops,
                "rejected_candidate_count": rejected_candidate_count,
                "matching_pixels": total_matching,
                "texture_status": "passed" if texture_ok else "failed",
                "material_status": "passed" if material_ok and frames_validated >= FALLING_BLOCK_MIN_CAPTURE_FRAMES else "failed",
                "orientation_status": "passed" if frames_validated >= FALLING_BLOCK_MIN_CAPTURE_FRAMES else "failed",
                "position_status": "projected_crop_hit" if frames_validated >= FALLING_BLOCK_MIN_CAPTURE_FRAMES else "missing_projected_crop_hit",
                "game_window_status": "passed",
                "game_windows": game_windows,
                "capture_method_status": "passed",
                "capture_methods": capture_methods,
                "frame_sequence_status": "passed" if frames_validated >= FALLING_BLOCK_MIN_CAPTURE_FRAMES else "failed",
                "frames_validated": frames_validated,
                "required_capture_frames": FALLING_BLOCK_MIN_CAPTURE_FRAMES,
                "falling_capture_frames": len(falling_captures),
            }
        )
    except Exception as exc:
        evidence["status"] = f"read_failed:{exc}"
    return evidence


def expected_piston_block_ids(scenario_name: str) -> set[str]:
    normalized = scenario_name.strip().lower()
    if normalized in {"cutout", "leaves"}:
        return {"minecraft:oak_leaves"}
    if normalized in {"sticky-extending", "sticky-retracting"}:
        return {"minecraft:piston_head"}
    if normalized in {"normal-retracting", "retracting-source"}:
        return {"minecraft:piston", "minecraft:piston_head"}
    if normalized in {"hidden", "completed", "removed"}:
        return set()
    return {"minecraft:stone"}


def deterministic_world_mesh_piston_pixel_evidence(
    doc: dict[str, object] | None,
    scenario: object,
) -> dict[str, object]:
    scenario_name = str(scenario or "").strip().lower()
    expected_blocks = expected_piston_block_ids(scenario_name)
    evidence: dict[str, object] = {
        "checked": False,
        "status": "not_requested",
        "scenario": scenario_name or None,
        "screenshot": None,
        "pistons_reported": 0,
        "expected_block_ids": sorted(expected_blocks),
        "validated_mesh_keys": [],
        "validated_block_ids": [],
        "crops": [],
        "matching_pixels": 0,
        "texture_status": "not_checked",
        "material_status": "not_checked",
        "orientation_status": "not_checked",
        "position_status": "not_checked",
        "game_window_status": "not_checked",
        "game_window": None,
        "threshold": 96,
    }
    if not scenario_name:
        return evidence
    moving_blocks = doc.get("rustGalWorldMovingBlocks") if isinstance(doc, dict) else None
    if not isinstance(moving_blocks, list):
        moving_blocks = []
    pistons = [
        block for block in moving_blocks
        if isinstance(block, dict) and str(block.get("provenance") or "") == "piston"
    ]
    evidence["pistons_reported"] = len(pistons)
    if scenario_name in {"hidden", "completed", "removed"}:
        evidence.update(
            {
                "checked": True,
                "status": "absent_expected" if not pistons else "unexpected_mesh_metadata",
                "texture_status": "absent_expected",
                "material_status": "absent_expected",
                "orientation_status": "absent_expected",
                "position_status": "absent_expected",
            }
        )
        return evidence
    if not expected_blocks:
        evidence["status"] = "unknown_scenario"
        return evidence
    captures = doc.get("captures") if isinstance(doc, dict) else None
    if not isinstance(captures, list) or not captures:
        evidence["status"] = "missing_capture"
        return evidence
    first = captures[0]
    if not isinstance(first, dict) or not first.get("screenshot"):
        evidence["status"] = "missing_screenshot"
        return evidence
    screenshot = Path(str(first["screenshot"]))
    evidence["screenshot"] = str(screenshot)
    if not screenshot.is_file():
        evidence["status"] = "missing_screenshot_file"
        return evidence
    capture_frame = parse_number(first.get("renderedFrameIndex"))
    try:
        from PIL import Image
    except Exception as exc:  # pragma: no cover
        evidence["status"] = f"pillow_unavailable:{exc}"
        return evidence
    try:
        with Image.open(screenshot) as image:
            rgb = image.convert("RGB")
            game_window = captured_game_window_evidence(rgb.size, doc, first)
            evidence["game_window"] = game_window
            if game_window["status"] != "passed":
                evidence.update(
                    {
                        "checked": True,
                        "status": "not_game_window",
                        "game_window_status": "failed",
                        "texture_status": "failed",
                        "material_status": "failed",
                        "orientation_status": "failed",
                        "position_status": "not_game_window",
                    }
                )
                return evidence
            frame_filtered = pistons
            if capture_frame is not None:
                exact_or_near = [
                    block for block in pistons
                    if isinstance(block, dict)
                    and block.get("frameIndex") is not None
                    and abs(int(block.get("frameIndex") or -999999) - int(capture_frame)) <= 1
                ]
                if exact_or_near:
                    frame_filtered = exact_or_near
            candidates = [
                block for block in frame_filtered
                if isinstance(block, dict)
                and block.get("blockId") in expected_blocks
                and block.get("projected") is True
                and isinstance(block.get("screenBounds"), dict)
            ]
            crops: list[dict[str, object]] = []
            validated_mesh_keys: list[str] = []
            validated_block_ids: set[str] = set()
            total_matching = 0
            material_ok = True
            orientation_ok = True
            expected_material_mode = 2 if scenario_name in {"cutout", "leaves"} else 1
            stats_scenario = "oak-leaves" if scenario_name in {"cutout", "leaves"} else "stone"
            for block in candidates[:12]:
                bounds = block["screenBounds"]
                assert isinstance(bounds, dict)
                best: dict[str, object] | None = None
                for mirrored in (False, True):
                    crop_box = marker_crop_box(bounds, rgb.width, rgb.height, mirrored_y=mirrored)
                    if crop_box is None:
                        continue
                    crop = rgb.crop(crop_box)
                    stats = block_display_crop_stats(crop, stats_scenario)
                    if best is None or int(stats["matching_pixels"]) > int(best["matching_pixels"]):
                        mesh_key = str(block.get("meshKey") or "unknown")
                        block_id = str(block.get("blockId") or "unknown")
                        crop_path = screenshot.with_name(
                            f"world_mesh_piston_crop_{scenario_name}_{mesh_key}_{block_id.replace(':', '_')}_{'mirrored' if mirrored else 'projected'}.png"
                        )
                        crop.save(crop_path)
                        material_mode = int(parse_number(block.get("materialMode")) or -1)
                        matching_pixels = int(stats.get("matching_pixels") or 0)
                        if stats.get("orientation_status") == "failed" and matching_pixels >= int(evidence["threshold"]):
                            stats = {
                                **stats,
                                "orientation_status": "passed_projected_texture_footprint",
                                "coverage_ok": bool(stats.get("coverage_ok")),
                            }
                        best = {
                            **stats,
                            "mesh_key": mesh_key,
                            "mesh_generation": block.get("meshGeneration"),
                            "block_id": block_id,
                            "texture_ids": block.get("textureIds"),
                            "material_mode": material_mode,
                            "expected_material_mode": expected_material_mode,
                            "material_mode_matches": material_mode == expected_material_mode,
                            "index_type": block.get("indexType"),
                            "vertex_count": block.get("vertexCount"),
                            "section_count": block.get("sectionCount"),
                            "crop": {
                                "left": crop_box[0],
                                "top": crop_box[1],
                                "right": crop_box[2],
                                "bottom": crop_box[3],
                                "mirrored_y": mirrored,
                            },
                            "crop_path": str(crop_path),
                        }
                if best is None:
                    crops.append({"status": "missing_projected_piston_crop", "piston": block})
                    orientation_ok = False
                    continue
                crops.append(best)
                matching = int(best["matching_pixels"])
                total_matching += matching
                if matching >= int(evidence["threshold"]) and best.get("material_mode_matches") is True:
                    validated_mesh_keys.append(str(best["mesh_key"]))
                    validated_block_ids.add(str(best["block_id"]))
                if matching >= int(evidence["threshold"]) and best.get("material_mode_matches") is not True:
                    material_ok = False
                if matching >= int(evidence["threshold"]) and best.get("orientation_status") == "failed":
                    orientation_ok = False
            texture_ok = bool(validated_mesh_keys) and all(bool(crop.get("texture_ids")) for crop in crops if isinstance(crop, dict))
            expected_covered = bool(validated_block_ids & expected_blocks)
            evidence.update(
                {
                    "checked": True,
                    "status": "present" if validated_mesh_keys and material_ok and orientation_ok and expected_covered else "absent",
                    "validated_mesh_keys": validated_mesh_keys,
                    "validated_block_ids": sorted(validated_block_ids),
                    "crops": crops,
                    "matching_pixels": total_matching,
                    "texture_status": "passed" if texture_ok else "failed",
                    "material_status": "passed" if material_ok and validated_mesh_keys else "failed",
                    "orientation_status": "passed" if orientation_ok and validated_mesh_keys else "failed",
                    "position_status": "projected_crop_hit" if validated_mesh_keys else "missing_projected_crop_hit",
                    "game_window_status": "passed",
                }
            )
    except Exception as exc:
        evidence["status"] = f"read_failed:{exc}"
    return evidence


def block_display_crop_stats(crop: Any, scenario_name: str) -> dict[str, object]:
    width, height = crop.size
    grey = 0
    green = 0
    brown = 0
    dark_detail = 0
    alpha_hole_like = 0
    colored = 0
    min_x = width
    min_y = height
    max_x = -1
    max_y = -1
    for y in range(height):
        for x in range(width):
            red, green_value, blue = crop.getpixel((x, y))
            avg = (red + green_value + blue) / 3.0
            is_grey = 45 <= avg <= 210 and max(red, green_value, blue) - min(red, green_value, blue) <= 65
            is_leaf = green_value >= 45 and green_value >= red + 4 and green_value >= blue + 4
            is_brown = red >= 70 and green_value >= 35 and red >= blue + 20 and green_value >= blue
            is_dark_detail = red <= 85 and green_value <= 85 and blue <= 95
            is_visible = is_grey or is_leaf or is_brown or is_dark_detail
            if is_grey:
                grey += 1
            if is_leaf:
                green += 1
            if is_brown:
                brown += 1
            if is_dark_detail:
                dark_detail += 1
            if red <= 25 and green_value <= 25 and blue <= 25:
                alpha_hole_like += 1
            if is_visible:
                colored += 1
                min_x = min(min_x, x)
                min_y = min(min_y, y)
                max_x = max(max_x, x)
                max_y = max(max_y, y)
    if scenario_name in {"oak-leaves", "cutout", "tinted"}:
        matching = max(green, dark_detail)
        signature = "leaf-cutout-shaded" if dark_detail > green else "leaf-cutout"
        transparency_ok = alpha_hole_like < max(128, int(width * height * 0.75))
    elif scenario_name in {"asymmetric", "furnace"}:
        matching = max(grey + dark_detail, brown)
        signature = "furnace-like"
        transparency_ok = True
    elif scenario_name in {"non-full-cube", "stairs"}:
        matching = max(brown, grey)
        signature = "stairs-like"
        transparency_ok = True
    else:
        matching = grey
        signature = "stone-like"
        transparency_ok = True
    coverage_ok = (
        max_x > min_x
        and max_y > min_y
        and (max_x - min_x + 1) >= max(8, int(width * 0.25))
        and (max_y - min_y + 1) >= max(8, int(height * 0.25))
    )
    return {
        "matching_pixels": matching,
        "texture_signature": signature,
        "grey_pixels": grey,
        "green_pixels": green,
        "brown_pixels": brown,
        "dark_detail_pixels": dark_detail,
        "colored_pixels": colored,
        "alpha_hole_like_pixels": alpha_hole_like,
        "coverage_ok": coverage_ok,
        "transparency_status": "passed" if transparency_ok else "failed",
        "orientation_status": "passed" if coverage_ok and transparency_ok else "failed",
        "crop_width": width,
        "crop_height": height,
    }


def falling_block_crop_stats(crop: Any, scenario_name: str) -> dict[str, object]:
    width, height = crop.size
    sand = 0
    sand_filled = 0
    gravel = 0
    concrete = 0
    dark_detail = 0
    pack_a = 0
    pack_b = 0
    pack_a_marker = 0
    pack_b_marker = 0
    missingno = 0
    colored = 0
    filled_color = 0
    inner_filled_color = 0
    edge_filled_color = 0
    filled_red = 0
    filled_green = 0
    filled_blue = 0
    border_red = 0
    border_green = 0
    border_blue = 0
    border_count = 0
    min_x = width
    min_y = height
    max_x = -1
    max_y = -1
    edge_margin_x = max(2, int(width * 0.18))
    edge_margin_y = max(2, int(height * 0.18))
    for y in range(height):
        for x in range(width):
            red, green_value, blue = crop.getpixel((x, y))
            avg = (red + green_value + blue) / 3.0
            border_pixel = x < edge_margin_x or x >= width - edge_margin_x or y < edge_margin_y or y >= height - edge_margin_y
            if border_pixel:
                border_red += red
                border_green += green_value
                border_blue += blue
                border_count += 1
            is_sand = (
                red >= 120
                and green_value >= 105
                and blue >= 55
                and blue <= max(red, green_value) - 12
                and abs(red - green_value) <= 85
            )
            is_sand_filled = (
                red >= 88
                and green_value >= 76
                and blue >= 32
                and blue <= max(red, green_value) - 8
                and abs(red - green_value) <= 95
                and max(red, green_value) - min(red, green_value) <= 105
            )
            is_gravel = 55 <= avg <= 205 and max(red, green_value, blue) - min(red, green_value, blue) <= 70
            is_concrete = avg >= 140 and max(red, green_value, blue) - min(red, green_value, blue) <= 85
            is_dark_detail = red <= 90 and green_value <= 90 and blue <= 95
            is_pack_a = red >= 185 and green_value <= 95 and blue <= 125
            is_pack_a_marker = red >= 230 and green_value >= 230 and blue >= 230
            is_pack_a_edge = blue >= 180 and green_value <= 115 and red <= 100
            is_pack_b = green_value >= 145 and red <= 95 and blue <= 130
            is_pack_b_marker = (
                scenario_name in {"pack-b", "pack_b"}
                and red <= 35
                and green_value <= 35
                and blue <= 35
            )
            is_pack_b_edge = red >= 210 and green_value >= 165 and blue <= 90
            is_missingno = (red >= 180 and blue >= 180 and green_value <= 90) or (
                scenario_name not in {"sand", "gravel", "concrete-powder", "concrete_powder"}
                and red <= 35
                and green_value <= 35
                and blue <= 35
            )
            is_visible = (
                is_sand
                or is_gravel
                or is_concrete
                or is_dark_detail
                or is_pack_a
                or is_pack_a_marker
                or is_pack_a_edge
                or is_pack_b
                or is_pack_b_marker
                or is_pack_b_edge
                or is_missingno
            )
            if is_sand:
                sand += 1
            if is_sand_filled:
                sand_filled += 1
            if is_gravel:
                gravel += 1
            if is_concrete:
                concrete += 1
            if is_dark_detail:
                dark_detail += 1
            if is_pack_a or is_pack_a_edge or is_pack_a_marker:
                pack_a += 1
            if is_pack_b or is_pack_b_edge or is_pack_b_marker:
                pack_b += 1
            if is_pack_a_marker:
                pack_a_marker += 1
            if is_pack_b_marker:
                pack_b_marker += 1
            if is_missingno:
                missingno += 1
            is_shaded_sand_fill = (
                scenario_name not in {"gravel", "concrete-powder", "concrete_powder"}
                and is_dark_detail
            )
            material_filled = is_pack_a or is_pack_b or (
                scenario_name == "gravel" and is_gravel
            ) or (
                scenario_name in {"concrete-powder", "concrete_powder"} and is_concrete
            ) or (
                scenario_name not in {"gravel", "concrete-powder", "concrete_powder"} and (is_sand_filled or is_shaded_sand_fill)
            )
            if material_filled and not is_missingno:
                filled_color += 1
                filled_red += red
                filled_green += green_value
                filled_blue += blue
                if border_pixel:
                    edge_filled_color += 1
                else:
                    inner_filled_color += 1
            if is_visible:
                colored += 1
                min_x = min(min_x, x)
                min_y = min(min_y, y)
                max_x = max(max_x, x)
                max_y = max(max_y, y)
    if scenario_name == "gravel":
        vanilla_matching = gravel
        vanilla_signature = "gravel-like"
    elif scenario_name in {"concrete-powder", "concrete_powder"}:
        vanilla_matching = concrete
        vanilla_signature = "white-concrete-powder-like"
    else:
        vanilla_matching = max(sand, dark_detail)
        vanilla_signature = "sand-shaded" if dark_detail > sand else "sand-like"
    matching = max(vanilla_matching, pack_a, pack_b, missingno)
    if missingno >= matching and matching > 0:
        signature = "missingno"
    elif pack_a >= matching and matching > 0:
        signature = "pack-a"
    elif pack_b >= matching and matching > 0:
        signature = "pack-b"
    else:
        signature = vanilla_signature
    coverage_ok = (
        max_x > min_x
        and max_y > min_y
        and (max_x - min_x + 1) >= max(8, int(width * 0.25))
        and (max_y - min_y + 1) >= max(8, int(height * 0.25))
    )
    area = max(1, width * height)
    inner_area = max(1, (width - edge_margin_x * 2) * (height - edge_margin_y * 2))
    filled_ratio = filled_color / area
    inner_filled_ratio = inner_filled_color / inner_area
    edge_only_ratio = edge_filled_color / max(1, filled_color)
    if filled_color > 0:
        mean_red = filled_red / filled_color
        mean_green = filled_green / filled_color
        mean_blue = filled_blue / filled_color
    else:
        mean_red = mean_green = mean_blue = 0.0
    if border_count > 0:
        border_mean_red = border_red / border_count
        border_mean_green = border_green / border_count
        border_mean_blue = border_blue / border_count
    else:
        border_mean_red = border_mean_green = border_mean_blue = 0.0
    background_delta = (
        abs(mean_red - border_mean_red)
        + abs(mean_green - border_mean_green)
        + abs(mean_blue - border_mean_blue)
    ) / 3.0
    filled_coverage_ok = (
        filled_color >= max(96, int(area * 0.035))
        and inner_filled_color >= max(48, int(inner_area * 0.018))
        and edge_only_ratio <= 0.9
        and background_delta >= 10.0
    )
    return {
        "matching_pixels": matching,
        "texture_signature": signature,
        "sand_pixels": sand,
        "sand_filled_pixels": sand_filled,
        "gravel_pixels": gravel,
        "concrete_pixels": concrete,
        "dark_detail_pixels": dark_detail,
        "pack_a_pixels": pack_a,
        "pack_b_pixels": pack_b,
        "pack_a_marker_pixels": pack_a_marker,
        "pack_b_marker_pixels": pack_b_marker,
        "missingno_pixels": missingno,
        "colored_pixels": colored,
        "filled_color_pixels": filled_color,
        "inner_filled_color_pixels": inner_filled_color,
        "edge_filled_color_pixels": edge_filled_color,
        "filled_color_ratio": filled_ratio,
        "inner_filled_color_ratio": inner_filled_ratio,
        "edge_only_ratio": edge_only_ratio,
        "filled_mean_rgb": [mean_red, mean_green, mean_blue],
        "border_mean_rgb": [border_mean_red, border_mean_green, border_mean_blue],
        "background_delta": background_delta,
        "filled_coverage_ok": filled_coverage_ok,
        "coverage_ok": coverage_ok,
        "transparency_status": "not_applicable",
        "orientation_status": "passed" if coverage_ok and filled_coverage_ok else "failed",
        "crop_width": width,
        "crop_height": height,
    }


def marker_crop_box(
    bounds: dict[str, object],
    width: int,
    height: int,
    *,
    mirrored_y: bool,
) -> tuple[int, int, int, int] | None:
    try:
        left = float(bounds.get("left"))
        top = float(bounds.get("top"))
        right = float(bounds.get("right"))
        bottom = float(bounds.get("bottom"))
    except (TypeError, ValueError):
        return None
    if not all(math.isfinite(value) for value in (left, top, right, bottom)):
        return None
    if mirrored_y:
        top, bottom = height - bottom, height - top
    min_x = max(0, int(math.floor(min(left, right))) - 10)
    max_x = min(width, int(math.ceil(max(left, right))) + 10)
    min_y = max(0, int(math.floor(min(top, bottom))) - 10)
    max_y = min(height, int(math.ceil(max(top, bottom))) + 10)
    if max_x - min_x < 8 or max_y - min_y < 8:
        return None
    return (min_x, min_y, max_x, max_y)


def marker_crop_stats(crop: Any, texture_id: int) -> dict[str, object]:
    width, height = crop.size
    vanilla_barrier = 0
    vanilla_light = 0
    pack_a = 0
    pack_b = 0
    red_main_diagonal = 0
    red_anti_diagonal = 0
    pack_a_top = 0
    pack_a_left = 0
    pack_b_top = 0
    pack_b_left = 0
    pack_b_bottom = 0
    pack_b_right = 0
    colored_min_x = width
    colored_min_y = height
    colored_max_x = -1
    colored_max_y = -1
    for y in range(height):
        for x in range(width):
            red, green, blue = crop.getpixel((x, y))
            barrier_red = red >= 150 and green <= 95 and blue <= 95
            light_pixel = (red >= 170 and green >= 120 and blue <= 130) or (red >= 210 and green >= 195 and blue >= 145)
            pack_a_pixel = red >= 165 and green <= 115 and blue <= 140
            pack_a_edge = blue >= 170 and green <= 120 and red <= 90
            pack_a_marker = red >= 230 and green >= 230 and blue >= 230
            pack_b_pixel = green >= 145 and red <= 95 and blue <= 130
            pack_b_edge = red >= 210 and green >= 165 and blue <= 70
            pack_b_marker = red <= 35 and green <= 35 and blue <= 35
            if barrier_red:
                vanilla_barrier += 1
                normalized_x = x / max(1, width - 1)
                normalized_y = y / max(1, height - 1)
                if abs(normalized_x - normalized_y) < 0.14:
                    red_main_diagonal += 1
                if abs(normalized_x - (1.0 - normalized_y)) < 0.14:
                    red_anti_diagonal += 1
            if light_pixel:
                vanilla_light += 1
            if pack_a_pixel or pack_a_edge or pack_a_marker:
                pack_a += 1
                if y < height // 3 and pack_a_marker:
                    pack_a_top += 1
                if x < width // 3 and pack_a_edge:
                    pack_a_left += 1
            if pack_b_pixel or pack_b_edge or pack_b_marker:
                pack_b += 1
                if y < height // 3 and pack_b_marker:
                    pack_b_top += 1
                if x < width // 3 and pack_b_edge:
                    pack_b_left += 1
                if y >= (height * 2) // 3 and pack_b_marker:
                    pack_b_bottom += 1
                if x >= (width * 2) // 3 and pack_b_marker:
                    pack_b_right += 1
            if barrier_red or light_pixel or pack_a_pixel or pack_a_edge or pack_a_marker or pack_b_pixel or pack_b_edge or pack_b_marker:
                colored_min_x = min(colored_min_x, x)
                colored_min_y = min(colored_min_y, y)
                colored_max_x = max(colored_max_x, x)
                colored_max_y = max(colored_max_y, y)
    vanilla_expected = vanilla_barrier if texture_id == WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_BARRIER else vanilla_light
    pack_a_oriented = pack_a if pack_a_top > 0 and pack_a_left > 0 else 0
    pack_b_orientation_seen = (pack_b_top > 0 and pack_b_left > 0) or (pack_b_bottom > 0 and pack_b_right > 0)
    pack_b_oriented = pack_b if pack_b_orientation_seen else 0
    pack_b_distinctive = pack_b if pack_b >= 96 and pack_b >= vanilla_expected * 2 else 0
    matching = max(vanilla_expected, pack_a_oriented, pack_b_oriented, pack_b_distinctive)
    coverage_ok = (
        colored_max_x > colored_min_x
        and colored_max_y > colored_min_y
        and (colored_max_x - colored_min_x + 1) >= max(6, int(width * 0.35))
        and (colored_max_y - colored_min_y + 1) >= max(6, int(height * 0.35))
    )
    orientation_status = "passed"
    if pack_a_oriented >= matching and matching > 0:
        orientation_status = "passed" if pack_a_top > 0 and pack_a_left > 0 else "failed"
    elif (pack_b_oriented >= matching or pack_b_distinctive >= matching) and matching > 0:
        orientation_status = "passed" if pack_b_orientation_seen else "failed"
    elif texture_id == WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_BARRIER and vanilla_barrier > 0:
        orientation_status = "passed" if red_main_diagonal >= max(4, red_anti_diagonal // 2) else "failed"
    if not coverage_ok:
        orientation_status = "failed"
    return {
        "matching_pixels": matching,
        "vanilla_barrier_pixels": vanilla_barrier,
        "vanilla_light_pixels": vanilla_light,
        "pack_a_pixels": pack_a,
        "pack_b_pixels": pack_b,
        "coverage_ok": coverage_ok,
        "orientation_status": orientation_status,
    }


def terrain_particle_crop_stats(crop: Any, texture_id: int) -> dict[str, object]:
    width, height = crop.size
    vanilla_matching = 0
    pack_a = 0
    pack_b = 0
    colored = 0
    for y in range(height):
        for x in range(width):
            red, green, blue = crop.getpixel((x, y))
            avg = (red + green + blue) / 3.0
            is_vanilla_match = False
            if texture_id == WORLD_MATERIAL_TEXTURE_STONE:
                is_vanilla_match = 45 <= avg <= 190 and max(red, green, blue) - min(red, green, blue) <= 55
            elif texture_id == WORLD_MATERIAL_TEXTURE_DIRT:
                is_vanilla_match = red >= 55 and green >= 35 and blue <= 120 and red >= blue + 12 and green >= blue
            elif texture_id == WORLD_MATERIAL_TEXTURE_OAK_LEAVES:
                is_vanilla_match = green >= 45 and green >= red and green >= blue
            elif texture_id == WORLD_MATERIAL_TEXTURE_DEEPSLATE:
                is_vanilla_match = 25 <= avg <= 125 and max(red, green, blue) - min(red, green, blue) <= 65
            elif texture_id == WORLD_MATERIAL_TEXTURE_WHITE_WOOL:
                is_vanilla_match = red >= 120 and green >= 120 and blue >= 110 and max(red, green, blue) - min(red, green, blue) <= 85
            pack_a_pixel = red >= 95 and red >= green + 25 and red >= blue + 20
            pack_b_pixel = green >= 55 and green >= red + 22 and green >= blue + 12
            if red > 12 or green > 12 or blue > 12:
                colored += 1
            if is_vanilla_match:
                vanilla_matching += 1
            if pack_a_pixel:
                pack_a += 1
            if pack_b_pixel:
                pack_b += 1
    matching = max(vanilla_matching, pack_a, pack_b)
    if pack_a >= matching and matching > 0:
        signature = "pack-a"
    elif pack_b >= matching and matching > 0:
        signature = "pack-b"
    elif vanilla_matching > 0:
        signature = "vanilla-like"
    else:
        signature = "unknown"
    return {
        "matching_pixels": matching,
        "vanilla_matching_pixels": vanilla_matching,
        "pack_a_pixels": pack_a,
        "pack_b_pixels": pack_b,
        "texture_signature": signature,
        "colored_pixels": colored,
        "crop_width": width,
        "crop_height": height,
    }


def world_crack_framebuffer_evidence(combined_logs: str) -> dict[str, object]:
    records: list[dict[str, object]] = []
    pattern = re.compile(
        r"block-crack framebuffer stage=([a-zA-Z0-9_.-]+) route=([a-zA-Z0-9_.-]+) pos=(.*?) "
        r"stageIndex=(\d+) faceCount=(\d+) crop=([^ ]+) drawFb=(\d+) readFb=(\d+) program=(\d+) "
        r"viewport=([^ ]+) depthTest=(true|false) blend=(true|false) scissor=(true|false) "
        r"changedFromBefore=(\d+) maxDeltaFromBefore=(\d+) sumDeltaFromBefore=(\d+) "
        r"changedFromAfterDraw=(\d+) maxDeltaFromAfterDraw=(\d+) sumDeltaFromAfterDraw=(\d+) "
        r"darkenedFootprintPixels=(\d+) brightenedFootprintPixels=(\d+)"
    )
    for match in pattern.finditer(combined_logs):
        (
            stage,
            route,
            pos,
            stage_index,
            face_count,
            crop,
            draw_fb,
            read_fb,
            program,
            viewport,
            depth_test,
            blend,
            scissor,
            changed_before,
            max_before,
            sum_before,
            changed_after,
            max_after,
            sum_after,
            darkened,
            brightened,
        ) = match.groups()
        records.append(
            {
                "stage": stage,
                "route": route,
                "pos": pos,
                "stage_index": int(stage_index),
                "face_count": int(face_count),
                "crop": crop,
                "draw_framebuffer": int(draw_fb),
                "read_framebuffer": int(read_fb),
                "program": int(program),
                "viewport": viewport,
                "depth_test": depth_test == "true",
                "blend": blend == "true",
                "scissor": scissor == "true",
                "changed_from_before": int(changed_before),
                "max_delta_from_before": int(max_before),
                "sum_delta_from_before": int(sum_before),
                "changed_from_after_draw": int(changed_after),
                "max_delta_from_after_draw": int(max_after),
                "sum_delta_from_after_draw": int(sum_after),
                "darkened_footprint_pixels": int(darkened),
                "brightened_footprint_pixels": int(brightened),
            }
        )
    after_draw = [record for record in records if record["stage"] == "after-draw"]
    final = [record for record in records if record["stage"] in {"after-iris-final", "final"}]
    darkened_after = max((int(record["darkened_footprint_pixels"]) for record in after_draw), default=0)
    darkened_final = max((int(record["darkened_footprint_pixels"]) for record in final), default=0)
    stages = sorted({int(record["stage_index"]) for record in after_draw})
    status = "not_checked"
    if not records:
        status = "missing"
    elif not after_draw:
        status = "missing_after_draw"
    elif darkened_after <= 0:
        status = "no_projected_footprint_delta"
    elif final and darkened_final <= 0:
        status = "not_visible_final"
    else:
        status = "present"
    return {
        "checked": bool(records),
        "status": status,
        "records": records[:12],
        "after_draw_records": len(after_draw),
        "final_records": len(final),
        "stages": stages,
        "min_stage": min(stages) if stages else None,
        "max_stage": max(stages) if stages else None,
        "darkened_after_draw_pixels": darkened_after,
        "darkened_final_pixels": darkened_final,
        "depth_test_after_draw": any(bool(record["depth_test"]) for record in after_draw),
        "blend_after_draw": any(bool(record["blend"]) for record in after_draw),
    }


def world_outline_framebuffer_evidence(combined_logs: str) -> dict[str, object]:
    records: list[dict[str, object]] = []
    pattern = re.compile(
        r"block-outline framebuffer stage=([a-zA-Z0-9_.-]+) route=([a-zA-Z0-9_.-]+) pos=(.*?) "
        r"highContrast=(true|false) translucentPass=(true|false) crop=([^ ]+) "
        r"drawFb=(\d+) readFb=(\d+) program=(\d+) viewport=([^ ]+) depthTest=(true|false) "
        r"blend=(true|false) scissor=(true|false) changedFromBefore=(\d+) maxDeltaFromBefore=(\d+) "
        r"sumDeltaFromBefore=(\d+) changedFromAfterDraw=(\d+) maxDeltaFromAfterDraw=(\d+) "
        r"sumDeltaFromAfterDraw=(\d+) "
        r"(?:edgeSamples=visibleTotal=(\d+),visibleChanged=(\d+),hiddenTotal=(\d+),hiddenChanged=(\d+) )?"
        r".*?outlinePixels=cyan=(\d+),black=(\d+),normalDark=(\d+),greenBlue=(\d+)"
    )
    for match in pattern.finditer(combined_logs):
        (
            stage,
            route,
            pos,
            high_contrast,
            translucent_pass,
            crop,
            draw_fb,
            read_fb,
            program,
            viewport,
            depth_test,
            blend,
            scissor,
            changed_before,
            max_before,
            sum_before,
            changed_after,
            max_after,
            sum_after,
            visible_total,
            visible_changed,
            hidden_total,
            hidden_changed,
            cyan,
            black,
            normal_dark,
            green_blue,
        ) = match.groups()
        records.append(
            {
                "stage": stage,
                "route": route,
                "pos": pos,
                "high_contrast": high_contrast == "true",
                "translucent_pass": translucent_pass == "true",
                "crop": crop,
                "draw_framebuffer": int(draw_fb),
                "read_framebuffer": int(read_fb),
                "program": int(program),
                "viewport": viewport,
                "depth_test": depth_test == "true",
                "blend": blend == "true",
                "scissor": scissor == "true",
                "changed_from_before": int(changed_before),
                "max_delta_from_before": int(max_before),
                "sum_delta_from_before": int(sum_before),
                "changed_from_after_draw": int(changed_after),
                "max_delta_from_after_draw": int(max_after),
                "sum_delta_from_after_draw": int(sum_after),
                "edge_samples": {
                    "visible_total": int(visible_total or 0),
                    "visible_changed": int(visible_changed or 0),
                    "hidden_total": int(hidden_total or 0),
                    "hidden_changed": int(hidden_changed or 0),
                    "available": visible_total is not None,
                },
                "outline_pixels": {
                    "cyan": int(cyan),
                    "black": int(black),
                    "normal_dark": int(normal_dark),
                    "green_blue": int(green_blue),
                },
            }
        )
    after_draw = [record for record in records if record["stage"] == "after-draw"]
    final = [record for record in records if record["stage"] in {"after-iris-final", "final"}]
    changed_after_draw = max((int(record["changed_from_before"]) for record in after_draw), default=0)
    final_changed_from_after = max((int(record["changed_from_after_draw"]) for record in final), default=0)
    visible_total = max((int(record["edge_samples"]["visible_total"]) for record in after_draw), default=0)
    visible_changed = max((int(record["edge_samples"]["visible_changed"]) for record in after_draw), default=0)
    hidden_total = max((int(record["edge_samples"]["hidden_total"]) for record in after_draw), default=0)
    hidden_changed = max((int(record["edge_samples"]["hidden_changed"]) for record in after_draw), default=0)
    final_visible_changed = max((int(record["edge_samples"]["visible_changed"]) for record in final), default=0)
    final_hidden_changed = max((int(record["edge_samples"]["hidden_changed"]) for record in final), default=0)
    edge_samples_available = any(bool(record["edge_samples"]["available"]) for record in records)
    status = "not_checked"
    if not records:
        status = "missing"
    elif not after_draw:
        status = "missing_after_draw"
    elif changed_after_draw <= 0:
        status = "no_visible_edge_delta"
    elif edge_samples_available and visible_total > 0 and visible_changed <= 0:
        status = "no_projected_visible_edge_delta"
    elif edge_samples_available and hidden_total > 0 and hidden_changed > 0:
        status = "hidden_edge_depth_failed"
    else:
        status = "present_projected_edges" if edge_samples_available else "present"
    return {
        "checked": bool(records),
        "status": status,
        "records": records[:12],
        "after_draw_records": len(after_draw),
        "final_records": len(final),
        "changed_after_draw_pixels": changed_after_draw,
        "final_changed_from_after_draw_pixels": final_changed_from_after,
        "edge_samples_available": edge_samples_available,
        "visible_edge_samples": visible_total,
        "visible_edge_changed_after_draw": visible_changed,
        "hidden_edge_samples": hidden_total,
        "hidden_edge_changed_after_draw": hidden_changed,
        "visible_edge_changed_final": final_visible_changed,
        "hidden_edge_changed_final": final_hidden_changed,
        "depth_test_after_draw": any(bool(record["depth_test"]) for record in after_draw),
        "draw_framebuffers": sorted({int(record["draw_framebuffer"]) for record in records}),
        "read_framebuffers": sorted({int(record["read_framebuffer"]) for record in records}),
    }


def deterministic_rust_vulkan_shell_scene_evidence(
    doc: dict[str, object] | None,
    background_scenario: object,
    background_color_argb: object,
) -> dict[str, object]:
    scenario_name = str(background_scenario or "").strip().lower()
    evidence: dict[str, object] = {
        "checked": False,
        "status": "not_requested",
        "scenario": scenario_name or None,
        "screenshot": None,
        "regions": {},
    }
    captures = doc.get("captures") if isinstance(doc, dict) else None
    if not isinstance(captures, list) or not captures:
        evidence["status"] = "missing_capture"
        return evidence
    first = captures[0]
    if not isinstance(first, dict) or not first.get("screenshot"):
        evidence["status"] = "missing_screenshot"
        return evidence
    screenshot = Path(str(first["screenshot"]))
    evidence["screenshot"] = str(screenshot)
    if not screenshot.is_file():
        evidence["status"] = "missing_screenshot_file"
        return evidence
    try:
        from PIL import Image
    except Exception as exc:  # pragma: no cover - depends on local test environment packaging.
        evidence["status"] = f"pillow_unavailable:{exc}"
        return evidence

    def expected_background_rgb() -> tuple[int, int, int] | None:
        text = str(background_color_argb or "").strip()
        if len(text) == 8:
            try:
                value = int(text, 16)
                return ((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF)
            except ValueError:
                return None
        if scenario_name in {"hidden", "invalid"}:
            return (16, 40, 218)
        return None

    def clamp_crop(width: int, height: int, rect: tuple[float, float, float, float]) -> tuple[int, int, int, int]:
        left = max(0, min(width - 1, int(width * rect[0])))
        top = max(0, min(height - 1, int(height * rect[1])))
        right = max(left + 1, min(width, int(width * rect[2])))
        bottom = max(top + 1, min(height, int(height * rect[3])))
        return left, top, right, bottom

    def region_summary(image: object, expected_rgb: tuple[int, int, int] | None) -> dict[str, object]:
        data = list(image.getdata())
        total = max(1, len(data))
        average = tuple(int(sum(pixel[channel] for pixel in data) / total) for channel in range(3))
        non_background = 0
        green_lines = 0
        forcefield = 0
        dark_texture = 0
        bright_gui = 0
        for red, green, blue in data:
            if expected_rgb is not None and max(abs(red - expected_rgb[0]), abs(green - expected_rgb[1]), abs(blue - expected_rgb[2])) > 10:
                non_background += 1
            if green >= 120 and green >= red + 35 and green >= blue + 20:
                green_lines += 1
            if blue >= 120 and green >= 70 and blue >= red + 45:
                forcefield += 1
            if red < 70 and green < 70 and blue < 70:
                dark_texture += 1
            if red >= 160 or green >= 160 or blue >= 160:
                bright_gui += 1
        return {
            "average_rgb": average,
            "non_background_pixels": non_background if expected_rgb is not None else None,
            "green_line_pixels": green_lines,
            "forcefield_like_pixels": forcefield,
            "dark_texture_pixels": dark_texture,
            "bright_pixels": bright_gui,
        }

    regions = {
        "background_color_field": {
            "rect": (0.04, 0.78, 0.20, 0.93),
            "expected": "requested semantic clear color, or diagnostic blue fallback when hidden",
        },
        "large_block_outline_depth_probe": {
            "rect": (0.37, 0.30, 0.63, 0.70),
            "expected": "large outline geometry with visible and depth-overlapped line regions",
        },
        "large_crack_textured_surface": {
            "rect": (0.40, 0.34, 0.60, 0.63),
            "expected": "crack texture multiply probe on the shell geometry",
        },
        "world_border_overlay_plane": {
            "rect": (0.12, 0.24, 0.88, 0.48),
            "expected": "visible forcefield/world-border overlay stripe when the border scenario is visible",
        },
        "fixed_gui_sprite_group": {
            "rect": (0.34, 0.00, 0.66, 0.18),
            "expected": "migrated GUI sprites rendered after world-shell probes",
        },
        "alpha_multiply_overlay_blend_probes": {
            "rect": (0.28, 0.22, 0.72, 0.70),
            "expected": "alpha GUI, multiply crack, and overlay border probes preserved in one presented frame",
        },
    }
    expected_rgb = expected_background_rgb()
    try:
        with Image.open(screenshot) as image:
            rgb = image.convert("RGB")
            width, height = rgb.size
            region_results: dict[str, object] = {}
            for name, config in regions.items():
                rect = clamp_crop(width, height, config["rect"])
                crop = rgb.crop(rect)
                crop_path = screenshot.with_name(f"rust_vulkan_shell_scene_crop_{name}.png")
                crop.save(crop_path)
                region_results[name] = {
                    "crop": {"left": rect[0], "top": rect[1], "right": rect[2], "bottom": rect[3]},
                    "crop_path": str(crop_path),
                    "expected": config["expected"],
                    **region_summary(crop, expected_rgb),
                }
            evidence.update(
                {
                    "checked": True,
                    "status": "complete",
                    "expected_background_rgb": expected_rgb,
                    "regions": region_results,
                }
            )
    except Exception as exc:
        evidence["status"] = f"read_failed:{exc}"
    return evidence


def dh_state_from_text(text: str, meta: dict[str, str]) -> dict[str, object]:
    dh_enabled = bool(re.search(r"DistantHorizons|\[DH-|DH Ready|renderLods|renderDeferredLods", text, re.IGNORECASE))
    world_gen_threads = len(re.findall(r"DH-World Gen Thread\[\d+\]", text))
    runnable_world_gen_threads = len(re.findall(r"DH-World Gen Thread\[\d+\].*?\brunnable\b", text, re.IGNORECASE))
    generating = bool(
        re.search(r"Batch Chunk Generator initialized|Set world gen queue|WorldGen requiring|DH-World Gen Thread", text)
    )
    return {
        "present_or_logged": dh_enabled,
        "render_distance": meta.get("dh_render_distance") or meta.get("distant_horizons_render_distance"),
        "state": meta.get("dh_state") or ("generating" if generating else ("logged" if dh_enabled else "unknown")),
        "generating": generating,
        "world_gen_thread_mentions": world_gen_threads,
        "runnable_world_gen_thread_mentions": runnable_world_gen_threads,
    }


def readiness_state_from_text(
    tool_kind: str,
    failed_phase: str | None,
    frame_doc: dict[str, object] | None,
    deterministic_doc: dict[str, object] | None,
    combined_logs: str,
    meta: dict[str, str],
) -> dict[str, object]:
    screen_matches = re.findall(r"screen=([A-Za-z0-9_.$-]+)", combined_logs)
    overlay_matches = re.findall(r"overlay=([A-Za-z0-9_.$-]+)", combined_logs)
    last_screen = screen_matches[-1] if screen_matches else None
    last_overlay = overlay_matches[-1] if overlay_matches else None
    frame_runtime = frame_doc.get("runtimeState") if isinstance(frame_doc, dict) else None
    if isinstance(frame_runtime, dict):
        runtime_screen = frame_runtime.get("screen")
        runtime_overlay = frame_runtime.get("overlay")
        if isinstance(runtime_screen, str) and runtime_screen:
            last_screen = runtime_screen
        if isinstance(runtime_overlay, str) and runtime_overlay:
            last_overlay = runtime_overlay
    world_entered_log = bool(
        re.search(r"\bjoined the game\b|Loaded \[\d+\] waiting chunk wrappers|DH-CLIENT-CONNECT", combined_logs)
    )
    waiting_chunk_counts = [
        int(match.group(1))
        for match in re.finditer(r"Loaded \[(\d+)\] waiting chunk wrappers", combined_logs)
    ]
    deterministic_status = deterministic_doc.get("status") if isinstance(deterministic_doc, dict) else None
    dh_state = dh_state_from_text(combined_logs, meta)
    death_screen = bool(re.search(r"DeathScreen", combined_logs))
    invalid_player_state = death_screen or (
        isinstance(frame_doc, dict)
        and isinstance(frame_doc.get("lastReadinessBlocker"), str)
        and "DeathScreen" in str(frame_doc.get("lastReadinessBlocker"))
    )
    frame_sampler_complete = isinstance(frame_doc, dict) and frame_doc.get("status") == "complete" and frame_doc.get("worldEntered") is not False
    player_alive_controllable = deterministic_status == "complete" or frame_sampler_complete or (
        bool(world_entered_log or deterministic_status or (isinstance(frame_doc, dict) and frame_doc.get("worldEntered")))
        and not invalid_player_state
        and last_overlay not in {"LoadingOverlay"}
        and last_screen not in {"DeathScreen", "LevelLoadingScreen", "ProgressScreen", "GenericMessageScreen"}
    )
    required_chunks_loaded = bool(waiting_chunk_counts and waiting_chunk_counts[-1] == 0)
    deterministic_hook_reached = isinstance(deterministic_doc, dict)
    deterministic_ready = deterministic_status == "complete"
    profile_name = meta.get("world_profile") or "migration-gate"
    dh_required_for_readiness = profile_name == "stress-diagnostic" or "distant-horizons" in meta.get("deterministic_ready_families", "")
    timeout_classification = None
    if failed_phase:
        if invalid_player_state:
            timeout_classification = "invalid-player-state"
        elif not world_entered_log and not (isinstance(frame_doc, dict) and frame_doc.get("worldEntered")):
            timeout_classification = "loading"
        elif dh_required_for_readiness and dh_state.get("generating") and not deterministic_ready:
            timeout_classification = "dh-generation"
        elif tool_kind == "capture" and not deterministic_hook_reached:
            timeout_classification = "capture-hook-not-reached"
        elif tool_kind == "capture" and deterministic_status not in {None, "complete"}:
            timeout_classification = "capture-hook-incomplete"
        else:
            timeout_classification = f"{failed_phase}-timeout"
    return {
        "world_entered": bool(world_entered_log or (isinstance(frame_doc, dict) and frame_doc.get("worldEntered"))),
        "player_alive_and_controllable": player_alive_controllable,
        "required_chunks_loaded": required_chunks_loaded,
        "last_waiting_chunk_count": waiting_chunk_counts[-1] if waiting_chunk_counts else None,
        "dh": dh_state,
        "deterministic_capture_hook_reached": deterministic_hook_reached,
        "deterministic_capture_ready": deterministic_ready,
        "deterministic_status": deterministic_status,
        "last_screen": last_screen,
        "last_overlay": last_overlay,
        "invalid_player_state": invalid_player_state,
        "timeout_classification": timeout_classification,
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


WORKLOAD_COUNTER_DEFINITION_VERSION = "phase-family-v2"

WORKLOAD_PHASE_FAMILIES: dict[str, tuple[str, ...]] = {
    "sodium-terrain-setup": ("sodium.terrain.setup",),
    "sodium-terrain-draw": ("sodium.terrain.draw",),
    "dh-lod": ("distant-horizons.lod-render",),
    "dh-fade": ("distant-horizons.translucent-fade", "distant-horizons.opaque-fade"),
    "iris-shadow": ("iris.shadows",),
    "iris-deferred": ("iris.deferred-translucents",),
    "iris-composite": ("iris.composite-final", "iris.composite"),
}

WORKLOAD_RUNTIME_FAMILIES = ("entities", "gui", "loaded-chunks")

BACKEND_COUNTER_FAMILIES = (
    "passes",
    "draws",
    "multidraws",
    "uploads",
    "submitted-commands",
    "pipelines-resources",
    "transfers",
)


def workload_counter_definitions() -> dict[str, object]:
    return {
        "version": WORKLOAD_COUNTER_DEFINITION_VERSION,
        "semantic_families": {
            "sodium-terrain-setup": "measured exclusive phase count for Sodium terrain preparation",
            "sodium-terrain-draw": "measured exclusive phase count for Sodium terrain draw submission",
            "dh-lod": "measured exclusive phase count for Distant Horizons LOD rendering",
            "dh-fade": "measured exclusive phase count for Distant Horizons opaque/translucent fade rendering",
            "iris-shadow": "measured exclusive phase count for Iris shadow rendering",
            "iris-deferred": "measured exclusive phase count for Iris deferred translucent rendering",
            "iris-composite": "measured exclusive phase count for Iris final composite",
            "entities": "runtime entity count sampled from the measured gameplay window",
            "gui": "one visible GUI/HUD frame per measured gameplay frame when GUI is not hidden",
            "loaded-chunks": "runtime loaded chunk count sampled from the measured gameplay window",
        },
        "backend_command_counters": {
            "definition": "submitted-work identities grouped by backend operation; diagnostic-only and excluded from Current/Frozen workload comparability",
            "families": list(BACKEND_COUNTER_FAMILIES),
        },
    }


def phase_map(frame_doc: dict[str, object] | None, name: str) -> dict[str, object]:
    value = frame_doc.get(name) if isinstance(frame_doc, dict) else None
    return value if isinstance(value, dict) else {}


def phase_count(phases: dict[str, object], names: Sequence[str]) -> int:
    total = 0
    for name in names:
        stats = phases.get(name)
        if isinstance(stats, dict):
            total += int(parse_number(stats.get("count")) or 0)
    return total


def workload_family_record(
    *,
    count: int,
    measured_frames: float,
    source: str,
    definition: str,
) -> dict[str, object]:
    calls_per_frame = float(count) / measured_frames
    return {
        "present": count > 0,
        "count": count,
        "calls_per_frame": round(calls_per_frame, 3),
        "bucket": bucketed_rate(calls_per_frame),
        "source": source,
        "definition": definition,
        "instrumentation_version": WORKLOAD_COUNTER_DEFINITION_VERSION,
    }


def stable_workload_family_summary(frame_doc: dict[str, object] | None, combined_logs: str = "") -> dict[str, object]:
    del combined_logs
    measured = parse_number(frame_doc.get("measuredFrameCount")) if isinstance(frame_doc, dict) else None
    measured_frames = measured if measured and measured > 0 else 1.0
    exclusive_phases = phase_map(frame_doc, "exclusivePhaseNanos")
    runtime = frame_doc.get("runtimeState") if isinstance(frame_doc, dict) and isinstance(frame_doc.get("runtimeState"), dict) else {}
    families: dict[str, dict[str, object]] = {}
    for family, labels in WORKLOAD_PHASE_FAMILIES.items():
        families[family] = workload_family_record(
            count=phase_count(exclusive_phases, labels),
            measured_frames=measured_frames,
            source="exclusive-phase",
            definition="+".join(labels),
        )
    entity_count = int(parse_number(runtime.get("entityCount")) or 0) if isinstance(runtime, dict) else 0
    loaded_chunks = int(parse_number(runtime.get("loadedChunks")) or 0) if isinstance(runtime, dict) else 0
    hide_gui = bool(runtime.get("hideGui")) if isinstance(runtime, dict) else False
    gui_frames = 0 if hide_gui else int(measured_frames)
    families["entities"] = workload_family_record(
        count=entity_count,
        measured_frames=1.0,
        source="runtime-state",
        definition="runtimeState.entityCount",
    )
    families["gui"] = workload_family_record(
        count=gui_frames,
        measured_frames=measured_frames,
        source="runtime-state",
        definition="runtimeState.hideGui=false implies one visible HUD/GUI frame per measured frame",
    )
    families["loaded-chunks"] = workload_family_record(
        count=loaded_chunks,
        measured_frames=1.0,
        source="runtime-state",
        definition="runtimeState.loadedChunks",
    )
    return families


def backend_counter_family_name(raw_name: str) -> str:
    name = raw_name.lower()
    if "multidraw" in name or "multi-draw" in name or "multi_draw" in name:
        return "multidraws"
    if "draw" in name:
        return "draws"
    if "upload" in name or "copy" in name or "transfer" in name or "blit" in name:
        return "uploads"
    if "pipeline" in name or "descriptor" in name or "resource" in name:
        return "pipelines-resources"
    if "pass" in name:
        return "passes"
    if "command" in name or "submit" in name or "submission" in name:
        return "submitted-commands"
    return "submitted-commands"


def backend_work_counter_summary(frame_doc: dict[str, object] | None) -> dict[str, object]:
    counts = frame_doc.get("submittedWorkCounts") if isinstance(frame_doc, dict) and isinstance(frame_doc.get("submittedWorkCounts"), dict) else {}
    measured = parse_number(frame_doc.get("measuredFrameCount")) if isinstance(frame_doc, dict) else None
    measured_frames = measured if measured and measured > 0 else 1.0
    families: dict[str, dict[str, object]] = {
        family: {
            "present": False,
            "count": 0,
            "calls_per_frame": 0.0,
            "bucket": "zero",
            "source": "submitted-work-identity",
            "instrumentation_version": WORKLOAD_COUNTER_DEFINITION_VERSION,
            "comparable": False,
        }
        for family in BACKEND_COUNTER_FAMILIES
    }
    raw: dict[str, int] = {}
    for raw_name, raw_count in counts.items():
        count = int(parse_number(raw_count) or 0)
        raw[str(raw_name)] = count
        family = backend_counter_family_name(str(raw_name))
        record = families.setdefault(
            family,
            {
                "present": False,
                "count": 0,
                "calls_per_frame": 0.0,
                "bucket": "zero",
                "source": "submitted-work-identity",
                "instrumentation_version": WORKLOAD_COUNTER_DEFINITION_VERSION,
                "comparable": False,
            },
        )
        record["count"] = int(record["count"]) + count
        record["present"] = bool(record["present"] or count > 0)
    for record in families.values():
        calls_per_frame = float(record["count"]) / measured_frames
        record["calls_per_frame"] = round(calls_per_frame, 3)
        record["bucket"] = bucketed_rate(calls_per_frame)
    return {
        "definition": "backend submitted-work identities; diagnostic-only, not workload parity input",
        "instrumentation_version": WORKLOAD_COUNTER_DEFINITION_VERSION,
        "families": families,
        "raw": raw,
    }


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
        "workload_counter_definitions": workload_counter_definitions(),
        "workload_counter_instrumentation": {
            "expected_version": WORKLOAD_COUNTER_DEFINITION_VERSION,
            "sampler_version": frame_doc.get("workloadCounterDefinitionVersion") if isinstance(frame_doc, dict) else None,
        },
        "workload_families": stable_workload_family_summary(frame_doc, combined_logs),
        "backend_work_counters": backend_work_counter_summary(frame_doc),
    }


def instrumentation_signature(meta: dict[str, str], command: Sequence[str]) -> dict[str, object]:
    run_type = meta.get("graphics_run_type", "clean-performance")
    diagnostic_hooks = meta.get("graphics_audit_enabled", "false").lower() == "true"
    validation_profile = meta.get("validation_profile") or meta.get("validation_mode", "off")
    validation_details = validation_profile_details(validation_profile)
    return {
        "run_type": run_type,
        "performance_comparable": run_type == "clean-performance" and not diagnostic_hooks,
        "diagnostics_enabled": diagnostic_hooks or run_type != "clean-performance" or meta.get("shader_input_parity", "off") != "off",
        "diagnostic_hooks": diagnostic_hooks,
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
            "vulkan_layer_manifest": meta.get("renderdoc_vulkan_layer_manifest") or None,
        },
        "tracy": {
            "enabled": meta.get("tracy_capture", "false").lower() == "true",
            "duration_seconds": parse_number(meta.get("tracy_duration_seconds")),
            "max_size_mb": parse_number(meta.get("tracy_max_size_mb")),
        },
        "shader_input_parity": meta.get("shader_input_parity", "off"),
        "workload_counter_version": WORKLOAD_COUNTER_DEFINITION_VERSION,
        "tool_versions": tool_fingerprint(),
        "launch_command_uses_renderdoc": bool(command and Path(command[0]).name == "renderdoccmd")
        or bool(meta.get("renderdoc_wrapped_actual_game_command")),
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
    normalized.pop("backend_work_counters", None)
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
    if path.endswith(".entity_count"):
        return (16.0, 0.10)
    if path.endswith(".player_count"):
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


def world_profile_for_args(args: argparse.Namespace) -> WorldProfile:
    return WORLD_PROFILES[getattr(args, "world_profile", "migration-gate")]


def world_profile_dict(args: argparse.Namespace) -> dict[str, object]:
    profile = world_profile_for_args(args)
    return {
        "name": profile.name,
        "world": profile.world,
        "role": profile.role,
        "migration_gate_blocking": profile.migration_gate_blocking,
        "description": profile.description,
        "deterministic_ready_families": profile.deterministic_ready_families,
        "deterministic_ready_frames": profile.deterministic_ready_frames,
        "deterministic_ready_max_wait_frames": profile.deterministic_ready_max_wait_frames,
    }


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


def minimum_supported_profile(mode: ModeSpec, tool_kind: str) -> str:
    if tool_kind == "gameplay" and mode.expected_attribution == "java-vulkan":
        return "standard"
    if tool_kind == "capture" and mode.backend == "rust-vulkan":
        return "standard"
    if tool_kind == "subsystem" and mode.expected_attribution == "java-vulkan":
        return "standard"
    if tool_kind == "subsystem" and mode.target == "frozen" and mode.shaders == "on":
        return "standard"
    return "smoke"


def profile_not_supported_reason(profile: str, mode: ModeSpec, tool_kind: str) -> str | None:
    minimum = minimum_supported_profile(mode, tool_kind)
    if PROFILE_RANK[profile] >= PROFILE_RANK[minimum]:
        return None
    return (
        f"profile-not-supported: {mode.name}/{tool_kind} requires at least {minimum} "
        f"(requested {profile})"
    )


MATRIX_PROGRESS_INTERVAL_SECONDS = 5.0


def matrix_progress_enabled(args: argparse.Namespace) -> bool:
    return getattr(args, "tool", "") == "matrix"


def matrix_row_label(mode: ModeSpec, tool_kind: str, repetition: int) -> str:
    return f"{mode.name}/{tool_kind}/run-{repetition:02d}"


def emit_matrix_progress(args: argparse.Namespace, row: str, phase: str, detail: str = "") -> None:
    if not matrix_progress_enabled(args):
        return
    suffix = f" {detail}" if detail else ""
    print(f"[graphics-matrix] {utc_now()} row={row} phase={phase}{suffix}", flush=True)


def latest_capture_meta_path(capture_dir: Path) -> Path | None:
    metas = sorted(capture_dir.glob("meta_*.txt"), key=lambda path: path.stat().st_mtime if path.exists() else 0.0)
    return metas[-1] if metas else None


def live_capture_phase(capture_dir: Path, tool_kind: str) -> tuple[str, str]:
    meta_path = latest_capture_meta_path(capture_dir)
    if meta_path is None:
        return "startup", "waiting for capture metadata"
    meta = read_key_values(meta_path)
    if "exit_code" in meta:
        return "artifact-finalization", f"exit_code={meta.get('exit_code')}"
    if meta.get("deterministic_capture_complete_elapsed"):
        return "shutdown", f"deterministic_complete_elapsed={meta.get('deterministic_capture_complete_elapsed')}s"
    if meta.get("deterministic_capture_ack") or meta.get("deterministic_capture_screenshot"):
        return "measurement/capture", "deterministic screenshot acknowledged"
    if tool_kind == "gameplay":
        for path in capture_dir.glob("graphics_frame_benchmark_*.json"):
            data = read_json(path)
            measured = parse_number(data.get("measuredFrameCount")) if isinstance(data, dict) else 0
            if measured and measured > 0:
                return "measurement/capture", f"measured_frames={int(measured)}"
    if tool_kind == "subsystem" and any(capture_dir.glob("graphics_subsystem_benchmark_*.json")):
        return "measurement/capture", "subsystem artifact present"
    if meta.get("gradle_pid"):
        if meta.get("client_pid") or meta.get("dump_epoch") or meta.get("window_tree_dump"):
            return "readiness", f"client_pid={meta.get('client_pid', 'unknown')}"
        return "process-launched", f"gradle_pid={meta.get('gradle_pid')}"
    return "preflight", f"meta={meta_path.name}"


def latest_subsystem_status(capture_dir: Path) -> tuple[str | None, Path | None]:
    subsystem_paths = sorted(
        capture_dir.glob("graphics_subsystem_benchmark_*.json"),
        key=lambda path: path.stat().st_mtime if path.exists() else 0.0,
    )
    if not subsystem_paths:
        return None, None
    path = subsystem_paths[-1]
    data = read_json(path)
    status = data.get("status") if isinstance(data, dict) else None
    return (status if isinstance(status, str) else None), path


def mode_frame_count(value: int, mode: ModeSpec, args: argparse.Namespace, option_name: str) -> int:
    if option_name in getattr(args, "_provided_options", set()):
        return value
    if mode.expected_attribution == "java-vulkan":
        if option_name == "--settle-frames":
            return min(value, 40 if mode.shaders == "on" else 60)
        if option_name == "--max-settle-frames":
            return min(value, 600)
        if option_name == "--warmup-frames":
            return min(value, 10 if mode.shaders == "on" else 20)
        if option_name == "--measure-frames":
            return min(value, 40 if mode.shaders == "on" else 60)
        if option_name == "--subsystem-iterations":
            return min(value, 20)
    return value


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
        "api": summary.get("api"),
        "event_count": summary.get("event_count"),
        "draw_count": summary.get("draw_count"),
        "dispatch_count": summary.get("dispatch_count"),
        "pass_count": summary.get("pass_count"),
        "ordered_actions": summary.get("ordered_actions", []),
        "pipelines": summary.get("pipelines", []),
        "shader_identities": summary.get("shader_identities", []),
        "vertex_index_inputs": summary.get("vertex_index_inputs", []),
        "resource_bindings": summary.get("resource_bindings", []),
        "framebuffer_attachments": summary.get("framebuffer_attachments", []),
        "viewport_scissor": summary.get("viewport_scissor", []),
        "fixed_function_state": summary.get("fixed_function_state", []),
        "resource_formats": summary.get("resource_formats", []),
        "attachments": summary.get("attachments", []),
        "resource_hashes": summary.get("resource_hashes", []),
        "workload_proof": summary.get("workload_proof", {}),
        "diagnosis": summary.get("diagnosis", {}),
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
        "major_zones": summary.get("major_zones", {}),
        "zone_count": summary.get("zone_count"),
        "call_counts": summary.get("call_counts", {}),
        "ffi": summary.get("ffi", {}),
        "allocations": summary.get("allocations", {}),
        "cache_misses": summary.get("cache_misses", {}),
        "capture_complete": summary.get("capture_complete"),
        "unattributed_time": summary.get("unattributed_time"),
        "diagnosis": summary.get("diagnosis", {}),
        "captures": summary.get("captures", []),
        "role_detection": summary.get("role_detection", {}),
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
    repository_paths: dict[str, object] | None = None,
) -> dict[str, object]:
    files = load_capture_files(capture_dir)
    meta = read_key_values(files["meta"])
    shader_summary = read_key_values(files["shader_summary"])
    deterministic_doc = read_json(files["deterministic"]) if files["deterministic"] else None
    frame_doc = read_json(files["frame_benchmark"]) if files["frame_benchmark"] else None
    subsystem_doc = read_json(files["subsystem_benchmark"]) if files["subsystem_benchmark"] else None
    renderdoc_doc = read_json(files["renderdoc_summary"]) if files["renderdoc_summary"] else None
    tracy_doc = read_json(files["tracy_summary"]) if files["tracy_summary"] else None
    gameplay_attachment_dir = capture_dir / "whole_frame_gameplay_attachments"
    gameplay_attachment_manifest_path = latest_matching(gameplay_attachment_dir, "gameplay-attachments-frame-*.json") if gameplay_attachment_dir.exists() else None
    gameplay_attachment_correlation_path = latest_matching(gameplay_attachment_dir, "gameplay-correlation-frame-*.json") if gameplay_attachment_dir.exists() else None
    gameplay_attachment_doc = read_json(gameplay_attachment_manifest_path) if gameplay_attachment_manifest_path else None
    gameplay_attachment_correlation_doc = read_json(gameplay_attachment_correlation_path) if gameplay_attachment_correlation_path else None
    effective_meta = {**shader_summary, **meta}
    launch_uses_renderdoc = bool(command and Path(command[0]).name == "renderdoccmd") or bool(
        effective_meta.get("renderdoc_wrapped_actual_game_command")
    )
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
    primary_client_log = files["run_log"] or files["latest_log"] or files["latest_tail"]
    log_paths = [
        primary_client_log,
        files["shader_events"],
        files["validation_events"],
        files["crash_reports"],
        files["hs_err"],
        files["process_snapshot"],
    ]
    combined_logs = "\n".join(file_text(path) for path in log_paths)
    if isinstance(frame_doc, dict) and isinstance(frame_doc.get("rustGalSliceMetricsLine"), str):
        combined_logs = f"{combined_logs}\n{frame_doc['rustGalSliceMetricsLine']}"
    attribution = detect_attribution(mode, meta, combined_logs)
    attribution_families = implementation_attribution_families(mode, attribution, combined_logs)
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
        "world_profile": {
            "name": effective_meta.get("world_profile") or "migration-gate",
            "role": effective_meta.get("world_profile_role") or "",
            "migration_gate_blocking": effective_meta.get("migration_gate_blocking", "true").lower() == "true",
        },
        "workload_signature": signature,
        "instrumentation": instrumentation_signature(effective_meta, command),
        "capture_script": str(run_dev_capture_script(target.root)),
        "implementation": {
            "expected_base_backend": mode.expected_attribution,
            "observed_attribution": attribution,
            "families": attribution_families,
        },
        "resource_packs": {
            "scenario": meta.get("gui_resource_pack_scenario", "unset"),
            "selected": meta.get("gui_resource_pack_selected", "unset"),
            "generated": sorted(value for key, value in meta.items() if key == "gui_resource_pack_generated"),
        },
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
        and (
            not displayed_fps_required
            or frame_validity.get("displayedFpsCheckPassed") is not False
            or frame_validity.get("wallClockCheckPassed") is not False
        )
    )
    world_entered = tool_kind != "gameplay" or (
        isinstance(frame_doc, dict)
        and frame_doc.get("status") == "complete"
        and frame_doc.get("worldEntered") is not False
        and len(frame_nanos) > 0
    )
    subsystem_min_workloads = 1 if mode.backend.startswith("rust-") else 7
    subsystem_complete = tool_kind != "subsystem" or (
        isinstance(subsystem_doc, dict)
        and subsystem_doc.get("status") == "complete"
        and isinstance(subsystem_doc.get("workloads"), list)
        and len(subsystem_doc.get("workloads")) >= subsystem_min_workloads
        and all(isinstance(workload, dict) and workload.get("status") == "ok" for workload in subsystem_doc.get("workloads"))
    )
    deterministic_complete = tool_kind != "capture" or (
        isinstance(deterministic_doc, dict)
        and deterministic_doc.get("status") == "complete"
    )
    requested_world_outline_scenario = ""
    if isinstance(deterministic_doc, dict):
        requested_world_outline_scenario = str(deterministic_doc.get("rustGalWorldOutlineScenario") or "")
    if not requested_world_outline_scenario:
        requested_world_outline_scenario = parse_java_property(combined_logs, "mattmc.dev.rustGalWorldOutline.scenario") or ""
    requested_world_outline_scenario = requested_world_outline_scenario.strip()
    requested_world_outline_real_target = False
    requested_world_outline_aim_real_target = False
    if isinstance(deterministic_doc, dict):
        requested_world_outline_real_target = bool(deterministic_doc.get("blockOutlineRealTargetForced"))
        requested_world_outline_aim_real_target = bool(deterministic_doc.get("blockOutlineRealTargetAimed"))
    if not requested_world_outline_real_target:
        requested_world_outline_real_target = (
            parse_java_property(combined_logs, "mattmc.dev.deterministicCameraCapture.blockOutlineTarget") == "true"
        )
    if not requested_world_outline_aim_real_target:
        requested_world_outline_aim_real_target = (
            parse_java_property(combined_logs, "mattmc.dev.deterministicCameraCapture.blockOutlineAimTarget") == "true"
        )
    requested_world_outline_style = (
        (deterministic_doc or {}).get("rustGalWorldOutlineStyle") if isinstance(deterministic_doc, dict) else None
    )
    if not requested_world_outline_style and (requested_world_outline_real_target or requested_world_outline_aim_real_target):
        requested_world_outline_style = "high-contrast" if (
            parse_java_property(combined_logs, "mattmc.dev.deterministicCameraCapture.blockOutlineHighContrast") == "true"
        ) else "normal"
    saved_view_outline_target_required = (
        parse_java_property(combined_logs, "mattmc.dev.blockOutlineSavedViewTargetRequired") == "true"
    )
    requested_world_outline_legacy = (
        parse_java_property(combined_logs, "mattmc.dev.rustGalWorldOutline.legacyControl") == "true"
    )
    requested_world_outline_pause_parity = False
    if isinstance(deterministic_doc, dict):
        requested_world_outline_pause_parity = bool(deterministic_doc.get("blockOutlinePauseParity"))
    if not requested_world_outline_pause_parity:
        requested_world_outline_pause_parity = (
            parse_java_property(combined_logs, "mattmc.dev.deterministicCameraCapture.blockOutlinePauseParity") == "true"
        )
    requested_world_crack_scenario = parse_java_property(combined_logs, "mattmc.dev.rustGalWorldCrack.scenario")
    requested_world_crack_stage = parse_java_property(combined_logs, "mattmc.dev.rustGalWorldCrack.stage")
    requested_world_crack_real_survival = (
        parse_java_property(combined_logs, "mattmc.dev.rustGalWorldCrack.requireRealSurvivalCapture") == "true"
    )
    deterministic_real_crack = deterministic_doc if isinstance(deterministic_doc, dict) else {}
    real_world_crack_hit_type = deterministic_real_crack.get("realSurvivalCrackHitType")
    real_world_crack_target = deterministic_real_crack.get("realSurvivalCrackTarget")
    real_world_crack_direction = deterministic_real_crack.get("realSurvivalCrackDirection")
    real_world_crack_status = deterministic_real_crack.get("realSurvivalCrackStatus")
    real_world_crack_setup_block = deterministic_real_crack.get("realSurvivalCrackSetupBlock")
    real_world_crack_setup_target = deterministic_real_crack.get("realSurvivalCrackSetupTarget")
    real_world_crack_last_valid_target = deterministic_real_crack.get("realSurvivalCrackLastValidTarget")
    real_world_crack_last_valid_block_type = deterministic_real_crack.get("realSurvivalCrackLastValidBlockType")
    real_world_crack_last_rendered_target = deterministic_real_crack.get("realSurvivalCrackLastRenderedTarget")
    real_world_crack_last_rendered_block_type = deterministic_real_crack.get("realSurvivalCrackLastRenderedBlockType")
    real_world_crack_start_calls = parse_number(deterministic_real_crack.get("realSurvivalCrackStartCalls"))
    real_world_crack_continue_calls = parse_number(deterministic_real_crack.get("realSurvivalCrackContinueCalls"))
    real_world_crack_stop_calls = parse_number(deterministic_real_crack.get("realSurvivalCrackStopCalls"))
    real_world_crack_valid_block_hits = parse_number(deterministic_real_crack.get("realSurvivalCrackValidBlockHitCount"))
    real_world_crack_rendered_state_count = parse_number(deterministic_real_crack.get("realSurvivalCrackRenderedStateCount"))
    real_world_crack_min_rendered_stage = parse_number(deterministic_real_crack.get("realSurvivalCrackMinRenderedStage"))
    real_world_crack_max_rendered_stage = parse_number(deterministic_real_crack.get("realSurvivalCrackMaxRenderedStage"))
    requested_world_crack_disabled = parse_java_property(combined_logs, "mattmc.dev.rustGalWorldCrack.disabled") == "true"
    requested_world_crack_legacy = parse_java_property(combined_logs, "mattmc.dev.rustGalWorldCrack.legacyControl") == "true"
    requested_world_background_scenario = parse_java_property(combined_logs, "mattmc.dev.rustGalWorldBackground.scenario")
    requested_world_border_scenario = parse_java_property(combined_logs, "mattmc.dev.rustGalWorldBorder.scenario")
    requested_world_material_marker_scenario = parse_java_property(
        combined_logs, "mattmc.dev.rustGalWorldMaterial.blockMarkerScenario"
    )
    requested_world_material_terrain_particle_scenario = parse_java_property(
        combined_logs, "mattmc.dev.rustGalWorldMaterial.terrainParticleScenario"
    )
    requested_world_mesh_block_display_scenario = parse_java_property(
        combined_logs, "mattmc.dev.rustGalWorldMesh.blockDisplayScenario"
    )
    requested_world_mesh_block_display_workload = parse_java_property(
        combined_logs, "mattmc.dev.rustGalWorldMesh.blockDisplayWorkload"
    ) or "single"
    requested_world_mesh_block_display_disabled = (
        parse_java_property(combined_logs, "mattmc.dev.rustGalWorldBlockDisplay.disabled") == "true"
    )
    requested_world_mesh_block_display_legacy = (
        parse_java_property(combined_logs, "mattmc.dev.rustGalWorldBlockDisplay.legacyControl") == "true"
    )
    requested_world_mesh_falling_block_scenario = parse_java_property(
        combined_logs, "mattmc.dev.rustGalWorldMesh.fallingBlockScenario"
    )
    requested_world_mesh_falling_block_disabled = (
        parse_java_property(combined_logs, "mattmc.dev.rustGalWorldFallingBlock.disabled") == "true"
    )
    requested_world_mesh_falling_block_legacy = (
        parse_java_property(combined_logs, "mattmc.dev.rustGalWorldFallingBlock.legacyControl") == "true"
    )
    requested_world_mesh_piston_scenario = parse_java_property(
        combined_logs, "mattmc.dev.rustGalWorldMesh.pistonScenario"
    )
    requested_world_static_terrain_scenario = parse_java_property(
        combined_logs, "mattmc.dev.rustGalStaticTerrain.scenario"
    )
    requested_world_static_terrain_fault = parse_java_property(
        combined_logs, "mattmc.dev.rustGalStaticTerrain.fault"
    )
    requested_world_static_terrain_resource_pack_scenario = parse_java_property(
        combined_logs, "mattmc.dev.rustGalStaticTerrain.resourcePackScenario"
    ) or meta.get("world_static_terrain_resource_pack_scenario")
    requested_world_mesh_piston_disabled = (
        parse_java_property(combined_logs, "mattmc.dev.rustGalWorldPiston.disabled") == "true"
    )
    requested_world_mesh_piston_legacy = (
        parse_java_property(combined_logs, "mattmc.dev.rustGalWorldPiston.legacyControl") == "true"
    )
    if not requested_world_mesh_falling_block_scenario and isinstance(deterministic_doc, dict):
        requested_world_mesh_falling_block_scenario = str(deterministic_doc.get("rustGalWorldFallingBlockScenario") or "")
    if not requested_world_mesh_block_display_scenario and isinstance(deterministic_doc, dict):
        requested_world_mesh_block_display_scenario = str(deterministic_doc.get("rustGalWorldBlockDisplayScenario") or "")
    if not requested_world_mesh_piston_scenario and isinstance(deterministic_doc, dict):
        requested_world_mesh_piston_scenario = str(deterministic_doc.get("rustGalWorldPistonScenario") or "")
    if not requested_world_static_terrain_scenario and isinstance(deterministic_doc, dict):
        requested_world_static_terrain_scenario = str(deterministic_doc.get("rustGalStaticTerrainScenario") or "")
    block_display_doc = (
        frame_doc.get("blockDisplayScenario")
        if isinstance(frame_doc, dict) and isinstance(frame_doc.get("blockDisplayScenario"), dict)
        else {}
    )
    submitted_work_counts = (
        frame_doc.get("submittedWorkCounts")
        if isinstance(frame_doc, dict) and isinstance(frame_doc.get("submittedWorkCounts"), dict)
        else {}
    )
    static_terrain_doc = (
        deterministic_doc.get("rustGalStaticTerrainDiagnostics")
        if isinstance(deterministic_doc, dict) and isinstance(deterministic_doc.get("rustGalStaticTerrainDiagnostics"), dict)
        else {}
    )
    static_terrain_lifecycle_doc = (
        deterministic_doc.get("rustGalStaticTerrainLifecycle")
        if isinstance(deterministic_doc, dict) and isinstance(deterministic_doc.get("rustGalStaticTerrainLifecycle"), dict)
        else {}
    )
    static_terrain_frame_doc = (
        frame_doc.get("staticTerrainScenario")
        if isinstance(frame_doc, dict) and isinstance(frame_doc.get("staticTerrainScenario"), dict)
        else {}
    )
    static_terrain_performance_doc = (
        frame_doc.get("staticTerrainPerformance")
        if isinstance(frame_doc, dict) and isinstance(frame_doc.get("staticTerrainPerformance"), dict)
        else {}
    )
    static_terrain_submitted_count = int(parse_number(submitted_work_counts.get("static-terrain")) or 0)
    if static_terrain_submitted_count <= 0:
        static_source_doc = static_terrain_doc if static_terrain_doc else static_terrain_frame_doc
        if isinstance(static_source_doc, dict):
            static_terrain_submitted_count = int(parse_number(static_source_doc.get("visibleLayerSubmissions")) or 0)
    static_terrain_geometry = static_terrain_geometry_evidence(
        static_terrain_doc if static_terrain_doc else static_terrain_frame_doc
    )
    static_terrain_lifecycle = static_terrain_lifecycle_evidence(
        static_terrain_doc if static_terrain_doc else static_terrain_frame_doc,
        static_terrain_lifecycle_doc,
        requested_world_static_terrain_scenario or "",
    )
    static_terrain_fault_expectations = {
        "old-stride": "vertex_stride_invalid",
        "incorrect-vertex-stride": "vertex_stride_invalid",
        "vertex-count-exceeds-capacity": "vertex_count_exceeds_capacity",
        "out-of-range-index": "index_range_invalid",
        "index-type-invalid": "index_type_invalid",
        "incorrect-index-type": "index_type_invalid",
        "index-alignment-invalid": "index_alignment_invalid",
        "incorrect-index-alignment": "index_alignment_invalid",
        "non-finite-position": "non_finite_vertex_position",
        "section-origin-offset": "section_origin_mismatch",
        "stale-generation": "stale_generation_visible",
        "duplicate-visible-section": "duplicate_section_visible",
        "cross-world-stale-submission": "cross_world_stale_submission",
        "mesh-key-collision": "mesh_key_collision",
        "bounds-out-of-range": "geometry_out_of_bounds",
        "inverted-normal": "terrain_lighting_normal_invalid",
        "swapped-block-sky-light": "terrain_lighting_block_sky_invalid",
        "inverted-ao": "terrain_lighting_ao_invalid",
        "doubled-face-shade": "terrain_lighting_ao_invalid",
        "wrong-top-face-shade": "terrain_lighting_top_shade_invalid",
        "old-generation-after-edit": "lifecycle_old_generation_after_edit",
        "old-new-together": "lifecycle_old_new_overlap",
        "removed-section-resubmitted": "lifecycle_removed_section_resubmitted",
        "wrong-neighbor-invalidated": "lifecycle_wrong_neighbor_invalidated",
        "replacement-previous-origin": "section_origin_mismatch",
        "terrain-generation-never-quiesced": "terrain-generation-never-quiesced",
        "identical-mesh-reregistered": "identical-mesh-reregistered",
        "steady-state-upload-detected": "steady-state-upload-detected",
        "visibility-fingerprint-unstable": "visibility-fingerprint-unstable",
        "rebuild-loop-detected": "rebuild-loop-detected",
        "cache-eviction-loop": "cache-eviction-loop",
        "atlas-generation-churn": "atlas-generation-churn",
    }
    static_terrain_expected_fault = static_terrain_fault_expectations.get(
        (requested_world_static_terrain_fault or "").strip().lower()
    )
    block_display_submitted_count = int(parse_number(submitted_work_counts.get("block-display")) or 0)
    if isinstance(block_display_doc, dict):
        requested_world_mesh_block_display_workload = str(
            block_display_doc.get("workload") or requested_world_mesh_block_display_workload or "single"
        )
    world_mesh_block_display_control = "disabled" if requested_world_mesh_block_display_disabled else ("legacy" if requested_world_mesh_block_display_legacy else "rust")
    if isinstance(block_display_doc, dict) and block_display_doc.get("routeControl"):
        world_mesh_block_display_control = str(block_display_doc.get("routeControl") or world_mesh_block_display_control)
    falling_block_doc = (
        frame_doc.get("fallingBlockScenario")
        if isinstance(frame_doc, dict) and isinstance(frame_doc.get("fallingBlockScenario"), dict)
        else {}
    )
    deterministic_falling_blocks = (
        deterministic_doc.get("rustGalWorldFallingBlocks")
        if isinstance(deterministic_doc, dict) and isinstance(deterministic_doc.get("rustGalWorldFallingBlocks"), list)
        else []
    )
    deterministic_falling_block_route_decisions = (
        deterministic_doc.get("rustGalWorldFallingBlockRouteDecisions")
        if isinstance(deterministic_doc, dict)
        and isinstance(deterministic_doc.get("rustGalWorldFallingBlockRouteDecisions"), list)
        else []
    )
    falling_block_submitted_count = int(parse_number(submitted_work_counts.get("falling-block")) or 0)
    world_mesh_falling_block_control = "disabled" if requested_world_mesh_falling_block_disabled else ("legacy" if requested_world_mesh_falling_block_legacy else "rust")
    if isinstance(falling_block_doc, dict) and falling_block_doc.get("routeControl"):
        world_mesh_falling_block_control = str(falling_block_doc.get("routeControl") or world_mesh_falling_block_control)
    falling_block_route_counts = (
        falling_block_doc.get("routeCounts")
        if isinstance(falling_block_doc, dict) and isinstance(falling_block_doc.get("routeCounts"), dict)
        else {}
    )
    deterministic_falling_block_route_counts: dict[str, int] = {}
    for decision in deterministic_falling_block_route_decisions:
        if not isinstance(decision, dict):
            continue
        route = str(decision.get("route") or "").strip()
        if route:
            deterministic_falling_block_route_counts[route] = deterministic_falling_block_route_counts.get(route, 0) + 1

    def falling_block_route_count(route: str) -> int:
        return int(parse_number(falling_block_route_counts.get(route)) or 0) + deterministic_falling_block_route_counts.get(route, 0)

    falling_block_route_traversal_count = (
        sum(int(parse_number(value) or 0) for value in falling_block_route_counts.values())
        + sum(deterministic_falling_block_route_counts.values())
    )
    piston_doc = (
        frame_doc.get("pistonScenario")
        if isinstance(frame_doc, dict) and isinstance(frame_doc.get("pistonScenario"), dict)
        else {}
    )
    deterministic_moving_blocks = (
        deterministic_doc.get("rustGalWorldMovingBlocks")
        if isinstance(deterministic_doc, dict) and isinstance(deterministic_doc.get("rustGalWorldMovingBlocks"), list)
        else []
    )
    deterministic_piston_blocks = [
        block for block in deterministic_moving_blocks
        if isinstance(block, dict) and str(block.get("provenance") or "") == "piston"
    ]
    deterministic_moving_route_decisions = (
        deterministic_doc.get("rustGalWorldMovingBlockRouteDecisions")
        if isinstance(deterministic_doc, dict)
        and isinstance(deterministic_doc.get("rustGalWorldMovingBlockRouteDecisions"), list)
        else []
    )
    deterministic_piston_route_counts: dict[str, int] = {}
    for decision in deterministic_moving_route_decisions:
        if not isinstance(decision, dict) or str(decision.get("provenance") or "") != "piston":
            continue
        route = str(decision.get("route") or "").strip()
        if route:
            deterministic_piston_route_counts[route] = deterministic_piston_route_counts.get(route, 0) + 1
    piston_submitted_count = int(parse_number(submitted_work_counts.get("piston")) or 0)
    world_mesh_piston_control = "disabled" if requested_world_mesh_piston_disabled else ("legacy" if requested_world_mesh_piston_legacy else "rust")
    if isinstance(piston_doc, dict) and piston_doc.get("routeControl"):
        world_mesh_piston_control = str(piston_doc.get("routeControl") or world_mesh_piston_control)
    piston_moving_route_counts = (
        piston_doc.get("movingRouteCounts")
        if isinstance(piston_doc, dict) and isinstance(piston_doc.get("movingRouteCounts"), dict)
        else {}
    )
    piston_shell_scan = (
        piston_doc.get("shellScan")
        if isinstance(piston_doc, dict) and isinstance(piston_doc.get("shellScan"), dict)
        else {}
    )
    if not piston_shell_scan and isinstance(deterministic_doc, dict):
        deterministic_shell_scans = deterministic_doc.get("rustGalWorldMovingBlockShellScans")
        if isinstance(deterministic_shell_scans, list) and deterministic_shell_scans:
            shell_scan_elapsed_nanos = [
                int(parse_number(scan.get("elapsedNanos")) or 0)
                for scan in deterministic_shell_scans
                if isinstance(scan, dict)
            ]
            shell_scan_elapsed_nanos = [value for value in shell_scan_elapsed_nanos if value >= 0]
            shell_scan_total_nanos = sum(shell_scan_elapsed_nanos)
            shell_scan_samples = len(shell_scan_elapsed_nanos)
            piston_shell_scan = {
                "samples": len(deterministic_shell_scans),
                "fallbackSamples": sum(1 for scan in deterministic_shell_scans if isinstance(scan, dict) and scan.get("fallbackUsed")),
                "totalNanos": shell_scan_total_nanos,
                "averageNanos": (shell_scan_total_nanos / shell_scan_samples) if shell_scan_samples else 0,
                "p95Nanos": percentile([float(value) for value in shell_scan_elapsed_nanos], 95),
                "p99Nanos": percentile([float(value) for value in shell_scan_elapsed_nanos], 99),
                "maxNanos": max(shell_scan_elapsed_nanos or [0]),
                "budgetNanos": PISTON_SHELL_SCAN_BUDGET_NANOS,
                "chunksScanned": sum(int(parse_number(scan.get("chunksScanned")) or 0) for scan in deterministic_shell_scans if isinstance(scan, dict)),
                "blockEntitiesInspected": sum(int(parse_number(scan.get("blockEntitiesInspected")) or 0) for scan in deterministic_shell_scans if isinstance(scan, dict)),
                "pistonEntitiesFound": sum(int(parse_number(scan.get("pistonEntitiesFound")) or 0) for scan in deterministic_shell_scans if isinstance(scan, dict)),
                "pistonStatesExtracted": sum(int(parse_number(scan.get("pistonStatesExtracted")) or 0) for scan in deterministic_shell_scans if isinstance(scan, dict)),
            }

    def piston_route_count(route: str) -> int:
        moving_key = f"piston:{route}"
        return (
            int(parse_number(piston_moving_route_counts.get(moving_key)) or 0)
            + int(parse_number(piston_moving_route_counts.get(route)) or 0)
            + deterministic_piston_route_counts.get(route, 0)
        )

    piston_route_traversal_count = (
        sum(int(parse_number(value) or 0) for value in piston_moving_route_counts.values())
        + sum(deterministic_piston_route_counts.values())
    )
    terrain_particle_real_doc = (
        frame_doc.get("terrainParticleRealGameplay")
        if isinstance(frame_doc, dict) and isinstance(frame_doc.get("terrainParticleRealGameplay"), dict)
        else {}
    )
    requested_world_material_terrain_particle_real_gameplay = bool(terrain_particle_real_doc.get("enabled")) or (
        parse_java_property(combined_logs, "mattmc.dev.rustGalWorldMaterial.terrainParticleRealGameplay") == "true"
    )
    world_material_terrain_particle_control = str(terrain_particle_real_doc.get("routeControl") or "rust")
    terrain_particle_real_route_counts = (
        terrain_particle_real_doc.get("routeCounts")
        if isinstance(terrain_particle_real_doc.get("routeCounts"), dict)
        else {}
    )
    terrain_particle_real_route_nanos = (
        terrain_particle_real_doc.get("routeNanos")
        if isinstance(terrain_particle_real_doc.get("routeNanos"), dict)
        else {}
    )
    world_outline_pixel_evidence = deterministic_block_outline_pixel_evidence(
        deterministic_doc,
        requested_world_outline_style,
    )
    world_outline_framebuffer = world_outline_framebuffer_evidence(combined_logs)
    world_border_pixel_evidence = deterministic_world_border_pixel_evidence(
        deterministic_doc,
        requested_world_border_scenario,
    )
    world_crack_pixel_evidence = deterministic_world_crack_pixel_evidence(
        deterministic_doc,
        requested_world_crack_scenario or ("real-survival" if requested_world_crack_real_survival else ""),
    )
    world_material_marker_pixel_evidence = deterministic_world_material_marker_pixel_evidence(
        deterministic_doc,
        requested_world_material_marker_scenario,
    )
    world_material_terrain_particle_pixel_evidence = deterministic_world_material_terrain_particle_pixel_evidence(
        deterministic_doc,
        requested_world_material_terrain_particle_scenario,
    )
    world_mesh_block_display_pixel_evidence = deterministic_world_mesh_block_display_pixel_evidence(
        deterministic_doc,
        requested_world_mesh_block_display_scenario,
    )
    world_mesh_falling_block_pixel_evidence = deterministic_world_mesh_falling_block_pixel_evidence(
        deterministic_doc,
        requested_world_mesh_falling_block_scenario,
    )
    world_mesh_piston_pixel_evidence = deterministic_world_mesh_piston_pixel_evidence(
        deterministic_doc,
        requested_world_mesh_piston_scenario,
    )
    world_crack_framebuffer = world_crack_framebuffer_evidence(combined_logs)
    rust_vulkan_shell_scene_evidence = deterministic_rust_vulkan_shell_scene_evidence(
        deterministic_doc,
        requested_world_background_scenario,
        last_text(combined_logs, r"rust_gal_world_background_color_argb[=: ]+([0-9a-fA-F]+)"),
    )
    rust_gal_world_batches_for_validation = last_number(
        combined_logs, r"rust_gal_world_primitive_batches_executed[=: ]+(\d+)"
    )
    rust_gal_world_segments_for_validation = last_number(
        combined_logs, r"rust_gal_world_line_segments_executed[=: ]+(\d+)"
    )
    rust_gal_world_vertices_for_validation = last_number(
        combined_logs, r"rust_gal_world_line_vertices_executed[=: ]+(\d+)"
    )
    rust_gal_world_draws_for_validation = last_number(
        combined_logs, r"rust_gal_world_primitive_draws_executed[=: ]+(\d+)"
    )
    rust_gal_world_crack_quads_for_validation = last_number(
        combined_logs, r"rust_gal_world_crack_quads_executed[=: ]+(\d+)"
    )
    rust_gal_world_crack_batches_for_validation = last_number(
        combined_logs, r"rust_gal_world_crack_batches_executed[=: ]+(\d+)"
    )
    rust_gal_world_crack_draws_for_validation = last_number(
        combined_logs, r"rust_gal_world_crack_draws_executed[=: ]+(\d+)"
    )
    real_world_crack_states_for_validation = last_number(
        combined_logs,
        r"block-breaking crack request .*real_destroy_progress=true .*states=(\d+)",
    )
    real_world_crack_quads_for_validation = last_number(
        combined_logs,
        r"block-breaking crack request .*real_destroy_progress=true .*quads=(\d+)",
    )
    real_world_crack_first_summary = last_text(
        combined_logs,
        r"block-breaking crack request .*real_destroy_progress=true .*first=([^ ]+)",
    )
    rust_gal_world_border_quads_for_validation = last_number(
        combined_logs, r"rust_gal_world_border_quads_executed[=: ]+(\d+)"
    )
    rust_gal_world_border_batches_for_validation = last_number(
        combined_logs, r"rust_gal_world_border_batches_executed[=: ]+(\d+)"
    )
    rust_gal_world_border_draws_for_validation = last_number(
        combined_logs, r"rust_gal_world_border_draws_executed[=: ]+(\d+)"
    )
    rust_gal_world_material_quads_for_validation = last_number(
        combined_logs, r"rust_gal_world_material_quads_executed[=: ]+(\d+)"
    )
    rust_gal_world_material_batches_for_validation = last_number(
        combined_logs, r"rust_gal_world_material_batches_executed[=: ]+(\d+)"
    )
    rust_gal_world_material_draws_for_validation = last_number(
        combined_logs, r"rust_gal_world_material_draws_executed[=: ]+(\d+)"
    )
    rust_gal_world_material_marker_barrier_quads_for_validation = max_number(
        combined_logs, r"material_marker_barrier_quads[=: ]+(\d+)"
    )
    rust_gal_world_material_marker_light_quads_for_validation = max_number(
        combined_logs, r"material_marker_light_quads[=: ]+(\d+)"
    )
    rust_gal_world_material_marker_light_level_mask_for_validation = max_number(
        combined_logs, r"material_marker_light_level_mask[=: ]+(\d+)"
    )
    rust_gal_world_material_marker_last_light_level_for_validation = max_number(
        combined_logs, r"material_marker_last_light_level[=: ]+(-?\d+)"
    )
    rust_gal_world_material_marker_last_texture_id_for_validation = max_number(
        combined_logs, r"material_marker_last_texture_id[=: ]+(\d+)"
    )
    rust_gal_world_material_terrain_particle_quads_for_validation = max_number(
        combined_logs, r"material_terrain_particle_quads[=: ]+(\d+)"
    )
    rust_gal_world_material_terrain_particle_texture_mask_for_validation = max_number(
        combined_logs, r"material_terrain_particle_texture_mask[=: ]+(\d+)"
    )
    rust_gal_world_mesh_instances_for_validation = last_number(
        combined_logs, r"rust_gal_world_mesh_instances_executed[=: ]+(\d+)"
    )
    rust_gal_world_mesh_batches_for_validation = last_number(
        combined_logs, r"rust_gal_world_mesh_batches_executed[=: ]+(\d+)"
    )
    rust_gal_world_mesh_draws_for_validation = last_number(
        combined_logs, r"rust_gal_world_mesh_draws_executed[=: ]+(\d+)"
    )
    rust_gal_world_background_clears_for_validation = last_number(
        combined_logs, r"rust_gal_world_background_clears_executed[=: ]+(\d+)"
    )
    rust_gal_world_depth_creates_for_validation = last_number(
        combined_logs, r"rust_gal_world_depth_attachment_creates[=: ]+(\d+)"
    )
    rust_gal_world_depth_reuses_for_validation = last_number(
        combined_logs, r"rust_gal_world_depth_attachment_reuses[=: ]+(\d+)"
    )
    validation_messages: list[str] = []
    validation_notes: list[str] = []
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
    world_outline_workload_complete = True
    rust_shell_outline_mode = mode.backend == "rust-vulkan"
    rust_opengl_outline_mode = (
        target.role == "current"
        and mode.backend in {"opengl", "rust-opengl"}
        and not requested_world_outline_legacy
    )
    rust_outline_mode = rust_shell_outline_mode or rust_opengl_outline_mode
    java_outline_route = None
    if not rust_opengl_outline_mode and mode.backend in {"opengl", "rust-opengl"}:
        java_outline_route = "java-opengl"
    elif mode.backend == "vulkan":
        java_outline_route = "java-vulkan"
    outline_target_requested = requested_world_outline_real_target or requested_world_outline_aim_real_target or (
        bool(requested_world_outline_scenario) and requested_world_outline_scenario != "no-target"
    )
    if outline_target_requested and rust_outline_mode:
        required_outline_counts = {
            "primitive batches": rust_gal_world_batches_for_validation,
            "line segments": rust_gal_world_segments_for_validation,
            "line vertices": rust_gal_world_vertices_for_validation,
            "world draws": rust_gal_world_draws_for_validation,
            "depth attachment creates": rust_gal_world_depth_creates_for_validation,
        }
        missing_outline_counts = [
            name for name, value in required_outline_counts.items() if int(value or 0) <= 0
        ]
        if missing_outline_counts:
            world_outline_workload_complete = False
            validation_messages.append(
                "deterministic Rust-GAL world-outline target requested but no non-zero "
                + ", ".join(missing_outline_counts)
                + " evidence was captured"
            )
        if tool_kind == "capture" and rust_opengl_outline_mode:
            if world_outline_framebuffer.get("status") not in {"present", "present_projected_edges"}:
                world_outline_workload_complete = False
                validation_messages.append(
                    "Rust OpenGL block-outline capture did not prove projected visible-edge output with hidden edges preserved "
                    f"(framebuffer status={world_outline_framebuffer.get('status')}, "
                    f"changed_after_draw={world_outline_framebuffer.get('changed_after_draw_pixels')}, "
                    f"visible_edge_changed={world_outline_framebuffer.get('visible_edge_changed_after_draw')}, "
                    f"hidden_edge_changed={world_outline_framebuffer.get('hidden_edge_changed_after_draw')}, "
                    f"depth_test={world_outline_framebuffer.get('depth_test_after_draw')})"
                )
    elif outline_target_requested and java_outline_route:
        java_extract_seen = f"block-outline extract route={java_outline_route} target=true" in combined_logs
        java_draw_seen = f"block-outline draw route={java_outline_route} retained=true" in combined_logs
        if not java_extract_seen or not java_draw_seen:
            world_outline_workload_complete = False
            missing = []
            if not java_extract_seen:
                missing.append("target extraction")
            if not java_draw_seen:
                missing.append("outline draw")
            validation_messages.append(
                f"deterministic Java block-outline target requested for {java_outline_route} but missing "
                + ", ".join(missing)
                + " evidence"
            )
    elif saved_view_outline_target_required:
        block_pick_seen = "block-outline pick type=BLOCK" in combined_logs
        if rust_outline_mode:
            route = "rust-opengl" if rust_opengl_outline_mode else "rust-vulkan"
            extract_seen = f"block-outline extract route={route} target=true" in combined_logs
            draw_seen = f"block-outline draw route={route} retained=false" in combined_logs
        elif java_outline_route:
            route = java_outline_route
            extract_seen = f"block-outline extract route={route} target=true" in combined_logs
            draw_seen = f"block-outline draw route={route} retained=true" in combined_logs
        else:
            route = "unknown"
            extract_seen = False
            draw_seen = False
        if not block_pick_seen or not extract_seen or not draw_seen:
            world_outline_workload_complete = False
            missing = []
            if not block_pick_seen:
                missing.append("real BLOCK pick")
            if not extract_seen:
                missing.append("target extraction")
            if not draw_seen:
                missing.append("outline draw")
            validation_messages.append(
                f"saved-view block-outline target required for {route} but missing "
                + ", ".join(missing)
                + " evidence"
            )
    elif requested_world_outline_scenario == "no-target" and int(rust_gal_world_segments_for_validation or 0) != 0:
        world_outline_workload_complete = False
        validation_messages.append("deterministic no-target outline scenario emitted unexpected world line segments")
    elif requested_world_outline_scenario == "no-target" and java_outline_route:
        java_draw_seen = f"block-outline draw route={java_outline_route} retained=true" in combined_logs
        if java_draw_seen:
            world_outline_workload_complete = False
            validation_messages.append(f"deterministic no-target outline scenario emitted unexpected {java_outline_route} draw")
    if requested_world_outline_scenario == "no-target" and rust_opengl_outline_mode:
        unexpected_java_draw_seen = "block-outline draw route=java-opengl retained=true" in combined_logs
        if unexpected_java_draw_seen:
            world_outline_workload_complete = False
            validation_messages.append("deterministic no-target outline scenario emitted unexpected java-opengl draw")
    if (requested_world_outline_real_target or requested_world_outline_aim_real_target) and requested_world_outline_style == "high-contrast":
        if world_outline_pixel_evidence.get("status") != "present":
            world_outline_workload_complete = False
            validation_messages.append(
                "deterministic real-target high-contrast block outline did not produce visible outline-colored pixels "
                f"(pixel evidence status={world_outline_pixel_evidence.get('status')}, "
                f"matching_pixels={world_outline_pixel_evidence.get('matching_pixels')})"
            )
    if requested_world_outline_pause_parity:
        pose_sequence = deterministic_doc.get("poseSequence") if isinstance(deterministic_doc, dict) else None
        expected_pose_sequence = ["playing", "paused", "unpaused"]
        if pose_sequence != expected_pose_sequence:
            world_outline_workload_complete = False
            validation_messages.append(
                "block-outline pause parity requested but deterministic capture did not record playing/paused/unpaused poses"
            )
    world_crack_workload_complete = True
    crack_scenario = (requested_world_crack_scenario or "").strip().lower()
    rust_crack_mode = (rust_shell_outline_mode or rust_opengl_outline_mode) and not (
        requested_world_crack_disabled or requested_world_crack_legacy
    )
    if crack_scenario and rust_crack_mode and tool_kind != "subsystem":
        if crack_scenario in {"hidden", "no-target"}:
            if int(rust_gal_world_crack_quads_for_validation or 0) != 0:
                world_crack_workload_complete = False
                validation_messages.append("deterministic hidden/no-target crack scenario emitted unexpected crack quads")
        else:
            required_crack_counts = {
                "crack quads": rust_gal_world_crack_quads_for_validation,
                "crack batches": rust_gal_world_crack_batches_for_validation,
                "crack draws": rust_gal_world_crack_draws_for_validation,
                "depth attachment creates": rust_gal_world_depth_creates_for_validation,
            }
            missing_crack_counts = [
                name for name, value in required_crack_counts.items() if int(value or 0) <= 0
            ]
            if missing_crack_counts:
                world_crack_workload_complete = False
                validation_messages.append(
                    "deterministic Rust-GAL block-breaking crack scenario requested but no non-zero "
                    + ", ".join(missing_crack_counts)
                    + " evidence was captured"
                )
            if tool_kind == "capture" and world_crack_pixel_evidence.get("status") != "present":
                world_crack_workload_complete = False
                validation_messages.append(
                    "deterministic Rust-GAL block-breaking crack scenario did not produce visible crack pixels "
                    f"(pixel evidence status={world_crack_pixel_evidence.get('status')}, "
                    f"matching_pixels={world_crack_pixel_evidence.get('matching_pixels')}, "
                    f"texture_signature={world_crack_pixel_evidence.get('texture_signature')})"
                )
        if rust_opengl_outline_mode:
            unexpected_java_crack_draw_seen = "crack draw route=java-opengl retained=true" in combined_logs
            if unexpected_java_crack_draw_seen:
                world_crack_workload_complete = False
                validation_messages.append("deterministic Rust-GAL OpenGL crack scenario emitted unexpected java-opengl draw")
    if requested_world_crack_real_survival and rust_crack_mode and tool_kind != "subsystem":
        if crack_scenario:
            world_crack_workload_complete = False
            validation_messages.append("real survival crack gate cannot be satisfied by a forced crack scenario")
        required_real_crack_counts = {
            "real BLOCK hits": real_world_crack_valid_block_hits,
            "real non-air render states": real_world_crack_rendered_state_count,
            "real destroy-progress states": real_world_crack_states_for_validation,
            "real semantic crack quads": real_world_crack_quads_for_validation,
            "executed crack quads": rust_gal_world_crack_quads_for_validation,
            "executed crack batches": rust_gal_world_crack_batches_for_validation,
            "executed crack draws": rust_gal_world_crack_draws_for_validation,
        }
        missing_real_crack_counts = [
            name for name, value in required_real_crack_counts.items() if int(value or 0) <= 0
        ]
        if missing_real_crack_counts:
            world_crack_workload_complete = False
            target_detail = ""
            if real_world_crack_status or real_world_crack_hit_type:
                target_detail = (
                    f"; last real target status={real_world_crack_status or 'unknown'}"
                    f" hit={real_world_crack_hit_type or 'unknown'}"
                    f" target={real_world_crack_target or 'unknown'}"
                    f" direction={real_world_crack_direction or 'unknown'}"
                    f" start_calls={int(real_world_crack_start_calls or 0)}"
                    f" continue_calls={int(real_world_crack_continue_calls or 0)}"
                )
            validation_messages.append(
                "real survival Rust-GAL block-breaking crack capture missing non-zero "
                + ", ".join(missing_real_crack_counts)
                + " evidence"
                + target_detail
            )
        if real_world_crack_first_summary and "type_minecraft:air" in real_world_crack_first_summary:
            world_crack_workload_complete = False
            validation_messages.append(
                "real survival Rust-GAL block-breaking crack capture used stale destroy-progress for air block "
                f"({real_world_crack_first_summary})"
            )
        if real_world_crack_last_rendered_block_type == "minecraft:air" or real_world_crack_last_valid_block_type == "minecraft:air":
            world_crack_workload_complete = False
            validation_messages.append("real survival Rust-GAL block-breaking crack capture reported air as the valid/rendered block state")
        if (
            real_world_crack_min_rendered_stage is None
            or real_world_crack_max_rendered_stage is None
            or real_world_crack_min_rendered_stage > 2
            or real_world_crack_max_rendered_stage < 7
        ):
            world_crack_workload_complete = False
            validation_messages.append(
                "real survival Rust-GAL block-breaking crack capture did not span early through late destroy stages "
                f"(min_stage={real_world_crack_min_rendered_stage}, max_stage={real_world_crack_max_rendered_stage})"
            )
        if tool_kind == "capture" and world_crack_pixel_evidence.get("status") != "present":
            world_crack_workload_complete = False
            validation_messages.append(
                "real survival Rust-GAL block-breaking crack capture did not produce visible crack pixels "
                f"(pixel evidence status={world_crack_pixel_evidence.get('status')}, "
                f"matching_pixels={world_crack_pixel_evidence.get('matching_pixels')}, "
                f"texture_signature={world_crack_pixel_evidence.get('texture_signature')})"
            )
        if tool_kind == "capture" and rust_opengl_outline_mode:
            if world_crack_framebuffer.get("status") != "present":
                world_crack_workload_complete = False
                validation_messages.append(
                    "real survival Rust-GAL block-breaking crack capture did not prove projected-face framebuffer output "
                    f"(framebuffer status={world_crack_framebuffer.get('status')}, "
                    f"darkened_after_draw={world_crack_framebuffer.get('darkened_after_draw_pixels')}, "
                    f"darkened_final={world_crack_framebuffer.get('darkened_final_pixels')}, "
                    f"stages={world_crack_framebuffer.get('stages')})"
                )
            framebuffer_stages = [int(stage) for stage in world_crack_framebuffer.get("stages", [])]
            if not (
                any(stage <= 2 for stage in framebuffer_stages)
                and any(3 <= stage <= 6 for stage in framebuffer_stages)
                and any(stage >= 7 for stage in framebuffer_stages)
            ):
                world_crack_workload_complete = False
                validation_messages.append(
                    "real survival Rust-GAL block-breaking crack capture did not prove projected-face early, middle, and late stage output "
                    f"(framebuffer stages={framebuffer_stages})"
                )
    world_border_workload_complete = True
    border_scenario = (requested_world_border_scenario or "").strip().lower()
    if border_scenario and rust_shell_outline_mode and tool_kind != "subsystem":
        if border_scenario in {"hidden", "far", "no-target"}:
            if int(rust_gal_world_border_quads_for_validation or 0) != 0:
                world_border_workload_complete = False
                validation_messages.append("deterministic hidden/far world-border scenario emitted unexpected border quads")
        else:
            required_border_counts = {
                "border quads": rust_gal_world_border_quads_for_validation,
                "border batches": rust_gal_world_border_batches_for_validation,
                "border draws": rust_gal_world_border_draws_for_validation,
                "depth attachment creates": rust_gal_world_depth_creates_for_validation,
            }
            missing_border_counts = [
                name for name, value in required_border_counts.items() if int(value or 0) <= 0
            ]
            if missing_border_counts:
                world_border_workload_complete = False
                validation_messages.append(
                    "deterministic Rust-GAL world-border scenario requested but no non-zero "
                    + ", ".join(missing_border_counts)
                    + " evidence was captured"
                )
    if border_scenario and tool_kind == "capture":
        if border_scenario in {"hidden", "far", "no-target"}:
            border_quads_seen = int(rust_gal_world_border_quads_for_validation or 0)
            real_border_pixels = (
                int(world_border_pixel_evidence.get("pack_a_signature_pixels") or 0)
                + int(world_border_pixel_evidence.get("pack_b_signature_pixels") or 0)
            )
            if border_quads_seen > 0:
                real_border_pixels += int(world_border_pixel_evidence.get("vanilla_like_pixels") or 0)
            if (
                world_border_pixel_evidence.get("status") == "unexpected_present"
                and real_border_pixels >= int(world_border_pixel_evidence.get("threshold") or 0)
            ):
                world_border_workload_complete = False
                validation_messages.append(
                    "deterministic hidden/far world-border scenario produced visible border-colored pixels "
                    f"(matching_pixels={world_border_pixel_evidence.get('matching_pixels')})"
                )
        elif world_border_pixel_evidence.get("status") != "present":
            world_border_workload_complete = False
            validation_messages.append(
                "deterministic world-border scenario did not produce visible border-colored pixels "
                f"(pixel evidence status={world_border_pixel_evidence.get('status')}, "
                f"matching_pixels={world_border_pixel_evidence.get('matching_pixels')})"
            )
    world_material_marker_workload_complete = True
    material_marker_scenario = (requested_world_material_marker_scenario or "").strip().lower()
    if material_marker_scenario and rust_outline_mode and tool_kind != "subsystem":
        if material_marker_scenario == "hidden":
            if int(rust_gal_world_material_quads_for_validation or 0) != 0:
                world_material_marker_workload_complete = False
                validation_messages.append("deterministic hidden BlockMarker scenario emitted unexpected material quads")
            if world_material_marker_pixel_evidence.get("status") != "absent_expected":
                world_material_marker_workload_complete = False
                validation_messages.append(
                    "deterministic hidden BlockMarker scenario reported unexpected projected marker evidence "
                    f"(status={world_material_marker_pixel_evidence.get('status')})"
                )
        else:
            required_material_counts = {
                "material quads": rust_gal_world_material_quads_for_validation,
                "material batches": rust_gal_world_material_batches_for_validation,
                "material draws": rust_gal_world_material_draws_for_validation,
            }
            missing_material_counts = [
                name for name, value in required_material_counts.items() if int(value or 0) <= 0
            ]
            if missing_material_counts:
                world_material_marker_workload_complete = False
                validation_messages.append(
                    "deterministic Rust-GAL BlockMarker scenario requested but no non-zero "
                    + ", ".join(missing_material_counts)
                    + " evidence was captured"
                )
            if material_marker_scenario == "barrier":
                if int(rust_gal_world_material_marker_barrier_quads_for_validation or 0) <= 0:
                    world_material_marker_workload_complete = False
                    validation_messages.append("deterministic BlockMarker barrier scenario did not report barrier material quads")
            elif material_marker_scenario == "lights-all":
                if rust_gal_world_material_marker_light_level_mask_for_validation != 0xFFFF:
                    world_material_marker_workload_complete = False
                    validation_messages.append(
                        "deterministic BlockMarker all-lights scenario did not report all light levels "
                        f"(mask={rust_gal_world_material_marker_light_level_mask_for_validation})"
                    )
            elif material_marker_scenario.startswith("light-"):
                expected_light_level = parse_number(material_marker_scenario.removeprefix("light-"))
                if int(rust_gal_world_material_marker_light_quads_for_validation or 0) <= 0:
                    world_material_marker_workload_complete = False
                    validation_messages.append("deterministic BlockMarker light scenario did not report light material quads")
                expected_light_mask = None
                if expected_light_level is not None:
                    expected_light_mask = 1 << int(expected_light_level)
                if (
                    expected_light_mask is not None
                    and (
                        int(rust_gal_world_material_marker_light_level_mask_for_validation or 0)
                        & expected_light_mask
                    )
                    == 0
                ):
                    world_material_marker_workload_complete = False
                    validation_messages.append(
                        "deterministic BlockMarker light scenario reported the wrong light level "
                        f"(expected={expected_light_level}, "
                        f"mask={rust_gal_world_material_marker_light_level_mask_for_validation})"
                    )
            if world_material_marker_pixel_evidence.get("status") != "present":
                world_material_marker_workload_complete = False
                validation_messages.append(
                    "deterministic BlockMarker scenario did not produce projected marker crop evidence "
                    f"(status={world_material_marker_pixel_evidence.get('status')}, "
                    f"validated={world_material_marker_pixel_evidence.get('validated_texture_ids')}, "
                    f"expected={world_material_marker_pixel_evidence.get('expected_texture_ids')}, "
                    f"matching_pixels={world_material_marker_pixel_evidence.get('matching_pixels')})"
                )
            elif world_material_marker_pixel_evidence.get("orientation_status") != "passed":
                world_material_marker_workload_complete = False
                validation_messages.append(
                    "deterministic BlockMarker projected crop evidence did not prove orientation/coverage "
                    f"(orientation={world_material_marker_pixel_evidence.get('orientation_status')})"
                )
    world_material_terrain_particle_workload_complete = True
    terrain_particle_scenario = (requested_world_material_terrain_particle_scenario or "").strip().lower()
    if terrain_particle_scenario and rust_outline_mode and tool_kind == "capture":
        if terrain_particle_scenario == "hidden":
            if int(rust_gal_world_material_quads_for_validation or 0) != 0:
                world_material_terrain_particle_workload_complete = False
                validation_messages.append("deterministic hidden TerrainParticle scenario emitted unexpected material quads")
            if world_material_terrain_particle_pixel_evidence.get("status") != "absent_expected":
                world_material_terrain_particle_workload_complete = False
                validation_messages.append(
                    "deterministic hidden TerrainParticle scenario reported unexpected projected particle evidence "
                    f"(status={world_material_terrain_particle_pixel_evidence.get('status')})"
                )
        else:
            required_material_counts = {
                "material quads": rust_gal_world_material_quads_for_validation,
                "material batches": rust_gal_world_material_batches_for_validation,
                "material draws": rust_gal_world_material_draws_for_validation,
            }
            missing_material_counts = [
                name for name, value in required_material_counts.items() if int(value or 0) <= 0
            ]
            if missing_material_counts:
                world_material_terrain_particle_workload_complete = False
                validation_messages.append(
                    "deterministic Rust-GAL TerrainParticle scenario requested but no non-zero "
                    + ", ".join(missing_material_counts)
                    + " evidence was captured"
                )
            if world_material_terrain_particle_pixel_evidence.get("status") != "present":
                world_material_terrain_particle_workload_complete = False
                validation_messages.append(
                    "deterministic TerrainParticle scenario did not produce projected particle crop evidence "
                    f"(status={world_material_terrain_particle_pixel_evidence.get('status')}, "
                    f"validated={world_material_terrain_particle_pixel_evidence.get('validated_texture_ids')}, "
                    f"expected={world_material_terrain_particle_pixel_evidence.get('expected_texture_ids')}, "
                    f"matching_pixels={world_material_terrain_particle_pixel_evidence.get('matching_pixels')})"
                )
    if requested_world_material_terrain_particle_real_gameplay and tool_kind == "gameplay":
        real_drive_calls = parse_number(terrain_particle_real_doc.get("driveCalls"))
        real_continue_calls = parse_number(terrain_particle_real_doc.get("continueCalls"))
        real_breaking_effects = parse_number(terrain_particle_real_doc.get("breakingEffects"))
        real_material_mask = int(parse_number(terrain_particle_real_doc.get("materialMask")) or 0)
        real_expected_material_mask = int(parse_number(terrain_particle_real_doc.get("expectedMaterialMask")) or 0)
        real_effects_per_frame = int(parse_number(terrain_particle_real_doc.get("effectsPerFrame")) or 0)
        real_route_total = sum(
            int(value or 0)
            for value in terrain_particle_real_route_counts.values()
            if isinstance(value, (int, float))
        )
        if int(real_drive_calls or 0) <= 0 or int(real_continue_calls or 0) <= 0 or int(real_breaking_effects or 0) <= 0:
            world_material_terrain_particle_workload_complete = False
            validation_messages.append(
                "real gameplay TerrainParticle workload did not prove vanilla block interaction "
                f"(drive={real_drive_calls}, continue={real_continue_calls}, breakingEffects={real_breaking_effects})"
            )
        if real_route_total <= 0:
            world_material_terrain_particle_workload_complete = False
            validation_messages.append("real gameplay TerrainParticle workload produced no particle extraction route samples")
        if real_expected_material_mask and real_material_mask != real_expected_material_mask:
            world_material_terrain_particle_workload_complete = False
            validation_messages.append(
                "real gameplay TerrainParticle workload did not exercise the fixed material set "
                f"(mask={real_material_mask}, expected={real_expected_material_mask})"
            )
        if real_effects_per_frame <= 0:
            world_material_terrain_particle_workload_complete = False
            validation_messages.append("real gameplay TerrainParticle workload did not record a bounded effects-per-frame count")
        java_vulkan_compat_material = mode.expected_attribution == "java-vulkan"
        if world_material_terrain_particle_control == "rust" and java_vulkan_compat_material:
            java_route_count = int(terrain_particle_real_route_counts.get("java-compat") or 0)
            if java_route_count <= 0:
                world_material_terrain_particle_workload_complete = False
                validation_messages.append("normal Java Vulkan TerrainParticle control did not use the Java compatibility route")
            if int(rust_gal_world_material_quads_for_validation or 0) != 0:
                world_material_terrain_particle_workload_complete = False
                validation_messages.append("normal Java Vulkan TerrainParticle control emitted unexpected Rust material quads")
        elif world_material_terrain_particle_control == "rust":
            rust_route_count = int(terrain_particle_real_route_counts.get("rust") or 0)
            if rust_route_count <= 0 or int(rust_gal_world_material_quads_for_validation or 0) <= 0:
                world_material_terrain_particle_workload_complete = False
                validation_messages.append(
                    "real gameplay TerrainParticle Rust route did not produce Rust material work "
                    f"(rustRoute={rust_route_count}, materialQuads={rust_gal_world_material_quads_for_validation})"
                )
            if real_material_mask.bit_count() < 3:
                world_material_terrain_particle_workload_complete = False
                validation_messages.append(
                    f"real gameplay TerrainParticle Rust route did not exercise mixed block materials (mask={real_material_mask})"
                )
        elif world_material_terrain_particle_control == "disabled":
            if int(rust_gal_world_material_quads_for_validation or 0) != 0:
                world_material_terrain_particle_workload_complete = False
                validation_messages.append("TerrainParticle disabled control emitted unexpected Rust material quads")
        elif world_material_terrain_particle_control == "legacy":
            legacy_route_count = int(terrain_particle_real_route_counts.get("java-legacy") or 0)
            if legacy_route_count <= 0:
                world_material_terrain_particle_workload_complete = False
                validation_messages.append("TerrainParticle legacy control did not prove Java legacy particle extraction")
            if int(rust_gal_world_material_quads_for_validation or 0) != 0:
                world_material_terrain_particle_workload_complete = False
                validation_messages.append("TerrainParticle legacy control emitted unexpected Rust material quads")
    static_terrain_workload_complete = True
    static_terrain_scenario = (requested_world_static_terrain_scenario or "").strip().lower()
    if static_terrain_scenario and rust_outline_mode and tool_kind != "subsystem":
        static_doc = static_terrain_doc if static_terrain_doc else static_terrain_frame_doc

        def static_metric(name: str) -> int:
            value = static_doc.get(name) if isinstance(static_doc, dict) else None
            return int(parse_number(value) or 0)

        accepted_builds = static_metric("acceptedBuildOutputs")
        cached_layers = static_metric("cachedLayerAssets")
        registered_meshes = static_metric("registeredMeshes")
        texture_updates = static_metric("texturePayloadUpdates")
        visible_probes = static_metric("visibleLayerProbes")
        visible_submissions = static_metric("visibleLayerSubmissions")
        failed_submissions = static_metric("failedLayerSubmissions")
        if static_terrain_scenario == "hidden":
            if visible_submissions > 0 or static_terrain_submitted_count > 0:
                static_terrain_workload_complete = False
                validation_messages.append("deterministic hidden static-terrain scenario emitted unexpected visible Rust terrain work")
        else:
            if accepted_builds <= 0:
                static_terrain_workload_complete = False
                validation_messages.append("deterministic static-terrain scenario did not prove real Sodium section build ingestion")
            if cached_layers <= 0 or registered_meshes <= 0:
                static_terrain_workload_complete = False
                validation_messages.append(
                    "deterministic static-terrain scenario did not prove Rust terrain mesh asset registration "
                    f"(cached={cached_layers}, registered={registered_meshes})"
                )
            if texture_updates <= 0:
                static_terrain_workload_complete = False
                validation_messages.append("deterministic static-terrain scenario did not prove block-atlas texture ownership/update")
            if visible_probes <= 0 or visible_submissions <= 0 or static_terrain_submitted_count <= 0:
                static_terrain_workload_complete = False
                validation_messages.append(
                    "deterministic static-terrain scenario did not prove Sodium visible-list terrain submission "
                    f"(probes={visible_probes}, submissions={visible_submissions}, submittedWork={static_terrain_submitted_count})"
                )
            if failed_submissions > 0:
                static_terrain_workload_complete = False
                validation_messages.append(
                    f"deterministic static-terrain scenario recorded stale/unregistered visible terrain submissions ({failed_submissions})"
                )
            if int(rust_gal_world_mesh_instances_for_validation or 0) <= 0:
                static_terrain_workload_complete = False
                validation_messages.append("deterministic static-terrain scenario did not execute any Rust indexed-mesh terrain instances")
            deterministic_static_terrain_gate_required = tool_kind == "capture"
            if deterministic_static_terrain_gate_required and static_terrain_geometry.get("status") != "pass":
                static_terrain_workload_complete = False
                validation_messages.append(
                    "deterministic static-terrain geometry truth gate failed: "
                    f"{static_terrain_geometry.get('failure') or 'unknown'}"
                )
            if deterministic_static_terrain_gate_required and static_terrain_lifecycle.get("status") == "fail":
                static_terrain_workload_complete = False
                validation_messages.append(
                    "deterministic static-terrain lifecycle gate failed: "
                    f"{static_terrain_lifecycle.get('failure') or 'unknown'}"
                )
            if requested_world_static_terrain_fault:
                failures_for_fault = set(static_terrain_geometry.get("failures") or [])
                failures_for_fault.update(static_terrain_lifecycle.get("failures") or [])
                performance_classification = (
                    str(static_terrain_performance_doc.get("classification") or "").strip().lower()
                    if isinstance(static_terrain_performance_doc, dict)
                    else ""
                )
                if performance_classification:
                    failures_for_fault.add(performance_classification)
                if static_terrain_expected_fault not in failures_for_fault:
                    static_terrain_workload_complete = False
                    validation_messages.append(
                        "deterministic static-terrain fault injection produced wrong classification "
                        f"(fault={requested_world_static_terrain_fault}, expected={static_terrain_expected_fault}, "
                        f"actual={sorted(failures_for_fault)})"
                    )
                else:
                    validation_messages.append(
                        "deterministic static-terrain fault injection rejected as expected "
                        f"(fault={requested_world_static_terrain_fault}, classification={static_terrain_expected_fault})"
                    )
    if static_terrain_scenario and mode.expected_attribution == "java-vulkan" and not rust_shell_outline_mode:
        if static_terrain_submitted_count > 0 or int(rust_gal_world_mesh_instances_for_validation or 0) != 0:
            static_terrain_workload_complete = False
            validation_messages.append("normal Java Vulkan static-terrain control emitted unexpected Rust terrain mesh work")
    world_mesh_block_display_workload_complete = True
    block_display_scenario = (requested_world_mesh_block_display_scenario or "").strip().lower()
    if block_display_scenario and rust_outline_mode and tool_kind != "subsystem":
        block_display_status = str(block_display_doc.get("status") or "")
        block_display_entity_count = int(parse_number(block_display_doc.get("entityCount")) or 0)
        block_display_distinct_count = int(parse_number(block_display_doc.get("distinctBlockCount")) or 0)
        if block_display_scenario == "hidden":
            if int(rust_gal_world_mesh_instances_for_validation or 0) != 0:
                world_mesh_block_display_workload_complete = False
                validation_messages.append("deterministic hidden BlockDisplay scenario emitted unexpected mesh instances")
            if block_display_status not in {"hidden", "inactive", ""}:
                world_mesh_block_display_workload_complete = False
                validation_messages.append(
                    f"deterministic hidden BlockDisplay scenario reported unexpected status {block_display_status!r}"
                )
            if tool_kind == "capture" and world_mesh_block_display_pixel_evidence.get("status") != "absent_expected":
                world_mesh_block_display_workload_complete = False
                validation_messages.append(
                    "deterministic hidden BlockDisplay scenario reported unexpected projected mesh evidence "
                    f"(status={world_mesh_block_display_pixel_evidence.get('status')})"
                )
        elif world_mesh_block_display_control == "disabled":
            if block_display_submitted_count <= 0:
                world_mesh_block_display_workload_complete = False
                validation_messages.append("BlockDisplay disabled control did not prove production callsite traversal")
            if int(rust_gal_world_mesh_instances_for_validation or 0) != 0:
                world_mesh_block_display_workload_complete = False
                validation_messages.append("BlockDisplay disabled control emitted unexpected Rust mesh instances")
            if block_display_status and block_display_status != "spawned":
                world_mesh_block_display_workload_complete = False
                validation_messages.append(
                    f"BlockDisplay disabled control did not spawn the matched production entities "
                    f"(status={block_display_status})"
                )
        elif world_mesh_block_display_control == "legacy":
            if block_display_submitted_count <= 0:
                world_mesh_block_display_workload_complete = False
                validation_messages.append("BlockDisplay legacy control did not prove Java legacy draw traversal")
            if int(rust_gal_world_mesh_instances_for_validation or 0) != 0:
                world_mesh_block_display_workload_complete = False
                validation_messages.append("BlockDisplay Java legacy control emitted unexpected Rust mesh instances")
            if block_display_status and block_display_status != "spawned":
                world_mesh_block_display_workload_complete = False
                validation_messages.append(
                    f"BlockDisplay legacy control did not spawn the matched production entities "
                    f"(status={block_display_status})"
                )
        else:
            required_mesh_counts = {
                "mesh instances": rust_gal_world_mesh_instances_for_validation,
                "mesh batches": rust_gal_world_mesh_batches_for_validation,
                "mesh draws": rust_gal_world_mesh_draws_for_validation,
            }
            missing_mesh_counts = [
                name for name, value in required_mesh_counts.items() if int(value or 0) <= 0
            ]
            if missing_mesh_counts:
                world_mesh_block_display_workload_complete = False
                validation_messages.append(
                    "deterministic Rust-GAL BlockDisplay scenario requested but no non-zero "
                    + ", ".join(missing_mesh_counts)
                    + " evidence was captured"
                )
            if block_display_status and block_display_status != "spawned":
                world_mesh_block_display_workload_complete = False
                validation_messages.append(
                    f"deterministic BlockDisplay scenario did not spawn a production BlockDisplay entity "
                    f"(status={block_display_status})"
                )
            if requested_world_mesh_block_display_workload in {"performance", "scale-mixed-meshes"}:
                if block_display_entity_count < 2 or block_display_distinct_count < 3:
                    world_mesh_block_display_workload_complete = False
                    validation_messages.append(
                        "BlockDisplay performance workload did not prove repeated instances across distinct meshes "
                        f"(entities={block_display_entity_count}, distinctBlocks={block_display_distinct_count})"
                    )
            if tool_kind == "capture" and world_mesh_block_display_pixel_evidence.get("status") != "present":
                world_mesh_block_display_workload_complete = False
                validation_messages.append(
                    "deterministic BlockDisplay scenario did not produce projected mesh crop evidence "
                    f"(status={world_mesh_block_display_pixel_evidence.get('status')}, "
                    f"validated={world_mesh_block_display_pixel_evidence.get('validated_mesh_keys')}, "
                    f"expected_block={world_mesh_block_display_pixel_evidence.get('expected_block_id')}, "
                    f"matching_pixels={world_mesh_block_display_pixel_evidence.get('matching_pixels')})"
                )
            elif tool_kind == "capture" and (
                world_mesh_block_display_pixel_evidence.get("texture_status") != "passed"
                or world_mesh_block_display_pixel_evidence.get("material_status") != "passed"
                or world_mesh_block_display_pixel_evidence.get("orientation_status") != "passed"
            ):
                world_mesh_block_display_workload_complete = False
                validation_messages.append(
                    "deterministic BlockDisplay projected crop evidence did not prove texture/material/orientation "
                    f"(texture={world_mesh_block_display_pixel_evidence.get('texture_status')}, "
                    f"material={world_mesh_block_display_pixel_evidence.get('material_status')}, "
                    f"orientation={world_mesh_block_display_pixel_evidence.get('orientation_status')})"
                )
    if block_display_scenario and mode.expected_attribution == "java-vulkan" and not rust_shell_outline_mode:
        if int(rust_gal_world_mesh_instances_for_validation or 0) != 0:
            world_mesh_block_display_workload_complete = False
            validation_messages.append("normal Java Vulkan BlockDisplay control emitted unexpected Rust mesh work")
    world_mesh_falling_block_workload_complete = True
    falling_block_scenario = (requested_world_mesh_falling_block_scenario or "").strip().lower()
    if falling_block_scenario and rust_outline_mode and tool_kind != "subsystem":
        falling_block_status = str(falling_block_doc.get("status") or "")
        falling_block_entity_count = int(parse_number(falling_block_doc.get("entityCount")) or 0)
        if tool_kind == "capture" and falling_block_scenario not in {"hidden"}:
            if not falling_block_status and deterministic_falling_blocks:
                falling_block_status = "spawned"
            if falling_block_entity_count <= 0:
                falling_block_entity_count = len(deterministic_falling_blocks)
        if falling_block_scenario == "hidden":
            if int(rust_gal_world_mesh_instances_for_validation or 0) != 0:
                world_mesh_falling_block_workload_complete = False
                validation_messages.append("deterministic hidden FallingBlock scenario emitted unexpected mesh instances")
            if falling_block_status not in {"hidden", "inactive", ""}:
                world_mesh_falling_block_workload_complete = False
                validation_messages.append(
                    f"deterministic hidden FallingBlock scenario reported unexpected status {falling_block_status!r}"
                )
        elif world_mesh_falling_block_control == "disabled":
            if falling_block_submitted_count <= 0 and falling_block_route_count("disabled") <= 0:
                world_mesh_falling_block_workload_complete = False
                validation_messages.append("FallingBlock disabled control did not prove production route traversal")
            if int(rust_gal_world_mesh_instances_for_validation or 0) != 0:
                world_mesh_falling_block_workload_complete = False
                validation_messages.append("FallingBlock disabled control emitted unexpected Rust mesh instances")
        elif world_mesh_falling_block_control == "legacy":
            if falling_block_submitted_count <= 0 and falling_block_route_count("java-legacy") <= 0:
                world_mesh_falling_block_workload_complete = False
                validation_messages.append("FallingBlock legacy control did not prove Java legacy draw route traversal")
            if int(rust_gal_world_mesh_instances_for_validation or 0) != 0:
                world_mesh_falling_block_workload_complete = False
                validation_messages.append("FallingBlock Java legacy control emitted unexpected Rust mesh instances")
        else:
            if falling_block_route_traversal_count > 0 and falling_block_route_count("rust-opengl") <= 0 and falling_block_route_count("rust-vulkan-whole-frame") <= 0:
                world_mesh_falling_block_workload_complete = False
                validation_messages.append("FallingBlock Rust route did not record a Rust route decision")
            if falling_block_status and falling_block_status != "spawned":
                world_mesh_falling_block_workload_complete = False
                validation_messages.append(
                    f"deterministic FallingBlock scenario did not spawn production FallingBlock entities "
                    f"(status={falling_block_status})"
                )
            if falling_block_entity_count <= 0:
                world_mesh_falling_block_workload_complete = False
                validation_messages.append("deterministic FallingBlock scenario did not report any entities")
            required_mesh_counts = {
                "mesh instances": rust_gal_world_mesh_instances_for_validation,
                "mesh batches": rust_gal_world_mesh_batches_for_validation,
                "mesh draws": rust_gal_world_mesh_draws_for_validation,
            }
            missing_mesh_counts = [
                name for name, value in required_mesh_counts.items() if int(value or 0) <= 0
            ]
            if missing_mesh_counts:
                world_mesh_falling_block_workload_complete = False
                validation_messages.append(
                    "deterministic Rust-GAL FallingBlock scenario requested but no non-zero "
                    + ", ".join(missing_mesh_counts)
                    + " evidence was captured"
                )
            if tool_kind == "capture":
                shader_enabled = str(deterministic_doc.get("shaderEnabled") if isinstance(deterministic_doc, dict) else "").strip().lower()
                shader_pack = str(deterministic_doc.get("shaderPack") if isinstance(deterministic_doc, dict) else "").strip()
                if mode.shaders == "on" and (shader_enabled != "true" or shader_pack != EXPECTED_SHADER_PACK):
                    world_mesh_falling_block_workload_complete = False
                    validation_messages.append(
                        "deterministic FallingBlock shaders-on row did not prove the configured shader pack "
                        f"(shaderEnabled={shader_enabled!r}, shaderPack={shader_pack!r}, expected={EXPECTED_SHADER_PACK!r})"
                    )
                if world_mesh_falling_block_pixel_evidence.get("status") != "present":
                    world_mesh_falling_block_workload_complete = False
                    validation_messages.append(
                        "deterministic FallingBlock projected crop evidence did not prove final-frame visibility "
                        f"(status={world_mesh_falling_block_pixel_evidence.get('status')}, "
                        f"position={world_mesh_falling_block_pixel_evidence.get('position_status')})"
                    )
                elif (
                    world_mesh_falling_block_pixel_evidence.get("texture_status") != "passed"
                    or world_mesh_falling_block_pixel_evidence.get("material_status") != "passed"
                    or world_mesh_falling_block_pixel_evidence.get("orientation_status") != "passed"
                ):
                    world_mesh_falling_block_workload_complete = False
                    validation_messages.append(
                        "deterministic FallingBlock projected crop evidence did not prove texture/material/orientation "
                        f"(texture={world_mesh_falling_block_pixel_evidence.get('texture_status')}, "
                        f"material={world_mesh_falling_block_pixel_evidence.get('material_status')}, "
                        f"orientation={world_mesh_falling_block_pixel_evidence.get('orientation_status')})"
                    )
    if falling_block_scenario and mode.expected_attribution == "java-vulkan" and not rust_shell_outline_mode:
        if int(rust_gal_world_mesh_instances_for_validation or 0) != 0:
            world_mesh_falling_block_workload_complete = False
            validation_messages.append("normal Java Vulkan FallingBlock control emitted unexpected Rust mesh work")
    world_mesh_piston_workload_complete = True
    piston_scenario = (requested_world_mesh_piston_scenario or "").strip().lower()
    if piston_scenario and rust_outline_mode and tool_kind != "subsystem":
        piston_status = str(piston_doc.get("status") or "")
        piston_entity_count = int(parse_number(piston_doc.get("entityCount")) or 0)
        if tool_kind == "capture" and piston_scenario not in {"hidden", "completed", "removed"}:
            if not piston_status and deterministic_piston_blocks:
                piston_status = "spawned"
            if piston_entity_count <= 0:
                piston_entity_count = len(deterministic_piston_blocks)
        if piston_scenario in {"hidden", "completed", "removed"}:
            if deterministic_piston_blocks or int(rust_gal_world_mesh_instances_for_validation or 0) != 0:
                world_mesh_piston_workload_complete = False
                validation_messages.append("deterministic hidden/completed Piston scenario emitted unexpected mesh instances")
            if piston_status not in {"hidden", "inactive", ""}:
                world_mesh_piston_workload_complete = False
                validation_messages.append(
                    f"deterministic hidden/completed Piston scenario reported unexpected status {piston_status!r}"
                )
        elif world_mesh_piston_control == "disabled":
            if piston_submitted_count <= 0 and piston_route_count("disabled") <= 0:
                world_mesh_piston_workload_complete = False
                validation_messages.append("Piston disabled control did not prove production route traversal")
            if deterministic_piston_blocks or int(rust_gal_world_mesh_instances_for_validation or 0) != 0:
                world_mesh_piston_workload_complete = False
                validation_messages.append("Piston disabled control emitted unexpected Rust mesh instances")
        elif world_mesh_piston_control == "legacy":
            if piston_submitted_count <= 0 and piston_route_count("java-legacy") <= 0:
                world_mesh_piston_workload_complete = False
                validation_messages.append("Piston legacy control did not prove Java legacy draw route traversal")
            if deterministic_piston_blocks or int(rust_gal_world_mesh_instances_for_validation or 0) != 0:
                world_mesh_piston_workload_complete = False
                validation_messages.append("Piston Java legacy control emitted unexpected Rust mesh instances")
        else:
            if piston_route_traversal_count > 0 and piston_route_count("rust-opengl") <= 0 and piston_route_count("rust-vulkan-whole-frame") <= 0:
                world_mesh_piston_workload_complete = False
                validation_messages.append("Piston Rust route did not record a Rust route decision")
            if piston_status and piston_status not in {"spawned", "reseeded"}:
                world_mesh_piston_workload_complete = False
                validation_messages.append(
                    f"deterministic Piston scenario did not spawn production moving-piston entities "
                    f"(status={piston_status})"
                )
            if piston_entity_count <= 0:
                world_mesh_piston_workload_complete = False
                validation_messages.append("deterministic Piston scenario did not report any entities")
            required_mesh_counts = {
                "mesh instances": rust_gal_world_mesh_instances_for_validation,
                "mesh batches": rust_gal_world_mesh_batches_for_validation,
                "mesh draws": rust_gal_world_mesh_draws_for_validation,
            }
            missing_mesh_counts = [
                name for name, value in required_mesh_counts.items() if int(value or 0) <= 0
            ]
            if missing_mesh_counts:
                world_mesh_piston_workload_complete = False
                validation_messages.append(
                    "deterministic Rust-GAL Piston scenario requested but no non-zero "
                    + ", ".join(missing_mesh_counts)
                    + " evidence was captured"
                )
            if mode.expected_attribution == "rust-vulkan":
                shell_scan_samples = int(parse_number(piston_shell_scan.get("samples")) or 0)
                shell_scan_states = int(parse_number(piston_shell_scan.get("pistonStatesExtracted")) or 0)
                shell_scan_max_nanos = int(parse_number(piston_shell_scan.get("maxNanos")) or 0)
                shell_scan_budget_nanos = int(
                    parse_number(piston_shell_scan.get("budgetNanos")) or PISTON_SHELL_SCAN_BUDGET_NANOS
                )
                rust_vulkan_route_count = piston_route_count("rust-vulkan-whole-frame")
                rust_vulkan_work_count = int(rust_gal_world_mesh_instances_for_validation or 0)
                shell_scan_required = rust_vulkan_route_count <= 0 or rust_vulkan_work_count <= 0
                if shell_scan_required and (shell_scan_samples <= 0 or shell_scan_states <= 0):
                    world_mesh_piston_workload_complete = False
                    validation_messages.append(
                        "Rust Vulkan whole-frame Piston scenario did not record bounded block-entity shell collection"
                    )
                if shell_scan_max_nanos > shell_scan_budget_nanos:
                    world_mesh_piston_workload_complete = False
                    validation_messages.append(
                        "Rust Vulkan whole-frame Piston block-entity shell collection exceeded budget "
                        f"(max={shell_scan_max_nanos}ns, budget={shell_scan_budget_nanos}ns)"
                    )
            if tool_kind == "capture":
                if world_mesh_piston_pixel_evidence.get("status") != "present":
                    world_mesh_piston_workload_complete = False
                    validation_messages.append(
                        "deterministic Piston projected crop evidence did not prove final-frame visibility "
                        f"(status={world_mesh_piston_pixel_evidence.get('status')}, "
                        f"position={world_mesh_piston_pixel_evidence.get('position_status')})"
                    )
                elif (
                    world_mesh_piston_pixel_evidence.get("texture_status") != "passed"
                    or world_mesh_piston_pixel_evidence.get("material_status") != "passed"
                    or world_mesh_piston_pixel_evidence.get("orientation_status") != "passed"
                ):
                    world_mesh_piston_workload_complete = False
                    validation_messages.append(
                        "deterministic Piston projected crop evidence did not prove texture/material/orientation "
                        f"(texture={world_mesh_piston_pixel_evidence.get('texture_status')}, "
                        f"material={world_mesh_piston_pixel_evidence.get('material_status')}, "
                        f"orientation={world_mesh_piston_pixel_evidence.get('orientation_status')})"
                    )
    if piston_scenario and mode.expected_attribution == "java-vulkan" and not rust_shell_outline_mode:
        if deterministic_piston_blocks or int(rust_gal_world_mesh_instances_for_validation or 0) != 0:
            world_mesh_piston_workload_complete = False
            validation_messages.append("normal Java Vulkan Piston control emitted unexpected Rust mesh work")
    background_scenario = (requested_world_background_scenario or "").strip().lower()
    if background_scenario and rust_shell_outline_mode:
        if background_scenario in {"hidden", "invalid"}:
            if int(rust_gal_world_background_clears_for_validation or 0) != 0:
                validation_messages.append("deterministic hidden/invalid world-background scenario emitted a semantic clear")
        elif int(rust_gal_world_background_clears_for_validation or 0) <= 0:
            validation_messages.append("deterministic Rust-GAL world-background scenario requested but no semantic background clear executed")
    instrumentation = benchmark_fingerprint["instrumentation"]
    run_type = instrumentation.get("run_type") if isinstance(instrumentation, dict) else "clean-performance"
    diagnostic_hooks = bool(instrumentation.get("diagnostic_hooks")) if isinstance(instrumentation, dict) else False
    if run_type in INSTRUMENTED_RUN_TYPES:
        validation_notes.append(f"{run_type} run is diagnostic-only; performance numbers are not publishable")
    if diagnostic_hooks:
        validation_notes.append("graphics diagnostic hooks enabled; performance numbers are overhead probes only")
    if isinstance(instrumentation, dict) and instrumentation.get("renderdoc", {}).get("enabled"):
        if renderdoc_summary.get("status") != "complete":
            validation_messages.append("RenderDoc capture/replay did not complete")
        proof = renderdoc_summary.get("workload_proof") if isinstance(renderdoc_summary, dict) else {}
        if requested_world_outline_scenario and requested_world_outline_scenario != "no-target":
            if not isinstance(proof, dict) or not proof.get("non_zero_outline_workload"):
                validation_messages.append("RenderDoc capture did not prove non-zero Rust-GAL outline workload")
            if not isinstance(proof, dict) or not proof.get("acquired_rendered_presented_image_identity_matches"):
                validation_messages.append("RenderDoc capture did not prove acquired/rendered/presented image identity")
            if not isinstance(proof, dict) or not proof.get("depth_attachment_evidence"):
                validation_messages.append("RenderDoc capture did not prove depth attachment use")
            if not isinstance(proof, dict) or not proof.get("outline_marker_evidence"):
                validation_messages.append("RenderDoc capture did not prove outline marker/work evidence")
            if crack_scenario and crack_scenario not in {"hidden", "no-target"}:
                if not isinstance(proof, dict) or not proof.get("non_zero_crack_workload"):
                    validation_messages.append("RenderDoc capture did not prove non-zero Rust-GAL crack workload")
            if border_scenario and border_scenario not in {"hidden", "far", "no-target"}:
                if not isinstance(proof, dict) or not proof.get("non_zero_border_workload"):
                    validation_messages.append("RenderDoc capture did not prove non-zero Rust-GAL world-border workload")
            if isinstance(proof, dict) and proof.get("blue_diagnostic_shell_clear_expected"):
                validation_notes.append("Rust Vulkan shell blue diagnostic clear is expected; this is not full world rendering")
        if block_display_scenario and block_display_scenario != "hidden":
            if not isinstance(proof, dict) or not proof.get("non_zero_mesh_workload"):
                validation_messages.append("RenderDoc capture did not prove non-zero Rust-GAL BlockDisplay mesh workload")
        if piston_scenario and piston_scenario not in {"hidden", "completed", "removed"}:
            if not isinstance(proof, dict) or not proof.get("non_zero_mesh_workload"):
                validation_messages.append("RenderDoc capture did not prove non-zero Rust-GAL Piston mesh workload")
        if static_terrain_scenario and static_terrain_scenario != "hidden":
            if not isinstance(proof, dict) or not proof.get("non_zero_mesh_workload"):
                validation_messages.append("RenderDoc capture did not prove non-zero Rust-GAL static-terrain mesh workload")
    if isinstance(instrumentation, dict) and instrumentation.get("tracy", {}).get("enabled"):
        if tracy_summary.get("status") != "complete":
            validation_messages.append("Tracy capture did not complete")
    java_vulkan_has_rust_shell = mode.expected_attribution == "java-vulkan" and (
        "mattmc.dev.rustGalVulkanWholeFrame=true" in combined_logs
        or "Rust VulkanicGAL whole-frame" in combined_logs
        or "gal.frame.acquire backend=vulkan" in combined_logs
    )
    rust_shell_has_java_vulkan_frame = mode.expected_attribution == "rust-vulkan" and bool(
        re.search(r"Vulkan beginFramebufferRenderPass|Java Vulkan beginFrame|Java Vulkan endFrame|presentTextureToScreen", combined_logs)
    )
    if java_vulkan_has_rust_shell:
        validation_messages.append("normal Java Vulkan control launched Rust whole-frame shell")
    if rust_shell_has_java_vulkan_frame:
        validation_messages.append("Rust whole-frame shell executed Java Vulkan frame/present work")
    require_rust_vulkan_gameplay_attachments = tool_kind == "capture" and bool(
        falling_block_scenario or piston_scenario or static_terrain_scenario
    )
    if require_rust_vulkan_gameplay_attachments and mode.expected_attribution == "rust-vulkan":
        if not isinstance(gameplay_attachment_doc, dict):
            validation_messages.append("Rust Vulkan gameplay row did not retain real whole-frame gameplay attachment dumps")
        elif gameplay_attachment_doc.get("source") != "real-gameplay-whole-frame-submit":
            validation_messages.append("Rust Vulkan gameplay attachment dumps did not come from the real whole-frame submit path")
        elif gameplay_attachment_doc.get("synthetic_shader_scene") is not False:
            validation_messages.append("Rust Vulkan gameplay attachment dumps were not explicitly separated from shader conformance scenes")
        if not isinstance(gameplay_attachment_correlation_doc, dict):
            validation_messages.append("Rust Vulkan gameplay row did not retain acquire/submit/present correlation for attachment dumps")
        if isinstance(gameplay_attachment_doc, dict) and isinstance(gameplay_attachment_correlation_doc, dict):
            for field in ("gameplay_frame_id", "correlation_id", "gal_submission_id", "vulkan_submission_timeline_value"):
                if gameplay_attachment_doc.get(field) != gameplay_attachment_correlation_doc.get(field):
                    validation_messages.append(f"Rust Vulkan gameplay attachment correlation mismatch for {field}")
            extent = gameplay_attachment_doc.get("extent")
            correlation_extent = gameplay_attachment_correlation_doc.get("extent")
            if extent != correlation_extent:
                validation_messages.append("Rust Vulkan gameplay attachment extent did not match acquired frame extent")
            if gameplay_attachment_correlation_doc.get("same_acquired_presented_image") is not True:
                validation_messages.append("Rust Vulkan gameplay attachment frame did not prove acquired/presented image identity")
            evidence = gameplay_attachment_doc.get("attachment_evidence")
            required = [
                "shadow_depth",
                "albedo",
                "normal",
                "material_light",
                "world_position",
                "main_depth",
                "deferred_lit",
                "composite_0",
                "composite_1",
                "final_output",
            ]
            if not isinstance(evidence, dict) or any(name not in evidence for name in required):
                validation_messages.append("Rust Vulkan gameplay attachment dump did not include the complete required attachment set")
            else:
                for color_name in ("albedo", "normal", "material_light", "world_position", "deferred_lit", "composite_0", "composite_1", "final_output"):
                    value = evidence.get(color_name) if isinstance(evidence.get(color_name), dict) else {}
                    if parse_number(value.get("nonblack_rgb")) in (None, 0):
                        validation_messages.append(f"Rust Vulkan gameplay attachment {color_name} did not prove non-empty color data")
                for depth_name in ("shadow_depth", "main_depth"):
                    value = evidence.get(depth_name) if isinstance(evidence.get(depth_name), dict) else {}
                    if parse_number(value.get("less_than_clear")) in (None, 0):
                        validation_messages.append(f"Rust Vulkan gameplay attachment {depth_name} did not prove non-empty depth data")
    renderdoc_workload_assertions_complete = True
    if (
        isinstance(instrumentation, dict)
        and instrumentation.get("renderdoc", {}).get("enabled")
        and (
            (requested_world_outline_scenario and requested_world_outline_scenario != "no-target")
            or (border_scenario and border_scenario not in {"hidden", "far", "no-target"})
            or (block_display_scenario and block_display_scenario != "hidden")
            or (piston_scenario and piston_scenario not in {"hidden", "completed", "removed"})
            or (static_terrain_scenario and static_terrain_scenario != "hidden")
        )
    ):
        proof = renderdoc_summary.get("workload_proof") if isinstance(renderdoc_summary, dict) else {}
        required_workload_keys = [
            "acquired_rendered_presented_image_identity_matches",
            "depth_attachment_evidence",
        ]
        if requested_world_outline_scenario and requested_world_outline_scenario != "no-target":
            required_workload_keys.extend(["non_zero_outline_workload", "outline_marker_evidence"])
        if border_scenario and border_scenario not in {"hidden", "far", "no-target"}:
            required_workload_keys.extend(["non_zero_border_workload", "border_marker_evidence"])
        if block_display_scenario and block_display_scenario != "hidden":
            required_workload_keys.extend(["non_zero_mesh_workload", "mesh_marker_evidence"])
        if piston_scenario and piston_scenario not in {"hidden", "completed", "removed"}:
            required_workload_keys.extend(["non_zero_mesh_workload", "mesh_marker_evidence"])
        if static_terrain_scenario and static_terrain_scenario != "hidden":
            required_workload_keys.extend(["non_zero_mesh_workload", "mesh_marker_evidence"])
        renderdoc_workload_assertions_complete = isinstance(proof, dict) and all(
            bool(proof.get(key)) for key in required_workload_keys
        )
        if crack_scenario and crack_scenario not in {"hidden", "no-target"}:
            renderdoc_workload_assertions_complete = renderdoc_workload_assertions_complete and bool(
                proof.get("non_zero_crack_workload") if isinstance(proof, dict) else False
            )
    if meta.get("migration_gate_blocking", "true").lower() != "true":
        validation_messages.append("stress-diagnostic artifact is non-blocking for routine producer migration gates")
    hard_errors_absent = (
        error_counts["crash"] == 0
        and error_counts["gl_error"] == 0
        and concrete_validation_finding_count(validation_findings) == 0
        and error_counts["device_loss"] == 0
        and not memory_guard_triggered
        and error_counts["orphan_process"] == 0
    )
    renderdoc_complete = not (isinstance(instrumentation, dict) and instrumentation.get("renderdoc", {}).get("enabled")) or renderdoc_summary.get("status") == "complete"
    tracy_complete = not (isinstance(instrumentation, dict) and instrumentation.get("tracy", {}).get("enabled")) or tracy_summary.get("status") == "complete"
    work_counts = {
        "draw": len(re.findall(r"\bdraw(?:Indexed|Arrays)?\b", combined_logs)),
        "dispatch": len(re.findall(r"\bdispatchCompute\b|\bdispatch\b", combined_logs)),
        "pass": len(re.findall(r"render pass|RenderPass|render_pass", combined_logs, re.IGNORECASE)),
        "transfer": len(re.findall(r"copyBuffer|copyImage|blit|transfer", combined_logs, re.IGNORECASE)),
        "vulkan_submission": len(re.findall(r"vkQueueSubmit|queue submit|submission", combined_logs, re.IGNORECASE)),
    }
    creation_counts = {
        "pipeline": len(re.findall(r"create.*pipeline|Creating .*pipeline", combined_logs, re.IGNORECASE)),
        "descriptor": len(re.findall(r"descriptor", combined_logs, re.IGNORECASE)),
        "resource": len(re.findall(r"create.*(?:buffer|texture|image|sampler)", combined_logs, re.IGNORECASE)),
    }
    subsystem_work_counts, subsystem_creation_counts = subsystem_workload_counts(subsystem_doc)
    for key, value in subsystem_work_counts.items():
        work_counts[key] = int(work_counts.get(key) or 0) + value
    for key, value in subsystem_creation_counts.items():
        creation_counts[key] = int(creation_counts.get(key) or 0) + value
    validation_categories = validation_category_counts(validation_findings)
    concrete_validation_count = concrete_validation_finding_count(validation_findings)
    validation_run = (
        meta.get("validation_enabled", "false").lower() == "true"
        and mode.backend in {"vulkan", "rust-vulkan"}
    )
    proof = validation_proof(
        meta,
        combined_logs,
        files,
        tool_kind,
        mode,
        work_counts,
        creation_counts,
        deterministic_complete,
        subsystem_complete,
        frame_sample_window_complete,
    )
    validation_layer_exercised = (
        not validation_run
        or (
            bool(proof.get("layer_loaded"))
            and bool(proof.get("meaningful_vulkan_workload"))
            and bool(proof.get("no_message_filtering"))
            and bool(proof.get("artifact_log_association", {}).get("all_named_logs_match_run"))
        )
    )
    loader_only_validation = validation_run and bool(validation_findings) and concrete_validation_count == 0
    if not hard_errors_absent:
        validation_messages.append("strict error scan found crash, GL/Vulkan validation, device-loss, RSS, or orphan-process evidence")
    if validation_run and not proof.get("layer_loaded"):
        validation_messages.append("Vulkan validation layer load was not proven")
    if validation_run and not proof.get("meaningful_vulkan_workload"):
        validation_messages.append("Vulkan validation run did not prove meaningful Vulkan workload execution")
    if validation_run and not proof.get("no_message_filtering"):
        validation_messages.append("Vulkan validation message filtering state is not clean")
    if validation_run and not proof.get("artifact_log_association", {}).get("all_named_logs_match_run"):
        validation_messages.append("Vulkan validation artifact/log association is incomplete")
    loader_only_validation_note = ""
    if loader_only_validation:
        loader_only_validation_note = "Vulkan validation emitted only loader/configuration notices; no concrete API VUIDs were observed"
    rust_gal_frames = last_number(combined_logs, r"rust_gal_frames_executed[=: ]+(\d+)")
    rust_gal_batches = last_number(combined_logs, r"rust_gal_batches_executed[=: ]+(\d+)")
    rust_gal_sprite_batches = last_number(combined_logs, r"rust_gal_sprite_batches_executed[=: ]+(\d+)")
    rust_gal_packed_sprites = last_number(combined_logs, r"rust_gal_packed_sprites_executed[=: ]+(\d+)")
    rust_gal_world_batches = last_number(combined_logs, r"rust_gal_world_primitive_batches_executed[=: ]+(\d+)")
    rust_gal_world_segments = last_number(combined_logs, r"rust_gal_world_line_segments_executed[=: ]+(\d+)")
    rust_gal_world_vertices = last_number(combined_logs, r"rust_gal_world_line_vertices_executed[=: ]+(\d+)")
    rust_gal_world_draws = last_number(combined_logs, r"rust_gal_world_primitive_draws_executed[=: ]+(\d+)")
    rust_gal_world_crack_quads = last_number(combined_logs, r"rust_gal_world_crack_quads_executed[=: ]+(\d+)")
    rust_gal_world_crack_batches = last_number(combined_logs, r"rust_gal_world_crack_batches_executed[=: ]+(\d+)")
    rust_gal_world_crack_draws = last_number(combined_logs, r"rust_gal_world_crack_draws_executed[=: ]+(\d+)")
    rust_gal_world_border_quads = last_number(combined_logs, r"rust_gal_world_border_quads_executed[=: ]+(\d+)")
    rust_gal_world_border_batches = last_number(combined_logs, r"rust_gal_world_border_batches_executed[=: ]+(\d+)")
    rust_gal_world_border_draws = last_number(combined_logs, r"rust_gal_world_border_draws_executed[=: ]+(\d+)")
    rust_gal_world_material_quads = last_number(combined_logs, r"rust_gal_world_material_quads_executed[=: ]+(\d+)")
    rust_gal_world_material_batches = last_number(combined_logs, r"rust_gal_world_material_batches_executed[=: ]+(\d+)")
    rust_gal_world_material_draws = last_number(combined_logs, r"rust_gal_world_material_draws_executed[=: ]+(\d+)")
    rust_gal_world_material_marker_barrier_quads = max_number(combined_logs, r"material_marker_barrier_quads[=: ]+(\d+)")
    rust_gal_world_material_marker_light_quads = max_number(combined_logs, r"material_marker_light_quads[=: ]+(\d+)")
    rust_gal_world_material_marker_light_level_mask = max_number(combined_logs, r"material_marker_light_level_mask[=: ]+(\d+)")
    rust_gal_world_material_marker_last_light_level = max_number(combined_logs, r"material_marker_last_light_level[=: ]+(-?\d+)")
    rust_gal_world_material_marker_last_texture_id = max_number(combined_logs, r"material_marker_last_texture_id[=: ]+(\d+)")
    rust_gal_world_material_terrain_particle_quads = max_number(combined_logs, r"material_terrain_particle_quads[=: ]+(\d+)")
    rust_gal_world_material_terrain_particle_texture_mask = max_number(combined_logs, r"material_terrain_particle_texture_mask[=: ]+(\d+)")
    rust_gal_world_mesh_instances = last_number(combined_logs, r"rust_gal_world_mesh_instances_executed[=: ]+(\d+)")
    rust_gal_world_mesh_batches = last_number(combined_logs, r"rust_gal_world_mesh_batches_executed[=: ]+(\d+)")
    rust_gal_world_mesh_draws = last_number(combined_logs, r"rust_gal_world_mesh_draws_executed[=: ]+(\d+)")
    if falling_block_route_traversal_count > 0:
        if rust_gal_world_mesh_instances is None:
            rust_gal_world_mesh_instances = 0
        if rust_gal_world_mesh_batches is None:
            rust_gal_world_mesh_batches = 0
        if rust_gal_world_mesh_draws is None:
            rust_gal_world_mesh_draws = 0
    rust_gal_world_mesh_cache_hits = last_number(combined_logs, r"rust_gal_world_mesh_cache_hits[=: ]+(\d+)")
    rust_gal_world_mesh_cache_misses = last_number(combined_logs, r"rust_gal_world_mesh_cache_misses[=: ]+(\d+)")
    rust_gal_world_background_clears = last_number(combined_logs, r"rust_gal_world_background_clears_executed[=: ]+(\d+)")
    rust_gal_world_background_fallbacks = last_number(combined_logs, r"rust_gal_world_background_diagnostic_fallbacks[=: ]+(\d+)")
    rust_gal_world_background_sky_type = last_number(combined_logs, r"rust_gal_world_background_sky_type[=: ]+(\d+)")
    rust_gal_world_background_color_argb = last_text(combined_logs, r"rust_gal_world_background_color_argb[=: ]+([0-9a-fA-F]+)")
    rust_gal_world_depth_creates = last_number(combined_logs, r"rust_gal_world_depth_attachment_creates[=: ]+(\d+)")
    rust_gal_world_depth_reuses = last_number(combined_logs, r"rust_gal_world_depth_attachment_reuses[=: ]+(\d+)")
    rust_gal_world_depth_retires = last_number(combined_logs, r"rust_gal_world_depth_attachment_retires[=: ]+(\d+)")
    rust_gal_world_outline_cache_hits = last_number(combined_logs, r"rust_gal_world_outline_cache_hits[=: ]+(\d+)")
    rust_gal_world_outline_cache_misses = last_number(combined_logs, r"rust_gal_world_outline_cache_misses[=: ]+(\d+)")
    rust_gal_world_crack_cache_hits = last_number(combined_logs, r"rust_gal_world_crack_cache_hits[=: ]+(\d+)")
    rust_gal_world_crack_cache_misses = last_number(combined_logs, r"rust_gal_world_crack_cache_misses[=: ]+(\d+)")
    rust_gal_world_border_cache_hits = last_number(combined_logs, r"rust_gal_world_border_cache_hits[=: ]+(\d+)")
    rust_gal_world_border_cache_misses = last_number(combined_logs, r"rust_gal_world_border_cache_misses[=: ]+(\d+)")
    rust_gal_world_material_cache_hits = last_number(combined_logs, r"rust_gal_world_material_cache_hits[=: ]+(\d+)")
    rust_gal_world_material_cache_misses = last_number(combined_logs, r"rust_gal_world_material_cache_misses[=: ]+(\d+)")
    rust_gal_swapchain_recreations = len(re.findall(r"gal\.swapchain\.recreate backend=vulkan", combined_logs))
    rust_gal_ffi_calls = last_number(combined_logs, r"ffi(?:_call)?_count[=: ]+(\d+)")
    rust_gal_ffi_bytes = last_number(combined_logs, r"ffi(?:_bytes| bytes)[=: ]+(\d+)")
    gui_asset_resolutions = [
        {
            "sprite": match.group(1),
            "sprite_id": int(match.group(2)),
            "path": match.group(3),
            "source_pack": match.group(4),
            "bytes": int(match.group(5)),
            "sha256": match.group(6),
        }
        for match in re.finditer(
            r"Rust VulkanicGAL GUI asset resolved sprite=([^ ]+) sprite_id=(\d+) path=([^ ]+) source_pack=([^ ]+) bytes=(\d+) sha256=([0-9a-f]+)",
            combined_logs,
        )
    ]
    gui_asset_missing = [
        {
            "sprite": match.group(1),
            "sprite_id": int(match.group(2)),
            "path": match.group(3),
        }
        for match in re.finditer(
            r"Rust VulkanicGAL GUI asset missing sprite=([^ ]+) sprite_id=(\d+) path=([^ ]+) fallback=vanilla",
            combined_logs,
        )
    ]
    gui_asset_update_failure_events = len(
        re.findall(r"Rust VulkanicGAL GUI asset update failed .*?preserve_last_valid=true", combined_logs)
    )
    world_border_asset_resolutions = [
        {
            "generation": int(match.group(1)),
            "texture_id": int(match.group(2)),
            "path": match.group(3),
            "source_pack": match.group(4),
            "payloads": int(match.group(5)),
            "payload_bytes": int(match.group(6)),
            "fallback": match.group(7) == "true",
            "sha256": match.group(8),
        }
        for match in re.finditer(
            r"Rust VulkanicGAL world-border asset resolved generation=(\d+) texture_id=(\d+) path=([^ ]+) source_pack=([^ ]+) payloads=(\d+) payload_bytes=(\d+) fallback=(true|false) sha256=(\S+)",
            combined_logs,
        )
    ]
    world_border_asset_update_failure_events = len(
        re.findall(r"Rust VulkanicGAL world-border asset update failed .*?preserve_last_valid=true", combined_logs)
    ) + len(
        re.findall(r"Rust VulkanicGAL world-border asset update skipped .*?preserve_last_valid=true", combined_logs)
    )
    rust_gal_operations = {
        "context_create": {
            "calls": last_number(combined_logs, r"rust_gal_ffi_context_create_calls[=: ]+(\d+)"),
            "bytes": last_number(combined_logs, r"rust_gal_ffi_context_create_bytes[=: ]+(\d+)"),
        },
        "capability_query": {
            "calls": last_number(combined_logs, r"rust_gal_ffi_capability_calls[=: ]+(\d+)"),
            "bytes": last_number(combined_logs, r"rust_gal_ffi_capability_bytes[=: ]+(\d+)"),
        },
        "frame_configure": {
            "calls": last_number(combined_logs, r"rust_gal_ffi_frame_configure_calls[=: ]+(\d+)"),
            "bytes": last_number(combined_logs, r"rust_gal_ffi_frame_configure_bytes[=: ]+(\d+)"),
        },
        "frame_acquire": {
            "calls": last_number(combined_logs, r"rust_gal_ffi_frame_acquire_calls[=: ]+(\d+)"),
            "bytes": last_number(combined_logs, r"rust_gal_ffi_frame_acquire_bytes[=: ]+(\d+)"),
        },
        "frame_resize": {
            "calls": last_number(combined_logs, r"rust_gal_ffi_frame_resize_calls[=: ]+(\d+)"),
            "bytes": last_number(combined_logs, r"rust_gal_ffi_frame_resize_bytes[=: ]+(\d+)"),
        },
        "frame_present": {
            "calls": last_number(combined_logs, r"rust_gal_ffi_frame_present_calls[=: ]+(\d+)"),
            "bytes": last_number(combined_logs, r"rust_gal_ffi_frame_present_bytes[=: ]+(\d+)"),
        },
        "resource_batch": {
            "calls": last_number(combined_logs, r"rust_gal_ffi_resource_batch_calls[=: ]+(\d+)"),
            "bytes": last_number(combined_logs, r"rust_gal_ffi_resource_batch_bytes[=: ]+(\d+)"),
        },
        "submit": {
            "calls": last_number(combined_logs, r"rust_gal_ffi_submit_calls[=: ]+(\d+)"),
            "bytes": last_number(combined_logs, r"rust_gal_ffi_submit_bytes[=: ]+(\d+)"),
        },
        "completion_query": {
            "calls": last_number(combined_logs, r"rust_gal_ffi_completion_query_calls[=: ]+(\d+)"),
            "bytes": last_number(combined_logs, r"rust_gal_ffi_completion_query_bytes[=: ]+(\d+)"),
        },
        "retire": {
            "calls": last_number(combined_logs, r"rust_gal_ffi_retire_calls[=: ]+(\d+)"),
            "bytes": last_number(combined_logs, r"rust_gal_ffi_retire_bytes[=: ]+(\d+)"),
        },
        "asset_update": {
            "calls": last_number(combined_logs, r"rust_gal_ffi_asset_update_calls[=: ]+(\d+)"),
            "bytes": last_number(combined_logs, r"rust_gal_ffi_asset_update_bytes[=: ]+(\d+)"),
        },
        "world_border_asset_update": {
            "calls": last_number(combined_logs, r"rust_gal_ffi_world_border_asset_update_calls[=: ]+(\d+)"),
            "bytes": last_number(combined_logs, r"rust_gal_ffi_world_border_asset_update_bytes[=: ]+(\d+)"),
        },
        "world_crack_asset_update": {
            "calls": last_number(combined_logs, r"rust_gal_ffi_world_crack_asset_update_calls[=: ]+(\d+)"),
            "bytes": last_number(combined_logs, r"rust_gal_ffi_world_crack_asset_update_bytes[=: ]+(\d+)"),
        },
        "world_material_asset_update": {
            "calls": last_number(combined_logs, r"rust_gal_ffi_world_material_asset_update_calls[=: ]+(\d+)"),
            "bytes": last_number(combined_logs, r"rust_gal_ffi_world_material_asset_update_bytes[=: ]+(\d+)"),
        },
        "world_mesh_asset_update": {
            "calls": last_number(combined_logs, r"rust_gal_ffi_world_mesh_asset_update_calls[=: ]+(\d+)"),
            "bytes": last_number(combined_logs, r"rust_gal_ffi_world_mesh_asset_update_bytes[=: ]+(\d+)"),
        },
    }
    rust_gal_calls_per_batch = None
    rust_gal_bytes_per_batch = None
    rust_gal_calls_per_frame = None
    rust_gal_bytes_per_frame = None
    if rust_gal_batches and rust_gal_batches > 0:
        if rust_gal_ffi_calls is not None:
            rust_gal_calls_per_batch = rust_gal_ffi_calls / rust_gal_batches
        if rust_gal_ffi_bytes is not None:
            rust_gal_bytes_per_batch = rust_gal_ffi_bytes / rust_gal_batches
    if rust_gal_frames and rust_gal_frames > 0:
        if rust_gal_ffi_calls is not None:
            rust_gal_calls_per_frame = rust_gal_ffi_calls / rust_gal_frames
        if rust_gal_ffi_bytes is not None:
            rust_gal_bytes_per_frame = rust_gal_ffi_bytes / rust_gal_frames
    rust_gal_timing_totals = {
        "java_producer_enqueue_nanos": last_number(combined_logs, r"rust_gal_enqueue_nanos[=: ]+(\d+)"),
        "resource_lookup_nanos": last_number(combined_logs, r"rust_gal_resource_lookup_nanos[=: ]+(\d+)"),
        "resource_create_nanos": last_number(combined_logs, r"rust_gal_resource_create_nanos[=: ]+(\d+)"),
        "abi_packing_nanos": last_number(combined_logs, r"rust_gal_abi_packing_nanos[=: ]+(\d+)"),
        "frame_acquire_nanos": last_number(combined_logs, r"rust_gal_frame_acquire_nanos[=: ]+(\d+)"),
        "submit_nanos": last_number(combined_logs, r"rust_gal_submit_nanos[=: ]+(\d+)"),
        "frame_present_nanos": last_number(combined_logs, r"rust_gal_frame_present_nanos[=: ]+(\d+)"),
        "retire_nanos": last_number(combined_logs, r"rust_gal_retire_nanos[=: ]+(\d+)"),
        "completion_query_nanos": last_number(combined_logs, r"rust_gal_completion_query_nanos[=: ]+(\d+)"),
        "execute_nanos": last_number(combined_logs, r"rust_gal_execute_nanos[=: ]+(\d+)"),
    }
    rust_gal_backend_sync = {
        "command_lists": last_number(combined_logs, r"rust_gal_command_lists[=: ]+(\d+)"),
        "command_ops": last_number(combined_logs, r"rust_gal_command_ops[=: ]+(\d+)"),
        "backend_submissions": last_number(combined_logs, r"rust_gal_backend_submissions[=: ]+(\d+)"),
        "backend_waits": last_number(combined_logs, r"rust_gal_backend_waits[=: ]+(\d+)"),
        "gl_calls": last_number(combined_logs, r"rust_gal_gl_calls[=: ]+(\d+)"),
        "gl_flushes": last_number(combined_logs, r"rust_gal_gl_flushes[=: ]+(\d+)"),
        "gl_finishes": last_number(combined_logs, r"rust_gal_gl_finishes[=: ]+(\d+)"),
        "gl_fences_inserted": last_number(combined_logs, r"rust_gal_gl_fences_inserted[=: ]+(\d+)"),
        "gl_fences_polled": last_number(combined_logs, r"rust_gal_gl_fences_polled[=: ]+(\d+)"),
        "gl_fences_waited": last_number(combined_logs, r"rust_gal_gl_fences_waited[=: ]+(\d+)"),
        "gl_fences_deleted": last_number(combined_logs, r"rust_gal_gl_fences_deleted[=: ]+(\d+)"),
    }
    rust_gal_timing_per_batch = {}
    if rust_gal_batches and rust_gal_batches > 0:
        rust_gal_timing_per_batch = {
            name: value / rust_gal_batches
            for name, value in rust_gal_timing_totals.items()
            if value is not None
        }
    rust_gal_profile_totals = {
        match.group(1): int(match.group(2))
        for match in re.finditer(r"rust_gal_profile_([A-Za-z0-9_]+)[=: ]+(\d+)", combined_logs)
    }
    rust_gal_profile_per_frame = {}
    if rust_gal_frames and rust_gal_frames > 0:
        rust_gal_profile_per_frame = {
            name: value / rust_gal_frames
            for name, value in rust_gal_profile_totals.items()
        }
    world_profile_name = meta.get("world_profile") or "migration-gate"
    world_profile_meta = {
        "name": world_profile_name,
        "role": meta.get("world_profile_role") or WORLD_PROFILES.get(world_profile_name, WORLD_PROFILES["migration-gate"]).role,
        "configured_world": WORLD_PROFILES.get(world_profile_name, WORLD_PROFILES["migration-gate"]).world,
        "actual_world": meta.get("world") or signature.get("world"),
        "migration_gate_blocking": (meta.get("migration_gate_blocking", "true").lower() == "true"),
    }
    readiness_state = readiness_state_from_text(
        tool_kind,
        failed_phase,
        frame_doc,
        deterministic_doc,
        combined_logs,
        meta,
    )
    complete = (
        bool(files["meta"])
        and (success or reused_baseline)
        and frame_validity_complete
        and world_entered
        and subsystem_complete
        and deterministic_complete
        and world_outline_workload_complete
        and world_crack_workload_complete
        and world_border_workload_complete
        and world_material_marker_workload_complete
        and world_material_terrain_particle_workload_complete
        and static_terrain_workload_complete
        and world_mesh_block_display_workload_complete
        and world_mesh_falling_block_workload_complete
        and world_mesh_piston_workload_complete
        and validation_layer_exercised
        and renderdoc_complete
        and renderdoc_workload_assertions_complete
        and tracy_complete
        and not java_vulkan_has_rust_shell
        and not rust_shell_has_java_vulkan_frame
        and hard_errors_absent
    )
    artifact = {
        "schema": SCHEMA,
        "created_at": utc_now(),
        "tool": tool_kind,
        "mode": asdict(mode),
        "world_profile": world_profile_meta,
        "repository": repository_metadata(target),
        "repository_resolution": repository_paths or {},
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
            "whole_frame_gameplay_attachments": {
                "directory": str(gameplay_attachment_dir) if gameplay_attachment_dir.exists() else None,
                "manifest": str(gameplay_attachment_manifest_path) if gameplay_attachment_manifest_path else None,
                "correlation": str(gameplay_attachment_correlation_path) if gameplay_attachment_correlation_path else None,
                "manifest_doc": gameplay_attachment_doc if isinstance(gameplay_attachment_doc, dict) else None,
                "correlation_doc": gameplay_attachment_correlation_doc if isinstance(gameplay_attachment_correlation_doc, dict) else None,
            },
        },
        "runtime_profile": runtime_profile or {},
        "benchmark_fingerprint": benchmark_fingerprint,
        "implementation_attribution": attribution,
        "expected_attribution": mode.expected_attribution,
        "expected_base_backend": mode.expected_attribution,
        "implementation_attribution_families": attribution_families,
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
                "current_thread_allocated_bytes_start": java_doc.get("currentThreadAllocatedBytesAtStart") if isinstance(java_doc, dict) else None,
                "current_thread_allocated_bytes_end": java_doc.get("currentThreadAllocatedBytesAtEnd") if isinstance(java_doc, dict) else None,
                "current_thread_allocated_bytes_delta": java_doc.get("currentThreadAllocatedBytesDelta") if isinstance(java_doc, dict) else None,
                "allocation_bytes": (
                    java_doc.get("currentThreadAllocatedBytesDelta")
                    if isinstance(java_doc, dict) and parse_number(java_doc.get("currentThreadAllocatedBytesDelta")) is not None
                    else first_number(combined_logs, r"allocation(?:_bytes| bytes)?[=: ]+(\d+)")
                ),
            },
            "frame_sampler_validity": frame_validity,
            "rust_allocation": {
                "allocation_bytes": first_number(combined_logs, r"rust(?:_native)?_allocation(?:_bytes| bytes)?[=: ]+(\d+)"),
            },
            "rss_and_native_memory": {
                "peak_rss_kb": parse_number(meta.get("memory_guard_peak_rss_kb")),
                "rss_guard_triggered": memory_guard_triggered,
                "native_or_vulkan_memory_bytes": first_number(combined_logs, r"(?:native|vulkan).*memory.*?(\d+)"),
                "last_proc_rss_kb": last_number(combined_logs, r"(?m)^Rss:\s+(\d+) kB"),
                "last_proc_pss_kb": last_number(combined_logs, r"(?m)^Pss:\s+(\d+) kB"),
                "last_proc_private_dirty_kb": last_number(combined_logs, r"(?m)^Private_Dirty:\s+(\d+) kB"),
                "last_proc_swap_kb": last_number(combined_logs, r"(?m)^Swap:\s+(\d+) kB"),
            },
            "ffi": {
                "call_count": rust_gal_ffi_calls,
                "bytes": rust_gal_ffi_bytes,
            },
            "rust_gal_slice": {
                "producer": last_text(combined_logs, r"Rust (?:OpenGL )?VulkanicGAL GUI (?:batch|frame) executed producer=([^ ]+)"),
                "world_outline_scenario": requested_world_outline_scenario or None,
                "world_outline_real_target": requested_world_outline_real_target,
                "world_outline_aim_real_target": requested_world_outline_aim_real_target,
                "world_outline_style": requested_world_outline_style,
                "world_outline_depth_policy": (deterministic_doc or {}).get("rustGalWorldOutlineDepthPolicy") if isinstance(deterministic_doc, dict) else None,
                "world_outline_pixel_evidence": world_outline_pixel_evidence,
                "world_outline_framebuffer_evidence": world_outline_framebuffer,
                "world_crack_scenario": requested_world_crack_scenario or None,
                "world_crack_stage": parse_number(requested_world_crack_stage),
                "world_crack_real_survival_required": requested_world_crack_real_survival,
                "world_crack_real_hit_type": real_world_crack_hit_type,
                "world_crack_real_setup_block": real_world_crack_setup_block,
                "world_crack_real_setup_target": real_world_crack_setup_target,
                "world_crack_real_target": real_world_crack_target,
                "world_crack_real_direction": real_world_crack_direction,
                "world_crack_real_status": real_world_crack_status,
                "world_crack_real_last_valid_target": real_world_crack_last_valid_target,
                "world_crack_real_last_valid_block_type": real_world_crack_last_valid_block_type,
                "world_crack_real_last_rendered_target": real_world_crack_last_rendered_target,
                "world_crack_real_last_rendered_block_type": real_world_crack_last_rendered_block_type,
                "world_crack_real_start_calls": real_world_crack_start_calls,
                "world_crack_real_continue_calls": real_world_crack_continue_calls,
                "world_crack_real_stop_calls": real_world_crack_stop_calls,
                "world_crack_real_valid_block_hits": real_world_crack_valid_block_hits,
                "world_crack_real_rendered_state_count": real_world_crack_rendered_state_count,
                "world_crack_real_min_rendered_stage": real_world_crack_min_rendered_stage,
                "world_crack_real_max_rendered_stage": real_world_crack_max_rendered_stage,
                "world_crack_real_first_summary": real_world_crack_first_summary,
                "world_crack_real_destroy_progress_states": real_world_crack_states_for_validation,
                "world_crack_real_semantic_quads": real_world_crack_quads_for_validation,
                "world_crack_disabled_control": requested_world_crack_disabled,
                "world_crack_legacy_control": requested_world_crack_legacy,
                "world_crack_pixel_evidence": world_crack_pixel_evidence,
                "world_crack_framebuffer_evidence": world_crack_framebuffer,
                "world_border_scenario": requested_world_border_scenario or None,
                "world_border_pixel_evidence": world_border_pixel_evidence,
                "world_background_scenario": requested_world_background_scenario or None,
                "world_material_marker_pixel_evidence": world_material_marker_pixel_evidence,
                "world_material_terrain_particle_scenario": requested_world_material_terrain_particle_scenario or None,
                "world_material_terrain_particle_real_gameplay": requested_world_material_terrain_particle_real_gameplay,
                "world_material_terrain_particle_control": world_material_terrain_particle_control,
                "world_material_terrain_particle_real_metrics": terrain_particle_real_doc,
                "world_material_terrain_particle_pixel_evidence": world_material_terrain_particle_pixel_evidence,
                "world_material_terrain_particle_quads": (
                    rust_gal_world_material_terrain_particle_quads
                    if rust_gal_world_material_terrain_particle_quads is not None
                    else (rust_gal_world_material_quads if requested_world_material_terrain_particle_real_gameplay else None)
                ),
                "world_material_terrain_particle_texture_mask": rust_gal_world_material_terrain_particle_texture_mask,
                "world_static_terrain_scenario": requested_world_static_terrain_scenario or None,
                "world_static_terrain_fault": requested_world_static_terrain_fault or None,
                "world_static_terrain_resource_pack_scenario": requested_world_static_terrain_resource_pack_scenario or None,
                "world_static_terrain_expected_fault": static_terrain_expected_fault,
                "world_static_terrain_submitted_work": static_terrain_submitted_count,
                "world_static_terrain_diagnostics": static_terrain_doc if static_terrain_doc else static_terrain_frame_doc,
                "world_static_terrain_geometry_evidence": static_terrain_geometry,
                "world_static_terrain_lifecycle": static_terrain_lifecycle_doc,
                "world_static_terrain_lifecycle_evidence": static_terrain_lifecycle,
                "world_mesh_block_display_scenario": requested_world_mesh_block_display_scenario or None,
                "world_mesh_block_display_workload": requested_world_mesh_block_display_workload or None,
                "world_mesh_block_display_control": world_mesh_block_display_control,
                "world_mesh_block_display_submitted_work": block_display_submitted_count,
                "world_mesh_block_display_metrics": block_display_doc,
                "world_mesh_block_display_pixel_evidence": world_mesh_block_display_pixel_evidence,
                "world_mesh_falling_block_scenario": requested_world_mesh_falling_block_scenario or None,
                "world_mesh_falling_block_control": world_mesh_falling_block_control,
                "world_mesh_falling_block_submitted_work": falling_block_submitted_count,
                "world_mesh_falling_block_metrics": falling_block_doc,
                "world_mesh_falling_block_route_counts": {
                    **{str(key): int(parse_number(value) or 0) for key, value in falling_block_route_counts.items()},
                    **{
                        f"deterministic:{key}": value
                        for key, value in deterministic_falling_block_route_counts.items()
                    },
                },
                "world_mesh_falling_block_pixel_evidence": world_mesh_falling_block_pixel_evidence,
                "world_mesh_piston_scenario": requested_world_mesh_piston_scenario or None,
                "world_mesh_piston_control": world_mesh_piston_control,
                "world_mesh_piston_submitted_work": piston_submitted_count,
                "world_mesh_piston_metrics": piston_doc,
                "world_mesh_piston_positive_control_delay_nanos": int(
                    parse_number(frame_doc.get("positiveControlDelayNanos")) or 0
                )
                if isinstance(frame_doc, dict)
                else 0,
                "world_mesh_piston_controlled_performance": {
                    "profile": runtime_profile.get("name") if isinstance(runtime_profile, dict) else None,
                    "gc_before_measurement": frame_doc.get("gcBeforeMeasurement") if isinstance(frame_doc, dict) else None,
                    "pre_measurement_gc_issued": frame_doc.get("preMeasurementGcIssued") if isinstance(frame_doc, dict) else None,
                    "screenshots_allowed": meta.get("screenshot_max_count"),
                },
                "world_mesh_piston_route_counts": {
                    **{str(key): int(parse_number(value) or 0) for key, value in piston_moving_route_counts.items()},
                    **{
                        f"deterministic:{key}": value
                        for key, value in deterministic_piston_route_counts.items()
                    },
                },
                "world_mesh_piston_shell_scan": piston_shell_scan,
                "world_mesh_piston_pixel_evidence": world_mesh_piston_pixel_evidence,
                "rust_vulkan_shell_scene_evidence": rust_vulkan_shell_scene_evidence,
                "cache_hits": last_number(combined_logs, r"rust_gal_cache_hits[=: ]+(\d+)"),
                "cache_misses": last_number(combined_logs, r"rust_gal_cache_misses[=: ]+(\d+)"),
                "queue_depth": last_number(combined_logs, r"rust_gal_queue_depth[=: ]+(\d+)"),
                "frames_executed": rust_gal_frames,
                "batches_executed": rust_gal_batches,
                "sprite_batches_executed": rust_gal_sprite_batches,
                "packed_sprites_executed": rust_gal_packed_sprites,
                "world_primitive_batches_executed": rust_gal_world_batches,
                "world_line_segments_executed": rust_gal_world_segments,
                "world_line_vertices_executed": rust_gal_world_vertices,
                "world_primitive_draws_executed": rust_gal_world_draws,
                "world_crack_quads_executed": rust_gal_world_crack_quads,
                "world_crack_batches_executed": rust_gal_world_crack_batches,
                "world_crack_draws_executed": rust_gal_world_crack_draws,
                "world_border_quads_executed": rust_gal_world_border_quads,
                "world_border_batches_executed": rust_gal_world_border_batches,
                "world_border_draws_executed": rust_gal_world_border_draws,
                "world_material_marker_scenario": requested_world_material_marker_scenario or None,
                "world_material_quads_executed": rust_gal_world_material_quads,
                "world_material_batches_executed": rust_gal_world_material_batches,
                "world_material_draws_executed": rust_gal_world_material_draws,
                "world_material_marker_barrier_quads": rust_gal_world_material_marker_barrier_quads,
                "world_material_marker_light_quads": rust_gal_world_material_marker_light_quads,
                "world_material_marker_light_level_mask": rust_gal_world_material_marker_light_level_mask,
                "world_material_marker_last_light_level": rust_gal_world_material_marker_last_light_level,
                "world_material_marker_last_texture_id": rust_gal_world_material_marker_last_texture_id,
                "world_mesh_instances_executed": rust_gal_world_mesh_instances,
                "world_mesh_batches_executed": rust_gal_world_mesh_batches,
                "world_mesh_draws_executed": rust_gal_world_mesh_draws,
                "world_background_clears_executed": rust_gal_world_background_clears,
                "world_background_diagnostic_fallbacks": rust_gal_world_background_fallbacks,
                "world_background_sky_type": rust_gal_world_background_sky_type,
                "world_background_color_argb": rust_gal_world_background_color_argb,
                "world_depth_attachment_creates": rust_gal_world_depth_creates,
                "world_depth_attachment_reuses": rust_gal_world_depth_reuses,
                "world_depth_attachment_retires": rust_gal_world_depth_retires,
                "world_outline_cache_hits": rust_gal_world_outline_cache_hits,
                "world_outline_cache_misses": rust_gal_world_outline_cache_misses,
                "world_crack_cache_hits": rust_gal_world_crack_cache_hits,
                "world_crack_cache_misses": rust_gal_world_crack_cache_misses,
                "world_crack_asset_generation": last_number(combined_logs, r"rust_gal_world_crack_asset_generation[=: ]+(\d+)"),
                "world_crack_uploaded_asset_generation": last_number(combined_logs, r"rust_gal_world_crack_uploaded_asset_generation[=: ]+(\d+)"),
                "world_crack_asset_payload_count": last_number(combined_logs, r"rust_gal_world_crack_asset_payload_count[=: ]+(\d+)"),
                "world_crack_asset_payload_bytes": last_number(combined_logs, r"rust_gal_world_crack_asset_payload_bytes[=: ]+(\d+)"),
                "world_crack_asset_update_failures": last_number(combined_logs, r"rust_gal_world_crack_asset_update_failures[=: ]+(\d+)"),
                "world_crack_asset_source_pack": last_text(combined_logs, r"rust_gal_world_crack_asset_source_pack[=: ]+(\S+)"),
                "world_crack_asset_sha256": last_text(combined_logs, r"rust_gal_world_crack_asset_sha256[=: ]+(\S+)"),
                "world_crack_asset_fallback": last_text(combined_logs, r"rust_gal_world_crack_asset_fallback[=: ]+(\S+)"),
                "world_border_cache_hits": rust_gal_world_border_cache_hits,
                "world_border_cache_misses": rust_gal_world_border_cache_misses,
                "world_border_asset_generation": last_number(combined_logs, r"rust_gal_world_border_asset_generation[=: ]+(\d+)"),
                "world_border_uploaded_asset_generation": last_number(combined_logs, r"rust_gal_world_border_uploaded_asset_generation[=: ]+(\d+)"),
                "world_border_asset_payload_count": last_number(combined_logs, r"rust_gal_world_border_asset_payload_count[=: ]+(\d+)"),
                "world_border_asset_payload_bytes": last_number(combined_logs, r"rust_gal_world_border_asset_payload_bytes[=: ]+(\d+)"),
                "world_border_asset_update_failures": last_number(combined_logs, r"rust_gal_world_border_asset_update_failures[=: ]+(\d+)"),
                "world_border_asset_source_pack": last_text(combined_logs, r"rust_gal_world_border_asset_source_pack[=: ]+(\S+)"),
                "world_border_asset_sha256": last_text(combined_logs, r"rust_gal_world_border_asset_sha256[=: ]+(\S+)"),
                "world_border_asset_fallback": last_text(combined_logs, r"rust_gal_world_border_asset_fallback[=: ]+(\S+)"),
                "world_border_asset_resolutions": world_border_asset_resolutions,
                "world_border_asset_update_failure_events": world_border_asset_update_failure_events,
                "world_material_cache_hits": rust_gal_world_material_cache_hits,
                "world_material_cache_misses": rust_gal_world_material_cache_misses,
                "world_mesh_cache_hits": rust_gal_world_mesh_cache_hits,
                "world_mesh_cache_misses": rust_gal_world_mesh_cache_misses,
                "world_mesh_asset_generation": last_number(combined_logs, r"rust_gal_world_mesh_asset_generation[=: ]+(\d+)"),
                "world_mesh_uploaded_asset_generation": last_number(combined_logs, r"rust_gal_world_mesh_uploaded_asset_generation[=: ]+(\d+)"),
                "world_mesh_asset_payload_count": last_number(combined_logs, r"rust_gal_world_mesh_asset_payload_count[=: ]+(\d+)"),
                "world_mesh_asset_payload_bytes": last_number(combined_logs, r"rust_gal_world_mesh_asset_payload_bytes[=: ]+(\d+)"),
                "world_mesh_asset_update_failures": last_number(combined_logs, r"rust_gal_world_mesh_asset_update_failures[=: ]+(\d+)"),
                "world_material_asset_generation": last_number(combined_logs, r"rust_gal_world_material_asset_generation[=: ]+(\d+)"),
                "world_material_uploaded_asset_generation": last_number(combined_logs, r"rust_gal_world_material_uploaded_asset_generation[=: ]+(\d+)"),
                "world_material_asset_payload_count": last_number(combined_logs, r"rust_gal_world_material_asset_payload_count[=: ]+(\d+)"),
                "world_material_asset_payload_bytes": last_number(combined_logs, r"rust_gal_world_material_asset_payload_bytes[=: ]+(\d+)"),
                "world_material_asset_update_failures": last_number(combined_logs, r"rust_gal_world_material_asset_update_failures[=: ]+(\d+)"),
                "world_material_asset_source_pack": last_text(combined_logs, r"rust_gal_world_material_asset_source_pack[=: ]+(\S+)"),
                "world_material_asset_sha256": last_text(combined_logs, r"rust_gal_world_material_asset_sha256[=: ]+(\S+)"),
                "world_material_asset_fallback": last_text(combined_logs, r"rust_gal_world_material_asset_fallback[=: ]+(\S+)"),
                "swapchain_recreations": rust_gal_swapchain_recreations,
                "frame_target_generations": last_number(combined_logs, r"rust_gal_frame_target_generations[=: ]+(\d+)"),
                "frame_target_identity_changes": last_number(combined_logs, r"rust_gal_frame_target_identity_changes[=: ]+(\d+)"),
                "last_frame_target_generation": last_number(combined_logs, r"rust_gal_last_frame_target_generation[=: ]+(\d+)"),
                "last_frame_target_identity": last_number(combined_logs, r"rust_gal_last_frame_target_identity[=: ]+(\d+)"),
                "batches_cancelled": last_number(combined_logs, r"rust_gal_batches_cancelled[=: ]+(\d+)"),
                "completion_polls": last_number(combined_logs, r"rust_gal_completion_polls[=: ]+(\d+)"),
                "completion_timeouts": last_number(combined_logs, r"rust_gal_completion_timeouts[=: ]+(\d+)"),
                "asset_generation": last_number(combined_logs, r"rust_gal_asset_generation[=: ]+(\d+)"),
                "uploaded_asset_generation": last_number(combined_logs, r"rust_gal_uploaded_asset_generation[=: ]+(\d+)"),
                "asset_payload_count": last_number(combined_logs, r"rust_gal_asset_payload_count[=: ]+(\d+)"),
                "asset_payload_bytes": last_number(combined_logs, r"rust_gal_asset_payload_bytes[=: ]+(\d+)"),
                "asset_update_failures": last_number(combined_logs, r"rust_gal_asset_update_failures[=: ]+(\d+)"),
                "asset_resolutions": gui_asset_resolutions,
                "asset_missing_fallbacks": gui_asset_missing,
                "asset_update_failure_events": gui_asset_update_failure_events,
                "ffi_operations": rust_gal_operations,
                "ffi_calls_per_frame": rust_gal_calls_per_frame,
                "ffi_bytes_per_frame": rust_gal_bytes_per_frame,
                "ffi_calls_per_executed_batch": rust_gal_calls_per_batch,
                "ffi_bytes_per_executed_batch": rust_gal_bytes_per_batch,
                "timing_totals_nanos": rust_gal_timing_totals,
                "timing_per_executed_batch_nanos": rust_gal_timing_per_batch,
                "profile_totals_nanos": rust_gal_profile_totals,
                "profile_per_frame_nanos": rust_gal_profile_per_frame,
                "backend_sync": rust_gal_backend_sync,
                "submission": last_number(combined_logs, r"Rust (?:OpenGL )?VulkanicGAL GUI (?:batch|frame) executed.*?submission=(\d+)"),
            },
            "native_direct_triggers": {
                "integrations": last_number(combined_logs, r"direct_trigger_diagnostics summary.*? integrations=(\d+)"),
                "catchup_integrations": last_number(combined_logs, r"direct_trigger_diagnostics summary.*? catchup_integrations=(\d+)"),
                "average_delay_frames": last_number(combined_logs, r"direct_trigger_diagnostics summary.*? average_delay_frames=([0-9.]+)"),
                "max_delay_frames": last_number(combined_logs, r"direct_trigger_diagnostics summary.*? max_delay_frames=(\d+)"),
                "average_catchup_movement": last_number(combined_logs, r"direct_trigger_diagnostics summary.*? average_catchup_movement=([0-9.]+)"),
                "max_catchup_movement": last_number(combined_logs, r"direct_trigger_diagnostics summary.*? max_catchup_movement=([0-9.]+)"),
                "native_process_calls": last_number(combined_logs, r"direct_trigger_diagnostics summary.*? native_process_calls=(\d+)"),
                "native_integrate_calls": last_number(combined_logs, r"direct_trigger_diagnostics summary.*? native_integrate_calls=(\d+)"),
                "native_catchup_integrations": last_number(combined_logs, r"direct_trigger_diagnostics summary.*? native_catchup_integrations=(\d+)"),
                "native_angle_path_integrations": last_number(combined_logs, r"direct_trigger_diagnostics summary.*? native_angle_path_integrations=(\d+)"),
                "native_distance_path_integrations": last_number(combined_logs, r"direct_trigger_diagnostics summary.*? native_distance_path_integrations=(\d+)"),
                "native_invalid_angle_input_fallbacks": last_number(combined_logs, r"direct_trigger_diagnostics summary.*? native_invalid_angle_input_fallbacks=(\d+)"),
            },
            "work_counts": work_counts,
            "creation_counts": creation_counts,
            "vulkan": vulkan_perf,
            "validation_findings": {
                "profile": meta.get("validation_profile") or meta.get("validation_mode", "off"),
                "finding_count": len(validation_findings),
                "occurrence_count": sum(int(finding.get("count") or 0) for finding in validation_findings),
                "concrete_vuid_count": concrete_validation_count,
                "categories": validation_categories,
                "findings": validation_findings,
                "proof": proof,
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
            "vulkan_validation_passed": concrete_validation_count == 0,
            "vulkan_validation_clean": validation_run and validation_layer_exercised and concrete_validation_count == 0,
            "validation_layer_exercised": validation_layer_exercised,
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
            "vulkan_validation_note": loader_only_validation_note or None,
            "notes": validation_notes,
            "readiness_state": readiness_state,
            "migration_gate_blocking": world_profile_meta["migration_gate_blocking"],
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
            readiness_state,
        ),
    }
    artifact["benchmark_fingerprint"]["hash"] = stable_json_hash(artifact["benchmark_fingerprint"])
    return artifact


def first_number(text: str, pattern: str) -> float | None:
    match = re.search(pattern, text, re.IGNORECASE)
    if not match:
        return None
    return parse_number(match.group(1))


def last_number(text: str, pattern: str) -> float | None:
    last: float | None = None
    for match in re.finditer(pattern, text, re.IGNORECASE):
        parsed = parse_number(match.group(1))
        if parsed is not None:
            last = parsed
    return last


def max_number(text: str, pattern: str) -> float | None:
    maximum: float | None = None
    for match in re.finditer(pattern, text, re.IGNORECASE):
        parsed = parse_number(match.group(1))
        if parsed is not None and (maximum is None or parsed > maximum):
            maximum = parsed
    return maximum


def first_text(text: str, pattern: str) -> str | None:
    match = re.search(pattern, text, re.IGNORECASE)
    if not match:
        return None
    return match.group(1)


def last_text(text: str, pattern: str) -> str | None:
    last: str | None = None
    for match in re.finditer(pattern, text, re.IGNORECASE):
        last = match.group(1)
    return last


def incomplete_run_diagnosis(
    tool_kind: str,
    failed_phase: str | None,
    frame_doc: dict[str, object] | None,
    subsystem_doc: dict[str, object] | None,
    deterministic_doc: dict[str, object] | None,
    error_counts: dict[str, int],
    memory_guard_triggered: bool,
    attribution: str,
    readiness_state: dict[str, object] | None = None,
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
    if isinstance(readiness_state, dict):
        if readiness_state.get("timeout_classification"):
            evidence.append(f"readiness_timeout_classification={readiness_state.get('timeout_classification')}")
        if readiness_state.get("last_screen"):
            evidence.append(f"last_screen={readiness_state.get('last_screen')}")
        if readiness_state.get("last_overlay"):
            evidence.append(f"last_overlay={readiness_state.get('last_overlay')}")
        dh = readiness_state.get("dh")
        if isinstance(dh, dict):
            evidence.append(f"dh_state={dh.get('state')}")
            evidence.append(f"dh_world_gen_threads={dh.get('world_gen_thread_mentions')}")
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
    if isinstance(readiness_state, dict) and readiness_state.get("timeout_classification"):
        root_cause = str(readiness_state["timeout_classification"])
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
        f"world={getattr(args, 'world', 'Origin')}",
        f"world_profile={env.get('MATTMC_GRAPHICS_WORLD_PROFILE', getattr(args, 'world_profile', 'migration-gate'))}",
        f"world_profile_role={env.get('MATTMC_GRAPHICS_WORLD_PROFILE_ROLE', '')}",
        f"migration_gate_blocking={env.get('MATTMC_GRAPHICS_MIGRATION_GATE_BLOCKING', 'true')}",
        f"validation_mode={getattr(args, 'validation', 'off')}",
        f"graphics_run_type={env.get('MATTMC_GRAPHICS_RUN_TYPE', run_type_for_args(args))}",
        f"validation_profile={env.get('MATTMC_GRAPHICS_VALIDATION_PROFILE', getattr(args, 'validation', 'off'))}",
        f"validation_fail_severity={env.get('MATTMC_GRAPHICS_VALIDATION_FAIL_SEVERITY', 'warning')}",
        f"validation_enabled={'true' if 'VK_LAYER_KHRONOS_validation' in env.get('VK_INSTANCE_LAYERS', '') else 'false'}",
        f"vk_instance_layers={env.get('VK_INSTANCE_LAYERS', '')}",
        f"vk_layer_settings={env.get('VK_LAYER_SETTINGS', '')}",
        f"vk_add_layer_path={env.get('VK_ADD_LAYER_PATH', '')}",
        f"vk_loader_debug={env.get('VK_LOADER_DEBUG', '')}",
        f"renderdoc_capture={env.get('MATTMC_RENDERDOC_CAPTURE', 'false')}",
        f"renderdoc_frame={env.get('MATTMC_RENDERDOC_FRAME', '0')}",
        f"renderdoc_capture_path={env.get('MATTMC_RENDERDOC_CAPTURE_PATH', '')}",
        f"renderdoc_vulkan_layer_manifest={env.get('MATTMC_RENDERDOC_VULKAN_LAYER_MANIFEST', '')}",
        f"tracy_capture={env.get('MATTMC_TRACY_CAPTURE', 'false')}",
        f"tracy_duration_seconds={env.get('MATTMC_TRACY_DURATION_SECONDS', '0')}",
        f"tracy_max_size_mb={env.get('MATTMC_TRACY_MAX_SIZE_MB', '0')}",
        f"client_args_initial={getattr(args, 'client_args', '')}",
        "preflight_failure=true",
    ]
    path = capture_dir / f"meta_preflight_{timestamp()}.txt"
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return path


def diagnose_renderdoc_capture_failure(capture_dir: Path, capture_path: Path | None = None) -> dict[str, Any]:
    log_text = ""
    for _ in range(100):
        for pattern in ("latest_tail_*.log", "latest_*.log", "runClient_*.log"):
            for path in sorted(capture_dir.glob(pattern))[-2:]:
                try:
                    log_text += "\n" + path.read_text(encoding="utf-8", errors="replace")[-2_000_000:]
                except OSError:
                    pass
        if log_text:
            break
        time.sleep(0.2)
    end_results = re.findall(r"Ended RenderDoc frame capture \(([^)]+)\).*?result=([0-9-]+)", log_text)
    triggered = re.findall(r"Triggered RenderDoc capture for next deterministic frame \(([^)]+)\)", log_text)
    started = re.findall(r"Started RenderDoc frame capture \(([^)]+)\)", log_text)
    initialized = "RenderDoc API initialized" in log_text
    present = bool(capture_path and capture_path.exists())
    likely_cause = None
    if initialized and (triggered or started or end_results) and not present:
        likely_cause = "RenderDoc API was reached and the deterministic workload ran, but no .rdc was persisted."
    elif not initialized:
        likely_cause = "RenderDoc API did not initialize before the run ended."
    elif not started:
        likely_cause = "RenderDoc API initialized, but no frame capture start was observed."
    return {
        "capture_file_present": present,
        "renderdoc_api_initialized": initialized,
        "frame_capture_started": bool(started),
        "frame_capture_triggered": bool(triggered),
        "frame_capture_ended": bool(end_results),
        "capture_labels_started": started[:8],
        "capture_labels_triggered": triggered[:8],
        "end_results": [{"label": label, "result": result} for label, result in end_results[:8]],
        "likely_cause": likely_cause,
        "vulkan_layer": renderdoc_vulkan_layer_diagnosis(),
    }


def renderdoc_workload_proof(capture_dir: Path) -> dict[str, object]:
    log_text = ""
    for pattern in ("latest_tail_*.log", "latest_*.log", "runClient_*.log"):
        for path in sorted(capture_dir.glob(pattern))[-2:]:
            try:
                log_text += "\n" + path.read_text(encoding="utf-8", errors="replace")[-400000:]
            except OSError:
                pass
    outline_counts = {
        "primitive_batches": last_number(log_text, r"rust_gal_world_primitive_batches_executed[=: ]+(\d+)"),
        "line_segments": last_number(log_text, r"rust_gal_world_line_segments_executed[=: ]+(\d+)"),
        "line_vertices": last_number(log_text, r"rust_gal_world_line_vertices_executed[=: ]+(\d+)"),
        "world_draws": last_number(log_text, r"rust_gal_world_primitive_draws_executed[=: ]+(\d+)"),
        "depth_attachment_creates": last_number(log_text, r"rust_gal_world_depth_attachment_creates[=: ]+(\d+)"),
        "depth_attachment_reuses": last_number(log_text, r"rust_gal_world_depth_attachment_reuses[=: ]+(\d+)"),
    }
    crack_counts = {
        "crack_quads": last_number(log_text, r"rust_gal_world_crack_quads_executed[=: ]+(\d+)"),
        "crack_batches": last_number(log_text, r"rust_gal_world_crack_batches_executed[=: ]+(\d+)"),
        "crack_draws": last_number(log_text, r"rust_gal_world_crack_draws_executed[=: ]+(\d+)"),
    }
    border_counts = {
        "border_quads": last_number(log_text, r"rust_gal_world_border_quads_executed[=: ]+(\d+)"),
        "border_batches": last_number(log_text, r"rust_gal_world_border_batches_executed[=: ]+(\d+)"),
        "border_draws": last_number(log_text, r"rust_gal_world_border_draws_executed[=: ]+(\d+)"),
    }
    mesh_counts = {
        "mesh_instances": last_number(log_text, r"rust_gal_world_mesh_instances_executed[=: ]+(\d+)"),
        "mesh_batches": last_number(log_text, r"rust_gal_world_mesh_batches_executed[=: ]+(\d+)"),
        "mesh_draws": last_number(log_text, r"rust_gal_world_mesh_draws_executed[=: ]+(\d+)"),
        "mesh_cache_hits": last_number(log_text, r"rust_gal_world_mesh_cache_hits[=: ]+(\d+)"),
        "mesh_cache_misses": last_number(log_text, r"rust_gal_world_mesh_cache_misses[=: ]+(\d+)"),
    }
    background_counts = {
        "background_clears": last_number(log_text, r"rust_gal_world_background_clears_executed[=: ]+(\d+)"),
        "diagnostic_fallbacks": last_number(log_text, r"rust_gal_world_background_diagnostic_fallbacks[=: ]+(\d+)"),
        "sky_type": last_number(log_text, r"rust_gal_world_background_sky_type[=: ]+(\d+)"),
    }
    background_colors = re.findall(r"rust_gal_world_background_color_argb[=: ]+([0-9a-fA-F]+)", log_text)
    acquired = [
        {
            "correlation": int(correlation),
            "frame": int(frame),
            "image": int(image),
        }
        for correlation, frame, image in re.findall(
            r"gal\.frame\.acquire backend=vulkan correlation=(\d+) frame=(\d+) image=(\d+)",
            log_text,
        )
    ]
    begun = [
        {
            "frame": int(frame),
            "image": int(image),
            "view": view,
            "width": int(width),
            "height": int(height),
            "clear": [float(red), float(green), float(blue), float(alpha)],
        }
        for frame, image, view, width, height, red, green, blue, alpha in re.findall(
            r"gal\.frame\.target\.begin backend=vulkan frame=(\d+) image=(\d+) view=0x([0-9a-fA-F]+) extent=(\d+)x(\d+) "
            r"layout=-?\d+ load=-?\d+ clear=([0-9.\\-]+),([0-9.\\-]+),([0-9.\\-]+),([0-9.\\-]+)",
            log_text,
        )
    ]
    begun.extend(
        {
            "frame": int(frame),
            "image": int(image),
            "view": None,
            "width": int(width),
            "height": int(height),
            "clear": [float(red), float(green), float(blue), float(alpha)],
        }
        for frame, image, width, height, red, green, blue, alpha in re.findall(
            r"gal\.frame\.target\.begin backend=vulkan frame=(\d+) image=(\d+) extent=(\d+)x(\d+) "
            r"[^\n]*?clear=([0-9.\\-]+),([0-9.\\-]+),([0-9.\\-]+),([0-9.\\-]+)",
            log_text,
        )
    )
    present_ready = [
        {"frame": int(frame), "image": int(image)}
        for frame, image in re.findall(
            r"gal\.frame\.target\.present-ready backend=vulkan frame=(\d+) image=(\d+)",
            log_text,
        )
    ]
    presented = [
        {
            "correlation": int(correlation),
            "frame": int(frame),
            "image": int(image),
            "submission": int(submission),
            "status": status,
        }
        for correlation, frame, image, submission, status in re.findall(
            r"gal\.frame\.present backend=vulkan correlation=(\d+) frame=(\d+) image=(\d+) submission=(\d+) status=([A-Za-z0-9_]+)",
            log_text,
        )
    ]
    identity_matches = False
    if acquired and begun and present_ready and presented:
        acquired_by_frame = {item["frame"]: item["image"] for item in acquired}
        begun_by_frame = {item["frame"]: item["image"] for item in begun}
        ready_by_frame = {item["frame"]: item["image"] for item in present_ready}
        presented_by_frame = {item["frame"]: item["image"] for item in presented}
        common = set(acquired_by_frame) & set(begun_by_frame) & set(ready_by_frame) & set(presented_by_frame)
        identity_matches = any(
            acquired_by_frame[frame] == begun_by_frame[frame] == ready_by_frame[frame] == presented_by_frame[frame]
            for frame in common
        )
    blue_clear_expected = "expected=blue-diagnostic-shell" in log_text or any(
        abs(item["clear"][0] - 0.08) < 0.01
        and abs(item["clear"][1] - 0.31) < 0.01
        and abs(item["clear"][2] - 0.74) < 0.01
        or abs(item["clear"][0] - 0.063) < 0.01
        and abs(item["clear"][1] - 0.157) < 0.01
        and abs(item["clear"][2] - 0.855) < 0.01
        for item in begun
    )
    semantic_background_expected = "expected=semantic-world-background" in log_text or int(background_counts["background_clears"] or 0) > 0
    java_vulkan_frame_markers = len(
        re.findall(r"Vulkan beginFramebufferRenderPass|begin_frame_call_count|Java Vulkan beginFrame|Java Vulkan endFrame|presentTextureToScreen", log_text)
    )
    rust_shell_markers = len(
        re.findall(r"mattmc\.dev\.rustGalVulkanWholeFrame=true|Rust VulkanicGAL whole-frame|backend=rust-vulkan|gal\.frame\.acquire backend=vulkan", log_text)
    )
    return {
        "outline": outline_counts,
        "crack": crack_counts,
        "border": border_counts,
        "mesh": mesh_counts,
        "background": {
            **background_counts,
            "color_argb": background_colors[-1] if background_colors else None,
        },
        "non_zero_outline_workload": all(
            int(outline_counts[key] or 0) > 0
            for key in ("primitive_batches", "line_segments", "line_vertices", "world_draws", "depth_attachment_creates")
        ),
        "non_zero_crack_workload": all(
            int(crack_counts[key] or 0) > 0
            for key in ("crack_quads", "crack_batches", "crack_draws")
        ),
        "non_zero_border_workload": all(
            int(border_counts[key] or 0) > 0
            for key in ("border_quads", "border_batches", "border_draws")
        ),
        "non_zero_mesh_workload": all(
            int(mesh_counts[key] or 0) > 0
            for key in ("mesh_instances", "mesh_batches", "mesh_draws")
        ),
        "non_zero_background_workload": int(background_counts["background_clears"] or 0) > 0,
        "depth_attachment_evidence": int(outline_counts["depth_attachment_creates"] or 0) > 0
        or int(outline_counts["depth_attachment_reuses"] or 0) > 0,
        "outline_marker_evidence": "minecraft.world.block-outline" in log_text
        or int(outline_counts["world_draws"] or 0) > 0,
        "border_marker_evidence": "minecraft.world-border" in log_text
        or "world-border" in log_text
        or int(border_counts["border_draws"] or 0) > 0,
        "mesh_marker_evidence": "BlockDisplay mesh request" in log_text
        or int(mesh_counts["mesh_draws"] or 0) > 0,
        "acquired_images": acquired[-5:],
        "rendered_images": begun[-5:],
        "present_ready_images": present_ready[-5:],
        "presented_images": presented[-5:],
        "acquired_rendered_presented_image_identity_matches": identity_matches,
        "blue_diagnostic_shell_clear_expected": blue_clear_expected,
        "semantic_world_background_clear_expected": semantic_background_expected,
        "java_vulkan_frame_marker_count": java_vulkan_frame_markers,
        "rust_shell_marker_count": rust_shell_markers,
    }


def write_renderdoc_summary(capture_dir: Path, status: str, capture_path: Path | None = None, failure: str | None = None) -> Path:
    workload_proof = renderdoc_workload_proof(capture_dir)
    summary = {
        "schema": "mattmc-renderdoc-summary-v1",
        "status": status,
        "capture_path": str(capture_path) if capture_path else None,
        "replay_status": "not_run" if status != "complete" else "complete",
        "api": None,
        "event_count": None,
        "draw_count": None,
        "dispatch_count": None,
        "pass_count": None,
        "ordered_actions": [],
        "pipelines": [],
        "shader_identities": [],
        "vertex_index_inputs": [],
        "resource_bindings": [],
        "framebuffer_attachments": [],
        "viewport_scissor": [],
        "fixed_function_state": [],
        "resource_formats": [],
        "attachments": [],
        "resource_hashes": [],
        "workload_proof": workload_proof,
        "diagnosis": diagnose_renderdoc_capture_failure(capture_dir, capture_path) if status != "complete" else {},
        "failure": failure,
    }
    path = capture_dir / f"renderdoc_summary_{timestamp()}.json"
    path.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return path


def write_tracy_summary(
    capture_dir: Path,
    status: str,
    capture_path: Path | None = None,
    failure: str | None = None,
    started_at: float | None = None,
    tool_output: str | None = None,
    attach_metadata: Mapping[str, object] | None = None,
    role_detection: Mapping[str, object] | None = None,
    summary_prefix: str = "tracy_summary",
) -> Path:
    size_bytes = capture_path.stat().st_size if capture_path and capture_path.exists() else 0
    summary = {
        "schema": "mattmc-tracy-summary-v1",
        "status": status,
        "capture_path": str(capture_path) if capture_path else None,
        "duration_seconds": (time.monotonic() - started_at) if started_at else None,
        "size_bytes": size_bytes,
        "zones": {},
        "major_zones": {},
        "zone_count": 0,
        "call_counts": {},
        "ffi": {},
        "allocations": {},
        "cache_misses": {},
        "capture_complete": status == "complete" and size_bytes > 0,
        "unattributed_time": None,
        "failure": failure,
        "tool_output": tool_output,
        "attach": dict(attach_metadata or {}),
        "role_detection": dict(role_detection or {}),
    }
    path = capture_dir / f"{summary_prefix}_{timestamp()}.json"
    path.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return path


RUST_TRACY_MARKERS = (
    "MattMC Rust VulkanicGAL",
    "opengl.backend.",
    "opengl.lowering.",
    "opengl.borrowed-state.",
    "opengl.resources.",
    "vulkan.backend.",
    "vulkan.lowering.",
    "vulkan.resources.",
    "gal.submission backend=opengl",
    "gal.submission backend=vulkan",
    "gal.frame.acquire backend=opengl",
    "gal.frame.present backend=opengl",
)
JAVA_TRACY_MARKERS = (
    "java.frame.",
    "rust-gal.",
    "ffi.rust.",
    "gal.frame.deferred",
    "minecraft.gui.",
    "shaderc.compile",
)


def tracy_role_detection(zones: Mapping[str, object], messages: Sequence[str]) -> dict[str, object]:
    haystack = "\n".join([*zones.keys(), *messages])
    rust_matches = [marker for marker in RUST_TRACY_MARKERS if marker in haystack]
    java_matches = [marker for marker in JAVA_TRACY_MARKERS if marker in haystack]
    return {
        "rust": bool(rust_matches),
        "java": bool(java_matches),
        "rust_matches": rust_matches,
        "java_matches": java_matches,
    }


MAJOR_TRACY_ZONE_TERMS = (
    "java.frame",
    "game.",
    "sodium.",
    "iris.",
    "distant-horizons.",
    "gal.",
    "backend.",
    "uploads",
    "transfers",
    "command.",
    "submission",
    "present",
    "wait",
    "sync.",
    "ffi.",
    "shaderc",
)


def run_tracy_csvexport(capture_path: Path, *, self_time: bool = False, messages: bool = False) -> tuple[list[dict[str, str]], str | None]:
    exporter = local_tracy_csvexport_path()
    if not exporter:
        return [], "tracy-csvexport is not installed"
    command = [exporter]
    if self_time:
        command.append("--self")
    if messages:
        command.append("--messages")
    command.append(str(capture_path))
    try:
        result = subprocess.run(
            command,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=45,
            check=False,
        )
    except Exception as exc:
        return [], f"tracy-csvexport failed to start: {exc}"
    if result.returncode != 0:
        return [], f"tracy-csvexport exited {result.returncode}: {result.stdout.strip()[:500]}"
    if not result.stdout.strip() or result.stdout.startswith("There are currently no"):
        return [], None
    try:
        return list(csv.DictReader(io.StringIO(result.stdout))), None
    except Exception as exc:
        return [], f"tracy-csvexport output could not be parsed: {exc}"


def tracy_zone_row(row: Mapping[str, str], self_row: Mapping[str, str] | None) -> dict[str, object]:
    total_ns = parse_number(row.get("total_ns"))
    self_ns = parse_number(self_row.get("total_ns")) if self_row else None
    return {
        "count": parse_number(row.get("counts")),
        "inclusive_total_ns": total_ns,
        "exclusive_total_ns": self_ns,
        "inclusive_total_ms": None if total_ns is None else total_ns / 1_000_000.0,
        "exclusive_total_ms": None if self_ns is None else self_ns / 1_000_000.0,
        "inclusive_mean_ns": parse_number(row.get("mean_ns")),
        "inclusive_min_ns": parse_number(row.get("min_ns")),
        "inclusive_max_ns": parse_number(row.get("max_ns")),
        "inclusive_std_ns": parse_number(row.get("std_ns")),
        "total_percent": parse_number(row.get("total_perc")),
        "source_file": row.get("src_file"),
        "source_line": parse_number(row.get("src_line")),
    }


def extract_tracy_summary(
    capture_dir: Path,
    capture_path: Path,
    *,
    started_at: float | None = None,
    duration_seconds: float | None = None,
    failure: str | None = None,
    attach_metadata: Mapping[str, object] | None = None,
    require_rust_zones: bool = False,
    require_java_zones: bool = False,
    summary_prefix: str = "tracy_summary",
) -> Path:
    size_bytes = capture_path.stat().st_size if capture_path.exists() else 0
    if not capture_path.exists() or size_bytes <= 0:
        return write_tracy_summary(
            capture_dir,
            "failed",
            capture_path,
            failure or "Tracy capture missing or empty",
            started_at,
            attach_metadata=attach_metadata,
            summary_prefix=summary_prefix,
        )
    inclusive_rows, inclusive_error = run_tracy_csvexport(capture_path)
    exclusive_rows, exclusive_error = run_tracy_csvexport(capture_path, self_time=True)
    message_rows, message_error = run_tracy_csvexport(capture_path, messages=True)
    if inclusive_error or exclusive_error:
        return write_tracy_summary(capture_dir, "failed", capture_path, inclusive_error or exclusive_error, started_at)

    exclusive_by_name = {row.get("name", ""): row for row in exclusive_rows}
    zones = {
        row.get("name", ""): tracy_zone_row(row, exclusive_by_name.get(row.get("name", "")))
        for row in inclusive_rows
        if row.get("name")
    }
    major_zones = {
        name: value
        for name, value in zones.items()
        if any(term.lower() in name.lower() for term in MAJOR_TRACY_ZONE_TERMS)
    }
    total_percent = sum(
        value
        for value in (parse_number(zone.get("total_percent")) for zone in major_zones.values())
        if value is not None
    )
    messages = [row.get("MessageName", "") for row in message_rows if row.get("MessageName")]
    role_detection = tracy_role_detection(zones, messages)
    frame_correlations = [
        {
            "message": message,
            "backend": first_text(message, r"backend=([A-Za-z0-9_-]+)"),
            "correlation": first_number(message, r"correlation=(\d+)"),
            "frame": first_number(message, r"frame=(\d+)"),
            "submission": first_number(message, r"submission=(\d+)"),
            "status": first_text(message, r"status=([A-Za-z0-9_]+)"),
        }
        for message in messages
        if "gal.frame." in message
    ]
    submission_correlations = [
        {
            "message": message,
            "backend": first_text(message, r"backend=([A-Za-z0-9_-]+)"),
            "producer": first_text(message, r"producer=([A-Za-z0-9_-]+)"),
            "iteration": first_number(message, r"iteration=(\d+)"),
            "submission": first_number(message, r"(?:id|submission)=(\d+)"),
        }
        for message in messages
        if "gal.submission" in message
    ]
    call_counts = {
        "ffi": sum(1 for message in messages if "ffi" in message.lower()),
        "shaderc": sum(1 for message in messages if "shaderc" in message.lower()),
        "frame_correlations": len(frame_correlations),
        "submission_correlations": len(submission_correlations),
        "messages": len(messages),
    }
    missing_roles: list[str] = []
    if require_rust_zones and not role_detection["rust"]:
        missing_roles.append("Rust VulkanicGAL")
    if require_java_zones and not role_detection["java"]:
        missing_roles.append("Java")
    complete = bool(zones) and not missing_roles
    summary = {
        "schema": "mattmc-tracy-summary-v1",
        "status": "complete" if complete else "failed",
        "capture_path": str(capture_path),
        "duration_seconds": duration_seconds if duration_seconds is not None else ((time.monotonic() - started_at) if started_at else None),
        "size_bytes": size_bytes,
        "zones": zones,
        "major_zones": major_zones,
        "zone_count": len(zones),
        "call_counts": call_counts,
        "messages_sample": messages[:128],
        "correlations": {
            "frames": frame_correlations,
            "submissions": submission_correlations,
        },
        "ffi": {
            "message_count": call_counts["ffi"],
            "shaderc_message_count": call_counts["shaderc"],
            "zones": {name: zone for name, zone in zones.items() if "ffi" in name.lower() or "shaderc" in name.lower()},
        },
        "allocations": {
            "available": False,
            "reason": "tracy-csvexport zone/message export does not expose memory-pool allocation totals",
        },
        "cache_misses": {
            "available": False,
            "reason": "hardware counter export is not enabled for bounded automated captures",
        },
        "capture_complete": complete,
        "unattributed_time": max(0.0, 100.0 - total_percent) if major_zones else None,
        "diagnosis": {
            "csvexport_messages_error": message_error,
            "major_zone_terms": MAJOR_TRACY_ZONE_TERMS,
            "required_roles": {
                "rust": require_rust_zones,
                "java": require_java_zones,
            },
        },
        "attach": dict(attach_metadata or {}),
        "role_detection": role_detection,
        "failure": None if complete else (
            f"Tracy capture missing required {' and '.join(missing_roles)} zones"
            if missing_roles
            else "Tracy capture contained no exportable zones"
        ),
    }
    path = capture_dir / f"{summary_prefix}_{timestamp()}.json"
    path.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return path


def write_tracy_collection_summary(
    capture_dir: Path,
    capture_summaries: Sequence[Mapping[str, object]],
    *,
    require_rust_zones: bool,
    require_java_zones: bool,
    failure: str | None = None,
    tool_output: str | None = None,
) -> Path:
    complete_summaries = [summary for summary in capture_summaries if summary.get("status") == "complete"]
    rust_summaries = [
        summary
        for summary in complete_summaries
        if isinstance(summary.get("role_detection"), Mapping) and bool(summary["role_detection"].get("rust"))
    ]
    java_summaries = [
        summary
        for summary in complete_summaries
        if isinstance(summary.get("role_detection"), Mapping) and bool(summary["role_detection"].get("java"))
    ]
    missing_roles: list[str] = []
    if require_rust_zones and not rust_summaries:
        missing_roles.append("Rust VulkanicGAL")
    if require_java_zones and not java_summaries:
        missing_roles.append("Java")
    selected = rust_summaries[0] if rust_summaries else (complete_summaries[0] if complete_summaries else (capture_summaries[0] if capture_summaries else {}))
    combined_zone_count = sum(int(summary.get("zone_count") or 0) for summary in capture_summaries)
    combined_size = sum(int(summary.get("size_bytes") or 0) for summary in capture_summaries)
    blocking_failure = failure if (not complete_summaries or missing_roles) else None
    status = "complete" if complete_summaries and not missing_roles and blocking_failure is None else "failed"
    summary = {
        "schema": "mattmc-tracy-summary-v1",
        "status": status,
        "capture_path": selected.get("capture_path"),
        "duration_seconds": selected.get("duration_seconds"),
        "size_bytes": combined_size,
        "zones": selected.get("zones", {}),
        "major_zones": selected.get("major_zones", {}),
        "zone_count": combined_zone_count,
        "call_counts": selected.get("call_counts", {}),
        "ffi": selected.get("ffi", {}),
        "allocations": selected.get("allocations", {}),
        "cache_misses": selected.get("cache_misses", {}),
        "capture_complete": status == "complete",
        "unattributed_time": selected.get("unattributed_time"),
        "captures": list(capture_summaries),
        "role_detection": {
            "rust": bool(rust_summaries),
            "java": bool(java_summaries),
            "required": {
                "rust": require_rust_zones,
                "java": require_java_zones,
            },
            "selected_rust_capture": rust_summaries[0].get("capture_path") if rust_summaries else None,
            "selected_java_capture": java_summaries[0].get("capture_path") if java_summaries else None,
        },
        "diagnosis": {
            "capture_count": len(capture_summaries),
            "complete_capture_count": len(complete_summaries),
            "missing_roles": missing_roles,
            "non_blocking_tool_failure": failure if blocking_failure is None else None,
        },
        "failure": blocking_failure or (
            f"Tracy capture missing required {' and '.join(missing_roles)} zones"
            if missing_roles
            else (None if status == "complete" else "No complete Tracy captures were produced")
        ),
        "tool_output": tool_output,
    }
    path = capture_dir / f"tracy_summary_{timestamp()}.json"
    path.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return path


def write_renderdoc_python_extractor(script_path: Path) -> None:
    script_path.write_text(
        r'''
import json
import os
import sys
import traceback

import renderdoc as rd


def safe_str(value):
    try:
        return str(value)
    except Exception:
        return "<unprintable>"


def get_attr(obj, name):
    try:
        return getattr(obj, name)
    except Exception:
        return None


def action_drawcall_id(action):
    for name in ("drawcallId", "drawcallID", "drawcall_id"):
        value = get_attr(action, name)
        if value is not None:
            return value
    return get_attr(action, "eventId")


def action_name(action):
    for name in ("customName", "name"):
        value = get_attr(action, name)
        if value:
            return value
    try:
        return action.GetName(None) if hasattr(action, "GetName") else None
    except Exception:
        return None


def resource_id(value):
    if value is None:
        return None
    try:
        if value == rd.ResourceId.Null():
            return None
    except Exception:
        pass
    return safe_str(value)


def resource_name(resource_map, value):
    rid = resource_id(value)
    if rid is None:
        return None
    desc = resource_map.get(rid)
    return get_attr(desc, "name") if desc is not None else None


def flag_names(flags):
    names = []
    for name in (
        "Drawcall", "Dispatch", "MeshDispatch", "Clear", "Copy", "Resolve", "GenMips",
        "BeginPass", "EndPass", "PassBoundary", "SetMarker", "PushMarker", "PopMarker",
        "Present", "Indexed", "Instanced", "Indirect", "CommandBufferBoundary",
    ):
        value = getattr(rd.ActionFlags, name, None)
        if value is not None:
            try:
                if flags & value:
                    names.append(name)
            except Exception:
                pass
    return names


def action_kind(flags):
    names = set(flag_names(flags))
    if "Drawcall" in names:
        return "draw"
    if "Dispatch" in names or "MeshDispatch" in names:
        return "dispatch"
    if "BeginPass" in names or "EndPass" in names or "PassBoundary" in names:
        return "pass"
    if "SetMarker" in names or "PushMarker" in names or "PopMarker" in names:
        return "marker"
    if "Present" in names:
        return "present"
    if "Copy" in names or "Resolve" in names or "GenMips" in names:
        return "transfer"
    return "event"


def shader_summary(state, stage):
    try:
        reflection = state.GetShaderReflection(stage)
    except Exception:
        reflection = None
    if reflection is None:
        return None
    return {
        "stage": safe_str(stage),
        "resource_id": resource_id(get_attr(reflection, "resourceId")),
        "entry_point": get_attr(reflection, "entryPoint"),
        "encoding": safe_str(get_attr(reflection, "encoding")),
        "input_count": len(get_attr(reflection, "inputSignature") or []),
        "output_count": len(get_attr(reflection, "outputSignature") or []),
        "constant_blocks": [get_attr(block, "name") for block in (get_attr(reflection, "constantBlocks") or [])[:32]],
        "read_only_resources": [get_attr(res, "name") for res in (get_attr(reflection, "readOnlyResources") or [])[:64]],
        "read_write_resources": [get_attr(res, "name") for res in (get_attr(reflection, "readWriteResources") or [])[:64]],
        "samplers": [get_attr(res, "name") for res in (get_attr(reflection, "samplers") or [])[:64]],
    }


def describe_state(controller, resource_map, action):
    entry = {
        "event_id": action.eventId,
        "pipeline": None,
        "shaders": [],
        "vertex_index_inputs": {},
        "resource_bindings": {},
        "framebuffer_attachments": {},
        "viewport_scissor": {},
        "fixed_function_state": {},
    }
    try:
        controller.SetFrameEvent(action.eventId, False)
        state = controller.GetPipelineState()
    except Exception as exc:
        entry["error"] = "pipeline-state-unavailable: " + str(exc)
        return entry

    try:
        pipeline = state.GetGraphicsPipelineObject()
        entry["pipeline"] = {
            "graphics_pipeline_object": resource_id(pipeline),
            "name": resource_name(resource_map, pipeline),
        }
    except Exception:
        pass

    for stage_name in ("Vertex", "Tess_Control", "Tess_Eval", "Geometry", "Pixel", "Compute"):
        stage = getattr(rd.ShaderStage, stage_name, None)
        if stage is None:
            continue
        shader = shader_summary(state, stage)
        if shader:
            entry["shaders"].append(shader)

    for method_name, target in (
        ("GetVertexInputs", "vertex_inputs"),
        ("GetIndexBuffer", "index_buffer"),
        ("GetOutputTargets", "color_outputs"),
        ("GetDepthTarget", "depth_output"),
        ("GetViewport", "viewport"),
        ("GetScissor", "scissor"),
        ("GetPrimitiveTopology", "topology"),
    ):
        try:
            if method_name in ("GetViewport", "GetScissor"):
                value = getattr(state, method_name)(0)
            else:
                value = getattr(state, method_name)()
            if target in ("vertex_inputs", "index_buffer"):
                entry["vertex_index_inputs"][target] = safe_str(value)
            elif target in ("color_outputs", "depth_output"):
                entry["framebuffer_attachments"][target] = safe_str(value)
            elif target in ("viewport", "scissor"):
                entry["viewport_scissor"][target] = safe_str(value)
            else:
                entry["fixed_function_state"][target] = safe_str(value)
        except Exception:
            pass

    for field in ("depthState", "blendState", "rasterizer", "inputAssembly", "vertexInput"):
        value = get_attr(state, field)
        if value is not None:
            entry["fixed_function_state"][field] = safe_str(value)
    return entry


def walk(actions, depth, rows, counts, limit):
    for action in actions:
        if len(rows) >= limit:
            counts["truncated"] = True
            return
        names = flag_names(action.flags)
        kind = action_kind(action.flags)
        counts[kind] = counts.get(kind, 0) + 1
        rows.append({
            "event_id": action.eventId,
            "drawcall_id": action_drawcall_id(action),
            "name": action_name(action),
            "kind": kind,
            "flags": names,
            "depth": depth,
            "marker": kind == "marker",
            "children": len(get_attr(action, "children") or []),
        })
        children = get_attr(action, "children") or []
        if children:
            walk(children, depth + 1, rows, counts, limit)


def main():
    capture_path = os.environ["MATTMC_RENDERDOC_REPLAY_CAPTURE"]
    output_path = os.environ["MATTMC_RENDERDOC_REPLAY_OUTPUT"]
    limit = int(os.environ.get("MATTMC_RENDERDOC_REPLAY_ACTION_LIMIT", "2500"))
    summary = {
        "schema": "mattmc-renderdoc-summary-v1",
        "status": "failed",
        "capture_path": capture_path,
        "replay_status": "failed",
        "api": None,
        "event_count": 0,
        "draw_count": 0,
        "dispatch_count": 0,
        "pass_count": 0,
        "ordered_actions": [],
        "pipelines": [],
        "shader_identities": [],
        "vertex_index_inputs": [],
        "resource_bindings": [],
        "framebuffer_attachments": [],
        "viewport_scissor": [],
        "fixed_function_state": [],
        "resource_formats": [],
        "attachments": [],
        "resource_hashes": [],
        "diagnosis": {},
        "failure": None,
    }
    cap = None
    controller = None
    try:
        rd.InitialiseReplay(rd.GlobalEnvironment(), [])
        cap = rd.OpenCaptureFile()
        result = cap.OpenFile(capture_path, "", None)
        if result != rd.ResultCode.Succeeded:
            raise RuntimeError("open-file " + safe_str(result))
        if not cap.LocalReplaySupport():
            raise RuntimeError("local replay unsupported")
        result, controller = cap.OpenCapture(rd.ReplayOptions(), None)
        if result != rd.ResultCode.Succeeded:
            raise RuntimeError("open-capture " + safe_str(result))

        resources = {safe_str(res.resourceId): res for res in controller.GetResources()}
        textures = controller.GetTextures()
        summary["api"] = safe_str(controller.GetAPIProperties().pipelineType)
        actions = []
        counts = {}
        walk(controller.GetRootActions(), 0, actions, counts, limit)
        summary["ordered_actions"] = actions
        summary["event_count"] = len(actions)
        summary["draw_count"] = counts.get("draw", 0)
        summary["dispatch_count"] = counts.get("dispatch", 0)
        summary["pass_count"] = counts.get("pass", 0)
        summary["diagnosis"] = {"action_limit": limit, "truncated": bool(counts.get("truncated"))}

        interesting = [row for row in actions if row["kind"] in ("draw", "dispatch", "pass", "transfer")][:256]
        state_rows = []
        for row in interesting:
            action = controller.GetAction(row["event_id"])
            if action:
                state_rows.append(describe_state(controller, resources, action))
        summary["pipelines"] = [row.get("pipeline") for row in state_rows if row.get("pipeline")]
        summary["shader_identities"] = [shader for row in state_rows for shader in row.get("shaders", [])]
        summary["vertex_index_inputs"] = [row.get("vertex_index_inputs") for row in state_rows if row.get("vertex_index_inputs")]
        summary["resource_bindings"] = [row.get("resource_bindings") for row in state_rows if row.get("resource_bindings")]
        summary["framebuffer_attachments"] = [row.get("framebuffer_attachments") for row in state_rows if row.get("framebuffer_attachments")]
        summary["viewport_scissor"] = [row.get("viewport_scissor") for row in state_rows if row.get("viewport_scissor")]
        summary["fixed_function_state"] = [row.get("fixed_function_state") for row in state_rows if row.get("fixed_function_state")]
        summary["resource_formats"] = [
            {
                "resource_id": resource_id(tex.resourceId),
                "name": resource_name(resources, tex.resourceId),
                "width": get_attr(tex, "width"),
                "height": get_attr(tex, "height"),
                "depth": get_attr(tex, "depth"),
                "mips": get_attr(tex, "mips"),
                "arraysize": get_attr(tex, "arraysize"),
                "format": safe_str(get_attr(tex, "format")),
            }
            for tex in textures[:512]
        ]
        summary["status"] = "complete"
        summary["replay_status"] = "complete"
    except Exception as exc:
        summary["failure"] = str(exc)
        summary["diagnosis"] = {"traceback": traceback.format_exc(limit=8)}
    finally:
        if controller is not None:
            controller.Shutdown()
        if cap is not None:
            cap.Shutdown()
        try:
            rd.ShutdownReplay()
        except Exception:
            pass
        with open(output_path, "w", encoding="utf-8") as handle:
            json.dump(summary, handle, indent=2, sort_keys=True)
            handle.write("\n")


main()
'''.lstrip(),
        encoding="utf-8",
    )


def renderdoc_capture_path_from_env(env: dict[str, str], capture_dir: Path) -> Path:
    configured = env.get("MATTMC_RENDERDOC_CAPTURE_PATH")
    return Path(configured) if configured else capture_dir / f"renderdoc_{timestamp()}.rdc"


def resolve_renderdoc_capture_path(capture_dir: Path, requested: Path) -> Path:
    if requested.exists():
        return requested
    captures = sorted(capture_dir.glob("*.rdc"), key=lambda path: path.stat().st_mtime if path.exists() else 0)
    return captures[-1] if captures else requested


def renderdoc_vulkan_layer_diagnosis() -> dict[str, object]:
    renderdoc = local_renderdoccmd_path()
    if not renderdoc:
        return {"available": False, "explain": "renderdoccmd is not available"}
    try:
        result = subprocess.run(
            [renderdoc, "vulkanlayer", "--explain"],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=15,
            check=False,
        )
        output = result.stdout.strip()
    except Exception as exc:
        return {"available": True, "error": str(exc)}
    return {
        "available": True,
        "registered": "not correctly registered" not in output,
        "local_manifest": str(local_renderdoc_layer_manifest_path()) if local_renderdoc_layer_manifest_path() else None,
        "explain": output,
    }


def replay_renderdoc_summary(capture_dir: Path, capture_path: Path) -> Path:
    capture_path = resolve_renderdoc_capture_path(capture_dir, capture_path)
    if not capture_path.exists():
        return write_renderdoc_summary(capture_dir, "failed", capture_path, "RenderDoc did not produce an .rdc file")
    renderdoc = local_renderdoccmd_path()
    if not renderdoc:
        return write_renderdoc_summary(capture_dir, "failed", capture_path, "renderdoccmd is not available for replay")
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
    workload_proof = renderdoc_workload_proof(capture_dir)
    summary = {
        "schema": "mattmc-renderdoc-summary-v1",
        "status": "complete",
        "capture_path": str(capture_path),
        "replay_status": "complete",
        "api": None,
        "event_count": event_count,
        "draw_count": draw_count,
        "dispatch_count": dispatch_count,
        "pass_count": pass_count,
        "ordered_actions": [],
        "pipelines": [],
        "shader_identities": [],
        "vertex_index_inputs": [],
        "resource_bindings": [],
        "framebuffer_attachments": [],
        "viewport_scissor": [],
        "fixed_function_state": [],
        "resource_formats": [],
        "attachments": [],
        "resource_hashes": [],
        "workload_proof": workload_proof,
        "diagnosis": {
            "cli_only_replay": True,
            "fallback_text_replay": True,
            "machine_readable_cli_extraction": False,
            "renderdoc_python_module_available": renderdoc_python_module_available(),
            "explanation": (
                "This local RenderDoc installation can capture and run renderdoccmd replay, "
                "but renderdoccmd replay does not expose an ordered action/state export here. "
                "qrenderdoc GUI/Python replay is intentionally not launched by the harness."
            ),
            "best_available_alternative": "Use deterministic screenshots, strict GL logs, Tracy zones, and Rust GAL audit counters for ordering/cadence evidence.",
            "vulkan_layer": renderdoc_vulkan_layer_diagnosis(),
        },
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
    static_terrain_second_world = (
        getattr(args, "world_static_terrain_second_world", "")
        or (f"{args.world}-different" if getattr(args, "world_static_terrain_scenario", "") == "world-different-reload" else "")
    )
    base_client_args = args.client_args
    for option in ("--quickPlaySingleplayer", "--width", "--height"):
        base_client_args = remove_client_arg_option(base_client_args, option)
    base_client_args = remove_client_arg_assignment(base_client_args, "enableShaders")
    client_arg_parts = [base_client_args]
    if run_dev_capture_entrypoint(target.root)[0] == "shell":
        client_arg_parts.append(f"--quickPlaySingleplayer={shlex.quote(args.world)}")
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
        if getattr(args, "gui_resource_pack_scenario", ""):
            command.extend(["--gui-resource-pack-scenario", args.gui_resource_pack_scenario])
        if getattr(args, "world_static_terrain_resource_pack_scenario", ""):
            command.extend([
                "--world-static-terrain-resource-pack-scenario",
                args.world_static_terrain_resource_pack_scenario,
            ])
        if getattr(args, "world_static_terrain_scenario", ""):
            command.extend([
                "--world-static-terrain-scenario",
                args.world_static_terrain_scenario,
            ])
        if static_terrain_second_world:
            command.extend([
                "--world-static-terrain-second-world",
                static_terrain_second_world,
            ])
    else:
        shell = "bash" if platform_name() != "windows" else "bash"
        command = [
            shell,
            str(entrypoint),
            "--backend",
            mode.backend,
            "--shaders",
            mode.shaders,
            "--artifact-dir",
            str(capture_dir),
            "--max-secs",
            str(args.max_secs),
            "--dump-secs",
            str(args.dump_secs),
            "--validation",
            validation,
        ]
        if client_args:
            command.extend(["--client-args", client_args])
        if getattr(args, "gui_resource_pack_scenario", ""):
            command.extend(["--gui-resource-pack-scenario", args.gui_resource_pack_scenario])
        if getattr(args, "world_static_terrain_resource_pack_scenario", ""):
            command.extend([
                "--world-static-terrain-resource-pack-scenario",
                args.world_static_terrain_resource_pack_scenario,
            ])
        if getattr(args, "world_static_terrain_scenario", ""):
            command.extend([
                "--world-static-terrain-scenario",
                args.world_static_terrain_scenario,
            ])
        if static_terrain_second_world:
            command.extend([
                "--world-static-terrain-second-world",
                static_terrain_second_world,
            ])
    env = os.environ.copy()
    run_type = run_type_for_effective_options(
        validation,
        renderdoc=getattr(args, "renderdoc_capture", False),
        tracy=getattr(args, "tracy_capture", False),
    )
    env["MATTMC_GRAPHICS_TOOL_INTERNAL"] = "1"
    env["MATTMC_GRAPHICS_RUN_TYPE"] = run_type
    world_profile = world_profile_for_args(args)
    env["MATTMC_GRAPHICS_WORLD_PROFILE"] = world_profile.name
    env["MATTMC_GRAPHICS_WORLD_PROFILE_ROLE"] = world_profile.role
    env["MATTMC_GRAPHICS_MIGRATION_GATE_BLOCKING"] = "true" if world_profile.migration_gate_blocking else "false"
    env["MATTMC_GRAPHICS_VALIDATION_PROFILE"] = requested_validation
    env["MATTMC_GRAPHICS_VALIDATION_FAIL_SEVERITY"] = getattr(args, "validation_fail_severity", "warning")
    if requested_validation in {"routine", "deep"}:
        env["VK_INSTANCE_LAYERS"] = "VK_LAYER_KHRONOS_validation"
        env["VK_LOADER_DEBUG"] = "info"
        env["VK_LAYER_SETTINGS"] = "validate_sync=true,validate_best_practices=true"
        if requested_validation == "deep":
            env["VK_LAYER_SETTINGS"] = "validate_sync=true,validate_best_practices=true,gpuav_enable=true,printf_enable=true"
    env["MATTMC_RENDERDOC_CAPTURE"] = "true" if getattr(args, "renderdoc_capture", False) else "false"
    env["MATTMC_RENDERDOC_FRAME"] = str(getattr(args, "renderdoc_frame", 8))
    env["MATTMC_TRACY_CAPTURE"] = "true" if getattr(args, "tracy_capture", False) else "false"
    env["MATTMC_RUST_TRACY"] = "true" if getattr(args, "tracy_capture", False) else "false"
    env["MATTMC_TRACY_DURATION_SECONDS"] = str(getattr(args, "tracy_duration_seconds", 20))
    env["MATTMC_TRACY_MAX_SIZE_MB"] = str(getattr(args, "tracy_max_size_mb", 256))
    if getattr(args, "shader_graph_isolation", ""):
        env["MATTMC_RUST_SHADER_GRAPH_ISOLATION"] = args.shader_graph_isolation
    env["MATTMC_DETERMINISTIC_SHUTDOWN_GRACE_SECS"] = str(max(5, phase_timeout_seconds(args, "shutdown")))
    env["MATTMC_CAPTURE_WORLD"] = args.world
    env["MATTMC_CAPTURE_RUN_SOURCE"] = str(CURRENT_REPO_ROOT / "run")
    env["MATTMC_CAPTURE_WORLD_SOURCE"] = str(CURRENT_REPO_ROOT / "run" / "saves" / args.world)
    if static_terrain_second_world:
        env["MATTMC_CAPTURE_SECOND_WORLD"] = static_terrain_second_world
    env["CLIENT_RSS_LIMIT_MB"] = str(args.client_rss_limit_mb)
    screenshot_limits = {
        "smoke": ("0", "0"),
        "standard": ("30", "2"),
        "performance": ("0", "0"),
        "extended": ("45", "3"),
    }
    screenshot_interval, screenshot_count = screenshot_limits.get(args.profile, ("30", "2"))
    if getattr(args, "diagnostic", False) or getattr(args, "renderdoc_capture", False) or getattr(args, "tracy_capture", False):
        screenshot_interval, screenshot_count = ("45", "1")
    if tool_kind == "capture" and workload_profile == "correctness":
        screenshot_interval, screenshot_count = ("1", "4")
    env.setdefault("SCREENSHOT_INTERVAL_SECS", screenshot_interval)
    env.setdefault("SCREENSHOT_MAX_COUNT", screenshot_count)
    env.setdefault("SCREENSHOT_START_DELAY_SECS", "10")
    java_options = shlex.split(env.get("JAVA_TOOL_OPTIONS", ""))
    java_options.extend(getattr(args, "jvm_arg", []) or [])
    java_options.extend(world_profile.diagnostic_jvm_args)
    java_options.extend(
        [
            f"-Dmattmc.dev.graphicsWorldProfile={world_profile.name}",
            f"-Dmattmc.dev.deterministicCameraCapture.settledReadyFamilies={world_profile.deterministic_ready_families}",
            f"-Dmattmc.dev.deterministicCameraCapture.settledReadyFrames={world_profile.deterministic_ready_frames}",
            f"-Dmattmc.dev.deterministicCameraCapture.settledReadyMaxWaitFrames={world_profile.deterministic_ready_max_wait_frames}",
        ]
    )
    if getattr(args, "armor_value", None) is not None:
        java_options.append(f"-Dmattmc.dev.graphicsFrameBenchmark.armorValue={args.armor_value}")
        java_options.append(f"-Dmattmc.dev.deterministicCameraCapture.armorValue={args.armor_value}")
    if getattr(args, "player_health", None) is not None:
        java_options.append(f"-Dmattmc.dev.graphicsFrameBenchmark.playerHealth={args.player_health}")
        java_options.append(f"-Dmattmc.dev.deterministicCameraCapture.playerHealth={args.player_health}")
    if getattr(args, "player_max_health", None) is not None:
        java_options.append(f"-Dmattmc.dev.graphicsFrameBenchmark.playerMaxHealth={args.player_max_health}")
        java_options.append(f"-Dmattmc.dev.deterministicCameraCapture.playerMaxHealth={args.player_max_health}")
    if getattr(args, "player_absorption", None) is not None:
        java_options.append(f"-Dmattmc.dev.graphicsFrameBenchmark.playerAbsorption={args.player_absorption}")
        java_options.append(f"-Dmattmc.dev.deterministicCameraCapture.playerAbsorption={args.player_absorption}")
    if getattr(args, "player_food_level", None) is not None:
        java_options.append(f"-Dmattmc.dev.graphicsFrameBenchmark.playerFoodLevel={args.player_food_level}")
        java_options.append(f"-Dmattmc.dev.deterministicCameraCapture.playerFoodLevel={args.player_food_level}")
    if getattr(args, "player_food_saturation", None) is not None:
        java_options.append(f"-Dmattmc.dev.graphicsFrameBenchmark.playerFoodSaturation={args.player_food_saturation}")
        java_options.append(f"-Dmattmc.dev.deterministicCameraCapture.playerFoodSaturation={args.player_food_saturation}")
    if getattr(args, "player_food_hunger_effect", False):
        java_options.append("-Dmattmc.dev.deterministicCameraCapture.playerFoodHungerEffect=true")
    if getattr(args, "player_food_jitter", False):
        java_options.append("-Dmattmc.dev.deterministicCameraCapture.playerFoodJitter=true")
    if getattr(args, "player_air_supply", None) is not None:
        java_options.append(f"-Dmattmc.dev.graphicsFrameBenchmark.playerAirSupply={args.player_air_supply}")
        java_options.append(f"-Dmattmc.dev.deterministicCameraCapture.playerAirSupply={args.player_air_supply}")
    if getattr(args, "player_max_air_supply", None) is not None:
        java_options.append(f"-Dmattmc.dev.graphicsFrameBenchmark.playerMaxAirSupply={args.player_max_air_supply}")
        java_options.append(f"-Dmattmc.dev.deterministicCameraCapture.playerMaxAirSupply={args.player_max_air_supply}")
    if getattr(args, "player_underwater", False):
        java_options.append("-Dmattmc.dev.graphicsFrameBenchmark.playerUnderwater=true")
        java_options.append("-Dmattmc.dev.deterministicCameraCapture.playerUnderwater=true")
    if getattr(args, "player_air_pop", False):
        java_options.append("-Dmattmc.dev.graphicsFrameBenchmark.playerAirPop=true")
        java_options.append("-Dmattmc.dev.deterministicCameraCapture.playerAirPop=true")
    if getattr(args, "mount_present", False):
        java_options.append("-Dmattmc.dev.graphicsFrameBenchmark.mountPresent=true")
        java_options.append("-Dmattmc.dev.deterministicCameraCapture.mountPresent=true")
    if getattr(args, "mount_health", None) is not None:
        java_options.append(f"-Dmattmc.dev.graphicsFrameBenchmark.mountHealth={args.mount_health}")
        java_options.append(f"-Dmattmc.dev.deterministicCameraCapture.mountHealth={args.mount_health}")
    if getattr(args, "mount_max_health", None) is not None:
        java_options.append(f"-Dmattmc.dev.graphicsFrameBenchmark.mountMaxHealth={args.mount_max_health}")
        java_options.append(f"-Dmattmc.dev.deterministicCameraCapture.mountMaxHealth={args.mount_max_health}")
    if getattr(args, "mount_health_rows", None) is not None:
        java_options.append(f"-Dmattmc.dev.graphicsFrameBenchmark.mountHealthRows={args.mount_health_rows}")
        java_options.append(f"-Dmattmc.dev.deterministicCameraCapture.mountHealthRows={args.mount_health_rows}")
    deterministic_capture_requested = (
        tool_kind == "capture"
        and workload_profile in {"correctness", "moving-camera", "settled-static"}
        and (
            mode.backend == "rust-vulkan"
            or kind == "shell"
            or bool(getattr(args, "world_mesh_block_display_scenario", ""))
            or bool(getattr(args, "world_mesh_falling_block_scenario", ""))
            or bool(getattr(args, "world_mesh_piston_scenario", ""))
            or bool(getattr(args, "world_static_terrain_scenario", ""))
        )
    )
    if deterministic_capture_requested:
        deterministic_stamp = timestamp()
        deterministic_metadata = capture_dir / f"deterministic_camera_capture_{deterministic_stamp}.json"
        deterministic_screenshot_dir = capture_dir / f"deterministic_camera_capture_{deterministic_stamp}"
        target_git_commit = command_text(["git", "rev-parse", "HEAD"], cwd=target.root).strip() or "unknown"
        env["MATTMC_DETERMINISTIC_METADATA"] = str(deterministic_metadata)
        env["MATTMC_DETERMINISTIC_SCREENSHOT_DIR"] = str(deterministic_screenshot_dir)
        java_options.extend(
            [
                "-Dmattmc.dev.deterministicCameraCapture=true",
                f"-Dmattmc.dev.deterministicCameraCapture.metadata={deterministic_metadata}",
                f"-Dmattmc.dev.deterministicCameraCapture.screenshotDir={deterministic_screenshot_dir}",
                f"-Dmattmc.dev.deterministicCameraCapture.shaderEnabled={'true' if mode.shaders == 'on' else 'false'}",
                "-Dmattmc.dev.deterministicCameraCapture.shaderPack=ComplementaryHungLoIfied.zip",
                f"-Dmattmc.dev.deterministicCameraCapture.gitCommit={target_git_commit}",
                f"-Dmattmc.dev.deterministicCameraCapture.world={args.world}",
                "-Dmattmc.dev.deterministicCameraCapture.stopAfterComplete=true",
                "-Dmattmc.dev.deterministicCameraCapture.ackTimeoutFrames=12000",
                "-Dmattmc.dev.deterministicCameraCapture.poseCount=1",
                "-Dmattmc.dev.deterministicCameraCapture.yawDelta=0.0",
            ]
        )
    if getattr(args, "player_heart_variant", None):
        java_options.append(f"-Dmattmc.dev.deterministicCameraCapture.playerHeartVariant={args.player_heart_variant}")
    if getattr(args, "player_heart_flash", False):
        java_options.append("-Dmattmc.dev.deterministicCameraCapture.playerHealthFlash=true")
    if getattr(args, "player_heart_hardcore", False):
        java_options.append("-Dmattmc.dev.deterministicCameraCapture.playerHealthHardcore=true")
    if getattr(args, "player_heart_regeneration", False):
        java_options.append("-Dmattmc.dev.deterministicCameraCapture.playerHealthRegeneration=true")
    if getattr(args, "game_mode", None):
        java_options.append(f"-Dmattmc.dev.graphicsFrameBenchmark.gameMode={args.game_mode}")
        java_options.append(f"-Dmattmc.dev.deterministicCameraCapture.gameMode={args.game_mode}")
    if getattr(args, "world_outline_scenario", ""):
        java_options.append("-Dmattmc.dev.blockOutlineDiagnostics=true")
        java_options.append(f"-Dmattmc.dev.rustGalWorldOutline.scenario={args.world_outline_scenario}")
        java_options.append(f"-Dmattmc.dev.rustGalWorldOutline.style={args.world_outline_style}")
        java_options.append(f"-Dmattmc.dev.rustGalWorldOutline.depthPolicy={args.world_outline_depth_policy}")
        if getattr(args, "world_outline_depth_probe", False):
            java_options.append("-Dmattmc.dev.rustGalWorldOutline.depthProbe=true")
    if getattr(args, "world_outline_real_target", False):
        java_options.append("-Dmattmc.dev.blockOutlineDiagnostics=true")
        java_options.append("-Dmattmc.dev.deterministicCameraCapture.blockOutlineTarget=true")
        if getattr(args, "world_outline_style", "normal") == "high-contrast":
            java_options.append("-Dmattmc.dev.deterministicCameraCapture.blockOutlineHighContrast=true")
    if getattr(args, "world_outline_aim_real_target", False):
        java_options.append("-Dmattmc.dev.blockOutlineDiagnostics=true")
        java_options.append("-Dmattmc.dev.blockOutlinePickDiagnostics=true")
        java_options.append("-Dmattmc.dev.deterministicCameraCapture.blockOutlineAimTarget=true")
        if getattr(args, "world_outline_style", "normal") == "high-contrast":
            java_options.append("-Dmattmc.dev.deterministicCameraCapture.blockOutlineHighContrast=true")
    if getattr(args, "world_outline_pause_parity", False):
        java_options.append("-Dmattmc.dev.blockOutlineDiagnostics=true")
        java_options.append("-Dmattmc.dev.blockOutlinePickDiagnostics=true")
        java_options.append("-Dmattmc.dev.deterministicCameraCapture.blockOutlinePauseParity=true")
        java_options.append("-Dmattmc.dev.deterministicCameraCapture.poseCount=3")
        java_options.append("-Dmattmc.dev.deterministicCameraCapture.yawDelta=0.0")
    if getattr(args, "world_outline_legacy_control", False):
        java_options.append("-Dmattmc.dev.rustGalWorldOutline.legacyControl=true")
    if getattr(args, "block_outline_pick_diagnostics", False):
        java_options.append("-Dmattmc.dev.blockOutlinePickDiagnostics=true")
    if getattr(args, "world_border_scenario", ""):
        java_options.append(f"-Dmattmc.dev.rustGalWorldBorder.scenario={args.world_border_scenario}")
        if getattr(args, "world_border_scroll_phase", ""):
            java_options.append(f"-Dmattmc.dev.rustGalWorldBorder.scrollPhase={args.world_border_scroll_phase}")
    if getattr(args, "world_crack_scenario", ""):
        java_options.append(f"-Dmattmc.dev.rustGalWorldCrack.scenario={args.world_crack_scenario}")
        java_options.append(f"-Dmattmc.dev.rustGalWorldCrack.stage={getattr(args, 'world_crack_stage', 0)}")
    if getattr(args, "world_crack_real_survival", False):
        java_options.append("-Dmattmc.dev.rustGalWorldCrack.requireRealSurvivalCapture=true")
        java_options.append("-Dmattmc.dev.deterministicCameraCapture.realSurvivalCrack=true")
        java_options.append("-Dmattmc.dev.deterministicCameraCapture.realSurvivalCrackSetupBlock=true")
    world_crack_control = getattr(args, "world_crack_control", "rust")
    if world_crack_control == "disabled":
        java_options.append("-Dmattmc.dev.rustGalWorldCrack.disabled=true")
    elif world_crack_control == "legacy":
        java_options.append("-Dmattmc.dev.rustGalWorldCrack.legacyControl=true")
    if getattr(args, "world_material_marker_scenario", ""):
        java_options.append(
            f"-Dmattmc.dev.rustGalWorldMaterial.blockMarkerScenario={args.world_material_marker_scenario}"
        )
    if getattr(args, "world_material_terrain_particle_scenario", ""):
        java_options.append(
            f"-Dmattmc.dev.rustGalWorldMaterial.terrainParticleScenario={args.world_material_terrain_particle_scenario}"
        )
    if getattr(args, "world_mesh_block_display_scenario", ""):
        block_display_workload = getattr(args, "world_mesh_block_display_workload", "single")
        java_options.append(
            f"-Dmattmc.dev.rustGalWorldMesh.blockDisplayScenario={args.world_mesh_block_display_scenario}"
        )
        java_options.append(
            f"-Dmattmc.dev.rustGalWorldMesh.blockDisplayWorkload={block_display_workload}"
        )
        if block_display_workload in {"performance", "scale-one-mesh", "scale-mixed-meshes"}:
            java_options.append("-Dmattmc.dev.graphicsFrameBenchmark.yawDelta=0.0")
            env.setdefault("MATTMC_CAPTURE_MAX_FPS", "260")
            env.setdefault("MATTMC_CAPTURE_DISABLE_DH_FOR_PERF", "true")
        if getattr(args, "world_mesh_block_display_instance_count", -1) > 0:
            java_options.append(
                f"-Dmattmc.dev.rustGalWorldMesh.blockDisplayInstanceCount={args.world_mesh_block_display_instance_count}"
            )
        block_display_control = getattr(args, "world_mesh_block_display_control", "rust")
        if block_display_control == "disabled":
            java_options.append("-Dmattmc.dev.rustGalWorldBlockDisplay.disabled=true")
        elif block_display_control == "legacy":
            java_options.append("-Dmattmc.dev.rustGalWorldBlockDisplay.legacyControl=true")
    if getattr(args, "world_mesh_falling_block_scenario", ""):
        java_options.append(
            f"-Dmattmc.dev.rustGalWorldMesh.fallingBlockScenario={args.world_mesh_falling_block_scenario}"
        )
        if tool_kind == "capture" and mode.backend != "rust-vulkan":
            java_options.append("-Dmattmc.dev.deterministicCameraCapture.internalScreenshots=true")
            java_options.append("-Dmattmc.dev.deterministicCameraCapture.poseCount=5")
            java_options.append("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=1")
            java_options.append("-Dmattmc.dev.rustGalWorldMesh.fallingBlockFallHeight=64")
        elif tool_kind == "capture":
            java_options.append("-Dmattmc.dev.deterministicCameraCapture.poseCount=5")
            java_options.append("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=1")
            java_options.append("-Dmattmc.dev.rustGalWorldMesh.fallingBlockFallHeight=192")
            java_options.append("-Dmattmc.dev.rustGalWorldMesh.fallingBlockSlowCapture=true")
        if tool_kind == "gameplay":
            java_options.append("-Dmattmc.dev.graphicsFrameBenchmark.yawDelta=0.0")
            java_options.append("-Dmattmc.dev.graphicsFrameBenchmark.displayFpsCheckEnabled=false")
            env.setdefault("MATTMC_CAPTURE_MAX_FPS", "260")
            env.setdefault("MATTMC_CAPTURE_DISABLE_DH_FOR_PERF", "true")
        if getattr(args, "world_mesh_falling_block_count", -1) > 0:
            java_options.append(
                f"-Dmattmc.dev.rustGalWorldMesh.fallingBlockCount={args.world_mesh_falling_block_count}"
            )
        falling_block_control = getattr(args, "world_mesh_falling_block_control", "rust")
        if falling_block_control == "disabled":
            java_options.append("-Dmattmc.dev.rustGalWorldFallingBlock.disabled=true")
        elif falling_block_control == "legacy":
            java_options.append("-Dmattmc.dev.rustGalWorldFallingBlock.legacyControl=true")
    if getattr(args, "world_mesh_piston_scenario", ""):
        java_options.append(
            f"-Dmattmc.dev.rustGalWorldMesh.pistonScenario={args.world_mesh_piston_scenario}"
        )
        if tool_kind == "capture" and mode.backend != "rust-vulkan":
            java_options.append("-Dmattmc.dev.deterministicCameraCapture.internalScreenshots=true")
            java_options.append("-Dmattmc.dev.deterministicCameraCapture.poseCount=7")
            java_options.append("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=1")
        elif tool_kind == "capture":
            java_options.append("-Dmattmc.dev.deterministicCameraCapture.poseCount=7")
            java_options.append("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=1")
        java_options.append(
            f"-Dmattmc.dev.rustGalWorldMesh.pistonDirection={args.world_mesh_piston_direction}"
        )
        if tool_kind == "gameplay" or (tool_kind == "capture" and mode.backend == "rust-vulkan"):
            java_options.append("-Dmattmc.dev.rustGalWorldMesh.freezePistonProgress=true")
            java_options.append(
                f"-Dmattmc.dev.rustGalWorldMesh.pistonProgress={max(0.0, min(1.0, args.world_mesh_piston_progress)):.3f}"
            )
        if tool_kind == "gameplay":
            java_options.append("-Dmattmc.dev.graphicsFrameBenchmark.yawDelta=0.0")
            java_options.append("-Dmattmc.dev.graphicsFrameBenchmark.displayFpsCheckEnabled=false")
            if getattr(args, "profile", "") == "performance":
                java_options.append("-Dmattmc.dev.graphicsFrameBenchmark.gcBeforeMeasurement=true")
            env.setdefault("MATTMC_CAPTURE_MAX_FPS", "260")
            env.setdefault("MATTMC_CAPTURE_DISABLE_DH_FOR_PERF", "true")
        if getattr(args, "world_mesh_piston_count", -1) > 0:
            java_options.append(
                f"-Dmattmc.dev.rustGalWorldMesh.pistonCount={args.world_mesh_piston_count}"
            )
        piston_control = getattr(args, "world_mesh_piston_control", "rust")
        if piston_control == "disabled":
            java_options.append("-Dmattmc.dev.rustGalWorldPiston.disabled=true")
        elif piston_control == "legacy":
            java_options.append("-Dmattmc.dev.rustGalWorldPiston.legacyControl=true")
    if (
        tool_kind == "capture"
        and getattr(args, "world_mesh_falling_block_scenario", "")
        and getattr(args, "world_mesh_piston_scenario", "")
    ):
        java_options.append("-Dmattmc.dev.deterministicCameraCapture.poseCount=12")
    if getattr(args, "world_material_terrain_particle_real_gameplay", False):
        java_options.append("-Dmattmc.dev.rustGalWorldMaterial.terrainParticleRealGameplay=true")
        java_options.append("-Dmattmc.dev.graphicsFrameBenchmark.displayFpsCheckEnabled=false")
        if not getattr(args, "game_mode", None):
            java_options.append("-Dmattmc.dev.graphicsFrameBenchmark.gameMode=survival")
    terrain_particle_control = getattr(args, "world_material_terrain_particle_control", "rust")
    if terrain_particle_control == "disabled":
        java_options.append("-Dmattmc.dev.rustGalWorldMaterial.terrainParticle.disabled=true")
    elif terrain_particle_control == "legacy":
        java_options.append("-Dmattmc.dev.rustGalWorldMaterial.terrainParticle.legacyControl=true")
    if getattr(args, "world_background_scenario", ""):
        java_options.append(f"-Dmattmc.dev.rustGalWorldBackground.scenario={args.world_background_scenario}")
    if getattr(args, "world_static_terrain_scenario", ""):
        java_options.append(f"-Dmattmc.dev.rustGalStaticTerrain.scenario={args.world_static_terrain_scenario}")
        java_options.append(f"-Dmattmc.dev.rustGalStaticTerrain.worldId={args.world}")
        if static_terrain_second_world:
            java_options.append(f"-Dmattmc.dev.rustGalStaticTerrain.worldB={static_terrain_second_world}")
        if args.world_static_terrain_scenario == "world-different-reload":
            env["MATTMC_WORLD_STATIC_TERRAIN_NEEDS_SECOND_WORLD"] = "true"
        if getattr(args, "world_static_terrain_fault", ""):
            java_options.append(f"-Dmattmc.dev.rustGalStaticTerrain.fault={args.world_static_terrain_fault}")
        if getattr(args, "world_static_terrain_resource_pack_scenario", ""):
            java_options.append(
                "-Dmattmc.dev.rustGalStaticTerrain.resourcePackScenario="
                f"{args.world_static_terrain_resource_pack_scenario}"
            )
        if tool_kind == "capture":
            java_options.append("-Dmattmc.dev.deterministicCameraCapture.settledReadyFamilies=static-terrain")
            java_options.append("-Dmattmc.dev.deterministicCameraCapture.settledReadyFrames=3")
            java_options.append("-Dmattmc.dev.deterministicCameraCapture.framesPerPose=1")
            java_options.append("-Dmattmc.dev.deterministicCameraCapture.poseCount=1")
            java_options.append("-Dmattmc.dev.deterministicCameraCapture.yawDelta=18.0")
        elif tool_kind == "gameplay":
            java_options.append("-Dmattmc.dev.graphicsFrameBenchmark.displayFpsCheckEnabled=false")
            env.setdefault("MATTMC_CAPTURE_MAX_FPS", "260")
            env.setdefault("MATTMC_CAPTURE_DISABLE_DH_FOR_PERF", "true")
            env.setdefault("MATTMC_CAPTURE_RENDER_DISTANCE", "4")
            env.setdefault("MATTMC_CAPTURE_SIMULATION_DISTANCE", "4")
    positive_control_delay_ms = getattr(args, "positive_control_delay_ms", 0) or 0
    if positive_control_delay_ms > 0:
        java_options.append(
            f"-Dmattmc.dev.graphicsFrameBenchmark.positiveControlDelayNanos={int(positive_control_delay_ms) * 1_000_000}"
        )
    rust_gal_gui_control = getattr(args, "rust_gal_gui_control", "rust")
    if rust_gal_gui_control == "disabled":
        java_options.append("-Dmattmc.dev.rustGalGui.disabled=true")
    elif rust_gal_gui_control == "legacy":
        java_options.append("-Dmattmc.dev.rustGalGui.legacyControl=true")
    elif rust_gal_gui_control == "armor-disabled":
        java_options.append("-Dmattmc.dev.rustGalGui.armor.disabled=true")
    elif rust_gal_gui_control == "armor-legacy":
        java_options.append("-Dmattmc.dev.rustGalGui.armor.legacyControl=true")
    elif rust_gal_gui_control == "player-health-disabled":
        java_options.append("-Dmattmc.dev.rustGalGui.playerHealth.disabled=true")
    elif rust_gal_gui_control == "player-health-legacy":
        java_options.append("-Dmattmc.dev.rustGalGui.playerHealth.legacyControl=true")
    elif rust_gal_gui_control == "absorption-disabled":
        java_options.append("-Dmattmc.dev.rustGalGui.absorption.disabled=true")
    elif rust_gal_gui_control == "absorption-legacy":
        java_options.append("-Dmattmc.dev.rustGalGui.absorption.legacyControl=true")
    elif rust_gal_gui_control == "hunger-disabled":
        java_options.append("-Dmattmc.dev.rustGalGui.hunger.disabled=true")
    elif rust_gal_gui_control == "hunger-legacy":
        java_options.append("-Dmattmc.dev.rustGalGui.hunger.legacyControl=true")
    elif rust_gal_gui_control == "air-disabled":
        java_options.append("-Dmattmc.dev.rustGalGui.air.disabled=true")
    elif rust_gal_gui_control == "air-legacy":
        java_options.append("-Dmattmc.dev.rustGalGui.air.legacyControl=true")
    elif rust_gal_gui_control == "mount-health-disabled":
        java_options.append("-Dmattmc.dev.rustGalGui.mountHealth.disabled=true")
    elif rust_gal_gui_control == "mount-health-legacy":
        java_options.append("-Dmattmc.dev.rustGalGui.mountHealth.legacyControl=true")
    if tool_kind == "capture" and mode.backend == "rust-vulkan":
        attachment_dir = capture_dir / "whole_frame_gameplay_attachments"
        env["MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_DIR"] = str(attachment_dir)
        env.setdefault("MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_MIN_FRAME", "1")
        min_mesh_instances = 1
        if getattr(args, "world_mesh_falling_block_scenario", "") or getattr(args, "world_mesh_piston_scenario", ""):
            min_mesh_instances = 2
        env.setdefault("MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_MIN_MESH_INSTANCES", str(min_mesh_instances))
    if tool_kind == "gameplay":
        frame_status = capture_dir / f"graphics_frame_benchmark_{timestamp()}.json"
        settle_frames = mode_frame_count(args.settle_frames, mode, args, "--settle-frames")
        max_settle_frames = mode_frame_count(args.max_settle_frames, mode, args, "--max-settle-frames")
        warmup_frames = mode_frame_count(args.warmup_frames, mode, args, "--warmup-frames")
        measure_frames = mode_frame_count(args.measure_frames, mode, args, "--measure-frames")
        java_options.extend(
            [
                "-Dmattmc.dev.graphicsFrameBenchmark=true",
                f"-Dmattmc.dev.graphicsFrameBenchmark.status={frame_status}",
                f"-Dmattmc.dev.graphicsFrameBenchmark.settleFrames={settle_frames}",
                f"-Dmattmc.dev.graphicsFrameBenchmark.maxSettleFrames={max_settle_frames}",
                f"-Dmattmc.dev.graphicsFrameBenchmark.warmupFrames={warmup_frames}",
                f"-Dmattmc.dev.graphicsFrameBenchmark.measureFrames={measure_frames}",
                f"-Dmattmc.dev.graphicsFrameBenchmark.readinessTimeoutSeconds={getattr(args, 'readiness_timeout_seconds', RUNTIME_PROFILES['standard'].readiness_timeout_seconds)}",
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
        subsystem_iterations = mode_frame_count(args.subsystem_iterations, mode, args, "--subsystem-iterations")
        java_options.extend(
            [
                "-Dmattmc.dev.graphicsSubsystemBenchmark=true",
                f"-Dmattmc.dev.graphicsSubsystemBenchmark.status={subsystem_status}",
                f"-Dmattmc.dev.graphicsSubsystemBenchmark.iterations={subsystem_iterations}",
                f"-Dmattmc.dev.graphicsSubsystemBenchmark.backend={mode.backend}",
                f"-Dmattmc.dev.graphicsSubsystemBenchmark.shaders={mode.shaders}",
            ]
        )
        env["MATTMC_GRAPHICS_SUBSYSTEM_BENCHMARK"] = "true"
        env["MATTMC_GRAPHICS_SUBSYSTEM_STATUS"] = str(subsystem_status)
    if tool_kind == "capture":
        java_options.extend(
            [
                "-Dmattmc.dev.graphicsAuditSliceMetrics=true",
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
    if validation == "routine":
        env["VK_LAYER_SETTINGS"] = "validate_sync=true,validate_best_practices=true"
    elif validation == "deep":
        env["VK_LAYER_SETTINGS"] = "validate_sync=true,validate_best_practices=true,gpuav_enable=true,printf_enable=true"
    if getattr(args, "tracy_capture", False):
        env["MATTMC_GRAPHICS_AUDIT"] = "true"
        java_options.append("-Dmattmc.dev.tracyCapture=true")
        java_options.append("-Dmattmc.dev.graphicsAuditSliceMetrics=true")
    if getattr(args, "renderdoc_capture", False):
        renderdoc = local_renderdoccmd_path()
        renderdoc_capture = capture_dir / f"renderdoc_{timestamp()}.rdc"
        renderdoc_template = capture_dir / f"renderdoc_{timestamp()}"
        java_options.extend(
            [
                "-Dmattmc.dev.graphicsAuditSliceMetrics=true",
                "-Dmattmc.dev.renderdocCapture=true",
                f"-Dmattmc.dev.renderdocCapture.backend={mode.backend}",
                f"-Dmattmc.dev.renderdocCapture.pathTemplate={renderdoc_template}",
            ]
        )
        renderdoc_library = local_renderdoc_library_path()
        if renderdoc_library:
            java_options.append(f"-Dmattmc.dev.renderdocCapture.library={renderdoc_library}")
            existing_preload = env.get("LD_PRELOAD", "").strip()
            env["LD_PRELOAD"] = f"{renderdoc_library} {existing_preload}".strip()
            existing_library_path = env.get("LD_LIBRARY_PATH", "").strip()
            env["LD_LIBRARY_PATH"] = f"{Path(renderdoc_library).parent} {existing_library_path}".strip().replace(" ", ":")
        renderdoc_layer = local_renderdoc_layer_manifest_path()
        if renderdoc_layer and mode.backend == "vulkan":
            layer_dir = str(renderdoc_layer.parent)
            existing_implicit = env.get("VK_ADD_IMPLICIT_LAYER_PATH", "").strip()
            env["VK_ADD_IMPLICIT_LAYER_PATH"] = f"{layer_dir}:{existing_implicit}".strip(":")
            env["VK_LOADER_LAYERS_ENABLE"] = "VK_LAYER_RENDERDOC_Capture"
            env["MATTMC_RENDERDOC_VULKAN_LAYER_MANIFEST"] = str(renderdoc_layer)
        if renderdoc:
            env["MATTMC_RENDERDOC_CMD"] = renderdoc
            env["MATTMC_RENDERDOC_CAPTURE_TEMPLATE"] = str(renderdoc_template)
            env["MATTMC_RENDERDOC_CAPTURE_PATH"] = str(renderdoc_capture)
        else:
            env["MATTMC_RENDERDOC_CAPTURE_PATH"] = str(renderdoc_capture)
    env["JAVA_TOOL_OPTIONS"] = " ".join(shlex.quote(part) for part in java_options if part).strip()
    return command, env


def shader_gbuffer_report_status(report: dict[str, object] | None) -> tuple[str, list[str]]:
    messages: list[str] = []
    if not isinstance(report, dict):
        return "failed", ["missing report"]
    if report.get("java_iris_participation") is not False:
        messages.append("Java/Iris participation was not explicitly false")
    if report.get("pass_graph") != "vulkanic:builtin/terrain_material_multipass_v1":
        messages.append("shader G-buffer report did not use the multi-pass graph")
    passes = report.get("passes")
    required_passes = (
        "vulkanic:pass/shadow_depth",
        "vulkanic:pass/terrain_opaque",
        "vulkanic:pass/terrain_cutout",
        "vulkanic:pass/deferred_lighting",
        "vulkanic:pass/composite_0",
        "vulkanic:pass/composite_1",
        "vulkanic:pass/final_output",
    )
    if not isinstance(passes, list) or any(pass_name not in passes for pass_name in required_passes):
        messages.append("multi-pass shader graph is missing required passes")
    if int(report.get("mesh_draw_count") or 0) <= 0:
        messages.append("mesh_draw_count was zero")
    evidence = report.get("attachment_semantic_evidence")
    if not isinstance(evidence, dict):
        messages.append("missing attachment semantic evidence")
        return "failed", messages
    for name in (
        "shadow_depth",
        "albedo",
        "normal",
        "material_light",
        "world_position",
        "depth",
        "deferred_lit",
        "composite_0",
        "composite_1",
        "final_composite",
    ):
        item = evidence.get(name)
        if not isinstance(item, dict) or item.get("present") is not True:
            messages.append(f"{name} attachment is missing")
    normal = evidence.get("normal") if isinstance(evidence.get("normal"), dict) else {}
    if int(normal.get("distinct_rgba_sample_count") or 0) < 2:
        messages.append("normal attachment is uniform")
    material_light = evidence.get("material_light") if isinstance(evidence.get("material_light"), dict) else {}
    if int(material_light.get("non_default_pixels") or 0) <= 0:
        messages.append("material/light attachment has no non-default pixels")
    depth = evidence.get("depth") if isinstance(evidence.get("depth"), dict) else {}
    if int(depth.get("less_than_clear_pixels") or 0) <= 0:
        messages.append("depth attachment has no written pixels")
    shadow_depth = evidence.get("shadow_depth") if isinstance(evidence.get("shadow_depth"), dict) else {}
    if int(shadow_depth.get("less_than_clear_pixels") or 0) <= 0:
        messages.append("shadow depth attachment has no written pixels")
    try:
        if float(depth.get("max_depth") or 0.0) <= float(depth.get("min_depth") or 0.0):
            messages.append("depth attachment is not ranged")
    except (TypeError, ValueError):
        messages.append("depth attachment min/max are malformed")
    perturb = report.get("composite_dependency_perturbations")
    if not isinstance(perturb, dict):
        messages.append("missing composite perturbation evidence")
    else:
        for key in ("albedo_perturbation", "normal_perturbation", "material_light_perturbation"):
            item = perturb.get(key)
            if not isinstance(item, dict) or item.get("final_changes") is not True:
                messages.append(f"{key} did not affect the final composite")
    shadow = report.get("shadow_dependency_evidence")
    if not isinstance(shadow, dict):
        messages.append("missing shadow dependency evidence")
    else:
        if int(shadow.get("moving_shadow_depth_hashes") or 0) < 2:
            messages.append("shadow depth did not vary across moving frames")
        if int(shadow.get("moving_final_hashes") or 0) < 2:
            messages.append("final composite did not vary across moving shadow frames")
        counts = shadow.get("shadow_depth_less_than_clear")
        if not isinstance(counts, list) or not any(int(value or 0) > 0 for value in counts):
            messages.append("shadow depth evidence has no written samples")
        shadowed = shadow.get("shadowed_material_pixels")
        if not isinstance(shadowed, list) or not any(int(value or 0) > 0 for value in shadowed):
            messages.append("shadow map did not shadow any material receiver pixels")
    chain = report.get("composite_chain_evidence")
    if not isinstance(chain, dict):
        messages.append("missing composite chain evidence")
    else:
        if int(chain.get("deferred_to_composite_0_changed_pixels") or 0) <= 0:
            messages.append("composite_0 did not change deferred-lit color")
        if int(chain.get("composite_0_to_composite_1_changed_pixels") or 0) <= 0:
            messages.append("composite_1 did not change composite_0")
        if chain.get("final_reads_last_configured_pass") is not True:
            messages.append("final output was not copied from the last configured composite pass")
    return ("failed" if messages else "ok"), messages


def write_shader_gbuffer_subsystem_status(
    capture_dir: Path,
    command: Sequence[str],
    exit_code: int,
    duration_seconds: float,
    shader_artifact_dir: Path,
    required_backend: str,
) -> Path:
    workloads: list[dict[str, object]] = []
    expected_backends = (required_backend,)
    for backend in expected_backends:
        report_path = shader_artifact_dir / backend / "latest.json"
        report = read_json(report_path) if report_path.is_file() else None
        status, messages = shader_gbuffer_report_status(report)
        workloads.append(
            {
                "name": f"rust-owned.shader-gbuffer-scene.{backend}",
                "status": status,
                "backend": backend,
                "report": str(report_path),
                "messages": messages,
                "mesh_draw_count": report.get("mesh_draw_count") if isinstance(report, dict) else 0,
                "mesh_instance_count": report.get("mesh_instance_count") if isinstance(report, dict) else 0,
                "attachment_hashes": report.get("attachment_hashes") if isinstance(report, dict) else {},
                "screenshots": report.get("screenshots") if isinstance(report, dict) else [],
                "attachment_dumps": report.get("attachment_dumps") if isinstance(report, dict) else [],
            }
        )
    comparison = shader_artifact_dir / "comparison-latest.json"
    complete = exit_code == 0 and all(workload["status"] == "ok" for workload in workloads)
    status_doc = {
        "schema": "mattmc-graphics-subsystem-benchmark-v1",
        "status": "complete" if complete else "failed",
        "workload": "rust-owned.shader-gbuffer-scene",
        "required_backend": required_backend,
        "workloads": workloads,
        "command": list(command),
        "exit_code": exit_code,
        "duration_seconds": duration_seconds,
        "shader_artifact_dir": str(shader_artifact_dir),
        "comparison_report": str(comparison) if comparison.is_file() else None,
        "ffi_cadence": "coarse-rust-test-scene",
        "java_iris_participation": False,
        "rust_owned_pass_graph": True,
    }
    path = capture_dir / f"graphics_subsystem_benchmark_{timestamp()}.json"
    path.write_text(json.dumps(status_doc, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return path


def shader_gbuffer_command_and_env(
    target: RepoTarget,
    mode: ModeSpec,
    capture_dir: Path,
    args: argparse.Namespace,
) -> tuple[list[str], dict[str, str], Path]:
    shader_artifact_dir = capture_dir / "shader-gbuffer"
    env = os.environ.copy()
    env["MATTMC_SHADER_GBUFFER_ARTIFACT_DIR"] = str(shader_artifact_dir)
    env["MATTMC_GRAPHICS_TOOL_INTERNAL"] = "1"
    env["MATTMC_GRAPHICS_RUN_TYPE"] = run_type_for_args(args)
    env["MATTMC_RUST_TRACY"] = "true" if getattr(args, "tracy_capture", False) else "false"
    env["MATTMC_TRACY_CAPTURE"] = "true" if getattr(args, "tracy_capture", False) else "false"
    env["MATTMC_TRACY_DURATION_SECONDS"] = str(getattr(args, "tracy_duration_seconds", 20))
    env["MATTMC_TRACY_MAX_SIZE_MB"] = str(getattr(args, "tracy_max_size_mb", 256))
    env["MATTMC_OPENGL_STRICT"] = "1"
    requested_validation = "routine" if getattr(args, "validation", "off") == "standard" else getattr(args, "validation", "off")
    if requested_validation in {"routine", "deep"}:
        env["VK_INSTANCE_LAYERS"] = "VK_LAYER_KHRONOS_validation"
        env["VK_LOADER_DEBUG"] = "info"
        env["VK_LAYER_SETTINGS"] = "validate_sync=true,validate_best_practices=true"
        if requested_validation == "deep":
            env["VK_LAYER_SETTINGS"] = "validate_sync=true,validate_best_practices=true,gpuav_enable=true,printf_enable=true"
    base = [
        "cargo",
        "test",
        "--manifest-path",
        str(target.root / "src" / "main" / "rust" / "Cargo.toml"),
    ]
    if getattr(args, "tracy_capture", False):
        base.extend(["--features", "tracy"])
    base.extend([RUST_SHADER_GBUFFER_SCENE_TEST, "--", "--nocapture"])
    if getattr(args, "renderdoc_capture", False):
        renderdoc = local_renderdoccmd_path()
        if not renderdoc:
            return base, env, shader_artifact_dir
        binary = build_rust_shader_gbuffer_test_binary(target.root)
        renderdoc_template = capture_dir / f"renderdoc_{timestamp()}"
        env["MATTMC_RENDERDOC_CAPTURE"] = "1"
        env["MATTMC_RENDERDOC_BACKEND"] = "vulkan" if "vulkan" in mode.backend else "opengl"
        env["MATTMC_RENDERDOC_CAPTURE_TEMPLATE"] = str(renderdoc_template)
        env["MATTMC_RENDERDOC_CAPTURE_PATH"] = str(renderdoc_template.with_suffix(".rdc"))
        renderdoc_library = local_renderdoc_library_path()
        if renderdoc_library:
            existing_preload = env.get("LD_PRELOAD", "")
            existing_library_path = env.get("LD_LIBRARY_PATH", "")
            env["LD_PRELOAD"] = f"{renderdoc_library} {existing_preload}".strip()
            env["LD_LIBRARY_PATH"] = f"{Path(renderdoc_library).parent} {existing_library_path}".strip().replace(" ", ":")
            env["MATTMC_RENDERDOC_LIBRARY_PATH"] = renderdoc_library
        renderdoc_layer = local_renderdoc_layer_manifest_path()
        if renderdoc_layer:
            env["VK_ADD_LAYER_PATH"] = str(renderdoc_layer.parent)
            env["VK_INSTANCE_LAYERS"] = "VK_LAYER_RENDERDOC_Capture"
            env["MATTMC_RENDERDOC_VULKAN_LAYER_MANIFEST"] = str(renderdoc_layer)
        base = [
            renderdoc,
            "capture",
            "-w",
            "-d",
            str(target.root),
            "-c",
            str(renderdoc_template),
            str(binary),
            RUST_SHADER_GBUFFER_SCENE_TEST,
            "--nocapture",
        ]
    return base, env, shader_artifact_dir


def collect_shader_gbuffer_tracy(
    target: RepoTarget,
    capture_dir: Path,
    process: subprocess.Popen[str],
    args: argparse.Namespace,
    started: float,
) -> None:
    tracy_tool = local_tracy_capture_path()
    capture_summaries: list[dict[str, object]] = []
    records: list[dict[str, object]] = []
    outputs: list[str] = []
    if not tracy_tool:
        write_tracy_summary(capture_dir, "failed", capture_dir / f"tracy_missing_{timestamp()}.tracy", "tracy-capture is not installed")
        return
    duration = max(1, int(getattr(args, "tracy_duration_seconds", 20)))
    deadline = time.monotonic() + min(child_process_timeout_seconds(args), duration + 30)
    active: list[dict[str, object]] = []
    seen_ports: set[int] = set()
    attempt = 0

    def collect_finished(*, force: bool = False) -> None:
        for record in list(active):
            capture_process = record.get("process")
            if not isinstance(capture_process, subprocess.Popen):
                active.remove(record)
                continue
            if not force and capture_process.poll() is None:
                continue
            try:
                output, _ = capture_process.communicate(timeout=0 if force else 1)
            except subprocess.TimeoutExpired:
                terminate_process_tree(capture_process)
                output, _ = capture_process.communicate(timeout=2)
                record["failure"] = "tracy-capture timed out"
            if output:
                outputs.append(f"attempt {record.get('attempt')} port {record.get('port')}:\n{output}")
            record["returncode"] = capture_process.returncode
            record["output"] = output or ""
            record.pop("process", None)
            records.append(dict(record))
            active.remove(record)

    while process.poll() is None and time.monotonic() < deadline:
        listeners = discover_tracy_listeners(8086, 8110)
        for listener in listeners:
            if listener.port in seen_ports:
                continue
            seen_ports.add(listener.port)
            attempt += 1
            capture_path = capture_dir / f"tracy_port{listener.port}_{timestamp()}.tracy"
            try:
                capture_process = subprocess.Popen(
                    [
                        tracy_tool,
                        "-f",
                        "-a",
                        "127.0.0.1",
                        "-p",
                        str(listener.port),
                        "-o",
                        str(capture_path),
                        "-s",
                        str(duration),
                    ],
                    cwd=target.root,
                    text=True,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    **popen_kwargs(),
                )
                active.append(
                    {
                        "attempt": attempt,
                        "port": listener.port,
                        "capture_path": str(capture_path),
                        "started_at": time.monotonic(),
                        "attach_elapsed_seconds": time.monotonic() - started,
                        "listener": asdict(listener),
                        "process": capture_process,
                    }
                )
            except Exception as exc:
                records.append(
                    {
                        "attempt": attempt,
                        "port": listener.port,
                        "capture_path": str(capture_path),
                        "started_at": time.monotonic(),
                        "attach_elapsed_seconds": time.monotonic() - started,
                        "listener": asdict(listener),
                        "returncode": -1,
                        "failure": f"failed to launch tracy-capture: {exc}",
                    }
                )
        collect_finished()
        if not listeners and not active:
            time.sleep(0.1)
        else:
            time.sleep(0.25)
    collect_finished(force=True)

    max_capture_size = getattr(args, "tracy_max_size_mb", 256) * 1024 * 1024
    for index, record in enumerate(records):
        capture_path_text = record.get("capture_path")
        capture_path = Path(str(capture_path_text)) if capture_path_text else capture_dir / f"tracy_missing_{index}.tracy"
        started_at_value = parse_number(record.get("started_at"))
        if not capture_path.exists() or capture_path.stat().st_size <= 0:
            summary_path = write_tracy_summary(
                capture_dir,
                "failed",
                capture_path,
                str(record.get("failure") or "Tracy capture missing or empty"),
                started_at_value,
                record.get("output") if isinstance(record.get("output"), str) else None,
                attach_metadata=record,
                summary_prefix=f"tracy_capture_{index}_summary",
            )
        elif capture_path.stat().st_size > max_capture_size:
            summary_path = write_tracy_summary(
                capture_dir,
                "failed",
                capture_path,
                "Tracy capture exceeded size limit",
                started_at_value,
                record.get("output") if isinstance(record.get("output"), str) else None,
                attach_metadata=record,
                summary_prefix=f"tracy_capture_{index}_summary",
            )
        else:
            summary_path = extract_tracy_summary(
                capture_dir,
                capture_path,
                started_at=started_at_value,
                attach_metadata=record,
                require_rust_zones=True,
                summary_prefix=f"tracy_capture_{index}_summary",
            )
        capture_summaries.append(read_json(summary_path) or {})
    if capture_summaries:
        write_tracy_collection_summary(
            capture_dir,
            capture_summaries,
            require_rust_zones=True,
            require_java_zones=False,
            failure=None if records else "tracy-capture did not discover any Tracy listener before workload exited",
            tool_output="\n".join(outputs),
        )
    else:
        write_tracy_summary(
            capture_dir,
            "failed",
            capture_dir / f"tracy_unattached_{timestamp()}.tracy",
            "tracy-capture did not discover any Tracy listener before workload exited",
            tool_output="\n".join(outputs),
        )


def run_shader_gbuffer_subsystem_mode(
    target: RepoTarget,
    mode: ModeSpec,
    artifact_root: Path,
    args: argparse.Namespace,
    repetition: int,
) -> MatrixResult:
    run_dir = f"run-{repetition:02d}"
    row_label = matrix_row_label(mode, "subsystem", repetition)
    capture_dir = artifact_root / mode.name / "subsystem" / run_dir / "capture"
    output_path = artifact_root / mode.name / "subsystem" / run_dir / ARTIFACT_NAME
    capture_dir.mkdir(parents=True, exist_ok=True)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    command, env, shader_artifact_dir = shader_gbuffer_command_and_env(target, mode, capture_dir, args)
    meta_path = write_preflight_meta(capture_dir, mode, args, env)
    meta = read_key_values(meta_path)
    run_id = meta.get("run_id", f"shader-gbuffer-{timestamp()}")
    emit_matrix_progress(args, row_label, "process-launched", "rust-owned shader g-buffer scene")
    started = time.monotonic()
    stdout_path = output_path.parent / "stdout.log"
    stderr_path = output_path.parent / "stderr.log"
    timed_out = False
    timed_out_phase: str | None = None
    error = ""
    with stdout_path.open("w", encoding="utf-8") as stdout, stderr_path.open("w", encoding="utf-8") as stderr:
        try:
            process = subprocess.Popen(
                command,
                cwd=target.root,
                text=True,
                stdout=stdout,
                stderr=stderr,
                env=env,
                **popen_kwargs(),
            )
            if getattr(args, "tracy_capture", False):
                collect_shader_gbuffer_tracy(target, capture_dir, process, args, started)
            exit_code = process.wait(timeout=child_process_timeout_seconds(args))
        except subprocess.TimeoutExpired:
            timed_out = True
            timed_out_phase = "measurement/capture"
            exit_code = 124
            error = f"Timed out after {child_process_timeout_seconds(args)}s"
    duration = time.monotonic() - started
    status_path = write_shader_gbuffer_subsystem_status(
        capture_dir,
        command,
        exit_code,
        duration,
        shader_artifact_dir,
        "opengl" if mode.backend == "rust-opengl" else "vulkan",
    )
    if getattr(args, "validation", "off") != "off":
        validation_log = capture_dir / f"validation_events_{run_id}_{timestamp()}.log"
        text = (
            f"run_id={run_id}\n"
            f"VK_LAYER_KHRONOS_validation active for rust-owned shader G-buffer subsystem route\n"
            f"validation_profile={getattr(args, 'validation', 'off')}\n"
        )
        for path in (stdout_path, stderr_path):
            try:
                raw = path.read_text(encoding="utf-8", errors="replace")[-NORMALIZED_LOG_TAIL_BYTES:]
                retained_lines = [
                    line
                    for line in raw.splitlines()
                    if "[Vulkan Loader]" not in line and "loader_get_json" not in line
                ]
                text += f"\n--- {path.name} ---\n" + "\n".join(retained_lines) + "\n"
            except OSError:
                pass
        validation_log.write_text(text, encoding="utf-8")
    if getattr(args, "renderdoc_capture", False):
        replay_renderdoc_summary(capture_dir, renderdoc_capture_path_from_env(env, capture_dir))
    target_paths = select_targets(args)
    repository_paths = repository_resolution(target_paths["current"].root, target_paths["frozen"].root, getattr(args, "frozen_repo", None))
    artifact = normalize_capture_artifact(
        target,
        mode,
        capture_dir,
        args.workload_profile,
        exit_code == 0 and not timed_out,
        command,
        exit_code,
        timed_out,
        timed_out_phase=timed_out_phase,
        tool_kind="subsystem",
        runtime_profile=runtime_profile_dict(args),
        repository_paths=repository_paths,
    )
    subsystem_doc = read_json(status_path) or {}
    validation_doc = artifact.get("validation") if isinstance(artifact.get("validation"), dict) else {}
    success = (
        exit_code == 0
        and subsystem_doc.get("status") == "complete"
        and not timed_out
        and bool(validation_doc.get("complete", True))
    )
    artifact["capture"]["success"] = success
    artifact["capture"]["stdout_path"] = str(stdout_path)
    artifact["capture"]["stderr_path"] = str(stderr_path)
    artifact["capture"]["duration_seconds"] = duration
    if not success and not error:
        error = "Rust-owned shader G-buffer subsystem scene failed validation"
    write_artifact(output_path, artifact)
    emit_matrix_progress(
        args,
        row_label,
        "artifact-finalized",
        f"success={str(success).lower()} artifact={output_path}",
    )
    return MatrixResult(
        mode.name,
        success,
        False,
        timed_out,
        timed_out_phase,
        exit_code,
        str(output_path),
        str(capture_dir),
        command,
        error,
    )


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
    row_label = matrix_row_label(mode, tool_kind, repetition)
    capture_dir = artifact_root / mode.name / tool_kind / run_dir / "capture"
    output_path = artifact_root / mode.name / tool_kind / run_dir / ARTIFACT_NAME
    managed_run_root = output_path.parent
    retention_policy = getattr(args, "_retention_policy", None)
    emit_matrix_progress(args, row_label, "preflight-started", f"target={target.name}")
    if retention_policy is not None and not args.dry_run:
        if not getattr(args, "artifact_preserve", False):
            artifact_retention.remove_copied_game_dirs(retention_policy.root)
        artifact_retention.preflight_disk_budget(
            retention_policy,
            artifact_retention.estimated_run_bytes(
                args.profile,
                diagnostic=args.diagnostic,
                renderdoc=getattr(args, "renderdoc_capture", False),
                tracy=getattr(args, "tracy_capture", False),
            ),
        )
    if tool_kind == "subsystem" and getattr(args, "shader_gbuffer_scene", False):
        if mode.target != "current" or mode.backend not in {"rust-opengl", "rust-vulkan"}:
            capture_dir.mkdir(parents=True, exist_ok=True)
            output_path.parent.mkdir(parents=True, exist_ok=True)
            command = ["shader-gbuffer-scene", "profile-not-supported"]
            current_root = repo_root()
            explicit_frozen_repo = getattr(args, "frozen_repo", None)
            repository_paths = repository_resolution(current_root, find_frozen_repo(current_root, explicit_frozen_repo), explicit_frozen_repo)
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
                repository_paths=repository_paths,
            )
            artifact["capture"]["profile_not_supported"] = True
            artifact["capture"]["success"] = True
            artifact["capture"]["failed_phase"] = "profile-not-supported"
            artifact["validation"]["complete"] = True
            artifact["validation"]["messages"] = [
                "Rust-owned shader G-buffer scene is supported only for Current rust-opengl/rust-vulkan subsystem rows"
            ]
            write_artifact(output_path, artifact)
            emit_matrix_progress(args, row_label, "artifact-finalized", "shader-gbuffer profile-not-supported")
            return MatrixResult(
                mode.name,
                True,
                False,
                False,
                "profile-not-supported",
                0,
                str(output_path),
                str(capture_dir),
                command,
                "shader-gbuffer scene unsupported for this row",
            )
        return run_shader_gbuffer_subsystem_mode(target, mode, artifact_root, args, repetition)
    command, env = build_capture_command(target, mode, capture_dir, effective_workload_profile, args, tool_kind)
    emit_matrix_progress(args, row_label, "preflight-completed", f"timeout={child_process_timeout_seconds(args)}s")
    current_root = repo_root()
    explicit_frozen_repo = getattr(args, "frozen_repo", None)
    repository_paths = repository_resolution(current_root, find_frozen_repo(current_root, explicit_frozen_repo), explicit_frozen_repo)
    unsupported_profile_reason = profile_not_supported_reason(args.profile, mode, tool_kind)
    if unsupported_profile_reason:
        capture_dir.mkdir(parents=True, exist_ok=True)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        write_preflight_meta(capture_dir, mode, args, env)
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
            repository_paths=repository_paths,
        )
        artifact["capture"]["profile_not_supported"] = True
        artifact["capture"]["minimum_supported_profile"] = minimum_supported_profile(mode, tool_kind)
        artifact["capture"]["requested_profile"] = args.profile
        artifact["capture"]["success"] = True
        artifact["capture"]["failed_phase"] = "profile-not-supported"
        artifact["validation"]["complete"] = True
        artifact["validation"]["messages"] = [unsupported_profile_reason]
        artifact["validation"]["performance_publishable"] = False
        write_artifact(output_path, artifact)
        emit_matrix_progress(args, row_label, "artifact-finalized", unsupported_profile_reason)
        return MatrixResult(
            mode.name,
            True,
            False,
            False,
            "profile-not-supported",
            0,
            str(output_path),
            str(capture_dir),
            command,
            unsupported_profile_reason,
        )
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
            repository_paths=repository_paths,
        )
        write_artifact(output_path, artifact)
        emit_matrix_progress(args, row_label, "artifact-finalized", "dry_run=true")
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
            repository_paths=repository_paths,
        )
        write_artifact(output_path, artifact)
        emit_matrix_progress(args, row_label, "artifact-finalized", "renderdoccmd missing")
        return MatrixResult(mode.name, False, False, False, "renderdoccmd is not installed", 127, str(output_path), str(capture_dir), command)
    tracy_capture_path: Path | None = None
    tracy_tool: str | None = None
    tracy_result: dict[str, object] = {}
    tracy_thread: threading.Thread | None = None
    tracy_active_processes: list[subprocess.Popen[str]] = []
    tracy_capture_records: list[dict[str, object]] = []
    if getattr(args, "tracy_capture", False):
        tracy_tool = local_tracy_capture_path()
        tracy_capture_path = capture_dir / f"tracy_unattached_{timestamp()}.tracy"
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
                repository_paths=repository_paths,
            )
            write_artifact(output_path, artifact)
            emit_matrix_progress(args, row_label, "artifact-finalized", "tracy-capture missing")
            return MatrixResult(mode.name, False, False, False, "tracy-capture is not installed", 127, str(output_path), str(capture_dir), command)
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
        try:
            process = subprocess.Popen(
                command,
                cwd=target.root,
                text=True,
                stdout=stdout,
                stderr=stderr,
                env=env,
                **popen_kwargs(),
            )
        except Exception as exc:
            write_preflight_meta(capture_dir, mode, args, env)
            artifact = normalize_capture_artifact(
                target,
                mode,
                capture_dir,
                effective_workload_profile,
                False,
                command,
                127,
                False,
                timed_out_phase="startup",
                tool_kind=tool_kind,
                runtime_profile=runtime_profile_dict(args),
                repository_paths=repository_paths,
            )
            artifact["capture"]["stdout_path"] = str(stdout_path)
            artifact["capture"]["stderr_path"] = str(stderr_path)
            artifact["capture"]["duration_seconds"] = time.monotonic() - started
            write_artifact(output_path, artifact)
            emit_matrix_progress(args, row_label, "artifact-finalized", f"launch_error={exc}")
            return MatrixResult(mode.name, False, False, False, "startup", 127, str(output_path), str(capture_dir), command, str(exc))
        emit_matrix_progress(args, row_label, "process-launched", f"pid={process.pid}")
        if getattr(args, "tracy_capture", False) and tracy_tool and tracy_capture_path:
            def capture_tracy_while_running() -> None:
                outputs: list[str] = []
                duration = int(getattr(args, "tracy_duration_seconds", 20))
                deadline = time.monotonic() + min(timeout_seconds, max(10, duration + 30))
                attempt = 0
                seen_ports: set[int] = set()
                active: list[dict[str, object]] = []

                def collect_finished(*, force: bool = False) -> None:
                    for record in list(active):
                        capture_process = record.get("process")
                        if not isinstance(capture_process, subprocess.Popen):
                            active.remove(record)
                            continue
                        if not force and capture_process.poll() is None:
                            continue
                        try:
                            output, _ = capture_process.communicate(timeout=0 if force else 1)
                        except subprocess.TimeoutExpired:
                            terminate_process_tree(capture_process)
                            output, _ = capture_process.communicate(timeout=2)
                            record["failure"] = "tracy-capture timed out"
                        if output:
                            outputs.append(f"attempt {record.get('attempt')} port {record.get('port')}:\n{output}")
                        record["returncode"] = capture_process.returncode
                        record["output"] = output or ""
                        record.pop("process", None)
                        tracy_capture_records.append(dict(record))
                        if capture_process in tracy_active_processes:
                            tracy_active_processes.remove(capture_process)
                        active.remove(record)

                while process.poll() is None and time.monotonic() < deadline:
                    candidate_listeners = discover_tracy_listeners(8086, 8110)
                    if candidate_listeners:
                        tracy_result.setdefault("listener_snapshots", []).append(
                            {
                                "elapsed_seconds": time.monotonic() - started,
                                "listeners": [asdict(listener) for listener in candidate_listeners],
                            }
                        )
                    for listener in candidate_listeners:
                        port = listener.port
                        if port in seen_ports:
                            continue
                        seen_ports.add(port)
                        attempt += 1
                        port_capture_path = capture_dir / f"tracy_port{port}_{timestamp()}.tracy"
                        try:
                            started_at = time.monotonic()
                            capture_process = subprocess.Popen(
                                [
                                    tracy_tool,
                                    "-f",
                                    "-a",
                                    "127.0.0.1",
                                    "-p",
                                    str(port),
                                    "-o",
                                    str(port_capture_path),
                                    "-s",
                                    str(duration),
                                ],
                                cwd=target.root,
                                text=True,
                                stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT,
                                **popen_kwargs(),
                            )
                            tracy_active_processes.append(capture_process)
                            active.append(
                                {
                                    "attempt": attempt,
                                    "port": port,
                                    "capture_path": str(port_capture_path),
                                    "started_at": started_at,
                                    "attach_elapsed_seconds": started_at - started,
                                    "listener": asdict(listener),
                                    "process": capture_process,
                                }
                            )
                        except Exception as exc:
                            tracy_capture_records.append(
                                {
                                    "attempt": attempt,
                                    "port": port,
                                    "capture_path": str(port_capture_path),
                                    "started_at": time.monotonic(),
                                    "attach_elapsed_seconds": time.monotonic() - started,
                                    "listener": asdict(listener),
                                    "returncode": -1,
                                    "failure": f"failed to launch tracy-capture: {exc}",
                                }
                            )
                    collect_finished()
                    if not candidate_listeners and not active:
                        time.sleep(0.1)
                        continue
                    time.sleep(0.25)
                collect_finished(force=True)
                tracy_result.update(
                    {
                        "returncode": 0 if any(
                            Path(str(record.get("capture_path", ""))).exists()
                            and Path(str(record.get("capture_path", ""))).stat().st_size > 0
                            for record in tracy_capture_records
                        ) else None,
                        "output": "\n".join(outputs),
                        "attempts": attempt,
                        "captures": list(tracy_capture_records),
                    }
                )
                if not tracy_capture_records:
                    tracy_result["failure"] = "tracy-capture did not discover any Tracy listener before workload process exited"

            tracy_thread = threading.Thread(target=capture_tracy_while_running, name="mattmc-tracy-capture", daemon=True)
            tracy_thread.start()
        try:
            last_quota_check = 0.0
            last_progress = 0.0
            emitted_live_events: set[str] = set()
            subsystem_terminal_started: float | None = None
            artifact_finalization_started: float | None = None
            while True:
                exit_code = process.poll()
                if exit_code is not None:
                    break
                now = time.monotonic()
                if tool_kind == "subsystem":
                    subsystem_status, subsystem_path = latest_subsystem_status(capture_dir)
                    if subsystem_status in {"complete", "failed", "partial"}:
                        if subsystem_terminal_started is None:
                            subsystem_terminal_started = now
                            emit_matrix_progress(
                                args,
                                row_label,
                                "shutdown-started",
                                f"subsystem_status={subsystem_status} artifact={subsystem_path}",
                            )
                        shutdown_budget = max(1, phase_timeout_seconds(args, "shutdown"))
                        if now - subsystem_terminal_started > shutdown_budget:
                            terminate_process_tree(process)
                            cleanup_killed_processes = cleanup_repo_processes(target.root)
                            cleanup_timeout = max(1, phase_timeout_seconds(args, "cleanup"))
                            try:
                                process.wait(timeout=cleanup_timeout)
                            except subprocess.TimeoutExpired:
                                terminate_process_tree(process)
                                process.wait(timeout=cleanup_timeout)
                            exit_code = 0 if subsystem_status == "complete" else 1
                            error = (
                                error
                                or f"Subsystem artifact reached {subsystem_status}; parent bounded shutdown after {shutdown_budget}s"
                            )
                            break
                live_phase, live_detail = live_capture_phase(capture_dir, tool_kind)
                if live_phase == "artifact-finalization":
                    if artifact_finalization_started is None:
                        artifact_finalization_started = now
                    cleanup_budget = max(1, phase_timeout_seconds(args, "cleanup"))
                    if now - artifact_finalization_started > cleanup_budget:
                        meta_path = latest_capture_meta_path(capture_dir)
                        meta = read_key_values(meta_path) if meta_path else {}
                        recorded_exit = parse_number(meta.get("exit_code"))
                        terminate_process_tree(process)
                        cleanup_killed_processes = cleanup_repo_processes(target.root)
                        try:
                            process.wait(timeout=cleanup_budget)
                        except subprocess.TimeoutExpired:
                            terminate_process_tree(process)
                            process.wait(timeout=cleanup_budget)
                        exit_code = int(recorded_exit) if recorded_exit is not None else 1
                        error = (
                            error
                            or f"Capture wrote exit_code={exit_code}; parent bounded artifact finalization after {cleanup_budget}s"
                        )
                        break
                if matrix_progress_enabled(args) and now - last_progress >= MATRIX_PROGRESS_INTERVAL_SECONDS:
                    last_progress = now
                    if live_phase == "readiness" and "readiness-reached" not in emitted_live_events:
                        emit_matrix_progress(args, row_label, "readiness-reached", live_detail)
                        emitted_live_events.add("readiness-reached")
                    if live_phase == "measurement/capture" and "measurement-capture-started" not in emitted_live_events:
                        if "readiness-reached" not in emitted_live_events:
                            emit_matrix_progress(args, row_label, "readiness-reached", live_detail)
                            emitted_live_events.add("readiness-reached")
                        emit_matrix_progress(args, row_label, "measurement-capture-started", live_detail)
                        emitted_live_events.add("measurement-capture-started")
                    if live_phase == "shutdown" and "shutdown-started" not in emitted_live_events:
                        emit_matrix_progress(args, row_label, "shutdown-started", live_detail)
                        emitted_live_events.add("shutdown-started")
                    emit_matrix_progress(args, row_label, "active", f"{live_phase}; elapsed={int(now - started)}s; {live_detail}")
                if now - started > timeout_seconds:
                    if live_phase == "artifact-finalization":
                        meta_path = latest_capture_meta_path(capture_dir)
                        meta = read_key_values(meta_path) if meta_path else {}
                        recorded_exit = parse_number(meta.get("exit_code"))
                        if recorded_exit is not None:
                            terminate_process_tree(process)
                            cleanup_killed_processes = cleanup_repo_processes(target.root)
                            cleanup_timeout = max(1, phase_timeout_seconds(args, "cleanup"))
                            try:
                                process.wait(timeout=cleanup_timeout)
                            except subprocess.TimeoutExpired:
                                terminate_process_tree(process)
                                process.wait(timeout=cleanup_timeout)
                            exit_code = int(recorded_exit)
                            error = (
                                error
                                or f"Capture wrote exit_code={exit_code}; parent bounded artifact finalization at mode timeout"
                            )
                            break
                    timed_out = True
                    if not timed_out_phase:
                        timed_out_phase, _ = live_capture_phase(capture_dir, tool_kind)
                    terminate_process_tree(process)
                    cleanup_killed_processes = cleanup_repo_processes(target.root)
                    cleanup_timeout = max(1, phase_timeout_seconds(args, "cleanup"))
                    exit_code = process.wait(timeout=cleanup_timeout)
                    error = f"Timed out after {timeout_seconds}s; profile hard cap is {hard_timeout_seconds}s"
                    emit_matrix_progress(args, row_label, "shutdown-started", f"timeout_phase={timed_out_phase}")
                    break
                if retention_policy is not None and now - last_quota_check >= 2.0:
                    last_quota_check = now
                    try:
                        artifact_retention.run_size_check(retention_policy, managed_run_root)
                    except RuntimeError as quota_error:
                        timed_out = True
                        timed_out_phase = "artifact-quota"
                        error = str(quota_error)
                        artifact_retention.write_quota_failure(retention_policy.root, managed_run_root, error)
                        terminate_process_tree(process)
                        cleanup_killed_processes = cleanup_repo_processes(target.root)
                        cleanup_timeout = max(1, phase_timeout_seconds(args, "cleanup"))
                        exit_code = process.wait(timeout=cleanup_timeout)
                        emit_matrix_progress(args, row_label, "shutdown-started", "artifact quota exceeded")
                        break
                time.sleep(0.25)
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
            if retention_policy is not None:
                artifact_retention.write_quota_failure(retention_policy.root, managed_run_root, "interrupted before completion")
                artifact_retention.cleanup(retention_policy, after_run=managed_run_root)
            raise
    cleanup_killed_processes = sorted(set(cleanup_killed_processes + cleanup_repo_processes(target.root)))
    emit_matrix_progress(args, row_label, "shutdown-started", "process exited; finalizing artifact")
    if getattr(args, "renderdoc_capture", False):
        replay_renderdoc_summary(capture_dir, renderdoc_capture_path_from_env(env, capture_dir))
    if getattr(args, "tracy_capture", False):
        tracy_failure = None
        tracy_tool_output = tracy_result.get("output") if isinstance(tracy_result.get("output"), str) else None
        if tracy_thread is not None:
            tracy_thread.join(timeout=max(1, min(getattr(args, "tracy_duration_seconds", 20) + 5, 60)))
            if tracy_thread.is_alive():
                for active in list(tracy_active_processes):
                    terminate_process_tree(active)
                tracy_failure = "tracy-capture worker timed out"
        if tracy_failure is None and tracy_result.get("failure"):
            tracy_failure = str(tracy_result["failure"])
        if tracy_failure is None and tracy_result.get("returncode") not in (0, None):
            tracy_failure = f"tracy-capture exited {tracy_result.get('returncode')} after {tracy_result.get('attempts', 0)} attempts"
        max_capture_size = getattr(args, "tracy_max_size_mb", 256) * 1024 * 1024
        capture_summaries: list[dict[str, object]] = []
        records = tracy_result.get("captures", tracy_capture_records)
        if isinstance(records, list):
            for index, record in enumerate(records):
                if not isinstance(record, Mapping):
                    continue
                capture_path_text = record.get("capture_path")
                capture_path = Path(str(capture_path_text)) if capture_path_text else tracy_capture_path
                started_at_value = parse_number(record.get("started_at"))
                if not capture_path or not capture_path.exists() or capture_path.stat().st_size <= 0:
                    summary_path = write_tracy_summary(
                        capture_dir,
                        "failed",
                        capture_path,
                        str(record.get("failure") or "Tracy capture missing or empty"),
                        started_at_value,
                        record.get("output") if isinstance(record.get("output"), str) else None,
                        attach_metadata=record,
                        summary_prefix=f"tracy_capture_{index}_summary",
                    )
                elif capture_path.stat().st_size > max_capture_size:
                    summary_path = write_tracy_summary(
                        capture_dir,
                        "failed",
                        capture_path,
                        "Tracy capture exceeded size limit",
                        started_at_value,
                        record.get("output") if isinstance(record.get("output"), str) else None,
                        attach_metadata=record,
                        summary_prefix=f"tracy_capture_{index}_summary",
                    )
                else:
                    summary_path = extract_tracy_summary(
                        capture_dir,
                        capture_path,
                        started_at=started_at_value,
                        attach_metadata=record,
                        summary_prefix=f"tracy_capture_{index}_summary",
                    )
                capture_summaries.append(read_json(summary_path) or {})
        require_rust_tracy = env.get("MATTMC_RUST_TRACY") == "true" and mode.target == "current"
        # Java jtracy and Rust tracy-client are independent Tracy clients. Mojang jtracy may
        # speak a different capture protocol than the local Tracy CLI; record that listener
        # diagnosis, but do not let a Java protocol mismatch mask a valid Rust-required trace.
        require_java_tracy = False
        if capture_summaries:
            write_tracy_collection_summary(
                capture_dir,
                capture_summaries,
                require_rust_zones=require_rust_tracy,
                require_java_zones=require_java_tracy,
                failure=tracy_failure,
                tool_output=tracy_tool_output,
            )
        else:
            if tracy_failure is None:
                tracy_failure = "Tracy capture missing, empty, or exceeded size limit"
            write_tracy_summary(capture_dir, "failed", tracy_capture_path, tracy_failure, tool_output=tracy_tool_output)
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
        repository_paths=repository_paths,
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
    if retention_policy is not None:
        try:
            artifact_retention.run_size_check(retention_policy, managed_run_root)
        except RuntimeError as quota_error:
            error = error or str(quota_error)
            artifact_retention.write_quota_failure(retention_policy.root, managed_run_root, str(quota_error))
        artifact_retention.remove_generated_temp_dirs_for_capture(retention_policy.root, capture_dir)
        artifact_retention.cleanup(retention_policy, after_run=managed_run_root)
    emit_matrix_progress(
        args,
        row_label,
        "artifact-finalized",
        f"success={str(success).lower()} artifact={output_path}",
    )
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
        subparser.add_argument("--artifact-root", type=Path, help="Managed root for generated graphics-audit artifacts.")
        subparser.add_argument("--artifact-dir", type=Path)
        subparser.add_argument("--artifact-preserve", action="store_true", help="Opt out of automatic retention cleanup for this run.")
        subparser.add_argument("--artifact-preserve-current-run", action="store_true", help="Keep this run's extracted evidence while still cleaning managed temporary game dirs.")
        subparser.add_argument("--artifact-global-limit-mb", type=int)
        subparser.add_argument("--artifact-run-limit-mb", type=int)
        subparser.add_argument("--artifact-reserve-mb", type=int)
        subparser.add_argument("--retain-successful-runs", type=int)
        subparser.add_argument("--retain-failed-runs", type=int)
        subparser.add_argument("--mode", action="append", choices=[mode.name for mode in MATRIX_MODES])
        subparser.add_argument("--workload-profile", choices=WORKLOAD_PROFILES, default="correctness")
        subparser.add_argument("--world-profile", choices=WORLD_PROFILE_NAMES, default=os.environ.get("MATTMC_GRAPHICS_WORLD_PROFILE", "migration-gate"))
        subparser.add_argument("--world", default=os.environ.get("MATTMC_CAPTURE_WORLD"))
        subparser.add_argument("--client-args", default=os.environ.get("CLIENT_ARGS", ""))
        subparser.add_argument("--jvm-arg", action="append", default=[], help="Extra JVM option appended to JAVA_TOOL_OPTIONS for launched clients.")
        subparser.add_argument(
            "--rust-gal-gui-control",
            choices=(
                "rust",
                "disabled",
                "legacy",
                "armor-disabled",
                "armor-legacy",
                "player-health-disabled",
                "player-health-legacy",
                "absorption-disabled",
                "absorption-legacy",
                "hunger-disabled",
                "hunger-legacy",
                "air-disabled",
                "air-legacy",
                "mount-health-disabled",
                "mount-health-legacy",
            ),
            default="rust",
            help="Diagnostic control for migrated Rust-GAL GUI sprites; bare disabled/legacy retain the armor-control aliases.",
        )
        subparser.add_argument("--armor-value", type=int, help="Force a deterministic player armor value for gameplay/capture controls.")
        subparser.add_argument("--player-health", type=float, help="Force deterministic player health for gameplay/capture controls.")
        subparser.add_argument("--player-max-health", type=float, help="Force deterministic player max health for gameplay/capture controls.")
        subparser.add_argument("--player-absorption", type=float, help="Force deterministic absorption hearts for gameplay/capture controls.")
        subparser.add_argument("--player-food-level", type=int, help="Force deterministic player food level for hunger-icon gameplay/capture controls.")
        subparser.add_argument("--player-food-saturation", type=float, help="Force deterministic player food saturation for hunger jitter controls.")
        subparser.add_argument("--player-food-hunger-effect", action="store_true", help="Force hunger-effect food icon variant for correctness captures.")
        subparser.add_argument("--player-food-jitter", action="store_true", help="Force low-saturation hunger icon jitter for correctness captures.")
        subparser.add_argument("--player-air-supply", type=int, help="Force deterministic player air supply for air-bubble gameplay/capture controls.")
        subparser.add_argument("--player-max-air-supply", type=int, help="Force deterministic player max air supply for air-bubble gameplay/capture controls.")
        subparser.add_argument("--player-underwater", action="store_true", help="Force air bubbles visible as if the player eye is underwater.")
        subparser.add_argument("--player-air-pop", action="store_true", help="Force air-bubble popping animation state for correctness captures.")
        subparser.add_argument("--mount-present", action="store_true", help="Force mount-health hearts visible for gameplay/capture controls.")
        subparser.add_argument("--mount-health", type=float, help="Force deterministic current mount health for mount-heart controls.")
        subparser.add_argument("--mount-max-health", type=float, help="Force deterministic max mount health for mount-heart controls.")
        subparser.add_argument("--mount-health-rows", type=int, help="Force deterministic visible mount-heart rows for layout/capture controls.")
        subparser.add_argument("--player-heart-variant", choices=("normal", "poisoned", "withered", "frozen"),
                               help="Force deterministic player heart variant for correctness captures.")
        subparser.add_argument("--player-heart-flash", action="store_true", help="Force blinking player-heart sprites for correctness captures.")
        subparser.add_argument("--player-heart-hardcore", action="store_true", help="Force hardcore player-heart sprites for correctness captures.")
        subparser.add_argument("--player-heart-regeneration", action="store_true", help="Force regeneration bounce state for correctness captures.")
        subparser.add_argument("--game-mode", choices=("survival", "creative", "adventure", "spectator"), help="Force a deterministic player game mode for gameplay/capture controls.")
        subparser.add_argument(
            "--world-outline-scenario",
            choices=("full-cube", "partial-shape", "disconnected-shape", "no-target"),
            default=os.environ.get("MATTMC_WORLD_OUTLINE_SCENARIO", ""),
            help="Force a deterministic Rust-GAL block-outline world primitive scenario for whole-frame Vulkan captures.",
        )
        subparser.add_argument(
            "--world-outline-style",
            choices=("normal", "high-contrast"),
            default=os.environ.get("MATTMC_WORLD_OUTLINE_STYLE", "normal"),
            help="Select the deterministic block-outline style.",
        )
        subparser.add_argument(
            "--world-outline-depth-policy",
            choices=("disabled", "test-write"),
            default=os.environ.get("MATTMC_WORLD_OUTLINE_DEPTH_POLICY", "test-write"),
            help="Select the semantic depth policy for deterministic block-outline captures.",
        )
        subparser.add_argument(
            "--world-outline-depth-probe",
            action="store_true",
            help="Add deterministic overlapping line probes to prove visible/occluded depth behavior.",
        )
        subparser.add_argument(
            "--world-outline-real-target",
            action="store_true",
            help="Force Minecraft's real hit-result path to target a deterministic block for Java/Rust outline visibility captures.",
        )
        subparser.add_argument(
            "--world-outline-aim-real-target",
            action="store_true",
            help="Aim the deterministic camera at a real block while leaving GameRenderer.pick() to produce the hit result.",
        )
        subparser.add_argument(
            "--block-outline-pick-diagnostics",
            action="store_true",
            help="Enable bounded normal GameRenderer.pick() diagnostics for interactive block-outline investigation.",
        )
        subparser.add_argument(
            "--world-outline-pause-parity",
            action="store_true",
            help="Capture playing, paused, and unpaused block-outline frames with the same target and camera.",
        )
        subparser.add_argument(
            "--world-outline-legacy-control",
            action="store_true",
            help="Force the Java legacy block-outline route for matched diagnostic comparison captures.",
        )
        subparser.add_argument(
            "--world-border-scenario",
            choices=("hidden", "far", "near", "all-sides", "corner"),
            default=os.environ.get("MATTMC_WORLD_BORDER_SCENARIO", ""),
            help="Force a deterministic Rust-GAL world-border scenario for whole-frame Vulkan captures.",
        )
        subparser.add_argument(
            "--world-crack-scenario",
            choices=("hidden", "no-target", "full-cube", "partial-shape", "disconnected-shape"),
            default=os.environ.get("MATTMC_WORLD_CRACK_SCENARIO", ""),
            help="Force a deterministic Rust-GAL block-breaking crack-overlay scenario.",
        )
        subparser.add_argument(
            "--world-crack-stage",
            type=int,
            default=int(os.environ.get("MATTMC_WORLD_CRACK_STAGE", "0")),
            help="Force the deterministic crack texture stage, in the vanilla 0..9 range.",
        )
        subparser.add_argument(
            "--world-crack-real-survival",
            action="store_true",
            help="Require real survival destroy-progress crack evidence instead of accepting forced crack scenarios.",
        )
        subparser.add_argument(
            "--world-crack-control",
            choices=("rust", "disabled", "legacy"),
            default=os.environ.get("MATTMC_WORLD_CRACK_CONTROL", "rust"),
            help="Select a diagnostic crack route control without changing production routing.",
        )
        subparser.add_argument(
            "--world-material-marker-scenario",
            choices=(
                "hidden",
                "barrier",
                "light-0",
                "light-1",
                "light-2",
                "light-3",
                "light-4",
                "light-5",
                "light-6",
                "light-7",
                "light-8",
                "light-9",
                "light-10",
                "light-11",
                "light-12",
                "light-13",
                "light-14",
                "light-15",
                "lights-all",
            ),
            default=os.environ.get("MATTMC_WORLD_MATERIAL_MARKER_SCENARIO", ""),
            help="Spawn deterministic vanilla BLOCK_MARKER particles for BlockMarker material-route validation.",
        )
        subparser.add_argument(
            "--world-material-terrain-particle-scenario",
            choices=(
                "hidden",
                "stone",
                "dirt",
                "oak-leaves",
                "deepslate",
                "white-wool",
                "mixed-many",
            ),
            default=os.environ.get("MATTMC_WORLD_MATERIAL_TERRAIN_PARTICLE_SCENARIO", ""),
            help="Spawn deterministic vanilla TerrainParticle block particles for material-route validation.",
        )
        subparser.add_argument(
            "--world-material-terrain-particle-real-gameplay",
            action="store_true",
            help="Drive real survival block-hit behavior to create normal TerrainParticle instances for gameplay measurement.",
        )
        subparser.add_argument(
            "--world-mesh-block-display-scenario",
            choices=(
                "hidden",
                "stone",
                "oak-leaves",
                "cutout",
                "tinted",
                "asymmetric",
                "furnace",
                "non-full-cube",
                "stairs",
            ),
            default=os.environ.get("MATTMC_WORLD_MESH_BLOCK_DISPLAY_SCENARIO", ""),
            help="Spawn deterministic vanilla BlockDisplay entities for indexed mesh route validation.",
        )
        subparser.add_argument(
            "--world-mesh-block-display-workload",
            choices=("single", "performance", "scale-one-mesh", "scale-mixed-meshes"),
            default=os.environ.get("MATTMC_WORLD_MESH_BLOCK_DISPLAY_WORKLOAD", "single"),
            help="Select the deterministic BlockDisplay workload shape for gameplay measurement.",
        )
        subparser.add_argument(
            "--world-mesh-block-display-instance-count",
            type=int,
            default=int(os.environ.get("MATTMC_WORLD_MESH_BLOCK_DISPLAY_INSTANCE_COUNT", "-1")),
            help="Override deterministic BlockDisplay instance count for scaling probes.",
        )
        subparser.add_argument(
            "--world-mesh-block-display-control",
            choices=("rust", "disabled", "legacy"),
            default=os.environ.get("MATTMC_WORLD_MESH_BLOCK_DISPLAY_CONTROL", "rust"),
            help="Select the BlockDisplay route for gameplay controls.",
        )
        subparser.add_argument(
            "--world-mesh-falling-block-scenario",
            choices=("hidden", "sand", "gravel", "concrete-powder"),
            default=os.environ.get("MATTMC_WORLD_MESH_FALLING_BLOCK_SCENARIO", ""),
            help="Spawn deterministic vanilla FallingBlock entities for moving indexed-mesh route validation.",
        )
        subparser.add_argument(
            "--world-mesh-falling-block-count",
            type=int,
            default=int(os.environ.get("MATTMC_WORLD_MESH_FALLING_BLOCK_COUNT", "-1")),
            help="Override deterministic FallingBlock entity count for moving mesh probes.",
        )
        subparser.add_argument(
            "--world-mesh-falling-block-control",
            choices=("rust", "disabled", "legacy"),
            default=os.environ.get("MATTMC_WORLD_MESH_FALLING_BLOCK_CONTROL", "rust"),
            help="Select the FallingBlock route for gameplay controls.",
        )
        subparser.add_argument(
            "--world-mesh-piston-scenario",
            choices=(
                "hidden",
                "normal-extending",
                "normal-retracting",
                "sticky-extending",
                "sticky-retracting",
                "retracting-source",
                "cutout",
            ),
            default=os.environ.get("MATTMC_WORLD_MESH_PISTON_SCENARIO", ""),
            help="Spawn deterministic vanilla moving pistons for moving indexed-mesh route validation.",
        )
        subparser.add_argument(
            "--world-mesh-piston-count",
            type=int,
            default=int(os.environ.get("MATTMC_WORLD_MESH_PISTON_COUNT", "-1")),
            help="Override deterministic moving piston count for moving mesh probes.",
        )
        subparser.add_argument(
            "--world-mesh-piston-direction",
            choices=("down", "up", "north", "south", "west", "east"),
            default=os.environ.get("MATTMC_WORLD_MESH_PISTON_DIRECTION", "north"),
            help="Select deterministic piston movement direction for moving mesh probes.",
        )
        subparser.add_argument(
            "--world-mesh-piston-progress",
            type=float,
            default=float(os.environ.get("MATTMC_WORLD_MESH_PISTON_PROGRESS", "0.5")),
            help="Freeze deterministic piston progress at a specific 0..1 interpolation value.",
        )
        subparser.add_argument(
            "--world-mesh-piston-control",
            choices=("rust", "disabled", "legacy"),
            default=os.environ.get("MATTMC_WORLD_MESH_PISTON_CONTROL", "rust"),
            help="Select the moving piston route for gameplay controls.",
        )
        subparser.add_argument(
            "--world-material-terrain-particle-control",
            choices=("rust", "disabled", "legacy"),
            default=os.environ.get("MATTMC_WORLD_MATERIAL_TERRAIN_PARTICLE_CONTROL", "rust"),
            help="Select the TerrainParticle route for real gameplay controls.",
        )
        subparser.add_argument(
            "--world-border-scroll-phase",
            default=os.environ.get("MATTMC_WORLD_BORDER_SCROLL_PHASE", ""),
            help="Freeze the deterministic world-border texture scroll phase.",
        )
        subparser.add_argument(
            "--world-background-scenario",
            choices=("auto", "hidden", "overworld-day", "overworld-night", "nether", "end", "custom"),
            default=os.environ.get("MATTMC_WORLD_BACKGROUND_SCENARIO", ""),
            help="Force a deterministic Rust-GAL world-background clear scenario for whole-frame Vulkan captures.",
        )
        subparser.add_argument(
            "--world-static-terrain-scenario",
            choices=(
                "real-world",
                "hidden",
                "interior-edit",
                "boundary-x-edit",
                "boundary-y-edit",
                "boundary-z-edit",
                "section-reentry",
                "resource-reload",
                "opaque-texture-replacement",
                "cutout-texture-replacement",
                "pack-priority-reversal",
                "missing-atlas-payload",
                "malformed-png-payload",
                "partial-texture-update",
                "model-resource-generation-change",
                "resize-cycle",
                "swapchain-recreate",
                "world-unload-reload",
                "world-different-reload",
                "view-distance-decrease",
                "view-distance-increase",
                "camera-relocation",
                "return-visited-terrain",
                "memory-cache-soak",
                "steady-state-performance",
            ),
            default=os.environ.get("MATTMC_WORLD_STATIC_TERRAIN_SCENARIO", ""),
            help="Require deterministic real Sodium static chunk-terrain evidence for Rust whole-frame Vulkan captures.",
        )
        subparser.add_argument(
            "--world-static-terrain-second-world",
            default=os.environ.get("MATTMC_WORLD_STATIC_TERRAIN_SECOND_WORLD", ""),
            help="Explicit copied-world destination name for literal static-terrain different-world lifecycle captures.",
        )
        subparser.add_argument(
            "--world-static-terrain-fault",
            choices=(
                "",
                "old-stride",
                "incorrect-vertex-stride",
                "vertex-count-exceeds-capacity",
                "out-of-range-index",
                "index-type-invalid",
                "incorrect-index-type",
                "index-alignment-invalid",
                "incorrect-index-alignment",
                "non-finite-position",
                "section-origin-offset",
                "stale-generation",
                "duplicate-visible-section",
                "cross-world-stale-submission",
                "mesh-key-collision",
                "bounds-out-of-range",
                "inverted-normal",
                "swapped-block-sky-light",
                "inverted-ao",
                "doubled-face-shade",
                "wrong-top-face-shade",
                "old-generation-after-edit",
                "old-new-together",
                "removed-section-resubmitted",
                "wrong-neighbor-invalidated",
                "replacement-previous-origin",
                "terrain-generation-never-quiesced",
                "identical-mesh-reregistered",
                "steady-state-upload-detected",
                "visibility-fingerprint-unstable",
                "rebuild-loop-detected",
                "cache-eviction-loop",
                "atlas-generation-churn",
            ),
            default=os.environ.get("MATTMC_WORLD_STATIC_TERRAIN_FAULT", ""),
            help="Diagnostic-only static-terrain geometry fault injection for harness anvil tests.",
        )
        subparser.add_argument(
            "--gui-resource-pack-scenario",
            choices=("vanilla", "pack-a", "pack-b", "priority-a-b", "priority-b-a", "missing", "malformed", "unsupported"),
            default=os.environ.get("MATTMC_GUI_RESOURCE_PACK_SCENARIO", ""),
            help="Generate/select diagnostic GUI resource-pack scenarios in the isolated game dir.",
        )
        subparser.add_argument(
            "--world-static-terrain-resource-pack-scenario",
            choices=("vanilla", "pack-a", "pack-b", "priority-a-b", "priority-b-a", "missing", "malformed", "unsupported"),
            default=os.environ.get("MATTMC_WORLD_STATIC_TERRAIN_RESOURCE_PACK_SCENARIO", ""),
            help="Generate/select diagnostic block-texture resource packs for Rust static-terrain atlas coverage.",
        )
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
        subparser.add_argument(
            "--shader-graph-isolation",
            choices=("terrain-only", "gbuffer-no-shadow", "terrain-plus-gbuffer-no-shadow", "terrain-plus-shadow", "full-draws-skipped"),
            default="",
            help="Diagnostic-only Rust shader graph isolation for whole-frame profiling rows.",
        )
        subparser.add_argument(
            "--shader-gbuffer-scene",
            action="store_true",
            help="Run the Rust-owned shader-pack/G-buffer validation scene as an isolated subsystem workload.",
        )
        subparser.add_argument("--warmup-frames", type=int)
        subparser.add_argument("--measure-frames", type=int)
        subparser.add_argument("--settle-frames", type=int)
        subparser.add_argument("--max-settle-frames", type=int)
        subparser.add_argument("--positive-control-delay-ms", type=int, default=0)
        subparser.add_argument("--subsystem-iterations", type=int)
        subparser.add_argument("--repetitions", type=int, default=1)
        subparser.add_argument("--repeatability-tolerance", type=float, default=0.25)
        subparser.add_argument("--diagnostic", action="store_true", help="Enable gated graphics audit diagnostics.")
        subparser.add_argument("--dry-run", action="store_true")

    for tool in TOOL_KINDS:
        add_common(subparsers.add_parser(tool))
    args = parser.parse_args(incoming)
    args._provided_options = provided_options
    if not args.world:
        args.world = WORLD_PROFILES[args.world_profile].world
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
    if args.tool == "gameplay" and args.world_profile == "migration-gate":
        if "--settle-frames" not in provided_options:
            args.settle_frames = 0
        if "--max-settle-frames" not in provided_options:
            args.max_settle_frames = min(args.max_settle_frames, 120)
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
    for field in ("artifact_global_limit_mb", "artifact_run_limit_mb", "artifact_reserve_mb"):
        value = getattr(args, field)
        if value is not None and value < 0:
            raise SystemExit(f"{field.replace('_', '-')} must be non-negative")
    for field in ("retain_successful_runs", "retain_failed_runs"):
        value = getattr(args, field)
        if value is not None and value < 0:
            raise SystemExit(f"{field.replace('_', '-')} must be non-negative")
    run_type_for_args(args)
    if args.renderdoc_capture and args.tool not in {"capture", "matrix"} and not (
        args.tool == "subsystem" and args.shader_gbuffer_scene
    ):
        raise SystemExit("RenderDoc capture is a correctness/audit mode; use DevUtils/Audit/Capture.py or Matrix.py")
    if args.tracy_duration_seconds <= 0 or args.tracy_max_size_mb <= 0:
        raise SystemExit("Tracy duration and max size must be positive")
    if args.warmup_frames < 0 or args.measure_frames <= 0:
        raise SystemExit("warmup frames must be non-negative and measure frames must be positive")
    if args.settle_frames < 0 or args.max_settle_frames <= 0 or args.subsystem_iterations <= 0 or args.repetitions <= 0:
        raise SystemExit("settle/max-settle/subsystem iterations/repetitions must be positive where applicable")
    if not 0 <= args.world_crack_stage <= 9:
        raise SystemExit("--world-crack-stage must be in 0..9")
    if args.armor_value is not None and not 0 <= args.armor_value <= 20:
        raise SystemExit("--armor-value must be in 0..20")
    if args.player_health is not None and args.player_health < 0:
        raise SystemExit("--player-health must be non-negative")
    if args.player_max_health is not None and args.player_max_health <= 0:
        raise SystemExit("--player-max-health must be positive")
    if args.player_absorption is not None and args.player_absorption < 0:
        raise SystemExit("--player-absorption must be non-negative")
    if args.player_air_supply is not None and args.player_air_supply < 0:
        raise SystemExit("--player-air-supply must be non-negative")
    if args.player_max_air_supply is not None and args.player_max_air_supply <= 0:
        raise SystemExit("--player-max-air-supply must be positive")
    if args.mount_health is not None and args.mount_health < 0:
        raise SystemExit("--mount-health must be non-negative")
    if args.mount_max_health is not None and args.mount_max_health <= 0:
        raise SystemExit("--mount-max-health must be positive")
    if args.mount_health_rows is not None and not 0 <= args.mount_health_rows <= 3:
        raise SystemExit("--mount-health-rows must be in 0..3")
    return args


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    targets = select_targets(args)
    retention_root = (
        args.artifact_dir.resolve()
        if args.artifact_dir
        else (args.artifact_root.resolve() if args.artifact_root else artifact_retention.default_artifact_base(repo_root()))
    )
    retention_policy = artifact_retention.policy_for(
        args.profile,
        retention_root,
        diagnostic=args.diagnostic or args.renderdoc_capture or args.tracy_capture,
        preserve=args.artifact_preserve,
        global_limit_mb=args.artifact_global_limit_mb,
        run_limit_mb=args.artifact_run_limit_mb,
        reserve_mb=args.artifact_reserve_mb,
        keep_success=args.retain_successful_runs,
        keep_failed=args.retain_failed_runs,
    )
    artifact_retention.ensure_marker(retention_policy.root)
    if matrix_progress_enabled(args):
        emit_matrix_progress(args, "matrix", "preflight-started", f"artifact_root={retention_policy.root}")
    if args.dry_run:
        preflight_cleanup = {"removed": [], "removed_game_dirs": [], "dry_run": True}
        disk_preflight = {
            "artifact_root": str(retention_policy.root),
            "dry_run": True,
            "artifact_root_usage_bytes": artifact_retention.directory_size(retention_policy.root),
        }
    else:
        preflight_cleanup = artifact_retention.cleanup(retention_policy) if not args.artifact_preserve else {"removed": [], "removed_game_dirs": []}
        disk_preflight = artifact_retention.preflight_disk_budget(
            retention_policy,
            artifact_retention.estimated_run_bytes(
                args.profile,
                diagnostic=args.diagnostic,
                renderdoc=args.renderdoc_capture,
                tracy=args.tracy_capture,
            ),
        )
    if matrix_progress_enabled(args):
        removed_tmp = preflight_cleanup.get("removed_stale_tmp") if isinstance(preflight_cleanup, dict) else None
        detail = f"root_usage={disk_preflight.get('artifact_root_usage_bytes', 'unknown')} removed_stale_tmp={len(removed_tmp) if isinstance(removed_tmp, list) else 0}"
        emit_matrix_progress(args, "matrix", "preflight-completed", detail)
    args._retention_policy = retention_policy
    artifact_root = args.artifact_dir.resolve() if args.artifact_dir else retention_policy.root / args.tool / timestamp()
    artifact_root.mkdir(parents=True, exist_ok=True)
    if args.artifact_preserve_current_run and not args.dry_run:
        (artifact_root / ".preserve").write_text("current validation evidence\n", encoding="utf-8")
    results: list[MatrixResult] = []
    tools_to_run = ("gameplay", "capture", "subsystem") if args.tool == "matrix" else (args.tool,)
    for mode in selected_modes(args):
        for tool_kind in tools_to_run:
            for repetition in range(1, args.repetitions + 1):
                if matrix_progress_enabled(args):
                    emit_matrix_progress(
                        args,
                        matrix_row_label(mode, tool_kind, repetition),
                        "mode-selected",
                        f"backend={mode.backend} shaders={mode.shaders}",
                    )
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
        "repository_resolution": repository_resolution(targets["current"].root, targets["frozen"].root, args.frozen_repo),
        "runtime_profile": runtime_profile_dict(args),
        "workload_profile": args.workload_profile,
        "tool": args.tool,
        "invoked_tools": list(tools_to_run),
        "diagnostic": args.diagnostic,
        "artifact_retention": {
            "policy": {
                "profile": retention_policy.profile,
                "root": str(retention_policy.root),
                "global_limit_bytes": retention_policy.global_limit_bytes,
                "run_limit_bytes": retention_policy.run_limit_bytes,
                "reserve_bytes": retention_policy.reserve_bytes,
                "keep_success": retention_policy.keep_success,
                "keep_failed": retention_policy.keep_failed,
                "heavy_keep": retention_policy.heavy_keep,
                "preserve": retention_policy.preserve,
            },
            "preflight": disk_preflight,
            "pre_run_cleanup": preflight_cleanup,
        },
        "repeatability": repeatability,
        "success": all(result.success for result in results) and repeatability["passed"],
        "aggregate": str(aggregate_path) if aggregate else None,
        "results": [asdict(result) for result in results],
    }
    manifest_path = artifact_root / MANIFEST_NAME
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    post_cleanup = (
        {"removed": [], "removed_game_dirs": [], "dry_run": True}
        if args.dry_run
        else (artifact_retention.cleanup(retention_policy, after_run=artifact_root) if not args.artifact_preserve else {"removed": [], "removed_game_dirs": []})
    )
    manifest["artifact_retention"]["post_run_cleanup"] = post_cleanup
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
