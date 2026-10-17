#!/usr/bin/env python3
"""Observe a bounded video after a live gameplay benchmark starts its camera path.

Run alongside Gameplay.py in a fresh artifact root. Matching uses the actual
KnotClient PID's working directory; no client is launched or terminated here.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import subprocess
import sys
import time


def read_status(path: Path) -> dict:
    try:
        return json.loads(path.read_text())
    except (OSError, ValueError):
        return {}


def client_pid(game_dir: Path) -> int | None:
    matches = []
    for process in Path('/proc').iterdir():
        if not process.name.isdigit():
            continue
        try:
            args = (process / 'cmdline').read_bytes().split(b'\0')
            if (b'net.fabricmc.loader.impl.launch.knot.KnotClient' in args
                    and (process / 'cwd').resolve() == game_dir.resolve()):
                matches.append(int(process.name))
        except OSError:
            continue
    if len(matches) > 1:
        raise RuntimeError('multiple clients own the exact game directory')
    return matches[0] if matches else None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact-root', type=Path, required=True)
    parser.add_argument('--mode', action='append', required=True)
    parser.add_argument('--timeout', type=float, default=360)
    parser.add_argument('--fps', type=int, choices=(30,60,120), default=120)
    parser.add_argument('--seconds', type=float, default=10)
    parser.add_argument('--minimum-path-frames', type=int, default=0)
    parser.add_argument('--region', default='0,260,900,350')
    parser.add_argument('--unmanaged-extent', help='temporary isolated X11 extent, WIDTH,HEIGHT')
    args = parser.parse_args()
    if not 1 <= args.timeout <= 1200 or not 1 <= args.seconds <= 10 or not 0 <= args.minimum_path_frames <= 12000:
        parser.error('timeout must be 1..1200 seconds and video 1..10 seconds')
    if any(Path(mode).name != mode or mode in ('.','..') for mode in args.mode):
        parser.error('mode must be a directory name')
    extent = None
    if args.unmanaged_extent:
        try:
            extent = tuple(map(int, args.unmanaged_extent.split(',')))
        except ValueError:
            parser.error('unmanaged extent must contain two integers')
        if len(extent) != 2 or not 320 <= extent[0] <= 3840 or not 240 <= extent[1] <= 2160:
            parser.error('unmanaged extent must be within 320x240 through 3840x2160')
    args.artifact_root = args.artifact_root.resolve()
    for mode in args.mode:
        deadline = time.monotonic() + args.timeout
        status_path = None
        status = {}
        while time.monotonic() < deadline:
            paths = list((args.artifact_root / mode / 'gameplay').glob('run-*/capture/graphics_frame_benchmark_*.json'))
            if len(paths) > 1:
                raise RuntimeError('use a fresh artifact root with exactly one run per mode')
            if paths:
                status_path = paths[0]
                artifact = status_path.parent.parent / 'graphics_audit_artifact.json'
                if artifact.exists():
                    raise RuntimeError(f'{mode} harness finished before observation; inspect {artifact}')
                status = read_status(status_path)
                if status.get('status') in ('failed','complete'):
                    raise RuntimeError(f'{mode} became terminal before observation: {status.get("failureReason")}')
                if (status.get('settledFrameIndex',-1) >= 0
                        and status.get('framesSeenIncludingSettleWarmup',0)-status['settledFrameIndex'] >= args.minimum_path_frames):
                    break
            time.sleep(.2)
        else:
            raise RuntimeError(f'{mode} did not start the settled camera path within the bounded wait')
        directories = list(status_path.parent.glob('game_dir_*'))
        if len(directories) != 1:
            raise RuntimeError('expected one isolated gameplay directory')
        pid = client_pid(directories[0])
        if pid is None:
            raise RuntimeError('settled status has no live client owning its game directory')
        output = args.artifact_root / f'{mode}-video'
        if output.exists():
            raise RuntimeError('video output already exists')
        print(f'{mode}: observing PID {pid}, path={status.get("cameraPath")}', flush=True)
        transition = {}
        target = None
        identity = None
        original_extent = None
        try:
            if extent:
                from CaptureWindowVideo import process_start
                from capture_runner import find_linux_client_window_id, window_capture_provenance
                from x11_large_window import request_window
                target = find_linux_client_window_id(pid)
                if not target or window_capture_provenance('linux', target, pid).get('status') != 'verified':
                    raise RuntimeError('no exact client-owned window for the size request')
                identity = process_start(pid)
                window = status.get('window', {})
                original_extent = (window.get('width'), window.get('height'))
                if any(not isinstance(value, int) or value <= 0 for value in original_extent):
                    raise RuntimeError('benchmark has no valid original window extent')
                transition.update(requested_extent=extent, original_extent=original_extent,
                                  wall_request_ns=time.time_ns(), window=target, pid=pid,
                                  process_start_ticks=identity)
                request_window(target, extent, unmanaged=True)
                size_deadline = min(deadline, time.monotonic()+15)
                while time.monotonic() < size_deadline:
                    status = read_status(status_path)
                    if status.get('status') in ('failed', 'complete'):
                        raise RuntimeError('benchmark terminated during the size request')
                    window = status.get('window', {})
                    if (window.get('width'), window.get('height')) == extent:
                        transition['wall_acknowledged_ns'] = time.time_ns()
                        break
                    time.sleep(.2)
                else:
                    raise RuntimeError('client did not acknowledge the unmanaged extent')
            command = [sys.executable,str(Path(__file__).with_name('CaptureWindowVideo.py')),
                '--pid',str(pid),'--output',str(output),'--fps',str(args.fps),
                '--seconds',str(args.seconds),'--region',args.region]
            if target:
                command.extend(['--window-id', target])
            result = subprocess.run(command,timeout=args.seconds+60)
        finally:
            if transition:
                try:
                    if (process_start(pid) != identity
                            or window_capture_provenance('linux', target, pid).get('observedWindowPid') != pid):
                        raise RuntimeError('client/window identity changed before restoration')
                    request_window(target, original_extent, unmanaged=False)
                    transition['wall_restore_requested_ns'] = time.time_ns()
                except (OSError, RuntimeError, ValueError) as error:
                    transition['restore_error'] = str(error)
                output.mkdir(exist_ok=True)
                (output/'window-transition.json').write_text(json.dumps(transition,indent=2)+'\n')
                if transition.get('restore_error'):
                    raise RuntimeError(transition['restore_error'])
        if not output.exists():
            raise RuntimeError(f'{mode} video produced no artifact directory; inspect its process output')
        (output / 'benchmark-context-before.json').write_text(json.dumps(status,indent=2)+'\n')
        after = read_status(status_path)
        (output / 'benchmark-context-after.json').write_text(json.dumps(after,indent=2)+'\n')
        if result.returncode:
            video = read_status(output / 'video.json')
            raise RuntimeError(f'{mode} observation failed: {video.get("error", "inspect video.json and process output")}')
        if after.get('settledFrameIndex') != status.get('settledFrameIndex'):
            raise RuntimeError(f'{mode} benchmark restarted while recording')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (OSError,RuntimeError,subprocess.SubprocessError) as error:
        print(error,file=sys.stderr)
        raise SystemExit(1)
