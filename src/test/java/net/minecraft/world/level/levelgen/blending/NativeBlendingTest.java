package net.minecraft.world.level.levelgen.blending;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeBlendingTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    static long compare(BlendingFixtures.Grid grid) {
        int n = grid.size()*grid.size(); var a=new double[n+3]; var o=new double[n+3];
        var expectedA=new double[n+3];var expectedO=new double[n+3];
        Arrays.fill(a,7); Arrays.fill(o,9); Arrays.fill(expectedA,7); Arrays.fill(expectedO,9);
        BlendingFixtures.original(grid.original(),grid.x(),grid.z(),grid.size(),expectedA,expectedO);
        grid.production().fillBlendingOutputs(grid.x(),grid.z(),grid.size(),a,o);
        for(int i=0;i<n+3;i++) {
            assertEquals(Double.doubleToRawLongBits(expectedA[i]),Double.doubleToRawLongBits(a[i]),"alpha "+i+" "+grid);
            assertEquals(Double.doubleToRawLongBits(expectedO[i]),Double.doubleToRawLongBits(o[i]),"offset "+i+" "+grid);
        }
        return n;
    }
    @Test void completeSeededGridParity() {
        long points=0;int grids=0;
        for(var name:BlendingFixtures.names()) for(int seed=0;seed<128;seed++) {
            var grid=BlendingFixtures.grid(name,1977L+seed);points+=compare(grid);grids++;
            var a=new double[grid.size()*grid.size()];var o=a.clone();
            assertTrue(NativeBlending.fill(grid.production(),grid.data(),grid.x(),grid.z(),grid.size(),a,o),"Native bypass "+name);
        }
        System.out.println("BLENDING_GRID_PARITY grids="+grids+" points="+points);
    }
    @Test void exposedSeamMatchesOriginalColumnLayoutAndDirectPrecedence() {
        var grid=BlendingFixtures.grid("seam",42);int[] samples={0},direct={0};
        grid.data().forEach((key,value)->value.iterateHeights(ChunkPos.getX(key)*4,ChunkPos.getZ(key)*4,(x,z,h)->{
            assertEquals(0,x);assertTrue(z>=-28&&z<=31);samples[0]++;
        }));
        assertEquals(60,samples[0]);var blender=grid.production();
        for(int x=0;x<5;x++)for(int z=0;z<5;z++)if(blender.heightForNativeGrid(x,z)!=Double.MAX_VALUE)direct[0]++;
        assertEquals(5,direct[0]);compare(grid);
    }
    @Test void boundaryCoordinatesMissingDataAndFloatRounding() {
        long points=0;int grids=0;
        for(int base:new int[]{0,-1,1,-1_874_999,1_874_999,Integer.MIN_VALUE/4,Integer.MAX_VALUE/4})
            for(int seed=0;seed<24;seed++) {
                var map=new Long2ObjectOpenHashMap<BlendingData>();var random=new Random(seed);
                for(int dx=-3;dx<=1;dx++) for(int dz=-2;dz<=2;dz++) {
                    var h=new double[16];
                    for(int p=0;p<16;p++)h[p]=p%4==seed%4?Double.MAX_VALUE:
                        new double[]{-64,0,-.5,64,Math.nextUp(64.0),Math.nextDown(64.0),319,1024}[random.nextInt(8)];
                    map.put(ChunkPos.asLong(base+dx,base+dz),BlendingFixtures.data(h));
                }
                points+=compare(new BlendingFixtures.Grid(map,base*4+seed%8,base*4-seed%8,5));grids++;
            }
        // 27-quart cutoff and smoothing/positive modulo boundaries, ordered ties.
        for(int delta:new int[]{-32,-28,-27,-26,-1,0,1,26,27,28,32}) {
            var map=BlendingFixtures.grid("border",42).data();
            points+=compare(new BlendingFixtures.Grid(map,delta,delta,5));grids++;
        }
        System.out.println("BLENDING_EDGE_PARITY grids="+grids+" points="+points);
    }
    @Test void variedMapOrdersCancellationAndFiniteHeightBits() {
        long points=0;int eligible=0;
        var random=new Random(0x4d6174744d43L);
        for(int fixture=0;fixture<2048;fixture++) {
            var map=new Long2ObjectOpenHashMap<BlendingData>(1+random.nextInt(128));
            var keys=new ArrayList<Long>();
            for(int x=-4;x<0;x++)for(int z=-3;z<=3;z++)if(random.nextInt(4)!=0)keys.add(ChunkPos.asLong(x,z));
            Collections.shuffle(keys,random);
            for(long key:keys) {
                var h=new double[16];
                for(int i=0;i<16;i++) {
                    double v=switch(fixture%4) {
                        case 0 -> random.nextDouble()*384-64;
                        case 1 -> Math.scalb(random.nextDouble()*2-1,random.nextInt(1020)-510);
                        case 2 -> i%2==0?1.0e18:-1.0e18;
                        default -> Math.nextAfter((random.nextInt(384)-64)*.5,random.nextBoolean()?Double.POSITIVE_INFINITY:Double.NEGATIVE_INFINITY);
                    };
                    h[i]=random.nextInt(5)==0?Double.MAX_VALUE:v;
                }
                map.put(key,BlendingFixtures.data(h));
            }
            var grid=new BlendingFixtures.Grid(map,1+random.nextInt(32),random.nextInt(32)-16,fixture%8==0?9:5);
            points+=compare(grid);
            int n=grid.size()*grid.size();
            if(NativeBlending.fill(grid.production(),map,grid.x(),grid.z(),grid.size(),new double[n],new double[n]))eligible++;
        }
        assertTrue(eligible>1800,"Native coverage "+eligible);
        System.out.println("BLENDING_RANDOM_PARITY grids=2048 points="+points+" native_batches="+eligible);
    }
    @Test void currentInputsAreNotCached() {
        var grid=BlendingFixtures.grid("border",0);for(int iteration=0;iteration<16;iteration++) {
            compare(grid);
            var packed=grid.data().values().iterator().next().pack();var h=packed.heights().orElseThrow();
            Arrays.fill(h,iteration*8+.5);grid.data().put(grid.data().keySet().iterator().nextLong(),BlendingFixtures.data(h));
            grid.data().put(ChunkPos.asLong(-4,iteration%4),BlendingFixtures.data(h.clone()));
        }
        // BlendingData.unpack retains input arrays. Mutations must be visible.
        double[] shared=new double[16];Arrays.fill(shared,64);
        grid.data().put(ChunkPos.asLong(-1,0),BlendingFixtures.data(shared));
        for(double h:new double[]{-64,-.5,0,128,319}){Arrays.fill(shared,h);compare(grid);}
    }
    @Test void nonfiniteAndTinyCompatibility() {
        for(String name:BlendingFixtures.names()) {
            var g=BlendingFixtures.grid(name,0);
            for(int size:new int[]{1,2,3,4,5,9,17,18})compare(new BlendingFixtures.Grid(g.data(),g.x(),g.z(),size));
        }
        for(double v:new double[]{Double.NaN,Double.longBitsToDouble(0x7ff8123456789abcL),Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.MAX_VALUE,-Double.MAX_VALUE}) {
            var g=BlendingFixtures.grid("border",0);var h=new double[16];Arrays.fill(h,v);
            g.data().put(ChunkPos.asLong(-1,0),BlendingFixtures.data(h));compare(g);
        }
        compare(new BlendingFixtures.Grid(new Long2ObjectOpenHashMap<>(),0,0,5));
        var a=new double[25];var o=a.clone();Blender.empty().fillBlendingOutputs(0,0,5,a,o);
        for(int i=0;i<25;i++){assertEquals(1,a[i]);assertEquals(0,o[i]);}
    }
    @Test void customCallbackOrderExceptionAndPartialWrites() {
        var calls=new ArrayList<Long>();var a=new double[25];var o=a.clone();Arrays.fill(a,7);Arrays.fill(o,9);
        var custom=new Blender(new Long2ObjectOpenHashMap<>(),new Long2ObjectOpenHashMap<>()) {
            @Override public BlendingOutput blendOffsetAndFactor(int x,int z) {
                calls.add(ChunkPos.asLong(x,z));
                if(calls.size()==13)throw new IllegalStateException("stop");
                return new BlendingOutput(x,z);
            }
        };
        assertThrows(IllegalStateException.class,()->custom.fillBlendingOutputs(-2,-3,5,a,o));
        int at=0;for(int x=0;x<5;x++)for(int z=0;z<5;z++,at++) {
            int index=x+z*5;
            if(at<13)assertEquals(ChunkPos.asLong((-2+x)*4,(-3+z)*4),calls.get(at));
            assertEquals(at<12?(-2+x)*4:7,a[index]);assertEquals(at<12?(-3+z)*4:9,o[index]);
        }
        assertThrows(IllegalArgumentException.class,()->custom.fillBlendingOutputs(0,0,5,a,a));
        assertThrows(IllegalArgumentException.class,()->custom.fillBlendingOutputs(0,0,Integer.MAX_VALUE,a,o));
        // Successful native calls after exceptions reuse a released lease.
        compare(BlendingFixtures.grid("large",73));
    }
    @Test void concurrentCallsAndGc() throws Exception {
        try(var pool=Executors.newFixedThreadPool(4)) {
            var tasks=new ArrayList<Callable<Long>>();
            for(int thread=0;thread<4;thread++){int seed=thread;tasks.add(()->{long points=0;for(int i=0;i<64;i++){points+=compare(BlendingFixtures.grid("border",seed*64+i));if(i%16==0)System.gc();}return points;});}
            long points=0;for(var result:pool.invokeAll(tasks))points+=result.get();assertEquals(6400,points);
        }
    }

    @Test void malformedLayoutsRetainOriginalExceptionAndPartialArrays() {
        for(int length:new int[]{0,1,7,15,17,32}) {
            var map=BlendingFixtures.grid("border",11).data();
            var h=new double[length];Arrays.fill(h,64);
            map.put(ChunkPos.asLong(0,0),BlendingFixtures.data(h));
            var a=new double[25];var o=a.clone();var ea=a.clone();var eo=a.clone();
            Arrays.fill(a,7);Arrays.fill(ea,7);Arrays.fill(o,9);Arrays.fill(eo,9);
            Throwable expected=null,actual=null;
            try{BlendingFixtures.original(new JavaBlender(map,new Long2ObjectOpenHashMap<>()),0,0,5,ea,eo);}catch(Throwable t){expected=t;}
            try{new Blender(map,new Long2ObjectOpenHashMap<>()).fillBlendingOutputs(0,0,5,a,o);}catch(Throwable t){actual=t;}
            assertEquals(expected==null?null:expected.getClass(),actual==null?null:actual.getClass());
            assertArrayEquals(ea,a);assertArrayEquals(eo,o);
        }
        // Custom maps must retain their original per-query virtual forEach calls.
        var calls=new int[]{0};
        var custom=new Long2ObjectOpenHashMap<BlendingData>() {
            @Override public void forEach(java.util.function.BiConsumer<? super Long,? super BlendingData> action) {
                calls[0]++;super.forEach(action);
            }
        };
        custom.putAll(BlendingFixtures.grid("border",42).data());
        var a=new double[25];var o=a.clone();
        BlendingFixtures.original(new JavaBlender(custom,new Long2ObjectOpenHashMap<>()),4,-2,5,a,o);
        int expected=calls[0];calls[0]=0;
        new Blender(custom,new Long2ObjectOpenHashMap<>()).fillBlendingOutputs(4,-2,5,a,o);
        assertEquals(expected,calls[0]);assertEquals(25,expected);
    }

    @Test void ffiRejectsInvalidMetadataWithoutWriting() throws Throwable {
        var handle=net.minecraft.util.NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_blending_heights",
            java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_INT,
                java.lang.foreign.ValueLayout.ADDRESS,java.lang.foreign.ValueLayout.ADDRESS,java.lang.foreign.ValueLayout.JAVA_INT,
                java.lang.foreign.ValueLayout.ADDRESS,java.lang.foreign.ValueLayout.ADDRESS,java.lang.foreign.ValueLayout.JAVA_INT,
                java.lang.foreign.ValueLayout.ADDRESS,java.lang.foreign.ValueLayout.JAVA_INT));
        try(var arena=java.lang.foreign.Arena.ofConfined()) {
            var c=arena.allocate(8,4);var h=arena.allocate(8,8);var q=arena.allocate(8,4);
            var d=arena.allocate(8,8);var out=arena.allocate(16,8);
            h.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE,0,64);d.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE,0,Double.MAX_VALUE);
            q.set(java.lang.foreign.ValueLayout.JAVA_INT,0,1);q.set(java.lang.foreign.ValueLayout.JAVA_INT,4,1);
            for(int count:new int[]{-1,0,4097}) {
                out.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE,0,7);
                int status=(int)handle.invokeExact(c,h,count,q,d,1,out,2);
                assertEquals(-1,status);assertEquals(7,out.get(java.lang.foreign.ValueLayout.JAVA_DOUBLE,0));
            }
            int status=(int)handle.invokeExact(c,h,1,q,d,1,out,2);assertEquals(0,status);
            q.set(java.lang.foreign.ValueLayout.JAVA_INT,0,0);q.set(java.lang.foreign.ValueLayout.JAVA_INT,4,0);
            status=(int)handle.invokeExact(c,h,1,q,d,1,out,2);assertEquals(1,status);
        }
    }
}
