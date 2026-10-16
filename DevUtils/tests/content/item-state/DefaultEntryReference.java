import java.nio.file.*;
import java.util.*;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.component.*;

/** Live hash entries and snapshot array entries, using only nonstructural writes. */
public final class DefaultEntryReference {
    @SuppressWarnings("unchecked") static Reference2ObjectMap<DataComponentType<?>,Object> backing(DataComponentMap map)throws Exception {
        var method=map.getClass().getDeclaredMethod("map");method.setAccessible(true);return (Reference2ObjectMap<DataComponentType<?>,Object>)method.invoke(map);
    }
    static String consume(Reference2ObjectMap<DataComponentType<?>,Object> map,boolean fast,String mode) {
        var iterator=fast?Reference2ObjectMaps.fastIterator(map):map.reference2ObjectEntrySet().iterator();
        Reference2ObjectMap.Entry<DataComponentType<?>,Object> entry;
        if(mode.equals("bulk")) {
            var saved=new ArrayList<Reference2ObjectMap.Entry<DataComponentType<?>,Object>>();
            if(fast)Reference2ObjectMaps.fastForEach(map,saved::add);else map.reference2ObjectEntrySet().forEach(saved::add);
            entry=saved.get(0);var key=entry.getKey();Object old=entry.getValue();map.put(key,1001);
            return "sameOld="+(entry.getValue()==old)+"/value="+entry.getValue()+"/map="+map.get(key)+"/reused="+(saved.get(0)==saved.get(1));
        }
        entry=iterator.next();var key=entry.getKey();Object old=entry.getValue();var events=new ArrayList<String>();
        if(mode.equals("replace"))map.put(key,1001);
        if(mode.equals("set"))try{events.add("old="+entry.setValue(1002));}catch(Throwable failure){events.add(failure.getClass().getName()+":"+failure.getMessage());}
        events.add("sameOld="+(entry.getValue()==old));events.add("value="+entry.getValue());events.add("map="+map.get(key));
        events.add("hash="+(entry.hashCode()==(System.identityHashCode(key)^Objects.hashCode(entry.getValue()))));
        events.add("equal="+entry.equals(new AbstractReference2ObjectMap.BasicEntry<>(key,map.get(key))));
        var second=iterator.next();events.add("reused="+(entry==second));return events.toString();
    }
    static String run(int count,boolean fast,String mode)throws Exception {
        var input=new Reference2ObjectArrayMap<DataComponentType<?>,Object>();var builder=DataComponentMap.builder();
        for(int i=0;i<count;i++){var key=DataComponentType.<Integer>builder().persistent(Codec.INT).build();input.put(key,i);builder.set(key,i);}
        var actual=backing(builder.build());Reference2ObjectMap<DataComponentType<?>,Object> expected=count<8?new Reference2ObjectArrayMap<>(input):new Reference2ObjectOpenHashMap<>(input);
        var a=consume(actual,fast,mode);var b=consume(expected,fast,mode);
        return count+"/fast="+fast+"/"+mode+"/exact="+a.equals(b)+(a.equals(b)?"":"/actual="+a+"/expected="+b);
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();var rows=new ArrayList<String>();
        for(int count:new int[]{2,12})for(boolean fast:new boolean[]{false,true})for(String mode:List.of("plain","replace","set","bulk"))rows.add(run(count,fast,mode));
        Files.write(Path.of(args[0]),rows);System.out.println("DEFAULT_ENTRY rows="+rows.size());
    }
}
