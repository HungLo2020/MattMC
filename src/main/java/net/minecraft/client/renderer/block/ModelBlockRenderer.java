package net.minecraft.client.renderer.block;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.hooks.HookRegistry;
import net.minecraft.hooks.ModelBlockRendererHooks;
import net.sodium.client.model.quad.BakedQuadView;
import net.sodium.client.render.frapi.render.SimpleBlockRenderContext;
import net.sodium.client.render.immediate.model.BakedModelEncoder;
import net.sodium.client.render.vertex.VertexConsumerUtils;

@Environment(EnvType.CLIENT)
public class ModelBlockRenderer implements net.fabricmc.fabric.api.renderer.v1.render.FabricBlockModelRenderer {
	private static final Direction[] DIRECTIONS = Direction.values();
	public final BlockColors blockColors; // Made public for Sodium FRAPI integration
	private static final int CACHE_SIZE = 100;
	static final ThreadLocal<ModelBlockRenderer.Cache> CACHE = ThreadLocal.withInitial(ModelBlockRenderer.Cache::new);

	public ModelBlockRenderer(BlockColors blockColors) {
		this.blockColors = blockColors;
	}

	@Override
	public void render(net.minecraft.world.level.BlockAndTintGetter blockView, net.minecraft.client.renderer.block.model.BlockStateModel model, 
	                   net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos, 
	                   PoseStack matrices,
	                   net.fabricmc.fabric.api.renderer.v1.render.BlockVertexConsumerProvider vertexConsumers, 
	                   boolean cull, long seed, int overlay) {
		// Rust meshes block models from copied semantics; there is no Java
		// immediate tessellation into vertex consumers.
		throw new IllegalStateException("Java block-model tessellation is unavailable; Rust meshes block models");
	}

	private static boolean shouldRenderFace(BlockAndTintGetter blockAndTintGetter, BlockState blockState, boolean bl, Direction direction, BlockPos blockPos) {
		if (!bl) {
			return true;
		} else {
			BlockState blockState2 = blockAndTintGetter.getBlockState(blockPos);
			return Block.shouldRenderFace(blockState, blockState2, direction);
		}
	}

	public static void enableCaching() {
		((ModelBlockRenderer.Cache)CACHE.get()).enable();
	}

	public static void clearCache() {
		((ModelBlockRenderer.Cache)CACHE.get()).disable();
	}

