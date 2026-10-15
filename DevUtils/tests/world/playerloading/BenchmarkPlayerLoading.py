#!/usr/bin/env python3
"""Matched policy/dispatch/readiness workloads; keep builds and servers stopped.

Measures identical Java driver code against Frozen, actual pre-change sources
and Current. Quiet samples exclude measured JIT work but retain their raw data.
Construction/bootstrap are reported separately. This is not worldgen/IO/FPS.
"""
import argparse
import json
import os
from pathlib import Path
import re
import statistics
import subprocess

from VerifyPlayerLoading import CONTROL_SOURCES, FROZEN, FROZEN_HEAD, ROOT, sha, source_identity

DRIVER = Path(__file__).with_suffix('.java')


def matched_work(row):
    """Compare complete ordered work vectors, including deterministic phases.

    A reused fixture may alternate states. Requiring one checksum globally
    wrongly rejects an identical periodic workload on all three backends.
    Never sort or normalize phase order to conceal a backend difference.
    """
    sides = ('frozen', 'control', 'current')
    primary = [tuple((s['jvm_pair'], s['index'], s['checksum'], s['adds'], s['removals'], s['attachments'])
                     for s in row[side]['samples']) for side in sides]
    latency = [tuple((s['jvm_pair'], s['checksum'], s['dispatch_count'], s['main_count'])
                     for s in row[side]['latency_diagnostics']) for side in sides]
    return primary[0] == primary[1] == primary[2] and latency[0] == latency[1] == latency[2]


