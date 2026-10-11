package net.minecraft.world.level.chunk;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;

/** Temporary semantic bindings; Rust owns canonical cached colors and neighborhoods. */
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
    private static final MethodHandle PLAN = handle("plan", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS), Linker.Option.critical(true));
    private static final MethodHandle BLEND = handle("blend", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle BOUNDED_BLEND = handle("blend_bounded", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS), Linker.Option.critical(true));
    private static final MethodHandle SOURCE = handle("source", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle SOURCE_BOUNDED = handle("source_bounded", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS), Linker.Option.critical(false));
    private static final class Scratch {
        final int[] indices = new int[49];
        final long[] descriptors = new long[98];
        final MemorySegment plan = MemorySegment.ofArray(indices), heapViews = MemorySegment.ofArray(descriptors);
        final Arena arena = Arena.ofAuto();
        final MemorySegment views = arena.allocate(98L * 8, 8), result = arena.allocate(12, 4);
        final NativeDhColumnList[] pins = new NativeDhColumnList[49];
    }
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
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
    /** False preserves the Java callback/failure route, with the position untouched. */
    public boolean tryBlend(BiomeIdentity center, FullDataSourceV2 source, DhBlockPosMutable pos, int minY, int radius) {
        if (source.getClass() != FullDataSourceV2.class || source.mapping.getClass() != com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap.class) return false;
        if (trySourceBlend(center, source, pos, minY, radius)) return true;
        var scratch = SCRATCH.get();
        NativeDhIdMap map = null;
        try {
            int count = (int)PLAN.invokeExact(source.getPos(), pos.getX(), pos.getZ(), radius, scratch.plan);
            if (count <= 0) return false;
            long pointCount = 0;
            for (int i = 0; i < count; i++) {
                int index = scratch.indices[i];
                var list = index < 0 ? null : source.getColumn(index);
                MemorySegment points = MemorySegment.NULL;
                if (list instanceof NativeDhColumnList column) {
                    scratch.pins[i] = column; points = column.stagePoints();
                    if (points.byteSize() / 8 > 4096) return false;
                } else if (list != null && (list.getClass() != LongArrayList.class || !list.isEmpty())) return false;
                scratch.descriptors[i * 2] = points.address();
                scratch.descriptors[i * 2 + 1] = points.byteSize() / 8;
                pointCount += points.byteSize() / 8;
            }
            map = source.mapping.nativeMaterialMap();
            if (map == null) return false;
            int result;
            if (pointCount <= 4096) {
                result = (int)BOUNDED_BLEND.invokeExact(owner, center.owner, map.stageOwner(), source.getPos(), pos.getX(), pos.getY(), pos.getZ(), minY, radius, scratch.heapViews, scratch.result);
                // A bounded decline leaves output untouched. The same prepared
                // views may use the safepoint-capable path before Java callbacks.
                if (result == 0) {
                    MemorySegment.copy(scratch.heapViews, 0, scratch.views, 0, count * 16L);
                    result = (int)BLEND.invokeExact(owner, center.owner, map.stageOwner(), source.getPos(), pos.getX(), pos.getY(), pos.getZ(), minY, radius, scratch.views, scratch.result);
                }
            } else {
                MemorySegment.copy(scratch.heapViews, 0, scratch.views, 0, count * 16L);
                result = (int)BLEND.invokeExact(owner, center.owner, map.stageOwner(), source.getPos(), pos.getX(), pos.getY(), pos.getZ(), minY, radius, scratch.views, scratch.result);
            }
            if (result != 1) return false;
            pos.setX(scratch.result.getAtIndex(ValueLayout.JAVA_INT, 1));
            pos.setZ(scratch.result.getAtIndex(ValueLayout.JAVA_INT, 2));
            return true;
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally {
            Reference.reachabilityFence(scratch.pins); Reference.reachabilityFence(map);
            Reference.reachabilityFence(center); Reference.reachabilityFence(source); Reference.reachabilityFence(this);
            java.util.Arrays.fill(scratch.pins, null);
        }
    }
    private boolean trySourceBlend(BiomeIdentity center, FullDataSourceV2 source, DhBlockPosMutable pos, int minY, int radius) {
        if(!source.nativeSource().isLive())return false;
        var map = source.mapping.nativeMaterialMap();
        if (map == null) return false;
        var table = source.nativeSource();
        var result = SCRATCH.get().result;
        try {
            int status = (int) SOURCE_BOUNDED.invokeExact(owner, center.owner, map.stageOwner(), table.stageOwner(),
                source.getPos(), pos.getX(), pos.getY(), pos.getZ(), minY, radius, result);
            if (status == 0) status = (int) SOURCE.invokeExact(owner, center.owner, map.stageOwner(), table.stageOwner(),
                source.getPos(), pos.getX(), pos.getY(), pos.getZ(), minY, radius, result);
            if (status != 1) return false;
            pos.setX(result.getAtIndex(ValueLayout.JAVA_INT, 1)); pos.setZ(result.getAtIndex(ValueLayout.JAVA_INT, 2));
            return true;
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(map); Reference.reachabilityFence(table); Reference.reachabilityFence(source);
            Reference.reachabilityFence(center); Reference.reachabilityFence(this); }
    }
    /** Valid only immediately following a successful tryBlend on this thread. */
    public int blendedColor() { return SCRATCH.get().result.getAtIndex(ValueLayout.JAVA_INT, 0); }
}
