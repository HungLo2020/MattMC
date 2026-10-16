#!/usr/bin/env python3
"""Early stock fresh/disk-loaded FULL and loaded-reuse probe, not final acceptance."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import statistics
import subprocess
import sys
import time

SOURCES = Path(__file__).resolve().parent
sys.path.insert(0, str(SOURCES.parent))
import VerifyFullGeneration as full
import VerifyEndFeatures as end

ROOT = full.ROOT
FROZEN = ROOT.parent / 'MattMC_JavaPerfTesting/MattMC'
PIN = '7a4d181717dc0c7da9086f1a17687dfd522ce417'


def logged(command, path, cwd, env):
    with path.open('w') as output:
        subprocess.run(command, cwd=cwd, env=env, stdout=output, stderr=subprocess.STDOUT, check=True)
    return path.read_text()


def classpath(repo, out, env, build=False, label=None):
    label = label or repo.name
    init = out / (label + '-classpath.gradle')
    init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('generationProbeClasspath') "
                    "{ doLast { println('GEN_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")
    command = [str(repo / 'gradlew'), '-q', '-PmattmcRustProfile=release', '-I', str(init)]
    command += ['classes', 'generationProbeClasspath'] if build else ['generationProbeClasspath']
    result = logged(command, out / (label + '-classpath.log'), repo, env)
    return next(line.removeprefix('GEN_CP ') for line in result.splitlines() if line.startswith('GEN_CP '))


def verify_pre(path):
    manifest = json.loads((path / 'manifest.json').read_text())
    if not manifest.get('complete_classpath'):
        raise ValueError('pre-change comparison requires a complete sealed runtime')
    for relative, expected in manifest['files'].items():
        if full.sha(path / relative) != expected:
            raise ValueError('pre-change artifact changed: ' + relative)
    for name, expected in manifest.get('dependency_jars', {}).items():
        if full.sha(Path(name)) != expected:
            raise ValueError('pre-change dependency changed: ' + name)
    return manifest


def verify_foundation(path):
    manifest = json.loads((path / 'manifest.json').read_text())
    for relative, expected in manifest['files'].items():
        if full.sha(path / relative) != expected: raise ValueError('retained foundation changed: ' + relative)
    prefix = 'net/minecraft/server/level/NativeChunkGeneration'
    classes = ROOT / 'build/classes/java/main'
    for name, expected in manifest['all_main_classes'].items():
        if not name.startswith(prefix) and full.sha(classes / name) != expected:
            raise ValueError('foundation overlay has changed downstream classes: ' + name)
    allowed = {'src/main/java/net/minecraft/server/level/NativeChunkGeneration.java'}
    allowed |= {'src/main/rust/world/level/chunk_generation/' + name for name in ['mod.rs', 'ffi.rs', 'tests.rs']}
    for name, expected in manifest['all_production_sources'].items():
        if name not in allowed and full.sha(ROOT / name) != expected:
            raise ValueError('foundation overlay has changed downstream source: ' + name)
    return manifest


def agent(side, cp, out, env):
    classes = out / (side + '-agent')
    classes.mkdir()
    sources = [SOURCES / name for name in ['GenerationAgent.java', 'GenerationAudit.java']]
    sources += [SOURCES.parent / 'full_generation/FullGenAudit.java']
    logged([str(full.JDK / 'javac'), '-cp', cp, '-d', str(classes), *map(str, sources)],
           out / (side + '-agent-javac.log'), ROOT, env)
    manifest = out / (side + '-MANIFEST.MF')
    manifest.write_text('Premain-Class: fullgeneration.GenerationAgent\n\n')
    jar = out / (side + '-agent.jar')
    subprocess.run([str(full.JDK / 'jar'), 'cfm', str(jar), str(manifest), '-C', str(classes), '.'], check=True)
    return jar


def launch(side, phase, cp, jar, args, env, previous=None):
    out = args.output / (side + '-' + phase)
    out.mkdir()
    server = out / 'server'
    server.mkdir()
    (server / 'eula.txt').write_text('eula=true\n')
    (server / 'server.properties').write_text('''level-seed=42
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
''' + getattr(args, 'extra_server_properties', ''))
    full.wait_for_memory(args.heap, sys.stderr)
    command = ['taskset', '-c', args.cpus, str(full.JDK / 'java'), '-Xms1G', '-Xmx' + args.heap,
               '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '-Dmax.bg.threads=1',
               '-Daudit.output=' + str(out), '-Dgeneration.dimensions=' + args.dimensions,
               '-Dgeneration.reopen=' + str(previous is not None).lower(),
               '-Dgeneration.orderedFeatures=' + str(args.ordered_features).lower(),
               '-Dgeneration.samples=' + str(args.samples), '-Dgeneration.iterations=' + str(args.iterations),
               '-javaagent:' + str(jar)]
    command += getattr(args, 'extra_jvm_args', [])
    native = args.pre_runtime / 'native' if side == 'pre' else args.foundation_runtime / 'native' if side == 'foundation' else ROOT / 'build/rust/native'
    if side != 'frozen':
        command += ['--enable-native-access=ALL-UNNAMED', '-Dmattmc.rust.natives.dir=' + str(native)]
    command += ['-cp', cp, 'net.minecraft.server.Main', '--nogui', '--port', '0']
    if previous is not None:
        command += ['--universe', str(previous / 'server')]
    (out / 'command.json').write_text(json.dumps(command, indent=2) + '\n')
    mappings = {}
    peak_rss = 0
    peak_swap = 0
    def swap_counters():
        return {key: int(value) for key, value in (line.split() for line in Path('/proc/vmstat').read_text().splitlines())
                if key in ('pswpin', 'pswpout')}
    swap_before = swap_counters()
    start = time.monotonic()
    with (out / 'server.log').open('w') as log:
        proc = subprocess.Popen(command, cwd=server, env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            while proc.poll() is None:
                try:
                    status = Path(f'/proc/{proc.pid}/status').read_text().splitlines()
                    peak_rss = max(peak_rss, next(int(line.split()[1]) * 1024 for line in status if line.startswith('VmHWM:')))
                    peak_swap = max(peak_swap, next(int(line.split()[1]) * 1024 for line in status if line.startswith('VmSwap:')))
                    for line in Path(f'/proc/{proc.pid}/maps').read_text().splitlines():
                        if 'mattmc' in line and '.so' in line:
                            path = line.split()[-1]
                            if path not in mappings and Path(path).is_file(): mappings[path] = full.sha(Path(path))
                except (FileNotFoundError, ProcessLookupError, StopIteration):
                    pass
                if time.monotonic() - start > args.timeout: raise TimeoutError('server timed out: ' + str(out))
                time.sleep(1)
        finally:
            if proc.poll() is None: proc.terminate(); proc.wait(timeout=30)
    (out / 'process.json').write_text(json.dumps({'exit': proc.returncode, 'elapsed_seconds': time.monotonic() - start,
                                                'peak_observed_rss_bytes': peak_rss, 'peak_observed_swap_bytes': peak_swap,
                                                'host_swap_before': swap_before, 'host_swap_after': swap_counters(),
                                                'native_mappings': mappings}, indent=2) + '\n')
    if proc.returncode != 0 or not (out / 'complete.json').exists():
        raise RuntimeError('probe incomplete: ' + str(out / 'server.log'))
    if side != 'frozen' and full.sha(native / 'mattmc_rust-linux-x64.so') not in mappings.values():
        raise RuntimeError('expected native image was not observed mapped: ' + str(out))
    result = json.loads((out / 'complete.json').read_text())
    if side in ('current', 'foundation'):
        for row in result['rows']:
            if row['native_after'][4] <= row['native_before'][4] or row['native_after'][5] <= row['native_before'][5]:
                raise RuntimeError('native generation did not advance during stock request: ' + row['dimension'])
    print(out.name, 'complete', round(time.monotonic() - start, 1), 's', flush=True)
    return out, result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--pre-runtime', type=Path, required=True)
    parser.add_argument('--foundation-runtime', type=Path, help='retained NativeChunkGeneration class/library pair, other main classes must be unchanged')
    parser.add_argument('--sides', default='frozen,pre,current')
    parser.add_argument('--dimensions', default='minecraft:the_end')
    parser.add_argument('--ordered-features', action='store_true',
                        help='time an ordered FEATURES margin before FULL, matching the deterministic all-dimension observer')
    parser.add_argument('--cpus', default='4-7')
    parser.add_argument('--heap', default='6G')
    parser.add_argument('--samples', type=int, default=9)
    parser.add_argument('--iterations', type=int, default=2500)
    parser.add_argument('--timeout', type=int, default=600)
    parser.add_argument('--skip-build', action='store_true')
    args = parser.parse_args()
    args.output = args.output.resolve(); args.pre_runtime = args.pre_runtime.resolve()
    if args.foundation_runtime: args.foundation_runtime = args.foundation_runtime.resolve()
    if not args.output.is_relative_to(ROOT / 'build'): raise ValueError('output must be under build/')
    args.output.mkdir(parents=True, exist_ok=False)
    sides = args.sides.split(',')
    if not set(sides) <= {'frozen', 'pre', 'foundation', 'current'}: raise ValueError('unknown side')
    if 'foundation' in sides and not args.foundation_runtime: raise ValueError('foundation side requires --foundation-runtime')
    if subprocess.check_output(['git', '-C', str(FROZEN), 'rev-parse', 'HEAD'], text=True).strip() != PIN:
        raise ValueError('Frozen reference pin changed')
    if subprocess.check_output(['git', '-C', str(FROZEN), 'status', '--porcelain'], text=True).strip():
        raise ValueError('Frozen checkout must be clean')
    lock = ROOT / 'build/full-generation/.driver.lock'; lock.parent.mkdir(parents=True, exist_ok=True)
    with lock.open('a') as guard:
        fcntl.flock(guard, fcntl.LOCK_EX)
        env = os.environ.copy(); env['JAVA_HOME'] = str(full.JDK.parent); env['PATH'] = str(full.JDK) + os.pathsep + env['PATH']
        pre = verify_pre(args.pre_runtime)
        foundation = verify_foundation(args.foundation_runtime) if 'foundation' in sides else None
        prepared = {}
        for side in sides:
            cp = (args.pre_runtime / 'classpath.txt').read_text().strip() if side == 'pre' else classpath(FROZEN if side == 'frozen' else ROOT, args.output, env, side == 'current' and not args.skip_build, side)
            if side == 'foundation': cp = str(args.foundation_runtime / 'classes') + os.pathsep + cp
            prepared[side] = cp, agent(side, cp, args.output, env)
        source_paths = sorted(SOURCES.glob('*.java')) + [SOURCES.parent / 'full_generation/FullGenAudit.java', Path(__file__).resolve()]
        audit = {str(path.relative_to(ROOT)): full.sha(path) for path in source_paths}
        before = {'current': end.inputs(ROOT), 'frozen': end.inputs(FROZEN), 'audit': audit}
        results = {}
        for side in sides:
            cp, jar = prepared[side]
            fresh, first = launch(side, 'fresh', cp, jar, args, env)
            _, second = launch(side, 'reopen', cp, jar, args, env, fresh)
            results[side] = {'fresh': first, 'reopen': second}
        after = {'current': end.inputs(ROOT), 'frozen': end.inputs(FROZEN),
                 'audit': {str(path.relative_to(ROOT)): full.sha(path) for path in source_paths}}
        verify_pre(args.pre_runtime)
        if foundation: verify_foundation(args.foundation_runtime)
        if before != after: raise RuntimeError('source, compiled runtime, native image or observer changed during probe')
        comparisons = []
        for phase in ['fresh', 'reopen']:
            for side in sides:
                for row in results[side][phase]['rows']:
                    if side == 'current' and 'pre' in results:
                        reference = next(r for r in results['pre'][phase]['rows'] if r['dimension'] == row['dimension'])
                        if reference['future_aliases'] != row['future_aliases']:
                            raise RuntimeError('pre/current future alias shape differs: ' + phase + ' ' + row['dimension'])
                        if reference['fingerprints'] != row['fingerprints']:
                            raise RuntimeError('pre/current FULL output differs: ' + phase + ' ' + row['dimension'])
                    if side != 'frozen' and 'frozen' in results:
                        reference = next(r for r in results['frozen'][phase]['rows'] if r['dimension'] == row['dimension'])
                        if reference['future_aliases'] != row['future_aliases']:
                            raise RuntimeError('Frozen future alias shape differs: ' + phase + ' ' + row['dimension'])
                        identical = reference['fingerprints'] == row['fingerprints']
                        retained_difference = False
                        if not identical:
                            if row['dimension'] == 'minecraft:primordial_caves' and 'pre' in results:
                                pre_row = next(r for r in results['pre'][phase]['rows'] if r['dimension'] == row['dimension'])
                                retained_difference = pre_row['fingerprints'] == row['fingerprints']
                            if not retained_difference:
                                raise RuntimeError('new or unestablished Frozen FULL output difference: ' + phase + ' ' + row['dimension'])
                        comparisons.append({'side': side, 'phase': phase, 'dimension': row['dimension'], 'fingerprints_identical': identical,
                            'retained_pre_change_difference': retained_difference,
                            'field_differences': {key: {'frozen': reference['fingerprints'].get(key), 'side': row['fingerprints'].get(key)}
                                for key in sorted(reference['fingerprints'].keys() | row['fingerprints'].keys())
                                if reference['fingerprints'].get(key) != row['fingerprints'].get(key)},
                            'loaded_reuse_median_ratio_frozen': statistics.median(row['loaded_reuse_ns_per_request']) / statistics.median(reference['loaded_reuse_ns_per_request']),
                            'hot_holder_quiet_samples': sum(sample['compiler_ms'] == 0 for sample in row['hot_holder_read'])})
        (args.output / 'result.json').write_text(json.dumps({'probe_complete': True, 'acceptance_campaign': False,
            'before': before, 'after': after, 'pre_head': pre['source_head'], 'comparisons': comparisons,
            'ordered_features': args.ordered_features,
            'retained_foundation': {'head': foundation['head'], 'native': foundation['native']} if foundation else None,
            'limits': 'One small region/process per side; first-request latency and short loaded samples are diagnostic, not final performance acceptance.'}, indent=2) + '\n')
        print('Connected probe complete; final lifecycle/performance acceptance remains pending.', flush=True)


if __name__ == '__main__': main()
