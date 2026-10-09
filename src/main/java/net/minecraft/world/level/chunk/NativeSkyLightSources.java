package net.minecraft.world.level.chunk;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.NativeBlockRegistry;
import net.minecraft.world.level.block.state.BlockState;

/** Packed section bridge for server-side skylight sources. */
public final class NativeSkyLightSources {
    private static final int PENDING_OFFSET = 32;
    private static final int UPPER_FACES_OFFSET = 288;
    private static final int HEIGHTS_OFFSET = 1312;
    private static final MethodHandle SECTION = NativeLibraryLoader.downcallHandle(
        "mattmc_rust", "mattmc_skylight_sources_section",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle EMPTY = NativeLibraryLoader.downcallHandle(
        "mattmc_rust", "mattmc_skylight_sources_empty",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT), Linker.Option.critical(true));

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private NativeSkyLightSources() {}

    private static final class Scratch {
        final MemorySegment frame = Arena.ofAuto().allocate(HEIGHTS_OFFSET + 256 * 8, 8);
        final MemorySegment words = Arena.ofAuto().allocate(2048 * 8, 8);
        final MemorySegment exportHeader = Arena.ofAuto().allocate(16, 4);
        final MemorySegment ids = Arena.ofAuto().allocate(257 * 4, 4);
    }

    /** False leaves the destination untouched so the caller can use its original reader. */
    public static boolean fill(ChunkAccess chunk, int minSourceY, BitStorage destination) {
        // Rust derives state descriptors and face edges from its block registry.
        if (chunk == null || !NativeBlockRegistry.ready() || destination.getClass() != SimpleBitStorage.class
            || destination.getSize() != 256) return false;
        if (chunk.getClass() != ProtoChunk.class && chunk.getClass() != LevelChunk.class) return false;
        int minY = chunk.getMinY(), height = chunk.getHeight();
        if (minY % 16 != 0 || height % 16 != 0 || height < 16 || height > 4096
            || Math.abs((long)minY) > 1_000_000 || minSourceY != minY - 1
            || destination.getBits() != Mth.ceillog2(height + 3)) return false;
        var sections = chunk.getSections();
        if (sections.length != height / 16) return false;
        for (var section : sections) {
            if (section == null || section.getClass() != LevelChunkSection.class) return false;
        }
        int highest = chunk.getHighestFilledSectionIndex();
        if (highest == -1) return clearEmpty(destination);

        var scratch = SCRATCH.get();
        var frame = scratch.frame;
        int stride = destination.getRaw().length;
        MemorySegment.copy(MemorySegment.ofArray(destination.getRaw()), 0, frame, HEIGHTS_OFFSET, stride * 8L);
        frame.asSlice(PENDING_OFFSET, 256).fill((byte)1);
        frame.asSlice(UPPER_FACES_OFFSET, 1024).fill((byte)0);
        // Header: storage bits, height bits, stride, section base, sentinel Y,
        // exclusive max Y, face count (Rust's), palette size.
        frame.setAtIndex(ValueLayout.JAVA_INT, 1, destination.getBits());
        frame.setAtIndex(ValueLayout.JAVA_INT, 2, stride);
        frame.setAtIndex(ValueLayout.JAVA_INT, 4, minSourceY);
        frame.setAtIndex(ValueLayout.JAVA_INT, 5, minY + height);
        for (int sectionIndex = highest; sectionIndex >= 0; sectionIndex--) {
            var section = sections[sectionIndex];
            int bits = -1, len = 0, count = 1;
            MemorySegment ids = scratch.ids;
            if (!section.hasOnlyAir()) {
                var container = section.getStates();
                if (container.getClass() != PalettedContainer.class) return false;
                var live = container.nativeLiveBlocks();
                if (live != null) {
                    len = live.export(scratch.words, scratch.ids, scratch.exportHeader);
                    bits = scratch.exportHeader.getAtIndex(ValueLayout.JAVA_INT, 0);
                    boolean global = scratch.exportHeader.getAtIndex(ValueLayout.JAVA_INT, 2) != 0;
                    count = global ? Block.BLOCK_STATE_REGISTRY.size() : scratch.exportHeader.getAtIndex(ValueLayout.JAVA_INT, 3);
                    ids = global ? MemorySegment.NULL : scratch.ids;
                } else {
                    var data = container.dataForNativeScan();
                    var storage = data.storage();
                    var palette = data.palette();
                    if (storage.getClass() != SimpleBitStorage.class && storage.getClass() != ZeroBitStorage.class) return false;
                    if (storage.getSize() != 4096) return false;
                    count = palette.getSize();
                    bits = storage.getBits();
                    if (palette.getClass() == GlobalPalette.class) {
                        if (container.registryForNativeScan() != Block.BLOCK_STATE_REGISTRY
                            || count != Block.BLOCK_STATE_REGISTRY.size()) return false;
                        // Palette ids are state ids; Rust declines custom subclasses.
                        ids = MemorySegment.NULL;
                    } else {
                        if (palette.getClass() != SingleValuePalette.class && palette.getClass() != LinearPalette.class
                            && palette.getClass() != HashMapPalette.class) return false;
                        if (count > 256) return false;
                        for (int i = 0; i < count; i++) {
                            var state = palette.valueFor(i);
                            if (state == null || state.getClass() != BlockState.class) return false;
                            int id = Block.BLOCK_STATE_REGISTRY.getId(state);
                            if (id < 0) return false;
                            ids.setAtIndex(ValueLayout.JAVA_INT, i, id);
                        }
                    }
                    var raw = storage.getRaw();
                    len = raw.length;
                    MemorySegment.copy(MemorySegment.ofArray(raw), 0, scratch.words, 0, len * 8L);
                }
            }
            frame.setAtIndex(ValueLayout.JAVA_INT, 0, bits);
            frame.setAtIndex(ValueLayout.JAVA_INT, 3, minY + sectionIndex * 16);
            frame.setAtIndex(ValueLayout.JAVA_INT, 7, count);
            int remaining = scan(scratch.words, len, ids, count, frame);
            if (remaining < 0) return false;
            if (remaining == 0) break;
        }
        MemorySegment.copy(frame, HEIGHTS_OFFSET, MemorySegment.ofArray(destination.getRaw()), 0, stride * 8L);
        return true;
    }

    private static int scan(MemorySegment words, int length, MemorySegment ids, int count, MemorySegment frame) {
        try {
            return (int)SECTION.invokeExact(words, length, ids, count, frame, (int)frame.byteSize());
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Native skylight source reconstruction failed", e);
        }
    }

    private static boolean clearEmpty(BitStorage destination) {
        var raw = destination.getRaw();
        // At most 128 words: bounded, no native allocations, blocking or callbacks.
        try {
            return (int)EMPTY.invokeExact(MemorySegment.ofArray(raw), raw.length, destination.getBits()) == 0;
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Native empty skylight source clear failed", e);
        }
    }
}
