import java.nio.file.*;
import java.util.*;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import static org.mockito.Mockito.*;

/** Same public ItemStack.copy observer on Frozen/pre/current; effectful Item dispatch is mocked. */
public final class ItemStackCopyEffectsReference {
    static String state(ItemStack stack, Item item) {
        return stack==null?"absent":"count="+stack.getCount()+"/sameItem="+(stack.getItem()==item)
            +"/repair="+stack.get(DataComponents.REPAIR_COST)+"/pop="+stack.getPopTime()+"/patch="+stack.getComponentsPatch();
    }
    static String run(String mode) {
        Item item=mock(Item.class,withSettings().mockMaker("mock-maker-subclass"));
        doReturn(Items.STICK.components()).when(item).components();
        ItemStack[] source={null},inner={null};boolean[] copying={false},nested={false};var events=new ArrayList<String>();
        AssertionError expected=new AssertionError("original copy dispatch failure");
        doAnswer(call->{
            if(!copying[0]){events.add("construct");return item;}
            events.add((nested[0]?"inner":"outer")+":"+source[0].getCount()+":"+source[0].get(DataComponents.REPAIR_COST));
            if(!nested[0])switch(mode){
                case "mutate","throw" -> {source[0].set(DataComponents.REPAIR_COST,9);source[0].setCount(7);if(mode.equals("throw"))throw expected;}
                case "nested" -> {nested[0]=true;try{inner[0]=source[0].copy();}finally{nested[0]=false;}source[0].set(DataComponents.REPAIR_COST,9);source[0].setCount(7);}
            }
            return item;
        }).when(item).asItem();
        source[0]=new ItemStack(item,11);source[0].setPopTime(5);source[0].set(DataComponents.REPAIR_COST,1);
        var escaped=source[0].getComponentsPatch();copying[0]=true;ItemStack returned=null;String failure="none";
        try{returned=source[0].copy();}catch(Throwable thrown){failure=thrown.getClass().getName()+":"+thrown.getMessage()+"/same="+(thrown==expected);}
        return mode+" returned="+state(returned,item)+" source="+state(source[0],item)+" inner="+state(inner[0],item)
            +"/returnSameSource="+(returned==source[0])+"/returnSameInner="+(returned!=null&&returned==inner[0])+" escaped="+escaped+" events="+events+" failure="+failure;
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var rows=new ArrayList<String>();for(String mode:List.of("clean","mutate","throw","nested"))rows.add(run(mode));
        Files.write(Path.of(args[0]),rows);System.out.println("ITEM_COPY_EFFECTS rows="+rows.size());
    }
}
