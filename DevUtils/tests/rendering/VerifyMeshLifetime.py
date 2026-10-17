#!/usr/bin/env python3
"""Replay mesh lifetime policy and measure its full Java/native boundary.

The old tracker is pinned internal renderer policy, not a Frozen correctness
baseline. Frozen OpenGL gameplay remains a separate required check.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import statistics
import subprocess

ROOT = Path(__file__).resolve().parents[3]
PARENT = 'efe94ff71665339d567ea65560b3c8143d1d2736'
TRACKER = 'src/main/java/net/vulkanic/world/DynamicWorldMeshLifetime.java'


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def sources():
    paths = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z', '--', 'src', 'DevUtils', 'build.gradle'], cwd=ROOT).split(b'\0')
    return {os.fsdecode(p): sha(ROOT / os.fsdecode(p)) for p in paths if p and (ROOT / os.fsdecode(p)).is_file()}


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
    result = {'pins': pin, 'passed': False, 'guards_pass': False,
              'scope': 'Pinned internal lifetime policy; does not establish Frozen rendering or gameplay parity.'}
    env = os.environ.copy()
    for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        env.pop(key, None)
    env.update(JAVA_HOME=str(Path(args.java_home)), PATH=str(jdk) + os.pathsep + env.get('PATH', ''))

    def run(command, name):
        with (out / name).open('w') as log:
            subprocess.run(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
        return (out / name).read_text()

    try:
        original = subprocess.check_output(['git', 'show', PARENT + ':' + TRACKER], cwd=ROOT)
        result['reference'] = {'commit': PARENT, 'path': TRACKER, 'sha256': hashlib.sha256(original).hexdigest()}
        legacy = out / 'LegacyDynamicWorldMeshLifetime.java'
        legacy.write_text(original.decode().replace('DynamicWorldMeshLifetime', 'LegacyDynamicWorldMeshLifetime'))
        init = out / 'classpath.gradle'
        init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('meshLifetimeClasspath') { doLast { println('LIFETIME_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")
        data = run([str(ROOT / 'gradlew'), '-q', '-I', str(init), 'meshLifetimeClasspath'], 'classpath.log')
        cp = next(line.removeprefix('LIFETIME_CP ') for line in data.splitlines() if line.startswith('LIFETIME_CP '))
        classes = out / 'classes'; classes.mkdir()
        run([str(jdk / 'javac'), '-cp', cp, '-d', str(classes), str(legacy), str(ROOT / 'DevUtils/tests/rendering/VerifyMeshLifetime.java')], 'compile.log')
        class_images = {}
        for base in cp.split(os.pathsep):
            p = Path(base) / 'net/vulkanic/world/DynamicWorldMeshLifetime.class'
            if p.is_file(): class_images[str(p)] = sha(p)
        assert len(class_images) == 1
        class_images.update({str(p): sha(p) for p in classes.rglob('*.class')})
        result['class_images'] = class_images
        command = [str(jdk / 'java'), '-Xms1g', '-Xmx1g', '-XX:+UseG1GC', '--enable-native-access=ALL-UNNAMED',
                   '-Dmattmc.rust.natives.dir=' + str(native.parent), '-cp', str(classes) + os.pathsep + cp, 'net.vulkanic.world.VerifyMeshLifetime']
        assert 'REPLAY 200000 PASS' in run(command, 'replay.log')
        result['passed'] = True
        if args.benchmark:
            timings = {}
            for case in ('stable', 'churn', 'rig-stable', 'rig-churn'):
                rows = {side: [] for side in ('current', 'legacy')}
                for index, side in enumerate(['current', 'legacy'] * 3):
                    data = run(command + [side, case], f'{case}-{index}-{side}.log')
                    samples = [list(map(int, row)) for row in re.findall(r'SAMPLE (\d+) (\d+) (\d+) (\d+)', data)]
                    assert len(samples) == 8 and not any(row[3] for row in samples), (case, side, 'timed compilation', samples)
                    rows[side].extend(samples)
                assert len({row[2] for samples in rows.values() for row in samples}) == 1
                timings[case] = {'samples': rows,
                                'median_ms': {side: statistics.median(row[0] for row in samples) / 1e6 for side, samples in rows.items()},
                                'median_java_bytes': {side: statistics.median(row[1] for row in samples) for side, samples in rows.items()}}
            result['timings'] = timings
    finally:
        result['guards_pass'] = pin == {'sources': sources(), 'native': sha(native)}
        if 'class_images' in result:
            result['guards_pass'] &= all(sha(Path(p)) == digest for p, digest in result['class_images'].items())
        (out / 'results.json').write_text(json.dumps(result, indent=2) + '\n')
    assert result['passed'] and result['guards_pass']
    print('Internal lifetime policy replay200000 and source/class/native guards PASS')
    if 'timings' in result:
        print(json.dumps({k: {x: v[x] for x in ('median_ms', 'median_java_bytes')} for k, v in result['timings'].items()}, indent=2))


if __name__ == '__main__':
    main()
