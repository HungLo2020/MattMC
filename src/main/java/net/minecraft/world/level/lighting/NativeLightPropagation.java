package net.minecraft.world.level.lighting;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.SectionPos;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.NativeBlockRegistry;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.NativeLightBlocks;
import org.jetbrains.annotations.Nullable;

/** Rust propagation for {@link LightEngine#runLightUpdates()}: the decrease
 * then increase queues of a {@link BlockLightEngine} or {@link SkyLightEngine}
 * run in {@code world/level/lighting/propagation/}. Sections reach Rust
 * through a callback on first use; written layers install once at the end
 * with the original copy-on-write. Input Rust rejects leaves storage and
 * queues untouched for the Java propagation. */
final class NativeLightPropagation {
    private static final MethodHandle TABLES_CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_tables_create",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG));
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_engine_create",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_engine_release",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle RUN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_run_owned",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle RUN_CONNECTED = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_run_connected",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle DIRECT_LAYER_READS = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_engine_direct_layer_reads",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final MethodHandle RESULTS = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_result_keys",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    private static final MethodHandle COPY_RESULT = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_result_copy",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
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
    static final AtomicLong BLOCK_SEEDED = new AtomicLong();

    static boolean seedBlockSources(net.minecraft.world.level.chunk.LightChunk chunk, NativeLightQueues queues) {
        if (!enabled || !net.minecraft.world.level.chunk.NativeLightSources.seed(chunk, queues)) return false;
        BLOCK_SEEDED.incrementAndGet();
        return true;
    }

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

    /** Rust's light tables, derived from its block registry on first use; 0 without one. */
    private static final class Tables {
        static final long HANDLE = create();

