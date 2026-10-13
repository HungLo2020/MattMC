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
                        assertInstanceOf(NativeDhContributors.class, owner.get(reader), "Ordinary parent footprints are Rust-owned snapshots");
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
                var force = net.vulkanic.world.DistantHorizonsSemanticCollector.FORCE_SEMANTIC_PRESERVATION_PROPERTY;
                try (var ordinary = FullDataToRenderDataTransformer.transformFullDataToRenderSource(watched, level)) {
                    org.mockito.Mockito.verify(watched, org.mockito.Mockito.never()).getRetainedSemanticHorizontalContributors(x, z);
                    assertNull(ordinary.getRetainedSemanticHorizontalContributors(x, z));
                }
                System.setProperty(force, "true");
                LongList[] received;
                try (var render = FullDataToRenderDataTransformer.transformFullDataToRenderSource(watched, level)) {
                    org.mockito.Mockito.verify(watched, org.mockito.Mockito.times(1)).getRetainedSemanticHorizontalContributors(x, z);
                    org.mockito.Mockito.verify(watched, org.mockito.Mockito.never()).getSemanticHorizontalContributors(x, z);
                    received = render.getRetainedSemanticHorizontalContributors(x, z);
                } finally { System.clearProperty(force); }
                // Each read returns fresh views over the same Rust snapshot.
                assertNotNull(received); for (int i = 0; i < 4; i++) assertEquals(expected[i], received[i]);
            }
        } finally { compression.setWithoutFiringEvents(previous); }
    }
    /** Frozen's {@code semanticallyUniformTwoByTwo}, as the reference for the Rust predicate. */
    private static boolean frozenUniform(FullDataSourceV2 source, int x, int z, int[] remap) {
        for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) {
            int index = FullDataSourceV2.relativePosToIndex(x + dx, z + dz);
            if (source.columnWorldCompressionMode.getByte(index) != EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS.value
                || !source.hasSemanticHorizontalUniformity(x + dx, z + dz)) return false;
        }
        LongList reference = source.getColumnAtRelPos(x, z);
        for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) {
            LongList other = source.getColumnAtRelPos(x + dx, z + dz);
            if (reference == null || other == null) { if (reference != other) return false; continue; }
            if (reference.size() != other.size()) return false;
            for (int i = 0; i < reference.size(); i++) {
                long a = reference.getLong(i), b = other.getLong(i);
                if (FullDataPointUtil.getBottomY(a) != FullDataPointUtil.getBottomY(b) || FullDataPointUtil.getHeight(a) != FullDataPointUtil.getHeight(b)) return false;
                int left = FullDataPointUtil.getId(a), right = FullDataPointUtil.getId(b);
                if (left < 0 || left >= remap.length || right < 0 || right >= remap.length || remap[left] != remap[right]) return false;
            }
        }
        return true;
    }
    private static int[] offsets(FullDataSourceV2 parent, FullDataSourceV2 child) {
        long childZero = DhSectionPos.getChildByIndex(parent.getPos(), 0);
        return new int[]{DhSectionPos.getX(child.getPos()) == DhSectionPos.getX(childZero) ? 0 : 32,
            DhSectionPos.getZ(child.getPos()) == DhSectionPos.getZ(childZero) ? 0 : 32};
    }
    @Test void parentUniformityMatchesFrozenPredicateAndEveryAdmittedFootprintIsRemapped() throws Exception {
        var compression = Config.Common.LodBuilding.worldCompression; var previous = compression.get();
        compression.setWithoutFiringEvents(EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
        int admitted = 0, uniformCount = 0;
        try {
            for (int test = 0; test < 32; test++) {
                try (var source = FullDataSourceV2.createNative(NativeDhColumnsTest.savedNativeColumns(test));
                     var parent = FullDataSourceV2.createEmpty(DhSectionPos.getParentPos(source.getPos()))) {
                    // A reversed parent mapping makes the remap non-identity.
                    for (int id = source.mapping.size() - 1; id >= 0; id--)
                        parent.mapping.addIfNotPresentAndGetId(source.mapping.getBiomeWrapper(id), source.mapping.getBlockStateWrapper(id));
                    int[] map = parent.mapping.mergeAndReturnRemappedEntityIds(source.mapping);
                    parent.updateFromDataSource(source);
                    int[] at = offsets(parent, source);
                    for (int x = 0; x < 64; x += 2) for (int z = 0; z < 64; z += 2) {
                        boolean expected = frozenUniform(source, x, z, map);
                        int px = at[0] + x / 2, pz = at[1] + z / 2;
                        assertEquals(expected, parent.hasSemanticHorizontalUniformity(px, pz), "case " + test + " cell " + x + "," + z);
                        var footprint = parent.getRetainedSemanticHorizontalContributors(px, pz);
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
        } finally { compression.setWithoutFiringEvents(previous); }
        assertTrue(admitted > 1000); assertTrue(uniformCount > 0);
    }
    @Test void parentFootprintAdmissionPreservesCapacityAndReplacementOrder() {
        try (var source = FullDataSourceV2.createNative(NativeDhColumnsTest.savedNativeColumns(1));
             var parent = FullDataSourceV2.createEmpty(DhSectionPos.getParentPos(source.getPos()))) {
            for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) source.setSemanticHorizontalUniformity(x, z, false);
            int[] at = offsets(parent, source);
            LongList[] filler = {new LongArrayList(new long[]{0}), null, null, null};
            // 1023 footprints outside the quadrant plus one at its cell (0, 1): the table is full.
            int placed = 0;
            for (int x = 0; x < 64 && placed < 1023; x++) for (int z = 0; z < 64 && placed < 1023; z++) {
                boolean inQuadrant = x >= at[0] && x < at[0] + 32 && z >= at[1] && z < at[1] + 32;
                if (!inQuadrant) { parent.setSemanticHorizontalContributors(x, z, filler); placed++; }
            }
            parent.setSemanticHorizontalContributors(at[0], at[1] + 1, filler);
            assertTrue(parent.updateFromDataSource(source));
            assertNull(parent.getSemanticHorizontalContributors(at[0], at[1]), "full: the first cell gets none");
            var retained = parent.getRetainedSemanticHorizontalContributors(at[0], at[1] + 1);
            assertNotNull(retained, "a cell that releases its old footprint may take a new one");
            assertNull(parent.getSemanticHorizontalContributors(at[0], at[1] + 2));
            var expected = new LongArrayList(retained[0]);
            source.getColumnAtRelPos(0, 2).clear();
            assertEquals(expected, retained[0], "Independent immutable values survive source mutation");
            assertThrows(UnsupportedOperationException.class, () -> retained[0].add(0, 7));
        }
    }
    @Test void parentFootprintsDistinguishMissingFromEmptyColumns() {
        var compression = Config.Common.LodBuilding.worldCompression; var previous = compression.get();
        compression.setWithoutFiringEvents(EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
        try (var source = FullDataSourceV2.createEmpty(DhSectionPos.encode(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, 0, 0));
             var parent = FullDataSourceV2.createEmpty(DhSectionPos.getParentPos(source.getPos()))) {
            source.mapping.addIfNotPresentAndGetId(NativeDhColumnsTest.occlusionBiome(0), com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper.AIR);
            source.setColumn(0, null);
            parent.updateFromDataSource(source);
            assertFalse(parent.hasSemanticHorizontalUniformity(0, 0)); assertTrue(parent.hasSemanticHorizontalUniformity(0, 1));
            var footprint = parent.getSemanticHorizontalContributors(0, 0); assertNotNull(footprint); assertNull(footprint[0]);
            for (int i = 1; i < 4; i++) { assertNotNull(footprint[i]); assertTrue(footprint[i].isEmpty()); }
            assertNull(parent.getSemanticHorizontalContributors(0, 1));
        } finally { compression.setWithoutFiringEvents(previous); }
    }
}
