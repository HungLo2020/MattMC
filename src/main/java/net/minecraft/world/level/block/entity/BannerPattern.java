package net.minecraft.world.level.block.entity;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceLocation;

public record BannerPattern(ResourceLocation assetId, String translationKey) {
	public static final Codec<BannerPattern> DIRECT_CODEC = net.minecraft.resources.NativeData.codec(net.minecraft.resources.NativeData.Kind.BANNER_PATTERN, false, net.minecraft.resources.NativeDataProjections::bannerPattern, net.minecraft.resources.NativeDataProjections::bannerPatternOut);
	public static final StreamCodec<RegistryFriendlyByteBuf, BannerPattern> DIRECT_STREAM_CODEC = StreamCodec.composite(
		ResourceLocation.STREAM_CODEC, BannerPattern::assetId, ByteBufCodecs.STRING_UTF8, BannerPattern::translationKey, BannerPattern::new
	);
	public static final Codec<Holder<BannerPattern>> CODEC = RegistryFileCodec.create(Registries.BANNER_PATTERN, DIRECT_CODEC);
	public static final StreamCodec<RegistryFriendlyByteBuf, Holder<BannerPattern>> STREAM_CODEC = ByteBufCodecs.holder(
		Registries.BANNER_PATTERN, DIRECT_STREAM_CODEC
	);
}
