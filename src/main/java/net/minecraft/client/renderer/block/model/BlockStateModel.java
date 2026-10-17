package net.minecraft.client.renderer.block.model;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
// Sodium FRAPI imports
import net.sodium.client.render.frapi.render.AbstractBlockRenderContext;
import net.sodium.client.services.PlatformModelAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockModelPart;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import java.util.function.Predicate;

@Environment(EnvType.CLIENT)
public interface BlockStateModel extends FabricBlockStateModel {
	void collectParts(RandomSource randomSource, List<BlockModelPart> list);

	default List<BlockModelPart> collectParts(RandomSource randomSource) {
		List<BlockModelPart> list = new ObjectArrayList<>();
		this.collectParts(randomSource, list);
		return list;
	}

	TextureAtlasSprite particleIcon();
	
	// Sodium FRAPI: FabricBlockStateModel implementation for Fabric Rendering API
	@Override
	default void emitQuads(QuadEmitter emitter, BlockAndTintGetter blockView, BlockPos pos, BlockState state, 
			RandomSource random, Predicate<@Nullable Direction> cullTest) {
		List<BlockModelPart> parts = PlatformModelAccess.getInstance().collectPartsOf(this, blockView, pos, state, random, emitter);
		int partCount = parts.size();

		if (emitter instanceof AbstractBlockRenderContext.BlockEmitter be) {
			ChunkSectionLayer type = ItemBlockRenderTypes.getChunkRenderType(state);

			for (int i = 0; i < partCount; ++i) {
				if (PlatformModelAccess.getInstance().getPartRenderType(parts.get(i), state, type) != type) {
					be.markInvalidToDowngrade();
					break;
				}
			}
		}

		for (int i = 0; i < partCount; ++i) {
			((FabricBlockModelPart) parts.get(i)).emitQuads(emitter, cullTest);
		}
	}
}
