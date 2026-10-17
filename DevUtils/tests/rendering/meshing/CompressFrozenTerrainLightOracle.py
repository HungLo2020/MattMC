"""Losslessly compact the recorded Frozen production-light oracle, never Current."""
import argparse
import hashlib
import struct
from pathlib import Path


def compress(source: Path, output: Path) -> None:
    raw = source.read_bytes()
    expected = "f4599863f5b11a7274ae2c9c49ba701ae9b6e47df025be35020ada0f7b0ecadb"
    if hashlib.sha256(raw).hexdigest() != expected:
        raise ValueError("Input is not the recorded untouched Frozen terrain-light oracle")
    magic, count = struct.unpack_from(">II", raw)
    if magic != 0x4C574F52 or count != 31809 * 8 or len(raw) != 8 + count * 36:
        raise ValueError("Invalid Frozen oracle shape")
    rows, patterns, state_patterns = {}, {}, []
    for state in range(count // 8):
        pattern = []
        for variant in range(8):
            fields = struct.unpack_from(">9I", raw, 8 + (state * 8 + variant) * 36)
            if fields[:2] != (state, variant):
                raise ValueError("Noncanonical state/variant order")
            pattern.append(rows.setdefault(fields[2:], len(rows)))
        state_patterns.append(patterns.setdefault(tuple(pattern), len(patterns)))
    runs = []
    for pattern in state_patterns:
        if runs and runs[-1][1] == pattern:
            runs[-1] = (runs[-1][0] + 1, pattern)
        else:
            runs.append((1, pattern))
    packed = bytearray(struct.pack(">4I", 0x544C4932, len(state_patterns), 8, len(rows)))
    for row in rows:
        packed.extend(struct.pack(">7I", *row))
    packed.extend(struct.pack(">I", len(patterns)))
    for pattern in patterns:
        packed.extend(struct.pack(">8H", *pattern))
    packed.extend(struct.pack(">I", len(runs)))
    for length, pattern in runs:
        packed.extend(struct.pack(">2H", length, pattern))
    output.write_bytes(packed)
    print(f"Retained {count} Frozen rows in {len(packed)} bytes")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    compress(args.source, args.output)
