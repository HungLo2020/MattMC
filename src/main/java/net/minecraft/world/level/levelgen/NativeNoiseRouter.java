package net.minecraft.world.level.levelgen;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Holder;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NativeNoiseState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jetbrains.annotations.Nullable;

/** Native-owned interpolation slices for the native NOISE fill. Each of the
 * chunk's interpolators is compiled, from its chunk-wrapped graph, into one
 * Rust router program: the density operations with CacheOnce shared by
 * identity, FlatCache values copied once and Cache2D proven transparent. Rust
 * then fills every column of a slice in one ordinary downcall. Chunks with any
 * other function, or with a cell cache that could observe a slice-filled
 * CacheOnce, keep Java's slice fill. */
final class NativeNoiseRouter implements AutoCloseable {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_noise_router_create",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    // A whole slice per call: an ordinary downcall into off-heap memory, so the GC is never held.
    private static final MethodHandle SLICE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_noise_router_slice",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_noise_router_release",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    // Tests compare both routes in one JVM; production reads the property once.
    private static volatile boolean enabled = !Boolean.getBoolean("mattmc.worldgen.javaNoiseRouter");
    // Natively filled slices, so tests can prove the gate engaged.
    static final AtomicLong SLICES = new AtomicLong();
    private static final int MAX_NODES = 4096, MAX_DEPTH = 256;

    private final List<NoiseChunk.NoiseInterpolator> interpolators;
    private final NativeNoiseState[] states;
    private final Arena arena = Arena.ofConfined();
    private final MemorySegment output;
    private final int columns, points;
    private final long handle;
    // A template instance keeps its per-seed programs reachable.
    @Nullable
    private final NativeChunkNoise template;

    private NativeNoiseRouter(List<NoiseChunk.NoiseInterpolator> interpolators, NativeNoiseState[] states, int columns, int points, long handle) {
        this(interpolators, states, columns, points, handle, null, interpolators.size());
    }

    private NativeNoiseRouter(List<NoiseChunk.NoiseInterpolator> interpolators, NativeNoiseState[] states, int columns, int points, long handle,
                              @Nullable NativeChunkNoise template, int roots) {
        this.template = template;
        this.interpolators = interpolators;
        this.states = states;
        this.columns = columns;
        this.points = points;
        this.handle = handle;
        this.output = this.arena.allocate((long)roots * columns * points * 8L, 8);
    }

    long handle() {
        return this.handle;
    }

    /** A per-chunk instance of a per-seed template; its slices go only to a
     * bound native traversal, never into Java interpolators. */
    static NativeNoiseRouter instance(long handle, NativeChunkNoise template, int columns, int points) {
        return new NativeNoiseRouter(List.of(), template.states(), columns, points, handle, template, template.roots);
    }

    /** Verification only: one slice's raw values (root, column, Y), or null when declined. */
    @Nullable
    double[] sliceValues(int blockX) {
        int status;
        try {
            status = (int)SLICE.invokeExact(this.handle, blockX, this.output, this.output.byteSize() / 8);
        } catch (Throwable error) {
            throw new IllegalStateException("Native noise router slice failed", error);
        } finally {
            Reference.reachabilityFence(this.states);
            Reference.reachabilityFence(this.template);
        }
        return status == 0 ? this.output.toArray(ValueLayout.JAVA_DOUBLE) : null;
    }

    static void setEnabled(boolean value) {
        enabled = value;
    }

