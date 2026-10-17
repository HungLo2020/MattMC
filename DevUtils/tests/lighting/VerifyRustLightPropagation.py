#!/usr/bin/env python3
"""Verify Rust light propagation and sky-source seeding against Java's and benchmark them."""
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
# Last commit before Rust light propagation; production edits are audited against it.
REFERENCE = '54611cfc25dbdf60ae4b11dc17557d2bec77469d'
CASES = ['terrain', 'edits', 'nether']
BENCHMARK = 'net.minecraft.world.level.lighting.LightPropagationVerification'
PARITY = ['net.minecraft.world.level.lighting.NativeLightPropagationTest']
# The original Java propagation and seeding (-Dmattmc.lighting.javaPropagation), against Rust.
BASELINE, CANDIDATE = 'java', 'native'
# Every production Java edit, as exact (original, replacement) pairs applied in
# order to the reference file; anything else is an unaudited change.
PRODUCTION_REWRITES = {
    'src/main/java/net/minecraft/world/level/lighting/LightEngine.java': [
        ('\t\tthis.blockNodesToCheck.trim(512);\n\t\tint i = 0;\n\t\ti += this.propagateDecreases();\n\t\ti += this.propagateIncreases();\n',
         '\t\tthis.blockNodesToCheck.trim(512);\n\t\tint i = this.nativePropagation == null ? -1 : this.nativePropagation.run(this.storage, this.decreaseQueue, this.increaseQueue);\n\t\tif (i < 0) {\n\t\t\ti = 0;\n\t\t\ti += this.propagateDecreases();\n\t\t\ti += this.propagateIncreases();\n\t\t}\n'),
        ('\tprivate final LightChunk[] lastChunk = new LightChunk[2];\n',
         '\tprivate final LightChunk[] lastChunk = new LightChunk[2];\n\t@Nullable\n\tprivate final NativeLightPropagation nativePropagation = NativeLightPropagation.create(this);\n'),
    ],
    'src/main/java/net/minecraft/world/level/lighting/SkyLightEngine.java': [
        ('\t\t\tDataLayer dataLayer = this.storage.getDataLayerToWrite(o);\n\t\t\tif (dataLayer != null) {\n',
         '\t\t\tDataLayer dataLayer = this.storage.getDataLayerToWrite(o);\n\t\t\tif (seed != null && dataLayer != null && dataLayer.getClass() == DataLayer.class) {\n\t\t\t\tif (!seed.section(this, dataLayer, SectionPos.sectionToBlockCoord(n), k, m)) {\n\t\t\t\t\tbreak;\n\t\t\t\t}\n\t\t\t} else if (dataLayer != null) {\n'),
        ('\t\tint m = SectionPos.sectionToBlockCoord(chunkPos.z);\n',
         '\t\tint m = SectionPos.sectionToBlockCoord(chunkPos.z);\n\t\tNativeLightPropagation.SkySeed seed = NativeLightPropagation.skySeed(\n\t\t\tchunkSkyLightSources, chunkSkyLightSources2, chunkSkyLightSources3, chunkSkyLightSources4, chunkSkyLightSources5\n\t\t);\n'),
    ],
    'src/main/java/net/minecraft/world/level/chunk/DataLayer.java': [
        ('\t\t\tthrow (IllegalArgumentException)Util.pauseInIde(new IllegalArgumentException("DataLayer should be 2048 bytes not: " + bs.length));\n\t\t}\n',
         '\t\t\tthrow (IllegalArgumentException)Util.pauseInIde(new IllegalArgumentException("DataLayer should be 2048 bytes not: " + bs.length));\n\t\t}\n\t}\n\n\t// Raw view for the Rust light bridge; never allocates.\n\t@Nullable\n\tpublic byte[] dataForNativeLight() {\n\t\treturn this.data;\n\t}\n\n\tpublic int defaultValueForNativeLight() {\n\t\treturn this.defaultValue;\n'),
    ],
}

RUST = ['src/main/rust/world/level/lighting/propagation/mod.rs', 'src/main/rust/world/level/lighting/propagation/ffi.rs',
        'src/main/rust/world/level/lighting/propagation/seed.rs']


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
    parser.add_argument('--output', default='build/light-propagation-migration/acceptance')
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
                    "gradle.rootProject.tasks.register('writeLightPropagationClasspath') { dependsOn 'testClasses'; doLast { new File("
                    + json.dumps(str(classpath)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    run(['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writeLightPropagationClasspath', '-x', 'testRustNative', '--console=plain'], out / 'build.log')
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
    rust = run(['cargo', 'test', '--release', 'world::level::lighting'], out / 'rust-tests.log', ROOT / 'src/main/rust')
    library = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    flags = ['-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '--enable-native-access=ALL-UNNAMED',
             '-XX:ActiveProcessorCount=' + str(1 + len(background))]
    base = ['taskset', '-c', ','.join(str(cpu) for cpu in [args.cpu, *background]), 'java', *flags,
            '-Dmattmc.rust.natives.dir=' + str(library.parent), '-cp', classpath.read_text().strip()]
    files = [ROOT / path for path in PRODUCTION_REWRITES] + [ROOT / path for path in RUST]
    files += [ROOT / 'src/main/java/net/minecraft/world/level/lighting/NativeLightPropagation.java',
              ROOT / 'src/main/java/net/minecraft/world/level/chunk/NativeLightBlocks.java', Path(__file__).resolve(),
              ROOT / 'src/test/java/net/minecraft/world/level/lighting/NativeLightPropagationTest.java',
              ROOT / 'src/test/java/net/minecraft/world/level/lighting/LightPropagationFixtures.java',
              ROOT / 'src/test/java/net/minecraft/world/level/lighting/LightPropagationVerification.java',
              ROOT / 'src/test/java/net/minecraft/world/level/levelgen/LightTerrainFixtures.java',
              ROOT / 'src/test/resources/lighting/terrain.json.gz']
    report = {'reference': REFERENCE, 'parity': parity, 'java_tests': java_tests, 'baseline': BASELINE, 'candidate': CANDIDATE,
              'rust_tests': [line for line in rust.splitlines() if 'test result' in line],
              'scope': ('Production light-engine calls (section statuses, light sources including sky seeding, checkBlock and every '
                        'runLightUpdates with its Rust boundary, section callbacks and installs) for initial lighting of 36 saved FULL '
                        'overworld chunks, 96 self-reverting edits on that lit terrain, and initial block lighting of 36 NOISE-filled '
                        'nether chunks. Terrain loading and engine construction excluded. No whole-game timing.'),
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
