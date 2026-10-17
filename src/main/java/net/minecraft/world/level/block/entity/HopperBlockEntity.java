package net.minecraft.world.level.block.entity;

import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class HopperBlockEntity extends RandomizableContainerBlockEntity implements Hopper, NativeRowsContainer {
	public static final int MOVE_ITEM_SPEED = 8;
	public static final int HOPPER_CONTAINER_SIZE = 5;
	private static final int[][] CACHED_SLOTS = new int[54][];
	private static final int NO_COOLDOWN_TIME = -1;
	private static final Component DEFAULT_NAME = Component.translatable("container.hopper");
	private NonNullList<ItemStack> items = this.newItems(5);
	private int cooldownTime = -1;
	private long tickedGameTime;
	private Direction facing;

	public HopperBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BlockEntityType.HOPPER, blockPos, blockState);
		this.facing = blockState.getValue(HopperBlock.FACING);
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.items = this.newItems(this.getContainerSize());
		if (!this.tryLoadLootTable(valueInput)) {
			ContainerHelper.loadAllItems(valueInput, this.items);
		}

		this.cooldownTime = valueInput.getIntOr("TransferCooldown", -1);
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		if (!this.trySaveLootTable(valueOutput)) {
			ContainerHelper.saveAllItems(valueOutput, this.items);
		}

		valueOutput.putInt("TransferCooldown", this.cooldownTime);
	}

	@Override
	public int getContainerSize() {
		return this.items.size();
	}

	@Override
	public ItemStack removeItem(int i, int j) {
		this.unpackLootTable(null);
		return ContainerHelper.removeItem(this.getItems(), i, j);
	}

	@Override
	public void setItem(int i, ItemStack itemStack) {
		this.unpackLootTable(null);
		this.getItems().set(i, itemStack);
		itemStack.limitSize(this.getMaxStackSize(itemStack));
	}

	@Override
	public void setBlockState(BlockState blockState) {
		super.setBlockState(blockState);
		this.facing = blockState.getValue(HopperBlock.FACING);
	}

	@Override
	protected Component getDefaultName() {
		return DEFAULT_NAME;
	}

	public static void pushItemsTick(Level level, BlockPos blockPos, BlockState blockState, HopperBlockEntity hopperBlockEntity) {
		hopperBlockEntity.cooldownTime--;
		hopperBlockEntity.tickedGameTime = level.getGameTime();
		if (!hopperBlockEntity.isOnCooldown()) {
			hopperBlockEntity.setCooldown(0);
			tryMoveItems(level, blockPos, blockState, hopperBlockEntity, () -> suckInItems(level, hopperBlockEntity));
		}
	}

	private static boolean tryMoveItems(
		Level level, BlockPos blockPos, BlockState blockState, HopperBlockEntity hopperBlockEntity, BooleanSupplier booleanSupplier
	) {
		if (level.isClientSide()) {
			return false;
		} else {
			if (!hopperBlockEntity.isOnCooldown() && (Boolean)blockState.getValue(HopperBlock.ENABLED)) {
				boolean bl = false;
				if (!hopperBlockEntity.isEmpty()) {
					bl = ejectItems(level, blockPos, hopperBlockEntity);
				}

				if (!hopperBlockEntity.inventoryFull()) {
					bl |= booleanSupplier.getAsBoolean();
				}

				if (bl) {
					hopperBlockEntity.setCooldown(8);
					setChanged(level, blockPos, blockState);
					return true;
				}
			}

			return false;
		}
	}

	private boolean inventoryFull() {
		var rows = this.nativeRows();
		if (rows != null) {
			int full = rows.fullness();
			if (full >= 0) return full == 1;
		}
		for (ItemStack itemStack : this.items) {
			if (itemStack.isEmpty() || itemStack.getCount() != itemStack.getMaxStackSize()) {
				return false;
			}
		}

		return true;
	}

	/** The stock hopper's own slots are native rows (exact class only); subclasses keep their list. */
	private NonNullList<ItemStack> newItems(int size) {
		return this.getClass() == HopperBlockEntity.class ? net.minecraft.world.item.NativeItemSlots.withSize(size) : NonNullList.withSize(size, ItemStack.EMPTY);
	}

	/** Exact loot-free stock hopper: emptiness read from the native rows (unpackLootTable has nothing to do). */
	@Override
	public boolean isEmpty() {
		var rows = this.nativeRows();
		return rows != null ? rows.allEmpty() : super.isEmpty();
	}

	/** Native rows of an exact, loot-free stock hopper (consumers such as comparators read them natively). */
	@Nullable
	@Override
	public net.minecraft.world.item.NativeItemSlots nativeSlotsForTransfer() {
		return this.nativeRows();
	}

	@Nullable
	private net.minecraft.world.item.NativeItemSlots nativeRows() {
		return this.getClass() == HopperBlockEntity.class && this.lootTable == null && this.items instanceof net.minecraft.world.item.NativeItemSlots rows ? rows : null;
	}

	/**
	 * ejectItems between exact stock native containers, computed natively: the
	 * same isFullContainer/slot/target order, a one-item split, and the
	 * original effects only for the actual move. -1 keeps the original path.
	 */
	/** Native rows of a container: one row set, or both halves of a stock double chest (concatenated, compound order). */
	private record NativeTarget(net.minecraft.world.item.NativeItemSlots first, @Nullable net.minecraft.world.item.NativeItemSlots second, Container firstHalf, @Nullable Container secondHalf) {
		/** The half holding concatenated slot {@code slot}, its rows and its local slot. */
		Container half(int slot) { return this.second != null && slot >= this.first.size() ? this.secondHalf : this.firstHalf; }
		net.minecraft.world.item.NativeItemSlots rows(int slot) { return this.second != null && slot >= this.first.size() ? this.second : this.first; }
		int local(int slot) { return this.second != null && slot >= this.first.size() ? slot - this.first.size() : slot; }
	}

	@Nullable
	private static NativeTarget nativeTarget(Container container) {
		var rows = nativeContainerRows(container);
		if (rows != null) return new NativeTarget(rows, null, container, null);
		if (container.getClass() == net.minecraft.world.CompoundContainer.class) {
			var compound = (net.minecraft.world.CompoundContainer)container;
			var first = NativeStockContainer.exact(compound.firstContainer()) ? ((NativeStockContainer)compound.firstContainer()).nativeSlotsForTransfer() : null;
			var second = NativeStockContainer.exact(compound.secondContainer()) ? ((NativeStockContainer)compound.secondContainer()).nativeSlotsForTransfer() : null;
			if (first != null && second != null) return new NativeTarget(first, second, compound.firstContainer(), compound.secondContainer());
		}
		return null;
	}

	/** Stock chests, barrels and shulker boxes mark themselves changed in setItem/removeItem; the hopper does not. */
	private static boolean marksOwnChanges(Container container) {
		return container.getClass() != HopperBlockEntity.class;
	}

	private static long withSlot(long plan, int slot) { return (plan & ~0xFF00L) | ((long)slot << 8); }
	private static long withIndex(long plan, int index) { return (plan & ~0xFFL) | index; }

	private static int ejectNative(HopperBlockEntity hopper, Container container) {
		var source = hopper.nativeRows();
		if (source == null) return -1;
		var target = nativeTarget(container);
		if (target == null) return -1;
		long plan = source.hopperPlan(null, target.first(), target.second(), container.getClass() == ShulkerBoxBlockEntity.class ? 2 : 0);
		if (plan == -2) return -1;
		if (plan == -1) return 0;
		int slot = (int)((plan >> 8) & 0xFF);
		if (!source.hopperCommit(target.rows(slot), withSlot(plan, target.local(slot)))) return -1;
		boolean placed = ((plan >> 16) & 1) != 0, wasEmpty = ((plan >> 17) & 1) != 0;
		// Original tryMoveInItem: setItem (whose chest/barrel implementation marks that half changed), cooldown, setChanged; then ejectItems' setChanged.
		if (placed && marksOwnChanges(target.half(slot))) target.half(slot).setChanged();
		if (wasEmpty && container instanceof HopperBlockEntity targetHopper && !targetHopper.isOnCustomCooldown()) {
			targetHopper.setCooldown(8 - (targetHopper.tickedGameTime >= hopper.tickedGameTime ? 1 : 0));
		}
		container.setChanged();
		container.setChanged();
		return 1;
	}

	/** Native rows of an exact stock chest or hopper, or null for the original path. */
	@Nullable
	private static net.minecraft.world.item.NativeItemSlots nativeContainerRows(Container container) {
		return container instanceof NativeRowsContainer rows ? rows.nativeSlotsForTransfer() : null;
	}

	/**
	 * suckInItems' slot loop for exact stock containers, natively, when the first
	 * non-empty source slot moves; original effects: the chest's own removeItem
	 * change mark, the cooldown rule, then setChanged on the hopper and source.
	 */
	private static int suckNative(Hopper hopper, Container container) {
		if (!(hopper instanceof HopperBlockEntity target)) return -1;
		var into = target.nativeRows(); var from = nativeTarget(container);
		if (into == null || from == null) return -1;
		long plan = from.first().hopperPlan(from.second(), into, null, 1);
		if (plan == -2) return -1;
		if (plan == -1) return 0;
		int index = (int)(plan & 0xFF);
		if (!from.rows(index).hopperCommit(into, withIndex(plan, from.local(index)))) return -1;
		boolean wasEmpty = ((plan >> 17) & 1) != 0;
		// The source half's own removeItem change mark (chest/barrel), then the original effects.
		if (marksOwnChanges(from.half(index))) from.half(index).setChanged();
		if (wasEmpty && !target.isOnCustomCooldown()) {
			target.setCooldown(8 - (container instanceof HopperBlockEntity sourceHopper && target.tickedGameTime >= sourceHopper.tickedGameTime ? 1 : 0));
		}
		target.setChanged();
		container.setChanged();
		return 1;
	}

	private static boolean ejectItems(Level level, BlockPos blockPos, HopperBlockEntity hopperBlockEntity) {
		Container container = getAttachedContainer(level, blockPos, hopperBlockEntity);
		if (container == null) {
			return false;
		} else {
			int moved = ejectNative(hopperBlockEntity, container);
			if (moved >= 0) return moved == 1;
			Direction direction = hopperBlockEntity.facing.getOpposite();
			if (isFullContainer(container, direction)) {
				return false;
			} else {
				for (int i = 0; i < hopperBlockEntity.getContainerSize(); i++) {
					ItemStack itemStack = hopperBlockEntity.getItem(i);
					if (!itemStack.isEmpty()) {
						int j = itemStack.getCount();
						ItemStack itemStack2 = addItem(hopperBlockEntity, container, hopperBlockEntity.removeItem(i, 1), direction);
						if (itemStack2.isEmpty()) {
							container.setChanged();
							return true;
						}

						itemStack.setCount(j);
						if (j == 1) {
							hopperBlockEntity.setItem(i, itemStack);
						}
					}
				}

				return false;
			}
		}
	}

	private static int[] getSlots(Container container, Direction direction) {
		if (container instanceof WorldlyContainer worldlyContainer) {
			return worldlyContainer.getSlotsForFace(direction);
		} else {
			int i = container.getContainerSize();
			if (i < CACHED_SLOTS.length) {
				int[] is = CACHED_SLOTS[i];
				if (is != null) {
					return is;
				} else {
					int[] js = createFlatSlots(i);
					CACHED_SLOTS[i] = js;
					return js;
				}
			} else {
				return createFlatSlots(i);
			}
		}
	}

	private static int[] createFlatSlots(int i) {
		int[] is = new int[i];
		int j = 0;

		while (j < is.length) {
			is[j] = j++;
		}

		return is;
	}

	private static boolean isFullContainer(Container container, Direction direction) {
		int[] is = getSlots(container, direction);

		for (int i : is) {
			ItemStack itemStack = container.getItem(i);
			if (itemStack.getCount() < itemStack.getMaxStackSize()) {
				return false;
			}
		}

		return true;
	}

	public static boolean suckInItems(Level level, Hopper hopper) {
		BlockPos blockPos = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY() + 1.0, hopper.getLevelZ());
		BlockState blockState = level.getBlockState(blockPos);
		Container container = getSourceContainer(level, hopper, blockPos, blockState);
		if (container != null) {
			int moved = suckNative(hopper, container);
			if (moved >= 0) return moved == 1;
			Direction direction = Direction.DOWN;

			for (int i : getSlots(container, direction)) {
				if (tryTakeInItemFromSlot(hopper, container, i, direction)) {
					return true;
				}
			}

			return false;
		} else {
			boolean bl = hopper.isGridAligned() && blockState.isCollisionShapeFullBlock(level, blockPos) && !blockState.is(BlockTags.DOES_NOT_BLOCK_HOPPERS);
			if (!bl) {
				for (ItemEntity itemEntity : getItemsAtAndAbove(level, hopper)) {
					if (addItem(hopper, itemEntity)) {
						return true;
					}
				}
			}

			return false;
		}
	}

	private static boolean tryTakeInItemFromSlot(Hopper hopper, Container container, int i, Direction direction) {
		ItemStack itemStack = container.getItem(i);
		if (!itemStack.isEmpty() && canTakeItemFromContainer(hopper, container, itemStack, i, direction)) {
			int j = itemStack.getCount();
			ItemStack itemStack2 = addItem(container, hopper, container.removeItem(i, 1), null);
			if (itemStack2.isEmpty()) {
				container.setChanged();
				return true;
			}

			itemStack.setCount(j);
			if (j == 1) {
				container.setItem(i, itemStack);
			}
		}

		return false;
	}

	public static boolean addItem(Container container, ItemEntity itemEntity) {
		if (container.getClass() == HopperBlockEntity.class) {
			HopperBlockEntity hopper = (HopperBlockEntity)container;
			var rows = hopper.nativeRows();
			ItemStack source = itemEntity.getItem();
			if (rows != null && source.getItem() != null && source.getItem().nativeTransferCopyIsPure()) {
				long result = rows.hopperTake(source);
				if (result >= 0) {
					// Original per-move effects (tryMoveInItem): cooldown on the first move into an empty hopper, setChanged per move.
					int remaining = (int)(result & 0xFFFFFFFFL), moves = (int)((result >> 32) & 0xFF);
					if (moves > 0 && ((result >> 40) & 1) != 0 && !hopper.isOnCustomCooldown()) hopper.setCooldown(8);
					for (int move = 0; move < moves; move++) hopper.setChanged();
					if (remaining == 0) {
						itemEntity.setItem(ItemStack.EMPTY);
						itemEntity.discard();
						return true;
					}
					itemEntity.setItem(source.copyWithCount(remaining));
					return false;
				}
			}
		}
		boolean bl = false;
		ItemStack itemStack = itemEntity.getItem().copy();
		ItemStack itemStack2 = addItem(null, container, itemStack, null);
		if (itemStack2.isEmpty()) {
			bl = true;
			itemEntity.setItem(ItemStack.EMPTY);
			itemEntity.discard();
		} else {
			itemEntity.setItem(itemStack2);
		}

		return bl;
	}

	public static ItemStack addItem(@Nullable Container container, Container container2, ItemStack itemStack, @Nullable Direction direction) {
		if (container2 instanceof WorldlyContainer worldlyContainer && direction != null) {
			int[] is = worldlyContainer.getSlotsForFace(direction);

			for (int i = 0; i < is.length && !itemStack.isEmpty(); i++) {
				itemStack = tryMoveInItem(container, container2, itemStack, is[i], direction);
			}
		} else {
			int j = container2.getContainerSize();

			for (int i = 0; i < j && !itemStack.isEmpty(); i++) {
				itemStack = tryMoveInItem(container, container2, itemStack, i, direction);
			}
		}

		return itemStack;
	}

	private static boolean canPlaceItemInContainer(Container container, ItemStack itemStack, int i, @Nullable Direction direction) {
		return !container.canPlaceItem(i, itemStack)
			? false
			: !(container instanceof WorldlyContainer worldlyContainer && !worldlyContainer.canPlaceItemThroughFace(i, itemStack, direction));
	}

	private static boolean canTakeItemFromContainer(Container container, Container container2, ItemStack itemStack, int i, Direction direction) {
		return !container2.canTakeItem(container, i, itemStack)
			? false
			: !(container2 instanceof WorldlyContainer worldlyContainer && !worldlyContainer.canTakeItemThroughFace(i, itemStack, direction));
	}

	private static ItemStack tryMoveInItem(@Nullable Container container, Container container2, ItemStack itemStack, int i, @Nullable Direction direction) {
		ItemStack itemStack2 = container2.getItem(i);
		if (canPlaceItemInContainer(container2, itemStack, i, direction)) {
			boolean bl = false;
			boolean bl2 = container2.isEmpty();
			if (itemStack2.isEmpty()) {
				container2.setItem(i, itemStack);
				itemStack = ItemStack.EMPTY;
				bl = true;
			} else if (canMergeItems(itemStack2, itemStack)) {
				int j = itemStack.getMaxStackSize() - itemStack2.getCount();
				int k = Math.min(itemStack.getCount(), j);
				itemStack.shrink(k);
				itemStack2.grow(k);
				bl = k > 0;
			}

			if (bl) {
				if (bl2 && container2 instanceof HopperBlockEntity hopperBlockEntity && !hopperBlockEntity.isOnCustomCooldown()) {
					int k = 0;
					if (container instanceof HopperBlockEntity hopperBlockEntity2 && hopperBlockEntity.tickedGameTime >= hopperBlockEntity2.tickedGameTime) {
						k = 1;
					}

					hopperBlockEntity.setCooldown(8 - k);
				}

				container2.setChanged();
			}
		}

		return itemStack;
	}

	@Nullable
	private static Container getAttachedContainer(Level level, BlockPos blockPos, HopperBlockEntity hopperBlockEntity) {
		return getContainerAt(level, blockPos.relative(hopperBlockEntity.facing));
	}

	@Nullable
	private static Container getSourceContainer(Level level, Hopper hopper, BlockPos blockPos, BlockState blockState) {
		return getContainerAt(level, blockPos, blockState, hopper.getLevelX(), hopper.getLevelY() + 1.0, hopper.getLevelZ());
	}

	public static List<ItemEntity> getItemsAtAndAbove(Level level, Hopper hopper) {
		AABB aABB = hopper.getSuckAabb().move(hopper.getLevelX() - 0.5, hopper.getLevelY() - 0.5, hopper.getLevelZ() - 0.5);
		return level.getEntitiesOfClass(ItemEntity.class, aABB, EntitySelector.ENTITY_STILL_ALIVE);
	}

	@Nullable
	public static Container getContainerAt(Level level, BlockPos blockPos) {
		return getContainerAt(level, blockPos, level.getBlockState(blockPos), blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
	}

	@Nullable
	private static Container getContainerAt(Level level, BlockPos blockPos, BlockState blockState, double d, double e, double f) {
		Container container = getBlockContainer(level, blockPos, blockState);
		if (container == null) {
			container = getEntityContainer(level, d, e, f);
		}

		return container;
	}

	@Nullable
	private static Container getBlockContainer(Level level, BlockPos blockPos, BlockState blockState) {
		Block block = blockState.getBlock();
		if (block instanceof WorldlyContainerHolder) {
			return ((WorldlyContainerHolder)block).getContainer(blockState, level, blockPos);
		} else if (blockState.hasBlockEntity() && level.getBlockEntity(blockPos) instanceof Container container) {
			if (container instanceof ChestBlockEntity && block instanceof ChestBlock) {
				container = ChestBlock.getContainer((ChestBlock)block, blockState, level, blockPos, true);
			}

			return container;
		} else {
			return null;
		}
	}

	@Nullable
	private static Container getEntityContainer(Level level, double d, double e, double f) {
		List<Entity> list = level.getEntities((Entity)null, new AABB(d - 0.5, e - 0.5, f - 0.5, d + 0.5, e + 0.5, f + 0.5), EntitySelector.CONTAINER_ENTITY_SELECTOR);
		return !list.isEmpty() ? (Container)list.get(level.random.nextInt(list.size())) : null;
	}

	private static boolean canMergeItems(ItemStack itemStack, ItemStack itemStack2) {
		return itemStack.getCount() <= itemStack.getMaxStackSize() && ItemStack.isSameItemSameComponents(itemStack, itemStack2);
	}

	@Override
	public double getLevelX() {
		return this.worldPosition.getX() + 0.5;
	}

	@Override
	public double getLevelY() {
		return this.worldPosition.getY() + 0.5;
	}

	@Override
	public double getLevelZ() {
		return this.worldPosition.getZ() + 0.5;
	}

	@Override
	public boolean isGridAligned() {
		return true;
	}

	private void setCooldown(int i) {
		this.cooldownTime = i;
	}

	private boolean isOnCooldown() {
		return this.cooldownTime > 0;
	}

	private boolean isOnCustomCooldown() {
		return this.cooldownTime > 8;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> nonNullList) {
		this.items = nonNullList;
	}

	public static void entityInside(Level level, BlockPos blockPos, BlockState blockState, Entity entity, HopperBlockEntity hopperBlockEntity) {
		if (entity instanceof ItemEntity itemEntity
			&& !itemEntity.getItem().isEmpty()
			&& entity.getBoundingBox().move(-blockPos.getX(), -blockPos.getY(), -blockPos.getZ()).intersects(hopperBlockEntity.getSuckAabb())) {
			tryMoveItems(level, blockPos, blockState, hopperBlockEntity, () -> addItem(hopperBlockEntity, itemEntity));
		}
	}

	@Override
	protected AbstractContainerMenu createMenu(int i, Inventory inventory) {
		return new HopperMenu(i, inventory, this);
	}
}
