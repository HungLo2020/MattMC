#!/usr/bin/env python3
"""Compare the real ChunkMap holder scheduling, status-transition, unload and
eager-save pipeline across Frozen, pre-change Current (its sources compiled over the
candidate classpath) and the candidate. All traces must be identical.

DistanceManager's chunksToUpdateFutures is an identity-hash set, so each side runs twice:
- exact: -XX:hashCode=2 (every identity hash equal) makes its traversal, including
  resizes and additions during reentrant passes, the same on every JVM; the full
  traces (prepare throws and reentry included) must match in order;
- hash: ordinary identity hashes; the driver replays each recorded add through an
  independent model of the table and checks every pass ran in the predicted order
  (0 mismatches required), and the order-independent traces must match.
Benchmarks use ordinary hashing.

  python3 DevUtils/tests/world/chunkholders/VerifyChunkHolders.py --java-home "$JAVA_HOME" \
    --control-ref <pre-change commit> --output /new/dir
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import statistics
import subprocess

ROOT = Path(__file__).resolve().parents[4]
FROZEN = ROOT.parent / 'MattMC_JavaPerfTesting/MattMC'
FROZEN_HEAD = '7a4d181717dc0c7da9086f1a17687dfd522ce417'
DRIVER = ROOT / 'DevUtils/tests/world/chunkholders/VerifyChunkHolders.java'
# Files the native holder-registry slice replaces; the control side compiles their
# pre-change sources over the candidate classpath.
CONTROL_SOURCES = ['server/level/ChunkMap.java', 'server/level/DistanceManager.java', 'server/level/LoadingChunkTracker.java',
                   'server/level/ChunkHolder.java', 'server/level/NativeChunkHolders.java', 'server/level/TicketChunkDistance.java']
# Equal identity hashes for the traced runs (see above).
TRACE_FLAGS = ['-XX:+UnlockExperimentalVMOptions', '-XX:hashCode=2']


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def candidate_patch_sha():
    """Tracked source diff plus untracked source files, so new files are pinned too."""
    digest = hashlib.sha256(subprocess.check_output(['git', 'diff', 'HEAD', '--', 'src'], cwd=ROOT))
    untracked = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', '--', 'src'], cwd=ROOT, text=True).split()
    for name in sorted(untracked):
        digest.update(name.encode() + b'\0' + (ROOT / name).read_bytes())
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--control-ref', required=True, help='pre-change Current commit for the control side')
    parser.add_argument('--benchmark', action='store_true', help='also time the untraced pipeline: 3 alternating rounds per side')
    args = parser.parse_args()
    assert args.java_home, 'JDK 25 required'
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    jdk = Path(args.java_home) / 'bin'
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    frozen_head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=FROZEN, text=True).strip()
    assert frozen_head == FROZEN_HEAD, frozen_head
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=FROZEN)
    control_ref = subprocess.check_output(['git', 'rev-parse', args.control_ref + '^{commit}'], cwd=ROOT, text=True).strip()
    result = {'frozen_head': frozen_head, 'control_ref': control_ref, 'native': sha(native), 'driver': sha(DRIVER),
              'candidate_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
              'candidate_diff_sha256': candidate_patch_sha(),
              'scope': 'Real ChunkMap without its constructor: real TicketStorage, ChunkMap.DistanceManager.runAllUpdates '
                       '(loading graph, holder scheduling, both chunksToUpdateFutures passes) with driver prepare futures '
                       '(pre-completed, completed later with a chunk, unloaded or failed, or throwing), a reentrant '
                       'full-status listener (ticket changes and loading runs inside a pass) and the worldgen/light '
                       'queue-level listeners; promote; real holders with injected full chunks, save dependencies '
                       'completed out of order; tick (unload queue, eager saves) under budgets; saveAllChunks; real '
                       'ChunkMap.save with writes completed or failed by the driver; driver clock for the 10 s save throttle. '
                       'Observed through ChunkMap and holder methods: visible order and levels, updating/visible identity, '
                       'per-holder level, accessibility, queue level, full status and future identity, prepare/listener '
                       'effects in order, pending chunksToUpdateFutures in traversal order, size, '
                       'save/unload/level-unload/failure order, active writes. 8 seeds x 2000 steps.',
              'passed': False}
    env = os.environ.copy()
    for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        env.pop(key, None)
    env.update(JAVA_HOME=str(jdk.parent), PATH=str(jdk) + os.pathsep + env.get('PATH', ''))
    init = output / 'classpath.gradle'
    # Main runtime plus the test dependency jars. No task dependency: Frozen finalizes
    # compileJava with its test suite. Build both trees first.
    init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('holdersPrintClasspath') { "
                    "doLast { println('HOLDERS_CP '+p.sourceSets.main.runtimeClasspath.asPath+File.pathSeparator"
                    "+p.configurations.testRuntimeClasspath.asPath) } } }\n")

    def run(command, name, cwd=ROOT):
        with (output / name).open('w') as log:
            status = subprocess.call(command, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT)
        if status:
            raise RuntimeError(f'{name} exited {status}; see {output / name}')
        return (output / name).read_text()

    classpaths, commands = {}, {}
    for side, repo in [('frozen', FROZEN), ('current', ROOT)]:
        data = run([str(repo / 'gradlew'), '-q', '-I', str(init), 'holdersPrintClasspath'], side + '-classpath.log', repo)
        classpaths[side] = next(line.removeprefix('HOLDERS_CP ') for line in data.splitlines() if line.startswith('HOLDERS_CP '))
    control_classes = output / 'control-classes'
    control_classes.mkdir()
    sources = []
    for name in CONTROL_SOURCES:
        destination = output / 'control-source' / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(subprocess.check_output(['git', 'show', f'{control_ref}:src/main/java/net/minecraft/{name}'], cwd=ROOT))
        sources.append(str(destination))
    run([str(jdk / 'javac'), '-nowarn', '-cp', classpaths['current'], '-d', str(control_classes)] + sources, 'compile-control.log')
    classpaths['control'] = str(control_classes) + os.pathsep + classpaths['current']
    for side in ('frozen', 'control', 'current'):
        target = output / 'driver' / side
        target.mkdir(parents=True)
        run([str(jdk / 'javac'), '-nowarn', '-cp', classpaths[side], '-d', str(target), str(DRIVER)], f'compile-driver-{side}.log')
        command = [str(jdk / 'java'), '-Xms1g', '-Xmx1g', '-XX:+UseG1GC', '--enable-native-access=ALL-UNNAMED',
                   '-XX:+EnableDynamicAgentLoading', '-Dnet.bytebuddy.experimental=true', '-Dmattmc.rust.natives.dir=' + str(native.parent)]
        command += ['-cp', str(target) + os.pathsep + classpaths[side],
                    'net.minecraft.server.level.VerifyChunkHolders', side, str(output / f'{side}-trace.txt')]
        commands[side] = command
        data = run(command[:1] + TRACE_FLAGS + command[1:], f'{side}-run.log')
        trace = re.search(r'TRACE (\w+) (\d+)', data)
        assert trace, f'{side}: no trace'
        result[side] = {'trace': trace.group(1), 'steps': int(trace.group(2))}
        hashed = command[:-2] + [side, str(output / f'{side}-hash-trace.txt'), 'hash']
        data = run(hashed, f'{side}-hash-run.log')
        trace = re.search(r'TRACE (\w+) (\d+)', data)
        assert trace, f'{side}: no hash trace'
        oracle = [tuple(map(int, m)) for m in re.findall(r'ORACLE seed \d+ passes (\d+) holders (\d+) mismatches (\d+)', data)]
        result[side]['hash_trace'] = trace.group(1)
        # Counters are cumulative across seeds: the last line has the totals.
        result[side]['oracle'] = dict(zip(('passes', 'holders', 'mismatches'), oracle[-1])) if oracle else None
    sides = ('frozen', 'control', 'current')
    result['traces_equal'] = len({result[side]['trace'] for side in sides}) == 1
    result['hash_traces_equal'] = len({result[side]['hash_trace'] for side in sides}) == 1
    result['oracle_ok'] = all(result[side]['oracle'] and result[side]['oracle']['passes'] > 0
                              and result[side]['oracle']['mismatches'] == 0 for side in sides)
    for suffix, key in (('', 'traces_equal'), ('-hash', 'hash_traces_equal')):
        if result[key]:
            continue
        for side in ('control', 'current'):
            for line, (a, b) in enumerate(zip((output / f'frozen{suffix}-trace.txt').open(), (output / f'{side}{suffix}-trace.txt').open())):
                if a != b:
                    result.setdefault('first_difference' + suffix, {})[side] = {'line': line, 'frozen': a.strip()[:2000], side: b.strip()[:2000]}
                    break
    if args.benchmark:
        timings = {side: [] for side in commands}
        for round_index in range(3):
            for side in ('frozen', 'control', 'current'):
                data = run(commands[side] + ['benchmark', '15', '5'], f'benchmark-{round_index}-{side}.log')
                timings[side] += [int(ns) for ns in re.findall(r'TIMING \d+ (\d+)', data)]
        result['benchmark'] = {side: {'samples': len(v), 'median_ns_per_run': statistics.median(v), 'min_ns': min(v), 'max_ns': max(v)}
                               for side, v in timings.items()}
        result['benchmark_workload'] = ('the traced pipeline untraced: 4 seeds x 2000 steps of ticket churn, loading-graph runs '
                                        'with holder scheduling, promotes, unload-queue ticks, eager saves and stubbed writes, '
                                        'plus updating/visible lookups of 40 keys per step; Mockito interception of ChunkMap '
                                        'calls applies equally to every side; 5 warmups then 10 samples per JVM, 3 rounds')
    result['passed'] = result['traces_equal'] and result['hash_traces_equal'] and result['oracle_ok']
    (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({key: value for key, value in result.items() if key not in ('frozen', 'control', 'current')}, indent=2))
    print('PASS' if result['passed'] else 'FAIL')
    raise SystemExit(0 if result['passed'] else 1)


if __name__ == '__main__':
    main()
