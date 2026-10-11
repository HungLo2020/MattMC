#!/usr/bin/env python3
"""Alternate complete DH face builds against Frozen or a preceding Current image."""
import argparse
import hashlib
import json
from pathlib import Path
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
    parser.add_argument('--reference-classpath-file', type=Path, required=True)
    parser.add_argument('--native-dir', type=Path, required=True)
    parser.add_argument('--reference-native-dir', type=Path)
    parser.add_argument('--reference-kind', choices=('frozen', 'previous-current'), required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--pairs', type=int, default=6)
    parser.add_argument('--rounds', type=int, default=1000)
    parser.add_argument('--copies', type=int, default=2)
    parser.add_argument('--semantic-spans', type=int, choices=(0, 8, 32), default=0, help='Current semantic extension; previous Current comparison only')
    parser.add_argument('--production-facts', action='store_true', help='stock directional shade facts on Current; identical shade results on references')
    parser.add_argument('--pattern', type=int, choices=range(8), required=True)
    args = parser.parse_args()
    if not 2 <= args.pairs <= 50 or args.rounds < 1 or args.copies < 1:
        parser.error('positive rounds/copies and 2..50 pairs required')
    if args.reference_kind == 'previous-current' and args.reference_native_dir is None:
        parser.error('previous Current requires its own native directory')
    if args.semantic_spans and args.reference_kind != 'previous-current':
        parser.error('Frozen predates semantic spans; use previous-current for this extension')
    args.output.mkdir(parents=True, exist_ok=False)
    native_dirs = {'current': args.native_dir, 'reference': args.reference_native_dir or args.native_dir}
    paths = [args.native_dir / 'mattmc_rust-linux-x64.so', *args.driver.glob('*.class')]
    if args.reference_native_dir:
        paths.append(args.reference_native_dir / 'mattmc_rust-linux-x64.so')
    if len(paths) < 2:
        raise RuntimeError('Compile VerifyDhFaces.java against Frozen first')
    pins = {str(p): digest(p) for p in paths}
    cps = {side: str(args.driver.resolve()) + ':' + p.read_text().strip() for side, p in
           [('current', args.current_classpath_file), ('reference', args.reference_classpath_file)]}
    order = [side for pair in range(args.pairs) for side in
             (['reference', 'current'] if pair % 2 == 0 else ['current', 'reference'])]
    protocol = {'scope': 'whole-box CPU face production, merging, counts and owner release; no FPS acceptance',
                'reference': args.reference_kind, 'pattern': args.pattern, 'rounds': args.rounds,
                'semantic_spans': args.semantic_spans, 'production_facts': args.production_facts, 'copies': args.copies, 'boxes': 32 * args.rounds * args.copies,
                'warmup': 'five equally sized rounds per JVM', 'order': order, 'pins': pins}
    (args.output / 'protocol.json').write_text(json.dumps(protocol, indent=2) + '\n')
    rows = []
    for index, side in enumerate(order):
        assert all(digest(Path(p)) == expected for p, expected in pins.items()), 'Inputs changed'
        command = [str(args.java), '--enable-native-access=ALL-UNNAMED', '-Xmx2g',
                   '-Dmattmc.dh.faces.semanticSpans=' + str(args.semantic_spans),
                   '-Dmattmc.dh.faces.productionFacts=' + str(args.production_facts).lower(),
                   '-Dmattmc.rust.natives.dir=' + str(native_dirs[side].resolve()), '-cp', cps[side],
                   'VerifyDhFaces', 'bench', str(args.pattern), str(args.rounds), str(args.copies)]
        start = time.time_ns()
        result = subprocess.run(command, capture_output=True, text=True, timeout=180)
        (args.output / f'{index:02d}-{side}.log').write_text(result.stdout + result.stderr)
        observations = [json.loads(line) for line in result.stdout.splitlines() if line.startswith('{"elapsed_ns":')]
        if result.returncode or len(observations) != 1:
            raise RuntimeError(f'Failed run {index}; retained log describes the failure')
        row = {'side': side, **observations[0], 'start_wall_ns': start, 'exit': result.returncode, 'command': command}
        assert row['boxes'] == protocol['boxes']
        rows.append(row)
        (args.output / 'runs.json').write_text(json.dumps(rows, indent=2) + '\n')
        print(index, side, round(row['elapsed_ns'] / 1e6, 3), 'ms', flush=True)
    assert len({row['checksum'] for row in rows}) == 1, 'Count checksum mismatch'
    assert all(digest(Path(p)) == expected for p, expected in pins.items()), 'Inputs changed'
    medians = {side: statistics.median(r['elapsed_ns'] for r in rows if r['side'] == side) / 1e6 for side in cps}
    ratio = medians['current'] / medians['reference']
    report = {**protocol, 'median_ms': medians, 'current_over_reference': ratio,
              'scoped_median_gate_pass': ratio <= 1, 'runs': rows}
    (args.output / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'median_ms': medians, 'ratio': ratio, 'passed': ratio <= 1}))
    if ratio > 1:
        raise SystemExit('Scoped performance gate failed; retain candidate and investigate')


if __name__ == '__main__':
    main()
