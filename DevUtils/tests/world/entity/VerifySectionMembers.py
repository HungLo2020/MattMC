#!/usr/bin/env python3
"""Compare production native section membership with actual untouched Frozen."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[4]
FROZEN = ROOT.parent / 'MattMC_JavaPerfTesting/MattMC'


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def sources(root):
    paths = subprocess.check_output(
        ['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z',
         '--', 'src', 'DevUtils', 'build.gradle'], cwd=root).split(b'\0')
    return {os.fsdecode(p): sha(root / os.fsdecode(p))
            for p in paths if p and (root / os.fsdecode(p)).is_file()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not args.java_home:
        parser.error('--java-home or JAVA_HOME must identify JDK25')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    jdk = Path(args.java_home) / 'bin'
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    prompt = ROOT / 'docs/STANDARD-COPILOT-PROMPTS.md'
    pins = {'current': sources(ROOT), 'frozen': sources(FROZEN),
            'native': sha(native), 'prompt': sha(prompt),
            'frozen_head': subprocess.check_output(
                ['git', '-C', str(FROZEN), 'rev-parse', 'HEAD'], text=True).strip()}
    assert pins['frozen_head'] == '7a4d181717dc0c7da9086f1a17687dfd522ce417'
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=FROZEN)
    assert pins['prompt'] == 'c5695e9bc559fb430bc3ee14721785a28c6de7e0f7cae7d70b2dad0f41cd94c5'
    result = {'pins': pins, 'passed': False, 'guards_pass': False,
              'scope': 'Actual Frozen root/typed membership, callback mutation, captured arrays, '
                       'live iterator/stream/spliterator behavior, and production EntitySection '
                       'bounds/cast/consumer queries; supplemental to gameplay.'}
    receipt = output / 'results.json'
    receipt.write_text(json.dumps(result, indent=2) + '\n')
    env = os.environ.copy()
    env.update(JAVA_HOME=str(Path(args.java_home)),
               PATH=str(jdk) + os.pathsep + env.get('PATH', ''))
    init = output / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { def p=gradle.rootProject; "
                    "p.tasks.register('sectionMembersRuntimeClasspath') { doLast { "
                    "println('MEMBERS_CP '+p.sourceSets.main.runtimeClasspath.asPath) } } }\n")

    def run(command, name, cwd=ROOT):
        with (output / name).open('w') as log:
            status = subprocess.call(command, cwd=cwd, env=env,
                                     stdout=log, stderr=subprocess.STDOUT)
        if status:
            raise RuntimeError(f'{name} exited {status}')
        return (output / name).read_text()

    try:
        counts = {}
        images = {}
        for side, repo in [('frozen', FROZEN), ('current', ROOT)]:
            data = run([str(repo / 'gradlew'), '-q', '-I', str(init),
                        'sectionMembersRuntimeClasspath'], side + '-classpath.log', repo)
            cp = next(line.removeprefix('MEMBERS_CP ') for line in data.splitlines()
                      if line.startswith('MEMBERS_CP '))
            target = output / 'oracle' / side
            target.mkdir(parents=True)
            run([str(jdk / 'javac'), '-cp', cp, '-d', str(target),
                 str(ROOT / 'DevUtils/tests/world/entity/VerifySectionMembers.java')],
                'compile-' + side + '.log')
            # HotSpot can drop messages from repeatedly thrown VM exceptions.
            # Compare full production failure messages on both sides, without
            # normalizing observer output or changing either implementation.
            command = [str(jdk / 'java'), '-XX:-OmitStackTraceInFastThrow']
            if side == 'current':
                command += ['--enable-native-access=ALL-UNNAMED',
                            '-Dmattmc.rust.natives.dir=' + str(native.parent)]
            command += ['-cp', str(target) + os.pathsep + cp, 'VerifySectionMembers',
                        str(output / (side + '-members.txt')), side]
            data = run(command, side + '-record.log')
            match = re.search(r'MEMBERS_CASES (\d+) bytes=(\d+) sha=([0-9a-f]{64})', data)
            assert match, f'{side}: no complete corpus receipt'
            counts[side] = int(match[1])
            assert sha(output / (side + '-members.txt')) == match[3]
            checked = {}
            for line in data.splitlines():
                line = line.split('[STDOUT]: ', 1)[-1]
                if not line.startswith('CLASS '):
                    continue
                _, name, digest = line.split()
                resource = name.replace('.', '/') + '.class'
                path = next(Path(base) / resource for base in cp.split(os.pathsep)
                            if (Path(base) / resource).is_file())
                assert path.is_relative_to(repo) and sha(path) == digest
                checked[str(path)] = digest
            assert len(checked) == (7 if side == 'current' else 2)
            images[side] = checked
            if side == 'current':
                assert 'NATIVE_IMAGE ' + pins['native'] + ' ' + str(native) in data
        assert counts['current'] == counts['frozen'] and counts['current'] >= 5500
        current = (output / 'current-members.txt').read_text().splitlines()
        frozen = (output / 'frozen-members.txt').read_text().splitlines()
        differences = [{'line': i + 1, 'current': a, 'frozen': b}
                       for i, (a, b) in enumerate(zip(current, frozen)) if a != b]
        result['differences'] = differences[:40]
        result['line_counts'] = {'current': len(current), 'frozen': len(frozen)}
        assert not differences and len(current) == len(frozen), 'Production behavior differs; inspect results.json'
        result.update(passed=True, cases=counts['current'], class_images=images,
                      fixture_sha256=sha(output / 'frozen-members.txt'),
                      driver_classes={str(p.relative_to(output / 'oracle')): sha(p)
                                      for p in (output / 'oracle').rglob('*.class')})
    finally:
        result['guards_pass'] = (sources(ROOT) == pins['current']
                                and sources(FROZEN) == pins['frozen']
                                and sha(native) == pins['native']
                                and sha(prompt) == pins['prompt'])
        if 'class_images' in result:
            result['guards_pass'] &= all(sha(Path(p)) == digest
                                        for side in result['class_images'].values()
                                        for p, digest in side.items())
        receipt.write_text(json.dumps(result, indent=2) + '\n')
    assert result['passed'] and result['guards_pass']
    print(f"Actual Frozen section membership {result['cases']} PASS; source/native/class guards PASS")


if __name__ == '__main__':
    main()
