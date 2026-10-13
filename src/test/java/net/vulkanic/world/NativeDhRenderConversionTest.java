package net.vulkanic.world;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static net.vulkanic.world.NativeDhRenderConversion.*;

class NativeDhRenderConversionTest {
    @Test void nativeInputsAndOutputStayAuthoritativeAcrossContextualGrowth() {
        try (var source = com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2.createEmpty(com.seibel.distanthorizons.core.pos.DhSectionPos.encode((byte)6, 0, 0));
             var reader = source.nativeSource().conversionReader(); var output = new NativeDhRenderSource(1)) {
            assertTrue(net.minecraft.world.level.chunk.NativeDhCodec.initialize(source));
            reader.column(0);
            var writer = source.getColumn(0);
            writer.set(0, (1L << 32) | (10L << 44));
            var calls = new ArrayList<Integer>();
            convert(0, 0, new Access() {
                int colors;
                @Override public java.lang.foreign.MemorySegment inputOwner() { return reader.borrowOwner(); }
                @Override public java.lang.foreign.MemorySegment outputOwner() { return output.borrowOwner(); }
                @Override public long outputWindow() { return (2L << 32) | 17; }
                @Override public long call(int op, int index, long value, long extra) {
                    calls.add(op);
                    return switch(op) {
                        case RESOLVE, SNOW -> 0;
                        case COLOR -> {
                            if (colors++ == 0) writer.add((1L << 32) | (3L << 44));
                            yield 0xff223344L;
                        }
                        case BLOCK_MATERIAL -> 2;
                        default -> throw new AssertionError("Native storage unexpectedly projected through Java: " + op);
                    };
                }
            });
            assertEquals(0x2f22334400b00a00L, output.getLong(17));
            assertEquals(0x2f22334400400300L, output.getLong(18));
            assertEquals(0, output.getLong(16)); assertEquals(0, output.getLong(19));
            assertEquals(8, calls.size());
        }
    }
    @Test void outputCloseDuringCallbackIsRefusedUntilNativeUseFinishes() {
        try (var output = new NativeDhRenderSource(1)) {
            assertThrows(IllegalStateException.class, () -> convert(0, 0, new Access() {
                @Override public java.lang.foreign.MemorySegment outputOwner() { return output.borrowOwner(); }
                @Override public long outputWindow() { return 1L << 32; }
                @Override public long call(int op, int index, long value, long extra) {
                    output.close();
                    throw new AssertionError("Closing a pinned output must fail");
                }
            }));
            assertEquals(4096, output.size());
            output.set(0, 123); assertEquals(123, output.getLong(0));
        }
    }
    @Test void callbackExceptionRetainsIdentityAndStopsAtPublishedPrefix() {
        var failure = new AssertionError("color failed");
        var calls = new ArrayList<Integer>();
        var actual = assertThrows(AssertionError.class, () -> convert(0, 0, (op, index, value, extra) -> {
            calls.add(op);
            return switch (op) {
                case SIZE -> 1;
                case POINT -> 1L << 32;
                case RESOLVE, SNOW -> 0;
                case COLOR -> throw failure;
                default -> throw new AssertionError("Unexpected operation " + op);
            };
        }));
        assertSame(failure, actual);
        assertEquals(List.of(SIZE, POINT, RESOLVE, SNOW, COLOR), calls);
    }
    @Test void nestedConversionRestoresOuterCallbackAndEmptyOutputIsPublishedOnce() {
        var outer = new ArrayList<Integer>();
        var nested = new ArrayList<Integer>();
        convert(0, 0, (op, index, value, extra) -> {
            outer.add(op);
            if (op == SIZE) {
                convert(0, 0, (innerOp, innerIndex, innerValue, innerExtra) -> {
                    nested.add(innerOp); assertEquals(0, innerValue); return 0;
                });
                return 0;
            }
            assertEquals(OUTPUT_SET, op); assertEquals(0, index); assertEquals(0, value);
            return 0;
        });
        assertEquals(List.of(SIZE, OUTPUT_SET), outer);
        assertEquals(List.of(SIZE, OUTPUT_SET), nested);
    }
}
