#!/usr/bin/env python3
"""Measure Rust-owned shadow capture against the previous Java candidate buffers.

Supplemental internal comparison; unchanged Frozen OpenGL remains the end-to-end baseline.
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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    assert args.java_home, 'JDK 25 required'
    out = args.output.resolve(); out.mkdir(parents=True, exist_ok=False)
    jdk = Path(args.java_home) / 'bin'
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    pin = {'sources': sources(), 'native': sha(native)}
    result = {'pins': pin, 'passed': False, 'scope': '128 frames of shadow candidate capture, bounds preparation and sparse consumption. All fields compared before timing. Prior Java records and buffers are an internal control, not Frozen acceptance.'}
    env = os.environ.copy()
    for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'): env.pop(key, None)
    env.update(JAVA_HOME=str(jdk.parent), PATH=str(jdk) + os.pathsep + env.get('PATH', ''))
    images = {}

    def run(command, name):
        with (out / name).open('w') as log:
            subprocess.run(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
        return (out / name).read_text()

    try:
        init = out / 'classpath.gradle'
        init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('shadowCandidateClasspath') { doLast { println('CANDIDATE_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")
        data = run([str(ROOT / 'gradlew'), '-q', '-I', str(init), 'shadowCandidateClasspath'], 'classpath.log')
        cp = next(line.removeprefix('CANDIDATE_CP ') for line in data.splitlines() if line.startswith('CANDIDATE_CP '))
        classes = out / 'classes'; classes.mkdir()
        run([str(jdk / 'javac'), '-cp', cp, '-d', str(classes), str(ROOT / 'DevUtils/tests/rendering/VerifyShadowCandidates.java')], 'compile.log')
        for base in cp.split(os.pathsep):
            directory = Path(base) / 'net/vulkanic'
            images.update({str(p): sha(p) for p in directory.rglob('*.class')})
        images.update({str(p): sha(p) for p in classes.rglob('*.class')})
        result['class_images'] = images
        command = [str(jdk / 'java'), '-Xms1g', '-Xmx1g', '-XX:+UseG1GC', '-Xbatch', '--enable-native-access=ALL-UNNAMED',
                   '-Dmattmc.rust.natives.dir=' + str(native.parent), '-cp', os.pathsep.join([str(classes), cp]), 'net.vulkanic.bridge.VerifyShadowCandidates']
        timings = {}; result['timings'] = timings
        for case in ('64-plain', '64-leash', '1024-plain', '1024-leash'):
            rows = {side: [] for side in ('current', 'control')}
            for index, side in enumerate(['current', 'control'] * 3):
                data = run(command + [side, case], f'{case}-{index}-{side}.log')
                samples = [list(map(int, row)) for row in re.findall(r'CANDIDATE_TIMING (\d+) (\d+) (-?\d+) (\d+)', data)]
                assert len(samples) == 8 and all(row[3] == 0 for row in samples), (case, side, samples)
                assert 'NATIVE_IMAGE ' + pin['native'] in data
                assert 'CANDIDATE_EQUIVALENCE ' in data
                loaded = re.findall(r'CLASS_IMAGE (\w+) (\S+)', data); assert len(loaded) == 3
                for digest, uri in loaded:
                    parsed = urlparse(uri); assert parsed.scheme == 'file'
                    path = Path(unquote(parsed.path)); assert images[str(path)] == digest == sha(path)
                rows[side].extend(samples)
            assert len({row[2] for samples in rows.values() for row in samples}) == 1, 'Output checksums differ'
            timings[case] = {'samples': rows,
                'median_ms': {side: statistics.median(row[0] for row in samples) / 1e6 for side, samples in rows.items()},
                'median_java_bytes': {side: statistics.median(row[1] for row in samples) for side, samples in rows.items()}}
        result['passed'] = True
    finally:
        result['guards_pass'] = sources() == pin['sources'] and sha(native) == pin['native'] and all(sha(Path(p)) == digest for p, digest in images.items())
        (out / 'results.json').write_text(json.dumps(result, indent=2) + '\n')
    assert result['guards_pass']
    print(json.dumps({case: {k: v for k, v in row.items() if k != 'samples'} for case, row in result['timings'].items()}, indent=2))


if __name__ == '__main__': main()
