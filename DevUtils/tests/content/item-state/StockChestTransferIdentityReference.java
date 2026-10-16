import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.ItemLike;

/** Malformed ItemLike and singleton identity remain observable through the real menu. */
public final class StockChestTransferIdentityReference {
    static final List<String> rows=new ArrayList<>();
    static ItemStack malformed(){int[] calls={0};return new ItemStack((ItemLike)()->calls[0]++==0?Items.STICK:null,5);}
    static void run(String kind){
        var chest=new ChestBlockEntity(BlockPos.ZERO,Blocks.CHEST.defaultBlockState());chest.setRemoved();
        var inventory=new Inventory(null,new EntityEquipment());var menu=ChestMenu.threeRows(1,inventory,chest);chest.clearRemoved();
        ItemStack source=kind.equals("null-source")?malformed():new ItemStack(Items.STICK,11);
        ItemStack target=kind.equals("null-target")?malformed():ItemStack.EMPTY;
        chest.setItem(0,target);inventory.setItem(9,source);
        if(kind.equals("singleton-count"))ItemStack.EMPTY.setCount(27);
        String result;
        try{var copy=menu.quickMoveStack(null,27);result="returned="+copy.getCount()+"/sameSource="+(copy==source);}
        catch(Throwable failure){result="thrown="+failure.getClass().getName()+":"+failure.getMessage();}
        rows.add(kind+" "+result+" source="+source.getCount()+" target="+target.getCount()+" slot0="+chest.getItem(0).getCount()+"/target="+(chest.getItem(0)==target)+" slot1="+chest.getItem(1).getCount()+"/target="+(chest.getItem(1)==target)+" sourceStill="+(inventory.getItem(9)==source));
        ItemStack.EMPTY.setCount(0);
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(String kind:List.of("null-source","null-target","singleton-count"))run(kind);
        Files.write(Path.of(args[0]),rows);System.out.println("STOCK_CHEST_IDENTITIES rows="+rows.size());
    }
}
