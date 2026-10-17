import java.nio.file.*;
import java.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.nbt.NbtOps;
import static org.mockito.Mockito.*;

/** Original equipment presence/null/enum/effect contracts; no native API. */
public final class EquipmentStateReference {
    static final List<String> rows=new ArrayList<>();
    @SuppressWarnings("unchecked") static Map<EquipmentSlot,ItemStack> items(EntityEquipment equipment)throws Exception {var field=EntityEquipment.class.getDeclaredField("items");field.setAccessible(true);return (Map<EquipmentSlot,ItemStack>)field.get(equipment);}
    static String state(EntityEquipment eq,ItemStack source)throws Exception {
        var entries=new ArrayList<String>();var aliases=new IdentityHashMap<ItemStack,Integer>();
        for(var entry:items(eq).entrySet()){var s=entry.getValue();entries.add(entry.getKey()+"="+(s==null?"NULL":(s==ItemStack.EMPTY?"EMPTY":s.getCount()+":"+s.getPopTime()+":source="+(s==source)+":alias="+aliases.computeIfAbsent(s,k->aliases.size()))));}
        return entries+"/source="+source.getCount()+":"+source.getPopTime();
    }
    static String error(Throwable t){return t.getClass().getName()+":"+t.getMessage();}
    static void run(String mode)throws Exception {
        EntityEquipment eq=new EntityEquipment();ItemStack source=new ItemStack(Items.STICK,11);source.setPopTime(3);var events=new ArrayList<String>();String result="ok";
        Player player=mock(Player.class,withSettings().mockMaker("mock-maker-subclass"));
        doAnswer(call->{events.add("level/pop="+source.getPopTime());if(events.size()==1){var added=new ItemStack(Items.DIRT,7);added.setPopTime(2);eq.set(EquipmentSlot.SADDLE,added);}if(mode.endsWith("throw"))throw new AssertionError("original equipment level failure");return null;}).when(player).level();
        doAnswer(call->{ItemStack value=call.getArgument(0);events.add("drop="+(value==null?"NULL":value.getCount()));eq.set(EquipmentSlot.SADDLE,new ItemStack(Items.DIRT,7));if(mode.endsWith("throw")&&events.size()==2)throw new AssertionError("original equipment drop failure");return null;}).when(player).drop(nullable(ItemStack.class),eq(true),eq(false));
        try {switch(mode) {
            case "empty" -> result="empty="+eq.isEmpty()+"/missing="+(eq.get(EquipmentSlot.HEAD)==ItemStack.EMPTY);
            case "present-empty" -> {eq.set(EquipmentSlot.HEAD,ItemStack.EMPTY);result="empty="+eq.isEmpty();}
            case "present-null" -> {result="oldEmpty="+(eq.set(EquipmentSlot.HEAD,null)==ItemStack.EMPTY)+"/getNull="+(eq.get(EquipmentSlot.HEAD)==null);try{result+="/empty="+eq.isEmpty();}catch(Throwable t){result+="/"+error(t);}}
            case "clear" -> {eq.set(EquipmentSlot.HEAD,source);eq.set(EquipmentSlot.OFFHAND,null);eq.clear();result="empty="+eq.isEmpty();}
            case "self" -> {eq.set(EquipmentSlot.HEAD,source);eq.set(EquipmentSlot.FEET,source);eq.setAll(eq);}
            case "set-all" -> {EntityEquipment other=new EntityEquipment();other.set(EquipmentSlot.FEET,source);other.set(EquipmentSlot.HEAD,source);other.set(EquipmentSlot.OFFHAND,null);eq.set(EquipmentSlot.BODY,new ItemStack(Items.DIRT,7));eq.setAll(other);events.add("other="+state(other,source));}
            case "null-source" -> {eq.set(EquipmentSlot.HEAD,source);eq.setAll(null);}
            case "null-key-set" -> eq.set(null,source);
            case "null-key-get" -> result="empty="+(eq.get(null)==ItemStack.EMPTY);
            case "entry" -> {eq.set(EquipmentSlot.HEAD,source);var entry=items(eq).entrySet().iterator().next();eq.set(EquipmentSlot.HEAD,new ItemStack(Items.DIRT,7));events.add("live="+entry.getValue().getCount());events.add("old="+entry.setValue(null).getCount());result="getNull="+(eq.get(EquipmentSlot.HEAD)==null);}
            case "tick","tick-throw","tick-null" -> {eq.set(EquipmentSlot.FEET,source);eq.set(EquipmentSlot.HEAD,mode.equals("tick-null")?null:source);eq.tick(player);}
            case "drop","drop-throw","drop-null" -> {eq.set(EquipmentSlot.OFFHAND,source);eq.set(EquipmentSlot.HEAD,ItemStack.EMPTY);if(mode.equals("drop-null"))eq.set(EquipmentSlot.BODY,null);eq.dropAll(player);}
            case "codec","codec-null","codec-empty" -> {if(!mode.equals("codec-empty"))eq.set(EquipmentSlot.HEAD,mode.equals("codec-null")?null:source);eq.set(EquipmentSlot.FEET,ItemStack.EMPTY);result=EntityEquipment.CODEC.encodeStart(NbtOps.INSTANCE,eq).toString();}
            default -> throw new AssertionError(mode);
        }}catch(Throwable t){result=error(t);}
        rows.add(mode+" "+result+" "+state(eq,source)+" events="+events);
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(String mode:List.of("empty","present-empty","present-null","clear","self","set-all","null-source","null-key-set","null-key-get","entry","tick","tick-throw","tick-null","drop","drop-throw","drop-null","codec","codec-null","codec-empty"))run(mode);
        Files.write(Path.of(args[0]),rows);System.out.println("EQUIPMENT_STATE rows="+rows.size());
    }
}
