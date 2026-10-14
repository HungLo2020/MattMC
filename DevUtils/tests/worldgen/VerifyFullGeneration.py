#!/usr/bin/env python3
"""Generate fixed-seed FULL chunks in Frozen and Current servers and compare them.

Each side runs `net.minecraft.server.Main` in a fresh world under the output
directory with a small audit agent (`full_generation/`). Parity runs request
chunks serially on one worker and fingerprint every chunk's serialized save form
(sections, palettes, light, heightmaps, block entities, structures, ticks,
post-processing) plus generation entities. Timing runs (`--timing`) use normal
parallel scheduling without fingerprints.

  python3 DevUtils/tests/worldgen/VerifyFullGeneration.py --output build/full-generation/baseline
  python3 DevUtils/tests/worldgen/VerifyFullGeneration.py --output build/full-generation/t1 --timing --pairs 3
"""
import argparse
import fcntl
import gzip
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[3]
FROZEN = ROOT.parent / 'MattMC_JavaPerfTesting/MattMC'
FROZEN_HEAD = '7a4d181717dc0c7da9086f1a17687dfd522ce417'
AGENT = Path(__file__).resolve().with_name('full_generation')
NATIVE = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
JDK = ROOT / 'libraries/jdk/linux-x64/bin'


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def wait_for_memory(heap, log):
    """Hold a launch until the heap plus ZGC and native overhead fits in free memory."""
    need = int(heap.rstrip('Gg')) + 4
    while True:
        with open('/proc/meminfo') as stream:
            info = {line.split(':')[0]: int(line.split()[1]) for line in stream}
        free = info['MemAvailable'] // (1024 * 1024)
        if free >= need:
            return
        print(f'waiting for memory: {free} GiB available, {need} GiB needed', file=log, flush=True)
        time.sleep(30)


def run_logged(command, log, cwd, env):
    with log.open('w') as stream:
        status = subprocess.call(command, cwd=cwd, env=env, stdout=stream, stderr=subprocess.STDOUT)
    if status:
        raise SystemExit(f'{log.name} exit {status}; see {log}')
    return log.read_text()


def prepare(side, repo, out, env, skip_build):
    init = out / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('fullGenerationClasspath') "
                    "{ doLast { println('GEN_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")
    tasks = ['fullGenerationClasspath'] if skip_build else ['classes', 'fullGenerationClasspath', '-x', 'test']
    command = [str(repo / 'gradlew'), '-q', '-PmattmcRustProfile=release', '-I', str(init), *tasks]
    data = run_logged(command, out / f'{side}-classpath.log', repo, env)
    classpath = next(line.removeprefix('GEN_CP ') for line in data.splitlines() if line.startswith('GEN_CP '))
    classes = out / f'{side}-agent'
    classes.mkdir(exist_ok=True)
    run_logged([str(JDK / 'javac'), '-cp', classpath, '-d', str(classes), *map(str, sorted(AGENT.glob('*.java')))], out / f'{side}-javac.log', repo, env)
    manifest = out / f'{side}-MANIFEST.MF'
    manifest.write_text('Premain-Class: fullgeneration.FullGenAgent\n\n')
    jar = out / f'{side}-agent.jar'
    subprocess.run([str(JDK / 'jar'), 'cfm', str(jar), str(manifest), '-C', str(classes), '.'], check=True)
    return classpath, jar


def native_dir(side, args):
    return (args.reference_root if side == 'reference' else ROOT) / 'build/rust/native'


