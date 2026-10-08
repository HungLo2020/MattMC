#!/usr/bin/env python3
"""Validate a rendering batch end to end: tests, lifecycle gate, Frozen
parity and FPS, with one command and one artifact directory.

    python3 DevUtils/tests/rendering/RunValidation.py --label <label> [--perf]

Order, chosen so nothing is built or run twice and the GPU is never shared by
two clients:
1. Java tests on the release native library, without Gradle's serial rerun of
   the Rust suite (`-x testRustNative`). This also builds the release library
   and Java classes the clients use, so no client's readiness timer pays for a
   cold build. With --skip-java-tests the build runs on its own.
2. The Rust suite (parallel, failures re-run serially to separate flakes) and
   the wiki check run in the background while the lifecycle gate and both
   parity pairs run on the GPU. They check correctness, not timing.
3. FPS runs last, alone. By default one clean 1,800-frame run per mode on the
   current client. --perf instead interleaves current and Frozen (ABAB,
   6,000 frames each) for every mode; use it only when a change claims a
   performance effect.

Everything lands in artifacts/graphics-captures/validation/<label>/ (an
existing label is refused), with summary.json and summary.md. The exit status
is 0 only when every step passes. Inspect the parity side-by-side images
listed in the summary; image checks are not automatic.
"""
from __future__ import annotations

import argparse
import gzip
import json
import math
import os
import re
import signal
import statistics
import subprocess
import sys
import threading
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO / "DevUtils" / "Common"))
from artifact_retention import ensure_marker, retire_completed_fixtures, retire_old_invocations
from RunLifecycleGate import SCENARIOS as LIFECYCLE_SCENARIOS
FROZEN_REPO = REPO.parent / "MattMC_JavaPerfTesting" / "MattMC"
GOAL5 = REPO / "artifacts" / "graphics-captures" / "goal5"
DEFAULT_RUN_SOURCE = GOAL5 / "terrain-look-direction" / "cold-source" / "run"
DEFAULT_VANILLA_RUN_SOURCE = GOAL5 / "shadow-orb-ordering-fix" / "copied-source" / "run"
DEFAULT_SHADER_PACK = GOAL5 / "rejected-source-shadow-depth" / "original-pack" / "ComplementaryHungLoIfied.zip"

# The rendering suites whose code paths a rendering batch touches.
DEFAULT_JAVA_TESTS = ("net.vulkanic.*", "net.sodium.*", "com.seibel.*", "net.minecraft.client.dev.*")

# (label, shaders, dh) for FPS.
FPS_MODES = (("vanilla", False, False), ("shaders", True, False),
             ("vanilla-dh", False, True), ("shaders-dh", True, True))

DH_ARGS = ["--world-distant-horizons-real-world", "--world-distant-horizons-water",
           "--dh-composition-mode", "DOUBLE_PASS", "--world-distant-horizons-opaque"]

PARITY_PAIRS = {
    # Iris + DH on the settled coast pose; DH generic rendering on.
    "shaders-dh": dict(modes=("current-rust-vulkan-shaders-on", "frozen-opengl-shaders-on"),
                       pose="150.5,125,530.5,105,15",
                       extra=["--rust-selected-source-execution", *DH_ARGS, "--rust-full-gameplay-attachments"],
                       env={"MATTMC_CAPTURE_DH_RADIUS_OVERRIDE": "32", "MATTMC_CAPTURE_DH_GENERIC": "true"},
                       vanilla_source=False),
    "vanilla": dict(modes=("current-rust-vulkan-shaders-off", "frozen-opengl-shaders-off"),
                    pose="150.5,100,530.5,105,10",
                    extra=["--client-args", "enableShaders=false"],
                    env={}, vanilla_source=True),
}

CLIENT_FAILURES = re.compile(r"Exception|panicked at")


def log(message: str) -> None:
    print(f"[{time.strftime('%H:%M:%S')}] {message}", flush=True)


