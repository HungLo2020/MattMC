import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Complete actual quickMoveStack cost. Fixture reset/output checks are outside the timer.
 * All snapshot/insert facade creation, native admission/adoption and Java notifications are inside.
 * Null-world notifications are exercised; this is a feasibility diagnostic, not gameplay acceptance.
 */
public final class StockChestTransferTimingReference {
    static final int POOL=32, BATCHES=128;
    static final List<String> rows=new ArrayList<>();
    static final com.sun.management.ThreadMXBean threads=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    static HolderLookup.Provider registries;
    static volatile long sink;
    static long compilation(){var b=ManagementFactory.getCompilationMXBean();return b.isCompilationTimeMonitoringSupported()?b.getTotalCompilationTime():-1;}
    static long collections(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionCount())).sum();}
    static long allocation(){return threads.getThreadAllocatedBytes(Thread.currentThread().threadId());}
    static final class Fixture {
        final ChestBlockEntity chest=new ChestBlockEntity(BlockPos.ZERO,Blocks.CHEST.defaultBlockState());
        final Inventory inventory=new Inventory(null,new EntityEquipment());
        final ChestMenu menu;
        final ItemStack source=new ItemStack(Items.STICK,11);
        final ItemStack[] targets=new ItemStack[27];
        final String shape;
        Fixture(String shape){this.shape=shape;chest.setRemoved();menu=ChestMenu.threeRows(1,inventory,chest);chest.clearRemoved();source.setPopTime(7);
            for(int i=0;i<27;i++) {targets[i]=shape.equals("empty")?ItemStack.EMPTY:new ItemStack(shape.equals("many")||(shape.equals("late")&&i==26)||(shape.equals("one")&&i==0)?Items.STICK:Items.DIRT,64);chest.setItem(i,targets[i]);}}
        void reset(){source.setCount(shape.equals("zero")?0:shape.equals("many")?100:11);inventory.setItem(9,source);
            if(shape.equals("empty"))chest.setItem(0,ItemStack.EMPTY);
            else for(int i=0;i<27;i++)targets[i].setCount(shape.equals("many")||(shape.equals("late")&&i==26)||(shape.equals("one")&&i==0)?60:64);}
        long consume(ItemStack returned){long c=returned.getCount()*31L+returned.getPopTime()+source.getCount()*37L;for(int i=0;i<27;i++)c=c*31+chest.getItem(i).getCount();return c;}
    }
    record Sample(long elapsed,long bytes,long checksum){}
    static Sample run(Fixture[] fixtures,ItemStack[] returned){long elapsed=0,bytes=0,checksum=0;
        for(int batch=0;batch<BATCHES;batch++){
            for(var fixture:fixtures)fixture.reset();
            long allocated=allocation(),start=System.nanoTime();
            for(int i=0;i<POOL;i++)returned[i]=fixtures[i].menu.quickMoveStack(null,27);
            elapsed+=System.nanoTime()-start;bytes+=allocation()-allocated;
            for(int i=0;i<POOL;i++)checksum+=fixtures[i].consume(returned[i]);
        }return new Sample(elapsed,bytes,checksum);}
    static void measure(String shape)throws Exception{
        Fixture[] fixtures=new Fixture[POOL];for(int i=0;i<POOL;i++)fixtures[i]=new Fixture(shape);ItemStack[] returned=new ItemStack[POOL];
        long began=System.nanoTime(),quiet=began,compiler=compilation();int rounds=0;
        do{sink^=run(fixtures,returned).checksum();rounds++;Thread.sleep(25);long next=compilation();if(next!=compiler)quiet=System.nanoTime();compiler=next;
            if(rounds>=5&&System.nanoTime()-began>=1_000_000_000L&&System.nanoTime()-quiet>=250_000_000L)break;
        }while(System.nanoTime()-began<12_000_000_000L);
        rows.add("# warmup "+shape+" rounds="+rounds+" ns="+(System.nanoTime()-began));
        for(int sample=0;sample<5;sample++){
            compiler=compilation();long gc=collections();Sample result=run(fixtures,returned);sink^=result.checksum();
            rows.add("quick_move_"+shape+","+sample+","+(POOL*BATCHES)+","+result.elapsed()+","+result.checksum()+","+(compilation()-compiler)+","+(collections()-gc)+","+result.bytes());
            // Complete serialized chest output is consumed after timing; saving is a separate operation.
            rows.add("# save "+shape+" sample="+sample+" hash="+fixtures[0].chest.saveWithFullMetadata(registries).hashCode());
        }
    }
    public static void main(String[] args)throws Exception{
        long boot=System.nanoTime();net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();registries=net.minecraft.server.RegistryLayer.createRegistryAccess().compositeAccess();
        rows.add("# boot_ns="+(System.nanoTime()-boot)+" actual_stock_menu=true fixture_reset_and_output_checks=outside_timer null_world=true native_allocated_bytes=not_measured");
        rows.add("workload,sample,operations,elapsed_ns,checksum,compiler_ms,gc_count,allocated_bytes");
        for(String shape:List.of("zero","full","one","late","many","empty"))measure(shape);
        if(Boolean.parseBoolean(args[1])) {
            var descriptor=java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_LONG,java.lang.foreign.ValueLayout.JAVA_LONG);
            var method=Class.forName("net.minecraft.util.NativeLibraryLoader").getMethod("downcallHandle",String.class,String.class,java.lang.foreign.FunctionDescriptor.class);
            var counter=(java.lang.invoke.MethodHandle)method.invoke(null,"mattmc_rust","mattmc_item_transfer_counter",descriptor);
            for(long i=0;i<3;i++){long value;try{value=(long)counter.invokeExact(i);}catch(Throwable e){throw new AssertionError(e);}if(value<=0)throw new AssertionError("Vacuous native transfer counter"+i);rows.add("# native_counter "+i+"="+value);}
        }
        Files.write(Path.of(args[0]),rows);System.out.println("STOCK_CHEST_TRANSFER_TIMING sink="+sink);
    }
}
