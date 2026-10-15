#!/usr/bin/env python3
"""Temporarily break scoped policy contracts and require the sealed oracle to fail.

Run with exclusive checkout/build ownership and no other builds/tests. Only two
explicit source files are changed. Exact originals are retained in the output;
finally restores them and rebuilds/reruns the positive control. Never commit a
mutation. A known fixture assertion with an actual differing Frozen trace prefix
is a semantic failure; a process crash/build error is not a detected mutation.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess

from VerifyPlayerLoading import DRIVER, ROOT, sha, source_identity

RUST = ROOT / 'src/main/rust/world/level/chunk_distance/player_loading.rs'
JAVA = ROOT / 'src/main/java/net/minecraft/server/level/DistanceManager.java'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--reference-result', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    reference_path = args.reference_result.resolve()
    reference = json.loads(reference_path.read_text())
    assert reference['passed'] and reference['traces_equal']
    assert sha(DRIVER) == reference['driver_sha256'], 'Re-establish the oracle after observer changes'
    expected = reference['frozen']['trace']
    frozen_command = reference['commands']['frozen.log']
    frozen_index = frozen_command.index('net.minecraft.server.level.VerifyPlayerLoading')
    frozen_trace = Path(frozen_command[frozen_index + 2])
    assert sha(frozen_trace) == expected, 'Sealed Frozen trace file changed'
    frozen_lines = frozen_trace.read_bytes().splitlines(keepends=True)
    originals = {path: path.read_bytes() for path in (RUST, JAVA)}
    for path, content in originals.items():
        (output / (path.name + '.original')).write_bytes(content)
    result = {'status': 'incomplete', 'reference_result': str(reference_path), 'expected_trace': expected,
              'original_source_sha256': source_identity(), 'driver_sha256': sha(DRIVER),
              'original_files': {str(path.relative_to(ROOT)): hashlib.sha256(content).hexdigest()
                                 for path, content in originals.items()}, 'controls': []}
    env = os.environ.copy()
    jdk = args.java_home.resolve() / 'bin'
    env.update(JAVA_HOME=str(jdk.parent), PATH=str(jdk) + os.pathsep + env.get('PATH', ''))
    for name in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        env.pop(name, None)

    def save():
        (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')

    def restore():
        for path, original in originals.items():
            if path.read_bytes() != original:
                path.write_bytes(original)

    def replace(path, old, new):
        text = path.read_text()
        assert text.count(old) == 1, (path, old, 'not unique')
        path.write_text(text.replace(old, new))

    def build(label):
        command = [str(ROOT / 'gradlew'), '-PmattmcRustProfile=dev', 'classes']
        with (output / (label + '-build.log')).open('w') as log:
            code = subprocess.call(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
        if code:
            raise RuntimeError(f'{label}: mutation/control build failed ({code}); not an oracle kill')

    def observe(label):
        command = list(reference['commands']['current.log'])
        trace_file = output / (label + '-trace.txt')
        index = command.index('net.minecraft.server.level.VerifyPlayerLoading')
        command[index + 2] = str(trace_file)
        log_path = output / (label + '-run.log')
        with log_path.open('w') as log:
            code = subprocess.call(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
        data = log_path.read_text()
        trace = re.search(r'TRACE (\w+) (\d+)', data)
        coverage = re.search(r'COVERAGE (.+)', data)
        if not trace and code == 1 and trace_file.exists() \
                and 'java.lang.AssertionError: No actual admitted player-loading ticket' in data \
                and 'net.minecraft.server.level.VerifyPlayerLoading.pending(' in data:
            # A strict boundary mutant can fail the real admission invariant
            # before main prints its final digest. Require an independently
            # differing effect prefix too; merely terminating early is no kill.
            lines = trace_file.read_bytes().splitlines(keepends=True)
            prefix = frozen_lines[:len(lines)]
            difference = next(((index, original, actual) for index, (original, actual)
                               in enumerate(zip(prefix, lines), 1) if original != actual), None)
            if difference:
                index, original, actual = difference
                return {'exit': code, 'trace': sha(trace_file), 'events': len(lines),
                        'coverage': 'known actual-admission fixture assertion with differing sealed Frozen effect prefix',
                        'semantic_assertion': 'No actual admitted player-loading ticket',
                        'first_difference': {'line': index, 'frozen': original.decode().rstrip('\n'),
                                             'mutant': actual.decode().rstrip('\n')},
                        'native_sha256': sha(ROOT / 'build/rust/native/mattmc_rust-linux-x64.so')}
        if not trace or not coverage or code not in (0, 1):
            raise RuntimeError(f'{label}: no completed behavioral trace (exit {code}); not an oracle kill')
        return {'exit': code, 'trace': trace[1], 'events': int(trace[2]), 'coverage': coverage[1],
                'native_sha256': sha(ROOT / 'build/rust/native/mattmc_rust-linux-x64.so')}

    def strict_threshold():
        replace(RUST, 'self.level(key) <= self.view_distance', 'self.level(key) < self.view_distance')

    def skip_recheck():
        replace(RUST, 'self.level(key) <= self.view_distance', 'true')

    def early_release():
        replace(JAVA, 'this.policy.markRelease(key);',
                'this.policy.markRelease(key);\n                                DistanceManager.this.ticketDispatcher.release(key, () -> {}, false);')

    def reverse_changes():
        replace(RUST, 'for &(key, level) in changes {', 'for &(key, level) in changes.iter().rev() {')

    def clear_on_failure():
        replace(RUST, '#[cfg(test)]\nmod tests {', '''impl Drop for Walk {
    fn drop(&mut self) {
        if self.kind == 2 {
            if let Source::Loading(owner) = &self.source { owner.lock().releases.clear_membership(); }
        }
    }
}

#[cfg(test)]
mod tests {''')

    def type_equality():
        replace(JAVA, 'ticket.getType() == TicketType.PLAYER_LOADING', 'ticket.getType().equals(TicketType.PLAYER_LOADING)')

    def premature_view_commit():
        replace(RUST, 'let cursor = {\n            let state = owner.lock();',
                'let cursor = {\n            let mut state = owner.lock();\n            if kind == 1 { state.view_distance = target; }')

    controls = [('strict-threshold', strict_threshold), ('skip-admission-recheck', skip_recheck),
                ('release-before-readiness', early_release), ('reverse-change-order', reverse_changes),
                ('clear-releases-after-failure', clear_on_failure), ('type-equality-instead-of-identity', type_equality),
                ('premature-view-commit', premature_view_commit)]
    def interrupted(*_):
        raise KeyboardInterrupt()

    signal.signal(signal.SIGTERM, interrupted)
    try:
        build('initial-positive')
        positive = observe('initial-positive')
        assert positive['exit'] == 0 and positive['trace'] == expected, 'Positive control differs from sealed Frozen trace'
        result['initial_positive'] = positive
        save()
        for label, mutate in controls:
            restore()
            mutate()
            result['active_mutation'] = label
            result['source_restored'] = False
            save()
            build(label)
            observed = observe(label)
            observed.update(name=label, killed=observed['trace'] != expected)
            result['controls'].append(observed)
            print(label, 'killed', observed['killed'], 'trace', observed['trace'], flush=True)
            save()
            assert observed['killed'], f'{label}: oracle did not detect deliberately wrong behavior'
        result['status'] = 'controls-detected'
    except BaseException as error:
        result['status'] = 'failed'
        result['error'] = f'{type(error).__name__}: {error}'
    finally:
        restore()
        result['source_restored'] = all(path.read_bytes() == original for path, original in originals.items())
        result.pop('active_mutation', None)
        try:
            build('restored-positive')
            positive = observe('restored-positive')
            result['restored_positive'] = positive
            result['restored_binary_verified'] = positive['exit'] == 0 and positive['trace'] == expected
        except BaseException as error:
            result['restored_binary_verified'] = False
            result['restore_error'] = f'{type(error).__name__}: {error}'
        if result['status'] == 'controls-detected' and result['source_restored'] and result['restored_binary_verified']:
            result['status'] = 'passed'
        save()
    print(json.dumps({key: value for key, value in result.items() if key != 'controls'}, indent=2))
    return 0 if result['status'] == 'passed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