def base_env(args: argparse.Namespace) -> dict[str, str]:
    env = dict(os.environ)
    # One release profile for every step (symbols for profilers), so no step
    # invalidates another's native build.
    env["CARGO_PROFILE_RELEASE_STRIP"] = "none"
    env["CARGO_PROFILE_RELEASE_DEBUG"] = "line-tables-only"
    env.setdefault("MATTMC_CAPTURE_MAX_FPS", "260")
    env["MATTMC_CAPTURE_SHADER_PACK_SOURCE"] = str(args.shader_pack)
    env.pop("MATTMC_CAPTURE_DH_RADIUS_OVERRIDE", None)
    env.pop("MATTMC_CAPTURE_DH_GENERIC", None)
    return env


def gradle_java_tests_command(filters: list[str] | None) -> list[str]:
    command = ["./gradlew", "-PmattmcRustProfile=release", "test", "-x", "testRustNative", "--console=plain"]
    for pattern in filters or ():
        command += ["--tests", pattern]
    return command


def gradle_build_command() -> list[str]:
    return ["./gradlew", "-PmattmcRustProfile=release", "buildRustNative", "classes", "--console=plain"]


def parity_command(root: Path, pair: dict, jvm_args: list[str] = ()) -> list[str]:
    command = [sys.executable, "DevUtils/Audit/Capture.py", "--profile", "extended"]
    for mode in pair["modes"]:
        command += ["--mode", mode]
    return command + [
        "--repo-root", str(REPO), "--frozen-repo", str(FROZEN_REPO),
        "--artifact-root", str(root), "--artifact-preserve-current-run",
        "--capture-camera-pose", pair["pose"], "--rust-profile", "release",
        "--workload-profile", "settled-static", "--validation", "standard", "--diagnostic",
        *pair["extra"], *[f"--jvm-arg={arg}" for arg in jvm_args],
    ]


def fps_command(root: Path, mode: str, shaders: bool, dh: bool, frames: int,
                jvm_args: list[str] = ()) -> list[str]:
    command = [
        sys.executable, "DevUtils/PerfAudit/Gameplay.py", "--profile", "extended", "--mode", mode,
        "--repo-root", str(REPO), "--frozen-repo", str(FROZEN_REPO),
        "--artifact-root", str(root), "--artifact-preserve-current-run",
        "--workload-profile", "moving-camera", "--capture-camera-pose", "150.5,100,530.5,105,10",
        "--rust-profile", "release", "--settle-frames", "360", "--warmup-frames", "240",
        "--measure-frames", str(frames), "--client-args", f"enableShaders={'true' if shaders else 'false'}",
    ]
    if dh:
        command.append("--world-distant-horizons-real-world")
    return command + [f"--jvm-arg={arg}" for arg in jvm_args]


def fps_mode(side: str, shaders: bool) -> str:
    backend = "current-rust-vulkan" if side == "current" else "frozen-opengl"
    return f"{backend}-shaders-{'on' if shaders else 'off'}"


def perf_order(repeats: int) -> list[str]:
    """Interleaved sides, current first: ABAB for two repeats."""
    return [side for _ in range(repeats) for side in ("current", "frozen")]


def client_start_ticks(proc: Path) -> int:
    stat = (proc / "stat").read_text()
    return int(stat[stat.rfind(")") + 2:].split()[19])


def leftover_clients(artifact_root: Path | None = None, proc_root: Path = Path("/proc")) -> list[tuple[int, int]]:
    """Identify clients; destructive cleanup always supplies an invocation root."""
    artifact_root = artifact_root.resolve() if artifact_root is not None else None
    pids = []
    if not proc_root.is_dir():
        return pids
    for proc in proc_root.iterdir():
        if not proc.name.isdigit():
            continue
        try:
            if (proc / "comm").read_text().strip() != "java":
                continue
            cmdline = (proc / "cmdline").read_bytes().replace(b"\0", b" ").decode(errors="replace")
            cwd = (proc / "cwd").resolve(strict=True) if artifact_root is not None else None
            start = client_start_ticks(proc)
        except (OSError, ValueError, IndexError):
            continue
        if (artifact_root is None or cwd == artifact_root or artifact_root in cwd.parents) and (
                "KnotClient" in cmdline or "devlaunchinjector" in cmdline):
            pids.append((int(proc.name), start))
    return pids


