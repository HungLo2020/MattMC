import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import static org.mockito.Mockito.*;

/** Same real ChestMenu and exact ChestBlockEntity; only external world effects are mocked. */
public final class StockChestTransferEffectsReference {
    static HolderLookup.Provider registries;
    static java.lang.reflect.Method getItems,setItems;
    static final List<String> rows=new ArrayList<>();
    @SuppressWarnings("unchecked") static NonNullList<ItemStack> raw(ChestBlockEntity c) throws Exception {return (NonNullList<ItemStack>)getItems.invoke(c);}
    @SuppressWarnings("unchecked") static Map<DataComponentType<?>,Object> prototype(ItemStack source) throws Exception {
        var p=source.getPrototype();var method=p.getClass().getDeclaredMethod("map");method.setAccessible(true);return (Map<DataComponentType<?>,Object>)method.invoke(p);
    }
    static void run(String name) throws Exception {
        var chest=new ChestBlockEntity(BlockPos.ZERO,Blocks.CHEST.defaultBlockState());chest.setRemoved();
        var inventory=new Inventory(null,new EntityEquipment());var menu=ChestMenu.threeRows(1,inventory,chest);chest.clearRemoved();
        var source=new ItemStack(Items.STICK,11);inventory.setItem(9,source);
        if(!name.startsWith("empty"))chest.setItem(0,new ItemStack(Items.STICK,60));
        Throwable[] originalFailure={null};
        var originalTarget=raw(chest).get(0);var events=new ArrayList<String>();int[] dirty={0};boolean[] reentered={false};
        Level level=mock(Level.class,withSettings().mockMaker("mock-maker-subclass"));
        doAnswer(call->{
            int n=++dirty[0];events.add("dirty"+n+":source="+source.getCount()+":target="+originalTarget.getCount());
            if(name.equals("empty-throw"+n) || name.equals("merge-throw"+n)){var failure=new IllegalStateException("dirty failure "+n);originalFailure[0]=failure;throw failure;}
            if(name.equals("merge-error") && n==1){var failure=new AssertionError("original dirty Error");originalFailure[0]=failure;throw failure;}
            if(n==1)switch(name){
                case "slot-loss" -> menu.slots.set(1,new Slot(chest,1,0,0){@Override public ItemStack getItem(){events.add("custom-get");return super.getItem();}});
                case "sublist-loss" -> menu.slots.subList(1,2).set(0,new Slot(chest,1,0,0){@Override public ItemStack getItem(){events.add("sublist-custom-get");return super.getItem();}});
                case "iterator-loss" -> {var iterator=menu.slots.listIterator(1);iterator.next();iterator.set(new Slot(chest,1,0,0){@Override public ItemStack getItem(){events.add("iterator-custom-get");return super.getItem();}});}
                case "remove-loss" -> menu.slots.remove(1);
                case "native-list-loss" -> setItems.invoke(chest,raw(new ChestBlockEntity(BlockPos.ZERO,Blocks.CHEST.defaultBlockState())));
                case "list-loss" -> setItems.invoke(chest,NonNullList.withSize(27,ItemStack.EMPTY));
                case "loot-loss" -> chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,ResourceLocation.withDefaultNamespace("chests/simple_dungeon")));
                case "patch-loss" -> source.set(DataComponents.REPAIR_COST,9);
                case "default-loss" -> prototype(source).remove(DataComponents.MAX_STACK_SIZE);
                case "reenter" -> {if(!reentered[0]){reentered[0]=true;events.add("inner="+menu.quickMoveStack(null,27).getCount());}}
            }
            return null;
        }).when(level).blockEntityChanged(BlockPos.ZERO);
        doAnswer(call->{events.add("neighbour");return null;}).when(level).updateNeighbourForOutputSignal(BlockPos.ZERO,Blocks.CHEST);
        when(level.getServer()).thenAnswer(call->{events.add("server");return null;});
        chest.setLevel(level);
        Object savedMax=prototype(source).get(DataComponents.MAX_STACK_SIZE);
        String result;
        try{var returned=menu.quickMoveStack(null,27);result="return="+StockChestTransferReference.state(returned)+"/source="+(returned==source);}
        catch(Throwable failure){result="throw="+failure.getClass().getName()+":"+failure.getMessage()+"/sameThrowable="+(failure==originalFailure[0]);}
        var slots=new ArrayList<String>();IdentityHashMap<ItemStack,Integer> aliases=new IdentityHashMap<>();
        for(int i=0;i<27;i++){var s=raw(chest).get(i);if(!s.isEmpty())slots.add(i+":"+StockChestTransferReference.state(s)+"/alias="+aliases.computeIfAbsent(s,k->aliases.size())+"/source="+(s==source));}
        String saved=chest.saveWithFullMetadata(registries).toString();
        rows.add(name+" "+result+" source="+StockChestTransferReference.state(source)+"/inventorySame="+(inventory.getItem(9)==source)+" target="+originalTarget.getCount()+" slots="+slots+" events="+events+" saved="+saved);
        if(name.equals("default-loss"))prototype(source).put(DataComponents.MAX_STACK_SIZE,savedMax);
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();registries=net.minecraft.server.RegistryLayer.createRegistryAccess().compositeAccess();
        getItems=ChestBlockEntity.class.getDeclaredMethod("getItems");getItems.setAccessible(true);setItems=ChestBlockEntity.class.getDeclaredMethod("setItems",NonNullList.class);setItems.setAccessible(true);
        for(String name:List.of("empty-clean","empty-throw1","empty-throw2","empty-throw3","merge-clean","merge-throw1","merge-throw2","merge-throw3","merge-throw4","slot-loss","sublist-loss","iterator-loss","remove-loss","merge-error","native-list-loss","list-loss","loot-loss","patch-loss","default-loss","reenter"))run(name);
        Files.write(Path.of(args[0]),rows);System.out.println("STOCK_CHEST_EFFECTS rows="+rows.size());
    }
}
