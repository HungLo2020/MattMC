package net.minecraft.world.level.levelgen;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.levelgen.synth.NativeNoiseState;
import org.jetbrains.annotations.Nullable;

/** Rust-owned chunk noise instantiation. A {@link RandomState} compiles, once,
 * everything the native NOISE fill needs from its unwrapped router: a slice
 * template over the interpolated functions the cell cache and ore veins read,
 * a program of FlatCache inputs, the cell density program and the ore vein and
 * aquifer barrier inputs. An eligible {@link NoiseChunk} then skips Java's
 * per-chunk graph wrapping: each fill instantiates the template with the
 * chunk's FlatCache tables, computed natively as FlatCache computes them. Java
 * wraps the chunk's graph only if a Java path asks for it. */
final class NativeChunkNoise {
    private static final MethodHandle TEMPLATE = bind("mattmc_noise_router_template");
    private static final MethodHandle FLATS = bind("mattmc_flat_program_create");
    private static final MethodHandle INSTANCE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_noise_router_instance",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS), Linker.Option.critical(true));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_surface_program_release",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final Cleaner CLEANER = Cleaner.create();
    // Tests compare both routes in one JVM; production reads the property once.
    private static volatile boolean enabled = !Boolean.getBoolean("mattmc.worldgen.javaChunkNoise");

    private static MethodHandle bind(String name) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", name, FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    }

    private final long template, flats;
    // Both programs reference these states; they stay reachable while they live.
    private final NativeNoiseState[] states;
    final int roots;
    final NativeDensityProgram cellProgram;
    final int[] cellInputs;
    // Chunks with a structure Beardifier: the final density's own cell program
    // (Java adds the Beardifier cell to it), or null to keep such chunks wrapped.
    @Nullable
    final NativeDensityProgram densityProgram;
    @Nullable
    final int[] densityInputs;
    // Toggle, ridged A and ridged B roots, or null without ore veins.
    @Nullable
    final int[] ore;
    final double ridgedConstant, ridgedBound;
    @Nullable
    final DensityFunctions.Noise gap;
    @Nullable
    final DensityFunctions.Noise barrier;

    private NativeChunkNoise(long template, long flats, NativeNoiseState[] states, int roots, NativeCellDensity.Template cells, int[] cellInputs,
                             @Nullable NativeCellDensity.Template density, @Nullable int[] densityInputs,
                             @Nullable int[] ore, double ridgedConstant, double ridgedBound, @Nullable DensityFunctions.Noise gap,
                             @Nullable DensityFunctions.Noise barrier) {
        this.densityProgram = density == null ? null : density.program();
        this.densityInputs = densityInputs;
        this.template = template;
        this.flats = flats;
        this.states = states;
        this.roots = roots;
        this.cellProgram = cells.program();
        this.cellInputs = cellInputs;
        this.ore = ore;
        this.ridgedConstant = ridgedConstant;
        this.ridgedBound = ridgedBound;
        this.gap = gap;
        this.barrier = barrier;
        CLEANER.register(this, new Release(template, flats));
    }

    private record Release(long template, long flats) implements Runnable {
        @Override
        public void run() {
            try {
                RELEASE.invokeExact(this.template);
                if (this.flats != 0) RELEASE.invokeExact(this.flats);
            } catch (Throwable error) {
                throw new IllegalStateException("Cannot release native chunk noise", error);
            }
        }
    }

    static void setEnabled(boolean value) {
        enabled = value;
    }

    static boolean enabled() {
        return enabled;
    }

    @Nullable
    private static DensityFunction resolve(DensityFunction f) {
        for (int step = 0; step < 64 && f != null; step++) {
            f = NativeDensity.unwrap(f);
            if (f.getClass() != DensityFunctions.HolderHolder.class) return f;
            Holder<DensityFunction> holder = ((DensityFunctions.HolderHolder)f).function();
            if (!holder.isBound()) return null;
            f = holder.value();
        }
        return null;
    }

