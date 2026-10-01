package net.minecraft.world.level.levelgen.feature;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeOreGeometryTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    static double[] spheres(int size,long seed,int x,int y,int z) {
        var random=new LegacyRandomSource(seed);
        float angle=random.nextFloat()*(float)Math.PI, g=size/8.0F;
        double d=x+Math.sin(angle)*g,e=x-Math.sin(angle)*g,f=z+Math.cos(angle)*g,h=z-Math.cos(angle)*g;
        double sy=y+random.nextInt(3)-2,ey=y+random.nextInt(3)-2;
        var ds=new double[size*4];
        for(int q=0;q<size;q++){
            float r=(float)q/size;
            ds[q*4]=Mth.lerp((double)r,d,e);ds[q*4+1]=Mth.lerp((double)r,sy,ey);ds[q*4+2]=Mth.lerp((double)r,f,h);
            double v=random.nextDouble()*size/16.0;
            ds[q*4+3]=((Mth.sin((float)Math.PI*r)+1.0F)*v+1.0)/2.0;
        }
        return ds;
    }
    static int[] nativeVein(int size,long seed,int x,int y,int z) {
        var random=new LegacyRandomSource(seed);
        float angle=random.nextFloat()*(float)Math.PI,g=size/8.0F;
        double x0=x+Math.sin(angle)*g,x1=x-Math.sin(angle)*g,z0=z+Math.cos(angle)*g,z1=z-Math.cos(angle)*g;
        double y0=y+random.nextInt(3)-2,y1=y+random.nextInt(3)-2;
        try(var spans=NativeOreGeometry.vein(random,size,x0,x1,y0,y1,z0,z1,x-40,y-40,z-40)) {return NativeOreGeometryBridge.expand(Arrays.copyOf(spans.values,spans.length));}
    }
    @Test void everyCodecSizeAndSeededGeometry(){
        long points=0;
        for(int size=0;size<=64;size++)for(int seed=0;seed<256;seed++){
            int x=(seed%3-1)*29_999_900,y=seed%400-64,z=-x;
            var input=spheres(size,seed*0x9e3779b97f4a7c15L,x,y,z);var before=input.clone();
            var expected=JavaOreGeometry.candidates(input,x-40,y-40,z-40);
            assertArrayEquals(expected,NativeOreGeometryBridge.candidates(input,x-40,y-40,z-40),"size="+size+" seed="+seed);
            assertArrayEquals(expected,nativeVein(size,seed*0x9e3779b97f4a7c15L,x,y,z),"Fused sphere formation");
            assertArrayEquals(before,input);points+=expected.length/3;
        }
        System.out.println("ORE_GEOMETRY_PARITY fixtures="+(65*256)+" points="+points);
    }
    @Test void programmaticSizesBeyondCodec() {
        for(int size:new int[]{65,96,128})for(int seed=0;seed<32;seed++)
            assertArrayEquals(JavaOreGeometry.candidates(spheres(size,seed,0,0,0),-40,-40,-40),nativeVein(size,seed,0,0,0));
    }
    @Test void strictEdgesNonfiniteAndContainment(){
        for(double radius:new double[]{-1.,-0.,0.,Double.MIN_VALUE,0.5,Math.nextDown(0.5),Math.nextUp(0.5),1.,Math.sqrt(0.75),2.,24.5,Double.NaN}){
            double[] input={0.5,0.5,0.5,radius,0.5,0.5,0.5,radius,1.,1.,1.,0.5,0.,0.,0.,2.};
            for(int low=-3;low<4;low++)assertArrayEquals(JavaOreGeometry.candidates(input,low,low,low),NativeOreGeometryBridge.candidates(input,low,low,low));
        }
        var random=new Random(738);
        for(int trial=0;trial<2000;trial++){
            double[] input=new double[(trial%65)*4];
            for(int i=0;i<input.length;i+=4){input[i]=random.nextDouble()*12-6;input[i+1]=random.nextDouble()*12-6;input[i+2]=random.nextDouble()*12-6;input[i+3]=random.nextDouble()*5-1;}
            assertArrayEquals(JavaOreGeometry.candidates(input,-4,-4,-4),NativeOreGeometryBridge.candidates(input,-4,-4,-4));
        }
        assertThrows(IllegalArgumentException.class,()->NativeOreGeometryBridge.candidates(new double[3],0,0,0));
    }
    static OreConfiguration configuration(int size,float discard,boolean benchmark){
        return new OreConfiguration(List.of(
            OreConfiguration.target(new RandomBlockMatchTest(Blocks.STONE,.65F),benchmark?Blocks.STONE.defaultBlockState():Blocks.IRON_ORE.defaultBlockState()),
            OreConfiguration.target(new BlockMatchTest(Blocks.STONE),benchmark?Blocks.STONE.defaultBlockState():Blocks.GOLD_ORE.defaultBlockState())),size,discard);
    }
    static boolean place(boolean nativeMode,OreTestWorld world,RandomSource random,OreConfiguration config,BlockPos origin){
        var context=new FeaturePlaceContext<>(Optional.empty(),world.level,null,random,origin,config);
        return nativeMode?new OreFeature(OreConfiguration.CODEC).place(context):new JavaOreFeature(OreConfiguration.CODEC).place(context);
    }
    @Test void originalPlacementTraceRngAndBlocks(){
        long events=0;
        var world=new OreTestWorld(-64,128,true,true);
        for(int size=0;size<=64;size++)for(int variant=0;variant<9;variant++){
            long seed=0x123456789abcdefL+size*31+variant;
            var config=configuration(size,new float[]{0F,.5F,1F}[variant%3],false);
            var origin=new BlockPos(variant%2==0?15:-1,new int[]{-64,-63,-16,0,62,64}[variant%6],variant%2==0?0:15);
            world.denyWrites=variant%2==0;
            RandomSource original=variant<3?new LegacyRandomSource(seed):variant<6?new XoroshiroRandomSource(seed):new WorldgenRandom(new XoroshiroRandomSource(seed));
            RandomSource nativeRandom=variant<3?new LegacyRandomSource(seed):variant<6?new XoroshiroRandomSource(seed):new WorldgenRandom(new XoroshiroRandomSource(seed));
            world.reset();boolean expected=place(false,world,original,config,origin);long[] trace=world.trace.toLongArray();var expectedBlocks=world.writtenBlocks();
            world.reset();assertEquals(expected,place(true,world,nativeRandom,config,origin));
            assertArrayEquals(trace,world.trace.toLongArray(),"placement size="+size+" variant="+variant);
            assertEquals(expectedBlocks,world.writtenBlocks(),"Final block states");
            if(original instanceof WorldgenRandom a)assertEquals(a.getCount(),((WorldgenRandom)nativeRandom).getCount(),"Worldgen RNG draw counter");
            for(int i=0;i<16;i++)assertEquals(original.nextLong(),nativeRandom.nextLong(),"RNG state");events+=trace.length/2;
        }
        System.out.println("ORE_PLACEMENT_PARITY fixtures="+(65*9)+" events="+events);
    }
    @Test void heightmapRejectionPreservesRng() {
        var world=new OreTestWorld(-64,128,false,true);world.noHeight=true;
        var config=configuration(64,1,false);var a=new LegacyRandomSource(7);var b=new LegacyRandomSource(7);
        assertFalse(place(false,world,a,config,BlockPos.ZERO));
        assertFalse(place(true,world,b,config,BlockPos.ZERO));
        for(int i=0;i<16;i++)assertEquals(a.nextLong(),b.nextLong());
        assertTrue(world.trace.isEmpty());
    }
    @Test void randomCallbackReentryAndFailureReleaseLeases() {
        RandomSource nested = new LegacyRandomSource(901) {
            boolean first = true;
            @Override public double nextDouble() {
                if(first) {first=false;nativeVein(64,13,0,0,0);}
                return super.nextDouble();
            }
        };
        int[] actual;
        try(var spans=NativeOreGeometry.vein(nested,9,0,2,0,2,0,2,-10,-10,-10)) {
            actual=NativeOreGeometryBridge.expand(Arrays.copyOf(spans.values,spans.length));
            nativeVein(64,81,0,0,0);
            assertArrayEquals(actual,NativeOreGeometryBridge.expand(Arrays.copyOf(spans.values,spans.length)));
        }
        try(var spans=NativeOreGeometry.vein(new LegacyRandomSource(901),9,0,2,0,2,0,2,-10,-10,-10)) {
            assertArrayEquals(actual,NativeOreGeometryBridge.expand(Arrays.copyOf(spans.values,spans.length)));
        }
        var failure=new IllegalStateException("RNG failure");
        RandomSource broken=new LegacyRandomSource(0) {@Override public double nextDouble(){throw failure;}};
        assertSame(failure,assertThrows(IllegalStateException.class,()->NativeOreGeometry.vein(broken,4,0,0,0,0,0,0,0,0,0)));
        assertArrayEquals(JavaOreGeometry.candidates(spheres(9,13,0,0,0),-40,-40,-40),nativeVein(9,13,0,0,0));
    }
    @Test void adjacentRoundingThresholds() {
        var random=new Random(27091);
        for(int trial=0;trial<256;trial++) {
            double x=random.nextDouble()*6-3,y=random.nextDouble()*6-3,z=random.nextDouble()*6-3;
            double dx=.5-x,dy=.5-y,dz=.5-z,r=Math.sqrt(dx*dx+dy*dy+dz*dz);
            for(double radius:new double[]{Math.nextDown(r),r,Math.nextUp(r)}) {
                double[] input={x,y,z,radius};
                assertArrayEquals(JavaOreGeometry.candidates(input,-16,-16,-16),NativeOreGeometryBridge.candidates(input,-16,-16,-16));
            }
        }
    }
    @Test void concurrentAndNestedCalls() throws Exception {
        try(var executor=Executors.newFixedThreadPool(5)){
            var tasks=new ArrayList<Future<?>>();
            for(int t=0;t<4;t++){final int seed=t;tasks.add(executor.submit(()->{
                for(int i=0;i<100;i++){
                    int size=new int[]{4,9,17,64}[i%4];
                    var ds=spheres(size,seed*100+i,0,0,0);
                    assertArrayEquals(JavaOreGeometry.candidates(ds,-40,-40,-40),NativeOreGeometryBridge.candidates(ds,-40,-40,-40));
                    assertArrayEquals(JavaOreGeometry.candidates(ds,-40,-40,-40),nativeVein(size,seed*100+i,0,0,0));
                }
            }));}
            tasks.add(executor.submit(()->{for(int i=0;i<4;i++)System.gc();}));
            for(var task:tasks)task.get();
        }
        var world=new OreTestWorld(-64,128,false,true);
        var config=configuration(33,0,false);
        world.onWriteCheck=()->place(true,new OreTestWorld(-64,128,false,false),new LegacyRandomSource(15),config,BlockPos.ZERO);
        var random=new LegacyRandomSource(14);place(true,world,random,config,BlockPos.ZERO);long[] actual=world.trace.toLongArray();
        world.reset();place(false,world,new LegacyRandomSource(14),config,BlockPos.ZERO);assertArrayEquals(world.trace.toLongArray(),actual);
    }
}
