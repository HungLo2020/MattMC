package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.NativeLightBlocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Rust propagation for {@link LightEngine#runLightUpdates()}: the decrease
 * then increase queues of a {@link BlockLightEngine} or {@link SkyLightEngine}
 * run in {@code world/level/lighting/propagation/}. Sections reach Rust
 * through a callback on first use; written layers install once at the end
 * with the original copy-on-write. Input Rust rejects leaves storage and
 * queues untouched for the Java propagation. */
final class NativeLightPropagation {
    private static final MethodHandle TABLES_CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_tables_create",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_engine_create",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_engine_release",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle RUN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_run",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle RESULTS = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_results",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    private static final MethodHandle SKY_SECTION = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_sky_section",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS), Linker.Option.critical(true));
    private static final ThreadLocal<SkySeed> SEEDS = ThreadLocal.withInitial(SkySeed::new);
    private static final MemorySegment SECTION;
    private static final Cleaner CLEANER = Cleaner.create();
    // The pass running on this thread, for the section callback.
    private static final ThreadLocal<NativeLightPropagation> CURRENT = new ThreadLocal<>();
    // Tests compare both routes in one JVM; production reads the property once.
    private static volatile boolean enabled = !Boolean.getBoolean("mattmc.lighting.javaPropagation");
    // Passes Rust completed and passes it handed back, so tests can prove the route.
    static final AtomicLong PASSES = new AtomicLong();
    static final AtomicLong REJECTED = new AtomicLong();
    // Chunks whose sky sources Rust seeded.
    static final AtomicLong SEEDED = new AtomicLong();

    static {
        try {
            MethodHandle section = MethodHandles.lookup().findStatic(NativeLightPropagation.class, "section",
                MethodType.methodType(int.class, int.class, long.class, MemorySegment.class));
            SECTION = Linker.nativeLinker().upcallStub(section, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG, ValueLayout.ADDRESS), Arena.global());
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    /** Light properties of every registered state, built on first use. */
    private static final class Tables {
        static final char[] TYPES;
        static final long HANDLE;

        static {
            char[] types = new char[Block.BLOCK_STATE_REGISTRY.size()];
            long handle = 0;
            try {
                handle = build(types);
            } catch (RuntimeException error) {
                handle = 0;
            }
            TYPES = types;
            HANDLE = handle;
        }

        private static int face(VoxelShape shape, IdentityHashMap<VoxelShape, Integer> seen, HashMap<List<AABB>, Integer> ids, ArrayList<VoxelShape> shapes) {
            Integer known = seen.get(shape);
            if (known != null) return known;
            // Exact box lists; no coordinate quantization or shape approximation.
            var key = List.copyOf(shape.toAabbs());
            Integer id = ids.get(key);
            if (id == null) {
                id = shapes.size();
                ids.put(key, id);
                shapes.add(shape);
            }
            seen.put(shape, id);
            return id;
        }

        private static long build(char[] stateTypes) throws RuntimeException {
            var shapes = new ArrayList<VoxelShape>();
            var ids = new HashMap<List<AABB>, Integer>();
            var seen = new IdentityHashMap<VoxelShape, Integer>();
            face(Shapes.empty(), seen, ids, shapes); // Empty shapes are face 0.
            var typeIds = new HashMap<List<Integer>, Integer>();
            var types = new ArrayList<List<Integer>>();
            Direction[] directions = Direction.values();
            for (int id = 0; id < stateTypes.length; id++) {
                BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
                if (state == null || state.getClass() != BlockState.class) {
                    stateTypes[id] = NativeLightBlocks.UNSUPPORTED;
                    continue;
                }
                var type = new ArrayList<Integer>(9);
                // LightEngine.getOpacity, getLightEmission, isEmptyShape, then getOcclusionShape per direction.
                type.add(Math.max(1, state.getLightBlock()));
                type.add(state.getLightEmission());
                type.add(LightEngine.isEmptyShape(state) ? 1 : 0);
                for (Direction direction : directions) {
                    type.add(face(LightEngine.getOcclusionShape(state, direction), seen, ids, shapes));
                }
                Integer typeId = typeIds.get(type);
                if (typeId == null) {
                    typeId = types.size();
                    typeIds.put(type, typeId);
                    types.add(type);
                }
                if (typeId >= NativeLightBlocks.UNSUPPORTED) return 0;
                stateTypes[id] = (char)(int)typeId;
            }
            int faces = shapes.size();
            if (faces > 4096) return 0;
            byte[] occludes = new byte[faces * faces];
            // The original shape operation supplies the immutable truth table.
            for (int from = 0; from < faces; from++) {
                for (int to = 0; to < faces; to++) {
                    occludes[from * faces + to] = (byte)(Shapes.faceShapeOccludes(shapes.get(from), shapes.get(to)) ? 1 : 0);
                }
            }
            char[] flat = new char[types.size() * 9];
            for (int t = 0; t < types.size(); t++) {
                for (int k = 0; k < 9; k++) flat[t * 9 + k] = (char)(int)types.get(t).get(k);
            }
            int air = stateTypes[Block.BLOCK_STATE_REGISTRY.getId(Blocks.AIR.defaultBlockState())];
            try (Arena arena = Arena.ofConfined()) {
                return (long)TABLES_CREATE.invokeExact(arena.allocateFrom(ValueLayout.JAVA_CHAR, stateTypes), stateTypes.length,
                    arena.allocateFrom(ValueLayout.JAVA_CHAR, flat), types.size(), arena.allocateFrom(ValueLayout.JAVA_BYTE, occludes), faces, air);
            } catch (RuntimeException | Error error) {
                throw error;
            } catch (Throwable error) {
                throw new IllegalStateException("Native light tables failed", error);
            }
        }
    }

    /** {@link SkyLightEngine#propagateLightSources}'s per-section loop in Rust,
     * for one chunk: each column's lowest source and its four neighbours'. */
    static final class SkySeed {
        private final int[] columns = new int[5 * 256];
        private final byte[] scratch = new byte[2048];
        private final long[] entries = new long[8192];
        private final int[] out = new int[3];

        /** One section: writes the layer like the original's {@code set} calls
         * and enqueues the same increases in the same order. Returns whether a
         * source lies below the section (the original's loop condition). */
        boolean section(SkyLightEngine engine, DataLayer layer, int bottom, int minX, int minZ) {
            byte[] data = layer.dataForNativeLight();
            int status;
            try {
                status = (int)SKY_SECTION.invokeExact(MemorySegment.ofArray(data == null ? this.scratch : data),
                    data == null ? layer.defaultValueForNativeLight() : -1, MemorySegment.ofArray(this.columns), bottom, minX, minZ,
                    MemorySegment.ofArray(this.entries), MemorySegment.ofArray(this.out));
            } catch (RuntimeException | Error error) {
                throw error;
            } catch (Throwable error) {
                throw new IllegalStateException("Native sky light sources failed", error);
            }
            if (status != 0) throw new IllegalStateException("Native sky light sources rejected: " + status);
            // A lazy layer allocates on its first write, as DataLayer.set does.
            if (data == null && this.out[1] != 0) System.arraycopy(this.scratch, 0, layer.getData(), 0, 2048);
            for (int i = 0; i < this.out[0]; i++) engine.enqueueIncrease(this.entries[2 * i], this.entries[2 * i + 1]);
            return this.out[2] != 0;
        }
    }

    /** This thread's seed for a chunk's sources (center, north, south, west,
     * east), or null to keep the Java loop. */
    @Nullable
    static SkySeed skySeed(ChunkSkyLightSources center, ChunkSkyLightSources north, ChunkSkyLightSources south, ChunkSkyLightSources west,
                           ChunkSkyLightSources east) {
        if (!enabled) return null;
        SkySeed seed = SEEDS.get();
        int[] columns = seed.columns;
        for (int r = 0; r < 16; r++) {
            for (int s = 0; s < 16; s++) {
                int i = r * 16 + s;
                columns[i] = center.getLowestSourceY(s, r);
                columns[256 + i] = r == 0 ? north.getLowestSourceY(s, 15) : center.getLowestSourceY(s, r - 1);
                columns[512 + i] = r == 15 ? south.getLowestSourceY(s, 0) : center.getLowestSourceY(s, r + 1);
                columns[768 + i] = s == 0 ? west.getLowestSourceY(15, r) : center.getLowestSourceY(s - 1, r);
                columns[1024 + i] = s == 15 ? east.getLowestSourceY(0, r) : center.getLowestSourceY(s + 1, r);
            }
        }
        SEEDED.incrementAndGet();
        return seed;
    }

    private final long handle;
    private final boolean sky;
    private final LightEngine<?, ?> engine;
    private final Arena arena = Arena.ofAuto();
    private final MemorySegment out = arena.allocate(3 * 8L, 8);
    private MemorySegment decreases = arena.allocate(512 * 8L, 8);
    private MemorySegment increases = arena.allocate(512 * 8L, 8);

    private NativeLightPropagation(long handle, boolean sky, LightEngine<?, ?> engine) {
        this.handle = handle;
        this.sky = sky;
        this.engine = engine;
        CLEANER.register(this, () -> release(handle));
    }

    static void setEnabled(boolean value) {
        enabled = value;
    }

    /** Null keeps the Java propagation: engines and storages other than the
     * two vanilla classes may override what Rust models. */
    @Nullable
    static NativeLightPropagation create(LightEngine<?, ?> engine) {
        boolean sky = engine.getClass() == SkyLightEngine.class;
        if (!sky && engine.getClass() != BlockLightEngine.class) return null;
        long handle;
        try {
            handle = (long)CREATE.invokeExact(sky ? 1 : 0);
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Native light engine failed", error);
        }
        return new NativeLightPropagation(handle, sky, engine);
    }

    private static void release(long handle) {
        try {
            RELEASE.invokeExact(handle);
        } catch (Throwable error) {
            throw new IllegalStateException("Native light engine release failed", error);
        }
    }

    private MemorySegment drain(LongArrayFIFOQueue queue, MemorySegment into) {
        int size = queue.size();
        if (into.byteSize() < size * 8L) into = this.arena.allocate(Math.max(size, (int)(into.byteSize() / 4)) * 8L, 8);
        for (int i = 0; i < size; i++) into.setAtIndex(ValueLayout.JAVA_LONG, i, queue.dequeueLong());
        return into;
    }

    private static void refill(LongArrayFIFOQueue queue, MemorySegment from, int size) {
        for (int i = 0; i < size; i++) queue.enqueue(from.getAtIndex(ValueLayout.JAVA_LONG, i));
    }

    /** {@code propagateDecreases() + propagateIncreases()}, or -1 with the
     * queues and storage unchanged for the Java propagation. */
    int run(LayerLightSectionStorage<?> storage, LongArrayFIFOQueue decreaseQueue, LongArrayFIFOQueue increaseQueue) {
        if (!enabled || Tables.HANDLE == 0) return -1;
        if (decreaseQueue.isEmpty() && increaseQueue.isEmpty()) return 0;
        if (storage.getClass() != (this.sky ? SkyLightSectionStorage.class : BlockLightSectionStorage.class)) return -1;
        int decreaseCount = decreaseQueue.size(), increaseCount = increaseQueue.size();
        this.decreases = drain(decreaseQueue, this.decreases);
        this.increases = drain(increaseQueue, this.increases);
        int lowest = this.sky ? ((SkyLightSectionStorage)storage).getBottomSectionY() : Integer.MAX_VALUE;
        int status;
        CURRENT.set(this);
        try {
            status = (int)RUN.invokeExact(this.handle, Tables.HANDLE, this.decreases, decreaseCount, this.increases, increaseCount, lowest,
                SECTION, this.out);
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Native light propagation failed", error);
        } finally {
            CURRENT.remove();
        }
        if (status != 0) {
            if (status == -1) throw new IllegalStateException("Native light propagation rejected its input");
            // Unsupported input or a failed callback: Java reruns the pass from the same queues.
            refill(decreaseQueue, this.decreases, decreaseCount);
            refill(increaseQueue, this.increases, increaseCount);
            REJECTED.incrementAndGet();
            return -1;
        }
        long processed = this.out.getAtIndex(ValueLayout.JAVA_LONG, 0);
        int written = (int)this.out.getAtIndex(ValueLayout.JAVA_LONG, 1);
        int affected = (int)this.out.getAtIndex(ValueLayout.JAVA_LONG, 2);
        long[] keys = new long[written];
        byte[] layers = new byte[written * 2048];
        long[] sections = new long[affected];
        try {
            status = (int)RESULTS.invokeExact(this.handle, MemorySegment.ofArray(keys), MemorySegment.ofArray(layers), written,
                MemorySegment.ofArray(sections), affected);
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Native light results failed", error);
        }
        if (status != 0) throw new IllegalStateException("Native light results rejected: " + status);
        for (int i = 0; i < written; i++) {
            long key = keys[i];
            // setStoredLevel's copy-on-write: the first write of a pass copies the layer.
            DataLayer layer = storage.changedSections.add(key) ? storage.updatingSectionData.copyDataLayer(key) : storage.getDataLayer(key, true);
            System.arraycopy(layers, i * 2048, layer.getData(), 0, 2048);
        }
        for (long section : sections) storage.sectionsAffectedByLightUpdates.add(section);
        PASSES.incrementAndGet();
        return (int)processed;
    }

    /** Rust's section callback. Mode 0: the stored layer of {@code section}
     * (0 absent, 1 stored); mode 1: its block states (0). Negative values
     * reject (-1) or report a failure (-2); the Java rerun meets it again. */
    private static int section(int mode, long section, MemorySegment buffer) {
        NativeLightPropagation current = CURRENT.get();
        if (current == null) return -2;
        try {
            buffer = buffer.reinterpret(NativeLightBlocks.BUFFER);
            LayerLightSectionStorage<?> storage = current.engine.storage;
            if (mode == 0) {
                DataLayer layer = storage.getDataLayer(section, true);
                return layer == null ? 0 : NativeLightBlocks.layer(layer, storage.lightOnInSection(section), buffer);
            }
            int x = SectionPos.x(section), z = SectionPos.z(section);
            return NativeLightBlocks.blocks(current.engine.chunkSource.getChunkForLighting(x, z), x, SectionPos.y(section), z, Tables.TYPES, buffer);
        } catch (Throwable error) {
            return -2;
        }
    }
}
