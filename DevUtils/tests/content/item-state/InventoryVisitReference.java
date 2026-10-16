import java.nio.file.*;
import java.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.level.storage.*;
import net.minecraft.tags.ItemTags;
import static org.mockito.Mockito.*;

/** Live cursor/effect ordering through public inventory visitors and producers. */
public final class InventoryVisitReference {
    static String state(Inventory inv,ItemStack source) {
        var slots=new ArrayList<String>();var aliases=new IdentityHashMap<ItemStack,Integer>();
        for(int i=0;i<36;i++){var s=inv.getItem(i);if(s!=ItemStack.EMPTY)slots.add(i+":"+s.getCount()+":"+s.getPopTime()+":source="+(s==source)+":alias="+aliases.computeIfAbsent(s,k->aliases.size()));}
        return "selected="+inv.getSelectedSlot()+"/changed="+inv.getTimesChanged()+"/slots="+slots;
    }
    static String run(String mode) {
        var events=new ArrayList<String>();Inventory[] owner={null};Player player=mock(Player.class,withSettings().mockMaker("mock-maker-subclass"));
        doAnswer(call->{ItemStack s=call.getArgument(0);events.add("drop="+s.getCount());owner[0].setItem(5,new ItemStack(Items.STICK,7));if(mode.endsWith("throw"))throw new AssertionError("original drop failure");return null;}).when(player).drop(any(ItemStack.class),eq(true),eq(false));
        var equipment=new EntityEquipment(){@Override public void clear(){events.add("equipment-clear");super.clear();}};
        Inventory inv=new Inventory(mode.equals("null-player-tick")||mode.equals("null-player-drop")?null:player,equipment);owner[0]=inv;ItemStack source=new ItemStack(Items.STICK,11);source.setPopTime(3);inv.setItem(0,source);inv.setItem(2,source);inv.setSelectedSlot(8);String result="ok";
        if(mode.startsWith("tick"))doAnswer(call->{events.add("level/sourcePop="+source.getPopTime());if(events.size()==1){var added=new ItemStack(Items.STICK,7);added.setPopTime(3);inv.setItem(5,added);inv.setSelectedSlot(0);}if(mode.endsWith("throw"))throw new AssertionError("original level failure");return null;}).when(player).level();
        var oldRouting=Inventory.EQUIPMENT_SLOT_MAPPING.get(99);
        try {switch(mode) {
            case "save","save-throw" -> inv.save(new ValueOutput.TypedOutputList<ItemStackWithSlot>() {
                public boolean isEmpty(){return events.isEmpty();}
                public void add(ItemStackWithSlot entry){events.add("save="+entry.slot()+":"+entry.stack().getCount());inv.setItem(5,new ItemStack(Items.STICK,7));if(mode.endsWith("throw"))throw new AssertionError("original output failure");}
            });
            case "load","load-throw" -> {
                var data=new ArrayList<>(List.of(new ItemStackWithSlot(3,source),new ItemStackWithSlot(5,new ItemStack(Items.DIRT,7))));
                inv.load(new ValueInput.TypedInputList<ItemStackWithSlot>() {
                    public boolean isEmpty(){return false;}
                    public java.util.stream.Stream<ItemStackWithSlot> stream(){throw new AssertionError("must retain original iterator");}
                    public Iterator<ItemStackWithSlot> iterator(){events.add("iterator/mainCleared="+inv.getItem(0).isEmpty());return new Iterator<>(){int index;public boolean hasNext(){return index<data.size();}public ItemStackWithSlot next(){events.add("next="+index);if(index==1&&mode.endsWith("throw"))throw new AssertionError("original input failure");return data.get(index++);}};}
                });
            }
            case "predicate","predicate-throw","predicate-routing" -> result="contains="+inv.contains(s->{events.add("predicate="+s.getCount());if(events.size()==1){inv.setItem(5,new ItemStack(Items.STICK,7));if(mode.endsWith("routing"))Inventory.EQUIPMENT_SLOT_MAPPING.put(99,EquipmentSlot.OFFHAND);if(mode.endsWith("throw"))throw new AssertionError("original predicate failure");}return !mode.endsWith("routing")&&s.getCount()==7;});
            case "drop","drop-throw" -> inv.dropAll();
            case "replace","replace-routing" -> {Inventory other=new Inventory(null,new EntityEquipment()){@Override public ItemStack getItem(int i){events.add("read="+i);if(i==0){inv.setSelectedSlot(2);if(mode.endsWith("routing"))Inventory.EQUIPMENT_SLOT_MAPPING.put(99,EquipmentSlot.OFFHAND);}return i==5?source:ItemStack.EMPTY;}};other.setSelectedSlot(3);inv.replaceWith(other);}
            case "null-player-tick" -> inv.tick();
            case "null-player-drop" -> inv.dropAll();
            case "null-save" -> inv.save(null);
            case "null-load" -> inv.load(null);
            case "null-predicate" -> result="contains="+inv.contains((java.util.function.Predicate<ItemStack>)null);
            case "null-fill" -> inv.fillStackedContents(null);
            case "null-replace" -> inv.replaceWith(null);
            case "tick","tick-throw" -> inv.tick();
            case "fill","fill-throw" -> {
                var contents=mock(StackedItemContents.class,withSettings().mockMaker("mock-maker-subclass"));
                doAnswer(call->{ItemStack value=call.getArgument(0);events.add("account="+value.getCount()+":source="+(value==source));if(events.size()==1)inv.setItem(5,new ItemStack(Items.STICK,7));if(events.size()==2&&mode.endsWith("throw"))throw new AssertionError("original account failure");return null;}).when(contents).accountSimpleStack(any(ItemStack.class));
                inv.fillStackedContents(contents);
            }
            case "clear" -> inv.clearContent();
            default -> throw new AssertionError(mode);
        }}catch(Throwable failure){result=failure.getClass().getName()+":"+failure.getMessage();}finally{if(oldRouting==null)Inventory.EQUIPMENT_SLOT_MAPPING.remove(99);else Inventory.EQUIPMENT_SLOT_MAPPING.put(99,oldRouting);}
        return mode+" "+result+"/"+state(inv,source)+"/events="+events;
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();var rows=new ArrayList<String>();
        for(String mode:List.of("save","save-throw","load","load-throw","predicate","predicate-throw","drop","drop-throw","replace","clear","predicate-routing","replace-routing","tick","tick-throw","fill","fill-throw","null-player-tick","null-player-drop","null-save","null-load","null-predicate","null-fill","null-replace"))rows.add(run(mode));
        Files.write(Path.of(args[0]),rows);System.out.println("INVENTORY_VISIT rows="+rows.size());
    }
}
