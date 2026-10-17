package net.minecraft.world.level.lighting;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.function.LongConsumer;
import java.util.function.LongToIntFunction;
import java.util.function.LongFunction;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.world.level.chunk.DataLayer;

/** Focused observer of protected facade callback/iterator contracts. */
public final class VerifyCoordinatorCollections {
    static PrintWriter trace;
    static boolean messages;
    interface Action {void run();}
    static void outcome(String name,Action action){
        try{action.run();trace.println(name+" OK");}
        catch(Throwable failure){trace.println(name+" "+failure.getClass().getName()+(messages?" message="+failure.getMessage():""));}
    }
    static void sets(LongSet set){
        set.clear();var empty=set.iterator();
        outcome("empty-null",()->empty.forEachRemaining((LongConsumer)null));
        for(boolean zero:new boolean[]{true,false}){
            set.clear();set.add(zero?0:123);var iterator=set.iterator();
            Error marker=new AssertionError("callback marker");
            outcome("throw "+zero,()->iterator.forEachRemaining((LongConsumer)value->{trace.println("visit "+value);throw marker;}));
            trace.println("remaining "+iterator.hasNext());
            outcome("remove",iterator::remove);trace.println("size "+set.size());
            outcome("next",()->trace.println("next-value "+iterator.nextLong()));
        }
        set.clear();for(int i=0;i<8;i++)set.add(i);
        var iterator=set.iterator();boolean[] grew={false};
        outcome("grow-during-bulk",()->iterator.forEachRemaining((LongConsumer)value->{
            trace.println("grow-visit "+value);
            if(!grew[0]){grew[0]=true;for(int i=100;i<500;i++)set.add(i);}
        }));
        trace.println("grow-size "+set.size());
    }
    static Long2ByteMap bytes(){var map=new BlockLightSectionStorage(null).sectionStates;map.put(1,(byte)7);return map;}
    static void byteCallbacks(){
        for(String mode:new String[]{"absent","nullable","present","compute","merge"}){
            var map=bytes();
            outcome("byte-callback "+mode,()->{
                byte result;
                switch(mode){
                    case "absent" -> result=map.computeIfAbsent(13,(LongToIntFunction)key->{trace.println("byte callback absent");map.put(13,(byte)9);return 11;});
                    case "nullable" -> result=map.computeIfAbsentNullable(13,key->{trace.println("byte callback nullable");map.put(13,(byte)9);return (byte)11;});
                    case "present" -> result=map.computeIfPresent(1,(key,value)->{trace.println("byte callback present "+value);map.remove(1);map.put(3,(byte)9);return (byte)11;});
                    case "compute" -> result=map.compute(1,(key,value)->{trace.println("byte callback compute "+value);map.remove(1);map.put(3,(byte)9);return null;});
                    default -> result=map.merge(1,(byte)2,(left,right)->{trace.println("byte callback merge "+left+" "+right);map.remove(1);map.put(3,(byte)9);return (byte)11;});
                }
                trace.println("byte result "+result);
            });
            trace.println("byte after "+map.size()+" "+map.get(1)+" "+map.get(3)+" "+map.get(13)+" "+map.containsKey(1));
        }
        var map=bytes();var entry=map.long2ByteEntrySet().iterator().next();
        outcome("entry-equals-invalid",()->trace.println(entry.equals(new java.util.AbstractMap.SimpleEntry<>("bad",(byte)7))));
        var external=new java.util.AbstractMap.SimpleEntry<Long,Byte>(1L,(byte)9){
            @Override public Long getKey(){trace.println("external getKey");map.put(1,(byte)9);return 1L;}
            @Override public Byte getValue(){trace.println("external getValue");return (byte)9;}
        };
        outcome("entry-equals-reentry",()->trace.println("equals "+entry.equals(external)));
        outcome("entry-contains-reentry",()->trace.println("contains "+map.long2ByteEntrySet().contains(external)));
        outcome("entry-remove-reentry",()->trace.println("removed "+map.long2ByteEntrySet().remove(external)));
    }
    static void mapBulk(){
        for(String mode:new String[]{"entries","fast-entries","keys","values"}){
            var map=bytes();map.clear();
            outcome("empty-map-bulk "+mode,()->{
                switch(mode){
                    case "entries" -> map.long2ByteEntrySet().iterator().forEachRemaining(null);
                    case "fast-entries" -> ((Long2ByteMap.FastEntrySet)map.long2ByteEntrySet()).fastForEach(null);
                    case "keys" -> map.keySet().forEach((LongConsumer)null);
                    default -> map.values().forEach((it.unimi.dsi.fastutil.bytes.ByteConsumer)null);
                }
            });
            for(int i=0;i<8;i++)map.put(i,(byte)i);
            var held=new ArrayList<Long2ByteMap.Entry>();boolean[] changed={false};
            java.util.function.Consumer<Long2ByteMap.Entry> callback=e->{
                trace.println("map bulk "+mode+" "+e.getLongKey()+" "+e.getByteValue());held.add(e);
                if(!changed[0]){changed[0]=true;for(int i=100;i<160;i++)map.put(i,(byte)(i%16));}
            };
            outcome("map-bulk "+mode,()->{
                switch(mode){
                    case "entries" -> map.long2ByteEntrySet().iterator().forEachRemaining(callback);
                    case "fast-entries" -> ((Long2ByteMap.FastEntrySet)map.long2ByteEntrySet()).fastForEach(callback);
                    case "keys" -> map.keySet().iterator().forEachRemaining((LongConsumer)key->{trace.println("map key "+key);if(!changed[0]){changed[0]=true;for(int i=100;i<160;i++)map.put(i,(byte)i);}});
                    default -> map.values().iterator().forEachRemaining((it.unimi.dsi.fastutil.bytes.ByteConsumer)value->{trace.println("map value "+value);if(!changed[0]){changed[0]=true;for(int i=100;i<160;i++)map.put(i,(byte)i);}});
                }
            });
            if(held.size()>1)trace.println("map bulk shared "+mode+" "+(held.get(0)==held.get(1)));
        }
    }
    static String value(DataLayer layer){return layer==null?"null":Integer.toString(layer.get(0,0,0));}
    static void objects(){
        for(String mode:new String[]{"absent","present","compute","merge","merge-null"}){
            var map=new BlockLightSectionStorage(null).queuedSections;map.put(1,new DataLayer(7));
            outcome("object-callback "+mode,()->{
                DataLayer result;
                switch(mode){
                    case "absent" -> result=map.computeIfAbsent(13,(LongFunction<DataLayer>)key->{trace.println("object callback absent");map.put(13,new DataLayer(9));return new DataLayer(11);});
                    case "present" -> result=map.computeIfPresent(1,(key,old)->{trace.println("object callback present "+value(old));map.remove(1);map.put(3,new DataLayer(9));return new DataLayer(11);});
                    case "compute" -> result=map.compute(1,(key,old)->{trace.println("object callback compute "+value(old));map.remove(1);map.put(3,new DataLayer(9));return null;});
                    case "merge-null" -> result=map.merge(13,null,(left,right)->{trace.println("unexpected null merge callback");return left;});
                    default -> result=map.merge(1,new DataLayer(2),(left,right)->{trace.println("object callback merge "+value(left)+" "+value(right));map.remove(1);map.put(3,new DataLayer(9));return new DataLayer(11);});
                }
                trace.println("object result "+value(result));
            });
            trace.println("object after "+map.size()+" "+value(map.get(1))+" "+value(map.get(3))+" "+value(map.get(13))+" "+map.containsKey(1));
        }
        var map=new BlockLightSectionStorage(null).queuedSections;
        for(int i=1;i<9;i++)map.put(i,new DataLayer(i));
        boolean[] changed={false};
        outcome("object-values-reentry",()->map.values().forEach(layer->{
            trace.println("object bulk "+value(layer));
            if(!changed[0]){changed[0]=true;map.clear();for(int i=100;i<500;i++)map.put(i,new DataLayer(i%16));map.clear();System.gc();}
        }));
        trace.println("object bulk after "+map.size());
    }
    static void splitters(){
        var storage=new BlockLightSectionStorage(null);LongSet set=storage.changedSections;
        var empty=set.spliterator();outcome("split-empty-null",()->empty.forEachRemaining((LongConsumer)null));
        for(int i=0;i<8;i++)set.add(i);
        var split=set.spliterator();trace.println("split initial "+split.characteristics()+" "+split.estimateSize());
        outcome("split throw",()->split.tryAdvance((LongConsumer)key->{trace.println("split visit "+key);throw new AssertionError("marker");}));
        trace.println("split after-throw "+split.estimateSize());
        var left=split.trySplit();trace.println("split halves "+(left==null?"null":left.characteristics()+" "+left.estimateSize())+" right="+split.characteristics()+" "+split.estimateSize());
        if(left!=null)left.forEachRemaining((LongConsumer)key->trace.println("split left "+key));
        split.forEachRemaining((LongConsumer)key->trace.println("split right "+key));
        trace.println("views "+(storage.sectionStates.keySet()==storage.sectionStates.keySet())+" "+(storage.sectionStates.values()==storage.sectionStates.values())+" "+(storage.sectionStates.long2ByteEntrySet()==storage.sectionStates.long2ByteEntrySet()));
        for(String mode:new String[]{"entries","keys","values"}){
            var map=bytes();map.put(0,(byte)3);map.put(123,(byte)9);
            outcome("map split "+mode,()->{
                switch(mode){
                    case "entries" -> {var s=map.long2ByteEntrySet().spliterator();trace.println("entry split initial "+s.characteristics()+" "+s.estimateSize());outcome("entry split throw",()->s.forEachRemaining(entry->{trace.println("entry split visit "+entry.getLongKey());throw new AssertionError("marker");}));trace.println("entry split after "+s.estimateSize());}
                    case "keys" -> {var s=map.keySet().spliterator();trace.println("key split initial "+s.characteristics()+" "+s.estimateSize());outcome("key split throw",()->s.forEachRemaining((LongConsumer)key->{trace.println("key split visit "+key);throw new AssertionError("marker");}));trace.println("key split after "+s.estimateSize());}
                    default -> {var s=map.values().spliterator();trace.println("value split initial "+s.characteristics()+" "+s.estimateSize());outcome("value split throw",()->s.forEachRemaining((it.unimi.dsi.fastutil.bytes.ByteConsumer)value->{trace.println("value split visit "+value);throw new AssertionError("marker");}));trace.println("value split after "+s.estimateSize());}
                }
            });
        }
    }
    static void effects(){
        for(boolean present:new boolean[]{false,true}){
            var map=bytes();
            var function=new it.unimi.dsi.fastutil.longs.Long2ByteFunction(){
                public boolean containsKey(long key){trace.println("function contains "+present);if(present)map.put(key,(byte)9);else map.defaultReturnValue((byte)12);return present;}
                public byte get(long key){trace.println("function get");return 11;}
            };
            trace.println("function result "+map.computeIfAbsent(13,function)+" size="+map.size()+" value="+map.get(13));
        }
        for(String mode:new String[]{"remove","replace","remove-argument","replace-argument","contains-zero","contains-nonzero"}){
            var map=new BlockLightSectionStorage(null).queuedSections;boolean[] once={false};
            var receiver=new DataLayer(7){
                @Override public boolean equals(Object other){
                    trace.println("object equality "+mode);
                    if(!once[0]){
                        once[0]=true;
                        if(mode.startsWith("contains")){
                            map.clear();for(int i=100;i<160;i++)map.put(i,new DataLayer(i%16));map.clear();
                        }else{map.remove(1);map.put(3,new DataLayer(9));}
                    }
                    return !mode.startsWith("contains");
                }
            };
            map.put(mode.equals("contains-zero")?0:1,mode.endsWith("argument")?new DataLayer(7):receiver);
            outcome("object predicate "+mode,()->{
                boolean result=switch(mode){
                    case "remove" -> map.remove(1,new DataLayer(7));
                    case "replace" -> map.replace(1,new DataLayer(7),new DataLayer(11));
                    case "remove-argument" -> map.remove(1,receiver);
                    case "replace-argument" -> map.replace(1,receiver,new DataLayer(11));
                    default -> map.containsValue(new DataLayer(1));
                };
                trace.println("predicate result "+result);
            });
            trace.println("predicate after "+map.size()+" "+value(map.get(1))+" "+value(map.get(3)));
        }
        var map=new BlockLightSectionStorage(null).queuedSections;
        var function=new it.unimi.dsi.fastutil.longs.Long2ObjectFunction<DataLayer>(){
            public boolean containsKey(long key){trace.println("object function contains");map.put(key,new DataLayer(9));return true;}
            public DataLayer get(long key){trace.println("object function get");return null;}
        };
        trace.println("object function result "+value(map.computeIfAbsent(13,function))+" size="+map.size()+" contains="+map.containsKey(13));
    }
    public static void main(String[] args)throws Exception{
        try(var writer=new PrintWriter(Files.newBufferedWriter(Path.of(args[0])))){
            trace=writer;
            if(args.length>1&&args[1].equals("splitters")){splitters();return;}
            if(args.length>1&&args[1].equals("messages")){messages=true;sets(new BlockLightSectionStorage(null).changedSections);return;}
            if(args.length>1&&args[1].equals("effects")){effects();return;}
            var storage=new BlockLightSectionStorage(null);sets(storage.changedSections);
            var states=storage.sectionStates;states.clear();
            outcome("empty-entry-null",()->states.long2ByteEntrySet().forEach(null));
            states.put(0,(byte)7);states.put(123,(byte)9);states.put(456,(byte)11);
            var held=new ArrayList<Long2ByteMap.Entry>();
            ((Long2ByteMap.FastEntrySet)states.long2ByteEntrySet()).fastForEach(entry->{trace.println("entry "+entry.getLongKey()+" "+entry.getByteValue());held.add(entry);});
            trace.println("fast-shared "+(held.get(0)==held.get(1))+" "+(held.get(1)==held.get(2)));
            mapBulk();byteCallbacks();objects();
        }
        System.out.println("COLLECTION_CONTRACT captured");
    }
}
