#!/usr/bin/env python3
"""Replay actual Frozen ServerLevel random ticks and measure the whole selection loop."""
import argparse
import json
import os
from pathlib import Path
import re
import statistics
import subprocess
from VerifyBlockCollisionScan import ROOT, FROZEN, sha, sources


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--benchmark', action='store_true')
    parser.add_argument('--java-control-ref', help='Optional prior Current Level/ServerLevel selection, over candidate native storage; not a Frozen baseline')
    args = parser.parse_args()
    assert args.java_home, 'JDK 25 required'
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    jdk = Path(args.java_home) / 'bin'
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    pins = {'current': sources(ROOT), 'frozen': sources(FROZEN), 'native': sha(native),
            'prompt': sha(ROOT / 'docs/STANDARD-COPILOT-PROMPTS.md'),
            'frozen_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=FROZEN, text=True).strip()}
    assert pins['frozen_head'] == '7a4d181717dc0c7da9086f1a17687dfd522ce417'
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=FROZEN)
    result = {'pins': pins, 'passed': False, 'guards_pass': False,
              'scope': 'Production ServerLevel.tickChunk with actual palettes/counters/NetherWart callbacks in disconnected world shells; does not establish whole-game parity.'}
    env = os.environ.copy()
    for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        env.pop(key, None)
    env.update(JAVA_HOME=str(jdk.parent), PATH=str(jdk) + os.pathsep + env.get('PATH', ''))
    init = output / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('randomTickPrintClasspath') { doLast { println('TICK_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")

    def run(command, name, cwd=ROOT):
        with (output / name).open('w') as log:
            status = subprocess.call(command, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT)
        if status:
            raise RuntimeError(f'{name} exited {status}')
        return (output / name).read_text()

    commands, images, classpaths, targets = {}, {}, {}, {}
    try:
        for side, repo in [('frozen', FROZEN), ('current', ROOT)]:
            data = run([str(repo / 'gradlew'), '-q', '-I', str(init), 'randomTickPrintClasspath'], side + '-classpath.log', repo)
            cp = next(line.removeprefix('TICK_CP ') for line in data.splitlines() if line.startswith('TICK_CP '))
            target = output / 'oracle' / side
            target.mkdir(parents=True)
            classpaths[side], targets[side] = cp, target
            run([str(jdk / 'javac'), '-cp', cp, '-d', str(target),
                 str(ROOT / 'DevUtils/tests/world/chunk/VerifyCameraEnvironment.java'),
                 str(ROOT / 'DevUtils/tests/world/chunk/VerifyRandomTicks.java')], 'compile-' + side + '.log')
            command = [str(jdk / 'java'), '-Xms1g', '-Xmx1g', '-XX:+UseG1GC', '-Xbatch']
            if side == 'current':
                command += ['--enable-native-access=ALL-UNNAMED', '-Dmattmc.rust.natives.dir=' + str(native.parent)]
            command += ['-cp', str(target) + os.pathsep + cp, 'net.minecraft.client.VerifyRandomTicks', side, str(output / 'frozen-ticks.bin.gz')]
            commands[side] = command
            data = run(command, side + '-replay.log')
            assert 'TICK_CASES 600' in data
            checked = {}
            for name, digest in re.findall(r'CLASS (\S+) (\w+)', data):
                resource = name.replace('.', '/') + '.class'
                path = next(Path(base) / resource for base in cp.split(os.pathsep) if (Path(base) / resource).is_file())
                assert path.is_relative_to(repo) and sha(path) == digest
                checked[str(path)] = digest
            assert len(checked) == (9 if side == 'current' else 8)
            images[side] = checked
            if side == 'current':
                assert 'NATIVE_IMAGE ' + pins['native'] + ' ' + str(native) in data
        if args.java_control_ref:
            ref = subprocess.check_output(['git', 'rev-parse', args.java_control_ref + '^{commit}'], cwd=ROOT, text=True).strip()
            target = output / 'control-classes'
            target.mkdir()
            control_sources = []
            for name in ('world/level/Level.java', 'server/level/ServerLevel.java'):
                path = 'src/main/java/net/minecraft/' + name
                destination = output / 'control-source' / name
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(subprocess.check_output(['git', 'show', ref + ':' + path], cwd=ROOT))
                control_sources.append(destination)
            run([str(jdk / 'javac'), '-cp', classpaths['current'], '-d', str(target)] + [str(p) for p in control_sources], 'compile-control.log')
            command = [str(jdk / 'java'), '-Xms1g', '-Xmx1g', '-XX:+UseG1GC', '-Xbatch', '--enable-native-access=ALL-UNNAMED',
                       '-Dmattmc.rust.natives.dir=' + str(native.parent), '-cp',
                       os.pathsep.join([str(target), str(targets['current']), classpaths['current']]),
                       'net.minecraft.client.VerifyRandomTicks', 'control', str(output / 'frozen-ticks.bin.gz')]
            commands['control'] = command
            data = run(command, 'control-replay.log')
            assert 'TICK_CASES 600' in data and 'NATIVE_IMAGE ' + pins['native'] in data
            checked = {}
            for name, digest in re.findall(r'CLASS (\S+) (\w+)', data):
                resource = name.replace('.', '/') + '.class'
                path = next(Path(base) / resource for base in [str(target)] + classpaths['current'].split(os.pathsep) if (Path(base) / resource).is_file())
                assert sha(path) == digest
                checked[str(path)] = digest
            assert len(checked) == 8
            images['control'] = checked
            result['java_control'] = {'ref': ref, 'sources': {str(p): sha(p) for p in control_sources},
                                      'scope': 'Prior Current Level/ServerLevel loop over candidate native storage and remaining Current classes; internal causal control.'}
        result.update(replay_passed=True, cases=600, fixture_sha256=sha(output / 'frozen-ticks.bin.gz'), class_images=images)
        if args.benchmark:
            sides = ['current', 'frozen'] + (['control'] if args.java_control_ref else [])
            samples = {side: [] for side in sides}
            for index, side in enumerate(sides * 3):
                data = run(commands[side] + ['benchmark'], f'benchmark-{index}-{side}.log')
                rows = [list(map(int, row)) for row in re.findall(r'TICK_TIMING (\d+) (\d+) (\d+) (-?\d+) (\d+)', data)]
                assert len(rows) == 24 and all(row[4] == 0 for row in rows), (side, 'missing or compiling samples', rows)
                samples[side].extend(rows)
            for density in (4096, 16, 1):
                for side in sides[1:]:
                    assert [r[3] for r in samples['current'] if r[0] == density] == [r[3] for r in samples[side] if r[0] == density]
            result['benchmark'] = {
                'scope': '4096 production tickChunk calls/sample, 24 sections, speed3; sparse/1-in-16/full NetherWart. Real callbacks receive a controlled RandomSource returning non-growth draws. No world placement or GPU.',
                'protocol': 'Three alternating rounds per side with matching Xbatch/G1/1g; 96 warmups, eight measured samples per density; reject measured compilation.',
                'samples': samples,
                'medians': {density: {side: {'ms': statistics.median(r[1] for r in rows if r[0] == density) / 1e6,
                                           'java_bytes': statistics.median(r[2] for r in rows if r[0] == density)}
                                      for side, rows in samples.items()} for density in (4096, 16, 1)}}
        result['passed'] = True
    finally:
        result['guards_pass'] = (sources(ROOT) == pins['current'] and sources(FROZEN) == pins['frozen']
                                and sha(native) == pins['native'] and sha(ROOT / 'docs/STANDARD-COPILOT-PROMPTS.md') == pins['prompt']
                                and not subprocess.check_output(['git', 'status', '--porcelain'], cwd=FROZEN)
                                and all(sha(Path(path)) == digest for side in images.values() for path, digest in side.items()))
        (output / 'results.json').write_text(json.dumps(result, indent=2) + '\n')
    assert result['passed'] and result['guards_pass']
    print('Actual Frozen random-tick cases600 PASS; source/native/class guards PASS')
    if args.benchmark:
        print(json.dumps(result['benchmark']['medians'], indent=2))


if __name__ == '__main__':
    main()
