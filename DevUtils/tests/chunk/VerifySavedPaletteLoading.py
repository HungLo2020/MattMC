#!/usr/bin/env python3
"""Replay untouched Frozen's public saved-palette loader and time complete callers.

Build the release and test classes first. Timing is deliberately sequential;
run without other clients, builds or profilers. Receipts identify both inputs.
"""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import statistics
import subprocess

ROOT = Path(__file__).resolve().parents[3]
FROZEN = ROOT.parent / "MattMC_JavaPerfTesting" / "MattMC"
OUT = ROOT / "build/native-saved-palettes"
DRIVER = ROOT / "src/test/java/net/minecraft/world/level/chunk/SavedPaletteVerification.java"
FIXTURE = ROOT / "src/main/rust/world/level/chunk/palette/frozen-saved-palettes.bin.gz"
CLASSES = ["PalettedContainer", "Strategy", "SingleValuePalette", "LinearPalette", "HashMapPalette", "GlobalPalette"]

def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

def inventory(root):
    paths = subprocess.check_output(["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z",
                                     "--", "src", "DevUtils", "build.gradle"], cwd=root).split(b"\0")
    return {os.fsdecode(path): sha(root / os.fsdecode(path)) for path in paths if path and (root / os.fsdecode(path)).is_file()}

