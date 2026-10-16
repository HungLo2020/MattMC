import java.nio.file.*;
import java.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import static org.mockito.Mockito.*;

/** Captured source-slot effects, null/EMPTY results, reentry and actual repeated caller. */
public final class MenuSourceReference {
    static final List<String> rows=new ArrayList<>();
    static String stack(ItemStack s){return s==null?"NULL":s==ItemStack.EMPTY?"EMPTY":s.getCount()+":"+s.getPopTime();}
    static void run(String kind,String mode,boolean repeated){
        var player=mock(Player.class,withSettings().mockMaker("mock-maker-subclass"));doReturn(true).when(player).canUseSlot(any(EquipmentSlot.class));doReturn(EntityType.PLAYER).when(player).getType();
        var inv=new Inventory(player,new EntityEquipment());doReturn(inv).when(player).getInventory();
        AbstractContainerMenu menu=kind.equals("chest")?ChestMenu.threeRows(1,inv):new InventoryMenu(inv,true,player);int index=kind.equals("chest")?54:9;
        var source=new ItemStack(Items.STICK,11);source.setPopTime(3);var other=new ItemStack(Items.DIRT,7);int main=kind.equals("chest")?0:9;inv.setItem(main,source);
        var events=new ArrayList<String>();boolean[] active={true},once={false};Slot previous=menu.slots.get(index);
        menu.slots.set(index,new Slot(previous.container,previous.getContainerSlot(),0,0){
            @Override public boolean hasItem(){if(!active[0])return super.hasItem();events.add("has");if(mode.equals("has-throw"))throw new AssertionError("original source has failure");return true;}
            @Override public ItemStack getItem(){
                if(!active[0])return super.getItem();events.add("get");
                if(mode.equals("get-throw"))throw new AssertionError("original source getter failure");
                if(mode.equals("get-null"))return null;if(mode.equals("get-empty"))return ItemStack.EMPTY;
                if(!once[0]){once[0]=true;if(mode.equals("get-replace")){events.add("replace");menu.slots.set(index,new Slot(previous.container,previous.getContainerSlot(),0,0));}else{events.add("reenter");menu.setCarried(other);}}
                return super.getItem();
            }
            @Override public void onTake(Player p,ItemStack s){events.add("take="+stack(s));super.onTake(p,s);}
        });
        String result="ok";ItemStack returned=ItemStack.EMPTY;
        try{if(repeated)menu.clicked(index,0,ClickType.QUICK_MOVE,player);else returned=menu.quickMoveStack(player,index);}
        catch(Throwable t){result=t.getClass().getName()+":"+t.getMessage();if(t.getCause()!=null)result+=" cause="+t.getCause().getClass().getName()+":"+t.getCause().getMessage();}
        finally{active[0]=false;}
        var state=new ArrayList<String>();for(int i=0;i<menu.slots.size();i++){var s=menu.slots.get(i).getItem();if(s!=ItemStack.EMPTY)state.add(i+"="+stack(s)+":source="+(s==source)+":return="+(s==returned));}
        rows.add(kind+"/"+mode+"/"+repeated+" "+result+" return="+stack(returned)+" source="+stack(source)+" carried="+stack(menu.getCarried())+":other="+(menu.getCarried()==other)+" slots="+state+" events="+events);
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(String kind:List.of("chest","inventory"))for(String mode:List.of("has-throw","get-throw","get-null","get-empty","get-replace","get-reenter"))for(boolean repeated:new boolean[]{false,true})run(kind,mode,repeated);
        Files.write(Path.of(args[0]),rows);System.out.println("MENU_SOURCE rows="+rows.size());
    }
}
