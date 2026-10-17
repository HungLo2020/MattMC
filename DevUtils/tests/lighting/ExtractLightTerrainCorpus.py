#!/usr/bin/env python3
"""Extract a grid of saved FULL overworld chunks' block states for the light propagation tests.

Reads a MattMC server world's region files without modifying them and writes
gzip JSON in the heightmap corpus format (palette and packed words per
section). Saved light is not extracted: the tests light the terrain themselves.
The normal verification driver uses the committed corpus.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'worldgen'))
from ExtractBeardifierCorpus import chunks


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--world', type=Path, required=True)
    parser.add_argument('--seed', type=int, required=True)
    parser.add_argument('--x', type=int, required=True)
    parser.add_argument('--z', type=int, required=True)
    parser.add_argument('--size', type=int, default=6)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    wanted = {(args.x + dx, args.z + dz) for dx in range(args.size) for dz in range(args.size)}
    found, regions = {}, []
    for path in sorted((args.world / 'region').glob('r.*.mca')):
        used = False
        for chunk in chunks(path):
            key = (chunk['xPos'], chunk['zPos'])
            if key not in wanted:
                continue
            if chunk['Status'] != 'minecraft:full':
                raise RuntimeError('Chunk is not FULL: ' + str(key))
            sections = [{'y': s['Y'], 'palette': s['block_states']['palette'], 'data': s['block_states'].get('data', [])}
                        for s in chunk['sections'] if 'block_states' in s]
            found[key] = {'dimension': 'overworld', 'seed': args.seed, 'chunk': list(key), 'min_y': chunk['yPos'] * 16,
                          'height': len(sections) * 16, 'sections': sections}
            used = True
        if used:
            regions.append({'path': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
    if set(found) != wanted:
        raise RuntimeError('Missing chunks: ' + str(sorted(wanted - set(found))))
    payload = {'provenance': {'world': 'MattMC dedicated server, normal overworld, seed ' + str(args.seed), 'regions': regions,
                              'selection': f'{args.size}x{args.size} FULL chunks from ({args.x}, {args.z}); block states only'},
               'chunks': [found[key] for key in sorted(wanted, key=lambda k: (k[1], k[0]))]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with gzip.GzipFile(args.output, 'wb', mtime=0) as out:
        out.write((json.dumps(payload, separators=(',', ':')) + '\n').encode())
    print('Extracted', len(found), 'chunks to', args.output)


if __name__ == '__main__':
    main()
