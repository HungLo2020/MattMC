#!/usr/bin/env python3
"""Real dedicated-server player movement, boundary lighting, unload and reopen.

Reuses the existing player-loading lifecycle observer unchanged; adds counters
proving the actual threaded coordinator ran. Test agents are observational,
enabled only for this correctness gate. No networking/client/FPS/timing claim.
"""
import json
import os
from pathlib import Path
import signal
import subprocess
from VerifyThreadedLighting import Campaign, HERE, ROOT, arguments, sha

LIFECYCLE = ROOT / 'DevUtils/tests/world/playerloading'
SOURCES = [LIFECYCLE / name for name in ('PlayerLoadingServerAgent.java', 'PlayerLoadingServerAudit.java')]
SOURCES += [HERE / name for name in ('ThreadedLightingServerAgent.java', 'ThreadedLightingServerAudit.java')]


def main():
    parser = arguments(__doc__)
    parser.add_argument('--sides', default='frozen,control,current')
    parser.add_argument('--timeout', type=int, default=900)
    args = parser.parse_args()
    campaign = Campaign(args)
    campaign.result.update(scope=__doc__, runs={}, observer_sources={str(p): sha(p) for p in SOURCES})
    try:
        for side in args.sides.split(','):
            assert side in campaign.classpaths
            classes = campaign.output / (side + '-agent-classes')
            classes.mkdir()
            campaign.run([str(campaign.jdk / 'javac'), '-nowarn', '-cp', campaign.classpaths[side], '-d', str(classes),
                          *map(str, SOURCES)], side + '-agent-compile.log')
            manifest = campaign.output / (side + '-MANIFEST.MF')
            manifest.write_text('Premain-Class: threadedlighting.ThreadedLightingServerAgent\n\n')
            jar = campaign.output / (side + '-agent.jar')
            campaign.run([str(campaign.jdk / 'jar'), 'cfm', str(jar), str(manifest), '-C', str(classes), '.'], side + '-agent-jar.log')
            jar_sha = sha(jar)
            world = campaign.output / side / 'server'
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
                target = campaign.output / side / phase
                target.mkdir()
                command = [str(campaign.jdk / 'java'), '-Xms512m', '-Xmx3g', '-XX:+UseG1GC',
                    '--enable-native-access=ALL-UNNAMED', '-Dmax.bg.threads=2',
                    '-Dmattmc.rust.natives.dir=' + str(campaign.native_dir(side)),
                    '-DplayerLoading.output=' + str(target), '-DplayerLoading.reopen=' + str(phase == 'reopen').lower(),
                    '-DplayerLoading.expectNative=' + str(side == 'current').lower(),
                    '-DthreadedLight.expectNative=' + str(side == 'current').lower(),
                    '-javaagent:' + str(jar), '-cp', campaign.classpaths[side],
                    'net.minecraft.server.Main', '--nogui', '--port', '0']
                campaign.result['commands'][side + '/' + phase] = command
                with (target / 'server.log').open('w') as log:
                    process = subprocess.Popen(command, cwd=world, env=campaign.env, stdout=log, stderr=subprocess.STDOUT,
                                               start_new_session=True)
                    try:
                        code = process.wait(timeout=args.timeout)
                    finally:
                        if process.poll() is None:
                            try:
                                os.killpg(process.pid, signal.SIGTERM)
                            except ProcessLookupError:
                                pass
                            try:
                                process.wait(timeout=15)
                            except subprocess.TimeoutExpired:
                                os.killpg(process.pid, signal.SIGKILL)
                                process.wait()
                audit = json.loads((target / 'result.json').read_text())
                lighting = json.loads((target / 'threaded-lighting.json').read_text())
                campaign.result['runs'][side + '/' + phase] = {'exit': code, 'audit': audit, 'lighting': lighting}
                campaign.save()
                assert code == 0 and audit['status'] == 'passed' and not audit.get('cleanupError'), (side, phase, 'lifecycle failure')
                assert lighting['passed'] and sha(jar) == jar_sha, (side, phase, 'coordinator nonvacuity/image failure')
                print(side, phase, 'PASS', len(audit['checks']), 'checks', lighting, flush=True)
        common = {}
        for name, data in campaign.result['runs'].items():
            phase = name.split('/')[1]
            checks = [(row['check'], row['passed']) for row in data['audit']['checks'] if not row['check'].startswith('actual native')]
            if phase in common:
                assert common[phase] == checks, (name, 'different lifecycle checks')
            else:
                common[phase] = checks
        assert all(sha(Path(p)) == digest for p, digest in campaign.result['observer_sources'].items())
        campaign.result['passed'] = True
    finally:
        campaign.seal()
    assert campaign.result['passed'] and campaign.result['guards_pass']


if __name__ == '__main__':
    main()
