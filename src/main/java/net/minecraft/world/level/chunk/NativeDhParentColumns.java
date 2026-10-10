package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.longs.LongList;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;
import org.jetbrains.annotations.Nullable;

/** One Rust producer call and retained CPU owner for a complete parent quadrant. */
public final class NativeDhParentColumns {
    private static MethodHandle handle(String operation, FunctionDescriptor signature) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_parent_columns_" + operation, signature);
    }
    private static final MethodHandle CREATE = handle("create", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle VIEW = handle("view", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final class Scratch {
        final Arena arena = Arena.ofAuto();
        final MemorySegment inputs = arena.allocate(4096L * 24, 8), metadata = arena.allocate(96, 8), existing = arena.allocate(1024);
    }
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private final MemorySegment owner, points, offsets, uniform, contributors, columnMetadata, hashes;
    private NativeDhParentColumns(MemorySegment pointer, MemorySegment metadata) {
        var arena = Arena.ofAuto(); boolean attached = false;
        try {
            if ((int) VIEW.invokeExact(pointer, metadata) != 1) throw new IllegalStateException("Invalid DH parent owner");
            owner = pointer.reinterpret(1, arena, NativeDhParentColumns::release); attached = true;
            long length = metadata.getAtIndex(ValueLayout.JAVA_LONG, 1);
            if (length < 0 || length > 1024L * 4096 || metadata.getAtIndex(ValueLayout.JAVA_LONG, 3) != 1025)
                throw new IllegalStateException("Invalid DH parent extent");
            points = metadata.getAtIndex(ValueLayout.ADDRESS, 0).reinterpret(length * 8, arena, null).asReadOnly();
            offsets = metadata.getAtIndex(ValueLayout.ADDRESS, 2).reinterpret(1025L * 8, arena, null).asReadOnly();
            if (metadata.getAtIndex(ValueLayout.JAVA_LONG, 5) != 1024 || metadata.getAtIndex(ValueLayout.JAVA_LONG, 7) != 1024)
                throw new IllegalStateException("Invalid DH parent semantic extent");
            uniform = metadata.getAtIndex(ValueLayout.ADDRESS, 4).reinterpret(1024, arena, null).asReadOnly();
            contributors = metadata.getAtIndex(ValueLayout.ADDRESS, 6).reinterpret(1024L * 64, arena, null).asReadOnly();
            if (metadata.getAtIndex(ValueLayout.JAVA_LONG, 9) != 1024 || metadata.getAtIndex(ValueLayout.JAVA_LONG, 11) != 1024)
                throw new IllegalStateException("Invalid DH parent metadata extent");
            columnMetadata = metadata.getAtIndex(ValueLayout.ADDRESS, 8).reinterpret(2048, arena, null).asReadOnly();
            hashes = metadata.getAtIndex(ValueLayout.ADDRESS, 10).reinterpret(4096, arena, null).asReadOnly();
        } catch (RuntimeException | Error e) { if (!attached) release(pointer); throw e; }
        catch (Throwable e) { if (!attached) release(pointer); throw new IllegalStateException(e); }
    }
    @Nullable public static NativeDhParentColumns build(NativeDhMaterials materials, LongList[] columns, int[] remap) {
        // This entry point requests only reduced columns, without footprint admission.
        return build(materials, columns, remap, null, null, null, 1024);
    }
    /** Input arrays contain facts; Rust determines uniformity and ordered admission. */
    @Nullable public static NativeDhParentColumns build(NativeDhMaterials materials, LongList[] columns, int[] remap,
        byte[] compression, boolean[] sourceUniform, boolean[] previousFootprints, int retainedFootprints) {
        return build(materials, columns, remap, compression, sourceUniform, previousFootprints, retainedFootprints, null);
    }
    @Nullable public static NativeDhParentColumns build(NativeDhMaterials materials, LongList[] columns, int[] remap,
        byte[] compression, boolean[] sourceUniform, boolean[] previousFootprints, int retainedFootprints, byte[] generationSteps) {
        if (materials == null || columns.length != 4096 || retainedFootprints < 0
            || generationSteps != null && generationSteps.length < 4096
            || compression != null && compression.length < 4096 || sourceUniform != null && sourceUniform.length != 4096
            || previousFootprints != null && previousFootprints.length != 1024) return null;
        for (var column : columns) if (column != null && (!(column instanceof NativeDhColumnList) || column.size() > 4096)) return null;
        var scratch = SCRATCH.get();
        try {
            for (int i = 0; i < 4096; i++) {
                var column = (NativeDhColumnList) columns[i];
                scratch.inputs.setAtIndex(ValueLayout.ADDRESS, i * 3L, column == null ? MemorySegment.NULL : column.stagePoints());
                scratch.inputs.setAtIndex(ValueLayout.JAVA_LONG, i * 3L + 1, column == null ? 0L : (long) column.size());
                long facts = (compression == null ? 0 : Byte.toUnsignedInt(compression[i]))
                    | (sourceUniform != null && sourceUniform[i] ? 256 : 0) | (column == null ? 0 : 512)
                    | (generationSteps == null ? 0 : Byte.toUnsignedInt(generationSteps[i]) << 10);
                scratch.inputs.setAtIndex(ValueLayout.JAVA_LONG, i * 3L + 2, facts);
            }
            for (int i = 0; i < 1024; i++) scratch.existing.setAtIndex(ValueLayout.JAVA_BYTE, i,
                (byte) (previousFootprints != null && previousFootprints[i] ? 1 : 0));
            var pointer = (MemorySegment) CREATE.invokeExact(scratch.inputs, materials.owner, materials.stageRemap(remap), remap.length,
                scratch.existing, retainedFootprints);
            return pointer.address() == 0 ? null : new NativeDhParentColumns(pointer, scratch.metadata);
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(columns); Reference.reachabilityFence(materials); }
    }
    /** Returned lists keep this immutable native owner alive and detach on writes. */
    public NativeDhColumnList column(int x, int z) {
        if (x < 0 || x >= 32 || z < 0 || z >= 32) throw new IndexOutOfBoundsException("DH parent column");
        int index = x * 32 + z;
        try {
            long first = offsets.getAtIndex(ValueLayout.JAVA_LONG, index), last = offsets.getAtIndex(ValueLayout.JAVA_LONG, index + 1);
            return new NativeDhColumnList(this, points.asSlice(first * 8, (last - first) * 8), hashes.getAtIndex(ValueLayout.JAVA_INT, index));
        } finally { Reference.reachabilityFence(this); }
    }
    public byte generationStep(int x, int z) { return metadata(x, z, 0); }
    public byte compressionMode(int x, int z) { return metadata(x, z, 1); }
    private byte metadata(int x, int z, int field) {
        int index = index(x, z);
        try { return columnMetadata.getAtIndex(ValueLayout.JAVA_BYTE, index * 2L + field); }
        finally { Reference.reachabilityFence(this); }
    }
    public boolean isUniform(int x, int z) {
        int index = index(x, z);
        try { return uniform.getAtIndex(ValueLayout.JAVA_BYTE, index) != 0; }
        finally { Reference.reachabilityFence(this); }
    }
    /** Null means no admitted footprint; a four-null array remains a present footprint. */
    @Nullable public LongList[] contributors(int x, int z) {
        int index = index(x, z);
        try {
            var metadata = contributors.asSlice(index * 64L, 64);
            if (metadata.getAtIndex(ValueLayout.JAVA_LONG, 1) == -2) return null;
            return NativeDhContributors.fromDescriptors(this, metadata);
        } finally { Reference.reachabilityFence(this); }
    }
    private static int index(int x, int z) {
        if (x < 0 || x >= 32 || z < 0 || z >= 32) throw new IndexOutOfBoundsException("DH parent column");
        return x * 32 + z;
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); } catch (Throwable e) { throw new IllegalStateException(e); }
    }
}