    /** Compiles every interpolator of a fresh chunk; null keeps Java's slice fill. */
    @Nullable
    static NativeNoiseRouter create(NoiseChunk chunk) {
        // Compiles the chunk's wrapped graph; a native chunk builds it now.
        chunk.ensureWrapped();
        if (!enabled || chunk.getClass() != NoiseChunk.class || chunk.getBlender() != Blender.empty() || chunk.interpolating
            || chunk.interpolators.isEmpty() || chunk.interpolators.size() > 64) {
            return null;
        }
        Compiler compiler = new Compiler(false);
        int[] roots = new int[chunk.interpolators.size()];
        for (int index = 0; index < roots.length; index++) {
            NoiseChunk.NoiseInterpolator interpolator = chunk.interpolators.get(index);
            if (interpolator.getClass() != NoiseChunk.NoiseInterpolator.class || interpolator.cellOwner() != chunk) return null;
            roots[index] = compiler.add(interpolator.wrapped(), 0);
            if (roots[index] < 0) return null;
        }
        // Java's cell caches run with this chunk as context after each slice and
        // could read a CacheOnce value the Java slice fill left behind.
        var visited = Collections.<Object>newSetFromMap(new IdentityHashMap<>());
        for (NoiseChunk.CacheAllInCell cache : chunk.cellCaches) {
            if (!compiler.cannotObserve(cache.wrapped(), visited, 0)) return null;
        }
        int columns = chunk.cellCountXZ + 1, points = chunk.cellCountY + 1, flatSize = chunk.noiseSizeXZ + 1;
        for (NoiseChunk.FlatCache cache : compiler.flats) {
            if (cache.values.length != flatSize * flatSize) return null;
            compiler.params.addElements(compiler.params.size(), cache.values);
        }
        Packed packed = compiler.pack(roots, columns, points, chunk.cellWidth, chunk.cellHeight, chunk.firstCellZ(), chunk.cellNoiseMinY,
            chunk.firstNoiseX, chunk.firstNoiseZ, flatSize);
        NativeNoiseState[] states = packed.states();
        long handle;
        try {
            handle = (long)CREATE.invokeExact(MemorySegment.ofArray(packed.ints()), packed.ints().length, MemorySegment.ofArray(packed.doubles()),
                packed.doubles().length, MemorySegment.ofArray(packed.longs()), packed.longs().length);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot create native noise router", error);
        } finally {
            Reference.reachabilityFence(states);
        }
        // Rust also proves every Cache2D input Y-independent; a rejected program keeps Java slices.
        if (handle == 0) return null;
        return new NativeNoiseRouter(List.copyOf(chunk.interpolators), states, columns, points, handle);
    }

    /** Fills slice0 (first) or slice1 at block X; false leaves the slice to Java. */
    boolean fill(boolean first, int blockX) {
        int status;
        try {
            status = (int)SLICE.invokeExact(this.handle, blockX, this.output, this.output.byteSize() / 8);
        } catch (Throwable error) {
            throw new IllegalStateException("Native noise router slice failed", error);
        } finally {
            Reference.reachabilityFence(this.states);
        }
        if (status == 1) return false;
        if (status != 0) throw new IllegalStateException("Native noise router slice failed: " + status);
        for (int index = 0; index < this.interpolators.size(); index++) {
            NoiseChunk.NoiseInterpolator interpolator = this.interpolators.get(index);
            double[][] slice = first ? interpolator.slice0 : interpolator.slice1;
            for (int column = 0; column < this.columns; column++) {
                MemorySegment.copy(this.output, ValueLayout.JAVA_DOUBLE, ((long)index * this.columns + column) * this.points * 8L, slice[column], 0, this.points);
            }
        }
        SLICES.incrementAndGet();
        return true;
    }

    @Override
    public void close() {
        try {
            RELEASE.invokeExact(this.handle);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot release native noise router", error);
        } finally {
            Reference.reachabilityFence(this.states);
            Reference.reachabilityFence(this.template);
            this.arena.close();
        }
    }

    /** A program in the create ABI, with the noise states it references. */
    record Packed(int[] ints, double[] doubles, long[] longs, NativeNoiseState[] states) {}

    /** Builds a router program. Slice programs compute exactly what each
     * chunk-wrapped node's compute does with the chunk as context during a
     * slice fill. Point programs compile a RandomState's unwrapped router for
     * point contexts at quart-aligned columns, where every chunk cache marker
     * reads through to its input: interpolators, CacheOnce and cell caches
     * evaluate directly, and FlatCache and Cache2D markers give their
     * Y-independent input's value (Rust validates the independence). */
    /** FlatCache markers of a RandomState's unwrapped router, numbered in the
     * order compilers first meet them: the slots of per-chunk FlatCache tables. */
    static final class FlatSlots {
        final List<DensityFunctions.Marker> markers = new ArrayList<>();
        private final IdentityHashMap<DensityFunctions.Marker, Integer> ids = new IdentityHashMap<>();

