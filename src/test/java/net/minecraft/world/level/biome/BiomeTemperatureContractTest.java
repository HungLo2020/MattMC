package net.minecraft.world.level.biome;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.*;
class BiomeTemperatureContractTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 static final class Subject {
  final List<String> trace=new ArrayList<>(); final Biome biome; int reads; Runnable callback; RuntimeException failure;
  Subject(){this(.2f);}
  Subject(float base){this(base,Biome.TemperatureModifier.NONE);}
  Subject(float base,Biome.TemperatureModifier modifier){biome=new Biome(new Biome.ClimateSettings(true,base,modifier,.5f),new BiomeSpecialEffects.Builder().fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build(),BiomeGenerationSettings.EMPTY,MobSpawnSettings.EMPTY);}
  Position at(long key){return new Position(key,0,this);}
  boolean warm(BlockPos p,int sea){return biome.warmEnoughToRain(p,sea);}
 }
 static final class Position extends BlockPos {
  final long key; final int height; final Subject owner;
  Position(long key,int height,Subject owner){super(0,height,0);this.key=key;this.height=height;this.owner=owner;}
  @Override public long asLong(){if(owner!=null)owner.trace.add("key");return key;}
  @Override public int getY(){if(owner!=null){owner.trace.add("y");owner.reads++;if(owner.failure!=null)throw owner.failure;var work=owner.callback;if(work!=null)work.run();}return height;}
  @Override public int getX(){if(owner!=null)owner.trace.add("x");return 0;}
  @Override public int getZ(){if(owner!=null)owner.trace.add("z");return 0;}
 }
 @Test void cacheHitSkipsTheOriginalCoordinateQueries(){var b=new Subject();var p=b.at(1);assertTrue(b.warm(p,63));assertEquals(List.of("key","y"),b.trace);b.trace.clear();assertTrue(b.warm(p,300));assertEquals(List.of("key"),b.trace);assertEquals(1,b.reads);}
 @Test void insertionOrderIsNotRefreshedByAHit(){var b=new Subject();for(int i=0;i<1024;i++)assertTrue(b.warm(b.at(i),63));assertEquals(1024,b.reads);assertTrue(b.warm(b.at(0),63));assertEquals(1024,b.reads);assertTrue(b.warm(b.at(1024),63));assertEquals(1025,b.reads);assertTrue(b.warm(b.at(1),63));assertEquals(1025,b.reads);assertTrue(b.warm(b.at(0),63));assertEquals(1026,b.reads);}
 @Test void nanValuesRemainMissesAndPreservePublicThresholdSemantics(){var b=new Subject(Float.NaN);var p=b.at(3);assertFalse(b.warm(p,63));assertFalse(b.biome.shouldMeltFrozenOceanIcebergSlightly(p,63));assertTrue(b.biome.coldEnoughToSnow(p,63));assertEquals(3,b.reads);assertEquals(List.of("key","y","key","y","key","y"),b.trace);}
 @Test void throwingCoordinateGetterDoesNotPopulateCache(){var b=new Subject();var p=b.at(4);var failure=new RuntimeException("temperature coordinate");b.failure=failure;assertSame(failure,assertThrows(RuntimeException.class,()->b.warm(p,63)));assertEquals(List.of("key","y"),b.trace);b.failure=null;assertTrue(b.warm(p,63));assertEquals(2,b.reads);assertTrue(b.warm(p,63));assertEquals(2,b.reads);}
 @Test void reentrantMissUsesLiveCapacityAfterItsActualCallback(){var b=new Subject();for(int i=0;i<1023;i++)assertTrue(b.warm(b.at(i),63));b.callback=()->{b.callback=null;assertTrue(b.warm(b.at(3000),63));};assertTrue(b.warm(b.at(4000),63));assertEquals(1025,b.reads);assertTrue(b.warm(b.at(1),63));assertEquals(1025,b.reads);assertTrue(b.warm(b.at(0),63));assertEquals(1026,b.reads);}
 @Test void onePositionKeepsSeaLevelHistoryPerBiomeAndPerActualThread()throws Exception{var b=new Subject(.15f);var p=new BlockPos(0,200,0);assertTrue(b.warm(p,300));assertTrue(b.warm(p,63));try(var pool=Executors.newSingleThreadExecutor()){assertFalse(pool.submit(()->b.warm(p,63)).get(5,TimeUnit.SECONDS));assertFalse(pool.submit(()->b.warm(p,300)).get(5,TimeUnit.SECONDS));}assertTrue(b.warm(p,63));var other=new Subject(.15f);assertFalse(other.warm(p,63));assertFalse(other.warm(p,300));}

 @Test void highAltitudeReadsYAgainAfterActualNoiseCoordinates(){var b=new Subject(.15f);assertFalse(b.warm(new Position(80,200,b),63));assertEquals(List.of("key","y","x","z","y"),b.trace);}
 @Test void frozenModifierUsesTheActualRepeatedCoordinateGetters(){var b=new Subject(.01f,Biome.TemperatureModifier.FROZEN);assertTrue(b.warm(b.at(90),63));assertEquals(List.of("key","x","z","x","z","x","z","y"),b.trace);b.trace.clear();assertTrue(b.warm(b.at(90),63));assertEquals(List.of("key"),b.trace);}
 @Test void nullModifierFailsBeforeCoordinatesAndNeverPopulatesTheCache(){var b=new Subject(.2f,null);var p=b.at(91);assertThrows(NullPointerException.class,()->b.warm(p,63));assertThrows(NullPointerException.class,()->b.warm(p,63));assertEquals(List.of("key","key"),b.trace);}
 @Test void noneModifierPreservesAllRawBitsAndNeverTouchesPosition(){for(int bits:new int[]{0,0x80000000,0x7fc12345,0xffc12345,0x7f800000,1})assertEquals(bits,Float.floatToRawIntBits(Biome.TemperatureModifier.NONE.modifyTemperature(null,Float.intBitsToFloat(bits))));}
 @Test void coordinateFailureDuringFrozenModifierPreservesItsCompletedPrefix(){var failure=new RuntimeException("frozen x");var trace=new ArrayList<String>();var p=new BlockPos(0,0,0){int reads;@Override public int getX(){trace.add("x");if(++reads==2)throw failure;return 0;}@Override public int getZ(){trace.add("z");return 0;}};assertSame(failure,assertThrows(RuntimeException.class,()->Biome.TemperatureModifier.FROZEN.modifyTemperature(p,.2f)));assertEquals(List.of("x","z","x"),trace);}
}
