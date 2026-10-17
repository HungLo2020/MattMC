#!/usr/bin/env python3
"""Inject a decoration failure and check nothing past it is published.

For each route (native: the Nth native block write fails mid-placement; Java:
the Nth `WorldGenRegion.setBlock` throws), a Current server generates one
overworld region serially until the failure, times out on the stuck chunk and
saves while halting. A second launch on the same world reads every persisted
chunk status around the region without generating anything.

Accepts when, on both routes, the failure surfaces as the "Feature placement"
crash for one decorating chunk, that chunk is persisted below FEATURES, and every
chunk the failed step could write is, after its rollback, identical in saved
form to what it held just before the step.

  python3 DevUtils/tests/worldgen/VerifyGenerationFailure.py --output build/generation-failure/<label>
"""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parent))
import VerifyFullGeneration as full  # noqa: E402

FEATURES_OR_LATER = {'minecraft:features', 'minecraft:initialize_light', 'minecraft:light', 'minecraft:spawn', 'minecraft:full'}


def run_server(label, classpath, jar, out, args, env, properties, world=None):
    """One Current server; returns its run directory. `world` reuses a saved world."""
    run = out / label
    run.mkdir()
    server = run / 'server'
    if world is None:
        server.mkdir()
        (server / 'eula.txt').write_text('eula=true\n')
        (server / 'server.properties').write_text(f'level-seed={args.seed}\nlevel-name=world\nserver-ip=127.0.0.1\nserver-port=0\n'
                                                  'online-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=-1\n'
                                                  'pause-when-empty-seconds=-1\nsync-chunk-writes=true\ngenerate-structures=true\n')
    else:
        shutil.copytree(world, server)
    full.wait_for_memory('8G', sys.stderr)
    command = ['taskset', '-c', args.cpus, str(full.JDK / 'java'), '-Xms2G', '-Xmx8G', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders',
               '-Dmax.bg.threads=1', f'-Daudit.output={run}', '-Daudit.serial=true', '-Daudit.hash=false',
               f'-Daudit.radius={args.radius}', '-Daudit.rounds=1', '-Daudit.dimensions=minecraft:overworld',
               '--enable-native-access=ALL-UNNAMED', f'-Dmattmc.rust.natives.dir={full.NATIVE.parent}',
               *[f'-D{key}={value}' for key, value in properties.items()], f'-javaagent:{jar}',
               '-cp', classpath, 'net.minecraft.server.Main', '--nogui', '--port', '0']
    (run / 'command.json').write_text(json.dumps(command, indent=2))
    start = time.time()
    with (run / 'server.log').open('w') as log:
        proc = subprocess.Popen(command, cwd=server, stdout=log, stderr=subprocess.STDOUT)
        try:
            proc.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            proc.terminate()
            try:
                proc.wait(timeout=30)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait()
    print(label, 'exit', proc.returncode, 'seconds', round(time.time() - start, 1), flush=True)
    return run


def failure_report(log):
    """The crash's cause and decorating chunk, from the logged "Feature placement" report."""
    text = log.read_text(errors='replace')
    found = 'Description: Feature placement' in text or 'ReportedException: Feature placement' in text
    centre = re.search(r'CenterX: (-?\d+)\s+CenterZ: (-?\d+)', text)
    cause = re.search(r'Caused by: ([\w.$]+: [^\n]+)', text)
    return {'feature_placement': found, 'cause': cause.group(1) if cause else None,
            'chunk': f'{centre.group(1)},{centre.group(2)}' if centre else None}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--seed', type=int, default=42)
    parser.add_argument('--radius', type=int, default=2)
    parser.add_argument('--writes', type=int, default=20000, help='writes before the injected failure')
    parser.add_argument('--at-chunk', type=int, default=-1,
                        help='arm the failure just before this chunk of the ordered decoration pass (row order over the region and margin)')
    parser.add_argument('--cpus', default='2-7')
    parser.add_argument('--timeout', type=int, default=900)
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--no-rollback', action='store_true',
                        help='control: leave failed writes in place; the rollback check must then report changed chunks')
    parser.add_argument('--no-transaction', action='store_true',
                        help='control: no FEATURES transaction at all (no capture, ownership locks or deferred effects), as before it existed')
    args = parser.parse_args()
    out = args.output.resolve()
    if not out.is_relative_to(full.ROOT / 'build'):
        parser.error('output must be under build/')
    out.mkdir(parents=True, exist_ok=False)
    env = {**full.os.environ, 'JAVA_HOME': str(full.JDK.parent), 'PATH': str(full.JDK) + full.os.pathsep + full.os.environ.get('PATH', '')}
    classpath, jar = full.prepare('current', full.ROOT, out, env, args.skip_build)
    results = {}
    routes = (('native', f'fail:{args.writes}', 'fail:-1', {}),
              # Java's route: every feature through WorldGenRegion.setBlock, as before native decoration.
              ('java', f'javafail:{args.writes}', 'javafail:-1', {'mattmc.worldgen.javaDecoration': 'true'}))
    for route, spec, startup, extra in routes:
        failed = run_server(f'{route}-fail', classpath, jar, out, args, env, {
            'mattmc.worldgen.decorationTest': startup, 'audit.injectFailure': spec, 'audit.chunkTimeoutSeconds': 30,
            'audit.regionTimeoutSeconds': 60, 'audit.injectAtChunk': args.at_chunk,
            **({'mattmc.worldgen.decorationRollback': 'false'} if args.no_rollback else {}),
            **({'mattmc.worldgen.decorationTransaction': 'false'} if args.no_transaction else {}), **extra})
        report = failure_report(failed / 'server.log')
        inspected = run_server(f'{route}-inspect', classpath, jar, out, args, env, {'audit.inspect': 'true'}, world=failed / 'server')
        statuses = json.loads((inspected / 'statuses.json').read_text())['minecraft:overworld']
        memory = json.loads((failed / 'statuses-memory.json').read_text()) if (failed / 'statuses-memory.json').exists() else {}
        saved = (failed / 'saved.txt').exists()
        rollback = json.loads((failed / 'rollback.json').read_text()) if (failed / 'rollback.json').exists() else {}
        # A rollback stage that reported a failure fails the route, whatever was saved.
        rollback_failed = (failed / 'rollback-failed.txt').exists()
        restored = bool(rollback) and all(v == 'identical' for v in rollback.values()) and not rollback_failed
        chunk = report['chunk']
        published = statuses.get(chunk) in FEATURES_OR_LATER or memory.get(chunk) in FEATURES_OR_LATER if chunk else None
        counts = lambda values: {s: list(values).count(s) for s in sorted(set(values))}
        results[route] = {**report, 'saved_after_failure': saved, 'failed_chunk_memory': memory.get(chunk) if chunk else None,
                          'failed_chunk_persisted': statuses.get(chunk) if chunk else None,
                          'memory_counts': counts(memory.values()), 'persisted_counts': counts(statuses.values()),
                          'rolled_back_chunks': rollback, 'rollback_identical': restored, 'rollback_stage_failed': rollback_failed,
                          'accepted': bool(report['feature_placement'] and chunk and saved and chunk in statuses and not published and restored)}
        print(route, json.dumps(results[route]), flush=True)
    (out / 'failure.json').write_text(json.dumps(results, indent=2))
    accepted = all(r['accepted'] for r in results.values())
    print('Failure paths accepted:', accepted)
    sys.exit(0 if accepted else 1)


if __name__ == '__main__':
    main()
