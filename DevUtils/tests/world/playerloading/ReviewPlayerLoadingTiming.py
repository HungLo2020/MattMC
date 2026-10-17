#!/usr/bin/env python3
"""Review sealed raw timings using exact ordered work-vector comparison.

Preserve the original inconclusive receipt. Reparse every measured sample from
the raw JVM logs and verify source/library/observer identity before applying
the corrected comparator. No new measurements or phase normalization.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess

from BenchmarkPlayerLoading import DRIVER, matched_work
from VerifyPlayerLoading import FROZEN, FROZEN_HEAD, ROOT, sha, source_identity


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--result', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    original = args.result.resolve()
    result = json.loads(original.read_text())
    assert result['inputs_unchanged']
    assert result['candidate_source_sha256'] == source_identity()
    assert result['native_sha256'] == sha(ROOT / 'build/rust/native/mattmc_rust-linux-x64.so')
    assert result['driver_sha256'] == sha(DRIVER)
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=FROZEN, text=True).strip() == FROZEN_HEAD
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=FROZEN)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    review = {'original_result': str(original), 'original_result_sha256': sha(original),
              'original_status': result['status'], 'reviewer_sha256': sha(__file__),
              'comparator_sha256': sha(Path(__file__).with_name('BenchmarkPlayerLoading.py')),
              'method': 'Exact ordered per-index checksum/effect vectors and separate ordered latency-work vectors; '
                        'no sorting or phase normalization. Original samples, summaries and ratios are unchanged.',
              'raw_logs_sha256': {}, 'phase_vectors': {}}
    fields = ('index', 'ns', 'checksum', 'compile_ms', 'gc_ms', 'adds', 'removals', 'attachments',
              'allocated_bytes', 'policy_native_bytes', 'rss_bytes', 'swap_bytes', 'heap_used_bytes')
    pattern = (r'TIMING (\d+) (\d+) CHECKSUM (-?\d+) COMPILE_MS (\d+) GC_MS (\d+) ADDS (\d+) REMOVALS (\d+) '
               r'ATTACHMENTS (\d+) ALLOCATED_BYTES (-?\d+) POLICY_NATIVE_BYTES (\d+) RSS_BYTES (-?\d+) '
               r'SWAP_BYTES (-?\d+) HEAP_USED_BYTES (\d+)')
    pairs = result['configuration']['pairs']
    samples = result['configuration']['samples']
    for workload, row in result['workloads'].items():
        review['phase_vectors'][workload] = {}
        for side in ('frozen', 'control', 'current'):
            phases = set()
            for pair in range(pairs):
                log = original.parent / f'{workload}-{pair}-{side}.log'
                data = log.read_text()
                review['raw_logs_sha256'][log.name] = sha(log)
                parsed = [dict(zip(fields, map(int, sample)), jvm_pair=pair)
                          for sample in re.findall(pattern, data)]
                stored = [sample for sample in row[side]['samples'] if sample['jvm_pair'] == pair]
                assert len(parsed) == samples and parsed == stored, (workload, side, pair, 'raw samples differ')
                assert sum(sample['compile_ms'] == 0 for sample in parsed) >= min(samples, 8)
                phases.update((sample['checksum'], sample['adds'], sample['removals'], sample['attachments']) for sample in parsed)
                warmed = re.search(r'WARMUP_COMPLETE CYCLES (\d+) QUIET_STREAK (\d+)', data)
                assert warmed and int(warmed[2]) >= 12
                observed = row[side]['warmup'][pair]
                assert observed['cycles'] == int(warmed[1]) and observed['quiet_streak'] == int(warmed[2])
            review['phase_vectors'][workload][side] = sorted(phases)
        row['equal_work'] = matched_work(row)
        assert row['equal_work'] and row['equal_cold_work'] and row['settled_gc_observed']
    result['status'] = 'measured'
    result['all_within_general_10_percent'] = all(row['within_general_10_percent'] for row in result['workloads'].values())
    result['all_within_boundary_25_percent'] = all(row['within_boundary_25_percent'] for row in result['workloads'].values())
    result['review'] = review
    (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print('Measured workloads:', {workload: row['ratio_vs_frozen'] for workload, row in result['workloads'].items()})
    print('Exact ordered work vectors match; original raw measurement receipt preserved:', original)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
