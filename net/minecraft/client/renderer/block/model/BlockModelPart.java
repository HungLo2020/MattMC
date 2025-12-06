package net.minecraft.client.renderer.block.model;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface BlockModelPart extends FabricBlockModelPart {
	List<BakedQuad> getQuads(@Nullable Direction direction);

	boolean useAmbientOcclusion();

	TextureAtlasSprite particleIcon();

	@Environment(EnvType.CLIENT)
	public interface Unbaked extends ResolvableModel {
		BlockModelPart bake(ModelBaker modelBaker);
	}
}
