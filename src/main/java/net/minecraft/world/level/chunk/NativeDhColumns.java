package net.minecraft.world.level.chunk;

import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.dataObjects.BlockBiomeWrapperPair;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.Nullable;

/** Retained Rust DH points/material IDs; all views are ordinary CPU memory. */
public final class NativeDhColumns {
    private static final MethodHandle BUILD = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_columns_from_chunk",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle VIEW = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_columns_view",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_columns_release",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private final MemorySegment owner, points, offsets;
    private final BlockBiomeWrapperPair[] compatibilityPairs;
    private final long pos;
    private final int chunkX, chunkZ, compression, sourceHash;
    private NativeDhColumns(MemorySegment pointer, MemorySegment metadata, List<Holder<Biome>> biomes, ILevelWrapper level) {
        Arena arena = Arena.ofAuto(); boolean attached = false;
        try {
            if ((int)VIEW.invokeExact(pointer, metadata) != 1) throw new IllegalStateException("Invalid DH columns");
            long count = metadata.getAtIndex(ValueLayout.JAVA_LONG, 1), materialCount = metadata.getAtIndex(ValueLayout.JAVA_LONG, 4);
            if (count < 256 || count > 256L * 4082 || materialCount < 1 || materialCount > count + 256) throw new IllegalStateException("DH column view bounds");
            MemorySegment materials = metadata.getAtIndex(ValueLayout.ADDRESS, 3).reinterpret(materialCount * 8).asReadOnly();
            pos = metadata.getAtIndex(ValueLayout.JAVA_LONG, 5);
            chunkX = (int)metadata.getAtIndex(ValueLayout.JAVA_LONG, 6); chunkZ = (int)metadata.getAtIndex(ValueLayout.JAVA_LONG, 7);
            compression = (int)metadata.getAtIndex(ValueLayout.JAVA_LONG, 8);
            sourceHash = (int)metadata.getAtIndex(ValueLayout.JAVA_LONG, 9);
            compatibilityPairs = new BlockBiomeWrapperPair[(int)materialCount];
            // Resolve each unique compatibility identity once, preserving the
            // producer's wrapper-cache timing without retaining the live world.
            for (int id = 0; id < compatibilityPairs.length; id++) {
                int biome = materials.getAtIndex(ValueLayout.JAVA_INT, id * 2L);
                int state = materials.getAtIndex(ValueLayout.JAVA_INT, id * 2L + 1);
                var rawState = Block.stateById(state); var holder = biomes.get(biome);
                var blockWrapper = state == 0 ? BlockStateWrapper.AIR : BlockStateWrapper.fromBlockState(rawState, level);
                var biomeWrapper = BiomeWrapper.getBiomeWrapper(holder, level);
                compatibilityPairs[id] = BlockBiomeWrapperPair.get(blockWrapper, biomeWrapper);
            }
            owner = pointer.reinterpret(1, arena, NativeDhColumns::release); attached = true;
            points = metadata.getAtIndex(ValueLayout.ADDRESS, 0).reinterpret(count * 8, arena, null).asReadOnly();
            offsets = metadata.getAtIndex(ValueLayout.ADDRESS, 2).reinterpret(257 * 4, arena, null).asReadOnly();
        } catch (RuntimeException | Error e) { if (!attached) release(pointer); throw e; }
        catch (Throwable e) { if (!attached) release(pointer); throw new IllegalStateException("Cannot adopt DH columns", e); }
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); }
        catch (Throwable e) { throw new IllegalStateException("Cannot release DH columns", e); }
    }
    /** Custom world, wrapper/cache overrides and unready biome stages decline. */
    @Nullable public static NativeDhColumns build(ILevelWrapper level, ChunkWrapper wrapper, int compression) {
        if (level.getClass() != ClientLevelWrapper.class && level.getClass() != ServerLevelWrapper.class
            && level.getClass() != com.seibel.distanthorizons.common.wrappers.level.ServerKeyedClientLevelWrapper.class) return null;
        if (!(level.getWrappedMcObject() instanceof Level world) || world.registryAccess().lookupOrThrow(Registries.BLOCK) != BuiltInRegistries.BLOCK) return null;
        var input = wrapper.nativeDhLightingInput();
        if (input == null || input.previous() == null) return null;
        var chunk = input.chunk();
        if (chunk instanceof ProtoChunk && !chunk.getPersistedStatus().isOrAfter(ChunkStatus.BIOMES)) return null;
        var sections = chunk.nativeDhColumnSections();
        if (sections == null || sections.size() > 255) return null;
        var registry = world.registryAccess().lookupOrThrow(Registries.BIOME);
        List<Holder<Biome>> biomeValues = null;
        try (Arena scratch = Arena.ofConfined()) {
            MemorySegment metadata = scratch.allocate(80, 8);
            for (int s = 0; s < sections.size(); s++) {
                var q = sections.biome(s);
                if (biomeValues == null) {
                    biomeValues = q.binding.values;
                    if (!matchesRegistry(biomeValues, registry)) return null;
                } else if (biomeValues != q.binding.values && !sameHolders(biomeValues, q.binding.values)) return null;
            }
            var pos = chunk.getPos();
            MemorySegment pointer = (MemorySegment)BUILD.invokeExact(sections.stageOwner(), input.heights().stageOwner(), input.previous().stageOwner(),
                pos.x, pos.z, compression);
            return pointer.address() == 0 ? null : new NativeDhColumns(pointer, metadata, biomeValues, level);
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Cannot build native DH columns", e); }
        finally { Reference.reachabilityFence(sections); Reference.reachabilityFence(input); }
    }
    /** The last pair of bindings proven to hold the same holders in the same order. */
    private record Equivalent(java.lang.ref.WeakReference<List<Holder<Biome>>> a, java.lang.ref.WeakReference<List<Holder<Biome>>> b) {}
    private static volatile Equivalent equivalent;
    /**
     * Sections of one chunk can come from different container factories, each
     * with its own binding over the same frozen biome registry. Identical
     * holders in identical order give identical biome IDs, so they are one binding.
     */
    private static boolean sameHolders(List<Holder<Biome>> a, List<Holder<Biome>> b) {
        var proof = equivalent;
        if (proof != null && (proof.a().get() == a && proof.b().get() == b || proof.a().get() == b && proof.b().get() == a)) return true;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        equivalent = new Equivalent(new java.lang.ref.WeakReference<>(a), new java.lang.ref.WeakReference<>(b));
        return true;
    }
    /** The last binding proven to match its registry; both are fixed after load. */
    private record Validated(java.lang.ref.WeakReference<List<Holder<Biome>>> values, java.lang.ref.WeakReference<Object> registry) {}
    private static volatile Validated validated;
    /** Every chunk shares one biome binding; verify its registry order once, not per chunk. */
    private static boolean matchesRegistry(List<Holder<Biome>> values, net.minecraft.core.Registry<Biome> registry) {
        var proof = validated;
        if (proof != null && proof.values().get() == values && proof.registry().get() == registry) return true;
        if (values.size() != registry.size()) return false;
        for (int id = 0; id < values.size(); id++) {
            if (registry.getId(values.get(id).value()) != id) return false;
        }
        validated = new Validated(new java.lang.ref.WeakReference<>(values), new java.lang.ref.WeakReference<>(registry));
        return true;
    }
    MemorySegment stageSourceOwner() { return owner; }
    public long position() { return pos; }
    public int offsetX() { return (chunkX & 3) * 16; }
    public int offsetZ() { return (chunkZ & 3) * 16; }
    public int compression() { return compression; }
    public int sourceHash() { return sourceHash; }
    public boolean ownsOriginalColumn(it.unimi.dsi.fastutil.longs.LongList list, int x, int z) {
        if (!(list instanceof NativeDhColumnList view)) return false;
        int index = x * 16 + z;
        try {
            int start = offsets.getAtIndex(ValueLayout.JAVA_INT, index), end = offsets.getAtIndex(ValueLayout.JAVA_INT, index + 1);
            return view.isOriginal(this, points.address() + start * 8L, (end - start) * 8L);
        } finally { Reference.reachabilityFence(owner); }
    }
    public int materialCount() { return compatibilityPairs.length; }
    public BlockBiomeWrapperPair material(int id) {
        java.util.Objects.checkIndex(id, compatibilityPairs.length);
        return compatibilityPairs[id];
    }
    public NativeDhColumnList column(int x, int z) {
        java.util.Objects.checkIndex(x, 16); java.util.Objects.checkIndex(z, 16);
        try {
            int c = x * 16 + z;
            int start = offsets.getAtIndex(ValueLayout.JAVA_INT, c), end = offsets.getAtIndex(ValueLayout.JAVA_INT, c + 1);
            return new NativeDhColumnList(this, points.asSlice(start * 8L, (end - start) * 8L));
        } finally { Reference.reachabilityFence(owner); }
    }
    NativeDhMutableColumn sharedColumn(MemorySegment span) {
        try { return NativeDhMutableColumn.shared(0, owner, (span.address() - points.address()) / 8, (int)(span.byteSize() / 8)); }
        finally { Reference.reachabilityFence(this); }
    }

}
