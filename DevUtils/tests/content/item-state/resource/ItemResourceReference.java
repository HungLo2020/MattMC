import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** One original complete transfer, scoped requested-heap accounting and actual
 * retained public-facade retirement. Instrumentation is never latency evidence. */
public final class ItemResourceReference {
    static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    static volatile long sink;
    static Method handle,live;
    static class Sample {
        int tag;long[] atEnd;long nativeHandle;
        WeakReference<ItemStack> snapshot,inserted,source;
        WeakReference<Object> menu;
        ItemStack retained;
    }
    static long handle(ItemStack value)throws Exception {
        var field=ItemStack.class.getDeclaredField("state");field.setAccessible(true);
        return (long)handle.invoke(null,field.get(value));
    }
    static boolean live(long value)throws Exception{return (boolean)live.invoke(null,value);}
    static Sample operation(boolean retain)throws Throwable {
        var fixture=new StockChestTransferTimingReference.Fixture("empty");fixture.reset();
        int tag=ItemNativeAllocation.begin();ItemStack returned;
        long[] values;
        try{returned=fixture.menu.quickMoveStack(null,27);}
        finally{values=ItemNativeAllocation.end(tag);}
        var inserted=fixture.chest.getItem(0);
        if(returned.getCount()!=11||returned.getItem()!=Items.STICK||inserted.getCount()!=11||inserted.getItem()!=Items.STICK||fixture.source.getCount()!=0||returned==inserted)
            throw new AssertionError("Original complete empty-destination transfer changed");
        sink^=fixture.consume(returned);
        var sample=new Sample();sample.tag=tag;sample.atEnd=values;sample.nativeHandle=handle(returned);
        sample.snapshot=new WeakReference<>(returned);sample.inserted=new WeakReference<>(inserted);
        sample.source=new WeakReference<>(fixture.source);sample.menu=new WeakReference<Object>(fixture.menu);
        if(retain)sample.retained=returned;
        Reference.reachabilityFence(fixture);return sample;
    }
    static boolean ordinaryRootsCleared(List<Sample> samples){
        for(int i=0;i<samples.size();i++){
            var s=samples.get(i);
            if(s.menu.get()!=null||s.source.get()!=null||s.inserted.get()!=null||(i!=0&&s.snapshot.get()!=null))return false;
        }
        return true;
    }
    static boolean released(List<Sample> samples)throws Throwable {
        for(var s:samples)if(s.snapshot.get()!=null||live(s.nativeHandle)||ItemNativeAllocation.snapshot(s.tag)[11]!=0)return false;
        return true;
    }
    static boolean otherNativeOwnersReleased(List<Sample> samples)throws Throwable {
        for(int i=1;i<samples.size();i++){var s=samples.get(i);if(live(s.nativeHandle)||ItemNativeAllocation.snapshot(s.tag)[11]!=0)return false;}
        return true;
    }
    interface Check {boolean done()throws Throwable;}
    static void observe(Check condition)throws Throwable {
        long end=System.nanoTime()+10_000_000_000L;
        do{System.gc();Thread.sleep(50);if(condition.done())return;}while(System.nanoTime()<end);
        throw new AssertionError("Bounded normal GC/Cleaner/reclaimer observation did not reach the required state");
    }
    static void requireHeld(Sample sample)throws Exception {
        if(sample.retained==null||sample.retained.getCount()!=11||sample.retained.getItem()!=Items.STICK||sample.snapshot.get()!=sample.retained||!live(sample.nativeHandle))
            throw new AssertionError("Actual retained public snapshot/native owner missing");
        Reference.reachabilityFence(sample.retained);
    }
    static List<Map<String,Object>> snapshots(List<Sample> samples)throws Throwable {
        var rows=new ArrayList<Map<String,Object>>();
        for(var s:samples){var row=new LinkedHashMap<String,Object>();row.put("native",ItemNativeAllocation.sample(s.tag,ItemNativeAllocation.snapshot(s.tag)));
            row.put("snapshotReachable",s.snapshot.get()!=null);row.put("sourceReachable",s.source.get()!=null);row.put("insertedReachable",s.inserted.get()!=null);
            row.put("menuReachable",s.menu.get()!=null);row.put("snapshotHandleLive",live(s.nativeHandle));rows.add(row);}
        return rows;
    }
    static void write(Path out,String name,Object value)throws Exception{Files.writeString(out.resolve(name),JSON.toJson(value));}
    public static void main(String[] args)throws Throwable {
        Path out=Path.of(args[0]);Files.createDirectories(out);
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        Class<?> state=Class.forName("net.minecraft.world.item.NativeItemState"),stack=Class.forName("net.minecraft.world.item.NativeItemState$Stack");
        handle=state.getDeclaredMethod("handleForTest",stack);handle.setAccessible(true);
        live=state.getDeclaredMethod("liveForTest",long.class);live.setAccessible(true);
        write(out,"allocator-controls.json",ItemNativeAllocation.control());
        // Grow normal owner-table capacity outside scopes and retire warmup roots.
        var warm=new ArrayList<StockChestTransferTimingReference.Fixture>();
        for(int i=0;i<96;i++)warm.add(new StockChestTransferTimingReference.Fixture("empty"));
        for(int i=0;i<4096;i++){var f=warm.get(i%warm.size());f.reset();sink^=f.consume(f.menu.quickMoveStack(null,27));}
        warm=null;for(int i=0;i<8;i++){System.gc();Thread.sleep(50);}
        var samples=new ArrayList<Sample>();for(int i=0;i<32;i++)samples.add(operation(i==0));
        var operations=new ArrayList<Map<String,Object>>();for(var s:samples)operations.add(ItemNativeAllocation.sample(s.tag,s.atEnd));write(out,"operations.json",operations);
        observe(()->ordinaryRootsCleared(samples)&&otherNativeOwnersReleased(samples));requireHeld(samples.get(0));
        long heldRust=ItemNativeAllocation.snapshot(samples.get(0).tag)[11];
        if(heldRust==0)throw new AssertionError("Retained real facade did not retain any tagged actual Rust allocation");
        write(out,"retirement-held.json",snapshots(samples));
        samples.get(0).retained=null;observe(()->released(samples));write(out,"retirement-released.json",snapshots(samples));
        for(var s:samples){var end=ItemNativeAllocation.snapshot(s.tag);if(end[6]!=0||end[13]!=0||end[20]!=0)throw new AssertionError("Measured allocation failure");}
        write(out,"complete.json",Map.of("passed",true,"operations",32,"heldRustRequestedBytes",heldRust,"scope","Original complete forward_empty operation only; requested libc heap, not RSS/mmap/whole-process totals; no latency claim; normal GC/Cleaner/reclaimer observation"));
        System.out.println("ITEM_RESOURCE_PASS heldRust="+heldRust+" operations=32 sink="+sink);
    }
}
