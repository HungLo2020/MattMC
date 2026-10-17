#!/usr/bin/env python3
"""Identical compiled DH cached tint workload against untouched Frozen; supplemental CPU timing."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import statistics
import subprocess
import time


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--frozen-classpath-file", type=Path, required=True)
    parser.add_argument("--current-classpath-file", type=Path, required=True)
    parser.add_argument("--native-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--pairs", type=int, default=6)
    parser.add_argument("--rounds", type=int, default=50000)
    parser.add_argument("--warmup-rounds", type=int, default=50000)
    parser.add_argument("--radius", type=int, choices=range(4), default=3)
    parser.add_argument("--detail", type=int, choices=(6, 7, 8), default=6)
    args = parser.parse_args()
    if not 2 <= args.pairs <= 50 or args.rounds < 1 or args.warmup_rounds < 1:
        parser.error("pairs must be 2..50; measured and warmup rounds must be positive")
    args.output.mkdir(parents=True, exist_ok=False)
    driver = args.output / "driver"
    source = Path("build/classes/java/test/net/minecraft/world/level/levelgen")
    target = driver / "net/minecraft/world/level/levelgen"
    target.mkdir(parents=True)
    classes = list(source.glob("DhTintVerification*.class"))
    if not classes:
        raise RuntimeError("Compile the test driver first")
    for path in classes:
        shutil.copyfile(path, target / path.name)
    classpaths = {side: str(driver.resolve()) + ":" + path.read_text().strip()
                  for side, path in [("Frozen", args.frozen_classpath_file), ("Current", args.current_classpath_file)]}
    native = args.native_dir / "mattmc_rust-linux-x64.so"
    pins = {"native": digest(native), "driver": {p.name: digest(p) for p in classes}}
    protocol = {"scope": "synthetic CPU DH cached tint workload, not server ticks/FPS or gameplay acceptance",
                "rounds": args.rounds, "warmup": f"five {args.warmup_rounds}-round rounds in every JVM", "pairs": args.pairs,
                "order": [side for pair in range(args.pairs) for side in (["Frozen", "Current"] if pair % 2 == 0 else ["Current", "Frozen"])],
                "pins": pins, "java": str(args.java), "classpaths": classpaths, "radius": args.radius, "detail": args.detail}
    (args.output / "protocol.json").write_text(json.dumps(protocol, indent=2) + "\n")
    rows = []
    for number, side in enumerate(protocol["order"]):
        if digest(native) != pins["native"]:
            raise RuntimeError("Native image changed during timing")
        command = [str(args.java), "--enable-native-access=ALL-UNNAMED", "-Xmx2G",
                   "-Dmattmc.rust.natives.dir=" + str(args.native_dir.resolve()), "-cp", classpaths[side],
                   "net.minecraft.world.level.levelgen.DhTintVerification", str(args.rounds), str(args.warmup_rounds), str(args.radius), str(args.detail)]
        start = time.time_ns()
        result = subprocess.run(command, text=True, capture_output=True, timeout=180)
        (args.output / f"{number:02d}-{side}.log").write_text(result.stdout + result.stderr)
        matches = re.findall(r"DH_TINT (\d+) (-?\d+)", result.stdout)
        row = {"side": side, "exit": result.returncode, "start_wall_ns": start, "command": command}
        if result.returncode or len(matches) != 1:
            rows.append(row)
            (args.output / "failed.json").write_text(json.dumps(rows, indent=2) + "\n")
            raise RuntimeError(f"Timing {number} failed; retained log")
        row.update(elapsed_ns=int(matches[0][0]), checksum=int(matches[0][1]))
        rows.append(row)
        (args.output / "runs.json").write_text(json.dumps(rows, indent=2) + "\n")
        print(number, side, round(row["elapsed_ns"] / 1e6, 3), "ms", flush=True)
    if len({row["checksum"] for row in rows}) != 1:
        raise RuntimeError("Frozen/Current checksum mismatch")
    medians = {side: statistics.median(row["elapsed_ns"] for row in rows if row["side"] == side) / 1e6 for side in classpaths}
    report = {"scope": protocol["scope"], "median_ms": medians,
              "current_over_frozen": medians["Current"] / medians["Frozen"], "pins": pins, "runs": rows}
    (args.output / "result.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({key: value for key, value in report.items() if key not in ("pins", "runs")}, indent=2))


if __name__ == "__main__":
    main()
