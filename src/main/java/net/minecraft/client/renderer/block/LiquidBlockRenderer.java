package net.minecraft.client.renderer.block;

import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.Plane;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

@Environment(EnvType.CLIENT)
public class LiquidBlockRenderer {
	private static final float MAX_FLUID_HEIGHT = 0.8888889F;
	private final TextureAtlasSprite[] lavaIcons = new TextureAtlasSprite[2];
	private final TextureAtlasSprite[] waterIcons = new TextureAtlasSprite[2];
	private TextureAtlasSprite waterOverlay;

	protected void setupSprites(BlockModelShaper blockModelShaper, MaterialSet materialSet) {
		this.lavaIcons[0] = blockModelShaper.getBlockModel(Blocks.LAVA.defaultBlockState()).particleIcon();
		this.lavaIcons[1] = materialSet.get(ModelBakery.LAVA_FLOW);
		this.waterIcons[0] = blockModelShaper.getBlockModel(Blocks.WATER.defaultBlockState()).particleIcon();
		this.waterIcons[1] = materialSet.get(ModelBakery.WATER_FLOW);
		this.waterOverlay = materialSet.get(ModelBakery.WATER_OVERLAY);
		
		// Notify Fabric API's FluidRenderHandlerRegistry of sprite updates
		net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry.INSTANCE.onFluidRendererReload(
			this, this.waterIcons, this.lavaIcons, this.waterOverlay);
	}

	private static boolean isNeighborSameFluid(FluidState fluidState, FluidState fluidState2) {
		return fluidState2.getType().isSame(fluidState.getType());
	}

	private static boolean isFaceOccludedByState(Direction direction, float f, BlockState blockState) {
		VoxelShape voxelShape = blockState.getFaceOcclusionShape(direction.getOpposite());
		if (voxelShape == Shapes.empty()) {
			return false;
		} else if (voxelShape == Shapes.block()) {
			boolean bl = f == 1.0F;
			return direction != Direction.UP || bl;
		} else {
			VoxelShape voxelShape2 = Shapes.box(0.0, 0.0, 0.0, 1.0, f, 1.0);
			return Shapes.blockOccludes(voxelShape2, voxelShape, direction);
		}
	}

	private static boolean isFaceOccludedBySelf(BlockState blockState, Direction direction) {
		return isFaceOccludedByState(direction.getOpposite(), 1.0F, blockState);
	}

	public static boolean shouldRenderFace(FluidState fluidState, BlockState blockState, Direction direction, FluidState fluidState2) {
		return !isFaceOccludedBySelf(blockState, direction) && !isNeighborSameFluid(fluidState, fluidState2);
	}

	private float calculateAverageHeight(BlockAndTintGetter blockAndTintGetter, Fluid fluid, float f, float g, float h, BlockPos blockPos) {
		if (!(h >= 1.0F) && !(g >= 1.0F)) {
			float[] fs = new float[2];
			if (h > 0.0F || g > 0.0F) {
				float i = this.getHeight(blockAndTintGetter, fluid, blockPos);
				if (i >= 1.0F) {
					return 1.0F;
				}

				this.addWeightedHeight(fs, i);
			}

			this.addWeightedHeight(fs, f);
			this.addWeightedHeight(fs, h);
			this.addWeightedHeight(fs, g);
			return fs[0] / fs[1];
		} else {
			return 1.0F;
		}
	}

	private void addWeightedHeight(float[] fs, float f) {
		if (f >= 0.8F) {
			fs[0] += f * 10.0F;
			fs[1] += 10.0F;
		} else if (f >= 0.0F) {
			fs[0] += f;
			fs[1]++;
		}
	}

	private float getHeight(BlockAndTintGetter blockAndTintGetter, Fluid fluid, BlockPos blockPos) {
		BlockState blockState = blockAndTintGetter.getBlockState(blockPos);
		return this.getHeight(blockAndTintGetter, fluid, blockPos, blockState, blockState.getFluidState());
	}

	private float getHeight(BlockAndTintGetter blockAndTintGetter, Fluid fluid, BlockPos blockPos, BlockState blockState, FluidState fluidState) {
		if (fluid.isSame(fluidState.getType())) {
			BlockState blockState2 = blockAndTintGetter.getBlockState(blockPos.above());
			return fluid.isSame(blockState2.getFluidState().getType()) ? 1.0F : fluidState.getOwnHeight();
		} else {
			return !blockState.isSolid() ? 0.0F : -1.0F;
		}
	}

	private void vertex(VertexConsumer vertexConsumer, float f, float g, float h, float i, float j, float k, float l, float m, int n) {
		vertexConsumer.addVertex(f, g, h).setColor(i, j, k, 1.0F).setUv(l, m).setLight(n).setNormal(0.0F, 1.0F, 0.0F);
	}

	private int getLightColor(BlockAndTintGetter blockAndTintGetter, BlockPos blockPos) {
		int i = LevelRenderer.getLightColor(blockAndTintGetter, blockPos);
		int j = LevelRenderer.getLightColor(blockAndTintGetter, blockPos.above());
		int k = i & 0xFF;
		int l = j & 0xFF;
		int m = i >> 16 & 0xFF;
		int n = j >> 16 & 0xFF;
		return (k > l ? k : l) | (m > n ? m : n) << 16;
	}
}
