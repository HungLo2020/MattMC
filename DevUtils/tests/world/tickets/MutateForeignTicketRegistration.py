#!/usr/bin/env python3
'Foreign Ticket reservation cleanup must be idempotent.'
from pathlib import Path
import subprocess, json, hashlib, os
import argparse
r = Path(__file__).resolve().parents[4]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--classpath-file', type=Path, default=r / 'build/chunk-generation/s25-test-classpath.txt')
parser.add_argument('--java-home', type=Path, default=r / 'libraries/jdk/linux-x64')
args = parser.parse_args()
out = args.output.resolve()
out.mkdir(parents=True, exist_ok=False)
p = r / 'src/main/java/net/minecraft/world/level/NativeTicketStorage.java'
original = p.read_text()
native = r / 'build/rust/native/mattmc_rust-linux-x64.so'
sha = lambda f: hashlib.sha256(f.read_bytes()).hexdigest()
guards = {str(f): sha(f) for f in [p, native, r / 'build/classes/java/main/net/minecraft/world/level/NativeTicketStorage.class']}
body = original.replace('final class NativeTicketStorage {', 'final class NativeTicketStorage {\n static final java.util.concurrent.atomic.AtomicInteger TEST_CELL_ATTEMPTS=new java.util.concurrent.atomic.AtomicInteger();\n static final java.util.concurrent.atomic.AtomicInteger TEST_CELL_FREES=new java.util.concurrent.atomic.AtomicInteger();', 1)
a = 'java.lang.ref.Cleaner.Cleanable cleanup = CLEANER.register(ticket, release);'
assert body.count(a) == 1
body = body.replace(a, a + '\n if(Boolean.getBoolean("ticket.cell.registeredFailure"))throw new OutOfMemoryError("cell-registered-control");')
a = 'int cell = slot.getAndSet(-1);'
assert body.count(a) == 1
body = body.replace(a, 'TEST_CELL_ATTEMPTS.incrementAndGet();\n' + a)
a = 'int freed = (int)CELLS_FREE.invokeExact(cell);'
assert body.count(a) == 1
body = body.replace(a, 'int freed = TEST_CELL_FREES.incrementAndGet()==1 ? (int)CELLS_FREE.invokeExact(cell) : 0;')
probe = 'package net.minecraft.world.level;\nimport java.lang.ref.WeakReference;\nimport net.minecraft.server.level.*;\npublic class TicketCellRegistrationProbe {\n static WeakReference<Ticket> abandon(){\n  TicketStorage storage=new TicketStorage();Ticket ticket=new Ticket(TicketType.PORTAL,33) {};\n  System.setProperty("ticket.cell.registeredFailure","true");\n  try {storage.addTicket(7L,ticket);throw new AssertionError("registered cell failure not exercised");}\n  catch(OutOfMemoryError expected){if(!"cell-registered-control".equals(expected.getMessage()))throw expected;}\n  finally {System.clearProperty("ticket.cell.registeredFailure");}\n  if(!storage.getTickets(7L).isEmpty())throw new AssertionError("entry published before registered adoption completed");\n  if(ticket.boundCell()!=-1)throw new AssertionError("failed reservation bound the ticket");\n  return new WeakReference<>(ticket);\n }\n public static void main(String[] args)throws Exception{\n  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();\n  WeakReference<Ticket> gone=abandon();\n  for(int i=0;i<160 && (gone.get()!=null || NativeTicketStorage.TEST_CELL_ATTEMPTS.get()<2);i++){System.gc();Thread.sleep(20);}\n  if(gone.get()!=null)throw new AssertionError("abandoned ticket retained");\n  if(NativeTicketStorage.TEST_CELL_ATTEMPTS.get()<2)throw new AssertionError("registered GC cleanup not observed");\n  int frees=NativeTicketStorage.TEST_CELL_FREES.get();\n  if(frees!=1)throw new AssertionError("registered cell cleanup released reservation "+frees+" times");\n  System.out.println("CELL_REGISTRATION_PASS manualAndGc="+NativeTicketStorage.TEST_CELL_ATTEMPTS.get()+" nativeRelease="+frees);\n }\n}\n'
cp = args.classpath_file.read_text().strip()
rows = []
for label, source in [('idempotent', body), ('repeated-release', body.replace('slot.getAndSet(-1)', 'slot.get()'))]:
    d = out / label
    src = d / 'source/net/minecraft/world/level'
    src.mkdir(parents=True)
    classes = d / 'classes'
    classes.mkdir()
    (src / 'NativeTicketStorage.java').write_text(source)
    (src / 'TicketCellRegistrationProbe.java').write_text(probe)
    with (d / 'javac.log').open('w') as f:
        subprocess.run([str(args.java_home / 'bin/javac'), '-g', '-cp', cp, '-d', str(classes), str(src / 'NativeTicketStorage.java'), str(src / 'TicketCellRegistrationProbe.java')], stdout=f, stderr=subprocess.STDOUT, check=True)
    c = [str(args.java_home / 'bin/java'), '-Xmx256m', '--enable-native-access=ALL-UNNAMED', '-Dmattmc.rust.natives.dir=' + str(native.parent), '-cp', str(classes) + os.pathsep + cp, 'net.minecraft.world.level.TicketCellRegistrationProbe']
    (d / 'command.json').write_text(json.dumps(c, indent=2) + '\n')
    with (d / 'test.log').open('w') as f:
        result = subprocess.run(c, stdout=f, stderr=subprocess.STDOUT, timeout=60)
    log = (d / 'test.log').read_text()
    kill = result.returncode != 0 and 'registered cell cleanup released reservation 2 times' in log
    if label == 'idempotent':
        assert result.returncode == 0, log[-3000:]
    else:
        assert kill, log[-3000:]
    rows.append({'case': label, 'exit': result.returncode, 'intended_failure': kill, 'isolated_adapter_sha256': sha(src / 'NativeTicketStorage.java')})
    print(label, 'PASS' if result.returncode == 0 else 'intended mutation detected', flush=True)
assert all((sha(Path(f)) == h for f, h in guards.items()))
(out / 'result.json').write_text(json.dumps({'passed': True, 'production_inputs': guards, 'rows': rows, 'negative_second_raw_release_trapped': True}, indent=2) + '\n')
