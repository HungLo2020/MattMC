#!/usr/bin/env python3
"""Measure ordinary saved-view gameplay, including visible minimap and terrain travel.

The existing engines own isolated world copies and process cleanup. This driver
never selects a benchmark camera or waits for DH/terrain publication to settle.
"""
from __future__ import annotations

import argparse
from contextlib import closing
import json
import os
from pathlib import Path
import shlex
import shutil
import sqlite3
import struct
import subprocess
import sys
import time

import RunTerrainFlight as flight
import RunValidation as validation
from x11_game_input import GameInput

REPO = Path(__file__).resolve().parents[3]


def frame_summary(path: Path, start_ms: float, end_ms: float) -> dict:
    data = path.read_bytes()
    if len(data) < 5:
        raise RuntimeError("incomplete frame recording")
    count, overflow = struct.unpack_from('>I?', data)
    if overflow or len(data) != 5 + 24 * count:
        raise RuntimeError("overflowed or truncated frame recording")
    samples = []
    for epoch_ms, interval_ns, tick_ns in struct.iter_unpack('>qqq', data[5:]):
        if start_ms <= epoch_ms < end_ms and interval_ns > 0:
            samples.append(interval_ns)
    if not samples:
        raise RuntimeError("no completed frames in observation")
    ordered = sorted(samples)
    percentile = lambda q: ordered[min(len(ordered) - 1, int((len(ordered) - 1) * q))] / 1e6
    return {'frames': len(samples), 'fps_from_frame_intervals': 1e9 * len(samples) / sum(samples),
            'mean_frame_ms': sum(samples) / len(samples) / 1e6,
            'p95_frame_ms': percentile(.95), 'p99_frame_ms': percentile(.99),
            'max_frame_ms': max(samples) / 1e6,
            'frames_over_50ms': sum(n > 50_000_000 for n in samples),
            'frames_over_100ms': sum(n > 100_000_000 for n in samples)}


def validate_samples(samples: list[dict], minimap: str, motion: bool = False) -> None:
    if not samples or any(not x.get('world') or x.get('screen') != 'none'
            or x.get('debug_visible') or x.get('throttle') != 'NONE'
            or (minimap == 'visible' and not x.get('minimap_allowed'))
            or x.get('overflow') or x.get('minimap_hidden') != (minimap == 'hidden') for x in samples):
        raise RuntimeError("observation includes a menu, hidden/incorrect map, throttle, or invalid world state")
    if motion and abs(samples[-1]['x'] - samples[0]['x']) + abs(samples[-1]['z'] - samples[0]['z']) < 1:
        raise RuntimeError("travel input did not move the player")


def verify_source(source: Path, world: str, needs_frozen: bool) -> dict:
    save = source / 'saves' / world
    if not source.is_dir() or not (save / 'level.dat').is_file():
        raise ValueError("run source must contain the requested saved world")
    formats = {}
    for database in save.rglob('DistantHorizons.sqlite'):
        with closing(sqlite3.connect(database.resolve().as_uri() + '?mode=ro', uri=True)) as connection:
            counts = connection.execute('SELECT DataFormatVersion,COUNT(*) FROM FullData GROUP BY DataFormatVersion').fetchall()
        if needs_frozen and any(version not in (1, 2, 3) for version, count in counts):
            raise ValueError("Frozen cannot read this DH cache; supply a shared compatible copied run source")
        formats[str(database.relative_to(source))] = counts
    return {'run_source': str(source), 'world': world, 'dh_database_formats': formats}


