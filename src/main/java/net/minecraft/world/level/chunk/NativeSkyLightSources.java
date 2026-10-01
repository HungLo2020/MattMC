package net.minecraft.world.level.chunk;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Packed section bridge for server-side skylight sources. */
public final class NativeSkyLightSources {
    private static final int PENDING_OFFSET = 32;
    private static final int UPPER_FACES_OFFSET = 288;
    private static final int HEIGHTS_OFFSET = 1312;
    private static final MethodHandle SECTION = NativeLibraryLoader.downcallHandle(
        "mattmc_rust", "mattmc_skylight_sources_section",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle EMPTY = NativeLibraryLoader.downcallHandle(
        "mattmc_rust", "mattmc_skylight_sources_empty",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT), Linker.Option.critical(true));

    // BlockState light properties and occlusion faces are immutable after bootstrap.
    private record Tables(int[] descriptors, int faces, MemorySegment flags, MemorySegment edges) {}
    private static final Tables TABLES = tables();
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private NativeSkyLightSources() {}

    private static int face(VoxelShape shape, HashMap<List<AABB>, Integer> ids, ArrayList<VoxelShape> shapes) {
        // Exact box lists; no coordinate quantization or shape approximation.
        var key = List.copyOf(shape.toAabbs());
        var id = ids.get(key);
        if (id != null) return id;
        int next = shapes.size();
        ids.put(key, next);
        shapes.add(shape);
        return next;
    }

    private static Tables tables() {
        var shapes = new ArrayList<VoxelShape>();
        var ids = new HashMap<List<AABB>, Integer>();
        face(Shapes.empty(), ids, shapes); // AIR's face ID is zero.
        int[] descriptors = new int[Block.BLOCK_STATE_REGISTRY.size()];
        for (int i = 0; i < descriptors.length; i++) {
            var state = Block.BLOCK_STATE_REGISTRY.byId(i);
            if (state.getClass() != BlockState.class) {
                descriptors[i] = -1;
                continue;
            }
            int up = face(LightEngine.getOcclusionShape(state, Direction.UP), ids, shapes);
            int down = face(LightEngine.getOcclusionShape(state, Direction.DOWN), ids, shapes);
            if (shapes.size() > 512) {
                return new Tables(new int[0], 0, MemorySegment.NULL, MemorySegment.NULL);
            }
            descriptors[i] = (state.getLightBlock() != 0 ? 1 : 0) | (up << 1) | (down << 16);
        }
        int count = shapes.size();
        byte[] edges = new byte[count * count];
        // Original Java shape operation supplies the immutable truth table.
        for (int down = 0; down < count; down++) {
            for (int up = 0; up < count; up++) {
                edges[down * count + up] = (byte)(Shapes.faceShapeOccludes(shapes.get(down), shapes.get(up)) ? 1 : 0);
            }
        }
        var arena = Arena.ofAuto();
        var flags = arena.allocate(descriptors.length * 4L, 4);
        var matrix = arena.allocate(edges.length, 1);
        MemorySegment.copy(MemorySegment.ofArray(descriptors), 0, flags, 0, flags.byteSize());
        MemorySegment.copy(MemorySegment.ofArray(edges), 0, matrix, 0, matrix.byteSize());
        return new Tables(descriptors, count, flags.asReadOnly(), matrix.asReadOnly());
    }

    private static final class Scratch {
        final MemorySegment frame = Arena.ofAuto().allocate(HEIGHTS_OFFSET + 256 * 8, 8);
        final MemorySegment words = Arena.ofAuto().allocate(2048 * 8, 8);
        final MemorySegment flags = Arena.ofAuto().allocate(256 * 4, 4);
    }

    /** False leaves the destination untouched so the caller can use its original reader. */
    public static boolean fill(ChunkAccess chunk, int minSourceY, BitStorage destination) {
        if (chunk == null || TABLES.faces == 0 || destination.getClass() != SimpleBitStorage.class
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
        // exclusive max Y, face count, descriptor count.
        frame.setAtIndex(ValueLayout.JAVA_INT, 1, destination.getBits());
        frame.setAtIndex(ValueLayout.JAVA_INT, 2, stride);
        frame.setAtIndex(ValueLayout.JAVA_INT, 4, minSourceY);
        frame.setAtIndex(ValueLayout.JAVA_INT, 5, minY + height);
        frame.setAtIndex(ValueLayout.JAVA_INT, 6, TABLES.faces);
        for (int sectionIndex = highest; sectionIndex >= 0; sectionIndex--) {
            var section = sections[sectionIndex];
            int bits = -1, len = 0, count = 1;
            MemorySegment flags = scratch.flags;
            if (!section.hasOnlyAir()) {
                var container = section.getStates();
                if (container.getClass() != PalettedContainer.class) return false;
                var data = container.dataForNativeScan();
                var storage = data.storage();
                var palette = data.palette();
                if (storage.getClass() != SimpleBitStorage.class && storage.getClass() != ZeroBitStorage.class) return false;
                if (storage.getSize() != 4096) return false;
                count = palette.getSize();
                bits = storage.getBits();
                if (palette.getClass() == GlobalPalette.class) {
                    if (container.registryForNativeScan() != Block.BLOCK_STATE_REGISTRY
                        || count != TABLES.descriptors.length) return false;
                    // Unsupported subclasses are rejected if encountered by Rust.
                    flags = TABLES.flags;
                } else {
                    if (palette.getClass() != SingleValuePalette.class && palette.getClass() != LinearPalette.class
                        && palette.getClass() != HashMapPalette.class) return false;
                    if (count > 256) return false;
                    for (int i = 0; i < count; i++) {
                        var state = palette.valueFor(i);
                        if (state == null || state.getClass() != BlockState.class) return false;
                        int id = Block.BLOCK_STATE_REGISTRY.getId(state);
                        if (id < 0 || id >= TABLES.descriptors.length || TABLES.descriptors[id] < 0) return false;
                        flags.setAtIndex(ValueLayout.JAVA_INT, i, TABLES.descriptors[id]);
                    }
                }
                var raw = storage.getRaw();
                len = raw.length;
                MemorySegment.copy(MemorySegment.ofArray(raw), 0, scratch.words, 0, len * 8L);
            }
            frame.setAtIndex(ValueLayout.JAVA_INT, 0, bits);
            frame.setAtIndex(ValueLayout.JAVA_INT, 3, minY + sectionIndex * 16);
            frame.setAtIndex(ValueLayout.JAVA_INT, 7, count);
            int remaining = scan(scratch.words, len, flags, count, frame);
            if (remaining < 0) return false;
            if (remaining == 0) break;
        }
        MemorySegment.copy(frame, HEIGHTS_OFFSET, MemorySegment.ofArray(destination.getRaw()), 0, stride * 8L);
        return true;
    }

    private static int scan(MemorySegment words, int length, MemorySegment flags, int count, MemorySegment frame) {
        try {
            return (int)SECTION.invokeExact(words, length, flags, count, TABLES.edges,
                (int)TABLES.edges.byteSize(), frame, (int)frame.byteSize());
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
