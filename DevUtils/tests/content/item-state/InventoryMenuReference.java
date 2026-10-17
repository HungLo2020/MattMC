import java.nio.file.*;
import java.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import static org.mockito.Mockito.*;

/** Actual inventory-menu specialized dispatch and leftover drop order. */
public final class InventoryMenuReference {
    static final List<String> rows=new ArrayList<>();
    static String stack(ItemStack value,ItemStack source){return value==null?"NULL":value==ItemStack.EMPTY?"EMPTY":value.getCount()+":"+value.getPopTime()+":source="+(value==source);}
    static void run(String mode) {
        var events=new ArrayList<String>();var player=mock(Player.class,withSettings().mockMaker("mock-maker-subclass"));
        doReturn(true).when(player).canUseSlot(any(EquipmentSlot.class));doReturn(EntityType.PLAYER).when(player).getType();
        Inventory inv=new Inventory(player,new EntityEquipment());doReturn(inv).when(player).getInventory();
        ItemStack source=new ItemStack(mode.contains("armor")?Items.DIAMOND_HELMET:mode.contains("offhand")?Items.SHIELD:Items.STICK,mode.contains("armor")||mode.contains("offhand")?1:mode.contains("partial")?130:11);source.setPopTime(3);
        ItemStack[] escaped={null};
        doAnswer(call->{EquipmentSlot slot=call.getArgument(0);ItemStack old=call.getArgument(1),next=call.getArgument(2);events.add("equip="+slot+":"+stack(old,source)+">"+stack(next,source)+":current="+stack(inv.getItem(slot==EquipmentSlot.OFFHAND?40:39),source));escaped[0]=next;if(mode.equals("armor-throw"))throw new AssertionError("original equip failure");return null;}).when(player).onEquipItem(any(EquipmentSlot.class),any(ItemStack.class),any(ItemStack.class));
        doAnswer(call->{ItemStack value=call.getArgument(0);events.add("drop="+stack(value,source));if(mode.equals("drop-throw"))throw new AssertionError("original leftover drop failure");return null;}).when(player).drop(any(ItemStack.class),eq(false));
        doAnswer(call->{events.add("award="+call.getArgument(1));return null;}).when(player).awardStat(any(net.minecraft.stats.Stat.class),anyInt());
        InventoryMenu menu=new InventoryMenu(inv,true,player);int index=9;ItemStack returned=ItemStack.EMPTY;String result="ok";
        if(mode.startsWith("result")||mode.equals("drop-throw")) {
            index=0;if(mode.contains("partial")||mode.equals("drop-throw")){for(int i=0;i<36;i++)inv.setItem(i,new ItemStack(Items.DIRT,64));inv.setItem(9,ItemStack.EMPTY);source.setCount(130);}
            menu.getResultSlot().container.setItem(0,source);
        }else if(mode.equals("armor-out")){index=5;inv.setItem(39,source);}
        else if(mode.equals("offhand-out")){index=45;inv.setItem(40,source);}
        else {index=mode.equals("hotbar")||mode.contains("armor")||mode.contains("offhand")?36:9;inv.setItem(index==36?0:9,source);}
        if(mode.equals("take-throw")){Slot previous=menu.slots.get(index);menu.slots.set(index,new Slot(previous.container,previous.getContainerSlot(),0,0){@Override public void onTake(Player p,ItemStack value){events.add("take="+stack(value,source));throw new AssertionError("original inventory onTake failure");}});}
        try {returned=menu.quickMoveStack(player,index);}catch(Throwable failure){result=failure.getClass().getName()+":"+failure.getMessage();}
        var state=new ArrayList<String>();var aliases=new IdentityHashMap<ItemStack,Integer>();
        for(int i=0;i<menu.slots.size();i++){var value=menu.slots.get(i).getItem();if(value!=ItemStack.EMPTY)state.add(i+"="+stack(value,source)+":return="+(value==returned)+":escaped="+(value==escaped[0])+":alias="+aliases.computeIfAbsent(value,k->aliases.size()));}
        rows.add(mode+" "+result+"/return="+stack(returned,source)+"/source="+stack(source,source)+"/escaped="+stack(escaped[0],source)+"/changed="+inv.getTimesChanged()+"/slots="+state+"/events="+events);
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(String mode:List.of("main","hotbar","armor-in","armor-out","armor-throw","offhand-in","offhand-out","result","result-partial","drop-throw","take-throw"))run(mode);
        Files.write(Path.of(args[0]),rows);System.out.println("INVENTORY_MENU rows="+rows.size());
    }
}
