package net.minecraft.world.level.levelgen;

import java.util.HashMap;
import java.util.Random;
import java.util.function.DoubleSupplier;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.synth.NativeDensityProgram;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeOperandsTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    record Input(DoubleSupplier value) implements DensityFunction.SimpleFunction {
        public double compute(FunctionContext c){return value.getAsDouble();}
        public double minValue(){return Double.NEGATIVE_INFINITY;}
        public double maxValue(){return Double.POSITIVE_INFINITY;}
        public KeyDispatchDataCodec<? extends DensityFunction> codec(){throw new UnsupportedOperationException();}
    }
    @Test void mandatoryOperandsKeepOrderAndExactArithmetic() {
        StringBuilder trace=new StringBuilder();double[] values=new double[2];
        var a=new Input(()->{trace.append('a');return values[0];});
        var b=new Input(()->{trace.append('b');return values[1];});
        var source=DensityFunctions.add(a.square().cube(),b.square().cube()).halfNegative();
        var compiled=NativeOperands.compile(source,new HashMap<>());
        assertInstanceOf(NativeOperands.class,compiled);
        var point=new DensityFunction.SinglePointContext(0,0,0);var random=new Random(194672);
        for(int i=0;i<100000;i++) {
            values[0]=Double.longBitsToDouble(random.nextLong());values[1]=Double.longBitsToDouble(random.nextLong());
            double x=values[0]*values[0],y=values[1]*values[1];
            double sum=x*x*x+y*y*y;double expected=sum>0?sum:sum*0.5;
            trace.setLength(0);
            assertEquals(Double.doubleToLongBits(expected),Double.doubleToLongBits(compiled.compute(point)));
            assertEquals("ab",trace.toString());
        }
    }
    @Test void conditionalChildrenAreNeverGatheredEagerly() {
        StringBuilder trace=new StringBuilder();
        var zero=new Input(()->{trace.append('a');return 0;});
        var fail=new Input(()->{throw new AssertionError("Skipped input was evaluated");});
        var other=new Input(()->{trace.append('b');return 2.;});
        var branch=DensityFunctions.mul(zero,fail);
        var source=DensityFunctions.add(branch.square().cube(),other.square().cube());
        var compiled=NativeOperands.compile(source,new HashMap<>());
        assertInstanceOf(NativeOperands.class,compiled);
        assertEquals(64.,compiled.compute(new DensityFunction.SinglePointContext(0,0,0)));
        assertEquals("ab",trace.toString());
    }
    @Test void reentrantProgramsKeepOuterOperands() {
        var a=new Input(()->2.);var b=new Input(()->3.);
        var inner=NativeOperands.compile(DensityFunctions.add(a.square().cube(),b.square().cube()),new HashMap<>());
        var point=new DensityFunction.SinglePointContext(0,0,0);
        var reentrant=new Input(()->inner.compute(point));
        var outer=NativeOperands.compile(DensityFunctions.add(a.square().cube(),reentrant.square().cube()),new HashMap<>());
        double v=793.,square=v*v;double expected=64.+square*square*square;
        assertEquals(Double.doubleToLongBits(expected),Double.doubleToLongBits(outer.compute(point)));
    }
    @Test void operandIndexesAreValidated() {
        var program=new NativeDensityProgram(new NativeDensityProgram.Node[]{
            new NativeDensityProgram.Node(23,4,0,0,-1,0,0,0)
        },new NormalNoise[0],4);
        assertThrows(IllegalArgumentException.class,()->program.operands(1,2,3,4));
    }
}
