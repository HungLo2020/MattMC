package net.minecraft.world.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

public class ResultContainer implements Container, RecipeCraftingHolder, net.minecraft.world.level.block.entity.NativeStockContainer {
	/**
	 * Exact instances keep the single result cell in native rows (a container cell, as
	 * crafting grids do), so the result slot's broadcast digests it natively; every
	 * method below is unchanged, including reading cell 0 for any index.
	 */
	private final net.minecraft.world.item.NativeItemState.ContainerCell nativeContainer = this.getClass() == ResultContainer.class
		? net.minecraft.world.item.NativeItemState.ContainerCell.create(net.minecraft.world.item.NativeItemSlots.withSize(1)) : null;
	private final NonNullList<ItemStack> itemStacks = this.nativeContainer != null ? this.nativeContainer.facade() : NonNullList.withSize(1, ItemStack.EMPTY);
	@Nullable
	private RecipeHolder<?> recipeUsed;

	@Override
	public int getContainerSize() {
		return 1;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack itemStack : this.itemStacks) {
			if (!itemStack.isEmpty()) {
				return false;
			}
		}

		return true;
	}

	@Override
	public ItemStack getItem(int i) {
		return this.itemStacks.get(0);
	}

	@Override
	public ItemStack removeItem(int i, int j) {
		return ContainerHelper.takeItem(this.itemStacks, 0);
	}

	@Override
	public ItemStack removeItemNoUpdate(int i) {
		return ContainerHelper.takeItem(this.itemStacks, 0);
	}

	@Override
	public void setItem(int i, ItemStack itemStack) {
		this.itemStacks.set(0, itemStack);
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
		this.itemStacks.clear();
	}

	@Override
	public net.minecraft.world.item.NativeItemSlots nativeSlotsForTransfer() {
		return this.nativeContainer != null && this.itemStacks instanceof net.minecraft.world.item.NativeItemSlots rows ? rows : null;
	}

	@Override
	public net.minecraft.world.item.NativeItemState.ContainerCell nativeContainerForSlots() {
		return this.nativeContainer;
	}

	@Override
	public void setRecipeUsed(@Nullable RecipeHolder<?> recipeHolder) {
		this.recipeUsed = recipeHolder;
	}

	@Nullable
	@Override
	public RecipeHolder<?> getRecipeUsed() {
		return this.recipeUsed;
	}
}
