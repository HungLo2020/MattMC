package net.minecraft.client.renderer.block.model;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record SimpleModelWrapper(QuadCollection quads, boolean useAmbientOcclusion, TextureAtlasSprite particleIcon) implements BlockModelPart {
	public static SimpleModelWrapper bake(ModelBaker modelBaker, ResourceLocation resourceLocation, ModelState modelState) {
		ResolvedModel resolvedModel = modelBaker.getModel(resourceLocation);
		TextureSlots textureSlots = resolvedModel.getTopTextureSlots();
		boolean bl = resolvedModel.getTopAmbientOcclusion();
		TextureAtlasSprite textureAtlasSprite = resolvedModel.resolveParticleSprite(textureSlots, modelBaker);
		QuadCollection quadCollection = resolvedModel.bakeTopGeometry(textureSlots, modelBaker, modelState);
		return new SimpleModelWrapper(quadCollection, bl, textureAtlasSprite);
	}

	@Override
	public List<BakedQuad> getQuads(@Nullable Direction direction) {
		return this.quads.getQuads(direction);
	}
}
