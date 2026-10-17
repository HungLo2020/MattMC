#!/usr/bin/env python3
"""Compare actual TicketStorage traces across Frozen, pre-change Current and the candidate."""
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
DRIVER = ROOT / 'DevUtils/tests/world/tickets/VerifyTicketStorage.java'
# Original observer pin: fixture adaptation must match its Frozen trace.
ORIGINAL_OBSERVER_REF = '5f9c0b3b819a72d374882418c0e7c08145096ac4'


def verify_runtime(path):
    manifest = json.loads((path / 'manifest.json').read_text())
    if not manifest.get('complete_classpath'):
        raise ValueError('A complete sealed pre-change runtime is required')
    for name, expected in manifest['files'].items():
        if sha(path / name) != expected:
            raise ValueError('Pre-change runtime changed: ' + name)
    for name, expected in manifest.get('dependency_jars', {}).items():
        if sha(Path(name)) != expected:
            raise ValueError('Pre-change dependency changed: ' + name)
    return manifest


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def candidate_patch_sha():
    """Tracked source diff plus untracked source files, so new files are pinned too."""
    digest = hashlib.sha256(subprocess.check_output(['git', 'diff', 'HEAD', '--', 'src'], cwd=ROOT))
    untracked = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', '--', 'src'], cwd=ROOT, text=True).split()
    for name in sorted(untracked):
        digest.update(name.encode() + b'\0' + (ROOT / name).read_bytes())
    return digest.hexdigest()


