package net.minecraft.world.item;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.ref.WeakReference;
import java.lang.foreign.ValueLayout;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import org.junit.jupiter.api.Test;

class NativeInventoryTest {
    // Keep one mock maker in the JUnit process: Mockito 5's subclass maker can
    // also return inline abstract-mock handlers, making lookup order observable.
    private static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    private record DetachedSource(WeakReference<ItemStack> facade,long handle) {}
    private record CraftingCandidate(Inventory inventory,WeakReference<ItemStack> facade,long handle,int[] callbacks) {}
    private static CraftingCandidate craftingCandidateForGc() {
        var inventory=new Inventory(null,new EntityEquipment());
        var item=org.mockito.Mockito.mock(Item.class);
        org.mockito.Mockito.doReturn(Items.STICK.components()).when(item).components();
        org.mockito.Mockito.doReturn(item).when(item).asItem();
        var candidate=new ItemStack(item,11);inventory.setItem(0,candidate);
        var weak=new WeakReference<>(candidate);long handle=NativeItemState.handleForTest(candidate.state);int[] callbacks={0};
        org.mockito.Mockito.doAnswer(call->{
            callbacks[0]++;inventory.setItem(0,new ItemStack(Items.DIRT,7));System.gc();
            assertNotNull(weak.get());assertEquals(11,weak.get().getCount());assertTrue(NativeItemState.liveForTest(handle));
            assertEquals(1,inventory.getFreeSlot());
            return Items.STICK.builtInRegistryHolder();
        }).when(item).builtInRegistryHolder();
        return new CraftingCandidate(inventory,weak,handle,callbacks);
    }
    @Test void craftingScanRetainsCapturedActualFacadeThroughReplacementCallbackThenReleasesIt() throws Exception {
        bootstrap();var held=craftingCandidateForGc();
        assertEquals(0,held.inventory().findSlotMatchingCraftingIngredient(Items.STICK.builtInRegistryHolder(),ItemStack.EMPTY));
        assertEquals(1,held.callbacks()[0]);assertSame(Items.DIRT,held.inventory().getItem(0).getItem());
        long until=System.nanoTime()+5_000_000_000L;
        while((held.facade().get()!=null||NativeItemState.liveForTest(held.handle()))&&System.nanoTime()<until){System.gc();Thread.sleep(10);}
        assertNull(held.facade().get());assertFalse(NativeItemState.liveForTest(held.handle()));
        java.lang.ref.Reference.reachabilityFence(held.inventory());
    }
    private static DetachedSource sourceForLeaseGc(Inventory inventory){
        var source=new ItemStack(Items.STICK,11);inventory.setItem(9,source);return new DetachedSource(new WeakReference<>(source),NativeItemState.handleForTest(source.state));
    }
    @Test void actualMenuAllocationLeasesSurviveGcAndNestedDirtyEffectsThenRetireDetachedSource() throws Exception {
        bootstrap();var inventory=new Inventory(null,new EntityEquipment());
        var chest=new net.minecraft.world.level.block.entity.ChestBlockEntity(net.minecraft.core.BlockPos.ZERO,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        chest.setRemoved();var menu=net.minecraft.world.inventory.ChestMenu.threeRows(1,inventory,chest);chest.clearRemoved();
        var detached=sourceForLeaseGc(inventory);inventory.setItem(10,new ItemStack(Items.DIRT,7));chest.setItem(0,new ItemStack(Items.STICK,60));
        var level=org.mockito.Mockito.mock(net.minecraft.world.level.Level.class);
        int[] dirty={0};org.mockito.Mockito.doAnswer(call->{
            if(++dirty[0]==1){
                inventory.setItem(9,ItemStack.EMPTY);System.gc();assertNotNull(detached.facade().get());assertEquals(7,detached.facade().get().getCount());
                var nested=menu.quickMoveStack(null,28);assertEquals(7,nested.getCount());assertSame(Items.DIRT,nested.getItem());
            }
            return null;
        }).when(level).blockEntityChanged(net.minecraft.core.BlockPos.ZERO);chest.setLevel(level);
        var result=menu.quickMoveStack(null,27);assertEquals(11,result.getCount());assertSame(Items.STICK,result.getItem());assertSame(ItemStack.EMPTY,inventory.getItem(9));
        assertEquals(64,chest.getItem(0).getCount());assertSame(Items.DIRT,chest.getItem(1).getItem());assertEquals(7,chest.getItem(1).getCount());assertSame(Items.STICK,chest.getItem(2).getItem());assertEquals(7,chest.getItem(2).getCount());
        long until=System.nanoTime()+5_000_000_000L;while((detached.facade().get()!=null||NativeItemState.liveForTest(detached.handle()))&&System.nanoTime()<until){System.gc();Thread.sleep(10);}
        assertNull(detached.facade().get());assertFalse(NativeItemState.liveForTest(detached.handle()));java.lang.ref.Reference.reachabilityFence(menu);
    }
    @Test void actualLinkedEquipmentSearchUsesLivePublicRoutingAndPreservesForeignGetterDispatch() {
        bootstrap();var equipment=new EntityEquipment();var inv=new Inventory(null,equipment);var target=new ItemStack(Items.STICK,60);var source=new ItemStack(Items.STICK,11);
        equipment.set(EquipmentSlot.OFFHAND,target);assertEquals(40,inv.getSlotWithRemainingSpace(source));
        EquipmentSlot original=Inventory.EQUIPMENT_SLOT_MAPPING.get(40);EquipmentSlot defaultSlot=Inventory.EQUIPMENT_SLOT_MAPPING.defaultReturnValue();
        try{
            Inventory.EQUIPMENT_SLOT_MAPPING.put(40,EquipmentSlot.HEAD);assertEquals(-1,inv.getSlotWithRemainingSpace(source));equipment.set(EquipmentSlot.HEAD,target);assertEquals(40,inv.getSlotWithRemainingSpace(source));
            Inventory.EQUIPMENT_SLOT_MAPPING.remove(40);Inventory.EQUIPMENT_SLOT_MAPPING.defaultReturnValue(EquipmentSlot.HEAD);assertEquals(40,inv.getSlotWithRemainingSpace(source));
            equipment.set(EquipmentSlot.HEAD,null);assertThrows(NullPointerException.class,()->inv.getSlotWithRemainingSpace(source));equipment.set(EquipmentSlot.HEAD,ItemStack.EMPTY);assertEquals(-1,inv.getSlotWithRemainingSpace(null));
        }finally{Inventory.EQUIPMENT_SLOT_MAPPING.defaultReturnValue(defaultSlot);Inventory.EQUIPMENT_SLOT_MAPPING.put(40,original);}
        int[] calls={0};var custom=new EntityEquipment(){@Override public ItemStack get(EquipmentSlot slot){calls[0]++;assertEquals(EquipmentSlot.OFFHAND,slot);return target;}};
        assertNull(custom.nativeEquipmentForInventory());assertEquals(40,new Inventory(null,custom).getSlotWithRemainingSpace(source));assertEquals(1,calls[0]);
    }
    @Test void completeNativeMenuAndTransferRestoreNestedEffectsAndKeepCleanupThrowPrefix() throws Exception {
        bootstrap();for(boolean throwAfterNested:new boolean[]{false,true}){
            var inv=new Inventory(null,new EntityEquipment());
            var chest=new net.minecraft.world.level.block.entity.ChestBlockEntity(net.minecraft.core.BlockPos.ZERO,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            chest.setRemoved();var menu=net.minecraft.world.inventory.ChestMenu.threeRows(1,inv,chest);chest.clearRemoved();
            var source=new ItemStack(Items.STICK,11);var nestedSource=new ItemStack(Items.DIRT,7);inv.setItem(0,source);inv.setItem(1,nestedSource);chest.setItem(0,new ItemStack(Items.STICK,60));
            ItemStack[] inner={null};boolean[] entered={false};var expected=new AssertionError("original cleanup after nested move");
            menu.slots.set(54,new net.minecraft.world.inventory.Slot(inv,0,0,0){
                @Override public void setByPlayer(ItemStack next,ItemStack old){
                    if(!entered[0]){entered[0]=true;assertSame(ItemStack.EMPTY,next);assertSame(source,old);inner[0]=menu.quickMoveStack(null,55);if(throwAfterNested)throw expected;}
                    super.setByPlayer(next,old);
                }
            });
            if(throwAfterNested){assertSame(expected,assertThrows(AssertionError.class,()->menu.quickMoveStack(null,54)));assertSame(source,inv.getItem(0));}
            else{var returned=menu.quickMoveStack(null,54);assertEquals(11,returned.getCount());assertSame(Items.STICK,returned.getItem());assertSame(ItemStack.EMPTY,inv.getItem(0));}
            assertTrue(entered[0]);assertEquals(7,inner[0].getCount());assertSame(Items.DIRT,inner[0].getItem());assertSame(ItemStack.EMPTY,inv.getItem(1));
            assertEquals(0,source.getCount());assertEquals(64,chest.getItem(0).getCount());assertEquals(7,chest.getItem(1).getCount());assertEquals(7,chest.getItem(2).getCount());assertSame(Items.DIRT,chest.getItem(2).getItem());
            for(String name:new String[]{"MENU_ACTION_EFFECTS","TRANSFER_EFFECTS"}){var field=NativeItemState.class.getDeclaredField(name);field.setAccessible(true);var effects=(ThreadLocal<?>)field.get(null);assertNull(effects.get());effects.remove();}
            assertSame(ItemStack.EMPTY,menu.quickMoveStack(null,55));
        }
    }
    private static NativeItemState.Menu actualMenu(net.minecraft.world.inventory.AbstractContainerMenu menu) throws Exception {
        var field=net.minecraft.world.inventory.AbstractContainerMenu.class.getDeclaredField("nativeMenu");field.setAccessible(true);return (NativeItemState.Menu)field.get(menu);
    }
    @Test void actualMenuRosterVersionAndCarriedShareOneOwnerAndFailedBindingDoesNotPublish() throws Exception {
        bootstrap();var inv=new Inventory(null,new EntityEquipment());var menu=net.minecraft.world.inventory.ChestMenu.threeRows(1,inv);var owner=actualMenu(menu);
        assertNotNull(owner);assertEquals(63,owner.size());assertEquals(63,owner.version());assertSame(menu.slots.get(0),owner.get(0));
        var source=new ItemStack(Items.STICK,11);menu.setCarried(source);assertSame(source,owner.carried());source.setCount(-7);assertEquals(-7,owner.carried().state.count());
        menu.initializeContents(17,java.util.List.of(),null);assertNull(owner.carried());assertEquals(17,menu.getStateId());
        var field=NativeItemState.Menu.class.getDeclaredField("bindings");field.setAccessible(true);
        @SuppressWarnings("unchecked") var original=(java.util.Map<Long,net.minecraft.world.inventory.Slot>)field.get(owner);
        var fail=new java.util.concurrent.atomic.AtomicBoolean(false);var expected=new AssertionError("original menu binding failure");
        var injected=new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<net.minecraft.world.inventory.Slot>(){@Override public net.minecraft.world.inventory.Slot put(long key,net.minecraft.world.inventory.Slot slot){if(fail.getAndSet(false))throw expected;return super.put(key,slot);}};
        injected.putAll(original);field.set(owner,injected);var first=menu.slots.get(0);long version=owner.version();
        fail.set(true);assertSame(expected,assertThrows(AssertionError.class,()->menu.slots.set(0,new net.minecraft.world.inventory.Slot(inv,7,0,0))));
        assertSame(first,menu.slots.get(0));assertEquals(63,owner.size());assertEquals(version+1,owner.version());assertEquals(63,injected.size());
        assertThrows(NullPointerException.class,()->menu.slots.set(63,null));assertEquals(version+2,owner.version());
    }
    private record HeldMenu(NativeItemState.Menu menu,WeakReference<Object> slot,WeakReference<Object> inventory,long rowsHandle) {}
    private static HeldMenu menuWithAliasedEndpoint() {
        var inv=new Inventory(null,new EntityEquipment());var slot=new net.minecraft.world.inventory.Slot(inv,7,0,0);var menu=NativeItemState.Menu.create();
        menu.add(0,slot);menu.add(1,slot);menu.remove(0);
        return new HeldMenu(menu,new WeakReference<>(slot),new WeakReference<>(inv),((NativeItemSlots)inv.getNonEquipmentItems()).handleForTest());
    }
    @Test void nativeMenuRetiresOnlyLastEndpointAliasAndItsActualInventoryAllocation() throws Exception {
        bootstrap();var held=menuWithAliasedEndpoint();System.gc();assertNotNull(held.slot().get());assertNotNull(held.inventory().get());assertTrue(NativeItemState.liveForTest(held.rowsHandle()));
        held.menu().remove(0);long until=System.nanoTime()+5_000_000_000L;
        while((held.slot().get()!=null||held.inventory().get()!=null||NativeItemState.liveForTest(held.rowsHandle()))&&System.nanoTime()<until){System.gc();Thread.sleep(10);}
        assertNull(held.slot().get());assertNull(held.inventory().get());assertFalse(NativeItemState.liveForTest(held.rowsHandle()));assertEquals(0,held.menu().size());java.lang.ref.Reference.reachabilityFence(held);
    }
    private static HeldMeta afterMenuFacadeGc() throws Exception {
        var menu=NativeItemState.Menu.create();var lease=menu.headerLeaseForTest();long original=NativeItemState.handleForTest(menu),retained=lease.handle();menu.changed();
        var view=lease.view().asSlice(0,8);var weak=new WeakReference<Object>(menu);menu=null;lease=null;long until=System.nanoTime()+5_000_000_000L;
        while((weak.get()!=null||NativeItemState.liveForTest(original))&&System.nanoTime()<until){System.gc();Thread.sleep(10);}
        assertNull(weak.get());assertFalse(NativeItemState.liveForTest(original));assertTrue(NativeItemState.liveForTest(retained));assertTrue(view.isReadOnly());assertEquals(1L,view.get(ValueLayout.JAVA_LONG,0));
        return new HeldMeta(weak,original,retained,view);
    }
    @Test void derivedMenuHeaderViewPinsActualAllocationAndReleasesAfterItsFinalScope() throws Exception {
        bootstrap();long failures=NativeItemState.cleanupFailuresForTest();var held=afterMenuFacadeGc();long retained=held.retained();var weak=new WeakReference<>(held.view());held=null;
        long until=System.nanoTime()+5_000_000_000L;while((weak.get()!=null||NativeItemState.liveForTest(retained))&&System.nanoTime()<until){System.gc();Thread.sleep(10);}
        assertNull(weak.get());assertFalse(NativeItemState.liveForTest(retained));assertEquals(failures,NativeItemState.cleanupFailuresForTest());
    }
    @Test void stockSlotLinkUsesActualIndexAndLiveFacadeWhileSubclassDispatchRemainsVirtual() {
        bootstrap();var inv=new Inventory(null,new EntityEquipment());
        var slot=new net.minecraft.world.inventory.Slot(inv,7,19,23);slot.index=123;
        var link=NativeItemState.bindStockSlot(inv,7);assertNotNull(link);assertEquals(7,link.index());
        assertEquals(7,slot.getContainerSlot());assertEquals(123,slot.index);assertFalse(slot.hasItem());
        var first=new ItemStack(Items.STICK,11);inv.setItem(7,first);assertSame(first,slot.getItem());assertSame(first,link.stack());assertTrue(slot.hasItem());
        first.setCount(-1);assertFalse(slot.hasItem());first.setCount(3);
        var replacement=new ItemStack(Items.DIRT,9);inv.getNonEquipmentItems().set(7,replacement);assertSame(replacement,slot.getItem());
        int[] calls={0};var custom=new net.minecraft.world.inventory.Slot(inv,7,0,0){@Override public ItemStack getItem(){calls[0]++;return ItemStack.EMPTY;}};
        assertFalse(custom.hasItem());assertEquals(1,calls[0]);assertSame(replacement,slot.getItem());
        assertNull(NativeItemState.bindStockSlot(inv,36));assertNull(NativeItemState.bindStockSlot(null,0));
        assertNull(NativeItemState.bindStockSlot(new Inventory(null,new EntityEquipment()){},7));
        assertThrows(NullPointerException.class,()->new net.minecraft.world.inventory.Slot(null,7,0,0).getItem());
    }
    private static HeldMeta afterSlotFacadeGc() throws Exception {
        var inv=new Inventory(null,new EntityEquipment());var link=NativeItemState.bindStockSlot(inv,35);
        long original=NativeItemState.handleForTest(link);var lease=link.leaseForTest();long retained=lease.handle();
        var view=lease.view().asSlice(0,4);assertTrue(view.isReadOnly());assertEquals(35,view.get(ValueLayout.JAVA_INT,0));
        var weak=new WeakReference<Object>(link);link=null;lease=null;inv=null;long until=System.nanoTime()+5_000_000_000L;
        while((weak.get()!=null||NativeItemState.liveForTest(original))&&System.nanoTime()<until){System.gc();Thread.sleep(10);}
        assertNull(weak.get());assertFalse(NativeItemState.liveForTest(original));assertTrue(NativeItemState.liveForTest(retained));assertEquals(35,view.get(ValueLayout.JAVA_INT,0));
        return new HeldMeta(weak,original,retained,view);
    }
    @Test void derivedSlotIndexViewRetainsActualBindingAfterFacadeReleaseAndThenReleasesIt() throws Exception {
        bootstrap();long failures=NativeItemState.cleanupFailuresForTest();var held=afterSlotFacadeGc();long retained=held.retained();var weak=new WeakReference<>(held.view());held=null;
        long until=System.nanoTime()+5_000_000_000L;while((weak.get()!=null||NativeItemState.liveForTest(retained))&&System.nanoTime()<until){System.gc();Thread.sleep(10);}
        assertNull(weak.get());assertFalse(NativeItemState.liveForTest(retained));assertEquals(failures,NativeItemState.cleanupFailuresForTest());
    }
    @Test void stockChestSlotFollowsReplaceableNativeContainerAndForeignOriginalGetters() {
        bootstrap();var chest=new net.minecraft.world.level.block.entity.ChestBlockEntity(net.minecraft.core.BlockPos.ZERO,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        var cell=chest.nativeContainerForSlots();var old=cell.facade();var slot=new net.minecraft.world.inventory.Slot(chest,0,0,0);
        var source=new ItemStack(Items.STICK,11);old.set(0,source);assertSame(source,slot.getItem());assertTrue(slot.hasItem());
        var inv=new Inventory(null,new EntityEquipment());var target=new ItemStack(Items.DIRT,7);inv.setItem(0,target);
        cell.replace(inv.getNonEquipmentItems());assertSame(target,slot.getItem());assertSame(source,old.get(0));target.setCount(0);assertFalse(slot.hasItem());
        int[] calls={0};var foreign=new net.minecraft.core.NonNullList<ItemStack>(new java.util.ArrayList<>(java.util.List.of(source)),ItemStack.EMPTY){
            @Override public ItemStack get(int index){calls[0]++;return super.get(index);}
        };
        cell.replace(foreign);assertSame(source,slot.getItem());assertEquals(1,calls[0]);assertTrue(slot.hasItem());assertEquals(2,calls[0]);
        cell.replace(null);assertNull(cell.facade());assertThrows(NullPointerException.class,slot::getItem);
        cell.replace(old);assertSame(source,slot.getItem());
        chest.setLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,net.minecraft.resources.ResourceLocation.withDefaultNamespace("test_slot_pending")));
        assertNull(chest.nativeSlotsForTransfer());assertSame(source,slot.getItem());assertTrue(slot.hasItem());
    }
    private record HeldContainer(net.minecraft.world.level.block.entity.ChestBlockEntity chest,net.minecraft.world.inventory.Slot slot,
        WeakReference<Object> previous,WeakReference<Object> stack,long rowsHandle,long stackHandle) {}
    private static HeldContainer afterContainerReplacement() {
        var chest=new net.minecraft.world.level.block.entity.ChestBlockEntity(net.minecraft.core.BlockPos.ZERO,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        var old=(NativeItemSlots)chest.nativeContainerForSlots().facade();var stack=new ItemStack(Items.STICK,11);old.set(0,stack);
        var slot=new net.minecraft.world.inventory.Slot(chest,0,0,0);assertSame(stack,slot.getItem());
        var held=new HeldContainer(chest,slot,new WeakReference<>(old),new WeakReference<>(stack),old.handleForTest(),NativeItemState.handleForTest(stack.state));
        chest.nativeContainerForSlots().replace(NativeItemSlots.withSize(27));return held;
    }
    @Test void liveContainerAndSlotReleaseReplacedBackingAndItsFacadeWhileEscapedListsKeepTheirOwnOwner() throws Exception {
        bootstrap();var held=afterContainerReplacement();long until=System.nanoTime()+5_000_000_000L;
        while((held.previous().get()!=null||held.stack().get()!=null||NativeItemState.liveForTest(held.rowsHandle())||NativeItemState.liveForTest(held.stackHandle()))&&System.nanoTime()<until){System.gc();Thread.sleep(10);}
        assertNull(held.previous().get());assertNull(held.stack().get());assertFalse(NativeItemState.liveForTest(held.rowsHandle()));assertFalse(NativeItemState.liveForTest(held.stackHandle()));
        assertSame(ItemStack.EMPTY,held.slot().getItem());assertNotNull(held.chest().nativeContainerForSlots().facade());java.lang.ref.Reference.reachabilityFence(held);
    }
    @Test void actualMainProducerListAliasesSelectionCounterAndPopTimeShareNativeOwners() {
        bootstrap(); var inv=new Inventory(null,new EntityEquipment());var list=inv.getNonEquipmentItems();
        assertInstanceOf(NativeItemSlots.class,list);assertSame(list,inv.nativeMainSlotsForTransfer());assertSame(list,inv.getNonEquipmentItems());
        var stack=new ItemStack(Items.STICK,11);stack.setPopTime(Integer.MIN_VALUE);list.set(0,stack);list.subList(1,2).set(0,stack);
        assertSame(stack,inv.getItem(1));assertEquals(2,inv.getFreeSlot());inv.setSelectedSlot(8);
        assertThrows(IllegalArgumentException.class,()->inv.setSelectedSlot(9));assertEquals(8,inv.getSelectedSlot());
        var lease=((NativeItemSlots)list).inventoryLeaseForTest();lease.view().set(ValueLayout.JAVA_INT,4,Integer.MAX_VALUE);
        inv.setChanged();assertEquals(Integer.MIN_VALUE,inv.getTimesChanged());assertEquals(Integer.MIN_VALUE,lease.view().get(ValueLayout.JAVA_INT,4));
        assertEquals(Integer.MIN_VALUE,stack.getPopTime());ItemStack copy=stack.copy();assertEquals(Integer.MIN_VALUE,copy.getPopTime());
        stack.setPopTime(5);assertEquals(Integer.MIN_VALUE,copy.getPopTime());assertSame(stack,inv.removeItemNoUpdate(0));assertSame(stack,inv.getItem(1));
        java.lang.ref.Reference.reachabilityFence(lease);java.lang.ref.Reference.reachabilityFence(inv);
    }
    @Test void selectionCommitAndMainWritePrefixesSurviveBadPickAndEquipmentThrow() {
        bootstrap();Inventory[] owner={null};var failure=new AssertionError("original equipment error");
        var equipment=new EntityEquipment(){@Override public ItemStack set(EquipmentSlot slot,ItemStack value){assertSame(value,owner[0].getNonEquipmentItems().get(0));throw failure;}};
        Inventory inv=new Inventory(null,equipment);owner[0]=inv;inv.setSelectedSlot(3);inv.setItem(3,new ItemStack(Items.STICK,11));
        assertThrows(ArrayIndexOutOfBoundsException.class,()->inv.pickSlot(40));assertEquals(4,inv.getSelectedSlot());
        EquipmentSlot old=Inventory.EQUIPMENT_SLOT_MAPPING.get(0);Inventory.EQUIPMENT_SLOT_MAPPING.put(0,EquipmentSlot.OFFHAND);
        var value=new ItemStack(Items.STICK,7);
        try {assertSame(failure,assertThrows(AssertionError.class,()->inv.setItem(0,value)));assertSame(value,inv.getItem(0));}
        finally {if(old==null)Inventory.EQUIPMENT_SLOT_MAPPING.remove(0);else Inventory.EQUIPMENT_SLOT_MAPPING.put(0,old);}
    }
    @Test void inventorySubclassKeepsOriginalListAndPrivateSelectionReads() {
        bootstrap();var inv=new Inventory(null,new EntityEquipment()){@Override public int getSelectedSlot(){return 7;}};
        assertNull(inv.nativeMainSlotsForTransfer());assertFalse(inv.getNonEquipmentItems() instanceof NativeItemSlots);
        var item=new ItemStack(Items.STICK,11);inv.setItem(0,item);assertSame(item,inv.getSelectedItem());assertEquals(7,inv.getSelectedSlot());
    }
    private record HeldMeta(WeakReference<Object> owner,long original,long retained,java.lang.foreign.MemorySegment view) {}
    private static HeldMeta afterFacadeGc() throws Exception {
        Inventory inv=new Inventory(null,new EntityEquipment());NativeItemSlots list=(NativeItemSlots)inv.getNonEquipmentItems();
        long original=list.handleForTest();var lease=list.inventoryLeaseForTest();long retained=lease.handle();
        inv.setSelectedSlot(8);inv.setChanged();var view=lease.view().asSlice(4,4).asReadOnly();
        var weak=new WeakReference<Object>(list);inv=null;list=null;lease=null;long until=System.nanoTime()+5_000_000_000L;
        while((weak.get()!=null||NativeItemState.liveForTest(original))&&System.nanoTime()<until){System.gc();Thread.sleep(10);}
        assertNull(weak.get());assertFalse(NativeItemState.liveForTest(original));assertTrue(NativeItemState.liveForTest(retained));assertEquals(1,view.get(ValueLayout.JAVA_INT,0));
        return new HeldMeta(weak,original,retained,view);
    }
    @Test void derivedMetadataViewPinsActualInventoryAllocationAfterFacadeAndOriginalHandleRelease() throws Exception {
        bootstrap();long failures=NativeItemState.cleanupFailuresForTest();var held=afterFacadeGc();long retained=held.retained();var weak=new WeakReference<>(held.view());held=null;
        long until=System.nanoTime()+5_000_000_000L;while((weak.get()!=null||NativeItemState.liveForTest(retained))&&System.nanoTime()<until){System.gc();Thread.sleep(10);}
        assertNull(weak.get());assertFalse(NativeItemState.liveForTest(retained));assertEquals(failures,NativeItemState.cleanupFailuresForTest());
    }
    @Test void reverseStockChestConsumerMergesAllTargetsAndCountsEachNativeChange() {
        bootstrap();Inventory inv=new Inventory(null,new EntityEquipment());
        var chest=new net.minecraft.world.level.block.entity.ChestBlockEntity(net.minecraft.core.BlockPos.ZERO,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        chest.setRemoved();var menu=net.minecraft.world.inventory.ChestMenu.threeRows(1,inv,chest);chest.clearRemoved();
        for(int slot=0;slot<36;slot++)inv.setItem(slot,new ItemStack(Items.STICK,60));
        ItemStack source=new ItemStack(Items.STICK,64);source.setPopTime(7);chest.setItem(0,source);source.setCount(100);
        ItemStack returned=menu.quickMoveStack(null,0);assertEquals(100,returned.getCount());assertEquals(7,returned.getPopTime());assertNotSame(source,returned);
        assertSame(ItemStack.EMPTY,chest.getItem(0));assertEquals(0,source.getCount());assertEquals(25,inv.getTimesChanged());
        for(int slot=0;slot<36;slot++)assertEquals(slot<=8 || slot>=20?64:60,inv.getItem(slot).getCount());
    }
    @Test void reverseStockInsertionKeepsEquipmentErrorBeforeCountersAndSourceCleanup() {
        bootstrap();Inventory[] owner={null};var expected=new AssertionError("original reverse equipment error");
        var equipment=new EntityEquipment(){@Override public ItemStack set(EquipmentSlot slot,ItemStack value){assertSame(value,owner[0].getItem(8));assertEquals(0,owner[0].getTimesChanged());throw expected;}};
        Inventory inv=new Inventory(null,equipment);owner[0]=inv;
        var chest=new net.minecraft.world.level.block.entity.ChestBlockEntity(net.minecraft.core.BlockPos.ZERO,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        chest.setRemoved();var menu=net.minecraft.world.inventory.ChestMenu.threeRows(1,inv,chest);chest.clearRemoved();
        ItemStack source=new ItemStack(Items.STICK,11);source.setPopTime(5);chest.setItem(0,source);
        EquipmentSlot old=Inventory.EQUIPMENT_SLOT_MAPPING.get(8);Inventory.EQUIPMENT_SLOT_MAPPING.put(8,EquipmentSlot.OFFHAND);
        try {assertSame(expected,assertThrows(AssertionError.class,()->menu.quickMoveStack(null,0)));assertEquals(0,source.getCount());assertSame(source,chest.getItem(0));assertEquals(11,inv.getItem(8).getCount());assertEquals(5,inv.getItem(8).getPopTime());assertEquals(0,inv.getTimesChanged());}
        finally {if(old==null)Inventory.EQUIPMENT_SLOT_MAPPING.remove(8);else Inventory.EQUIPMENT_SLOT_MAPPING.put(8,old);}
    }

    @Test void reverseCustomMenuSizesUseOriginalDestinationEndBeforeNativeAdmission() {
        bootstrap();
        for(boolean extra:new boolean[]{false,true}) {
            Inventory inv=new Inventory(null,new EntityEquipment());
            var chest=new net.minecraft.world.level.block.entity.ChestBlockEntity(net.minecraft.core.BlockPos.ZERO,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            chest.setRemoved();var menu=net.minecraft.world.inventory.ChestMenu.threeRows(1,inv,chest);chest.clearRemoved();
            ItemStack source=new ItemStack(Items.STICK,11);chest.setItem(0,source);var other=new net.minecraft.world.SimpleContainer(1);
            if(extra){for(int slot=0;slot<36;slot++)inv.setItem(slot,new ItemStack(Items.DIRT,64));menu.slots.add(new net.minecraft.world.inventory.Slot(other,0,0,0));}
            else menu.slots.remove(62);
            assertEquals(11,menu.quickMoveStack(null,0).getCount());assertSame(ItemStack.EMPTY,chest.getItem(0));
            assertEquals(11,(extra?other.getItem(0):inv.getItem(7)).getCount());if(!extra)assertSame(ItemStack.EMPTY,inv.getItem(8));
        }
    }

    @Test void inventoryAddPreservesAliasAndLastIterationResult() {
        bootstrap();var player=org.mockito.Mockito.mock(net.minecraft.world.entity.player.Player.class);
        Inventory inv=new Inventory(player,new EntityEquipment());ItemStack source=new ItemStack(Items.STICK,11);
        inv.setItem(0,source);assertTrue(inv.add(0,source));assertSame(source,inv.getItem(0));assertEquals(0,source.getCount());assertEquals(5,source.getPopTime());
        for(int i=0;i<36;i++)inv.setItem(i,new ItemStack(Items.DIRT,64));
        inv.setItem(0,new ItemStack(Items.STICK,60));source=new ItemStack(Items.STICK,11);
        assertFalse(inv.add(source));assertEquals(7,source.getCount());assertEquals(64,inv.getItem(0).getCount());assertEquals(5,inv.getItem(0).getPopTime());
        org.mockito.Mockito.verify(player).hasInfiniteMaterials();
    }
    @Test void inventoryAddRestoresNestedCallbackAndReleasesOwnerBeforeEquipmentEffects() {
        bootstrap();Inventory[] owner={null};var events=new java.util.ArrayList<String>();
        var equipment=new EntityEquipment(){@Override public ItemStack get(EquipmentSlot slot){
            events.add("equipment");Inventory nested=new Inventory(null,new EntityEquipment());
            assertTrue(nested.add(2,new ItemStack(Items.STICK,3)));assertEquals(3,nested.getItem(2).getCount());
            owner[0].setSelectedSlot(8);owner[0].setItem(3,new ItemStack(Items.STICK,60));return ItemStack.EMPTY;
        }};
        Inventory inv=new Inventory(null,equipment);owner[0]=inv;var source=new ItemStack(Items.STICK,4);
        assertTrue(inv.add(source));assertEquals(0,source.getCount());assertEquals(64,inv.getItem(3).getCount());assertEquals(8,inv.getSelectedSlot());assertEquals(java.util.List.of("equipment"),events);
    }
    @Test void inventoryAddPreservesOriginalThrowableAndDamagedClearBeforeBadIndex() {
        bootstrap();var expected=new AssertionError("equipment read failure");
        var equipment=new EntityEquipment(){@Override public ItemStack get(EquipmentSlot slot){throw expected;}};
        Inventory inv=new Inventory(null,equipment);var source=new ItemStack(Items.STICK,11);
        var report=assertThrows(net.minecraft.ReportedException.class,()->inv.add(source));assertSame(expected,report.getCause());assertEquals(11,source.getCount());
        var damaged=new ItemStack(Items.DIAMOND_SWORD);damaged.setDamageValue(1);
        var invalid=assertThrows(net.minecraft.ReportedException.class,()->inv.add(40,damaged));assertInstanceOf(ArrayIndexOutOfBoundsException.class,invalid.getCause());assertEquals(0,damaged.getCount());
    }
    @Test void nativeScansKeepNullShortCircuitAndAddPickDisplacementPrefix() {
        bootstrap();Inventory inv=new Inventory(null,new EntityEquipment());
        assertEquals(-1,inv.findSlotMatchingItem(null));assertEquals(-1,inv.getSlotWithRemainingSpace(null));
        inv.setItem(0,new ItemStack(Items.STICK,11));
        assertThrows(NullPointerException.class,()->inv.findSlotMatchingItem(null));
        assertThrows(NullPointerException.class,()->inv.findSlotMatchingCraftingIngredient(Items.STICK.builtInRegistryHolder(),null));
        for(int i=0;i<9;i++)inv.setItem(i,new ItemStack(Items.STICK,11));
        ItemStack displaced=inv.getItem(0);assertThrows(NullPointerException.class,()->inv.addAndPickItem(null));
        assertEquals(0,inv.getSelectedSlot());assertSame(displaced,inv.getItem(0));assertSame(displaced,inv.getItem(9));
    }
    @Test void nativeHotbarScanRereadsSelectionAfterEnchantmentEffectAndPropagatesOriginalError() {
        bootstrap();Inventory inv=new Inventory(null,new EntityEquipment());
        for(int i=0;i<9;i++)inv.setItem(i,new ItemStack(Items.STICK,11));inv.setSelectedSlot(8);
        var enchantments=org.mockito.Mockito.mock(net.minecraft.world.item.enchantment.ItemEnchantments.class);
        org.mockito.Mockito.doAnswer(call->{assertEquals(9,inv.getFreeSlot());inv.setSelectedSlot(0);return false;}).when(enchantments).isEmpty();
        inv.getItem(8).set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,enchantments);
        assertEquals(1,inv.getSuitableHotbarSlot());assertEquals(0,inv.getSelectedSlot());
        var expected=new AssertionError("enchantment scan failure");org.mockito.Mockito.doThrow(expected).when(enchantments).isEmpty();inv.setSelectedSlot(8);
        assertSame(expected,assertThrows(AssertionError.class,inv::getSuitableHotbarSlot));
    }
    @Test void nativeVisitorResolvesOriginalFacadeBeforeReentryAndRestoresFrameAfterError() {
        bootstrap();Inventory inv=new Inventory(null,new EntityEquipment());var original=new ItemStack(Items.STICK,11);inv.setItem(0,original);inv.setItem(2,original);
        var seen=new java.util.ArrayList<ItemStack>();
        assertFalse(inv.contains(itemStack->{
            seen.add(itemStack);
            if(seen.size()==1){inv.setItem(0,new ItemStack(Items.DIRT,7));inv.setItem(2,ItemStack.EMPTY);assertFalse(inv.contains(stack->false));assertSame(original,itemStack);assertEquals(11,itemStack.getCount());}
            return false;
        }));
        assertEquals(inv.getContainerSize(),seen.size());assertSame(original,seen.get(0));assertSame(ItemStack.EMPTY,seen.get(2));
        var expected=new AssertionError("original visitor failure");assertSame(expected,assertThrows(AssertionError.class,()->inv.contains(stack->{inv.setItem(5,new ItemStack(Items.STICK,7));throw expected;})));
        assertTrue(inv.contains(stack->stack.getCount()==7));assertEquals(7,inv.getItem(5).getCount());
    }
    private record ClearedInventory(Inventory inventory,WeakReference<ItemStack> source,long handle) {}
    private static ClearedInventory clearedInventory() {
        Inventory inv=new Inventory(null,new EntityEquipment());var source=new ItemStack(Items.STICK,11);source.setPopTime(5);
        inv.setItem(0,source);inv.setItem(1,source);inv.setItem(40,new ItemStack(Items.DIRT,9));inv.setSelectedSlot(8);inv.setChanged();
        var list=inv.getNonEquipmentItems();var iterator=list.iterator();list.clear();
        assertSame(ItemStack.EMPTY,iterator.next());assertSame(list,inv.getNonEquipmentItems());assertEquals(11,source.getCount());assertEquals(5,source.getPopTime());
        return new ClearedInventory(inv,new WeakReference<>(source),NativeItemState.handleForTest(source.state));
    }
    @Test void nativeBulkClearKeepsLiveListAndEquipmentMetadataAndReleasesOldFacadeBindings() throws Exception {
        bootstrap();var held=clearedInventory();long until=System.nanoTime()+5_000_000_000L;
        while((held.source().get()!=null||NativeItemState.liveForTest(held.handle()))&&System.nanoTime()<until){System.gc();Thread.sleep(10);}
        assertNull(held.source().get());assertFalse(NativeItemState.liveForTest(held.handle()));
        var inv=held.inventory();assertEquals(8,inv.getSelectedSlot());assertEquals(1,inv.getTimesChanged());assertEquals(9,inv.getItem(40).getCount());
        for(int i=0;i<36;i++)assertSame(ItemStack.EMPTY,inv.getItem(i));inv.setItem(3,new ItemStack(Items.STICK,7));assertEquals(7,inv.getItem(3).getCount());
    }
    @Test void nativeIdentityRemovalStopsAtFirstFacadeAndRetainsOriginalEquipmentFallback() {
        bootstrap();var expected=new AssertionError("original equipment fallback");
        Inventory inv=new Inventory(null,new EntityEquipment(){@Override public ItemStack get(EquipmentSlot slot){throw expected;}});
        var source=new ItemStack(Items.STICK,11);inv.setItem(0,source);inv.setItem(1,source);inv.removeItem(source);
        assertSame(ItemStack.EMPTY,inv.getItem(0));assertSame(source,inv.getItem(1));inv.removeItem(ItemStack.EMPTY);
        assertSame(expected,assertThrows(AssertionError.class,()->inv.removeItem((ItemStack)null)));assertEquals(11,source.getCount());
    }

    private static long transferCounter(long index) {
        try {
            var counter=net.minecraft.util.NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_item_transfer_counter",
                java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_LONG,java.lang.foreign.ValueLayout.JAVA_LONG));
            return (long)counter.invokeExact(index);
        } catch (Throwable failure) { throw new AssertionError(failure); }
    }
    private static long preparedCopies() { return transferCounter(3L); }
    private static long retiredUnadopted() { return transferCounter(4L); }
    // The returned snapshot and an empty-destination insertion adopt copies the
    // native operation prepared, in both stock directions: one each, a
    // distinct published facade with the source's count/pop time/components,
    // and later source component changes do not reach it (copy-on-write).
    @Test void stockEmptyInsertionsPublishTheNativelyPreparedCopy() {
        bootstrap();
        for(boolean reverse:new boolean[]{false,true}) {
            Inventory inv=new Inventory(null,new EntityEquipment());
            var chest=new net.minecraft.world.level.block.entity.ChestBlockEntity(net.minecraft.core.BlockPos.ZERO,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            chest.setRemoved();var menu=net.minecraft.world.inventory.ChestMenu.threeRows(1,inv,chest);chest.clearRemoved();
            ItemStack source=new ItemStack(Items.STICK,11);source.setPopTime(7);
            source.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("a"));
            if(reverse)chest.setItem(0,source);else inv.setItem(9,source);
            long before=preparedCopies();
            ItemStack returned=menu.quickMoveStack(null,reverse?0:27);
            assertEquals(before+2,preparedCopies(),"the snapshot and the inserted copy are both prepared natively");
            assertSame(Items.STICK,returned.getItem());assertEquals(11,returned.getCount());assertEquals(7,returned.getPopTime());assertNotSame(source,returned);
            ItemStack inserted=reverse?inv.getItem(8):chest.getItem(0);
            assertNotSame(source,inserted);assertNotSame(returned,inserted);
            assertSame(Items.STICK,inserted.getItem());assertEquals(11,inserted.getCount());assertEquals(7,inserted.getPopTime());
            assertEquals("a",inserted.getHoverName().getString());assertEquals(0,source.getCount());
            source.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("b"));
            assertEquals("a",inserted.getHoverName().getString());
            assertSame(inserted,reverse?inv.getItem(8):chest.getItem(0));
        }
    }

    // Inventory add's empty-slot resource copy and damaged copyAndClear adopt
    // the copy the native add prepared: one each, published with the original
    // count/pop-time results, the damaged source cleared.
    @Test void inventoryAddAdoptsNativelyPreparedEmptyAndDamagedCopies() {
        bootstrap();
        Inventory inv=new Inventory(null,new EntityEquipment());
        ItemStack stick=new ItemStack(Items.STICK,11);stick.setPopTime(3);
        long before=preparedCopies();
        assertTrue(inv.add(stick));
        assertEquals(before+1,preparedCopies(),"one prepared copy for the empty-slot resource");
        ItemStack added=inv.getItem(0);assertNotSame(stick,added);assertSame(Items.STICK,added.getItem());
        assertEquals(11,added.getCount());assertEquals(5,added.getPopTime());assertEquals(0,stick.getCount());
        ItemStack sword=new ItemStack(Items.DIAMOND_SWORD);sword.setDamageValue(1);sword.setPopTime(9);
        before=preparedCopies();
        assertTrue(inv.add(sword));
        assertEquals(before+1,preparedCopies(),"one prepared copy for the damaged addition");
        ItemStack damaged=inv.getItem(1);assertNotSame(sword,damaged);assertEquals(1,damaged.getDamageValue());
        assertEquals(1,damaged.getCount());assertEquals(5,damaged.getPopTime());assertEquals(0,sword.getCount());
        assertSame(added,inv.getItem(0));assertSame(damaged,inv.getItem(1));
    }

    private record PublishedCopies(WeakReference<ItemStack> returned,WeakReference<ItemStack> inserted,long returnedHandle,long insertedHandle) {}
    private static PublishedCopies publishedCopies() {
        Inventory inv=new Inventory(null,new EntityEquipment());
        var chest=new net.minecraft.world.level.block.entity.ChestBlockEntity(net.minecraft.core.BlockPos.ZERO,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        chest.setRemoved();var menu=net.minecraft.world.inventory.ChestMenu.threeRows(1,inv,chest);chest.clearRemoved();
        inv.setItem(9,new ItemStack(Items.STICK,11));long before=preparedCopies();
        ItemStack returned=menu.quickMoveStack(null,27);ItemStack inserted=chest.getItem(0);
        assertEquals(before+2,preparedCopies());
        long returnedHandle=NativeItemState.handleForTest(returned.state),insertedHandle=NativeItemState.handleForTest(inserted.state);
        assertTrue(NativeItemState.liveForTest(returnedHandle));assertTrue(NativeItemState.liveForTest(insertedHandle));
        return new PublishedCopies(new WeakReference<>(returned),new WeakReference<>(inserted),returnedHandle,insertedHandle);
    }
    // Adopted prepared copies are ordinary facades: after their last Java use
    // the Cleaner retires the native owners; nothing keeps them alive.
    @Test void adoptedPreparedCopiesRetireAfterLastUse() throws Exception {
        bootstrap();var held=publishedCopies();long until=System.nanoTime()+5_000_000_000L;
        while((held.returned().get()!=null||held.inserted().get()!=null||NativeItemState.liveForTest(held.returnedHandle())||NativeItemState.liveForTest(held.insertedHandle()))&&System.nanoTime()<until){System.gc();Thread.sleep(10);}
        assertNull(held.returned().get());assertNull(held.inserted().get());
        assertFalse(NativeItemState.liveForTest(held.returnedHandle()));assertFalse(NativeItemState.liveForTest(held.insertedHandle()));
    }

    // When Java declines the prepared copy (an impure item keeps the original
    // Java copy), the native operation retires it immediately.
    @Test void declinedPreparedCopyIsRetiredNatively() throws Exception {
        bootstrap();
        var pure=Item.class.getDeclaredField("nativeTransferCopyPure");pure.setAccessible(true);
        try {
            pure.setBoolean(Items.STICK,false);
            Inventory inv=new Inventory(null,new EntityEquipment());ItemStack stick=new ItemStack(Items.STICK,11);stick.setPopTime(3);
            long prepared=preparedCopies(),retired=retiredUnadopted();
            assertTrue(inv.add(stick));
            assertEquals(prepared+1,preparedCopies(),"the native add still prepares the empty-slot copy");
            assertEquals(retired+1,retiredUnadopted(),"the copy Java declined is retired natively");
            ItemStack added=inv.getItem(0);assertNotSame(stick,added);assertEquals(11,added.getCount());assertEquals(5,added.getPopTime());assertEquals(0,stick.getCount());
        } finally { pure.setBoolean(Items.STICK,true); }
    }
}
