#!/usr/bin/env python3
"""Compare actual End feature methods on Frozen, unchanged Current Java and Rust.

Runs real dedicated servers, using isolated proto-chunk neighborhoods so spawn
preparation cannot contaminate the fixture. No Frozen source is rewritten. The
reference, Java and native fixture rows must all match; missing output fails.
"""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import statistics
import shutil

import VerifyFullGeneration as full

ROOT = full.ROOT
SOURCES = Path(__file__).with_name('end_features')


def command(argv, cwd=ROOT):
    return subprocess.check_output(argv, cwd=cwd, text=True).strip()


def build(side, repo, out, env, skip):
    init = out / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('endFeatureClasspath') "
                    "{ doLast { println('END_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")
    tasks = ['endFeatureClasspath'] if skip else ['classes', 'endFeatureClasspath', '-x', 'test']
    log = full.run_logged([str(repo / 'gradlew'), '-q', '-PmattmcRustProfile=release', '-I', str(init), *tasks],
                          out / f'{side}-build.log', repo, env)
    cp = next(row.removeprefix('END_CP ') for row in log.splitlines() if row.startswith('END_CP '))
    classes = out / f'{side}-agent'
    classes.mkdir()
    full.run_logged([str(full.JDK / 'javac'), '-cp', cp, '-d', str(classes), *map(str, sorted(SOURCES.glob('*.java')))],
                    out / f'{side}-javac.log', repo, env)
    manifest = out / f'{side}-MANIFEST.MF'
    manifest.write_text('Premain-Class: endfeatures.EndFeatureAgent\n\n')
    jar = out / f'{side}-agent.jar'
    subprocess.run([str(full.JDK / 'jar'), 'cfm', str(jar), str(manifest), '-C', str(classes), '.'], check=True)
    return cp, jar