        int slot(DensityFunctions.Marker marker) {
            return this.ids.computeIfAbsent(marker, key -> {
                this.markers.add(key);
                return this.markers.size() - 1;
            });
        }
    }

    static final class Compiler {
        private final boolean point, flatPoints;
        // Template slice programs and their FlatCache inputs share these slots.
        @Nullable
        private final FlatSlots slots;
        // FlatCache markers compiled as exact chunk FlatCache points, by memo slot.
        int flatPointSlots;
        // A template's FlatCache slot count for pack(); otherwise the chunk tables'.
        int packedFlatSlots = -1;

        Compiler(boolean point) {
            this(point, false);
        }

        /** With {@code flatPoints}, point programs evaluate FlatCache markers as a
         * chunk's FlatCache does at any point, through a per-chunk binding. */
        Compiler(boolean point, boolean flatPoints) {
            this(point, flatPoints, null);
        }

        /** With {@code slots}: a slice program over a RandomState's unwrapped graph
         * (a per-seed template; FlatCache markers read per-chunk tables), or a point
         * program of FlatCache inputs whose nested FlatCaches use the same slots. */
        Compiler(boolean point, boolean flatPoints, @Nullable FlatSlots slots) {
            this.point = point;
            this.flatPoints = flatPoints;
            this.slots = slots;
        }
        final IntArrayList nodes = new IntArrayList();
        final DoubleArrayList params = new DoubleArrayList();
        final IntArrayList splineHeaders = new IntArrayList(), splineNodes = new IntArrayList(), splineKnots = new IntArrayList();
        final List<NativeNoiseState> states = new ArrayList<>();
        final List<NoiseChunk.FlatCache> flats = new ArrayList<>();
        private final IdentityHashMap<DensityFunction, Integer> ids = new IdentityHashMap<>();
        private final IdentityHashMap<NativeNoiseState, Integer> stateIds = new IdentityHashMap<>();
        private final IdentityHashMap<NoiseChunk.FlatCache, Integer> flatIds = new IdentityHashMap<>();
        private final Set<DensityFunction> cacheOnce = Collections.newSetFromMap(new IdentityHashMap<>());

        /** Packs with the root list and the 9 slice geometry values (unused by point programs). */
        Packed pack(int[] roots, int... geometry) {
            IntArrayList ints = new IntArrayList();
            ints.addElements(0, new int[]{this.nodes.size() / 5, roots.length, this.splineHeaders.size() / 9, this.splineNodes.size() / 4,
                this.splineKnots.size() / 3, this.states.size(), this.packedFlatSlots >= 0 ? this.packedFlatSlots : this.flats.size()});
            ints.addElements(ints.size(), geometry);
            ints.addAll(this.nodes);
            ints.addElements(ints.size(), roots);
            ints.addAll(this.splineHeaders);
            ints.addAll(this.splineNodes);
            ints.addAll(this.splineKnots);
            NativeNoiseState[] states = this.states.toArray(NativeNoiseState[]::new);
            long[] longs = new long[states.length * 2];
            for (int index = 0; index < states.length; index++) {
                longs[index * 2] = states[index].state().address();
                longs[index * 2 + 1] = states[index].state().byteSize();
            }
            return new Packed(ints.toIntArray(), this.params.toDoubleArray(), longs, states);
        }

        /** A point program's root; FindTopSurface is accepted only here. */
        int addRoot(DensityFunction function) {
            DensityFunction f = resolve(function);
            if (this.point && f != null && f.getClass() == DensityFunctions.FindTopSurface.class) {
                var n = (DensityFunctions.FindTopSurface)f;
                int density = add(n.density(), 1), upper = add(n.upperBound(), 1);
                return density < 0 || upper < 0 ? -1 : node(36, density, upper, 0, null, n.lowerBound(), n.cellHeight(), 0, 0);
            }
            return add(function, 0);
        }

        int add(DensityFunction function, int depth) {
            if (depth > MAX_DEPTH) return -1;
            DensityFunction f = resolve(function);
            if (f == null) return -1;
            Integer known = this.ids.get(f);
            if (known != null) return known;
            int id = compile(f, depth);
            if (id >= 0) this.ids.put(f, id);
            return id;
        }

