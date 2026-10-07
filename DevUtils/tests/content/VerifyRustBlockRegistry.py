#!/usr/bin/env python3
"""Verify the Rust block registry and the consumers that derive their tables from it, and benchmark them against the reference tree."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import re
import shutil
import statistics
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
# Last commit before the block registry: each bridge built its own state table in Java.
REFERENCE = '5457b41ae29059c1f26b439d0d537476a439b9df'
PARITY = [
    'net.minecraft.world.level.block.NativeBlockRegistryTest',
    'net.minecraft.world.level.lighting.NativeLightPropagationTest',
    'net.minecraft.world.level.lighting.NativeSkyLightSourcesTest',
    'net.minecraft.world.level.levelgen.NativeHeightmapTest',
    'net.minecraft.world.level.levelgen.NativeNoiseFillTest',
    'net.minecraft.world.level.levelgen.NativeSurfaceChunkTest',
    'net.minecraft.world.level.levelgen.NativeCarversTest',
    'net.minecraft.world.level.chunk.storage.NativeChunkSectionsTest',
    'net.minecraft.world.level.chunk.NativePalettePackingTest',
]
RUST_TESTS = ['content::', 'world::level::lighting', 'world::level::levelgen::heightmap', 'world::level::levelgen::noise_fill',
              'world::level::levelgen::proto_chunk', 'world::level::levelgen::carver', 'storage::chunk']
STARTUP = 'DevUtils/tests/content/BlockTableStartup.java'
# Steady-state consumer benchmarks: (key, class, arguments, row pattern). Rows carry their checksum,
# which must match between trees.
HOT = [
    ('heightmap', 'net.minecraft.world.level.levelgen.NativeHeightmapVerification', ['native', 'all'], 'HEIGHTMAP_BENCH'),
    ('skylight', 'net.minecraft.world.level.lighting.NativeSkyLightSourcesVerification', ['native', 'all'], 'SKYLIGHT_BENCH'),
    ('palette', 'net.minecraft.world.level.chunk.NativePalettePackingVerification', ['native', 'all'], 'PALETTE_BENCH'),
    ('light_terrain', 'net.minecraft.world.level.lighting.LightPropagationVerification', ['native', 'terrain'], 'PLAYER_DISTANCE_BENCH'),
    ('light_edits', 'net.minecraft.world.level.lighting.LightPropagationVerification', ['native', 'edits'], 'PLAYER_DISTANCE_BENCH'),
    ('sections_encode', 'net.minecraft.world.level.chunk.storage.NativeChunkSectionsVerification', ['native', 'encode'], 'PLAYER_DISTANCE_BENCH'),
    ('sections_load', 'net.minecraft.world.level.chunk.storage.NativeChunkSectionsVerification', ['native', 'load'], 'PLAYER_DISTANCE_BENCH'),
]
ROW = re.compile(r'(HEIGHTMAP_BENCH|SKYLIGHT_BENCH|PALETTE_BENCH) name=(\w+) repeats=\d+ ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)'
                 r'|PLAYER_DISTANCE_BENCH case=(\w+) mode=\w+ warmup_ns=\d+ repeats=\d+ ns=(\[.*?\]) cpu_ns=\[.*?\] jit=(\[.*?\]) checksum=(-?\d+)')


def run(command, path, cwd=ROOT):
    result = subprocess.run(command, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    path.write_text(result.stdout)
    if result.returncode:
        raise RuntimeError('Command failed; see ' + str(path))
    return result.stdout


def classpath(tree, out, name):
    """Builds `tree` with the release native library and returns its test runtime classpath."""
    target, init = out / (name + '-classpath.txt'), out / (name + '-classpath.gradle')
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.register('writeBlockRegistryClasspath') { dependsOn 'testClasses'; "
                    "doLast { new File(" + json.dumps(str(target)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    run(['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writeBlockRegistryClasspath', '-x', 'testRustNative', '--console=plain'],
        out / (name + '-build.log'), tree)
    return target.read_text().strip()


def reference_tree(out):
    """The reference commit as plain files (no worktree), sharing this tree's Rust build cache."""
    tree = out / 'reference'
    if not (tree / 'build.gradle').exists():
        tree.mkdir(parents=True, exist_ok=True)
        archive = subprocess.run(['git', 'archive', REFERENCE], cwd=ROOT, stdout=subprocess.PIPE, check=True).stdout
        subprocess.run(['tar', '-x', '-C', str(tree)], input=archive, check=True)
        cache = ROOT / 'build/rust/target'
        if cache.is_dir() and not (tree / 'build/rust/target').exists():
            (tree / 'build/rust').mkdir(parents=True, exist_ok=True)
            subprocess.run(['cp', '-al', str(cache), str(tree / 'build/rust/target')], check=True)
    return tree


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='build/block-registry-migration/acceptance')
    parser.add_argument('--forks', type=int, default=3)
    parser.add_argument('--startup-samples', type=int, default=8, help='fresh JVMs per tree per comparison')
    parser.add_argument('--cpu', type=int, default=5)
    parser.add_argument('--case', default='all', help='comma-separated hot-path keys, "none", or "all"')
    parser.add_argument('--parity-only', action='store_true')
    parser.add_argument('--skip-parity', action='store_true')
    parser.add_argument('--pilot', action='store_true')
    args = parser.parse_args()
    keys = [h[0] for h in HOT] if args.case == 'all' else [] if args.case == 'none' else args.case.split(',')
    if any(key not in [h[0] for h in HOT] for key in keys):
        parser.error('Unknown case')
    if not args.pilot and not args.parity_only and args.forks < 3:
        parser.error('At least three independent comparisons required')
    if args.cpu not in os.sched_getaffinity(0):
        parser.error('Worker CPU unavailable')
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / 'build'):
        parser.error('Output must be under build/')
    out.mkdir(parents=True, exist_ok=True)

    candidate_cp = classpath(ROOT, out, 'candidate')
    parity, java_tests = [], 0
    if not args.skip_parity:
        tests = [argument for name in PARITY for argument in ('--tests', name)]
        run(['./gradlew', 'test', '-x', 'buildRustNative', '-x', 'testRustNative', *tests, '--console=plain'], out / 'parity.log')
        for name in PARITY:
            xml = (ROOT / ('build/test-results/test/TEST-' + name + '.xml')).read_text()
            (out / (name.rsplit('.', 1)[1] + '.xml')).write_text(xml)
            result = ET.fromstring(xml)
            if int(result.attrib['failures']) or int(result.attrib['errors']):
                raise RuntimeError('Parity failed: ' + name)
            java_tests += int(result.attrib['tests'])
            parity += re.findall(r'[A-Z_]+_(?:PARITY|ARITHMETIC|FACES)[^\n<]*', xml)
    rust = run(['cargo', 'test', '--release', '--lib', '--', *RUST_TESTS], out / 'rust-tests.log', ROOT / 'src/main/rust')
    library = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    files = [ROOT / line for line in subprocess.check_output(['git', 'diff', '--name-only', REFERENCE, '--', 'src'], cwd=ROOT, text=True).split()]
    files += [ROOT / line for line in subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', 'src', 'DevUtils/tests/content'],
                                                               cwd=ROOT, text=True).split()]
    files = sorted({f for f in files if f.exists()} | {Path(__file__).resolve(), ROOT / STARTUP})
    report = {'reference': REFERENCE, 'parity': parity, 'java_tests': java_tests,
              'rust_tests': [line for line in rust.splitlines() if 'test result' in line],
              'scope': ('startup: one fresh JVM per sample, from frozen registries to every block-state table the Rust consumers use '
                        '(reference: eight Java builders; candidate: one export, Rust install and derived views). Bootstrap, the native '
                        'library load and handle linking excluded. Hot paths: each consumer\'s existing native-mode benchmark in both trees, '
                        'whose per-row checksums must match.'),
              'cpu': args.cpu, 'pilot': args.pilot, 'cases': keys,
              'java_version': subprocess.check_output(['java', '--version'], text=True),
              'rust_version': subprocess.check_output(['rustc', '--version'], text=True),
              'hardware': json.loads(subprocess.check_output(['lscpu', '-J'], text=True)),
              'native_sha256': hashlib.sha256(library.read_bytes()).hexdigest(),
              'sources': {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in files},
              'pairs': [], 'performance': {}}

    def save():
        (out / 'results.json').write_text(json.dumps(report, indent=2) + '\n')

    save()
    if args.parity_only:
        print(out / 'results.json')
        return
    reference = reference_tree(out)
    reference_cp = classpath(reference, out, 'reference')
    trees = {'reference': (reference, reference_cp), 'candidate': (ROOT, candidate_cp)}
    jvm = ['-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '--enable-native-access=ALL-UNNAMED']

    def java(tree, arguments, log):
        root, cp = trees[tree]
        return run(['taskset', '-c', str(args.cpu), 'java', *jvm, '-Dmattmc.rust.natives.dir=' + str(root / 'build/rust/native'),
                    '-cp', cp, *arguments], log, root)

    for fork in range(args.forks):
        pair = {'startup': {}}
        report['pairs'].append(pair)
        order = ['reference', 'candidate'] if fork % 2 == 0 else ['candidate', 'reference']
        for sample in range(args.startup_samples if not args.pilot else 2):
            for tree in order:
                text = java(tree, [str(ROOT / STARTUP)], out / f'{fork}-startup-{sample}-{tree}.log')
                found = re.findall(r'BLOCK_TABLES_STARTUP tree=(\w+) ns=(\d+) check=(-?\d+)', text)
                if len(found) != 1 or found[0][0] != tree:
                    raise RuntimeError('Incomplete startup sample')
                pair['startup'].setdefault(tree, []).append(int(found[0][1]))
                pair['startup'].setdefault(tree + '_check', int(found[0][2]))
        save()
        for key, cls, arguments, _ in HOT:
            if key not in keys:
                continue
            for tree in order:
                print(f'Comparison {fork + 1}: {key} {tree}', flush=True)
                extra = ['quick'] if args.pilot and cls.endswith(('LightPropagationVerification', 'NativeChunkSectionsVerification')) else []
                text = java(tree, [cls, *arguments, *extra], out / f'{fork}-{key}-{tree}.log')
                rows = ROW.findall(text)
                if not rows:
                    raise RuntimeError('No measurements: ' + key)
                for kind, name, samples, jit, checksum, case, case_samples, case_jit, case_checksum in rows:
                    name, samples, jit, checksum = (name, samples, jit, checksum) if kind else (case, case_samples, case_jit, case_checksum)
                    row = pair.setdefault(key + ':' + name, {})
                    if 'checksum' in row and row['checksum'] != int(checksum):
                        raise RuntimeError('Outputs differ between trees: ' + key + ':' + name)
                    row['checksum'] = int(checksum)
                    row[tree] = json.loads(samples)
                    row[tree + '_jit'] = json.loads(jit)
                save()
    rng = random.Random(1977)

    def summarize(pairs):
        ratios = [statistics.median(c) / statistics.median(r) for r, c in pairs]
        bootstrap = sorted(statistics.median(statistics.median(rng.choices(c, k=len(c))) / statistics.median(rng.choices(r, k=len(r)))
                                             for r, c in rng.choices(pairs, k=len(pairs))) for _ in range(10000))
        return {'reference_ns': statistics.median(statistics.median(r) for r, c in pairs),
                'candidate_ns': statistics.median(statistics.median(c) for r, c in pairs),
                'ratios': ratios, 'ratio_ci95': [bootstrap[250], bootstrap[9749]],
                'faster_5pct': max(ratios) <= .95 and bootstrap[9749] <= .95,
                'no_regression': bootstrap[9749] <= 1.02}

    names = sorted({name for p in report['pairs'] for name in p})
    for name in names:
        rows = [p[name] for p in report['pairs'] if name in p]
        report['performance'][name] = summarize([(row['reference'], row['candidate']) for row in rows])
        if name != 'startup' and len({row['checksum'] for row in rows}) != 1:
            raise RuntimeError('Cross-JVM output mismatch: ' + name)
    for name, digest in report['sources'].items():
        if hashlib.sha256((ROOT / name).read_bytes()).hexdigest() != digest:
            raise RuntimeError('Measured source changed: ' + name)
    if hashlib.sha256(library.read_bytes()).hexdigest() != report['native_sha256']:
        raise RuntimeError('Measured library changed')
    report['integrity'] = {'source_hashes_match': True, 'native_hash_matches': True, 'checksums_match': True}
    save()
    print(json.dumps(report['performance'], indent=2))
    print(out / 'results.json')


if __name__ == '__main__':
    main()
