#!/usr/bin/env python3
"""Matched production producers/dispatch/batch/propagation, outside build windows."""
import json
import re
import statistics
from pathlib import Path
from VerifyThreadedLighting import Campaign, arguments


def pressure():
    values = dict(line.split() for line in Path('/proc/vmstat').read_text().splitlines())
    return {key: int(values[key]) for key in ('pswpin', 'pswpout', 'pgmajfault')}


def parse(text):
    samples = []
    for match in re.finditer(r'\bSAMPLE (\d+) (\d+) (\d+) (\d+) (-?\d+) (\d+) (\d+)', text):
        samples.append(dict(zip(('index', 'ns', 'java_bytes', 'compilation_ms', 'checksum', 'dispatch', 'light'),
                                map(int, match.groups()))))
    cold = re.search(r'\bCOLD (\d+) (\d+)', text)
    warm = re.search(r'\bWARM (\d+)', text)
    engine = re.search(r'\bENGINE_CONSTRUCTION (\d+)', text)
    resources = [line.split('RESOURCE ', 1)[1] for line in text.splitlines() if 'RESOURCE SAMPLE ' in line]
    assert cold and warm and engine and samples and len(samples) == len(resources), 'Incomplete benchmark output'
    return {'samples': samples, 'cold_constructor_ns': int(cold[1]), 'cold_first_initialize_ns': int(cold[2]),
            'cold_engine_construction_ns': int(engine[1]),
            'warmup_cycles': int(warm[1]), 'resources': resources}


def main():
    parser = arguments(__doc__)
    parser.add_argument('--pairs', type=int, default=3)
    parser.add_argument('--samples', type=int, default=12)
    parser.add_argument('--steps', type=int, default=32)
    parser.add_argument('--workloads', default='0,1,64,1000,2501')
    parser.add_argument('--cpu', type=int, default=4)
    args = parser.parse_args()
    campaign = Campaign(args)
    campaign.result.update(status='incomplete', workloads={}, host_start=pressure(),
        scope='Actual public checkBlock/waiter producers, chunk dispatcher, coordinator and real block/sky propagation; '
              'initial lit world, alternating border edits. No generation/IO/client/FPS claim.',
        configuration={'pairs': args.pairs, 'samples': args.samples, 'steps': args.steps, 'cpu': args.cpu,
                       'workloads': args.workloads})
    try:
        for workload in map(int, args.workloads.split(',')):
            steps = max(args.steps, 32768 if workload == 0 else 1024 if workload == 1 else args.steps)
            row = {'pairs': [], 'steps_per_sample': steps, 'passed_general_10pct': False}
            campaign.result['workloads'][str(workload)] = row
            for pair in range(args.pairs):
                orders = (('frozen', 'control', 'current'), ('current', 'control', 'frozen'))
                results = {'order': orders[pair % 2], 'matched_work': False}
                row['pairs'].append(results)
                for side in results['order']:
                    text = campaign.run(campaign.java(side, 'BenchmarkThreadedLighting',
                        [workload, steps, args.samples], timing=True, cpu=args.cpu),
                        f'{workload}-pair{pair}-{side}.log')
                    data = parse(text)
                    assert len(data['samples']) == args.samples
                    quiet = [s for s in data['samples'] if s['compilation_ms'] == 0]
                    data['quiet_count'] = len(quiet)
                    data['median_quiet_ns'] = statistics.median(s['ns'] for s in quiet) if quiet else None
                    results[side] = data
                    campaign.save()
                vectors = [tuple((s['index'], s['checksum'], s['dispatch'], s['light']) for s in results[side]['samples'])
                           for side in ('frozen', 'control', 'current')]
                results['matched_work'] = vectors[0] == vectors[1] == vectors[2]
                results['quiet_enough'] = all(results[s]['quiet_count'] >= 8 for s in ('frozen', 'control', 'current'))
                if results['matched_work'] and results['quiet_enough']:
                    results['ratio_current_frozen'] = results['current']['median_quiet_ns'] / results['frozen']['median_quiet_ns']
                    results['ratio_current_control'] = results['current']['median_quiet_ns'] / results['control']['median_quiet_ns']
                campaign.save()
            ratios = [p.get('ratio_current_frozen') for p in row['pairs']]
            row['passed_general_10pct'] = all(value is not None and value <= 1.10 for value in ratios)
            row['median_ratio_current_frozen'] = statistics.median(ratios) if all(v is not None for v in ratios) else None
            campaign.save()
        campaign.result['host_end'] = pressure()
        campaign.result['status'] = 'measured' if all(p['matched_work'] and p['quiet_enough']
            for row in campaign.result['workloads'].values() for p in row['pairs']) else 'inconclusive'
        campaign.result['passed'] = campaign.result['status'] == 'measured' and all(
            row['passed_general_10pct'] for row in campaign.result['workloads'].values())
    finally:
        campaign.seal()
    assert campaign.result['guards_pass'], 'Source/native/class/driver seal changed'
    print(json.dumps({key: {'ratio': row['median_ratio_current_frozen'], 'general_10pct': row['passed_general_10pct']}
                      for key, row in campaign.result['workloads'].items()}))
    if campaign.result['status'] != 'measured':
        raise SystemExit('Inconclusive; retained every original sample')
    if not campaign.result['passed']:
        raise SystemExit('General allowance exceeded; attribution/review required, not self-approved')


if __name__ == '__main__':
    main()
