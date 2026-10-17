#!/usr/bin/env python3
"""Verify Rust multi-noise biome searches against Java's per-sample searches and benchmark them."""
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
# Last commit before Rust biome searches; production edits are audited against it.
REFERENCE = '121ad13c84e45555c34814d54a8199194b37f39c'
CASES = ['rings', 'locate']
BENCHMARK = 'net.minecraft.world.level.levelgen.NativeBiomeSearchVerification'
PARITY = ['net.minecraft.world.level.levelgen.NativeBiomeSearchTest']
# Java's per-sample searches (-Dmattmc.worldgen.javaBiomeSearch), against Rust searches.
BASELINE, CANDIDATE = 'java', 'native'
# Every production Java edit, as exact (original, replacement) pairs applied in
# order to the reference file; anything else is an unaudited change.
PRODUCTION_REWRITES = {
    'src/main/java/net/minecraft/world/level/biome/BiomeSource.java': [
        ('\n\t\t\treturn null;\n\t\t}\n',
         '\n\t\treturn null;\n'),
        ('\t\t\t\t\t\treturn Pair.of(new BlockPos(m, q, n), holder);\n\t\t\t\t\t}\n\t\t\t\t}\n\t\t\t}\n',
         '\t\t\t\t\t\treturn Pair.of(new BlockPos(m, q, n), holder);\n\t\t\t\t\t}\n\t\t\t\t}\n\t\t\t}\n\t\t}\n'),
        ('\n\t\t\t\tfor (int q : is) {\n\t\t\t\t\tint r = QuartPos.fromBlock(q);\n\t\t\t\t\tHolder<Biome> holder = this.getNoiseBiome(o, r, p, sampler);\n\t\t\t\t\tif (set.contains(holder)) {\n\t\t\t\t\t\treturn Pair.of(new BlockPos(m, q, n), holder);\n\t\t\t\t\t}\n',
         '\n\t\tfor (BlockPos.MutableBlockPos mutableBlockPos : BlockPos.spiralAround(BlockPos.ZERO, l, Direction.EAST, Direction.SOUTH)) {\n\t\t\tint m = blockPos.getX() + mutableBlockPos.getX() * j;\n\t\t\tint n = blockPos.getZ() + mutableBlockPos.getZ() * j;\n\t\t\tint o = QuartPos.fromBlock(m);\n\t\t\tint p = QuartPos.fromBlock(n);\n\n\t\t\tfor (int q : is) {\n\t\t\t\tint r = QuartPos.fromBlock(q);\n\t\t\t\tHolder<Biome> holder = this.getNoiseBiome(o, r, p, sampler);\n\t\t\t\tif (set.contains(holder)) {\n\t\t\t\t\treturn Pair.of(new BlockPos(m, q, n), holder);\n'),
        ('\n\t\t\tfor (BlockPos.MutableBlockPos mutableBlockPos : BlockPos.spiralAround(BlockPos.ZERO, l, Direction.EAST, Direction.SOUTH)) {\n\t\t\t\tint m = blockPos.getX() + mutableBlockPos.getX() * j;\n\t\t\t\tint n = blockPos.getZ() + mutableBlockPos.getZ() * j;\n\t\t\t\tint o = QuartPos.fromBlock(m);\n\t\t\t\tint p = QuartPos.fromBlock(n);\n',
         '\n\t/** The spiral search of {@link #findClosestBiome3d} over the accepted biomes and the Ys to try per column. */\n\t@Nullable\n\tprotected Pair<BlockPos, Holder<Biome>> findClosestBiome3d(BlockPos blockPos, int i, int j, Set<Holder<Biome>> set, int[] is, Climate.Sampler sampler) {\n\t\tint l = Math.floorDiv(i, j);\n'),
        ('\t\t\tint[] is = Mth.outFromOrigin(blockPos.getY(), levelReader.getMinY() + 1, levelReader.getMaxY() + 1, k).toArray();\n',
         '\t\t\tint[] is = Mth.outFromOrigin(blockPos.getY(), levelReader.getMinY() + 1, levelReader.getMaxY() + 1, k).toArray();\n\t\t\treturn this.findClosestBiome3d(blockPos, i, j, set, is, sampler);\n\t\t}\n\t}\n'),
        ('\t\t} else {\n\t\t\tint l = Math.floorDiv(i, j);\n',
         '\t\t} else {\n'),
        ('\t\treturn this.findBiomeHorizontal(i, j, k, l, 1, predicate, randomSource, false, sampler);\n\t}\n\n',
         '\t\treturn this.findBiomeHorizontal(i, j, k, l, 1, predicate, randomSource, false, sampler);\n\t}\n\n\t/** {@link #findBiomeHorizontal(int, int, int, int, Predicate, RandomSource, Climate.Sampler)}\n\t * for membership in a holder set, a pure test sources may evaluate once per biome. */\n\t@Nullable\n\tpublic Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(\n\t\tint i, int j, int k, int l, net.minecraft.core.HolderSet<Biome> biomes, RandomSource randomSource, Climate.Sampler sampler\n\t) {\n\t\treturn this.findBiomeHorizontal(i, j, k, l, biomes::contains, randomSource, sampler);\n\t}\n\n'),
    ],
    'src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSource.java': [
        ('\t\treturn optional.isPresent() && ((Holder)optional.get()).is(resourceKey);\n\t}\n\n\t@Override\n',
         '\t\treturn optional.isPresent() && ((Holder)optional.get()).is(resourceKey);\n\t}\n\n\t@Override\n\tpublic Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(\n\t\tint i, int j, int k, int l, net.minecraft.core.HolderSet<Biome> biomes, net.minecraft.util.RandomSource randomSource, Climate.Sampler sampler\n\t) {\n\t\tvar search = net.minecraft.world.level.levelgen.NativeBiomeSearch.horizontal(this, i, j, k, l, biomes::contains, randomSource, sampler);\n\t\treturn search.handled() ? search.result() : super.findBiomeHorizontal(i, j, k, l, biomes, randomSource, sampler);\n\t}\n\n\t@Override\n\tprotected Pair<BlockPos, Holder<Biome>> findClosestBiome3d(BlockPos blockPos, int i, int j, java.util.Set<Holder<Biome>> set, int[] is,\n\t\tClimate.Sampler sampler) {\n\t\tvar search = net.minecraft.world.level.levelgen.NativeBiomeSearch.closest(this, blockPos, i, j, set, is, sampler);\n\t\treturn search.handled() ? search.result() : super.findClosestBiome3d(blockPos, i, j, set, is, sampler);\n\t}\n\n\t@Override\n'),
    ],
    'src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java': [
        ('\t\t\t\t\t\t\t\t.findBiomeHorizontal(\n\t\t\t\t\t\t\t\t\tSectionPos.sectionToBlockCoord(o, 8), 0, SectionPos.sectionToBlockCoord(p, 8), 112, holderSet::contains, randomSource2, this.randomState.sampler()\n',
         '\t\t\t\t\t\t\t\t.findBiomeHorizontal(\n\t\t\t\t\t\t\t\t\tSectionPos.sectionToBlockCoord(o, 8), 0, SectionPos.sectionToBlockCoord(p, 8), 112, holderSet, randomSource2, this.randomState.sampler()\n'),
    ],
}

