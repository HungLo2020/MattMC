import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Original replaceable chest backing, live Slot callers and escaped list identities. */
public final class StockContainerBindingReference {
    static final List<String> rows=new ArrayList<>();
    static final Method get,set;
    static {try{get=ChestBlockEntity.class.getDeclaredMethod("getItems");set=ChestBlockEntity.class.getDeclaredMethod("setItems",NonNullList.class);get.setAccessible(true);set.setAccessible(true);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @SuppressWarnings("unchecked") static NonNullList<ItemStack> list(ChestBlockEntity chest)throws Exception{return (NonNullList<ItemStack>)get.invoke(chest);}
    static void replace(ChestBlockEntity chest,NonNullList<ItemStack> items)throws Exception{set.invoke(chest,items);}
    static ChestBlockEntity chest(){return new ChestBlockEntity(BlockPos.ZERO,Blocks.CHEST.defaultBlockState());}
    static String value(ItemStack s){return s==null?"NULL":s==ItemStack.EMPTY?"EMPTY":s.getItem()+":"+s.getCount();}
    static void run(String mode)throws Exception {
        var a=chest();var b=chest();var original=list(a);var other=list(b);var source=new ItemStack(Items.STICK,11);original.set(0,source);
        var slot=new Slot(a,0,7,9);String result="ok";
        try{switch(mode){
            case "replace" -> {var next=NonNullList.withSize(27,ItemStack.EMPTY);var target=new ItemStack(Items.DIRT,7);next.set(0,target);replace(a,next);result="list="+(list(a)==next)+",slot="+(slot.getItem()==target)+",old="+(original.get(0)==source);}
            case "swap" -> {var target=new ItemStack(Items.DIRT,7);other.set(0,target);ChestBlockEntity.swapContents(a,b);result="a="+(list(a)==other)+",b="+(list(b)==original)+",slot="+(slot.getItem()==target)+",old="+(b.getItem(0)==source);}
            case "self-swap" -> {ChestBlockEntity.swapContents(a,a);result="list="+(list(a)==original)+",slot="+(slot.getItem()==source);}
            case "shared" -> {replace(b,original);source.setCount(5);result="a="+(a.getItem(0)==source)+",b="+(b.getItem(0)==source)+",count="+b.getItem(0).getCount();}
            case "empty-replace" -> {replace(a,NonNullList.withSize(27,ItemStack.EMPTY));result="empty="+!slot.hasItem()+",old="+value(original.get(0));}
            case "short" -> {var next=NonNullList.withSize(1,ItemStack.EMPTY);replace(a,next);result="size="+a.getContainerSize()+",list="+list(a).size()+",slot="+value(slot.getItem());}
            case "short-error" -> {replace(a,NonNullList.withSize(1,ItemStack.EMPTY));a.getItem(1);}
            case "null" -> {replace(a,null);result="null="+(list(a)==null)+",old="+value(original.get(0));}
            case "null-get" -> {replace(a,null);slot.getItem();}
            case "restore" -> {replace(a,null);replace(a,original);result="list="+(list(a)==original)+",slot="+(slot.getItem()==source);}
            default -> throw new AssertionError(mode);
        }}catch(Throwable t){result=t.getClass().getName()+":"+t.getMessage();}
        rows.add(mode+" "+result);
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(String mode:List.of("replace","swap","self-swap","shared","empty-replace","short","short-error","null","null-get","restore"))run(mode);
        Files.write(Path.of(args[0]),rows);System.out.println("STOCK_CONTAINER rows="+rows.size());
    }
}
