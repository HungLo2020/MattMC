package net.minecraft.world.level.levelgen;

import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.io.*;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual native producer -> 2x2 producer -> ordinary parent consumer; CPU only. */
@Tag("parity")
class NativeDhReductionTest {
    @BeforeAll static void bootstrap() throws Exception { NativeDhColumnsTest.bootstrap(); }
    @Test void nativeQuadrantDeclinesCompatibilityInputsBeforePublication() {
        var columns = NativeDhColumnsTest.savedNativeColumns(0); assertNotNull(columns);
        try (var source = FullDataSourceV2.createNative(columns); var materials = NativeDhMaterials.create(source.mapping)) {
            assertNotNull(materials); int hash = source.hashCode();
            int[] ids = java.util.stream.IntStream.range(0, source.mapping.size()).toArray();
            var inputs = source.dataPoints.clone(); inputs[0] = new LongArrayList();
            assertNull(NativeDhParentColumns.build(materials, inputs, ids), "Custom list keeps compatibility path");
            var oversized = NativeDhColumnList.empty(); oversized.addElements(0, new long[4097]); inputs[0] = oversized;
            assertNull(NativeDhParentColumns.build(materials, inputs, ids));
            var invalid = NativeDhColumnList.empty(); invalid.add(FullDataPointUtil.encode(Integer.MAX_VALUE, 1, 0, (byte) 0, (byte) 0)); inputs[0] = invalid;
            assertNull(NativeDhParentColumns.build(materials, inputs, ids));
            int[] unsupported = new int[ids.length]; java.util.Arrays.fill(unsupported, -1);
            assertNull(NativeDhParentColumns.build(materials, source.dataPoints, unsupported));
            assertEquals(hash, source.hashCode());
        } catch (com.seibel.distanthorizons.core.util.objects.DataCorruptedException e) { throw new AssertionError(e); }
    }
    @Test void ordinaryParentRemapsExistingMaterialsAndDetectsRepeatedUpdatesAndDetachedWrites() throws Exception {
        var compression = Config.Common.LodBuilding.worldCompression; var previous = compression.get();
        compression.setWithoutFiringEvents(EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
        try {
            var columns = NativeDhColumnsTest.savedNativeColumns(0); assertNotNull(columns);
            try (var source = FullDataSourceV2.createNative(columns);
                 var parent = FullDataSourceV2.createEmpty(DhSectionPos.getParentPos(columns.position()));
                 var materials = NativeDhMaterials.create(source.mapping)) {
                int last = source.mapping.size() - 1;
                parent.mapping.addIfNotPresentAndGetId(source.mapping.getBiomeWrapper(last), source.mapping.getBlockStateWrapper(last));
                assertTrue(parent.updateFromDataSource(source));
                int[] remap = parent.mapping.mergeAndReturnRemappedEntityIds(source.mapping);
                assertEquals(0, remap[last]); assertNotEquals(0, remap[0]);
                long childZero = DhSectionPos.getChildByIndex(parent.getPos(), 0);
                int ox = DhSectionPos.getX(source.getPos()) == DhSectionPos.getX(childZero) ? 0 : 32;
                int oz = DhSectionPos.getZ(source.getPos()) == DhSectionPos.getZ(childZero) ? 0 : 32;
                for (int x = 0; x < 64; x += 2) for (int z = 0; z < 64; z += 2) {
                    var direct = NativeDhColumnList.tryReduce(materials, new LongList[]{source.getColumnAtRelPos(x, z), source.getColumnAtRelPos(x, z + 1),
                        source.getColumnAtRelPos(x + 1, z), source.getColumnAtRelPos(x + 1, z + 1)});
                    var received = parent.getColumnAtRelPos(ox + x / 2, oz + z / 2);
                    assertTrue(((NativeDhColumnList) received).isOriginalParentView()); assertEquals(direct.size(), received.size());
                    for (int p = 0; p < direct.size(); p++) assertEquals(FullDataPointUtil.remap(remap, direct.getLong(p)), received.getLong(p));
                }
                assertFalse(parent.updateFromDataSource(source), "Unchanged native results preserve change detection");
                var changed = parent.getColumnAtRelPos(ox + columns.offsetX() / 2, oz + columns.offsetZ() / 2);
                long original = changed.getLong(0); changed.set(0, original ^ (1L << 60));
                assertTrue(parent.updateFromDataSource(source));
                assertEquals(original, parent.getColumnAtRelPos(ox + columns.offsetX() / 2, oz + columns.offsetZ() / 2).getLong(0));
                assertEquals(original ^ (1L << 60), changed.getLong(0), "Replaced detached column remains independent");
            }
        } finally { compression.setWithoutFiringEvents(previous); }
    }
    @Test void nativeParentConsumerMatchesEveryFrozenReductionAndPreservesSourceAndRetainedResults() throws Exception {
        var compression = Config.Common.LodBuilding.worldCompression; var previous = compression.get();
        compression.setWithoutFiringEvents(EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
        try (var in = new DataInputStream(new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/world/chunk/frozen-dh-reduction.bin.gz"))))) {
            assertEquals(0x44485231, in.readInt()); assertEquals(5, in.readInt());
            for (int n = 0; n < 5; n++) { in.readUTF(); assertEquals(64, in.readUTF().length()); }
            assertEquals("3419a7da318caf78ddec3df594f83841b78a5307033577d7d3073df50b9b01eb", in.readUTF());
            assertTrue(in.readUTF().contains("Supplemental CPU evidence")); assertEquals(32, in.readInt());
            for (int test = 0; test < 32; test++) {
                String name = in.readUTF(); var columns = NativeDhColumnsTest.savedNativeColumns(test); assertNotNull(columns);
                LongList retained; LongArrayList before;
                try (var source = FullDataSourceV2.createNative(columns);
                     var parent = FullDataSourceV2.createEmpty(DhSectionPos.getParentPos(columns.position()))) {
                    assertEquals(source.mapping.size(), in.readInt());
                    for (int id = 0; id < source.mapping.size(); id++) {
                        var wrapper = (BlockStateWrapper) source.mapping.getBlockStateWrapper(id);
                        assertEquals(in.readInt(), wrapper.blockState == null ? 0 : Block.getId(wrapper.blockState));
                        assertEquals(in.readUnsignedByte(), wrapper.getOpacity());
                        assertEquals(in.readBoolean(), wrapper.isAir()); assertEquals(in.readBoolean(), wrapper.isLiquid());
                    }
                    for (var column : source.dataPoints) {
                        assertEquals(in.readInt(), column.size()); for (int p = 0; p < column.size(); p++) assertEquals(in.readLong(), column.getLong(p));
                    }
                    int sourceHash = source.hashCode(); assertTrue(parent.updateFromDataSource(source));
                    long childZero = DhSectionPos.getChildByIndex(parent.getPos(), 0);
                    int ox = DhSectionPos.getX(source.getPos()) == DhSectionPos.getX(childZero) ? 0 : 32;
                    int oz = DhSectionPos.getZ(source.getPos()) == DhSectionPos.getZ(childZero) ? 0 : 32;
                    try (var materials = NativeDhMaterials.create(source.mapping)) {
                        assertNotNull(materials);
                        int[] identity = java.util.stream.IntStream.range(0, source.mapping.size()).toArray();
                        int[] reverse = java.util.stream.IntStream.range(0, source.mapping.size()).map(i -> source.mapping.size() - 1 - i).toArray();
                        var quadrant = NativeDhParentColumns.build(materials, source.dataPoints, identity);
                        var remapped = NativeDhParentColumns.build(materials, source.dataPoints, reverse);
                        assertNotNull(quadrant, name + " whole native quadrant admission");
                        assertNotNull(remapped, name + " whole native remapped quadrant admission");
                        for (int x = 0; x < 64; x += 2) for (int z = 0; z < 64; z += 2) {
                            LongList[] inputs = {source.getColumnAtRelPos(x, z), source.getColumnAtRelPos(x, z + 1),
                                source.getColumnAtRelPos(x + 1, z), source.getColumnAtRelPos(x + 1, z + 1)};
                            var direct = NativeDhColumnList.tryReduce(materials, inputs); assertNotNull(direct, name + " actual native reducer admission");
                            var received = parent.getColumnAtRelPos(ox + x / 2, oz + z / 2);
                            assertInstanceOf(NativeDhColumnList.class, received, name + " parent keeps Rust ownership");
                            assertTrue(((NativeDhColumnList) received).isOriginalParentView(), name + " ordinary update consumed whole native quadrant");
                            var batched = quadrant.column(x / 2, z / 2);
                            var translated = remapped.column(x / 2, z / 2);
                            int length = in.readInt(); assertEquals(length, direct.size()); assertEquals(length, received.size());
                            assertEquals(length, batched.size()); assertEquals(length, translated.size());
                            for (int p = 0; p < length; p++) {
                                long point = in.readLong(); assertEquals(point, direct.getLong(p)); assertEquals(point, received.getLong(p), name + " " + x + "," + z);
                                assertEquals(point, batched.getLong(p)); assertEquals(FullDataPointUtil.remap(reverse, point), translated.getLong(p));
                            }
                            assertEquals(new LongArrayList(direct).hashCode(), direct.hashCode());
                            assertEquals(new LongArrayList(received).hashCode(), received.hashCode());
                            assertEquals(new LongArrayList(translated).hashCode(), translated.hashCode());
                        }
                    }
                    assertEquals(sourceHash, source.hashCode());
                    int index = FullDataSourceV2.relativePosToIndex(columns.offsetX(), columns.offsetZ());
                    assertTrue(columns.ownsOriginalColumn(source.dataPoints[index], 0, 0));
                    retained = parent.getColumnAtRelPos(ox + columns.offsetX() / 2, oz + columns.offsetZ() / 2); before = new LongArrayList(retained);
                }
                assertFalse(before.isEmpty()); assertEquals(before, retained, "Native parent result survives source/parent/material lease close");
                assertTrue(((NativeDhColumnList) retained).isOriginalParentView());
                retained.set(0, retained.getLong(0) ^ (1L << 60)); assertNotEquals(before, retained, "Result retains independent mutable storage");
                assertFalse(((NativeDhColumnList) retained).isOriginalParentView());
            }
            assertEquals(-1, in.read());
        } finally { compression.setWithoutFiringEvents(previous); }
    }
    @Test void nativeMetadataAndSeededHashesMatchFrozenAndMutationInvalidatesTheSeed() throws Exception {
        try (var input = new DataInputStream(new GZIPInputStream(Objects.requireNonNull(
            NativeDhReductionTest.class.getResourceAsStream("/world/chunk/frozen-dh-parent-metadata.bin.gz"))))) {
            assertEquals(0x44485031, input.readInt());
            assertEquals("dd03b56d30b6f7f02755a6251e30a538f49936da38fcc34777f1c4d0ff9b0ed4", input.readUTF());
            assertEquals(64, input.readUTF().length()); assertTrue(input.readUTF().contains("Actual Frozen")); assertEquals(32, input.readInt());
            try (var source = FullDataSourceV2.createNative(NativeDhColumnsTest.savedNativeColumns(1));
                 var materials = NativeDhMaterials.create(source.mapping)) {
                int[] map = java.util.stream.IntStream.range(0, source.mapping.size()).map(i -> source.mapping.size() - 1 - i).toArray();
                for (int test = 0; test < 32; test++) {
                    byte[] steps = input.readNBytes(4096), modes = input.readNBytes(4096);
                    var parent = NativeDhParentColumns.build(materials, source.dataPoints, map, modes, null, null, 1024, steps);
                    assertNotNull(parent);
                    for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
                        assertEquals(input.readByte(), parent.generationStep(x, z)); assertEquals(input.readByte(), parent.compressionMode(x, z));
                        var column = parent.column(x, z);
                        var hashValid = NativeDhColumnList.class.getDeclaredField("hashValid"); hashValid.setAccessible(true);
                        assertTrue(hashValid.getBoolean(column), "Native parent seeds the immutable hash before the first hash read");
                        assertEquals(new LongArrayList(column).hashCode(), column.hashCode());
                        if (!column.isEmpty()) {
                            long value = column.getLong(0); column.set(0, value ^ (1L << 60));
                            assertFalse(hashValid.getBoolean(column), "Mutation invalidates the seeded hash");
                            var oldOwner = NativeDhColumnList.class.getDeclaredField("owner"); oldOwner.setAccessible(true);
                            var oldPoints = NativeDhColumnList.class.getDeclaredField("points"); oldPoints.setAccessible(true);
                            assertNull(oldOwner.get(column), "Detached vectors stop retaining the parent producer");
                            assertEquals(0L, ((java.lang.foreign.MemorySegment)oldPoints.get(column)).address(), "Old arena-backed segments are released too");
                            assertEquals(new LongArrayList(column).hashCode(), column.hashCode());
                        }
                    }
                    steps[0] ^= 0x7f; modes[0] ^= 0x7f;
                    assertNull(NativeDhParentColumns.build(materials, source.dataPoints, map, modes, null, null, 1024, new byte[4095]));
                }
            }
            assertEquals(-1, input.read());
        }
    }
    @Test void ordinaryParentPublishesNativeMetadataAndPreservesResizedGenerationExceptionPrefix() {
        try (var source = FullDataSourceV2.createNative(NativeDhColumnsTest.savedNativeColumns(1));
             var legacy = FullDataSourceV2.createNative(NativeDhColumnsTest.savedNativeColumns(1));
             var parent = FullDataSourceV2.createEmpty(DhSectionPos.getParentPos(source.getPos()));
             var expected = FullDataSourceV2.createEmpty(DhSectionPos.getParentPos(source.getPos()))) {
            legacy.dataPoints[0] = new LongArrayList(legacy.dataPoints[0]);
            for (int i = 0; i < 4096; i++) {
                source.columnGenerationSteps.set(i, (byte)i); legacy.columnGenerationSteps.set(i, (byte)i);
                source.columnWorldCompressionMode.set(i, (byte)(255 - i)); legacy.columnWorldCompressionMode.set(i, (byte)(255 - i));
            }
            assertEquals(expected.updateFromDataSource(legacy), parent.updateFromDataSource(source));
            assertEquals(expected.columnGenerationSteps, parent.columnGenerationSteps);
            assertEquals(expected.columnWorldCompressionMode, parent.columnWorldCompressionMode);
            boolean expectedRepeat = expected.updateFromDataSource(legacy);
            boolean actualRepeat = parent.updateFromDataSource(source);
            assertEquals(expectedRepeat, actualRepeat, "Repeat updates preserve the original Java route, including visual-compression culling");
            for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) assertEquals(expected.getColumnAtRelPos(x, z), parent.getColumnAtRelPos(x, z));
            source.columnGenerationSteps.size(4095); legacy.columnGenerationSteps.size(4095);
            var actualError = assertThrows(IndexOutOfBoundsException.class, () -> parent.updateFromDataSource(source));
            var expectedError = assertThrows(IndexOutOfBoundsException.class, () -> expected.updateFromDataSource(legacy));
            assertEquals(expectedError.getClass(), actualError.getClass()); assertEquals(expectedError.getMessage(), actualError.getMessage());
            assertEquals(expected.columnGenerationSteps, parent.columnGenerationSteps); assertEquals(expected.columnWorldCompressionMode, parent.columnWorldCompressionMode);
            for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) assertEquals(expected.getColumnAtRelPos(x, z), parent.getColumnAtRelPos(x, z));
        }
    }

