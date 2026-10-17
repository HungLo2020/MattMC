package net.minecraft.world.level.levelgen;

import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import it.unimi.dsi.fastutil.longs.*;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.world.level.chunk.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The Rust same-detail merge (the only route) against the recorded actual
 * Frozen corpus, plus footprints, hashing, lifetimes and corrupt input.
 */
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
            if (state[i].points == null) source.setColumn(i, null);
            else source.getColumn(i).addElements(0, state[i].points);
        }
        return source;
    }
    /** The same-detail merge alone (no culling), as {@code updateFromSameDetailLevel}. */
    private static boolean update(FullDataSourceV2 target, FullDataSourceV2 source, int[] remap) {
        var update = NativeDhSameDetailMerge.update(target.nativeSource(), source.nativeSource(), remap, target.getPos());
        if (update.metadataUpdated()) target.isEmpty = false;
        return update.changed();
    }
    private static void check(Column[] expected, FullDataSourceV2 source, String context) {
        for (int i = 0; i < 4096; i++) {
            assertEquals(expected[i].generation, source.columnGenerationSteps.getByte(i), context + " generation " + i);
            assertEquals(expected[i].compression, source.columnWorldCompressionMode.getByte(i), context + " compression " + i);
            if (expected[i].points == null) assertNull(source.getColumn(i), context + " null " + i);
            else {
                assertArrayEquals(expected[i].points, source.getColumn(i).toLongArray(), context + " points " + i);
                assertEquals(new LongArrayList(expected[i].points).hashCode(), source.getColumn(i).hashCode(), context + " hash " + i);
            }
        }
    }
    /** Frozen's {@code generateHashCode} over a recorded state. */
    private static int frozenHash(Column[] state) {
        Object[] columns = new Object[4096];
        var generations = new it.unimi.dsi.fastutil.bytes.ByteArrayList();
        var compression = new it.unimi.dsi.fastutil.bytes.ByteArrayList();
        for (int i = 0; i < 4096; i++) {
            columns[i] = state[i].points == null ? null : new LongArrayList(state[i].points);
            generations.add(state[i].generation); compression.add(state[i].compression);
        }
        return 43 * (17 * (31 * Long.hashCode(6) + Arrays.deepHashCode(columns)) + generations.hashCode()) + compression.hashCode();
    }
    @Test void mergeMatchesAll48ActualFrozenCasesForBothUpdates() throws Exception {
        try (var in = new DataInputStream(new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/world/chunk/frozen-dh-same-detail.bin.gz"))))) {
            assertEquals(0x44484d31, in.readInt());
            assertEquals("dd03b56d30b6f7f02755a6251e30a538f49936da38fcc34777f1c4d0ff9b0ed4", in.readUTF());
            assertEquals(64, in.readUTF().length()); assertTrue(in.readUTF().contains("65536 signed generation pairs")); assertEquals(48, in.readInt());
            for (int test = 0; test < 48; test++) {
                String name = in.readUTF(); int[] remap = new int[in.readInt()]; for (int i = 0; i < remap.length; i++) remap[i] = in.readInt();
                boolean empty = in.readBoolean(); var targetState = read(in); var inputState = read(in);
                try (var target = source(targetState); var input = source(inputState)) {
                    target.isEmpty = empty;
                    LongList[] identities = target.columnReferences();
                    for (int pass = 0; pass < 2; pass++) {
                        boolean changed = in.readBoolean(), expectedEmpty = in.readBoolean(); var expected = read(in);
                        var update = NativeDhSameDetailMerge.update(target.nativeSource(), input.nativeSource(), remap, 6);
                        if (update.metadataUpdated()) target.isEmpty = false;
                        assertEquals(changed, update.changed(), name + " changed " + pass);
                        assertEquals(expectedEmpty, target.isEmpty, name + " isEmpty " + pass);
                        for (int i = 0; i < 4096; i++) if (identities[i] != null) assertSame(identities[i], target.getColumn(i), name + " identity " + i);
                        check(expected, target, name + " pass " + pass);
                        // Exact saved Frozen state also exercises signed byte/null/overflow hashing.
                        int expectedHash = frozenHash(expected);
                        assertEquals(expectedHash, target.nativeSource().hash(6), name + " saved Frozen state hash");
                        assertEquals(1, update.hash() >>> 32, name + " fused hash");
                        assertEquals(expectedHash, (int) update.hash(), name + " fused publication matches saved Frozen hash");
                    }
                    check(inputState, input, name + " input remains unchanged");
                }
            }
            assertEquals(-1, in.read());
        }
    }
    @Test void ordinarySavedProducerFootprintsAreRustOwnedAndSurviveSourceClose() throws Exception {
        var setting = Config.Common.LodBuilding.worldCompression; var previous = setting.get();
        setting.setWithoutFiringEvents(EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
        LongList[] retained; LongArrayList retainedPoints;
        try {
            var columns = NativeDhColumnsTest.savedNativeColumns(1); assertNotNull(columns);
            int x = columns.offsetX(), z = columns.offsetZ(), i = x * 64 + z;
            try (var input = FullDataSourceV2.createNative(columns); var target = FullDataSourceV2.createEmpty(columns.position())) {
                input.setSemanticHorizontalUniformity(x, z, false);
                input.setSemanticHorizontalContributors(x, z, new LongList[]{input.getColumn(i), null, new LongArrayList(), input.getColumn(i)});
                assertTrue(input.nativeSource().hasFootprint(i));
                var identity = target.getColumn(i); int sourceHash = input.hashCode();
                assertTrue(target.updateFromDataSource(input)); assertSame(identity, target.getColumn(i));
                assertEquals(input.getColumn(i), target.getColumn(i)); assertEquals(sourceHash, input.hashCode());
                retained = target.getRetainedSemanticHorizontalContributors(x, z); assertNotNull(retained);
                assertNull(retained[1]); assertTrue(retained[2].isEmpty());
                assertEquals(Math.min(256, input.getColumn(i).size()), retained[0].size(), "footprints keep the saved bound");
                retainedPoints = new LongArrayList(retained[0]);
                assertThrows(UnsupportedOperationException.class, () -> target.getRetainedSemanticHorizontalContributors(x,z)[0].set(0, 9));
                assertFalse(target.updateFromDataSource(input), "Merge-only repeat preserves Frozen change detection");
                input.getColumn(i).clear(); assertEquals(retainedPoints, retained[0]);
            }
            assertEquals(retainedPoints, retained[0], "Footprints own independent points that outlive both sources");
        } finally { setting.setWithoutFiringEvents(previous); }
    }
    @Test void publicUpdateHashesFinalPointsAndKeepsMetadataOnlyCachedHashPolicy() throws Exception {
        var setting=Config.Common.LodBuilding.worldCompression;var previous=setting.get();
        try {
            for(var mode:EDhApiWorldCompressionMode.values()) {
                setting.setWithoutFiringEvents(mode);
                var columns=NativeDhColumnsTest.savedNativeColumns(1);assertNotNull(columns);
                try(var input=FullDataSourceV2.createNative(columns);var target=FullDataSourceV2.createEmpty(columns.position())) {
                    assertTrue(target.updateFromDataSource(input),mode.toString());
                    assertEquals(target.nativeSource().hash(target.getPos()),target.hashCode(),"Hash must describe the final, possibly culled points");
                    if(mode==EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS) {
                        int cached=target.hashCode();int index=columns.offsetX()*64+columns.offsetZ();
                        input.columnWorldCompressionMode.set(index,(byte)-128);
                        assertFalse(target.updateFromDataSource(input));
                        assertEquals(cached,target.hashCode(),"Metadata-only publication preserves Frozen's cached hash");
                        assertEquals((byte)-128,target.columnWorldCompressionMode.getByte(index));
                    }
                }
            }
        } finally {setting.setWithoutFiringEvents(previous);}
    }
    @Test void orderedFootprintAdmissionFollowsCellOrderAtCapacity() throws Exception {
        try (var input = FullDataSourceV2.createEmpty(6); var target = FullDataSourceV2.createEmpty(6)) {
            LongList[] footprint = {new LongArrayList(new long[]{0}), null, new LongArrayList(), new LongArrayList(new long[]{0})};
            for (int i = 0; i < 4096; i++) {
                if (i < 3) { input.getColumn(i).add(0); input.columnGenerationSteps.set(i, (byte)9); input.setSemanticHorizontalUniformity(i / 64, i % 64, i == 0); }
                // 1024 recipient footprints: the table is full.
                if (i == 0 || i >= 100 && i < 1123) target.setSemanticHorizontalContributors(i / 64, i % 64, footprint);
            }
            input.setSemanticHorizontalContributors(0, 1, footprint); input.setSemanticHorizontalContributors(0, 2, footprint);
            assertTrue(update(target, input, new int[]{7}));
            // Cell 0 turns uniform and frees a slot; cell 1 takes it; cell 2 finds the table full again.
            assertNull(target.getSemanticHorizontalContributors(0, 0));
            var admitted = target.getSemanticHorizontalContributors(0, 1);
            assertNotNull(admitted); assertEquals(LongArrayList.of(7), admitted[0], "admitted footprints are remapped");
            assertNull(admitted[1]); assertTrue(admitted[2].isEmpty());
            assertNull(target.getSemanticHorizontalContributors(0, 2));
            for (int i = 100; i < 1123; i++) assertNotNull(target.getSemanticHorizontalContributors(i / 64, i % 64), "untouched footprint " + i);
            for (int i = 0; i < 3; i++) assertEquals(LongArrayList.of(7), target.getColumn(i));
        }
    }
    @Test void corruptRemapsRejectTheWholeMergeAndMetadataSizeIsFixed() throws Exception {
        try (var input = FullDataSourceV2.createEmpty(6); var target = FullDataSourceV2.createEmpty(6)) {
            input.getColumn(0).add(0); input.columnGenerationSteps.set(0,(byte)9);
            input.getColumn(1).addElements(0,new long[]{1, 2}); input.columnGenerationSteps.set(1,(byte)9);
            var before = target.hashCode();
            assertThrows(IllegalStateException.class, () -> update(target,input,new int[]{7,8}), "point ID 2 is outside the remap");
            assertEquals(before, target.nativeSource().hash(6), "a rejected merge changes nothing");
            assertTrue(target.getColumn(0).isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> input.columnWorldCompressionMode.size(4095));
            assertThrows(UnsupportedOperationException.class, () -> input.columnGenerationSteps.add((byte) 0));
            assertEquals(4096, input.columnWorldCompressionMode.size());
        }
    }
    @Test void bindingAListFromAnotherSourceOrACustomListBindsANativeCopy() throws Exception {
        try (var input = FullDataSourceV2.createEmpty(6); var target = FullDataSourceV2.createEmpty(6)) {
            target.getColumn(0).add(42);
            input.setColumn(0, target.getColumn(0)); input.columnGenerationSteps.set(0,(byte)9);
            assertNotSame(target.getColumn(0), input.getColumn(0), "a bound list is never shared between cells");
            assertInstanceOf(NativeDhColumnList.class, input.getColumn(0));
            input.getColumn(0).set(0, 7);
            assertEquals(LongArrayList.of(42), target.getColumn(0));
            target.setColumn(1, target.getColumn(0));
            assertNotSame(target.getColumn(0), target.getColumn(1)); assertEquals(target.getColumn(0), target.getColumn(1));
            var custom = new LongArrayList(new long[]{3, 4});
            input.setColumn(2, custom); custom.set(0, 99);
            assertEquals(LongArrayList.of(3, 4), input.getColumn(2), "Java lists are copied when bound");
            input.columnGenerationSteps.set(2,(byte)9);
            assertTrue(update(target, input, java.util.stream.IntStream.range(0, 100).toArray()));
            assertEquals(LongArrayList.of(7), target.getColumn(0)); assertEquals(LongArrayList.of(3, 4), target.getColumn(2));
        }
    }
    @Test void mergeDetachesProducerRecipientsInPlace() throws Exception {
        var columns=NativeDhColumnsTest.savedNativeColumns(1);
        int index=columns.offsetX()*64+columns.offsetZ();
        try(var target=FullDataSourceV2.createNative(columns);var input=FullDataSourceV2.createEmpty(columns.position())) {
            var identity=(NativeDhColumnList)target.getColumn(index);
            assertTrue(identity.isOriginalProducerView());
            input.getColumn(index).add(0); input.columnGenerationSteps.set(index,(byte)9);
            input.columnWorldCompressionMode.set(index,(byte)-128);
            update(target, input, new int[]{17});
            assertSame(identity,target.getColumn(index)); assertFalse(identity.isOriginalProducerView());
            assertEquals(new LongArrayList(new long[]{17}),identity); assertEquals((byte)-128,target.columnWorldCompressionMode.getByte(index));
        }
    }
    @Test void repeatedUpdatesObserveSourceRecipientAndRemapChangesEvenWhenPointsHaveEqualHashes() throws Exception {
        try(var target=FullDataSourceV2.createEmpty(6);var input=FullDataSourceV2.createEmpty(6)) {
            var recipient=target.getColumn(0);var source=input.getColumn(0);
            recipient.addElements(0,new long[]{1,0});source.addElements(0,new long[]{0,31});
            target.columnGenerationSteps.set(0,(byte)9);input.columnGenerationSteps.set(0,(byte)9);
            int[] ids=java.util.stream.IntStream.range(0,32).toArray();
            assertEquals(recipient.hashCode(),source.hashCode());
            assertFalse(update(target,input,ids));assertEquals(LongArrayList.of(0,31),recipient);
            assertFalse(update(target,input,ids));assertSame(recipient,target.getColumn(0));
            source.set(0,1);source.set(1,0);
            assertFalse(update(target,input,ids));assertEquals(LongArrayList.of(1,0),recipient);
            recipient.set(0,0);recipient.set(1,31);
            assertFalse(update(target,input,ids));assertEquals(LongArrayList.of(1,0),recipient);
            ids[1]=-1;assertTrue(update(target,input,ids));assertEquals(LongArrayList.of(-1,0),recipient);
            assertFalse(update(target,input,ids));
            ids[1]=1;assertTrue(update(target,input,ids));assertFalse(update(target,input,ids));
            var replacement=NativeDhColumnList.empty();replacement.addElements(0,new long[]{1,0});input.setColumn(0,replacement);
            assertFalse(update(target,input,ids));assertEquals(LongArrayList.of(1,0),recipient);
            assertEquals(new LongArrayList(recipient).hashCode(),recipient.hashCode());
        }
    }
    @Test void emptySourceReadersAreLazyAndClosedSourcesStillHash() throws Exception {
        var source=FullDataSourceV2.createEmpty(6);
        try {
            var field=FullDataSourceV2.class.getDeclaredField("dataPoints");field.setAccessible(true);
            var projections=(LongList[])field.get(source);
            assertTrue(Arrays.stream(projections).allMatch(Objects::isNull));
            int hash=source.nativeSource().hash(6); assertEquals(hash,source.hashCode());
            assertTrue(Arrays.stream(projections).allMatch(Objects::isNull),"Native empty hashing must not construct Java readers");
            // Scalar metadata edits must still affect the authoritative empty-state hash.
            source.columnGenerationSteps.set(123,(byte)-128);source.columnWorldCompressionMode.set(456,(byte)127);
            assertNotEquals(hash,source.nativeSource().hash(6));assertTrue(Arrays.stream(projections).allMatch(Objects::isNull));
            var reader=source.getColumn(0);assertSame(reader,source.getColumn(0));
            source.setColumn(4095,null);assertNull(source.getColumn(4095));
            source.close();assertFalse(source.nativeSource().isLive());
            assertThrows(IllegalStateException.class,()->source.nativeSource().stageOwner());
            assertNull(source.getColumn(4095));assertTrue(source.getColumn(4094).isEmpty());
            reader.add(31);assertSame(reader,source.getColumn(0));assertEquals(31,source.getColumn(0).getLong(0));
            source.getColumn(4094).add(7);assertEquals(7,source.getColumn(4094).getLong(0));
            int closed=43*(17*(31*Long.hashCode(6)+Arrays.deepHashCode(source.columnReferences()))+source.columnGenerationSteps.hashCode())
                +source.columnWorldCompressionMode.hashCode();
            assertEquals(closed,source.nativeSource().hash(6),"a closed source hashes its retained values in Rust");
        } finally {if(source.nativeSource().isLive())source.close();}
    }
    @Test void lazyAndAdoptedSourceColumnsKeepGroupedLifetimeAcrossClose() throws Exception {
        var points = NativeDhColumnList.class.getDeclaredMethod("stagePoints"); points.setAccessible(true);
        var target = FullDataSourceV2.createEmpty(6);
        try (var input = FullDataSourceV2.createEmpty(6)) {
            var first = target.getColumn(0); // Existing empty identity must survive adoption.
            target.setColumn(1, null); // Missing and unprojected empty recipients both need adoption.
            for (int i = 0; i < 3; i++) {
                input.getColumn(i).add(0); input.columnGenerationSteps.set(i, (byte)9);
            }
            update(target, input, new int[]{0});
            assertSame(first, target.getColumn(0));
            var scope = ((java.lang.foreign.MemorySegment)points.invoke(first)).scope();
            for (int i = 1; i < 3; i++) {
                assertSame(scope, ((java.lang.foreign.MemorySegment)points.invoke(target.getColumn(i))).scope());
                assertEquals(LongArrayList.of(0), target.getColumn(i));
            }
            var lazy = target.getColumn(3); lazy.add(7);
            assertSame(scope, ((java.lang.foreign.MemorySegment)points.invoke(lazy)).scope());
            var retained = target.getColumn(2);
            target.close();
            assertFalse(target.nativeSource().isLive());
            first.set(0, 31); retained.add(8); lazy.clear();
            assertEquals(LongArrayList.of(31), first);
            assertEquals(LongArrayList.of(0, 8), retained);
            assertTrue(lazy.isEmpty());
            assertTrue(scope.isAlive(), "Explicit table close must not invalidate retained grouped readers");
        } finally { if (target.nativeSource().isLive()) target.close(); }
    }
    @Test void primaryPublicationAndOcclusionLeaveUnescapedReadersNativeUntilDemandOrClose() throws Exception {
        var field=FullDataSourceV2.class.getDeclaredField("dataPoints");field.setAccessible(true);
        var target=FullDataSourceV2.createEmpty(6);
        try(var input=FullDataSourceV2.createEmpty(6)) {
            target.mapping.addIfNotPresentAndGetId(NativeDhColumnsTest.occlusionBiome(0),com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper.AIR);
            var escaped=target.getColumn(0);
            target.setColumn(1,null);
            for(int i:new int[]{0,1,2,64,65,66,129}) {
                input.getColumn(i).addElements(0,new long[]{0,0});input.columnGenerationSteps.set(i,(byte)9);
            }
            assertTrue(update(target,input,new int[]{0}));
            var bindings=(LongList[])field.get(target);
            assertSame(escaped,bindings[0]);assertEquals(LongArrayList.of(0,0),escaped);
            for(int i:new int[]{1,2,64,65,66,129})assertNull(bindings[i],"Unescaped primary "+i);
            try(var policy=NativeDhMaterials.create(target.mapping)) {
                assertNotNull(policy);NativeDhOcclusionPass.cull(policy,target.nativeSource());
            }
            assertNull(bindings[65],"Native culling must not materialize its selected center");
            var retained=target.getColumn(1);assertEquals(LongArrayList.of(0,0),retained);
            assertSame(retained,target.getColumn(1));assertNull(bindings[2]);
            target.close();
            assertEquals(LongArrayList.of(0,0),target.getColumn(2),"Unescaped values survive explicit close");
            retained.add(7);assertEquals(LongArrayList.of(0,0,7),retained);
            assertSame(escaped,target.getColumn(0));
        } finally {if(target.nativeSource().isLive())target.close();}
    }
}
