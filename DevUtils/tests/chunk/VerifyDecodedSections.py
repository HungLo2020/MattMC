#!/usr/bin/env python3
"""Replay actual Frozen saved chunks, then compare complete region-read/parse cost.

Compile release main/test classes first. Do not overlap timed runs with builds,
clients or profiling. Every round, JIT outlier and failed pair is retained.
"""
import argparse
import gzip
import json
import os
from pathlib import Path
import statistics
import subprocess

from VerifySavedPaletteLoading import ROOT, FROZEN, inventory, sha

OUT = ROOT / "build/native-saved-palettes/chunk-loader"
DRIVER = ROOT / "src/test/java/net/minecraft/world/level/chunk/storage/DecodedChunkVerification.java"
FIXTURE = ROOT / "src/test/resources/storage/frozen-decoded-chunks.bin.gz"
NATIVE = ROOT / "build/rust/native/mattmc_rust-linux-x64.so"
CLASSES = ("storage/SerializableChunkData", "storage/RegionFileStorage", "PalettedContainer", "DataLayer")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--benchmark", action="store_true")
    mode.add_argument("--profile", choices=("current", "frozen"), help="Record JFR over 1,000 warmed corpus loads")
    parser.add_argument("--pairs", type=int, default=3)
    parser.add_argument("--cpu", type=int, default=max(os.sched_getaffinity(0)))
    parser.add_argument("--reference", choices=("frozen", "predecessor", "saved-owner-checkpoint", "hydration-checkpoint"), default="frozen")
    options = parser.parse_args()
    if not options.java_home or options.pairs < 2 or options.cpu not in os.sched_getaffinity(0):
        parser.error("JDK25, at least two pairs and an available CPU are required")
    jdk = Path(options.java_home) / "bin"
    OUT.mkdir(parents=True, exist_ok=True)
    cp = {side: (ROOT / f"build/native-chunk-tasks/{side}-classpath.txt").read_text().strip()
          for side in ("current", "frozen")}
    native_dirs = {"current": NATIVE.parent, "frozen": NATIVE.parent}
    if options.reference == "predecessor":
        cp[options.reference] = (OUT.parent / "predecessor-classpath.txt").read_text().strip()
        native_dirs[options.reference] = OUT.parent / "predecessor-native"
    elif options.reference in ("saved-owner-checkpoint", "hydration-checkpoint"):
        checkpoint = OUT.parent / options.reference
        cp[options.reference] = (checkpoint / "classpath.txt").read_text().strip()
        native_dirs[options.reference] = checkpoint / "native"

    def snapshot():
        pins = {"current_sources": inventory(ROOT), "frozen_sources": inventory(FROZEN),
                "native": sha(NATIVE), "fixture": sha(FIXTURE), "driver": sha(DRIVER),
                "prompt": sha(ROOT / "docs/STANDARD-COPILOT-PROMPTS.md"),
                "frozen_head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=FROZEN, text=True).strip(),
                "classes": {side: {str(p): sha(p) for base in cp[side].split(os.pathsep)
                                    if Path(base).is_dir() for p in Path(base).rglob("*.class")}
                            for side in cp}}
        if options.reference != "frozen":
            pins["reference_native"] = sha(native_dirs[options.reference] / NATIVE.name)
        return pins

    pins = snapshot()
    assert pins["frozen_head"] == "7a4d181717dc0c7da9086f1a17687dfd522ce417"
    assert pins["prompt"] == "c5695e9bc559fb430bc3ee14721785a28c6de7e0f7cae7d70b2dad0f41cd94c5"
    old = ROOT / "build/native-chunk-tasks/frozen-source-pins.json"
    if old.exists():
        assert pins["frozen_sources"] == json.loads(old.read_text())["files"]
    classes = OUT / "oracle"
    classes.mkdir(exist_ok=True)

    def run(command, name):
        log = OUT / name
        with log.open("w") as stream:
            status = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT).returncode
        if status:
            raise RuntimeError(f"Exit {status}: {log}")
        return log.read_text()

    run([str(jdk / "javac"), "-cp", cp["frozen"], "-d", str(classes), str(DRIVER)], "compile.log")
    compiled = {str(p): sha(p) for p in classes.rglob("*.class")}

    def java(side, mode, world, oracle=None):
        command = [str(jdk / "java"), "--enable-native-access=ALL-UNNAMED",
                   "-Dmattmc.rust.natives.dir=" + str(native_dirs[side]),
                   "-cp", str(classes) + os.pathsep + cp[side],
                   "net.minecraft.world.level.chunk.storage.DecodedChunkVerification", mode, str(world)]
        if oracle:
            command.append(str(oracle))
        if mode in ("bench", "profile"):
            command[1:1] = ["-Xms512m", "-Xmx512m", "-XX:-TieredCompilation", "-XX:CompileThreshold=1000", "-Xbatch"]
            command = ["taskset", "-c", str(options.cpu), *command]
        return command

    def check_images(side, output):
        for name in CLASSES:
            resource = "net/minecraft/world/level/chunk/" + name + ".class"
            resolved = next(Path(base) / resource for base in cp[side].split(os.pathsep) if (Path(base) / resource).is_file())
            qualified = resource.removesuffix(".class").replace("/", ".")
            assert f"CLASS_IMAGE {qualified} " in output
            line = next(line.split("CLASS_IMAGE ", 1)[1] for line in output.splitlines()
                        if f"CLASS_IMAGE {qualified} " in line)
            assert str(resolved) in line and line.endswith(sha(resolved)), line
            if side == "frozen":
                assert resolved.is_relative_to(FROZEN / "build/classes/java/main"), line
        if side != "frozen":
            assert "NATIVE_IMAGE " + str((native_dirs[side] / NATIVE.name).resolve()) in output

    result = {"pins": pins, "driver_classes": compiled, "protocol": {"cpu": options.cpu,
              "warmups": 200, "rounds": 8, "saved_chunks_per_round": 40, "pairs": options.pairs},
              "pairs": [], "guards_pass": False, "performance_pass": False}
    target = OUT / ("results-" + ("profile-" + options.profile if options.profile else options.reference) + ".json")
    target.write_text(json.dumps(result, indent=2) + "\n")
    try:
        fresh = OUT / "rerecorded.bin.gz"
        output = run(java("frozen", "record", OUT / "replay-frozen-world", fresh), "rerecord.log")
        check_images("frozen", output)
        assert gzip.decompress(fresh.read_bytes()) == gzip.decompress(FIXTURE.read_bytes())
        output = run(java("current", "verify", OUT / "replay-current-world", FIXTURE), "replay.log")
        check_images("current", output)
        result["frozen_parity_pass"] = True
        if options.benchmark:
            for pair in range(options.pairs):
                row = {"pair": pair}
                for side in (["current", options.reference] if pair % 2 == 0 else [options.reference, "current"]):
                    output = run(java(side, "bench", OUT / f"timing-{options.reference}-{pair}-{side}-world"),
                                 f"timing-{options.reference}-{pair}-{side}.log")
                    check_images(side, output)
                    values = [dict(zip(("nanoseconds", "checksum", "compilation_ms"),
                                       map(int, line.split("TIMING ", 1)[1].split())))
                              for line in output.splitlines() if "TIMING " in line]
                    row[side] = values
                    assert len(values) == 8
                result["pairs"].append(row)
                assert len({value["checksum"] for side in ("current", options.reference) for value in row[side]}) == 1
                row["ratio"] = statistics.median(v["nanoseconds"] for v in row["current"]) / statistics.median(v["nanoseconds"] for v in row[options.reference])
                row["jit_clean"] = all(v["compilation_ms"] == 0 for side in ("current", options.reference) for v in row[side])
                target.write_text(json.dumps(result, indent=2) + "\n")
                print("pair", pair, "ratio", round(row["ratio"], 4), "jit_clean", row["jit_clean"], flush=True)
                assert row["jit_clean"], "Measured compilation; all rounds retained"
            result["performance_pass"] = all(row["ratio"] <= 1 for row in result["pairs"])
        elif options.profile:
            side = options.profile
            recording = OUT / f"profile-{side}.jfr"
            output = run(java(side, "profile", OUT / f"profile-{side}-world", recording), f"profile-{side}.log")
            check_images(side, output)
            assert "PROFILE_DONE " in output
            result["profile"] = {"side": side, "recording": str(recording), "chunks": 40000,
                                 "scope": "warmed loader with JFR; diagnostic, not timing acceptance"}
            run([str(jdk / "jfr"), "print", "--json", "--events",
                 "jdk.ExecutionSample,jdk.NativeMethodSample,jdk.ObjectAllocationSample,jdk.GarbageCollection",
                 str(recording)], f"profile-{side}-events.json")
    except BaseException as failure:
        result["failure"] = repr(failure)
        raise
    finally:
        result["guards_pass"] = snapshot() == pins and compiled == {str(p): sha(p) for p in classes.rglob("*.class")}
        target.write_text(json.dumps(result, indent=2) + "\n")
    assert result["guards_pass"], "Source/class/native inputs changed"
    print("PASS: actual Frozen replay; performance_pass=" + str(result["performance_pass"]), flush=True)
    if options.benchmark and not result["performance_pass"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
