#!/usr/bin/env python3
"""Build the JDK25-only copied-cow fixture adapter; no game class replacement."""
import argparse
import hashlib
import json
import subprocess
import shlex
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--frozen-repo", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[3]
    output = args.output.resolve()
    if output.exists() or not output.is_relative_to(root / "build"):
        parser.error("Output must be a new directory under this checkout's build/")
    output.mkdir(parents=True)
    classes = output / "classes"
    classes.mkdir()
    jdk = args.java_home.resolve() / "bin"
    sources = [Path(__file__).with_name(name) for name in
               ["LeashedCowFixtureAgent.java", "LeashedCowFixtureHook.java"]]
    subprocess.run([str(jdk / "javac"), "-d", str(classes), *map(str, sources)], check=True)
    manifest = output / "MANIFEST.MF"
    manifest.write_text("Premain-Class: leashedcowfixture.LeashedCowFixtureAgent\n\n")
    agent, hook = output / "agent.jar", output / "hook.jar"
    subprocess.run([str(jdk / "jar"), "cfm", str(agent), str(manifest), "-C", str(classes),
                    "leashedcowfixture/LeashedCowFixtureAgent.class", "-C", str(classes),
                    "leashedcowfixture/LeashedCowFixtureAgent$1.class"], check=True)
    subprocess.run([str(jdk / "jar"), "cf", str(hook), "-C", str(classes),
                    "leashedcowfixture/LeashedCowFixtureHook.class"], check=True)
    prefix = (args.frozen_repo.resolve() / "build").as_uri() + "/"
    flags = ["-javaagent:" + str(agent) + "=" + prefix, "-Xbootclasspath/a:" + str(hook)]
    subprocess.run([str(jdk / "java"), *flags, "-version"], check=True, capture_output=True)
    pins = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in [*sources, agent, hook]}
    (output / "fixture.json").write_text(json.dumps({"jvm_args": flags, "hashes": pins,
        "scope": "Frozen capture helper only: attach its disposable cow to its single server player before spawn. No gameplay/renderer/registry transformation; requires cow leash scenario."}, indent=2) + "\n")
    print("Fixture adapter startup check PASS; launch the harness with the same JDK:")
    print("export JAVA_HOME=" + shlex.quote(str(args.java_home.resolve())))
    print('export PATH="$JAVA_HOME/bin:$PATH"')
    print("Pass these shared capture arguments:")
    for flag in flags:
        print("--jvm-arg=" + flag)


if __name__ == "__main__":
    main()
