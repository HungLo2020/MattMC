package net.minecraft.world.level.lighting;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;

/** Actual per-level engine bindings; no Java light-state or command mirror. */
final class NativeLevelLighting {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_level_create",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_level_release",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle CHECK = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_level_check",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle APPLY_IMPORT = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_tasks_apply_import",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle COMMAND = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_level_command",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
    private static final MethodHandle PROJECTION = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_level_projection",
        FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final java.lang.invoke.VarHandle INT=ValueLayout.JAVA_INT.varHandle();
    private static final MethodHandle FRAME_RESOURCE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_level_frame_resource",
        FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
    private static final MethodHandle TASK_RESOURCE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_tasks_context_resource",
        FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
    private static final MethodHandle FRAME_READ = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_level_frame_read",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle FRAME_STEP = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_level_frame_step",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle FRAME_RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_level_frame_release",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle TASK_CONTEXT = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_tasks_context",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle TASK_ABORT = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_tasks_context_abort",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
    private static final MethodHandle FRAME_SOURCE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_level_frame_bind_source",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle TASK_SOURCE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_tasks_context_bind_source",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private final MemorySegment owner,projection;
    private final LightEngine<?, ?> block, sky;
    private final NativeLightingMap resources;

    private NativeLevelLighting(LightEngine<?, ?> block, LightEngine<?, ?> sky) {
        this.block = block; this.sky = sky;
        this.resources = (block == null ? sky : block).storage.updatingSectionData.nativeMap;
        try {
            MemorySegment blockBinding = block == null ? MemorySegment.NULL : block.lightCoordinator.bindingForPropagation();
            MemorySegment skyBinding = sky == null ? MemorySegment.NULL : sky.lightCoordinator.bindingForPropagation();
            MemorySegment pointer = (MemorySegment)CREATE.invokeExact(blockBinding, skyBinding);
            owner = NativeLightingMap.adopt(pointer, 1, RELEASE);
            MemorySegment metadata=(MemorySegment)PROJECTION.invokeExact(owner);
            if(metadata.address()==0)throw new IllegalStateException("Missing level-light projection");
            projection=metadata.reinterpret(Integer.BYTES).asReadOnly();
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Native level-light binding failed", failure); }
        finally { Reference.reachabilityFence(block); Reference.reachabilityFence(sky); }
    }

    static NativeLevelLighting create(LightEngine<?, ?> block, LightEngine<?, ?> sky) {
        if (block == null && sky == null || block != null && (block.getClass() != BlockLightEngine.class || block.lightCoordinator == null)
                || sky != null && (sky.getClass() != SkyLightEngine.class || sky.lightCoordinator == null)) return null;
        if(block!=null&&sky!=null&&!block.storage.updatingSectionData.nativeMap.sharesIdentityResources(sky.storage.updatingSectionData.nativeMap))return null;
        return new NativeLevelLighting(block, sky);
    }

    boolean matches(LightEngine<?, ?> block, LightEngine<?, ?> sky) { return this.block == block && this.sky == sky; }
    MemorySegment ownerForTasks() { return owner; }

    boolean hasWork(){try{return ((int)INT.getAcquire(projection,0L)&3)!=0;}finally{Reference.reachabilityFence(this);}}
    int runUpdates(){
        try{
            if(((int)INT.getAcquire(projection,0L)&12)==12){
                if(block!=null)block.clearChunkCacheForNativeCoordinator();
                if(sky!=null)sky.clearChunkCacheForNativeCoordinator();
                return 0;
            }
            return (int)command(3,0,false);
        }finally{Reference.reachabilityFence(this);}
    }
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException failed(Throwable failure) throws T {
        throw (T)failure;
    }
    void updateStatus(long key,boolean empty){
        command(0,key,empty);
    }
    long command(int kind,long key,boolean value){
        long frame=0;
        try(Arena arena=Arena.ofConfined()){
            MemorySegment output=arena.allocate(7L*Long.BYTES,Long.BYTES);
            frame=(long)COMMAND.invokeExact(owner,kind,key,value?1:0);
            if(frame==0)return 0;
            resume(frame,MemorySegment.NULL,0,output);
            return output.getAtIndex(ValueLayout.JAVA_LONG,0);
        }catch(Throwable failure){throw failed(failure);}
        finally{
            try{if(frame!=0)FRAME_RELEASE.invokeExact(frame);}
            catch(Throwable failure){throw failed(failure);}
            finally{resources.drainForCoordinator();Reference.reachabilityFence(this);}
        }
    }
    void resumeTask(MemorySegment tasks,long cursor){
        try(Arena arena=Arena.ofConfined()){
            resume(0,tasks,cursor,arena.allocate(7L*Long.BYTES,Long.BYTES));
        }catch(Throwable failure){throw failed(failure);}
        finally{
            try{TASK_ABORT.invokeExact(tasks,cursor);}
            catch(Throwable failure){throw failed(failure);}
            finally{resources.drainForCoordinator();Reference.reachabilityFence(this);}
        }
    }
    void resumeRequest(long frame,NativeLightRequests request,MemorySegment output)throws Throwable{resume(frame,MemorySegment.NULL,0,output,request);}
    void drainRequestResources(){resources.drainForCoordinator();Reference.reachabilityFence(this);}
    private void applyRequestImport(long frame,int target)throws Throwable{
        LightEngine<?,?> engine=target==0?block:target==1?sky:null;
        if(engine==null)throw new IllegalStateException("Missing native light effect context");
        try {
            synchronized(engine.storage.queuedSections){
                int status=NativeLightRequests.applyImport(frame);
                if(status==-20)throw new OutOfMemoryError("Native lighting table allocation failed");
                if(status!=0)throw new IllegalStateException("Native light coordinator rejected operation: "+status);
            }
        } finally {
            resources.drainForCoordinator();
            Reference.reachabilityFence(this);
        }
    }
    private void resume(long frame,MemorySegment tasks,long cursor,MemorySegment output)throws Throwable{resume(frame,tasks,cursor,output,null);}
    private void resume(long frame,MemorySegment tasks,long cursor,MemorySegment output,NativeLightRequests request)throws Throwable{
        int action=request!=null?NativeLightRequests.read(frame,output):frame==0?(int)TASK_CONTEXT.invokeExact(tasks,cursor,0,0L,MemorySegment.NULL,output):(int)FRAME_READ.invokeExact(frame,output);
        net.minecraft.world.level.chunk.LevelChunkSection[] levelChunkSections=null;
        MemorySegment result=MemorySegment.NULL;
        NativeLightingMap resultMap=null;
        net.minecraft.world.level.block.state.BlockState nodeState=null;
        try{
            for(;;){
                NativeLightCoordinator.checkEffectStatus(action,output);
                if(action==0)return;
                if(request!=null&&action>=32){
                    long scalar=0;
                    switch(action){
                        case 32->levelChunkSections=request.chunk.getSectionsForRead();
                        case 33->scalar=request.chunk.getSectionsCount();
                        case 34->{net.minecraft.world.level.chunk.LevelChunkSection levelChunkSection=levelChunkSections[(int)output.getAtIndex(ValueLayout.JAVA_LONG,0)];scalar=levelChunkSection.hasOnlyAir()?1:0;}
                        case 35->scalar=request.host.levelHeightAccessor.getSectionYFromSectionIndex((int)output.getAtIndex(ValueLayout.JAVA_LONG,0));
                        case 37->scalar=net.minecraft.SharedConstants.DEBUG_VERBOSE_SERVER_EVENTS?1:0;
                        case 38->request.logger.debug("LIT {}",request.position);
                        case 40->scalar=request.host.getMinLightSection();
                        case 41->scalar=request.host.getMaxLightSection();
                        case 42->scalar=request.host.levelHeightAccessor.getMinSectionY();
                        case 43->scalar=request.host.levelHeightAccessor.getMaxSectionY();
                        case 44->applyRequestImport(frame,(int)output.getAtIndex(ValueLayout.JAVA_LONG,6));
                        default->throw new IllegalStateException("Unknown native request effect "+action);
                    }
                    action=NativeLightRequests.step(frame,scalar,MemorySegment.NULL,output);continue;
                }
                int target=(int)output.getAtIndex(ValueLayout.JAVA_LONG,6);
                LightEngine<?,?> engine=target==0?block:target==1?sky:null;
                if(engine==null)throw new IllegalStateException("Missing native light effect context");
                NativeLightingMap map=engine.storage.updatingSectionData.nativeMap;
                long changed=output.getAtIndex(ValueLayout.JAVA_LONG,5);
                MemorySegment changes=MemorySegment.ofAddress(output.getAtIndex(ValueLayout.JAVA_LONG,4)).reinterpret(changed*Integer.BYTES);
                for(long i=0;i<changed;i++)map.refreshForCoordinator(changes.getAtIndex(ValueLayout.JAVA_INT,i));
                int slot=(int)output.getAtIndex(ValueLayout.JAVA_LONG,0);
                long scalar=0;
                resultMap=map;
                switch(action){
                    case 1->result=map.entryForCoordinator(SkyLightSectionStorage.repeatFirstLayer(map.resolveForCoordinator(slot)));
                    case 2->result=map.entryForCoordinator(map.resolveForCoordinator(slot).copy());
                    case 3->scalar=map.resolveForCoordinator(slot).get((int)output.getAtIndex(ValueLayout.JAVA_LONG,1)&15,
                        (int)output.getAtIndex(ValueLayout.JAVA_LONG,1)>>8,(int)output.getAtIndex(ValueLayout.JAVA_LONG,1)>>4&15);
                    case 4->map.resolveForCoordinator(slot).set((int)output.getAtIndex(ValueLayout.JAVA_LONG,1)&15,
                        (int)output.getAtIndex(ValueLayout.JAVA_LONG,1)>>8,(int)output.getAtIndex(ValueLayout.JAVA_LONG,1)>>4&15,
                        (int)output.getAtIndex(ValueLayout.JAVA_LONG,2));
                    case 5->{
                        MemorySegment snapshot=request!=null?NativeLightRequests.resource(frame):frame==0?(MemorySegment)TASK_RESOURCE.invokeExact(tasks,cursor):(MemorySegment)FRAME_RESOURCE.invokeExact(frame);
                        engine.storage.publishNativeMap(map.adoptSnapshot(snapshot));
                    }
                    case 6->engine.storage.chunkSource.onLightUpdate(target==0?net.minecraft.world.level.LightLayer.BLOCK:net.minecraft.world.level.LightLayer.SKY,
                        net.minecraft.core.SectionPos.of(output.getAtIndex(ValueLayout.JAVA_LONG,0)));
                    case 7->{nodeState=((BlockLightEngine)engine).stateForNativeNode(output.getAtIndex(ValueLayout.JAVA_LONG,0));
                        scalar=net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(nodeState);}
                    case 8->{
                        int z=(int)output.getAtIndex(ValueLayout.JAVA_LONG,1);
                        ChunkSkyLightSources source=((SkyLightEngine)engine).sourceInputForNative(slot,z,false);
                        if(source==null)scalar=output.getAtIndex(ValueLayout.JAVA_LONG,2);
                        else if(source.getClass()!=ChunkSkyLightSources.class)scalar=source.getLowestSourceY(slot&15,z&15);
                        else bindSourceInput(frame,tasks,cursor,request,source);
                    }
                    case 9->scalar=engine.propagateForNativeCoordinator();
                    case 10->engine.clearChunkCacheForNativeCoordinator();
                    case 11->scalar=nodeState.getLightEmission();
                    case 12->scalar=LightEngine.isEmptyShape(nodeState)?1:0;
                    case 14->engine.checkNode(output.getAtIndex(ValueLayout.JAVA_LONG,0));
                    case 16->map.drainForCoordinator();
                    case 17->{
                        ChunkSkyLightSources source=((SkyLightEngine)engine).sourceInputForNative(slot,(int)output.getAtIndex(ValueLayout.JAVA_LONG,1),true);
                        if(source.getClass()!=ChunkSkyLightSources.class)scalar=source.getHighestLowestSourceY();
                        else bindSourceInput(frame,tasks,cursor,request,source);
                    }
                    case 18->scalar=map.resolveForCoordinator(slot).isEmpty()?1:0;
                    case 19->map.resolveForCoordinator(slot).fill((int)output.getAtIndex(ValueLayout.JAVA_LONG,1));
                    case 20->((BlockLightEngine)engine).propagateSourcesForNativeLevel(slot,(int)output.getAtIndex(ValueLayout.JAVA_LONG,1));
                    case 21->((SkyLightEngine)engine).propagateSourcesForNativeLevel(slot,(int)output.getAtIndex(ValueLayout.JAVA_LONG,1));
                    case 23->{}
                    default->throw new IllegalStateException("Unknown native level-light effect "+action);
                }
                try{action=request!=null?NativeLightRequests.step(frame,scalar,result,output):frame==0?(int)TASK_CONTEXT.invokeExact(tasks,cursor,1,scalar,result,output):(int)FRAME_STEP.invokeExact(frame,scalar,result,output);}
                finally{map.releaseCoordinatorEntry(result);result=MemorySegment.NULL;}
            }
        }finally{if(result.address()!=0)resultMap.releaseCoordinatorEntry(result);}
    }

    private void bindSourceInput(long frame,MemorySegment tasks,long cursor,NativeLightRequests request,
                                 ChunkSkyLightSources source)throws Throwable {
        try {
            MemorySegment input=MemorySegment.ofAddress(source.nativeOwnerForLighting());
            int status=request!=null?NativeLightRequests.bindSource(frame,input):frame==0?
                (int)TASK_SOURCE.invokeExact(tasks,cursor,input):(int)FRAME_SOURCE.invokeExact(frame,input);
            if(status!=0)throw new IllegalStateException("Native source input rejected: "+status);
        } finally {Reference.reachabilityFence(source);}
    }

    /** The native command selects the physical VM resource; no native borrow
     * survives acquiring this original SynchronizedMap monitor. */
    void applyImport(MemorySegment tasks, long cursor, int target, net.minecraft.world.level.chunk.DataLayer source) {
        LightEngine<?, ?> engine = target == 0 ? block : target == 1 ? sky : null;
        if (engine == null) throw new IllegalStateException("Missing native light effect context");
        try {
            synchronized (engine.storage.queuedSections) {
                // Fallible identity binding stays at the original PRE point,
                // after monitor acquisition. Capture no bytes or old mode.
                MemorySegment entry = resources.entryForCoordinator(source);
                try {
                    int status = (int)APPLY_IMPORT.invokeExact(tasks, cursor, entry);
                    if (status == -20) throw new OutOfMemoryError("Native lighting table allocation failed");
                    if (status != 0) throw new IllegalStateException("Native light coordinator rejected operation: " + status);
                } finally { resources.releaseCoordinatorEntry(entry); }
            }
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Native queued-light effect failed", failure); }
        finally { resources.drainForCoordinator(); Reference.reachabilityFence(source); Reference.reachabilityFence(this); }
    }

    void check(long pos) {
        try {
            int status = (int)CHECK.invokeExact(owner, pos);
            if (status == -20) throw new OutOfMemoryError("Native lighting table allocation failed");
            if (status != 0) throw new IllegalStateException("Native light coordinator rejected operation: " + status);
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Native level-light check failed", failure); }
        finally { Reference.reachabilityFence(this); }
    }
}
