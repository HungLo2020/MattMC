package net.minecraft.world.level.chunk;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeDhConversionReaderTest {
    private static FullDataSourceV2 source() {
        return FullDataSourceV2.createEmpty(DhSectionPos.encode((byte)6, 0, 0));
    }
    @Test void primaryTraversalCreatesNoPersistentJavaColumnBindings() {
        try (var source = source(); var reader = source.nativeSource().conversionReader()) {
            assertTrue(NativeDhCodec.initialize(source));
            for (int i = 0; i < 4096; i++) {
                var column = reader.column(i);
                assertEquals(1, column.size()); assertEquals(0, column.getLong(0));
                assertThrows(IndexOutOfBoundsException.class, () -> column.getLong(1));
                assertNull(source.nativeSource().boundColumn(i));
            }
            reader.close();
            assertThrows(IllegalStateException.class, () -> reader.getLong(0));
            assertThrows(IllegalStateException.class, () -> reader.column(0));
        }
    }
    @Test void escapedCopiedMissingAndClosedColumnsPreservePublicIdentity() {
        var source = source();
        try (var reader = source.nativeSource().conversionReader()) {
            var escaped = source.getColumn(1); escaped.add(17);
            source.setColumn(2, LongArrayList.of(23)); var bound = source.getColumn(2);
            assertInstanceOf(NativeDhColumnList.class, bound, "a Java list is bound as a native copy");
            source.setColumn(3, null);
            assertSame(escaped, reader.column(1)); assertSame(bound, reader.column(2)); assertEquals(LongArrayList.of(23), bound);
            assertNull(reader.column(3)); assertEquals(0, reader.column(4).size());
            assertNull(source.nativeSource().boundColumn(4));
            source.close();
            assertSame(escaped, reader.column(1)); assertSame(bound, reader.column(2));
            assertNull(reader.column(3));
        } finally { source.close(); }
    }
    @Test void selectedLeaseSurvivesSourceReplacementCloseAndNestedReaders() {
        var source = source();
        try (var first = source.nativeSource().conversionReader(); var nested = source.nativeSource().conversionReader()) {
            assertTrue(NativeDhCodec.initialize(source));
            var selected = first.column(0);
            assertEquals(0, nested.column(1).getLong(0));
            // An escaped writer mutates the same retained native column.
            var writer = source.getColumn(0); writer.set(0, 31); writer.add(41);
            assertEquals(2, selected.size()); assertEquals(41, selected.getLong(1));
            source.setColumn(0, LongArrayList.of(99)); source.close();
            assertEquals(31, selected.getLong(0)); assertEquals(41, selected.getLong(1));
        } finally { source.close(); }
    }
}
