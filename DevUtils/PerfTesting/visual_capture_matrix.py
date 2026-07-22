#!/usr/bin/env python3
"""Build a comparable OpenGL/Vulkan deterministic visual-performance matrix."""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any


COMMON_FINGERPRINT_FIELDS = (
    "schemaVersion",
    "resolution",
    "world",
    "dimension",
    "distantHorizonsActive",
    "cameraPath",
    "position",
    "yaw",
    "pitch",
    "yawDelta",
    "poseCount",
    "framesPerPose",
    "warmupFrames",
    "measureFrames",
    "settledReadyFrames",
    "settledReadyMaxWaitFrames",
    "settledReadyFamilies",
    "jvm",
    "harness",
    "profilerFlags",
)


@dataclass(frozen=True)
class MatrixRowSpec:
    repository: str
    backend: str
    shaders: str
    artifact: Path | None
    missing_reason: str = ""


def parse_row(value: str) -> MatrixRowSpec:
    parts = value.split(",", 3)
    if len(parts) != 4:
        raise argparse.ArgumentTypeError("row must be repository,backend,shaders,artifact")
    repository, backend, shaders, artifact = (part.strip() for part in parts)
    validate_mode(repository, backend, shaders)
    return MatrixRowSpec(repository, backend, shaders, Path(artifact))


def parse_missing(value: str) -> MatrixRowSpec:
    parts = value.split(",", 3)
    if len(parts) != 4:
        raise argparse.ArgumentTypeError("missing row must be repository,backend,shaders,reason")
    repository, backend, shaders, reason = (part.strip() for part in parts)
    validate_mode(repository, backend, shaders)
    if not reason:
        raise argparse.ArgumentTypeError("missing row reason must not be blank")
    return MatrixRowSpec(repository, backend, shaders, None, reason)


def validate_mode(repository: str, backend: str, shaders: str) -> None:
    if repository not in {"current", "frozen-java"}:
        raise argparse.ArgumentTypeError("repository must be current or frozen-java")
    if backend not in {"opengl", "vulkan"}:
        raise argparse.ArgumentTypeError("backend must be opengl or vulkan")
    if shaders not in {"off", "on"}:
        raise argparse.ArgumentTypeError("shaders must be off or on")


def metadata_path_for(artifact: Path) -> Path:
    if artifact.is_file():
        return artifact
    candidates = sorted(artifact.glob("deterministic_camera_capture_*.json"))
    if not candidates:
        raise RuntimeError(f"artifact has no deterministic metadata: {artifact}")
    return candidates[-1]


def audit_path_for(artifact: Path) -> Path:
    if artifact.is_file():
        root = artifact.parent
    else:
        root = artifact
    candidates = sorted(root.glob("performance_audit_*/vulkan-perf-audit-*.txt"))
    if not candidates:
        candidates = sorted(root.glob("**/vulkan-perf-audit-*.txt"))
    if not candidates:
        raise RuntimeError(f"artifact has no performance audit report: {artifact}")
    return candidates[-1]


