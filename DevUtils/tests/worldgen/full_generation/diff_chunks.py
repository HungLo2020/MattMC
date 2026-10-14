#!/usr/bin/env python3
"""Decode two FULL-generation dumps and list differing blocks per chunk.

  python3 DevUtils/tests/worldgen/full_generation/diff_chunks.py A/chunks.txt.gz B/chunks.txt.gz \
      --dimension minecraft:overworld --label measure-1 [--chunk 60,-47] [--limit 40]
"""
import argparse
import collections
import gzip
import re


def parse(text):
    """Parses the audit's canonical SNBT subset into Python values."""
    pos = 0
    number = re.compile(r'-?[0-9.eE+-]+[bBsSlLfFdD]?')

    def value():
        nonlocal pos
        c = text[pos]
        if c == '{':
            pos += 1
            out = {}
            while text[pos] != '}':
                key = string() if text[pos] == '"' else bare()
                pos += 1  # ':'
                out[key] = value()
                pos += 1  # ','
            pos += 1
            return out
        if c == '[':
            pos += 1
            if text[pos + 1] == ';':
                kind = text[pos]
                pos += 2
                end = text.index(']', pos)
                body = text[pos:end]
                pos = end + 1
                return [int(x.rstrip('LBlb')) for x in body.split(',') if x]
            out = []
            while text[pos] != ']':
                out.append(value())
                pos += 1
            pos += 1
            return out
        if c in '"\'':
            return string()
        m = number.match(text, pos)
        if m and m.end() > pos:
            pos = m.end()
            return m.group(0)
        return bare()

    def string():
        nonlocal pos
        quote = text[pos]
        pos += 1
        out = []
        while text[pos] != quote:
            if text[pos] == '\\':
                pos += 1
            out.append(text[pos])
            pos += 1
        pos += 1
        return ''.join(out)

    def bare():
        nonlocal pos
        start = pos
        while text[pos] not in ':,}]':
            pos += 1
        return text[start:pos]

    return value()


def load(path, dimension, label):
    chunks = {}
    with gzip.open(path, 'rt') as stream:
        lines = stream.read().split('\n')
    i = 0
    while i < len(lines):
        header = lines[i].split()
        if len(header) != 3:
            i += 1
            continue
        dim, lab, pos = header
        if dim == dimension and lab == label:
            chunks[pos] = lines[i + 1]
        i += 2
    return chunks


def state_name(entry):
    props = entry.get('Properties')
    name = entry['Name']
    return name + ('[' + ','.join(f'{k}={v}' for k, v in sorted(props.items())) + ']' if props else '')


def decode(section):
    states = section.get('block_states', {})
    palette = [state_name(p) for p in states.get('palette', [])]
    data = states.get('data')
    if not data:
        return [palette[0]] * 4096
    bits = max(4, (len(palette) - 1).bit_length())
    per = 64 // bits
    mask = (1 << bits) - 1
    out = []
    for i in range(4096):
        word = data[i // per] & ((1 << 64) - 1)
        out.append(palette[(word >> (i % per * bits)) & mask])
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('a')
    parser.add_argument('b')
    parser.add_argument('--dimension', default='minecraft:overworld')
    parser.add_argument('--label', default='measure-0')
    parser.add_argument('--chunk', action='append')
    parser.add_argument('--limit', type=int, default=30)
    args = parser.parse_args()
    a, b = load(args.a, args.dimension, args.label), load(args.b, args.dimension, args.label)
    summary = collections.Counter()
    for pos in sorted(set(a) & set(b)):
        if args.chunk and pos not in args.chunk or a[pos] == b[pos]:
            continue
        ca, cb = parse(a[pos]), parse(b[pos])
        cx, cz = map(int, pos.split(','))
        sa = {int(str(s['Y']).rstrip('b')): s for s in ca['sections']}
        sb = {int(str(s['Y']).rstrip('b')): s for s in cb['sections']}
        shown = 0
        for y in sorted(set(sa) | set(sb)):
            da, db = decode(sa.get(y, {})), decode(sb.get(y, {}))
            for i, (x, z) in enumerate(zip(da, db)):
                if x != z:
                    summary[(x, z)] += 1
                    if shown < args.limit:
                        print(pos, 'block', cx * 16 + (i & 15), y * 16 + (i >> 8), cz * 16 + ((i >> 4) & 15), x, '->', z)
                        shown += 1
        for key in sorted(set(ca) | set(cb)):
            if key != 'sections' and ca.get(key) != cb.get(key):
                print(pos, 'field', key, str(ca.get(key))[:300], '->', str(cb.get(key))[:300])
    print('changed state pairs (A -> B):')
    for (x, z), n in summary.most_common(25):
        print(f'  {n:6d} {x} -> {z}')


if __name__ == '__main__':
    main()
