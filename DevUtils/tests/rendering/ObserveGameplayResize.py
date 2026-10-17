#!/usr/bin/env python3
"""Exercise three actual X11 resizes on an isolated gameplay benchmark client.

Run beside Gameplay.py using a fresh artifact root. This is a transition
diagnostic; screenshots and timings are not registered Frozen parity evidence.
"""
from __future__ import annotations

import argparse
import ctypes
import ctypes.util
import json
from pathlib import Path
import subprocess
import sys
import time

from PIL import Image

from ObserveGameplayVideo import client_pid, read_status
from CaptureWindowVideo import process_start
from capture_runner import (capture_linux_x11_window, find_linux_client_window_id,
                            window_capture_provenance)

EXTENTS = ((960, 540), (1600, 900), (1280, 720))


def request_resize(target: str, extent: tuple[int, int]) -> None:
    x11 = ctypes.CDLL(ctypes.util.find_library('X11'))
    x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
    x11.XOpenDisplay.restype = ctypes.c_void_p
    x11.XResizeWindow.argtypes = [ctypes.c_void_p, ctypes.c_ulong,
                                 ctypes.c_uint, ctypes.c_uint]
    x11.XSync.argtypes = [ctypes.c_void_p, ctypes.c_int]
    x11.XCloseDisplay.argtypes = [ctypes.c_void_p]
    display = x11.XOpenDisplay(None)
    if not display:
        raise RuntimeError('no X11 display')
    try:
        x11.XResizeWindow(display, int(target, 16), *extent)
        x11.XSync(display, False)
    finally:
        x11.XCloseDisplay(display)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact-root', type=Path, required=True)
    parser.add_argument('--mode', action='append', required=True)
    parser.add_argument('--timeout', type=float, default=360)
    args = parser.parse_args()
    if not 1 <= args.timeout <= 1200:
        parser.error('timeout must be 1..1200 seconds')
    if any(Path(mode).name != mode or mode in ('.', '..') for mode in args.mode):
        parser.error('mode must be a directory name')
    root = args.artifact_root.resolve()
    for mode in args.mode:
        deadline = time.monotonic() + args.timeout
        status_path = None
        while time.monotonic() < deadline:
            paths = list((root / mode / 'gameplay').glob('run-*/capture/graphics_frame_benchmark_*.json'))
            if len(paths) > 1:
                raise RuntimeError('use a fresh root with exactly one run per mode')
            if paths:
                status_path = paths[0]
                status = read_status(status_path)
                if status.get('status') in ('failed', 'complete'):
                    raise RuntimeError(f'{mode} terminated before resize')
                if (status.get('settledFrameIndex', -1) >= 0
                        and status.get('framesSeenIncludingSettleWarmup', 0)
                        - status['settledFrameIndex'] >= 600):
                    break
            time.sleep(.2)
        else:
            raise RuntimeError(f'{mode} did not start its camera path')
        directories = list(status_path.parent.glob('game_dir_*'))
        if len(directories) != 1:
            raise RuntimeError('expected one isolated game directory')
        pid = client_pid(directories[0])
        target = find_linux_client_window_id(pid) if pid else None
        if not pid or not target:
            raise RuntimeError('no exact client-owned window')
        identity = process_start(pid)
        output = root / f'{mode}-resize'
        output.mkdir(exist_ok=False)
        report = {'schema': 'gameplay-window-resize-v1', 'status': 'failed',
                  'client_pid': pid, 'process_start_ticks': identity, 'steps': [],
                  'limits': 'External transition observations only. No exact-frame image parity, first-resized-frame, performance or flicker acceptance.'}
        try:
            for step, extent in enumerate(EXTENTS, 1):
                before = read_status(status_path)
                provenance = window_capture_provenance('linux', target, pid)
                if (process_start(pid) != identity or provenance['status'] != 'verified'
                        or before.get('status') in ('failed', 'complete')):
                    raise RuntimeError('client/window no longer belongs to the live benchmark')
                receipt = {'step': step, 'requested_extent': extent,
                           'wall_request_ns': time.time_ns(), 'provenance': provenance,
                           'benchmark_before': before}
                report['steps'].append(receipt)
                request_resize(target, extent)
                step_deadline = min(deadline, time.monotonic() + 15)
                image_path = output / f'step-{step}.png'
                while time.monotonic() < step_deadline:
                    status = read_status(status_path)
                    if status.get('status') in ('failed', 'complete'):
                        raise RuntimeError('benchmark terminated during resize')
                    window = status.get('window', {})
                    if (window.get('width'), window.get('height')) == extent:
                        if capture_linux_x11_window(target, image_path):
                            with Image.open(image_path) as image:
                                if image.size == extent:
                                    break
                    time.sleep(.2)
                else:
                    raise RuntimeError(f'client did not acknowledge extent {extent}')
                # Let normal rendering advance through the new resource extent.
                time.sleep(2)
                after = read_status(status_path)
                provenance_after = window_capture_provenance('linux', target, pid)
                if (process_start(pid) != identity or provenance_after['status'] != 'verified'
                        or after.get('status') in ('failed', 'complete')
                        or after.get('framesSeenIncludingSettleWarmup', 0)
                        <= before.get('framesSeenIncludingSettleWarmup', 0)):
                    raise RuntimeError('normal gameplay did not advance after resize')
                receipt.update(wall_observed_ns=time.time_ns(), benchmark_after=after,
                               provenance_after=provenance_after, screenshot=image_path.name)
                print(f'{mode}: observed {extent[0]}x{extent[1]}', flush=True)
            report['status'] = 'complete'
        except (OSError, RuntimeError, ValueError, subprocess.SubprocessError) as error:
            report['error'] = str(error)
            raise
        finally:
            (output / 'resize.json').write_text(json.dumps(report, indent=2) + '\n')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
