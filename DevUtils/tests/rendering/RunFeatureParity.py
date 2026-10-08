#!/usr/bin/env python3
"""Frozen parity for feature fixtures the coast poses never show: block
entities, held items and first-person hands, enchanted equipment.

    python3 DevUtils/tests/rendering/RunFeatureParity.py --label <label> [--scenario NAME ...]
        [--repo-root <checkout>]

Each scenario is one Capture.py Frozen/current pair on the harness's
deterministic fixtures (`--world-mesh-model-scenario`, `--hotbar-item-fixture`).
Every requested fixture must pass its Frozen comparison. Optional
`--compare <baseline label>` also diagnoses regressions from a prior run;
an unchanged historical failure still fails absolute parity.
Results: artifacts/graphics-captures/feature-parity/<label>/summary.json.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO / "DevUtils" / "Common"))
from artifact_retention import ensure_marker, retire_completed_fixtures, retire_old_invocations
from RunValidation import leftover_clients, stop_leftover_clients
FROZEN_REPO = REPO.parent / "MattMC_JavaPerfTesting" / "MattMC"
SHADER_PACK = REPO / "run" / "shaderpacks" / "ComplementaryHungLoIfied.zip"

# name: (shaders, extra Capture.py arguments)
SCENARIOS: dict[str, tuple[bool, list[str]]] = {
    "chest": (False, ["--world-mesh-model-scenario", "chest"]),
    "chest-shaders": (True, ["--world-mesh-model-scenario", "chest"]),
    "bed": (False, ["--world-mesh-model-scenario", "bed"]),
    "oak-sign": (False, ["--world-mesh-model-scenario", "oak-sign"]),
    "banner": (False, ["--world-mesh-model-scenario", "white-banner-red-cross"]),
    "zombie-armor-foil": (False, ["--world-mesh-model-scenario", "zombie",
                                  "--jvm-arg=-Dmattmc.dev.graphicsAuditEquipment=foil"]),
    "held-trident-foil": (False, ["--hotbar-item-fixture", "model-foil"]),
    "held-shield": (False, ["--hotbar-item-fixture", "shield"]),
    "held-shield-foil": (False, ["--hotbar-item-fixture", "shield-foil"]),
    "empty-hand": (False, ["--hotbar-item-fixture", "standard-3d", "--empty-selected-hand"]),
}

# Manifest sub-reports worth comparing; absent ones are skipped.
REPORTS = ("cross_repository_visual_parity", "cross_repository_model_crop_parity",
           "model_item_foil_parity", "equipment_parity")


def status_of(report) -> str | None:
    if isinstance(report, dict):
        for key in ("status", "passed", "success"):
            if key in report:
                return str(report[key])
        return "present"
    return None if report is None else str(report)


def summarize(artifact_dir: Path, exit_code: int) -> dict:
    manifest_path = artifact_dir / "graphics_audit_manifest.json"
    result: dict = {"exit": exit_code}
    if not manifest_path.exists():
        return {**result, "success": False, "error": "no manifest"}
    manifest = json.loads(manifest_path.read_text())
    result["success"] = manifest.get("success")
    result["reports"] = {name: status_of(manifest.get(name)) for name in REPORTS if manifest.get(name) is not None}
    visual = artifact_dir / "paired_visual_static_terrain" / "visual_parity_report.json"
    if visual.exists():
        pair = (json.loads(visual.read_text()).get("pairs") or [{}])[0]
        result["mean_rgb"] = [round(x, 3) for x in (pair.get("diff") or {}).get("mean_rgb_abs", [])]
        result["images"] = sorted(str(p) for p in visual.parent.glob("pair-*/side_by_side_*.png"))
    return result


def compare(current: dict, baseline: dict) -> list[str]:
    """Regressions: a report or success that passed on the baseline but not now,
    or a visual mean RGB more than 1.0 worse in any channel."""
    problems = []
    requested = set(current.get("requested_scenarios", baseline["scenarios"]))
    problems.extend(f"{name}: baseline missing requested scenario" for name in sorted(requested - baseline["scenarios"].keys()))
    for name, base in baseline["scenarios"].items():
        if "requested_scenarios" in current and name not in current["requested_scenarios"]:
            continue
        now = current["scenarios"].get(name)
        if now is None:
            problems.append(f"{name}: required baseline scenario missing")
            continue
        if base.get("success") and not now.get("success"):
            problems.append(f"{name}: success {base.get('success')} -> {now.get('success')}")
        for report, base_status in (base.get("reports") or {}).items():
            now_status = (now.get("reports") or {}).get(report)
            if now_status is None or (base_status in ("True", "complete", "passed")
                                      and now_status not in ("True", "complete", "passed")):
                problems.append(f"{name}: {report} {base_status} -> {now_status}")
        if base.get("mean_rgb") and not valid_rgb(now.get("mean_rgb")):
            problems.append(f"{name}: visual measurements missing or invalid")
        if valid_rgb(base.get("mean_rgb")) and valid_rgb(now.get("mean_rgb")):
            for channel, (b, n) in enumerate(zip(base["mean_rgb"], now["mean_rgb"])):
                if n > b + 1.0:
                    problems.append(f"{name}: mean RGB channel {channel} {b} -> {n}")
    return problems


def valid_rgb(values) -> bool:
    return (isinstance(values, list) and len(values) == 3
            and all(type(value) in (int, float) and math.isfinite(value) and value >= 0
                    for value in values))


def passed(summary: dict) -> bool:
    """A historical non-regression comparison does not replace Frozen parity."""
    scenarios = summary.get("scenarios") or {}
    return (bool(scenarios) and not summary.get("regressions")
            and set(summary.get("requested_scenarios", scenarios)) == set(scenarios)
            and all(type(row.get("exit")) is int and row["exit"] == 0 and row.get("success") is True
                    and valid_rgb(row.get("mean_rgb"))
                    and row.get("reports", {}).get("cross_repository_visual_parity")
                    in ("True", "complete", "passed") for row in scenarios.values()))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--label", required=True)
    parser.add_argument("--scenario", action="append", choices=sorted(SCENARIOS))
    parser.add_argument("--repo-root", type=Path, default=REPO, help="checkout to capture (default this one)")
    parser.add_argument("--compare", help="baseline label to compare against")
    parser.add_argument("--run-source", type=Path, default=Path(os.environ.get("MATTMC_CAPTURE_RUN_SOURCE", REPO / "run")))
    parser.add_argument("--shader-pack", type=Path, default=Path(os.environ.get("MATTMC_CAPTURE_SHADER_PACK_SOURCE", SHADER_PACK)))
    parser.add_argument("--frozen-repo", type=Path, default=FROZEN_REPO)
    parser.add_argument("--jvm-arg", action="append", default=[], help="extra client JVM option (current side)")
    args = parser.parse_args()
    if any(Path(label).name != label or label in (".", "..") for label in (args.label, args.compare) if label is not None):
        parser.error("labels must be single directory names")
    args.repo_root = args.repo_root.resolve()
    args.run_source = args.run_source.resolve()
    args.shader_pack = args.shader_pack.resolve()
    args.frozen_repo = args.frozen_repo.resolve()
    missing = [str(path) for path in (args.repo_root, args.run_source, args.shader_pack, args.frozen_repo) if not path.exists()]
    if missing:
        parser.error("missing inputs: " + ", ".join(missing))
    if leftover_clients():
        parser.error("a game client is already running; stop it before verification")

    out = REPO / "artifacts" / "graphics-captures" / "feature-parity" / args.label
    if out.exists():
        parser.error(f"{out} already exists; pick a new label")
    out.mkdir(parents=True)
    ensure_marker(out)
    env = dict(os.environ)
    # The same release profile as RunValidation.py, so neither rebuilds the
    # other's native library inside a client's readiness window.
    env["CARGO_PROFILE_RELEASE_STRIP"] = "none"
    env["CARGO_PROFILE_RELEASE_DEBUG"] = "line-tables-only"
    env["MATTMC_CAPTURE_RUN_SOURCE"] = str(args.run_source)
    env["MATTMC_CAPTURE_SHADER_PACK_SOURCE"] = str(args.shader_pack)
    summary = {"schema": "mattmc-feature-parity-v2", "label": args.label, "repo_root": str(args.repo_root), "head": subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=args.repo_root, text=True, capture_output=True).stdout.strip(),
        "requested_scenarios": list(dict.fromkeys(args.scenario or SCENARIOS)), "scenarios": {}}
    # Build first, so no client's readiness window pays for a cold build.
    with (out / "build.log").open("w") as log:
        built = subprocess.call(["./gradlew", "-PmattmcRustProfile=release", "buildRustNative", "classes",
                                 "--console=plain"], cwd=args.repo_root, env=env, stdout=log, stderr=subprocess.STDOUT)
    if built != 0:
        print(f"build failed; see {out / 'build.log'}", flush=True)
        return 1
    for name in summary["requested_scenarios"]:
        shaders, extra = SCENARIOS[name]
        suffix = "on" if shaders else "off"
        artifact_dir = out / name
        command = [sys.executable, str(args.repo_root / "DevUtils" / "Audit" / "Capture.py"), "--profile", "standard",
                   "--mode", f"current-rust-vulkan-shaders-{suffix}", "--mode", f"frozen-opengl-shaders-{suffix}",
                   "--repo-root", str(args.repo_root), "--frozen-repo", str(args.frozen_repo),
                   "--world", "Origin", "--artifact-dir", str(artifact_dir), "--artifact-preserve-current-run",
                   "--rust-profile", "release",
                   *extra, *[f"--jvm-arg={arg}" for arg in args.jvm_arg]]
        print(f"{name} ...", flush=True)
        with (out / f"{name}.log").open("w") as log:
            try:
                code = subprocess.call(command, cwd=args.repo_root, env=env, stdout=log, stderr=subprocess.STDOUT,
                                       timeout=1800)
            except subprocess.TimeoutExpired:
                code = 124
        summary["scenarios"][name] = summarize(artifact_dir, code)
        summary["scenarios"][name]["owned_orphans"] = stop_leftover_clients(out)
        if summary["scenarios"][name]["owned_orphans"]:
            summary["scenarios"][name]["success"] = False
        retire_completed_fixtures(out)
        print(f"{name}: {json.dumps({k: v for k, v in summary['scenarios'][name].items() if k != 'images'})}", flush=True)
    if args.compare:
        baseline = json.loads((out.parent / args.compare / "summary.json").read_text())
        summary["regressions"] = compare(summary, baseline)
        print("regressions:", summary["regressions"] or "none", flush=True)
    summary["passed"] = passed(summary)
    summary["workspace_retention"] = retire_completed_fixtures(out)
    (out / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    summary["retired_invocations"] = retire_old_invocations(out, summary["schema"],
                                                          protected_labels=[args.compare] if args.compare else [])
    (out / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