RUST = ['src/main/rust/world/level/biome/search/mod.rs', 'src/main/rust/world/level/biome/search/ffi.rs',
        'src/main/rust/world/level/levelgen/router/mod.rs', 'src/main/rust/world/level/biome/climate/search.rs']


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
    parser.add_argument('--output', default='build/biome-search-migration/acceptance')
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
                    "gradle.rootProject.tasks.register('writeBiomeSearchClasspath') { dependsOn 'testClasses'; doLast { new File("
                    + json.dumps(str(classpath)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    run(['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writeBiomeSearchClasspath', '-x', 'testRustNative', '--console=plain'], out / 'build.log')
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
    rust = run(['cargo', 'test', '--release', 'world::level::biome'], out / 'rust-tests.log', ROOT / 'src/main/rust')
    library = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    flags = ['-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '--enable-native-access=ALL-UNNAMED',
             '-XX:ActiveProcessorCount=' + str(1 + len(background))]
    base = ['taskset', '-c', ','.join(str(cpu) for cpu in [args.cpu, *background]), 'java', *flags,
            '-Dmattmc.rust.natives.dir=' + str(library.parent), '-cp', classpath.read_text().strip()]
    files = [ROOT / path for path in PRODUCTION_REWRITES] + [ROOT / path for path in RUST]
    files += [ROOT / 'src/main/java/net/minecraft/world/level/levelgen/NativeBiomeSearch.java', Path(__file__).resolve(),
              ROOT / 'src/test/java/net/minecraft/world/level/levelgen/NativeBiomeSearchTest.java',
              ROOT / 'src/test/java/net/minecraft/world/level/levelgen/NativeBiomeSearchVerification.java',
              ROOT / 'src/test/java/net/minecraft/world/level/biome/BiomeSearchAccess.java']
    report = {'reference': REFERENCE, 'parity': parity, 'java_tests': java_tests, 'baseline': BASELINE, 'candidate': CANDIDATE,
              'rust_tests': [line for line in rust.splitlines() if 'test result' in line],
              'scope': ('rings: an overworld ChunkGeneratorStructureState computing every concentric-ring structure position '
                        '(stronghold findBiomeHorizontal searches on the background executor, wall time). locate: twelve '
                        'findClosestBiome3d spiral searches for rare biomes (radius 6400, step 32, Ys every 64). Registry and '
                        'RandomState setup excluded. No whole-game timing.'),
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
