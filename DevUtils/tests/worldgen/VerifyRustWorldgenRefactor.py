#!/usr/bin/env python3
"""Compare frozen pre-refactor and current production worldgen builds.

Capture before editing with --capture-baseline (after release testClasses).
Then rebuild release testClasses and run without that flag. Native libraries
and Java classes are isolated per implementation; production files are never
swapped. Existing parity/benchmark drivers include Java/native boundary costs.
All artifacts are written under build/. No original-Java speedup gate is reused
as a refactor regression gate.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import random
import re
import shutil
import statistics
import subprocess

ROOT = Path(__file__).resolve().parents[3]
LIBRARY = "mattmc_rust-linux-x64.so"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def classes_digest(folder):
    result = hashlib.sha256()
    for name in ("main", "test"):
        for path in sorted((folder / name).rglob("*.class")):
            result.update(str(path.relative_to(folder)).encode())
            result.update(bytes.fromhex(digest(path)))
    return result.hexdigest()


def snapshot(destination):
    destination.mkdir(parents=True, exist_ok=False)
    for name in ("main", "test"):
        shutil.copytree(ROOT / "build/classes/java" / name, destination / name)
    shutil.copy2(ROOT / "build/rust/native" / LIBRARY, destination / LIBRARY)
    (destination / "revision.txt").write_text(subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True))


def compare(pairs):
    rng = random.Random(20261001)
    ratios = [statistics.median(b) / statistics.median(a) for a, b in pairs]
    boot = []
    for _ in range(10000):
        boot.append(statistics.median(
            statistics.median(rng.choices(b, k=len(b))) /
            statistics.median(rng.choices(a, k=len(a)))
            for a, b in rng.choices(pairs, k=len(pairs))))
    boot.sort()
    return {
        "baseline_median_ms": statistics.median(statistics.median(a) for a, _ in pairs) / 1e6,
        "candidate_median_ms": statistics.median(statistics.median(b) for _, b in pairs) / 1e6,
        "fork_ratios": ratios,
        "median_ratio": statistics.median(ratios),
        "ratio_ci95": [boot[250], boot[9749]],
        "detected_regression": boot[250] > 1.0,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", default="build/worldgen-organization")
    parser.add_argument("--classpath", required=True, help="Existing test runtime classpath file")
    parser.add_argument("--capture-baseline", action="store_true")
    parser.add_argument("--forks", type=int, default=3)
    parser.add_argument("--cpu", type=int, default=2)
    parser.add_argument("--parity-only", action="store_true")
    parser.add_argument("--benchmark-only", action="store_true")
    parser.add_argument("--world")
    parser.add_argument("--domain", choices=("all", "terrain", "surface"), default="all")
    parser.add_argument("--surface-warmups", type=int, default=60)
    parser.add_argument("--surface-rounds", type=int, default=24)
    args = parser.parse_args()
    output = (ROOT / args.output).resolve()
    if not output.is_relative_to(ROOT / "build"):
        parser.error("Output must be under build/")
    if args.forks < 1 or args.cpu not in os.sched_getaffinity(0):
        parser.error("Choose positive forks and an available CPU")
    if args.parity_only and args.benchmark_only:
        parser.error("Choose at most one verification mode")
    if args.surface_warmups < 1 or args.surface_rounds < 2:
        parser.error("Choose positive warmups and at least two measurement rounds")
    domains = {"all": ("World", "Surface"), "terrain": ("World",), "surface": ("Surface",)}[args.domain]
    if args.capture_baseline:
        snapshot(output / "baseline")
        return
    if not (output / "baseline" / LIBRARY).exists():
        parser.error("Capture a release baseline before refactoring")
    if not (output / "candidate").exists():
        snapshot(output / "candidate")
    elif digest(output / "candidate" / LIBRARY) != digest(ROOT / "build/rust/native" / LIBRARY):
        parser.error("Candidate changed; use a fresh output directory and frozen baseline")
    if classes_digest(output / "candidate") != classes_digest(ROOT / "build/classes/java"):
        parser.error("Candidate Java classes changed; use a fresh candidate snapshot")
    cp = (ROOT / args.classpath).read_text().strip().split(os.pathsep)
    report = {"cpu": args.cpu, "platform": platform.platform(),
              "java_version": subprocess.check_output(["java", "-version"], stderr=subprocess.STDOUT, text=True).strip(),
              "surface_warmups": args.surface_warmups, "surface_rounds": args.surface_rounds,
              "implementations": {}, "fingerprints": {}, "forks": []}
    for mode in ("baseline", "candidate"):
        folder = output / mode
        report["implementations"][mode] = {
            "native_sha256": digest(folder / LIBRARY),
            "revision": (folder / "revision.txt").read_text().strip(),
            "classes_sha256": classes_digest(folder),
        }

    def save():
        (output / "results.json").write_text(json.dumps(report, indent=2))

    def run(mode, domain, operation, label):
        folder = output / mode
        classpath = [str(folder / p.name) if p in
                     (ROOT / "build/classes/java/main", ROOT / "build/classes/java/test") else str(p)
                     for p in map(Path, cp)]
        command = ["taskset", "-c", str(args.cpu), "java", "-Xbatch", "-Xms1G", "-Xmx4G",
                   "-XX:+UseZGC", "-XX:+UseCompactObjectHeaders", "--enable-native-access=ALL-UNNAMED",
                   "-Dsurface.warmups=" + str(args.surface_warmups),
                   "-Dsurface.rounds=" + str(args.surface_rounds),
                   "-Dmattmc.rust.natives.dir=" + str(folder), "-cp", os.pathsep.join(classpath),
                   "net.minecraft.world.level.levelgen.Native" + domain + "Verification", operation]
        if args.world:
            command.append(args.world)
        (output / (label + "-command.json")).write_text(json.dumps(command, indent=2))
        print(label, flush=True)
        log = output / (label + ".log")
        with log.open("w") as stream:
            subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT,
                           check=True, timeout=3600)
        return log.read_text()

    if not args.benchmark_only:
        for domain in domains:
            values = {}
            for mode in ("baseline", "candidate"):
                text = run(mode, domain, "fingerprint", f"parity-{domain}-{mode}")
                # Logging prefixes contain timestamps; compare only payloads.
                values[mode] = [match[0] for line in text.splitlines()
                                if (match := re.search(r"(?:WORLD |SURFACE_PARITY ).*", line))]
                if not values[mode]:
                    raise RuntimeError("Missing fingerprints: " + domain)
            values["identical"] = values["baseline"] == values["candidate"]
            report["fingerprints"][domain] = values
            save()
            if not values["identical"]:
                raise SystemExit("Parity failed: " + domain)
    if not args.parity_only:
        for fork in range(args.forks):
            row = {}
            for mode in (("baseline", "candidate") if fork % 2 == 0 else ("candidate", "baseline")):
                row[mode] = {}
                for domain in domains:
                    text = run(mode, domain, "benchmark", f"benchmark-{fork}-{domain}-{mode}")
                    for line in text.splitlines():
                        if "WORLD_BENCH " not in line and "SURFACE_BENCH " not in line:
                            continue
                        name = re.search(r"name=(\S+)", line)[1]
                        row[mode][domain + "/" + name] = {
                            "samples_ns": json.loads(re.search(r"samples=(\[.*?\])", line)[1]),
                            "jit_ms": json.loads(re.search(r"jit_ms=(\[.*?\])", line)[1]),
                        }
            if not row["baseline"] or row["baseline"].keys() != row["candidate"].keys():
                raise RuntimeError("Missing or mismatched benchmark workloads")
            report["forks"].append(row)
            save()
        report["performance"] = {name: compare([
            (f["baseline"][name]["samples_ns"], f["candidate"][name]["samples_ns"])
            for f in report["forks"]]) for name in sorted(report["forks"][0]["baseline"])}
        save()
        print(json.dumps(report["performance"], indent=2), flush=True)
        if any(v["detected_regression"] for v in report["performance"].values()):
            raise SystemExit("A regression was detected; inspect the retained samples")


if __name__ == "__main__":
    main()
