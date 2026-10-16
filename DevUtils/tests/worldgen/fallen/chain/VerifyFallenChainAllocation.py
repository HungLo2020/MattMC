#!/usr/bin/env python3
"""Inject actual program adoption/publication failures and check native ownership.

Linux/glibc requested-heap observation only; no timing or process-memory claim.
The optional expected-leak mode records an original failing negative control.
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
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--classpath', type=Path, required=True)
    parser.add_argument('--native', type=Path, required=True)
    parser.add_argument('--reference', type=Path, required=True)
    parser.add_argument('--expect-adoption-leak', action='store_true')
    args = parser.parse_args()
    out = args.output.resolve()
    assert out.is_relative_to(ROOT / 'build')
    out.mkdir(exist_ok=False, parents=True)
    base = Path(__file__).parents[1]
    sources = [base / name for name in ('FallenAudit.java', 'FallenChainRisks.java',
        'cost/FallenAllocation.java', 'cost/FallenResourceAgent.java',
        'chain/FallenChainAllocation.java', 'chain/FallenChainAllocationAgent.java')]
    observer = base / 'cost/fallen_alloc.c'
    library = out / 'libfallen_alloc.so'
    classpath = args.classpath.read_text().strip()
    native = args.native.resolve() / 'mattmc_rust-linux-x64.so'
    reference = args.reference.resolve() / 'manifest.json'
    inputs = {str(path): sha(path) for path in [*sources, observer, native, reference, Path(__file__)]}
    pin = json.loads(reference.read_text())
    inputs.update(pin['resource_images'])
    for entry in classpath.split(os.pathsep):
        if Path(entry).is_dir():
            inputs.update({str(path): sha(path) for path in Path(entry).rglob('*.class')})
    result = {'scope': __doc__, 'input_images': inputs, 'pre_commit': pin['pre_commit'], 'passed': False}
    def save():
        (out / 'result.json').write_text(json.dumps(result, indent=2))
    save()
    try:
        with (out / 'gcc.log').open('w') as log:
            subprocess.run(['gcc', '-shared', '-fPIC', '-O2', '-fno-builtin', '-Wall', '-Wextra', '-Werror',
                '-o', str(library), str(observer), '-ldl', '-pthread'], stdout=log, stderr=subprocess.STDOUT, check=True)
        classes = out / 'classes'
        classes.mkdir()
        with (out / 'compile.log').open('w') as log:
            subprocess.run([str(JAVA / 'javac'), '-nowarn', '-cp', classpath, '-d', str(classes),
                *map(str, sources)], stdout=log, stderr=subprocess.STDOUT, check=True)
        manifest = out / 'MANIFEST.MF'
        manifest.write_text('Premain-Class: fallen.FallenChainAllocationAgent\n\n')
        jar = out / 'agent.jar'
        subprocess.run([str(JAVA / 'jar'), 'cfm', str(jar), str(manifest), '-C', str(classes), '.'], capture_output=True, check=True)
        server = out / 'server'
        server.mkdir()
        (server / 'eula.txt').write_text('eula=true\n')
        (server / 'server.properties').write_text('level-seed=42\nlevel-name=world\nserver-ip=127.0.0.1\nserver-port=0\n'
            'online-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=-1\npause-when-empty-seconds=-1\n')
        command = ['taskset', '-c', '2-7', str(JAVA / 'java'), '-Xms1G', '-Xmx4G', '-XX:+UseZGC',
            '-XX:+UseCompactObjectHeaders', '-Dmax.bg.threads=1', '--enable-native-access=ALL-UNNAMED',
            f'-Daudit.output={out}', '-Dfallen.driver=fallen.FallenChainAllocation',
            f'-Dfallen.alloc.library={library}', f'-Dmattmc.rust.natives.dir={native.parent}',
            f'-javaagent:{jar}', '-cp', str(jar) + os.pathsep + classpath, 'net.minecraft.server.Main', '--nogui', '--port', '0']
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
                        raise TimeoutError('Allocation boundary observer')
                    time.sleep(.5)
            finally:
                if process.poll() is None:
                    process.terminate()
                    process.wait(timeout=20)
        result.update(exit=process.returncode, mapped={path: sha(path) for path in mapped})
        assert process.returncode == 0 and (out / 'complete.json').exists(), out / 'server.log'
        assert str(library) in mapped
        assert {sha(path) for path in mapped if 'mattmc_rust' in path} == {sha(native)}
        report = json.loads((out / 'allocation-boundaries.json').read_text())
        rows = {row['boundary']: row for row in report['cases']}
        assert set(rows) == {'adoption', 'publication', 'replacement'}
        result['passed'] = all(row['allTaggedRustRetired'] for row in rows.values())
        if args.expect_adoption_leak:
            assert not rows['adoption']['allTaggedRustRetired'] and all(rows[key]['allTaggedRustRetired'] for key in ('publication', 'replacement'))
            result['negative_control_passed'] = True
        else:
            assert result['passed'], 'Native allocation survived failed adoption/publication'
    finally:
        result['guards_pass'] = all(Path(path).exists() and sha(path) == digest for path, digest in inputs.items())
        result['passed'] = result['passed'] and result['guards_pass']
        save()
    assert result['guards_pass']
    print('CHAIN_ALLOCATION', 'expected negative' if args.expect_adoption_leak else 'PASS', flush=True)


if __name__ == '__main__':
    main()