    private static boolean interpolated(@Nullable DensityFunction f) {
        return f != null && f.getClass() == DensityFunctions.Marker.class && ((DensityFunctions.Marker)f).type() == DensityFunctions.Marker.Type.Interpolated;
    }

    /** Compiles a RandomState's unwrapped router; null keeps chunks on Java wrapping. */
    @Nullable
    static NativeChunkNoise compile(RandomState random, NoiseGeneratorSettings settings, Map<NativeCellDensity.Key, NativeDensityProgram> cellPrograms) {
        // Chunk wrapping rebuilds every node, resolving holders and normalizing
        // (two-argument functions of constants become MulOrAdd, for example).
        // Rebuild the same way, keeping markers in place of the chunk's caches.
        var normalized = new java.util.HashMap<DensityFunction, DensityFunction>();
        NoiseRouter router = random.router().mapAll(f -> normalized.computeIfAbsent(f,
            g -> g instanceof DensityFunctions.HolderHolder holder ? holder.function().value() : g));
        // The chunk's cell cache: cacheAllInCell(add(finalDensity, beardifier)).
        var cells = NativeCellDensity.template(DensityFunctions.add(router.finalDensity(), DensityFunctions.BeardifierMarker.INSTANCE), cellPrograms);
        if (cells == null) return null;
        List<DensityFunctions.Marker> markers = new ArrayList<>(cells.inputs());
        int[] cellInputs = new int[markers.size()];
        for (int index = 0; index < cellInputs.length; index++) cellInputs[index] = index;
        // With a structure Beardifier the cell cache is add(finalDensity, beardifier):
        // finalDensity's own cell program, then the Beardifier cell (Java's Ap2 ADD).
        var density = NativeCellDensity.template(router.finalDensity(), cellPrograms);
        int[] densityInputs = null;
        if (density != null) {
            densityInputs = new int[density.inputs().size()];
            for (int index = 0; index < densityInputs.length; index++) densityInputs[index] = markers.indexOf(density.inputs().get(index));
            for (int input : densityInputs) if (input < 0) { density = null; densityInputs = null; break; }
        }
        int[] ore = null;
        double ridgedConstant = 0, ridgedBound = 0;
        DensityFunctions.Noise gap = null;
        if (settings.oreVeinsEnabled()) {
            // The vanilla shape NativeNoiseFill requires of a chunk's wrapped veins.
            DensityFunction toggle = resolve(router.veinToggle()), ridged = resolve(router.veinRidged()), gapFunction = resolve(router.veinGap());
            if (!interpolated(toggle) || !(ridged instanceof DensityFunctions.MulOrAdd add) || add.specificType() != DensityFunctions.MulOrAdd.Type.ADD
                || !(resolve(add.input()) instanceof DensityFunctions.Ap2 max) || max.type() != DensityFunctions.TwoArgumentSimpleFunction.Type.MAX
                || !(resolve(max.argument1()) instanceof DensityFunctions.Mapped a) || a.type() != DensityFunctions.Mapped.Type.ABS
                || !interpolated(resolve(a.input()))
                || !(resolve(max.argument2()) instanceof DensityFunctions.Mapped b) || b.type() != DensityFunctions.Mapped.Type.ABS
                || !interpolated(resolve(b.input()))
                || gapFunction == null || gapFunction.getClass() != DensityFunctions.Noise.class) {
                return null;
            }
            ore = new int[]{root(markers, toggle), root(markers, resolve(a.input())), root(markers, resolve(b.input()))};
            ridgedConstant = add.argument();
            ridgedBound = max.argument2().maxValue();
            gap = (DensityFunctions.Noise)gapFunction;
        }
        DensityFunction barrierFunction = resolve(router.barrierNoise());
        DensityFunctions.Noise barrier = barrierFunction != null && barrierFunction.getClass() == DensityFunctions.Noise.class
            ? (DensityFunctions.Noise)barrierFunction : null;
        if (settings.isAquifersEnabled() && barrier == null) return null;

        var slots = new NativeNoiseRouter.FlatSlots();
        var slices = new NativeNoiseRouter.Compiler(false, false, slots);
        int[] roots = new int[markers.size()];
        for (int index = 0; index < roots.length; index++) {
            roots[index] = slices.add(markers.get(index).wrapped(), 0);
            if (roots[index] < 0) return null;
        }
        int templateSlots = slots.markers.size();
        var flatInputs = new NativeNoiseRouter.Compiler(true, true, slots);
        it.unimi.dsi.fastutil.ints.IntArrayList flatRoots = new it.unimi.dsi.fastutil.ints.IntArrayList();
        // Nested FlatCaches can add slots while their parents compile.
        for (int slot = 0; slot < slots.markers.size(); slot++) {
            int root = flatInputs.add(slots.markers.get(slot).wrapped(), 0);
            if (root < 0) return null;
            flatRoots.add(root);
        }
        // Template tables: flatSize 1, zeroed; instances supply them per chunk.
        for (int slot = 0; slot < templateSlots; slot++) slices.params.add(0.0);
        slices.packedFlatSlots = templateSlots;
        var packed = slices.pack(roots, 1, 1, 1, 1, 0, 0, 0, 0, 1);
        NativeNoiseRouter.Packed flatPacked = templateSlots == 0 ? null : flatInputs.pack(flatRoots.toIntArray(), 1, 1, 1, 1, 0, 0, 0, 0, 1);
        long template = create(TEMPLATE, packed), flats = flatPacked == null ? 0 : create(FLATS, flatPacked);
        if (template == 0 || (flatPacked != null && flats == 0)) {
            try {
                if (template != 0) RELEASE.invokeExact(template);
                if (flats != 0) RELEASE.invokeExact(flats);
            } catch (Throwable error) {
                throw new IllegalStateException("Cannot release native chunk noise", error);
            }
            return null;
        }
        List<NativeNoiseState> states = new ArrayList<>(List.of(packed.states()));
        if (flatPacked != null) states.addAll(List.of(flatPacked.states()));
        return new NativeChunkNoise(template, flats, states.toArray(NativeNoiseState[]::new), roots.length, cells, cellInputs, density, densityInputs,
            ore, ridgedConstant, ridgedBound, gap, barrier);
    }

