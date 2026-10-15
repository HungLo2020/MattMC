package net.minecraft.core.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public class TaczBulletHoleParticleOptions implements ParticleOptions {
	public static final StreamCodec<RegistryFriendlyByteBuf, TaczBulletHoleParticleOptions> STREAM_CODEC = StreamCodec.ofMember(
		TaczBulletHoleParticleOptions::writeToNetwork, TaczBulletHoleParticleOptions::readFromNetwork
	);
	private final Direction direction;
	private final BlockPos pos;
	private final String ammoId;
	private final String gunId;
	private final String gunDisplayId;

	public TaczBulletHoleParticleOptions(Direction direction, BlockPos pos, String ammoId, String gunId, String gunDisplayId) {
		this.direction = direction;
		this.pos = pos;
		this.ammoId = ammoId;
		this.gunId = gunId;
		this.gunDisplayId = gunDisplayId;
	}

	private static TaczBulletHoleParticleOptions readFromNetwork(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
		Direction direction = Direction.STREAM_CODEC.decode(registryFriendlyByteBuf);
		BlockPos blockPos = BlockPos.STREAM_CODEC.decode(registryFriendlyByteBuf);
		String ammoId = registryFriendlyByteBuf.readUtf();
		String gunId = registryFriendlyByteBuf.readUtf();
		String gunDisplayId = registryFriendlyByteBuf.readUtf();
		return new TaczBulletHoleParticleOptions(direction, blockPos, ammoId, gunId, gunDisplayId);
	}

	private void writeToNetwork(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
		Direction.STREAM_CODEC.encode(registryFriendlyByteBuf, this.direction);
		BlockPos.STREAM_CODEC.encode(registryFriendlyByteBuf, this.pos);
		registryFriendlyByteBuf.writeUtf(this.ammoId);
		registryFriendlyByteBuf.writeUtf(this.gunId);
		registryFriendlyByteBuf.writeUtf(this.gunDisplayId);
	}

	@Override
	public ParticleType<TaczBulletHoleParticleOptions> getType() {
		return ParticleTypes.BULLET_HOLE;
	}

	public Direction direction() {
		return this.direction;
	}

	public BlockPos pos() {
		return this.pos;
	}

	public String ammoId() {
		return this.ammoId;
	}

	public String gunId() {
		return this.gunId;
	}

	public String gunDisplayId() {
		return this.gunDisplayId;
	}
}
