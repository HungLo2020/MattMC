package net.minecraft.client.renderer.block.model;

import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface BlockModelPart extends net.irisshaders.iris.compat.general.IrisModelPart, net.fabricmc.fabric.api.renderer.v1.model.FabricBlockModelPart {
	List<BakedQuad> getQuads(@Nullable Direction direction);

	boolean useAmbientOcclusion();

	TextureAtlasSprite particleIcon();
	
	// Sodium FRAPI: Implementation of FabricBlockModelPart
	@Override
	default void emitQuads(net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter emitter, java.util.function.Predicate<@Nullable Direction> cullTest) {
		if (emitter instanceof net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext.BlockEmitter be) {
			be.emitPart(this, cullTest);
		}
		// If not a BlockEmitter, do nothing - no super call needed since interface
		// provides default implementation
	}

	@Environment(EnvType.CLIENT)
	public interface Unbaked extends ResolvableModel {
		BlockModelPart bake(ModelBaker modelBaker);
	}
}
