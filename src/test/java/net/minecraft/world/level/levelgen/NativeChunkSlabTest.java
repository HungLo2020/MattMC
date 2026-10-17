package net.minecraft.world.level.levelgen;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.NativeChunkSlab;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A chunk's fresh sections come from one native slab and behave exactly like
 * sections created one by one: reads, writes with palette growth, counters,
 * biome writes, copies and network form, and slab lifetime under GC.
 */
class NativeChunkSlabTest {
    @BeforeAll static void load() { NativeNoiseFillTest.load(); }
    @AfterAll static void close() { NativeNoiseFillTest.close(); }

    private static byte[] network(LevelChunkSection section) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        section.write(buffer);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }

    private static void assertSame(LevelChunkSection expected, LevelChunkSection actual, String label) {
        assertEquals(expected.hasOnlyAir(), actual.hasOnlyAir(), label);
        assertEquals(expected.packedSectionCounts(), actual.packedSectionCounts(), label + " counters");
        for (int i = 0; i < 4096; i++) {
            int x = i & 15, y = i >> 8, z = (i >> 4) & 15;
            assertSame(expected.getBlockState(x, y, z), actual.getBlockState(x, y, z), label + " block " + i);
        }
        for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++)
            assertSame(expected.getNoiseBiome(x, y, z), actual.getNoiseBiome(x, y, z), label + " biome");
        assertArrayEquals(network(expected), network(actual), label + " network form");
    }
    private static void assertSame(Object expected, Object actual, String label) {
        if (expected != actual) fail(label + ": expected " + expected + " but was " + actual);
    }

    @Test
    void protoChunkSectionsComeFromOneSlabAndMatchFreshSections() {
        var factory = PalettedContainerFactory.create(NativeNoiseFillTest.registries);
        long before = NativeChunkSlab.created();
        var chunk = new ProtoChunk(ChunkPos.ZERO, UpgradeData.EMPTY, LevelHeightAccessor.create(-64, 384), factory, null);
        assertEquals(before + 1, NativeChunkSlab.created(), "one slab for the chunk's 24 fresh sections");
        var sections = chunk.getSections();
        assertEquals(24, sections.length);

        List<BlockState> states = new ArrayList<>();
        for (int id = 0; id < Block.BLOCK_STATE_REGISTRY.size(); id += 97) states.add(Block.BLOCK_STATE_REGISTRY.byId(id));
        List<Holder<Biome>> biomes = NativeNoiseFillTest.registries.lookupOrThrow(Registries.BIOME).listElements()
            .map(holder -> (Holder<Biome>) holder).toList();
        Random random = new Random(11);
        for (int s = 0; s < sections.length; s++) {
            var slab = sections[s];
            var single = new LevelChunkSection(factory);
            assertSame(single, slab, "fresh section " + s);
            var slabCopy = slab.copy();
            // Writes through the counter-fused path, growing the palette several times.
            int writes = s == 0 ? 0 : 1 << Math.min(s, 11);
            for (int w = 0; w < writes; w++) {
                int x = random.nextInt(16), y = random.nextInt(16), z = random.nextInt(16);
                var state = states.get(random.nextInt(Math.min(states.size(), 2 + s * 3)));
                assertSame(single.setBlockState(x, y, z, state), slab.setBlockState(x, y, z, state), "previous state");
            }
            if (s % 3 == 0) {
                var a = (PalettedContainer<Holder<Biome>>) single.getBiomes();
                var b = (PalettedContainer<Holder<Biome>>) slab.getBiomes();
                for (int w = 0; w < s; w++) {
                    int x = random.nextInt(4), y = random.nextInt(4), z = random.nextInt(4);
                    var biome = biomes.get(random.nextInt(Math.min(biomes.size(), 1 + s)));
                    assertSame(a.getAndSet(x, y, z, biome), b.getAndSet(x, y, z, biome), "previous biome");
                }
            }
            assertSame(single, slab, "written section " + s);
            assertSame(new LevelChunkSection(factory), slabCopy, "copy taken before writes " + s);
            assertSame(slab.copy(), slab, "copy after writes " + s);
        }
    }

    @Test
    void oneRetainedSectionKeepsItsSlabAliveThroughCollections() throws Exception {
        var factory = PalettedContainerFactory.create(NativeNoiseFillTest.registries);
        var kept = new ProtoChunk(ChunkPos.ZERO, UpgradeData.EMPTY, LevelHeightAccessor.create(-64, 384), factory, null).getSections()[5];
        var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 200; i++) new ProtoChunk(new ChunkPos(i, round), UpgradeData.EMPTY, LevelHeightAccessor.create(-64, 384), factory, null);
            System.gc();
            Thread.sleep(50);
            kept.setBlockState(round, round, round, stone);
            assertSame(stone, kept.getBlockState(round, round, round), "retained slab section after GC " + round);
            assertEquals(round + 1, kept.packedSectionCounts() & 0xffff, "non-empty count");
        }
    }
}