def stop_leftover_clients(artifact_root: Path) -> int:
    stopped = 0
    for pid, start in leftover_clients(artifact_root):
        fd = None
        try:
            # pidfds address the original process even if its PID is reused.
            if hasattr(os, "pidfd_open") and hasattr(signal, "pidfd_send_signal"):
                fd = os.pidfd_open(pid)
            if client_start_ticks(Path("/proc") / str(pid)) != start:
                continue
            if fd is not None:
                signal.pidfd_send_signal(fd, signal.SIGKILL)
            else:
                os.kill(pid, signal.SIGKILL)
            stopped += 1
        except (OSError, ValueError, IndexError):
            pass
        finally:
            if fd is not None:
                os.close(fd)
    return stopped


def run_logged(command: list[str], log_path: Path, env: dict[str, str], timeout: int) -> int:
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open("w") as handle:
        try:
            return subprocess.call(command, cwd=REPO, env=env, stdout=handle, stderr=subprocess.STDOUT,
                                   timeout=timeout)
        except subprocess.TimeoutExpired:
            handle.write(f"\nTIMEOUT after {timeout}s\n")
            return 124


def read_client_health(run_dir: Path) -> dict:
    exceptions, terrain_failures = 0, None
    for client_log in run_dir.glob("capture/runClient_*.log*"):
        if client_log.suffix == ".gz":
            text = gzip.decompress(client_log.read_bytes()).decode(errors="replace")
        elif client_log.suffix == ".log":
            text = client_log.read_text(errors="replace")
        else:
            continue
        exceptions += len(CLIENT_FAILURES.findall(text))
        found = re.findall(r"failureCount=(\d+)", text)
        if found:
            terrain_failures = int(found[-1])
    return {"exceptions": exceptions, "terrain_failures": terrain_failures}


def positive_number(value) -> bool:
    return type(value) in (int, float) and math.isfinite(value) and value > 0


def read_fps_artifact(artifact: Path, expected_frames: int | None = None) -> dict:
    try:
        document = json.loads(artifact.read_text())
        metrics = document["metrics"]
        frames = metrics["frame_time_ms"]
        count, total = frames.get("count"), frames.get("total")
    except (OSError, ValueError, KeyError, TypeError, AttributeError) as error:
        return {"clean": False, "error": f"invalid FPS artifact: {error}"}
    fps = 1000 * count / total if type(count) is int and count > 0 and positive_number(total) else None
    vuids = (metrics.get("validation_findings") or {}).get("concrete_vuid_count")
    health = read_client_health(artifact.parent)
    validity = document.get("validation") or {}
    clean = (health["exceptions"] == 0 and health["terrain_failures"] in (None, 0)
             and type(vuids) is int and vuids == 0
             and type(document.get("capture", {}).get("exit_code")) is int
             and document["capture"]["exit_code"] == 0
             and all(validity.get(k) is True for k in
                     ("complete", "crash_free", "device_loss_free", "frame_sampler_validity_passed", "performance_publishable"))
             and all(validity.get(k) is False for k in ("orphan_process_detected", "rss_guard_triggered"))
             and positive_number(fps) and positive_number(frames.get("median"))
             and positive_number(frames.get("p99"))
             and (expected_frames is None or count == expected_frames))
    return {"fps": round(fps, 1) if fps else None, "median_ms": frames.get("median"), "p99_ms": frames.get("p99"),
            "frames": frames.get("count"), "vuids": vuids, **health, "clean": clean}


