#!/usr/bin/env python3
"""Original aquifer factory/noise effects and real checked-Throwable identity."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[4]
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / 'DevUtils/tests/worldgen/chunk_generation'))
import VerifyChunkGeneration as common


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pre-runtime', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--controls', action='store_true')
    args = parser.parse_args()
    if not Path('/proc/self/maps').exists():
        parser.error('Actual native mapping verification requires Linux /proc.')
    pre = (ROOT / args.pre_runtime).resolve()
    manifest = common.verify_pre(pre)
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / 'build'):
        parser.error('Use a fresh output directory under build/.')
    out.mkdir(parents=True, exist_ok=False)
    jdk = ROOT / 'libraries/jdk/linux-x64/bin'
    source = HERE / 'AquiferFactoryEffects.java'
    support = [Path(value) for value in (ROOT / 'build/chunk-generation/s25-test-classpath.txt').read_text().strip().split(os.pathsep)
               if Path(value).name.startswith('objenesis-') and value.endswith('.jar')
               and not value.endswith(('-sources.jar', '-javadoc.jar'))]
    if len(support) != 1 or not support[0].is_file():
        raise RuntimeError('Expected one actual Objenesis test-support jar.')
    inputs = {str(path): sha(path) for path in [Path(__file__), source, support[0]]}
    before = {'current': common.end.inputs(ROOT), 'frozen': common.end.inputs(common.FROZEN)}
    if before['frozen']['head'] != common.PIN or before['frozen']['dirty']:
        raise RuntimeError('Original Frozen must remain clean at its pinned commit.')
    classpaths = {
        'frozen': (ROOT / 'build/chunk-generation/s28-frozen-main-classpath.txt').read_text().strip(),
        'pre': (pre / 'classpath.txt').read_text().strip(),
        'current': (ROOT / 'build/chunk-generation/s28-current-main-classpath.txt').read_text().strip(),
    }
    for side in classpaths:
        classpaths[side] += os.pathsep + str(support[0])
    classes = out / 'classes'
    classes.mkdir()
    compile_command = [str(jdk / 'javac'), '--release', '25', '-proc:none', '-cp', classpaths['frozen'], '-d', str(classes), str(source)]
    (out / 'compile-command.json').write_text(json.dumps(compile_command, indent=2))
    with (out / 'compile.log').open('w') as log:
        subprocess.run(compile_command, stdout=log, stderr=subprocess.STDOUT, check=True)
    observers = {str(path.relative_to(classes)): sha(path) for path in classes.rglob('*.class')}
    native_dirs = {'frozen': None, 'pre': pre / 'native', 'current': ROOT / 'build/rust/native'}
    result = {'passed': False, 'before': before, 'sources': inputs, 'once_Frozen_observers': observers,
              'pre_head': manifest['source_head'], 'runs': {},
              'scope': 'Actual public Aquifer effects, same once-Frozen-compiled observer; pre-change is diagnostic, Current must equal original Frozen. No copied aquifer/RNG or Current golden.'}

    def save():
        (out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')

    def run(label, side, overlay=None):
        classlog = out / (label + '-classes.log')
        cp = str(classes) + os.pathsep + classpaths[side]
        if overlay:
            cp = str(overlay) + os.pathsep + cp
        cmd = [str(jdk / 'java'), '-Xms256m', '-Xmx3g', '--enable-native-access=ALL-UNNAMED',
               '-Xlog:class+load=info:file=' + str(classlog)]
        native = native_dirs[side]
        if native:
            cmd += ['-Dmattmc.rust.natives.dir=' + str(native)]
        cmd += ['-cp', cp, 'net.minecraft.world.level.levelgen.AquiferFactoryEffects']
        (out / (label + '-command.json')).write_text(json.dumps(cmd, indent=2))
        mapped = {}
        start = time.monotonic()
        with (out / (label + '.log')).open('w') as log:
            job = subprocess.Popen(cmd, stdout=log, stderr=subprocess.STDOUT)
            try:
                while job.poll() is None:
                    try:
                        for line in Path(f'/proc/{job.pid}/maps').read_text().splitlines():
                            if 'mattmc_rust' in line and '.so' in line:
                                path = Path(line.split()[-1]).resolve()
                                mapped[str(path)] = sha(path)
                    except (FileNotFoundError, ProcessLookupError):
                        pass
                    if time.monotonic() - start > 180:
                        raise TimeoutError(label)
                    time.sleep(.05)
            finally:
                if job.poll() is None:
                    job.terminate()
                    job.wait()
        prefix = 'AQUIFER_FACTORY '
        rows = [line[line.index(prefix):] for line in (out / (label + '.log')).read_text().splitlines() if prefix in line]
        if job.returncode != 0 or len(rows) != 22:
            raise RuntimeError((label, job.returncode, len(rows)))
        trace = ('\n'.join(rows) + '\n').encode()
        (out / (label + '.trace')).write_bytes(trace)
        origins = classlog.read_text()
        assert 'net.minecraft.world.level.levelgen.AquiferFactoryEffects source: file:' + str(classes) + '/' in origins
        main_path = next(Path(value) for value in classpaths[side].split(os.pathsep) if Path(value).is_dir())
        assert 'net.minecraft.world.level.levelgen.Aquifer source: file:' + str(main_path) + '/' in origins
        if native:
            library = (native / 'mattmc_rust-linux-x64.so').resolve()
            assert mapped == {str(library): sha(library)}, mapped
        else:
            assert not mapped
        if overlay:
            assert 'net.minecraft.world.level.levelgen.NativeAquifer source: file:' + str(overlay) + '/' in origins
        result['runs'][label] = {'exit': job.returncode, 'rows': len(rows), 'trace_sha256': sha(out / (label + '.trace')),
                                 'mapped_native': mapped, 'actual_class_origins': True}
        save()
        return rows

    try:
        traces = {side: run(side, side) for side in ['frozen', 'pre', 'current']}
        original = traces['frozen']
        for mode in ['at-fail', 'draw-fail', 'noise-fail']:
            row = next(row for row in original if f'mode={mode} phase=first ' in row)
            assert 'same-marker=true ' in row, 'Original checked callback did not execute: ' + mode
        result['comparison'] = {'current_original_exact': traces['current'] == original,
                                'pre_original_exact': traces['pre'] == original,
                                'pre_differing_rows': [i for i, (a, b) in enumerate(zip(original, traces['pre'])) if a != b]}
        if not result['comparison']['current_original_exact']:
            raise AssertionError('Current aquifer effects differ from original Frozen.')
        if args.controls:
            path = ROOT / 'src/main/java/net/minecraft/world/level/levelgen/NativeAquifer.java'
            text = path.read_text()
            fixed = 'catch(Throwable t) { throw originalFailure(t); }'
            assert text.count(fixed) == 2
            for name, broken, expected in [
                ('compute-wrapper', 'catch(Throwable t) { throw new IllegalStateException("Native aquifer call failed",t); }', 3),
                ('fluid-wrapper', 'catch(Throwable t) { throw new IllegalStateException("Native aquifer fluid source failed",t); }', 1),
            ]:
                mutant = text.replace(fixed, broken, 1) if name == 'compute-wrapper' else broken.join(text.rsplit(fixed, 1))
                directory = out / name
                java = directory / 'source/net/minecraft/world/level/levelgen/NativeAquifer.java'
                java.parent.mkdir(parents=True)
                java.write_text(mutant)
                overlay = directory / 'classes'
                overlay.mkdir()
                with (directory / 'compile.log').open('w') as log:
                    subprocess.run([str(jdk / 'javac'), '--release', '25', '-proc:none', '-cp', classpaths['current'],
                                    '-d', str(overlay), str(java)], stdout=log, stderr=subprocess.STDOUT, check=True)
                inputs[str(java)] = sha(java)
                inputs.update({str(path): sha(path) for path in overlay.rglob('*.class')})
                rows = run(name, 'current', overlay)
                differences = [(a, b) for a, b in zip(original, rows) if a != b]
                assert len(differences) == expected
                assert all(a.split(' calls=')[1] == b.split(' calls=')[1] for a, b in differences)
                result['runs'][name]['rejected_by_original_comparison'] = True
        result['passed'] = True
    finally:
        result['after'] = {'current': common.end.inputs(ROOT), 'frozen': common.end.inputs(common.FROZEN)}
        result['guards_pass'] = result['after'] == before and all(sha(path) == value for path, value in inputs.items())
        result['guards_pass'] &= observers == {str(path.relative_to(classes)): sha(path) for path in classes.rglob('*.class')}
        common.verify_pre(pre)
        result['passed'] &= result['guards_pass']
        save()
    if not result['passed']:
        raise SystemExit(1)
    print('AQUIFER_FACTORY_EFFECTS_PASS', result['comparison'], flush=True)


if __name__ == '__main__':
    main()