        private static long create() {
            if (!NativeBlockRegistry.ready()) return 0;
            try {
                return (long)TABLES_CREATE.invokeExact();
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
        private final Arena arena = Arena.ofAuto();
        private final MemorySegment nativeColumns = arena.allocate(1280 * 4L, 4);
        private final MemorySegment nativeOut = arena.allocate(3 * 4L, 4);

        private boolean compatibilityProjected;
        private void projectCompatibilityColumns(){
            if(!compatibilityProjected){
                MemorySegment.copy(this.nativeColumns,ValueLayout.JAVA_INT,0,this.columns,0,this.columns.length);
                compatibilityProjected=true;
            }
        }
        MemorySegment columnsForCoordinator(){return this.nativeColumns;}

        /** One section: writes the layer like the original's {@code set} calls
         * and enqueues the same increases in the same order. Returns whether a
         * source lies below the section (the original's loop condition). */
        boolean section(SkyLightEngine engine, DataLayer layer, int bottom, int minX, int minZ) {
            if (engine.getClass() == SkyLightEngine.class && layer.getClass() == DataLayer.class
                    && layer.seedNativeSkyLightQueued(this.nativeColumns, bottom, minX, minZ, engine.lightWork, this.nativeOut)) {
                return this.nativeOut.getAtIndex(ValueLayout.JAVA_INT, 2) != 0;
            }
            projectCompatibilityColumns();
            if (layer.getClass() == DataLayer.class
                    && layer.seedNativeSkyLight(this.columns, bottom, minX, minZ, this.entries, this.out)) {
                for (int i = 0; i < this.out[0]; i++) engine.enqueueIncrease(this.entries[2 * i], this.entries[2 * i + 1]);
                return this.out[2] != 0;
            }
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
    private static final MethodHandle SOURCE_COLUMNS = NativeLibraryLoader.downcallHandle("mattmc_rust",
        "mattmc_sky_source_seed_columns", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

    static SkySeed skySeed(ChunkSkyLightSources center, ChunkSkyLightSources north, ChunkSkyLightSources south, ChunkSkyLightSources west,
                           ChunkSkyLightSources east) {
        // Custom readers have observable conditional calls in the original
        // section loop. Eagerly gathering a matrix changes calls and failures.
        if (!enabled || center.getClass() != ChunkSkyLightSources.class
                || north.getClass() != ChunkSkyLightSources.class || south.getClass() != ChunkSkyLightSources.class
                || west.getClass() != ChunkSkyLightSources.class || east.getClass() != ChunkSkyLightSources.class) return null;
        SkySeed seed = SEEDS.get();
        try {
            long c=center.nativeOwnerForLighting(),n=north.nativeOwnerForLighting(),s=south.nativeOwnerForLighting(),
                w=west.nativeOwnerForLighting(),e=east.nativeOwnerForLighting();
            int status=(int)SOURCE_COLUMNS.invokeExact(c,n,s,w,e,seed.nativeColumns);
            if(status!=0)throw new IllegalStateException("Native source columns rejected: "+status);
            // Keep the existing compatibility array's reentrant update timing.
            // Rust produced the snapshot; this array is a derived legacy transport.
            seed.compatibilityProjected=false;
            seed.projectCompatibilityColumns();
        } catch(Throwable error) {
            throw NativeSkySourceOwner.fail(error);
        } finally {
            java.lang.ref.Reference.reachabilityFence(center);
            java.lang.ref.Reference.reachabilityFence(north);
            java.lang.ref.Reference.reachabilityFence(south);
            java.lang.ref.Reference.reachabilityFence(west);
            java.lang.ref.Reference.reachabilityFence(east);
        }
        SEEDED.incrementAndGet();
        return seed;
    }

    private final long handle;
    private final boolean sky;
    private final LightEngine<?, ?> engine;
    // Keep the callback's CPU owner live until Rust has copied its generation.
    @Nullable private DataLayer borrowedLayer;
    // Only the current callback's primary CPU owner, not a state/word mirror.
    private final NativeLightBlocks.Lease borrowedBlocks = new NativeLightBlocks.Lease();
    @Nullable private byte[] compatibilityResult;
    private final Arena arena = Arena.ofAuto();
    private final MemorySegment out = arena.allocate(3 * 8L, 8);
    private boolean running;

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

    /** {@code propagateDecreases() + propagateIncreases()}, or -1 with the
     * queues and storage unchanged for the Java propagation. */
    int run(LayerLightSectionStorage<?> storage, NativeLightQueues queues) {
        if (this.running || !enabled || Tables.HANDLE == 0) return -1;
        if (queues.isEmpty(0) && queues.isEmpty(1)) return 0;
        if (storage.getClass() != (this.sky ? SkyLightSectionStorage.class : BlockLightSectionStorage.class)) return -1;
        NativeLightPropagation previous = CURRENT.get();
        this.running = true;
        CURRENT.set(this);
        try { return runPass(storage, queues); }
        finally {
            this.running = false;
            this.borrowedLayer = null;
            this.borrowedBlocks.clear();
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            Reference.reachabilityFence(queues);
            Reference.reachabilityFence(this);
        }
    }

    private int runPass(LayerLightSectionStorage<?> storage, NativeLightQueues queues) {
        int lowest = this.sky ? ((SkyLightSectionStorage)storage).getBottomSectionY() : Integer.MAX_VALUE;
        int status;
        try {
            NativeLightCoordinator coordinator = this.engine.lightCoordinator;
            if (coordinator != null) {
                try {
                    status = (int)RUN_CONNECTED.invokeExact(this.handle, Tables.HANDLE, queues.ownerForNativeCall(),
                        coordinator.bindingForPropagation(), lowest, SECTION, this.out);
                } finally { Reference.reachabilityFence(coordinator); }
            } else {
                status = (int)RUN.invokeExact(this.handle, Tables.HANDLE, queues.ownerForNativeCall(), lowest, SECTION, this.out);
            }
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Native light propagation failed", error);
        }
        if (status != 0) {
            if (status == -1) throw new IllegalStateException("Native light propagation rejected its input");
            // Rust restores original inputs after any callback additions before replay.
            REJECTED.incrementAndGet();
            return -1;
        }
        long processed = this.out.getAtIndex(ValueLayout.JAVA_LONG, 0);
        int written = (int)this.out.getAtIndex(ValueLayout.JAVA_LONG, 1);
        int affected = (int)this.out.getAtIndex(ValueLayout.JAVA_LONG, 2);
        if(storage.coordinator!=null){
            storage.coordinator.installResults(this,this.handle,written,affected);
            PASSES.incrementAndGet();
            return(int)processed;
        }
        long[] keys = new long[written];
        long[] sections = new long[affected];
        try {
            status = (int)RESULTS.invokeExact(this.handle, MemorySegment.ofArray(keys), written,
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
            if (layer.getClass() == DataLayer.class && layer.installNativeLightResult(this.handle, i)) continue;
            if (this.compatibilityResult == null) this.compatibilityResult = new byte[2048];
            try {
                status = (int)COPY_RESULT.invokeExact(this.handle, i, MemorySegment.ofArray(this.compatibilityResult), 2048);
            } catch (Throwable error) { throw new IllegalStateException("Native light compatibility export failed", error); }
            if (status != 0) throw new IllegalStateException("Native light compatibility export rejected: " + status);
            System.arraycopy(this.compatibilityResult, 0, layer.getData(), 0, 2048);
        }
        for (long section : sections) storage.sectionsAffectedByLightUpdates.add(section);
        PASSES.incrementAndGet();
        return (int)processed;
    }

    long directLayerReadsForVerification() {
        try { return (long)DIRECT_LAYER_READS.invokeExact(this.handle); }
        catch (Throwable failure) { throw new IllegalStateException("Native light input diagnostic failed", failure); }
        finally { Reference.reachabilityFence(this); }
    }

    /** Contextual compatibility leaf used after the native COW decision. */
    void installCompatibilityResult(int index, DataLayer layer) {
        if (layer.getClass() == DataLayer.class && layer.installNativeLightResult(this.handle, index)) return;
        if (this.compatibilityResult == null) this.compatibilityResult = new byte[2048];
        int status;
        try { status = (int)COPY_RESULT.invokeExact(this.handle, index, MemorySegment.ofArray(this.compatibilityResult), 2048); }
        catch (Throwable error) { throw new IllegalStateException("Native light compatibility export failed", error); }
        if (status != 0) throw new IllegalStateException("Native light compatibility export rejected: " + status);
        System.arraycopy(this.compatibilityResult, 0, layer.getData(), 0, 2048);
    }

    /** Rust's section callback. Mode 0: the stored layer of {@code section}
     * (0 absent, 1 stored); mode 1: its block states (0); mode 2: replace a
     * previous Java layer pin after a connected native read (0). Negative values
     * reject (-1) or report a failure (-2); the Java rerun meets it again. */
    private static int section(int mode, long section, MemorySegment buffer) {
        NativeLightPropagation current = CURRENT.get();
        if (current == null) return -2;
        try {
            if (mode == 2) {
                current.borrowedLayer = null;
                return 0;
            }
            buffer = buffer.reinterpret(NativeLightBlocks.BUFFER);
            LayerLightSectionStorage<?> storage = current.engine.storage;
            if (mode == 0) {
                DataLayer layer = storage.getDataLayer(section, true);
                current.borrowedLayer = layer;
                return layer == null ? 0 : NativeLightBlocks.layer(layer, storage.lightOnInSection(section), buffer);
            }
            int x = SectionPos.x(section), z = SectionPos.z(section);
            return NativeLightBlocks.blocks(current.engine.chunkSource.getChunkForLighting(x, z), x, SectionPos.y(section), z, buffer,
                current.borrowedBlocks);
        } catch (Throwable error) {
            return -2;
        }
    }
}
