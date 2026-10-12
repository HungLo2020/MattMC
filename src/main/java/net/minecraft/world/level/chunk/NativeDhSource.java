package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.longs.LongList;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import net.minecraft.util.NativeLibraryLoader;

/** List identity projections only; Rust retains the live primary column table. */
public final class NativeDhSource implements AutoCloseable {
    private static final MethodHandle CODEC_PROJECT = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_codec_project",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle CLOSE_PROJECTION = handle("close_projection", FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle CLOSED_KIND = handle("closed_kind", FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle CLOSED_COLUMN = handle("closed_column", FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle CLOSED_RELEASE = handle("closed_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private MemorySegment closedProjection;
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
    private static final MethodHandle RETAIN_COLUMN = handle("retain_column",
        FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle READER = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_source_reader",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT),Linker.Option.critical(false));
    private static final MethodHandle MISSING = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_source_reader_masks",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS),Linker.Option.critical(true));
    private static final MethodHandle ORIGINAL = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_source_original",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS),Linker.Option.critical(true));
    private final java.util.concurrent.atomic.AtomicBoolean released = new java.util.concurrent.atomic.AtomicBoolean();
    private long[] closedMissing;
    private final MemorySegment owner;
    private final NativeDhMetadata metadata;
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
            metadata = new NativeDhMetadata(owner);
        } catch (RuntimeException | Error e) { if (!attached && pointer.address() != 0) release(pointer); throw e; }
        catch (Throwable e) { if (!attached && pointer.address() != 0) release(pointer); throw new IllegalStateException(e); }
    }
    public NativeDhMetadataList generation() { return metadata.generation; }
    public NativeDhMetadataList compression() { return metadata.compression; }
    MemorySegment metadataOwner() { return metadata.owner; }
    public void initializeUniform(boolean value) { metadata.uniform.fill((byte)(value?1:0)); }
    public boolean uniform(int index) {
        java.util.Objects.checkIndex(index,4096);
        try { return metadata.uniform.get(ValueLayout.JAVA_BYTE,index)!=0; }
        finally { Reference.reachabilityFence(metadata); }
    }
    public void uniform(int index, boolean value) {
        java.util.Objects.checkIndex(index,4096);
        try { metadata.uniform.set(ValueLayout.JAVA_BYTE,index,(byte)(value?1:0)); }
        finally { Reference.reachabilityFence(metadata); }
    }
    /** Explicit compatibility snapshot; ordinary native consumers read the owned plane. */
    public boolean[] uniformSnapshot() {
        var values=new boolean[4096];for(int i=0;i<4096;i++)values[i]=uniform(i);return values;
    }
    /** Rust owns empty slots and committed vectors; readers are demand-created. */
    public LongList getColumn(int index) {
        var column=bindings[index];
        if(column!=null)return column;
        int kind=kind(index);
        if(kind==0)return null;
        if(kind==2) {
            var view=NativeDhMutableColumn.adopt(isLive() ? retainColumn(index) : retainClosedColumn(index),group);
            bindings[index]=view;view.attach(this,index);return view;
        }
        if(kind!=3)throw new IllegalStateException("Missing DH identity projection");
        if(isLive())try {if((int)READER.invokeExact(owner,index)!=1)throw new IllegalStateException("Invalid DH reader registration");}
        catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
        finally {Reference.reachabilityFence(this);}
        var empty=NativeDhColumnList.empty(group);bindings[index]=empty;empty.attach(this,index);return empty;
    }
    /** Mutation projection must not create readers for unescaped native columns. */
    NativeDhColumnList reader(int index) { return (NativeDhColumnList)bindings[index]; }
    MemorySegment retainColumn(int index) {
        if(!isLive())throw new IllegalStateException("Closed DH source");
        try {return (MemorySegment)RETAIN_COLUMN.invokeExact(owner,index);}
        catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
        finally {Reference.reachabilityFence(this);}
    }
    public LongList[] columnReferences() {
        for(int i=0;i<4096;i++)getColumn(i);
        return bindings.clone();
    }
    LongList boundColumn(int index) { return bindings[index]; }
    /** One scoped Rust reader for a synchronous conversion, without list escapes. */
    public NativeDhConversionReader conversionReader() { return new NativeDhConversionReader(this); }
    private int kind(int index) {
        if(!isLive()) {
            if((closedMissing[index/64]&(1L<<(index%64)))!=0)return 0;
            try{return (int)CLOSED_KIND.invokeExact(closedProjection,index);}
            catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
            finally{Reference.reachabilityFence(this);}
        }
        try {return (int)KIND.invokeExact(owner,index);}
        catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
        finally{Reference.reachabilityFence(this);}
    }
    public boolean isLive(){return !released.get();}
    boolean codecWritable() {
        if (!isLive()) return false;
        for (var binding : bindings) {
            if (binding != null && (!(binding instanceof NativeDhColumnList column) || column.codecAliases(this))) return false;
        }
        return true;
    }
    void projectCodec(MemorySegment scratch) throws Throwable {
        int count = (int)CODEC_PROJECT.invokeExact(owner, scratch);
        if (count < 0 || count > 4096) throw new IllegalStateException("Invalid DH codec projection");
        for (int i = 0; i < count; i++) {
            int at = i * 5;
            int index = (int)scratch.getAtIndex(ValueLayout.JAVA_LONG, at);
            if (!(bindings[index] instanceof NativeDhColumnList column)) continue;
            long pointer = scratch.getAtIndex(ValueLayout.JAVA_LONG, at + 1);
            var replacement = column.codecOwner().address() == pointer ? null : retainColumn(index);
            column.acceptCodecView(scratch.getAtIndex(ValueLayout.JAVA_LONG, at + 2),
                scratch.getAtIndex(ValueLayout.JAVA_LONG, at + 3), scratch.getAtIndex(ValueLayout.JAVA_LONG, at + 4) != 0, replacement);
        }
        Reference.reachabilityFence(this);
    }
    @Override public void close() {
        if(!isLive())return;
        var masks=new long[128];
        try {
            if((int)MISSING.invokeExact(owner,MemorySegment.ofArray(masks))!=1)throw new IllegalStateException("Invalid DH source presence");
            // Retain CPU leases in a lazy closed projection. No point
            // copies or Java column readers are constructed during source close.
            var pointer=(MemorySegment)CLOSE_PROJECTION.invokeExact(owner);
            if(pointer.address()==0)throw new OutOfMemoryError("Closed DH source projection");
            try { closedProjection=pointer.reinterpret(1,group.arena,NativeDhSource::releaseClosed); }
            catch (RuntimeException | Error e) { releaseClosed(pointer); throw e; }
            closedMissing=java.util.Arrays.copyOf(masks,64);
            if(released.compareAndSet(false,true))release(owner);
        }catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
        finally{Reference.reachabilityFence(this);}
    }
    private MemorySegment retainClosedColumn(int index) {
        try{return (MemorySegment)CLOSED_COLUMN.invokeExact(closedProjection,index);}
        catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
        finally{Reference.reachabilityFence(this);}
    }
    private static void releaseClosed(MemorySegment pointer) {
        try{CLOSED_RELEASE.invokeExact(pointer);}catch(Throwable e){throw new IllegalStateException(e);}
    }
    /** -1 keeps custom/closed readers on the original validation sequence. */
    public int originalProducer(NativeDhColumns producer,long position,it.unimi.dsi.fastutil.bytes.ByteList generation,it.unimi.dsi.fastutil.bytes.ByteList compression){
        if(!isLive()||!NativeDhMetadataList.supported(generation)||!NativeDhMetadataList.supported(compression)
            ||generation.size()!=4096||compression.size()!=4096)return -1;
        try{return (int)ORIGINAL.invokeExact(owner,producer.stageSourceOwner(),position,NativeDhMetadataList.borrow(generation),NativeDhMetadataList.borrow(compression));}
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
    public long tryHash(long position,it.unimi.dsi.fastutil.bytes.ByteList generation,it.unimi.dsi.fastutil.bytes.ByteList compression){
        if(!isLive()||!NativeDhMetadataList.supported(generation)||!NativeDhMetadataList.supported(compression)
            ||generation.size()!=4096||compression.size()!=4096)return 0;
        var result=HASH_RESULT.get();
        try{
            if((int)HASH.invokeExact(owner,position,NativeDhMetadataList.borrow(generation),NativeDhMetadataList.borrow(compression),result)!=1)return 0;
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