def host_memory_pressure():
    # Observations only; no system/cgroup settings are changed on the server.
    vm = dict(line.split() for line in Path('/proc/vmstat').read_text().splitlines())
    return {'swap_pages_in': int(vm['pswpin']), 'swap_pages_out': int(vm['pswpout']),
            'major_faults': int(vm['pgmajfault']),
            'memory_pressure': Path('/proc/pressure/memory').read_text().strip()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--frozen-classpath-file', type=Path, required=True)
    parser.add_argument('--current-classpath-file', type=Path, required=True)
    parser.add_argument('--control-ref', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--native-profile', choices=['dev', 'release'], default='release')
    parser.add_argument('--workloads', default='walk,teleport,views,readiness,disconnect')
    parser.add_argument('--pairs', type=int, default=3)
    parser.add_argument('--warmups', type=int, default=32,
                        help='minimum complete observation cycles; also require12 compiler-quiet cycles, bounded at512')
    parser.add_argument('--samples', type=int, default=10)
    parser.add_argument('--steps', type=int, default=32)
    parser.add_argument('--cpu', type=int)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    jdk = args.java_home.resolve() / 'bin'
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    built = ROOT / 'build/rust/target' / ('release' if args.native_profile == 'release' else 'debug') / 'libmattmc_rust.so'
    assert sha(native) == sha(built), 'Declared native profile does not match loaded artifact'
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=FROZEN, text=True).strip() == FROZEN_HEAD
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=FROZEN)
    base = subprocess.check_output(['git', 'rev-parse', args.control_ref + '^{commit}'], cwd=ROOT, text=True).strip()
    result = {'status': 'incomplete', 'frozen_head': FROZEN_HEAD, 'control_ref': base,
              'candidate_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
              'candidate_source_sha256': source_identity(), 'native_sha256': sha(native),
              'driver_sha256': sha(DRIVER), 'native_profile': args.native_profile,
              'configuration': vars(args).copy(), 'load_average': os.getloadavg(),
              'host_memory_start': host_memory_pressure(),
              'scope': 'Real DistanceManager, storage and dispatcher with stepped executors and controlled holder futures; '
                       'no trace/mock interception in timed windows. Not generation, IO, networking or FPS.', 'workloads': {}}
    result['configuration'] = {key: str(value) if isinstance(value, Path) else value for key, value in result['configuration'].items()}
    env = os.environ.copy()
    for name in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        env.pop(name, None)
    env.update(JAVA_HOME=str(jdk.parent), PATH=str(jdk) + os.pathsep + env.get('PATH', ''))
    classpaths = {'frozen': args.frozen_classpath_file.read_text().strip(),
                 'current': args.current_classpath_file.read_text().strip()}

    def run(command, name):
        with (output / name).open('w') as log:
            code = subprocess.call(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
        if code:
            raise RuntimeError(f'{name}: exit {code}')
        return (output / name).read_text()

    def save():
        (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')

    try:
        control = output / 'control-classes'
        control.mkdir()
        sources = []
        for name in CONTROL_SOURCES:
            target = output / 'control-source' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(subprocess.check_output(['git', 'show', f'{base}:src/main/java/net/minecraft/{name}'], cwd=ROOT))
            sources.append(str(target))
        run([str(jdk / 'javac'), '-nowarn', '-cp', classpaths['current'], '-d', str(control), *sources], 'compile-control.log')
        classpaths['control'] = str(control) + os.pathsep + classpaths['current']
        commands = {}
        for side in ('frozen', 'control', 'current'):
            classes = output / (side + '-driver')
            classes.mkdir()
            run([str(jdk / 'javac'), '-nowarn', '-cp', classpaths[side], '-d', str(classes), str(DRIVER)], 'compile-' + side + '.log')
            command = [str(jdk / 'java'), '-Xms1g', '-Xmx2g', '-XX:+UseG1GC', '-Xbatch', '-XX:-TieredCompilation',
                       '--enable-native-access=ALL-UNNAMED', '-Dmattmc.rust.natives.dir=' + str(native.parent),
                       '-cp', str(classes) + os.pathsep + classpaths[side], 'net.minecraft.server.level.BenchmarkPlayerLoading']
            if args.cpu is not None:
                command = ['taskset', '-c', str(args.cpu), *command]
            commands[side] = command
        result['commands'] = commands
        for workload in args.workloads.split(','):
            assert workload in ('walk', 'teleport', 'views', 'readiness', 'disconnect'), workload
            row = result['workloads'][workload] = {side: {'samples': [], 'cold': [], 'constructor': [],
                'first_feedback': [], 'first_checksums': [], 'warmup': [], 'latency_diagnostics': [], 'settled_memory': []} for side in commands}
            for pair in range(args.pairs):
                order = ['frozen', 'control', 'current']
                order = order[pair % 3:] + order[:pair % 3]
                for side in order:
                    command = commands[side] + [workload, str(args.warmups), str(args.samples), str(args.steps)]
                    data = run(command, f'{workload}-{pair}-{side}.log')
                    cold = re.search(r'COLD (\d+) CONSTRUCT (\d+)', data)
                    assert cold, (side, workload, 'no cold timing')
                    row[side]['cold'].append(int(cold[1]))
                    row[side]['constructor'].append(int(cold[2]))
                    first = re.search(r'FIRST_FEEDBACK (\d+) CHECKSUM (-?\d+)', data)
                    assert first, (side, workload, 'no first-use feedback timing')
                    row[side]['first_feedback'].append(int(first[1]))
                    row[side]['first_checksums'].append(int(first[2]))
                    warmed = re.search(r'WARMUP_COMPLETE CYCLES (\d+) QUIET_STREAK (\d+)', data)
                    assert warmed and int(warmed[2]) >= 12, (side, workload, 'observation loop did not settle')
                    row[side]['warmup'].append({'jvm_pair': pair, 'cycles': int(warmed[1]), 'quiet_streak': int(warmed[2])})
                    samples = re.findall(r'TIMING (\d+) (\d+) CHECKSUM (-?\d+) COMPILE_MS (\d+) GC_MS (\d+) ADDS (\d+) REMOVALS (\d+) ATTACHMENTS (\d+) ALLOCATED_BYTES (-?\d+) POLICY_NATIVE_BYTES (\d+) RSS_BYTES (-?\d+) SWAP_BYTES (-?\d+) HEAP_USED_BYTES (\d+)', data)
                    assert len(samples) == args.samples, (side, workload, len(samples))
                    for sample in samples:
                        measured = dict(zip(
                            ('index', 'ns', 'checksum', 'compile_ms', 'gc_ms', 'adds', 'removals', 'attachments',
                             'allocated_bytes', 'policy_native_bytes', 'rss_bytes', 'swap_bytes', 'heap_used_bytes'), map(int, sample)))
                        measured['jvm_pair'] = pair
                        assert all(measured[name] > 0 for name in ('adds', 'removals', 'attachments')), \
                            (side, workload, 'vacuous feedback workload', measured)
                        row[side]['samples'].append(measured)
                    latency = re.search(r'LATENCY CHECKSUM (-?\d+) ELAPSED_NS (\d+) DISPATCH_COUNT (\d+) DISPATCH_P50_NS (\d+) DISPATCH_P95_NS (\d+) DISPATCH_MAX_NS (\d+) MAIN_COUNT (\d+) MAIN_P50_NS (\d+) MAIN_P95_NS (\d+) MAIN_MAX_NS (\d+)', data)
                    settled = re.search(r'SETTLED GC_COLLECTIONS (\d+) RSS_BYTES (-?\d+) SWAP_BYTES (-?\d+) HEAP_USED_BYTES (\d+) POLICY_NATIVE_BYTES (\d+)', data)
                    assert latency and settled, (side, workload, 'missing separate latency/settled observations')
                    diagnostic = dict(zip(('checksum', 'elapsed_ns', 'dispatch_count', 'dispatch_p50_ns',
                        'dispatch_p95_ns', 'dispatch_max_ns', 'main_count', 'main_p50_ns', 'main_p95_ns',
                        'main_max_ns'), map(int, latency.groups())))
                    diagnostic['jvm_pair'] = pair
                    assert diagnostic['dispatch_count'] > 0 and diagnostic['main_count'] > 0, \
                        (side, workload, 'vacuous latency observation')
                    row[side]['latency_diagnostics'].append(diagnostic)
                    memory = dict(zip(('gc_collections', 'rss_bytes', 'swap_bytes', 'heap_used_bytes',
                                       'policy_native_bytes'), map(int, settled.groups())))
                    memory['jvm_pair'] = pair
                    row[side]['settled_memory'].append(memory)
                    save()
            row['equal_work'] = matched_work(row)
            row['equal_cold_work'] = len({checksum for side in commands for checksum in row[side]['first_checksums']}) == 1
            row['summary'] = {}
            for side in commands:
                quiet = [sample['ns'] for sample in row[side]['samples'] if sample['compile_ms'] == 0]
                per_jvm = []
                for pair in range(args.pairs):
                    values = [sample['ns'] for sample in row[side]['samples']
                              if sample['jvm_pair'] == pair and sample['compile_ms'] == 0]
                    per_jvm.append({'pair': pair, 'quiet_samples': len(values),
                                    'median_ns': statistics.median(values) if values else None})
                medians = [jvm['median_ns'] for jvm in per_jvm if jvm['median_ns'] is not None]
                row['summary'][side] = {'quiet_samples': len(quiet), 'all_samples': len(row[side]['samples']),
                    # Give each alternating fresh JVM equal weight; a noisy or
                    # under-warmed invocation must not disappear into a pool.
                    'median_ns': statistics.median(medians) if len(medians) == args.pairs else None,
                    'pooled_median_ns': statistics.median(quiet) if quiet else None, 'per_jvm': per_jvm,
                    'min_ns': min(quiet) if quiet else None, 'max_ns': max(quiet) if quiet else None,
                    'cold_median_ns': statistics.median(row[side]['cold']),
                    'constructor_median_ns': statistics.median(row[side]['constructor'])}
                row['summary'][side]['first_feedback_median_ns'] = statistics.median(row[side]['first_feedback'])
                row['summary'][side]['constructor_and_first_feedback_median_ns'] = statistics.median(
                    constructed + feedback for constructed, feedback
                    in zip(row[side]['constructor'], row[side]['first_feedback']))
                row['summary'][side].update({
                    'median_java_thread_allocated_bytes': statistics.median(sample['allocated_bytes'] for sample in row[side]['samples']),
                    'max_policy_native_capacity_bytes': max(sample['policy_native_bytes'] for sample in row[side]['samples']),
                    'max_process_rss_bytes': max(sample['rss_bytes'] for sample in row[side]['samples']),
                    'max_process_swap_bytes': max(sample['swap_bytes'] for sample in row[side]['samples']),
                    'min_policy_native_capacity_bytes': min(sample['policy_native_bytes'] for sample in row[side]['samples']),
                    'max_settled_rss_bytes': max(sample['rss_bytes'] for sample in row[side]['settled_memory']),
                    'max_settled_heap_bytes': max(sample['heap_used_bytes'] for sample in row[side]['settled_memory']),
                    'max_settled_policy_native_bytes': max(sample['policy_native_bytes'] for sample in row[side]['settled_memory']),
                    'diagnostic_queue_tasks_per_second': statistics.median(
                        (sample['dispatch_count'] + sample['main_count']) * 1e9 / sample['elapsed_ns']
                        for sample in row[side]['latency_diagnostics']),
                })
                if row['summary'][side]['median_ns']:
                    row['summary'][side]['feedback_cycles_per_second'] = 1e9 / row['summary'][side]['median_ns']
            enough = all(jvm['quiet_samples'] >= min(args.samples, 8)
                         for side in commands for jvm in row['summary'][side]['per_jvm'])
            row['enough_compiler_quiet_samples'] = enough
            row['settled_gc_observed'] = all(sample['gc_collections'] > 0
                for side in commands for sample in row[side]['settled_memory'])
            if enough:
                ratio = row['summary']['current']['median_ns'] / row['summary']['frozen']['median_ns']
                row['ratio_vs_frozen'] = ratio
                row['paired_ratios_vs_frozen'] = [
                    row['summary']['current']['per_jvm'][pair]['median_ns']
                    / row['summary']['frozen']['per_jvm'][pair]['median_ns'] for pair in range(args.pairs)]
                row['within_general_10_percent'] = ratio <= 1.10
                row['within_boundary_25_percent'] = ratio <= 1.25
            print(workload, json.dumps(row['summary']), 'ratio', row.get('ratio_vs_frozen'), 'equal_work', row['equal_work'], flush=True)
            save()
        result['inputs_unchanged'] = source_identity() == result['candidate_source_sha256'] and sha(native) == result['native_sha256']
        rows = list(result['workloads'].values())
        valid = result['inputs_unchanged'] and all(row['equal_work'] and row['equal_cold_work'] and row['enough_compiler_quiet_samples']
                                                 and row['settled_gc_observed'] for row in rows)
        result['status'] = 'measured' if valid else 'inconclusive'
        result['all_within_general_10_percent'] = valid and all(row.get('within_general_10_percent') for row in rows)
        result['all_within_boundary_25_percent'] = valid and all(row.get('within_boundary_25_percent') for row in rows)
    except Exception as error:
        result['status'] = 'failed'
        result['error'] = f'{type(error).__name__}: {error}'
    result['host_memory_end'] = host_memory_pressure()
    result['load_average_end'] = os.getloadavg()
    save()
    print(json.dumps({key: value for key, value in result.items() if key not in ('commands', 'workloads')}, indent=2))
    return 0 if result['status'] == 'measured' else 1


if __name__ == '__main__':
    raise SystemExit(main())