def launch(side, label, classpath, jar, out, args, env):
    run = out / label
    run.mkdir()
    world = run / 'server'
    world.mkdir()
    (world / 'eula.txt').write_text('eula=true\n')
    (world / 'server.properties').write_text(f'''level-seed={args.seed}
level-name=world
server-ip=127.0.0.1
server-port=0
online-mode=false
enforce-secure-profile=false
view-distance=2
simulation-distance=2
max-tick-time=-1
pause-when-empty-seconds=-1
sync-chunk-writes=false
generate-structures=true
spawn-protection=0
''')
    wait_for_memory(args.heap, sys.stderr)
    command = ['taskset', '-c', args.cpus, str(JDK / 'java'), '-Xms2G', f'-Xmx{args.heap}', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders',
               f'-Dmax.bg.threads={1 if not args.timing else args.workers}',
               f'-Daudit.output={run}', f'-Daudit.serial={str(not args.timing).lower()}', f'-Daudit.hash={str(not args.timing).lower()}',
               f'-Daudit.radius={args.radius}', f'-Daudit.rounds={args.rounds}', f'-Daudit.warmups={args.warmups}',
               f'-Daudit.dimensions={args.dimensions}', f'-Daudit.resume={str(args.resume).lower()}',
               f'-Daudit.biomes={args.biomes}', f'-Daudit.structures={args.structures}', f'-javaagent:{jar}']
    if side.startswith('current') or side == 'reference':
        command += ['--enable-native-access=ALL-UNNAMED', f'-Dmattmc.rust.natives.dir={native_dir(side, args)}']
    if side == 'current-java':
        # Same build with every native decoration route switched off: the pre-change Java path.
        command += ['-Dmattmc.worldgen.javaDecoration=true']
    command += [*args.jvm_arg, '-cp', classpath, 'net.minecraft.server.Main', '--nogui', '--port', '0']
    (run / 'command.json').write_text(json.dumps(command, indent=2))
    start = time.time()
    peak = 0
    with (run / 'server.log').open('w') as log:
        proc = subprocess.Popen(command, cwd=world, stdout=log, stderr=subprocess.STDOUT)
        try:
            while proc.poll() is None:
                try:
                    status = Path(f'/proc/{proc.pid}/status').read_text().splitlines()
                    peak = max(peak, next(int(x.split()[1]) * 1024 for x in status if x.startswith('VmRSS:')))
                except (FileNotFoundError, StopIteration):
                    pass
                if time.time() - start > args.timeout:
                    raise TimeoutError(f'{label} exceeded {args.timeout}s')
                time.sleep(1)
        finally:
            if proc.poll() is None:
                proc.terminate()
                try:
                    proc.wait(timeout=30)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait()
    log = (run / 'server.log').read_text(errors='replace')
    process = {'exit_code': proc.returncode, 'elapsed_seconds': time.time() - start, 'peak_rss_bytes': peak,
               'exceptions': sum(1 for line in log.splitlines() if 'Exception' in line or 'Error' in line and 'ERROR]' in line)}
    (run / 'process.json').write_text(json.dumps(process, indent=2))
    print(label, side, 'exit', proc.returncode, 'seconds', round(process['elapsed_seconds'], 1), 'peak_MiB', peak >> 20, flush=True)
    if not (run / 'complete.json').exists():
        raise SystemExit(f'{label} did not complete; see {run}/server.log')
    if args.remove_worlds:
        subprocess.run(['rm', '-rf', str(world / 'world')], check=True)
    return json.loads((run / 'regions.json').read_text()), process


def compare(frozen, current):
    report = {'regions': {}, 'identical': True}
    for a, b in zip(frozen, current):
        assert (a['dimension'], a['label']) == (b['dimension'], b['label'])
        if 'fingerprints' not in a:
            continue
        diffs = {}
        for chunk in sorted(set(a['fingerprints']) | set(b['fingerprints'])):
            fa, fb = a['fingerprints'].get(chunk, {}), b['fingerprints'].get(chunk, {})
            fields = [k for k in sorted(set(fa) | set(fb)) if fa.get(k) != fb.get(k)]
            if fields:
                diffs[chunk] = fields
        field_counts = {}
        for fields in diffs.values():
            for field in fields:
                key = field.split('/')[0] + ('/' + field.split('/')[-1] if field.startswith('sections/') else '')
                field_counts[key] = field_counts.get(key, 0) + 1
        report['regions'][a['dimension'] + '/' + a['label']] = {'chunks': len(b['fingerprints']), 'different_chunks': len(diffs),
                                                                 'field_counts': field_counts, 'different': diffs}
        report['identical'] &= not diffs
    return report


