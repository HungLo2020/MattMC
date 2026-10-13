#!/usr/bin/env python3
"""Compare production DH conversion policy and retained-source CPU workloads."""
import argparse
import json
import os
from pathlib import Path
import statistics
import subprocess

from VerifyDhRenderSource import ROOT, FROZEN, digest, sources


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--saved-manifest', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--predecessor', type=Path)
    parser.add_argument('--cpus', default='5,0,1')
    parser.add_argument('--resolved-world', action='store_true', help='Use actual stock reads of initialized color caches, excluding texture/tint resolution')
    parser.add_argument('--full-source', action='store_true', help='Time the full public production transformer; hash retained output and close outside timing')
    args = parser.parse_args()
    if args.full_source and not args.resolved_world:
        parser.error('--full-source requires --resolved-world')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=FROZEN, text=True).strip() == '7a4d181717dc0c7da9086f1a17687dfd522ce417'
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=FROZEN)
    before = {'current': sources(ROOT), 'frozen': sources(FROZEN)}
    manifest = args.saved_manifest.resolve()
    pins = {str(manifest): digest(manifest)}
    prompt = ROOT / 'docs/STANDARD-COPILOT-PROMPTS.md'
    pins[str(prompt)] = digest(prompt)
    for row in json.loads(manifest.read_text())['rows']:
        for field in row['fields'].values():
            path = manifest.parent / field['file']
            pins[str(path)] = digest(path)
    env = os.environ.copy()
    for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        env.pop(key, None)
    jdk = Path(env['JAVA_HOME']) / 'bin'
    init = output / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('dhConversionClasspath') { "
                    "doLast { println('CONVERSION_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")
    classpaths = {}
    for side, repo in (('current', ROOT), ('frozen', FROZEN)):
        result = subprocess.run([str(repo / 'gradlew'), '-q', '-I', str(init), 'dhConversionClasspath'],
                                cwd=repo, env=env, text=True, capture_output=True, check=True)
        (output / f'{side}-classpath.log').write_text(result.stdout + result.stderr)
        cp = next(line.removeprefix('CONVERSION_CP ') for line in result.stdout.splitlines() if line.startswith('CONVERSION_CP '))
        compiled = output / side
        compiled.mkdir()
        subprocess.run([str(jdk / 'javac'), '-cp', cp, '-d', str(compiled),
                        str(ROOT / 'DevUtils/tests/rendering/VerifyDhConversionPolicy.java'),
                        str(ROOT / 'DevUtils/tests/rendering/VerifyDhPrimaryConversion.java'),
                        str(ROOT / 'DevUtils/tests/rendering/DhResolvedColorFixture.java')], env=env, check=True)
        classpaths[side] = str(compiled) + os.pathsep + cp
    native = ROOT / 'build/rust/native'
    image = native / 'mattmc_rust-linux-x64.so'
    pins[str(image)] = digest(image)
    if args.predecessor:
        args.predecessor = args.predecessor.resolve()
        original = json.loads((args.predecessor / 'pins.json').read_text())
        for name, expected in original.items():
            path = Path(name) if Path(name).is_absolute() else ROOT / name
            assert digest(path) == expected, path
            pins[str(path)] = expected
        classpaths['old'] = str(args.predecessor / 'classes') + os.pathsep + classpaths['current']
    for cp in classpaths.values():
        for entry in cp.split(os.pathsep):
            path = Path(entry)
            if path.is_dir():
                for file in path.rglob('*.class'):
                    pins[str(file)] = digest(file)
            elif path.is_file():
                pins[str(path)] = digest(path)
    protocol = {
        'scope': 'Production column conversion; retained saved geometry/light, nine stock material bindings and deterministic colors. Hydration outside timing. Column mode hashes points inside timing; full-source mode retains complete production outputs and hashes/closes after timing. No contextual-world/GPU/FPS acceptance.',
        'full_source_transform': args.full_source, 'resolved_stock_color_reads': args.resolved_world, 'warmups': 4, 'samples_per_jvm': 8, 'source_pins': before, 'pins': pins,
        'current_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
    }
    (output / 'protocol.json').write_text(json.dumps(protocol, indent=2) + '\n')

    def run(side, label, main_class, arguments):
        command = ['taskset', '-c', args.cpus, str(jdk / 'java'), '--enable-native-access=ALL-UNNAMED',
                   '-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '-XX:ActiveProcessorCount=3']
        if side != 'frozen':
            library = args.predecessor / 'native' if side == 'old' else native
            command += ['-Dmattmc.rust.natives.dir=' + str(library)]
        command += ['-cp', classpaths[side], main_class, *arguments]
        log = output / (label + '.log')
        with log.open('w') as stream:
            result = subprocess.run(command, cwd=FROZEN if side == 'frozen' else ROOT, env=env,
                                    stdout=stream, stderr=subprocess.STDOUT, timeout=600)
        assert result.returncode == 0, (side, result.returncode, log)
        return command, log.read_text().splitlines()

    oracles = {}
    for stock in (False, True):
        for side in classpaths:
            for semantic in ((False,) if side == 'frozen' else (False, True)):
                label = f'oracle-{side}-stock-{stock}-semantic-{semantic}'
                _, lines = run(side, label, 'VerifyDhConversionPolicy', [str(semantic).lower(), 'unused', str(stock).lower()])
                rows = [line for line in lines if line.startswith('CASE ')]
                assert len(rows) == 896, (label, len(rows))
                oracles[(stock, side, semantic)] = rows
        assert oracles[(stock, 'current', False)] == oracles[(stock, 'frozen', False)], ('Frozen mismatch', stock)
        if 'old' in classpaths:
            for semantic in (False, True):
                assert oracles[(stock, 'current', semantic)] == oracles[(stock, 'old', semantic)], ('Predecessor mismatch', stock, semantic)
    order = ['old', 'current', 'frozen', 'current', 'frozen', 'old'] if 'old' in classpaths else ['frozen', 'current', 'current', 'frozen']
    rows = []
    for index, side in enumerate(order):
        print('Timing', index, side, flush=True)
        command, lines = run(side, f'{index:02d}-{side}', 'VerifyDhPrimaryConversion', [str(manifest), 'native', 'resolved-world' if args.resolved_world else 'proxy', 'full-source' if args.full_source else 'column'])
        expected_storage = 'STORAGE reader=false output=false' if side == 'frozen' else 'STORAGE reader=true output=true'
        if args.full_source:
            assert 'WORKLOAD full-source' in lines
        else:
            assert expected_storage in lines, (side, 'wrong storage route')
            assert ('MATERIALS scope=' + str(side == 'current' and args.resolved_world).lower()) in lines, (side, 'wrong material route')
        values = [line.split() for line in lines if line.startswith('SAMPLE ')]
        assert len(values) == 8, (side, len(values))
        rows.append({'side': side, 'command': command, 'ns': [int(v[2]) for v in values],
                     'hashes': sorted({v[3] for v in values}), 'bytes': [int(v[4]) for v in values]})
        (output / 'partial.json').write_text(json.dumps(rows, indent=2) + '\n')
    assert len({tuple(row['hashes']) for row in rows}) == 1
    guards = {'sources': before == {'current': sources(ROOT), 'frozen': sources(FROZEN)},
              'classes_native_corpus_prompt': all(digest(Path(name)) == expected for name, expected in pins.items())}
    assert all(guards.values()), guards
    medians = {side: statistics.median(statistics.median(r['ns']) for r in rows if r['side'] == side) / 1e6 for side in classpaths}
    allocations = {side: statistics.median(v for r in rows if r['side'] == side for v in r['bytes']) for side in classpaths}
    result = {'median_ms': medians, 'median_heap_bytes': allocations, 'guards': guards,
              'oracle_cases_per_mode': 896, 'stock_and_custom_oracles_match': True,
              'frozen_cpu_pass': medians['current'] <= medians['frozen']}
    (output / 'results.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2), flush=True)


if __name__ == '__main__':
    main()