def performance_comparison(modes: dict) -> list[str]:
    """Frozen is the performance floor; run health alone is not acceptance."""
    failures = []
    for name, _, _ in FPS_MODES:
        sides = modes.get(name, {})
        current, frozen = sides.get("current", {}), sides.get("frozen", {})
        if (not current.get("clean") or not frozen.get("clean")
                or len(current.get("fps", [])) < 2
                or len(current.get("fps", [])) != len(frozen.get("fps", []))
                or any(not positive_number(value) for side in (current, frozen)
                       for key in ("fps", "p99_ms") for value in side.get(key, []))
                or any(len(side.get("p99_ms", [])) != len(side.get("fps", [])) for side in (current, frozen))):
            failures.append(f"{name}: two clean paired repeats per side are required")
            continue
        current_fps, frozen_fps = (statistics.median(side["fps"]) for side in (current, frozen))
        if current_fps < frozen_fps:
            failures.append(f"{name}: Current {current_fps:.1f} FPS < Frozen {frozen_fps:.1f} FPS")
        current_tail, frozen_tail = (statistics.median(side["p99_ms"]) for side in (current, frozen))
        if current_tail > frozen_tail:
            failures.append(f"{name}: Current p99 {current_tail:.3f} ms > Frozen {frozen_tail:.3f} ms")
    return failures


def read_parity(root: Path, expect_dh: bool = False) -> dict:
    reports = sorted(root.glob("**/paired_visual_static_terrain/visual_parity_report.json"))
    if not reports:
        return {"passed": False, "error": "no visual parity report"}
    try:
        report = json.loads(reports[-1].read_text())
        pair = report["pairs"][0]
        rgb = pair["diff"]["mean_rgb_abs"]
        dh = pair.get("dh_visible_extension") or {}
        vuids = [json.loads(a.read_text())["metrics"]["validation_findings"]["concrete_vuid_count"]
                 for a in root.glob("**/current-rust-*/**/graphics_audit_artifact.json")]
    except (OSError, ValueError, KeyError, IndexError, TypeError) as error:
        return {"passed": False, "error": f"invalid parity evidence: {error}"}
    images = sorted(str(p.relative_to(REPO)) for p in reports[-1].parent.glob("pair-*/side_by_side_*.png"))
    valid_rgb = (isinstance(rgb, list) and len(rgb) == 3
                 and all(type(value) in (int, float) and math.isfinite(value) and value >= 0 for value in rgb))
    passed = (report.get("passed") is True and valid_rgb and bool(vuids)
              and all(type(value) is int and value == 0 for value in vuids)
              and (dh.get("passed") is True if expect_dh else dh.get("passed") is not False))
    return {"passed": passed, "mean_rgb": [round(x, 3) for x in rgb] if valid_rgb else None,
            "dh_passed": dh.get("passed"), "vuids": vuids, "images": images}


def failed_rust_tests(output: str) -> list[str]:
    """Test names from libtest's trailing 'failures:' list."""
    blocks = re.findall(r"^failures:\n((?:    \S+\n)+)", output, re.MULTILINE)
    return blocks[-1].split() if blocks else []


