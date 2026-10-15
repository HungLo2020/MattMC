package net.minecraft.network.chat.contents.objects;

import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.ResourceLocation;

public record AtlasSprite(ResourceLocation atlas, ResourceLocation sprite) implements ObjectInfo {
	public static final ResourceLocation DEFAULT_ATLAS = AtlasIds.BLOCKS;
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
