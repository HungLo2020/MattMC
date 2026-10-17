#!/usr/bin/env python3
"""Early matched complete update/import costs; preserve raw failures and noise."""
import importlib.util
import json
import statistics
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'threaded'))
from VerifyThreadedLighting import Campaign, arguments, images, sha, ROOT
from BenchmarkThreadedLighting import parse, pressure


def main():
    parser = arguments(__doc__)
    parser.add_argument('--pairs', type=int, default=1)
    parser.add_argument('--samples', type=int, default=12)
    parser.add_argument('--cpu', type=int, default=4)
    parser.add_argument('--modes', default='direct-empty,direct-seed,queued-retained')
    args = parser.parse_args()
    campaign = Campaign(args)
    campaign.result.update(status='incomplete', workloads={}, host_start=pressure(),
        scope='Direct public empty block+sky runLightUpdates on an enabled lit world; '
              'complete public block/sky source production and actual updates; retained saved-layer public imports through actual threaded PRE/update/POST. '
              'Import decode/layer creation excluded; no server/IO/client/FPS claim.',
        configuration={'pairs': args.pairs, 'samples': args.samples, 'cpu': args.cpu})
    archive = campaign.output / 'driver-source'
    archive.mkdir()
    for driver in HERE.iterdir():
        if driver.suffix in ('.java', '.py'):
            campaign.result['driver_sha256'][str(driver.relative_to(ROOT))] = sha(driver)
            (archive / driver.name).write_bytes(driver.read_bytes())
    try:
        for side in ('frozen', 'control', 'current'):
            campaign.run([str(campaign.jdk / 'javac'), '-nowarn', '-cp',
                str(campaign.drivers[side]) + ':' + campaign.classpaths[side],
                '-d', str(campaign.drivers[side]), str(HERE / 'BenchmarkLightCoordinator.java')],
                'compile-import-' + side + '.log')
        campaign.class_images = images({**campaign.classpaths,
            **{'observer-' + s: str(p) for s, p in campaign.drivers.items()}})
        campaign.result['class_images'] = campaign.class_images
        for mode in args.modes.split(','):
            count, steps = (0, 131072) if mode == 'direct-empty' else (0, 128) if mode == 'direct-seed' else (18, 256)
            row = {'pairs': [], 'steps_per_sample': steps, 'sections_per_cycle': count}
            campaign.result['workloads'][mode] = row
            for pair in range(args.pairs):
                order = ('frozen', 'control', 'current') if pair % 2 == 0 else ('current', 'control', 'frozen')
                result = {'order': order}
                row['pairs'].append(result)
                for side in order:
                    output = campaign.run(campaign.java(side, 'BenchmarkLightCoordinator',
                        [mode, count, steps, args.samples], timing=True, cpu=args.cpu),
                        f'{mode}-pair{pair}-{side}.log')
                    data = parse(output)
                    assert len(data['samples']) == args.samples
                    quiet = [s for s in data['samples'] if s['compilation_ms'] == 0]
                    data['quiet_count'] = len(quiet)
                    data['median_quiet_ns'] = statistics.median(s['ns'] for s in quiet) if quiet else None
                    data['admission'] = [s for s in output.splitlines() if 'NATIVE_ADMISSION ' in s]
                    result[side] = data
                    campaign.save()
                vectors = [tuple((s['index'], s['checksum'], s['dispatch'], s['light']) for s in result[side]['samples'])
                           for side in ('frozen', 'control', 'current')]
                result['matched_work'] = vectors[0] == vectors[1] == vectors[2]
                result['quiet_enough'] = all(result[s]['quiet_count'] >= 8 for s in order)
                if result['matched_work'] and result['quiet_enough']:
                    for baseline in ('frozen', 'control'):
                        result['ratio_current_' + baseline] = result['current']['median_quiet_ns'] / result[baseline]['median_quiet_ns']
                campaign.save()
            ratios = [p.get('ratio_current_frozen') for p in row['pairs']]
            row['passed_general_10pct'] = all(r is not None and r <= 1.10 for r in ratios)
            campaign.save()
        campaign.result['host_end'] = pressure()
        campaign.result['status'] = 'measured' if all(p.get('matched_work') and p.get('quiet_enough')
            for row in campaign.result['workloads'].values() for p in row['pairs']) else 'inconclusive'
        campaign.result['passed'] = campaign.result['status'] == 'measured' and all(
            r['passed_general_10pct'] for r in campaign.result['workloads'].values())
    finally:
        campaign.seal()
    assert campaign.result['guards_pass'], 'Source/native/class/driver seal changed'
    print(json.dumps({k: {'pairs': [{n: v for n, v in p.items() if n.startswith('ratio_')} for p in r['pairs']],
                         'general_10pct': r['passed_general_10pct']} for k, r in campaign.result['workloads'].items()}))
    if not campaign.result['passed']:
        raise SystemExit('Early cost gate failed/inconclusive; retained raw evidence, review required')


if __name__ == '__main__':
    main()
