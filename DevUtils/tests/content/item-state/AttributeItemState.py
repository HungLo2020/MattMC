#!/usr/bin/env python3
"""Bounded attribution on an unchanged candidate; not a baseline or acceptance run."""
from __future__ import annotations
import argparse
import csv
import json
from pathlib import Path
import statistics
import subprocess
import time
from VerifyItemState import ROOT, class_seal, sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--candidate', type=Path, required=True, help='focused sealed candidate receipt directory')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    out = args.output.resolve()
    if not out.is_relative_to(ROOT / 'build') or out.exists():
        parser.error('--output must be a new build/ directory')
    candidate = json.loads((args.candidate / 'receipt.json').read_text())
    before = class_seal(); assert before == candidate['compiled_classes']
    native = ROOT / 'build/rust/native'
    image = native / 'mattmc_rust-linux-x64.so'; identity = {'sha256': sha(image), 'inode': image.stat().st_ino}
    assert identity['sha256'] == candidate['native_sha256']
    source = Path(__file__).with_suffix('.java'); source_hash = sha(source)
    out.mkdir(); classes = out / 'observer'; classes.mkdir(); (out / source.name).write_bytes(source.read_bytes())
    cp = (ROOT / 'build/item-state-current-classpath.txt').read_text().strip()
    receipt = {'kind': 'ATTRIBUTION ONLY; one current JVM, five dependent samples, excluded stages explicitly named; timings are not additive',
               'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
               'candidate': str(args.candidate), 'native': identity, 'compiled_classes': before,
               'source_sha256': source_hash, 'driver_sha256': sha(Path(__file__)), 'complete': False}
    def save(): (out / 'receipt.json').write_text(json.dumps(receipt, indent=2))
    save()
    subprocess.run([str(args.java_home / 'bin/javac'), '-cp', cp, '-d', str(classes), str(source)], check=True)
    command = [str(args.java_home / 'bin/java'), '-Xmx1g', '--enable-native-access=ALL-UNNAMED',
               '-Dmattmc.rust.natives.dir=' + str(native), '-cp', str(classes) + ':' + cp,
               'net.minecraft.world.item.AttributeItemState', str(out / 'samples.csv')]
    mapped = []; start = time.monotonic()
    with (out / 'observer.log').open('w') as log:
        process = subprocess.Popen(command, cwd=out, stdout=log, stderr=subprocess.STDOUT)
        try:
            while process.poll() is None:
                if not mapped:
                    try: mapped = [line for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines() if 'mattmc_rust' in line]
                    except FileNotFoundError: pass
                if time.monotonic() - start > 240: raise TimeoutError('attribution observer')
                time.sleep(.1)
        finally:
            if process.poll() is None:
                process.terminate()
                try: process.wait(timeout=5)
                except subprocess.TimeoutExpired: process.kill(); process.wait()
    assert process.returncode == 0, process.returncode
    assert mapped
    for line in mapped:
        fields = line.split(maxsplit=5)
        assert Path(fields[5]).resolve() == image.resolve() and int(fields[4]) == identity['inode']
    assert sha(image) == identity['sha256'] and image.stat().st_ino == identity['inode']
    assert class_seal() == before and sha(source) == source_hash
    rows = list(csv.DictReader(line for line in (out / 'samples.csv').read_text().splitlines() if not line.startswith('#')))
    assert len(rows) == 60
    summary = {}
    for name in dict.fromkeys(row['workload'] for row in rows):
        selected = [row for row in rows if row['workload'] == name]
        assert len({row['checksum'] for row in selected}) == 1, (name, selected)
        quiet = [int(row['elapsed_ns']) / int(row['operations']) for row in selected if row['compiler_ms'] == '0' and row['gc_count'] == '0']
        summary[name] = {'quiet_samples': len(quiet), 'quiet_median_ns': statistics.median(quiet) if quiet else None,
                         'all_ns': [int(row['elapsed_ns']) / int(row['operations']) for row in selected],
                         'allocated_bytes_per_operation': [int(row['allocated_bytes']) / int(row['operations']) for row in selected]}
    receipt.update({'command': command, 'native_mappings': mapped, 'rows': rows, 'summary': summary, 'complete': True})
    save(); print(json.dumps(summary, indent=2), flush=True)

if __name__ == '__main__': main()
