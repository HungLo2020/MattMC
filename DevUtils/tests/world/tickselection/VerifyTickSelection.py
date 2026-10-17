#!/usr/bin/env python3
"""Compare tick selection (block-ticking and spawn-candidate walks, shuffle, spawn count
and point reads) across Frozen, pre-change Current (its sources compiled over the
candidate classpath) and the candidate, driving a real DistanceManager and
TicketStorage. All traces must be identical. Keys are chunk longs, so no identity
hashing affects order.

  python3 DevUtils/tests/world/tickselection/VerifyTickSelection.py --java-home "$JAVA_HOME" \
    --control-ref <pre-change commit> --output /new/dir [--benchmark]
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
DRIVER = ROOT / 'DevUtils/tests/world/tickselection/VerifyTickSelection.java'
# Files the native tick-selection views replace; the control side compiles their
# pre-change sources over the candidate classpath.
CONTROL_SOURCES = ['server/level/SimulationChunkTracker.java', 'server/level/DistanceManager.java',
                   'server/level/PlayerChunkDistances.java']


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
    parser.add_argument('--benchmark', action='store_true', help='also time the untraced workload: 3 alternating rounds per side')
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
              'scope': 'Real DistanceManager and TicketStorage: seeded forced and player-simulation tickets, headless '
                       'stub players joining and leaving, simulation distance changes and simulation runs. Each step: the '
                       'block-ticking walk in order (some walks reenter with ticket changes and a simulation run, or throw '
                       'at a chosen entry; visited prefix and iterator exception recorded), the spawn-candidate walk in '
                       'order, Util.shuffle of it with a seeded RandomSource and its final state, spawn count, and '
                       'getChunkLevel/inEntityTickingRange/inBlockTickingRange/hasPlayersNearby over a key grid. '
                       '8 seeds x 3000 steps.',
              'passed': False}
    env = os.environ.copy()
    for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        env.pop(key, None)
    env.update(JAVA_HOME=str(jdk.parent), PATH=str(jdk) + os.pathsep + env.get('PATH', ''))
    init = output / 'classpath.gradle'
    # Main runtime plus the test dependency jars. No task dependency: Frozen finalizes
    # compileJava with its test suite. Build both trees first.
    init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('selectionPrintClasspath') { "
                    "doLast { println('SELECTION_CP '+p.sourceSets.main.runtimeClasspath.asPath+File.pathSeparator"
                    "+p.configurations.testRuntimeClasspath.asPath) } } }\n")

    def run(command, name, cwd=ROOT):
        with (output / name).open('w') as log:
            status = subprocess.call(command, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT)
        if status:
            raise RuntimeError(f'{name} exited {status}; see {output / name}')
        return (output / name).read_text()

    classpaths, commands = {}, {}
    for side, repo in [('frozen', FROZEN), ('current', ROOT)]:
        data = run([str(repo / 'gradlew'), '-q', '-I', str(init), 'selectionPrintClasspath'], side + '-classpath.log', repo)
        classpaths[side] = next(line.removeprefix('SELECTION_CP ') for line in data.splitlines() if line.startswith('SELECTION_CP '))
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
        # Keep Java's iterator exception messages deterministic: without this, C2 can
        # replace a hot NullPointerException with a preallocated one with no message.
        command = [str(jdk / 'java'), '-Xms1g', '-Xmx1g', '-XX:+UseG1GC', '-XX:-OmitStackTraceInFastThrow', '--enable-native-access=ALL-UNNAMED',
                   '-XX:+EnableDynamicAgentLoading', '-Dnet.bytebuddy.experimental=true', '-Dmattmc.rust.natives.dir=' + str(native.parent)]
        command += ['-cp', str(target) + os.pathsep + classpaths[side],
                    'net.minecraft.server.level.VerifyTickSelection', side, str(output / f'{side}-trace.txt')]
        commands[side] = command
        data = run(command, f'{side}-run.log')
        trace = re.search(r'TRACE (\w+) (\d+)', data)
        assert trace, f'{side}: no trace'
        result[side] = {'trace': trace.group(1), 'steps': int(trace.group(2)),
                        'reader': re.findall(r'READER seed \d+ reads (\d+) inconsistent (\d+)', data)}
    sides = ('frozen', 'control', 'current')
    result['traces_equal'] = len({result[side]['trace'] for side in sides}) == 1
    if not result['traces_equal']:
        for side in ('control', 'current'):
            for line, (x, y) in enumerate(zip((output / 'frozen-trace.txt').open(), (output / f'{side}-trace.txt').open())):
                if x != y:
                    result.setdefault('first_difference', {})[side] = {'line': line, 'frozen': x.strip()[:2000], side: y.strip()[:2000]}
                    break
    if args.benchmark:
        workloads = {
            'benchmark': ('4 seeds x 3000 steps of the same churn untraced; per step a simulation run, the block-ticking '
                          'walk, the spawn-candidate walk, the spawn count and 4 x 64 inEntityTickingRange point reads'),
            'benchmark-points': ('the same churn and simulation runs; per step only 16 x 64 inEntityTickingRange plus '
                                 'inBlockTickingRange point reads (the hot entity-tick path)'),
        }
        result['benchmark'] = {}
        for workload, description in workloads.items():
            timings = {side: [] for side in commands}
            for round_index in range(3):
                for side in ('frozen', 'control', 'current'):
                    data = run(commands[side] + [workload, '15', '5'], f'{workload}-{round_index}-{side}.log')
                    timings[side] += [int(ns) for ns in re.findall(r'TIMING \d+ (\d+)', data)]
            result['benchmark'][workload] = {
                'workload': description + '; 5 warmups then 10 samples per JVM, 3 rounds',
                **{side: {'samples': len(v), 'median_ns_per_run': statistics.median(v), 'min_ns': min(v), 'max_ns': max(v)}
                   for side, v in timings.items()}}
    result['passed'] = result['traces_equal']
    (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({key: value for key, value in result.items() if key not in ('frozen', 'control', 'current')}, indent=2))
    print('PASS' if result['passed'] else 'FAIL')
    raise SystemExit(0 if result['passed'] else 1)


if __name__ == '__main__':
    main()
