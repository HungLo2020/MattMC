#!/usr/bin/env python3
'Counter resource adoption rollback and registered GC cleanup.'
from pathlib import Path
import json, subprocess, hashlib, os
import argparse
r = Path(__file__).resolve().parents[4]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--classpath-file', type=Path, default=r / 'build/chunk-generation/s25-test-classpath.txt')
parser.add_argument('--java-home', type=Path, default=r / 'libraries/jdk/linux-x64')
args = parser.parse_args()
out = args.output.resolve()
out.mkdir(parents=True, exist_ok=False)
p = r / 'src/main/java/net/minecraft/server/level/NativeTicket.java'
original = p.read_text()
native = r / 'build/rust/native/mattmc_rust-linux-x64.so'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
guards = {str(f): sha(f) for f in [p, native, r / 'build/classes/java/main/net/minecraft/server/level/NativeTicket.class']}
body = original.replace('final class NativeTicket {', 'final class NativeTicket {\n static final java.util.concurrent.atomic.AtomicInteger TEST_ATTEMPTS=new java.util.concurrent.atomic.AtomicInteger();\n static final java.util.concurrent.atomic.AtomicInteger TEST_RELEASES=new java.util.concurrent.atomic.AtomicInteger();\n static void testRelease(int cell) throws Throwable {if(TEST_RELEASES.incrementAndGet()==1)RELEASE.invokeExact(cell);}', 1)
a = 'return MemorySegment.ofAddress(address).reinterpret(CELL_BYTES, arena, cleanup).asReadOnly();'
assert body.count(a) == 1
body = body.replace(a, 'MemorySegment registered = MemorySegment.ofAddress(address).reinterpret(CELL_BYTES, arena, cleanup);\n if(Boolean.getBoolean("ticket.counter.registeredFailure"))throw new OutOfMemoryError("counter-registered-control");\n return registered;')
a = 'int owned = cell.getAndSet(-1);'
assert body.count(a) == 1
body = body.replace(a, 'TEST_ATTEMPTS.incrementAndGet();\n' + a)
a = 'RELEASE.invokeExact(owned);'
assert body.count(a) == 1
body = body.replace(a, 'testRelease(owned);')
probe = 'package net.minecraft.server.level;\npublic class TicketCounterAdoptionProbe {\n static void abandon(){\n  Ticket ticket=new Ticket(TicketType.PORTAL,33);\n  System.setProperty("ticket.counter.registeredFailure","true");\n  try {ticket.decreaseTicksLeft();throw new AssertionError("registered counter failure did not run");}\n  catch(OutOfMemoryError expected){if(!"counter-registered-control".equals(expected.getMessage()))throw expected;}\n  finally {System.clearProperty("ticket.counter.registeredFailure");}\n  if(ticket.boundCell()!=-1 || ticket.unboundTicksLeft()!=300)throw new AssertionError("failed counter adoption changed original state");\n }\n public static void main(String[] args)throws Exception{\n  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();abandon();\n  for(int i=0;i<160 && NativeTicket.TEST_ATTEMPTS.get()<2 && NativeTicket.TEST_RELEASES.get()<2;i++){System.gc();Thread.sleep(20);}\n  int releases=NativeTicket.TEST_RELEASES.get();\n  if(releases!=1)throw new AssertionError("registered counter cleanup released native resource "+releases+" times");\n  if(NativeTicket.TEST_ATTEMPTS.get()<2)throw new AssertionError("registered counter GC cleanup not observed");\n  System.out.println("COUNTER_ADOPTION_PASS attempts="+NativeTicket.TEST_ATTEMPTS.get()+" actualNativeRelease="+releases);\n }\n}\n'
cp = args.classpath_file.read_text().strip()
rows = []
for label, source in [('idempotent', body), ('raw-rollback', body.replace('cleanup.run();\n            throw failure(t);', 'try { testRelease((int)cell); } catch (Throwable second) { t.addSuppressed(second); }\n            throw failure(t);'))]:
    d = out / label
    src = d / 'source/net/minecraft/server/level'
    src.mkdir(parents=True)
    classes = d / 'classes'
    classes.mkdir()
    (src / 'NativeTicket.java').write_text(source)
    (src / 'TicketCounterAdoptionProbe.java').write_text(probe)
    with (d / 'javac.log').open('w') as f:
        subprocess.run([str(args.java_home / 'bin/javac'), '-g', '-cp', cp, '-d', str(classes), str(src / 'NativeTicket.java'), str(src / 'TicketCounterAdoptionProbe.java')], stdout=f, stderr=subprocess.STDOUT, check=True)
    c = [str(args.java_home / 'bin/java'), '-Xmx256m', '--enable-native-access=ALL-UNNAMED', '-Dmattmc.rust.natives.dir=' + str(native.parent), '-cp', str(classes) + os.pathsep + cp, 'net.minecraft.server.level.TicketCounterAdoptionProbe']
    (d / 'command.json').write_text(json.dumps(c, indent=2) + '\n')
    with (d / 'test.log').open('w') as f:
        done = subprocess.run(c, stdout=f, stderr=subprocess.STDOUT, timeout=60)
    log = (d / 'test.log').read_text()
    kill = done.returncode != 0 and 'registered counter cleanup released native resource 2 times' in log
    if label == 'idempotent':
        assert done.returncode == 0, log[-3000:]
    else:
        assert kill, log[-3000:]
    rows.append({'case': label, 'exit': done.returncode, 'intended_failure': kill, 'adapter_sha256': sha(src / 'NativeTicket.java')})
    print(label, 'PASS' if done.returncode == 0 else 'intended mutation detected', flush=True)
assert all((sha(Path(p)) == h for p, h in guards.items()))
(out / 'result.json').write_text(json.dumps({'passed': True, 'production_inputs': guards, 'rows': rows, 'second_raw_release_trapped': True}, indent=2) + '\n')
