package net.minecraft.world.level.levelgen;

import com.mojang.serialization.Codec;
import java.util.stream.LongStream;
import net.minecraft.Util;

public class Xoroshiro128PlusPlus {
    private final NativeRandomBits bits;
	public static final Codec<Xoroshiro128PlusPlus> CODEC = Codec.LONG_STREAM
		.comapFlatMap(
			longStream -> Util.fixedSize(longStream, 2).map(ls -> new Xoroshiro128PlusPlus(ls[0], ls[1])),
			xoroshiro128PlusPlus -> LongStream.of(new long[]{xoroshiro128PlusPlus.bits.get(0), xoroshiro128PlusPlus.bits.get(1)})
		);

	public Xoroshiro128PlusPlus(RandomSupport.Seed128bit seed128bit) {
		this(seed128bit.seedLo(), seed128bit.seedHi());
	}

    public Xoroshiro128PlusPlus(long l,long m){bits=new NativeRandomBits(l,m);}
    private Xoroshiro128PlusPlus(long seed,boolean singleSeed){bits=new NativeRandomBits(seed);}
    static Xoroshiro128PlusPlus fromSeed(long seed){return new Xoroshiro128PlusPlus(seed,true);}
    int nativeBounded(int bound){return bits.bounded(bound);}
    double nativeDouble(){return bits.nextDouble();}
    float nativeFloat(){return bits.nextFloat();}
    void nativeConsume(int count){bits.consume(count);}
    private Xoroshiro128PlusPlus(NativeRandomBits bits){this.bits=bits;}
    static Xoroshiro128PlusPlus fromSource(NativeXoroshiroSource source){return new Xoroshiro128PlusPlus(new NativeRandomBits(source));}
    NativeXoroshiroSource sourceOwner(){return this.bits.sourceOwner();}
    static void attach(NativeXoroshiroSource source,Xoroshiro128PlusPlus value){value.bits.bind(source);}
    public long nativeSeedLo(){return bits.get(0);}
    public long nativeSeedHi(){return bits.get(1);}
    public void nativeSetState(long lo,long hi){bits.set(lo,hi);}
    long nativeNoiseLeaf(boolean simplex){return bits.noiseLeaf(simplex);}
    public long nextLong(){return bits.next();}
}
