package net.minecraft.world.level.levelgen;

import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.generation.DhLightingEngine;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.Random;
import net.minecraft.world.level.chunk.NativeDhColumnList;
import net.minecraft.world.level.chunk.NativeDhMaterials;
import net.minecraft.world.level.chunk.NativeDhSkyLight;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The Rust data-source sky bake ({@code dh_columns/sky.rs}) against the
 * original Java {@code bakeDataSourceSkyLight} on identical sources: saved
 * native chunk columns (real materials, air, water and stone) plus random
 * stacks over the whole 64x64 grid, so light propagates across columns.
 */
class NativeDhSkyLightTest {
    @BeforeAll static void bootstrap() throws Exception { NativeDhColumnsTest.bootstrap(); }

    /** The saved chunk's columns, then random top-down stacks of its materials in every other column. */
    private static FullDataSourceV2 source(int test, long seed) throws Exception {
        var columns = NativeDhColumnsTest.savedNativeColumns(test);
        assertNotNull(columns);
        var source = FullDataSourceV2.createEmpty(columns.position());
        try (var chunk = FullDataSourceV2.createNative(columns)) { source.updateFromDataSource(chunk); }
        int materials = source.mapping.size();
        Random random = new Random(seed);
        for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) {
            var existing = source.getColumnAtRelPos(x, z);
            if (existing != null && !existing.isEmpty() || random.nextInt(5) == 0) continue;
            var points = new LongArrayList();
            int top = 300 + random.nextInt(40);
            for (int i = 0, n = 1 + random.nextInt(6); i < n && top > 1; i++) {
                int height = 1 + random.nextInt(Math.min(top, 30));
                points.add(FullDataPointUtil.encode(random.nextInt(materials), height, top - height, (byte) random.nextInt(16), (byte) 0));
                top -= height;
            }
            // A native column, as merged and decoded sources hold; Java lists keep the Java bake.
            var column = NativeDhColumnList.empty();
            column.addElements(0, points.toLongArray());
            source.setSingleColumn(column, x, z, EDhApiWorldGenerationStep.LIGHT, EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
        }
        return source;
    }

    @Test
    void rustBakeMatchesTheJavaBakeForEveryPointAndRefreshesEscapedReaders() throws Exception {
        int baked = 0;
        for (int test = 0; test < 8; test++) {
            for (int maxSky : new int[] {15, 0, 9}) {
                try (var java = source(test, test * 31L + maxSky); var rust = source(test, test * 31L + maxSky);
                     var policy = NativeDhMaterials.create(rust.mapping)) {
                    assertNotNull(policy, "stock materials admit the native bake");
                    // Readers that escaped before the bake must see the baked points.
                    LongList[] escaped = new LongList[64];
                    for (int i = 0; i < 64; i++) escaped[i] = rust.getColumnAtRelPos(i, (i * 7) % 64);
                    DhLightingEngine.INSTANCE.bakeDataSourceSkyLightInJava(java, maxSky);
                    assertTrue(NativeDhSkyLight.tryBake(policy, rust.nativeSource(), rust.mapping.size(), maxSky), "native bake ran");
                    for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) {
                        LongList expected = java.getColumnAtRelPos(x, z), actual = rust.getColumnAtRelPos(x, z);
                        assertEquals(expected == null, actual == null, "presence " + x + "," + z);
                        if (expected != null) assertEquals(new LongArrayList(expected), new LongArrayList(actual),
                            "test " + test + " max " + maxSky + " column " + x + "," + z);
                    }
                    for (int i = 0; i < 64; i++) {
                        LongList expected = java.getColumnAtRelPos(i, (i * 7) % 64);
                        if (expected != null) assertEquals(new LongArrayList(expected), new LongArrayList(escaped[i]), "escaped reader " + i);
                    }
                    baked++;
                }
            }
        }
        assertEquals(24, baked);
    }

    @Test
    void unsupportedSourcesDeclineWithoutChanges() throws Exception {
        try (var source = source(0, 5); var policy = NativeDhMaterials.create(source.mapping)) {
            // A point whose material has no mapping entry cannot be baked; nothing may change.
            var unmapped = new LongArrayList(new long[] {FullDataPointUtil.encode(source.mapping.size(), 4, 100, (byte) 0, (byte) 0)});
            source.setColumn(0, unmapped);
            var before = new LongArrayList[4096];
            for (int i = 0; i < 4096; i++) { var column = source.getColumn(i); before[i] = column == null ? null : new LongArrayList(column); }
            assertFalse(NativeDhSkyLight.tryBake(policy, source.nativeSource(), source.mapping.size(), 15));
            for (int i = 0; i < 4096; i++) {
                var column = source.getColumn(i);
                assertEquals(before[i], column == null ? null : new LongArrayList(column), "column " + i + " unchanged");
            }
            assertFalse(NativeDhSkyLight.tryBake(null, source.nativeSource(), source.mapping.size(), 15), "no policy declines");
        }
    }
}