    @Test void clearingAndMutatingProducerColumnsReleaseOnlyTheDetachedReadersOldLease() throws Exception {
        var columns = NativeDhColumnsTest.savedNativeColumns(1); assertNotNull(columns);
        try (var source = FullDataSourceV2.createNative(columns); var materials = NativeDhMaterials.create(source.mapping)) {
            int[] map = java.util.stream.IntStream.range(0, source.mapping.size()).toArray();
            var parent = NativeDhParentColumns.build(materials, source.dataPoints, map); assertNotNull(parent);
            int x = columns.offsetX() / 2, z = columns.offsetZ() / 2;
            var cleared = parent.column(x, z); var retained = parent.column(x, z); assertFalse(cleared.isEmpty());
            var expected = new LongArrayList(retained);
            var oldOwner = NativeDhColumnList.class.getDeclaredField("owner"); oldOwner.setAccessible(true);
            var oldPoints = NativeDhColumnList.class.getDeclaredField("points"); oldPoints.setAccessible(true);
            cleared.clear(); assertEquals(1, cleared.hashCode()); assertNull(oldOwner.get(cleared));
            assertEquals(0L, ((java.lang.foreign.MemorySegment)oldPoints.get(cleared)).address());
            assertEquals(expected, retained); assertTrue(retained.isOriginalParentView());
            var producerView = (NativeDhColumnList)source.getColumnAtRelPos(columns.offsetX(), columns.offsetZ());
            assertTrue(producerView.isOriginalProducerView()); assertFalse(producerView.isEmpty());
            var original = new LongArrayList(producerView); producerView.set(0, producerView.getLong(0) ^ (1L << 60));
            assertNull(oldOwner.get(producerView)); assertEquals(0L, ((java.lang.foreign.MemorySegment)oldPoints.get(producerView)).address());
            assertEquals(new LongArrayList(producerView).hashCode(), producerView.hashCode()); assertNotEquals(original, producerView);
            assertEquals(expected, retained, "Other consumers keep their immutable producer values");
        }
    }

}
