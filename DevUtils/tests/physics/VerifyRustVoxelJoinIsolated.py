#!/usr/bin/env python3
"""Confirm small/registered voxel joins in fresh JVMs containing one workload each.
Run full VerifyRustVoxelJoin acceptance first. Includes the unchanged complete caller.
"""
import hashlib
import json
from pathlib import Path
import random
import re
import statistics
import subprocess

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / 'build/voxel-join-migration/acceptance'
NAMES = ['identical_or_random_8', 'cube_or_random_8', 'registered_or_8']


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def main():
    accepted = json.loads((OUT / 'results.json').read_text())
    require(accepted.get('integrity') and all(v['passes'] for v in accepted['performance'].values()),
            'Run full accepted voxel join verification first')
    cp = (OUT / 'classpath.txt').read_text().strip()
    base = ['taskset', '-c', str(accepted['cpu']), 'java', *accepted['jvm'],
            '-Dmattmc.rust.natives.dir=' + str(ROOT / 'build/rust/native'), '-cp', cp,
            'net.minecraft.world.phys.shapes.NativeVoxelJoinVerification']
    report = {'scope': 'Fresh JVM per workload: same unchanged full public caller and fixture, independent specialization/warmup. Initial fixture/scratch setup excluded. No result cache.',
              'driver_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              'pairs': [], 'performance': {}}
    for fork in range(3):
        pair = {}
        for name in NAMES:
            row = pair.setdefault(name, {})
            for mode in (['java', 'native'] if fork % 2 == 0 else ['native', 'java']):
                print(f'Isolated pair {fork+1}: {name} {mode}', flush=True)
                log = OUT / f'isolated-{fork}-{name}-{mode}.log'
                with log.open('w') as stream:
                    subprocess.run(base + [mode, name], cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)
                rows = re.findall(r'VOXEL_BENCH name=(\w+).*?ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)', log.read_text())
                require(len(rows) == 1 and rows[0][0] == name, 'Incomplete isolated measurements')
                _, samples, jit, checksum = rows[0]
                require(not any(json.loads(jit)), 'Compilation during measured rounds')
                row[mode] = json.loads(samples)
                require('checksum' not in row or row['checksum'] == int(checksum), 'Outputs differ')
                require(int(checksum) == accepted['pairs'][0][name]['checksum'], 'Fixture differs from full acceptance')
                row['checksum'] = int(checksum)
        report['pairs'].append(pair)
        (OUT / 'isolated-results.json').write_text(json.dumps(report, indent=2))
    rng = random.Random(1977)
    for name in NAMES:
        pairs = [(p[name]['java'], p[name]['native']) for p in report['pairs']]
        ratios = [statistics.median(n)/statistics.median(j) for j, n in pairs]
        boots = sorted(statistics.median(statistics.median(rng.choices(n,k=len(n)))/statistics.median(rng.choices(j,k=len(j)))
                       for j,n in rng.choices(pairs,k=len(pairs))) for _ in range(10000))
        report['performance'][name] = {'java_ns': statistics.median(statistics.median(j) for j,n in pairs),
            'native_ns': statistics.median(statistics.median(n) for j,n in pairs), 'ratios': ratios,
            'ratio_ci95': [boots[250], boots[9749]], 'passes': max(ratios) <= .95 and boots[9749] <= .95}
        require(len({p[name]['checksum'] for p in report['pairs']}) == 1, 'Cross-fork checksums differ')
    require(all(hashlib.sha256((ROOT/f).read_bytes()).hexdigest() == h for f,h in accepted['sources'].items()), 'Measured sources changed')
    require(hashlib.sha256((ROOT/'build/rust/native/mattmc_rust-linux-x64.so').read_bytes()).hexdigest() == accepted['native_sha256'], 'Native library changed')
    require(hashlib.sha256(Path(__file__).read_bytes()).hexdigest() == report['driver_sha256'], 'Isolated driver changed')
    report['integrity'] = {'source_hashes_match': True, 'native_hash_matches': True, 'cross_fork_checksums_match': True}
    (OUT / 'isolated-results.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report['performance'], indent=2))
    require(all(v['passes'] for v in report['performance'].values()), 'Isolated 5% performance gate failed')


if __name__ == '__main__':
    main()
