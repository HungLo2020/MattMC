#!/usr/bin/env python3
"""Verify ore geometry parity and complete placement performance, including FFM.

No server, renderer, whole-game tests, commits or system configuration changes.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import re
import statistics
import subprocess

ROOT = Path(__file__).resolve().parents[3]
REFERENCE = '594da3f15e9cab3284567af65330aad376e7a871'
PACKAGE = 'net.minecraft.world.level.levelgen.feature.'


def run(command, log):
    with log.open('w') as stream:
        subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)
    return log.read_text()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='build/ore-migration/acceptance')
    parser.add_argument('--forks', type=int, default=3)
    parser.add_argument('--cpu', type=int, default=2)
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--parity-only', action='store_true')
    args = parser.parse_args()
    if not args.parity_only and args.forks < 3:
        parser.error('At least three process pairs are required')
    if args.cpu not in os.sched_getaffinity(0):
        parser.error('CPU unavailable')
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / 'build'):
        parser.error('Output must be under build/')
    out.mkdir(parents=True, exist_ok=True)
    reference_path = 'src/main/java/net/minecraft/world/level/levelgen/feature/OreFeature.java'
    original = subprocess.check_output(['git', 'show', REFERENCE + ':' + reference_path], cwd=ROOT)
    oracle = ROOT / 'src/test/java/net/minecraft/world/level/levelgen/feature/JavaOreFeature.java'
    if oracle.read_bytes() != re.sub(rb'\bOreFeature\b', b'JavaOreFeature', original):
        raise RuntimeError('Original Java oracle changed beyond class renaming')
    # Also pin the streaming geometry benchmark to the exact original loops.
    # Only the terminal world operation is replaced by coordinate consumption.
    source = original.decode()
    start = source.index('\t\tint p = oreConfiguration.size;')
    end = source.index('\n\t\ttry (BulkSectionAccess', start)
    formation = source[start:end].replace('int p = oreConfiguration.size;', 'int p = size;')
    start = source.index('\t\t\tfor (int xx =')
    end = source.index('int ak =', start)
    scan = source[start:end].rstrip().replace(' && !worldGenLevel.isOutsideBuildHeight(ag)', '')
    consumption = 'sum = sum * 31 + ae; sum = sum * 31 + ag; sum = sum * 31 + ai;'
    expected_body = 'long sum=0;' + formation + scan + consumption + '}' * 8 + 'return sum;'
    kernel = (ROOT / 'src/test/java/net/minecraft/world/level/levelgen/feature/JavaOreKernel.java').read_text()
    actual_body = kernel[kernel.index('long sum=0;'):kernel.index('return sum;') + len('return sum;')]
    if re.sub(r'\s+', '', expected_body) != re.sub(r'\s+', '', actual_body):
        raise RuntimeError('Streaming geometry oracle differs from original loops')
    (out / 'OriginalOreFeature.java').write_bytes(original)
    cpfile = out / 'classpath.txt'
    init = out / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.register('writeOreClasspath') { dependsOn 'testClasses'; doLast { new File(" + json.dumps(str(cpfile)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command = ['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writeOreClasspath', '-x', 'testRustNative', '--console=plain']
    if args.skip_build:
        command += ['-x', 'buildRustNative']
    run(command, out / 'build.log')
    run(['./gradlew', 'test', '-x', 'buildRustNative', '-x', 'testRustNative', '--tests', PACKAGE + 'NativeOreGeometryTest', '--console=plain'], out / 'parity.log')
    xml = ROOT / ('build/test-results/test/TEST-' + PACKAGE + 'NativeOreGeometryTest.xml')
    (out / 'parity.xml').write_bytes(xml.read_bytes())
    if 'failures="0"' not in xml.read_text() or 'errors="0"' not in xml.read_text():
        raise RuntimeError('Parity failed')
    run(['rustc', '--edition=2021', '--test', 'src/test/rust/worldgen.rs', '-o', str(out / 'worldgen-tests')], out / 'rust-build.log')
    run([str(out / 'worldgen-tests'), 'feature::ore'], out / 'rust-tests.log')
    native = ROOT / 'build/rust/native'
    jvm = ['-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '--enable-native-access=ALL-UNNAMED']
    base = ['taskset', '-c', str(args.cpu), 'java', *jvm, '-Dmattmc.rust.natives.dir=' + str(native), '-cp', cpfile.read_text().strip(), PACKAGE + 'NativeOreGeometryVerification']
    files = list((ROOT / 'src/main/rust/world/level/levelgen/feature').rglob('*.rs'))
    files += list((ROOT / 'src/test/java/net/minecraft/world/level/levelgen/feature').glob('*Ore*.java'))
    files += [ROOT / reference_path, ROOT / 'src/main/java/net/minecraft/world/level/levelgen/feature/NativeOreGeometry.java', Path(__file__).resolve()]
    report = {
        'reference': REFERENCE,
        'scope': 'Geometry acceptance: original streaming sphere construction/pruning/scanning versus fused Rust including RNG, inputs, native transitions, result copying and ordered output consumption. Complete place() timings are also reported separately.',
        'fixture': '128 origins and 32 seeds per origin per size, equal weights each side. Same-block STONE writes allow deterministic repeated placement without excluding writes. Solid terrain (discard=0) and deterministic 1/19 air terrain (discard=0.5).',
        'jvm': jvm, 'cpu': args.cpu,
        'java_version': subprocess.check_output(['java', '--version'], text=True),
        'hardware': json.loads(subprocess.check_output(['lscpu', '-J'], text=True)),
        'native_sha256': hashlib.sha256((native / 'mattmc_rust-linux-x64.so').read_bytes()).hexdigest(),
        'sources': {str(f.relative_to(ROOT)): hashlib.sha256(f.read_bytes()).hexdigest() for f in files},
        'parity': re.findall(r'ORE_\w+_PARITY[^\n<]*', xml.read_text()),
        'pairs': [], 'performance': {},
    }
    def save():
        (out / 'results.json').write_text(json.dumps(report, indent=2))
    save()
    if args.parity_only:
        print(out / 'results.json')
        return
    for fork in range(args.forks):
        pair = {}
        for scope in ['geometry', 'placement']:
            for mode in (['java', 'native'] if fork % 2 == 0 else ['native', 'java']):
                print(f'Pair {fork + 1}: {scope} {mode}', flush=True)
                text = run(base + [mode, 'all', scope], out / f'{fork}-{scope}-{mode}.log')
                matches = re.findall(r'ORE_BENCH scope=(\w+) size=(\d+) mixed=(true|false).*?ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)', text)
                if len(matches) != (12 if scope == 'geometry' else 24):
                    raise RuntimeError('Incomplete measurements')
                for measured_scope, size, mixed, samples, jit, checksum in matches:
                    if any(json.loads(jit)) or measured_scope != scope:
                        raise RuntimeError('Compilation during timing or wrong scope')
                    name = scope + '_' + size + ('_air' if mixed == 'true' else '_solid')
                    row = pair.setdefault(name, {})
                    row[mode] = json.loads(samples)
                    if 'checksum' in row and row['checksum'] != int(checksum):
                        raise RuntimeError('Unequal workloads')
                    row['checksum'] = int(checksum)
        report['pairs'].append(pair)
        save()
    rng = random.Random(1937)
    for name in report['pairs'][0]:
        pairs = [(r[name]['java'], r[name]['native']) for r in report['pairs']]
        ratios = [statistics.median(n) / statistics.median(j) for j, n in pairs]
        boots = sorted(statistics.median(statistics.median(rng.choices(n, k=len(n))) / statistics.median(rng.choices(j, k=len(j))) for j, n in rng.choices(pairs, k=len(pairs))) for _ in range(10000))
        report['performance'][name] = {
            'java_ns': statistics.median(statistics.median(j) for j, n in pairs),
            'native_ns': statistics.median(statistics.median(n) for j, n in pairs),
            'ratios': ratios, 'ratio_ci95': [boots[250], boots[9749]],
            'passes': max(ratios) <= .95 and boots[9749] <= .95,
        }
    save()
    print(json.dumps(report['performance'], indent=2))
    if not all(v['passes'] for k, v in report['performance'].items() if k.startswith('geometry_')):
        raise SystemExit('5% performance gate FAILED')
    print(out / 'results.json')


if __name__ == '__main__':
    main()
