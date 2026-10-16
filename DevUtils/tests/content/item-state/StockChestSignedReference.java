import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Identical real-menu observer on original Frozen/pre and candidate; no native API dependency. */
public final class StockChestSignedReference {
    static String state(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "*" + stack.getCount()
            + "/empty=" + stack.isEmpty() + "/singleton=" + (stack==ItemStack.EMPTY) + "/patch=" + stack.getComponentsPatch();
    }
    @SuppressWarnings("unchecked") static Map<DataComponentType<?>,Object> defaults(ItemStack stack) throws Exception {
        var prototype=stack.getPrototype();var accessor=prototype.getClass().getDeclaredMethod("map");accessor.setAccessible(true);
        return (Map<DataComponentType<?>,Object>)accessor.invoke(prototype);
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var registries=net.minecraft.server.RegistryLayer.createRegistryAccess().compositeAccess();var rows=new ArrayList<String>();
        for(int max:new int[]{-7,0})for(boolean cleared:new boolean[]{false,true}) {
            var chest=new ChestBlockEntity(BlockPos.ZERO,Blocks.CHEST.defaultBlockState());chest.setRemoved();
            var inventory=new Inventory(null,new EntityEquipment());var menu=ChestMenu.threeRows(1,inventory,chest);chest.clearRemoved();
            ItemStack source=new ItemStack(Items.STICK,11);if(cleared)((PatchedDataComponentMap)source.getComponents()).clearPatch();
            inventory.setItem(9,source);var defaults=defaults(source);Object original=defaults.put(DataComponents.MAX_STACK_SIZE,max);
            try {
                ItemStack returned=menu.quickMoveStack(null,27);var slots=new ArrayList<String>();
                for(int i=0;i<27;i++)slots.add(i+":"+state(chest.getItem(i))+"/source="+(chest.getItem(i)==source)+"/returned="+(chest.getItem(i)==returned));
                rows.add("max="+max+"/cleared="+cleared+" return="+state(returned)+" source="+state(source)
                    +"/inventorySame="+(inventory.getItem(9)==source)+" slots="+slots+" saved="+chest.saveWithFullMetadata(registries));
            } finally {defaults.put(DataComponents.MAX_STACK_SIZE,original);}
        }
        Files.write(Path.of(args[0]),rows);System.out.println("STOCK_SIGNED rows="+rows.size());
    }
}
