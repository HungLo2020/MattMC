package net.minecraft.world.level.levelgen;

import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeUnaryTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion();Bootstrap.bootStrap(); }
    static final class Input implements DensityFunction.SimpleFunction {
        double value;int reads;
        public double compute(FunctionContext c) { reads++;return value; }
        public double minValue(){return Double.NEGATIVE_INFINITY;}
        public double maxValue(){return Double.POSITIVE_INFINITY;}
        public KeyDispatchDataCodec<? extends DensityFunction> codec(){throw new UnsupportedOperationException();}
    }
    private static void same(double expected,double actual) {
        assertEquals(Double.doubleToLongBits(expected),Double.doubleToLongBits(actual));
    }
    @Test void fusedChainMatchesOriginalJavaArithmetic() {
        var input=new Input();
        var source=DensityFunctions.add(DensityFunctions.mul(input.abs().square().cube().halfNegative()
            .quarterNegative().invert().squeeze().clamp(-0.3,0.7),DensityFunctions.constant(-2)),DensityFunctions.constant(-0.));
        var compiled=NativeUnary.compile(source,new HashMap<>());
        assertInstanceOf(NativeUnary.class,compiled);
        var point=new DensityFunction.SinglePointContext(1,2,3);
        var random=new Random(7162);
        for(int i=0;i<100000;i++) {
            double v=i<8?new double[]{0.,-0.,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.MIN_VALUE,-Double.MIN_VALUE,1.}[i]
                : Double.longBitsToDouble(random.nextLong());
            input.value=v;int before=input.reads;
            double d=Math.abs(v);d=d*d;d=d*d*d;d=d>0?d:d*0.5;d=d>0?d:d*0.25;d=1./d;
            d=Mth.clamp(d,-1.,1.);d=d/2.-d*d*d/24.;d=Mth.clamp(d,-0.3,0.7);d=d*-2.;d=d+-0.;
            same(d,compiled.compute(point));assertEquals(before+1,input.reads);
        }
    }
    @Test void arrayTailsMatchScalarAndKeepProviderVisits() {
        var input=new Input();input.value=-0.25;
        var source=input.square().cube().halfNegative();
        var compiled=NativeUnary.compile(source,new HashMap<>());
        for(int count:new int[]{0,1,3,255,256,257,513}) {
            int[] visits={0};
            var provider=new DensityFunction.ContextProvider() {
                public DensityFunction.FunctionContext forIndex(int i) {
                    assertEquals(visits[0]++,i);return new DensityFunction.SinglePointContext(i,0,0);
                }
                public void fillAllDirectly(double[] values,DensityFunction f) {
                    for(int i=0;i<values.length;i++)values[i]=f.compute(forIndex(i));
                }
            };
            double[] values=new double[count];compiled.fillArray(values,provider);
            assertEquals(count,visits[0]);
            for(double value:values)same(0.000244140625,value);
        }
    }
    @Test void sharedProgramsDoNotRetainInputObjects() {
        var programs=new HashMap<List<NativeUnaryProgram.Step>,NativeUnaryProgram>();
        var a=new Input();a.value=2.;var b=new Input();b.value=3.;
        var first=NativeUnary.compile(a.square().cube(),programs);
        var second=NativeUnary.compile(b.square().cube(),programs);
        assertEquals(1,programs.size());
        var point=new DensityFunction.SinglePointContext(0,0,0);
        same(64.,first.compute(point));same(729.,second.compute(point));
    }
    @Test void longChainsPreserveOperationOrder() {
        var input=new Input();input.value=-8.;DensityFunction source=input;
        for(int i=0;i<40;i++)source=source.halfNegative();
        var compiled=NativeUnary.compile(source,new HashMap<>());
        double expected=-8.;for(int i=0;i<40;i++)expected=expected>0?expected:expected*0.5;
        same(expected,compiled.compute(new DensityFunction.SinglePointContext(0,0,0)));
        assertEquals(1,input.reads);
    }
    @Test void invalidProgramsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new NativeUnaryProgram(List.of()));
        assertThrows(IllegalArgumentException.class,()->new NativeUnaryProgram(List.of(new NativeUnaryProgram.Step(8,0,0))));
        assertThrows(IllegalArgumentException.class,()->new NativeUnaryProgram(Collections.nCopies(17,new NativeUnaryProgram.Step(15,0,0))));
    }
    @Test void constantMinMaxPreserveNanAndSignedZero() {
        var input=new Input();
        for(double limit:new double[]{-0.,0.,-0.5,0.5,Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY}) {
            var source=DensityFunctions.max(DensityFunctions.min(input,DensityFunctions.constant(limit)),DensityFunctions.constant(-0.)).square();
            var compiled=NativeUnary.compile(source,new HashMap<>());
            assertInstanceOf(NativeUnary.class,compiled);
            for(double v:new double[]{-0.,0.,-1.,1.,Double.MIN_VALUE,Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY}) {
                input.value=v;double a=Math.max(Math.min(v,limit),-0.);
                same(a*a,compiled.compute(new DensityFunction.SinglePointContext(0,0,0)));
            }
        }
    }
    @Test void wrapperPreservesSerialization() {
        var source=DensityFunctions.constant(-0.125).square().cube().halfNegative();
        var compiled=NativeUnary.compile(source,new HashMap<>());
        var ops=com.mojang.serialization.JsonOps.INSTANCE;
        assertEquals(DensityFunction.DIRECT_CODEC.encodeStart(ops,source).getOrThrow(),
            DensityFunction.DIRECT_CODEC.encodeStart(ops,compiled).getOrThrow());
    }
    @Test void equivalentWrappersKeepGraphSharing() {
        var input=new Input();
        var first=NativeUnary.compile(input.square().cube(),new HashMap<>());
        var second=NativeUnary.compile(input.square().cube(),new HashMap<>());
        assertNotSame(first,second);
        assertEquals(first,second);
        assertEquals(first.hashCode(),second.hashCode());
        assertEquals(DensityFunctions.interpolated(first),DensityFunctions.interpolated(second));
    }
    @Test void cacheKeepsOriginalGraphAndItsExistingHitRules() {
        var input=new Input();input.value=2.;
        var source=input.square().cube();
        var evaluator=NativeUnary.tree(source,new HashMap<>());
        var cache=new NoiseChunk.Cache2D(source,evaluator);
        assertSame(source,cache.wrapped());
        same(64.,cache.compute(new DensityFunction.SinglePointContext(0,0,0)));
        input.value=3.;
        same(64.,cache.compute(new DensityFunction.SinglePointContext(0,10,0)));
        same(729.,cache.compute(new DensityFunction.SinglePointContext(1,10,0)));
        assertEquals(2,input.reads);
    }
    @Test void conditionalConstantStepsPreserveProviderVisits() {
        var input=new Input();input.value=0.5;
        var source=DensityFunctions.max(DensityFunctions.min(input,DensityFunctions.constant(0.25)),DensityFunctions.constant(0.)).square();
        var compiled=NativeUnary.compile(source,new HashMap<>());
        var visits=new ArrayList<Integer>();
        var provider=new DensityFunction.ContextProvider() {
            public DensityFunction.FunctionContext forIndex(int i){visits.add(i);return new DensityFunction.SinglePointContext(i,0,0);}
            public void fillAllDirectly(double[] values,DensityFunction f){for(int i=0;i<values.length;i++)values[i]=f.compute(forIndex(i));}
        };
        double[] expected=new double[7],actual=new double[7];source.fillArray(expected,provider);
        var original=List.copyOf(visits);visits.clear();compiled.fillArray(actual,provider);
        assertEquals(original,visits);assertArrayEquals(expected,actual);
    }
}
