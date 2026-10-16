#!/usr/bin/env python3
"""Run the existing strict holder observer on actual Frozen/pre/candidate images.

The pre side uses a complete retained runtime, not old sources over new owners.
This is a correctness regression; it performs no performance acceptance.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

SOURCES = Path(__file__).resolve().parent
sys.path.insert(0, str(SOURCES.parent))
import VerifyFullGeneration as full
import VerifyEndFeatures as end
import VerifyChunkGeneration as probe

ROOT = full.ROOT
DRIVER = ROOT / 'DevUtils/tests/world/chunkholders/VerifyChunkHolders.java'


def readbacks(text, seeds, mode):
    readers = re.findall(r'READER seed (\d+) reads (\d+) inconsistent (\d+)', text)
    expected = set(range(1, seeds + 1))
    if len(readers) != seeds or {int(seed) for seed, _, _ in readers} != expected:
        raise ValueError('missing or duplicate holder reader receipts')
    if any(int(reads) == 0 or int(bad) != 0 for _, reads, bad in readers):
        raise ValueError('holder reader was vacuous or inconsistent')
    coverage = re.findall(r'COVERAGE seed (\d+) kept-holders-outside-updating (\d+)', text)
    if len(coverage) != seeds or {int(seed) for seed, _ in coverage} != expected:
        raise ValueError('missing or duplicate retained-holder coverage receipts')
    # Exact mode includes a deterministic retained-holder scenario in every
    # seed. Hash mode exercises ordinary seeded churn; some seeds have none.
    if sum(int(count) for _, count in coverage) == 0 or mode == 'exact' and any(int(count) == 0 for _, count in coverage):
        raise ValueError('retained-holder scenario was vacuous')
    oracle = re.findall(r'ORACLE seed (\d+) passes (\d+) holders (\d+) mismatches (\d+)', text)
    if mode == 'hash':
        if len(oracle) != seeds or {int(seed) for seed, _, _, _ in oracle} != expected:
            raise ValueError('missing or duplicate hash-order oracle receipts')
        if any(int(passes) == 0 or int(holders) == 0 or int(bad) != 0 for _, passes, holders, bad in oracle):
            raise ValueError('hash-order oracle was vacuous or inconsistent')
    return {'reader_observations': sum(int(reads) for _, reads, _ in readers),
            'reader_inconsistencies': 0, 'retained_holder_observations': sum(int(count) for _, count in coverage),
            'oracle_final_cumulative': oracle[-1] if oracle else None}


def run(command, out, env, native, steps, seeds, mode):
    out.parent.mkdir(parents=True, exist_ok=True)
    out.with_suffix('.command.json').write_text(json.dumps(command, indent=2) + '\n')
    mappings = {}
    start = time.monotonic()
    with out.open('w') as log:
        proc = subprocess.Popen(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            while proc.poll() is None:
                try:
                    for line in Path(f'/proc/{proc.pid}/maps').read_text().splitlines():
                        if 'mattmc' in line and '.so' in line:
                            path = line.split()[-1]
                            if path not in mappings and Path(path).is_file(): mappings[path] = full.sha(Path(path))
                except (FileNotFoundError, ProcessLookupError): pass
                if time.monotonic() - start > 900: raise TimeoutError('holder observer timed out')
                time.sleep(.2)
        finally:
            if proc.poll() is None: proc.kill(); proc.wait()
    out.with_suffix('.process.json').write_text(json.dumps({'exit': proc.returncode, 'native_mappings': mappings,
                                                          'elapsed_seconds': time.monotonic() - start}, indent=2) + '\n')
    if proc.returncode != 0: raise RuntimeError('holder observer failed: ' + str(out))
    if native and full.sha(native / 'mattmc_rust-linux-x64.so') not in mappings.values(): raise RuntimeError('expected native image not mapped')
    text = out.read_text()
    match = re.search(r'TRACE (\w+) (\d+)', text)
    if not match: raise RuntimeError('missing strict trace: ' + str(out))
    if int(match[2]) != steps * seeds: raise RuntimeError('wrong holder step count')
    oracle = re.findall(r'ORACLE seed \d+ passes (\d+) holders (\d+) mismatches (\d+)', text)
    if oracle and (int(oracle[-1][0]) == 0 or int(oracle[-1][2]) != 0): raise RuntimeError('hash-order oracle failed')
    return {'digest': match[1], 'steps': int(match[2]), 'oracle': oracle[-1] if oracle else None,
            'readbacks': readbacks(text, seeds, mode)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--pre-runtime', type=Path, required=True)
    parser.add_argument('--test-classpath-file', type=Path, required=True)
    parser.add_argument('--steps', type=int, default=2000)
    parser.add_argument('--seeds', type=int, default=8)
    parser.add_argument('--reuse-baselines', type=Path,
                        help='revalidate retained original/Frozen/pre receipts and run only the affected Current image')
    args = parser.parse_args()
    args.output = args.output.resolve(); args.pre_runtime = args.pre_runtime.resolve()
    if not args.output.is_relative_to(ROOT / 'build'): raise ValueError('output must be under build/')
    args.output.mkdir(parents=True, exist_ok=False)
    if subprocess.check_output(['git', '-C', str(probe.FROZEN), 'rev-parse', 'HEAD'], text=True).strip() != probe.PIN:
        raise ValueError('Frozen pin changed')
    if subprocess.check_output(['git', '-C', str(probe.FROZEN), 'status', '--porcelain'], text=True).strip(): raise ValueError('Frozen dirty')
    pre = probe.verify_pre(args.pre_runtime)
    env = os.environ.copy(); env['JAVA_HOME'] = str(full.JDK.parent); env['PATH'] = str(full.JDK) + os.pathsep + env['PATH']
    for key in ['JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS']: env.pop(key, None)
    current = probe.classpath(ROOT, args.output, env, label='current')
    frozen = None if args.reuse_baselines else probe.classpath(probe.FROZEN, args.output, env, label='frozen')
    # Common observer-only dependencies, including Mockito. Never substitute a
    # current production class/resource/dependency for the actual pre runtime.
    extras = [part for part in args.test_classpath_file.read_text().strip().split(os.pathsep) if part not in current.split(os.pathsep)]
    classpaths = {'current': current} if args.reuse_baselines else {
        'current': current, 'frozen': frozen, 'pre': (args.pre_runtime / 'classpath.txt').read_text().strip()}
    original = args.output / 'original-source/net/minecraft/server/level/VerifyChunkHolders.java'
    original.parent.mkdir(parents=True)
    original.write_bytes(subprocess.check_output(['git', 'show', pre['source_head'] + ':DevUtils/tests/world/chunkholders/VerifyChunkHolders.java']))
    rows = {}
    reused = None
    if args.reuse_baselines:
        retained = args.reuse_baselines.resolve()
        previous = json.loads((retained / 'result.json').read_text())
        if not previous.get('passed') or previous['before'] != previous['after']:
            raise ValueError('retained campaign did not pass its input seals')
        if previous['pre_head'] != pre['source_head'] or previous['before']['frozen'] != end.inputs(probe.FROZEN):
            raise ValueError('retained reference runtime identity changed')
        old_source = retained / 'VerifyChunkHolders.executed.java'
        if full.sha(old_source) != previous['before']['observer'] or full.sha(original) != previous['before']['original_observer']:
            raise ValueError('retained original/adapted observer source mismatch')
        # Only the approved native setup call changed. Reject any alteration to
        # random inputs, original-reflection setup or public observations.
        changes = {
            'getDeclaredMethod("bump", ChunkStatus.class)': 'getDeclaredMethod("bump", ChunkStatus.class, ChunkStatus.class)',
            'for (ChunkStatus status : ChunkStatus.getStatusList()) bump.invoke(owner, status);':
                'for (ChunkStatus status : ChunkStatus.getStatusList()) bump.invoke(owner, status == ChunkStatus.EMPTY ? null : status.getParent(), status);',
        }
        expected = old_source.read_text()
        for old, new in changes.items():
            if expected.count(old) != 1: raise ValueError('unrecognized retained fixture adaptation')
            expected = expected.replace(old, new)
        if DRIVER.read_text() != expected: raise ValueError('observer changes exceed the audited native setup signature')
        receipts = {}
        for label, row in previous['rows'].items():
            if label.startswith('current-'): continue
            log = retained / (label + '.log'); text = log.read_text()
            match = re.search(r'TRACE (\w+) (\d+)', text)
            if not match or match[1] != row['digest'] or int(match[2]) != args.steps * args.seeds:
                raise ValueError('retained strict trace or step count changed')
            receipt = readbacks(text, args.seeds, label.rsplit('-', 1)[-1])
            rows[label] = {**row, 'readbacks': receipt, 'reused': True}
            receipts[label] = full.sha(log)
        if len(rows) != 8: raise ValueError('expected all eight original/adapted Frozen/pre receipts')
        reused = {'directory': str(retained), 'result_sha256': full.sha(retained / 'result.json'),
                  'observer_sha256': full.sha(old_source), 'log_hashes': receipts,
                  'impact': 'Only the two native bump setup expressions changed; original branch, random inputs and observations match retained source exactly.'}
    adapted = args.output / 'VerifyChunkHolders.executed.java'
    shutil.copy2(DRIVER, adapted)
    prepared = {}
    for side, main_cp in classpaths.items():
        cp = main_cp + os.pathsep + os.pathsep.join(extras)
        for version in ['adapted', 'original'] if side != 'current' else ['adapted']:
            classes = args.output / (side + '-' + version + '-classes'); classes.mkdir()
            source = DRIVER if version == 'adapted' else original
            with (args.output / (side + '-' + version + '-javac.log')).open('w') as log:
                subprocess.run([str(full.JDK / 'javac'), '-nowarn', '-cp', cp, '-d', str(classes), str(source)], stdout=log, stderr=subprocess.STDOUT, check=True)
            prepared[side, version] = str(classes) + os.pathsep + cp
    before = {'current': end.inputs(ROOT), 'frozen': end.inputs(probe.FROZEN), 'observer': full.sha(DRIVER), 'original_observer': full.sha(original)}
    for (side, version), cp in prepared.items():
        native = None if side == 'frozen' else args.pre_runtime / 'native' if side == 'pre' else ROOT / 'build/rust/native'
        for mode in ['exact', 'hash']:
            label = side + '-' + version + '-' + mode
            command = [str(full.JDK / 'java'), '-Xms1g', '-Xmx1g', '-XX:+UseG1GC', '--enable-native-access=ALL-UNNAMED',
                       '-XX:+EnableDynamicAgentLoading', '-Dnet.bytebuddy.experimental=true', '-Dholders.steps=' + str(args.steps), '-Dholders.seeds=' + str(args.seeds)]
            if native: command += ['-Dmattmc.rust.natives.dir=' + str(native)]
            if mode == 'exact': command += ['-XX:+UnlockExperimentalVMOptions', '-XX:hashCode=2']
            trace = args.output / (label + '-trace.txt')
            command += ['-cp', cp, 'net.minecraft.server.level.VerifyChunkHolders', side, str(trace)]
            if mode == 'hash': command += ['hash']
            rows[label] = run(command, args.output / (label + '.log'), env, native, args.steps, args.seeds, mode)
            if mode == 'hash' and not rows[label]['oracle']: raise RuntimeError('hash observer was vacuous')
            if version == 'adapted':
                expected = 'native-generation-owner' if side == 'current' else 'original-reflection'
                if 'SETUP_CONTROL passed adapter=' + expected not in (args.output / (label + '.log')).read_text():
                    raise RuntimeError('setup adapter control missing or wrong route')
            print(label, rows[label], flush=True)
    after = {'current': end.inputs(ROOT), 'frozen': end.inputs(probe.FROZEN), 'observer': full.sha(DRIVER), 'original_observer': full.sha(original)}
    probe.verify_pre(args.pre_runtime)
    if reused:
        retained = args.reuse_baselines.resolve()
        if reused['result_sha256'] != full.sha(retained / 'result.json') or any(
                full.sha(retained / (label + '.log')) != expected for label, expected in reused['log_hashes'].items()):
            raise RuntimeError('retained receipts changed during candidate run')
    if before != after: raise RuntimeError('production or observer images changed')
    for mode in ['exact', 'hash']:
        if len({row['digest'] for label, row in rows.items() if label.endswith('-' + mode)}) != 1:
            raise RuntimeError('strict holder trace differs: ' + mode)
    (args.output / 'result.json').write_text(json.dumps({'passed': True, 'before': before, 'after': after,
        'pre_head': pre['source_head'], 'rows': rows, 'reused_baselines': reused}, indent=2) + '\n')
    print('Actual Frozen/pre/current registry composition and original/adapted fixture traces match.', flush=True)


if __name__ == '__main__': main()
