import com.google.common.collect.Iterators;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.component.*;

/** Same-JVM original fastutil comparison avoids identity-hash/order normalization. */
public final class DefaultCursorContractReference {
    static final List<String> rows=new ArrayList<>();
    static String error(Throwable t){return t.getClass().getName()+":"+t.getMessage();}
    static void run(int count,String mode){
        var input=new Reference2ObjectArrayMap<DataComponentType<?>,Object>();var builder=DataComponentMap.builder();
        var labels=new IdentityHashMap<DataComponentType<?>,Integer>();
        for(int i=0;i<count;i++){var key=DataComponentType.<Integer>builder().persistent(Codec.INT).build();labels.put(key,i);input.put(key,i);builder.set(key,i);}
        var actual=builder.build();Reference2ObjectMap<DataComponentType<?>,Object> expected=count<8?new Reference2ObjectArrayMap<>(input):new Reference2ObjectOpenHashMap<>(input);
        if(actual.keySet()!=actual.keySet())throw new AssertionError("escaped key view identity changed");
        if(mode.startsWith("set-")){
            var a=setConsume(actual.keySet(),()->actual.keySet().clear(),labels,mode.substring(4));
            var b=setConsume(expected.keySet(),expected::clear,labels,mode.substring(4));
            if(!a.equals(b))throw new AssertionError("default set foreach "+count+"/"+mode+" actual="+a+" expected="+b);
            rows.add(count+" "+mode+" exact=true");return;
        }
        if(mode.equals("split-child")){
            var a=splitConsume(actual.keySet().spliterator(),labels);var b=splitConsume(expected.keySet().spliterator(),labels);
            if(!a.equals(b))throw new AssertionError("default split child "+count+" actual="+a+" expected="+b);
            rows.add(count+" split-child exact=true");return;
        }
        if(mode.equals("spliterator")) {
            int a=actual.keySet().spliterator().characteristics(),b=expected.keySet().spliterator().characteristics();
            if(a!=b)throw new AssertionError("default characteristics "+count+" actual="+a+" expected="+b);
            rows.add(count+" spliterator exact=true");return;
        }
        Iterator<String> left=mode.startsWith("keys-")?keys(actual.keySet().iterator(),labels):
            Iterators.transform(actual.iterator(),e->labels.get(e.type())+":"+e.value());
        Iterator<String> right=mode.startsWith("keys-")?keys(expected.keySet().iterator(),labels):
            Iterators.transform(Reference2ObjectMaps.fastIterator(expected),e->labels.get(e.getKey())+":"+e.getValue());
        String action=mode.startsWith("keys-")?mode.substring(5):mode;
        List<String> a=consume(left,()->actual.keySet().clear(),action);List<String>b=consume(right,expected::clear,action);
        if(!a.equals(b))throw new AssertionError("default cursor "+count+"/"+mode+" actual="+a+" expected="+b);
        rows.add(count+" "+mode+" exact=true");
    }
    static List<String> setConsume(Set<DataComponentType<?>> set,Runnable clear,IdentityHashMap<DataComponentType<?>,Integer> labels,String mode){
        var events=new ArrayList<String>();boolean[] first={true};try{set.forEach(k->{events.add("visit="+labels.get(k));if(first[0]){first[0]=false;if(mode.equals("clear"))clear.run();else throw new IllegalStateException("original set failure");}});events.add("complete");}catch(Throwable e){events.add(error(e));}return events;
    }
    static List<String> splitConsume(Spliterator<DataComponentType<?>> source,IdentityHashMap<DataComponentType<?>,Integer> labels){
        var events=new ArrayList<String>();var child=source.trySplit();events.add("parent="+source.characteristics()+":"+source.estimateSize());
        if(child!=null){events.add("child="+child.characteristics()+":"+child.estimateSize());child.forEachRemaining(k->events.add("child="+labels.get(k)));}
        source.forEachRemaining(k->events.add("parent="+labels.get(k)));return events;
    }
    static Iterator<String> keys(Iterator<DataComponentType<?>> source,IdentityHashMap<DataComponentType<?>,Integer> labels){return new Iterator<>(){
        public boolean hasNext(){return source.hasNext();}public String next(){return ""+labels.get(source.next());}
        public void forEachRemaining(java.util.function.Consumer<? super String> action){source.forEachRemaining(k->action.accept(""+labels.get(k)));}
    };}
    static List<String> consume(Iterator<String> iterator,Runnable clear,String mode){
        var events=new ArrayList<String>();boolean[] first={true};
        try{iterator.forEachRemaining(value->{events.add("visit="+value);if(first[0]){first[0]=false;if(mode.equals("clear"))clear.run();if(mode.equals("throw"))throw new IllegalStateException("original foreach failure");}});events.add("complete");}
        catch(Throwable t){events.add("foreach="+error(t));}
        try{for(int guard=0;iterator.hasNext()&&guard<20;guard++)events.add("resume="+iterator.next());events.add("end="+iterator.hasNext());}
        catch(Throwable t){events.add("resume="+error(t));}
        return events;
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        int failures=0;for(int count:new int[]{2,12})for(String mode:List.of("clear","throw","keys-clear","keys-throw","spliterator","set-clear","set-throw","split-child"))try{run(count,mode);}catch(AssertionError e){rows.add(e.getMessage());failures++;}
        Files.write(Path.of(args[0]),rows);System.out.println("DEFAULT_CURSOR_CONTRACT rows="+rows.size());if(failures!=0)throw new AssertionError("Cursor differences="+failures);
    }
}
