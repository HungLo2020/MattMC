package net.minecraft.world.level.levelgen;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRangeBatchTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    static DensityFunction.NoiseHolder noise(long seed,int octaves) {
        var parameters=new NormalNoise.NoiseParameters(-octaves,java.util.Collections.nCopies(octaves,1.));
        return new DensityFunction.NoiseHolder(Holder.direct(parameters),NormalNoise.create(new XoroshiroRandomSource(seed),parameters));
    }
    static DensityFunction.FunctionContext point(int i) {
        return new DensityFunction.SinglePointContext(i*31-100,i%257-128,i*13-57);
    }
    static final class Provider implements DensityBatch.Provider {
        int visits,last=-1;
        public DensityFunction.FunctionContext forIndex(int i){visits++;last=i;return point(i);}
        public void fillAllDirectly(double[] values,DensityFunction f) {
            for(int i=0;i<values.length;i++)values[i]=f.compute(forIndex(i));
        }
    }
    private static void verify(DensityFunction inside,DensityFunction outside) {
        var input=new DensityFunctions.YClampedGradient(-64,64,-1,1);
        for(double min:new double[]{-0.25,Double.NaN,Double.NEGATIVE_INFINITY}) {
            for(double max:new double[]{0.25,-0.,Double.NaN,Double.POSITIVE_INFINITY}) {
                var choice=new DensityFunctions.RangeChoice(input,min,max,inside,outside);
                for(int count:new int[]{0,1,3,4,5,49,128,255,256,257,513}) {
                    var provider=new Provider();double[] values=new double[count];
                    choice.fillArray(values,provider);
                    assertEquals(count*2,provider.visits);assertEquals(count-1,provider.last);
                    for(int i=0;i<count;i++) {
                        var context=point(i);double d=input.compute(context);
                        double expected=(d>=min && d<max?inside:outside).compute(context);
                        assertEquals(Double.doubleToLongBits(expected),Double.doubleToLongBits(values[i]));
                    }
                }
            }
        }
    }
    @Test void selectedNoiseStreamsAndConstantsMatchScalar() {
        var first=new DensityFunctions.Noise(noise(42,4),0.25,0.125);
        var second=new DensityFunctions.Noise(noise(-17,3),0.5,0.3);
        verify(first,second);verify(first,DensityFunctions.constant(-0.));
        verify(DensityFunctions.constant(Double.NaN),second);
    }
    @Test void shiftedAxesMatchScalar() {
        verify(new DensityFunctions.ShiftA(noise(13,4)),new DensityFunctions.ShiftB(noise(27,4)));
    }
    @Test void ordinaryFfmPathHonorsSelectedCount() {
        verify(new DensityFunctions.Noise(noise(19,33),0.25,0.5),DensityFunctions.zero());
    }
}
