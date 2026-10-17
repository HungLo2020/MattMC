package net.minecraft.world.level.levelgen;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class LegacyLeafDispatchContractTest {
 static final class Effects extends LegacyRandomSource {
  final List<String> calls=new ArrayList<>();final IOException marker=new IOException("Legacy actual leaf callback");int failAt;Runnable callback;
  Effects(){super(42);}
  @Override public double nextDouble(){calls.add("D");Runnable action=callback;callback=null;if(action!=null)action.run();return .25;}
  @Override public int nextInt(int bound){calls.add("I"+bound);if(calls.size()==failAt)return LegacySourceContractTest.<Integer,RuntimeException>raise(marker);return 0;}
 }
 @Test void actualLegacySubclassLeafKeepsDoubleBoundedDispatchAndCallbackReseeding(){
  for(boolean simplex:new boolean[]{false,true}){
   var source=new Effects();source.callback=()->source.setSeed(19);
   double x=simplex?new net.minecraft.world.level.levelgen.synth.SimplexNoise(source).xo:new net.minecraft.world.level.levelgen.synth.ImprovedNoise(source).xo;
   assertEquals(64.0,x);assertEquals(259,source.calls.size());assertEquals(List.of("D","D","D","I256","I255"),source.calls.subList(0,5));assertEquals("I1",source.calls.get(258));assertEquals(new LegacyRandomSource(19).nextLong(),source.nextLong());
  }
 }
 @Test void actualLegacySubclassLeafKeepsCheckedFailureIdentityAndCommittedEffectPrefix(){
  for(boolean simplex:new boolean[]{false,true}){
   var source=new Effects();source.failAt=5;
   assertSame(source.marker,assertThrows(IOException.class,()->{if(simplex)new net.minecraft.world.level.levelgen.synth.SimplexNoise(source);else new net.minecraft.world.level.levelgen.synth.ImprovedNoise(source);}));
   assertEquals(List.of("D","D","D","I256","I255"),source.calls);assertEquals(new LegacyRandomSource(42).nextLong(),source.nextLong());
  }
 }
}
