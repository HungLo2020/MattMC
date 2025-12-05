package net.minecraft.network.chat.contents.objects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.ResourceLocation;

public record AtlasSprite(ResourceLocation atlas, ResourceLocation sprite) implements ObjectInfo {
	public static final ResourceLocation DEFAULT_ATLAS = AtlasIds.BLOCKS;
	public static final MapCodec<AtlasSprite> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				ResourceLocation.CODEC.optionalFieldOf("atlas", DEFAULT_ATLAS).forGetter(AtlasSprite::atlas),
				ResourceLocation.CODEC.fieldOf("sprite").forGetter(AtlasSprite::sprite)
			)
			.apply(instance, AtlasSprite::new)
	);

	@Override
	public MapCodec<AtlasSprite> codec() {
		return MAP_CODEC;
	}

	@Override
	public FontDescription fontDescription() {
		return new FontDescription.AtlasSprite(this.atlas, this.sprite);
	}

	private static String toShortName(ResourceLocation resourceLocation) {
		return resourceLocation.getNamespace().equals("minecraft") ? resourceLocation.getPath() : resourceLocation.toString();
	}

	@Override
	public String description() {
		String string = toShortName(this.sprite);
		return this.atlas.equals(DEFAULT_ATLAS) ? "[" + string + "]" : "[" + string + "@" + toShortName(this.atlas) + "]";
	}
}
