#!/usr/bin/env python3
"""Run immutable public-API fallen-tree observers on real dedicated servers.

Every requested side must emit complete, identical ordered fixture evidence.
No Frozen source rewrite or independent placement implementation is used.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[4]
SOURCES = Path(__file__).parent
JAVA = Path('/usr/lib/jvm/java-25-openjdk-amd64/bin')
FROZEN = ROOT.parent / 'MattMC_JavaPerfTesting/MattMC'


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def run(argv, log, cwd=ROOT):
    with log.open('w') as stream:
        result = subprocess.run(list(map(str, argv)), cwd=cwd, stdout=stream, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(f'Command failed ({result.returncode}): {log}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--reference', type=Path, default=ROOT / 'build/fallen-tree/s0027-pre-3f1d-2')
    parser.add_argument('--sides', default='frozen,pre,current')
    parser.add_argument('--current-classpath', type=Path)
    parser.add_argument('--current-native', type=Path)
    parser.add_argument('--frozen-observations', type=Path, help='Immutable Frozen fixture JSON from the identical observer; required for a current-only run')
    parser.add_argument('--timeout', type=int, default=900)
    parser.add_argument('--capture-reference', action='store_true', help='Explicitly use immutable pre-change classes as untouched current reference')
    parser.add_argument('--native-parent', action='store_true', help='Exercise existing actual native selector programs on pre/current')
    parser.add_argument('--marker', action='store_true', help='Require legacy Java bodies to remain unused')
    parser.add_argument('--fixtures', default='.*', help='Full-match Java fixture name expression; paired with --expected-fixtures for a subset')
    parser.add_argument('--expected-fixtures', type=int, help='Require this exact fixture count, including subset controls')
    parser.add_argument('--extended', action='store_true')
    parser.add_argument('--logical-parent', action='store_true', help='For native parents, compare genuine callbacks plus full physical world/RNG rather than Java block-method entry probes')
    parser.add_argument('--program-marker', action='store_true', help='Require the five stock parent leaves to bypass the Java configured-feature adapter')
    parser.add_argument('--native-decorations', action='store_true', help='Require stock parent decoration writes to bypass Java setBlock, with a nonvacuous Frozen counterpart')
    parser.add_argument('--jvm-arg', action='append', default=[], help='Explicit JVM argument recorded on each real server launch')
    parser.add_argument('--expect-java-fallen', action='store_true', help='Require selected stock controls to enter original fallen/attached Java bodies')
    args = parser.parse_args()
    if args.fixtures != '.*' and not args.expected_fixtures:
        parser.error('a fixture subset requires --expected-fixtures to prevent vacuous controls')
    if 'current' in args.sides.split(',') and not args.capture_reference and not (args.current_classpath and args.current_native):
        parser.error('candidate current requires explicit --current-classpath and --current-native; use --capture-reference only before production edits')
    if 'frozen' not in args.sides.split(',') and args.frozen_observations is None:
        parser.error('runs without Frozen require --frozen-observations; a side may not be its own oracle')
    out = args.output.resolve()
    if not out.is_relative_to(ROOT / 'build'):
        parser.error('output must be under build/')
    out.mkdir(parents=True, exist_ok=False)
    (out / 'observer-source').mkdir()
    for source in SOURCES.glob('*'):
        if source.is_file(): shutil.copy2(source, out / 'observer-source' / source.name)
    pre = args.reference.resolve()
    original = json.loads((pre / 'manifest.json').read_text())
    source_images = {str(p): sha(p) for p in sorted(SOURCES.glob('*')) if p.is_file()}
    if args.frozen_observations:
        saved = args.frozen_observations.resolve().parent.parent / 'observer-source'
        for name in [source.name for source in sorted(SOURCES.glob('*.java'))]:
            assert sha(saved / name) == sha(SOURCES / name), ('Observer differs from preserved Frozen', name)
    sides = args.sides.split(',')
    classpaths = {'pre': (pre / 'classpath.txt').read_text().strip(),
                  'frozen': (pre / 'frozen-classpath.txt').read_text().strip()}
    classpaths['current'] = (args.current_classpath or pre / 'classpath.txt').read_text().strip()
    natives = {'pre': pre / 'native', 'current': (args.current_native or pre / 'native').resolve()}
    classes = {}
    for cp in classpaths.values():
        for entry in cp.split(os.pathsep):
            path = Path(entry)
            if path.is_dir():
                classes.update({str(p): sha(p) for p in path.rglob('*.class')})
    native_images = {str(p): sha(p) for path in natives.values() for p in path.glob('*') if p.is_file()}
    resource_images = {**original['resource_images'], **original['frozen_resource_images']}
    production_paths = set(original['sources'])
    extra = pre / 'additional-rng-originals/manifest.json'
    if extra.exists():
        production_paths.update(json.loads(extra.read_text()))
    production_paths.update(str(p.relative_to(ROOT)) for pattern in ['src/main/rust/world/level/levelgen/decoration/fallen_tree*.rs', 'src/main/java/net/minecraft/world/level/levelgen/NativeFallenTrees.java'] for p in ROOT.glob(pattern))
    result = {'scope': 'Actual public fallen-tree effects/metadata/rng on dedicated-server isolated proto regions; no FULL-world or cost claim',
              'pre_commit': original['pre_commit'], 'frozen_head': original['frozen_head'],
              'source_images': source_images, 'class_images': classes, 'native_images': native_images,
              'reference_manifest_sha256': sha(pre / 'manifest.json'), 'capture_reference': args.capture_reference, 'native_parent': args.native_parent, 'fixture_filter': args.fixtures, 'expected_fixtures': args.expected_fixtures, 'legacy_marker': args.marker, 'logical_parent': args.logical_parent, 'program_marker': args.program_marker, 'native_decorations': args.native_decorations, 'jvm_args': args.jvm_arg, 'expect_java_fallen': args.expect_java_fallen,
              'production_images': {str(ROOT / p): sha(ROOT / p) for p in sorted(production_paths)} , 'runs': {}, 'passed': False}
    def save():
        (out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    save()
    try:
        # Compile all observer artifacts before starting any server.
        for side in sides:
            folder = out / side
            folder.mkdir()
            agent = folder / 'agent'
            agent.mkdir()
            run([JAVA / 'javac', '-nowarn', '-cp', classpaths[side], '-d', agent,
                 *sorted(SOURCES.glob('*.java'))], folder / 'compile.log')
            manifest = folder / 'MANIFEST.MF'
            manifest.write_text('Premain-Class: fallen.FallenAgent\n\n')
            run([JAVA / 'jar', 'cfm', folder / 'agent.jar', manifest, '-C', agent, '.'], folder / 'jar.log')
        for side in sides:
            folder = out / side
            server = folder / 'server'
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
''')
            command = ['taskset', '-c', '2-7', str(JAVA / 'java'), '-Xms1G', '-Xmx4G', '-XX:+UseZGC',
                       '-XX:+UseCompactObjectHeaders', '-Dmax.bg.threads=1', '--enable-native-access=ALL-UNNAMED',
                       f'-Daudit.output={folder}', f'-Dfallen.marker={str(args.marker and side != "frozen").lower()}', f'-Dfallen.fixtures={args.fixtures}', f'-Dfallen.extended={str(args.extended).lower()}', f'-Dfallen.logicalParent={str(args.logical_parent).lower()}', f'-Dfallen.programMarker={str(args.program_marker and side != "frozen").lower()}',
                       f'-javaagent:{folder / "agent.jar"}']
            if side != 'frozen':
                command += [f'-Dmattmc.rust.natives.dir={natives[side]}', f'-Dfallen.nativeParent={str(args.native_parent).lower()}']
            command += [*args.jvm_arg, '-cp', str(folder / 'agent.jar') + os.pathsep + classpaths[side],
                        'net.minecraft.server.Main', '--nogui', '--port', '0']
            (folder / 'command.json').write_text(json.dumps(command, indent=2) + '\n')
            print('START', side, flush=True)
            started = time.monotonic()
            mapped = set()
            with (folder / 'server.log').open('w') as stream:
                process = subprocess.Popen(command, cwd=server, stdout=stream, stderr=subprocess.STDOUT)
                try:
                    while process.poll() is None:
                        try:
                            for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines():
                                path = line.split()[-1]
                                if 'mattmc_rust' in path and Path(path).is_file():
                                    mapped.add(path)
                        except (FileNotFoundError, ProcessLookupError):
                            pass
                        if time.monotonic() - started > args.timeout:
                            process.terminate()
                            raise TimeoutError(f'{side} server exceeded {args.timeout}s')
                        time.sleep(0.5)
                finally:
                    if process.poll() is None:
                        process.terminate()
                        try:
                            process.wait(timeout=20)
                        except subprocess.TimeoutExpired:
                            process.kill()
                            process.wait()
            row = {'exit': process.returncode, 'elapsed_seconds': time.monotonic() - started,
                   'mapped_native_images': {path: sha(path) for path in sorted(mapped)}}
            result['runs'][side] = row
            save()
            if process.returncode or not (folder / 'complete.json').exists():
                raise AssertionError(f'{side} missing successful observer completion: {folder / "server.log"}')
            complete = json.loads((folder / 'complete.json').read_text())
            observations = json.loads((folder / 'fixtures.json').read_text())
            assert complete['success'] and complete['fixtures'] == len(observations) and (len(observations) == args.expected_fixtures if args.expected_fixtures else len(observations) >= 40)
            row.update(complete=complete, fixtures_sha256=sha(folder / 'fixtures.json'))
            if side == 'current' and args.expect_java_fallen:
                assert complete['legacyFallenBodies'] == len(observations) and complete['legacyAttachedBodies'] > 0, 'Decoration switch did not preserve original Java bodies'
            if side == 'current' and args.native_decorations:
                assert complete['stockVmDecorationWrites'] == 0, 'Stock decorations still write through Java setBlock'
            if side != 'frozen' and args.program_marker:
                assert complete['programAdapterEntries'] == 0, 'Stock program entered the configured Java adapter'
            if side != 'frozen':
                expected = sha(natives[side] / 'mattmc_rust-linux-x64.so')
                assert row['mapped_native_images'] and set(row['mapped_native_images'].values()) == {expected}
            print('DONE', side, complete['fixtures'], row['fixtures_sha256'], flush=True)
            save()
        oracle = args.frozen_observations.resolve() if args.frozen_observations else out / 'frozen/fixtures.json'
        if args.native_decorations:
            frozen_complete = oracle.parent / 'complete.json'
            assert json.loads(frozen_complete.read_text())['stockVmDecorationWrites'] > 0, 'Frozen stock decorations must actually write'
            result['frozen_decorations_complete_sha256'] = sha(frozen_complete)
        reference = json.loads(oracle.read_text())
        if args.fixtures != '.*':
            reference = [row for row in reference if re.fullmatch(args.fixtures, row['name'])]
        if args.expected_fixtures:
            assert len(reference) == args.expected_fixtures, 'Frozen subset count differs'
        result['oracle_fixture_names'] = [row['name'] for row in reference]
        result['frozen_oracle'] = {'path': str(oracle), 'sha256': sha(oracle)}
        result['comparisons'] = {side: json.loads((out / side / 'fixtures.json').read_text()) == reference for side in sides if side != 'frozen'}
        result['passed'] = (bool(result['comparisons']) and all(result['comparisons'].values())) or (sides == ['frozen'] and not args.marker)
    except BaseException as failure:
        result['error'] = type(failure).__name__ + ': ' + str(failure)
        raise
    finally:
        result['guards_pass'] = all(Path(p).exists() and sha(p) == h for images in [source_images, classes, native_images, resource_images, result['production_images']] for p, h in images.items())
        result['passed'] = result['passed'] and result['guards_pass']
        save()
    assert result['passed'], out / 'result.json'
    print('FALLEN_MATCH', result['comparisons'], flush=True)


if __name__ == '__main__':
    main()
