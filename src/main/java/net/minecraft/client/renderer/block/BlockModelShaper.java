package net.minecraft.client.renderer.block;

import java.util.Map;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public class BlockModelShaper {
	private Map<BlockState, BlockStateModel> modelByStateCache = Map.of();
	private final ModelManager modelManager;

	public BlockModelShaper(ModelManager modelManager) {
		this.modelManager = modelManager;
	}

	public TextureAtlasSprite getParticleIcon(BlockState blockState) {
		return this.getBlockModel(blockState).particleIcon();
	}

	public BlockStateModel getBlockModel(BlockState blockState) {
		BlockStateModel blockStateModel = (BlockStateModel)this.modelByStateCache.get(blockState);
		if (blockStateModel == null) {
			blockStateModel = this.modelManager.getMissingBlockStateModel();
		}

		return blockStateModel;
	}

	public ModelManager getModelManager() {
		return this.modelManager;
	}

	public void replaceCache(Map<BlockState, BlockStateModel> map) {
		this.modelByStateCache = map;
	}
}
