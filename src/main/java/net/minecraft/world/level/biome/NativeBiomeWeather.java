package net.minecraft.world.level.biome;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/** VM observations for the shared native weather policy and temperature child. */
final class NativeBiomeWeather {
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_biome_weather_" + name, descriptor);
    }
    private static final MethodHandle FRAME = handle("frame", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle BIND = handle("bind", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle NEXT = handle("next", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle NOISE = handle("noise", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle STATS = handle("statistics", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

    static void bind(long driver, long cache, int modifier) {
        try {
            int result = (int)BIND.invokeExact(driver, cache, modifier);
            if (result != 0) throw new IllegalStateException("Invalid native weather temperature binding");
        } catch (Throwable failure) { NativeBiomeWeather.<RuntimeException,Object>raise(failure); }
    }
    static int evaluate(Biome biome, BlockPos blockPos, LevelReader levelReader, int mode, int seaLevel, boolean edge) {
        long driver = 0;
        try {
            driver = (long)FRAME.invokeExact(mode, seaLevel, edge ? 1 : 0);
            if (driver == 0) throw new OutOfMemoryError("Native biome weather invocation allocation failed");
            return run(driver, biome, blockPos, levelReader);
        } catch (Throwable failure) { return raise(failure); }
        finally {
            if (driver != 0) {
                try { RELEASE.invokeExact(driver); }
                catch (Throwable failure) { NativeBiomeWeather.<RuntimeException,Object>raise(failure); }
            }
        }
    }
    private static int run(long driver, Biome biome, BlockPos blockPos, LevelReader levelReader) throws Throwable {
        MemorySegment words = MemorySegment.ofAddress(driver).reinterpret(32).asReadOnly();
        long response = 0;
        int failed = 0;
        Throwable original = null;
        float base = 0;
        BlockState blockState = null;
        FluidState fluidState = null;
        for (;;) {
            int status = (int)NEXT.invokeExact(driver, response, failed);
            if (status != 1) {
                if (original != null) throw original;
                if (status != 0) throw new IllegalStateException("Invalid native biome weather continuation");
                return (int)words.get(ValueLayout.JAVA_LONG, 0);
            }
            int command = (int)words.get(ValueLayout.JAVA_LONG, 0);
            int argument = (int)words.get(ValueLayout.JAVA_LONG, 8);
            response = 0;
            try {
                switch (command) {
                    case 1: base = biome.getBaseTemperature(); response = Float.floatToRawIntBits(base) & 0xffffffffL; break;
                    case 2: response = blockPos.getY(); break;
                    case 3: response = blockPos.getX(); break;
                    case 4: response = blockPos.getZ(); break;
                    case 5: {
                        var snapshot = Biome.temperatureNoise(argument);
                        MemorySegment state = snapshot.state();
                        int result;
                        try { result = (int)NOISE.invokeExact(driver, state, state.byteSize()); }
                        finally { Reference.reachabilityFence(snapshot); }
                        if (result != 0) throw new IllegalStateException("Invalid native weather noise binding: " + result);
                        break;
                    }
                    case 6: biome.invokeTemperatureModifier(blockPos, base); break;
                    case 100: response = levelReader.getSeaLevel(); break;
                    case 101: response = biome.hasPrecipitation() ? 1 : 0; break;
                    case 102: response = blockPos.asLong(); break;
                    case 103: biome.bindWeatherTemperature(driver); break;
                    case 104: response = blockPos.getY(); break;
                    case 105: response = levelReader.isInsideBuildHeight(argument) ? 1 : 0; break;
                    case 106: response = levelReader.getBrightness(LightLayer.BLOCK, blockPos); break;
                    case 107: blockState = levelReader.getBlockState(blockPos); break;
                    case 108: fluidState = levelReader.getFluidState(blockPos); break;
                    case 109: response = fluidState.getType() == Fluids.WATER ? 1 : 0; break;
                    case 110: response = blockState.getBlock() instanceof LiquidBlock ? 1 : 0; break;
                    case 111: response = blockState.isAir() ? 1 : 0; break;
                    case 112: response = blockState.is(Blocks.SNOW) ? 1 : 0; break;
                    case 113: response = Blocks.SNOW.defaultBlockState().canSurvive(levelReader, blockPos) ? 1 : 0; break;
                    case 120: response = levelReader.isWaterAt(blockPos.west()) ? 1 : 0; break;
                    case 121: response = levelReader.isWaterAt(blockPos.east()) ? 1 : 0; break;
                    case 122: response = levelReader.isWaterAt(blockPos.north()) ? 1 : 0; break;
                    case 123: response = levelReader.isWaterAt(blockPos.south()) ? 1 : 0; break;
                    default: throw new IllegalStateException("Unknown native biome weather effect " + command);
                }
            } catch (Throwable failure) { original = failure; failed = 1; }
        }
    }
    static long[] statistics() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocate(3L * Long.BYTES, Long.BYTES);
            STATS.invokeExact(data);
            return data.toArray(ValueLayout.JAVA_LONG);
        } catch (Throwable failure) { return raise(failure); }
    }
    @SuppressWarnings("unchecked") private static <E extends Throwable,T> T raise(Throwable failure) throws E { throw (E)failure; }
}
