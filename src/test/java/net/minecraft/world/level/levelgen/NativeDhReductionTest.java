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

/** Actual native producer -> Rust parent update (the only route); CPU only. */
@Tag("parity")
class NativeDhReductionTest {
    @BeforeAll static void bootstrap() throws Exception { NativeDhColumnsTest.bootstrap(); }
    private static int[] offsets(FullDataSourceV2 parent, FullDataSourceV2 child) {
        long childZero = DhSectionPos.getChildByIndex(parent.getPos(), 0);
        return new int[]{DhSectionPos.getX(child.getPos()) == DhSectionPos.getX(childZero) ? 0 : 32,
            DhSectionPos.getZ(child.getPos()) == DhSectionPos.getZ(childZero) ? 0 : 32};
    }
    @Test void inputsTheOldNativeRouteDeclinedReduceNatively() throws Exception {
        var columns = NativeDhColumnsTest.savedNativeColumns(0); assertNotNull(columns);
        try (var source = FullDataSourceV2.createNative(columns);
             var parent = FullDataSourceV2.createEmpty(DhSectionPos.getParentPos(columns.position()))) {
            // A Java list and an oversized column are ordinary input now.
            source.setColumn(0, new LongArrayList(new long[]{FullDataPointUtil.encode(0, 4, 60, (byte) 0, (byte) 15)}));
            var oversized = NativeDhColumnList.empty(); oversized.addElements(0, new long[4097]); source.setColumn(65, oversized);
            assertTrue(parent.updateFromDataSource(source));
            int[] at = offsets(parent, source);
            assertFalse(parent.getColumnAtRelPos(at[0], at[1]).isEmpty());
            // A sampled ID without a mapping entry fails like Java's mapping lookup.
            var invalid = NativeDhColumnList.empty(); invalid.add(FullDataPointUtil.encode(Integer.MAX_VALUE >> 1, 1, 0, (byte) 0, (byte) 0));
            source.setColumn(2, invalid);
            assertThrows(IndexOutOfBoundsException.class, () -> parent.updateFromDataSource(source));
        }
    }
    @Test void ordinaryParentRemapsExistingMaterialsAndDetectsRepeatedUpdatesAndDetachedWrites() throws Exception {
        var compression = Config.Common.LodBuilding.worldCompression; var previous = compression.get();
        compression.setWithoutFiringEvents(EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
        try {
            var columns = NativeDhColumnsTest.savedNativeColumns(0); assertNotNull(columns);
            try (var source = FullDataSourceV2.createNative(columns);
                 var parent = FullDataSourceV2.createEmpty(DhSectionPos.getParentPos(columns.position()));
                 var identityParent = FullDataSourceV2.createEmpty(DhSectionPos.getParentPos(columns.position()))) {
                int last = source.mapping.size() - 1;
                parent.mapping.addIfNotPresentAndGetId(source.mapping.getBiomeWrapper(last), source.mapping.getBlockStateWrapper(last));
                assertTrue(parent.updateFromDataSource(source));
                assertTrue(identityParent.updateFromDataSource(source));
                int[] remap = parent.mapping.mergeAndReturnRemappedEntityIds(source.mapping);
                assertEquals(0, remap[last]); assertNotEquals(0, remap[0]);
                int[] at = offsets(parent, source);
                for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
                    var expected = identityParent.getColumnAtRelPos(at[0] + x, at[1] + z);
                    var received = parent.getColumnAtRelPos(at[0] + x, at[1] + z);
                    assertEquals(expected.size(), received.size());
                    for (int p = 0; p < expected.size(); p++) assertEquals(FullDataPointUtil.remap(remap, expected.getLong(p)), received.getLong(p));
                }
                assertFalse(parent.updateFromDataSource(source), "Unchanged native results preserve change detection");
                var changed = parent.getColumnAtRelPos(at[0] + columns.offsetX() / 2, at[1] + columns.offsetZ() / 2);
                long original = changed.getLong(0); changed.set(0, original ^ (1L << 60));
                assertTrue(parent.updateFromDataSource(source));
                assertEquals(original, parent.getColumnAtRelPos(at[0] + columns.offsetX() / 2, at[1] + columns.offsetZ() / 2).getLong(0));
                assertEquals(original ^ (1L << 60), changed.getLong(0), "A replaced escaped column remains independent");
            }
        } finally { compression.setWithoutFiringEvents(previous); }
    }
    @Test void parentUpdateMatchesEveryFrozenReductionAndPreservesSourceAndRetainedResults() throws Exception {
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
                    for (var column : source.columnReferences()) {
                        assertEquals(in.readInt(), column.size()); for (int p = 0; p < column.size(); p++) assertEquals(in.readLong(), column.getLong(p));
                    }
                    int sourceHash = source.hashCode(); assertTrue(parent.updateFromDataSource(source));
                    int[] at = offsets(parent, source);
                    for (int x = 0; x < 64; x += 2) for (int z = 0; z < 64; z += 2) {
                        var received = parent.getColumnAtRelPos(at[0] + x / 2, at[1] + z / 2);
                        assertInstanceOf(NativeDhColumnList.class, received, name + " parent keeps Rust ownership");
                        int length = in.readInt(); assertEquals(length, received.size(), name + " " + x + "," + z);
                        for (int p = 0; p < length; p++) assertEquals(in.readLong(), received.getLong(p), name + " " + x + "," + z);
                        assertEquals(new LongArrayList(received).hashCode(), received.hashCode());
                    }
                    assertEquals(sourceHash, source.hashCode());
                    int index = FullDataSourceV2.relativePosToIndex(columns.offsetX(), columns.offsetZ());
                    assertTrue(columns.ownsOriginalColumn(source.getColumn(index), 0, 0), "the child keeps its producer spans");
                    retained = parent.getColumnAtRelPos(at[0] + columns.offsetX() / 2, at[1] + columns.offsetZ() / 2); before = new LongArrayList(retained);
                }
                assertFalse(before.isEmpty()); assertEquals(before, retained, "Parent results survive source and parent close");
                retained.set(0, retained.getLong(0) ^ (1L << 60)); assertNotEquals(before, retained, "Results own independent mutable storage");
            }
            assertEquals(-1, in.read());
        } finally { compression.setWithoutFiringEvents(previous); }
    }
    @Test void parentMetadataMatchesFrozenAndSeededHashesFollowMutation() throws Exception {
        var compression = Config.Common.LodBuilding.worldCompression; var previous = compression.get();
        compression.setWithoutFiringEvents(EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
        try (var input = new DataInputStream(new GZIPInputStream(Objects.requireNonNull(
            NativeDhReductionTest.class.getResourceAsStream("/world/chunk/frozen-dh-parent-metadata.bin.gz"))))) {
            assertEquals(0x44485031, input.readInt());
            assertEquals("dd03b56d30b6f7f02755a6251e30a538f49936da38fcc34777f1c4d0ff9b0ed4", input.readUTF());
            assertEquals(64, input.readUTF().length()); assertTrue(input.readUTF().contains("Actual Frozen")); assertEquals(32, input.readInt());
            try (var source = FullDataSourceV2.createNative(NativeDhColumnsTest.savedNativeColumns(1))) {
                for (int test = 0; test < 32; test++) {
                    byte[] steps = input.readNBytes(4096), modes = input.readNBytes(4096);
                    source.columnGenerationSteps.setElements(0, steps); source.columnWorldCompressionMode.setElements(0, modes);
                    try (var parent = FullDataSourceV2.createEmpty(DhSectionPos.getParentPos(source.getPos()))) {
                        parent.updateFromDataSource(source);
                        int[] at = offsets(parent, source);
                        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
                            int index = FullDataSourceV2.relativePosToIndex(at[0] + x, at[1] + z);
                            assertEquals(input.readByte(), parent.columnGenerationSteps.getByte(index), "generation " + test + " " + x + "," + z);
                            assertEquals(input.readByte(), parent.columnWorldCompressionMode.getByte(index), "compression " + test + " " + x + "," + z);
                            var column = parent.getColumn(index);
                            int seededHash = column.hashCode();
                            assertEquals(new LongArrayList(column).hashCode(), seededHash);
                            if (test == 0 && !column.isEmpty()) {
                                long value = column.getLong(0); column.set(0, value ^ (1L << 60));
                                int expectedHash = new LongArrayList(column).hashCode();
                                assertNotEquals(seededHash, expectedHash, "The mutation changes the wrapped list hash");
                                assertEquals(expectedHash, column.hashCode(), "Rust hash validity observes the mutation");
                            }
                        }
                    }
                }
            }
            assertEquals(-1, input.read());
        } finally { compression.setWithoutFiringEvents(previous); }
    }
    @Test void parentMetadataIsTheSignedGenerationMinimumAndCompressionMaximum() {
        try (var source = FullDataSourceV2.createNative(NativeDhColumnsTest.savedNativeColumns(1));
             var parent = FullDataSourceV2.createEmpty(DhSectionPos.getParentPos(source.getPos()))) {
            for (int i = 0; i < 4096; i++) {
                source.columnGenerationSteps.set(i, (byte)i); source.columnWorldCompressionMode.set(i, (byte)(255 - i));
            }
            parent.updateFromDataSource(source);
            int[] at = offsets(parent, source);
            for (int x = 0; x < 64; x += 2) for (int z = 0; z < 64; z += 2) {
                byte min = Byte.MAX_VALUE, max = Byte.MIN_VALUE;
                for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) {
                    int i = FullDataSourceV2.relativePosToIndex(x + dx, z + dz);
                    min = (byte) Math.min(min, (byte) i); max = (byte) Math.max(max, (byte) (255 - i));
                }
                int index = FullDataSourceV2.relativePosToIndex(at[0] + x / 2, at[1] + z / 2);
                assertEquals(min, parent.columnGenerationSteps.getByte(index));
                assertEquals(max, parent.columnWorldCompressionMode.getByte(index));
            }
        }
    }
    @Test void mutatingProducerColumnsReleasesOnlyThatReadersLease() throws Exception {
        var columns = NativeDhColumnsTest.savedNativeColumns(1); assertNotNull(columns);
        try (var source = FullDataSourceV2.createNative(columns);
             var parent = FullDataSourceV2.createEmpty(DhSectionPos.getParentPos(columns.position()))) {
            assertTrue(parent.updateFromDataSource(source));
            int[] at = offsets(parent, source);
            var parentColumn = parent.getColumnAtRelPos(at[0] + columns.offsetX() / 2, at[1] + columns.offsetZ() / 2);
            var expected = new LongArrayList(parentColumn);
            var oldOwner = NativeDhColumnList.class.getDeclaredField("owner"); oldOwner.setAccessible(true);
            var oldPoints = NativeDhColumnList.class.getDeclaredField("points"); oldPoints.setAccessible(true);
            var producerView = (NativeDhColumnList)source.getColumnAtRelPos(columns.offsetX(), columns.offsetZ());
            assertTrue(producerView.isOriginalProducerView()); assertFalse(producerView.isEmpty());
            var original = new LongArrayList(producerView); producerView.set(0, producerView.getLong(0) ^ (1L << 60));
            assertNull(oldOwner.get(producerView)); assertEquals(0L, ((java.lang.foreign.MemorySegment)oldPoints.get(producerView)).address());
            assertEquals(new LongArrayList(producerView).hashCode(), producerView.hashCode()); assertNotEquals(original, producerView);
            assertEquals(expected, parentColumn, "Parent results are independent of later child writes");
            var cleared = (NativeDhColumnList) source.getColumnAtRelPos(columns.offsetX() + 1, columns.offsetZ());
            cleared.clear(); assertEquals(1, cleared.hashCode()); assertNull(oldOwner.get(cleared));
        }
    }
}
