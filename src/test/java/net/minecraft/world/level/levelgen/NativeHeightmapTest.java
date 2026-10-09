package net.minecraft.world.level.levelgen;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeHeightmapTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    static EnumSet<Heightmap.Types> types(int bits) {
        var result = EnumSet.noneOf(Heightmap.Types.class);
        for (var type : Heightmap.Types.values()) if ((bits & (1 << type.ordinal())) != 0) result.add(type);
        return result;
    }

    static void compare(ProtoChunk original, ProtoChunk candidate, Set<Heightmap.Types> types) {
        JavaHeightmapPrimer.primeHeightmaps(original,types);
        assertTrue(NativeHeightmap.prime(candidate,types),"Must exercise the native route");
        for (var type : Heightmap.Types.values()) {
            assertEquals(original.hasPrimedHeightmap(type),candidate.hasPrimedHeightmap(type));
            if (original.hasPrimedHeightmap(type)) {
                assertArrayEquals(original.getOrCreateHeightmapUnprimed(type).getRawData(),candidate.getOrCreateHeightmapUnprimed(type).getRawData(),type.toString());
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
                    assertEquals(original.getOrCreateHeightmapUnprimed(type).getFirstAvailable(x,z),candidate.getOrCreateHeightmapUnprimed(type).getFirstAvailable(x,z));
            }
        }
    }

    @Test void everyTypeSubsetAndExistingRawBits() {
        int cases = 0;
        for (var pattern : HeightmapFixtures.WORKLOADS) for (int seed = 0; seed < 8; seed++) {
            int minY = new int[]{-64,0,-2032,2032}[seed % 4];
            int height = new int[]{16,32,128,384}[seed % 4];
            var source = HeightmapFixtures.chunk(pattern,minY,height,seed);
            for (int bits = 0; bits < 64; bits++) {
                var a = HeightmapFixtures.withSections(source.getSections(),minY,height);
                var b = HeightmapFixtures.withSections(source.getSections(),minY,height);
                HeightmapFixtures.initialMaps(a,seed); HeightmapFixtures.initialMaps(b,seed);
                compare(a,b,types(bits)); cases++;
            }
        }
        System.out.println("HEIGHTMAP_PARITY fixtures="+cases+" raw_maps="+(cases*6)+" columns="+(cases*6*256));
    }

    @Test void missingMapsReprimeAndBlockMutations() {
        for (var pattern : HeightmapFixtures.WORKLOADS) {
            var source = HeightmapFixtures.chunk(pattern,-64,384,61);
            var a = HeightmapFixtures.withSections(source.getSections(),-64,384);
            var b = HeightmapFixtures.withSections(source.getSections(),-64,384);
            for (int step = 0; step < 5; step++) {
                compare(a,b,types(63));
                var section = source.getSection(23);
                section.setBlockState(step,15,step,step%2==0?Blocks.WATER.defaultBlockState():Blocks.OAK_LEAVES.defaultBlockState());
                section.recalcBlockCounts();
            }
        }
    }

    @Test void unresolvedColumnsPreserveLegacyIteratorState() {
        for (var pattern : new String[]{"backlog","backlog_leaves","backlog_cave_air","backlog_air"}) for (int prefix : new int[]{0,9,24}) {
            var source = HeightmapFixtures.chunk(pattern,-64,32,prefix);
            for (int bits = 1; bits < 64; bits++) {
                var a = HeightmapFixtures.withSections(source.getSections(),-64,32);
                var b = HeightmapFixtures.withSections(source.getSections(),-64,32);
                HeightmapFixtures.initialMaps(a,bits); HeightmapFixtures.initialMaps(b,bits);
                compare(a,b,types(bits));
            }
        }
        System.out.println("HEIGHTMAP_BACKLOG_PARITY fixtures=756");
    }

    @Test void savedTerrainAcrossSeedsAndDimensions() {
        int cases = 0;
        for (var record : HeightmapFixtures.saved()) {
            var source = HeightmapFixtures.recorded(record);
            for (int bits = 1; bits < 64; bits++) {
                var a = HeightmapFixtures.withSections(source.getSections(),source.getMinY(),source.getHeight());
                var b = HeightmapFixtures.withSections(source.getSections(),source.getMinY(),source.getHeight());
                if ((bits&1)==0) {HeightmapFixtures.initialMaps(a,bits);HeightmapFixtures.initialMaps(b,bits);}
                compare(a,b,types(bits)); cases++;
            }
        }
        System.out.println("HEIGHTMAP_SAVED_PARITY fixtures="+cases+" saved_chunks="+HeightmapFixtures.saved().size());
    }

    @Test void customReadersKeepOriginalCallbacks() {
        var source = HeightmapFixtures.chunk("overworld",-64,128,18);
        var seen = new ArrayList<Long>();
        var custom = new ProtoChunk(source.getPos(),UpgradeData.EMPTY,source.getSections(),new net.minecraft.world.ticks.ProtoChunkTicks<>(),new net.minecraft.world.ticks.ProtoChunkTicks<>(),net.minecraft.world.level.LevelHeightAccessor.create(-64,128),null,null) {
            @Override public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos pos) { seen.add(pos.asLong()); return super.getBlockState(pos); }
        };
        assertFalse(NativeHeightmap.prime(custom,types(63)));
        assertTrue(seen.isEmpty());
        JavaHeightmapPrimer.primeHeightmaps(custom,types(63)); var expected = List.copyOf(seen);
        seen.clear(); Heightmap.primeHeightmaps(custom,types(63)); assertEquals(expected,seen);
        var plain = HeightmapFixtures.withSections(source.getSections(),-64,128);
        assertFalse(NativeHeightmap.prime(plain,new LinkedHashSet<>(types(63))));
    }

    @Test void parallelReadersAndMaximumHeight() throws Exception {
        var large = HeightmapFixtures.chunk("end",-2048,4096,3);
        compare(HeightmapFixtures.withSections(large.getSections(),-2048,4096),HeightmapFixtures.withSections(large.getSections(),-2048,4096),types(63));
        var source = HeightmapFixtures.chunk("global",-64,128,193);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<Future<?>>();
            for (int task = 0; task < 4; task++) futures.add(executor.submit(() -> {
                for (int i = 0; i < 40; i++) {
                    var a = HeightmapFixtures.withSections(source.getSections(),-64,128);
                    var b = HeightmapFixtures.withSections(source.getSections(),-64,128);
                    HeightmapFixtures.initialMaps(a,i); HeightmapFixtures.initialMaps(b,i);
                    compare(a,b,types(i%63+1));
                }
            }));
            for (var future : futures) future.get();
        }
    }

    @Test void corruptPackedIdPreservesOriginalFailureAndPartialWrites() throws Exception {
        var source = HeightmapFixtures.chunk("solid",-64,32,0);
        long[] words = net.minecraft.world.level.chunk.SectionFingerprint.rawWords(source.getSection(1).getStates());words[words.length-1] |= 15L << 60;
        var a = HeightmapFixtures.withSections(source.getSections(),-64,32);
        var b = HeightmapFixtures.withSections(source.getSections(),-64,32);
        var expected = assertThrows(RuntimeException.class,()->JavaHeightmapPrimer.primeHeightmaps(a,types(63)));
        var actual = assertThrows(RuntimeException.class,()->Heightmap.primeHeightmaps(b,types(63)));
        assertEquals(expected.getClass(),actual.getClass());assertEquals(expected.getMessage(),actual.getMessage());
        for(var type:Heightmap.Types.values())assertArrayEquals(a.getOrCreateHeightmapUnprimed(type).getRawData(),b.getOrCreateHeightmapUnprimed(type).getRawData());
    }

    @Test void realLevelChunksAndDebugWorldCompatibility() {
        String previous = System.getProperty("net.bytebuddy.experimental");
        System.setProperty("net.bytebuddy.experimental","true");
        try {
            var level = org.mockito.Mockito.mock(net.minecraft.world.level.Level.class);
            org.mockito.Mockito.when(level.getMinY()).thenReturn(-64);
            org.mockito.Mockito.when(level.getHeight()).thenReturn(128);
            org.mockito.Mockito.when(level.getSectionsCount()).thenReturn(8);
            var source = HeightmapFixtures.chunk("overworld",-64,128,77);
            var a = new LevelChunk(level,source.getPos(),UpgradeData.EMPTY,new net.minecraft.world.ticks.LevelChunkTicks<>(),new net.minecraft.world.ticks.LevelChunkTicks<>(),0,source.getSections(),null,null);
            var b = new LevelChunk(level,source.getPos(),UpgradeData.EMPTY,new net.minecraft.world.ticks.LevelChunkTicks<>(),new net.minecraft.world.ticks.LevelChunkTicks<>(),0,source.getSections(),null,null);
            HeightmapFixtures.initialMaps(a,193);HeightmapFixtures.initialMaps(b,193);
            JavaHeightmapPrimer.primeHeightmaps(a,types(63));assertTrue(NativeHeightmap.prime(b,types(63)));
            for(var type:Heightmap.Types.values())assertArrayEquals(a.getOrCreateHeightmapUnprimed(type).getRawData(),b.getOrCreateHeightmapUnprimed(type).getRawData());
            org.mockito.Mockito.when(level.isDebug()).thenReturn(true);
            assertFalse(NativeHeightmap.prime(b,types(63)));
        } finally {
            if(previous==null)System.clearProperty("net.bytebuddy.experimental");else System.setProperty("net.bytebuddy.experimental",previous);
        }
    }
}
