#!/usr/bin/env python3
"""Exercise real chunk-boundary movement with normal gameplay and bounded video.

The current/Frozen capture engines retain launch, isolation and cleanup ownership.
Neither frame benchmark nor deterministic camera controller is enabled. Commands
and movement affect only the engine's isolated copied singleplayer world.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import signal
import sqlite3
from pathlib import Path
import subprocess
import sys
import time
import tomllib
import shutil
import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "Common"))
import artifact_retention

from x11_game_input import GameInput
from CaptureWindowVideo import process_start
from capture_runner import capture_linux_x11_window, find_linux_client_window_id, window_capture_provenance


def flight_disk_preflight(output: Path, diagnostic_attachments: bool = False) -> dict:
    # Preserve observations while retaining the shared eight-GiB reserve and
    # one-GiB estimate used for extended captures. Preserve mode alone has a
    # zero estimate; it must not waive the space needed by this bounded run.
    policy = artifact_retention.policy_for("extended", output, preserve=True)
    return artifact_retention.preflight_disk_budget(
        policy, artifact_retention.estimated_run_bytes("extended")
        + (1024 ** 3 if diagnostic_attachments else 0))


def client_pid(root: Path) -> tuple[int, Path] | None:
    matches = []
    for entry in Path("/proc").iterdir():
        if not entry.name.isdigit():
            continue
        try:
            command = (entry / "cmdline").read_bytes().split(b"\0")
            directory = (entry / "cwd").resolve()
            if (b"net.fabricmc.loader.impl.launch.knot.KnotClient" in command
                    and directory.is_relative_to(root) and directory.name.startswith("game_dir_")):
                matches.append((int(entry.name), directory))
        except (OSError, ProcessLookupError):
            continue
    if len(matches) > 1:
        raise RuntimeError("multiple clients own the flight artifact root")
    return matches[0] if matches else None



def stop_observer(observer: subprocess.Popen) -> None:
    """Reap our own observer and its decoder on an interrupted observation."""
    if observer.poll() is None:
        try:
            os.killpg(observer.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
        try:
            observer.wait(timeout=10)
        except subprocess.TimeoutExpired:
            try:
                os.killpg(observer.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            observer.wait(timeout=10)
    else:
        observer.wait(timeout=10)



def stop_orphaned_client(root: Path) -> bool:
    """Last-resort cleanup after engine failure, scoped to its isolated root.

    Linux pidfds keep a reused PID from receiving a signal. This never stops a
    client that successfully remains owned by a live capture engine.
    """
    client = client_pid(root)
    if client is None:
        return False
    pid, directory = client
    if not directory.is_relative_to(root):
        raise RuntimeError("refusing cleanup outside the isolated artifact root")
    identity = process_start(pid)
    fd = os.pidfd_open(pid)
    try:
        if process_start(pid) != identity or client_pid(root) != client:
            raise RuntimeError("client identity changed before orphan cleanup")
        signal.pidfd_send_signal(fd, signal.SIGTERM)
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            try:
                if process_start(pid) != identity:
                    return True
            except OSError:
                return True
            time.sleep(.05)
        try:
            signal.pidfd_send_signal(fd, signal.SIGKILL)
        except ProcessLookupError:
            pass
        return True
    finally:
        os.close(fd)

def command_acknowledgment(log: Path, offset: int, tokens: tuple[str, ...]) -> str | None:
    text = log.read_text(errors="replace")[offset:]
    failures = ("Incorrect argument for command", "Unknown or incomplete command", "Unknown command")
    if any(failure in text for failure in failures):
        raise RuntimeError("game rejected flight setup command: " + text[-1200:])
    return next((line for line in text.splitlines() if all(token in line for token in tokens)), None)


def debug_world_text(text: str) -> bool:
    # Only a real F3 world view has both fields; a joined server/window title
    # still allows LevelLoadingScreen, which ignores ordinary look input.
    return bool(re.search(r"section[- ]relative", text, re.IGNORECASE)
                and re.search(r"facing\s*:", text, re.IGNORECASE))


def read_world_debug_text(screenshot: Path, mask: Path) -> str:
    with Image.open(screenshot) as image:
        pixels = np.asarray(image.convert("RGB"))[:425, :650]
    monochrome = (pixels.max(2) - pixels.min(2) < 3) & (pixels[:, :, 0] > 180)
    Image.fromarray(np.where(monochrome, 0, 255).astype("uint8")).save(mask)
    return subprocess.run(["tesseract", str(mask), "stdout", "--psm", "6"],
                          capture_output=True, text=True, check=True, timeout=5).stdout


def wait_for_first_world_view(controller: GameInput, output: Path) -> dict:
    if not shutil.which("tesseract"):
        raise RuntimeError("first-turn readiness requires tesseract for the actual F3 world view")
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        controller.tap("F3")
        time.sleep(.5)
        controller.check()
        screenshot = output / "start-f3.png"
        if not capture_linux_x11_window(controller.target, screenshot):
            raise RuntimeError("first-turn readiness screenshot failed")
        mask = output / "start-f3-text.png"
        text = read_world_debug_text(screenshot, mask)
        controller.check()
        if debug_world_text(text):
            return {"screenshot": screenshot.name, "wall_ns": time.time_ns(),
                    "readiness": "actual F3 world view", "ocr": text}
    raise RuntimeError("first-turn could not verify the actual world view within 45 seconds")


def wait_for_debug_hidden(controller: GameInput, output: Path) -> dict:
    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        time.sleep(.2)
        controller.check()
        screenshot = output / "ready-world.png"
        if not capture_linux_x11_window(controller.target, screenshot):
            raise RuntimeError("debug-hidden readiness screenshot failed")
        text = read_world_debug_text(screenshot, output / "ready-world-text.png")
        if not debug_world_text(text):
            return {"screenshot": screenshot.name, "wall_ns": time.time_ns(), "readiness": "F3 hidden before look"}
    raise RuntimeError("F3 overlay did not hide before first look")


def runtime_outcome(capture: Path, engine_exit_code: int) -> dict:
    metadata = list(capture.glob("meta_*.txt"))
    if len(metadata) != 1:
        raise RuntimeError("expected one retained capture metadata file")
    values = dict(line.split("=", 1) for line in metadata[0].read_text().splitlines() if "=" in line)
    if engine_exit_code != 0 or values.get("timeout") != "true" or not values.get("end_epoch"):
        raise RuntimeError("capture engine did not reach its bounded terminal timeout")
    if values.get("exit_code") not in ("0", "143", "137"):
        raise RuntimeError("unexpected client exit code: " + values.get("exit_code", "missing"))
    if values.get("memory_guard_triggered") == "true":
        raise RuntimeError("capture engine memory guard triggered")
    if values.get("cleanup_client_core_dumping") == "true":
        raise RuntimeError("isolated client began a native core dump during termination")
    for pattern in ("hs_err_*.txt", "crash_reports_*.txt"):
        for path in capture.glob(pattern):
            if path.read_text().strip():
                raise RuntimeError("capture retained crash evidence: " + str(path))
    for path in capture.glob("validation_events_*.log"):
        text = path.read_text(errors="replace")
        if any(token in text for token in ("VUID-", "Validation Error", "Validation Warning")):
            raise RuntimeError("capture retained concrete validation diagnostics: " + str(path))
    return {"status": "bounded_timeout_reaped", "metadata": str(metadata[0]),
            "client_exit_code": int(values["exit_code"]),
            "rust_native_sha256": values.get("rust_native_sha256"),
            "peak_rss_kb": values.get("memory_guard_peak_rss_kb"),
            "limitations": "Bounded observation; no long-run memory or flicker acceptance."}


def verify_dh_disabled(directory: Path) -> dict:
    path = directory / "config/DistantHorizons.toml"
    data = tomllib.loads(path.read_text())
    mode = data.get("client", {}).get("advanced", {}).get("debugging", {}).get("rendererMode")
    if mode != "DISABLED":
        raise RuntimeError("ordinary vanilla flight requires DH rendererMode DISABLED; actual=" + repr(mode))
    return {"dh_renderer_mode": mode, "config": str(path)}


def verify_dh_enabled(directory: Path) -> dict:
    path = directory / "config/DistantHorizons.toml"
    data = tomllib.loads(path.read_text())
    advanced = data.get("client", {}).get("advanced", {})
    mode = advanced.get("debugging", {}).get("rendererMode")
    quality = advanced.get("graphics", {}).get("quality", {})
    radius = quality.get("lodChunkRenderDistanceRadius")
    generation = data.get("common", {}).get("worldGenerator", {}).get("enableDistantGeneration")
    if mode != "DEFAULT" or type(radius) is not int or not 1 <= radius <= 64 or generation is not False:
        raise RuntimeError("DH flight requires DEFAULT rendering, radius 1..64 and distant generation disabled in the copied source")
    return {"dh_renderer_mode": mode, "dh_radius_chunks": radius,
            "dh_vanilla_fade": quality.get("vanillaFadeMode"),
            "dh_fog": advanced.get("graphics", {}).get("fog", {}).get("enableDhFog"),
            "dh_distant_generation": generation, "config": str(path)}


def verify_dh_source(directory: Path, world: str) -> dict:
    configuration = verify_dh_enabled(directory)
    database = directory / "saves" / world / "data/DistantHorizons.sqlite"
    with sqlite3.connect(database.resolve(strict=True).as_uri() + "?mode=ro", uri=True) as connection:
        formats = connection.execute("SELECT DataFormatVersion, COUNT(*) FROM FullData GROUP BY DataFormatVersion").fetchall()
    if not formats or any(version not in (1, 2) for version, _ in formats):
        raise RuntimeError("DH flight requires nonempty Frozen-compatible format 1/2 LOD data")
    with database.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    return {**configuration, "database": str(database), "database_sha256": digest,
            "database_format_counts": formats}


def dh_execution_samples(runtime: str) -> list[dict]:
    samples = []
    for line in runtime.splitlines():
        match = re.fullmatch(
            r"(?:\[[0-9]{2}:[0-9]{2}:[0-9]{2}\] \[Render thread/INFO\]: \[STDOUT\]: )?"
            r"\[MattMC DH\] submitted (.+)", line)
        if match is None:
            continue
        fields = dict(token.split("=", 1) for token in match.group(1).split())
        sample = {key: int(fields[key]) for key in
                  ("world_frame", "submission", "instances", "opaque", "transparent", "water", "wall_ms")}
        if (sample["world_frame"] <= 0 or sample["submission"] <= 0 or sample["instances"] <= 0
                or any(sample[key] < 0 for key in ("opaque", "transparent", "water"))
                or sample["instances"] != sum(sample[key] for key in ("opaque", "transparent", "water"))):
            raise RuntimeError("invalid DH execution trace")
        samples.append(sample)
    return samples


def dh_submissions_for_video(samples: list[dict], video: dict) -> list[dict]:
    if video.get("status") != "complete":
        raise RuntimeError("DH observation video did not complete")
    start = video["ffmpeg_wall_start_ns"] // 1_000_000
    end = video["ffmpeg_wall_end_ns"] // 1_000_000
    selected = [sample for sample in samples if start <= sample["wall_ms"] <= end]
    if not selected:
        raise RuntimeError("no successful native DH segment submission observed during video")
    return selected



def verify_shader_configuration(directory: Path, shaders: str, expected_archive_sha256: str | None = None) -> dict:
    path = directory / "config/iris.properties"
    values = dict(line.split("=", 1) for line in path.read_text().splitlines()
                  if "=" in line and not line.startswith("#"))
    expected = str(shaders == "on").lower()
    if values.get("enableShaders") != expected:
        raise RuntimeError("runtime Iris configuration does not match requested shader mode")
    result = {"iris_enable_shaders": values["enableShaders"], "shader_pack": values.get("shaderPack")}
    if shaders == "on" and expected_archive_sha256 is not None:
        archive = directory / "shaderpacks" / values.get("shaderPack", "")
        if not archive.is_file():
            raise RuntimeError("runtime shader archive is missing")
        with archive.open("rb") as stream:
            actual = hashlib.file_digest(stream, "sha256").hexdigest()
        result["shader_pack_sha256"] = actual
        if actual != expected_archive_sha256:
            raise RuntimeError("runtime shader archive differs from the copied source: " + actual)
    return result

def launch_command(repo: Path, output: Path, backend: str, shaders: str, world: str) -> tuple[Path, list[str]]:
    engine = repo / "DevUtils/Common/capture_runner.py"
    command = [sys.executable, str(engine)]
    if not engine.is_file():
        engine = engine.with_suffix(".sh")
        if not engine.is_file():
            raise ValueError("repository has no supported capture engine")
        command = ["bash", str(engine)]
    # The legacy Frozen shell does not add quick-play or window dimensions.
    # Quote the world within the single Gradle runClient argument string.
    import shlex
    client_args = shlex.join([f"--quickPlaySingleplayer={world}", "--width", "1280", "--height", "720", f"enableShaders={str(shaders == 'on').lower()}"])
    command += ["--backend", backend, "--shaders", shaders, "--world", world,
                "--client-args", client_args, "--artifact-dir", str(output / "capture"),
                "--max-secs", "180", "--dump-secs", "170"]
    if backend == "rust-vulkan":
        command += ["--rust-profile", "release", "--skip-tests", "--validation", "standard"]
    else:
        command += ["--validation", "off"]
    return engine, command


def input_ready(proof: dict, backend: str, shaders: str, runtime: str) -> bool:
    # Server join precedes LevelLoadingScreen completion. The window title is
    # refreshed when the client closes that screen, after its connection exists.
    if proof.get("status") != "verified" or " - Singleplayer" not in proof.get("windowProperties", ""):
        return False
    if backend == "rust-vulkan" and shaders == "on":
        routes = [line for line in runtime.splitlines() if "[MattMC shaders]" in line and "shader route" in line]
        if not (routes and "shader route active" in routes[-1]):
            return False
    return True

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--run-source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--backend", choices=("rust-vulkan", "opengl"), required=True)
    parser.add_argument("--shaders", choices=("on", "off"), default="off")
    parser.add_argument("--dh", choices=("on", "off"), default="off")
    parser.add_argument("--world", default="Origin")
    parser.add_argument("--pose", choices=("coast", "buried", "forest"), default="coast")
    parser.add_argument("--motion", choices=("translate", "turn", "travel-turn", "first-turn"), default="translate",
                        help="ordinary travel, stationary turns, or mouse turns during travel")
    parser.add_argument("--seconds", type=int, default=8, choices=range(1, 11))
    parser.add_argument("--first-turn-idle-seconds", type=int, default=0, choices=range(0, 21),
                        help="stand still before the first mouse turn; default preserves cold-entry observation")
    parser.add_argument("--diagnostic-attachments", action="store_true",
                        help="enable correlated Rust attachment observations; adds audit/readback overhead")
    args = parser.parse_args()
    if args.diagnostic_attachments and (args.backend != "rust-vulkan" or args.shaders != "on"
                                        or args.motion != "first-turn"):
        parser.error("diagnostic attachments require a Rust shader-enabled first-turn observation")
    if args.first_turn_idle_seconds and args.motion != "first-turn":
        parser.error("first-turn idle applies only to first-turn observations")
    if args.motion != "translate" and args.pose != "forest":
        parser.error("turn observations require the forest pose")
    if args.motion in ("travel-turn", "first-turn") and args.seconds < 8:
        parser.error("mouse-turn observations require at least eight seconds")
    args.repo = args.repo.resolve(strict=True)
    args.run_source = args.run_source.resolve(strict=True)
    if args.motion == "first-turn" and not (args.run_source / ".terrain-turn-fixture-owned").is_file():
        parser.error("first-turn requires a saved camera from PrepareTerrainTurnFixture.java")
    args.output = args.output.resolve()
    if Path(args.world).name != args.world or not (args.run_source / "saves" / args.world).is_dir():
        parser.error("world must name one existing source save")
    dh_source = verify_dh_source(args.run_source, args.world) if args.dh == "on" else None
    expected_archive_sha256 = None
    if args.shaders == "on":
        properties = dict(line.split("=", 1) for line in (args.run_source / "config/iris.properties").read_text().splitlines()
                          if "=" in line and not line.startswith("#"))
        archive = args.run_source / "shaderpacks" / properties.get("shaderPack", "")
        if not archive.is_file():
            parser.error("shader observations require a configured archive in the copied source")
        with archive.open("rb") as stream:
            expected_archive_sha256 = hashlib.file_digest(stream, "sha256").hexdigest()
    current = Path(__file__).resolve().parents[3]
    if not args.output.is_relative_to(current / "artifacts/graphics-captures"):
        parser.error("output must be inside Current's ignored graphics artifact tree")
    args.output.mkdir(parents=True, exist_ok=False)
    disk_preflight = flight_disk_preflight(args.output, args.diagnostic_attachments)
    engine, command = launch_command(args.repo, args.output, args.backend, args.shaders, args.world)
    env = os.environ.copy()
    env.update(MATTMC_GRAPHICS_TOOL_INTERNAL="1", MATTMC_CAPTURE_RUN_SOURCE=str(args.run_source),
               MATTMC_CAPTURE_PRESERVE_ISOLATED_GAME_DIR="true", MATTMC_CAPTURE_RENDER_DISTANCE="10",
               MATTMC_CAPTURE_SIMULATION_DISTANCE="10", MATTMC_CAPTURE_MAX_FPS="260",
               MATTMC_CAPTURE_DISABLE_DH_FOR_PERF=str(args.dh == "off").lower(),
               MATTMC_CAPTURE_DISABLE_DH_FOR_ORDINARY_SOURCE=str(args.dh == "off").lower(), MATTMC_GRAPHICS_AUDIT="false",
               MATTMC_GRAPHICS_SUBSYSTEM_BENCHMARK="false")
    attachment_paths = None
    if args.diagnostic_attachments:
        attachment_paths = {"attachments": str(args.output / "frame-attachments"),
                            "request": str(args.output / "frame-attachment-request.properties"),
                            "source": str(args.output / "source-pass-diagnostics")}
        env.update(MATTMC_GRAPHICS_AUDIT="true", MATTMC_RUST_SELECTED_SOURCE_EXECUTION="1",
                   MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_DIR=attachment_paths["attachments"],
                   MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_REQUEST=attachment_paths["request"],
                   MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR=attachment_paths["source"],
                   MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_FINAL_ONLY="0",
                   MATTMC_RUST_SOURCE_DH_DEPTH_COVERAGE="1",
                   MATTMC_RUST_SELECTED_SOURCE_CAPTURE_STAGE="shader-pack-trace",
                   MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_UNIFORM_RECEIPT="1")
    if args.backend == "rust-vulkan":
        env["MATTMC_TRACE_GPU_OVERLAP"] = "1"
    env["JAVA_TOOL_OPTIONS"] = (env.get("JAVA_TOOL_OPTIONS", "")
        + " -Dmattmc.dev.graphicsFrameBenchmark=false -Dmattmc.dev.deterministicCameraCapture=false")
    if args.diagnostic_attachments:
        env["JAVA_TOOL_OPTIONS"] += " -Dmattmc.dev.graphicsAuditSliceMetrics=true"
    if args.dh == "on" and args.backend == "rust-vulkan":
        env["JAVA_TOOL_OPTIONS"] += " -Dmattmc.dev.rustGalDistantHorizons.traceExecution=true"
    receipt = {"schema": "terrain-flight-observation-v1", "status": "starting", "backend": args.backend,
               "shaders": args.shaders, "shader_pack_sha256_requested": expected_archive_sha256, "dh": args.dh, "dh_source": dh_source,
               "pose": args.pose, "motion": args.motion, "first_turn_idle_seconds": args.first_turn_idle_seconds,
               "diagnostic_attachment_paths": attachment_paths,
               "source_run": str(args.run_source), "command": command, "disk_preflight": disk_preflight,
               "limitations": "Normal input and unregistered window video; inspect actual F3 positions, chunk work and capture logs. No FPS, registered pixel parity or flicker-absence acceptance.",
               "events": []}
    path = args.output / "flight.json"
    def write():
        path.write_text(json.dumps(receipt, indent=2) + "\n")
    write()
    controller = None
    observer = None
    with (args.output / "engine.log").open("w") as log:
        engine_process = subprocess.Popen(command, cwd=args.repo, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            deadline = time.monotonic() + 95
            client = None
            while time.monotonic() < deadline:
                if engine_process.poll() is not None:
                    raise RuntimeError("capture engine stopped before flight")
                client = client_pid(args.output)
                if client:
                    game_log = client[1] / "logs/latest.log"
                    if game_log.is_file() and "joined the game" in game_log.read_text(errors="replace"):
                        target = find_linux_client_window_id(client[0])
                        proof = window_capture_provenance("linux", target, client[0]) if target else {}
                        runtime = "".join(p.read_text(errors="replace") for p in (args.output / "capture").glob("runClient_*.log"))
                        if input_ready(proof, args.backend, args.shaders, runtime):
                            receipt["input_ready"] = {"wall_ns": time.time_ns(), "window": proof}
                            break
                time.sleep(.2)
            else:
                raise RuntimeError("isolated client did not become ready for ordinary input within 95 seconds")
            if args.motion != "first-turn":
                time.sleep(5)
            pid, directory = client
            receipt["effective_configuration"] = {**(verify_dh_enabled(directory) if args.dh == "on" else verify_dh_disabled(directory)),
                                                    **verify_shader_configuration(directory, args.shaders, expected_archive_sha256)}
            controller = GameInput(pid)
            receipt.update(pid=pid, game_dir=str(directory), process_start_ticks=process_start(pid),
                           window=controller.target, provenance=window_capture_provenance("linux", controller.target, pid))
            teleport = ("tp @s 150.5 125 530.5 105 15", ("Teleported", "150.500000", "125.000000", "530.500000"))
            if args.pose == "buried":
                teleport = ("tp @s 167.5 38 553.5 0 0", ("Teleported", "167.500000", "38.000000", "553.500000"))
            elif args.pose == "forest":
                teleport = ("tp @s 150.5 95 530.5 285 25", ("Teleported", "150.500000", "95.000000", "530.500000"))
            setup = (
                ("gamemode spectator", ("Set own game mode to Spectator Mode",)),
                teleport,
                ("gamerule doDaylightCycle false", ("doDaylightCycle", "false")),
                ("gamerule doWeatherCycle false", ("doWeatherCycle", "false")),
                ("weather clear", ("Set the weather to clear",)),
                ("time set 6000", ("Set the time to 6000",)),
            )
            for text, tokens in (() if args.motion == "first-turn" else setup):
                offset = len(game_log.read_text(errors="replace"))
                controller.command(text)
                deadline = time.monotonic() + 4
                while time.monotonic() < deadline:
                    acknowledgment = command_acknowledgment(game_log, offset, tokens)
                    if acknowledgment:
                        break
                    controller.check()
                    time.sleep(.1)
                else:
                    raise RuntimeError("setup command acknowledgment missing: " + text)
                receipt["events"].append({"command": text, "wall_ns": time.time_ns(),
                                           "acknowledgment": acknowledgment})
                write()
            if args.motion != "first-turn":
                time.sleep(12)  # Let chat fade without hiding ordinary chunk rendering.
            if args.dh == "on" and args.backend == "rust-vulkan" and args.motion != "first-turn":
                # The saved camera may face only near terrain. Position it first;
                # require DH submissions at the actual observation pose.
                deadline = time.monotonic() + 30
                logs = list((args.output / "capture").glob("runClient_*.log"))
                if len(logs) != 1:
                    raise RuntimeError("expected one DH native runtime log")
                ready_after = receipt["events"][-1]["wall_ns"] // 1_000_000
                while time.monotonic() < deadline:
                    samples = dh_execution_samples(logs[0].read_text(errors="replace"))
                    if any(sample["wall_ms"] >= ready_after for sample in samples):
                        break
                    controller.check()
                    time.sleep(.2)
                else:
                    raise RuntimeError("no successful native DH segment submission at the observation pose")
            if args.motion != "first-turn":
                controller.tap("F3")
                time.sleep(1)
            def screenshot(name):
                controller.check()
                target = args.output / f"{name}.png"
                if not capture_linux_x11_window(controller.target, target):
                    raise RuntimeError("exact game-window screenshot failed")
                receipt["events"].append({"screenshot": target.name, "wall_ns": time.time_ns()})
                write()
            if args.motion == "first-turn":
                if args.first_turn_idle_seconds:
                    idle_start = time.time_ns()
                    idle_end = time.monotonic() + args.first_turn_idle_seconds
                    while time.monotonic() < idle_end:
                        controller.check()
                        time.sleep(.05)
                    receipt["events"].append({"first_turn_idle_seconds": args.first_turn_idle_seconds,
                                              "wall_start_ns": idle_start, "wall_ns": time.time_ns()})
                    write()
                receipt["events"].append(wait_for_first_world_view(controller, args.output))
                controller.look_relative(1, 0)
                receipt["events"].append({"mouse_prime_dx": 1, "wall_ns": time.time_ns()})
                write()
            else:
                screenshot("start-f3")
            controller.tap("F3")
            if args.motion == "first-turn":
                receipt["events"].append(wait_for_debug_hidden(controller, args.output))
                write()
            else:
                time.sleep(1)
            observations = (("forward", "w"), ("back", "s"), ("revisit", "w")) if args.motion == "first-turn" else (("forward", "w"), ("back", "s"))
            receipt["observation_names"] = [name for name, _ in observations]
            for direction, key in observations:
                video = [sys.executable, str(Path(__file__).with_name("CaptureWindowVideo.py")),
                         "--pid", str(pid), "--output", str(args.output / direction), "--fps", "60" if args.motion == "translate" else "30",
                         "--seconds", str(args.seconds), "--region", "0,260,900,350" if args.motion == "translate" else "0,0,1280,720"]
                with (args.output / f"{direction}-observer.log").open("w") as observer_log:
                    def start_observer():
                        nonlocal observer
                        observer = subprocess.Popen(video, stdout=observer_log, stderr=subprocess.STDOUT,
                                                    start_new_session=True)
                    if args.motion == "first-turn":
                        start_observer()
                        time.sleep(1)
                        turn_start = time.time_ns()
                        dx = -100 if direction == "back" else 100
                        for _ in range(12):
                            controller.look_relative(dx, 0)
                            time.sleep(.025)
                        receipt["events"].append({"mouse_dx": dx * 12, "mouse_dy": 0,
                                                  "wall_start_ns": turn_start, "wall_ns": time.time_ns()})
                        write()
                    elif args.motion == "turn":
                        turn = f"tp @s 150.5 95 530.5 {105 if direction == 'forward' else 285} 25"
                        offset = len(game_log.read_text(errors="replace"))
                        def before_submit():
                            start_observer()
                            time.sleep(1)
                            receipt["events"].append({"turn": turn, "submit_start_wall_ns": time.time_ns()})
                            write()
                        controller.command(turn, before_submit=before_submit)
                        deadline = time.monotonic() + 4
                        acknowledgment = None
                        while time.monotonic() < deadline:
                            acknowledgment = command_acknowledgment(game_log, offset, teleport[1])
                            if acknowledgment:
                                break
                            controller.check()
                            time.sleep(.1)
                        if not acknowledgment:
                            raise RuntimeError("turn command acknowledgment missing")
                        receipt["events"][-1].update(acknowledgment=acknowledgment, wall_ns=time.time_ns())
                    else:
                        start_observer()
                        controller.key(key, True)
                        receipt["events"].append({"key_down": key, "wall_ns": time.time_ns()})
                    hold_end = time.monotonic() + args.seconds + 1
                    next_turn = time.monotonic() + 2
                    last_turn = hold_end - 2
                    try:
                        while time.monotonic() < hold_end:
                            controller.check()
                            if args.motion == "travel-turn" and next_turn <= time.monotonic() < last_turn:
                                turn_start = time.time_ns()
                                for _ in range(12):
                                    controller.look_relative(100, 0)
                                    time.sleep(.025)
                                receipt["events"].append({"mouse_dx": 1200, "mouse_dy": 0,
                                                          "wall_start_ns": turn_start, "wall_ns": time.time_ns()})
                                write()
                                next_turn += 2
                            time.sleep(.05)
                    finally:
                        if args.motion not in ("turn", "first-turn"):
                            controller.key(key, False)
                    if args.motion not in ("turn", "first-turn"):
                        receipt["events"].append({"key_up": key, "wall_ns": time.time_ns()})
                    if observer.wait(timeout=25):
                        raise RuntimeError("flight video observer failed")
                    observer = None
                time.sleep(2)
                controller.tap("F3")
                time.sleep(1)
                screenshot(direction + "-f3")
                controller.tap("F3")
                time.sleep(1)
            receipt["status"] = "observation_complete_requires_position_review"
            write()
        except Exception as error:
            receipt.update(status="failed", error=str(error))
            write()
            if engine.suffix == ".py" and engine_process.poll() is None:
                # Current's engine has a signal handler that owns verified
                # client cleanup. The Frozen shell retains its bounded timer.
                engine_process.terminate()
        finally:
            try:
                if observer is not None:
                    stop_observer(observer)
            except Exception as error:
                receipt.update(status="failed", observer_cleanup_error=str(error))
            try:
                if controller is not None:
                    errors = controller.close()
                    if errors:
                        receipt.update(status="failed", input_cleanup_errors=errors)
            except Exception as error:
                receipt.update(status="failed", input_cleanup_error=str(error))
            # The existing engine owns termination of its verified client.
            try:
                receipt["engine_exit_code"] = engine_process.wait(timeout=210)
            except subprocess.TimeoutExpired:
                engine_process.terminate()
                try:
                    receipt["engine_exit_code"] = engine_process.wait(timeout=25)
                except subprocess.TimeoutExpired:
                    engine_process.kill()
                    receipt["engine_exit_code"] = engine_process.wait(timeout=10)
                receipt.update(status="failed", error="capture engine exceeded bounded runtime")
            try:
                if client_pid(args.output):
                    receipt.update(status="failed", error="capture engine left an isolated client alive")
                    receipt["orphan_client_cleanup"] = stop_orphaned_client(args.output)
                receipt["runtime"] = runtime_outcome(args.output / "capture", receipt["engine_exit_code"])
                if client_pid(args.output):
                    raise RuntimeError("capture engine left an isolated game client alive")
                if args.dh == "on" and args.backend == "rust-vulkan":
                    logs = list((args.output / "capture").glob("runClient_*.log"))
                    if len(logs) != 1:
                        raise RuntimeError("expected one DH native runtime log")
                    samples = dh_execution_samples(logs[0].read_text(errors="replace"))
                    receipt["dh_submission_samples"] = samples
                    receipt["dh_submission_samples_per_video"] = {}
                    for direction in receipt.get("observation_names", []) if receipt["status"] == "observation_complete_requires_position_review" else ():
                        video = json.loads((args.output / direction / "video.json").read_text())
                        receipt["dh_submission_samples_per_video"][direction] = dh_submissions_for_video(samples, video)
            except Exception as error:
                receipt.update(status="failed", runtime_error=str(error))
            write()
    print(json.dumps({"receipt": str(path), "status": receipt["status"]}))
    return 0 if receipt["status"] == "observation_complete_requires_position_review" else 1


if __name__ == "__main__":
    raise SystemExit(main())