        /** Wrappers whose compute is their input's: optimizer wrappers, CacheOnce
         * (a pure input gives the same value at every visit), holders and
         * BlendDensity under the empty blender. */
        @Nullable
        private DensityFunction resolve(DensityFunction f) {
            for (int step = 0; step < 64 && f != null; step++) {
                f = NativeDensity.unwrap(f);
                Class<?> type = f.getClass();
                if (type == NoiseChunk.CacheOnce.class) {
                    this.cacheOnce.add(f);
                    f = ((NoiseChunk.CacheOnce)f).wrapped();
                } else if (type == DensityFunctions.HolderHolder.class) {
                    Holder<DensityFunction> holder = ((DensityFunctions.HolderHolder)f).function();
                    if (!holder.isBound()) return null;
                    f = holder.value();
                } else if (type == DensityFunctions.BlendDensity.class) {
                    f = ((DensityFunctions.BlendDensity)f).input();
                } else if (this.point && type == DensityFunctions.Marker.class) {
                    var marker = (DensityFunctions.Marker)f;
                    var kind = marker.type();
                    if (kind == DensityFunctions.Marker.Type.FlatCache || kind == DensityFunctions.Marker.Type.Cache2D) return f;
                    f = marker.wrapped();
                } else if (this.slots != null && type == DensityFunctions.Marker.class
                    && ((DensityFunctions.Marker)f).type() == DensityFunctions.Marker.Type.CacheOnce) {
                    // A chunk's CacheOnce reads through during slice fills, as above.
                    f = ((DensityFunctions.Marker)f).wrapped();
                } else {
                    return f;
                }
            }
            return null;
        }

