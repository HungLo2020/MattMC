package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.biome.Biome;

/** Authoritative native biome storage; Java retains scoped CPU views only. */
final class NativeLiveBiomeSection<T> {
    static final class Binding<T> {
        final List<T> values;
        final IdentityHashMap<T, Integer> ids = new IdentityHashMap<>();
        final int[] sky;
        Binding(List<T> values, int[] sky) {
            this.values = List.copyOf(values); this.sky = sky;
            for (int i = 0; i < values.size(); i++) ids.put(values.get(i), i);
        }
        int id(Object value) { return ids.getOrDefault(value, -1); }
    }
    private static final Map<Strategy<?>, Binding<?>> BINDINGS = Collections.synchronizedMap(new WeakHashMap<>());
    static void register(Strategy<Holder<Biome>> strategy, Registry<Biome> registry) {
        if (registry.getClass() != net.minecraft.core.MappedRegistry.class
                || !((net.minecraft.core.MappedRegistry<Biome>)registry).isFrozen()
                || registry.size() < 9 || registry.size() > 65535) return;
        var values = new ArrayList<Holder<Biome>>(registry.size());
        int[] sky = new int[registry.size()];
        for (int i = 0; i < sky.length; i++) {
            var holder = strategy.globalMap().byId(i);
            if (holder == null || !(holder instanceof Holder.Reference<Biome>)
                    || strategy.globalMap().getId(holder) != i) return;
            values.add(holder); sky[i] = holder.value().getSkyColor();
        }
        BINDINGS.put(strategy, new Binding<>(values, sky));
    }
    @SuppressWarnings("unchecked")
    static <T> Binding<T> binding(Strategy<T> strategy) { return (Binding<T>) BINDINGS.get(strategy); }
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_live_biome_" + name, descriptor);
    }
    private static final MethodHandle CREATE = handle("create", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle COPY = handle("copy", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle VIEW = handle("view", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle VIEW_RELEASE = handle("view_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle WRITE = handle("write", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle SINGLE = handle("read_single", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle INVALIDATE = handle("invalidate", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle EXPORT = handle("export", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final VarHandle LONG = ValueLayout.JAVA_LONG.varHandle(), INT = ValueLayout.JAVA_INT.varHandle();
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private static final class Scratch {
        final Arena arena = Arena.ofAuto();
        final MemorySegment words = arena.allocate(128, 8), palette = arena.allocate(32, 4), header = arena.allocate(48, 8);
    }
    final Binding<T> binding;
    final MemorySegment owner;
    private volatile View view;
    private SingletonAliases<T> singleton;
    /** Only compatibility escape allocates a shared Java palette. Weak members
     * do not keep abandoned native owners alive. Normal state stays in Rust. */
    private static final class SingletonAliases<T> {
        private final ArrayList<WeakReference<NativeLiveBiomeSection<T>>> members = new ArrayList<>();
        private volatile SingleValuePalette<T> escaped;
        synchronized void add(NativeLiveBiomeSection<T> section) {
            members.removeIf(reference -> { var member = reference.get(); return member == null || member.singleton != this; });
            members.add(new WeakReference<>(section));
        }
        boolean escaped() { return escaped != null; }
        SingleValuePalette<T> palette() { return escaped; }
        synchronized SingleValuePalette<T> transfer(T value) {
            if (escaped == null) {
                escaped = new SingleValuePalette<>(List.of(value));
                for (var reference : members) {
                    var member = reference.get();
                    if (member != null && member.singleton == this) member.invalidate();
                }
                members.clear();
            }
            return escaped;
        }
    }
    private NativeLiveBiomeSection(MemorySegment pointer, Binding<T> binding) {
        this(pointer, binding, null);
    }
    private NativeLiveBiomeSection(MemorySegment pointer, Binding<T> binding, SingletonAliases<T> aliases) {
        try { this.owner = pointer.asReadOnly().reinterpret(1, Arena.ofAuto(), p -> release(RELEASE, p)); }
        catch (Throwable failure) { release(RELEASE, pointer); throw failure; }
        this.binding = binding; this.view = loadView();
        if (view.bits == 0) {
            singleton = aliases == null ? new SingletonAliases<>() : aliases;
            singleton.add(this);
        }
    }
    private static void release(MethodHandle method, MemorySegment pointer) {
        try { method.invokeExact(pointer); }
        catch (Throwable failure) { throw failure(failure); }
    }
    static <T> NativeLiveBiomeSection<T> adopt(Strategy<T> strategy, PalettedContainer.Data<T> data) {
        var binding = binding(strategy); if (binding == null) return null;
        var storage = data.storage(); var palette = data.palette();
        if ((storage.getClass() != SimpleBitStorage.class && storage.getClass() != ZeroBitStorage.class)
                || storage.getSize() != 64) return null;
        boolean global = palette == strategy.globalPalette();
        if (!global && palette.getClass() != SingleValuePalette.class && palette.getClass() != LinearPalette.class) return null;
        int count = global ? 0 : palette.getSize();
        if (!global && (count < 1 || count > 8)) return null;
        var scratch = SCRATCH.get();
        for (int i = 0; i < count; i++) {
            int id;
            try { id = binding.id(palette.valueFor(i)); }
            catch (MissingPaletteEntryException | IllegalStateException malformed) { return null; }
            if (id < 0) return null;
            scratch.palette.setAtIndex(ValueLayout.JAVA_INT, i, id);
        }
        long[] words = storage.getRaw(); if (words.length > 16) return null;
        MemorySegment.copy(MemorySegment.ofArray(words), 0, scratch.words, 0, words.length * 8L);
        try {
            var pointer = (MemorySegment) CREATE.invokeExact(scratch.words, words.length, storage.getBits(),
                data.configuration().bitsInStorage(), scratch.palette, count, binding.values.size(),
                strategy.getConfigurationForBitCount(32).bitsInMemory());
            return pointer.address() == 0 ? null : new NativeLiveBiomeSection<>(pointer, binding);
        } catch (Throwable failure) { throw failure(failure); }
    }
    private record View(int bits, int requested, boolean global, MemorySegment words,
                        MemorySegment palette, MemorySegment count, MemorySegment pin) {
        int id(int index) {
            org.apache.commons.lang3.Validate.inclusiveBetween(0L, 63L, index);
            if (bits == 0) return (int) INT.getAcquire(palette, 0L);
            int per = 64 / bits;
            long word = (long) LONG.getAcquire(words, index / per * 8L);
            int id = (int) ((word >>> (index % per * bits)) & ((1L << bits) - 1));
            return global ? id : (int) INT.getAcquire(palette, id * 4L);
        }
    }
    private View loadView() {
        var h = SCRATCH.get().header; MemorySegment lease = MemorySegment.NULL; boolean adopted = false;
        try {
            if ((int) VIEW.invokeExact(owner, h) != 0) throw new IllegalStateException("Invalid native biome view");
            lease = h.getAtIndex(ValueLayout.ADDRESS, 5); var arena = Arena.ofAuto();
            int bits = h.getAtIndex(ValueLayout.JAVA_INT, 0), requested = h.getAtIndex(ValueLayout.JAVA_INT, 1);
            boolean global = h.getAtIndex(ValueLayout.JAVA_INT, 2) != 0;
            var words = h.getAtIndex(ValueLayout.ADDRESS, 2).asReadOnly().reinterpret(h.getAtIndex(ValueLayout.JAVA_INT, 3) * 8L, arena, null);
            var palette = h.getAtIndex(ValueLayout.ADDRESS, 3).asReadOnly().reinterpret((global ? 0 : bits == 0 ? 1 : 1 << bits) * 4L, arena, null);
            var count = h.getAtIndex(ValueLayout.ADDRESS, 4).asReadOnly().reinterpret(4, arena, null);
            // Register one cleanup owner before allocating the final Java projection.
            var pin = lease.asReadOnly().reinterpret(1, arena, p -> release(VIEW_RELEASE, p));
            adopted = true; return new View(bits, requested, global, words, palette, count, pin);
        } catch (Throwable failure) { throw failure(failure); }
        finally { if (!adopted && lease.address() != 0) release(VIEW_RELEASE, lease); Reference.reachabilityFence(this); }
    }
    T get(int index) { var v = view; try { return binding.values.get(v.id(index)); } finally { Reference.reachabilityFence(v); } }
    int bits() { return view.bits; }
    int requestedBits() { return view.requested; }
    int id(Object value) { return binding.id(value); }
    T paletteValue(int index) {
        var v = view;
        try {
            if (v.global) { if (index < 0 || index >= binding.values.size()) throw new MissingPaletteEntryException(index); return binding.values.get(index); }
            if (v.bits == 0 && index != 0) throw new IllegalStateException("Missing Palette entry for id " + index + ".");
            if (index < 0 || index >= (int) INT.getAcquire(v.count, 0L)) throw new MissingPaletteEntryException(index);
            return binding.values.get((int) INT.getAcquire(v.palette, index * 4L));
        } finally { Reference.reachabilityFence(v); }
    }
    T write(int index, int id) {
        try {
            long result = (long) WRITE.invokeExact(owner, index, id);
            if (result < 0) throw new IllegalArgumentException("Invalid native biome write");
            if (result >>> 32 != 0) { view = loadView(); if (view.bits != 0) singleton = null; }
            return binding.values.get((int) result);
        } catch (Throwable failure) { throw failure(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    void readSingle(int id) {
        try { if ((int) SINGLE.invokeExact(owner, id) != 0) throw new IllegalArgumentException("Invalid biome singleton"); }
        catch (Throwable failure) { throw failure(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    void invalidate() { try { INVALIDATE.invokeExact(owner); } catch (Throwable failure) { throw failure(failure); } finally { Reference.reachabilityFence(this); } }
    NativeLiveBiomeSection<T> copy() {
        try { return new NativeLiveBiomeSection<>((MemorySegment) COPY.invokeExact(owner), binding, singleton); }
        catch (Throwable failure) { throw failure(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    boolean maybeHas(java.util.function.Predicate<T> predicate) {
        var v = view;
        try {
            if (v.global) return true;
            for (int i = 0; i < (int) INT.getAcquire(v.count, 0L); i++) if (predicate.test(paletteValue(i))) return true;
            return false;
        } finally { Reference.reachabilityFence(v); }
    }
    boolean compatibilityEscaped() { return singleton != null && singleton.escaped(); }
    PalettedContainer.Data<T> legacyForMutation(Strategy<T> strategy) {
        if (singleton != null && singleton.escaped()) {
            // Its native owner was invalidated by a different alias. The shared
            // palette, possibly already mutated by that caller, is authoritative.
            return new PalettedContainer.Data<>(strategy.getConfigurationForBitCount(0),
                new ZeroBitStorage(64), singleton.palette());
        }
        var data = legacy(strategy);
        if (singleton != null) {
            var palette = singleton.transfer(data.palette().valueFor(0));
            return new PalettedContainer.Data<>(data.configuration(), data.storage(), palette);
        }
        invalidate();
        return data;
    }
    PalettedContainer.Data<T> legacy(Strategy<T> strategy) {
        var s = SCRATCH.get();
        try {
            int length = (int) EXPORT.invokeExact(owner, s.words, s.palette, s.header);
            if (length < 0) throw new IllegalStateException("Invalid biome export");
            int bits = s.header.getAtIndex(ValueLayout.JAVA_INT, 0), requested = s.header.getAtIndex(ValueLayout.JAVA_INT, 1);
            int count = s.header.getAtIndex(ValueLayout.JAVA_INT, 3); long[] words = new long[length];
            MemorySegment.copy(s.words, 0, MemorySegment.ofArray(words), 0, length * 8L);
            var values = new ArrayList<T>(count);
            for (int i = 0; i < count; i++) values.add(binding.values.get(s.palette.getAtIndex(ValueLayout.JAVA_INT, i)));
            var config = strategy.getConfigurationForBitCount(requested);
            return new PalettedContainer.Data<>(config, bits == 0 ? new ZeroBitStorage(64) : new SimpleBitStorage(bits, 64, words), config.createPalette(strategy, values));
        } catch (Throwable failure) { throw failure(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    private static RuntimeException failure(Throwable failure) {
        if (failure instanceof RuntimeException exception) return exception;
        if (failure instanceof Error error) throw error;
        return new IllegalStateException("Native biome boundary failed", failure);
    }
}
