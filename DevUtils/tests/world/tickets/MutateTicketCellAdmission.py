#!/usr/bin/env python3
'Ticket entry publication must follow actual VM resource preparation.'
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
source = r / 'src/main/java/net/minecraft/world/level/NativeTicketStorage.java'
original = source.read_text()
native = r / 'build/rust/native/mattmc_rust-linux-x64.so'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
guards = {str(p): sha(p) for p in [source, native, r / 'build/classes/java/main/net/minecraft/world/level/NativeTicketStorage.class']}
probe = 'package net.minecraft.world.level;\nimport static org.junit.jupiter.api.Assertions.*;\nimport com.mojang.serialization.JsonOps;\nimport net.minecraft.server.level.*;\nimport org.junit.jupiter.api.*;\nclass TicketCellAdoptionFailureContractTest {\n @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}\n @Test void publishedTicketKeepsItsCounterAfterAdoptionFailure(){\n  TicketStorage storage=new TicketStorage();Ticket ticket=new Ticket(TicketType.PORTAL,33);\n  System.setProperty("ticket.cell.adoptionFailure","true");\n  try {assertEquals("ticket-cell-adoption-control",assertThrows(OutOfMemoryError.class,()->storage.addTicket(7L,ticket)).getMessage());}\n  finally {System.clearProperty("ticket.cell.adoptionFailure");}\n  assertTrue(storage.getTickets(7L).isEmpty(),"new ticket entry was published before its resources were prepared");\n  assertEquals(-1,ticket.boundCell());\n  assertTrue(storage.addTicket(7L,ticket));\n  assertSame(ticket,storage.getTickets(7L).get(0));\n  ChunkMap map=org.mockito.Mockito.mock(ChunkMap.class);storage.purgeStaleTickets(map);storage.purgeStaleTickets(map);\n  long actual=Ticket.CODEC.codec().encodeStart(JsonOps.INSTANCE,ticket).getOrThrow().getAsJsonObject().get("ticks_left").getAsLong();\n  assertEquals(298L,actual,"published ticket lost its actual stored counter after adoption failure");\n }\n}\n'
cp = args.classpath_file.read_text().strip()
hook = 'if(Boolean.getBoolean("ticket.cell.adoptionFailure")){System.clearProperty("ticket.cell.adoptionFailure");throw new OutOfMemoryError("ticket-cell-adoption-control");}\n'
anchor = 'java.lang.ref.WeakReference<Ticket> reference = new java.lang.ref.WeakReference<>(ticket);'
assert original.count(anchor) == 3
legacy = '                weak[cell] = new java.lang.ref.WeakReference<>(ticket);'
assert original.count(legacy) == 1
positive = original.replace(anchor, hook + anchor).replace(legacy, hook + legacy)
# Publish a real native list entry before the fallible VM root creation.
# The stock counter already exists here; only its VM binding has not been adopted.
early = """if(Boolean.getBoolean("ticket.cell.adoptionFailure")) {
 try {
  int kind=Types.id(Ticket.nativeBindingType(ticket));
  long ignored=(long)RESOURCE_PUBLISH.invokeExact(storage.id,0,0,kind,Ticket.nativeBindingLevel(ticket),Types.flags(kind),Ticket.nativeBindingType(ticket).timeout(),cell,MemorySegment.ofArray(storage.io));
 } catch(Throwable failure) { throw failure(failure); }
}
"""
mutant = positive.replace(hook, early + hook)
rows = []
for label, body in [('prepared', positive), ('early-publication', mutant)]:
    d = out / label
    src = d / 'source/net/minecraft/world/level'
    src.mkdir(parents=True)
    classes = d / 'classes'
    classes.mkdir()
    (src / 'NativeTicketStorage.java').write_text(body)
    (src / 'TicketCellAdoptionFailureContractTest.java').write_text(probe)
    with (d / 'javac.log').open('w') as log:
        subprocess.run([str(args.java_home / 'bin/javac'), '-g', '-cp', cp, '-d', str(classes), str(src / 'NativeTicketStorage.java'), str(src / 'TicketCellAdoptionFailureContractTest.java'), str(r / 'DevUtils/tests/worldgen/chunk_generation/GenerationContractRunner.java')], stdout=log, stderr=subprocess.STDOUT, check=True)
    c = [str(args.java_home / 'bin/java'), '--enable-native-access=ALL-UNNAMED', '-Dnet.bytebuddy.experimental=true', '-Dmattmc.rust.natives.dir=' + str(native.parent), '-cp', str(classes) + os.pathsep + cp, 'GenerationContractRunner', 'net.minecraft.world.level.TicketCellAdoptionFailureContractTest']
    (d / 'command.json').write_text(json.dumps(c, indent=2) + '\n')
    with (d / 'test.log').open('w') as log:
        p = subprocess.run(c, stdout=log, stderr=subprocess.STDOUT, timeout=60)
    text = (d / 'test.log').read_text()
    kill = p.returncode != 0 and 'new ticket entry was published before its resources were prepared' in text
    rows.append({'case': label, 'exit': p.returncode, 'intended_failure': kill, 'adapter_sha256': sha(src / 'NativeTicketStorage.java')})
    if label == 'prepared':
        assert p.returncode == 0, text[-4000:]
    else:
        assert kill, text[-4000:]
    print(label, 'PASS' if p.returncode == 0 else 'intended mutation detected', flush=True)
assert all((sha(Path(p)) == h for p, h in guards.items()))
(out / 'result.json').write_text(json.dumps({'passed': True, 'production_inputs': guards, 'rows': rows}, indent=2) + '\n')
