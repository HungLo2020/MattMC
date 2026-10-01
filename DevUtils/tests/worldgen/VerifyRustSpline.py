#!/usr/bin/env python3
"""Focused spline parity and production-caller performance acceptance.

Does not launch a game, renderer, or unrelated test suite. Java's reference spline
arithmetic remains unchanged; its source is checked against the pinned revision.
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
REFERENCE = 'ffb5fd3949bd346663b7378f6a57528ac862538c'

def run(command, log):
    with log.open('w') as f:
        subprocess.run(command, cwd=ROOT, stdout=f, stderr=subprocess.STDOUT, check=True)
    return log.read_text()

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', default='build/spline-migration/acceptance')
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
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.register('writeSplineClasspath') { dependsOn 'testClasses'; doLast { new File(" + json.dumps(str(cpfile)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command = ['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writeSplineClasspath', '-x', 'testRustNative', '--console=plain']
    if args.skip_build: command += ['-x', 'buildRustNative']
    run(command, out / 'build.log')
    source = 'src/main/java/net/minecraft/util/CubicSpline.java'
    original = subprocess.check_output(['git', 'show', REFERENCE + ':' + source], cwd=ROOT)
    if original != (ROOT / source).read_bytes(): raise RuntimeError('Original Java spline evaluator changed')
    reference_sources = out / 'reference-sources'
    reference_sources.mkdir(exist_ok=True)
    (reference_sources / 'CubicSpline.java').write_bytes(original)
    native = ROOT / 'build/rust/native'
    jvm = ['-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '--enable-native-access=ALL-UNNAMED']
    base = ['taskset', '-c', str(args.cpu), 'java', *jvm, '-Dmattmc.rust.natives.dir=' + str(native), '-cp', cpfile.read_text().strip(), 'net.minecraft.world.level.levelgen.NativeSplineVerification']
    report = {'reference': REFERENCE, 'scope': 'Production chunk-bound spline compute, cached coordinate transforms and FFM; steady and mapping/binding plus 25 evaluations per curve', 'jvm': jvm, 'cpu': args.cpu,
              'java_version': subprocess.check_output(['java', '--version'], text=True),
              'native_sha256': hashlib.sha256((native / 'mattmc_rust-linux-x64.so').read_bytes()).hexdigest(),
              'oracle_sha256': hashlib.sha256(original).hexdigest(), 'pairs': [], 'performance': {}}
    sources = list((ROOT / 'src/main/rust/world/level/levelgen/density/spline').glob('*.rs')) + [ROOT / 'src/main/java/net/minecraft/world/level/levelgen' / n for n in ['NativeSpline.java','NativeSplineProgram.java','NativeUnary.java','NoiseChunk.java','DensityFunctions.java']]
    sources += [ROOT / 'src/main/java/net/minecraft/world/level/levelgen/NativeDensity.java', ROOT / 'src/test/java/net/minecraft/world/level/levelgen/NativeSplineVerification.java', ROOT / 'src/test/java/net/minecraft/world/level/levelgen/NativeSplineKernelTest.java', Path(__file__).resolve()]
    report['sources'] = {str(f.relative_to(ROOT)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sources}
    mapping = ROOT / 'src/main/java/net/minecraft/util/CubicSplineMapping.java'
    report['sources'][str(mapping.relative_to(ROOT))] = hashlib.sha256(mapping.read_bytes()).hexdigest()
    def save(): (out / 'results.json').write_text(json.dumps(report, indent=2))
    run(['./gradlew','test','-x','buildRustNative','-x','testRustNative','--tests','net.minecraft.world.level.levelgen.NativeSplineKernelTest','--console=plain'],out / 'kernel-tests.log')
    parity = run(base + ['parity'],out / 'parity.log')
    m = re.search(r'SPLINE_PARITY decisions=(\d+) exact=true', parity)
    if not m: raise RuntimeError('Parity incomplete')
    report['production_parity_queries'] = int(m[1]); save()
    if args.parity_only: print(out / 'results.json'); return
    for fork in range(args.forks):
        pair = {}
        for mode in (['java','native'] if fork % 2 == 0 else ['native','java']):
            print(f'Pair {fork+1}: {mode} all spline workloads', flush=True)
            text = run(base + [mode, 'all'], out / f'{fork}-{mode}.log')
            matches = re.findall(r'SPLINE_BENCH name=(\S+) lifecycle=(true|false).*?ns=(\[.*?\]) jit=(\[.*?\])', text)
            if len(matches) != 6: raise RuntimeError('Incomplete measurements')
            for settings, cycle, samples, jit in matches:
                if any(json.loads(jit)): raise RuntimeError('Compilation during measurements')
                name = settings + ('_lifecycle' if cycle == 'true' else '_steady')
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