def run(args, log):
    with log.open("w") as output:
        result = subprocess.run(args, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(f"Exit {result.returncode}: {log}")
    return log.read_text()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--benchmark", action="store_true")
    parser.add_argument("--pairs", type=int, default=3)
    parser.add_argument("--iterations", type=int, default=1000)
    parser.add_argument("--warmups", type=int, default=25)
    parser.add_argument("--reference", choices=("frozen", "predecessor"), default="frozen")
    parser.add_argument("--cpu", type=int, default=max(os.sched_getaffinity(0)))
    options = parser.parse_args()
    if not options.java_home:
        parser.error("Set JAVA_HOME to JDK25 or pass --java-home")
    if options.pairs < 2 or options.iterations < 1 or options.warmups < 10:
        parser.error("At least two pairs, ten warmups and positive iterations are required")
    if options.cpu not in os.sched_getaffinity(0):
        parser.error("CPU is outside the process affinity")
    OUT.mkdir(exist_ok=True)
    jdk = Path(options.java_home) / "bin"
    cp = {side: (ROOT / f"build/native-chunk-tasks/{side}-classpath.txt").read_text().strip() for side in ("current", "frozen")}
    if options.reference == "predecessor":
        cp["predecessor"] = (OUT/"predecessor-classpath.txt").read_text().strip()
    native = ROOT / "build/rust/native/mattmc_rust-linux-x64.so"
    pins = {"head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
            "frozen_head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=FROZEN, text=True).strip(),
            "native": sha(native), "driver": sha(DRIVER), "fixture": sha(FIXTURE),
            "frozen_classes": {name: sha(FROZEN / f"build/classes/java/main/net/minecraft/world/level/chunk/{name}.class") for name in CLASSES}}
    assert pins["frozen_head"] == "7a4d181717dc0c7da9086f1a17687dfd522ce417"
    pins["current_sources"] = inventory(ROOT)
    pins["frozen_sources"] = inventory(FROZEN)
    pins["current_classes"] = {str(path.relative_to(ROOT)): sha(path) for path in
        (ROOT/"build/classes/java/main/net/minecraft/world/level/chunk").rglob("*.class")}
    pins["protected_prompt"] = sha(ROOT/"docs/STANDARD-COPILOT-PROMPTS.md")
    if options.reference == "predecessor":
        pins["predecessor_native"] = sha(OUT/"predecessor-native/mattmc_rust-linux-x64.so")
        pins["predecessor_classes"] = {str(path.relative_to(OUT)):sha(path) for path in (OUT/"predecessor-classes").rglob("*.class")}
    assert pins["protected_prompt"] == "c5695e9bc559fb430bc3ee14721785a28c6de7e0f7cae7d70b2dad0f41cd94c5"
    prior=ROOT/"build/native-chunk-tasks/frozen-source-pins.json"
    if prior.is_file():
        assert pins["frozen_sources"] == json.loads(prior.read_text())["files"]
    classes = OUT / "oracle"
    classes.mkdir(exist_ok=True)
    run([str(jdk/"javac"), "-cp", cp["frozen"], "-d", str(classes), str(DRIVER)], OUT/"oracle-compile.log")
    pins["driver_classes"] = {str(path.relative_to(OUT)): sha(path) for path in classes.rglob("*.class")}
    def java(side, *args):
        native_dir=OUT/"predecessor-native" if side=="predecessor" else native.parent
        command=[str(jdk/"java"), "--enable-native-access=ALL-UNNAMED", "-Dmattmc.rust.natives.dir="+str(native_dir),
                "-cp", str(classes)+":"+cp[side], "net.minecraft.world.level.chunk.SavedPaletteVerification", *map(str,args)]
        if args[0]=="bench":
            command[1:1]=["-Xms512m","-Xmx512m","-XX:-TieredCompilation","-XX:CompileThreshold=1000","-Xbatch"]
            return ["taskset","-c",str(options.cpu),*command]
        return command
    fresh = OUT/"frozen-saved-palettes-rerecorded.bin.gz"
    run(java("frozen", "record", fresh), OUT/"oracle-rerecord.log")
    assert gzip.decompress(fresh.read_bytes()) == gzip.decompress(FIXTURE.read_bytes()), "Frozen oracle changed"
    run(java("current", "verify", FIXTURE), OUT/"oracle-replay.log")
    reference=options.reference
    result_path=OUT/f"caller-results-{reference}.json"
    results = {"pins": pins, "oracle_cases": 82, "reference":reference, "cpu":options.cpu,
               "protocol":{"warmups":options.warmups,"rounds":8,"pairs":options.pairs,"iterations":options.iterations,
                           "jvm":"512MiB heap, synchronous C2 compilation, threshold1000; supplemental steady-state caller timing",
                           "measured_compilation_ms_required":0}, "pairs": []}
    if options.benchmark:
        for kind, count in [("blocks",1),("blocks",2),("blocks",17),("blocks",129),("blocks",257),("blocks",4096),
                            ("biomes",1),("biomes",2),("biomes",5),("biomes",9),("biomes",65)]:
            for pair in range(options.pairs):
                row = {"kind":kind,"count":count,"pair":pair,"iterations":options.iterations}
                for side in (["current",reference] if pair%2==0 else [reference,"current"]):
                    log=OUT/f"timing-{reference}-{kind}-{count}-{pair}-{side}.log"
                    output=run(java(side,"bench",kind,count,options.iterations,options.warmups),log)
                    expected_native=(OUT/"predecessor-native/mattmc_rust-linux-x64.so" if side=="predecessor" else native).resolve()
                    if side!="frozen":
                        assert "NATIVE_IMAGE "+str(expected_native) in output, f"Wrong native consumer: {log}"
                    values=[]
                    for line in output.splitlines():
                        if "TIMING " in line:
                            elapsed,checksum,compilation=map(int,line.split("TIMING ",1)[1].split())
                            values.append({"nanoseconds":elapsed,"checksum":checksum,"compilation_ms":compilation})
                    assert len(values)==8, f"Missing rounds: {log}"
                    row[side]=values
                assert len({item["checksum"] for side in ("current",reference) for item in row[side]})==1
                row["ratio"] = statistics.median(item["nanoseconds"] for item in row["current"])/statistics.median(item["nanoseconds"] for item in row[reference])
                results["pairs"].append(row)
                result_path.write_text(json.dumps(results,indent=2)+"\n")
                assert all(item["compilation_ms"]==0 for side in ("current",reference) for item in row[side]), f"Measured JIT activity: {kind}/{count}/{pair}; all rounds retained"
                print(kind,count,pair,round(row["ratio"],4),flush=True)
    assert sha(native)==pins["native"] and sha(DRIVER)==pins["driver"] and sha(FIXTURE)==pins["fixture"]
    assert {name:sha(FROZEN/f"build/classes/java/main/net/minecraft/world/level/chunk/{name}.class") for name in CLASSES}==pins["frozen_classes"]
    assert inventory(ROOT)==pins["current_sources"] and inventory(FROZEN)==pins["frozen_sources"]
    assert {str(path.relative_to(ROOT)):sha(path) for path in
        (ROOT/"build/classes/java/main/net/minecraft/world/level/chunk").rglob("*.class")}==pins["current_classes"]
    assert {str(path.relative_to(OUT)):sha(path) for path in classes.rglob("*.class")}==pins["driver_classes"]
    assert sha(ROOT/"docs/STANDARD-COPILOT-PROMPTS.md")==pins["protected_prompt"]
    if reference=="predecessor":
        assert sha(OUT/"predecessor-native/mattmc_rust-linux-x64.so")==pins["predecessor_native"]
        assert {str(path.relative_to(OUT)):sha(path) for path in (OUT/"predecessor-classes").rglob("*.class")}==pins["predecessor_classes"]
    result_path.write_text(json.dumps(results,indent=2)+"\n")
    print("PASS: 82 actual Frozen caller cases; receipts in",OUT)

if __name__ == "__main__":
    main()