def build_agent(output: Path, jdk: Path) -> Path:
    classes = output / 'observer-classes'
    classes.mkdir()
    hooks = output / 'GameplayFrameHooks.java'
    hooks.write_text("public final class GameplayFrameHooks {\n"
        " public static volatile Runnable begin, end, draw;\n"
        " public static void beginFrame() { Runnable r = begin; if (r != null) r.run(); }\n"
        " public static void endFrame() { Runnable r = end; if (r != null) r.run(); }\n"
        " public static void recordDhDraw() { Runnable r = draw; if (r != null) r.run(); }\n"
        "}\n")
    subprocess.run([str(jdk / 'javac'), '--add-modules', 'jdk.attach', '-d', str(classes),
                    str(hooks), str(Path(__file__).with_name('GameplayPerformanceObserver.java'))], check=True)
    subprocess.run([str(jdk / 'jar'), 'cf', str(output / 'observer-hooks.jar'),
                    '-C', str(classes), 'GameplayFrameHooks.class'], check=True)
    (classes / 'GameplayFrameHooks.class').unlink()  # Only bootstrap may expose this helper.
    manifest = output / 'observer-manifest.txt'
    manifest.write_text('Manifest-Version: 1.0\nAgent-Class: GameplayPerformanceObserver\n'
                        'Main-Class: GameplayPerformanceObserver\nCan-Retransform-Classes: true\n\n')
    jar = output / 'observer.jar'
    subprocess.run([str(jdk / 'jar'), 'cfm', str(jar), str(manifest), '-C', str(classes), '.'], check=True)
    return jar


