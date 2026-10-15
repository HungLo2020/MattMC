package net.minecraft.core.particles;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public class ItemParticleOption implements ParticleOptions {
	private final ParticleType<ItemParticleOption> type;
	private final ItemStack itemStack;

	public static StreamCodec<? super RegistryFriendlyByteBuf, ItemParticleOption> streamCodec(ParticleType<ItemParticleOption> particleType) {
		return ItemStack.STREAM_CODEC.map(itemStack -> new ItemParticleOption(particleType, itemStack), itemParticleOption -> itemParticleOption.itemStack);
	}

	public ItemParticleOption(ParticleType<ItemParticleOption> particleType, ItemStack itemStack) {
		if (itemStack.isEmpty()) {
			throw new IllegalArgumentException("Empty stacks are not allowed");
		} else {
			this.type = particleType;
			this.itemStack = itemStack;
		}
	}

	@Override
	public ParticleType<ItemParticleOption> getType() {
		return this.type;
	}

	public ItemStack getItem() {
		return this.itemStack;
	}
}