def launch(side, label, cp, jar, out, args, env, previous=None):
    run = out / label
    run.mkdir()
    server = run / 'server'
    server.mkdir()
    (server / 'eula.txt').write_text('eula=true\n')
    (server / 'server.properties').write_text(f"""level-seed={args.seed}
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
""")
    full.wait_for_memory(args.heap, sys.stderr)
    argv = ['taskset', '-c', args.cpus, str(full.JDK / 'java'), '-Xms1G', f'-Xmx{args.heap}', '-XX:+UseZGC',
            '-XX:+UseCompactObjectHeaders', '-Dmax.bg.threads=1', f'-Daudit.output={run}',
            '-Dend.native=' + str(side in ('current', 'reference')).lower(), '-Dend.reopen=' + str(previous is not None).lower(), f'-javaagent:{jar}', '-Dend.timing=' + str(args.timing).lower(),
            f'-Dend.warmup={args.warmup}', f'-Dend.samples={args.samples}', f'-Dend.iterations={args.iterations}']
    argv += ['-Dend.freshWrites=' + str(args.fresh_writes).lower()]
    argv += [f'-Dend.warmupSeconds={args.warmup_seconds}', f'-Dend.compilerQuietMs={args.compiler_quiet_ms}']
    argv += [f'-Dend.workloads={args.workloads}', '-Dend.profileBoundary=' + str(args.profile_boundary).lower()]
    if side != 'frozen':
        argv += ['--enable-native-access=ALL-UNNAMED', f'-Dmattmc.rust.natives.dir={(args.reference_artifacts / "native") if side == "reference" else ROOT / "build/rust/native"}']
    if side == 'current-java':
        argv += ['-Dmattmc.worldgen.javaDecoration=true']
    if side == 'reference':
        manifest = json.loads((args.reference_artifacts / 'manifest.json').read_text())
        cp = (args.reference_artifacts / 'classpath.txt').read_text().strip() if manifest.get('complete_classpath') else str(args.reference_artifacts / 'classes') + os.pathsep + cp
    argv += ['-cp', cp, 'net.minecraft.server.Main', '--nogui', '--port', '0']
    if previous is not None:
        argv += ['--universe', str(previous / 'server')]
    (run / 'command.json').write_text(json.dumps(argv, indent=2))
    started = time.monotonic()
    peak = 0
    mappings = {}
    with (run / 'server.log').open('w') as log:
        proc = subprocess.Popen(argv, cwd=server, env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            while proc.poll() is None:
                try:
                    status = Path(f'/proc/{proc.pid}/status').read_text().splitlines()
                    peak = max(peak, next(int(line.split()[1]) * 1024 for line in status if line.startswith('VmRSS:')))
                    for line in Path(f'/proc/{proc.pid}/maps').read_text().splitlines():
                        if 'mattmc' in line and '.so' in line and len(line.split()) >= 6:
                            path = line.split()[-1]
                            if path not in mappings and Path(path).is_file():
                                mappings[path] = full.sha(Path(path))
                except (FileNotFoundError, ProcessLookupError, StopIteration):
                    pass
                if time.monotonic() - started > args.timeout:
                    raise TimeoutError(f'{side} exceeded {args.timeout}s')
                time.sleep(1)
        finally:
            if proc.poll() is None:
                proc.terminate()
                try:
                    proc.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait()
    process = {'exit_code': proc.returncode, 'elapsed_seconds': time.monotonic() - started,
               'peak_rss_bytes': peak, 'native_mappings': mappings}
    (run / 'process.json').write_text(json.dumps(process, indent=2))
    if proc.returncode != 0 or not (run / 'complete.json').exists():
        raise SystemExit(f'{label} did not complete successfully; see {run / "server.log"}')
    print(label, 'exit', proc.returncode, 'seconds', round(process['elapsed_seconds'], 1), flush=True)
    return run


def inputs(repo):
    paths = [p for p in (repo / 'src/main/rust').rglob('*.rs') if 'target' not in p.parts]
    paths += list((repo / 'src/main/java').rglob('*.java'))
    digest = hashlib.sha256()
    for path in sorted(paths):
        digest.update(str(path.relative_to(repo)).encode())
        digest.update(path.read_bytes())
    native = repo / 'build/rust/native/mattmc_rust-linux-x64.so'
    classes = hashlib.sha256()
    for path in sorted((repo / 'build/classes/java/main').rglob('*.class')):
        classes.update(str(path.relative_to(repo)).encode()); classes.update(path.read_bytes())
    return {'head': command(['git', 'rev-parse', 'HEAD'], repo),
            'dirty': command(['git', 'status', '--porcelain=v1'], repo),
            'source_sha256': digest.hexdigest(),
            'classes_sha256': classes.hexdigest(),
            'native_sha256': full.sha(native) if native.exists() else None}


def verify_native_image(run, side, args):
    if side == 'frozen':
        return
    image = (args.reference_artifacts / 'native/mattmc_rust-linux-x64.so') if side == 'reference' else full.NATIVE
    actual = json.loads((run / 'process.json').read_text())['native_mappings']
    if full.sha(image) not in actual.values():
        raise SystemExit('expected native image was not observed mapped by ' + side)


def reference_inputs(root):
    manifest = json.loads((root / 'manifest.json').read_text())
    for relative, expected in manifest['files'].items():
        if full.sha(root / relative) != expected:
            raise SystemExit('reference artifact changed: ' + relative)
    if manifest.get('complete_classpath'):
        for path, expected in manifest.get('dependencies', {}).items():
            if full.sha(Path(path)) != expected:
                raise SystemExit('reference dependency changed: ' + path)
        return manifest
    # This is a targeted overlay of the changed End classes, not a general
    # repository snapshot. Every other Java production dependency must still
    # match its recorded source base.
    allowed = {'src/main/java/net/minecraft/world/level/levelgen/' + p for p in (
        'NativeDecoration.java', 'NativeDecorationEncoder.java', 'NativeEndFeatures.java', 'NativeTopMaterial.java', 'feature/SpikeFeature.java',
        'feature/EndGatewayFeature.java', 'feature/EndPlatformFeature.java')}
    changed = command(['git', 'diff', '--name-only', manifest['source_head'], '--', 'src/main/java']).splitlines()
    untracked = command(['git', 'ls-files', '--others', '--exclude-standard', '--', 'src/main/java']).splitlines()
    if set(changed + untracked) - allowed:
        raise SystemExit('reference overlay has changed Java dependencies; build a new isolated reference')
    return manifest


def timing(sides, prepared, args, env):
    if args.rounds < 3 or args.samples < 5 or args.iterations < 1 or args.warmup < 1:
        raise SystemExit('timing requires >=3 rounds, >=5 samples and positive iterations/warmup')
    runs = []
    selected = args.workloads.split(',') if args.workloads else ['worldgen-0', 'worldgen-1', 'worldgen-2', 'live-0', 'live-1', 'live-2', 'live-3']
    expected_entries = {0} if 'worldgen-0' in selected else set()
    expected_entries.update(min(int(name[-1]), 2) for name in selected if name.startswith('live-'))
    for repeat in range(args.rounds):
        order = sides if repeat % 2 == 0 else list(reversed(sides))
        for side in order:
            cp, jar = prepared['frozen' if side == 'frozen' else 'current']
            run = launch(side, f'{side}-{repeat}', cp, jar, args.output, args, env)
            verify_native_image(run, side, args)
            rows = json.loads((run / 'timings.json').read_text())
            receipt = json.loads((run / 'complete.json').read_text())
            if side == 'current' and (len(receipt.get('nativeEntries', [])) != 3 or not all(receipt['nativeEntries'][i] > 0 for i in expected_entries)):
                raise SystemExit('timing did not exercise the selected native End entrypoints')
            runs.append({'side': side, 'round': repeat, 'rows': rows})
    reference = runs[0]['rows']
    for run in runs:
        for a, b in zip(reference, run['rows'], strict=True):
            if (a['name'], a['state'], a['nextRandom']) != (b['name'], b['state'], b['nextRandom']):
                raise SystemExit(f"timing workload/result mismatch in {run['side']} round {run['round']} {a['name']}")
    summary = {}
    for index, row in enumerate(reference):
        values = {side: [r['rows'][index] for r in runs if r['side'] == side] for side in sides}
        medians = {side: statistics.median([v for r in rs for v in r['samples_ns']]) for side, rs in values.items()}
        paired = []
        if 'current' in values and 'frozen' in values:
            paired = [statistics.median(c['samples_ns']) / statistics.median(f['samples_ns'])
                      for c, f in zip(values['current'], values['frozen'], strict=True)]
        boundary = row['name'].startswith('live-') or row['name'] == 'worldgen-0'
        limit = 1.25 if boundary else 1.10
        summary[row['name']] = {'median_ns': medians, 'candidate_frozen_paired_ratios': paired,
                                'limit': limit, 'all_rounds_within_limit': bool(paired) and all(v <= limit for v in paired),
                                'cold_ns': {side: [r['cold_ns'] for r in rs] for side, rs in values.items()}}
    result = {'workloads_identical': True, 'rounds': args.rounds, 'workloads': summary,
              'fresh_writes': args.fresh_writes,
              'instrumented_diagnostic': args.profile_boundary,
              'all_within_limits': None if args.profile_boundary else all(r['all_rounds_within_limit'] for r in summary.values())}
    (args.output / 'timing-summary.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(result, indent=2))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--sides', default='frozen,current-java,current')
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--reference-artifacts', type=Path)
    parser.add_argument('--cpus', default=','.join(map(str, sorted(os.sched_getaffinity(0)))))
    parser.add_argument('--heap', default='4G')
    parser.add_argument('--timeout', type=int, default=600)
    parser.add_argument('--seed', type=int, default=123456789)
    parser.add_argument('--timing', action='store_true')
    parser.add_argument('--fresh-writes', action='store_true',
                        help='reset spike/gateway blocks to end stone outside each timed operation; retain steady-state runs separately')
    parser.add_argument('--rounds', type=int, default=3)
    parser.add_argument('--warmup', type=int, default=1000)
    parser.add_argument('--samples', type=int, default=15)
    parser.add_argument('--iterations', type=int, default=50)
    parser.add_argument('--warmup-seconds', type=float, default=3.0,
                        help='minimum warmup time per workload, in addition to the operation count')
    parser.add_argument('--compiler-quiet-ms', type=int, default=500,
                        help='require this quiet compilation interval before collecting samples')
    parser.add_argument('--workloads', default='', help='comma-separated workload names; default all seven')
    parser.add_argument('--profile-boundary', action='store_true',
                        help='instrument generation refresh/effect phases; diagnostic only, never a budget pass')
    args = parser.parse_args()
    if args.warmup_seconds < 0 or args.compiler_quiet_ms < 0:
        parser.error('warmup seconds and compiler quiet interval must be nonnegative')
    allowed_workloads = {'worldgen-0', 'worldgen-1', 'worldgen-2', 'live-0', 'live-1', 'live-2', 'live-3'}
    if args.workloads and (len(args.workloads.split(',')) != len(set(args.workloads.split(','))) or set(args.workloads.split(',')) - allowed_workloads):
        parser.error('workloads must be unique known names: ' + ','.join(sorted(allowed_workloads)))
    if (args.workloads or args.profile_boundary) and not args.timing:
        parser.error('workload selection and boundary profiling require --timing')
    args.output = args.output.resolve()
    if not args.output.is_relative_to((ROOT / 'build').resolve()):
        parser.error('--output must be under build/')
    if args.output.exists():
        parser.error('--output must be a new directory')
    sides = args.sides.split(',')
    if len(sides) != len(set(sides)) or any(s not in ('frozen', 'current-java', 'current', 'reference') for s in sides):
        parser.error('sides must be unique frozen,current-java,current,reference values')
    if 'reference' in sides:
        if args.reference_artifacts is None:
            parser.error('reference side requires --reference-artifacts')
        args.reference_artifacts = args.reference_artifacts.resolve()
        reference_inputs(args.reference_artifacts)
    if 'frozen' in sides:
        if command(['git', 'rev-parse', 'HEAD'], full.FROZEN) != full.FROZEN_HEAD:
            raise SystemExit('Frozen HEAD does not match the pinned oracle')
        if command(['git', 'status', '--porcelain=v1', '--', 'src/main/java'], full.FROZEN):
            raise SystemExit('Frozen Java sources are dirty')
    env = os.environ.copy()
    env['JAVA_HOME'] = str(full.JDK.parent)
    env['PATH'] = str(full.JDK) + os.pathsep + env['PATH']
    args.output.mkdir(parents=True)
    lock_path = ROOT / 'build/full-generation/.driver.lock'
    lock_path.parent.mkdir(exist_ok=True)
    with lock_path.open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        source_files = sorted(SOURCES.glob('*.java')) + [Path(__file__).resolve()]
        source_blobs = {p: p.read_bytes() for p in source_files}
        for path, blob in source_blobs.items():
            target = args.output / 'audit-source' / path.relative_to(ROOT)
            target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(blob)
        prepared = {}
        for side in sides:
            key = 'frozen' if side == 'frozen' else 'current'
            if key not in prepared:
                prepared[key] = build(key, full.FROZEN if key == 'frozen' else ROOT, args.output, env, args.skip_build)
        if any(path.read_bytes() != blob for path, blob in source_blobs.items()):
            raise SystemExit('audit sources changed while building the observer')
        before = {s: reference_inputs(args.reference_artifacts) if s == 'reference' else inputs(full.FROZEN if s == 'frozen' else ROOT) for s in sides}
        audit_inputs = {str(p.relative_to(ROOT)): full.sha(p) for p in sorted(SOURCES.glob('*.java'))}
        audit_inputs[str(Path(__file__).resolve().relative_to(ROOT))] = full.sha(Path(__file__))
        audit_inputs.update({str(jar): full.sha(jar) for _, jar in prepared.values()})
        def seal_audit():
            if any(full.sha(ROOT / path) != expected for path, expected in audit_inputs.items()):
                raise SystemExit('audit source or compiled observer changed during the campaign')
        (args.output / 'audit-inputs.json').write_text(json.dumps(audit_inputs, indent=2))
        (args.output / 'inputs-before.json').write_text(json.dumps(before, indent=2))
        if args.timing:
            result = timing(sides, prepared, args, env)
            after = {s: reference_inputs(args.reference_artifacts) if s == 'reference' else inputs(full.FROZEN if s == 'frozen' else ROOT) for s in sides}
            (args.output / 'inputs-after.json').write_text(json.dumps(after, indent=2))
            seal_audit()
            if before != after:
                raise SystemExit('source/native inputs changed during timing')
            if not args.profile_boundary and not result['all_within_limits']:
                raise SystemExit(1)
            return
        rows, transactions = {}, {}
        for side in sides:
            cp, jar = prepared['frozen' if side == 'frozen' else 'current']
            run = launch(side, side, cp, jar, args.output, args, env)
            verify_native_image(run, side, args)
            process = json.loads((run / 'process.json').read_text())
            receipt = json.loads((run / 'complete.json').read_text())
            if process['exit_code'] != 0 or not receipt.get('success') or receipt.get('fixtures', 0) < 27:
                raise SystemExit(f'{side} did not pass its completion gate')
            if side == 'current' and (len(receipt.get('nativeEntries', [])) != 3 or not all(n > 0 for n in receipt['nativeEntries'])):
                raise SystemExit('correctness fixtures did not exercise every native End entrypoint')
            rows[side] = json.loads((run / 'fixtures.json').read_text())
            if side != 'frozen':
                transactions[side] = json.loads((run / 'transactions.json').read_text())
                if len(transactions[side]) != (2 if side in ('current', 'reference') else 1):
                    raise SystemExit('missing End rollback/retry coverage: ' + side)
            reopened = launch(side, side + '-reopen', cp, jar, args.output, args, env, previous=run)
            saved = json.loads((run / 'saved-live.json').read_text())
            actual = json.loads((reopened / 'reopened.json').read_text())
            if saved != actual:
                (args.output / f'{side}-reopen-difference.json').write_text(json.dumps({'saved': saved, 'reopened': actual}, indent=2))
                raise SystemExit(f'{side} live state changed across save/reopen')
        after = {s: reference_inputs(args.reference_artifacts) if s == 'reference' else inputs(full.FROZEN if s == 'frozen' else ROOT) for s in sides}
        (args.output / 'inputs-after.json').write_text(json.dumps(after, indent=2))
        seal_audit()
        if before != after:
            raise SystemExit('source/native inputs changed during the run')
        reference = rows[sides[0]]
        # Native storage writes bypass Java's setBlock; the state comparison
        # covers that route. Keep full write tapes for Java/direct-effect runs.
        def comparable(row, side):
            native_storage = side in ('current', 'reference') and row['name'].startswith(('gateway-', 'platform-'))
            value = {k: v for k, v in row.items() if k != 'writes' or not native_storage}
            if side in ('current', 'reference') and row['name'].startswith(('spike-', 'default-spike-')):
                # Shared geometry is checked by final storage/palette state and
                # the direct effect-sink order tests. Keep crystal creation and
                # its two real Java writes observable across the storage route.
                value['writes'] = [entry for entry in row['writes'] if entry.startswith('entity:') or entry.endswith(':BlockPos')]
            return value
        differences = {s: [a['name'] for a, b in zip(reference, r, strict=True) if comparable(a, s) != comparable(b, s)]
                       for s, r in rows.items()}
        result = {'identical': all(not d for d in differences.values()), 'differences': differences,
                  'fixtures': len(reference), 'sides': sides, 'inputs_unchanged': True,
                  'transaction_effect_failure_identical': len({json.dumps(r[0], sort_keys=True) for r in transactions.values()}) <= 1}
        result['identical'] &= result['transaction_effect_failure_identical']
        (args.output / 'comparison.json').write_text(json.dumps(result, indent=2))
        print(json.dumps(result, indent=2))
        if not result['identical']:
            raise SystemExit(1)


if __name__ == '__main__':
    main()
