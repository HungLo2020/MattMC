package net.minecraft.world.level.biome;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.core.BlockPos;

/** Portable actual public consumers and cache observations; no copied temperature algorithm. */
public final class TemperatureProbe {
    static final MessageDigest HASH;
    static { try { HASH = MessageDigest.getInstance("SHA-256"); } catch(Exception e){throw new ExceptionInInitializerError(e);} }
    static final ByteBuffer WORD = ByteBuffer.allocate(8);
    static long operations;
    static volatile long sink;
    static void word(long value){WORD.clear();WORD.putLong(value);HASH.update(WORD.array());operations++;}
    static Biome biome(float base,Biome.TemperatureModifier modifier) {
        return new Biome(new Biome.ClimateSettings(true,base,modifier,.5f),
            new BiomeSpecialEffects.Builder().fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build(),
            BiomeGenerationSettings.EMPTY,MobSpawnSettings.EMPTY);
    }
    static void history(Biome biome)throws Exception {
        var field=Biome.class.getDeclaredField("temperatureCache");field.setAccessible(true);
        Object cache=((ThreadLocal<?>)field.get(biome)).get();
        if(cache instanceof it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap map) {
            word(map.size());for(var e:map.long2FloatEntrySet()){word(e.getLongKey());word(Integer.toUnsignedLong(Float.floatToRawIntBits(e.getFloatValue())));}
        } else {
            var method=cache.getClass().getDeclaredMethod("snapshot");method.setAccessible(true);
            long[] pairs=(long[])method.invoke(cache);word(pairs.length/2);for(long value:pairs)word(value);
        }
    }
    static void corpus()throws Exception {
        int[] coordinates={0,1,-1,17,-17,16777217,-16777217,29999984,-29999984,Integer.MIN_VALUE,Integer.MAX_VALUE};
        int[] heights={-2048,-64,0,79,80,81,96,200,319,2047,Integer.MIN_VALUE,Integer.MAX_VALUE};
        int[] seas={63,300,-64,Integer.MAX_VALUE,Integer.MIN_VALUE};
        int[] bases={0,0x80000000,Float.floatToRawIntBits(.15f),Float.floatToRawIntBits(.15f)-1,Float.floatToRawIntBits(.15f)+1,Float.floatToRawIntBits(.1f),Float.floatToRawIntBits(.1f)-1,Float.floatToRawIntBits(.1f)+1,Float.floatToRawIntBits(.2f),Float.floatToRawIntBits(-.7f),0x7fc12345,0xffc12345,0x7f800000,0xff800000,1};
        for(var modifier:Biome.TemperatureModifier.values())for(int bits:bases){
            float base=Float.intBitsToFloat(bits);Biome b=biome(base,modifier);
            for(int x:coordinates)for(int z:coordinates){
                word(Float.floatToRawIntBits(modifier.modifyTemperature(new BlockPos(x,0,z),base)));
                for(int y:heights)for(int sea:seas){
                    BlockPos p=new BlockPos(x,y,z);
                    word(b.warmEnoughToRain(p,sea)?1:0);
                    word(b.shouldMeltFrozenOceanIcebergSlightly(p,sea)?1:0);
                    word(b.coldEnoughToSnow(p,sea)?1:0);
                }
            }
            history(b);
        }
        System.out.println("TEMPERATURE_CORPUS {\"operations\":"+operations+",\"sha256\":\""+HexFormat.of().formatHex(HASH.digest())+"\"}");
    }
    static long work(Biome b,BlockPos.MutableBlockPos pos,int kind,int count) {
        long sum=0;
        for(int i=0;i<count;i++){
            pos.set(kind==0?0:i&4095,96,kind==0?0:(i>>>12)&15);
            if(kind==2)sum+=Float.floatToRawIntBits(Biome.TemperatureModifier.FROZEN.modifyTemperature(pos,.15f));
            else sum+=b.warmEnoughToRain(pos,63)?1:0;
        }
        sink=sum;return sum;
    }
    static void screen(){
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        for(int kind=0;kind<3;kind++){
            var b=biome(.15f,Biome.TemperatureModifier.NONE);var pos=new BlockPos.MutableBlockPos();work(b,pos,kind,40000);
            int count=kind==0?1000000:120000;
            for(int round=0;round<3;round++){
                long compilation=java.lang.management.ManagementFactory.getCompilationMXBean().getTotalCompilationTime();
                long allocated=bean.getThreadAllocatedBytes(Thread.currentThread().threadId()),cpu=bean.getCurrentThreadCpuTime(),start=System.nanoTime();
                long sum=work(b,pos,kind,count);long elapsed=System.nanoTime()-start;cpu=bean.getCurrentThreadCpuTime()-cpu;allocated=bean.getThreadAllocatedBytes(Thread.currentThread().threadId())-allocated;
                compilation=java.lang.management.ManagementFactory.getCompilationMXBean().getTotalCompilationTime()-compilation;
                System.out.println("TEMPERATURE_SCREEN {\"kind\":"+kind+",\"round\":"+round+",\"count\":"+count+",\"elapsed_ns\":"+elapsed+",\"cpu_ns\":"+cpu+",\"java_bytes\":"+allocated+",\"compilation_ms\":"+compilation+",\"sum\":"+sum+"}");
            }
        }
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var resource=Biome.class.getResource("Biome.class");
        System.out.println("TEMPERATURE_ORIGIN "+resource+" "+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource.openStream().readAllBytes())));
        if(args.length!=0&&args[0].equals("screen"))screen();else corpus();
        Thread.sleep(250); // Allow the parent to observe the actual loaded native image.
    }
}
