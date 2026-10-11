package net.minecraft.world.level.levelgen;

import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.world.level.chunk.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** CPU ownership/transport evidence, not a gameplay or performance acceptance. */
@Tag("parity")
class NativeDhContributorsTest {
    @BeforeAll static void bootstrap() throws Exception {
        Class.forName("com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer");
        NativeDhColumnsTest.bootstrap();
    }
    @Test void allSavedNativeFootprintsPreserveExactRemappingAndDirectConsumerOwnership() {
        int nonempty = 0;
        for (int test = 0; test < 32; test++) {
            var producer = NativeDhColumnsTest.savedNativeColumns(test); assertNotNull(producer);
            try (var source = FullDataSourceV2.createNative(producer)) {
                int before = source.hashCode();
                int[] map = java.util.stream.IntStream.range(0, source.mapping.size()).map(i -> source.mapping.size() - 1 - i).toArray();
                for (int x = 0; x < 64; x += 2) for (int z = 0; z < 64; z += 2) {
                    LongList[] input = {source.getColumnAtRelPos(x, z), source.getColumnAtRelPos(x, z + 1),
                        source.getColumnAtRelPos(x + 1, z), source.getColumnAtRelPos(x + 1, z + 1)};
                    var snapshot = NativeDhContributors.snapshot(input, map, 256); assertNotNull(snapshot);
                    assertTrue(NativeDhContributors.isSnapshot(snapshot));
                    for (int i = 0; i < 4; i++) {
                        assertEquals(Math.min(256, input[i].size()), snapshot[i].size());
                        for (int p = 0; p < snapshot[i].size(); p++) assertEquals(FullDataPointUtil.remap(map, input[i].getLong(p)), snapshot[i].getLong(p));
                        nonempty += snapshot[i].isEmpty() ? 0 : 1;
                    }
                    var render = ColumnRenderSource.createEmpty(source.getPos(), 1, 0);
                    render.setSemanticHorizontalContributors(0, 0, snapshot);
                    var retained = render.getRetainedSemanticHorizontalContributors(0, 0);
                    for (int i = 0; i < 4; i++) assertSame(snapshot[i], retained[i], "Direct native consumer keeps the immutable readers");
                    assertArrayEquals(snapshot, render.getSemanticHorizontalContributors(0, 0));
                }
                assertEquals(before, source.hashCode());
            }
        }
        assertTrue(nonempty > 1000);
    }
    @Test void nativeAndHeapSourcesRetainNullEmptyBoundsSignedIdsAndDefensivePublicCopies() {
        long[] values = new long[300];
        for (int i = 0; i < values.length; i++) values[i] = (long) (i % 3) | ((long) i << 32) | (13L << 60);
        var nativeInput = NativeDhColumnList.empty(); nativeInput.addElements(0, values);
        var heapInput = new LongArrayList(values);
        int[] map = {7, -1, Integer.MIN_VALUE};
        var snapshot = NativeDhContributors.snapshot(new LongList[]{nativeInput, null, new LongArrayList(), heapInput}, map, 256);
        assertNotNull(snapshot); assertNull(snapshot[1]); assertNotNull(snapshot[2]); assertTrue(snapshot[2].isEmpty());
        assertEquals(256, snapshot[0].size()); assertEquals(256, snapshot[3].size());
        for (int i = 0; i < 256; i++) assertEquals(FullDataPointUtil.remap(map, values[i]), snapshot[0].getLong(i));
        long expected = snapshot[0].getLong(0); nativeInput.set(0, 123); heapInput.set(0, 456);
        assertEquals(expected, snapshot[0].getLong(0)); assertEquals(expected, snapshot[3].getLong(0));
        assertThrows(UnsupportedOperationException.class, () -> snapshot[0].set(0, 99));
        var render = ColumnRenderSource.createEmpty(0, 1, 0); render.setSemanticHorizontalContributors(1, 2, snapshot);
        snapshot[0] = null;
        var retained = render.getRetainedSemanticHorizontalContributors(1, 2); assertEquals(expected, retained[0].getLong(0));
        var exported = render.getSemanticHorizontalContributors(1, 2); exported[0].set(0, 888);
        assertEquals(expected, render.getSemanticHorizontalContributors(1, 2)[0].getLong(0));
        render.clearSemanticMaterialsForColumn(1, 2);
        assertNull(render.getRetainedSemanticHorizontalContributors(1, 2)); assertEquals(expected, retained[0].getLong(0));
    }
    @Test void compatibilityDeclinePreservesCallbacksAndNativeSnapshotSurvivesSourceClose() {
        var custom = new LongArrayList(new long[]{17}) { @Override public long getLong(int i) { return super.getLong(i); } };
        assertNull(NativeDhContributors.snapshot(new LongList[]{custom, null, null, null}, null, 256));
        assertNull(NativeDhContributors.snapshot(new LongList[]{new LongArrayList(new long[]{3}), null, null, null}, new int[]{0}, 256));
        assertNull(NativeDhContributors.snapshot(new LongList[]{new LongArrayList(new long[4097]), null, null, null}, null, Integer.MAX_VALUE));
        LongList[] retained;
        try (var source = FullDataSourceV2.createEmpty(DhSectionPos.encode((byte) 7, 0, 0))) {
            var heap = new LongArrayList(new long[]{17}); source.setSemanticHorizontalContributors(1, 2, new LongList[]{heap, null, new LongArrayList(), null});
            retained = source.getRetainedSemanticHorizontalContributors(1, 2); assertTrue(NativeDhContributors.isSnapshot(retained));
            heap.set(0, 99); source.getSemanticHorizontalContributors(1, 2)[0].set(0, 333);
            assertEquals(17, retained[0].getLong(0)); assertNotNull(retained[2]);
            retained[1] = new LongArrayList(new long[]{888}); assertNull(source.getRetainedSemanticHorizontalContributors(1, 2)[1]);
        }
        assertEquals(17, retained[0].getLong(0));
    }
    @Test void actualParentContributorProducerRetainsIndependentNativeFootprints() throws Exception {
        var compression = Config.Common.LodBuilding.worldCompression; var previous = compression.get();
        compression.setWithoutFiringEvents(EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
        try {
            var producer = NativeDhColumnsTest.savedNativeColumns(1); assertNotNull(producer);
            LongList[] retained = null; LongArrayList[] expected = null; int admitted = 0;
            try (var source = FullDataSourceV2.createNative(producer);
                 var parent = FullDataSourceV2.createEmpty(DhSectionPos.getParentPos(source.getPos()))) {
                assertTrue(parent.updateFromDataSource(source));
                for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) {
                    var footprint = parent.getRetainedSemanticHorizontalContributors(x, z);
                    if (footprint == null) continue;
                    assertTrue(NativeDhContributors.isSnapshot(footprint), "Actual ordinary parent produced native footprints");
                    for (var reader : footprint) if (reader != null) {
                        var owner = reader.getClass().getDeclaredField("owner"); owner.setAccessible(true);
                        assertInstanceOf(NativeDhParentColumns.class, owner.get(reader), "Ordinary parent uses the quadrant owner, with no per-cell native captures");
                    }
                    var render = ColumnRenderSource.createEmpty(parent.getPos(), 1, 0); render.setSemanticHorizontalContributors(0, 0, footprint);
                    var received = render.getRetainedSemanticHorizontalContributors(0, 0);
                    for (int i = 0; i < 4; i++) assertSame(footprint[i], received[i]);
                    retained = footprint; expected = parent.getSemanticHorizontalContributors(x, z); admitted++;
                }
            }
            assertTrue(admitted > 0); assertArrayEquals(expected, retained, "Footprint survives producer/parent close");
        } finally { compression.setWithoutFiringEvents(previous); }
    }
    @Test void actualTransformerUsesRetainedFootprintsOnlyWhenSemanticPreservationIsEnabled() {
        var compression = Config.Common.LodBuilding.worldCompression; var previous = compression.get();
        compression.setWithoutFiringEvents(EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
        try {
            var producer = NativeDhColumnsTest.savedNativeColumns(1); assertNotNull(producer);
            try (var source = FullDataSourceV2.createNative(producer);
                 var parent = FullDataSourceV2.createEmpty(DhSectionPos.getParentPos(source.getPos()))) {
                parent.updateFromDataSource(source);
                int x = -1, z = -1;
                for (int a = 0; a < 64 && x == -1; a++) for (int b = 0; b < 64; b++) {
                    if (parent.getRetainedSemanticHorizontalContributors(a, b) != null && !parent.getColumnAtRelPos(a, b).isEmpty()) { x = a; z = b; break; }
                }
                assertTrue(x >= 0); var expected = parent.getRetainedSemanticHorizontalContributors(x, z);
                var watched = org.mockito.Mockito.spy(parent);
                org.mockito.Mockito.doThrow(new AssertionError("Transformer must not export point arrays")).when(watched).getSemanticHorizontalContributors(x, z);
                var level = org.mockito.Mockito.mock(IClientLevelWrapper.class);
                org.mockito.Mockito.when(level.getWrappedMcObject()).thenReturn(NativeDhColumnsTest.registryOnlyFixtureWorld());
                org.mockito.Mockito.when(level.hasCeiling()).thenReturn(true);
                org.mockito.Mockito.when(level.getBlockColor(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(0xff223344);
                var data = parent.getColumnAtRelPos(x, z);
                var render = ColumnRenderSource.createEmpty(parent.getPos(), Math.max(1, data.size()), 0);
                var ordinary = ColumnRenderSource.createEmpty(parent.getPos(), Math.max(1, data.size()), 0);
                FullDataToRenderDataTransformer.updateOrReplaceRenderDataViewColumnWithFullDataColumn(
                    level, watched, ordinary, x, z, x * 2, z * 2, ordinary.getVerticalDataPointView(x, z), data, false);
                org.mockito.Mockito.verify(watched, org.mockito.Mockito.never()).getRetainedSemanticHorizontalContributors(x, z);
                assertNull(ordinary.getRetainedSemanticHorizontalContributors(x, z));
                FullDataToRenderDataTransformer.updateOrReplaceRenderDataViewColumnWithFullDataColumn(
                    level, watched, render, x, z, x * 2, z * 2, render.getVerticalDataPointView(x, z), data, true);
                org.mockito.Mockito.verify(watched, org.mockito.Mockito.times(1)).getRetainedSemanticHorizontalContributors(x, z);
                org.mockito.Mockito.verify(watched, org.mockito.Mockito.never()).getSemanticHorizontalContributors(x, z);
                var received = render.getRetainedSemanticHorizontalContributors(x, z);
                assertNotNull(received); for (int i = 0; i < 4; i++) assertSame(expected[i], received[i]);
            }
        } finally { compression.setWithoutFiringEvents(previous); }
    }
    @Test void quadrantSemanticProducerMatchesOriginalPredicateAndEveryAdmittedFootprint() throws Exception {
        var predicate = FullDataSourceV2.class.getDeclaredMethod("semanticallyUniformTwoByTwo",
            FullDataSourceV2.class, int.class, int.class, int[].class);
        predicate.setAccessible(true);
        int admitted = 0, uniformCount = 0;
        for (int test = 0; test < 32; test++) {
            try (var source = FullDataSourceV2.createNative(NativeDhColumnsTest.savedNativeColumns(test));
                 var materials = NativeDhMaterials.create(source.mapping)) {
                var columns = new LongList[4096]; var proofs = new boolean[4096];
                for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) {
                    columns[x * 64 + z] = source.getColumnAtRelPos(x, z);
                    proofs[x * 64 + z] = source.hasSemanticHorizontalUniformity(x, z);
                }
                int[] map = java.util.stream.IntStream.range(0, source.mapping.size()).map(i -> source.mapping.size() - 1 - i).toArray();
                var parent = NativeDhParentColumns.build(materials, columns, map, source.columnWorldCompressionMode.toByteArray(), proofs, new boolean[1024], 0);
                assertNotNull(parent);
                for (int x = 0; x < 64; x += 2) for (int z = 0; z < 64; z += 2) {
                    boolean expected = (boolean) predicate.invoke(null, source, x, z, map);
                    assertEquals(expected, parent.isUniform(x / 2, z / 2), "case " + test + " cell " + x + "," + z);
                    var footprint = parent.contributors(x / 2, z / 2);
                    if (expected) { uniformCount++; assertNull(footprint); continue; }
                    admitted++; assertNotNull(footprint); assertTrue(NativeDhContributors.isSnapshot(footprint));
                    for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) {
                        var input = source.getColumnAtRelPos(x + dx, z + dz); var output = footprint[dx * 2 + dz];
                        if (input == null) { assertNull(output); continue; }
                        assertEquals(Math.min(256, input.size()), output.size());
                        for (int i = 0; i < output.size(); i++) assertEquals(FullDataPointUtil.remap(map, input.getLong(i)), output.getLong(i));
                    }
                }
            }
        }
        assertTrue(admitted > 1000); assertTrue(uniformCount > 0);
    }
    @Test void nativeQuadrantAdmissionPreservesCapacityAndReplacementOrder() {
        try (var source = FullDataSourceV2.createNative(NativeDhColumnsTest.savedNativeColumns(1));
             var materials = NativeDhMaterials.create(source.mapping)) {
            var columns = new LongList[4096];
            for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) columns[x * 64 + z] = source.getColumnAtRelPos(x, z);
            int[] map = java.util.stream.IntStream.range(0, source.mapping.size()).toArray();
            var existing = new boolean[1024]; existing[1] = true;
            var parent = NativeDhParentColumns.build(materials, columns, map, new byte[4096], new boolean[4096], existing, 1024);
            assertNotNull(parent); assertNull(parent.contributors(0, 0)); assertNotNull(parent.contributors(0, 1)); assertNull(parent.contributors(0, 2));
            var retained = parent.contributors(0, 1); var expected = new LongArrayList(retained[0]);
            assertNull(NativeDhParentColumns.build(materials, columns, map, new byte[4096], new boolean[4096], existing, 0), "Inconsistent membership facts decline");
            source.getColumnAtRelPos(0, 2).clear();
            assertEquals(expected, retained[0], "Independent immutable values survive source mutation");
            assertThrows(UnsupportedOperationException.class, () -> retained[0].add(0, 7));
        }
    }

