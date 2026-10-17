import java.nio.file.*;
import java.util.*;
import net.minecraft.core.NonNullList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

/** Original public menu roster/carried contracts before native menu ownership. */
public final class MenuStateReference {
    static final List<String> rows=new ArrayList<>();
    static String value(ItemStack s){return s==null?"NULL":s==ItemStack.EMPTY?"EMPTY":s.getCount()+":"+s.getPopTime();}
    static AbstractContainerMenu menu(String kind){var inv=new Inventory(null,new EntityEquipment());return kind.equals("chest")?ChestMenu.threeRows(1,inv):new InventoryMenu(inv,true,null);}
    static void run(String kind,String mode){
        var menu=menu(kind);NonNullList<Slot> list=menu.slots;Slot first=list.get(0);int size=list.size();
        var source=new ItemStack(Items.STICK,11);source.setPopTime(7);var other=new ItemStack(Items.DIRT,5);var events=new ArrayList<String>();String result="ok";
        try{switch(mode){
            case "shape" -> result="size="+size+",same="+(menu.slots==list)+",first="+first.index+":"+first.getContainerSlot();
            case "set" -> {var slot=new Slot(new SimpleContainer(1),0,3,4);result="old="+(list.set(0,slot)==first)+",now="+(list.get(0)==slot)+",index="+slot.index;}
            case "alias" -> {list.set(1,first);result="same="+(list.get(0)==list.get(1))+",removed="+(list.remove(0)==first)+",next="+(list.get(0)==first);}
            case "add" -> {list.add(0,first);result="same="+(list.get(0)==list.get(1))+",index="+first.index;}
            case "remove" -> result="same="+(list.remove(0)==first)+",next="+list.get(0).index;
            case "get-low" -> list.get(-1);
            case "get-high" -> list.get(size);
            case "set-high" -> list.set(size,first);
            case "set-null-high" -> list.set(size,null);
            case "add-high" -> list.add(size+1,first);
            case "add-null-high" -> list.add(size+1,null);
            case "remove-high" -> list.remove(size);
            case "iterator-live" -> {var it=list.iterator();it.next();list.set(1,first);list.add(first);result="next="+(it.next()==first);int seen=2;while(it.hasNext()){it.next();seen++;}result+=",seen="+seen;}
            case "iterator-remove" -> {var it=list.iterator();result="same="+(it.next()==first);it.remove();result+=",next="+it.next().index;}
            case "sublist" -> {var view=list.subList(0,2);result="old="+(view.set(1,first)!=first)+",same="+(view.get(0)==view.get(1));view.remove(0);result+=",first="+(list.get(0)==first)+",view="+view.size();}
            case "equals-throw" -> {var needle=new Slot(new SimpleContainer(1),0,0,0){@Override public boolean equals(Object item){events.add("equals="+(item==first));throw new AssertionError("original slot equality failure");}};list.remove(needle);}
            case "clear" -> {list.clear();result="empty="+list.isEmpty();}
            case "carried" -> {menu.setCarried(source);result="same="+(menu.getCarried()==source);source.setCount(-7);result+=",value="+value(menu.getCarried());}
            case "carried-null" -> {menu.setCarried(null);result="null="+(menu.getCarried()==null);}
            case "carried-empty" -> {menu.setCarried(ItemStack.EMPTY);result="empty="+(menu.getCarried()==ItemStack.EMPTY);}
            case "initialize" -> {menu.setCarried(source);menu.initializeContents(13,List.of(),other);result="same="+(menu.getCarried()==other)+",id="+menu.getStateId();}
            case "initialize-error" -> {menu.setCarried(source);List<ItemStack> input=new AbstractList<>(){public int size(){events.add("size");return 1;}public ItemStack get(int index){events.add("get="+index);throw new AssertionError("original list getter failure");}};menu.initializeContents(13,input,other);}
            default -> throw new AssertionError(mode);
        }}catch(Throwable t){result=t.getClass().getName()+":"+t.getMessage();}
        rows.add(kind+"/"+mode+" "+result+" size="+list.size()+" carried="+value(menu.getCarried())+":source="+(menu.getCarried()==source)+" events="+events);
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(String kind:List.of("chest","inventory"))for(String mode:List.of("shape","set","alias","add","remove","get-low","get-high","set-high","set-null-high","add-high","add-null-high","remove-high","iterator-live","iterator-remove","sublist","equals-throw","clear","carried","carried-null","carried-empty","initialize","initialize-error"))run(kind,mode);
        Files.write(Path.of(args[0]),rows);System.out.println("MENU_STATE rows="+rows.size());
    }
}
