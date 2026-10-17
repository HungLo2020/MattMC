package net.minecraft.core.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class BlockParticleOption implements ParticleOptions {
	private final ParticleType<BlockParticleOption> type;
	private final BlockState state;

	public static StreamCodec<? super RegistryFriendlyByteBuf, BlockParticleOption> streamCodec(ParticleType<BlockParticleOption> particleType) {
		return ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY)
			.map(blockState -> new BlockParticleOption(particleType, blockState), blockParticleOption -> blockParticleOption.state);
	}

	public BlockParticleOption(ParticleType<BlockParticleOption> particleType, BlockState blockState) {
		this.type = particleType;
		this.state = blockState;
	}

	@Override
	public ParticleType<BlockParticleOption> getType() {
		return this.type;
	}

	public BlockState getState() {
		return this.state;
	}
}
