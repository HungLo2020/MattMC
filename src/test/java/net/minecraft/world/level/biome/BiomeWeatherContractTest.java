package net.minecraft.world.level.biome;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.*;

/** Portable baseline for the next connected biome policy move. */
class BiomeWeatherContractTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 static Biome biome(boolean precipitation,float base){return new Biome(new Biome.ClimateSettings(precipitation,base,Biome.TemperatureModifier.NONE,.5f),new BiomeSpecialEffects.Builder().fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build(),BiomeGenerationSettings.EMPTY,MobSpawnSettings.EMPTY);}
 static class Fixture {
  final List<String> trace=new ArrayList<>();final LevelReader level=mock(LevelReader.class);final Biome b;final BlockPos pos=new BlockPos(7,70,11){@Override public long asLong(){trace.add("key");return BlockPos.asLong(7,70,11);}@Override public int getY(){trace.add("y");return 70;}};
  Fixture(boolean precipitation,float base){b=biome(precipitation,base);when(level.getSeaLevel()).thenAnswer(i->{trace.add("sea");return 63;});when(level.isInsideBuildHeight(anyInt())).thenAnswer(i->{trace.add("inside");return true;});when(level.getBrightness(eq(LightLayer.BLOCK),same(pos))).thenAnswer(i->{trace.add("light");return 0;});when(level.getBlockState(same(pos))).thenAnswer(i->{trace.add("block");return Blocks.WATER.defaultBlockState();});when(level.getFluidState(same(pos))).thenAnswer(i->{trace.add("fluid");return Blocks.WATER.defaultBlockState().getFluidState();});trace.clear();}
 }
 @Test void warmFreezeReadsSeaAndTemperatureBeforeAnyWorldMaterial(){var f=new Fixture(true,1);assertFalse(f.b.shouldFreeze(f.level,f.pos,false));assertEquals(List.of("sea","key","y"),f.trace);}
 @Test void coldInteriorWaterFreezesWithoutNeighbourQueriesWhenEdgeCheckDisabled(){var f=new Fixture(true,0);assertTrue(f.b.shouldFreeze(f.level,f.pos,false));assertEquals(List.of("sea","key","y","y","inside","light","block","fluid"),f.trace);}
 @Test void outsideHeightSkipsLightAndMaterials(){var f=new Fixture(true,0);when(f.level.isInsideBuildHeight(anyInt())).thenAnswer(i->{f.trace.add("inside");return false;});f.trace.clear();assertFalse(f.b.shouldFreeze(f.level,f.pos,false));assertEquals(List.of("sea","key","y","y","inside"),f.trace);}
 @Test void seaLevelFailurePrecedesEvenNullPositionAndNoPrecipitation(){var f=new Fixture(false,0);var expected=new RuntimeException("sea");when(f.level.getSeaLevel()).thenThrow(expected);f.trace.clear();assertSame(expected,assertThrows(RuntimeException.class,()->f.b.shouldSnow(f.level,null)));assertEquals(List.of(),f.trace);}
 @Test void drySnowSkipsPositionAndBrightnessAfterSeaLevel(){var f=new Fixture(false,0);assertFalse(f.b.shouldSnow(f.level,f.pos));assertEquals(List.of("sea"),f.trace);assertEquals(Biome.Precipitation.NONE,f.b.getPrecipitationAt(null,63));}
 @Test void brightColdWaterSkipsMaterialReads(){var f=new Fixture(true,0);when(f.level.getBrightness(eq(LightLayer.BLOCK),same(f.pos))).thenAnswer(i->{f.trace.add("light");return 10;});assertFalse(f.b.shouldFreeze(f.level,f.pos,false));assertEquals(List.of("sea","key","y","y","inside","light"),f.trace);}

 @Test void waterNeighboursAreShortCircuitedInWestEastNorthSouthOrder(){
  long[] expected={BlockPos.asLong(6,70,11),BlockPos.asLong(8,70,11),BlockPos.asLong(7,70,10),BlockPos.asLong(7,70,12)};
  for(int firstDry=0;firstDry<=4;firstDry++){
   var f=new Fixture(true,0);int dry=firstDry;var seen=new ArrayList<Long>();
   doAnswer(i->{BlockPos pos=i.getArgument(0);seen.add(pos.asLong());return seen.size()-1!=dry;}).when(f.level).isWaterAt(any(BlockPos.class));
   f.trace.clear();assertEquals(firstDry<4,f.b.shouldFreeze(f.level,f.pos,true));
   assertArrayEquals(java.util.Arrays.copyOf(expected,Math.min(firstDry+1,4)),seen.stream().mapToLong(Long::longValue).toArray());
  }
 }
 @Test void genuineFluidFailureFollowsBlockReadAndSkipsNeighbours(){
  var f=new Fixture(true,0);var expected=new RuntimeException("fluid");
  doAnswer(i->{f.trace.add("fluid");throw expected;}).when(f.level).getFluidState(same(f.pos));
  f.trace.clear();assertSame(expected,assertThrows(RuntimeException.class,()->f.b.shouldFreeze(f.level,f.pos,true)));
  assertEquals(List.of("sea","key","y","y","inside","light","block","fluid"),f.trace);verify(f.level,never()).isWaterAt(any());
 }

 @Test void seaLevelCallbackCanPopulateTheSharedCacheBeforeOuterLookup(){
  var b=biome(true,.15f);var trace=new ArrayList<String>();
  var pos=new BlockPos(7,200,11){@Override public long asLong(){trace.add("key");return BlockPos.asLong(7,200,11);}@Override public int getY(){trace.add("y");return 200;}};
  var level=mock(LevelReader.class);doAnswer(i->{trace.add("sea");assertTrue(b.warmEnoughToRain(pos,300));return 63;}).when(level).getSeaLevel();
  assertFalse(b.shouldFreeze(level,pos,false));assertEquals(List.of("sea","key","y","key"),trace);verify(level,never()).getBrightness(any(),any());
 }
 @Test void cachedTemperatureStillLeavesTheWorldHeightReadAtItsOriginalPoint(){
  var f=new Fixture(true,0);assertFalse(f.b.warmEnoughToRain(f.pos,300));f.trace.clear();
  assertTrue(f.b.shouldFreeze(f.level,f.pos,false));assertEquals(List.of("sea","key","y","inside","light","block","fluid"),f.trace);
 }
 @Test void neighbourDerivationUsesTheActualVirtualPositionAndAlias(){
  var b=biome(true,0);var alias=new BlockPos(200,300,400);var seen=new ArrayList<BlockPos>();
  var pos=new BlockPos(7,70,11){@Override public BlockPos west(){return alias;}};var level=mock(LevelReader.class);
  when(level.getSeaLevel()).thenReturn(63);when(level.isInsideBuildHeight(anyInt())).thenReturn(true);
  when(level.getBlockState(any())).thenReturn(Blocks.WATER.defaultBlockState());when(level.getFluidState(any())).thenReturn(Blocks.WATER.defaultBlockState().getFluidState());
  doAnswer(i->{seen.add(i.getArgument(0));return false;}).when(level).isWaterAt(any());
  assertTrue(b.shouldFreeze(level,pos,true));assertEquals(1,seen.size());assertSame(alias,seen.get(0));
 }
 @Test void coldSnowQueriesSurvivalOnlyForAirOrSnow(){
  for(var state:List.of(Blocks.AIR.defaultBlockState(),Blocks.SNOW.defaultBlockState(),Blocks.STONE.defaultBlockState())){
   var b=biome(true,0);var pos=new BlockPos(7,70,11);var level=mock(LevelReader.class);var reads=new ArrayList<BlockPos>();
   when(level.getSeaLevel()).thenReturn(63);when(level.isInsideBuildHeight(anyInt())).thenReturn(true);
   doAnswer(i->{BlockPos p=i.getArgument(0);reads.add(p);return p.equals(pos)?state:Blocks.STONE.defaultBlockState();}).when(level).getBlockState(any());
   assertEquals(state.isAir()||state.is(Blocks.SNOW),b.shouldSnow(level,pos));assertEquals((state.isAir()||state.is(Blocks.SNOW))?List.of(pos,pos.below()):List.of(pos),reads);
  }
 }
}
