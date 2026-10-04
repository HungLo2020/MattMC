#!/usr/bin/env python3
"""Retain two correlated Rust attachment sets during an ordinary first mouse turn."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "Common"))
from client_memory import java_main


def read_json(path: Path) -> dict | None:
    try:
        return json.loads(path.read_text())
    except (FileNotFoundError, json.JSONDecodeError):
        return None


def verify_client(flight: dict) -> None:
    process = Path("/proc") / str(flight["pid"])
    before = (process / "stat").read_text().rsplit(")", 1)[1].split()[19]
    command = (process / "cmdline").read_bytes().split(b"\0")
    if ((process / "exe").resolve(strict=True).name != "java"
            or java_main([arg.decode() for arg in command]) != "net.fabricmc.loader.impl.launch.knot.KnotClient"
            or (process / "cwd").resolve(strict=True) != Path(flight["game_dir"]).resolve()
            or before != flight["process_start_ticks"]
            or (process / "stat").read_text().rsplit(")", 1)[1].split()[19] != before):
        raise RuntimeError("isolated flight client identity changed")


def verify_capture(manifest: dict, correlation: dict, properties: dict, requested_frame: int) -> None:
    for key in ("gameplay_frame_id", "correlation_id", "deterministic_rendered_frame_index"):
        if manifest[key] != correlation[key] or manifest[key] != int(properties[key]):
            raise RuntimeError("attachment identity differs from its promoted request/presentation")
    if (manifest["gal_submission_id"] != correlation["gal_submission_id"]
            or not correlation["same_acquired_presented_image"]
            or not 0 <= manifest["gameplay_frame_id"] - requested_frame <= 8):
        raise RuntimeError("attachment submission/presentation or bounded promotion differs")


def verify_source_receipt(receipt: dict, manifest: dict, *, uniform: bool = False,
                          execution: bool = False) -> None:
    submission = "gal_submission_id" if uniform else "submission_id"
    identities = {"frame_id": "gameplay_frame_id", submission: "gal_submission_id"}
    if not execution:
        identities["correlation_id"] = "correlation_id"
    if any(receipt.get(key) != manifest[value] for key, value in identities.items()):
        raise RuntimeError("source stage/uniform/execution belongs to a different captured submission")


def observe(root: Path, later_seconds: int) -> None:
    output = root / "attachment-observation"
    output.mkdir(exist_ok=False)
    report = {"schema": "ordinary-flight-attachment-observation-v1", "status": "waiting", "captures": [],
              "limitations": "Two externally armed completed GPU readbacks alter timing; no registered parity, exact latency or FPS acceptance. The later sample is not assumed settled."}
    def write():
        (output / "observation.json").write_text(json.dumps(report, indent=2) + "\n")
    deadline = time.monotonic() + 155
    try:
        write()
        first = None
        while time.monotonic() < deadline:
            flight = read_json(root / "flight.json")
            if flight and (flight["status"] == "failed" or flight.get("runtime")):
                raise RuntimeError("flight stopped before the first attachment request")
            moves = [event for event in flight.get("events", []) if "mouse_dx" in event] if flight else []
            if moves:
                first = moves[0]
                break
            time.sleep(.01)
        if first is None:
            raise RuntimeError("first mouse turn not observed within the deadline")
        expected = {"attachments": root / "frame-attachments",
                    "request": root / "frame-attachment-request.properties",
                    "source": root / "source-pass-diagnostics"}
        if (flight["backend"] != "rust-vulkan" or flight["shaders"] != "on"
                or flight.get("diagnostic_attachment_paths") != {key: str(path) for key, path in expected.items()}):
            raise RuntimeError("flight must explicitly enable its owned diagnostic attachments")
        verify_client(flight)
        report.update(client_pid=flight["pid"], process_start_ticks=flight["process_start_ticks"], first_mouse_turn=first)
        for label, offset in (("early", 0), ("later", later_seconds)):
            while time.time_ns() < first["wall_ns"] + offset * 1_000_000_000:
                if time.monotonic() >= deadline:
                    raise RuntimeError("attachment request deadline exceeded")
                verify_client(flight)
                time.sleep(.01)
            verify_client(flight)
            if shutil.disk_usage(root).free < 8 * 1024 ** 3 + 256 * 1024 ** 2:
                raise RuntimeError("attachment readback disk reserve unavailable")
            logs = list((root / "capture").glob("runClient_*.log"))
            if len(logs) != 1:
                raise RuntimeError("expected one owned runtime log")
            with logs[0].open("rb") as log:
                log.seek(max(0, logs[0].stat().st_size - 256 * 1024))
                matches = re.findall(r"gal.frame.acquire backend=vulkan correlation=(\d+) frame=(\d+)",
                                     log.read().decode(errors="replace"))
            if not matches:
                raise RuntimeError("no live acquisition receipt")
            correlation_id, frame_id = map(int, matches[-1])
            if min(frame_id, correlation_id) <= 0:
                raise RuntimeError("invalid acquisition identity")
            # The request protocol requires a positive capture index even with
            # deterministic capture disabled. Use the observed frame ordinal,
            # never infer a Java deterministic counter or native GPU identity.
            request = (f"gameplay_frame_id={frame_id}\ncorrelation_id={correlation_id}\n"
                       f"deterministic_rendered_frame_index={frame_id}\nsource_selected_capture=1\n"
                       "source_selected_pending=1\nrequired_entity_mesh=0\n")
            temporary = expected["request"].with_suffix(".tmp")
            temporary.write_text(request)
            requested_at = time.time_ns()
            os.replace(temporary, expected["request"])
            item = {"label": label, "requested_wall_ns": requested_at, "requested_properties": request,
                    "offset_after_mouse_input_seconds": (requested_at - first["wall_ns"]) / 1e9}
            report["captures"].append(item)
            write()
            manifest = None
            capture_deadline = min(deadline, time.monotonic() + 20)
            while time.monotonic() < capture_deadline:
                verify_client(flight)
                properties = dict(line.split("=", 1) for line in expected["request"].read_text().splitlines() if "=" in line)
                if properties.get("source_selected_pending") == "0":
                    promoted = int(properties["gameplay_frame_id"])
                    manifest_path = expected["attachments"] / f"gameplay-attachments-frame-{promoted}.json"
                    correlation_path = expected["attachments"] / f"gameplay-correlation-frame-{promoted}.json"
                    manifest = read_json(manifest_path)
                    correlation = read_json(correlation_path)
                    if manifest and correlation:
                        verify_capture(manifest, correlation, properties, frame_id)
                        item.update(manifest=manifest, correlation=correlation, promoted_properties=properties,
                                    completed_wall_ns=time.time_ns())
                        break
                    manifest = None
                time.sleep(.01)
            if manifest is None:
                raise RuntimeError("completed correlated readback not observed within its deadline")
            target = output / label
            target.mkdir()
            attachment_files = [manifest_path.name, correlation_path.name, manifest["sky_fog_receipt"],
                                *manifest["attachment_files"], *manifest["source_uniform_receipts"]]
            for relative in attachment_files:
                source = expected["attachments"] / relative
                source.resolve(strict=True).relative_to(expected["attachments"].resolve())
                if relative in manifest["source_uniform_receipts"]:
                    verify_source_receipt(json.loads(source.read_text()), manifest, uniform=True)
                destination = target / "attachments" / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, destination)
            for source in expected["source"].glob(f"*-frame-{manifest['gameplay_frame_id']}.*"):
                if source.suffix == ".json":
                    if source.name.startswith(("selected-source-shader-pack-trace-", "selected-source-overlay-")):
                        verify_source_receipt(json.loads(source.read_text()), manifest)
                    elif source.name.startswith("selected-source-execution-"):
                        verify_source_receipt(json.loads(source.read_text()), manifest, execution=True)
                destination = target / "source" / source.name
                destination.parent.mkdir(exist_ok=True)
                shutil.copy2(source, destination)
            item["retained_files"] = {str(path.relative_to(target)): {"bytes": path.stat().st_size,
                                      "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
                                      for path in target.rglob("*") if path.is_file()}
            write()
            print(f"{label}: completed frame {manifest['gameplay_frame_id']} submission {manifest['gal_submission_id']}", flush=True)
        report["status"] = "complete"
        write()
    except Exception as error:
        report.update(status="failed", error=str(error))
        write()
        raise


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--flight-output", type=Path, required=True)
    parser.add_argument("--later-seconds", type=int, choices=range(3, 11), default=6)
    args = parser.parse_args()
    root = args.flight_output.resolve(strict=True)
    if not root.is_relative_to(Path(__file__).resolve().parents[3] / "artifacts/graphics-captures"):
        parser.error("flight output must be inside Current's ignored graphics artifact tree")
    observe(root, args.later_seconds)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
