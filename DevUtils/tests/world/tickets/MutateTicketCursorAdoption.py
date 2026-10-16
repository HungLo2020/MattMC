#!/usr/bin/env python3
"""Check source-owned cursor adoption rollback, retry and exactly-once return."""
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
native = root / 'build/rust/native/mattmc_rust-linux-x64.so'
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
paths = [adapter, native]
paths.extend((root / 'build/classes/java/main/net/minecraft/world/level').glob('NativeTicketStorage*.class'))
guards = {str(path): digest(path) for path in paths}
body = adapter.read_text()
anchor = 'final class NativeTicketStorage {'
assert body.count(anchor) == 1
body = body.replace(anchor, anchor + '\n    static volatile long TEST_CURSOR_ADDRESS;')
anchor = 'this.address = created;'
assert body.count(anchor) == 1
body = body.replace(anchor, anchor + '\n            TEST_CURSOR_ADDRESS = created;')
anchor = 'prepared = MemorySegment.ofAddress(created).reinterpret(80).asReadOnly();'
assert body.count(anchor) == 1
hook = '\n                        if (Boolean.getBoolean("ticket.cursor.bindingFailure")) throw new OutOfMemoryError("cursor-binding-control");'
body = body.replace(anchor, anchor + hook)
early_open = '\n                        if (mode == 0) storage.modifications++;\n                        check((long)CURSOR_OPEN.invokeExact(storage.id, created, prepared.get(J, 72), value, mode));'
abort = 'check((long)CURSOR_ABORT.invokeExact(storage.id, created));'
assert body.count(abort) == 1
clear = 'this.generation = 0;'
assert body.count(clear) == 1
probe = '''package net.minecraft.world.level;
public final class TicketCursorAdoptionProbe {
 public static void main(String[] args) throws Exception {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  TicketStorage storage=new TicketStorage();
  var field=TicketStorage.class.getDeclaredField("tickets");field.setAccessible(true);
  NativeTicketStorage source=(NativeTicketStorage)field.get(storage);
  System.setProperty("ticket.cursor.bindingFailure","true");
  try {try(var ignored=source.admission(7L)){throw new AssertionError("cursor binding failure did not run");}}
  catch(OutOfMemoryError expected){if(!"cursor-binding-control".equals(expected.getMessage()))throw expected;}
  finally {System.clearProperty("ticket.cursor.bindingFailure");}
  long failed=NativeTicketStorage.TEST_CURSOR_ADDRESS;
  if(storage.hasTickets())throw new AssertionError("cursor source mutated before VM binding adoption");
  try(var cursor=source.admission(7L)){
   if(NativeTicketStorage.TEST_CURSOR_ADDRESS!=failed)throw new AssertionError("failed cursor adoption leaked its native reservation");
   if(!cursor.list.isEmpty())throw new AssertionError("unexpected ticket during cursor adoption retry");
   try {cursor.close();cursor.close();}
   catch(IllegalStateException failure){throw new AssertionError("cursor close released one native reservation twice",failure);}
  }
  var ticket=new net.minecraft.server.level.Ticket(net.minecraft.server.level.TicketType.PORTAL,33);
  if(!storage.addTicket(7L,ticket)||storage.getTickets(7L).getFirst()!=ticket)throw new AssertionError("cursor adoption retry lost the actual Ticket");
  System.out.println("CURSOR_ADOPTION_PASS rollbackBeforeSource=true retrySameSlot=true exactlyOnce=true");
 }
}
'''
cp = args.classpath_file.read_text().strip()
cases = [
    ('owned-source', body, None),
    ('early-source-publication', body.replace(hook, early_open + hook), 'cursor source mutated before VM binding adoption'),
    ('missing-reservation-return', body.replace(abort, ';'), 'failed cursor adoption leaked its native reservation'),
    ('repeated-raw-return', body.replace(clear, '/* control: do not clear the owned generation */'), 'cursor close released one native reservation twice'),
]
rows = []
for label, source, witness in cases:
    case = out / label
    package = case / 'source/net/minecraft/world/level'
    package.mkdir(parents=True)
    copied = package / 'NativeTicketStorage.java'
    copied.write_text(source)
    test = package / 'TicketCursorAdoptionProbe.java'
    test.write_text(probe)
    classes = case / 'classes'
    classes.mkdir()
    with (case / 'compile.log').open('w') as log:
        subprocess.run([str(args.java_home / 'bin/javac'), '-g', '-cp', cp, '-d', str(classes), str(copied), str(test)], stdout=log, stderr=subprocess.STDOUT, check=True)
    command = [str(args.java_home / 'bin/java'), '-Xmx256m', '--enable-native-access=ALL-UNNAMED', '-Dmattmc.rust.natives.dir=' + str(native.parent), '-cp', str(classes) + os.pathsep + cp, 'net.minecraft.world.level.TicketCursorAdoptionProbe']
    (case / 'command.json').write_text(json.dumps(command, indent=2) + '\n')
    with (case / 'run.log').open('w') as log:
        done = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=90)
    log = (case / 'run.log').read_text()
    killed = witness is not None and done.returncode != 0 and witness in log
    assert (done.returncode == 0 and 'CURSOR_ADOPTION_PASS' in log) if witness is None else killed, log[-4000:]
    rows.append({'case': label, 'exit': done.returncode, 'intended_failure': killed, 'adapter_sha256': digest(copied)})
    print(label, 'PASS' if done.returncode == 0 else 'intended mutation detected', flush=True)
assert all(digest(Path(path)) == expected for path, expected in guards.items())
(out / 'result.json').write_text(json.dumps({'passed': True, 'production_inputs': guards, 'rows': rows, 'performance_acceptance': False}, indent=2) + '\n')
