package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class DispenserBlockEntity extends RandomizableContainerBlockEntity implements NativeRowsContainer {
	public static final int CONTAINER_SIZE = 9;
	private static final Component DEFAULT_NAME = Component.translatable("container.dispenser");
	private NonNullList<ItemStack> items = this.newItems(9);

	protected DispenserBlockEntity(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
		super(blockEntityType, blockPos, blockState);
	}

	public DispenserBlockEntity(BlockPos blockPos, BlockState blockState) {
		this(BlockEntityType.DISPENSER, blockPos, blockState);
	}

	@Override
	public int getContainerSize() {
		return 9;
	}

	public int getRandomSlot(RandomSource randomSource) {
		this.unpackLootTable(null);
		int i = -1;
		int j = 1;
		var rows = this.nativeSlotsForTransfer();
		long occupied = rows != null ? rows.occupiedMask() : -2;
		if (occupied != -2) {
			// The same loop and random draws over native emptiness, without reading the slots' facades.
			for (int k = 0; k < this.items.size(); k++) {
				if ((occupied & (1L << k)) != 0 && randomSource.nextInt(j++) == 0) {
					i = k;
				}
			}
			return i;
		}

		for (int k = 0; k < this.items.size(); k++) {
			if (!this.items.get(k).isEmpty() && randomSource.nextInt(j++) == 0) {
				i = k;
			}
		}

		return i;
	}

	public ItemStack insertItem(ItemStack itemStack) {
		int i = this.getMaxStackSize(itemStack);

		for (int j = 0; j < this.items.size(); j++) {
			ItemStack itemStack2 = this.items.get(j);
			if (itemStack2.isEmpty() || ItemStack.isSameItemSameComponents(itemStack, itemStack2)) {
				int k = Math.min(itemStack.getCount(), i - itemStack2.getCount());
				if (k > 0) {
					if (itemStack2.isEmpty()) {
						this.setItem(j, itemStack.split(k));
					} else {
						itemStack.shrink(k);
						itemStack2.grow(k);
					}
				}

				if (itemStack.isEmpty()) {
					break;
				}
			}
		}

		return itemStack;
	}

	@Override
	protected Component getDefaultName() {
		return DEFAULT_NAME;
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.items = this.newItems(this.getContainerSize());
		if (!this.tryLoadLootTable(valueInput)) {
			ContainerHelper.loadAllItems(valueInput, this.items);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		if (!this.trySaveLootTable(valueOutput)) {
			ContainerHelper.saveAllItems(valueOutput, this.items);
		}
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	/** Stock dispensers and droppers keep their slots in native rows (exact classes only). */
	private boolean exactStock() {
		return this.getClass() == DispenserBlockEntity.class || this.getClass() == DropperBlockEntity.class;
	}

	private NonNullList<ItemStack> newItems(int size) {
		return this.exactStock() ? net.minecraft.world.item.NativeItemSlots.withSize(size) : NonNullList.withSize(size, ItemStack.EMPTY);
	}

	@org.jetbrains.annotations.Nullable
	@Override
	public net.minecraft.world.item.NativeItemSlots nativeSlotsForTransfer() {
		return this.exactStock() && this.lootTable == null && this.items instanceof net.minecraft.world.item.NativeItemSlots rows ? rows : null;
	}

	/** Exact loot-free stock dispenser/dropper: emptiness read from the native rows. */
	@Override
	public boolean isEmpty() {
		var rows = this.nativeSlotsForTransfer();
		return rows != null ? rows.allEmpty() : super.isEmpty();
	}

	/** The inherited setItem chain; exact native rows discard the previous contents without observing them. */
	@Override
	public void setItem(int i, ItemStack itemStack) {
		if (!this.exactStock()) {
			super.setItem(i, itemStack);
			return;
		}
		this.unpackLootTable(null);
		if (this.getItems() instanceof net.minecraft.world.item.NativeItemSlots nativeItems) nativeItems.replace(i, itemStack);
		else this.getItems().set(i, itemStack);
		itemStack.limitSize(this.getMaxStackSize(itemStack));
		this.setChanged();
	}

	@Override
	protected void setItems(NonNullList<ItemStack> nonNullList) {
		this.items = nonNullList;
	}

	@Override
	protected AbstractContainerMenu createMenu(int i, Inventory inventory) {
		return new DispenserMenu(i, inventory, this);
	}
}
