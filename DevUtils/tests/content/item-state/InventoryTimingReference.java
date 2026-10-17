import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.util.ProblemReporter;
import static org.mockito.Mockito.*;

/** Connected diagnostic. Runs the UNCHANGED six forward workloads first, then
 * complete reverse/add/pick/repeated caller operations. External Player is mocked
 * identically on every side. Reset/save/consumption remain outside each timer.
 */
public final class InventoryTimingReference {
    static final int POOL=32,BATCHES=128;
    static final List<String> rows=new ArrayList<>();
    static final com.sun.management.ThreadMXBean threads=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    static volatile long sink;
    static final class Fixture {
        final String mode;
        final Player player=mock(Player.class,withSettings().mockMaker("mock-maker-subclass").stubOnly());
        final Inventory inventory=new Inventory(player,new EntityEquipment());
        final ChestBlockEntity chest=new ChestBlockEntity(BlockPos.ZERO,Blocks.CHEST.defaultBlockState());
        final ChestMenu menu;
        final ItemStack source;
        final ItemStack[] targets=new ItemStack[36];
        int beforeChanges;boolean added;
        Fixture(String mode) {
            this.mode=mode;doReturn(inventory).when(player).getInventory();
            source=new ItemStack(mode.equals("add_damaged")?Items.DIAMOND_SWORD:Items.STICK,11);if(mode.equals("add_damaged"))source.setDamageValue(1);
            chest.setRemoved();menu=ChestMenu.threeRows(1,inventory,chest);chest.clearRemoved();
            for(int i=0;i<36;i++)targets[i]=new ItemStack(mode.endsWith("many")||mode.equals("add_partial")&&i==0||mode.endsWith("one")&&i==8||mode.endsWith("late")&&i==(mode.startsWith("reverse")?9:35)?Items.STICK:Items.DIRT,64);
        }
        void reset() {
            boolean empty=mode.endsWith("empty")||mode.startsWith("clicked");boolean pick=mode.startsWith("pick");
            inventory.setSelectedSlot(pick?8:0);
            for(int i=0;i<36;i++) {
                boolean clear=empty||pick&&mode.equals("pick_displace")&&i>=9;
                targets[i].setCount(pick?7:targets[i].getItem()==Items.STICK?60:64);targets[i].setPopTime(0);
                inventory.setItem(i,clear?ItemStack.EMPTY:targets[i]);
            }
            for(int i=0;i<27;i++)chest.setItem(i,ItemStack.EMPTY);
            source.setCount(mode.endsWith("zero")?0:mode.endsWith("many")?100:mode.startsWith("clicked")?130:mode.equals("add_damaged")?1:11);source.setPopTime(7);
            if(mode.startsWith("reverse")||mode.equals("clicked_reverse")){chest.setItem(0,source);source.setCount(mode.endsWith("zero")?0:mode.endsWith("many")?100:mode.startsWith("clicked")?130:11);}
            if(mode.equals("clicked_forward"))inventory.setItem(9,source);
            if(mode.equals("add_alias"))inventory.setItem(0,source);
            beforeChanges=inventory.getTimesChanged();added=false;
        }
        ItemStack run() {
            if(mode.startsWith("reverse"))return menu.quickMoveStack(null,0);
            if(mode.startsWith("clicked")){menu.clicked(mode.equals("clicked_reverse")?0:27,0,ClickType.QUICK_MOVE,player);return ItemStack.EMPTY;}
            if(mode.startsWith("pick")){inventory.addAndPickItem(source);return ItemStack.EMPTY;}
            added=mode.equals("add_alias")?inventory.add(0,source):mode.equals("add_damaged")?inventory.add(3,source):inventory.add(source);return ItemStack.EMPTY;
        }
        long consume(ItemStack returned) {
            long result=source.getCount()*37L+source.getPopTime()+returned.getCount()*31L+returned.getPopTime()+inventory.getSelectedSlot()*7L+(inventory.getTimesChanged()-beforeChanges)*13L+(added?1:0);
            for(int i=0;i<36;i++){var value=inventory.getItem(i);result=result*31+value.getCount()*3L+value.getPopTime()+(value==source?1:0);}
            for(int i=0;i<27;i++)result=result*31+chest.getItem(i).getCount();return result;
        }
        String save()throws Exception {
            var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,StockChestTransferTimingReference.registries);
            @SuppressWarnings("unchecked") var codec=(com.mojang.serialization.Codec<ItemStackWithSlot>)ItemStackWithSlot.class.getField("CODEC").get(null);
            inventory.save(output.list("Inventory",codec));return "chest="+chest.saveWithFullMetadata(StockChestTransferTimingReference.registries).hashCode()+"/inventory="+output.buildResult().hashCode();
        }
    }
    record Sample(long elapsed,long bytes,long checksum){}
    static Sample run(Fixture[] fixtures,ItemStack[] returned) {
        long elapsed=0,bytes=0,checksum=0;
        for(int batch=0;batch<BATCHES;batch++) {
            for(var fixture:fixtures)fixture.reset();
            long allocated=StockChestTransferTimingReference.allocation(),start=System.nanoTime();
            for(int i=0;i<POOL;i++)returned[i]=fixtures[i].run();
            elapsed+=System.nanoTime()-start;bytes+=StockChestTransferTimingReference.allocation()-allocated;
            for(int i=0;i<POOL;i++)checksum+=fixtures[i].consume(returned[i]);
        }
        return new Sample(elapsed,bytes,checksum);
    }
    static void measure(String mode)throws Exception {
        Fixture[] fixtures=new Fixture[POOL];for(int i=0;i<POOL;i++)fixtures[i]=new Fixture(mode);var returned=new ItemStack[POOL];
        long began=System.nanoTime(),quiet=began,compiler=StockChestTransferTimingReference.compilation();int rounds=0;
        do {sink^=run(fixtures,returned).checksum();rounds++;Thread.sleep(25);long next=StockChestTransferTimingReference.compilation();if(next!=compiler)quiet=System.nanoTime();compiler=next;
            if(rounds>=5&&System.nanoTime()-began>=1_000_000_000L&&System.nanoTime()-quiet>=250_000_000L)break;
        }while(System.nanoTime()-began<12_000_000_000L);
        rows.add("# warmup "+mode+" rounds="+rounds+" ns="+(System.nanoTime()-began));
        for(int sample=0;sample<5;sample++) {
            compiler=StockChestTransferTimingReference.compilation();long gc=StockChestTransferTimingReference.collections();var result=run(fixtures,returned);sink^=result.checksum();
            rows.add(mode+","+sample+","+(POOL*BATCHES)+","+result.elapsed()+","+result.checksum()+","+(StockChestTransferTimingReference.compilation()-compiler)+","+(StockChestTransferTimingReference.collections()-gc)+","+result.bytes());
            rows.add("# save "+mode+" sample="+sample+" "+fixtures[0].save());
        }
        if(requireNative&&!fixtures[0].inventory.getNonEquipmentItems().getClass().getName().equals("net.minecraft.world.item.NativeItemSlots"))throw new AssertionError("Native inventory producer absent");
    }
    static boolean requireNative;
    public static void main(String[] args)throws Exception {
        requireNative=Boolean.parseBoolean(args[1]);StockChestTransferTimingReference.main(args);
        rows.addAll(Files.readAllLines(Path.of(args[0])));rows.add("# connected_inventory=true complete_facades=true external_player=mocked reset_save_consumption=outside_timer native_allocated_bytes=not_measured");
        for(String mode:List.of("reverse_zero","reverse_full","reverse_one","reverse_late","reverse_many","reverse_empty","add_zero","add_full","add_partial","add_late","add_many","add_empty","add_alias","add_damaged","pick_empty","pick_displace","pick_full","clicked_forward","clicked_reverse"))measure(mode);
        Files.write(Path.of(args[0]),rows);System.out.println("INVENTORY_TIMING sink="+sink);
    }
}
