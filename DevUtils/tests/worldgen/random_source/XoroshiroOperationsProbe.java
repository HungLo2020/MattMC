package net.minecraft.world.level.levelgen;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.mojang.serialization.JsonOps;
/** Observe real public source methods, child factories, cache resets and exact state. */
public final class XoroshiroOperationsProbe {
 static void word(MessageDigest hash,long value){hash.update(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array());}
 public static void main(String[]args)throws Exception{var hash=MessageDigest.getInstance("SHA-256");int observations=0;int[] bounds={1,2,3,7,16,255,256,257,1073741825,Integer.MAX_VALUE};int[] skips={-3,0,1,17,262};for(long seed:new long[]{0,1,-1,Long.MIN_VALUE,Long.MAX_VALUE,42}){var source=new XoroshiroRandomSource(seed);for(int i=0;i<2048;i++){if(i%257==0)source.setSeed(seed^i);long value=switch(i%12){case 0->source.nextInt(bounds[(i/12)%bounds.length]);case 1->Float.floatToRawIntBits(source.nextFloat());case 2->Double.doubleToRawLongBits(source.nextDouble());case 3->source.nextInt();case 4->source.nextBoolean()?1:0;case 5->source.nextLong();case 6->{source.consumeCount(skips[(i/12)%skips.length]);yield 0;}case 7->Double.doubleToRawLongBits(source.nextGaussian());case 8->source.fork().nextLong();case 9->source.forkPositional().at(i,-i,i*17).nextLong();case 10->Double.doubleToRawLongBits(source.forkPositional().fromHashOf("octave_"+i).nextDouble());default->source.forkPositional().fromSeed(i).nextInt(257);};word(hash,i);word(hash,value);observations++;if(i%64==0){hash.update(XoroshiroRandomSource.CODEC.encodeStart(JsonOps.INSTANCE,source).getOrThrow().toString().getBytes(StandardCharsets.UTF_8));observations++;}}}System.out.println("RNG_OPERATION_CORPUS "+observations+" "+HexFormat.of().formatHex(hash.digest()));Thread.sleep(250);}
}
