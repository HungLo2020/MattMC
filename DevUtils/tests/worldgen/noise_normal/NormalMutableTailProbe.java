package net.minecraft.world.level.levelgen.synth;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
/** Genuine factory mutation produces different actual child sizes; inspect public live amplitudes. */
public final class NormalMutableTailProbe {
 public static void main(String[] args)throws Exception{
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  for(Class<?> c:new Class<?>[]{NormalNoise.class,PerlinNoise.class,ImprovedNoise.class}){var url=c.getResource(c.getSimpleName()+".class");try(var input=url.openStream()){System.out.println("NORMAL_ORIGIN "+c.getName()+" "+url+" "+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));}}
  var amplitudes=new DoubleArrayList(new double[]{1,1});var random=new CompositeConstructorContractTest.Source();random.onHash=()->amplitudes.add(1.0);
  var parameters=new NormalNoise.NoiseParameters(-1,amplitudes);var noise=NormalNoise.create(random,parameters);
  if(noise.parameters()!=parameters)throw new AssertionError("parameter identity changed");
  double before=noise.getValue(12.25,-4.5,6.75);amplitudes.set(2,2.0);double after=noise.getValue(12.25,-4.5,6.75);
  System.out.println("NORMAL_MUTABLE_TAIL {\"before_bits\":"+Double.doubleToRawLongBits(before)+",\"after_bits\":"+Double.doubleToRawLongBits(after)+",\"size\":"+amplitudes.size()+",\"hashes\":\""+random.hashes+"\"}");Thread.sleep(250);
 }
}
