package net.minecraft.world.level.levelgen;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NativeBlockRegistry;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.Nullable;

/** Rust-owned block storage of one {@link ProtoChunk} while a generation stage
 * (SURFACE, CARVERS) runs on it: the chunk's sections and its two
 * world-generation heightmaps move to Rust, every write is
 * {@code ProtoChunk.setBlockState} before light initialization, and the
 * modified sections, both heightmaps and the stage's post-processing marks
 * install once at the end. Stages borrow the storage's handle. */
final class NativeProtoChunk implements AutoCloseable {
    private static final MethodHandle CREATE = bind("create", true, FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle HEIGHT = bind("height", false, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle GET = bind("get", false, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle SET = bind("set", false, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle SECTION = bind("section", true, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle HEIGHTMAPS = bind("heightmaps", true, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle POST_PROCESS = bind("post_process", true, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = bind("release", false, FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final EnumSet<Heightmap.Types> GENERATION = EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG);

    private static MethodHandle bind(String suffix, boolean critical, FunctionDescriptor descriptor) {
        return critical
            ? NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_proto_chunk_" + suffix, descriptor, Linker.Option.critical(true))
            : NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_proto_chunk_" + suffix, descriptor);
    }

    final long handle;
    private final ChunkAccess chunk;

    private NativeProtoChunk(long handle, ChunkAccess chunk) {
        this.handle = handle;
        this.chunk = chunk;
    }

    /** Moves a chunk's block storage to Rust; null keeps setBlockState writes.
     * Requires a plain ProtoChunk before light initialization whose only
     * heightmaps to update are the primed world-generation pair, and modelled sections. */
    @Nullable
    static NativeProtoChunk create(ChunkAccess chunk) {
        // Rust reads per-state flags from its block registry.
        if (!NativeBlockRegistry.ready() || chunk.getClass() != ProtoChunk.class || ((ProtoChunk)chunk).getPersistedStatus().isOrAfter(ChunkStatus.INITIALIZE_LIGHT)
            || !((ProtoChunk)chunk).getPersistedStatus().heightmapsAfter().equals(GENERATION)
            || !chunk.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE_WG) || !chunk.hasPrimedHeightmap(Heightmap.Types.OCEAN_FLOOR_WG)) {
            return null;
        }
        LevelChunkSection[] sections = chunk.getSections();
        int minY = chunk.getMinY(), height = chunk.getHeight();
        if (sections.length == 0 || sections.length * 16 != height) return null;
        long[] surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG).getRawData();
        long[] floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG).getRawData();
        if (surface.length != floor.length) return null;
        LevelChunkSection.GeneratedSection[] states = new LevelChunkSection.GeneratedSection[sections.length];
        int intCount = 4 + 7 * sections.length, longCount = 2 * surface.length;
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            var state = section == null || section.getClass() != LevelChunkSection.class ? null : section.exportGenerated();
            if (state == null) return null;
            for (int id : state.palette()) if (id < 0) return null;
            states[index] = state;
            intCount += state.palette().length;
            longCount += state.raw().length;
        }
        // Exact arrays, filled once: [minY, height, count, global bits, headers, palettes].
        int[] intArray = new int[intCount];
        long[] longArray = new long[longCount];
        intArray[0] = minY;
        intArray[1] = height;
        intArray[2] = sections.length;
        intArray[3] = sections[0].generatedGlobalPaletteBits();
        int at = 4, palettes = 4 + 7 * sections.length, raw = 0;
        for (var state : states) {
            intArray[at++] = state.kind();
            intArray[at++] = state.bits();
            intArray[at++] = state.palette().length;
            intArray[at++] = state.raw().length;
            intArray[at++] = state.nonEmpty();
            intArray[at++] = state.ticking();
            intArray[at++] = state.fluid();
            System.arraycopy(state.palette(), 0, intArray, palettes, state.palette().length);
            palettes += state.palette().length;
            System.arraycopy(state.raw(), 0, longArray, raw, state.raw().length);
            raw += state.raw().length;
        }
        System.arraycopy(surface, 0, longArray, raw, surface.length);
        System.arraycopy(floor, 0, longArray, raw + surface.length, floor.length);
        long handle;
        try {
            handle = (long)CREATE.invokeExact(MemorySegment.ofArray(intArray), intArray.length, MemorySegment.ofArray(longArray), longArray.length,
                surface.length);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot create native chunk storage", error);
        }
        return handle == 0 ? null : new NativeProtoChunk(handle, chunk);
    }

    ChunkAccess chunk() {
        return this.chunk;
    }

    /** {@code getHeight(WORLD_SURFACE_WG, x, z)} on the Rust heightmap. */
    int height(int x, int z) {
        try {
            return (int)HEIGHT.invokeExact(this.handle, x, z);
        } catch (Throwable error) {
            throw new IllegalStateException("Native chunk height failed", error);
        }
    }

    /** {@code ProtoChunk.getBlockState} on the Rust storage. */
    BlockState get(BlockPos.MutableBlockPos position, int y) {
        int id;
        try {
            id = (int)GET.invokeExact(this.handle, position.getX(), y, position.getZ());
        } catch (Throwable error) {
            throw new IllegalStateException("Native chunk read failed", error);
        }
        // ProtoChunk reads AIR in an only-air section and VOID_AIR outside the sections.
        return id >= 0 ? Block.stateById(id) : id == -2 ? Blocks.AIR.defaultBlockState() : this.chunk.getBlockState(position.setY(y));
    }

    /** {@code ProtoChunk.setBlockState}, then a post-processing mark for a fluid
     * inside the build height when {@code markFluids} (buildSurface's column). */
    void set(int x, int y, int z, BlockState state, boolean markFluids) {
        int status;
        try {
            status = (int)SET.invokeExact(this.handle, x, y, z, Block.getId(state), markFluids ? 1 : 0);
        } catch (Throwable error) {
            throw new IllegalStateException("Native chunk write failed", error);
        }
        if (status != 0) throw new IllegalStateException("Native chunk write rejected: " + status);
    }

    /** buildSurface's block column over this storage, at the position's X and Z. */
    BlockColumn column(BlockPos.MutableBlockPos position) {
        return new BlockColumn() {
            @Override
            public BlockState getBlock(int y) {
                return NativeProtoChunk.this.get(position, y);
            }

            @Override
            public void setBlock(int y, BlockState state) {
                NativeProtoChunk.this.set(position.getX(), y, position.getZ(), state, true);
            }

            public String toString() {
                return "NativeBlockColumn " + NativeProtoChunk.this.chunk.getPos();
            }
        };
    }

    /** Installs the world-generation heightmaps' current Rust values into the
     * Java chunk, for Java code that reads them mid-stage. */
    void syncHeightmaps() {
        try {
            Heightmap surface = this.chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
            Heightmap floor = this.chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
            long[] a = new long[surface.getRawData().length], b = new long[a.length];
            int length = (int)HEIGHTMAPS.invokeExact(this.handle, MemorySegment.ofArray(a), MemorySegment.ofArray(b), a.length);
            if (length != a.length) throw new IllegalStateException("Native chunk heightmap size " + length);
            surface.setRawData(this.chunk, Heightmap.Types.WORLD_SURFACE_WG, a);
            floor.setRawData(this.chunk, Heightmap.Types.OCEAN_FLOOR_WG, b);
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Native chunk heightmap failed", error);
        }
    }

    /** Installs every modified section, both heightmaps and the post-processing marks. */
    void install() {
        try {
            int[] info = new int[6];
            int[] palette = new int[4096];
            long[] raw = new long[4096];
            LevelChunkSection[] sections = this.chunk.getSections();
            for (int index = 0; index < sections.length; index++) {
                int written = (int)SECTION.invokeExact(this.handle, index, MemorySegment.ofArray(info), MemorySegment.ofArray(palette), palette.length,
                    MemorySegment.ofArray(raw), raw.length);
                if (written == 0) continue;
                if (written != 1) throw new IllegalStateException("Native chunk section overflow");
                List<BlockState> states = new ArrayList<>(info[1]);
                for (int entry = 0; entry < info[1]; entry++) states.add(Block.stateById(palette[entry]));
                sections[index].installGenerated(info[0], states, Arrays.copyOf(raw, info[2]), info[3], info[4], info[5]);
            }
            this.syncHeightmaps();
            int[] positions = new int[3 * 256];
            int count = (int)POST_PROCESS.invokeExact(this.handle, MemorySegment.ofArray(positions), 256);
            if (count < 0) {
                positions = new int[3 * -count];
                count = (int)POST_PROCESS.invokeExact(this.handle, MemorySegment.ofArray(positions), -count);
            }
            BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
            for (int index = 0; index < count; index++) {
                this.chunk.markPosForPostprocessing(position.set(positions[index * 3], positions[index * 3 + 1], positions[index * 3 + 2]));
            }
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Native chunk install failed", error);
        }
    }

    @Override
    public void close() {
        try {
            RELEASE.invokeExact(this.handle);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot release native chunk storage", error);
        }
    }
}
