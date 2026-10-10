package net.minecraft.world.level.chunk;

import java.lang.reflect.Field;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeBiomeWorldTest {
    @BeforeAll static void load() { NativeLiveBiomeSectionTest.load(); }

    @Test void mutableSectionArrayEscapeDeclinesNativeSamplingBeforeCallerReplacement() throws Exception {
        var strategy = NativeLiveBiomeSectionTest.strategy;
        var values = NativeLiveBiomeSectionTest.values;
        var blockStrategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        var factory = new PalettedContainerFactory(blockStrategy, Blocks.AIR.defaultBlockState(), null,
            strategy, values.get(0), null);
        LevelChunk chunk = mock(LevelChunk.class, CALLS_REAL_METHODS);
        assertEquals(LevelChunk.class, chunk.getClass());
        doReturn(new ChunkPos(0,0)).when(chunk).getPos();
        var sections = new LevelChunkSection[2];
        for (int i=0;i<2;i++) sections[i] = new LevelChunkSection(
            new PalettedContainer<>(Blocks.AIR.defaultBlockState(),blockStrategy),
            new PalettedContainer<>(values.get(7),strategy));
        Field field = ChunkAccess.class.getDeclaredField("sections"); field.setAccessible(true); field.set(chunk,sections);
        var world = NativeBiomeWorld.create(factory,0,32); assertNotNull(world);
        world.range(0,0,3); world.replace(chunk);
        var position = new Vec3(1.5,1.5,1.5);
        Vec3 first = world.sample(position); assertNotNull(first); assertTrue(first.z > 0);
        world.range(Integer.MIN_VALUE,Integer.MIN_VALUE,3);
        assertEquals(first,world.sample(position)); // Frozen int subtraction/abs admits resident chunk zero.
        world.range(0,0,3);
        assertSame(sections,chunk.getSectionsForRead()); assertFalse(chunk.hasEscapedSectionArray());
        chunk.setNativeSectionArrayEscapeListener(() -> world.replace(chunk));
        var mutable = chunk.getSections(); assertSame(sections,mutable); assertTrue(chunk.hasEscapedSectionArray());
        assertNull(world.sample(position)); // Invalidation precedes mutable caller access.
        mutable[0] = null; world.replace(chunk); assertNull(world.sample(position));
        world.remove(0,0); assertEquals(Vec3.ZERO,world.sample(position));
    }
}
