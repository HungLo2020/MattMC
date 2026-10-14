package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.util.ArrayList;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Rust's authoritative live packed block section; Java keeps only scoped CPU views. */
final class NativeLiveBlockSection {
    private static final Class<?> BLOCK_STRATEGY = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY).getClass();
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_live_section_" + name, descriptor);
    }
    private static final MethodHandle CREATE = handle("create", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle INVALIDATE = handle("invalidate", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle COPY = handle("copy", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle VIEW = handle("view", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle VIEW_RELEASE = handle("view_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle READ_SINGLE = handle("read_single", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle WRITE = handle("write", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle WRITE_COUNTS = handle("write_counts", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle RECOUNT = handle("recount", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle SNAPSHOT = handle("snapshot", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle HISTOGRAM = handle("histogram", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle LIGHT = handle("light", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle EXPORT = handle("export", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final VarHandle LONG = ValueLayout.JAVA_LONG.varHandle(), INT = ValueLayout.JAVA_INT.varHandle();
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private static final class Scratch {
        final Arena arena = Arena.ofAuto();
        final MemorySegment words = arena.allocate(1024 * 8, 8), palette = arena.allocate(257 * 4, 4), header = arena.allocate(48, 8);
    }
    private final MemorySegment owner;
    /** The arena whose scope the owner and its views carry (a slab or release scope), or null for reclaimer owners. */
    @Nullable private final Arena scope;
    private volatile View view;

    static NativeLiveBlockSection adoptOwned(MemorySegment pointer) {
        if (pointer.address() == 0) throw new IllegalStateException("Missing native live section");
        var release = NativeReleaseScope.current();
        if (release != null) {
            // Freed when the release scope closes; later reads throw instead of reading freed memory.
            MemorySegment bound;
            try { bound = pointer.reinterpret(1, release, owner -> release(RELEASE, owner)).asReadOnly(); }
            catch (Throwable failure) { release(RELEASE, pointer); throw failure; }
            return new NativeLiveBlockSection(bound, release);
        }
        MemorySegment scoped;
        // Callers pin the section by holding this segment, so it is the reclaimer's referent.
        try { scoped = pointer.reinterpret(1).asReadOnly(); NativeReclaimer.register(scoped, pointer, NativeReclaimer.LIVE_SECTION); }
        catch (Throwable failure) { release(RELEASE, pointer); throw failure; }
        return new NativeLiveBlockSection(scoped, null);
    }
    private NativeLiveBlockSection(MemorySegment scoped, @Nullable Arena scope) {
        this.owner = scoped;
        this.scope = scope;
        this.view = loadView(null);
    }
    /** A slab item: {@code scoped} carries the slab arena's scope, which keeps the item alive. */
    static NativeLiveBlockSection adoptBorrowed(MemorySegment scoped, Arena arena) { return new NativeLiveBlockSection(scoped.asReadOnly(), arena); }
    /** Arguments of {@code mattmc_live_section_create} for a fresh single-value container,
     * {@code [state, requested bits, registry size, global bits]}, or null when
     * {@link #adopt} would decline it. */
    static int @Nullable [] freshParameters(Strategy<BlockState> strategy, BlockState value) {
        if (strategy.getClass() != BLOCK_STRATEGY || strategy.globalMap() != Block.BLOCK_STATE_REGISTRY
                || Block.BLOCK_STATE_REGISTRY.size() > 65535 || value == null || value.getClass() != BlockState.class) return null;
        var fresh = strategy.getConfigurationForBitCount(0);
        if (fresh.bitsInMemory() != 0) return null;
        int id = Block.getId(value);
        if (Block.BLOCK_STATE_REGISTRY.byId(id) != value) return null;
        return new int[] {id, fresh.bitsInStorage(), Block.BLOCK_STATE_REGISTRY.size(), strategy.getConfigurationForBitCount(32).bitsInMemory()};
    }
    MemorySegment stageOwner() { return this.owner; }
    void invalidate() {
        try { INVALIDATE.invokeExact(this.owner); }
        catch (Throwable failure) { throw new IllegalStateException("Cannot retire native block binding", failure); }
        finally { Reference.reachabilityFence(this); }
    }
    private static void release(MethodHandle handle, MemorySegment pointer) {
        try { handle.invokeExact(pointer); }
        catch (Throwable failure) { throw new IllegalStateException("Cannot release native live section", failure); }
    }
    static <T> @Nullable NativeLiveBlockSection adopt(Strategy<T> strategy, PalettedContainer.Data<T> data) {
        if (strategy.getClass() != BLOCK_STRATEGY || strategy.globalMap() != Block.BLOCK_STATE_REGISTRY
                || Block.BLOCK_STATE_REGISTRY.size() > 65535) return null;
        var storage = data.storage(); var palette = data.palette();
        if ((storage.getClass() != SimpleBitStorage.class && storage.getClass() != ZeroBitStorage.class) || storage.getSize() != 4096) return null;
        boolean global = palette == strategy.globalPalette();
        if (!global && palette.getClass() != SingleValuePalette.class && palette.getClass() != LinearPalette.class && palette.getClass() != HashMapPalette.class) return null;
        int count = global ? 0 : palette.getSize();
        if (!global && (count < 1 || count > 256)) return null;
        var scratch = SCRATCH.get();
        try {
            for (int i = 0; i < count; i++) {
                T state;
                try { state = palette.valueFor(i); }
                catch (MissingPaletteEntryException | IllegalStateException malformed) { return null; }
                if (!(state instanceof BlockState block) || block.getClass() != BlockState.class) return null;
                int id = Block.getId(block);
                if (Block.BLOCK_STATE_REGISTRY.byId(id) != block) return null;
                scratch.palette.setAtIndex(ValueLayout.JAVA_INT, i, id);
            }
            var raw = storage.getRaw();
            if (raw.length > 1024) return null;
            MemorySegment.copy(MemorySegment.ofArray(raw), 0, scratch.words, 0, raw.length * 8L);
            var result = (MemorySegment) CREATE.invokeExact(scratch.words, raw.length, storage.getBits(), data.configuration().bitsInStorage(),
                    scratch.palette, count, Block.BLOCK_STATE_REGISTRY.size(), strategy.getConfigurationForBitCount(32).bitsInMemory());
            return result.address() == 0 ? null : adoptOwned(result);
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot adopt native live section", failure); }
    }
    /**
     * The owner pins its current generation, and each view pins the owner, so
     * a view needs no registration of its own. After growth, Rust returns the
     * replaced generations; {@code previous}, which readers may still hold,
     * keeps them alive.
     */
    private View loadView(@Nullable View previous) {
        var h = SCRATCH.get().header;
        MemorySegment lease = MemorySegment.NULL;
        boolean adopted = false;
        try {
            if ((int) VIEW.invokeExact(this.owner, h) != 0) throw new IllegalStateException("Invalid native section view");
            lease = h.getAtIndex(ValueLayout.ADDRESS, 5);
            int bits = h.getAtIndex(ValueLayout.JAVA_INT, 0), requested = h.getAtIndex(ValueLayout.JAVA_INT, 1);
            boolean global = h.getAtIndex(ValueLayout.JAVA_INT, 2) != 0;
            var words = viewSegment(h.getAtIndex(ValueLayout.ADDRESS, 2), h.getAtIndex(ValueLayout.JAVA_INT, 3) * 8L);
            var palette = viewSegment(h.getAtIndex(ValueLayout.ADDRESS, 3), (global ? 0 : bits == 0 ? 1 : (1 << bits) + (bits >= 5 ? 1 : 0)) * 4L);
            var count = viewSegment(h.getAtIndex(ValueLayout.ADDRESS, 4), 4);
            var result = new View(this.owner, bits, requested, global, words, palette, count);
            if (lease.address() != 0) NativeReclaimer.register(previous != null ? previous : result, lease, NativeReclaimer.LIVE_SECTION_VIEW);
            adopted = true;
            return result;
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot view native live section", failure); }
        finally { if (!adopted && lease.address() != 0) release(VIEW_RELEASE, lease); Reference.reachabilityFence(this); }
    }
    /** A view span carrying the owner's scope, so a closed slab or release scope cannot be read through it. */
    private MemorySegment viewSegment(MemorySegment address, long size) {
        return (this.scope == null ? address.reinterpret(size) : address.reinterpret(size, this.scope, null)).asReadOnly();
    }
    static final class View {
        final int bits, requested, per, mul, add, shift;
        final boolean global;
        final long mask;
        final MemorySegment words, palette, count;
        /** The owner segment: a reachable view keeps the owner, and so its generation, alive. */
        @SuppressWarnings("unused") private final MemorySegment pin;
        View(MemorySegment pin, int bits, int requested, boolean global, MemorySegment words, MemorySegment palette, MemorySegment count) {
            this.pin = pin;
            this.bits = bits; this.requested = requested; this.global = global; this.words = words; this.palette = palette; this.count = count;
            this.per = bits == 0 ? 0 : 64 / bits; this.mask = (1L << bits) - 1;
            int[] params = bits == 0 ? new int[3] : SimpleBitStorage.nativeCellParameters(bits);
            this.mul = params[0]; this.add = params[1]; this.shift = params[2];
        }
        int stateId(int index) {
            if (index < 0 || index >= 4096) org.apache.commons.lang3.Validate.inclusiveBetween(0L, 4095L, index);
            if (bits == 0) return (int)INT.getAcquire(palette, 0L);
            int cell = (int)((index * Integer.toUnsignedLong(mul) + Integer.toUnsignedLong(add)) >>> 32 >>> shift);
            long word = (long)LONG.getAcquire(words, cell * 8L);
            int id = (int)((word >>> ((index - cell * per) * bits)) & mask);
            return global ? id : (int)INT.getAcquire(palette, id * 4L);
        }
        BlockState paletteValue(int id) {
            if (global) {
                var state = Block.BLOCK_STATE_REGISTRY.byId(id);
                if (state == null) throw new MissingPaletteEntryException(id);
                return state;
            }
            if (bits == 0 && id != 0) throw new IllegalStateException("Missing Palette entry for id " + id + ".");
            if (id < 0 || id >= (int)INT.getAcquire(count, 0L)) throw new MissingPaletteEntryException(id);
            return Block.BLOCK_STATE_REGISTRY.byId((int)INT.getAcquire(palette, id * 4L));
        }
        BlockState state(int index) { return Block.BLOCK_STATE_REGISTRY.byId(stateId(index)); }
    }
    BlockState get(int index) { var v = this.view; try { return v.state(index); } finally { Reference.reachabilityFence(v); } }
    BlockState paletteValue(int id) {
        var v = this.view;
        try { return v.paletteValue(id); } finally { Reference.reachabilityFence(v); }
    }
    boolean maybeHas(java.util.function.Predicate<BlockState> predicate) {
        var v = this.view;
        try {
            if (v.global) return true; // GlobalPalette's maybeHas never calls the predicate.
            for (int i = 0; i < (int)INT.getAcquire(v.count, 0L); i++) if (predicate.test(v.paletteValue(i))) return true;
            return false;
        } finally { Reference.reachabilityFence(v); }
    }
    int bits() { return view.bits; }
    /** After a native write grew the palette: Java's view must follow the owner's new generation. */
    void reloadViewAfterNativeWrite() {
        var current = this.view;
        try { this.view = loadView(current); }
        finally { Reference.reachabilityFence(current); Reference.reachabilityFence(this); }
    }
    int requestedBits() { return view.requested; }
    boolean isSingle(Object value) { return view.bits == 0 && get(0) == value; }
    int write(int index, int id) {
        var current = this.view;
        try {
            // An admitted state already at this position needs no palette/storage mutation.
            int old = current.stateId(index);
            if (old == id) return old;
            long result = (long)WRITE.invokeExact(this.owner, index, id);
            if (result < 0) throw new IllegalArgumentException("Invalid native section write");
            if ((result >>> 32) != 0) this.view = loadView(current);
            return (int)result;
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot mutate native live section", failure); }
        finally { Reference.reachabilityFence(current); Reference.reachabilityFence(this); }
    }
    int writeWithCounters(int index, int id, NativeSectionCounters counters) {
        var current = this.view;
        try {
            // Exact admitted BlockStates expose cached facts. Replacing the
            // same object has zero net short deltas, even for stale counters.
            if (current.stateId(index) == id) return id;
            if (!net.minecraft.world.level.block.NativeBlockRegistry.ready()) return -1;
            long result = (long)WRITE_COUNTS.invokeExact(this.owner, counters.owner(), index, id);
            if (result == -2) return -1;
            if (result < 0) throw new IllegalArgumentException("Invalid native section/count write");
            if ((result >>> 32) != 0) this.view = loadView(current);
            return (int)result;
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot mutate native section counters", failure); }
        finally { Reference.reachabilityFence(current); Reference.reachabilityFence(counters); Reference.reachabilityFence(this); }
    }
    boolean recount(NativeSectionCounters counters) {
        if (!net.minecraft.world.level.block.NativeBlockRegistry.ready()) return false;
        try { return (int)RECOUNT.invokeExact(this.owner, counters.owner()) == 1; }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot recount native section", failure); }
        finally { Reference.reachabilityFence(counters); Reference.reachabilityFence(this); }
    }
    void readSingle(int id) {
        try {
            if ((int)READ_SINGLE.invokeExact(this.owner, id) != 0) throw new IllegalStateException("Invalid native single-palette read");
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot read native single palette", failure); }
        finally { Reference.reachabilityFence(this); }
    }
    NativeLiveBlockSection copy() {
        try { return adoptOwned((MemorySegment)COPY.invokeExact(this.owner)); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot copy native live section", failure); }
        finally { Reference.reachabilityFence(this); }
    }
    NativeBlockSectionSnapshot snapshot(@Nullable Arena arena) {
        try { return NativeBlockSectionSnapshot.adopt((MemorySegment)SNAPSHOT.invokeExact(this.owner), arena); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot snapshot native live section", failure); }
        finally { Reference.reachabilityFence(this); }
    }
    int paletteSize() { var v = this.view; try { return v.global ? Block.BLOCK_STATE_REGISTRY.size() : (int)INT.getAcquire(v.count, 0L); } finally { Reference.reachabilityFence(v); } }
    int histogram(MemorySegment workspace, int length, MemorySegment output) {
        try { return (int)HISTOGRAM.invokeExact(this.owner, workspace, length, output, 4096); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot count native live section", failure); }
        finally { Reference.reachabilityFence(this); }
    }
    int light(MemorySegment buffer) {
        try { return (int)LIGHT.invokeExact(this.owner, buffer, (int)buffer.byteSize()); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot export native light states", failure); }
        finally { Reference.reachabilityFence(this); }
    }
    // [bits, requested, global, count], then words and canonical palette IDs.
    int export(MemorySegment words, MemorySegment palette, MemorySegment header) {
        if (words.byteSize() < 1024 * 8 || palette.byteSize() < 257 * 4 || header.byteSize() < 16) throw new IllegalArgumentException("Short section export buffer");
        try { return (int)EXPORT.invokeExact(this.owner, words, palette, header); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot export native live section", failure); }
        finally { Reference.reachabilityFence(this); }
    }
    /** Temporary compatibility projection; never installed beside the live owner. */
    @SuppressWarnings("unchecked")
    <T> PalettedContainer.Data<T> legacy(Strategy<T> strategy) {
        var scratch = SCRATCH.get(); int length = export(scratch.words, scratch.palette, scratch.header);
        int bits = scratch.header.getAtIndex(ValueLayout.JAVA_INT, 0), requested = scratch.header.getAtIndex(ValueLayout.JAVA_INT, 1);
        int count = scratch.header.getAtIndex(ValueLayout.JAVA_INT, 3);
        long[] raw = new long[length]; MemorySegment.copy(scratch.words, 0, MemorySegment.ofArray(raw), 0, length * 8L);
        var entries = new ArrayList<T>(count);
        for (int i = 0; i < count; i++) entries.add((T)Block.BLOCK_STATE_REGISTRY.byId(scratch.palette.getAtIndex(ValueLayout.JAVA_INT, i)));
        var config = strategy.getConfigurationForBitCount(requested);
        return new PalettedContainer.Data<>(config, bits == 0 ? new ZeroBitStorage(4096) : new SimpleBitStorage(bits, 4096, raw), config.createPalette(strategy, entries));
    }
}
