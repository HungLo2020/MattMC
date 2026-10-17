package net.minecraft.world.level.lighting;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.world.level.chunk.DataLayer;

/** Rust map policy and typed layer ownership, with temporary CPU object-identity pins. */
final class NativeLightingMap {
    private static final VarHandle INT = ValueLayout.JAVA_INT.varHandle();
    // Reuse the existing package-internal typed owner adoption; neither this
    // cross-package adapter nor the focused proof adds a public lighting API.
    private static final MethodHandle ADOPT_LAYER=layerMethod("adoptDecoded",MethodType.methodType(DataLayer.class,MemorySegment.class,MemorySegment.class),true);
    private static final MethodHandle REFRESH_LAYER=layerMethod("refreshNativeLightView",MethodType.methodType(void.class),false);
    private static MethodHandle layerMethod(String name,MethodType type,boolean isStatic){
        try{
            var lookup=MethodHandles.privateLookupIn(DataLayer.class,MethodHandles.lookup());
            return isStatic?lookup.findStatic(DataLayer.class,name,type):lookup.findVirtual(DataLayer.class,name,type);
        }catch(ReflectiveOperationException error){throw new ExceptionInInitializerError(error);}
    }
    static final class Bindings {
        final MethodHandle registryCreate, registryRelease, registryView, registryViewRelease;
        final MethodHandle reserve, reservationRelease, retired, ack;
        final MethodHandle reservationBatchCreate,reservationBatchHeader,reservationBatchRelease,reservationBatchCancel;
        final MethodHandle create, copy, release, view, viewRelease, metadata;
        final MethodHandle materialize, entryCreate, entryExisting, entryRelease, identityCreate;
        final MethodHandle get, has, set, remove, cache, top, topSet, minY, count, keys, sample;
        Bindings() {
            registryCreate = handle("registry_create",FunctionDescriptor.of(ValueLayout.ADDRESS));
            registryRelease = handle("registry_release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            registryView = handle("registry_view",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
            registryViewRelease = handle("registry_view_release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            reserve = handle("reserve",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
            reservationRelease = handle("reservation_release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            reservationBatchCreate=handle("reservation_batch_create",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
            reservationBatchHeader=handle("reservation_batch_header",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
            reservationBatchRelease=handle("reservation_batch_release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            reservationBatchCancel=handle("reservation_batch_cancel",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
            retired = handle("retired",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
            ack = handle("retired_ack",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
            create = handle("create",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
            copy = handle("copy",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
            release = handle("release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            view = handle("view",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
            viewRelease = handle("view_release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            metadata = handle("metadata",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
            get = handle("get",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
            has = handle("has",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
            set = handle("set_identity",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
            remove = handle("remove",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
            cache = handle("cache",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
            top = handle("top",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
            topSet = handle("top_set",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
            sample = handle("sample",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
            count = handle("count",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
            keys = handle("keys",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
            minY = handle("min_y",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
            materialize = handle("materialize_primary",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
            entryCreate = handle("entry_create",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
            entryExisting = handle("entry_existing",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
            entryRelease = handle("entry_release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            identityCreate = handle("identity_create",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
        }
        private static MethodHandle handle(String suffix,FunctionDescriptor descriptor) {
            return net.minecraft.util.NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_map_"+suffix,descriptor);
        }
    }
    private static RuntimeException failure(Throwable error) {
        if(error instanceof Error e)throw e;
        if(error instanceof RuntimeException e)return e;
        return new IllegalStateException(error);
    }
    private static void release(MethodHandle method,MemorySegment segment) {
        try { method.invokeExact(segment); } catch(Throwable error){throw failure(error);}
    }
    static MemorySegment adopt(MemorySegment pointer,long bytes,MethodHandle release) {
        return adopt(pointer,bytes,release,null);
    }
    /** Handle-only action shared by failed adoption and eventual arena cleanup. */
    static final class ReleaseOnce implements java.util.function.Consumer<MemorySegment> {
        private final java.util.concurrent.atomic.AtomicBoolean claimed=new java.util.concurrent.atomic.AtomicBoolean();
        private final MethodHandle release;
        private final MemorySegment pointer;
        ReleaseOnce(MethodHandle release,MemorySegment pointer){this.release=release;this.pointer=pointer;}
        public void accept(MemorySegment ignored){if(claimed.compareAndSet(false,true))release(release,pointer);}
    }
    static MemorySegment adopt(MemorySegment pointer,long bytes,MethodHandle release,Arena arena) {
        if(pointer.address()==0)throw new IllegalStateException("Missing native light-map owner");
        ReleaseOnce action;
        try{action=new ReleaseOnce(release,pointer);}
        catch(RuntimeException|Error error){rollback(release,pointer,error);throw error;}
        try { return pointer.asReadOnly().reinterpret(bytes,arena==null?Arena.ofAuto():arena,action); }
        catch(RuntimeException|Error error){
            try{action.accept(pointer);}catch(RuntimeException|Error cleanup){try{error.addSuppressed(cleanup);}catch(RuntimeException|Error ignored){}}
            throw error;
        }
    }
    private static final class CpuView {
        final MemorySegment handle, metadata;
        CpuView(MemorySegment pointer,long bytes,MethodHandle release) {
            handle=adopt(pointer,Long.BYTES,release);
            metadata=handle.get(ValueLayout.ADDRESS,0).asReadOnly().reinterpret(bytes);
        }
    }

    static final class IdentityPins {
        private static final int PAGE_BITS=7, PAGE_SIZE=1<<PAGE_BITS;
        final Bindings bindings;
        final MemorySegment owner, view, pending, drain, reservedSlot;
        @SuppressWarnings("unchecked")
        private volatile AtomicReferenceArray<DataLayer>[] pages = new AtomicReferenceArray[0];
        private static final class Identity {
            final long id;
            int references;
            Identity(long id){this.id=id;}
        }
        // These keys add no roots: every key has at least one corresponding
        // reachability slot, and it is removed when the last slot clears.
        private final java.util.IdentityHashMap<DataLayer,Identity> identities=new java.util.IdentityHashMap<>();
        private ReservationBatch reservationBatch;
        // The global reclaimer may keep this action alive. It must not keep
        // pins, custom layers, an engine or a map alive through that action.
        private static final class RetirementCleanup extends java.lang.ref.WeakReference<IdentityPins> implements Runnable {
            RetirementCleanup(IdentityPins pins) { super(pins); }
            public void run() {
                IdentityPins pins = get();
                if (pins != null) pins.drainRetiredSnapshot();
            }
        }
        final Runnable retirementCleanup = new RetirementCleanup(this);
        // Cursor/metadata are CPU binding transport only. Native cells own all
        // ready/claimed/cancelled reservation state; no map policy is mirrored.
        private static final class ReservationBatch {
            final MemorySegment owner,slots;
            final int count;
            int next;
            ReservationBatch(Bindings bindings,MemorySegment registry)throws Throwable{
                MemorySegment pointer=(MemorySegment)bindings.reservationBatchCreate.invokeExact(registry,64L);
                if(pointer.address()==0)throw new IllegalStateException("Light identity slots exhausted");
                owner=adopt(pointer,1,bindings.reservationBatchRelease);
                MemorySegment header=((MemorySegment)bindings.reservationBatchHeader.invokeExact(owner)).asReadOnly().reinterpret(2L*Long.BYTES);
                long size=header.get(ValueLayout.JAVA_LONG,0);
                if(size<=0||size>64)throw new IllegalStateException("Invalid native identity reservation batch");
                count=(int)size;slots=MemorySegment.ofAddress(header.get(ValueLayout.JAVA_LONG,Long.BYTES)).reinterpret(size*2L*Long.BYTES).asReadOnly();
                Reference.reachabilityFence(owner);
            }
        }
        private ReservationBatch batch()throws Throwable{
            ReservationBatch current=reservationBatch;
            if(current==null||current.next==current.count)reservationBatch=current=new ReservationBatch(bindings,owner);
            return current;
        }
        IdentityPins(Bindings bindings) {
            this.bindings=bindings;
            try {
                owner=adopt((MemorySegment)bindings.registryCreate.invokeExact(),1,bindings.registryRelease);
                CpuView projection=new CpuView((MemorySegment)bindings.registryView.invokeExact(owner),Integer.BYTES,bindings.registryViewRelease);
                view=projection.handle;pending=projection.metadata;
                drain=Arena.ofAuto().allocate(64L*Integer.BYTES,Integer.BYTES);
                reservedSlot=Arena.ofAuto().allocate(ValueLayout.JAVA_INT);
            } catch(Throwable error){throw failure(error);}
        }
        private long put(int slot,DataLayer layer) {return put(slot,layer,0);}
        private long put(int slot,DataLayer layer,long nativeIdentity) {
            int page=(int)(Integer.toUnsignedLong(slot)>>>PAGE_BITS);
            var table=pages;
            if(page>=table.length) {
                table=Arrays.copyOf(table,Math.max(page+1,Math.max(8,table.length*2)));
            }
            if(table[page]==null)table[page]=new AtomicReferenceArray<>(PAGE_SIZE);
            DataLayer previous=table[page].get(slot&(PAGE_SIZE-1));
            if(previous!=null){Identity identity=identities.get(previous);if(--identity.references==0)identities.remove(previous);}
            long id=0;
            if(layer!=null){
                Identity identity=identities.get(layer);
                if(identity==null){
                    id=nativeIdentity;
                    if(id==0)try{id=(long)bindings.identityCreate.invokeExact(owner);}catch(Throwable error){throw failure(error);}
                    if(id==0)throw new IllegalStateException("Light object identities exhausted");
                    identity=new Identity(id);identities.put(layer,identity);
                }
                identity.references++;id=identity.id;
            }
            table[page].set(slot&(PAGE_SIZE-1),layer);
            pages=table;
            return id;
        }
        private DataLayer resolve(int slot) {
            if(slot==0)return null;
            var table=pages;int page=(int)(Integer.toUnsignedLong(slot)>>>PAGE_BITS);
            DataLayer layer=page<table.length && table[page]!=null ? table[page].get(slot&(PAGE_SIZE-1)) : null;
            if(layer!=null)return layer;
            // Native creation/COW need no Java facade until a caller observes
            // the identity. Serialize just this first handoff across snapshots.
            synchronized(this) {
                table=pages;
                layer=page<table.length && table[page]!=null ? table[page].get(slot&(PAGE_SIZE-1)) : null;
                if(layer!=null)return layer;
                try(Arena output=Arena.ofConfined()) {
                    MemorySegment projected=output.allocate(2L*Long.BYTES,Long.BYTES);
                    MemorySegment nativeOwner=(MemorySegment)bindings.materialize.invokeExact(owner,slot,projected);
                    if(nativeOwner.address()==0)throw new IllegalStateException("Retired CPU light identity before result resolution");
                    layer=(DataLayer)ADOPT_LAYER.invokeExact(nativeOwner,projected.get(ValueLayout.ADDRESS,0));
                    put(slot,layer,projected.get(ValueLayout.JAVA_LONG,Long.BYTES));
                    return layer;
                } catch(Throwable error){throw failure(error);}
            }
        }
        // Resolve returned objects before draining. A remove can retire its
        // slot during the native call; the Java local then keeps its identity.
        private void drain() {
            if((int)INT.getAcquire(pending,0L)==0)return;
            synchronized(this) {
                drainBatch();
            }
        }
        private int drainBatch() {
            try {
                int count=(int)bindings.retired.invokeExact(owner,drain,64);
                if(count<0)throw new IllegalStateException("Invalid light identity retirement");
                for(int i=0;i<count;i++) {
                    int slot=drain.getAtIndex(ValueLayout.JAVA_INT,i);
                    put(slot,null);
                }
                if((int)bindings.ack.invokeExact(owner,drain,count)!=0)throw new IllegalStateException("Invalid light identity acknowledgement");
                return count;
            } catch(Throwable error){throw failure(error);}
            finally {Reference.reachabilityFence(this);}
        }
        /** Discharge the pending set observed after actual native release.
         * New concurrent work cannot extend this cleanup without bound. */
        private void drainRetiredSnapshot() {
            synchronized(this) {
                long remaining=Integer.toUnsignedLong((int)INT.getAcquire(pending,0L));
                while(remaining>0) {
                    int count=drainBatch();
                    if(count==0)break;
                    remaining-=count;
                }
            }
        }
    }

    private final Bindings bindings;
    private final IdentityPins pins;
    private final MemorySegment owner, metadata;
    private static final class Library { static final Bindings BINDINGS = new Bindings(); }
    NativeLightingMap() { this(Library.BINDINGS,new IdentityPins(Library.BINDINGS),(MemorySegment)null); }
    static IdentityPins createPins() { return new IdentityPins(Library.BINDINGS); }
    NativeLightingMap(IdentityPins pins) { this(Library.BINDINGS,pins,(MemorySegment)null); }
    private NativeLightingMap(Bindings bindings,IdentityPins pins,MemorySegment source) {
        this(bindings,pins,source,false);
    }
    private NativeLightingMap(Bindings bindings,IdentityPins pins,MemorySegment source,boolean owned) {
        this.bindings=bindings;this.pins=pins;
        MemorySegment created=MemorySegment.NULL;
        try {
            created=owned?source:source==null?(MemorySegment)bindings.create.invokeExact(pins.owner):(MemorySegment)bindings.copy.invokeExact(source);
            if(created.address()==0)throw new IllegalStateException("Missing native light-map owner");
            MemorySegment header=(MemorySegment)bindings.metadata.invokeExact(created);
            if(header.address()==0)throw new IllegalStateException("Missing native light-map metadata");
            // Metadata belongs to this actual root; every reader fences this
            // map, which is the existing reclaimer's sole registration referent.
            owner=created.reinterpret(1).asReadOnly();
            metadata=header.reinterpret(2L*Integer.BYTES).asReadOnly();
            NativeLightBindings.registerMapOwner(this,created,pins.retirementCleanup);
        } catch(Throwable error){
            rollback(bindings.release,created,error);
            throw failure(error);
        }
        finally {Reference.reachabilityFence(pins);Reference.reachabilityFence(source);}
    }
    NativeLightingMap copy() {
        try {return new NativeLightingMap(bindings,pins,owner);}
        finally {Reference.reachabilityFence(this);}
    }
    /** A unique snapshot lease produced by the native coordinator. */
    NativeLightingMap adoptSnapshot(MemorySegment snapshot) {
        SnapshotLease lease;
        try{lease=new SnapshotLease(snapshot);}
        catch(RuntimeException|Error error){release(bindings.release,snapshot);throw error;}
        try{return new NativeLightingMap(bindings,pins,lease);}
        finally{if(!lease.claimed)release(bindings.release,snapshot);Reference.reachabilityFence(this);}
    }
    private static final class SnapshotLease {
        final MemorySegment pointer;
        boolean claimed;
        SnapshotLease(MemorySegment pointer){this.pointer=pointer;}
        MemorySegment take(){claimed=true;return pointer;}
    }
    private NativeLightingMap(Bindings bindings,IdentityPins pins,SnapshotLease lease){
        this(bindings,pins,lease.take(),true);
    }
    private static void rollback(MethodHandle release,MemorySegment pointer,Throwable original){
        if(pointer.address()==0)return;
        try{release(release,pointer);}
        catch(Throwable cleanup){try{original.addSuppressed(cleanup);}catch(RuntimeException|Error ignored){}}
    }
    MemorySegment ownerForCoordinator(){return owner;}
    boolean sharesIdentityResources(NativeLightingMap other){return this.pins==other.pins;}
    DataLayer resolveForCoordinator(int slot) {
        try{return pins.resolve(slot);}
        finally{Reference.reachabilityFence(this);}
    }
    void refreshForCoordinator(int slot) {
        // An unobserved native-created identity can stay lazy on the Java side;
        // its eventual facade is made from the current native generation.
        synchronized(pins) {
            var pages=pins.pages;int page=(int)(Integer.toUnsignedLong(slot)>>>IdentityPins.PAGE_BITS);
            DataLayer layer=page<pages.length && pages[page]!=null ? pages[page].get(slot&(IdentityPins.PAGE_SIZE-1)) : null;
            if(layer!=null)try{REFRESH_LAYER.invokeExact(layer);}catch(Throwable error){throw failure(error);}
        }
        Reference.reachabilityFence(this);
    }
    void drainForCoordinator(){try{pins.drain();}finally{Reference.reachabilityFence(this);}}
    /** Independent typed entry; no Java object crosses the boundary. Release
     * it after the coordinator has retained the result. */
    MemorySegment entryForCoordinator(DataLayer layer) {
        if(layer==null)return MemorySegment.NULL;
        synchronized(pins) {
            MemorySegment reservation=MemorySegment.NULL;int slot=0;
            try {
                MemorySegment nativeLayer=layer.getClass()==DataLayer.class?layer.nativeLightOwnerForMap():MemorySegment.NULL;
                IdentityPins.Identity known=pins.identities.get(layer);
                if(known!=null){
                    MemorySegment retained=(MemorySegment)bindings.entryExisting.invokeExact(owner,known.id,nativeLayer);
                    if(retained.address()!=0)return retained;
                }
                reservation=(MemorySegment)bindings.reserve.invokeExact(pins.owner,pins.reservedSlot);
                if(reservation.address()==0)throw new IllegalStateException("Light identity slots exhausted");
                slot=pins.reservedSlot.get(ValueLayout.JAVA_INT,0);long identity=pins.put(slot,layer);
                MemorySegment entry=(MemorySegment)bindings.entryCreate.invokeExact(owner,reservation,nativeLayer,identity);
                if(entry.address()==0)throw new IllegalStateException("Rejected native light entry");
                reservation=MemorySegment.NULL;return entry;
            } catch(Throwable error){throw failure(error);}
            finally {
                if(reservation.address()!=0){if(slot!=0)pins.put(slot,null);release(bindings.reservationRelease,reservation);}
                Reference.reachabilityFence(layer);Reference.reachabilityFence(this);
            }
        }
    }
    /** Transfer a saved-layer reservation directly to its canonical coordinator.
     * output[2] is the native ownership receipt, including failed insertions. */
    int queueForCoordinator(MemorySegment coordinator,MethodHandle operation,long key,DataLayer layer,MemorySegment output) {
        return queueForCoordinator(coordinator,operation,null,key,layer,output);
    }
    int queueForCoordinator(MemorySegment coordinator,MethodHandle operation,MethodHandle existing,long key,DataLayer layer,MemorySegment output) {
        synchronized(pins) {
            IdentityPins.ReservationBatch batch=null;int index=-1,slot=0;Throwable primary=null;
            output.setAtIndex(ValueLayout.JAVA_LONG,2,0L);
            try {
                long identity=0;MemorySegment nativeLayer=MemorySegment.NULL;
                if(layer!=null){
                    nativeLayer=layer.getClass()==DataLayer.class?layer.nativeLightOwnerForMap():MemorySegment.NULL;
                    IdentityPins.Identity known=pins.identities.get(layer);
                    if(existing!=null&&known!=null){
                        int status=(int)existing.invokeExact(coordinator,key,nativeLayer,known.id,output);
                        if(status!=1)return status;
                    }
                    batch=pins.batch();index=batch.next++;
                    long offset=(long)index*2L*Long.BYTES;
                    slot=batch.slots.get(ValueLayout.JAVA_INT,offset);identity=pins.put(slot,layer,batch.slots.get(ValueLayout.JAVA_LONG,offset+Long.BYTES));
                }
                MemorySegment batchOwner=batch==null?MemorySegment.NULL:batch.owner;
                return (int)operation.invokeExact(coordinator,key,batchOwner,index,nativeLayer,identity,output);
            } catch(Throwable error){primary=error;throw failure(error);}
            finally {
                try{
                    if(batch!=null && output.getAtIndex(ValueLayout.JAVA_LONG,2)==0){
                        if(slot!=0)pins.put(slot,null);
                        if((int)bindings.reservationBatchCancel.invokeExact(batch.owner,index)!=0)throw new IllegalStateException("Reservation cancellation rejected");
                    }
                }catch(Throwable cleanup){
                    if(primary==null)throw failure(cleanup);
                    try{primary.addSuppressed(cleanup);}catch(RuntimeException|Error ignored){}
                }finally{Reference.reachabilityFence(batch);Reference.reachabilityFence(layer);Reference.reachabilityFence(this);}
            }
        }
    }
    void releaseCoordinatorEntry(MemorySegment entry){if(entry.address()!=0)release(bindings.entryRelease,entry);}
    DataLayer get(long key,boolean raw) {
        try {
            int slot=(int)bindings.get.invokeExact(owner,key,raw?1:0);
            DataLayer result=pins.resolve(slot);pins.drain();return result;
        } catch(Throwable error){throw failure(error);}
        finally {Reference.reachabilityFence(this);}
    }
    boolean has(long key) {
        try {boolean result=(int)bindings.has.invokeExact(owner,key)!=0;pins.drain();return result;}
        catch(Throwable error){throw failure(error);}
        finally {Reference.reachabilityFence(this);}
    }
    void set(long key,DataLayer layer) {
        // Original storage excludes concurrent mutation. Registry locking only
        // serializes CPU slot handoffs among distinct snapshots sharing pins.
        synchronized(pins) {
            MemorySegment reservation=MemorySegment.NULL;int slot=0;long identity=0;
            try {
                if(layer!=null) {
                    MemorySegment output=pins.reservedSlot;
                    reservation=(MemorySegment)bindings.reserve.invokeExact(pins.owner,output);
                    if(reservation.address()==0)throw new IllegalStateException("Light identity slots exhausted");
                    slot=output.get(ValueLayout.JAVA_INT,0);identity=pins.put(slot,layer);
                }
                MemorySegment source=layer==null || layer.getClass()!=DataLayer.class
                    ? MemorySegment.NULL:layer.nativeLightOwnerForMap();
                if((int)bindings.set.invokeExact(owner,key,reservation,source,identity)!=0)throw new IllegalStateException("Rejected light-map source");
                reservation=MemorySegment.NULL; // Native map consumed this reservation.
                pins.drain();
            } catch(Throwable error){throw failure(error);}
            finally {
                if(reservation.address()!=0) {if(slot!=0)pins.put(slot,null);release(bindings.reservationRelease,reservation);}
                Reference.reachabilityFence(layer);Reference.reachabilityFence(this);
            }
        }
    }
    DataLayer remove(long key) {
        // Distinct snapshots share retirement. Resolve the returned identity
        // before another snapshot can acknowledge/reuse its retired slot.
        synchronized(pins) {
            try {int slot=(int)bindings.remove.invokeExact(owner,key);DataLayer result=pins.resolve(slot);pins.drain();return result;}
            catch(Throwable error){throw failure(error);}
            finally {Reference.reachabilityFence(this);}
        }
    }
    void clearCache(boolean disable) {
        try {if((int)bindings.cache.invokeExact(owner,disable?1:0)!=0)throw new IllegalStateException("Rejected light-map cache update");pins.drain();}
        catch(Throwable error){throw failure(error);}
        finally {Reference.reachabilityFence(this);}
    }
    int top(long key) {
        try {return(int)bindings.top.invokeExact(owner,key);}
        catch(Throwable error){throw failure(error);}
        finally {Reference.reachabilityFence(this);}
    }
    int putTop(long key,int value,boolean remove) {
        try {return(int)bindings.topSet.invokeExact(owner,key,value,remove?1:0);}
        catch(Throwable error){throw failure(error);}
        finally {Reference.reachabilityFence(this);}
    }
    long sample(long block, boolean sky, boolean updating, boolean lightOn) {
        try {
            long result = (long)bindings.sample.invokeExact(owner, block, sky ? 1 : 0, updating ? 1 : 0, lightOn ? 1 : 0);
            pins.drain(); // Bounded reclamation also runs during otherwise idle scalar reads.
            return result;
        }
        catch (Throwable error) { throw failure(error); }
        finally { Reference.reachabilityFence(this); }
    }
    private long[] keysForVerification(boolean tops) {
        try (Arena output = Arena.ofConfined()) {
            int count = (int)bindings.count.invokeExact(owner, tops ? 1 : 0);
            if (count < 0) throw new IllegalStateException("Invalid light-map projection");
            MemorySegment keys = output.allocate((long)count * Long.BYTES, Long.BYTES);
            if ((int)bindings.keys.invokeExact(owner, tops ? 1 : 0, keys, count) != count)
                throw new IllegalStateException("Light-map projection changed during verification");
            return keys.toArray(ValueLayout.JAVA_LONG);
        } catch (Throwable error) { throw failure(error); }
        finally { Reference.reachabilityFence(this); }
    }
    java.util.Map<Long,DataLayer> snapshotForVerification() {
        var result = new java.util.HashMap<Long,DataLayer>();
        for (long key : keysForVerification(false)) result.put(key, get(key, true));
        return result;
    }
    java.util.Map<Long,Integer> topsForVerification() {
        var result = new java.util.HashMap<Long,Integer>();
        for (long key : keysForVerification(true)) result.put(key, top(key));
        return result;
    }
    int lowestY() {try{return(int)INT.getAcquire(metadata,0L);}finally{Reference.reachabilityFence(this);}}
    void lowestY(int value,boolean defaultOnly) {
        try {if((int)bindings.minY.invokeExact(owner,value,defaultOnly?1:0)!=0)throw new IllegalStateException("Rejected sky-height metadata");}
        catch(Throwable error){throw failure(error);}
        finally {Reference.reachabilityFence(this);}
    }
}
