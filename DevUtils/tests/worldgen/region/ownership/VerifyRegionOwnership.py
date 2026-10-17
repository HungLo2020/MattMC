#!/usr/bin/env python3
"""Actual retained region adoption, replay failure, rollback and last-use controls.

The requested libc heap observer is instrumented evidence, never latency or RSS.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[5]
JAVA = Path('/usr/lib/jvm/java-25-openjdk-amd64/bin')


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('output', 'classpath', 'native', 'reference'):
        parser.add_argument('--' + name, type=Path, required=True)
    kills = parser.add_mutually_exclusive_group()
    kills.add_argument('--kill-release', action='store_true', help='Require the native-retirement witness to reject an omitted actual release')
    kills.add_argument('--kill-ack', action='store_true', help='Require ordered replay to reject a forgotten successful prefix')
    kills.add_argument('--expect-unscoped-handle', action='store_true', help='Preserve the original unsafe opaque-handle GC control without dereferencing freed memory')
    kills.add_argument('--expect-release-argument-leak', action='store_true', help='Preserve a release-argument allocation failure and native retention witness')
    args = parser.parse_args()
    out = args.output.resolve()
    assert out.is_relative_to(ROOT / 'build')
    out.mkdir(parents=True, exist_ok=False)
    cp = args.classpath.read_text().strip()
    helper = ROOT / 'build/fallen-tree/s0029-chain-allocation-candidate-2/agent.jar'
    cp = str(helper) + os.pathsep + cp
    native = args.native.resolve() / 'mattmc_rust-linux-x64.so'
    reference = args.reference.resolve() / 'manifest.json'
    pin = json.loads(reference.read_text())
    sources = sorted(Path(__file__).parent.glob('*.java'))
    observer = ROOT / 'DevUtils/tests/worldgen/fallen/cost/fallen_alloc.c'
    inputs = {str(path): sha(path) for path in [*sources, observer, helper, native, reference, Path(__file__)]}
    inputs.update(pin['resource_images'])
    for path in pin['sources']:
        inputs[str(ROOT / path)] = sha(ROOT / path)
    for entry in cp.split(os.pathsep):
        if Path(entry).is_dir():
            inputs.update({str(path): sha(path) for path in Path(entry).rglob('*.class')})
    result = {'scope': __doc__, 'input_images': inputs, 'pre_commit': pin['pre_commit'], 'passed': False}
    def save():
        (out / 'result.json').write_text(json.dumps(result, indent=2))
    save()
    try:
        library = out / 'libfallen_alloc.so'
        with (out / 'gcc.log').open('w') as log:
            subprocess.run(['gcc', '-shared', '-fPIC', '-O2', '-fno-builtin', '-Wall', '-Wextra', '-Werror',
                '-o', str(library), str(observer), '-ldl', '-pthread'], stdout=log, stderr=subprocess.STDOUT, check=True)
        classes = out / 'classes'
        classes.mkdir()
        with (out / 'compile.log').open('w') as log:
            subprocess.run([str(JAVA / 'javac'), '-nowarn', '-cp', cp, '-d', str(classes), *map(str, sources)],
                stdout=log, stderr=subprocess.STDOUT, check=True)
        manifest = out / 'MANIFEST.MF'
        manifest.write_text('Premain-Class: fallen.RegionOwnershipAgent\n\n')
        jar = out / 'agent.jar'
        subprocess.run([str(JAVA / 'jar'), 'cfm', str(jar), str(manifest), '-C', str(classes), '.'], capture_output=True, check=True)
        server = out / 'server'
        server.mkdir()
        (server / 'eula.txt').write_text('eula=true\n')
        (server / 'server.properties').write_text('level-seed=42\nlevel-name=world\nserver-ip=127.0.0.1\nserver-port=0\n'
            'online-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=-1\npause-when-empty-seconds=-1\n')
        command = ['taskset', '-c', '2-7', str(JAVA / 'java'), '-Xms1G', '-Xmx4G', '-XX:+UseZGC',
            '-XX:+UseCompactObjectHeaders', '-Dmax.bg.threads=1', '--enable-native-access=ALL-UNNAMED',
            f'-Daudit.output={out}', '-Dfallen.driver=fallen.RegionOwnership', f'-Dfallen.alloc.library={library}',
            f'-Dmattmc.rust.natives.dir={native.parent}', f'-javaagent:{jar}', '-cp', str(jar) + os.pathsep + cp,
            'net.minecraft.server.Main', '--nogui', '--port', '0']
        if args.kill_release:
            command.insert(command.index('-cp'), '-Dregion.killRelease=true')
        if args.kill_ack:
            command.insert(command.index('-cp'), '-Dregion.killAck=true')
        (out / 'command.json').write_text(json.dumps(command, indent=2))
        mapped = set()
        started = time.monotonic()
        with (out / 'server.log').open('w') as log:
            process = subprocess.Popen(command, cwd=server, stdout=log, stderr=subprocess.STDOUT,
                env={**os.environ, 'LD_PRELOAD': str(library)})
            try:
                while process.poll() is None:
                    try:
                        for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines():
                            path = line.split()[-1]
                            if ('mattmc_rust' in path or 'libfallen_alloc' in path) and Path(path).is_file():
                                mapped.add(path)
                    except (FileNotFoundError, ProcessLookupError):
                        pass
                    if time.monotonic() - started > 300:
                        raise TimeoutError('Region ownership observer')
                    time.sleep(.5)
            finally:
                if process.poll() is None:
                    process.terminate()
                    process.wait(timeout=20)
        result.update(exit=process.returncode, seconds=time.monotonic()-started, mapped={path: sha(path) for path in mapped})
        assert str(library) in mapped
        assert {sha(path) for path in mapped if 'mattmc_rust' in path} == {sha(native)}
        if args.kill_release or args.kill_ack or args.expect_unscoped_handle or args.expect_release_argument_leak:
            failure = (out / 'failed.txt').read_text()
            expected = 'Tagged Rust owner retained' if args.kill_release or args.expect_release_argument_leak else 'Opaque native handle did not retain owner' if args.expect_unscoped_handle else 'Lost/duplicated projection or effect after effect'
            assert not (out / 'complete.json').exists() and expected in failure, failure
            result['negative_control_passed'] = True
            result['intended_failure'] = failure
        else:
            assert process.returncode == 0 and (out / 'complete.json').exists(), out / 'server.log'
            report = json.loads((out / 'ownership.json').read_text())
            names = {row['case'] for row in report['cases']}
            assert names == {'adoption', 'projection', 'claim', 'effect', 'replacement-rollback', 'close-in-callback', 'lifetime', 'opaque-handle-lifetime', 'release-argument-allocation', 'ack-argument-allocation'}
            result['passed'] = report['passed']
    finally:
        result['guards_pass'] = all(Path(path).exists() and sha(path) == digest for path, digest in inputs.items())
        result['passed'] = result['passed'] and result['guards_pass']
        save()
    assert result['guards_pass'] and (result['passed'] or result.get('negative_control_passed', False))
    print('REGION_OWNERSHIP expected negative' if args.kill_release or args.kill_ack or args.expect_unscoped_handle or args.expect_release_argument_leak else 'REGION_OWNERSHIP PASS', flush=True)


if __name__ == '__main__':
    main()
