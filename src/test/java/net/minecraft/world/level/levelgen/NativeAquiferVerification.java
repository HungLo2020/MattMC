package net.minecraft.world.level.levelgen;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Opt-in differential corpus. VerifyRustAquifer.py provides the unchanged
 * original algorithm from Git under a renamed class in ignored build output. */
public final class NativeAquiferVerification {
    private static Constructor<?> oracle;
    private static BlockState unregistered;
    private static final double[] SPECIAL = {0.0,-0.0,Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,
        -Double.MIN_VALUE,-0.01,-0.1,-0.5,-1,-2,-4,1};
    private static final class Fixture {
        final List<String> trace = new ArrayList<>();
        final NoiseChunk noise = mock(NoiseChunk.class);
        final NoiseRouter router;
        final PositionalRandomFactory random;
        final Aquifer.FluidPicker picker;
        Aquifer instance;
        boolean nested;
        Fixture(long seed, int scenario, boolean legacy, boolean observedRandom) {
            when(noise.maxPreliminarySurfaceLevel(anyInt(),anyInt(),anyInt(),anyInt())).thenReturn(320);
            when(noise.preliminarySurfaceLevel(anyInt(),anyInt())).thenAnswer(call -> {
                int x=call.getArgument(0),z=call.getArgument(1);trace.add("surface:"+x+":"+z);
                return scenario%3==0 ? -64 : scenario%3==1 ? 80 : Math.floorMod(x*31+z*17,384)-64;
            });
            var base = legacy ? new LegacyRandomSource(seed).forkPositional() : new XoroshiroRandomSource(seed).forkPositional();
            random = !observedRandom ? base : new PositionalRandomFactory() {
                public RandomSource at(int x,int y,int z) { trace.add("random:"+x+":"+y+":"+z);return base.at(x,y,z); }
                public RandomSource fromHashOf(String value){return base.fromHashOf(value);}
                public RandomSource fromSeed(long value){return base.fromSeed(value);}
                public void parityConfigString(StringBuilder out){base.parityConfigString(out);}
            };
            var zero=DensityFunctions.zero();
            router=new NoiseRouter(function("barrier",scenario),function("flood",scenario),function("spread",scenario),function("lava",scenario),
                zero,zero,zero,function("erosion",scenario),function("depth",scenario),zero,zero,zero,zero,zero,zero);
            picker=(x,y,z)-> {
                trace.add("fluid:"+x+":"+y+":"+z);
                int variant=Math.floorMod((x>>4)*3+(z>>4)*7+scenario,7);
                BlockState state=switch(variant) {case 0->Blocks.AIR.defaultBlockState();case 1->Blocks.LAVA.defaultBlockState();
                    case 2->unregistered;default->Blocks.WATER.defaultBlockState();};
                int level=switch(scenario%4) {case 0->63;case 1->-54;case 2->Math.floorMod(x*7+z*11,200)-100;default->y< -54?-54:63;};
                if(scenario==9)level=(x&1)==0?Integer.MAX_VALUE:Integer.MIN_VALUE;
                if(scenario==10)level=(z&1)==0?Integer.MIN_VALUE:Integer.MAX_VALUE;
                return new Aquifer.FluidStatus(level,state);
            };
        }
        DensityFunction function(String name,int scenario) {
            return new DensityFunction.SimpleFunction() {
                public double compute(DensityFunction.FunctionContext c) {
                    int x=c.blockX(),y=c.blockY(),z=c.blockZ();trace.add(name+":"+x+":"+y+":"+z);
                    if(name.equals("barrier") && scenario==6 && !nested && instance!=null) {
                        nested=true;
                        try {
                            var result=instance.computeSubstance(new DensityFunction.SinglePointContext(x+(Math.floorMod(x,16)==15?-1:1),y,z),-4);
                            trace.add("nested:"+result+":"+instance.shouldScheduleFluidUpdate());
                        } finally {nested=false;}
                    }
                    int h=x*734287+y*912931+z*438289+scenario*7919;
                    if(scenario>=8 && scenario<=11)return switch(scenario) {
                        case 8->Double.NaN;case 9->Double.POSITIVE_INFINITY;case 10->Double.NEGATIVE_INFINITY;default->-0.0;
                    };
                    if(scenario>=12 && name.equals("spread"))return scenario==12?Double.POSITIVE_INFINITY:Double.NEGATIVE_INFINITY;
                    if(name.equals("barrier") && scenario==7)return Double.NaN;
                    return switch(name) {
                        case "depth" -> Math.floorMod(h,800)/100.0;
                        case "erosion" -> Math.floorMod(h,200)/100.0-1;
                        default -> Math.floorMod(h,400)/100.0-2;
                    };
                }
                public double minValue(){return Double.NEGATIVE_INFINITY;}
                public double maxValue(){return Double.POSITIVE_INFINITY;}
                public KeyDispatchDataCodec<? extends DensityFunction> codec(){throw new UnsupportedOperationException();}
            };
        }
        Aquifer create(boolean reference,ChunkPos pos) throws Exception {
            return instance=reference ? (Aquifer)oracle.newInstance(noise,pos,router,random,-64,384,picker)
                : Aquifer.create(noise,pos,router,random,-64,384,picker);
        }
    }
    private static final class ObservedContext implements DensityFunction.FunctionContext {
        final int x,y,z;final List<String> trace;int reads;
        ObservedContext(int x,int y,int z,List<String> trace) {this.x=x;this.y=y;this.z=z;this.trace=trace;}
        public int blockX(){trace.add("contextX");return x;}
        public int blockY(){trace.add("contextY");return y+(reads++==0?0:1);}
        public int blockZ(){trace.add("contextZ");return z;}
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("net.bytebuddy.experimental","true");
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        unregistered=mock(BlockState.class);
        oracle=Class.forName("net.minecraft.world.level.levelgen.JavaAquiferReference").getDeclaredConstructors()[0];oracle.setAccessible(true);
        long count=0;Random points=new Random(128734);
        for(long seed:new long[]{0,42,-123456789,Long.MIN_VALUE,Long.MAX_VALUE})for(boolean legacy:new boolean[]{false,true})
            for(int scenario=0;scenario<14;scenario++)for(int[] position:new int[][]{{0,0},{-1,-1},{1874998,-1874998}}) {
                Fixture a=new Fixture(seed,scenario,legacy,true),b=new Fixture(seed,scenario,legacy,true);
                ChunkPos pos=new ChunkPos(position[0],position[1]);
                Aquifer java=a.create(true,pos),rust=b.create(false,pos);
                for(int i=0;i<6000;i++) {
                    int x=pos.getMinBlockX()+points.nextInt(16),y=-64+points.nextInt(384),z=pos.getMinBlockZ()+points.nextInt(16);
                    double density=i%3==0?SPECIAL[i%SPECIAL.length]:points.nextDouble()*-4;
                    var context=new DensityFunction.SinglePointContext(x,y,z);
                    DensityFunction.FunctionContext javaContext=i%29==0?new ObservedContext(x,y,z,a.trace):context;
                    DensityFunction.FunctionContext rustContext=i%29==0?new ObservedContext(x,y,z,b.trace):context;
                    BlockState expected=java.computeSubstance(javaContext,density),actual=rust.computeSubstance(rustContext,density);
                    if(expected!=actual || java.shouldScheduleFluidUpdate()!=rust.shouldScheduleFluidUpdate())
                        throw new AssertionError("Aquifer mismatch seed="+seed+" legacy="+legacy+" scenario="+scenario+" at="+context+" density="+density+" expected="+expected+" actual="+actual);
                    if(!a.trace.equals(b.trace))throw new AssertionError("Callback order mismatch at "+context+"\nJava: "+a.trace+"\nRust: "+b.trace);
                    a.trace.clear();b.trace.clear();count++;
                }
            }
        System.out.println("AQUIFER_PARITY decisions="+count+" exact_states=true exact_updates=true exact_callback_traces=true");
    }
}
