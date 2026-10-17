package net.minecraft.world;

import net.minecraft.world.item.ItemStack;

public record ItemStackWithSlot(int slot, ItemStack stack) {
	/** Rust decodes, validates and encodes slotted stacks (`content/data/items.rs`). */
	public static final net.minecraft.resources.NativeValues.ValueCodec<ItemStackWithSlot> CODEC = net.minecraft.resources.NativeValues.codec(
		net.minecraft.resources.NativeValues.Kind.ITEM_STACK_WITH_SLOT,
		(value, context) -> {
			Object[] f = net.minecraft.resources.NativeData.record(value);
			return new ItemStackWithSlot((Integer)f[0], net.minecraft.world.item.NativeItemStacks.project(f[1], context));
		},
		(slotted, builder) -> new Object[]{slotted.slot(), net.minecraft.world.item.NativeItemStacks.unproject(slotted.stack(), builder)}
	);

	public boolean isValidInContainer(int i) {
		return this.slot >= 0 && this.slot < i;
	}
}
