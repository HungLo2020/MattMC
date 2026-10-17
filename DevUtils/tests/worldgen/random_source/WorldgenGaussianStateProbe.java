package net.minecraft.world.level.levelgen;

import java.util.HexFormat;
import net.minecraft.util.RandomSource;

/** Real Gaussian values/cache/count/backing state across original public effects. */
public final class WorldgenGaussianStateProbe {
    static void put(long value){WorldgenStateProbe.put(value);}
    static void cache(MarsagliaPolarGaussian value)throws Exception {put(WorldgenGaussianContractTest.has(value)?1:0);put(Double.doubleToRawLongBits(WorldgenGaussianContractTest.value(value)));}
    public static void main(String[]args)throws Exception {
        for(Class<?> type:new Class<?>[]{WorldgenRandom.class,LegacyRandomSource.class,XoroshiroRandomSource.class,MarsagliaPolarGaussian.class})WorldgenStateProbe.origin(type);
        long[] raw={0,Long.MIN_VALUE,Double.doubleToRawLongBits(19.25),0x7ff8000000000042L};
        for(boolean xoro:new boolean[]{false,true})for(long seed:new long[]{0,42,-1,Long.MIN_VALUE,Long.MAX_VALUE,123456789012345L})for(int kind=0;kind<3;kind++){
            RandomSource backing=xoro?new XoroshiroRandomSource(seed):new LegacyRandomSource(seed),otherBacking=xoro?new XoroshiroRandomSource(seed^19):new LegacyRandomSource(seed^19);
            var wrapper=new WorldgenRandom(backing);var other=new WorldgenRandom(otherBacking);var cache=kind==0?WorldgenGaussianContractTest.cache(wrapper):new MarsagliaPolarGaussian(wrapper);
            for(int i=0;i<128;i++){
                put(Double.doubleToRawLongBits(cache.nextGaussian()));put(wrapper.getCount());put(other.getCount());cache(cache);WorldgenStateProbe.state(backing);WorldgenStateProbe.state(otherBacking);
                if(i%11==0){wrapper.setSeed(seed^i);other.setSeed(~seed+i);put(wrapper.getCount());put(other.getCount());cache(cache);WorldgenStateProbe.state(backing);WorldgenStateProbe.state(otherBacking);}
                if(i%17==0){cache.reset();cache(cache);}
                if(i%23==0){WorldgenGaussianContractTest.set(cache,true,Double.longBitsToDouble(raw[(i/23)%raw.length]));put(Double.doubleToRawLongBits(cache.nextGaussian()));put(wrapper.getCount());put(other.getCount());cache(cache);}
                if(kind==2&&(i==63||i==95))WorldgenGaussianContractTest.target(cache,i==63?other:wrapper);
                if(i==96){WorldgenStateProbe.count(wrapper,Integer.MAX_VALUE-3);WorldgenStateProbe.count(other,Integer.MAX_VALUE-3);}
            }
        }
        System.out.println("WORLDGEN_GAUSSIAN_CORPUS "+WorldgenStateProbe.observations+" "+HexFormat.of().formatHex(WorldgenStateProbe.HASH.digest()));
    }
}
