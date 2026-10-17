package net.minecraft.world.level.levelgen.synth;
import java.security.MessageDigest;
import java.util.*;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.*;
/** Public composite metadata/samples/RNG state and sequential mutation, no copied algorithm. */
public final class CompositeConstructorProbe {
 static final MessageDigest HASH;static long count;
 static{try{HASH=MessageDigest.getInstance("SHA-256");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 static void word(long value){for(int i=0;i<8;i++)HASH.update((byte)(value>>>(i*8)));count++;}
 static void value(double value){word(Double.doubleToRawLongBits(value));}
 static void text(StringBuilder text){HASH.update(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 static RandomSource source(int family,long seed){return family==0?RandomSource.create(seed):new XoroshiroRandomSource(seed);}
 static void origin(Class<?> type)throws Exception{var url=type.getResource(type.getSimpleName()+".class");try(var input=url.openStream()){System.out.println("COMPOSITE_ORIGIN "+type.getName()+" "+url+" "+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));}}
 public static void main(String[] args)throws Exception {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  for(Class<?> type:new Class<?>[]{PerlinNoise.class,ImprovedNoise.class,NormalNoise.class,PerlinSimplexNoise.class,BlendedNoise.class,RandomSource.class,LegacyRandomSource.class,XoroshiroRandomSource.class})origin(type);
  for(String name:new String[]{"net.minecraft.world.level.levelgen.synth.NativePerlin","net.minecraft.world.level.levelgen.synth.NativeNoiseSeed"})try{origin(Class.forName(name));}catch(ClassNotFoundException absent){}
  long[] seeds={0,1,-1,42,-7340013412337L,Long.MIN_VALUE,Long.MAX_VALUE,0x1234567812345678L};
  double[] points={0,.125,-.125,1,-1,7.75,-19.25,10000.125,29999984.5,-29999984.5};
  for(int family=0;family<2;family++)for(long seed:seeds) {
   for(boolean legacy:new boolean[]{false,true}) {
    var random=source(family,seed);var amplitudes=new DoubleArrayList(new double[]{1,0,.5,1});
    var noise=legacy?PerlinNoise.createLegacyForLegacyNetherBiome(random,-3,amplitudes):PerlinNoise.create(random,-3,amplitudes);
    var debug=new StringBuilder();noise.parityConfigString(debug);text(debug);value(noise.maxBrokenValue(.75));
    for(int phase=0;phase<3;phase++) {
     if(phase==1)amplitudes.set(2,.75);if(phase==2)amplitudes.set(1,1.0);
     for(double x:points)for(double y:points){value(noise.getValue(x,y,x-y*.5));value(noise.getValue(x,y,x-y*.5,.5,.25,false));value(noise.getValue(x,y,x-y*.5,.5,.25,true));}
    }
    for(int i=0;i<4;i++){var leaf=noise.getOctaveNoise(i);word(leaf==null?0:1);if(leaf!=null){value(leaf.xo);value(leaf.yo);value(leaf.zo);value(leaf.noise(1.25,7.5,-3.75));}}
    word(random.nextLong());
   }
   for(boolean legacy:new boolean[]{false,true}) {
    var random=source(family,seed);var amplitudes=new DoubleArrayList(new double[]{1,0,.5,1});var parameters=new NormalNoise.NoiseParameters(-3,amplitudes);
    var noise=legacy?NormalNoise.createLegacyNetherBiome(random,parameters):NormalNoise.create(random,parameters);
    value(noise.maxValue());word(noise.parameters().firstOctave());var debug=new StringBuilder();noise.parityConfigString(debug);text(debug);
    for(int phase=0;phase<3;phase++){if(phase==1)amplitudes.set(2,.75);if(phase==2)amplitudes.set(1,1.0);for(double x:points)for(double y:points)value(noise.getValue(x,y,x-y*.5));}
    word(random.nextLong());
   }
   for(var octaves:List.of(List.of(-3,-1,0),List.of(-2,0,1),List.of(-2,0,2))) {
    var random=source(family,seed);var noise=new PerlinSimplexNoise(random,octaves);
    for(double x:points)for(double y:points){value(noise.getValue(x,y,false));value(noise.getValue(x,y,true));}word(random.nextLong());
   }
   var random=source(family,seed);var blended=new BlendedNoise(random,.25,.125,80,160,8);value(blended.minValue());value(blended.maxValue());
   var debug=new StringBuilder();blended.parityConfigString(debug);text(debug);
   for(double x:points)for(double y:points)value(blended.compute(new DensityFunction.SinglePointContext((int)x,(int)y,(int)(x-y*.5))));word(random.nextLong());
  }
  System.out.println("COMPOSITE_CONSTRUCTOR_CORPUS "+count+" "+HexFormat.of().formatHex(HASH.digest()));Thread.sleep(250);
 }
}
