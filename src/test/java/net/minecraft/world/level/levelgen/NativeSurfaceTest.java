package net.minecraft.world.level.levelgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NativeSurfaceTest {
    private static String previousByteBuddy;
    @BeforeAll static void bootstrap() {
        // The repository's Mockito/Byte Buddy version predates the required JDK.
        previousByteBuddy = System.getProperty("net.bytebuddy.experimental");
        System.setProperty("net.bytebuddy.experimental", "true");
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
    }
    @AfterAll static void restoreProperties() {
        if (previousByteBuddy == null) System.clearProperty("net.bytebuddy.experimental");
        else System.setProperty("net.bytebuddy.experimental", previousByteBuddy);
    }

    @Test void nativeBiomeColumnsMatchOriginalIncludingNegativeCoordinatesAndTies() {
        var random = new Random(923551);
        int[] selected = new int[4096], observed = new int[3];
        var pos = new BlockPos.MutableBlockPos();
        for (long seed : new long[]{0,42,-123456789,Long.MIN_VALUE,Long.MAX_VALUE}) {
            var manager = new BiomeManager((x,y,z) -> {observed[0]=x; observed[1]=y; observed[2]=z; return null;}, seed);
            for (int run=0;run<120;run++) {
                int x=run<8 ? new int[]{0,1,-1,2,-2,29999999,Integer.MIN_VALUE,Integer.MAX_VALUE}[run] : random.nextInt();
                int z=random.nextInt(), min=-2032+random.nextInt(1000), count=run==0?4096:1+random.nextInt(1000);
                NativeSurface.biomeIndices(seed,x,z,min,count,selected);
                int base=(min-2)>>2, height=((min+count-3)>>2)-base+2;
                for(int y=0;y<count;y++) {
                    manager.getBiome(pos.set(x,min+y,z)); int index=selected[y], column=index/height;
                    assertEquals(((x-2)>>2)+(column>>1),observed[0]);
                    assertEquals(base+index%height,observed[1]);
                    assertEquals(((z-2)>>2)+(column&1),observed[2]);
                }
            }
        }
        assertThrows(IllegalArgumentException.class,()->NativeSurface.biomeIndices(0,0,0,0,0,selected));
        assertThrows(IllegalArgumentException.class,()->NativeSurface.biomeIndices(0,0,0,0,4097,selected));
        assertThrows(IllegalArgumentException.class,()->NativeSurface.biomeIndices(0,0,0,0,2,new int[1]));
    }

    private static SurfaceRules.Context context(SurfaceSystem system, RandomState random, NoiseChunk noise) {
        var generator=mock(ChunkGenerator.class); when(generator.getMinY()).thenReturn(-64); when(generator.getGenDepth()).thenReturn(384);
        var chunk=mock(net.minecraft.world.level.chunk.ProtoChunk.class);
        when(chunk.getHeight(any(),anyInt(),anyInt())).thenAnswer(i->(int)i.getArgument(1)*3+(int)i.getArgument(2)*7);
        return new SurfaceRules.Context(system,random,chunk,noise,p->null,null,
            new WorldGenerationContext(generator,LevelHeightAccessor.create(-64,384)));
    }

    @Test void nativeRulesMatchJavaForExceptionalArithmeticBoundsAndLazyConditions() {
        var system=mock(SurfaceSystem.class); var randomState=mock(RandomState.class); var noise=mock(NoiseChunk.class);
        var secondary=new AtomicReference<>(0.0);
        when(system.getSurfaceDepth(anyInt(),anyInt())).thenReturn(3);
        when(system.getSurfaceSecondary(anyInt(),anyInt())).thenAnswer(i->secondary.get());
        when(noise.preliminarySurfaceLevel(anyInt(),anyInt())).thenReturn(63);
        when(randomState.getOrCreateRandomFactory(any())).thenReturn(new XoroshiroRandomSource.XoroshiroPositionalRandomFactory(43,91));
        when(randomState.getOrCreateNoise(any())).thenReturn(NormalNoise.create(new XoroshiroRandomSource(12),new NormalNoise.NoiseParameters(-3,1.0)));
        var conditions=new ArrayList<SurfaceRules.ConditionSource>();
        for(int offset:new int[]{Integer.MIN_VALUE,-20,0,3,Integer.MAX_VALUE}) for(boolean add:new boolean[]{false,true}) {
            conditions.add(SurfaceRules.stoneDepthCheck(offset,add,30,CaveSurface.FLOOR));
            conditions.add(SurfaceRules.stoneDepthCheck(offset,add,0,CaveSurface.CEILING));
            conditions.add(new SurfaceRules.WaterConditionSource(offset,-20,add));
            conditions.add(new SurfaceRules.YConditionSource(VerticalAnchor.absolute(offset),20,add));
        }
        conditions.addAll(List.of(SurfaceRules.hole(),SurfaceRules.steep(),SurfaceRules.abovePreliminarySurface(),
            SurfaceRules.noiseCondition(Noises.SURFACE,-.2,.4),SurfaceRules.verticalGradient("surface_test",VerticalAnchor.absolute(-20),VerticalAnchor.absolute(20))));
        for (int[] bounds : new int[][]{{10,10},{20,-20},{Integer.MIN_VALUE,Integer.MAX_VALUE},{Integer.MAX_VALUE,Integer.MIN_VALUE}})
            conditions.add(SurfaceRules.verticalGradient("surface_test",VerticalAnchor.absolute(bounds[0]),VerticalAnchor.absolute(bounds[1])));
        var rng=new Random(44831);
        double[] extras={-1,0,1,-.3,Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,Double.MIN_VALUE};
        for(var condition:conditions) for(boolean inverse:new boolean[]{false,true}) {
            var c=inverse?SurfaceRules.not(condition):condition;
            var source=SurfaceRules.sequence(SurfaceRules.ifTrue(c,SurfaceRules.state(Blocks.DIRT.defaultBlockState())),SurfaceRules.state(Blocks.STONE.defaultBlockState()));
            var javaContext=context(system,randomState,noise); var nativeContext=context(system,randomState,noise);
            var reference=source.apply(javaContext); var actual=new NativeSurface(source,nativeContext,null,null);
            for(int i=0;i<2000;i++) {
                int x=rng.nextInt(),z=rng.nextInt(),y=i%2==0?rng.nextInt():rng.nextInt(500)-100;
                int above=rng.nextInt(),below=rng.nextInt(),water=i%3==0?Integer.MIN_VALUE:rng.nextInt();
                secondary.set(extras[i%extras.length]); javaContext.updateXZ(x,z);nativeContext.updateXZ(x,z);
                javaContext.updateY(above,below,water,x,y,z);nativeContext.updateY(above,below,water,x,y,z);
                assertSame(reference.tryApply(x,y,z),actual.tryApply(x,y,z),condition+" at "+x+","+y+","+z);
            }
        }
    }

    @Test void lazySteepConditionObservesEarlierWritesInTheSameColumn() {
        var system=mock(SurfaceSystem.class);when(system.getSurfaceDepth(anyInt(),anyInt())).thenReturn(3);
        var random=mock(RandomState.class);var noise=mock(NoiseChunk.class);
        var context=context(system,random,noise);var chunk=context.chunk;
        var section=mock(net.minecraft.world.level.chunk.LevelChunkSection.class);
        var blocks=new net.minecraft.world.level.block.state.BlockState[16];java.util.Arrays.fill(blocks,Blocks.STONE.defaultBlockState());
        when(chunk.getMinY()).thenReturn(0);
        when(chunk.getSections()).thenReturn(new net.minecraft.world.level.chunk.LevelChunkSection[]{section});
        when(chunk.getSectionIndex(anyInt())).thenAnswer(i->(int)i.getArgument(0)>>4);
        when(section.getBlockState(anyInt(),anyInt(),anyInt())).thenAnswer(i->blocks[(int)i.getArgument(1)]);
        when(chunk.getHeight(any(),anyInt(),anyInt())).thenAnswer(i->{
            if((int)i.getArgument(1)!=0 || (int)i.getArgument(2)!=0)return 18;
            assertSame(Blocks.AIR.defaultBlockState(),blocks[15],"Top write must precede the first steep observation");return 14;
        });
        var source=SurfaceRules.sequence(SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR,SurfaceRules.state(Blocks.AIR.defaultBlockState())),
            SurfaceRules.ifTrue(SurfaceRules.steep(),SurfaceRules.state(Blocks.DIRT.defaultBlockState())),SurfaceRules.state(Blocks.GRAVEL.defaultBlockState()));
        var program=new NativeSurface(source,context,null,new BiomeManager((x,y,z)->null,0));
        assertTrue(program.canBatch);context.updateXZ(0,0);var writes=new ArrayList<Integer>();
        program.column(chunk,new net.minecraft.world.level.chunk.BlockColumn(){
            public net.minecraft.world.level.block.state.BlockState getBlock(int y){return blocks[y];}
            public void setBlock(int y,net.minecraft.world.level.block.state.BlockState state){writes.add(y);blocks[y]=state;}
        },Blocks.STONE.defaultBlockState(),0,0,15);
        assertEquals(16,writes.size());
        for(int i=0;i<16;i++){assertEquals(15-i,writes.get(i));assertSame(i==15?Blocks.AIR.defaultBlockState():Blocks.DIRT.defaultBlockState(),blocks[i]);}
    }

    @Test void largeAndDeepRuleTreesUseNativeContinuationsWithoutChangingFirstMatch() {
        var system=mock(SurfaceSystem.class);when(system.getSurfaceDepth(anyInt(),anyInt())).thenReturn(3);
        var c=context(system,mock(RandomState.class),mock(NoiseChunk.class));c.updateXZ(0,0);c.updateY(1,1,Integer.MIN_VALUE,0,0,0);
        var children=new ArrayList<SurfaceRules.RuleSource>();
        for(int i=0;i<12000;i++)children.add(SurfaceRules.ifTrue(SurfaceRules.yBlockCheck(VerticalAnchor.absolute(1),0),SurfaceRules.state(Blocks.DIRT.defaultBlockState())));
        children.add(SurfaceRules.state(Blocks.STONE.defaultBlockState()));
        var source=new SurfaceRules.SequenceRuleSource(children);
        assertSame(source.apply(c).tryApply(0,0,0),new NativeSurface(source,c,null,null).tryApply(0,0,0));
        SurfaceRules.RuleSource deep=SurfaceRules.state(Blocks.STONE.defaultBlockState());
        for(int i=0;i<600;i++)deep=SurfaceRules.ifTrue(SurfaceRules.not(SurfaceRules.hole()),deep);
        assertSame(deep.apply(c).tryApply(0,0,0),new NativeSurface(deep,c,null,null).tryApply(0,0,0));
    }
}