def parse_audit(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def load_row(spec: MatrixRowSpec) -> dict[str, Any]:
    if spec.artifact is None:
        return {
            "repository": spec.repository,
            "backend": spec.backend,
            "shaders": spec.shaders,
            "status": "missing",
            "reason": spec.missing_reason,
        }
    metadata_path = metadata_path_for(spec.artifact)
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    audit_path = audit_path_for(spec.artifact)
    audit = parse_audit(audit_path)
    fingerprint = metadata.get("benchmarkFingerprint")
    if not isinstance(fingerprint, dict):
        raise RuntimeError(f"metadata is missing benchmarkFingerprint: {metadata_path}")
    expected_repo = fingerprint.get("repositoryIdentity")
    expected_backend = fingerprint.get("backend")
    expected_shaders = normalize_shader_state(fingerprint.get("shaderEnabled"))
    if expected_repo != spec.repository or expected_backend != spec.backend or expected_shaders != spec.shaders:
        raise RuntimeError(
            "row identity mismatch for "
            f"{metadata_path}: expected {spec.repository}/{spec.backend}/{spec.shaders}, "
            f"found {expected_repo}/{expected_backend}/{expected_shaders}"
        )
    return {
        "repository": spec.repository,
        "backend": spec.backend,
        "shaders": spec.shaders,
        "status": "complete",
        "artifact": str(spec.artifact),
        "metadata": str(metadata_path),
        "audit": str(audit_path),
        "commit": metadata.get("gitCommit", fingerprint.get("repositoryCommit", "unknown")),
        "captureDate": audit.get("timestamp_utc", "unknown"),
        "fingerprintHash": metadata.get("benchmarkFingerprintHash", ""),
        "fingerprint": fingerprint,
        "metrics": {
            "medianMs": float_value(audit, "deterministic_measured_frame_median_ms"),
            "p95Ms": float_value(audit, "deterministic_measured_frame_p95_ms"),
            "p99Ms": float_value(audit, "deterministic_measured_frame_p99_ms"),
            "worstMs": float_value(audit, "deterministic_measured_frame_worst_ms"),
            "totalMs": float_value(audit, "deterministic_measured_frame_total_ms"),
            "draws": int_value(audit, "graphics_draw_count"),
            "dispatches": int_value(audit, "compute_dispatch_count"),
            "galV2Draws": int_value(audit, "gal_v2_graphics_draw_count"),
            "galFallbackDraws": int_value(audit, "gal_v2_legacy_fallback_draw_count"),
            "backendGraphicsMs": phase_value(audit, spec.backend),
            "captureMs": first_float_value(
                audit,
                "phase_gal.graphics.capture_ms",
                "phase.gal.graphics.capture.total_ms",
            ),
            "loweringMs": lowering_value(audit),
            "gcMs": int_value(audit, "java_gc_collection_time_ms"),
            "rssKb": int_value(audit, "process_rss_kb"),
            "submissions": int_value(audit, "vulkan_submit_count"),
            "descriptorPlans": int_value(audit, "descriptor_plan_count"),
            "descriptorSetAllocs": int_value(audit, "descriptor_set_allocation_count"),
            "openglStateChanges": int_value(audit, "opengl_state_change_count"),
        },
    }


def normalize_shader_state(value: Any) -> str:
    normalized = str(value).strip().lower()
    if normalized == "true":
        return "on"
    if normalized == "false":
        return "off"
    return normalized


def float_value(audit: dict[str, str], key: str) -> float | None:
    value = audit.get(key)
    if value is None:
        return None
    try:
        return float(value)
    except ValueError:
        return None


def first_float_value(audit: dict[str, str], *keys: str) -> float | None:
    for key in keys:
        value = float_value(audit, key)
        if value is not None:
            return value
    return None


def int_value(audit: dict[str, str], key: str) -> int | None:
    value = audit.get(key)
    if value is None:
        return None
    match = re.match(r"^-?[0-9]+", value)
    if not match:
        return None
    return int(match.group(0))


def phase_value(audit: dict[str, str], backend: str) -> float | None:
    candidates = [
        f"phase_backend.{backend}.graphics.v2_ms",
        f"phase_backend.{backend}.graphics_ms",
        f"backend.{backend}.graphics.v2_ms",
        f"backend.{backend}.graphics_ms",
        f"phase.backend.{backend}.graphics.v2.total_ms",
        f"phase.backend.{backend}.graphics.total_ms",
    ]
    for key in candidates:
        value = float_value(audit, key)
        if value is not None:
            return value
    return None


def lowering_value(audit: dict[str, str]) -> float | None:
    total = 0.0
    found = False
    for key, value in audit.items():
        if key.startswith("legacy_graphics_lowering_step_") and key.endswith("_total_ms"):
            try:
                total += float(value)
                found = True
            except ValueError:
                pass
    if found:
        return total
    return float_value(audit, "phase_backend.vulkan.graphics.v2_ms")


def validate_matrix(rows: list[dict[str, Any]]) -> None:
    complete = [row for row in rows if row.get("status") == "complete"]
    if not complete:
        raise RuntimeError("matrix contains no complete rows")
    reference = complete[0]["fingerprint"]
    mismatches: list[str] = []
    for row in complete[1:]:
        fingerprint = row["fingerprint"]
        for field in COMMON_FINGERPRINT_FIELDS:
            if fingerprint.get(field) != reference.get(field):
                mismatches.append(
                    f"{row['repository']}/{row['backend']}/{row['shaders']} field {field}: "
                    f"{fingerprint.get(field)!r} != {reference.get(field)!r}"
                )
        if row["shaders"] == "on" and fingerprint.get("shaderPack") != reference.get("shaderPack"):
            mismatches.append(
                f"{row['repository']}/{row['backend']}/{row['shaders']} shaderPack: "
                f"{fingerprint.get('shaderPack')!r} != {reference.get('shaderPack')!r}"
            )
    if mismatches:
        raise RuntimeError("benchmark fingerprints are not comparable:\n" + "\n".join(mismatches))


def markdown_table(rows: list[dict[str, Any]]) -> str:
    headers = [
        "repo",
        "backend",
        "shaders",
        "status",
        "median",
        "p95",
        "p99",
        "worst",
        "total",
        "draws",
        "v2",
        "fallback",
        "backend gfx",
        "capture",
        "lowering",
        "gc ms",
        "rss kb",
        "artifact",
        "fingerprint",
    ]
    lines = ["| " + " | ".join(headers) + " |", "| " + " | ".join(["---"] * len(headers)) + " |"]
    for row in rows:
        if row.get("status") != "complete":
            values = [
                row["repository"],
                row["backend"],
                row["shaders"],
                "missing: " + row["reason"],
                *[""] * 14,
                "",
            ]
        else:
            metrics = row["metrics"]
            values = [
                row["repository"],
                row["backend"],
                row["shaders"],
                "complete",
                format_metric(metrics["medianMs"]),
                format_metric(metrics["p95Ms"]),
                format_metric(metrics["p99Ms"]),
                format_metric(metrics["worstMs"]),
                format_metric(metrics["totalMs"]),
                format_metric(metrics["draws"]),
                format_metric(metrics["galV2Draws"]),
                format_metric(metrics["galFallbackDraws"]),
                format_metric(metrics["backendGraphicsMs"]),
                format_metric(metrics["captureMs"]),
                format_metric(metrics["loweringMs"]),
                format_metric(metrics["gcMs"]),
                format_metric(metrics["rssKb"]),
                row["artifact"],
                row["fingerprintHash"],
            ]
        lines.append("| " + " | ".join(escape_markdown(str(value)) for value in values) + " |")
    return "\n".join(lines) + "\n"


def format_metric(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, float):
        return f"{value:.4f}"
    return str(value)


def escape_markdown(value: str) -> str:
    return value.replace("|", "\\|")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--row", action="append", default=[], type=parse_row)
    parser.add_argument("--missing", action="append", default=[], type=parse_missing)
    parser.add_argument("--output-json", type=Path, required=True)
    parser.add_argument("--output-md", type=Path, required=True)
    args = parser.parse_args()

    rows = [load_row(spec) for spec in [*args.row, *args.missing]]
    expected = {(repo, backend, shaders) for repo in ("current", "frozen-java") for backend in ("opengl", "vulkan") for shaders in ("off", "on")}
    actual = {(row["repository"], row["backend"], row["shaders"]) for row in rows}
    missing_specs = sorted(expected - actual)
    if missing_specs:
        raise RuntimeError("matrix is missing rows: " + ", ".join("/".join(item) for item in missing_specs))
    validate_matrix(rows)
    output = {
        "schema": "mattmc-visual-performance-matrix-v1",
        "rows": rows,
    }
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_md.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(json.dumps(output, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    args.output_md.write_text(markdown_table(rows), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