        private int compile(DensityFunction f, int depth) {
            Class<?> type = f.getClass();
            if (type == DensityFunctions.Constant.class) return node(0, 0, 0, 0, null, ((DensityFunctions.Constant)f).value(), 0, 0, 0);
            if (f == DensityFunctions.BlendAlpha.INSTANCE) return node(0, 0, 0, 0, null, 1.0, 0, 0, 0);
            if (f == DensityFunctions.BlendOffset.INSTANCE) return node(0, 0, 0, 0, null, 0.0, 0, 0, 0);
            if (type == DensityFunctions.Noise.class) {
                var n = (DensityFunctions.Noise)f;
                return node(1, 0, 0, 0, noise(n.noise()), n.xzScale(), n.yScale(), 0, 0);
            }
            if (type == DensityFunctions.Shift.class) return node(2, 0, 0, 0, noise(((DensityFunctions.Shift)f).offsetNoise()), 0, 0, 0, 0);
            if (type == DensityFunctions.ShiftA.class) return node(3, 0, 0, 0, noise(((DensityFunctions.ShiftA)f).offsetNoise()), 0, 0, 0, 0);
            if (type == DensityFunctions.ShiftB.class) return node(4, 0, 0, 0, noise(((DensityFunctions.ShiftB)f).offsetNoise()), 0, 0, 0, 0);
            if (type == DensityFunctions.ShiftedNoise.class) {
                var n = (DensityFunctions.ShiftedNoise)f;
                int a = add(n.shiftX(), depth + 1), b = add(n.shiftY(), depth + 1), c = add(n.shiftZ(), depth + 1);
                return a < 0 || b < 0 || c < 0 ? -1 : node(5, a, b, c, noise(n.noise()), n.xzScale(), n.yScale(), 0, 0);
            }
            if (type == DensityFunctions.WeirdScaledSampler.class) {
                var n = (DensityFunctions.WeirdScaledSampler)f;
                int a = add(n.input(), depth + 1);
                int op = n.rarityValueMapper() == DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE1 ? 6 : 7;
                return a < 0 ? -1 : node(op, a, 0, 0, noise(n.noise()), 0, 0, 0, 0);
            }
            if (type == DensityFunctions.Ap2.class) {
                var n = (DensityFunctions.Ap2)f;
                int op = switch (n.type()) { case ADD -> 8; case MUL -> 9; case MIN -> 10; case MAX -> 11; };
                int a = add(n.argument1(), depth + 1), b = add(n.argument2(), depth + 1);
                return a < 0 || b < 0 ? -1 : node(op, a, b, 0, null, n.argument2().minValue(), n.argument2().maxValue(), 0, 0);
            }
            if (type == DensityFunctions.MulOrAdd.class) {
                var n = (DensityFunctions.MulOrAdd)f;
                int a = add(n.input(), depth + 1);
                return a < 0 ? -1 : node(n.specificType() == DensityFunctions.MulOrAdd.Type.MUL ? 12 : 13, a, 0, 0, null, n.argument(), 0, 0, 0);
            }
            if (type == DensityFunctions.Mapped.class) {
                var n = (DensityFunctions.Mapped)f;
                int a = add(n.input(), depth + 1);
                return a < 0 ? -1 : node(14 + n.type().ordinal(), a, 0, 0, null, 0, 0, 0, 0);
            }
            if (type == DensityFunctions.Clamp.class) {
                var n = (DensityFunctions.Clamp)f;
                int a = add(n.input(), depth + 1);
                return a < 0 ? -1 : node(21, a, 0, 0, null, n.minValue(), n.maxValue(), 0, 0);
            }
            if (type == DensityFunctions.RangeChoice.class) {
                var n = (DensityFunctions.RangeChoice)f;
                int a = add(n.input(), depth + 1), b = add(n.whenInRange(), depth + 1), c = add(n.whenOutOfRange(), depth + 1);
                return a < 0 || b < 0 || c < 0 ? -1 : node(22, a, b, c, null, n.minInclusive(), n.maxExclusive(), 0, 0);
            }
            if (type == DensityFunctions.YClampedGradient.class) {
                var n = (DensityFunctions.YClampedGradient)f;
                return node(30, 0, 0, 0, null, n.fromY(), n.toY(), n.fromValue(), n.toValue());
            }
            if (type == BlendedNoise.class) return node(31, 0, 0, 0, ((BlendedNoise)f).nativeState(), 0, 0, 0, 0);
            if (type == DensityFunctions.EndIslandDensityFunction.class) {
                return node(32, 0, 0, 0, ((DensityFunctions.EndIslandDensityFunction)f).islandNoise().nativeState(), 0, 0, 0, 0);
            }
            if (type == DensityFunctions.Spline.class) {
                int spline = spline(((DensityFunctions.Spline)f).spline(), depth);
                return spline < 0 ? -1 : node(33, spline, 0, 0, null, 0, 0, 0, 0);
            }
            if (!this.point && type == NoiseChunk.FlatCache.class) {
                var cache = (NoiseChunk.FlatCache)f;
                Integer slot = this.flatIds.get(cache);
                if (slot == null) {
                    slot = this.flats.size();
                    this.flats.add(cache);
                    this.flatIds.put(cache, slot);
                }
                return node(34, slot, 0, 0, null, 0, 0, 0, 0);
            }
            if (!this.point && this.slots != null && type == DensityFunctions.Marker.class) {
                // Template: FlatCache reads this chunk's table; Cache2D as for chunks.
                var marker = (DensityFunctions.Marker)f;
                if (marker.type() == DensityFunctions.Marker.Type.FlatCache) return node(34, this.slots.slot(marker), 0, 0, null, 0, 0, 0, 0);
                if (marker.type() != DensityFunctions.Marker.Type.Cache2D) return -1;
                int a = add(marker.wrapped(), depth + 1);
                return a < 0 ? -1 : node(35, a, 0, 0, null, 0, 0, 0, 0);
            }
            if (this.point && type == DensityFunctions.Marker.class) {
                var marker = (DensityFunctions.Marker)f;
                int a = add(marker.wrapped(), depth + 1);
                if (a < 0) return -1;
                if (this.flatPoints && marker.type() == DensityFunctions.Marker.Type.FlatCache) {
                    int slot = this.slots != null ? this.slots.slot(marker) : this.flatPointSlots++;
                    return node(37, a, slot, 0, null, 0, 0, 0, 0);
                }
                // FlatCache and Cache2D: the value of a Y-independent input.
                return node(35, a, 0, 0, null, 0, 0, 0, 0);
            }
            if (!this.point && type == NoiseChunk.Cache2D.class) {
                // Rust accepts it only over a Y-independent input.
                int a = add(((NoiseChunk.Cache2D)f).wrapped(), depth + 1);
                return a < 0 ? -1 : node(35, a, 0, 0, null, 0, 0, 0, 0);
            }
            return -1;
        }

