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
/** Derived canonical source consumed after actual factory/source facades and anchors collect. */
final class NativePositionalLifetimeTest {
    static MethodHandle h(String group,String name,FunctionDescriptor type){return NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_"+group+"_"+name,type);}
    static final MethodHandle FW=h("positional","weak_new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    static final MethodHandle FC=h("positional","weak_count",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    static final MethodHandle FU=h("positional","weak_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    static final MethodHandle SW=h("gaussian","weak_new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
    static final MethodHandle SC=h("gaussian","weak_count",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    static final MethodHandle SU=h("gaussian","weak_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    static final MethodHandle START=h("gaussian","draw_new_source",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
    static final MethodHandle NEXT=h("gaussian","draw_next",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    static final MethodHandle END=h("gaussian","draw_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    static Object field(Object value,String name)throws Exception{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    static void weak(List<WeakReference<?>> refs,Object value){assertNotNull(value);refs.add(new WeakReference<>(value));}
    record Pending(long frame,long factoryWatch,long sourceWatch,long sourceStrong,List<WeakReference<?>> refs){boolean collected(){return refs.stream().allMatch(value->value.refersTo(null));}}
    static Pending pending(boolean xoro)throws Throwable {
        PositionalRandomFactory factory=xoro?new XoroshiroRandomSource.XoroshiroPositionalRandomFactory(42,42):new LegacyRandomSource.LegacyPositionalRandomFactory(12345);
        NativePositionalFactory factoryOwner=xoro?((XoroshiroRandomSource.XoroshiroPositionalRandomFactory)factory).nativeFactory():((LegacyRandomSource.LegacyPositionalRandomFactory)factory).nativeFactory();
        RandomSource child=factory.fromSeed(42);
        Object sourceOwner=xoro?XoroshiroRandomSource.nativeWorldgenProducer(child):LegacyRandomSource.nativeGaussianProducer(child);
        MarsagliaPolarGaussian cache=xoro?((XoroshiroRandomSource)child).nativeGaussian():((LegacyRandomSource)child).nativeGaussian();
        long source=xoro?((NativeXoroshiroSource)sourceOwner).handle():((NativeLegacySource)sourceOwner).handle();
        int kind=xoro?1:2;long frame=0,fw=0,sw=0;boolean success=false;
        try {
            fw=(long)FW.invokeExact(factoryOwner.handle());sw=(long)SW.invokeExact(source,kind);
            assertEquals(1,(long)FC.invokeExact(fw));assertEquals(1,(long)SC.invokeExact(sw),"one actual source lease must belong to its VM anchor");
            frame=(long)START.invokeExact(source,kind,source,kind);assertNotEquals(0,frame);
            long strong=(long)SC.invokeExact(sw);assertEquals(3,strong,"real source base plus Gaussian cache/producer");
            var refs=new ArrayList<WeakReference<?>>();weak(refs,factory);weak(refs,factoryOwner);weak(refs,field(factoryOwner,"owner"));weak(refs,child);weak(refs,cache);weak(refs,sourceOwner);weak(refs,field(sourceOwner,"owner"));
            var result=new Pending(frame,fw,sw,strong,refs);success=true;return result;
        }finally {
            try{if(!success){if(frame!=0)END.invokeExact(frame);if(fw!=0)FU.invokeExact(fw);if(sw!=0)SU.invokeExact(sw);}}
            finally{Reference.reachabilityFence(factory);Reference.reachabilityFence(factoryOwner);Reference.reachabilityFence(child);Reference.reachabilityFence(cache);Reference.reachabilityFence(sourceOwner);}
        }
    }
    static void verify(boolean xoro)throws Throwable {
        var pending=pending(xoro);long frame=pending.frame;boolean safe=true;
        try {
            long deadline=System.nanoTime()+5_000_000_000L;
            while(true){
                long source=(long)SC.invokeExact(pending.sourceWatch);
                if(source==0){safe=false;fail("actual derived source disappeared before its native consumer");}
                if(pending.collected()&&(long)FC.invokeExact(pending.factoryWatch)==0&&source==pending.sourceStrong-1)break;
                assertTrue(System.nanoTime()<deadline,"actual factory/source facade and arena base leases did not retire within original5s");System.gc();Thread.sleep(20);
            }
            var words=MemorySegment.ofAddress(frame).reinterpret(16).asReadOnly();assertEquals(1,(int)NEXT.invokeExact(frame,0L));assertEquals(2,words.getAtIndex(ValueLayout.JAVA_LONG,0));
            assertEquals(0,(long)FC.invokeExact(pending.factoryWatch));assertEquals(1,(long)SC.invokeExact(pending.sourceWatch),"only actual aliased cache survives the producer's last draw");
            double radius=Double.longBitsToDouble(words.getAtIndex(ValueLayout.JAVA_LONG,1));assertTrue(radius>0&&radius<1);
            assertEquals(0,(int)NEXT.invokeExact(frame,Double.doubleToRawLongBits(Math.log(radius))));assertTrue(Double.isFinite(Double.longBitsToDouble(words.getAtIndex(ValueLayout.JAVA_LONG,1))));assertEquals(1,(long)SC.invokeExact(pending.sourceWatch));
            END.invokeExact(frame);frame=0;assertEquals(0,(long)SC.invokeExact(pending.sourceWatch));assertEquals(0,(long)FC.invokeExact(pending.factoryWatch));
        }finally{try{if(frame!=0&&safe)END.invokeExact(frame);}finally{FU.invokeExact(pending.factoryWatch);SU.invokeExact(pending.sourceWatch);}}
    }
    @Test void actualLegacyDerivedSourceFinishesAfterFactoryAndAllFacadeAnchorsCollect()throws Throwable{verify(false);}
    @Test void actualXoroshiroDerivedSourceFinishesAfterFactoryAndAllFacadeAnchorsCollect()throws Throwable{verify(true);}
}
