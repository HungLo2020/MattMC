import java.nio.file.*;
import java.util.*;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;
import static org.mockito.Mockito.*;

/** Inventory scan baseline: live main slots and effectful component equality. */
public final class InventoryScanReference {
    static final DataComponentType<Probe> KEY=DataComponentType.<Probe>builder().persistent(Codec.STRING.xmap(Probe::new,p->p.name)).build();
    static final class Probe {
        final String name;Runnable effect=()->{};Probe(String name){this.name=name;}
        public boolean equals(Object value){effect.run();return value instanceof Probe p&&name.equals(p.name);}
        public int hashCode(){return name.hashCode();}
    }
    static String run(String mode) {
        var events=new ArrayList<String>();Inventory inv=new Inventory(null,new EntityEquipment());var source=new ItemStack(Items.STICK,11);
        inv.setSelectedSlot(8);inv.setItem(8,new ItemStack(Items.DIRT,64));inv.setItem(0,new ItemStack(Items.DIRT,64));inv.setItem(3,new ItemStack(Items.STICK,60));String result;
        if(mode.equals("hotbar-full"))for(int i=0;i<9;i++)inv.setItem(i,new ItemStack(Items.DIRT,64));
        if(mode.startsWith("equality")) {
            Probe left=new Probe("same"),right=new Probe("same");source.set(KEY,left);inv.getItem(3).set(KEY,right);
            Runnable effect=()->{events.add("equal");inv.setSelectedSlot(2);inv.setItem(5,new ItemStack(Items.STICK,1));if(mode.endsWith("throw"))throw new AssertionError("original equality failure");};left.effect=effect;right.effect=effect;
        }
        if(mode.startsWith("enchantment")) {
            for(int i=0;i<9;i++)inv.setItem(i,new ItemStack(Items.STICK,11));
            var enchantments=mock(net.minecraft.world.item.enchantment.ItemEnchantments.class,withSettings().mockMaker("mock-maker-subclass"));
            doAnswer(call->{events.add("enchantment/free="+inv.getFreeSlot());inv.setSelectedSlot(0);if(mode.endsWith("throw"))throw new AssertionError("original enchantment failure");return false;}).when(enchantments).isEmpty();
            inv.getItem(8).set(DataComponents.ENCHANTMENTS,enchantments);
        }
        if(mode.equals("add-pick-null"))for(int i=0;i<9;i++)inv.setItem(i,new ItemStack(Items.STICK,11));
        try {result=switch(mode) {
            case "enchantment","enchantment-throw" -> "slot="+inv.getSuitableHotbarSlot();
            case "matching-null" -> "slot="+inv.findSlotMatchingItem(null);
            case "remaining-null" -> "slot="+inv.getSlotWithRemainingSpace(null);
            case "crafting-null" -> "slot="+inv.findSlotMatchingCraftingIngredient(Items.STICK.builtInRegistryHolder(),null);
            case "add-pick-null" -> {inv.addAndPickItem(null);yield "ok";}
            case "hotbar","hotbar-full" -> "slot="+inv.getSuitableHotbarSlot();
            case "remaining" -> "slot="+inv.getSlotWithRemainingSpace(source);
            case "crafting" -> "slot="+inv.findSlotMatchingCraftingIngredient(Items.STICK.builtInRegistryHolder(),ItemStack.EMPTY);
            case "contains" -> "contains="+inv.contains(source);
            case "predicate" -> "contains="+inv.contains(s->{events.add("visit="+s.getCount());if(events.size()==2)inv.setItem(1,source);return s==source;});
            default -> "slot="+inv.findSlotMatchingItem(source);
        };}catch(Throwable failure){result=failure.getClass().getName()+":"+failure.getMessage();}
        var slots=new ArrayList<String>();for(int i=0;i<36;i++){var s=inv.getItem(i);if(!s.isEmpty())slots.add(i+":"+s.getCount()+":source="+(s==source));}
        return mode+" "+result+"/selected="+inv.getSelectedSlot()+"/source="+source.getCount()+"/slots="+slots+"/events="+events;
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();var rows=new ArrayList<String>();
        for(String mode:List.of("hotbar","hotbar-full","remaining","matching","crafting","contains","predicate","equality","equality-throw","matching-null","remaining-null","crafting-null","add-pick-null","enchantment","enchantment-throw"))rows.add(run(mode));
        Files.write(Path.of(args[0]),rows);System.out.println("INVENTORY_SCAN rows="+rows.size());
    }
}
