package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.longs.LongList;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import net.minecraft.util.NativeLibraryLoader;

/**
 * The Rust DH full-data source: its column table, metadata planes and semantic
 * footprints, and every source update. Java holds only list identity
 * projections of columns, created on demand, and footprint views.
 */
public final class NativeDhSource implements AutoCloseable {
    private static final MethodHandle CODEC_PROJECT = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_codec_project",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle CLOSE_PROJECTION = handle("close_projection", FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle CLOSED_KIND = handle("closed_kind", FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle CLOSED_COLUMN = handle("closed_column", FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle CLOSED_RELEASE = handle("closed_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle CLOSED_FOOTPRINT = handle("closed_footprint", FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle FOOTPRINT = handle("footprint", FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle FOOTPRINT_SET = handle("footprint_set", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT), Linker.Option.critical(true));
    private static final MethodHandle FOOTPRINT_CLEAR = handle("footprint_clear", FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle FOOTPRINTS_DECODE = handle("footprints_decode", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS), Linker.Option.critical(true));
    private static final MethodHandle UPDATE_PARENT = handle("update_parent", FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,
        ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    private static final MethodHandle DOWNSAMPLE = handle("downsample", FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,
        ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle HASH_FULL = handle("hash_full", FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
    private MemorySegment closedProjection;
    private static MethodHandle handle(String name, FunctionDescriptor type, Linker.Option... options) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_source_" + name, type, options);
    }
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
    /** Footprint cell limit of one source update ({@code MAX_SEMANTIC_HORIZONTAL_CONTRIBUTOR_CELLS}). */
    public static final int FOOTPRINT_POINTS = 256;
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
    /** True when the cell has a footprint: one read of the Rust-maintained presence plane. */
    public boolean hasFootprint(int index) {
        java.util.Objects.checkIndex(index,4096);
        try { return metadata.footprints.get(ValueLayout.JAVA_BYTE,index)!=0; }
        finally { Reference.reachabilityFence(metadata); }
    }
    /**
     * Read-only views of footprints already read, so repeated reads (render
     * conversion, persistence) create no new views. Every operation that can
     * change footprints in Rust drops it.
     */
    private LongList[][] footprintViews;
    void footprintsChanged() { footprintViews = null; }
    /** The cell's footprint as four read-only views (null entries are null columns), or null. */
    public LongList[] footprint(int index) {
        if(!hasFootprint(index))return null;
        var views=footprintViews;
        if(views!=null&&views[index]!=null)return views[index].clone();
        LongList[] adopted;
        try {
            var pointer=isLive() ? (MemorySegment)FOOTPRINT.invokeExact(owner,index) : (MemorySegment)CLOSED_FOOTPRINT.invokeExact(closedProjection,index);
            if(pointer.address()==0)return null;
            adopted=NativeDhContributors.adopt(pointer);
        }catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
        finally{Reference.reachabilityFence(this);}
        if(views==null)footprintViews=views=new LongList[4096][];
        views[index]=adopted;
        return adopted.clone();
    }
    /** Replace the cell's footprint with a copy of {@code columns}, each clipped to {@link #FOOTPRINT_POINTS}; null clears it. */
    public void setFootprint(int index, LongList[] columns) {
        java.util.Objects.checkIndex(index,4096);
        if(!isLive())throw new IllegalStateException("Closed DH source");
        footprintsChanged();
        try {
            if(columns==null){if((int)FOOTPRINT_CLEAR.invokeExact(owner,index)!=1)throw new IllegalStateException("Invalid DH footprint");return;}
            if(columns.length!=4)throw new IllegalArgumentException("DH footprints have four columns");
            long[] spans=new long[8];Object[] pins=new Object[4];
            try(var arena=Arena.ofConfined()) {
                for(int i=0;i<4;i++) {
                    if(columns[i]==null){spans[i*2+1]=-1;continue;}
                    var points=NativeDhContributors.points(columns[i],arena);pins[i]=points;
                    spans[i*2]=points.address();spans[i*2+1]=points.byteSize()/8;
                }
                if((int)FOOTPRINT_SET.invokeExact(owner,index,MemorySegment.ofArray(spans),FOOTPRINT_POINTS)!=1)
                    throw new IllegalStateException("Invalid DH footprint");
            } finally {Reference.reachabilityFence(pins);Reference.reachabilityFence(columns);}
        }catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
        finally{Reference.reachabilityFence(this);}
    }
    /**
     * Decode a decompressed V4 footprint sidecar straight into this source.
     * Returns {@code [status, value]}: 0 complete, 1 end of input, 2 a column
     * over the saved bound (value is its size). Cells before a stop are applied.
     */
    public int[] decodeFootprints(byte[] bytes, int offset, int length) {
        java.util.Objects.checkFromIndexSize(offset,length,bytes.length);
        if(!isLive())throw new IllegalStateException("Closed DH source");
        footprintsChanged();
        long[] result=new long[2];
        try {
            if((int)FOOTPRINTS_DECODE.invokeExact(owner,MemorySegment.ofArray(bytes).asSlice(offset,length),length,MemorySegment.ofArray(result))!=1)
                throw new IllegalStateException("Invalid DH footprint sidecar decode");
        }catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
        finally{Reference.reachabilityFence(this);}
        if(result[0]==3)throw new OutOfMemoryError("Cannot decode DH footprint sidecar");
        return new int[]{(int)result[0],(int)result[1]};
    }
    /**
     * Reduce the one-detail-finer {@code input} into this source's quadrant at
     * cell offset ({@code offsetX}, {@code offsetZ}). One Rust call commits
     * metadata, uniformity, footprints and columns; returns whether point data changed.
     */
    public boolean updateParent(NativeDhSource input, NativeDhMaterials materials, int[] remap, int offsetX, int offsetZ) {
        footprintsChanged();
        long[] out=new long[3];int result;
        // A whole quadrant runs for milliseconds: an ordinary (non-critical) call over native scratch.
        try(var arena=Arena.ofConfined()) {
            var output=arena.allocate(ValueLayout.JAVA_LONG,3);
            result=(int)UPDATE_PARENT.invokeExact(stageOwner(),input.stageOwner(),materials.owner,arena.allocateFrom(ValueLayout.JAVA_INT,remap),remap.length,
                offsetX,offsetZ,output);
            MemorySegment.copy(output,ValueLayout.JAVA_LONG,0,out,0,3);
        }catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
        finally{Reference.reachabilityFence(this);Reference.reachabilityFence(input);Reference.reachabilityFence(materials);}
        // Every quadrant column was replaced: escaped readers keep their old leases.
        for(int x=offsetX;x<offsetX+32;x++)for(int z=offsetZ;z<offsetZ+32;z++)unbind(x*64+z);
        return finish(result,out);
    }
    /**
     * Project the one-detail-coarser {@code input} onto this source, whose
     * cells are the input quadrant at cell offset ({@code offsetX}, {@code offsetZ}).
     * Returns whether any column was written.
     */
    public boolean downsample(NativeDhSource input, int[] remap, int offsetX, int offsetZ) {
        footprintsChanged();
        long[] out=new long[3];long[] replaced=new long[64];int result;
        try(var arena=Arena.ofConfined()) {
            var output=arena.allocate(ValueLayout.JAVA_LONG,3);var written=arena.allocate(ValueLayout.JAVA_LONG,64);
            result=(int)DOWNSAMPLE.invokeExact(stageOwner(),input.stageOwner(),arena.allocateFrom(ValueLayout.JAVA_INT,remap),remap.length,
                offsetX,offsetZ,output,written);
            MemorySegment.copy(output,ValueLayout.JAVA_LONG,0,out,0,3);
            MemorySegment.copy(written,ValueLayout.JAVA_LONG,0,replaced,0,64);
        }catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
        finally{Reference.reachabilityFence(this);Reference.reachabilityFence(input);}
        for(int i=0;i<4096;i++)if((replaced[i/64]&(1L<<(i%64)))!=0)unbind(i);
        return finish(result,out);
    }
    private void unbind(int index) {
        var previous=bindings[index];
        if(previous==null)return;
        bindings[index]=null;
        if(previous instanceof NativeDhColumnList old)old.detach(this,index);
    }
    private static boolean finish(int result, long[] out) {
        if(result==1)return out[0]!=0;
        if(result==-1) {
            int value=(int)out[2];
            switch((int)out[1]) {
                case 1 -> throw new IndexOutOfBoundsException("DH full-data ID ["+value+"] has no mapping entry");
                case 2 -> throw new IndexOutOfBoundsException("DH full-data ID ["+value+"] is outside the remapped IDs");
                case 3 -> throw new IllegalStateException("DH full-data column ["+value+"] has an invalid size");
                default -> {}
            }
        }
        throw new IllegalStateException("Invalid DH source update arguments");
    }
    /**
     * The complete base-data hash, or an exception for an invalid (negative-size) column.
     * A closed source has no table: its escaped readers may still change, so it
     * is hashed over those readers, exactly as {@code generateHashCode} defines.
     */
    public int hash(long position) {
        if(!isLive()) {
            int result=31*Long.hashCode(position)+java.util.Arrays.deepHashCode(columnReferences());
            return 43*(17*result+metadata.generation.hashCode())+metadata.compression.hashCode();
        }
        long tagged;
        try{tagged=(long)HASH_FULL.invokeExact(owner,position);}
        catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
        finally{Reference.reachabilityFence(this);}
        if((tagged>>>32)==0)throw new IllegalStateException("DH full-data column has an invalid size");
        return (int)tagged;
    }
    public LongList[] columnReferences() {
        for(int i=0;i<4096;i++)getColumn(i);
        return bindings.clone();
    }
    /** One scoped Rust reader for a synchronous conversion, without list escapes. */
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
            if (binding instanceof NativeDhColumnList column && column.codecAliases(this)) return false;
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
    /**
     * Bind {@code column} to the cell. Rust owns every bound column: any other
     * list, or a native list already bound to a cell, is bound as a native
     * copy, so a column is never shared between cells. Null makes the cell missing.
     */
    public void setColumn(int index, LongList column) {
        java.util.Objects.checkIndex(index, bindings.length);
        if (bindings[index] == column && (column!=null || kind(index)==0)) return;
        var previous = bindings[index];
        NativeDhColumnList bound = null;
        if (column != null) {
            bound = column instanceof NativeDhColumnList nativeColumn && !nativeColumn.isBound() ? nativeColumn : NativeDhColumnList.copyOf(column, group);
            var lease = bound.stageSourceOwner();
            publish(index, lease.address() == 0 ? 3 : 2, lease);
            bound.attach(this, index);
        } else publish(index, 0, MemorySegment.NULL);
        bindings[index] = bound;
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
    NativeDhColumnGroup group() { return group; }
    public MemorySegment stageOwner() { if(!isLive())throw new IllegalStateException("Closed DH source");return owner; }
    private static void release(MemorySegment owner) {
        try { RELEASE.invokeExact(owner); } catch (Throwable e) { throw new IllegalStateException(e); }
    }
}