	@Environment(EnvType.CLIENT)
	protected static enum AdjacencyInfo {
		DOWN(
			new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH},
			0.5F,
			true,
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.SOUTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.NORTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.NORTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.SOUTH
			}
		),
		UP(
			new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH},
			1.0F,
			true,
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.SOUTH,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.SOUTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.NORTH,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.NORTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.NORTH,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.NORTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.SOUTH,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.SOUTH
			}
		),
		NORTH(
			new Direction[]{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST},
			0.8F,
			true,
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.FLIP_WEST
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.FLIP_EAST
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_EAST
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_WEST
			}
		),
		SOUTH(
			new Direction[]{Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP},
			0.8F,
			true,
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.WEST
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_WEST,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.WEST,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.WEST
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.EAST
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.FLIP_EAST,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.EAST,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.EAST
			}
		),
		WEST(
			new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH},
			0.6F,
			true,
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.SOUTH,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.SOUTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.NORTH,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.NORTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.NORTH,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.NORTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.SOUTH,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.SOUTH
			}
		),
		EAST(
			new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH},
			0.6F,
			true,
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.SOUTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.DOWN,
				ModelBlockRenderer.SizeInfo.NORTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.NORTH,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.FLIP_NORTH,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.NORTH
			},
			new ModelBlockRenderer.SizeInfo[]{
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.SOUTH,
				ModelBlockRenderer.SizeInfo.FLIP_UP,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
				ModelBlockRenderer.SizeInfo.UP,
				ModelBlockRenderer.SizeInfo.SOUTH
			}
		);

		final Direction[] corners;
		final boolean doNonCubicWeight;
		final ModelBlockRenderer.SizeInfo[] vert0Weights;
		final ModelBlockRenderer.SizeInfo[] vert1Weights;
		final ModelBlockRenderer.SizeInfo[] vert2Weights;
		final ModelBlockRenderer.SizeInfo[] vert3Weights;
		private static final ModelBlockRenderer.AdjacencyInfo[] BY_FACING = (ModelBlockRenderer.AdjacencyInfo[])Util.make(
			new ModelBlockRenderer.AdjacencyInfo[6], adjacencyInfos -> {
				adjacencyInfos[Direction.DOWN.get3DDataValue()] = DOWN;
				adjacencyInfos[Direction.UP.get3DDataValue()] = UP;
				adjacencyInfos[Direction.NORTH.get3DDataValue()] = NORTH;
				adjacencyInfos[Direction.SOUTH.get3DDataValue()] = SOUTH;
				adjacencyInfos[Direction.WEST.get3DDataValue()] = WEST;
				adjacencyInfos[Direction.EAST.get3DDataValue()] = EAST;
			}
		);

		private AdjacencyInfo(
			final Direction[] directions,
			final float f,
			final boolean bl,
			final ModelBlockRenderer.SizeInfo[] sizeInfos,
			final ModelBlockRenderer.SizeInfo[] sizeInfos2,
			final ModelBlockRenderer.SizeInfo[] sizeInfos3,
			final ModelBlockRenderer.SizeInfo[] sizeInfos4
		) {
			this.corners = directions;
			this.doNonCubicWeight = bl;
			this.vert0Weights = sizeInfos;
			this.vert1Weights = sizeInfos2;
			this.vert2Weights = sizeInfos3;
			this.vert3Weights = sizeInfos4;
		}

		public static ModelBlockRenderer.AdjacencyInfo fromFacing(Direction direction) {
			return BY_FACING[direction.get3DDataValue()];
		}
	}

	@Environment(EnvType.CLIENT)
	static class AmbientOcclusionRenderStorage extends ModelBlockRenderer.CommonRenderStorage {
		final float[] faceShape = new float[ModelBlockRenderer.SizeInfo.COUNT];

		public AmbientOcclusionRenderStorage() {
		}

		public void calculate(BlockAndTintGetter blockAndTintGetter, BlockState blockState, BlockPos blockPos, Direction direction, boolean bl) {
			BlockPos blockPos2 = this.faceCubic ? blockPos.relative(direction) : blockPos;
			ModelBlockRenderer.AdjacencyInfo adjacencyInfo = ModelBlockRenderer.AdjacencyInfo.fromFacing(direction);
			MutableBlockPos mutableBlockPos = this.scratchPos;
			mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[0]);
			BlockState blockState2 = blockAndTintGetter.getBlockState(mutableBlockPos);
			int i = this.cache.getLightColor(blockState2, blockAndTintGetter, mutableBlockPos);
			float f = this.cache.getShadeBrightness(blockState2, blockAndTintGetter, mutableBlockPos);
			mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[1]);
			BlockState blockState3 = blockAndTintGetter.getBlockState(mutableBlockPos);
			int j = this.cache.getLightColor(blockState3, blockAndTintGetter, mutableBlockPos);
			float g = this.cache.getShadeBrightness(blockState3, blockAndTintGetter, mutableBlockPos);
			mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[2]);
			BlockState blockState4 = blockAndTintGetter.getBlockState(mutableBlockPos);
			int k = this.cache.getLightColor(blockState4, blockAndTintGetter, mutableBlockPos);
			float h = this.cache.getShadeBrightness(blockState4, blockAndTintGetter, mutableBlockPos);
			mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[3]);
			BlockState blockState5 = blockAndTintGetter.getBlockState(mutableBlockPos);
			int l = this.cache.getLightColor(blockState5, blockAndTintGetter, mutableBlockPos);
			float m = this.cache.getShadeBrightness(blockState5, blockAndTintGetter, mutableBlockPos);
			BlockState blockState6 = blockAndTintGetter.getBlockState(mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[0]).move(direction));
			boolean bl2 = !blockState6.isViewBlocking(blockAndTintGetter, mutableBlockPos) || blockState6.getLightBlock() == 0;
			BlockState blockState7 = blockAndTintGetter.getBlockState(mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[1]).move(direction));
			boolean bl3 = !blockState7.isViewBlocking(blockAndTintGetter, mutableBlockPos) || blockState7.getLightBlock() == 0;
			BlockState blockState8 = blockAndTintGetter.getBlockState(mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[2]).move(direction));
			boolean bl4 = !blockState8.isViewBlocking(blockAndTintGetter, mutableBlockPos) || blockState8.getLightBlock() == 0;
			BlockState blockState9 = blockAndTintGetter.getBlockState(mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[3]).move(direction));
			boolean bl5 = !blockState9.isViewBlocking(blockAndTintGetter, mutableBlockPos) || blockState9.getLightBlock() == 0;
			float n;
			int o;
			if (!bl4 && !bl2) {
				n = f;
				o = i;
			} else {
				mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[0]).move(adjacencyInfo.corners[2]);
				BlockState blockState10 = blockAndTintGetter.getBlockState(mutableBlockPos);
				n = this.cache.getShadeBrightness(blockState10, blockAndTintGetter, mutableBlockPos);
				o = this.cache.getLightColor(blockState10, blockAndTintGetter, mutableBlockPos);
			}

			float p;
			int q;
			if (!bl5 && !bl2) {
				p = f;
				q = i;
			} else {
				mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[0]).move(adjacencyInfo.corners[3]);
				BlockState blockState10 = blockAndTintGetter.getBlockState(mutableBlockPos);
				p = this.cache.getShadeBrightness(blockState10, blockAndTintGetter, mutableBlockPos);
				q = this.cache.getLightColor(blockState10, blockAndTintGetter, mutableBlockPos);
			}

			float r;
			int s;
			if (!bl4 && !bl3) {
				r = f;
				s = i;
			} else {
				mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[1]).move(adjacencyInfo.corners[2]);
				BlockState blockState10 = blockAndTintGetter.getBlockState(mutableBlockPos);
				r = this.cache.getShadeBrightness(blockState10, blockAndTintGetter, mutableBlockPos);
				s = this.cache.getLightColor(blockState10, blockAndTintGetter, mutableBlockPos);
			}

			float t;
			int u;
			if (!bl5 && !bl3) {
				t = f;
				u = i;
			} else {
				mutableBlockPos.setWithOffset(blockPos2, adjacencyInfo.corners[1]).move(adjacencyInfo.corners[3]);
				BlockState blockState10 = blockAndTintGetter.getBlockState(mutableBlockPos);
				t = this.cache.getShadeBrightness(blockState10, blockAndTintGetter, mutableBlockPos);
				u = this.cache.getLightColor(blockState10, blockAndTintGetter, mutableBlockPos);
			}

			int v = this.cache.getLightColor(blockState, blockAndTintGetter, blockPos);
			mutableBlockPos.setWithOffset(blockPos, direction);
			BlockState blockState11 = blockAndTintGetter.getBlockState(mutableBlockPos);
			if (this.faceCubic || !blockState11.isSolidRender()) {
				v = this.cache.getLightColor(blockState11, blockAndTintGetter, mutableBlockPos);
			}

			float w = this.faceCubic
				? this.cache.getShadeBrightness(blockAndTintGetter.getBlockState(blockPos2), blockAndTintGetter, blockPos2)
				: this.cache.getShadeBrightness(blockAndTintGetter.getBlockState(blockPos), blockAndTintGetter, blockPos);
			ModelBlockRenderer.AmbientVertexRemap ambientVertexRemap = ModelBlockRenderer.AmbientVertexRemap.fromFacing(direction);
			if (this.facePartial && adjacencyInfo.doNonCubicWeight) {
				float x = (m + f + p + w) * 0.25F;
				float y = (h + f + n + w) * 0.25F;
				float z = (h + g + r + w) * 0.25F;
				float aa = (m + g + t + w) * 0.25F;
				float ab = this.faceShape[adjacencyInfo.vert0Weights[0].index] * this.faceShape[adjacencyInfo.vert0Weights[1].index];
				float ac = this.faceShape[adjacencyInfo.vert0Weights[2].index] * this.faceShape[adjacencyInfo.vert0Weights[3].index];
				float ad = this.faceShape[adjacencyInfo.vert0Weights[4].index] * this.faceShape[adjacencyInfo.vert0Weights[5].index];
				float ae = this.faceShape[adjacencyInfo.vert0Weights[6].index] * this.faceShape[adjacencyInfo.vert0Weights[7].index];
				float af = this.faceShape[adjacencyInfo.vert1Weights[0].index] * this.faceShape[adjacencyInfo.vert1Weights[1].index];
				float ag = this.faceShape[adjacencyInfo.vert1Weights[2].index] * this.faceShape[adjacencyInfo.vert1Weights[3].index];
				float ah = this.faceShape[adjacencyInfo.vert1Weights[4].index] * this.faceShape[adjacencyInfo.vert1Weights[5].index];
				float ai = this.faceShape[adjacencyInfo.vert1Weights[6].index] * this.faceShape[adjacencyInfo.vert1Weights[7].index];
				float aj = this.faceShape[adjacencyInfo.vert2Weights[0].index] * this.faceShape[adjacencyInfo.vert2Weights[1].index];
				float ak = this.faceShape[adjacencyInfo.vert2Weights[2].index] * this.faceShape[adjacencyInfo.vert2Weights[3].index];
				float al = this.faceShape[adjacencyInfo.vert2Weights[4].index] * this.faceShape[adjacencyInfo.vert2Weights[5].index];
				float am = this.faceShape[adjacencyInfo.vert2Weights[6].index] * this.faceShape[adjacencyInfo.vert2Weights[7].index];
				float an = this.faceShape[adjacencyInfo.vert3Weights[0].index] * this.faceShape[adjacencyInfo.vert3Weights[1].index];
				float ao = this.faceShape[adjacencyInfo.vert3Weights[2].index] * this.faceShape[adjacencyInfo.vert3Weights[3].index];
				float ap = this.faceShape[adjacencyInfo.vert3Weights[4].index] * this.faceShape[adjacencyInfo.vert3Weights[5].index];
				float aq = this.faceShape[adjacencyInfo.vert3Weights[6].index] * this.faceShape[adjacencyInfo.vert3Weights[7].index];
				this.brightness[ambientVertexRemap.vert0] = Math.clamp(x * ab + y * ac + z * ad + aa * ae, 0.0F, 1.0F);
				this.brightness[ambientVertexRemap.vert1] = Math.clamp(x * af + y * ag + z * ah + aa * ai, 0.0F, 1.0F);
				this.brightness[ambientVertexRemap.vert2] = Math.clamp(x * aj + y * ak + z * al + aa * am, 0.0F, 1.0F);
				this.brightness[ambientVertexRemap.vert3] = Math.clamp(x * an + y * ao + z * ap + aa * aq, 0.0F, 1.0F);
				int ar = blend(l, i, q, v);
				int as = blend(k, i, o, v);
				int at = blend(k, j, s, v);
				int au = blend(l, j, u, v);
				this.lightmap[ambientVertexRemap.vert0] = blend(ar, as, at, au, ab, ac, ad, ae);
				this.lightmap[ambientVertexRemap.vert1] = blend(ar, as, at, au, af, ag, ah, ai);
				this.lightmap[ambientVertexRemap.vert2] = blend(ar, as, at, au, aj, ak, al, am);
				this.lightmap[ambientVertexRemap.vert3] = blend(ar, as, at, au, an, ao, ap, aq);
			} else {
				float x = (m + f + p + w) * 0.25F;
				float y = (h + f + n + w) * 0.25F;
				float z = (h + g + r + w) * 0.25F;
				float aa = (m + g + t + w) * 0.25F;
				this.lightmap[ambientVertexRemap.vert0] = blend(l, i, q, v);
				this.lightmap[ambientVertexRemap.vert1] = blend(k, i, o, v);
				this.lightmap[ambientVertexRemap.vert2] = blend(k, j, s, v);
				this.lightmap[ambientVertexRemap.vert3] = blend(l, j, u, v);
				this.brightness[ambientVertexRemap.vert0] = x;
				this.brightness[ambientVertexRemap.vert1] = y;
				this.brightness[ambientVertexRemap.vert2] = z;
				this.brightness[ambientVertexRemap.vert3] = aa;
			}

			float x = blockAndTintGetter.getShade(direction, bl);

			for (int av = 0; av < this.brightness.length; av++) {
				this.brightness[av] = this.brightness[av] * x;
			}
		}

		private static int blend(int i, int j, int k, int l) {
			if (i == 0) {
				i = l;
			}

			if (j == 0) {
				j = l;
			}

			if (k == 0) {
				k = l;
			}

			return i + j + k + l >> 2 & 16711935;
		}

		private static int blend(int i, int j, int k, int l, float f, float g, float h, float m) {
			int n = (int)((i >> 16 & 0xFF) * f + (j >> 16 & 0xFF) * g + (k >> 16 & 0xFF) * h + (l >> 16 & 0xFF) * m) & 0xFF;
			int o = (int)((i & 0xFF) * f + (j & 0xFF) * g + (k & 0xFF) * h + (l & 0xFF) * m) & 0xFF;
			return n << 16 | o;
		}
	}

	@Environment(EnvType.CLIENT)
	static enum AmbientVertexRemap {
		DOWN(0, 1, 2, 3),
		UP(2, 3, 0, 1),
		NORTH(3, 0, 1, 2),
		SOUTH(0, 1, 2, 3),
		WEST(3, 0, 1, 2),
		EAST(1, 2, 3, 0);

		final int vert0;
		final int vert1;
		final int vert2;
		final int vert3;
		private static final ModelBlockRenderer.AmbientVertexRemap[] BY_FACING = (ModelBlockRenderer.AmbientVertexRemap[])Util.make(
			new ModelBlockRenderer.AmbientVertexRemap[6], ambientVertexRemaps -> {
				ambientVertexRemaps[Direction.DOWN.get3DDataValue()] = DOWN;
				ambientVertexRemaps[Direction.UP.get3DDataValue()] = UP;
				ambientVertexRemaps[Direction.NORTH.get3DDataValue()] = NORTH;
				ambientVertexRemaps[Direction.SOUTH.get3DDataValue()] = SOUTH;
				ambientVertexRemaps[Direction.WEST.get3DDataValue()] = WEST;
				ambientVertexRemaps[Direction.EAST.get3DDataValue()] = EAST;
			}
		);

		private AmbientVertexRemap(final int j, final int k, final int l, final int m) {
			this.vert0 = j;
			this.vert1 = k;
			this.vert2 = l;
			this.vert3 = m;
		}

		public static ModelBlockRenderer.AmbientVertexRemap fromFacing(Direction direction) {
			return BY_FACING[direction.get3DDataValue()];
		}
	}

	@Environment(EnvType.CLIENT)
	static class Cache {
		private boolean enabled;
		private final Long2IntLinkedOpenHashMap colorCache = (Long2IntLinkedOpenHashMap)Util.make(() -> {
			Long2IntLinkedOpenHashMap long2IntLinkedOpenHashMap = new Long2IntLinkedOpenHashMap(100, 0.25F) {
				@Override
				protected void rehash(int i) {
				}
			};
			long2IntLinkedOpenHashMap.defaultReturnValue(Integer.MAX_VALUE);
			return long2IntLinkedOpenHashMap;
		});
		private final Long2FloatLinkedOpenHashMap brightnessCache = (Long2FloatLinkedOpenHashMap)Util.make(() -> {
			Long2FloatLinkedOpenHashMap long2FloatLinkedOpenHashMap = new Long2FloatLinkedOpenHashMap(100, 0.25F) {
				@Override
				protected void rehash(int i) {
				}
			};
			long2FloatLinkedOpenHashMap.defaultReturnValue(Float.NaN);
			return long2FloatLinkedOpenHashMap;
		});
		private final LevelRenderer.BrightnessGetter cachedBrightnessGetter = (blockAndTintGetter, blockPos) -> {
			long l = blockPos.asLong();
			int i = this.colorCache.get(l);
			if (i != Integer.MAX_VALUE) {
				return i;
			} else {
				int j = LevelRenderer.BrightnessGetter.DEFAULT.packedBrightness(blockAndTintGetter, blockPos);
				if (this.colorCache.size() == 100) {
					this.colorCache.removeFirstInt();
				}

				this.colorCache.put(l, j);
				return j;
			}
		};

		private Cache() {
		}

		public void enable() {
			this.enabled = true;
		}

		public void disable() {
			this.enabled = false;
			this.colorCache.clear();
			this.brightnessCache.clear();
		}

		public int getLightColor(BlockState blockState, BlockAndTintGetter blockAndTintGetter, BlockPos blockPos) {
			return LevelRenderer.getLightColor(
				this.enabled ? this.cachedBrightnessGetter : LevelRenderer.BrightnessGetter.DEFAULT, blockAndTintGetter, blockState, blockPos
			);
		}

		public float getShadeBrightness(BlockState blockState, BlockAndTintGetter blockAndTintGetter, BlockPos blockPos) {
			long l = blockPos.asLong();
			if (this.enabled) {
				float f = this.brightnessCache.get(l);
				if (!Float.isNaN(f)) {
					return f;
				}
			}

			float f = blockState.getShadeBrightness(blockAndTintGetter, blockPos);
			if (this.enabled) {
				if (this.brightnessCache.size() == 100) {
					this.brightnessCache.removeFirstFloat();
				}

				this.brightnessCache.put(l, f);
			}

			return f;
		}
	}

	@Environment(EnvType.CLIENT)
	static class CommonRenderStorage {
		public final MutableBlockPos scratchPos = new MutableBlockPos();
		public boolean faceCubic;
		public boolean facePartial;
		public final float[] brightness = new float[4];
		public final int[] lightmap = new int[4];
		public int tintCacheIndex = -1;
		public int tintCacheValue;
		public final ModelBlockRenderer.Cache cache = (ModelBlockRenderer.Cache)ModelBlockRenderer.CACHE.get();
	}

	@Environment(EnvType.CLIENT)
	protected static enum SizeInfo {
		DOWN(0),
		UP(1),
		NORTH(2),
		SOUTH(3),
		WEST(4),
		EAST(5),
		FLIP_DOWN(6),
		FLIP_UP(7),
		FLIP_NORTH(8),
		FLIP_SOUTH(9),
		FLIP_WEST(10),
		FLIP_EAST(11);

		public static final int COUNT = values().length;
		final int index;

		private SizeInfo(final int j) {
			this.index = j;
		}
	}

	// Sodium: Fast model rendering helpers (merged from ModelBlockRendererMixin model.block)
	private static final ThreadLocal<net.minecraft.util.RandomSource> SODIUM$RANDOM = ThreadLocal.withInitial(() -> new net.minecraft.world.level.levelgen.SingleThreadedRandomSource(42L));
	private static final ThreadLocal<java.util.List<BlockModelPart>> SODIUM$LIST = ThreadLocal.withInitial(() -> new it.unimi.dsi.fastutil.objects.ObjectArrayList<>());

	@SuppressWarnings("ForLoopReplaceableByForEach")
	private static void sodium$renderQuads(PoseStack.Pose matrices, net.sodium.api.vertex.buffer.VertexBufferWriter writer, int defaultColor, java.util.List<BakedQuad> quads, int light, int overlay) {
		for (int i = 0; i < quads.size(); i++) {
			BakedQuad bakedQuad = quads.get(i);

			if (bakedQuad.vertices().length < 32) {
				continue; // ignore bad quads
			}

			BakedQuadView quad = (BakedQuadView) (Object) bakedQuad;

			int color = quad.hasColor() ? defaultColor : 0xFFFFFFFF;

			BakedModelEncoder.writeQuadVertices(writer, matrices, quad, color, light, overlay, false);

			if (quad.getSprite() != null) {
				net.sodium.api.texture.SpriteUtil.INSTANCE.markSpriteActive(quad.getSprite());
			}
		}
	}

}