        private int node(int op, int a, int b, int c, @Nullable NativeNoiseState state, double p, double q, double r, double s) {
            int id = this.nodes.size() / 5;
            if (id >= MAX_NODES) return -1;
            int stateIndex = -1;
            if (state != null) {
                Integer known = this.stateIds.get(state);
                if (known == null) {
                    known = this.states.size();
                    this.states.add(state);
                    this.stateIds.put(state, known);
                }
                stateIndex = known;
            }
            this.nodes.addElements(this.nodes.size(), new int[]{op, a, b, c, stateIndex});
            this.params.addElements(this.params.size(), new double[]{p, q, r, s});
            return id;
        }

        @Nullable
        private static NativeNoiseState noise(DensityFunction.NoiseHolder holder) {
            NormalNoise noise = holder.noise();
            return noise == null ? null : noise.nativeState();
        }

        /** One spline table with up to four coordinate nodes, in the layout of
         * the existing spline programs: children precede parents. */
        private int spline(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> root, int depth) {
            int nodeStart = this.splineNodes.size() / 4, knotStart = this.splineKnots.size() / 3;
            int[] axes = new int[4];
            int[] axisCount = {0};
            var seen = new IdentityHashMap<CubicSpline<?, ?>, Integer>();
            if (splineNode(root, nodeStart, knotStart, axes, axisCount, seen, depth, 0) < 0) return -1;
            int index = this.splineHeaders.size() / 9;
            this.splineHeaders.addElements(this.splineHeaders.size(), new int[]{nodeStart, this.splineNodes.size() / 4 - nodeStart,
                knotStart, this.splineKnots.size() / 3 - knotStart, axisCount[0], axes[0], axes[1], axes[2], axes[3]});
            return index;
        }

        private int splineNode(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline, int nodeStart, int knotStart,
                               int[] axes, int[] axisCount, IdentityHashMap<CubicSpline<?, ?>, Integer> seen, int depth, int level) {
            if (level > 64 || this.splineNodes.size() / 4 - nodeStart >= 4096 || this.splineKnots.size() / 3 - knotStart >= 16384) return -1;
            Integer known = seen.get(spline);
            if (known != null) return known;
            int axis = -1, start = 0, count = 0;
            float value = 0;
            if (spline instanceof CubicSpline.Constant<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> constant) {
                value = constant.value();
            } else if (spline instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> multi) {
                Holder<DensityFunction> holder = multi.coordinate().function();
                if (!holder.isBound()) return -1;
                int coordinate = add(holder.value(), depth + 1);
                if (coordinate < 0) return -1;
                for (int index = 0; index < axisCount[0]; index++) if (axes[index] == coordinate) axis = index;
                if (axis < 0) {
                    if (axisCount[0] == 4) return -1;
                    axis = axisCount[0]++;
                    axes[axis] = coordinate;
                }
                count = multi.locations().length;
                if (count == 0 || count != multi.derivatives().length || count != multi.values().size()) return -1;
                int[] children = new int[count];
                for (int index = 0; index < count; index++) {
                    children[index] = splineNode(multi.values().get(index), nodeStart, knotStart, axes, axisCount, seen, depth, level + 1);
                    if (children[index] < 0) return -1;
                }
                start = this.splineKnots.size() / 3 - knotStart;
                for (int index = 0; index < count; index++) {
                    this.splineKnots.addElements(this.splineKnots.size(), new int[]{Float.floatToRawIntBits(multi.locations()[index]),
                        Float.floatToRawIntBits(multi.derivatives()[index]), children[index]});
                }
            } else {
                return -1;
            }
            int id = this.splineNodes.size() / 4 - nodeStart;
            this.splineNodes.addElements(this.splineNodes.size(), new int[]{axis, start, count, Float.floatToRawIntBits(value)});
            seen.put(spline, id);
            return id;
        }

