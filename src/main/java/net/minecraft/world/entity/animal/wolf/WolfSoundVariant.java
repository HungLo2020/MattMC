package net.minecraft.world.entity.animal.wolf;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.sounds.SoundEvent;

public record WolfSoundVariant(
	Holder<SoundEvent> ambientSound,
	Holder<SoundEvent> deathSound,
	Holder<SoundEvent> growlSound,
	Holder<SoundEvent> hurtSound,
	Holder<SoundEvent> pantSound,
	Holder<SoundEvent> whineSound
) {
	public static final Codec<WolfSoundVariant> DIRECT_CODEC = net.minecraft.resources.NativeData.codec(net.minecraft.resources.NativeData.Kind.WOLF_SOUND_VARIANT, false, net.minecraft.resources.NativeDataProjections::wolfSound, net.minecraft.resources.NativeDataProjections::wolfSoundOut);
	public static final Codec<WolfSoundVariant> NETWORK_CODEC = net.minecraft.resources.NativeData.codec(net.minecraft.resources.NativeData.Kind.WOLF_SOUND_VARIANT, true, net.minecraft.resources.NativeDataProjections::wolfSound, net.minecraft.resources.NativeDataProjections::wolfSoundOut);
	public static final Codec<Holder<WolfSoundVariant>> CODEC = RegistryFixedCodec.create(Registries.WOLF_SOUND_VARIANT);
	public static final StreamCodec<RegistryFriendlyByteBuf, Holder<WolfSoundVariant>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.WOLF_SOUND_VARIANT);
}
