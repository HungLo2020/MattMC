package net.minecraft.world.level.lighting;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual scoped release action/JDK arena behavior with counter handles.
 * Post-registration failure ordering is modeled, not actual heap exhaustion. */
public final class VerifyCoordinatorAdoption {
    static final AtomicInteger releases=new AtomicInteger();
    static void release(MemorySegment pointer){if(pointer.address()!=0x117)throw new AssertionError();releases.incrementAndGet();}
    static void expect(int expected){if(releases.get()!=expected)throw new AssertionError("releases="+releases.get()+" expected="+expected);}
    public static void main(String[] args) throws Throwable {
        var raw=MemorySegment.ofAddress(0x117);
        var method=MethodHandles.lookup().findStatic(VerifyCoordinatorAdoption.class,"release",MethodType.methodType(void.class,MemorySegment.class));
        try(var arena=Arena.ofConfined()){NativeLightingMap.adopt(raw,1,method,arena);expect(0);}
        expect(1);
        try(var arena=Arena.ofConfined()){
            try{NativeLightingMap.adopt(raw,-1,method,arena);throw new AssertionError("negative length admitted");}
            catch(IllegalArgumentException expected){expect(2);}
        }
        expect(2);
        var closed=Arena.ofConfined();closed.close();
        try{NativeLightingMap.adopt(raw,1,method,closed);throw new AssertionError("closed arena admitted");}
        catch(IllegalStateException expected){expect(3);}
        for(boolean cleanupFirst:new boolean[]{false,true}){
            var action=new NativeLightingMap.ReleaseOnce(method,raw);
            var arena=Arena.ofConfined();
            raw.reinterpret(1,arena,action);
            Error original=new AssertionError("modeled failure after cleanup registration");
            try{throw original;}
            catch(Error failure){
                if(failure!=original)throw new AssertionError("error replaced");
                if(cleanupFirst)arena.close();
                action.accept(raw);
            }finally{if(!cleanupFirst)arena.close();}
        }
        expect(5);
        var action=new NativeLightingMap.ReleaseOnce(method,raw);
        var start=new CountDownLatch(1);
        Runnable race=()->{try{start.await();action.accept(raw);}catch(InterruptedException failure){throw new AssertionError(failure);}};
        var left=new Thread(race);var right=new Thread(race);left.start();right.start();start.countDown();left.join();right.join();
        expect(6);
        System.out.println("PASS adoption: actual arena success/invalid-length/closed-arena; modeled post-registration cleanup/rollback orders and concurrent claim; six exact releases; no real heap exhaustion");
    }
}
