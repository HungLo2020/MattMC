package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;
import org.jetbrains.annotations.Nullable;

/**
 * Data-source sky light baked in Rust over the primary column table
 * ({@code dh_columns/sky.rs}), replacing the per-point list writes of
 * {@code DhLightingEngine.bakeDataSourceSkyLight}. Escaped readers get the
 * detached views, as after native occlusion culling.
 */
public final class NativeDhSkyLight {
    /** {@code -Dmattmc.dh.javaSkyBake=true} keeps the original Java bake (control runs). */
    public static final boolean JAVA_ONLY = Boolean.getBoolean("mattmc.dh.javaSkyBake");
    private static final MethodHandle BAKE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_source_bake_sky",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final ThreadLocal<MemorySegment> OUTPUT = ThreadLocal.withInitial(
        () -> Arena.ofAuto().allocate((1L + 4096L * 3) * 8, 8));

    private NativeDhSkyLight() {}

    /** Bakes {@code source}, whose mapping holds {@code materials} entries, and returns
     * true, or returns false with nothing changed. */
    public static boolean tryBake(@Nullable NativeDhMaterials policy, NativeDhSource source, int materials, int maxSkyLight) {
        if (JAVA_ONLY || policy == null || !source.isLive()) return false;
        var output = OUTPUT.get();
        try {
            int result = (int) BAKE.invokeExact(source.stageOwner(), policy.owner, materials, maxSkyLight, output);
            if (result == 0) return false;
            int count = Math.toIntExact(output.getAtIndex(ValueLayout.JAVA_LONG, 0));
            for (int row = 0; row < count; row++) {
                long offset = 1L + row * 3L;
                var column = source.reader((int) output.getAtIndex(ValueLayout.JAVA_LONG, offset));
                if (column == null) continue;
                column.acceptMergeView(output.getAtIndex(ValueLayout.JAVA_LONG, offset + 1),
                    output.getAtIndex(ValueLayout.JAVA_LONG, offset + 2), true, null);
            }
            if (result == -1) throw new OutOfMemoryError("Cannot detach DH columns for sky light");
            if (result != 1) throw new IllegalStateException("Invalid DH sky light result");
            return true;
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(source); Reference.reachabilityFence(policy); }
    }
}
