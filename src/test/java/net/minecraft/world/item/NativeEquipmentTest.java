package net.minecraft.world.item;

import java.lang.ref.WeakReference;
import java.util.Map;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeEquipmentTest {
    private static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    private static NativeItemState.EquipmentMap backing(EntityEquipment eq)throws Exception {var field=EntityEquipment.class.getDeclaredField("items");field.setAccessible(true);return assertInstanceOf(NativeItemState.EquipmentMap.class,field.get(eq));}
    @Test void actualNativeEquipmentKeepsNullPresenceEnumKeysAndSharedStackIdentity() throws Exception {
        bootstrap();var eq=new EntityEquipment();var map=backing(eq);var source=new ItemStack(Items.STICK,11);
        assertSame(ItemStack.EMPTY,eq.get(EquipmentSlot.HEAD));eq.set(EquipmentSlot.HEAD,null);assertTrue(map.containsKey(EquipmentSlot.HEAD));assertNull(eq.get(EquipmentSlot.HEAD));
        eq.set(EquipmentSlot.FEET,source);eq.set(EquipmentSlot.OFFHAND,source);eq.clear();assertEquals(3,map.size());assertSame(ItemStack.EMPTY,eq.get(EquipmentSlot.HEAD));assertEquals(11,source.getCount());
        eq.set(EquipmentSlot.HEAD,source);eq.setAll(eq);assertEquals(0,map.size());assertTrue(eq.isEmpty());
    }
    private record Cleared(EntityEquipment equipment,WeakReference<ItemStack> stack,long handle) {}
    private static Cleared cleared() {
        var eq=new EntityEquipment();var source=new ItemStack(Items.STICK,11);eq.set(EquipmentSlot.HEAD,source);eq.set(EquipmentSlot.OFFHAND,source);eq.clear();
        return new Cleared(eq,new WeakReference<>(source),NativeItemState.handleForTest(source.state));
    }
    @Test void nativeEquipmentClearRetiresFormerJavaFacadeBindingsAndNativeHandles() throws Exception {
        bootstrap();var held=cleared();long until=System.nanoTime()+5_000_000_000L;
        while((held.stack().get()!=null||NativeItemState.liveForTest(held.handle()))&&System.nanoTime()<until){System.gc();Thread.sleep(10);}
        assertNull(held.stack().get());assertFalse(NativeItemState.liveForTest(held.handle()));assertEquals(2,backing(held.equipment()).size());assertTrue(held.equipment().isEmpty());
    }
    @Test void equipmentFacadeRootFailureOccursBeforeNativeMembershipPublication() throws Exception {
        bootstrap();var eq=new EntityEquipment();var map=backing(eq);var field=map.getClass().getDeclaredField("bindings");field.setAccessible(true);
        @SuppressWarnings("unchecked") var original=(Map<Long,ItemStack>)field.get(map);var expected=new AssertionError("equipment root allocation failure");
        var failing=new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<ItemStack>(original){@Override public ItemStack put(long key,ItemStack value){if(!containsKey(key))throw expected;return super.put(key,value);}};
        field.set(map,failing);var source=new ItemStack(Items.STICK,11);
        assertSame(expected,assertThrows(AssertionError.class,()->eq.set(EquipmentSlot.HEAD,source)));assertFalse(map.containsKey(EquipmentSlot.HEAD));assertSame(ItemStack.EMPTY,eq.get(EquipmentSlot.HEAD));assertEquals(11,source.getCount());
        field.set(map,original);eq.set(EquipmentSlot.HEAD,source);assertSame(source,eq.get(EquipmentSlot.HEAD));
    }
}
