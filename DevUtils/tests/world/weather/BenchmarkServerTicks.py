#!/usr/bin/env python3
"""Matched whole-server tick timing for a candidate and a control classpath.

Runs real dedicated servers on loopback with /tick sprint, alternating
candidate and control processes, and records the server's own ms/tick report
plus the process CPU time (utime+stime from /proc) consumed by each sprint.
Workloads: an idle flat world, and a loaded default world with force-loaded
chunks and mobs. Linux only (reads /proc).

  python3 DevUtils/tests/world/weather/BenchmarkServerTicks.py \
    --java /path/to/jdk/bin/java --classpath-file main-classpath.txt \
    --native-dir build/rust/native --control-overlay PRE_CHANGE_CLASSES --output /new/dir
"""
import argparse
import json
import os
from pathlib import Path
import re
import statistics
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'server'))
from VerifyComposterTransfers import Server, sha256  # noqa: E402

CLOCK_TICKS = os.sysconf('SC_CLK_TCK')
WORKLOADS = {
    'idle': ({'level-type': 'minecraft:flat', 'generate-structures': 'false'}, []),
    # Mobs spawn relative to the console source (world spawn, always loaded).
    'loaded': ({'level-type': 'minecraft:normal'},
               [f'summon minecraft:{mob} ~{x} ~8 ~{z}' for mob in ('cow', 'sheep', 'chicken', 'pig')
                for x in range(-12, 13, 6) for z in range(-12, 13, 6)]),
}


def cpu_seconds(pid):
    fields = Path(f'/proc/{pid}/stat').read_text().rsplit(')', 1)[1].split()
    return (int(fields[11]) + int(fields[12])) / CLOCK_TICKS


def run_server(name, java, classpath, native, output, workload, port, sprints, ticks, timeout):
    properties, setup = WORKLOADS[workload]
    world_dir = output / name
    world_dir.mkdir()
    props = {'server-ip': '127.0.0.1', 'server-port': port, 'level-name': 'world', 'level-seed': 22,
             'view-distance': 4, 'simulation-distance': 4, 'pause-when-empty-seconds': 0, 'enable-rcon': 'false',
             'enable-query': 'false', 'motd': 'Disposable tick benchmark', **properties}
    (world_dir / 'server.properties').write_text(''.join(f'{k}={v}\n' for k, v in props.items()))
    command = [str(java), '-Xmx3G', '-Xms3G', '-XX:+UseG1GC', '--enable-native-access=ALL-UNNAMED',
               '-Dmattmc.rust.natives.dir=' + str(native), '-cp', classpath, 'net.minecraft.server.Main', '--nogui']
    server = Server(command, world_dir, world_dir, timeout)
    rows = []
    try:
        server.start()
        if setup:
            # Force-load a 10x10-chunk area around the origin and let it load
            # during a discarded sprint before adding mobs.
            server.command_batch(['forceload add -80 -80 79 79', 'tick unfreeze'])
            server.send('tick sprint 400')
            server.until(lambda line: 'Sprint completed with' in line, timeout)
            server.command_batch(setup)
        else:
            server.command_batch(['tick unfreeze'])
        for index in range(sprints):
            before = cpu_seconds(server.process.pid)
            server.send(f'tick sprint {ticks}')
            lines = server.until(lambda line: 'Sprint completed with' in line, timeout)
            after = cpu_seconds(server.process.pid)
            match = re.search(r'Sprint completed with ([\d.,]+) ticks per second, or ([\d.,]+) ms per tick', lines[-1])
            rows.append({'sprint': index, 'ticks': ticks, 'ms_per_tick': float(match.group(2).replace(',', '')),
                         'cpu_ms_per_tick': (after - before) * 1000 / ticks})
    finally:
        server.stop(save=False)
        server.commands.close()
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--classpath-file', type=Path, required=True)
    parser.add_argument('--native-dir', type=Path, required=True)
    parser.add_argument('--control-overlay', type=Path, required=True)
    parser.add_argument('--frozen-classpath-file', type=Path, help='adds a matched Frozen side (the acceptance baseline)')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--rounds', type=int, default=3)
    parser.add_argument('--sprints', type=int, default=4, help='per server; the first is reported as warmup')
    parser.add_argument('--ticks', type=int, default=6000)
    parser.add_argument('--port', type=int, default=25585)
    parser.add_argument('--timeout', type=float, default=600)
    args = parser.parse_args()
    java, native = args.java.resolve(strict=True), args.native_dir.resolve(strict=True)
    base = os.pathsep.join(str(Path(e).resolve(strict=True)) for e in args.classpath_file.read_text().strip().split(os.pathsep))
    classpaths = {'candidate': base, 'control': str(args.control_overlay.resolve(strict=True)) + os.pathsep + base}
    if args.frozen_classpath_file:
        classpaths['frozen'] = os.pathsep.join(str(Path(e).resolve(strict=True)) for e in args.frozen_classpath_file.read_text().strip().split(os.pathsep))
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    results = {'driver_sha256': sha256(Path(__file__).resolve()), 'native_sha256': sha256(native / 'mattmc_rust-linux-x64.so'),
               'loadavg_start': Path('/proc/loadavg').read_text().strip(), 'runs': []}
    for workload in WORKLOADS:
        for round_index in range(args.rounds):
            sides = list(classpaths)
            order = sides[round_index % len(sides):] + sides[:round_index % len(sides)]
            for side in order:
                rows = run_server(f'{workload}-{round_index}-{side}', java, classpaths[side], native, output, workload,
                                  args.port, args.sprints, args.ticks, args.timeout)
                results['runs'].append({'workload': workload, 'round': round_index, 'side': side, 'sprints': rows})
                print(workload, round_index, side, [(r['ms_per_tick'], round(r['cpu_ms_per_tick'], 3)) for r in rows], flush=True)
    summary = {}
    for workload in WORKLOADS:
        for side in classpaths:
            measured = [r for run in results['runs'] if run['workload'] == workload and run['side'] == side for r in run['sprints'][1:]]
            summary[f'{workload}/{side}'] = {key: {'median': statistics.median(r[key] for r in measured), 'min': min(r[key] for r in measured),
                                                   'max': max(r[key] for r in measured), 'n': len(measured)}
                                             for key in ('ms_per_tick', 'cpu_ms_per_tick')}
    results['summary'] = summary
    results['loadavg_end'] = Path('/proc/loadavg').read_text().strip()
    (output / 'result.json').write_text(json.dumps(results, indent=2) + '\n')
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    main()
