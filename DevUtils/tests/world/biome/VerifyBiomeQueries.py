#!/usr/bin/env python3
"""Compare live biome coordinate selection with the untouched Frozen manager."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import statistics

ROOT = Path(__file__).resolve().parents[4]
FROZEN = ROOT.parent / 'MattMC_JavaPerfTesting/MattMC'


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def sources(root):
    paths = subprocess.check_output(
        ['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z', '--', 'src', 'DevUtils', 'build.gradle'], cwd=root).split(b'\0')
    return {os.fsdecode(p): sha(root / os.fsdecode(p)) for p in paths if p and (root / os.fsdecode(p)).is_file()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--output', type=Path, default=ROOT / 'build/native-biome-query/replay')
    parser.add_argument("--benchmark", action="store_true", help="Six interleaved scalar-manager JVMs after the oracle; excludes direct world lookup/gameplay")
    args = parser.parse_args()
    assert args.java_home, 'JDK25 required'
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    jdk = Path(args.java_home) / 'bin'
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    pins = {'current': sources(ROOT), 'frozen': sources(FROZEN), 'native': sha(native),
            'prompt': sha(ROOT / 'docs/STANDARD-COPILOT-PROMPTS.md'),
            'frozen_head': subprocess.check_output(['git', '-C', str(FROZEN), 'rev-parse', 'HEAD'], text=True).strip()}
    assert pins['frozen_head'] == '7a4d181717dc0c7da9086f1a17687dfd522ce417'
    assert pins['prompt'] == 'c5695e9bc559fb430bc3ee14721785a28c6de7e0f7cae7d70b2dad0f41cd94c5'
    result = {'pins': pins, 'passed': False, 'guards_pass': False,
              'scope': 'Actual Frozen biome selection over full-range random coordinates and signed boundary cubes; source callback count and exception identity. Supplemental to gameplay.'}
    receipt = output / 'results.json'
    receipt.write_text(json.dumps(result, indent=2) + '\n')
    env = os.environ.copy()
    env.update(JAVA_HOME=str(Path(args.java_home)), PATH=str(jdk) + os.pathsep + env.get('PATH', ''))
    classpaths = {}
    init = output / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('biomePrintRuntimeClasspath') { doLast { println('BIOME_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")

    def run(command, name, cwd=ROOT):
        with (output / name).open('w') as log:
            status = subprocess.call(command, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT)
        if status:
            raise RuntimeError(f'{name} exited {status}')
        return (output / name).read_text()

    try:
        outputs = {}
        for side, repo in [('frozen', FROZEN), ('current', ROOT)]:
            data = run([str(repo / 'gradlew'), '-q', '-I', str(init), 'biomePrintRuntimeClasspath'], side + '-classpath.log', repo)
            classpaths[side] = next(line.removeprefix('BIOME_CP ') for line in data.splitlines() if line.startswith('BIOME_CP '))
            target = output / 'oracle' / side
            target.mkdir(parents=True, exist_ok=True)
            run([str(jdk / 'javac'), '-cp', classpaths[side], '-d', str(target), str(ROOT / 'DevUtils/tests/world/biome/VerifyBiomeQueries.java')], 'compile-' + side + '.log')
            command = [str(jdk / 'java')]
            if side == 'current':
                command += ['--enable-native-access=ALL-UNNAMED', '-Dmattmc.rust.natives.dir=' + str(native.parent)]
            command += ['-cp', str(target) + os.pathsep + classpaths[side], 'VerifyBiomeQueries', side, str(output / 'frozen-biomes.bin.gz')]
            outputs[side] = run(command, 'record-frozen.log' if side == 'frozen' else 'replay-native.log')
            assert 'BIOME_CASES 162701' in outputs[side]
        images = {}
        for side, data in outputs.items():
            checked = {}
            repo = ROOT if side == 'current' else FROZEN
            for line in data.splitlines():
                line = line.split('[STDOUT]: ', 1)[-1]
                if not line.startswith('CLASS '):
                    continue
                _, name, digest = line.split()
                resource = name.replace('.', '/') + '.class'
                path = next(Path(base) / resource for base in classpaths[side].split(os.pathsep) if (Path(base) / resource).is_file())
                assert path.is_relative_to(repo) and sha(path) == digest
                checked[str(path)] = digest
            assert len(checked) == (4 if side == "current" else 3)
            images[side] = checked
        result['class_images'] = images
        result['driver_classes'] = {str(p.relative_to(output / 'oracle')): sha(p) for p in (output / 'oracle').rglob('*.class')}
        assert 'NATIVE_IMAGE ' + pins['native'] + ' ' + str(native) in outputs['current']
        result.update(passed=True, cases=162701, fixture_sha256=sha(output / 'frozen-biomes.bin.gz'))
        if args.benchmark:
            for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
                env.pop(key, None)
            for side in ('frozen', 'current'):
                run([str(jdk / 'javac'), '-cp', classpaths[side], '-d', str(output / 'oracle' / side),
                     str(ROOT / 'DevUtils/tests/world/biome/BenchmarkBiomeQueries.java')], 'compile-benchmark-' + side + '.log')
            timings = {'current': [], 'frozen': []}
            for index, side in enumerate(['current', 'frozen'] * 3):
                command = [str(jdk / 'java'), '-Xms1g', '-Xmx1g', '-XX:+UseG1GC']
                if side == 'current':
                    command += ['--enable-native-access=ALL-UNNAMED', '-Dmattmc.rust.natives.dir=' + str(native.parent)]
                command += ['-cp', str(output / 'oracle' / side) + os.pathsep + classpaths[side], 'BenchmarkBiomeQueries']
                data = run(command, f'benchmark-{index}-{side}.log')
                samples = [list(map(int, line.split()[1:])) for line in data.splitlines() if line.startswith('BIOME_TIMING ')]
                assert len(samples) == 8
                timings[side].extend(samples)
            assert len({row[2] for rows in timings.values() for row in rows}) == 1
            result['scalar_benchmark'] = {
                'scope': '1048576 production-manager queries per sample, prebuilt full-range positions, scalar custom source; excludes direct retained-world lookup, GPU, and gameplay.',
                'protocol': 'Six interleaved JVMs, five warmup and eight measured samples per JVM.',
                'samples': timings,
                'median_ms': {side: statistics.median(row[0] for row in rows) / 1e6 for side, rows in timings.items()},
                'median_java_bytes': {side: statistics.median(row[1] for row in rows) for side, rows in timings.items()},
            }

    finally:
        result['guards_pass'] = sources(ROOT) == pins['current'] and sources(FROZEN) == pins['frozen'] and sha(native) == pins['native'] and sha(ROOT / 'docs/STANDARD-COPILOT-PROMPTS.md') == pins['prompt']
        if 'class_images' in result:
            result['guards_pass'] &= all(sha(Path(p)) == digest for side in result['class_images'].values() for p, digest in side.items())
        receipt.write_text(json.dumps(result, indent=2) + '\n')
    assert result['passed'] and result['guards_pass']
    print('Actual Frozen biome queries162701 PASS;source/native/class guards PASS')


if __name__ == '__main__':
    main()
