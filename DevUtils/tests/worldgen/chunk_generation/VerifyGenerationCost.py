#!/usr/bin/env python3
"""Matched complete stock generation control costs using a flat-air fixture.

This changes test-world properties only. No stage/kernel hooks or production
fallbacks. Real stock saved FULL forms must match before reporting timings.
"""
import argparse
import fcntl
import json
import os
from pathlib import Path
import statistics
import subprocess
import sys

import VerifyChunkGeneration as probe

ROOT = probe.ROOT
SOURCES = Path(__file__).resolve().parent


def agent(side, cp, out, env):
    classes = out / (side + '-cost-agent'); classes.mkdir()
    sources = [SOURCES / name for name in ['GenerationCostAgent.java', 'GenerationCostAudit.java', 'GenerationAudit.java']]
    sources += [SOURCES.parent / 'full_generation/FullGenAudit.java']
    probe.logged([str(probe.full.JDK / 'javac'), '-g', '-nowarn', '-cp', cp, '-d', str(classes), *map(str, sources)],
                 out / (side + '-agent-javac.log'), ROOT, env)
    manifest = out / (side + '-MANIFEST.MF'); manifest.write_text('Premain-Class: fullgeneration.GenerationCostAgent\n\n')
    jar = out / (side + '-cost-agent.jar')
    subprocess.run([str(probe.full.JDK / 'jar'), 'cfm', str(jar), str(manifest), '-C', str(classes), '.'], check=True)
    return jar


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--pre-runtime', type=Path, required=True)
    parser.add_argument('--sides', default='frozen,pre,current')
    parser.add_argument('--rounds', type=int, default=3)
    parser.add_argument('--warmup-requests', type=int, default=128)
    parser.add_argument('--samples', type=int, default=15)
    parser.add_argument('--batch', type=int, default=16)
    parser.add_argument('--loaded-iterations', type=int, default=100000)
    parser.add_argument('--cpus', default='4-7')
    parser.add_argument('--heap', default='6G')
    parser.add_argument('--timeout', type=int, default=900)
    parser.add_argument('--pilot', action='store_true', help='fixture/driver validation only; never performance acceptance')
    args = parser.parse_args()
    args.output = args.output.resolve(); args.pre_runtime = args.pre_runtime.resolve()
    if not args.output.is_relative_to(ROOT / 'build'): raise ValueError('output must be under build/')
    args.output.mkdir(parents=True, exist_ok=False)
    sides = args.sides.split(',')
    if not set(sides) <= {'frozen', 'pre', 'current'} or len(set(sides)) != len(sides): raise ValueError('invalid sides')
    if args.rounds < 1 or args.samples < 3 or args.batch < 1 or args.warmup_requests < 1 or args.loaded_iterations < 1: raise ValueError('invalid work counts')
    if not args.pilot and (args.rounds < 3 or set(sides) != {'frozen', 'pre', 'current'}): raise ValueError('matched campaign requires three rounds and all three sides')
    if subprocess.check_output(['git', '-C', str(probe.FROZEN), 'rev-parse', 'HEAD'], text=True).strip() != probe.PIN: raise ValueError('Frozen pin changed')
    if subprocess.check_output(['git', '-C', str(probe.FROZEN), 'status', '--porcelain'], text=True).strip(): raise ValueError('Frozen dirty')
    guard_path = ROOT / 'build/full-generation/.driver.lock'; guard_path.parent.mkdir(parents=True, exist_ok=True)
    with guard_path.open('a') as guard:
        fcntl.flock(guard, fcntl.LOCK_EX)
        env = os.environ.copy(); env['JAVA_HOME'] = str(probe.full.JDK.parent); env['PATH'] = str(probe.full.JDK) + os.pathsep + env['PATH']
        for key in ['JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS']: env.pop(key, None)
        pre = probe.verify_pre(args.pre_runtime)
        prepared = {}
        for side in sides:
            cp = (args.pre_runtime / 'classpath.txt').read_text().strip() if side == 'pre' else probe.classpath(probe.FROZEN if side == 'frozen' else ROOT, args.output, env, label=side)
            prepared[side] = cp, agent(side, cp, args.output, env)
        audit_paths = [SOURCES / name for name in ['GenerationCostAgent.java', 'GenerationCostAudit.java', 'GenerationAudit.java', 'VerifyGenerationCost.py', 'VerifyChunkGeneration.py']]
        audit_paths += [SOURCES.parent / 'full_generation/FullGenAudit.java']
        def inputs(): return {'current': probe.end.inputs(ROOT), 'frozen': probe.end.inputs(probe.FROZEN), 'audit': {str(p.relative_to(ROOT)): probe.full.sha(p) for p in audit_paths}}
        before = inputs()
        base = args.output
        args.dimensions = 'minecraft:overworld'; args.ordered_features = False; args.iterations = 0
        args.extra_server_properties = 'level-type=minecraft:flat\ngenerator-settings={"layers":[],"biome":"minecraft:the_void","structure_overrides":[],"lakes":false,"features":false}\ngenerate-structures=false\n'
        args.extra_jvm_args = ['-Dgeneration.costWarm=' + str(args.warmup_requests), '-Dgeneration.costSamples=' + str(args.samples),
                               '-Dgeneration.costBatch=' + str(args.batch), '-Dgeneration.costLoaded=' + str(args.loaded_iterations)]
        results = {}
        for round_index in range(args.rounds):
            args.output = base / ('round-' + str(round_index + 1)); args.output.mkdir()
            order = sides[round_index % len(sides):] + sides[:round_index % len(sides)]
            for side in order:
                cp, jar = prepared[side]
                fresh, first = probe.launch(side, 'fresh', cp, jar, args, env)
                _, second = probe.launch(side, 'reopen', cp, jar, args, env, fresh)
                results[round_index + 1, side] = {'fresh': first, 'reopen': second}
        args.output = base
        after = inputs(); probe.verify_pre(args.pre_runtime)
        if before != after: raise RuntimeError('source/classes/native/observers changed during cost run')
        comparison = []
        for round_index in range(1, args.rounds + 1):
            for side in sides:
                pair = results[round_index, side]
                if pair['fresh']['fingerprints'] != pair['reopen']['fingerprints']:
                    raise RuntimeError('saved FULL forms changed across disk round trip')
            for phase in ['fresh', 'reopen']:
                references = [results[round_index, side][phase] for side in sides]
                for row in references[1:]:
                    if row['settings'] != references[0]['settings'] or row['request_count'] != references[0]['request_count'] or row['fingerprints'] != references[0]['fingerprints']:
                        raise RuntimeError('complete request work/output differs')
                for family, key in [('request_samples', 'ns_per_full_request'), ('loaded_samples', 'ns_per_loaded_request'), ('holder_samples', 'ns_per_holder_read')]:
                    row = {'round': round_index, 'phase': phase, 'family': family, 'sides': {}}
                    for side in sides:
                        samples = results[round_index, side][phase][family]
                        quiet = [x[key] for x in samples if x['compiler_ms'] == 0 and x['gc_ms'] == 0]
                        row['sides'][side] = {'all_samples': samples, 'quiet_samples': len(quiet), 'median_ns': statistics.median(quiet) if len(quiet) >= 5 else None}
                    for reference in ['frozen', 'pre']:
                        if 'current' in sides and reference in sides:
                            a = row['sides']['current']['median_ns']; b = row['sides'][reference]['median_ns']
                            row['ratio_current_' + reference] = a / b if a is not None and b is not None else None
                    comparison.append(row)
        report = {'work_and_output_match': True, 'before': before, 'after': after, 'pre_head': pre['source_head'], 'pilot': args.pilot,
                  'acceptance': False, 'comparisons': comparison,
                  'scope': 'Actual stock flat-air new FULL requests (normal neighbour overlap), disk-loaded FULL, loaded request and live holder read; fixed warmup/work; real executors/IO/constructors/effects/consumption; serialized output/save outside timers.',
                  'limits': 'No automatic budget pass. Require enough compilation/GC-quiet samples and inspect all process/host pressure receipts. Pilot never establishes performance acceptance.'}
        (base / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
        print('Complete stock cost work/output and image seals match; inspect retained timing and noise, no automatic acceptance.', flush=True)


if __name__ == '__main__': main()
