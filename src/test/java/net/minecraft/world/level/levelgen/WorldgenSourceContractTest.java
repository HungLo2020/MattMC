package net.minecraft.world.level.levelgen;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class WorldgenSourceContractTest {
 @SuppressWarnings("unchecked") static <T,E extends Throwable>T raise(Throwable failure)throws E{throw(E)failure;}
 static final class Bits extends LegacyRandomSource {
  final List<Integer> calls=new ArrayList<>();final IOException marker=new IOException("actual wrapper bits");Runnable callback;int failAt;
  Bits(){super(42);}
  @Override public int next(int bits){calls.add(bits);var effect=callback;callback=null;if(effect!=null)effect.run();if(calls.size()==failAt)return WorldgenSourceContractTest.<Integer,RuntimeException>raise(marker);return super.next(bits);}
 }
 static final class Words extends XoroshiroRandomSource {
  int calls;Runnable callback;final long value=0x123456789abcdef0L;
  Words(){super(42);}
  @Override public long nextLong(){calls++;var effect=callback;callback=null;if(effect!=null)effect.run();return value;}
 }
 @Test void legacyWrapperKeepsJdkDrawsAndOneCountPerActualBitRequest(){
  var wrapper=new WorldgenRandom(new LegacyRandomSource(42));var control=new java.util.Random(42);
  for(int i=0;i<64;i++){assertEquals(control.nextInt(),wrapper.nextInt());assertEquals(control.nextLong(),wrapper.nextLong());assertEquals(control.nextBoolean(),wrapper.nextBoolean());assertEquals(control.nextFloat(),wrapper.nextFloat());assertEquals(Double.doubleToRawLongBits((double)(float)control.nextDouble()),Double.doubleToRawLongBits(wrapper.nextDouble()));assertEquals((i+1)*7,wrapper.getCount());}
 }
 @Test void nonLegacyTargetReceivesOneRealWordForEveryBitRequestIncludingMaskedCounts(){
  var source=new Words();var wrapper=new WorldgenRandom(source);int calls=0;
  for(int bits:new int[]{Integer.MIN_VALUE,-65,-33,-1,0,1,26,27,31,32,33,48,63,64,65,Integer.MAX_VALUE}){assertEquals((int)(source.value >>> 64-bits),wrapper.next(bits));assertEquals(++calls,wrapper.getCount());assertEquals(calls,source.calls);}
 }
 @Test void checkedSecondDrawFailureKeepsBothCountCommitsAndTheFirstSourcePrefix(){
  var source=new Bits();var wrapper=new WorldgenRandom(source);source.failAt=2;assertSame(source.marker,assertThrows(IOException.class,wrapper::nextLong));assertEquals(2,wrapper.getCount());assertEquals(List.of(32,32),source.calls);source.failAt=0;var control=new LegacyRandomSource(42);control.next(32);assertEquals(control.nextLong(),wrapper.nextLong());assertEquals(4,wrapper.getCount());
 }
 @Test void nestedBitCallbackObservesTheCommittedOuterCountBeforeItsOwnDraw(){
  var source=new Bits();var wrapper=new WorldgenRandom(source);var control=new LegacyRandomSource(42);int nested=control.nextInt(),outer=control.nextInt();source.callback=()->{assertEquals(1,wrapper.getCount());assertEquals(nested,wrapper.nextInt());assertEquals(2,wrapper.getCount());};assertEquals(outer,wrapper.nextInt());assertEquals(2,wrapper.getCount());assertEquals(control.nextLong(),wrapper.nextLong());assertEquals(4,wrapper.getCount());
 }
 static void target(WorldgenRandom wrapper,net.minecraft.util.RandomSource value)throws Exception{var f=WorldgenRandom.class.getDeclaredField("randomSource");f.setAccessible(true);f.set(wrapper,value);}
 @Test void outerLongReadsTheRealTargetAgainAfterItsFirstWordCallback()throws Exception{
  var first=new Words();var second=new LegacyRandomSource(19);var control=new LegacyRandomSource(19);var wrapper=new WorldgenRandom(first);first.callback=()->{try{target(wrapper,second);}catch(Exception failure){throw new AssertionError(failure);}};long expected=((long)(int)(first.value>>>32)<<32)+control.next(32);assertEquals(expected,wrapper.nextLong());assertEquals(1,first.calls);assertEquals(2,wrapper.getCount());assertEquals(control.nextLong(),wrapper.nextLong());
 }
 static final class ConstructorHook extends WorldgenRandom {
  boolean caught;
  ConstructorHook(net.minecraft.util.RandomSource source){super(source);}
  @Override public synchronized void setSeed(long seed){super.setSeed(seed);try{next(32);}catch(NullPointerException expected){caught=true;}}
 }
 @Test void constructorVirtualHookKeepsCountCommittedBeforeTheNullTargetFailure(){
  var source=new ConstructorHook(new LegacyRandomSource(42));assertTrue(source.caught);assertEquals(1,source.getCount());assertEquals(new LegacyRandomSource(42).nextInt(),source.nextInt());assertEquals(2,source.getCount());
 }
 static final class ForkSource extends LegacyRandomSource {
  final net.minecraft.util.RandomSource child=new LegacyRandomSource(19);int forks;int positional;
  ForkSource(){super(42);}
  @Override public net.minecraft.util.RandomSource fork(){forks++;return child;}
  @Override public PositionalRandomFactory forkPositional(){positional++;return child.forkPositional();}
 }
 @Test void forkOperationsKeepActualTargetDispatchWithoutIncrementingWrapperCount(){
  var source=new ForkSource();var wrapper=new WorldgenRandom(source);assertSame(source.child,wrapper.fork());assertNotNull(wrapper.forkPositional());assertEquals(1,source.forks);assertEquals(1,source.positional);assertEquals(0,wrapper.getCount());
 }
 @Test void rejectedBoundAndNegativeSkipDoNotCommitCounts(){
  var source=new Bits();var wrapper=new WorldgenRandom(source);assertEquals("Bound must be positive",assertThrows(IllegalArgumentException.class,()->wrapper.nextInt(0)).getMessage());wrapper.consumeCount(-7);assertEquals(0,wrapper.getCount());assertTrue(source.calls.isEmpty());wrapper.consumeCount(5);assertEquals(5,wrapper.getCount());assertEquals(List.of(32,32,32,32,32),source.calls);
 }
 @Test void delegatedReseedDoesNotResetTheWrappersOwnAlreadyProducedGaussian(){
  var source=new WorldgenRandom(new LegacyRandomSource(42));var control=new WorldgenRandom(new LegacyRandomSource(42));assertEquals(Double.doubleToRawLongBits(control.nextGaussian()),Double.doubleToRawLongBits(source.nextGaussian()));int count=source.getCount();source.setSeed(19);control.setSeed(19);assertEquals(Double.doubleToRawLongBits(control.nextGaussian()),Double.doubleToRawLongBits(source.nextGaussian()));assertEquals(count,source.getCount());assertEquals(Double.doubleToRawLongBits(control.nextGaussian()),Double.doubleToRawLongBits(source.nextGaussian()));assertEquals(control.getCount(),source.getCount());
 }
 static final class WrapperEffects extends WorldgenRandom {
  List<String> calls=new ArrayList<>();final IOException marker=new IOException("actual wrapper seed");int seeds;
  WrapperEffects(){super(new LegacyRandomSource(42));}
  @Override public synchronized void setSeed(long seed){if(calls!=null){calls.add("S"+seed);if(++seeds==2)WorldgenSourceContractTest.<Void,RuntimeException>raise(marker);}}
  @Override public long nextLong(){calls.add("L");return 123;}
 }
 @Test void decorationSeedKeepsVirtualWrapperCallsAndTheOriginalLastSeedThrowable(){
  var source=new WrapperEffects();assertSame(source.marker,assertThrows(IOException.class,()->source.setDecorationSeed(42,Integer.MIN_VALUE,Integer.MAX_VALUE)));assertEquals(4,source.calls.size());assertEquals("S42",source.calls.get(0));assertEquals(List.of("L","L"),source.calls.subList(1,3));assertTrue(source.calls.get(3).startsWith("S"));assertEquals(0,source.getCount());
 }
 @Test void realLeafConsumesTheSameBitCountAndBackingPrefixAsVirtualWrapperDispatch(){
  for(boolean xoro:new boolean[]{false,true})for(boolean simplex:new boolean[]{false,true}){
   net.minecraft.util.RandomSource first=xoro?new XoroshiroRandomSource(42):new LegacyRandomSource(42),second=xoro?new XoroshiroRandomSource(42):new LegacyRandomSource(42);
   var actual=new WorldgenRandom(first);var virtual=new WorldgenRandom(second){};
   if(simplex){var a=new net.minecraft.world.level.levelgen.synth.SimplexNoise(actual);var b=new net.minecraft.world.level.levelgen.synth.SimplexNoise(virtual);assertEquals(a.xo,b.xo);assertEquals(a.getValue(.25,-.5,.75),b.getValue(.25,-.5,.75));}
   else{var a=new net.minecraft.world.level.levelgen.synth.ImprovedNoise(actual);var b=new net.minecraft.world.level.levelgen.synth.ImprovedNoise(virtual);assertEquals(a.xo,b.xo);assertEquals(a.noise(.25,-.5,.75),b.noise(.25,-.5,.75));}
   assertEquals(virtual.getCount(),actual.getCount());assertTrue(actual.getCount()>=262);assertEquals(second.nextLong(),first.nextLong());
  }
 }
 @Test void customLeafFailureAndReentryKeepTheRealCommittedBitPrefix(){
  for(boolean simplex:new boolean[]{false,true}){
   var backing=new Bits();var wrapper=new WorldgenRandom(backing);backing.failAt=8;
   backing.callback=()->{assertEquals(1,wrapper.getCount());wrapper.setSeed(19);};
   assertSame(backing.marker,assertThrows(IOException.class,()->{if(simplex)new net.minecraft.world.level.levelgen.synth.SimplexNoise(wrapper);else new net.minecraft.world.level.levelgen.synth.ImprovedNoise(wrapper);}));
   assertEquals(8,wrapper.getCount());assertEquals(List.of(26,27,26,27,26,27,31,31),backing.calls);
   var control=new LegacyRandomSource(19);for(int bits:backing.calls.subList(0,7))control.next(bits);backing.failAt=0;assertEquals(control.nextLong(),wrapper.nextLong());assertEquals(10,wrapper.getCount());
  }
 }

 static final class GeneratorEffect extends Xoroshiro128PlusPlus {
  int calls;Runnable callback;final long value=0x123456789abcdef0L;
  GeneratorEffect(){super(42,81);}
  @Override public long nextLong(){calls++;var action=callback;callback=null;if(action!=null)action.run();return value;}
 }
 @Test void exactXoroTargetKeepsItsActualPrivateGeneratorCallbackAndReseedBetweenBits()throws Exception{
  var source=new XoroshiroRandomSource(42);var generator=new GeneratorEffect();var field=XoroshiroRandomSource.class.getDeclaredField("randomNumberGenerator");field.setAccessible(true);field.set(source,generator);var wrapper=new WorldgenRandom(source);var control=new XoroshiroRandomSource(19);
  generator.callback=()->{assertEquals(1,wrapper.getCount());wrapper.setSeed(19);};
  long expected=((long)(int)(generator.value>>>32)<<32)+(int)(control.nextLong()>>>32);assertEquals(expected,wrapper.nextLong());assertEquals(1,generator.calls);assertEquals(2,wrapper.getCount());assertEquals(control.nextLong(),source.nextLong());
 }

}
