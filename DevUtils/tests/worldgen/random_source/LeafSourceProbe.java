package net.minecraft.world.level.levelgen;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.reflect.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.level.levelgen.synth.*;
/** Observe actual original VM or native compact leaf data, not a copied generator. */
public final class LeafSourceProbe {
 static void word(OutputStream out,long word)throws IOException{out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(word).array());}
 static byte[] permutation(Object leaf)throws Exception{
  try{Field p=leaf.getClass().getDeclaredField("p");p.setAccessible(true);Object actual=p.get(leaf);byte[] result=new byte[256];for(int i=0;i<256;i++)result[i]=(byte)Array.getInt(actual,i);return result;}
  catch(NoSuchFieldException compact){Field field=leaf.getClass().getDeclaredField("seed");field.setAccessible(true);Object actual=field.get(leaf);Method reader=actual.getClass().getDeclaredMethod("diagnosticPermutation");reader.setAccessible(true);return(byte[])reader.invoke(actual);}
 }
 public static void main(String[]args)throws Exception{
  for(Class<?> type:new Class<?>[]{Xoroshiro128PlusPlus.class,XoroshiroRandomSource.class,ImprovedNoise.class,SimplexNoise.class}){var url=type.getResource(type.getSimpleName()+".class");try(var input=url.openStream()){System.out.println("RNG_LEAF_ORIGIN "+type.getName()+" "+url+" "+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));}}
  try(var out=Files.newOutputStream(Path.of(args[0]))){for(long[] seed:new long[][]{{0,0},{1,2},{-1,Long.MAX_VALUE},{Long.MIN_VALUE,-1},{0,1},{1,0}})for(int kind=0;kind<2;kind++){var source=new XoroshiroRandomSource(seed[0],seed[1]);Object leaf=kind==0?new ImprovedNoise(source):new SimplexNoise(source);for(String name:new String[]{"xo","yo","zo"})word(out,Double.doubleToRawLongBits(leaf.getClass().getField(name).getDouble(leaf)));out.write(permutation(leaf));word(out,source.nextLong());out.write(XoroshiroRandomSource.CODEC.encodeStart(JsonOps.INSTANCE,source).getOrThrow().toString().getBytes(StandardCharsets.UTF_8));}}
  Thread.sleep(250);
 }
}
