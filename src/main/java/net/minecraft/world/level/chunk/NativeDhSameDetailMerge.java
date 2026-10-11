package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.util.NativeLibraryLoader;
import org.jetbrains.annotations.Nullable;

/** Prepared complete source merge; retained footprints own independent native values. */
public final class NativeDhSameDetailMerge implements AutoCloseable {
    private static MethodHandle handle(String name, FunctionDescriptor signature) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_same_detail_" + name, signature);
    }
    private static final MethodHandle APPLY_METADATA = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_same_detail_metadata",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS),Linker.Option.critical(true));
    private final MemorySegment actions;
    private final int actionCount;
    private static final MethodHandle SOURCES = handle("sources", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final class SourceScratch {
        final Arena arena = Arena.ofAuto();
        final MemorySegment facts = arena.allocate(4096L * 6, 8), footprints = arena.allocate(4096L * 72, 8), metadata = arena.allocate(88, 8);
        final byte[] uniform = new byte[4096], publication = new byte[4096];
        final long[] projection = new long[4096*4];
        final MemorySegment projectionHeap = MemorySegment.ofArray(projection);
    }
    private static final ThreadLocal<SourceScratch> SOURCE_SCRATCH = ThreadLocal.withInitial(SourceScratch::new);
    private static final MethodHandle CREATE = handle("create", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle VIEW = handle("view", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle PROJECTION = NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_dh_same_detail_projection",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS),Linker.Option.critical(true));
    private static final MethodHandle COMMIT_SOURCE = handle("commit_source", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle COMMIT = handle("commit", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle TAKE = handle("take", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final class Scratch {
        final Arena arena = Arena.ofAuto();
        final MemorySegment inputs = arena.allocate(4096L * 48, 8), footprints = arena.allocate(4096L * 64, 8);
        final MemorySegment metadata = arena.allocate(88, 8), targets = arena.allocate(4096L * 8, 8), output = arena.allocate(4096L * 16, 8);
        final MemorySegment detached = arena.allocate(4096);
        final IdentityHashMap<NativeDhColumnList, Boolean> unique = new IdentityHashMap<>();
    }
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private static final java.lang.ref.Cleaner CLEANER = java.lang.ref.Cleaner.create();
    private final java.lang.ref.Cleaner.Cleanable cleanup;
    private final MemorySegment owner, flags, metadata, hashes, footprints;
    private final boolean changed;
    private Object sourceLease;
    private NativeDhSource targetSource;
    private NativeDhSameDetailMerge(MemorySegment pointer, MemorySegment output, Object source) {
        var arena = Arena.ofShared(); boolean attached = false;
        try {
            if ((int) VIEW.invokeExact(pointer, output) != 1) throw new IllegalStateException("Invalid native DH merge");
            owner = pointer.reinterpret(1, arena, NativeDhSameDetailMerge::release); attached = true;
            for (int i = 1; i < 8; i += 2) if (output.getAtIndex(ValueLayout.JAVA_LONG, i) != 4096)
                throw new IllegalStateException("Invalid native DH merge extent");
            flags = output.getAtIndex(ValueLayout.ADDRESS, 0).reinterpret(4096, arena, null).asReadOnly();
            metadata = output.getAtIndex(ValueLayout.ADDRESS, 2).reinterpret(8192, arena, null).asReadOnly();
            hashes = output.getAtIndex(ValueLayout.ADDRESS, 4).reinterpret(16384, arena, null).asReadOnly();
            footprints = output.getAtIndex(ValueLayout.ADDRESS, 6).reinterpret(4096L * 64, arena, null).asReadOnly();
            changed = output.getAtIndex(ValueLayout.JAVA_LONG, 8) != 0;
            long count = output.getAtIndex(ValueLayout.JAVA_LONG,10);
            if(count<0||count>4096)throw new IllegalStateException("Invalid DH footprint action count");
            actionCount=(int)count; actions=output.getAtIndex(ValueLayout.ADDRESS,9).reinterpret(count*4,arena,null).asReadOnly();
            sourceLease = source;
            cleanup = CLEANER.register(this, arena::close);
        } catch (RuntimeException | Error e) { if (!attached) release(pointer); arena.close(); throw e; }
        catch (Throwable e) { if (!attached) release(pointer); arena.close(); throw new IllegalStateException(e); }
    }
    /** Retained columns eliminate per-column Java identity scans and descriptors. */
    @Nullable public static NativeDhSameDetailMerge prepareSources(NativeDhSource target, NativeDhSource source,
        ByteArrayList targetGeneration, ByteArrayList targetCompression, ByteArrayList sourceGeneration, ByteArrayList sourceCompression,
        boolean[] uniform, Map<Integer, LongList[]> oldFootprints, Map<Integer, LongList[]> sourceFootprints, int[] remap) {
        if (!target.isLive() || !source.isLive() || uniform.length != 4096 || targetGeneration.getClass() != ByteArrayList.class || sourceGeneration.getClass() != ByteArrayList.class
            || targetCompression.getClass() != ByteArrayList.class || sourceCompression.getClass() != ByteArrayList.class
            || targetGeneration.size() != 4096 || targetCompression.size() != 4096 || sourceGeneration.size() != 4096 || sourceCompression.size() != 4096
            || oldFootprints.size() > 4096 || sourceFootprints.size() > 4096) return null;
        byte[][] arrays = {targetGeneration.elements(), targetCompression.elements(), sourceGeneration.elements(), sourceCompression.elements()};
        for (int i = 0; i < 4; i++) for (int j = 0; j < i; j++) if (arrays[i] == arrays[j]) return null;
        var scratch = SOURCE_SCRATCH.get();
        try {
            for (int i = 0; i < 4; i++) MemorySegment.copy(MemorySegment.ofArray(arrays[i]),0,scratch.facts,i*4096L,4096);
            for (int i = 0; i < 4096; i++) scratch.uniform[i] = (byte)(uniform[i] ? 1 : 0);
            MemorySegment.copy(MemorySegment.ofArray(scratch.uniform),0,scratch.facts,4096L*4,4096);
            scratch.facts.asSlice(4096L*5,4096).fill((byte)0);
            for (int index : oldFootprints.keySet()) {
                if (index < 0 || index >= 4096) return null;
                scratch.facts.set(ValueLayout.JAVA_BYTE,4096L*5+index,(byte)1);
            }
            int row = 0;
            for (var entry : sourceFootprints.entrySet()) {
                int index = entry.getKey(); if (index < 0 || index >= 4096) return null;
                scratch.footprints.set(ValueLayout.JAVA_LONG,row*72L,(long)index);
                if (!NativeDhContributors.stageSnapshot(entry.getValue(),scratch.footprints.asSlice(row*72L+8,64))) return null;
                row++;
            }
            try (var arena = Arena.ofConfined()) {
                var ids = arena.allocateFrom(ValueLayout.JAVA_INT,remap);
                var pointer = (MemorySegment)SOURCES.invokeExact(target.stageOwner(),source.stageOwner(),scratch.facts,scratch.footprints,
                    row,ids,remap.length,oldFootprints.size());
                if (pointer.address() == 0) return null;
                var merge = new NativeDhSameDetailMerge(pointer,scratch.metadata,java.util.List.of(source,sourceFootprints,target));
                merge.targetSource = target;
                return merge;
            }
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(target); Reference.reachabilityFence(source); Reference.reachabilityFence(sourceFootprints); }
    }
    /** Raw arrays are facts, not Java generation/admission policy answers. */
    @Nullable public static NativeDhSameDetailMerge prepare(LongList[] target, LongList[] source,
        ByteArrayList targetGeneration, ByteArrayList targetCompression, ByteArrayList sourceGeneration, ByteArrayList sourceCompression,
        boolean[] uniform, Map<Integer, LongList[]> oldFootprints, Map<Integer, LongList[]> sourceFootprints, int[] remap) {
        if (target.length != 4096 || source.length != 4096 || uniform.length != 4096
            || targetGeneration.getClass() != ByteArrayList.class || sourceGeneration.getClass() != ByteArrayList.class
            || targetCompression.getClass() != ByteArrayList.class || targetCompression.size() != 4096
            || sourceCompression.getClass() != ByteArrayList.class || targetGeneration.size() != 4096
            || sourceGeneration.size() != 4096 || sourceCompression.size() != 4096
            || oldFootprints.size() > 4096 || sourceFootprints.size() > 4096) return null;
        var oldSteps = targetGeneration.elements(); var newSteps = sourceGeneration.elements(); var modes = sourceCompression.elements();
        var oldModes = targetCompression.elements();
        if (oldSteps == newSteps || oldSteps == modes || newSteps == modes
            || oldModes == oldSteps || oldModes == newSteps || oldModes == modes) return null;
        var scratch = SCRATCH.get();
        try {
            for (var column : target) if (column != null) {
                if (!(column instanceof NativeDhColumnList nativeColumn) || column.size() > 4096
                    || scratch.unique.put(nativeColumn, Boolean.TRUE) != null) return null;
            }
            for (var column : source) if (column != null) {
                if (column instanceof NativeDhColumnList nativeColumn) {
                    if (column.size() > 4096 || scratch.unique.containsKey(nativeColumn)) return null;
                } else if (column.getClass() != LongArrayList.class || !column.isEmpty()) return null;
            }
            for (int i = 0; i < 4096; i++) {
                var input = source[i] instanceof NativeDhColumnList nativeColumn ? nativeColumn : null;
                var previous = (NativeDhColumnList) target[i];
                scratch.inputs.setAtIndex(ValueLayout.ADDRESS, i * 6L, input == null ? MemorySegment.NULL : input.stagePoints());
                scratch.inputs.setAtIndex(ValueLayout.JAVA_LONG, i * 6L + 1, input == null ? 0L : (long) input.size());
                scratch.inputs.setAtIndex(ValueLayout.ADDRESS, i * 6L + 2, previous == null ? MemorySegment.NULL : previous.stagePoints());
                scratch.inputs.setAtIndex(ValueLayout.JAVA_LONG, i * 6L + 3, previous == null ? 0L : (long) previous.size());
                long facts = Byte.toUnsignedInt(oldSteps[i]) | (Byte.toUnsignedInt(newSteps[i]) << 8) | (Byte.toUnsignedInt(modes[i]) << 16)
                    | (uniform[i] ? 1 << 24 : 0) | (source[i] == null ? 0 : 1 << 25) | (previous == null ? 0 : 1 << 26);
                scratch.inputs.setAtIndex(ValueLayout.JAVA_LONG, i * 6L + 4, facts);
                scratch.inputs.setAtIndex(ValueLayout.ADDRESS, i * 6L + 5, MemorySegment.NULL);
            }
            for (int index : oldFootprints.keySet()) {
                if (index < 0 || index >= 4096) return null;
                long offset = index * 6L + 4;
                scratch.inputs.setAtIndex(ValueLayout.JAVA_LONG, offset, scratch.inputs.getAtIndex(ValueLayout.JAVA_LONG, offset) | (1 << 27));
            }
            for (var entry : sourceFootprints.entrySet()) {
                int index = entry.getKey(); if (index < 0 || index >= 4096) return null;
                var header = scratch.footprints.asSlice(index * 64L, 64);
                if (!NativeDhContributors.stageSnapshot(entry.getValue(), header)) return null;
                scratch.inputs.setAtIndex(ValueLayout.ADDRESS, index * 6L + 5, header);
            }
            try (var arena = Arena.ofConfined()) {
                var ids = arena.allocateFrom(ValueLayout.JAVA_INT, remap);
                var pointer = (MemorySegment) CREATE.invokeExact(scratch.inputs, ids, remap.length, oldFootprints.size());
                return pointer.address() == 0 ? null : new NativeDhSameDetailMerge(pointer, scratch.metadata, source);
            }
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { scratch.unique.clear(); Reference.reachabilityFence(target); Reference.reachabilityFence(source);
            Reference.reachabilityFence(sourceFootprints); Reference.reachabilityFence(oldFootprints); }
    }
    /** One native copy/remap into reusable vector capacity, then CPU view publication. */
    public void commit(LongList[] columns) { commit(columns, null); }
    public void commit(LongList[] columns, NativeDhSource source) {
        if (columns.length != 4096) throw new IllegalArgumentException("DH merge columns");
        if (source != null && source == targetSource) { commitSource(columns, source); return; }
        var scratch = SCRATCH.get();
        try {
            for (int i = 0; i < 4096; i++) scratch.targets.setAtIndex(ValueLayout.ADDRESS, i,
                updated(i) && columns[i] != null ? ((NativeDhColumnList)columns[i]).stageOcclusionOwner(true) : MemorySegment.NULL);
            int result = (int) COMMIT.invokeExact(owner, scratch.targets, scratch.output);
            if (result == 0) throw new IllegalStateException("Invalid DH merge publication");
            if (result == -1) {
                for (int i = 0; i < 4096; i++) if (updated(i) && columns[i] != null) {
                    ((NativeDhColumnList)columns[i]).acceptOcclusionView(scratch.output.getAtIndex(ValueLayout.ADDRESS, i * 2L),
                        scratch.output.getAtIndex(ValueLayout.JAVA_LONG, i * 2L + 1), false, 0);
                }
                throw new OutOfMemoryError("Cannot reserve DH merge storage");
            }
            if (result != 1) throw new IllegalStateException("Invalid DH merge result");
            for (int i = 0; i < 4096; i++) if (updated(i)) {
                var column = (NativeDhColumnList)columns[i];
                if (column == null) {
                    var pointer = (MemorySegment) TAKE.invokeExact(owner, i);
                    column = NativeDhMutableColumn.adopt(pointer);
                    if (source == null) columns[i] = column; else source.setColumn(i, column);
                }
                column.acceptOcclusionView(scratch.output.getAtIndex(ValueLayout.ADDRESS, i * 2L),
                    scratch.output.getAtIndex(ValueLayout.JAVA_LONG, i * 2L + 1), true, hashes.getAtIndex(ValueLayout.JAVA_INT, i));
            }
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(columns); Reference.reachabilityFence(sourceLease); Reference.reachabilityFence(this); }
        sourceLease = null; targetSource = null;
    }
    private void commitSource(LongList[] columns, NativeDhSource source) {
        var scratch = SCRATCH.get();
        try {
            int result = (int) COMMIT_SOURCE.invokeExact(owner, source.stageOwner(), scratch.output, scratch.detached);
            if (result != 1 && result != -1) throw new IllegalStateException("Invalid retained DH publication");
            var publication=SOURCE_SCRATCH.get();
            if((int)PROJECTION.invokeExact(owner,scratch.output,scratch.detached,publication.projectionHeap)!=1)throw new IllegalStateException("Invalid DH projection");
            var rows=publication.projection;
            for (int i = 0; i < 4096; i++) if ((rows[i*4+3]&1)!=0) {
                if ((rows[i*4+3]&8)!=0) continue; // Exact native copy proof: points and extent are unchanged.
                var column = (NativeDhColumnList) columns[i];
                boolean hasOwner = (rows[i*4+3]&4)!=0;
                MemorySegment replacement = null;
                if (result == 1 && !hasOwner) replacement = (MemorySegment) TAKE.invokeExact(owner,i);
                if (column == null) {
                    if (result == -1) continue;
                    column = NativeDhMutableColumn.adopt(replacement, source.group());
                    source.setColumn(i,column);
                    replacement = null;
                }
                // Read numeric addresses: unchanged extents need no temporary FFM segment.
                column.acceptMergeView(rows[i*4],rows[i*4+1],result==1,result==1?(int)rows[i*4+2]:0,hasOwner,replacement);
            }
            if (result == -1) throw new OutOfMemoryError("Cannot reserve retained DH merge storage");
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(columns); Reference.reachabilityFence(sourceLease); Reference.reachabilityFence(source); Reference.reachabilityFence(this); }
        sourceLease = null; targetSource = null;
    }
    /** Close only after publication when no contributor reader retains this batch. */
    @Override public void close() { cleanup.clean(); sourceLease = null; targetSource = null; }
    public boolean publishMetadata(ByteArrayList generation, ByteArrayList compression, boolean[] uniform) {
        if(generation.size()!=4096||compression.size()!=4096||uniform.length!=4096)throw new IllegalArgumentException("DH metadata extent");
        var scratch=SOURCE_SCRATCH.get();
        try {
            int count=(int)APPLY_METADATA.invokeExact(owner,MemorySegment.ofArray(generation.elements()),
                MemorySegment.ofArray(compression.elements()),MemorySegment.ofArray(scratch.publication));
            if(count<0)throw new IllegalStateException("Invalid DH metadata publication");
            for(int i=0;i<4096;i++)if((scratch.publication[i]&1)!=0)uniform[i]=(scratch.publication[i]&2)!=0;
            return count!=0;
        } catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
        finally{Reference.reachabilityFence(this);}
    }
    public int footprintActionCount(){return actionCount;}
    public int footprintAction(int action){
        java.util.Objects.checkIndex(action,actionCount);
        try{return actions.getAtIndex(ValueLayout.JAVA_INT,action);}finally{Reference.reachabilityFence(this);}
    }
    public boolean changed() { return changed; }
    public boolean updated(int index) {
        try { return (flags.getAtIndex(ValueLayout.JAVA_BYTE, index) & 1) != 0; }
        finally { Reference.reachabilityFence(this); }
    }
    public boolean uniform(int index) {
        try { return (flags.getAtIndex(ValueLayout.JAVA_BYTE, index) & 2) != 0; }
        finally { Reference.reachabilityFence(this); }
    }
    public byte generation(int index) {
        try { return metadata.getAtIndex(ValueLayout.JAVA_BYTE, index * 2L); }
        finally { Reference.reachabilityFence(this); }
    }
    public byte compression(int index) {
        try { return metadata.getAtIndex(ValueLayout.JAVA_BYTE, index * 2L + 1); }
        finally { Reference.reachabilityFence(this); }
    }
    public LongList[] contributors(int index) {
        try {
            var header = footprints.asSlice(index * 64L, 64);
            return header.getAtIndex(ValueLayout.JAVA_LONG, 1) == -2 ? null : NativeDhContributors.fromDescriptors(this, header);
        } finally { Reference.reachabilityFence(this); }
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); } catch (Throwable e) { throw new IllegalStateException(e); }
    }
}
