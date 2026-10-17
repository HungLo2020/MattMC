#!/usr/bin/env python3
"""Run the identical real player-loading policy fixture on Frozen/control/current.

Build current and Frozen beforehand, then provide their main+test-dependency
classpaths. The control compiles the actual pre-change sources, not an expected
implementation. This scripted executor/future oracle supplements a separate
real dedicated-server player lifecycle gate.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[4]
FROZEN = ROOT.parent / 'MattMC_JavaPerfTesting/MattMC'
FROZEN_HEAD = '7a4d181717dc0c7da9086f1a17687dfd522ce417'
DRIVER = Path(__file__).with_suffix('.java')
CONTROL_SOURCES = [
    'server/level/DistanceManager.java', 'server/level/PlayerChunkDistances.java',
    'server/level/ThrottlingChunkTaskDispatcher.java', 'server/level/ChunkTaskDispatcher.java',
    'server/level/NativeChunkTasks.java', 'server/level/ChunkTaskPriorityQueue.java',
    'world/level/TicketStorage.java', 'world/level/NativeTicketStorage.java',
]


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def source_identity():
    digest = hashlib.sha256(subprocess.check_output(['git', 'diff', 'HEAD', '--', 'src'], cwd=ROOT))
    names = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', '--', 'src'],
                                    cwd=ROOT, text=True).splitlines()
    for name in sorted(names):
        digest.update(name.encode() + b'\0' + (ROOT / name).read_bytes())
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--frozen-classpath-file', type=Path, required=True)
    parser.add_argument('--current-classpath-file', type=Path, required=True)
    parser.add_argument('--control-ref', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--seeds', type=int, default=4)
    parser.add_argument('--steps', type=int, default=160)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    jdk = args.java_home.resolve() / 'bin'
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=FROZEN, text=True).strip() == FROZEN_HEAD
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=FROZEN)
    base = subprocess.check_output(['git', 'rev-parse', args.control_ref + '^{commit}'], cwd=ROOT, text=True).strip()
    result = {
        'frozen_head': FROZEN_HEAD, 'control_ref': base,
        'candidate_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'candidate_source_sha256': source_identity(), 'native_sha256': sha(native),
        'driver_sha256': sha(DRIVER), 'seeds': args.seeds, 'steps_per_seed': args.steps,
        'scope': 'Actual DistanceManager/ThrottlingChunkTaskDispatcher/TicketStorage; manually stepped executors, '
                 'fixture holder callbacks and real controlled CompletableFutures. Not a live server/player gate.',
        'passed': False,
    }
    env = os.environ.copy()
    for name in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        env.pop(name, None)
    classpaths = {
        'frozen': args.frozen_classpath_file.read_text().strip(),
        'current': args.current_classpath_file.read_text().strip(),
    }

    def run(command, name):
        result.setdefault('commands', {})[name] = command
        with (output / name).open('w') as log:
            code = subprocess.call(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
        if code:
            (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
            raise RuntimeError(f'{name} exited {code}; see {output / name}')
        return (output / name).read_text()

    control = output / 'control-classes'
    control.mkdir()
    sources = []
    for name in CONTROL_SOURCES:
        destination = output / 'control-source' / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(subprocess.check_output(['git', 'show', f'{base}:src/main/java/net/minecraft/{name}'], cwd=ROOT))
        sources.append(str(destination))
    run([str(jdk / 'javac'), '-nowarn', '-cp', classpaths['current'], '-d', str(control), *sources], 'compile-control.log')
    classpaths['control'] = str(control) + os.pathsep + classpaths['current']
    for side in ('frozen', 'control', 'current'):
        classes = output / ('driver-' + side)
        classes.mkdir()
        run([str(jdk / 'javac'), '-nowarn', '-cp', classpaths[side], '-d', str(classes), str(DRIVER)], f'compile-{side}.log')
        data = run([
            str(jdk / 'java'), '-Xms1g', '-Xmx2g', '-XX:+UseG1GC',
            '-XX:+UnlockExperimentalVMOptions', '-XX:hashCode=2', '-XX:-OmitStackTraceInFastThrow',
            '--enable-native-access=ALL-UNNAMED', '-XX:+EnableDynamicAgentLoading', '-Dnet.bytebuddy.experimental=true',
            '-Dmattmc.rust.natives.dir=' + str(native.parent), '-cp', str(classes) + os.pathsep + classpaths[side],
            'net.minecraft.server.level.VerifyPlayerLoading', side, str(output / f'{side}-trace.txt'),
            str(args.seeds), str(args.steps),
        ], f'{side}.log')
        trace = re.search(r'TRACE (\w+) (\d+)', data)
        coverage = re.search(r'COVERAGE (.+)', data)
        assert trace and coverage, f'Missing trace/coverage for {side}'
        result[side] = {'trace': trace[1], 'events': int(trace[2]), 'coverage': coverage[1]}
    result['traces_equal'] = len({result[side]['trace'] for side in classpaths}) == 1
    if not result['traces_equal']:
        for side in ('control', 'current'):
            with (output / 'frozen-trace.txt').open() as left, (output / f'{side}-trace.txt').open() as right:
                from itertools import zip_longest
                for index, (x, y) in enumerate(zip_longest(left, right), 1):
                    if x != y:
                        result.setdefault('first_difference', {})[side] = {'line': index, 'frozen': x, side: y}
                        break
    result['inputs_unchanged'] = source_identity() == result['candidate_source_sha256'] and sha(native) == result['native_sha256']
    result['passed'] = result['traces_equal'] and result['inputs_unchanged']
    (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({key: value for key, value in result.items() if key != 'commands'}, indent=2))
    return 0 if result['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
