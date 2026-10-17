package net.minecraft.world.level.chunk;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeSectionCountersTest {
    private static Holder<Biome> biome;
    @BeforeAll static void bootstrap() {
        NativePalettePackingTest.bootstrap();
        biome = Holder.direct(new Biome.BiomeBuilder().hasPrecipitation(false).temperature(0).downfall(0)
                .specialEffects(new net.minecraft.world.level.biome.BiomeSpecialEffects.Builder()
                        .fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build())
                .mobSpawnSettings(net.minecraft.world.level.biome.MobSpawnSettings.EMPTY)
                .generationSettings(net.minecraft.world.level.biome.BiomeGenerationSettings.EMPTY).build());
    }
    private static PalettedContainer<BlockState> fresh() {
        return new PalettedContainer<>(Blocks.AIR.defaultBlockState(), Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
    }
    private static LevelChunkSection section(PalettedContainer<BlockState> states) {
        return new LevelChunkSection(states, new PalettedContainer<Holder<Biome>>(biome, Strategy.createForBiomes(new IdMapper<>())));
    }
    @Test void canonicalPoliciesUseTheFusedNativeEntryAndDirectRecount() {
        var states = fresh(); var counters = new NativeSectionCounters(0); var owner = states.nativeLiveBlocks();
        assertNotNull(owner);
        assertEquals(Block.getId(Blocks.AIR.defaultBlockState()), owner.writeWithCounters(17, Block.getId(Blocks.WATER.defaultBlockState()), counters));
        assertEquals(1L | (1L << 32), counters.packed());
        assertTrue(owner.recount(counters)); assertEquals(2, counters.packed());
        assertSame(Blocks.WATER.defaultBlockState(), states.get(1, 0, 1));
    }
    @Test void sharedContainersKeepIndependentCountersAndCopyKeepsStaleCounts() {
        var states = fresh(); var a = section(states); var b = section(states);
        a.setBlockState(0, 0, 0, Blocks.WATER.defaultBlockState());
        assertEquals(1L | (1L << 32), a.packedSectionCounts());
        assertEquals(0, b.packedSectionCounts());
        var copy = b.copy();
        assertEquals(0, copy.packedSectionCounts());
        assertSame(Blocks.WATER.defaultBlockState(), copy.getBlockState(0, 0, 0));
        b.setBlockState(0, 0, 0, Blocks.AIR.defaultBlockState(), false);
        assertEquals(65535L | (65535L << 32), b.packedSectionCounts());
        assertEquals(1L | (1L << 32), a.packedSectionCounts());
        assertSame(Blocks.WATER.defaultBlockState(), copy.getBlockState(0, 0, 0));
        copy.recalcBlockCounts(); assertEquals(2, copy.packedSectionCounts());
    }
    @Test void failedNetworkReadPreservesItsCounterPrefixAndSinglePaletteAliases() {
        var a = section(fresh()); var b = a.copy();
        var wire = new FriendlyByteBuf(Unpooled.buffer());
        try {
            wire.writeShort(-7); wire.writeByte(4);
            assertThrows(IndexOutOfBoundsException.class, () -> b.read(wire));
            assertEquals(65529, b.packedSectionCounts());
            assertEquals(0, a.packedSectionCounts());
            wire.clear(); wire.writeByte(0); wire.writeVarInt(Block.getId(Blocks.LAVA.defaultBlockState()));
            b.getStates().read(wire);
            assertSame(Blocks.LAVA.defaultBlockState(), a.getBlockState(0, 0, 0));
            assertEquals(0, a.packedSectionCounts()); // Palette alias never recounts its other section.
            a.recalcBlockCounts();
            assertEquals(8192L | (4096L << 16) | (4096L << 32), a.packedSectionCounts());
            assertEquals(65529, b.packedSectionCounts());
        } finally { wire.release(); }
    }
    @Test void signedCounterOverflowAndInvalidWritesMatchTheOriginalOracle() {
        var a = section(fresh());
        a.installGenerated(0, List.of(Blocks.AIR.defaultBlockState()), new long[0], 32767, -32768, 65535);
        var before = a.packedSectionCounts();
        assertThrows(IllegalArgumentException.class, () -> a.setBlockState(-1, 0, 0, Blocks.STONE.defaultBlockState()));
        assertEquals(before, a.packedSectionCounts());
        a.setBlockState(0, 0, 0, Blocks.OAK_LEAVES.defaultBlockState());
        assertEquals(32768L | (32769L << 16) | (65535L << 32), a.packedSectionCounts());
        assertFalse(a.isRandomlyTickingBlocks());
        var reference = new JavaSectionCounts(fresh()); var actual = section(fresh());
        long seed = 713;
        for (int n = 0; n < 20000; n++) {
            seed = seed * 6364136223846793005L + 1;
            int index = (int)(seed >>> 32) & 4095;
            var value = Block.BLOCK_STATE_REGISTRY.byId(n < 600 ? n : (int)((seed >>> 16) % Block.BLOCK_STATE_REGISTRY.size()));
            assertSame(reference.setBlockState(index & 15, index >> 8, index >> 4 & 15, value, false),
                    actual.setBlockState(index & 15, index >> 8, index >> 4 & 15, value, false));
            assertEquals(reference.rawCounts(), actual.packedSectionCounts());
            if ((n & 255) == 0) { reference.recalcBlockCounts(); actual.recalcBlockCounts(); assertEquals(reference.rawCounts(), actual.packedSectionCounts()); }
        }
    }
    @Test void customCallbackFailurePreservesTheOriginalCounterPrefixAndTrace() {
        var trace = new java.util.ArrayList<String>();
        class CustomState extends BlockState {
            final String label;
            final boolean throwsTick;
            CustomState(String label, boolean throwsTick) {
                super(Blocks.STONE, new it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap<>(),
                        com.mojang.serialization.MapCodec.unit(Blocks.STONE.defaultBlockState()));
                this.label = label; this.throwsTick = throwsTick;
            }
            @Override public net.minecraft.world.level.material.FluidState getFluidState() {
                trace.add(label + ":fluid"); return Blocks.AIR.defaultBlockState().getFluidState();
            }
            @Override public boolean isAir() { trace.add(label + ":air"); return false; }
            @Override public boolean isRandomlyTicking() {
                trace.add(label + ":tick");
                if (throwsTick) throw new IllegalStateException("custom tick");
                return true;
            }
        }
        var old = new CustomState("old", false); var value = new CustomState("new", true);
        var states = new PalettedContainer<BlockState>(old, Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
        var actual = section(states); var reference = new JavaSectionCounts(states.copy());
        trace.clear();
        var expected = assertThrows(IllegalStateException.class, () -> reference.setBlockState(0, 0, 0, value));
        var expectedTrace = List.copyOf(trace); trace.clear();
        var failure = assertThrows(IllegalStateException.class, () -> actual.setBlockState(0, 0, 0, value));
        assertEquals(expected.getMessage(), failure.getMessage()); assertEquals(expectedTrace, trace);
        assertEquals(reference.rawCounts(), actual.packedSectionCounts());
        assertEquals(4096L | (4095L << 16), actual.packedSectionCounts());
        assertSame(value, actual.getBlockState(0, 0, 0));
        long prefix = actual.packedSectionCounts();
        assertThrows(IllegalStateException.class, actual::recalcBlockCounts);
        assertEquals(prefix, actual.packedSectionCounts());
    }
    @Test void readOnlyCounterViewRetainsItsOwnerThroughGc() throws Exception {
        var section = section(fresh()); section.setBlockState(0, 0, 0, Blocks.STONE.defaultBlockState());
        var field = LevelChunkSection.class.getDeclaredField("counters"); field.setAccessible(true);
        var counts = (NativeSectionCounters)field.get(section); MemorySegment view = counts.owner();
        assertTrue(view.isReadOnly());
        assertThrows(IllegalArgumentException.class, () -> view.set(ValueLayout.JAVA_LONG, 0, 0));
        section = null; counts = null; System.gc();
        assertEquals(1, (long)ValueLayout.JAVA_LONG.varHandle().getAcquire(view, 0L));
        assertThrows(NoSuchFieldException.class, () -> LevelChunkSection.class.getDeclaredField("nonEmptyBlockCount"));
    }
}
