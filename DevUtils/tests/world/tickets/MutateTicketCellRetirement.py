#!/usr/bin/env python3
'Native cell retirement serializes resource release, drain and reuse.'
from pathlib import Path
import subprocess, json, hashlib
import argparse
r = Path(__file__).resolve().parents[4]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--classpath-file', type=Path, default=r / 'build/chunk-generation/s25-test-classpath.txt')
parser.add_argument('--java-home', type=Path, default=r / 'libraries/jdk/linux-x64')
args = parser.parse_args()
out = args.output.resolve()
out.mkdir(parents=True, exist_ok=False)
p = r / 'src/main/rust/world/level/chunk_distance/ticket_storage/slab.rs'
original = p.read_text()
sha = lambda s: hashlib.sha256(s.encode()).hexdigest()
a = original.index('pub(crate) fn release_resource(slot: u32) {')
b = original.index('\npub(crate) fn resource_address', a)
unlocked = original[:a] + 'pub(crate) fn release_resource(slot: u32) {\n let cell=cell(slot);\n if cell.owners.fetch_update(Ordering::AcqRel, Ordering::Acquire,|n|n.checked_sub(1))==Ok(1) && cell.refs.load(Ordering::Acquire)==0 {\n  #[cfg(test)] cleanup_pause::pause(slot);\n  free(slot);\n }\n}\n' + original[b:]
queued = original.replace('if !cell.queued.load(Ordering::Acquire) { free_locked(&mut free, slot); }', 'free_locked(&mut free, slot);')
assert queued != original
cases = [('repaired', original, None), ('unlocked-last-owner', unlocked, 'late resource cleanup freed a later reservation'), ('lost-unpin', queued, 'assertion failed: cell(slot).live.load(Ordering::Acquire)')]
rows = []
for label, source, witness in cases:
    d = out / label
    d.mkdir()
    (d / 'slab.rs').write_text(source)
    (d / 'probe.rs').write_text('#[derive(Debug)] enum Fault { Allocation, IllegalState }\nmod slab;\n')
    with (d / 'compile.log').open('w') as f:
        subprocess.run(['rustc', '--edition=2021', '--test', '-O', str(d / 'probe.rs'), '-o', str(d / 'probe')], stdout=f, stderr=subprocess.STDOUT, check=True)
    c = [str(d / 'probe'), '--test-threads=1', '--nocapture']
    (d / 'command.json').write_text(json.dumps(c, indent=2) + '\n')
    with (d / 'test.log').open('w') as f:
        done = subprocess.run(c, stdout=f, stderr=subprocess.STDOUT, timeout=30)
    log = (d / 'test.log').read_text()
    kill = done.returncode != 0 and witness in log if witness else False
    if witness:
        assert kill, log[-4000:]
    else:
        assert done.returncode == 0 and '4 passed' in log, log[-4000:]
    rows.append({'case': label, 'exit': done.returncode, 'source_sha256': sha(source), 'intended_failure': kill})
    print(label, 'PASS' if done.returncode == 0 else 'intended mutation detected', flush=True)
assert p.read_text() == original
(out / 'result.json').write_text(json.dumps({'passed': True, 'production_source_sha256': sha(original), 'production_unchanged': True, 'scope': 'actual slab compiled as isolated std-only Rust test crate; production cfg(test) barriers; no mutation of production source/library', 'rows': rows}, indent=2) + '\n')
