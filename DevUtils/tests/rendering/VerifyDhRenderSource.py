#!/usr/bin/env python3
"""Compare saved-geometry DH source ownership/builds against untouched Frozen."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import statistics
import subprocess

ROOT = Path(__file__).resolve().parents[3]
FROZEN = ROOT.parent / 'MattMC_JavaPerfTesting' / 'MattMC'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def sources(repo):
    names = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z',
                                     '--', 'src', 'DevUtils', 'build.gradle'], cwd=repo).split(b'\0')
    return {os.fsdecode(n): digest(repo / os.fsdecode(n)) for n in names if n and (repo / os.fsdecode(n)).is_file()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--saved-manifest', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--height', type=int, choices=(16, 32, 64, 128), default=128)
    parser.add_argument('--storage-only', action='store_true')
    parser.add_argument('--predecessor', type=Path, help='Pinned classes/ and native/ directories from the preceding Current build')
    parser.add_argument('--cpus', default='5,0,1')
    args = parser.parse_args()
    output = args.output.resolve(); output.mkdir(parents=True, exist_ok=False)
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=FROZEN, text=True).strip() == '7a4d181717dc0c7da9086f1a17687dfd522ce417'
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=FROZEN)
    before = {'current': sources(ROOT), 'frozen': sources(FROZEN)}
    manifest = args.saved_manifest.resolve(); corpus = json.loads(manifest.read_text())
    pins = {str(manifest): digest(manifest), str(ROOT / 'docs/STANDARD-COPILOT-PROMPTS.md'): digest(ROOT / 'docs/STANDARD-COPILOT-PROMPTS.md')}
    for row in corpus['rows']:
        for field in row['fields'].values():
            path = manifest.parent / field['file']; pins[str(path)] = digest(path)
    init = output / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('dhRenderSourceClasspath') { "
                    "doLast { println('RENDER_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")
    jdk = Path(os.environ['JAVA_HOME']) / 'bin'
    env = os.environ.copy()
    for name in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        env.pop(name, None)
    classpaths = {}
    for side, repo in (('current', ROOT), ('frozen', FROZEN)):
        result = subprocess.run([str(repo / 'gradlew'), '-q', '-I', str(init), 'dhRenderSourceClasspath'],
                                cwd=repo, capture_output=True, text=True, env=env, check=True)
        (output / f'{side}-classpath.log').write_text(result.stdout + result.stderr)
        cp = next(line.removeprefix('RENDER_CP ') for line in result.stdout.splitlines() if line.startswith('RENDER_CP '))
        driver = output / side; driver.mkdir()
        subprocess.run([str(jdk / 'javac'), '-cp', cp, '-d', str(driver),
                        str(ROOT / 'DevUtils/tests/rendering/VerifyDhRenderSource.java')], check=True, env=env)
        classpaths[side] = str(driver) + os.pathsep + cp
    native = ROOT / 'build/rust/native'
    pins[str(native / 'mattmc_rust-linux-x64.so')] = digest(native / 'mattmc_rust-linux-x64.so')
    if args.predecessor:
        args.predecessor = args.predecessor.resolve()
        original = json.loads((args.predecessor / 'pins.json').read_text())
        assert all(digest(Path(path)) == expected for path, expected in original.items())
        classpaths['old'] = str(args.predecessor / 'classes') + os.pathsep + classpaths['current']
        pins.update(original)
    for cp in classpaths.values():
        for entry in cp.split(os.pathsep):
            path = Path(entry)
            if path.is_dir():
                for file in path.rglob('*.class'):
                    pins[str(file)] = digest(file)
            elif path.is_file():
                pins[str(path)] = digest(path)
    protocol = {'scope': '24 saved full-data geometry/light inputs with fixed colors; create, fill/reduce, minimal checksum, optional production column build/merge, close. Full point hashing/geometry oracle runs outside timing. No contextual color or whole-game acceptance.',
                'sources': len(corpus['rows']), 'height': args.height, 'build': not args.storage_only,
                'warmup_rounds': 8, 'samples_per_jvm': 16, 'source_pins': before, 'pins': pins}
    (output / 'protocol.json').write_text(json.dumps(protocol, indent=2) + '\n')

    def run(side, label, oracle=False):
        command = ['taskset', '-c', args.cpus, str(jdk / 'java'), '--enable-native-access=ALL-UNNAMED',
                   '-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '-XX:ActiveProcessorCount=3']
        if side != 'frozen':
            image = args.predecessor / 'native' if side == 'old' else native
            command += ['-Dmattmc.rust.natives.dir=' + str(image)]
        command += ['-cp', classpaths[side], 'VerifyDhRenderSource', str(manifest), str(args.height), str(not args.storage_only).lower()]
        if oracle:
            command.append('oracle')
        log = output / (label + '.log')
        with log.open('w') as stream:
            result = subprocess.run(command, cwd=FROZEN if side == 'frozen' else ROOT, env=env,
                                    stdout=stream, stderr=subprocess.STDOUT, timeout=600)
        assert result.returncode == 0, (side, log)
        return command, log.read_text().splitlines()

    oracles = {}
    for side in classpaths:
        _, lines = run(side, side + '-oracle', True)
        values = [line for line in lines if line.startswith('ORACLE ')]
        assert len(values) == 1, side
        oracles[side] = values[0]
    assert len(set(oracles.values())) == 1, oracles
    order = ['old', 'current', 'frozen', 'current', 'frozen', 'old'] if args.predecessor else ['frozen', 'current', 'current', 'frozen']
    rows = []
    for index, side in enumerate(order):
        print(index, side, flush=True)
        command, lines = run(side, f'{index:02d}-{side}')
        values = [line.split() for line in lines if line.startswith('SAMPLE ')]
        assert len(values) == 16, (side, len(values))
        rows.append({'side': side, 'command': command, 'samples_ns': [int(v[2]) for v in values],
                     'checksums': sorted({v[3] for v in values}), 'heap_allocation_bytes': [int(v[4]) for v in values],
                     'median_ms': statistics.median(int(v[2]) for v in values) / 1e6})
        (output / 'partial.json').write_text(json.dumps(rows, indent=2) + '\n')
    assert len({str(r['checksums']) for r in rows}) == 1
    guards = {'sources': before == {'current': sources(ROOT), 'frozen': sources(FROZEN)},
              'classes_native_corpus_prompt': all(digest(Path(path)) == expected for path, expected in pins.items())}
    assert all(guards.values()), guards
    medians = {side: statistics.median(r['median_ms'] for r in rows if r['side'] == side) for side in classpaths}
    heap = {side: statistics.median(v for r in rows if r['side'] == side for v in r['heap_allocation_bytes']) for side in classpaths}
    result = {**protocol, 'oracles': oracles, 'order': order, 'runs': rows, 'median_ms': medians,
              'median_heap_allocation_bytes': heap, 'guards': guards, 'frozen_performance_pass': medians['current'] <= medians['frozen']}
    (output / 'results.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({k: result[k] for k in ('median_ms', 'median_heap_allocation_bytes', 'oracles', 'guards', 'frozen_performance_pass')}))


if __name__ == '__main__':
    main()
