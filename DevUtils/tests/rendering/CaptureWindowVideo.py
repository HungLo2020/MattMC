#!/usr/bin/env python3
"""Record a short lossless X11 window crop for temporal rendering diagnosis.

Use a live gameplay client PID and the same stationary region on Current and
Frozen. The observer adds overhead; its timing is not performance evidence.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import re
from pathlib import Path
import shutil
import subprocess
import sys
import time

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "Common"))
from capture_runner import (capture_linux_x11_window, find_linux_client_window_id,
                            linux_x11_window_is_viewable, window_capture_provenance)


def process_start(pid: int) -> str:
    # /proc comm can contain spaces or parentheses; starttime is field 22.
    return Path(f"/proc/{pid}/stat").read_text().rsplit(") ", 1)[1].split()[19]


def window_extent(target: str) -> tuple[int, int]:
    info = subprocess.run(['xwininfo', '-id', target, '-stats'], check=True,
                          capture_output=True, text=True, timeout=5).stdout
    width = re.search(r'^\s*Width:\s*(\d+)\s*$', info, re.MULTILINE)
    height = re.search(r'^\s*Height:\s*(\d+)\s*$', info, re.MULTILINE)
    if not width or not height or min(int(width[1]), int(height[1])) <= 0:
        raise RuntimeError('X11 did not report a valid client extent')
    return int(width[1]), int(height[1])


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pid", type=int, required=True)
    parser.add_argument("--window-id", help="previously identified X11 window; exact PID ownership is still required")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--region", required=True, help="x,y,width,height inside the client window")
    parser.add_argument("--fps", type=int, default=60)
    parser.add_argument("--seconds", type=float, default=8)
    args = parser.parse_args()
    try:
        region = tuple(map(int, args.region.split(",")))
    except ValueError:
        parser.error("region must contain four integers")
    if len(region) != 4 or min(region[:2]) < 0 or min(region[2:]) <= 0:
        parser.error("region needs nonnegative x,y and positive width,height")
    if region[2] * region[3] > 1280 * 720:
        parser.error("observe at most 1280x720 pixels per frame")
    if args.pid <= 0 or args.fps not in (30, 60, 120):
        parser.error("provide a live PID and fps 30, 60 or 120")
    if not math.isfinite(args.seconds) or not 1 <= args.seconds <= 10:
        parser.error("seconds must be within 1..10")
    ffmpeg = shutil.which("ffmpeg")
    ffprobe = shutil.which("ffprobe")
    if not ffmpeg or not ffprobe or not os.environ.get("DISPLAY"):
        parser.error("ffmpeg, ffprobe and an X11 DISPLAY are required")
    target = args.window_id or find_linux_client_window_id(args.pid)
    provenance = window_capture_provenance("linux", target, args.pid) if target else {}
    if provenance.get("status") != "verified" or not linux_x11_window_is_viewable(target):
        parser.error("the exact client PID must own a viewable X11 window")
    try:
        start_identity = process_start(args.pid)
    except OSError:
        parser.error("client exited before observation")
    args.output.mkdir(parents=True, exist_ok=False)
    report = {"schema": "mattmc-window-video-v1", "status": "failed",
              "window_provenance": provenance, "region": region,
              "requested_fps": args.fps, "requested_seconds": args.seconds,
              "wall_start_ns": time.time_ns(), "frames": [],
              "interpretation": "Presented window observations; animation, tearing and sampling aliasing need interpretation. Observer timing is not performance evidence."}
    decoder = None
    try:
        extent = window_extent(target)
        report['client_extent'] = extent
        before = args.output / "window-before.png"
        if not capture_linux_x11_window(target, before):
            raise RuntimeError("initial exact-window capture failed")
        with Image.open(before) as image:
            screenshot_extent = image.size
        report['screenshot_extent'] = screenshot_extent
        x, y, width, height = region
        if x + width > extent[0] or y + height > extent[1]:
            raise RuntimeError("region exceeds client extent")
        if x + width > screenshot_extent[0] or y + height > screenshot_extent[1]:
            raise RuntimeError("region exceeds the visible screenshot extent")
        video = args.output / "window-crop.mkv"
        with (args.output / "ffmpeg.log").open("w") as log:
            command = [ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "warning", "-n",
                       "-f", "x11grab", "-window_id", target, "-framerate", str(args.fps),
                       "-draw_mouse", "0", "-grab_x", str(x), "-grab_y", str(y),
                       "-video_size", f"{width}x{height}",
                       "-i", os.environ["DISPLAY"], "-t", str(args.seconds),
                       "-c:v", "ffv1",
                       "-level", "3", "-threads", "1", "-pix_fmt", "bgr0",
                       "-fps_mode", "passthrough", str(video)]
            report["capture_command"] = command
            report["source_region_only"] = True
            report["ffmpeg_wall_start_ns"] = time.time_ns()
            subprocess.run(command, check=True, stdout=log, stderr=log,
                           timeout=args.seconds + 20)
            report["ffmpeg_wall_end_ns"] = time.time_ns()
        report["wall_end_ns"] = time.time_ns()
        after_provenance = window_capture_provenance("linux", target, args.pid)
        report["window_provenance_after"] = after_provenance
        if (process_start(args.pid) != start_identity
                or after_provenance.get("status") != "verified"
                or not linux_x11_window_is_viewable(target)):
            raise RuntimeError("client/window identity changed during observation")
        after = args.output / "window-after.png"
        if not capture_linux_x11_window(target, after):
            raise RuntimeError("final exact-window capture failed")
        with Image.open(after) as image:
            if image.size != screenshot_extent or window_extent(target) != extent:
                raise RuntimeError("client resized; use a separate transition observation")
        previous = low = high = None
        frame_bytes = width * height * 3
        maximum_frames = math.ceil(args.seconds * args.fps) + 4
        probe = subprocess.run([ffprobe, "-v", "error", "-select_streams", "v:0",
                                "-show_entries", "frame=best_effort_timestamp_time",
                                "-of", "csv=p=0", str(video)],
                               check=True, capture_output=True, text=True, timeout=15)
        timestamps = [float(value.strip()) for value in probe.stdout.splitlines() if value.strip()]
        if (not 2 <= len(timestamps) <= maximum_frames
                or any(not math.isfinite(t) for t in timestamps)
                or any(b <= a for a, b in zip(timestamps, timestamps[1:]))):
            raise RuntimeError("video timestamps are missing, unordered or exceed the frame bound")
        with (args.output / "decode.log").open("w") as log:
            decoder = subprocess.Popen([ffmpeg, "-nostdin", "-v", "error", "-i", str(video),
                                        "-threads", "1", "-pix_fmt", "rgb24", "-fps_mode", "passthrough",
                                        "-f", "rawvideo", "-"],
                                       stdout=subprocess.PIPE, stderr=log)
            while True:
                raw = decoder.stdout.read(frame_bytes)
                if not raw:
                    break
                if len(raw) != frame_bytes or len(report["frames"]) >= maximum_frames:
                    raise RuntimeError("truncated frame or video exceeds bounded frame count")
                current = np.frombuffer(raw, dtype=np.uint8).reshape(height, width, 3)
                index = len(report["frames"])
                if index >= len(timestamps):
                    raise RuntimeError("decoded frame has no timestamp")
                receipt = {"index": index, "video_timestamp_seconds": timestamps[index]}
                if previous is not None:
                    delta = np.abs(current.astype(np.int16) - previous.astype(np.int16))
                    receipt.update(mean_rgb_abs=delta.mean(axis=(0, 1)).tolist(),
                                   fraction_any_channel_over_32=float((delta.max(axis=2) > 32).mean()))
                else:
                    Image.fromarray(current).save(args.output / "first-frame.png")
                low = current.copy() if low is None else np.minimum(low, current)
                high = current.copy() if high is None else np.maximum(high, current)
                previous = current.copy()
                report["frames"].append(receipt)
            if decoder.wait(timeout=10) != 0:
                raise RuntimeError("lossless video decode failed")
        count = len(report["frames"])
        if count != len(timestamps):
            raise RuntimeError("timestamp count does not match decoded frames")
        if count < args.seconds * args.fps * .8:
            raise RuntimeError("too few video frames for the requested sampling rate")
        Image.fromarray(previous).save(args.output / "last-frame.png")
        Image.fromarray(high - low).save(args.output / "temporal-range.png")
        report.update(status="complete", captured_frames=count, client_extent=extent,
                      video=video.name, video_bytes=video.stat().st_size)
    except (OSError, RuntimeError, ValueError, subprocess.SubprocessError) as error:
        report["error"] = str(error)
    finally:
        if decoder is not None:
            if decoder.poll() is None:
                decoder.kill()
            decoder.wait(timeout=10)
            decoder.stdout.close()
        (args.output / "video.json").write_text(json.dumps(report, indent=2) + "\n")
    print(args.output / "video.json")
    return 0 if report["status"] == "complete" else 1


if __name__ == "__main__":
    raise SystemExit(main())
