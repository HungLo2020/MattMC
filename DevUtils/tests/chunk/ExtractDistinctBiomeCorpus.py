#!/usr/bin/env python3
"""Extract saved biome palettes/words from the existing 16-chunk profiling corpus."""
import argparse
import hashlib
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'worldgen'))
from ExtractBeardifierCorpus import chunks


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[3]
    original = json.loads((root / 'src/test/resources/worldgen/heightmap/chunks.json').read_text())
    selected = {(c['seed'], c['dimension'], *c['chunk']) for c in original['chunks']}
    result = []
    names = {'region': 'overworld', 'DIM-1/region': 'nether', 'DIM1/region': 'end',
             'dimensions/minecraft/primordial_caves/region': 'primordial'}
    for region in original['provenance']['regions']:
        path = args.profile / region['path']
        if hashlib.sha256(path.read_bytes()).hexdigest() != region['sha256']:
            raise RuntimeError('Profiling region changed: ' + str(path))
        seed = 42 if region['path'].startswith('baseline-seed42/') else -123456789
        directory = region['path'].split('/server/world/')[1].rsplit('/', 1)[0]
        dimension = names[directory]
        for chunk in chunks(path):
            if (seed, dimension, chunk['xPos'], chunk['zPos']) not in selected: continue
            result.append({'seed': seed, 'dimension': dimension, 'chunk': [chunk['xPos'], chunk['zPos']],
                'sections': [{'y': s['Y'], 'palette': s['biomes']['palette'],
                    'data': s['biomes'].get('data', [])} for s in chunk['sections'] if 'biomes' in s]})
    if len(result) != len(selected): raise RuntimeError('Missing selected chunks')
    payload = {'provenance': {**original['provenance'],
        'selection': 'Exact biome palettes and padded words from the same sixteen FULL chunks as the heightmap corpus.'},
        'chunks': result}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, separators=(',', ':')) + '\n')
    print('Extracted', len(result), 'chunks and', sum(len(c['sections']) for c in result), 'biome sections')


if __name__ == '__main__': main()
