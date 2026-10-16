import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.component.*;

/** Same-JVM original fastutil cursor controls, compiled unchanged for all three runtimes. */
public final class DefaultIteratorSkipReference {
    static List<String> consume(ObjectIterator<DataComponentType<?>> it,int amount,boolean remove,IdentityHashMap<DataComponentType<?>,Integer> labels) {
        var events=new ArrayList<String>();
        try{events.add("skip="+it.skip(amount));}catch(Throwable t){events.add("skip="+t.getClass().getName()+":"+t.getMessage());}
        if(remove)try{it.remove();events.add("removed");}catch(Throwable t){events.add("remove="+t.getClass().getName()+":"+t.getMessage());}
        try{for(int guard=0;it.hasNext()&&guard<30;guard++)events.add("next="+labels.get(it.next()));events.add("end="+it.hasNext());}
        catch(Throwable t){events.add("next="+t.getClass().getName()+":"+t.getMessage());}
        return events;
    }
    static void run(int count,int amount,boolean remove) {
        var input=new Reference2ObjectArrayMap<DataComponentType<?>,Object>();var builder=DataComponentMap.builder();
        var labels=new IdentityHashMap<DataComponentType<?>,Integer>();
        for(int i=0;i<count;i++){var key=DataComponentType.<Integer>builder().persistent(Codec.INT).build();labels.put(key,i);input.put(key,i);builder.set(key,i);}
        var actual=builder.build();Reference2ObjectMap<DataComponentType<?>,Object> expected=count<8?new Reference2ObjectArrayMap<>(input):new Reference2ObjectOpenHashMap<>(input);
        @SuppressWarnings("unchecked") var iterator=(ObjectIterator<DataComponentType<?>>)actual.keySet().iterator();
        var left=consume(iterator,amount,remove,labels);var right=consume(expected.keySet().iterator(),amount,remove,labels);
        if(!left.equals(right)||actual.size()!=expected.size())throw new AssertionError(count+"/"+amount+"/"+remove+" actual="+left+" expected="+right+" sizes="+actual.size()+":"+expected.size());
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();var rows=new ArrayList<String>();int failures=0;
        for(int count:new int[]{2,12})for(int amount:new int[]{-1,-2,0,1,3,Integer.MAX_VALUE,Integer.MIN_VALUE})for(boolean remove:new boolean[]{false,true}) {
            try{run(count,amount,remove);rows.add(count+"/"+amount+"/"+remove+" exact=true");}
            catch(AssertionError difference){rows.add(difference.getMessage());failures++;}
        }
        Files.write(Path.of(args[0]),rows);System.out.println("DEFAULT_SKIP rows="+rows.size());if(failures!=0)throw new AssertionError("Skip differences="+failures);
    }
}
