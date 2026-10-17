package net.minecraft.world.level.block.entity;

import net.minecraft.world.Container;
import net.minecraft.world.item.NativeItemSlots;
import org.jetbrains.annotations.Nullable;

/**
 * A stock container whose exact class keeps its slots in native rows (chest,
 * barrel, hopper, shulker box, dispenser, dropper). Hoppers, comparators and
 * occupancy checks consume the rows natively; every other caller, and every
 * subclass, keeps the original Java behavior.
 */
public interface NativeRowsContainer extends Container {
	/** The native rows of an exact, loot-free instance, else null. */
	@Nullable
	NativeItemSlots nativeSlotsForTransfer();
}
