import java.lang.ref.Reference;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** S0034 pending contents: scoped requested-heap accounting for each pending
 * producer, retirement while the owning container stays alive, a held control,
 * and a bounded churn of process RSS/heap. Instrumentation is never latency evidence. */
public final class ItemPendingResourceReference {
    static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    static volatile long sink;
    static Method chestItems,pendingCount;
    static java.lang.invoke.MethodHandle COUNTER;
    static long counter(long which)throws Throwable{return (long)COUNTER.invokeExact(which);}
    static int pending(Object slots)throws Exception{return (int)pendingCount.invoke(slots);}
    static Object chestSlots(StockChestTransferTimingReference.Fixture f)throws Exception{return chestItems.invoke(f.chest);}
    static Object inventorySlots(Inventory inventory)throws Exception{return inventorySlots.get(inventory);}
    static java.lang.reflect.Field inventorySlots;
    static ItemStack damaged(){var sword=new ItemStack(Items.DIAMOND_SWORD);sword.setDamageValue(1);return sword;}

    /** One pending producer under a tag; retire() empties the row while its owner stays reachable. */
    record Operation(String name,int tag,long[] atEnd,Object owner,Runnable retire,Object slots) {}
    interface Producer {Operation run()throws Throwable;}
    /** Only measured operations take an observer tag (the observer has 16,384). */
    static boolean TAGGED;
    static final Map<String,Producer> PRODUCERS=new LinkedHashMap<>();
    static {
        PRODUCERS.put("quick_move",()->{
            var f=new StockChestTransferTimingReference.Fixture("empty");f.reset();int tag=TAGGED?ItemNativeAllocation.begin():0;long[] v=null;
            try{sink^=f.menu.quickMoveStack(null,27).getCount();}finally{if(tag!=0)v=ItemNativeAllocation.end(tag);}
            return new Operation("quick_move",tag,v,f,()->f.chest.setItem(0,ItemStack.EMPTY),chestSlots(f));});
        PRODUCERS.put("quick_move_materialized",()->{
            var f=new StockChestTransferTimingReference.Fixture("empty");f.reset();int tag=TAGGED?ItemNativeAllocation.begin():0;long[] v=null;
            try{sink^=f.menu.quickMoveStack(null,27).getCount();}finally{if(tag!=0)v=ItemNativeAllocation.end(tag);}
            sink^=f.chest.getItem(0).getCount();
            return new Operation("quick_move_materialized",tag,v,f,()->f.chest.setItem(0,ItemStack.EMPTY),chestSlots(f));});
        PRODUCERS.put("pickup_empty",()->{
            var inv=new Inventory(null,new EntityEquipment());var source=new ItemStack(Items.STICK,11);int tag=TAGGED?ItemNativeAllocation.begin():0;long[] v=null;
            try{sink^=inv.add(source)?1:0;}finally{if(tag!=0)v=ItemNativeAllocation.end(tag);}
            return new Operation("pickup_empty",tag,v,inv,()->inv.setItem(0,ItemStack.EMPTY),inventorySlots(inv));});
        PRODUCERS.put("pickup_damaged",()->{
            var inv=new Inventory(null,new EntityEquipment());var source=damaged();int tag=TAGGED?ItemNativeAllocation.begin():0;long[] v=null;
            try{sink^=inv.add(source)?1:0;}finally{if(tag!=0)v=ItemNativeAllocation.end(tag);}
            return new Operation("pickup_damaged",tag,v,inv,inv::clearContent,inventorySlots(inv));});
    }
    static void observe(String what,Check condition)throws Throwable {
        long end=System.nanoTime()+10_000_000_000L;
        do{if(condition.done())return;System.gc();Thread.sleep(50);}while(System.nanoTime()<end);
        throw new AssertionError("Bounded GC/Cleaner observation did not reach: "+what);
    }
    interface Check {boolean done()throws Throwable;}
    static long live(int tag)throws Throwable{return ItemNativeAllocation.snapshot(tag)[11];}
    static long rss()throws Exception{
        for(String line:Files.readAllLines(Path.of("/proc/self/status")))if(line.startsWith("VmRSS:"))return Long.parseLong(line.replaceAll("[^0-9]",""))*1024;
        throw new AssertionError("VmRSS unavailable");
    }
    static void write(Path out,String name,Object value)throws Exception{Files.writeString(out.resolve(name),JSON.toJson(value));}
    public static void main(String[] args)throws Throwable {
        Path out=Path.of(args[0]);Files.createDirectories(out);int rounds=Integer.parseInt(args[1]),cycles=Integer.parseInt(args[2]);
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        chestItems=net.minecraft.world.level.block.entity.ChestBlockEntity.class.getDeclaredMethod("getItems");chestItems.setAccessible(true);
        inventorySlots=Inventory.class.getDeclaredField("nativeItems");inventorySlots.setAccessible(true);
        pendingCount=Class.forName("net.minecraft.world.item.NativeItemSlots").getDeclaredMethod("pendingForTest");pendingCount.setAccessible(true);
        COUNTER=net.minecraft.util.NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_item_transfer_counter",
            java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_LONG,java.lang.foreign.ValueLayout.JAVA_LONG));
        write(out,"allocator-controls.json",ItemNativeAllocation.control());
        // Warm owner tables and pending maps outside scopes.
        for(int i=0;i<4096;i++)for(var p:PRODUCERS.values()){var op=p.run();op.retire().run();}
        for(int i=0;i<8;i++){System.gc();Thread.sleep(50);}

        // 1. Each producer publishes pending (counter 5), keeps tagged Rust bytes
        //    while the row exists, and frees them when the row is emptied.
        var operations=new ArrayList<Map<String,Object>>();
        TAGGED=true;
        for(var entry:PRODUCERS.entrySet())for(int i=0;i<8;i++){
            long published=counter(5);var op=entry.getValue().run();
            if(counter(5)!=published+1)throw new AssertionError(entry.getKey()+" did not publish pending contents natively");
            boolean materialized=entry.getKey().endsWith("materialized");
            int before=pending(op.slots());if(!materialized&&before!=1)throw new AssertionError(entry.getKey()+" pending rows "+before);
            observe(entry.getKey()+" holds its row",()->live(op.tag())>0);long held=live(op.tag());
            op.retire().run();
            if(pending(op.slots())!=0)throw new AssertionError(entry.getKey()+" kept a pending recipe after retirement");
            // Materialized contents are owned by their facade: released by GC/Cleaner.
            observe(entry.getKey()+" frees its row",()->live(op.tag())==0);
            Reference.reachabilityFence(op.owner());
            var row=new LinkedHashMap<String,Object>(ItemNativeAllocation.sample(op.tag(),op.atEnd()));
            row.put("name",entry.getKey());row.put("heldRustBytes",held);row.put("releasedRustBytes",live(op.tag()));row.put("ownerReachableAtRelease",true);
            operations.add(row);
        }
        TAGGED=false;
        write(out,"operations.json",operations);

        // 2. Held control: a retained pending row keeps its bytes until replaced.
        var heldInventory=new Inventory(null,new EntityEquipment());int heldTag=ItemNativeAllocation.begin();
        try{heldInventory.add(new ItemStack(Items.STICK,11));}finally{ItemNativeAllocation.end(heldTag);}
        for(int i=0;i<8;i++){System.gc();Thread.sleep(50);}
        long heldBytes=live(heldTag);if(heldBytes==0)throw new AssertionError("Held pending row retained no tagged Rust allocation");
        heldInventory.setItem(0,ItemStack.EMPTY);observe("held control release",()->live(heldTag)==0);

        // 3. Bounded churn: every producer, retired each cycle, plus owners dropped.
        var churn=new ArrayList<Map<String,Object>>();long published=counter(5),materializedCount=counter(6),rolledBack=counter(7);
        for(int round=0;round<rounds;round++){
            for(int c=0;c<cycles;c++)for(var p:PRODUCERS.values()){var op=p.run();op.retire().run();}
            for(int i=0;i<4;i++){System.gc();Thread.sleep(50);}
            var runtime=Runtime.getRuntime();
            churn.add(new LinkedHashMap<>(Map.of("round",round,"rssBytes",rss(),"heapUsedBytes",runtime.totalMemory()-runtime.freeMemory(),
                "pendingPublished",counter(5)-published,"pendingMaterialized",counter(6)-materializedCount,"pendingRolledBack",counter(7)-rolledBack)));
        }
        write(out,"churn.json",churn);
        for(var row:operations)if(((Number)row.get("releasedRustBytes")).longValue()!=0)throw new AssertionError("Released row kept bytes: "+row);
        write(out,"complete.json",Map.of("passed",true,"heldControlRustBytes",heldBytes,"producers",PRODUCERS.keySet(),"rounds",rounds,"cycles",cycles,
            "scope","S0034 pending producers; requested libc heap per tag (retirement while the owner is reachable), held control, and process VmRSS/Java heap after forced GC per churn round; no latency claim"));
        System.out.println("ITEM_PENDING_RESOURCE_PASS held="+heldBytes+" sink="+sink);
    }
}
