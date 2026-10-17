#!/usr/bin/env python3
"""Compare every registered entity type's definition with Frozen.

Compiles EntityTypeReference once against Frozen's classpath and runs it in
fresh JVMs on Frozen and Current, alternating. The full dumps must be
identical. This is definition evidence only: it does not establish gameplay,
save lifecycle or full-client parity. Frozen's main classes must already be
built; no Frozen source changes.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import sys

sys.path.insert(0, str(Path(__file__).parent))
from VerifyStateGraphs import ROOT, classpath, command, identity, sha  # noqa: E402

OBSERVER = Path(__file__).with_name("EntityTypeReference.java")
ROW = re.compile(r"\bENTITY_TYPE_REFERENCE types=(\d+) sha256=([0-9a-f]{64}) bootstrap_ns=(\d+)$", re.MULTILINE)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--frozen-repo", type=Path, default=ROOT.parent / "MattMC_JavaPerfTesting/MattMC")
    parser.add_argument("--output", type=Path, default=Path("build/entity-type-verification"))
    parser.add_argument("--java-home", type=Path, help="JDK 25 home; defaults to JAVA_HOME or java on PATH")
    parser.add_argument("--pairs", type=int, default=2, help="alternating fresh-JVM comparisons")
    # --frozen-repo may also name a pre-change Current checkout, for a matched bootstrap timing.
    args = parser.parse_args()
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
    env = dict(os.environ)
    out.mkdir(parents=True)
    report = {"schema": "mattmc-entity-type-verification-v1", "passed": False,
              "scope": "Every registered entity type's ID, key, category, flags, immune blocks, dimensions, "
                       "eye height, all attachment points, spawn scale, tracking range, update interval, "
                       "description ID, default loot table and required features. Fresh-JVM bootstrap only.",
              "current": identity(ROOT), "frozen": identity(frozen), "runs": [],
              "observer_sha256": sha(OBSERVER), "driver_sha256": sha(Path(__file__))}
    try:
        cp = {side: classpath(tree, out, side, env) for side, tree in (("current", ROOT), ("frozen", frozen))}
        classes = out / "observer"
        classes.mkdir()
        command([javac, "-cp", cp["frozen"], "-d", str(classes), str(OBSERVER)], ROOT, out / "observer-compile.log", env)
        dumps = {}
        for pair in range(args.pairs):
            for side in (("frozen", "current") if pair % 2 == 0 else ("current", "frozen")):
                tree = ROOT if side == "current" else frozen
                dump = out / f"{side}-{pair + 1}.txt"
                output = command([java, "-Xmx3g", "--enable-native-access=ALL-UNNAMED",
                                  "-Dmattmc.rust.natives.dir=" + str(tree / "build/rust/native"),
                                  "-cp", str(classes) + os.pathsep + cp[side], "EntityTypeReference", str(dump)],
                                 out, out / f"{side}-{pair + 1}.log", env)
                found = ROW.findall(output)
                if len(found) != 1:
                    raise RuntimeError(f"Missing/ambiguous observer receipt: {side} {pair + 1}")
                report["runs"].append({"side": side, "types": int(found[0][0]), "sha256": found[0][1],
                                       "bootstrap_ms": int(found[0][2]) / 1e6})
                dumps.setdefault(side, dump.read_text())
                if dump.read_text() != dumps[side]:
                    raise RuntimeError(f"{side} dump changed between runs")
        if dumps["frozen"] != dumps["current"]:
            frozen_rows, current_rows = dumps["frozen"].splitlines(), dumps["current"].splitlines()
            first = next((i for i, (a, b) in enumerate(zip(frozen_rows, current_rows)) if a != b), min(len(frozen_rows), len(current_rows)))
            raise RuntimeError(f"Entity type definitions differ at row {first}:\n frozen:  "
                               f"{frozen_rows[first] if first < len(frozen_rows) else '<end>'}\n current: "
                               f"{current_rows[first] if first < len(current_rows) else '<end>'}")
        for side in ("frozen", "current"):
            times = sorted(run["bootstrap_ms"] for run in report["runs"] if run["side"] == side)
            report[f"{side}_bootstrap_median_ms"] = times[len(times) // 2]
        report["passed"] = True
    finally:
        (out / "results.json").write_text(json.dumps(report, indent=2) + "\n")
    print(f"PASS {report['runs'][0]['types']} entity types match; bootstrap median "
          f"{report['frozen_bootstrap_median_ms']:.0f} ms reference / {report['current_bootstrap_median_ms']:.0f} ms current; "
          f"{out / 'results.json'}")


if __name__ == "__main__":
    main()
