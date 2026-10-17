package net.minecraft.world.level.levelgen;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Gaussian kernel/count/backing/cache after real VM facade/anchor GC. */
final class NativeWorldgenGaussianLifetimeTest {
    static MethodHandle h(String group,String name,FunctionDescriptor type){return NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_"+group+"_"+name,type);}
    static final MethodHandle START=h("gaussian","draw_new_source",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
    static final MethodHandle BIND=h("gaussian","draw_bind_worldgen",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG));
    static final MethodHandle NEXT=h("gaussian","draw_next",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    static final MethodHandle END=h("gaussian","draw_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    static final MethodHandle WATCH=h("gaussian","weak_new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
    static final MethodHandle COUNT=h("gaussian","weak_count",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    static final MethodHandle UNWATCH=h("gaussian","weak_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    static final MethodHandle CWATCH=h("worldgen","weak_new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    static final MethodHandle CCOUNT=h("worldgen","weak_count",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    static final MethodHandle CUNWATCH=h("worldgen","weak_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    static Object field(Object value,Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    static void weak(List<WeakReference<?>> list,Object value){assertNotNull(value);list.add(new WeakReference<>(value));}
    record Pending(long frame,long cacheWatch,long sourceWatch,long countWatch,long cacheStrong,long sourceStrong,long countStrong,List<WeakReference<?>> facadesAndAnchors){boolean collected(){return facadesAndAnchors.stream().allMatch(value->value.refersTo(null));}}
    static Pending pending(boolean xoro,boolean deferCount)throws Throwable {
        RandomSource backing=xoro?new XoroshiroRandomSource(42):new LegacyRandomSource(42);var wrapper=new WorldgenRandom(backing);var cache=WorldgenGaussianContractTest.cache(wrapper);
        var cacheOwner=(NativeLegacySource)field(wrapper,LegacyRandomSource.class,"nativeSource");Object sourceOwner=xoro?XoroshiroRandomSource.nativeWorldgenProducer(backing):LegacyRandomSource.nativeGaussianProducer(backing);
        NativeWorldgenSource countOwner=deferCount?null:wrapper.nativeState();long source=xoro?((NativeXoroshiroSource)sourceOwner).handle():((NativeLegacySource)sourceOwner).handle();long cacheId=cacheOwner.handle();assertNotEquals(cacheId,source);
        long frame=0,cacheWatch=0,sourceWatch=0,countWatch=0;boolean success=false;
        try {
            frame=(long)START.invokeExact(cacheId,2,0L,1);assertNotEquals(0,frame);assertEquals(1,(int)BIND.invokeExact(frame,countOwner==null?0L:countOwner.handle(),xoro?2:1,source));
            cacheWatch=(long)WATCH.invokeExact(cacheId,2);sourceWatch=(long)WATCH.invokeExact(source,xoro?1:2);if(countOwner!=null)countWatch=(long)CWATCH.invokeExact(countOwner.handle());
            long cs=(long)COUNT.invokeExact(cacheWatch),ss=(long)COUNT.invokeExact(sourceWatch),ws=countWatch==0?0:(long)CCOUNT.invokeExact(countWatch);assertTrue(cs>1&&ss>1);if(!deferCount)assertTrue(ws>1);
            var refs=new ArrayList<WeakReference<?>>();weak(refs,backing);weak(refs,wrapper);weak(refs,cache);weak(refs,cacheOwner);weak(refs,sourceOwner);weak(refs,field(cacheOwner,NativeLegacySource.class,"owner"));weak(refs,field(sourceOwner,xoro?NativeXoroshiroSource.class:NativeLegacySource.class,"owner"));if(countOwner!=null){weak(refs,countOwner);weak(refs,field(countOwner,NativeWorldgenSource.class,"owner"));}
            var result=new Pending(frame,cacheWatch,sourceWatch,countWatch,cs,ss,ws,refs);success=true;return result;
        }finally {
            try{if(!success){if(frame!=0)END.invokeExact(frame);if(cacheWatch!=0)UNWATCH.invokeExact(cacheWatch);if(sourceWatch!=0)UNWATCH.invokeExact(sourceWatch);if(countWatch!=0)CUNWATCH.invokeExact(countWatch);}}
            finally{Reference.reachabilityFence(backing);Reference.reachabilityFence(wrapper);Reference.reachabilityFence(cache);Reference.reachabilityFence(cacheOwner);Reference.reachabilityFence(sourceOwner);Reference.reachabilityFence(countOwner);}
        }
    }
    static void verify(boolean xoro,boolean deferCount)throws Throwable {
        var pending=pending(xoro,deferCount);long frame=pending.frame;boolean safe=true;
        try {
            long deadline=System.nanoTime()+5_000_000_000L;
            while(true){
                long cache=(long)COUNT.invokeExact(pending.cacheWatch),source=(long)COUNT.invokeExact(pending.sourceWatch),count=pending.countWatch==0?1:(long)CCOUNT.invokeExact(pending.countWatch);
                if(cache==0||source==0||count==0){safe=false;fail("actual Gaussian pending consumer lost live data before use");}
                if(pending.collected()&&cache==pending.cacheStrong-1&&source==pending.sourceStrong-1&&(deferCount||count==pending.countStrong-1))break;
                assertTrue(System.nanoTime()<deadline,"actual facade/arena base leases did not retire within original5s");System.gc();Thread.sleep(20);
            }
            var words=MemorySegment.ofAddress(frame).reinterpret(16).asReadOnly();assertEquals(1,(int)NEXT.invokeExact(frame,0L));
            if(deferCount){
                assertEquals(4,words.getAtIndex(ValueLayout.JAVA_LONG,0));assertEquals(1,(long)COUNT.invokeExact(pending.sourceWatch));assertEquals(1,(long)COUNT.invokeExact(pending.cacheWatch));
                END.invokeExact(frame);frame=0;assertEquals(0,(long)COUNT.invokeExact(pending.sourceWatch));assertEquals(0,(long)COUNT.invokeExact(pending.cacheWatch));
            }else{
                assertEquals(2,words.getAtIndex(ValueLayout.JAVA_LONG,0));assertEquals(0,(long)COUNT.invokeExact(pending.sourceWatch));assertEquals(0,(long)CCOUNT.invokeExact(pending.countWatch));assertEquals(1,(long)COUNT.invokeExact(pending.cacheWatch));
                double radius=Double.longBitsToDouble(words.getAtIndex(ValueLayout.JAVA_LONG,1));assertTrue(radius>0&&radius<1);assertEquals(0,(int)NEXT.invokeExact(frame,Double.doubleToRawLongBits(Math.log(radius))));assertTrue(Double.isFinite(Double.longBitsToDouble(words.getAtIndex(ValueLayout.JAVA_LONG,1))));
                END.invokeExact(frame);frame=0;assertEquals(0,(long)COUNT.invokeExact(pending.cacheWatch));
            }
        }finally{try{if(frame!=0&&safe)END.invokeExact(frame);}finally{UNWATCH.invokeExact(pending.cacheWatch);UNWATCH.invokeExact(pending.sourceWatch);if(pending.countWatch!=0)CUNWATCH.invokeExact(pending.countWatch);}}
    }
    @Test void actualLegacyKernelCompletesAfterCountBackingAndCacheFacadesAndAnchorsCollect()throws Throwable{verify(false,false);}
    @Test void actualXoroshiroKernelCompletesAfterCountBackingAndCacheFacadesAndAnchorsCollect()throws Throwable{verify(true,false);}
    @Test void abandonedGenuineCountRequestOwnsBackingUntilItsActualFinalNativeRelease()throws Throwable{verify(false,true);}
}
