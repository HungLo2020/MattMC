package net.minecraft.world.level.levelgen;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.*;
import net.minecraft.util.NativeLibraryLoader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Actual source-backed leaf/draw kernel after wrapper and arena-anchor collection. */
final class NativeWorldgenLifetimeTest {
 static MethodHandle h(String name,FunctionDescriptor type){return NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_worldgen_"+name,type);}
 static final MethodHandle NEW=h("kernel_new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG));
 static final MethodHandle NEXT=h("kernel_next",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
 static final MethodHandle END=h("kernel_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
 static final MethodHandle WATCH=h("weak_new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
 static final MethodHandle COUNT=h("weak_count",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
 static final MethodHandle UNWATCH=h("weak_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
 static final MethodHandle VALUE=h("count",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG));
 static final MethodHandle SOURCE_WATCH=NativeGaussianLifetimeTest.WATCH,SOURCE_COUNT=NativeGaussianLifetimeTest.COUNT,SOURCE_UNWATCH=NativeGaussianLifetimeTest.UNWATCH;
 record Pending(long kernel,long owner,long countWatch,long sourceWatch,long countStrong,long sourceStrong,WeakReference<WorldgenRandom> wrapper,WeakReference<LegacyRandomSource> source,WeakReference<NativeWorldgenSource> countFacade,WeakReference<NativeLegacySource> sourceFacade){
  boolean collected(){return wrapper.refersTo(null)&&source.refersTo(null)&&countFacade.refersTo(null)&&sourceFacade.refersTo(null);}
 }
 static Pending pending()throws Throwable{
  var source=new LegacyRandomSource(42);var wrapper=new WorldgenRandom(source);var owner=wrapper.nativeState();var backing=LegacyRandomSource.nativeGaussianProducer(source);long kernel=0,cw=0,sw=0;boolean success=false;
  try{
   kernel=(long)NEW.invokeExact(owner.handle(),1,backing.handle());assertNotEquals(0,kernel);
   cw=(long)WATCH.invokeExact(owner.handle());sw=(long)SOURCE_WATCH.invokeExact(backing.handle(),2);
   long cs=(long)COUNT.invokeExact(cw),ss=(long)SOURCE_COUNT.invokeExact(sw);assertTrue(cs>1&&ss>1);
   var result=new Pending(kernel,owner.handle(),cw,sw,cs,ss,new WeakReference<>(wrapper),new WeakReference<>(source),new WeakReference<>(owner),new WeakReference<>(backing));success=true;return result;
  }finally{
   try{if(!success){if(kernel!=0)END.invokeExact(kernel);if(cw!=0)UNWATCH.invokeExact(cw);if(sw!=0)SOURCE_UNWATCH.invokeExact(sw);}}
   finally{Reference.reachabilityFence(wrapper);Reference.reachabilityFence(source);Reference.reachabilityFence(owner);Reference.reachabilityFence(backing);}
  }
 }
 @Test void actualNativeKernelKeepsCountAndBackingAfterTheirVmFacadesAndAnchorsRetire()throws Throwable{
  var pending=pending();long kernel=pending.kernel;boolean safe=true;
  try{
   long deadline=System.nanoTime()+5_000_000_000L;
   while(true){
    long count=(long)COUNT.invokeExact(pending.countWatch),source=(long)SOURCE_COUNT.invokeExact(pending.sourceWatch);
    if(count==0||source==0){safe=false;fail("actual pending kernel lost its count or backing data");}
    if(pending.collected()&&count==pending.countStrong-1&&source==pending.sourceStrong-1)break;
    assertTrue(System.nanoTime()<deadline,"actual count/source arena bases did not retire within5s");System.gc();Thread.sleep(20);
   }
   long value=(long)NEXT.invokeExact(kernel,32);assertEquals(0,value>>>32);assertEquals(-1170105035,(int)value);assertEquals(1,(int)VALUE.invokeExact(pending.owner));
   END.invokeExact(kernel);kernel=0;assertEquals(0,(long)COUNT.invokeExact(pending.countWatch));assertEquals(0,(long)SOURCE_COUNT.invokeExact(pending.sourceWatch));
  }finally{try{if(kernel!=0&&safe)END.invokeExact(kernel);}finally{UNWATCH.invokeExact(pending.countWatch);SOURCE_UNWATCH.invokeExact(pending.sourceWatch);}}
 }
}
