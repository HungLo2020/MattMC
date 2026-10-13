package net.vulkanic.world;

import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NativeCollisionGeometry;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static net.vulkanic.world.NativeDhRenderConversion.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeDhConversionScopeTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        assertTrue(NativeCollisionGeometry.ready());
    }

    // Isolate the native scope/ABI from world construction. Production admission
    // remains restricted to exact stock worlds, source maps and native readers.
    private static NativeDhConversionScope scope() throws Throwable {
        var create = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_conversion_materials_create",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        var constructor = NativeDhConversionScope.class.getDeclaredConstructor(MemorySegment.class);
        constructor.setAccessible(true);
        return constructor.newInstance((MemorySegment)create.invokeExact(3));
    }

    private static long facts(boolean grass) {
        return (long)(Block.getId((grass ? Blocks.SHORT_GRASS : Blocks.STONE).defaultBlockState()) + 1) << 32;
    }

    private static final class Probe implements Access {
        final NativeDhConversionScope scope;
        HashSet<IBlockStateWrapper> ignored = new HashSet<>(), caves = new HashSet<>(), snow = new HashSet<>();
        final List<Integer> calls = new ArrayList<>();
        final long[] points, output = new long[3];
        int selected = -1;
        long color = 0xff123456L;
        boolean invalidateDuringColor;
        Probe(NativeDhConversionScope scope, long... points) { this.scope = scope; this.points = points; }
        @Override public MemorySegment materialsOwner() { return scope.borrow(ignored, caves, snow); }
        @Override public long call(int op, int index, long value, long extra) {
            calls.add(op);
            return switch (op) {
                case SIZE -> points.length;
                case POINT -> points[index];
                case RESOLVE -> { selected = index; yield facts(index == 1); }
                case COLOR_SELECTED -> {
                    selected = (int)value;
                    assertEquals(selected == 1 ? 11 : 10, value >>> 32);
                    yield color();
                }
                case COLOR -> color();
                case OUTPUT_GET -> output[index];
                case OUTPUT_SET -> { output[index] = value; yield 0; }
                case PREVIOUS_MATERIAL, SEMANTIC_SET, SEMANTIC_MERGE,
                     SEMANTIC_SET_UNAVAILABLE, SEMANTIC_MERGE_UNAVAILABLE -> 0;
                default -> throw new AssertionError("Unexpected callback " + op);
            };
        }
        private long color() {
            if (invalidateDuringColor) NativeDhConversionScope.colorsChanged();
            return color;
        }
    }
    private static long point(int id, int bottom) { return id | (1L << 32) | ((long)bottom << 44); }

    @Test void temporaryPointsReuseNativeStorageClearActiveWindowAndCloseWithScope() throws Throwable {
        NativeDhRenderSource scratch;
        var scope = scope();
        try (scope) {
            assertNull(scope.temporaryOutput(4097));
            scratch = scope.temporaryOutput(3);
            scratch.set(0, 17); scratch.set(1, 19); scratch.set(2, 23);
            assertSame(scratch, scope.temporaryOutput(2));
            assertEquals(0, scratch.getLong(0)); assertEquals(0, scratch.getLong(1));
            assertEquals(23, scratch.getLong(2));
        }
        assertEquals(0, scratch.size());
        assertThrows(IndexOutOfBoundsException.class, () -> scratch.getLong(0));
        assertThrows(IllegalStateException.class, () -> scope.temporaryOutput(1));
    }

    @Test void repeatedMaterialSelectsCurrentPositionAndPolicyReplacementInvalidatesFacts() throws Throwable {
        try (var scope = scope()) {
            var probe = new Probe(scope, point(0, 10));
            convert(0, 0, probe);
            assertTrue(probe.calls.contains(RESOLVE));
            long expected = probe.output[0];
            probe.calls.clear();
            convert(0, 0, probe);
            assertFalse(probe.calls.contains(RESOLVE));
            assertTrue(probe.calls.contains(COLOR_SELECTED));
            assertEquals(expected, probe.output[0]);
            probe.ignored = new HashSet<>();
            probe.calls.clear(); convert(0, 0, probe);
            assertTrue(probe.calls.contains(RESOLVE));
            probe.snow = new HashSet<>() {};
            probe.calls.clear(); convert(0, 0, probe);
            assertTrue(probe.calls.contains(RESOLVE));
            assertFalse(probe.calls.contains(COLOR_SELECTED));
        }
    }

    @Test void transferredTintCannotReuseSemanticVariantFromCachedAvoidedMaterial() throws Throwable {
        try (var scope = scope()) {
            var probe = new Probe(scope, point(1, 11), point(0, 10));
            convert(1 | 2 | 8, 0, probe);
            long expected = probe.output[0];
            probe.calls.clear();
            convert(1 | 2 | 8, 0, probe);
            assertFalse(probe.calls.contains(RESOLVE));
            assertEquals(1, probe.calls.stream().filter(op -> op == COLOR_SELECTED).count());
            assertTrue(probe.calls.contains(SEMANTIC_SET_UNAVAILABLE));
            assertFalse(probe.calls.contains(SEMANTIC_SET));
            assertEquals(expected, probe.output[0]);
        }
    }

    @Test void constantColorsStayNativeUntilReloadAndNeverSkipSemanticQueries() throws Throwable {
        try (var scope = scope()) {
            var probe = new Probe(scope, point(0, 10));
            probe.color |= Long.MIN_VALUE;
            convert(0, 0, probe);
            long expected = probe.output[0];
            probe.calls.clear(); convert(0, 0, probe);
            assertFalse(probe.calls.contains(COLOR));
            assertFalse(probe.calls.contains(COLOR_SELECTED));
            assertEquals(expected, probe.output[0]);
            NativeDhConversionScope.colorsChanged();
            probe.color = Long.MIN_VALUE | 0xffabcdefL;
            probe.calls.clear(); convert(0, 0, probe);
            assertTrue(probe.calls.contains(COLOR_SELECTED));
            assertNotEquals(expected, probe.output[0]);
            // A semantic query must select this position even when its RGB is constant.
            probe.color &= Long.MAX_VALUE;
            probe.calls.clear(); convert(8, 0, probe);
            assertTrue(probe.calls.contains(COLOR_SELECTED));
            assertTrue(probe.calls.contains(SEMANTIC_SET));
        }
    }

    @Test void reloadDuringColorCallbackDoesNotCacheItsOldResult() throws Throwable {
        try (var scope = scope()) {
            var probe = new Probe(scope, point(0, 10));
            probe.color |= Long.MIN_VALUE;
            probe.invalidateDuringColor = true;
            convert(0, 0, probe);
            probe.invalidateDuringColor = false;
            probe.calls.clear(); convert(0, 0, probe);
            assertTrue(probe.calls.contains(COLOR_SELECTED));
        }
    }
}
