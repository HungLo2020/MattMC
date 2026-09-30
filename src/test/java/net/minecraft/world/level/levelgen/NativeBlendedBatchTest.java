package net.minecraft.world.level.levelgen;

import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeBlendedBatchTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void vectorBatchesMatchPreviouslyVerifiedScalarNoise() {
        var random = new Random(821765);
        double[][] parameters = {{.25,.125,80.,160.,8.}, {.001,.001,.001,.001,1.},
                {1000.,1000.,1000.,1000.,8.}, {1.,.01,.001,1000.,1.},
                {.5,1.,1.,1.,8.}, {0.,.125,80.,160.,8.}, {Double.NaN,.125,80.,160.,8.}};
        for (long seed : new long[]{0,42,-76431,Long.MIN_VALUE,Long.MAX_VALUE}) {
            for (double[] p : parameters) {
                var noise = new BlendedNoise(new XoroshiroRandomSource(seed),p[0],p[1],p[2],p[3],p[4]);
                for (int count : new int[]{0,1,3,4,17,65,255,256,257,513}) {
                    double[] xyz = new double[count*3], values = new double[count];
                    for (int i=0;i<count;i++) {
                        int x = random.nextInt(60_000_001)-30_000_000;
                        int y = random.nextInt(4097)-2048;
                        int z = random.nextInt(60_000_001)-30_000_000;
                        if (i%19==0) x=Integer.MIN_VALUE;
                        if (i%23==0) z=Integer.MAX_VALUE;
                        xyz[i*3]=x;xyz[i*3+1]=y;xyz[i*3+2]=z;
                    }
                    noise.fillNative(xyz,values);
                    for (int i=0;i<count;i++) {
                        double expected=noise.compute(new DensityFunction.SinglePointContext((int)xyz[i*3],(int)xyz[i*3+1],(int)xyz[i*3+2]));
                        assertEquals(Double.doubleToLongBits(expected),Double.doubleToLongBits(values[i]),
                                "seed="+seed+" size="+count+" index="+i+" parameters="+java.util.Arrays.toString(p));
                    }
                }
            }
        }
    }

    @Test void terrainAdapterPreservesCoordinateAccessorOrderAndRejectsOverrides() {
        var noise=new BlendedNoise(new LegacyRandomSource(71),.25,.125,80.,160.,8.);
        var batch=new DensityBatch();assertTrue(batch.prepare(noise,17));
        var visits=new StringBuilder();
        for (int i=0;i<17;i++) {
            final int y=i*8-64;
            batch.record(i,new DensityFunction.FunctionContext() {
                public int blockX(){visits.append('x');return 12;}
                public int blockY(){visits.append('y');return y;}
                public int blockZ(){visits.append('z');return -16;}
            });
        }
        double[] values=new double[17];batch.finish(values);
        assertEquals("xyz".repeat(17),visits.toString());
        for(int i=0;i<17;i++)assertEquals(Double.doubleToLongBits(noise.compute(new DensityFunction.SinglePointContext(12,i*8-64,-16))),Double.doubleToLongBits(values[i]));
        var override=new BlendedNoise(new LegacyRandomSource(71),.25,.125,80.,160.,8.) {
            @Override public double compute(DensityFunction.FunctionContext context){return 42.;}
        };
        assertFalse(batch.prepare(override,17));
    }

    @Test void invalidBuffersAreRejected() {
        var noise=BlendedNoise.createUnseeded(.25,.125,80.,160.,8.);
        assertThrows(IllegalArgumentException.class,()->noise.fillNative(new double[11],new double[4]));
        var same=new double[12];assertThrows(IllegalArgumentException.class,()->noise.fillNative(same,same));
    }
    @Test void prefixFillsKeepTheUnusedTail() {
        var noise=BlendedNoise.createUnseeded(.25,.125,80.,160.,8.);
        double[] xyz=new double[17*3],values=new double[21];
        java.util.Arrays.fill(values,37.25);
        for(int i=0;i<17;i++){xyz[i*3]=i;xyz[i*3+1]=i*4-16;xyz[i*3+2]=-i;}
        noise.fillNative(xyz,values,17);
        for(int i=0;i<17;i++)assertEquals(Double.doubleToLongBits(noise.compute(new DensityFunction.SinglePointContext(i,i*4-16,-i))),Double.doubleToLongBits(values[i]));
        for(int i=17;i<21;i++)assertEquals(37.25,values[i]);
        assertThrows(IllegalArgumentException.class,()->noise.fillNative(xyz,values,-1));
        assertThrows(IllegalArgumentException.class,()->noise.fillNative(xyz,values,22));
    }
}
