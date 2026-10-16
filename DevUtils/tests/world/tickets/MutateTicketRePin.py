#!/usr/bin/env python3
'A selected native release must not erase a later Java strong root.'
from pathlib import Path
import subprocess, json, hashlib, os
import argparse
r = Path(__file__).resolve().parents[4]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--classpath-file', type=Path, default=r / 'build/chunk-generation/s25-test-classpath.txt')
parser.add_argument('--java-home', type=Path, default=r / 'libraries/jdk/linux-x64')
parser.add_argument('--adapter-source', type=Path, default=r / 'src/main/java/net/minecraft/world/level/NativeTicketStorage.java')
parser.add_argument('--native-dir', type=Path, default=r / 'build/rust/native')
args = parser.parse_args()
out = args.output.resolve()
out.mkdir(parents=True, exist_ok=False)
p = args.adapter_source.resolve()
original = p.read_text()
native = args.native_dir.resolve() / 'mattmc_rust-linux-x64.so'
sha = lambda f: hashlib.sha256(f.read_bytes()).hexdigest()
guards = {str(f): sha(f) for f in [p, native, r / 'build/classes/java/main/net/minecraft/world/level/NativeTicketStorage.class']}
base = original
anchor = 'Ticket[] tickets = strong;'
if base.count(anchor) != 1:
    parser.error('This retained driver targets the legacy global-array protocol. Use MutateTicketScopedRoots.py for current code, or select a sealed compatible legacy adapter/classpath/native image explicitly.')
base = base.replace(anchor, 'TicketRePinProbe.selected(drained, count);\n' + anchor)
a = base.index('static void pin(Ticket ticket) {')
b = base.index('\n\t\tprivate static long address', a)
part = base[a:b]
anchor = '\t\t\tsynchronized (LOCK) {'
assert part.count(anchor) == 1
part = part.replace(anchor, 'TicketRePinProbe.attempting(cell);\n' + anchor)
part = part.replace('strong[cell] = ticket;', 'strong[cell] = ticket; TicketRePinProbe.pinned();')
base = base[:a] + part + base[b:]
a = base.index('static Prepared prepare(Ticket ticket, long initial) {')
b = base.index('static final class Prepared', a)
part = base[a:b]
anchor = '                synchronized (LOCK) {'
assert part.count(anchor) == 2
part = part.replace(anchor, 'TicketRePinProbe.attempting(cell);\n' + anchor, 1)
base = base[:a] + part + base[b:]
a = base.index('static final class Prepared')
b = base.index('/** Binds {@code ticket}', a)
part = base[a:b].replace('strong[cell] = ticket;', 'strong[cell] = ticket; TicketRePinProbe.pinned();')
base = base[:a] + part + base[b:]
a = base.index('static void drain() {')
start = base.index('synchronized (LOCK) {', a)
op = base.index('{', start)
depth = 1
close = op + 1
while depth:
    if base[close] == '{':
        depth += 1
    if base[close] == '}':
        depth -= 1
    close += 1
mutant = base[:start] + base[op + 1:close - 1] + base[close:]
cp = args.classpath_file.read_text().strip()
rows = []
for label, body in [('serialized', base), ('stale-unpin', mutant)]:
    d = out / label
    src = d / 'source/net/minecraft/world/level'
    src.mkdir(parents=True)
    classes = d / 'classes'
    classes.mkdir()
    (src / 'NativeTicketStorage.java').write_text(body)
    (src / 'TicketRePinProbe.java').write_text(Path(__file__).with_name('TicketRePinProbe.java').read_text())
    with (d / 'javac.log').open('w') as f:
        subprocess.run([str(args.java_home / 'bin/javac'), '-g', '-cp', cp, '-d', str(classes), str(src / 'NativeTicketStorage.java'), str(src / 'TicketRePinProbe.java')], stdout=f, stderr=subprocess.STDOUT, check=True)
    for recycle in ['false', 'true']:
        c = [str(args.java_home / 'bin/java'), '-Xmx256m', '--enable-native-access=ALL-UNNAMED', '-Dmattmc.rust.natives.dir=' + str(native.parent), '-cp', str(classes) + os.pathsep + cp, 'net.minecraft.world.level.TicketRePinProbe', recycle]
        (d / (recycle + '-command.json')).write_text(json.dumps(c, indent=2) + '\n')
        with (d / (recycle + '.log')).open('w') as f:
            done = subprocess.run(c, stdout=f, stderr=subprocess.STDOUT, timeout=45)
        log = (d / (recycle + '.log')).read_text()
        kill = done.returncode != 0 and 'stale drain unpin erased the newer required strong root' in log
        if label == 'serialized':
            assert done.returncode == 0, log[-4000:]
        else:
            assert kill, log[-4000:]
        rows.append({'case': label, 'recycled_slot': recycle == 'true', 'exit': done.returncode, 'intended_failure': kill, 'adapter_sha256': sha(src / 'NativeTicketStorage.java')})
        print(label, recycle, 'PASS' if done.returncode == 0 else 'intended mutation detected', flush=True)
assert all((sha(Path(f)) == h for f, h in guards.items()))
(out / 'result.json').write_text(json.dumps({'passed': True, 'production_inputs': guards, 'production_unchanged': True, 'rows': rows}, indent=2) + '\n')
