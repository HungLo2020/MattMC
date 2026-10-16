import com.mojang.serialization.Codec;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import static org.mockito.Mockito.*;

/** Original public component consumers and effect positions; same source on every side. */
public final class ComponentPayloadConsumerReference {
    static final List<String> rows=new ArrayList<>(),events=new ArrayList<>();
    static final DataComponentType<Object> KEY=DataComponentType.<Object>builder().persistent(Codec.STRING.xmap(s->(Object)s,Object::toString)).build();
    interface Call { Object run() throws Throwable; }
    static String call(Call action){try{return String.valueOf(action.run());}catch(Throwable failure){return failure.getClass().getName()+":"+failure.getMessage();}}
    static void row(String name,Object value){rows.add(name+" "+value+" events="+events);events.clear();}
    static Item mockItem(DataComponentMap map){
        Item item=mock(Item.class,withSettings().mockMaker("mock-maker-subclass"));
        doReturn(map).when(item).components();doReturn(item).when(item).asItem();
        doReturn(Items.STICK.builtInRegistryHolder()).when(item).builtInRegistryHolder();return item;
    }
    static Holder<Enchantment> enchantment(){return Holder.Reference.createStandAlone(new HolderOwner<Enchantment>(){},ResourceKey.create(Registries.ENCHANTMENT,ResourceLocation.withDefaultNamespace("payload_consumer_test")));}
    static Inventory filled(){var inv=new Inventory(null,new EntityEquipment());for(int i=0;i<9;i++)inv.setItem(i,new ItemStack(Items.STICK,11));return inv;}
    static void aliases() throws Exception {
        var key=enchantment();var mutable=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);mutable.set(key,1);var shared=mutable.toImmutable();var copied=new ItemEnchantments.Mutable(shared).toImmutable();
        var inv=filled();for(int i=0;i<9;i++)inv.getItem(i).set(DataComponents.ENCHANTMENTS,shared);events.clear();
        row("hotbar-all-shared-enchanted",inv.getSuitableHotbarSlot());
        mutable.set(key,0);row("hotbar-shared-live-clear",inv.getSuitableHotbarSlot()+"/empty="+shared.isEmpty()+"/copied="+copied.getLevel(key));
        inv.getItem(0).set(DataComponents.ENCHANTMENTS,copied);row("hotbar-copied-owner-independent",inv.getSuitableHotbarSlot());
        var plain=new it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<Holder<Enchantment>>();plain.put(key,0);
        var constructor=ItemEnchantments.class.getDeclaredConstructor(it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap.class);constructor.setAccessible(true);
        var zero=new ItemEnchantments.Mutable(constructor.newInstance(plain)).toImmutable();inv=filled();inv.getItem(0).set(DataComponents.ENCHANTMENTS,zero);
        row("zero-level-is-still-enchanted",inv.getSuitableHotbarSlot()+"/size="+zero.size()+"/level="+zero.getLevel(key));
    }
    static void primitiveCopies(){
        var source=new ItemStack(Items.STICK,11);Integer box=Integer.valueOf(1300);
        source.set(DataComponents.MAX_DAMAGE,2000);source.set(DataComponents.DAMAGE,box);var copy=source.copy();var patch=source.getComponentsPatch();
        source.set(DataComponents.DAMAGE,1400);events.clear();
        row("primitive-cow-copy",source.getDamageValue()+"/copy="+copy.getDamageValue()+"/sameBox="+(copy.get(DataComponents.DAMAGE)==box)+"/escaped="+patch.get(DataComponents.DAMAGE).orElseThrow());
        var inv=new Inventory(null,new EntityEquipment());row("damaged-add-copy-clear",call(()->inv.add(0,source))+"/source="+source.getCount()+"/target="+inv.getItem(0).getCount()+"/damage="+inv.getItem(0).getDamageValue());
    }
    static void crafting(String mode){
        var inv=new Inventory(null,new EntityEquipment());var item=mockItem(Items.STICK.components());var target=new ItemStack(item,3);inv.setItem(0,target);
        var expected=new AssertionError("original item holder failure");boolean[] nested={false};
        doAnswer(call->{
            events.add("holder");
            if(mode.equals("nested")&&!nested[0]){
                nested[0]=true;try{events.add("inner="+inv.findSlotMatchingCraftingIngredient(Items.STICK.builtInRegistryHolder(),ItemStack.EMPTY));}finally{nested[0]=false;}
            }
            if(!nested[0]){inv.setItem(0,new ItemStack(Items.DIRT,7));inv.setSelectedSlot(2);}
            if(mode.equals("throw"))throw expected;
            return Items.STICK.builtInRegistryHolder();
        }).when(item).builtInRegistryHolder();
        events.clear();Throwable[] caught={null};String result=call(()->{try{return inv.findSlotMatchingCraftingIngredient(Items.STICK.builtInRegistryHolder(),ItemStack.EMPTY);}catch(Throwable failure){caught[0]=failure;throw failure;}});
        row("craft-captured-"+mode,result+"/same="+(caught[0]==expected)+"/selected="+inv.getSelectedSlot()+"/replacement="+inv.getItem(0).is(Items.DIRT)+"/old="+target.getCount());
    }
    static void foreignEnchantment(String mode){
        var inv=new Inventory(null,new EntityEquipment());var item=mockItem(Items.STICK.components());var target=new ItemStack(item,3);inv.setItem(0,target);
        var value=mock(ItemEnchantments.class,withSettings().mockMaker("mock-maker-subclass"));var expected=new AssertionError("original foreign enchantment");
        doAnswer(call->{events.add("enchantment/free="+inv.getFreeSlot());inv.setItem(0,new ItemStack(Items.DIRT,7));if(mode.equals("throw"))throw expected;return mode.equals("empty");}).when(value).isEmpty();
        target.set(DataComponents.ENCHANTMENTS,value);events.clear();Throwable[] caught={null};
        String result=call(()->{try{return inv.findSlotMatchingCraftingIngredient(Items.STICK.builtInRegistryHolder(),ItemStack.EMPTY);}catch(Throwable failure){caught[0]=failure;throw failure;}});
        row("craft-foreign-enchantment-"+mode,result+"/same="+(caught[0]==expected)+"/replacement="+inv.getItem(0).is(Items.DIRT));
    }
    static final class Probe {
        final boolean nullEqual;Probe(boolean nullEqual){this.nullEqual=nullEqual;}
        public boolean equals(Object other){events.add("equal:"+other);return other==null?nullEqual:other instanceof Integer integer&&integer==7;}
        public int hashCode(){return 7;}public String toString(){return "probe";}
    }
    static void equality(String mode){
        var prototype=DataComponentMap.builder().addAll(Items.STICK.components()).set(KEY,42).build();var item=mockItem(prototype);
        var source=new ItemStack(item,3);var target=new ItemStack(item,60);var probe=new Probe(mode.contains("null"));
        if(mode.startsWith("opaque"))source.set(KEY,probe);else if(mode.startsWith("integer"))source.set(KEY,7);else source.remove(KEY);
        if(mode.endsWith("integer"))target.set(KEY,7);else if(mode.endsWith("opaque"))target.set(KEY,probe);else target.remove(KEY);
        var inv=new Inventory(null,new EntityEquipment());inv.setItem(0,target);events.clear();
        row("equality-direction-"+mode,call(()->inv.findSlotMatchingItem(source)));
    }
    @SuppressWarnings({"rawtypes","unchecked"}) static void scalar(String mode){
        var source=new ItemStack(Items.STICK,11);source.set(DataComponents.MAX_DAMAGE,mode.equals("negative")?-5:100);source.set(DataComponents.DAMAGE,7);
        if(mode.equals("bad-damage"))source.set((DataComponentType)DataComponents.DAMAGE,"wrong");
        if(mode.equals("bad-max"))source.set((DataComponentType)DataComponents.MAX_DAMAGE,"wrong");
        if(mode.equals("bad-max-unbreakable")){source.set((DataComponentType)DataComponents.MAX_DAMAGE,"wrong");source.set(DataComponents.UNBREAKABLE,net.minecraft.util.Unit.INSTANCE);}
        var inv=new Inventory(null,new EntityEquipment());events.clear();row("scalar-consumer-"+mode,call(()->inv.add(0,source))+"/source="+source.getCount()+"/target="+inv.getItem(0).getCount());
    }
    static void foreignPrototype(){
        var base=Items.STICK.components();var foreign=new DataComponentMap(){
            @Override public <T>T get(DataComponentType<? extends T> key){events.add(key==DataComponents.MAX_DAMAGE?"max":key==DataComponents.UNBREAKABLE?"unbreakable":key==DataComponents.DAMAGE?"damage":key==DataComponents.MAX_STACK_SIZE?"limit":"other");return base.get(key);}
            @Override public Set<DataComponentType<?>>keySet(){return base.keySet();}
        };
        var item=mockItem(foreign);var source=new ItemStack(item,3);var inv=new Inventory(null,new EntityEquipment());events.clear();
        row("foreign-prototype-effects",call(()->inv.add(0,source))+"/source="+source.getCount()+"/target="+inv.getItem(0).getCount());
    }
    public static void main(String[]args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        aliases();primitiveCopies();for(String mode:List.of("replace","nested","throw"))crafting(mode);
        for(String mode:List.of("empty","nonempty","throw"))foreignEnchantment(mode);
        for(String mode:List.of("opaque-integer","integer-opaque","opaque-null","removed-opaque"))equality(mode);
        for(String mode:List.of("negative","bad-damage","bad-max","bad-max-unbreakable"))scalar(mode);foreignPrototype();
        Files.write(Path.of(args[0]),rows);System.out.println("COMPONENT_PAYLOAD rows="+rows.size());
    }
}