    /** A marker's root, adding the vein interpolators the cell cache does not read. */
    private static int root(List<DensityFunctions.Marker> markers, DensityFunction marker) {
        int index = markers.indexOf(marker);
        if (index >= 0) return index;
        markers.add((DensityFunctions.Marker)marker);
        return markers.size() - 1;
    }

    private static long create(MethodHandle handle, NativeNoiseRouter.Packed packed) {
        try {
            return (long)handle.invokeExact(MemorySegment.ofArray(packed.ints()), packed.ints().length, MemorySegment.ofArray(packed.doubles()),
                packed.doubles().length, MemorySegment.ofArray(packed.longs()), packed.longs().length);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot create native chunk noise", error);
        } finally {
            Reference.reachabilityFence(packed.states());
        }
    }

    /** A router over this template for one chunk, its FlatCache tables computed natively. */
    NativeNoiseRouter instance(NoiseChunk chunk) {
        int[] geometry = {chunk.cellCountXZ + 1, chunk.cellCountY + 1, chunk.cellWidth, chunk.cellHeight, chunk.firstCellZ(), chunk.cellNoiseMinY,
            chunk.firstNoiseX, chunk.firstNoiseZ, chunk.noiseSizeXZ + 1};
        long handle;
        try {
            handle = (long)INSTANCE.invokeExact(this.template, this.flats, MemorySegment.ofArray(geometry));
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot instantiate native chunk noise", error);
        } finally {
            Reference.reachabilityFence(this);
        }
        if (handle == 0) throw new IllegalStateException("Native chunk noise rejected its chunk");
        return NativeNoiseRouter.instance(handle, this, chunk.cellCountXZ + 1, chunk.cellCountY + 1);
    }

    /** Verification only: keeps the states referenced while instances run. */
    NativeNoiseState[] states() {
        return this.states;
    }
}
