package net.minecraft.world.level.levelgen;

import java.util.*;
import net.minecraft.util.BoundedFloatFunction;
import net.minecraft.util.CubicSpline;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeSplineKernelTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    private record Axis(int index) implements BoundedFloatFunction<float[]> {
        public float apply(float[] p) { return p[index]; }
        public float minValue() { return Float.NEGATIVE_INFINITY; }
        public float maxValue() { return Float.POSITIVE_INFINITY; }
    }
    private record Built(CubicSpline<float[],Axis> javaSpline,int node) {}
    private static Built tree(Random rng,int depth,List<NativeSplineProgram.Node> ns,List<NativeSplineProgram.Knot> ks) {
        if(depth==0 || rng.nextInt(4)==0) {
            float value=rng.nextBoolean()?rng.nextFloat()*8-4:Float.intBitsToFloat(rng.nextInt());
            int id=ns.size();ns.add(new NativeSplineProgram.Node(-1,0,0,value));
            return new Built(new CubicSpline.Constant<>(value),id);
        }
        int count=1+rng.nextInt(6),axis=rng.nextInt(4);float[] locations=new float[count],derivatives=new float[count];
        List<CubicSpline<float[],Axis>> children=new ArrayList<>();int[] ids=new int[count];
        float location=-2;
        for(int i=0;i<count;i++) {
            locations[i]=location;location+=rng.nextFloat();derivatives[i]=rng.nextBoolean()?rng.nextFloat()*10-5:0;
            var child=tree(rng,depth-1,ns,ks);children.add(child.javaSpline);ids[i]=child.node;
        }
        int start=ks.size();for(int i=0;i<count;i++)ks.add(new NativeSplineProgram.Knot(locations[i],derivatives[i],ids[i]));
        int id=ns.size();ns.add(new NativeSplineProgram.Node(axis,start,count,0));
        return new Built(new CubicSpline.Multipoint<>(new Axis(axis),locations,children,derivatives,Float.NEGATIVE_INFINITY,Float.POSITIVE_INFINITY),id);
    }
    @Test void matchesOriginalJavaForNestedTreesAndExtremeFloats() {
        Random rng=new Random(1937);float[] special={0,-0f,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.MIN_VALUE,-Float.MIN_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE,-2,Math.nextUp(-2f),Math.nextDown(-2f)};
        for(int t=0;t<150;t++) {
            var ns=new ArrayList<NativeSplineProgram.Node>();var ks=new ArrayList<NativeSplineProgram.Knot>();
            var source=tree(rng,4,ns,ks);var program=new NativeSplineProgram(ns,ks);
            for(int i=0;i<4000;i++) {
                float[] inputs=new float[4];for(int d=0;d<4;d++) inputs[d]=i<special.length?special[i]:i%2==0?rng.nextFloat()*8-4:Float.intBitsToFloat(rng.nextInt());
                float expected=source.javaSpline.apply(inputs),actual=program.sample(inputs[0],inputs[1],inputs[2],inputs[3]);
                assertEquals(Float.floatToIntBits(expected),Float.floatToIntBits(actual),"tree="+t+" input="+Arrays.toString(inputs));
            }
        }
    }
    @Test void rejectsInvalidPrograms() {
        assertThrows(IllegalArgumentException.class,()->new NativeSplineProgram(List.of(),List.of()));
        assertThrows(IllegalArgumentException.class,()->new NativeSplineProgram(List.of(new NativeSplineProgram.Node(0,0,1,0)),List.of(new NativeSplineProgram.Knot(0,0,0))));
        assertThrows(IllegalArgumentException.class,()->new NativeSplineProgram(List.of(new NativeSplineProgram.Node(9,0,1,0)),List.of()));
    }
    @Test void customCoordinateCallsRemainLazyAndOrdered() {
        var calls=new ArrayList<String>();
        DensityFunction fn=new DensityFunction.SimpleFunction() {
            public double compute(FunctionContext c){calls.add("coordinate");return c.blockY();}
            public double minValue(){return -100;}public double maxValue(){return 100;}
            public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec(){throw new UnsupportedOperationException();}
        };
        var coord=new DensityFunctions.Spline.Coordinate(net.minecraft.core.Holder.direct(fn));
        var spline=CubicSpline.<DensityFunctions.Spline.Point,DensityFunctions.Spline.Coordinate>builder(coord).addPoint(-1,2).addPoint(0,4).addPoint(1,6).build();
        var source=new DensityFunctions.Spline(spline);var nativeFunction=NativeSpline.compile(source);
        for(int y:new int[]{-10,-1,0,1,10}) {
            var c=new DensityFunction.SinglePointContext(0,y,0);calls.clear();double expected=source.compute(c);var expectedCalls=List.copyOf(calls);calls.clear();
            assertEquals(expected,nativeFunction.compute(c));assertEquals(expectedCalls,calls);
        }
    }
    @Test void statefulVisitorsCanSplitPreviouslySharedCoordinates() {
        var coordinate=new DensityFunctions.Spline.Coordinate(net.minecraft.core.Holder.direct(DensityFunctions.constant(0)));
        var nested=CubicSpline.<DensityFunctions.Spline.Point,DensityFunctions.Spline.Coordinate>builder(coordinate).addPoint(-1,2).addPoint(1,8).build();
        var root=CubicSpline.<DensityFunctions.Spline.Point,DensityFunctions.Spline.Coordinate>builder(coordinate).addPoint(-1,nested).addPoint(1,nested).build();
        var source=new DensityFunctions.Spline(root);
        var seq=new java.util.concurrent.atomic.AtomicInteger();
        var mapped=(DensityFunctions.Spline)source.mapAll(f -> f instanceof DensityFunctions.Constant ? DensityFunctions.constant(seq.getAndIncrement()-1) : f);
        var compiled=NativeSpline.compile(mapped);
        var point=new DensityFunction.SinglePointContext(0,0,0);
        assertEquals(Double.doubleToLongBits(mapped.compute(point)),Double.doubleToLongBits(compiled.compute(point)));
    }
    @Test void fusedCoordinateTransformsRoundOnlyAtTheOriginalFloatCast() {
        var program=new NativeSplineProgram(List.of(new NativeSplineProgram.Node(-1,0,0,-2),new NativeSplineProgram.Node(-1,0,0,7),new NativeSplineProgram.Node(0,0,2,0)),
            List.of(new NativeSplineProgram.Knot(-1,3,0),new NativeSplineProgram.Knot(1,-2,1)));
        var rng=new Random(923);
        for(int op=12;op<=21;op++) {
            var steps=List.of(new NativeUnaryProgram.Step(op,.7,1.3));
            var coordinates=program.coordinates(List.of(steps));
            for(int i=0;i<10000;i++) {
                double x=i%2==0?rng.nextDouble()*8-4:Double.longBitsToDouble(rng.nextLong());
                double transformed = switch(op) {
                    case 12 -> x * .7;
                    case 13 -> x + .7;
                    case 14 -> Math.abs(x);
                    case 15 -> x * x;
                    case 16 -> x * x * x;
                    case 17 -> x > 0 ? x : x * .5;
                    case 18 -> x > 0 ? x : x * .25;
                    case 19 -> 1.0 / x;
                    case 20 -> { double v=net.minecraft.util.Mth.clamp(x,-1.,1.); yield v/2.-v*v*v/24.; }
                    case 21 -> net.minecraft.util.Mth.clamp(x,.7,1.3);
                    default -> throw new AssertionError(op);
                };
                float expected=program.sample((float)transformed,0,0,0);
                assertEquals(Float.floatToIntBits(expected),Float.floatToIntBits(program.cached(coordinates,x,0,0,0)));
            }
        }
    }
    @Test void sharedProgramSupportsConcurrentReaders() throws Exception {
        var ns=new ArrayList<NativeSplineProgram.Node>();var ks=new ArrayList<NativeSplineProgram.Knot>();
        var source=tree(new Random(42),4,ns,ks);var program=new NativeSplineProgram(ns,ks);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var tasks=new ArrayList<java.util.concurrent.Future<?>>();
            for(int worker=0;worker<4;worker++) {
                final int seed=worker;
                tasks.add(executor.submit(()->{
                    var rng=new Random(seed);
                    for(int i=0;i<50000;i++) {
                        float[] p={rng.nextFloat()*8-4,rng.nextFloat()*8-4,rng.nextFloat()*8-4,rng.nextFloat()*8-4};
                        assertEquals(Float.floatToIntBits(source.javaSpline.apply(p)),Float.floatToIntBits(program.sample(p[0],p[1],p[2],p[3])));
                    }
                }));
            }
            for(var task:tasks)task.get();
        }
    }
    @Test void largeSharedGraphsUseTheOrdinaryBoundaryAndBoundExpandedWork() {
        var ns=new ArrayList<NativeSplineProgram.Node>();var ks=new ArrayList<NativeSplineProgram.Knot>();
        ns.add(new NativeSplineProgram.Node(-1,0,0,2));
        CubicSpline<float[],Axis> source=new CubicSpline.Constant<>(2);
        for(int level=1;level<=9;level++) {
            int start=ks.size();
            ks.add(new NativeSplineProgram.Knot(-1,3,level-1));ks.add(new NativeSplineProgram.Knot(1,-2,level-1));
            ns.add(new NativeSplineProgram.Node(0,start,2,0));
            source=new CubicSpline.Multipoint<>(new Axis(0),new float[]{-1,1},List.of(source,source),new float[]{3,-2},-100,100);
        }
        var program=new NativeSplineProgram(ns,ks);
        var coordinates=program.coordinates(List.of());
        for(int i=0;i<1000;i++) {
            float x=(i-500)/256f;
            int expected=Float.floatToIntBits(source.apply(new float[]{x}));
            assertEquals(expected,Float.floatToIntBits(program.sample(x,0,0,0)));
            assertEquals(expected,Float.floatToIntBits(program.cached(coordinates,x,0,0,0)));
        }
        assertThrows(IllegalArgumentException.class,()->program.coordinates(Collections.nCopies(5,List.of())));
        for(int level=10;level<=12;level++) {
            int start=ks.size();
            ks.add(new NativeSplineProgram.Knot(-1,3,level-1));ks.add(new NativeSplineProgram.Knot(1,-2,level-1));
            ns.add(new NativeSplineProgram.Node(0,start,2,0));
        }
        assertThrows(IllegalArgumentException.class,()->new NativeSplineProgram(ns,ks));
    }
    @Test void customProviderReceivesOriginalFunctionAndOriginalVisits() {
        var c=new DensityFunctions.Spline.Coordinate(net.minecraft.core.Holder.direct(DensityFunctions.yClampedGradient(-10,10,-1,1)));
        var curve=CubicSpline.<DensityFunctions.Spline.Point,DensityFunctions.Spline.Coordinate>builder(c).addPoint(-1,2).addPoint(0,4).addPoint(1,8).build();
        var source=new DensityFunctions.Spline(curve);var compiled=NativeSpline.compile(source);
        var provider=new DensityFunction.ContextProvider() {
            public DensityFunction.FunctionContext forIndex(int i) {return new DensityFunction.SinglePointContext(0,i,0);}
            public void fillAllDirectly(double[] a,DensityFunction f) {assertSame(source,f);for(int i=0;i<a.length;i++)a[i]=f.compute(forIndex(i));}
        };
        double[] expected=new double[32],actual=new double[32];source.fillArray(expected,provider);compiled.fillArray(actual,provider);assertArrayEquals(expected,actual);
    }
}
