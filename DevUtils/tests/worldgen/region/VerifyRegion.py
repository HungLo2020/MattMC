#!/usr/bin/env python3
"""Public FEATURES-region callback/alias controls against immutable real servers."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[4]
JAVA = Path('/usr/lib/jvm/java-25-openjdk-amd64/bin')


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--reference', type=Path, required=True)
    parser.add_argument('--sides', default='frozen,pre')
    parser.add_argument('--current-classpath', type=Path)
    parser.add_argument('--current-native', type=Path)
    parser.add_argument('--frozen-observations', type=Path)
    parser.add_argument('--fixtures', default='.*')
    parser.add_argument('--expected-fixtures', type=int, default=10)
    args = parser.parse_args()
    out = args.output.resolve()
    assert out.is_relative_to(ROOT / 'build')
    out.mkdir(parents=True, exist_ok=False)
    reference = args.reference.resolve()
    pin = json.loads((reference / 'manifest.json').read_text())
    sides = args.sides.split(',')
    assert set(sides) <= {'frozen', 'pre', 'current'}
    if 'current' in sides:
        assert args.current_classpath and args.current_native
    if 'frozen' not in sides:
        assert args.frozen_observations
    classpaths = {side: (reference / ('frozen-classpath.txt' if side == 'frozen' else 'classpath.txt')).read_text().strip() for side in sides}
    if 'current' in sides:
        classpaths['current'] = args.current_classpath.read_text().strip()
    natives = {'pre': reference / 'native', 'current': args.current_native.resolve() if args.current_native else reference / 'native'}
    helpers = {side: ROOT / 'build/fallen-tree/s0029-complete-focused-1' / ('frozen' if side == 'frozen' else 'current') / 'agent.jar' for side in sides}
    boots = {side: ROOT / 'build/fallen-tree/s0029-complete-cost-1' / ('frozen-agent.jar' if side == 'frozen' else 'current-agent.jar') for side in sides}
    sources = sorted(Path(__file__).parent.glob('*.java'))
    inputs = {str(path): sha(path) for path in [*sources, Path(__file__), reference / 'manifest.json', *helpers.values(), *boots.values()]}
    inputs.update(pin['resource_images'])
    inputs.update(pin['frozen_resource_images'])
    for side, cp in classpaths.items():
        for entry in cp.split(os.pathsep):
            if Path(entry).is_dir():
                inputs.update({str(path): sha(path) for path in Path(entry).rglob('*.class')})
        if side != 'frozen':
            library = natives[side] / 'mattmc_rust-linux-x64.so'
            inputs[str(library)] = sha(library)
    production = {str(ROOT / path): sha(ROOT / path) for path in pin['sources']}
    inputs.update(production)
    (out / 'observer-source').mkdir()
    for path in [*sources, Path(__file__)]:
        shutil.copy2(path, out / 'observer-source' / path.name)
    if args.frozen_observations:
        saved = args.frozen_observations.resolve().parent.parent / 'observer-source'
        for path in sources:
            assert sha(saved / path.name) == sha(path)
    result = {'scope': __doc__, 'pre_commit': pin['pre_commit'], 'frozen_head': pin['frozen_head'],
        'input_images': inputs, 'fixtures': args.fixtures, 'expected_fixtures': args.expected_fixtures, 'runs': {}, 'comparisons': {}, 'passed': False}
    def save():
        (out / 'result.json').write_text(json.dumps(result, indent=2))
    save()
    try:
        for side in sides:
            run = out / side
            run.mkdir()
            classes = run / 'classes'
            classes.mkdir()
            cp = os.pathsep.join([str(helpers[side]), str(boots[side]), classpaths[side]])
            with (run / 'compile.log').open('w') as log:
                subprocess.run([str(JAVA / 'javac'), '-nowarn', '-cp', cp, '-d', str(classes), *map(str, sources)],
                    stdout=log, stderr=subprocess.STDOUT, check=True)
            manifest = run / 'MANIFEST.MF'
            manifest.write_text('Premain-Class: fallen.RegionAuditAgent\n\n')
            jar = run / 'agent.jar'
            subprocess.run([str(JAVA / 'jar'), 'cfm', str(jar), str(manifest), '-C', str(classes), '.'], capture_output=True, check=True)
            server = run / 'server'
            server.mkdir()
            (server / 'eula.txt').write_text('eula=true\n')
            (server / 'server.properties').write_text('level-seed=42\nlevel-name=world\nserver-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n'
                'view-distance=2\nsimulation-distance=2\nmax-tick-time=-1\npause-when-empty-seconds=-1\nsync-chunk-writes=false\n')
            command = ['taskset', '-c', '2-7', str(JAVA / 'java'), '-Xms1G', '-Xmx4G', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders',
                '-Dmax.bg.threads=1', '--enable-native-access=ALL-UNNAMED', f'-Daudit.output={run}', '-Dfallen.driver=fallen.RegionAudit',
                f'-Dfallen.nativeParent={str(side != "frozen").lower()}', f'-Dregion.fixtures={args.fixtures}', f'-javaagent:{jar}']
            if side != 'frozen':
                command.append(f'-Dmattmc.rust.natives.dir={natives[side]}')
            command += ['-cp', str(jar) + os.pathsep + cp, 'net.minecraft.server.Main', '--nogui', '--port', '0']
            (run / 'command.json').write_text(json.dumps(command, indent=2))
            print('START region', side, flush=True)
            mapped = set()
            started = time.monotonic()
            with (run / 'server.log').open('w') as log:
                process = subprocess.Popen(command, cwd=server, stdout=log, stderr=subprocess.STDOUT)
                try:
                    while process.poll() is None:
                        try:
                            for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines():
                                path = line.split()[-1]
                                if 'mattmc_rust' in path and Path(path).is_file():
                                    mapped.add(path)
                        except (FileNotFoundError, ProcessLookupError):
                            pass
                        if time.monotonic() - started > 600:
                            raise TimeoutError(side)
                        time.sleep(.5)
                finally:
                    if process.poll() is None:
                        process.terminate()
                        process.wait(timeout=20)
            result['runs'][side] = {'exit': process.returncode, 'mapped_native': {path: sha(path) for path in mapped}}
            save()
            assert process.returncode == 0 and (run / 'complete.json').exists(), run / 'server.log'
            complete = json.loads((run / 'complete.json').read_text())
            assert complete['success'] and complete['fixtures'] == args.expected_fixtures
            if side == 'frozen':
                assert not mapped
            else:
                assert mapped and {sha(path) for path in mapped} == {sha(natives[side] / 'mattmc_rust-linux-x64.so')}
            print('DONE region', side, complete['fixtures'], flush=True)
        oracle = args.frozen_observations.resolve() if args.frozen_observations else out / 'frozen/fixtures.json'
        expected = json.loads(oracle.read_text())
        import re
        expected = [row for row in expected if re.fullmatch(args.fixtures, row['name'])]
        assert len(expected) == args.expected_fixtures
        for side in sides:
            if side == 'frozen':
                continue
            actual = json.loads((out / side / 'fixtures.json').read_text())
            result['comparisons'][side] = {'identical': actual == expected,
                'different': [row['name'] for row, reference_row in zip(actual, expected) if row != reference_row]}
        result['passed'] = all(row['identical'] for row in result['comparisons'].values())
    finally:
        result['guards_pass'] = all(Path(path).exists() and sha(path) == digest for path, digest in inputs.items())
        result['passed'] = result['passed'] and result['guards_pass']
        save()
    print('REGION_MATCH', result['comparisons'], flush=True)
    assert result['passed'], out / 'result.json'


if __name__ == '__main__':
    main()
