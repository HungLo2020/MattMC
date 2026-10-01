package net.minecraft.world.level.chunk;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Borrow packed sections under normal chunk ownership, scan in Rust, then publish
 * the six existing packed maps. Native code retains no pointers or world state. */
public final class NativeHeightmap {
    private NativeHeightmap() {}
    private static final MethodHandle SECTION = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_heightmap_section",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final Heightmap.Types[] TYPES = Heightmap.Types.values();
    // BlockState's air/solid/fluid properties are initialized immutable state data.
    private static final int[] MASKS = masks();
    private static final boolean GLOBAL_SUPPORTED = java.util.Arrays.stream(MASKS).allMatch(value -> value >= 0);
    private static final MemorySegment GLOBAL_MASKS = globalMasks();
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private static int mask(BlockState state) {
        if (state.is(Blocks.AIR)) return 0; // Original primer's explicit AIR test.
        int result = 0;
        for (var type : TYPES) if (type.isOpaque().test(state)) result |= 1 << type.ordinal();
        return result;
    }

    private static int[] masks() {
        int[] result = new int[Block.BLOCK_STATE_REGISTRY.size()];
        for (int i = 0; i < result.length; i++) {
            var state = Block.BLOCK_STATE_REGISTRY.byId(i);
            // Preserve virtual property behavior for custom state subclasses.
            result[i] = state.getClass() == BlockState.class ? mask(state) : -1;
        }
        return result;
    }

    private static MemorySegment globalMasks() {
        var segment = Arena.ofAuto().allocate(MASKS.length * 4L, 4);
        MemorySegment.copy(MemorySegment.ofArray(MASKS), 0, segment, 0, segment.byteSize());
        return segment.asReadOnly();
    }

    private static final class Scratch {
        final MemorySegment frame = Arena.ofAuto().allocate(288 + 6 * 256 * 8, 8);
        final MemorySegment words = Arena.ofAuto().allocate(2048 * 8, 8);
        final MemorySegment flags = Arena.ofAuto().allocate(256 * 4, 4);
        final Heightmap[] maps = new Heightmap[6];
    }

    private static boolean supported(PalettedContainer<BlockState> container) {
        if (container.getClass() != PalettedContainer.class) return false;
        var data = container.dataForNativeScan();
        var storage = data.storage();
        if (storage.getClass() != SimpleBitStorage.class && storage.getClass() != ZeroBitStorage.class) return false;
        if (storage.getSize() != 4096) return false;
        var palette = data.palette();
        if (palette.getClass() == GlobalPalette.class) {
            if (container.registryForNativeScan() != Block.BLOCK_STATE_REGISTRY || palette.getSize() != MASKS.length) return false;
            return GLOBAL_SUPPORTED;
        }
        if (palette.getClass() != SingleValuePalette.class && palette.getClass() != LinearPalette.class
            && palette.getClass() != HashMapPalette.class) return false;
        if (palette.getSize() > 256) return false;
        for (int i = 0; i < palette.getSize(); i++) if (palette.valueFor(i).getClass() != BlockState.class) return false;
        return true;
    }

    /** Returns false for extensible/custom readers; Heightmap retains their Java path. */
    public static boolean prime(ChunkAccess chunk, Set<Heightmap.Types> types) {
        if (!(types instanceof EnumSet<?>)) return false;
        if (types.isEmpty()) return true;
        if (chunk == null) return false;
        if (chunk.getClass() != ProtoChunk.class && chunk.getClass() != LevelChunk.class) return false;
        if (chunk instanceof LevelChunk level && level.getLevel().isDebug()) return false;
        int minY = chunk.getMinY(), height = chunk.getHeight();
        if (minY % 16 != 0 || height % 16 != 0 || height < 16 || height > 4096 || Math.abs((long)minY) > 1_000_000) return false;
        var sections = chunk.getSections();
        if (sections.length != height / 16) return false;
        for (var section : sections) {
            if (section == null || section.getClass() != LevelChunkSection.class) return false;
        }
        var scratch = SCRATCH.get();
        try {
            return evaluate(chunk, types, sections, minY, height, scratch);
        } finally {
            java.util.Arrays.fill(scratch.maps, null);
        }
    }

    private static boolean evaluate(ChunkAccess chunk, Set<Heightmap.Types> types,
                                    LevelChunkSection[] sections, int minY, int height, Scratch scratch) {
        int selected = 0, heightBits = Mth.ceillog2(height + 1);
        int stride = (256 + 64 / heightBits - 1) / (64 / heightBits);
        for (var type : types) {
            int map = type.ordinal();
            var value = chunk.getOrCreateHeightmapUnprimed(type);
            if (value.getClass() != Heightmap.class || value.getRawData().length != stride) return false;
            scratch.maps[map] = value;
            selected |= 1 << map;
            MemorySegment.copy(MemorySegment.ofArray(value.getRawData()), 0, scratch.frame, 288 + map * stride * 8L, stride * 8L);
        }
        var frame = scratch.frame;
        frame.asSlice(32, 256).fill((byte)selected);
        frame.setAtIndex(ValueLayout.JAVA_INT, 1, heightBits);
        frame.setAtIndex(ValueLayout.JAVA_INT, 3, stride);
        frame.setAtIndex(ValueLayout.JAVA_INT, 5, minY);
        frame.setAtIndex(ValueLayout.JAVA_INT, 6, minY + height);
        frame.setAtIndex(ValueLayout.JAVA_INT, 7, selected);
        for (int sectionIndex = chunk.getHighestFilledSectionIndex(); sectionIndex >= 0; sectionIndex--) {
            var section = sections[sectionIndex];
            if (section.hasOnlyAir()) continue; // Exactly the original chunk read shortcut.
            if (!supported(section.getStates())) return false;
            var data = section.getStates().dataForNativeScan();
            var storage = data.storage();
            var palette = data.palette();
            int count = palette.getSize();
            var flags = scratch.flags;
            if (palette.getClass() == GlobalPalette.class) flags = GLOBAL_MASKS;
            else for (int i = 0; i < count; i++) {
                var state = palette.valueFor(i);
                int id = Block.BLOCK_STATE_REGISTRY.getId(state);
                flags.setAtIndex(ValueLayout.JAVA_INT, i, id >= 0 && id < MASKS.length ? MASKS[id] : mask(state));
            }
            var raw = storage.getRaw();
            MemorySegment.copy(MemorySegment.ofArray(raw), 0, scratch.words, 0, raw.length * 8L);
            frame.setAtIndex(ValueLayout.JAVA_INT, 0, storage.getBits());
            frame.setAtIndex(ValueLayout.JAVA_INT, 2, count);
            frame.setAtIndex(ValueLayout.JAVA_INT, 4, minY + sectionIndex * 16);
            int remaining;
            try {
                remaining = (int)SECTION.invokeExact(scratch.words, raw.length, flags, count, frame, (int)frame.byteSize());
            } catch (RuntimeException | Error e) { throw e; }
            catch (Throwable e) { throw new IllegalStateException("Native heightmap priming failed", e); }
            // Malformed packed IDs take the original path, preserving its exception
            // and partial-write behavior. Scratch maps have not been published yet.
            if (remaining < 0) return false;
            if (remaining == 0) break;
        }
        for (var type : types) {
            int map = type.ordinal();
            MemorySegment.copy(frame, 288 + map * stride * 8L, MemorySegment.ofArray(scratch.maps[map].getRawData()), 0, stride * 8L);
            scratch.maps[map] = null;
        }
        return true;
    }
}
