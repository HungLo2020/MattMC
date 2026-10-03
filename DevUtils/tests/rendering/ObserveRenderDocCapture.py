#!/usr/bin/env python3
"""Capture up to three live Frozen OpenGL particle frames through RenderDoc.

Run beside a fresh Capture.py invocation with --renderdoc-capture and a visible
terrain-particle fixture. Only the exact isolated KnotClient PID is captured.
Replay is a separate step after the authoritative harness has terminated.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time

CONFIG_ENV = 'MATTMC_RENDERDOC_OBSERVER_CONFIG'
# qrenderdoc executes --python in its console namespace without __file__.
SCRIPT_PATH = (Path(json.loads(os.environ[CONFIG_ENV])['script_path'])
               if CONFIG_ENV in os.environ else Path(__file__).resolve())
sys.path.insert(0, str(SCRIPT_PATH.parents[2] / 'Common'))
from client_memory import java_main


def isolated_pid(game_dir: Path, proc_root: Path = Path('/proc')) -> tuple[int, str] | None:
    matches = []
    for process in proc_root.iterdir():
        if not process.name.isdigit():
            continue
        try:
            before = (process / 'stat').read_text().rsplit(')', 1)[1].split()[19]
            if ((process / 'exe').resolve(strict=True).name != 'java'
                    or (process / 'cwd').resolve(strict=True) != game_dir.resolve()):
                continue
            payload = (process / 'cmdline').read_bytes()
            if len(payload) > 1024 * 1024:
                continue
            args = payload.split(b'\0')
            values = [arg.decode() for arg in args]
            if java_main(values) != 'net.fabricmc.loader.impl.launch.knot.KnotClient':
                continue
            paths = [values[i + 1] for i, arg in enumerate(values[:-1]) if arg == '--gameDir']
            paths.extend(arg.split('=', 1)[1] for arg in values if arg.startswith('--gameDir='))
            if any(Path(path).resolve() != game_dir.resolve() for path in paths):
                continue
            # Gradle's --args can replace its default --gameDir argument. The
            # isolated working directory still identifies that exact client.
            ticks = (process / 'stat').read_text().rsplit(')', 1)[1].split()[19]
            if ticks == before:
                matches.append((int(process.name), ticks))
        except (OSError, UnicodeError, IndexError):
            continue
    if len(matches) > 1:
        raise RuntimeError('more than one client owns the isolated game directory')
    return matches[0] if matches else None


def observe(config: dict) -> None:
    import renderdoc as rd

    root = Path(config['root'])
    output = Path(config['output'])
    deadline = time.monotonic() + config['timeout']
    target = None
    report = {'schema': 'frozen-renderdoc-observation-v2', 'status': 'failed', 'captures': [],
              'limits': 'At most three diagnostic frames; no normal performance or image-parity acceptance.'}
    try:
        status_path = None
        identity = None
        while time.monotonic() < deadline:
            artifacts = list((root / config['mode'] / 'capture').glob(
                'run-*/graphics_audit_artifact.json'))
            if artifacts:
                raise RuntimeError('harness finalized before visible particle observation')
            paths = list((root / config['mode'] / 'capture').glob(
                'run-*/capture/deterministic_camera_capture_*.json'))
            if len(paths) > 1:
                raise RuntimeError('use one run in a fresh artifact root')
            if paths:
                status_path = paths[0]
                try:
                    status = json.loads(status_path.read_text())
                except (OSError, ValueError):
                    time.sleep(.1)
                    continue
                producer = {key: status.get(key) for key in (
                    'status', 'backend', 'renderedFrameIndex', 'terrainParticleFixture',
                    'worldMenuFixture', 'shaderPack', 'shaderEnabled', 'window')}
                report['last_producer_status'] = producer
                if status.get('status') in ('complete', 'failed'):
                    raise RuntimeError('client terminated capture before observation')
                fixture = status.get('terrainParticleFixture') or {}
                world = status.get('worldMenuFixture') or {}
                directories = list(status_path.parent.glob('game_dir_*'))
                if (fixture.get('complete') and not fixture.get('hidden', True)
                        and world.get('worldPresent')
                        and len(directories) == 1):
                    identity = isolated_pid(directories[0])
                    if identity:
                        break
            time.sleep(.1)
        if not identity:
            raise RuntimeError('visible particle producer did not become ready')
        report.update(client_pid=identity[0], process_start_ticks=identity[1],
                      producer_status=producer, status_path=str(status_path))
        while time.monotonic() < deadline and target is None:
            if isolated_pid(directories[0]) != identity:
                raise RuntimeError('client exited before RenderDoc target connection')
            ident = 0
            for _ in range(16):
                ident = rd.EnumerateRemoteTargets('', ident)
                if not ident:
                    break
                candidate = rd.CreateTargetControl('', ident, 'MattMC read-only observation', False)
                if candidate is None:
                    continue
                if candidate.Connected() and candidate.GetPID() == identity[0]:
                    target = candidate
                    report.update(target_ident=ident, target_name=target.GetTarget())
                    break
                candidate.Shutdown()
            if target is None:
                time.sleep(.1)
        if target is None or isolated_pid(directories[0]) != identity:
            raise RuntimeError('no RenderDoc target for the exact live client')
        # Poll announcements so GetAPI reflects the initialized GL context.
        while time.monotonic() < deadline and 'OpenGL' not in target.GetAPI():
            target.ReceiveMessage(None)
            if not target.Connected() or isolated_pid(directories[0]) != identity:
                raise RuntimeError('client disconnected before GL observation')
            time.sleep(.05)
        if 'OpenGL' not in target.GetAPI():
            raise RuntimeError('target has no OpenGL context')
        # Frozen writes its running receipt at setup, then at capture completion;
        # renderedFrameIndex is not a live counter. Let the real world render
        # after producer setup, without modifying Frozen or its readiness gate.
        dwell_end = min(deadline, time.monotonic() + 2)
        while time.monotonic() < dwell_end:
            if isolated_pid(directories[0]) != identity:
                raise RuntimeError('client changed during producer dwell')
            time.sleep(.1)
        report.update(api=target.GetAPI(), capture_request_ns=time.time_ns())
        target.TriggerCapture(1)
        while time.monotonic() < deadline:
            message = target.ReceiveMessage(None)
            if message.type == rd.TargetControlMessageType.NewCapture:
                capture = message.newCapture
                if not capture.local or 'OpenGL' not in str(capture.api):
                    raise RuntimeError('capture is not the local OpenGL target')
                path = Path(capture.path).resolve()
                path.relative_to(root)
                if not path.is_file():
                    raise RuntimeError('local capture file is missing')
                report['captures'].append({'capture_path': str(path),
                                           'capture_frame': capture.frameNumber,
                                           'captured_ns': time.time_ns()})
                print('Captured exact Frozen client frame: ' + str(path), flush=True)
                if len(report['captures']) == 3:
                    report['status'] = 'complete'
                    return
                # Sample later world frames too; the running producer receipt
                # may have been written before the loading screen disappeared.
                time.sleep(2)
                if isolated_pid(directories[0]) != identity:
                    raise RuntimeError('client changed before the next bounded capture')
                target.TriggerCapture(1)
            if not target.Connected():
                raise RuntimeError('client disconnected before capture receipt')
            time.sleep(.05)
        raise RuntimeError('RenderDoc capture did not finish within its observation bound')
    except Exception as error:
        report['error'] = str(error)
        raise
    finally:
        if target is not None:
            target.Shutdown()
        output.mkdir(parents=True, exist_ok=True)
        (output / 'observation.json').write_text(json.dumps(report, indent=2) + '\n')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact-root', type=Path, required=True)
    parser.add_argument('--mode', choices=['frozen-opengl-shaders-on', 'frozen-opengl-shaders-off'],
                        default='frozen-opengl-shaders-on')
    parser.add_argument('--timeout', type=int, default=300)
    parser.add_argument('--qrenderdoc', type=Path,
                        default=Path(__file__).resolve().parents[2] / '.cache/tools/renderdoc/bin/qrenderdoc')
    args = parser.parse_args()
    if not 1 <= args.timeout <= 600:
        parser.error('timeout must be 1..600 seconds')
    root = args.artifact_root.resolve()
    output = root / 'renderdoc-observation'
    output.mkdir(parents=True, exist_ok=False)
    config = {'root': str(root), 'mode': args.mode, 'timeout': args.timeout,
              'output': str(output), 'script_path': str(SCRIPT_PATH)}
    env = dict(os.environ, **{CONFIG_ENV: json.dumps(config)})
    # Official Linux builds embed their matching Python and RenderDoc module.
    return subprocess.run([str(args.qrenderdoc.resolve()), '--python', str(Path(__file__).resolve())],
                          env=env, timeout=args.timeout + 15).returncode


if __name__ == '__main__':
    if CONFIG_ENV in os.environ:
        code = 0
        try:
            observe(json.loads(os.environ[CONFIG_ENV]))
        except Exception as error:
            print(str(error), file=sys.stderr, flush=True)
            code = 1
        # --python runs before the viewer opens; no GUI replay/presenter is needed.
        os._exit(code)
    raise SystemExit(main())
