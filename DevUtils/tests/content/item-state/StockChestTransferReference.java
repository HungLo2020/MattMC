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

/** Same actual menu consumer on Frozen, sealed pre-master and candidate. */
public final class StockChestTransferReference {
    static HolderLookup.Provider registries;
    static final List<String> rows = new ArrayList<>();
    static String state(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "*" + stack.getCount()
            + "/pop=" + stack.getPopTime() + "/patch=" + stack.getComponentsPatch();
    }
    static void run(String name) throws Exception {
        ChestBlockEntity chest = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        // Suppress world opener notifications during fixture construction only.
        chest.setRemoved();
        Inventory inventory = new Inventory(null, new EntityEquipment());
        ChestMenu menu = ChestMenu.threeRows(1, inventory, chest);
        chest.clearRemoved();
        ItemStack source = new ItemStack(Items.STICK, name.equals("many") ? 100 : name.equals("overstack") ? 130 : 11);
        source.setPopTime(7);
        inventory.setItem(9, source);
        switch (name) {
            case "zero" -> source.setCount(0);
            case "one" -> chest.setItem(0, new ItemStack(Items.STICK, 60));
            case "many" -> { for (int i=0;i<27;i++) chest.setItem(i, new ItemStack(Items.STICK, 60)); }
            case "late" -> { for (int i=0;i<26;i++) chest.setItem(i, new ItemStack(Items.DIRT, 64)); chest.setItem(26, new ItemStack(Items.STICK, 60)); }
            case "full" -> { for (int i=0;i<27;i++) chest.setItem(i, new ItemStack(Items.DIRT, 64)); }
            case "alias-source" -> { chest.setItem(0, source); chest.setItem(1, source); }
            case "alias-target" -> { ItemStack target = new ItemStack(Items.STICK, 60); chest.setItem(0,target); chest.setItem(1,target); }
            case "patched" -> source.set(DataComponents.REPAIR_COST, 5);
            case "custom-list" -> { var method = ChestBlockEntity.class.getDeclaredMethod("setItems",NonNullList.class); method.setAccessible(true); method.invoke(chest,NonNullList.withSize(27,ItemStack.EMPTY)); }
        }
        ItemStack result = menu.quickMoveStack(null,27);
        List<String> slots = new ArrayList<>(); IdentityHashMap<ItemStack,Integer> aliases = new IdentityHashMap<>();
        for(int i=0;i<27;i++) { ItemStack s=chest.getItem(i); if(!s.isEmpty()) { int alias=aliases.computeIfAbsent(s,k->aliases.size()); slots.add(i+":"+state(s)+"/a="+alias+"/source="+(s==source)+"/result="+(s==result)); } }
        String saved = chest.saveWithFullMetadata(registries).toString();
        rows.add(name+" return="+state(result)+"/emptySingleton="+(result==ItemStack.EMPTY)+"/source="+(result==source)
            +" source="+state(source)+" inventorySame="+(inventory.getItem(9)==source)+" slots="+slots+" saved="+saved);
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        registries = net.minecraft.server.RegistryLayer.createRegistryAccess().compositeAccess();
        for(String name:List.of("zero","empty","one","many","late","full","overstack","alias-source","alias-target","patched","custom-list")) run(name);
        if (Boolean.getBoolean("mattmc.require.native.transfer")) {
            var descriptor=java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_LONG, java.lang.foreign.ValueLayout.JAVA_LONG);
            var loader=Class.forName("net.minecraft.util.NativeLibraryLoader");
            var method=loader.getMethod("downcallHandle",String.class,String.class,java.lang.foreign.FunctionDescriptor.class);
            var counter=(java.lang.invoke.MethodHandle)method.invoke(null,"mattmc_rust","mattmc_item_transfer_counter",descriptor);
            for(long i=0;i<3;i++) {long value;try{value=(long)counter.invokeExact(i);}catch(Throwable e){throw new AssertionError(e);}if(value<=0)throw new AssertionError("Vacuous native transfer counter"+i);System.out.println("NATIVE_TRANSFER counter="+i+" value="+value);}
        }
        Files.write(Path.of(args[0]),rows); System.out.println("STOCK_CHEST_TRANSFER rows="+rows.size());
    }
}
