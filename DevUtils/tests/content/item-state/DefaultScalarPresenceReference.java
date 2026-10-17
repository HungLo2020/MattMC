import java.nio.file.*;
import java.util.*;
import net.minecraft.core.component.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.block.entity.*;

/** Legal raw trusted default maps: component key presence and non-null value differ. */
public final class DefaultScalarPresenceReference {
    @SuppressWarnings({"unchecked","rawtypes"}) static DataComponentMap prototype(boolean nullUnbreakable)throws Exception {
        var builder=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).set(DataComponents.MAX_DAMAGE,100).set(DataComponents.DAMAGE,1);
        var field=builder.getClass().getDeclaredField("map");field.setAccessible(true);((Map)field.get(builder)).put(DataComponents.UNBREAKABLE,nullUnbreakable?null:net.minecraft.util.Unit.INSTANCE);
        return builder.build();
    }
    static ItemStack stack(DataComponentMap proto,int count)throws Exception {
        var constructor=ItemStack.class.getDeclaredConstructor(net.minecraft.world.level.ItemLike.class,int.class,PatchedDataComponentMap.class);constructor.setAccessible(true);
        return constructor.newInstance(Items.STICK,count,new PatchedDataComponentMap(proto));
    }
    static String run(boolean nil)throws Exception {
        var proto=prototype(nil);
        Inventory inv=new Inventory(null,new EntityEquipment());
        var chest=new ChestBlockEntity(net.minecraft.core.BlockPos.ZERO,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        chest.setRemoved();var menu=ChestMenu.threeRows(1,inv,chest);chest.clearRemoved();
        for(int i=0;i<27;i++)chest.setItem(i,new ItemStack(Items.DIRT,64));
        var target=stack(proto,60);chest.setItem(0,target);var source=stack(proto,11);inv.setItem(0,source);
        boolean stackable=source.isStackable();var result=menu.quickMoveStack(null,54);
        return "null="+nil+"/prototypeHas="+proto.has(DataComponents.UNBREAKABLE)+"/stackHas="+source.has(DataComponents.UNBREAKABLE)+"/stackable="+stackable+"/source="+source.getCount()+"/target="+target.getCount()+"/returned="+result.getCount()+"/sameSource="+(result==source);
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var rows=new ArrayList<String>();for(boolean nil:new boolean[]{false,true})rows.add(run(nil));Files.write(Path.of(args[0]),rows);System.out.println("SCALAR_PRESENCE rows="+rows.size());
    }
}
