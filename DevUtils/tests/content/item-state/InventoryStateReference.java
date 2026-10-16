import java.nio.file.*;
import java.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;

/** Unchanged public inventory producer/consumer oracle, independent of native bridge classes. */
public final class InventoryStateReference {
    static final List<String> rows=new ArrayList<>();
    static String state(Inventory inventory,ItemStack source) {
        var aliases=new IdentityHashMap<ItemStack,Integer>();var slots=new ArrayList<String>();
        var items=inventory.getNonEquipmentItems();
        for(int i=0;i<items.size();i++){var s=items.get(i);if(s!=ItemStack.EMPTY)slots.add(i+":"+s.getCount()+":"+s.getPopTime()+":empty="+s.isEmpty()+":alias="+aliases.computeIfAbsent(s,k->aliases.size())+":source="+(s==source));}
        return "selected="+inventory.getSelectedSlot()+"/changed="+inventory.getTimesChanged()+"/size="+inventory.getContainerSize()+"/slots="+slots+"/source="+(source==null?"absent":source.getCount()+":"+source.getPopTime());
    }
    static String error(Throwable t){return t.getClass().getName()+":"+t.getMessage();}
    static void run(String mode) {
        Inventory inv=new Inventory(null,new EntityEquipment());ItemStack source=new ItemStack(Items.STICK,11);String result="ok";
        try {switch(mode) {
            case "selected" -> {inv.setSelectedSlot(8);try{inv.setSelectedSlot(9);}catch(Throwable t){result=error(t);}}
            case "negative-selected" -> {inv.setSelectedSlot(3);try{inv.setSelectedSlot(-1);}catch(Throwable t){result=error(t);}}
            case "changed" -> {inv.setChanged();inv.setChanged();}
            case "list-alias" -> {var list=inv.getNonEquipmentItems();list.set(0,source);list.subList(1,3).set(0,source);var it=list.listIterator(2);it.next();it.set(source);result="sameList="+(list==inv.getNonEquipmentItems());}
            case "pick" -> {inv.setItem(0,source);inv.setItem(3,new ItemStack(Items.DIRT,7));inv.pickSlot(3);}
            case "pick-invalid" -> {inv.setSelectedSlot(3);inv.setItem(3,source);inv.pickSlot(40);}
            case "add-pick" -> {inv.setItem(0,new ItemStack(Items.DIRT,7));inv.addAndPickItem(source);}
            case "add-empty" -> result="added="+inv.add(source);
            case "add-merge" -> {inv.setItem(0,new ItemStack(Items.STICK,60));result="added="+inv.add(source);}
            case "add-alias" -> {inv.setItem(0,source);result="added="+inv.add(0,source);}
            case "partial-null-player" -> {for(int i=0;i<36;i++)inv.setItem(i,new ItemStack(Items.DIRT,64));inv.setItem(0,new ItemStack(Items.STICK,60));result="added="+inv.add(source);}
            case "damaged" -> {source=new ItemStack(Items.DIAMOND_SWORD);source.setDamageValue(1);result="added="+inv.add(3,source);}
            case "damaged-invalid" -> {source=new ItemStack(Items.DIAMOND_SWORD);source.setDamageValue(1);result="added="+inv.add(40,source);}
            case "remove-alias" -> {inv.setItem(0,source);inv.setItem(1,source);inv.removeItem(source);}
            case "remove-negative-index" -> {inv.setItem(0,source);result="empty="+(inv.removeItem(-1,1)==ItemStack.EMPTY);}
            case "remove-no-update-negative" -> inv.removeItemNoUpdate(-1);
            case "remove-negative-count" -> {inv.setItem(0,source);result="empty="+(inv.removeItem(0,-1)==ItemStack.EMPTY);}
            case "null-before-index" -> inv.setItem(-1,null);
            case "clear" -> {inv.setSelectedSlot(8);inv.setItem(0,source);inv.setItem(1,source);inv.setChanged();inv.clearContent();}
            default -> throw new AssertionError(mode);
        }} catch(Throwable t){result=error(t);}
        rows.add(mode+" "+result+" "+state(inv,source));
    }
    static void routing(boolean fail) {
        var old=Inventory.EQUIPMENT_SLOT_MAPPING.get(0);var events=new ArrayList<String>();Inventory[] ref={null};
        var equipment=new EntityEquipment(){@Override public ItemStack set(EquipmentSlot slot,ItemStack value){events.add("equipment:"+slot+":mainAlready="+(ref[0].getNonEquipmentItems().get(0)==value));if(fail)throw new IllegalStateException("original equipment failure");return super.set(slot,value);}};
        Inventory inv=new Inventory(null,equipment);ref[0]=inv;ItemStack source=new ItemStack(Items.STICK,11);Inventory.EQUIPMENT_SLOT_MAPPING.put(0,EquipmentSlot.OFFHAND);String result="ok";
        try{inv.setItem(0,source);}catch(Throwable t){result=error(t);}finally{if(old==null)Inventory.EQUIPMENT_SLOT_MAPPING.remove(0);else Inventory.EQUIPMENT_SLOT_MAPPING.put(0,old);}
        rows.add("routing/"+fail+" "+result+" "+state(inv,source)+" events="+events);
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(String mode:List.of("selected","negative-selected","changed","list-alias","pick","pick-invalid","add-pick","add-empty","add-merge","add-alias","partial-null-player","damaged","damaged-invalid","remove-alias","remove-negative-index","remove-no-update-negative","remove-negative-count","null-before-index","clear"))run(mode);
        routing(false);routing(true);Files.write(Path.of(args[0]),rows);System.out.println("INVENTORY_STATE rows="+rows.size());
        if(Boolean.getBoolean("mattmc.require.native.inventory")){var inv=new Inventory(null,new EntityEquipment());if(!inv.getNonEquipmentItems().getClass().getName().equals("net.minecraft.world.item.NativeItemSlots"))throw new AssertionError("native inventory producer absent");}
    }
}
