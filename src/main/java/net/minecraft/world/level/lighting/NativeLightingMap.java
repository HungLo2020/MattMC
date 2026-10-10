package net.minecraft.world.level.lighting;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.world.level.chunk.DataLayer;

/** Rust map policy and typed layer ownership, with temporary CPU object-identity pins. */
final class NativeLightingMap {
    private static final VarHandle INT = ValueLayout.JAVA_INT.varHandle();
    static final class Bindings {
        final MethodHandle registryCreate, registryRelease, registryView, registryViewRelease;
        final MethodHandle reserve, reservationRelease, retired, ack;
        final MethodHandle create, copy, release, view, viewRelease;
        final MethodHandle get, has, set, remove, cache, top, topSet, minY, count, keys, sample;
        Bindings() {
            registryCreate = handle("registry_create",FunctionDescriptor.of(ValueLayout.ADDRESS));
            registryRelease = handle("registry_release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            registryView = handle("registry_view",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
            registryViewRelease = handle("registry_view_release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            reserve = handle("reserve",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
            reservationRelease = handle("reservation_release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            retired = handle("retired",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
            ack = handle("retired_ack",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
            create = handle("create",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
            copy = handle("copy",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
            release = handle("release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            view = handle("view",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
            viewRelease = handle("view_release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            get = handle("get",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
            has = handle("has",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
            set = handle("set",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
            remove = handle("remove",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
            cache = handle("cache",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
            top = handle("top",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
            topSet = handle("top_set",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
            sample = handle("sample",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
            count = handle("count",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
            keys = handle("keys",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
            minY = handle("min_y",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
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
    private static MemorySegment adopt(MemorySegment pointer,long bytes,MethodHandle release) {
        return adopt(pointer,bytes,release,Arena.ofAuto());
    }
    private static MemorySegment adopt(MemorySegment pointer,long bytes,MethodHandle release,Arena arena) {
        if(pointer.address()==0)throw new IllegalStateException("Missing native light-map owner");
        try { return pointer.asReadOnly().reinterpret(bytes,arena,p -> release(release,p)); }
        catch(RuntimeException|Error error){ release(release,pointer);throw error; }
    }
    private static final class CpuView {
        final MemorySegment handle, metadata;
        CpuView(MemorySegment pointer,long bytes,MethodHandle release) {
            Arena arena=Arena.ofAuto();
            handle=adopt(pointer,Long.BYTES,release,arena);
            metadata=handle.get(ValueLayout.ADDRESS,0).asReadOnly().reinterpret(bytes,arena,null);
        }
    }

    private static final class IdentityPins {
        private static final int PAGE_BITS=7, PAGE_SIZE=1<<PAGE_BITS;
        final Bindings bindings;
        final MemorySegment owner, view, pending, drain, reservedSlot;
        @SuppressWarnings("unchecked")
        private volatile AtomicReferenceArray<DataLayer>[] pages = new AtomicReferenceArray[0];
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
        private void put(int slot,DataLayer layer) {
            int page=(int)(Integer.toUnsignedLong(slot)>>>PAGE_BITS);
            var table=pages;
            if(page>=table.length) {
                table=Arrays.copyOf(table,Math.max(page+1,Math.max(8,table.length*2)));
            }
            if(table[page]==null)table[page]=new AtomicReferenceArray<>(PAGE_SIZE);
            table[page].set(slot&(PAGE_SIZE-1),layer);
            pages=table;
        }
        private DataLayer resolve(int slot) {
            if(slot==0)return null;
            var table=pages;int page=(int)(Integer.toUnsignedLong(slot)>>>PAGE_BITS);
            DataLayer layer=table[page].get(slot&(PAGE_SIZE-1));
            if(layer==null)throw new IllegalStateException("Retired CPU light identity before result resolution");
            return layer;
        }
        // Resolve returned objects before draining. A remove can retire its
        // slot during the native call; the Java local then keeps its identity.
        private void drain() {
            if((int)INT.getAcquire(pending,0L)==0)return;
            synchronized(this) {
                try {
                    int count=(int)bindings.retired.invokeExact(owner,drain,64);
                    if(count<0)throw new IllegalStateException("Invalid light identity retirement");
                    for(int i=0;i<count;i++) {
                        int slot=drain.getAtIndex(ValueLayout.JAVA_INT,i);
                        pages[(int)(Integer.toUnsignedLong(slot)>>>PAGE_BITS)].set(slot&(PAGE_SIZE-1),null);
                    }
                    if((int)bindings.ack.invokeExact(owner,drain,count)!=0)throw new IllegalStateException("Invalid light identity acknowledgement");
                } catch(Throwable error){throw failure(error);}
                finally {Reference.reachabilityFence(this);}
            }
        }
    }

    private final Bindings bindings;
    private final IdentityPins pins;
    private final MemorySegment owner, view, metadata;
    private static final class Library { static final Bindings BINDINGS = new Bindings(); }
    NativeLightingMap() { this(Library.BINDINGS,new IdentityPins(Library.BINDINGS),null); }
    private NativeLightingMap(Bindings bindings,IdentityPins pins,MemorySegment source) {
        this.bindings=bindings;this.pins=pins;
        try {
            MemorySegment created=source==null?(MemorySegment)bindings.create.invokeExact(pins.owner):(MemorySegment)bindings.copy.invokeExact(source);
            owner=adopt(created,1,bindings.release);
            CpuView projection=new CpuView((MemorySegment)bindings.view.invokeExact(owner),2L*Integer.BYTES,bindings.viewRelease);
            view=projection.handle;metadata=projection.metadata;
        } catch(Throwable error){throw failure(error);}
        finally {Reference.reachabilityFence(pins);Reference.reachabilityFence(source);}
    }
    NativeLightingMap copy() {
        try {return new NativeLightingMap(bindings,pins,owner);}
        finally {Reference.reachabilityFence(this);}
    }
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
            MemorySegment reservation=MemorySegment.NULL;int slot=0;
            try {
                if(layer!=null) {
                    MemorySegment output=pins.reservedSlot;
                    reservation=(MemorySegment)bindings.reserve.invokeExact(pins.owner,output);
                    if(reservation.address()==0)throw new IllegalStateException("Light identity slots exhausted");
                    slot=output.get(ValueLayout.JAVA_INT,0);pins.put(slot,layer);
                }
                MemorySegment source=layer==null || layer.getClass()!=DataLayer.class
                    ? MemorySegment.NULL:layer.nativeLightOwnerForMap();
                if((int)bindings.set.invokeExact(owner,key,reservation,source)!=0)throw new IllegalStateException("Rejected light-map source");
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
    int lowestY() {return(int)INT.getAcquire(metadata,0L);}
    void lowestY(int value,boolean defaultOnly) {
        try {if((int)bindings.minY.invokeExact(owner,value,defaultOnly?1:0)!=0)throw new IllegalStateException("Rejected sky-height metadata");}
        catch(Throwable error){throw failure(error);}
        finally {Reference.reachabilityFence(this);}
    }
}
