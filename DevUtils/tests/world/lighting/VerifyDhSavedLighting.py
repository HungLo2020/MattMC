#!/usr/bin/env python3
"""Alternate complete saved-light imports against untouched Frozen or prior Current."""
import argparse
import hashlib
import json
from pathlib import Path
import statistics
import subprocess


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--driver', type=Path, required=True)
    parser.add_argument('--current-classpath-file', type=Path, required=True)
    parser.add_argument('--reference-classpath-file', type=Path, required=True)
    parser.add_argument('--native-dir', type=Path, required=True)
    parser.add_argument('--reference-native-dir', type=Path)
    parser.add_argument('--reference-kind', choices=('frozen', 'previous-current'), required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--pairs', type=int, default=6)
    parser.add_argument('--calls', type=int, default=100)
    args = parser.parse_args()
    if not 2 <= args.pairs <= 50 or args.calls < 1:
        parser.error('positive calls and 2..50 pairs required')
    if args.reference_kind == 'previous-current' and args.reference_native_dir is None:
        parser.error('previous Current requires its own native image')
    args.output.mkdir(parents=True, exist_ok=False)
    dirs = {'current': args.native_dir, 'reference': args.reference_native_dir or args.native_dir}
    paths = [args.native_dir / 'mattmc_rust-linux-x64.so', *args.driver.glob('*.class')]
    if args.reference_native_dir:
        paths.append(args.reference_native_dir / 'mattmc_rust-linux-x64.so')
    if len(paths) < 2:
        raise RuntimeError('Compile VerifyDhSavedLighting.java against Frozen first')
    pins = {str(p): digest(p) for p in paths}
    cps = {side: str(args.driver.resolve()) + ':' + path.read_text().strip() for side, path in
           [('current', args.current_classpath_file), ('reference', args.reference_classpath_file)]}
    order = [side for pair in range(args.pairs) for side in
             (['reference', 'current'] if pair % 2 == 0 else ['current', 'reference'])]
    protocol = {'scope': 'complete saved import and sampled reads; GC-managed release; no FPS acceptance',
                'reference': args.reference_kind, 'calls': args.calls, 'sections': 24,
                'warmup': 'five equally sized rounds per JVM', 'order': order, 'pins': pins}
    (args.output / 'protocol.json').write_text(json.dumps(protocol, indent=2) + '\n')
    rows = []
    for index, side in enumerate(order):
        assert all(digest(Path(p)) == sha for p, sha in pins.items()), 'Inputs changed'
        command = [str(args.java), '--enable-native-access=ALL-UNNAMED', '-Xmx2g',
                   '-Dmattmc.rust.natives.dir=' + str(dirs[side].resolve()), '-cp', cps[side],
                   'VerifyDhSavedLighting', 'bench', str(args.calls)]
        result = subprocess.run(command, capture_output=True, text=True, timeout=180)
        (args.output / f'{index:02d}-{side}.log').write_text(result.stdout + result.stderr)
        observations = [json.loads(line[line.index('{"elapsed_ns":'):])
                        for line in result.stdout.splitlines() if '{"elapsed_ns":' in line]
        if result.returncode or len(observations) != 1:
            raise RuntimeError(f'Failed run {index}; retained log describes failure')
        row = {'side': side, **observations[0], 'exit': result.returncode, 'command': command}
        assert row['calls'] == args.calls
        if side == 'current' or args.reference_kind == 'previous-current':
            assert row['native_image_sha256'] == digest(dirs[side] / 'mattmc_rust-linux-x64.so')
        assert row['native_import'] == (side == 'current')
        rows.append(row)
        (args.output / 'runs.json').write_text(json.dumps(rows, indent=2) + '\n')
        print(index, side, round(row['elapsed_ns'] / 1e6, 3), 'ms', flush=True)
    assert len({row['checksum'] for row in rows}) == 1, 'Read checksum mismatch'
    assert all(digest(Path(p)) == sha for p, sha in pins.items()), 'Inputs changed'
    medians = {side: statistics.median(row['elapsed_ns'] for row in rows if row['side'] == side) / 1e6 for side in cps}
    ratio = medians['current'] / medians['reference']
    report = {**protocol, 'median_ms': medians, 'current_over_reference': ratio,
              'scoped_median_gate_pass': ratio <= 1, 'runs': rows}
    (args.output / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'median_ms': medians, 'ratio': ratio, 'passed': ratio <= 1}))
    if ratio > 1:
        raise SystemExit('Scoped performance gate failed; retain and investigate')


if __name__ == '__main__':
    main()
