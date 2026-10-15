#!/usr/bin/env python3
"""Real dedicated-server check of chunk holder scheduling, unload, save and reload.

Starts a disposable flat world on loopback with ticks frozen. Forceloads distant
chunks, writes blocks in them, releases them so they unload, and checks that the
unload saved them and they reload; releases and re-adds a chunk so its pending
holder is revived; saves, restarts and checks persistence. Run the same driver on
the candidate and on a --class-overlay of pre-change classes; both must pass.

--inject-readonly-region adds a separately reported experiment (not a gate): a
read-only region directory during a save into a new region file. Writes into
already-open region files ignore permissions, so live write-failure injection is
otherwise unavailable; ChunkMap's save-failure handling is covered by
VerifyChunkHolders.py, and IOWorker recovery by its unit tests.

  python3 DevUtils/tests/world/chunkholders/VerifyChunkHoldersServer.py \
    --java /path/to/jdk/bin/java --classpath-file main-classpath.txt \
    --native-dir build/rust/native --output /new/dir [--class-overlay DIR]
"""
import argparse
import json
import os
from pathlib import Path
import stat
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
    parser.add_argument('--port', type=int, default=25589)
    parser.add_argument('--timeout', type=float, default=180)
    parser.add_argument('--inject-readonly-region', action='store_true',
                        help='experiment, reported separately: lock the region directory and force a save into a new region file')
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
                  'enable-query': 'false', 'motd': 'Disposable chunk-holder acceptance'}
    (world_dir / 'server.properties').write_text(''.join(f'{k}={v}\n' for k, v in properties.items()))
    command = [str(java), '-Xmx2G', '-Xms512M', '--enable-native-access=ALL-UNNAMED',
               '-Dmattmc.rust.natives.dir=' + str(native), '-cp', classpath, 'net.minecraft.server.Main', '--nogui']
    server = Server(command, world_dir, output, args.timeout)
    checks = []
    region = world_dir / 'world' / 'region'
    locked = []

    def check(name, condition):
        ok = server.condition(condition)
        checks.append({'check': name, 'condition': condition, 'passed': ok})
        if not ok:
            raise AssertionError(f'{name}: {condition}')

    def console_has(text):
        return any(text in line for line in (output / f'console-{server.run}.log').read_text().splitlines())

    def lock(path):
        locked.append((path, path.stat().st_mode))
        path.chmod(stat.S_IRUSR | stat.S_IRGRP | (stat.S_IXUSR | stat.S_IXGRP if path.is_dir() else 0))

    def unlock():
        while locked:
            path, mode = locked.pop()
            path.chmod(mode)

    result = {'status': 'failed', 'driver_sha256': sha256(Path(__file__).resolve()), 'overlay': str(args.class_overlay) if args.class_overlay else None}
    try:
        server.start()
        server.command_batch(['forceload add 320 320 335 335', 'forceload add 640 640'])
        server.step(40)
        check('forced chunk loaded', 'if loaded 320 -60 320')
        server.command_batch(['setblock 321 -55 321 minecraft:gold_block', 'setblock 641 -55 641 minecraft:emerald_block'])
        server.step(5)
        # Releasing the forced tickets drops the holders: unload saves them.
        server.command_batch(['forceload remove 320 320 335 335', 'forceload remove 640 640'])
        server.step(200)
        check('released chunk unloaded', 'unless loaded 320 -60 320')
        check('released single chunk unloaded', 'unless loaded 640 -60 640')
        # Reload: the unload save must be readable, through a revived or new holder.
        server.command_batch(['forceload add 320 320', 'forceload add 640 640'])
        server.step(40)
        check('change saved at unload and reloaded', 'if block 321 -55 321 minecraft:gold_block')
        check('second change saved at unload and reloaded', 'if block 641 -55 641 minecraft:emerald_block')
        # Release and re-add within the same tick window: revival of a pending holder.
        server.command_batch(['forceload remove 320 320', 'forceload add 320 320'])
        server.step(20)
        check('revived holder keeps its chunk', 'if block 321 -55 321 minecraft:gold_block')
        server.command_batch(['save-all', 'save-all flush'])
        server.step(5)
        server.stop(save=True)

        server.start()
        server.step(40)
        check('forced chunk reloaded after restart', 'if loaded 320 -60 320')
        check('saved block survives restart', 'if block 321 -55 321 minecraft:gold_block')
        server.command_batch(['forceload remove all'])
        server.step(200)
        check('remove all unloads', 'unless loaded 320 -60 320')
        server.stop(save=True)
        result['status'] = 'passed' if all(c['passed'] for c in checks) else 'failed'
    except Exception as exc:
        result['error'] = f'{type(exc).__name__}: {exc}'
        print(result['error'], file=sys.stderr, flush=True)
    finally:
        unlock()
        try:
            server.stop(save=True)
        except Exception as exc:
            result['status'] = 'failed'
            result['cleanup_error'] = f'{type(exc).__name__}: {exc}'
        if args.inject_readonly_region and result['status'] == 'passed':
            # After the gate, so its outcome (including a crash) cannot affect the gate.
            result['experiment'] = readonly_experiment(server, region, lock, unlock, console_has, output)
        server.commands.close()
        result.update(checks=checks, tick_steps=server.tick_steps, server_exits=server.exits)
        (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'status': result['status'], 'checks': len(checks), 'error': result.get('error'),
                      'experiment': result.get('experiment'), 'output': str(output)}, indent=2))
    return 0 if result['status'] == 'passed' else 1


def readonly_experiment(server, region, lock, unlock, console_has, output):
    """Not a gate: what a read-only region directory does to a save into a new
    region file. Returns what was observed."""
    observed = {}
    try:
        server.start()
        server.step(20)
        lock(region)
        server.command_batch(['forceload add 2048 2048'])
        server.step(40)
        observed['loaded'] = server.condition('if loaded 2048 -60 2048')
        server.command_batch(['setblock 2049 -55 2049 minecraft:diamond_block', 'save-all flush'])
        server.step(20)
        observed['save_failure_reported'] = console_has('Failed to save chunk 128,128')
        observed['region_open_failures'] = sum(1 for line in (output / f'console-{server.run}.log').read_text().splitlines()
                                               if 'Open region failed' in line)
        unlock()
        server.stop(save=True)
    except Exception as exc:
        observed['error'] = f'{type(exc).__name__}: {exc}'
        observed['region_open_failures'] = sum(1 for path in output.glob('console-*.log')
                                               for line in path.read_text(errors='replace').splitlines() if 'Open region failed' in line)
        unlock()
        try:
            server.stop(save=False)
        except Exception as stop_error:
            observed['stop_error'] = f'{type(stop_error).__name__}: {stop_error}'
    return observed


if __name__ == '__main__':
    raise SystemExit(main())
