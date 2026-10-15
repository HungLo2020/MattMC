#!/usr/bin/env python3
"""Real dedicated-server check that scheduled block/fluid ticks run and survive save/reopen.

Starts a disposable flat world on loopback with ticks frozen. Places sand over air
(a scheduled block tick makes it fall) and a water source (fluid ticks spread it),
saves while those ticks are still pending, restarts, steps ticks and checks the
results with console assertions. Run the same driver on the candidate and on a
--class-overlay of pre-change classes; both must pass.

  python3 DevUtils/tests/world/ticks/VerifyChunkTicksServer.py \
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
    parser.add_argument('--port', type=int, default=25586)
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
                  'enable-query': 'false', 'motd': 'Disposable scheduled-tick acceptance'}
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

    # Spawn chunks of a flat world: surface air starts at y=-60. Columns at x=2..12, z=2.
    result = {'status': 'failed', 'driver_sha256': sha256(Path(__file__).resolve()), 'overlay': str(args.class_overlay) if args.class_overlay else None}
    try:
        server.start()
        server.command_batch(['forceload add 0 0 31 31'])
        server.step(5)
        # Sand placed over air schedules a block tick (delay 2); water schedules fluid ticks.
        server.command_batch(['setblock 2 -50 2 minecraft:sand', 'setblock 8 -60 2 minecraft:water'])
        server.step(1)
        check('sand tick still pending before save', 'if block 2 -50 2 minecraft:sand')
        check('water has not spread yet', 'unless block 10 -60 2 minecraft:water')
        server.stop(save=True)

        server.start()
        server.step(60)
        check('pending block tick ran after reopen: sand left its spot', 'unless block 2 -50 2 minecraft:sand')
        check('sand landed on the ground', 'if block 2 -60 2 minecraft:sand')
        check('pending fluid ticks ran after reopen: water spread', 'if block 10 -60 2 minecraft:water')
        # A second round within one run: scheduling, reentrant rescheduling by flowing water.
        server.command_batch(['setblock 20 -60 20 minecraft:water'])
        server.step(60)
        check('water spread within one run', 'if block 23 -60 20 minecraft:water')
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