        /** True if evaluating {@code f} with the chunk as context cannot read a
         * CacheOnce the router replaced. Interpolators are leaves (cell
         * caches interpolate them); unknown functions are assumed to read one. */
        boolean cannotObserve(DensityFunction f, Set<Object> visited, int depth) {
            if (f == null || depth > MAX_DEPTH) return false;
            f = NativeDensity.unwrap(f);
            if (!visited.add(f)) return true;
            if (this.cacheOnce.contains(f)) return false;
            Class<?> type = f.getClass();
            if (type == NoiseChunk.NoiseInterpolator.class || f instanceof DensityFunctions.BeardifierOrMarker
                || type == DensityFunctions.Constant.class || type == DensityFunctions.Noise.class || type == DensityFunctions.Shift.class
                || type == DensityFunctions.ShiftA.class || type == DensityFunctions.ShiftB.class || type == DensityFunctions.YClampedGradient.class
                || type == BlendedNoise.class || type == DensityFunctions.EndIslandDensityFunction.class
                || f == DensityFunctions.BlendAlpha.INSTANCE || f == DensityFunctions.BlendOffset.INSTANCE) {
                return true;
            }
            if (f instanceof DensityFunctions.MarkerOrMarked marked && (type == NoiseChunk.CacheOnce.class || type == NoiseChunk.Cache2D.class
                || type == NoiseChunk.FlatCache.class || type == NoiseChunk.CacheAllInCell.class || type == DensityFunctions.Marker.class)) {
                return cannotObserve(marked.wrapped(), visited, depth + 1);
            }
            if (type == DensityFunctions.HolderHolder.class) {
                var holder = ((DensityFunctions.HolderHolder)f).function();
                return holder.isBound() && cannotObserve(holder.value(), visited, depth + 1);
            }
            if (type == DensityFunctions.Ap2.class) {
                var n = (DensityFunctions.Ap2)f;
                return cannotObserve(n.argument1(), visited, depth + 1) && cannotObserve(n.argument2(), visited, depth + 1);
            }
            if (type == DensityFunctions.RangeChoice.class) {
                var n = (DensityFunctions.RangeChoice)f;
                return cannotObserve(n.input(), visited, depth + 1) && cannotObserve(n.whenInRange(), visited, depth + 1)
                    && cannotObserve(n.whenOutOfRange(), visited, depth + 1);
            }
            if (type == DensityFunctions.ShiftedNoise.class) {
                var n = (DensityFunctions.ShiftedNoise)f;
                return cannotObserve(n.shiftX(), visited, depth + 1) && cannotObserve(n.shiftY(), visited, depth + 1)
                    && cannotObserve(n.shiftZ(), visited, depth + 1);
            }
            if (type == DensityFunctions.MulOrAdd.class) return cannotObserve(((DensityFunctions.MulOrAdd)f).input(), visited, depth + 1);
            if (type == DensityFunctions.Mapped.class) return cannotObserve(((DensityFunctions.Mapped)f).input(), visited, depth + 1);
            if (type == DensityFunctions.Clamp.class) return cannotObserve(((DensityFunctions.Clamp)f).input(), visited, depth + 1);
            if (type == DensityFunctions.WeirdScaledSampler.class) return cannotObserve(((DensityFunctions.WeirdScaledSampler)f).input(), visited, depth + 1);
            if (type == DensityFunctions.BlendDensity.class) return cannotObserve(((DensityFunctions.BlendDensity)f).input(), visited, depth + 1);
            if (type == DensityFunctions.Spline.class) return splineCannotObserve(((DensityFunctions.Spline)f).spline(), visited, depth + 1);
            return false;
        }

        private boolean splineCannotObserve(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline,
                                            Set<Object> visited, int depth) {
            if (depth > MAX_DEPTH) return false;
            if (!visited.add(spline) || spline instanceof CubicSpline.Constant<?, ?>) return true;
            if (!(spline instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> multi)) return false;
            var holder = multi.coordinate().function();
            if (!holder.isBound() || !cannotObserve(holder.value(), visited, depth + 1)) return false;
            for (var child : multi.values()) if (!splineCannotObserve(child, visited, depth + 1)) return false;
            return true;
        }
    }
}
