package net.minecraft.client.renderer.block.model;

import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public class SingleVariant implements BlockStateModel {
	private final BlockModelPart model;

	public SingleVariant(BlockModelPart blockModelPart) {
		this.model = blockModelPart;
	}

	@Override
	public void collectParts(RandomSource randomSource, List<BlockModelPart> list) {
		list.add(this.model);
	}

	@Override
	public TextureAtlasSprite particleIcon() {
		return this.model.particleIcon();
	}

	public BlockModelPart sodium$getModelPart() {
		return this.model;
	}
}
