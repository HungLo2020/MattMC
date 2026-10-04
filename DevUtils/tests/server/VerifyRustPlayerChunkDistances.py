#!/usr/bin/env python3
"""Verify the Rust-owned chunk-distance trackers (player fields, simulation) against pinned original Java."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import re
import statistics
import subprocess
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parent))
import player_distance_oracle as oracle  # noqa: E402

ROOT = Path(__file__).resolve().parents[3]
PACKAGE = 'net.minecraft.server.level.'
TEST_BASE = ROOT / 'src/test/java/net/minecraft/server/level'
LIGHTING_TESTS = 'src/test/java/net/minecraft/world/level/lighting/'
# Commit that pinned the original graph, chunk tracker and queue oracles.
GRAPH_ORACLE_COMMIT = 'f5473e41d'
CASES = ['single_walk', 'group_walk', 'teleport_churn', 'player_walk', 'ticket_churn', 'distance_changes']
SIMULATION_CASES = {'player_walk', 'ticket_churn', 'distance_changes'}
PARITY_TESTS = ['NativePlayerChunkDistancesTest', 'NativeSimulationChunkTrackerTest']
TICKET_STORAGE = 'src/main/java/net/minecraft/world/level/TicketStorage.java'
TICKET_STORAGE_REWRITES = [
    ('import it.unimi.dsi.fastutil.longs.LongOpenHashSet;\n',
     'import it.unimi.dsi.fastutil.longs.LongIterator;\nimport it.unimi.dsi.fastutil.longs.LongOpenHashSet;\n'),
    ('\tpublic List<Ticket> getTickets(long l) {',
     '\t/** Chunks holding active tickets, so a listener attached after tickets exist can seed its state. */\n'
     '\tpublic LongIterator activeTicketChunks() {\n\t\treturn this.tickets.keySet().iterator();\n\t}\n\n'
     '\tpublic List<Ticket> getTickets(long l) {'),
]
PRODUCTION = 'src/main/java/net/minecraft/server/level/DistanceManager.java'

# The production DistanceManager may differ from the reference only here.
PRODUCTION_REWRITES = [
    ('\tprivate final DistanceManager.FixedPlayerDistanceChunkTracker naturalSpawnChunkCounter = new DistanceManager.FixedPlayerDistanceChunkTracker(8);\n'
     '\tprivate final DistanceManager.PlayerTicketTracker playerTicketManager = new DistanceManager.PlayerTicketTracker(32);\n',
     '\t// Rust owns both player-distance graphs and their shared player-presence source.\n'
     '\tprivate final PlayerChunkDistances playerDistances = new PlayerChunkDistances(8, 32);\n'
     '\tprivate final DistanceManager.FixedPlayerDistanceChunkTracker naturalSpawnChunkCounter = new DistanceManager.FixedPlayerDistanceChunkTracker(\n'
     '\t\tthis.playerDistances, PlayerChunkDistances.NATURAL_SPAWN\n\t);\n'
     '\tprivate final DistanceManager.PlayerTicketTracker playerTicketManager = new DistanceManager.PlayerTicketTracker(\n'
     '\t\tthis.playerDistances, PlayerChunkDistances.PLAYER_TICKETS\n\t);\n'),
    ('\t\tthis.naturalSpawnChunkCounter.update(l, 0, true);\n\t\tthis.playerTicketManager.update(l, 0, true);\n',
     '\t\tthis.playerDistances.playerEntered(l);\n'),
    ('\t\t\tthis.naturalSpawnChunkCounter.update(l, Integer.MAX_VALUE, false);\n\t\t\tthis.playerTicketManager.update(l, Integer.MAX_VALUE, false);\n',
     '\t\t\tthis.playerDistances.chunkVacated(l);\n'),
    ('\tclass FixedPlayerDistanceChunkTracker extends ChunkTracker {\n'
     '\t\tprotected final Long2ByteMap chunks = new Long2ByteOpenHashMap();\n'
     '\t\tprotected final int maxDistance;\n\n'
     '\t\tprotected FixedPlayerDistanceChunkTracker(final int i) {\n'
     '\t\t\tsuper(i + 2, 16, 256);\n\t\t\tthis.maxDistance = i;\n\t\t\tthis.chunks.defaultReturnValue((byte)(i + 2));\n\t\t}\n',
     '\t/** Published level view of one native distance field. Each run replays the\n'
     '\t * field\'s ordered {@code setLevel} calls into this original map logic. */\n'
     '\tstatic class FixedPlayerDistanceChunkTracker {\n'
     '\t\tprotected final Long2ByteMap chunks = new Long2ByteOpenHashMap();\n'
     '\t\tprotected final int maxDistance;\n'
     '\t\tprivate final PlayerChunkDistances distances;\n'
     '\t\tprivate final int field;\n'
     '\t\tprivate final PlayerChunkDistances.LevelSink levelSink = this::setLevel;\n\n'
     '\t\tprotected FixedPlayerDistanceChunkTracker(PlayerChunkDistances distances, int field) {\n'
     '\t\t\tthis.distances = distances;\n\t\t\tthis.field = field;\n'
     '\t\t\tthis.maxDistance = distances.maxDistance(field);\n'
     '\t\t\tthis.chunks.defaultReturnValue((byte)(this.maxDistance + 2));\n\t\t}\n'),
    ('\t\t@Override\n\t\tprotected int getLevel(long l) {\n\t\t\treturn this.chunks.get(l);\n\t\t}\n\n'
     '\t\t@Override\n\t\tprotected void setLevel(long l, int i) {',
     '\t\tprotected int getLevel(long l) {\n\t\t\treturn this.chunks.get(l);\n\t\t}\n\n'
     '\t\tprotected void setLevel(long l, int i) {'),
    ('\t\t@Override\n\t\tprotected int getLevelFromSource(long l) {\n'
     '\t\t\treturn this.havePlayer(l) ? 0 : Integer.MAX_VALUE;\n\t\t}\n\n'
     '\t\tprivate boolean havePlayer(long l) {\n'
     '\t\t\tObjectSet<ServerPlayer> objectSet = DistanceManager.this.playersPerChunk.get(l);\n'
     '\t\t\treturn objectSet != null && !objectSet.isEmpty();\n\t\t}\n\n'
     '\t\tpublic void runAllUpdates() {\n\t\t\tthis.runUpdates(Integer.MAX_VALUE);\n\t\t}\n',
     '\t\tpublic void runAllUpdates() {\n\t\t\tthis.distances.runAllUpdates(this.field, this.levelSink);\n\t\t}\n'),
    ('\t\tprotected PlayerTicketTracker(final int i) {\n\t\t\tsuper(i);\n\t\t\tthis.viewDistance = 0;\n'
     '\t\t\tthis.queueLevels.defaultReturnValue(i + 2);\n\t\t}\n',
     '\t\tprotected PlayerTicketTracker(PlayerChunkDistances distances, int field) {\n'
     '\t\t\tsuper(distances, field);\n\t\t\tthis.viewDistance = 0;\n'
     '\t\t\tthis.queueLevels.defaultReturnValue(this.maxDistance + 2);\n\t\t}\n'),
]


def run(command, path):
    result = subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    path.write_text(result.stdout)
    if result.returncode:
        raise RuntimeError('Command failed; see ' + str(path))
    return result.stdout


def git_show(commit, name):
    return subprocess.check_output(['git', 'show', commit + ':' + name], cwd=ROOT, text=True)


def audit(out):
    if (ROOT / oracle.ORACLE).read_text() != oracle.expected_oracle(ROOT):
        raise RuntimeError('Original tracker oracle differs from the pinned reference rewrite')
    if (ROOT / oracle.SIMULATION_ORACLE).read_text() != oracle.expected_simulation_oracle(ROOT):
        raise RuntimeError('Original simulation tracker oracle differs from the pinned reference rewrite')
    expected = git_show(oracle.REFERENCE, TICKET_STORAGE)
    for old, new in TICKET_STORAGE_REWRITES:
        if expected.count(old) != 1:
            raise RuntimeError('TicketStorage rewrite does not apply exactly once: ' + old[:60])
        expected = expected.replace(old, new)
    if (ROOT / TICKET_STORAGE).read_text() != expected:
        raise RuntimeError('TicketStorage changed beyond the seeding accessor')
    for name in ['JavaChunkTracker.java', 'JavaDynamicGraphMinFixedPoint.java', 'JavaLeveledPriorityQueue.java']:
        if (ROOT / LIGHTING_TESTS / name).read_text() != git_show(GRAPH_ORACLE_COMMIT, LIGHTING_TESTS + name):
            raise RuntimeError('Pinned original graph oracle changed: ' + name)
    expected = git_show(oracle.REFERENCE, PRODUCTION)
    for old, new in PRODUCTION_REWRITES:
        if expected.count(old) != 1:
            raise RuntimeError('Production rewrite does not apply exactly once: ' + old[:60])
        expected = expected.replace(old, new)
    if (ROOT / PRODUCTION).read_text() != expected:
        raise RuntimeError('Production DistanceManager changed beyond the documented native ownership edits')
    (out / 'reference.txt').write_text(oracle.REFERENCE + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='build/player-chunk-distance-migration/acceptance')
    parser.add_argument('--forks', type=int, default=3)
    parser.add_argument('--cpu', type=int, default=5)
    parser.add_argument('--background-cpus', default='0,1')
    parser.add_argument('--case', default='all')
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--parity-only', action='store_true')
    parser.add_argument('--pilot', action='store_true')
    args = parser.parse_args()
    cases = CASES if args.case == 'all' else args.case.split(',')
    if len(set(cases)) != len(cases) or any(case not in CASES for case in cases):
        parser.error('Unknown/duplicate case')
    if not args.pilot and not args.parity_only and args.forks < 3:
        parser.error('At least three independent comparisons required')
    background = [int(cpu) for cpu in args.background_cpus.split(',')]
    if len(background) < 2 or args.cpu in background or len(set(background)) != len(background):
        parser.error('Separate worker CPUs required')
    if not {args.cpu, *background}.issubset(os.sched_getaffinity(0)):
        parser.error('CPU unavailable')
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / 'build'):
        parser.error('Output must be under build/')
    out.mkdir(parents=True, exist_ok=True)
    audit(out)
    classpath, init = out / 'classpath.txt', out / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.named('test') { outputs.upToDateWhen { false } }; "
                    "gradle.rootProject.tasks.register('writePlayerDistanceClasspath') { dependsOn 'testClasses'; doLast { new File("
                    + json.dumps(str(classpath)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    command = ['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writePlayerDistanceClasspath', '-x', 'testRustNative', '--console=plain']
    if args.skip_build:
        command += ['-x', 'buildRustNative']
    run(command, out / 'build.log')
    tests = []
    for name in PARITY_TESTS:
        tests += ['--tests', PACKAGE + name]
    run(['./gradlew', '-I', str(init), 'test', '-x', 'buildRustNative', '-x', 'testRustNative', *tests, '--console=plain'], out / 'parity.log')
    java_tests = 0
    for name in PARITY_TESTS:
        xml = (ROOT / ('build/test-results/test/TEST-' + PACKAGE + name + '.xml')).read_text()
        (out / ('parity-' + name + '.xml')).write_text(xml)
        result = ET.fromstring(xml)
        if int(result.attrib['failures']) or int(result.attrib['errors']):
            raise RuntimeError('Parity failed: ' + name)
        java_tests += int(result.attrib['tests'])
    rust = subprocess.run(['cargo', 'test', '--release', 'world::level::chunk_distance'], cwd=ROOT / 'src/main/rust',
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    (out / 'rust-tests.log').write_text(rust.stdout)
    if rust.returncode or 'test result: ok. 6 passed' not in rust.stdout:
        raise RuntimeError('Rust tests failed; see rust-tests.log')
    library = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    flags = ['-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '--enable-native-access=ALL-UNNAMED',
             '-XX:ActiveProcessorCount=' + str(1 + len(background))]
    base = ['taskset', '-c', ','.join(str(cpu) for cpu in [args.cpu, *background]), 'java', *flags,
            '-Dmattmc.rust.natives.dir=' + str(library.parent), '-cp', classpath.read_text().strip()]
    files = [ROOT / PRODUCTION, ROOT / 'src/main/java/net/minecraft/server/level/PlayerChunkDistances.java',
             ROOT / TICKET_STORAGE, ROOT / oracle.SIMULATION_ORACLE,
             *[ROOT / 'src/main/java/net/minecraft/server/level' / name for name in ['SimulationChunkTracker.java', 'SimulationChunkDistance.java']],
             Path(__file__).resolve(), Path(oracle.__file__).resolve(), ROOT / oracle.ORACLE,
             *sorted((ROOT / 'src/main/rust/world/level/chunk_distance').glob('*.rs')),
             ROOT / 'src/main/rust/world/level/lighting/priority_queue/queue.rs',
             *[TEST_BASE / name for name in ['NativePlayerChunkDistancesTest.java', 'PlayerChunkDistancesVerification.java',
                                             'NativeSimulationChunkTrackerTest.java', 'SimulationChunkTrackerVerification.java']],
             *[ROOT / LIGHTING_TESTS / name for name in ['JavaChunkTracker.java', 'JavaDynamicGraphMinFixedPoint.java', 'JavaLeveledPriorityQueue.java']]]
    report = {
        'reference': oracle.REFERENCE,
        'scope': 'Native-owned player presence, natural-spawn, player-ticket and simulation distance graphs, the simulation ticket-level '
                 'mirror, pending levels and queues. Player cases time DistanceManager bookkeeping, ChunkMap move order, per-tick '
                 'runAllUpdates, ticket-tracker toUpdate collection and spawn-range queries; simulation cases time real TicketStorage '
                 'ticket changes, per-tick runAllUpdates, entity-ticking queries and iteration. All native calls, change-log drains and '
                 'published-view replay included. Construction excluded equally. No ticket dispatch, chunk loading or whole-game tests.',
        'cpu_time_scope': 'Benchmark thread CPU time including Java and Rust execution; excludes waiting and concurrent worker CPU. Wall time is primary.',
        'pilot': args.pilot, 'cases': cases, 'java_tests': java_tests,
        'rust_tests': rust.stdout.splitlines()[-3:], 'cpu': args.cpu, 'background_cpus': background, 'jvm': flags,
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
            for mode in (['java', 'native'] if fork % 2 == 0 else ['native', 'java']):
                print(f'Comparison {fork + 1}: {name} {mode}', flush=True)
                benchmark = 'SimulationChunkTrackerVerification' if name in SIMULATION_CASES else 'PlayerChunkDistancesVerification'
                text = run(base + [PACKAGE + benchmark, mode, name] + (['quick'] if args.pilot else []),
                           out / f'{fork}-{name}-{mode}.log')
                bench = re.findall(r'PLAYER_DISTANCE_BENCH case=(\w+) mode=(\w+) warmup_ns=(\d+) repeats=\d+ ns=(\[.*?\]) '
                                   r'cpu_ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)', text)
                fixture = re.findall(r'PLAYER_DISTANCE_FIXTURE case=(\w+) ticks=(\d+) moves=(\d+) trace=([a-f0-9]{64})', text)
                if len(bench) != 1 or bench[0][0] != name or len(fixture) != 1 or fixture[0][0] != name:
                    raise RuntimeError('Incomplete measurement')
                _, _, warmup, samples, cpu, jit, checksum = bench[0]
                metadata = {'ticks': int(fixture[0][1]), 'moves': int(fixture[0][2]), 'trace_sha256': fixture[0][3]}
                if 'fixture' in row and row['fixture'] != metadata:
                    raise RuntimeError('Different workload')
                row['fixture'] = metadata
                if not args.pilot and int(warmup) < 15_000_000_000:
                    raise RuntimeError('Insufficient warmup')
                row[mode + '_jit'] = json.loads(jit)
                if 'checksum' in row and row['checksum'] != int(checksum):
                    raise RuntimeError('Backend outputs differ for ' + name)
                row['checksum'] = int(checksum)
                row[mode] = json.loads(samples)
                row[mode + '_cpu'] = json.loads(cpu)
                save()
    rng = random.Random(1977)

    def summarize(pairs):
        ratios = [statistics.median(n) / statistics.median(j) for j, n in pairs]
        bootstrap = sorted(statistics.median(statistics.median(rng.choices(n, k=len(n))) / statistics.median(rng.choices(j, k=len(j)))
                                             for j, n in rng.choices(pairs, k=len(pairs))) for _ in range(10000))
        return {'java_ns': statistics.median(statistics.median(j) for j, n in pairs),
                'native_ns': statistics.median(statistics.median(n) for j, n in pairs),
                'ratios': ratios, 'ratio_ci95': [bootstrap[250], bootstrap[9749]],
                'passes': max(ratios) <= .95 and bootstrap[9749] <= .95}

    for name in cases:
        report['performance'][name] = summarize([(p[name]['java'], p[name]['native']) for p in report['pairs']])
        report['performance'][name]['thread_cpu'] = summarize([(p[name]['java_cpu'], p[name]['native_cpu']) for p in report['pairs']])
        report['performance'][name]['jit_active_samples'] = sum(
            1 for p in report['pairs'] for mode in ('java', 'native') for value in p[name][mode + '_jit'] if value)
    for name, digest in report['sources'].items():
        if hashlib.sha256((ROOT / name).read_bytes()).hexdigest() != digest:
            raise RuntimeError('Measured source changed: ' + name)
    if hashlib.sha256(library.read_bytes()).hexdigest() != report['native_sha256']:
        raise RuntimeError('Measured library changed')
    for name in cases:
        if len({p[name]['checksum'] for p in report['pairs']}) != 1:
            raise RuntimeError('Cross-JVM output mismatch')
        if len({p[name]['fixture']['trace_sha256'] for p in report['pairs']}) != 1:
            raise RuntimeError('Cross-JVM input transcript mismatch')
    report['integrity'] = {'source_hashes_match': True, 'native_hash_matches': True, 'cross_fork_checksums_match': True,
                           'backend_checksums_match': True}
    save()
    print(json.dumps(report['performance'], indent=2))
    # Performance is reported honestly; 5% is a preference, not a correctness gate.
    print(out / 'results.json')


if __name__ == '__main__':
    main()
