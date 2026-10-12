package net.vulkanic.world;

import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ConcurrentModificationException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeDhRenderSourceTest {
    @Test void overlappingCopiesAndResizingPublishDirectlyAcrossBothStorageKinds() {
        try (var data = new NativeDhRenderSource(1)) {
            for (int i = 0; i < 8; i++) data.set(i, i + 1);
            var input = new ColumnArrayView(data, 4, 0, 2);
            var output = new ColumnArrayView(data, 4, 2, 2);
            output.copyFrom(input);
            long[] values = new long[8]; data.getElements(0, values, 0, 8);
            assertArrayEquals(new long[]{1,2,1,2,3,4,7,8}, values);
            output.fill(-1); assertEquals(java.util.Arrays.hashCode(new long[]{-1,-1,-1,-1}), output.getDataHash());
            var heap = new ColumnArrayView(new LongArrayList(new long[]{91,92}), 2, 0, 1);
            output.copyFrom(heap);
            assertArrayEquals(new long[]{91,0,92,0}, new long[]{output.get(0),output.get(1),output.get(2),output.get(3)});
            heap = new ColumnArrayView(new LongArrayList(new long[4]), 4, 0, 2); heap.copyFrom(output);
            assertArrayEquals(new long[]{91,0,92,0}, heap.data.toLongArray());
        }
    }
    @Test void sourceCloseInvalidatesBorrowedSegmentsAndEscapedColumnReadsSafely() {
        var source = ColumnRenderSource.createEmpty(0L, 4, 0);
        var view = source.getVerticalDataPointView(0, 0);
        assertInstanceOf(NativeDhRenderSource.class, view.data);
        view.set(0, 12);
        MemorySegment borrowed = NativeDhRenderSource.borrow(view.data, 0, 4);
        assertEquals(12, borrowed.get(ValueLayout.JAVA_LONG, 0));
        source.close(); source.close();
        assertThrows(IllegalStateException.class, () -> borrowed.get(ValueLayout.JAVA_LONG, 0));
        assertThrows(ConcurrentModificationException.class, () -> view.get(0));
        try (var replacement = ColumnRenderSource.createEmpty(0L, 4, 0)) {
            assertEquals(0, replacement.getDataPoint(0, 0, 0));
            assertThrows(ConcurrentModificationException.class, () -> view.get(0));
        }
    }
    @Test void primarySourceNonvoidQueryAndDenseSemanticsPreserveCellBoundaries() {
        try (var source = ColumnRenderSource.createEmpty(0L, 4, 0)) {
            assertFalse(source.hasNonVoidDataPoints()); source.markNotEmpty(); assertFalse(source.hasNonVoidDataPoints());
            // Frozen tests the combined packed fields, not geometric max-min.
            source.getVerticalDataPointView(0,0).set(0, (19L << 20) | (19L << 8));
            assertTrue(source.hasNonVoidDataPoints()); source.getVerticalDataPointView(0,0).set(0, 0L);
            source.getVerticalDataPointView(63,63).set(3, 1L << 20); assertTrue(source.hasNonVoidDataPoints());
            int id = source.internSemanticMaterial("minecraft:stone", "minecraft:plains");
            source.setSemanticMaterialId(63,63,3,id); source.setSemanticVariantProvenance(63,63,3,(byte)1,0);
            assertEquals(id, source.getSemanticMaterialId(63,63,3)); assertEquals(1, source.getSemanticVariantState(63,63,3));
            source.clearSemanticMaterialsForColumn(63,62); assertEquals(id, source.getSemanticMaterialId(63,63,3));
            source.clearSemanticMaterialsForColumn(63,63); assertEquals(0, source.getSemanticMaterialId(63,63,3));
            assertEquals(1L << 20, source.getDataPoint(63,63,3));
        }
    }
}
