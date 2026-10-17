package net.minecraft.world.item.equipment.trim;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceLocation;

public record TrimPattern(ResourceLocation assetId, Component description, boolean decal) {
	public static final Codec<TrimPattern> DIRECT_CODEC = net.minecraft.resources.NativeData.codec(net.minecraft.resources.NativeData.Kind.TRIM_PATTERN, false, net.minecraft.resources.NativeDataProjections::trimPattern, net.minecraft.resources.NativeDataProjections::trimPatternOut);
	public static final StreamCodec<RegistryFriendlyByteBuf, TrimPattern> DIRECT_STREAM_CODEC = StreamCodec.composite(
		ResourceLocation.STREAM_CODEC,
		TrimPattern::assetId,
		ComponentSerialization.STREAM_CODEC,
		TrimPattern::description,
		ByteBufCodecs.BOOL,
		TrimPattern::decal,
		TrimPattern::new
	);
	public static final Codec<Holder<TrimPattern>> CODEC = RegistryFileCodec.create(Registries.TRIM_PATTERN, DIRECT_CODEC);
	public static final StreamCodec<RegistryFriendlyByteBuf, Holder<TrimPattern>> STREAM_CODEC = ByteBufCodecs.holder(Registries.TRIM_PATTERN, DIRECT_STREAM_CODEC);

	public Component copyWithStyle(Holder<TrimMaterial> holder) {
		return this.description.copy().withStyle(holder.value().description().getStyle());
	}
}
