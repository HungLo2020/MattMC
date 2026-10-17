package net.sodium.client.render.chunk.compile.tasks;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.BlockAndTintGetter;
import org.lwjgl.system.MemoryUtil;

/** CPU construction lease for a Rust-owned immutable world color snapshot. */
final class NativeSectionColors implements AutoCloseable {
    private static final MethodHandle PLAN = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_world_section_colors_plan", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle FINISH = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_world_section_colors_finish", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_world_section_colors_release", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private long identity;

    static NativeSectionColors capture(int minX, int minY, int minZ, long activeIndices, int count,
            long kinds, long literals, BlockAndTintGetter slice) {
        var result = new NativeSectionColors();
        long output = MemoryUtil.nmemAlloc(4L * Long.BYTES);
        try {
            check((int) PLAN.invokeExact(minX, minY, minZ, MemorySegment.ofAddress(activeIndices), count,
                    MemorySegment.ofAddress(kinds), MemorySegment.ofAddress(literals), MemorySegment.ofAddress(output)));
            result.identity = MemoryUtil.memGetLong(output);
            long queries = MemoryUtil.memGetLong(output + 8);
            long values = MemoryUtil.memGetLong(output + 16);
            int length = Math.toIntExact(MemoryUtil.memGetLong(output + 24));
            var position = new BlockPos.MutableBlockPos();
            for (int i = 0; i < length; i++) {
                long query = queries + (long) i * 4 * Integer.BYTES;
                position.set(MemoryUtil.memGetInt(query + 4), MemoryUtil.memGetInt(query + 8), MemoryUtil.memGetInt(query + 12));
                int color = switch (MemoryUtil.memGetInt(query)) {
                    case 1 -> BiomeColors.getAverageGrassColor(slice, position);
                    case 2 -> BiomeColors.getAverageFoliageColor(slice, position);
                    case 3 -> BiomeColors.getAverageDryFoliageColor(slice, position);
                    default -> throw new IllegalStateException("Invalid native world color request");
                };
                MemoryUtil.memPutInt(values + (long) i * Integer.BYTES, color | 0xFF000000);
            }
            check((int) FINISH.invokeExact(result.identity));
            return result;
        } catch (Throwable failure) {
            result.close();
            throw new IllegalStateException("Could not capture native world colors", failure);
        } finally {
            MemoryUtil.nmemFree(output);
        }
    }

    long identity() { return this.identity; }

    private static void check(int status) {
        if (status != 0) throw new IllegalStateException("Native world colors failed with status " + status);
    }

    @Override
    public void close() {
        if (this.identity != 0) {
            long released = this.identity;
            this.identity = 0;
            try { check((int) RELEASE.invokeExact(released)); }
            catch (Throwable failure) { throw new IllegalStateException("Could not release native world colors", failure); }
        }
    }
}
