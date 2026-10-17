package net.minecraft.world.level.levelgen;

import com.mojang.serialization.JsonOps;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.RandomSource;

/** Actual wrapper count, seed/fork operations and backing state; original oracle. */
public final class WorldgenStateProbe {
    static final MessageDigest HASH;
    static long observations;
    static { try { HASH = MessageDigest.getInstance("SHA-256"); } catch (Exception failure) { throw new ExceptionInInitializerError(failure); } }
    static void put(long value) { HASH.update(ByteBuffer.allocate(8).putLong(value).array()); observations++; }
    static void state(RandomSource source) throws Exception {
        if (source instanceof LegacyRandomSource legacy) {
            long value;
            try { value = (long)LegacyRandomSource.class.getMethod("rawSeed").invoke(legacy); }
            catch (NoSuchMethodException original) {
                var field = LegacyRandomSource.class.getDeclaredField("seed"); field.setAccessible(true);
                value = ((AtomicLong)field.get(legacy)).get();
            }
            put(value);
        } else {
            String value = XoroshiroRandomSource.CODEC.encodeStart(JsonOps.INSTANCE, (XoroshiroRandomSource)source).getOrThrow().toString();
            HASH.update(value.getBytes(StandardCharsets.UTF_8)); observations++;
        }
    }
    static void count(WorldgenRandom source, int value) throws Exception {
        try { WorldgenRandom.class.getMethod("nativeSetCount", int.class).invoke(source, value); }
        catch (NoSuchMethodException original) { var field = WorldgenRandom.class.getDeclaredField("count"); field.setAccessible(true); field.setInt(source, value); }
    }
    static void origin(Class<?> type) throws Exception {
        var url = type.getResource(type.getSimpleName() + ".class");
        try (var input = url.openStream()) { System.out.println("WORLDGEN_ORIGIN " + type.getName() + " " + url + " " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()))); }
    }
    public static void main(String[] args) throws Exception {
        origin(WorldgenRandom.class); origin(LegacyRandomSource.class); origin(XoroshiroRandomSource.class);
        int[] bits = {Integer.MIN_VALUE,-65,-33,-1,0,1,26,27,31,32,33,48,63,64,65,Integer.MAX_VALUE};
        for (boolean xoro : new boolean[]{false,true}) for (long seed : new long[]{0,42,-1,Long.MIN_VALUE,Long.MAX_VALUE,123456789012345L}) {
            RandomSource backing = xoro ? new XoroshiroRandomSource(seed) : new LegacyRandomSource(seed);
            var source = new WorldgenRandom(backing);
            for (int i = 0; i < 64; i++) {
                put(source.next(bits[i % bits.length])); put(source.getCount());
                put(source.nextInt(i % 2 == 0 ? 7 : 1073741825)); put(source.getCount());
                put(source.nextLong()); put(Double.doubleToRawLongBits(source.nextDouble())); put(Float.floatToRawIntBits(source.nextFloat())); put(source.nextBoolean() ? 1 : 0); put(source.getCount());
                put(source.setDecorationSeed(seed ^ i, Integer.MIN_VALUE + i, Integer.MAX_VALUE - i)); put(source.getCount()); state(backing);
                source.setFeatureSeed(seed, Integer.MAX_VALUE - i, Integer.MIN_VALUE + i); state(backing); put(source.getCount());
                source.setLargeFeatureSeed(seed + i, Integer.MIN_VALUE + i, Integer.MAX_VALUE - i); state(backing); put(source.getCount());
                source.setLargeFeatureWithSalt(seed, Integer.MAX_VALUE - i, Integer.MIN_VALUE + i, -i); state(backing); put(source.getCount());
                put(source.fork().nextLong()); put(source.forkPositional().fromSeed(seed ^ i).nextLong()); put(source.getCount()); state(backing);
                source.consumeCount(i % 5 - 2); put(source.getCount()); state(backing);
                if (i == 31) { count(source, Integer.MAX_VALUE - 1); put(source.nextLong()); put(source.getCount()); }
            }
        }
        System.out.println("WORLDGEN_CORPUS " + observations + " " + HexFormat.of().formatHex(HASH.digest()));
        observations=0;
        for(boolean xoro:new boolean[]{false,true})for(boolean simplex:new boolean[]{false,true})for(long seed:new long[]{0,42,-1,Long.MIN_VALUE,Long.MAX_VALUE,123456789012345L}){
            RandomSource backing=xoro?new XoroshiroRandomSource(seed):new LegacyRandomSource(seed);var source=new WorldgenRandom(backing);count(source,Integer.MAX_VALUE-100);
            if(simplex){var leaf=new net.minecraft.world.level.levelgen.synth.SimplexNoise(source);put(Double.doubleToRawLongBits(leaf.xo));put(Double.doubleToRawLongBits(leaf.yo));put(Double.doubleToRawLongBits(leaf.zo));for(int i=0;i<64;i++)put(Double.doubleToRawLongBits(leaf.getValue(i*.25,-i*.125,i*.5)));}
            else{var leaf=new net.minecraft.world.level.levelgen.synth.ImprovedNoise(source);put(Double.doubleToRawLongBits(leaf.xo));put(Double.doubleToRawLongBits(leaf.yo));put(Double.doubleToRawLongBits(leaf.zo));for(int i=0;i<64;i++)put(Double.doubleToRawLongBits(leaf.noise(i*.25,-i*.125,i*.5)));}
            put(source.getCount());state(backing);put(backing.nextLong());
            for(int x:new int[]{Integer.MIN_VALUE,-46341,0,46341,Integer.MAX_VALUE})for(int z:new int[]{Integer.MIN_VALUE,-46341,0,46341,Integer.MAX_VALUE})put(WorldgenRandom.seedSlimeChunk(x,z,seed,0x123456789abcdef0L).nextLong());
        }
        System.out.println("WORLDGEN_LEAF_CORPUS " + observations + " " + HexFormat.of().formatHex(HASH.digest()));
    }
}
