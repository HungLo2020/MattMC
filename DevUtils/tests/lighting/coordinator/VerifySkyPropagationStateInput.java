package net.minecraft.world.level.lighting;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.ref.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.ticks.ProtoChunkTicks;

/** Actual propagation input: VM retirement, live primary read and last release. */
public final class VerifySkyPropagationStateInput {
    static MethodHandle h(String name,FunctionDescriptor type){return NativeLibraryLoader.downcallHandle("mattmc_rust",name,type);}
    static final MethodHandle RUN=h("mattmc_light_sky_run_connected_effects",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    static final MethodHandle BIND=h("mattmc_light_block_capture_state",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    static final MethodHandle PROBE=h("mattmc_light_coordinator_block_probe_new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    static final MethodHandle OWNERS=h("mattmc_light_coordinator_block_probe_owners",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    static final MethodHandle DROP=h("mattmc_light_coordinator_block_probe_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    static final MemorySegment LEAF;
    static{try{LEAF=Linker.nativeLinker().upcallStub(MethodHandles.lookup().findStatic(VerifySkyPropagationStateInput.class,"leaf",MethodType.methodType(long.class,int.class,long.class,long.class,long.class)),FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG),Arena.global());}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    static final Exception MARKER=new Exception("original propagation input marker");
    static SkyLightEngine engine;static Object held;static NativeReleaseScope omittedScope;static Capture capture;static boolean fail,control;static int states;static Throwable failure;
    record Capture(long probe,List<WeakReference<Object>> weak){}
    static Capture prepare(long slot)throws Throwable{
        NativeReleaseScope scope=NativeReleaseScope.open();
        try{
            if(scope==null)throw new AssertionError("Real release scope unavailable");
            var sections=new LevelChunkSection[4];for(int i=0;i<4;i++)sections[i]=new LevelChunkSection(new PalettedContainer<>(Blocks.AIR.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),null);
            sections[1].setBlockState(7,8,8,Blocks.GLOWSTONE.defaultBlockState(),false);sections[1].setBlockState(8,8,8,Blocks.GLOWSTONE.defaultBlockState(),false);
            var chunk=new ProtoChunk(new ChunkPos(0,0),UpgradeData.EMPTY,sections,new ProtoChunkTicks<>(),new ProtoChunkTicks<>(),LevelHeightAccessor.create(0,64),null,null);
            var input=new NativeLightBlocks.NodeLease();if(!input.acquire(chunk,new BlockPos(7,24,8)))throw new AssertionError("Real primary admission declined");
            long probe=(long)PROBE.invokeExact(input.pointer());
            try{
                if((int)BIND.invokeExact(MemorySegment.ofAddress(slot),input.pointer(),input.index())!=1)throw new AssertionError("Actual propagation input declined primary capture");
                if((long)OWNERS.invokeExact(probe)!=2)throw new AssertionError("Actual base and propagation input must both own section");
                sections[1].setBlockState(7,8,8,Blocks.AIR.defaultBlockState(),false);
                var f=NativeLightBlocks.NodeLease.class.getDeclaredField("owner");f.setAccessible(true);Object owner=f.get(input);
                var a=owner.getClass().getDeclaredField("owner");a.setAccessible(true);Object anchor=a.get(owner);
                if(control)held=chunk;
                return new Capture(probe,List.of(new WeakReference<>(chunk),new WeakReference<>(sections[1]),new WeakReference<>(owner),new WeakReference<>(anchor)));
            }catch(Throwable e){DROP.invokeExact(probe);throw e;}finally{input.clear();}
        }finally{if(Boolean.getBoolean("propagationInput.omitBaseClose"))omittedScope=scope;else if(scope!=null)scope.close();}
    }
    static long leaf(int action,long a,long b,long c){
        try{
            if(action==11){engine.lightCoordinator.refreshPropagationEntries(a,b);return 0;}
            if(action!=3)throw new AssertionError("Pure native consumer unexpectedly requested VM leaf "+action);
            if(++states==2){if((long)OWNERS.invokeExact(capture.probe())!=0)throw new AssertionError("Primary input crossed a later real state callback");return Block.getId(Blocks.STONE.defaultBlockState());}
            if(c==0)throw new AssertionError("Original state callback did not receive its actual capture slot");
            long closed=NativeReleaseScope.closed();capture=prepare(c);
            if(control){System.gc();System.gc();if(capture.weak().getFirst().get()==null)throw new AssertionError("Real caller-held facade collected");held=null;}
            long deadline=System.nanoTime()+5_000_000_000L;
            while((capture.weak().stream().anyMatch(w->w.get()!=null)||(long)OWNERS.invokeExact(capture.probe())!=1)&&System.nanoTime()<deadline){System.gc();Thread.sleep(10);}
            if(capture.weak().stream().anyMatch(w->w.get()!=null)||(long)OWNERS.invokeExact(capture.probe())!=1||NativeReleaseScope.closed()!=closed+1)throw new AssertionError("Actual closed arena did not leave only propagation primary input");
            if(fail)throw MARKER;
            return Block.getId(Blocks.GLOWSTONE.defaultBlockState()); // Actual live AIR must win.
        }catch(Throwable e){failure=e;return Long.MIN_VALUE;}
    }
    static void run(boolean checked,boolean callerHeld)throws Throwable{
        engine=SkyLightEngine.withBindings(new VerifyBlockInputLifetime.Getter(),new NativeLightBindings());
        var map=engine.storage.updatingSectionData;long section=SectionPos.asLong(0,1,0);map.setLayer(section,new DataLayer());map.clearCache();
        engine.lightWork.enqueue(1,BlockPos.asLong(8,24,8),2048L|1024L|768L|2L);fail=checked;control=callerHeld;states=0;capture=null;failure=null;
        try(Arena arena=Arena.ofConfined()){
            int status=(int)RUN.invokeExact(engine.lightWork.ownerForNativeCall(),engine.lightCoordinator.bindingForPropagation(),LEAF,arena.allocate(ValueLayout.JAVA_LONG));
            if(failure!=null&&failure!=MARKER)throw failure;
            if(status!=(checked?-2:0)||checked&&failure!=MARKER)throw new AssertionError("Original checked failure boundary changed");
            var layer=map.getLayer(section);if(layer.get(8,8,8)!=2||layer.get(7,8,8)!=(checked?0:1)||layer.get(9,8,8)!=0)throw new AssertionError("Actual primary consumer ignored live AIR or replayed failed prefix");
            if(capture==null||(long)OWNERS.invokeExact(capture.probe())!=0||states!=(checked?1:2))throw new AssertionError("Actual primary last-use/failure retirement changed");
            System.out.println("PROPAGATION_STATE_INPUT checked="+checked+" callerHeld="+callerHeld+" VMcollected=true actualScopeClosed=true livePrimary=true beforeLaterState=true finalNativeOwners=0");
        }finally{held=null;if(omittedScope!=null){omittedScope.close();omittedScope=null;}if(capture!=null)DROP.invokeExact(capture.probe());capture=null;Reference.reachabilityFence(engine);engine=null;}
    }
    public static void main(String[] args)throws Throwable{SharedConstants.tryDetectVersion();Bootstrap.bootStrap();if(!NativeBlockRegistry.ready())throw new AssertionError("Original production block-table admission unavailable",NativeBlockRegistry.failure());for(boolean checked:new boolean[]{false,true})for(boolean held:new boolean[]{false,true})run(checked,held);}
}
