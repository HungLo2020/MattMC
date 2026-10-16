import java.nio.file.*;
import java.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;
import static org.mockito.Mockito.*;

/** Actual public add actor, including original Java callback/error prefixes. */
public final class InventoryAddReference {
    static final List<String> rows=new ArrayList<>();
    static String state(Inventory inv,ItemStack source) {
        var slots=new ArrayList<String>();var aliases=new IdentityHashMap<ItemStack,Integer>();
        for(int i=0;i<36;i++){var s=inv.getNonEquipmentItems().get(i);if(s!=ItemStack.EMPTY)slots.add(i+":"+s.getCount()+":"+s.getPopTime()+":source="+(s==source)+":alias="+aliases.computeIfAbsent(s,k->aliases.size()));}
        return "source="+source.getCount()+":"+source.getPopTime()+"/selected="+inv.getSelectedSlot()+"/changed="+inv.getTimesChanged()+"/slots="+slots;
    }
    static String error(Throwable failure) {return failure.getClass().getName()+":"+failure.getMessage()+"/cause="+(failure.getCause()==null?"none":failure.getCause().getClass().getName()+":"+failure.getCause().getMessage());}
    static void run(String mode) {
        var events=new ArrayList<String>();Player player=mock(Player.class,withSettings().mockMaker("mock-maker-subclass"));
        doAnswer(call->{events.add("creative");return mode.contains("creative");}).when(player).hasInfiniteMaterials();
        Inventory[] owner={null};var expected=new AssertionError("original equipment failure");
        var equipment=new EntityEquipment(){@Override public ItemStack get(EquipmentSlot slot){
            events.add("equipment:"+slot);
            if(mode.equals("equipment-throw"))throw expected;
            if(mode.equals("nested")) {Inventory other=new Inventory(null,new EntityEquipment());events.add("nested="+other.add(2,new ItemStack(Items.STICK,3)));owner[0].setSelectedSlot(8);owner[0].setItem(3,new ItemStack(Items.STICK,60));}
            return super.get(slot);
        }};
        Inventory inv=new Inventory(player,equipment);owner[0]=inv;ItemStack source=new ItemStack(Items.STICK,11);int index=-1;
        if(mode.startsWith("full")||mode.startsWith("partial"))for(int i=0;i<36;i++)inv.setItem(i,new ItemStack(Items.DIRT,64));
        if(mode.startsWith("partial"))inv.setItem(0,new ItemStack(Items.STICK,60));
        if(mode.equals("alias")){inv.setItem(0,source);index=0;}
        if(mode.startsWith("damaged")){source=new ItemStack(Items.DIAMOND_SWORD);source.setDamageValue(1);index=mode.endsWith("bad")?40:3;}
        if(mode.equals("explicit-negative"))index=-2;
        if(mode.equals("explicit-equipment"))index=40;
        if(mode.equals("empty"))source=ItemStack.EMPTY;
        if(mode.equals("air"))source=new ItemStack(Items.AIR,11);
        if(mode.equals("negative-count"))source.setCount(-11);
        if(mode.equals("selected-merge"))inv.setItem(0,new ItemStack(Items.STICK,60));
        if(mode.equals("offhand-merge"))equipment.set(EquipmentSlot.OFFHAND,new ItemStack(Items.STICK,60));
        if(mode.equals("main-aliases")){var shared=new ItemStack(Items.STICK,60);inv.setItem(2,shared);inv.setItem(3,shared);}
        if(mode.equals("overfull-explicit")){inv.setItem(0,new ItemStack(Items.STICK,80));index=0;}
        String result;try{result="added="+inv.add(index,source);}catch(Throwable failure){result=error(failure)+"/same="+(failure.getCause()==expected);}
        rows.add(mode+" "+result+" "+state(inv,source)+" events="+events);
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(String mode:List.of("empty","air","negative-count","clean","selected-merge","offhand-merge","main-aliases","alias","full","full-creative","partial","partial-creative","damaged","damaged-bad","explicit-negative","explicit-equipment","overfull-explicit","equipment-throw","nested"))run(mode);
        Files.write(Path.of(args[0]),rows);System.out.println("INVENTORY_ADD rows="+rows.size());
    }
}
