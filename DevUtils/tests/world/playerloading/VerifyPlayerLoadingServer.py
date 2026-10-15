#!/usr/bin/env python3
"""Actual dedicated-server players, chunk loading, effects, unload and reopen.

The test agent observes real entry points and attaches real ServerPlayer objects
through PlayerList with an in-memory packet sink. No networking/client claim.
Frozen, actual pre-change classes and Current must pass the same lifecycle;
Current additionally proves actual native admission/recheck execution.
"""
import argparse
import json
import os
from pathlib import Path
import signal
import subprocess

from VerifyPlayerLoading import CONTROL_SOURCES, DRIVER, FROZEN, FROZEN_HEAD, ROOT, sha, source_identity

SOURCES = [DRIVER.with_name(name) for name in ('PlayerLoadingServerAgent.java', 'PlayerLoadingServerAudit.java')]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--frozen-classpath-file', type=Path, required=True)
    parser.add_argument('--current-classpath-file', type=Path, required=True)
    parser.add_argument('--control-ref', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--sides', default='frozen,control,current')
    parser.add_argument('--timeout', type=int, default=900)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    jdk = args.java_home.resolve() / 'bin'
    native = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=FROZEN, text=True).strip() == FROZEN_HEAD
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=FROZEN)
    base = subprocess.check_output(['git', 'rev-parse', args.control_ref + '^{commit}'], cwd=ROOT, text=True).strip()
    result = {'status': 'failed', 'frozen_head': FROZEN_HEAD, 'control_ref': base,
              'candidate_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
              'candidate_source_sha256': source_identity(), 'native_sha256': sha(native),
              'observer_sha256': {source.name: sha(source) for source in SOURCES},
              'scope': 'Real DedicatedServer/ServerLevel, actual PlayerList/ServerPlayer/chunk paths; packet sink only. '
                       'Not network/client/FPS or exact live RNG evidence.', 'runs': {}}
    env = os.environ.copy()
    for name in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        env.pop(name, None)
    env.update(JAVA_HOME=str(jdk.parent), PATH=str(jdk) + os.pathsep + env.get('PATH', ''))
    classpaths = {'frozen': args.frozen_classpath_file.read_text().strip(),
                 'current': args.current_classpath_file.read_text().strip()}

    def run(command, log):
        with log.open('w') as stream:
            code = subprocess.call(command, cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT)
        if code:
            raise RuntimeError(f'{log}: exit {code}')

    try:
        control = output / 'control-classes'
        control.mkdir()
        sources = []
        for name in CONTROL_SOURCES:
            target = output / 'control-source' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(subprocess.check_output(['git', 'show', f'{base}:src/main/java/net/minecraft/{name}'], cwd=ROOT))
            sources.append(str(target))
        run([str(jdk / 'javac'), '-nowarn', '-cp', classpaths['current'], '-d', str(control), *sources], output / 'control-compile.log')
        classpaths['control'] = str(control) + os.pathsep + classpaths['current']
        for side in args.sides.split(','):
            assert side in classpaths, side
            classes = output / (side + '-agent-classes')
            classes.mkdir()
            run([str(jdk / 'javac'), '-nowarn', '-cp', classpaths[side], '-d', str(classes), *map(str, SOURCES)], output / (side + '-compile.log'))
            manifest = output / (side + '-MANIFEST.MF')
            manifest.write_text('Premain-Class: playerloading.PlayerLoadingServerAgent\n\n')
            jar = output / (side + '-agent.jar')
            run([str(jdk / 'jar'), 'cfm', str(jar), str(manifest), '-C', str(classes), '.'], output / (side + '-jar.log'))
            world = output / side / 'server'
            world.mkdir(parents=True)
            (world / 'eula.txt').write_text('eula=true\n')
            (world / 'server.properties').write_text('''level-seed=5
level-name=world
level-type=minecraft:flat
generate-structures=false
server-ip=127.0.0.1
server-port=0
online-mode=false
enforce-secure-profile=false
view-distance=2
simulation-distance=2
max-tick-time=-1
pause-when-empty-seconds=-1
enable-rcon=false
enable-query=false
spawn-protection=0
''')
            for phase in ('initial', 'reopen'):
                target = output / side / phase
                target.mkdir()
                command = [str(jdk / 'java'), '-Xms512m', '-Xmx3g', '-XX:+UseG1GC',
                           '--enable-native-access=ALL-UNNAMED', '-Dmax.bg.threads=2',
                           '-Dmattmc.rust.natives.dir=' + str(native.parent),
                           '-DplayerLoading.output=' + str(target),
                           '-DplayerLoading.reopen=' + str(phase == 'reopen').lower(),
                           '-DplayerLoading.expectNative=' + str(side == 'current').lower(),
                           '-javaagent:' + str(jar), '-cp', classpaths[side],
                           'net.minecraft.server.Main', '--nogui', '--port', '0']
                (target / 'command.json').write_text(json.dumps(command, indent=2))
                with (target / 'server.log').open('w') as log:
                    process = subprocess.Popen(command, cwd=world, env=env, stdout=log, stderr=subprocess.STDOUT,
                                               start_new_session=True)
                    try:
                        code = process.wait(timeout=args.timeout)
                    except subprocess.TimeoutExpired:
                        raise RuntimeError(f'{side}/{phase}: owned server timeout; logs retained')
                    finally:
                        # The server owns a separate process group. An interrupted
                        # observer must retire it too, not leave it after Ctrl-C.
                        if process.poll() is None:
                            try:
                                os.killpg(process.pid, signal.SIGTERM)
                            except ProcessLookupError:
                                pass
                            try:
                                process.wait(timeout=15)
                            except subprocess.TimeoutExpired:
                                try:
                                    os.killpg(process.pid, signal.SIGKILL)
                                except ProcessLookupError:
                                    pass
                                process.wait()
                audit = json.loads((target / 'result.json').read_text()) if (target / 'result.json').exists() else {}
                result['runs'][side + '/' + phase] = {'exit': code, 'audit': audit}
                (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
                print(side, phase, 'exit', code, 'status', audit.get('status'), 'checks', len(audit.get('checks', [])), flush=True)
                if code or audit.get('status') != 'passed' or audit.get('cleanupError'):
                    raise RuntimeError(f'{side}/{phase} failed; see {target}')
        # Asynchronous server scheduling is not a byte-for-byte event oracle.
        # Compare actual common checks; candidate-only native route controls are
        # separately mandatory and all coverage details remain unnormalized.
        common = {}
        for name, data in result['runs'].items():
            phase = name.split('/')[1]
            checks = [(item['check'], item['passed']) for item in data['audit']['checks']
                      if not item['check'].startswith('actual native')]
            if phase in common:
                assert common[phase] == checks, (name, common[phase], checks)
            else:
                common[phase] = checks
        result['inputs_unchanged'] = source_identity() == result['candidate_source_sha256'] and sha(native) == result['native_sha256']
        assert result['inputs_unchanged'], 'Candidate inputs changed during lifecycle'
        result['status'] = 'passed'
    except Exception as error:
        result['error'] = f'{type(error).__name__}: {error}'
    (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'status': result['status'], 'error': result.get('error'), 'output': str(output)}, indent=2))
    return 0 if result['status'] == 'passed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
