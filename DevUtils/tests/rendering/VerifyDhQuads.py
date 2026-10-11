#!/usr/bin/env python3
"""Isolated alternating Frozen/Current complete DH builder CPU comparisons."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import statistics
import subprocess
import time


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--driver', type=Path, required=True)
    parser.add_argument('--current-classpath-file', type=Path, required=True)
    parser.add_argument('--frozen-classpath-file', type=Path, required=True)
    parser.add_argument('--native-dir', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--pairs', type=int, default=6)
    parser.add_argument('--rounds', type=int, default=300)
    parser.add_argument('--quads', type=int, default=2048)
    parser.add_argument('--seed', type=int, choices=(0, 1, 2), required=True)
    parser.add_argument('--mode', choices=('topology', 'packets'), required=True)
    args = parser.parse_args()
    if not 2 <= args.pairs <= 50 or args.rounds < 1 or args.quads < 1:
        parser.error('positive rounds/quads and 2..50 pairs required')
    args.output.mkdir(parents=True, exist_ok=False)
    native = args.native_dir / 'mattmc_rust-linux-x64.so'
    paths = [native, *args.driver.glob('*.class')]
    if len(paths) < 2:
        raise RuntimeError('Compile the identical driver against Frozen first')
    pins = {str(p): digest(p) for p in paths}
    cps = {side: str(args.driver.resolve()) + ':' + path.read_text().strip()
           for side, path in [('current', args.current_classpath_file), ('frozen', args.frozen_classpath_file)]}
    order = [side for pair in range(args.pairs) for side in
             (['frozen', 'current'] if pair % 2 == 0 else ['current', 'frozen'])]
    protocol = {'scope': 'synthetic CPU build lifecycle; no gameplay/FPS acceptance',
                'mode': args.mode, 'seed': args.seed, 'quads': args.quads, 'rounds': args.rounds,
                'warmup': 'five equally sized rounds per JVM', 'order': order, 'pins': pins}
    (args.output / 'protocol.json').write_text(json.dumps(protocol, indent=2) + '\n')
    rows = []
    for index, side in enumerate(order):
        assert all(digest(Path(p)) == expected for p, expected in pins.items()), 'Inputs changed'
        command = [str(args.java), '--enable-native-access=ALL-UNNAMED', '-Xmx2G',
                   '-Dmattmc.rust.natives.dir=' + str(args.native_dir.resolve()), '-cp', cps[side],
                   'VerifyDhQuads', 'bench', side, str(args.seed), str(args.quads), str(args.rounds), args.mode]
        start = time.time_ns()
        result = subprocess.run(command, capture_output=True, text=True, timeout=180)
        (args.output / f'{index:02d}-{side}.log').write_text(result.stdout + result.stderr)
        match = re.findall(r'RESULT tree=(\w+) ns=(\d+) checksum=(-?\d+) quads=(\d+) rounds=(\d+)', result.stdout)
        if result.returncode or len(match) != 1 or match[0][0] != side:
            raise RuntimeError(f'Failed run {index}, see retained log')
        row = {'side': side, 'elapsed_ns': int(match[0][1]), 'checksum': int(match[0][2]),
               'start_wall_ns': start, 'exit': result.returncode, 'command': command}
        rows.append(row)
        (args.output / 'runs.json').write_text(json.dumps(rows, indent=2) + '\n')
        print(index, side, round(row['elapsed_ns'] / 1e6, 3), 'ms', flush=True)
    assert len({row['checksum'] for row in rows}) == 1, 'Result mismatch'
    assert all(digest(Path(p)) == expected for p, expected in pins.items()), 'Inputs changed'
    medians = {side: statistics.median(row['elapsed_ns'] for row in rows if row['side'] == side) / 1e6 for side in cps}
    ratio = medians['current'] / medians['frozen']
    report = {**protocol, 'median_ms': medians, 'current_over_frozen': ratio,
              'scoped_median_gate_pass': ratio <= 1, 'runs': rows}
    (args.output / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({k: v for k, v in report.items() if k not in ('runs', 'pins', 'order')}, indent=2))
    if ratio > 1:
        raise SystemExit('Scoped performance gate failed; retain candidate and investigate')


if __name__ == '__main__':
    main()
