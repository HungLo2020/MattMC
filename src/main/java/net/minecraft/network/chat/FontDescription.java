package net.minecraft.network.chat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.ResolvableProfile;

public interface FontDescription {
	FontDescription.Resource DEFAULT = new FontDescription.Resource(ResourceLocation.withDefaultNamespace("default"));

	public record AtlasSprite(ResourceLocation atlasId, ResourceLocation spriteId) implements FontDescription {
	}

	public record PlayerSprite(ResolvableProfile profile, boolean hat) implements FontDescription {
	}

	public record Resource(ResourceLocation id) implements FontDescription {
	}
}
