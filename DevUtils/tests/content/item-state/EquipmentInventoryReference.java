import java.nio.file.*;
import java.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;

/** Live public routing, actual equipment values and original foreign getter prefixes. */
public final class EquipmentInventoryReference {
    static final List<String> rows=new ArrayList<>();
    static String value(ItemStack s){return s==null?"NULL":s==ItemStack.EMPTY?"EMPTY":s.getItem()+":"+s.getCount()+":"+s.getPopTime();}
    static void run(String mode){
        var mapping=Inventory.EQUIPMENT_SLOT_MAPPING;EquipmentSlot saved=mapping.get(40),def=mapping.defaultReturnValue();boolean present=mapping.containsKey(40);
        var events=new ArrayList<String>();Inventory[] ref={null};var source=new ItemStack(Items.STICK,11);final ItemStack[] targetRef={new ItemStack(Items.STICK,60)};var target=targetRef[0];String result="ok";
        EntityEquipment equipment=mode.startsWith("foreign")?new EntityEquipment(){@Override public ItemStack get(EquipmentSlot slot){
            events.add("get="+slot);if(mode.equals("foreign-reentry")||mode.equals("foreign-throw")){ref[0].setSelectedSlot(1);ref[0].setItem(1,targetRef[0]);}
            if(mode.equals("foreign-throw"))throw new AssertionError("original equipment query failure");return mode.equals("foreign-null")?null:mode.equals("foreign-reentry")?ItemStack.EMPTY:targetRef[0];
        }}:mode.startsWith("no-equipment")?null:new EntityEquipment();
        var inv=new Inventory(null,equipment);ref[0]=inv;
        try{switch(mode){
            case "present","add" -> equipment.set(EquipmentSlot.OFFHAND,target);
            case "null","null-source-null" -> equipment.set(EquipmentSlot.OFFHAND,null);
            case "empty","null-source-empty" -> equipment.set(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
            case "alias" -> {equipment.set(EquipmentSlot.OFFHAND,source);target=source;}
            case "route" -> {equipment.set(EquipmentSlot.OFFHAND,target);equipment.set(EquipmentSlot.HEAD,new ItemStack(Items.DIRT,7));mapping.put(40,EquipmentSlot.HEAD);}
            case "default-route" -> {equipment.set(EquipmentSlot.HEAD,target);mapping.remove(40);mapping.defaultReturnValue(EquipmentSlot.HEAD);}
            case "removed","no-equipment-unmapped" -> mapping.remove(40);
        }
        if(mode.equals("add"))result="added="+inv.add(source);
        else result="slot="+inv.getSlotWithRemainingSpace(mode.startsWith("null-source")?null:source);
        }catch(Throwable t){result=t.getClass().getName()+":"+t.getMessage();}
        finally{mapping.defaultReturnValue(def);if(present)mapping.put(40,saved);else mapping.remove(40);}
        var main=new ArrayList<String>();for(int i=0;i<36;i++){var s=inv.getNonEquipmentItems().get(i);if(s!=ItemStack.EMPTY)main.add(i+"="+value(s)+":source="+(s==source)+":target="+(s==target));}
        rows.add(mode+" "+result+" source="+value(source)+" target="+value(target)+" selected="+inv.getSelectedSlot()+" main="+main+" events="+events);
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(String mode:List.of("absent","present","null","empty","alias","route","default-route","removed","add","null-source-null","null-source-empty","no-equipment","no-equipment-unmapped","foreign","foreign-null","foreign-reentry","foreign-throw"))run(mode);
        Files.write(Path.of(args[0]),rows);System.out.println("EQUIPMENT_INVENTORY rows="+rows.size());
    }
}
