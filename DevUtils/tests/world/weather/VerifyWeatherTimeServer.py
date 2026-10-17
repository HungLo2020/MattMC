#!/usr/bin/env python3
"""Real dedicated-server weather/time persistence check for a compiled runtime.

Starts a disposable flat world on loopback with ticks frozen, drives /weather,
/time and controlled tick steps (including from the Nether, whose level data is
derived), saves, restarts and reads level.dat directly. Startup ticks before the
freeze vary, so checks compare deltas and persisted-versus-observed values.

  python3 DevUtils/tests/world/weather/VerifyWeatherTimeServer.py \
    --java /path/to/jdk/bin/java --classpath-file /path/to/main-classpath.txt \
    --native-dir build/rust/native --output /new/output/dir [--class-overlay DIR]

--class-overlay prepends pre-change classes for a control run with the same driver.
"""
import argparse
import gzip
import io
import json
import os
from pathlib import Path
import re
import struct
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'server'))
from VerifyComposterTransfers import Server, sha256  # noqa: E402


def read_nbt(path):
    """Minimal NBT reader for level.dat (big-endian, gzip)."""
    data = io.BytesIO(gzip.decompress(Path(path).read_bytes()))

    def take(fmt):
        size = struct.calcsize(fmt)
        return struct.unpack('>' + fmt, data.read(size))[0]

    def string():
        return data.read(take('H')).decode('utf-8', 'replace')

    def payload(tag):
        if tag == 1: return take('b')
        if tag == 2: return take('h')
        if tag == 3: return take('i')
        if tag == 4: return take('q')
        if tag == 5: return take('f')
        if tag == 6: return take('d')
        if tag == 7: return data.read(take('i'))
        if tag == 8: return string()
        if tag == 9:
            inner, count = take('b'), take('i')
            return [payload(inner) for _ in range(count)]
        if tag == 10:
            result = {}
            while (inner := take('b')) != 0:
                name = string()
                result[name] = payload(inner)
            return result
        if tag == 11: return [take('i') for _ in range(take('i'))]
        if tag == 12: return [take('q') for _ in range(take('i'))]
        raise ValueError(f'unknown NBT tag {tag}')

    assert take('b') == 10
    string()
    return payload(10)


