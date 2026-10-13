package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Transitional loaded-chunk events; native storage and color windows stay in Rust. */
public final class NativeBiomeWorld {
    private static final AtomicLong EPOCHS = new AtomicLong(1);
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_live_biome_world_" + name, descriptor);
    }
    private static final MethodHandle CREATE = handle("create_colors", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle REPLACE = handle("replace_inputs", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle COMPATIBILITY = handle("compatibility", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle REMOVE = handle("remove", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle CLEAR = handle("clear", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle RANGE = handle("range", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle SAMPLE = handle("sample", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
        ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));
    private static final MethodHandle SAMPLE_FOG = handle("sample_fog", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
        ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));
    private static final MethodHandle BIOME = handle("biome", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle BIOME_AT_BLOCK = handle("biome_at_block", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final ThreadLocal<MemorySegment> RESULT = ThreadLocal.withInitial(() -> Arena.ofAuto().allocate(24, 8));
    private final NativeLiveBiomeSection.Binding<?> binding;
    private final long epoch;
    private final MemorySegment owner;
    private boolean enabled = true;

    @Nullable
    public static NativeBiomeWorld create(PalettedContainerFactory factory, int minY, int height) {
        var binding = NativeLiveBiomeSection.binding(factory.biomeStrategy());
        long epoch = EPOCHS.getAndIncrement();
        if (binding == null || epoch <= 0 || (minY & 15) != 0 || height <= 0 || (height & 15) != 0) return null;
        int fallback = binding.id(factory.defaultBiome()); if (fallback < 0) return null;
        try (Arena call = Arena.ofConfined()) {
            var colors = call.allocateFrom(ValueLayout.JAVA_INT, binding.sky);
            var fog = call.allocateFrom(ValueLayout.JAVA_INT, binding.fog);
            var pointer = (MemorySegment) CREATE.invokeExact(colors, fog, binding.sky.length, minY >> 2,
                height >> 2, fallback, 65536, epoch);
            if (pointer.address() == 0) return null;
            MemorySegment scoped;
            try { scoped = pointer.asReadOnly().reinterpret(1, Arena.ofAuto(), NativeBiomeWorld::release); }
            catch (Throwable failure) { release(pointer); throw failure; }
            return new NativeBiomeWorld(binding, epoch, scoped);
        } catch (Throwable failure) { throw failure(failure); }
    }
    private NativeBiomeWorld(NativeLiveBiomeSection.Binding<?> binding, long epoch, MemorySegment owner) {
        this.binding = binding; this.epoch = epoch; this.owner = owner;
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); } catch (Throwable failure) { throw failure(failure); }
    }
    public void replace(LevelChunk chunk) {
        if (!enabled) return;
        int x = chunk.getPos().x, z = chunk.getPos().z;
        chunk.getSectionsForRead(); // Preserve custom/escaped getter dispatch before admission.
        NativeBiomeChunkInputs inputs = null;
        if (chunk.getClass() == LevelChunk.class && !chunk.hasEscapedSectionArray()) {
            try { inputs = chunk.nativeBiomeSections(binding); }
            catch (NativeBiomeChunkInputs.CaptureFailure rejected) { disable(); return; }
        }
        try {
            int status = inputs == null ? (int)COMPATIBILITY.invokeExact(owner, epoch, x, z)
                : (int)REPLACE.invokeExact(owner, epoch, x, z, inputs.stageOwner());
            if (status != 0) disable(); // Bounded admission failure must not masquerade as missing terrain.
        } catch (Throwable failure) { throw failure(failure); }
        finally { Reference.reachabilityFence(inputs); Reference.reachabilityFence(this); }
    }

    private void disable() {
        enabled = false;
        try {
            if ((int) CLEAR.invokeExact(owner, epoch) != 0) throw new IllegalStateException("Native biome residency cleanup failed");
        } catch (Throwable failure) { throw failure(failure); }
    }
    public void remove(int x, int z) {
        if (!enabled) return;
        try { if ((int) REMOVE.invokeExact(owner, epoch, x, z) != 0) disable(); }
        catch (Throwable failure) { throw failure(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    public void range(int x, int z, int radius) {
        if (!enabled) return;
        try { if ((int) RANGE.invokeExact(owner, epoch, x, z, radius) != 0) disable(); }
        catch (Throwable failure) { throw failure(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    @Nullable
    public net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome(int x, int y, int z) {
        if (!enabled) return null;
        try { return identity((int) BIOME.invokeExact(owner, epoch, x, y, z)); }
        catch (Throwable failure) { throw failure(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    @Nullable
    public net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biomeAtBlock(long seed, int x, int y, int z) {
        if (!enabled) return null;
        try { return identity((int) BIOME_AT_BLOCK.invokeExact(owner, epoch, seed, x, y, z)); }
        catch (Throwable failure) { throw failure(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    @SuppressWarnings("unchecked")
    private net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> identity(int id) {
        return id < 0 ? null : (net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>) binding.values.get(id);
    }
    @Nullable
    public Vec3 sample(Vec3 position) { return sample(position, SAMPLE); }
    @Nullable
    public Vec3 sampleFog(Vec3 position) { return sample(position, SAMPLE_FOG); }
    private Vec3 sample(Vec3 position, MethodHandle sampler) {
        if (!enabled) return null;
        var result = RESULT.get();
        try {
            if ((int) sampler.invokeExact(owner, epoch, position.x, position.y, position.z, result) != 0) return null;
            return new Vec3(result.getAtIndex(ValueLayout.JAVA_DOUBLE, 0), result.getAtIndex(ValueLayout.JAVA_DOUBLE, 1), result.getAtIndex(ValueLayout.JAVA_DOUBLE, 2));
        } catch (Throwable failure) { throw failure(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    private static RuntimeException failure(Throwable failure) {
        if (failure instanceof RuntimeException exception) return exception;
        if (failure instanceof Error error) throw error;
        return new IllegalStateException("Native world biome boundary failed", failure);
    }
}
