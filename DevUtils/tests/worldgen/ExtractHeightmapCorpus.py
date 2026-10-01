#!/usr/bin/env python3
"""Read saved profiling chunks; preserve their exact block palettes and packed words."""
import argparse
import hashlib
import json
from pathlib import Path
from ExtractBeardifierCorpus import chunks


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    records, regions = [], []
    dimensions = {'overworld': 'region', 'nether': 'DIM-1/region', 'end': 'DIM1/region',
                  'primordial': 'dimensions/minecraft/primordial_caves/region'}
    for run, seed in [('baseline-seed42', 42), ('baseline-seed-negative', -123456789)]:
        for dimension, directory in dimensions.items():
            selected = 0
            for path in sorted((args.profile / run / 'server/world' / directory).glob('r.*.mca')):
                rx, rz = map(int, path.stem.split('.')[1:])
                if not (127 <= rx <= 137 and 127 <= rz <= 129):
                    continue
                used = False
                for chunk in chunks(path):
                    if chunk['Status'] != 'minecraft:full':
                        continue
                    sections = [{'y': s['Y'], 'palette': s['block_states']['palette'],
                                 'data': s['block_states'].get('data', [])}
                                for s in chunk['sections'] if 'block_states' in s]
                    min_y = chunk['yPos'] * 16
                    height = (max(s['y'] for s in sections) + 1) * 16 - min_y
                    records.append({'dimension': dimension, 'seed': seed,
                                    'chunk': [chunk['xPos'], chunk['zPos']],
                                    'min_y': min_y, 'height': height, 'sections': sections})
                    selected += 1
                    used = True
                    if selected == 2:
                        break
                if used:
                    regions.append({'path': str(path.relative_to(args.profile)),
                                    'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
                if selected == 2:
                    break
            if selected != 2:
                raise RuntimeError(f'Missing FULL chunks for {run}/{dimension}')
    payload = {'provenance': {'profile_revision': '0719ec4bd5f4956ceb9f341c9c641ad7294ef632',
                              'selection': 'First two FULL chunks per seed/dimension in lexical measured-region order.',
                              'regions': regions}, 'chunks': records}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, separators=(',', ':')) + '\n')
    print(f'Extracted {len(records)} complete packed chunks ({args.output.stat().st_size} bytes)')


if __name__ == '__main__':
    main()
