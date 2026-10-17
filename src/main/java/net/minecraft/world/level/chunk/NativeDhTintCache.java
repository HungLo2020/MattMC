package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;

/** One block state's cached biome tints, owned by Rust. Render conversion
 * reads and blends them natively; Java publishes resolver results here. */
public final class NativeDhTintCache {
    private static MethodHandle handle(String name, FunctionDescriptor type, Linker.Option... options) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_tint_" + name, type, options);
    }
    private static final MethodHandle CREATE = handle("create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle BIOME = handle("biome_v2", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle BIOME_UPDATE = handle("biome_update", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle BIOME_RELEASE = handle("biome_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle BIND = handle("bind", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle EPOCH = handle("epoch", FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final java.lang.invoke.VarHandle WORD = ValueLayout.JAVA_LONG.varHandle();
    private static final MemorySegment GENERATION = generation();
    private static final MethodHandle PUT = handle("put", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private final MemorySegment owner;
    // Transport projections only: Rust assigns membership and stable cells.
    // No color or averaging policy is cached in Java. Native generation changes
    // invalidate both successful and missing bindings after key/membership edits.
    private record Binding(long generation, MemorySegment cell) {}
    private final java.util.concurrent.ConcurrentHashMap<BiomeIdentity, Binding> bindings = new java.util.concurrent.ConcurrentHashMap<>();
    private static MemorySegment generation() {
        try { return ((MemorySegment)EPOCH.invokeExact()).reinterpret(8).asReadOnly(); }
        catch (Throwable e) { throw new IllegalStateException(e); }
    }
    public NativeDhTintCache(String identity) { owner = create(identity, CREATE, RELEASE, null); }
    /** The Rust cache, for a synchronous native consumer that keeps this object reachable. */
    public MemorySegment stageOwner() { return owner; }
    public static final class BiomeIdentity {
        private volatile String identity;
        private final MemorySegment owner;
        public BiomeIdentity(String identity) { this(identity, java.util.Objects.hash(identity)); }
        public BiomeIdentity(String identity, int hash) { this.identity = identity; owner = create(identity, BIOME, BIOME_RELEASE, hash); }
        public boolean matches(String value) { return identity.equals(value); }
        public MemorySegment stageOwner() { return owner; }
        public void update(String value) {
            if (matches(value)) return;
            try (var arena = Arena.ofConfined()) {
                var units = arena.allocateFrom(ValueLayout.JAVA_CHAR, value.toCharArray());
                if ((int)BIOME_UPDATE.invokeExact(owner, units, value.length()) != 1) throw new IllegalArgumentException("Unsupported DH tint identity");
                identity = value;
            } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
            finally { Reference.reachabilityFence(this); }
        }
    }
    private static MemorySegment create(String text, MethodHandle create, MethodHandle release, Integer hash) {
        MemorySegment pointer = MemorySegment.NULL; boolean adopted = false;
        try (var input = Arena.ofConfined()) {
            var units = input.allocateFrom(ValueLayout.JAVA_CHAR, text.toCharArray());
            pointer = hash == null ? (MemorySegment)create.invokeExact(units, text.length()) : (MemorySegment)create.invokeExact(units, text.length(), (int)hash);
            if (pointer.address() == 0) throw new IllegalArgumentException("Unsupported DH tint identity");
            var result = pointer.reinterpret(1, Arena.ofAuto(), value -> destroy(release, value));
            adopted = true; return result;
        } catch (RuntimeException | Error e) { if (!adopted && pointer.address() != 0) destroy(release, pointer); throw e; }
        catch (Throwable e) { if (!adopted && pointer.address() != 0) destroy(release, pointer); throw new IllegalStateException(e); }
    }
    private static void destroy(MethodHandle method, MemorySegment owner) {
        try { method.invokeExact(owner); } catch (Throwable e) { throw new IllegalStateException(e); }
    }
    /** Bit32 means cached; low32 carries every color including the invalid sentinel. */
    public long get(BiomeIdentity biome) {
        try {
            long generation = (long)WORD.getAcquire(GENERATION, 0L);
            var binding = bindings.get(biome);
            if (binding == null || binding.generation() != generation) {
                var cell = (MemorySegment)BIND.invokeExact(owner, biome.owner);
                binding = new Binding(generation, cell.address() == 0 ? MemorySegment.NULL : cell.reinterpret(8).asReadOnly());
                bindings.put(biome, binding);
            }
            return binding.cell().address() == 0 ? 0L : (long)WORD.getAcquire(binding.cell(), 0L);
        }
        catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(biome); Reference.reachabilityFence(this); }
    }
    public void put(BiomeIdentity biome, int color) {
        try { if ((int)PUT.invokeExact(owner, biome.owner, color) != 1) throw new IllegalStateException("DH tint publication"); }
        catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(biome); Reference.reachabilityFence(this); }
    }
}
