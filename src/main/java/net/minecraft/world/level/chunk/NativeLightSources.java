package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.lighting.NativeLightQueues;

/** Ordinary block-light production from retained CPU world inputs to native work. */
public final class NativeLightSources {
    private static final MethodHandle SEED = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_block_sources",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private NativeLightSources() {}

    /** False retains contextual readers and callbacks for unsupported chunks. */
    public static boolean seed(LightChunk chunk, NativeLightQueues queues) {
        if (!(chunk instanceof ChunkAccess access)) return false;
        NativeChunkInputs inputs = access.nativeDhSections();
        if (inputs == null) return false;
        try {
            int count = (int) SEED.invokeExact(inputs.stageOwner(), queues.ownerForNativeCall(),
                access.getPos().getMinBlockX(), access.getPos().getMinBlockZ());
            if (count == -2) return false;
            if (count < 0) throw new IllegalStateException("Native block light sources rejected: " + count);
            return true;
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Native block light sources failed", failure); }
        finally { Reference.reachabilityFence(inputs); Reference.reachabilityFence(queues); }
    }
}
