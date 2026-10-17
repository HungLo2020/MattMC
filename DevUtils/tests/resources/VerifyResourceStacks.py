#!/usr/bin/env python3
"""Compare Frozen and Current resource stacks over the same content.

Runs ResourceStackReference in alternating fresh JVM pairs against Frozen's
and this checkout's classes. Both read the same packs: this checkout's
resources as a directory pack and as a vanilla-builder pack, a jar subset,
and synthetic fixtures; tags load from those stacks, and server registries
load from the vanilla data plus fixture packs for registry synchronization. Every record must match byte for byte, and each warm
timing median must stay within the performance gate (Current at most 10%
slower than Frozen by default).

Frozen's main classes must be built beforehand. No Frozen sources change.
"""
from __future__ import annotations

import argparse
import difflib
import json
import os
from pathlib import Path
import re
import shutil
import statistics
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "DevUtils/tests/content"))
from VerifyStateGraphs import classpath, command, identity, sha  # noqa: E402

OBSERVER = Path(__file__).with_name("ResourceStackReference.java")
ROW = re.compile(r"\bRESOURCE_STACK_REFERENCE sha256=([0-9a-f]{64}) client_list_read_ns=(\d+) client_lookup_ns=(\d+) "
                 r"server_list_read_ns=(\d+) server_lookup_ns=(\d+) client_manager_ns=(\d+) server_manager_ns=(\d+) tags_ns=(\d+) "
                 r"sync_ns=(\d+) retag_ns=(\d+) first_retag_ns=(\d+)$", re.MULTILINE)
TIMINGS = ("client_list_read_ns", "client_lookup_ns", "server_list_read_ns", "server_lookup_ns", "client_manager_ns", "server_manager_ns",
           "tags_ns", "sync_ns", "retag_ns")
# Reported but not gated: one cold sample per run (the first rebind after publishing).
REPORTED = ("first_retag_ns",)
# Directories copied into the jar subset, plus one jar-only file per pack type.
SUBSET = ("assets/minecraft/textures/block", "assets/minecraft/blockstates", "assets/minecraft/lang",
          "data/minecraft/recipe", "data/minecraft/tags/block")


