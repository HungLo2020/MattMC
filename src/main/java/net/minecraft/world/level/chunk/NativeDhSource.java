package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.longs.LongList;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import net.minecraft.util.NativeLibraryLoader;

/** List identity projections only; Rust retains the live primary column table. */
public final class NativeDhSource implements AutoCloseable {
    private static MethodHandle handle(String name, FunctionDescriptor type) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_source_" + name, type);
    }
    private static final MethodHandle HASH = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_source_hash",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS),Linker.Option.critical(true));
    private static final ThreadLocal<MemorySegment> HASH_RESULT=ThreadLocal.withInitial(()->Arena.ofAuto().allocate(4,4));
    private static final MethodHandle CREATE = handle("create", FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle SET = handle("set", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle KIND = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_source_kind",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT),Linker.Option.critical(false));
    private static final MethodHandle MISSING = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_source_missing",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS),Linker.Option.critical(true));
    private static final MethodHandle ORIGINAL = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_source_original",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS),Linker.Option.critical(true));
    private final java.util.concurrent.atomic.AtomicBoolean released = new java.util.concurrent.atomic.AtomicBoolean();
    private long[] closedMissing;
    private final MemorySegment owner;
    private final LongList[] bindings;
    private final NativeDhColumnGroup group;
    final WeakReference<NativeDhSource> weak = new WeakReference<>(this);
    public NativeDhSource(LongList[] bindings) { this(bindings, new NativeDhColumnGroup()); }
    public NativeDhSource(LongList[] bindings, NativeDhColumnGroup group) {
        if (bindings.length != 4096) throw new IllegalArgumentException("DH source extent");
        this.bindings = bindings;
        this.group = java.util.Objects.requireNonNull(group);
        MemorySegment pointer = MemorySegment.NULL; boolean attached = false;
        try {
            pointer = (MemorySegment) CREATE.invokeExact();
            if (pointer.address() == 0) throw new OutOfMemoryError("DH source owner");
            var disposal = released;
            owner = pointer.reinterpret(1, Arena.ofAuto(), p -> { if (disposal.compareAndSet(false,true)) release(p); }); attached = true;
        } catch (RuntimeException | Error e) { if (!attached && pointer.address() != 0) release(pointer); throw e; }
        catch (Throwable e) { if (!attached && pointer.address() != 0) release(pointer); throw new IllegalStateException(e); }
    }
    /** Rust already owns present-empty slots; Java readers are demand-created. */
    public LongList getColumn(int index) {
        var column=bindings[index];
        if(column!=null)return column;
        int kind=kind(index);
        if(kind==0)return null;
        if(kind!=3)throw new IllegalStateException("Missing DH identity projection");
        var empty=NativeDhColumnList.empty(group);bindings[index]=empty;empty.attach(this,index);return empty;
    }
    public LongList[] columnReferences() {
        for(int i=0;i<4096;i++)getColumn(i);
        return bindings.clone();
    }
    private int kind(int index) {
        if(!isLive())return (closedMissing[index/64]&(1L<<(index%64)))!=0?0:3;
        try {return (int)KIND.invokeExact(owner,index);}
        catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
        finally{Reference.reachabilityFence(this);}
    }
    public boolean isLive(){return !released.get();}
    @Override public void close() {
        if(!isLive())return;
        var missing=new long[64];
        try {
            if((int)MISSING.invokeExact(owner,MemorySegment.ofArray(missing))!=1)throw new IllegalStateException("Invalid DH source presence");
            closedMissing=missing;
            if(released.compareAndSet(false,true))release(owner);
        }catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
        finally{Reference.reachabilityFence(this);}
    }
    /** -1 keeps custom/closed readers on the original validation sequence. */
    public int originalProducer(NativeDhColumns producer,long position,it.unimi.dsi.fastutil.bytes.ByteArrayList generation,it.unimi.dsi.fastutil.bytes.ByteArrayList compression){
        if(!isLive()||generation.getClass()!=it.unimi.dsi.fastutil.bytes.ByteArrayList.class||compression.getClass()!=it.unimi.dsi.fastutil.bytes.ByteArrayList.class
            ||generation.size()!=4096||compression.size()!=4096)return -1;
        try{return (int)ORIGINAL.invokeExact(owner,producer.stageSourceOwner(),position,MemorySegment.ofArray(generation.elements()),MemorySegment.ofArray(compression.elements()));}
        catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
        finally{Reference.reachabilityFence(this);Reference.reachabilityFence(producer);}
    }
    public void setColumn(int index, LongList column) {
        java.util.Objects.checkIndex(index, bindings.length);
        if (bindings[index] == column && (column!=null || kind(index)==0)) return;
        var previous = bindings[index];
        if (column instanceof NativeDhColumnList nativeColumn) {
            var lease = nativeColumn.stageSourceOwner();
            publish(index, lease.address() == 0 ? 3 : 2, lease);
            nativeColumn.attach(this, index);
        } else publish(index, column == null ? 0 : 1, MemorySegment.NULL);
        bindings[index] = column;
        if (previous instanceof NativeDhColumnList old) old.detach(this, index);
    }
    void materialized(int index, NativeDhColumnList column, MemorySegment lease) {
        if (bindings[index] == column) publish(index, 2, lease);
    }
    private void publish(int index, int kind, MemorySegment column) {
        if(!isLive()) {if(kind==0)closedMissing[index/64]|=1L<<(index%64);else closedMissing[index/64]&=~(1L<<(index%64));return;}
        try { if ((int) SET.invokeExact(owner, index, kind, column) != 1) throw new IllegalStateException("DH source binding"); }
        catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(this); Reference.reachabilityFence(column); }
    }
    public long tryHash(long position,it.unimi.dsi.fastutil.bytes.ByteArrayList generation,it.unimi.dsi.fastutil.bytes.ByteArrayList compression){
        if(!isLive()||generation.getClass()!=it.unimi.dsi.fastutil.bytes.ByteArrayList.class||compression.getClass()!=it.unimi.dsi.fastutil.bytes.ByteArrayList.class
            ||generation.size()!=4096||compression.size()!=4096)return 0;
        var result=HASH_RESULT.get();
        try{
            if((int)HASH.invokeExact(owner,position,MemorySegment.ofArray(generation.elements()),MemorySegment.ofArray(compression.elements()),result)!=1)return 0;
            return (1L<<32)|(result.get(ValueLayout.JAVA_INT,0)&0xffffffffL);
        }catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
        finally{Reference.reachabilityFence(this);}
    }
    NativeDhColumnGroup group() { return group; }
    public MemorySegment stageOwner() { if(!isLive())throw new IllegalStateException("Closed DH source");return owner; }
    private static void release(MemorySegment owner) {
        try { RELEASE.invokeExact(owner); } catch (Throwable e) { throw new IllegalStateException(e); }
    }
}
