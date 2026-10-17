package net.minecraft.world.level.levelgen;

import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import org.objenesis.ObjenesisStd;

/** Original public aquifer effects; no copied aquifer or random algorithm. */
public final class AquiferFactoryEffects {
    static final Exception MARKER = new Exception("original checked aquifer factory failure");
    @SuppressWarnings("unchecked") static <T extends Throwable> RuntimeException raise(Throwable failure) throws T { throw (T) failure; }
    // Only surface/context resources are supplied by this fixture. Actual
    // Aquifer construction, caching, callback order and retry are production.
    static final class Chunk extends NoiseChunk {
        private Chunk() { super(1,null,0,0,null,null,null,null,null); }
        @Override public int maxPreliminarySurfaceLevel(int x,int z,int xx,int zz) { return 0; }
        @Override public int preliminarySurfaceLevel(int x,int z) { return 0; }
    }
    static final class Factory implements PositionalRandomFactory {
        final List<String> calls = new ArrayList<>();
        final String mode;
        int positions, draws;
        boolean reject = true;
        Runnable callback;
        Factory(String mode) { this.mode=mode; }
        @Override public RandomSource at(int x,int y,int z) {
            calls.add("at:"+x+":"+y+":"+z);positions++;
            Runnable action=callback;callback=null;if(action!=null)action.run();
            if(reject&&mode.equals("at-fail")&&positions==4)throw raise(MARKER);
            return new LegacyRandomSource(42) {
                @Override public int nextInt(int bound) {
                    calls.add("draw:"+bound);draws++;
                    if(reject&&mode.equals("draw-fail")&&draws==7)throw raise(MARKER);
                    return bound-1;
                }
            };
        }
        @Override public RandomSource fromHashOf(String text) { throw new AssertionError("Speculative hash"); }
        @Override public RandomSource fromSeed(long seed) { throw new AssertionError("Speculative seed"); }
        @Override public void parityConfigString(StringBuilder out) { throw new AssertionError("Speculative config"); }
    }
    static Aquifer aquifer(Factory effects,String mode) {
        PositionalRandomFactory factory=effects;
        if(mode.equals("legacy-subclass"))factory=new LegacyRandomSource.LegacyPositionalRandomFactory(7) {
            @Override public RandomSource at(int x,int y,int z) { return effects.at(x,y,z); }
        };
        if(mode.equals("xoro-subclass"))factory=new XoroshiroRandomSource.XoroshiroPositionalRandomFactory(7,9) {
            @Override public RandomSource at(int x,int y,int z) { return effects.at(x,y,z); }
        };
        var zero=DensityFunctions.zero();
        DensityFunction erosion=mode.equals("noise-fail")?new DensityFunction.SimpleFunction() {
            @Override public double compute(FunctionContext context) {
                effects.calls.add("noise:"+context.blockX()+":"+context.blockY()+":"+context.blockZ());
                if(effects.reject)throw raise(MARKER);
                return 0;
            }
            @Override public double minValue() { throw new AssertionError("Speculative minimum"); }
            @Override public double maxValue() { throw new AssertionError("Speculative maximum"); }
            @Override public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() { throw new AssertionError("Speculative codec"); }
        }:zero;
        var router=new NoiseRouter(zero,zero,zero,zero,zero,zero,zero,erosion,zero,zero,zero,zero,zero,zero,zero);
        return Aquifer.create(new ObjenesisStd().newInstance(Chunk.class),new ChunkPos(0,0),router,factory,-64,128,
            (x,y,z)->new Aquifer.FluidStatus(63,Blocks.WATER.defaultBlockState()));
    }
    static void query(Aquifer aquifer,Factory effects,String phase,int x,int y,int z) {
        Throwable failure=null;String value="none";
        try { var result=aquifer.computeSubstance(new DensityFunction.SinglePointContext(x,y,z),-0.5);value=result==null?"null":result.toString(); }
        catch(Throwable actual) { failure=actual; }
        System.out.println("AQUIFER_FACTORY mode="+effects.mode+" phase="+phase+" value="+value+" schedule="+aquifer.shouldScheduleFluidUpdate()
            +" same-marker="+(failure==MARKER)+" error="+(failure==null?"none":failure.getClass().getName()+":"+failure.getMessage())+" calls="+effects.calls);
        effects.calls.clear();
    }
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        for(String mode:List.of("plain","at-fail","draw-fail","noise-fail","nested","legacy-subclass","xoro-subclass")) {
            var effects=new Factory(mode);var aquifer=aquifer(effects,mode);
            if(mode.equals("nested"))effects.callback=()->{effects.calls.add("nested-start");query(aquifer,effects,"nested",8,-20,8);effects.calls.add("nested-end");};
            query(aquifer,effects,"first",0,-20,0);effects.reject=false;
            query(aquifer,effects,"retry",0,-20,0);query(aquifer,effects,"cached",0,-20,0);
        }
    }
}
