package net.minecraft.world.inventory;

import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;

public class TransientCraftingContainer implements CraftingContainer, net.minecraft.world.level.block.entity.NativeStockContainer {
	/**
	 * Exact instances keep the grid in native rows (a container cell, as stock chests
	 * do), so menu slot links read and broadcast it natively; subclasses keep the list.
	 * The list is never replaced, and every original notification below is unchanged.
	 */
	private final net.minecraft.world.item.NativeItemState.ContainerCell nativeContainer;
	private final NonNullList<ItemStack> items;
	private final int width;
	private final int height;
	private final AbstractContainerMenu menu;

	public TransientCraftingContainer(AbstractContainerMenu abstractContainerMenu, int i, int j) {
		this(abstractContainerMenu, i, j, null);
	}

	private TransientCraftingContainer(AbstractContainerMenu abstractContainerMenu, int i, int j, NonNullList<ItemStack> nonNullList) {
		this.nativeContainer = nonNullList == null && this.getClass() == TransientCraftingContainer.class && i * j <= 27
			? net.minecraft.world.item.NativeItemState.ContainerCell.create(net.minecraft.world.item.NativeItemSlots.withSize(i * j)) : null;
		this.items = this.nativeContainer != null ? this.nativeContainer.facade() : nonNullList != null ? nonNullList : NonNullList.withSize(i * j, ItemStack.EMPTY);
		this.menu = abstractContainerMenu;
		this.width = i;
		this.height = j;
	}

	@Override
	public int getContainerSize() {
		return this.items.size();
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack itemStack : this.items) {
			if (!itemStack.isEmpty()) {
				return false;
			}
		}

		return true;
	}

	@Override
	public ItemStack getItem(int i) {
		return i >= this.getContainerSize() ? ItemStack.EMPTY : this.items.get(i);
	}

	@Override
	public ItemStack removeItemNoUpdate(int i) {
		return ContainerHelper.takeItem(this.items, i);
	}

	@Override
	public ItemStack removeItem(int i, int j) {
		ItemStack itemStack = ContainerHelper.removeItem(this.items, i, j);
		if (!itemStack.isEmpty()) {
			this.menu.slotsChanged(this);
		}

		return itemStack;
	}

	@Override
	public void setItem(int i, ItemStack itemStack) {
		this.items.set(i, itemStack);
		this.menu.slotsChanged(this);
	}

	@Override
	public void setChanged() {
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		this.items.clear();
	}

	@Override
	public int getHeight() {
		return this.height;
	}

	@Override
	public int getWidth() {
		return this.width;
	}

	@Override
	public List<ItemStack> getItems() {
		return List.copyOf(this.items);
	}

	@Override
	public net.minecraft.world.item.NativeItemSlots nativeSlotsForTransfer() {
		return this.nativeContainer != null && this.items instanceof net.minecraft.world.item.NativeItemSlots rows ? rows : null;
	}

	@Override
	public net.minecraft.world.item.NativeItemState.ContainerCell nativeContainerForSlots() {
		return this.nativeContainer;
	}

	@Override
	public void fillStackedContents(StackedItemContents stackedItemContents) {
		for (ItemStack itemStack : this.items) {
			stackedItemContents.accountSimpleStack(itemStack);
		}
	}
}
