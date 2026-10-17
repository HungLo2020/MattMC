import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;

/** Original public enchantment alias/view/callback behavior, unchanged on every side. */
public final class EnchantmentPayloadReference {
    static final List<String> rows=new ArrayList<>(),events=new ArrayList<>();
    static Constructor<ItemEnchantments> constructor;
    static Enchantment definition;
    static class Key implements Holder<Enchantment> {
        final String id;final int hash;Runnable hashEffect,equalsEffect,valueEffect;
        Key(String id){this(id,5);}Key(String id,int hash){this.id=id;this.hash=hash;}
        public int hashCode(){events.add("hash:"+id);if(hashEffect!=null)hashEffect.run();return hash;}
        public boolean equals(Object other){events.add("eq:"+id+":"+other);if(equalsEffect!=null)equalsEffect.run();return other instanceof Key key&&id.equals(key.id);}
        public String toString(){return id;}
        public Enchantment value(){events.add("value:"+id);if(valueEffect!=null)valueEffect.run();return definition;}
        public boolean isBound(){return true;}public boolean is(ResourceLocation key){return false;}public boolean is(ResourceKey<Enchantment> key){return false;}
        public boolean is(Predicate<ResourceKey<Enchantment>> predicate){return false;}public boolean is(TagKey<Enchantment> tag){events.add("tag:"+id);return false;}
        public boolean is(Holder<Enchantment> other){return equals(other);}public Stream<TagKey<Enchantment>> tags(){return Stream.empty();}
        public Either<ResourceKey<Enchantment>,Enchantment> unwrap(){return Either.right(definition);}public Optional<ResourceKey<Enchantment>> unwrapKey(){return Optional.empty();}
        public Holder.Kind kind(){return Holder.Kind.DIRECT;}public boolean canSerializeIn(HolderOwner<Enchantment> owner){return true;}
    }
    interface Call {Object run() throws Throwable;}
    static Throwable original(Throwable failure){while(failure instanceof InvocationTargetException wrapped)failure=wrapped.getCause();return failure;}
    static String call(Call body){try{return String.valueOf(body.run());}catch(Throwable failure){failure=original(failure);return failure.getClass().getName()+":"+failure.getMessage();}}
    static ItemEnchantments create(Object2IntOpenHashMap<Holder<Enchantment>> map) throws Exception {return constructor.newInstance(map);}
    static String state(ItemEnchantments value){var out=new ArrayList<String>();for(var entry:value.entrySet())out.add(entry.getKey()+"="+entry.getIntValue());return out+"/size="+value.size()+"/empty="+value.isEmpty();}
    static void row(String name,Object value){rows.add(name+" "+value+" events="+events);events.clear();}
    static void aliases() throws Exception {
        Key a=new Key("a"),b=new Key("b");var mutable=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);mutable.set(a,2);var first=mutable.toImmutable();var copy=new ItemEnchantments.Mutable(first);var copied=copy.toImmutable();var keys=first.keySet();var entries=first.entrySet();events.clear();
        mutable.set(a,3);mutable.set(b,4);row("shared-versus-copy",state(first)+"/copy="+state(copied)+"/keys="+keys+"/entrySize="+entries.size()+"/mutableKeyIdentity="+(mutable.keySet()==mutable.keySet())+"/outerKeyIdentity="+(first.keySet()==first.keySet()));
        mutable.set(a,999);mutable.set(b,0);row("set-clamp-remove",state(first)+"/a="+first.getLevel(a)+"/b="+first.getLevel(b));
        mutable.upgrade(a,2);mutable.upgrade(b,-1);mutable.upgrade(b,999);row("upgrade",state(first));
        row("outer-keys-remove",call(()->keys.remove(a)));row("outer-entries-clear",call(()->{entries.clear();return "done";}));
        var entry=first.entrySet().iterator().next();int old=entry.setValue(-9);row("live-entry-write",old+"/key="+entry.getKey()+"/value="+entry.getIntValue()+"/state="+state(first));
        row("revalidate-shared",call(mutable::toImmutable));var uncheckedCopy=new ItemEnchantments.Mutable(first);row("mutable-copy-does-not-validate",uncheckedCopy.getLevel(entry.getKey()));
        mutable.set(entry.getKey(),5);row("repair-shared",state(mutable.toImmutable()));
        var legacy=new Object2IntOpenHashMap<Holder<Enchantment>>();legacy.defaultReturnValue(17);legacy.put(a,0);var physical=create(legacy);events.clear();legacy.put(b,7);row("constructor-shares-physical-map",state(physical)+"/missing="+physical.getLevel(new Key("missing")));
        var copiedLegacy=new ItemEnchantments.Mutable(physical);row("copy-default-return",copiedLegacy.getLevel(new Key("missing")));
    }
    @SuppressWarnings("unchecked") static void validation() throws Exception {
        Key key=new Key("k");
        for(int level:new int[]{-1,0,1,255,256}){var map=new Object2IntOpenHashMap<Holder<Enchantment>>();map.put(key,level);events.clear();row("constructor-level-"+level,call(()->state(create(map))));}
        var field=ItemEnchantments.class.getDeclaredField("LEVEL_CODEC");field.setAccessible(true);var codec=(Codec<Integer>)field.get(null);
        for(int level:new int[]{-1,0,1,255,256})row("persistent-level-"+level,codec.parse(JsonOps.INSTANCE,new JsonPrimitive(level)).toString());
        var mutable=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);mutable.set(null,1);var view=mutable.toImmutable();row("null-key",state(view)+"/level="+view.getLevel(null));mutable.set(null,-1);row("null-remove",state(view));
    }
    static ItemEnchantments.Mutable pair(Key a,Key b){var m=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);m.set(a,1);m.set(b,2);events.clear();return m;}
    static void views(){
        for(String mode:List.of("iterator","forEach","spliterator","array")){
            var m=pair(new Key("a"),new Key("b"));var view=m.toImmutable();var held=new ArrayList<Object2IntMap.Entry<Holder<Enchantment>>>();
            if(mode.equals("iterator"))view.entrySet().iterator().forEachRemaining(held::add);
            else if(mode.equals("forEach"))view.entrySet().forEach(held::add);
            else if(mode.equals("spliterator"))view.entrySet().spliterator().forEachRemaining(held::add);
            else for(Object entry:view.entrySet().toArray())held.add((Object2IntMap.Entry<Holder<Enchantment>>)entry);
            var entry=held.getFirst();String write=call(()->entry.setValue(8));row("entry-"+mode,write+"/identity="+(held.get(0)==held.get(1))+"/held="+held+"/state="+state(view));
        }
        var a=new Key("a");var b=new Key("b");var mutable=pair(a,b);var view=mutable.toImmutable();var entry=view.entrySet().iterator().next();var iterator=mutable.keySet().iterator();iterator.next();iterator.remove();row("retained-entry-after-removal",call(()->entry.getKey()+"="+entry.getIntValue())+"/state="+state(view));
    }
    static void callbacks(){
        var a=new Key("a");var b=new Key("b");var m=pair(a,b);var query=new Key("a");boolean[] once={false};
        query.hashEffect=()->{if(!once[0]){once[0]=true;m.set(new Key("c"),3);}};row("hash-reentry",m.getLevel(query)+"/state="+state(m.toImmutable()));
        once[0]=false;query.hashEffect=null;query.equalsEffect=()->{if(!once[0]){once[0]=true;m.set(a,7);}};row("equals-reentry",m.getLevel(query)+"/state="+state(m.toImmutable()));
        var expected=new AssertionError("original holder hash");query.hashEffect=()->{throw expected;};Throwable[] caught={null};String error=call(()->{try{return m.getLevel(query);}catch(Throwable failure){caught[0]=failure;throw failure;}});row("hash-error",error+"/same="+(caught[0]==expected));
        query.hashEffect=null;query.equalsEffect=null;var growth=pair(new Key("a"),new Key("b"));var growing=new Key("a");growing.hashEffect=()->{for(int i=0;i<64;i++)growth.set(new Key("x"+i,31+i),1);};row("growth-during-hash",call(()->growth.getLevel(growing))+"/size="+growth.keySet().size());
        var removing=pair(new Key("a"),new Key("b"));int[] count={0};row("predicate-error-prefix",call(()->{removing.removeIf(key->{events.add("predicate:"+key);if(++count[0]==2)throw new AssertionError("original predicate");return true;});return "done";})+"/state="+state(removing.toImmutable()));
        var live=pair(new Key("a"),new Key("b"));count[0]=0;row("predicate-live",call(()->{live.removeIf(key->{events.add("predicate:"+key);if(++count[0]==1)live.set(new Key("c"),3);return false;});return "done";})+"/state="+state(live.toImmutable()));
    }
    static void tooltip(){
        var a=new Key("a");var b=new Key("b");var mutable=pair(a,b);var view=mutable.toImmutable();var lines=new ArrayList<String>();
        row("tooltip-reentry",call(()->{view.addToTooltip(Item.TooltipContext.EMPTY,line->{lines.add(line.getString());events.add("line:"+line.getString());if(lines.size()==1)mutable.set(a,3);},TooltipFlag.NORMAL,DataComponentMap.EMPTY);return lines;})+"/state="+state(view));
        var expected=new AssertionError("original tooltip");Throwable[] caught={null};row("tooltip-error",call(()->{try{view.addToTooltip(Item.TooltipContext.EMPTY,line->{throw expected;},TooltipFlag.NORMAL,DataComponentMap.EMPTY);return "done";}catch(Throwable failure){caught[0]=failure;throw failure;}})+"/same="+(caught[0]==expected));
    }
    static void iteratorContracts(){
        int wrapHash=0;
        while((it.unimi.dsi.fastutil.HashCommon.mix(wrapHash)&31)!=31)wrapHash++;
        var wrapping=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        wrapping.set(new Key("a",wrapHash),1);wrapping.set(new Key("b",wrapHash),2);wrapping.set(new Key("c",wrapHash),3);events.clear();
        var iterator=wrapping.keySet().iterator();var visited=new ArrayList<String>();int steps=0;
        String removed=call(()->{while(iterator.hasNext()){if(visited.size()>=8)throw new AssertionError("nonterminating original iterator");visited.add(iterator.next().toString());iterator.remove();}return visited;});
        row("wrapped-key-iterator-remove",removed+"/state="+state(wrapping.toImmutable()));
        row("iterator-remove-twice",call(()->{iterator.remove();return "done";}));
        var skipping=pair(new Key("a"),new Key("b"));var skip=(it.unimi.dsi.fastutil.objects.ObjectIterator<Holder<Enchantment>>)skipping.keySet().iterator();
        row("iterator-negative-skip",skip.skip(-1)+"/has="+skip.hasNext()+"/next="+call(skip::next));
        var failing=pair(new Key("a"),new Key("b"));var failed=failing.keySet().iterator();var seen=new ArrayList<String>();
        String failure=call(()->{failed.forEachRemaining(key->{seen.add(key.toString());throw new AssertionError("original iterator consumer");});return "done";});
        row("iterator-consumer-error",failure+"/seen="+seen+"/has="+failed.hasNext()+"/next="+call(failed::next)+"/later="+call(failed::next)+"/remaining="+failed.hasNext());
        var nested=pair(new Key("a"),new Key("b"));var nestedIterator=nested.keySet().iterator();var nestedSeen=new ArrayList<String>();
        row("iterator-same-cursor-reentry",call(()->{nestedIterator.forEachRemaining(key->{nestedSeen.add(key.toString());if(nestedIterator.hasNext())nestedSeen.add("nested:"+nestedIterator.next());});return nestedSeen;})+"/remaining="+nestedIterator.hasNext());
        var splittable=pair(new Key("a"),new Key("b"));var split=splittable.toImmutable().entrySet().spliterator();var splitSeen=new ArrayList<String>();
        row("spliterator-error-resume",call(()->split.tryAdvance(entry->{splitSeen.add(entry.getKey().toString());throw new AssertionError("original spliterator consumer");}))+"/resume="+call(()->{split.forEachRemaining(entry->splitSeen.add(entry.getKey().toString()));return splitSeen;}));
    }
    static void entryAndSplitContracts(){
        var a=new Key("a");var b=new Key("b");var m=pair(a,b);var view=m.toImmutable();var entry=view.entrySet().iterator().next();boolean[] once={false};
        b.hashEffect=()->{if(!once[0]){once[0]=true;m.set(b,7);}};
        row("entry-hash-live-value",entry.hashCode()+"/state="+state(view));b.hashEffect=null;
        once[0]=false;b.equalsEffect=()->{if(!once[0]){once[0]=true;m.set(a,9);}};
        var other=new AbstractMap.SimpleEntry<Holder<Enchantment>,Integer>(new Key("b"),7){
            @Override public Holder<Enchantment> getKey(){events.add("other:key");return super.getKey();}
            @Override public Integer getValue(){events.add("other:value");return super.getValue();}
        };
        row("entry-equals-effect-order",entry.equals(other)+"/state="+state(view));b.equalsEffect=null;
        row("entry-equals-null-value",call(()->entry.equals(new AbstractMap.SimpleEntry<>(entry.getKey(),null))));
        var live=pair(new Key("a"),new Key("b"));var split=live.toImmutable().entrySet().spliterator();var seen=new ArrayList<String>();
        String failed=call(()->{split.forEachRemaining(e->{seen.add(e.getKey().toString());throw new AssertionError("original split foreach");});return "done";});
        row("split-foreach-error-repeats-current",failed+"/next="+call(()->split.tryAdvance(e->seen.add(e.getKey().toString())))+"/seen="+seen+"/estimate="+split.estimateSize());
        var parts=pair(new Key("a"),new Key("b"));var parent=parts.toImmutable().entrySet().spliterator();String before=parent.estimateSize()+":"+parent.characteristics();var child=parent.trySplit();String after=parent.estimateSize()+":"+parent.characteristics()+"/child="+child.estimateSize()+":"+child.characteristics();var childKeys=new ArrayList<String>();var parentKeys=new ArrayList<String>();child.forEachRemaining(e->childKeys.add(e.getKey().toString()));parent.forEachRemaining(e->parentKeys.add(e.getKey().toString()));
        row("split-independent-ranges",before+"/after="+after+"/child="+childKeys+"/parent="+parentKeys+"/remaining="+child.estimateSize()+":"+parent.estimateSize());
        var negative=(it.unimi.dsi.fastutil.objects.ObjectSpliterator<?>)parts.toImmutable().entrySet().spliterator();row("split-negative-skip",call(()->negative.skip(-1)));
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        definition=new Enchantment(Component.literal("test"),Enchantment.definition(HolderSet.direct(),1,3,Enchantment.constantCost(1),Enchantment.constantCost(1),1),HolderSet.direct(),DataComponentMap.EMPTY);
        constructor=ItemEnchantments.class.getDeclaredConstructor(Object2IntOpenHashMap.class);constructor.setAccessible(true);
        aliases();validation();views();callbacks();tooltip();iteratorContracts();entryAndSplitContracts();Files.write(Path.of(args[0]),rows);System.out.println("ENCHANTMENT_PAYLOAD rows="+rows.size());
    }
}
