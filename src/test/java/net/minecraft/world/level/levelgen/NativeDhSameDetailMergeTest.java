package net.minecraft.world.level.levelgen;

import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import it.unimi.dsi.fastutil.longs.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.world.level.chunk.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual retained-owner ABI and ordinary source consumer; supplemental CPU proof. */
@Tag("parity")
class NativeDhSameDetailMergeTest {
    @BeforeAll static void bootstrap() throws Exception { NativeDhColumnsTest.bootstrap(); }
    private record Column(byte generation, byte compression, long[] points) {}
    private static Column[] read(DataInputStream in) throws IOException {
        var result = new Column[4096];
        for (int i = 0; i < result.length; i++) {
            byte generation = in.readByte(), compression = in.readByte(); int n = in.readInt();
            long[] points = n < 0 ? null : new long[n];
            if (points != null) for (int p = 0; p < n; p++) points[p] = in.readLong();
            result[i] = new Column(generation, compression, points);
        }
        return result;
    }
    private static FullDataSourceV2 source(Column[] state) {
        var source = FullDataSourceV2.createEmpty(6);
        for (int i = 0; i < 4096; i++) {
            source.columnGenerationSteps.set(i, state[i].generation); source.columnWorldCompressionMode.set(i, state[i].compression);
            if (state[i].points == null) source.dataPoints[i] = null;
            else source.dataPoints[i].addElements(0, state[i].points);
        }
        return source;
    }
    private static void forceOriginalRoute(FullDataSourceV2 source) {
        for (int i = 0; i < 4096; i++) if (source.dataPoints[i] != null) {
            source.dataPoints[i] = new LongArrayList(source.dataPoints[i]); return;
        }
        throw new AssertionError("Fixture requires one original target reader");
    }
    private static boolean update(FullDataSourceV2 target, FullDataSourceV2 source, int[] remap) throws Exception {
        var method = FullDataSourceV2.class.getDeclaredMethod("updateFromSameDetailLevel", FullDataSourceV2.class, int[].class);
        method.setAccessible(true); return (boolean)method.invoke(target, source, remap);
    }
    private static void check(Column[] expected, FullDataSourceV2 source, String context) {
        for (int i = 0; i < 4096; i++) {
            assertEquals(expected[i].generation, source.columnGenerationSteps.getByte(i), context + " generation " + i);
            assertEquals(expected[i].compression, source.columnWorldCompressionMode.getByte(i), context + " compression " + i);
            if (expected[i].points == null) assertNull(source.dataPoints[i], context + " null " + i);
            else {
                assertArrayEquals(expected[i].points, source.dataPoints[i].toLongArray(), context + " points " + i);
                assertEquals(new LongArrayList(expected[i].points).hashCode(), source.dataPoints[i].hashCode(), context + " hash " + i);
            }
        }
    }
    @Test void nativePreparationAndPublicationMatchAll48ActualFrozenCasesAndBothUpdates() throws Exception {
        try (var in = new DataInputStream(new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/world/chunk/frozen-dh-same-detail.bin.gz"))))) {
            assertEquals(0x44484d31, in.readInt());
            assertEquals("dd03b56d30b6f7f02755a6251e30a538f49936da38fcc34777f1c4d0ff9b0ed4", in.readUTF());
            assertEquals(64, in.readUTF().length()); assertTrue(in.readUTF().contains("65536 signed generation pairs")); assertEquals(48, in.readInt());
            for (int test = 0; test < 48; test++) {
                String name = in.readUTF(); int[] remap = new int[in.readInt()]; for (int i = 0; i < remap.length; i++) remap[i] = in.readInt();
                boolean empty = in.readBoolean(); var targetState = read(in); var inputState = read(in);
                try (var target = source(targetState); var original = source(targetState); var input = source(inputState)) {
                    target.isEmpty = original.isEmpty = empty; forceOriginalRoute(original);
                    LongList[] identities = target.dataPoints.clone();
                    for (int pass = 0; pass < 2; pass++) {
                        boolean changed = in.readBoolean(), expectedEmpty = in.readBoolean(); var expected = read(in);
                        var nativeMerge = NativeDhSameDetailMerge.prepare(target.dataPoints, input.dataPoints, target.columnGenerationSteps,
                            target.columnWorldCompressionMode, input.columnGenerationSteps, input.columnWorldCompressionMode,
                            new boolean[4096], Map.of(), Map.of(), remap);
                        assertNotNull(nativeMerge, name + " must admit actual native ABI"); assertEquals(changed, nativeMerge.changed());
                        nativeMerge.commit(target.dataPoints);
                        for (int i = 0; i < 4096; i++) if (nativeMerge.updated(i)) {
                            target.columnGenerationSteps.set(i, nativeMerge.generation(i)); target.columnWorldCompressionMode.set(i, nativeMerge.compression(i));
                            target.isEmpty = false;
                            if (identities[i] != null) assertSame(identities[i], target.dataPoints[i]);
                        }
                        assertEquals(changed, update(original, input, remap), name + " original route flag");
                        assertEquals(expectedEmpty, target.isEmpty); assertEquals(expectedEmpty, original.isEmpty);
                        check(expected, target, name + " native pass " + pass); check(expected, original, name + " original pass " + pass);
                    }
                    check(inputState, input, name + " input remains unchanged");
                }
            }
            assertEquals(-1, in.read());
        }
    }
    @Test void ordinarySavedProducerUsesBatchFootprintsAndRetainedReadersSurviveSourceClose() throws Exception {
        var setting = Config.Common.LodBuilding.worldCompression; var previous = setting.get();
        setting.setWithoutFiringEvents(EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
        LongList[] retained = null; LongArrayList retainedPoints = null;
        try {
            var columns = NativeDhColumnsTest.savedNativeColumns(1); assertNotNull(columns);
            int x = columns.offsetX(), z = columns.offsetZ(), i = x * 64 + z;
            try (var input = FullDataSourceV2.createNative(columns); var target = FullDataSourceV2.createEmpty(columns.position())) {
                input.setSemanticHorizontalUniformity(x, z, false);
                input.setSemanticHorizontalContributors(x, z, new LongList[]{input.dataPoints[i], null, new LongArrayList(), input.dataPoints[i]});
                var identity = target.dataPoints[i]; int sourceHash = input.hashCode();
                assertTrue(target.updateFromDataSource(input)); assertSame(identity, target.dataPoints[i]);
                assertEquals(input.dataPoints[i], target.dataPoints[i]); assertEquals(sourceHash, input.hashCode());
                retained = target.getRetainedSemanticHorizontalContributors(x, z); assertNotNull(retained);
                var owner = retained[0].getClass().getDeclaredField("owner"); owner.setAccessible(true);
                var batch = assertInstanceOf(NativeDhSameDetailMerge.class, owner.get(retained[0]), "Ordinary update must use complete native producer");
                var lease = NativeDhSameDetailMerge.class.getDeclaredField("sourceLease"); lease.setAccessible(true);
                assertNull(lease.get(batch), "Committed readers must not retain the borrowed source array");
                retainedPoints = new LongArrayList(retained[0]); assertThrows(UnsupportedOperationException.class, () -> target.getRetainedSemanticHorizontalContributors(x,z)[0].set(0, 9));
                assertFalse(target.updateFromDataSource(input), "Merge-only repeat preserves Frozen change detection");
                input.dataPoints[i].clear(); assertEquals(retainedPoints, retained[0]);
            }
            assertEquals(retainedPoints, retained[0], "Committed footprints release borrowed source leases and own independent points");
        } finally { setting.setWithoutFiringEvents(previous); }
    }
    @Test void orderedFootprintAdmissionMatchesOriginalRouteAtCapacity() throws Exception {
        try (var input = FullDataSourceV2.createEmpty(6); var target = FullDataSourceV2.createEmpty(6); var original = FullDataSourceV2.createEmpty(6)) {
            LongList[] footprint = {new LongArrayList(new long[]{0}), null, new LongArrayList(), new LongArrayList(new long[]{0})};
            for (int i = 0; i < 4096; i++) {
                if (i < 3) { input.dataPoints[i].add(0); input.columnGenerationSteps.set(i, (byte)9); input.setSemanticHorizontalUniformity(i / 64, i % 64, i == 0); }
                if (i == 0 || i >= 100 && i < 1123) {
                    target.setSemanticHorizontalContributors(i / 64, i % 64, footprint); original.setSemanticHorizontalContributors(i / 64, i % 64, footprint);
                }
            }
            input.setSemanticHorizontalContributors(0, 1, footprint); input.setSemanticHorizontalContributors(0, 2, footprint);
            forceOriginalRoute(original); assertEquals(update(original, input, new int[]{7}), update(target, input, new int[]{7}));
            for (int i = 0; i < 4096; i++) {
                assertEquals(original.dataPoints[i], target.dataPoints[i]); assertEquals(original.hasSemanticHorizontalUniformity(i/64,i%64), target.hasSemanticHorizontalUniformity(i/64,i%64));
                assertArrayEquals(original.getSemanticHorizontalContributors(i/64,i%64), target.getSemanticHorizontalContributors(i/64,i%64));
            }
            assertNotNull(target.getSemanticHorizontalContributors(0,1)); assertNull(target.getSemanticHorizontalContributors(0,2));
        }
    }
    @Test void invalidRemapAndResizedMetadataRetainOriginalSuccessfulPrefix() throws Exception {
        for (boolean shortMetadata : new boolean[]{false,true}) {
            try (var input = FullDataSourceV2.createEmpty(6); var target = FullDataSourceV2.createEmpty(6); var original = FullDataSourceV2.createEmpty(6)) {
                input.dataPoints[0].add(0); input.columnGenerationSteps.set(0,(byte)9);
                int bad = shortMetadata ? 4095 : 1; input.columnGenerationSteps.set(bad,(byte)9);
                input.dataPoints[bad].addElements(0,new long[]{1, shortMetadata ? 0 : 2});
                if (shortMetadata) input.columnWorldCompressionMode.size(4095);
                forceOriginalRoute(original);
                var expected = assertThrows(InvocationTargetException.class, () -> update(original,input,new int[]{7,8})).getCause();
                var actual = assertThrows(InvocationTargetException.class, () -> update(target,input,new int[]{7,8})).getCause();
                assertEquals(expected.getClass(),actual.getClass()); assertEquals(expected.getMessage(),actual.getMessage());
                assertEquals(original.columnGenerationSteps,target.columnGenerationSteps); assertEquals(original.columnWorldCompressionMode,target.columnWorldCompressionMode);
                for (int i = 0; i < 4096; i++) assertEquals(original.dataPoints[i],target.dataPoints[i],"Successful prefix column "+i);
            }
        }
    }
    @Test void listAliasesSharedMetadataAndCustomCallbacksDeclineBeforeNativePublication() throws Exception {
        try (var input = FullDataSourceV2.createEmpty(6); var target = FullDataSourceV2.createEmpty(6)) {
            target.dataPoints[0].add(42); var old = new LongArrayList(target.dataPoints[0]);
            input.dataPoints[0] = target.dataPoints[0]; input.columnGenerationSteps.set(0,(byte)9);
            assertNull(NativeDhSameDetailMerge.prepare(target.dataPoints,input.dataPoints,target.columnGenerationSteps,target.columnWorldCompressionMode,
                input.columnGenerationSteps,input.columnWorldCompressionMode,new boolean[4096],Map.of(),Map.of(),new int[]{0}));
            assertEquals(old,target.dataPoints[0]);
            int[] calls = {0};
            input.dataPoints[0] = new LongArrayList(new long[]{0}) {
                @Override public int size() { calls[0]++; throw new IllegalStateException("original custom size callback"); }
            };
            assertNull(NativeDhSameDetailMerge.prepare(target.dataPoints,input.dataPoints,target.columnGenerationSteps,target.columnWorldCompressionMode,
                input.columnGenerationSteps,input.columnWorldCompressionMode,new boolean[4096],Map.of(),Map.of(),new int[]{0}));
            assertEquals(0,calls[0]);
            var error = assertThrows(InvocationTargetException.class, () -> update(target,input,new int[]{0})).getCause();
            assertEquals("original custom size callback",error.getMessage()); assertEquals(1,calls[0]); assertEquals(old,target.dataPoints[0]);
            input.dataPoints[0] = NativeDhColumnList.empty();
            assertNull(NativeDhSameDetailMerge.prepare(target.dataPoints,input.dataPoints,target.columnGenerationSteps,target.columnWorldCompressionMode,
                target.columnGenerationSteps,input.columnWorldCompressionMode,new boolean[4096],Map.of(),Map.of(),new int[]{0}));
        }
    }
}
