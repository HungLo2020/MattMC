#!/usr/bin/env python3
"""Compare production surface generation with original Java extracted from Git.

No source checkout or production fallback switch is used. Generated references,
logs and results live under build/. Timed calls include preparation of the native
column, every boundary crossing, callbacks and applying all chunk writes.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import re
import statistics
import subprocess

ROOT = Path(__file__).resolve().parents[3]
REFERENCE = "0719ec4bd5f4956ceb9f341c9c641ad7294ef632"
PACKAGE = "src/main/java/net/minecraft/world/level/levelgen/"


def invoke(command, log=None):
    if log:
        with log.open("w") as stream:
            subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)
    else:
        return subprocess.check_output(command, cwd=ROOT, text=True).strip()


def fingerprints(path):
    result = {}
    for line in path.read_text().splitlines():
        match = re.search(r"SURFACE_PARITY name=(\S+) seed=(-?\d+) chunks=(\d+) sha256=(\w+)", line)
        if match:
            result[match[1] + ":" + match[2]] = {"chunks": int(match[3]), "sha256": match[4]}
    if not result:
        raise RuntimeError("No fingerprints in " + str(path))
    return result


def timings(path):
    result = {}
    for line in path.read_text().splitlines():
        if "SURFACE_BENCH " not in line:
            continue
        name = re.search(r"name=(\S+)", line)[1]
        values = json.loads(re.search(r"samples=(\[.*?\])", line)[1])
        jit = json.loads(re.search(r"jit_ms=(\[.*?\])", line)[1])
        result[name] = {"samples_ns": values, "jit_ms": jit,
                        "conservative_ns": [max(1, v-(j+1)*1_000_000) for v, j in zip(values, jit)]}
    if not result:
        raise RuntimeError("No timings in " + str(path))
    return result


def comparison(forks):
    rng = random.Random(20260930)
    result = {}
    names = set(forks[0]["java"])
    if any(set(f[mode]) != names for f in forks for mode in ["java", "native"]):
        raise RuntimeError("Mismatched benchmark workloads")
    for name in sorted(names):
        pairs = [(f["java"][name]["conservative_ns"], f["native"][name]["samples_ns"]) for f in forks]
        ratios = [statistics.median(n)/statistics.median(j) for j, n in pairs]
        boot = []
        for _ in range(10000):
            sampled = [statistics.median(rng.choices(n, k=len(n)))/statistics.median(rng.choices(j, k=len(j)))
                       for j, n in rng.choices(pairs, k=len(pairs))]
            boot.append(statistics.median(sampled))
        boot.sort()
        result[name] = {
            "java_median_ms": statistics.median(statistics.median(f["java"][name]["samples_ns"]) for f in forks)/1e6,
            "native_median_ms": statistics.median(statistics.median(f["native"][name]["samples_ns"]) for f in forks)/1e6,
            "conservative_fork_ratios": ratios, "ratio_ci95": [boot[250], boot[9749]],
            "passes_5_percent": max(ratios) <= .95 and boot[9749] <= .95,
        }
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", default=REFERENCE)
    parser.add_argument("--output", default="build/rust-surface-verification")
    parser.add_argument("--forks", type=int, default=3)
    parser.add_argument("--cpu", type=int, default=2)
    parser.add_argument("--world")
    parser.add_argument("--skip-build", action="store_true")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--parity-only", action="store_true")
    mode.add_argument("--benchmark-only", action="store_true")
    args = parser.parse_args()
    if args.forks < 1 or args.cpu not in os.sched_getaffinity(0):
        parser.error("Choose a positive fork count and an available CPU")
    output = (ROOT / args.output).resolve()
    if not output.is_relative_to(ROOT / "build"):
        parser.error("Output must be under build/")
    output.mkdir(parents=True, exist_ok=True)
    cp_file = output / "classpath.txt"
    init = output / "classpath.gradle"
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.register('writeSurfaceVerificationClasspath') { "
                    "dependsOn 'testClasses'; doLast { new File(" + json.dumps(str(cp_file)) + ").text = "
                    "gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command = ["./gradlew", "-I", str(init), "-PmattmcRustProfile=release", "writeSurfaceVerificationClasspath", "--console=plain"]
    if args.skip_build:
        command += ["-x", "buildRustNative"]
    print("Preparing surface comparison", flush=True)
    invoke(command, output / "build.log")
    reference = invoke(["git", "rev-parse", "--verify", args.reference + "^{commit}"])
    sources = output / "reference-sources"
    classes = output / "reference-classes"
    classes.mkdir(exist_ok=True)
    files = []
    for name in ["SurfaceSystem", "SurfaceRules"]:
        path = PACKAGE + name + ".java"
        target = sources / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(invoke(["git", "show", reference + ":" + path]) + "\n")
        files.append(str(target))
    cp = cp_file.read_text().strip()
    invoke(["javac", "-cp", cp, "-d", str(classes), *files], output / "reference-build.log")
    native_dir = ROOT / "build/rust/native"
    report = {"reference": reference, "cpu": args.cpu, "native_sha256": hashlib.sha256((native_dir / "mattmc_rust-linux-x64.so").read_bytes()).hexdigest(),
              "scope": "SurfaceSystem.buildSurface, including native preparation, boundary and ordered writes; fixture setup excluded", "forks": []}
    def save(): (output / "results.json").write_text(json.dumps(report, indent=2))
    def run(implementation, operation, label):
        classpath = str(classes) + os.pathsep + cp if implementation == "java" else cp
        log = output / (label + ".log")
        command = ["taskset", "-c", str(args.cpu), "java", "-Xbatch", "-Xms1G", "-Xmx4G", "-XX:+UseZGC", "-XX:+UseCompactObjectHeaders",
                   "--enable-native-access=ALL-UNNAMED", "-Dmattmc.rust.natives.dir=" + str(native_dir), "-cp", classpath,
                   "net.minecraft.world.level.levelgen.NativeSurfaceVerification", operation]
        if args.world: command.append(args.world)
        print(label, flush=True)
        invoke(command, log)
        return log
    if not args.benchmark_only:
        java = fingerprints(run("java", "fingerprint", "parity-java"))
        native = fingerprints(run("native", "fingerprint", "parity-native"))
        report["fingerprints"] = {"java": java, "native": native, "identical": java == native}
        save()
        if java != native: raise SystemExit("Surface parity FAILED; inspect results.json")
        print("Exact surface parity:", sum(v["chunks"] for v in java.values()), "chunks", flush=True)
    if not args.parity_only:
        for fork in range(args.forks):
            row = {}
            for implementation in (["java", "native"] if fork % 2 == 0 else ["native", "java"]):
                row[implementation] = timings(run(implementation, "benchmark", f"benchmark-{fork}-{implementation}"))
            report["forks"].append(row); save()
        report["performance"] = comparison(report["forks"]); save()
        print(json.dumps(report["performance"], indent=2))
        if not all(v["passes_5_percent"] for v in report["performance"].values()):
            raise SystemExit("5% surface performance gate FAILED; inspect results.json")
    print(output / "results.json", flush=True)


if __name__ == "__main__":
    main()
