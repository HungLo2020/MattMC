package net.minecraft.world.level.levelgen;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.chunk.NativeDhColumns;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A generated chunk's columns merged straight into a memory source
 * ({@code FullDataSourceV2.updateFromNativeColumns}) against the producer
 * route ({@code createNative} then {@code updateFromDataSource}): every point,
 * the mapping and its order, metadata, uniformity, hash and update flags,
 * across fresh recipients, repeated merges and several chunks of one section.
 */
class NativeDhColumnsMergeTest {
    @BeforeAll static void bootstrap() throws Exception { NativeDhColumnsTest.bootstrap(); }

    private static void assertSameSource(FullDataSourceV2 expected, FullDataSourceV2 actual, String label) {
        for (int i = 0; i < 4096; i++) {
            var a = expected.getColumn(i); var b = actual.getColumn(i);
            assertEquals(a == null, b == null, label + " presence " + i);
            if (a != null) assertEquals(new LongArrayList(a), new LongArrayList(b), label + " column " + i);
            assertEquals(expected.columnGenerationSteps.getByte(i), actual.columnGenerationSteps.getByte(i), label + " generation " + i);
            assertEquals(expected.columnWorldCompressionMode.getByte(i), actual.columnWorldCompressionMode.getByte(i), label + " compression " + i);
            assertEquals(expected.hasSemanticHorizontalUniformity(i / 64, i % 64), actual.hasSemanticHorizontalUniformity(i / 64, i % 64), label + " uniform " + i);
        }
        assertEquals(expected.mapping.size(), actual.mapping.size(), label + " mapping size");
        for (int id = 0; id < expected.mapping.size(); id++) {
            assertSame(expected.mapping.getBlockStateWrapper(id), actual.mapping.getBlockStateWrapper(id), label + " state " + id);
            assertSame(expected.mapping.getBiomeWrapper(id), actual.mapping.getBiomeWrapper(id), label + " biome " + id);
        }
        assertEquals(expected.mapping.hashCode(), actual.mapping.hashCode(), label + " mapping hash");
        assertEquals(expected.hashCode(), actual.hashCode(), label + " hash");
        assertEquals(expected.isEmpty, actual.isEmpty, label + " isEmpty");
        assertEquals(expected.applyToParent, actual.applyToParent, label + " applyToParent");
        assertEquals(expected.applyToChildren, actual.applyToChildren, label + " applyToChildren");
    }

    @Test
    void directMergeMatchesTheProducerRouteForEveryPointAndFlag() throws Exception {
        // Group saved chunks by section, so several chunks merge into one recipient.
        List<NativeDhColumns> columns = new ArrayList<>();
        for (int test = 0; test < 32; test++) { var c = NativeDhColumnsTest.savedNativeColumns(test); assertNotNull(c); columns.add(c); }
        int merged = 0;
        for (var first : columns) {
            try (var viaProducer = FullDataSourceV2.createEmpty(first.position());
                 var direct = FullDataSourceV2.createEmpty(first.position())) {
                // Every chunk of this section, then the first chunk again (an unchanged re-merge).
                List<NativeDhColumns> order = new ArrayList<>();
                for (var c : columns) if (c.position() == first.position()) order.add(c);
                order.add(first);
                for (var c : order) {
                    boolean expectedChanged;
                    try (var producer = FullDataSourceV2.createNative(c)) { expectedChanged = viaProducer.updateFromDataSource(producer); }
                    int hashBefore = direct.hashCode();
                    assertTrue(direct.updateFromNativeColumns(c), "direct merge applies to a block-detail memory source");
                    assertSameSource(viaProducer, direct, "chunk at offset " + c.offsetX() + "," + c.offsetZ());
                    if (!expectedChanged) assertEquals(hashBefore, direct.hashCode(), "unchanged re-merge keeps the hash");
                    merged++;
                }
            }
        }
        assertTrue(merged >= 33, "merged " + merged);
    }

    @Test
    void sourcesItCannotMergeDeclineWithoutChangingPoints() throws Exception {
        var columns = NativeDhColumnsTest.savedNativeColumns(0);
        assertNotNull(columns);
        try (var other = FullDataSourceV2.createEmpty(com.seibel.distanthorizons.core.pos.DhSectionPos.encode(
                (byte) (com.seibel.distanthorizons.core.pos.DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL + 1), 0, 0))) {
            assertFalse(other.updateFromNativeColumns(columns), "a different position or detail declines");
            for (int i = 0; i < 4096; i++) assertTrue(other.getColumn(i) == null || other.getColumn(i).isEmpty());
        }
    }
}
