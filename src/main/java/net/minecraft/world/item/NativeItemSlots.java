package net.minecraft.world.item;

import net.minecraft.core.NonNullList;

/** Exact stock-chest list facade. Custom/subclass lists retain their original owner. */
public final class NativeItemSlots extends NonNullList<ItemStack> {
    private final NativeItemState.Slots owner;
    private NativeItemSlots(int size) { this(NativeItemState.Slots.create(size, ItemStack.EMPTY)); }
    private NativeItemSlots(NativeItemState.Slots owner) { super(owner, ItemStack.EMPTY); this.owner = owner; }
    public static NonNullList<ItemStack> withSize(int size) { return new NativeItemSlots(size); }
    /** One actual owner for main36 membership, selection and change count. */
    public static NativeItemSlots inventory() { return new NativeItemSlots(NativeItemState.Slots.createInventory(ItemStack.EMPTY)); }
    NativeItemState.Slots actualOwner() { return this.owner; }
    NativeItemState.SlotLink bindMainSlot(int index) { return NativeItemState.SlotLink.create(this.owner,index); }
    @Override public void clear() { this.owner.clearAll(); }
    public void bindEquipment(net.minecraft.world.entity.EntityEquipment equipment){
        if(equipment!=null){var store=equipment.nativeEquipmentForInventory();if(store!=null)this.owner.bindEquipment(store);}
    }
    public boolean addInventory(ItemStack source,int index,NativeItemState.InventoryAddEffects effects) { return this.owner.addInventory(source,index,effects); }
    public int scanInventory(ItemStack source,int mode,NativeItemState.InventoryAddEffects effects) { return this.owner.scanInventory(source,mode,effects); }
    public boolean visitInventory(ItemStack source,int mode,int limit,NativeItemState.InventoryVisitEffects effects) { return this.owner.visitInventory(source,mode,limit,effects); }
    public int selected() { return this.owner.selected(); }
    public void selected(int value) { this.owner.selected(value); }
    public void markChanged() { this.owner.changed(); }
    public int changes() { return this.owner.changes(); }
    public int freeSlot() { return this.owner.freeSlot(); }
    public boolean mainEmpty() { return this.owner.mainEmpty(); }
    public void swapSelected(int index) { this.owner.swapSelected(index); }
    NativeItemState.ScalarLeaseForTest inventoryLeaseForTest() { return this.owner.inventoryLeaseForTest(); }
    public boolean runTransfer(ItemStack source, NativeItemState.TransferEffects effects) { return this.owner.runTransfer(source, effects); }
    /** One native scan/commit ending before the next Java effect. */
    public long transferStep(ItemStack source, int phase, int index) { return this.owner.transferStep(source, phase, index); }
    public void commitTransferInsert(ItemStack source, int index, ItemStack inserted, int amount) { this.owner.commitTransferInsert(source, index, inserted, amount); }
    long handleForTest() { return NativeItemState.handleForTest(this.owner); }
}
