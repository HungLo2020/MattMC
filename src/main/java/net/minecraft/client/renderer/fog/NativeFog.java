package net.minecraft.client.renderer.fog;

import java.lang.foreign.Arena;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;
import org.joml.Vector4f;

/** CPU fog policy. Java supplies ordered semantic callbacks and projects mutable hook data. */
public final class NativeFog {
    private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT;
    private static final ValueLayout.OfFloat FLOAT = ValueLayout.JAVA_FLOAT;
    private static final AddressLayout ADDRESS = ValueLayout.ADDRESS;

    private static MethodHandle pure(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_fog_" + name,
            descriptor, Linker.Option.critical(true));
    }

    private static MethodHandle stateful(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_fog_" + name, descriptor);
    }

    private static final MethodHandle CREATE_RAIN = stateful("rain_create", FunctionDescriptor.of(ADDRESS));
    private static final MethodHandle RELEASE_RAIN = stateful("rain_release", FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle UPDATE_RAIN = stateful("rain_update",
        FunctionDescriptor.of(FLOAT, ADDRESS, FLOAT, INT, INT, FLOAT));
    private static final MethodHandle WATER_COLOR = stateful("water_color",
        FunctionDescriptor.of(INT, ValueLayout.JAVA_LONG, INT));
    private static final MethodHandle RESET_WATER = stateful("water_reset", FunctionDescriptor.ofVoid());
    private static final MethodHandle RANGES = pure("ranges",
        FunctionDescriptor.ofVoid(INT, FLOAT, FLOAT, FLOAT, INT, ADDRESS));
    private static final MethodHandle VOID_DARKNESS = pure("void_darkness",
        FunctionDescriptor.of(FLOAT, FLOAT, INT, ValueLayout.JAVA_DOUBLE));
    private static final MethodHandle EFFECT_DARKNESS = pure("effect_darkness",
        FunctionDescriptor.of(FLOAT, INT, FLOAT, FLOAT, INT));
    private static final MethodHandle COLOR = pure("finish_color",
        FunctionDescriptor.ofVoid(INT, INT, FLOAT, FLOAT, FLOAT, ADDRESS));
    private static final MethodHandle AIR = pure("air_color",
        FunctionDescriptor.of(INT, FLOAT, FLOAT, FLOAT, INT, FLOAT, INT, INT, FLOAT, FLOAT));

    private static final class Scratch {
        final float[] values = new float[6];
        final MemorySegment segment = MemorySegment.ofArray(values);
    }
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private NativeFog() {}

    private static RuntimeException failure(Throwable t) {
        if (t instanceof RuntimeException r) return r;
        if (t instanceof Error e) throw e;
        return new IllegalStateException(t);
    }

    /** One retained rain history per atmospheric environment, as in the original producer. */
    public static final class RainState {
        private final Arena scope;
        private final MemorySegment owner;

        public RainState() {
            MemorySegment pointer = MemorySegment.NULL;
            boolean attached = false;
            try {
                pointer = (MemorySegment)CREATE_RAIN.invokeExact();
                scope = Arena.ofAuto();
                owner = pointer.reinterpret(1, scope, NativeFog::releaseRain);
                attached = true;
            } catch (Throwable t) { throw failure(t); }
            finally { if (!attached && pointer.address() != 0) releaseRain(pointer); }
        }

        public float update(float rain, int skyLight, boolean precipitation, float ticks) {
            int present = precipitation ? 1 : 0;
            try { return (float)UPDATE_RAIN.invokeExact(owner, rain, skyLight, present, ticks); }
            catch (Throwable t) { throw failure(t); }
            finally { Reference.reachabilityFence(this); }
        }
    }

    private static void releaseRain(MemorySegment pointer) {
        try { RELEASE_RAIN.invokeExact(pointer); }
        catch (Throwable t) { throw failure(t); }
    }

    public static int waterColor(long now, int biome) {
        try { return (int)WATER_COLOR.invokeExact(now, biome); }
        catch (Throwable t) { throw failure(t); }
    }

    public static void resetWater() {
        try { RESET_WATER.invokeExact(); }
        catch (Throwable t) { throw failure(t); }
    }

    /** Updates only the fields owned by this stage; existing hook-visible values survive. */
    public static void ranges(FogData data, int kind, float distance, float parameter, float cloud, int flags) {
        Scratch scratch = SCRATCH.get();
        float[] values = scratch.values;
        values[0] = data.environmentalStart;
        values[1] = data.renderDistanceStart;
        values[2] = data.environmentalEnd;
        values[3] = data.renderDistanceEnd;
        values[4] = data.skyEnd;
        values[5] = data.cloudEnd;
        try { RANGES.invokeExact(kind, distance, parameter, cloud, flags, scratch.segment); }
        catch (Throwable t) { throw failure(t); }
        data.environmentalStart = values[0];
        data.renderDistanceStart = values[1];
        data.environmentalEnd = values[2];
        data.renderDistanceEnd = values[3];
        data.skyEnd = values[4];
        data.cloudEnd = values[5];
    }

    public static float voidDarkness(float onset, int minY, double cameraY) {
        try { return (float)VOID_DARKNESS.invokeExact(onset, minY, cameraY); }
        catch (Throwable t) { throw failure(t); }
    }

    public static float effectDarkness(int kind, float original, float parameter, boolean ending) {
        int flag = ending ? 1 : 0;
        try { return (float)EFFECT_DARKNESS.invokeExact(kind, original, parameter, flag); }
        catch (Throwable t) { throw failure(t); }
    }

    public static Vector4f finishColor(int base, int kind, float darkness, float world, float vision) {
        Scratch scratch = SCRATCH.get();
        try { COLOR.invokeExact(base, kind, darkness, world, vision, scratch.segment); }
        catch (Throwable t) { throw failure(t); }
        return new Vector4f(scratch.values[0], scratch.values[1], scratch.values[2], scratch.values[3]);
    }

    public static int airColor(float red, float green, float blue, int sunrise, float sunWeight,
            int sky, int distance, float rain, float thunder) {
        try { return (int)AIR.invokeExact(red, green, blue, sunrise, sunWeight, sky, distance, rain, thunder); }
        catch (Throwable t) { throw failure(t); }
    }
}
