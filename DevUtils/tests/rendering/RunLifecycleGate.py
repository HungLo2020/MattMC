#!/usr/bin/env python3
"""Run the renderer lifecycle gate: each transition scenario on the current
Rust/Vulkan client with Iris + Distant Horizons, failing on any client
exception, Rust panic or GAL dependency violation.

These transitions (save and quit, reload, resize, view distance) cross the
pipelined Java/Rust frame boundary, where completed-frame receipts and
runtime-owned resources outlive the state that produced them. Run it with
every rendering batch, next to Frozen parity.

Run from the repository root with a relative script path (the capture harness
stops processes whose command line names the repository):

    python3 DevUtils/tests/rendering/RunLifecycleGate.py --label gate1

Inputs come from the usual capture environment variables
(MATTMC_CAPTURE_RUN_SOURCE, MATTMC_CAPTURE_SHADER_PACK_SOURCE) or the matching
options. The DH radius stays the world's own unless --dh-radius is given.
"""
from __future__ import annotations

import argparse
import gzip
import json
import os
import re
import subprocess
import sys
from pathlib import Path

from runtime_log_health import separate_shutdown_disconnects

REPO = Path(__file__).resolve().parents[3]

SCENARIOS = (
    "world-unload-reload",
    "world-different-reload",
    "resource-reload",
    "resize-cycle",
    "swapchain-recreate",
    "view-distance-decrease",
    "view-distance-increase",
)

# Client-log patterns that fail a scenario even when the audit passes. The GAL
# logs every validation failure (rate-limited), including teardown errors
# that callers discard, so a still-bound resource fails the gate here.
FAILURE_PATTERNS = {
    "exception": re.compile(r"Exception"),
    "panic": re.compile(r"panicked at"),
    "dependency-violation": re.compile(r"DependencyViolation"),
}
# Reported, not failing: some validation rejections are expected probes.
REPORT_PATTERNS = {
    "gal-validation": re.compile(r"rust_gal_validation_failure"),
}


def capture_command(artifact_root: Path, scenario: str, shaders: bool, dh: bool,
                    jvm_args: list[str]) -> list[str]:
    command = [
        sys.executable, "DevUtils/Audit/Capture.py", "--profile", "extended",
        "--mode", "current-rust-vulkan-shaders-on" if shaders else "current-rust-vulkan-shaders-off",
        "--repo-root", str(REPO),
        "--frozen-repo", str(REPO.parent / "MattMC_JavaPerfTesting" / "MattMC"),
        "--artifact-root", str(artifact_root), "--artifact-preserve-current-run",
        "--capture-camera-pose", "150.5,125,530.5,105,15", "--rust-profile", "release",
        "--workload-profile", "settled-static", "--rust-selected-source-execution",
        "--validation", "standard", "--diagnostic", "--rust-full-gameplay-attachments",
        "--world-static-terrain-scenario", scenario,
    ]
    command += [f"--jvm-arg={arg}" for arg in jvm_args]
    if dh:
        command += [
            "--world-distant-horizons-real-world", "--world-distant-horizons-water",
            "--dh-composition-mode", "DOUBLE_PASS", "--world-distant-horizons-opaque",
        ]
    return command


def scan_logs(artifact_root: Path) -> dict[str, int]:
    patterns = {**FAILURE_PATTERNS, **REPORT_PATTERNS}
    counts = {name: 0 for name in patterns}
    counts["shutdown-disconnects"] = 0
    # Large client logs are kept gzipped; scan both forms.
    for log in artifact_root.glob("**/current-rust-*/capture/run-01/capture/runClient_*.log*"):
        if log.suffix == ".gz":
            text = gzip.decompress(log.read_bytes()).decode(errors="replace")
        elif log.suffix == ".log":
            text = log.read_text(errors="replace")
        else:
            continue
        text, shutdown_disconnects = separate_shutdown_disconnects(text)
        counts["shutdown-disconnects"] += shutdown_disconnects
        for name, pattern in patterns.items():
            counts[name] += len(pattern.findall(text))
    return counts


def audit_status(artifact_root: Path) -> str:
    matrices = sorted(artifact_root.glob("**/graphics_audit_matrix.json"))
    if not matrices:
        return "no-matrix"
    rows = json.loads(matrices[-1].read_text()).get("rows") or []
    if not rows:
        return "no-rows"
    return "ok" if all(row.get("crash_free") is True for row in rows) else "crashed"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--label", required=True, help="artifact directory name")
    parser.add_argument("--scenario", action="append", choices=SCENARIOS,
                        help="run only these scenarios (repeatable); default all")
    parser.add_argument("--no-shaders", action="store_true", help="vanilla instead of the Iris pack")
    parser.add_argument("--no-dh", action="store_true", help="without Distant Horizons")
    parser.add_argument("--jvm-arg", action="append", default=[],
                        help="extra client JVM option (repeatable), e.g. -Dmattmc.dev.forceTerrainVertexStaging=true")
    parser.add_argument("--run-source", help="MATTMC_CAPTURE_RUN_SOURCE override")
    parser.add_argument("--shader-pack", help="MATTMC_CAPTURE_SHADER_PACK_SOURCE override")
    parser.add_argument("--artifact-root", type=Path,
                        help="output directory (default artifacts/graphics-captures/lifecycle-gate/<label>)")
    parser.add_argument("--dh-radius", default=None,
                        help="MATTMC_CAPTURE_DH_RADIUS_OVERRIDE (default: the world's own radius; the "
                             "resize/swapchain DH residency checks assume it)")
    args = parser.parse_args()

    env = dict(os.environ)
    if args.run_source:
        env["MATTMC_CAPTURE_RUN_SOURCE"] = args.run_source
    if args.shader_pack:
        env["MATTMC_CAPTURE_SHADER_PACK_SOURCE"] = args.shader_pack
    if args.dh_radius:
        env["MATTMC_CAPTURE_DH_RADIUS_OVERRIDE"] = args.dh_radius
    else:
        env.pop("MATTMC_CAPTURE_DH_RADIUS_OVERRIDE", None)
    env.setdefault("MATTMC_CAPTURE_MAX_FPS", "260")

    out = args.artifact_root or REPO / "artifacts" / "graphics-captures" / "lifecycle-gate" / args.label
    results = []
    for scenario in args.scenario or SCENARIOS:
        root = out / scenario
        root.mkdir(parents=True, exist_ok=True)
        with (root / "driver.log").open("w") as log:
            code = subprocess.call(capture_command(root / "run", scenario, not args.no_shaders, not args.no_dh, args.jvm_arg),
                                   cwd=REPO, env=env, stdout=log, stderr=subprocess.STDOUT)
        counts = scan_logs(root)
        status = audit_status(root)
        passed = code == 0 and status == "ok" and not any(counts[name] for name in FAILURE_PATTERNS)
        results.append({"scenario": scenario, "exit": code, "audit": status, **counts, "passed": passed})
        print(f"{scenario:24} {'PASS' if passed else 'FAIL'} exit={code} audit={status} "
              + " ".join(f"{name}={count}" for name, count in counts.items()), flush=True)
    (out / "summary.json").write_text(json.dumps(results, indent=2) + "\n")
    return 0 if all(result["passed"] for result in results) else 1


if __name__ == "__main__":
    sys.exit(main())
