package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import net.minecraft.world.level.chunk.DataLayer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeLightMapTest {
    private static int identity(DataLayer layer, ArrayList<DataLayer> pool) {
        if (layer == null) return 0;
        for (int i = 0; i < pool.size(); i++) if (pool.get(i) == layer) return i + 1;
        pool.add(layer); return pool.size();
    }

    @Test void actualFrozenOperationsCrossCanonicalProducerAndNativeOwners() throws Exception {
        var maps = new ArrayList<DataLayerStorageMap<?>>();
        var pool = new ArrayList<DataLayer>();
        try (var input = new DataInputStream(new GZIPInputStream(
                getClass().getResourceAsStream("/lighting/frozen-light-maps.bin.gz")))) {
            assertEquals(0x4c4d5031, input.readInt()); assertEquals(4096, input.readInt());
            for (int row = 0; row < 4096; row++) {
                int kind = input.readInt(), op = input.readInt(), index = input.readInt();
                long key = input.readLong();
                int arg = input.readInt(), arg2 = input.readInt(), expected = input.readInt();
                int expectedSample = input.readInt(), expectedError = input.readInt();
                if (row == 0 || row == 2048) {
                    maps.clear(); pool.clear();
                    maps.add(kind == 0 ? new BlockLightSectionStorage.BlockDataLayerStorageMap()
                        : new SkyLightSectionStorage.SkyDataLayerStorageMap());
                    for (int value : new int[]{3, 7, -9, 31}) pool.add(new DataLayer(value));
                }
                var map = maps.get(index); assertNotNull(map.nativeMap);
                int result = 0, error = 0, sample = 0; DataLayer returned = null;
                switch (op) {
                    case 0 -> map.setLayer(key, arg == 0 ? null : pool.get(arg - 1));
                    case 1 -> { returned = map.getLayer(key); result = identity(returned, pool); }
                    case 2 -> result = map.hasLayer(key) ? 1 : 0;
                    case 3 -> { returned = map.removeLayer(key); result = identity(returned, pool); }
                    case 4 -> { maps.add(map.copy()); result = maps.size() - 1; }
                    case 5 -> map.clearCache();
                    case 6 -> map.disableCache();
                    case 7 -> {
                        try { returned = map.copyDataLayer(key); result = identity(returned, pool); }
                        catch (NullPointerException failure) { error = 1; }
                    }
                    case 8 -> { pool.get(arg - 1).fill(arg2); result = pool.get(arg - 1).get(0, 0, 0); }
                    case 9 -> result = ((SkyLightSectionStorage.SkyDataLayerStorageMap)map).top(key);
                    case 10 -> result = ((SkyLightSectionStorage.SkyDataLayerStorageMap)map).putTop(key, arg);
                    case 11 -> result = ((SkyLightSectionStorage.SkyDataLayerStorageMap)map).removeTop(key);
                    case 12 -> ((SkyLightSectionStorage.SkyDataLayerStorageMap)map).lowestY(arg);
                    case 13 -> ((SkyLightSectionStorage.SkyDataLayerStorageMap)map).topDefault(arg);
                    default -> fail("Unknown Frozen operation " + op);
                }
                if (returned != null) sample = returned.get(0, 0, 0);
                assertArrayEquals(new int[]{expected, expectedSample, expectedError},
                    new int[]{result, sample, error}, "Frozen row " + row + ", op " + op);
                if (row % 127 == 0) System.gc();
            }
            assertEquals(-1, input.read());
        }
    }

    @Test void suppliedMapsRetainAliasesAndIndependentCopyPublication() {
        var layers = new Long2ObjectOpenHashMap<DataLayer>();
        var tops = new Long2IntOpenHashMap();
        var map = new SkyLightSectionStorage.SkyDataLayerStorageMap(layers, tops, -4);
        assertNull(map.nativeMap);
        var layer = new DataLayer(9); layers.put(1, layer); tops.put(1, 12);
        assertSame(layer, map.getLayer(1)); assertEquals(12, map.top(1));
        var copy = map.copy(); layers.remove(1); tops.put(1, 15);
        assertSame(layer, copy.getLayer(1)); assertEquals(12, copy.top(1));
        layer.fill(3); assertEquals(3, copy.getLayer(1).get(0, 0, 0));
    }

    @Test void arrayEscapeAndVirtualCopyRemainVisibleThroughRetainedMaps() {
        var map = new BlockLightSectionStorage.BlockDataLayerStorageMap();
        var source = new DataLayer(9); map.setLayer(1, source);
        var copy = map.copy();
        byte[] bytes = source.getData(); bytes[0] = 0x32;
        assertSame(source, copy.getLayer(1)); assertEquals(2, map.getLayer(1).get(0, 0, 0));
        assertEquals(3, copy.getLayer(1).get(1, 0, 0));
        source.fill(7); assertEquals(7, copy.getLayer(1).get(1, 0, 0));
        class CustomLayer extends DataLayer {
            int copies;
            @Override public DataLayer copy() { copies++; return new DataLayer(13); }
        }
        var custom = new CustomLayer(); map.setLayer(2, custom);
        DataLayer detached = map.copyDataLayer(2);
        assertEquals(1, custom.copies); assertEquals(13, detached.get(0, 0, 0));
        assertSame(detached, map.getLayer(2)); assertNotSame(custom, detached);
    }
    @Test void queuedLightPrecedesVisiblePublicationAndWritesDetachThePublishedLayer() {
        var getter = new net.minecraft.world.level.chunk.LightChunkGetter() {
            @Override public net.minecraft.world.level.chunk.LightChunk getChunkForLighting(int x, int z) { return null; }
            @Override public net.minecraft.world.level.BlockGetter getLevel() { return net.minecraft.world.level.EmptyBlockGetter.INSTANCE; }
        };
        var storage = new BlockLightSectionStorage(getter);
        long key = net.minecraft.core.SectionPos.asLong(0, 0, 0);
        var published = new DataLayer(4);
        storage.updatingSectionData.setLayer(key, published);
        storage.changedSections.add(key); storage.swapSectionMap();
        assertSame(published, storage.getDataLayerData(key));
        var queued = new DataLayer(6); storage.queuedSections.put(key, queued);
        assertSame(queued, storage.getDataLayerData(key));
        storage.setStoredLevel(net.minecraft.core.BlockPos.asLong(0, 0, 0), 5);
        assertEquals(4, published.get(0, 0, 0));
        assertEquals(5, storage.getDataLayer(key, true).get(0, 0, 0));
        assertSame(queued, storage.getDataLayerData(key));
        storage.swapSectionMap(); assertSame(queued, storage.getDataLayerData(key));
        storage.queuedSections.remove(key);
        assertEquals(5, storage.getDataLayerData(key).get(0, 0, 0));
        assertNotSame(published, storage.getDataLayerData(key));
    }

    @Test void actualFrozenScalarLightConsumersUseTheTypedMapDirectly() throws Exception {
        var getter = new net.minecraft.world.level.chunk.LightChunkGetter() {
            public net.minecraft.world.level.chunk.LightChunk getChunkForLighting(int x, int z) { return null; }
            public net.minecraft.world.level.BlockGetter getLevel() { return net.minecraft.world.level.EmptyBlockGetter.INSTANCE; }
        };
        try (var input = new DataInputStream(new GZIPInputStream(
                getClass().getResourceAsStream("/lighting/frozen-light-samples.bin.gz")))) {
            assertEquals(0x4c535031, input.readInt()); assertEquals(1024, input.readInt());
            for (int row = 0; row < 1024; row++) {
                int kind = input.readInt(), mode = input.readInt(); long block = input.readLong();
                int initial = input.readInt(); boolean allocated = input.readInt() != 0;
                boolean updating = input.readInt() != 0, enabled = input.readInt() != 0;
                int expected = input.readInt();
                long key = net.minecraft.core.SectionPos.blockToSection(block);
                long column = net.minecraft.core.SectionPos.getZeroNode(key);
                var layer = new DataLayer(initial);
                if (allocated) {
                    byte[] bytes = new byte[2048];
                    for (int j = 0; j < bytes.length; j++) bytes[j] = (byte)(j * 31 + row);
                    layer = DataLayer.copyOf(bytes);
                }
                int actual; long nativeResult;
                if (kind == 0) {
                    var storage = new BlockLightSectionStorage(getter);
                    if (mode != 0) storage.updatingSectionData.setLayer(key, layer);
                    storage.changedSections.add(key); storage.swapSectionMap();
                    nativeResult = storage.visibleSectionData.nativeMap.sample(block, false, false, true);
                    actual = storage.getLightValue(block);
                } else {
                    var storage = new SkyLightSectionStorage(getter);
                    var map = storage.updatingSectionData; int y = net.minecraft.core.SectionPos.y(key);
                    map.lowestY(y - 4);
                    int top = mode == 0 ? y - 4 : mode == 1 ? y : y + (mode == 2 ? 1 : 5);
                    map.putTop(column, top);
                    if (mode == 2) map.setLayer(key, layer);
                    else if (mode == 3 || mode == 5) {
                        map.setLayer(net.minecraft.core.SectionPos.offset(key, 0, mode == 3 ? 2 : 3, 0), layer);
                        if (mode == 5) map.setLayer(key, null);
                    }
                    storage.setLightEnabled(column, enabled);
                    storage.changedSections.add(key); storage.swapSectionMap();
                    nativeResult = (updating ? map : storage.visibleSectionData).nativeMap.sample(block, true, updating, enabled);
                    actual = storage.getLightValue(block, updating);
                }
                assertNotEquals(0L, nativeResult, "Native consumer must execute, row " + row);
                assertEquals(expected, (int)nativeResult, "Native Frozen row " + row);
                assertEquals(expected, actual, "World consumer Frozen row " + row);
            }
            assertEquals(-1, input.read());
        }
    }

    @Test void directConsumerDeclinesCustomLayersAndMutableArrayEscapes() {
        var map = new BlockLightSectionStorage.BlockDataLayerStorageMap();
        var source = new DataLayer(-1); map.setLayer(0, source); map.disableCache();
        long value = map.nativeMap.sample(0, false, false, true);
        assertNotEquals(0L, value); assertEquals(-1, (int)value);
        source.getData()[0] = 0x32;
        assertEquals(0L, map.nativeMap.sample(0, false, false, true));
        assertEquals(2, map.getLayer(0).get(0, 0, 0));
        source.fill(7); assertEquals(0L, map.nativeMap.sample(0, false, false, true));
        map.setLayer(0, source);
        assertEquals(7, (int)map.nativeMap.sample(0, false, false, true));
        class Custom extends DataLayer {
            @Override public int get(int x, int y, int z) { return 123; }
            @Override public java.lang.foreign.MemorySegment nativeLightOwnerForMap() {
                throw new AssertionError("Custom source admission must precede owner callbacks");
            }
        }
        var custom = new Custom(); map.setLayer(0, custom);
        assertEquals(0L, map.nativeMap.sample(0, false, false, true));
        assertEquals(123, map.getLayer(0).get(0, 0, 0));
    }

    @Test void customStorageKeepsItsVirtualLayerLookup() {
        var getter = new net.minecraft.world.level.chunk.LightChunkGetter() {
            public net.minecraft.world.level.chunk.LightChunk getChunkForLighting(int x, int z) { return null; }
            public net.minecraft.world.level.BlockGetter getLevel() { return net.minecraft.world.level.EmptyBlockGetter.INSTANCE; }
        };
        var result = new DataLayer(123);
        var storage = new BlockLightSectionStorage(getter) {
            @Override protected DataLayer getDataLayer(long key, boolean updating) { return result; }
        };
        assertEquals(123, storage.getLightValue(0));
    }

    @Test void concurrentSnapshotRetirementCannotClearAnUnresolvedRemoval() throws Exception {
        var producer = new BlockLightSectionStorage.BlockDataLayerStorageMap();
        var sibling = producer.copy(); sibling.disableCache();
        var layer = new DataLayer(7);
        var running = new java.util.concurrent.atomic.AtomicBoolean(true);
        var ready = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var drain = executor.submit(() -> {
            ready.countDown();
            while (running.get()) assertFalse(sibling.hasLayer(-1));
        });
        try {
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            for (int i = 0; i < 20_000; i++) {
                producer.setLayer(1, layer);
                assertSame(layer, producer.removeLayer(1));
            }
        } finally {
            running.set(false);
            try { drain.get(10, java.util.concurrent.TimeUnit.SECONDS); }
            finally { executor.shutdownNow(); }
        }
    }

    @Test void originalProtectedNullConstructorRemainsAvailableToExternalSubclasses() {
        var source = new DataLayer(11);
        var custom = new net.minecraft.world.level.lighting.compat.LegacyExternalLightMap(source);
        assertSame(source, custom.getLayer(9));
        assertSame(source, custom.copy().getLayer(12));
    }

}
