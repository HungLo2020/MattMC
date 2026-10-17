#!/usr/bin/env python3
"""Extract saved chunks' compressed NBT from a MattMC world for the chunk serialization tests.

Reads region files without modifying them. Selects FULL chunks and chunks
saved at earlier generation statuses, and writes their zlib payloads as
`[magic 'CNBT', count, then per chunk: x, z, length, bytes]` (big-endian).
The normal verification driver uses the committed corpus.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys
import zlib

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'worldgen'))
from ExtractBeardifierCorpus import Reader


def payloads(path):
    with path.open('rb') as f:
        headers = f.read(4096)
        for i in range(1024):
            offset = int.from_bytes(headers[i * 4:i * 4 + 3], 'big')
            if not offset:
                continue
            f.seek(offset * 4096)
            n = int.from_bytes(f.read(4), 'big')
            kind = f.read(1)[0]
            data = f.read(n - 1)
            if kind != 2:
                continue
            r = Reader(zlib.decompress(data))
            t = r.num('B')
            r.string()
            tag = r.tag(t)
            yield tag['xPos'], tag['zPos'], tag['Status'], data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--world', type=Path, required=True)
    parser.add_argument('--full', type=int, default=24)
    parser.add_argument('--per-status', type=int, default=4)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    chosen, counts, regions = [], {}, []
    for path in sorted((args.world / 'region').glob('r.*.mca')):
        used = False
        for x, z, status, data in payloads(path):
            limit = args.full if status == 'minecraft:full' else args.per_status
            if counts.get(status, 0) >= limit:
                continue
            counts[status] = counts.get(status, 0) + 1
            chosen.append((x, z, data))
            used = True
        if used:
            regions.append({'path': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
    out = bytearray(b'CNBT' + struct.pack('>i', len(chosen)))
    for x, z, data in chosen:
        out += struct.pack('>iii', x, z, len(data)) + data
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(bytes(out))
    args.output.with_suffix('.json').write_text(json.dumps({'regions': regions, 'statuses': counts}, indent=1) + '\n')
    print('Extracted', len(chosen), 'chunks', counts)


if __name__ == '__main__':
    main()
