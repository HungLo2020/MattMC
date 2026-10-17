#!/usr/bin/env python3
"""One bounded paired JFR attribution of unchanged complete public calls.

Raw recordings stay local. Only selected owned-operation stacks are suitable
for review; sample weights are not exact allocation totals or native CPU shares.
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
    for name in ('output', 'reference', 'current-classpath', 'current-native'):
        parser.add_argument('--'+name, type=Path, required=True)
    args = parser.parse_args()
    out = args.output.resolve()
    assert out.is_relative_to(ROOT/'build')
    out.mkdir(parents=True, exist_ok=False)
    source = Path(__file__).with_name('FallenChainProfile.java')
    pin = json.loads((args.reference/'manifest.json').read_text())
    sources = sorted(Path(__file__).parent.glob('*.java'))
    inputs = {str(path): sha(path) for path in [*sources, Path(__file__), args.reference/'manifest.json']}
    inputs.update(pin['resource_images'])
    result = {'scope': __doc__, 'runs': {}, 'input_images': inputs, 'passed': False}
    def save():
        (out/'result.json').write_text(json.dumps(result, indent=2))
    try:
        for side in ('pre', 'current'):
            run = out/side
            run.mkdir()
            cp = (args.current_classpath if side == 'current' else args.reference/'classpath.txt').read_text().strip()
            helper = ROOT/'build/fallen-tree/s0029-complete-cost-1/current-agent.jar'
            cp = str(helper)+os.pathsep+cp
            native = (args.current_native if side == 'current' else args.reference/'native').resolve()
            inputs[str(helper)] = sha(helper)
            inputs[str(native/'mattmc_rust-linux-x64.so')] = sha(native/'mattmc_rust-linux-x64.so')
            for entry in cp.split(os.pathsep):
                if Path(entry).is_dir():
                    inputs.update({str(path): sha(path) for path in Path(entry).rglob('*.class')})
            classes = run/'classes'
            classes.mkdir()
            with (run/'compile.log').open('w') as log:
                subprocess.run([str(JAVA/'javac'), '-nowarn', '-cp', cp, '-d', str(classes), *map(str, sources)],
                    check=True, stdout=log, stderr=subprocess.STDOUT)
            server = run/'server'
            server.mkdir()
            (server/'eula.txt').write_text('eula=true\n')
            (server/'server.properties').write_text('level-seed=42\nlevel-name=world\nserver-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=-1\npause-when-empty-seconds=-1\n')
            # This retained resource agent only boots the selected driver; no
            # allocation interposer or voxel/feature probes are installed.
            boot = ROOT/'build/fallen-tree/s0029-chain-allocation-candidate-2/agent.jar'
            inputs[str(boot)] = sha(boot)
            command = ['taskset', '-c', '2-7', str(JAVA/'java'), '-Xms2G', '-Xmx4G', '-XX:+UseZGC',
                '-XX:+UseCompactObjectHeaders', '-Dmax.bg.threads=1', '--enable-native-access=ALL-UNNAMED',
                f'-Daudit.output={run}', f'-Dfallen.cost.side={side}', '-Dfallen.driver=fallen.FallenChainProfile',
                f'-Dmattmc.rust.natives.dir={native}', '-XX:FlightRecorderOptions=stackdepth=128',
                f'-javaagent:{boot}', '-cp', str(classes)+os.pathsep+str(boot)+os.pathsep+cp,
                'net.minecraft.server.Main', '--nogui', '--port', '0']
            manifest = run/'MANIFEST.MF'
            manifest.write_text('Premain-Class: fallen.RegionProfileAgent\n\n')
            bootjar = run/'boot.jar'
            subprocess.run([str(JAVA/'jar'), 'cfm', str(bootjar), str(manifest), '-C', str(classes), '.'], check=True, capture_output=True)
            command[command.index(f'-javaagent:{boot}')] = f'-javaagent:{bootjar}'
            (run/'command.json').write_text(json.dumps(command, indent=2))
            save()
            mapped = set()
            started = time.monotonic()
            with (run/'server.log').open('w') as log:
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
                        if time.monotonic()-started > 600:
                            raise TimeoutError(side)
                        time.sleep(.5)
                finally:
                    if process.poll() is None:
                        process.terminate()
                        process.wait(timeout=20)
            result['runs'][side] = {'exit': process.returncode, 'mapped_native': {p: sha(p) for p in mapped}}
            assert process.returncode == 0 and (run/'complete.json').exists(), run/'server.log'
            assert mapped and {sha(p) for p in mapped} == {sha(native/'mattmc_rust-linux-x64.so')}
        result['passed'] = True
    finally:
        result['guards_pass'] = all(Path(p).exists() and sha(p) == h for p, h in inputs.items())
        result['passed'] = result['passed'] and result['guards_pass']
        save()
    assert result['passed']


if __name__ == '__main__':
    main()
