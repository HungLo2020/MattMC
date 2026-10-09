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
    private static final MethodHandle COPY = handle("copy", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle VIEW = handle("view", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle VIEW_RELEASE = handle("view_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle READ_SINGLE = handle("read_single", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle WRITE = handle("write", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
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
    private volatile View view;

    private NativeLiveBlockSection(MemorySegment pointer) {
        MemorySegment scoped;
        try { scoped = pointer.asReadOnly().reinterpret(1, Arena.ofAuto(), p -> release(RELEASE, p)); }
        catch (Throwable failure) { release(RELEASE, pointer); throw failure; }
        this.owner = scoped;
        this.view = loadView();
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
            return result.address() == 0 ? null : new NativeLiveBlockSection(result);
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot adopt native live section", failure); }
    }
    private View loadView() {
        var h = SCRATCH.get().header;
        MemorySegment lease = MemorySegment.NULL;
        boolean adopted = false;
        try {
            if ((int) VIEW.invokeExact(this.owner, h) != 0) throw new IllegalStateException("Invalid native section view");
            lease = h.getAtIndex(ValueLayout.ADDRESS, 5);
            var arena = Arena.ofAuto();
            int bits = h.getAtIndex(ValueLayout.JAVA_INT, 0), requested = h.getAtIndex(ValueLayout.JAVA_INT, 1);
            boolean global = h.getAtIndex(ValueLayout.JAVA_INT, 2) != 0;
            var words = h.getAtIndex(ValueLayout.ADDRESS, 2).asReadOnly().reinterpret(h.getAtIndex(ValueLayout.JAVA_INT, 3) * 8L, arena, null);
            var palette = h.getAtIndex(ValueLayout.ADDRESS, 3).asReadOnly().reinterpret((global ? 0 : bits == 0 ? 1 : (1 << bits) + (bits >= 5 ? 1 : 0)) * 4L, arena, null);
            var count = h.getAtIndex(ValueLayout.ADDRESS, 4).asReadOnly().reinterpret(4, arena, null);
            // Build every allocating projection before registering the single cleanup owner.
            var result = new View(bits, requested, global, words, palette, count);
            result.pin = lease.asReadOnly().reinterpret(1, arena, p -> release(VIEW_RELEASE, p));
            adopted = true;
            return result;
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot view native live section", failure); }
        finally { if (!adopted && lease.address() != 0) release(VIEW_RELEASE, lease); Reference.reachabilityFence(this); }
    }
    static final class View {
        final int bits, requested, per, mul, add, shift;
        final boolean global;
        final long mask;
        final MemorySegment words, palette, count;
        private MemorySegment pin;
        View(int bits, int requested, boolean global, MemorySegment words, MemorySegment palette, MemorySegment count) {
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
            if ((result >>> 32) != 0) this.view = loadView();
            return (int)result;
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot mutate native live section", failure); }
        finally { Reference.reachabilityFence(current); Reference.reachabilityFence(this); }
    }
    void readSingle(int id) {
        try {
            if ((int)READ_SINGLE.invokeExact(this.owner, id) != 0) throw new IllegalStateException("Invalid native single-palette read");
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot read native single palette", failure); }
        finally { Reference.reachabilityFence(this); }
    }
    NativeLiveBlockSection copy() {
        try { return new NativeLiveBlockSection((MemorySegment)COPY.invokeExact(this.owner)); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot copy native live section", failure); }
        finally { Reference.reachabilityFence(this); }
    }
    NativeBlockSectionSnapshot snapshot(Arena arena) {
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
