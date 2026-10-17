#!/usr/bin/env python3
"""Replay prior Current registry policy and measure production rig admission.

This internal causal control is not a Frozen rendering correctness baseline.
"""
import argparse
import json
import os
from pathlib import Path
import re
import statistics
import subprocess
from urllib.parse import unquote, urlparse
from VerifyMeshLifetime import ROOT, sha, sources

PARENT = '372dcacbe94a9ab6488834617b71925b29d550e4'
PACKAGE = 'src/main/java/net/vulkanic/world/'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--benchmark', action='store_true')
    args = parser.parse_args()
    assert args.java_home, 'JDK25 required'
    out = args.output.resolve(); out.mkdir(parents=True, exist_ok=False)
    jdk = Path(args.java_home) / 'bin'
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    pin = {'sources': sources(), 'native': sha(native)}
    result = {'pins': pin, 'passed': False, 'guards_pass': False, 'reference': PARENT,
              'scope': 'Registry replay and actual prepareModelRigLocked on pre-admitted inert assets; no GPU upload or Frozen gameplay acceptance.'}
    env = os.environ.copy()
    for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'): env.pop(key, None)
    env.update(JAVA_HOME=str(jdk.parent), PATH=str(jdk) + os.pathsep + env.get('PATH', ''))

    def run(command, name):
        with (out / name).open('w') as log:
            subprocess.run(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
        return (out / name).read_text()

    images = {}
    try:
        original = subprocess.check_output(['git', 'show', PARENT + ':' + PACKAGE + 'MembershipJournaledMap.java'], cwd=ROOT)
        legacy = out / 'LegacyMembershipJournaledMap.java'
        legacy.write_text(original.decode().replace('MembershipJournaledMap', 'LegacyMembershipJournaledMap'))
        controls = out / 'control-source'; controls.mkdir()
        control_sources = []
        for name in ('MembershipJournaledMap.java', 'RustGalWorldPrimitiveRenderer.java'):
            path = controls / name
            path.write_bytes(subprocess.check_output(['git', 'show', PARENT + ':' + PACKAGE + name], cwd=ROOT))
            control_sources.append(path)
        result['reference_sources'] = {str(p): sha(p) for p in control_sources}
        init = out / 'classpath.gradle'
        init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('assetRegistryClasspath') { doLast { println('REGISTRY_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")
        data = run([str(ROOT / 'gradlew'), '-q', '-I', str(init), 'assetRegistryClasspath'], 'classpath.log')
        cp = next(line.removeprefix('REGISTRY_CP ') for line in data.splitlines() if line.startswith('REGISTRY_CP '))
        classes = out / 'classes'; classes.mkdir()
        control = out / 'control-classes'; control.mkdir()
        run([str(jdk / 'javac'), '-cp', cp, '-d', str(classes), str(legacy),
             str(ROOT / 'DevUtils/tests/rendering/VerifyAssetRegistry.java')], 'compile.log')
        run([str(jdk / 'javac'), '-cp', cp, '-d', str(control)] + [str(p) for p in control_sources], 'compile-control.log')
        for base in cp.split(os.pathsep):
            directory = Path(base) / 'net/vulkanic/world'
            for pattern in ('WorldAssetRegistry*.class', 'MembershipJournalMark.class', 'RustGalWorldPrimitiveRenderer*.class', 'DynamicWorldMeshLifetime*.class'):
                images.update({str(p): sha(p) for p in directory.glob(pattern)})
        images.update({str(p): sha(p) for root in (classes, control) for p in root.rglob('*.class')})
        result['class_images'] = images
        command = [str(jdk / 'java'), '-Xms1g', '-Xmx1g', '-XX:+UseG1GC', '-Xbatch', '--enable-native-access=ALL-UNNAMED',
                   '-Dmattmc.rust.natives.dir=' + str(native.parent)]
        commands = {side: command + ['-Dregistry.side=' + side, '-cp', os.pathsep.join(([str(control)] if side == 'control' else []) + [str(classes), cp]),
                                    'net.vulkanic.world.VerifyAssetRegistry'] for side in ('current', 'control')}
        def loaded_images(data, side):
            rows = re.findall(r'CLASS_IMAGE (\w+) (\S+)', data)
            assert len(rows) == 3, rows
            for digest, uri in rows:
                parsed = urlparse(uri); assert parsed.scheme == 'file'
                path = Path(unquote(parsed.path)); assert images[str(path)] == digest == sha(path)
                if path.name == 'RustGalWorldPrimitiveRenderer.class':
                    assert path.is_relative_to(control) == (side == 'control')
        data = run(commands['current'], 'replay.log')
        assert 'REGISTRY_REPLAY 200000 PASS' in data and 'NATIVE_IMAGE ' + pin['native'] in data
        loaded_images(data, 'current')
        result['replay_cases'] = 200000
        if args.benchmark:
            timings = {}
            for case in ('stable', 'aliases', 'churn'):
                rows = {side: [] for side in commands}
                for index, side in enumerate(['current', 'control'] * 3):
                    data = run(commands[side] + [case], f'{case}-{index}-{side}.log')
                    samples = [list(map(int, row)) for row in re.findall(r'REGISTRY_TIMING (\d+) (\d+) (\d+) (\d+)', data)]
                    assert len(samples) == 8 and all(row[3] == 0 for row in samples), (case, side, samples)
                    assert 'NATIVE_IMAGE ' + pin['native'] in data
                    loaded_images(data, side)
                    rows[side].extend(samples)
                assert len({row[2] for samples in rows.values() for row in samples}) == 1
                timings[case] = {'samples': rows,
                    'median_ms': {side: statistics.median(row[0] for row in samples) / 1e6 for side, samples in rows.items()},
                    'median_java_bytes': {side: statistics.median(row[1] for row in samples) for side, samples in rows.items()}}
            result['timings'] = timings
        result['passed'] = True
    finally:
        result['guards_pass'] = sources() == pin['sources'] and sha(native) == pin['native'] and all(sha(Path(p)) == digest for p, digest in images.items())
        (out / 'results.json').write_text(json.dumps(result, indent=2) + '\n')
    assert result['guards_pass']
    print('Internal registry replay200000 PASS; source/class/native guards PASS')
    if args.benchmark: print(json.dumps({case: {k: v for k, v in row.items() if k != 'samples'} for case, row in result['timings'].items()}, indent=2))


if __name__ == '__main__': main()
