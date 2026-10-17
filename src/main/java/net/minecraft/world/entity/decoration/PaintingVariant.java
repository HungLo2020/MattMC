package net.minecraft.world.entity.decoration;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceLocation;

public record PaintingVariant(int width, int height, ResourceLocation assetId, Optional<Component> title, Optional<Component> author) {
	public static final Codec<PaintingVariant> DIRECT_CODEC = net.minecraft.resources.NativeData.codec(net.minecraft.resources.NativeData.Kind.PAINTING_VARIANT, false, net.minecraft.resources.NativeDataProjections::painting, net.minecraft.resources.NativeDataProjections::paintingOut);
	public static final StreamCodec<RegistryFriendlyByteBuf, PaintingVariant> DIRECT_STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		PaintingVariant::width,
		ByteBufCodecs.VAR_INT,
		PaintingVariant::height,
		ResourceLocation.STREAM_CODEC,
		PaintingVariant::assetId,
		ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC,
		PaintingVariant::title,
		ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC,
		PaintingVariant::author,
		PaintingVariant::new
	);
	public static final Codec<Holder<PaintingVariant>> CODEC = RegistryFixedCodec.create(Registries.PAINTING_VARIANT);
	public static final StreamCodec<RegistryFriendlyByteBuf, Holder<PaintingVariant>> STREAM_CODEC = ByteBufCodecs.holder(
		Registries.PAINTING_VARIANT, DIRECT_STREAM_CODEC
	);

	public int area() {
		return this.width() * this.height();
	}
}
