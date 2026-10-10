package net.minecraft.world.level.levelgen;

import com.seibel.distanthorizons.common.wrappers.WrapperFactory;
import com.seibel.distanthorizons.common.wrappers.block.*;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.transformers.LodDataBuilder;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import it.unimi.dsi.fastutil.longs.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.ticks.ProtoChunkTicks;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Supplemental real-registry CPU fixtures; not gameplay or saved-biome parity. */
@Tag("parity")
class NativeDhColumnsTest {
    private static ILevelWrapper level;
    private static Registry<Biome> biomes;
    private static PalettedContainerFactory factory;
    private static List<Holder<Biome>> pattern;
    private static boolean fixtureReady;
    private static Object allocate(Class<?> target) throws Exception {
        Class<?> type=Class.forName("sun.misc.Unsafe");var field=type.getDeclaredField("theUnsafe");field.setAccessible(true);
        return type.getMethod("allocateInstance",Class.class).invoke(field.get(null),target);
    }
    @BeforeAll static synchronized void bootstrap() throws Exception {
        if (fixtureReady) return;
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        SingletonInjector.INSTANCE.bind(IWrapperFactory.class,WrapperFactory.INSTANCE);
        var shared=(IMinecraftSharedWrapper)Proxy.newProxyInstance(IMinecraftSharedWrapper.class.getClassLoader(),new Class<?>[]{IMinecraftSharedWrapper.class},
            (p,m,a)->switch(m.getName()) {
                case "getInstallationDirectory" -> Path.of("run").toFile();
                case "isDedicatedServer" -> false;
                case "getDelayedSetupComplete" -> true;
                default -> throw new UnsupportedOperationException("Unexpected fixture environment callback "+m);
            });
        SingletonInjector.INSTANCE.bind(IMinecraftSharedWrapper.class,shared);
        try(var resources=new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
                List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()))) {
            var layers=net.minecraft.server.RegistryLayer.createRegistryAccess();
            var tags=net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources,layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
            var lookups=net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN),tags);
            var worldgen=net.minecraft.resources.RegistryDataLoader.load(resources,lookups,net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
            var access=layers.replaceFrom(net.minecraft.server.RegistryLayer.WORLDGEN,worldgen).compositeAccess();
            biomes=access.lookupOrThrow(Registries.BIOME);factory=PalettedContainerFactory.create(access);
            pattern=List.of(biomes.getOrThrow(Biomes.PLAINS),biomes.getOrThrow(Biomes.OCEAN),biomes.getOrThrow(Biomes.DESERT),biomes.getOrThrow(Biomes.SAVANNA));
            // Real stock class, registry-only CPU fixture. No constructor/world
            // simulation is emulated, and no runtime acceptance follows from it.
            var world=(ServerLevel)allocate(ServerLevel.class);
            var registry=Level.class.getDeclaredField("registryAccess");registry.setAccessible(true);registry.set(world,access);
            level=(ServerLevelWrapper)allocate(ServerLevelWrapper.class);
            var wrapped=ServerLevelWrapper.class.getDeclaredField("level");wrapped.setAccessible(true);wrapped.set(level,world);
        }
        fixtureReady = true;
    }
    @Test void fixtureBootstrapCanRepeatWithoutRebindingOrReplacingTheWorld() throws Exception {
        Object before = registryOnlyFixtureWorld(); bootstrap(); bootstrap();
        assertSame(before, registryOnlyFixtureWorld());
        assertSame(WrapperFactory.INSTANCE, SingletonInjector.INSTANCE.get(IWrapperFactory.class));
    }
    private static ProtoChunk chunk(int index,int biomePattern) {
        var record=HeightmapFixtures.saved().get(index);var source=HeightmapFixtures.recorded(record);
        var sections=new LevelChunkSection[source.getSections().length];
        for(int s=0;s<sections.length;s++) {
            var data=factory.createForBiomes();
            for(int y=0;y<4;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++)
                data.getAndSetUnchecked(x,y,z,pattern.get(biomePattern==0?0:(x+3*z+y+s)%4));
            sections[s]=new LevelChunkSection(source.getSections()[s].getStates(),data);
        }
        var result=new ProtoChunk(new ChunkPos(-37+index,41-index),UpgradeData.EMPTY,sections,new ProtoChunkTicks<>(),new ProtoChunkTicks<>(),
            LevelHeightAccessor.create(source.getMinY(),source.getHeight()),factory,null);
        result.setPersistedStatus(ChunkStatus.BIOMES);return result;
    }
    private static ChunkWrapper light(ProtoChunk chunk) {
        var wrapper=new ChunkWrapper(chunk,level);wrapper.getWorldBlockLightPosList();
        var nearby=new ArrayList<com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper>();nearby.add(wrapper);
        com.seibel.distanthorizons.core.generation.DhLightingEngine.INSTANCE.lightChunk(wrapper,nearby,15);
        assertTrue(wrapper.isDhBlockLightingCorrect());
        assertNotNull(wrapper.nativeDhLightingInput().previous());return wrapper;
    }
    /** Shared real saved-chunk producer for the separately scoped reduction fixture. */
    static NativeDhColumns savedNativeColumns(int test) {
        return NativeDhColumns.build(level, light(chunk(test / 2, test % 2)), 1);
    }
    static Object registryOnlyFixtureWorld() { return level.getWrappedMcObject(); }
    private static long retainedInputAddress(ChunkAccess chunk) throws Exception {
        var getter = ChunkAccess.class.getDeclaredMethod("nativeDhColumnSections"); getter.setAccessible(true);
        Object inputs = getter.invoke(chunk); assertNotNull(inputs);
        var owner = inputs.getClass().getDeclaredMethod("stageOwner"); owner.setAccessible(true);
        return ((java.lang.foreign.MemorySegment)owner.invoke(inputs)).address();
    }
    @Test void ordinaryColumnsReuseChunkBindingsAndReadCurrentBiomeGenerations() throws Exception {
        var chunk = chunk(0, 0); var wrapper = light(chunk);
        var original = NativeDhColumns.build(level, wrapper, 1); assertNotNull(original);
        long address = retainedInputAddress(chunk);
        var repeated = NativeDhColumns.build(level, wrapper, 1); assertNotNull(repeated);
        assertEquals(address, retainedInputAddress(chunk));
        assertEquals(original.sourceHash(), repeated.sourceHash());
        for (var section : chunk.getSections()) {
            @SuppressWarnings("unchecked") var container = (PalettedContainer<Holder<Biome>>)section.getBiomes();
            for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++)
                container.getAndSet(x, y, z, pattern.get(1));
        }
        var changed = NativeDhColumns.build(level, wrapper, 1); assertNotNull(changed);
        assertEquals(address, retainedInputAddress(chunk), "Biome generation growth stays attached to the retained mutable owner");
        for (int id = 0; id < changed.materialCount(); id++) assertEquals(BiomeWrapper.getBiomeWrapper(pattern.get(1), level), changed.material(id).biome);
        for (int id = 0; id < original.materialCount(); id++) assertEquals(BiomeWrapper.getBiomeWrapper(pattern.get(0), level), original.material(id).biome);
    }
    @Test void productionHandoffAllFrozenPointsMaterialsMetadataHashesAndRetainedLifetimes() throws Exception {
        try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/world/chunk/frozen-dh-columns.bin.gz"))))) {
            assertEquals(0x44484331,in.readInt());assertEquals(11,in.readInt());
            for(int i=0;i<11;i++){in.readUTF();assertEquals(64,in.readUTF().length());}
            assertEquals("ee865336a6a8cc6d801dada6a0b54da98564a6ed6d216df3cefb4666f7fc187b",in.readUTF());assertTrue(in.readUTF().contains("Supplemental CPU fixture"));
            for(int n=in.readInt();n>0;n--){in.readUTF();in.readInt();}
            for(int n=in.readInt();n>0;n--){in.readUTF();in.readInt();}
            assertEquals(32,in.readInt());
            for(int test=0;test<32;test++) {
                String name=in.readUTF();var chunk=chunk(test/2,test%2);var wrapper=light(chunk);
                assertEquals(chunk.getPos().x,in.readInt());assertEquals(chunk.getPos().z,in.readInt());
                assertEquals(chunk.getMinY(),in.readInt());assertEquals(chunk.getHeight(),in.readInt());
                assertEquals(wrapper.getMinNonEmptyHeight(),in.readInt());assertEquals(wrapper.getMaxNonEmptyHeight(),in.readInt());
                for(var section:chunk.getSections())for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)assertEquals(in.readInt(),Block.getId(section.getBlockState(x,y,z)));
                for(var section:chunk.getSections())for(int y=0;y<4;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++)assertEquals(in.readInt(),biomes.getId(section.getNoiseBiome(x,y,z).value()));
                for(int y=chunk.getMinY();y<=chunk.getMaxY();y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
                    int packed=in.readUnsignedByte();assertEquals(packed&15,wrapper.getDhBlockLight(x,y,z));assertEquals(packed>>4,wrapper.getDhSkyLight(x,y,z));
                }
                for(int x=0;x<16;x++)for(int z=0;z<16;z++){assertEquals(in.readInt(),wrapper.getSolidHeightMapValue(x,z));assertEquals(in.readInt(),wrapper.getLightBlockingHeightMapValue(x,z));}
                LongList retained; LongArrayList before;
                try(var output=LodDataBuilder.createFromChunk(level,wrapper)) {
                    assertNotNull(output);assertEquals(in.readLong(),output.getPos());assertEquals(in.readBoolean(),output.isEmpty);assertEquals(in.readBoolean(),output.applyToParent);
                    assertEquals(in.readInt(),output.hashCode(),name);assertEquals(in.readInt(),output.mapping.hashCode(),name);
                    assertEquals(in.readInt(),output.mapping.size());
                    for(int id=0;id<output.mapping.size();id++) {
                        var biome=(BiomeWrapper)output.mapping.getBiomeWrapper(id);var state=(BlockStateWrapper)output.mapping.getBlockStateWrapper(id);
                        assertEquals(in.readInt(),biomes.getId(biome.biome.value()));assertEquals(in.readInt(),state.blockState==null?0:Block.getId(state.blockState));
                        assertEquals(in.readUTF(),biome.getSerialString());assertEquals(in.readUTF(),state.getSerialString());
                    }
                    int nativeColumns=0;
                    for(int x=0;x<64;x++)for(int z=0;z<64;z++) {
                        int index=FullDataSourceV2.relativePosToIndex(x,z);var list=output.dataPoints[index];
                        assertEquals(in.readUnsignedByte(),Byte.toUnsignedInt(output.columnGenerationSteps.getByte(index)));
                        assertEquals(in.readUnsignedByte(),Byte.toUnsignedInt(output.columnWorldCompressionMode.getByte(index)));
                        assertEquals(in.readInt(),list.size());for(int p=0;p<list.size();p++)assertEquals(in.readLong(),list.getLong(p),name+" column "+x+","+z);
                        if(list instanceof NativeDhColumnList nativeList && nativeList.isOriginalProducerView())nativeColumns++;
                    }
                    assertEquals(256,nativeColumns,"Actual whole-producer admission, no Java point arrays");
                    retained=output.getColumnAtRelPos((chunk.getPos().x&3)*16,(chunk.getPos().z&3)*16);
                    before=new LongArrayList(retained);
                }
                assertEquals(before,retained,"CPU owner survives pooled-source close");
            }
            assertEquals(-1,in.read());
        }
    }
    private static com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper occlusionBiome(int id) {
        var type = com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper.class;
        return (com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> switch (method.getName()) {
                case "getSerialString", "getName", "toString" -> "occlusion-fixture-" + id;
                case "hashCode" -> id;
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.toString());
            });
    }
    @Test void actualNativeOcclusionMatchesBothFrozenPassesAndRetainsClosedSourceViews() throws Exception {
        int admitted = 0, removed = 0;
        try (var in = new DataInputStream(new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/world/chunk/frozen-dh-occlusion.bin.gz"))))) {
            assertEquals(0x44484f31, in.readInt()); assertEquals(6, in.readInt());
            for (int n = 0; n < 6; n++) { in.readUTF(); assertEquals(64, in.readUTF().length()); }
            String inputHash = in.readUTF();
            try (var input = new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/world/chunk/frozen-dh-columns.bin.gz")))) {
                assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())), inputHash);
            }
            assertTrue(in.readUTF().contains("Supplemental CPU evidence")); assertEquals(32, in.readInt());
            for (int test = 0; test < 32; test++) {
                String name = in.readUTF(); var mapping = new FullDataPointIdMap(6);
                for (int id = 0, n = in.readInt(); id < n; id++) {
                    int state = in.readInt(), opacity = in.readUnsignedByte();
                    var wrapper = state == 0 ? BlockStateWrapper.AIR : BlockStateWrapper.fromBlockState(Block.stateById(state), level);
                    assertEquals(opacity, wrapper.getOpacity()); assertEquals(id, mapping.addIfNotPresentAndGetId(occlusionBiome(id), wrapper));
                }
                LongList retained; LongArrayList before;
                try (var source = FullDataSourceV2.createEmpty(6); var batch = FullDataSourceV2.createEmpty(6)) {
                    source.mapping.addAll(mapping);
                    for (int i = 0; i < source.dataPoints.length; i++) {
                        var column = source.dataPoints[i];
                        assertInstanceOf(NativeDhColumnList.class, column);
                        long[] points = new long[in.readInt()]; for (int p = 0; p < points.length; p++) points[p] = in.readLong();
                        column.addElements(0, points); batch.dataPoints[i].addElements(0, points);
                    }
                    try (var policy = NativeDhMaterials.create(source.mapping)) {
                        assertNotNull(policy, name + " canonical material admission");
                        for (int pass = 0; pass < 2; pass++) {
                            assertTrue(NativeDhOcclusionPass.tryCull(policy, batch.dataPoints), name + " actual complete native pass");
                            for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) {
                                var center = (NativeDhColumnList) source.getColumnAtRelPos(x, z);
                                if (center.size() <= 1) continue;
                                var a = source.tryGetColumnAtRelPos(x + 1, z); var b = source.tryGetColumnAtRelPos(x - 1, z);
                                var c = source.tryGetColumnAtRelPos(x, z + 1); var d = source.tryGetColumnAtRelPos(x, z - 1);
                                if (a == null || b == null || c == null || d == null || a.isEmpty() || b.isEmpty() || c.isEmpty() || d.isEmpty()) continue;
                                int previous = center.size();
                                assertTrue(center.tryCull(policy, a, b, c, d), name + " actual native consumer");
                                admitted++; removed += previous - center.size();
                            }
                            for (int i = 0; i < 4096; i++) {
                                var column = source.dataPoints[i]; assertEquals(in.readInt(), column.size(), name + " pass " + pass + " column " + i);
                                for (int p = 0; p < column.size(); p++) assertEquals(in.readLong(), column.getLong(p), name + " pass " + pass + " point " + p);
                                assertEquals(new LongArrayList(column).hashCode(), column.hashCode());
                                assertEquals(column, batch.dataPoints[i], name + " whole pass matches Frozen column " + i);
                                assertEquals(column.hashCode(), batch.dataPoints[i].hashCode());
                            }
                        }
                    }
                    retained = Arrays.stream(source.dataPoints).filter(c -> !c.isEmpty()).findFirst().orElseThrow(); before = new LongArrayList(retained);
                }
                assertEquals(before, retained, "Mutable native lease survives source close");
            }
            assertEquals(-1, in.read());
        }
        assertTrue(admitted > 1000, "The saved corpus must exercise actual native consumers");
        assertTrue(removed > 0, "The corpus must include real occlusion mutations");
    }
    @Test void completeOcclusionDeclinesAliasesCustomCallbacksAndBadIdsWithoutChangingPoints() {
        try (var source = FullDataSourceV2.createEmpty(6)) {
            source.mapping.addIfNotPresentAndGetId(occlusionBiome(0), BlockStateWrapper.AIR);
            try (var policy = NativeDhMaterials.create(source.mapping)) {
                assertNotNull(policy);
                LongList original = source.dataPoints[65];
                original.add(3L << 32); original.add(1L << 32);
                int oldHash = original.hashCode(); var old = new LongArrayList(original);
                LongList next = source.dataPoints[66]; source.dataPoints[66] = original;
                assertFalse(NativeDhOcclusionPass.tryCull(policy, source.dataPoints));
                assertEquals(old, original); assertEquals(oldHash, original.hashCode());
                source.dataPoints[66] = new LongArrayList() {
                    @Override public boolean isEmpty() { throw new AssertionError("Custom callback must remain in original route"); }
                };
                assertFalse(NativeDhOcclusionPass.tryCull(policy, source.dataPoints));
                assertEquals(old, original);
                source.dataPoints[66] = new LongArrayList(new long[]{1L << 32});
                assertFalse(NativeDhOcclusionPass.tryCull(policy, source.dataPoints));
                source.dataPoints[66] = next;
                original.set(0, (3L << 32) | 0xffff_ffffL); old = new LongArrayList(original);
                assertFalse(NativeDhOcclusionPass.tryCull(policy, source.dataPoints));
                assertEquals(old, original);
                original.set(0, 3L << 32);
                source.dataPoints[66] = new LongArrayList(); source.dataPoints[67] = null;
                assertTrue(NativeDhOcclusionPass.tryCull(policy, source.dataPoints));
                assertEquals(oldHash, original.hashCode());
            }
        }
    }
    @Test void mutableNativeSelfCopiesRemappingFailurePrefixesAndListAliasesMatchCompatibility() {
        var column = NativeDhColumnList.empty(); column.addElements(0, new long[]{0, 1, 2});
        var alias = column; column.addAll(1, column); assertEquals(LongArrayList.of(0, 0, 1, 2, 1, 2), alias);
        column.removeElements(1, 4); assertEquals(LongArrayList.of(0, 1, 2), alias);
        var originalFailure = assertThrows(RuntimeException.class, () -> com.seibel.distanthorizons.core.util.FullDataPointUtil.remap(new int[]{9, 8}, 2));
        var nativeFailure = assertThrows(RuntimeException.class, () -> column.remapIds(new int[]{9, 8}));
        assertEquals(originalFailure.getClass(), nativeFailure.getClass());
        assertEquals(originalFailure.getCause().getClass(), nativeFailure.getCause().getClass());
        assertEquals(originalFailure.getMessage(), nativeFailure.getMessage());
        assertEquals(LongArrayList.of(9, 8, 2), alias, "Original successful mutation prefix survives later ID failure");
        assertEquals(alias.hashCode(), new LongArrayList(alias).hashCode());
        column.clear(); column.add(77); assertEquals(LongArrayList.of(77), alias);
    }
    @Test void nativeProducerFeedsMutableRecipientUpdateAndPublicCullerWithoutChangingSource() throws Exception {
        var compression = com.seibel.distanthorizons.core.config.Config.Common.LodBuilding.worldCompression;
        var previous = compression.get();
        compression.setWithoutFiringEvents(com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode.VISUALLY_EQUAL);
        try (var in = new DataInputStream(new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/world/chunk/frozen-dh-occlusion.bin.gz"))))) {
            assertEquals(0x44484f31, in.readInt());
            for (int n = in.readInt(); n > 0; n--) { in.readUTF(); in.readUTF(); }
            in.readUTF(); in.readUTF(); assertEquals(32, in.readInt());
            for (int test = 0; test < 32; test++) {
                String name = in.readUTF();
                var columns = NativeDhColumns.build(level, light(chunk(test / 2, test % 2)), 1);
                assertNotNull(columns, name);
                try (var source = FullDataSourceV2.createNative(columns);
                     var recipient = FullDataSourceV2.createEmpty(source.getPos())) {
                    assertEquals(source.mapping.size(), in.readInt());
                    for (int id = 0; id < source.mapping.size(); id++) {
                        int expectedState = in.readInt(), expectedOpacity = in.readUnsignedByte();
                        var wrapper = (BlockStateWrapper) source.mapping.getBlockStateWrapper(id);
                        assertEquals(expectedState, wrapper.blockState == null ? 0 : Block.getId(wrapper.blockState));
                        assertEquals(expectedOpacity, wrapper.getOpacity());
                    }
                    for (var column : source.dataPoints) {
                        assertEquals(in.readInt(), column.size(), name + " producer");
                        for (int p = 0; p < column.size(); p++) assertEquals(in.readLong(), column.getLong(p));
                    }
                    int sourceHash = source.hashCode();
                    int index = FullDataSourceV2.relativePosToIndex(columns.offsetX(), columns.offsetZ());
                    LongList alias = recipient.dataPoints[index];
                    assertTrue(recipient.updateFromDataSource(source), name + " actual recipient update");
                    assertSame(alias, recipient.dataPoints[index], "Update preserves existing list identity");
                    for (int pass = 0; pass < 2; pass++) {
                        if (pass != 0) try (var policy = NativeDhMaterials.create(recipient.mapping)) {
                            assertNotNull(policy);
                            for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) if (recipient.getColumnAtRelPos(x, z).size() > 1)
                                com.seibel.distanthorizons.core.dataObjects.transformers.FullDataOcclusionCuller.cullHiddenDatapointsInColumn(recipient, x, z, policy);
                        }
                        for (var column : recipient.dataPoints) {
                            assertInstanceOf(NativeDhColumnList.class, column, "Recipient keeps authoritative Rust point storage");
                            assertFalse(((NativeDhColumnList) column).isOriginalProducerView());
                            assertEquals(in.readInt(), column.size(), name + " recipient pass " + pass);
                            for (int p = 0; p < column.size(); p++) assertEquals(in.readLong(), column.getLong(p));
                        }
                    }
                    assertEquals(sourceHash, source.hashCode(), "Recipient mutation never alters producer snapshot");
                    assertTrue(columns.ownsOriginalColumn(source.dataPoints[index], 0, 0));
                }
            }
            assertEquals(-1, in.read());
        } finally { compression.setWithoutFiringEvents(previous); }
    }
    @Test void listReadsSymmetricEqualityIteratorsSublistsBulkCopiesAndMutationsAreCompatible() {
        var columns=NativeDhColumns.build(level,light(chunk(0,1)),1);assertNotNull(columns);
        LongList original=columns.column(0,0);var expected=new LongArrayList(original);var pristine=new LongArrayList(original);assertEquals(expected,original);assertEquals(original,expected);
        assertEquals(expected.hashCode(),original.hashCode());assertEquals(0,expected.compareTo(original));assertEquals(0,original.compareTo(expected));
        long[] copied=new long[original.size()+4];original.getElements(0,copied,2,original.size());assertArrayEquals(expected.toLongArray(),Arrays.copyOfRange(copied,2,copied.length-2));
        assertArrayEquals(expected.toLongArray(),original.longStream().toArray());assertEquals(expected.subList(0,3),original.subList(0,3));
        var iterator=original.listIterator();assertEquals(iterator.nextLong(),expected.getLong(0));iterator.set(123);expected.set(0,123);
        iterator.add(456);expected.add(1,456);assertEquals(expected,original);iterator.previousLong();iterator.remove();expected.removeLong(1);assertEquals(expected,original);
        original.subList(0,2).clear();expected.subList(0,2).clear();assertEquals(expected,original);
        original.addElements(0,new long[]{4,5,6});expected.addElements(0,new long[]{4,5,6});assertEquals(expected,original);
        original.setElements(1,new long[]{7,8});expected.setElements(1,new long[]{7,8});assertEquals(expected,original);
        original.sort(null);expected.sort(null);assertEquals(expected,original);original.clear();assertTrue(original.isEmpty());
        assertEquals(pristine,columns.column(0,0),"Other views remain immutable");
    }
    @Test void mapMutationsTransfersAndClearPreserveNativeInputAndOriginalIds() {
        var columns=NativeDhColumns.build(level,light(chunk(0,1)),1);assertNotNull(columns);
        var input=FullDataPointIdMap.fromNative(columns);var copied=new FullDataPointIdMap(columns.position());copied.addAll(input);
        assertEquals(input.size(),copied.size());assertEquals(input.hashCode(),copied.hashCode());
        int[] ids=new FullDataPointIdMap(0).mergeAndReturnRemappedEntityIds(input);for(int i=0;i<ids.length;i++)assertEquals(i,ids[i]);
        int hash=input.hashCode();assertEquals(0,input.addIfNotPresentAndGetId(input.getBiomeWrapper(0),input.getBlockStateWrapper(0)));assertEquals(hash,input.hashCode());
        input.clear(123);assertTrue(input.isEmpty());assertEquals(-1,input.getMaxValidId());assertEquals(columns.materialCount(),copied.size());
    }
    @Test void publicColumnAndMetadataMutationsBeforeFirstHashKeepOriginalCacheSemantics() {
        var columns=NativeDhColumns.build(level,light(chunk(0,1)),1);assertNotNull(columns);
        for(int mutation=0;mutation<4;mutation++)try(var output=FullDataSourceV2.createNative(columns)) {
            int index=FullDataSourceV2.relativePosToIndex(columns.offsetX(),columns.offsetZ());
            switch(mutation) {
                case 0 -> output.dataPoints[index].set(0,123456L);
                case 1 -> output.dataPoints[index]=LongArrayList.of(7654321L);
                case 2 -> output.columnGenerationSteps.set(index,(byte)3);
                case 3 -> output.columnWorldCompressionMode.set(index,(byte)2);
            }
            int expected=(int)(output.getPos()^(output.getPos()>>>32));
            expected=31*expected+Arrays.deepHashCode(output.dataPoints);
            expected=17*expected+output.columnGenerationSteps.hashCode();expected=43*expected+output.columnWorldCompressionMode.hashCode();
            assertEquals(expected,output.hashCode(),"First hash observes public mutation "+mutation);
            output.dataPoints[index].clear();assertEquals(expected,output.hashCode(),"Original public mutation does not invalidate an already cached hash");
        }
    }
    @Test void explicitObjectSerializationEscapesValuesWithoutNativeOwnership() throws Exception {
        var columns=NativeDhColumns.build(level,light(chunk(0,1)),1);assertNotNull(columns);var original=columns.column(0,0);
        var bytes=new ByteArrayOutputStream();try(var output=new ObjectOutputStream(bytes)){output.writeObject(original);}
        try(var input=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))){var restored=input.readObject();assertInstanceOf(LongArrayList.class,restored);assertEquals(original,restored);}
    }
    @Test void eventSubscribersRunOriginalCallbacksAndCanOverrideMaterials() {
        var wrapper=light(chunk(0,0));var calls=new int[1];
        var handler=new com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiChunkProcessingEvent() {
            @Override public void blockOrBiomeChangedDuringChunkProcessing(
                    com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam<EventParam> event) {
                calls[0]++;event.value.setBlockOverride(BlockStateWrapper.AIR);
            }
        };
        var injector=com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector.INSTANCE;
        injector.bind(com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiChunkProcessingEvent.class,handler);
        try(var output=LodDataBuilder.createFromChunk(level,wrapper)) {
            assertNotNull(output);assertTrue(calls[0]>0);
            for(var column:output.dataPoints)if(column instanceof NativeDhColumnList nativeList)assertFalse(nativeList.isOriginalProducerView());
            for(int id=0;id<output.mapping.size();id++)assertTrue(output.mapping.getBlockStateWrapper(id).isAir());
        } finally { assertTrue(injector.unbind(com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiChunkProcessingEvent.class,handler.getClass())); }
    }
    @Test void customWorldCacheOverridesAndUnreadyBiomesDeclineBeforeNativePublication() {
        var source=chunk(0,0);var wrapper=light(source);var callbacks=new int[1];
        var custom=(ILevelWrapper)Proxy.newProxyInstance(ILevelWrapper.class.getClassLoader(),new Class<?>[]{ILevelWrapper.class},(p,m,a)->{callbacks[0]++;throw new AssertionError(m);});
        assertNull(NativeDhColumns.build(custom,wrapper,1));assertEquals(0,callbacks[0]);
        var holder=pattern.getFirst();var replacement=BiomeWrapper.getBiomeWrapper(pattern.get(1),level);
        var previous=BiomeWrapper.WRAPPER_BY_BIOME.put(holder,replacement);
        try { assertNull(NativeDhColumns.build(level,wrapper,1)); }
        finally { if(previous==null)BiomeWrapper.WRAPPER_BY_BIOME.remove(holder);else BiomeWrapper.WRAPPER_BY_BIOME.put(holder,previous); }
        source.setPersistedStatus(ChunkStatus.EMPTY);assertNull(NativeDhColumns.build(level,wrapper,1));
    }
}