def timing_summary(sides, results):
    """Per dimension and side: medians over launches of summed measured regions."""
    import statistics
    summary = {}
    for slot, runs in results.items():
        per_dimension = {}
        for regions in runs:
            totals = {}
            for r in regions:
                if not r['label'].startswith('measure'):
                    continue
                t = totals.setdefault(r['dimension'], {'wall': 0.0, 'cpu': 0.0, 'features': 0.0})
                t['wall'] += r['wall_seconds']
                t['cpu'] += r['process_cpu_seconds']
                t['features'] += r['synchronous_task_ms'].get('generateFeatures', {}).get('ms', 0.0)
            for dimension, t in totals.items():
                per_dimension.setdefault(dimension, []).append(t)
        for dimension, launches in per_dimension.items():
            summary.setdefault(dimension, {})[sides[slot]] = {
                'runs': len(launches),
                'wall_seconds': [t['wall'] for t in launches],
                'features_ms': [t['features'] for t in launches],
                'wall_seconds_median': statistics.median(t['wall'] for t in launches),
                'features_ms_median': statistics.median(t['features'] for t in launches),
                'process_cpu_seconds_median': statistics.median(t['cpu'] for t in launches),
            }
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--seed', type=int, default=42)
    parser.add_argument('--radius', type=int, default=4)
    parser.add_argument('--rounds', type=int, default=1)
    parser.add_argument('--warmups', type=int, default=0)
    parser.add_argument('--dimensions', default='', help='comma-separated dimension IDs; default all')
    parser.add_argument('--sides', default='frozen,current',
                        help='frozen, current, current-java (native decoration off) or reference (--reference-root)')
    parser.add_argument('--reference-root', type=Path, help='another checkout, for example a pre-change worktree under build/')
    parser.add_argument('--timing', action='store_true', help='parallel generation without fingerprints')
    parser.add_argument('--resume', action='store_true', help='carve each region, save and unload it, then finish it to FULL from disk')
    parser.add_argument('--pairs', type=int, default=1, help='alternating Frozen/Current launches')
    parser.add_argument('--workers', type=int, default=4)
    parser.add_argument('--cpus', default='2-7')
    parser.add_argument('--timeout', type=int, default=3600)
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--remove-worlds', action='store_true', help='delete generated worlds after fingerprinting')
    parser.add_argument('--jvm-arg', action='append', default=[])
    parser.add_argument('--heap', default='8G', help='server -Xmx, the same on every side')
    parser.add_argument('--biomes', default='', help='comma-separated biome IDs: also measure a region around the nearest of each')
    parser.add_argument('--structures', default='', help='comma-separated structure IDs: also measure a region around the nearest start of each')
    parser.add_argument('--compare-to', type=Path, action='append', default=[],
                        help='earlier run directory (for example a pre-change Current side) to compare the first side against')
    args = parser.parse_args()
    out = args.output.resolve()
    if args.reference_root:
        args.reference_root = args.reference_root.resolve()
    if not out.is_relative_to(ROOT / 'build'):
        parser.error('output must be under build/')
    out.mkdir(parents=True, exist_ok=False)
    # One driver at a time: concurrent 8 GiB servers exhaust memory and the kernel kills them.
    lock = (ROOT / 'build/full-generation/.driver.lock').open('w')
    try:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        print('another VerifyFullGeneration run holds the lock; waiting', flush=True)
        fcntl.flock(lock, fcntl.LOCK_EX)
    sides = args.sides.split(',')
    if 'frozen' in sides:
        assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=FROZEN, text=True).strip() == FROZEN_HEAD
        assert not subprocess.check_output(['git', 'status', '--porcelain', '--', 'src', 'build.gradle'], cwd=FROZEN)
    env = {**os.environ, 'JAVA_HOME': str(JDK.parent), 'PATH': str(JDK) + os.pathsep + os.environ.get('PATH', '')}
    builds = {side.split('-')[0] for side in sides}
    roots = {'current': ROOT, 'frozen': FROZEN, 'reference': args.reference_root}
    if 'reference' in builds and not args.reference_root:
        parser.error('--sides reference needs --reference-root')
    prepared = {side: prepare(side, roots[side], out, env, args.skip_build) for side in builds}
    prepared = {side: prepared[side.split('-')[0]] for side in sides}
    receipt = {'args': {k: str(v) for k, v in vars(args).items()},
               'current_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
               'current_dirty': bool(subprocess.check_output(['git', 'status', '--porcelain', '--', 'src'], cwd=ROOT)),
               'native_sha256': sha(NATIVE) if any(s.startswith('current') for s in sides) else None, 'frozen_head': FROZEN_HEAD,
               'reference_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=args.reference_root, text=True).strip()
               if args.reference_root else None, 'runs': []}
    results = {}
    for pair in range(args.pairs):
        order = list(enumerate(sides)) if pair % 2 == 0 else list(reversed(list(enumerate(sides))))
        for slot, side in order:
            label = f'{side}-{pair}' if sides.count(side) == 1 else f'{side}{slot}-{pair}'
            regions, process = launch(side, label, *prepared[side], out, args, env)
            results.setdefault(slot, []).append(regions)
            receipt['runs'].append({'label': label, 'process': process,
                                    'regions': [{k: v for k, v in r.items() if k != 'fingerprints'} for r in regions]})
            (out / 'receipt.json').write_text(json.dumps(receipt, indent=2))
    if not args.timing and len(sides) == 2:
        report = compare(results[0][0], results[1][0])
        (out / 'comparison.json').write_text(json.dumps(report, indent=2))
        for name, region in report['regions'].items():
            print(name, 'chunks', region['chunks'], 'different', region['different_chunks'], region['field_counts'])
        print('FULL parity identical:', report['identical'])
    for reference in [] if args.timing else args.compare_to:
        report = compare(json.loads((reference / 'regions.json').read_text()), results[0][0])
        name = 'comparison-' + reference.parent.name + '-' + reference.name + '.json'
        (out / name).write_text(json.dumps(report, indent=2))
        for region_name, region in report['regions'].items():
            print('vs', reference, region_name, 'different', region['different_chunks'], region['field_counts'])
        print('FULL parity identical with', reference, ':', report['identical'])
    if args.timing:
        summary = timing_summary(sides, results)
        (out / 'timing-summary.json').write_text(json.dumps(summary, indent=2))
        for dimension, rows in summary.items():
            for side, row in rows.items():
                print(f"{dimension:28s} {side:13s} wall {row['wall_seconds_median']:7.3f}s  features {row['features_ms_median']:9.1f}ms  "
                      f"cpu {row['process_cpu_seconds_median']:7.2f}s  runs {row['runs']}")


if __name__ == '__main__':
    main()
