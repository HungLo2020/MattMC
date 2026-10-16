import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import static org.mockito.Mockito.*;

/** Actual stock menu and actual repeated QUICK_MOVE caller; only Player's inventory lookup is mocked. */
public final class InventoryQuickMoveReference {
    static HolderLookup.Provider registries;static final List<String> rows=new ArrayList<>();
    static String stack(ItemStack s){return BuiltInRegistries.ITEM.getKey(s.getItem())+"*"+s.getCount()+"/pop="+s.getPopTime()+"/emptySingleton="+(s==ItemStack.EMPTY)+"/patch="+s.getComponentsPatch();}
    static void run(boolean reverse,boolean repeated,String shape) throws Exception {
        var events=new ArrayList<String>();Inventory[] ref={null};var expected=new AssertionError("original equipment failure");
        var equipment=new EntityEquipment(){@Override public ItemStack set(EquipmentSlot slot,ItemStack value){events.add("equipment:"+slot+"/main="+(ref[0].getItem(8)==value)+"/changed="+ref[0].getTimesChanged());if(shape.equals("equipment-error"))throw expected;return super.set(slot,value);}};
        Player player=mock(Player.class,withSettings().mockMaker("mock-maker-subclass"));Inventory inv=new Inventory(player,equipment);ref[0]=inv;int[] lookups={0};
        doAnswer(call->{lookups[0]++;return inv;}).when(player).getInventory();
        var chest=new ChestBlockEntity(BlockPos.ZERO,Blocks.CHEST.defaultBlockState());chest.setRemoved();var menu=ChestMenu.threeRows(1,inv,chest);chest.clearRemoved();
        int count=shape.equals("many")?100:shape.equals("overstack")?130:shape.equals("zero")?0:11;
        ItemStack source=new ItemStack(Items.STICK,11);source.setPopTime(7);
        if(reverse){chest.setItem(0,source);source.setCount(count);}else{source.setCount(count);inv.setItem(9,source);}
        int size=reverse?36:27;
        for(int i=0;i<size;i++){
            boolean same=shape.equals("many")||(shape.equals("one")&&i==(reverse?8:0));
            boolean empty=shape.equals("empty")||shape.equals("overstack")||shape.equals("zero")||shape.equals("equipment-error")||shape.equals("short-menu");
            ItemStack target=empty?ItemStack.EMPTY:new ItemStack(same?Items.STICK:Items.DIRT,same?60:64);
            if(reverse)inv.setItem(i,target);else chest.setItem(i,target);
        }
        if(shape.equals("alias-source")){if(reverse){inv.setItem(8,source);inv.setItem(7,source);}else{chest.setItem(0,source);chest.setItem(1,source);}}
        var extra=new net.minecraft.world.SimpleContainer(1);
        if(shape.equals("extra-slot"))menu.slots.add(new Slot(extra,0,0,0));
        if(shape.equals("short-menu"))menu.slots.remove(menu.slots.size()-1);
        EquipmentSlot old=Inventory.EQUIPMENT_SLOT_MAPPING.get(8);if(shape.equals("equipment-error"))Inventory.EQUIPMENT_SLOT_MAPPING.put(8,EquipmentSlot.OFFHAND);
        String result;try {
            if(repeated){menu.clicked(reverse?0:27,0,ClickType.QUICK_MOVE,player);result="clicked";}
            else {ItemStack returned=menu.quickMoveStack(player,reverse?0:27);result="returned="+stack(returned)+"/sameSource="+(returned==source);}
        }catch(Throwable t){result="throw="+t.getClass().getName()+":"+t.getMessage()+"/same="+(t==expected);}
        finally {if(shape.equals("equipment-error")){if(old==null)Inventory.EQUIPMENT_SLOT_MAPPING.remove(8);else Inventory.EQUIPMENT_SLOT_MAPPING.put(8,old);}}
        var aliases=new IdentityHashMap<ItemStack,Integer>();var output=new ArrayList<String>();
        for(int owner=0;owner<2;owner++)for(int i=0;i<(owner==0?27:36);i++){
            ItemStack s=owner==0?chest.getItem(i):inv.getNonEquipmentItems().get(i);
            if(s!=ItemStack.EMPTY)output.add(owner+":"+i+":"+stack(s)+"/alias="+aliases.computeIfAbsent(s,k->aliases.size())+"/source="+(s==source));
        }
        var invOut=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,registries);@SuppressWarnings("unchecked") var codec=(com.mojang.serialization.Codec<ItemStackWithSlot>)ItemStackWithSlot.class.getField("CODEC").get(null);inv.save(invOut.list("Inventory",codec));
        rows.add(reverse+"/"+repeated+"/"+shape+" "+result+" source="+stack(source)+"/selected="+inv.getSelectedSlot()+"/changed="+inv.getTimesChanged()
            +"/playerLookups="+lookups[0]+"/extra="+stack(extra.getItem(0))+" slots="+output+" events="+events+" chestSave="+chest.saveWithFullMetadata(registries)+" inventorySave="+invOut.buildResult());
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();registries=net.minecraft.server.RegistryLayer.createRegistryAccess().compositeAccess();
        for(boolean reverse:new boolean[]{false,true})for(boolean repeated:new boolean[]{false,true})for(String shape:List.of("zero","full","empty","one","many","overstack","alias-source"))run(reverse,repeated,shape);
        for(boolean repeated:new boolean[]{false,true})for(String shape:List.of("equipment-error","extra-slot","short-menu"))run(true,repeated,shape);Files.write(Path.of(args[0]),rows);System.out.println("INVENTORY_QUICK_MOVE rows="+rows.size());
    }
}
