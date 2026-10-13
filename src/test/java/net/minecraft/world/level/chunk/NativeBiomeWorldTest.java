package net.minecraft.world.level.chunk;

import java.lang.reflect.Field;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeBiomeWorldTest {
    @BeforeAll static void load() { NativeLiveBiomeSectionTest.load(); }

    @Test void directIdentityReadsClampHeightAndObservePrimaryMutationWithoutRepublishing() throws Exception {
        var chunk=chunk(2,7);var world=NativeBiomeWorld.create(factory(),0,32);assertNotNull(world);
        world.range(0,0,3);world.replace(chunk);
        var values=NativeLiveBiomeSectionTest.values;
        for(int section=0;section<2;section++) {
            @SuppressWarnings("unchecked") var biomes=(PalettedContainer<Holder<Biome>>)chunk.getSection(section).getBiomes();
            for(int i=0;i<64;i++)biomes.getAndSet(i&3,i>>4,i>>2&3,values.get(section*64+i));
        }
        for(int y:new int[]{Integer.MIN_VALUE,-1,0,1,3,4,7,8,Integer.MAX_VALUE}) {
            int clamped=Math.max(0,Math.min(7,y));
            for(int x=0;x<4;x++)for(int z=0;z<4;z++)
                assertSame(values.get((clamped/4)*64+((clamped&3)<<4)+(z<<2)+x),world.biome(x,y,z));
        }
        assertSame(values.get(0),world.biome(-1,0,0));
        world.range(10,10,1);assertSame(values.get(0),world.biome(1,1,1));
        world.range(0,0,3);assertSame(values.get(21),world.biome(1,1,1));
        chunk.setNativeSectionArrayEscapeListener(()->world.replace(chunk));
        chunk.getSections();assertNull(world.biome(1,1,1));assertNull(world.biomeAtBlock(7,6,6,6));
        world.remove(0,0);assertSame(values.get(0),world.biome(1,1,1));
    }

    @Test void clientManagerConsumesNativeIdentityAndCompatibilityKeepsSourceDispatch() throws Exception {
        var chunk=chunk(2,7);var world=NativeBiomeWorld.create(factory(),0,32);assertNotNull(world);
        world.range(0,0,3);world.replace(chunk);
        var level=mock(ClientLevel.class,CALLS_REAL_METHODS);
        var cache=mock(net.minecraft.client.multiplayer.ClientChunkCache.class,CALLS_REAL_METHODS);
        Field nativeWorld=cache.getClass().getDeclaredField("nativeBiomes");nativeWorld.setAccessible(true);nativeWorld.set(cache,world);
        Field source=ClientLevel.class.getDeclaredField("chunkSource");source.setAccessible(true);source.set(level,cache);
        var manager=new net.minecraft.world.level.biome.BiomeManager(level,7);
        assertSame(NativeLiveBiomeSectionTest.values.get(7),manager.getBiome(new net.minecraft.core.BlockPos(6,6,6)));
        assertSame(NativeLiveBiomeSectionTest.values.get(7),level.getNoiseBiome(1,1,1));
        verify(level,never()).getChunk(anyInt(),anyInt(),any(),anyBoolean());
        doReturn(null).when(level).getChunk(anyInt(),anyInt(),any(),anyBoolean());
        doReturn(NativeLiveBiomeSectionTest.values.get(91)).when(level).getUncachedNoiseBiome(anyInt(),anyInt(),anyInt());
        chunk.setNativeSectionArrayEscapeListener(()->world.replace(chunk));chunk.getSections();
        assertSame(NativeLiveBiomeSectionTest.values.get(91),manager.getBiome(new net.minecraft.core.BlockPos(6,6,6)));
        verify(level,times(1)).getChunk(anyInt(),anyInt(),any(),anyBoolean());
    }

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
    private static PalettedContainerFactory factory() {
        return new PalettedContainerFactory(Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY),Blocks.AIR.defaultBlockState(),null,
            NativeLiveBiomeSectionTest.strategy,NativeLiveBiomeSectionTest.values.get(0),null);
    }
    private static LevelChunk chunk(int count,int biome) throws Exception {
        var chunk=mock(LevelChunk.class,CALLS_REAL_METHODS);var level=mock(ClientLevel.class);
        doReturn(level).when(chunk).getLevel();doReturn(new ChunkPos(0,0)).when(chunk).getPos();
        doReturn(0).when(chunk).getMinY();doReturn(count*16).when(chunk).getHeight();
        var sections=new LevelChunkSection[count];
        for(int i=0;i<count;i++)sections[i]=new LevelChunkSection(
            new PalettedContainer<>(Blocks.AIR.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),
            new PalettedContainer<>(NativeLiveBiomeSectionTest.values.get(biome),NativeLiveBiomeSectionTest.strategy));
        Field field=ChunkAccess.class.getDeclaredField("sections");field.setAccessible(true);field.set(chunk,sections);return chunk;
    }
    @Test void worldAndColumnsShareOneBindingAcrossRepeatsGrowthAndContainerReplacement() throws Exception {
        var chunk=chunk(2,7);var world=NativeBiomeWorld.create(factory(),0,32);assertNotNull(world);
        var expected=NativeLiveBiomeSection.binding(NativeLiveBiomeSectionTest.strategy);
        world.range(0,0,3);world.replace(chunk);var first=chunk.nativeBiomeSections(expected);assertNotNull(first);
        var columns=chunk.nativeDhColumnSections();assertNotNull(columns);
        Field attached=NativeChunkInputs.class.getDeclaredField("biomes");attached.setAccessible(true);assertSame(first,attached.get(columns));
        var position=new Vec3(1.5,1.5,1.5);var before=world.sample(position);assertNotNull(before);
        for(int i=0;i<8;i++){world.replace(chunk);assertSame(first,chunk.nativeBiomeSections(expected));assertSame(columns,chunk.nativeDhColumnSections());}
        @SuppressWarnings("unchecked") var values=(PalettedContainer<Holder<Biome>>)chunk.getSection(0).getBiomes();
        for(int i=0;i<64;i++)values.getAndSet(i&3,i>>4,i>>2&3,NativeLiveBiomeSectionTest.values.get(i+20));
        world.replace(chunk);assertSame(first,chunk.nativeBiomeSections(expected));assertNotEquals(before,world.sample(position));
        Field biomeField=LevelChunkSection.class.getDeclaredField("biomes");biomeField.setAccessible(true);
        biomeField.set(chunk.getSection(0),new PalettedContainer<>(NativeLiveBiomeSectionTest.values.get(91),NativeLiveBiomeSectionTest.strategy));
        world.replace(chunk);var replaced=chunk.nativeBiomeSections(expected);assertNotNull(replaced);assertNotSame(first,replaced);
        var next=chunk.nativeDhColumnSections();assertNotSame(columns,next);assertSame(replaced,attached.get(next));
        assertSame(NativeLiveBiomeSectionTest.values.get(20),first.biome(0).get(0));
        assertSame(NativeLiveBiomeSectionTest.values.get(91),replaced.biome(0).get(0));
        assertNotNull(world.sampleFog(position));world.remove(0,0);assertEquals(Vec3.ZERO,world.sample(position));
        assertSame(NativeLiveBiomeSectionTest.values.get(20),first.biome(0).get(0));
    }
    @Test void canonicalBiomesRemainNativeWhenBlockInputAdmissionDeclines() throws Exception {
        var chunk=chunk(2,7);
        var customBlocks=new PalettedContainer<>(Blocks.AIR.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)) {};
        Field states=LevelChunkSection.class.getDeclaredField("states");states.setAccessible(true);states.set(chunk.getSection(0),customBlocks);
        assertNull(chunk.nativeDhSections());var world=NativeBiomeWorld.create(factory(),0,32);assertNotNull(world);
        world.range(0,0,3);world.replace(chunk);var sample=world.sample(new Vec3(1.5,1.5,1.5));assertNotNull(sample);assertTrue(sample.z>0);
        assertNotNull(chunk.nativeBiomeSections(NativeLiveBiomeSection.binding(NativeLiveBiomeSectionTest.strategy)));
    }
    @Test void compatibilityMutationCannotReuseTheCachedInputOrColorWindow() throws Exception {
        var chunk=chunk(2,7);var world=NativeBiomeWorld.create(factory(),0,32);assertNotNull(world);
        world.range(0,0,3);world.replace(chunk);var position=new Vec3(1.5,1.5,1.5);assertNotNull(world.sample(position));
        var previous=chunk.nativeBiomeSections(NativeLiveBiomeSection.binding(NativeLiveBiomeSectionTest.strategy));assertNotNull(previous);
        ((PalettedContainer<?>)chunk.getSection(0).getBiomes()).dataForCompatibilityMutation();
        world.replace(chunk);assertNull(world.sample(position));assertNull(world.sampleFog(position));
        assertNull(chunk.nativeBiomeSections(NativeLiveBiomeSection.binding(NativeLiveBiomeSectionTest.strategy)));
        world.remove(0,0);assertEquals(Vec3.ZERO,world.sample(position));
    }
    @Test void unsupportedSectionBiomeGetterKeepsItsOriginalCallBeforeCompatibilityAdmission() throws Exception {
        var chunk=chunk(2,7);int[] calls={0};
        var custom=new LevelChunkSection(new PalettedContainer<>(Blocks.AIR.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),null) {
            @Override public PalettedContainerRO<Holder<Biome>> getBiomes() {calls[0]++;return null;}
        };
        chunk.getSectionsForRead()[0]=custom;
        var world=NativeBiomeWorld.create(factory(),0,32);assertNotNull(world);world.replace(chunk);
        assertEquals(1,calls[0]);assertNull(world.sample(new Vec3(1.5,1.5,1.5)));
    }
    @Test void zeroAndOversizedCanonicalArraysDisableRatherThanPretendTheChunkIsMissing() throws Exception {
        var world=NativeBiomeWorld.create(factory(),0,32);assertNotNull(world);world.replace(chunk(0,7));assertNull(world.sample(Vec3.ZERO));
        world=NativeBiomeWorld.create(factory(),0,32);assertNotNull(world);world.replace(chunk(1025,7));assertNull(world.sample(Vec3.ZERO));
    }
}
