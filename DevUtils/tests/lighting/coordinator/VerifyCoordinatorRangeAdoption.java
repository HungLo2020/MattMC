package net.minecraft.world.level.lighting;

import java.lang.foreign.MemorySegment;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

/** Actual scoped staged child cancellation with modeled Java wrapper failure.
 * No heap exhaustion or production pause hook is injected. */
public final class VerifyCoordinatorRangeAdoption {
    static Object field(Object owner,String name)throws Exception{
        var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    static WeakReference<?> exercise()throws Exception{
        var storage=new BlockLightSectionStorage(null);var owner=storage.coordinator;
        for(int i=0;i<8;i++)storage.changedSections.add(i);
        var iterator=storage.changedSections.spliterator();Object range=null;
        for(var f:iterator.getClass().getDeclaredFields())if(f.getType().getSimpleName().equals("RangeView")){f.setAccessible(true);range=f.get(iterator);}
        if(range==null)throw new AssertionError("Actual native range wrapper missing");
        Class<?> factory=null;for(Class<?> type:NativeLightCoordinator.class.getDeclaredClasses())if(type.getSimpleName().equals("SplitFactory"))factory=type;
        if(factory==null)throw new AssertionError("Actual split factory missing");
        Object[] staged={null};Error marker=new AssertionError("modeled wrapper allocation failure");
        Object fail=Proxy.newProxyInstance(factory.getClassLoader(),new Class<?>[]{factory},(proxy,method,args)->{staged[0]=args[0];throw marker;});
        var split=NativeLightCoordinator.class.getDeclaredMethod("childRange",range.getClass(),factory);split.setAccessible(true);
        long size=iterator.estimateSize();int flags=iterator.characteristics();
        for(int i=0;i<2;i++){
            try{split.invoke(owner,range,fail);throw new AssertionError("Missing modeled failure");}
            catch(InvocationTargetException failure){if(failure.getCause()!=marker)throw new AssertionError("Original failure replaced",failure);}
            if(iterator.estimateSize()!=size||iterator.characteristics()!=flags)throw new AssertionError("Donor committed before wrapper adoption");
            Object release=field(staged[0],"release");
            if(!((AtomicBoolean)field(release,"claimed")).get())throw new AssertionError("Staged native child was not cancelled immediately");
            // Replay eventual cleanup while the cancelled Java wrapper is held:
            // the same once action must never release its raw pointer again.
            @SuppressWarnings("unchecked") var action=(java.util.function.Consumer<MemorySegment>)release;
            action.accept(MemorySegment.NULL);staged[0]=null;
        }
        int[] count={0};iterator.forEachRemaining((java.util.function.LongConsumer)value->count[0]++);
        if(count[0]!=8)throw new AssertionError("Cancelled split consumed donor entries");
        Reference.reachabilityFence(iterator);Reference.reachabilityFence(storage);
        return new WeakReference<>(owner);
    }
    public static void main(String[] args)throws Exception{
        long before=NativeLightCoordinator.liveForVerification();var owner=exercise();
        for(int i=0;i<200&&(owner.get()!=null||NativeLightCoordinator.liveForVerification()!=before);i++){System.gc();Thread.sleep(10);}
        if(owner.get()!=null||NativeLightCoordinator.liveForVerification()!=before)throw new AssertionError("Staged/root range retained actual storage");
        System.out.println("PASS actual staged split cancellation: modeled wrapper failures preserve donor/error, shared cleanup action claimed once,8 entries remain, actual storage returns to baseline; no real heap exhaustion");
    }
}
