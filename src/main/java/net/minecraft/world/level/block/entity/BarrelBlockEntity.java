package net.minecraft.world.level.block.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class BarrelBlockEntity extends RandomizableContainerBlockEntity implements NativeStockContainer {
	private static final Component DEFAULT_NAME = Component.translatable("container.barrel");
	private static final class LegacyItems {NonNullList<ItemStack> items;LegacyItems(NonNullList<ItemStack> items){this.items=items;}}
	/** Like the stock chest: the exact class keeps its slots in a replaceable native container cell. */
	private final net.minecraft.world.item.NativeItemState.ContainerCell nativeContainer = this.getClass()==BarrelBlockEntity.class
		? net.minecraft.world.item.NativeItemState.ContainerCell.create(net.minecraft.world.item.NativeItemSlots.withSize(27)):null;
	private final LegacyItems legacyItems = this.nativeContainer==null?new LegacyItems(NonNullList.withSize(27,ItemStack.EMPTY)):null;
	private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
		@Override
		protected void onOpen(Level level, BlockPos blockPos, BlockState blockState) {
			BarrelBlockEntity.this.playSound(blockState, SoundEvents.BARREL_OPEN);
			BarrelBlockEntity.this.updateBlockState(blockState, true);
		}

		@Override
		protected void onClose(Level level, BlockPos blockPos, BlockState blockState) {
			BarrelBlockEntity.this.playSound(blockState, SoundEvents.BARREL_CLOSE);
			BarrelBlockEntity.this.updateBlockState(blockState, false);
		}

		@Override
		protected void openerCountChanged(Level level, BlockPos blockPos, BlockState blockState, int i, int j) {
		}

		@Override
		public boolean isOwnContainer(Player player) {
			if (player.containerMenu instanceof ChestMenu) {
				Container container = ((ChestMenu)player.containerMenu).getContainer();
				return container == BarrelBlockEntity.this;
			} else {
				return false;
			}
		}
	};

	public BarrelBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BlockEntityType.BARREL, blockPos, blockState);
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		if (!this.trySaveLootTable(valueOutput)) {
			ContainerHelper.saveAllItems(valueOutput, this.currentItems());
		}
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.replaceItems(this.createItems(this.getContainerSize()));
		if (!this.tryLoadLootTable(valueInput)) {
			ContainerHelper.loadAllItems(valueInput, this.currentItems());
		}
	}

	@Override
	public int getContainerSize() {
		return 27;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.currentItems();
	}

	@Override
	protected void setItems(NonNullList<ItemStack> nonNullList) {
		this.replaceItems(nonNullList);
	}

	private NonNullList<ItemStack> createItems(int size) {
		return this.getClass() == BarrelBlockEntity.class ? net.minecraft.world.item.NativeItemSlots.withSize(size) : NonNullList.withSize(size, ItemStack.EMPTY);
	}
	private NonNullList<ItemStack> currentItems(){return this.nativeContainer==null?this.legacyItems.items:this.nativeContainer.facade();}
	private void replaceItems(NonNullList<ItemStack> items){if(this.nativeContainer==null)this.legacyItems.items=items;else this.nativeContainer.replace(items);}

	/** Exact-stock admission, as for the chest; pending loot keeps its original Java effects. */
	@org.jetbrains.annotations.Nullable
	@Override
	public net.minecraft.world.item.NativeItemSlots nativeSlotsForTransfer() {
		return this.getClass() == BarrelBlockEntity.class && this.lootTable == null && this.currentItems() instanceof net.minecraft.world.item.NativeItemSlots nativeSlots
			? nativeSlots : null;
	}

	@Override
	public final net.minecraft.world.item.NativeItemState.ContainerCell nativeContainerForSlots() {return this.nativeContainer;}

	/** Exact loot-free stock barrel: emptiness read from the native rows. */
	@Override
	public boolean isEmpty() {
		var rows = this.nativeSlotsForTransfer();
		return rows != null ? rows.allEmpty() : super.isEmpty();
	}

	/** The inherited setItem chain; an exact native barrel discards the previous contents without observing them. */
	@Override
	public void setItem(int i, ItemStack itemStack) {
		if (this.getClass() != BarrelBlockEntity.class) {
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
	protected Component getDefaultName() {
		return DEFAULT_NAME;
	}

	@Override
	protected AbstractContainerMenu createMenu(int i, Inventory inventory) {
		return ChestMenu.threeRows(i, inventory, this);
	}

	@Override
	public void startOpen(ContainerUser containerUser) {
		if (!this.remove && !containerUser.getLivingEntity().isSpectator()) {
			this.openersCounter
				.incrementOpeners(containerUser.getLivingEntity(), this.getLevel(), this.getBlockPos(), this.getBlockState(), containerUser.getContainerInteractionRange());
		}
	}

	@Override
	public void stopOpen(ContainerUser containerUser) {
		if (!this.remove && !containerUser.getLivingEntity().isSpectator()) {
			this.openersCounter.decrementOpeners(containerUser.getLivingEntity(), this.getLevel(), this.getBlockPos(), this.getBlockState());
		}
	}

	@Override
	public List<ContainerUser> getEntitiesWithContainerOpen() {
		return this.openersCounter.getEntitiesWithContainerOpen(this.getLevel(), this.getBlockPos());
	}

	public void recheckOpen() {
		if (!this.remove) {
			this.openersCounter.recheckOpeners(this.getLevel(), this.getBlockPos(), this.getBlockState());
		}
	}

	void updateBlockState(BlockState blockState, boolean bl) {
		this.level.setBlock(this.getBlockPos(), blockState.setValue(BarrelBlock.OPEN, bl), 3);
	}

	void playSound(BlockState blockState, SoundEvent soundEvent) {
		Vec3i vec3i = ((Direction)blockState.getValue(BarrelBlock.FACING)).getUnitVec3i();
		double d = this.worldPosition.getX() + 0.5 + vec3i.getX() / 2.0;
		double e = this.worldPosition.getY() + 0.5 + vec3i.getY() / 2.0;
		double f = this.worldPosition.getZ() + 0.5 + vec3i.getZ() / 2.0;
		this.level.playSound(null, d, e, f, soundEvent, SoundSource.BLOCKS, 0.5F, this.level.random.nextFloat() * 0.1F + 0.9F);
	}
}
