package net.vulkanic.world;

import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NativeCollisionGeometry;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
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

    /** One full-data column 0 holding {@code points}, converted into a 3-point render column. */
    private static final class Probe implements Access, AutoCloseable {
        final NativeDhConversionScope scope;
        HashSet<IBlockStateWrapper> ignored = new HashSet<>(), caves = new HashSet<>(), snow = new HashSet<>();
        final List<Integer> calls = new ArrayList<>();
        final FullDataSourceV2 source;
        final NativeDhRenderSource render = new NativeDhRenderSource(3);
        final long[] output = new long[3];
        int selected = -1;
        long color = 0xff123456L;
        boolean invalidateDuringColor;
        Probe(NativeDhConversionScope scope, long... points) {
            this.scope = scope;
            var data = new it.unimi.dsi.fastutil.longs.LongList[4096];
            data[0] = new it.unimi.dsi.fastutil.longs.LongArrayList(points);
            long pos = DhSectionPos.encode((byte)6, 0, 0);
            source = FullDataSourceV2.createWithData(pos, new com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap(pos),
                data, new byte[4096], new byte[4096]);
        }
        void run(int flags) {
            convertSource(flags, 0, this, source.nativeSource().stageOwner(), render, 3, scope.scratch(),
                scope.borrow(ignored, caves, snow), java.lang.foreign.MemorySegment.NULL);
            for (int i = 0; i < 3; i++) output[i] = render.getLong(i);
        }
        @Override public long call(int op, int index, long value) {
            calls.add(op);
            return switch (op) {
                case COLUMN_BEGIN -> { assertEquals(0, index); yield 0; }
                case RESOLVE -> { selected = index; yield facts(index == 1); }
                case COLOR_SELECTED -> {
                    selected = (int)value;
                    assertEquals(selected == 1 ? 11 : 10, value >>> 32);
                    yield color();
                }
                case COLOR -> color();
                case PREVIOUS_MATERIAL, SEMANTIC_SET, SEMANTIC_MERGE,
                     SEMANTIC_SET_UNAVAILABLE, SEMANTIC_MERGE_UNAVAILABLE, COLUMN_END -> 0;
                default -> throw new AssertionError("Unexpected callback " + op);
            };
        }
        private long color() {
            if (invalidateDuringColor) NativeDhConversionScope.colorsChanged();
            return color;
        }
        @Override public void close() { render.close(); source.close(); }
    }
    private static long point(int id, int bottom) { return id | (1L << 32) | ((long)bottom << 44); }

    @Test void scratchIsReusedAndClosesWithScope() throws Throwable {
        NativeDhRenderSource scratch;
        var scope = scope();
        try (scope) {
            scratch = scope.scratch();
            assertEquals(4096, scratch.size());
            assertSame(scratch, scope.scratch());
        }
        assertEquals(0, scratch.size());
        assertThrows(IllegalStateException.class, scope::scratch);
    }

    @Test void repeatedMaterialSelectsCurrentPositionAndPolicyReplacementInvalidatesFacts() throws Throwable {
        try (var scope = scope(); var probe = new Probe(scope, point(0, 10))) {
            probe.run(0);
            assertTrue(probe.calls.contains(RESOLVE));
            long expected = probe.output[0];
            probe.calls.clear();
            probe.run(0);
            assertFalse(probe.calls.contains(RESOLVE));
            assertTrue(probe.calls.contains(COLOR_SELECTED));
            assertEquals(expected, probe.output[0]);
            probe.ignored = new HashSet<>();
            probe.calls.clear(); probe.run(0);
            assertTrue(probe.calls.contains(RESOLVE));
            probe.snow = new HashSet<>() {};
            probe.calls.clear(); probe.run(0);
            assertTrue(probe.calls.contains(RESOLVE));
            assertFalse(probe.calls.contains(COLOR_SELECTED));
        }
    }

    @Test void transferredTintCannotReuseSemanticVariantFromCachedAvoidedMaterial() throws Throwable {
        try (var scope = scope(); var probe = new Probe(scope, point(1, 11), point(0, 10))) {
            probe.run(1 | 2 | 8);
            long expected = probe.output[0];
            probe.calls.clear();
            probe.run(1 | 2 | 8);
            assertFalse(probe.calls.contains(RESOLVE));
            assertEquals(1, probe.calls.stream().filter(op -> op == COLOR_SELECTED).count());
            assertTrue(probe.calls.contains(SEMANTIC_SET_UNAVAILABLE));
            assertFalse(probe.calls.contains(SEMANTIC_SET));
            assertEquals(expected, probe.output[0]);
        }
    }

    @Test void constantColorsStayNativeUntilReloadAndNeverSkipSemanticQueries() throws Throwable {
        try (var scope = scope(); var probe = new Probe(scope, point(0, 10))) {
            probe.color |= Long.MIN_VALUE;
            probe.run(0);
            long expected = probe.output[0];
            probe.calls.clear(); probe.run(0);
            assertFalse(probe.calls.contains(COLOR));
            assertFalse(probe.calls.contains(COLOR_SELECTED));
            assertEquals(expected, probe.output[0]);
            NativeDhConversionScope.colorsChanged();
            probe.color = Long.MIN_VALUE | 0xffabcdefL;
            probe.calls.clear(); probe.run(0);
            assertTrue(probe.calls.contains(COLOR_SELECTED));
            assertNotEquals(expected, probe.output[0]);
            // A semantic query must select this position even when its RGB is constant.
            probe.color &= Long.MAX_VALUE;
            probe.calls.clear(); probe.run(8);
            assertTrue(probe.calls.contains(COLOR_SELECTED));
            assertTrue(probe.calls.contains(SEMANTIC_SET));
        }
    }

    @Test void reloadDuringColorCallbackDoesNotCacheItsOldResult() throws Throwable {
        try (var scope = scope(); var probe = new Probe(scope, point(0, 10))) {
            probe.color |= Long.MIN_VALUE;
            probe.invalidateDuringColor = true;
            probe.run(0);
            probe.invalidateDuringColor = false;
            probe.calls.clear(); probe.run(0);
            assertTrue(probe.calls.contains(COLOR_SELECTED));
        }
    }
}
