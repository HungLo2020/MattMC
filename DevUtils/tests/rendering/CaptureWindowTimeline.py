#!/usr/bin/env python3
"""Observe a bounded X11 client-window timeline without changing rendering.

Launch through the existing gameplay/capture harness first. Supply its live
client PID; use identical stationary scenes and regions on Current and Frozen.
Pixel changes are diagnostic evidence, not an automatic flicker/parity verdict.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import sys
import time

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "Common"))
from capture_runner import (capture_linux_x11_window, find_linux_client_window_id,
                            linux_x11_window_is_viewable, window_capture_provenance)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pid", type=int, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--frames", type=int, default=120)
    parser.add_argument("--interval", type=float, default=0.05)
    parser.add_argument("--region", help="x,y,width,height; omit to observe the whole window")
    args = parser.parse_args()
    if args.pid <= 0:
        parser.error("pid must identify a live client process")
    if not 2 <= args.frames <= 240 or not math.isfinite(args.interval) or not 0.01 <= args.interval <= 5:
        parser.error("frames must be 2–240 and interval must be 0.01–5 seconds")
    try:
        region = tuple(map(int, args.region.split(','))) if args.region else None
    except ValueError:
        parser.error("region must contain four comma-separated integers")
    if region and (len(region) != 4 or min(region[:2]) < 0 or min(region[2:]) <= 0):
        parser.error("region must be nonnegative x,y and positive width,height")
    target = find_linux_client_window_id(args.pid)
    if not target or not linux_x11_window_is_viewable(target):
        parser.error("the specified live client has no viewable X11 window")
    provenance = window_capture_provenance("linux", target, args.pid)
    if provenance.get("status") != "verified":
        parser.error("window PID provenance could not be verified: " + str(provenance))
    args.output.mkdir(parents=True, exist_ok=False)
    receipts = []
    previous = low = high = None
    start = time.monotonic()
    try:
        for index in range(args.frames):
            if not Path(f"/proc/{args.pid}").exists() or not linux_x11_window_is_viewable(target):
                raise RuntimeError("client/window stopped during observation")
            filename = args.output / f"frame-{index:04d}.png"
            if not capture_linux_x11_window(target, filename):
                raise RuntimeError("the exact client-window capture failed")
            with Image.open(filename) as captured:
                current = np.asarray(captured.convert("RGB"), dtype=np.uint8)
            if region:
                x, y, width, height = region
                if x + width > current.shape[1] or y + height > current.shape[0]:
                    raise RuntimeError("region exceeds the captured client size")
                current = current[y:y+height, x:x+width]
            if previous is not None and current.shape != previous.shape:
                raise RuntimeError("window size changed; use a separate transition observation")
            receipt = {"index": index, "screenshot": filename.name,
                       "elapsed_seconds": time.monotonic() - start,
                       "wall_time_ns": time.time_ns()}
            if previous is not None:
                delta = np.abs(current.astype(np.int16) - previous.astype(np.int16))
                receipt.update(mean_rgb_abs=delta.mean(axis=(0, 1)).tolist(),
                               fraction_any_channel_over_32=float((delta.max(axis=2) > 32).mean()))
            low = current.copy() if low is None else np.minimum(low, current)
            high = current.copy() if high is None else np.maximum(high, current)
            previous = current
            receipts.append(receipt)
            remaining = start + (index + 1) * args.interval - time.monotonic()
            if remaining > 0:
                time.sleep(remaining)
        Image.fromarray(high - low).save(args.output / "temporal-range.png")
        status = "complete"
    except (RuntimeError, OSError) as error:
        status = "failed"
        receipts.append({"error": str(error)})
    report = {"schema": "mattmc-window-timeline-v1", "status": status,
              "window_provenance": provenance, "region": region,
              "requested_frames": args.frames, "captured_frames": sum('index' in r for r in receipts),
              "interval_seconds": args.interval, "frames": receipts,
              "interpretation": "Observed presented window pixels; animation, scene changes and sampling aliasing require interpretation."}
    (args.output / "timeline.json").write_text(json.dumps(report, indent=2) + "\n")
    print(args.output / "timeline.json")
    return 0 if status == "complete" else 1


if __name__ == "__main__":
    raise SystemExit(main())
