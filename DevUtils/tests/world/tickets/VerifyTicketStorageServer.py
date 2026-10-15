#!/usr/bin/env python3
"""Real dedicated-server check that chunk tickets load, expire and persist.

Starts a disposable flat world on loopback with ticks frozen. Forceloads chunks,
removes one, saves and restarts, then checks with console assertions which chunks
are loaded. That covers the forced-ticket path, the per-tick purge, natively
notified loading/simulation trackers, deactivation on close and reactivation from
the saved ticket list. Run the same driver on the candidate and on a
--class-overlay of pre-change classes; both must pass.

  python3 DevUtils/tests/world/tickets/VerifyTicketStorageServer.py \
    --java /path/to/jdk/bin/java --classpath-file main-classpath.txt \
    --native-dir build/rust/native --output /new/dir [--class-overlay DIR]
"""
import argparse
import json
import os
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'server'))
from VerifyComposterTransfers import Server, sha256  # noqa: E402


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--classpath-file', type=Path, required=True)
    parser.add_argument('--native-dir', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--class-overlay', type=Path)
    parser.add_argument('--port', type=int, default=25587)
    parser.add_argument('--timeout', type=float, default=180)
    args = parser.parse_args()
    java, native = args.java.resolve(strict=True), args.native_dir.resolve(strict=True)
    classpath = os.pathsep.join(str(Path(e).resolve(strict=True)) for e in args.classpath_file.read_text().strip().split(os.pathsep))
    if args.class_overlay:
        classpath = str(args.class_overlay.resolve(strict=True)) + os.pathsep + classpath
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    world_dir = output / 'server'
    world_dir.mkdir()
    properties = {'server-ip': '127.0.0.1', 'server-port': args.port, 'level-name': 'world', 'level-seed': 5,
                  'level-type': 'minecraft:flat', 'generate-structures': 'false', 'view-distance': 2,
                  'simulation-distance': 2, 'pause-when-empty-seconds': 0, 'enable-rcon': 'false',
                  'enable-query': 'false', 'motd': 'Disposable chunk-ticket acceptance'}
    (world_dir / 'server.properties').write_text(''.join(f'{k}={v}\n' for k, v in properties.items()))
    command = [str(java), '-Xmx2G', '-Xms512M', '--enable-native-access=ALL-UNNAMED',
               '-Dmattmc.rust.natives.dir=' + str(native), '-cp', classpath, 'net.minecraft.server.Main', '--nogui']
    server = Server(command, world_dir, output, args.timeout)
    checks = []

    def check(name, condition):
        ok = server.condition(condition)
        checks.append({'check': name, 'condition': condition, 'passed': ok})
        if not ok:
            raise AssertionError(f'{name}: {condition}')

    result = {'status': 'failed', 'driver_sha256': sha256(Path(__file__).resolve()), 'overlay': str(args.class_overlay) if args.class_overlay else None}
    try:
        server.start()
        # Two forced areas far apart, plus one in the Nether to cover a second storage.
        server.command_batch(['forceload add 320 320 351 351', 'forceload add 640 640',
                              'execute in minecraft:the_nether run forceload add 96 96'])
        server.step(40)
        check('forced area loaded', 'if loaded 320 -60 320')
        check('forced area corner loaded', 'if loaded 351 -60 351')
        check('single forced chunk loaded', 'if loaded 640 -60 640')
        check('nether forced chunk loaded', 'in minecraft:the_nether if loaded 96 64 96')
        check('unforced chunk not loaded', 'unless loaded 960 -60 960')
        # Removing a forced ticket must lower the loading level and let it unload.
        server.command_batch(['forceload remove 640 640'])
        server.step(200)
        check('removed forced chunk unloaded', 'unless loaded 640 -60 640')
        check('remaining forced area still loaded', 'if loaded 320 -60 320')
        server.stop(save=True)

        # Saved forced tickets come back deactivated, then are reactivated.
        server.start()
        server.step(40)
        check('forced area reloaded from saved tickets', 'if loaded 320 -60 320')
        check('nether forced chunk reloaded', 'in minecraft:the_nether if loaded 96 64 96')
        check('removed forced chunk stays unloaded', 'unless loaded 640 -60 640')
        server.command_batch(['forceload remove all', 'execute in minecraft:the_nether run forceload remove all'])
        server.step(200)
        check('forceload remove all unloads', 'unless loaded 320 -60 320')
        server.stop(save=True)

        server.start()
        server.step(40)
        check('no forced chunks after remove all and restart', 'unless loaded 320 -60 320')
        server.stop(save=True)
        result['status'] = 'passed'
    except Exception as exc:
        result['error'] = f'{type(exc).__name__}: {exc}'
        print(result['error'], file=sys.stderr, flush=True)
    finally:
        try:
            server.stop(save=True)
        except Exception as exc:
            result['status'] = 'failed'
            result['cleanup_error'] = f'{type(exc).__name__}: {exc}'
        server.commands.close()
        result.update(checks=checks, tick_steps=server.tick_steps, server_exits=server.exits)
        (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'status': result['status'], 'checks': len(checks), 'error': result.get('error'), 'output': str(output)}, indent=2))
    return 0 if result['status'] == 'passed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
