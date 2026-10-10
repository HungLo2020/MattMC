#!/usr/bin/env python3
"""Unattended look-around performance run for Current.

Copies the player's game directory (options, configs, VoxelMap data and one
world) into an isolated recording folder, launches Current straight into that
world, and sweeps the camera continuously (yaw side to side, pitch up and
down) for a fixed time with the minimap and the copied settings. The session
recorder, JFR, GPU/CPU samplers and the per-thread stall sampler all run, and
the client exits by itself. The original game directory is never modified.

    python3 DevUtils/tests/rendering/RunScriptedLook.py --label baseline
    python3 DevUtils/PerfAudit/Recording.py summarize artifacts/recordings/<name>
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import signal
import subprocess
import sys
import threading
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO / "DevUtils" / "PerfAudit"))
import Recording  # noqa: E402

# Settings forced in the copied options.txt. Everything else (render distance,
# FPS limit, graphics, mipmaps, minimap, Distant Horizons) is the player's own.
FORCED_OPTIONS = {
    "enableVsync": "false",
    "pauseOnLostFocus": "false",
    "fullscreen": "false",
}
COPIED_ENTRIES = ("options.txt", "config", "voxelmap", "resourcepacks")
WORLD_NAME = "ScriptedLook"


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--source-run", type=Path, default=REPO / "run", help="game directory to copy (default: run/)")
    parser.add_argument("--world", default="New World", help="save folder name under <source-run>/saves")
    parser.add_argument("--seconds", type=float, default=150.0, help="sweep length once in the world")
    parser.add_argument("--yaw-amplitude", type=float, default=150.0)
    parser.add_argument("--yaw-period", type=float, default=2.4)
    parser.add_argument("--pitch-amplitude", type=float, default=60.0)
    parser.add_argument("--pitch-period", type=float, default=1.6)
    parser.add_argument("--width", type=int, default=1920)
    parser.add_argument("--height", type=int, default=1012)
    parser.add_argument("--label", default="scripted-look")
    parser.add_argument("--perf-after", type=float, default=None,
                        help="also record a flat `perf` CPU profile starting this many seconds after entering the world")
    parser.add_argument("--perf-seconds", type=float, default=30.0)
    parser.add_argument("--jvm-arg", action="append", default=[], help="extra client JVM argument, written --jvm-arg=-Dname=value (repeatable)")
    parser.add_argument("--keep-game-dir", action="store_true", help="keep the copied game directory afterwards")
    parser.add_argument("--timeout", type=float, default=900.0, help="seconds to wait for the client in total")
    return parser.parse_args(argv)


def patch_options(text: str, forced: dict[str, str]) -> str:
    """Overrides `key:value` lines in options.txt, appending missing keys."""
    seen = set()
    out = []
    for line in text.splitlines():
        key = line.split(":", 1)[0]
        if key in forced:
            out.append(f"{key}:{forced[key]}")
            seen.add(key)
        else:
            out.append(line)
    out += [f"{key}:{value}" for key, value in forced.items() if key not in seen]
    return "\n".join(out) + "\n"


def prepare_game_dir(source: Path, world: str, game: Path) -> None:
    game.mkdir(parents=True)
    for entry in COPIED_ENTRIES:
        path = source / entry
        if path.is_dir():
            shutil.copytree(path, game / entry)
        elif path.is_file():
            shutil.copy2(path, game / entry)
    world_dir = source / "saves" / world
    if not (world_dir / "level.dat").is_file():
        raise SystemExit(f"ERROR: {world_dir} is not a world (no level.dat)")
    # One whitespace-free name: Gradle splits -PmattmcRunArgs on whitespace.
    shutil.copytree(world_dir, game / "saves" / WORLD_NAME)
    options = game / "options.txt"
    options.write_text(patch_options(options.read_text(encoding="utf-8") if options.is_file() else "", FORCED_OPTIONS),
                       encoding="utf-8")


def client_command(args: argparse.Namespace, game: Path, out: Path) -> list[str]:
    jvm = [
        "-Dmattmc.dev.scriptedCamera=true",
        f"-Dmattmc.dev.scriptedCamera.seconds={args.seconds}",
        f"-Dmattmc.dev.scriptedCamera.yawAmplitude={args.yaw_amplitude}",
        f"-Dmattmc.dev.scriptedCamera.yawPeriod={args.yaw_period}",
        f"-Dmattmc.dev.scriptedCamera.pitchAmplitude={args.pitch_amplitude}",
        f"-Dmattmc.dev.scriptedCamera.pitchPeriod={args.pitch_period}",
        *args.jvm_arg,
    ]
    return [
        str(REPO / "gradlew"), "-PmattmcRustProfile=release", "runClient",
        f"-PmattmcRunGameDir={game}",
        f"-PmattmcRunArgs=--quickPlaySingleplayer {WORLD_NAME} --width {args.width} --height {args.height}",
        f"-PmattmcRecordDir={out}",
        "-PmattmcRunJvmArgs=" + " ".join(jvm),
    ]


def wait_in_world(out: Path, timeout: float) -> bool:
    deadline = time.monotonic() + timeout
    seconds = out / "seconds.csv"
    while time.monotonic() < deadline:
        if seconds.is_file() and any(line.rstrip().endswith(",true") for line in seconds.read_text().splitlines()[1:]):
            return True
        time.sleep(2.0)
    return False


def perf_profile(out: Path, after: float, length: float, timeout: float) -> None:
    if not wait_in_world(out, timeout):
        return
    time.sleep(after)
    pid = Recording._client_pid()
    perf = shutil.which("perf")
    if pid is None or perf is None:
        return
    with open(out / "perf-flat.log", "w", encoding="utf-8") as log:
        subprocess.run([perf, "record", "-F", "999", "-p", str(pid), "-o", str(out / "perf-flat.data"),
                        "--", "sleep", str(length)], stdout=log, stderr=subprocess.STDOUT, check=False)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    out = Recording.new_recording_dir(REPO, args.label)
    game = out / "game"
    prepare_game_dir(args.source_run.resolve(), args.world, game)
    environment = os.environ.copy()
    # Symbol-bearing release library (same code, readable by perf); Gradle keys
    # its native build on these, so keep them identical between runs.
    environment.setdefault("CARGO_PROFILE_RELEASE_STRIP", "none")
    environment.setdefault("CARGO_PROFILE_RELEASE_DEBUG", "line-tables-only")
    environment.setdefault("MATTMC_RUST_VULKAN_GPU_TIMESTAMPS", "true")
    meta = {
        "harness": "scripted-look-v1", "world": args.world, "seconds": args.seconds,
        "yaw": [args.yaw_amplitude, args.yaw_period], "pitch": [args.pitch_amplitude, args.pitch_period],
        "window": [args.width, args.height], "forced_options": FORCED_OPTIONS,
        "git_head": subprocess.run(["git", "-C", str(REPO), "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip(),
        "git_dirty": bool(subprocess.run(["git", "-C", str(REPO), "status", "--porcelain"], capture_output=True, text=True).stdout.strip()),
        "memory_before": Recording.memory_snapshot(),
    }
    print(f"Scripted look-around run: {out}", flush=True)
    samplers = Recording.Samplers(out)
    stalls = Recording.StallSampler(out)
    samplers.start()
    stalls.start()
    perf_thread = None
    if args.perf_after is not None:
        perf_thread = threading.Thread(target=perf_profile, args=(out, args.perf_after, args.perf_seconds, args.timeout),
                                       daemon=True)
        perf_thread.start()
    command = client_command(args, game, out)
    code = None
    with open(out / "console.log", "w", encoding="utf-8", errors="replace") as log:
        process = subprocess.Popen(command, cwd=REPO, env=environment, stdout=log, stderr=subprocess.STDOUT)
        try:
            code = process.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            pid = Recording._client_pid()
            if pid is not None:
                os.kill(pid, signal.SIGTERM)
            code = process.wait(timeout=120)
    samplers.stop()
    stalls.stop()
    if perf_thread is not None:
        perf_thread.join(timeout=5)
    meta["gradle_exit"] = code
    meta["memory_after"] = Recording.memory_snapshot()
    meta["sweep_completed"] = "scripted camera sweep complete" in (out / "console.log").read_text(errors="replace")
    (out / "harness.json").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    if not args.keep_game_dir:
        shutil.rmtree(game, ignore_errors=True)
    print(Recording.summarize(out), end="", flush=True)
    print(f"Recording saved to {out}; sweep completed: {meta['sweep_completed']}", flush=True)
    return 0 if meta["sweep_completed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