KEYS = ['Time', 'DayTime', 'clearWeatherTime', 'rainTime', 'raining', 'thunderTime', 'thundering']


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--classpath-file', type=Path, required=True)
    parser.add_argument('--native-dir', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--class-overlay', type=Path)
    parser.add_argument('--port', type=int, default=25584)
    parser.add_argument('--timeout', type=float, default=180)
    args = parser.parse_args()
    java = args.java.resolve(strict=True)
    native = args.native_dir.resolve(strict=True)
    classpath = os.pathsep.join(str(Path(e).resolve(strict=True)) for e in args.classpath_file.read_text().strip().split(os.pathsep))
    if args.class_overlay:
        classpath = str(args.class_overlay.resolve(strict=True)) + os.pathsep + classpath
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    world_dir = output / 'server'
    world_dir.mkdir()
    properties = {
        'server-ip': '127.0.0.1', 'server-port': args.port, 'level-name': 'world', 'level-seed': 3,
        'level-type': 'minecraft:flat', 'generate-structures': 'false', 'view-distance': 2,
        'simulation-distance': 2, 'pause-when-empty-seconds': 0, 'enable-rcon': 'false', 'enable-query': 'false',
        'motd': 'Disposable weather/time acceptance',
    }
    (world_dir / 'server.properties').write_text(''.join(f'{k}={v}\n' for k, v in properties.items()))
    command = [str(java), '-Xmx2G', '-Xms512M', '--enable-native-access=ALL-UNNAMED',
               '-Dmattmc.rust.natives.dir=' + str(native), '-cp', classpath, 'net.minecraft.server.Main', '--nogui']
    server = Server(command, world_dir, output, args.timeout)
    checks = []

    def check(name, ok, detail):
        checks.append({'check': name, 'passed': bool(ok), 'detail': detail})
        if not ok:
            raise AssertionError(f'{name}: {detail}')

    def time(kind, dimension=None):
        prefix = f'execute in {dimension} run ' if dimension else ''
        lines = server.command_batch([f'{prefix}time query {kind}'])
        values = [int(m.group(1)) for line in lines if (m := re.search(r'\[Server thread/INFO\]: The time is (-?\d+)$', line))]
        if len(values) != 1:
            raise RuntimeError(f'ambiguous time response {lines}')
        return values[0]

    def saved():
        lines = server.command_batch(['save-all flush'])
        if not any(line.endswith('Saved the game') for line in lines):
            raise RuntimeError('save-all flush not acknowledged')
        return {key: read_nbt(world_dir / 'world/level.dat')['Data'].get(key) for key in KEYS}

    result = {'status': 'failed', 'driver_sha256': sha256(Path(__file__).resolve()), 'overlay': str(args.class_overlay) if args.class_overlay else None}
    try:
        server.start()
        server.command_batch(['time set 1000', 'weather thunder 600'])
        state = saved()
        check('weather command persisted', state['raining'] == 1 and state['thundering'] == 1 and state['rainTime'] == 600 and state['thunderTime'] == 600 and state['clearWeatherTime'] == 0, state)
        day, game = time('daytime'), time('gametime')
        check('daytime set', day == 1000 % 24000, day)
        check('nether reads shared clock', time('daytime', 'minecraft:the_nether') == day and time('gametime', 'minecraft:the_nether') == game, day)
        server.step(40)
        after = saved()
        check('tick step advances shared clock once', time('gametime') - game == 40 and time('daytime') - day == 40, after)
        check('weather counters count down per tick', after['rainTime'] == 560 and after['thunderTime'] == 560 and after['raining'] == 1, after)
        server.command_batch(['execute in minecraft:the_nether run time add 500'])
        check('/time add from a derived dimension advances once', time('daytime') - day == 540, time('daytime'))
        server.command_batch(['gamerule doDaylightCycle false'])
        before = time('daytime')
        server.step(20)
        check('daylight rule stops day time only', time('daytime') == before and time('gametime') - game == 60, time('daytime'))
        server.command_batch(['gamerule doDaylightCycle true', 'gamerule doWeatherCycle false'])
        frozen = saved()
        server.step(20)
        check('weather rule freezes counters', saved()['rainTime'] == frozen['rainTime'], frozen)
        server.command_batch(['gamerule doWeatherCycle true', 'weather clear 300'])
        cleared = saved()
        check('weather clear persisted', cleared['raining'] == 0 and cleared['thundering'] == 0 and cleared['clearWeatherTime'] == 300, cleared)
        server.step(10)
        before_stop = saved()
        check('clear countdown', before_stop['clearWeatherTime'] == 290 and before_stop['rainTime'] == 1 and before_stop['thunderTime'] == 1, before_stop)
        server.stop(save=True)
        on_disk = {key: read_nbt(world_dir / 'world/level.dat')['Data'].get(key) for key in KEYS}
        # The final save may follow a few unfrozen shutdown ticks; never fewer.
        check('shutdown save not behind observed state', on_disk['Time'] >= before_stop['Time'] and on_disk['DayTime'] >= before_stop['DayTime'], on_disk)

        server.start()
        reopened_game, reopened_day = time('gametime'), time('daytime')
        check('reopen restores clock', reopened_game >= on_disk['Time'] and reopened_day >= on_disk['DayTime'] and reopened_game - on_disk['Time'] == reopened_day - on_disk['DayTime'], [reopened_game, reopened_day, on_disk])
        reopened = saved()
        check('reopen restores weather counters', reopened['raining'] == 0 and reopened['clearWeatherTime'] == on_disk['clearWeatherTime'] - (reopened_game - on_disk['Time']), [reopened, on_disk])
        server.command_batch(['weather rain 50'])
        server.step(60)
        toggled = saved()
        # /weather rain sets both counters to 50: at 50 rain stops and thunder starts,
        # then the next tick samples a rain delay and a thunder duration.
        check('expired counters toggle then sample', toggled['raining'] == 0 and toggled['thundering'] == 1
              and 12000 - 10 <= toggled['rainTime'] <= 180000 and 3600 - 10 <= toggled['thunderTime'] <= 15600, toggled)
        server.stop(save=True)
        log = ''.join(p.read_text(errors='replace') for p in sorted(output.glob('console-*.log')))
        check('no native weather failures logged', 'Native weather' not in log and 'NativeWeatherTime' not in log, 'console logs')
        check('clean exits', all(e['exit'] == 0 and not e['cleanup_errors'] for e in server.exits), server.exits)
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
