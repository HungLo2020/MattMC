package net.minecraft.world.entity.variant;

import net.minecraft.core.ClientAsset;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record ModelAndTexture<T>(T model, ClientAsset.ResourceTexture asset) {
	public ModelAndTexture(T object, ResourceLocation resourceLocation) {
		this(object, new ClientAsset.ResourceTexture(resourceLocation));
	}

	public static <T> StreamCodec<RegistryFriendlyByteBuf, ModelAndTexture<T>> streamCodec(StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
		return StreamCodec.composite(streamCodec, ModelAndTexture::model, ClientAsset.ResourceTexture.STREAM_CODEC, ModelAndTexture::asset, ModelAndTexture::new);
	}
}