def rust_tests(out: Path, env: dict[str, str], threads: int) -> dict:
    manifest = str(REPO / "src" / "main" / "rust" / "Cargo.toml")
    env = {**env, "ALSOFT_DRIVERS": "null"}
    command = ["cargo", "test", "--manifest-path", manifest, "--locked", "--lib", "--", f"--test-threads={threads}"]
    code = run_logged(command, out / "rust-tests.log", env, 3600)
    text = (out / "rust-tests.log").read_text(errors="replace")
    summary = re.findall(r"test result: .*", text)
    result = {"exit": code, "result": summary[-1] if summary else None, "flaky": [], "failed": []}
    failures = failed_rust_tests(text)
    if code != 0 and failures:
        # Native Vulkan/OpenAL/EGL tests share process-global driver state;
        # re-run the failures serially to tell a race from a real failure.
        rerun = ["cargo", "test", "--manifest-path", manifest, "--locked", "--lib", "--",
                 "--test-threads=1", "--exact", *failures]
        rerun_code = run_logged(rerun, out / "rust-tests-rerun.log", env, 1800)
        rerun_text = (out / "rust-tests-rerun.log").read_text(errors="replace")
        still = failed_rust_tests(rerun_text) if rerun_code else []
        result.update(failed=still, flaky=[t for t in failures if t not in still],
                      exit=rerun_code)
    result["passed"] = result["exit"] == 0
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--label", required=True, help="artifact directory name (must be new)")
    parser.add_argument("--perf", action="store_true",
                        help="interleaved current/Frozen FPS (ABAB, 6,000 frames) instead of one health run")
    parser.add_argument("--perf-repeats", type=int, default=2, help="runs per side and mode with --perf")
    parser.add_argument("--skip", action="append", default=[],
                        choices=("java-tests", "rust-tests", "wiki", "gate", "parity", "fps"),
                        help="skip a step (repeatable)")
    parser.add_argument("--java-test", action="append", dest="java_tests",
                        help="Gradle --tests pattern (repeatable); default the rendering suites")
    parser.add_argument("--all-java-tests", action="store_true", help="run every Java test")
    parser.add_argument("--rust-test-threads", type=int, default=4)
    parser.add_argument("--jvm-arg", action="append", default=[],
                        help="extra client JVM option for the gate, parity and current-client FPS runs (repeatable), "
                             "e.g. -Dmattmc.dev.forceTerrainVertexStaging=true")
    parser.add_argument("--run-source", type=Path,
                        default=Path(os.environ.get("MATTMC_CAPTURE_RUN_SOURCE", DEFAULT_RUN_SOURCE)),
                        help="run directory for the gate and the Iris+DH pair")
    parser.add_argument("--vanilla-run-source", type=Path, default=DEFAULT_VANILLA_RUN_SOURCE,
                        help="run directory for the vanilla pair and FPS")
    parser.add_argument("--shader-pack", type=Path,
                        default=Path(os.environ.get("MATTMC_CAPTURE_SHADER_PACK_SOURCE", DEFAULT_SHADER_PACK)))
    args = parser.parse_args()
    if Path(args.label).name != args.label or args.label in (".", ".."):
        parser.error("--label must be one directory name")
    if args.perf and args.perf_repeats < 2:
        parser.error("--perf requires at least two repeats per side")

    out = REPO / "artifacts" / "graphics-captures" / "validation" / args.label
    if out.exists():
        parser.error(f"{out.relative_to(REPO)} already exists; pick a new label")
    missing = [str(p) for p in (args.run_source, args.vanilla_run_source, args.shader_pack, FROZEN_REPO)
               if not p.exists()]
    if missing:
        parser.error("missing inputs: " + ", ".join(missing))
    stray = leftover_clients()
    if stray:
        parser.error(f"MattMC clients already running (pids {stray}); stop them first")
    out.mkdir(parents=True)
    ensure_marker(out)
    env = base_env(args)
    summary: dict = {"schema": "mattmc-validation-v2", "label": args.label, "head": subprocess.run(["git", "rev-parse", "HEAD"], cwd=REPO,
                     text=True, capture_output=True).stdout.strip(), "steps": {}, "timings_s": {}}
    summary["requested_steps"] = ["build" if "java-tests" in args.skip else "java-tests"] + [
        step for step in ("rust-tests", "wiki", "gate", "fps") if step not in args.skip] + [
        f"parity-{name}" for name in PARITY_PAIRS if "parity" not in args.skip]
    steps = summary["steps"]
    started = time.monotonic()

    def timed(name: str, began: float) -> None:
        summary["timings_s"][name] = round(time.monotonic() - began)

    # 1. Java tests; their release build is the clients' prebuild.
    began = time.monotonic()
    if "java-tests" not in args.skip:
        log("java tests")
        filters = None if args.all_java_tests else (args.java_tests or list(DEFAULT_JAVA_TESTS))
        code = run_logged(gradle_java_tests_command(filters), out / "java-tests.log", env, 3600)
        steps["java-tests"] = {"exit": code, "passed": code == 0}
    else:
        log("release build")
        code = run_logged(gradle_build_command(), out / "build.log", env, 3600)
        steps["build"] = {"exit": code, "passed": code == 0}
    timed("java-tests-or-build", began)
    if code != 0 and "java-tests" in args.skip:
        log("build failed; stopping")
        return finish(out, summary, started)

    # 2. CPU checks in the background while the GPU runs correctness steps.
    cpu_results: dict = {}

    def cpu_checks() -> None:
        for step in ("rust-tests", "wiki"):
            if step in args.skip:
                continue
            began = time.monotonic()
            try:
                if step == "rust-tests":
                    cpu_results[step] = rust_tests(out, env, args.rust_test_threads)
                else:
                    code = run_logged([sys.executable, "DevUtils/RunWiki.py", "check"], out / "wiki.log", env, 600)
                    cpu_results[step] = {"exit": code, "passed": code == 0}
            except Exception as error:
                cpu_results[step] = {"passed": False, "error": f"{type(error).__name__}: {error}"}
            finally:
                summary["timings_s"][step] = round(time.monotonic() - began)

    cpu = threading.Thread(target=cpu_checks)
    cpu.start()

    if "gate" not in args.skip:
        log("lifecycle gate")
        began = time.monotonic()
        code = run_logged([sys.executable, "DevUtils/tests/rendering/RunLifecycleGate.py", "--label", args.label,
                           "--artifact-root", str(out / "gate"), "--run-source", str(args.run_source),
                           "--shader-pack", str(args.shader_pack),
                           *[f"--jvm-arg={arg}" for arg in args.jvm_arg]], out / "gate.log", env, 7200)
        scenarios = json.loads((out / "gate" / "summary.json").read_text()) if (out / "gate" / "summary.json").exists() else []
        steps["gate"] = {"exit": code, "passed": code == 0
                         and {s["scenario"] for s in scenarios} == set(LIFECYCLE_SCENARIOS)
                         and all(s.get("passed") is True for s in scenarios),
                         "scenarios": {s["scenario"]: s["passed"] for s in scenarios}}
        steps["gate"]["owned_orphans"] = stop_leftover_clients(out)
        if steps["gate"]["owned_orphans"]:
            steps["gate"]["passed"] = False
        timed("gate", began)

    if "parity" not in args.skip:
        for name, pair in PARITY_PAIRS.items():
            log(f"parity {name}")
            began = time.monotonic()
            pair_env = {**env, **pair["env"],
                        "MATTMC_CAPTURE_RUN_SOURCE": str(args.vanilla_run_source if pair["vanilla_source"] else args.run_source)}
            root = out / "parity" / name
            code = run_logged(parity_command(root, pair, args.jvm_arg), out / "parity" / f"{name}.log", pair_env, 3600)
            steps[f"parity-{name}"] = {"exit": code, **read_parity(root, expect_dh=name == "shaders-dh")}
            if code != 0:
                steps[f"parity-{name}"]["passed"] = False
            steps[f"parity-{name}"]["owned_orphans"] = stop_leftover_clients(out)
            if steps[f"parity-{name}"]["owned_orphans"]:
                steps[f"parity-{name}"]["passed"] = False
            timed(f"parity-{name}", began)

    log("waiting for CPU checks")
    cpu.join()
    steps.update(cpu_results)

    # 3. FPS last, with nothing else running.
    if "fps" not in args.skip:
        fps_env = {**env, "MATTMC_CAPTURE_RUN_SOURCE": str(args.vanilla_run_source)}
        frames = 6000 if args.perf else 1800
        sides = perf_order(args.perf_repeats) if args.perf else ["current"]
        rows: dict = {}
        for label, shaders, dh in FPS_MODES:
            for index, side in enumerate(sides):
                log(f"fps {label} {side} #{index}")
                began = time.monotonic()
                root = out / "fps" / f"{label}-{side}-{index}"
                code = run_logged(fps_command(root, fps_mode(side, shaders), shaders, dh, frames,
                                              args.jvm_arg if side == "current" else []),
                                  root / "driver.log", fps_env, 1800)
                artifacts = sorted(root.glob("**/run-01/graphics_audit_artifact.json"))
                row = read_fps_artifact(artifacts[-1], frames) if artifacts else {"clean": False, "error": "no artifact"}
                row["exit"] = code
                rows.setdefault(label, {}).setdefault(side, []).append(row)
                row["owned_orphans"] = stop_leftover_clients(out)
                if row["owned_orphans"]:
                    row["clean"] = False
                timed(f"fps-{label}-{side}-{index}", began)
        fps_summary = {}
        for label, by_side in rows.items():
            fps_summary[label] = {side: {"fps": [r.get("fps") for r in runs],
                                         "median_fps": statistics.median([r["fps"] for r in runs if r.get("fps")] or [0]),
                                         "median_ms": [r.get("median_ms") for r in runs],
                                         "p99_ms": [r.get("p99_ms") for r in runs],
                                         "clean": all(r.get("clean") and r.get("exit") == 0 for r in runs)}
                                  for side, runs in by_side.items()}
        comparisons = performance_comparison(fps_summary) if args.perf else []
        steps["fps"] = {"protocol": f"{'interleaved ' + ''.join('A' if s == 'current' else 'B' for s in sides) if args.perf else 'single'}"
                                    f", {frames} frames", "modes": fps_summary, "runs": rows,
                        "comparison_failures": comparisons,
                        "performance_verified": args.perf and not comparisons,
                        "passed": all(v["clean"] for by in fps_summary.values() for v in by.values())
                                  and not comparisons}
    return finish(out, summary, started)


