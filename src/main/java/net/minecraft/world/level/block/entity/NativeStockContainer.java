package net.minecraft.world.level.block.entity;

import net.minecraft.world.Container;
import net.minecraft.world.item.NativeItemSlots;
import net.minecraft.world.item.NativeItemState;
import org.jetbrains.annotations.Nullable;

/**
 * A stock 27-slot container (chest, barrel) whose exact class keeps its slots in
 * a replaceable native container cell. Native transfers, menu slot links,
 * hoppers and comparators consume these rows; subclasses return null and keep
 * their virtual behavior.
 */
public interface NativeStockContainer extends NativeRowsContainer {
	/** The current native rows of an exact, loot-free instance, else null. */
	@Override
	@Nullable
	NativeItemSlots nativeSlotsForTransfer();

	/** The replaceable native container cell of an exact instance, else null. */
	@Nullable
	NativeItemState.ContainerCell nativeContainerForSlots();

	/** True for the exact stock classes that own a native container cell. */
	static boolean exact(@Nullable Container container) {
		return container != null && (container.getClass() == ChestBlockEntity.class || container.getClass() == BarrelBlockEntity.class);
	}
}
