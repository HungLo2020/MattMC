#!/usr/bin/env python3
"""Observe an ordinary resource reload after RunTerrainFlight releases movement input."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess
import sys
import time

from x11_game_input import GameInput
from capture_runner import capture_linux_x11_window, window_capture_provenance


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--flight-output", type=Path, required=True)
    args = parser.parse_args()
    root = args.flight_output.resolve(strict=True)
    current = Path(__file__).resolve().parents[3]
    if not root.is_relative_to(current / "artifacts/graphics-captures"):
        parser.error("flight output must be inside Current's graphics artifact tree")
    output = root / "resource-reload"
    output.mkdir(exist_ok=False)
    path = output / "reload.json"
    report = {"schema": "ordinary-flight-resource-reload-v1", "status": "waiting", "events": [],
              "limitations": "Ordinary input and unregistered window observations; no exact first-reload-frame, pixel parity, throughput or long-run acceptance."}
    def write():
        path.write_text(json.dumps(report, indent=2) + "\n")
    write()
    controller = observer = None
    try:
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            try:
                flight = json.loads((root / "flight.json").read_text())
            except (FileNotFoundError, json.JSONDecodeError):
                time.sleep(.2)
                continue
            if flight["status"] == "failed" or flight.get("runtime") is not None:
                raise RuntimeError("flight stopped before supplemental reload")
            if flight["status"] == "observation_complete_requires_position_review":
                break
            time.sleep(.2)
        else:
            raise RuntimeError("flight movement did not finish within the reload deadline")
        time.sleep(2)  # The flight's finally block releases keys and restores focus.
        controller = GameInput(flight["pid"])
        if controller.identity != flight["process_start_ticks"]:
            raise RuntimeError("flight PID was replaced")
        report.update(pid=flight["pid"], process_start_ticks=controller.identity,
                      provenance=window_capture_provenance("linux", controller.target, controller.pid))
        game_log = Path(flight["game_dir"]) / "logs/latest.log"
        offset = game_log.stat().st_size
        def screenshot(name):
            controller.check()
            if not capture_linux_x11_window(controller.target, output / name):
                raise RuntimeError("exact game-window screenshot failed")
            report["events"].append({"screenshot": name, "wall_ns": time.time_ns()})
            write()
        screenshot("before.png")
        with (output / "observer.log").open("w") as log:
            observer = subprocess.Popen([sys.executable, str(Path(__file__).with_name("CaptureWindowVideo.py")),
                "--pid", str(controller.pid), "--output", str(output / "video"), "--fps", "30", "--seconds", "8",
                "--region", "0,0,1280,720"],
                stdout=log, stderr=subprocess.STDOUT)
            time.sleep(.5)
            controller.key("F3", True)
            try:
                time.sleep(.1)
                controller.tap("t")
            finally:
                controller.key("F3", False)
            report["events"].append({"keys": "F3+t", "wall_ns": time.time_ns()})
            write()
            elapsed = 0
            for delay in (2, 3, 5, 5):
                time.sleep(delay)
                elapsed += delay
                screenshot(f"after-{elapsed}s.png")
            tail = game_log.read_bytes()[offset:].decode(errors="replace")
            reload_lines = [line for line in tail.splitlines() if "Reloading ResourceManager:" in line]
            report["acknowledgment"] = reload_lines
            write()
            if len(reload_lines) != 1:
                raise RuntimeError("expected exactly one acknowledged ordinary resource reload")
            if observer.wait(timeout=15):
                raise RuntimeError("reload video observer failed")
            observer = None
        report.update(status="observed_requires_visual_and_runtime_review")
    except Exception as error:
        report.update(status="failed", error=str(error))
    finally:
        if observer is not None:
            if observer.poll() is None:
                observer.terminate()
            try:
                observer.wait(timeout=10)
            except subprocess.TimeoutExpired:
                observer.kill()
                observer.wait(timeout=10)
        if controller is not None:
            errors = controller.close()
            if errors:
                report.update(status="failed", input_cleanup_errors=errors)
        write()
    print(json.dumps({"receipt": str(path), "status": report["status"]}))
    return 0 if report["status"] == "observed_requires_visual_and_runtime_review" else 1


if __name__ == "__main__":
    raise SystemExit(main())
