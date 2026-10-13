package net.vulkanic.world;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static net.vulkanic.world.NativeDhRenderConversion.*;

class NativeDhRenderConversionTest {
    private static FullDataSourceV2 source(long... points) {
        var data = new it.unimi.dsi.fastutil.longs.LongList[4096];
        data[0] = new it.unimi.dsi.fastutil.longs.LongArrayList(points);
        long pos = DhSectionPos.encode((byte)6, 0, 0);
        return FullDataSourceV2.createWithData(pos, new com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap(pos),
            data, new byte[4096], new byte[4096]);
    }
    private static void run(FullDataSourceV2 source, NativeDhRenderSource output, int height, Access access) {
        try (var scratch = new NativeDhRenderSource(1)) {
            convertSource(0, 0, access, source.nativeSource().stageOwner(), output, height, scratch, java.lang.foreign.MemorySegment.NULL);
        }
    }

    @Test void nativeInputsAndOutputStayAuthoritativeAcrossContextualGrowth() {
        try (var source = source((1L << 32) | (10L << 44)); var output = new NativeDhRenderSource(2)) {
            var writer = source.getColumn(0);
            var calls = new ArrayList<Integer>();
            run(source, output, 2, new Access() {
                int colors;
                @Override public long call(int op, int index, long value) {
                    calls.add(op);
                    return switch(op) {
                        case COLUMN_BEGIN, RESOLVE, SNOW -> 0;
                        case COLOR -> {
                            // Rust reads the live column after every query.
                            if (colors++ == 0) writer.add((1L << 32) | (3L << 44));
                            yield 0xff223344L;
                        }
                        case BLOCK_MATERIAL -> 2;
                        default -> throw new AssertionError("Native storage unexpectedly projected through Java: " + op);
                    };
                }
            });
            assertEquals(0x2f22334400b00a00L, output.getLong(0));
            assertEquals(0x2f22334400400300L, output.getLong(1));
            assertEquals(0, output.getLong(2));
            assertEquals(List.of(COLUMN_BEGIN, RESOLVE, SNOW, COLOR, BLOCK_MATERIAL, RESOLVE, SNOW, COLOR, BLOCK_MATERIAL), calls);
        }
    }
    @Test void outputCloseDuringCallbackIsRefusedUntilNativeUseFinishes() {
        try (var source = source((1L << 32) | (10L << 44)); var output = new NativeDhRenderSource(1)) {
            assertThrows(IllegalStateException.class, () -> run(source, output, 1, (op, index, value) -> {
                output.close();
                throw new AssertionError("Closing a pinned output must fail");
            }));
            assertEquals(4096, output.size());
            output.set(0, 123); assertEquals(123, output.getLong(0));
        }
    }
    @Test void callbackExceptionRetainsIdentityAndStopsAtPublishedPrefix() {
        var failure = new AssertionError("color failed");
        var calls = new ArrayList<Integer>();
        try (var source = source(1L << 32); var output = new NativeDhRenderSource(1)) {
            var actual = assertThrows(AssertionError.class, () -> run(source, output, 1, (op, index, value) -> {
                calls.add(op);
                return switch (op) {
                    case COLUMN_BEGIN, RESOLVE, SNOW -> 0;
                    case COLOR -> throw failure;
                    default -> throw new AssertionError("Unexpected operation " + op);
                };
            }));
            assertSame(failure, actual);
            assertEquals(List.of(COLUMN_BEGIN, RESOLVE, SNOW, COLOR), calls);
            assertEquals(0, output.getLong(0));
        }
    }
    @Test void nestedConversionRestoresOuterCallback() {
        var outer = new ArrayList<Integer>();
        var nested = new ArrayList<Integer>();
        try (var first = source((1L << 32) | (10L << 44)); var second = source((1L << 32) | (4L << 44));
             var firstOutput = new NativeDhRenderSource(1); var secondOutput = new NativeDhRenderSource(1)) {
            run(first, firstOutput, 1, (op, index, value) -> {
                outer.add(op);
                if (op == COLUMN_BEGIN) run(second, secondOutput, 1, (innerOp, innerIndex, innerValue) -> {
                    nested.add(innerOp);
                    return innerOp == COLOR ? 0xff000001L : innerOp == BLOCK_MATERIAL ? 2 : 0;
                });
                return op == COLOR ? 0xff000002L : op == BLOCK_MATERIAL ? 2 : 0;
            });
            assertEquals(List.of(COLUMN_BEGIN, RESOLVE, SNOW, COLOR, BLOCK_MATERIAL), outer);
            assertEquals(List.of(COLUMN_BEGIN, RESOLVE, SNOW, COLOR, BLOCK_MATERIAL), nested);
            assertEquals(0x2f00000200b00a00L, firstOutput.getLong(0));
            assertEquals(0x2f00000100500400L, secondOutput.getLong(0));
        }
    }
}
