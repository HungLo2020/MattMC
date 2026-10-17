#!/usr/bin/env python3
"""Losslessly compress independently recorded Frozen fixtures after SHA checks."""
import argparse
import gzip
import hashlib
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--biomes', type=Path, required=True)
    parser.add_argument('--sky', type=Path, required=True)
    parser.add_argument('--view-range', type=Path, help='optional actual Frozen client storage recording')
    parser.add_argument('--output-root', type=Path, required=True)
    args = parser.parse_args()
    records = (
        (args.biomes, '63b50ee1120956b4befcd6f8d2807bcf7f7b3534af6836a1ee8f56e5a67e6e38', 'chunk/biomes/frozen-live-biomes.bin.gz'),
        (args.sky, 'fd1fe4b1615f2a0568c6e968aebfe6c22cedf0fc25f21944db95615e30a00a7c', 'biome/live/frozen-sky-sampler.bin.gz'),
    )
    if args.view_range:
        records += ((args.view_range, 'bc0e259e47ad18c4916ad50d241c561f836dd664336e46054cd0190f3ed3ca9c', 'biome/live/frozen-view-range.bin.gz'),)
    validated = []
    for source, expected, relative in records:
        data = source.read_bytes()
        if hashlib.sha256(data).hexdigest() != expected:
            raise SystemExit(f'Unexpected Frozen recording: {source}')
        validated.append((relative, gzip.compress(data, mtime=0)))
    for relative, data in validated:
        output = args.output_root / relative
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_bytes(data)
        print(f'{relative}: {len(data)} bytes')


if __name__ == '__main__':
    main()
