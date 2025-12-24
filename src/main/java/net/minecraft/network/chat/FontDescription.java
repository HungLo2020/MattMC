package net.minecraft.network.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.ResolvableProfile;

public interface FontDescription {
	Codec<FontDescription> CODEC = ResourceLocation.CODEC
		.flatComapMap(
			FontDescription.Resource::new,
			fontDescription -> fontDescription instanceof FontDescription.Resource resource
				? DataResult.success(resource.id())
				: DataResult.error(() -> "Unsupported font description type: " + fontDescription)
		);
	FontDescription.Resource DEFAULT = new FontDescription.Resource(ResourceLocation.withDefaultNamespace("default"));

	public record AtlasSprite(ResourceLocation atlasId, ResourceLocation spriteId) implements FontDescription {
	}

	public record PlayerSprite(ResolvableProfile profile, boolean hat) implements FontDescription {
	}

	public record Resource(ResourceLocation id) implements FontDescription {
	}
}
