#!/usr/bin/env python3
"""Observe actual request producers on pinned Frozen, preserved pre and Current images.

Build first. This runner compiles portable observers once against Frozen, checks
all selected image/source inputs, and records the native library actually mapped
by each JVM. It does not build or certify release/performance acceptance.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[4]
HERE = Path(__file__).resolve().parent
FROZEN = ROOT.parent / 'MattMC_JavaPerfTesting/MattMC'
FROZEN_BASE = '7a4d181717dc0c7da9086f1a17687dfd522ce417'


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--profile-description', required=True)
    parser.add_argument('--observer', action='append', choices=('ThreadedRequestContracts', 'ThreadedRequestFailures', 'ThreadedRequestLifetimes', 'ThreadedUnloadCustody'),
                        help='Run only selected observers; the default runs all four')
    for side in ('frozen', 'pre', 'current'):
        parser.add_argument('--' + side + '-classpath', type=Path, required=True)
        if side != 'frozen':
            parser.add_argument('--' + side + '-native', type=Path, required=True)
    args = parser.parse_args()
    observers = args.observer or ['ThreadedRequestContracts', 'ThreadedRequestFailures', 'ThreadedRequestLifetimes', 'ThreadedUnloadCustody']
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    result = {'passed': False, 'profile': args.profile_description, 'frozen_base': FROZEN_BASE,
              'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
              'runs': {}}
    assert Path('/proc/self/maps').exists(), 'Actual native mapping observation requires Linux'
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=FROZEN, text=True).strip() == FROZEN_BASE
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=FROZEN)
    inputs = {}
    classpaths = {}
    natives = {}
    for side in ('frozen', 'pre', 'current'):
        cpfile = getattr(args, side + '_classpath').resolve()
        inputs[str(cpfile)] = sha(cpfile)
        classpaths[side] = cpfile.read_text().strip()
        if side != 'frozen':
            native = getattr(args, side + '_native').resolve() / 'mattmc_rust-linux-x64.so'
            natives[side] = native
            inputs[str(native)] = sha(native)
        for part in classpaths[side].split(os.pathsep):
            path = Path(part)
            if path.is_dir():
                for selected in path.rglob('*'):
                    if selected.is_file():
                        inputs[str(selected)] = sha(selected)
            elif path.is_file():
                inputs[str(path)] = sha(path)
    sources = [HERE / (name + '.java') for name in
               ('VerifyThreadedLighting', 'ThreadedRequestContracts', 'ThreadedRequestFailures', 'ThreadedRequestLifetimes', 'ThreadedUnloadCustody')]
    for source in [*sources, Path(__file__).resolve()]:
        inputs[str(source)] = sha(source)
    classes = output / 'classes'
    classes.mkdir()
    compile_command = [str(args.java_home / 'bin/javac'), '-g', '-cp', classpaths['frozen'],
                       '-d', str(classes), *map(str, sources)]
    with (output / 'compile.log').open('w') as log:
        compiled = subprocess.run(compile_command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    result['compile_command'] = compile_command
    result['compile_exit'] = compiled.returncode
    if compiled.returncode:
        (output / 'result.json').write_text(json.dumps(result, indent=2))
        raise RuntimeError('Observer compilation failed; see compile.log')
    for path in classes.rglob('*.class'):
        inputs[str(path)] = sha(path)
    result['inputs'] = inputs
    env = {key: value for key, value in os.environ.items()
           if key not in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'MATTMC_RUST_NATIVE_DIR')}
    traces = {}
    for observer in observers:
        for side in ('frozen', 'pre', 'current'):
            label = observer + '-' + side
            trace = output / (label + '.trace')
            command = [str(args.java_home / 'bin/java'), '-Xms256m', '-Xmx2g',
                       '--enable-native-access=ALL-UNNAMED', '-DthreadedLight.frozen=' + str(side == 'frozen').lower(),
                       '-DthreadedLight.nativeRequests=' + str(side == 'current').lower(),
                       *([] if side == 'frozen' else ['-Dmattmc.rust.natives.dir=' + str(natives[side].parent)]), '-cp',
                       str(classes) + os.pathsep + classpaths[side],
                       'net.minecraft.server.level.' + observer, str(trace)]
            mapped = set()
            started = time.monotonic()
            with (output / (label + '.log')).open('w') as log:
                process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
                try:
                    while process.poll() is None:
                        try:
                            for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines():
                                name = line.split()[-1]
                                if 'mattmc_rust' in name:
                                    mapped.add(name)
                        except (FileNotFoundError, ProcessLookupError):
                            pass
                        if time.monotonic() - started > 180:
                            raise TimeoutError(label)
                        time.sleep(.025)
                finally:
                    if process.poll() is None:
                        process.terminate()
                        process.wait()
            result['runs'][label] = {'command': command, 'exit': process.returncode,
                                    'seconds': time.monotonic() - started,
                                    'native_mapping_matches': mapped == (set() if side == 'frozen' else {str(natives[side])}),
                                    'mapped_native': {name: sha(name) for name in mapped}}
            traces[label] = trace.read_bytes() if trace.exists() else None
            (output / 'result.json').write_text(json.dumps(result, indent=2))
    result['traces_match'] = {observer: traces[observer + '-frozen'] is not None and
                             traces[observer + '-frozen'] == traces[observer + '-pre'] == traces[observer + '-current']
                             for observer in observers}
    result['guards_pass'] = all(sha(path) == digest for path, digest in inputs.items())
    result['passed'] = (result['guards_pass'] and all(result['traces_match'].values()) and
                        all(run['exit'] == 0 and run['native_mapping_matches'] for run in result['runs'].values()))
    (output / 'result.json').write_text(json.dumps(result, indent=2))
    print({key: value for key, value in result.items() if key not in ('inputs', 'runs', 'compile_command')})
    if not result['passed']:
        raise RuntimeError('Request contracts failed; preserve traces and logs in ' + str(output))


if __name__ == '__main__':
    main()
