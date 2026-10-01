#!/usr/bin/env python3
"""Focused beardifier parity and production-caller performance acceptance.

Uses the original Java evaluator (class-name change only) and recorded structure
geometry. Does not launch a renderer or run unrelated tests.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import statistics
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
REFERENCE = '594da3f15e9cab3284567af65330aad376e7a871'

def run(command, log):
    with log.open('w') as f:
        subprocess.run(command, cwd=ROOT, stdout=f, stderr=subprocess.STDOUT, check=True)
    return log.read_text()

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', default='build/beardifier-migration/acceptance')
    p.add_argument('--forks', type=int, default=3)
    p.add_argument('--cpu', type=int, default=2)
    p.add_argument('--skip-build', action='store_true')
    p.add_argument('--parity-only', action='store_true')
    args = p.parse_args()
    if not args.parity_only and args.forks < 3: p.error('At least three process pairs are required')
    if args.cpu not in os.sched_getaffinity(0): p.error('CPU is unavailable')
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / 'build'): p.error('Output must be under build/')
    out.mkdir(parents=True, exist_ok=True)
    cpfile = out / 'classpath.txt'
    init = out / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.register('writeBeardifierClasspath') { dependsOn 'testClasses'; doLast { new File(" + json.dumps(str(cpfile)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command = ['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writeBeardifierClasspath', '-x', 'testRustNative', '--console=plain']
    if args.skip_build: command += ['-x', 'buildRustNative']
    run(command, out / 'build.log')
    source = 'src/main/java/net/minecraft/world/level/levelgen/Beardifier.java'
    original = subprocess.check_output(['git', 'show', REFERENCE + ':' + source], cwd=ROOT)
    expected = re.sub(rb'\bBeardifier\b', b'JavaBeardifier', original)
    oracle = ROOT / 'src/test/java/net/minecraft/world/level/levelgen/JavaBeardifier.java'
    if oracle.read_bytes() != expected: raise RuntimeError('Pinned Java oracle changed beyond class renaming')
    reference_sources = out / 'reference-sources'
    reference_sources.mkdir(exist_ok=True)
    (reference_sources / 'Beardifier.java').write_bytes(original)
    native = ROOT / 'build/rust/native'
    jvm = ['-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '--enable-native-access=ALL-UNNAMED']
    base = ['taskset', '-c', str(args.cpu), 'java', *jvm, '-Dmattmc.rust.natives.dir=' + str(native), '-cp', cpfile.read_text().strip(), 'net.minecraft.world.level.levelgen.NativeBeardifierVerification']
    report = {'reference': REFERENCE, 'scope': 'Production Beardifier cell fill over saved structure geometry: plan construction, current geometry packing, native calls, result copy, provider state and output consumption; active cells and full chunk height', 'jvm': jvm, 'cpu': args.cpu,
              'java_version': subprocess.check_output(['java', '--version'], text=True),
              'native_sha256': hashlib.sha256((native / 'mattmc_rust-linux-x64.so').read_bytes()).hexdigest(),
              'oracle_sha256': hashlib.sha256(original).hexdigest(), 'pairs': [], 'performance': {}}
    sources = list((ROOT / 'src/main/rust/world/level/levelgen/density/beardifier').glob('*.rs'))
    sources += [ROOT / 'src/main/java/net/minecraft/world/level/levelgen' / n for n in ['Beardifier.java','NativeBeardifier.java','NoiseChunk.java']]
    sources += [ROOT / 'src/test/java/net/minecraft/world/level/levelgen' / n for n in ['JavaBeardifier.java','NativeBeardifierTest.java','NativeBeardifierVerification.java']]
    sources += [ROOT / 'src/test/resources/worldgen/beardifier/structures.json',Path(__file__).resolve()]
    report['sources'] = {str(f.relative_to(ROOT)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sources}
    report['hardware'] = json.loads(subprocess.check_output(['lscpu','-J'],text=True))
    def save(): (out / 'results.json').write_text(json.dumps(report, indent=2))
    run(['./gradlew','test','-x','buildRustNative','-x','testRustNative','--tests','net.minecraft.world.level.levelgen.NativeBeardifierTest','--console=plain'],out / 'kernel-tests.log')
    parity = run(base + ['parity'],out / 'parity.log')
    m = re.search(r'BEARD_PARITY points=(\d+) fixtures=\d+ exact=true', parity)
    if not m: raise RuntimeError('Parity incomplete')
    report['production_parity_queries'] = int(m[1]); save()
    if args.parity_only: print(out / 'results.json'); return
    for fork in range(args.forks):
        pair = {}
        for mode in (['java','native'] if fork % 2 == 0 else ['native','java']):
            print(f'Pair {fork+1}: {mode} all beardifier workloads', flush=True)
            text = run(base + [mode, 'all'], out / f'{fork}-{mode}.log')
            matches = re.findall(r'BEARD_BENCH group=(\S+) full=(true|false).*?ns=(\[.*?\]) jit=(\[.*?\])', text)
            if len(matches) != 6: raise RuntimeError('Incomplete measurements')
            for settings, cycle, samples, jit in matches:
                if any(json.loads(jit)): raise RuntimeError('Compilation during measurements')
                name = settings + ('_full_height' if cycle == 'true' else '_active_cells')
                pair.setdefault(name, {})[mode] = json.loads(samples)
        report['pairs'].append(pair); save()
    import random
    rng = random.Random(1937)
    for name in report['pairs'][0]:
        pairs = [(r[name]['java'],r[name]['native']) for r in report['pairs']]
        ratios = [statistics.median(n)/statistics.median(j) for j,n in pairs]
        boots = sorted(statistics.median(statistics.median(rng.choices(n,k=len(n)))/statistics.median(rng.choices(j,k=len(j))) for j,n in rng.choices(pairs,k=len(pairs))) for _ in range(10000))
        report['performance'][name] = {'java_ns':statistics.median(statistics.median(j) for j,n in pairs), 'native_ns':statistics.median(statistics.median(n) for j,n in pairs),
                                      'ratios':ratios,'ratio_ci95':[boots[250],boots[9749]],'passes':max(ratios)<=.95 and boots[9749]<=.95}
    save();print(json.dumps(report['performance'],indent=2))
    if not all(v['passes'] for v in report['performance'].values()): raise SystemExit('5% gate FAILED')
    print(out / 'results.json')

if __name__ == '__main__':
    main()
