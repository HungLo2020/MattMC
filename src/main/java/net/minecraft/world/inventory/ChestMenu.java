package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class ChestMenu extends AbstractContainerMenu {
	private final Container container;
	private static final class LegacyRows {final int value;LegacyRows(int value){this.value=value;}}
	private final LegacyRows legacyRows;
	private final Inventory playerInventory;

	private ChestMenu(MenuType<?> menuType, int i, Inventory inventory, int j) {
		this(menuType, i, inventory, new SimpleContainer(9 * j), j);
	}

	public static ChestMenu oneRow(int i, Inventory inventory) {
		return new ChestMenu(MenuType.GENERIC_9x1, i, inventory, 1);
	}

	public static ChestMenu twoRows(int i, Inventory inventory) {
		return new ChestMenu(MenuType.GENERIC_9x2, i, inventory, 2);
	}

	public static ChestMenu threeRows(int i, Inventory inventory) {
		return new ChestMenu(MenuType.GENERIC_9x3, i, inventory, 3);
	}

	public static ChestMenu fourRows(int i, Inventory inventory) {
		return new ChestMenu(MenuType.GENERIC_9x4, i, inventory, 4);
	}

	public static ChestMenu fiveRows(int i, Inventory inventory) {
		return new ChestMenu(MenuType.GENERIC_9x5, i, inventory, 5);
	}

	public static ChestMenu sixRows(int i, Inventory inventory) {
		return new ChestMenu(MenuType.GENERIC_9x6, i, inventory, 6);
	}

	public static ChestMenu threeRows(int i, Inventory inventory, Container container) {
		return new ChestMenu(MenuType.GENERIC_9x3, i, inventory, container, 3);
	}

	public static ChestMenu sixRows(int i, Inventory inventory, Container container) {
		return new ChestMenu(MenuType.GENERIC_9x6, i, inventory, container, 6);
	}

	public ChestMenu(MenuType<?> menuType, int i, Inventory inventory, Container container, int j) {
		super(menuType, i);
		checkContainerSize(container, j * 9);
		this.container = container;
		this.legacyRows=this.nativeMenuForAction()==null?new LegacyRows(j):null;
		if(this.nativeMenuForAction()!=null)this.nativeMenuForAction().chestRows(j);
		this.playerInventory = inventory;
		container.startOpen(inventory.player);
		int k = 18;
		this.addChestGrid(container, 8, 18);
		int l = 18 + this.containerRows() * 18 + 13;
		this.addStandardInventorySlots(inventory, 8, l);
	}

	private void addChestGrid(Container container, int i, int j) {
		for (int k = 0; k < this.containerRows(); k++) {
			for (int l = 0; l < 9; l++) {
				this.addSlot(new Slot(container, l + k * 9, i + l * 18, j + k * 18));
			}
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return this.container.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int i) {
		if(this.nativeMenuForAction()!=null)return this.quickMoveOwned(player,i);
		Slot slot = this.slots.get(i);
		if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
		ItemStack source = slot.getItem();
		return this.isNativeTransferSource(slot, i) ? this.quickMoveNative(source, slot, i) : this.quickMoveLegacy(source, slot, i);
	}

	private int containerRows(){var owner=this.nativeMenuForAction();return owner==null?this.legacyRows.value:owner.chestRows();}
	@Override
	protected boolean emptyOwnedSource(int index){
		var clear=this.container.getClass()==net.minecraft.world.level.block.entity.ChestBlockEntity.class
			&&((net.minecraft.world.level.block.entity.ChestBlockEntity)this.container).nativeSlotsForTransfer()!=null
			?((net.minecraft.world.level.block.entity.ChestBlockEntity)this.container).nativeContainerForSlots():null;
		return this.nativeMenuForAction().emptySource(index,clear);
	}
	@Override
	protected net.minecraft.world.item.NativeItemState.TransferEffects prepareOwnedTransfer(Slot slot,ItemStack source,int index){
		if(!this.isNativeTransferSource(slot,index))return null;
		var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)this.container;
		return index<27?this.prepareStockInventoryTransfer(source,chest,this.playerInventory):this.prepareStockChestTransfer(source,chest);
	}
	private boolean isNativeTransferSource(Slot slot, int i) {
		if (this.getClass() != ChestMenu.class || this.containerRows() != 3 || slot.getClass() != Slot.class
			|| this.container.getClass() != net.minecraft.world.level.block.entity.ChestBlockEntity.class) return false;
		if (i >= 0 && i < 27) return slot.container == this.container && slot.getContainerSlot() == i
			&& this.playerInventory.nativeMainSlotsForTransfer() != null;
		return i >= 27 && i < 63 && slot.container.getClass() == Inventory.class
			&& slot.getContainerSlot() == (i < 54 ? i - 18 : i - 54);
	}

	private ItemStack quickMoveNative(ItemStack source, Slot slot, int index) {
		ItemStack snapshot = source.copyForNativeTransfer();
		var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity)this.container;
		boolean changed = index < 27 ? this.moveItemStackToStockInventory(source,chest,this.playerInventory)
			: this.moveItemStackToStockChest(source,chest);
		if (!changed) return ItemStack.EMPTY;
		finishSourceSlot(source, slot);
		return snapshot;
	}

	private ItemStack quickMoveLegacy(ItemStack itemStack2, Slot slot, int i) {
		ItemStack snapshot = itemStack2.copy();
		boolean changed = i < this.containerRows() * 9
			? this.moveItemStackTo(itemStack2, this.containerRows() * 9, this.slots.size(), true)
			: this.moveItemStackTo(itemStack2, 0, this.containerRows() * 9, false);
		if (!changed) return ItemStack.EMPTY;
		finishSourceSlot(itemStack2, slot);
		return snapshot;
	}

	private static void finishSourceSlot(ItemStack source, Slot slot) {
		if (source.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
		else slot.setChanged();
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.container.stopOpen(player);
	}

	public Container getContainer() {
		return this.container;
	}

	public int getRowCount() {
		return this.containerRows();
	}
}
