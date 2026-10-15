#!/usr/bin/env python3
"""Compare content definition dumps between a reference checkout and Current.

Compiles a dump observer (default ContentReference) once against the
reference classpath and runs it in alternating fresh JVMs on both sides. The
full dumps must be identical; the first difference is reported. The reference
is Frozen by default; pass --reference-repo to compare against another build,
such as pre-change Current. Definition evidence only, not gameplay acceptance.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import sys

sys.path.insert(0, str(Path(__file__).parent))
from VerifyStateGraphs import ROOT, classpath, command, identity, sha  # noqa: E402


def first_difference(reference: str, current: str) -> str:
    a, b = reference.splitlines(), current.splitlines()
    i = next((i for i, (x, y) in enumerate(zip(a, b)) if x != y), min(len(a), len(b)))
    return (f"row {i}:\n reference: {a[i] if i < len(a) else '<end>'}\n"
            f" current:   {b[i] if i < len(b) else '<end>'}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference-repo", type=Path, default=ROOT.parent / "MattMC_JavaPerfTesting/MattMC")
    parser.add_argument("--observer", default="ContentReference", help="observer class in DevUtils/tests/content")
    parser.add_argument("--sections", nargs="*", default=[], help="observer sections to compare (default all)")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--java-home", type=Path)
    parser.add_argument("--pairs", type=int, default=1)
    args = parser.parse_args()
    reference = args.reference_repo.resolve()
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / "build") or out.exists():
        parser.error("--output must be a new directory below this checkout's build/")
    if reference == ROOT or not (reference / "build/classes/java/main/net/minecraft/server/Bootstrap.class").is_file():
        parser.error("The reference must be a separate checkout with compiled main classes")
    home = args.java_home or (Path(os.environ["JAVA_HOME"]) if "JAVA_HOME" in os.environ else None)
    java = str(home / "bin/java") if home else shutil.which("java")
    javac = str(home / "bin/javac") if home else shutil.which("javac")
    observer = Path(__file__).with_name(args.observer + ".java")
    env = dict(os.environ)
    out.mkdir(parents=True)
    report = {"schema": "mattmc-content-verification-v1", "observer": args.observer, "sections": args.sections,
              "passed": False, "current": identity(ROOT), "reference": identity(reference), "runs": [],
              "observer_sha256": sha(observer), "driver_sha256": sha(Path(__file__))}
    try:
        cp = {side: classpath(tree, out, side, env) for side, tree in (("current", ROOT), ("frozen", reference))}
        classes = out / "observer"
        classes.mkdir()
        command([javac, "-cp", cp["frozen"], "-d", str(classes), str(observer)], ROOT, out / "observer-compile.log", env)
        dumps = {}
        for pair in range(args.pairs):
            for side in (("frozen", "current") if pair % 2 == 0 else ("current", "frozen")):
                tree = ROOT if side == "current" else reference
                dump = out / f"{side}-{pair + 1}.txt"
                # Implicit exceptions keep their detailed messages however warm
                # the JIT is, so recorded throw messages compare deterministically.
                output = command([java, "-Xmx4g", "-XX:-OmitStackTraceInFastThrow", "--enable-native-access=ALL-UNNAMED",
                                  "-Dmattmc.rust.natives.dir=" + str(tree / "build/rust/native"),
                                  "-cp", str(classes) + os.pathsep + cp[side], args.observer, str(dump), *args.sections],
                                 out, out / f"{side}-{pair + 1}.log", env)
                receipt = [line for line in output.splitlines() if "_REFERENCE" in line]
                report["runs"].append({"side": "reference" if side == "frozen" else side, "receipt": receipt[-1] if receipt else None})
                text = dump.read_text()
                if dumps.setdefault(side, text) != text:
                    raise RuntimeError(f"{side} dump changed between runs")
        if dumps["frozen"] != dumps["current"]:
            raise RuntimeError("Content definitions differ at " + first_difference(dumps["frozen"], dumps["current"]))
        report["passed"] = True
        report["rows"] = len(dumps["current"].splitlines())
    finally:
        (out / "results.json").write_text(json.dumps(report, indent=2) + "\n")
    print(f"PASS {report['rows']} content rows identical; {out / 'results.json'}")


if __name__ == "__main__":
    main()
