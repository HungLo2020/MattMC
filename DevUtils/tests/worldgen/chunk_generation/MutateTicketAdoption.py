#!/usr/bin/env python3
"""Keep production untouched; inject the registration/rollback failure in a copy.
The negative traps its second raw release to avoid intentionally freeing one native allocation twice.
"""
from pathlib import Path
import subprocess,json,hashlib,os,argparse
parser=argparse.ArgumentParser(description="Exercise registered-adoption failure and actual GC cleanup in isolated NativeTicketStorage copies.")
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--classpath-file',type=Path,required=True)
args=parser.parse_args()
root=Path(__file__).resolve().parents[4]
out=args.output.resolve()
if not out.is_relative_to(root/'build'):raise ValueError('output must be under build/')
out.mkdir(parents=True,exist_ok=False)
source=root/'src/main/java/net/minecraft/world/level/NativeTicketStorage.java';original=source.read_text();sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
guards={str(p):sha(p) for p in [source,root/'build/rust/native/mattmc_rust-linux-x64.so',root/'build/classes/java/main/net/minecraft/world/level/NativeTicketStorage.class']}
cp=args.classpath_file.read_text().strip();jdk=root/'libraries/jdk/linux-x64/bin'
probe='''package net.minecraft.world.level;
public final class TicketOwnerAdoptionProbe {
 public static void main(String[] args) throws Exception {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  System.setProperty("ticket.adoption.registeredFailure","true");
  try {new NativeTicketStorage();throw new AssertionError("injected registered-adoption failure did not run");}
  catch(OutOfMemoryError expected){if(!"registered-adoption-control".equals(expected.getMessage()))throw expected;}
  for(int i=0;i<160 && NativeTicketStorage.TEST_CLEANUPS.get()<2 && NativeTicketStorage.TEST_RELEASES.get()<2;i++){System.gc();Thread.sleep(20);}
  int attempts=NativeTicketStorage.TEST_CLEANUPS.get(),releases=NativeTicketStorage.TEST_RELEASES.get();
  if(releases!=1)throw new AssertionError("registered constructor cleanup released native owner "+releases+" times");
  if(attempts<2)throw new AssertionError("registered GC cleanup not observed: "+attempts);
  System.out.println("REGISTERED_ADOPTION_PASS attempts="+attempts+" nativeReleases="+releases);
 }
}
'''
base=original.replace('final class NativeTicketStorage {','''final class NativeTicketStorage {
 static final java.util.concurrent.atomic.AtomicInteger TEST_CLEANUPS=new java.util.concurrent.atomic.AtomicInteger();
 static final java.util.concurrent.atomic.AtomicInteger TEST_RELEASES=new java.util.concurrent.atomic.AtomicInteger();''',1)
a='this.owner = MemorySegment.ofAddress(created).reinterpret(1, arena, cleanup);';assert base.count(a)==1
base=base.replace(a,'''
 MemorySegment registered=MemorySegment.ofAddress(created).reinterpret(1,arena,cleanup);
 if(Boolean.getBoolean("ticket.adoption.registeredFailure"))throw new OutOfMemoryError("registered-adoption-control");
 this.owner=registered;
 ''')
a='public void run() { long owned = handle.getAndSet(0);';assert base.count(a)==1;base=base.replace(a,'public void run() { TEST_CLEANUPS.incrementAndGet(); long owned = handle.getAndSet(0);')
a='try { RELEASE.invokeExact(handle); }';assert base.count(a)==1;base=base.replace(a,'try { if(TEST_RELEASES.incrementAndGet()==1) RELEASE.invokeExact(handle); }')
# The trap records the mutant's second raw release, avoiding an intentional process double free.
for label in ['positive','raw-rollback-mutant']:
 text=base
 if label!='positive':
  a='catch (Throwable t) { cleanup.run(); throw failure(t); }';assert text.count(a)==1
  text=text.replace(a,'catch (Throwable t) { release(created); throw failure(t); }')
 d=out/label;src=d/'source/net/minecraft/world/level';src.mkdir(parents=True);classes=d/'classes';classes.mkdir()
 (src/'NativeTicketStorage.java').write_text(text);(src/'TicketOwnerAdoptionProbe.java').write_text(probe)
 with (d/'javac.log').open('w') as log:subprocess.run([str(jdk/'javac'),'-g','-cp',cp,'-d',str(classes),str(src/'NativeTicketStorage.java'),str(src/'TicketOwnerAdoptionProbe.java')],stdout=log,stderr=subprocess.STDOUT,check=True)
 cmd=[str(jdk/'java'),'-Xms128m','-Xmx256m','--enable-native-access=ALL-UNNAMED','-Dmattmc.rust.natives.dir='+str(root/'build/rust/native'),'-cp',str(classes)+os.pathsep+cp,'net.minecraft.world.level.TicketOwnerAdoptionProbe']
 (d/'command.json').write_text(json.dumps(cmd,indent=2)+'\n')
 with (d/'run.log').open('w') as log:result=subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT,timeout=60)
 log=(d/'run.log').read_text()
 if label=='positive':assert result.returncode==0 and 'REGISTERED_ADOPTION_PASS attempts=2 nativeReleases=1' in log,log[-3500:]
 else:assert result.returncode!=0 and 'registered constructor cleanup released native owner 2 times' in log,log[-3500:]
 print(label,'PASS',flush=True)
assert all(sha(Path(p))==h for p,h in guards.items())
(out/'result.json').write_text(json.dumps({'passed':True,'original_inputs':guards,'scope':'isolated copy; injected failure after actual auto-arena cleanup registration; GC callback observed; only second raw release trapped in negative to avoid intentional native double free','production_unchanged':True},indent=2)+'\n')
