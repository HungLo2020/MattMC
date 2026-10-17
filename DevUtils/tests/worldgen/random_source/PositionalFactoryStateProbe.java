package net.minecraft.world.level.levelgen;
import java.util.HexFormat;
import net.minecraft.util.RandomSource;
/** Actual public factory, child source/cache/state and successor observations. */
public final class PositionalFactoryStateProbe {
    static void put(long value){WorldgenStateProbe.put(value);}
    static void sample(RandomSource source) throws Exception {
        WorldgenStateProbe.state(source);
        for(int i=0;i<12;i++){
            put(source.nextLong());put(source.nextInt(37));
            put(Double.doubleToRawLongBits(source.nextGaussian()));
            if(i%4==0)WorldgenStateProbe.state(source);
        }
        var child=source.fork();put(child.nextLong());WorldgenStateProbe.state(child);
        var positional=source.forkPositional();put(positional.at(Integer.MIN_VALUE,19,Integer.MAX_VALUE).nextLong());
        WorldgenStateProbe.state(source);
    }
    static void origin(Class<?> type)throws Exception {
        var url=type.getResource("/"+type.getName().replace('.','/')+".class");
        try(var input=url.openStream()){System.out.println("POSITIONAL_ORIGIN "+type.getName()+" "+url+" "+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));}
    }
    public static void main(String[] args)throws Exception {
        for(Class<?> c:new Class<?>[]{LegacyRandomSource.class,XoroshiroRandomSource.class,LegacyRandomSource.LegacyPositionalRandomFactory.class,XoroshiroRandomSource.XoroshiroPositionalRandomFactory.class})origin(c);
        for(boolean xoro:new boolean[]{false,true})for(long a:new long[]{0,42,-1,Long.MIN_VALUE,Long.MAX_VALUE,123456789012345L}){
            PositionalRandomFactory factory=xoro?new XoroshiroRandomSource.XoroshiroPositionalRandomFactory(a,~a):new LegacyRandomSource.LegacyPositionalRandomFactory(a);
            for(int[] xyz:new int[][]{{0,0,0},{7,-11,23},{Integer.MIN_VALUE,Integer.MAX_VALUE,-1},{Integer.MAX_VALUE,-1,Integer.MIN_VALUE}})sample(factory.at(xyz[0],xyz[1],xyz[2]));
            for(long seed:new long[]{0,42,-1,Long.MIN_VALUE,Long.MAX_VALUE})sample(factory.fromSeed(seed));
            for(String text:new String[]{"","minecraft:overworld","Aa","BB","\u03bb\ud83d\ude00","\ud800","\udc00","\udc00\ud800","a".repeat(129)})sample(factory.fromHashOf(text));
            var builder=new StringBuilder("prefix ");factory.parityConfigString(builder);put(builder.length());for(int i=0;i<builder.length();i++)put(builder.charAt(i));
        }
        System.out.println("POSITIONAL_FACTORY_CORPUS "+WorldgenStateProbe.observations+" "+HexFormat.of().formatHex(WorldgenStateProbe.HASH.digest()));
    }
}
