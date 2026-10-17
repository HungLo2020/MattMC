#!/usr/bin/env python3
"""Matched actual applyBiomeDecoration triplets and complete-call costs.

Short runs establish work parity only. Acceptance costs require idle matched
hosts, warmed runs and enough GC/compiler-quiet groups; no GPU/FPS claim.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import statistics
import subprocess
import time

ROOT = Path(__file__).resolve().parents[5]
JAVA = Path('/usr/lib/jvm/java-25-openjdk-amd64/bin')


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('output', 'reference'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--current-classpath', type=Path)
    parser.add_argument('--current-native', type=Path)
    parser.add_argument('--sides', default='frozen,pre,current')
    parser.add_argument('--frozen-observations', type=Path)
    parser.add_argument('--warmups', type=int, default=32)
    parser.add_argument('--groups', type=int, default=8)
    parser.add_argument('--batch', type=int, default=8)
    parser.add_argument('--resources', action='store_true', help='Separate instrumented requested-heap/retirement run; its timings are not acceptance evidence')
    args = parser.parse_args()
    sides = args.sides.split(',')
    assert set(sides) <= {'frozen', 'pre', 'current'}
    assert args.groups > 0 and args.batch > 0 and args.warmups >= 0
    if 'current' in sides:
        assert args.current_classpath and args.current_native
    if 'frozen' not in sides:
        assert args.frozen_observations
    out = args.output.resolve()
    assert out.is_relative_to(ROOT / 'build')
    out.mkdir(parents=True, exist_ok=False)
    reference = args.reference.resolve()
    pin = json.loads((reference / 'manifest.json').read_text())
    sources = sorted(Path(__file__).parent.glob('*.java'))
    inputs = {str(path): sha(path) for path in [*sources, Path(__file__), reference / 'manifest.json']}
    inputs.update(pin['resource_images'])
    inputs.update(pin['frozen_resource_images'])
    for path in pin['sources']:
        inputs[str(ROOT / path)] = sha(ROOT / path)
    classpaths, natives, helpers = {}, {}, {}
    for side in sides:
        classpaths[side] = (args.current_classpath if side == 'current' else reference / ('frozen-classpath.txt' if side == 'frozen' else 'classpath.txt')).read_text().strip()
        helpers[side] = ROOT / 'build/fallen-tree/s0029-complete-cost-1' / ('frozen-agent.jar' if side == 'frozen' else 'current-agent.jar')
        inputs[str(helpers[side])] = sha(helpers[side])
        for entry in classpaths[side].split(os.pathsep):
            if Path(entry).is_dir():
                inputs.update({str(path): sha(path) for path in Path(entry).rglob('*.class')})
        if side != 'frozen':
            natives[side] = (args.current_native if side == 'current' else reference / 'native').resolve()
            library = natives[side] / 'mattmc_rust-linux-x64.so'
            inputs[str(library)] = sha(library)
    if args.frozen_observations:
        for source in sources:
            assert sha(args.frozen_observations.parent.parent / 'observer-source' / source.name) == sha(source)
    (out / 'observer-source').mkdir()
    for source in [*sources, Path(__file__)]:
        shutil.copy2(source, out / 'observer-source' / source.name)
    library = out / 'libfallen_alloc.so'
    observer = ROOT / 'DevUtils/tests/worldgen/fallen/cost/fallen_alloc.c'
    if args.resources:
        inputs[str(observer)] = sha(observer)
    result = {'scope': __doc__, 'resource_mode': args.resources, 'input_images': inputs, 'pre_commit': pin['pre_commit'], 'frozen_head': pin['frozen_head'],
        'settings': {'warmups': args.warmups, 'groups': args.groups, 'batch': args.batch}, 'runs': {}, 'comparisons': {}, 'passed': False}
    def save():
        (out / 'result.json').write_text(json.dumps(result, indent=2))
    save()
    data = {}
    try:
        if args.resources:
            with (out / 'gcc.log').open('w') as log:
                subprocess.run(['gcc', '-shared', '-fPIC', '-O2', '-fno-builtin', '-Wall', '-Wextra', '-Werror',
                    '-o', str(library), str(observer), '-ldl', '-pthread'], stdout=log, stderr=subprocess.STDOUT, check=True)
            inputs[str(library)] = sha(library)
        for side in sides:
            run = out / side
            run.mkdir()
            classes = run / 'classes'
            classes.mkdir()
            cp = str(helpers[side]) + os.pathsep + classpaths[side]
            with (run / 'compile.log').open('w') as log:
                subprocess.run([str(JAVA / 'javac'), '-nowarn', '-cp', cp, '-d', str(classes), *map(str, sources)],
                    stdout=log, stderr=subprocess.STDOUT, check=True)
            manifest = run / 'MANIFEST.MF'
            manifest.write_text('Premain-Class: fallen.RegionSequenceAgent\n\n')
            jar = run / 'agent.jar'
            subprocess.run([str(JAVA / 'jar'), 'cfm', str(jar), str(manifest), '-C', str(classes), '.'], capture_output=True, check=True)
            server = run / 'server'
            server.mkdir()
            (server / 'eula.txt').write_text('eula=true\n')
            (server / 'server.properties').write_text('level-seed=42\nlevel-name=world\nserver-ip=127.0.0.1\nserver-port=0\n'
                'online-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=-1\npause-when-empty-seconds=-1\n')
            command = ['taskset', '-c', '2-7', str(JAVA / 'java'), '-Xms1G', '-Xmx4G', '-XX:+UseZGC',
                '-XX:+UseCompactObjectHeaders', '-Dmax.bg.threads=1', '--enable-native-access=ALL-UNNAMED',
                f'-Daudit.output={run}', '-Dfallen.driver=fallen.RegionSequence', f'-Dfallen.cost.side={side}', f'-Dregion.sequence.warmups={args.warmups}',
                f'-Dregion.sequence.groups={args.groups}', f'-Dregion.sequence.batch={args.batch}', f'-javaagent:{jar}']
            if side != 'frozen':
                command.append(f'-Dmattmc.rust.natives.dir={natives[side]}')
            if args.resources:
                command += ['-Dregion.sequence.resources=true', f'-Dfallen.alloc.library={library}']
            command += ['-cp', str(classes) + os.pathsep + cp, 'net.minecraft.server.Main', '--nogui', '--port', '0']
            (run / 'command.json').write_text(json.dumps(command, indent=2))
            print('REGION_SEQUENCE_START', side, flush=True)
            started = time.monotonic()
            mapped = set()
            with (run / 'server.log').open('w') as log:
                process = subprocess.Popen(command, cwd=server, stdout=log, stderr=subprocess.STDOUT,
                    env={**os.environ, **({'LD_PRELOAD': str(library)} if args.resources else {})})
                try:
                    while process.poll() is None:
                        try:
                            for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines():
                                path = line.split()[-1]
                                if ('mattmc_rust' in path or 'libfallen_alloc' in path) and Path(path).is_file():
                                    mapped.add(path)
                        except (FileNotFoundError, ProcessLookupError):
                            pass
                        if time.monotonic() - started > 900:
                            raise TimeoutError(side)
                        time.sleep(.5)
                finally:
                    if process.poll() is None:
                        process.terminate()
                        process.wait(timeout=20)
            result['runs'][side] = {'exit': process.returncode, 'seconds': time.monotonic()-started, 'mapped_native': {path: sha(path) for path in mapped}}
            save()
            assert process.returncode == 0 and (run / 'complete.json').exists(), run / 'server.log'
            actual_native = {path for path in mapped if 'mattmc_rust' in path}
            assert not actual_native if side == 'frozen' else actual_native and {sha(path) for path in actual_native} == {sha(natives[side] / 'mattmc_rust-linux-x64.so')}
            if args.resources:
                assert str(library) in mapped
                assert (run / 'retirement.json').exists() and (run / 'allocator-controls.json').exists()
            rows = json.loads((run / 'sequence.json').read_text())
            data[side] = {row['name']: row for row in rows}
            assert len(rows) == 2 and set(data[side]) == {'native-triple', 'callback-triple'}
        oracle = data.get('frozen') or {row['name']: row for row in json.loads(args.frozen_observations.read_text())}
        for name, old in oracle.items():
            row = {'work': {}, 'quiet_groups': {}, 'nanos': {}, 'java_bytes': {}, 'requested_heap': {}}
            for side, values in data.items():
                actual = values[name]
                row['work'][side] = all(actual[key] == old[key] for key in ('checksum', 'callbacks', 'snapshot'))
                quiet = [sample for sample in actual['samples'] if sample['quiet']]
                row['quiet_groups'][side] = len(quiet)
                for metric, target in [('nanos', 'nanos'), ('javaBytes', 'java_bytes')]:
                    row[target][side] = statistics.median(sample[metric]/sample['operations'] for sample in quiet) if len(quiet) >= 3 and not args.resources else None
                if args.resources:
                    row['requested_heap'][side] = {category: {metric: statistics.median(sample[category][index] for sample in actual['allocationSamples'])
                        for metric, index in [('requests', 1), ('peak', 5), ('live_at_return', 4)]} for category in ['all', 'rust', 'otherNative']}
            result['comparisons'][name] = row
        result['passed'] = all(all(row['work'].values()) for row in result['comparisons'].values())
    finally:
        result['guards_pass'] = all(Path(path).exists() and sha(path) == digest for path, digest in inputs.items())
        result['passed'] = result['passed'] and result['guards_pass']
        save()
    assert result['passed']
    print('REGION_SEQUENCE PASS', flush=True)


if __name__ == '__main__':
    main()
