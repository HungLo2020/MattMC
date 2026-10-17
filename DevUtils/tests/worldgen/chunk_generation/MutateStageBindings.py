#!/usr/bin/env python3
"""Isolated native stage-binding adoption, identity publication and root controls.

No production source/class/native outputs are modified. Native-release negatives
leak only in disposable JVMs; no freed pointer is invoked to prove a failure.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time
import VerifyChunkGeneration as probe
HERE=Path(__file__).resolve().parent
ROOT=probe.ROOT

ALIAS_PROBE = 'package net.minecraft.server.level;\nimport static org.junit.jupiter.api.Assertions.*;\nimport java.util.concurrent.*;\nimport org.junit.jupiter.api.*;\nclass StageAliasPublicationTest {\n static final CompletableFuture<Object> FIRST=new CompletableFuture<>(), SECOND=new CompletableFuture<>();\n static final CountDownLatch firstWritten=new CountDownLatch(1), releaseFirst=new CountDownLatch(1), secondCommitted=new CountDownLatch(1);\n static final OutOfMemoryError FAILURE=new OutOfMemoryError("before native alias publication");\n static volatile boolean concurrent, fail;\n static void afterWeak(CompletableFuture<?> future) {\n  if(fail && future==SECOND)throw FAILURE;\n  if(concurrent && future==FIRST){firstWritten.countDown();try{assertTrue(releaseFirst.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}}\n }\n static void afterCommit(CompletableFuture<?> future){if(concurrent&&future==SECOND)secondCommitted.countDown();}\n @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}\n @Test void concurrentAliasPublicationMustKeepNativeIdentityAndVmRootTogether() throws Exception {\n  org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("alias.concurrent"));\n  var holder=new GenerationChunkHolderContractTest.Holder(0,0);var owner=holder.generationOwner();\n  var binding=new NativeChunkGeneration.StageBinding(owner,net.minecraft.world.level.chunk.status.ChunkStatus.EMPTY);\n  concurrent=true;\n  try(var threads=Executors.newFixedThreadPool(2)){\n   var first=threads.submit(()->binding.bindFuture(FIRST,1));assertTrue(firstWritten.await(5,TimeUnit.SECONDS));\n   var second=threads.submit(()->binding.bindFuture(SECOND,1));\n   try{secondCommitted.await(700,TimeUnit.MILLISECONDS);}finally{releaseFirst.countDown();}\n   first.get(5,TimeUnit.SECONDS);second.get(5,TimeUnit.SECONDS);\n  }finally{concurrent=false;releaseFirst.countDown();}\n  CompletableFuture<?> selected=binding.projection.slot.get();\n  assertSame(binding,NativeChunkGeneration.StageBinding.capture(owner,selected),"native binding identity diverged from its VM root");\n }\n @Test void failedAliasPublicationMustPreserveThePreviouslyBoundIdentity(){\n  org.junit.jupiter.api.Assumptions.assumeFalse(Boolean.getBoolean("alias.concurrent"));\n  var holder=new GenerationChunkHolderContractTest.Holder(0,0);var owner=holder.generationOwner();\n  var binding=new NativeChunkGeneration.StageBinding(owner,net.minecraft.world.level.chunk.status.ChunkStatus.EMPTY);\n  binding.bindFuture(FIRST,1);fail=true;\n  try{assertSame(FAILURE,assertThrows(OutOfMemoryError.class,()->binding.bindFuture(SECOND,1)));}finally{fail=false;}\n  assertSame(binding,NativeChunkGeneration.StageBinding.capture(owner,FIRST),"failed alias publication lost the previous binding");\n }\n}\n'
ALLOCATION_PROBE = 'package net.minecraft.server.level;\nimport static org.junit.jupiter.api.Assertions.*;\nimport static org.mockito.Mockito.*;\nimport org.junit.jupiter.api.*;\nimport org.junit.jupiter.api.parallel.Isolated;\nimport net.minecraft.world.level.chunk.ProtoChunk;\nimport net.minecraft.world.level.chunk.status.ChunkStatus;\n@Isolated\nclass StageBindingAllocationTest {\n static final OutOfMemoryError FAILURE=new OutOfMemoryError("isolated stage binding adoption failure");\n static void fail(String point){if(point.equals(System.getProperty("stage.binding.fail"))){System.clearProperty("stage.binding.fail");throw FAILURE;}}\n @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}\n static void check(String point,int engines,int callbacks,boolean registered)throws Exception{\n  NativeStageBindingOwnerTest.settle();long[] before=NativeChunkGeneration.stageBindingStatistics();\n  var f=new ChunkStageBindingContractTest.Fixture();System.setProperty("stage.binding.fail",point);\n  try{assertSame(FAILURE,assertThrows(OutOfMemoryError.class,f::apply));}finally{System.clearProperty("stage.binding.fail");}\n  assertSame(ChunkStatus.EMPTY,f.holder.getLatestStatus(),"failed adoption lost the committed started prefix");\n  assertEquals(engines,f.map.trace.size());\n  if(registered){\n   assertNotNull(f.engine.callback,"real registered callback was detached after caller failure");\n   var chunk=mock(ProtoChunk.class);assertSame(chunk,f.engine.fire(chunk).orElse(null));\n   assertSame(chunk,f.holder.getAllFutures().getFirst().getSecond().join().orElse(null));\n   f.engine.callback=null;\n  }else assertNull(f.engine.callback);\n  NativeStageBindingOwnerTest.settle();\n  for(int i=0;i<160;i++){\n   System.gc();Thread.sleep(20);long[] after=NativeChunkGeneration.stageBindingStatistics();\n   if(after[0]<=before[0]&&after[3]<=before[3]&&after[2]>=before[2]+1&&after[5]>=before[5]+callbacks)break;\n  }\n  long[] after=NativeChunkGeneration.stageBindingStatistics();\n  assertEquals(before[1]+1,after[1]);assertEquals(before[4]+callbacks,after[4]);\n  assertTrue(after[0]<=before[0],"failed binding adoption did not retire its native owner");\n  assertTrue(after[3]<=before[3],"failed callback adoption did not retire its native lease");\n  assertTrue(after[2]>=before[2]+1);assertTrue(after[5]>=before[5]+callbacks);\n  java.lang.ref.Reference.reachabilityFence(f);\n }\n @Test void bindingAdoptionFailureRetiresBeforeEngineDispatch()throws Exception{check("binding-adopt",0,0,false);}\n @Test void bindingRootFailureRetiresTheUnpublishedOwner()throws Exception{check("binding-root",0,0,false);}\n @Test void callbackAdoptionFailureKeepsTheEnginePrefixAndRetiresBothOwners()throws Exception{check("callback-adopt",1,1,false);}\n @Test void callerFailureAfterRegistrationKeepsTheRealCallbackUntilItsLastUse()throws Exception{check("registration-return",1,1,true);}\n}\n'

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath-file',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();out=args.output.resolve()
    if not out.is_relative_to(ROOT/'build'):raise ValueError('output must be under build/')
    out.mkdir(parents=True,exist_ok=False)
    source=ROOT/'src/main/java/net/minecraft/server/level/NativeChunkGeneration.java'
    holder_source=source.with_name('GenerationChunkHolder.java')
    original=source.read_text();holder_original=holder_source.read_text()
    start=original.index('        synchronized void bindFuture(');stop=original.index('        synchronized boolean matches(',start)
    body=original[start:stop]
    rollback='if (role == 0) projection.work = prior; else projection.slot = prior; '
    if body.count(rollback)!=1:raise ValueError('inspect changed alias publication rollback')
    hook='StageAliasPublicationTest.afterWeak(future); '
    alias=body.replace('try { StageBindings.FUTURE.invokeExact', 'try { '+hook+'StageBindings.FUTURE.invokeExact')
    alias=alias.replace('System.identityHashCode(future)); }','System.identityHashCode(future)); StageAliasPublicationTest.afterCommit(future); }')
    positive=original[:start]+alias+original[stop:]
    binding_adopt='            owner = adopt(created, 1, arena, StageBindings.RELEASE);'
    callback_adopt='            id = created; owner = adopt(created, 1, arena, StageBindings.CALLBACK_RELEASE);'
    root_publish='                holder.publishStageRoot(this);'
    if any(positive.count(m)!=1 for m in [binding_adopt,callback_adopt,root_publish]):raise ValueError('inspect changed native binding adoption boundaries')
    positive=positive.replace(binding_adopt,binding_adopt+'\n            StageBindingAllocationTest.fail("binding-adopt");')
    positive=positive.replace(callback_adopt,callback_adopt+'\n            StageBindingAllocationTest.fail("callback-adopt");')
    positive=positive.replace(root_publish,root_publish+'\n                StageBindingAllocationTest.fail("binding-root");')
    registered='                    binding.bindFuture(returned, 0);'
    if holder_original.count(registered)!=1:raise ValueError('inspect changed actual handle registration')
    holder=holder_original.replace(registered,registered+'\n                    StageBindingAllocationTest.fail("registration-return");')
    weak_work=positive.replace('        final StageProjection projection;','        final StageProjection projection;\n        CompletableFuture<?> leakedWork;')
    weak_work=weak_work.replace('            if (future == null) return;','            if (role == 0) leakedWork = future;\n            if (future == null) return;',1)
    weak_holder=holder
    a=weak_holder.index('    private static final class BoundStageCallback');b=weak_holder.index('    /** VM resources are invocation locals.',a)
    section=weak_holder[a:b].replace('final GenerationChunkHolder holder;','final java.lang.ref.WeakReference<GenerationChunkHolder> holder;').replace('this.holder = holder;','this.holder = new java.lang.ref.WeakReference<>(holder);').replace('return holder.completeBoundStage','return holder.get().completeBoundStage')
    weak_holder=weak_holder[:a]+section+weak_holder[b:]
    no_pin=positive.replace('StageBindings.PIN_TASK.invokeExact(id, row, driver);','/* Deliberately omit native task binding adoption in this child JVM. */')
    if no_pin==positive:raise ValueError('inspect changed native pending binding adoption')
    cases=[
        ('alias-concurrent',positive,holder,['StageAliasPublicationTest'],True,None),
        ('alias-failed-publication',positive,holder,['StageAliasPublicationTest'],False,None),
        ('missing-alias-serialization',positive.replace('synchronized void bindFuture(', 'void bindFuture('),holder,['StageAliasPublicationTest'],True,'native binding identity diverged from its VM root'),
        ('missing-alias-rollback',positive.replace(rollback,''),holder,['StageAliasPublicationTest'],False,'failed alias publication lost the previous binding'),
        ('adoption-and-root-positives',positive,holder,['StageBindingAllocationTest','NativeStageBindingOwnerTest','ChunkStageBindingContractTest','ChunkStageInvocationContractTest'],False,None),
        ('missing-binding-release',positive.replace(binding_adopt,'            owner = MemorySegment.ofAddress(created);'),holder,['NativeStageBindingOwnerTest'],False,'last real use left the native stage binding alive'),
        ('missing-callback-release',positive.replace(callback_adopt,'            id = created; owner = MemorySegment.ofAddress(created);'),holder,['NativeStageBindingOwnerTest'],False,'last real use left the native stage binding alive'),
        ('strong-work-retention',weak_work,holder,['ChunkStageBindingContractTest'],False,'stage callback retained resources after their original last use'),
        ('weak-callback-holder',positive,weak_holder,['ChunkStageBindingContractTest'],False,'callable callback still needs its actual holder'),
        ('missing-native-pending-custody',no_pin,holder,['NativeStageBindingOwnerTest'],False,'pending task lost native binding after VM facade collection'),
        ('missing-claimed-binding-release',positive.replace('owner = claimed == 0 ? MemorySegment.NULL : adopt(claimed, 1, arena, StageBindings.RELEASE);','owner = MemorySegment.ofAddress(claimed);'),holder,['NativeStageBindingOwnerTest'],False,'last real use left the native stage binding alive'),
        ('missing-invocation-projection-roots',positive.replace('for (TaskFrame frame = captures; frame != null; frame = frame.captureNext)', 'for (TaskFrame frame = (TaskFrame)null; frame != null; frame = frame.captureNext)'),holder,['NativeStageBindingOwnerTest'],False,'native task invocation lost its binding before genuine virtual return'),
        ('missing-native-invocation-custody',positive.replace('StageBindings.INVOKE.invokeExact(driver, id);','/* Omit real native invocation custody only in this child. */'),holder,['NativeStageBindingOwnerTest'],False,'native immediate invocation lost binding during genuine getNow after facade GC'),
    ]
    cp=args.classpath_file.read_text().strip();before=probe.end.inputs(ROOT)
    fixture=ROOT/'src/test/java/net/minecraft/server/level'
    fixture_sources=[fixture/name for name in ['GenerationChunkHolderContractTest.java','ChunkStageInvocationContractTest.java','ChunkStageBindingContractTest.java','NativeStageBindingOwnerTest.java']]
    fixture_hashes={str(p.relative_to(ROOT)):probe.full.sha(p) for p in fixture_sources}
    rows=[]
    for label,adapter,holder_adapter,tests,concurrent,witness in cases:
        run=out/label;package=run/'source/net/minecraft/server/level';package.mkdir(parents=True)
        copied=package/source.name;copied.write_text(adapter)
        copied_holder=package/holder_source.name;copied_holder.write_text(holder_adapter)
        alias_probe=package/'StageAliasPublicationTest.java';alias_probe.write_text(ALIAS_PROBE)
        allocation_probe=package/'StageBindingAllocationTest.java';allocation_probe.write_text(ALLOCATION_PROBE)
        classes=run/'classes';classes.mkdir()
        sources=[copied,copied_holder,alias_probe,allocation_probe,*fixture_sources,HERE/'GenerationContractRunner.java']
        with (run/'compile.log').open('w') as log:p=subprocess.run([str(probe.full.JDK/'javac'),'-cp',cp,'-d',str(classes),*map(str,sources)],stdout=log,stderr=subprocess.STDOUT)
        if p.returncode:raise RuntimeError('isolated control compile failed: '+str(run/'compile.log'))
        command=[str(probe.full.JDK/'java'),'--enable-native-access=ALL-UNNAMED','-Dnet.bytebuddy.experimental=true','-Dmattmc.rust.natives.dir='+str(ROOT/'build/rust/native'),'-Dalias.concurrent='+str(concurrent).lower(),'-cp',str(classes)+os.pathsep+cp,'GenerationContractRunner',*['net.minecraft.server.level.'+x for x in tests]]
        (run/'command.json').write_text(json.dumps(command,indent=2)+'\n');mappings={}
        with (run/'test.log').open('w') as log:
            process=subprocess.Popen(command,stdout=log,stderr=subprocess.STDOUT,cwd=ROOT)
            try:
                deadline=time.monotonic()+120
                while process.poll() is None:
                    try:
                        for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines():
                            if 'mattmc' in line and '.so' in line:
                                p=Path(line.split()[-1])
                                if p.is_file() and str(p) not in mappings:mappings[str(p)]=probe.full.sha(p)
                    except (FileNotFoundError,ProcessLookupError):pass
                    if time.monotonic()>deadline:raise TimeoutError('binding control timed out: '+label)
                    time.sleep(.1)
            finally:
                if process.poll() is None:process.kill();process.wait()
        text=(run/'test.log').read_text();detected=witness is not None and process.returncode!=0 and witness in text
        mapped=before['native_sha256'] in mappings.values()
        rows.append({'case':label,'exit':process.returncode,'intended_failure':detected,'mapped_expected_native':mapped,'native_mappings':mappings,'adapter_sha256':probe.full.sha(copied),'holder_adapter_sha256':probe.full.sha(copied_holder)})
        (out/'rows.json').write_text(json.dumps(rows,indent=2)+'\n')
        if not mapped or (witness is None and process.returncode!=0) or (witness is not None and not detected):raise RuntimeError('binding control missed its intended witness: '+label)
        print(label,'PASS' if witness is None else 'intended mutation detected',flush=True)
    after=probe.end.inputs(ROOT)
    if before!=after or fixture_hashes!={str(p.relative_to(ROOT)):probe.full.sha(p) for p in fixture_sources}:raise RuntimeError('production inputs or fixtures changed during isolated controls')
    (out/'result.json').write_text(json.dumps({'passed':True,'before':before,'after':after,'fixture_hashes':fixture_hashes,'rows':rows,'scope':'isolated Java copies only; deliberately leaked releases in disposable children, no premature-free native pointer use'},indent=2)+'\n')

if __name__=='__main__':main()