def finish(out: Path, summary: dict, started: float) -> int:
    summary["timings_s"]["total"] = round(time.monotonic() - started)
    summary["missing_steps"] = sorted(set(summary.get("requested_steps", [])) - summary["steps"].keys())
    summary["passed"] = (bool(summary["steps"]) and not summary["missing_steps"]
                         and all(step.get("passed") is True for step in summary["steps"].values()))
    summary["workspace_retention"] = retire_completed_fixtures(out)
    (out / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    lines = [f"# Validation {summary['label']} @ {summary['head'][:9]}: {'PASS' if summary['passed'] else 'FAIL'}", ""]
    if summary["missing_steps"]:
        lines.append(f"- Missing requested steps: {', '.join(summary['missing_steps'])}")
    for name, step in summary["steps"].items():
        detail = ""
        if name.startswith("parity-"):
            detail = f" mean RGB {step.get('mean_rgb')} DH {step.get('dh_passed')} VUIDs {step.get('vuids')}"
        elif name == "gate":
            detail = " " + " ".join(f"{k}={'PASS' if v else 'FAIL'}" for k, v in step.get("scenarios", {}).items())
        elif name == "rust-tests":
            detail = f" {step.get('result')} flaky={step.get('flaky')} failed={step.get('failed')}"
        elif name == "fps":
            detail = f" ({step['protocol']}) " + "; ".join(
                f"{mode}: " + ", ".join(f"{side} {v['median_fps']:.0f}" for side, v in sides.items())
                for mode, sides in step["modes"].items())
            if step.get("comparison_failures"):
                detail += " failures=" + "; ".join(step["comparison_failures"])
        if step.get("error"):
            detail += f" error={step['error']}"
        lines.append(f"- {name}: {'PASS' if step.get('passed') else 'FAIL'}{detail}")
    images = [img for step in summary["steps"].values() for img in step.get("images", [])]
    if images:
        lines += ["", "Inspect the parity images:", *[f"- {img}" for img in images]]
    lines += ["", "Timings (s): " + ", ".join(f"{k} {v}" for k, v in summary["timings_s"].items())]
    (out / "summary.md").write_text("\n".join(lines) + "\n")
    summary["retired_invocations"] = retire_old_invocations(out, summary["schema"])
    (out / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print("\n".join(lines), flush=True)
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
