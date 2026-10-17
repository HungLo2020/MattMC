package net.minecraft.world.level.lighting;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.chunk.DataLayer;

/** Genuine contextual source/layer effects; Rust owns the live conditional cursor. */
final class NativeLiveSkySources {
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_live_sky_" + name, descriptor);
    }
    private static final MethodHandle BEGIN = handle("begin", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle READ = handle("read", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle STEP = handle("step", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle END = handle("end", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException originalFailure(Throwable failure) throws T {
        throw (T)failure;
    }

    static void run(SkyLightEngine engine, SkyLightSectionStorage storage, NativeLightQueues queues,
                    int x, int z, int top, int bottom, ChunkSkyLightSources center,
                    ChunkSkyLightSources north, ChunkSkyLightSources south,
                    ChunkSkyLightSources west, ChunkSkyLightSources east) {
        long frame = 0;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment output = arena.allocate(4L * Long.BYTES, Long.BYTES);
            frame = (long)BEGIN.invokeExact(queues.ownerForNativeCall(), x, z, top, bottom);
            if (frame == 0) throw new IllegalStateException("Missing live sky-source owner");
            DataLayer dataLayer = null;
            int action = (int)READ.invokeExact(frame, output);
            for (;;) {
                if (action == 0) return;
                long scalar = 0;
                switch (action) {
                    case 1 -> {
                        dataLayer = storage.getDataLayerToWrite(output.getAtIndex(ValueLayout.JAVA_LONG, 0));
                        scalar = dataLayer == null ? 0 : 1;
                    }
                    case 2 -> {
                        ChunkSkyLightSources source = switch ((int)output.getAtIndex(ValueLayout.JAVA_LONG, 0)) {
                            case 0 -> center;
                            case 1 -> north;
                            case 2 -> south;
                            case 3 -> west;
                            case 4 -> east;
                            default -> throw new IllegalStateException("Unknown live sky-source context");
                        };
                        scalar = source.getLowestSourceY((int)output.getAtIndex(ValueLayout.JAVA_LONG, 1),
                            (int)output.getAtIndex(ValueLayout.JAVA_LONG, 2));
                    }
                    case 3 -> dataLayer.set((int)output.getAtIndex(ValueLayout.JAVA_LONG, 0),
                        (int)output.getAtIndex(ValueLayout.JAVA_LONG, 1),
                        (int)output.getAtIndex(ValueLayout.JAVA_LONG, 2),
                        (int)output.getAtIndex(ValueLayout.JAVA_LONG, 3));
                    case 5 -> throw new IllegalStateException("Light queue rejected entry: " + output.getAtIndex(ValueLayout.JAVA_LONG, 0));
                    default -> throw new IllegalStateException("Native live sky-source rejected effect: " + action);
                }
                action = (int)STEP.invokeExact(frame, scalar, output);
            }
        } catch (Throwable failure) {
            throw NativeLiveSkySources.<RuntimeException>originalFailure(failure);
        } finally {
            try { if (frame != 0) END.invokeExact(frame); }
            catch (Throwable failure) {
                throw NativeLiveSkySources.<RuntimeException>originalFailure(failure);
            } finally {
                Reference.reachabilityFence(engine);
                Reference.reachabilityFence(storage);
                Reference.reachabilityFence(queues);
                Reference.reachabilityFence(center);
                Reference.reachabilityFence(north);
                Reference.reachabilityFence(south);
                Reference.reachabilityFence(west);
                Reference.reachabilityFence(east);
            }
        }
    }
}
