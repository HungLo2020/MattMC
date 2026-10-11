package net.minecraft.client.renderer.block.model;

import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record SimpleModelWrapper(QuadCollection quads, boolean useAmbientOcclusion, TextureAtlasSprite particleIcon) implements BlockModelPart {
	@Override
	public List<BakedQuad> getQuads(@Nullable Direction direction) {
		return this.quads.getQuads(direction);
	}
}
