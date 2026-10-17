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
    /** One bounded native read of every row (List.copyOf, ArrayList copies); see Slots.toArray. */
    @Override public Object[] toArray() { return this.owner.toArray(); }
    /** {@link #set} whose caller discards the previous contents; unobserved native contents stay unmaterialized. */
    public void replace(int index, ItemStack value) { java.util.Objects.requireNonNull(value); this.owner.replace(index, value); }
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
    /** Records the recipe for contents a native transfer published without a facade (op 18). */
    public void recordPending(long key, ItemStack source, long retired, long adopted) { this.owner.recordPending(key, source, retired, adopted); }
    int pendingForTest() { return this.owner.pendingForTest(); }
    /** Native stock hopper eject plan into {@code target}; see NativeItemState.Slots#hopperPlan. */
    public long hopperPlan(NativeItemSlots target, boolean suck) { return this.owner.hopperPlan(target.owner, suck); }
    /** Plan with a double chest's second half on either side ({@code null} when absent). */
    public long hopperPlan(@org.jetbrains.annotations.Nullable NativeItemSlots second, NativeItemSlots target, @org.jetbrains.annotations.Nullable NativeItemSlots targetSecond, int flags) {
        return this.owner.hopperPlan(second == null ? null : second.owner, target.owner, targetSecond == null ? null : targetSecond.owner, flags);
    }
    public boolean hopperCommit(NativeItemSlots target, long plan) { return this.owner.hopperCommit(target.owner, plan); }
    /** Contents of row {@code index} for a read that retains nothing (saving): pending contents are copied, not exposed. */
    public ItemStack getForSave(int index) { ItemStack snapshot = this.owner.pendingSnapshot(index); return snapshot != null ? snapshot : this.get(index); }
    /** Bitmask of non-empty rows (row i is bit i), or -2 for more than 63 rows. */
    public long occupiedMask() { return this.owner.occupiedMask(); }
    /** True when every row is empty (Container.isEmpty), without reading facades. */
    public boolean allEmpty() { return (this.owner.occupancy() & 1) != 0; }
    /** Every row non-empty at its max stack size (1), not (0), or -2 when a patched stack needs Java's scan. */
    public int fullness() { long bits = this.owner.occupancy(); return (bits & 4) != 0 ? -2 : (bits & 2) != 0 ? 1 : 0; }
    /** Hopper item-entity collection into these rows; see NativeItemState.Slots#hopperTake. */
    public long hopperTake(ItemStack source) { return this.owner.hopperTake(source); }
    /** Comparator signal of these rows followed by {@code second}'s, or -2 when Java must compute it. */
    public int redstoneSignalWith(NativeItemSlots second) { return this.owner.redstoneSignalWith(second.owner); }
    /** Comparator signal of these rows, or -2 when Java must compute it. */
    public int redstoneSignal() { return this.owner.redstoneSignal(); }
    long handleForTest() { return NativeItemState.handleForTest(this.owner); }
}
