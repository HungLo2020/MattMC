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
# Files the native ticket-storage slice replaces; the control side compiles their
# pre-change sources over the candidate classpath.
CONTROL_SOURCES = ['world/level/TicketStorage.java', 'server/level/Ticket.java', 'server/level/TicketChunkDistance.java',
                   'server/level/LoadingChunkTracker.java', 'server/level/SimulationChunkTracker.java']


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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--control-ref', required=True, help='pre-change Current commit for the control side')
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
    control_ref = subprocess.check_output(['git', 'rev-parse', args.control_ref + '^{commit}'], cwd=ROOT, text=True).strip()
    result = {'frozen_head': frozen_head, 'control_ref': control_ref, 'native': sha(native), 'driver': sha(DRIVER),
              'candidate_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
              'candidate_diff_sha256': candidate_patch_sha(),
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
                   '-Dmattmc.rust.natives.dir=' + str(native.parent), '-cp', str(target) + os.pathsep + classpaths[side],
                   'net.minecraft.world.level.VerifyTicketStorage', side, str(output / f'{side}-trace.txt')]
        commands[side] = command
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
        data = run(command, f'{side}-run.log')
        trace = re.search(r'TRACE (\w+) (\d+)', data)
        assert trace, f'{side}: no trace'
        result[side] = {'trace': trace.group(1), 'ticks': int(trace.group(2))}
    # The control overlay must actually replace LevelChunkTicks.
    # Informational until the candidate changes TicketStorage.
    result['control_overlays_ticket_storage'] = result['control']['classes'].get('TicketStorage') != result['current']['classes'].get('TicketStorage')
    sides = ['frozen', 'control', 'current'] + [side for side in result if side.startswith('current-batch')]
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
                for side in ('frozen', 'control', 'current'):
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
    result['passed'] = result['traces_equal']
    (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({key: value for key, value in result.items() if key not in ('frozen', 'control', 'current')}, indent=2))
    print('PASS' if result['passed'] else 'FAIL')
    raise SystemExit(0 if result['passed'] else 1)


if __name__ == '__main__':
    main()
