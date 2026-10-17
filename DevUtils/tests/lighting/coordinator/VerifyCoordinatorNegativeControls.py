#!/usr/bin/env python3
"""Omit actual candidate operations against its exact, unchanged engine observer.

Requires a fresh successful VerifyCoordinatorCandidate engine receipt. Frozen/pre
images and production class files are never changed. Every mutation is isolated.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'threaded'))
from VerifyThreadedLighting import ROOT, images, sha, source_identity


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--positive-result', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    positive = json.loads(args.positive_result.read_text())
    assert positive['passed'] and positive['guards_pass'] and positive['kind'] in ('engine', 'sky-producer')
    sky = positive['kind'] == 'sky-producer'
    if sky: assert 'current' in positive['runs']
    assert source_identity() == positive['source_before']
    assert all(sha(path) == digest for path, digest in positive['class_images'].items())
    original = positive['commands']['current-run.log' if sky else 'run.log']
    classpath = original[original.index('-cp') + 1]
    relative = Path('net/minecraft/world/level/lighting/NativeLightCoordinator.class')
    selected = next(Path(part) / relative for part in classpath.split(os.pathsep)
                    if (Path(part) / relative).is_file())
    library = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    assert sha(library) == positive['native_sha256']
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    tool = out / 'MutateCoordinator.java'
    tool.write_bytes((HERE / tool.name).read_bytes())
    result = {'passed': False, 'guards_pass': False,
              'scope': 'Artifact-only omitted actual operations; unchanged engine observer; no baseline regeneration',
              'positive_result': str(args.positive_result.resolve()),
              'positive_result_sha256': sha(args.positive_result),
              'source_before': source_identity(), 'native_sha256': sha(library),
              'original_class_sha256': sha(selected), 'mutator_sha256': sha(tool),
              'observer_sha256': positive['observer_sha256'], 'kind': positive['kind'], 'controls': {}, 'commands': {}}
    before = images({'current': classpath})
    env = os.environ.copy()
    for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        env.pop(key, None)

    def run(command, log):
        result['commands'][log] = command
        with (out / log).open('w') as stream:
            return subprocess.call(command, cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT)

    try:
        assert run([str(args.java_home / 'bin/javac'), '-cp', classpath,
                    '-d', str(out / 'mutator'), str(tool)], 'compile.log') == 0
        operations = ('seedSky', 'writable') if sky else (
            'queueSectionData', 'updateSectionStatus', 'runLightUpdates',
            'installResults', 'setStored', 'hasLightWork')
        for method in operations:
            # The stock level status producer now enters its retained native
            # frame directly; omit that actual entry rather than a bypassed facade.
            level_methods={'updateSectionStatus':'updateStatus','runLightUpdates':'runUpdates','hasLightWork':'hasWork'}
            target_relative = (Path('net/minecraft/world/level/lighting/NativeLevelLighting.class')
                if method in level_methods else relative)
            actual_method = level_methods.get(method,method)
            actual_selected = next(Path(part) / target_relative for part in classpath.split(os.pathsep)
                if (Path(part) / target_relative).is_file())
            overlay = out / method / 'classes'
            target = overlay / target_relative
            assert run([str(args.java_home / 'bin/java'), '-cp',
                        str(out / 'mutator') + os.pathsep + classpath, 'MutateCoordinator',
                        str(actual_selected), str(target), actual_method], method + '-mutate.log') == 0
            assert sha(target) != sha(actual_selected)
            command = original.copy()
            command[command.index('-cp') + 1] = str(overlay) + os.pathsep + classpath
            trace = out / method / ('traces' if sky else 'trace.txt')
            command[-2 if sky else -1] = str(trace)
            code = run(command, method + '-run.log')
            log = (out / (method + '-run.log')).read_text()
            observed = ({mode: {'rows': len((trace / (mode + '.txt')).read_text().splitlines()),
                                 'sha256': sha(trace / (mode + '.txt'))}
                         for mode in positive['expected']} if sky and code == 0 else
                        sha(trace) if trace.is_file() else None)
            expected = positive['expected'] if sky else positive['current']['sha256']
            # Linkage/descriptor/native-load failures do not prove a semantic kill.
            invalid = any(word in log for word in ('VerifyError', 'LinkageError',
                'NoClassDefFoundError', 'WrongMethodTypeException', 'UnsatisfiedLinkError'))
            assertion = (code != 0 and 'java.lang.AssertionError' in log and
                ('Native complete sky producer admission did not match layer contract' in log
                 if sky else 'VerifyLightCoordinator.java:' in log))
            difference = code == 0 and observed is not None and observed != expected
            # Omitting lifecycle allocation has a precise semantic failure:
            # real propagation reads the section that was never allocated.
            missing_section = (method == 'updateSectionStatus' and code != 0
                and 'java.lang.NullPointerException: Cannot invoke "net.minecraft.world.level.chunk.DataLayer.get(int, int, int)" because "dataLayer" is null' in log
                and 'VerifyLightCoordinator$Fixture.<init>' in log)
            result['controls'][method] = {'exit': code, 'actual_class': str(target_relative),
                'actual_method': actual_method, 'original_class_sha256': sha(actual_selected), 'mutated_class_sha256': sha(target),
                'trace_sha256': observed, 'expected_trace_sha256': expected,
                'observer_assertion': assertion, 'trace_difference': difference, 'missing_section_read': missing_section,
                'invalid_runtime_failure': invalid, 'killed': not invalid and (assertion or difference or missing_section)}
            (out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
        result['passed'] = all(row['killed'] for row in result['controls'].values())
    finally:
        result['guards_pass'] = (source_identity() == result['source_before']
            and sha(library) == result['native_sha256']
            and sha(args.positive_result) == result['positive_result_sha256']
            and sha(tool) == result['mutator_sha256']
            and all(sha(path) == digest for path, digest in before.items())
            and all(sha(out / name / 'classes' / row['actual_class']) == row['mutated_class_sha256']
                    for name, row in result['controls'].items()))
        (out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'passed': result['passed'], 'guards_pass': result['guards_pass'],
                      'controls': {name: row['killed'] for name, row in result['controls'].items()}}))
    assert result['passed'] and result['guards_pass'], str(out / 'result.json')


if __name__ == '__main__':
    main()
