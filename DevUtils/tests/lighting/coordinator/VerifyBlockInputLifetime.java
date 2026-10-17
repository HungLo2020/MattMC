package net.minecraft.world.level.lighting;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.ProtoChunkTicks;

/** Actual block-node section ownership after its real release scope closes. */
public final class VerifyBlockInputLifetime {
    static NativeReleaseScope omittedScope;
    static MethodHandle handle(String name,FunctionDescriptor descriptor){return NativeLibraryLoader.downcallHandle("mattmc_rust",name,descriptor);}
    static final MethodHandle COMMAND=handle("mattmc_light_level_command",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
    static final MethodHandle READ=handle("mattmc_light_level_frame_read",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    static final MethodHandle BIND=handle("mattmc_light_level_frame_bind_block",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    static final MethodHandle STEP=handle("mattmc_light_level_frame_step",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    static final MethodHandle END=handle("mattmc_light_level_frame_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    static final MethodHandle PROBE=handle("mattmc_light_coordinator_block_probe_new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    static final MethodHandle OWNERS=handle("mattmc_light_coordinator_block_probe_owners",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    static final MethodHandle DROP=handle("mattmc_light_coordinator_block_probe_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    static final MethodHandle DIRECT_BEGIN=handle("mattmc_light_coordinator_begin",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    static final MethodHandle DIRECT_BIND=handle("mattmc_light_coordinator_bind_block",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    static final MethodHandle DIRECT_STEP=handle("mattmc_light_coordinator_step",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    static final MethodHandle DIRECT_END=handle("mattmc_light_coordinator_end",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
    static final MethodHandle TASK_CREATE=handle("mattmc_light_tasks_create",FunctionDescriptor.of(ValueLayout.ADDRESS));
    static final MethodHandle TASK_DESTROY=handle("mattmc_light_tasks_destroy",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    static final MethodHandle TASK_BEGIN=handle("mattmc_light_tasks_begin_level",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    static final MethodHandle TASK_CHANGE=handle("mattmc_light_tasks_change",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    static final MethodHandle TASK_CONTEXT=handle("mattmc_light_tasks_context",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    static final MethodHandle TASK_BIND=handle("mattmc_light_tasks_context_bind_block",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    static final MethodHandle TASK_ABORT=handle("mattmc_light_tasks_context_abort",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
    static final class Pending implements AutoCloseable {
        final String path;long frame;MemorySegment owner=MemorySegment.NULL,tasks=MemorySegment.NULL;
        Pending(String path,BlockLightEngine engine,NativeLevelLighting level,MemorySegment output)throws Throwable {
            this.path=path;int action;
            if(path.equals("direct")){
                var field=NativeLightCoordinator.class.getDeclaredField("owner");field.setAccessible(true);owner=(MemorySegment)field.get(engine.lightCoordinator);
                frame=(long)DIRECT_BEGIN.invokeExact(owner,engine.lightCoordinator.bindingForPropagation(),6,BlockPos.asLong(8,24,8),0L);
                action=(int)DIRECT_STEP.invokeExact(owner,frame,0L,MemorySegment.NULL,output);
            }else if(path.equals("task")){
                tasks=(MemorySegment)TASK_CREATE.invokeExact();
                frame=(long)TASK_BEGIN.invokeExact(tasks,level.ownerForTasks());
                if(frame<=0||(long)TASK_CHANGE.invokeExact(tasks,2,frame,0L,output)<=1)throw new AssertionError("Actual typed task did not begin");
                action=(int)TASK_CONTEXT.invokeExact(tasks,frame,0,0L,MemorySegment.NULL,output);
            }else{
                frame=(long)COMMAND.invokeExact(level.ownerForTasks(),3,0L,0);
                action=(int)READ.invokeExact(frame,output);
            }
            if(frame==0||action!=7)throw new AssertionError("Actual block-state input missing: "+path+" "+action);
        }
        int bind(MemorySegment section,int index)throws Throwable {
            return path.equals("direct")?(int)DIRECT_BIND.invokeExact(owner,frame,section,index):path.equals("task")?
                (int)TASK_BIND.invokeExact(tasks,frame,section,index):(int)BIND.invokeExact(frame,section,index);
        }
        int step(long scalar,MemorySegment output)throws Throwable {
            return path.equals("direct")?(int)DIRECT_STEP.invokeExact(owner,frame,scalar,MemorySegment.NULL,output):path.equals("task")?
                (int)TASK_CONTEXT.invokeExact(tasks,frame,1,scalar,MemorySegment.NULL,output):(int)STEP.invokeExact(frame,scalar,MemorySegment.NULL,output);
        }
        public void close(){
            try{
                if(frame==0)return;
                if(path.equals("direct")){int status=(int)DIRECT_END.invokeExact(owner,frame);if(status!=0)throw new AssertionError("Actual direct frame did not release");}
                else if(path.equals("task")){TASK_ABORT.invokeExact(tasks,frame);TASK_DESTROY.invokeExact(tasks);tasks=MemorySegment.NULL;}
                else END.invokeExact(frame);
                frame=0;
            }catch(RuntimeException|Error failure){throw failure;}catch(Throwable failure){throw new AssertionError(failure);}
        }
    }
    static final class Getter implements LightChunkGetter,BlockGetter {
        public LightChunk getChunkForLighting(int x,int z){return null;}
        public BlockGetter getLevel(){return this;}
        public void onLightUpdate(LightLayer layer,SectionPos section){}
        public BlockEntity getBlockEntity(BlockPos pos){return null;}
        public BlockState getBlockState(BlockPos pos){return Blocks.AIR.defaultBlockState();}
        public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
        public int getMinY(){return 0;}
        public int getHeight(){return 64;}
    }
    record Capture(long probe,List<WeakReference<Object>> weak){}
    static Capture capture(Pending pending,boolean mutate)throws Throwable {
        var scope=NativeReleaseScope.open();
        try{
            if(scope==null)throw new AssertionError("Actual section release scope required");
            var sections=new LevelChunkSection[4];
            for(int i=0;i<4;i++)sections[i]=new LevelChunkSection(new PalettedContainer<>(Blocks.AIR.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),null);
            sections[1].setBlockState(8,8,8,Blocks.GLOWSTONE.defaultBlockState(),false);
            sections[1].setBlockState(9,8,8,Blocks.GLOWSTONE.defaultBlockState(),false);
            var chunk=new ProtoChunk(new ChunkPos(0,0),UpgradeData.EMPTY,sections,new ProtoChunkTicks<>(),new ProtoChunkTicks<>(),LevelHeightAccessor.create(0,64),null,null);
            var input=new NativeLightBlocks.NodeLease();
            if(!input.acquire(chunk,new BlockPos(8,24,8)))throw new AssertionError("Canonical actual primary section declined");
            long probe=(long)PROBE.invokeExact(input.pointer());
            try{
                if(!Boolean.getBoolean("blockInput.omitFrameCapture")){
                    if(pending.bind(input.pointer(),input.index())!=0)throw new AssertionError("Actual pending node rejected section");
                    if(pending.bind(input.pointer(),input.index())!=-27)throw new AssertionError("Duplicate capture accepted");
                    if((long)OWNERS.invokeExact(probe)!=2)throw new AssertionError("Actual base and frame must both retain section");
                }
                // The waiting frame must read the primary owner at consume time,
                // rather than the value sampled during admission or column zero.
                if(mutate)sections[1].setBlockState(8,8,8,Blocks.STONE.defaultBlockState(),false);
                var field=NativeLightBlocks.NodeLease.class.getDeclaredField("owner");field.setAccessible(true);Object owner=field.get(input);
                var anchorField=owner.getClass().getDeclaredField("owner");anchorField.setAccessible(true);Object anchor=anchorField.get(owner);
                return new Capture(probe,List.of(new WeakReference<>(chunk),new WeakReference<>(sections[1]),new WeakReference<>(owner),new WeakReference<>(anchor)));
            }catch(Throwable failure){DROP.invokeExact(probe);throw failure;}
            finally{input.clear();}
        }finally{if(Boolean.getBoolean("blockInput.omitBaseClose"))omittedScope=scope;else if(scope!=null)scope.close();}
    }
    static void run(String path,boolean abandon,boolean mutate)throws Throwable {
        var engine=BlockLightEngine.withBindings(new Getter(),new NativeLightBindings());
        NativeLevelLighting level=NativeLevelLighting.create(engine,null);
        if(level==null)throw new AssertionError("Actual level binding required");
        var map=engine.storage.updatingSectionData;long key=SectionPos.asLong(0,1,0);
        var layer=new DataLayer(15);map.setLayer(key,layer);map.clearCache();engine.storage.changedSections.add(key);
        engine.setLightEnabled(new ChunkPos(0,0),true);engine.checkBlock(new BlockPos(8,24,8));
        Pending pending=null;Capture capture=null;
        try(Arena arena=Arena.ofConfined()){
            var output=arena.allocate(7L*8,8);pending=new Pending(path,engine,level,output);
            long closed=NativeReleaseScope.closed();capture=capture(pending,mutate);
            if(NativeReleaseScope.closed()!=closed+1||(long)OWNERS.invokeExact(capture.probe())!=1)throw new AssertionError("Actual closed arena did not leave exactly the pending frame owner");
            long deadline=System.nanoTime()+5_000_000_000L;
            while(capture.weak().stream().anyMatch(weak->weak.get()!=null)&&System.nanoTime()<deadline){System.gc();Thread.sleep(10);}
            if(capture.weak().stream().anyMatch(weak->weak.get()!=null))throw new AssertionError("Pending input retained VM section/chunk/owner/segment");
            if(abandon)pending.close();
            else{
                int action=pending.step(Block.getId(Blocks.GLOWSTONE.defaultBlockState()),output);
                if(action!=(path.equals("direct")?0:9))throw new AssertionError("Expected original last effect after actual block policy: "+action);
                int expected=mutate?0:15;
                if(layer.get(8,8,8)!=expected||map.getLayer(key).get(8,8,8)!=expected)throw new AssertionError("Actual node consumer ignored live primary state");
            }
            if((long)OWNERS.invokeExact(capture.probe())!=0)throw new AssertionError("Section crossed actual last use/abandonment");
            System.out.println("BLOCK_INPUT_LIFETIME path="+path+" abandon="+abandon+" mutate-primary="+mutate+" VMcollected=true actualScopeClosed=true actual-payload=true finalNativeOwners=0");
        }finally{
            try{if(pending!=null)pending.close();}finally{
                if(capture!=null)DROP.invokeExact(capture.probe());
                if(omittedScope!=null){omittedScope.close();omittedScope=null;}
                Reference.reachabilityFence(level);Reference.reachabilityFence(engine);
            }
        }
    }
    public static void main(String[] args)throws Throwable {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        for(String path:List.of("direct","level","task"))for(boolean abandon:new boolean[]{false,true})for(boolean mutate:new boolean[]{false,true})run(path,abandon,mutate);
    }
}
