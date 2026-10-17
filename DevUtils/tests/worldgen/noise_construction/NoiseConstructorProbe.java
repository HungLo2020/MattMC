package net.minecraft.world.level.levelgen.synth;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.*;
/** Actual public constructor metadata, samplers, derivative and next RNG word. */
public final class NoiseConstructorProbe {
 static final MessageDigest HASH;static long count;
 static{try{HASH=MessageDigest.getInstance("SHA-256");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 static void put(long value){for(int i=0;i<8;i++)HASH.update((byte)(value>>>(i*8)));count++;}
 public static void main(String[] args)throws Exception{
  for(Class<?> type:new Class<?>[]{ImprovedNoise.class,SimplexNoise.class,RandomSource.class,XoroshiroRandomSource.class,WorldgenRandom.class}) {
   var url=type.getResource(type.getSimpleName()+".class");
   try(var input=url.openStream()){System.out.println("NOISE_ORIGIN "+type.getName()+" "+url+" "+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));}
  }
  long[] seeds={0,1,-1,42,-7340013412337L,Long.MIN_VALUE,Long.MAX_VALUE,0x1234567812345678L};
  double[] positions={0,.125,-.125,1,-1,7.75,-19.25,10000.125,29999984.5,-29999984.5,16777217.25,-16777217.25};
  for(int family=0;family<3;family++)for(long seed:seeds){
   RandomSource a=family==0?RandomSource.create(seed):family==1?new XoroshiroRandomSource(seed):new WorldgenRandom(RandomSource.create(seed));
   var improved=new ImprovedNoise(a);put(Double.doubleToRawLongBits(improved.xo));put(Double.doubleToRawLongBits(improved.yo));put(Double.doubleToRawLongBits(improved.zo));
   var text=new StringBuilder();improved.parityConfigString(text);HASH.update(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
   for(double x:positions)for(double y:positions){double z=x-y*.5;put(Double.doubleToRawLongBits(improved.noise(x,y,z)));put(Double.doubleToRawLongBits(improved.noise(x,y,z,.5,.25)));double[] derivative={.125,-.25,.5};put(Double.doubleToRawLongBits(improved.noiseWithDerivative(x,y,z,derivative)));for(double d:derivative)put(Double.doubleToRawLongBits(d));}
   put(a.nextLong());
   RandomSource b=family==0?RandomSource.create(seed):family==1?new XoroshiroRandomSource(seed):new WorldgenRandom(RandomSource.create(seed));
   var simplex=new SimplexNoise(b);put(Double.doubleToRawLongBits(simplex.xo));put(Double.doubleToRawLongBits(simplex.yo));put(Double.doubleToRawLongBits(simplex.zo));
   for(double x:positions)for(double y:positions){put(Double.doubleToRawLongBits(simplex.getValue(x,y)));put(Double.doubleToRawLongBits(simplex.getValue(x,y,x-y*.5)));}put(b.nextLong());
  }
  System.out.println("NOISE_CONSTRUCTOR_CORPUS "+count+" "+HexFormat.of().formatHex(HASH.digest()));
  Thread.sleep(250);
 }
}
