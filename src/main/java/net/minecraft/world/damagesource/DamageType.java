package net.minecraft.world.damagesource;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFixedCodec;

public record DamageType(String msgId, DamageScaling scaling, float exhaustion, DamageEffects effects, DeathMessageType deathMessageType) {
	public static final Codec<DamageType> DIRECT_CODEC = net.minecraft.resources.NativeData.codec(net.minecraft.resources.NativeData.Kind.DAMAGE_TYPE, false, net.minecraft.resources.NativeDataProjections::damageType, net.minecraft.resources.NativeDataProjections::damageTypeOut);
	public static final Codec<Holder<DamageType>> CODEC = RegistryFixedCodec.create(Registries.DAMAGE_TYPE);
	public static final StreamCodec<RegistryFriendlyByteBuf, Holder<DamageType>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.DAMAGE_TYPE);

	public DamageType(String string, DamageScaling damageScaling, float f) {
		this(string, damageScaling, f, DamageEffects.HURT, DeathMessageType.DEFAULT);
	}

	public DamageType(String string, DamageScaling damageScaling, float f, DamageEffects damageEffects) {
		this(string, damageScaling, f, damageEffects, DeathMessageType.DEFAULT);
	}

	public DamageType(String string, float f, DamageEffects damageEffects) {
		this(string, DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, f, damageEffects);
	}

	public DamageType(String string, float f) {
		this(string, DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, f);
	}
}