def current_runtime_sha():
    digest = hashlib.sha256()
    for directory in ('build/classes/java/main', 'build/resources/main'):
        for path in sorted((ROOT / directory).rglob('*')):
            if path.is_file():
                digest.update(path.relative_to(ROOT).as_posix().encode() + b'\0')
                digest.update(bytes.fromhex(sha(path)))
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--pre-runtime', type=Path, required=True,
                        help='complete sealed pre-change classes, resources, dependencies and native library')
    parser.add_argument('--control-ref', help='optional expected pre-change source commit')
    parser.add_argument('--frozen-native-dir', type=Path, required=True,
                        help='explicit compatible native directory for the pinned Frozen runtime')
    parser.add_argument('--benchmark', action='store_true')
    parser.add_argument('--batch-capacities', default='1,256',
                        help='extra candidate runs with fixed Java-predicate batch sizes; all traces must match')
    args = parser.parse_args()
    assert args.java_home, 'JDK 25 required'
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    jdk = Path(args.java_home) / 'bin'
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    frozen_head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=FROZEN, text=True).strip()
    assert frozen_head == FROZEN_HEAD, frozen_head
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=FROZEN)
    pre_runtime = args.pre_runtime.resolve()
    pre_manifest = verify_runtime(pre_runtime)
    control_ref = pre_manifest['source_head']
    if args.control_ref:
        expected = subprocess.check_output(['git', 'rev-parse', args.control_ref + '^{commit}'], cwd=ROOT, text=True).strip()
        assert control_ref == expected, (control_ref, expected)
    native_dirs = {'frozen': args.frozen_native_dir.resolve(), 'frozen-original': args.frozen_native_dir.resolve(),
                   'control': pre_runtime / 'native', 'current': native.parent}
    native_hashes = {side: sha(directory / native.name) for side, directory in native_dirs.items()}
    result = {'frozen_head': frozen_head, 'control_ref': control_ref, 'native': sha(native), 'driver': sha(DRIVER),
              'candidate_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
              'candidate_diff_sha256': candidate_patch_sha(),
              'candidate_runtime_sha256': current_runtime_sha(),
              'pre_runtime_manifest_sha256': sha(pre_runtime / 'manifest.json'),
              'native_images': native_hashes,
              'scope': 'Actual TicketStorage add/remove (new, shared-instance, same type/level re-add, radius), forced toggles, '
                       'purge against stub ChunkMap holders whose save futures complete mid-walk, close/reactivate, level replacement '
                       'and codec save/load; listener calls with notification-time level reads, ticket lists, ticksLeft, debug strings, '
                       'forced-set identity and order, keep-active; retained/written views, a second storage sharing Tickets, listener-driven mutation and Java removeTicketIf predicates that log calls, change later shared tickets, mutate and throw; seeds 9-12 trace a real natively notified SimulationChunkTracker, seeds 13-14 hold 700 tickets to cross predicate batch boundaries. 12 x 3000 + 2 x 600 steps; the candidate also runs at fixed batch sizes. No server.',
              'passed': False}
    env = os.environ.copy()
    for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        env.pop(key, None)
    env.update(JAVA_HOME=str(jdk.parent), PATH=str(jdk) + os.pathsep + env.get('PATH', ''))
    init = output / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('ticketsPrintClasspath') { "
                    "doLast { println('TICKETS_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")

    def run(command, name, cwd=ROOT):
        with (output / name).open('w') as log:
            status = subprocess.call(command, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT)
        if status:
            raise RuntimeError(f'{name} exited {status}; see {output / name}')
        return (output / name).read_text()

    classpaths, commands = {}, {}
    for side, repo in [('frozen', FROZEN), ('current', ROOT)]:
        data = run([str(repo / 'gradlew'), '-q', '-I', str(init), 'ticketsPrintClasspath'], side + '-classpath.log', repo)
        classpaths[side] = next(line.removeprefix('TICKETS_CP ') for line in data.splitlines() if line.startswith('TICKETS_CP '))
    classpaths['control'] = (pre_runtime / 'classpath.txt').read_text().strip()
    # No candidate classes or library may enter the sealed pre-change side.
    assert all((ROOT / 'build/classes/java/main').as_posix() != entry for entry in classpaths['control'].split(os.pathsep))
    classpaths['frozen-original'] = classpaths['frozen']
    original_source = output / 'original-driver' / 'net/minecraft/world/level/VerifyTicketStorage.java'
    original_source.parent.mkdir(parents=True)
    original_source.write_bytes(subprocess.check_output(
        ['git', 'show', ORIGINAL_OBSERVER_REF + ':DevUtils/tests/world/tickets/VerifyTicketStorage.java'], cwd=ROOT))
    result['original_observer_ref'] = ORIGINAL_OBSERVER_REF
    result['original_observer_sha256'] = sha(original_source)
    for side in ('frozen-original', 'frozen', 'control', 'current'):
        target = output / 'driver' / side
        target.mkdir(parents=True)
        source = original_source if side == 'frozen-original' else DRIVER
        run([str(jdk / 'javac'), '-g', '-cp', classpaths[side], '-d', str(target), str(source)], f'compile-driver-{side}.log')
        command = [str(jdk / 'java'), '-Xms1g', '-Xmx1g', '-XX:+UseG1GC', '--enable-native-access=ALL-UNNAMED',
                   '-Dmattmc.rust.natives.dir=' + str(native_dirs[side]), '-cp', str(target) + os.pathsep + classpaths[side],
                   'net.minecraft.world.level.VerifyTicketStorage', side, str(output / f'{side}-trace.txt')]
        commands[side] = command
        (output / f'{side}-command.json').write_text(json.dumps(command, indent=2) + '\n')
        data = run(command, f'{side}-run.log')
        trace = re.search(r'TRACE (\w+) (\d+)', data)
        assert trace, f'{side}: no trace'
        result[side] = {'trace': trace.group(1), 'ticks': int(trace.group(2)),
                        'classes': dict(re.findall(r'CLASS (\S+) (\w+)', data))}
    for capacity in filter(None, args.batch_capacities.split(',')):
        side = f'current-batch{capacity}'
        command = list(commands['current'])
        command.insert(1, f'-Dmattmc.ticketStorage.batch={int(capacity)}')
        command[-1] = str(output / f'{side}-trace.txt')
        (output / f'{side}-command.json').write_text(json.dumps(command, indent=2) + '\n')
        data = run(command, f'{side}-run.log')
        trace = re.search(r'TRACE (\w+) (\d+)', data)
        assert trace, f'{side}: no trace'
        result[side] = {'trace': trace.group(1), 'ticks': int(trace.group(2))}
    result['original_frozen_fixture_equivalence'] = result['frozen-original']['trace'] == result['frozen']['trace']
    result['control_is_complete_runtime'] = True
    sides = ['frozen-original', 'frozen', 'control', 'current'] + [side for side in result if side.startswith('current-batch')]
    result['traces_equal'] = len({result[side]['trace'] for side in sides}) == 1
    if not result['traces_equal']:
        for side in sides[1:]:
            for line, (a, b) in enumerate(zip((output / 'frozen-trace.txt').open(), (output / f'{side}-trace.txt').open())):
                if a != b:
                    result.setdefault('first_difference', {})[side] = {'line': line, 'frozen': a.strip(), side: b.strip()}
                    break
    if args.benchmark:
        for mode in ('benchmark', 'churn'):
            timings = {side: [] for side in commands}
            checksums = {side: set() for side in commands}
            for round_index in range(3):
                for side in ('frozen-original', 'frozen', 'control', 'current'):
                    data = run(commands[side] + [mode, '15', '5'], f'{mode}-{round_index}-{side}.log')
                    timings[side] += [int(ns) for ns in re.findall(r'TIMING \d+ (\d+)', data)]
                    checksums[side].update(re.findall(r'CHECKSUM (-?\d+)', data))
            result[mode] = {side: {'samples': len(values), 'median_ns_per_run': statistics.median(values),
                                   'min_ns': min(values), 'max_ns': max(values)} for side, values in timings.items()}
            result[mode + '_checksums_equal'] = len(set.union(*checksums.values())) == 1
        result['churn_workload'] = ('custom-listener path: the S0006 baseline churn, 2000 ticks of 40 forced/unknown adds, '
                                    '100-ticket removal batches and a Java removeTicketIf predicate; Java listeners')
        result['benchmark_workload'] = ('production-listener path: 1500 server-shaped ticks per run: 12 random portal/ender-pearl/spawn-search/forced/dragon/unknown '
                                        'tickets, untimed removals, purgeStaleTickets against holder readiness, both real trackers '
                                        'updated with production listeners; 5 warmup runs then 10 samples per JVM; 3 alternating rounds')
    verify_runtime(pre_runtime)
    assert candidate_patch_sha() == result['candidate_diff_sha256'], 'Production sources changed during verification'
    assert current_runtime_sha() == result['candidate_runtime_sha256'], 'Production classes/resources changed during verification'
    assert sha(DRIVER) == result['driver'], 'Observer changed during verification'
    assert all(sha(native_dirs[side] / native.name) == expected for side, expected in native_hashes.items()), 'Native image changed during verification'
    result['passed'] = result['traces_equal']
    result['performance_acceptance'] = False
    (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({key: value for key, value in result.items() if key not in ('frozen', 'control', 'current')}, indent=2))
    print('PASS' if result['passed'] else 'FAIL')
    raise SystemExit(0 if result['passed'] else 1)


if __name__ == '__main__':
    main()
