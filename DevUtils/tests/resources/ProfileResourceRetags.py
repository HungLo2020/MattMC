#!/usr/bin/env python3
"""Profile production tag rebinding/sync using an existing Frozen resource corpus.

Diagnostic only: attaches at the observer's existing retag-phase label, after
registry setup and initial synchronization warm-up. The sampled window includes
pending-tag loading, retagging and sync; it provides no acceptance timing or FPS.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[3]
HELPER = Path(__file__).with_suffix(".java")


def sha(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def sources(root: Path) -> dict[str, str]:
    names = subprocess.check_output([
        "git", "ls-files", "--cached", "--others", "--exclude-standard", "-z",
        "--", "src", "DevUtils", "build.gradle",
    ], cwd=root).split(b"\0")
    return {os.fsdecode(name): sha(root / os.fsdecode(name)) for name in names
            if name and (root / os.fsdecode(name)).is_file()}


def start_ticks(pid: int) -> str:
    stat = (Path("/proc") / str(pid) / "stat").read_text()
    return stat[stat.rfind(")") + 2:].split()[19]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verification", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--profiler", required=True, type=Path)
    parser.add_argument("--java-home", required=True, type=Path)
    parser.add_argument("--iterations", type=int, default=3000)
    parser.add_argument("--duration", type=int, default=15)
    args = parser.parse_args()
    corpus = args.verification.resolve()
    output = args.output.resolve()
    if not output.is_relative_to(ROOT / "build") or output == ROOT / "build" or output.exists():
        parser.error("--output must be a new directory below this checkout's build/")
    if args.duration < 1 or args.duration > 30 or args.iterations < 100:
        parser.error("duration must be 1–30 seconds; iterations must be at least 100")
    verification = json.loads((corpus / "results.json").read_text())
    if verification.get("records_identical") is not True:
        parser.error("verification must have completed identical Frozen/Current records")
    frozen = Path(verification["frozen"]["root"])
    frozen_identity = {
        "root": str(frozen),
        "head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=frozen, text=True).strip(),
        "status": subprocess.check_output(["git", "status", "--porcelain=v1"], cwd=frozen, text=True),
    }
    if frozen_identity != verification["frozen"]:
        parser.error("Frozen identity differs from the completed verification")
    args.java_home = args.java_home.resolve()
    native = ROOT / "build/rust/native/mattmc_rust-linux-x64.so"
    if sha(native) != verification["native_sha256"]:
        parser.error("Current native image differs from the completed verification")
    output.mkdir(parents=True)
    frozen_native = frozen / "build/rust/native/mattmc_rust-linux-x64.so"
    pins = {"current": sources(ROOT), "frozen": sources(frozen),
            "native": sha(native), "frozen_native": sha(frozen_native) if frozen_native.is_file() else None,
            "prompt": sha(ROOT / "docs/STANDARD-COPILOT-PROMPTS.md")}
    (output / "source-pins.json").write_text(json.dumps(pins, indent=2) + "\n")
    classes = output / "helper"
    classes.mkdir()
    classpaths = {side: (corpus / f"{side}-classpath.txt").read_text().strip()
                  for side in ("current", "frozen")}
    observer = corpus / "observer"
    compile_command = [str(args.java_home / "bin/javac"), "-cp",
                       str(observer) + os.pathsep + classpaths["frozen"],
                       "-d", str(classes), str(HELPER)]
    with (output / "compile.log").open("w") as log:
        subprocess.run(compile_command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
    receipt = {"diagnostic_only": True, "verification": str(corpus),
               "scope": "Observer retag-phase pending-tag loading/rebinding/sync; initial registry setup and initial sync excluded; no FPS or acceptance timing",
               "helper_sha256": sha(HELPER), "runs": [], "passed": False}
    try:
        for side, tree in (("current", ROOT), ("frozen", frozen)):
            run = output / side
            run.mkdir()
            ready = run / "profile-ready.txt"
            command = [str(args.java_home / "bin/java"), *verification["jvm_args"],
                       "-Dmattmc.rust.natives.dir=" + str(tree / "build/rust/native"),
                       "-cp", os.pathsep.join((str(classes), str(observer), classpaths[side])),
                       "ProfileResourceRetags", str(corpus / "content"), str(run / "fixtures"),
                       str(run / "warmup-record.txt"), str(ready), str(args.iterations)]
            row = {"side": side, "command": command, "passed": False}
            receipt["runs"].append(row)
            child = None
            try:
                with (run / "client.log").open("w") as log:
                    child = subprocess.Popen(command, cwd=run, stdout=log, stderr=subprocess.STDOUT)
                    row.update(pid=child.pid, start_ticks=start_ticks(child.pid))
                    deadline = time.monotonic() + 120
                    while not ready.exists():
                        if child.poll() is not None or time.monotonic() >= deadline:
                            raise RuntimeError(f"{side}: no live profiling window")
                        time.sleep(.1)
                    assert int(ready.read_text()) == child.pid
                    row["phase_marker"] = {"label": "-- reload", "wall_ns": ready.stat().st_mtime_ns}
                    assert child.poll() is None and start_ticks(child.pid) == row["start_ticks"]
                    maps = {line.split(maxsplit=5)[5] for line in
                            (Path("/proc") / str(child.pid) / "maps").read_text().splitlines()
                            if len(line.split(maxsplit=5)) == 6 and "mattmc_rust" in line}
                    row["mapped_native"] = {path: sha(Path(path)) for path in maps}
                    if side == "current":
                        assert len(maps) == 1 and next(iter(row["mapped_native"].values())) == pins["native"]
                    else:
                        assert all(value == pins["frozen_native"] for value in row["mapped_native"].values())
                    profiler = [str(args.profiler.resolve()), "-e", "itimer", "-i", "1ms",
                                "--cstack", "dwarf", "-t", "-d", str(args.duration),
                                "-o", "collapsed", "-f", str(run / "cpu.collapsed"), str(child.pid)]
                    row["profiler_command"] = profiler
                    print(side, "profiling PID", child.pid, flush=True)
                    with (run / "profiler.log").open("w") as profile_log:
                        row["profiler_exit"] = subprocess.call(profiler, stdout=profile_log,
                            stderr=subprocess.STDOUT, timeout=args.duration + 10)
                    assert child.poll() is None and start_ticks(child.pid) == row["start_ticks"], "profiling outlived workload"
                    row["driver_exit"] = child.wait(timeout=300)
                row["passed"] = row["profiler_exit"] == row["driver_exit"] == 0
                assert row["passed"], row
            finally:
                if child and child.poll() is None:
                    child.terminate()
                    try:
                        child.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        child.kill()
                        child.wait(timeout=10)
                (output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
        after = {"current": sources(ROOT), "frozen": sources(frozen),
                 "native": sha(native), "frozen_native": sha(frozen_native) if frozen_native.is_file() else None,
                 "prompt": sha(ROOT / "docs/STANDARD-COPILOT-PROMPTS.md")}
        receipt["guards"] = {key: pins[key] == after[key] for key in pins}
        receipt["passed"] = all(receipt["guards"].values()) and all(row["passed"] for row in receipt["runs"])
        assert receipt["passed"], receipt["guards"]
    finally:
        (output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print("Paired resource retag diagnostic profiles PASS; no performance acceptance claim.", flush=True)


if __name__ == "__main__":
    main()
