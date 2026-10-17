package net.minecraft.world.level;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;

public class TicketRePinProbe {
    static final CountDownLatch SELECTED=new CountDownLatch(1), RESUME=new CountDownLatch(1), ATTEMPT=new CountDownLatch(1), PINNED=new CountDownLatch(1);
    static volatile boolean armed;
    static volatile int target=-1, attempted=-1;
    static void waitFor(CountDownLatch latch) {
        try {if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("controlled re-pin phase timed out");}
        catch(InterruptedException e){throw new AssertionError(e);}
    }
    public static void selected(long[] cells,int count){
        if(!armed || !Thread.currentThread().getName().equals("drain"))return;
        for(int i=0;i<count;i++)if(cells[i]==target){SELECTED.countDown();waitFor(RESUME);return;}
        throw new AssertionError("native drain did not select old cell");
    }
    public static void attempting(int cell){
        if(armed && Thread.currentThread().getName().equals("re-store")){attempted=cell;ATTEMPT.countDown();}
    }
    public static void pinned(){if(armed && Thread.currentThread().getName().equals("re-store"))PINNED.countDown();}
    record Retired(WeakReference<MemorySegment> owner,long address,int cell){}
    static Retired replaceResource(Ticket ticket){
        MemorySegment old=Ticket.prepareNativeCounter(ticket,ticket.unboundTicksLeft());
        Retired retired=new Retired(new WeakReference<>(old),old.address(),old.get(ValueLayout.JAVA_INT,28));
        Ticket replacement=new Ticket(TicketType.PORTAL,33);
        MemorySegment next=Ticket.prepareNativeCounter(replacement,replacement.unboundTicksLeft());
        ticket.bindCell(next.get(ValueLayout.JAVA_INT,28),next.address());
        Reference.reachabilityFence(replacement);Reference.reachabilityFence(next);
        return retired;
    }
    record Stored(TicketStorage storage,WeakReference<Ticket> ticket){}
    static Stored arrange(boolean recycle)throws Exception{
        TicketStorage first=new TicketStorage(),second=new TicketStorage();
        Ticket original=new Ticket(TicketType.PORTAL,33);first.addTicket(7L,original);
        int old=original.boundCell();
        if(recycle){
            Retired retired=replaceResource(original);
            // The first storage's entry keeps this native cell allocated while
            // the replaced actual resource retires; no JVM heap is accessed.
            MemorySegment entryLease=MemorySegment.ofAddress(retired.address()).reinterpret(48);
            for(int i=0;i<160 && (retired.owner().get()!=null || entryLease.get(ValueLayout.JAVA_INT,32)!=0);i++){System.gc();Thread.sleep(20);}
            if(retired.owner().get()!=null || entryLease.get(ValueLayout.JAVA_INT,32)!=0)throw new AssertionError("old managed resource did not retire before drain");
            if(old!=retired.cell())throw new AssertionError("wrong retained native incarnation");
        }
        Ticket restored=recycle?new Ticket(TicketType.PORTAL,33):original;
        WeakReference<Ticket> weak=new WeakReference<>(restored);
        AtomicReference<Throwable> error=new AtomicReference<>();target=old;armed=true;
        Thread draining=new Thread(()->{try{first.removeTicket(7L,new Ticket(TicketType.PORTAL,33));}catch(Throwable e){error.set(e);}},"drain");
        Thread storing=new Thread(()->{try{second.addTicket(9L,restored);}catch(Throwable e){error.set(e);}},"re-store");
        draining.start();waitFor(SELECTED);storing.start();waitFor(ATTEMPT);
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(PINNED.getCount()!=0 && storing.getState()!=Thread.State.BLOCKED && System.nanoTime()<until)Thread.sleep(1);
        boolean blocked=storing.getState()==Thread.State.BLOCKED;
        if(!blocked && PINNED.getCount()!=0)throw new AssertionError("re-pin neither blocked nor completed");
        RESUME.countDown();draining.join(10000);storing.join(10000);armed=false;
        if(draining.isAlive() || storing.isAlive())throw new AssertionError("controlled threads did not complete");
        if(error.get()!=null)throw new AssertionError("controlled worker failed",error.get());
        if(attempted!=old)throw new AssertionError("re-store did not target selected/recycled cell: "+attempted+" vs "+old);
        if(second.getTickets(9L).get(0)!=restored)throw new AssertionError("wrong published ticket identity");
        System.out.println("CONTROLLED_REPIN recycle="+recycle+" blocked="+blocked+" cell="+old);
        return new Stored(second,weak);
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        Stored result=arrange(Boolean.parseBoolean(args[0]));
        for(int i=0;i<40;i++){System.gc();Thread.sleep(20);}
        Ticket ticket=result.ticket().get();
        if(ticket==null)throw new AssertionError("stale drain unpin erased the newer required strong root");
        if(result.storage().getTickets(9L).get(0)!=ticket)throw new AssertionError("required stored ticket lost after GC");
        ticket.decreaseTicksLeft();
        System.out.println("REPUBLISHED_ROOT_PASS recycle="+args[0]);Reference.reachabilityFence(result);
    }
}