def run_client(args, side: str, output: Path, jar: Path, jdk: Path) -> dict:
    output.mkdir()
    flight.artifact_retention.ensure_marker(output)
    (output / 'minimap-mode.txt').write_text(args.minimap)
    repo = args.repo if side == 'current' else args.frozen_repo
    backend = 'rust-vulkan' if side == 'current' else 'opengl'
    _, command = flight.launch_command(repo, output, backend, args.shaders, args.world)
    client_args = [f'--quickPlaySingleplayer={args.world}', '--width', str(args.width),
                   '--height', str(args.height), f'enableShaders={str(args.shaders == "on").lower()}']
    if args.fullscreen:
        client_args.append('--fullscreen')
    command[command.index('--client-args') + 1] = shlex.join(client_args)
    command[command.index('--max-secs') + 1] = str(args.startup_timeout + 3 * args.seconds + 60)
    command[command.index('--dump-secs') + 1] = str(args.startup_timeout + 3 * args.seconds + 50)
    command[command.index('--validation') + 1] = 'off'
    env = dict(os.environ)
    env.update({'MATTMC_CAPTURE_RUN_SOURCE': str(args.run_source),
                'MATTMC_CAPTURE_PRESERVE_ISOLATED_GAME_DIR': 'true',
                'MATTMC_GRAPHICS_TOOL_INTERNAL': '1', 'MATTMC_GRAPHICS_AUDIT': 'false',
                'MATTMC_CAPTURE_WORLD': args.world, 'MATTMC_CAPTURE_MAX_FPS': '260',
                'MATTMC_CAPTURE_RENDER_DISTANCE': str(args.render_distance),
                'MATTMC_CAPTURE_SIMULATION_DISTANCE': str(args.render_distance),
                'MATTMC_CAPTURE_WORLD_WIDTH': str(args.width), 'MATTMC_CAPTURE_WORLD_HEIGHT': str(args.height),
                'MATTMC_CAPTURE_DH_KEEP_FOG': 'true',
                'MATTMC_CAPTURE_DISABLE_DH_FOR_PERF': str(args.dh == 'off').lower(),
                'MATTMC_CAPTURE_DISABLE_DH_FOR_ORDINARY_SOURCE': str(args.dh == 'off').lower(),
                'CARGO_PROFILE_RELEASE_STRIP': 'none', 'CARGO_PROFILE_RELEASE_DEBUG': 'line-tables-only',
                'JAVA_TOOL_OPTIONS': '-Dmattmc.dev.graphicsFrameBenchmark=false -Dmattmc.dev.deterministicCameraCapture=false',
                '_JAVA_OPTIONS': f'-XX:-UseG1GC -XX:+UseZGC -Xms1G -Xmx{args.heap_gb}G '
                    '-XX:+UseCompactObjectHeaders -XX:ZUncommitDelay=5 -XX:Tier3InvocationThreshold=100 '
                    '-XX:Tier4InvocationThreshold=600 -XX:Tier4MinInvocationThreshold=300 -XX:Tier4CompileThreshold=700'})
    row = {'side': side, 'command': command, 'status': 'starting', 'spans': [], 'memory': []}
    controller = None
    log = (output / 'driver.log').open('w')
    process = subprocess.Popen(command, cwd=repo, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    try:
        deadline = time.monotonic() + args.startup_timeout
        while time.monotonic() < deadline:
            found = flight.client_pid(output)
            if found:
                pid, game_dir = found
                try:
                    controller = GameInput(pid)
                    break
                except RuntimeError:
                    pass
            if process.poll() is not None:
                raise RuntimeError("capture engine exited before the game window")
            time.sleep(.5)
        if controller is None:
            raise RuntimeError("no owned game window within startup timeout")
        controller.close()
        controller = None
        row.update(pid=pid, game_dir=str(game_dir))
        attach_env = {k: v for k, v in env.items() if k not in ('_JAVA_OPTIONS', 'JAVA_TOOL_OPTIONS')}
        subprocess.run([str(jdk / 'java'), '--add-modules', 'jdk.attach', '-jar', str(jar),
                        str(pid), str(jar), str(output)], env=attach_env, check=True, timeout=30,
                       stdout=log, stderr=subprocess.STDOUT)

        def latest():
            error = output / 'observer.error'
            if error.exists():
                raise RuntimeError(error.read_text())
            path = output / 'runtime.jsonl'
            if not path.exists():
                return {}
            lines = path.read_text().splitlines()
            return json.loads(lines[-1]) if lines else {}

        # Wait only for actual gameplay and the requested HUD setting. DH queues
        # and initial chunk work are deliberately not settlement prerequisites.
        while time.monotonic() < deadline:
            current = latest()
            if current.get('world') and current.get('screen') == 'none' and current.get('minimap_hidden') == (args.minimap == 'hidden'):
                break
            if process.poll() is not None:
                raise RuntimeError("capture engine exited during world loading")
            time.sleep(.5)
        else:
            raise RuntimeError("world/HUD input readiness timeout")
        controller = GameInput(pid)
        row['first_world_ms'] = time.time_ns() / 1e6
        for name in ('entry', 'stationary', 'travel'):
            controller.check()
            first = time.time_ns() / 1e6
            if name == 'travel':
                controller.key('w', True)
            try:
                for second in range(args.seconds):
                    controller.check()
                    if second % 5 == 0:
                        controller.look_relative(1 if second % 10 == 0 else -1, 0)
                    row['memory'].append({'time_ms': time.time_ns() / 1e6, 'meminfo': Path('/proc/meminfo').read_text()})
                    time.sleep(1)
            finally:
                if name == 'travel':
                    controller.key('w', False)
            last = time.time_ns() / 1e6
            samples = [json.loads(line) for line in (output / 'runtime.jsonl').read_text().splitlines()]
            samples = [s for s in samples if first <= s['time_ms'] < last]
            validate_samples(samples, args.minimap, name == 'travel')
            row['spans'].append({'name': name, 'start_ms': first, 'end_ms': last, 'runtime_samples': samples})
            print(side, name, 'displayed FPS', round(sum(s['fps'] for s in samples) / len(samples), 1), flush=True)
        (output / 'stop').touch()
        deadline = time.monotonic() + 10
        while not (output / 'frames.bin').exists() and time.monotonic() < deadline:
            time.sleep(.1)
        for span in row['spans']:
            span['frames'] = frame_summary(output / 'frames.bin', span['start_ms'], span['end_ms'])
            samples = span['runtime_samples']
            if 'presented_frames' in samples[0]:
                span['native_presented_fps'] = (samples[-1]['presented_frames'] - samples[0]['presented_frames']) * 1000 / (samples[-1]['time_ms'] - samples[0]['time_ms'])
        if args.dh == 'on':
            samples = [s for span in row['spans'] for s in span['runtime_samples']]
            counter = 'worldLodFramesExecuted' if side == 'current' else 'dh_draw_calls'
            if samples[-1].get(counter, 0) <= samples[0].get(counter, 0):
                raise RuntimeError("DH was requested but no measured DH draws were observed")
        row['status'] = 'complete'
    except Exception as error:
        row['status'] = 'failed'
        row['error'] = repr(error)
    finally:
        (output / 'stop').touch()
        if controller:
            controller.close()
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=25)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
        row['engine_exit_code'] = process.returncode
        row['termination'] = 'bounded owned cleanup; not a normal-exit stability gate'
        row['owned_orphans_cleaned'] = validation.stop_leftover_clients(output)
        log.close()
        row['client_health'] = validation.read_client_health(output)
        if row['client_health']['exceptions'] or row['client_health']['terrain_failures']:
            row['status'] = 'failed'
            row['error'] = 'client reported exceptions or terrain failures; inspect retained logs'
        (output / 'result.json').write_text(json.dumps(row, indent=2) + '\n')
    return row


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=REPO)
    parser.add_argument('--frozen-repo', type=Path, default=validation.FROZEN_REPO)
    parser.add_argument('--run-source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--world', default='Origin')
    parser.add_argument('--side', choices=('current', 'frozen', 'both'), default='both')
    parser.add_argument('--shaders', choices=('on', 'off'), default='off')
    parser.add_argument('--dh', choices=('on', 'off'), default='on')
    parser.add_argument('--minimap', choices=('visible', 'hidden'), default='visible')
    parser.add_argument('--seconds', type=int, choices=range(5, 121), default=30)
    parser.add_argument('--startup-timeout', type=int, default=120)
    parser.add_argument('--heap-gb', type=int, choices=range(2, 17), default=8)
    parser.add_argument('--width', type=int, default=1920)
    parser.add_argument('--height', type=int, default=1080)
    parser.add_argument('--render-distance', type=int, choices=range(2, 33), default=12)
    parser.add_argument('--fullscreen', action=argparse.BooleanOptionalAction, default=True)
    parser.add_argument('--jdk', type=Path, default=Path(shutil.which('java') or 'java').resolve().parent)
    args = parser.parse_args(argv)
    for key in ('repo', 'frozen_repo', 'run_source', 'output', 'jdk'):
        setattr(args, key, getattr(args, key).resolve())
    if args.world in ('.', '..') or '/' in args.world or '\\' in args.world:
        parser.error('world must be a save directory name')
    if not 320 <= args.width <= 7680 or not 240 <= args.height <= 4320 or not 30 <= args.startup_timeout <= 600:
        parser.error('window size/startup timeout outside bounded limits')
    provenance = verify_source(args.run_source, args.world, args.side != 'current')
    if args.output.exists():
        parser.error('output already exists; use a new label')
    flight.flight_disk_preflight(args.output)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / '.keep').touch()
    jar = build_agent(args.output, args.jdk)
    results = []
    for side in (('current', 'frozen') if args.side == 'both' else (args.side,)):
        results.append(run_client(args, side, args.output / side, jar, args.jdk))
        (args.output / 'summary.json').write_text(json.dumps({'protocol': 'ordinary-gameplay-v1',
            'source': provenance, 'controls': {k: str(v) if isinstance(v, Path) else v for k, v in vars(args).items()},
            'timing': 'same JDK 25 runTick entry/exit agent on both sides; retains all completed frames, no readiness resets',
            'results': results}, indent=2) + '\n')
    return 0 if all(r['status'] == 'complete' for r in results) else 1


if __name__ == '__main__':
    raise SystemExit(main())
