#!/usr/bin/env python3
"""Time content startup on a reference checkout and Current, alternating
fresh JVMs (DevUtils/tests/content/ContentTiming.java, or --observer such as
ValueTiming), and report medians.
Performance evidence for content ownership changes, not gameplay acceptance.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import statistics
import sys

sys.path.insert(0, str(Path(__file__).parent))
from VerifyStateGraphs import ROOT, classpath, command, identity  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference-repo", type=Path, default=ROOT.parent / "MattMC_JavaPerfTesting/MattMC")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--pairs", type=int, default=5)
    parser.add_argument("--rounds", type=int, default=50)
    parser.add_argument("--java-home", type=Path)
    parser.add_argument("--observer", default="ContentTiming", help="timing class in DevUtils/tests/content")
    args = parser.parse_args()
    reference = args.reference_repo.resolve()
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / "build") or out.exists():
        parser.error("--output must be a new directory below this checkout's build/")
    home = args.java_home or (Path(os.environ["JAVA_HOME"]) if "JAVA_HOME" in os.environ else None)
    java = str(home / "bin/java") if home else shutil.which("java")
    javac = str(home / "bin/javac") if home else shutil.which("javac")
    env = dict(os.environ)
    out.mkdir(parents=True)
    cp = {side: classpath(tree, out, side, env) for side, tree in (("current", ROOT), ("reference", reference))}
    classes = out / "observer"
    classes.mkdir()
    command([javac, "-cp", cp["reference"], "-d", str(classes), str(Path(__file__).with_name(args.observer + ".java"))], ROOT, out / "compile.log", env)
    samples: dict[str, list[dict[str, float]]] = {"reference": [], "current": []}
    for pair in range(args.pairs):
        for side in (("reference", "current") if pair % 2 == 0 else ("current", "reference")):
            tree = ROOT if side == "current" else reference
            output = command([java, "-Xmx4g", "--enable-native-access=ALL-UNNAMED", "-Dmattmc.rust.natives.dir=" + str(tree / "build/rust/native"),
                              "-cp", str(classes) + os.pathsep + cp[side], args.observer, str(args.rounds)],
                             out, out / f"{side}-{pair + 1}.log", env)
            line = next(l for l in output.splitlines() if "_TIMING " in l)
            samples[side].append({k: float(v) for k, v in re.findall(r"(\w+)=([\d.]+)", line.split("_TIMING ", 1)[1])})
    medians = {side: {k: statistics.median(s[k] for s in runs) for k in runs[0]} for side, runs in samples.items()}
    report = {"schema": "mattmc-content-timing-v1", "current": identity(ROOT), "reference": identity(reference),
              "samples": samples, "medians": medians}
    (out / "results.json").write_text(json.dumps(report, indent=2) + "\n")
    for key in medians["current"]:
        print(f"{key}: reference {medians['reference'][key]:.3f}  current {medians['current'][key]:.3f}")


if __name__ == "__main__":
    main()