    @Test void nativeQuadrantDistinguishesNullFromEmptyAndDeclinesMalformedFacts() {
        try (var source = FullDataSourceV2.createNative(NativeDhColumnsTest.savedNativeColumns(1));
             var materials = NativeDhMaterials.create(source.mapping)) {
            var columns = new LongList[4096]; java.util.Arrays.fill(columns, NativeDhColumnList.empty()); columns[0] = null;
            var proofs = new boolean[4096]; java.util.Arrays.fill(proofs, true);
            int[] map = java.util.stream.IntStream.range(0, source.mapping.size()).toArray();
            var parent = NativeDhParentColumns.build(materials, columns, map, new byte[4096], proofs, null, 0);
            assertNotNull(parent); assertFalse(parent.isUniform(0, 0)); assertTrue(parent.isUniform(0, 1));
            var footprint = parent.contributors(0, 0); assertNotNull(footprint); assertNull(footprint[0]);
            for (int i = 1; i < 4; i++) { assertNotNull(footprint[i]); assertTrue(footprint[i].isEmpty()); }
            assertNull(parent.contributors(0, 1));
            assertNull(NativeDhParentColumns.build(materials, columns, map, new byte[4095], proofs, null, 0));
            assertNull(NativeDhParentColumns.build(materials, columns, map, new byte[4096], new boolean[4095], null, 0));
            assertNull(NativeDhParentColumns.build(materials, columns, map, new byte[4096], proofs, new boolean[1023], 0));
        }
    }
    @Test void resizedPublicCompressionListRetainsOriginalExceptionAndSuccessfulUpdatePrefix() {
        try (var source = FullDataSourceV2.createNative(NativeDhColumnsTest.savedNativeColumns(1));
             var legacy = FullDataSourceV2.createNative(NativeDhColumnsTest.savedNativeColumns(1));
             var parent = FullDataSourceV2.createEmpty(DhSectionPos.getParentPos(source.getPos()));
             var expected = FullDataSourceV2.createEmpty(DhSectionPos.getParentPos(source.getPos()))) {
            legacy.setColumn(0, new LongArrayList(legacy.getColumn(0))); // Force the original per-group Java path.
            source.columnWorldCompressionMode.size(4095); legacy.columnWorldCompressionMode.size(4095);
            var actualError = assertThrows(IndexOutOfBoundsException.class, () -> parent.updateFromDataSource(source));
            var expectedError = assertThrows(IndexOutOfBoundsException.class, () -> expected.updateFromDataSource(legacy));
            assertEquals(expectedError.getClass(), actualError.getClass()); assertEquals(expectedError.getMessage(), actualError.getMessage());
            assertEquals(expected.columnGenerationSteps, parent.columnGenerationSteps);
            assertEquals(expected.columnWorldCompressionMode, parent.columnWorldCompressionMode);
            assertEquals(expected.mapping.size(), parent.mapping.size());
            for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) {
                assertEquals(expected.getColumnAtRelPos(x, z), parent.getColumnAtRelPos(x, z));
                assertEquals(expected.hasSemanticHorizontalUniformity(x, z), parent.hasSemanticHorizontalUniformity(x, z));
                assertArrayEquals(expected.getSemanticHorizontalContributors(x, z), parent.getSemanticHorizontalContributors(x, z));
            }
        }
    }

}
