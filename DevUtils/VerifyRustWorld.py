#!/usr/bin/env python3
"""Opt-in exact terrain comparison and warmed, boundary-inclusive benchmarks.

The Java reference is extracted from Git into ignored build output. This script
never checks out files, changes production routing, or creates commits. Timings
cover NOISE-stage base chunks, not surface/carving/decoration or a rendered game.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import random
import re
import statistics
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
REFERENCE = "ee1692c10cf96a806c22c7e195bad01abb588a2e"
JAVA_PACKAGE = "src/main/java/net/minecraft/world/level/levelgen/"
REFERENCE_FILES = [JAVA_PACKAGE + name + ".java" for name in
                   ["DensityFunctions", "NoiseChunk", "RandomState", "NoiseRouterData"]]
REFERENCE_FILES += [JAVA_PACKAGE + "synth/" + name + ".java" for name in
                    ["ImprovedNoise", "SimplexNoise", "PerlinNoise", "PerlinSimplexNoise", "NormalNoise", "BlendedNoise"]]


def invoke(command, log=None):
    if log:
        with log.open("w") as output:
            subprocess.run(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, check=True)
    else:
        return subprocess.check_output(command, cwd=ROOT, text=True).strip()


def parse_timings(text):
    rows = {}
    for line in text.splitlines():
        if "WORLD_BENCH " not in line:
            continue
        world = re.search(r"name=(\S+)", line)[1]
        rows[world] = {key: json.loads(re.search(key + r"=(\[.*?\])", line)[1])
                       for key in ("samples", "conservative", "jit_ms")}
        rows[world]["warmup_rounds"] = int(re.search(r"warmup=(\d+)", line)[1])
        rows[world]["median_ms"] = statistics.median(rows[world]["samples"]) / 1e6
    if not rows:
        raise RuntimeError("Benchmark produced no measurements")
    return rows


def compare(forks):
    rng = random.Random(20260929)
    reports = {}
    worlds = set(forks[0]["java"])
    if any(set(fork[mode]) != worlds for fork in forks for mode in ("java", "native")):
        raise RuntimeError("Benchmark workload sets differ")
    for world in sorted(worlds):
        pairs = [(fork["java"][world]["conservative"], fork["native"][world]["samples"]) for fork in forks]
        ratios = [statistics.median(n) / statistics.median(j) for j, n in pairs]
        boot = []
        # Resample JVM forks, then rounds within each fork. Subtract all reported
        # JIT time (plus counter rounding allowance) only from the Java baseline.
        for _ in range(10000):
            samples = []
            for index in rng.choices(range(len(pairs)), k=len(pairs)):
                j, n = pairs[index]
                samples.append(statistics.median(rng.choices(n, k=len(n))) /
                               statistics.median(rng.choices(j, k=len(j))))
            boot.append(statistics.median(samples))
        boot.sort()
        reports[world] = {
            "java_median_ms": statistics.median(f["java"][world]["median_ms"] for f in forks),
            "native_median_ms": statistics.median(f["native"][world]["median_ms"] for f in forks),
            "conservative_ratio": statistics.median(ratios),
            "fork_ratios": ratios,
            "ratio_ci95": [boot[250], boot[9749]],
            "passes_10_percent": max(ratios) <= 0.9 and boot[9749] <= 0.9,
        }
    return reports


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", default=REFERENCE)
    parser.add_argument("--forks", type=int, default=3)
    parser.add_argument("--cpu", type=int, default=2)
    parser.add_argument("--world", help="One noise setting; default benchmarks all settings")
    parser.add_argument("--parity-only", action="store_true")
    parser.add_argument("--benchmark-only", action="store_true")
    parser.add_argument("--skip-build", action="store_true", help="Require an already-built production release library")
    parser.add_argument("--output", default="build/rust-world-verification")
    args = parser.parse_args()
    if args.forks < 1 or args.parity_only and args.benchmark_only:
        parser.error("Invalid forks or verification mode")
    if not hasattr(os, "sched_getaffinity") or args.cpu not in os.sched_getaffinity(0):
        parser.error("Choose a CPU available in this Linux process's affinity mask")
    output = (ROOT / args.output).resolve()
    # Keep generated Java references, binaries and logs in ignored build output.
    if not output.is_relative_to(ROOT / "build"):
        parser.error("--output must be inside this repository's build directory")
    output.mkdir(parents=True, exist_ok=True)
    reference = invoke(["git", "rev-parse", "--verify", args.reference + "^{commit}"])
    cp_file = output / "classpath.txt"
    init = output / "classpath.gradle"
    init.write_text("gradle.projectsEvaluated {\n"
                    " gradle.rootProject.tasks.register('writeNativeWorldClasspath') {\n"
                    "  dependsOn gradle.rootProject.tasks.named('testClasses')\n"
                    "  doLast { new File(" + json.dumps(str(cp_file)) + ").text = "
                    "gradle.rootProject.sourceSets.test.runtimeClasspath.asPath }\n }\n}\n")
    command = ["./gradlew", "-I", str(init), "-PmattmcRustProfile=release", "writeNativeWorldClasspath", "--console=plain"]
    if args.skip_build:
        command += ["-x", "buildRustNative"]
    else:
        command += ["buildRustNative"]
    print("Preparing comparison classes", flush=True)
    invoke(command, output / "build.log")
    classpath = cp_file.read_text().strip()
    reference_sources = output / "reference-sources"
    reference_classes = output / "reference-classes"
    reference_classes.mkdir(exist_ok=True)
    files = []
    for name in REFERENCE_FILES:
        target = reference_sources / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(invoke(["git", "show", reference + ":" + name]) + "\n")
        files.append(str(target))
    invoke(["javac", "-cp", classpath, "-d", str(reference_classes), *files], output / "reference-build.log")
    native_dir = ROOT / "build/rust/native"
    native_file = native_dir / "mattmc_rust-linux-x64.so"
    if not native_file.is_file():
        raise RuntimeError("Build the production release native library first")
    report = {"reference_commit": reference, "platform": platform.platform(), "cpu": args.cpu,
              "java_version": invoke(["java", "--version"]),
              "native_sha256": hashlib.sha256(native_file.read_bytes()).hexdigest(),
              "scope": "NOISE stage; seed 42 and five chunk positions for timing; eight settings, five seeds, six positions for fingerprints",
              "fingerprints": {}, "forks": []}

    def run(mode, operation, label):
        cp = str(reference_classes) + os.pathsep + classpath if mode == "java" else classpath
        command = ["taskset", "-c", str(args.cpu), "java", "-Xbatch", "-Xms512m", "-Xmx3g",
                   "--enable-native-access=ALL-UNNAMED", "-Dmattmc.rust.natives.dir=" + str(native_dir),
                   "-cp", cp, "net.minecraft.world.level.levelgen.NativeWorldVerification", operation]
        if operation == "benchmark" and args.world:
            command.append(args.world)
        log = output / (label + "-" + mode + ".log")
        print(label, mode, flush=True)
        invoke(command, log)
        return log.read_text()

    if not args.benchmark_only:
        for mode in ("java", "native"):
            report["fingerprints"][mode] = re.findall(r"WORLD minecraft:.*", run(mode, "fingerprint", "parity"))
        if len(report["fingerprints"]["java"]) != 40 or report["fingerprints"]["java"] != report["fingerprints"]["native"]:
            (output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
            raise RuntimeError("Original Java / Rust terrain fingerprints differ")
        print("All 40 terrain fingerprints match", flush=True)
    if not args.parity_only:
        for fork in range(args.forks):
            results = {}
            for mode in (("java", "native") if fork % 2 == 0 else ("native", "java")):
                results[mode] = parse_timings(run(mode, "benchmark", "fork-" + str(fork + 1)))
            report["forks"].append(results)
            (output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
        report["comparisons"] = compare(report["forks"])
        report["passes_10_percent"] = all(row["passes_10_percent"] for row in report["comparisons"].values())
    (output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report.get("comparisons", {"parity": "passed"}), indent=2))
    return 0 if args.parity_only or report["passes_10_percent"] else 2


if __name__ == "__main__":
    sys.exit(main())
