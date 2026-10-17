#!/usr/bin/env python3
"""Measure production entity collection with actual Frozen and a Current consumer control.

Run VerifySectionMembers.py separately for the callback/mutation correctness corpus.
"""
import argparse
import json
import os
from pathlib import Path
import re
import statistics
import subprocess
from urllib.parse import unquote, urlparse
from VerifySectionMembers import ROOT, FROZEN, sources, sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--screen-shake', action='store_true', help='Measure effect-source collection and the original consumer capability loop')
    args = parser.parse_args()
    assert args.java_home, 'JDK 25 required'
    out = args.output.resolve(); out.mkdir(parents=True, exist_ok=False)
    jdk = Path(args.java_home) / 'bin'
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    pin = {'current': sources(ROOT), 'frozen': sources(FROZEN), 'native': sha(native),
           'frozen_head': subprocess.check_output(['git', '-C', str(FROZEN), 'rev-parse', 'HEAD'], text=True).strip()}
    assert pin['frozen_head'] == '7a4d181717dc0c7da9086f1a17687dfd522ce417'
    assert not subprocess.check_output(['git', '-C', str(FROZEN), 'status', '--porcelain'])
    result = {'pins': pin, 'passed': False,
              'scope': '4096 production getter collections per sample, including result allocation, projection and sparse consumption. Full identity/order checked before timing. 64 warmups, eight compiler-quiet samples (at most 16 attempts, all measured-JIT samples retained separately), three interleaved JVMs per side. Current control uses the previous get/callback sequence on candidate storage; Frozen is unchanged. Excludes gameplay and native memory.'}
    if args.screen_shake:
        result['scope'] = '4096 screen-shake source collections and capability loops per sample. Current uses retained native selection; control collects all non-spectators on candidate storage; unchanged Frozen uses its original getter. Four section layouts, three source frequencies, two callback frequencies; 64 warmups and eight compiler-quiet samples in three interleaved JVMs per side. Scoped CPU/Java allocation, not gameplay or total memory.'
    env = os.environ.copy()
    for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'): env.pop(key, None)
    env.update(JAVA_HOME=str(jdk.parent), PATH=str(jdk) + os.pathsep + env.get('PATH', ''))
    images = {}; classpaths = {}; commands = {}

    def run(command, name, cwd=ROOT):
        with (out / name).open('w') as log:
            subprocess.run(command, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
        return (out / name).read_text()

    try:
        init = out / 'classpath.gradle'
        init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('entityCollectionClasspath') { doLast { println('COLLECTION_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")
        for side, repo in [('current', ROOT), ('frozen', FROZEN)]:
            data = run([str(repo / 'gradlew'), '-q', '-I', str(init), 'entityCollectionClasspath'], side+'-classpath.log', repo)
            cp = next(line.removeprefix('COLLECTION_CP ') for line in data.splitlines() if line.startswith('COLLECTION_CP '))
            classes = out / side; classes.mkdir()
            run([str(jdk / 'javac'), '-cp', cp, '-d', str(classes),
                 str(ROOT / 'DevUtils/tests/world/entity/VerifySectionMembers.java'),
                 str(ROOT / 'DevUtils/tests/world/entity/BenchmarkEntityCollection.java'),
                 str(ROOT / 'DevUtils/tests/world/entity/BenchmarkScreenShakeSources.java')], side+'-compile.log')
            for base in cp.split(os.pathsep):
                for namespace in ('net/minecraft/world/level', 'net/minecraft/world/entity', 'net/minecraft/util'):
                    images.update({str(p): sha(p) for p in (Path(base) / namespace).rglob('*.class')})
            images.update({str(p): sha(p) for p in classes.rglob('*.class')})
            commands[side] = [str(jdk / 'java'), '-Xms1g', '-Xmx1g', '-XX:+UseG1GC', '-Xbatch',
                              '--enable-native-access=ALL-UNNAMED', '-Dmattmc.rust.natives.dir='+str(native.parent),
                              '-cp', str(classes)+os.pathsep+cp, 'BenchmarkScreenShakeSources' if args.screen_shake else 'BenchmarkEntityCollection']
        result['class_images'] = images
        samples = {side: [] for side in ('current', 'frozen', 'control')}; result['samples'] = samples
        result['rejected_jit'] = {}
        for index, side in enumerate(['current', 'frozen', 'control'] * 3):
            data = run(commands['frozen' if side == 'frozen' else 'current']+[side], f'{index}-{side}.log')
            rows = [list(map(int, match.split())) for match in re.findall(r'COLLECTION_TIMING ([0-9 -]+)', data)]
            assert len(rows) == (192 if args.screen_shake else 128) and all(len(row) == 8 and row[7] == 0 for row in rows), (side, 'missing samples or measured JIT', rows)
            result['rejected_jit'][f'{index}-{side}'] = [list(map(int, match.split())) for match in re.findall(r'COLLECTION_REJECTED_JIT ([0-9 -]+)', data)]
            if side != 'frozen': assert 'NATIVE_IMAGE '+pin['native'] in data
            loaded = re.findall(r'CLASS_IMAGE (\w+) (\S+)', data); assert len(loaded) == 4
            for digest, uri in loaded:
                parsed = urlparse(uri); assert parsed.scheme == 'file'
                path = Path(unquote(parsed.path)); assert images[str(path)] == digest == sha(path)
                assert path.is_relative_to(FROZEN if side == 'frozen' else ROOT)
            samples[side].extend(rows)
        result['timings'] = {}
        for case in sorted({tuple(row[:4]) for row in samples['current']}):
            rows = {side: [row for row in values if tuple(row[:4]) == case] for side, values in samples.items()}
            assert len({row[6] for values in rows.values() for row in values}) == 1
            result['timings']['-'.join(map(str, case))] = {
                'median_ms': {side: statistics.median(row[4] for row in values)/1e6 for side, values in rows.items()},
                'median_java_bytes': {side: statistics.median(row[5] for row in values) for side, values in rows.items()}}
        result['passed'] = True
    finally:
        result['guards_pass'] = sources(ROOT) == pin['current'] and sources(FROZEN) == pin['frozen'] and sha(native) == pin['native'] and all(sha(Path(p)) == digest for p, digest in images.items())
        (out / 'results.json').write_text(json.dumps(result, indent=2)+'\n')
    assert result['guards_pass']
    print(json.dumps(result['timings'], indent=2))


if __name__ == '__main__': main()
