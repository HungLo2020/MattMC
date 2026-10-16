import java.nio.file.*;
import java.util.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;

/** Shared unedited callers, captured iterator bounds and original virtual slot effects. */
public final class ContainerSlotReference {
    static final List<String> rows=new ArrayList<>();
    static String stack(ItemStack s){return s==null?"NULL":s==ItemStack.EMPTY?"EMPTY":s.getCount()+":"+s.getPopTime();}
    static String error(Throwable t){return t.getClass().getName()+":"+t.getMessage();}
    static class Probe implements Container {
        final ItemStack[] values={new ItemStack(Items.STICK,11),ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};
        final List<String> events=new ArrayList<>();int size=2,calls;boolean throwGet;
        public int getContainerSize(){events.add("size="+size);return size;}
        public ItemStack getItem(int i){events.add("get="+i);if(throwGet&&calls++==0)throw new AssertionError("original getter failure");return values[i];}
        public void setItem(int i,ItemStack s){events.add("set="+i+":"+stack(s));values[i]=s;}
        public ItemStack removeItem(int i,int amount){events.add("remove="+i+":"+amount);return values[i].split(amount);}
        public ItemStack removeItemNoUpdate(int i){var old=values[i];values[i]=ItemStack.EMPTY;return old;}
        public boolean isEmpty(){return false;}public void clearContent(){Arrays.fill(values,ItemStack.EMPTY);}public boolean stillValid(Player player){return true;}
        public void setChanged(){events.add("changed");}
    }
    static void run(String mode) {
        final Probe c=new Probe();Probe output=c;ItemStack source=new ItemStack(Items.STICK,11);source.setPopTime(3);String result="ok";
        Slot slot=new Slot(c,0,0,0){
            @Override public void setByPlayer(ItemStack next,ItemStack old){c.events.add("hook="+stack(old)+">"+stack(next));super.setByPlayer(next,old);}
            @Override public int getMaxStackSize(ItemStack s){c.events.add("capacity");if(mode.equals("insert-reentry"))c.setItem(0,new ItemStack(Items.DIRT,5));return super.getMaxStackSize(s);}
            @Override public void onTake(Player player,ItemStack s){c.events.add("take="+stack(s));if(mode.equals("take-throw"))throw new AssertionError("original onTake failure");super.onTake(player,s);}
        };
        try {switch(mode) {
            case "iterator" -> {var it=c.iterator();c.size=4;c.values[1]=source;var seen=new ArrayList<String>();while(it.hasNext())seen.add(stack(it.next()));result=seen.toString();}
            case "iterator-error" -> {c.throwGet=true;var it=new Container.ContainerIterator(c);try{it.next();}catch(Throwable t){c.events.add(error(t));}result="next="+stack(it.next());}
            case "iterator-end" -> {c.size=1;var it=c.iterator();it.next();it.next();}
            case "custom-iterator" -> {Probe custom=new Probe(){@Override public Iterator<ItemStack> iterator(){events.add("custom");return List.of(source).iterator();}};result="found="+custom.hasAnyMatching(s->{custom.events.add("predicate="+stack(s));return s==source;});output=custom;}
            case "set" -> slot.setByPlayer(source);
            case "insert","insert-reentry" -> {c.values[0]=ItemStack.EMPTY;result="same="+(slot.safeInsert(source,5)==source);}
            case "insert-alias" -> {c.values[0]=source;result="same="+(slot.safeInsert(source,5)==source);}
            case "take","take-throw" -> {c.values[0]=source;result="taken="+stack(slot.safeTake(11,11,null));}
            case "null-container" -> {Slot empty=new Slot(null,0,0,0);empty.getItem();}
            default -> throw new AssertionError(mode);
        }}catch(Throwable t){result=error(t);}
        var state=new ArrayList<String>();for(var s:output.values)state.add(stack(s)+":source="+(s==source));rows.add(mode+" "+result+" source="+stack(source)+" slots="+state+" events="+output.events);
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(String mode:List.of("iterator","iterator-error","iterator-end","custom-iterator","set","insert","insert-reentry","insert-alias","take","take-throw","null-container"))run(mode);
        Files.write(Path.of(args[0]),rows);System.out.println("CONTAINER_SLOT rows="+rows.size());
    }
}
