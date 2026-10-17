package net.minecraft.core.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.PositionSource;

public class VibrationParticleOption implements ParticleOptions {
	public static final StreamCodec<RegistryFriendlyByteBuf, VibrationParticleOption> STREAM_CODEC = StreamCodec.composite(
		PositionSource.STREAM_CODEC,
		VibrationParticleOption::getDestination,
		ByteBufCodecs.VAR_INT,
		VibrationParticleOption::getArrivalInTicks,
		VibrationParticleOption::new
	);
	private final PositionSource destination;
	private final int arrivalInTicks;

	public VibrationParticleOption(PositionSource positionSource, int i) {
		this.destination = positionSource;
		this.arrivalInTicks = i;
	}

	@Override
	public ParticleType<VibrationParticleOption> getType() {
		return ParticleTypes.VIBRATION;
	}

	public PositionSource getDestination() {
		return this.destination;
	}

	public int getArrivalInTicks() {
		return this.arrivalInTicks;
	}
}
