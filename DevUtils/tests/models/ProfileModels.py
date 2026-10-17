#!/usr/bin/env python3
"""Profile production model-only reloads using a completed Frozen corpus.

Diagnostic only: precomputed atlases, twenty warm reloads, then a bounded
paired CPU sample. Profiled timings are not acceptance measurements.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "DevUtils/tests/resources"))
from ProfileResourceRetags import sha, sources, start_ticks


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verification", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--profiler", type=Path, required=True)
    args = parser.parse_args()
    corpus, output = args.verification.resolve(), args.output.resolve()
    verification = json.loads((corpus / "results.json").read_text())
    if verification.get("records_identical") is not True:
        parser.error("Completed identical model records required")
    frozen = Path(verification["trees"]["frozen"]["root"])
    if frozen != Path(verification["trees"]["reference"]["root"]):
        parser.error("Both baseline roles must be actual unchanged Frozen")
    frozen_head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=frozen, text=True).strip()
    frozen_status = subprocess.check_output(["git", "status", "--porcelain"], cwd=frozen, text=True)
    if frozen_head != verification["trees"]["frozen"]["head"] or frozen_status:
        parser.error("Frozen must retain the completed corpus's clean source identity")
    if not output.is_relative_to(ROOT / "build") or output == ROOT / "build" or output.exists():
        parser.error("Output must be a new directory under build/")
    output.mkdir(parents=True)
    native = ROOT / "build/rust/native/mattmc_rust-linux-x64.so"
    before = {"current": sources(ROOT), "frozen": sources(frozen), "native": sha(native)}
    (output / "source-pins.json").write_text(json.dumps(before, indent=2) + "\n")
    rows = []
    try:
        for side, tree in [("current", ROOT), ("frozen", frozen)]:
            run = output / side
            run.mkdir()
            cp = (corpus / (side + "-classpath.txt")).read_text().strip()
            classes = run / "observer"
            classes.mkdir()
            observer = ROOT / "DevUtils/tests/models/ModelReference.java"
            with (run / "compile.log").open("w") as log:
                subprocess.run([str(args.java_home / "bin/javac"), "-cp", cp,
                                "-d", str(classes), str(observer)], stdout=log,
                               stderr=subprocess.STDOUT, check=True)
            ready = run / "ready.txt"
            command = [str(args.java_home / "bin/java"), *verification["jvm_args"],
                       "-Dmattmc.rust.natives.dir=" + str(tree / "build/rust/native"),
                       "-Dmattmc.models.profile.ready=" + str(ready), "-cp",
                       str(classes) + os.pathsep + cp, "ModelReference",
                       str(corpus / "content"), str(corpus / "content/packs"),
                       str(run / "record.txt"), "500"]
            row = {"side": side, "command": command, "passed": False}
            rows.append(row)
            with (run / "process.log").open("w") as log:
                process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT)
                try:
                    row.update(pid=process.pid, start_ticks=start_ticks(process.pid))
                    deadline = time.monotonic() + 180
                    while not ready.exists():
                        if process.poll() is not None or time.monotonic() >= deadline:
                            raise RuntimeError(f"{side}: model phase did not become ready")
                        time.sleep(.1)
                    assert start_ticks(process.pid) == row["start_ticks"]
                    maps = Path(f"/proc/{process.pid}/maps").read_text()
                    (run / "native-maps.txt").write_text(maps)
                    if side == "current":
                        assert any(line.endswith(" " + str(native.resolve())) for line in maps.splitlines())
                    profiler = [str(args.profiler.resolve()), "-e", "itimer", "-i", "1ms",
                                "-d", "15", "-o", "collapsed", "-t", "-f",
                                str(run / "cpu.collapsed"), str(process.pid)]
                    with (run / "profiler.log").open("w") as profile_log:
                        row["profiler_exit"] = subprocess.call(profiler, stdout=profile_log,
                                                               stderr=subprocess.STDOUT)
                    row["process_exit"] = process.wait(timeout=180)
                    row["passed"] = row["profiler_exit"] == row["process_exit"] == 0
                    assert row["passed"]
                finally:
                    if process.poll() is None:
                        process.terminate()
                        try:
                            process.wait(timeout=10)
                        except subprocess.TimeoutExpired:
                            process.kill()
                            process.wait()
    finally:
        now = {"current": sources(ROOT), "frozen": sources(frozen), "native": sha(native)}
        guards = {key: before[key] == value for key, value in now.items()}
        (output / "results.json").write_text(json.dumps({"diagnostic_only": True,
            "scope": "Model-only reload after twenty warmups; fifteen-second paired CPU samples; no acceptance timing",
            "runs": rows, "guards": guards,
            "passed": len(rows) == 2 and all(row["passed"] for row in rows) and all(guards.values())}, indent=2) + "\n")
    assert len(rows) == 2 and all(row["passed"] for row in rows) and all(guards.values())
    print("Paired model-only CPU profiles PASS; diagnostic only")


if __name__ == "__main__":
    main()
