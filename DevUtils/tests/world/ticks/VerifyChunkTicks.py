#!/usr/bin/env python3
"""Compare actual LevelTicks/LevelChunkTicks traces across Frozen, pre-change Current and the candidate."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import statistics
import subprocess

ROOT = Path(__file__).resolve().parents[4]
FROZEN = ROOT.parent / 'MattMC_JavaPerfTesting/MattMC'
FROZEN_HEAD = '7a4d181717dc0c7da9086f1a17687dfd522ce417'
DRIVER = ROOT / 'DevUtils/tests/world/ticks/VerifyChunkTicks.java'
# The file the native chunk-tick slice replaces; the control side compiles its
# pre-change source over the candidate classpath.
CONTROL_SOURCES = ['world/ticks/LevelChunkTicks.java', 'world/ticks/LevelTicks.java']


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def candidate_patch_sha():
    """Tracked source diff plus untracked source files, so new files are pinned too."""
    digest = hashlib.sha256(subprocess.check_output(['git', 'diff', 'HEAD', '--', 'src'], cwd=ROOT))
    untracked = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', '--', 'src'], cwd=ROOT, text=True).split()
    for name in sorted(untracked):
        digest.update(name.encode() + b'\0' + (ROOT / name).read_bytes())
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--control-ref', required=True, help='pre-change Current commit for the control side')
    parser.add_argument('--benchmark', action='store_true')
    args = parser.parse_args()
    assert args.java_home, 'JDK 25 required'
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    jdk = Path(args.java_home) / 'bin'
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    frozen_head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=FROZEN, text=True).strip()
    assert frozen_head == FROZEN_HEAD, frozen_head
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=FROZEN)
    control_ref = subprocess.check_output(['git', 'rev-parse', args.control_ref + '^{commit}'], cwd=ROOT, text=True).strip()
    result = {'frozen_head': frozen_head, 'control_ref': control_ref, 'native': sha(native), 'driver': sha(DRIVER),
              'candidate_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
              'candidate_diff_sha256': candidate_patch_sha(),
              'scope': 'Actual LevelTicks.tick/schedule/clearArea/copyAreaFrom/willTickThisTick/hasScheduledTick/count over 64 '
                       'LevelChunkTicks containers: random scheduling, priorities, budget truncation, chunk eligibility toggling, '
                       'callback rescheduling and clearArea during a run, throwing callbacks with retained run state, budgets 0/1/7/200, cross-chunk comparator ties, and unload/reload through pack/unpack; 10 seeds x up to 4000 ticks (seeds 1-5 throw and end at the original own crash). '
                       'No ServerLevel, block behaviour, NBT codec or client.',
              'passed': False}
    env = os.environ.copy()
    for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        env.pop(key, None)
    env.update(JAVA_HOME=str(jdk.parent), PATH=str(jdk) + os.pathsep + env.get('PATH', ''))
    init = output / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('ticksPrintClasspath') { "
                    "doLast { println('TICKS_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")

    def run(command, name, cwd=ROOT):
        with (output / name).open('w') as log:
            status = subprocess.call(command, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT)
        if status:
            raise RuntimeError(f'{name} exited {status}; see {output / name}')
        return (output / name).read_text()

    classpaths, commands = {}, {}
    for side, repo in [('frozen', FROZEN), ('current', ROOT)]:
        data = run([str(repo / 'gradlew'), '-q', '-I', str(init), 'ticksPrintClasspath'], side + '-classpath.log', repo)
        classpaths[side] = next(line.removeprefix('TICKS_CP ') for line in data.splitlines() if line.startswith('TICKS_CP '))
    control_classes = output / 'control-classes'
    control_classes.mkdir()
    sources = []
    for name in CONTROL_SOURCES:
        destination = output / 'control-source' / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(subprocess.check_output(['git', 'show', f'{control_ref}:src/main/java/net/minecraft/{name}'], cwd=ROOT))
        sources.append(str(destination))
    run([str(jdk / 'javac'), '-nowarn', '-cp', classpaths['current'], '-d', str(control_classes)] + sources, 'compile-control.log')
    classpaths['control'] = str(control_classes) + os.pathsep + classpaths['current']
    for side in ('frozen', 'control', 'current'):
        target = output / 'driver' / side
        target.mkdir(parents=True)
        run([str(jdk / 'javac'), '-nowarn', '-cp', classpaths[side], '-d', str(target), str(DRIVER)], f'compile-driver-{side}.log')
        command = [str(jdk / 'java'), '-Xms1g', '-Xmx1g', '-XX:+UseG1GC', '--enable-native-access=ALL-UNNAMED',
                   '-Dmattmc.rust.natives.dir=' + str(native.parent), '-cp', str(target) + os.pathsep + classpaths[side],
                   'net.minecraft.world.ticks.VerifyChunkTicks', side, str(output / f'{side}-trace.txt')]
        commands[side] = command
        data = run(command, f'{side}-run.log')
        trace = re.search(r'TRACE (\w+) (\d+)', data)
        assert trace, f'{side}: no trace'
        result[side] = {'trace': trace.group(1), 'ticks': int(trace.group(2)),
                        'classes': dict(re.findall(r'CLASS (\S+) (\w+)', data))}
    # The control overlay must actually replace LevelChunkTicks.
    assert result['control']['classes']['LevelChunkTicks'] != result['current']['classes']['LevelChunkTicks']
    result['traces_equal'] = len({result[side]['trace'] for side in ('frozen', 'control', 'current')}) == 1
    if not result['traces_equal']:
        for side in ('control', 'current'):
            for line, (a, b) in enumerate(zip((output / 'frozen-trace.txt').open(), (output / f'{side}-trace.txt').open())):
                if a != b:
                    result.setdefault('first_difference', {})[side] = {'line': line, 'frozen': a.strip(), side: b.strip()}
                    break
    if args.benchmark:
        timings = {side: [] for side in commands}
        gameplay = {side: [] for side in commands}
        for round_index in range(3):
            for side in ('frozen', 'control', 'current'):
                data = run(commands[side] + ['benchmark', '5', '10'], f'benchmark-{round_index}-{side}.log')
                timings[side] += [int(ns) for ns in re.findall(r'TIMING \d+ (\d+)', data)]
                data = run(commands[side] + ['benchmark', '5', '10', 'gameplay'], f'benchmark-gameplay-{round_index}-{side}.log')
                gameplay[side] += [int(ns) for ns in re.findall(r'TIMING \d+ (\d+)', data)]
        result['benchmark_gameplay'] = {side: {'samples': len(v), 'median_ns_per_4000_tick_run': statistics.median(v)} for side, v in gameplay.items()}
        result['benchmark'] = {side: {'samples': len(values), 'median_ns_per_4000_tick_run': statistics.median(values),
                                      'min_ns': min(values), 'max_ns': max(values)} for side, values in timings.items()}
        result['benchmark_workload'] = 'one seeded 4000-tick LevelTicks run over 64 containers per sample (no tracing); 5 warmup runs then 10 samples per JVM; 3 alternating rounds'
    result['passed'] = result['traces_equal']
    (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({key: value for key, value in result.items() if key not in ('frozen', 'control', 'current')}, indent=2))
    print('PASS' if result['passed'] else 'FAIL')
    raise SystemExit(0 if result['passed'] else 1)


if __name__ == '__main__':
    main()
