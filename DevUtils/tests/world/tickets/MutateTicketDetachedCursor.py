#!/usr/bin/env python3
"""Require native cursors to continue in a retained list's actual detached backing."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[4]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--classpath-file', type=Path, default=root / 'build/chunk-generation/s25-test-classpath.txt')
parser.add_argument('--java-home', type=Path, default=root / 'libraries/jdk/linux-x64')
args = parser.parse_args()
out = args.output.resolve()
out.mkdir(parents=True, exist_ok=False)
adapter = root / 'src/main/java/net/minecraft/world/level/NativeTicketStorage.java'
fixture = root / 'src/test/java/net/minecraft/server/level/TicketDetachedCursorContractTest.java'
native = root / 'build/rust/native/mattmc_rust-linux-x64.so'
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
paths = [adapter, fixture, native]
paths.extend((root / 'build/classes/java/main/net/minecraft/world/level').glob('NativeTicketStorage*.class'))
guards = {str(path): digest(path) for path in paths}
source = adapter.read_text()
anchor = 'void next(long answer) {'
assert source.count(anchor) == 1
cp = args.classpath_file.read_text().strip()
template = json.loads((root / 'build/chunk-generation/s25-r4-caller-portable/current-command.json').read_text())
template = template[:template.index('GenerationContractRunner') + 1]
rows = []
for label, body in [('live-backing', source), ('lost-backing', source.replace(anchor, anchor + '\n            if (this.command() == 200) answer = 0;'))]:
    case = out / label
    src = case / 'source/net/minecraft/world/level/NativeTicketStorage.java'
    src.parent.mkdir(parents=True)
    src.write_text(body)
    saved_fixture = case / fixture.name
    saved_fixture.write_bytes(fixture.read_bytes())
    classes = case / 'classes'
    classes.mkdir()
    with (case / 'compile.log').open('w') as log:
        subprocess.run([str(args.java_home / 'bin/javac'), '-g', '-cp', cp, '-d', str(classes), str(src), str(saved_fixture)], stdout=log, stderr=subprocess.STDOUT, check=True)
    command = template.copy() + ['net.minecraft.server.level.TicketDetachedCursorContractTest']
    command[0] = str(args.java_home / 'bin/java')
    command[command.index('-cp') + 1] = str(classes) + os.pathsep + command[command.index('-cp') + 1]
    command = [('-Dmattmc.rust.natives.dir=' + str(native.parent)) if item.startswith('-Dmattmc.rust.natives.dir=') else item for item in command]
    (case / 'command.json').write_text(json.dumps(command, indent=2) + '\n')
    with (case / 'run.log').open('w') as log:
        done = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=90)
    log = (case / 'run.log').read_text()
    witnesses = ['expected: <false> but was: <true>', 'expected: <true> but was: <false>', 'minimumContinuesAtItsPositionInTheActualDetachedBacking']
    killed = done.returncode != 0 and '3 tests failed' in log and all(witness in log for witness in witnesses)
    assert (done.returncode == 0 and '6 tests successful' in log) if label == 'live-backing' else killed, log[-4500:]
    rows.append({'case': label, 'exit': done.returncode, 'intended_failure': killed, 'adapter_sha256': digest(src)})
    print(label, '6PASS' if done.returncode == 0 else 'all three intended failures detected', flush=True)
assert all(digest(Path(path)) == expected for path, expected in guards.items())
(out / 'result.json').write_text(json.dumps({'passed': True, 'production_inputs': guards, 'rows': rows, 'performance_acceptance': False}, indent=2) + '\n')
