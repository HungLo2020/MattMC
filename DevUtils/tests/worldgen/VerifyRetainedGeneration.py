#!/usr/bin/env python3
"""Compare complete NOISE/SURFACE output against untouched Frozen classes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[3]
FROZEN = ROOT.parent / 'MattMC_JavaPerfTesting/MattMC'


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def sources(root):
    paths = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z', '--', 'src', 'DevUtils', 'build.gradle'], cwd=root).split(b'\0')
    return {os.fsdecode(p): sha(root / os.fsdecode(p)) for p in paths if p and (root / os.fsdecode(p)).is_file()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    jdk = Path(args.java_home) / 'bin'
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    pins = {'current': sources(ROOT), 'frozen': sources(FROZEN), 'native': sha(native)}
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=FROZEN, text=True).strip() == '7a4d181717dc0c7da9086f1a17687dfd522ce417'
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=FROZEN)
    result = {'passed': False, 'pins': pins, 'scope': '24 actual NOISE/SURFACE chunks: three settings, two seeds, four positions; full block IDs, counter predicates, heightmaps and postprocessing order. No gameplay or CPU claim.'}
    receipt = out / 'results.json'
    def save():
        receipt.write_text(json.dumps(result, indent=2) + '\n')
    save()
    env = {**os.environ, 'JAVA_HOME': str(jdk.parent), 'PATH': str(jdk) + os.pathsep + os.environ.get('PATH', '')}
    init = out / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; p.tasks.register('retainedGenerationClasspath') { doLast { println('GEN_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")
    def run(command, name, cwd):
        with (out / name).open('w') as log:
            status = subprocess.call(command, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT)
        assert status == 0, f'{name} exit {status}'
        return (out / name).read_text()
    try:
        images = {}
        for side, repo in [('frozen', FROZEN), ('current', ROOT)]:
            data = run([str(repo / 'gradlew'), '-q', '-I', str(init), 'retainedGenerationClasspath'], side + '-classpath.log', repo)
            cp = next(line.removeprefix('GEN_CP ') for line in data.splitlines() if line.startswith('GEN_CP '))
            target = out / side
            target.mkdir()
            run([str(jdk / 'javac'), '-cp', cp, '-d', str(target), str(ROOT / 'DevUtils/tests/worldgen/VerifyRetainedGeneration.java')], side + '-compile.log', repo)
            command = [str(jdk / 'java'), '-Xms1g', '-Xmx2g', '-XX:+UseG1GC']
            if side == 'current':
                command += ['--enable-native-access=ALL-UNNAMED', '-Dmattmc.rust.natives.dir=' + str(native.parent)]
            command += ['-cp', str(target) + os.pathsep + cp, 'net.minecraft.world.level.levelgen.VerifyRetainedGeneration', side, str(out / (side + '.txt'))]
            data = run(command, side + '.log', repo)
            assert 'GENERATION_CASES 24' in data
            checked = {}
            for line in data.splitlines():
                line = line.split('[STDOUT]: ', 1)[-1]
                if line.startswith('CLASS '):
                    _, name, digest = line.split()
                    resource = name.replace('.', '/') + '.class'
                    path = next(Path(base) / resource for base in cp.split(os.pathsep) if (Path(base) / resource).is_file())
                    assert path.is_relative_to(repo) and sha(path) == digest
                    checked[str(path)] = digest
            assert len(checked) == (10 if side == 'current' else 6)
            images[side] = checked
            if side == 'current':
                maps = [line.split()[-1] for line in data.splitlines() if 'NATIVE_MAP ' in line]
                assert maps and all(Path(path).resolve() == native.resolve() for path in maps)
            print(side, '24 generation cases complete', flush=True)
        a = (out / 'frozen.txt').read_text().splitlines()
        b = (out / 'current.txt').read_text().splitlines()
        result['differences'] = [{'frozen': x, 'current': y} for x, y in zip(a, b) if x != y]
        result['classes'] = images
        assert len(a) == len(b) == 24 and not result['differences'], 'Generated output mismatch'
        result['passed'] = True
    finally:
        result['guards_pass'] = pins == {'current': sources(ROOT), 'frozen': sources(FROZEN), 'native': sha(native)}
        save()
    assert result['guards_pass']
    print('Frozen generation comparison and identity guards PASS', flush=True)


if __name__ == '__main__':
    main()
