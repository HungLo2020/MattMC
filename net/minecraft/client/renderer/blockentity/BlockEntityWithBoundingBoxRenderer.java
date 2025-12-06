package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityWithBoundingBoxRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BoundingBoxRenderable;
import net.minecraft.world.level.block.entity.BoundingBoxRenderable.Mode;
import net.minecraft.world.level.block.entity.BoundingBoxRenderable.RenderableBox;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class BlockEntityWithBoundingBoxRenderer<T extends BlockEntity & BoundingBoxRenderable>
	implements BlockEntityRenderer<T, BlockEntityWithBoundingBoxRenderState> {
	public BlockEntityWithBoundingBoxRenderState createRenderState() {
		return new BlockEntityWithBoundingBoxRenderState();
	}

	public void extractRenderState(
		T blockEntity,
		BlockEntityWithBoundingBoxRenderState blockEntityWithBoundingBoxRenderState,
		float f,
		Vec3 vec3,
		@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(blockEntity, blockEntityWithBoundingBoxRenderState, f, vec3, crumblingOverlay);
		extract(blockEntity, blockEntityWithBoundingBoxRenderState);
	}

	public static <T extends BlockEntity & BoundingBoxRenderable> void extract(
		T blockEntity, BlockEntityWithBoundingBoxRenderState blockEntityWithBoundingBoxRenderState
	) {
		LocalPlayer localPlayer = Minecraft.getInstance().player;
		blockEntityWithBoundingBoxRenderState.isVisible = localPlayer.canUseGameMasterBlocks() || localPlayer.isSpectator();
		blockEntityWithBoundingBoxRenderState.box = blockEntity.getRenderableBox();
		blockEntityWithBoundingBoxRenderState.mode = blockEntity.renderMode();
		BlockPos blockPos = blockEntityWithBoundingBoxRenderState.box.localPos();
		Vec3i vec3i = blockEntityWithBoundingBoxRenderState.box.size();
		BlockPos blockPos2 = blockEntityWithBoundingBoxRenderState.blockPos;
		BlockPos blockPos3 = blockPos2.offset(blockPos);
		if (blockEntityWithBoundingBoxRenderState.isVisible
			&& blockEntity.getLevel() != null
			&& blockEntityWithBoundingBoxRenderState.mode == Mode.BOX_AND_INVISIBLE_BLOCKS) {
			blockEntityWithBoundingBoxRenderState.invisibleBlocks = new BlockEntityWithBoundingBoxRenderState.InvisibleBlockType[vec3i.getX()
				* vec3i.getY()
				* vec3i.getZ()];

			for (int i = 0; i < vec3i.getX(); i++) {
				for (int j = 0; j < vec3i.getY(); j++) {
					for (int k = 0; k < vec3i.getZ(); k++) {
						int l = k * vec3i.getX() * vec3i.getY() + j * vec3i.getX() + i;
						BlockState blockState = blockEntity.getLevel().getBlockState(blockPos3.offset(i, j, k));
						if (blockState.isAir()) {
							blockEntityWithBoundingBoxRenderState.invisibleBlocks[l] = BlockEntityWithBoundingBoxRenderState.InvisibleBlockType.AIR;
						} else if (blockState.is(Blocks.STRUCTURE_VOID)) {
							blockEntityWithBoundingBoxRenderState.invisibleBlocks[l] = BlockEntityWithBoundingBoxRenderState.InvisibleBlockType.STRUCUTRE_VOID;
						} else if (blockState.is(Blocks.BARRIER)) {
							blockEntityWithBoundingBoxRenderState.invisibleBlocks[l] = BlockEntityWithBoundingBoxRenderState.InvisibleBlockType.BARRIER;
						} else if (blockState.is(Blocks.LIGHT)) {
							blockEntityWithBoundingBoxRenderState.invisibleBlocks[l] = BlockEntityWithBoundingBoxRenderState.InvisibleBlockType.LIGHT;
						}
					}
				}
			}
		} else {
			blockEntityWithBoundingBoxRenderState.invisibleBlocks = null;
		}

		if (blockEntityWithBoundingBoxRenderState.isVisible) {
		}

		blockEntityWithBoundingBoxRenderState.structureVoids = null;
	}

	public void submit(
		BlockEntityWithBoundingBoxRenderState blockEntityWithBoundingBoxRenderState,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		CameraRenderState cameraRenderState
	) {
		if (blockEntityWithBoundingBoxRenderState.isVisible) {
			Mode mode = blockEntityWithBoundingBoxRenderState.mode;
			if (mode != Mode.NONE) {
				RenderableBox renderableBox = blockEntityWithBoundingBoxRenderState.box;
				BlockPos blockPos = renderableBox.localPos();
				Vec3i vec3i = renderableBox.size();
				if (vec3i.getX() >= 1 && vec3i.getY() >= 1 && vec3i.getZ() >= 1) {
					float f = 1.0F;
					float g = 0.9F;
					float h = 0.5F;
					BlockPos blockPos2 = blockPos.offset(vec3i);
					submitNodeCollector.submitCustomGeometry(
						poseStack,
						RenderType.lines(),
						(pose, vertexConsumer) -> ShapeRenderer.renderLineBox(
							pose,
							vertexConsumer,
							blockPos.getX(),
							blockPos.getY(),
							blockPos.getZ(),
							blockPos2.getX(),
							blockPos2.getY(),
							blockPos2.getZ(),
							0.9F,
							0.9F,
							0.9F,
							1.0F,
							0.5F,
							0.5F,
							0.5F
						)
					);
					this.submitInvisibleBlocks(blockEntityWithBoundingBoxRenderState, blockPos, vec3i, submitNodeCollector, poseStack);
				}
			}
		}
	}

	private void submitInvisibleBlocks(
		BlockEntityWithBoundingBoxRenderState blockEntityWithBoundingBoxRenderState,
		BlockPos blockPos,
		Vec3i vec3i,
		SubmitNodeCollector submitNodeCollector,
		PoseStack poseStack
	) {
		if (blockEntityWithBoundingBoxRenderState.invisibleBlocks != null) {
			BlockPos blockPos2 = blockEntityWithBoundingBoxRenderState.blockPos;
			BlockPos blockPos3 = blockPos2.offset(blockPos);
			submitNodeCollector.submitCustomGeometry(poseStack, RenderType.lines(), (pose, vertexConsumer) -> {
				for (int i = 0; i < vec3i.getX(); i++) {
					for (int j = 0; j < vec3i.getY(); j++) {
						for (int k = 0; k < vec3i.getZ(); k++) {
							int l = k * vec3i.getX() * vec3i.getY() + j * vec3i.getX() + i;
							BlockEntityWithBoundingBoxRenderState.InvisibleBlockType invisibleBlockType = blockEntityWithBoundingBoxRenderState.invisibleBlocks[l];
							if (invisibleBlockType != null) {
								float f = invisibleBlockType == BlockEntityWithBoundingBoxRenderState.InvisibleBlockType.AIR ? 0.05F : 0.0F;
								double d = blockPos3.getX() + i - blockPos2.getX() + 0.45F - f;
								double e = blockPos3.getY() + j - blockPos2.getY() + 0.45F - f;
								double g = blockPos3.getZ() + k - blockPos2.getZ() + 0.45F - f;
								double h = blockPos3.getX() + i - blockPos2.getX() + 0.55F + f;
								double m = blockPos3.getY() + j - blockPos2.getY() + 0.55F + f;
								double n = blockPos3.getZ() + k - blockPos2.getZ() + 0.55F + f;
								if (invisibleBlockType == BlockEntityWithBoundingBoxRenderState.InvisibleBlockType.AIR) {
									ShapeRenderer.renderLineBox(pose, vertexConsumer, d, e, g, h, m, n, 0.5F, 0.5F, 1.0F, 1.0F, 0.5F, 0.5F, 1.0F);
								} else if (invisibleBlockType == BlockEntityWithBoundingBoxRenderState.InvisibleBlockType.STRUCUTRE_VOID) {
									ShapeRenderer.renderLineBox(pose, vertexConsumer, d, e, g, h, m, n, 1.0F, 0.75F, 0.75F, 1.0F, 1.0F, 0.75F, 0.75F);
								} else if (invisibleBlockType == BlockEntityWithBoundingBoxRenderState.InvisibleBlockType.BARRIER) {
									ShapeRenderer.renderLineBox(pose, vertexConsumer, d, e, g, h, m, n, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F);
								} else if (invisibleBlockType == BlockEntityWithBoundingBoxRenderState.InvisibleBlockType.LIGHT) {
									ShapeRenderer.renderLineBox(pose, vertexConsumer, d, e, g, h, m, n, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F);
								}
							}
						}
					}
				}
			});
		}
	}

	private void renderStructureVoids(
		BlockEntityWithBoundingBoxRenderState blockEntityWithBoundingBoxRenderState, BlockPos blockPos, Vec3i vec3i, VertexConsumer vertexConsumer, Matrix4f matrix4f
	) {
		if (blockEntityWithBoundingBoxRenderState.structureVoids != null) {
			BlockPos blockPos2 = blockEntityWithBoundingBoxRenderState.blockPos;
			DiscreteVoxelShape discreteVoxelShape = new BitSetDiscreteVoxelShape(vec3i.getX(), vec3i.getY(), vec3i.getZ());

			for (int i = 0; i < vec3i.getX(); i++) {
				for (int j = 0; j < vec3i.getY(); j++) {
					for (int k = 0; k < vec3i.getZ(); k++) {
						int l = k * vec3i.getX() * vec3i.getY() + j * vec3i.getX() + i;
						if (blockEntityWithBoundingBoxRenderState.structureVoids[l]) {
							discreteVoxelShape.fill(i, j, k);
						}
					}
				}
			}

			discreteVoxelShape.forAllFaces((direction, ix, jx, kx) -> {
				float f = 0.48F;
				float g = ix + blockPos.getX() - blockPos2.getX() + 0.5F - 0.48F;
				float h = jx + blockPos.getY() - blockPos2.getY() + 0.5F - 0.48F;
				float lx = kx + blockPos.getZ() - blockPos2.getZ() + 0.5F - 0.48F;
				float m = ix + blockPos.getX() - blockPos2.getX() + 0.5F + 0.48F;
				float n = jx + blockPos.getY() - blockPos2.getY() + 0.5F + 0.48F;
				float o = kx + blockPos.getZ() - blockPos2.getZ() + 0.5F + 0.48F;
				ShapeRenderer.renderFace(matrix4f, vertexConsumer, direction, g, h, lx, m, n, o, 0.75F, 0.75F, 1.0F, 0.2F);
			});
		}
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 96;
	}
}
