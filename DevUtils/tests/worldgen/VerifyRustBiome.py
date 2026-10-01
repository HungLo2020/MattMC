#!/usr/bin/env python3
"""Independent Java biome oracle and warmed direct lookup benchmark (including FFM).

Reference sources are extracted into build/, never checked out over local work.
"""
import argparse
import hashlib
import json
import os
import platform
from pathlib import Path
import re
from VerifyRustSurface import invoke, comparison

ROOT = Path(__file__).resolve().parents[3]
REFERENCE = "5a231206da15336ed8f5e9d954ed7cca45a92274"
PACKAGE = "net/minecraft/world/level/biome/"


def timings(path):
    result = {}
    for line in path.read_text().splitlines():
        if "BIOME_BENCH " not in line:
            continue
        name = re.search(r"name=(\S+)", line)[1]
        result[name] = {
            "samples_ns": json.loads(re.search(r"samples=(\[.*?\])", line)[1]),
            "conservative_ns": json.loads(re.search(r"conservative=(\[.*?\])", line)[1]),
            "jit_ms": json.loads(re.search(r"jit_ms=(\[.*?\])", line)[1]),
        }
    if len(result) != 9:
        raise RuntimeError("Incomplete benchmark: " + str(path))
    return result


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--reference", default=REFERENCE)
    p.add_argument("--output", default="build/biome-migration/verification")
    p.add_argument("--forks", type=int, default=3)
    p.add_argument("--cpu", type=int, default=2)
    p.add_argument("--skip-build", action="store_true")
    modes = p.add_mutually_exclusive_group()
    modes.add_argument("--parity-only", action="store_true")
    modes.add_argument("--benchmark-only", action="store_true")
    args = p.parse_args()
    if args.forks < 1 or args.cpu not in os.sched_getaffinity(0):
        p.error("Choose a positive fork count and an available CPU")
    if not args.parity_only and args.forks < 3:
        p.error("Performance acceptance requires at least three independent process pairs")
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / "build"):
        p.error("Output must be under build/")
    out.mkdir(parents=True, exist_ok=True)
    cpfile = out / "classpath.txt"
    init = out / "classpath.gradle"
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.register('writeBiomeClasspath') { "
                    "dependsOn 'testClasses'; doLast { new File(" + json.dumps(str(cpfile)) + ").text = "
                    "gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command = ["./gradlew", "-I", str(init), "-PmattmcRustProfile=release", "writeBiomeClasspath", "--console=plain"]
    if args.skip_build:
        command += ["-x", "buildRustNative"]
    print("Preparing independent Java reference", flush=True)
    invoke(command, out / "build.log")
    cp = cpfile.read_text().strip()
    reference = invoke(["git", "rev-parse", "--verify", args.reference + "^{commit}"])
    sources, classes, oracle = out / "reference-sources", out / "reference-classes", out / "oracle-classes"
    classes.mkdir(exist_ok=True)
    oracle.mkdir(exist_ok=True)
    files = []
    for path in [PACKAGE + "Climate.java", PACKAGE + "MultiNoiseBiomeSource.java", "net/minecraft/world/level/chunk/LevelChunkSection.java"]:
        target = sources / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(invoke(["git", "show", reference + ":src/main/java/" + path]) + "\n")
        files.append(str(target))
    invoke(["javac", "-cp", cp, "-d", str(classes), *files], out / "reference-build.log")
    original = (sources / PACKAGE / "Climate.java").read_text()
    renamed = sources / PACKAGE / "JavaClimate.java"
    renamed.write_text(re.sub(r"\bClimate\b", "JavaClimate", original))
    adapter = sources / PACKAGE / "OriginalClimateOracle.java"
    adapter.write_text('''package net.minecraft.world.level.biome;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
public final class OriginalClimateOracle implements NativeBiomeVerification.OracleFactory {
 public NativeBiomeVerification.Oracle create(long[][] boxes) {
  var rows = new ArrayList<Pair<JavaClimate.ParameterPoint,Integer>>();
  for(int i=0;i<boxes.length;i++) {
   long[] b=boxes[i];
   rows.add(Pair.of(new JavaClimate.ParameterPoint(new JavaClimate.Parameter(b[0],b[1]),
    new JavaClimate.Parameter(b[2],b[3]),new JavaClimate.Parameter(b[4],b[5]),
    new JavaClimate.Parameter(b[6],b[7]),new JavaClimate.Parameter(b[8],b[9]),
    new JavaClimate.Parameter(b[10],b[11]),b[12]),i));
  }
  var tree=new JavaClimate.ParameterList<Integer>(rows);
  return p -> tree.findValue(new JavaClimate.TargetPoint(p[0],p[1],p[2],p[3],p[4],p[5]));
 }
}
''')
    invoke(["javac", "-cp", cp, "-d", str(oracle), str(renamed), str(adapter)], out / "oracle-build.log")
    native = ROOT / "build/rust/native"
    report = {"reference": reference, "cpu": args.cpu, "native_sha256": hashlib.sha256((native / "mattmc_rust-linux-x64.so").read_bytes()).hexdigest(),
              "scope": "Direct climate lookup including batch eligibility, native batch arrays, packing, downcall, leaf mapping and result consumption; tree/corpus construction excluded", "forks": [],
              "java_version": invoke(["java", "--version"]), "rust_version": invoke(["rustc", "--version"]),
              "platform": platform.platform(), "checkout": invoke(["git", "rev-parse", "HEAD"]),
              "jvm_options": ["-Xbatch", "-Xms512m", "-Xmx3g", "-XX:+UseZGC", "-XX:+UseCompactObjectHeaders", "--enable-native-access=ALL-UNNAMED"]}
    production = ["biome/Climate.java", "biome/MultiNoiseBiomeSource.java", "biome/NativeClimateTree.java",
                  "chunk/LevelChunkSection.java", "levelgen/BiomeSamplerSafety.java", "levelgen/NoiseChunk.java"]
    report["java_source_sha256"] = {path: hashlib.sha256((ROOT / "src/main/java/net/minecraft/world/level" / path).read_bytes()).hexdigest()
                                     for path in production}
    if args.benchmark_only and (out / "results.json").exists():
        previous = json.loads((out / "results.json").read_text())
        if all(previous.get(k) == report[k] for k in ["reference", "native_sha256", "java_source_sha256"]):
            for key in ["parity", "worlds", "worlds_identical", "seeded_corpora"]:
                if key in previous:
                    report[key] = previous[key]
    def save():
        (out / "results.json").write_text(json.dumps(report, indent=2))
    def run(mode, label):
        classpath = (str(classes) + os.pathsep + cp if mode in ["bench-java", "world-java"] else
                     str(oracle) + os.pathsep + cp if mode == "parity" else cp)
        log = out / (label + ".log")
        print(label, flush=True)
        command = ["taskset", "-c", str(args.cpu), "java", *report["jvm_options"], "-Dmattmc.rust.natives.dir=" + str(native),
                "-Dbiome.corpus.dir=" + str(out / "corpus-java"),
                "-cp", classpath,
                "net.minecraft.world.level.levelgen.NativeBiomeWorldVerification" if mode.startswith("world-") else "net.minecraft.world.level.biome.NativeBiomeVerification",
                mode.removeprefix("world-")]
        if mode.startswith("world-"):
            command.append(str(out / mode.replace("world-", "corpus-")))
        invoke(command, log)
        return log
    if not args.benchmark_only:
        log = run("parity", "parity")
        match = re.search(r"BIOME_PARITY_COMPLETE decisions=(\d+) exact=true", log.read_text())
        if not match:
            raise RuntimeError("Parity did not complete")
        report["parity"] = {"decisions": int(match[1]), "exact": True}
        save()
        print("Identical Java/native decisions:", match[1], flush=True)
        worlds = {}
        for mode in ["java", "native"]:
            log = run("world-" + mode, "world-" + mode)
            worlds[mode] = {m[1]+":"+m[2]: {"decisions": int(m[3]), "sha256": m[4]}
                           for m in re.finditer(r"BIOME_WORLD name=(\S+) seed=(-?\d+) decisions=(\d+) sha256=(\w+)", log.read_text())}
        report["worlds"] = worlds
        report["worlds_identical"] = bool(worlds["java"]) and worlds["java"] == worlds["native"]
        save()
        if not report["worlds_identical"]:
            raise SystemExit("Seeded biome section parity FAILED")
        print("Identical stored biome entries:", sum(v["decisions"] for v in worlds["native"].values()), flush=True)
        report["seeded_corpora"] = {}
        for name in ["overworld", "nether", "primordial_caves"]:
            a, b = [(out / ("corpus-" + mode) / (name + ".bin")).read_bytes() for mode in ["java", "native"]]
            if a != b:
                raise SystemExit("Seeded climate inputs differ: " + name)
            report["seeded_corpora"][name] = {"points": len(a)//48, "sha256": hashlib.sha256(a).hexdigest()}
        save()
    if not args.parity_only:
        for fork in range(args.forks):
            row = {}
            for mode in (["java", "native"] if fork % 2 == 0 else ["native", "java"]):
                row[mode] = timings(run("bench-" + mode, f"benchmark-{fork}-{mode}"))
            report["forks"].append(row)
            save()
        report["performance"] = comparison(report["forks"])
        for result in report["performance"].values():
            result["java_ns_per_query"] = result.pop("java_median_ms") * 1e6
            result["native_ns_per_query"] = result.pop("native_median_ms") * 1e6
        save()
        print(json.dumps(report["performance"], indent=2), flush=True)
        if not all(r["passes_5_percent"] for r in report["performance"].values()):
            raise SystemExit("5% direct biome lookup performance gate FAILED")
    print(out / "results.json", flush=True)


if __name__ == "__main__":
    main()
