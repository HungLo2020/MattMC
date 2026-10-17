package net.minecraft.world.level.lighting;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.util.NoSuchElementException;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.*;
import it.unimi.dsi.fastutil.bytes.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.DataLayer;

/** Contextual/object effects for Rust's per-engine policy. Collection views
 * delegate to the same native owner; they contain no membership mirror. */
final class NativeLightCoordinator {
    private static final MethodHandle CREATE=handle("create",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle PREPARE_SAVE=handle("prepare_save_publication",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle SAVED_ACQUIRE=handle("saved_acquire",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle SAVED_RELEASE=handle("saved_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle RELEASE=handle("release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle BLOCK_INPUT=handle("bind_block",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle SOURCE_INPUT=handle("bind_source",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle HEADER=handle("header",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle LIVE=handle("live",FunctionDescriptor.of(ValueLayout.JAVA_LONG));
    private static final MethodHandle ENGINES=handle("engines",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    private static final MethodHandle BIND=handle("bind",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle BIND_HEADER=handle("bind_header",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle BIND_RELEASE=handle("bind_release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle QUEUE=handle("queue_batch",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle QUEUE_EXISTING=handle("queue_existing",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle COLLECTION=handle("collection",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle WALK_CREATE=handle("walk_create",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle WALK_RELEASE=handle("walk_release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle WALK=handle("walk",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    private static final MethodHandle BULK_BEGIN=handle("bulk_begin",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle BULK_STEP=handle("bulk_step",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    private static final MethodHandle BULK_END=handle("bulk_end",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle SPLIT_CREATE=handle("split_create",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle SPLIT_RELEASE=handle("split_release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle SPLIT_PREPARE=handle("split_prepare",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle SPLIT_COMMIT=handle("split_commit",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    private static final MethodHandle SPLIT_QUERY=handle("split_query",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle SPLIT_BULK_BEGIN=handle("split_bulk_begin",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle BEGIN=handle("begin",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    private static final MethodHandle SEED_BEGIN=handle("seed_begin",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle STEP=handle("step",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle END=handle("end",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
    private static final MethodHandle COLLECTION_BEGIN=handle("collection_begin",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    private static final VarHandle INT=ValueLayout.JAVA_INT.varHandle();
    private static final long ABSENT=0xffffffffL;
    private static MethodHandle handle(String suffix,FunctionDescriptor descriptor){return NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_light_coordinator_"+suffix,descriptor);}
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException failure(Throwable error)throws E{throw(E)error;}
    private static void release(MethodHandle method,MemorySegment pointer){try{method.invokeExact(pointer);}catch(Throwable error){throw failure(error);}}
    private static void check(int status,MemorySegment output){
        if(status>=0)return;
        int index=(int)output.getAtIndex(ValueLayout.JAVA_LONG,0);long length=output.getAtIndex(ValueLayout.JAVA_LONG,1);
        switch(status){
            case -20 -> throw new OutOfMemoryError("Native lighting table allocation failed");
            case -21 -> throw new NullPointerException("Cannot invoke \"it.unimi.dsi.fastutil.longs.LongArrayList.getLong(int)\" because \"this.wrapped\" is null");
            case -22 -> throw new ArrayIndexOutOfBoundsException("Index "+index+" out of bounds for length "+length);
            case -23 -> throw new IndexOutOfBoundsException("Index ("+index+") is greater than or equal to list size ("+length+")");
            case -24 -> throw new NoSuchElementException();
            case -25 -> throw new IllegalStateException();
            case -26 -> throw new IllegalArgumentException("Neighbor count was not within range [0; 26]");
            default -> throw new IllegalStateException("Native light coordinator rejected operation: "+status);
        }
    }
    static void checkEffectStatus(int status,MemorySegment output){check(status,output);}
    private final MemorySegment owner,metadata,binding,engineMetadata;
    // A collection call has no Java effects. Consume its reply under this
    // private monitor before releasing it; continuations retain separate frames.
    private final MemorySegment collectionOutput=Arena.ofAuto().allocate(3L*Long.BYTES,Long.BYTES);
    private final NativeLightingMap map;
    private final LayerLightSectionStorage<?> storage;
    private final LightLayer layer;
    private final LightEngine<?,?> engine;
    NativeLightCoordinator(LayerLightSectionStorage<?> storage,LightLayer layer,NativeLightingMap map){
        this.storage=storage;this.layer=layer;this.map=map;
        this.engine=null;this.binding=MemorySegment.NULL;this.engineMetadata=MemorySegment.NULL;
        try{
            MemorySegment created=(MemorySegment)CREATE.invokeExact(map.ownerForCoordinator(),layer==LightLayer.SKY?1:0);
            if(created.address()==0)throw new IllegalStateException("Missing native light coordinator");
            owner=NativeLightingMap.adopt(created,1,RELEASE);
            metadata=((MemorySegment)HEADER.invokeExact(owner)).asReadOnly().reinterpret(88);
        }catch(Throwable error){throw failure(error);}
        finally{Reference.reachabilityFence(map);}
    }
    private NativeLightCoordinator(NativeLightCoordinator storageOwner,LightEngine<?,?> engine){
        this.owner=storageOwner.owner;this.metadata=storageOwner.metadata;this.map=storageOwner.map;
        this.storage=storageOwner.storage;this.layer=storageOwner.layer;this.engine=engine;
        try{
            int kind=engine.getClass()==BlockLightEngine.class?0:engine.getClass()==SkyLightEngine.class?1:2;
            MemorySegment created=(MemorySegment)BIND.invokeExact(owner,engine.lightWork.ownerForNativeCall(),kind);
            if(created.address()==0)throw new IllegalStateException("Native light engine binding unavailable");
            binding=NativeLightingMap.adopt(created,1,BIND_RELEASE);
            engineMetadata=((MemorySegment)BIND_HEADER.invokeExact(binding)).asReadOnly().reinterpret(4L*Integer.BYTES);
        }
        catch(Throwable error){throw failure(error);}
        finally{Reference.reachabilityFence(engine);Reference.reachabilityFence(storageOwner);}
    }
    NativeLightCoordinator forEngine(LightEngine<?,?> engine){return new NativeLightCoordinator(this,engine);}
    MemorySegment bindingForPropagation(){return binding;}
    static long liveForVerification(){try{return(long)LIVE.invokeExact();}catch(Throwable error){throw failure(error);}}
    int enginesForVerification(){try{return(int)ENGINES.invokeExact(owner);}catch(Throwable error){throw failure(error);}finally{Reference.reachabilityFence(this);}}
    int size(int table){
        try{
            if(table==0){if(binding.address()==0)throw new IllegalStateException("No engine-local node table");return(int)INT.getAcquire(engineMetadata,0L);}
            return(int)INT.getAcquire(metadata,4L*table);
        }finally{Reference.reachabilityFence(this);}
    }
    boolean inconsistent(){try{return(int)INT.getAcquire(metadata,32L)!=0;}finally{Reference.reachabilityFence(this);}}
    private long change(int which,int op,long key,long value,MemorySegment entry){
        try{
            synchronized(collectionOutput){
                check((int)COLLECTION.invokeExact(owner,binding,which,op,key,value,entry,collectionOutput),collectionOutput);
                return collectionOutput.getAtIndex(ValueLayout.JAVA_LONG,0);
            }
        }catch(Throwable error){throw failure(error);}
        finally{Reference.reachabilityFence(this);}
    }
    private long change(int which,int op,long key,long value){return change(which,op,key,value,MemorySegment.NULL);}
    LongSet set(int which){return new SetView(which);}
    Long2ByteMap states(){return new StateView();}
    Long2ObjectMap<DataLayer> queued(){return Long2ObjectMaps.synchronize(new QueuedView());}
    void queueSectionData(long key,DataLayer value){
        try{
            synchronized(storage.queuedSections){
                synchronized(collectionOutput){
                    check(map.queueForCoordinator(owner,QUEUE,QUEUE_EXISTING,key,value,collectionOutput),collectionOutput);
                }
            }
        }finally{map.drainForCoordinator();Reference.reachabilityFence(value);Reference.reachabilityFence(this);}
    }
    void prepareSavePublication(NativeLightingMap previous, NativeLightingMap next) {
        if(!map.sharesIdentityResources(previous)||!map.sharesIdentityResources(next))throw new IllegalStateException("Incompatible light save publication");
        try {
            // Rust's actual retirement producer owns eligibility. Ordinary
            // publications have no save continuation to prepare or synthesize.
            if((int)INT.getAcquire(metadata,84L)==0)return;
            synchronized(collectionOutput) {
                check((int)PREPARE_SAVE.invokeExact(owner,previous.ownerForCoordinator(),next.ownerForCoordinator(),collectionOutput),collectionOutput);
            }
        }catch(Throwable failure){throw failure(failure);}
        finally{Reference.reachabilityFence(previous);Reference.reachabilityFence(next);Reference.reachabilityFence(this);}
    }
    DataLayer retiredLayerForSave(long key) {
        try {
            if((int)INT.getAcquire(metadata,80L)==0)return null;
            try(Arena arena=Arena.ofConfined()) {
                MemorySegment output=arena.allocate(2L*Long.BYTES,Long.BYTES);
                try {
                    check((int)SAVED_ACQUIRE.invokeExact(owner,key,output),output);
                    return map.resolveForCoordinator((int)output.getAtIndex(ValueLayout.JAVA_LONG,1));
                }finally{SAVED_RELEASE.invokeExact(output.getAtIndex(ValueLayout.JAVA_LONG,0));}
            }
        }catch(Throwable failure){throw failure(failure);}
        finally{Reference.reachabilityFence(this);}
    }
    void markInconsistent(){change(0,10,0,1);}
    void updateSectionStatus(long key,boolean empty){run(0,key,empty?1:0);}
    void putSectionState(long key,byte state){run(8,key,Byte.toUnsignedInt(state));}
    void affectSection(long key){run(9,key,0);}
    void reconcile(){run(1,0,0);}
    void publish(){run(2,0,0);}
    void setStored(long pos,int value){run(3,pos,value);}
    DataLayer writable(long key){return map.resolveForCoordinator((int)run(4,key,0));}
    boolean hasLightWork(){try{return(int)INT.getAcquire(engineMetadata,12L)!=0;}finally{Reference.reachabilityFence(this);}}
    int runLightUpdates(){
        try{
            if((int)INT.getAcquire(engineMetadata,4L)!=0){
                engine.clearChunkCacheForNativeCoordinator();
                return 0;
            }
            return(int)run(5,0,0);
        }finally{Reference.reachabilityFence(this);}
    }
    boolean seedSky(int x,int z,int top,int bottom,NativeLightPropagation.SkySeed seed){
        try{return run(10,Integer.toUnsignedLong(x)|((long)z<<32),((long)top<<32)|Integer.toUnsignedLong(bottom),null,null,seed.columnsForCoordinator())!=0;}
        finally{Reference.reachabilityFence(seed);Reference.reachabilityFence(this);}
    }
    void checkNode(long pos){run(6,pos,0);}
    private long run(int op,long a,long b){
        return run(op,a,b,null);
    }
    void installResults(NativeLightPropagation propagation,long handle,int written,int affected){run(7,handle,((long)written<<32)|Integer.toUnsignedLong(affected),propagation);}
    private long run(int op,long a,long b,NativeLightPropagation propagation){
        return run(op,a,b,propagation,null);
    }
    @FunctionalInterface private interface CollectionLeaf {Object call(int leaf,long key,long previous);}
    private record CollectionContext(int which,int kind,int input,MemorySegment entry,CollectionLeaf leaf){}
    private long collection(int which,int kind,long key,int input,DataLayer value,CollectionLeaf leaf){
        MemorySegment entry=which==5?map.entryForCoordinator(value):MemorySegment.NULL;
        try{return run(-1,key,0,null,new CollectionContext(which,kind,input,entry,leaf));}
        finally{map.releaseCoordinatorEntry(entry);map.drainForCoordinator();Reference.reachabilityFence(value);}
    }
    private long run(int op,long a,long b,NativeLightPropagation propagation,CollectionContext collection){
        return run(op,a,b,propagation,collection,MemorySegment.NULL);
    }
    private long run(int op,long a,long b,NativeLightPropagation propagation,CollectionContext collection,MemorySegment seed){
        long frame=0;
        MemorySegment resultEntry=MemorySegment.NULL;
        try(Arena arena=Arena.ofConfined()){
            MemorySegment output=arena.allocate(6L*Long.BYTES,Long.BYTES);
            frame=op==10?(long)SEED_BEGIN.invokeExact(owner,binding,a,b,seed):collection==null?(long)BEGIN.invokeExact(owner,binding,op,a,b):
                (long)COLLECTION_BEGIN.invokeExact(owner,binding,collection.which(),collection.kind(),a,collection.input(),collection.entry());
            if(frame==0)throw new IllegalStateException("Native light continuation unavailable");
            long scalar=0;
            net.minecraft.world.level.block.state.BlockState nodeState=null;
            net.minecraft.world.level.chunk.NativeLightBlocks.NodeLease nodeInput=null;
            for(;;){
                int action;
                try{action=(int)STEP.invokeExact(owner,frame,scalar,resultEntry,output);}
                finally{map.releaseCoordinatorEntry(resultEntry);resultEntry=MemorySegment.NULL;}
                if(output.getAtIndex(ValueLayout.JAVA_LONG,3)==0)nodeState=null;
                long changed=output.getAtIndex(ValueLayout.JAVA_LONG,5);
                MemorySegment changes=MemorySegment.ofAddress(output.getAtIndex(ValueLayout.JAVA_LONG,4)).reinterpret(changed*Integer.BYTES);
                for(long i=0;i<changed;i++)map.refreshForCoordinator(changes.getAtIndex(ValueLayout.JAVA_INT,i));
                check(action,output);
                long x=output.getAtIndex(ValueLayout.JAVA_LONG,0),y=output.getAtIndex(ValueLayout.JAVA_LONG,1),z=output.getAtIndex(ValueLayout.JAVA_LONG,2);
                scalar=0;
                switch(action){
                    case 0 -> {return x;}
                    case 1 -> resultEntry=map.entryForCoordinator(SkyLightSectionStorage.repeatFirstLayer(map.resolveForCoordinator((int)x)));
                    case 2 -> resultEntry=map.entryForCoordinator(map.resolveForCoordinator((int)x).copy());
                    case 3 -> scalar=map.resolveForCoordinator((int)x).get((int)y&15,(int)y>>8,(int)y>>4&15);
                    case 4 -> map.resolveForCoordinator((int)x).set((int)y&15,(int)y>>8,(int)y>>4&15,(int)z);
                    case 5 -> storage.publishNativeMap(map.adoptSnapshot(MemorySegment.ofAddress(x)));
                    case 6 -> storage.chunkSource.onLightUpdate(layer,SectionPos.of(x));
                    case 7 -> {
                        if(nodeInput==null)nodeInput=new net.minecraft.world.level.chunk.NativeLightBlocks.NodeLease();
                        try {
                            nodeState=((BlockLightEngine)engine).stateForNativeNode(x,nodeInput);
                            if(nodeInput.hasOwner()){
                                int status=(int)BLOCK_INPUT.invokeExact(owner,frame,nodeInput.pointer(),nodeInput.index());
                                if(status==1)nodeState=nodeInput.compatibility();
                                else if(status!=0)throw new IllegalStateException("Native block input rejected: "+status);
                                else break;
                            }
                            scalar=Block.BLOCK_STATE_REGISTRY.getId(nodeState);
                        }finally{nodeInput.clear();}
                    }
                    case 8 -> {
                        ChunkSkyLightSources source=((SkyLightEngine)engine).sourceInputForNative((int)x,(int)y,false);
                        if(source==null)scalar=z;
                        else if(source.getClass()!=ChunkSkyLightSources.class)scalar=source.getLowestSourceY((int)x&15,(int)y&15);
                        else try {
                            int status=(int)SOURCE_INPUT.invokeExact(owner,frame,MemorySegment.ofAddress(source.nativeOwnerForLighting()));
                            if(status!=0)throw new IllegalStateException("Native source input rejected: "+status);
                        }finally{Reference.reachabilityFence(source);}
                    }
                    case 9 -> scalar=engine.propagateForNativeCoordinator();
                    case 10 -> engine.clearChunkCacheForNativeCoordinator();
                    case 11 -> scalar=nodeState.getLightEmission();
                    case 12 -> scalar=LightEngine.isEmptyShape(nodeState)?1:0;
                    case 13 -> propagation.installCompatibilityResult((int)y,map.resolveForCoordinator((int)x));
                    case 14 -> engine.checkNode(x);
                    case 15 -> {
                        Object result=collection.leaf().call((int)x,y,z);
                        if(result instanceof Boolean value)scalar=value?1:0;
                        else if(collection.which()==1)scalar=result==null?ABSENT:Byte.toUnsignedInt((Byte)result);
                        else resultEntry=map.entryForCoordinator((DataLayer)result);
                    }
                    default -> throw new IllegalStateException("Unknown native light effect "+action);
                }
            }
        }catch(Throwable error){throw failure(error);}
        finally{
            try{
                map.releaseCoordinatorEntry(resultEntry);
                if(frame!=0)try{if((int)END.invokeExact(owner,frame)!=0)throw new IllegalStateException("Native light continuation retired twice");}
                    catch(Throwable error){throw failure(error);}
                map.drainForCoordinator();
            }finally{
                Reference.reachabilityFence(propagation);Reference.reachabilityFence(engine);
                Reference.reachabilityFence(storage);Reference.reachabilityFence(this);
            }
        }
    }
    private final class Walk {
        final MemorySegment cursor;
        Walk(int which){
            try{
                MemorySegment pointer=(MemorySegment)WALK_CREATE.invokeExact(owner,binding,which);
                if(pointer.address()==0)throw new IllegalStateException("Native light cursor unavailable");
                cursor=NativeLightingMap.adopt(pointer,1,WALK_RELEASE);
            }catch(Throwable error){throw failure(error);}
        }
        long[] call(int op){
            try(Arena arena=Arena.ofConfined()){
                MemorySegment output=arena.allocate(3L*Long.BYTES,Long.BYTES);
                check((int)WALK.invokeExact(owner,cursor,op,output),output);
                return output.toArray(ValueLayout.JAVA_LONG);
            }catch(Throwable error){throw failure(error);}
            finally{Reference.reachabilityFence(this);Reference.reachabilityFence(NativeLightCoordinator.this);}
        }
        boolean hasNext(){return call(2)[0]!=0;}
        void bulk(int mode,BulkAction action){
            long lease=0;
            try(Arena arena=Arena.ofConfined()){
                MemorySegment output=arena.allocate(3L*Long.BYTES,Long.BYTES);
                lease=(long)BULK_BEGIN.invokeExact(cursor,mode);
                if(lease==0)throw new IllegalStateException("Native bulk cursor unavailable");
                for(;;){
                    check((int)BULK_STEP.invokeExact(lease,0,output),output);
                    if(output.getAtIndex(ValueLayout.JAVA_LONG,0)==0)return;
                    int at=(int)output.getAtIndex(ValueLayout.JAVA_LONG,1);
                    long key=output.getAtIndex(ValueLayout.JAVA_LONG,2);
                    action.accept(key,at);
                    check((int)BULK_STEP.invokeExact(lease,1,output),output);
                }
            }catch(Throwable error){throw failure(error);}
            finally{
                try{if(lease!=0)BULK_END.invokeExact(lease);}
                catch(Throwable error){throw failure(error);}
                finally{Reference.reachabilityFence(this);Reference.reachabilityFence(NativeLightCoordinator.this);}
            }
        }
    }
    @FunctionalInterface private interface BulkAction {void accept(long key,int at);}
    @FunctionalInterface private interface SplitFactory<T>{T wrap(RangeView range);}
    private final class RangeView {
        final MemorySegment owner;
        final NativeLightingMap.ReleaseOnce release;
        final int which,kind;
        RangeView(MemorySegment owner,NativeLightingMap.ReleaseOnce release,int which,int kind){this.owner=owner;this.release=release;this.which=which;this.kind=kind;}
        void cancel(Throwable original){try{release.accept(MemorySegment.NULL);}catch(Throwable cleanup){try{original.addSuppressed(cleanup);}catch(RuntimeException|Error ignored){}}}
        long query(int op,long arg,int field){
            try(Arena arena=Arena.ofConfined()){
                MemorySegment output=arena.allocate(2L*Long.BYTES,Long.BYTES);
                check((int)SPLIT_QUERY.invokeExact(owner,op,arg,output),output);
                return output.getAtIndex(ValueLayout.JAVA_LONG,field);
            }catch(Throwable error){throw failure(error);}
            finally{Reference.reachabilityFence(this);Reference.reachabilityFence(NativeLightCoordinator.this);}
        }
        long estimate(){return query(0,kind,0);}
        int characteristics(){return(int)query(0,kind,1);}
        long skip(long count){if(count<0)throw new IllegalArgumentException("Argument must be nonnegative: "+count);return query(1,count,0);}
        boolean traverse(boolean all,BulkAction action){
            long lease=0;boolean seen=false;
            try(Arena arena=Arena.ofConfined()){
                MemorySegment output=arena.allocate(3L*Long.BYTES,Long.BYTES);
                lease=(long)SPLIT_BULK_BEGIN.invokeExact(owner,all?1:0);
                if(lease==0)throw new IllegalStateException("Native range continuation unavailable");
                for(;;){
                    check((int)BULK_STEP.invokeExact(lease,0,output),output);
                    if(output.getAtIndex(ValueLayout.JAVA_LONG,0)==0)return seen;
                    int at=(int)output.getAtIndex(ValueLayout.JAVA_LONG,1);long key=output.getAtIndex(ValueLayout.JAVA_LONG,2);
                    seen=true;action.accept(key,at);
                    check((int)BULK_STEP.invokeExact(lease,1,output),output);
                }
            }catch(Throwable error){throw failure(error);}
            finally{
                try{if(lease!=0)BULK_END.invokeExact(lease);}catch(Throwable error){throw failure(error);}
                finally{Reference.reachabilityFence(this);Reference.reachabilityFence(NativeLightCoordinator.this);}
            }
        }
    }
    private RangeView adoptRange(MemorySegment pointer,int which,int kind){
        NativeLightingMap.ReleaseOnce action;
        try{action=new NativeLightingMap.ReleaseOnce(SPLIT_RELEASE,pointer);}
        catch(RuntimeException|Error error){release(SPLIT_RELEASE,pointer);throw error;}
        try{
            MemorySegment owned=pointer.asReadOnly().reinterpret(1,Arena.ofAuto(),action);
            return new RangeView(owned,action,which,kind);
        }catch(Throwable error){
            try{action.accept(pointer);}catch(Throwable cleanup){try{error.addSuppressed(cleanup);}catch(RuntimeException|Error ignored){}}
            throw failure(error);
        }
    }
    private <T>T rootRange(int which,int kind,SplitFactory<T> factory){
        try{
            MemorySegment pointer=(MemorySegment)SPLIT_CREATE.invokeExact(owner,binding,which);
            if(pointer.address()==0)throw new IllegalStateException("Native range unavailable");
            RangeView range=adoptRange(pointer,which,kind);
            try{return factory.wrap(range);}catch(Throwable error){range.cancel(error);throw error;}
        }catch(Throwable error){throw failure(error);}
        finally{Reference.reachabilityFence(this);}
    }
    private <T>T childRange(RangeView parent,SplitFactory<T> factory){
        try{
            MemorySegment pointer=(MemorySegment)SPLIT_PREPARE.invokeExact(parent.owner);
            if(pointer.address()==0)return null;
            RangeView child=adoptRange(pointer,parent.which,parent.kind);
            try{
                T wrapper=factory.wrap(child);
                if((int)SPLIT_COMMIT.invokeExact(child.owner)!=0)throw new IllegalStateException("Native split donor changed before commit");
                return wrapper;
            }catch(Throwable error){child.cancel(error);throw error;}
        }catch(Throwable error){throw failure(error);}
        finally{Reference.reachabilityFence(parent);Reference.reachabilityFence(this);}
    }
    private LongSpliterator longSpliterator(RangeView range){
        return new LongSpliterator(){
            public boolean tryAdvance(java.util.function.LongConsumer action){return range.traverse(false,(key,at)->action.accept(range.kind==0?key:change(range.which,8,0,at)));}
            public void forEachRemaining(java.util.function.LongConsumer action){range.traverse(true,(key,at)->action.accept(range.kind==0?key:change(range.which,8,0,at)));}
            public long estimateSize(){return range.estimate();}
            public int characteristics(){return range.characteristics();}
            public LongSpliterator trySplit(){return childRange(range,NativeLightCoordinator.this::longSpliterator);}
            public long skip(long count){return range.skip(count);}
        };
    }
    private ByteSpliterator byteSpliterator(RangeView range){
        return new ByteSpliterator(){
            public boolean tryAdvance(ByteConsumer action){return range.traverse(false,(key,at)->action.accept((byte)change(range.which,9,0,at)));}
            public void forEachRemaining(ByteConsumer action){range.traverse(true,(key,at)->action.accept((byte)change(range.which,9,0,at)));}
            public long estimateSize(){return range.estimate();}
            public int characteristics(){return range.characteristics();}
            public ByteSpliterator trySplit(){return childRange(range,NativeLightCoordinator.this::byteSpliterator);}
            public long skip(long count){return range.skip(count);}
        };
    }
    private <T>ObjectSpliterator<T> objectSpliterator(RangeView range,java.util.function.IntFunction<T> value){
        return new ObjectSpliterator<>(){
            public boolean tryAdvance(java.util.function.Consumer<? super T> action){return range.traverse(false,(key,at)->action.accept(value.apply(at)));}
            public void forEachRemaining(java.util.function.Consumer<? super T> action){range.traverse(true,(key,at)->action.accept(value.apply(at)));}
            public long estimateSize(){return range.estimate();}
            public int characteristics(){return range.characteristics();}
            public ObjectSpliterator<T> trySplit(){return childRange(range,child->objectSpliterator(child,value));}
            public long skip(long count){return range.skip(count);}
        };
    }
    private final class SetView extends AbstractLongSet {
        final int which;
        SetView(int which){this.which=which;}
        @Override public int size(){return NativeLightCoordinator.this.size(which);}
        @Override public boolean contains(long key){return change(which,0,key,0)!=0;}
        @Override public boolean add(long key){return change(which,2,key,1)==ABSENT;}
        @Override public boolean remove(long key){return change(which,3,key,0)!=ABSENT;}
        @Override public void clear(){change(which,5,0,0);}
        @Override public LongIterator iterator(){
            Walk walk=new Walk(which);
            return new LongIterator(){
                public boolean hasNext(){return walk.hasNext();}
                public long nextLong(){return walk.call(0)[0];}
                public void remove(){walk.call(1);}
                public void forEachRemaining(java.util.function.LongConsumer action){walk.bulk(0,(key,at)->action.accept(key));}
            };
        }
        @Override public void forEach(java.util.function.LongConsumer action){new Walk(which).bulk(2,(key,at)->action.accept(key));}
        @Override public LongSpliterator spliterator(){return rootRange(which,0,NativeLightCoordinator.this::longSpliterator);}
    }
    private final class StateView extends AbstractLong2ByteMap {
        private byte value(long raw){return raw==ABSENT?defaultReturnValue():(byte)raw;}
        @Override public int size(){return NativeLightCoordinator.this.size(1);}
        @Override public boolean containsKey(long key){return change(1,0,key,0)!=0;}
        @Override public boolean containsValue(byte value){return collection(1,9,0,Byte.toUnsignedInt(value),null,null)!=0;}
        @Override public byte get(long key){return value(change(1,1,key,0));}
        @Override public byte put(long key,byte value){return value(change(1,2,key,Byte.toUnsignedInt(value)));}
        @Override public byte remove(long key){return value(change(1,3,key,0));}
        @Override public void clear(){change(1,5,0,0);}
        @Override public void defaultReturnValue(byte value){change(1,7,0,Byte.toUnsignedInt(value));}
        @Override public byte defaultReturnValue(){return(byte)change(1,6,0,0);}
        @Override public byte computeIfAbsent(long key,java.util.function.LongToIntFunction function){
            java.util.Objects.requireNonNull(function);
            return(byte)collection(1,0,key,0,null,(leaf,k,old)->it.unimi.dsi.fastutil.SafeMath.safeIntToByte(function.applyAsInt(k)));
        }
        @Override public byte computeIfAbsentNullable(long key,java.util.function.LongFunction<? extends Byte> function){
            java.util.Objects.requireNonNull(function);return(byte)collection(1,1,key,0,null,(leaf,k,old)->function.apply(k));
        }
        @Override public byte computeIfAbsent(long key,Long2ByteFunction function){
            java.util.Objects.requireNonNull(function);return(byte)collection(1,5,key,0,null,(leaf,k,old)->leaf==1?function.containsKey(k):function.get(k));
        }
        @Override public byte computeIfPresent(long key,java.util.function.BiFunction<? super Long,? super Byte,? extends Byte> function){
            java.util.Objects.requireNonNull(function);return(byte)collection(1,2,key,0,null,(leaf,k,old)->function.apply(k,(byte)old));
        }
        @Override public byte compute(long key,java.util.function.BiFunction<? super Long,? super Byte,? extends Byte> function){
            java.util.Objects.requireNonNull(function);return(byte)collection(1,3,key,0,null,(leaf,k,old)->function.apply(k,old==ABSENT?null:(byte)old));
        }
        @Override public byte merge(long key,byte value,java.util.function.BiFunction<? super Byte,? super Byte,? extends Byte> function){
            java.util.Objects.requireNonNull(function);return(byte)collection(1,4,key,Byte.toUnsignedInt(value),null,(leaf,k,old)->function.apply((byte)old,value));
        }
        private final class EntryView implements Long2ByteMap.Entry {
            int at;
            EntryView(int at){this.at=at;}
            public long getLongKey(){return change(1,8,0,at);}
            public byte getByteValue(){return(byte)change(1,9,0,at);}
            public byte setValue(byte value){return(byte)change(1,11,at,Byte.toUnsignedInt(value));}
            @Override public boolean equals(Object value){
                if(!(value instanceof java.util.Map.Entry<?,?> entry))return false;
                return getLongKey()==((Long)entry.getKey()).longValue() && getByteValue()==((Byte)entry.getValue()).byteValue();
            }
            @Override public int hashCode(){return Long.hashCode(getLongKey())^Byte.hashCode(getByteValue());}
            @Override public String toString(){return getLongKey()+"=>"+getByteValue();}
        }
        private final class Entries extends AbstractObjectSet<Long2ByteMap.Entry> implements Long2ByteMap.FastEntrySet {
            public int size(){return StateView.this.size();}
            public void clear(){StateView.this.clear();}
            @Override public ObjectSpliterator<Long2ByteMap.Entry> spliterator(){return rootRange(1,1,range->objectSpliterator(range,EntryView::new));}
            public ObjectIterator<Long2ByteMap.Entry> iterator(){return iterator(false);}
            public ObjectIterator<Long2ByteMap.Entry> fastIterator(){return iterator(true);}
            private ObjectIterator<Long2ByteMap.Entry> iterator(boolean fast){
                Walk walk=new Walk(1);
                return new ObjectIterator<>(){
                    EntryView current;
                    public boolean hasNext(){return walk.hasNext();}
                    public Long2ByteMap.Entry next(){
                        int at=(int)walk.call(0)[2];
                        if(fast&&current!=null)current.at=at;else current=new EntryView(at);
                        return current;
                    }
                    public void remove(){walk.call(1);if(!fast&&current!=null)current.at=-1;}
                    public void forEachRemaining(java.util.function.Consumer<? super Long2ByteMap.Entry> action){
                        walk.bulk(1,(key,at)->{
                            if(fast&&current!=null)current.at=at;else current=new EntryView(at);
                            action.accept(current);
                        });
                    }
                };
            }
            @Override public void forEach(java.util.function.Consumer<? super Long2ByteMap.Entry> action){new Walk(1).bulk(3,(key,at)->action.accept(new EntryView(at)));}
            @Override public void fastForEach(java.util.function.Consumer<? super Long2ByteMap.Entry> action){
                EntryView entry=new EntryView(0);new Walk(1).bulk(3,(key,at)->{entry.at=at;action.accept(entry);});
            }
            @Override public boolean contains(Object value){
                return match(value,false);
            }
            @Override public boolean remove(Object value){
                return match(value,true);
            }
            private boolean match(Object value,boolean remove){
                if(!(value instanceof java.util.Map.Entry<?,?> e))return false;
                if(e.getKey()==null || !(e.getKey() instanceof Long))return false;
                if(e.getValue()==null || !(e.getValue() instanceof Byte))return false;
                long key=((Long)e.getKey()).longValue();byte expected=((Byte)e.getValue()).byteValue();
                return collection(1,remove?7:6,key,Byte.toUnsignedInt(expected),null,null)!=0;
            }
        }
        private final Entries entries=new Entries();
        private LongSet keys;
        private ByteCollection values;
        @Override public ObjectSet<Long2ByteMap.Entry> long2ByteEntrySet(){return entries;}
        @Override public LongSet keySet(){
            if(keys==null)keys=new AbstractLongSet(){
                public int size(){return StateView.this.size();}
                public boolean contains(long key){return StateView.this.containsKey(key);}
                public boolean remove(long key){int before=size();StateView.this.remove(key);return size()!=before;}
                public void clear(){StateView.this.clear();}
                @Override public LongSpliterator spliterator(){return rootRange(1,2,NativeLightCoordinator.this::longSpliterator);}
                public LongIterator iterator(){
                    Walk walk=new Walk(1);
                    return new LongIterator(){
                        public boolean hasNext(){return walk.hasNext();}
                        public long nextLong(){return walk.call(0)[0];}
                        public void remove(){walk.call(1);}
                        public void forEachRemaining(java.util.function.LongConsumer action){walk.bulk(1,(key,at)->action.accept(change(1,8,0,at)));}
                    };
                }
                public void forEach(java.util.function.LongConsumer action){new Walk(1).bulk(3,(key,at)->action.accept(change(1,8,0,at)));}
            };
            return keys;
        }
        @Override public ByteCollection values(){
            if(values==null)values=new AbstractByteCollection(){
                public int size(){return StateView.this.size();}
                public boolean contains(byte value){return StateView.this.containsValue(value);}
                public void clear(){StateView.this.clear();}
                @Override public ByteSpliterator spliterator(){return rootRange(1,3,NativeLightCoordinator.this::byteSpliterator);}
                public ByteIterator iterator(){
                    Walk walk=new Walk(1);
                    return new ByteIterator(){
                        public boolean hasNext(){return walk.hasNext();}
                        public byte nextByte(){return(byte)walk.call(0)[1];}
                        public void remove(){walk.call(1);}
                        public void forEachRemaining(ByteConsumer action){walk.bulk(1,(key,at)->action.accept((byte)change(1,9,0,at)));}
                    };
                }
                public void forEach(ByteConsumer action){new Walk(1).bulk(3,(key,at)->action.accept((byte)change(1,9,0,at)));}
            };
            return values;
        }
    }
    private final class QueuedView extends AbstractLong2ObjectMap<DataLayer> {
        @Override public int size(){return NativeLightCoordinator.this.size(5);}
        @Override public boolean containsKey(long key){return change(5,0,key,0)!=0;}
        @Override public boolean containsValue(Object value){return collection(5,9,0,0,null,(leaf,key,old)->java.util.Objects.equals(map.resolveForCoordinator((int)old),value))!=0;}
        @Override public DataLayer get(long key){long slot=change(5,1,key,0);return map.resolveForCoordinator((int)(slot==ABSENT?change(5,13,0,0):slot));}
        private DataLayer replace(int op,long key,DataLayer value){
            MemorySegment entry=map.entryForCoordinator(value);
            try{return map.resolveForCoordinator((int)change(5,op,key,0,entry));}
            finally{map.releaseCoordinatorEntry(entry);map.drainForCoordinator();Reference.reachabilityFence(value);}
        }
        @Override public DataLayer put(long key,DataLayer value){return replace(2,key,value);}
        @Override public DataLayer remove(long key){try{return map.resolveForCoordinator((int)change(5,3,key,0));}finally{map.drainForCoordinator();}}
        @Override public boolean remove(long key,Object value){return collection(5,7,key,0,null,(leaf,k,old)->java.util.Objects.equals(value,map.resolveForCoordinator((int)old)))!=0;}
        @Override public boolean replace(long key,DataLayer oldValue,DataLayer newValue){return collection(5,8,key,0,newValue,(leaf,k,old)->java.util.Objects.equals(oldValue,map.resolveForCoordinator((int)old)))!=0;}
        @Override public void clear(){try{change(5,5,0,0);}finally{map.drainForCoordinator();}}
        @Override public void defaultReturnValue(DataLayer value){replace(12,0,value);}
        @Override public DataLayer defaultReturnValue(){return map.resolveForCoordinator((int)change(5,13,0,0));}
        private DataLayer callback(int kind,long key,DataLayer value,CollectionLeaf leaf){return map.resolveForCoordinator((int)collection(5,kind,key,0,value,leaf));}
        @Override public DataLayer computeIfAbsent(long key,java.util.function.LongFunction<? extends DataLayer> function){
            java.util.Objects.requireNonNull(function);return callback(0,key,null,(leaf,k,old)->function.apply(k));
        }
        @Override public DataLayer computeIfAbsent(long key,Long2ObjectFunction<? extends DataLayer> function){
            java.util.Objects.requireNonNull(function);return callback(5,key,null,(leaf,k,old)->leaf==1?function.containsKey(k):function.get(k));
        }
        @Override public DataLayer computeIfPresent(long key,java.util.function.BiFunction<? super Long,? super DataLayer,? extends DataLayer> function){
            java.util.Objects.requireNonNull(function);return callback(2,key,null,(leaf,k,old)->function.apply(k,map.resolveForCoordinator((int)old)));
        }
        @Override public DataLayer compute(long key,java.util.function.BiFunction<? super Long,? super DataLayer,? extends DataLayer> function){
            java.util.Objects.requireNonNull(function);return callback(3,key,null,(leaf,k,old)->function.apply(k,old==ABSENT?null:map.resolveForCoordinator((int)old)));
        }
        @Override public DataLayer merge(long key,DataLayer value,java.util.function.BiFunction<? super DataLayer,? super DataLayer,? extends DataLayer> function){
            java.util.Objects.requireNonNull(function);java.util.Objects.requireNonNull(value);
            return callback(4,key,value,(leaf,k,old)->function.apply(map.resolveForCoordinator((int)old),value));
        }
        private final class EntryView implements Long2ObjectMap.Entry<DataLayer> {
            int at;
            EntryView(int at){this.at=at;}
            public long getLongKey(){return change(5,8,0,at);}
            public DataLayer getValue(){long slot=change(5,9,0,at);return slot==ABSENT?null:map.resolveForCoordinator((int)slot);}
            public DataLayer setValue(DataLayer value){return replace(11,at,value);}
            @Override public boolean equals(Object value){
                if(!(value instanceof java.util.Map.Entry<?,?> entry))return false;
                return getLongKey()==((Long)entry.getKey()).longValue() && java.util.Objects.equals(getValue(),entry.getValue());
            }
            @Override public int hashCode(){return Long.hashCode(getLongKey())^java.util.Objects.hashCode(getValue());}
            @Override public String toString(){return getLongKey()+"=>"+getValue();}
        }
        private final class Entries extends AbstractObjectSet<Long2ObjectMap.Entry<DataLayer>> implements Long2ObjectMap.FastEntrySet<DataLayer> {
            public int size(){return QueuedView.this.size();}
            public void clear(){QueuedView.this.clear();}
            @Override public ObjectSpliterator<Long2ObjectMap.Entry<DataLayer>> spliterator(){return rootRange(5,1,range->objectSpliterator(range,EntryView::new));}
            public ObjectIterator<Long2ObjectMap.Entry<DataLayer>> iterator(){return iterator(false);}
            public ObjectIterator<Long2ObjectMap.Entry<DataLayer>> fastIterator(){return iterator(true);}
            private ObjectIterator<Long2ObjectMap.Entry<DataLayer>> iterator(boolean fast){
                Walk walk=new Walk(5);
                return new ObjectIterator<>(){
                    EntryView current;
                    public boolean hasNext(){return walk.hasNext();}
                    public Long2ObjectMap.Entry<DataLayer> next(){
                        int at=(int)walk.call(0)[2];
                        if(fast&&current!=null)current.at=at;else current=new EntryView(at);
                        return current;
                    }
                    public void remove(){walk.call(1);if(!fast&&current!=null)current.at=-1;map.drainForCoordinator();}
                    public void forEachRemaining(java.util.function.Consumer<? super Long2ObjectMap.Entry<DataLayer>> action){
                        walk.bulk(1,(key,at)->{
                            if(fast&&current!=null)current.at=at;else current=new EntryView(at);
                            action.accept(current);
                        });
                    }
                };
            }
            @Override public void forEach(java.util.function.Consumer<? super Long2ObjectMap.Entry<DataLayer>> action){new Walk(5).bulk(3,(key,at)->action.accept(new EntryView(at)));}
            @Override public void fastForEach(java.util.function.Consumer<? super Long2ObjectMap.Entry<DataLayer>> action){
                EntryView entry=new EntryView(0);new Walk(5).bulk(3,(key,at)->{entry.at=at;action.accept(entry);});
            }
            @Override public boolean contains(Object value){
                return match(value,false);
            }
            @Override public boolean remove(Object value){
                return match(value,true);
            }
            private boolean match(Object value,boolean remove){
                if(!(value instanceof java.util.Map.Entry<?,?> e))return false;
                if(e.getKey()==null || !(e.getKey() instanceof Long))return false;
                long key=((Long)e.getKey()).longValue();Object expected=e.getValue();
                return collection(5,remove?7:6,key,0,null,(leaf,k,old)->java.util.Objects.equals(map.resolveForCoordinator((int)old),expected))!=0;
            }
        }
        private final Entries entries=new Entries();
        private LongSet keys;
        private ObjectCollection<DataLayer> values;
        @Override public ObjectSet<Long2ObjectMap.Entry<DataLayer>> long2ObjectEntrySet(){return entries;}
        @Override public LongSet keySet(){
            if(keys==null)keys=new AbstractLongSet(){
                public int size(){return QueuedView.this.size();}
                public boolean contains(long key){return QueuedView.this.containsKey(key);}
                public boolean remove(long key){int before=size();QueuedView.this.remove(key);return size()!=before;}
                public void clear(){QueuedView.this.clear();}
                @Override public LongSpliterator spliterator(){return rootRange(5,2,NativeLightCoordinator.this::longSpliterator);}
                public LongIterator iterator(){
                    Walk walk=new Walk(5);
                    return new LongIterator(){
                        public boolean hasNext(){return walk.hasNext();}
                        public long nextLong(){return walk.call(0)[0];}
                        public void remove(){walk.call(1);map.drainForCoordinator();}
                        public void forEachRemaining(java.util.function.LongConsumer action){walk.bulk(1,(key,at)->action.accept(change(5,8,0,at)));}
                    };
                }
                public void forEach(java.util.function.LongConsumer action){new Walk(5).bulk(3,(key,at)->action.accept(change(5,8,0,at)));}
            };
            return keys;
        }
        @Override public ObjectCollection<DataLayer> values(){
            if(values==null)values=new AbstractObjectCollection<>(){
                public int size(){return QueuedView.this.size();}
                public boolean contains(Object value){return QueuedView.this.containsValue(value);}
                public void clear(){QueuedView.this.clear();}
                @Override public ObjectSpliterator<DataLayer> spliterator(){return rootRange(5,4,range->objectSpliterator(range,at->map.resolveForCoordinator((int)change(5,9,0,at))));}
                public ObjectIterator<DataLayer> iterator(){
                    Walk walk=new Walk(5);
                    return new ObjectIterator<>(){
                        public boolean hasNext(){return walk.hasNext();}
                        public DataLayer next(){return map.resolveForCoordinator((int)walk.call(0)[1]);}
                        public void remove(){walk.call(1);map.drainForCoordinator();}
                        public void forEachRemaining(java.util.function.Consumer<? super DataLayer> action){walk.bulk(1,(key,at)->action.accept(map.resolveForCoordinator((int)change(5,9,0,at))));}
                    };
                }
                public void forEach(java.util.function.Consumer<? super DataLayer> action){new Walk(5).bulk(3,(key,at)->action.accept(map.resolveForCoordinator((int)change(5,9,0,at))));}
            };
            return values;
        }
    }
}
