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
            if (state[i].points == null) source.setColumn(i, null);
            else source.getColumn(i).addElements(0, state[i].points);
        }
        return source;
    }
    private static void forceOriginalRoute(FullDataSourceV2 source) {
        for (int i = 0; i < 4096; i++) if (source.getColumn(i) != null) {
            source.setColumn(i, new LongArrayList(source.getColumn(i))); return;
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
            if (expected[i].points == null) assertNull(source.getColumn(i), context + " null " + i);
            else {
                assertArrayEquals(expected[i].points, source.getColumn(i).toLongArray(), context + " points " + i);
                assertEquals(new LongArrayList(expected[i].points).hashCode(), source.getColumn(i).hashCode(), context + " hash " + i);
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
                try (var target = source(targetState); var direct = source(targetState); var scoped = source(targetState); var original = source(targetState); var input = source(inputState)) {
                    target.isEmpty = direct.isEmpty = scoped.isEmpty = original.isEmpty = empty; forceOriginalRoute(original);
                    LongList[] identities = target.columnReferences(), directIdentities = direct.columnReferences();
                    for (int pass = 0; pass < 2; pass++) {
                        boolean changed = in.readBoolean(), expectedEmpty = in.readBoolean(); var expected = read(in);
                        var nativeMerge = NativeDhSameDetailMerge.prepare(target.columnReferences(), input.columnReferences(), target.columnGenerationSteps,
                            target.columnWorldCompressionMode, input.columnGenerationSteps, input.columnWorldCompressionMode,
                            new boolean[4096], Map.of(), Map.of(), remap);
                        assertNotNull(nativeMerge, name + " must admit actual native ABI"); assertEquals(changed, nativeMerge.changed());
                        nativeMerge.commit(target.columnReferences(), target.nativeSource());
                        for (int i = 0; i < 4096; i++) if (nativeMerge.updated(i)) {
                            target.columnGenerationSteps.set(i, nativeMerge.generation(i)); target.columnWorldCompressionMode.set(i, nativeMerge.compression(i));
                            target.isEmpty = false;
                            if (identities[i] != null) assertSame(identities[i], target.getColumn(i));
                        }
                        var retainedMerge = NativeDhSameDetailMerge.prepareOwned(direct.nativeSource(),input.nativeSource(),Map.of(),Map.of(),remap);
                        assertNotNull(retainedMerge,name + " retained admission"); assertEquals(changed,retainedMerge.changed());
                        retainedMerge.commit(direct.columnReferences(),direct.nativeSource());
                        var publication=retainedMerge.publishMetadataWithHash(direct.nativeSource(),6);
                        if (publication.updated()) direct.isEmpty = false;
                        assertEquals(0,retainedMerge.footprintActionCount(),"No contributor facts in Frozen corpus");
                        for(int i=0;i<4096;i++) if(directIdentities[i]!=null) assertSame(directIdentities[i],direct.getColumn(i),name + " retained identity " + i);
                        assertEquals(expectedEmpty,direct.isEmpty); check(expected,direct,name + " retained pass " + pass);
                        // Exact saved Frozen state also exercises signed byte/null/overflow hashing.
                        int expectedHash = Long.hashCode(6); Object[] columns = new Object[4096];
                        var generations = new it.unimi.dsi.fastutil.bytes.ByteArrayList();
                        var compression = new it.unimi.dsi.fastutil.bytes.ByteArrayList();
                        for(int i=0;i<4096;i++) { columns[i]=expected[i].points==null?null:new LongArrayList(expected[i].points);
                            generations.add(expected[i].generation); compression.add(expected[i].compression); }
                        expectedHash=43*(17*(31*expectedHash+Arrays.deepHashCode(columns))+generations.hashCode())+compression.hashCode();
                        long retainedHash=direct.nativeSource().tryHash(6,direct.columnGenerationSteps,direct.columnWorldCompressionMode);
                        assertEquals(1,retainedHash>>>32,name + " retained hash admission"); assertEquals(expectedHash,(int)retainedHash,name + " saved Frozen state hash");
                        assertEquals(retainedHash,publication.hash(),name + " fused publication matches saved Frozen hash");
                        var scopedUpdate=NativeDhSameDetailMerge.updateOwnedWithoutFootprints(scoped.nativeSource(),input.nativeSource(),scoped.columnReferences(),remap,6);
                        assertNotNull(scopedUpdate,name + " scoped native admission");assertEquals(changed,scopedUpdate.changed());
                        if(scopedUpdate.metadataUpdated())scoped.isEmpty=false;
                        assertEquals(expectedEmpty,scoped.isEmpty);check(expected,scoped,name + " scoped pass " + pass);
                        assertEquals(retainedHash,scopedUpdate.hash(),name + " scoped saved Frozen hash");
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
                input.setSemanticHorizontalContributors(x, z, new LongList[]{input.getColumn(i), null, new LongArrayList(), input.getColumn(i)});
                var identity = target.getColumn(i); int sourceHash = input.hashCode();
                assertTrue(target.updateFromDataSource(input)); assertSame(identity, target.getColumn(i));
                assertEquals(input.getColumn(i), target.getColumn(i)); assertEquals(sourceHash, input.hashCode());
                retained = target.getRetainedSemanticHorizontalContributors(x, z); assertNotNull(retained);
                var owner = retained[0].getClass().getDeclaredField("owner"); owner.setAccessible(true);
                var batch = assertInstanceOf(NativeDhSameDetailMerge.class, owner.get(retained[0]), "Ordinary update must use complete native producer");
                var lease = NativeDhSameDetailMerge.class.getDeclaredField("sourceLease"); lease.setAccessible(true);
                assertNull(lease.get(batch), "Committed readers must not retain the borrowed source array");
                retainedPoints = new LongArrayList(retained[0]); assertThrows(UnsupportedOperationException.class, () -> target.getRetainedSemanticHorizontalContributors(x,z)[0].set(0, 9));
                assertFalse(target.updateFromDataSource(input), "Merge-only repeat preserves Frozen change detection");
                input.getColumn(i).clear(); assertEquals(retainedPoints, retained[0]);
            }
            assertEquals(retainedPoints, retained[0], "Committed footprints release borrowed source leases and own independent points");
        } finally { setting.setWithoutFiringEvents(previous); }
    }
    @Test void publicUpdateHashesFinalPointsAndKeepsMetadataOnlyCachedHashPolicy() throws Exception {
        var setting=Config.Common.LodBuilding.worldCompression;var previous=setting.get();
        try {
            for(var mode:EDhApiWorldCompressionMode.values()) {
                setting.setWithoutFiringEvents(mode);
                var columns=NativeDhColumnsTest.savedNativeColumns(1);assertNotNull(columns);
                try(var input=FullDataSourceV2.createNative(columns);
                    var target=FullDataSourceV2.createEmpty(columns.position());
                    var original=FullDataSourceV2.createEmpty(columns.position())) {
                    forceOriginalRoute(original);
                    assertEquals(original.updateFromDataSource(input),target.updateFromDataSource(input),mode.toString());
                    assertEquals(original.hashCode(),target.hashCode(),"Hash must describe the final, possibly culled points");
                    long computed=target.nativeSource().tryHash(target.getPos(),target.columnGenerationSteps,target.columnWorldCompressionMode);
                    assertEquals(1,computed>>>32);assertEquals((int)computed,target.hashCode());
                    for(int i=0;i<4096;i++)assertEquals(original.getColumn(i),target.getColumn(i));
                    if(mode==EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS) {
                        int cached=target.hashCode();int index=columns.offsetX()*64+columns.offsetZ();
                        input.columnWorldCompressionMode.set(index,(byte)-128);
                        assertFalse(original.updateFromDataSource(input));assertFalse(target.updateFromDataSource(input));
                        assertEquals(cached,target.hashCode(),"Metadata-only publication preserves Frozen's cached hash");
                        assertEquals(original.hashCode(),target.hashCode());
                        assertEquals((byte)-128,target.columnWorldCompressionMode.getByte(index));
                    }
                }
            }
        } finally {setting.setWithoutFiringEvents(previous);}
    }
    @Test void orderedFootprintAdmissionMatchesOriginalRouteAtCapacity() throws Exception {
        try (var input = FullDataSourceV2.createEmpty(6); var target = FullDataSourceV2.createEmpty(6); var original = FullDataSourceV2.createEmpty(6)) {
            LongList[] footprint = {new LongArrayList(new long[]{0}), null, new LongArrayList(), new LongArrayList(new long[]{0})};
            for (int i = 0; i < 4096; i++) {
                if (i < 3) { input.getColumn(i).add(0); input.columnGenerationSteps.set(i, (byte)9); input.setSemanticHorizontalUniformity(i / 64, i % 64, i == 0); }
                if (i == 0 || i >= 100 && i < 1123) {
                    target.setSemanticHorizontalContributors(i / 64, i % 64, footprint); original.setSemanticHorizontalContributors(i / 64, i % 64, footprint);
                }
            }
            input.setSemanticHorizontalContributors(0, 1, footprint); input.setSemanticHorizontalContributors(0, 2, footprint);
            forceOriginalRoute(original); assertEquals(update(original, input, new int[]{7}), update(target, input, new int[]{7}));
            for (int i = 0; i < 4096; i++) {
                assertEquals(original.getColumn(i), target.getColumn(i)); assertEquals(original.hasSemanticHorizontalUniformity(i/64,i%64), target.hasSemanticHorizontalUniformity(i/64,i%64));
                assertArrayEquals(original.getSemanticHorizontalContributors(i/64,i%64), target.getSemanticHorizontalContributors(i/64,i%64));
            }
            assertNotNull(target.getSemanticHorizontalContributors(0,1)); assertNull(target.getSemanticHorizontalContributors(0,2));
        }
    }
    @Test void invalidRemapAndResizedMetadataRetainOriginalSuccessfulPrefix() throws Exception {
        for (boolean shortMetadata : new boolean[]{false,true}) {
            try (var input = FullDataSourceV2.createEmpty(6); var target = FullDataSourceV2.createEmpty(6); var original = FullDataSourceV2.createEmpty(6)) {
                input.getColumn(0).add(0); input.columnGenerationSteps.set(0,(byte)9);
                int bad = shortMetadata ? 4095 : 1; input.columnGenerationSteps.set(bad,(byte)9);
                input.getColumn(bad).addElements(0,new long[]{1, shortMetadata ? 0 : 2});
                if (shortMetadata) input.columnWorldCompressionMode.size(4095);
                forceOriginalRoute(original);
                var expected = assertThrows(InvocationTargetException.class, () -> update(original,input,new int[]{7,8})).getCause();
                var actual = assertThrows(InvocationTargetException.class, () -> update(target,input,new int[]{7,8})).getCause();
                assertEquals(expected.getClass(),actual.getClass()); assertEquals(expected.getMessage(),actual.getMessage());
                assertEquals(original.columnGenerationSteps,target.columnGenerationSteps); assertEquals(original.columnWorldCompressionMode,target.columnWorldCompressionMode);
                for (int i = 0; i < 4096; i++) assertEquals(original.getColumn(i),target.getColumn(i),"Successful prefix column "+i);
            }
        }
    }
    @Test void listAliasesSharedMetadataAndCustomCallbacksDeclineBeforeNativePublication() throws Exception {
        try (var input = FullDataSourceV2.createEmpty(6); var target = FullDataSourceV2.createEmpty(6)) {
            target.getColumn(0).add(42); var old = new LongArrayList(target.getColumn(0));
            input.setColumn(0, target.getColumn(0)); input.columnGenerationSteps.set(0,(byte)9);
            assertNull(NativeDhSameDetailMerge.prepare(target.columnReferences(),input.columnReferences(),target.columnGenerationSteps,target.columnWorldCompressionMode,
                input.columnGenerationSteps,input.columnWorldCompressionMode,new boolean[4096],Map.of(),Map.of(),new int[]{0}));
            assertEquals(old,target.getColumn(0));
            int[] calls = {0};
            input.setColumn(0, new LongArrayList(new long[]{0}) {
                @Override public int size() { calls[0]++; throw new IllegalStateException("original custom size callback"); }
            });
            assertNull(NativeDhSameDetailMerge.prepare(target.columnReferences(),input.columnReferences(),target.columnGenerationSteps,target.columnWorldCompressionMode,
                input.columnGenerationSteps,input.columnWorldCompressionMode,new boolean[4096],Map.of(),Map.of(),new int[]{0}));
            assertEquals(0,calls[0]);
            var error = assertThrows(InvocationTargetException.class, () -> update(target,input,new int[]{0})).getCause();
            assertEquals("original custom size callback",error.getMessage()); assertEquals(1,calls[0]); assertEquals(old,target.getColumn(0));
            input.setColumn(0, NativeDhColumnList.empty());
            assertNull(NativeDhSameDetailMerge.prepare(target.columnReferences(),input.columnReferences(),target.columnGenerationSteps,target.columnWorldCompressionMode,
                target.columnGenerationSteps,input.columnWorldCompressionMode,new boolean[4096],Map.of(),Map.of(),new int[]{0}));
        }
    }
    @Test void retainedPublicationDetachesProducerRecipientsAndRejectsStaleStructuralProofs() throws Exception {
        var columns=NativeDhColumnsTest.savedNativeColumns(1);
        int index=columns.offsetX()*64+columns.offsetZ();
        try(var target=FullDataSourceV2.createNative(columns);var input=FullDataSourceV2.createEmpty(columns.position())) {
            var identity=(NativeDhColumnList)target.getColumn(index);
            assertTrue(identity.isOriginalProducerView());
            input.getColumn(index).add(0); input.columnGenerationSteps.set(index,(byte)9);
            input.columnWorldCompressionMode.set(index,(byte)-128);
            var merge=NativeDhSameDetailMerge.prepareSources(target.nativeSource(),input.nativeSource(),target.columnGenerationSteps,
                target.columnWorldCompressionMode,input.columnGenerationSteps,input.columnWorldCompressionMode,new boolean[4096],Map.of(),Map.of(),new int[]{17});
            assertNotNull(merge); merge.commit(target.columnReferences(),target.nativeSource());
            assertTrue(merge.publishMetadata(target.columnGenerationSteps,target.columnWorldCompressionMode,new boolean[4096]));
            assertSame(identity,target.getColumn(index)); assertFalse(identity.isOriginalProducerView());
            assertEquals(new LongArrayList(new long[]{17}),identity); assertEquals((byte)-128,target.columnWorldCompressionMode.getByte(index));
            var stale=NativeDhSameDetailMerge.prepareSources(target.nativeSource(),input.nativeSource(),target.columnGenerationSteps,
                target.columnWorldCompressionMode,input.columnGenerationSteps,input.columnWorldCompressionMode,new boolean[4096],Map.of(),Map.of(),new int[]{18});
            assertNotNull(stale); target.setColumn(4095,NativeDhColumnList.empty());
            assertThrows(IllegalStateException.class,()->stale.commit(target.columnReferences(),target.nativeSource()));
            assertEquals(new LongArrayList(new long[]{17}),identity,"Structural changes reject before point publication");
        }
    }
    @Test void retainedIdentityProofDetectsEmptyAliasesAfterAnEarlierSuccessfulAdmission() {
        try(var target=FullDataSourceV2.createEmpty(6);var input=FullDataSourceV2.createEmpty(6)) {
            assertNotNull(NativeDhSameDetailMerge.prepareSources(target.nativeSource(),input.nativeSource(),target.columnGenerationSteps,
                target.columnWorldCompressionMode,input.columnGenerationSteps,input.columnWorldCompressionMode,new boolean[4096],Map.of(),Map.of(),new int[]{0}));
            target.setColumn(1,target.getColumn(0));
            assertNull(NativeDhSameDetailMerge.prepareSources(target.nativeSource(),input.nativeSource(),target.columnGenerationSteps,
                target.columnWorldCompressionMode,input.columnGenerationSteps,input.columnWorldCompressionMode,new boolean[4096],Map.of(),Map.of(),new int[]{0}));
            target.setColumn(1,NativeDhColumnList.empty()); input.setColumn(2,target.getColumn(0));
            assertNull(NativeDhSameDetailMerge.prepareSources(target.nativeSource(),input.nativeSource(),target.columnGenerationSteps,
                target.columnWorldCompressionMode,input.columnGenerationSteps,input.columnWorldCompressionMode,new boolean[4096],Map.of(),Map.of(),new int[]{0}));
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

    @Test void emptySourceReadersAreLazyAndClosedReadersPreservePresenceAndPointLifetime() throws Exception {
        var source=FullDataSourceV2.createEmpty(6);
        try {
            var field=FullDataSourceV2.class.getDeclaredField("dataPoints");field.setAccessible(true);
            var projections=(LongList[])field.get(source);
            assertTrue(Arrays.stream(projections).allMatch(Objects::isNull));
            long hash=source.nativeSource().tryHash(6,source.columnGenerationSteps,source.columnWorldCompressionMode);
            assertEquals(1,hash>>>32); assertEquals((int)hash,source.hashCode());
            assertTrue(Arrays.stream(projections).allMatch(Objects::isNull),"Native empty hashing must not construct Java readers");
            // Scalar metadata edits must still affect the authoritative empty-state hash.
            source.columnGenerationSteps.set(123,(byte)-128);source.columnWorldCompressionMode.set(456,(byte)127);
            long edited=source.nativeSource().tryHash(6,source.columnGenerationSteps,source.columnWorldCompressionMode);
            assertNotEquals((int)hash,(int)edited);assertTrue(Arrays.stream(projections).allMatch(Objects::isNull));
            var uniform=FullDataSourceV2.class.getDeclaredMethod("semanticallyUniformTwoByTwo",FullDataSourceV2.class,int.class,int.class,int[].class);
            uniform.setAccessible(true);
            for(int index:new int[]{0,1,64,65})source.columnWorldCompressionMode.set(index,EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS.value);
            assertEquals(true,uniform.invoke(null,source,0,0,new int[]{0}),"Lazy present-empty readers preserve uniformity");
            source.setColumn(1,null);assertEquals(false,uniform.invoke(null,source,0,0,new int[]{0}),"Explicit null differs from a lazy empty reader");
            var reader=source.getColumn(0);assertSame(reader,source.getColumn(0));
            source.setColumn(4095,null);assertNull(source.getColumn(4095));
            source.close();assertFalse(source.nativeSource().isLive());
            assertThrows(IllegalStateException.class,()->source.nativeSource().stageOwner());
            assertNull(source.getColumn(4095));assertTrue(source.getColumn(4094).isEmpty());
            reader.add(31);assertSame(reader,source.getColumn(0));assertEquals(31,source.getColumn(0).getLong(0));
            source.getColumn(4094).add(7);assertEquals(7,source.getColumn(4094).getLong(0));
            assertEquals(0,source.nativeSource().tryHash(6,source.columnGenerationSteps,source.columnWorldCompressionMode));
        } finally {if(source.nativeSource().isLive())source.close();}
    }

    @Test void lazyAndAdoptedSourceColumnsKeepGroupedLifetimeAcrossClose() throws Exception {
        var points = NativeDhColumnList.class.getDeclaredMethod("stagePoints"); points.setAccessible(true);
        var bindings = FullDataSourceV2.class.getDeclaredField("dataPoints"); bindings.setAccessible(true);
        var target = FullDataSourceV2.createEmpty(6);
        try (var input = FullDataSourceV2.createEmpty(6)) {
            var first = target.getColumn(0); // Existing empty identity must survive adoption.
            target.setColumn(1, null); // Missing and unprojected empty recipients both need adoption.
            for (int i = 0; i < 3; i++) {
                input.getColumn(i).add(0); input.columnGenerationSteps.set(i, (byte)9);
            }
            var merge = NativeDhSameDetailMerge.prepareSources(target.nativeSource(), input.nativeSource(),
                target.columnGenerationSteps, target.columnWorldCompressionMode,
                input.columnGenerationSteps, input.columnWorldCompressionMode,
                new boolean[4096], Map.of(), Map.of(), new int[]{0});
            assertNotNull(merge);
            try {
                merge.commit((LongList[])bindings.get(target), target.nativeSource());
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
            } finally { merge.close(); }
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
                assertNotNull(policy);assertTrue(NativeDhOcclusionPass.tryCull(policy,target.nativeSource()));
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
