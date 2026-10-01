#!/usr/bin/env python3
"""Exact Java aquifer comparison and warmed, boundary-inclusive terrain timing.

The pre-migration aquifer is extracted into ignored build output. Other migrated
systems use the same current implementation in both processes.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from VerifyRustWorld import parse_timings
from VerifyRustSurface import comparison

ROOT = Path(__file__).resolve().parents[3]
REFERENCE = "0719ec4bd5f4956ceb9f341c9c641ad7294ef632"
PACKAGE = "src/main/java/net/minecraft/world/level/levelgen/"


def invoke(command, log):
    with log.open("w") as stream:
        subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", default="build/aquifer-migration/verification")
    parser.add_argument("--reference", default=REFERENCE)
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--forks", type=int, default=3)
    parser.add_argument("--cpu", type=int, default=2)
    parser.add_argument("--world", default="amplified,large_biomes,overworld", help="Comma-separated settings; defaults to all three aquifer-enabled settings")
    parser.add_argument("--seed", type=int, default=42, help="Timing seed; fingerprints always cover five seeds")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--parity-only", action="store_true")
    group.add_argument("--benchmark-only", action="store_true")
    args = parser.parse_args()
    if args.forks < 1 or args.cpu not in os.sched_getaffinity(0):
        parser.error("Choose a positive fork count and an available CPU")
    if not -(1 << 63) <= args.seed < (1 << 63):
        parser.error("Seed must fit a signed Java long")
    output = (ROOT / args.output).resolve()
    if not output.is_relative_to(ROOT / "build"):
        parser.error("Output must be under build/")
    output.mkdir(parents=True, exist_ok=True)
    cp_file = output / "classpath.txt"
    init = output / "classpath.gradle"
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.register('writeAquiferClasspath') { "
                    "dependsOn 'testClasses'; doLast { new File(" + json.dumps(str(cp_file)) + ").text = "
                    "gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command = ["./gradlew", "-I", str(init), "-PmattmcRustProfile=release", "writeAquiferClasspath", "--console=plain"]
    if args.skip_build:
        command += ["-x", "buildRustNative"]
    invoke(command, output / "build.log")
    reference = subprocess.check_output(["git", "rev-parse", "--verify", args.reference + "^{commit}"], cwd=ROOT, text=True).strip()
    source = subprocess.check_output(["git", "show", reference + ":" + PACKAGE + "Aquifer.java"], cwd=ROOT, text=True)
    sources = output / "reference-sources"
    sources.mkdir(exist_ok=True)
    classes = output / "reference-classes"
    classes.mkdir(exist_ok=True)
    (sources / "Aquifer.java").write_text(source)
    cp = cp_file.read_text().strip()
    chunk_source = subprocess.check_output(["git", "show", reference + ":" + PACKAGE + "NoiseChunk.java"], cwd=ROOT, text=True)
    (sources / "NoiseChunk.java").write_text(chunk_source)
    invoke(["javac", "-cp", cp, "-d", str(classes), str(sources / "Aquifer.java"), str(sources / "NoiseChunk.java")], output / "reference-build.log")
    # A renamed oracle permits call-by-call comparisons and callback traces in
    # one process without keeping a second Java algorithm in repository source.
    start = source.index("\tpublic static class NoiseBasedAquifer")
    oracle = source[:source.index("public interface Aquifer")] + source[start:source.rfind("}")]
    oracle = oracle.replace("public static class NoiseBasedAquifer", "public class JavaAquiferReference")
    oracle = oracle.replace("\n\t\tNoiseBasedAquifer(", "\n\t\tJavaAquiferReference(")
    # The extracted class is no longer a record nestmate; use its trivial accessors.
    oracle = re.sub(r"\.fluid(Level|Type)\b(?!\()", r".fluid\1()", oracle)
    (sources / "JavaAquiferReference.java").write_text(oracle)
    oracle_classes = output / "oracle-classes"
    oracle_classes.mkdir(exist_ok=True)
    invoke(["javac", "-cp", cp, "-d", str(oracle_classes), str(sources / "JavaAquiferReference.java")], output / "oracle-build.log")
    native = ROOT / "build/rust/native"
    report = {"reference": reference, "cpu": args.cpu, "seed": args.seed, "forks": [],
              "native_sha256": hashlib.sha256((native / "mattmc_rust-linux-x64.so").read_bytes()).hexdigest(),
              "scope": "Aquifer migration and lazy interpolation; terrain construction/materials and all foreign calls included"}
    def save(): (output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    def run(mode, operation, label):
        classpath = str(classes) + os.pathsep + cp if mode == "java" else str(oracle_classes) + os.pathsep + cp
        driver = "NativeAquiferVerification" if operation == "differential" else "NativeWorldVerification"
        command = ["taskset", "-c", str(args.cpu), "java", "-Xbatch", "-Xms512m", "-Xmx3g",
                   "--enable-native-access=ALL-UNNAMED", "-Dmattmc.test.aquiferPicker=true", "-Dmattmc.test.seed=" + str(args.seed), "-Dmattmc.rust.natives.dir=" + str(native), "-cp", classpath,
                   "net.minecraft.world.level.levelgen." + driver, operation]
        if operation == "benchmark": command.append(args.world)
        log = output / (label + "-" + mode + ".log")
        print(label, mode, flush=True)
        invoke(command, log)
        return log.read_text()
    if not args.benchmark_only:
        report["differential"] = run("native", "differential", "parity")
        hashes = {mode: re.findall(r"WORLD minecraft:.*", run(mode, "fingerprint", "terrain")) for mode in ["java", "native"]}
        report["fingerprints"] = hashes
        report["identical"] = len(hashes["java"]) == 40 and hashes["java"] == hashes["native"]
        save()
        if not report["identical"]: raise SystemExit("Terrain parity failed")
    if not args.parity_only:
        for fork in range(args.forks):
            row = {}
            for mode in (["java", "native"] if fork % 2 == 0 else ["native", "java"]):
                rows = parse_timings(run(mode, "benchmark", "fork-" + str(fork)))
                row[mode] = {name: {"samples_ns": data["samples"], "conservative_ns": data["conservative"],
                                   "jit_ms": data["jit_ms"], "warmup_rounds": data["warmup_rounds"]} for name, data in rows.items()}
            report["forks"].append(row); save()
        report["performance"] = comparison(report["forks"])
        report["passes_5_percent"] = all(r["passes_5_percent"] for r in report["performance"].values())
        save()
        print(json.dumps(report["performance"], indent=2))
        if not report["passes_5_percent"]: raise SystemExit(2)


if __name__ == "__main__": main()
