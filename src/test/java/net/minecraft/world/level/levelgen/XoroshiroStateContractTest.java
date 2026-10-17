package net.minecraft.world.level.levelgen;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
/** Original public RNG-state/codec boundaries before native state ownership. */
class XoroshiroStateContractTest {
 static JsonArray encode(Xoroshiro128PlusPlus value){return Xoroshiro128PlusPlus.CODEC.encodeStart(JsonOps.INSTANCE,value).getOrThrow().getAsJsonArray();}
 static JsonArray json(long...words){var a=new JsonArray();for(long word:words)a.add(word);return a;}
 @Test void allZeroConstructorKeepsOriginalFallbackState(){var value=new Xoroshiro128PlusPlus(0,0);assertEquals(json(-7046029254386353131L,7640891576956012809L),encode(value));assertEquals(encode(value),encode(new Xoroshiro128PlusPlus(new RandomSupport.Seed128bit(0,0))));}
 @Test void codecCopyContinuesFromTheExactAdvancedState(){var value=new Xoroshiro128PlusPlus(1,2);for(int i=0;i<17;i++)value.nextLong();var saved=encode(value);var copy=Xoroshiro128PlusPlus.CODEC.parse(JsonOps.INSTANCE,saved).getOrThrow();for(int i=0;i<128;i++)assertEquals(value.nextLong(),copy.nextLong());assertEquals(encode(value),encode(copy));}
 @Test void codecRejectsWrongWordCount(){for(var words:new JsonArray[]{json(),json(1),json(1,2,3)})assertTrue(Xoroshiro128PlusPlus.CODEC.parse(JsonOps.INSTANCE,words).error().isPresent());}
 static final class Trap extends Xoroshiro128PlusPlus{Trap(){super(1,2);}@Override public long nextLong(){throw new AssertionError("virtual nextLong");}public long nativeSeedLo(){throw new AssertionError("virtual nativeSeedLo");}public long nativeSeedHi(){throw new AssertionError("virtual nativeSeedHi");}}
 @Test void codecObservesActualPrivateStateWithoutVirtualGeneratorCalls(){assertEquals(json(1,2),encode(new Trap()));}
}
