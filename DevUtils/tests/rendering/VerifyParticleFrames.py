#!/usr/bin/env python3
"""Measure native particle capture and frame transport against its prior Java sequence.

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
    result = {'pins': pin, 'passed': False, 'scope': '128 frames of particle capture, frame snapshots, order mapping and sparse consumption. All fields compared before timing. Prior Java capture, records and buffers are an internal control, not Frozen acceptance.'}
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
        init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('particleFrameClasspath') { doLast { println('PARTICLE_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")
        data = run([str(ROOT / 'gradlew'), '-q', '-I', str(init), 'particleFrameClasspath'], 'classpath.log')
        cp = next(line.removeprefix('PARTICLE_CP ') for line in data.splitlines() if line.startswith('PARTICLE_CP '))
        classes = out / 'classes'; classes.mkdir()
        # Compile only the old CPU capture class, not the old renderer or runtime.
        previous = subprocess.check_output(['git', 'show', '0d23141bd:src/main/java/net/minecraft/client/renderer/state/QuadParticleRenderState.java'], cwd=ROOT, text=True)
        previous = previous.replace('QuadParticleRenderState', 'PreviousParticleState')
        previous = previous.replace('PreviousParticleState.PreparedBuffers', 'QuadParticleRenderState.PreparedBuffers')
        world = subprocess.check_output(['git', 'show', '0d23141bd:src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java'], cwd=ROOT, text=True)
        start = world.index('\tprivate static void validateParticleQuadSemantics(')
        end = world.index('\tprivate static void ensureBoundedParticleViewportLocked()', start)
        validation = world[start:end].replace('private static void', 'public static void').replace('ResourceLocation atlasLocation', 'net.minecraft.resources.ResourceLocation atlasLocation')
        previous = previous.rsplit('}', 1)[0] + validation + '}\n'
        control = out / 'PreviousParticleState.java'; control.write_text(previous)
        run([str(jdk / 'javac'), '-cp', cp, '-d', str(classes), str(control), str(ROOT / 'DevUtils/tests/rendering/VerifyParticleFrames.java')], 'compile.log')
        for base in cp.split(os.pathsep):
            directory = Path(base) / 'net/vulkanic'
            images.update({str(p): sha(p) for p in directory.rglob('*.class')})
            state = Path(base) / 'net/minecraft/client/renderer/state'
            images.update({str(p): sha(p) for p in state.glob('QuadParticleRenderState*.class')})
        images.update({str(p): sha(p) for p in classes.rglob('*.class')})
        result['class_images'] = images
        command = [str(jdk / 'java'), '-Xms1g', '-Xmx1g', '-XX:+UseG1GC', '-Xbatch', '--enable-native-access=ALL-UNNAMED',
                   '-Dmattmc.rust.natives.dir=' + str(native.parent), '-cp', os.pathsep.join([str(classes), cp]), 'net.vulkanic.bridge.VerifyParticleFrames']
        timings = {}; result['timings'] = timings
        for case in ('64-bulk', '64-scalar', '1024-bulk', '1024-scalar'):
            rows = {side: [] for side in ('current', 'control')}
            for index, side in enumerate(['current', 'control'] * 3):
                data = run(command + [side, case], f'{case}-{index}-{side}.log')
                samples = [list(map(int, row)) for row in re.findall(r'PARTICLE_TIMING (\d+) (\d+) (-?\d+) (\d+)', data)]
                assert len(samples) == 8 and all(row[3] == 0 for row in samples), (case, side, samples)
                assert 'NATIVE_IMAGE ' + pin['native'] in data
                assert 'PARTICLE_EQUIVALENCE ' in data
                loaded = re.findall(r'CLASS_IMAGE (\w+) (\S+)', data); assert len(loaded) == 5
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
