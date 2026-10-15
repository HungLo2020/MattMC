package net.minecraft.core.particles;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.phys.Vec3;

public record TrailParticleOption(Vec3 target, int color, int duration) implements ParticleOptions {
	public static final StreamCodec<RegistryFriendlyByteBuf, TrailParticleOption> STREAM_CODEC = StreamCodec.composite(
		Vec3.STREAM_CODEC,
		TrailParticleOption::target,
		ByteBufCodecs.INT,
		TrailParticleOption::color,
		ByteBufCodecs.VAR_INT,
		TrailParticleOption::duration,
		TrailParticleOption::new
	);

	@Override
	public ParticleType<TrailParticleOption> getType() {
		return ParticleTypes.TRAIL;
	}
}
