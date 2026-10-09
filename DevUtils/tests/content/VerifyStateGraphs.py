#!/usr/bin/env python3
"""Compare every block/fluid state graph with Frozen and measure fresh-JVM bootstrap.

This is graph/startup evidence only; runtime acceptance still needs RunValidation.
Frozen's existing main classes must be built beforehand. No Frozen sources change.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import statistics
import subprocess
import time

ROOT = Path(__file__).resolve().parents[3]
OBSERVER = Path(__file__).with_name("StateGraphReference.java")
# Bootstrap wraps stdout in Minecraft's logger; allow its prefix, but require
# one complete, unambiguous receipt with no trailing fields.
ROW = re.compile(r"\bSTATE_GRAPH_REFERENCE blocks=(\d+) fluids=(\d+) states=(\d+) "
                 r"transitions=(\d+) sha256=([0-9a-f]{64}) fluid_states=(\d+) fluid_sha256=([0-9a-f]{64}) property_definitions=(\d+) property_sha256=([0-9a-f]{64}) block_states=(\d+) block_sha256=([0-9a-f]{64}) physical_sha256=([0-9a-f]{64}) intrinsic_sha256=([0-9a-f]{64}) sound_events=(\d+) sound_profiles=(\d+) instruments=(\d+) offset_samples=(\d+) material_sha256=([0-9a-f]{64}) block_sets=(\d+) wood_types=(\d+) configured_blocks=(\d+) family_sha256=([0-9a-f]{64}) bootstrap_ns=(\d+) "
                 r"bootstrap_thread_bytes=(\d+)$", re.MULTILINE)
FIELDS = ("blocks", "fluids", "states", "transitions", "sha256", "fluid_states", "fluid_sha256", "property_definitions", "property_sha256", "block_states", "block_sha256", "physical_sha256", "intrinsic_sha256", "sound_events", "sound_profiles", "instruments", "offset_samples", "material_sha256", "block_sets", "wood_types", "configured_blocks", "family_sha256", "bootstrap_ns", "bootstrap_thread_bytes")
HASH_FIELDS = ("sha256", "fluid_sha256", "property_sha256", "block_sha256", "physical_sha256", "intrinsic_sha256", "material_sha256", "family_sha256")


def sha(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def command(argv: list[str], cwd: Path, log: Path, env: dict[str, str]) -> str:
    print(f"{log.name}", flush=True)
    with log.open("w") as stream:
        result = subprocess.run(argv, cwd=cwd, stdout=stream, stderr=subprocess.STDOUT,
                                env=env, timeout=600)
    if result.returncode:
        raise RuntimeError(f"Exit {result.returncode}; see {log}")
    return log.read_text(errors="replace")


def groovy_string(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"


def classpath(tree: Path, out: Path, side: str, env: dict[str, str]) -> str:
    target, init = out / f"{side}-classpath.txt", out / f"{side}-classpath.gradle"
    dependency = "dependsOn 'classes'; " if side == "current" else ""
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.register('writeStateGraphClasspath') { "
                    + dependency + "doLast { new File(" + groovy_string(str(target))
                    + ").text = gradle.rootProject.sourceSets.main.runtimeClasspath.asPath } } }\n")
    command([str(tree / "gradlew"), "-I", str(init), "-PmattmcRustProfile=release",
             "writeStateGraphClasspath", "--console=plain"], tree, out / f"{side}-build.log", env)
    cp = target.read_text().strip()
    if not cp or any(not Path(p).exists() for p in cp.split(os.pathsep)):
        raise RuntimeError(f"Missing classpath entries: {target}")
    return cp


def identity(tree: Path) -> dict:
    return {"root": str(tree),
            "head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=tree, text=True).strip(),
            "status": subprocess.check_output(["git", "status", "--porcelain=v1"], cwd=tree, text=True)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--frozen-repo", type=Path, default=ROOT.parent / "MattMC_JavaPerfTesting/MattMC")
    parser.add_argument("--output", type=Path, default=Path("build/state-graph-verification"))
    parser.add_argument("--java-home", type=Path, help="JDK 25 home; defaults to JAVA_HOME or java on PATH")
    parser.add_argument("--pairs", type=int, default=5, help="alternating fresh-JVM comparisons (at least three)")
    args = parser.parse_args()
    if args.pairs < 3:
        parser.error("At least three paired fresh-JVM comparisons are required")
    frozen = args.frozen_repo.resolve()
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / "build") or out == ROOT / "build" or out.exists():
        parser.error("--output must be a new directory below this checkout's build/")
    if frozen == ROOT or not (frozen / "build/classes/java/main/net/minecraft/server/Bootstrap.class").is_file():
        parser.error("Frozen must be a separate checkout with existing compiled main classes")
    home = args.java_home or (Path(os.environ["JAVA_HOME"]) if "JAVA_HOME" in os.environ else None)
    java = str(home / "bin/java") if home else shutil.which("java")
    javac = str(home / "bin/javac") if home else shutil.which("javac")
    if not java or not javac:
        parser.error("JDK java and javac are required")
    env = {**os.environ, "CARGO_PROFILE_RELEASE_STRIP": "none",
           "CARGO_PROFILE_RELEASE_DEBUG": "line-tables-only"}
    # No profiler, concurrent build, or game workload runs during these timings.
    out.mkdir(parents=True)
    report = {"schema": "mattmc-state-graph-verification-v8", "passed": False,
              "scope": "Block-set/wood definitions, codec/alias identities and all family bindings; all block/fluid domains, ordered states, defaults and transitions; all fluid IDs, intrinsic traits, "
                       "legacy block IDs and codec outputs/round trips; shared and integrated-content property definitions/codecs; all block state flags, lights, fluid associations, offsets and light-occlusion boxes; all 18 block physical settings, constructor-cache consistency and seven cached physical facts for every state; all cached/default map colors, copied color/emission functions and canonical fluid associations; all sound event identities/ranges, sound profiles/instruments, per-state sound/instrument bindings and exhaustive finite offset-table samples; fresh-JVM bootstrap only. "
                       "Does not establish gameplay, save lifecycle or full-client FPS parity.",
              "current": identity(ROOT), "frozen": identity(frozen), "pairs": [],
              "observer_sha256": sha(OBSERVER), "driver_sha256": sha(Path(__file__)),
              "java_version": subprocess.check_output([java, "--version"], text=True),
              "jvm_args": ["-Xms512m", "-Xmx3g", "-XX:+UseZGC", "-XX:+UseCompactObjectHeaders",
                           "--enable-native-access=ALL-UNNAMED"]}
    result_path = out / "results.json"

    def save() -> None:
        result_path.write_text(json.dumps(report, indent=2) + "\n")

    save()
    try:
        cp = {side: classpath(tree, out, side, env) for side, tree in (("current", ROOT), ("frozen", frozen))}
        sources = subprocess.check_output(["git", "ls-files", "-z", "src", "DevUtils/tests/content"], cwd=ROOT).split(b"\0")
        sources += subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard", "-z",
                                            "src", "DevUtils/tests/content"], cwd=ROOT).split(b"\0")
        report["sources"] = {os.fsdecode(p): sha(ROOT / os.fsdecode(p)) for p in sorted(set(sources))
                             if p and (ROOT / os.fsdecode(p)).is_file()}
        library = ROOT / "build/rust/native/mattmc_rust-linux-x64.so"
        report["native_sha256"] = sha(library)
        classes = out / "observer"
        classes.mkdir()
        command([javac, "-cp", cp["frozen"], "-d", str(classes), str(OBSERVER)], ROOT, out / "observer-compile.log", env)
        report["observer_class_sha256"] = sha(classes / "StateGraphReference.class")
        expected = None
        for pair in range(args.pairs):
            row = {}
            report["pairs"].append(row)
            for side in (("frozen", "current") if pair % 2 == 0 else ("current", "frozen")):
                tree = ROOT if side == "current" else frozen
                began = time.monotonic()
                output = command([java, *report["jvm_args"], "-Dmattmc.rust.natives.dir=" + str(tree / "build/rust/native"),
                                  "-cp", str(classes) + os.pathsep + cp[side], "StateGraphReference"],
                                 out, out / f"pair-{pair + 1}-{side}.log", env)
                found = ROW.findall(output)
                if len(found) != 1:
                    raise RuntimeError(f"Missing/ambiguous observer receipt: pair {pair + 1} {side}")
                receipt = {key: value if key in HASH_FIELDS else int(value) for key, value in zip(FIELDS, found[0])}
                if any(receipt[key] <= 0 for key in FIELDS if key not in HASH_FIELDS):
                    raise RuntimeError(f"Invalid observer counts/timings: {receipt}")
                semantics = {key: receipt[key] for key in FIELDS[:-2]}
                if expected is not None and semantics != expected:
                    raise RuntimeError(f"Graph mismatch: {semantics} vs {expected}")
                expected = semantics
                row[side] = {**receipt, "process_wall_s": time.monotonic() - began}
                save()
        report["semantics"] = expected
        report["bootstrap"] = {}
        for metric in ("bootstrap_ns", "bootstrap_thread_bytes"):
            medians = {side: statistics.median(pair[side][metric] for pair in report["pairs"])
                       for side in ("current", "frozen")}
            report["bootstrap"][metric] = {**medians, "current_over_frozen": medians["current"] / medians["frozen"],
                "paired_ratios": [pair["current"][metric] / pair["frozen"][metric] for pair in report["pairs"]]}
        if sha(library) != report["native_sha256"] or any(sha(ROOT / p) != h for p, h in report["sources"].items()):
            raise RuntimeError("Measured sources/native library changed during verification")
        if identity(frozen) != report["frozen"] or sha(OBSERVER) != report["observer_sha256"]:
            raise RuntimeError("Reference or observer changed during verification")
        report["passed"] = True
        report["integrity"] = {"source_hashes_match": True, "native_hash_matches": True, "reference_unchanged": True}
    except Exception as error:
        report["failure"] = str(error)
        raise
    finally:
        save()
    print(json.dumps({"semantics": report["semantics"], "bootstrap": report["bootstrap"]}, indent=2))
    print(result_path)


if __name__ == "__main__":
    main()
