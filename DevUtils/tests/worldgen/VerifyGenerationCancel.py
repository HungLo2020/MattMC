#!/usr/bin/env python3
"""Cancel generation mid-flight and check every persisted chunk is a whole result.

Each side (Frozen, Current, Current on Java's decoration route) decorates one
overworld region in the ordered pass up to chunk `--at-chunk`, then requests
every remaining chunk to FULL at once and removes every ticket `--delay-ms`
later, cancelling the scheduled generation. Once the chunks have unloaded and
saved, the audit dumps each persisted chunk around the region with its status.

A cancelled step must publish nothing partial: a chunk saved at a status holds
exactly what that status's whole steps produce. A chunk below FEATURES with no
decorated neighbour depends only on its position, so wherever two sides saved
one at the same status its saved form must be identical. Chunks beside a
decorated neighbour (whose decoration writes into them) and chunks at FEATURES
or later depend on the order the cancelled requests ran in; they are reported.

  python3 DevUtils/tests/worldgen/VerifyGenerationCancel.py --output build/generation-cancel/<label>
"""
import argparse
import gzip
import json
from pathlib import Path
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
import VerifyFullGeneration as full  # noqa: E402

FEATURES_OR_LATER = {'minecraft:features', 'minecraft:initialize_light', 'minecraft:light', 'minecraft:spawn', 'minecraft:full'}


def load(path):
    chunks = {}
    with gzip.open(path, 'rt') as f:
        lines = f.read().split('\n')
    for i in range(0, len(lines) - 1, 2):
        pos, status = lines[i].split(' ')
        chunks[pos] = (status, lines[i + 1])
    return chunks


def decorated_nearby(chunks, pos):
    """Whether a neighbour within one chunk reached FEATURES: its decoration writes here."""
    x, z = map(int, pos.split(','))
    return any(chunks.get(f'{x + dx},{z + dz}', ('absent', ''))[0] in FEATURES_OR_LATER
               for dx in (-1, 0, 1) for dz in (-1, 0, 1) if dx or dz)


def compare(a, b):
    same_status = differing_status = 0
    below, beside, later = [], [], []
    for pos, (status, form) in a.items():
        if pos not in b or status == 'absent':
            continue
        other_status, other_form = b[pos]
        if other_status != status:
            differing_status += 1
            continue
        same_status += 1
        if form == other_form:
            continue
        if status in FEATURES_OR_LATER:
            later.append(f'{pos} {status}')
        elif decorated_nearby(a, pos) or decorated_nearby(b, pos):
            beside.append(f'{pos} {status}')
        else:
            below.append(f'{pos} {status}')
    return {'same_status': same_status, 'differing_status': differing_status, 'differ_below_features': below,
            'differ_beside_decorated': beside, 'differ_features_or_later': later}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--at-chunk', type=int, default=20)
    parser.add_argument('--delay-ms', type=int, default=3000)
    parser.add_argument('--radius', type=int, default=3)
    parser.add_argument('--sides', default='frozen,current,current-java')
    parser.add_argument('--skip-build', action='store_true')
    args = parser.parse_args()
    out = args.output.resolve()
    if not out.is_relative_to(full.ROOT / 'build'):
        parser.error('output must be under build/')
    out.mkdir(parents=True, exist_ok=False)
    dumps = {}
    for side in args.sides.split(','):
        # One driver run per side: its own FULL comparison has nothing to read after a cancelled run.
        command = [sys.executable, str(Path(__file__).resolve().parent / 'VerifyFullGeneration.py'), '--output', str(out / side),
                   '--sides', side, '--rounds', '1', '--radius', str(args.radius), '--dimensions', 'minecraft:overworld',
                   f'--jvm-arg=-Daudit.cancelAtChunk={args.at_chunk}', f'--jvm-arg=-Daudit.cancelDelayMs={args.delay_ms}']
        if args.skip_build:
            command.append('--skip-build')
        with (out / f'{side}.log').open('w') as log:
            subprocess.run(command, stdout=log, stderr=subprocess.STDOUT)
        path = out / side / f'{side}-0' / 'cancelled.txt.gz'
        if not path.exists():
            print('missing dump for', side)
            sys.exit(1)
        dumps[side] = load(path)
        print(side, json.loads((out / side / f'{side}-0' / 'cancelled.json').read_text()), flush=True)
    sides = list(dumps)
    results = {}
    for i, a in enumerate(sides):
        for b in sides[i + 1:]:
            results[f'{a} vs {b}'] = compare(dumps[a], dumps[b])
            print(a, 'vs', b, json.dumps(results[f'{a} vs {b}']))
    (out / 'cancel.json').write_text(json.dumps(results, indent=2))
    accepted = all(not r['differ_below_features'] for r in results.values()) and all(r['same_status'] > 0 for r in results.values())
    print('Cancellation accepted:', accepted)
    sys.exit(0 if accepted else 1)


if __name__ == '__main__':
    main()
