#!/usr/bin/env python3
"""Verify the Rust noise router's interpolation slices against Java's slice fill and benchmark it."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import re
import statistics
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
# Last commit before the native noise router; production edits are audited against it.
REFERENCE = '858476969d6500b2a9e26d10c5d8c5967222ccfe'
CASES = ['overworld', 'amplified', 'nether']
BENCHMARK = 'net.minecraft.world.level.levelgen.NoiseFillVerification'
PARITY = ['net.minecraft.world.level.levelgen.NativeNoiseRouterTest', 'net.minecraft.world.level.levelgen.NativeNoiseFillTest']
# The previous production route (native fill, Java slices) against the router.
BASELINE, CANDIDATE = 'javaslices', 'native'
# Every production Java edit, as exact (original, replacement) pairs applied in
# order to the reference file; anything else is an unaudited change.
PRODUCTION_REWRITES = {
    'src/main/java/net/minecraft/world/level/levelgen/NoiseChunk.java': [
        ('\t\tthis.cellStartBlockX = (this.firstCellX + i) * this.cellWidth;\n\t}\n',
         '\t\tthis.cellStartBlockX = (this.firstCellX + i) * this.cellWidth;\n\t}\n\n    int firstCellZ() {\n        return this.firstCellZ;\n    }\n'),
        ('\tpublic void advanceCellX(int i) {\n\t\tthis.fillSlice(false, this.firstCellX + i + 1);\n',
         '\tpublic void advanceCellX(int i) {\n        this.advanceCellX(i, null);\n\t}\n\n\tvoid advanceCellX(int i, @Nullable NativeNoiseRouter router) {\n\t\tthis.fillSlice(false, this.firstCellX + i + 1, router);\n'),
        ('\t\t\tthis.interpolationCounter = 0L;\n\t\t\tthis.fillSlice(true, this.firstCellX);\n',
         '\t\t\tthis.interpolationCounter = 0L;\n\t\t\tthis.fillSlice(true, this.firstCellX, router);\n'),
        ('\tpublic void initializeForFirstCellX() {\n',
         '\tpublic void initializeForFirstCellX() {\n        this.initializeForFirstCellX(null);\n\t}\n\n    /** With a router, slices come from Rust; Java fills any slice it declines. */\n\tvoid initializeForFirstCellX(@Nullable NativeNoiseRouter router) {\n'),
        ('\t\tthis.inCellX = 0;\n',
         "\t\tthis.inCellX = 0;\n        if (router != null && router.fill(bl, this.cellStartBlockX)) {\n            // The column loop's end state. Every field is reassigned before its\n            // next read; the counters only need to pass every recorded value.\n            this.cellStartBlockZ = (this.firstCellZ + this.cellCountXZ) * this.cellWidth;\n            this.inCellZ = 0;\n            this.cellStartBlockY = (this.cellCountY + this.cellNoiseMinY) * this.cellHeight;\n            this.inCellY = 0;\n            this.arrayIndex = this.cellCountY;\n            this.interpolationCounter += (long)(this.cellCountXZ + 1) * (this.cellCountY + 1) * this.interpolators.size();\n            this.arrayInterpolationCounter += this.cellCountXZ + 2;\n            return;\n        }\n"),
        ('\n\tprivate void fillSlice(boolean bl, int i) {\n',
         '\n\tprivate void fillSlice(boolean bl, int i, @Nullable NativeNoiseRouter router) {\n'),
    ],
    'src/main/java/net/minecraft/world/level/levelgen/NativeNoiseFill.java': [
        ('            Reference.reachabilityFence(this.gap);\n            try { RELEASE.invokeExact(handle); }\n            catch (Throwable error) { throw new IllegalStateException("Cannot release native noise fill", error); }\n',
         '            Reference.reachabilityFence(this.gap);\n            try {\n                if (router != null) router.close();\n            } finally {\n                try { RELEASE.invokeExact(handle); }\n                catch (Throwable error) { throw new IllegalStateException("Cannot release native noise fill", error); }\n            }\n'),
        ('            for (int cellX = 0; cellX < cells; cellX++) {\n                this.chunk.advanceCellX(cellX);\n',
         '            for (int cellX = 0; cellX < cells; cellX++) {\n                this.chunk.advanceCellX(cellX, router);\n'),
        ('            boolean ore = this.toggle != null;\n            this.chunk.initializeForFirstCellX();\n',
         '            boolean ore = this.toggle != null;\n            this.chunk.initializeForFirstCellX(router);\n'),
        ('        if (handle == 0) throw new IllegalStateException("Native noise fill rejected its configuration");\n        try {\n',
         '        if (handle == 0) throw new IllegalStateException("Native noise fill rejected its configuration");\n        try {\n            router = NativeNoiseRouter.create(this.chunk);\n'),
        ('        if (handle == 0) throw new IllegalStateException("Native noise fill rejected its configuration");\n',
         '        if (handle == 0) throw new IllegalStateException("Native noise fill rejected its configuration");\n        NativeNoiseRouter router = null;\n'),
        ('/** Native-owned NOISE-stage block fill for {@code NoiseBasedChunkGenerator.doFill}.\n * Java still fills interpolation slices, cell density caches and aquifer cell\n * materials; Rust runs every block in the original order (interpolation,\n',
         '/** Native-owned NOISE-stage block fill for {@code NoiseBasedChunkGenerator.doFill}.\n * {@link NativeNoiseRouter} fills the interpolation slices; Java still fills\n * cell density caches and aquifer cell materials; Rust runs every block in the original order (interpolation,\n'),
    ],
    'src/main/java/net/minecraft/world/level/levelgen/DensityFunctions.java': [
        ('\t\t\tthis.islandNoise = new SimplexNoise(randomSource);\n\t\t}\n',
         '\t\t\tthis.islandNoise = new SimplexNoise(randomSource);\n\t\t}\n\n        SimplexNoise islandNoise() {\n            return this.islandNoise;\n        }\n'),
    ],
    'src/main/java/net/minecraft/world/level/levelgen/synth/BlendedNoise.java': [
        ('\tprivate volatile NativeNoise nativeNoise;\n',
         '\tprivate volatile NativeNoise nativeNoise;\n    /** The state compute samples, for native density routers. */\n    public NativeNoiseState nativeState() { return nativeNoise(); }\n'),
    ],
    'src/main/java/net/minecraft/world/level/levelgen/synth/SimplexNoise.java': [
        ('\tprivate volatile NativeNoise nativeNoise;\n',
         '\tprivate volatile NativeNoise nativeNoise;\n    /** The state endIslandHeight samples, for native density routers. */\n    public NativeNoiseState nativeState() { return nativeNoise(); }\n'),
    ],
    'src/main/rust/world/level/levelgen/mod.rs': [
        ('pub(crate) mod random;\n',
         'pub(crate) mod random;\npub(crate) mod router;\n'),
    ],
    'src/main/rust/world/level/levelgen/density/mod.rs': [
        ('mod program;\nmod spline;\n',
         'mod program;\npub(crate) mod spline;\n'),
        ('mod cell;\nmod end_islands;\n',
         'mod cell;\npub(crate) mod end_islands;\n'),
    ],
    'src/main/rust/world/level/levelgen/density/spline/mod.rs': [
        ('mod ffi;\nmod program;\n',
         'mod ffi;\npub(crate) mod program;\n'),
        ('mod coordinates;\nmod evaluate;\n',
         'mod coordinates;\npub(crate) mod evaluate;\n'),
    ],
    'src/main/rust/world/level/levelgen/density/spline/program.rs': [
        ('}\npub(super) const MAX_NODES: usize = 4096;\npub(super) const MAX_KNOTS: usize = 16384;\npub(super) fn valid(nodes: &[Node], knots: &[Knot]) -> bool {\n',
         '}\npub(crate) const MAX_NODES: usize = 4096;\npub(crate) const MAX_KNOTS: usize = 16384;\npub(crate) fn valid(nodes: &[Node], knots: &[Knot]) -> bool {\n'),
        ('#[derive(Clone, Copy, Debug)]\npub(super) struct Knot {\n',
         '#[derive(Clone, Copy, Debug)]\npub(crate) struct Knot {\n'),
        ('#[derive(Clone, Copy, Debug)]\npub(super) struct Node {\n',
         '#[derive(Clone, Copy, Debug)]\npub(crate) struct Node {\n'),
    ],
    'src/main/rust/world/level/levelgen/density/spline/evaluate.rs': [
        ('// Do not reassociate, use FMA, or widen to double: Java rounds every operation.\npub(super) fn evaluate(nodes: &[Node], knots: &[Knot], id: usize, axes: &[f32; 4]) -> f32 {\n',
         '// Do not reassociate, use FMA, or widen to double: Java rounds every operation.\npub(crate) fn evaluate(nodes: &[Node], knots: &[Knot], id: usize, axes: &[f32; 4]) -> f32 {\n'),
    ],
}

RUST = ['src/main/rust/world/level/levelgen/router/mod.rs', 'src/main/rust/world/level/levelgen/router/ffi.rs',
        'src/main/rust/world/level/levelgen/router/tests.rs']


def run(command, path, cwd=ROOT):
    result = subprocess.run(command, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    path.write_text(result.stdout)
    if result.returncode:
        raise RuntimeError('Command failed; see ' + str(path))
    return result.stdout


def audit():
    for path, rewrites in PRODUCTION_REWRITES.items():
        expected = subprocess.check_output(['git', 'show', REFERENCE + ':' + path], cwd=ROOT, text=True)
        for old, new in rewrites:
            if expected.count(old) != 1:
                raise RuntimeError('Production rewrite does not apply exactly once: ' + path)
            expected = expected.replace(old, new)
        if (ROOT / path).read_text() != expected:
            raise RuntimeError('Production edit differs from its audited rewrites: ' + path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='build/noise-router-migration/acceptance')
    parser.add_argument('--forks', type=int, default=3)
    parser.add_argument('--cpu', type=int, default=5)
    parser.add_argument('--background-cpus', default='0,1')
    parser.add_argument('--case', default='all')
    parser.add_argument('--parity-only', action='store_true')
    parser.add_argument('--pilot', action='store_true')
    args = parser.parse_args()
    cases = CASES if args.case == 'all' else args.case.split(',')
    if any(case not in CASES for case in cases):
        parser.error('Unknown case')
    if not args.pilot and not args.parity_only and args.forks < 3:
        parser.error('At least three independent comparisons required')
    background = [int(cpu) for cpu in args.background_cpus.split(',')]
    if len(background) < 2 or args.cpu in background or not {args.cpu, *background}.issubset(os.sched_getaffinity(0)):
        parser.error('Separate available worker CPUs required')
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / 'build'):
        parser.error('Output must be under build/')
    out.mkdir(parents=True, exist_ok=True)
    audit()
    classpath, init = out / 'classpath.txt', out / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.named('test') { outputs.upToDateWhen { false } }; "
                    "gradle.rootProject.tasks.register('writeNoiseRouterClasspath') { dependsOn 'testClasses'; doLast { new File("
                    + json.dumps(str(classpath)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    run(['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writeNoiseRouterClasspath', '-x', 'testRustNative', '--console=plain'], out / 'build.log')
    tests = [argument for name in PARITY for argument in ('--tests', name)]
    run(['./gradlew', '-I', str(init), 'test', '-x', 'buildRustNative', '-x', 'testRustNative', *tests, '--console=plain'], out / 'parity.log')
    parity, java_tests = [], 0
    for name in PARITY:
        xml = (ROOT / ('build/test-results/test/TEST-' + name + '.xml')).read_text()
        (out / (name.rsplit('.', 1)[1] + '.xml')).write_text(xml)
        result = ET.fromstring(xml)
        if int(result.attrib['failures']) or int(result.attrib['errors']):
            raise RuntimeError('Parity failed: ' + name)
        java_tests += int(result.attrib['tests'])
        parity += re.findall(r'[A-Z_]+_(?:PARITY|SYNTHETIC)[^\n<]*', xml)
    rust = run(['cargo', 'test', '--release', 'world::level::levelgen'], out / 'rust-tests.log', ROOT / 'src/main/rust')
    library = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    flags = ['-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '--enable-native-access=ALL-UNNAMED',
             '-XX:ActiveProcessorCount=' + str(1 + len(background))]
    base = ['taskset', '-c', ','.join(str(cpu) for cpu in [args.cpu, *background]), 'java', *flags,
            '-Dmattmc.rust.natives.dir=' + str(library.parent), '-cp', classpath.read_text().strip()]
    files = [ROOT / path for path in PRODUCTION_REWRITES] + [ROOT / path for path in RUST]
    files += [ROOT / 'src/main/java/net/minecraft/world/level/levelgen/NativeNoiseRouter.java', Path(__file__).resolve(),
              ROOT / 'src/test/java/net/minecraft/world/level/levelgen/NativeNoiseRouterTest.java',
              ROOT / 'src/test/java/net/minecraft/world/level/levelgen/NativeNoiseFillTest.java',
              ROOT / 'src/test/java/net/minecraft/world/level/levelgen/NoiseFillVerification.java']
    report = {'reference': REFERENCE, 'parity': parity, 'java_tests': java_tests, 'baseline': BASELINE, 'candidate': CANDIDATE,
              'rust_tests': [line for line in rust.splitlines() if 'test result' in line],
              'scope': 'NoiseBasedChunkGenerator.fillFromNoise per fresh ProtoChunk through the native fill: router compile and '
                       'release, interpolation slices, cell caches, aquifer cell materials, the block loop, section writes, heightmaps '
                       'and post-processing; Java slices versus native slices. Chunk and NoiseChunk construction excluded equally. '
                       'No surface, carving, features or whole-game timing.',
              'cpu': args.cpu, 'background_cpus': background, 'jvm': flags, 'pilot': args.pilot, 'cases': cases,
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
    for fork in range(args.forks):
        pair = {}
        report['pairs'].append(pair)
        for name in cases:
            row = pair.setdefault(name, {})
            for mode in ([BASELINE, CANDIDATE] if fork % 2 == 0 else [CANDIDATE, BASELINE]):
                print(f'Comparison {fork + 1}: {name} {mode}', flush=True)
                text = run(base + [BENCHMARK, mode, name] + (['quick'] if args.pilot else []), out / f'{fork}-{name}-{mode}.log')
                bench = re.findall(r'PLAYER_DISTANCE_BENCH case=(\w+) mode=(\w+) warmup_ns=(\d+) repeats=\d+ ns=(\[.*?\]) '
                                   r'cpu_ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)', text)
                if len(bench) != 1 or bench[0][0] != name:
                    raise RuntimeError('Incomplete measurement')
                _, _, warmup, samples, cpu, jit, checksum = bench[0]
                if not args.pilot and int(warmup) < 15_000_000_000:
                    raise RuntimeError('Insufficient warmup')
                if 'checksum' in row and row['checksum'] != int(checksum):
                    raise RuntimeError('Route outputs differ for ' + name)
                row['checksum'] = int(checksum)
                row[mode] = json.loads(samples)
                row[mode + '_jit'] = json.loads(jit)
                save()
    rng = random.Random(1977)

    def summarize(pairs):
        ratios = [statistics.median(n) / statistics.median(j) for j, n in pairs]
        bootstrap = sorted(statistics.median(statistics.median(rng.choices(n, k=len(n))) / statistics.median(rng.choices(j, k=len(j)))
                                             for j, n in rng.choices(pairs, k=len(pairs))) for _ in range(10000))
        return {'baseline_ns': statistics.median(statistics.median(j) for j, n in pairs),
                'candidate_ns': statistics.median(statistics.median(n) for j, n in pairs),
                'ratios': ratios, 'ratio_ci95': [bootstrap[250], bootstrap[9749]],
                'passes': max(ratios) <= .95 and bootstrap[9749] <= .95}

    for name in cases:
        report['performance'][name] = summarize([(p[name][BASELINE], p[name][CANDIDATE]) for p in report['pairs']])
        report['performance'][name]['jit_active_samples'] = sum(
            1 for p in report['pairs'] for mode in (BASELINE, CANDIDATE) for value in p[name][mode + '_jit'] if value)
        if len({p[name]['checksum'] for p in report['pairs']}) != 1:
            raise RuntimeError('Cross-JVM output mismatch')
    for name, digest in report['sources'].items():
        if hashlib.sha256((ROOT / name).read_bytes()).hexdigest() != digest:
            raise RuntimeError('Measured source changed: ' + name)
    if hashlib.sha256(library.read_bytes()).hexdigest() != report['native_sha256']:
        raise RuntimeError('Measured library changed')
    report['integrity'] = {'source_hashes_match': True, 'native_hash_matches': True, 'route_checksums_match': True}
    save()
    print(json.dumps(report['performance'], indent=2))
    print(out / 'results.json')


if __name__ == '__main__':
    main()
