package net.minecraft.world.level.levelgen;
import java.security.MessageDigest;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import com.mojang.serialization.JsonOps;
/** Independent original public sequence/state oracle; no migrated runtime claim. */
public final class XoroshiroStateProbe {
 public static void main(String[]args)throws Exception{var c=Xoroshiro128PlusPlus.class;var url=c.getResource(c.getSimpleName()+".class");try(var input=url.openStream()){System.out.println("RNG_ORIGIN "+url+" "+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));}var hash=MessageDigest.getInstance("SHA-256");int observations=0;for(long[] seed:new long[][]{{0,0},{1,2},{-1,Long.MAX_VALUE},{Long.MIN_VALUE,-1},{0,1},{1,0}}){var value=new Xoroshiro128PlusPlus(seed[0],seed[1]);for(int i=0;i<=2048;i++){if(i==0||i==1||i==2||i==31||i==1023||i==2048){var state=Xoroshiro128PlusPlus.CODEC.encodeStart(JsonOps.INSTANCE,value).getOrThrow();hash.update(state.toString().getBytes(StandardCharsets.UTF_8));observations++;}if(i<2048){hash.update(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value.nextLong()).array());observations++;}}}System.out.println("RNG_STATE_CORPUS "+observations+" "+HexFormat.of().formatHex(hash.digest()));Thread.sleep(250);}
}