def build_content(content: Path) -> None:
    resources = ROOT / "src/main/resources"
    content.mkdir(parents=True)
    (content / "resources").symlink_to(resources)
    pack = content / "packs/content_dir"
    pack.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text('{"pack":{"description":"content","pack_format":15,"supported_formats":[15,999]}}')
    (pack / "assets").symlink_to(resources / "assets")
    (pack / "data").symlink_to(resources / "data")
    with zipfile.ZipFile(content / "subset.jar", "w", zipfile.ZIP_DEFLATED) as jar:
        for directory in SUBSET:
            for file in sorted((resources / directory).rglob("*")):
                if file.is_file():
                    jar.write(file, file.relative_to(resources).as_posix())
        jar.writestr("assets/minecraft/fixture/jar_only.json", '{"jar":true}')
        jar.writestr("data/minecraft/fixture/jar_only.json", '{"jar":true}')
        # A directory pack inside the archive (the game jar's built-in data packs).
        meta = '{"pack":{"description":"in jar","pack_format":15,"supported_formats":[15,999]},' \
               '"overlays":{"entries":[{"formats":[15,999],"directory":"jar_overlay"}]}}'
        jar.writestr("packs_in_jar/jar_dir_pack/pack.mcmeta", meta)
        for side in ("assets", "data"):
            jar.writestr(f"packs_in_jar/jar_dir_pack/{side}/minecraft/fixture/in_jar.json", '{"in_jar":1}')
            jar.writestr(f"packs_in_jar/jar_dir_pack/{side}/minecraft/fixture/shared.json", '{"from":"jar pack"}')
            jar.writestr(f"packs_in_jar/jar_dir_pack/jar_overlay/{side}/minecraft/fixture/in_jar.json", '{"overlaid":1}')
            jar.writestr(f"packs_in_jar/jar_dir_pack/{side}/jarns/fixture/j.json", '{}')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--frozen-repo", type=Path, default=ROOT.parent / "MattMC_JavaPerfTesting/MattMC")
    parser.add_argument("--output", type=Path, default=Path("build/resource-stack-verification"))
    parser.add_argument("--java-home", type=Path, help="JDK 25 home; defaults to JAVA_HOME or java on PATH")
    parser.add_argument("--pairs", type=int, default=5, help="alternating fresh-JVM comparisons (at least three)")
    parser.add_argument("--iterations", type=int, default=7, help="warm timing iterations per run")
    parser.add_argument("--max-ratio", type=float, default=1.10, help="largest allowed Current/Frozen timing median")
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
    env = {**os.environ, "CARGO_PROFILE_RELEASE_STRIP": "none", "CARGO_PROFILE_RELEASE_DEBUG": "line-tables-only"}
    out.mkdir(parents=True)
    report = {"schema": "mattmc-resource-stack-verification-v1", "passed": False,
              "scope": "Pack lookups, listings, stacks, namespaces, root files, pack.mcmeta sections, sidecar metadata, repository "
                       "metadata and selection over the checkout's resources (directory, vanilla-builder and jar roots) and synthetic "
                       "fixtures; tag loading per registry directory; registry-data entries and update-tags payloads for three "
                       "client known-pack sets before and after static tag rebinds; warm list+read, lookup, manager-construction, "
                       "tag, sync and retag timings. Does not establish in-game reload, connection or gameplay behavior.",
              "current": identity(ROOT), "frozen": identity(frozen), "pairs": [], "max_ratio": args.max_ratio,
              "observer_sha256": sha(OBSERVER), "driver_sha256": sha(Path(__file__)),
              "jvm_args": ["-Xms512m", "-Xmx3g", "-XX:+UseZGC", "-XX:+UseCompactObjectHeaders", "--enable-native-access=ALL-UNNAMED"]}
    result_path = out / "results.json"

    def save() -> None:
        result_path.write_text(json.dumps(report, indent=2) + "\n")

    save()
    try:
        cp = {side: classpath(tree, out, side, env) for side, tree in (("current", ROOT), ("frozen", frozen))}
        library = ROOT / "build/rust/native/mattmc_rust-linux-x64.so"
        report["native_sha256"] = sha(library)
        content = out / "content"
        build_content(content)
        classes = out / "observer"
        classes.mkdir()
        command([javac, "-cp", cp["frozen"], "-d", str(classes), str(OBSERVER)], ROOT, out / "observer-compile.log", env)
        reference = None
        for pair in range(args.pairs):
            row = {}
            report["pairs"].append(row)
            for side in (("frozen", "current") if pair % 2 == 0 else ("current", "frozen")):
                tree = ROOT if side == "current" else frozen
                fixtures = out / f"fixtures-{pair + 1}-{side}"
                record = out / f"record-{pair + 1}-{side}.txt"
                began = time.monotonic()
                output = command([java, *report["jvm_args"], "-Dmattmc.rust.natives.dir=" + str(tree / "build/rust/native"),
                                  "-cp", str(classes) + os.pathsep + cp[side], "ResourceStackReference",
                                  str(content), str(fixtures), str(record), str(args.iterations)],
                                 out, out / f"pair-{pair + 1}-{side}.log", env)
                found = ROW.findall(output)
                if len(found) != 1:
                    raise RuntimeError(f"Missing/ambiguous observer receipt: pair {pair + 1} {side}")
                receipt = {"sha256": found[0][0], **{key: int(value) for key, value in zip(TIMINGS + REPORTED, found[0][1:])}}
                row[side] = {**receipt, "process_wall_s": time.monotonic() - began}
                save()
                text = record.read_text()
                if reference is None:
                    reference = (record, text)
                elif text != reference[1]:
                    diff = list(difflib.unified_diff(reference[1].splitlines(), text.splitlines(),
                                                     str(reference[0].name), str(record.name), lineterm="", n=1))
                    (out / "mismatch.diff").write_text("\n".join(diff) + "\n")
                    raise RuntimeError(f"Record mismatch ({len(diff)} diff lines); see {out / 'mismatch.diff'}")
        report["records_identical"] = True
        report["record_lines"] = len(reference[1].splitlines())
        report["timings"] = {}
        failures = []
        for metric in TIMINGS:
            medians = {side: statistics.median(pair[side][metric] for pair in report["pairs"]) for side in ("current", "frozen")}
            ratio = medians["current"] / medians["frozen"]
            report["timings"][metric] = {**medians, "current_over_frozen": ratio,
                                         "paired_ratios": [pair["current"][metric] / pair["frozen"][metric] for pair in report["pairs"]]}
            if ratio > args.max_ratio:
                failures.append(f"{metric} {ratio:.3f}")
        for metric in REPORTED:
            medians = {side: statistics.median(pair[side][metric] for pair in report["pairs"]) for side in ("current", "frozen")}
            report["timings"][metric] = {**medians, "current_over_frozen": medians["current"] / medians["frozen"], "gated": False}
        if sha(library) != report["native_sha256"] or identity(frozen) != report["frozen"]:
            raise RuntimeError("Native library or reference changed during verification")
        if failures:
            raise RuntimeError("Performance gate failed: " + ", ".join(failures))
        report["passed"] = True
    except Exception as error:
        report["failure"] = str(error)
        raise
    finally:
        save()
    print(json.dumps({"timings": report.get("timings"), "record_lines": report.get("record_lines")}, indent=2))
    print(result_path)


if __name__ == "__main__":
    main()
